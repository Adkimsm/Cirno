@file:OptIn(ExperimentalScrollBarApi::class)

package nep.timeline.cirno.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import nep.timeline.cirno.ApplicationActivity
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.ui.custom.BackNavigationIcon
import nep.timeline.cirno.ui.page.AppConfigOomAdjDialogHost
import nep.timeline.cirno.ui.page.backgroundOomAdjItems
import nep.timeline.cirno.ui.page.backgroundOomAdjSelectedIndex
import nep.timeline.cirno.ui.page.rememberAppConfigState
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.LocalImageBackdrop
import nep.timeline.cirno.ui.utils.MiuixBackground
import nep.timeline.cirno.ui.utils.UiPrefs
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

@Composable
fun ApplicationHome(activity: ApplicationActivity) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val isWideScreen = shouldShowSplitPane()
    val appName = activity.intent.getStringExtra("appName") ?: "App"
    val packageName = activity.intent.getStringExtra("packageName") ?: return
    val userId = activity.intent.getStringExtra("userId")?.toIntOrNull() ?: 0
    val holder = rememberAppConfigState(packageName, userId, WindowUtils::showToast)
    val builtinWhitelistSummary = stringResource(R.string.builtin_whitelist_summary)
    val whitelistExemptionBlocked = stringResource(R.string.whitelist_exemption_blocked)
    val globalSettings = GlobalVars.globalSettings
    var processQuery by rememberSaveable { mutableStateOf("") }
    var processSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val processBehaviors = context.resources.getStringArray(R.array.process_behaviors).toList()
    val backgroundOomAdjUpdateFailed = stringResource(R.string.background_oom_level_update_failed)
    val batteryOptimizationUpdateFailedText = stringResource(R.string.battery_optimization_update_failed)

    AppConfigOomAdjDialogHost(holder)

    val blurUiEnabled = UiPrefs.getBlur(context)
    val backdrop = rememberBlurBackdrop(blurUiEnabled, true)
    val imageBackdrop = if (blurUiEnabled && BackgroundManager.currentUri != null && isRenderEffectSupported()) {
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
                        if (!holder.isSystemApp) {
                            SwitchPreference(
                                title = stringResource(R.string.white_app),
                                summary = if (holder.isBuiltinWhitelistApp) builtinWhitelistSummary else null,
                                checked = holder.isBuiltinWhitelistApp || holder.white,
                                enabled = !holder.isBuiltinWhitelistApp,
                                onCheckedChange = {
                                    holder.onWhiteChanged(it)
                                }
                            )
                        }

                        SwitchPreference(
                            title = stringResource(R.string.battery_opt),
                            checked = holder.batteryOptimizationEnabled,
                            onCheckedChange = {
                                holder.onBatteryOptimizationChanged(it, batteryOptimizationUpdateFailedText)
                            }
                        )

                        if (!holder.isBuiltinWhitelistApp && (!holder.isSystemApp || holder.black)) {
                            SwitchPreference(
                                title = stringResource(R.string.background_play),
                                checked = holder.backgroundPlay,
                                enabled = !holder.userWhitelist,
                                onCheckedChange = {
                                    holder.onBackgroundPlayChanged(it, whitelistExemptionBlocked)
                                }
                            )

                            SwitchPreference(
                                title = stringResource(R.string.location_check),
                                checked = holder.locationUse,
                                enabled = !holder.userWhitelist,
                                onCheckedChange = {
                                    holder.onLocationUseChanged(it, whitelistExemptionBlocked)
                                }
                            )

                            SwitchPreference(
                                title = stringResource(R.string.netreceive_unfreeze),
                                summary = if (holder.packetAvailable == true) null else stringResource(R.string.packet_required_summary),
                                checked = holder.networkMessage,
                                enabled = holder.packetAvailable == true && !holder.userWhitelist,
                                onCheckedChange = {
                                    holder.onNetworkMessageChanged(it, whitelistExemptionBlocked)
                                }
                            )

                            SwitchPreference(
                                title = stringResource(R.string.network_speed_check),
                                checked = holder.networkSpeed,
                                enabled = !holder.userWhitelist,
                                onCheckedChange = {
                                    holder.onNetworkSpeedChanged(it, whitelistExemptionBlocked)
                                }
                            )
                        }

                        SwitchPreference(
                            title = stringResource(R.string.block_autostart),
                            checked = holder.blockAutostart,
                            enabled = !holder.userWhitelist,
                            onCheckedChange = {
                                holder.onBlockAutostartChanged(it, whitelistExemptionBlocked)
                            }
                        )

                        if (globalSettings?.memoryTrimEnabled == true) {
                            SwitchPreference(
                                title = stringResource(R.string.memory_trim_enabled),
                                checked = holder.memoryTrimEnabled,
                                onCheckedChange = {
                                    holder.onMemoryTrimChanged(it)
                                }
                            )
                        }

                        if (
                            globalSettings?.memoryTrimEnabled == true &&
                            globalSettings.memoryTrimGcEnabled &&
                            holder.memoryTrimEnabled
                        ) {
                            SwitchPreference(
                                title = stringResource(R.string.memory_trim_gc_enabled),
                                checked = holder.memoryTrimGcEnabled,
                                onCheckedChange = {
                                    holder.onMemoryTrimGcChanged(it)
                                }
                            )
                        }

                        OverlayDropdownPreference(
                            title = stringResource(R.string.background_oom_level),
                            items = backgroundOomAdjItems(holder.backgroundOomAdj),
                            selectedIndex = backgroundOomAdjSelectedIndex(holder.backgroundOomAdj),
                            onSelectedIndexChange = {
                                holder.onBackgroundOomAdjPresetSelected(it, backgroundOomAdjUpdateFailed)
                            }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.black_app),
                            summary = if (holder.isBuiltinWhitelistApp) stringResource(R.string.builtin_whitelist_blacklist_blocked) else null,
                            checked = holder.black,
                            onCheckedChange = {
                                holder.onBlackChanged(it)
                            }
                        )

                    }
                }

                if (holder.processListLoaded && !holder.isBuiltinWhitelistApp && !holder.userWhitelist && holder.isSystemApp == holder.black) {
                    item {
                        SmallTitle(text = stringResource(R.string.process_freeze_control))
                        CirnoCard(modifier = Modifier.padding(12.dp)) {
                            if (holder.processList.isEmpty()) {
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
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
                                ) {}

                                val query = processQuery.trim()
                                val visibleProcesses = if (query.isEmpty()) holder.processList
                                    else holder.processList.filter { it.contains(query, ignoreCase = true) }

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
                                                 holder.setProcessBehavior(processName, selected, behavior)
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
