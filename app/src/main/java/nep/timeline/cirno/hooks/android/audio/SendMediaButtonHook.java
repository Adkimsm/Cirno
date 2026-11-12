package nep.timeline.cirno.hooks.android.audio;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.FreezerService;

public class SendMediaButtonHook extends MethodHook {
    private static final String TARGET_CLASS = "com.android.server.media.MediaSessionRecord$SessionCb";
    private static final List<String> TARGET_METHODS = List.of(
            "sendMediaButton", "play", "playFromMediaId", "playFromSearch",
            "playFromUri", "next", "previous", "seekTo");

    public SendMediaButtonHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        Class<?> targetClass = CakeReflection.findClassIfExists(TARGET_CLASS, classLoader);
        if (targetClass == null) {
            Log.w("MediaSessionRecord$SessionCb 不存在");
            return new HookTarget[0];
        }

        String fieldName = null;
        for (Field field : targetClass.getDeclaredFields()) {
            if (field.getType().getName().equals("com.android.server.media.MediaSessionRecord")) {
                fieldName = field.getName();
                break;
            }
        }

        if (fieldName == null) {
            Log.w("无法监听媒体按键!");
            return new HookTarget[0];
        }

        final String ownerField = fieldName;
        return new HookTarget[] {
                HookTarget.resolve(TARGET_CLASS + "#mediaButton",
                        classLoader -> {
                            List<Method> methods = new ArrayList<>();
                            for (Method method : targetClass.getDeclaredMethods()) {
                                if (TARGET_METHODS.contains(method.getName())) {
                                    methods.add(method);
                                }
                            }
                            return methods;
                        },
                        executable -> createHookCallback(ownerField))
        };
    }

    private static CakeHooker.Callback createHookCallback(String fieldName) {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                try {
                    Object record = CakeReflection.getObjectField(callback.getThisObject(), fieldName);
                    if (record == null) {
                        return;
                    }

                    int uid = CakeReflection.getIntField(record, "mOwnerUid");
                    FreezerService.temporaryUnfreezeIfNeed(uid, "媒体按键", 3000);
                } catch (Exception e) {
                    Log.e("媒体按键处理失败", e);
                }
            }
        };
    }
}
