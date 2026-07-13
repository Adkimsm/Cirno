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

            CakeHooker.Callback hookCallback = new CakeHooker.Callback() {
                @Override
                public void call(CakeHooker.BeforeHookCallback callback) {
                    Method method = (Method) callback.getExecutable();

                    Class<?>[] paramTypes = method.getParameterTypes();
                    int intentIndex = -1;
                    int userIdIndex = -1;
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (paramTypes[i] == Intent.class && intentIndex < 0) {
                            intentIndex = i;
                        }
                        if (paramTypes[i] == int.class && i > 3) {
                            userIdIndex = i;
                        }
                    }

                    if (intentIndex < 0 || userIdIndex < 0) {
                        return;
                    }

                    Intent intent = (Intent) callback.getArgs()[intentIndex];
                    int userId = (int) callback.getArgs()[userIdIndex];
                    if (intent == null) {
                        return;
                    }

                    String action = intent.getAction();
                    if (ACTION_TILE_CLICK.equals(action)) {
                        String packageName = intent.getStringExtra("package_name");
                        if (packageName != null) {
                            postTileClickUnfreeze(packageName, userId);
                        }
                        return;
                    }

                    if (action == null
                            || !action.endsWith(".android.c2dm.intent.RECEIVE")
                            || action.equals("org.unifiedpush.android.connector.MESSAGE")
                            || action.equals("com.meizu.flyme.push.intent.MESSAGE")) {
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

            if (amsMethod != null) {
                CakeHooker.hook(amsMethod, hookCallback);
                Log.i("监听广播意图 (ActivityManagerService)");
            }
            if (controllerMethod != null) {
                CakeHooker.hook(controllerMethod, hookCallback);
                Log.i("监听广播意图 (BroadcastController)");
            }
        } catch (Throwable throwable) {
            Log.e("监听广播意图失败", throwable);
        }
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
