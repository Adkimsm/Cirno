package nep.timeline.cirno.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalUiStyle
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.utils.AddOnStatusRepository.Status
import nep.timeline.cirno.ui.utils.WindowUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

private const val ADD_ON_DOWNLOAD_URL = "https://openapi.shrairo.top/Libmodule"

@Composable
fun AddOnInstallDialog(
    status: Status?,
    onDismissRequest: () -> Unit,
) {
    if (status != Status.NOT_INSTALLED && status != Status.DISABLED) return

    val uriHandler = LocalUriHandler.current
    val openLinkFailed = stringResource(R.string.open_link_failed)
    val onDownload = {
        onDismissRequest()
        runCatching { uriHandler.openUri(ADD_ON_DOWNLOAD_URL) }
            .onFailure { WindowUtils.showToast(openLinkFailed) }
        Unit
    }

    if (LocalUiStyle.current == UI_STYLE_MATERIAL) {
        MaterialAddOnInstallDialog(status, onDismissRequest, onDownload)
    } else {
        MiuixAddOnInstallDialog(status, onDismissRequest, onDownload)
    }
}

@Composable
private fun MaterialAddOnInstallDialog(
    status: Status,
    onDismissRequest: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add_on_dialog_title)) },
        text = {
            Text(
                stringResource(
                    if (status == Status.DISABLED) {
                        R.string.add_on_disabled_message
                    } else {
                        R.string.add_on_not_installed_message
                    }
                )
            )
        },
        confirmButton = {
            if (status == Status.DISABLED) {
                Button(onClick = onDismissRequest) {
                    Text(stringResource(R.string.close))
                }
            } else {
                Button(onClick = onDownload) {
                    Text(stringResource(R.string.download))
                }
            }
        },
        dismissButton = if (status == Status.NOT_INSTALLED) {
            {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun MiuixAddOnInstallDialog(
    status: Status,
    onDismissRequest: () -> Unit,
    onDownload: () -> Unit,
) {
    OverlayDialog(
        title = stringResource(R.string.add_on_dialog_title),
        summary = stringResource(
            if (status == Status.DISABLED) {
                R.string.add_on_disabled_message
            } else {
                R.string.add_on_not_installed_message
            }
        ),
        show = true,
        onDismissRequest = onDismissRequest,
    ) {
        if (status == Status.DISABLED) {
            MiuixTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.close),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onDismissRequest,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                )
                MiuixTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.download),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onDownload,
                )
            }
        }
    }
}
