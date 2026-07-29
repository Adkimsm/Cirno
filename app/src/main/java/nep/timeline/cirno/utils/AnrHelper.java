package nep.timeline.cirno.utils;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class AnrHelper {
    // 解冻后的宽限期：刚解冻的进程主线程可能还在补处理积压任务，此窗口内的 ANR 依旧压制
    private static final long THAW_GRACE_PERIOD_MS = 10_000L;

    public static void processingAnr(CakeHooker.BeforeHookCallback callback, Object app) {
        if (app == null)
            return;
        ProcessRecord processRecord = ProcessService.getProcessRecord(app);
        if (processRecord == null)
            return;
        AppRecord appRecord = processRecord.getAppRecord();
        if (appRecord == null)
            return;
        if (appRecord.isSystem())
            return;
        // 只压制"因冻结导致"的 ANR：应用被冻结或刚解冻时跳过 ANR 处理；
        // 正常运行中真实卡死的应用仍应走系统 ANR 流程（旧实现无条件吞掉所有第三方 ANR）
        if (appRecord.isFrozen()
                || System.currentTimeMillis() - appRecord.getLastThawTime() < THAW_GRACE_PERIOD_MS)
            callback.returnAndSkip(null);
    }
}
