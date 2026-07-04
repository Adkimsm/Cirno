package nep.timeline.cirno.hooks.android.notification;

import java.lang.reflect.Method;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.threads.FreezerHandler;

public class NotificationHook {
    public NotificationHook(ClassLoader classLoader) {
        try {
            Class<?> clazz = CakeReflection.findClassIfExists("com.android.server.notification.NotificationManagerService", classLoader);

            if (clazz == null) {
                Log.e("无法监听通知意图!");
                return;
            }

            Method targetMethod = null;
            for (Method method : clazz.getDeclaredMethods())
                if (method.getName().equals("enqueueNotificationInternal") && (targetMethod == null || targetMethod.getParameterTypes().length < method.getParameterTypes().length))
                    targetMethod = method;

            if (targetMethod == null) {
                Log.e("无法监听通知意图!");
                return;
            }

            CakeHooker.hook(targetMethod, new CakeHooker.Callback() {
                @Override
                public void call(CakeHooker.BeforeHookCallback callback) {
                    int userId = (int) callback.getArgs()[7];
                    String packageName = callback.getArgs()[0].toString();
                    AppRecord appRecord = AppService.get(packageName, userId);
                    if (appRecord != null && appRecord.isWaitingNotification()) {
                        appRecord.setWaitingNotification(false);
                        Log.d(packageName + " 接收消息通知");
                    }
                }
            });

            Log.i("监听通知意图");
        } catch (Throwable throwable) {
            Log.e(GlobalVars.TAG + " -> 无法通知广播意图, 异常", throwable);
            Log.e("监听通知意图失败", throwable);
        }
    }
}
