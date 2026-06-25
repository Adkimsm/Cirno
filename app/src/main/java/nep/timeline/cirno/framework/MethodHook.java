package nep.timeline.cirno.framework;

import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;

public abstract class MethodHook {
    public final int ANY_VERSION = -1;
    public final ClassLoader classLoader;
    public io.github.libxposed.api.XposedInterface.HookHandle unhooker;
    protected boolean hooked = false;

    public MethodHook(ClassLoader classLoader) {
        this.classLoader = classLoader;
        try {
            startHook();
        } catch (Throwable throwable) {
            if (!isIgnoreError())
                Log.e(getTargetMethod(), throwable);
        }
    }

    public abstract String getTargetClass();

    public abstract String getTargetMethod();

    public abstract Object[] getTargetParam();

    public abstract CakeHooker.Callback getTargetHook();

    public int getMinVersion() {
        return ANY_VERSION;
    }

    public boolean isIgnoreError() {
        return false;
    }

    public void startHook() {
        int minVersion = getMinVersion();
        if (minVersion == ANY_VERSION || Build.VERSION.SDK_INT >= minVersion) {
            Object[] targetParam = getTargetParam();
            CakeHooker.Callback targetHook = getTargetHook();

            if (targetHook == null || targetParam == null)
                return;

            String targetMethod = getTargetMethod();
            String targetClass = getTargetClass();

            ArrayList<Object> param = new ArrayList<>(Arrays.asList(targetParam));
            param.add(targetHook);
            try {
                if (targetMethod == null)
                    unhooker = CakeReflection.findAndHookConstructor(targetClass, classLoader, param.toArray());
                else
                    unhooker = CakeReflection.findAndHookMethod(targetClass, classLoader, targetMethod, param.toArray());
                hooked = true;
                Log.i(getTargetMethod() + " -> 成功Hook完毕!");
            } catch (Throwable t) {
                logAvailableSignatures(targetClass, targetMethod, targetMethod == null);
                throw t;
            }
        }
    }

    public boolean isHooked() {
        return hooked;
    }

    private void logAvailableSignatures(String className, String methodName, boolean isConstructor) {
        try {
            Class<?> clazz = CakeReflection.findClassIfExists(className, classLoader);
            if (clazz == null) {
                Log.d("[MethodHook-DEBUG] " + className + " 类未找到");
                return;
            }

            StringBuilder sb = new StringBuilder();
            if (isConstructor) {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    sb.append("  ").append(Arrays.toString(ctor.getParameterTypes())).append("\n");
                }
            } else {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals(methodName)) {
                        sb.append("  ").append(Arrays.toString(method.getParameterTypes())).append("\n");
                    }
                }
            }

            String label = isConstructor ? "构造函数" : methodName;
            if (sb.length() > 0) {
                Log.d("[MethodHook-DEBUG] " + label + " 可用签名:\n" + sb);
            } else {
                Log.d("[MethodHook-DEBUG] " + label + " 未找到任何同名签名");
            }
        } catch (Throwable ignored) {
        }
    }

    public void unhook() {
        if (unhooker == null)
            return;

        unhooker.unhook();
        unhooker = null;
    }
}
