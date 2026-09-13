package nep.timeline.cirno.ui.page

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.provide.BatteryOptimizationBinder
import nep.timeline.cirno.ui.app.KeyColors
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.app.UI_STYLE_MIUIX
import nep.timeline.cirno.ui.app.themeColorSpecLabel
import nep.timeline.cirno.ui.app.themePaletteStyleLabel
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.ConfigBackupZipUtils
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigRepository
import nep.timeline.cirno.ui.utils.RootConfigSaveScope
import nep.timeline.cirno.ui.utils.RootFreezerRepository
import nep.timeline.cirno.ui.utils.UiPrefs
import nep.timeline.cirno.ui.utils.UpdateChecker
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun hookTypeValue(label: String): String = when (label) {
    "Auto" -> GlobalSettings.HOOK_TYPE_AUTO
    "Millet" -> GlobalSettings.HOOK_TYPE_MILLET
    "Hans" -> GlobalSettings.HOOK_TYPE_HANS
    "Vivo" -> GlobalSettings.HOOK_TYPE_VIVO
    "Re-Kernel Kernel" -> GlobalSettings.HOOK_TYPE_REKERNEL
    "Re-Kernel eBPF" -> GlobalSettings.HOOK_TYPE_REKERNEL_EBPF
    "nkBinder" -> GlobalSettings.HOOK_TYPE_NKBINDER
    else -> label.lowercase()
}

internal fun hookTypeLabel(value: String): String = when (value) {
    GlobalSettings.HOOK_TYPE_AUTO -> "Auto"
    GlobalSettings.HOOK_TYPE_MILLET -> "Millet"
    GlobalSettings.HOOK_TYPE_HANS -> "Hans"
    GlobalSettings.HOOK_TYPE_VIVO -> "Vivo"
    GlobalSettings.HOOK_TYPE_REKERNEL -> "Re-Kernel Kernel"
    GlobalSettings.HOOK_TYPE_REKERNEL_EBPF -> "Re-Kernel eBPF"
    GlobalSettings.HOOK_TYPE_NKBINDER -> "nkBinder"
    else -> value
}

internal fun formatSpeedThreshold(bytesPerSec: Int): String {
    if (bytesPerSec < 1048576) return "${bytesPerSec / 1024} KB/s"
    return String.format("%.2f MB/s", bytesPerSec / 1048576.0)
}

internal fun batteryOptModeToIndex(mode: String): Int = when (mode) {
    GlobalSettings.BATTERY_OPT_MODE_ALL_USER_APPS -> 1
    GlobalSettings.BATTERY_OPT_MODE_CLEAR_USER_APPS -> 2
    else -> 0
}

internal fun indexToBatteryOptMode(index: Int): String = when (index) {
    1 -> GlobalSettings.BATTERY_OPT_MODE_ALL_USER_APPS
    2 -> GlobalSettings.BATTERY_OPT_MODE_CLEAR_USER_APPS
    else -> GlobalSettings.BATTERY_OPT_MODE_APP
}

internal fun memoryTrimLevelToIndex(level: Int): Int = when (level) {
    15 -> 0; 20 -> 1; 40 -> 2; 60 -> 3; 80 -> 4; else -> 3
}

internal fun indexToMemoryTrimLevel(index: Int): Int = when (index) {
    0 -> 15; 1 -> 20; 2 -> 40; 3 -> 60; 4 -> 80; else -> 60
}

internal fun logLevelToIndex(level: String): Int = when (level) {
    GlobalSettings.LOG_LEVEL_NONE -> 0
    GlobalSettings.LOG_LEVEL_DEBUG -> 2
    else -> 1
}

internal fun indexToLogLevel(index: Int): String = when (index) {
    0 -> GlobalSettings.LOG_LEVEL_NONE
    2 -> GlobalSettings.LOG_LEVEL_DEBUG
    else -> GlobalSettings.LOG_LEVEL_INFO
}

/**
 * 两套 UI（MiuiX / Material）设置页共享的状态与业务逻辑。
 *
 * toast 通道通过 [showToast] 参数化：MiuiX 页面传 WindowUtils.showToast，
 * Material 页面使用默认的 AppContext.showToast，保持两页原有提示通道不变。
 */
