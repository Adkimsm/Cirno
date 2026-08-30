package nep.timeline.cirno.ui.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val CHECK_URL = "https://cirno.bot.cd/api/check-update"
    private const val PREFS_NAME = "cirno_update"
    private const val KEY_SKIPPED = "update_skipped"
    private const val KEY_SKIPPED_VERSION = "skipped_version_name"

    private val gson = Gson()

    suspend fun checkForUpdate(): UpdateResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL(CHECK_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = gson.fromJson(body, JsonObject::class.java) ?: return@withContext null

            val versionName = json.get("versionName")?.asString ?: return@withContext null
            val downloadUrl = json.get("downloadUrl")?.asString ?: return@withContext null
            val changelog = json.get("changelog")?.asString
            val publishedAt = json.get("publishedAt")?.asString

            val localVersionName = BuildConfig.VERSION_NAME
            if (compareVersions(versionName, localVersionName) <= 0) {
                return@withContext null
            }

            UpdateResult(
                versionName = versionName,
                downloadUrl = downloadUrl,
                changelog = changelog,
                publishedAt = publishedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    fun compareVersions(a: String, b: String): Int {
        val aParts = a.split("-", limit = 2)
        val bParts = b.split("-", limit = 2)

        val aMajor = aParts[0].toIntOrNull() ?: 0
        val bMajor = bParts[0].toIntOrNull() ?: 0
        if (aMajor != bMajor) return aMajor.compareTo(bMajor)

        val aMinor = aParts.getOrNull(1)?.toLongOrNull() ?: 0
        val bMinor = bParts.getOrNull(1)?.toLongOrNull() ?: 0
        return aMinor.compareTo(bMinor)
    }

    fun isSkipped(context: Context, versionName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SKIPPED, false) && prefs.getString(KEY_SKIPPED_VERSION, null) == versionName
    }

    fun markSkipped(context: Context, versionName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIPPED, true)
            .putString(KEY_SKIPPED_VERSION, versionName)
            .apply()
    }

    fun clearSkipped(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIPPED, false)
            .remove(KEY_SKIPPED_VERSION)
            .apply()
    }
}

data class UpdateResult(
    val versionName: String,
    val downloadUrl: String,
    val changelog: String?,
    val publishedAt: String?
)
