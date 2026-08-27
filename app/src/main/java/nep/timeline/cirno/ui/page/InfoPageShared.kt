package nep.timeline.cirno.ui.page

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootFreezerRepository
import nep.timeline.cirno.ui.utils.UpdateChecker
import nep.timeline.cirno.ui.utils.UpdateResult
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.utils.XposedServiceStatus

data class InfoHookStatusState(
    val connecting: Boolean = true,
    val statusBinderAvailable: Boolean = false,
    val hasError: Boolean = false,
    val freezerAvailable: Boolean = true,
    val hookVersion: String? = null,
    val hookFingerprint: String? = null,
    val hookType: String? = null,
)

class InfoScreenStateHolder {
    var binderState by mutableStateOf(InfoHookStatusState())
    var updateResult by mutableStateOf<UpdateResult?>(null)
    var showUpdateDialog by mutableStateOf(false)
    var isCheckingUpdate by mutableStateOf(false)

    fun dismissUpdateDialog() {
        showUpdateDialog = false
    }

    fun startUpdateCheck() {
        isCheckingUpdate = true
    }
}

private fun snapshotToInfoState(snapshot: HookStatusRepository.HookStatusSnapshot) = InfoHookStatusState(
    connecting = !snapshot.statusBinderAvailable,
    statusBinderAvailable = snapshot.statusBinderAvailable,
    hasError = snapshot.hasError,
    freezerAvailable = !snapshot.statusBinderAvailable || RootFreezerRepository.isAnyFreezerAvailable(),
    hookVersion = snapshot.hookVersion,
    hookFingerprint = snapshot.hookFingerprint,
    hookType = snapshot.hookType,
)

@Composable
fun rememberInfoScreenState(context: Context): InfoScreenStateHolder {
    val holder = remember { InfoScreenStateHolder() }
    val binderChecked = XposedServiceStatus.state.value.binderChecked

    LaunchedEffect(Unit) {
        holder.binderState = withContext(Dispatchers.IO) {
            snapshotToInfoState(HookStatusRepository.loadHookStatusSnapshot())
        }
        val result = UpdateChecker.checkForUpdate()
        if (result != null && !UpdateChecker.isSkipped(context, result.versionName)) {
            holder.updateResult = result
            holder.showUpdateDialog = true
        }
    }

    LaunchedEffect(binderChecked) {
        if (!binderChecked) return@LaunchedEffect
        holder.binderState = withContext(Dispatchers.IO) {
            snapshotToInfoState(HookStatusRepository.loadHookStatusSnapshot())
        }
    }

    LaunchedEffect(holder.isCheckingUpdate) {
        if (!holder.isCheckingUpdate) return@LaunchedEffect
        val result = UpdateChecker.checkForUpdate()
        holder.isCheckingUpdate = false
        if (result == null) {
            WindowUtils.showToast(context.getString(R.string.update_already_latest))
        } else {
            holder.updateResult = result
            holder.showUpdateDialog = true
        }
    }

    return holder
}
