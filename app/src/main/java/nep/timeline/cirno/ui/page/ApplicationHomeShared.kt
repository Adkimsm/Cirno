package nep.timeline.cirno.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nep.timeline.cirno.CommonConstants
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.R
import nep.timeline.cirno.binder.BinderService
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.provide.ApplicationBinder
import nep.timeline.cirno.provide.BatteryOptimizationBinder
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.HookStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigSaveScope
import nep.timeline.cirno.utils.PKGUtils

/**
 * 两套 UI（MiuiX / Material）应用配置页共享的状态与业务逻辑。
 *
 * toast 通道通过 [showToast] 参数化：MiuiX 页面传 WindowUtils.showToast，
 * Material 页面使用默认的 AppContext.showToast，保持两页原有提示通道不变。
 *
 * 状态统一由 holder 持有（原先 MiuiX 版部分开关 remember 在 LazyColumn item{}
 * 块内，滚动出屏会丢状态），修复了该潜在问题。
 */
class AppConfigStateHolder(
    val packageName: String,
    val userId: Int,
    val isSystemApp: Boolean,
    val isBuiltinWhitelistApp: Boolean,
    private val showToast: (String) -> Unit = AppContext::showToast,
) {
    // ---- 异步加载状态 ----
    var packetAvailable by mutableStateOf<Boolean?>(null)
    val processList = mutableStateListOf<String>()
    val processExclusions = mutableStateListOf<String>()
    var processListLoaded by mutableStateOf(false)

    // ---- 配置开关状态（初值读 AppConfigs）----
    var black by mutableStateOf(AppConfigs.isBlackApp(packageName, userId))
    var white by mutableStateOf(AppConfigs.isWhiteApp(packageName, userId))
    var userWhitelist by mutableStateOf(AppConfigs.hasUserWhitelist(packageName, userId))
    var batteryOptimizationEnabled by mutableStateOf(true)
    var backgroundPlay by mutableStateOf(AppConfigs.isBackgroundPlayAllowed(packageName, userId))
    var locationUse by mutableStateOf(AppConfigs.isLocationUseAllowed(packageName, userId))
    var networkMessage by mutableStateOf(AppConfigs.isNetworkMessageAllowed(packageName, userId))
    var networkSpeed by mutableStateOf(AppConfigs.isNetworkSpeedAllowed(packageName, userId))
    var blockAutostart by mutableStateOf(AppConfigs.isAutostartBlocked(packageName, userId))
    var memoryTrimEnabled by mutableStateOf(AppConfigs.isMemoryTrimEnabled(packageName, userId))
    var memoryTrimGcEnabled by mutableStateOf(AppConfigs.isMemoryTrimGcEnabled(packageName, userId))
    var backgroundOomAdj by mutableStateOf(AppConfigs.getBackgroundOomAdj(packageName, userId))
    var showBackgroundOomAdjCustomDialog by mutableStateOf(false)

    // ================= 加载（供 LaunchedEffect 调用） =================

    suspend fun loadProcessList() {
        val (names, excluded) = withContext(Dispatchers.IO) {
            val processNames = mutableListOf<String>()
            BinderService.waitForConnection(1500L)
            val appBinder = ApplicationBinder.getInstance()
            if (appBinder != null) {
                try {
                    val json = appBinder.getProcessesForApp(packageName, userId)
                    val type = object : TypeToken<List<String>>() {}.type
                    val parsed: List<String> = Gson().fromJson(json, type) ?: emptyList()
                    processNames.addAll(parsed)
                } catch (_: Throwable) {
                }
            }
            processNames to AppConfigs.getExcludedProcesses(packageName, userId)
        }
        processList.clear()
        processList.addAll(names.filter { it.isNotBlank() }.distinct().sorted())
        processExclusions.clear()
        processExclusions.addAll(excluded)
        processListLoaded = true
    }

    suspend fun loadBatteryOptimization() {
        batteryOptimizationEnabled = withContext(Dispatchers.IO) {
            BatteryOptimizationBinder.getInstance()?.isBatteryOptimizationEnabled(packageName, userId) ?: true
        }
    }

    suspend fun loadPacketAvailable() {
        packetAvailable = withContext(Dispatchers.IO) {
            HookStatusRepository.isPacketAvailable()
        }
    }

    /** packet 不可用且网络消息开关打开时，强制关闭并落盘 */
    fun enforcePacketAvailability() {
        if (packetAvailable == false && networkMessage) {
            networkMessage = false
            AppConfigs.setNetworkMessageAllowed(packageName, userId, false)
            saveApplicationSettingsAsync()
        }
    }

    // ================= 保存包装 =================

    fun saveApplicationSettingsAsync(defaultError: String = "配置更新失败", onFailed: (String) -> Unit = {}) {
        RootConfigSaveScope.saveApplicationSettingsAsync(
            defaultError = defaultError,
            onFailed = onFailed,
        )
    }

    // ================= 业务回调（含 AppConfigs 写入 + 失败回滚） =================

    /** 白名单开关：开启时级联关闭 4 个豁免项，失败回滚 6 个状态 */
    fun onWhiteChanged(checked: Boolean) {
        if (isBuiltinWhitelistApp) return
        val prevWhite = white
        val prevUserWhite = userWhitelist
        val prevBackground = backgroundPlay
        val prevLocation = locationUse
        val prevNetwork = networkMessage
        val prevNetworkSpeed = networkSpeed

        white = checked
        userWhitelist = checked
        AppConfigs.setWhiteApp(packageName, userId, checked)
        if (checked) {
            backgroundPlay = false
            AppConfigs.setBackgroundPlayAllowed(packageName, userId, false)
            locationUse = false
            AppConfigs.setLocationUseAllowed(packageName, userId, false)
            networkMessage = false
            AppConfigs.setNetworkMessageAllowed(packageName, userId, false)
            networkSpeed = false
            AppConfigs.setNetworkSpeedAllowed(packageName, userId, false)
        }

        saveApplicationSettingsAsync("白名单更新失败") { error ->
            white = prevWhite
            userWhitelist = prevUserWhite
            AppConfigs.setWhiteApp(packageName, userId, prevWhite)
            backgroundPlay = prevBackground
            AppConfigs.setBackgroundPlayAllowed(packageName, userId, prevBackground)
            locationUse = prevLocation
            AppConfigs.setLocationUseAllowed(packageName, userId, prevLocation)
            networkMessage = prevNetwork
            AppConfigs.setNetworkMessageAllowed(packageName, userId, prevNetwork)
            networkSpeed = prevNetworkSpeed
            AppConfigs.setNetworkSpeedAllowed(packageName, userId, prevNetworkSpeed)
            showToast(error)
        }
    }

    fun onBlackChanged(checked: Boolean) {
        val prevBlack = black
        black = checked
        AppConfigs.setBlackApp(packageName, userId, checked)

        saveApplicationSettingsAsync("黑名单更新失败") { error ->
            black = prevBlack
            AppConfigs.setBlackApp(packageName, userId, prevBlack)
            showToast(error)
        }
    }

    /** 电池优化开关：重置全局模式为 APP、写应用配置、回写 binder，任一失败都回滚 */
    fun onBatteryOptimizationChanged(enabled: Boolean, errorText: String) {
        val previous = batteryOptimizationEnabled
        val settings = GlobalVars.globalSettings
        val previousMode = settings?.batteryOptimizationMode
        if (settings != null && previousMode != null && previousMode != GlobalSettings.BATTERY_OPT_MODE_APP) {
            settings.batteryOptimizationMode = GlobalSettings.BATTERY_OPT_MODE_APP
            RootConfigSaveScope.saveGlobalSettingsAsync("电池优化模式更新失败") {
                settings.batteryOptimizationMode = previousMode
            }
        }
        batteryOptimizationEnabled = enabled
        AppConfigs.setBatteryOptimizationEnabled(packageName, userId, enabled)
        saveApplicationSettingsAsync("电池优化更新失败") { error ->
            batteryOptimizationEnabled = previous
            AppConfigs.setBatteryOptimizationEnabled(packageName, userId, previous)
            showToast(error)
        }
        CoroutineScope(Dispatchers.IO).launch {
            val success = BatteryOptimizationBinder.getInstance()
                ?.setBatteryOptimizationEnabled(packageName, userId, enabled) == true
            if (!success) {
                withContext(Dispatchers.Main) {
                    batteryOptimizationEnabled = previous
                    AppConfigs.setBatteryOptimizationEnabled(packageName, userId, previous)
                    showToast(errorText)
                }
            }
        }
    }

    fun onBackgroundPlayChanged(checked: Boolean, whitelistBlockedText: String) {
        if (userWhitelist && checked) {
            showToast(whitelistBlockedText)
            return
        }
        val previous = backgroundPlay
        backgroundPlay = checked
        AppConfigs.setBackgroundPlayAllowed(packageName, userId, checked)
        saveApplicationSettingsAsync("后台播放配置更新失败") { error ->
            backgroundPlay = previous
            AppConfigs.setBackgroundPlayAllowed(packageName, userId, previous)
            showToast(error)
        }
    }

    fun onLocationUseChanged(checked: Boolean, whitelistBlockedText: String) {
        if (userWhitelist && checked) {
            showToast(whitelistBlockedText)
            return
        }
        val previous = locationUse
        locationUse = checked
        AppConfigs.setLocationUseAllowed(packageName, userId, checked)
        saveApplicationSettingsAsync("定位配置更新失败") { error ->
            locationUse = previous
            AppConfigs.setLocationUseAllowed(packageName, userId, previous)
            showToast(error)
        }
    }

    fun onNetworkMessageChanged(checked: Boolean, whitelistBlockedText: String) {
        if (userWhitelist && checked) {
            showToast(whitelistBlockedText)
            return
        }
        val previous = networkMessage
        networkMessage = checked
        AppConfigs.setNetworkMessageAllowed(packageName, userId, checked)
        saveApplicationSettingsAsync("网络消息配置更新失败") { error ->
            networkMessage = previous
            AppConfigs.setNetworkMessageAllowed(packageName, userId, previous)
            showToast(error)
        }
    }

    fun onNetworkSpeedChanged(checked: Boolean, whitelistBlockedText: String) {
        if (userWhitelist && checked) {
            showToast(whitelistBlockedText)
            return
        }
        val previous = networkSpeed
        networkSpeed = checked
        AppConfigs.setNetworkSpeedAllowed(packageName, userId, checked)
        saveApplicationSettingsAsync("网速识别配置更新失败") { error ->
            networkSpeed = previous
            AppConfigs.setNetworkSpeedAllowed(packageName, userId, previous)
            showToast(error)
        }
    }

    fun onBlockAutostartChanged(checked: Boolean, whitelistBlockedText: String) {
        if (userWhitelist && checked) {
            showToast(whitelistBlockedText)
            return
        }
        val previous = blockAutostart
        blockAutostart = checked
        AppConfigs.setAutostartBlocked(packageName, userId, checked)
        saveApplicationSettingsAsync("自启动拦截配置更新失败") { error ->
            blockAutostart = previous
            AppConfigs.setAutostartBlocked(packageName, userId, previous)
            showToast(error)
        }
    }

    fun onMemoryTrimChanged(checked: Boolean) {
        val previous = memoryTrimEnabled
        memoryTrimEnabled = checked
        AppConfigs.setMemoryTrimEnabled(packageName, userId, checked)
        saveApplicationSettingsAsync("内存回收配置更新失败") { error ->
            memoryTrimEnabled = previous
            AppConfigs.setMemoryTrimEnabled(packageName, userId, previous)
            showToast(error)
        }
    }

    fun onMemoryTrimGcChanged(checked: Boolean) {
        val previous = memoryTrimGcEnabled
        memoryTrimGcEnabled = checked
        AppConfigs.setMemoryTrimGcEnabled(packageName, userId, checked)
        saveApplicationSettingsAsync("GC 配置更新失败") { error ->
            memoryTrimGcEnabled = previous
            AppConfigs.setMemoryTrimGcEnabled(packageName, userId, previous)
            showToast(error)
        }
    }

    /** OOM 预设下拉：adj 为 null 表示选中"自定义"，打开对话框 */
    fun onBackgroundOomAdjPresetSelected(index: Int, errorText: String) {
        val adj = backgroundOomAdjForPresetIndex(index)
        if (adj == null) {
            showBackgroundOomAdjCustomDialog = true
            return
        }
        commitBackgroundOomAdj(adj, errorText)
    }

    /** 自定义对话框 onConfirm */
    fun commitBackgroundOomAdj(adj: Int, errorText: String) {
        val previous = backgroundOomAdj
        showBackgroundOomAdjCustomDialog = false
        backgroundOomAdj = adj
        AppConfigs.setBackgroundOomAdj(packageName, userId, adj)
        saveApplicationSettingsAsync(errorText) { error ->
            backgroundOomAdj = previous
            AppConfigs.setBackgroundOomAdj(packageName, userId, previous)
            showToast(error)
        }
    }

    fun dismissBackgroundOomAdjDialog() {
        showBackgroundOomAdjCustomDialog = false
    }

    /** 进程行为下拉：behaviorState 由 UI 侧 remember(processName) 持有 */
    fun setProcessBehavior(processName: String, selected: Int, behaviorState: MutableState<Int>) {
        val previous = behaviorState.value
        behaviorState.value = selected
        AppConfigs.setProcessBehavior(packageName, userId, processName, selected)
        saveApplicationSettingsAsync("进程配置更新失败") { error ->
            behaviorState.value = previous
            AppConfigs.setProcessBehavior(packageName, userId, processName, previous)
            showToast(error)
        }
    }
}

