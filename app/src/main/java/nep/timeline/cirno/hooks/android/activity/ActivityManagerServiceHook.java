package nep.timeline.cirno.hooks.android.activity;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.MonitorBinderHub;

public class ActivityManagerServiceHook extends MethodHook {
    public ActivityManagerServiceHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.ActivityManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "setSystemProcess";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[0];
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                ActivityManagerService.setInstance(callback.getThisObject());
            }

            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                MonitorBinderHub.ensureBinderRegistered("ActivityManagerService.setSystemProcess");
            }
        };
    }
}
