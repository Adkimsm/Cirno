package nep.timeline.cirno.ui.utils

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun currentWindowWidthSizeClass(): WindowWidthSizeClass {
    val context = LocalContext.current
    val activity = context as? Activity ?: context.findActivity()
    checkNotNull(activity) { "Window size class requires an Activity context" }
    return calculateWindowSizeClass(activity).widthSizeClass
}

@Composable
fun shouldShowSplitPane(): Boolean =
    currentWindowWidthSizeClass() != WindowWidthSizeClass.Compact

@Composable
fun shouldShowExpandedPane(): Boolean =
    currentWindowWidthSizeClass() == WindowWidthSizeClass.Expanded

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
