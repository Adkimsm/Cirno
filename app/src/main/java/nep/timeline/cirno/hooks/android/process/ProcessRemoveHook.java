package nep.timeline.cirno.hooks.android.process;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.ProcessService;

public class ProcessRemoveHook extends MethodHook {
    public ProcessRemoveHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.am.ProcessList", "removeProcessNameLocked", new Object[]{String.class, int.class, "com.android.server.am.ProcessRecord"}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                String name = (String) callback.getArgs()[0];
                int uid = (int) callback.getArgs()[1];
                Object systemProcessRecord = callback.getArgs()[2];
                
                AppRecord appRecord = ProcessService.removeProcessRecord(name, uid);
                
                if (systemProcessRecord != null) {
                    try {
                        int pid = nep.timeline.cirno.reflect.CakeReflection.getIntField(systemProcessRecord, "mPid");
                        MonitorBinderHub.onProcessRemoved(pid);
                    } catch (Throwable ignored) {
                    }
                }
            }
        })
        };
    }

}
