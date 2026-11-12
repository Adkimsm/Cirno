package nep.timeline.cirno.hooks.android.signal;

import android.os.Process;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class SendSignalQuietHook extends MethodHook {
    public SendSignalQuietHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method(Process.class.getTypeName(), "sendSignalQuiet", new Object[]{int.class, int.class}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                int pid = (int) callback.getArgs()[0];
                int signal = (int) callback.getArgs()[1];
                if (signal != Process.SIGNAL_KILL)
                    return;

                ProcessRecord processRecord = ProcessService.getProcessRecordByPid(pid);
                if (processRecord == null || processRecord.isDeathProcess())
                    return;

                ProcessService.removeProcessRecordWithoutThaw(processRecord, "Process.sendSignalQuiet(SIGKILL)");
            }
        })
        };
    }

}