@Composable
fun rememberAppConfigState(
    packageName: String,
    userId: Int,
    showToast: (String) -> Unit = AppContext::showToast,
): AppConfigStateHolder {
    val context = LocalContext.current
    val holder = remember(packageName, userId) {
        AppConfigStateHolder(
            packageName = packageName,
            userId = userId,
            isSystemApp = try {
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                PKGUtils.isSystemApp(packageInfo.applicationInfo)
            } catch (_: Throwable) {
                false
            },
            isBuiltinWhitelistApp = CommonConstants.isWhitelistApps(packageName),
            showToast = showToast,
        )
    }
    LaunchedEffect(packageName, userId) { holder.loadProcessList() }
    LaunchedEffect(packageName, userId) { holder.loadBatteryOptimization() }
    LaunchedEffect(Unit) { holder.loadPacketAvailable() }
    LaunchedEffect(holder.packetAvailable, holder.networkMessage) { holder.enforcePacketAvailability() }
    return holder
}

/** OOM 自定义对话框宿主，两套 UI 共用 */
@Composable
fun AppConfigOomAdjDialogHost(holder: AppConfigStateHolder) {
    if (holder.showBackgroundOomAdjCustomDialog) {
        val errorText = stringResource(R.string.background_oom_level_update_failed)
        BackgroundOomAdjCustomDialog(
            initialAdj = holder.backgroundOomAdj,
            onDismissRequest = { holder.dismissBackgroundOomAdjDialog() },
            onConfirm = { holder.commitBackgroundOomAdj(it, errorText) },
        )
    }
}
