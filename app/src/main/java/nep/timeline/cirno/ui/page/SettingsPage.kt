@file:OptIn(ExperimentalScrollBarApi::class)

package nep.timeline.cirno.ui.page

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalIsWideScreen
import nep.timeline.cirno.ui.app.LocalUpdateAppState
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.LocalImageBackdrop
import nep.timeline.cirno.ui.utils.UiPrefs
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import nep.timeline.cirno.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsPage(
    active: Boolean,
    padding: PaddingValues,
    scrollEndHaptic: Boolean
) {
    val isWideScreen = LocalIsWideScreen.current
    val imageBackdrop = LocalImageBackdrop.current
    val backdrop = if (imageBackdrop != null) null else rememberBlurBackdrop()
    val blurActive = imageBackdrop != null || backdrop != null
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
    val screenState = rememberSettingsScreenState(WindowUtils::showToast)
    val holder = screenState.holder
    val lists = screenState.lists
    val backup = rememberSettingsBackupLaunchers(holder)
    val freezerModeFrozenUnavailableText = stringResource(R.string.freezer_mode_frozen_unavailable)
    val freezerModeUidUnavailableText = stringResource(R.string.freezer_mode_uid_unavailable)
    val batteryOptimizationUpdateFailedText = stringResource(R.string.battery_optimization_update_failed)
    val customBackgroundUpdatedText = stringResource(R.string.custom_background_updated)
    val customBackgroundUpdateFailedText = stringResource(R.string.custom_background_update_failed)
    val customBackgroundRemovedText = stringResource(R.string.custom_background_removed)
    val hookTypeErrorText = stringResource(R.string.error)
    val hookTypeRestartText = stringResource(R.string.hook_type_changed_restart)

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
                            items = lists.freezerModeItems,
                            selectedIndex = holder.freezerModeIndex,
                            onSelectedIndexChange = {
                                holder.onFreezerModeSelected(
                                    scope, it,
                                    freezerModeFrozenUnavailableText,
                                    freezerModeUidUnavailableText,
                                )
                            }
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.battery_optimization_mode),
                            items = lists.batteryOptimizationModeItems,
                            selectedIndex = holder.batteryOptimizationModeIndex,
                            onSelectedIndexChange = {
                                holder.onBatteryOptimizationModeSelected(scope, it, batteryOptimizationUpdateFailedText)
                            }
                        )
                        Text(
                            text = stringResource(R.string.interval_freeze_delay) + " | " + holder.freezeDelay.toInt() + " s",
                            modifier = Modifier.padding(17.dp),
                        )
                        Slider(
                            value = holder.freezeDelay,
                            onValueChange = {
                                holder.freezeDelay = it
                            },
                            onValueChangeFinished = {
                                holder.commitFreezeDelay()
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        )
                        Text(
                            text = stringResource(R.string.wake_freeze_delay) + " | " + holder.wakeFreezeDelay.toInt() + " s",
                            modifier = Modifier.padding(17.dp),
                        )
                        Slider(
                            value = holder.wakeFreezeDelay,
                            onValueChange = {
                                holder.wakeFreezeDelay = it
                            },
                            onValueChangeFinished = {
                                holder.commitWakeFreezeDelay()
                            },
                            valueRange = 1f..120f,
                            steps = 118,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        )
                        Text(
                            text = stringResource(R.string.network_speed_threshold) + " | " + formatSpeedThreshold(holder.networkSpeedThreshold.toInt()),
                            modifier = Modifier.padding(17.dp),
                        )
                        Slider(
                            value = holder.networkSpeedThreshold,
                            onValueChange = {
                                holder.networkSpeedThreshold = it
                            },
                            onValueChangeFinished = {
                                holder.commitNetworkSpeedThreshold()
                            },
                            valueRange = 102400f..2097152f,
                            steps = 99,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        )
                        SwitchPreference(
                            title = stringResource(R.string.boot_freeze_all),
                            checked = holder.bootFreezeAll == 1,
                            onCheckedChange = {
                                holder.setBootFreezeAll(it)
                            }
                         )
                        val hookTypeItems = holder.hookTypeItems()
                        val hookTypeIndex = rememberHookTypeIndex(holder, hookTypeItems)
                        OverlayDropdownPreference(
                            title = stringResource(R.string.hook_type),
                            items = hookTypeItems,
                            selectedIndex = hookTypeIndex.intValue,
                            onSelectedIndexChange = {
                                holder.onHookTypeSelected(
                                    hookTypeItems, it, hookTypeIndex,
                                    hookTypeErrorText, hookTypeRestartText,
                                )
                            }
                        )
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.settings_memory_group))
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchPreference(
                                title = stringResource(R.string.compaction_enabled),
                                checked = holder.compactionEnabled == 1,
                                onCheckedChange = {
                                    holder.setCompactionEnabled(it)
                                }
                            )
                            if (holder.compactionEnabled == 1) {
                                Text(
                                    text = stringResource(R.string.compaction_delay) + " | " + holder.compactionDelay.toInt() + " s",
                                    modifier = Modifier.padding(17.dp),
                                )
                                Slider(
                                    value = holder.compactionDelay,
                                    onValueChange = {
                                        holder.compactionDelay = it
                                    },
                                    onValueChangeFinished = {
                                        holder.commitCompactionDelay()
                                    },
                                    valueRange = 1f..30f,
                                    steps = 28,
                                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                                )
                                Text(
                                    text = stringResource(R.string.compaction_throttle) + " | " + holder.compactionThrottle.toInt() + " s",
                                    modifier = Modifier.padding(17.dp),
                                )
                                Slider(
                                    value = holder.compactionThrottle,
                                    onValueChange = {
                                        holder.compactionThrottle = it
                                    },
                                    onValueChangeFinished = {
                                        holder.commitCompactionThrottle()
                                    },
                                    valueRange = 1f..60f,
                                    steps = 58,
                                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                                )
                            }
                        }
                        SwitchPreference(
                            title = stringResource(R.string.memory_trim_enabled),
                            checked = holder.memoryTrimEnabled == 1,
                            onCheckedChange = {
                                holder.setMemoryTrimEnabled(it)
                            }
                        )
                        if (holder.memoryTrimEnabled == 1) {
                            Text(
                                text = stringResource(R.string.memory_trim_delay) + " | " + holder.memoryTrimDelay.toInt() + " s",
                                modifier = Modifier.padding(17.dp),
                            )
                            Slider(
                                value = holder.memoryTrimDelay,
                                onValueChange = {
                                    holder.memoryTrimDelay = it
                                },
                                onValueChangeFinished = {
                                    holder.commitMemoryTrimDelay()
                                },
                                valueRange = 1f..60f,
                                steps = 58,
                                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                            )
                            OverlayDropdownPreference(
                                title = stringResource(R.string.memory_trim_level),
                                items = lists.trimLevelItems,
                                selectedIndex = holder.memoryTrimLevelIndex,
                                onSelectedIndexChange = {
                                    holder.onMemoryTrimLevelSelected(it)
                                }
                            )
                            SwitchPreference(
                                title = stringResource(R.string.memory_trim_gc_enabled),
                                checked = holder.memoryTrimGcEnabled == 1,
                                onCheckedChange = {
                                    holder.setMemoryTrimGcEnabled(it)
                                }
                            )
                            Text(
                                text = stringResource(R.string.memory_trim_throttle) + " | " + holder.memoryTrimThrottle.toInt() + " s",
                                modifier = Modifier.padding(17.dp),
                            )
                            Slider(
                                value = holder.memoryTrimThrottle,
                                onValueChange = {
                                    holder.memoryTrimThrottle = it
                                },
                                onValueChangeFinished = {
                                    holder.commitMemoryTrimThrottle()
                                },
                                valueRange = 60f..1800f,
                                steps = 57,
                                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                            )
                        }
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.settings_ui_group))
                    CirnoCard(modifier = Modifier.padding(12.dp)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.ui_style),
                            items = lists.uiStyleItems,
                            selectedIndex = holder.uiStyleIndex,
                            onSelectedIndexChange = {
                                holder.uiStyleIndex = it
                                UiPrefs.setUiStyle(context, it)
                                updateAppState { state -> state.copy(uiStyle = it) }
                            }
                        )

                        OverlayDropdownPreference(
                            title = stringResource(R.string.update_channel),
                            items = lists.updateChannelItems,
                            selectedIndex = holder.updateChannelIndex,
                            onSelectedIndexChange = {
                                holder.onUpdateChannelSelected(context, it)
                            }
                        )

                        OverlayDropdownPreference(
                            title = stringResource(R.string.navigation_style),
                            items = lists.navItems,
                            selectedIndex = holder.navIndex,
                            onSelectedIndexChange = {
                                holder.navIndex = it
                                UiPrefs.setNavigationStyle(context, it)
                                updateAppState { state -> state.copy(navigationStyle = it) }
                            }
                        )

                        OverlayDropdownPreference(
                            title = stringResource(R.string.theme_mode),
                            items = lists.themeItems,
                            selectedIndex = holder.themeIndex,
                            onSelectedIndexChange = {
                                holder.themeIndex = it
                                UiPrefs.setColorMode(context, it)
                                updateAppState { state -> state.copy(colorMode = it) }
                            }
                        )

                        if (holder.themeIndex in 3..5) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.theme_key_color),
                                items = lists.keyColorItems,
                                selectedIndex = holder.keyColorIndex,
                                onSelectedIndexChange = {
                                    holder.keyColorIndex = it
                                    UiPrefs.setThemeKeyColor(context, it)
                                    updateAppState { state -> state.copy(themeKeyColor = it) }
                                }
                            )

                            OverlayDropdownPreference(
                                title = stringResource(R.string.theme_color_spec),
                                items = lists.colorSpecItems,
                                selectedIndex = holder.colorSpecIndex,
                                onSelectedIndexChange = {
                                    holder.colorSpecIndex = it
                                    UiPrefs.setThemeColorSpec(context, it)
                                    updateAppState { state -> state.copy(themeColorSpec = it) }
                                }
                            )

                            OverlayDropdownPreference(
                                title = stringResource(R.string.theme_palette_style),
                                items = lists.paletteStyleItems,
                                selectedIndex = holder.paletteStyleIndex,
                                onSelectedIndexChange = {
                                    holder.paletteStyleIndex = it
                                    UiPrefs.setThemePaletteStyle(context, it)
                                    updateAppState { state -> state.copy(themePaletteStyle = it) }
                                }
                            )
                        }

                        if (isRenderEffectSupported()) {
                            SwitchPreference(
                                title = stringResource(R.string.blur_ui),
                                summary = stringResource(R.string.blur_ui_desc),
                                checked = holder.blurEnabled == 1,
                                onCheckedChange = {
                                    holder.blurEnabled = if (it) 1 else 0
                                    UiPrefs.setBlur(context, it)
                                    updateAppState { state -> state.copy(blur = it) }
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
                            items = lists.levelItems,
                            selectedIndex = holder.levelIndex,
                            onSelectedIndexChange = {
                                holder.onLogLevelSelected(it)
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
                                backup.launchBackup(backup.backupFileName)
                            }
                        )

                        ArrowPreference(
                            title = stringResource(R.string.restore_config),
                            summary = stringResource(R.string.restore_config_desc),
                            onClick = {
                                backup.launchRestore()
                            }
                        )
                    }
                }
            }
        }

        OverlayDialog(
            title = stringResource(R.string.update_channel_ci_warning_title),
            summary = stringResource(R.string.update_channel_ci_warning_message),
            show = holder.showCiChannelConfirm,
            onDismissRequest = { holder.dismissCiChannelConfirm() },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = { holder.dismissCiChannelConfirm() },
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.ok),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { holder.confirmCiChannel(context) },
                )
            }
        }
    }
}
