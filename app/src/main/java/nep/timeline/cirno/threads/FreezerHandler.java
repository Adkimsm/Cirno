package nep.timeline.cirno.threads;

import android.os.Handler;
import android.os.Message;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;

public class FreezerHandler {
    public static final Handler handler = new FreezerMessageHandler(Handlers.makeLooper("Freezer"));
    private static final long FreezeDelayNum = getFreezeDelayMs();

    public static void removeAppMessage(AppRecord appRecord) {
        handler.removeCallbacksAndMessages(appRecord);
    }

    public static void sendFreezeMessage(AppRecord appRecord) {
        if (handler.hasMessages(0, appRecord))
            return;

        sendFreezeMessageIgnoreMessages(appRecord);
    }

    public static void sendFreezeMessageIgnoreMessages(AppRecord appRecord) {
        sendFreezeMessageDelayed(appRecord, FreezeDelayNum);
    }

    public static void sendTemporaryFreezeMessage(AppRecord appRecord, long delayMs) {
        sendFreezeMessageDelayed(appRecord, Math.max(0L, delayMs));
    }

    private static void sendFreezeMessageDelayed(AppRecord appRecord, long delayMs) {
        removeAppMessage(appRecord);

        Message obtain = handler.obtainMessage(0, appRecord);
        if (delayMs < 1)
            handler.sendMessage(obtain);
        else
            handler.sendMessageDelayed(obtain, delayMs);
    }

    private static long getFreezeDelayMs() {
        if (GlobalVars.globalSettings == null) {
            return 0L;
        }
        return 1000L * GlobalVars.globalSettings.freezeDelay;
    }
}
