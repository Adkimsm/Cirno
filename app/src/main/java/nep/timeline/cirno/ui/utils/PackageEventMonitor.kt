package nep.timeline.cirno.ui.utils

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nep.timeline.cirno.ui.IRootPackageService

object PackageEventMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    var listening = false
        private set
    private var bound = false
    @Volatile
    private var service: IRootPackageService? = null
    private var retryDelayMs = 1000L
    private val reconnect = Runnable { start() }
    private val bindTimeout = Runnable {
        if (bound && service == null) {
            bound = false
            // Unbinding invokes onServiceDisconnected; the guard there prevents recursion.
            RootService.unbind(connection)
            scheduleReconnect()
        }
    }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            PackageEventService.READY -> {
                listening = message.arg1 == 1
                retryDelayMs = 1000L
                // Registration precedes the initial/reconnect scan.
                AppListRepository.invalidateAll()
                _revision.value++
            }
            PackageEventService.CHANGED -> {
                val name = message.data.getString("packageName")
                if (name != null) {
                    AppListRepository.invalidatePackage(
                        name,
                        message.data.getInt("userId", -1),
                        message.data.getString("action"),
                    )
                } else {
                    AppListRepository.invalidateAll()
                }
                _revision.value++
            }
            PackageEventService.INVENTORY_CHANGED -> {
                AppListRepository.invalidateInventory()
                _revision.value++
            }
        }
        true
    })

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            handler.removeCallbacks(bindTimeout)
            try {
                val remote = IRootPackageService.Stub.asInterface(binder)
                service = remote
                Messenger(remote.eventMessenger).send(Message.obtain(null, PackageEventService.REGISTER).apply {
                    replyTo = messenger
                })
            } catch (e: RemoteException) {
                onServiceDisconnected(name)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!bound) return
            listening = false
            service = null
            bound = false
            AppListRepository.invalidateAll()
            handler.removeCallbacks(bindTimeout)
            scheduleReconnect()
        }

        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
    }

    fun getApplicationInfo(packageName: String, userId: Int): ApplicationInfo? = try {
        service?.getApplicationInfo(packageName, userId)
    } catch (e: RemoteException) {
        Log.w("CirnoPackageEvents", "Root metadata query failed", e)
        null
    }

    fun isMetadataAvailable(): Boolean = service != null

    fun loadApplicationInfo(pm: PackageManager, packageName: String, userId: Int): ApplicationInfo? {
        if (userId != android.os.Process.myUserHandle().hashCode()) {
            return getApplicationInfo(packageName, userId)
        }
        return try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Called from the main thread. The bound helper lives only as long as ui side. */
    fun start() {
        if (bound) return
        handler.removeCallbacks(reconnect)
        bound = true
        try {
            RootService.bind(Intent(AppContext.context, PackageEventService::class.java), connection)
            handler.postDelayed(bindTimeout, 15000)
        } catch (e: RuntimeException) {
            bound = false
            Log.w("CirnoPackageEvents", "Root helper bind failed", e)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        handler.removeCallbacks(reconnect)
        handler.postDelayed(reconnect, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(30000L)
    }
}
