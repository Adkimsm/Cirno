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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nep.timeline.cirno.MainActivity.AppListViewModelSingleton.appListViewModel
import nep.timeline.cirno.R

import nep.timeline.cirno.ui.app.LocalIsWideScreen
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.LocalImageBackdrop
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.cirnoCardBackground
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import nep.timeline.cirno.ui.utils.rememberBlurBackdrop
import nep.timeline.cirno.ui.viewModel.AppItemCompose
import nep.timeline.cirno.ui.viewModel.AppListViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
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
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private val AppListTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val AppListBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Composable
fun TopBarActions() {
    val showTopPopup = remember { mutableStateOf(false) }
    val topPopupHoldDown = remember { mutableStateOf(false) }
    val type by appListViewModel.type.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    IconButton(
        onClick = {
            showTopPopup.value = true
            topPopupHoldDown.value = true
        },
        holdDownState = topPopupHoldDown.value,
    ) {
        Icon(
            imageVector = MiuixIcons.More,
            contentDescription = "WindowListPopup",
            tint = colorScheme.onBackground,
        )
    }
    WindowListPopup(
        show = showTopPopup.value,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = {
            showTopPopup.value = false
        },
        onDismissFinished = {
            topPopupHoldDown.value = false
        },
        content = {
            val state = LocalDismissState.current
            val items = stringArrayResource(R.array.dropdownOptions)
            ListPopupColumn {
                items.forEachIndexed { index, string ->
                    key(index) {
                        DropdownImpl(
                            text = string,
                            optionSize = items.size,
                            isSelected = type == index,
                            onSelectedIndexChange = { selectedIdx ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                appListViewModel.updateByQuery(type = selectedIdx)
                                state?.invoke()
                            },
                            index = index
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun AppPage(
    viewModel: AppListViewModel,
    padding: PaddingValues,
    scrollEndHaptic: Boolean
) {
    val scrollBehavior = MiuixScrollBehavior()
    val isWideScreen = LocalIsWideScreen.current
    val screenState = rememberAppListScreenState(viewModel)
    val filteredApps = screenState.filteredApps
    var expanded by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchValue = screenState.searchValue
    val type = screenState.type
    val updatedApps = screenState.updatedApps
    val isLoading = screenState.loadingApps

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            return@LaunchedEffect
        }
        viewModel.update().join()
        isRefreshing = false
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
                    title = stringResource(R.string.app_list),
                    isWideScreen = isWideScreen,
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    actions = {
                        TopBarActions()
                    }
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
                                        onQueryChange = {
                                            viewModel.updateByQuery(it, type)
                                        },
                                        onSearch = {
                                            keyboardController?.hide()
                                        },
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
                                                viewModel.updateByQuery("", type)
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

                            SmallTitle(
                                text = when (type) {
                                    2 -> stringResource(R.string.frozen_info)
                                    else -> stringResource(R.string.app_info)
                                }
                            )
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
                                            AppItemCompose(item)
                                        }
                                    } else {
                                        val isFirst = i == 0
                                        val isLast = i == appCount - 1
                                        val shape = when {
                                            isFirst -> AppListTopShape
                                            isLast -> AppListBottomShape
                                            else -> RectangleShape
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
                                                .clip(shape)
                                                .cirnoCardBackground(shape, colorScheme.surfaceContainer),
                                        ) {
                                            AppItemCompose(item)
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
        }
    }
}
