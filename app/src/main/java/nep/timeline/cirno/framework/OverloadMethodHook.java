package nep.timeline.cirno.framework;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;

/** MethodHook variant for hooks that may target multiple overloads. */
public abstract class OverloadMethodHook extends MethodHook {
    private List<XposedInterface.HookHandle> hookHandles;

    protected enum HookMode {
        BEFORE,
        AFTER,
        BOTH
    }

    public OverloadMethodHook(ClassLoader classLoader) {
        super(classLoader);
    }

    /** Known signatures to prefer before falling back to all overloads. */
    protected Object[][] getPreferredParamTypes() {
        return new Object[0][];
    }

    /** Additional filter for the fallback overload scan. */
    protected boolean shouldHookFallback(Method method) {
        return getTargetMethod().equals(method.getName());
    }

    protected HookMode getHookMode() {
        return HookMode.BOTH;
    }

    @Override
    public void startHook() {
        if (hookHandles == null) {
            hookHandles = new ArrayList<>();
        }

        int minVersion = getMinVersion();
        if (minVersion != ANY_VERSION && Build.VERSION.SDK_INT < minVersion) {
            return;
        }

        Class<?> targetClass = CakeReflection.findClassIfExists(getTargetClass(), classLoader);
        CakeHooker.Callback targetHook = getTargetHook();
        if (targetClass == null || targetHook == null || getTargetMethod() == null) {
            return;
        }

        Set<Method> targetMethods = new LinkedHashSet<>();
        Object[][] preferredParamTypes = getPreferredParamTypes();
        if (preferredParamTypes != null) {
            for (Object[] parameterTypes : preferredParamTypes) {
                Method method = CakeReflection.findMethodExactIfExists(
                        targetClass, getTargetMethod(), parameterTypes);
                if (method != null) {
                    targetMethods.add(method);
                }
            }
        }

        if (targetMethods.isEmpty()) {
            for (Method method : targetClass.getDeclaredMethods()) {
                if (shouldHookFallback(method)) {
                    targetMethods.add(method);
                }
            }
        }

        for (Method method : targetMethods) {
            try {
                method.setAccessible(true);
                XposedInterface.HookHandle handle;
                switch (getHookMode()) {
                    case BEFORE:
                        handle = CakeHooker.hookBefore(method, callback ->
                                targetHook.call(callback));
                        break;
                    case AFTER:
                        handle = CakeHooker.hookAfter(method, callback ->
                                targetHook.call(callback));
                        break;
                    default:
                        handle = CakeHooker.hook(method, targetHook);
                        break;
                }
                hookHandles.add(handle);
                hooked = true;
                Log.i(getTargetMethod() + " "
                        + Arrays.toString(method.getParameterTypes())
                        + " -> 成功Hook完毕!");
            } catch (Throwable throwable) {
                Log.e(getTargetMethod() + " hook 失败: " + method, throwable);
            }
        }
    }

    @Override
    public void unhook() {
        if (hookHandles == null) {
            return;
        }

        for (XposedInterface.HookHandle handle : hookHandles) {
            if (handle != null) {
                handle.unhook();
            }
        }
        hookHandles.clear();
        hooked = false;
    }
}
