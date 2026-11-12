package nep.timeline.cirno.hooks.android.anr;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.utils.AnrHelper;

public class ANRHelperHooks extends MethodHook {
    private static final String TARGET_CLASS = "com.android.server.am.AnrHelper";
    private static final String PROCESS_RECORD = "com.android.server.am.ProcessRecord";
    private static final String ANR_RECORD = "com.android.server.am.AnrHelper$AnrRecord";

    public ANRHelperHooks(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.resolve(TARGET_CLASS + "#appNotResponding",
                        classLoader -> {
                            Class<?> targetClass = CakeReflection.findClassIfExists(TARGET_CLASS, classLoader);
                            if (targetClass == null)
                                return List.of();

                            List<Method> methods = new ArrayList<>();
                            for (Method method : targetClass.getDeclaredMethods()) {
                                if ((method.getName().equals("appNotResponding")
                                        || method.getName().equals("deferAppNotResponding"))
                                        && method.getReturnType().equals(void.class)) {
                                    methods.add(method);
                                }
                            }
                            return methods;
                        },
                        executable -> createCallback((Method) executable))
        };
    }

    private static CakeHooker.Callback createCallback(Method method) {
        Integer index = findIndex(method.getParameterTypes(), PROCESS_RECORD);
        if (index == null) {
            Integer miuiRecordIndex = findIndex(method.getParameterTypes(), ANR_RECORD);
            if (miuiRecordIndex == null)
                return null;

            return new CakeHooker.Callback() {
                @Override
                public void call(CakeHooker.BeforeHookCallback callback) {
                    Object anrRecord = callback.getArgs()[miuiRecordIndex];
                    if (anrRecord == null)
                        return;
                    Object app = CakeReflection.getObjectField(anrRecord, "mApp");
                    if (app == null)
                        return;
                    AnrHelper.processingAnr(callback, app);
                }
            };
        }

        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Object record = callback.getArgs()[index];
                if (record == null)
                    return;
                AnrHelper.processingAnr(callback, record);
            }
        };
    }

    private static Integer findIndex(Class<?>[] parameterTypes, String clazz) {
        for (int i = 0; i < parameterTypes.length; i++)
            if (clazz.equals(parameterTypes[i].getName()))
                return i;
        return null;
    }

    @Override
    public boolean isIgnoreError() {
        return true;
    }
}
