package nep.timeline.cirno.hooks.android.location;

import android.os.Binder;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.LocationHandler;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.ILocationListener;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class ListenerUnregisterHook extends MethodHook {
    public ListenerUnregisterHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.location.LocationManagerService", "unregisterLocationListener", new Object[]{"android.location.ILocationListener"}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                ILocationListener listener = new ILocationListener(callback.getArgs()[0]);
                int pid = Binder.getCallingPid();

                Handlers.location.post(() -> {
                    ProcessRecord processRecord = ProcessService.getProcessRecordByPid(pid);

                    if (processRecord == null)
                        return;

                    AppRecord appRecord = processRecord.getAppRecord();

                    if (appRecord == null)
                        return;

                    if (appRecord.getAppState().removeLocationListener(listener.asBinder()))
                        LocationHandler.call(appRecord);
                });
            }
        })
        };
    }

}
