package nep.timeline.cirno.hooks.android.xiaomi;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.utils.ReflectUtils;
import nep.timeline.cirno.utils.SystemChecker;

public class MilletMonitorHook extends MethodHook {
    private static final int MILLET_MONITOR_ALL = 0x7;

    public MilletMonitorHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.miui.server.greeze.GreezeManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "registerMonitor";
    }

    @Override
    public Object[] getTargetParam() {
        try {
            Class<?> tokenClass = Class.forName("miui.greeze.IMonitorToken", false, classLoader);
            return new Object[]{tokenClass, int.class};
        } catch (Throwable t) {
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(),
                    Object.class,
                    int.class);
        }
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    CakeReflection.setIntField(callback.getThisObject(), "mRegisteredMonitor", MILLET_MONITOR_ALL);
                    Log.i("mRegisteredMonitor 已强制设为 ALL(7)");
                } catch (Throwable t) {
                    Log.e("设置 mRegisteredMonitor 失败", t);
                }
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