class SettingsStateHolder(
    context: Context,
    var globalSettings: GlobalSettings,
    private val showToast: (String) -> Unit = AppContext::showToast,
) {
    // ---- Hook 状态 ----
    var hookStatus by mutableStateOf<HookStatusRepository.HookStatusSnapshot?>(null)

    // ---- 冻结组（GlobalSettings 派生）----
    var freezeDelay by mutableFloatStateOf(globalSettings.freezeDelay.toFloat())
    var wakeFreezeDelay by mutableFloatStateOf(globalSettings.wakeFreezeDelay.toFloat())
    var networkSpeedThreshold by mutableFloatStateOf(globalSettings.networkSpeedThreshold.toFloat())
    var bootFreezeAll by mutableIntStateOf(if (globalSettings.bootFreezeAll) 1 else 0)
    var freezerModeIndex by mutableIntStateOf(if (globalSettings.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN) 1 else 0)
    var batteryOptimizationModeIndex by mutableIntStateOf(batteryOptModeToIndex(globalSettings.batteryOptimizationMode))

    // ---- 内存组 ----
    var compactionEnabled by mutableIntStateOf(if (globalSettings.compactionEnabled) 1 else 0)
    var compactionDelay by mutableFloatStateOf(globalSettings.compactionDelay.toFloat())
    var compactionThrottle by mutableFloatStateOf(globalSettings.compactionThrottle.toFloat())
    var memoryTrimEnabled by mutableIntStateOf(if (globalSettings.memoryTrimEnabled) 1 else 0)
    var memoryTrimDelay by mutableFloatStateOf(globalSettings.memoryTrimDelay.toFloat())
    var memoryTrimLevelIndex by mutableIntStateOf(memoryTrimLevelToIndex(globalSettings.memoryTrimLevel))
    var memoryTrimGcEnabled by mutableIntStateOf(if (globalSettings.memoryTrimGcEnabled) 1 else 0)
    var memoryTrimThrottle by mutableFloatStateOf(globalSettings.memoryTrimThrottle.toFloat())

    // ---- UI 偏好组（UiPrefs 派生）----
    var uiStyleIndex by mutableIntStateOf(UiPrefs.getUiStyle(context).coerceIn(UI_STYLE_MIUIX, UI_STYLE_MATERIAL))
    var navIndex by mutableIntStateOf(UiPrefs.getNavigationStyle(context).coerceIn(0, 2))
    var themeIndex by mutableIntStateOf(UiPrefs.getColorMode(context).coerceIn(0, 5))
    var keyColorIndex by mutableIntStateOf(UiPrefs.getThemeKeyColor(context).coerceIn(0, KeyColors.size))
    var colorSpecIndex by mutableIntStateOf(UiPrefs.getThemeColorSpec(context).coerceIn(0, ThemeColorSpec.entries.lastIndex))
    var paletteStyleIndex by mutableIntStateOf(UiPrefs.getThemePaletteStyle(context).coerceIn(0, ThemePaletteStyle.entries.lastIndex))
    var blurEnabled by mutableIntStateOf(if (UiPrefs.getBlur(context)) 1 else 0)

    // ---- 更新渠道 / 日志 ----
    var updateChannelIndex by mutableIntStateOf(if (UpdateChecker.getUpdateChannel(context) == UpdateChecker.CHANNEL_CI) 1 else 0)
    var showCiChannelConfirm by mutableStateOf(false)
    var levelIndex by mutableIntStateOf(logLevelToIndex(globalSettings.logLevel))

    suspend fun loadHookStatus() {
        hookStatus = withContext(Dispatchers.IO) {
            HookStatusRepository.loadHookStatusSnapshot()
        }
    }

    fun saveGlobalSettingsAsync(defaultError: String, onFailed: () -> Unit = {}) {
        RootConfigSaveScope.saveGlobalSettingsAsync(
            defaultError = defaultError,
            onFailed = onFailed,
        )
    }

    /** 恢复备份成功后：rebind globalSettings 并全量回同步（两套 UI 原逻辑的并集） */
    fun reloadAfterRestore(context: Context) {
        globalSettings = GlobalVars.globalSettings ?: globalSettings
        syncLocalStateFromSettings(context)
    }

    fun syncLocalStateFromSettings(context: Context) {
        freezeDelay = globalSettings.freezeDelay.toFloat()
        wakeFreezeDelay = globalSettings.wakeFreezeDelay.toFloat()
        networkSpeedThreshold = globalSettings.networkSpeedThreshold.toFloat()
        bootFreezeAll = if (globalSettings.bootFreezeAll) 1 else 0
        freezerModeIndex = if (globalSettings.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN) 1 else 0
        batteryOptimizationModeIndex = batteryOptModeToIndex(globalSettings.batteryOptimizationMode)
        uiStyleIndex = UiPrefs.getUiStyle(context).coerceIn(UI_STYLE_MIUIX, UI_STYLE_MATERIAL)
        levelIndex = logLevelToIndex(globalSettings.logLevel)
        compactionEnabled = if (globalSettings.compactionEnabled) 1 else 0
        compactionDelay = globalSettings.compactionDelay.toFloat()
        compactionThrottle = globalSettings.compactionThrottle.toFloat()
        memoryTrimEnabled = if (globalSettings.memoryTrimEnabled) 1 else 0
        memoryTrimDelay = globalSettings.memoryTrimDelay.toFloat()
        memoryTrimLevelIndex = memoryTrimLevelToIndex(globalSettings.memoryTrimLevel)
        memoryTrimGcEnabled = if (globalSettings.memoryTrimGcEnabled) 1 else 0
        memoryTrimThrottle = globalSettings.memoryTrimThrottle.toFloat()
    }

    fun hookTypeItems(): List<String> = buildList {
        add("Auto")
        hookStatus?.availableHookTypes?.let { addAll(it) }
    }

    fun onFreezerModeSelected(scope: CoroutineScope, index: Int, frozenUnavailableText: String, uidUnavailableText: String) {
        val previousMode = globalSettings.freezerMode
        val previousIndex = freezerModeIndex
        scope.launch {
            val (uidAvailable, frozenAvailable) = withContext(Dispatchers.IO) {
                RootFreezerRepository.isUidFreezerAvailable() to RootFreezerRepository.isFrozenFreezerAvailable()
            }
            val (mode, available) = when (index) {
                0 -> GlobalSettings.FREEZER_MODE_UID to uidAvailable
                1 -> GlobalSettings.FREEZER_MODE_FROZEN to frozenAvailable
                else -> return@launch
            }

            if (!available) {
                showToast(if (index == 1) frozenUnavailableText else uidUnavailableText)
                return@launch
            }

            freezerModeIndex = index
            globalSettings.freezerMode = mode
            saveGlobalSettingsAsync("冻结模式更新失败") {
                globalSettings.freezerMode = previousMode
                freezerModeIndex = previousIndex
            }
        }
    }

    fun onBatteryOptimizationModeSelected(scope: CoroutineScope, index: Int, errorText: String) {
        val previousMode = globalSettings.batteryOptimizationMode
        val previousIndex = batteryOptimizationModeIndex
        batteryOptimizationModeIndex = index
        globalSettings.batteryOptimizationMode = indexToBatteryOptMode(index)
        saveGlobalSettingsAsync(errorText) {
            globalSettings.batteryOptimizationMode = previousMode
            batteryOptimizationModeIndex = previousIndex
        }
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                BatteryOptimizationBinder.getInstance()?.syncBatteryOptimizationWhitelist() == true
            }
            if (!success) {
                globalSettings.batteryOptimizationMode = previousMode
                batteryOptimizationModeIndex = previousIndex
                showToast(errorText)
            }
        }
    }

    fun onHookTypeSelected(items: List<String>, index: Int, indexState: MutableIntState, errorText: String, restartText: String) {
        val selected = hookTypeValue(items[index])
        val previous = globalSettings.hookType
        val previousIndex = indexState.intValue
        indexState.intValue = index
        globalSettings.hookType = selected
        saveGlobalSettingsAsync(errorText) {
            globalSettings.hookType = previous
            indexState.intValue = previousIndex
        }
        showToast(restartText)
    }

    // ---- 滑条提交（onValueChangeFinished，含 coerce 与回滚；不回写滑条值，与原行为一致）----
    fun commitFreezeDelay() {
        val previous = globalSettings.freezeDelay
        globalSettings.freezeDelay = freezeDelay.toInt().coerceAtLeast(1)
        saveGlobalSettingsAsync("冻结延迟更新失败") {
            globalSettings.freezeDelay = previous
            freezeDelay = previous.toFloat()
        }
    }

    fun commitWakeFreezeDelay() {
        val previous = globalSettings.wakeFreezeDelay
        globalSettings.wakeFreezeDelay = wakeFreezeDelay.toInt().coerceIn(1, 120)
        saveGlobalSettingsAsync("唤醒冻结延迟更新失败") {
            globalSettings.wakeFreezeDelay = previous
            wakeFreezeDelay = previous.toFloat()
        }
    }

    fun commitNetworkSpeedThreshold() {
        val previous = globalSettings.networkSpeedThreshold
        globalSettings.networkSpeedThreshold = networkSpeedThreshold.toInt().coerceIn(102400, 2097152)
        saveGlobalSettingsAsync("网速识别阈值更新失败") {
            globalSettings.networkSpeedThreshold = previous
            networkSpeedThreshold = previous.toFloat()
        }
    }

    fun commitCompactionDelay() {
        val previous = globalSettings.compactionDelay
        globalSettings.compactionDelay = compactionDelay.toInt().coerceAtLeast(1)
        saveGlobalSettingsAsync("压缩延迟更新失败") {
            globalSettings.compactionDelay = previous
            compactionDelay = previous.toFloat()
        }
    }

    fun commitCompactionThrottle() {
        val previous = globalSettings.compactionThrottle
        globalSettings.compactionThrottle = compactionThrottle.toInt().coerceAtLeast(1)
        saveGlobalSettingsAsync("压缩节流更新失败") {
            globalSettings.compactionThrottle = previous
            compactionThrottle = previous.toFloat()
        }
    }

    fun commitMemoryTrimDelay() {
        val previous = globalSettings.memoryTrimDelay
        globalSettings.memoryTrimDelay = memoryTrimDelay.toInt().coerceAtLeast(1)
        saveGlobalSettingsAsync("回收延迟更新失败") {
            globalSettings.memoryTrimDelay = previous
            memoryTrimDelay = previous.toFloat()
        }
    }

    fun commitMemoryTrimThrottle() {
        val previous = globalSettings.memoryTrimThrottle
        globalSettings.memoryTrimThrottle = memoryTrimThrottle.toInt().coerceAtLeast(60)
        saveGlobalSettingsAsync("回收节流更新失败") {
            globalSettings.memoryTrimThrottle = previous
            memoryTrimThrottle = previous.toFloat()
        }
    }

    // ---- 开关（含回滚）----
    fun setBootFreezeAll(checked: Boolean) {
        val previous = globalSettings.bootFreezeAll
        bootFreezeAll = if (checked) 1 else 0
        globalSettings.bootFreezeAll = checked
        saveGlobalSettingsAsync("开机冻结更新失败") {
            globalSettings.bootFreezeAll = previous
            bootFreezeAll = if (previous) 1 else 0
        }
    }

    fun setCompactionEnabled(checked: Boolean) {
        val previous = globalSettings.compactionEnabled
        compactionEnabled = if (checked) 1 else 0
        globalSettings.compactionEnabled = checked
        saveGlobalSettingsAsync("压缩设置更新失败") {
            globalSettings.compactionEnabled = previous
            compactionEnabled = if (previous) 1 else 0
        }
    }

    fun setMemoryTrimEnabled(checked: Boolean) {
        val previous = globalSettings.memoryTrimEnabled
        memoryTrimEnabled = if (checked) 1 else 0
        globalSettings.memoryTrimEnabled = checked
        saveGlobalSettingsAsync("回收设置更新失败") {
            globalSettings.memoryTrimEnabled = previous
            memoryTrimEnabled = if (previous) 1 else 0
        }
    }

    fun setMemoryTrimGcEnabled(checked: Boolean) {
        val previous = globalSettings.memoryTrimGcEnabled
        memoryTrimGcEnabled = if (checked) 1 else 0
        globalSettings.memoryTrimGcEnabled = checked
        saveGlobalSettingsAsync("GC设置更新失败") {
            globalSettings.memoryTrimGcEnabled = previous
            memoryTrimGcEnabled = if (previous) 1 else 0
        }
    }

    // ---- 下拉（含回滚）----
    fun onMemoryTrimLevelSelected(index: Int) {
        val previousIndex = memoryTrimLevelIndex
        val previousLevel = globalSettings.memoryTrimLevel
        memoryTrimLevelIndex = index
        globalSettings.memoryTrimLevel = indexToMemoryTrimLevel(index)
        saveGlobalSettingsAsync("回收级别更新失败") {
            globalSettings.memoryTrimLevel = previousLevel
            memoryTrimLevelIndex = previousIndex
        }
    }

    fun onLogLevelSelected(index: Int) {
        val previous = globalSettings.logLevel
        levelIndex = index
        globalSettings.logLevel = indexToLogLevel(index)
        saveGlobalSettingsAsync("日志级别更新失败") {
            globalSettings.logLevel = previous
            levelIndex = logLevelToIndex(previous)
        }
    }

    // ---- 更新渠道 ----
    fun onUpdateChannelSelected(context: Context, index: Int) {
        if (index == 1) {
            showCiChannelConfirm = true
        } else {
            updateChannelIndex = index
            UpdateChecker.setUpdateChannel(context, UpdateChecker.CHANNEL_RELEASE)
        }
    }

    fun confirmCiChannel(context: Context) {
        showCiChannelConfirm = false
        updateChannelIndex = 1
        UpdateChecker.setUpdateChannel(context, UpdateChecker.CHANNEL_CI)
    }

    fun dismissCiChannelConfirm() {
        showCiChannelConfirm = false
    }
}

