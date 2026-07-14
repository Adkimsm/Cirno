package nep.timeline.cirno.hooks.android.process;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.OomAdjService;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class ProcessAddHook extends MethodHook {
    public ProcessAddHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.ProcessList";
    }

    @Override
    public String getTargetMethod() {
        return "addProcessNameLocked";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{"com.android.server.am.ProcessRecord"};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                Object record = callback.getArgs()[0];
                if (record == null)
                    return;
                ProcessRecord processRecord = ProcessService.addProcessRecord(record);
                OomAdjService.applyForProcessAsync(processRecord);
                MonitorBinderHub.onProcessAdded(record);
                MonitorBinderHub.ensureBinderRegistered("ProcessList.addProcessNameLocked");
                if ("nep.timeline.cirno".equals(processRecord.getPackageName())) {
                    Handlers.binder.postDelayed(() ->
                        MonitorBinderHub.ensureBinderRegistered("manager process started"), 300L);
                    Handlers.binder.postDelayed(() ->
                        MonitorBinderHub.ensureBinderRegistered("manager process started"), 1000L);
                    Handlers.binder.postDelayed(() ->
                        MonitorBinderHub.ensureBinderRegistered("manager process started"), 2000L);
                    Handlers.binder.postDelayed(() ->
                        MonitorBinderHub.ensureBinderRegistered("manager process started"), 3000L);
                }
            }
        };
    }
}
