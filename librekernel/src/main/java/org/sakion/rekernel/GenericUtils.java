package org.sakion.rekernel;

import android.system.ErrnoException;
import android.system.Os;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

class GenericUtils {
    private GenericUtils() {}

    static final int SOCKET_RECV_BUFSIZE = 64 * 1024;
    static final int DEFAULT_RECV_BUFSIZE = 8 * 1024;

    static final int NETLINK_GENERIC = 16;
    static final int SOL_NETLINK = 270;
    static final int NETLINK_ADD_MEMBERSHIP = 1;
    static final int GENL_ID_CTRL = 16;       // == NLMSG_MIN_TYPE, the "nlctrl" family
    static final int NLMSG_MIN_TYPE = 0x10;
    static final short NLM_F_REQUEST = 0x01;

    // Structural constants live in Netlink (the Android-free wire layer);
    // re-exported here so existing `import static ...GenericUtils.*` keep working.
    static final int NLMSG_HDRLEN = NetlinkUtils.NLMSG_HDRLEN;  // struct nlmsghdr
    static final int GENL_HDRLEN = NetlinkUtils.GENL_HDRLEN;    // struct genlmsghdr
    static final int NLA_HDRLEN = NetlinkUtils.NLA_HDRLEN;      // struct nlattr

    static final byte CTRL_CMD_GETFAMILY = 3;
    static final short CTRL_ATTR_FAMILY_ID = 1;
    static final short CTRL_ATTR_FAMILY_NAME = 2;
    static final short CTRL_ATTR_MCAST_GROUPS = 7;
    static final short CTRL_ATTR_MCAST_GRP_NAME = 1;
    static final short CTRL_ATTR_MCAST_GRP_ID = 2;

    static final String GENL_FAMILY_NAME = "rekernel";
    static final String GENL_MCGRP_NAME = "events";
    static final byte GENL_VERSION = 1;
    static final byte REKERNEL_C_EVENT = 1;                 // kernel -> user
    static final byte REKERNEL_C_ADD_MONITOR_NET = 2;       // user -> kernel: add uid
    static final byte REKERNEL_C_DEL_MONITOR_NET = 3;       // user -> kernel: remove uid
    static final byte REKERNEL_C_KILL_NET = 4;              // user -> kernel: kill a pid's TCP/UDP sockets
    static final byte REKERNEL_C_GET_VERSION = 5;           // user -> kernel: query version (kernel replies unicast REKERNEL_A_MSG)
    static final short REKERNEL_A_MSG = 1;
    static final short REKERNEL_A_UID = 2;
    static final short REKERNEL_A_PID = 3;

    static volatile int familyId = -1;       // genl family id ("rekernel")
    static volatile int mcastGroupId = -1;    // genl multicast group id ("events")

    public static volatile String lastResolveError = null;

    static void closeAndSignalBlockedThreads(FileDescriptor fd) throws IOException {
        if (fd == null)
            return;

        try {
            Os.close(fd);
        } catch (ErrnoException errnoException) {
            IOException exception = new IOException(errnoException.getMessage());
            exception.initCause(errnoException);
            throw exception;
        }
    }

    static boolean resolveFamily(FileDescriptor descriptor) {
        lastResolveError = null;
        try {
            byte[] name = (GENL_FAMILY_NAME + "\u0000").getBytes(StandardCharsets.UTF_8);
            int total = NLMSG_HDRLEN + GENL_HDRLEN + NetlinkUtils.align4(NLA_HDRLEN + name.length);

            ByteBuffer byteBuffer = NetlinkUtils.nlBuf(total);
            NetlinkUtils.putNlMsgHdr(byteBuffer, total, GENL_ID_CTRL, NLM_F_REQUEST, 1, 0);
            NetlinkUtils.putGenlHdr(byteBuffer, CTRL_CMD_GETFAMILY, 1);
            NetlinkUtils.putAttrBytes(byteBuffer, CTRL_ATTR_FAMILY_NAME, name);

            Os.write(descriptor, byteBuffer.array(), 0, total);

            ByteBuffer reply = ByteBuffer.allocate(DEFAULT_RECV_BUFSIZE);
            int length = Os.read(descriptor, reply);
            if (length <= 0) {
                lastResolveError = "family回复为空";
                return false;
            }
            reply.order(ByteOrder.nativeOrder());

            return parseFamilyReply(reply, length);
        } catch (Throwable _) {
            lastResolveError = "family解析异常";
            return false;
        }
    }

