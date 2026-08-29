@file:OptIn(ExperimentalScrollBarApi::class)
package nep.timeline.cirno.ui.page

import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nep.timeline.cirno.BuildConfig
import nep.timeline.cirno.GlobalVars
import nep.timeline.cirno.MainActivity.AppListViewModelSingleton.appListViewModel
import nep.timeline.cirno.R
import nep.timeline.cirno.configs.settings.ApplicationSettings
import nep.timeline.cirno.ui.app.LocalIsWideScreen
import nep.timeline.cirno.ui.app.LocalNavigator
import nep.timeline.cirno.ui.app.isInDarkTheme
import nep.timeline.cirno.ui.navigation3.Route
import nep.timeline.cirno.ui.page.rememberInfoScreenState
import nep.timeline.cirno.ui.utils.AdaptiveTopAppBar
import nep.timeline.cirno.ui.utils.AppContext
import nep.timeline.cirno.ui.utils.BlurredBar
import nep.timeline.cirno.ui.utils.LocalImageBackdrop
import nep.timeline.cirno.ui.dialog.UpdateDialog
import nep.timeline.cirno.ui.utils.CirnoCard
import nep.timeline.cirno.ui.utils.AddOnStatusRepository
import nep.timeline.cirno.ui.utils.RootConfigRepository
import nep.timeline.cirno.ui.utils.UpdateResult
import nep.timeline.cirno.ui.utils.WindowUtils
import nep.timeline.cirno.ui.utils.XposedServiceStatus
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import nep.timeline.cirno.ui.utils.rememberBlurBackdrop
import nep.timeline.cirno.utils.VersionUtils
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.text.SimpleDateFormat
import java.util.Date

var clickNum = 0
var lastClickTime = 0L
val fool = SimpleDateFormat("MMdd").format(Date()).equals("0401")

private data class HookStatusState(
    val statusBinderAvailable: Boolean = false,
    val hasError: Boolean = false,
    val freezerAvailable: Boolean = true,
    val hookVersion: String? = null,
)

@Composable
fun InfoPage(
    callback: (Int) -> Unit,
    padding: PaddingValues,
    scrollEndHaptic: Boolean
) {
    val isWideScreen = LocalIsWideScreen.current
    val imageBackdrop = LocalImageBackdrop.current
    val backdrop = if (imageBackdrop != null) null else rememberBlurBackdrop()
    val blurActive = imageBackdrop != null || backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val infoState = rememberInfoScreenState(LocalContext.current)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop, blurActive, topAppBarScrollBehavior) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.app_name),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor
                )
            }
        },
    ) { innerPadding ->
        InfoContent(
            padding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            backdrop = backdrop,
            scrollEndHaptic = scrollEndHaptic,
            callback = callback,
            infoState = infoState,
        )
    }
}

