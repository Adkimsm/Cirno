package nep.timeline.cirno.rekernel;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

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
import nep.timeline.cirno.netlink.NetlinkSocketAddress;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.StatusBinderHub;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.StringUtils;

public class GenericReKernel {
    private static FileDescriptor fileDescriptor = null;

    private static volatile int familyId = -1;
    private static volatile int mcastGroupId = -1;

    private static final int SOCKET_RECV_BUFSIZE = 64 * 1024;
    private static final int DEFAULT_RECV_BUFSIZE = 8 * 1024;

    private static final int NETLINK_GENERIC = 16;
    private static final int SOL_NETLINK = 270;
    private static final int NETLINK_ADD_MEMBERSHIP = 1;
    private static final int GENL_ID_CTRL = 16;
    private static final int NLMSG_MIN_TYPE = 0x10;
    private static final short NLM_F_REQUEST = 0x01;

    private static final int NLMSG_HDRLEN = 16;
    private static final int GENL_HDRLEN = 4;
    private static final int NLA_HDRLEN = 4;
    private static final int NLA_TYPE_MASK = 0x3FFF;

    private static final byte CTRL_CMD_GETFAMILY = 3;
    private static final short CTRL_ATTR_FAMILY_ID = 1;
    private static final short CTRL_ATTR_FAMILY_NAME = 2;
    private static final short CTRL_ATTR_MCAST_GROUPS = 7;
    private static final short CTRL_ATTR_MCAST_GRP_NAME = 1;
    private static final short CTRL_ATTR_MCAST_GRP_ID = 2;

    private static final String GENL_FAMILY_NAME = "rekernel";
    private static final String GENL_MCGRP_NAME = "events";
    private static final byte GENL_VERSION = 1;
    private static final byte REKERNEL_C_EVENT = 1;
    private static final byte REKERNEL_C_MONITOR_NET = 2;
    private static final byte REKERNEL_C_DEL_MONITOR_NET = 3;
    private static final short REKERNEL_A_MSG = 1;
    private static final short REKERNEL_A_UID = 2;

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private static int align4(int n) {
        return (n + 3) & ~3;
    }

    public static boolean isRunning() {
        return fileDescriptor != null && fileDescriptor.valid();
    }

