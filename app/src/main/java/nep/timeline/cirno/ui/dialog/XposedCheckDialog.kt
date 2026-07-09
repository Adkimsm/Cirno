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
fun XposedCheckDialog(message: MutableState<String?>) {
    if (message.value == null) {
        return
    }

    AppTheme(uiStyle = UI_STYLE_MIUIX, smoothRounding = false) {
        MiuixXposedCheckDialog(message)
    }
}

@Composable
private fun MiuixXposedCheckDialog(message: MutableState<String?>) {
    OverlayDialog(
        title = stringResource(R.string.warning),
        summary = message.value.orEmpty(),
        show = message.value != null,
        onDismissRequest = {
            message.value = null
            exitProcess(0)
        },
    ) {
        MiuixTextButton(
            modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.ok),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    message.value = null
                    exitProcess(0)
                }
            )
    }
}
