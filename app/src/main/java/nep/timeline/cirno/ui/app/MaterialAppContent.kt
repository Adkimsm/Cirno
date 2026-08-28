package nep.timeline.cirno.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import nep.timeline.cirno.MainActivity.AppListViewModelSingleton.appListViewModel
import nep.timeline.cirno.MainActivity.MonitorViewModelSingleton.monitorViewModel
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.NavigationShellBackHandler
import nep.timeline.cirno.ui.app.rememberMainPagerBackEnabled
import nep.timeline.cirno.ui.navigation3.Navigator
import nep.timeline.cirno.ui.navigation3.Route
import nep.timeline.cirno.ui.page.material.LocalSnackbarHostState
import nep.timeline.cirno.ui.page.material.MaterialAboutPage
import nep.timeline.cirno.ui.page.material.MaterialAppPage
import nep.timeline.cirno.ui.page.material.MaterialInfoPage
import nep.timeline.cirno.ui.page.material.MaterialLogPage
import nep.timeline.cirno.ui.page.material.MaterialMonitorPage
import nep.timeline.cirno.ui.page.material.MaterialSettingsPage
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.shouldShowSplitPane

private data class MaterialNavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MaterialAppContent(
    active: Boolean,
    padding: PaddingValues,
) {
    val pageCount = if (active) 4 else 2
    val settingsPageIndex = if (active) 3 else 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val mainPagerState = rememberMaterialMainPagerState(pagerState)
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val backStack = remember { mutableStateListOf<NavKey>().apply { add(Route.Main) } }
    val navigator = remember { Navigator(backStack) }
    val isWideScreen = shouldShowSplitPane()
    val navigationItems = remember(active) {
        if (active) {
            listOf(
                MaterialNavItem(AppContext.context.getString(R.string.main), Icons.Outlined.Home),
                MaterialNavItem(AppContext.context.getString(R.string.app_list), Icons.Outlined.Apps),
                MaterialNavItem(AppContext.context.getString(R.string.running_list), Icons.Outlined.Memory),
                MaterialNavItem(AppContext.context.getString(R.string.settings), Icons.Outlined.Settings),
            )
        } else {
            listOf(
                MaterialNavItem(AppContext.context.getString(R.string.main), Icons.Outlined.Home),
                MaterialNavItem(AppContext.context.getString(R.string.settings), Icons.Outlined.Settings),
            )
        }
    }

    NavigationShellBackHandler(
        isBackEnabled = rememberMainPagerBackEnabled(mainPagerState.selectedPage, navigator),
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )

    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalNavigator provides navigator,
        LocalMainPagerState provides rememberMainPagerState(pagerState),
        LocalIsWideScreen provides isWideScreen,
    ) {
        val entryProvider = rememberSharedNavigationEntries(
            backStack = backStack,
            mainContent = {
                MaterialHome(
                    padding = padding,
                    navigationItems = navigationItems,
                    mainPagerState = mainPagerState,
                    settingsPageIndex = settingsPageIndex,
                )
            },
            aboutContent = { MaterialAboutPage(padding = padding) },
            logContent = { MaterialLogPage(padding = padding) },
        )

        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider,
        )

        val appState = LocalAppState.current
        val transitionEffects = rememberSharedTransitionEffects(appState)

        Box(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                NavDisplay(
                    entries = entries,
                    onBack = { navigator.pop() },
                    transitionEffects = transitionEffects,
                )
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun MaterialHome(
    padding: PaddingValues,
    navigationItems: List<MaterialNavItem>,
    mainPagerState: MaterialMainPagerState,
    settingsPageIndex: Int,
) {
    val isWideScreen = LocalIsWideScreen.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        bottomBar = {
            if (!isWideScreen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    navigationItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = mainPagerState.selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.outline,
                                unselectedTextColor = MaterialTheme.colorScheme.outline,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        navigationItems.forEachIndexed { index, item ->
                            NavigationRailItem(
                                selected = mainPagerState.selectedPage == index,
                                onClick = { mainPagerState.animateToPage(index) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.outline,
                                    unselectedTextColor = MaterialTheme.colorScheme.outline,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    MaterialAppPager(
                        active = settingsPageIndex == 3,
                        padding = PaddingValues(top = innerPadding.calculateTopPadding()),
                        pagerState = mainPagerState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            MaterialAppPager(
                active = settingsPageIndex == 3,
                padding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + innerPadding.calculateBottomPadding(),
                ),
                pagerState = mainPagerState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MaterialAppPager(
    active: Boolean,
    padding: PaddingValues,
    pagerState: MaterialMainPagerState,
    modifier: Modifier = Modifier,
) {
    val appState = LocalAppState.current
    HorizontalPager(
        state = pagerState.pagerState,
        modifier = modifier,
        userScrollEnabled = appState.enablePageUserScroll,
        verticalAlignment = Alignment.Top,
    ) { page ->
        when (page) {
            0 -> MaterialInfoPage(
                padding = padding,
            )
            1 -> if (active) {
                MaterialAppPage(
                    viewModel = appListViewModel,
                    padding = padding,
                )
            } else {
                MaterialSettingsPage(
                    active = false,
                    padding = padding,
                )
            }
            2 -> if (active) {
                MaterialMonitorPage(
                    viewModel = monitorViewModel,
                    padding = padding,
                )
            } else {
                MaterialSettingsPage(
                    active = true,
                    padding = padding,
                )
            }
            else -> MaterialSettingsPage(
                active = true,
                padding = padding,
            )
        }
    }
}



private class MaterialMainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberMaterialMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MaterialMainPagerState = remember(pagerState, coroutineScope) {
    MaterialMainPagerState(pagerState, coroutineScope)
}
