package nep.timeline.cirno.hooks.android.battery;

import java.util.List;

import nep.timeline.cirno.framework.OverloadMethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;

/** Re-applies the configured policy after framework-side allowlist changes. */
public class DeviceIdleWhitelistUpdateHook extends OverloadMethodHook {
    public DeviceIdleWhitelistUpdateHook(ClassLoader classLoader) { super(classLoader); }

    @Override public String getTargetClass() { return "com.android.server.DeviceIdleController"; }
    @Override public String getTargetMethod() { return "updateWhitelistAppIdsLocked"; }
    @Override protected Object[][] getPreferredParamTypes() {
        return new Object[][] {
                {},
                {String.class, int.class, String.class, List.class}
        };
    }
    @Override protected HookMode getHookMode() {
        return HookMode.AFTER;
    }
    @Override public CakeHooker.Callback getTargetHook() {
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
