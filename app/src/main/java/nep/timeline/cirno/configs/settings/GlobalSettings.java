package nep.timeline.cirno.configs.settings;

public class GlobalSettings {
    public static final String BATTERY_OPT_MODE_APP = "app";
    public static final String BATTERY_OPT_MODE_DISABLED = "disabled";
    public static final String LOG_LEVEL_NONE = "none";
    public static final String LOG_LEVEL_INFO = "info";
    public static final String LOG_LEVEL_DEBUG = "debug";
    public static final String FREEZER_MODE_UID = "uid";
    public static final String FREEZER_MODE_FROZEN = "frozen";
    public static final String HOOK_TYPE_AUTO = "auto";
    public static final String HOOK_TYPE_MILLET = "millet";
    public static final String HOOK_TYPE_HANS = "hans";
    public static final String HOOK_TYPE_VIVO = "vivo";
    public static final String HOOK_TYPE_REKERNEL = "rekernel";
    public static final String HOOK_TYPE_REKERNEL_EBPF = "rekernel-ebpf";
    public static final String HOOK_TYPE_NKBINDER = "nkbinder";

    public int netlinkUnit;
    public String hookType = HOOK_TYPE_AUTO;
    public String freezerMode = FREEZER_MODE_UID;
    public int freezeDelay = 5;
    public int wakeFreezeDelay = 30;
    public int networkSpeedThreshold = 102400;
    public String batteryOptimizationMode = BATTERY_OPT_MODE_DISABLED;
    public String logLevel = LOG_LEVEL_INFO;

    public static GlobalSettings ensureInitialized(GlobalSettings settings) {
        if (settings == null) {
            return new GlobalSettings();
        }
        if (settings.hookType == null) {
            settings.hookType = HOOK_TYPE_AUTO;
        }
        if ("all_user_apps".equals(settings.batteryOptimizationMode)
                || "clear_user_apps".equals(settings.batteryOptimizationMode)) {
            settings.batteryOptimizationMode = BATTERY_OPT_MODE_DISABLED;
        } else if (!BATTERY_OPT_MODE_APP.equals(settings.batteryOptimizationMode)
                && !BATTERY_OPT_MODE_DISABLED.equals(settings.batteryOptimizationMode)) {
            settings.batteryOptimizationMode = BATTERY_OPT_MODE_APP;
        }
        if (!FREEZER_MODE_FROZEN.equals(settings.freezerMode) && !FREEZER_MODE_UID.equals(settings.freezerMode)) {
            settings.freezerMode = FREEZER_MODE_UID;
        }
        // 数值下限钳制：手工编辑 JSON 写入负值/0 不应破坏冻结防抖与轮询节奏
        if (settings.freezeDelay < 0) {
            settings.freezeDelay = 5;
        }
        if (settings.wakeFreezeDelay < 1) {
            settings.wakeFreezeDelay = 30;
        }
        if (settings.networkSpeedThreshold < 1) {
            settings.networkSpeedThreshold = 102400;
        }
        if (!LOG_LEVEL_NONE.equals(settings.logLevel)
                && !LOG_LEVEL_INFO.equals(settings.logLevel)
                && !LOG_LEVEL_DEBUG.equals(settings.logLevel)) {
            settings.logLevel = LOG_LEVEL_INFO;
        }
        return settings;
    }
}
