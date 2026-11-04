package tv.cleanyour.core.flows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Fork (Clean Your TV RU): the widgets' choice, the add-on's config.json, and the city search's answer.
class ForkWidgetsTest {
    private val moscow = WidgetCity("Москва", "Москва", "Россия", 55.75204, 37.61781, "Europe/Moscow")

    @Test fun theConfigHasOnlyTheSwitchedOnLinesAndTheCity() {
        val json = Json.parseToJsonElement(ForkWidgets(f1 = true, weather = true, city = moscow).configJson("ru").decodeToString()).jsonObject
        assertEquals("1", json["spec"]!!.jsonPrimitive.content)
        assertEquals("ru", json["lang"]!!.jsonPrimitive.content)
        assertTrue("f1" in json)
        assertFalse("holidays" in json)
        val w = json["weather"]!!.jsonObject
        assertEquals("Москва", w["name"]!!.jsonPrimitive.content)
        assertEquals("true", w["under_clock"]!!.jsonPrimitive.content)
        val other = Json.parseToJsonElement(ForkWidgets(holidays = true).configJson("de").decodeToString()).jsonObject
        assertEquals("", other["lang"]!!.jsonPrimitive.content)
    }

    @Test fun weatherWithoutACityIsNoLine() {
        assertFalse(ForkWidgets(weather = true).any)
        assertTrue(ForkWidgets(weather = true, city = moscow).any)
        assertTrue(ForkWidgets(holidays = true).any)
    }

    @Test fun theChoiceIsKeptPerTv() {
        val w = ForkWidgets(f1 = true, holidays = true, funHolidays = false, city = moscow)
        assertEquals(w, ForkWidgetsPrefs.decode(ForkWidgetsPrefs.encode(w)))
        assertNull(ForkWidgetsPrefs.decode("not json"))
        assertEquals("Москва, Россия", moscow.label)
    }

    @Test fun theCitySearchReadsOpenMeteosAnswer() {
        val answer = """{"results":[{"id":524901,"name":"Москва","latitude":55.75222,"longitude":37.61556,"timezone":"Europe/Moscow",
            "country":"Россия","admin1":"Москва"},{"name":"Moscow","latitude":46.73239,"longitude":-117.00017,"country":"США","admin1":"Айдахо"},
            {"name":"","latitude":1.0,"longitude":2.0},{"name":"Nowhere","latitude":95.0,"longitude":0.0}],"generationtime_ms":0.5}"""
        val cities = CityLookup.parse(answer)
        assertEquals(listOf("Москва", "Moscow"), cities.map { it.name })
        assertEquals("Moscow, Айдахо, США", cities[1].label)
        assertEquals(emptyList(), CityLookup.parse("""{"generationtime_ms":0.3}"""))
    }
}
