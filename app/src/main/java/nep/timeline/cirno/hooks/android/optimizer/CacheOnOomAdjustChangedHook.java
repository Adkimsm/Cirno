package nep.timeline.cirno.hooks.android.optimizer;

import android.os.Build;

import nep.timeline.cirno.framework.OverloadMethodHook;
import nep.timeline.cirno.reflect.CakeHooker;

public class CacheOnOomAdjustChangedHook extends OverloadMethodHook {
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
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                callback.returnAndSkip(null);
            }
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
