package nep.timeline.cirno.ui.utils

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ApkInstaller {
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val apkFile = downloadApk(context, url, onProgress)
                if (!ApkSignatureVerifier.verify(context, apkFile)) {
                    apkFile.delete()
                    throw Exception(context.getString(R.string.apk_signature_mismatch))
                }
                installWithRoot(apkFile)
                apkFile.delete()
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File {
        val outputFile = File(context.cacheDir, "update.apk")
        if (outputFile.exists()) outputFile.delete()

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("HTTP error: ${connection.responseCode}")
        }

        val inputStream = connection.inputStream
        val outputStream = FileOutputStream(outputFile)

        val totalBytes = connection.contentLength
        var downloadedBytes = 0

        val buffer = ByteArray(8192)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            if (totalBytes > 0) {
                val progress = (downloadedBytes * 100 / totalBytes)
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
        connection.disconnect()

        return outputFile
    }

    private fun installWithRoot(apkFile: File) {
        val result = Shell.cmd("pm install -r \"${apkFile.absolutePath}\"").exec()
        if (!result.isSuccess) {
            val err = result.err.joinToString("\n")
            throw Exception("Install failed: $err")
        }
    }
}