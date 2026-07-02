package nep.timeline.cirno.services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.socket.SocketProtocol;

public final class SocketServer {
    private static final ExecutorService serverExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Cirno-SocketServer");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService clientExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Cirno-SocketHandler");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final Gson gson = new Gson();
    private static volatile int actualPort = 0;
    private static volatile String authToken = null;
    private static volatile ServerSocket activeServer = null;

    private SocketServer() {
    }

    public static int getActualPort() {
        return actualPort;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void start() {
        if (running.compareAndSet(false, true)) {
            serverExecutor.execute(SocketServer::run);
            Log.i("SocketServer: starting");
        }
    }

    public static void stop() {
        running.set(false);
        ServerSocket server = activeServer;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
        serverExecutor.shutdownNow();
        clientExecutor.shutdownNow();
    }

    private static String loadOrCreateToken() {
        File tokenFile = new File(GlobalVars.CONFIG_DIR, "cirno_hook.token");
        try {
            File parent = tokenFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (tokenFile.exists()) {
                String token = new String(Files.readAllBytes(tokenFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
        } catch (IOException ignored) {
        }
        String token = java.util.UUID.randomUUID().toString();
        try {
            java.io.FileWriter writer = new java.io.FileWriter(tokenFile);
            writer.write(token);
            writer.close();
            Log.i("SocketServer: generated new auth token");
        } catch (IOException e) {
            Log.w("SocketServer: failed to write token file", e);
        }
        return token;
    }

    private static void run() {
        authToken = loadOrCreateToken();
        ServerSocket server = null;

        for (int attempt = 0; attempt < SocketProtocol.MAX_BIND_ATTEMPTS; attempt++) {
            if (!running.get()) break;
            int port = ThreadLocalRandom.current().nextInt(SocketProtocol.PORT_MIN, SocketProtocol.PORT_MAX + 1);
            try {
                server = new ServerSocket(port, 50, InetAddress.getByName(SocketProtocol.HOST));
                activeServer = server;
                actualPort = port;
                writePortFile(port);
                Log.i("SocketServer: listening on " + SocketProtocol.HOST + ":" + port);
                break;
            } catch (IOException e) {
                Log.w("SocketServer: port " + port + " bind failed, retrying in " + SocketProtocol.BIND_RETRY_DELAY_MS + "ms");
                try {
                    Thread.sleep(SocketProtocol.BIND_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        if (server == null) {
            Log.e("SocketServer: all ports failed, giving up");
            running.set(false);
            return;
        }

        try {
            while (running.get()) {
                try {
                    Socket client = server.accept();
                    if (client != null) {
                        clientExecutor.execute(() -> handleClient(client));
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        Log.w("SocketServer: accept failed", e);
                    }
                }
            }
        } finally {
            running.set(false);
            activeServer = null;
            actualPort = 0;
            deletePortFile();
            try {
                server.close();
            } catch (IOException ignored) {
            }
            Log.i("SocketServer: stopped");
        }
    }

    private static void writePortFile(int port) {
        File portFile = new File(GlobalVars.CONFIG_DIR, SocketProtocol.PORT_FILE_NAME);
        try {
            FileWriter writer = new FileWriter(portFile);
            writer.write(String.valueOf(port));
            writer.close();
        } catch (IOException e) {
            Log.w("SocketServer: failed to write port file", e);
        }
    }

    private static void deletePortFile() {
        File portFile = new File(GlobalVars.CONFIG_DIR, SocketProtocol.PORT_FILE_NAME);
        if (portFile.exists()) {
            portFile.delete();
        }
    }

    private static boolean authenticate(Socket client, InputStream in, OutputStream out) {
        try {
            client.setSoTimeout(5000);
            String requestJson = SocketProtocol.readMessage(in);
            if (requestJson == null) {
                return false;
            }
            String method = SocketProtocol.parseMethod(requestJson);
            if (!SocketProtocol.METHOD_HANDSHAKE.equals(method)) {
                return false;
            }
            long id = SocketProtocol.parseId(requestJson);
            JsonObject params = SocketProtocol.parseParams(requestJson);
            String token = SocketProtocol.getStringParam(params, "token", "");
            if (authToken == null || !authToken.equals(token)) {
                SocketProtocol.writeMessage(out, SocketProtocol.createErrorResponse(id, "auth failed"));
                return false;
            }
            SocketProtocol.writeMessage(out, SocketProtocol.createResponse(id, gson.toJsonTree("ok")));
            client.setSoTimeout(SocketProtocol.CLIENT_SO_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            Log.w("SocketServer: handshake error", e);
            return false;
        }
    }

    private static void handleClient(Socket client) {
        try {
            client.setTcpNoDelay(true);
            InputStream in = client.getInputStream();
            OutputStream out = new BufferedOutputStream(client.getOutputStream());

            if (!authenticate(client, in, out)) {
                return;
            }

            while (running.get() && !client.isClosed()) {
                String json;
                try {
                    json = SocketProtocol.readMessage(in);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                if (json == null) {
                    break;
                }
                String response = dispatch(json);
                if (response != null) {
                    SocketProtocol.writeMessage(out, response);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                Log.w("SocketServer: client handler error", e);
            }
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String dispatch(String requestJson) {
        long id;
        try {
            id = SocketProtocol.parseId(requestJson);
        } catch (Exception e) {
            return SocketProtocol.createErrorResponse(0, "invalid request");
        }

        String method = SocketProtocol.parseMethod(requestJson);
        if (method == null) {
            return SocketProtocol.createErrorResponse(id, "missing method");
        }

        JsonObject params = SocketProtocol.parseParams(requestJson);

        try {
            return switch (method) {
                case "ping" -> SocketProtocol.createResponse(id, gson.toJsonTree("pong"));
                case "getSignal" -> handleGetSignal(id, params);
                case "getStatusSnapshot" -> handleGetStatusSnapshot(id);
                case "getMonitorSnapshot" -> handleGetMonitorSnapshot(id);
                case "isPacketAvailable" -> handleIsPacketAvailable(id);
                case "getHookVersion" -> handleGetHookVersion(id);
                case "getRunningApplication" -> handleGetRunningApplication(id);
                case "getProcessesForApp" -> handleGetProcessesForApp(id, params);
                case "getNetworkSpeed" -> handleGetNetworkSpeed(id, params);
                case "isFrozen" -> handleIsFrozen(id, params);
                case "getFrozenStates" -> handleGetFrozenStates(id, params);
                default -> SocketProtocol.createErrorResponse(id, "unknown method: " + method);
            };
        } catch (Exception e) {
            return SocketProtocol.createErrorResponse(id, e.getMessage());
        }
    }

    private static String handleGetSignal(long id, JsonObject params) {
        String key = SocketProtocol.getStringParam(params, "key", "");
        String result = StatusBinderHub.getSignal(key);
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleGetStatusSnapshot(long id) {
        String result = StatusBinderHub.statusBinder.getStatusSnapshot();
        return SocketProtocol.createResponse(id, gson.fromJson(result, JsonElement.class));
    }

    private static String handleGetMonitorSnapshot(long id) {
        List<String> running = MonitorBinderHub.applicationBinder.getRunningApplication();
        List<String> frozenStates = MonitorBinderHub.frozenStateBinder.getFrozenStates(new java.util.ArrayList<>(running));
        JsonObject obj = new JsonObject();
        obj.add("running", gson.toJsonTree(running));
        obj.add("frozenStates", gson.toJsonTree(frozenStates));
        return SocketProtocol.createResponse(id, obj);
    }

    private static String handleIsPacketAvailable(long id) {
        boolean result = StatusBinderHub.statusBinder.isPacketAvailable();
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleGetHookVersion(long id) {
        String result = StatusBinderHub.statusBinder.getHookVersion();
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleGetRunningApplication(long id) {
        List<String> result = MonitorBinderHub.applicationBinder.getRunningApplication();
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleGetProcessesForApp(long id, JsonObject params) {
        String packageName = SocketProtocol.getStringParam(params, "packageName", "");
        int userId = SocketProtocol.getIntParam(params, "userId", 0);
        String result = MonitorBinderHub.applicationBinder.getProcessesForApp(packageName, userId);
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleGetNetworkSpeed(long id, JsonObject params) {
        String packageName = SocketProtocol.getStringParam(params, "packageName", "");
        int userId = SocketProtocol.getIntParam(params, "userId", 0);
        String result = MonitorBinderHub.applicationBinder.getNetworkSpeed(packageName, userId);
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleIsFrozen(long id, JsonObject params) {
        String packageName = SocketProtocol.getStringParam(params, "packageName", "");
        int userId = SocketProtocol.getIntParam(params, "userId", 0);
        String result = MonitorBinderHub.frozenStateBinder.isFrozen(packageName, userId);
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }

    private static String handleGetFrozenStates(long id, JsonObject params) {
        JsonArray appsArray = params.getAsJsonArray("apps");
        List<String> apps = new java.util.ArrayList<>();
        if (appsArray != null) {
            for (int i = 0; i < appsArray.size(); i++) {
                apps.add(appsArray.get(i).getAsString());
            }
        }
        List<String> result = MonitorBinderHub.frozenStateBinder.getFrozenStates(apps);
        return SocketProtocol.createResponse(id, gson.toJsonTree(result));
    }
}
