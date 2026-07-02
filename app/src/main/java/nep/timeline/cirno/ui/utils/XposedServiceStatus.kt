package nep.timeline.cirno.ui.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

object XposedServiceStatus {
    private const val TAG = "XposedServiceStatus"
    private const val API_HOT_RELOAD = 102
    private val started = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = mutableStateOf(ModuleStatus())
    @Volatile
    private var currentService: XposedService? = null

    val state: State<ModuleStatus> = mutableState

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                Log.i(TAG, "Xposed service connected: ${service.frameworkName} ${service.frameworkVersion}")
                currentService = service
                mutableState.value = ModuleStatus(
                    active = true,
                    frameworkName = service.frameworkName,
                    frameworkVersion = service.frameworkVersion,
                    apiVersion = service.apiVersion,
                    scope = runCatching { service.scope }.getOrDefault(emptyList()),
                )
            }

            override fun onServiceDied(service: XposedService) {
                Log.w(TAG, "Xposed service died: ${service.frameworkName} ${service.frameworkVersion}")
                if (currentService === service) {
                    currentService = null
                }
                mutableState.value = mutableState.value.copy(
                    active = false,
                    scope = emptyList(),
                )
            }
        })
    }

    fun hotReloadRunningTargets(onComplete: (HotReloadOutcome) -> Unit) {
        val service = currentService
        if (service == null || service.apiVersion < API_HOT_RELOAD) {
            mainHandler.post { onComplete(HotReloadOutcome(supported = false)) }
            return
        }

        val targets = runCatching {
            service.runningTargets.filter { target ->
                target.processName == "system_server" ||
                    target.processName == "android" ||
                    target.processName == "com.android.systemui"
            }
        }.getOrElse { throwable ->
            mainHandler.post { onComplete(HotReloadOutcome(error = throwable.message ?: throwable.toString())) }
            return
        }

        if (targets.isEmpty()) {
            mainHandler.post { onComplete(HotReloadOutcome(targetCount = 0)) }
            return
        }

        val lock = Any()
        val results = mutableListOf<String>()
        var remaining = targets.size
        for (target in targets) {
            runCatching {
                service.hotReloadModule(target, null) { reloadedTarget, result ->
                    val done: Boolean
                    synchronized(lock) {
                        results += "${reloadedTarget.processName}: $result"
                        remaining -= 1
                        done = remaining == 0
                    }
                    if (done) {
                        mainHandler.post {
                            onComplete(
                                HotReloadOutcome(
                                    targetCount = targets.size,
                                    results = results.toList(),
                                )
                            )
                        }
                    }
                }
            }.onFailure { throwable ->
                val done: Boolean
                synchronized(lock) {
                    results += "${target.processName}: ${throwable.message ?: throwable.toString()}"
                    remaining -= 1
                    done = remaining == 0
                }
                if (done) {
                    mainHandler.post {
                        onComplete(HotReloadOutcome(targetCount = targets.size, results = results.toList()))
                    }
                }
            }
        }
    }
}

data class ModuleStatus(
    val active: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val apiVersion: Int = 0,
    val scope: List<String> = emptyList(),
) {
    val supportsHotReload: Boolean get() = apiVersion >= 102
}

data class HotReloadOutcome(
    val supported: Boolean = true,
    val targetCount: Int = 0,
    val results: List<String> = emptyList(),
    val error: String? = null,
)
