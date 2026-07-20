package nep.timeline.cirno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.configs.ConfigManager
import nep.timeline.cirno.configs.ConfigManagerJson.ReadResult
import nep.timeline.cirno.ui.app.AppTheme
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.app.keyColorFor
import nep.timeline.cirno.ui.ApplicationHome
import nep.timeline.cirno.ui.page.material.MaterialApplicationHome
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.MiuixBackground

class ApplicationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.init(this)
        BackgroundManager.init(this)
        enableEdgeToEdge()
        setContent {
            var configLoaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    if (ConfigManager.manager.readConfigSU() == ReadResult.MISSING) {
                        ConfigManager.manager.saveConfigSU()
                    }
                }
                configLoaded = true
            }
            AppTheme(
                uiStyle = if (configLoaded) GlobalVars.globalSettings?.uiStyle ?: 0 else 0,
                colorMode = if (configLoaded) GlobalVars.globalSettings?.colorMode ?: 0 else 0,
                keyColor = if (configLoaded) keyColorFor(GlobalVars.globalSettings?.themeKeyColor ?: 0) else null,
                paletteStyle = if (configLoaded) GlobalVars.globalSettings?.themePaletteStyle ?: 0 else 0,
                colorSpec = if (configLoaded) GlobalVars.globalSettings?.themeColorSpec ?: 0 else 0,
            ) {
                if (GlobalVars.globalSettings?.uiStyle == UI_STYLE_MATERIAL) {
                    MaterialApplicationHome(this)
                } else {
                    ApplicationHome(this)
                }
            }
        }
    }
}
