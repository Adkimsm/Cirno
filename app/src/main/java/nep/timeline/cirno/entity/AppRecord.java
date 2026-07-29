package nep.timeline.cirno.entity;

import android.content.pm.ApplicationInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import nep.timeline.cirno.CommonConstants;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.AutofillData;
import nep.timeline.cirno.utils.CredentialData;
import nep.timeline.cirno.utils.InputMethodData;
import nep.timeline.cirno.utils.PKGUtils;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class AppRecord {
    private final String packageName;
    private final int userId;
    private final int uid;
    private final ApplicationInfo applicationInfo;
    private final List<ProcessRecord> processRecords = new CopyOnWriteArrayList<>();
    private volatile AppState appState;
    private volatile boolean frozen;
    private volatile Runnable waitingNotificationRunnable;
    private volatile int thawSeq = 0;
    private volatile long lastThawTime = 0L;

    public AppRecord(ApplicationInfo applicationInfo) {
        this.packageName = applicationInfo.packageName;
        this.userId = PKGUtils.getUserId(applicationInfo.uid);
        this.uid = applicationInfo.uid;
        this.applicationInfo = applicationInfo;
        this.appState = new AppState(this);
    }

    public boolean isSystem() {
        return packageName == null || InputMethodData.isCurrentInputMethod(this) || AutofillData.hasActiveSession(this) || CredentialData.hasActiveSession(this) || PKGUtils.isSystemApp(applicationInfo) || CommonConstants.isWhitelistApps(packageName);
    }

    public String getPackageName() {
        return packageName;
    }

    public int getUserId() {
        return userId;
    }

    public int getUid() {
        return uid;
    }

    public ApplicationInfo getApplicationInfo() {
        return applicationInfo;
    }

    public List<ProcessRecord> getProcessRecords() {
        return processRecords;
    }

    public AppState getAppState() {
        return appState;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public synchronized int nextThawSeq() {
        return ++thawSeq;
    }

    public long getLastThawTime() {
        return lastThawTime;
    }

    public void markThawed() {
        this.lastThawTime = System.currentTimeMillis();
    }

    public int getThawSeq() {
        return thawSeq;
    }

    public synchronized void setWaitingNotificationRunnable(Runnable runnable) {
        if (waitingNotificationRunnable != null) {
            Handlers.notification.removeCallbacks(waitingNotificationRunnable);
        }
        waitingNotificationRunnable = runnable;
    }

    public synchronized void clearWaitingNotificationRunnable() {
        if (waitingNotificationRunnable != null) {
            Handlers.notification.removeCallbacks(waitingNotificationRunnable);
            waitingNotificationRunnable = null;
        }
    }

    public synchronized Runnable getWaitingNotificationRunnable() {
        return waitingNotificationRunnable;
    }

    public String getPackageNameWithUser() {
        if (userId == 0)
            return packageName;
        return packageName + ":" + userId;
    }

    public void reset() {
        this.frozen = false;
        nextThawSeq();
        this.appState = new AppState(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj instanceof AppRecord appRecord)
            return getUserId() == appRecord.getUserId() && getPackageName().equals(appRecord.getPackageName());
        return false;
    }

    @Override
    public int hashCode() {
        int userId = getUserId();
        String packageName = getPackageName();
        return ((Integer.hashCode(userId) + 59) * 59) + (packageName == null ? 43 : packageName.hashCode());
    }
}
