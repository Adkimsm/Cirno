package nep.timeline.cirno.ui.utils

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.provide.StatusBinder
import java.io.InputStream
import java.nio.charset.StandardCharsets

enum class LogEmptyReason {
    ReadFailed,
    FileLogFailed,
}

data class LogSnapshot(
    val lines: List<String>,
    val size: Long,
    val truncated: Boolean,
    val emptyReason: LogEmptyReason? = null,
    val emptyReasonDetail: String? = null,
)

object RootLogRepository {
    private const val MAX_LOG_BYTES = 4 * 1024 * 1024L
    private const val MAX_LOG_LINES = 10_000
    private const val SIGNAL_FILE_LOG_ERROR = "file_log_error"

    fun getLogSnapshot(): LogSnapshot {
        val file = SuFile(GlobalVars.LOG_DIR, "current.log")
        return readSnapshot(file)
    }

    fun getLogContentAfter(byteOffset: Long): LogSnapshot {
        val file = SuFile(GlobalVars.LOG_DIR, "current.log")
        return try {
            if (!file.exists()) return emptySnapshotWithFileLogError()
            val size = file.length()
            if (size < byteOffset || byteOffset < 0L) return readSnapshot(file)
            if (size == byteOffset) {
                return if (size == 0L) emptySnapshotWithFileLogError() else LogSnapshot(emptyList(), size, false)
            }

            SuFileInputStream.open(file).use { input ->
                input.skipFully(byteOffset)
                LogSnapshot(
                    lines = bytesToLines(input.readBytes()),
                    size = size,
                    truncated = false,
                )
            }
        } catch (e: Throwable) {
            LogSnapshot(emptyList(), 0L, false, LogEmptyReason.ReadFailed, formatError(e))
        }
    }

    private fun readSnapshot(file: SuFile): LogSnapshot {
        return try {
            if (!file.exists()) return emptySnapshotWithFileLogError()
            val size = file.length()
            val readOffset = (size - MAX_LOG_BYTES).coerceAtLeast(0L)
            val truncatedByBytes = readOffset > 0L

            SuFileInputStream.open(file).use { input ->
                input.skipFully(readOffset)
                var content = String(input.readBytes(), StandardCharsets.UTF_8)
                if (truncatedByBytes) {
                    content = content.substringAfter('\n', "")
                }
                val lines = content.lines().filter { it.isNotBlank() }
                val limitedLines = if (lines.size > MAX_LOG_LINES) {
                    lines.takeLast(MAX_LOG_LINES)
                } else {
                    lines
                }
                val fileLogError = if (limitedLines.isEmpty()) getFileLogError() else null
                LogSnapshot(
                    lines = limitedLines,
                    size = size,
                    truncated = truncatedByBytes || lines.size > MAX_LOG_LINES,
                    emptyReason = if (fileLogError == null) null else LogEmptyReason.FileLogFailed,
                    emptyReasonDetail = fileLogError,
                )
            }
        } catch (e: Throwable) {
            LogSnapshot(emptyList(), 0L, false, LogEmptyReason.ReadFailed, formatError(e))
        }
    }

    private fun emptySnapshotWithFileLogError(): LogSnapshot {
        val fileLogError = getFileLogError()
        return LogSnapshot(
            lines = emptyList(),
            size = 0L,
            truncated = false,
            emptyReason = if (fileLogError == null) null else LogEmptyReason.FileLogFailed,
            emptyReasonDetail = fileLogError,
        )
    }

    private fun getFileLogError(): String? {
        return try {
            val value = StatusBinder.getInstance()?.getSignal(SIGNAL_FILE_LOG_ERROR)
            value?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatError(throwable: Throwable): String {
        val message = throwable.message
        return if (message.isNullOrBlank()) {
            throwable.javaClass.simpleName
        } else {
            "${throwable.javaClass.simpleName}: $message"
        }
    }

    private fun bytesToLines(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        return String(bytes, StandardCharsets.UTF_8).lines().filter { it.isNotBlank() }
    }

    private fun InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped <= 0L) break
            remaining -= skipped
        }
    }
}
