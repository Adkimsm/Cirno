package nep.timeline.cirno.hooks.android.camera;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.CameraHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;

public class CameraBinderDiedHook extends MethodHook {
    public CameraBinderDiedHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.camera.CameraServiceProxy", "binderDied", new Object[]{}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                Handlers.camera.post(() -> {
                    try {
                        CameraHandler.clearAll();
                    } catch (Exception e) {
                        Log.e("CameraBinderDiedHook 处理异常", e);
                    }
                });
            }
        })
        };
    }

}