/** stringResource 派生的选项列表，重组时重建 */
class SettingsOptionLists(
    val freezerModeItems: List<String>,
    val batteryOptimizationModeItems: List<String>,
    val navItems: List<String>,
    val uiStyleItems: List<String>,
    val themeItems: List<String>,
    val keyColorItems: List<String>,
    val colorSpecItems: List<String>,
    val paletteStyleItems: List<String>,
    val updateChannelItems: List<String>,
    val levelItems: List<String>,
    val trimLevelItems: List<String>,
)

class SettingsScreenState(
    val holder: SettingsStateHolder,
    val lists: SettingsOptionLists,
)

@Composable
fun rememberSettingsScreenState(
    showToast: (String) -> Unit = AppContext::showToast,
): SettingsScreenState {
    val context = LocalContext.current
    val holder = remember {
        SettingsStateHolder(
            context = context,
            globalSettings = GlobalVars.globalSettings ?: GlobalSettings().also { GlobalVars.globalSettings = it },
            showToast = showToast,
        )
    }
    LaunchedEffect(Unit) {
        holder.loadHookStatus()
    }
    val lists = SettingsOptionLists(
        freezerModeItems = listOf(
            stringResource(R.string.freezer_mode_uid),
            stringResource(R.string.freezer_mode_frozen),
        ),
        batteryOptimizationModeItems = listOf(
            stringResource(R.string.battery_optimization_mode_app),
            stringResource(R.string.battery_optimization_mode_all_user_apps),
            stringResource(R.string.battery_optimization_mode_clear_user_apps),
        ),
        navItems = listOf(
            stringResource(R.string.normal),
            stringResource(R.string.floating),
            stringResource(R.string.apple_floating),
        ),
        uiStyleItems = listOf(
            stringResource(R.string.ui_style_miuix),
            stringResource(R.string.ui_style_material),
        ),
        themeItems = listOf(
            stringResource(R.string.theme_follow_system),
            stringResource(R.string.theme_light),
            stringResource(R.string.theme_dark),
            stringResource(R.string.theme_monet_system),
            stringResource(R.string.theme_monet_light),
            stringResource(R.string.theme_monet_dark),
        ),
        keyColorItems = listOf(stringResource(R.string.theme_key_color_default)) + KeyColors.map { it.first },
        colorSpecItems = ThemeColorSpec.entries.map(::themeColorSpecLabel),
        paletteStyleItems = ThemePaletteStyle.entries.map(::themePaletteStyleLabel),
        updateChannelItems = listOf(
            stringResource(R.string.update_channel_release),
            stringResource(R.string.update_channel_ci),
        ),
        levelItems = listOf(
            stringResource(R.string.log_close),
            stringResource(R.string.log_info),
            stringResource(R.string.log_debug),
        ),
        trimLevelItems = listOf(
            stringResource(R.string.trim_level_running_critical),
            stringResource(R.string.trim_level_ui_hidden),
            stringResource(R.string.trim_level_background),
            stringResource(R.string.trim_level_moderate),
            stringResource(R.string.trim_level_complete),
        ),
    )
    return SettingsScreenState(holder, lists)
}

