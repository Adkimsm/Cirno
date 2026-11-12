package nep.timeline.cirno.hooks.android.activity;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.MonitorBinderHub;

public class ActivityManagerServiceHook extends MethodHook {
    public ActivityManagerServiceHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.am.ActivityManagerService", "setSystemProcess", new Object[0], new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                ActivityManagerService.setInstance(callback.getThisObject());
            }

            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                MonitorBinderHub.ensureBinderRegistered("ActivityManagerService.setSystemProcess");
            }
        })
        };
    }

}