    private static boolean sendCommand(byte cmd, boolean hasUid, int uid) {
        if (!isRunning() || familyId < 0)
            return false;

        try {
            int payloadLen = GENL_HDRLEN + (hasUid ? (NLA_HDRLEN + 4) : 0);
            int total = NLMSG_HDRLEN + payloadLen;

            byte[] bytes = new byte[total];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.order(ByteOrder.nativeOrder());

            byteBuffer.putInt(total);
            byteBuffer.putShort((short) familyId);
            byteBuffer.putShort(NLM_F_REQUEST);
            byteBuffer.putInt(1);
            byteBuffer.putInt(0);

            byteBuffer.put(cmd);
            byteBuffer.put(GENL_VERSION);
            byteBuffer.putShort((short) 0);

            if (hasUid) {
                byteBuffer.putShort((short) (NLA_HDRLEN + 4));
                byteBuffer.putShort(REKERNEL_A_UID);
                byteBuffer.putInt(uid);
            }

            try {
                Os.write(fileDescriptor, bytes, 0, bytes.length);
                return true;
            } catch (ErrnoException ignored) {
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static boolean monitorNet(int uid) {
        Log.d("GenericNetlink monitorNet uid=" + uid);
        return sendCommand(REKERNEL_C_MONITOR_NET, true, uid);
    }

    public static boolean delMonitorNet(int uid) {
        Log.d("GenericNetlink delMonitorNet uid=" + uid);
        return sendCommand(REKERNEL_C_DEL_MONITOR_NET, true, uid);
    }

    private static boolean resolveFamily(FileDescriptor descriptor) {
        try {
            byte[] name = (GENL_FAMILY_NAME + "\u0000").getBytes(StandardCharsets.UTF_8);
            int attrLen = NLA_HDRLEN + name.length;
            int total = NLMSG_HDRLEN + GENL_HDRLEN + align4(attrLen);

            byte[] bytes = new byte[total];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            byteBuffer.order(ByteOrder.nativeOrder());

            byteBuffer.putInt(total);
            byteBuffer.putShort((short) GENL_ID_CTRL);
            byteBuffer.putShort(NLM_F_REQUEST);
            byteBuffer.putInt(1);
            byteBuffer.putInt(0);

            byteBuffer.put(CTRL_CMD_GETFAMILY);
            byteBuffer.put((byte) 1);
            byteBuffer.putShort((short) 0);

            byteBuffer.putShort((short) attrLen);
            byteBuffer.putShort(CTRL_ATTR_FAMILY_NAME);
            byteBuffer.put(name);

            Os.write(descriptor, bytes, 0, bytes.length);

            ByteBuffer reply = ByteBuffer.allocate(DEFAULT_RECV_BUFSIZE);
            int length = Os.read(descriptor, reply);
            if (length <= 0)
                return false;
            reply.order(ByteOrder.nativeOrder());

            return parseFamilyReply(reply, length);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean parseFamilyReply(ByteBuffer byteBuffer, int length) {
        if (length < NLMSG_HDRLEN + GENL_HDRLEN)
            return false;

        short nlmsgType = byteBuffer.getShort(4);
        if (nlmsgType != GENL_ID_CTRL)
            return false;

        int nlmsgLen = byteBuffer.getInt(0);
        int end = Math.min(nlmsgLen, length);
        int pos = NLMSG_HDRLEN + GENL_HDRLEN;

        int fId = -1;
        int grpId = -1;

        while (pos + NLA_HDRLEN <= end) {
            int nlaLen = byteBuffer.getShort(pos) & 0xFFFF;
            int nlaType = byteBuffer.getShort(pos + 2) & NLA_TYPE_MASK;
            if (nlaLen < NLA_HDRLEN)
                break;

            int dataPos = pos + NLA_HDRLEN;
            int dataLen = nlaLen - NLA_HDRLEN;

            if (nlaType == CTRL_ATTR_FAMILY_ID && dataLen >= 2) {
                fId = byteBuffer.getShort(dataPos) & 0xFFFF;
            } else if (nlaType == CTRL_ATTR_MCAST_GROUPS) {
                grpId = parseMcastGroups(byteBuffer, dataPos, dataPos + dataLen);
            }

            pos += align4(nlaLen);
        }

        if (fId < 0)
            return false;

        familyId = fId;
        mcastGroupId = grpId;
        Log.i("Generic Netlink Family解析成功, familyId=" + fId + ", mcastGroupId=" + grpId);
        return true;
    }

    private static int parseMcastGroups(ByteBuffer byteBuffer, int start, int end) {
        int pos = start;
        while (pos + NLA_HDRLEN <= end) {
            int outerLen = byteBuffer.getShort(pos) & 0xFFFF;
            if (outerLen < NLA_HDRLEN)
                break;

            int innerStart = pos + NLA_HDRLEN;
            int innerEnd = Math.min(pos + outerLen, end);

            String name = null;
            int id = -1;

            int ip = innerStart;
            while (ip + NLA_HDRLEN <= innerEnd) {
                int aLen = byteBuffer.getShort(ip) & 0xFFFF;
                int aType = byteBuffer.getShort(ip + 2) & NLA_TYPE_MASK;
                if (aLen < NLA_HDRLEN)
                    break;

                int aData = ip + NLA_HDRLEN;
                int aDataLen = aLen - NLA_HDRLEN;

                if (aType == CTRL_ATTR_MCAST_GRP_NAME) {
                    name = readString(byteBuffer, aData, aDataLen);
                } else if (aType == CTRL_ATTR_MCAST_GRP_ID && aDataLen >= 4) {
                    id = byteBuffer.getInt(aData);
                }

                ip += align4(aLen);
            }

            if (GENL_MCGRP_NAME.equals(name))
                return id;

            pos += align4(outerLen);
        }
        return -1;
    }

    private static String extractEvent(ByteBuffer byteBuffer, int length) {
        if (length < NLMSG_HDRLEN + GENL_HDRLEN)
            return null;

        short nlmsgType = byteBuffer.getShort(4);
        if (nlmsgType < NLMSG_MIN_TYPE)
            return null;

        int genlCmd = byteBuffer.get(NLMSG_HDRLEN) & 0xFF;
        if (genlCmd != REKERNEL_C_EVENT)
            return null;

        int nlmsgLen = byteBuffer.getInt(0);
        int end = Math.min(nlmsgLen, length);
        int pos = NLMSG_HDRLEN + GENL_HDRLEN;

        while (pos + NLA_HDRLEN <= end) {
            int nlaLen = byteBuffer.getShort(pos) & 0xFFFF;
            int nlaType = byteBuffer.getShort(pos + 2) & NLA_TYPE_MASK;
            if (nlaLen < NLA_HDRLEN)
                break;

            int dataPos = pos + NLA_HDRLEN;
            int dataLen = nlaLen - NLA_HDRLEN;

            if (nlaType == REKERNEL_A_MSG)
                return readString(byteBuffer, dataPos, dataLen);

            pos += align4(nlaLen);
        }
        return null;
    }

    private static String readString(ByteBuffer byteBuffer, int dataPos, int dataLen) {
        int strLen = dataLen;
        while (strLen > 0 && byteBuffer.get(dataPos + strLen - 1) == 0)
            strLen--;
        byte[] out = new byte[strLen];
        for (int i = 0; i < strLen; i++)
            out[i] = byteBuffer.get(dataPos + i);
        return new String(out, StandardCharsets.UTF_8);
    }

    public static void start(ClassLoader classLoader, Runnable onConnected, Runnable onFailed) {
        if (isRunning() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return;

        executorService.execute(() -> {
            try {
                Log.i("正在尝试Generic Netlink连接");

                FileDescriptor descriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, NETLINK_GENERIC);

                Class<?> libcore = CakeReflection.findClass("libcore.io.Libcore", classLoader);
                Object os = CakeReflection.getStaticObjectField(libcore, "os");
                CakeReflection.callMethod(os, "setsockoptInt", descriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_RECV_BUFSIZE);

                if (!descriptor.valid()) {
                    IoUtils.closeQuietly(classLoader, descriptor);
                    Log.w("Generic Netlink初始化失败");
                    if (onFailed != null) onFailed.run();
                    return;
                }

                Os.bind(descriptor, (SocketAddress) CakeReflection.newInstance(CakeReflection.findClass("android.system.NetlinkSocketAddress", classLoader), 0, 0));

                if (!resolveFamily(descriptor)) {
                    IoUtils.closeQuietly(classLoader, descriptor);
                    Log.w("Generic Netlink Family解析失败, 回退至Legacy");
                    LegacyReKernel.start(classLoader, onConnected, onFailed);
                    return;
                }

                if (mcastGroupId > 0)
                    CakeReflection.callMethod(os, "setsockoptInt", descriptor, SOL_NETLINK, NETLINK_ADD_MEMBERSHIP, mcastGroupId);

                fileDescriptor = descriptor;
                Log.i("Generic Netlink连接成功");
                StatusBinderHub.setSignal("available_rekernel", "1");
                if (onConnected != null) onConnected.run();
                StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel");

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

                while (true) {
                    try {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(DEFAULT_RECV_BUFSIZE);
                        int length = Os.read(descriptor, byteBuffer);
                        if (length == DEFAULT_RECV_BUFSIZE)
                            Log.w("Generic Netlink读取缓冲区已满");
                        byteBuffer.order(ByteOrder.nativeOrder());
                        String data = extractEvent(byteBuffer, length);
                        if (data != null && !data.isEmpty())
                            ReKernel.onEvent(data);
                    } catch (ErrnoException | StringIndexOutOfBoundsException | InterruptedIOException | NumberFormatException ignored) {
                    } catch (Exception e) {
                        Log.e("Generic Netlink接收消息失败", e);
                    }
                }
            } catch (Exception e) {
                Log.w("Generic Netlink连接失败", e);
                if (onFailed != null) onFailed.run();
            }
        });
    }
}
