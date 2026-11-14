package nep.timeline.cirno.hooks.android.activity;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.NetworkSpeedMonitor;
import nep.timeline.cirno.services.NetworkStateMonitor;
import nep.timeline.cirno.utils.FrozenRW;
import nep.timeline.cirno.utils.InputMethodData;
import nep.timeline.cirno.utils.ReflectUtils;

public class ActivityManagerSystemReadyHook extends MethodHook {
    public ActivityManagerSystemReadyHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.am.ActivityManagerService", "systemReady", ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists("com.android.server.am.ActivityManagerService", classLoader),
                "systemReady", Runnable.class), new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                FrozenRW.ensureFrozenCgroups();
                MonitorBinderHub.setBootCompleted();
                MonitorBinderHub.ensureBinderRegistered("ActivityManagerService.systemReady");
                NetworkSpeedMonitor.init();
                NetworkStateMonitor.init();
                InputMethodData.initFromSettingsWithRetry();
            }
        })
        };
    }

}
