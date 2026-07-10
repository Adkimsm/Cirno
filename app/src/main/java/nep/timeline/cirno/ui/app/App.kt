package nep.timeline.cirno.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.dialog.XposedCheckDialog
import nep.timeline.cirno.ui.utils.XposedServiceStatus
import nep.timeline.cirno.ui.viewModel.AppUiStateViewModel
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val LOADING_TIMEOUT_MS = 3_500L

@Composable
fun App(
    active: Boolean,
    padding: PaddingValues = PaddingValues(0.dp),
    appUiStateViewModel: AppUiStateViewModel,
) {
    val appState by appUiStateViewModel.state.collectAsStateWithLifecycle()
    val xposedServiceState by derivedStateOf { XposedServiceStatus.state.value }
    val xposedCheckMessage = rememberSaveable { mutableStateOf<String?>(null) }
    var loadingTimedOut by rememberSaveable { mutableStateOf(false) }

    val xposedCheckFailureMessage = when {
        xposedServiceState.active && !xposedServiceState.supportsXposedApi -> stringResource(R.string.xposed_api_unsupported)
        else -> null
    }

    LaunchedEffect(xposedCheckFailureMessage) {
        if (xposedCheckFailureMessage != null) {
            xposedCheckMessage.value = xposedCheckFailureMessage
        }
    }

    LaunchedEffect(
        xposedServiceState.active,
        xposedServiceState.waitingBinder,
        xposedServiceState.binderChecked,
    ) {
        if (xposedServiceState.binderChecked) {
            loadingTimedOut = false
            return@LaunchedEffect
        }
        loadingTimedOut = false
        delay(LOADING_TIMEOUT_MS)
        loadingTimedOut = true
    }

    val isLoading = !loadingTimedOut && !xposedServiceState.binderChecked

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
                if (isLoading) {
                    FullPageLoading(appState.uiStyle)
                }
                XposedCheckDialog(xposedCheckMessage)
            }
        }
    }
}

@Composable
private fun FullPageLoading(uiStyle: Int) {
    val backgroundColor = if (uiStyle == UI_STYLE_MATERIAL) {
        MaterialTheme.colorScheme.background
    } else {
        MiuixTheme.colorScheme.surface
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (uiStyle == UI_STYLE_MATERIAL) {
            CircularProgressIndicator()
        } else {
            InfiniteProgressIndicator()
        }
    }
}
