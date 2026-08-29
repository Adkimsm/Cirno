package nep.timeline.cirno.provide;

import nep.timeline.cirno.binder.BinderService;

public final class BatteryOptimizationBinder {
    private BatteryOptimizationBinder() {
    }

    public static BatteryOptimizationBinderFacade getInstance() {
        return BinderService.getBatteryOptimizationBinder();
    }
}
