package nep.timeline.cirno.rekernel;

import java.util.List;
import java.util.Set;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.StringUtils;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class ReKernel {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public static boolean isRunning() {
        return org.sakion.rekernel.ReKernel.isRunning();
    }

    public static boolean monitorNet(int uid) {
        return org.sakion.rekernel.ReKernel.addMonitorNet(uid);
    }

    public static boolean delMonitorNet(int uid) {
        return org.sakion.rekernel.ReKernel.delMonitorNet(uid);
    }

    public static void start(ClassLoader classLoader, Runnable onConnected) {
        start(classLoader, onConnected, null);
    }

    public static void start(ClassLoader classLoader, Runnable onConnected, Runnable onFailed) {
        int netlinkUnit = GlobalVars.globalSettings != null
                ? GlobalVars.globalSettings.netlinkUnit : -1;

        int result = org.sakion.rekernel.ReKernel.registerListener(
                new AdapterCallback(),
                true,
                netlinkUnit
        );

        if (result == -1) {
            String error = org.sakion.rekernel.ReKernel.lastError;
            Log.w("ReKernel连接失败" + (error != null ? ": " + error : ""));
            if (onFailed != null) onFailed.run();
            return;
        }

        Log.i("ReKernel已连接, protocol=" + (result == 0 ? "Generic" : "Legacy#" + result));
        nep.timeline.cirno.services.StatusBinderHub.setSignal("available_rekernel", "1");
        nep.timeline.cirno.services.StatusBinderHub.setSignal(
                nep.timeline.cirno.services.StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel");
        if (onConnected != null) onConnected.run();

        Handlers.rekernel.postDelayed(() -> {
            Set<String> apps = GlobalVars.applicationSettings != null
                    ? GlobalVars.applicationSettings.networkMessageApps : null;
            if (apps != null) {
                for (String key : apps) {
                    String[] parts = key.split("#");
                    if (parts.length < 1) continue;
                    String pkg = parts[0];
                    int userId = parts.length > 1 ? StringUtils.StringToInteger(parts[1]) : 0;
                    AppRecord record = AppService.get(pkg, userId);
                    if (record != null) {
                        monitorNet(record.getUid());
                    }
                }
            }
        }, 10_000L);
    }

    private static class AdapterCallback implements org.sakion.rekernel.ReKernel.Callback {
        @Override
        public void binder(int type, boolean oneway, int fromUid, int fromPid,
                           int targetUid, int targetPid, String rpcName, int code) {
            try {
                if (oneway && type != BINDER_FREE_BUFFER_FULL)
                    return;

                String typeName = switch (type) {
                    case BINDER_TRANSACTION -> "TRANSACTION";
                    case BINDER_REPLY -> "REPLY";
                    case BINDER_FREE_BUFFER_FULL -> "FREE_BUFFER_FULL";
                    default -> "UNKNOWN";
                };

                List<AppRecord> appRecords = AppService.getByUid(targetUid);
                if (appRecords.isEmpty())
                    return;
                for (AppRecord appRecord : appRecords) {
                    if (appRecord == null)
                        continue;
                    FreezerService.temporaryUnfreezeIfNeed(appRecord,
                            "内核Binder(" + (oneway ? "ASYNC" : "SYNC") + "), 类型: " + typeName,
                            TEMP_UNFREEZE_INTERVAL_MS);
                }
            } catch (Exception e) {
                Log.e("ReKernel Binder事件处理失败", e);
            }
        }

        @Override
        public void signal(int signal, int killerUid, int killerPid,
                           int targetUid, int targetPid) {
            try {
                if (targetPid <= 0)
                    return;
                ProcessRecord processRecord = ProcessService.getProcessRecordByPid(targetPid);
                if (processRecord == null)
                    return;
                ProcessService.removeProcessRecordWithoutThaw(processRecord,
                        "ReKernel Signal(signal=" + signal + ")");
            } catch (Exception e) {
                Log.e("ReKernel Signal事件处理失败", e);
            }
        }

        @Override
        public void network(int proto, int targetUid, int dataLen) {
            try {
                String protoName = switch (proto) {
                    case PROTO_IPV4 -> "ipv4";
                    case PROTO_IPV6 -> "ipv6";
                    default -> "unknown";
                };

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

                    FreezerService.temporaryUnfreezeIfNeed(appRecord,
                            "内核Network(" + protoName + ")",
                            TEMP_UNFREEZE_INTERVAL_MS);
                }
            } catch (Exception e) {
                Log.e("ReKernel Network事件处理失败", e);
            }
        }

        @Override
        public void disconnected() {
            Log.w("ReKernel连接已断开");
            nep.timeline.cirno.services.StatusBinderHub.setSignal("available_rekernel", "0");
        }

        @Override
        public void exception(Exception exception) {
            Log.e("ReKernel异常", exception);
        }
    }
}
