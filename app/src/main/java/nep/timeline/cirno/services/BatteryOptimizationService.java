package nep.timeline.cirno.services;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.log.Log;

/** Owns only the permanent user allowlist; temporary and system entries are untouched. */
public final class BatteryOptimizationService {
    private static final Object LOCK = new Object();
    private static final ThreadLocal<Boolean> syncing = new ThreadLocal<>();
    private static volatile Object controller;
    private static volatile boolean controllerMissingLogged;

    private BatteryOptimizationService() {
    }

    public static Object getController() {
        return controller;
    }

    public static void setController(Object value) {
        if (value == null) return;
        boolean changed = controller != value;
        controller = value;
        if (changed) {
            controllerMissingLogged = false;
            Log.i("Battery optimization controller initialized: " + value.getClass().getName());
        }
    }

    public static boolean isBatteryOptimizationEnabled(String packageName, int userId) {
        Object value = controller;
        if (value == null) {
            if (!controllerMissingLogged) {
                controllerMissingLogged = true;
                Log.w("Battery optimization query skipped: controller is null; package="
                        + packageName + " userId=" + userId);
            }
            return true;
        }
        if (packageName == null) {
            Log.w("Battery optimization query skipped: package is null userId=" + userId);
            return true;
        }
        try {
            Set<String> whitelist = getUserWhitelist(value);
            return !whitelist.contains(packageName);
        } catch (Throwable e) {
            Log.w("Battery optimization query failed: " + packageName, e);
            return true;
        }
    }

    public static boolean setBatteryOptimizationEnabled(String packageName, int userId, boolean enabled) {
        Object value = controller;
        if (value == null) {
            Log.w("Battery optimization update skipped: controller is null package=" + packageName
                    + " userId=" + userId + " enabled=" + enabled);
            return false;
        }
        if (packageName == null || packageName.isEmpty()) {
            Log.w("Battery optimization update skipped: package is empty userId=" + userId
                    + " enabled=" + enabled);
            return false;
        }
        synchronized (LOCK) {
            boolean nested = Boolean.TRUE.equals(syncing.get());
            if (!nested) syncing.set(true);
            try {
                Set<String> before = getUserWhitelist(value);
                Object invocationResult;
                if (enabled) {
                    invocationResult = CakeReflection.callMethod(value,
                            "removePowerSaveWhitelistAppInternal", packageName);
                } else {
                    List<String> packages = new ArrayList<>();
                    packages.add(packageName);
                    invocationResult = CakeReflection.callMethod(value,
                            "addPowerSaveWhitelistAppsInternal", packages);
                }
                boolean actual = isBatteryOptimizationEnabled(packageName, userId);
                boolean success = actual == enabled;
                if (!success) {
                    Log.w("Battery optimization update verification failed package=" + packageName
                            + " userId=" + userId + " enabled=" + enabled + " actual=" + actual
                            + " invocationResult=" + invocationResult + " userWhitelistBefore=" + before
                            + " userWhitelistAfter=" + getUserWhitelist(value));
                }
                return success;
            } catch (Throwable e) {
                Log.w("Battery optimization update failed package=" + packageName + " userId=" + userId
                        + " enabled=" + enabled + " controller=" + value.getClass().getName(), e);
                return false;
            } finally {
                if (!nested) syncing.remove();
            }
        }
    }

    public static boolean sync() {
        Object value = controller;
        if (value == null) return false;
        if (Boolean.TRUE.equals(syncing.get())) return true;
        synchronized (LOCK) {
            syncing.set(true);
            try {
                GlobalSettings settings = GlobalVars.globalSettings;
                if (settings == null) return false;
                Set<String> packages = getTargetPackages(settings.batteryOptimizationMode);
                Set<String> current = getUserWhitelist(value);
                boolean batchMode = !GlobalSettings.BATTERY_OPT_MODE_APP.equals(settings.batteryOptimizationMode);
                boolean success = true;
                for (String packageName : packages) {
                    if (!current.contains(packageName)) {
                        success &= setBatteryOptimizationEnabled(packageName, 0, false);
                    }
                }
                for (String packageName : current) {
                    if (!packages.contains(packageName) && (!batchMode || isNonSystemPackage(packageName))) {
                        success &= setBatteryOptimizationEnabled(packageName, 0, true);
                    }
                }
                return success;
            } catch (Throwable e) {
                Log.w("Battery optimization whitelist sync failed", e);
                return false;
            } finally {
                syncing.remove();
            }
        }
    }

    public static boolean isSyncing() {
        return Boolean.TRUE.equals(syncing.get());
    }

