package nep.timeline.cirno.hooks.android.freeze;

import android.os.Build;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.binder.HansKernelUnfreezeHook;
import nep.timeline.cirno.hooks.android.xiaomi.GreezeManagerServiceHook;
import nep.timeline.cirno.hooks.android.xiaomi.MilletMonitorHook;
import nep.timeline.cirno.hooks.android.xiaomi.XiaomiHooks;
import nep.timeline.cirno.rekernel.ReKernel;
import nep.timeline.cirno.services.NkBinderService;
import nep.timeline.cirno.services.StatusBinderHub;

public class FreezeHookManager {
    private final XiaomiHooks xiaomiHooks;
    private final MethodHook hansHook;

    public FreezeHookManager(ClassLoader classLoader) {
        new GreezeManagerServiceHook(classLoader);
        new MilletMonitorHook(classLoader);
        xiaomiHooks = new XiaomiHooks(classLoader);
        hansHook = new HansKernelUnfreezeHook(classLoader);
    }

    public void start(ClassLoader classLoader) {
        StatusBinderHub.setSignal("available_millet", XiaomiHooks.isAvailable() ? "1" : "0");
        StatusBinderHub.setSignal("available_hans", hansHook.isHooked() ? "1" : "0");
        StatusBinderHub.setSignal("available_rekernel", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "1" : "0");
        StatusBinderHub.setSignal("available_nkbinder", NkBinderService.isAvailable() ? "1" : "0");

        String hookType = GlobalVars.globalSettings != null
                ? GlobalVars.globalSettings.hookType : GlobalSettings.HOOK_TYPE_AUTO;

        Runnable onReKernelConnected = () -> {
            xiaomiHooks.unhookAll();
            hansHook.unhook();
        };

        switch (hookType) {
            case GlobalSettings.HOOK_TYPE_MILLET -> {
                if (XiaomiHooks.isAvailable()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Millet");
                }
            }
            case GlobalSettings.HOOK_TYPE_HANS -> {
                if (hansHook.isHooked()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Hans");
                }
            }
            case GlobalSettings.HOOK_TYPE_REKERNEL -> {
                StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel");
                ReKernel.start(classLoader, onReKernelConnected);
            }
            case GlobalSettings.HOOK_TYPE_NKBINDER -> {
                xiaomiHooks.unhookAll();
                hansHook.unhook();
                NkBinderService.start(classLoader);
            }
            default -> {
                // Auto: ReKernel > Millet/Hans > nkBinder
                ReKernel.start(classLoader, onReKernelConnected);
                if (XiaomiHooks.isAvailable()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Millet");
                } else if (hansHook.isHooked()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Hans");
                } else if (NkBinderService.isAvailable()) {
                    NkBinderService.start(classLoader);
                }
            }
        }
    }
}
