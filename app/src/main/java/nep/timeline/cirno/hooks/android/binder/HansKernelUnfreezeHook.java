package nep.timeline.cirno.hooks.android.binder;

import java.util.List;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.utils.SystemChecker;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class HansKernelUnfreezeHook extends MethodHook {
    private static final int BINDER_SYNC_TYPE = 1;
    private static final int SIGNAL_TYPE = 3;
    private static final int PACKET_TYPE = 4;
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public HansKernelUnfreezeHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.OplusHansManager";
    }

    @Override
    public String getTargetMethod() {
        return "unfreezeForKernel";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{int.class, int.class, int.class, int.class, int.class, String.class, int.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleHansEvents())
                    return;

                int type = (int) callback.getArgs()[0];
                int targetPid = (int) callback.getArgs()[3];
                int targetUid = (int) callback.getArgs()[4];
                int code = (int) callback.getArgs()[6];

                switch (type) {
                    case BINDER_SYNC_TYPE -> FreezerService.temporaryUnfreezeIfNeed(targetUid, "Binder", TEMP_UNFREEZE_INTERVAL_MS);
                    case SIGNAL_TYPE -> {
                        if (!isHandledSignal(code))
                            return;
                        ProcessRecord processRecord = ProcessService.getProcessRecordByPid(targetPid);
                        if (processRecord == null)
                            return;
                        if (ProcessService.removeProcessRecordWithoutThaw(processRecord, "HansKernel Signal(signal=" + code + ")") != null)
                            MonitorBinderHub.onProcessRemoved(targetPid);
                    }
                    case PACKET_TYPE -> {
                        List<AppRecord> appRecords = AppService.getByUid(targetUid);
                        if (appRecords.isEmpty()) {
                            // 非 Cirno 管理的 uid（系统 uid、未缓存应用）放行厂商解冻逻辑，
                            // 否则被内核冻结但不受 Cirno 管理的进程将无人解冻
                            return;
                        }

                        boolean allowed = false;
                        for (AppRecord appRecord : appRecords) {
                            if (appRecord == null)
                                continue;

                            boolean networkMessageAllowed = AppConfigs.isNetworkMessageAllowed(
                                appRecord.getPackageName(),
                                appRecord.getUserId()
                            );
                            if (!networkMessageAllowed)
                                continue;

                            allowed = true;
                            FreezerService.temporaryUnfreezeIfNeed(appRecord, "HansKernel Packet", TEMP_UNFREEZE_INTERVAL_MS);
                        }

                        if (!allowed)
                            callback.returnAndSkip(null);
                    }
                }
            }
        };
    }

    private static boolean isHandledSignal(int code) {
        return code == 9 || code == 15 || code == 6 || code == 3;
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isOplus(classLoader);
    }
}
