package nep.timeline.cirno.hooks.android.battery;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;

public class DeviceIdleBootPhaseHook extends MethodHook {
    public DeviceIdleBootPhaseHook(ClassLoader classLoader) { super(classLoader); }

    @Override public String getTargetClass() { return "com.android.server.DeviceIdleController"; }
    @Override public String getTargetMethod() { return "onBootPhase"; }
    @Override public Object[] getTargetParam() { return new Object[]{int.class}; }
    @Override public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override public void call(CakeHooker.AfterHookCallback callback) {
                BatteryOptimizationService.setController(callback.getThisObject());
                Object[] args = callback.getArgs();
                if (args.length > 0 && args[0] instanceof Integer
                        && (Integer) args[0] >= 500) {
                    BatteryOptimizationService.sync();
                }
            }
        };
    }
}
