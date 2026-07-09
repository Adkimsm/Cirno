package nep.timeline.cirno.ui.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.AppTheme
import nep.timeline.cirno.ui.app.UI_STYLE_MIUIX
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import kotlin.system.exitProcess

@Composable
fun XposedApiUnsupportedDialog(showDialog: MutableState<Boolean>) {
    if (!showDialog.value) {
        return
    }

    AppTheme(uiStyle = UI_STYLE_MIUIX, smoothRounding = false) {
        MiuixXposedApiUnsupportedDialog(showDialog)
    }
}

@Composable
private fun MiuixXposedApiUnsupportedDialog(showDialog: MutableState<Boolean>) {
    OverlayDialog(
        title = stringResource(R.string.warning),
        summary = stringResource(R.string.xposed_api_unsupported),
        show = showDialog.value,
        onDismissRequest = {
            showDialog.value = false
            exitProcess(0)
        },
    ) {
        MiuixTextButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.ok),
            colors = ButtonDefaults.textButtonColorsPrimary(),
            onClick = {
                showDialog.value = false
                exitProcess(0)
            }
        )
    }
}
