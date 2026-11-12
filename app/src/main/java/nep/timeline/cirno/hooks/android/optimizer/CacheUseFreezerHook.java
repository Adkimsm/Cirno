package nep.timeline.cirno.hooks.android.optimizer;

import android.os.Build;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.CachedAppOptimizer;

public class CacheUseFreezerHook extends MethodHook {
    public CacheUseFreezerHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.am.CachedAppOptimizer", "useFreezer", new Object[0], (CakeHooker.ReplacementCallback) chain -> {
            if (CachedAppOptimizer.getInstance() == null)
                synchronized (CachedAppOptimizer.class) {
                    CachedAppOptimizer.setInstance(chain.getThisObject());
                }

            return false;
        })
        };
    }

    @Override
    public int getMinVersion() {
        return Build.VERSION_CODES.R;
    }
}
