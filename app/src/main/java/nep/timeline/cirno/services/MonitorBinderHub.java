package nep.timeline.cirno.services;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import nep.timeline.cirno.configs.policy.FreezeExemption;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.provide.ApplicationBinderFacade;
import nep.timeline.cirno.provide.FrozenStateBinderFacade;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.FreezeExemptionChecker;
import nep.timeline.cirno.virtuals.ProcessRecord;

public final class MonitorBinderHub {
    private static final String REASON_UNKNOWN = "UNKNOWN";
    private static volatile long lastPublishedAtMs = 0L;
    private static volatile boolean bootCompleted = false;
    private static volatile boolean loggedSkippedBoot = false;
    private static volatile boolean loggedSkippedAms = false;
    private static volatile boolean loggedSocketStarted = false;
    private static final java.util.concurrent.ConcurrentHashMap<String, List<String>> PROCESS_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int PROCESS_NAME_CACHE_MAX_SIZE = 256;
    
    // System snapshot for running apps
    private static volatile SystemRunningSnapshot systemSnapshot = null;
    private static volatile long lastFullScanMs = 0L;
    private static final long FULL_SCAN_INTERVAL_MS = 10000L;
    private static final Object snapshotLock = new Object();

    private MonitorBinderHub() {
    }

    public static void setBootCompleted() {
        bootCompleted = true;
        scheduleRebroadcast();
    }

    public static boolean isBootCompleted() {
        return bootCompleted;
    }

    public static void restoreBootCompleted(boolean value) {
        bootCompleted = value;
    }

    public static void stopForHotReload() {
        Handlers.rekernel.removeCallbacksAndMessages(null);
        loggedSocketStarted = false;
    }

    public static void refreshForHotReload() {
        synchronized (snapshotLock) {
            systemSnapshot = buildFullSystemSnapshot();
        }
        publish("hot reload");
    }

    // Inner classes for system snapshot
    private static final class SystemRunningSnapshot {
        final List<String> runningApps;
        final Map<String, List<SystemProcessInfo>> appProcesses;
        final Map<Integer, SystemProcessInfo> pidMap;

        SystemRunningSnapshot(List<String> runningApps, Map<String, List<SystemProcessInfo>> appProcesses, Map<Integer, SystemProcessInfo> pidMap) {
            this.runningApps = runningApps;
            this.appProcesses = appProcesses;
            this.pidMap = pidMap;
        }
    }

    private static final class SystemProcessInfo {
        final int pid;
        final int uid;
        final String processName;
        long lastCpuTime;
        long lastTotalTime;
        float cachedCpuUsage;

        SystemProcessInfo(int pid, int uid, String processName) {
            this.pid = pid;
            this.uid = uid;
            this.processName = processName;
            this.lastCpuTime = 0L;
            this.lastTotalTime = 0L;
            this.cachedCpuUsage = 0f;
        }

        void updateCpuUsage(long currentTotalTime) {
            long currentProcessTime = readProcessCpuTime(pid);
            if (currentProcessTime < 0 || currentTotalTime <= 0) {
                cachedCpuUsage = 0f;
                return;
            }
            if (lastTotalTime > 0) {
                long processDelta = currentProcessTime - lastCpuTime;
                long totalDelta = currentTotalTime - lastTotalTime;
                if (totalDelta > 0) {
                    cachedCpuUsage = (float) processDelta / totalDelta * Runtime.getRuntime().availableProcessors() * 100f;
                }
            }
            lastCpuTime = currentProcessTime;
            lastTotalTime = currentTotalTime;
        }
    }

