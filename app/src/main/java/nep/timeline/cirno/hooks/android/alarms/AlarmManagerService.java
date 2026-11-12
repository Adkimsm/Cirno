package nep.timeline.cirno.hooks.android.alarms;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.utils.ForceAppStandbyListener;

public class AlarmManagerService extends MethodHook {
    public AlarmManagerService(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.alarm.AlarmManagerService", "onBootPhase", new Object[]{int.class}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                ForceAppStandbyListener.setInstance(CakeReflection.getObjectField(callback.getThisObject(), "mForceAppStandbyListener"));
            }
        })
        };
    }

}
