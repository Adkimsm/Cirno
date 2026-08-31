package nep.timeline.cirno.ui.utils

import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.configs.ConfigManager
import nep.timeline.cirno.configs.ConfigManagerJson.ReadResult
import nep.timeline.cirno.configs.settings.ApplicationSettings
import nep.timeline.cirno.configs.settings.GlobalSettings

object RootConfigRepository {
    private var lastError: String = ""

    @Volatile
    private var loaded: Boolean = false

    // 读取配置会替换 GlobalVars 里的配置对象实例，持有旧实例的 UI 闭包之后的修改会写进一个
    // 已被丢弃的对象（用户看到"改了设置没生效"）。本进程加载过就不再重读，避免无谓替换。
    fun ensureLoadedIntoMemory(): Boolean {
        if (isLoaded()) {
            return true
        }
        synchronized(this) {
            if (isLoaded()) {
                return true
            }
            return loadIntoMemory()
        }
    }

    private fun isLoaded(): Boolean =
        loaded && GlobalVars.globalSettings != null && GlobalVars.applicationSettings != null

    fun loadIntoMemory(): Boolean {
        return try {
            if (ConfigManager.manager.readConfigSU() == ReadResult.MISSING) {
                ConfigManager.manager.saveConfigSU()
            }
            loaded = true
            lastError = ""
            true
        } catch (e: Throwable) {
            lastError = e.message ?: "读取配置失败"
            false
        }
    }

    fun saveGlobalSettingsFromMemory(): Boolean {
        // 直接落盘当前内存对象，不再走 toJson/fromJson 往返：
        // 往返会新建对象并替换 GlobalVars.globalSettings，令 UI 侧已捕获的引用失效
        GlobalVars.globalSettings = GlobalSettings.ensureInitialized(GlobalVars.globalSettings)
        return try {
            if (GlobalVars.globalSettings?.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN
                && !RootFreezerRepository.isFrozenFreezerAvailable()) {
                lastError = "创建 frozen/unfrozen cgroup 失败"
                return false
            }
            val ok = ConfigManager.manager.saveConfigSU()
            lastError = if (ok) "" else "更新全局配置失败"
            ok
        } catch (e: Throwable) {
            lastError = e.message ?: "更新全局配置失败"
            false
        }
    }

    fun saveApplicationSettingsFromMemory(): Boolean {
        GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(GlobalVars.applicationSettings)
        return try {
            val ok = ConfigManager.manager.saveConfigSU()
            lastError = if (ok) "" else "更新应用配置失败"
            ok
        } catch (e: Throwable) {
            lastError = e.message ?: "更新应用配置失败"
            false
        }
    }

    fun getGlobalSettingsJsonOrNull(): String? {
        return try {
            ConfigManager.manager.dumpGlobalSettingsJson()
        } catch (e: Throwable) {
            lastError = e.message ?: "读取全局配置失败"
            null
        }
    }

    fun getApplicationSettingsJsonOrNull(): String? {
        return try {
            ConfigManager.manager.dumpApplicationSettingsJson()
        } catch (e: Throwable) {
            lastError = e.message ?: "读取应用配置失败"
            null
        }
    }

    fun applySettingsJson(globalJson: String, applicationJson: String): Boolean {
        return try {
            val globalOk = ConfigManager.manager.applyGlobalSettingsJsonSU(globalJson)
            val applicationOk = ConfigManager.manager.applyApplicationSettingsJsonSU(applicationJson)
            val ok = globalOk && applicationOk
            lastError = if (ok) "" else "恢复配置失败"
            ok
        } catch (e: Throwable) {
            lastError = e.message ?: "恢复配置失败"
            false
        }
    }

    fun getLastErrorOrDefault(defaultMessage: String): String {
        return lastError.ifBlank { defaultMessage }
    }
}
