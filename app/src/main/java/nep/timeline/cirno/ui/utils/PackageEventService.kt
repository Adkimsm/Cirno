package nep.timeline.cirno.ui.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import nep.timeline.cirno.ui.IRootPackageService
import java.io.File
import java.lang.reflect.Method

/** ui side helper: package notifications are registered by the root process. */
@SuppressLint("PrivateApi") // This class runs under libsu app_process, never the ordinary app process.
class PackageEventService : RootService() {
    private val handler = Handler(Looper.getMainLooper())
    private var client: Messenger? = null
    private var manager: Any? = null
    private var callback: Any? = null
    private var unregister: Method? = null
    private var listening = false
    private var filesObserver: FileObserver? = null
    private val filesChanged = Runnable { send(Message.obtain(null, INVENTORY_CHANGED)) }
    private val service = object : IRootPackageService.Stub() {
        override fun getEventMessenger(): IBinder = messenger.binder

        override fun getApplicationInfo(packageName: String, userId: Int): ApplicationInfo? {
            // Incoming Binder calls otherwise retain the ui side identity during nested IPC.
            val identity = Binder.clearCallingIdentity()
            return try {
                val pm = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null)
                val api = Class.forName("android.content.pm.IPackageManager")
                val flagsType = if (Build.VERSION.SDK_INT >= 33) Long::class.javaPrimitiveType else Int::class.javaPrimitiveType
                val flags: Any = if (Build.VERSION.SDK_INT >= 33) 0L else 0
                api.getMethod("getApplicationInfo", String::class.java, flagsType, Int::class.javaPrimitiveType)
                    .invoke(pm, packageName, flags, userId) as ApplicationInfo?
            } catch (e: ReflectiveOperationException) {
                Log.w(TAG, "ApplicationInfo unavailable: $packageName#$userId", e)
                null
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }
    }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == REGISTER) {
            client = message.replyTo
            send(Message.obtain(null, READY).apply { arg1 = if (listening) 1 else 0 })
        }
        true
    })

    override fun onCreate() {
        check(Process.myUid() == 0) { "Package listener must run as root" }
        // New Android versions deliver PackageMonitor events directly through PMS.
        // Older versions allow an unattached root process to register with AMS.
        val callbacksRegistered = registerPackageMonitor() ||
            (Build.VERSION.SDK_INT < 35 && registerBroadcastReceiver())
        // PMS filters some callbacks even for UID 0. Watch its committed package state as
        // well, including profile-only installations and creation/removal of whole users.
        listening = watchPackageFiles()
        Log.i(TAG, "Package listener uid=${Process.myUid()} callbacks=$callbacksRegistered files=$listening")
    }

    override fun onBind(intent: Intent): IBinder = service

    private fun registerPackageMonitor(): Boolean = try {
        val pm = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null)!!
        val type = Class.forName("android.os.IRemoteCallback")
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                data.enforceInterface("android.os.IRemoteCallback")
                val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                @Suppress("DEPRECATION")
                val intent = bundle?.getParcelable<Intent>(
                    "android.content.pm.extra.EXTRA_PACKAGE_MONITOR_CALLBACK_RESULT",
                )
                if (intent != null) onPackageEvent(intent)
                return true
            }
        }
        val remote = Class.forName("android.os.IRemoteCallback\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val api = Class.forName("android.content.pm.IPackageManager")
        api.getMethod("registerPackageMonitorCallback", type, Int::class.javaPrimitiveType)
            .invoke(pm, remote, -1)
        manager = pm
        callback = remote
        unregister = api.getMethod("unregisterPackageMonitorCallback", type)
        true
    } catch (e: ReflectiveOperationException) {
        Log.d(TAG, "PackageMonitor callback unavailable", e)
        false
    } catch (e: RuntimeException) {
        Log.w(TAG, "PackageMonitor registration failed", e)
        false
    }

    @SuppressLint("BlockedPrivateApi") // Verify inotify descriptors in the root runtime; fail closed if unavailable.
    private fun watchPackageFiles(): Boolean {
        val system = File("/data/system")
        val users = File(system, "users")
        val userDirectories = users.listFiles() ?: return false
        if (!system.canRead() || !users.canRead()) return false
        val directories = listOf(system, users) + userDirectories.filter {
            it.isDirectory && it.name.toIntOrNull() != null
        }
        // Watch directories rather than the XML inode: AtomicFile replaces that inode.
        val nextObserver = object : FileObserver(directories, CLOSE_WRITE or MOVED_TO or CREATE or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                when {
                    path == "userlist.xml" || path?.toIntOrNull() != null -> handler.post {
                        val available = watchPackageFiles()
                        if (available != listening) {
                            listening = available
                            send(Message.obtain(null, READY).apply { arg1 = if (listening) 1 else 0 })
                        }
                        scheduleFilesChanged()
                    }
                    path == "packages.xml" || path == "package-restrictions.xml" -> handler.post {
                        scheduleFilesChanged()
                    }
                }
            }
        }
        filesObserver?.stopWatching()
        nextObserver.startWatching()
        filesObserver = nextObserver
        // startWatching() silently ignores inotify failures. Do not mark the inventory
        // trustworthy unless every directory actually has a watch descriptor.
        return try {
            val field = FileObserver::class.java.getDeclaredField("mDescriptors").apply { isAccessible = true }
            val descriptors = field.get(nextObserver) as? IntArray
            descriptors != null && descriptors.size == directories.size && descriptors.all { it >= 0 }
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Cannot verify package file watches", e)
            false
        }
    }

    private fun scheduleFilesChanged() {
        handler.removeCallbacks(filesChanged)
        handler.postDelayed(filesChanged, 350)
    }

    private fun registerBroadcastReceiver(): Boolean = try {
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        val receiverType = Class.forName("android.content.IIntentReceiver")
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                data.enforceInterface("android.content.IIntentReceiver")
                val intent = if (data.readInt() != 0) Intent.CREATOR.createFromParcel(data) else null
                if (intent != null) onPackageEvent(intent)
                // All observed package broadcasts are unordered; no finishReceiver is needed.
                return true
            }
        }
        val remote = Class.forName("android.content.IIntentReceiver\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val api = Class.forName("android.app.IActivityManager")
        api.getMethod(
            "registerReceiverWithFeature", Class.forName("android.app.IApplicationThread"),
            String::class.java, String::class.java, String::class.java, receiverType,
            IntentFilter::class.java, String::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).invoke(am, null, null, null, null, remote, filter, null, -1, 2)
        manager = am
        callback = remote
        unregister = api.getMethod("unregisterReceiver", receiverType)
        true
    } catch (e: ReflectiveOperationException) {
        Log.w(TAG, "Cross-user broadcast registration failed", e)
        false
    } catch (e: RuntimeException) {
        Log.w(TAG, "Cross-user broadcast registration failed", e)
        false
    }

    private fun onPackageEvent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            }
            // PMS callbacks use ADDED with EXTRA_REPLACING for completed updates.
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED -> Unit
            else -> return
        }
        val packageName = intent.data?.schemeSpecificPart ?: return
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        val userId = intent.getIntExtra("android.intent.extra.user_handle", if (uid >= 0) uid / 100000 else -1)
        handler.post {
            send(Message.obtain(null, CHANGED).apply {
                data = Bundle().apply {
                    putString("packageName", packageName)
                    putInt("userId", userId)
                    putString("action", intent.action)
                }
            })
        }
    }

    private fun send(message: Message) {
        try {
            client?.send(message)
        } catch (_: RemoteException) {
            client = null
        }
    }

    override fun onDestroy() {
        filesObserver?.stopWatching()
        try {
            unregister?.invoke(manager, callback)
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Package listener cleanup failed", e)
        }
        handler.removeCallbacksAndMessages(null)
        client = null
    }

    companion object {
        const val REGISTER = 1
        const val READY = 2
        const val CHANGED = 3
        const val INVENTORY_CHANGED = 4
        private const val TAG = "CirnoPackageEvents"
    }
}
