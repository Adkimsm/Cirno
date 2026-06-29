package nep.timeline.cirno.ui.viewModel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.kongzue.dialogx.DialogX
import com.kongzue.dialogx.dialogs.PopTip
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.policy.FreezeExemption
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.provide.ApplicationBinder
import nep.timeline.cirno.ui.custom.CustomBasicComponent
import nep.timeline.cirno.ui.utils.AppContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Composable
fun FrozenAppItemCompose(
    app: AppItem
) {
    val message = stringResource(R.string.system_not_flagged_but_frozen)
    val networkSpeedFailedText = stringResource(R.string.network_speed_failed)
    val compactionToastText = stringResource(R.string.compaction_toast_simple)
    val scope = rememberCoroutineScope()
    val subtitleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    val subtitleText = buildAnnotatedString {
        withStyle(SpanStyle(color = subtitleColor)) {
            append(app.applicationProcessCount.toString() + stringResource(R.string.process))
            if (app.frozenProcessCount > 0) {
                append(" " + app.frozenProcessCount.toString() + stringResource(R.string.is_frozen) + "  ")
                withStyle(SpanStyle(color = Color(0xFFFF8C00))) {
                    append("V2")
                }
            }
        }
        
        if (app.frozenType != null && app.frozenType.equals("SYSTEM_NOT_FLAGGED_BUT_FROZEN")) {
            append(" ")
            withStyle(SpanStyle(color = Color(0xFFD13636))) {
                append(stringResource(R.string.frozen_wrong))
            }
        }
    }

    CustomBasicComponent(
        title = app.appName,
        subtitleAnnotated = subtitleText,
        rightText = String.format(Locale.ROOT, "%.2f%%", app.cpuUsage),
        rightTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        leftAction = {
            Box(
                modifier = Modifier.size(64.dp).padding(end = 16.dp)
            ) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.appIcon),
                    contentDescription = app.appName,
                    modifier = Modifier.size(64.dp)
                )
                if (app.compactedProcessCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            fontSize = 10.sp,
                            color = Color.White,
                        )
                    }
                }
            }
        },
        rightActions = {
            Text(
                text = getMemSize(app.rss),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        },
        modifier = Modifier.combinedClickable(
            onLongClick = {
                AppContext.enterAppPage(app)
            },
            onClick = {
                if (app.networkSpeedEnabled) {
                    scope.launch {
                        val speedText = withContext(Dispatchers.IO) {
                            val binder = ApplicationBinder.getInstance() ?: return@withContext null
                            try {
                                val json = binder.getNetworkSpeed(app.packageName, app.userId)
                                val rx = json.substringAfter("\"rx\":").substringBefore(",").trim().toLongOrNull() ?: 0L
                                val tx = json.substringAfter("\"tx\":").substringBefore("}").trim().toLongOrNull() ?: 0L
                                "\u2191${formatSpeed(tx)} \u2193${formatSpeed(rx)}"
                            } catch (_: Exception) {
                                null
                            }
                        }
                        PopTip.build().setTheme(DialogX.THEME.AUTO).setMessage(speedText ?: networkSpeedFailedText).show()
                    }
                } else if (app.frozenType != null && app.frozenType.equals("SYSTEM_NOT_FLAGGED_BUT_FROZEN"))
                    PopTip.build().setTheme(DialogX.THEME.AUTO).setMessage(message).show()
                else if (!app.isFrozen) {
                    val reason = FreezeExemption.fromReason(app.notFrozenReason).displayText
                    PopTip.build().setTheme(DialogX.THEME.AUTO).setMessage(reason).show()
                } else if (app.compactedProcessCount > 0) {
                    val text = stringResource(R.string.compaction_toast_simple, app.compactedProcessCount)
                    PopTip.build().setTheme(DialogX.THEME.AUTO).setMessage(text).show()
                }
            }
        ),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    )
}

private fun getMemSize(mem: Long): String {
    val bigDecimal = BigDecimal(mem)
    if (mem < 1000) return mem.toString() + "KB"
    if (mem < 1024000) return bigDecimal.divide(BigDecimal(1024), 0, RoundingMode.HALF_UP)
        .toString() + "MB"
    return bigDecimal.divide(BigDecimal(1048576), 2, RoundingMode.HALF_UP).toString() + "GB"
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "${bytesPerSec}B/s"
    if (bytesPerSec < 1024 * 1024) return "${BigDecimal(bytesPerSec).divide(BigDecimal(1024), 1, RoundingMode.HALF_UP)}KB/s"
    return "${BigDecimal(bytesPerSec).divide(BigDecimal(1048576), 2, RoundingMode.HALF_UP)}MB/s"
}
