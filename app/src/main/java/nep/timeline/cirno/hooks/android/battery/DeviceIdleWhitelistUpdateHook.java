package nep.timeline.cirno.hooks.android.battery;

import android.os.Build;

import java.util.List;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.BatteryOptimizationService;
import nep.timeline.cirno.utils.ReflectUtils;
import nep.timeline.cirno.utils.SystemChecker;

/** Re-applies the configured policy after framework-side allowlist changes. */
public class DeviceIdleWhitelistUpdateHook extends MethodHook {
    public DeviceIdleWhitelistUpdateHook(ClassLoader classLoader) { super(classLoader); }

    @Override public String getTargetClass() { return "com.android.server.DeviceIdleController"; }
    @Override public String getTargetMethod() { return "updateWhitelistAppIdsLocked"; }
    @Override public Object[] getTargetParam() {
        // vivo Android 16+: 4 参数版本 (String, int, String, List)
        if (SystemChecker.isVivo(classLoader) && Build.VERSION.SDK_INT >= 36) {
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(),
                    String.class, int.class, String.class, List.class
            );
        }

        // 默认: 无参数版本 (适用于所有其他设备和旧版本)
        return new Object[0];
    }
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
