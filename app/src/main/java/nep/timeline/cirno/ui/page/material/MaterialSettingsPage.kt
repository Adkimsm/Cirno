package nep.timeline.cirno.ui.page.material

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nep.timeline.cirno.BuildConfig
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.ui.app.LocalUpdateAppState
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.app.UI_STYLE_MIUIX
import nep.timeline.cirno.ui.app.KeyColors
import nep.timeline.cirno.ui.app.themeColorSpecLabel
import nep.timeline.cirno.ui.app.themePaletteStyleLabel
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.ConfigBackupZipUtils
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigRepository
import nep.timeline.cirno.ui.utils.RootConfigSaveScope
import nep.timeline.cirno.ui.utils.RootFreezerRepository
import nep.timeline.cirno.ui.utils.WindowUtils
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialSettingsPage(
    active: Boolean,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val updateAppState = LocalUpdateAppState.current
    val scope = rememberCoroutineScope()
    var globalSettings = GlobalVars.globalSettings ?: GlobalSettings().also { GlobalVars.globalSettings = it }

    val hookStatus = remember { mutableStateOf<HookStatusRepository.HookStatusSnapshot?>(null) }
    LaunchedEffect(Unit) {
        hookStatus.value = withContext(Dispatchers.IO) {
            HookStatusRepository.loadHookStatusSnapshot()
        }
    }

    val backupFailedText = stringResource(R.string.backup_failed)
    val freezerModeFrozenUnavailableText = stringResource(R.string.freezer_mode_frozen_unavailable)
    val freezerModeUidUnavailableText = stringResource(R.string.freezer_mode_uid_unavailable)
    val backupSuccessText = stringResource(R.string.backup_success)
    val restoreSuccessText = stringResource(R.string.restore_success)
    val restoreSuccessReloadFailedText = stringResource(R.string.restore_success_reload_failed)
    val restoreFailedApplyText = stringResource(R.string.restore_failed_apply)
    val restoreFailedOpenText = stringResource(R.string.restore_failed_open)
    val restoreFailedStructureText = stringResource(R.string.restore_failed_structure)
    val restoreFailedRequiredFilesText = stringResource(R.string.restore_failed_required_files)
    val restoreFailedJsonText = stringResource(R.string.restore_failed_json)
    val restoreFailedIoText = stringResource(R.string.restore_failed_io)
    val restoreFailedUnknownText = stringResource(R.string.restore_failed_unknown)

    val freezeDelay = remember { mutableFloatStateOf(globalSettings.freezeDelay.toFloat()) }
    val wakeFreezeDelay = remember { mutableFloatStateOf(globalSettings.wakeFreezeDelay.toFloat()) }
    val networkSpeedThreshold = remember { mutableFloatStateOf(globalSettings.networkSpeedThreshold.toFloat()) }
    val bootFreezeAll = remember { mutableIntStateOf(if (globalSettings.bootFreezeAll) 1 else 0) }
    val compactionEnabled = remember { mutableIntStateOf(if (globalSettings.compactionEnabled) 1 else 0) }
    val compactionDelay = remember { mutableFloatStateOf(globalSettings.compactionDelay.toFloat()) }
    val freezerModeItems = listOf(stringResource(R.string.freezer_mode_uid), stringResource(R.string.freezer_mode_frozen))
    val uiStyleItems = listOf(stringResource(R.string.ui_style_miuix), stringResource(R.string.ui_style_material))
    val themeItems = listOf(
        stringResource(R.string.theme_follow_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
        stringResource(R.string.theme_monet_system),
        stringResource(R.string.theme_monet_light),
        stringResource(R.string.theme_monet_dark),
    )
    val keyColorItems = listOf(stringResource(R.string.theme_key_color_default)) + KeyColors.map { it.first }
    val colorSpecItems = ThemeColorSpec.entries.map(::themeColorSpecLabel)
    val paletteStyleItems = ThemePaletteStyle.entries.map(::themePaletteStyleLabel)
    val uiStyleIndex = remember { mutableIntStateOf(globalSettings.uiStyle.coerceIn(UI_STYLE_MIUIX, UI_STYLE_MATERIAL)) }
    val freezerModeIndex = remember { mutableIntStateOf(if (globalSettings.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN) 1 else 0) }
    val themeIndex = remember { mutableIntStateOf(globalSettings.colorMode.coerceIn(0, 5)) }
    val keyColorIndex = remember { mutableIntStateOf(globalSettings.themeKeyColor.coerceIn(0, KeyColors.size)) }
    val colorSpecIndex = remember { mutableIntStateOf(globalSettings.themeColorSpec.coerceIn(0, ThemeColorSpec.entries.lastIndex)) }
    val paletteStyleIndex = remember { mutableIntStateOf(globalSettings.themePaletteStyle.coerceIn(0, ThemePaletteStyle.entries.lastIndex)) }
    val levelItems = listOf(stringResource(R.string.log_close), stringResource(R.string.log_info), stringResource(R.string.log_debug))
    val levelIndex = remember {
        mutableIntStateOf(
            when (GlobalVars.globalSettings.logLevel) {
                GlobalSettings.LOG_LEVEL_NONE -> 0
                GlobalSettings.LOG_LEVEL_DEBUG -> 2
                else -> 1
            }
        )
    }
    fun saveGlobalSettingsAsync(defaultError: String, onFailed: () -> Unit) {
        RootConfigSaveScope.saveGlobalSettingsAsync(
            defaultError = defaultError,
            onFailed = onFailed,
        )
    }

    fun syncLocalStateFromSettings() {
        freezeDelay.floatValue = globalSettings.freezeDelay.toFloat()
        wakeFreezeDelay.floatValue = globalSettings.wakeFreezeDelay.toFloat()
        networkSpeedThreshold.floatValue = globalSettings.networkSpeedThreshold.toFloat()
        bootFreezeAll.intValue = if (globalSettings.bootFreezeAll) 1 else 0
        compactionEnabled.intValue = if (globalSettings.compactionEnabled) 1 else 0
        compactionDelay.floatValue = globalSettings.compactionDelay.toFloat()
        freezerModeIndex.intValue = if (globalSettings.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN) 1 else 0
        uiStyleIndex.intValue = globalSettings.uiStyle.coerceIn(UI_STYLE_MIUIX, UI_STYLE_MATERIAL)
        themeIndex.intValue = globalSettings.colorMode.coerceIn(0, 5)
        keyColorIndex.intValue = globalSettings.themeKeyColor.coerceIn(0, KeyColors.size)
        colorSpecIndex.intValue = globalSettings.themeColorSpec.coerceIn(0, ThemeColorSpec.entries.lastIndex)
        paletteStyleIndex.intValue = globalSettings.themePaletteStyle.coerceIn(0, ThemePaletteStyle.entries.lastIndex)
        levelIndex.intValue = when (globalSettings.logLevel) {
            GlobalSettings.LOG_LEVEL_NONE -> 0
            GlobalSettings.LOG_LEVEL_DEBUG -> 2
            else -> 1
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
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

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (message, restored) = withContext(Dispatchers.IO) {
                try {
                    val restored = ConfigBackupZipUtils.readAndValidateBackupZip(context.contentResolver, uri)
                    val applied = RootConfigRepository.applySettingsJson(restored.globalJson, restored.applicationJson)
                    if (!applied) return@withContext RootConfigRepository.getLastErrorOrDefault(restoreFailedApplyText) to false
                    if (!RootConfigRepository.loadIntoMemory()) return@withContext restoreSuccessReloadFailedText to false
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
                globalSettings = GlobalVars.globalSettings ?: globalSettings
                syncLocalStateFromSettings()
                updateAppState { state ->
                    state.copy(
                        uiStyle = globalSettings.uiStyle,
                        navigationStyle = globalSettings.navigationStyle,
                        colorMode = globalSettings.colorMode,
                        themeKeyColor = globalSettings.themeKeyColor,
                        themeColorSpec = globalSettings.themeColorSpec,
                        themePaletteStyle = globalSettings.themePaletteStyle,
                        blur = globalSettings.blurUI,
                    )
                }
            }
            AppContext.showToast(message)
        }
    }

    MaterialPageScaffold(
        title = stringResource(R.string.settings),
        padding = padding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (active) {
            item {
                MaterialSettingsSection(title = stringResource(R.string.settings_freeze_group)) {
                    MaterialDropdownItem(Icons.Outlined.Update, stringResource(R.string.freezer_mode), freezerModeItems, freezerModeIndex.intValue) {
                        val previousMode = globalSettings.freezerMode
                        val previousIndex = freezerModeIndex.intValue
                        scope.launch {
                            val (uidAvailable, frozenAvailable) = withContext(Dispatchers.IO) {
                                RootFreezerRepository.isUidFreezerAvailable() to RootFreezerRepository.isFrozenFreezerAvailable()
                            }
                            val (mode, available) = when (it) {
                                0 -> GlobalSettings.FREEZER_MODE_UID to uidAvailable
                                1 -> GlobalSettings.FREEZER_MODE_FROZEN to frozenAvailable
                                else -> return@launch
                            }

                            if (!available) {
                                AppContext.showToast(
                                    if (it == 1) freezerModeFrozenUnavailableText
                                    else freezerModeUidUnavailableText
                                )
                                return@launch
                            }

                            freezerModeIndex.intValue = it
                            globalSettings.freezerMode = mode
                            saveGlobalSettingsAsync("冻结模式更新失败") {
                                globalSettings.freezerMode = previousMode
                                freezerModeIndex.intValue = previousIndex
                            }
                        }
                    }
                    MaterialSliderItem(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.interval_freeze_delay),
                        valueText = "${freezeDelay.floatValue.toInt()} s",
                        value = freezeDelay.floatValue,
                        valueRange = 1f..30f,
                        steps = 28,
                        onValueChange = { freezeDelay.floatValue = it },
                        onValueFinished = {
                            val previous = globalSettings.freezeDelay
                            globalSettings.freezeDelay = freezeDelay.floatValue.toInt().coerceAtLeast(1)
                            saveGlobalSettingsAsync("冻结延迟更新失败") {
                                globalSettings.freezeDelay = previous
                                freezeDelay.floatValue = previous.toFloat()
                            }
                        },
                    )
                    MaterialSliderItem(
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.wake_freeze_delay),
                        valueText = "${wakeFreezeDelay.floatValue.toInt()} s",
                        value = wakeFreezeDelay.floatValue,
                        valueRange = 1f..120f,
                        steps = 118,
                        onValueChange = { wakeFreezeDelay.floatValue = it },
                        onValueFinished = {
                            val previous = globalSettings.wakeFreezeDelay
                            globalSettings.wakeFreezeDelay = wakeFreezeDelay.floatValue.toInt().coerceIn(1, 120)
                            saveGlobalSettingsAsync("唤醒冻结延迟更新失败") {
                                globalSettings.wakeFreezeDelay = previous
                                wakeFreezeDelay.floatValue = previous.toFloat()
                            }
                        },
                    )
                    MaterialSliderItem(
                        icon = Icons.Outlined.Speed,
                        title = stringResource(R.string.network_speed_threshold),
                        valueText = materialFormatSpeedThreshold(networkSpeedThreshold.floatValue.toInt()),
                        value = networkSpeedThreshold.floatValue,
                        valueRange = 102400f..2097152f,
                        steps = 99,
                        onValueChange = { networkSpeedThreshold.floatValue = it },
                        onValueFinished = {
                            val previous = globalSettings.networkSpeedThreshold
                            globalSettings.networkSpeedThreshold = networkSpeedThreshold.floatValue.toInt().coerceIn(102400, 2097152)
                            saveGlobalSettingsAsync("网速识别阈值更新失败") {
                                globalSettings.networkSpeedThreshold = previous
                                networkSpeedThreshold.floatValue = previous.toFloat()
                            }
                        },
                    )
                    MaterialSwitchItem(
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.boot_freeze_all),
                        summary = null,
                        checked = bootFreezeAll.intValue == 1,
                        onCheckedChange = {
                            val previous = globalSettings.bootFreezeAll
                            bootFreezeAll.intValue = if (it) 1 else 0
                            globalSettings.bootFreezeAll = it
                            saveGlobalSettingsAsync("开机冻结更新失败") {
                                globalSettings.bootFreezeAll = previous
                                bootFreezeAll.intValue = if (previous) 1 else 0
                            }
                         }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        MaterialSwitchItem(
                            icon = Icons.Outlined.FilterList,
                            title = stringResource(R.string.compaction_enabled),
                            summary = null,
                            checked = compactionEnabled.intValue == 1,
                            onCheckedChange = {
                                val previous = globalSettings.compactionEnabled
                                compactionEnabled.intValue = if (it) 1 else 0
                                globalSettings.compactionEnabled = it
                                saveGlobalSettingsAsync("压缩设置更新失败") {
                                    globalSettings.compactionEnabled = previous
                                    compactionEnabled.intValue = if (previous) 1 else 0
                                }
                            }
                        )
                        if (compactionEnabled.intValue == 1) {
                            MaterialSliderItem(
                                icon = Icons.Outlined.Timer,
                                title = stringResource(R.string.compaction_delay),
                                valueText = "${compactionDelay.floatValue.toInt()} s",
                                value = compactionDelay.floatValue,
                                valueRange = 1f..30f,
                                steps = 28,
                                onValueChange = { compactionDelay.floatValue = it },
                                onValueFinished = {
                                    val previous = globalSettings.compactionDelay
                                    globalSettings.compactionDelay = compactionDelay.floatValue.toInt().coerceAtLeast(1)
                                    saveGlobalSettingsAsync("压缩延迟更新失败") {
                                        globalSettings.compactionDelay = previous
                                        compactionDelay.floatValue = previous.toFloat()
                                    }
                                },
                            )
                        }
                    }
                    val snapshot = hookStatus.value
                    val hookTypeItems = buildList {
                        add("Auto")
                        snapshot?.availableHookTypes?.let { addAll(it) }
                    }
                    val hookTypeIndex = remember(snapshot) {
                        mutableIntStateOf(
                            hookTypeItems.indexOfFirst {
                                it.equals(globalSettings.hookType, ignoreCase = true)
                            }.coerceAtLeast(0)
                        )
                    }
                    val hookTypeErrorText = stringResource(R.string.error)
                    val hookTypeRestartText = stringResource(R.string.hook_type_changed_restart)
                    MaterialDropdownItem(Icons.Outlined.Update, stringResource(R.string.hook_type), hookTypeItems, hookTypeIndex.intValue) {
                        val selected = hookTypeItems[it].lowercase()
                        val previous = globalSettings.hookType
                        val previousIndex = hookTypeIndex.intValue
                        hookTypeIndex.intValue = it
                        globalSettings.hookType = selected
                        saveGlobalSettingsAsync(hookTypeErrorText) {
                            globalSettings.hookType = previous
                            hookTypeIndex.intValue = previousIndex
                        }
                        WindowUtils.showToast(hookTypeRestartText)
                    }
                }
            }
            item {
                MaterialSettingsSection(title = stringResource(R.string.settings_ui_group)) {
                    MaterialDropdownItem(Icons.Outlined.Dashboard, stringResource(R.string.ui_style), uiStyleItems, uiStyleIndex.intValue) {
                        val previous = globalSettings.uiStyle
                        uiStyleIndex.intValue = it
                        globalSettings.uiStyle = it
                        RootConfigSaveScope.saveGlobalSettingsAndThen(
                            defaultError = "界面风格更新失败",
                            onSuccess = {
                                updateAppState { state -> state.copy(uiStyle = it) }
                            },
                            onFailed = {
                                globalSettings.uiStyle = previous
                                uiStyleIndex.intValue = previous.coerceIn(UI_STYLE_MIUIX, UI_STYLE_MATERIAL)
                                updateAppState { state -> state.copy(uiStyle = previous) }
                            },
                        )
                    }
                    MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_mode), themeItems, themeIndex.intValue) {
                        val previous = globalSettings.colorMode
                        themeIndex.intValue = it
                        globalSettings.colorMode = it
                        updateAppState { state -> state.copy(colorMode = it) }
                        saveGlobalSettingsAsync("主题模式更新失败") {
                            globalSettings.colorMode = previous
                            themeIndex.intValue = previous.coerceIn(0, 5)
                            updateAppState { state -> state.copy(colorMode = previous) }
                        }
                    }
                    if (themeIndex.intValue in 3..5) {
                        MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_key_color), keyColorItems, keyColorIndex.intValue) {
                            val previous = globalSettings.themeKeyColor
                            keyColorIndex.intValue = it
                            globalSettings.themeKeyColor = it
                            updateAppState { state -> state.copy(themeKeyColor = it) }
                            saveGlobalSettingsAsync("主题强调色更新失败") {
                                globalSettings.themeKeyColor = previous
                                keyColorIndex.intValue = previous.coerceIn(0, KeyColors.size)
                                updateAppState { state -> state.copy(themeKeyColor = previous) }
                            }
                        }
                        MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_color_spec), colorSpecItems, colorSpecIndex.intValue) {
                            val previous = globalSettings.themeColorSpec
                            colorSpecIndex.intValue = it
                            globalSettings.themeColorSpec = it
                            updateAppState { state -> state.copy(themeColorSpec = it) }
                            saveGlobalSettingsAsync("主题色彩标准更新失败") {
                                globalSettings.themeColorSpec = previous
                                colorSpecIndex.intValue = previous.coerceIn(0, ThemeColorSpec.entries.lastIndex)
                                updateAppState { state -> state.copy(themeColorSpec = previous) }
                            }
                        }
                        MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_palette_style), paletteStyleItems, paletteStyleIndex.intValue) {
                            val previous = globalSettings.themePaletteStyle
                            paletteStyleIndex.intValue = it
                            globalSettings.themePaletteStyle = it
                            updateAppState { state -> state.copy(themePaletteStyle = it) }
                            saveGlobalSettingsAsync("主题色彩风格更新失败") {
                                globalSettings.themePaletteStyle = previous
                                paletteStyleIndex.intValue = previous.coerceIn(0, ThemePaletteStyle.entries.lastIndex)
                                updateAppState { state -> state.copy(themePaletteStyle = previous) }
                            }
                        }
                    }
                }
            }
        }

        item {
            MaterialSettingsSection(title = stringResource(R.string.settings_log_group)) {
                MaterialDropdownItem(Icons.Outlined.BugReport, stringResource(R.string.log_level), levelItems, levelIndex.intValue) {
                    val previous = globalSettings.logLevel
                    levelIndex.intValue = it
                    globalSettings.logLevel = when (it) {
                        0 -> GlobalSettings.LOG_LEVEL_NONE
                        2 -> GlobalSettings.LOG_LEVEL_DEBUG
                        else -> GlobalSettings.LOG_LEVEL_INFO
                    }
                    saveGlobalSettingsAsync("日志级别更新失败") {
                        globalSettings.logLevel = previous
                        levelIndex.intValue = when (previous) {
                            GlobalSettings.LOG_LEVEL_NONE -> 0
                            GlobalSettings.LOG_LEVEL_DEBUG -> 2
                            else -> 1
                        }
                    }
                }
            }
        }

        if (active) {
            item {
                MaterialSettingsSection(title = stringResource(R.string.settings_backup_group)) {
                    MaterialActionItem(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.backup_config),
                        summary = stringResource(R.string.backup_config_desc),
                        onClick = { backupLauncher.launch("cirno-config-backup.zip") },
                    )
                    MaterialActionItem(
                        icon = Icons.Outlined.Restore,
                        title = stringResource(R.string.restore_config),
                        summary = stringResource(R.string.restore_config_desc),
                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    )
                }
            }
        }
    }
}

private fun materialFormatSpeedThreshold(bytesPerSec: Int): String {
    if (bytesPerSec < 1048576) return "${bytesPerSec / 1024} KB/s"
    return String.format("%.2f MB/s", bytesPerSec / 1048576.0)
}
