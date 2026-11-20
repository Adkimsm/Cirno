package nep.timeline.cirno.ui.utils

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.configs.settings.GlobalSettings
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.provide.BatteryOptimizationBinder
import nep.timeline.cirno.provide.BatteryOptimizationBinderFacade
import nep.timeline.cirno.utils.PKGUtils
import java.io.File
import java.util.Locale

/** Access load() from the single AppListViewModel loader; event callbacks only mark dirty keys. */
object AppListRepository {
    private data class Metadata(
        val name: String,
        val info: ApplicationInfo?,
        val system: Boolean,
        val apkModified: Long,
    )
    private data class IconKey(val packageName: String, val userId: Int, val info: ApplicationInfo)
    private val icons = LruCache<IconKey, Drawable>(128)
    private val lock = Any()
    private val metadata = HashMap<String, Metadata>()
    private var installedKeys: Set<String>? = null
    private val dirtyKeys = HashSet<String>()
    private val dirtyPackages = HashSet<String>()
    private var inventoryDirty = true
    private var clearMetadata = false
    private var configuration: Configuration? = null
    private val _configRevision = MutableStateFlow(0L)
    val configRevision = _configRevision.asStateFlow()

    fun notifyConfigChanged() {
        synchronized(lock) { _configRevision.value++ }
    }

    fun invalidatePackage(packageName: String, userId: Int, action: String? = null) {
        synchronized(lock) {
            inventoryDirty = true
            if (userId < 0 || action == Intent.ACTION_PACKAGE_REPLACED || action == Intent.ACTION_PACKAGE_ADDED) {
                // An APK update can change resources for every user with this package.
                dirtyPackages.add(packageName)
            } else {
                dirtyKeys.add("$packageName#$userId")
            }
        }
    }

    fun invalidateAll() {
        synchronized(lock) {
            inventoryDirty = true
            clearMetadata = true
        }
    }

    fun invalidateInventory() {
        synchronized(lock) { inventoryDirty = true }
    }

    fun load(force: Boolean = false, validateInventory: Boolean = false): List<AppItem> {
        val started = SystemClock.elapsedRealtime()
        val context = AppContext.context
        val pm = context.packageManager
        val newConfiguration = Configuration(context.resources.configuration)
        val dirty: Set<String>
        val packages: Set<String>
        val enumerate: Boolean
        synchronized(lock) {
            if (configuration != newConfiguration) {
                configuration = newConfiguration
                clearMetadata = true
            }
            if (force || clearMetadata) {
                metadata.clear()
                icons.evictAll()
            }
            clearMetadata = false
            dirty = dirtyKeys.toSet()
            packages = dirtyPackages.toSet()
            dirtyKeys.clear()
            dirtyPackages.clear()
            enumerate = force || validateInventory || inventoryDirty || installedKeys == null
            inventoryDirty = false
        }
        metadata.keys.removeAll { it in dirty || it.substringBeforeLast('#') in packages }
        val keys = try {
            if (enumerate) RootPackageRepository.loadManagedAppKeySet().also { installedKeys = it }
            else installedKeys.orEmpty()
        } catch (e: Exception) {
            synchronized(lock) { inventoryDirty = true }
            throw e
        }
        metadata.keys.retainAll(keys)
        val inventoryMs = SystemClock.elapsedRealtime() - started
        var misses = 0
        val apps = keys.map { key ->
            val packageName = key.substringBeforeLast('#')
            val userId = key.substringAfterLast('#').toInt()
            val cached = metadata[key]?.takeIf {
                !enumerate || (it.apkModified > 0L && it.info?.sourceDir?.let { path ->
                    File(path).lastModified() == it.apkModified
                } == true)
            }
            val info = cached ?: loadMetadata(pm, packageName, userId).also {
                metadata.remove(key)
                if (it.info != null) metadata[key] = it
                misses++
            }
            AppItem().apply {
                this.packageName = packageName
                this.userId = userId
                appName = info.name
                applicationInfo = info.info
                systemApp = info.system
                white = AppConfigs.isWhiteApp(packageName, userId)
                black = AppConfigs.isBlackApp(packageName, userId)
                backgroundPlay = AppConfigs.isBackgroundPlayAllowed(packageName, userId)
                locationCheck = if (AppConfigs.isLocationUseAllowed(packageName, userId)) 1 else 0
                networkCheck = AppConfigs.isNetworkMessageAllowed(packageName, userId)
                networkSpeedEnabled = AppConfigs.isNetworkSpeedAllowed(packageName, userId)
                blockAutostart = AppConfigs.isAutostartBlocked(packageName, userId)
                backgroundOomAdj = AppConfigs.getBackgroundOomAdj(packageName, userId)
                processConfig = AppConfigs.getExcludedProcesses(packageName, userId).isNotEmpty()
                    || AppConfigs.getKilledProcesses(packageName, userId).isNotEmpty()
            }
        }
        val metadataMs = SystemClock.elapsedRealtime() - started - inventoryMs
        val states = if (GlobalSettings.BATTERY_OPT_MODE_APP == GlobalVars.globalSettings?.batteryOptimizationMode) {
            BatteryOptimizationBinder.getInstance()?.getBatteryOptimizationStates(
                apps.map { it.packageName }, apps.map { it.userId }.toIntArray(),
            )
        } else null
        apps.forEachIndexed { index, app ->
            app.idle = states?.getOrNull(index) == BatteryOptimizationBinderFacade.BATTERY_OPTIMIZATION_ENABLED
        }
        val sorted = apps.sortedWith(compareBy<AppItem> { priority(it) }
            .thenBy { it.appName.lowercase(Locale.ROOT) })
        Log.d("CirnoAppList", "apps=${apps.size} enumerate=$enumerate metadataMisses=$misses " +
            "inventoryMs=$inventoryMs metadataMs=$metadataMs totalMs=${SystemClock.elapsedRealtime() - started}")
        return sorted
    }

    private fun loadMetadata(pm: PackageManager, packageName: String, userId: Int): Metadata {
        val info = if (userId == android.os.Process.myUserHandle().hashCode()) {
            try { pm.getApplicationInfo(packageName, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        } else {
            PackageEventMonitor.getApplicationInfo(packageName, userId)
        }
        val suffix = if (userId == 0) "" else "#$userId"
        val name = try { info?.loadLabel(pm)?.toString() ?: packageName } catch (_: RuntimeException) { packageName }
        return Metadata(name + suffix, info, info?.let(PKGUtils::isSystemApp) ?: false,
            info?.sourceDir?.let { File(it).lastModified() } ?: 0L)
    }

    fun cachedIcon(app: AppItem): Drawable? = app.applicationInfo?.let {
        icons.get(IconKey(app.packageName, app.userId, it))
    }

    fun loadIcon(app: AppItem): Drawable? {
        val info = app.applicationInfo ?: return null
        val key = IconKey(app.packageName, app.userId, info)
        icons.get(key)?.let { return it }
        return try {
            info.loadIcon(AppContext.context.packageManager)?.also { icons.put(key, it) }
        } catch (e: RuntimeException) {
            Log.w("CirnoAppList", "Icon unavailable: ${app.packageName}#${app.userId}", e)
            null
        }
    }

    private fun priority(app: AppItem): Int = when {
        app.white -> 0
        app.backgroundPlay || app.locationCheck != 0 || app.networkCheck || app.networkSpeedEnabled
            || app.processConfig || app.blockAutostart || AppConfigs.isValidBackgroundOomAdj(app.backgroundOomAdj) -> 1
        app.black -> 2
        else -> 3
    }
}
