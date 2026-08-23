package nep.timeline.cirno.configs.checkers;

import nep.timeline.cirno.CommonConstants;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.policy.Capability;
import nep.timeline.cirno.configs.policy.PolicyKey;
import nep.timeline.cirno.configs.settings.ApplicationSettings;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.rekernel.ReKernel;
import nep.timeline.cirno.services.AppService;

import java.util.HashSet;
import java.util.Set;

public class AppConfigs {
    public static final int PROCESS_BEHAVIOR_FREEZE = 0;
    public static final int PROCESS_BEHAVIOR_NONE = 1;
    public static final int PROCESS_BEHAVIOR_KILL = 2;
    public static final int BACKGROUND_OOM_ADJ_DEFAULT = Integer.MIN_VALUE;
    public static final int BACKGROUND_OOM_ADJ_MIN = 0;
    public static final int BACKGROUND_OOM_ADJ_MAX = 999;

    private static ApplicationSettings getSafeSettings() {
        // 只读快路径：旧实现每次检查都把旧引用无条件写回 GlobalVars.applicationSettings，
        // 会与配置重载线程竞态——热路径线程可能用旧引用覆盖刚加载的新配置（热更新被静默回滚）
        ApplicationSettings settings = GlobalVars.applicationSettings;
        if (settings != null) {
            return settings;
        }
        synchronized (AppConfigs.class) {
            settings = GlobalVars.applicationSettings;
            if (settings == null) {
                settings = ApplicationSettings.ensureInitialized(null);
                GlobalVars.applicationSettings = settings;
            }
            return settings;
        }
    }

    public static boolean hasAnyNetworkSpeedApps() {
        return !getSafeSettings().networkSpeedApps.isEmpty();
    }

    private static Set<String> getCapabilityApps(Capability capability) {
        switch (capability) {
            case BLACK_LIST:
                return getSafeSettings().blackApps;
            case WHITE_LIST:
                return getSafeSettings().whiteApps;
            case ALLOW_BACKGROUND_AUDIO:
                return getSafeSettings().backgroundPlayApps;
            case ALLOW_LOCATION:
                return getSafeSettings().locationUseApps;
            case ALLOW_NETWORK_MESSAGE:
                return getSafeSettings().networkMessageApps;
            case ALLOW_NETWORK_SPEED:
                return getSafeSettings().networkSpeedApps;
            case BLOCK_AUTOSTART:
                return getSafeSettings().blockAutostartApps;
            default:
                throw new IllegalArgumentException("Unsupported capability: " + capability);
        }
    }

