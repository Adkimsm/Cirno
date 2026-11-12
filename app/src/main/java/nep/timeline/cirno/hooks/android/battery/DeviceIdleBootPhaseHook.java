package nep.timeline.cirno.hooks.android.battery;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;

public class DeviceIdleBootPhaseHook extends MethodHook {
    public DeviceIdleBootPhaseHook(ClassLoader classLoader) { super(classLoader); }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.DeviceIdleController", "onBootPhase", new Object[]{int.class}, new CakeHooker.Callback() {
            @Override public void call(CakeHooker.AfterHookCallback callback) {
                BatteryOptimizationService.setController(callback.getThisObject());
                Object[] args = callback.getArgs();
                if (args.length > 0 && args[0] instanceof Integer) {
                    int phase = (Integer) args[0];
                    Log.i("DeviceIdleController.onBootPhase: phase=" + phase 
                            + " controller=" + callback.getThisObject().getClass().getName());
                    
                    if (phase >= 500) {
                        // 延迟 1 秒执行，确保系统完全就绪，避免第一次重启时 controller 未完全初始化
                        BatteryOptimizationService.requestSync(1000);
                        Log.i("Battery optimization sync scheduled after boot phase " + phase);
                    }
                }
            }
        })
        };
    }

}