    // Build full snapshot from system
    private static SystemRunningSnapshot buildFullSystemSnapshot() {
        try {
            Object mPidsSelfLocked = ActivityManagerService.getPidsSelfLocked();
            if (mPidsSelfLocked == null) {
                Log.w("buildFullSystemSnapshot: mPidsSelfLocked is null");
                return new SystemRunningSnapshot(new ArrayList<>(), new HashMap<>(), new HashMap<>());
            }

            Map<String, Integer> appUidMap = new HashMap<>();
            Map<String, List<SystemProcessInfo>> appProcessesMap = new HashMap<>();
            Map<Integer, SystemProcessInfo> pidMap = new HashMap<>();

            synchronized (mPidsSelfLocked) {
                int size = (int) CakeReflection.callMethod(mPidsSelfLocked, "size");
                for (int i = 0; i < size; i++) {
                    Object systemProcessRecord = CakeReflection.callMethod(mPidsSelfLocked, "valueAt", i);
                    if (systemProcessRecord == null) continue;

                    int pid = CakeReflection.getIntField(systemProcessRecord, "mPid");
                    if (pid <= 0) continue;

                    Object info = CakeReflection.getObjectField(systemProcessRecord, "info");
                    if (info == null) continue;

                    ApplicationInfo appInfo = (ApplicationInfo) info;
                    String packageName = appInfo.packageName;
                    if (packageName == null || packageName.isEmpty() || "android".equals(packageName)) {
                        continue;
                    }

                    int userId = CakeReflection.getIntField(systemProcessRecord, "userId");
                    int uid = CakeReflection.getIntField(systemProcessRecord, "uid");
                    String processName = (String) CakeReflection.getObjectField(systemProcessRecord, "processName");

                    String key = packageName + ":" + userId;
                    appUidMap.put(key, uid);

                    SystemProcessInfo processInfo = new SystemProcessInfo(pid, uid, processName);
                    appProcessesMap.computeIfAbsent(key, k -> new ArrayList<>()).add(processInfo);
                    pidMap.put(pid, processInfo);
                }
            }

            List<String> runningApps = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : appUidMap.entrySet()) {
                runningApps.add(entry.getKey() + ":" + entry.getValue());
            }

            lastFullScanMs = SystemClock.uptimeMillis();
            return new SystemRunningSnapshot(runningApps, appProcessesMap, pidMap);
        } catch (Throwable e) {
            Log.w("buildFullSystemSnapshot failed", e);
            return new SystemRunningSnapshot(new ArrayList<>(), new HashMap<>(), new HashMap<>());
        }
    }

    // Incremental add process
    public static void onProcessAdded(Object systemProcessRecord) {
        if (systemProcessRecord == null) return;

        synchronized (snapshotLock) {
            SystemRunningSnapshot snapshot = systemSnapshot;
            if (snapshot == null) {
                return;
            }

            try {
                int pid = CakeReflection.getIntField(systemProcessRecord, "mPid");
                if (pid <= 0) return;

                Object info = CakeReflection.getObjectField(systemProcessRecord, "info");
                if (info == null) return;

                ApplicationInfo appInfo = (ApplicationInfo) info;
                String packageName = appInfo.packageName;
                if (packageName == null || packageName.isEmpty() || "android".equals(packageName)) {
                    return;
                }

                int userId = CakeReflection.getIntField(systemProcessRecord, "userId");
                int uid = CakeReflection.getIntField(systemProcessRecord, "uid");
                String processName = (String) CakeReflection.getObjectField(systemProcessRecord, "processName");

                String key = packageName + ":" + userId;
                String appKey = key + ":" + uid;

                SystemProcessInfo processInfo = new SystemProcessInfo(pid, uid, processName);
                List<SystemProcessInfo> processes = snapshot.appProcesses.get(key);
                if (processes == null) {
                    processes = new ArrayList<>();
                    snapshot.appProcesses.put(key, processes);
                    snapshot.runningApps.add(appKey);
                }
                processes.add(processInfo);
                snapshot.pidMap.put(pid, processInfo);

            } catch (Throwable e) {
                Log.w("onProcessAdded failed", e);
            }
        }
    }

    // Incremental remove process
    public static void onProcessRemoved(int pid) {
        synchronized (snapshotLock) {
            SystemRunningSnapshot snapshot = systemSnapshot;
            if (snapshot == null) return;

            try {
                SystemProcessInfo removed = snapshot.pidMap.remove(pid);
                if (removed == null) {
                    return;
                }

                String emptyAppKey = null;
                for (Map.Entry<String, List<SystemProcessInfo>> entry : snapshot.appProcesses.entrySet()) {
                    List<SystemProcessInfo> processes = entry.getValue();
                    if (processes.remove(removed)) {
                        if (processes.isEmpty()) {
                            emptyAppKey = entry.getKey();
                        }
                        break;
                    }
                }
                if (emptyAppKey != null) {
                    String keyToRemove = emptyAppKey;
                    snapshot.appProcesses.remove(emptyAppKey);
                    snapshot.runningApps.removeIf(app -> app.startsWith(keyToRemove + ":"));
                }
            } catch (Throwable e) {
                Log.w("onProcessRemoved failed", e);
            }
        }
    }

    // Get or update system snapshot
    private static SystemRunningSnapshot getOrUpdateSystemSnapshot() {
        SystemRunningSnapshot snapshot = systemSnapshot;
        long now = SystemClock.uptimeMillis();

        boolean needFullScan = snapshot == null || (now - lastFullScanMs) > FULL_SCAN_INTERVAL_MS;

        if (needFullScan) {
            synchronized (snapshotLock) {
                snapshot = systemSnapshot;
                if (snapshot == null || (now - lastFullScanMs) > FULL_SCAN_INTERVAL_MS) {
                    snapshot = buildFullSystemSnapshot();
                    systemSnapshot = snapshot;
                }
            }
        }

        return snapshot;
    }

    // Get frozen state for system app (not managed by cirno)
    private static String getSystemAppFrozenState(String packageName, int userId, long totalCpuTime) {
        SystemRunningSnapshot snapshot = systemSnapshot;
        if (snapshot == null) {
            return "NOT_FROZEN[UNKNOWN]";
        }

        String key = packageName + ":" + userId;
        List<SystemProcessInfo> processes;
        synchronized (snapshotLock) {
            processes = snapshot.appProcesses.get(key);
            if (processes == null || processes.isEmpty()) {
                return "NOT_FROZEN[UNKNOWN]";
            }
            processes = new ArrayList<>(processes);
        }

        int processCount = processes.size();
        long rss = 0L;
        float cpuUsage = 0f;

        for (SystemProcessInfo proc : processes) {
            rss += readProcessRssKb(proc.pid);
            proc.updateCpuUsage(totalCpuTime);
            cpuUsage += proc.cachedCpuUsage;
        }

        String cpuString = String.format(java.util.Locale.ROOT, "%.2f", cpuUsage);
        return "NOT_FROZEN[NOT_MANAGED],PROCESS_COUNT[" + processCount + "],FROZEN_COUNT[0],RSS[" + rss + "],CPU[" + cpuString + "]";
    }

    // Utility methods for reading process info
    private static long readProcessCpuTime(int pid) {
        if (pid <= 0) return -1;
        String path = "/proc/" + pid + "/stat";
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line == null) return -1;
            int rp = line.lastIndexOf(')');
            if (rp < 0 || rp + 3 >= line.length()) return -1;
            String[] tail = line.substring(rp + 2).split("\\s+");
            if (tail.length < 15) return -1;
            long utime = Long.parseLong(tail[11]);
            long stime = Long.parseLong(tail[12]);
            long cutime = Long.parseLong(tail[13]);
            long cstime = Long.parseLong(tail[14]);
            return utime + stime + cutime + cstime;
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static long readTotalCpuTime() {
        File file = new File("/proc/stat");
        if (!file.exists() || !file.canRead()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return -1;
            String[] parts = line.split("\\s+");
            long sum = 0;
            for (int i = 1; i < parts.length; i++) {
                try {
                    sum += Long.parseLong(parts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
            return sum;
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static long readProcessRssKb(int pid) {
        if (pid <= 0) return 0L;
        try {
            File file = new File("/proc/" + pid + "/status");
            if (!file.exists() || !file.canRead()) return 0L;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("VmRSS:")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            return Long.parseLong(parts[1]);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static void trimProcessNameCacheIfNeeded() {
        int size = PROCESS_NAME_CACHE.size();
        if (size < PROCESS_NAME_CACHE_MAX_SIZE) {
            return;
        }
        int removeCount = size - (PROCESS_NAME_CACHE_MAX_SIZE / 2);
        for (String key : PROCESS_NAME_CACHE.keySet()) {
            PROCESS_NAME_CACHE.remove(key);
            if (--removeCount <= 0) {
                break;
            }
        }
    }

    static final ApplicationBinderFacade applicationBinder = new ApplicationBinderFacade() {
        @Override
        public List<String> getRunningApplication() {
            SystemRunningSnapshot snapshot = getOrUpdateSystemSnapshot();
            synchronized (snapshotLock) {
                return new ArrayList<>(snapshot.runningApps);
            }
        }

        @Override
        public String getProcessesForApp(String packageName, int userId) {
            if (packageName == null || packageName.isEmpty()) {
                return "[]";
            }
            String cacheKey = packageName + "#" + userId;
            List<String> cached = PROCESS_NAME_CACHE.get(cacheKey);
            if (cached != null) {
                return new Gson().toJson(cached);
            }
            LinkedHashSet<String> processNames = new LinkedHashSet<>();
            try {
                android.content.Context context = ActivityManagerService.getContext();
                if (context != null) {
                    PackageManager pm = context.getPackageManager();
                    if (pm != null) {
                        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS;
                        PackageInfo pkgInfo = pm.getPackageInfo(packageName, flags);
                        if (pkgInfo.activities != null) {
                            for (ActivityInfo info : pkgInfo.activities) {
                                String name = info.processName;
                                if (name != null && !name.isEmpty()) {
                                    processNames.add(name);
                                }
                            }
                        }
                        if (pkgInfo.services != null) {
                            for (ServiceInfo info : pkgInfo.services) {
                                String name = info.processName;
                                if (name != null && !name.isEmpty()) {
                                    processNames.add(name);
                                }
                            }
                        }
                        if (pkgInfo.receivers != null) {
                            for (ActivityInfo info : pkgInfo.receivers) {
                                String name = info.processName;
                                if (name != null && !name.isEmpty()) {
                                    processNames.add(name);
                                }
                            }
                        }
                        if (pkgInfo.providers != null) {
                            for (ProviderInfo info : pkgInfo.providers) {
                                String name = info.processName;
                                if (name != null && !name.isEmpty()) {
                                    processNames.add(name);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                Log.w("MonitorBinder getProcessesForApp failed pkg=" + packageName + " userId=" + userId, e);
            }
            if (processNames.isEmpty()) {
                processNames.add(packageName);
            }
            List<String> result = new ArrayList<>(processNames);
            trimProcessNameCacheIfNeeded();
            PROCESS_NAME_CACHE.put(cacheKey, result);
            return new Gson().toJson(result);
        }

        @Override
        public String getNetworkSpeed(String packageName, int userId) {
            if (packageName == null || packageName.isEmpty()) {
                return "{\"rx\":0,\"tx\":0}";
            }
            AppRecord appRecord = AppService.get(packageName, userId);
            if (appRecord == null) {
                return "{\"rx\":0,\"tx\":0}";
            }
            long[] speed = NetworkSpeedMonitor.getSpeed(appRecord.getUid());
            return "{\"rx\":" + speed[0] + ",\"tx\":" + speed[1] + "}";
        }
    };

    static final FrozenStateBinderFacade frozenStateBinder = new FrozenStateBinderFacade() {
        @Override
        public String isFrozen(String packageName, int userId) {
            return getFrozenState(packageName, userId, readTotalCpuTime());
        }

        private String getFrozenState(String packageName, int userId, long totalCpuTime) {
            if (packageName == null || packageName.isEmpty()) {
                return "NOT_FROZEN[UNKNOWN]";
            }
            AppRecord appRecord = AppService.get(packageName, userId);
            if (appRecord == null) {
                return getSystemAppFrozenState(packageName, userId, totalCpuTime);
            }
            int processCount = 0;
            int frozenCount = 0;
            int compactedCount = 0;
            long rss = 0L;
            float cpuUsage = 0f;
            for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
                if (processRecord == null || processRecord.isDeathProcess()) {
                    continue;
                }
                processCount++;
                if (processRecord.isFrozen()) {
                    frozenCount++;
                    if (processRecord.isCompacted()) {
                        compactedCount++;
                    }
                }
                processRecord.updateCachedRss();
                rss += processRecord.getCachedRssKb();
                processRecord.updateCachedCpuUsage(totalCpuTime);
                cpuUsage += processRecord.getCachedCpuUsage();
            }
            if (processCount <= 0) {
                return "NOT_FROZEN[UNKNOWN]";
            }
            String cpuString = String.format(java.util.Locale.ROOT, "%.2f", cpuUsage);
            if (frozenCount > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append("V2(").append(frozenCount).append("/").append(processCount).append(")");
                sb.append(",RSS[").append(rss).append("]");
                sb.append(",CPU[").append(cpuString).append("]");
                if (compactedCount > 0) {
                    sb.append(",COMPACTED[").append(compactedCount).append("/").append(frozenCount).append("]");
                }
                return sb.toString();
            }
            FreezeExemption exemption = FreezeExemptionChecker.check(appRecord);
            String reason;
            if (exemption != null) {
                reason = exemption.reason;
            } else if (frozenCount < processCount) {
                reason = "WAITING_FROZEN";
            } else {
                reason = REASON_UNKNOWN;
            }
            return "NOT_FROZEN[" + reason + "],PROCESS_COUNT[" + processCount + "],FROZEN_COUNT[" + frozenCount + "],RSS[" + rss + "],CPU[" + cpuString + "]";
        }

        @Override
        public List<String> getFrozenStates(List<String> apps) {
            List<String> result = new ArrayList<>();
            if (apps == null) {
                return result;
            }
            long totalCpuTime = readTotalCpuTime();
            java.util.HashMap<String, String> localCache = new java.util.HashMap<>();
            for (String entry : apps) {
                if (entry == null || entry.isEmpty()) {
                    result.add("");
                    continue;
                }
                String[] parts = entry.split(":");
                if (parts.length < 2) {
                    result.add("");
                    continue;
                }
                String packageName = parts[0];
                int userId;
                try {
                    userId = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    result.add("");
                    continue;
                }
                String cacheKey = packageName + "#" + userId;
                String frozenState = localCache.get(cacheKey);
                if (frozenState == null) {
                    frozenState = getFrozenState(packageName, userId, totalCpuTime);
                    localCache.put(cacheKey, frozenState);
                }
                result.add(frozenState);
            }
            return result;
        }
    };

    public static void publish() {
        publish("unspecified");
    }

    public static void publish(String reason) {
        try {
            if (!bootCompleted) {
                if (!loggedSkippedBoot) {
                    Log.d("MonitorBinderHub: publish skipped - boot not completed");
                    loggedSkippedBoot = true;
                }
                return;
            }
            SocketServer.start();
            if (!loggedSocketStarted) {
                Log.d("MonitorBinderHub: socket server started, reason=" + reason);
                loggedSocketStarted = true;
            }
        } catch (Throwable e) {
            Log.w("MonitorBinderHub publish failed", e);
        }
    }

    private static void scheduleRebroadcast() {
        Handlers.rekernel.postDelayed(() -> {
            if (bootCompleted) {
                publish("boot rebroadcast");
            }
        }, 5000L);
    }

}
