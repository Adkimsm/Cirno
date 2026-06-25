package nep.timeline.cirno.hooks.android.xiaomi;

import android.content.Context;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.GreezeManagerServiceWrapper;
import nep.timeline.cirno.utils.SystemChecker;

public class GreezeManagerServiceHook extends MethodHook {
    public GreezeManagerServiceHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.miui.server.greeze.GreezeManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "init";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{Context.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                GreezeManagerServiceWrapper.setInstance(callback.getThisObject());
                Log.i("GreezeManagerService 实例已捕获");

                try {
                    Class<?> debugConfig = CakeReflection.findClassIfExists(
                            "com.miui.server.greeze.GreezeManagerDebugConfig", classLoader);
                    if (debugConfig != null) {
                        CakeReflection.setStaticBooleanField(debugConfig, "milletEnable", true);
                        Log.i("milletEnable 已强制启用");
                    }
                } catch (Throwable t) {
                    Log.e("设置 milletEnable 失败", t);
                }
            }
        };
    }

    @Override
    public void startHook() {
        String targetClass = getTargetClass();
        CakeHooker.Callback callback = getTargetHook();
        if (callback == null)
            return;

        try {
            unhooker = CakeReflection.findAndHookMethod(targetClass, classLoader, "init", Context.class, callback);
            hooked = true;
            Log.i("init(Context) -> 成功Hook完毕!");
            return;
        } catch (Throwable ignored) {
            Log.d("init(Context) 未找到，尝试 hook 构造函数");
        }

        try {
            unhooker = CakeReflection.findAndHookConstructor(targetClass, classLoader, Context.class, callback);
            hooked = true;
            Log.i("<init>(Context) -> 成功Hook完毕!");
        } catch (Throwable t) {
            Log.e("GreezeManagerService hook 失败", t);
            throw new RuntimeException(t);
        }
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
