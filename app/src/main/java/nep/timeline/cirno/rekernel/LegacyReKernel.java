package nep.timeline.cirno.rekernel;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.netlink.IoUtils;
import nep.timeline.cirno.netlink.NetlinkClient;
import nep.timeline.cirno.netlink.NetlinkSocketAddress;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.StatusBinderHub;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.StringUtils;

public class LegacyReKernel {
    private static FileDescriptor fileDescriptor = null;
    private static boolean defaultUnit = false;
    private static final int NETLINK_UNIT_DEFAULT = 22;
    private static final int NETLINK_UNIT_MAX = 26;
    private static final int SOCKET_RECV_BUFSIZE = 64 * 1024;
    private static final int DEFAULT_RECV_BUFSIZE = 8 * 1024;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static boolean isRunning() {
        return fileDescriptor != null && fileDescriptor.valid();
    }

    public static boolean monitorNet(int uid) {
        if (!isRunning() || defaultUnit)
            return false;

        Log.d("Legacy monitorNet uid=" + uid);

        try {
            byte[] payload = new byte[8];
            ByteBuffer cmdBuf = ByteBuffer.wrap(payload);
            cmdBuf.order(ByteOrder.nativeOrder());
            cmdBuf.putInt(2);
            cmdBuf.putInt(uid);

            byte[] bytes = new byte[16 + payload.length];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.order(ByteOrder.nativeOrder());

            byteBuffer.putInt(bytes.length);
            byteBuffer.putShort((short) 0x11);
            byteBuffer.putShort((short) 0x1);
            byteBuffer.putInt(1);
            byteBuffer.putInt(100);
            byteBuffer.put(payload);

            try {
                Os.write(fileDescriptor, bytes, 0, bytes.length);
                return true;
            } catch (ErrnoException ignored) {
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static boolean delMonitorNet(int uid) {
        if (!isRunning() || defaultUnit)
            return false;

        Log.d("Legacy delMonitorNet uid=" + uid);

        try {
            byte[] payload = new byte[8];
            ByteBuffer cmdBuf = ByteBuffer.wrap(payload);
            cmdBuf.order(ByteOrder.nativeOrder());
            cmdBuf.putInt(3);
            cmdBuf.putInt(uid);

            byte[] bytes = new byte[16 + payload.length];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.order(ByteOrder.nativeOrder());

            byteBuffer.putInt(bytes.length);
            byteBuffer.putShort((short) 0x11);
            byteBuffer.putShort((short) 0x1);
            byteBuffer.putInt(1);
            byteBuffer.putInt(100);
            byteBuffer.put(payload);

            try {
                Os.write(fileDescriptor, bytes, 0, bytes.length);
                return true;
            } catch (ErrnoException ignored) {
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static void start(ClassLoader classLoader) {
        if (isRunning() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return;

        executorService.execute(() -> {
            try {
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
                            return;
                        }
                        File unitFile = files[0];
                        netlinkUnit = StringUtils.StringToInteger(unitFile.getName());
                    } else {
                        defaultUnit = true;
                        netlinkUnit = NETLINK_UNIT_DEFAULT;
                    }
                }

                NetlinkClient netlinkClient;
                try {
                    netlinkClient = new NetlinkClient(classLoader, netlinkUnit);
                } catch (Throwable e) {
                    Log.w("初始化ReKernel(Legacy)客户端失败, netlinkUnit=" + netlinkUnit, e);
                    return;
                }
                try {
                    if (!netlinkClient.getMDescriptor().valid()) {
                        Log.w("无法连接至ReKernel(Legacy)服务器");
                        netlinkClient.close();
                        return;
                    }

                    try {
                        netlinkClient.bind((SocketAddress) new NetlinkSocketAddress(100).toInstance(classLoader));
                    } catch (Throwable e) {
                        Log.w("绑定ReKernel(Legacy)客户端失败, netlinkUnit=" + netlinkUnit + ", portId=100", e);
                        netlinkClient.close();
                        return;
                    }

                    fileDescriptor = netlinkClient.getMDescriptor();

                    if (!defaultUnit) {
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
                            Log.d("发送proc_remove消息");
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
                            Log.d("发送内核init命令");
                        } catch (Throwable e) {
                            Log.w("无法发送内核init命令", e);
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
                                        monitorNet(record.getUid());
                                    }
                                }
                            }
                        }, 10_000L);
                    }

                    Log.i("已连接至ReKernel(Legacy), " + netlinkUnit + "#100");
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel");

                    while (true) {
                        try {
                            ByteBuffer byteBuffer = netlinkClient.recvMessage();
                            String data = new String(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit(), StandardCharsets.UTF_8);
                            if (!data.isEmpty())
                                ReKernel.onEvent(data);
                        } catch (ErrnoException | InterruptedIOException |
                                 NumberFormatException ignored) {
                        } catch (Exception e) {
                            Log.e("ReKernel(Legacy)接收消息失败, netlinkUnit=" + netlinkUnit, e);
                        }
                    }
                } finally {
                    fileDescriptor = null;
                    netlinkClient.close();
                }
            } catch (Throwable throwable) {
                Log.w("ReKernel(Legacy)", throwable);
            }
        });
    }
}
