package nep.timeline.cirno.hooks.android.notification;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.NotificationExemptionHandler;
import nep.timeline.cirno.reflect.CakeHooker;

public class registerUpdatedByAppHook extends MethodHook {
    public registerUpdatedByAppHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        String recordClass = "com.android.server.notification.NotificationRecord";
        return new HookTarget[] {
                HookTarget.method("com.android.server.notification.NotificationUsageStats", "registerUpdatedByApp",
                        new Object[] {recordClass, recordClass}, new CakeHooker.Callback() {
                            @Override
                            public void call(CakeHooker.AfterHookCallback callback) {
                                Object[] args = callback.getArgs();
                                if (args[1] != null) {
                                    NotificationExemptionHandler.onNotificationChanged(args[0]);
                                }
                            }
                        })
        };
    }
}
