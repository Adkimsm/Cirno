package nep.timeline.cirno.hooks.android.wakelock;

import android.os.Build;
import android.os.IBinder;
import android.os.WorkSource;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.utils.PKGUtils;
import nep.timeline.cirno.utils.SystemChecker;

public class WakeLockHook extends MethodHook {
    public WakeLockHook(ClassLoader classLoader) {
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
        return "com.android.server.power.PowerManagerService";
    }

    private String targetMethod() {
        return "acquireWakeLockInternal";
    }

    private Object[] targetParams() {
        if (SystemChecker.isSamsung(classLoader) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return new Object[] { IBinder.class, int.class, int.class, String.class, String.class, WorkSource.class,
                    String.class, int.class, int.class, "android.os.IWakeLockCallback", boolean.class };
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
            return new Object[] { IBinder.class, int.class, int.class, String.class, String.class, WorkSource.class,
                    String.class, int.class, int.class, "android.os.IWakeLockCallback" };
        return new Object[] { IBinder.class, int.class, int.class, String.class, String.class, WorkSource.class,
                String.class, int.class, int.class };
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                String packageName = (String) callback.getArgs()[4];
                int uid = (int) callback.getArgs()[7];

                AppRecord appRecord = AppService.get(packageName, PKGUtils.getUserId(uid));
                if (appRecord == null)
                    return;

                if (appRecord.isFrozen()
                        && !AppConfigs.isNetworkMessageAllowed(packageName, PKGUtils.getUserId(uid)))
                    callback.returnAndSkip(null);
            }
        };
    }
}
