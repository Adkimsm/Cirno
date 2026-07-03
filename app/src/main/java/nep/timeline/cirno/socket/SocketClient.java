package nep.timeline.cirno.socket;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.IOUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.provide.ApplicationBinderFacade;
import nep.timeline.cirno.provide.FrozenStateBinderFacade;
import nep.timeline.cirno.provide.StatusBinderFacade;

public final class SocketClient {
    private static final SocketClient INSTANCE = new SocketClient();
    private static final Gson gson = new Gson();
    private static final long CONNECT_TIMEOUT_MS = 3000L;
    private static final long REQUEST_TIMEOUT_MS = 5000L;
    private static final long RECONNECT_BASE_DELAY_MS = 500L;
    private static final long RECONNECT_MAX_DELAY_MS = 5000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Cirno-Heartbeat");
        t.setDaemon(true);
        return t;
    });
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected = false;
    private volatile long lastConnectFailedAtMs = 0;
    private volatile String lastConnectError = null;

    private SocketClient() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!connected) return;
            try {
                sendRequest("ping", null);
            } catch (IOException e) {
                Log.w("SocketClient: heartbeat failed, closing connection", e);
                close();
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static SocketClient getInstance() {
        return INSTANCE;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getLastConnectError() {
        return lastConnectError;
    }

    public boolean waitForConnection(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        lock.lock();
        try {
            close();
            do {
                try {
                    ensureConnected();
                    return true;
                } catch (Exception e) {
                    rememberConnectError(e);
                }

                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }

                try {
                    Thread.sleep(Math.min(300L, remaining));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } while (true);
        } finally {
            lock.unlock();
        }
    }

    private synchronized void ensureConnected() throws IOException {
        if (connected && socket != null && !socket.isClosed()) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastConnectFailedAtMs;
        if (lastConnectFailedAtMs > 0 && elapsed < RECONNECT_BASE_DELAY_MS) {
            try {
                Thread.sleep(RECONNECT_BASE_DELAY_MS - elapsed);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        connect();
    }

    private void connect() throws IOException {
        close();
        String token = readToken();
        if (token == null || token.isEmpty()) {
            throw connectFailure(lastConnectError != null ? lastConnectError : "auth token is missing or empty");
        }

        int port = readPort();
        if (port < 0) {
            lastConnectFailedAtMs = System.currentTimeMillis();
            throw connectFailure(lastConnectError != null ? lastConnectError : "port file is missing or invalid");
        }

        int result = trySingleConnect(port, token);
        if (result < 0) {
            lastConnectFailedAtMs = System.currentTimeMillis();
            throw connectFailure(lastConnectError != null ? lastConnectError : "failed to connect to port " + port);
        }
    }

    private int trySingleConnect(int port, String token) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(SocketProtocol.HOST, port), (int) CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            s.setSoTimeout((int) REQUEST_TIMEOUT_MS);
            InputStream sockIn = s.getInputStream();
            OutputStream sockOut = new BufferedOutputStream(s.getOutputStream());

            long id = requestIdGenerator.incrementAndGet();
            JsonObject params = new JsonObject();
            params.addProperty("token", token);
            String handshake = SocketProtocol.createRequest(id, SocketProtocol.METHOD_HANDSHAKE, gson.toJson(params));
            SocketProtocol.writeMessage(sockOut, handshake);
            String response = SocketProtocol.readMessage(sockIn);
            if (response == null) {
                rememberConnectError("handshake response is empty");
                closeSocket(s);
                return -1;
            }
            String error = SocketProtocol.parseError(response);
            if (error != null) {
                rememberConnectError("handshake rejected: " + error);
                closeSocket(s);
                return -1;
            }
            String result = SocketProtocol.parseResult(response);
            if (result == null || !result.contains("ok")) {
                rememberConnectError("handshake result is invalid: " + result);
                closeSocket(s);
                return -1;
            }
            socket = s;
            in = sockIn;
            out = sockOut;
            connected = true;
            lastConnectFailedAtMs = 0;
            lastConnectError = null;
            Log.i("SocketClient: connected to port " + port);
            return port;
        } catch (SocketTimeoutException e) {
            rememberConnectError("connect timeout to " + SocketProtocol.HOST + ":" + port);
        } catch (Exception e) {
            rememberConnectError("connect to " + SocketProtocol.HOST + ":" + port + " failed: " + formatThrowable(e));
        }
        if (s != null) {
            closeSocket(s);
        }
        return -1;
    }

    private String readToken() {
        try {
            SuFile tokenFile = new SuFile(GlobalVars.CONFIG_DIR, "cirno_hook.token");
            if (tokenFile.exists()) {
                String token = IOUtils.toString(() -> SuFileInputStream.open(tokenFile), StandardCharsets.UTF_8).trim();
                if (!token.isEmpty()) {
                    return token;
                }
                rememberConnectError("auth token is empty");
                return null;
            }
            rememberConnectError("auth token file is missing");
        } catch (Exception e) {
            rememberConnectError("failed to read auth token file: " + formatThrowable(e));
            Log.e("SocketClient: failed to read token file", e);
        }
        return null;
    }

    private int readPort() {
        try {
            SuFile portFile = new SuFile(GlobalVars.CONFIG_DIR, SocketProtocol.PORT_FILE_NAME);
            if (portFile.exists()) {
                String content = IOUtils.toString(() -> SuFileInputStream.open(portFile), StandardCharsets.UTF_8).trim();
                if (content.isEmpty()) {
                    rememberConnectError("port file is empty");
                    return -1;
                }
                int port = Integer.parseInt(content);
                if (port >= SocketProtocol.PORT_MIN && port <= SocketProtocol.PORT_MAX) {
                    return port;
                }
                rememberConnectError("port is out of range: " + port);
                return -1;
            }
            rememberConnectError("port file is missing");
        } catch (NumberFormatException e) {
            rememberConnectError("port file is invalid: " + formatThrowable(e));
        } catch (Exception e) {
            rememberConnectError("failed to read port file: " + formatThrowable(e));
            Log.e("SocketClient: failed to read port file", e);
        }
        return -1;
    }

    private IOException connectFailure(String message) {
        rememberConnectError(message);
        return new IOException(message);
    }

    private void rememberConnectError(Throwable throwable) {
        rememberConnectError(formatThrowable(throwable));
    }

    private void rememberConnectError(String message) {
        lastConnectError = message;
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static String formatThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    public synchronized void close() {
        connected = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
        in = null;
        out = null;
    }

    private String sendRequest(String method, String paramsJson) throws IOException {
        lock.lock();
        try {
            ensureConnected();
            long id = requestIdGenerator.incrementAndGet();
            String request = SocketProtocol.createRequest(id, method, paramsJson);
            SocketProtocol.writeMessage(out, request);
            String response = SocketProtocol.readMessage(in);
            if (response == null) {
                close();
                throw new IOException("null response");
            }
            String error = SocketProtocol.parseError(response);
            if (error != null) {
                throw new IOException("server error: " + error);
            }
            return SocketProtocol.parseResult(response);
        } catch (IOException e) {
            close();
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public StatusBinderFacade getStatusBinder() {
        return statusFacade;
    }

    public ApplicationBinderFacade getApplicationBinder() {
        return applicationFacade;
    }

    public FrozenStateBinderFacade getFrozenStateBinder() {
        return frozenStateFacade;
    }

    public static class MonitorSnapshot {
        public final List<String> running;
        public final List<String> frozenStates;

        public MonitorSnapshot(List<String> running, List<String> frozenStates) {
            this.running = running;
            this.frozenStates = frozenStates;
        }
    }

    public MonitorSnapshot getMonitorSnapshot() {
        try {
            String result = sendRequest("getMonitorSnapshot", null);
            JsonObject obj = gson.fromJson(result, JsonObject.class);
            List<String> running = gson.fromJson(obj.get("running"), new TypeToken<List<String>>() {}.getType());
            List<String> frozenStates = gson.fromJson(obj.get("frozenStates"), new TypeToken<List<String>>() {}.getType());
            return new MonitorSnapshot(running, frozenStates);
        } catch (Exception e) {
            Log.w("SocketClient: getMonitorSnapshot failed", e);
            return null;
        }
    }

    private final StatusBinderFacade statusFacade = new StatusBinderFacade() {
        @Override
        public String getSignal(String key) {
            try {
                String params = "{\"key\":" + gson.toJson(key) + "}";
                String result = sendRequest("getSignal", params);
                return gson.fromJson(result, String.class);
            } catch (Exception e) {
                Log.w("SocketClient: getSignal failed", e);
                return "";
            }
        }

        @Override
        public String getStatusSnapshot() {
            try {
                return sendRequest("getStatusSnapshot", null);
            } catch (Exception e) {
                Log.w("SocketClient: getStatusSnapshot failed", e);
                return null;
            }
        }

        @Override
        public boolean isPacketAvailable() {
            try {
                String result = sendRequest("isPacketAvailable", null);
                return gson.fromJson(result, Boolean.class);
            } catch (Exception e) {
                Log.w("SocketClient: isPacketAvailable failed", e);
                return false;
            }
        }

        @Override
        public String getHookVersion() {
            try {
                String result = sendRequest("getHookVersion", null);
                return gson.fromJson(result, String.class);
            } catch (Exception e) {
                Log.w("SocketClient: getHookVersion failed", e);
                return null;
            }
        }
    };

    private final ApplicationBinderFacade applicationFacade = new ApplicationBinderFacade() {
        @Override
        public List<String> getRunningApplication() {
            try {
                String result = sendRequest("getRunningApplication", null);
                return gson.fromJson(result, new TypeToken<List<String>>() {}.getType());
            } catch (Exception e) {
                Log.w("SocketClient: getRunningApplication failed", e);
                return java.util.Collections.emptyList();
            }
        }

        @Override
        public String getProcessesForApp(String packageName, int userId) {
            try {
                String params = "{\"packageName\":" + gson.toJson(packageName) + ",\"userId\":" + userId + "}";
                String result = sendRequest("getProcessesForApp", params);
                return gson.fromJson(result, String.class);
            } catch (Exception e) {
                Log.w("SocketClient: getProcessesForApp failed", e);
                return "[]";
            }
        }

        @Override
        public String getNetworkSpeed(String packageName, int userId) {
            try {
                String params = "{\"packageName\":" + gson.toJson(packageName) + ",\"userId\":" + userId + "}";
                String result = sendRequest("getNetworkSpeed", params);
                return gson.fromJson(result, String.class);
            } catch (Exception e) {
                Log.w("SocketClient: getNetworkSpeed failed", e);
                return "{\"rx\":0,\"tx\":0}";
            }
        }
    };

    private final FrozenStateBinderFacade frozenStateFacade = new FrozenStateBinderFacade() {
        @Override
        public String isFrozen(String packageName, int userId) {
            try {
                String params = "{\"packageName\":" + gson.toJson(packageName) + ",\"userId\":" + userId + "}";
                String result = sendRequest("isFrozen", params);
                return gson.fromJson(result, String.class);
            } catch (Exception e) {
                Log.w("SocketClient: isFrozen failed", e);
                return "NOT_FROZEN[UNKNOWN]";
            }
        }

        @Override
        public List<String> getFrozenStates(List<String> apps) {
            try {
                String params = "{\"apps\":" + gson.toJson(apps) + "}";
                String result = sendRequest("getFrozenStates", params);
                return gson.fromJson(result, new TypeToken<List<String>>() {}.getType());
            } catch (Exception e) {
                Log.w("SocketClient: getFrozenStates failed", e);
                return java.util.Collections.emptyList();
            }
        }
    };
}
