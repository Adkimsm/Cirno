package nep.timeline.cirno.utils;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import java.util.HashMap;
import java.util.Map;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;

public class InputMethodData {
    private static AppRecord currentInputMethodApp;
    private static String currentInputMethodPackageName;
    private static int currentInputMethodUserId = -1;

    public static synchronized Map<String, Object> saveState() {
        HashMap<String, Object> state = new HashMap<>();
        state.put("currentInputMethodPackageName", currentInputMethodPackageName);
        state.put("currentInputMethodUserId", currentInputMethodUserId);
        return state;
    }

    public static synchronized void restoreState(Object savedState) {
        if (!(savedState instanceof Map<?, ?> state))
            return;

        Object packageName = state.get("currentInputMethodPackageName");
        currentInputMethodPackageName = packageName instanceof String ? (String) packageName : null;
        Object userId = state.get("currentInputMethodUserId");
        currentInputMethodUserId = userId instanceof Integer ? (Integer) userId : -1;
        currentInputMethodApp = null;

        getCurrentInputMethodApp();
    }

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
            return setCurrentInputMethod(id, userId) != null;
        } catch (Throwable throwable) {
            Log.w("从 Settings 恢复当前输入法失败", throwable);
            return false;
        }
    }

    public static synchronized AppRecord setCurrentInputMethod(String id, int userId) {
        String packageName = getPackageNameFromId(id);
        if (packageName == null || packageName.isEmpty() || userId < 0)
            return null;

        currentInputMethodPackageName = packageName;
        currentInputMethodUserId = userId;
        currentInputMethodApp = AppService.get(packageName, userId);
        return currentInputMethodApp;
    }

    public static synchronized AppRecord getCurrentInputMethodApp() {
        if (currentInputMethodApp == null
                && currentInputMethodPackageName != null
                && currentInputMethodUserId >= 0) {
            currentInputMethodApp = AppService.get(currentInputMethodPackageName, currentInputMethodUserId);
        }
        return currentInputMethodApp;
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

    public static boolean isCurrentInputMethod(AppRecord appRecord) {
        if (appRecord == null) {
            return false;
        }

        return currentInputMethodPackageName != null
                && currentInputMethodUserId >= 0
                && currentInputMethodPackageName.equals(appRecord.getPackageName())
                && currentInputMethodUserId == appRecord.getUserId();
    }
}
