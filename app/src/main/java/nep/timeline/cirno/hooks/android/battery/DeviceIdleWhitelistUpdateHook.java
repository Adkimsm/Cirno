package nep.timeline.cirno.hooks.android.battery;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;

/** Re-applies the configured policy after framework-side allowlist changes. */
public class DeviceIdleWhitelistUpdateHook extends MethodHook {
    public DeviceIdleWhitelistUpdateHook(ClassLoader classLoader) { super(classLoader); }

    @Override public String getTargetClass() { return "com.android.server.DeviceIdleController"; }
    @Override public String getTargetMethod() { return "updateWhitelistAppIdsLocked"; }
    @Override public Object[] getTargetParam() { return new Object[0]; }
    @Override public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override public void call(CakeHooker.AfterHookCallback callback) {
                if (!BatteryOptimizationService.isSyncing()) {
                    BatteryOptimizationService.sync();
                }
            }
        };
    }
}
