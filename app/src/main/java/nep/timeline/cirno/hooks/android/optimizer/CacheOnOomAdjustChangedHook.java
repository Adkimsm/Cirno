package nep.timeline.cirno.hooks.android.optimizer;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;

public class CacheOnOomAdjustChangedHook extends MethodHook {
    private List<XposedInterface.HookHandle> hookHandles;

    public CacheOnOomAdjustChangedHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.CachedAppOptimizer";
    }

    @Override
    public String getTargetMethod() {
        return "onOomAdjustChanged";
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
        if (hookHandles == null)
            hookHandles = new ArrayList<>();

        int minVersion = getMinVersion();
        if (minVersion != ANY_VERSION && Build.VERSION.SDK_INT < minVersion)
            return;

        Class<?> clazz = CakeReflection.findClassIfExists(getTargetClass(), classLoader);
        if (clazz == null)
            return;

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("onOomAdjustChanged")) {
                try {
                    method.setAccessible(true);
                    XposedInterface.HookHandle handle = CakeHooker.hookBefore(method,
                            callback -> callback.returnAndSkip(null));
                    hookHandles.add(handle);
                    hooked = true;
                } catch (Throwable t) {
                    Log.e("onOomAdjustChanged hook failed for " + method, t);
                }
            }
        }
        if (hooked) {
            Log.i("onOomAdjustChanged -> 成功Hook完毕!");
        }
    }

    @Override
    public int getMinVersion() {
        return Build.VERSION_CODES.S;
    }

    @Override
    public boolean isIgnoreError() {
        return true;
    }

    @Override
    public void unhook() {
        if (hookHandles != null) {
            for (XposedInterface.HookHandle handle : hookHandles) {
                if (handle != null) {
                    handle.unhook();
                }
            }
            hookHandles.clear();
        }
        hooked = false;
    }
}
