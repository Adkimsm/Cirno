package nep.timeline.cirno.hooks.android.window;

import android.os.IBinder;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.OverlayHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.PKGUtils;

public class WindowAppOpVisibilityHook extends MethodHook {
    private static final int TYPE_APPLICATION_OVERLAY = 2038;

    public WindowAppOpVisibilityHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.wm.WindowState";
    }

    @Override
    public String getTargetMethod() {
        return "setAppOpVisibilityLw";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{boolean.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    Object windowState = callback.getThisObject();
                    Object attrs = CakeReflection.getObjectField(windowState, "mAttrs");
                    if ((int) CakeReflection.getObjectField(attrs, "type")
                            != TYPE_APPLICATION_OVERLAY) {
                        return;
                    }

                    Object session = CakeReflection.getObjectField(windowState, "mSession");
                    Object client = CakeReflection.getObjectField(windowState, "mClient");
                    if (session == null || client == null) {
                        return;
                    }

                    String packageName = (String) CakeReflection.getObjectField(session, "mPackageName");
                    int uid = CakeReflection.getIntField(session, "mUid");
                    IBinder token = (IBinder) CakeReflection.callMethod(client, "asBinder");
                    if (packageName == null || packageName.isEmpty() || token == null) {
                        return;
                    }

                    boolean visible = (boolean) callback.getArgs()[0];
                    int userId = PKGUtils.getUserId(uid);
                    Handlers.overlay.post(() -> {
                        try {
                            OverlayHandler.onWindowVisibilityChanged(
                                    packageName, userId, token, visible);
                        } catch (Exception e) {
                            Log.e("WindowAppOpVisibilityHook 处理异常", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("WindowAppOpVisibilityHook 异常", e);
                }
            }
        };
    }
}
