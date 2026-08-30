package nep.timeline.cirno.ui.custom

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CustomBasicComponent(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    title: String? = null,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    titleFontSize: TextUnit = TextUnit.Unspecified,
    subtitle: String? = null,
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    subtitleAnnotated: AnnotatedString? = null,
    subtitleFontSize: TextUnit = TextUnit.Unspecified,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    subtitleOverflow: TextOverflow = TextOverflow.Clip,
    summary: String? = null,
    summaryColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    leftAction: @Composable (() -> Unit?)? = null,
    rightText: String? = null,
    rightTextColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    rightActions: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Row(
        modifier = if (onClick != null) {
            modifier
                .clickable(
                    indication = LocalIndication.current,
                    interactionSource = interactionSource
                ) {
                    onClick.invoke()
                }
        } else {
            modifier
        }
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .padding(insideMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        leftAction?.let {
            it()
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.title4,
                    fontSize = titleFontSize,
                    color = titleColor
                )
            }
            if (subtitleAnnotated != null) {
                Text(
                    text = subtitleAnnotated,
                    style = MiuixTheme.textStyles.subtitle,
                    fontSize = subtitleFontSize,
                )
            } else {
                subtitle?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.subtitle,
                        fontSize = subtitleFontSize,
                        maxLines = subtitleMaxLines,
                        overflow = subtitleOverflow,
                        color = subtitleColor
                    )
                }
            }
            summary?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = summaryColor
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            rightText?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = rightTextColor
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = rightActions
            )
        }
    }
}
