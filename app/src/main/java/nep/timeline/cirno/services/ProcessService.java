package nep.timeline.cirno.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.utils.FrozenRW;
import nep.timeline.cirno.utils.ProcUtils;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class ProcessService {
    private static final String WCHAN_V2_FROZEN = "do_freezer_trap";
    private static final Map<String, Map<Integer, ProcessRecord>> PROCESS_NAME_MAP = new ConcurrentHashMap<>();
    private static final Object lock = new Object();

    public static ProcessRecord addProcessRecord(Object record) {
        return addProcessRecord(record, true);
    }

    private static ProcessRecord addProcessRecord(Object record, boolean scheduleFreeze) {
        if (record == null)
            return null;

        ProcessRecord processRecord = new ProcessRecord(record);
        AppRecord appRecord = processRecord.getAppRecord();
        if (appRecord == null)
            return null;

        ProcessRecord oldRecord;
        synchronized (lock) {
            Map<Integer, ProcessRecord> records = PROCESS_NAME_MAP.computeIfAbsent(processRecord.getProcessName(), k -> new ConcurrentHashMap<>());
            oldRecord = records.put(processRecord.getRunningUid(), processRecord);
            if (oldRecord != null) {
                AppRecord oldAppRecord = oldRecord.getAppRecord();
                if (oldAppRecord != null)
                    oldAppRecord.getProcessRecords().remove(oldRecord);
            }
            appRecord.getProcessRecords().add(processRecord);
        }

        // 被替换的旧记录若仍处于冻结状态且是不同 pid，解冻旧进程避免其永久卡死
        if (oldRecord != null && oldRecord.isFrozen()) {
            int oldPid = oldRecord.getPid();
            if (oldPid > 0 && oldPid != processRecord.getPid())
                FrozenRW.thawQuietly(oldRecord.getRunningUid(), oldPid);
        }

        if (scheduleFreeze)
            FreezerHandler.sendFreezeMessage(appRecord);

        return processRecord;
    }

    public static void rebuildFromSystem() {
        try {
            Object ams = ActivityManagerService.getInstance();
            if (ams == null) {
                Log.w("ProcessService rebuild skipped: ActivityManagerService is null");
                return;
            }

            Object mPidsSelfLocked = ActivityManagerService.getPidsSelfLocked();
            if (mPidsSelfLocked == null) {
                Log.w("ProcessService rebuild skipped: mPidsSelfLocked is null");
                return;
            }

            synchronized (lock) {
                PROCESS_NAME_MAP.clear();
                AppService.clearRecords();
            }

            // 只在持有 AMS 热锁期间做引用拷贝，反射解析 / PMS 查询 / 读 /proc 全部移到锁外
            java.util.ArrayList<Object> rawRecords = new java.util.ArrayList<>();
            synchronized (mPidsSelfLocked) {
                int size = (int) CakeReflection.callMethod(mPidsSelfLocked, "size");
                for (int i = 0; i < size; i++) {
                    Object systemProcessRecord = CakeReflection.callMethod(mPidsSelfLocked, "valueAt", i);
                    if (systemProcessRecord != null)
                        rawRecords.add(systemProcessRecord);
                }
            }

            int rebuiltProcesses = 0;
            int frozenProcesses = 0;
            for (Object systemProcessRecord : rawRecords) {
                ProcessRecord processRecord = addProcessRecord(systemProcessRecord, false);
                if (processRecord == null)
                    continue;

                rebuiltProcesses++;
                int pid = processRecord.getPid();
                if (WCHAN_V2_FROZEN.equals(ProcUtils.readWchan(pid))) {
                    processRecord.setFrozen(true);
                    AppRecord appRecord = processRecord.getAppRecord();
                    if (appRecord != null)
                        appRecord.setFrozen(true);
                    frozenProcesses++;
                }
            }

            Log.i("ProcessService rebuilt from system: processes=" + rebuiltProcesses + ", frozen=" + frozenProcesses);
        } catch (Throwable e) {
            Log.w("ProcessService rebuild failed", e);
        }
    }

    public static AppRecord removeProcessRecord(ProcessRecord processRecord) {
        return removeProcessRecord(processRecord.getProcessName(), processRecord.getRunningUid(), true, null);
    }

    public static AppRecord removeProcessRecordWithoutThaw(ProcessRecord processRecord, String path) {
        return removeProcessRecord(processRecord.getProcessName(), processRecord.getRunningUid(), false, path);
    }

    public static AppRecord removeProcessRecord(String name, int uid) {
        return removeProcessRecord(name, uid, true, null);
    }

    private static AppRecord removeProcessRecord(String name, int uid, boolean thawOnRemove, String path) {
        ProcessRecord processRecord;
        AppRecord appRecord;
        boolean shouldThaw;
        int thawUid;
        int thawPid;
        String processName;
        synchronized (lock) {
            Map<Integer, ProcessRecord> records = PROCESS_NAME_MAP.get(name);
            if (records == null)
                return null;
            processRecord = records.remove(uid);
            if (processRecord == null)
                return null;
            if (records.isEmpty())
                PROCESS_NAME_MAP.remove(name, records);
            shouldThaw = processRecord.isFrozen();
            thawUid = processRecord.getRunningUid();
            thawPid = processRecord.getPid();
            processName = processRecord.getProcessName();
            appRecord = processRecord.getAppRecord();
            if (appRecord != null) {
                appRecord.getProcessRecords().remove(processRecord);
                if (appRecord.getProcessRecords().isEmpty())
                    appRecord.reset();
                else
                    appRecord.setFrozen(hasFrozenProcess(appRecord));
            }
        }
        if (thawOnRemove && shouldThaw)
            FrozenRW.thaw(thawUid, thawPid);
        else if (!thawOnRemove && thawPid > 0)
            FrozenRW.thawQuietly(thawUid, thawPid);
        if (!thawOnRemove) {
            String packageName = appRecord == null ? processRecord.getPackageName() : appRecord.getPackageNameWithUser();
            Log.d(packageName + " 进程 " + processName + "(pid=" + thawPid + ") 被取消管理，路径: " + path);
        }
        return appRecord;
    }

    private static boolean hasFrozenProcess(AppRecord appRecord) {
        for (ProcessRecord processRecord : appRecord.getProcessRecords()) {
            if (processRecord != null && !processRecord.isDeathProcess() && processRecord.isFrozen())
                return true;
        }
        return false;
    }

    public static ProcessRecord getProcessRecord(Object record) {
        if (record == null)
            return null;
        // 广播分发等热路径高频调用：直接反射读两个字段做 map 查询，
        // 不再构造完整 ProcessRecord 包装（旧实现每次 4 次反射 + AppService 查询）
        try {
            String processName = (String) CakeReflection.getObjectField(record, "processName");
            int uid = CakeReflection.getIntField(record, "uid");
            return getProcessRecord(processName, uid);
        } catch (Throwable e) {
            return null;
        }
    }

    public static ProcessRecord getProcessRecord(String processName, int uid) {
        if (processName == null || processName.isEmpty())
            return null;
        Map<Integer, ProcessRecord> map = PROCESS_NAME_MAP.get(processName);
        if (map == null)
            return null;
        return map.get(uid);
    }

    public static ProcessRecord getProcessRecordByPid(int pid) {
        ProcessRecord processRecord;
        Object mPidsSelfLocked = ActivityManagerService.getPidsSelfLocked();
        if (mPidsSelfLocked == null)
            return null;
        synchronized (mPidsSelfLocked) {
            processRecord = getProcessRecord(CakeReflection.callMethod(mPidsSelfLocked, "get", pid));
        }
        return processRecord;
    }
}
