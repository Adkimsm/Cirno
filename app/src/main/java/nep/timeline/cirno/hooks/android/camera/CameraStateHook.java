package nep.timeline.cirno.hooks.android.camera;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.CameraHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.ReflectUtils;

public class CameraStateHook extends MethodHook {
    public CameraStateHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.camera.CameraServiceProxy", "updateActivityCount", ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists("com.android.server.camera.CameraServiceProxy", classLoader),
                "updateActivityCount", "android.hardware.CameraSessionStats"), new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    if (callback.getArgs().length < 1) {
                        return;
                    }

                    Object cameraState = callback.getArgs()[0];
                    if (cameraState == null) {
                        return;
                    }

                    String cameraId = (String) CakeReflection.callMethod(cameraState, "getCameraId");
                    String clientName = (String) CakeReflection.callMethod(cameraState, "getClientName");
                    int state = (int) CakeReflection.callMethod(cameraState, "getNewCameraState");

                    if (state != CameraHandler.CAMERA_STATE_ACTIVE
                            && state != CameraHandler.CAMERA_STATE_IDLE
                            && state != CameraHandler.CAMERA_STATE_CLOSED) {
                        return;
                    }

                    Set<Integer> userIds = resolveEnabledCameraUsers(callback.getThisObject());
                    Handlers.camera.post(() -> {
                        try {
                            CameraHandler.call(clientName, userIds, cameraId, state);
                        } catch (Exception e) {
                            Log.e("CameraStateHook 处理异常", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("CameraStateHook 异常", e);
                }
            }

            @SuppressWarnings("unchecked")
            private Set<Integer> resolveEnabledCameraUsers(Object proxy) {
                try {
                    Object enabledUsers = CakeReflection.getObjectField(proxy, "mEnabledCameraUsers");
                    if (enabledUsers instanceof Set<?>) {
                        Set<Integer> userIds = new LinkedHashSet<>();
                        for (Object userId : (Set<?>) enabledUsers) {
                            if (userId instanceof Integer) {
                                userIds.add((Integer) userId);
                            }
                        }
                        if (!userIds.isEmpty()) {
                            return userIds;
                        }
                    }
                } catch (Exception ignored) {
                }

                return Collections.singleton(ActivityManagerService.getCurrentOrTargetUserId());
            }
        })
        };
    }

}
