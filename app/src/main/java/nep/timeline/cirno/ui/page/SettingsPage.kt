@file:OptIn(ExperimentalScrollBarApi::class)

package nep.timeline.cirno.ui.page

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.ui.app.KeyColors
import nep.timeline.cirno.ui.app.LocalIsWideScreen
import nep.timeline.cirno.ui.app.LocalUpdateAppState
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.app.UI_STYLE_MIUIX
import nep.timeline.cirno.ui.app.themeColorSpecLabel
import nep.timeline.cirno.ui.app.themePaletteStyleLabel
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.ConfigBackupZipUtils
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigRepository
import nep.timeline.cirno.ui.utils.RootConfigSaveScope
import nep.timeline.cirno.ui.utils.RootFreezerRepository
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import nep.timeline.cirno.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun SettingsPage(
    active: Boolean,
    padding: PaddingValues,
    scrollEndHaptic: Boolean
) {
    val isWideScreen = LocalIsWideScreen.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop, blurActive, scrollBehavior) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings),
                    isWideScreen = isWideScreen,
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                )
            }
        }
    ) { innerPadding ->
        SettingsContent(
            active = active,
            padding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
            topAppBarScrollBehavior = scrollBehavior,
            backdrop = backdrop,
            scrollEndHaptic = scrollEndHaptic,
        )
    }
}

