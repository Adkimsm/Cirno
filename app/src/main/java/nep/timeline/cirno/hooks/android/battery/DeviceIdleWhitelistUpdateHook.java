package nep.timeline.cirno.hooks.android.battery;

import java.util.List;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;

/** Re-applies the configured policy after framework-side allowlist changes. */
public class DeviceIdleWhitelistUpdateHook extends MethodHook {
    private static final String TARGET_CLASS = "com.android.server.DeviceIdleController";
    private static final String TARGET_METHOD = "updateWhitelistAppIdsLocked";

    public DeviceIdleWhitelistUpdateHook(ClassLoader classLoader) { super(classLoader); }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.overloads(
                        TARGET_CLASS,
                        TARGET_METHOD,
                        new Object[][] {
                                {},
                                {String.class, int.class, String.class, List.class}
                        },
                        method -> method.getName().equals(TARGET_METHOD),
                        targetCallback())
        };
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override public void call(CakeHooker.AfterHookCallback callback) {
                if (!BatteryOptimizationService.isSyncing()) {
                    // 合并框架在短时间内连续触发的白名单更新。
                    BatteryOptimizationService.requestSync(200);
                }
            }
        };
    }
}