@Composable
private fun InfoContent(
    padding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    scrollEndHaptic: Boolean,
    callback: (Int) -> Unit,
    infoState: InfoScreenStateHolder,
) {
    val isWideScreen = LocalIsWideScreen.current
    val lazyListState = rememberLazyListState()
    val contentPadding = pageContentPadding(padding, padding, isWideScreen)
    val binderState = infoState.binderState
    var applicationSettings by remember { mutableStateOf(GlobalVars.applicationSettings) }
    val xposedServiceStatus = XposedServiceStatus.state.value

    LaunchedEffect(Unit) {
        if (applicationSettings == null) {
            withContext(Dispatchers.IO) {
                RootConfigRepository.ensureLoadedIntoMemory()
            }
        }
        applicationSettings = GlobalVars.applicationSettings
    }

    infoState.updateResult?.let { result ->
        if (infoState.showUpdateDialog) {
            UpdateDialog(
                show = true,
                updateResult = result,
                onDismissRequest = { infoState.dismissUpdateDialog() }
            )
        }
    }

    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(
                scrollEndHaptic,
                true,
                topAppBarScrollBehavior,
            ),
            contentPadding = contentPadding,
        ) {
            item {
                val active = xposedServiceStatus.active
                val statusBinderAvailable = binderState.statusBinderAvailable
                val connecting = binderState.connecting
                val hasError = binderState.hasError
                val hookVersion = binderState.hookVersion
                // 优先用 hook 指纹判断是否需要重启：纯 UI 更新指纹不变，不显示"请重启"。
                // hook 侧未上报指纹（旧版 hook）时兜底用 hookVersion 比较。
                val hookFingerprint = binderState.hookFingerprint
                val versionMismatch = active && !connecting && statusBinderAvailable &&
                    if (hookFingerprint != null) {
                        hookFingerprint != BuildConfig.HOOK_FINGERPRINT
                    } else {
                        hookVersion != null && hookVersion != BuildConfig.VERSION_NAME
                    }
                val addOnMissing = !AddOnStatusRepository.isAddOnEnabled()
                val androidScopeLabel = stringResource(R.string.scope_android)
                val systemUiScopeLabel = stringResource(R.string.scope_systemui)
                val missingScopeLabels = xposedServiceStatus.missingRequiredScopes.joinToString(separator = ", ") { scope ->
                    when (scope) {
                        "system" -> androidScopeLabel
                        else -> systemUiScopeLabel
                    }
                }
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (versionMismatch) {
                        WarningCard(stringResource(R.string.module_version_mismatch))
                    } else {
                        if (fool)
                            WarningCard(stringResource(R.string.fools_day))
                        if (!active)
                            WarningCard(stringResource(R.string.not_active))
                        if (active && statusBinderAvailable && xposedServiceStatus.missingRequiredScopes.isNotEmpty())
                            WarningCard(stringResource(R.string.scope_not_running, missingScopeLabels))
                        if (!connecting && hasError)
                            WarningCard(stringResource(R.string.internal_error))
                        if (!connecting && active && statusBinderAvailable && addOnMissing)
                            WarningCard(stringResource(R.string.add_on_required_warning))
                        if (!connecting && active && statusBinderAvailable && !binderState.freezerAvailable)
                            WarningCard(stringResource(R.string.freezer_v2_unavailable))
                    }
                    StatusCard(
                        active = active,
                        working = active && !connecting && !hasError && !addOnMissing,
                        connecting = connecting,
                        version = hookVersion
                            ?: stringResource(R.string.not_running),
                        applicationSettings = applicationSettings,
                        onClickStatus = {

                        },
                        onClickWhitelist = {
                            appListViewModel.updateByQuery(type = 0)
                            callback(1)
                        },
                        onClickBlacklist = {
                            appListViewModel.updateByQuery(type = 1)
                            callback(1)
                        }
                    )
                    InfoCard(
                        working = active && !connecting,
                        hookType = binderState.hookType,
                        xposedServiceStatus = xposedServiceStatus
                    )
                    UpdateCard(
                        isChecking = infoState.isCheckingUpdate,
                        onClick = {
                            infoState.startUpdateCheck()
                        }
                    )
                    if (active && xposedServiceStatus.supportsHotReload) {
                        HotReloadCard()
                    }
                    LearnMoreCard()
                    LogCard()
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    active: Boolean,
    working: Boolean,
    connecting: Boolean = false,
    version: String,
    applicationSettings: ApplicationSettings?,
    onClickStatus: () -> Unit = {},
    onClickWhitelist: () -> Unit = {},
    onClickBlacklist: () -> Unit = {},
    alpha: Boolean = false,
    colors: CardColors = CardDefaults.defaultColors(),
) {
    val isWideScreen = LocalIsWideScreen.current
    val cardHeight = if (isWideScreen) 300.dp else 220.dp
    val statusIconSize = if (isWideScreen) 170.dp else 128.dp
    val statusIconOffsetX = if (isWideScreen) 38.dp else 20.dp
    val statusIconOffsetY = if (isWideScreen) 45.dp else 28.dp

    Column(
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CirnoCard(
                modifier = modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.defaultColors(
                    color = when {
                        connecting -> if (alpha) colorScheme.surfaceContainer.copy(0.35f) else colorScheme.surfaceContainer
                        isDynamicColor -> if (alpha) colorScheme.secondaryContainer.copy(0.35f) else colorScheme.secondaryContainer
                        isInDarkTheme() -> if (working && !fool) (if (alpha) Color(0xFF1A3825).copy(0.35f) else Color(0xFF1A3825)) else (if (alpha) Color(0xFF381A1A).copy(0.35f) else Color(0xFF381A1A))
                        else -> if (working && !fool) (if (alpha) Color(0xFFDFFAE4).copy(0.35f) else Color(0xFFDFFAE4)) else (if (alpha) Color(0xFFFADFDF).copy(0.35f) else Color(0xFFFADFDF))
                    }
                ),
                onClick = {
                    onClickStatus()
                },
                showIndication = true,
                pressFeedbackType = if (alpha) PressFeedbackType.None else PressFeedbackType.Tilt
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(statusIconOffsetX, statusIconOffsetY),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        if (connecting) {
                            InfiniteProgressIndicator(modifier = Modifier.size(48.dp))
                        } else {
                            Icon(
                                modifier = Modifier.size(statusIconSize),
                                imageVector = if (working) if (fool) Icons.Rounded.PauseCircleOutline else Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                                tint = if (isDynamicColor) {
                                    colorScheme.primary.copy(0.8f)
                                } else {
                                    if (working && !fool) Color(0xFF36D167) else Color(0xFFD13636)
                                },
                                contentDescription = null
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(all = 16.dp)
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = when {
                                connecting -> stringResource(R.string.connecting)
                                working -> if (fool) stringResource(R.string.crying) else stringResource(R.string.working)
                                else -> stringResource(R.string.error)
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.version) + ": " + version,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CirnoCard(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    insideMargin = PaddingValues(16.dp),
                    onClick = { onClickWhitelist() },
                    showIndication = true,
                    pressFeedbackType = if (alpha) PressFeedbackType.None else PressFeedbackType.Tilt,
                    colors = colors
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.white_app),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = if (!active || applicationSettings == null || fool) "N/A" else applicationSettings.whiteApps.size.toString(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                CirnoCard(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    insideMargin = PaddingValues(16.dp),
                    onClick = { onClickBlacklist() },
                    showIndication = true,
                    pressFeedbackType = if (alpha) PressFeedbackType.None else PressFeedbackType.Tilt,
                    colors = colors
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.black_app),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = if (!active || applicationSettings == null || fool) "N/A" else applicationSettings.blackApps.size.toString(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    alpha: Boolean = false,
    color: Color? = null,
    onClick: (() -> Unit)? = null
) {
    CirnoCard(
        onClick = {
            onClick?.invoke()
        },
        colors = CardDefaults.defaultColors(
            color = color ?: when {
                isDynamicColor -> if (alpha) colorScheme.errorContainer.copy(0.35f) else colorScheme.errorContainer
                isInDarkTheme() -> if (alpha) Color(0XFF310808).copy(0.35f) else Color(0XFF310808)
                else -> if (alpha) Color(0XFF310808).copy(0.35f) else Color(0xFFF8E2E2)
            }
        ),
        modifier = modifier,
        showIndication = onClick != null,
        pressFeedbackType = if (alpha) PressFeedbackType.None else PressFeedbackType.Tilt
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = message,
                color = if (isDynamicColor) colorScheme.onErrorContainer else Color(0xFFF72727),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun HotReloadCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    val unsupportedText = stringResource(R.string.hot_reload_unsupported)
    val noTargetsText = stringResource(R.string.hot_reload_no_targets)
    val failedText = stringResource(R.string.hot_reload_failed)
    val submittedText = stringResource(R.string.hot_reload_submitted)
    fun startHotReload() {
        XposedServiceStatus.hotReloadRunningTargets { outcome ->
            resultMessage = when {
                !outcome.supported -> unsupportedText
                outcome.error != null -> failedText.format(outcome.error)
                outcome.targetCount == 0 -> noTargetsText
                else -> submittedText.format(outcome.results.joinToString(separator = "\n"))
            }
            showResultDialog = true
        }
    }

    OverlayDialog(
        title = stringResource(R.string.hot_reload_confirm_title),
        summary = stringResource(R.string.hot_reload_confirm_message),
        show = showConfirmDialog,
        onDismissRequest = { showConfirmDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.cancel),
                onClick = { showConfirmDialog = false },
            )
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.ok),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    showConfirmDialog = false
                    startHotReload()
                },
            )
        }
    }

    OverlayDialog(
        title = stringResource(R.string.hot_reload_result_title),
        show = showResultDialog,
        onDismissRequest = { showResultDialog = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                text = resultMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .verticalScroll(rememberScrollState()),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.ok),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { showResultDialog = false },
            )
        }
    }

    CirnoCard(
        modifier = modifier
            .fillMaxWidth(),
        colors = colors,
    ) {
        ArrowPreference(
            title = stringResource(R.string.hot_reload),
            summary = stringResource(R.string.hot_reload_summary),
            onClick = { showConfirmDialog = true }
        )
    }
}

@Composable
private fun LearnMoreCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
) {
    val navigator = LocalNavigator.current
    CirnoCard(
        modifier = modifier
            .fillMaxWidth(),
        colors = colors
    ) {
        ArrowPreference(
            title = stringResource(R.string.home_about_freezer),
            onClick = {
                navigator.push(Route.About)
            }
        )
    }
}

