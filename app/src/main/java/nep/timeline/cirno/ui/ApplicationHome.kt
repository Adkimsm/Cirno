@file:OptIn(ExperimentalScrollBarApi::class)

package nep.timeline.cirno.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nep.timeline.cirno.ApplicationActivity
import nep.timeline.cirno.CommonConstants
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.provide.ApplicationBinder
import nep.timeline.cirno.utils.PKGUtils
import nep.timeline.cirno.ui.custom.BackNavigationIcon
import nep.timeline.cirno.ui.page.BackgroundOomAdjCustomDialog
import nep.timeline.cirno.ui.page.backgroundOomAdjForPresetIndex
import nep.timeline.cirno.ui.page.backgroundOomAdjItems
import nep.timeline.cirno.ui.page.backgroundOomAdjSelectedIndex
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.LocalImageBackdrop
import nep.timeline.cirno.ui.utils.MiuixBackground
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigSaveScope
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import nep.timeline.cirno.ui.utils.rememberBlurBackdrop
import nep.timeline.cirno.ui.utils.shouldShowSplitPane
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ApplicationHome(activity: ApplicationActivity) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val isWideScreen = shouldShowSplitPane()
    val appName = activity.intent.getStringExtra("appName") ?: "App"
    val packageName = activity.intent.getStringExtra("packageName") ?: return
    val userId = activity.intent.getStringExtra("userId")?.toIntOrNull() ?: 0
    val packetAvailable = remember { mutableStateOf<Boolean?>(null) }
    val isBuiltinWhitelistApp = CommonConstants.isWhitelistApps(packageName)
    val builtinWhitelistSummary = stringResource(R.string.builtin_whitelist_summary)
    val whitelistExemptionBlocked = stringResource(R.string.whitelist_exemption_blocked)
    val globalSettings = GlobalVars.globalSettings
    val isSystemApp = remember {
        try {
            val packageInfo = activity.packageManager.getPackageInfo(packageName, 0)
            PKGUtils.isSystemApp(packageInfo.applicationInfo)
        } catch (_: Throwable) {
            false
        }
    }

    val processList = remember { mutableStateListOf<String>() }
    val processExclusions = remember { mutableStateListOf<String>() }
    val processListLoaded = remember { mutableStateOf(false) }
    var processQuery by rememberSaveable { mutableStateOf("") }
    var processSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val processBehaviors = context.resources.getStringArray(R.array.process_behaviors).toList()
    val black = remember { mutableStateOf(AppConfigs.isBlackApp(packageName, userId)) }
    val white = remember { mutableStateOf(AppConfigs.isWhiteApp(packageName, userId)) }
    val userWhitelist = remember { mutableStateOf(AppConfigs.hasUserWhitelist(packageName, userId)) }
    val backgroundOomAdj = remember { mutableStateOf(AppConfigs.getBackgroundOomAdj(packageName, userId)) }
    val showBackgroundOomAdjCustomDialog = remember { mutableStateOf(false) }
    val backgroundOomAdjUpdateFailed = stringResource(R.string.background_oom_level_update_failed)

    fun saveApplicationSettingsAsync(defaultError: String = "配置更新失败", onFailed: (String) -> Unit = {}) {
        RootConfigSaveScope.saveApplicationSettingsAsync(
            defaultError = defaultError,
            onFailed = onFailed,
        )
    }

    LaunchedEffect(packageName, userId) {
        val (names, excluded) = withContext(Dispatchers.IO) {
            val processNames = mutableListOf<String>()
            val appBinder = ApplicationBinder.getInstance()
            if (appBinder != null) {
                try {
                    val json = appBinder.getProcessesForApp(packageName, userId)
                    val type = object : TypeToken<List<String>>() {}.type
                    val parsed: List<String> = Gson().fromJson(json, type) ?: emptyList()
                    processNames.addAll(parsed)
                } catch (_: Throwable) {
                }
            }
            processNames to AppConfigs.getExcludedProcesses(packageName, userId)
        }
        processList.clear()
        processList.addAll(names.sorted())
        processExclusions.clear()
        processExclusions.addAll(excluded)
        processListLoaded.value = true
    }

    LaunchedEffect(Unit) {
        packetAvailable.value = withContext(Dispatchers.IO) {
            HookStatusRepository.isPacketAvailable()
        }
    }

    if (showBackgroundOomAdjCustomDialog.value) {
        BackgroundOomAdjCustomDialog(
            initialAdj = backgroundOomAdj.value,
            onDismissRequest = { showBackgroundOomAdjCustomDialog.value = false },
            onConfirm = { adj ->
                val previous = backgroundOomAdj.value
                showBackgroundOomAdjCustomDialog.value = false
                backgroundOomAdj.value = adj
                AppConfigs.setBackgroundOomAdj(packageName, userId, adj)
                saveApplicationSettingsAsync(backgroundOomAdjUpdateFailed) { error ->
                    backgroundOomAdj.value = previous
                    AppConfigs.setBackgroundOomAdj(packageName, userId, previous)
                    WindowUtils.showToast(error)
                }
            },
        )
    }

    val backdrop = rememberBlurBackdrop(globalSettings.blurUI, true)
    val imageBackdrop = if (globalSettings.blurUI && BackgroundManager.currentUri != null && isRenderEffectSupported()) {
        rememberLayerBackdrop { drawContent() }
    } else null
    val blurActive = imageBackdrop != null || backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    CompositionLocalProvider(LocalImageBackdrop provides imageBackdrop) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BlurredBar(backdrop, blurActive, scrollBehavior) {
                    AdaptiveTopAppBar(
                        title = appName,
                        isWideScreen = isWideScreen,
                        scrollBehavior = scrollBehavior,
                        color = barColor,
                        navigationIcon = {
                            BackNavigationIcon(onClick = { activity.finish() })
                        }
                    )
                }
            }
        ) { padding ->
            val lazyListState = rememberLazyListState()
            val contentPadding = pageContentPadding(padding, padding, isWideScreen)
            MiuixBackground(imageBackdrop = imageBackdrop) {
                Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                    LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(true, true, scrollBehavior),
                contentPadding = contentPadding,
            ) {
                item {
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        val backgroundPlay = remember { mutableStateOf(AppConfigs.isBackgroundPlayAllowed(packageName, userId)) }
                        val locationUse = remember { mutableStateOf(AppConfigs.isLocationUseAllowed(packageName, userId)) }
                        val networkMessage = remember { mutableStateOf(AppConfigs.isNetworkMessageAllowed(packageName, userId)) }
                        val networkSpeed = remember { mutableStateOf(AppConfigs.isNetworkSpeedAllowed(packageName, userId)) }
                        val blockAutostart = remember { mutableStateOf(AppConfigs.isAutostartBlocked(packageName, userId)) }
                        val memoryTrimEnabled = remember { mutableStateOf(AppConfigs.isMemoryTrimEnabled(packageName, userId)) }
                        val memoryTrimGcEnabled = remember { mutableStateOf(AppConfigs.isMemoryTrimGcEnabled(packageName, userId)) }

                        if (packetAvailable.value == false && networkMessage.value) {
                            networkMessage.value = false
                            AppConfigs.setNetworkMessageAllowed(packageName, userId, false)
                            saveApplicationSettingsAsync()
                        }

                        if (!isSystemApp) {
                            SwitchPreference(
                                title = stringResource(R.string.white_app),
                                summary = if (isBuiltinWhitelistApp) builtinWhitelistSummary else null,
                                checked = isBuiltinWhitelistApp || white.value,
                                enabled = !isBuiltinWhitelistApp,
                                onCheckedChange = {
                                    if (isBuiltinWhitelistApp) return@SwitchPreference
                                    val prevWhite = white.value
                                    val prevUserWhite = userWhitelist.value
                                    val prevBackground = backgroundPlay.value
                                    val prevLocation = locationUse.value
                                    val prevNetwork = networkMessage.value
                                    val prevNetworkSpeed = networkSpeed.value

                                    white.value = it
                                    userWhitelist.value = it
                                    AppConfigs.setWhiteApp(packageName, userId, it)
                                    if (it) {
                                        backgroundPlay.value = false
                                        AppConfigs.setBackgroundPlayAllowed(packageName, userId, false)
                                        locationUse.value = false
                                        AppConfigs.setLocationUseAllowed(packageName, userId, false)
                                        networkMessage.value = false
                                        AppConfigs.setNetworkMessageAllowed(packageName, userId, false)
                                        networkSpeed.value = false
                                        AppConfigs.setNetworkSpeedAllowed(packageName, userId, false)
                                    }

                                    saveApplicationSettingsAsync("白名单更新失败") { error ->
                                        white.value = prevWhite
                                        userWhitelist.value = prevUserWhite
                                        AppConfigs.setWhiteApp(packageName, userId, prevWhite)
                                        backgroundPlay.value = prevBackground
                                        AppConfigs.setBackgroundPlayAllowed(packageName, userId, prevBackground)
                                        locationUse.value = prevLocation
                                        AppConfigs.setLocationUseAllowed(packageName, userId, prevLocation)
                                        networkMessage.value = prevNetwork
                                        AppConfigs.setNetworkMessageAllowed(packageName, userId, prevNetwork)
                                        networkSpeed.value = prevNetworkSpeed
                                        AppConfigs.setNetworkSpeedAllowed(packageName, userId, prevNetworkSpeed)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )
                        }

                        if (!isBuiltinWhitelistApp && (!isSystemApp || black.value)) {
                            SwitchPreference(
                                title = stringResource(R.string.background_play),
                                checked = backgroundPlay.value,
                                enabled = !userWhitelist.value,
                                onCheckedChange = {
                                    if (userWhitelist.value && it) {
                                        WindowUtils.showToast(whitelistExemptionBlocked)
                                        return@SwitchPreference
                                    }
                                    val previous = backgroundPlay.value
                                    backgroundPlay.value = it
                                    AppConfigs.setBackgroundPlayAllowed(packageName, userId, it)
                                    saveApplicationSettingsAsync("后台播放配置更新失败") { error ->
                                        backgroundPlay.value = previous
                                        AppConfigs.setBackgroundPlayAllowed(packageName, userId, previous)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )

                            SwitchPreference(
                                title = stringResource(R.string.location_check),
                                checked = locationUse.value,
                                enabled = !userWhitelist.value,
                                onCheckedChange = {
                                    if (userWhitelist.value && it) {
                                        WindowUtils.showToast(whitelistExemptionBlocked)
                                        return@SwitchPreference
                                    }
                                    val previous = locationUse.value
                                    locationUse.value = it
                                    AppConfigs.setLocationUseAllowed(packageName, userId, it)
                                    saveApplicationSettingsAsync("定位配置更新失败") { error ->
                                        locationUse.value = previous
                                        AppConfigs.setLocationUseAllowed(packageName, userId, previous)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )

                            SwitchPreference(
                                title = stringResource(R.string.netreceive_unfreeze),
                                summary = if (packetAvailable.value == true) null else stringResource(R.string.packet_required_summary),
                                checked = networkMessage.value,
                                enabled = packetAvailable.value == true && !userWhitelist.value,
                                onCheckedChange = {
                                    if (userWhitelist.value && it) {
                                        WindowUtils.showToast(whitelistExemptionBlocked)
                                        return@SwitchPreference
                                    }
                                    val previous = networkMessage.value
                                    networkMessage.value = it
                                    AppConfigs.setNetworkMessageAllowed(packageName, userId, it)
                                    saveApplicationSettingsAsync("网络消息配置更新失败") { error ->
                                        networkMessage.value = previous
                                        AppConfigs.setNetworkMessageAllowed(packageName, userId, previous)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )

                            SwitchPreference(
                                title = stringResource(R.string.network_speed_check),
                                checked = networkSpeed.value,
                                enabled = !userWhitelist.value,
                                onCheckedChange = {
                                    if (userWhitelist.value && it) {
                                        WindowUtils.showToast(whitelistExemptionBlocked)
                                        return@SwitchPreference
                                    }
                                    val previous = networkSpeed.value
                                    networkSpeed.value = it
                                    AppConfigs.setNetworkSpeedAllowed(packageName, userId, it)
                                    saveApplicationSettingsAsync("网速识别配置更新失败") { error ->
                                        networkSpeed.value = previous
                                        AppConfigs.setNetworkSpeedAllowed(packageName, userId, previous)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )
                        }

                        SwitchPreference(
                            title = stringResource(R.string.block_autostart),
                            checked = blockAutostart.value,
                            enabled = !userWhitelist.value,
                            onCheckedChange = {
                                if (userWhitelist.value && it) {
                                    WindowUtils.showToast(whitelistExemptionBlocked)
                                    return@SwitchPreference
                                }
                                val previous = blockAutostart.value
                                blockAutostart.value = it
                                AppConfigs.setAutostartBlocked(packageName, userId, it)
                                saveApplicationSettingsAsync("自启动拦截配置更新失败") { error ->
                                    blockAutostart.value = previous
                                    AppConfigs.setAutostartBlocked(packageName, userId, previous)
                                    WindowUtils.showToast(error)
                                }
                            }
                        )

                        if (globalSettings?.memoryTrimEnabled == true) {
                            SwitchPreference(
                                title = stringResource(R.string.memory_trim_enabled),
                                checked = memoryTrimEnabled.value,
                                onCheckedChange = {
                                    val previous = memoryTrimEnabled.value
                                    memoryTrimEnabled.value = it
                                    AppConfigs.setMemoryTrimEnabled(packageName, userId, it)
                                    saveApplicationSettingsAsync("内存回收配置更新失败") { error ->
                                        memoryTrimEnabled.value = previous
                                        AppConfigs.setMemoryTrimEnabled(packageName, userId, previous)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )
                        }

                        if (
                            globalSettings?.memoryTrimEnabled == true &&
                            globalSettings.memoryTrimGcEnabled &&
                            memoryTrimEnabled.value
                        ) {
                            SwitchPreference(
                                title = stringResource(R.string.memory_trim_gc_enabled),
                                checked = memoryTrimGcEnabled.value,
                                onCheckedChange = {
                                    val previous = memoryTrimGcEnabled.value
                                    memoryTrimGcEnabled.value = it
                                    AppConfigs.setMemoryTrimGcEnabled(packageName, userId, it)
                                    saveApplicationSettingsAsync("GC 配置更新失败") { error ->
                                        memoryTrimGcEnabled.value = previous
                                        AppConfigs.setMemoryTrimGcEnabled(packageName, userId, previous)
                                        WindowUtils.showToast(error)
                                    }
                                }
                            )
                        }

                        OverlayDropdownPreference(
                            title = stringResource(R.string.background_oom_level),
                            items = backgroundOomAdjItems(backgroundOomAdj.value),
                            selectedIndex = backgroundOomAdjSelectedIndex(backgroundOomAdj.value),
                            onSelectedIndexChange = { index ->
                                val adj = backgroundOomAdjForPresetIndex(index)
                                if (adj == null) {
                                    showBackgroundOomAdjCustomDialog.value = true
                                    return@OverlayDropdownPreference
                                }
                                val previous = backgroundOomAdj.value
                                backgroundOomAdj.value = adj
                                AppConfigs.setBackgroundOomAdj(packageName, userId, adj)
                                saveApplicationSettingsAsync(backgroundOomAdjUpdateFailed) { error ->
                                    backgroundOomAdj.value = previous
                                    AppConfigs.setBackgroundOomAdj(packageName, userId, previous)
                                    WindowUtils.showToast(error)
                                }
                            }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.black_app),
                            summary = if (isBuiltinWhitelistApp) stringResource(R.string.builtin_whitelist_blacklist_blocked) else null,
                            checked = black.value,
                            onCheckedChange = {
                                val prevBlack = black.value

                                black.value = it
                                AppConfigs.setBlackApp(packageName, userId, it)

                                saveApplicationSettingsAsync("黑名单更新失败") { error ->
                                    black.value = prevBlack
                                    AppConfigs.setBlackApp(packageName, userId, prevBlack)
                                    WindowUtils.showToast(error)
                                }
                            }
                        )

                    }
                }

                if (processListLoaded.value && !isBuiltinWhitelistApp && !userWhitelist.value && isSystemApp == black.value) {
                    item {
                        SmallTitle(text = stringResource(R.string.process_freeze_control))
                        CirnoCard(modifier = Modifier.padding(12.dp)) {
                            if (processList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_process_hint),
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.process_freeze_control_summary),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                    color = Color.Gray
                                )
                                
                                val keyboardController = LocalSoftwareKeyboardController.current
                                SearchBar(
                                    inputField = {
                                        InputField(
                                            query = processQuery,
                                            onQueryChange = { processQuery = it },
                                            onSearch = { keyboardController?.hide() },
                                            expanded = processSearchExpanded,
                                            onExpandedChange = { processSearchExpanded = it },
                                            label = stringResource(R.string.search),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = MiuixIcons.Basic.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .padding(start = 12.dp, end = 8.dp)
                                                        .size(20.dp)
                                                        .alpha(0.4f),
                                                    tint = colorScheme.onSurfaceContainer,
                                                )
                                            }
                                        )
                                    },
                                    expanded = processSearchExpanded,
                                    onExpandedChange = { processSearchExpanded = it },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {}
                                
                                val query = processQuery.trim()
                                val visibleProcesses = if (query.isEmpty()) processList
                                    else processList.filter { it.contains(query, ignoreCase = true) }
                                
                                if (visibleProcesses.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.no_process_hint),
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    visibleProcesses.forEach { processName ->
                                         val behavior = remember(processName) {
                                             mutableStateOf(AppConfigs.getProcessBehavior(packageName, userId, processName))
                                         }
                                         OverlayDropdownPreference(
                                             title = processName,
                                             items = processBehaviors,
                                             selectedIndex = behavior.value,
                                             onSelectedIndexChange = { selected ->
                                                 val previous = behavior.value
                                                 behavior.value = selected
                                                 AppConfigs.setProcessBehavior(packageName, userId, processName, selected)
                                                 saveApplicationSettingsAsync("进程配置更新失败") { error ->
                                                     behavior.value = previous
                                                     AppConfigs.setProcessBehavior(packageName, userId, processName, previous)
                                                     WindowUtils.showToast(error)
                                                 }
                                             }
                                         )
                     }
                 }
            }
                }
            }
        }
    }
}
                }
            }
        }
    }
