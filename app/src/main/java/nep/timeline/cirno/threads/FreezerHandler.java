package nep.timeline.cirno.threads;

import android.os.Handler;
import android.os.Message;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.entity.AppState;
import nep.timeline.cirno.log.Log;

public class FreezerHandler {
    public static final Handler handler = new FreezerMessageHandler(Handlers.makeLooper("Freezer"));

    public static void removeAppMessage(AppRecord appRecord) {
        handler.removeCallbacksAndMessages(appRecord);
    }

    public static void sendFreezeMessage(AppRecord appRecord) {
        if (handler.hasMessages(0, appRecord))
            return;

        sendFreezeMessageIgnoreMessages(appRecord);
    }

    public static void sendFreezeMessageIgnoreMessages(AppRecord appRecord) {
        sendFreezeMessageDelayed(appRecord, getFreezeDelayMs());
    }

    public static void sendTemporaryFreezeMessage(AppRecord appRecord, long delayMs) {
        sendFreezeMessageDelayed(appRecord, Math.max(0L, delayMs));
    }

    public static void sendWaitingNotificationFreezeMessage(AppRecord appRecord, long interval) {
        long startTime = System.currentTimeMillis();
        AppState appState = appRecord.getAppState();
        Runnable waitingNotificationRunnable = new Runnable() {
            @Override
            public void run() {
                if (appRecord.getWaitingNotificationRunnable() != this) {
                    return;
                }
                if (!appState.isWaitingNotification()) {
                    clear();
                    return;
                }
                if (System.currentTimeMillis() - startTime > interval) {
                    appState.setWaitingNotification(false);
                    Log.d(appRecord.getPackageName() + " 等待消息通知超时");
                    clear();
                    return;
                }
                Handlers.notification.postDelayed(this, 1000);
            }

            private void clear() {
                appRecord.clearWaitingNotificationRunnable();
                Log.d(appRecord.getPackageName() + " 消息处理结束，发送冻结消息");
                FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord);
            }
        };
        appRecord.setWaitingNotificationRunnable(waitingNotificationRunnable);
        Handlers.notification.post(waitingNotificationRunnable);
    }

    private static void sendFreezeMessageDelayed(AppRecord appRecord, long delayMs) {
        removeAppMessage(appRecord);

        Message obtain = handler.obtainMessage(0, appRecord);
        if (delayMs < 1)
            handler.sendMessage(obtain);
        else
            handler.sendMessageDelayed(obtain, delayMs);
    }

    /**
     * 每次实时读取冻结延时：
     * 旧实现缓存在 static final 字段，类加载早于配置载入时会永远为 0（失去防抖），
     * 且配置热更新永不生效。
     */
    private static long getFreezeDelayMs() {
        if (GlobalVars.globalSettings == null) {
            return 5_000L;
        }
        return 1000L * GlobalVars.globalSettings.freezeDelay;
    }
}
