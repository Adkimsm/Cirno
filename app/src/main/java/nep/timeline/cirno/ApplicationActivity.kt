package nep.timeline.cirno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.ui.app.AppTheme
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.app.keyColorFor
import nep.timeline.cirno.ui.ApplicationHome
import nep.timeline.cirno.ui.page.material.MaterialApplicationHome
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.MiuixBackground
import nep.timeline.cirno.ui.utils.RootConfigRepository
import nep.timeline.cirno.ui.utils.UiPrefs

class ApplicationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.init(this)
        BackgroundManager.init(this)
        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    RootConfigRepository.ensureLoadedIntoMemory()
                }
            }
            AppTheme(
                uiStyle = UiPrefs.getUiStyle(this),
                colorMode = UiPrefs.getColorMode(this),
                keyColor = keyColorFor(UiPrefs.getThemeKeyColor(this)),
                paletteStyle = UiPrefs.getThemePaletteStyle(this),
                colorSpec = UiPrefs.getThemeColorSpec(this),
            ) {
                if (UiPrefs.getUiStyle(this) == UI_STYLE_MATERIAL) {
                    MaterialApplicationHome(this)
                } else {
                    ApplicationHome(this)
                }
            }
        }
    }
}
