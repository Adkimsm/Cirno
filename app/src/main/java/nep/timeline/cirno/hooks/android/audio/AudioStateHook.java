package nep.timeline.cirno.hooks.android.audio;

import android.media.AudioPlaybackConfiguration;
import android.os.Build;

import java.util.List;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.AudioHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.ReflectUtils;
import nep.timeline.cirno.virtuals.AudioPlaybackConfigurationReflect;

public class AudioStateHook extends MethodHook {
    public AudioStateHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        Object[] params = targetParams();
        if (params == null)
            return new HookTarget[0];
        return new HookTarget[] {
                HookTarget.method(targetClass(), targetMethod(), params, targetCallback())
        };
    }

    private String targetClass() {
        return AudioPlaybackConfiguration.class.getTypeName();
    }

    private String targetMethod() {
        return "handleStateEvent";
    }

    private Object[] targetParams() {
        if (Build.VERSION.SDK_INT >= 36)
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(targetClass(), classLoader),
                    targetMethod(), int.class, int[].class);
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(targetClass(), classLoader),
                targetMethod(), int.class);
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    Object result = callback.result;
                    if (result instanceof Boolean && !(boolean) result) {
                        return;
                    }

                    int event = (int) callback.getArgs()[0];

                    if (!AudioHandler.LISTEN_EVENT.contains(event)) {
                        return;
                    }

                    AudioPlaybackConfigurationReflect reflect = new AudioPlaybackConfigurationReflect((AudioPlaybackConfiguration) callback.getThisObject());

                    Handlers.audio.post(() -> {
                        try {
                            List<AppRecord> appRecords = AppService.getByUid(reflect.getClientUid());
                            if (appRecords == null) {
                                return;
                            }

                            int interfaceId = reflect.getPlayerInterfaceId();
                            for (AppRecord appRecord : appRecords) {
                                if (appRecord == null) {
                                    continue;
                                }

                                AudioHandler.call(appRecord, event, interfaceId);
                            }
                        } catch (Exception e) {
                            Log.e("AudioStateHook 处理异常", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("AudioStateHook 异常", e);
                }
            }
        };
    }
}
