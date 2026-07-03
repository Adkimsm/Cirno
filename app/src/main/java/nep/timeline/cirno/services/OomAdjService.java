package nep.timeline.cirno.services;

import android.os.Handler;

import java.nio.ByteBuffer;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class OomAdjService {
    private static final int LMK_PROCPRIO = 1;
    private static final Handler handler = Handlers.makeHandlerBackground("OomAdj");
    private static volatile Class<?> processListClass;

    public static void init(ClassLoader classLoader) {
        processListClass = CakeReflection.findClassIfExists("com.android.server.am.ProcessList", classLoader);
    }

    public static void applyForAppAsync(AppRecord appRecord) {
        if (appRecord == null) {
            return;
        }
        handler.post(() -> applyForApp(appRecord));
    }

    public static void applyForProcessAsync(ProcessRecord processRecord) {
        if (processRecord == null) {
            return;
        }
        handler.post(() -> applyForProcess(processRecord));
    }

    public static void applyForPidAsync(int pid) {
        if (pid <= 0) {
            return;
        }
        handler.post(() -> applyForProcess(ProcessService.getProcessRecordByPid(pid)));
    }

    private static void applyForApp(AppRecord appRecord) {
        if (!shouldApply(appRecord)) {
            return;
        }
        int adj = AppConfigs.getBackgroundOomAdj(appRecord.getPackageName(), appRecord.getUserId());
        for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
            applyAdj(processRecord, adj);
        }
    }

    private static void applyForProcess(ProcessRecord processRecord) {
        if (processRecord == null) {
            return;
        }
        AppRecord appRecord = processRecord.getAppRecord();
        if (!shouldApply(appRecord)) {
            return;
        }
        applyAdj(processRecord, AppConfigs.getBackgroundOomAdj(appRecord.getPackageName(), appRecord.getUserId()));
    }

    private static boolean shouldApply(AppRecord appRecord) {
        return appRecord != null
                && !appRecord.getAppState().isVisible()
                && AppConfigs.hasBackgroundOomAdj(appRecord.getPackageName(), appRecord.getUserId());
    }

    private static void applyAdj(ProcessRecord processRecord, int adj) {
        if (processRecord == null || processRecord.isDeathProcess() || !AppConfigs.isValidBackgroundOomAdj(adj)) {
            return;
        }
        if (writeLmkd(processRecord.getPid(), processRecord.getRunningUid(), adj)) {
            Log.d("OomAdjService: applied adj=" + adj + " to " + processRecord.getProcessName()
                    + " pid=" + processRecord.getPid());
        }
    }

    private static boolean writeLmkd(int pid, int uid, int adj) {
        Class<?> clazz = processListClass;
        if (clazz == null || pid <= 0) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.allocate(4 * 4);
        buffer.putInt(LMK_PROCPRIO);
        buffer.putInt(pid);
        buffer.putInt(uid);
        buffer.putInt(adj);
        try {
            Object result = CakeReflection.callStaticMethod(
                    clazz,
                    "writeLmkd",
                    new Class[]{ByteBuffer.class, ByteBuffer.class},
                    buffer,
                    null
            );
            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            Log.d("OomAdjService: writeLmkd failed for pid=" + pid + ": " + e.getMessage());
            return false;
        }
    }
}
