package tv.cleanyour.core.flows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.store.DocumentStore
import java.net.URLEncoder
import java.util.Locale

// Fork (Clean Your TV RU): the TV widgets of the Widgets add-on (tv-addons/widgets), chosen on the Widgets page.
// Each switched-on widget is one line under the TV's clock (the launcher's game_day rotation): the next F1
// session, the weather with the next sunrise or sunset for the chosen city, and today's holidays. The phone keeps
// the choice per TV (tvs/<id>/fork-widgets.json) and sends it to the add-on as its config.json
// (scripts/fork/ADDONS-CONTRACT.md) with a Make it yours send.

data class WidgetCity(
    val name: String, val admin1: String, val country: String,
    val latitude: Double, val longitude: Double, val timezone: String,
) {
    // "Москва, Россия"; the region only when it adds something.
    val label: String get() = listOf(name, admin1.takeIf { it.isNotBlank() && it != name }, country.takeIf { it.isNotBlank() })
        .filterNotNull().joinToString(", ")
}

data class ForkWidgets(
    val f1: Boolean = false,
    val weather: Boolean = false,
    val holidays: Boolean = false,
    val funHolidays: Boolean = true,
    val city: WidgetCity? = null,
) {
    // Whether any line goes under the clock: weather needs its city.
    val any: Boolean get() = f1 || (weather && city != null) || holidays

    // The add-on's config.json, spec 1. `lang` "ru", "en" or "" (the TV's).
    fun configJson(lang: String): ByteArray = buildJsonObject {
        put("spec", 1)
        put("lang", lang.takeIf { it == "ru" || it == "en" } ?: "")
        put("turn_seconds", TURN_SECONDS)
        if (f1) put("f1", buildJsonObject { put("under_clock", true) })
        city?.let { c ->
            put("weather", buildJsonObject {
                put("under_clock", weather)
                put("name", c.name); put("admin1", c.admin1); put("country", c.country)
                put("latitude", c.latitude); put("longitude", c.longitude); put("timezone", c.timezone)
            })
        }
        if (holidays) put("holidays", buildJsonObject { put("under_clock", true); put("fun", funHolidays) })
    }.toString().encodeToByteArray()

    companion object {
        const val PKG = "tv.cleanyour.ru.widgets"
        const val MODULE_ID = "cyt-widgets"
        const val TURN_SECONDS = 20
        // game_day: launcher 3.3 (versionCode 11) reads it.
        const val GAME_DAY_KEY = "game_day"
    }
}

class ForkWidgetsPrefs(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/fork-widgets.json"

    suspend fun load(id: TvId): ForkWidgets? = store.read(path(id)) { decode(it.decodeToString()) }

    suspend fun save(id: TvId, w: ForkWidgets) = store.write(path(id), encode(w).encodeToByteArray())

    companion object {
        fun encode(w: ForkWidgets): String = buildJsonObject {
            put("f1", w.f1); put("weather", w.weather); put("holidays", w.holidays); put("fun", w.funHolidays)
            w.city?.let { c ->
                put("city", buildJsonObject {
                    put("name", c.name); put("admin1", c.admin1); put("country", c.country)
                    put("latitude", c.latitude); put("longitude", c.longitude); put("timezone", c.timezone)
                })
            }
        }.toString()

        fun decode(text: String): ForkWidgets? = try {
            val o = Json.parseToJsonElement(text).jsonObject
            fun b(k: String, d: Boolean) = (o[k] as? JsonPrimitive)?.booleanOrNull ?: d
            ForkWidgets(b("f1", false), b("weather", false), b("holidays", false), b("fun", true), (o["city"] as? JsonObject)?.let(::city))
        } catch (e: Exception) {
            null
        }

        internal fun city(c: JsonObject): WidgetCity? {
            fun s(k: String) = (c[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            val lat = (c["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
            val lon = (c["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
            val name = s("name").trim()
            if (name.isEmpty() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return WidgetCity(name.take(80), s("admin1").take(80), s("country").take(80), lat, lon, s("timezone").take(64))
        }
    }
}

// The city search on the Weather widget: Open-Meteo's geocoding (no key), in the phone's language.
// Only this host (DownloadHosts) and only when the person searches.
class CityLookup(private val http: HttpFetcher?) {
    sealed interface Result {
        data class Found(val cities: List<WidgetCity>) : Result
        data object Offline : Result
    }

    suspend fun search(query: String, lang: String = Locale.getDefault().language): Result {
        val q = query.trim()
        if (q.length < 2 || http == null) return Result.Found(emptyList())
        val url = "https://$HOST/v1/search?name=" + URLEncoder.encode(q.take(80), "UTF-8") +
            "&count=8&language=" + (if (lang == "ru") "ru" else "en") + "&format=json"
        return try {
            val r = http.get(url, null, null)
            val body = r.body
            if (r.code != 200 || body == null) return Result.Offline
            // InputStream.readNBytes is Android 13+; the app runs from Android 9.
            val text = withContext(Dispatchers.IO) { body.use { readCapped(it) }.decodeToString() }
            Result.Found(parse(text))
        } catch (e: Exception) {
            Result.Offline
        }
    }

    companion object {
        const val HOST = "geocoding-api.open-meteo.com"
        private const val MAX_BYTES = 256 * 1024

        // At most MAX_BYTES of the answer (a city search is a few kilobytes).
        internal fun readCapped(input: java.io.InputStream): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (out.size() < MAX_BYTES) {
                val n = input.read(buf, 0, minOf(buf.size, MAX_BYTES - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        fun parse(text: String): List<WidgetCity> {
            val o = Json.parseToJsonElement(text).jsonObject
            val results = o["results"]?.jsonArray ?: return emptyList()
            return results.mapNotNull { (it as? JsonObject)?.let(ForkWidgetsPrefs::city) }
        }
    }
}
