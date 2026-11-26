package nep.timeline.cirno.hooks.android.notification;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.InstallPolicy;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.NotificationExemptionHandler;
import nep.timeline.cirno.reflect.CakeHooker;

public class NotificationSystemUpdateHook extends MethodHook {
    public NotificationSystemUpdateHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        String listenersClass = "com.android.server.notification.NotificationManagerService$NotificationListeners";
        String recordClass = "com.android.server.notification.NotificationRecord";
        CakeHooker.Callback callback = new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                // E.g. the system strips a foreground-service flag and updates the same record.
                NotificationExemptionHandler.onNotificationChanged(callback.getArgs()[0]);
            }
        };
        return new HookTarget[] {
                HookTarget.method(listenersClass, "notifyPostedLocked",
                        new Object[] {recordClass, recordClass, boolean.class}, callback),
                HookTarget.method(listenersClass, "notifyPostedLocked",
                        new Object[] {recordClass, recordClass}, callback)
        };
    }

    @Override
    protected InstallPolicy getInstallPolicy() {
        return InstallPolicy.FIRST_SUCCESS;
    }
}
