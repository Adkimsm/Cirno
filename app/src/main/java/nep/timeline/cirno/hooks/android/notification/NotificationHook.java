package nep.timeline.cirno.hooks.android.notification;

import java.lang.reflect.Method;

import nep.timeline.cirno.entity.AppState;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;

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

            // OEM ROM 可能改变 enqueueNotificationInternal 的参数布局，
            // 不能硬编码 args[7]：按签名定位最后一个 int 参数（AOSP 中即 incomingUserId）
            Class<?>[] paramTypes = targetMethod.getParameterTypes();
            int userIdIdx = -1;
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i] == int.class)
                    userIdIdx = i;
            }
            if (userIdIdx < 0 || paramTypes.length < 1 || paramTypes[0] != String.class) {
                Log.e("enqueueNotificationInternal 签名不符合预期，跳过通知监听");
                return;
            }
            final int userIdIndex = userIdIdx;

            CakeHooker.hook(targetMethod, new CakeHooker.Callback() {
                @Override
                public void call(CakeHooker.BeforeHookCallback callback) {
                    Object[] args = callback.getArgs();
                    Object pkgArg = args[0];
                    if (pkgArg == null)
                        return;
                    int userId = (int) args[userIdIndex];
                    String packageName = pkgArg.toString();
                    AppRecord appRecord = AppService.get(packageName, userId);
                    if (appRecord != null) {
                        AppState appState = appRecord.getAppState();
                        if(appState.setWaitingNotification(false)) {
                            Log.d(packageName + " 接收消息通知");
                        }
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
