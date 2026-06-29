package nep.timeline.cirno.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.ApkInstaller
import nep.timeline.cirno.ui.utils.UpdateChecker
import nep.timeline.cirno.ui.utils.UpdateResult
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun UpdateDialog(
    show: Boolean,
    updateResult: UpdateResult,
    onDismissRequest: () -> Unit
) {
    val isShow = rememberSaveable { mutableStateOf(false) }
    val dontRemind = rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showDownloadDialog = rememberSaveable { mutableStateOf(false) }
    val downloadProgress = rememberSaveable { mutableIntStateOf(0) }

    if (show && !isShow.value) {
        isShow.value = true
        dontRemind.value = false
    }

    DownloadProgressDialog(
        show = showDownloadDialog.value,
        progress = downloadProgress.intValue
    )

    OverlayDialog(
        title = stringResource(R.string.update_available),
        summary = stringResource(R.string.update_new_version, updateResult.versionName),
        show = show,
        onDismissRequest = {
            isShow.value = false
            onDismissRequest()
        },
    ) {
        Column {
            if (!updateResult.changelog.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                ) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.update_changelog),
                        fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2.fontSize,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = updateResult.changelog,
                        fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2.fontSize,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dontRemind.value = !dontRemind.value }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    state = if (dontRemind.value) ToggleableState.On else ToggleableState.Off,
                    onClick = { dontRemind.value = !dontRemind.value }
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.update_dont_remind),
                    fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2.fontSize,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.update_later),
                    onClick = {
                        if (dontRemind.value) {
                            UpdateChecker.markSkipped(context, updateResult.versionName)
                        }
                        isShow.value = false
                        onDismissRequest()
                    }
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.update_now),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        if (dontRemind.value) {
                            UpdateChecker.markSkipped(context, updateResult.versionName)
                        }
                        isShow.value = false
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
                                    AppContext.showToast(message)
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
