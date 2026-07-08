package nep.timeline.cirno.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nep.timeline.cirno.ui.dialog.XposedApiUnsupportedDialog
import nep.timeline.cirno.ui.utils.XposedServiceStatus
import nep.timeline.cirno.ui.viewModel.AppUiStateViewModel

@Composable
fun App(
    active: Boolean,
    padding: PaddingValues = PaddingValues(0.dp),
    appUiStateViewModel: AppUiStateViewModel,
) {
    val appState by appUiStateViewModel.state.collectAsStateWithLifecycle()
    val xposedServiceState = XposedServiceStatus.state.value
    val showXposedApiUnsupportedDialog = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(xposedServiceState.active, xposedServiceState.supportsXposedApi) {
        if (xposedServiceState.active && !xposedServiceState.supportsXposedApi) {
            showXposedApiUnsupportedDialog.value = true
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
            key(appState.uiStyle) {
                if (appState.uiStyle == UI_STYLE_MATERIAL) {
                    MaterialAppContent(active, padding)
                } else {
                    AppContent(active, padding)
                }
            }
            XposedApiUnsupportedDialog(showXposedApiUnsupportedDialog)
        }
    }
}
