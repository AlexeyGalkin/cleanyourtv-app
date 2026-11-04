package tv.cleanyour.ru.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** The data, the title maths, the lines under the clock and the phone's settings, on fixtures saved from the APIs. */
public class WidgetsTest {
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private static String fixture(String name) throws IOException {
        try (InputStream in = WidgetsTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- F1

    @Test
    public void seasonReadsEveryRaceWithItsSessionsInOrder() throws Exception {
        F1.Season s = F1.season(fixture("current.json"));
        assertEquals("2026", s.year);
        assertEquals(23, s.races.size());
        F1.Race china = s.races.get(1);
        assertEquals("shanghai", china.circuitId);
        assertTrue(china.sprint());
        assertEquals(F1.Kind.FP1, china.sessions.get(0).kind);
        assertEquals(F1.Kind.SPRINT_QUALI, china.sessions.get(1).kind);
        assertEquals(F1.Kind.SPRINT, china.sessions.get(2).kind);
        assertEquals(F1.Kind.QUALI, china.sessions.get(3).kind);
        assertEquals(F1.Kind.RACE, china.race().kind);
        assertEquals(Instant.parse("2026-03-15T07:00:00Z"), china.race().start);
    }

    @Test
    public void theWeekendShownIsTheNextOneUntilFourHoursAfterItsRace() throws Exception {
        F1.Season s = F1.season(fixture("current.json"));
        F1.Race brazil = s.current(Instant.parse("2026-11-04T12:00:00Z"));
        assertEquals("interlagos", brazil.circuitId);
        assertEquals(20, brazil.round);
        // Two hours after the race ends it is still Brazil; a day later it is Las Vegas.
        Instant raceEnd = brazil.race().end();
        assertEquals(20, s.current(raceEnd.plusSeconds(2 * 3600)).round);
        assertEquals(21, s.current(raceEnd.plusSeconds(24 * 3600)).round);
        assertNull(s.current(Instant.parse("2027-01-01T00:00:00Z")));
    }

    @Test
    public void standingsReadPointsWinsAndTheDriversLastTeam() throws Exception {
        F1.Table d = F1.drivers(fixture("driverstandings.json"));
        assertEquals(19, d.round);
        F1.Standing first = d.rows.get(0);
        assertEquals("antonelli", first.id);
        assertEquals(380, first.points, 0);
        assertEquals("mercedes", first.teamId);
        for (F1.Standing st : d.rows) {
            if (st.id.equals("lawson")) assertEquals("red_bull", st.teamId); // rb then red_bull: the last one
        }
        F1.Table c = F1.constructors(fixture("constructorstandings.json"));
        assertEquals("mercedes", c.rows.get(0).id);
        assertEquals(630, c.rows.get(0).points, 0);
    }

    @Test
    public void lastRaceReadsResultsAndTheFastestLap() throws Exception {
        F1.LastRace last = F1.lastRace(fixture("last-results.json"));
        assertEquals(19, last.round);
        assertEquals("1", last.results.get(0).position);
        assertEquals("max_verstappen", last.results.get(0).driverId);
        assertFalse(last.fastestLap.isEmpty());
    }

    @Test
    public void theTitleIsClinchedWhenTheLeadBeatsWhatIsLeft() throws Exception {
        F1.Season s = F1.season(fixture("current.json"));
        F1.Table d = F1.drivers(fixture("driverstandings.json"));
        int[] left = s.remaining(Instant.parse("2026-11-04T12:00:00Z"));
        assertEquals(4, left[0]);
        assertEquals(0, left[1]);
        assertTrue(F1.clinched(d, left));                       // 119 > 4 * 25
        assertFalse(F1.clinched(d, new int[] {5, 0}));          // 119 < 125
    }

    @Test
    public void theF1LineCountsDownToTheNextSessionInRussian() throws Exception {
        F1.Season s = F1.season(fixture("current.json"));
        Turn t = Turns.f1(s, Instant.parse("2026-11-04T12:00:00Z"), true);
        assertEquals("Этап 20 · Гран-при Сан-Паулу", t.topLine);
        assertEquals("Практика 1", t.word);
        assertEquals(Instant.parse("2026-11-06T15:30:00Z").toEpochMilli(), t.startMs);
        assertEquals(t.startMs + 60 * 60_000L, t.endMs);
        // During the race weekend's last session the word is the race start.
        Turn race = Turns.f1(s, Instant.parse("2026-11-08T16:00:00Z"), true);
        assertEquals("Старт гонки", race.word);
        Turn en = Turns.f1(s, Instant.parse("2026-11-04T12:00:00Z"), false);
        assertEquals("Round 20 · São Paulo Grand Prix", en.topLine.replace("Brazilian Grand Prix", "São Paulo Grand Prix"));
        assertNull(Turns.f1(s, Instant.parse("2027-01-01T00:00:00Z"), true));
    }

    @Test
    public void driverNamesAreRussianWithAnInitialAndFallBackToLatin() {
        assertEquals("М. Ферстаппен", Names.driverShort("max_verstappen", "Max", "Verstappen", true));
        assertEquals("M. Verstappen", Names.driverShort("max_verstappen", "Max", "Verstappen", false));
        assertEquals("J. Newcomer", Names.driverShort("newcomer", "Jo", "Newcomer", true).replace("Jo", "J"));
        assertTrue(Names.driverShort("ayumu_iwasa", "Ayumu", "Iwasa", true).matches("[А-Яа-яЁё. ]+"));
        assertEquals("Рейсинг Буллз", Names.team("rb", "RB F1 Team", true));
        assertEquals("RB F1 Team", Names.team("rb", "RB F1 Team", false));
    }

    // ---- Weather

    @Test
    public void forecastReadsNowAndTheDaysWithSunriseAndSunset() throws Exception {
        Weather.Forecast f = Weather.parse(fixture("open-meteo-moscow.json"));
        assertEquals(MSK, f.zone);
        assertEquals(7, f.days.size());
        assertNotNull(f.days.get(0).sunrise);
        assertNotNull(f.days.get(0).sunset);
        assertTrue(f.days.get(0).sunset.isAfter(f.days.get(0).sunrise));
    }

    @Test
    public void theWeatherLineCountsDownToTheNextSunsetThenSunrise() throws Exception {
        Weather.Forecast f = Weather.parse(fixture("open-meteo-moscow.json"));
        Config cfg = Config.parse("{\"spec\":1,\"weather\":{\"name\":\"Москва\",\"latitude\":55.75,\"longitude\":37.62}}");
        Weather.Day today = f.days.get(0);
        Turn noon = Turns.weather(cfg, f, today.sunrise.plusSeconds(3600), true);
        assertEquals("Закат", noon.word);
        assertEquals(today.sunset.toEpochMilli(), noon.startMs);
        assertTrue(noon.topLine.startsWith("Москва · "));
        Turn evening = Turns.weather(cfg, f, today.sunset.plusSeconds(60), true);
        assertEquals("Рассвет", evening.word);
        assertEquals(f.days.get(1).sunrise.toEpochMilli(), evening.startMs);
    }

    @Test
    public void degreesHaveASignAndWordsAreRussian() {
        assertEquals("+3°", Weather.degrees(2.6));
        assertEquals("−5°", Weather.degrees(-5.2));
        assertEquals("0°", Weather.degrees(-0.3));
        assertEquals("пасмурно", Weather.text(3, true));
        assertEquals("снегопад", Weather.text(85, true));
        assertEquals("overcast", Weather.text(3, false));
    }

    // ---- Holidays

    private static List<String> names(List<Holidays.Holiday> list) {
        List<String> out = new java.util.ArrayList<>();
        for (Holidays.Holiday h : list) out.add(h.ru);
        return out;
    }

    @Test
    public void orthodoxEasterIsRight() {
        assertEquals(LocalDate.of(2025, 4, 20), Holidays.orthodoxEaster(2025));
        assertEquals(LocalDate.of(2026, 4, 12), Holidays.orthodoxEaster(2026));
        assertEquals(LocalDate.of(2027, 5, 2), Holidays.orthodoxEaster(2027));
    }

    @Test
    public void holidaysOfTheDayPublicFirstAndFunOnlyWhenWanted() {
        List<Holidays.Holiday> unity = Holidays.on(LocalDate.of(2026, 11, 4), true);
        assertEquals("День народного единства", unity.get(0).ru);
        assertEquals(Holidays.MAJOR, unity.get(0).kind);
        assertEquals("Всемирный день телевидения", Holidays.on(LocalDate.of(2026, 11, 21), true).get(0).ru);
        assertTrue(Holidays.on(LocalDate.of(2026, 11, 21), false).isEmpty());
        assertTrue(names(Holidays.on(LocalDate.of(2026, 9, 13), true)).contains("День программиста"));
        assertTrue(names(Holidays.on(LocalDate.of(2028, 9, 12), true)).contains("День программиста")); // leap year
        assertEquals("День матери", Holidays.on(LocalDate.of(2026, 11, 29), true).get(0).ru);     // last Sunday
        assertEquals("Масленица", Holidays.on(LocalDate.of(2026, 2, 16), true).get(0).ru);        // Easter − 55
    }

    @Test
    public void theHolidayLineNamesTodayAndCountsDownToTheNextPublicHoliday() throws Exception {
        Config cfg = Config.parse("{\"spec\":1,\"holidays\":{}}");
        Turn t = Turns.holidays(cfg, LocalDate.of(2026, 11, 4), MSK, true);
        assertEquals("Сегодня: День народного единства", t.topLine);
        assertEquals("Новый год", t.word);
        assertEquals(LocalDate.of(2027, 1, 1).atStartOfDay(MSK).toInstant().toEpochMilli(), t.startMs);
        Turn quiet = Turns.holidays(cfg, LocalDate.of(2026, 11, 6), MSK, true);
        assertEquals("Ближайший праздник", quiet.topLine);
        Turn twoAtOnce = Turns.holidays(cfg, LocalDate.of(2026, 10, 1), MSK, true);
        assertEquals("Сегодня: Международный день музыки · Международный день кофе", twoAtOnce.topLine);
    }

    // ---- Settings and the rotation

    @Test
    public void settingsFromThePhoneAreValidated() throws Exception {
        Config c = Config.parse("{\"spec\":1,\"lang\":\"ru\",\"turn_seconds\":15,\"f1\":{\"under_clock\":true},"
                + "\"weather\":{\"under_clock\":true,\"name\":\"Москва\",\"admin1\":\"Москва\",\"country\":\"Россия\","
                + "\"latitude\":55.75204,\"longitude\":37.61781,\"timezone\":\"Europe/Moscow\"},\"holidays\":{\"fun\":false}}");
        assertTrue(c.f1);
        assertTrue(c.weather);
        assertTrue(c.holidays);
        assertFalse(c.funHolidays);
        assertEquals(15, c.turnSeconds);
        assertEquals("Москва, Россия", c.place());
        Config off = Config.parse("{\"spec\":1}");
        assertFalse(off.f1);
        assertFalse(off.weather);
        assertFalse(off.holidays);
        for (String bad : new String[] {"{}", "{\"spec\":2}", "not json", "{\"spec\":1,\"lang\":\"de\"}",
                "{\"spec\":1,\"weather\":{\"name\":\"X\",\"latitude\":91,\"longitude\":0}}",
                "{\"spec\":1,\"weather\":{\"name\":\"\",\"latitude\":1,\"longitude\":0}}"}) {
            try {
                Config.parse(bad);
                fail(bad);
            } catch (JSONException expected) {
                // rejected, the old settings stay
            }
        }
    }

    @Test
    public void theRotationHasOneLinePerWidgetSoonestFirst() throws Exception {
        F1.Season s = F1.season(fixture("current.json"));
        Weather.Forecast f = Weather.parse(fixture("open-meteo-moscow.json"));
        Config all = Config.parse("{\"spec\":1,\"f1\":{},\"weather\":{\"name\":\"Москва\",\"latitude\":55.75,\"longitude\":37.62},\"holidays\":{}}");
        Instant now = f.days.get(0).sunrise.plusSeconds(3600);
        List<Turn> turns = Turns.all(all, s, f, now, MSK, true);
        assertEquals(3, turns.size());
        assertEquals("weather", turns.get(0).source);
        for (int i = 1; i < turns.size(); i++) assertTrue(turns.get(i - 1).startMs <= turns.get(i).startMs);
        Config onlyF1 = Config.parse("{\"spec\":1,\"f1\":{}}");
        assertEquals(1, Turns.all(onlyF1, s, f, now, MSK, true).size());
    }
}
