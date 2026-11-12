package nep.timeline.cirno.hooks.android.anr;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.utils.AnrHelper;

public class ANRErrorStateHook extends MethodHook {
    public ANRErrorStateHook(ClassLoader classLoader) {
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
        return "com.android.server.am.ProcessErrorStateRecord";
    }

    private String targetMethod() {
        return "appNotResponding";
    }

    private Object[] targetParams() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU)
            return new Object[]{String.class, ApplicationInfo.class, String.class, "com.android.server.wm.WindowProcessController", boolean.class, "com.android.internal.os.TimeoutRecord", ExecutorService.class, boolean.class, boolean.class, Future.class};
        return new Object[]{String.class, ApplicationInfo.class, String.class, "com.android.server.wm.WindowProcessController", boolean.class, String.class, boolean.class};
    }

    @Override
    public boolean isIgnoreError() {
        return true;
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Object app = CakeReflection.getObjectField(callback.getThisObject(), "mApp");
                if (app == null)
                    return;
                AnrHelper.processingAnr(callback, app);
            }
        };
    }

    @Override
    public int getMinVersion() {
        return Build.VERSION_CODES.S;
    }
}
