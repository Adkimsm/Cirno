package nep.timeline.cirno.hooks.android.window;

import android.os.IBinder;
import android.view.View;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.OverlayHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.PKGUtils;
import nep.timeline.cirno.utils.ReflectUtils;

public class WindowAddHook extends MethodHook {
    private static final int TYPE_APPLICATION_OVERLAY = 2038;

    public WindowAddHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.wm.WindowManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "addWindow";
    }

    @Override
    public Object[] getTargetParam() {
        // 提供前 6 个核心参数，让工具匹配完整签名
        return ReflectUtils.findParameterTypesOrDefault(
            CakeReflection.findClassIfExists(getTargetClass(), classLoader),
            getTargetMethod(),
            "com.android.server.wm.Session",
            "android.view.IWindow",
            "android.view.WindowManager$LayoutParams",
            int.class,
            int.class,
            int.class
        );
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    if (callback.getArgs().length < 4) {
                        return;
                    }

                    // Session session = args[0]
                    Object session = callback.getArgs()[0];
                    if (session == null) {
                        return;
                    }

                    // IWindow client = args[1]
                    Object client = callback.getArgs()[1];
                    if (client == null) {
                        return;
                    }

                    // LayoutParams attrs = args[2]
                    Object attrs = callback.getArgs()[2];
                    if (attrs == null) {
                        return;
                    }

                    // int viewVisibility = args[3]
                    int viewVisibility = (int) callback.getArgs()[3];

                    // 提取窗口类型
                    int windowType = (int) CakeReflection.getObjectField(attrs, "type");
                    if (windowType != TYPE_APPLICATION_OVERLAY) {
                        return;
                    }

                    // 提取包名
                    String packageName = (String) CakeReflection.getObjectField(attrs, "packageName");
                    if (packageName == null || packageName.isEmpty()) {
                        return;
                    }

                    // 只处理可见窗口
                    if (viewVisibility != View.VISIBLE) {
                        return;
                    }

                    // 提取 UID 和 userId
                    int uid = (int) CakeReflection.getObjectField(session, "mUid");
                    int userId = PKGUtils.getUserId(uid);

                    // 提取 client token
                    IBinder clientToken = (IBinder) CakeReflection.callMethod(client, "asBinder");
                    if (clientToken == null) {
                        return;
                    }

                    // 投递到 overlay 线程处理
                    Handlers.overlay.post(() -> {
                        try {
                            OverlayHandler.onWindowAdded(packageName, userId, clientToken);
                        } catch (Exception e) {
                            Log.e("WindowAddHook 处理异常", e);
                        }
                    });

                } catch (Exception e) {
                    Log.e("WindowAddHook 异常", e);
                }
            }
        };
    }
}
