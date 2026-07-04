package nep.timeline.cirno.hooks.android.optimizer;

import android.os.Build;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;

public class CacheMemCompactionHandlerHook extends MethodHook {
    public CacheMemCompactionHandlerHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.CachedAppOptimizer$MemCompactionHandler";
    }

    @Override
    public String getTargetMethod() {
        return "handleMessage";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{"android.os.Message"};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                if (callback.throwable == null) {
                    return;
                }
                Log.w("CachedAppOptimizer.MemCompactionHandler.handleMessage suppressed crash", callback.throwable);
                callback.throwable = null;
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
