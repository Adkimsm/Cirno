package nep.timeline.cirno.services;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;

/** Owns only the permanent user allowlist; temporary and system entries are untouched. */
public final class BatteryOptimizationService {
    private static final Object LOCK = new Object();
    private static volatile boolean syncing;
    private static volatile boolean syncPending;
    private static volatile long syncGeneration;
    private static final ThreadLocal<Boolean> syncCaller = new ThreadLocal<>();
    private static volatile Object controller;
    private static volatile boolean controllerMissingLogged;
    private static volatile boolean whitelistBroadcastReady;
    private static volatile boolean whitelistBroadcastNotReadyLogged;
    private static final Set<String> managedPackages = new HashSet<>();
    private static final Runnable SYNC_TASK = BatteryOptimizationService::sync;
    private BatteryOptimizationService() {
    }

    public static Object getController() {
        return controller;
    }

    public static void setController(Object value) {
        if (value == null) return;
        boolean changed;
        synchronized (LOCK) {
            changed = controller != value;
            controller = value;
            if (changed) {
                syncGeneration++;
                syncPending = false;
            }
        }
        if (changed) {
            Handlers.config.removeCallbacks(SYNC_TASK);
            controllerMissingLogged = false;
            whitelistBroadcastReady = false;
            whitelistBroadcastNotReadyLogged = false;
            Log.i("Battery optimization controller initialized: " + value.getClass().getName());
            if (isTakeoverEnabled()) {
                requestSync(0);
            }
        }
    }

    /** 清理所有待执行的同步任务，用于热重载前清理。 */
    public static void clearPendingSync() {
        Handlers.config.removeCallbacks(SYNC_TASK);
        synchronized (LOCK) {
            syncPending = false;
            syncGeneration++;
        }
        Log.i("Battery optimization pending sync tasks cleared");
    }

    public static void requestSync(long delayMs) {
        if (!isTakeoverEnabled()) {
            clearPendingSync();
            return;
        }
        synchronized (LOCK) {
            syncPending = true;
        }
        Handlers.config.removeCallbacks(SYNC_TASK);
        Handlers.config.postDelayed(SYNC_TASK, Math.max(0L, delayMs));
    }

    public static boolean isBatteryOptimizationEnabled(String packageName, int userId) {
        return getBatteryOptimizationState(packageName, userId) != BatteryOptimizationState.DISABLED;
    }

    public static BatteryOptimizationState getBatteryOptimizationState(String packageName, int userId) {
        Object value = controller;
        if (value == null) {
            if (!controllerMissingLogged) {
                controllerMissingLogged = true;
                Log.w("Battery optimization query unknown: controller is null; package="
                        + packageName + " userId=" + userId);
            }
            return BatteryOptimizationState.UNKNOWN;
        }
        if (packageName == null || packageName.isEmpty()) {
            Log.w("Battery optimization query unknown: package is empty userId=" + userId);
            return BatteryOptimizationState.UNKNOWN;
        }
        try {
            Set<String> whitelist = getUserWhitelist(value);
            if (whitelist == null) return BatteryOptimizationState.UNKNOWN;
            return whitelist.contains(packageName)
                    ? BatteryOptimizationState.DISABLED : BatteryOptimizationState.ENABLED;
        } catch (Throwable e) {
            Log.w("Battery optimization query unknown: " + packageName, e);
            return BatteryOptimizationState.UNKNOWN;
        }
    }

    public enum BatteryOptimizationState { UNKNOWN, DISABLED, ENABLED }

