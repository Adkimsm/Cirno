package nep.timeline.cirno.ui.page.material

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nep.timeline.cirno.BuildConfig
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalNavigator
import nep.timeline.cirno.ui.dialog.DownloadProgressDialog
import nep.timeline.cirno.ui.navigation3.Route
import nep.timeline.cirno.ui.page.rememberInfoScreenState
import nep.timeline.cirno.ui.utils.ApkInstaller
import nep.timeline.cirno.ui.utils.AddOnStatusRepository
import nep.timeline.cirno.ui.utils.UpdateChecker
import nep.timeline.cirno.ui.utils.UpdateResult
import nep.timeline.cirno.utils.VersionUtils
import nep.timeline.cirno.ui.utils.XposedServiceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialInfoPage(
    padding: PaddingValues,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showToast = rememberMaterialToast()
    val infoState = rememberInfoScreenState(context)
    var hotReloadResult by remember { mutableStateOf<String?>(null) }
    val xposedServiceStatus = XposedServiceStatus.state.value

    hotReloadResult?.let { message ->
        AlertDialog(
            onDismissRequest = { hotReloadResult = null },
            title = {
                Text(text = stringResource(R.string.hot_reload_result_title))
            },
            text = {
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { hotReloadResult = null }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
        )
    }

    infoState.updateResult?.let { result ->
        if (infoState.showUpdateDialog) {
            MaterialUpdateDialog(
                show = true,
                updateResult = result,
                onDismissRequest = { infoState.dismissUpdateDialog() },
            )
        }
    }

    MaterialPageScaffold(
        title = stringResource(R.string.app_name),
        padding = padding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val binderState = infoState.binderState
            val active = xposedServiceStatus.active
            val connecting = binderState.connecting
            val addOnMissing = !AddOnStatusRepository.isAddOnEnabled()
            val working = active && !connecting && !binderState.hasError && !addOnMissing
            val hookVersion = binderState.hookVersion ?: stringResource(R.string.not_running)

            MaterialSurfaceCard(
                containerColor = when {
                    connecting -> MaterialTheme.colorScheme.surfaceContainer
                    working -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                },
                contentPadding = PaddingValues(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (connecting) {
                            MaterialLoadingIndicator(modifier = Modifier.size(32.dp))
                        } else {
                            Icon(
                                imageVector = if (working) Icons.Outlined.CheckCircleOutline else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (working) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when {
                                connecting -> stringResource(R.string.connecting)
                                working -> stringResource(R.string.working)
                                else -> stringResource(R.string.error)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = hookVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            val xposedServiceStatus = XposedServiceStatus.state.value
            val active = xposedServiceStatus.active
            val binderState = infoState.binderState
            val statusBinderAvailable = binderState.statusBinderAvailable
            val connecting = binderState.connecting
            // 优先用 hook 指纹判断是否需要重启：纯 UI 更新指纹不变，不显示"请重启"。
            // hook 侧未上报指纹（旧版 hook）时兜底用 hookVersion 比较。
            val hookFingerprint = binderState.hookFingerprint
            val versionMismatch = active && !connecting && statusBinderAvailable &&
                if (hookFingerprint != null) {
                    hookFingerprint != BuildConfig.HOOK_FINGERPRINT
                } else {
                    binderState.hookVersion != null && binderState.hookVersion != BuildConfig.VERSION_NAME
                }
            val addOnMissing = !AddOnStatusRepository.isAddOnEnabled()
            val androidScopeLabel = stringResource(R.string.scope_android)
            val systemUiScopeLabel = stringResource(R.string.scope_systemui)
            val missingScopeLabels = xposedServiceStatus.missingRequiredScopes.joinToString(separator = ", ") { scope ->
                when (scope) {
                    "system" -> androidScopeLabel
                    else -> systemUiScopeLabel
                }
            }
            if (versionMismatch) {
                MaterialWarningCard(stringResource(R.string.module_version_mismatch))
            } else {
                if (!active) {
                    MaterialWarningCard(stringResource(R.string.not_active))
                }
                if (active && statusBinderAvailable && xposedServiceStatus.missingRequiredScopes.isNotEmpty()) {
                    MaterialWarningCard(stringResource(R.string.scope_not_running, missingScopeLabels))
                }
                if (!connecting && binderState.hasError) {
                    MaterialWarningCard(stringResource(R.string.internal_error))
                }
                if (!connecting && active && statusBinderAvailable && addOnMissing) {
                    MaterialWarningCard(stringResource(R.string.add_on_required_warning))
                }
                if (!connecting && active && statusBinderAvailable && !binderState.freezerAvailable) {
                    MaterialWarningCard(stringResource(R.string.freezer_v2_unavailable))
                }
            }
        }

        item {
            val xposedServiceStatus = XposedServiceStatus.state.value
            val active = xposedServiceStatus.active
            val binderState = infoState.binderState
            val connecting = binderState.connecting
            val addOnMissing = !AddOnStatusRepository.isAddOnEnabled()
            val working = active && !connecting && !binderState.hasError && !addOnMissing
            MaterialSurfaceCard(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MaterialInfoRow(stringResource(R.string.manager_version), "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}-${BuildConfig.BUILD_TIME})")
                MaterialInfoRow(stringResource(R.string.hook_type), if (working && binderState.hookType != null) binderState.hookType else stringResource(R.string.unknown))
                MaterialInfoRow(stringResource(R.string.android_version), if (Build.VERSION.PREVIEW_SDK_INT != 0) (Build.VERSION.CODENAME + " Preview (API " + Build.VERSION.PREVIEW_SDK_INT + "/" + Build.VERSION.SDK_INT + ")") else (VersionUtils.getAndroidVersion() + " (API " + Build.VERSION.SDK_INT + ")"))
                MaterialInfoRow(stringResource(R.string.xposed_version), if (xposedServiceStatus.frameworkVersion.isNotEmpty()) "${xposedServiceStatus.frameworkName} ${xposedServiceStatus.frameworkVersion}, API ${xposedServiceStatus.apiVersion}" else stringResource(R.string.unknown))
                MaterialInfoRow(stringResource(R.string.system_fingerprint), Build.FINGERPRINT)
            }
        }

        item {
            val xposedServiceStatus = XposedServiceStatus.state.value
            if (xposedServiceStatus.active && xposedServiceStatus.supportsHotReload) {
                val unsupportedText = stringResource(R.string.hot_reload_unsupported)
                val noTargetsText = stringResource(R.string.hot_reload_no_targets)
                val failedText = stringResource(R.string.hot_reload_failed)
                val submittedText = stringResource(R.string.hot_reload_submitted)
                MaterialNavigationCard(
                    title = stringResource(R.string.hot_reload),
                    icon = { MaterialIcon(Icons.Outlined.Refresh) },
                    onClick = {
                        XposedServiceStatus.hotReloadRunningTargets { outcome ->
                            hotReloadResult = when {
                                !outcome.supported -> unsupportedText
                                outcome.error != null -> failedText.format(outcome.error)
                                outcome.targetCount == 0 -> noTargetsText
                                else -> submittedText.format(outcome.results.joinToString(separator = "\n"))
                            }
                        }
                    },
                )
            }
        }

        item {
            MaterialNavigationCard(
                title = if (infoState.isCheckingUpdate) stringResource(R.string.update_checking) else stringResource(R.string.check_update),
                icon = { MaterialIcon(Icons.Outlined.SystemUpdate) },
                enabled = !infoState.isCheckingUpdate,
                onClick = { infoState.startUpdateCheck() },
            )
        }

        item {
            MaterialNavigationCard(
                title = stringResource(R.string.home_logs),
                icon = { MaterialIcon(Icons.Outlined.BugReport) },
                onClick = { navigator.push(Route.Log) },
            )
        }

        item {
            MaterialNavigationCard(
                title = stringResource(R.string.home_about_freezer),
                icon = { MaterialIcon(Icons.Outlined.Info) },
                onClick = { navigator.push(Route.About) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MaterialWarningCard(text: String) {
    MaterialSurfaceCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun MaterialInfoRow(title: String, content: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MaterialUpdateDialog(
    show: Boolean,
    updateResult: UpdateResult,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showToast = rememberMaterialToast()
    var dontRemind by remember(updateResult.versionName) { mutableStateOf(false) }
    val showDownloadDialog = remember { mutableStateOf(false) }
    val downloadProgress = remember { mutableIntStateOf(0) }

    fun dismiss() {
        if (dontRemind) {
            UpdateChecker.markSkipped(context, updateResult.versionName)
        }
        onDismissRequest()
    }

    DownloadProgressDialog(
        show = showDownloadDialog.value,
        progress = downloadProgress.intValue
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.update_available))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.update_new_version, updateResult.versionName))
            if (updateResult.changelog != null && updateResult.changelog.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.update_changelog),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = updateResult.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = dontRemind,
                        onValueChange = { dontRemind = !dontRemind },
                        role = Role.Switch,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = dontRemind,
                        onCheckedChange = { dontRemind = it },
                    )
                    Text(text = stringResource(R.string.update_dont_remind))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (dontRemind) {
                        UpdateChecker.markSkipped(context, updateResult.versionName)
                    }
                    showDownloadDialog.value = true
                    scope.launch {
                        ApkInstaller.downloadAndInstall(
                            context = context,
                            url = updateResult.downloadUrl,
                            onProgress = { progress ->
                                downloadProgress.intValue = progress
                            },
                            onComplete = {
                                showDownloadDialog.value = false
                                onDismissRequest()
                            },
                            onError = { message ->
                                showDownloadDialog.value = false
                                showToast(message)
                                onDismissRequest()
                            }
                        )
                    }
                },
            ) {
                Text(text = stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = ::dismiss) {
                Text(text = stringResource(R.string.update_later))
            }
        },
    )
}
