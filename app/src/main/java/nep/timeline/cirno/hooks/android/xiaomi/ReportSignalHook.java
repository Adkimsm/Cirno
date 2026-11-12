package nep.timeline.cirno.hooks.android.xiaomi;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.utils.ReflectUtils;
import nep.timeline.cirno.utils.SystemChecker;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class ReportSignalHook extends MethodHook {
    public ReportSignalHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.miui.server.greeze.GreezeManagerService", "reportSignal", ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists("com.miui.server.greeze.GreezeManagerService", classLoader),
                "reportSignal",
                int.class,
                int.class,
                long.class), new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleMilletEvents())
                    return;

                int pid = (int) callback.getArgs()[1];
                if (pid <= 0)
                    return;

                ProcessRecord processRecord = ProcessService.getProcessRecordByPid(pid);
                if (processRecord == null)
                    return;

                if (ProcessService.removeProcessRecordWithoutThaw(processRecord, "MIUI Greeze reportSignal") != null)
                    MonitorBinderHub.onProcessRemoved(pid);
            }
        })
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
