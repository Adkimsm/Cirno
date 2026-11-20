package nep.timeline.cirno.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.ui.viewModel.AppListViewModel

data class AppListScreenState(
    val filteredApps: List<AppItem>,
    val searchValue: String,
    val type: Int,
    val updatedApps: Boolean,
    val hasLoadedOnce: Boolean,
    val error: Boolean,
)

@Composable
fun rememberAppListScreenState(viewModel: AppListViewModel, visible: Boolean): AppListScreenState {
    val filteredApps by viewModel.cacheFilterApps.collectAsStateWithLifecycle()
    val searchValue by viewModel.search.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val updatedApps by viewModel.updatedApps.collectAsStateWithLifecycle()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive = remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isActive.value = true
            } else if (event == Lifecycle.Event.ON_STOP) {
                isActive.value = false
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isActive.value, visible) {
        viewModel.setVisible(isActive.value && visible)
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.setVisible(false) }
    }

    return AppListScreenState(
        filteredApps = filteredApps,
        searchValue = searchValue,
        type = type,
        updatedApps = updatedApps,
        hasLoadedOnce = hasLoadedOnce,
        error = error,
    )
}
