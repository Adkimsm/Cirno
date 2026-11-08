@file:OptIn(ExperimentalScrollBarApi::class)
package nep.timeline.cirno.ui.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import nep.timeline.cirno.BuildConfig
import nep.timeline.cirno.R
import nep.timeline.cirno.ui.app.LocalAppState
import nep.timeline.cirno.ui.app.LocalIsWideScreen
import nep.timeline.cirno.ui.app.LocalNavigator
import nep.timeline.cirno.ui.app.isInDarkTheme
import nep.timeline.cirno.ui.custom.BackNavigationIcon
import nep.timeline.cirno.ui.custom.blend.ColorBlendToken
import nep.timeline.cirno.ui.custom.blur.BgEffectBackground
import nep.timeline.cirno.ui.utils.pageContentPadding
import nep.timeline.cirno.ui.utils.pageScrollModifiers
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Composable
fun AboutPage(
    padding: PaddingValues,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.about),
                scrollBehavior = topAppBarScrollBehavior,
                color = colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = { navigator.pop() },
                    )
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            padding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
            ),
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            lazyListState = lazyListState,
            scrollProgress = scrollProgress,
            onLogoHeightChanged = { logoHeightPx = it },
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
) {
    val appState = LocalAppState.current
    val isWideScreen = LocalIsWideScreen.current
    val uriHandler = LocalUriHandler.current

    val backdrop = rememberLayerBackdrop()
    var isOs3Effect by remember { mutableStateOf(true) }
    var showTextureSet by remember { mutableStateOf(false) }
    var blurRadius by remember { mutableFloatStateOf(60f) }
    var noiseCoefficient by remember { mutableFloatStateOf(BlurDefaults.NoiseCoefficient) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    val scrollPadding = pageContentPadding(
        padding,
        padding,
        isWideScreen,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )
    val logoPadding = pageContentPadding(
        padding,
        padding,
        isWideScreen,
        extraTop = 40.dp,
        extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
        extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
    )

    val isInDark = isInDarkTheme()
    var blurEnable by remember { mutableStateOf(isRenderEffectSupported()) }
    val dynamicBackground = remember { mutableStateOf(isRuntimeShaderSupported()) }
    val effectBackground = remember { mutableStateOf(isRuntimeShaderSupported()) }

    val surface = colorScheme.surface.copy(alpha = 0.6f)
    val blendConfigs = remember(isInDark) {
        mapOf(
            "Default" to if (isInDark) ColorBlendToken.Overlay_Thin_Light else ColorBlendToken.Pured_Regular_Light,
            "None" to emptyList(),
            "SrcOver" to listOf(BlendColorEntry(surface, BlurBlendMode.SrcOver)),
            "Screen" to listOf(BlendColorEntry(surface, BlurBlendMode.Screen)),
            "Multiply" to listOf(BlendColorEntry(surface, BlurBlendMode.Multiply)),
            "Overlay" to listOf(BlendColorEntry(surface, BlurBlendMode.Overlay)),
            "Soft Light" to listOf(BlendColorEntry(surface, BlurBlendMode.SoftLight)),
            "Linear Light" to listOf(BlendColorEntry(surface, BlurBlendMode.LinearLight)),
            "Linear Light Grey" to listOf(BlendColorEntry(surface, BlurBlendMode.LinearLightWithGreyscale)),
            "Linear Light Lab" to listOf(BlendColorEntry(surface, BlurBlendMode.LinearLightLab)),
            "Lab Lighten" to listOf(BlendColorEntry(surface, BlurBlendMode.LabLightenWithGreyscale)),
            "Lab Darken" to listOf(BlendColorEntry(surface, BlurBlendMode.LabDarkenWithGreyscale)),
            "MI Difference" to listOf(BlendColorEntry(surface, BlurBlendMode.MiDifference)),
            "MI Color Dodge" to listOf(BlendColorEntry(surface, BlurBlendMode.MiColorDodge)),
            "MI Color Burn" to listOf(BlendColorEntry(surface, BlurBlendMode.MiColorBurn)),
            "Plus Lighter" to listOf(BlendColorEntry(surface, BlurBlendMode.PlusLighter)),
            "Plus Darker" to listOf(BlendColorEntry(surface, BlurBlendMode.PlusDarker)),
        )
    }
    val configEntries = blendConfigs.entries.toList()
    var blendModeIndex by remember { mutableIntStateOf(0) }
    val currentConfigValue = configEntries.getOrNull(blendModeIndex)?.value ?: emptyList()
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }

    var iconProgress by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    if (iconProgress != 1f) iconProgress = 1f
                    if (projectNameProgress != 1f) projectNameProgress = 1f
                    if (iconProgress != 1f) iconProgress = 1f
                    return@onEach
                }

                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val stage3TotalLength = projectNameY - iconY

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress = ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                projectNameProgress = ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                iconProgress = ((offset.toFloat() - stage1TotalLength - stage2TotalLength) / stage3TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
            .collect { }
    }

    BgEffectBackground(
        dynamicBackground = dynamicBackground.value,
        isOs3Effect = isOs3Effect,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = effectBackground.value,
        alpha = { 1f - scrollProgress },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = logoPadding.calculateRightPadding(LayoutDirection.Ltr),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(24.dp)
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .background(Color.Transparent)
                    .onGloballyPositioned { coordinates ->
                        if (iconY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        iconY = y + size.height
                    },
            ) {
                Image(
                    modifier = Modifier.size(200.dp).textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 150f,
                        noiseCoefficient = noiseCoefficient,
                        colors = BlurColors(
                            blendColors = logoBlend,
                        ),
                        contentBlendMode = ComposeBlendMode.DstIn,
                        enabled = blurEnable,
                    ),
                    painter = painterResource(R.drawable.moon),
                    contentDescription = null,
                    alpha = 0.7f
                )
            }
            Text(
                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp)
                    .onGloballyPositioned { coordinates ->
                        if (projectNameY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        projectNameY = y + size.height
                    }
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 150f,
                        noiseCoefficient = noiseCoefficient,
                        colors = BlurColors(
                            blendColors = logoBlend,
                        ),
                        contentBlendMode = ComposeBlendMode.DstIn,
                        enabled = blurEnable,
                    ),
                text = stringResource(R.string.app_name) + if (BuildConfig.FREEZER_TYPE == "CI") " - CI" else "",
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = BuildConfig.VERSION_NAME + "." + BuildConfig.VERSION_CODE + "." + if (BuildConfig.BUILD_TYPE == "debug" || BuildConfig.DEBUG) "1" else "0",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().pageScrollModifiers(
                appState.enableScrollEndHaptic,
                true,
                topAppBarScrollBehavior,
            ),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 126.dp,
                        )
                        .onSizeChanged { size ->
                            onLogoHeightChanged(size.height)
                        }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = scrollPadding.calculateBottomPadding()),
                ) {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                            .textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = blurRadius,
                                noiseCoefficient = noiseCoefficient,
                                colors = BlurColors(
                                    blendColors = currentConfigValue,
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                ),
                                enabled = blurEnable && isInDark,
                            ),
                        colors = CardDefaults.defaultColors(
                            if (blurEnable && isInDark) Color.Transparent else colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.about_qq_group),
                            onClick = { uriHandler.openUri("https://qm.qq.com/q/jPqwiLpHs6") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about_telegram_channel),
                            onClick = { uriHandler.openUri("https://t.me/cirnoadk") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about_coolapk),
                            onClick = { uriHandler.openUri("https://www.coolapk.com/u/31449483") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about_github_source),
                            onClick = { uriHandler.openUri("https://github.com/Adkimsm/Cirno") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about_original_project),
                            onClick = { uriHandler.openUri("https://github.com/Freezer-Team/Cirno") },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = scrollPadding,
        )
    }

    OverlayBottomSheet(
        show = showTextureSet,
        title = "Texture Set",
        onDismissRequest = {
            showTextureSet = false
        },
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        LazyColumn {
            item {
                Card {
                    BasicComponent(
                        title = "Effect Background Enabled",
                        endActions = {
                            Switch(
                                effectBackground.value,
                                {
                                    effectBackground.value = it
                                },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )
                    BasicComponent(
                        title = "Dynamic Background Enabled",
                        endActions = {
                            Switch(
                                dynamicBackground.value,
                                {
                                    dynamicBackground.value = it
                                },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )
                    BasicComponent(
                        title = "Blur Enable",
                        endActions = {
                            Switch(
                                blurEnable,
                                {
                                    blurEnable = it
                                },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )
                    BasicComponent(
                        title = "Blur Radius",
                        endActions = { ValueText("${blurRadius.toInt()}") },
                        bottomAction = {
                            Slider(
                                value = blurRadius / 200f,
                                onValueChange = { blurRadius = it * 200f },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )

                    BasicComponent(
                        title = "Noise",
                        endActions = { ValueText("${(noiseCoefficient * 10000).toInt() / 10000f}") },
                        bottomAction = {
                            Slider(
                                value = noiseCoefficient / 0.1f,
                                onValueChange = { noiseCoefficient = it * 0.1f },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )

                    BasicComponent(
                        title = "Brightness",
                        endActions = { ValueText("${(brightness * 100).toInt() / 100f}") },
                        bottomAction = {
                            Slider(
                                value = (brightness + 1f) / 2f,
                                onValueChange = { brightness = it * 2f - 1f },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )

                    BasicComponent(
                        title = "Contrast",
                        endActions = { ValueText("${(contrast * 100).toInt() / 100f}") },
                        bottomAction = {
                            Slider(
                                value = contrast / 3f,
                                onValueChange = { contrast = it * 3f },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )

                    BasicComponent(
                        title = "Saturation",
                        endActions = { ValueText("${(saturation * 100).toInt() / 100f}") },
                        bottomAction = {
                            Slider(
                                value = saturation / 3f,
                                onValueChange = { saturation = it * 3f },
                            )
                        },
                        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 0.dp),
                    )

                    val currentConfigName = configEntries.getOrNull(blendModeIndex)?.key ?: "Default"
                    val modeId = blendConfigs[currentConfigName]
                    BasicComponent(
                        title = "Blend Mode",
                        endActions = {
                            ValueText(currentConfigName + if (modeId != null) " ($modeId)" else "")
                        },
                        bottomAction = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                TextButton(
                                    text = "Prev",
                                    onClick = {
                                        blendModeIndex = (blendModeIndex - 1 + blendConfigs.size) % blendConfigs.size
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    text = "Next",
                                    onClick = {
                                        blendModeIndex = (blendModeIndex + 1) % blendConfigs.size
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = colorScheme.onSurfaceVariantActions,
    )
}
