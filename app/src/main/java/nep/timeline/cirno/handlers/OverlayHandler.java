package nep.timeline.cirno.handlers;

import android.os.IBinder;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.FreezerHandler;

public class OverlayHandler {

    public static void onWindowAdded(String packageName, int userId, IBinder token) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        if (appRecord.getAppState().addWindowToken(token)) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 显示悬浮窗");
            FreezerService.thaw(appRecord);

            // 设置 Binder 死亡监听，防止内存泄漏
            try {
                token.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        onWindowRemoved(packageName, userId, token);
                    }
                }, 0);
            } catch (Exception e) {
                Log.e("OverlayHandler linkToDeath 失败", e);
            }
        }
    }

    public static void onWindowRemoved(String packageName, int userId, IBinder token) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        if (appRecord.getAppState().removeWindowToken(token)) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 关闭悬浮窗");
            FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord);
        }
    }
}
