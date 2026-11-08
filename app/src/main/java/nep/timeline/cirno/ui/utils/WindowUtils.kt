package nep.timeline.cirno.ui.utils

import android.os.Handler
import com.kongzue.dialogx.DialogX
import com.kongzue.dialogx.dialogs.PopTip
import nep.timeline.cirno.utils.EnvUtils

object WindowUtils {
    val handler: Handler = EnvUtils.makeHandler("UI")

    fun showToast(string: String) {
        PopTip.build().setTheme(DialogX.THEME.AUTO).setMessage(string).show()
    }

    fun showToast(resourceId: Int) {
        PopTip.build().setTheme(DialogX.THEME.AUTO).setMessage(resourceId).show()
    }
}
