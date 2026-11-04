package tv.cleanyour.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

// Fork (Clean Your TV RU): the app's own language, chosen in Settings: the phone's, English or Russian.
// Android 13 and later keep it as the per-app language (LocaleManager, also offered in the system's app
// settings through res/xml/locales_config.xml); on Android 9 to 12 the choice is a small preference applied to
// every context the app makes (MainActivity and CytApplication attachBaseContext). "" is the phone's language.
object AppLanguage {
    val TAGS: List<String> = listOf("", "en", "ru")
    private const val PREFS = "cyt_language"
    private const val KEY = "tag"

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
                .let { if (it in TAGS) it else "" }
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().let { if (it in TAGS) it else "" }
        }

    fun set(activity: Activity, tag: String) {
        require(tag in TAGS) { "unknown language $tag" }
        if (tag == current(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            // The system recreates the activities in the new language.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            activity.recreate()
        }
    }

    // Android 9 to 12: a context whose resources (and ICU formatting, which reads the configuration's locale) use
    // the chosen language. Android 13 and later do this themselves.
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty() || tag !in TAGS) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
