package org.sakion.rekernel;

import static org.sakion.rekernel.GenericUtils.DEFAULT_RECV_BUFSIZE;
import static org.sakion.rekernel.GenericUtils.GENL_HDRLEN;
import static org.sakion.rekernel.GenericUtils.GENL_VERSION;
import static org.sakion.rekernel.GenericUtils.NETLINK_ADD_MEMBERSHIP;
import static org.sakion.rekernel.GenericUtils.NETLINK_GENERIC;
import static org.sakion.rekernel.GenericUtils.NLA_HDRLEN;
import static org.sakion.rekernel.GenericUtils.NLMSG_HDRLEN;
import static org.sakion.rekernel.GenericUtils.NLM_F_REQUEST;
import static org.sakion.rekernel.GenericUtils.REKERNEL_A_PID;
import static org.sakion.rekernel.GenericUtils.REKERNEL_A_UID;
import static org.sakion.rekernel.GenericUtils.REKERNEL_C_ADD_MONITOR_NET;
import static org.sakion.rekernel.GenericUtils.REKERNEL_C_DEL_MONITOR_NET;
import static org.sakion.rekernel.GenericUtils.REKERNEL_C_GET_VERSION;
import static org.sakion.rekernel.GenericUtils.REKERNEL_C_KILL_NET;
import static org.sakion.rekernel.GenericUtils.SOCKET_RECV_BUFSIZE;
import static org.sakion.rekernel.GenericUtils.SOL_NETLINK;
import static org.sakion.rekernel.GenericUtils.StringToInteger;
import static org.sakion.rekernel.GenericUtils.extractEventRegion;
import static org.sakion.rekernel.GenericUtils.extractVersion;
import static org.sakion.rekernel.GenericUtils.familyId;
import static org.sakion.rekernel.GenericUtils.mcastGroupId;
import static org.sakion.rekernel.GenericUtils.resolveFamily;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ReKernel {
    private ReKernel() {}

    private static final HandlerThread THREAD = create();
    private static HandlerThread create() {
        HandlerThread t = new HandlerThread("Re-Kernel");
        t.start();
        return t;
    }
    private static final Handler HANDLER = new Handler(THREAD.getLooper());

    public static volatile String lastError = null;

    // --- Public forwarding methods ---

    public static boolean isRunning() {
        return Kernel.isRunning();
    }

    public static boolean isLegacy() {
        return Kernel.isLegacy();
    }

    public static boolean isDefaultUnit() {
        return Kernel.isDefaultUnit();
    }

    public static boolean addMonitorNet(int uid) {
        return Kernel.addMonitorNet(uid);
    }

    public static boolean delMonitorNet(int uid) {
        return Kernel.delMonitorNet(uid);
    }

    public static boolean destroySocket(int pid) {
        return Kernel.destroySocket(pid);
    }

    public static int getMajorVersion() {
        return Kernel.getMajorVersion();
    }

    public static int getMinorVersion() {
        return Kernel.getMinorVersion();
    }

    public static String getVersion() {
        return Kernel.getVersion();
    }

    public static int registerListener(Callback callback, boolean searchNetlinkUnit, int chooseNetlinkUnit) {
        return Kernel.registerListener(callback, searchNetlinkUnit, chooseNetlinkUnit);
    }

    public static void unregisterListener() {
        Kernel.unregisterListener();
    }

    // --- eBPF mode (unix socket) ---

    public static boolean eBPFisRunning() {
        return eBPF.isRunning();
    }

    public static String eBPFgetVersion() {
        return eBPF.getVersion();
    }

    public static boolean eBPFregisterListener(Callback callback) {
        return eBPF.registerListener(callback);
    }

    public static void eBPFunregisterListener() {
        eBPF.unregisterListener();
    }

    public static boolean eBPFaddMonitorNet(int uid) {
        return eBPF.addMonitorNet(uid);
    }

    public static boolean eBPFdelMonitorNet(int uid) {
        return eBPF.delMonitorNet(uid);
    }

    public static String probeAvailability(int chooseNetlinkUnit) {
        boolean hasKernel = Kernel.probeAvailability(true, chooseNetlinkUnit);
        boolean hasEbpf = eBPF.probeAvailability();
        if (hasKernel && hasEbpf)
            return "kernel,ebpf";
        if (hasKernel)
            return "kernel";
        if (hasEbpf)
            return "ebpf";
        return "0";
    }

    // --- Resolver ---

    private static void resolver(Callback.Category category, AsciiView data, Callback callback) {
        int indexOf = data.indexOf("type");
        int lastIndexOf = data.lastIndexOf(';');
        if (indexOf < 0 || lastIndexOf < 0 || indexOf > lastIndexOf)
            return;

        KvScan kv = data.scanner(indexOf, lastIndexOf);

        if (!kv.has("type")) {
            postException(callback, "Unknown type: null");
            return;
        }

        if (kv.valueIs("Binder")) {
            int found = Callback.BINDER_UNKNOWN;
            if (kv.has("bindertype")) {
                if (kv.valueIs("transaction")) found = Callback.BINDER_TRANSACTION;
                else if (kv.valueIs("reply")) found = Callback.BINDER_REPLY;
                else if (kv.valueIs("free_buffer_full")) found = Callback.BINDER_FREE_BUFFER_FULL;
            }
            if (found == Callback.BINDER_UNKNOWN)
                postException(callback, "Unknown binder type: " + kv.get("bindertype"));
            final int binderType = found;
            boolean oneway = kv.getInt("oneway") == 1;
            int fromPid = kv.getInt("from_pid");
            int fromUid = kv.getInt("from");
            int targetPid = kv.getInt("target_pid");
            int targetUid = kv.getInt("target");
            String rpcName = kv.getOrEmpty("rpc_name");
            int code = kv.getInt("code");
            // ponytail: one small capture-lambda per event remains; a Message-style recycler
            // pool could kill it if allocation profiling ever cares (it won't at these rates).
            HANDLER.post(() -> callback.binder(binderType, oneway, fromUid, fromPid, targetUid, targetPid, rpcName, code));
        } else if (kv.valueIs("Signal")) {
            int targetPid = kv.getInt("dst_pid");
            int targetUid = kv.getInt("dst");
            int killerPid = kv.getInt("killer_pid");
            int killerUid = kv.getInt("killer");
            int signal = kv.getInt("signal");
            HANDLER.post(() -> callback.signal(signal, killerUid, killerPid, targetUid, targetPid));
        } else if (kv.valueIs("Network")) {
            int targetUid = kv.getInt("target");
            int found = Callback.PROTO_UNKNOWN;
            if (kv.has("proto")) {
                if (kv.valueIs("ipv4")) found = Callback.PROTO_IPV4;
                else if (kv.valueIs("ipv6")) found = Callback.PROTO_IPV6;
                else postException(callback, "Unknown proto: " + kv.get("proto"));
            }
            final int proto = found;
            int dataLen = kv.has("data_len") ? kv.valueInt() : Callback.DATA_LEN_UNKNOWN;
            HANDLER.post(() -> callback.network(proto, targetUid, dataLen));
        } else if (kv.valueIs("Version")) {
            if (category == Callback.Category.eBPF) {
                // eBPF only
                eBPF.version = kv.get("version");
                synchronized (eBPF.versionLock) {
                    eBPF.versionLock.notifyAll();
                }
            } else postException(callback, "Unknown type: " + kv.get("type"));
        } else {
            postException(callback, "Unknown type: " + kv.get("type"));
        }
    }

    private static void postException(Callback callback, String message) {
        IllegalStateException e = new ParseDiagnostic(message);
        HANDLER.post(() -> callback.exception(e));
    }

    private static final class ParseDiagnostic extends IllegalStateException {
        ParseDiagnostic(String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class AsciiView implements CharSequence {
        private ByteBuffer buf;
        private int off, len;
        private byte[] scratch = new byte[64];
        private final KvScan kv = new KvScan(this);

        AsciiView set(ByteBuffer buf, int off, int len) {
            this.buf = buf;
            this.off = off;
            this.len = len;
            return this;
        }

        KvScan scanner(int start, int end) {
            return kv.reset(start, end);
        }

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int index) {
            return (char) (buf.get(off + index) & 0xFF);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return substring(start, end);
        }

        @Override
        public String toString() {
            return substring(0, len);
        }

        String substring(int start, int end) {
            int n = end - start;
            if (scratch.length < n)
                scratch = new byte[n];
            for (int i = 0; i < n; i++)
                scratch[i] = buf.get(off + start + i);
            return new String(scratch, 0, n, StandardCharsets.UTF_8);
        }

        int indexOf(String needle) {
            int n = needle.length();
            outer:
            for (int i = 0; i + n <= len; i++) {
                for (int k = 0; k < n; k++)
                    if (charAt(i + k) != needle.charAt(k))
                        continue outer;
                return i;
            }
            return -1;
        }

        int lastIndexOf(char c) {
            for (int i = len - 1; i >= 0; i--)
                if (charAt(i) == c)
                    return i;
            return -1;
        }
    }

    private static final class KvScan {
        private final AsciiView s;
        private int start, end;
        private int vStart, vEnd;

        KvScan(AsciiView s) {
            this.s = s;
        }

        KvScan reset(int start, int end) {
            this.start = start;
            this.end = end;
            return this;
        }

        boolean has(String key) {
            boolean found = false;
            int segStart = start, eq = -1, eqCount = 0;
            for (int i = start; i <= end; i++) {
                char c = i < end ? s.charAt(i) : ',';
                if (c == ',') {
                    if (eqCount == 1 && eq + 1 < i && keyEquals(segStart, eq, key)) {
                        int a = eq + 1, b = i;
                        while (a < b && s.charAt(a) <= ' ') a++;
                        while (b > a && s.charAt(b - 1) <= ' ') b--;
                        vStart = a;
                        vEnd = b;
                        found = true;
                    }
                    segStart = i + 1;
                    eq = -1;
                    eqCount = 0;
                } else if (c == '=' && eqCount++ == 0) {
                    eq = i;
                }
            }
            return found;
        }

        private boolean keyEquals(int a, int b, String key) {
            while (a < b && s.charAt(a) <= ' ') a++;
            while (b > a && s.charAt(b - 1) <= ' ') b--;
            if (b - a != key.length())
                return false;
            for (int k = 0; k < key.length(); k++)
                if (s.charAt(a + k) != key.charAt(k))
                    return false;
            return true;
        }

        boolean valueIs(String expected) {
            int n = vEnd - vStart;
            if (n != expected.length())
                return false;
            for (int k = 0; k < n; k++)
                if (s.charAt(vStart + k) != expected.charAt(k))
                    return false;
            return true;
        }

        int valueInt() {
            return GenericUtils.parseDigits(s, vStart, vEnd);
        }

        String get(String key) {
            return has(key) ? s.substring(vStart, vEnd) : null;
        }

        String getOrEmpty(String key) {
            return has(key) ? s.substring(vStart, vEnd) : "";
        }

        int getInt(String key) {
            return has(key) ? valueInt() : -1;
        }
    }

    // --- eBPF nested class ---

    public static class eBPF {
        private static final String SOCKET_NAME = "rekernel";

        private static final AtomicReference<LocalSocket> socketRef = new AtomicReference<>(null);
        private static volatile OutputStream out = null;
        private static volatile Callback cacheCallback = null;
        private static final Object writeLock = new Object();

        private static volatile String version = null;
        private static final Object versionLock = new Object();

        public static boolean isRunning() {
            LocalSocket socket = socketRef.get();
            return socket != null && socket.isConnected();
        }

        public static String getVersion() {
            if (version != null)
                return version;

            if (!isRunning())
                return null;

            sendCommand("GET_VERSION");
            synchronized (versionLock) {
                long deadline = System.currentTimeMillis() + 1000;
                while (version == null) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        lastError = "eBPF版本查询超时";
                        break;
                    }
                    try {
                        versionLock.wait(wait);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return version;
        }

        private static boolean sendCommand(String command) {
            if (!isRunning() || out == null) {
                lastError = "eBPF未连接: 无法发送命令 " + command;
                return false;
            }

            try {
                byte[] bytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
                synchronized (writeLock) {
                    out.write(bytes);
                    out.flush();
                }
                return true;
            } catch (Throwable e) {
                lastError = "eBPF命令发送失败 " + command + ": " + e.getClass().getSimpleName();
                return false;
            }
        }

        public static boolean addMonitorNet(int uid) {
            return sendCommand("ADD_MONITOR_NET " + uid);
        }

        public static boolean delMonitorNet(int uid) {
            return sendCommand("DEL_MONITOR_NET " + uid);
        }

        private static void readLoop(LocalSocket s, Callback callback) {
            try {
                FileDescriptor fd = s.getFileDescriptor();
                ByteBuffer acc = ByteBuffer.allocateDirect(DEFAULT_RECV_BUFSIZE);
                int scanned = 0;
                AsciiView view = new AsciiView();
                while (true) {
                    int n = Os.read(fd, acc);
                    if (n <= 0)
                        break;
                    int accLen = acc.position();
                    int lineStart = 0;
                    for (int i = scanned; i < accLen; i++) {
                        if (acc.get(i) == '\n') {
                            if (i > lineStart)
                                resolver(Callback.Category.eBPF, view.set(acc, lineStart, i - lineStart), callback);
                            lineStart = i + 1;
                        }
                    }
                    if (lineStart > 0) {
                        acc.position(lineStart);
                        acc.limit(accLen);
                        acc.compact();
                    }
                    scanned = acc.position();
                    if (!acc.hasRemaining()) {
                        ByteBuffer bigger = ByteBuffer.allocateDirect(acc.capacity() * 2);
                        acc.flip();
                        bigger.put(acc);
                        acc = bigger;
                    }
                }
                teardown(callback, null);
            } catch (Exception e) {
                lastError = "eBPF读异常: " + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                teardown(callback, e);
            }
        }

        private static void teardown(Callback cb, Exception readError) {
            LocalSocket s = socketRef.getAndSet(null);
            if (s == null)
                return;
            out = null;
            version = null;
            try {
                s.shutdownInput();
            } catch (Throwable _) {
            }
            try {
                s.close();
            } catch (Throwable _) {
            }
            if (cb != null) {
                if (readError != null)
                    HANDLER.post(() -> cb.exception(readError));
                HANDLER.post(() -> cb.disconnected(Callback.Category.eBPF));
            }
        }

        public static boolean registerListener(Callback callback) {
            if (isRunning() || callback == null) {
                lastError = "eBPF已在运行";
                return false;
            }

            LocalSocket s = null;
            try {
                s = new LocalSocket(LocalSocket.SOCKET_STREAM);
                s.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
                if (!s.isConnected()) {
                    lastError = "eBPF连接失败: socket未连接";
                    s.close();
                    return false;
                }

                lastError = null;
                out = s.getOutputStream();
                cacheCallback = callback;
                socketRef.set(s);

                final LocalSocket fs = s;
                Thread reader = new Thread(() -> readLoop(fs, callback), "Re-Kernel-Reader");
                reader.setDaemon(true);
                reader.start();
                return true;
            } catch (Throwable e) {
                lastError = "eBPF连接异常: " + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                try {
                    if (s != null)
                        s.close();
                } catch (Throwable _) {
                }
                socketRef.set(null);
                out = null;
            }

            return false;
        }

        public static boolean probeAvailability() {
            LocalSocket s = null;
            try {
                s = new LocalSocket(LocalSocket.SOCKET_STREAM);
                s.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
                return s.isConnected();
            } catch (Throwable _) {
                return false;
            } finally {
                try {
                    if (s != null)
                        s.close();
                } catch (Throwable _) {
                }
            }
        }

        public static void unregisterListener() {
            Callback cb = cacheCallback;
            cacheCallback = null;
            teardown(cb, null);
        }
    }

    // --- Kernel nested class ---

    public static class Kernel {
        private static final AtomicBoolean running = new AtomicBoolean(false);
        private static String version = null;
        private static int versionMajor = -1;
        private static int versionMinor = -1;
        private static FileDescriptor fileDescriptor = null;
        private static Callback cacheCallback = null;

        // --- Legacy ---
        private static boolean legacy = false;
        private static boolean defaultUnit = false;
        private static final int NETLINK_UNIT_DEFAULT = 22;
        private static final int NETLINK_UNIT_MAX = 26;
        private static final int USER_PORT = 100;
        private static final int LEGACY_MSG_TYPE = 0x11;
        // --------------

        private static volatile Thread readerThread = null;

        public static boolean isRunning() {
            return fileDescriptor != null && fileDescriptor.valid();
        }

        public static boolean isLegacy() {
            return legacy;
        }

        public static boolean isDefaultUnit() {
            return defaultUnit;
        }

        private static boolean sendCommand(byte cmd, boolean hasAttr, int attrType, int value) {
            if (!isRunning() || familyId < 0)
                return false;

            try {
                int total = NLMSG_HDRLEN + GENL_HDRLEN + (hasAttr ? (NLA_HDRLEN + 4) : 0);

                ByteBuffer byteBuffer = NetlinkUtils.nlBuf(total);
                NetlinkUtils.putNlMsgHdr(byteBuffer, total, familyId, NLM_F_REQUEST, 1, 0);
                NetlinkUtils.putGenlHdr(byteBuffer, cmd, GENL_VERSION);
                if (hasAttr)
                    NetlinkUtils.putAttrU32(byteBuffer, attrType, value);

                try {
                    Os.write(fileDescriptor, byteBuffer.array(), 0, total);
                    return true;
                } catch (ErrnoException _) {
                }
            } catch (Throwable _) {
            }

            return false;
        }

        private static boolean sendLegacyCommand(int cmdType, int value) {
            if (!isRunning() || defaultUnit)
                return false;

            try {
                int total = NLMSG_HDRLEN + 8;
                ByteBuffer byteBuffer = NetlinkUtils.nlBuf(total);
                NetlinkUtils.putNlMsgHdr(byteBuffer, total, LEGACY_MSG_TYPE, NLM_F_REQUEST, 1, USER_PORT);
                byteBuffer.putInt(cmdType);
                byteBuffer.putInt(value);

                try {
                    Os.write(fileDescriptor, byteBuffer.array(), 0, total);
                    return true;
                } catch (ErrnoException _) {
                }
            } catch (Throwable _) {
            }

            return false;
        }

        public static boolean addMonitorNet(int uid) {
            if (!isRunning())
                return false;

            if (legacy)
                return sendLegacyCommand(2, uid);

            return sendCommand(REKERNEL_C_ADD_MONITOR_NET, true, REKERNEL_A_UID, uid);
        }

        public static boolean delMonitorNet(int uid) {
            if (!isRunning() || getMajorVersion() < 10)
                return false;

            if (legacy)
                return sendLegacyCommand(3, uid);

            return sendCommand(REKERNEL_C_DEL_MONITOR_NET, true, REKERNEL_A_UID, uid);
        }

        private static void setVersion(String v) {
            version = v;
            int major = -1, minor = -1;
            if (v != null) {
                int dot = v.indexOf('.');
                if (dot < 0) {
                    major = StringToInteger(v);
                } else {
                    major = GenericUtils.parseDigits(v, 0, dot);
                    int dot2 = v.indexOf('.', dot + 1);
                    minor = GenericUtils.parseDigits(v, dot + 1, dot2 < 0 ? v.length() : dot2);
                }
            }
            versionMajor = major;
            versionMinor = minor;
        }

        public static int getMajorVersion() {
            return versionMajor;
        }

        public static int getMinorVersion() {
            return versionMinor;
        }

        public static boolean destroySocket(int pid) {
            if (!isRunning() || getMajorVersion() < 10)
                return false;

            if (legacy)
                return sendLegacyCommand(4, pid);

            return sendCommand(REKERNEL_C_KILL_NET, true, REKERNEL_A_PID, pid);
        }

        private static String readVersion() {
            FileDescriptor descriptor = null;
            try {
                descriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, NETLINK_GENERIC);
                Os.bind(descriptor, (SocketAddress) HiddenApiBypass.newInstance(Class.forName("android.system.NetlinkSocketAddress"), 0, 0));

                if (familyId < 0 && !resolveFamily(descriptor))
                    return null;

                int total = NLMSG_HDRLEN + GENL_HDRLEN;
                ByteBuffer request = NetlinkUtils.nlBuf(total);
                NetlinkUtils.putNlMsgHdr(request, total, familyId, NLM_F_REQUEST, 1, 0);
                NetlinkUtils.putGenlHdr(request, REKERNEL_C_GET_VERSION, GENL_VERSION);
                Os.write(descriptor, request.array(), 0, total);

                ByteBuffer reply = ByteBuffer.allocate(DEFAULT_RECV_BUFSIZE);
                int length = Os.read(descriptor, reply);
                reply.order(ByteOrder.nativeOrder());
                return extractVersion(reply, length);
            } catch (Throwable e) {
                lastError = "版本查询异常: " + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                return null;
            } finally {
                try {
                    GenericUtils.closeAndSignalBlockedThreads(descriptor);
                } catch (Throwable _) {
                }
            }
        }

        public static String getVersion() {
            return version;
        }

        public static boolean probeAvailability(boolean searchNetlinkUnit, int chooseNetlinkUnit) {
            return probeGenericAvailability() || probeLegacyAvailability(searchNetlinkUnit, chooseNetlinkUnit);
        }

        private static boolean probeGenericAvailability() {
            FileDescriptor descriptor = null;
            try {
                descriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, NETLINK_GENERIC);
                Os.setsockoptInt(descriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_RECV_BUFSIZE);
                if (!descriptor.valid())
                    return false;
                Os.bind(descriptor, (SocketAddress) HiddenApiBypass.newInstance(Class.forName("android.system.NetlinkSocketAddress"), 0, 0));
                return resolveFamily(descriptor);
            } catch (Throwable _) {
                return false;
            } finally {
                try {
                    GenericUtils.closeAndSignalBlockedThreads(descriptor);
                } catch (Throwable _) {
                }
            }
        }

        private static boolean probeLegacyAvailability(boolean searchNetlinkUnit, int chooseNetlinkUnit) {
            int netlinkUnit = resolveLegacyNetlinkUnit(searchNetlinkUnit, chooseNetlinkUnit);
            if (netlinkUnit < NETLINK_UNIT_DEFAULT || netlinkUnit > NETLINK_UNIT_MAX)
                return false;

            FileDescriptor descriptor = null;
            try {
                descriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, netlinkUnit);
                Os.setsockoptInt(descriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_RECV_BUFSIZE);
                if (!descriptor.valid())
                    return false;
                Os.bind(descriptor, (SocketAddress) HiddenApiBypass.newInstance(Class.forName("android.system.NetlinkSocketAddress"), USER_PORT, 0));
                return true;
            } catch (Throwable _) {
                return false;
            } finally {
                try {
                    GenericUtils.closeAndSignalBlockedThreads(descriptor);
                } catch (Throwable _) {
                }
            }
        }

        private static int resolveLegacyNetlinkUnit(boolean searchNetlinkUnit, int chooseNetlinkUnit) {
            if (chooseNetlinkUnit >= NETLINK_UNIT_DEFAULT && chooseNetlinkUnit <= NETLINK_UNIT_MAX && !searchNetlinkUnit)
                return chooseNetlinkUnit;
            if (!searchNetlinkUnit)
                return NETLINK_UNIT_DEFAULT;

            File dir = new File("/proc/rekernel");
            if (!dir.exists())
                return NETLINK_UNIT_DEFAULT;

            File[] files = dir.listFiles();
            if (files == null || files.length == 0)
                return -1;
            if (files.length == 1)
                return GenericUtils.StringToInteger(files[0].getName());
            if ("version".equals(files[0].getName()))
                return GenericUtils.StringToInteger(files[1].getName());
            return GenericUtils.StringToInteger(files[0].getName());
        }

        private static int startLegacy(Callback callback, boolean searchNetlinkUnit, int chooseNetlinkUnit) {
            lastError = null;
            try {
                int netlinkUnit;
                if (chooseNetlinkUnit >= NETLINK_UNIT_DEFAULT && chooseNetlinkUnit <= NETLINK_UNIT_MAX && !searchNetlinkUnit) {
                    lastError = "Legacy使用自定义netlink unit: " + chooseNetlinkUnit;
                    netlinkUnit = chooseNetlinkUnit;
                } else if (searchNetlinkUnit) {
                    File dir = new File("/proc/rekernel");
                    if (dir.exists()) {
                        File[] files = dir.listFiles();
                        if (files == null) {
                            lastError = "/proc/rekernel 目录无法读取";
                            return -1;
                        }
                        File file = files[0];
                        if (files.length == 1)
                            netlinkUnit = GenericUtils.StringToInteger(file.getName());
                        else if (file.getName().equals("version")) {
                            setVersion(Files.readAllLines(file.toPath()).get(0));
                            lastError = "Legacy从/proc/rekernel读取netlink unit: " + files[1].getName() + ", version=" + version;
                            netlinkUnit = GenericUtils.StringToInteger(files[1].getName());
                        } else {
                            setVersion(Files.readAllLines(files[1].toPath()).get(0));
                            lastError = "Legacy从/proc/rekernel读取netlink unit: " + file.getName() + ", version=" + version;
                            netlinkUnit = GenericUtils.StringToInteger(file.getName());
                        }
                    } else {
                        lastError = "Legacy使用默认netlink unit: " + NETLINK_UNIT_DEFAULT;
                        defaultUnit = true;
                        return -1;
                    }
                } else {
                    lastError = "Legacy使用默认netlink unit: " + NETLINK_UNIT_DEFAULT;
                    defaultUnit = true;
                    return -1;
                }

                FileDescriptor descriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, netlinkUnit);

                Os.setsockoptInt(descriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_RECV_BUFSIZE);

                if (!descriptor.valid()) {
                    lastError = "Legacy socket无效";
                    GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
                    return -1;
                }

                Os.bind(descriptor, (SocketAddress) HiddenApiBypass.newInstance(Class.forName("android.system.NetlinkSocketAddress"), 100, 0));

                fileDescriptor = descriptor;

                cacheCallback = callback;

                Thread reader = new Thread(() -> {
                    if (!running.get())
                        return;

                    if (!defaultUnit) {
                        try {
                            byte[] message = "#proc_remove\u0000".getBytes(StandardCharsets.UTF_8);
                            int total = NLMSG_HDRLEN + message.length;
                            ByteBuffer byteBuffer = NetlinkUtils.nlBuf(total);
                            NetlinkUtils.putNlMsgHdr(byteBuffer, total, LEGACY_MSG_TYPE, NLM_F_REQUEST, 1, USER_PORT);
                            byteBuffer.put(message);

                            try {
                                Os.write(descriptor, byteBuffer.array(), 0, total);
                            } catch (ErrnoException _) {
                            }
                        } catch (Throwable throwable) {
                            callback.exception(new IllegalStateException("FAILED_TO_SEND_MESSAGE_TO_RE_KERNEL_SERVER"));
                        }

                        try {
                            int total = NLMSG_HDRLEN + 4;
                            ByteBuffer byteBuffer = NetlinkUtils.nlBuf(total);
                            NetlinkUtils.putNlMsgHdr(byteBuffer, total, LEGACY_MSG_TYPE, NLM_F_REQUEST, 1, USER_PORT);
                            byteBuffer.putInt(1); // REMOVE_PROC CMD

                            try {
                                Os.write(descriptor, byteBuffer.array(), 0, total);
                            } catch (ErrnoException _) {
                            }
                        } catch (Throwable throwable) {
                            callback.exception(new IllegalStateException("FAILED_TO_SEND_MESSAGE_TO_RE_KERNEL_SERVER"));
                        }
                    }

                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(DEFAULT_RECV_BUFSIZE).order(ByteOrder.nativeOrder());
                    AsciiView view = new AsciiView();
                    while (running.get()) {
                        try {
                            byteBuffer.clear();
                            int length = Os.read(descriptor, byteBuffer);
                            if (length > 0)
                                resolver(Callback.Category.Legacy, view.set(byteBuffer, 0, length), callback);
                        } catch (ErrnoException e) {
                            if (!descriptor.valid() || e.errno == OsConstants.EBADF)
                                break;
                        } catch (StringIndexOutOfBoundsException | InterruptedIOException |
                                 NumberFormatException _) {
                        } catch (Exception e) {
                            callback.exception(e);
                            if (!running.get())
                                break;
                        }
                    }
                }, "Re-Kernel-Legacy");
                reader.setDaemon(true);
                reader.start();
                readerThread = reader;

                return defaultUnit ? -1 : netlinkUnit;
            } catch (Throwable t) {
                lastError = "Legacy模式启动异常: " + t.getClass().getSimpleName();
                String message = t.getMessage();
                if (message != null && !message.isEmpty()) {
                    lastError += ": " + message;
                }
                if (fileDescriptor != null) {
                    try {
                        GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
                    } catch (IOException _) {
                    }
                }
                running.set(false);
            }

            return -1;
        }

        public static int registerListener(Callback callback, boolean searchNetlinkUnit, int chooseNetlinkUnit) {
            if (callback == null) {
                lastError = "回调为空";
                return -1;
            }

            if (!running.compareAndSet(false, true)) {
                lastError = "已在运行";
                return -1;
            }

            lastError = null;
            try {
                FileDescriptor descriptor = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_DGRAM, NETLINK_GENERIC);

                Os.setsockoptInt(descriptor, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_RECV_BUFSIZE);

                if (!descriptor.valid()) {
                    lastError = "Generic socket无效";
                    GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
                    return -1;
                }

                Os.bind(descriptor, (SocketAddress) HiddenApiBypass.newInstance(Class.forName("android.system.NetlinkSocketAddress"), 0, 0));

                if (!resolveFamily(descriptor)) {
                    String resolveError = GenericUtils.lastResolveError;
                    lastError = "Generic family解析失败" + (resolveError != null ? ": " + resolveError : "");
                    GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
                    legacy = true;
                    return startLegacy(callback, searchNetlinkUnit, chooseNetlinkUnit);
                }

                if (mcastGroupId > 0)
                    Os.setsockoptInt(descriptor, SOL_NETLINK, NETLINK_ADD_MEMBERSHIP, mcastGroupId);

                fileDescriptor = descriptor;

                cacheCallback = callback;

                setVersion(readVersion());

                Thread reader = new Thread(() -> {
                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(DEFAULT_RECV_BUFSIZE).order(ByteOrder.nativeOrder());
                    AsciiView view = new AsciiView();
                    while (running.get()) {
                        try {
                            byteBuffer.clear();
                            int length = Os.read(descriptor, byteBuffer);
                            long region = extractEventRegion(byteBuffer, length);
                            if (region >= 0 && (int) region > 0)
                                resolver(Callback.Category.Generic, view.set(byteBuffer, (int) (region >>> 32), (int) region), callback);
                        } catch (ErrnoException e) {
                            if (!descriptor.valid() || e.errno == OsConstants.EBADF)
                                break;
                        } catch (StringIndexOutOfBoundsException | InterruptedIOException |
                                 NumberFormatException _) {
                        } catch (Exception e) {
                            callback.exception(e);
                            if (!running.get())
                                break;
                        }
                    }
                }, "Re-Kernel-Netlink");
                reader.setDaemon(true);
                reader.start();
                readerThread = reader;

                return 0;
            } catch (Throwable t) {
                lastError = "Generic模式启动异常: " + t.getClass().getSimpleName();
                String message = t.getMessage();
                if (message != null && !message.isEmpty()) {
                    lastError += ": " + message;
                }
                if (fileDescriptor != null) {
                    try {
                        GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
                    } catch (IOException _) {
                    }
                }
                running.set(false);
            }

            return -1;
        }

        public static void unregisterListener() {
            running.set(false);

            // 取出引用并立即清空静态状态，与 close() 是否抛异常无关
            Callback cb = cacheCallback;
            cacheCallback = null;
            Thread t = readerThread;
            readerThread = null;
            boolean wasLegacy = legacy;

            setVersion(null);

            // 关闭 fd，打断阻塞中的 Os.read()；即使抛异常也继续清理
            try {
                GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
            } catch (Throwable ignored) {
            }
            fileDescriptor = null;
            legacy = false;
            defaultUnit = false;

            // 等待 reader 线程真正退出（最多 3 秒），避免下次 bind 时 nl_pid=100 仍被占用
            // 注意：若是 reader 线程自己触发了 unregisterListener，跳过 join 防止死锁
            if (t != null && t != Thread.currentThread()) {
                try {
                    t.join(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            // 通知上层断开，用本地快照 wasLegacy 而非已被重置的静态字段
            if (cb != null)
                HANDLER.post(() -> cb.disconnected(wasLegacy ? Callback.Category.Legacy : Callback.Category.Generic));
        }
    }

    // --- Callback interface ---

    public interface Callback {
        /** binderType: unknown */
        int BINDER_UNKNOWN          = -1;
        /** binderType: a binder transaction call */
        int BINDER_TRANSACTION      = 0;
        /** binderType: a binder transaction reply */
        int BINDER_REPLY            = 1;
        /** binderType: free-buffer exhaustion burst */
        int BINDER_FREE_BUFFER_FULL = 2;

        /** proto: IPv4 */
        int PROTO_IPV4 = 4;
        /** proto: IPv6 */
        int PROTO_IPV6 = 6;
        /** proto: unknown */
        int PROTO_UNKNOWN = -1;

        /** data length: unknown */
        int DATA_LEN_UNKNOWN = -1;

        void disconnected(Category category);

        void exception(Exception exception);

        void binder(int type, boolean oneway, int fromUid, int fromPid, int targetUid, int targetPid, String rpcName, int code);

        void signal(int signal, int killerUid, int killerPid, int targetUid, int targetPid);

        void network(int proto, int targetUid, int dataLen);

        enum Category {
            eBPF,
            Generic,
            Legacy
        }
    }
}
