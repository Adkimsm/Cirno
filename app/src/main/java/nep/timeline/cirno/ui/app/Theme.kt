package nep.timeline.cirno.ui.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

const val UI_STYLE_MIUIX = 0
const val UI_STYLE_MATERIAL = 1

val LocalColorMode = compositionLocalOf { 0 }
val LocalUiStyle = compositionLocalOf { UI_STYLE_MIUIX }

private val WarmLightColorScheme = lightColorScheme(
    primary = Color(0xFF3A6B35),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBCFb2),
    onPrimaryContainer = Color(0xFF0D2109),
    secondary = Color(0xFF54634D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E8CD),
    onSecondaryContainer = Color(0xFF121F0D),
    tertiary = Color(0xFF386668),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBECEF),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7F1),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFF6F7F1),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFDFE4D7),
    onSurfaceVariant = Color(0xFF43483E),
    outline = Color(0xFF73796D),
    outlineVariant = Color(0xFFC3C8BC),
    inverseSurface = Color(0xFF2F312C),
    inverseOnSurface = Color(0xFFF0F1EB),
    surfaceDim = Color(0xFFD6D8D0),
    surfaceBright = Color(0xFFF6F7F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF0E9),
    surfaceContainer = Color(0xFFE7EAE1),
    surfaceContainerHigh = Color(0xFFE1E4DC),
    surfaceContainerHighest = Color(0xFFDBDED6),
)

private val WarmDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA1D399),
    onPrimary = Color(0xFF1D3719),
    primaryContainer = Color(0xFF334F2D),
    onPrimaryContainer = Color(0xFFBBCFb2),
    secondary = Color(0xFFBBCDB2),
    onSecondary = Color(0xFF273422),
    secondaryContainer = Color(0xFF3D4B37),
    onSecondaryContainer = Color(0xFFD7E8CD),
    tertiary = Color(0xFFA0D0D3),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF1E4E50),
    onTertiaryContainer = Color(0xFFBBECEF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C18),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF1A1C18),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF43483E),
    onSurfaceVariant = Color(0xFFC3C8BC),
    outline = Color(0xFF8D9387),
    outlineVariant = Color(0xFF43483E),
    inverseSurface = Color(0xFFE2E3DC),
    inverseOnSurface = Color(0xFF1A1C18),
    surfaceDim = Color(0xFF1A1C18),
    surfaceBright = Color(0xFF40423B),
    surfaceContainerLowest = Color(0xFF0F110C),
    surfaceContainerLow = Color(0xFF22241E),
    surfaceContainer = Color(0xFF262922),
    surfaceContainerHigh = Color(0xFF31332D),
    surfaceContainerHighest = Color(0xFF3C3E38),
)

private val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val MaterialTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Normal, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Normal, lineHeight = 52.sp),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun AppTheme(
    uiStyle: Int = UI_STYLE_MIUIX,
    colorMode: Int = 0,
    keyColor: Color? = null,
    paletteStyle: Int = 0,
    colorSpec: Int = 0,
    smoothRounding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val spec = ThemeColorSpec.entries.getOrNull(colorSpec) ?: ThemeColorSpec.Spec2021
    val style = ThemePaletteStyle.entries.getOrNull(paletteStyle) ?: ThemePaletteStyle.Content
    val context = LocalContext.current
    val monetEnabled = colorMode in 3..5
    val controller = remember(colorMode, keyColor, spec, style) {
        when (colorMode) {
            1 -> ThemeController(ColorSchemeMode.Light)
            2 -> ThemeController(ColorSchemeMode.Dark)
            3 -> ThemeController(ColorSchemeMode.MonetSystem, keyColor = keyColor, colorSpec = spec, paletteStyle = style)
            4 -> ThemeController(ColorSchemeMode.MonetLight, keyColor = keyColor, colorSpec = spec, paletteStyle = style)
            5 -> ThemeController(ColorSchemeMode.MonetDark, keyColor = keyColor, colorSpec = spec, paletteStyle = style)
            else -> ThemeController(ColorSchemeMode.System)
        }
    }
    val useDarkMaterial = when (colorMode) {
        2, 5 -> true
        1, 4 -> false
        else -> isSystemInDarkTheme()
    }
    val materialColors = if (monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (keyColor == null) {
            val baseScheme = remember(useDarkMaterial, context) {
                if (useDarkMaterial) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            rememberDynamicColorScheme(
                seedColor = Color.Unspecified,
                isDark = useDarkMaterial,
                style = style.toMaterialKolorStyle(),
                specVersion = spec.toMaterialKolorSpec(),
                primary = baseScheme.primary,
                secondary = baseScheme.secondary,
                tertiary = baseScheme.tertiary,
                neutral = baseScheme.surface,
                neutralVariant = baseScheme.surfaceVariant,
                error = baseScheme.error,
            )
        } else {
            rememberDynamicColorScheme(
                seedColor = keyColor,
                isDark = useDarkMaterial,
                style = style.toMaterialKolorStyle(),
                specVersion = spec.toMaterialKolorSpec(),
            )
        }
    } else {
        remember(colorMode, useDarkMaterial) {
            if (useDarkMaterial) WarmDarkColorScheme else WarmLightColorScheme
        }
    }
    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalUiStyle provides uiStyle,
        // miuix 0.9.2 起 MiuixTheme 不再接受 smoothRounding，需自行提供该 CompositionLocal
        LocalSquircleEnabled provides smoothRounding,
    ) {
        MiuixTheme(controller) {
            MaterialTheme(
                colorScheme = materialColors,
                shapes = MaterialShapes,
                typography = MaterialTypography,
                content = content,
            )
        }
    }
}

