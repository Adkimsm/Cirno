package nep.timeline.cirno.hooks.android.freeze;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.hooks.android.xiaomi.XiaomiHooks;
import nep.timeline.cirno.rekernel.ReKernel;

public final class FreezeBackend {
    private FreezeBackend() {
    }

    public static boolean shouldHandleMilletEvents() {
        String hookType = getHookType();
        if (GlobalSettings.HOOK_TYPE_MILLET.equals(hookType)) {
            return true;
        }
        if (GlobalSettings.HOOK_TYPE_AUTO.equals(hookType)) {
            return !ReKernel.isRunning();
        }
        return false;
    }

    public static boolean shouldHandleHansEvents() {
        String hookType = getHookType();
        if (GlobalSettings.HOOK_TYPE_HANS.equals(hookType)) {
            return true;
        }
        if (GlobalSettings.HOOK_TYPE_AUTO.equals(hookType)) {
            return !ReKernel.isRunning() && !XiaomiHooks.isAvailable();
        }
        return false;
    }

    public static boolean shouldHandleVivoEvents() {
        String hookType = getHookType();
        if (GlobalSettings.HOOK_TYPE_VIVO.equals(hookType)) {
            return true;
        }
        if (GlobalSettings.HOOK_TYPE_AUTO.equals(hookType)) {
            return !ReKernel.isRunning();
        }
        return false;
    }

    private static String getHookType() {
        return GlobalVars.globalSettings != null
                ? GlobalVars.globalSettings.hookType
                : GlobalSettings.HOOK_TYPE_AUTO;
    }
}
