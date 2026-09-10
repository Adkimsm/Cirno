package nep.timeline.cirno.hooks.android.battery;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.BatteryOptimizationService;
import nep.timeline.cirno.threads.Handlers;

public class DeviceIdleBootPhaseHook extends MethodHook {
    private static final Runnable SYNC_WHITELIST = BatteryOptimizationService::sync;

    public DeviceIdleBootPhaseHook(ClassLoader classLoader) { super(classLoader); }

    @Override public String getTargetClass() { return "com.android.server.DeviceIdleController"; }
    @Override public String getTargetMethod() { return "onBootPhase"; }
    @Override public Object[] getTargetParam() { return new Object[]{int.class}; }
    @Override public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override public void call(CakeHooker.AfterHookCallback callback) {
                BatteryOptimizationService.setController(callback.getThisObject());
                Object[] args = callback.getArgs();
                if (args.length > 0 && args[0] instanceof Integer) {
                    int phase = (Integer) args[0];
                    Log.i("DeviceIdleController.onBootPhase: phase=" + phase 
                            + " controller=" + callback.getThisObject().getClass().getName());
                    
                    if (phase >= 500) {
                        // 延迟 1 秒执行，确保系统完全就绪，避免第一次重启时 controller 未完全初始化
                        Handlers.config.postDelayed(SYNC_WHITELIST, 1000);
                        Log.i("Battery optimization sync scheduled after boot phase " + phase);
                    }
                }
            }
        };
    }
}
