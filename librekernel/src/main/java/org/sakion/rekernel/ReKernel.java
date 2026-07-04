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
import static org.sakion.rekernel.GenericUtils.extractEvent;
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
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static void resolver(Callback.Category category, String data, Callback callback) {
        int indexOf = data.indexOf("type");
        int lastIndexOf = data.lastIndexOf(";");
        if (indexOf < 0 || lastIndexOf < 0 || indexOf > lastIndexOf)
            return;

        String message = data.substring(indexOf, lastIndexOf);
        Map<String, String> params = new HashMap<>();
        for (String keyValue : message.split(",")) {
            String[] split = keyValue.split("=");
            if (split.length == 2)
                params.put(split[0].trim(), split[1].trim());
        }

        switch (params.get("type")) {
            case "Binder" -> {
                int binderType = switch (params.get("bindertype")) {
                    case "transaction" -> Callback.BINDER_TRANSACTION;
                    case "reply" -> Callback.BINDER_REPLY;
                    case "free_buffer_full" -> Callback.BINDER_FREE_BUFFER_FULL;
                    case null, default -> {
                        callback.exception(new IllegalStateException("Unknown binder type: " + params.get("bindertype")));
                        yield Callback.BINDER_UNKNOWN;
                    }
                };
                boolean oneway = StringToInteger(params.get("oneway")) == 1;
                int fromPid = StringToInteger(params.get("from_pid"));
                int fromUid = StringToInteger(params.get("from"));
                int targetPid = StringToInteger(params.get("target_pid"));
                int targetUid = StringToInteger(params.get("target"));
                String rpcName = params.getOrDefault("rpc_name", "");
                int code = StringToInteger(params.get("code"));
                callback.binder(binderType, oneway, fromUid, fromPid, targetUid, targetPid, rpcName, code);
            }
            case "Signal" -> {
                int targetPid = StringToInteger(params.get("dst_pid"));
                int targetUid = StringToInteger(params.get("dst"));
                int killerPid = StringToInteger(params.get("killer_pid"));
                int killerUid = StringToInteger(params.get("killer"));
                int signal = StringToInteger(params.get("signal"));
                callback.signal(signal, killerUid, killerPid, targetUid, targetPid);
            }
            case "Network" -> {
                int targetUid = StringToInteger(params.get("target"));
                int proto = params.containsKey("proto") ? switch (params.get("proto")) {
                    case "ipv4" -> Callback.PROTO_IPV4;
                    case "ipv6" -> Callback.PROTO_IPV6;
                    case null, default -> {
                        callback.exception(new IllegalStateException("Unknown proto: " + params.get("proto")));
                        yield Callback.PROTO_UNKNOWN;
                    }
                } : Callback.PROTO_UNKNOWN;
                int dataLen = params.containsKey("data_len") ? StringToInteger(params.get("data_len")) : Callback.DATA_LEN_UNKNOWN;
                callback.network(proto, targetUid, dataLen);
            }
            case "Version" -> {
                // eBPF only
                eBPF.version = params.get("version");
                synchronized (eBPF.versionLock) {
                    eBPF.versionLock.notifyAll();
                }
            }
            case null, default -> callback.exception(new IllegalStateException("Unknown type: " + params.get("type")));
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
                InputStream in = s.getInputStream();
                byte[] buf = new byte[DEFAULT_RECV_BUFSIZE];
                StringBuilder acc = new StringBuilder();
                int len;
                while ((len = in.read(buf)) >= 0) {
                    if (len == 0)
                        continue;
                    acc.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                    int nl;
                    while ((nl = acc.indexOf("\n")) >= 0) {
                        String line = acc.substring(0, nl);
                        acc.delete(0, nl + 1);
                        if (!line.isEmpty())
                            HANDLER.post(() -> resolver(Callback.Category.eBPF, line, callback));
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
        private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private static final AtomicBoolean running = new AtomicBoolean(false);
        private static String version = null;
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
            if (!isRunning() || version == null)
                return false;

            if (getMajorVersion() < 10)
                return false;

            if (legacy)
                return sendLegacyCommand(3, uid);

            return sendCommand(REKERNEL_C_DEL_MONITOR_NET, true, REKERNEL_A_UID, uid);
        }

        public static int getMajorVersion() {
            if (version == null)
                return -1;

            return StringToInteger(version.split("\\.")[0]);
        }

        public static int getMinorVersion() {
            if (version == null)
                return -1;

            return StringToInteger(version.split("\\.")[1]);
        }

        public static boolean destroySocket(int pid) {
            if (!isRunning() || version == null)
                return false;

            if (getMajorVersion() < 10)
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
                            version = Files.readAllLines(file.toPath()).get(0);
                            lastError = "Legacy从/proc/rekernel读取netlink unit: " + files[1].getName() + ", version=" + version;
                            netlinkUnit = GenericUtils.StringToInteger(files[1].getName());
                        } else {
                            version = Files.readAllLines(files[1].toPath()).get(0);
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

                executorService.execute(() -> {
                    if (!running.get()) {
                        return;
                    }

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

                    while (running.get()) {
                        try {
                            ByteBuffer byteBuffer = ByteBuffer.allocate(DEFAULT_RECV_BUFSIZE);
                            int length = Os.read(descriptor, byteBuffer);
                            byteBuffer.position(0);
                            byteBuffer.limit(length);
                            byteBuffer.order(ByteOrder.nativeOrder());
                            String data = new String(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit(), StandardCharsets.UTF_8);
                            if (!data.isEmpty())
                                HANDLER.post(() -> resolver(Callback.Category.Legacy, data, callback));
                        } catch (ErrnoException | StringIndexOutOfBoundsException |
                                 InterruptedIOException | NumberFormatException _) {
                            if (!running.get())
                                break;
                        } catch (Exception e) {
                            callback.exception(e);
                            if (!running.get())
                                break;
                        }
                    }
                });

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

                version = readVersion();

                executorService.execute(() -> {
                    while (running.get()) {
                        try {
                            ByteBuffer byteBuffer = ByteBuffer.allocate(DEFAULT_RECV_BUFSIZE);
                            int length = Os.read(descriptor, byteBuffer);
                            byteBuffer.order(ByteOrder.nativeOrder());
                            String data = extractEvent(byteBuffer, length);
                            if (data != null && !data.isEmpty())
                                HANDLER.post(() -> resolver(Callback.Category.Generic, data, callback));
                        } catch (ErrnoException | StringIndexOutOfBoundsException |
                                 InterruptedIOException | NumberFormatException _) {
                            if (!running.get())
                                break;
                        } catch (Exception e) {
                            callback.exception(e);
                            if (!running.get())
                                break;
                        }
                    }
                });

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
            try {
                running.set(false);
                if (cacheCallback != null) {
                    cacheCallback.disconnected(legacy ? Callback.Category.Legacy : Callback.Category.Generic);
                    cacheCallback = null;
                }
                version = null;
                GenericUtils.closeAndSignalBlockedThreads(fileDescriptor);
                fileDescriptor = null;
                legacy = false;
                defaultUnit = false;
            } catch (Throwable _) {
            }
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
