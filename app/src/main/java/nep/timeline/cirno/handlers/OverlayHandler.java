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
    private static final ConcurrentMap<IBinder, Boolean> APP_WINDOW_VISIBILITY = new ConcurrentHashMap<>();
    private static final ConcurrentMap<IBinder, Boolean> APP_OP_VISIBILITY = new ConcurrentHashMap<>();

    public static void onWindowAdded(String packageName, int userId, IBinder token) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        boolean currentAppVisible = APP_WINDOW_VISIBILITY.getOrDefault(token, false);
        // AppOps visibility is unknown until WindowState initializes it. Do not grant an
        // exemption before that state has been observed.
        boolean appOpVisible = APP_OP_VISIBILITY.getOrDefault(token, false);
        boolean changed = appRecord.getAppState().addWindowToken(
                token, currentAppVisible, appOpVisible);
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

    public static void onWindowAppVisibilityChanged(String packageName, int userId,
            IBinder token, boolean visible) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        APP_WINDOW_VISIBILITY.put(token, visible);
        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        boolean changed = appRecord.getAppState().setWindowAppVisibility(token, visible);
        onOverlayStateChanged(appRecord, visible, changed);
    }

    public static void onWindowAppOpVisibilityChanged(String packageName, int userId,
            IBinder token, boolean visible) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        APP_OP_VISIBILITY.put(token, visible);
        AppRecord appRecord = AppService.get(packageName, userId);
        if (appRecord == null) {
            return;
        }

        boolean changed = appRecord.getAppState().setWindowAppOpVisibility(token, visible);
        onOverlayStateChanged(appRecord, visible, changed);
    }

    private static void onOverlayStateChanged(AppRecord appRecord, boolean visible,
            boolean changed) {
        if (!changed) {
            return;
        }

        if (visible) {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 显示悬浮窗");
            FreezerService.thaw(appRecord);
        } else {
            Log.d("应用 " + appRecord.getPackageNameWithUser() + " 悬浮窗不可见");
            FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord);
        }
    }

    public static void onWindowRemoved(String packageName, int userId, IBinder token) {
        if (packageName == null || packageName.isEmpty() || token == null) {
            return;
        }

        APP_WINDOW_VISIBILITY.remove(token);
        APP_OP_VISIBILITY.remove(token);
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
