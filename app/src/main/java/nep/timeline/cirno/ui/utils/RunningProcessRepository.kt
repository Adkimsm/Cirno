package nep.timeline.cirno.ui.utils

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.provide.ApplicationBinder

data class RunningProcessItem(
    val pid: Int = 0,
    val name: String = "",
    val frozen: Boolean = false,
    val rssKb: Long = 0L,
    val cpu: Float = 0f,
)

// Gson allocates instances without invoking constructors, so declared defaults do not apply.
// processes stays nullable to survive payloads such as "{}" from a disconnected binder.
data class RunningProcessSnapshot(
    val uid: Int = -1,
    val processes: List<RunningProcessItem>? = null,
)

object RunningProcessRepository {
    suspend fun load(packageName: String, userId: Int): RunningProcessSnapshot? = withContext(Dispatchers.IO) {
        val binder = ApplicationBinder.getInstance() ?: return@withContext null
        try {
            val json = binder.getRunningProcessesForApp(packageName, userId)
            val snapshot = Gson().fromJson(json, RunningProcessSnapshot::class.java) ?: return@withContext null
            RunningProcessSnapshot(
                uid = snapshot.uid,
                processes = snapshot.processes.orEmpty().sortedWith(
                    compareByDescending<RunningProcessItem> { it.name == packageName }
                        .thenBy { it.name }
                        .thenBy { it.pid },
                ),
            )
        } catch (_: Throwable) {
            null
        }
    }
}
