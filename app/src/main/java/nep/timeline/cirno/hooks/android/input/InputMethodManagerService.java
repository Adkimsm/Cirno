package nep.timeline.cirno.hooks.android.input;

import android.os.Build;
import android.view.inputmethod.InputMethodInfo;

import java.util.Map;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.utils.InputMethodData;
import nep.timeline.cirno.utils.ReflectUtils;

public class InputMethodManagerService extends MethodHook {
    private static final long OLD_IME_FREEZE_DELAY_MS = 3000L;

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

                    if (id.isEmpty()) {
                        return;
                    }

                    int userId = (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM
                            && callback.getArgs().length > 3)
                            ? (int) callback.getArgs()[3]
                            : ActivityManagerService.getCurrentOrTargetUserId();

                    Object settings = resolveSettings(callback.getThisObject(), userId);

                    // 注意：thaw/sendFreezeMessage 必须在 InputMethodData 锁之外调用，
                    // 否则与 Freezer 线程（FreezerService 锁 -> InputMethodData 锁）构成 ABBA 死锁
                    AppRecord oldApp = null;
                    AppRecord appRecord = null;
                    boolean switched = false;

                    synchronized (InputMethodData.class) {
                        ensureMethodMap(callback.getThisObject(), settings);

                        Map<String, InputMethodInfo> inputMethodMap = InputMethodData.inputMethods;
                        if (inputMethodMap == null || inputMethodMap.isEmpty()) {
                            return;
                        }

                        InputMethodInfo inputMethodInfo = inputMethodMap.get(id);
                        if (inputMethodInfo == null) {
                            return;
                        }

                        if (inputMethodInfo.equals(InputMethodData.currentInputMethodInfo)) {
                            return;
                        }

                        oldApp = InputMethodData.currentInputMethodApp;
                        appRecord = AppService.get(inputMethodInfo.getPackageName(), userId);
                        InputMethodData.setCurrentInputMethodApp(appRecord, inputMethodInfo, userId);
                        switched = appRecord != oldApp;
                    }

                    if (!switched) {
                        return;
                    }

                    if (appRecord != null) {
                        FreezerService.thaw(appRecord);
                    }
                    if (oldApp != null && !InputMethodData.isCurrentInputMethod(oldApp)) {
                        FreezerHandler.sendTemporaryFreezeMessage(oldApp, OLD_IME_FREEZE_DELAY_MS);
                    }
                } catch (Throwable e) {
                    Log.e("InputMethodManagerService 处理失败", e);
                }
            }
        };
    }

    private Object resolveSettings(Object imms, int userId) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Class<?> repository = CakeReflection.findClassIfExists(
                    "com.android.server.inputmethod.InputMethodSettingsRepository", classLoader);
            if (repository != null) {
                try {
                    return CakeReflection.callStaticMethod(repository, "get", userId);
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            return CakeReflection.getObjectField(imms, "mSettings");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 从 mSettings / InputMethodSettingsRepository 解析 mMethodMap（及 InputMethodMap.mMap）。
     * map 为空时每次重试，避免首次 settings 未就绪后永久失败。
     */
    @SuppressWarnings("unchecked")
    private void ensureMethodMap(Object imms, Object settings) {
        if (InputMethodData.instance == null) {
            InputMethodData.instance = imms;
        }

        if (InputMethodData.inputMethods != null && !InputMethodData.inputMethods.isEmpty()) {
            return;
        }

        if (settings == null) {
            InputMethodData.inputMethods = null;
            return;
        }

        try {
            Object map = CakeReflection.getObjectField(settings, "mMethodMap");
            if (map == null) {
                InputMethodData.inputMethods = null;
                return;
            }

            if ("com.android.server.inputmethod.InputMethodMap".equals(map.getClass().getTypeName())) {
                Object inner = CakeReflection.getObjectField(map, "mMap");
                InputMethodData.inputMethods = inner instanceof Map
                        ? (Map<String, InputMethodInfo>) inner
                        : null;
            } else if (map instanceof Map) {
                InputMethodData.inputMethods = (Map<String, InputMethodInfo>) map;
            } else {
                InputMethodData.inputMethods = null;
            }
        } catch (Throwable throwable) {
            Log.w("解析 InputMethod method map 失败", throwable);
            InputMethodData.inputMethods = null;
        }
    }
}