    public static boolean setBatteryOptimizationEnabled(String packageName, int userId, boolean enabled) {
        if (!isTakeoverEnabled()) {
            Log.i("Battery optimization update skipped: takeover is disabled");
            return false;
        }
        Boolean systemApp = isSystemApp(packageName);
        if (systemApp == null || systemApp) {
            Log.i("Battery optimization update skipped: system app or package classification unavailable package="
                    + packageName);
            return false;
        }
        if (syncing && !Boolean.TRUE.equals(syncCaller.get())) {
            Log.i("Battery optimization update skipped: whitelist sync is in progress");
            return false;
        }
        Object value = controller;
        if (value == null || packageName == null || packageName.isEmpty()) {
            Log.w("Battery optimization update skipped: controller/package unavailable package="
                    + packageName + " userId=" + userId + " enabled=" + enabled);
            return false;
        }
        if (!isWhitelistBroadcastReady(value)) {
            logWhitelistBroadcastNotReady("update skipped");
            return false;
        }
        synchronized (LOCK) {
            if (!isTakeoverEnabled()) {
                Log.i("Battery optimization update skipped: takeover is disabled");
                return false;
            }
            if (controller != value) {
                Log.i("Battery optimization update skipped: controller changed");
                return false;
            }
            if (syncing && !Boolean.TRUE.equals(syncCaller.get())) {
                Log.i("Battery optimization update skipped: whitelist sync is in progress");
                return false;
            }
            boolean wasSyncing = syncing;
            boolean wasManaged = managedPackages.contains(packageName);
            Set<String> before = null;
            syncing = true;
            try {
                before = getUserWhitelist(value);
                if (before == null) {
                    Log.w("Battery optimization update skipped: failed to read user whitelist package="
                            + packageName + " userId=" + userId);
                    return false;
                }
                if (enabled) {
                    CakeReflection.callMethod(value, "removePowerSaveWhitelistAppInternal", packageName);
                } else {
                    List<String> packages = new ArrayList<>();
                    packages.add(packageName);
                    CakeReflection.callMethod(value, "addPowerSaveWhitelistAppsInternal", packages);
                }
                BatteryOptimizationState actual = getBatteryOptimizationState(packageName, userId);
                boolean success = (actual == (enabled
                        ? BatteryOptimizationState.ENABLED : BatteryOptimizationState.DISABLED));
                if (!success) {
                    restoreWhitelistMembership(value, packageName, before.contains(packageName));
                    restoreManagedPackage(packageName, wasManaged);
                    Log.w("Battery optimization update verification failed package=" + packageName
                            + " userId=" + userId + " enabled=" + enabled + " actual=" + actual
                            + " userWhitelistBefore=" + before + " userWhitelistAfter=" + getUserWhitelist(value));
                } else if (enabled) {
                    managedPackages.remove(packageName);
                } else if (!before.contains(packageName)) {
                    // Only remember packages Cirno actually added. An already allowlisted
                    // package may have been added externally and must remain protected.
                    managedPackages.add(packageName);
                }
                return success;
            } catch (Throwable e) {
                if (before != null) {
                    restoreWhitelistMembership(value, packageName, before.contains(packageName));
                    restoreManagedPackage(packageName, wasManaged);
                }
                Log.w("Battery optimization update failed package=" + packageName + " userId=" + userId
                        + " enabled=" + enabled + " controller=" + value.getClass().getName(), e);
                return false;
            } finally {
                syncing = wasSyncing;
            }
        }
    }

