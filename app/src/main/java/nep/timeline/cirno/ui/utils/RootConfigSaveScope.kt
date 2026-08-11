package nep.timeline.cirno.ui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RootConfigSaveScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun saveGlobalSettingsAsync(
        defaultError: String,
        onFailed: () -> Unit = {},
    ) {
        scope.launch {
            val error = if (RootConfigRepository.saveGlobalSettingsFromMemory()) {
                null
            } else {
                RootConfigRepository.getLastErrorOrDefault(defaultError)
            }
            if (error != null) {
                withContext(Dispatchers.Main) {
                    onFailed()
                    WindowUtils.showToast(error)
                }
            }
        }
    }

    fun saveGlobalSettingsAndThen(
        defaultError: String,
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {},
    ) {
        scope.launch {
            val success = RootConfigRepository.saveGlobalSettingsFromMemory()
            withContext(Dispatchers.Main) {
                if (success) {
                    onSuccess()
                } else {
                    val error = RootConfigRepository.getLastErrorOrDefault(defaultError)
                    onFailed()
                    WindowUtils.showToast(error)
                }
            }
        }
    }

    // 单应用设置页原本用 rememberCoroutineScope()，用户改完立刻返回会随 composition 一起
    // 取消协程，写盘还没开始就被丢掉，必须走这个进程级 scope
    fun saveApplicationSettingsAsync(
        defaultError: String,
        onFailed: (String) -> Unit = {},
    ) {
        scope.launch {
            val error = if (RootConfigRepository.saveApplicationSettingsFromMemory()) {
                null
            } else {
                RootConfigRepository.getLastErrorOrDefault(defaultError)
            }
            if (error != null) {
                withContext(Dispatchers.Main) {
                    onFailed(error)
                }
            }
        }
    }
}