@Composable
private fun LogCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
) {
    val navigator = LocalNavigator.current
    CirnoCard(
        modifier = modifier
            .fillMaxWidth(),
        colors = colors
    ) {
        ArrowPreference(
            title = stringResource(R.string.home_logs),
            summary = stringResource(R.string.home_logs_desc),
            onClick = {
                navigator.push(Route.Log)
            }
        )
    }
}

@Composable
private fun UpdateCard(
    isChecking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
) {
    CirnoCard(
        modifier = modifier
            .fillMaxWidth(),
        colors = colors,
        onClick = onClick,
        showIndication = !isChecking,
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        ArrowPreference(
            title = if (isChecking) stringResource(R.string.update_checking) else stringResource(R.string.check_update),
            summary = stringResource(R.string.update_check_summary),
            onClick = onClick
        )
    }
}

@Composable
private fun InfoCard(
    working: Boolean,
    hookType: String?,
    xposedServiceStatus: nep.timeline.cirno.ui.utils.ModuleStatus,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
) {
    @Composable
    fun InfoText(
        title: String,
        content: String,
        bottomPadding: Dp = 24.dp,
        modifier: Modifier = Modifier
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            modifier = modifier
        )
        Text(
            text = content,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = modifier.padding(top = 2.dp, bottom = bottomPadding)
        )
    }
    CirnoCard(colors = colors, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(
                title = stringResource(R.string.manager_version),
                content = "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "-" + BuildConfig.BUILD_TIME + ")",
                modifier = Modifier.pointerInput(UInt) {
                    detectTapGestures(
                        onTap = {
                            val curTime = System.currentTimeMillis()
                            if (((curTime - lastClickTime) / 1000) > 2)
                                clickNum = 0
                            clickNum++
                            lastClickTime = curTime
                            if (clickNum > 5) {
                                clickNum = 0
                                AppContext.playCry()
                                WindowUtils.showToast("Cirno")
                            }
                        }
                    )
                }
            )
            InfoText(
                title = stringResource(R.string.hook_type),
                content = if (working && hookType != null) hookType else stringResource(R.string.unknown)
            )
            InfoText(
                title = stringResource(R.string.android_version),
                content = if (Build.VERSION.PREVIEW_SDK_INT != 0) (Build.VERSION.CODENAME + " Preview (API " + Build.VERSION.PREVIEW_SDK_INT + "/" + Build.VERSION.SDK_INT + ")") else (VersionUtils.getAndroidVersion() + " (API " + Build.VERSION.SDK_INT + ")")
            )
            InfoText(
                title = stringResource(R.string.xposed_version),
                content = if (xposedServiceStatus.frameworkVersion.isNotEmpty()) "${xposedServiceStatus.frameworkName} ${xposedServiceStatus.frameworkVersion}, API ${xposedServiceStatus.apiVersion}" else stringResource(R.string.unknown)
            )
            InfoText(
                title = stringResource(R.string.system_fingerprint),
                content = Build.FINGERPRINT,
                bottomPadding = 0.dp
            )
        }
    }
}