    public static boolean sync() {
        Object value = controller;
        if (!isTakeoverEnabled()) {
            synchronized (LOCK) {
                syncPending = false;
            }
            return true;
        }
        final long generation;
        synchronized (LOCK) {
            if (syncing) {
                syncPending = true;
                return true;
            }
            syncPending = false;
            syncing = true;
            generation = syncGeneration;
            value = controller;
        }
        Set<String> addedBySync = new HashSet<>();
        Set<String> removedBySync = new HashSet<>();
        Set<String> managedBefore;
        boolean rollback = false;
        boolean cancelled = false;
        syncCaller.set(true);
        synchronized (LOCK) {
            managedBefore = new HashSet<>(managedPackages);
        }
        try {
            if (isSyncCancelled(generation)) return true;
            if (value == null) {
                Log.w("Battery optimization sync skipped: controller is null");
                return false;
            }
            if (!isWhitelistBroadcastReady(value)) {
                logWhitelistBroadcastNotReady("sync deferred");
                return false;
            }
            GlobalSettings settings = GlobalVars.globalSettings;
            if (settings == null) {
                Log.w("Battery optimization sync skipped: global settings not ready");
                return false;
            }
            if (!isTakeoverEnabled()) {
                cancelled = true;
                rollback = true;
                return true;
            }
            Set<String> packages = getTargetPackages();
            Set<String> current = getUserWhitelist(value);
            if (current == null) {
                Log.w("Battery optimization sync skipped: failed to read user whitelist");
                return false;
            }
            boolean success = true;
            for (String packageName : packages) {
                if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                    cancelled = true;
                    rollback = true;
                    return true;
                }
                if (!Boolean.FALSE.equals(isSystemApp(packageName))) {
                    continue;
                }
                if (!current.contains(packageName)) {
                    if (!setBatteryOptimizationEnabled(packageName, 0, false)) {
                        rollback = true;
                        return false;
                    }
                    addedBySync.add(packageName);
                }
            }
            for (String packageName : current) {
                if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                    cancelled = true;
                    rollback = true;
                    return true;
                }
                if (!packages.contains(packageName)
                        && isManagedPackage(packageName)
                        && Boolean.FALSE.equals(isSystemApp(packageName))) {
                    if (!setBatteryOptimizationEnabled(packageName, 0, true)) {
                        rollback = true;
                        return false;
                    }
                    removedBySync.add(packageName);
                }
            }
            if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                cancelled = true;
                rollback = true;
                return true;
            }
            Log.i("Battery optimization whitelist sync completed: success=" + success
                    + " targetCount=" + packages.size());
            return success;
        } catch (Throwable e) {
            rollback = true;
            Log.w("Battery optimization whitelist sync failed", e);
            return false;
        } finally {
            if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                cancelled = true;
                rollback = true;
            }
            if (rollback && controller == value) {
                restoreSyncChanges(value, addedBySync, removedBySync, managedBefore);
            }
            boolean rerun;
            synchronized (LOCK) {
                syncing = false;
                rerun = syncPending && isTakeoverEnabled()
                        && (generation != syncGeneration || (!rollback && !cancelled));
                syncPending = false;
            }
            syncCaller.remove();
            if (rerun) {
                requestSync(0);
            }
        }
    }

    public static boolean isSyncing() {
        return syncing;
    }

    private static boolean isSyncCancelled(long generation) {
        return generation != syncGeneration;
    }

    private static Set<String> getTargetPackages() {
        Set<String> result = new HashSet<>();
        addConfiguredValues(result, false);
        result.removeIf(packageName -> !Boolean.FALSE.equals(isSystemApp(packageName)));
        return result;
    }

    private static boolean isTakeoverEnabled() {
        GlobalSettings settings = GlobalVars.globalSettings;
        return settings != null
                && GlobalSettings.BATTERY_OPT_MODE_APP.equals(settings.batteryOptimizationMode);
    }

    private static boolean isManagedPackage(String packageName) {
        synchronized (LOCK) {
            return managedPackages.contains(packageName);
        }
    }

    /** Returns null when the package cannot be classified; null is protected from modification. */
    private static Boolean isSystemApp(String packageName) {
        Context context = ActivityManagerService.getContext();
        if (context == null || packageName == null || packageName.isEmpty()) return null;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return info.uid < android.os.Process.FIRST_APPLICATION_UID
                    || (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Throwable e) {
            Log.w("Failed to classify battery optimization package=" + packageName, e);
            return null;
        }
    }

    private static void restoreManagedPackage(String packageName, boolean managed) {
        if (managed) {
            managedPackages.add(packageName);
        } else {
            managedPackages.remove(packageName);
        }
    }

    private static void applyWhitelistState(Object value, String packageName, boolean allowlisted) {
        if (allowlisted) {
            List<String> packages = new ArrayList<>();
            packages.add(packageName);
            CakeReflection.callMethod(value, "addPowerSaveWhitelistAppsInternal", packages);
        } else {
            CakeReflection.callMethod(value, "removePowerSaveWhitelistAppInternal", packageName);
        }
    }

    private static void restoreWhitelistMembership(
            Object value,
            String packageName,
            boolean allowlisted
    ) {
        try {
            Set<String> current = getUserWhitelist(value);
            if (current != null && current.contains(packageName) != allowlisted) {
                applyWhitelistState(value, packageName, allowlisted);
            }
        } catch (Throwable e) {
            Log.w("Failed to restore battery optimization whitelist package=" + packageName, e);
        }
    }

    private static void restoreSyncChanges(
            Object value,
            Set<String> addedBySync,
            Set<String> removedBySync,
            Set<String> managedBefore
    ) {
        if (value != null) {
            for (String packageName : addedBySync) {
                restoreWhitelistMembership(value, packageName, false);
            }
            for (String packageName : removedBySync) {
                restoreWhitelistMembership(value, packageName, true);
            }
        }
        synchronized (LOCK) {
            managedPackages.clear();
            managedPackages.addAll(managedBefore);
        }
    }

    private static void addConfiguredValues(Set<String> result, boolean enabled) {
        if (GlobalVars.applicationSettings == null) return;
        for (Map.Entry<String, Boolean> entry
                : GlobalVars.applicationSettings.batteryOptimizationApps.entrySet()) {
            if (entry.getValue() == null || entry.getValue() != enabled) continue;
            String key = entry.getKey();
            int separator = key.lastIndexOf('#');
            String packageName = separator > 0 ? key.substring(0, separator) : key;
            if (!packageName.isEmpty()) result.add(packageName);
        }
    }

    /**
     * A14+ 的 DeviceIdleController 只在 onBootPhase(PHASE_SYSTEM_SERVICES_READY == 500) 里创建
     * mPowerSaveWhitelistChangedIntent；在那之前增删用户白名单，框架会在
     * reportPowerSaveWhitelistChangedLocked() -> sendBroadcastAsUser(null, ...) 抛 NPE，
     * 并跳过 updateWhitelistAppIdsLocked()/writeConfigFileLocked()。
     * onStart() 内部也会调用 updateWhitelistAppIdsLocked()（DeviceIdleWhitelistUpdateHook 据此
     * 安排 200ms 后的同步），所以开机时必然会命中这个窗口。
     * A12/A13 的 report 方法内部局部 new Intent，不存在该问题；字段不存在时不拦截。
     */
    private static boolean isWhitelistBroadcastReady(Object value) {
        if (whitelistBroadcastReady) return true;
        boolean ready;
        try {
            ready = CakeReflection.getObjectField(value, "mPowerSaveWhitelistChangedIntent") != null;
        } catch (Throwable e) {
            ready = true;
        }
        if (ready) whitelistBroadcastReady = true;
        return ready;
    }

    private static void logWhitelistBroadcastNotReady(String action) {
        if (whitelistBroadcastNotReadyLogged) return;
        whitelistBroadcastNotReadyLogged = true;
        Log.w("Battery optimization " + action
                + ": DeviceIdleController whitelist broadcast intent not ready (before PHASE_SYSTEM_SERVICES_READY)");
    }

    private static Set<String> getUserWhitelist(Object value) {
        try {
            Object field = CakeReflection.getObjectField(value, "mPowerSaveWhitelistUserApps");
            Object keys = CakeReflection.callMethod(field, "keySet");
            Set<String> result = new HashSet<>();
            if (keys instanceof Set<?>) {
                for (Object key : (Set<?>) keys) if (key instanceof String) result.add((String) key);
                return result;
            }
            Log.w("Failed to read battery optimization user whitelist: keySet unavailable");
        } catch (Throwable e) {
            Log.w("Failed to read battery optimization user whitelist", e);
        }
        return null;
    }

}
