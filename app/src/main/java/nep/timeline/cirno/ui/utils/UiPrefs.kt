package nep.timeline.cirno.ui.utils

import android.content.Context
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported

object UiPrefs {
    private const val PREFS_NAME = "cirno_ui"
    private const val KEY_UI_STYLE = "ui_style"
    private const val KEY_NAVIGATION_STYLE = "navigation_style"
    private const val KEY_COLOR_MODE = "color_mode"
    private const val KEY_THEME_KEY_COLOR = "theme_key_color"
    private const val KEY_THEME_COLOR_SPEC = "theme_color_spec"
    private const val KEY_THEME_PALETTE_STYLE = "theme_palette_style"
    private const val KEY_BLUR = "blur"

    data class UiState(
        val uiStyle: Int = 0,
        val navigationStyle: Int = 0,
        val colorMode: Int = 0,
        val themeKeyColor: Int = 0,
        val themeColorSpec: Int = 0,
        val themePaletteStyle: Int = 0,
        val blur: Boolean = isRenderEffectSupported(),
    )

    fun read(context: Context): UiState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return UiState(
            uiStyle = prefs.getInt(KEY_UI_STYLE, 0),
            navigationStyle = prefs.getInt(KEY_NAVIGATION_STYLE, 0),
            colorMode = prefs.getInt(KEY_COLOR_MODE, 0),
            themeKeyColor = prefs.getInt(KEY_THEME_KEY_COLOR, 0),
            themeColorSpec = prefs.getInt(KEY_THEME_COLOR_SPEC, 0),
            themePaletteStyle = prefs.getInt(KEY_THEME_PALETTE_STYLE, 0),
            blur = prefs.getBoolean(KEY_BLUR, isRenderEffectSupported()),
        )
    }

    fun getUiStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_UI_STYLE, 0)

    fun setUiStyle(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_UI_STYLE, value).apply()
    }

    fun getNavigationStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_NAVIGATION_STYLE, 0)

    fun setNavigationStyle(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NAVIGATION_STYLE, value).apply()
    }

    fun getColorMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_COLOR_MODE, 0)

    fun setColorMode(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_COLOR_MODE, value).apply()
    }

    fun getThemeKeyColor(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME_KEY_COLOR, 0)

    fun setThemeKeyColor(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_KEY_COLOR, value).apply()
    }

    fun getThemeColorSpec(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME_COLOR_SPEC, 0)

    fun setThemeColorSpec(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_COLOR_SPEC, value).apply()
    }

    fun getThemePaletteStyle(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME_PALETTE_STYLE, 0)

    fun setThemePaletteStyle(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_PALETTE_STYLE, value).apply()
    }

    fun getBlur(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLUR, isRenderEffectSupported())

    fun setBlur(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLUR, value).apply()
    }
}
