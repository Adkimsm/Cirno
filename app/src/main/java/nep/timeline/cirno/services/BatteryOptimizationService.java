package nep.timeline.cirno.services;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.ConfigManager;
import nep.timeline.cirno.configs.policy.PolicyKey;
import nep.timeline.cirno.configs.settings.ApplicationSettings;
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

    /** One real allowlist snapshot per list refresh, rather than one copy per package. */
    public static int[] getBatteryOptimizationStates(List<String> packageNames, int[] userIds) {
        if (packageNames == null || userIds == null || packageNames.size() != userIds.length) {
            throw new IllegalArgumentException("Package and user counts differ");
        }
        int[] states = new int[packageNames.size()];
        java.util.Arrays.fill(states, -1);
        Object value = controller;
        if (value == null) return states;
        Set<String> whitelist = getUserWhitelist(value);
        if (whitelist == null) return states;
        for (int i = 0; i < states.length; i++) {
            String pkg = packageNames.get(i);
            if (pkg != null && !pkg.isEmpty() && userIds[i] >= 0) {
                states[i] = whitelist.contains(pkg) ? 0 : 1;
            }
        }
        return states;
    }

    public static boolean setBatteryOptimizationEnabled(String packageName, int userId, boolean enabled) {
        synchronized (LOCK) {
            if (!isTakeoverEnabled() || syncing || controller == null
                    || packageName == null || packageName.isEmpty() || userId < 0) {
                return false;
            }
            if (!Boolean.FALSE.equals(isSystemApp(packageName, userId))) {
                Log.w("Battery optimization update skipped: package classification package="
                        + packageName + " userId=" + userId);
                return false;
            }
            Object value = controller;
            if (!isWhitelistBroadcastReady(value)) {
                logWhitelistBroadcastNotReady("update skipped");
                return false;
            }
            Map<String, Boolean> configured = readSavedPreferences();
            if (configured == null || !Boolean.valueOf(enabled).equals(configured.get(PolicyKey.of(packageName, userId)))) {
                Log.w("Battery optimization update skipped: saved preference unavailable package="
                        + packageName + " userId=" + userId);
                return false;
            }
            Target target = getTargets(configured, packageName, BatteryOptimizationService::isSystemApp).get(packageName);
            if (target == Target.UNKNOWN) return false;
            syncing = true;
            try {
                return updateWhitelist(value, packageName, target == Target.ALLOWLIST) != Update.FAILED;
            } finally {
                syncing = false;
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
            Map<String, Boolean> configured = readSavedPreferences();
            if (configured == null) return false;
            Map<String, Target> targets = getTargets(configured, null, BatteryOptimizationService::isSystemApp);
            Set<String> current = getUserWhitelist(value);
            if (current == null) {
                Log.w("Battery optimization sync skipped: failed to read user whitelist");
                return false;
            }
            for (Map.Entry<String, Target> entry : targets.entrySet()) {
                if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                    cancelled = true;
                    rollback = true;
                    return true;
                }
                String packageName = entry.getKey();
                if (entry.getValue() == Target.ALLOWLIST && !current.contains(packageName)) {
                    Update update = updateWhitelist(value, packageName, true);
                    if (update == Update.FAILED) {
                        rollback = true;
                        return false;
                    }
                    if (update == Update.CHANGED) addedBySync.add(packageName);
                }
            }
            for (String packageName : current) {
                if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                    cancelled = true;
                    rollback = true;
                    return true;
                }
                if (!targets.containsKey(packageName) && managedBefore.contains(packageName)
                        && Boolean.FALSE.equals(isSystemAppAcrossUsers(packageName))) {
                    Update update = updateWhitelist(value, packageName, false);
                    if (update == Update.FAILED) {
                        rollback = true;
                        return false;
                    }
                    if (update == Update.CHANGED) removedBySync.add(packageName);
                }
            }
            if (!isTakeoverEnabled() || isSyncCancelled(generation)) {
                cancelled = true;
                rollback = true;
                return true;
            }
            Log.i("Battery optimization whitelist sync completed: targetCount=" + targets.size());
            return true;
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

    private static boolean isTakeoverEnabled() {
        GlobalSettings settings = GlobalVars.globalSettings;
        return settings != null
                && GlobalSettings.BATTERY_OPT_MODE_APP.equals(settings.batteryOptimizationMode);
    }

    private static Map<String, Boolean> readSavedPreferences() {
        ApplicationSettings settings = ConfigManager.manager.readSavedApplicationSettings();
        return settings == null ? null : ApplicationSettings.ensureInitialized(settings).batteryOptimizationApps;
    }

    enum Target { ALLOWLIST, UNKNOWN }

    // DeviceIdleController applies one allowlist entry per package across all users.
    static Map<String, Target> getTargets(Map<String, Boolean> configured, String onlyPackage,
                                          BiFunction<String, Integer, Boolean> isSystemApp) {
        Map<String, Target> targets = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : configured.entrySet()) {
            if (!Boolean.FALSE.equals(entry.getValue()) || entry.getKey() == null) continue;
            String key = entry.getKey();
            int separator = key.lastIndexOf('#');
            String packageName = separator < 0 ? key : key.substring(0, separator);
            if (packageName.isEmpty() || (onlyPackage != null && !onlyPackage.equals(packageName))
                    || targets.get(packageName) == Target.ALLOWLIST) continue;
            if (separator < 0 && configured.containsKey(PolicyKey.of(packageName, 0))) continue;
            int userId;
            try {
                userId = separator < 0 ? 0 : Integer.parseInt(key.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                userId = -1;
            }
            Boolean systemApp = userId < 0 ? null : isSystemApp.apply(packageName, userId);
            if (systemApp == null) {
                targets.put(packageName, Target.UNKNOWN);
            } else if (!systemApp) {
                targets.put(packageName, Target.ALLOWLIST);
            }
        }
        return targets;
    }

    /** Returns null when the package cannot be classified; null is protected from modification. */
    private static Boolean isSystemApp(String packageName, int userId) {
        ApplicationInfo info = ActivityManagerService.getApplicationInfo(packageName, userId);
        if (info == null) {
            Log.w("Failed to classify battery optimization package=" + packageName + " userId=" + userId);
            return null;
        }
        return isSystemApp(info);
    }

    private static Boolean isSystemAppAcrossUsers(String packageName) {
        try {
            Context context = ActivityManagerService.getContext();
            if (context == null) return null;
            int matchAnyUser = CakeReflection.getStaticIntField(PackageManager.class, "MATCH_ANY_USER");
            return isSystemApp(context.getPackageManager().getApplicationInfo(packageName, matchAnyUser));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Throwable e) {
            Log.w("Failed to classify battery optimization package=" + packageName, e);
            return null;
        }
    }

    private static boolean isSystemApp(ApplicationInfo info) {
        return info.uid % 100_000 < android.os.Process.FIRST_APPLICATION_UID
                || (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private enum Update { CHANGED, UNCHANGED, FAILED }

    private static Update updateWhitelist(Object value, String packageName, boolean allowlisted) {
        synchronized (LOCK) {
            if (!isTakeoverEnabled() || controller != value) return Update.FAILED;
            Set<String> before = getUserWhitelist(value);
            if (before == null) return Update.FAILED;
            boolean wasAllowlisted = before.contains(packageName);
            if (wasAllowlisted == allowlisted || (!allowlisted && !managedPackages.contains(packageName))) {
                return Update.UNCHANGED;
            }
            try {
                applyWhitelistState(value, packageName, allowlisted);
                Set<String> after = getUserWhitelist(value);
                if (after != null && after.contains(packageName) == allowlisted) {
                    if (allowlisted) managedPackages.add(packageName);
                    else managedPackages.remove(packageName);
                    return Update.CHANGED;
                }
                Log.w("Battery optimization update verification failed package=" + packageName);
            } catch (Throwable e) {
                Log.w("Battery optimization update failed package=" + packageName, e);
            }
            restoreWhitelistMembership(value, packageName, wasAllowlisted);
            return Update.FAILED;
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
