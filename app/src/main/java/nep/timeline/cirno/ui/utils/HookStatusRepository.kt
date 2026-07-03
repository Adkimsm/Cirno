package nep.timeline.cirno.ui.utils

import com.google.gson.Gson
import com.google.gson.JsonParser
import nep.timeline.cirno.binder.BinderService
import nep.timeline.cirno.provide.StatusBinder
import nep.timeline.cirno.socket.SocketClient

object HookStatusRepository {
    data class HookStatusSnapshot(
        val statusBinderAvailable: Boolean,
        val hasError: Boolean = false,
        val hookVersion: String? = null,
        val deviceType: String? = null,
        val hookType: String? = null,
        val availableHookTypes: List<String> = emptyList(),
        val packetAvailable: Boolean = false,
    )

    private val gson = Gson()

    fun loadHookStatusSnapshot(): HookStatusSnapshot {
        BinderService.register(AppContext.context)
        val status = StatusBinder.getInstance() ?: return HookStatusSnapshot(statusBinderAvailable = false)
        return try {
            val snapshotJson = status.getStatusSnapshot()
            if (snapshotJson.isNullOrBlank()) {
                XposedServiceStatus.updateSocketConnectionState(SocketClient.getInstance().isConnected)
                return HookStatusSnapshot(statusBinderAvailable = false)
            }
            val obj = JsonParser.parseString(snapshotJson).asJsonObject
            XposedServiceStatus.updateSocketConnectionState(true)
            HookStatusSnapshot(
                statusBinderAvailable = true,
                hasError = obj.get("error")?.asString == "1",
                hookVersion = obj.get("hook_version")?.asString?.takeIf { it.isNotBlank() },
                deviceType = obj.get("device_type")?.asString?.takeIf { it.isNotBlank() },
                hookType = obj.get("hook_type")?.asString?.takeIf { it.isNotBlank() },
                availableHookTypes = obj.get("available_hook_types")?.asJsonArray?.map { it.asString } ?: emptyList(),
                packetAvailable = obj.get("packet_available")?.asBoolean ?: false,
            )
        } catch (_: Throwable) {
            XposedServiceStatus.updateSocketConnectionState(SocketClient.getInstance().isConnected)
            HookStatusSnapshot(statusBinderAvailable = false)
        }
    }

    fun isPacketAvailable(): Boolean {
        BinderService.register(AppContext.context)
        val status = StatusBinder.getInstance() ?: return false
        return try {
            status.isPacketAvailable
        } catch (_: Throwable) {
            false
        }
    }

    fun getHookVersion(): String? {
        BinderService.register(AppContext.context)
        val status = StatusBinder.getInstance() ?: return null
        return try {
            status.hookVersion.takeIf { !it.isNullOrBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}
