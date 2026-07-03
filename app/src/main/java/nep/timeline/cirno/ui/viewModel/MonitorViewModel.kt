package nep.timeline.cirno.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.utils.PackageUtils

class MonitorViewModel : ViewModel() {
    private val _filterApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _cacheFilterApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _search = MutableStateFlow("")
    private val _updatedApps = MutableStateFlow(true)
    private val _hasLoadedOnce = MutableStateFlow(false)
    val search: StateFlow<String> = _search
    val cacheFilterApps: StateFlow<List<AppItem>> = _cacheFilterApps
    val filterApps: StateFlow<List<AppItem>> = _filterApps
    val updatedApps: StateFlow<Boolean> = _updatedApps
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce
    private var monitorAppsJob: Job? = null

    private fun autoUpdateCacheFilterApps() {
        viewModelScope.launch {
            combine(_filterApps, _search) { apps, search ->
                apps.asSequence()
                    .filter {
                        if (search.isNotEmpty())
                            it.appName.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
                        else true
                    }.toList()
            }.collect { filteredApps ->
                _cacheFilterApps.value = filteredApps
            }
        }
    }

    fun updateSearch(query: String) {
        _search.value = query
    }

    fun getMonitorApps(showLoading: Boolean = true) {
        if (monitorAppsJob?.isActive == true) {
            return
        }
        if (showLoading) {
            _updatedApps.value = false
        }
        monitorAppsJob = viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                PackageUtils.getFrozenApplication(AppContext.context)
            }
            _filterApps.value = apps
            _hasLoadedOnce.value = true
            _updatedApps.value = true
        }
    }

    init {
        autoUpdateCacheFilterApps()
    }
}
