package nep.timeline.cirno.hooks.android.xiaomi;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.rekernel.ReKernel;
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
    public String getTargetClass() {
        return "com.miui.server.greeze.GreezeManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "reportSignal";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(),
                int.class,
                int.class,
                long.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (ReKernel.received) {
                    unhook();
                    return;
                }

                int pid = (int) callback.getArgs()[1];
                if (pid <= 0)
                    return;

                ProcessRecord processRecord = ProcessService.getProcessRecordByPid(pid);
                if (processRecord == null)
                    return;

                if (ProcessService.removeProcessRecordWithoutThaw(processRecord, "MIUI Greeze reportSignal") != null)
                    MonitorBinderHub.onProcessRemoved(pid);
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
