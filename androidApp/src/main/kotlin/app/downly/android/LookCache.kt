package app.downly.android

import android.content.Context
import app.downly.data.AppLanguage
import app.downly.data.AppSettings
import app.downly.data.ThemeMode

/**
 * The language and theme of the last run, kept in a tiny preferences file that can be read in a few milliseconds.
 *
 * The full settings are read from disk a little after start. Until then the app must already look right: the
 * first frame, and the layout direction of every dialog and menu window, depend on the language.
 */
object LookCache {
    private const val PREFS = "downly_look"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME = "theme"

    fun language(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null).orEmpty()
        return runCatching { AppLanguage.valueOf(saved) }.getOrDefault(AppLanguage.EN)
    }

    fun theme(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null).orEmpty()
        return runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    /** Settings that only carry the cached look; the controller replaces them when the real ones are loaded. */
    fun settings(context: Context): AppSettings = AppSettings(language = language(context), theme = theme(context))

    fun save(context: Context, language: AppLanguage, theme: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, language.name)
            .putString(KEY_THEME, theme.name)
            .apply()
    }
}
