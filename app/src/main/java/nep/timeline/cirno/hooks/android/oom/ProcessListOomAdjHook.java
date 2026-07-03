package nep.timeline.cirno.hooks.android.oom;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.OomAdjService;

public class ProcessListOomAdjHook extends MethodHook {
    private List<XposedInterface.HookHandle> hookHandles;

    public ProcessListOomAdjHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.ProcessList";
    }

    @Override
    public String getTargetMethod() {
        return "setOomAdj";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[0];
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
        };
    }

    @Override
    public void startHook() {
        if (hookHandles == null) {
            hookHandles = new ArrayList<>();
        }

        Class<?> clazz = CakeReflection.findClassIfExists(getTargetClass(), classLoader);
        if (clazz == null) {
            return;
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (!getTargetMethod().equals(method.getName()) || method.getParameterTypes().length < 3) {
                continue;
            }
            try {
                method.setAccessible(true);
                XposedInterface.HookHandle handle = CakeHooker.hookAfter(method, callback -> {
                    Object[] args = callback.getArgs();
                    if (args.length < 3 || !(args[0] instanceof Integer)) {
                        return;
                    }
                    OomAdjService.applyForPidAsync((Integer) args[0]);
                });
                hookHandles.add(handle);
                hooked = true;
            } catch (Throwable t) {
                Log.e("setOomAdj hook failed for " + method, t);
            }
        }
        if (hooked) {
            Log.i("setOomAdj -> 成功Hook完毕!");
        }
    }

    @Override
    public boolean isIgnoreError() {
        return true;
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
