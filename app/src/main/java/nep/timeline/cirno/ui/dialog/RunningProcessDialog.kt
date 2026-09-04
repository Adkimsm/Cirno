package nep.timeline.cirno.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nep.timeline.cirno.R
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.ui.page.material.MaterialLoadingIndicator
import nep.timeline.cirno.ui.utils.RunningProcessItem
import nep.timeline.cirno.ui.utils.RunningProcessRepository
import nep.timeline.cirno.ui.utils.RunningProcessSnapshot
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private const val REFRESH_INTERVAL_MS = 1500L
private val FrozenAccent = Color(0xFFFF8C00)

// Fields must be observable and every read must happen inside the dialog body lambda. The Miuix
// dialog content is invoked by an ancestor Scaffold popup host, so a value hoisted outside the
// lambda is only a captured constant for that subtree and never refreshes there.
private class RunningProcessState {
    var snapshot: RunningProcessSnapshot? by mutableStateOf(null)
    var loaded: Boolean by mutableStateOf(false)
    var failed: Boolean by mutableStateOf(false)
}

@Composable
private fun rememberRunningProcesses(app: AppItem): RunningProcessState {
    val state = remember(app.packageName, app.userId) { RunningProcessState() }

    LaunchedEffect(app.packageName, app.userId) {
        while (true) {
            val result = RunningProcessRepository.load(app.packageName, app.userId)
            if (result != null) {
                state.snapshot = result
                state.failed = false
            } else {
                state.failed = true
            }
            state.loaded = true
            delay(REFRESH_INTERVAL_MS)
        }
    }

    return state
}

@Composable
fun MiuixRunningProcessDialog(
    app: AppItem,
    onDismissFinished: () -> Unit,
) {
    val state = rememberRunningProcesses(app)
    // The caller unmounts us from onDismissFinished, so the dialog must stay composed while it
    // animates out. Driving show locally keeps that lifetime inside this composable.
    var show by remember { mutableStateOf(true) }

    OverlayDialog(
        show = show,
        title = app.appName,
        onDismissRequest = { show = false },
        onDismissFinished = onDismissFinished,
        content = {
            val snapshot = state.snapshot
            val processes = snapshot?.processes.orEmpty()

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiuixText(
                        text = stringResource(R.string.running_process_uid, snapshot?.uid ?: -1),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    MiuixText(
                        text = stringResource(
                            R.string.running_process_summary,
                            processes.size,
                            processes.count { it.frozen },
                        ),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(horizontal = 24.dp),
                ) {
                    when {
                        !state.loaded -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator()
                        }

                        processes.isEmpty() -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixText(
                                text = stringResource(
                                    if (state.failed) R.string.running_process_load_failed
                                    else R.string.running_process_empty
                                ),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }

                        else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            processes.forEach { process ->
                                key(process.pid) {
                                    MiuixProcessRow(process)
                                }
                            }
                        }
                    }
                }

                MiuixTextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    text = stringResource(R.string.close),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { show = false },
                )
            }
        },
    )
}

@Composable
private fun MiuixProcessRow(process: RunningProcessItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MiuixText(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                text = process.name,
                style = MiuixTheme.textStyles.title4,
                maxLines = 1,
                softWrap = false,
            )
            MiuixText(
                text = processDetailText(process),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MiuixText(
            modifier = Modifier.padding(start = 12.dp),
            text = stringResource(
                if (process.frozen) R.string.running_process_state_frozen
                else R.string.running_process_state_running
            ),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = if (process.frozen) FrozenAccent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun MaterialRunningProcessDialog(
    app: AppItem,
    onDismissRequest: () -> Unit,
) {
    val state = rememberRunningProcesses(app)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            androidx.compose.material3.Text(
                text = app.appName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            val snapshot = state.snapshot
            val processes = snapshot?.processes.orEmpty()

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.running_process_uid, snapshot?.uid ?: -1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Text(
                        text = stringResource(
                            R.string.running_process_summary,
                            processes.size,
                            processes.count { it.frozen },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    when {
                        !state.loaded -> MaterialLoadingIndicator(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )

                        processes.isEmpty() -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Text(
                                text = stringResource(
                                    if (state.failed) R.string.running_process_load_failed
                                    else R.string.running_process_empty
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            processes.forEachIndexed { index, process ->
                                key(process.pid) {
                                    if (index > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    MaterialProcessRow(process)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                androidx.compose.material3.Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun MaterialProcessRow(process: RunningProcessItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (process.frozen) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.outline
                ),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            androidx.compose.material3.Text(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                text = process.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            androidx.compose.material3.Text(
                text = processDetailText(process),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.material3.Text(
            modifier = Modifier.padding(start = 12.dp),
            text = stringResource(
                if (process.frozen) R.string.running_process_state_frozen
                else R.string.running_process_state_running
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (process.frozen) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun processDetailText(process: RunningProcessItem): String = stringResource(
    R.string.running_process_detail,
    process.pid,
    formatRssKb(process.rssKb),
    String.format(Locale.ROOT, "%.2f%%", process.cpu),
)

private fun formatRssKb(rssKb: Long): String {
    val bigDecimal = BigDecimal(rssKb)
    if (rssKb < 1000) return "${rssKb}KB"
    if (rssKb < 1024000) return bigDecimal.divide(BigDecimal(1024), 0, RoundingMode.HALF_UP).toString() + "MB"
    return bigDecimal.divide(BigDecimal(1048576), 2, RoundingMode.HALF_UP).toString() + "GB"
}
