package nep.timeline.cirno.ui.utils

import com.topjohnwu.superuser.io.SuFile

object AddOnStatusRepository {
    private const val TOMB_STONE_MODULE_PROP = "/data/adb/modules/lib_tombstone/module.prop"
    private const val TOMB_STONE_MODULE_DISABLE = "/data/adb/modules/lib_tombstone/disable"

    enum class Status {
        ENABLED,
        DISABLED,
        NOT_INSTALLED,
        UNKNOWN,
    }

    fun getStatus(): Status {
        return try {
            val moduleProp = SuFile(TOMB_STONE_MODULE_PROP)
            if (!moduleProp.exists()) {
                Status.NOT_INSTALLED
            } else if (SuFile(TOMB_STONE_MODULE_DISABLE).exists()) {
                Status.DISABLED
            } else {
                Status.ENABLED
            }
        } catch (_: Throwable) {
            Status.UNKNOWN
        }
    }

    fun isAddOnEnabled(): Boolean {
        return getStatus() == Status.ENABLED
    }
}
