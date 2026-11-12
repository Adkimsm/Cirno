package nep.timeline.cirno.hooks.android.battery;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;

public class DeviceIdleStartHook extends MethodHook {
    public DeviceIdleStartHook(ClassLoader classLoader) { super(classLoader); }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.DeviceIdleController", "onStart", new Object[0], new CakeHooker.Callback() {
            @Override public void call(CakeHooker.AfterHookCallback callback) {
                BatteryOptimizationService.setController(callback.getThisObject());
            }
        })
        };
    }

}
