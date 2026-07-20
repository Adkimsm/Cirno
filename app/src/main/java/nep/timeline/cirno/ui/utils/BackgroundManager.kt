package nep.timeline.cirno.ui.utils

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

object BackgroundManager {
    var currentUri by mutableStateOf<Uri?>(null)
        private set

    private const val BACKGROUND_FILE_NAME = "background.jpg"

    // --- 非 blur 模式（仅降 alpha）---
    const val topAppBarAlpha = 0.55f
    const val cardAlpha = 0.55f
    const val forceSmallTop = false

    // --- blur 模式（毛玻璃效果）---
    const val cardAlphaBlurred = 0.35f
    const val cardBlurAlpha = 0.65f
    const val topAppBarBlurAlpha = 0.65f
    const val cardBlurRadius = 45f
    const val topAppBarBlurRadius = 45f

    fun init(context: Context) {
        val file = backgroundFile(context)
        currentUri = if (file.exists()) fileUriWithRevision(file) else null
    }

    fun set(context: Context, sourceUri: Uri): Boolean {
        return try {
            val file = backgroundFile(context)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            currentUri = fileUriWithRevision(file)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun remove(context: Context) {
        backgroundFile(context).delete()
        currentUri = null
    }

    private fun backgroundFile(context: Context): File = File(context.filesDir, BACKGROUND_FILE_NAME)

    private fun fileUriWithRevision(file: File): Uri = Uri.fromFile(file).buildUpon()
        .appendQueryParameter("rev", file.lastModified().toString())
        .build()
}
