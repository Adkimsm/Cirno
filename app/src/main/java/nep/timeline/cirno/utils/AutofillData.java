package nep.timeline.cirno.utils;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.services.AppService;

public class AutofillData {
    public static volatile Object instance;
    public static volatile AppRecord currentAutofillApp;
    private static final Map<String, AppRecord> activeSessions = new HashMap<>();

    public static String makeSessionKey(int userId, int sessionId) {
        return userId + ":" + sessionId;
    }

    public static synchronized boolean putSession(int userId, int sessionId, AppRecord appRecord) {
        return activeSessions.put(makeSessionKey(userId, sessionId), appRecord) == null;
    }

    public static synchronized AppRecord removeSession(int userId, int sessionId) {
        return activeSessions.remove(makeSessionKey(userId, sessionId));
    }

    public static synchronized boolean hasActiveSession(AppRecord appRecord) {
        return activeSessions.containsValue(appRecord);
    }

    public static synchronized int getActiveSessionCount(AppRecord appRecord) {
        int count = 0;
        for (AppRecord record : activeSessions.values()) {
            if (record.equals(appRecord)) {
                count++;
            }
        }
        return count;
    }

    public static synchronized int getSessionCount() {
        return activeSessions.size();
    }

    public static synchronized Map<String, Object> saveState() {
        HashMap<String, Object> state = new HashMap<>();
        state.put("instance", instance);
        if (currentAutofillApp != null) {
            state.put("currentPackageName", currentAutofillApp.getPackageName());
            state.put("currentUserId", currentAutofillApp.getUserId());
        }

        ArrayList<Map<String, Object>> sessions = new ArrayList<>();
        for (Map.Entry<String, AppRecord> entry : activeSessions.entrySet()) {
            AppRecord appRecord = entry.getValue();
            if (appRecord == null)
                continue;
            HashMap<String, Object> session = new HashMap<>();
            session.put("key", entry.getKey());
            session.put("packageName", appRecord.getPackageName());
            session.put("userId", appRecord.getUserId());
            sessions.add(session);
        }
        state.put("sessions", sessions);
        return state;
    }

    public static synchronized void restoreState(Object savedState) {
        activeSessions.clear();
        currentAutofillApp = null;
        if (!(savedState instanceof Map<?, ?> state))
            return;

        instance = state.get("instance");
        Object currentPackageName = state.get("currentPackageName");
        Object currentUserId = state.get("currentUserId");
        if (currentPackageName instanceof String && currentUserId instanceof Integer)
            currentAutofillApp = AppService.get((String) currentPackageName, (Integer) currentUserId);

        Object sessions = state.get("sessions");
        if (!(sessions instanceof List<?>))
            return;

        for (Object value : (List<?>) sessions) {
            if (!(value instanceof Map<?, ?> session))
                continue;
            Object key = session.get("key");
            Object packageName = session.get("packageName");
            Object userId = session.get("userId");
            if (!(key instanceof String) || !(packageName instanceof String) || !(userId instanceof Integer))
                continue;
            AppRecord appRecord = AppService.get((String) packageName, (Integer) userId);
            if (appRecord != null)
                activeSessions.put((String) key, appRecord);
        }
    }
}
