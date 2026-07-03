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
    private static final String AVAILABLE_REKERNEL = "available_rekernel";
    private static final String REKERNEL_KERNEL = "kernel";
    private static final String REKERNEL_EBPF = "ebpf";
    private static volatile org.sakion.rekernel.ReKernel.Callback.Category currentCategory = null;

    public static boolean isRunning() {
        if (currentCategory == org.sakion.rekernel.ReKernel.Callback.Category.eBPF)
            return org.sakion.rekernel.ReKernel.eBPFisRunning();

        if (currentCategory == org.sakion.rekernel.ReKernel.Callback.Category.Generic
                || currentCategory == org.sakion.rekernel.ReKernel.Callback.Category.Legacy)
            return org.sakion.rekernel.ReKernel.isRunning();

        return org.sakion.rekernel.ReKernel.isRunning()
                || org.sakion.rekernel.ReKernel.eBPFisRunning();
    }

    public static boolean monitorNet(int uid) {
        if (currentCategory == org.sakion.rekernel.ReKernel.Callback.Category.eBPF)
            return org.sakion.rekernel.ReKernel.eBPFaddMonitorNet(uid);

        return org.sakion.rekernel.ReKernel.addMonitorNet(uid);
    }

    public static boolean delMonitorNet(int uid) {
        if (currentCategory == org.sakion.rekernel.ReKernel.Callback.Category.eBPF)
            return org.sakion.rekernel.ReKernel.eBPFdelMonitorNet(uid);

        return org.sakion.rekernel.ReKernel.delMonitorNet(uid);
    }

    public static void stop() {
        try {
            org.sakion.rekernel.ReKernel.unregisterListener();
        } catch (Throwable ignored) {
        }
        try {
            org.sakion.rekernel.ReKernel.eBPFunregisterListener();
        } catch (Throwable ignored) {
        }
        currentCategory = null;
    }

    public static void start(ClassLoader classLoader, Runnable onConnected) {
        startKernel(classLoader, onConnected, null);
    }

    public static void start(ClassLoader classLoader, Runnable onConnected, Runnable onFailed) {
        startKernel(classLoader, onConnected, onFailed);
    }

    public static void startKernel(ClassLoader classLoader, Runnable onConnected) {
        startKernel(classLoader, onConnected, null);
    }

    public static void startKernel(ClassLoader classLoader, Runnable onConnected, Runnable onFailed) {
        int netlinkUnit = GlobalVars.globalSettings != null
                ? GlobalVars.globalSettings.netlinkUnit : -1;

        int result = org.sakion.rekernel.ReKernel.registerListener(
                new AdapterCallback(),
                true,
                netlinkUnit
        );

        if (result == -1) {
            String error = org.sakion.rekernel.ReKernel.lastError;
            Log.w("ReKernel Kernel连接失败" + (error != null ? ": " + error : ""));
            if (onFailed != null) onFailed.run();
            return;
        }

        currentCategory = result == 0
                ? org.sakion.rekernel.ReKernel.Callback.Category.Generic
                : org.sakion.rekernel.ReKernel.Callback.Category.Legacy;
        Log.i("ReKernel已连接, protocol=" + (result == 0 ? "Generic" : "Legacy#" + result));
        addAvailableReKernel(REKERNEL_KERNEL);
        nep.timeline.cirno.services.StatusBinderHub.setSignal(
                nep.timeline.cirno.services.StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel Kernel");
        if (onConnected != null) onConnected.run();

        restoreMonitorNetApps();
    }

    public static void startEbpf(ClassLoader classLoader, Runnable onConnected) {
        startEbpf(classLoader, onConnected, null);
    }

    public static void startEbpf(ClassLoader classLoader, Runnable onConnected, Runnable onFailed) {
        boolean connected = org.sakion.rekernel.ReKernel.eBPFregisterListener(new AdapterCallback());

        if (!connected) {
            String error = org.sakion.rekernel.ReKernel.lastError;
            Log.w("ReKernel eBPF连接失败" + (error != null ? ": " + error : ""));
            if (onFailed != null) onFailed.run();
            return;
        }

        currentCategory = org.sakion.rekernel.ReKernel.Callback.Category.eBPF;
        String version = org.sakion.rekernel.ReKernel.eBPFgetVersion();
        Log.i("ReKernel已连接, protocol=eBPF" + (version != null ? ", version=" + version : ""));
        addAvailableReKernel(REKERNEL_EBPF);
        nep.timeline.cirno.services.StatusBinderHub.setSignal(
                nep.timeline.cirno.services.StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel eBPF");
        if (onConnected != null) onConnected.run();

        restoreMonitorNetApps();
    }

    private static void restoreMonitorNetApps() {
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

    private static void addAvailableReKernel(String type) {
        String current = nep.timeline.cirno.services.StatusBinderHub.getSignal(AVAILABLE_REKERNEL);
        boolean hasKernel = hasAvailableReKernel(current, REKERNEL_KERNEL) || "1".equals(current);
        boolean hasEbpf = hasAvailableReKernel(current, REKERNEL_EBPF) || "1".equals(current);
        if (REKERNEL_KERNEL.equals(type)) {
            hasKernel = true;
        } else if (REKERNEL_EBPF.equals(type)) {
            hasEbpf = true;
        }
        setAvailableReKernel(hasKernel, hasEbpf);
    }

    private static void removeAvailableReKernel(String type) {
        String current = nep.timeline.cirno.services.StatusBinderHub.getSignal(AVAILABLE_REKERNEL);
        boolean hasKernel = hasAvailableReKernel(current, REKERNEL_KERNEL) || "1".equals(current);
        boolean hasEbpf = hasAvailableReKernel(current, REKERNEL_EBPF) || "1".equals(current);
        if (REKERNEL_KERNEL.equals(type)) {
            hasKernel = false;
        } else if (REKERNEL_EBPF.equals(type)) {
            hasEbpf = false;
        }
        setAvailableReKernel(hasKernel, hasEbpf);
    }

    private static boolean hasAvailableReKernel(String value, String type) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return false;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            if (type.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static void setAvailableReKernel(boolean hasKernel, boolean hasEbpf) {
        String value;
        if (hasKernel && hasEbpf) {
            value = REKERNEL_KERNEL + "," + REKERNEL_EBPF;
        } else if (hasKernel) {
            value = REKERNEL_KERNEL;
        } else if (hasEbpf) {
            value = REKERNEL_EBPF;
        } else {
            value = "0";
        }
        nep.timeline.cirno.services.StatusBinderHub.setSignal(AVAILABLE_REKERNEL, value);
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
        public void disconnected(Category category) {
            if (currentCategory == category) {
                currentCategory = null;
            }
            Log.w("ReKernel连接已断开, protocol=" + category);
            if (category == Category.eBPF) {
                removeAvailableReKernel(REKERNEL_EBPF);
            } else if (category == Category.Generic || category == Category.Legacy) {
                removeAvailableReKernel(REKERNEL_KERNEL);
            }
        }

        @Override
        public void exception(Exception exception) {
            Log.e("ReKernel异常", exception);
        }
    }
}
