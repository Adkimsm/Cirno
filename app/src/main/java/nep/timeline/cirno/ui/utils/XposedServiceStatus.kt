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
import nep.timeline.cirno.binder.BinderService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val API_MIN_SUPPORTED = 101
private const val API_HOT_RELOAD = 102
private const val BINDER_POLL_INTERVAL_MS = 1_000L
private const val HOOK_STATUS_POLL_INTERVAL_MS = 100L
private val REQUIRED_SCOPES = listOf("system", "com.android.systemui")

object XposedServiceStatus {
    private const val TAG = "XposedServiceStatus"
    private val started = AtomicBoolean(false)
    private val binderWaitInFlight = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = mutableStateOf(ModuleStatus())
    private val binderWaitExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Cirno-Binder").apply { isDaemon = true }
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
                startBinderWaitIfNeeded()
            }

            override fun onServiceDied(service: XposedService) {
                Log.w(TAG, "Xposed service died: ${service.frameworkName} ${service.frameworkVersion}")
                if (currentService === service) {
                    currentService = null
                }
                binderWaitInFlight.set(false)
                mutableState.value = mutableState.value.copy(
                    active = false,
                    scope = emptyList(),
                    waitingBinder = false,
                    binderChecked = false,
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
                        waitForBinderThenComplete(
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
                    waitForBinderThenComplete(
                        HotReloadOutcome(targetCount = targets.size, results = results.toList())
                    ) { onComplete(it) }
                }
            }
        }
    }

    private fun waitForBinderThenComplete(outcome: HotReloadOutcome, onComplete: (HotReloadOutcome) -> Unit) {
        binderWaitInFlight.set(true)
        mainHandler.post {
            mutableState.value = mutableState.value.copy(
                waitingBinder = true,
                binderChecked = false,
            )
        }
        binderWaitExecutor.execute {
            val ready = waitForBinderReadyBlocking()
            mainHandler.post {
                binderWaitInFlight.set(false)
                mutableState.value = mutableState.value.copy(
                    waitingBinder = false,
                    binderChecked = ready,
                )
                onComplete(outcome)
            }
        }
    }

    private fun startBinderWaitIfNeeded() {
        if (BinderService.isConnected()) {
            mainHandler.post {
                mutableState.value = mutableState.value.copy(
                    binderChecked = true,
                    waitingBinder = false,
                )
            }
            return
        }
        if (!binderWaitInFlight.compareAndSet(false, true)) {
            return
        }
        mainHandler.post {
            mutableState.value = mutableState.value.copy(
                waitingBinder = true,
                binderChecked = false,
            )
        }
        binderWaitExecutor.execute {
            val ready = waitForBinderReadyBlocking()
            mainHandler.post {
                binderWaitInFlight.set(false)
                mutableState.value = mutableState.value.copy(
                    waitingBinder = false,
                    binderChecked = ready,
                )
            }
        }
    }

    private fun waitForBinderReadyBlocking(): Boolean {
        while (binderWaitInFlight.get()) {
            BinderService.register(AppContext.context)
            if (BinderService.waitForConnection(BINDER_POLL_INTERVAL_MS)) {
                return waitForHookStatusReadyBlocking()
            }
        }
        return false
    }

    private fun waitForHookStatusReadyBlocking(): Boolean {
        while (binderWaitInFlight.get()) {
            val snapshot = HookStatusRepository.loadHookStatusSnapshot()
            if (snapshot.statusBinderAvailable) {
                return true
            }
            sleepQuietly(HOOK_STATUS_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun sleepQuietly(delayMs: Long) {
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
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
    val waitingBinder: Boolean = false,
    val binderChecked: Boolean = false,
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
