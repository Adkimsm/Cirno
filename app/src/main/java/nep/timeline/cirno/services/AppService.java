package nep.timeline.cirno.services;

import android.content.pm.ApplicationInfo;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.utils.PKGUtils;

public class AppService {
    private static final Map<Integer, Map<String, AppRecord>> APP_RECORD_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, List<AppRecord>> UID_RECORD_MAP = new ConcurrentHashMap<>();

    public static AppRecord get(String packageName, int userId) {
        if (packageName == null || packageName.equals("android"))
            return null;

        Map<String, AppRecord> appRecords = APP_RECORD_MAP.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        return appRecords.computeIfAbsent(packageName, pkg -> {
            ApplicationInfo applicationInfo = ActivityManagerService.getApplicationInfo(pkg, userId);
            if (applicationInfo == null)
                return null;
            return new AppRecord(applicationInfo);
        });
    }

    public static List<AppRecord> getByUid(int uid) {
        try {
            List<AppRecord> records = UID_RECORD_MAP.get(uid);
            if (records != null)
                return records;
            putAppToCacheByUid(uid);
            records = UID_RECORD_MAP.get(uid);
            if (records == null)
                return Collections.emptyList();
            return records;
        } catch (Throwable e) {
            Log.d("AppService getByUid uid=" + uid, e);
        }
        return Collections.emptyList();
    }

    private static synchronized void putAppToCacheByUid(int uid) {
        if (uid <= Process.SYSTEM_UID) {
            UID_RECORD_MAP.put(uid, Collections.emptyList());
            return;
        }

        String[] keys = ActivityManagerService.getPackagesForUid(uid);
        if (keys == null || keys.length == 0) {
            UID_RECORD_MAP.put(uid, Collections.emptyList());
            return;
        }

        List<AppRecord> appRecords = new ArrayList<>();
        for (String key : keys) {
            String[] split = key.split(":");
            int userId = split.length == 1 ? PKGUtils.getUserId(uid) : Integer.parseInt(split[1].trim());
            AppRecord appRecord = get(split[0], userId);
            if (appRecord != null)
                appRecords.add(appRecord);
        }

        UID_RECORD_MAP.put(uid, appRecords);
    }

    public static List<AppRecord> getAllRecordsSnapshot() {
        LinkedHashSet<AppRecord> records = new LinkedHashSet<>();
        for (Map<String, AppRecord> appRecords : APP_RECORD_MAP.values()) {
            if (appRecords == null || appRecords.isEmpty()) {
                continue;
            }
            records.addAll(appRecords.values());
        }
        return List.copyOf(records);
    }

    public static void clearRecords() {
        APP_RECORD_MAP.clear();
        UID_RECORD_MAP.clear();
    }
}
