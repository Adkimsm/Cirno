package nep.timeline.cirno.handlers;

import android.os.IBinder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.threads.Handlers;

public class OverlayHandler {
    /**
     * AppOps visibility changes can be delivered while addWindow() is still running. Keep the
     * latest value until the add event reaches the overlay handler so a denied window cannot be
     * registered as visible after the visibility callback.
     */
    private static final ConcurrentMap<IBinder, Boolean> WINDOW_VISIBILITY = new ConcurrentHashMap<>();

    public static void onWindowAdded(String packageName, int userId, IBinder token) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        boolean visible = WINDOW_VISIBILITY.getOrDefault(token, true);
        boolean changed = appRecord.getAppState().addWindowToken(token, visible);
        if (changed) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 显示悬浮窗");
            FreezerService.thaw(appRecord);
        }

        // 设置 Binder 死亡监听，防止内存泄漏
        try {
            token.linkToDeath(() -> Handlers.overlay.post(
                    () -> onWindowRemoved(packageName, userId, token)), 0);
        } catch (Exception e) {
            Log.e("OverlayHandler linkToDeath 失败", e);
        }
    }

    public static void onWindowVisibilityChanged(String packageName, int userId,
            IBinder token, boolean visible) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        WINDOW_VISIBILITY.put(token, visible);
        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        boolean changed = appRecord.getAppState().setWindowTokenVisible(token, visible);
        if (!changed) {
            return;
        }

        if (visible) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 显示悬浮窗");
            FreezerService.thaw(appRecord);
        } else {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 关闭悬浮窗");
            FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord);
        }
    }

    public static void onWindowRemoved(String packageName, int userId, IBinder token) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        WINDOW_VISIBILITY.remove(token);
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