    private static Set<String> getTargetPackages(String mode) {
        Set<String> result = new HashSet<>();
        if (GlobalSettings.BATTERY_OPT_MODE_APP.equals(mode)) {
            if (GlobalVars.applicationSettings != null) {
                for (Map.Entry<String, Boolean> entry : GlobalVars.applicationSettings.batteryOptimizationApps.entrySet()) {
                    if (Boolean.FALSE.equals(entry.getValue())) {
                        String key = entry.getKey();
                        int separator = key.lastIndexOf('#');
                        result.add(separator > 0 ? key.substring(0, separator) : key);
                    }
                }
            }
            return result;
        }
        Context context = ActivityManagerService.getContext();
        if (GlobalSettings.BATTERY_OPT_MODE_CLEAR_USER_APPS.equals(mode)) {
            addConfiguredValues(result, false);
            return result;
        }
        if (context == null) return result;
        PackageManager pm = context.getPackageManager();
        try {
            UserManager userManager = context.getSystemService(UserManager.class);
            List<?> users = null;
            if (userManager != null) {
                try {
                    Object value = userManager.getClass().getMethod("getUsers").invoke(userManager);
                    if (value instanceof List<?>) users = (List<?>) value;
                } catch (Throwable ignored) {
                }
            }
            if (users == null || users.isEmpty()) {
                users = java.util.Collections.singletonList(null);
            }
            for (Object user : users) {
                int userId = getUserId(user);
                List<ApplicationInfo> installed = getInstalledApplicationsAsUser(pm, userId);
                for (ApplicationInfo info : installed) {
                    if (info != null && info.packageName != null && !isSystemApp(info)
                            && !isConfiguredValue(info.packageName, true)) {
                        result.add(info.packageName);
                    }
                }
            }
        } catch (Throwable e) {
            Log.w("Failed to enumerate user applications", e);
        }
        removeConfiguredValues(result, true);
        return result;
    }

    private static int getUserId(Object user) {
        if (user == null) return 0;
        try {
            return user.getClass().getField("id").getInt(user);
        } catch (Throwable ignored) {
            try {
                java.lang.reflect.Field field = user.getClass().getDeclaredField("id");
                field.setAccessible(true);
                return field.getInt(user);
            } catch (Throwable ignoredAgain) {
                return 0;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ApplicationInfo> getInstalledApplicationsAsUser(PackageManager pm, int userId) {
        try {
            java.lang.reflect.Method method = pm.getClass().getMethod(
                    "getInstalledApplicationsAsUser", int.class, int.class);
            Object value = method.invoke(pm, 0, userId);
            if (value instanceof List<?>) return (List<ApplicationInfo>) value;
        } catch (Throwable ignored) {
        }
        try {
            return pm.getInstalledApplications(0);
        } catch (Throwable ignored) {
            return java.util.Collections.emptyList();
        }
    }

    private static boolean isNonSystemPackage(String packageName) {
        Context context = ActivityManagerService.getContext();
        if (context == null) return true;
        try {
            return !isSystemApp(context.getPackageManager().getApplicationInfo(packageName, 0));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void addConfiguredValues(Set<String> result, boolean enabled) {
        if (GlobalVars.applicationSettings == null) return;
        for (Map.Entry<String, Boolean> entry : GlobalVars.applicationSettings.batteryOptimizationApps.entrySet()) {
            if (entry.getValue() == null || entry.getValue() != enabled) continue;
            String key = entry.getKey();
            int separator = key.lastIndexOf('#');
            result.add(separator > 0 ? key.substring(0, separator) : key);
        }
    }

    private static void removeConfiguredValues(Set<String> result, boolean enabled) {
        if (GlobalVars.applicationSettings == null) return;
        for (Map.Entry<String, Boolean> entry : GlobalVars.applicationSettings.batteryOptimizationApps.entrySet()) {
            if (entry.getValue() == null || entry.getValue() != enabled) continue;
            String key = entry.getKey();
            int separator = key.lastIndexOf('#');
            result.remove(separator > 0 ? key.substring(0, separator) : key);
        }
    }

    private static boolean isConfiguredValue(String packageName, boolean enabled) {
        if (GlobalVars.applicationSettings == null) return false;
        for (Map.Entry<String, Boolean> entry : GlobalVars.applicationSettings.batteryOptimizationApps.entrySet()) {
            String key = entry.getKey();
            int separator = key.lastIndexOf('#');
            String configuredPackage = separator > 0 ? key.substring(0, separator) : key;
            if (packageName.equals(configuredPackage) && entry.getValue() != null
                    && entry.getValue() == enabled) return true;
        }
        return false;
    }

    private static Set<String> getUserWhitelist(Object value) {
        Set<String> result = new HashSet<>();
        try {
            Object field = CakeReflection.getObjectField(value, "mPowerSaveWhitelistUserApps");
            Object keys = CakeReflection.callMethod(field, "keySet");
            if (keys instanceof Set<?>) {
                for (Object key : (Set<?>) keys) if (key instanceof String) result.add((String) key);
            }
        } catch (Throwable e) {
            Log.w("Failed to read battery optimization user whitelist", e);
        }
        return result;
    }

    private static boolean isSystemApp(ApplicationInfo info) {
        return info.uid < android.os.Process.FIRST_APPLICATION_UID
                || (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }
}
