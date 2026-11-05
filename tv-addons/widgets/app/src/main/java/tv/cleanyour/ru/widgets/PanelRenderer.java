package tv.cleanyour.ru.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The two cards of the fork's launcher panel (widget_panel), drawn at the TV's density: F1 on the left, weather and
 * holidays on the right (docs/fork/widgets-mockup.html). Both are 412×176 dp with the same five rows at the same
 * heights, so they sit as a pair: a kicker, the main line, a detail line, three columns and a footer.
 */
final class PanelRenderer {
    static final int W = 412, H = 176;
    // Row tops in dp inside a card (12 dp padding, 1 dp edge, 5 dp between rows).
    private static final float PAD_X = 15, R0 = 13, R1 = 32, R2 = 71, R3 = 92, R4 = 145, ROW3_H = 48;
    private static final int WHITE = 0xFFFFFFFF, RED = 0xFFFF6B5E, GOLD = 0xFFFFC145, BLUE = 0xFF6DB3FF;

    private final float d;
    private final boolean ru;
    private final Locale locale;
    private final ZoneId zone;
    private final boolean h24;
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    PanelRenderer(float density, boolean ru, ZoneId zone, boolean h24) {
        this.d = density;
        this.ru = ru;
        this.locale = ru ? new Locale("ru") : Locale.ENGLISH;
        this.zone = zone;
        this.h24 = h24;
    }

    private String t(String r, String e) {
        return ru ? r : e;
    }

    // ---- drawing helpers, all in dp

