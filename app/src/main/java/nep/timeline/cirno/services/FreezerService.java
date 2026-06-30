package nep.timeline.cirno.services;

import java.util.List;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.configs.policy.FreezeExemption;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.hooks.android.xiaomi.XiaomiHooks;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.ForceAppStandbyListener;
import nep.timeline.cirno.utils.FreezeExemptionChecker;
import nep.timeline.cirno.utils.FrozenRW;
import nep.timeline.cirno.utils.ProcUtils;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class FreezerService {
    private static final long THAW_VERIFY_DELAY_MS = 75L;
    private static final int MAX_THAW_RETRY_COUNT = 3;
    private static final String WCHAN_V2_FROZEN = "do_freezer_trap";

    public static synchronized void freezer(AppRecord appRecord) {
        appRecord.nextThawSeq();

        FreezeExemption exemption = FreezeExemptionChecker.check(appRecord);
        if (exemption != null) {
            Log.d("跳过冻结 app=" + appRecord.getPackageNameWithUser() + " reason=" + exemption.reason);
            return;
        }

        boolean hasFrozenProcess = false;
        for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
            if (processRecord.isDeathProcess())
                continue;

            if (processRecord.isFrozen()) {
                hasFrozenProcess = true;
                continue;
            }

            if (AppConfigs.isProcessExcludedFromFreeze(appRecord.getPackageName(), appRecord.getUserId(), processRecord.getProcessName())) {
                continue;
            }

            if (FrozenRW.frozen(processRecord.getRunningUid(), processRecord.getPid())) {
                processRecord.setFrozen(true);
                hasFrozenProcess = true;
            }
        }

        if (!hasFrozenProcess) {
            appRecord.setFrozen(false);
            return;
        }

        Handlers.alarms.post(() -> {
            try {
                ForceAppStandbyListener.removeAlarmsForUid(appRecord);
            } catch (Exception e) {
                Log.e("移除警报失败", e);
            }
        });

        Handlers.network.post(() -> NetworkManagementService.socketDestroy(appRecord));

        appRecord.setFrozen(true);

        if (GlobalVars.globalSettings != null && GlobalVars.globalSettings.compactionEnabled) {
            CompactionService.scheduleCompaction(appRecord, GlobalVars.globalSettings.compactionDelay * 1000L);
        }

        if (AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId())) {
            if (XiaomiHooks.isAvailable())
                GreezeManagerServiceWrapper.monitorNet(appRecord.getUid());
        }
    }

    public static synchronized void thaw(AppRecord appRecord) {
        FreezerHandler.removeAppMessage(appRecord);
        CompactionService.cancelCompaction(appRecord);

        if (!appRecord.isFrozen())
            return;

        if (AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId())) {
            if (XiaomiHooks.isAvailable())
                GreezeManagerServiceWrapper.clearMonitorNet(appRecord.getUid());
        }

        int thawSeq = appRecord.nextThawSeq();

        for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
            if (processRecord.isDeathProcess() || !processRecord.isFrozen())
                continue;

            if (FrozenRW.thaw(processRecord.getRunningUid(), processRecord.getPid())) {
                processRecord.setFrozen(false);
                processRecord.setLastCompactTime(0);
            }
        }

        appRecord.setFrozen(hasFrozenProcess(appRecord));
        FreezerHandler.handler.postDelayed(() -> verifyThawAndRetry(appRecord, thawSeq, 0), THAW_VERIFY_DELAY_MS);
    }

    public static void temporaryUnfreezeIfNeed(int uid, String reason, long interval) {
        List<AppRecord> appRecords = AppService.getByUid(uid);
        if (appRecords.isEmpty())
            return;

        for (AppRecord appRecord : appRecords) {
            if (appRecord == null)
                continue;

            temporaryUnfreezeIfNeed(appRecord, reason, interval);
        }
    }

    public static void temporaryUnfreezeIfNeed(String packageName, int userId, String reason, long interval) {
        temporaryUnfreezeIfNeed(AppService.get(packageName, userId), reason, interval);
    }

    public static void temporaryUnfreezeIfNeed(AppRecord appRecord, String reason, long interval) {
        if (appRecord == null)
            return;

        boolean blacklisted = AppConfigs.isBlackApp(appRecord.getPackageName(), appRecord.getUserId());
        if (!blacklisted && appRecord.isSystem())
            return;

        if (appRecord.isFrozen())
            Log.i(appRecord.getPackageNameWithUser() + " " + reason);

        thaw(appRecord);
        if (appRecord.isWaitingNotification()) {
            Thread waitingNotification = new Thread(new Runnable() {
                @Override
                public void run() {
                    long startTime = System.currentTimeMillis();
                    while(!Thread.currentThread().isInterrupted()) {
                        if (System.currentTimeMillis() - startTime > interval) {
                            appRecord.setWaitingNotification(false);
                            Log.d(appRecord.getPackageName() + " 等待消息通知超时");
                        }
                        try {
                            if(!appRecord.isWaitingNotification()) {
                                break;
                            }
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    FreezerHandler.sendFreezeMessageIgnoreMessages(appRecord);
                }
            });
            waitingNotification.start();
        } else {
            FreezerHandler.sendTemporaryFreezeMessage(appRecord, interval);
        }
    }

    private static boolean hasFrozenProcess(AppRecord appRecord) {
        for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
            if (processRecord != null && !processRecord.isDeathProcess() && processRecord.isFrozen()) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void verifyThawAndRetry(AppRecord appRecord, int thawSeq, int retryCount) {
        if (appRecord.getThawSeq() != thawSeq)
            return;

        boolean retried = false;
        boolean retryExhausted = retryCount >= MAX_THAW_RETRY_COUNT;

        for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
            if (processRecord == null || processRecord.isDeathProcess())
                continue;

            int pid = processRecord.getPid();
            String wchan = ProcUtils.readWchan(pid);
            if (!WCHAN_V2_FROZEN.equals(wchan))
                continue;

            if (retryExhausted) {
                Log.w(appRecord.getPackageNameWithUser() + " PID=" + pid + " 解冻重试" + MAX_THAW_RETRY_COUNT + "次后仍处于" + wchan);
                processRecord.setFrozen(true);
                continue;
            }

            if (FrozenRW.thaw(processRecord.getRunningUid(), pid)) {
                processRecord.setFrozen(false);
            }
            retried = true;
        }

        boolean frozen = hasFrozenProcess(appRecord);
        Log.d(appRecord.getPackageNameWithUser() + " retry=" + retryCount + " stillFrozen=" + frozen);
        appRecord.setFrozen(frozen);

        if (retried) {
            int nextRetryCount = retryCount + 1;
            FreezerHandler.handler.postDelayed(() -> verifyThawAndRetry(appRecord, thawSeq, nextRetryCount), THAW_VERIFY_DELAY_MS);
        }
    }
}