    static boolean parseFamilyReply(ByteBuffer byteBuffer, int length) {
        if (length < NLMSG_HDRLEN + GENL_HDRLEN)
            return false;

        int nlmsgLen = byteBuffer.getInt(0);
        short nlmsgType = byteBuffer.getShort(4);
        if (nlmsgType != GENL_ID_CTRL)
            return false;

        int end = Math.min(nlmsgLen, length);
        int fId = -1;
        int grpId = -1;

        NetlinkUtils.AttrCursor attr = new NetlinkUtils.AttrCursor(byteBuffer, NLMSG_HDRLEN + GENL_HDRLEN, end);
        while (attr.next()) {
            if (attr.type == CTRL_ATTR_FAMILY_ID && attr.dataLen >= 2)
                fId = byteBuffer.getShort(attr.dataPos) & 0xFFFF;
            else if (attr.type == CTRL_ATTR_MCAST_GROUPS)
                grpId = parseMcastGroups(byteBuffer, attr.dataPos, attr.dataPos + attr.dataLen);
        }

        if (fId < 0)
            return false;

        familyId = fId;
        mcastGroupId = grpId;
        return true;
    }

    static int parseMcastGroups(ByteBuffer byteBuffer, int start, int end) {
        // Outer attrs are index-keyed group entries (type ignored); each nests name/id attrs.
        NetlinkUtils.AttrCursor group = new NetlinkUtils.AttrCursor(byteBuffer, start, end);
        while (group.next()) {
            String name = null;
            int id = -1;

            NetlinkUtils.AttrCursor attr = new NetlinkUtils.AttrCursor(
                    byteBuffer, group.dataPos, Math.min(group.dataPos + group.dataLen, end));
            while (attr.next()) {
                if (attr.type == CTRL_ATTR_MCAST_GRP_NAME)
                    name = NetlinkUtils.readString(byteBuffer, attr.dataPos, attr.dataLen);
                else if (attr.type == CTRL_ATTR_MCAST_GRP_ID && attr.dataLen >= 4)
                    id = byteBuffer.getInt(attr.dataPos);
            }

            if (GENL_MCGRP_NAME.equals(name))
                return id;
        }
        return -1;
    }

    static long extractEventRegion(ByteBuffer byteBuffer, int length) {
        return findMsg(byteBuffer, length, REKERNEL_C_EVENT);
    }

    static String extractVersion(ByteBuffer byteBuffer, int length) {
        long region = findMsg(byteBuffer, length, REKERNEL_C_GET_VERSION);
        if (region < 0)
            return null;
        return new String(byteBuffer.array(), (int) (region >>> 32), (int) region, StandardCharsets.UTF_8);
    }

    private static long findMsg(ByteBuffer byteBuffer, int length, int expectedCmd) {
        if (length < NLMSG_HDRLEN + GENL_HDRLEN)
            return -1;

        int nlmsgLen = byteBuffer.getInt(0);
        short nlmsgType = byteBuffer.getShort(4);
        if (nlmsgType < NLMSG_MIN_TYPE) // NLMSG_ERROR / NLMSG_DONE / control
            return -1;

        int genlCmd = byteBuffer.get(NLMSG_HDRLEN) & 0xFF;
        if (genlCmd != expectedCmd)
            return -1;

        int end = Math.min(nlmsgLen, length);
        int pos = NLMSG_HDRLEN + GENL_HDRLEN;
        while (pos + NLA_HDRLEN <= end) {
            int nlaLen = byteBuffer.getShort(pos) & 0xFFFF;
            if (nlaLen < NLA_HDRLEN)
                break;
            if ((byteBuffer.getShort(pos + 2) & NetlinkUtils.NLA_TYPE_MASK) == REKERNEL_A_MSG) {
                int dataPos = pos + NLA_HDRLEN;
                int n = nlaLen - NLA_HDRLEN;
                while (n > 0 && byteBuffer.get(dataPos + n - 1) == 0) // trim trailing NULs
                    n--;
                return ((long) dataPos << 32) | n;
            }
            pos += NetlinkUtils.align4(nlaLen);
        }
        return -1;
    }

    static int StringToInteger(String str) {
        if (str == null || str.isEmpty())
            return -1;

        return parseDigits(str, 0, str.length());
    }

    static int parseDigits(CharSequence s, int from, int to) {
        long acc = 0;
        boolean any = false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                acc = acc * 10 + (c - '0');
                any = true;
                if (acc > Integer.MAX_VALUE)
                    return -1;
            }
        }
        return any ? (int) acc : -1;
    }
}
