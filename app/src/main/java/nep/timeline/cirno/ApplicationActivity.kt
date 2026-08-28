package nep.timeline.cirno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.ui.app.AppTheme
import nep.timeline.cirno.ui.app.UI_STYLE_MATERIAL
import nep.timeline.cirno.ui.app.keyColorFor
import nep.timeline.cirno.ui.ApplicationHome
import nep.timeline.cirno.ui.page.material.LocalSnackbarHostState
import nep.timeline.cirno.ui.page.material.MaterialApplicationHome
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BackgroundManager
import nep.timeline.cirno.ui.utils.MiuixBackground
import nep.timeline.cirno.ui.utils.RootConfigRepository

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
                    RootConfigRepository.ensureLoadedIntoMemory()
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
                val snackbarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (GlobalVars.globalSettings?.uiStyle == UI_STYLE_MATERIAL) {
                            MaterialApplicationHome(this@ApplicationActivity)
                        } else {
                            ApplicationHome(this@ApplicationActivity)
                        }
                        SnackbarHost(hostState = snackbarHostState)
                    }
                }
            }
        }
    }
}
