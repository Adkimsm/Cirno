package nep.timeline.cirno.hooks.android.notification;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.NotificationExemptionHandler;
import nep.timeline.cirno.reflect.CakeHooker;

public class registerRemovedByAppHook extends MethodHook {
    public registerRemovedByAppHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.notification.NotificationUsageStats", "registerRemovedByApp",
                        new Object[] {"com.android.server.notification.NotificationRecord"}, new CakeHooker.Callback() {
                            @Override
                            public void call(CakeHooker.AfterHookCallback callback) {
                                NotificationExemptionHandler.onNotificationChanged(callback.getArgs()[0]);
                            }
                        })
        };
    }
}
