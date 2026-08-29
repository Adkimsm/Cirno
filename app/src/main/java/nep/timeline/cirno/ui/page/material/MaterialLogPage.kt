package nep.timeline.cirno.ui.page.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nep.timeline.cirno.MainActivity.LogViewModelSingleton.logViewModel
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalNavigator
import nep.timeline.cirno.ui.page.matchesLogLevel
import nep.timeline.cirno.ui.utils.LogEmptyReason
import nep.timeline.cirno.ui.page.rememberLogScreenState
import nep.timeline.cirno.ui.viewModel.LogDisplayLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialLogPage(
    padding: PaddingValues,
) {
    val navigator = LocalNavigator.current
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val screenState = rememberLogScreenState(logViewModel)
    val uiState = screenState.uiState
    val filteredLines = screenState.filteredLines

    MaterialPageScaffold(
        title = stringResource(R.string.logs_title),
        padding = padding,
        lazyListState = lazyListState,
        selectionEnabled = true,
        navigationIcon = {
            IconButton(onClick = { navigator.pop() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = { logViewModel.setSearchExpanded(!uiState.searchExpanded) }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            MaterialLogMenu(
                selectedLevel = uiState.selectedLevel,
                onLevelSelected = logViewModel::selectLevel,
                onScrollTop = {
                    scope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                onScrollBottom = {
                    scope.launch {
                        val bottomIndex = if (filteredLines.isEmpty()) 0 else lazyListState.layoutInfo.totalItemsCount.minus(1).coerceAtLeast(0)
                        lazyListState.animateScrollToItem(bottomIndex)
                    }
                },
            )
        },
        header = {
            AnimatedVisibility(visible = uiState.searchExpanded) {
                MaterialLogSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = logViewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
        },
    ) {
        if (uiState.isLoading && !uiState.isInitialLoadDone) {
            item(key = "loading") {
                MaterialLoadingIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                )
            }
        } else if (uiState.isInitialLoadDone && uiState.allLines.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = materialLogEmptyText(uiState.emptyReason, uiState.emptyReasonDetail),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            if (uiState.isTruncated) {
                item(key = "logTruncated") {
                    Text(
                        text = stringResource(R.string.logs_truncated),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(filteredLines) { line ->
                MaterialLogLine(line = line)
            }
        }
    }
}

@Composable
private fun materialLogEmptyText(reason: LogEmptyReason?, detail: String?): String {
    val fallback = stringResource(R.string.logs_empty)
    if (reason == null || detail.isNullOrBlank()) return fallback
    return when (reason) {
        LogEmptyReason.ReadFailed -> stringResource(R.string.logs_read_failed, detail)
        LogEmptyReason.FileLogFailed -> stringResource(R.string.logs_file_log_failed, detail)
    }
}

@Composable
private fun MaterialLogMenu(
    selectedLevel: LogDisplayLevel,
    onLevelSelected: (LogDisplayLevel) -> Unit,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        CirnoDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            val levelItems = listOf(
                stringResource(R.string.log_all) to LogDisplayLevel.All,
                stringResource(R.string.log_debug) to LogDisplayLevel.Debug,
                stringResource(R.string.log_info) to LogDisplayLevel.Info,
                stringResource(R.string.log_warning) to LogDisplayLevel.Warning,
                stringResource(R.string.log_error) to LogDisplayLevel.Error,
            )
            levelItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(if (selectedLevel == item.second) "${item.first} *" else item.first) },
                    onClick = {
                        expanded = false
                        onLevelSelected(item.second)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scroll_to_top)) },
                onClick = {
                    expanded = false
                    onScrollTop()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scroll_to_bottom)) },
                onClick = {
                    expanded = false
                    onScrollBottom()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_log)) },
                onClick = {
                    expanded = false
                    logViewModel.exportLog(context)
                },
            )
        }
    }
}

@Composable
private fun MaterialLogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
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
        shape = MaterialTheme.shapes.small,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { keyboardController?.hide() },
        ),
    )
}

@Composable
private fun MaterialLogLine(line: String) {
    Text(
        text = line,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = when (logLevelOf(line)) {
            LogDisplayLevel.Error -> MaterialTheme.colorScheme.error
            LogDisplayLevel.Warning -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
    )
}

private fun logLevelOf(line: String): LogDisplayLevel? {
    val level = line.substringAfter(" ").substringAfter(" ").substringBefore(" ->")
    return when (level) {
        "错误", "ERROR", "E" -> LogDisplayLevel.Error
        "警告", "WARN", "W" -> LogDisplayLevel.Warning
        "信息", "INFO", "I" -> LogDisplayLevel.Info
        "调试", "DEBUG", "D" -> LogDisplayLevel.Debug
        else -> null
    }
}
