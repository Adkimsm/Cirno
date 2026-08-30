package nep.timeline.cirno.ui.utils

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.Shell

import nep.timeline.cirno.log.Log

object RootFreezerRepository {
    private const val TAG = "RootFreezerRepository"

    private const val FROZEN_DIR = "/sys/fs/cgroup/frozen"
    private const val UNFROZEN_DIR = "/sys/fs/cgroup/unfrozen"

    fun ensureFrozenFreezerAvailable(): Boolean {
        if (isFrozenFreezerAvailable()) {
            return true
        }

        return try {
            val result = Shell.cmd(
                "mkdir -p $FROZEN_DIR",
                "mkdir -p $UNFROZEN_DIR",
            ).exec()
            result.isSuccess && isFrozenFreezerAvailable()
        } catch (e: Throwable) {
            Log.e("$TAG: Failed to prepare Frozen mode", e)
            false
        }
    }

    fun isFrozenFreezerAvailable(): Boolean {
        return try {
            val frozenAvailable = SuFile("$FROZEN_DIR/cgroup.freeze").exists()
            val unfrozenAvailable = SuFile("$UNFROZEN_DIR/cgroup.freeze").exists()
            Log.d("$TAG: Frozen mode availability: frozen=$frozenAvailable, unfrozen=$unfrozenAvailable")
            frozenAvailable && unfrozenAvailable
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
