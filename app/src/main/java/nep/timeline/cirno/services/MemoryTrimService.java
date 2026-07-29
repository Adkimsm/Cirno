package nep.timeline.cirno.services;

import android.system.Os;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.virtuals.ProcessRecord;

/**
 * 内存修剪改为"冻结前"执行：
 * 只有仍在运行的进程才能真正处理 scheduleTrimMemory 与 GC 信号。
 * 旧实现在进程已被 cgroup 冻结后才发 oneway binder 和 SIGUSR1，
 * 事务只会积压在目标进程的 binder 缓冲区（FREE_BUFFER_FULL 的成因之一），
 * trim 实际被延迟到解冻瞬间才集中执行。
 */
public class MemoryTrimService {
    private static final int SIGNAL_USR1 = 10;

    /**
     * 若本次冻结前需要修剪，则对该应用所有存活且未冻结的进程发出 trim/GC，并返回 true。
     * 调用方应在返回 true 时推迟冻结，给进程留出处理窗口。
     */
    public static boolean preFreezeTrimIfNeeded(AppRecord appRecord) {
        GlobalSettings gs = GlobalVars.globalSettings;
        if (gs == null || !gs.memoryTrimEnabled)
            return false;
        if (!AppConfigs.isMemoryTrimEnabled(appRecord.getPackageName(), appRecord.getUserId()))
            return false;

        int level = gs.memoryTrimLevel;
        long throttleMs = gs.memoryTrimThrottle * 1000L;
        long now = System.currentTimeMillis();
        boolean gcEnabled = gs.memoryTrimGcEnabled
                && AppConfigs.isMemoryTrimGcEnabled(appRecord.getPackageName(), appRecord.getUserId());

        boolean trimmed = false;
        for (ProcessRecord pr : appRecord.getProcessRecords()) {
            if (pr.isDeathProcess() || pr.isFrozen())
                continue;

            long lastTime = pr.getLastTrimMemoryTime();
            if (lastTime > 0 && now - lastTime < throttleMs)
                continue;

            if (pr.scheduleTrimMemory(level)) {
                pr.setLastTrimMemoryTime(now);
                trimmed = true;
            }

            if (gcEnabled) {
                try {
                    Os.kill(pr.getPid(), SIGNAL_USR1);
                } catch (Throwable e) {
                    Log.d("MemoryTrimService: GC signal failed for "
                            + pr.getProcessName() + ": " + e.getMessage());
                }
            }
        }
        return trimmed;
    }
}
