package nep.timeline.cirno.entity;

import android.content.pm.ApplicationInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import nep.timeline.cirno.CommonConstants;
import nep.timeline.cirno.configs.checkers.AppConfigs;
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
    private volatile boolean waitingNotification = false;
    private volatile int thawSeq = 0;

    public AppRecord(ApplicationInfo applicationInfo) {
        this.packageName = applicationInfo.packageName;
        this.userId = PKGUtils.getUserId(applicationInfo.uid);
        this.uid = applicationInfo.uid;
        this.applicationInfo = applicationInfo;
        this.appState = new AppState(this);
    }

    public boolean isSystem() {
        return packageName == null || equals(InputMethodData.currentInputMethodApp) || AutofillData.hasActiveSession(this) || CredentialData.hasActiveSession(this) || PKGUtils.isSystemApp(applicationInfo) || CommonConstants.isWhitelistApps(packageName);
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

    public int getThawSeq() {
        return thawSeq;
    }

    private volatile Thread waitingNotificationThread;

    public boolean isWaitingNotification() { return waitingNotification; }

    public void setWaitingNotification(boolean waitingNotification) { this.waitingNotification = waitingNotification; }

    public void setWaitingNotificationThread(Thread thread) {
        if (this.waitingNotificationThread != null) {
            this.waitingNotificationThread.interrupt();
        }
        this.waitingNotificationThread = thread;
    }

    public void clearWaitingNotificationThread() {
        if (this.waitingNotificationThread != null) {
            this.waitingNotificationThread.interrupt();
            this.waitingNotificationThread = null;
        }
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
