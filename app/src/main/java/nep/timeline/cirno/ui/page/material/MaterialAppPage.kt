package nep.timeline.cirno.ui.page.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.checkers.AppConfigs
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.ui.page.rememberAppListScreenState
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.viewModel.AppListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialAppPage(
    viewModel: AppListViewModel,
    padding: PaddingValues,
) {
    val screenState = rememberAppListScreenState(viewModel)
    val apps = screenState.filteredApps
    val searchValue = screenState.searchValue
    val type = screenState.type
    val updatedApps = screenState.updatedApps
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isActive = remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
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

    val sortedApps = remember(apps, sortAscending) {
        sortConfiguredApps(apps)
    }

    MaterialPageScaffold(
        title = stringResource(R.string.app_list),
        padding = padding,
        actions = {
            MaterialAppToolbarActions(
                onSearch = { searchExpanded = !searchExpanded },
                onSort = { sortAscending = !sortAscending },
                onFilter = { filterExpanded = true },
                filterMenu = {
                    MaterialAppFilterMenu(
                        expanded = filterExpanded,
                        selectedType = type,
                        onDismiss = { filterExpanded = false },
                        onSelect = { selectedType ->
                            viewModel.updateByQuery(type = selectedType)
                            filterExpanded = false
                        },
                    )
                },
            )
        },
        header = {
            AnimatedVisibility(visible = searchExpanded) {
                OutlinedTextField(
                    value = searchValue,
                    onValueChange = { viewModel.updateByQuery(it, type) },
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
                    shape = MaterialTheme.shapes.small,
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
        } else {
            items(
                items = sortedApps,
                key = { it.packageName + "#" + it.userId },
            ) { app ->
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    MaterialAppListItem(app = app)
                }
            }
        }
    }
}

@Composable
private fun MaterialAppToolbarActions(
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    filterMenu: @Composable () -> Unit,
) {
    IconButton(onClick = onSearch) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.search),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(modifier = Modifier.width(16.dp))
    IconButton(onClick = onSort) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = stringResource(R.string.sort),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(modifier = Modifier.width(16.dp))
    Box {
        IconButton(onClick = onFilter) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = stringResource(R.string.filter),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        filterMenu()
    }
}

@Composable
private fun MaterialAppFilterMenu(
    expanded: Boolean,
    selectedType: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val items = stringArrayResource(R.array.dropdownOptions)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        items.forEachIndexed { index, title ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (index == selectedType) FontWeight.Medium else FontWeight.Normal,
                    )
                },
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun MaterialAppListItem(app: AppItem) {
    Card(
        onClick = { AppContext.enterAppPage(app) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = app.appIcon),
                contentDescription = app.appName,
                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
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
                    text = app.packageName + if (app.userId != 0) "#" + app.userId else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MaterialAppBadge(app = app)
        }
    }
}

@Composable
private fun MaterialAppBadge(app: AppItem) {
    val label = when {
        app.black -> stringResource(R.string.black_app)
        app.white -> stringResource(R.string.white_app)
        app.backgroundPlay -> stringResource(R.string.background_play)
        app.locationCheck != 0 -> stringResource(R.string.location_check)
        app.networkCheck -> stringResource(R.string.netreceive_unfreeze)
        app.networkSpeedEnabled -> stringResource(R.string.network_speed_check)
        app.processConfig -> stringResource(R.string.process)
        app.memoryTrimConfig || app.memoryTrimGcConfig -> stringResource(R.string.memory_policy)
        AppConfigs.isValidBackgroundOomAdj(app.backgroundOomAdj) -> stringResource(R.string.background_oom_level_badge, app.backgroundOomAdj)
        app.backgroundLevel == 1 -> stringResource(R.string.direct_app)
        app.backgroundLevel == 2 -> stringResource(R.string.foreground_service)
        app.idle -> stringResource(R.string.battery_opt)
        else -> null
    }

    if (label == null) return

    Spacer(modifier = Modifier.width(12.dp))
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun sortConfiguredApps(apps: List<AppItem>): List<AppItem> = apps
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<AppItem>> { !it.value.hasMaterialBadgeConfig() }
            .thenBy { it.index }
    )
    .map { it.value }

private fun AppItem.hasMaterialBadgeConfig(): Boolean = black || white || backgroundPlay || locationCheck != 0
    || networkCheck || networkSpeedEnabled || processConfig || memoryTrimConfig || memoryTrimGcConfig
    || AppConfigs.isValidBackgroundOomAdj(backgroundOomAdj) || backgroundLevel == 1
    || backgroundLevel == 2 || idle
