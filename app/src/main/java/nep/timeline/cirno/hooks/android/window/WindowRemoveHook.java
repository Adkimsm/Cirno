package nep.timeline.cirno.hooks.android.window;

import android.os.IBinder;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.OverlayHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.PKGUtils;

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
        return "removeClientToken";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{
            "com.android.server.wm.Session",
            IBinder.class
        };
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

                    // IBinder client = args[1]
                    IBinder clientToken = (IBinder) callback.getArgs()[1];
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
