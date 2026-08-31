package nep.timeline.cirno.utils;

import android.os.Process;

import java.nio.file.Files;
import java.nio.file.Paths;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.log.Log;

public class FrozenRW {
    public static final String cgroupV2 = "/sys/fs/cgroup";
    private static final String cgroupV2FrozenDir = cgroupV2 + "/frozen";
    private static final String cgroupV2UnfrozenDir = cgroupV2 + "/unfrozen";
    private static final String cgroupV2FrozenProcs = cgroupV2FrozenDir + "/cgroup.procs";
    private static final String cgroupV2UnfrozenProcs = cgroupV2UnfrozenDir + "/cgroup.procs";
    private static final boolean cgroupV2SysAppIsolated;

    static {
        String path = "/sys/fs/cgroup/uid_1000/cgroup.freeze";
        cgroupV2SysAppIsolated = !Files.exists(Paths.get(path));
    }

    private static boolean useFrozenMode() {
        GlobalSettings settings = GlobalVars.globalSettings;
        return settings != null && GlobalSettings.FREEZER_MODE_FROZEN.equals(settings.freezerMode);
    }

    private static boolean writeFrozen(int uid, int pid, int frozenState) {
        return writeFrozen(uid, pid, frozenState, true);
    }

    private static boolean writeFrozen(int uid, int pid, int frozenState, boolean logFailure) {
        if (useFrozenMode()) {
            String path = frozenState == 1 ? cgroupV2FrozenProcs : cgroupV2UnfrozenProcs;
            return RWUtils.writeFrozen(path, pid, logFailure);
        }

        if (!cgroupV2SysAppIsolated) {
            return RWUtils.writeFrozen(cgroupV2 + "/uid_" + uid + "/pid_" + pid + "/cgroup.freeze", frozenState, logFailure);
        }

        if (uid < Process.FIRST_APPLICATION_UID)
            return RWUtils.writeFrozen(cgroupV2 + "/system/uid_" + uid + "/pid_" + pid + "/cgroup.freeze", frozenState, logFailure);
        else
            return RWUtils.writeFrozen(cgroupV2 + "/apps/uid_" + uid + "/pid_" + pid + "/cgroup.freeze", frozenState, logFailure);
    }

    public static boolean ensureFrozenCgroups() {
        try {
            if (!Files.exists(Paths.get(cgroupV2FrozenDir)))
                Files.createDirectory(Paths.get(cgroupV2FrozenDir));
            if (!Files.exists(Paths.get(cgroupV2UnfrozenDir)))
                Files.createDirectory(Paths.get(cgroupV2UnfrozenDir));
        } catch (Throwable e) {
            Log.e("创建 frozen/unfrozen cgroup 失败", e);
        }

        return isFrozenCgroupsAvailable();
    }

    public static boolean isFrozenCgroupsAvailable() {
        return Files.isReadable(Paths.get(cgroupV2FrozenProcs))
                && Files.isWritable(Paths.get(cgroupV2FrozenProcs))
                && Files.isReadable(Paths.get(cgroupV2FrozenDir + "/cgroup.freeze"))
                && Files.isWritable(Paths.get(cgroupV2FrozenDir + "/cgroup.freeze"))
                && Files.isReadable(Paths.get(cgroupV2UnfrozenProcs))
                && Files.isWritable(Paths.get(cgroupV2UnfrozenProcs))
                && Files.isReadable(Paths.get(cgroupV2UnfrozenDir + "/cgroup.freeze"))
                && Files.isWritable(Paths.get(cgroupV2UnfrozenDir + "/cgroup.freeze"));
    }

    public static boolean frozen(int uid, int pid) {
        return writeFrozen(uid, pid, 1);
    }

    public static boolean thaw(int uid, int pid) {
        return writeFrozen(uid, pid, 0);
    }

    public static boolean thawQuietly(int uid, int pid) {
        return writeFrozen(uid, pid, 0, false);
    }
}