/** Hook 类型下拉的选中 index，依赖 hookStatus 到达后重算，保留 remember(key) 语义 */
@Composable
fun rememberHookTypeIndex(holder: SettingsStateHolder, items: List<String>): MutableIntState {
    val snapshot = holder.hookStatus
    return remember(snapshot) {
        mutableIntStateOf(
            items.indexOfFirst {
                it == hookTypeLabel(holder.globalSettings.hookType)
            }.coerceAtLeast(0)
        )
    }
}

class SettingsBackupLaunchers(
    val backupFileName: String,
    val launchBackup: (String) -> Unit,
    val launchRestore: () -> Unit,
)

/** 备份/恢复 launcher 的业务逻辑，toast 与原实现一致走 AppContext.showToast */
@Composable
fun rememberSettingsBackupLaunchers(holder: SettingsStateHolder): SettingsBackupLaunchers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupSuccessText = stringResource(R.string.backup_success)
    val backupFailedText = stringResource(R.string.backup_failed)
    val restoreSuccessText = stringResource(R.string.restore_success)
    val restoreSuccessReloadFailedText = stringResource(R.string.restore_success_reload_failed)
    val restoreFailedApplyText = stringResource(R.string.restore_failed_apply)
    val restoreFailedOpenText = stringResource(R.string.restore_failed_open)
    val restoreFailedStructureText = stringResource(R.string.restore_failed_structure)
    val restoreFailedRequiredFilesText = stringResource(R.string.restore_failed_required_files)
    val restoreFailedJsonText = stringResource(R.string.restore_failed_json)
    val restoreFailedIoText = stringResource(R.string.restore_failed_io)
    val restoreFailedUnknownText = stringResource(R.string.restore_failed_unknown)

    val backupFileName = remember {
        val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        "cirno-config-backup-$time.zip"
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                val globalJson = RootConfigRepository.getGlobalSettingsJsonOrNull()
                val applicationJson = RootConfigRepository.getApplicationSettingsJsonOrNull()
                if (globalJson == null || applicationJson == null) {
                    return@withContext RootConfigRepository.getLastErrorOrDefault(backupFailedText)
                }
                try {
                    ConfigBackupZipUtils.writeBackupZip(context.contentResolver, uri, globalJson, applicationJson)
                    backupSuccessText
                } catch (_: Throwable) {
                    backupFailedText
                }
            }
            AppContext.showToast(message)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val (message, restored) = withContext(Dispatchers.IO) {
                try {
                    val restored = ConfigBackupZipUtils.readAndValidateBackupZip(context.contentResolver, uri)
                    val applied = RootConfigRepository.applySettingsJson(restored.globalJson, restored.applicationJson)
                    if (!applied) {
                        return@withContext RootConfigRepository.getLastErrorOrDefault(restoreFailedApplyText) to false
                    }
                    if (!RootConfigRepository.loadIntoMemory()) {
                        return@withContext restoreSuccessReloadFailedText to false
                    }
                    restoreSuccessText to true
                } catch (e: ConfigBackupZipUtils.RestoreException) {
                    when (e.error) {
                        ConfigBackupZipUtils.RestoreError.OPEN_INPUT_FAILED -> restoreFailedOpenText
                        ConfigBackupZipUtils.RestoreError.INVALID_ZIP_STRUCTURE -> restoreFailedStructureText
                        ConfigBackupZipUtils.RestoreError.MISSING_REQUIRED_FILES -> restoreFailedRequiredFilesText
                        ConfigBackupZipUtils.RestoreError.INVALID_JSON -> restoreFailedJsonText
                        ConfigBackupZipUtils.RestoreError.IO_ERROR -> restoreFailedIoText
                    } to false
                } catch (_: Throwable) {
                    restoreFailedUnknownText to false
                }
            }
            if (restored) {
                holder.reloadAfterRestore(context)
            }
            AppContext.showToast(message)
        }
    }

    return SettingsBackupLaunchers(
        backupFileName = backupFileName,
        launchBackup = backupLauncher::launch,
        launchRestore = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
    )
}
