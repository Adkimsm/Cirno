package nep.timeline.cirno.hooks.android.window;

import android.os.Build;
import android.os.IBinder;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.OverlayHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.PKGUtils;

/**
 * Hook for monitoring overlay window removal.
 * 
 * Version compatibility:
 * - Android 12-14 (API 31-34): Hooks removeWindow(Session, IWindow)
 * - Android 15-16 (API 35-36): Hooks removeClientToken(Session, IBinder)
 * 
 * The method name and parameter type changed in Android 15.
 * This hook automatically adapts based on Build.VERSION.SDK_INT.
 */
public class WindowRemoveHook extends MethodHook {

    public WindowRemoveHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.wm.WindowManagerService";
    }

    @Override
    public String getTargetMethod() {
        // Android 15+ (API 35+) uses removeClientToken
        // Android 12-14 (API 31-34) uses removeWindow
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return "removeClientToken";
        } else {
            return "removeWindow";
        }
    }

    @Override
    public Object[] getTargetParam() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+: removeClientToken(Session, IBinder)
            return new Object[]{
                "com.android.server.wm.Session",
                IBinder.class
            };
        } else {
            // Android 12-14: removeWindow(Session, IWindow)
            return new Object[]{
                "com.android.server.wm.Session",
                "android.view.IWindow"
            };
        }
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                try {
                    if (callback.getArgs().length < 2) {
                        return;
                    }

                    // Session session = args[0]
                    Object session = callback.getArgs()[0];
                    if (session == null) {
                        return;
                    }

                    // Get client token (type differs by Android version)
                    IBinder clientToken;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        // Android 15+: args[1] is IBinder directly
                        clientToken = (IBinder) callback.getArgs()[1];
                    } else {
                        // Android 12-14: args[1] is IWindow, need to convert to IBinder
                        Object client = callback.getArgs()[1];
                        if (client == null) {
                            return;
                        }
                        clientToken = (IBinder) CakeReflection.callMethod(client, "asBinder");
                    }
                    
                    if (clientToken == null) {
                        return;
                    }

                    // 提取包名和 userId
                    int uid = (int) CakeReflection.getObjectField(session, "mUid");
                    int userId = PKGUtils.getUserId(uid);
                    String packageName = (String) CakeReflection.getObjectField(session, "mPackageName");

                    if (packageName == null || packageName.isEmpty()) {
                        return;
                    }

                    // 投递到 overlay 线程处理
                    Handlers.overlay.post(() -> {
                        try {
                            OverlayHandler.onWindowRemoved(packageName, userId, clientToken);
                        } catch (Exception e) {
                            Log.e("WindowRemoveHook 处理异常", e);
                        }
                    });

                } catch (Exception e) {
                    Log.e("WindowRemoveHook 异常", e);
                }
            }
        };
    }
}
