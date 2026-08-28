package nep.timeline.cirno.ui.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toUri
import nep.timeline.cirno.ApplicationActivity
import nep.timeline.cirno.R
import nep.timeline.cirno.entity.AppItem

@SuppressLint("StaticFieldLeak")
object AppContext {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    lateinit var context: Context
    private var cryMediaPlayer: MediaPlayer? = null

    fun init(context: Context) {
        AppContext.context = context.applicationContext
        cryMediaPlayer = null
    }

    fun showToast(string: String) {
        handler.post {
            Toast.makeText(context, string, Toast.LENGTH_LONG).show()
        }
    }

    fun playCry() {
        cryMediaPlayer?.start()
    }

    fun showToast(id: Int) {
        showToast(context.resources.getString(id))
    }

    @Deprecated("Please use LocalUriHandler instead")
    fun jumpUrl(url: String) {
        val intent = Intent()
        intent.action = "android.intent.action.VIEW"
        intent.data = url.toUri()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun enterAppPage(appItem: AppItem) {
        val intent = Intent()
        intent.setClass(context, ApplicationActivity::class.java)

        intent.putExtra("appName", appItem.appName)
        intent.putExtra("userId", appItem.userId.toString())
        intent.putExtra("packageName", appItem.packageName)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
