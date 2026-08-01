package nep.timeline.cirno.binder;

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
    private static final String KEY_RUNNING = "running";
    private static final String KEY_FROZEN_STATES = "frozenStates";

    private static final Object lock = new Object();
    private static ICirnoService hookService;
    private static IBinder hookBinder;
    private static IBinder.DeathRecipient hookDeathRecipient;
    private static volatile String lastConnectError;

    public static void register(android.content.Context appContext) {
        // No-op: provider-based architecture does not require explicit registration.
        // Retained for API compatibility with existing callers.
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
            if (hookService != null && hookBinder != null && hookBinder.isBinderAlive()) {
                return true;
            }
        }
        return refreshFromProvider();
    }

    public static String getLastConnectError() {
        return lastConnectError;
    }

    public static boolean waitForConnection(long timeoutMs) {
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
                String cached = CirnoBinderProvider.getCachedStatusSnapshot();
                return cached != null ? cached : null;
            }
            try {
                return remote.getStatusSnapshot();
            } catch (Throwable e) {
                rememberConnectError("getStatusSnapshot failed: " + formatThrowable(e));
                Log.w("BinderService: getStatusSnapshot failed", e);
                clearHookService();
                String cached = CirnoBinderProvider.getCachedStatusSnapshot();
                return cached != null ? cached : null;
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
        ICirnoService providerService = CirnoBinderProvider.getHookService();
        if (providerService != null) {
            IBinder providerBinder = providerService.asBinder();
            synchronized (lock) {
                // Only reuse the cache when it points to the binder currently
                // published by the provider. After a hot reload the process is
                // not killed, so the old binder may still report isBinderAlive()
                // == true while the provider already holds the freshly published
                // one; reusing the stale binder would return the old version.
                if (hookService != null && hookBinder == providerBinder && hookBinder.isBinderAlive()) {
                    return hookService;
                }
            }
            refreshFromProvider();
            synchronized (lock) {
                return hookService;
            }
        }
        synchronized (lock) {
            if (hookService != null && hookBinder != null && hookBinder.isBinderAlive()) {
                return hookService;
            }
        }
        refreshFromProvider();
        synchronized (lock) {
            return hookService;
        }
    }

    private static boolean refreshFromProvider() {
        ICirnoService service = CirnoBinderProvider.getHookService();
        if (service == null) {
            rememberConnectError("hook binder not available from provider");
            return false;
        }
        IBinder binder = service.asBinder();
        synchronized (lock) {
            if (hookBinder == binder && hookService != null && binder.isBinderAlive()) {
                rememberConnectError(null);
                return true;
            }
        }
        try {
            IBinder.DeathRecipient dr = BinderService::clearHookService;
            binder.linkToDeath(dr, 0);
            synchronized (lock) {
                if (hookBinder != null && hookDeathRecipient != null && hookBinder != binder) {
                    hookBinder.unlinkToDeath(hookDeathRecipient, 0);
                }
                hookService = service;
                hookBinder = binder;
                hookDeathRecipient = dr;
            }
            rememberConnectError(null);
            return true;
        } catch (RemoteException e) {
            rememberConnectError("hook binder died before linkToDeath");
            clearHookService();
            return false;
        }
    }

    private static void clearHookService() {
        synchronized (lock) {
            if (hookBinder != null && hookDeathRecipient != null) {
                hookBinder.unlinkToDeath(hookDeathRecipient, 0);
            }
            hookService = null;
            hookBinder = null;
            hookDeathRecipient = null;
        }
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
