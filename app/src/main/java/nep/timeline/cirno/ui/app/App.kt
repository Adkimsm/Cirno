package nep.timeline.cirno.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.dialog.XposedCheckDialog
import nep.timeline.cirno.ui.utils.XposedServiceStatus
import nep.timeline.cirno.ui.viewModel.AppUiStateViewModel

@Composable
fun App(
    active: Boolean,
    padding: PaddingValues = PaddingValues(0.dp),
    appUiStateViewModel: AppUiStateViewModel,
) {
    val appState by appUiStateViewModel.state.collectAsStateWithLifecycle()
    val xposedServiceState by derivedStateOf { XposedServiceStatus.state.value }
    val xposedCheckMessage = rememberSaveable { mutableStateOf<String?>(null) }

    val xposedCheckFailureMessage = when {
        xposedServiceState.active && !xposedServiceState.supportsXposedApi -> stringResource(R.string.xposed_api_unsupported)
        else -> null
    }

    LaunchedEffect(xposedCheckFailureMessage) {
        if (xposedCheckFailureMessage != null) {
            xposedCheckMessage.value = xposedCheckFailureMessage
        }
    }

    AppTheme(
        uiStyle = appState.uiStyle,
        colorMode = appState.colorMode,
        keyColor = keyColorFor(appState.themeKeyColor),
        paletteStyle = appState.themePaletteStyle,
        colorSpec = appState.themeColorSpec,
        smoothRounding = false,
    ) {
        CompositionLocalProvider(
            LocalAppState provides appState,
            LocalUpdateAppState provides appUiStateViewModel::update,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                key(appState.uiStyle) {
                    if (appState.uiStyle == UI_STYLE_MATERIAL) {
                        MaterialAppContent(active, padding)
                    } else {
                        AppContent(active, padding)
                    }
                }
                XposedCheckDialog(xposedCheckMessage)
            }
        }
    }
}
