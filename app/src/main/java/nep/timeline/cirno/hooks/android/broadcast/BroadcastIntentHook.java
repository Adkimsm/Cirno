package nep.timeline.cirno.hooks.android.broadcast;

import android.content.Intent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;

public class BroadcastIntentHook {
    private static final String ACTION_TILE_CLICK = "nep.timeline.cirno.TILE_CLICK";

    public BroadcastIntentHook(ClassLoader classLoader) {
        try {
            Class<?> amsClass = CakeReflection.findClassIfExists("com.android.server.am.ActivityManagerService", classLoader);
            Class<?> controllerClass = CakeReflection.findClassIfExists("com.android.server.am.BroadcastController", classLoader);

            Method amsMethod = amsClass != null ? findShortestBroadcastIntentLocked(amsClass) : null;
            Method controllerMethod = controllerClass != null ? findShortestBroadcastIntentLocked(controllerClass) : null;

            if (amsMethod == null && controllerMethod == null) {
                Log.e("无法监听广播意图，未找到 broadcastIntentLocked 方法");
                return;
            }

            AtomicBoolean handling = new AtomicBoolean(false);
            CakeHooker.Callback hookCallback = new CakeHooker.Callback() {
                @Override
                public void call(CakeHooker.BeforeHookCallback callback) {
                    if (handling.getAndSet(true)) {
                        return;
                    }
                    try {
                        Method method = (Method) callback.getExecutable();
                        if (method == null) {
                            return;
                        }

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
                        if (intent != null) {
                            String action = intent.getAction();

                            if (ACTION_TILE_CLICK.equals(action)) {
                                String packageName = intent.getStringExtra("package_name");
                                if (packageName != null) {
                                    FreezerService.temporaryUnfreezeIfNeed(packageName, userId, "控制中心磁贴", 3000);
                                }
                                return;
                            }

                            if (action == null || !action.endsWith(".android.c2dm.intent.RECEIVE") || action.equals("org.unifiedpush.android.connector.MESSAGE") || action.equals("com.meizu.flyme.push.intent.MESSAGE"))
                                return;

                            String packageName = (intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName());

                            if (packageName == null)
                                return;

                            AppRecord appRecord = AppService.get(packageName, userId);
                            if (appRecord == null)
                                return;
                            if (appRecord.isWaitingNotification()) {
                                appRecord.clearWaitingNotificationRunnable();
                            }
                            appRecord.setWaitingNotification(true);

                            FreezerService.temporaryUnfreezeIfNeed(appRecord, "MESSAGE PUSH", 1000L * GlobalVars.globalSettings.wakeFreezeDelay);
                        }
                    } finally {
                        handling.set(false);
                    }
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

    private static Method findShortestBroadcastIntentLocked(Class<?> clazz) {
        Method shortest = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("broadcastIntentLocked")) {
                if (shortest == null || method.getParameterTypes().length < shortest.getParameterTypes().length) {
                    shortest = method;
                }
            }
        }
        return shortest;
    }
}
