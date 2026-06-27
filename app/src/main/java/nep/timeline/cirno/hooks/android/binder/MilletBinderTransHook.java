package nep.timeline.cirno.hooks.android.binder;

import android.os.Build;

import java.util.Arrays;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class MilletBinderTransHook extends MethodHook {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public MilletBinderTransHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.miui.server.greeze.GreezeManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "reportBinderTrans";
    }

    @Override
    public Object[] getTargetParam() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return new Object[]{int.class, int.class, int.class, int.class, int.class, boolean.class, long.class, int.class, int.class};
        return new Object[]{int.class, int.class, int.class, int.class, int.class, boolean.class, long.class, int.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Log.d("reportBinderTrans params: " + Arrays.toString(callback.getArgs()));

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
