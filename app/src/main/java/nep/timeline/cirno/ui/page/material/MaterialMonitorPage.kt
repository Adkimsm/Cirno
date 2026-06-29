package nep.timeline.cirno.ui.page.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.policy.FreezeExemption
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.provide.ApplicationBinder
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.viewModel.MonitorViewModel
import nep.timeline.cirno.utils.PackageUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialMonitorPage(
    viewModel: MonitorViewModel,
    padding: PaddingValues,
) {
    val apps by viewModel.cacheFilterApps.collectAsStateWithLifecycle()
    val searchValue by viewModel.search.collectAsStateWithLifecycle()
    val updatedApps by viewModel.updatedApps.collectAsStateWithLifecycle()
    val filterApps by viewModel.filterApps.collectAsStateWithLifecycle()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isActive = remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isActive.value = true
            } else if (event == Lifecycle.Event.ON_STOP) {
                isActive.value = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isActive.value) {
        viewModel.getMonitorApps()
        while (isActive.value) {
            delay(1500)
            viewModel.getMonitorApps(showLoading = false)
        }
    }

    val sortedApps = remember(apps, sortAscending) {
        sortMonitorApps(apps, sortAscending)
    }

    MaterialPageScaffold(
        title = stringResource(R.string.running_list),
        padding = padding,
        actions = {
            IconButton(onClick = { searchExpanded = !searchExpanded }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = { sortAscending = !sortAscending }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        header = {
            AnimatedVisibility(visible = searchExpanded) {
                OutlinedTextField(
                    value = searchValue,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboardController?.hide() },
                    ),
                )
            }
        },
    ) {
        if (!updatedApps) {
            item(key = "loading") {
                MaterialLoadingIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                )
            }
        } else if (hasLoadedOnce && filterApps.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.monitor_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = sortedApps,
                key = { it.packageName + "#" + it.userId },
            ) { app ->
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    MaterialMonitorListItem(app = app)
                }
            }
        }
    }
}

@Composable
private fun MaterialMonitorListItem(app: AppItem) {
    val scope = rememberCoroutineScope()
    val systemNotFlaggedText = stringResource(R.string.system_not_flagged_but_frozen)
    val networkSpeedFailedText = stringResource(R.string.network_speed_failed)
    val compactionToastText = stringResource(R.string.compaction_toast)
    val frozenText = app.frozenType + " " + stringResource(R.string.freezing)
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

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
        if (app.frozenType != null && app.frozenType == "SYSTEM_NOT_FLAGGED_BUT_FROZEN") {
            append(" ")
            withStyle(SpanStyle(color = Color(0xFFD13636))) {
                append(stringResource(R.string.frozen_wrong))
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {
                when {
                    app.networkSpeedEnabled -> {
                        scope.launch {
                            val speedText = withContext(Dispatchers.IO) { getNetworkSpeedText(app) }
                            WindowUtils.showToast(speedText ?: networkSpeedFailedText)
                        }
                    }
                    app.frozenType == "SYSTEM_NOT_FLAGGED_BUT_FROZEN" -> WindowUtils.showToast(systemNotFlaggedText)
                    !app.isFrozen -> WindowUtils.showToast(FreezeExemption.fromReason(app.notFrozenReason).displayText)
                    app.compactedProcessCount > 0 -> {
                        val freed = getMemSize(app.compactedMemoryFreedKb)
                        WindowUtils.showToast(compactionToastText.format(app.compactedProcessCount, freed))
                    }
                    else -> WindowUtils.showToast(frozenText)
                }
            },
            onLongClick = { AppContext.enterAppPage(app) },
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(64.dp).padding(end = 16.dp)
            ) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.appIcon),
                    contentDescription = app.appName,
                    modifier = Modifier.size(64.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = String.format(Locale.ROOT, "%.2f%%", app.cpuUsage),
                    fontSize = 14.sp,
                    color = subtitleColor,
                )
                Text(
                    text = getMemSize(app.rss),
                    fontSize = 14.sp,
                    color = subtitleColor,
                )
            }
        }
    }
}

private fun getMemSize(mem: Long): String {
    val bigDecimal = BigDecimal(mem)
    if (mem < 1000) return mem.toString() + "KB"
    if (mem < 1024000) return bigDecimal.divide(BigDecimal(1024), 0, RoundingMode.HALF_UP)
        .toString() + "MB"
    return bigDecimal.divide(BigDecimal(1048576), 2, RoundingMode.HALF_UP).toString() + "GB"
}

private fun sortMonitorApps(apps: List<AppItem>, ascending: Boolean): List<AppItem> = apps.sortedWith(
    compareBy<AppItem> { PackageUtils.isSystemUIChecker(AppContext.context, it.packageInfo) }
        .thenBy { !it.isFrozen }
        .thenName(ascending)
)

private fun Comparator<AppItem>.thenName(ascending: Boolean): Comparator<AppItem> {
    val nameComparator = compareBy<AppItem> { it.appName.orEmpty().lowercase(Locale.ROOT) }
        .thenBy { it.packageName.orEmpty() }
        .thenBy { it.userId }
    return if (ascending) then(nameComparator) else then(nameComparator.reversed())
}

private fun getNetworkSpeedText(app: AppItem): String? {
    val binder = ApplicationBinder.getInstance() ?: return null
    return try {
        val json = binder.getNetworkSpeed(app.packageName, app.userId)
        val rx = json.substringAfter("\"rx\":").substringBefore(",").trim().toLongOrNull() ?: 0L
        val tx = json.substringAfter("\"tx\":").substringBefore("}").trim().toLongOrNull() ?: 0L
        "↑${formatSpeed(tx)} ↓${formatSpeed(rx)}"
    } catch (_: Throwable) {
        null
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "${bytesPerSec}B/s"
    if (bytesPerSec < 1024 * 1024) return "${BigDecimal(bytesPerSec).divide(BigDecimal(1024), 1, RoundingMode.HALF_UP)}KB/s"
    return "${BigDecimal(bytesPerSec).divide(BigDecimal(1048576), 2, RoundingMode.HALF_UP)}MB/s"
}
