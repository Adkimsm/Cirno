package nep.timeline.cirno.services;

import android.system.ErrnoException;

import java.io.File;
import java.io.InterruptedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.netlink.NetlinkClient;
import nep.timeline.cirno.netlink.NetlinkSocketAddress;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.FrozenRW;
import nep.timeline.cirno.utils.StringUtils;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class BinderService {
    private final static ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final int NETLINK_UNIT_DEFAULT = 22;
    private static final int NETLINK_UNIT_MAX = 26;
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;
    public static volatile boolean received = false;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static volatile NetlinkClient sNetlinkClient;

    private static Map<String, String> parseParams(String message) {
        Map<String, String> map = new HashMap<>();
        for (String keyValue : message.split(",")) {
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
            return StringUtils.StringToInteger(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void registerNetUid(int uid) {
        NetlinkClient client = sNetlinkClient;
        if (client == null) return;
        try {
            byte[] payload = new byte[8];
            ByteBuffer buf = ByteBuffer.wrap(payload);
            buf.order(ByteOrder.nativeOrder());
            buf.putInt(2);
            buf.putInt(uid);

            byte[] bytes = new byte[16 + payload.length];
            ByteBuffer msg = ByteBuffer.wrap(bytes);
            msg.order(ByteOrder.nativeOrder());
            msg.putInt(bytes.length);
            msg.putShort((short) 0x11);
            msg.putShort((short) 0x1);
            msg.putInt(1);
            msg.putInt(100);
            msg.put(payload);
            client.sendMessage(bytes, 0, bytes.length);
        } catch (Throwable e) {
            Log.w("无法注册内核网络消息 uid=" + uid, e);
        }
    }

    public static void unregisterNetUid(int uid) {
        NetlinkClient client = sNetlinkClient;
        if (client == null) return;
        try {
            byte[] payload = new byte[8];
            ByteBuffer buf = ByteBuffer.wrap(payload);
            buf.order(ByteOrder.nativeOrder());
            buf.putInt(3);
            buf.putInt(uid);

            byte[] bytes = new byte[16 + payload.length];
            ByteBuffer msg = ByteBuffer.wrap(bytes);
            msg.order(ByteOrder.nativeOrder());
            msg.putInt(bytes.length);
            msg.putShort((short) 0x11);
            msg.putShort((short) 0x1);
            msg.putInt(1);
            msg.putInt(100);
            msg.put(payload);
            client.sendMessage(bytes, 0, bytes.length);
        } catch (Throwable e) {
            Log.w("无法取消注册内核网络消息 uid=" + uid, e);
        }
    }

    public static void start(ClassLoader classLoader) {
        if (!isRunning.compareAndSet(false, true))
            return;

        executorService.execute(() -> {
            try {
                boolean rekernelFound = false;
                int netlinkUnit;
                int configNetlinkUnit = GlobalVars.globalSettings == null ? 0 : GlobalVars.globalSettings.netlinkUnit;
                if (configNetlinkUnit >= NETLINK_UNIT_DEFAULT && configNetlinkUnit <= NETLINK_UNIT_MAX) {
                    netlinkUnit = configNetlinkUnit;
                } else {
                    File dir = new File("/proc/rekernel");
                    if (dir.exists()) {
                        File[] files = dir.listFiles();
                        if (files == null) {
                            Log.w("找不到ReKernel单元");
                            isRunning.set(false);
                            return;
                        }
                        File unitFile = files[0];
                        netlinkUnit = StringUtils.StringToInteger(unitFile.getName());
                        rekernelFound = true;
                    } else netlinkUnit = NETLINK_UNIT_DEFAULT;
                }

                NetlinkClient netlinkClient;
                try {
                    netlinkClient = new NetlinkClient(classLoader, netlinkUnit);
                } catch (Throwable e) {
                    Log.w("初始化ReKernel客户端失败, netlinkUnit=" + netlinkUnit, e);
                    isRunning.set(false);
                    return;
                }
                sNetlinkClient = netlinkClient;
                try {
                    if (!netlinkClient.getMDescriptor().valid()) {
                        Log.w("无法连接至ReKernel服务器");
                        isRunning.set(false);
                        return;
                    }

                    try {
                        netlinkClient.bind((SocketAddress) new NetlinkSocketAddress(100).toInstance(classLoader));
                    } catch (Throwable e) {
                        Log.w("绑定ReKernel客户端失败, netlinkUnit=" + netlinkUnit + ", portId=100", e);
                        isRunning.set(false);
                        return;
                    }

                    if (rekernelFound) {
                        try {
                            byte[] message = "#proc_remove\0".getBytes(StandardCharsets.UTF_8);
                            byte[] bytes = new byte[16 + message.length];
                            ByteBuffer procRemoveBuf = ByteBuffer.wrap(bytes);
                            procRemoveBuf.order(ByteOrder.nativeOrder());
                            procRemoveBuf.putInt(bytes.length);
                            procRemoveBuf.putShort((short) 0x11);
                            procRemoveBuf.putShort((short) 0x1);
                            procRemoveBuf.putInt(1);
                            procRemoveBuf.putInt(100);
                            procRemoveBuf.put(message);
                            netlinkClient.sendMessage(bytes, 0, bytes.length);
                        } catch (Throwable e) {
                            Log.w("无法发送proc_remove消息", e);
                        }

                        try {
                            byte[] payload = new byte[4];
                            ByteBuffer cmdBuf = ByteBuffer.wrap(payload);
                            cmdBuf.order(ByteOrder.nativeOrder());
                            cmdBuf.putInt(1);

                            byte[] bytes = new byte[16 + payload.length];
                            ByteBuffer cmdMsgBuf = ByteBuffer.wrap(bytes);
                            cmdMsgBuf.order(ByteOrder.nativeOrder());
                            cmdMsgBuf.putInt(bytes.length);
                            cmdMsgBuf.putShort((short) 0x11);
                            cmdMsgBuf.putShort((short) 0x1);
                            cmdMsgBuf.putInt(1);
                            cmdMsgBuf.putInt(100);
                            cmdMsgBuf.put(payload);
                            netlinkClient.sendMessage(bytes, 0, bytes.length);
                        } catch (Throwable e) {
                            Log.w("无法发送内核1消息", e);
                        }

                        Handlers.rekernel.postDelayed(() -> {
                            Set<String> apps = GlobalVars.applicationSettings != null
                                ? GlobalVars.applicationSettings.networkMessageApps : null;
                            if (apps != null) {
                                for (String key : apps) {
                                    String[] parts = key.split("#");
                                    if (parts.length < 1) continue;
                                    String pkg = parts[0];
                                    int userId = parts.length > 1 ? StringUtils.StringToInteger(parts[1]) : 0;
                                    AppRecord record = AppService.get(pkg, userId);
                                    if (record != null) {
                                        registerNetUid(record.getUid());
                                    }
                                }
                            }
                        }, 10_000L);
                    }

                    Log.i("已连接至ReKernel, " + netlinkUnit + "#100");
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel");

                    while (true) {
                        try {
                            ByteBuffer byteBuffer = netlinkClient.recvMessage();
                            String data = new String(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit(), StandardCharsets.UTF_8);
                            if (!data.isEmpty()) {
                                int typeIndex = data.indexOf("type");
                                int endIndex = data.lastIndexOf(";");
                                if (typeIndex < 0 || endIndex <= typeIndex)
                                    continue;

                                Map<String, String> params = parseParams(data.substring(typeIndex, endIndex));
                                if (params.containsKey("type") && !received) {
                                    Log.i("成功接收到来自ReKernel的消息");
                                    received = true;
                                }
                                Handlers.rekernel.post(() -> {
                                    String type = params.get("type");
                                    if (type == null)
                                        return;

                                    switch (type) {
                                        case "Binder" -> {
                                            String bindertype = params.get("bindertype");
                                            int oneway = getIntParam(params, "oneway");
                                            int targetUid = getIntParam(params, "target");
                                            int fromPid = getIntParam(params, "from_pid");
                                            String rpcName = params.get("rpc_name");
                                            int code = getIntParam(params, "code");
                                            if (oneway == 1 && !"free_buffer_full".equals(bindertype))
                                                return;

                                            List<AppRecord> appRecords = AppService.getByUid(targetUid);
                                            if (appRecords.isEmpty())
                                                return;
                                            for (AppRecord appRecord : appRecords) {
                                                if (appRecord == null)
                                                    continue;

                                                FreezerService.temporaryUnfreezeIfNeed(appRecord, "内核Binder(" + (oneway == 1 ? "ASYNC" : "SYNC") + "), 类型: " + bindertype, TEMP_UNFREEZE_INTERVAL_MS);
                                            }
                                        }
                                        case "Signal" -> {
                                            int dstPid = getIntParam(params, "dst_pid");
                                            int signal = getIntParam(params, "signal");
                                            if (dstPid <= 0)
                                                return;
                                            ProcessRecord processRecord = ProcessService.getProcessRecordByPid(dstPid);
                                            if (processRecord == null)
                                                return;
                                            ProcessService.removeProcessRecordWithoutThaw(processRecord, "ReKernel Signal(signal=" + signal + ")");
                                        }
                                        case "Network" -> {
                                            int targetUid = getIntParam(params, "target");
                                            String proto = params.get("proto");
                                            List<AppRecord> appRecords = AppService.getByUid(targetUid);
                                            if (appRecords.isEmpty())
                                                return;
                                            for (AppRecord appRecord : appRecords) {
                                                if (appRecord == null)
                                                    continue;

                                                boolean networkMessageAllowed = AppConfigs.isNetworkMessageAllowed(
                                                    appRecord.getPackageName(),
                                                    appRecord.getUserId()
                                                );
                                                if (!networkMessageAllowed)
                                                    continue;

                                                FreezerService.temporaryUnfreezeIfNeed(appRecord, "内核Network(" + proto + ")", TEMP_UNFREEZE_INTERVAL_MS);
                                            }
                                        }
                                    }
                                });
                            }
                        } catch (ErrnoException | InterruptedIOException |
                                 NumberFormatException ignored) {

                        } catch (Exception e) {
                            Log.e("ReKernel接收消息失败, netlinkUnit=" + netlinkUnit, e);
                        }
                    }
                } finally {
                    sNetlinkClient = null;
                    netlinkClient.close();
                }
            } catch (Throwable throwable) {
                Log.w("ReKernel", throwable);
                isRunning.set(false);
            }
        });
    }
}
