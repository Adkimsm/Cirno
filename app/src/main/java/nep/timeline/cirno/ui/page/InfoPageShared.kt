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
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigRepository
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
    val frozenInitFailed: Boolean = false,
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

private fun snapshotToInfoState(snapshot: HookStatusRepository.HookStatusSnapshot): InfoHookStatusState {
    // 冻结模式选 UID 时，frozen 初始化信号不暴露也不触发 root 修复，避免误报；
    // 配置读取失败按默认（UID）处理，同样不暴露
    val frozenModeSelected = RootConfigRepository.ensureLoadedIntoMemory() &&
        GlobalVars.globalSettings?.freezerMode == GlobalSettings.FREEZER_MODE_FROZEN
    var frozenInitFailed = snapshot.frozenInitFailed && frozenModeSelected
    // 初始化失败时每次加载快照都尝试用 root 修复 frozen cgroup，成功则不再显示警告
    if (frozenInitFailed && RootFreezerRepository.repairFrozenCgroups()) {
        frozenInitFailed = false
    }
    return InfoHookStatusState(
        connecting = !snapshot.statusBinderAvailable,
        statusBinderAvailable = snapshot.statusBinderAvailable,
        hasError = snapshot.hasError,
        freezerAvailable = !snapshot.statusBinderAvailable || (RootFreezerRepository.isAnyFreezerAvailable() && !snapshot.frozenCgroupFailed),
        frozenInitFailed = frozenInitFailed,
        hookVersion = snapshot.hookVersion,
        hookFingerprint = snapshot.hookFingerprint,
        hookType = snapshot.hookType,
    )
}

@Composable
fun rememberInfoScreenState(context: Context): InfoScreenStateHolder {
    val holder = remember { InfoScreenStateHolder() }
    val binderChecked = XposedServiceStatus.state.value.binderChecked

    LaunchedEffect(Unit) {
        holder.binderState = withContext(Dispatchers.IO) {
            snapshotToInfoState(HookStatusRepository.loadHookStatusSnapshot())
        }
        val result = UpdateChecker.checkForUpdate(context)
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
        val result = UpdateChecker.checkForUpdate(context)
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
