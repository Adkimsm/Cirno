package nep.timeline.cirno.hooks.android.audio;

import android.media.AudioPlaybackConfiguration;

import java.util.List;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.AudioHandler;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.AudioPlaybackConfigurationReflect;

public class PlayerBanHook extends MethodHook {
    public PlayerBanHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.audio.PlaybackActivityMonitor", "checkBanPlayer", new Object[]{AudioPlaybackConfiguration.class, int.class}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    boolean result = (boolean) callback.result;

                    Object configuration = callback.getArgs()[0];
                    if (configuration == null) {
                        return;
                    }

                    try {
                        AudioPlaybackConfigurationReflect reflect =
                                new AudioPlaybackConfigurationReflect((AudioPlaybackConfiguration) configuration);

                        int uid = reflect.getClientUid();
                        int interfaceId = reflect.getPlayerInterfaceId();

                        if (result) {
                            Handlers.audio.post(() -> {
                                List<AppRecord> appRecords = AppService.getByUid(uid);
                                if (appRecords == null || appRecords.isEmpty()) {
                                    return;
                                }

                                for (AppRecord appRecord : appRecords) {
                                    if (appRecord == null)
                                        continue;

                                    AudioHandler.call(appRecord, AudioHandler.PLAYER_STATE_PAUSED, interfaceId);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e("PlayerBanHook 反射失败", e);
                    }

                } catch (Exception e) {
                    Log.e("PlayerBanHook 异常", e);
                }
            }
        })
        };
    }

}
