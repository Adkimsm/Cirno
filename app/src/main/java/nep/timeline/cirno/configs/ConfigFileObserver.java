package nep.timeline.cirno.configs;

import android.os.FileObserver;
import android.os.Handler;

import java.io.File;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.master.AndroidHooks;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.log.Log;

public class ConfigFileObserver extends FileObserver {
    private static final String GLOBAL_SETTINGS_FILE = "GlobalSettings.json";
    private static final String APPLICATION_SETTINGS_FILE = "ApplicationSettings.json";
    private static final Object LOCK = new Object();

    public ConfigFileObserver() {
        super(GlobalVars.CONFIG_DIR, FileObserver.DELETE | FileObserver.DELETE_SELF | FileObserver.MODIFY | FileObserver.MOVE_SELF);
        reInit();
        readConfigSynchronized();
    }

    @Override
    public void onEvent(int event, String path) {
        Handler handler = Handlers.config;
        switch (event & FileObserver.ALL_EVENTS) {
            // DELETE_SELF/MOVE_SELF 是对被监视目录自身的事件，path 恒为 null，
            // 不能用 path == null 提前返回（否则目录被删除重建后热更新永久失效）
            case FileObserver.DELETE_SELF:
            case FileObserver.MOVE_SELF: {
                Log.d("配置监听：配置目录被删除/移动 EVENT " + event);
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> {
                    reInit();
                    // 目录被删除后内核会移除 inotify watch，必须重新注册
                    startWatching();
                    readConfigSynchronized();
                }, 2000);
                break;
            }
            case FileObserver.DELETE:
            case FileObserver.MODIFY: {
                // 只响应两个配置文件的变化，无关文件事件不再取消已排队的重载任务
                if (!GLOBAL_SETTINGS_FILE.equals(path) && !APPLICATION_SETTINGS_FILE.equals(path)) break;
                Log.d("配置热更新：EVENT " + event + " Path " + path);
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(ConfigFileObserver::readConfigSynchronized, 2000);
                break;
            }
        }
    }

    private static void readConfigSynchronized() {
        synchronized (LOCK) {
            ConfigManager.manager.readConfig();
            AndroidHooks.syncCachedAppOptimizerHooks();
        }
    }

    public void reInit() {
        File configDir = new File(GlobalVars.CONFIG_DIR);
        if (!configDir.exists())
            configDir.mkdir();
        File logDir = new File(GlobalVars.LOG_DIR);
        if (!logDir.exists())
            logDir.mkdir();
    }
}