    public static boolean hasCapability(String pkg, int userId, Capability capability) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        return getCapabilityApps(capability).contains(PolicyKey.of(pkg, userId));
    }

    public static void setCapability(String pkg, int userId, Capability capability, boolean enabled) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        if (enabled && capability.isExemption && hasUserWhitelist(pkg, userId)) {
            return;
        }
        Set<String> apps = getCapabilityApps(capability);
        String key = PolicyKey.of(pkg, userId);
        if (enabled) {
            apps.add(key);
            if (capability == Capability.WHITE_LIST) {
                for (Capability cap : Capability.values()) {
                    if (cap.isExemption) {
                        getCapabilityApps(cap).remove(key);
                    }
                }
            }
        } else {
            apps.remove(key);
        }

        if (capability == Capability.ALLOW_NETWORK_MESSAGE) {
            AppRecord record = AppService.get(pkg, userId);
            if (record != null && ReKernel.isRunning()) {
                if (enabled) {
                    ReKernel.monitorNet(record.getUid());
                } else {
                    ReKernel.delMonitorNet(record.getUid());
                }
            }
        }
    }

    public static boolean isBlackApp(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.BLACK_LIST);
    }

    public static boolean isBlackApp(String pkg) {
        return isBlackApp(pkg, 0);
    }

    public static boolean isWhiteApp(String pkg, int userId) {
        return CommonConstants.isWhitelistApps(pkg) || hasCapability(pkg, userId, Capability.WHITE_LIST);
    }

    public static boolean hasUserWhitelist(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.WHITE_LIST);
    }

    public static boolean isWhiteApp(String pkg) {
        return isWhiteApp(pkg, 0);
    }

    public static boolean isBackgroundPlayAllowed(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.ALLOW_BACKGROUND_AUDIO);
    }

    public static boolean isBackgroundPlayAllowed(String pkg) {
        return isBackgroundPlayAllowed(pkg, 0);
    }

    public static boolean isLocationUseAllowed(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.ALLOW_LOCATION);
    }

    public static boolean isLocationUseAllowed(String pkg) {
        return isLocationUseAllowed(pkg, 0);
    }

    public static boolean isNetworkMessageAllowed(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.ALLOW_NETWORK_MESSAGE);
    }

    public static boolean isNetworkMessageAllowed(String pkg) {
        return isNetworkMessageAllowed(pkg, 0);
    }

    public static void setWhiteApp(String pkg, int userId, boolean enabled) {
        if (CommonConstants.isWhitelistApps(pkg)) {
            return;
        }
        setCapability(pkg, userId, Capability.WHITE_LIST, enabled);
    }

    public static void setBlackApp(String pkg, int userId, boolean enabled) {
        setCapability(pkg, userId, Capability.BLACK_LIST, enabled);
    }

    public static void setBlackApp(String pkg, boolean enabled) {
        setBlackApp(pkg, 0, enabled);
    }

    public static void setWhiteApp(String pkg, boolean enabled) {
        setWhiteApp(pkg, 0, enabled);
    }

    public static void setBackgroundPlayAllowed(String pkg, int userId, boolean allowed) {
        setCapability(pkg, userId, Capability.ALLOW_BACKGROUND_AUDIO, allowed);
    }

    public static void setBackgroundPlayAllowed(String pkg, boolean allowed) {
        setBackgroundPlayAllowed(pkg, 0, allowed);
    }

    public static void setLocationUseAllowed(String pkg, int userId, boolean allowed) {
        setCapability(pkg, userId, Capability.ALLOW_LOCATION, allowed);
    }

    public static void setLocationUseAllowed(String pkg, boolean allowed) {
        setLocationUseAllowed(pkg, 0, allowed);
    }

    public static void setNetworkMessageAllowed(String pkg, int userId, boolean allowed) {
        setCapability(pkg, userId, Capability.ALLOW_NETWORK_MESSAGE, allowed);
    }

    public static void setNetworkMessageAllowed(String pkg, boolean allowed) {
        setNetworkMessageAllowed(pkg, 0, allowed);
    }

    public static boolean isNetworkSpeedAllowed(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.ALLOW_NETWORK_SPEED);
    }

    public static boolean isNetworkSpeedAllowed(String pkg) {
        return isNetworkSpeedAllowed(pkg, 0);
    }

    public static void setNetworkSpeedAllowed(String pkg, int userId, boolean allowed) {
        setCapability(pkg, userId, Capability.ALLOW_NETWORK_SPEED, allowed);
    }

    public static void setNetworkSpeedAllowed(String pkg, boolean allowed) {
        setNetworkSpeedAllowed(pkg, 0, allowed);
    }

    public static boolean isAutostartBlocked(String pkg, int userId) {
        return hasCapability(pkg, userId, Capability.BLOCK_AUTOSTART);
    }

    public static boolean isAutostartBlocked(String pkg) {
        return isAutostartBlocked(pkg, 0);
    }

    public static void setAutostartBlocked(String pkg, int userId, boolean blocked) {
        setCapability(pkg, userId, Capability.BLOCK_AUTOSTART, blocked);
    }

    public static void setAutostartBlocked(String pkg, boolean blocked) {
        setAutostartBlocked(pkg, 0, blocked);
    }

    public static boolean isProcessExcludedFromFreeze(String pkg, int userId, String processName) {
        if (pkg == null || processName == null) {
            return false;
        }
        return getSafeSettings().frozenProcessExclusions.contains(PolicyKey.of(pkg, userId) + "#" + processName);
    }

    public static void setProcessExcludedFromFreeze(String pkg, int userId, String processName, boolean excluded) {
        if (pkg == null || processName == null) {
            return;
        }
        String key = PolicyKey.of(pkg, userId) + "#" + processName;
        if (excluded) {
            getSafeSettings().frozenProcessExclusions.add(key);
        } else {
            getSafeSettings().frozenProcessExclusions.remove(key);
        }
    }

    public static boolean isProcessKilled(String pkg, int userId, String processName) {
        if (pkg == null || processName == null) return false;
        return getSafeSettings().killedProcesses.contains(PolicyKey.of(pkg, userId) + "#" + processName);
    }

    public static void setProcessKilled(String pkg, int userId, String processName, boolean killed) {
        if (pkg == null || processName == null) return;
        String key = PolicyKey.of(pkg, userId) + "#" + processName;
        if (killed) getSafeSettings().killedProcesses.add(key);
        else getSafeSettings().killedProcesses.remove(key);
    }

    public static int getProcessBehavior(String pkg, int userId, String processName) {
        if (isProcessKilled(pkg, userId, processName)) return PROCESS_BEHAVIOR_KILL;
        if (isProcessExcludedFromFreeze(pkg, userId, processName)) return PROCESS_BEHAVIOR_NONE;
        return PROCESS_BEHAVIOR_FREEZE;
    }

    public static void setProcessBehavior(String pkg, int userId, String processName, int behavior) {
        setProcessExcludedFromFreeze(pkg, userId, processName, behavior == PROCESS_BEHAVIOR_NONE);
        setProcessKilled(pkg, userId, processName, behavior == PROCESS_BEHAVIOR_KILL);
    }

    public static Set<String> getExcludedProcesses(String pkg, int userId) {
        Set<String> result = new HashSet<>();
        if (pkg == null) {
            return result;
        }
        String prefix = PolicyKey.of(pkg, userId) + "#";
        for (String key : getSafeSettings().frozenProcessExclusions) {
            if (key.startsWith(prefix)) {
                result.add(key.substring(prefix.length()));
            }
        }
        return result;
    }

    public static Set<String> getKilledProcesses(String pkg, int userId) {
        Set<String> result = new HashSet<>();
        if (pkg == null) return result;
        String prefix = PolicyKey.of(pkg, userId) + "#";
        for (String key : getSafeSettings().killedProcesses) {
            if (key.startsWith(prefix)) result.add(key.substring(prefix.length()));
        }
        return result;
    }

    public static boolean isMemoryTrimEnabled(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty()) {
            return true;
        }
        return !getSafeSettings().memoryTrimDisabledApps.contains(PolicyKey.of(pkg, userId));
    }

    public static void setMemoryTrimEnabled(String pkg, int userId, boolean enabled) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        String key = PolicyKey.of(pkg, userId);
        if (enabled) {
            getSafeSettings().memoryTrimDisabledApps.remove(key);
        } else {
            getSafeSettings().memoryTrimDisabledApps.add(key);
        }
    }

    public static boolean hasMemoryTrimConfig(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        return getSafeSettings().memoryTrimDisabledApps.contains(PolicyKey.of(pkg, userId));
    }

    public static boolean isMemoryTrimGcEnabled(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty()) {
            return true;
        }
        return !getSafeSettings().memoryTrimGcDisabledApps.contains(PolicyKey.of(pkg, userId));
    }

    public static void setMemoryTrimGcEnabled(String pkg, int userId, boolean enabled) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        String key = PolicyKey.of(pkg, userId);
        if (enabled) {
            getSafeSettings().memoryTrimGcDisabledApps.remove(key);
        } else {
            getSafeSettings().memoryTrimGcDisabledApps.add(key);
        }
    }

    public static boolean hasMemoryTrimGcConfig(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        return getSafeSettings().memoryTrimGcDisabledApps.contains(PolicyKey.of(pkg, userId));
    }

    public static boolean isValidBackgroundOomAdj(int adj) {
        return adj >= BACKGROUND_OOM_ADJ_MIN && adj <= BACKGROUND_OOM_ADJ_MAX;
    }

    public static int getBackgroundOomAdj(String pkg, int userId) {
        if (pkg == null || pkg.isEmpty()) {
            return BACKGROUND_OOM_ADJ_DEFAULT;
        }
        Integer adj = getSafeSettings().backgroundOomAdjApps.get(PolicyKey.of(pkg, userId));
        if (adj == null || !isValidBackgroundOomAdj(adj)) {
            return BACKGROUND_OOM_ADJ_DEFAULT;
        }
        return adj;
    }

    public static int getBackgroundOomAdj(String pkg) {
        return getBackgroundOomAdj(pkg, 0);
    }

    public static boolean hasBackgroundOomAdj(String pkg, int userId) {
        return getBackgroundOomAdj(pkg, userId) != BACKGROUND_OOM_ADJ_DEFAULT;
    }

    public static void setBackgroundOomAdj(String pkg, int userId, int adj) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        String key = PolicyKey.of(pkg, userId);
        if (isValidBackgroundOomAdj(adj)) {
            getSafeSettings().backgroundOomAdjApps.put(key, adj);
        } else {
            getSafeSettings().backgroundOomAdjApps.remove(key);
        }
    }

    public static void clearBackgroundOomAdj(String pkg, int userId) {
        setBackgroundOomAdj(pkg, userId, BACKGROUND_OOM_ADJ_DEFAULT);
    }
}
