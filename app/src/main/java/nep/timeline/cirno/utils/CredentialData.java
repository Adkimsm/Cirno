package nep.timeline.cirno.utils;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.services.AppService;

public class CredentialData {
    private static final Map<String, AppRecord> activeSessions = new HashMap<>();

    public static String makeSessionKey(int userId, Object providerSession) {
        return userId + ":" + System.identityHashCode(providerSession);
    }

    public static synchronized boolean putSession(int userId, Object providerSession, AppRecord appRecord) {
        return activeSessions.put(makeSessionKey(userId, providerSession), appRecord) == null;
    }

    public static synchronized AppRecord removeSession(int userId, Object providerSession) {
        return activeSessions.remove(makeSessionKey(userId, providerSession));
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
        if (!(savedState instanceof Map<?, ?> state))
            return;

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
