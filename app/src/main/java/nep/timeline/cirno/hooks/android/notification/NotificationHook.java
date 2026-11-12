package nep.timeline.cirno.hooks.android.notification;

import java.lang.reflect.Method;
import java.util.List;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.entity.AppState;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;

public class NotificationHook extends MethodHook {
    private static final String TARGET_CLASS = "com.android.server.notification.NotificationManagerService";
    private static final String TARGET_METHOD = "enqueueNotificationInternal";

    public NotificationHook(ClassLoader classLoader) {
        super(classLoader);
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                int userId = (int) callback.getArgs()[7];
                String packageName = callback.getArgs()[0].toString();
                AppRecord appRecord = AppService.get(packageName, userId);
                if (appRecord != null) {
                    AppState appState = appRecord.getAppState();
                    if (appState.setWaitingNotification(false)) {
                        Log.d(packageName + " 接收消息通知");
                    }
                }
            }
        };
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.resolve(TARGET_CLASS + "#" + TARGET_METHOD,
                        classLoader -> {
                            Class<?> clazz = CakeReflection.findClassIfExists(TARGET_CLASS, classLoader);
                            if (clazz == null) {
                                Log.e("无法监听通知意图!");
                                return List.of();
                            }

                            Method targetMethod = null;
                            for (Method method : clazz.getDeclaredMethods()) {
                                if (method.getName().equals(TARGET_METHOD)
                                        && (targetMethod == null
                                        || targetMethod.getParameterTypes().length < method.getParameterTypes().length)) {
                                    targetMethod = method;
                                }
                            }

                            if (targetMethod == null) {
                                Log.e("无法监听通知意图!");
                                return List.of();
                            }
                            return List.of(targetMethod);
                        },
                        executable -> targetCallback())
        };
    }
}
