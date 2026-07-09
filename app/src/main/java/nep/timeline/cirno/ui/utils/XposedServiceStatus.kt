package nep.timeline.cirno.ui.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import nep.timeline.cirno.socket.SocketClient
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val API_MIN_SUPPORTED = 101
private const val API_HOT_RELOAD = 102
private val REQUIRED_SCOPES = listOf("system", "com.android.systemui")

object XposedServiceStatus {
    private const val TAG = "XposedServiceStatus"
    private const val SOCKET_WAIT_TIMEOUT_MS = 10_000L
    private val started = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = mutableStateOf(ModuleStatus())
    private val socketWaitExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Cirno-SocketWait").apply { isDaemon = true }
    }
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
            service.runningTargets.filter(::isReloadableTarget)
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
                        results += formatHotReloadResult(reloadedTarget, result)
                        remaining -= 1
                        done = remaining == 0
                    }
                    if (done) {
                        waitForSocketThenComplete(
                            HotReloadOutcome(
                                targetCount = targets.size,
                                results = results.toList(),
                            )
                        ) { onComplete(it) }
                    }
                }
            }.onFailure { throwable ->
                val done: Boolean
                synchronized(lock) {
                    results += "${formatTarget(target)}: FAILED - ${throwable.message ?: throwable.javaClass.simpleName}"
                    remaining -= 1
                    done = remaining == 0
                }
                if (done) {
                    waitForSocketThenComplete(
                        HotReloadOutcome(targetCount = targets.size, results = results.toList())
                    ) { onComplete(it) }
                }
            }
        }
    }

    private fun waitForSocketThenComplete(outcome: HotReloadOutcome, onComplete: (HotReloadOutcome) -> Unit) {
        mainHandler.post {
            mutableState.value = mutableState.value.copy(waitingSocket = true, socketError = null)
        }
        socketWaitExecutor.execute {
            val connected = SocketClient.getInstance().waitForConnection(SOCKET_WAIT_TIMEOUT_MS)
            val socketError = if (connected) null else currentSocketError()
            mainHandler.post {
                mutableState.value = mutableState.value.copy(waitingSocket = false, socketError = socketError)
                onComplete(outcome)
            }
        }
    }

    fun updateSocketConnectionState(connected: Boolean) {
        mainHandler.post {
            mutableState.value = mutableState.value.copy(
                socketError = if (connected) null else currentSocketError()
            )
        }
    }

    fun dismissSocketError() {
        mutableState.value = mutableState.value.copy(socketError = null)
    }

    private fun currentSocketError(): String {
        return SocketClient.getInstance().lastConnectError ?: "unknown socket connection error"
    }

    private fun isReloadableTarget(target: HookedTarget): Boolean {
        return target.state == HookedTarget.State.STALE ||
            target.state == HookedTarget.State.UP_TO_DATE ||
            target.state == HookedTarget.State.FAILED
    }

    private fun formatHotReloadResult(target: HookedTarget, result: HotReloadResult): String {
        val message = result.message
        return if (message.isNullOrBlank()) {
            "${formatTarget(target)}: ${result.status}"
        } else {
            "${formatTarget(target)}: ${result.status} - $message"
        }
    }

    private fun formatTarget(target: HookedTarget): String {
        return "${target.processName} (pid=${target.pid}, state=${target.state})"
    }
}

data class ModuleStatus(
    val active: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val apiVersion: Int = 0,
    val scope: List<String> = emptyList(),
    val waitingSocket: Boolean = false,
    val socketError: String? = null,
) {
    val supportsXposedApi: Boolean get() = !active || apiVersion >= API_MIN_SUPPORTED
    val missingRequiredScopes: List<String> get() = if (active) REQUIRED_SCOPES.filterNot { it in scope } else emptyList()
    val supportsHotReload: Boolean get() = apiVersion >= API_HOT_RELOAD
}

data class HotReloadOutcome(
    val supported: Boolean = true,
    val targetCount: Int = 0,
    val results: List<String> = emptyList(),
    val error: String? = null,
)
