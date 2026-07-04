package nep.timeline.cirno.utils;

import android.view.inputmethod.InputMethodInfo;

import java.util.HashMap;
import java.util.Map;

import nep.timeline.cirno.entity.AppRecord;
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

        if (currentInputMethodPackageName != null && currentInputMethodUserId >= 0)
            currentInputMethodApp = AppService.get(currentInputMethodPackageName, currentInputMethodUserId);
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
