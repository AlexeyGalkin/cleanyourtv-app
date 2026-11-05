package tv.cleanyour.ru.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The weather's look from amounts, day and night stretches, and the Russian F1 news feeds. */
public class WeatherNewsTest {
    private static String fixture(String name) throws IOException {
        try (InputStream in = WeatherNewsTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void heavySnowAndLightSleetLookDifferent() {
        assertEquals(Sky.SNOW3, Sky.kind(0, 8, 75, 100, 4));         // a snowfall: 8 cm in 12 h
        assertEquals(Sky.SLEET1, Sky.kind(1.5, 0.3, 68, 100, 3));    // a little rain with a little snow
        assertEquals(Sky.SLEET2, Sky.kind(5, 1.5, 68, 100, 3));
        assertEquals(Sky.BLIZZARD, Sky.kind(0, 3, 75, 100, 12));     // snow and a strong wind
        assertEquals(Sky.SNOW1, Sky.kind(0, 0.4, 71, 100, 3));
        assertEquals(Sky.RAIN1, Sky.kind(1, 0, 61, 90, 3));
        assertEquals(Sky.RAIN3, Sky.kind(15, 0, 65, 100, 3));
        assertEquals(Sky.DRIZZLE, Sky.kind(0.5, 0, 53, 100, 2));
        assertEquals(Sky.THUNDER, Sky.kind(4, 0, 95, 100, 5));
        assertEquals(Sky.HAIL, Sky.kind(4, 0, 96, 100, 5));
        assertEquals(Sky.FOG, Sky.kind(0, 0, 45, 100, 1));
        assertEquals(Sky.CLEAR, Sky.kind(0, 0, 0, 5, 1));
        assertEquals(Sky.PARTLY, Sky.kind(0, 0, 1, 40, 1));
        assertEquals(Sky.CLOUDY, Sky.kind(0, 0, 2, 70, 1));
        assertEquals(Sky.OVERCAST, Sky.kind(0, 0, 3, 95, 1));
        assertEquals(Sky.SNOW3, Sky.now(0, 1.2, 75, 100, 3));         // 1.2 cm in an hour is a snowfall
        assertEquals("Снегопад", Sky.words(Sky.SNOW3, true));
        assertEquals("мокрый снег с дождём", Sky.wordsInline(Sky.SLEET1, true));
        assertEquals("Heavy snow", Sky.words(Sky.SNOW3, false));
    }

    @Test
    public void theHoursFoldIntoDaysAndNights() throws Exception {
        Weather.Forecast f = Weather.parse(fixture("open-meteo-moscow-hourly.json"));
        assertFalse(f.periods.isEmpty());
        for (Weather.Period p : f.periods) assertNotNull(p.kind);
        LocalDate today = LocalDate.parse("2026-11-04");
        Weather.Period[][] days = Weather.nextDays(f, today, 3);
        for (int i = 0; i < 3; i++) {
            assertNotNull("day " + i, days[i][0]);
            assertNotNull("night " + i, days[i][1]);
            assertTrue(days[i][0].day);
            assertFalse(days[i][1].day);
            assertEquals(today.plusDays(i + 1), days[i][0].date);
            assertTrue("a day is not colder than its night here", days[i][0].temp >= days[i][1].temp - 6);
        }
        assertNotNull(f.nowKind);
    }

    @Test
    public void f1newsAndChampionatGiveRussianF1Headlines() throws Exception {
        List<News.Item> a = News.parse(fixture("rss-f1news.xml"), "f1news.ru", false);
        List<News.Item> b = News.parse(fixture("rss-championat.xml"), "Чемпионат", true);
        assertEquals(12, a.size());
        assertFalse(b.isEmpty());
        assertTrue("only F1 from Чемпионат", b.size() <= 12);
        for (News.Item i : a) {
            assertFalse(i.title.isEmpty());
            assertTrue(i.timeMs > 0);
            assertFalse(i.title.contains("<![CDATA["));
            assertFalse(i.title.contains("&quot;"));
        }
        List<News.Item> all = News.merge(a, b);
        for (int i = 1; i < all.size(); i++) assertTrue(all.get(i - 1).timeMs >= all.get(i).timeMs);
        Set<String> titles = new HashSet<>();
        for (News.Item i : all) assertTrue("no repeats", titles.add(i.title));
        List<News.Item> back = News.fromJson(News.toJson(all));
        assertEquals(all.size(), back.size());
        assertEquals(all.get(0).title, back.get(0).title);
    }

    @Test
    public void afterTheRaceTheNextWeekendCountsDownAtOnce() throws Exception {
        F1.Season s = F1.season(fixture("current.json"));
        F1.Race brazil = s.races.get(19);
        assertEquals(20, brazil.round);
        java.time.Instant hourAfter = brazil.race().end().plusSeconds(3600);
        Turn t = Turns.f1(s, hourAfter, true);
        assertNotNull(t);
        assertTrue(t.topLine, t.topLine.startsWith("Этап 21"));
    }

    @Test
    public void currentAmountsAreForFifteenMinutesAndACodeOfSnowIsSnow() {
        // 0.02 cm in 15 minutes is about 1 cm in 12 hours: snow, not "light snow" nor overcast.
        assertEquals(Sky.SNOW2, Sky.now(48, 0, 0.025, 73, 100, 2));
        assertEquals(Sky.SNOW1, Sky.now(48, 0, 0, 71, 100, 2));     // the code says snow, the amount rounds to 0
        assertEquals(Sky.OVERCAST, Sky.now(48, 0, 0, 3, 100, 2));
    }

    @Test
    public void entitiesAndCdataAreCleaned() {
        assertEquals("a b", News.clean("a &#99999999; &#xD800; b"));   // bad code points become spaces, folded
        assertEquals("«Мерседес» — о проблемах & планах", News.clean("<![CDATA[&laquo;Мерседес&raquo; &mdash; о <b>проблемах</b> &amp; планах]]>"));
        assertEquals("Ferrari's \"car\"", News.clean("Ferrari&#39;s &quot;car&quot;"));
        assertEquals(new ArrayList<News.Item>().size(), News.parse(null, "x", false).size());
    }
}
