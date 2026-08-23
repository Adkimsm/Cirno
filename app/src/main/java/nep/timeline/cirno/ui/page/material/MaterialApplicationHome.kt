package nep.timeline.cirno.ui.page.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Task
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.ApplicationActivity
import nep.timeline.cirno.CommonConstants
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.provide.ApplicationBinder
import nep.timeline.cirno.ui.page.BackgroundOomAdjCustomDialog
import nep.timeline.cirno.ui.page.backgroundOomAdjForPresetIndex
import nep.timeline.cirno.ui.page.backgroundOomAdjItems
import nep.timeline.cirno.ui.page.backgroundOomAdjSelectedIndex
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigSaveScope
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.utils.shouldShowSplitPane
import nep.timeline.cirno.utils.PKGUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialApplicationHome(activity: ApplicationActivity) {
    val isWideScreen = shouldShowSplitPane()
    val appName = activity.intent.getStringExtra("appName") ?: "App"
    val packageName = activity.intent.getStringExtra("packageName") ?: return
    val userId = activity.intent.getStringExtra("userId")?.toIntOrNull() ?: 0
    val packetAvailable = remember { mutableStateOf<Boolean?>(null) }
    val isBuiltinWhitelistApp = CommonConstants.isWhitelistApps(packageName)
    val builtinWhitelistSummary = stringResource(R.string.builtin_whitelist_summary)
    val whitelistExemptionBlocked = stringResource(R.string.whitelist_exemption_blocked)
    val globalSettings = GlobalVars.globalSettings
    val isSystemApp = remember(packageName) {
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
    val processBehaviors = stringArrayResource(R.array.process_behaviors)
    val black = remember { mutableStateOf(AppConfigs.isBlackApp(packageName, userId)) }
    val white = remember { mutableStateOf(AppConfigs.isWhiteApp(packageName, userId)) }
    val userWhitelist = remember { mutableStateOf(AppConfigs.hasUserWhitelist(packageName, userId)) }
    val backgroundPlay = remember { mutableStateOf(AppConfigs.isBackgroundPlayAllowed(packageName, userId)) }
    val locationUse = remember { mutableStateOf(AppConfigs.isLocationUseAllowed(packageName, userId)) }
    val networkMessage = remember { mutableStateOf(AppConfigs.isNetworkMessageAllowed(packageName, userId)) }
    val networkSpeed = remember { mutableStateOf(AppConfigs.isNetworkSpeedAllowed(packageName, userId)) }
    val blockAutostart = remember { mutableStateOf(AppConfigs.isAutostartBlocked(packageName, userId)) }
    val memoryTrimEnabled = remember { mutableStateOf(AppConfigs.isMemoryTrimEnabled(packageName, userId)) }
    val memoryTrimGcEnabled = remember { mutableStateOf(AppConfigs.isMemoryTrimGcEnabled(packageName, userId)) }
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

    LaunchedEffect(packetAvailable.value, networkMessage.value) {
        if (packetAvailable.value == false && networkMessage.value) {
            networkMessage.value = false
            AppConfigs.setNetworkMessageAllowed(packageName, userId, false)
            saveApplicationSettingsAsync()
        }
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

    MaterialPageScaffold(
        title = appName,
        padding = PaddingValues(bottom = if (isWideScreen) 0.dp else 16.dp),
        navigationIcon = {
            IconButton(onClick = { activity.finish() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        item {
            MaterialSettingsSection(title = stringResource(R.string.app_info)) {
                if (!isSystemApp) {
                    MaterialSwitchItem(
                        icon = Icons.Outlined.Security,
                        title = stringResource(R.string.white_app),
                        summary = if (isBuiltinWhitelistApp) builtinWhitelistSummary else null,
                        checked = isBuiltinWhitelistApp || white.value,
                        enabled = !isBuiltinWhitelistApp,
                    ) {
                        if (isBuiltinWhitelistApp) return@MaterialSwitchItem
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
                }

                if (!isBuiltinWhitelistApp && (!isSystemApp || black.value)) {
                    MaterialSwitchItem(Icons.Outlined.MusicNote, stringResource(R.string.background_play), null, backgroundPlay.value, !userWhitelist.value) {
                        if (userWhitelist.value && it) {
                            WindowUtils.showToast(whitelistExemptionBlocked)
                            return@MaterialSwitchItem
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
                    MaterialSwitchItem(Icons.Outlined.LocationOn, stringResource(R.string.location_check), null, locationUse.value, !userWhitelist.value) {
                        if (userWhitelist.value && it) {
                            WindowUtils.showToast(whitelistExemptionBlocked)
                            return@MaterialSwitchItem
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
                    MaterialSwitchItem(
                        icon = Icons.Outlined.NotificationsActive,
                        title = stringResource(R.string.netreceive_unfreeze),
                        summary = if (packetAvailable.value == true) null else stringResource(R.string.packet_required_summary),
                        checked = networkMessage.value,
                        enabled = packetAvailable.value == true && !userWhitelist.value,
                    ) {
                        if (userWhitelist.value && it) {
                            WindowUtils.showToast(whitelistExemptionBlocked)
                            return@MaterialSwitchItem
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
                    MaterialSwitchItem(Icons.Outlined.NetworkCheck, stringResource(R.string.network_speed_check), null, networkSpeed.value, !userWhitelist.value) {
                        if (userWhitelist.value && it) {
                            WindowUtils.showToast(whitelistExemptionBlocked)
                            return@MaterialSwitchItem
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
                }

                MaterialSwitchItem(Icons.Outlined.Block, stringResource(R.string.block_autostart), null, blockAutostart.value, !userWhitelist.value) {
                    if (userWhitelist.value && it) {
                        WindowUtils.showToast(whitelistExemptionBlocked)
                        return@MaterialSwitchItem
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

                if (globalSettings?.memoryTrimEnabled == true) {
                    MaterialSwitchItem(Icons.Outlined.Memory, stringResource(R.string.memory_trim_enabled), null, memoryTrimEnabled.value, true) {
                        val previous = memoryTrimEnabled.value
                        memoryTrimEnabled.value = it
                        AppConfigs.setMemoryTrimEnabled(packageName, userId, it)
                        saveApplicationSettingsAsync("内存回收配置更新失败") { error ->
                            memoryTrimEnabled.value = previous
                            AppConfigs.setMemoryTrimEnabled(packageName, userId, previous)
                            WindowUtils.showToast(error)
                        }
                    }
                }

                if (
                    globalSettings?.memoryTrimEnabled == true &&
                    globalSettings.memoryTrimGcEnabled &&
                    memoryTrimEnabled.value
                ) {
                    MaterialSwitchItem(Icons.Outlined.Memory, stringResource(R.string.memory_trim_gc_enabled), null, memoryTrimGcEnabled.value, true) {
                        val previous = memoryTrimGcEnabled.value
                        memoryTrimGcEnabled.value = it
                        AppConfigs.setMemoryTrimGcEnabled(packageName, userId, it)
                        saveApplicationSettingsAsync("GC 配置更新失败") { error ->
                            memoryTrimGcEnabled.value = previous
                            AppConfigs.setMemoryTrimGcEnabled(packageName, userId, previous)
                            WindowUtils.showToast(error)
                        }
                    }
                }

                MaterialDropdownItem(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.background_oom_level),
                    items = backgroundOomAdjItems(backgroundOomAdj.value),
                    selectedIndex = backgroundOomAdjSelectedIndex(backgroundOomAdj.value),
                ) { index ->
                    val adj = backgroundOomAdjForPresetIndex(index)
                    if (adj == null) {
                        showBackgroundOomAdjCustomDialog.value = true
                        return@MaterialDropdownItem
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

                MaterialSwitchItem(
                    icon = Icons.Outlined.Block,
                    title = stringResource(R.string.black_app),
                    summary = if (isBuiltinWhitelistApp) stringResource(R.string.builtin_whitelist_blacklist_blocked) else null,
                    checked = black.value,
                ) {
                    val prevBlack = black.value
                    black.value = it
                    AppConfigs.setBlackApp(packageName, userId, it)

                    saveApplicationSettingsAsync("黑名单更新失败") { error ->
                        black.value = prevBlack
                        AppConfigs.setBlackApp(packageName, userId, prevBlack)
                        WindowUtils.showToast(error)
                    }
                }
            }
        }

        if (processListLoaded.value && !isBuiltinWhitelistApp && !userWhitelist.value && isSystemApp == black.value) {
            item {
                MaterialSettingsSection(title = stringResource(R.string.process_freeze_control)) {
                    if (processList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.no_process_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = processQuery,
                            onValueChange = { processQuery = it },
                            label = { Text(stringResource(R.string.search)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (processQuery.isNotEmpty()) {
                                    IconButton(onClick = { processQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = null
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        
                        val query = processQuery.trim()
                        val visibleProcesses = if (query.isEmpty()) processList
                            else processList.filter { it.contains(query, ignoreCase = true) }
                        
                        if (visibleProcesses.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_process_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                                visibleProcesses.forEach { processName ->
                                    val behavior = remember(processName) {
                                        mutableStateOf(AppConfigs.getProcessBehavior(packageName, userId, processName))
                                    }
                                    MaterialDropdownItem(
                                        icon = Icons.Outlined.Task,
                                        title = processName,
                                        items = processBehaviors.toList(),
                                        selectedIndex = behavior.value,
                                    ) { selected ->
                                        val previous = behavior.value
                                        behavior.value = selected
                                        AppConfigs.setProcessBehavior(packageName, userId, processName, selected)
                                        saveApplicationSettingsAsync("进程配置更新失败") { error ->
                                            behavior.value = previous
                                            AppConfigs.setProcessBehavior(packageName, userId, processName, previous)
                                            WindowUtils.showToast(error)
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
