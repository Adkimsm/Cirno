package nep.timeline.cirno.binder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.atomic.AtomicBoolean;

import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.StatusBinderHub;

public final class CirnoBridgeConnector {
    private static final String MANAGER_PACKAGE = "nep.timeline.cirno";
    private static final String BRIDGE_CLASS = "nep.timeline.cirno.binder.CirnoBridgeService";
    private static final long BIND_RETRY_BACKOFF_MS = 5000L;
    private static final long STATUS_POLL_INTERVAL_MS = 100L;

    private static final Object lock = new Object();
    private static final AtomicBoolean binding = new AtomicBoolean(false);

    private static volatile ICirnoBridge bridge;
    private static volatile boolean registered;
    private static volatile boolean bound;
    private static volatile long lastBindFailedAtMs;
    private static volatile String lastFailureMessage;
    private static IBinder bridgeBinder;
    private static IBinder.DeathRecipient bridgeDeathRecipient;

    private static final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                binding.set(false);
                bound = true;
                bridgeBinder = service;
                bridge = ICirnoBridge.Stub.asInterface(service);
                bridgeDeathRecipient = () -> {
                    synchronized (lock) {
                        clearBridgeLocked();
                    }
                };
                try {
                    service.linkToDeath(bridgeDeathRecipient, 0);
                } catch (RemoteException e) {
                    clearBridgeLocked();
                    return;
                }
                lastBindFailedAtMs = 0L;
                lastFailureMessage = null;
            }
            registerHookBinder();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                clearBridgeLocked();
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            synchronized (lock) {
                clearBridgeLocked();
            }
            Log.w("CirnoBridgeConnector: broker returned null binding");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            synchronized (lock) {
                clearBridgeLocked();
            }
            publish();
        }
    };

    private CirnoBridgeConnector() {
    }

    public static void publish() {
        if (isBridgeReady()) {
            return;
        }
        if (bound && isBridgeAlive()) {
            registerHookBinder();
            return;
        }
        Context context = ActivityManagerService.getContext();
        if (context == null) {
            logFailureOnce("ActivityManagerService context is null");
            return;
        }
        if (!binding.compareAndSet(false, true)) {
            return;
        }
        if (isWithinBindBackoff()) {
            binding.set(false);
            return;
        }
        Intent intent = new Intent().setComponent(new ComponentName(MANAGER_PACKAGE, BRIDGE_CLASS));
        boolean bindResult;
        try {
            bindResult = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable e) {
            binding.set(false);
            logFailureOnce("bind broker failed: " + formatThrowable(e));
            return;
        }
        if (!bindResult) {
            binding.set(false);
            logFailureOnce("bindService returned false");
        }
    }

    public static void stopForHotReload() {
        Context context = ActivityManagerService.getContext();
        synchronized (lock) {
            if (context != null && bound) {
                try {
                    context.unbindService(connection);
                } catch (Throwable ignored) {
                }
            }
            clearBridgeLocked();
            binding.set(false);
            lastBindFailedAtMs = 0L;
            lastFailureMessage = null;
        }
    }

    private static void registerHookBinder() {
        ICirnoBridge currentBridge = bridge;
        if (currentBridge == null) {
            return;
        }
        try {
            String initialStatusSnapshot = waitForInitialStatusSnapshot();
            currentBridge.registerHookBinder(CirnoBinderService.getService(), initialStatusSnapshot);
            synchronized (lock) {
                registered = true;
                lastBindFailedAtMs = 0L;
                lastFailureMessage = null;
            }
            Log.i("CirnoBridgeConnector: hook binder registered");
        } catch (Throwable e) {
            synchronized (lock) {
                registered = false;
            }
            logFailureOnce("register hook binder failed: " + formatThrowable(e));
        }
    }

    private static String waitForInitialStatusSnapshot() throws InterruptedException {
        while (true) {
            String snapshot = StatusBinderHub.statusBinder.getStatusSnapshot();
            if (snapshot != null && !snapshot.isBlank()) {
                return snapshot;
            }
            Thread.sleep(STATUS_POLL_INTERVAL_MS);
        }
    }

    private static boolean isBridgeReady() {
        IBinder binder = bridgeBinder;
        return registered && bridge != null && binder != null && binder.isBinderAlive();
    }

    private static boolean isBridgeAlive() {
        IBinder binder = bridgeBinder;
        return bridge != null && binder != null && binder.isBinderAlive();
    }

    private static boolean isWithinBindBackoff() {
        long failedAt = lastBindFailedAtMs;
        return failedAt > 0L && (System.currentTimeMillis() - failedAt) < BIND_RETRY_BACKOFF_MS;
    }

    private static void logFailureOnce(String message) {
        synchronized (lock) {
            lastBindFailedAtMs = System.currentTimeMillis();
            if (message != null && message.equals(lastFailureMessage)) {
                return;
            }
            lastFailureMessage = message;
        }
        Log.w("CirnoBridgeConnector: " + message);
    }

    private static String formatThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private static void clearBridgeLocked() {
        registered = false;
        bound = false;
        if (bridgeBinder != null && bridgeDeathRecipient != null) {
            bridgeBinder.unlinkToDeath(bridgeDeathRecipient, 0);
        }
        bridgeBinder = null;
        bridgeDeathRecipient = null;
        bridge = null;
    }
}
