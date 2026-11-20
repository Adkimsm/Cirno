package nep.timeline.cirno.ui.utils

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.entity.AppItem

/** Only composed rows load icons; the repository keeps a bounded warm cache. */
@Composable
fun rememberAppListIcon(app: AppItem): Drawable? {
    val icon by produceState(
        initialValue = AppListRepository.cachedIcon(app),
        key1 = app.packageName,
        key2 = app.userId,
        key3 = app.applicationInfo,
    ) {
        // produceState retains its old value when its keys change after an APK update.
        value = AppListRepository.cachedIcon(app)
        value = withContext(Dispatchers.IO) { AppListRepository.loadIcon(app) }
    }
    return icon
}
