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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import nep.timeline.cirno.ApplicationActivity
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.ui.page.AppConfigOomAdjDialogHost
import nep.timeline.cirno.ui.page.backgroundOomAdjItems
import nep.timeline.cirno.ui.page.backgroundOomAdjSelectedIndex
import nep.timeline.cirno.ui.page.rememberAppConfigState
import nep.timeline.cirno.ui.utils.shouldShowSplitPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialApplicationHome(activity: ApplicationActivity) {
    val isWideScreen = shouldShowSplitPane()
    val appName = activity.intent.getStringExtra("appName") ?: "App"
    val packageName = activity.intent.getStringExtra("packageName") ?: return
    val userId = activity.intent.getStringExtra("userId")?.toIntOrNull() ?: 0
    val holder = rememberAppConfigState(packageName, userId)
    val builtinWhitelistSummary = stringResource(R.string.builtin_whitelist_summary)
    val whitelistExemptionBlocked = stringResource(R.string.whitelist_exemption_blocked)
    val globalSettings = GlobalVars.globalSettings
    var processQuery by rememberSaveable { mutableStateOf("") }
    val processBehaviors = stringArrayResource(R.array.process_behaviors)
    val backgroundOomAdjUpdateFailed = stringResource(R.string.background_oom_level_update_failed)
    val batteryOptimizationUpdateFailedText = stringResource(R.string.battery_optimization_update_failed)

    AppConfigOomAdjDialogHost(holder)

    MaterialPageScaffold(
        title = appName,
        padding = PaddingValues(bottom = if (isWideScreen) 0.dp else 16.dp),
        navigationIcon = {
            IconButton(onClick = { activity.finish() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        item {
            MaterialSettingsSection(title = stringResource(R.string.app_info)) {
                if (!holder.isSystemApp) {
                    MaterialSwitchItem(
                        icon = Icons.Outlined.Security,
                        title = stringResource(R.string.white_app),
                        summary = if (holder.isBuiltinWhitelistApp) builtinWhitelistSummary else null,
                        checked = holder.isBuiltinWhitelistApp || holder.white,
                        enabled = !holder.isBuiltinWhitelistApp,
                    ) {
                        holder.onWhiteChanged(it)
                    }
                }

                MaterialSwitchItem(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.battery_opt),
                    summary = null,
                    checked = holder.batteryOptimizationEnabled,
                ) {
                    holder.onBatteryOptimizationChanged(it, batteryOptimizationUpdateFailedText)
                }

                if (!holder.isBuiltinWhitelistApp && (!holder.isSystemApp || holder.black)) {
                    MaterialSwitchItem(Icons.Outlined.MusicNote, stringResource(R.string.background_play), null, holder.backgroundPlay, !holder.userWhitelist) {
                        holder.onBackgroundPlayChanged(it, whitelistExemptionBlocked)
                    }
                    MaterialSwitchItem(Icons.Outlined.LocationOn, stringResource(R.string.location_check), null, holder.locationUse, !holder.userWhitelist) {
                        holder.onLocationUseChanged(it, whitelistExemptionBlocked)
                    }
                    MaterialSwitchItem(
                        icon = Icons.Outlined.NotificationsActive,
                        title = stringResource(R.string.netreceive_unfreeze),
                        summary = if (holder.packetAvailable == true) null else stringResource(R.string.packet_required_summary),
                        checked = holder.networkMessage,
                        enabled = holder.packetAvailable == true && !holder.userWhitelist,
                    ) {
                        holder.onNetworkMessageChanged(it, whitelistExemptionBlocked)
                    }
                    MaterialSwitchItem(Icons.Outlined.NetworkCheck, stringResource(R.string.network_speed_check), null, holder.networkSpeed, !holder.userWhitelist) {
                        holder.onNetworkSpeedChanged(it, whitelistExemptionBlocked)
                    }
                }

                MaterialSwitchItem(Icons.Outlined.Block, stringResource(R.string.block_autostart), null, holder.blockAutostart, !holder.userWhitelist) {
                    holder.onBlockAutostartChanged(it, whitelistExemptionBlocked)
                }

                if (globalSettings?.memoryTrimEnabled == true) {
                    MaterialSwitchItem(Icons.Outlined.Memory, stringResource(R.string.memory_trim_enabled), null, holder.memoryTrimEnabled, true) {
                        holder.onMemoryTrimChanged(it)
                    }
                }

                if (
                    globalSettings?.memoryTrimEnabled == true &&
                    globalSettings.memoryTrimGcEnabled &&
                    holder.memoryTrimEnabled
                ) {
                    MaterialSwitchItem(Icons.Outlined.Memory, stringResource(R.string.memory_trim_gc_enabled), null, holder.memoryTrimGcEnabled, true) {
                        holder.onMemoryTrimGcChanged(it)
                    }
                }

                MaterialDropdownItem(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.background_oom_level),
                    items = backgroundOomAdjItems(holder.backgroundOomAdj),
                    selectedIndex = backgroundOomAdjSelectedIndex(holder.backgroundOomAdj),
                ) { index ->
                    holder.onBackgroundOomAdjPresetSelected(index, backgroundOomAdjUpdateFailed)
                }

                MaterialSwitchItem(
                    icon = Icons.Outlined.Block,
                    title = stringResource(R.string.black_app),
                    summary = if (holder.isBuiltinWhitelistApp) stringResource(R.string.builtin_whitelist_blacklist_blocked) else null,
                    checked = holder.black,
                ) {
                    holder.onBlackChanged(it)
                }
            }
        }

        if (holder.processListLoaded && !holder.isBuiltinWhitelistApp && !holder.userWhitelist && holder.isSystemApp == holder.black) {
            item {
                MaterialSettingsSection(title = stringResource(R.string.process_freeze_control)) {
                    if (holder.processList.isEmpty()) {
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
                                            contentDescription = stringResource(R.string.clear_search)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            shape = MaterialTheme.shapes.small,
                        )

                        val query = processQuery.trim()
                        val visibleProcesses = if (query.isEmpty()) holder.processList
                            else holder.processList.filter { it.contains(query, ignoreCase = true) }

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
                                        holder.setProcessBehavior(processName, selected, behavior)
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}
