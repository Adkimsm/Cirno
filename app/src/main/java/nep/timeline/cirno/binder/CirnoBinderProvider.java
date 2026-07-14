package nep.timeline.cirno.binder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

public class CirnoBinderProvider extends ContentProvider {
    private static final Object lock = new Object();
    private static volatile IBinder cachedBinder;
    private static volatile String cachedStatusSnapshot;
    private static IBinder.DeathRecipient deathRecipient;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("register".equals(method)) {
            if (Binder.getCallingUid() != Process.SYSTEM_UID) {
                throw new SecurityException("only system uid can register hook binder");
            }
            if (extras == null) {
                return null;
            }
            IBinder binder = extras.getBinder("hook_service");
            String snapshot = extras.getString("status_snapshot");
            synchronized (lock) {
                clearLocked();
                if (binder != null) {
                    cachedBinder = binder;
                    cachedStatusSnapshot = snapshot;
                    deathRecipient = () -> {
                        synchronized (lock) {
                            clearLocked();
                        }
                    };
                    try {
                        binder.linkToDeath(deathRecipient, 0);
                    } catch (RemoteException e) {
                        clearLocked();
                    }
                }
            }
            return null;
        }
        if ("get".equals(method)) {
            if (getContext() != null && Binder.getCallingUid() != getContext().getApplicationInfo().uid) {
                throw new SecurityException("only manager app uid can query hook binder");
            }
            Bundle result = new Bundle();
            synchronized (lock) {
                if (cachedBinder != null) {
                    result.putBinder("hook_service", cachedBinder);
                }
                if (cachedStatusSnapshot != null) {
                    result.putString("status_snapshot", cachedStatusSnapshot);
                }
            }
            return result;
        }
        return null;
    }

    public static ICirnoService getHookService() {
        IBinder binder = cachedBinder;
        if (binder == null || !binder.isBinderAlive()) {
            return null;
        }
        return ICirnoService.Stub.asInterface(binder);
    }

    public static String getCachedStatusSnapshot() {
        return cachedStatusSnapshot;
    }

    private static void clearLocked() {
        if (cachedBinder != null && deathRecipient != null) {
            cachedBinder.unlinkToDeath(deathRecipient, 0);
        }
        cachedBinder = null;
        cachedStatusSnapshot = null;
        deathRecipient = null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
