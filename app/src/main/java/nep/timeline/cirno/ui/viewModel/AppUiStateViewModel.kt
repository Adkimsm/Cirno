package nep.timeline.cirno.ui.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nep.timeline.cirno.ui.app.AppState
import nep.timeline.cirno.ui.utils.UiPrefs

class AppUiStateViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun loadFromUiPrefs(context: Context) {
        val prefs = UiPrefs.read(context)
        _state.value = AppState(
            uiStyle = prefs.uiStyle,
            navigationStyle = prefs.navigationStyle,
            colorMode = prefs.colorMode,
            themeKeyColor = prefs.themeKeyColor,
            themeColorSpec = prefs.themeColorSpec,
            themePaletteStyle = prefs.themePaletteStyle,
            blur = prefs.blur,
        )
    }

    fun update(transform: (AppState) -> AppState) {
        _state.value = transform(_state.value)
    }
}
