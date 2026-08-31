package nep.timeline.cirno.ui.utils

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
}
