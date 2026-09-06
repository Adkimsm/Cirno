package nep.timeline.cirno.hooks.android.vivo;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.VivoFreezeNetCtrlWrapper;
import nep.timeline.cirno.utils.SystemChecker;

public class FreezeNetCtrlHook extends MethodHook {
    public FreezeNetCtrlHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.frozen.policy.FreezeNetCtrl2";
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                VivoFreezeNetCtrlWrapper.setInstance(callback.getThisObject());
                Log.i("FreezeNetCtrl2 实例已捕获");
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
            unhooker = CakeReflection.findAndHookConstructor(targetClass, classLoader,
                    "com.android.server.am.frozen.policy.FrozenImApp",
                    "com.vivo.vpsnh.IVpsnhService",
                    callback);
            hooked = true;
            Log.i("FreezeNetCtrl2 <init> -> 成功Hook完毕!");
        } catch (Throwable t) {
            if (!isIgnoreError())
                Log.e("FreezeNetCtrl2 hook 失败", t);
        }
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