    private TextPaint paint(Typeface face, float sp, int colour) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face);
        p.setTextSize(sp * d);
        p.setColor(colour);
        return p;
    }

    /** Text at (x, baseline) in dp, cut with "…" at maxW dp; returns the width used in dp. */
    private float text(Canvas c, String s, float x, float baseline, TextPaint p, float maxW) {
        if (s == null || s.isEmpty() || maxW <= 0) return 0;
        CharSequence fit = TextUtils.ellipsize(s, p, maxW * d, TextUtils.TruncateAt.END);
        c.drawText(fit, 0, fit.length(), x * d, baseline * d, p);
        return p.measureText(fit, 0, fit.length()) / d;
    }

    private Bitmap card() {
        return Bitmap.createBitmap(Math.round(W * d), Math.round(H * d), Bitmap.Config.ARGB_8888);
    }

    private void frame(Canvas c) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x4D000000);
        RectF r = new RectF(0.5f * d, 0.5f * d, (W - 0.5f) * d, (H - 0.5f) * d);
        c.drawRoundRect(r, 16 * d, 16 * d, bg);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1 * d);
        edge.setColor(0x21FFFFFF);
        c.drawRoundRect(r, 16 * d, 16 * d, edge);
    }

    /** Row 0: a coloured bar and the kicker in capitals. */
    private void kicker(Canvas c, int colour, String s) {
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(colour);
        c.drawRoundRect(new RectF(PAD_X * d, (R0 + 1) * d, (PAD_X + 3) * d, (R0 + 13) * d), 1.5f * d, 1.5f * d, bar);
        TextPaint k = paint(regular, 11.5f, 0xEBFFFFFF);
        k.setLetterSpacing(0.13f);
        text(c, s.toUpperCase(locale), PAD_X + 12, R0 + 11, k, W - 2 * PAD_X - 12);
    }

    /** Row 2: short pieces side by side, each in its own colour, as many as fit. */
    private void detail(Canvas c, List<String> pieces, List<Integer> colours) {
        TextPaint p = paint(regular, 10.5f, WHITE);
        p.setLetterSpacing(0.01f);
        float x = PAD_X, max = W - PAD_X;
        for (int i = 0; i < pieces.size(); i++) {
            String s = pieces.get(i);
            p.setColor(colours.get(i));
            p.setTypeface(colours.get(i) == RED ? medium : regular);
            float w = p.measureText(s) / d;
            if (x + w > max) break;
            c.drawText(s, x * d, (R2 + 12) * d, p);
            x += w + 7;
        }
    }

    /** Row 3: three equal tiles; returns their left edges (dp). */
    private float[] tiles(Canvas c) {
        float gap = 8, w = (W - 2 * PAD_X - 2 * gap) / 3f;
        Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
        tile.setColor(0x0FFFFFFF);
        float[] xs = new float[3];
        for (int i = 0; i < 3; i++) {
            xs[i] = PAD_X + i * (w + gap);
            c.drawRoundRect(new RectF(xs[i] * d, R3 * d, (xs[i] + w) * d, (R3 + ROW3_H) * d), 10 * d, 10 * d, tile);
        }
        return xs;
    }

    private float tileW() {
        return (W - 2 * PAD_X - 16) / 3f;
    }

    private void tileLabel(Canvas c, float x, String s) {
        TextPaint p = paint(regular, 10, 0xBFFFFFFF);
        p.setLetterSpacing(0.1f);
        text(c, s.toUpperCase(locale), x + 8, R3 + 14, p, tileW() - 16);
    }

    /** Row 4: a coloured lead and the rest of the line. */
    private void footer(Canvas c, String lead, int colour, String rest) {
        float x = PAD_X;
        if (lead != null && !lead.isEmpty()) x += text(c, lead, x, R4 + 12, paint(medium, 12, colour), 140) + 8;
        text(c, rest, x, R4 + 12, paint(regular, 12, 0xEBFFFFFF), W - PAD_X - x);
    }

    private String time(Instant i) {
        return DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", locale).format(i.atZone(zone));
    }

    /** "2 д 3 ч", "4 ч 12 мин", "18 мин", like the launcher's own countdown. */
    String until(Instant from, Instant to) {
        long m = Math.max(0, Duration.between(from, to).toMinutes());
        long dd = m / 1440, h = m % 1440 / 60, mm = m % 60;
        if (dd > 0) return dd + t(" д ", "d ") + h + t(" ч", "h");
        if (h > 0) return h + t(" ч ", "h ") + mm + t(" мин", "m");
        return mm + t(" мин", "m");
    }

    static String shortSession(F1.Kind k, boolean ru) {
        switch (k) {
            case FP1: return ru ? "П1" : "FP1";
            case FP2: return ru ? "П2" : "FP2";
            case FP3: return ru ? "П3" : "FP3";
            case SPRINT_QUALI: return ru ? "СК" : "SQ";
            case SPRINT: return ru ? "СПРИНТ" : "SPRINT";
            case QUALI: return ru ? "КВАЛ." : "QUALI";
            default: return ru ? "ГОНКА" : "RACE";
        }
    }

    private String sessionLabel(F1.Session s) {
        return shortSession(s.kind, ru) + " " + weekday(s.start) + " " + time(s.start);
    }

    /** The detail row's width in dp for these sessions, as detail() draws them. */
    private float sessionsWidth(List<F1.Session> list) {
        TextPaint p = paint(regular, 10.5f, WHITE);
        p.setLetterSpacing(0.01f);
        float w = 0;
        for (F1.Session s : list) w += p.measureText(sessionLabel(s)) / d + 7;
        return w - 7;
    }

    private String weekday(Instant i) {
        return DateTimeFormatter.ofPattern("EEE", locale).format(i.atZone(zone)).replace(".", "").toUpperCase(locale);
    }

    private String date(LocalDate day) {
        return DateTimeFormatter.ofPattern(ru ? "EEE, d MMM" : "EEE d MMM", locale).format(day).replace(".", "");
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---- the F1 card

    /** Null when F1 is off or there is no weekend to show (no calendar yet, the season is over). */
    Bitmap left(Config cfg, F1.Season season, F1.Table drivers, List<News.Item> news, Instant now) {
        if (!cfg.f1 || season == null) return null;
        Object[] up = F1.upcoming(season, now);
        if (up == null) return null;
        F1.Race race = (F1.Race) up[0];
        F1.Session next = (F1.Session) up[1];
        Bitmap b = card();
        Canvas c = new Canvas(b);
        frame(c);
        kicker(c, Turn.F1_RED, t("Этап ", "Round ") + race.round + " · " + Names.grandPrix(race, ru));

        boolean live = !now.isBefore(next.start);
        String word = next.kind == F1.Kind.RACE ? t("Старт гонки", "Lights out") : Turns.session(next.kind, ru);
        String main = live ? "● " + Turns.session(next.kind, ru) + t(" · идёт сейчас", " · live now")
                : word + t(" через ", " in ") + until(now, next.start);
        text(c, main, PAD_X, R1 + 26, paint(light, 25, live ? RED : WHITE), W - 2 * PAD_X);

        // The weekend's sessions; when they do not all fit, past ones go first, so the next one and the race stay.
        List<F1.Session> shown = new ArrayList<>(race.sessions);
        while (shown.size() > 2 && sessionsWidth(shown) > W - 2 * PAD_X && !now.isBefore(shown.get(0).end()) && shown.get(0) != next) {
            shown.remove(0);
        }
        while (shown.size() > 2 && sessionsWidth(shown) > W - 2 * PAD_X) {
            int drop = -1;
            for (int i = 0; i < shown.size() - 1; i++) if (shown.get(i) != next) { drop = i; break; }
            if (drop < 0) break;
            shown.remove(drop);
        }
        List<String> pieces = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (F1.Session s : shown) {
            pieces.add(sessionLabel(s));
            colours.add(s == next ? RED : !now.isBefore(s.end()) ? 0x73FFFFFF : WHITE);
        }
        detail(c, pieces, colours);

        float[] xs = tiles(c);
        TextPaint big = paint(regular, 14, WHITE);
        TextPaint small = paint(regular, 11, 0xA6FFFFFF);
        boolean standings = !cfg.noSpoilers && drivers != null && drivers.rows.size() >= 3;
        for (int i = 0; i < 3; i++) {
            if (standings) {
                F1.Standing st = drivers.rows.get(i);
                String name = Names.driverShort(st.id, st.given, st.family, ru).replaceFirst("^.+?\\. ", "");
                tileLabel(c, xs[i], st.position + " · " + name);
                float w = text(c, num(st.points), xs[i] + 8, R3 + 38, big, tileW() - 16);
                text(c, t("очк.", "pts"), xs[i] + 8 + w + 4, R3 + 38, small, tileW() - 20 - w);
            } else {
                // Spoilers off: the next three sessions instead of the table.
                List<F1.Session> coming = new ArrayList<>();
                for (F1.Session s : race.sessions) if (now.isBefore(s.end())) coming.add(s);
                if (i >= coming.size()) continue;
                F1.Session s = coming.get(i);
                tileLabel(c, xs[i], shortSession(s.kind, ru) + " · " + weekday(s.start));
                text(c, time(s.start), xs[i] + 8, R3 + 38, big, tileW() - 16);
            }
        }

        News.Item n = cfg.news && !cfg.noSpoilers && news != null && !news.isEmpty() ? news.get(0) : null;
        if (n != null && News.fresh(n, now, 3 * 86_400_000L)) {
            Instant at = Instant.ofEpochMilli(n.timeMs);
            boolean today = at.atZone(zone).toLocalDate().equals(now.atZone(zone).toLocalDate());
            String when = today ? time(at) : DateTimeFormatter.ofPattern("d MMM", locale).format(at.atZone(zone)).replace(".", "");
            footer(c, "F1 · " + when, RED, n.title);
        } else {
            F1.Race after = null;
            for (F1.Race r : season.races) if (r.round > race.round && (after == null || r.round < after.round)) after = r;
            if (after != null) {
                footer(c, t("Далее", "Next"), RED, Names.grandPrix(after, ru) + " · "
                        + date(after.race().start.atZone(zone).toLocalDate()));
            }
        }
        return b;
    }

    // ---- the weather and holidays card

    /** Weather (now and three days), or holidays alone when the weather is off; null when both are off. */
    Bitmap right(Config cfg, Weather.Forecast f, LocalDate today, Instant now) {
        boolean weather = cfg.weather && cfg.city != null && f != null;
        if (!weather && !cfg.holidays) return null;
        Bitmap b = card();
        Canvas c = new Canvas(b);
        frame(c);
        if (weather) weather(c, cfg, f, today, now); else holidays(c, cfg, today);
        return b;
    }

    private void weather(Canvas c, Config cfg, Weather.Forecast f, LocalDate today, Instant now) {
        kicker(c, BLUE, cfg.city.name + t(" · сейчас", " · now"));
        WeatherIcon.draw(c, f.nowKind, f.day, PAD_X * d, (R1 + 1) * d, 32 * d, WHITE);
        float x = PAD_X + 42;
        x += text(c, Weather.degrees(f.temp), x, R1 + 27, paint(light, 28, WHITE), 90) + 10;
        text(c, Sky.words(f.nowKind, ru), x, R1 + 24, paint(medium, 15, WHITE), W - PAD_X - x);

        List<String> pieces = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (!Double.isNaN(f.feels)) { pieces.add(t("Ощущается ", "Feels like ") + Weather.degrees(f.feels)); colours.add(0xE6FFFFFF); }
        if (!Double.isNaN(f.wind)) { pieces.add(t("ветер ", "wind ") + Math.round(f.wind) + t(" м/с", " m/s")); colours.add(0xE6FFFFFF); }
        Object[] sun = Weather.nextSun(f, now);
        if (sun != null) {
            pieces.add(((Boolean) sun[0] ? t("восход ", "sunrise ") : t("закат ", "sunset ")) + time((Instant) sun[1]));
            colours.add(0xE6FFFFFF);
        }
        detail(c, pieces, colours);

        float[] xs = tiles(c);
        Weather.Period[][] days = Weather.nextDays(f, today, 3);
        TextPaint dayT = paint(regular, 14, WHITE);
        TextPaint nightT = paint(regular, 11, 0xA6FFFFFF);
        for (int i = 0; i < 3; i++) {
            tileLabel(c, xs[i], date(today.plusDays(i + 1)));
            float x0 = xs[i] + 7;
            Weather.Period day = days[i][0], night = days[i][1];
            if (day != null) {
                WeatherIcon.draw(c, day.kind, true, x0 * d, (R3 + 20) * d, 22 * d, WHITE);
                x0 += 24 + text(c, Weather.degrees(day.temp), x0 + 24, R3 + 37, dayT, 40) + 6;
            }
            if (night != null) {
                WeatherIcon.draw(c, night.kind, false, x0 * d, (R3 + 22) * d, 18 * d, 0xCCFFFFFF);
                text(c, Weather.degrees(night.temp), x0 + 20, R3 + 37, nightT, xs[i] + tileW() - x0 - 22);
            }
        }

        if (cfg.holidays) {
            holidayFooter(c, cfg, today);
        } else {
            Weather.Day td = f.days.isEmpty() ? null : f.days.get(0);
            if (td != null && td.sunrise != null && td.sunset != null) {
                footer(c, t("Сегодня", "Today"), GOLD, t("восход ", "sunrise ") + time(td.sunrise) + " · " + t("закат ", "sunset ") + time(td.sunset));
            }
        }
    }

    private void holidayFooter(Canvas c, Config cfg, LocalDate today) {
        List<Holidays.Holiday> todays = Holidays.on(today, cfg.funHolidays);
        Object[] next = Holidays.nextMajor(today);
        StringBuilder rest = new StringBuilder();
        for (int i = 0; i < Math.min(2, todays.size()); i++) {
            if (i > 0) rest.append(" · ");
            rest.append(todays.get(i).name(ru));
        }
        if (next != null) {
            if (rest.length() > 0) rest.append(" · ");
            rest.append(((Holidays.Holiday) next[1]).name(ru)).append(t(" через ", " in "))
                    .append(((LocalDate) next[0]).toEpochDay() - today.toEpochDay()).append(t(" дн.", " d"));
        }
        footer(c, todays.isEmpty() ? t("Скоро", "Soon") : t("Сегодня", "Today"), GOLD, rest.toString());
    }

    /** The right card with the weather off: today's holiday as the main line, the next three in the tiles. */
    private void holidays(Canvas c, Config cfg, LocalDate today) {
        kicker(c, GOLD, t("Праздники · ", "Holidays · ") + date(today));
        List<Holidays.Holiday> todays = Holidays.on(today, cfg.funHolidays);
        String main = todays.isEmpty() ? t("Сегодня обычный день", "An ordinary day") : todays.get(0).name(ru);
        text(c, main, PAD_X, R1 + 26, paint(light, 25, WHITE), W - 2 * PAD_X);
        List<String> pieces = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (int i = 1; i < todays.size(); i++) { pieces.add(todays.get(i).name(ru)); colours.add(0xE6FFFFFF); }
        detail(c, pieces, colours);
        float[] xs = tiles(c);
        List<Object[]> soon = Holidays.upcoming(today, cfg.funHolidays, 3);
        TextPaint name = paint(regular, 12, WHITE);
        for (int i = 0; i < Math.min(3, soon.size()); i++) {
            tileLabel(c, xs[i], date((LocalDate) soon.get(i)[0]));
            text(c, ((Holidays.Holiday) soon.get(i)[1]).name(ru), xs[i] + 8, R3 + 37, name, tileW() - 16);
        }
        Object[] next = Holidays.nextMajor(today);
        if (next != null) {
            footer(c, t("Скоро", "Soon"), GOLD, ((Holidays.Holiday) next[1]).name(ru) + t(" через ", " in ")
                    + (((LocalDate) next[0]).toEpochDay() - today.toEpochDay()) + t(" дн.", " d"));
        }
    }
}
