package nep.timeline.cirno.hooks.android.optimizer;

import android.os.Build;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;

public class CacheUseCompactionHook extends MethodHook {
    public CacheUseCompactionHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.CachedAppOptimizer";
    }

    @Override
    public String getTargetMethod() {
        return "useCompaction";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[0];
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return (CakeHooker.ReplacementCallback) chain -> {
            Object instance = chain.getThisObject();
            try {
                CakeReflection.setBooleanField(instance, "mUseCompaction", false);
            } catch (Throwable ignored) {
            }
            return false;
        };
    }

    @Override
    public int getMinVersion() {
        return Build.VERSION_CODES.S;
    }

    @Override
    public boolean isIgnoreError() {
        return true;
    }
}
