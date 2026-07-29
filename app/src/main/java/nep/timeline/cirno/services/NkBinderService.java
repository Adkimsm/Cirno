package nep.timeline.cirno.services;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class NkBinderService {
    // 不能用 static final：stop() shutdownNow 之后 executor 不可复用，
    // 热重载后再次 start() 会抛 RejectedExecutionException
    private static volatile ExecutorService executorService;
    private static final int MESSAGE_LENGTH = 128;
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;
    private static final long RECONNECT_BASE_DELAY_MS = 3000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;
    public static volatile boolean received = false;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static volatile LocalSocket activeSocket;

    private static Map<String, String> parseParams(String message) {
        Map<String, String> map = new HashMap<>();
        for (String keyValue : message.split(" ")) {
            String[] split = keyValue.split("=");
            if (split.length == 2)
                map.put(split[0].trim(), split[1].trim());
        }
        return map;
    }

    private static int getIntParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null)
            return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isAvailable() {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress("nkbinder", LocalSocketAddress.Namespace.ABSTRACT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void start(ClassLoader classLoader) {
        if (!isRunning.compareAndSet(false, true))
            return;

        ExecutorService executor = executorService;
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
            executorService = executor;
        }

        executor.execute(() -> {
            long reconnectDelay = RECONNECT_BASE_DELAY_MS;

            while (isRunning.get()) {
                try (LocalSocket socket = new LocalSocket()) {
                    activeSocket = socket;
                    socket.connect(new LocalSocketAddress("nkbinder", LocalSocketAddress.Namespace.ABSTRACT));
                    socket.setSoTimeout(0);

                    Log.i("已连接至 nkBinder");
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "nkBinder");
                    received = true;
                    reconnectDelay = RECONNECT_BASE_DELAY_MS;

                    InputStream in = socket.getInputStream();
                    byte[] buffer = new byte[MESSAGE_LENGTH];

                    while (isRunning.get()) {
                        int bytesRead = 0;
                        while (bytesRead < MESSAGE_LENGTH) {
                            int n = in.read(buffer, bytesRead, MESSAGE_LENGTH - bytesRead);
                            if (n < 0)
                                break;
                            bytesRead += n;
                        }
                        if (bytesRead < MESSAGE_LENGTH)
                            break;

                        String message = new String(buffer, 0, MESSAGE_LENGTH, StandardCharsets.UTF_8).trim();
                        if (message.isEmpty())
                            continue;

                        Map<String, String> params = parseParams(message);
                        String type = params.get("type");
                        if (type == null)
                            continue;

                        Handlers.rekernel.post(() -> {
                            switch (type) {
                                case "syncBinder" -> {
                                    int fromUid = getIntParam(params, "from_uid");
                                    int toPid = getIntParam(params, "to_pid");

                                    if (toPid <= 0)
                                        return;

                                    ProcessRecord processRecord = ProcessService.getProcessRecordByPid(toPid);
                                    if (processRecord == null)
                                        return;

                                    AppRecord appRecord = processRecord.getAppRecord();
                                    if (appRecord == null)
                                        return;

                                    FreezerService.temporaryUnfreezeIfNeed(appRecord, "nkBinder(syncBinder), from_uid=" + fromUid, TEMP_UNFREEZE_INTERVAL_MS);
                                }
                                case "signal" -> {
                                    int toPid = getIntParam(params, "to_pid");
                                    int signal = getIntParam(params, "signal");

                                    if (toPid <= 0)
                                        return;

                                    ProcessRecord processRecord = ProcessService.getProcessRecordByPid(toPid);
                                    if (processRecord == null)
                                        return;

                                    ProcessService.removeProcessRecordWithoutThaw(processRecord, "nkBinder Signal(signal=" + signal + ")");
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    if (isRunning.get())
                        Log.w("nkBinder 连接失败", e);
                } finally {
                    activeSocket = null;
                }

                if (!isRunning.get())
                    break;

                Log.i("nkBinder 断开连接, " + reconnectDelay + "ms 后重连");
                try {
                    Thread.sleep(reconnectDelay);
                } catch (InterruptedException e) {
                    break;
                }
                reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_DELAY_MS);
            }

            isRunning.set(false);
        });
    }

    public static void stop() {
        isRunning.set(false);
        LocalSocket socket = activeSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
        ExecutorService executor = executorService;
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
