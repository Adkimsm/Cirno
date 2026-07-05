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

public class InputMethodData {
    public static volatile Object instance;
    public static Map<String, InputMethodInfo> inputMethods = new HashMap<>();
    public static InputMethodInfo currentInputMethodInfo;
    public static AppRecord currentInputMethodApp;
    public static String currentInputMethodPackageName;
    public static int currentInputMethodUserId = -1;

    public static synchronized Map<String, Object> saveState() {
        HashMap<String, Object> state = new HashMap<>();
        String packageName = currentInputMethodPackageName;
        int userId = currentInputMethodUserId;
        if ((packageName == null || userId < 0) && currentInputMethodApp != null) {
            packageName = currentInputMethodApp.getPackageName();
            userId = currentInputMethodApp.getUserId();
        }
        state.put("instance", instance);
        state.put("inputMethods", inputMethods);
        state.put("currentInputMethodInfo", currentInputMethodInfo);
        state.put("currentInputMethodPackageName", packageName);
        state.put("currentInputMethodUserId", userId);
        return state;
    }

    @SuppressWarnings("unchecked")
    public static synchronized void restoreState(Object savedState) {
        if (!(savedState instanceof Map<?, ?> state))
            return;

        instance = state.get("instance");
        Object methods = state.get("inputMethods");
        inputMethods = methods instanceof Map<?, ?> ? (Map<String, InputMethodInfo>) methods : new HashMap<>();
        Object info = state.get("currentInputMethodInfo");
        currentInputMethodInfo = info instanceof InputMethodInfo ? (InputMethodInfo) info : null;
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

            String packageName = getPackageNameFromId(id);
            if (packageName == null || packageName.isEmpty())
                return false;

            int userId = ActivityManagerService.getCurrentOrTargetUserId();
            currentInputMethodInfo = inputMethods == null ? null : inputMethods.get(id);
            currentInputMethodPackageName = packageName;
            currentInputMethodUserId = userId;
            currentInputMethodApp = AppService.get(packageName, userId);
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

        AppRecord currentApp = currentInputMethodApp;
        if (appRecord.equals(currentApp)) {
            return true;
        }

        return currentInputMethodPackageName != null
                && currentInputMethodUserId >= 0
                && currentInputMethodPackageName.equals(appRecord.getPackageName())
                && currentInputMethodUserId == appRecord.getUserId();
    }
}
