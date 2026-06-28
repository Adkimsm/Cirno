package nep.timeline.cirno.rekernel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.services.StatusBinderHub;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.StringUtils;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class ReKernel {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public static boolean isRunning() {
        return GenericReKernel.isRunning() || LegacyReKernel.isRunning();
    }

    public static boolean monitorNet(int uid) {
        if (GenericReKernel.isRunning())
            return GenericReKernel.monitorNet(uid);
        return LegacyReKernel.monitorNet(uid);
    }

    public static boolean delMonitorNet(int uid) {
        if (GenericReKernel.isRunning())
            return GenericReKernel.delMonitorNet(uid);
        return LegacyReKernel.delMonitorNet(uid);
    }

    public static void onEvent(String data) {
        Handlers.rekernel.post(() -> {
            try {
                int typeIdx = data.indexOf("type");
                int semiIdx = data.lastIndexOf(";");
                if (typeIdx < 0 || semiIdx <= typeIdx)
                    return;

                Map<String, String> params = parseParams(data.substring(typeIdx, semiIdx));
                String type = params.get("type");
                if (type == null)
                    return;

                switch (type) {
                    case "Binder" -> {
                        String bindertype = params.get("bindertype");
                        int oneway = getIntParam(params, "oneway");
                        int targetUid = getIntParam(params, "target");
                        if (oneway == 1 && !"free_buffer_full".equals(bindertype))
                            return;

                        List<AppRecord> appRecords = AppService.getByUid(targetUid);
                        if (appRecords.isEmpty())
                            return;
                        for (AppRecord appRecord : appRecords) {
                            if (appRecord == null)
                                continue;

                            FreezerService.temporaryUnfreezeIfNeed(appRecord, "内核Binder(" + (oneway == 1 ? "ASYNC" : "SYNC") + "), 类型: " + bindertype, TEMP_UNFREEZE_INTERVAL_MS);
                        }
                    }
                    case "Signal" -> {
                        int dstPid = getIntParam(params, "dst_pid");
                        int signal = getIntParam(params, "signal");
                        if (dstPid <= 0)
                            return;
                        ProcessRecord processRecord = ProcessService.getProcessRecordByPid(dstPid);
                        if (processRecord == null)
                            return;
                        ProcessService.removeProcessRecordWithoutThaw(processRecord, "ReKernel Signal(signal=" + signal + ")");
                    }
                    case "Network" -> {
                        int targetUid = getIntParam(params, "target");
                        String proto = params.get("proto");
                        List<AppRecord> appRecords = AppService.getByUid(targetUid);
                        if (appRecords.isEmpty())
                            return;
                        for (AppRecord appRecord : appRecords) {
                            if (appRecord == null)
                                continue;

                            boolean networkMessageAllowed = AppConfigs.isNetworkMessageAllowed(
                                appRecord.getPackageName(),
                                appRecord.getUserId()
                            );
                            if (!networkMessageAllowed)
                                continue;

                            FreezerService.temporaryUnfreezeIfNeed(appRecord, "内核Network(" + proto + ")", TEMP_UNFREEZE_INTERVAL_MS);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("ReKernel事件处理失败", e);
            }
        });
    }

    private static Map<String, String> parseParams(String message) {
        Map<String, String> map = new HashMap<>();
        for (String keyValue : message.split(",")) {
            String[] split = keyValue.split("=");
            if (split.length == 2)
                map.put(split[0].trim(), split[1].trim());
        }
        return map;
    }

    private static int getIntParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null)
            return 0;
        try {
            return StringUtils.StringToInteger(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void start(ClassLoader classLoader, Runnable onConnected) {
        start(classLoader, onConnected, null);
    }

    public static void start(ClassLoader classLoader, Runnable onConnected, Runnable onFailed) {
        GenericReKernel.start(classLoader, onConnected, onFailed);
    }
}
