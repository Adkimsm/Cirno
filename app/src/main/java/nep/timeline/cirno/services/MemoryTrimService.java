package nep.timeline.cirno.services;

import android.os.Handler;
import android.os.Message;
import android.system.Os;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class MemoryTrimService {
    private static final int MSG_TRIM_APP = 0;

    private static final Handler handler = new TrimMessageHandler(
            Handlers.makeLooper("MemoryTrim"));

    public static void scheduleTrim(AppRecord appRecord, long delayMs) {
        removeTrimMessage(appRecord);
        Message msg = handler.obtainMessage(MSG_TRIM_APP);
        msg.obj = appRecord;
        msg.arg1 = appRecord.hashCode();
        if (delayMs <= 0) {
            handler.sendMessage(msg);
        } else {
            handler.sendMessageDelayed(msg, delayMs);
        }
    }

    public static void cancelTrim(AppRecord appRecord) {
        removeTrimMessage(appRecord);
    }

    private static void removeTrimMessage(AppRecord appRecord) {
        handler.removeMessages(MSG_TRIM_APP, appRecord);
    }

    private static void doTrim(AppRecord appRecord) {
        if (!appRecord.isFrozen()) return;

        GlobalSettings gs = GlobalVars.globalSettings;
        if (gs == null || !gs.memoryTrimEnabled) return;

        int level = gs.memoryTrimLevel;
        int throttleSec = gs.memoryTrimThrottle;
        long throttleMs = throttleSec * 1000L;
        long now = System.currentTimeMillis();

        int trimmedCount = 0;
        for (ProcessRecord pr : appRecord.getProcessRecords()) {
            if (pr.isDeathProcess() || !pr.isFrozen()) continue;

            long lastTime = pr.getLastTrimMemoryTime();
            if (lastTime > 0 && now - lastTime < throttleMs) continue;

            boolean trimmed = pr.scheduleTrimMemory(level);
            if (trimmed) {
                pr.setLastTrimMemoryTime(now);
                trimmedCount++;
            }

            if (gs.memoryTrimGcEnabled) {
                try {
                    Os.kill(pr.getPid(), 10);
                } catch (Throwable e) {
                    Log.d("MemoryTrimService: GC signal failed for "
                            + pr.getProcessName() + ": " + e.getMessage());
                }
            }
        }

        if (trimmedCount > 0) {
            Log.d("MemoryTrimService: trimmed " + trimmedCount + " processes for "
                    + appRecord.getPackageNameWithUser());
        }
    }

    private static final class TrimMessageHandler extends Handler {
        TrimMessageHandler(android.os.Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_TRIM_APP && msg.obj instanceof AppRecord) {
                AppRecord appRecord = (AppRecord) msg.obj;
                try {
                    doTrim(appRecord);
                } catch (Throwable e) {
                    Log.e("MemoryTrimService: doTrim failed", e);
                }
            }
        }
    }
}
