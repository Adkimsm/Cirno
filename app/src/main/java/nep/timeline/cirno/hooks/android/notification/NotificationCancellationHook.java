package nep.timeline.cirno.hooks.android.notification;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.InstallPolicy;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.NotificationExemptionHandler;
import nep.timeline.cirno.reflect.CakeHooker;

public class NotificationCancellationHook extends MethodHook {
    public NotificationCancellationHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        String serviceClass = "com.android.server.notification.NotificationManagerService";
        String recordClass = "com.android.server.notification.NotificationRecord";
        CakeHooker.Callback callback = new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                Object[] args = callback.getArgs();
                if (Boolean.TRUE.equals(args[5])) { // wasPosted
                    NotificationExemptionHandler.onNotificationChanged(args[0]);
                }
            }
        };
        return new HookTarget[] {
                HookTarget.method(serviceClass, "cancelNotificationLocked",
                        new Object[] {recordClass, boolean.class, int.class, int.class, int.class,
                                boolean.class, String.class, long.class}, callback),
                HookTarget.method(serviceClass, "cancelNotificationLocked",
                        new Object[] {recordClass, boolean.class, int.class, int.class, int.class,
                                boolean.class, String.class}, callback)
        };
    }

    @Override
    protected InstallPolicy getInstallPolicy() {
        return InstallPolicy.FIRST_SUCCESS;
    }
}
