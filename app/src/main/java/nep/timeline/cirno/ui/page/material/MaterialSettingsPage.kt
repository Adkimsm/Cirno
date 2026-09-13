package nep.timeline.cirno.ui.page.material

import android.os.Build
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
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nep.timeline.cirno.BuildConfig
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalUpdateAppState
import nep.timeline.cirno.ui.page.formatSpeedThreshold
import nep.timeline.cirno.ui.page.rememberHookTypeIndex
import nep.timeline.cirno.ui.page.rememberSettingsBackupLaunchers
import nep.timeline.cirno.ui.page.rememberSettingsScreenState
import nep.timeline.cirno.ui.utils.UiPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialSettingsPage(
    active: Boolean,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val updateAppState = LocalUpdateAppState.current
    val scope = rememberCoroutineScope()
    val screenState = rememberSettingsScreenState()
    val holder = screenState.holder
    val lists = screenState.lists
    val backup = rememberSettingsBackupLaunchers(holder)

    val batteryOptimizationUpdateFailedText = stringResource(R.string.battery_optimization_update_failed)
    val freezerModeFrozenUnavailableText = stringResource(R.string.freezer_mode_frozen_unavailable)
    val freezerModeUidUnavailableText = stringResource(R.string.freezer_mode_uid_unavailable)
    val hookTypeErrorText = stringResource(R.string.error)
    val hookTypeRestartText = stringResource(R.string.hook_type_changed_restart)

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
                    MaterialDropdownItem(Icons.Outlined.Update, stringResource(R.string.freezer_mode), lists.freezerModeItems, holder.freezerModeIndex) {
                        holder.onFreezerModeSelected(
                            scope, it,
                            freezerModeFrozenUnavailableText,
                            freezerModeUidUnavailableText,
                        )
                    }
                    MaterialDropdownItem(Icons.Outlined.Update, stringResource(R.string.battery_optimization_mode), lists.batteryOptimizationModeItems, holder.batteryOptimizationModeIndex) {
                        holder.onBatteryOptimizationModeSelected(scope, it, batteryOptimizationUpdateFailedText)
                    }
                    MaterialSliderItem(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.interval_freeze_delay),
                        valueText = "${holder.freezeDelay.toInt()} s",
                        value = holder.freezeDelay,
                        valueRange = 1f..30f,
                        steps = 28,
                        onValueChange = { holder.freezeDelay = it },
                        onValueFinished = {
                            holder.commitFreezeDelay()
                        },
                    )
                    MaterialSliderItem(
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.wake_freeze_delay),
                        valueText = "${holder.wakeFreezeDelay.toInt()} s",
                        value = holder.wakeFreezeDelay,
                        valueRange = 1f..120f,
                        steps = 118,
                        onValueChange = { holder.wakeFreezeDelay = it },
                        onValueFinished = {
                            holder.commitWakeFreezeDelay()
                        },
                    )
                    MaterialSliderItem(
                        icon = Icons.Outlined.Speed,
                        title = stringResource(R.string.network_speed_threshold),
                        valueText = formatSpeedThreshold(holder.networkSpeedThreshold.toInt()),
                        value = holder.networkSpeedThreshold,
                        valueRange = 102400f..2097152f,
                        steps = 99,
                        onValueChange = { holder.networkSpeedThreshold = it },
                        onValueFinished = {
                            holder.commitNetworkSpeedThreshold()
                        },
                    )
                    MaterialSwitchItem(
                        icon = Icons.Outlined.Update,
                        title = stringResource(R.string.boot_freeze_all),
                        summary = null,
                        checked = holder.bootFreezeAll == 1,
                        onCheckedChange = {
                            holder.setBootFreezeAll(it)
                        }
                    )
                    val hookTypeItems = holder.hookTypeItems()
                    val hookTypeIndex = rememberHookTypeIndex(holder, hookTypeItems)
                    MaterialDropdownItem(Icons.Outlined.Update, stringResource(R.string.hook_type), hookTypeItems, hookTypeIndex.intValue) {
                        holder.onHookTypeSelected(
                            hookTypeItems, it, hookTypeIndex,
                            hookTypeErrorText, hookTypeRestartText,
                        )
                    }
                }
            }
            item {
                MaterialSettingsSection(title = stringResource(R.string.settings_memory_group)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MaterialSwitchItem(
                            icon = Icons.Outlined.FilterList,
                            title = stringResource(R.string.compaction_enabled),
                            summary = null,
                            checked = holder.compactionEnabled == 1,
                            onCheckedChange = {
                                holder.setCompactionEnabled(it)
                            }
                        )
                        if (holder.compactionEnabled == 1) {
                            MaterialSliderItem(
                                icon = Icons.Outlined.Timer,
                                title = stringResource(R.string.compaction_delay),
                                valueText = "${holder.compactionDelay.toInt()} s",
                                value = holder.compactionDelay,
                                valueRange = 1f..30f,
                                steps = 28,
                                onValueChange = { holder.compactionDelay = it },
                                onValueFinished = {
                                    holder.commitCompactionDelay()
                                },
                            )
                            MaterialSliderItem(
                                icon = Icons.Outlined.Speed,
                                title = stringResource(R.string.compaction_throttle),
                                valueText = "${holder.compactionThrottle.toInt()} s",
                                value = holder.compactionThrottle,
                                valueRange = 1f..60f,
                                steps = 58,
                                onValueChange = { holder.compactionThrottle = it },
                                onValueFinished = {
                                    holder.commitCompactionThrottle()
                                },
                            )
                        }
                    }
                    MaterialSwitchItem(
                        icon = Icons.Outlined.FilterList,
                        title = stringResource(R.string.memory_trim_enabled),
                        summary = null,
                        checked = holder.memoryTrimEnabled == 1,
                        onCheckedChange = {
                            holder.setMemoryTrimEnabled(it)
                        }
                    )
                    if (holder.memoryTrimEnabled == 1) {
                        MaterialSliderItem(
                            icon = Icons.Outlined.Timer,
                            title = stringResource(R.string.memory_trim_delay),
                            valueText = "${holder.memoryTrimDelay.toInt()} s",
                            value = holder.memoryTrimDelay,
                            valueRange = 1f..60f,
                            steps = 58,
                            onValueChange = { holder.memoryTrimDelay = it },
                            onValueFinished = {
                                holder.commitMemoryTrimDelay()
                            },
                        )
                        MaterialDropdownItem(Icons.Outlined.FilterList, stringResource(R.string.memory_trim_level), lists.trimLevelItems, holder.memoryTrimLevelIndex) {
                            holder.onMemoryTrimLevelSelected(it)
                        }
                        MaterialSwitchItem(
                            icon = Icons.Outlined.FilterList,
                            title = stringResource(R.string.memory_trim_gc_enabled),
                            summary = null,
                            checked = holder.memoryTrimGcEnabled == 1,
                            onCheckedChange = {
                                holder.setMemoryTrimGcEnabled(it)
                            }
                        )
                        MaterialSliderItem(
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.memory_trim_throttle),
                            valueText = "${holder.memoryTrimThrottle.toInt()} s",
                            value = holder.memoryTrimThrottle,
                            valueRange = 60f..1800f,
                            steps = 57,
                            onValueChange = { holder.memoryTrimThrottle = it },
                            onValueFinished = {
                                holder.commitMemoryTrimThrottle()
                            },
                        )
                    }
                }
            }
            item {
                MaterialSettingsSection(title = stringResource(R.string.settings_ui_group)) {
                    MaterialDropdownItem(Icons.Outlined.Dashboard, stringResource(R.string.ui_style), lists.uiStyleItems, holder.uiStyleIndex) {
                        holder.uiStyleIndex = it
                        UiPrefs.setUiStyle(context, it)
                        updateAppState { state -> state.copy(uiStyle = it) }
                    }
                    MaterialDropdownItem(Icons.Outlined.SystemUpdate, stringResource(R.string.update_channel), lists.updateChannelItems, holder.updateChannelIndex) {
                        holder.onUpdateChannelSelected(context, it)
                    }
                    MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_mode), lists.themeItems, holder.themeIndex) {
                        holder.themeIndex = it
                        UiPrefs.setColorMode(context, it)
                        updateAppState { state -> state.copy(colorMode = it) }
                    }
                    if (holder.themeIndex in 3..5) {
                        MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_key_color), lists.keyColorItems, holder.keyColorIndex) {
                            holder.keyColorIndex = it
                            UiPrefs.setThemeKeyColor(context, it)
                            updateAppState { state -> state.copy(themeKeyColor = it) }
                        }
                        MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_color_spec), lists.colorSpecItems, holder.colorSpecIndex) {
                            holder.colorSpecIndex = it
                            UiPrefs.setThemeColorSpec(context, it)
                            updateAppState { state -> state.copy(themeColorSpec = it) }
                        }
                        MaterialDropdownItem(Icons.Outlined.Palette, stringResource(R.string.theme_palette_style), lists.paletteStyleItems, holder.paletteStyleIndex) {
                            holder.paletteStyleIndex = it
                            UiPrefs.setThemePaletteStyle(context, it)
                            updateAppState { state -> state.copy(themePaletteStyle = it) }
                        }
                    }
                }
            }
        }

        item {
            MaterialSettingsSection(title = stringResource(R.string.settings_log_group)) {
                MaterialDropdownItem(Icons.Outlined.BugReport, stringResource(R.string.log_level), lists.levelItems, holder.levelIndex) {
                    holder.onLogLevelSelected(it)
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
                        onClick = { backup.launchBackup(backup.backupFileName) },
                    )
                    MaterialActionItem(
                        icon = Icons.Outlined.Restore,
                        title = stringResource(R.string.restore_config),
                        summary = stringResource(R.string.restore_config_desc),
                        onClick = { backup.launchRestore() },
                    )
                }
            }
        }
    }

    if (holder.showCiChannelConfirm) {
        AlertDialog(
            onDismissRequest = { holder.dismissCiChannelConfirm() },
            title = {
                Text(text = stringResource(R.string.update_channel_ci_warning_title))
            },
            text = {
                Text(text = stringResource(R.string.update_channel_ci_warning_message))
            },
            dismissButton = {
                TextButton(onClick = { holder.dismissCiChannelConfirm() }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { holder.confirmCiChannel(context) },
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            },
        )
    }
}
