package nep.timeline.cirno.ui.utils

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import nep.timeline.cirno.log.Log

object RootFreezerRepository {
    private const val TAG = "RootFreezerRepository"

    private const val FROZEN_DIR = "/sys/fs/cgroup/frozen"
    private const val UNFROZEN_DIR = "/sys/fs/cgroup/unfrozen"

    fun isFrozenFreezerAvailable(): Boolean {
        return try {
            val paths = listOf(
                "$FROZEN_DIR/cgroup.procs",
                "$FROZEN_DIR/cgroup.freeze",
                "$UNFROZEN_DIR/cgroup.procs",
                "$UNFROZEN_DIR/cgroup.freeze",
            )
            val available = paths.all { SuFile(it).exists() }
            Log.d("$TAG: Frozen mode availability: $available")
            available
        } catch (e: Throwable) {
            Log.e("$TAG: Failed to check Frozen mode availability", e)
            false
        }
    }

    fun isUidFreezerAvailable(): Boolean {
        return try {
            if (SuFile("/sys/fs/cgroup/uid_1000/cgroup.freeze").exists()) {
                SuFile("/sys/fs/cgroup/uid_0/cgroup.freeze").exists()
            } else {
                SuFile("/sys/fs/cgroup/system/uid_0/cgroup.freeze").exists()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isAnyFreezerAvailable(): Boolean = isUidFreezerAvailable() || isFrozenFreezerAvailable()

    // frozen cgroup 初始化失败时由 UI 侧用 root 修复：目录/文件由 root 创建后属主是 root，
    // system_server 无法写入，因此需要 chown 回 system 并补齐权限，与 hook 侧初始化保持一致
    fun repairFrozenCgroups(): Boolean {
        return try {
            val result = Shell.cmd(
                "mkdir -p $FROZEN_DIR $UNFROZEN_DIR",
                "echo 1 > $FROZEN_DIR/cgroup.freeze",
                "echo 0 > $UNFROZEN_DIR/cgroup.freeze",
                "chown system:system $FROZEN_DIR $UNFROZEN_DIR",
                "chown system:system $FROZEN_DIR/cgroup.procs $FROZEN_DIR/cgroup.freeze $UNFROZEN_DIR/cgroup.procs $UNFROZEN_DIR/cgroup.freeze",
                "chmod 0755 $FROZEN_DIR $UNFROZEN_DIR",
                "chmod 0644 $FROZEN_DIR/cgroup.procs $FROZEN_DIR/cgroup.freeze $UNFROZEN_DIR/cgroup.procs $UNFROZEN_DIR/cgroup.freeze",
                "restorecon -R $FROZEN_DIR $UNFROZEN_DIR >/dev/null 2>&1 || true"
            ).exec()
            if (!result.isSuccess) {
                Log.w("$TAG: 修复 frozen cgroup 失败: " + result.err.joinToString(separator = "; "))
                return false
            }
            val available = isFrozenFreezerAvailable()
            Log.d("$TAG: 修复 frozen cgroup 后可用性: $available")
            available
        } catch (e: Throwable) {
            Log.w("$TAG: 修复 frozen cgroup 失败", e)
            false
        }
    }
}
