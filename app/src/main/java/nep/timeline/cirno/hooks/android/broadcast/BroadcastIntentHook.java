package nep.timeline.cirno.hooks.android.broadcast;

import android.content.Intent;

import java.lang.reflect.Method;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.entity.AppState;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.Handlers;

public class BroadcastIntentHook {
    private static final String ACTION_TILE_CLICK = "nep.timeline.cirno.TILE_CLICK";

    public BroadcastIntentHook(ClassLoader classLoader) {
        try {
            Class<?> amsClass = CakeReflection.findClassIfExists("com.android.server.am.ActivityManagerService", classLoader);
            Class<?> controllerClass = CakeReflection.findClassIfExists("com.android.server.am.BroadcastController", classLoader);

            Method amsMethod = amsClass != null ? findLongestBroadcastIntentLocked(amsClass) : null;
            Method controllerMethod = controllerClass != null ? findLongestBroadcastIntentLocked(controllerClass) : null;

            if (amsMethod == null) {
                Log.i("未找到 ActivityManagerService.broadcastIntentLocked 方法");
            }
            if (controllerMethod == null) {
                Log.i("未找到 BroadcastController.broadcastIntentLocked 方法");
            }
            if (amsMethod == null && controllerMethod == null) {
                Log.e("无法监听广播意图");
                return;
            }

            if (amsMethod != null) {
                CakeHooker.hook(amsMethod, createHookCallback(amsMethod));
                Log.i("监听广播意图 (ActivityManagerService)");
            }
            if (controllerMethod != null) {
                CakeHooker.hook(controllerMethod, createHookCallback(controllerMethod));
                Log.i("监听广播意图 (BroadcastController)");
            }
        } catch (Throwable throwable) {
            Log.e("监听广播意图失败", throwable);
        }
    }

    private static CakeHooker.Callback createHookCallback(Method method) {
        // broadcastIntentLocked 是持 AMS 锁的最热路径之一：
        // 参数下标在 hook 安装时一次性解析，避免每次广播都克隆参数类型数组做线性扫描
        Class<?>[] paramTypes = method.getParameterTypes();
        int intentIdx = -1;
        int userIdIdx = -1;
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == Intent.class && intentIdx < 0) {
                intentIdx = i;
            }
            if (paramTypes[i] == int.class && i > 3) {
                userIdIdx = i;
            }
        }

        final int intentIndex = intentIdx;
        final int userIdIndex = userIdIdx;

        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (intentIndex < 0 || userIdIndex < 0) {
                    return;
                }

                Object[] args = callback.getArgs();
                Intent intent = (Intent) args[intentIndex];
                if (intent == null) {
                    return;
                }
                int userId = (int) args[userIdIndex];

                String action = intent.getAction();
                if (action == null) {
                    return;
                }

                if (ACTION_TILE_CLICK.equals(action)) {
                    String packageName = intent.getStringExtra("package_name");
                    if (packageName != null) {
                        postTileClickUnfreeze(packageName, userId);
                    }
                    return;
                }

                // 推送广播：FCM(c2dm)、UnifiedPush、魅族推送均触发临时解冻
                if (!action.endsWith(".android.c2dm.intent.RECEIVE")
                        && !action.equals("org.unifiedpush.android.connector.MESSAGE")
                        && !action.equals("com.meizu.flyme.push.intent.MESSAGE")) {
                    return;
                }

                String packageName = intent.getComponent() == null
                        ? intent.getPackage()
                        : intent.getComponent().getPackageName();
                if (packageName == null) {
                    return;
                }

                postPushUnfreeze(packageName, userId);
            }
        };
    }

    private static void postTileClickUnfreeze(String packageName, int userId) {
        Handlers.broadcast.post(() -> {
            try {
                FreezerService.temporaryUnfreezeIfNeed(packageName, userId, "控制中心磁贴", 3000);
            } catch (Throwable throwable) {
                Log.d("广播异步处理磁贴 package=" + packageName + " userId=" + userId, throwable);
            }
        });
    }

    private static void postPushUnfreeze(String packageName, int userId) {
        Handlers.broadcast.post(() -> {
            try {
                AppRecord appRecord = AppService.get(packageName, userId);
                if (appRecord == null) {
                    return;
                }

                AppState appState = appRecord.getAppState();
                if (appState.isVisible()) {
                    appState.setWaitingNotification(false);
                    return;
                }
                if (!appState.setWaitingNotification(true)) {
                    appRecord.clearWaitingNotificationRunnable();
                }

                FreezerService.temporaryUnfreezeIfNeed(
                        appRecord,
                        "MESSAGE PUSH",
                        1000L * GlobalVars.globalSettings.wakeFreezeDelay
                );
            } catch (Throwable throwable) {
                Log.d("广播异步处理推送 package=" + packageName + " userId=" + userId, throwable);
            }
        });
    }

    private static Method findLongestBroadcastIntentLocked(Class<?> clazz) {
        Method longest = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("broadcastIntentLocked")) {
                if (longest == null || method.getParameterTypes().length > longest.getParameterTypes().length) {
                    longest = method;
                }
            }
        }
        return longest;
    }
}
