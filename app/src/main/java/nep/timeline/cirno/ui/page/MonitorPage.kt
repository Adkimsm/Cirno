@file:OptIn(ExperimentalScrollBarApi::class)
package nep.timeline.cirno.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nep.timeline.cirno.R
import nep.timeline.cirno.entity.AppItem
import nep.timeline.cirno.ui.app.LocalIsWideScreen
import nep.timeline.cirno.ui.dialog.MiuixRunningProcessDialog
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.LocalImageBackdrop
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.cirnoCardBackground
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import nep.timeline.cirno.ui.utils.rememberBlurBackdrop
import nep.timeline.cirno.ui.viewModel.FrozenAppItemCompose
import nep.timeline.cirno.ui.viewModel.MonitorViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private val MonitorListTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val MonitorListBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Composable
fun MonitorPage(
    viewModel: MonitorViewModel,
    padding: PaddingValues,
    scrollEndHaptic: Boolean
) {
    val scrollBehavior = MiuixScrollBehavior()
    val isWideScreen = LocalIsWideScreen.current

    val filteredApps by viewModel.cacheFilterApps.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val searchValue by viewModel.search.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val updatedApps by viewModel.updatedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.filterApps.collectAsStateWithLifecycle()
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive = remember { mutableStateOf(false) }

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    var processDialogTarget by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) return@LaunchedEffect
        viewModel.getMonitorApps(showLoading = false)
        isRefreshing = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START)
                isActive.value = true
            else if (event == Lifecycle.Event.ON_STOP)
                isActive.value = false
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

    val imageBackdrop = LocalImageBackdrop.current
    val backdrop = if (imageBackdrop != null) null else rememberBlurBackdrop()
    val blurActive = imageBackdrop != null || backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop, blurActive, scrollBehavior) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.running_list),
                    isWideScreen = isWideScreen,
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                )
            }
        },
        popupHost = {},
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AnimatedVisibility(
                visible = isLoading.isNotEmpty() && updatedApps,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true },
                    pullToRefreshState = pullToRefreshState,
                    topAppBarScrollBehavior = scrollBehavior,
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = if (isWideScreen) {
                            WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding()
                        } else {
                            0.dp
                        }
                    ),
                ) {
                    val lazyListState = rememberLazyListState()
                    val contentPadding =
                        pageContentPadding(innerPadding, padding, isWideScreen, extraTop = 12.dp)
                    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val listContentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        top = 0.dp,
                        end = contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        bottom = contentPadding.calculateBottomPadding() + navigationBarPadding,
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = contentPadding.calculateTopPadding(),
                                )
                        ) {
                            SearchBar(
                                modifier = Modifier.padding(bottom = 6.dp),
                                inputField = {
                                    InputField(
                                        query = searchValue,
                                        onQueryChange = { viewModel.updateSearch(it) },
                                        onSearch = { keyboardController?.hide() },
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it },
                                        label = stringResource(R.string.search),
                                        leadingIcon = {
                                            Icon(
                                                modifier = Modifier
                                                    .padding(start = 12.dp, end = 8.dp)
                                                    .size(20.dp)
                                                    .alpha(0.4f),
                                                imageVector = MiuixIcons.Basic.Search,
                                                tint = colorScheme.onSurfaceContainer,
                                                contentDescription = "Search"
                                            )
                                        }
                                    )
                                },
                                outsideEndAction = {
                                    Text(
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                            ) {
                                                expanded = false
                                                viewModel.updateSearch("")
                                            },
                                        text = stringResource(R.string.cancel),
                                        style = TextStyle(
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colorScheme.primary,
                                    )
                                },
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                            }

                            SmallTitle(text = stringResource(R.string.frozen_info))
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.pageScrollModifiers(
                                    scrollEndHaptic,
                                    true,
                                    scrollBehavior,
                                ),
                                contentPadding = listContentPadding,
                            ) {
                                val appItems = filteredApps
                                val appCount = appItems.size
                                itemsIndexed(
                                    items = appItems,
                                    key = { _, item -> item.packageName + "#" + item.userId }
                                ) { i, item ->
                                    if (appCount == 1) {
                                        CirnoCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                                            FrozenAppItemCompose(
                                                app = item,
                                                onViewProcesses = { processDialogTarget = item },
                                            )
                                        }
                                    } else {
                                        val isFirst = i == 0
                                        val isLast = i == appCount - 1
                                        val shape = when {
                                            isFirst -> MonitorListTopShape
                                            isLast -> MonitorListBottomShape
                                            else -> RectangleShape
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
                                                .clip(shape)
                                                .cirnoCardBackground(shape, colorScheme.surfaceContainer),
                                        ) {
                                            FrozenAppItemCompose(
                                                app = item,
                                                onViewProcesses = { processDialogTarget = item },
                                            )
                                        }
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(12.dp)) }
                            }

                            VerticalScrollBar(
                                adapter = rememberScrollBarAdapter(lazyListState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                trackPadding = listContentPadding,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !updatedApps,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        InfiniteProgressIndicator(
                            modifier = Modifier
                                .align(alignment = Alignment.CenterVertically),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hasLoadedOnce && updatedApps && isLoading.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.monitor_empty),
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            processDialogTarget?.let { target ->
                MiuixRunningProcessDialog(
                    app = target,
                    onDismissFinished = { processDialogTarget = null },
                )
            }
        }
    }
}
