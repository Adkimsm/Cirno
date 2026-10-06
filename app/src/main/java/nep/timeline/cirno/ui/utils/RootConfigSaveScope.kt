package nep.timeline.cirno.ui.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RootConfigSaveScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    fun saveGlobalSettingsAsync(
        defaultError: String,
        onFailed: () -> Unit = {},
    ) {
        scope.launch {
            val error = saveMutex.withLock {
                if (RootConfigRepository.saveGlobalSettingsFromMemory()) {
                    null
                } else {
                    RootConfigRepository.getLastErrorOrDefault(defaultError)
                }
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
            val error = saveMutex.withLock {
                if (RootConfigRepository.saveGlobalSettingsFromMemory()) {
                    null
                } else {
                    RootConfigRepository.getLastErrorOrDefault(defaultError)
                }
            }
            withContext(Dispatchers.Main) {
                if (error == null) {
                    onSuccess()
                } else {
                    onFailed()
                    WindowUtils.showToast(error)
                }
            }
        }
    }

    fun saveApplicationSettingsAndThen(
        defaultError: String,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit = {},
    ) {
        scope.launch {
            val error = saveMutex.withLock {
                if (RootConfigRepository.saveApplicationSettingsFromMemory()) {
                    null
                } else {
                    RootConfigRepository.getLastErrorOrDefault(defaultError)
                }
            }
            withContext(Dispatchers.Main) {
                if (error == null) {
                    onSuccess()
                } else {
                    onFailed(error)
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
            val error = saveMutex.withLock {
                if (RootConfigRepository.saveApplicationSettingsFromMemory()) {
                    null
                } else {
                    RootConfigRepository.getLastErrorOrDefault(defaultError)
                }
            }
            if (error != null) {
                withContext(Dispatchers.Main) {
                    onFailed(error)
                }
            }
        }
    }
}
