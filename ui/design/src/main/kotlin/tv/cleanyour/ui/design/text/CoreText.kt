package tv.cleanyour.ui.design.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.cleanyour.ui.design.R

// Fork (Clean Your TV RU): English the core modules make reaches the screen in the app's language. The planner's
// progress lines, the group titles, the reasons an app is protected, the "Better back on" advice and the app
// descriptions (core/spec facts.tsv) are English by design: problem reports, logs, saved plans and the core tests
// keep them so. Here, at display time only, they are looked up in res/raw/core_ru.tsv ("English<TAB>Russian", one
// per line; "{1}" marks the one changing part, an app name or a number). Anything not in the table shows as it is.
object CoreText {
    class Table internal constructor(private val exact: Map<String, String>, private val patterns: List<Pair<Regex, String>>) {
        fun translate(text: String): String {
            exact[text]?.let { return it }
            for ((re, ru) in patterns) {
                val m = re.matchEntire(text) ?: continue
                return ru.replace("{1}", exact[m.groupValues[1]] ?: m.groupValues[1])
            }
            return text
        }

        val size: Int get() = exact.size + patterns.size
    }

    private val EMPTY = Table(emptyMap(), emptyList())
    @Volatile private var loaded: Table? = null

    /** The table from its text; templates with {1} become patterns, the most specific (longest) first. */
    fun parse(tsv: String): Table {
        val exact = HashMap<String, String>()
        val patterns = ArrayList<Pair<String, String>>()
        for (line in tsv.lineSequence()) {
            val tab = line.indexOf('\t')
            if (tab <= 0 || line.startsWith("#")) continue
            val en = line.substring(0, tab)
            val ru = line.substring(tab + 1).trimEnd('\r')
            if (ru.isEmpty()) continue
            if ("{1}" in en && "{1}" in ru) patterns += en to ru else exact[en] = ru
        }
        val compiled = patterns.sortedByDescending { it.first.length }.map { (en, ru) ->
            val (head, tail) = en.split("{1}", limit = 2).let { it[0] to it[1] }
            Regex("^" + Regex.escape(head) + "(.+?)" + Regex.escape(tail) + "$") to ru
        }
        return Table(exact, compiled)
    }

    fun table(context: Context): Table = loaded ?: synchronized(this) {
        loaded ?: runCatching {
            context.resources.openRawResource(R.raw.core_ru).use { parse(it.readBytes().decodeToString()) }
        }.getOrDefault(EMPTY).also { loaded = it }
    }

    /**
     * [text] in Russian when the app's resources resolved to Russian (core_text_lang, so a phone set to Ukrainian
     * then Russian, which Android shows in values-ru, gets it too) and the table has it, else as it is.
     */
    @Composable
    fun of(text: String): String {
        if (text.isEmpty() || stringResource(R.string.core_text_lang) != "ru") return text
        val context = LocalContext.current
        val t = remember(context) { table(context.applicationContext) }
        return t.translate(text)
    }
}
