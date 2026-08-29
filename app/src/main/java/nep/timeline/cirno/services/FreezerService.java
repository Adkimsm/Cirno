package nep.timeline.cirno.services;

import android.system.Os;
import android.system.OsConstants;

import java.util.List;
import java.util.Objects;

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
import nep.timeline.cirno.virtuals.ProcessRecord;

public class FreezerService {
    // 瞬态豁免（网速活跃）没有可靠的事件源保证之后重新触发冻结，定期重试兜底
    private static final long TRANSIENT_EXEMPTION_RECHECK_MS = 10_000L;
    // 冻结前内存修剪后，留给目标进程处理 trim/GC 的时间窗口
    private static final long PRE_FREEZE_TRIM_SETTLE_MS = 3_000L;

    /**
     * 冻结与解冻改为按 AppRecord 加锁：
     * 不同应用之间的冻结/解冻不再互相串行（旧实现的全局 static synchronized
     * 会让前台切换应用的 thaw 等待其它应用完整的 freezer 流程，造成可感知卡顿）。
     */
    public static void freezer(AppRecord appRecord) {
        if (appRecord == null)
            return;

        synchronized (appRecord) {
            FreezeExemption exemption = FreezeExemptionChecker.check(appRecord);
            if (exemption != null) {
                if (exemption == FreezeExemption.NETWORK_SPEED)
                    FreezerHandler.sendTemporaryFreezeMessage(appRecord, TRANSIENT_EXEMPTION_RECHECK_MS);
                return;
            }

            // 冻结前修剪内存：进程只有在运行时才能真正处理 trim/GC。
            // （冻结后再发 oneway binder 只会积压在目标进程的 binder 缓冲区，
            // 解冻瞬间才集中执行，还可能触发 FREE_BUFFER_FULL）
            if (MemoryTrimService.preFreezeTrimIfNeeded(appRecord)) {
                FreezerHandler.sendTemporaryFreezeMessage(appRecord, PRE_FREEZE_TRIM_SETTLE_MS);
                return;
            }

            appRecord.nextThawSeq();

            boolean hasFrozenProcess = false;
            for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
                if (processRecord.isDeathProcess())
                    continue;

                int behavior = AppConfigs.getProcessBehavior(appRecord.getPackageName(), appRecord.getUserId(), processRecord.getProcessName());
                if (behavior == AppConfigs.PROCESS_BEHAVIOR_KILL) {
                    int pid = processRecord.getPid();
                    try {
                        Os.kill(pid, OsConstants.SIGKILL);
                    } catch (Throwable e) {
                        Log.w("杀死进程失败 pid=" + pid, e);
                    } finally {
                        ProcessService.removeProcessRecordWithoutThawIfCurrent(processRecord, "Cirno process kill");
                    }
                    continue;
                }

                if (processRecord.isFrozen()) {
                    hasFrozenProcess = true;
                    continue;
                }

                if (behavior == AppConfigs.PROCESS_BEHAVIOR_NONE) {
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

            boolean networkMessageAllowed = AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId());

            if (!networkMessageAllowed) {
                Handlers.alarms.post(() -> {
                    try {
                        ForceAppStandbyListener.removeAlarmsForUid(appRecord);
                    } catch (Exception e) {
                        Log.e("移除警报失败", e);
                    }
                });
            }

            appRecord.setFrozen(true);

            Handlers.network.post(() -> {
                // 冻结后毫秒级被解冻的场景下，不再销毁已恢复运行应用的 TCP 连接
                if (appRecord.isFrozen())
                    NetworkManagementService.socketDestroy(appRecord);
            });

            if (GlobalVars.globalSettings != null && GlobalVars.globalSettings.compactionEnabled) {
                CompactionService.scheduleCompaction(appRecord, GlobalVars.globalSettings.compactionDelay * 1000L);
            }

            if (networkMessageAllowed) {
                if (XiaomiHooks.isAvailable())
                    GreezeManagerServiceWrapper.monitorNet(appRecord.getUid());
            }
        }
    }

    public static void thaw(AppRecord appRecord) {
        if (appRecord == null)
            return;

        FreezerHandler.removeAppMessage(appRecord);
        CompactionService.cancelCompaction(appRecord);

        synchronized (appRecord) {
            if (!appRecord.isFrozen())
                return;

            if (AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId())) {
                if (XiaomiHooks.isAvailable())
                    GreezeManagerServiceWrapper.clearMonitorNet(appRecord.getUid());
            }

            for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
                if (processRecord.isDeathProcess() || !processRecord.isFrozen())
                    continue;

                if (FrozenRW.thaw(processRecord.getRunningUid(), processRecord.getPid())) {
                    processRecord.setFrozen(false);
                    processRecord.setLastCompactTime(0);
                }
            }

            appRecord.setFrozen(hasFrozenProcess(appRecord));
        }
    }

    public static void thawAll() {
        for (AppRecord appRecord : AppService.getAllRecordsSnapshot()) {
            if (appRecord != null && appRecord.isFrozen())
                thaw(appRecord);
        }
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

        if (appRecord.isFrozen() || Objects.equals(reason, "MESSAGE PUSH"))
            Log.i(appRecord.getPackageNameWithUser() + " " + reason);

        thaw(appRecord);
        if (appRecord.getAppState().isWaitingNotification()) {
            FreezerHandler.sendWaitingNotificationFreezeMessage(appRecord, interval);
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
}