@Composable
fun isInDarkTheme(): Boolean = when (LocalColorMode.current) {
    1, 4 -> false

    // Force dark mode
    2, 5, 6 -> true

    // Follow system (0 or default)
    else -> isSystemInDarkTheme()
}

val KeyColors: List<Pair<String, Color>> = listOf(
    "Blue" to Color(0xFF3482FF),
    "Green" to Color(0xFF36D167),
    "Purple" to Color(0xFF7C4DFF),
    "Yellow" to Color(0xFFFFB21D),
    "Orange" to Color(0xFFFF5722),
    "Pink" to Color(0xFFE91E63),
    "Teal" to Color(0xFF00BCD4),
)

fun keyColorFor(index: Int): Color? = if (index <= 0) null else KeyColors.getOrNull(index - 1)?.second

fun themeColorSpecLabel(spec: ThemeColorSpec): String = when (spec) {
    ThemeColorSpec.Spec2021 -> "2021"
    ThemeColorSpec.Spec2025 -> "2025"
    else -> spec.name
}

fun themePaletteStyleLabel(style: ThemePaletteStyle): String = when (style) {
    ThemePaletteStyle.TonalSpot -> "Tonal spot"
    ThemePaletteStyle.Neutral -> "Neutral"
    ThemePaletteStyle.Vibrant -> "Vibrant"
    ThemePaletteStyle.Expressive -> "Expressive"
    ThemePaletteStyle.Rainbow -> "Rainbow"
    ThemePaletteStyle.FruitSalad -> "Fruit salad"
    ThemePaletteStyle.Monochrome -> "Monochrome"
    ThemePaletteStyle.Fidelity -> "Fidelity"
    ThemePaletteStyle.Content -> "Content"
    else -> style.name
}

private fun ThemePaletteStyle.toMaterialKolorStyle(): com.materialkolor.PaletteStyle = when (this) {
    ThemePaletteStyle.TonalSpot -> com.materialkolor.PaletteStyle.TonalSpot
    ThemePaletteStyle.Neutral -> com.materialkolor.PaletteStyle.Neutral
    ThemePaletteStyle.Vibrant -> com.materialkolor.PaletteStyle.Vibrant
    ThemePaletteStyle.Expressive -> com.materialkolor.PaletteStyle.Expressive
    ThemePaletteStyle.Rainbow -> com.materialkolor.PaletteStyle.Rainbow
    ThemePaletteStyle.FruitSalad -> com.materialkolor.PaletteStyle.FruitSalad
    ThemePaletteStyle.Monochrome -> com.materialkolor.PaletteStyle.Monochrome
    ThemePaletteStyle.Fidelity -> com.materialkolor.PaletteStyle.Fidelity
    ThemePaletteStyle.Content -> com.materialkolor.PaletteStyle.Content
    else -> com.materialkolor.PaletteStyle.TonalSpot
}

private fun ThemeColorSpec.toMaterialKolorSpec(): ColorSpec.SpecVersion = when (this) {
    ThemeColorSpec.Spec2021 -> ColorSpec.SpecVersion.SPEC_2021
    ThemeColorSpec.Spec2025 -> ColorSpec.SpecVersion.SPEC_2025
    else -> ColorSpec.SpecVersion.SPEC_2021
}