@Composable
private fun SettingsContent(
    active: Boolean,
    padding: PaddingValues,
    topAppBarScrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    backdrop: LayerBackdrop?,
    scrollEndHaptic: Boolean,
) {
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    val updateAppState = LocalUpdateAppState.current
    val lazyListState = rememberLazyListState()
    val contentPadding = pageContentPadding(padding, padding, isWideScreen)
    val scope = rememberCoroutineScope()
    var globalSettings = GlobalVars.globalSettings ?: GlobalSettings().also { GlobalVars.globalSettings = it }
    val backupSuccessText = stringResource(R.string.backup_success)
    val backupFailedText = stringResource(R.string.backup_failed)
    val customBackgroundUpdatedText = stringResource(R.string.custom_background_updated)
    val freezerModeFrozenUnavailableText = stringResource(R.string.freezer_mode_frozen_unavailable)
    val freezerModeUidUnavailableText = stringResource(R.string.freezer_mode_uid_unavailable)
    val customBackgroundUpdateFailedText = stringResource(R.string.custom_background_update_failed)
    val customBackgroundRemovedText = stringResource(R.string.custom_background_removed)
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
    val hookStatus = remember { mutableStateOf<HookStatusRepository.HookStatusSnapshot?>(null) }
    LaunchedEffect(Unit) {
        hookStatus.value = withContext(Dispatchers.IO) {
            HookStatusRepository.loadHookStatusSnapshot()
        }
    }
    val freezeDelay = remember { mutableFloatStateOf(globalSettings.freezeDelay.toFloat()) }
    val wakeFreezeDelay = remember { mutableFloatStateOf(globalSettings.wakeFreezeDelay.toFloat()) }
    val networkSpeedThreshold = remember { mutableFloatStateOf(globalSettings.networkSpeedThreshold.toFloat()) }
    val bootFreezeAll = remember { mutableIntStateOf(if (globalSettings.bootFreezeAll) 1 else 0) }
    val compactionEnabled = remember { mutableIntStateOf(if (globalSettings.compactionEnabled) 1 else 0) }
    val compactionDelay = remember { mutableFloatStateOf(globalSettings.compactionDelay.toFloat()) }
    val freezerModeItems = listOf(
        stringResource(R.string.freezer_mode_uid),
        stringResource(R.string.freezer_mode_frozen),
    )
    val navItems = listOf(
        stringResource(R.string.normal),
        stringResource(R.string.floating),
        stringResource(R.string.apple_floating),
    )
    val uiStyleItems = listOf(
        stringResource(R.string.ui_style_miuix),
        stringResource(R.string.ui_style_material),
    )
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
    val freezerModeIndex = remember {
        mutableIntStateOf(if (globalSettings.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN) 1 else 0)
    }
    val navIndex = remember { mutableIntStateOf(globalSettings.navigationStyle.coerceIn(0, 2)) }
    val themeIndex = remember { mutableIntStateOf(globalSettings.colorMode.coerceIn(0, 5)) }
    val keyColorIndex = remember { mutableIntStateOf(globalSettings.themeKeyColor.coerceIn(0, KeyColors.size)) }
    val colorSpecIndex = remember { mutableIntStateOf(globalSettings.themeColorSpec.coerceIn(0, ThemeColorSpec.entries.lastIndex)) }
    val paletteStyleIndex = remember { mutableIntStateOf(globalSettings.themePaletteStyle.coerceIn(0, ThemePaletteStyle.entries.lastIndex)) }
    val blurEnabled = remember { mutableIntStateOf(if (globalSettings.blurUI) 1 else 0) }
    val levelItems = listOf(
        stringResource(R.string.log_close),
        stringResource(R.string.log_info),
        stringResource(R.string.log_debug),
    )
    val levelIndex = remember {
        mutableIntStateOf(
            when (GlobalVars.globalSettings.logLevel) {
                GlobalSettings.LOG_LEVEL_NONE -> 0
                GlobalSettings.LOG_LEVEL_DEBUG -> 2
                else -> 1
            }
        )
    }
    val hasCustomBackground = BackgroundManager.currentUri != null

    fun saveGlobalSettingsAsync(defaultError: String, onFailed: () -> Unit) {
        RootConfigSaveScope.saveGlobalSettingsAsync(
            defaultError = defaultError,
            onFailed = onFailed,
        )
    }

    fun syncLocalStateFromSettings() {
        if (hasCustomBackground && globalSettings.blurUI) {
            globalSettings.blurUI = false
        }
        freezeDelay.floatValue = globalSettings.freezeDelay.toFloat()
        wakeFreezeDelay.floatValue = globalSettings.wakeFreezeDelay.toFloat()
        networkSpeedThreshold.floatValue = globalSettings.networkSpeedThreshold.toFloat()
        freezerModeIndex.intValue = if (globalSettings.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN) 1 else 0
        uiStyleIndex.intValue = globalSettings.uiStyle.coerceIn(UI_STYLE_MIUIX, UI_STYLE_MATERIAL)
        navIndex.intValue = globalSettings.navigationStyle.coerceIn(0, 2)
        themeIndex.intValue = globalSettings.colorMode.coerceIn(0, 5)
        keyColorIndex.intValue = globalSettings.themeKeyColor.coerceIn(0, KeyColors.size)
        colorSpecIndex.intValue = globalSettings.themeColorSpec.coerceIn(0, ThemeColorSpec.entries.lastIndex)
        paletteStyleIndex.intValue = globalSettings.themePaletteStyle.coerceIn(0, ThemePaletteStyle.entries.lastIndex)
        blurEnabled.intValue = if (globalSettings.blurUI) 1 else 0
        levelIndex.intValue = when (globalSettings.logLevel) {
            GlobalSettings.LOG_LEVEL_NONE -> 0
            GlobalSettings.LOG_LEVEL_DEBUG -> 2
            else -> 1
        }
        compactionEnabled.intValue = if (globalSettings.compactionEnabled) 1 else 0
        compactionDelay.floatValue = globalSettings.compactionDelay.toFloat()
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
                globalSettings = GlobalVars.globalSettings ?: globalSettings
                val shouldDisableBlur = hasCustomBackground && globalSettings.blurUI
                syncLocalStateFromSettings()
                if (shouldDisableBlur) {
                    saveGlobalSettingsAsync("模糊效果更新失败") {}
                }
                updateAppState { state ->
                    state.copy(
                        uiStyle = globalSettings.uiStyle,
                        navigationStyle = globalSettings.navigationStyle,
                        colorMode = globalSettings.colorMode,
                        themeKeyColor = globalSettings.themeKeyColor,
                        themeColorSpec = globalSettings.themeColorSpec,
                        themePaletteStyle = globalSettings.themePaletteStyle,
                        blur = globalSettings.blurUI && !hasCustomBackground,
                    )
                }
            }
            AppContext.showToast(message)
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                BackgroundManager.set(context, uri)
            }
            if (success && globalSettings.blurUI) {
                val previous = globalSettings.blurUI
                blurEnabled.intValue = 0
                globalSettings.blurUI = false
                updateAppState { state -> state.copy(blur = false) }
                saveGlobalSettingsAsync("模糊效果更新失败") {
                    globalSettings.blurUI = previous
                    blurEnabled.intValue = if (previous) 1 else 0
                    updateAppState { state -> state.copy(blur = previous) }
                }
            }
            AppContext.showToast(if (success) customBackgroundUpdatedText else customBackgroundUpdateFailedText)
        }
    }

    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(scrollEndHaptic, true, topAppBarScrollBehavior),
            contentPadding = contentPadding,
        ) {
            if (active) {
                item {
                    SmallTitle(text = stringResource(R.string.settings_freeze_group))
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.freezer_mode),
                            items = freezerModeItems,
                            selectedIndex = freezerModeIndex.intValue,
                            onSelectedIndexChange = {
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
                                        WindowUtils.showToast(
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
                        )
                        Text(
                            text = stringResource(R.string.interval_freeze_delay) + " | " + freezeDelay.floatValue.toInt() + " s",
                            modifier = Modifier.padding(17.dp),
                        )
                        Slider(
                            value = freezeDelay.floatValue,
                            onValueChange = {
                                freezeDelay.floatValue = it
                            },
                            onValueChangeFinished = {
                                val previous = globalSettings.freezeDelay
                                globalSettings.freezeDelay = freezeDelay.floatValue.toInt().coerceAtLeast(1)
                                saveGlobalSettingsAsync("冻结延迟更新失败") {
                                    globalSettings.freezeDelay = previous
                                    freezeDelay.floatValue = previous.toFloat()
                                }
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        )
                        Text(
                            text = stringResource(R.string.wake_freeze_delay) + " | " + wakeFreezeDelay.floatValue.toInt() + " s",
                            modifier = Modifier.padding(17.dp),
                        )
                        Slider(
                            value = wakeFreezeDelay.floatValue,
                            onValueChange = {
                                wakeFreezeDelay.floatValue = it
                            },
                            onValueChangeFinished = {
                                val previous = globalSettings.wakeFreezeDelay
                                globalSettings.wakeFreezeDelay = wakeFreezeDelay.floatValue.toInt().coerceIn(1,120)
                                saveGlobalSettingsAsync("唤醒冻结延迟更新失败") {
                                    globalSettings.wakeFreezeDelay = previous
                                    wakeFreezeDelay.floatValue = previous.toFloat()
                                }
                            },
                            valueRange = 1f..120f,
                            steps = 118,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        )
                        Text(
                            text = stringResource(R.string.network_speed_threshold) + " | " + formatSpeedThreshold(networkSpeedThreshold.floatValue.toInt()),
                            modifier = Modifier.padding(17.dp),
                        )
                        Slider(
                            value = networkSpeedThreshold.floatValue,
                            onValueChange = {
                                networkSpeedThreshold.floatValue = it
                            },
                            onValueChangeFinished = {
                                val previous = globalSettings.networkSpeedThreshold
                                globalSettings.networkSpeedThreshold = networkSpeedThreshold.floatValue.toInt().coerceIn(102400, 2097152)
                                saveGlobalSettingsAsync("网速识别阈值更新失败") {
                                    globalSettings.networkSpeedThreshold = previous
                                    networkSpeedThreshold.floatValue = previous.toFloat()
                                }
                            },
                            valueRange = 102400f..2097152f,
                            steps = 99,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        )
                        SwitchPreference(
                            title = stringResource(R.string.boot_freeze_all),
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
                            SwitchPreference(
                                title = stringResource(R.string.compaction_enabled),
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
                                Text(
                                    text = stringResource(R.string.compaction_delay) + " | " + compactionDelay.floatValue.toInt() + " s",
                                    modifier = Modifier.padding(17.dp),
                                )
                                Slider(
                                    value = compactionDelay.floatValue,
                                    onValueChange = {
                                        compactionDelay.floatValue = it
                                    },
                                    onValueChangeFinished = {
                                        val previous = globalSettings.compactionDelay
                                        globalSettings.compactionDelay = compactionDelay.floatValue.toInt().coerceAtLeast(1)
                                        saveGlobalSettingsAsync("压缩延迟更新失败") {
                                            globalSettings.compactionDelay = previous
                                            compactionDelay.floatValue = previous.toFloat()
                                        }
                                    },
                                    valueRange = 1f..30f,
                                    steps = 28,
                                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
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
                        OverlayDropdownPreference(
                            title = stringResource(R.string.hook_type),
                            items = hookTypeItems,
                            selectedIndex = hookTypeIndex.intValue,
                            onSelectedIndexChange = {
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
                        )
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.settings_ui_group))
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.ui_style),
                            items = uiStyleItems,
                            selectedIndex = uiStyleIndex.intValue,
                            onSelectedIndexChange = {
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
                        )

                        OverlayDropdownPreference(
                            title = stringResource(R.string.navigation_style),
                            items = navItems,
                            selectedIndex = navIndex.intValue,
                            onSelectedIndexChange = {
                                val previous = globalSettings.navigationStyle
                                navIndex.intValue = it
                                globalSettings.navigationStyle = it
                                updateAppState { state -> state.copy(navigationStyle = it) }
                                saveGlobalSettingsAsync("导航样式更新失败") {
                                    globalSettings.navigationStyle = previous
                                    navIndex.intValue = previous.coerceIn(0, 2)
                                    updateAppState { state -> state.copy(navigationStyle = previous) }
                                }
                            }
                        )

                        OverlayDropdownPreference(
                            title = stringResource(R.string.theme_mode),
                            items = themeItems,
                            selectedIndex = themeIndex.intValue,
                            onSelectedIndexChange = {
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
                        )

                        if (themeIndex.intValue in 3..5) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.theme_key_color),
                                items = keyColorItems,
                                selectedIndex = keyColorIndex.intValue,
                                onSelectedIndexChange = {
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
                            )

                            OverlayDropdownPreference(
                                title = stringResource(R.string.theme_color_spec),
                                items = colorSpecItems,
                                selectedIndex = colorSpecIndex.intValue,
                                onSelectedIndexChange = {
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
                            )

                            OverlayDropdownPreference(
                                title = stringResource(R.string.theme_palette_style),
                                items = paletteStyleItems,
                                selectedIndex = paletteStyleIndex.intValue,
                                onSelectedIndexChange = {
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
                            )
                        }

                        if (isRenderEffectSupported() && !hasCustomBackground) {
                            SwitchPreference(
                                title = stringResource(R.string.blur_ui),
                                summary = stringResource(R.string.blur_ui_desc),
                                checked = blurEnabled.intValue == 1,
                                onCheckedChange = {
                                    val previous = globalSettings.blurUI
                                    blurEnabled.intValue = if (it) 1 else 0
                                    globalSettings.blurUI = it
                                    updateAppState { state -> state.copy(blur = it) }
                                    saveGlobalSettingsAsync("模糊效果更新失败") {
                                        globalSettings.blurUI = previous
                                        blurEnabled.intValue = if (previous) 1 else 0
                                        updateAppState { state -> state.copy(blur = previous) }
                                    }
                                }
                            )
                        }

                        ArrowPreference(
                            title = stringResource(R.string.custom_background),
                            summary = stringResource(R.string.custom_background_desc),
                            onClick = {
                                backgroundLauncher.launch(arrayOf("image/*"))
                            }
                        )

                        if (BackgroundManager.currentUri != null) {
                            ArrowPreference(
                                title = stringResource(R.string.remove_custom_background),
                                onClick = {
                                    BackgroundManager.remove(context)
                                    AppContext.showToast(customBackgroundRemovedText)
                                }
                            )
                        }
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.settings_log_group))
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.log_level),
                            items = levelItems,
                            selectedIndex = levelIndex.intValue,
                            onSelectedIndexChange = {
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
                        )
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.settings_backup_group))
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        ArrowPreference(
                            title = stringResource(R.string.backup_config),
                            summary = stringResource(R.string.backup_config_desc),
                            onClick = {
                                backupLauncher.launch(backupFileName)
                            }
                        )

                        ArrowPreference(
                            title = stringResource(R.string.restore_config),
                            summary = stringResource(R.string.restore_config_desc),
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpeedThreshold(bytesPerSec: Int): String {
    if (bytesPerSec < 1048576) return "${bytesPerSec / 1024} KB/s"
    return String.format("%.2f MB/s", bytesPerSec / 1048576.0)
}
