package nep.timeline.cirno.utils;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;

import java.util.HashMap;
import java.util.Map;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;

/**
 * 当前输入法状态：主路径来自 IMMS 内部 mMethodMap/mMap（与上游 cirno-nep 一致），
 * Settings 仅作启动/热重载兜底。
 */
public class InputMethodData {
    private static final int MAX_INIT_RETRY = 30;
    private static final long INIT_RETRY_DELAY_MS = 1000L;

    public static volatile Object instance;
    public static Map<String, InputMethodInfo> inputMethods = new HashMap<>();
    public static InputMethodInfo currentInputMethodInfo;
    public static AppRecord currentInputMethodApp;

    // 热重载：AppRecord 可能需重建，额外保存 package/user
    private static String currentInputMethodPackageName;
    private static int currentInputMethodUserId = -1;

    public static synchronized Map<String, Object> saveState() {
        HashMap<String, Object> state = new HashMap<>();
        if (currentInputMethodApp != null) {
            state.put("currentInputMethodPackageName", currentInputMethodApp.getPackageName());
            state.put("currentInputMethodUserId", currentInputMethodApp.getUserId());
        } else {
            state.put("currentInputMethodPackageName", currentInputMethodPackageName);
            state.put("currentInputMethodUserId", currentInputMethodUserId);
        }
        return state;
    }

    public static synchronized void restoreState(Object savedState) {
        if (!(savedState instanceof Map<?, ?> state))
            return;

        Object packageName = state.get("currentInputMethodPackageName");
        currentInputMethodPackageName = packageName instanceof String ? (String) packageName : null;
        Object userId = state.get("currentInputMethodUserId");
        currentInputMethodUserId = userId instanceof Integer ? (Integer) userId : -1;

        // IMMS 实例与 method map 指向旧对象，热重载后必须丢弃
        instance = null;
        inputMethods = new HashMap<>();
        currentInputMethodInfo = null;
        currentInputMethodApp = null;

        if (currentInputMethodPackageName != null && currentInputMethodUserId >= 0) {
            currentInputMethodApp = AppService.get(currentInputMethodPackageName, currentInputMethodUserId);
        }
    }

    public static void initFromSettingsWithRetry() {
        Thread thread = new Thread(() -> {
            for (int attempt = 0; attempt <= MAX_INIT_RETRY; attempt++) {
                if (refreshFromSettings()) {
                    AppRecord appRecord = getCurrentInputMethodApp();
                    if (appRecord != null) {
                        FreezerService.thaw(appRecord);
                    }
                    return;
                }
                try {
                    Thread.sleep(INIT_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Cirno-IME-Init");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 启动/热重载兜底：优先用已缓存的 method map 解析 id，否则 ComponentName 拆包名。
     */
    public static synchronized boolean refreshFromSettings() {
        try {
            Context context = ActivityManagerService.getContext();
            if (context == null)
                return false;

            String id = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            if (id == null || id.isEmpty())
                return false;

            int userId = ActivityManagerService.getCurrentOrTargetUserId();
            if (userId < 0)
                return false;

            InputMethodInfo info = null;
            Map<String, InputMethodInfo> map = inputMethods;
            if (map != null && !map.isEmpty()) {
                info = map.get(id);
            }

            String packageName;
            if (info != null) {
                packageName = info.getPackageName();
                currentInputMethodInfo = info;
            } else {
                packageName = getPackageNameFromId(id);
            }

            if (packageName == null || packageName.isEmpty())
                return false;

            currentInputMethodPackageName = packageName;
            currentInputMethodUserId = userId;
            currentInputMethodApp = AppService.get(packageName, userId);
            // AppRecord 暂未建好时仍视为已识别到包名，后续 getCurrentInputMethodApp 可懒加载
            return true;
        } catch (Throwable throwable) {
            Log.w("从 Settings 恢复当前输入法失败", throwable);
            return false;
        }
    }

    public static synchronized AppRecord getCurrentInputMethodApp() {
        if (currentInputMethodApp == null
                && currentInputMethodPackageName != null
                && currentInputMethodUserId >= 0) {
            currentInputMethodApp = AppService.get(currentInputMethodPackageName, currentInputMethodUserId);
        }
        return currentInputMethodApp;
    }

    public static synchronized void setCurrentInputMethodApp(AppRecord appRecord, InputMethodInfo info, int userId) {
        currentInputMethodInfo = info;
        currentInputMethodApp = appRecord;
        if (appRecord != null) {
            currentInputMethodPackageName = appRecord.getPackageName();
            currentInputMethodUserId = appRecord.getUserId();
        } else if (info != null) {
            currentInputMethodPackageName = info.getPackageName();
            currentInputMethodUserId = userId;
        }
    }

    public static boolean isCurrentInputMethod(AppRecord appRecord) {
        if (appRecord == null) {
            return false;
        }

        AppRecord current = getCurrentInputMethodApp();
        if (current != null) {
            return appRecord.equals(current);
        }

        // 兜底：仅有 package/user、AppRecord 尚未建好
        return currentInputMethodPackageName != null
                && currentInputMethodUserId >= 0
                && currentInputMethodPackageName.equals(appRecord.getPackageName())
                && currentInputMethodUserId == appRecord.getUserId();
    }

    private static String getPackageNameFromId(String id) {
        ComponentName componentName = ComponentName.unflattenFromString(id);
        if (componentName != null)
            return componentName.getPackageName();

        int slash = id.indexOf('/');
        if (slash <= 0)
            return id;
        return id.substring(0, slash);
    }
}
