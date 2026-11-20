package nep.timeline.cirno.ui.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.ui.utils.AppListRepository
import nep.timeline.cirno.ui.utils.PackageEventMonitor

class AppListViewModel : ViewModel() {
    private val _filterApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _cacheFilterApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _search = MutableStateFlow("")
    private val _type = MutableStateFlow(0)
    private val _updatedApps = MutableStateFlow(true)
    private val _hasLoadedOnce = MutableStateFlow(false)
    private val _error = MutableStateFlow(false)
    val search: StateFlow<String> = _search
    val cacheFilterApps: StateFlow<List<AppItem>> = _cacheFilterApps
    val type: StateFlow<Int> = _type
    val filterApps: StateFlow<List<AppItem>> = _filterApps
    val updatedApps: StateFlow<Boolean> = _updatedApps
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce
    val error: StateFlow<Boolean> = _error
    private var visible = false
    private var loadJob: Job? = null
    private var pending = false
    private var forcePending = false
    private var validatePending = false
    private var packageRevision = -1L
    private var configRevision = -1L

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        viewModelScope.launch {
            combine(_filterApps, _search, _type) { apps, query, type ->
                apps.filter { app ->
                    (if (type == 1) app.systemApp else !app.systemApp) &&
                        (query.isEmpty() || app.appName.contains(query, ignoreCase = true)
                            || app.packageName.contains(query, ignoreCase = true))
                }
            }.flowOn(Dispatchers.Default).collect { _cacheFilterApps.value = it }
        }
        viewModelScope.launch {
            PackageEventMonitor.revision.drop(1).debounce(600).collect { revision ->
                if (visible && revision > packageRevision) refresh(queueIfBusy = true)
            }
        }
        viewModelScope.launch {
            AppListRepository.configRevision.drop(1).debounce(100).collect { revision ->
                if (visible && revision > configRevision) refresh(queueIfBusy = true)
            }
        }
    }

    fun updateByQuery(appName: String = _search.value, type: Int) {
        _search.value = appName
        _type.value = type
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (value) {
            PackageEventMonitor.start()
            refresh(validateInventory = !PackageEventMonitor.listening)
        }
    }

    fun update(): Job = refresh(force = true, queueIfBusy = true)

    private fun refresh(
        force: Boolean = false,
        validateInventory: Boolean = false,
        queueIfBusy: Boolean = false,
    ): Job {
        forcePending = forcePending || force
        validatePending = validatePending || validateInventory
        loadJob?.takeIf { it.isActive }?.let {
            pending = pending || queueIfBusy || force || validateInventory
                || PackageEventMonitor.revision.value > packageRevision
                || AppListRepository.configRevision.value > configRevision
            return it
        }
        return viewModelScope.launch {
            if (!_hasLoadedOnce.value) _updatedApps.value = false
            try {
                // Prefer register-then-scan. A missing helper must never block the list forever.
                if (!_hasLoadedOnce.value && PackageEventMonitor.revision.value == 0L) {
                    withTimeoutOrNull(1500) { PackageEventMonitor.revision.first { it > 0 } }
                }
                do {
                    pending = false
                    val forceLoad = forcePending
                    val validate = validatePending
                    forcePending = false
                    validatePending = false
                    packageRevision = PackageEventMonitor.revision.value
                    configRevision = AppListRepository.configRevision.value
                    try {
                        val apps = withContext(Dispatchers.IO) {
                            AppListRepository.load(forceLoad, validate)
                        }
                        _filterApps.value = apps
                        _hasLoadedOnce.value = true
                        _error.value = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _error.value = true
                        Log.w("CirnoAppList", "App list refresh failed", e)
                    }
                } while (pending)
            } finally {
                _updatedApps.value = true
            }
        }.also { loadJob = it }
    }

    init {
        observeChanges()
    }
}
