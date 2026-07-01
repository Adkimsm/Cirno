package nep.timeline.cirno.virtuals;

import android.content.pm.ApplicationInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.services.AppService;

public class ProcessRecord {
    private final Object instance;
    private final int userId;
    private final int runningUid;
    private final ApplicationInfo applicationInfo;
    private final int uid;
    private final String packageName;
    private final String processName;
    private AppRecord appRecord;
    private volatile boolean frozen;
    private volatile long lastCompactTime;
    private volatile long lastTrimMemoryTime;
    private volatile long cachedRssKb = 0L;
    private volatile float cachedCpuUsage = 0.0f;
    private long lastProcessCpuTime = 0L;
    private long lastTotalCpuTime = 0L;

    public ProcessRecord(Object instance) {
        this.instance = instance;
        this.userId = CakeReflection.getIntField(instance, "userId");
        this.runningUid = CakeReflection.getIntField(instance, "uid");
        this.applicationInfo = (ApplicationInfo) CakeReflection.getObjectField(instance, "info");
        this.uid = applicationInfo.uid;
        this.packageName = applicationInfo.packageName;
        this.processName = (String) CakeReflection.getObjectField(instance, "processName");
        this.appRecord = AppService.get(packageName, userId);
    }

    public int getPid() {
        return (int) CakeReflection.getObjectField(instance, "mPid");
    }

    public boolean isDeathProcess() {
        return getPid() <= 0;
    }

    public AppRecord getAppRecord() {
        if (appRecord == null)
            appRecord = AppService.get(packageName, userId);
        return appRecord;
    }

    public int getUserId() {
        return userId;
    }

    public int getRunningUid() {
        return runningUid;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getProcessName() {
        return processName;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public Object getSystemInstance() {
        return instance;
    }

    public boolean isCompacted() {
        return lastCompactTime > 0;
    }

    public long getLastCompactTime() {
        return lastCompactTime;
    }

    public void setLastCompactTime(long time) {
        this.lastCompactTime = time;
    }

    public long getLastTrimMemoryTime() {
        return lastTrimMemoryTime;
    }

    public void setLastTrimMemoryTime(long time) {
        this.lastTrimMemoryTime = time;
    }

    public boolean scheduleTrimMemory(int level) {
        if (isDeathProcess()) return false;
        try {
            Object thread = CakeReflection.getObjectField(instance, "thread");
            if (thread == null) return false;
            CakeReflection.callMethod(thread, "scheduleTrimMemory", level);
            return true;
        } catch (Throwable e) {
            Log.d("ProcessRecord: scheduleTrimMemory failed for "
                    + processName + ": " + e.getMessage());
            return false;
        }
    }

    public long getCachedRssKb() {
        return cachedRssKb;
    }

    public void updateCachedRss() {
        if (isDeathProcess()) {
            cachedRssKb = 0L;
            return;
        }
        Long rss = readRssFromStatusFile("/proc/" + getPid() + "/status");
        cachedRssKb = rss == null ? 0L : rss;
    }

    public float getCachedCpuUsage() {
        return cachedCpuUsage;
    }

    public void updateCachedCpuUsage() {
        if (isDeathProcess()) {
            cachedCpuUsage = 0.0f;
            lastProcessCpuTime = 0L;
            lastTotalCpuTime = 0L;
            return;
        }
        int pid = getPid();
        long currentProcessTime = readProcessCpuTime(pid);
        long currentTotalTime = readTotalCpuTime();
        if (currentProcessTime < 0 || currentTotalTime <= 0) {
            cachedCpuUsage = 0.0f;
            return;
        }
        if (lastTotalCpuTime > 0) {
            long processDelta = currentProcessTime - lastProcessCpuTime;
            long totalDelta = currentTotalTime - lastTotalCpuTime;
            if (totalDelta > 0) {
                cachedCpuUsage = (float) processDelta / totalDelta * Runtime.getRuntime().availableProcessors() * 100f;
            }
        }
        lastProcessCpuTime = currentProcessTime;
        lastTotalCpuTime = currentTotalTime;
    }

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
            if (tail.length < 14) return -1;
            long utime = Long.parseLong(tail[11]);
            long stime = Long.parseLong(tail[12]);
            long cutime = Long.parseLong(tail[13]);
            long cstime = Long.parseLong(tail[14]);
            return utime + stime + cutime + cstime;
        } catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
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
        } catch (IOException | NumberFormatException ignored) {
        }
        return -1;
    }

    private static Long readRssFromStatusFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("VmRSS:")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[1]);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return null;
    }
}
