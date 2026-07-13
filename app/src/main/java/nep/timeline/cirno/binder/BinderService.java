package nep.timeline.cirno.binder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

import java.util.Collections;
import java.util.List;

import nep.timeline.cirno.provide.StatusBinderFacade;
import nep.timeline.cirno.provide.ApplicationBinderFacade;
import nep.timeline.cirno.provide.FrozenStateBinderFacade;
import nep.timeline.cirno.log.Log;

public class BinderService {
    private static final String MANAGER_PACKAGE = "nep.timeline.cirno";
    private static final String BRIDGE_CLASS = "nep.timeline.cirno.binder.CirnoBridgeService";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_FROZEN_STATES = "frozenStates";

    private static final Object lock = new Object();
    private static Context applicationContext;
    private static boolean binding;
    private static boolean bound;
    private static ICirnoBridge bridge;
    private static ICirnoService hookService;
    private static IBinder bridgeBinder;
    private static IBinder hookBinder;
    private static IBinder.DeathRecipient bridgeDeathRecipient;
    private static IBinder.DeathRecipient hookDeathRecipient;
    private static volatile String lastConnectError;

    public static void register(android.content.Context appContext) {
        if (appContext == null) {
            rememberConnectError("application context is null");
            return;
        }
        synchronized (lock) {
            applicationContext = appContext.getApplicationContext();
            if (bound || binding) {
                return;
            }
            binding = true;
        }

        Intent intent = new Intent().setComponent(new ComponentName(MANAGER_PACKAGE, BRIDGE_CLASS));
        boolean bindResult;
        try {
            bindResult = applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable e) {
            synchronized (lock) {
                binding = false;
            }
            rememberConnectError("bind broker failed: " + formatThrowable(e));
            Log.w("BinderService: bind broker failed", e);
            return;
        }

        if (!bindResult) {
            synchronized (lock) {
                binding = false;
            }
            rememberConnectError("bindService returned false");
        }
    }

    public static StatusBinderFacade getStatusBinder() {
        return statusFacade;
    }

    public static ApplicationBinderFacade getApplicationBinder() {
        return applicationFacade;
    }

    public static FrozenStateBinderFacade getFrozenStateBinder() {
        return frozenStateFacade;
    }

    public static boolean isConnected() {
        synchronized (lock) {
            return hookService != null && hookBinder != null && hookBinder.isBinderAlive();
        }
    }

    public static String getLastConnectError() {
        return lastConnectError;
    }

    public static boolean waitForConnection(long timeoutMs) {
        Context context = applicationContext;
        if (context != null) {
            register(context);
        }
        long deadline = SystemClock.uptimeMillis() + Math.max(0L, timeoutMs);
        do {
            ICirnoService remote = getRemoteService();
            if (remote != null) {
                return true;
            }
            if (SystemClock.uptimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                rememberConnectError("wait for binder interrupted");
                return false;
            }
        } while (true);
    }

    public static MonitorSnapshot getMonitorSnapshot() {
        ICirnoService remote = getRemoteService();
        if (remote == null) {
            return null;
        }
        try {
            Bundle bundle = remote.getMonitorSnapshot();
            if (bundle == null) {
                return null;
            }
            List<String> running = bundle.getStringArrayList(KEY_RUNNING);
            List<String> frozenStates = bundle.getStringArrayList(KEY_FROZEN_STATES);
            return new MonitorSnapshot(
                    running != null ? running : Collections.emptyList(),
                    frozenStates != null ? frozenStates : Collections.emptyList());
        } catch (Throwable e) {
            rememberConnectError("get monitor snapshot failed: " + formatThrowable(e));
            Log.w("BinderService: getMonitorSnapshot failed", e);
            clearHookService();
            return null;
        }
    }

    public static final class MonitorSnapshot {
        public final List<String> running;
        public final List<String> frozenStates;

        public MonitorSnapshot(List<String> running, List<String> frozenStates) {
            this.running = running;
            this.frozenStates = frozenStates;
        }
    }

