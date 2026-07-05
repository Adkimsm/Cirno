package nep.timeline.cirno.hooks.android.input;

import android.os.Build;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.utils.InputMethodData;
import nep.timeline.cirno.utils.ReflectUtils;

public class InputMethodManagerService extends MethodHook {
    public InputMethodManagerService(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.inputmethod.InputMethodManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "setInputMethodLocked";
    }

    @Override
    public Object[] getTargetParam() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM)
            return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(), String.class, int.class, int.class, int.class);
        return ReflectUtils.findParameterTypesOrDefault(
            CakeReflection.findClassIfExists(getTargetClass(), classLoader),
            getTargetMethod(), String.class, int.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                try {
                    if (callback.getArgs().length < 1) {
                        return;
                    }

                    Object arg0 = callback.getArgs()[0];
                    if (!(arg0 instanceof String id)) {
                        return;
                    }

                    if (id == null || id.isEmpty()) {
                        return;
                    }

                    int userId = (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM && callback.getArgs().length > 3)
                            ? (int) callback.getArgs()[3]
                            : ActivityManagerService.getCurrentOrTargetUserId();

                    synchronized (InputMethodData.class) {
                        AppRecord oldApp = InputMethodData.getCurrentInputMethodApp();
                        AppRecord appRecord = InputMethodData.setCurrentInputMethod(id, userId);
                        if (appRecord != null) {
                            FreezerService.thaw(appRecord);
                        }
                        if (oldApp != null && !InputMethodData.isCurrentInputMethod(oldApp)) {
                            FreezerHandler.sendFreezeMessage(oldApp);
                        }
                    }
                } catch (Exception e) {
                    Log.e("InputMethodManagerService 处理失败", e);
                }
            }
        };
    }
}
