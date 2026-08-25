package nep.timeline.cirno.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalUiStyle
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun DownloadProgressDialog(
    show: Boolean,
    progress: Int
) {
    if (!show) return

    if (LocalUiStyle.current == UI_STYLE_MATERIAL) {
        MaterialDownloadProgressDialog(progress)
    } else {
        MiuixDownloadProgressDialog(progress)
    }
}

@Composable
private fun MaterialDownloadProgressDialog(progress: Int) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.downloading)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.download_progress, progress),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun MiuixDownloadProgressDialog(progress: Int) {
    OverlayDialog(
        title = stringResource(R.string.downloading),
        summary = stringResource(R.string.download_progress, progress),
        show = true,
        onDismissRequest = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            MiuixLinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