    private static final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                binding = false;
                bound = true;
                bridgeBinder = service;
                bridge = ICirnoBridge.Stub.asInterface(service);
                bridgeDeathRecipient = BinderService::clearBridgeConnection;
                try {
                    service.linkToDeath(bridgeDeathRecipient, 0);
                } catch (RemoteException e) {
                    rememberConnectError("broker died before linkToDeath");
                    clearBridgeLocked();
                    return;
                }
            }
            rememberConnectError(null);
            refreshHookBinder();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            clearBridgeConnection();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            rememberConnectError("broker returned null binding");
            clearBridgeConnection();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            rememberConnectError("broker binding died");
            clearBridgeConnection();
            Context context = applicationContext;
            if (context != null) {
                register(context);
            }
        }
    };

    private static final StatusBinderFacade statusFacade = new StatusBinderFacade() {
        @Override
        public String getSignal(String key) {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return "";
            }
            try {
                return remote.getSignal(key);
            } catch (Throwable e) {
                rememberConnectError("getSignal failed: " + formatThrowable(e));
                Log.w("BinderService: getSignal failed", e);
                clearHookService();
                return "";
            }
        }

        @Override
        public String getStatusSnapshot() {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return getCachedStatusSnapshot();
            }
            try {
                return remote.getStatusSnapshot();
            } catch (Throwable e) {
                rememberConnectError("getStatusSnapshot failed: " + formatThrowable(e));
                Log.w("BinderService: getStatusSnapshot failed", e);
                clearHookService();
                return getCachedStatusSnapshot();
            }
        }

        @Override
        public boolean isPacketAvailable() {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return false;
            }
            try {
                return remote.isPacketAvailable();
            } catch (Throwable e) {
                rememberConnectError("isPacketAvailable failed: " + formatThrowable(e));
                Log.w("BinderService: isPacketAvailable failed", e);
                clearHookService();
                return false;
            }
        }

        @Override
        public String getHookVersion() {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return null;
            }
            try {
                return remote.getHookVersion();
            } catch (Throwable e) {
                rememberConnectError("getHookVersion failed: " + formatThrowable(e));
                Log.w("BinderService: getHookVersion failed", e);
                clearHookService();
                return null;
            }
        }
    };

    private static final ApplicationBinderFacade applicationFacade = new ApplicationBinderFacade() {
        @Override
        public List<String> getRunningApplication() {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return Collections.emptyList();
            }
            try {
                List<String> result = remote.getRunningApplication();
                return result != null ? result : Collections.emptyList();
            } catch (Throwable e) {
                rememberConnectError("getRunningApplication failed: " + formatThrowable(e));
                Log.w("BinderService: getRunningApplication failed", e);
                clearHookService();
                return Collections.emptyList();
            }
        }

        @Override
        public String getProcessesForApp(String packageName, int userId) {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return "[]";
            }
            try {
                String result = remote.getProcessesForApp(packageName, userId);
                return result != null ? result : "[]";
            } catch (Throwable e) {
                rememberConnectError("getProcessesForApp failed: " + formatThrowable(e));
                Log.w("BinderService: getProcessesForApp failed", e);
                clearHookService();
                return "[]";
            }
        }

        @Override
        public String getNetworkSpeed(String packageName, int userId) {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return "{\"rx\":0,\"tx\":0}";
            }
            try {
                String result = remote.getNetworkSpeed(packageName, userId);
                return result != null ? result : "{\"rx\":0,\"tx\":0}";
            } catch (Throwable e) {
                rememberConnectError("getNetworkSpeed failed: " + formatThrowable(e));
                Log.w("BinderService: getNetworkSpeed failed", e);
                clearHookService();
                return "{\"rx\":0,\"tx\":0}";
            }
        }
    };

    private static final FrozenStateBinderFacade frozenStateFacade = new FrozenStateBinderFacade() {
        @Override
        public String isFrozen(String packageName, int userId) {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return "NOT_FROZEN[UNKNOWN]";
            }
            try {
                String result = remote.isFrozen(packageName, userId);
                return result != null ? result : "NOT_FROZEN[UNKNOWN]";
            } catch (Throwable e) {
                rememberConnectError("isFrozen failed: " + formatThrowable(e));
                Log.w("BinderService: isFrozen failed", e);
                clearHookService();
                return "NOT_FROZEN[UNKNOWN]";
            }
        }

        @Override
        public List<String> getFrozenStates(List<String> apps) {
            ICirnoService remote = getRemoteService();
            if (remote == null) {
                return Collections.emptyList();
            }
            try {
                List<String> result = remote.getFrozenStates(apps);
                return result != null ? result : Collections.emptyList();
            } catch (Throwable e) {
                rememberConnectError("getFrozenStates failed: " + formatThrowable(e));
                Log.w("BinderService: getFrozenStates failed", e);
                clearHookService();
                return Collections.emptyList();
            }
        }
    };

    private static ICirnoService getRemoteService() {
        ICirnoService current = hookService;
        IBinder currentBinder = hookBinder;
        if (current != null && currentBinder != null && currentBinder.isBinderAlive()) {
            return current;
        }
        refreshHookBinder();
        synchronized (lock) {
            return hookService;
        }
    }

    private static void refreshHookBinder() {
        ICirnoBridge currentBridge;
        synchronized (lock) {
            currentBridge = bridge;
        }
        if (currentBridge == null) {
            Context context = applicationContext;
            if (context != null) {
                register(context);
            }
            return;
        }
        try {
            ICirnoService service = currentBridge.getHookBinder();
            if (service == null) {
                rememberConnectError("hook binder is not registered");
                clearHookService();
                return;
            }
            IBinder binder = service.asBinder();
            synchronized (lock) {
                if (hookBinder == binder && hookService != null && binder.isBinderAlive()) {
                    rememberConnectError(null);
                    return;
                }
            }
            IBinder.DeathRecipient deathRecipient = BinderService::clearHookService;
            binder.linkToDeath(deathRecipient, 0);
            synchronized (lock) {
                if (hookBinder != null && hookDeathRecipient != null && hookBinder != binder) {
                    hookBinder.unlinkToDeath(hookDeathRecipient, 0);
                }
                hookService = service;
                hookBinder = binder;
                hookDeathRecipient = deathRecipient;
            }
            rememberConnectError(null);
        } catch (RemoteException e) {
            rememberConnectError("hook binder died before linkToDeath");
            clearHookService();
        } catch (Throwable e) {
            rememberConnectError("get hook binder failed: " + formatThrowable(e));
            Log.w("BinderService: get hook binder failed", e);
            clearHookService();
        }
    }

    private static String getCachedStatusSnapshot() {
        ICirnoBridge currentBridge;
        synchronized (lock) {
            currentBridge = bridge;
        }
        if (currentBridge == null) {
            return null;
        }
        try {
            String snapshot = currentBridge.getInitialStatusSnapshot();
            return snapshot == null || snapshot.isBlank() ? null : snapshot;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clearBridgeConnection() {
        synchronized (lock) {
            clearBridgeLocked();
        }
    }

    private static void clearBridgeLocked() {
        clearHookServiceLocked();
        binding = false;
        bound = false;
        bridge = null;
        if (bridgeBinder != null && bridgeDeathRecipient != null) {
            bridgeBinder.unlinkToDeath(bridgeDeathRecipient, 0);
        }
        bridgeBinder = null;
        bridgeDeathRecipient = null;
    }

    private static void clearHookService() {
        synchronized (lock) {
            clearHookServiceLocked();
        }
    }

    private static void clearHookServiceLocked() {
        if (hookBinder != null && hookDeathRecipient != null) {
            hookBinder.unlinkToDeath(hookDeathRecipient, 0);
        }
        hookService = null;
        hookBinder = null;
        hookDeathRecipient = null;
    }

    private static void rememberConnectError(String message) {
        lastConnectError = message;
    }

    private static String formatThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
