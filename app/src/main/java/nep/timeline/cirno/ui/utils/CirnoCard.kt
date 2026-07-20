package nep.timeline.cirno.ui.utils

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun CirnoCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.Tilt,
    showIndication: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasBackground = BackgroundManager.currentUri != null
    val imageBackdrop = LocalImageBackdrop.current
    val useImageBlur = hasBackground && imageBackdrop != null

    val effectiveAlpha = when {
        useImageBlur -> BackgroundManager.cardAlphaBlurred
        hasBackground -> BackgroundManager.cardAlpha
        else -> null
    }
    val cardColors = if (effectiveAlpha != null) {
        colors.copy(color = colors.color.copy(alpha = effectiveAlpha))
    } else {
        colors
    }

    val blurModifier = if (useImageBlur) {
        Modifier
            .textureBlur(
                backdrop = imageBackdrop,
                shape = RoundedCornerShape(cornerRadius),
                blurRadius = BackgroundManager.cardBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(
                            color = MiuixTheme.colorScheme.surface.copy(BackgroundManager.cardBlurAlpha)
                        ),
                    ),
                ),
            )
    } else Modifier

    val finalModifier = modifier.then(blurModifier)

    if (onClick == null) {
        Card(
            modifier = finalModifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = cardColors,
            content = content,
        )
    } else {
        Card(
            modifier = finalModifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = cardColors,
            pressFeedbackType = pressFeedbackType,
            showIndication = showIndication,
            onClick = onClick,
            content = content,
        )
    }
}

@Composable
fun Modifier.cirnoCardBackground(
    shape: Shape,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
): Modifier {
    val hasBackground = BackgroundManager.currentUri != null
    val imageBackdrop = LocalImageBackdrop.current
    val useImageBlur = hasBackground && imageBackdrop != null

    val bgAlpha = when {
        useImageBlur -> BackgroundManager.cardAlphaBlurred
        hasBackground -> BackgroundManager.cardAlpha
        else -> null
    }
    val bgColor = if (bgAlpha != null) color.copy(alpha = bgAlpha) else color

    return this
        .then(
            if (useImageBlur) {
                Modifier
                    .textureBlur(
                        backdrop = imageBackdrop,
                        shape = shape,
                        blurRadius = BackgroundManager.cardBlurRadius,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(
                                    color = MiuixTheme.colorScheme.surface.copy(BackgroundManager.cardBlurAlpha)
                                ),
                            ),
                        ),
                    )
            } else Modifier
        )
        .background(bgColor, shape)
}
