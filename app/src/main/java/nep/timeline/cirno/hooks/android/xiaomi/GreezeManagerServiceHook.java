package nep.timeline.cirno.hooks.android.xiaomi;

import android.content.Context;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.InstallPolicy;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.GreezeManagerServiceWrapper;
import nep.timeline.cirno.utils.SystemChecker;

public class GreezeManagerServiceHook extends MethodHook {
    private static final String TARGET_CLASS = "com.miui.server.greeze.GreezeManagerService";

    public GreezeManagerServiceHook(ClassLoader classLoader) {
        super(classLoader);
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                GreezeManagerServiceWrapper.setInstance(callback.getThisObject());
                Log.i("GreezeManagerService 实例已捕获");

                /*try {
                    Class<?> debugConfig = CakeReflection.findClassIfExists(
                            "com.miui.server.greeze.GreezeManagerDebugConfig", classLoader);
                    if (debugConfig != null) {
                        CakeReflection.setStaticBooleanField(debugConfig, "milletEnable", true);
                        Log.i("milletEnable 已强制启用");
                    }
                } catch (Throwable t) {
                    Log.e("设置 milletEnable 失败", t);
                }*/
            }
        };
    }

    @Override
    protected HookTarget[] getTargets() {
        CakeHooker.Callback callback = targetCallback();
        return new HookTarget[] {
                HookTarget.method(TARGET_CLASS, "init", new Object[]{Context.class}, callback),
                HookTarget.constructor(TARGET_CLASS, new Object[]{Context.class}, callback)
        };
    }

    @Override
    protected InstallPolicy getInstallPolicy() {
        return InstallPolicy.FIRST_SUCCESS;
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
