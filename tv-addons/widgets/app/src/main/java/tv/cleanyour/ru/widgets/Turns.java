package tv.cleanyour.ru.widgets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The lines under the clock, one per switched-on widget; the launcher rotates them. Pure: the data, the time, the zone
 * and the language come in, the rows go out (GamesProvider adds the turn length).
 */
final class Turns {
    private Turns() {
    }

    static String session(F1.Kind k, boolean ru) {
        switch (k) {
            case FP1: return ru ? "Практика 1" : "Practice 1";
            case FP2: return ru ? "Практика 2" : "Practice 2";
            case FP3: return ru ? "Практика 3" : "Practice 3";
            case SPRINT_QUALI: return ru ? "Спринт-квалификация" : "Sprint qualifying";
            case SPRINT: return ru ? "Спринт" : "Sprint";
            case QUALI: return ru ? "Квалификация" : "Qualifying";
            default: return ru ? "Гонка" : "Race";
        }
    }

    /** The F1 line: "Этап 20 · Гран-при Сан-Паулу" over "Квалификация через 2 ч 14 мин". */
    static Turn f1(F1.Season season, Instant now, boolean ru) {
        if (season == null) return null;
        // The next weekend as soon as this one's race is over, not four hours later.
        Object[] up = F1.upcoming(season, now);
        if (up == null) return null;
        F1.Race race = (F1.Race) up[0];
        F1.Session s = (F1.Session) up[1];
        String top = (ru ? "Этап " : "Round ") + race.round + " · " + Names.grandPrix(race, ru);
        String word = s.kind == F1.Kind.RACE ? (ru ? "Старт гонки" : "Lights out") : session(s.kind, ru);
        return new Turn(top, word, s.start.toEpochMilli(), s.end().toEpochMilli(), Turn.F1_RED, "f1");
    }

    /** The weather line: "Москва · +3°, пасмурно" over "Закат через 1 ч 12 мин". */
    static Turn weather(Config cfg, Weather.Forecast f, Instant now, boolean ru) {
        if (cfg.city == null || f == null) return null;
        Object[] sun = Weather.nextSun(f, now);
        if (sun == null) return null;
        Instant at = (Instant) sun[1];
        String top = cfg.city.name + " · " + Weather.degrees(f.temp) + ", " + Sky.wordsInline(f.nowKind, ru);
        String word = (Boolean) sun[0] ? (ru ? "Рассвет" : "Sunrise") : (ru ? "Закат" : "Sunset");
        // One minute "live", then the launcher drops the row and asks again for the next sunrise or sunset.
        return new Turn(top, word, at.toEpochMilli(), at.toEpochMilli() + 60_000, Turn.WEATHER_BLUE, "weather");
    }

    /** The holiday line: "Сегодня: День народного единства" over "Новый год через 57 д 3 ч". */
    static Turn holidays(Config cfg, LocalDate today, ZoneId zone, boolean ru) {
        Object[] next = Holidays.nextMajor(today);
        if (next == null) return null;
        List<Holidays.Holiday> now = Holidays.on(today, cfg.funHolidays);
        StringBuilder top = new StringBuilder();
        if (now.isEmpty()) {
            top.append(ru ? "Ближайший праздник" : "Next holiday");
        } else {
            top.append(ru ? "Сегодня: " : "Today: ");
            for (int i = 0; i < Math.min(2, now.size()); i++) {
                if (i > 0) top.append(" · ");
                top.append(now.get(i).name(ru));
            }
        }
        LocalDate day = (LocalDate) next[0];
        Holidays.Holiday h = (Holidays.Holiday) next[1];
        long start = day.atStartOfDay(zone).toInstant().toEpochMilli();
        return new Turn(top.toString(), h.name(ru), start, start + 60_000, Turn.HOLIDAY_GOLD, "holidays");
    }

    static List<Turn> all(Config cfg, F1.Season season, Weather.Forecast forecast, Instant now, ZoneId zone, boolean ru) {
        List<Turn> out = new ArrayList<>();
        if (cfg.f1) add(out, f1(season, now, ru));
        if (cfg.weather) add(out, weather(cfg, forecast, now, ru));
        if (cfg.holidays) add(out, holidays(cfg, now.atZone(zone).toLocalDate(), zone, ru));
        out.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        return out;
    }

    private static void add(List<Turn> out, Turn t) {
        if (t != null) out.add(t);
    }
}
