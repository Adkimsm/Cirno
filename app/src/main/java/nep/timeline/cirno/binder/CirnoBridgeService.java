package nep.timeline.cirno.binder;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

public class CirnoBridgeService extends Service {
    private final Object lock = new Object();
    private ICirnoService hookService;
    private String initialStatusSnapshot;
    private IBinder.DeathRecipient hookDeathRecipient;

    private final ICirnoBridge.Stub bridge = new ICirnoBridge.Stub() {
        @Override
        public void registerHookBinder(ICirnoService service, String statusSnapshot) {
            enforceSystemCaller();
            synchronized (lock) {
                clearHookBinderLocked();
                if (service == null) {
                    return;
                }
                hookService = service;
                initialStatusSnapshot = statusSnapshot;
                hookDeathRecipient = () -> {
                    synchronized (lock) {
                        clearHookBinderLocked();
                    }
                };
                try {
                    service.asBinder().linkToDeath(hookDeathRecipient, 0);
                } catch (RemoteException e) {
                    clearHookBinderLocked();
                }
            }
        }

        @Override
        public ICirnoService getHookBinder() {
            enforceAppCaller();
            synchronized (lock) {
                if (hookService == null) {
                    return null;
                }
                if (!hookService.asBinder().isBinderAlive()) {
                    clearHookBinderLocked();
                    return null;
                }
                return hookService;
            }
        }

        @Override
        public String getInitialStatusSnapshot() {
            enforceAppCaller();
            synchronized (lock) {
                return initialStatusSnapshot;
            }
        }

        @Override
        public boolean isHookBinderAlive() {
            enforceAppCaller();
            synchronized (lock) {
                return hookService != null && hookService.asBinder().isBinderAlive();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return bridge;
    }

    private void enforceSystemCaller() {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("only system uid can register hook binder");
        }
    }

    private void enforceAppCaller() {
        if (Binder.getCallingUid() != getApplicationInfo().uid) {
            throw new SecurityException("only manager app uid can query hook binder");
        }
    }

    private void clearHookBinderLocked() {
        if (hookService != null && hookDeathRecipient != null) {
            hookService.asBinder().unlinkToDeath(hookDeathRecipient, 0);
        }
        hookService = null;
        initialStatusSnapshot = null;
        hookDeathRecipient = null;
    }
}
