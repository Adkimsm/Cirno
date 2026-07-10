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

public final class CirnoBridgeConnector {
    private static final String MANAGER_PACKAGE = "nep.timeline.cirno";
    private static final String BRIDGE_CLASS = "nep.timeline.cirno.binder.CirnoBridgeService";

    private static final Object lock = new Object();
    private static final AtomicBoolean binding = new AtomicBoolean(false);

    private static volatile ICirnoBridge bridge;
    private static volatile boolean registered;
    private static volatile boolean bound;
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
        if (registered && bridge != null) {
            return;
        }
        Context context = ActivityManagerService.getContext();
        if (context == null) {
            Log.w("CirnoBridgeConnector: ActivityManagerService context is null");
            return;
        }
        if (!binding.compareAndSet(false, true)) {
            return;
        }
        Intent intent = new Intent().setComponent(new ComponentName(MANAGER_PACKAGE, BRIDGE_CLASS));
        boolean bindResult;
        try {
            bindResult = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable e) {
            binding.set(false);
            Log.w("CirnoBridgeConnector: bind broker failed", e);
            return;
        }
        if (!bindResult) {
            binding.set(false);
            Log.w("CirnoBridgeConnector: bindService returned false");
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
        }
    }

    private static void registerHookBinder() {
        ICirnoBridge currentBridge = bridge;
        if (currentBridge == null) {
            return;
        }
        try {
            currentBridge.registerHookBinder(CirnoBinderService.getService());
            registered = true;
            Log.i("CirnoBridgeConnector: hook binder registered");
        } catch (Throwable e) {
            synchronized (lock) {
                registered = false;
            }
            Log.w("CirnoBridgeConnector: register hook binder failed", e);
        }
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
