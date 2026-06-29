package nep.timeline.cirno.services;

import android.os.Build;
import android.os.Handler;
import android.os.Message;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class CompactionService {
    private static final int MSG_COMPACT_APP = 0;
    private static final Handler handler = new CompactionMessageHandler(Handlers.makeLooper("Compaction"));

    private static volatile Class<?> compactProfileClass;
    private static volatile Object compactProfileFull;
    private static volatile Object compactSourceApp;
    private static volatile boolean enumsInitialized = false;
    private static volatile boolean initFailed = false;

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enumsInitialized && !initFailed;
    }

    public static void initEnums(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            initFailed = true;
            return;
        }
        if (enumsInitialized || initFailed) return;
        synchronized (CompactionService.class) {
            if (enumsInitialized || initFailed) return;
            try {
                compactProfileClass = CakeReflection.findClassIfExists(
                        "com.android.server.am.CachedAppOptimizer$CompactProfile", classLoader);
                if (compactProfileClass == null) {
                    Log.w("CompactionService: CompactProfile enum not found");
                    initFailed = true;
                    return;
                }
                compactProfileFull = Enum.valueOf((Class<Enum>) compactProfileClass, "FULL");

                Class<?> sourceClass = CakeReflection.findClassIfExists(
                        "com.android.server.am.CachedAppOptimizer$CompactSource", classLoader);
                if (sourceClass != null) {
                    compactSourceApp = Enum.valueOf((Class<Enum>) sourceClass, "APP");
                }
                if (compactSourceApp == null) {
                    Log.w("CompactionService: CompactSource.APP not found");
                    initFailed = true;
                    return;
                }

                enumsInitialized = true;
                Log.i("CompactionService: enums initialized successfully");
            } catch (Throwable e) {
                Log.e("CompactionService: failed to init enums", e);
                initFailed = true;
            }
        }
    }

    public static void scheduleCompaction(AppRecord appRecord, long delayMs) {
        if (!isSupported()) return;
        removeCompactionMessage(appRecord);
        Message msg = handler.obtainMessage(MSG_COMPACT_APP);
        msg.obj = appRecord;
        msg.arg1 = appRecord.hashCode();
        if (delayMs <= 0) {
            handler.sendMessage(msg);
        } else {
            handler.sendMessageDelayed(msg, delayMs);
        }
    }

    public static void cancelCompaction(AppRecord appRecord) {
        removeCompactionMessage(appRecord);
    }

    private static void removeCompactionMessage(AppRecord appRecord) {
        handler.removeMessages(MSG_COMPACT_APP, appRecord);
    }

    private static void doCompaction(AppRecord appRecord) {
        if (!isSupported() || !appRecord.isFrozen()) return;

        long totalRssBefore = 0L;
        for (ProcessRecord pr : appRecord.getProcessRecords()) {
            if (pr.isDeathProcess() || !pr.isFrozen()) continue;
            pr.updateCachedRss();
            totalRssBefore += pr.getCachedRssKb();
        }

        int compactedCount = 0;
        for (ProcessRecord pr : appRecord.getProcessRecords()) {
            if (pr.isDeathProcess() || !pr.isFrozen()) continue;
            if (pr.isCompacted()) continue;

            if (invokeCompactApp(pr)) {
                pr.setCompacted(true);
                compactedCount++;
            }
        }

        if (compactedCount > 0) {
            Log.d("CompactionService: compacted " + compactedCount + " processes for "
                    + appRecord.getPackageNameWithUser());
        }
    }

    private static boolean invokeCompactApp(ProcessRecord processRecord) {
        try {
            Object optimizerInstance = CachedAppOptimizer.getInstance();
            if (optimizerInstance == null) return false;
            if (compactProfileFull == null) return false;

            Object systemRecord = processRecord.getSystemInstance();
            if (systemRecord == null) return false;

            Object result = CakeReflection.callMethod(
                    optimizerInstance,
                    "compactApp",
                    systemRecord,
                    compactProfileFull,
                    compactSourceApp,
                    true
            );
            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            Log.d("CompactionService: compactApp failed for " + processRecord.getProcessName() + ": " + e.getMessage());
            return false;
        }
    }

    private static final class CompactionMessageHandler extends Handler {
        CompactionMessageHandler(android.os.Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_COMPACT_APP && msg.obj instanceof AppRecord) {
                AppRecord appRecord = (AppRecord) msg.obj;
                try {
                    doCompaction(appRecord);
                } catch (Throwable e) {
                    Log.e("CompactionService: doCompaction failed", e);
                }
            }
        }
    }
}
