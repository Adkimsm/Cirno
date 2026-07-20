package nep.timeline.cirno.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nep.timeline.cirno.ui.app.LocalAppState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

val LocalImageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

fun Modifier.pageScrollModifiers(
    enableScrollEndHaptic: Boolean,
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
    .overScrollVertical()
    .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
    .fillMaxHeight()

@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
): PaddingValues {
    val topPadding = innerPadding.calculateTopPadding() + extraTop
    val bottomPadding = if (isWideScreen) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + outerPadding.calculateBottomPadding()
    } else {
        outerPadding.calculateBottomPadding()
    }
    return remember(topPadding, bottomPadding, extraStart, extraEnd) {
        PaddingValues(
            top = topPadding,
            start = extraStart,
            end = extraEnd,
            bottom = bottomPadding,
        )
    }
}

@Composable
fun AdaptiveTopAppBar(
    title: String,
    isWideScreen: Boolean,
    scrollBehavior: ScrollBehavior,
    subtitle: String = "",
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val imageBackdrop = LocalImageBackdrop.current
    val hasImageBlur = imageBackdrop != null && BackgroundManager.currentUri != null

    val barColor = if (BackgroundManager.currentUri != null && color != Color.Transparent) {
        if (hasImageBlur) {
            color.copy(alpha = scrollBehavior.state.collapsedFraction)
        } else {
            color.copy(alpha = scrollBehavior.state.collapsedFraction * BackgroundManager.topAppBarAlpha)
        }
    } else {
        color
    }

    if (isWideScreen || BackgroundManager.forceSmallTop) {
        SmallTopAppBar(
            title = title,
            subtitle = subtitle,
            color = barColor,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = false,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        TopAppBar(
            title = title,
            subtitle = subtitle,
            color = barColor,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

@Composable
fun rememberBlurBackdrop(blurEnabled: Boolean = true, replaceBlurChecker: Boolean = false): LayerBackdrop? {
    if (!replaceBlurChecker) {
        val appState = LocalAppState.current
        if (!appState.blur) return null
    } else if (!blurEnabled) return null

    if (!isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface

    return rememberLayerBackdrop {
        drawContent()
        drawRect(surfaceColor)
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    scrollBehavior: ScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    val imageBackdrop = LocalImageBackdrop.current
    val useImageBlur = imageBackdrop != null && blurEnabled
    val effectiveBackdrop = if (useImageBlur) imageBackdrop else backdrop
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 1f

    val blurRadius = if (useImageBlur) {
        BackgroundManager.topAppBarBlurRadius
    } else 45f

    val blendAlpha = if (useImageBlur) {
        BackgroundManager.topAppBarBlurAlpha * collapsedFraction.coerceAtLeast(0.15f)
    } else {
        collapsedFraction * 0.8f
    }

    Box(
        modifier = if (blurEnabled && effectiveBackdrop != null) {
            Modifier.textureBlur(
                backdrop = effectiveBackdrop,
                blurRadius = blurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(blendAlpha)),
                    ),
                )
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

fun Modifier.textureBlur(
    backdrop: Backdrop?,
    shape: Shape = RectangleShape,
    blurRadius: Float = 45f,
    noiseCoefficient: Float = BlurDefaults.NoiseCoefficient,
    colors: BlurColors = BlurColors(),
    contentBlendMode: ComposeBlendMode = ComposeBlendMode.SrcOver,
    enabled: Boolean = true,
): Modifier {
    if (backdrop == null || !isRenderEffectSupported())
        return this
    return textureEffect(
        backdrop = backdrop,
        shape = shape,
        blurRadius = blurRadius,
        noiseCoefficient = noiseCoefficient,
        colors = colors,
        contentBlendMode = contentBlendMode,
        enabled = enabled,
    )
}
