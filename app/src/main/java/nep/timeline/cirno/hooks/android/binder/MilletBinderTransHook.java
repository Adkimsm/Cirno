package nep.timeline.cirno.hooks.android.binder;

import android.os.Build;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class MilletBinderTransHook extends MethodHook {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public MilletBinderTransHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        Object[] params = targetParams();
        if (params == null)
            return new HookTarget[0];
        return new HookTarget[] {
                HookTarget.method(targetClass(), targetMethod(), params, targetCallback())
        };
    }

    private String targetClass() {
        return "com.miui.server.greeze.GreezeManagerService";
    }

    private String targetMethod() {
        return "reportBinderTrans";
    }

    private Object[] targetParams() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return new Object[]{int.class, int.class, int.class, int.class, int.class, boolean.class, long.class, int.class, int.class};
        return new Object[]{int.class, int.class, int.class, int.class, int.class, boolean.class, long.class, int.class};
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleMilletEvents())
                    return;

                boolean isOneway = (boolean) callback.getArgs()[5];
                if (isOneway)
                    return;

                int dstUid = (int) callback.getArgs()[0];

                FreezerService.temporaryUnfreezeIfNeed(dstUid, "Binder", TEMP_UNFREEZE_INTERVAL_MS);
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
