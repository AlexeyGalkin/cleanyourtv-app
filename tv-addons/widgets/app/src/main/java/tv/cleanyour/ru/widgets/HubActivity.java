package tv.cleanyour.ru.widgets;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The card's screen: the F1 weekend, both championships, the last race, the weather for the chosen city and today's
 * holidays, each a tab along the top. Left and right move between tabs, down scrolls, Back leaves. Built in code to
 * stay small; every screen reads only the saved copies, and opening it fetches whatever is due.
 */
public final class HubActivity extends Activity {
    private static final int BG = 0xFF0E1116;
    private static final int PANEL = 0xFF181C24;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0x99FFFFFF;
    private static final int FAINT = 0x59FFFFFF;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<TextView> tabs = new ArrayList<>();
    private Repo repo;
    private Config cfg;
    private boolean ru;
    private ZoneId zone;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView status;
    private int tab;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            show(tab);
            main.postDelayed(this, 30_000);
        }
    };

    private String t(String ruText, String enText) {
        return ru ? ruText : enText;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        repo = new Repo(this);
        tab = saved == null ? 0 : saved.getInt("tab", 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = repo.config();
        ru = repo.russian(cfg);
        zone = ZoneId.systemDefault();
        build();
        show(tab);
        main.postDelayed(tick, 30_000);
        Background.refresh(this, true, () -> main.post(() -> show(tab)));
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(48), dp(27), dp(48), dp(20));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(t("Виджеты", "Widgets"), 30, TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        status = text("", 16, DIM, false);
        head.addView(status);
        root.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(14), 0, dp(14));
        String[] names = {t("F1: уик-энд", "F1 weekend"), t("Пилоты", "Drivers"), t("Конструкторы", "Constructors"),
                t("Результаты", "Results"), t("Новости", "News"), t("Погода", "Weather"), t("Праздники", "Holidays")};
        tabs.clear();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView tv = text(names[i], 18, DIM, false);
            tv.setPadding(dp(16), dp(8), dp(16), dp(8));
            tv.setFocusable(true);
            tv.setOnFocusChangeListener((v, has) -> {
                if (has && tab != index) show(index);
                styleTabs();
            });
            tv.setOnClickListener(v -> show(index));
            tabs.add(tv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(tv, lp);
        }
        root.addView(row);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView credit = text(t("Данные: Jolpica-F1, Open-Meteo. Строки под часами включаются в приложении на телефоне.",
                "Data: Jolpica-F1, Open-Meteo. The lines under the clock are switched on in the phone app."), 13, FAINT, false);
        credit.setPadding(0, dp(8), 0, 0);
        root.addView(credit);
        setContentView(root);
        tabs.get(tab).requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Up from the list goes back to the tabs, wherever the list is scrolled.
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && getCurrentFocus() != null && !tabs.contains(getCurrentFocus())
                && scroll.getScrollY() == 0) {
            tabs.get(tab).requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void styleTabs() {
        for (int i = 0; i < tabs.size(); i++) {
            TextView tv = tabs.get(i);
            boolean on = i == tab;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(20));
            g.setColor(tv.isFocused() ? 0xFFFFFFFF : on ? 0x33FFFFFF : 0x00000000);
            tv.setBackground(g);
            tv.setTextColor(tv.isFocused() ? 0xFF000000 : on ? TEXT : DIM);
        }
    }

    private void show(int index) {
        tab = index;
        styleTabs();
        content.removeAllViews();
        switch (index) {
            case 0: weekend(); break;
            case 1: if (gate()) drivers(); break;
            case 2: if (gate()) constructors(); break;
            case 3: if (gate()) results(); break;
            case 4: if (gate()) news(); break;
            case 5: weather(); break;
            default: holidays(); break;
        }
    }

    // ---- building blocks

    private TextView text(String s, float sp, int colour, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(colour);
        if (bold) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setSingleLine(false);
        return tv;
    }

    /** A focusable row (so the D-pad scrolls the list) with cells of the given weights. */
    private LinearLayout row(int colour) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(10), dp(16), dp(10));
        r.setFocusable(true);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(PANEL);
        normal.setCornerRadius(dp(10));
        r.setBackground(normal);
        r.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(10));
            g.setColor(has ? 0xFF2A3140 : PANEL);
            if (has) g.setStroke(dp(2), 0xCCFFFFFF);
            v.setBackground(g);
        });
        if (colour != 0) {
            View chip = new View(this);
            chip.setBackgroundColor(colour);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(28));
            lp.rightMargin = dp(14);
            r.addView(chip, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        content.addView(r, lp);
        return r;
    }

    private TextView cell(LinearLayout r, String s, float weight, float sp, int colour, int gravity) {
        TextView tv = text(s, sp, colour, false);
        tv.setGravity(gravity);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(tv, new LinearLayout.LayoutParams(weight == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return tv;
    }

    private void heading(String s, String sub) {
        content.addView(text(s, 28, TEXT, true));
        if (sub != null && !sub.isEmpty()) {
            TextView tv = text(sub, 18, DIM, false);
            tv.setPadding(0, dp(2), 0, dp(14));
            content.addView(tv);
        } else {
            content.addView(new FrameLayout(this), new LinearLayout.LayoutParams(1, dp(12)));
        }
    }

    private void empty(String s) {
        LinearLayout r = row(0);
        cell(r, s, 1, 20, DIM, Gravity.START);
    }

    private void updated(String file) {
        long at = repo.savedAt(file);
        status.setText(at == 0 ? "" : t("Обновлено: ", "Updated: ") + time(Instant.ofEpochMilli(at)));
    }

    private Locale locale() {
        return ru ? new Locale("ru") : Locale.ENGLISH;
    }

    private String time(Instant i) {
        String p = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
        return DateTimeFormatter.ofPattern(p, locale()).format(i.atZone(zone));
    }

    private String day(Instant i) {
        return DateTimeFormatter.ofPattern(ru ? "EEEE, d MMMM" : "EEEE d MMMM", locale()).format(i.atZone(zone));
    }

    private String dayOf(LocalDate d) {
        return DateTimeFormatter.ofPattern(ru ? "EEEE, d MMMM" : "EEEE d MMMM", locale()).format(d);
    }

    /** "2 д 3 ч", "4 ч 12 мин", "18 мин". */
    private String until(Instant from, Instant to) {
        long m = Math.max(0, Duration.between(from, to).toMinutes());
        long d = m / 1440, h = m % 1440 / 60, mm = m % 60;
        if (d > 0) return d + t(" д ", "d ") + h + t(" ч", "h");
        if (h > 0) return h + t(" ч ", "h ") + mm + t(" мин", "m");
        return mm + t(" мин", "m");
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---- tabs

    private void weekend() {
        updated(Repo.F1_SEASON);
        F1.Season s = repo.season();
        Instant now = Instant.now();
        F1.Race r = s == null ? null : s.current(now);
        if (r == null) {
            heading(t("Формула 1", "Formula 1"), null);
            empty(s == null ? t("Загружаем календарь… Нужен интернет на телевизоре.", "Loading the calendar… The TV needs the internet.")
                    : t("Сезон " + s.year + " завершён. Календарь следующего появится, когда его опубликуют.",
                    "The " + s.year + " season is over. Next season's calendar appears once it is published."));
            return;
        }
        String sub = Names.city(r, ru) + (r.sprint() ? t(" · спринт-уик-энд", " · sprint weekend") : "");
        heading(t("Этап ", "Round ") + r.round + " · " + Names.grandPrix(r, ru), sub);

        F1.Session next = F1.next(r, now);
        if (next != null) {
            boolean live = !now.isBefore(next.start);
            TextView big = text(live ? "● " + Turns.session(next.kind, ru) + t(" — идёт сейчас", " — live now")
                    : Turns.session(next.kind, ru) + t(" через ", " in ") + until(now, next.start), 26,
                    live ? 0xFFFF4D4D : TEXT, true);
            big.setPadding(0, 0, 0, dp(14));
            content.addView(big);
        }

        JSONObject hourly = null;
        String w = repo.read("f1-weather-" + r.round + ".json");
        if (w == null) {
            Background.execute(() -> {
                if (repo.circuitWeather(r) != null) main.post(() -> {
                    if (tab == 0) show(0);
                });
            });
        } else {
            try {
                hourly = new JSONObject(w).optJSONObject("hourly");
            } catch (JSONException ignored) {
                // no weather column
            }
        }
        for (F1.Session ss : r.sessions) {
            boolean done = !now.isBefore(ss.end());
            boolean isNext = ss == next;
            LinearLayout row = row(isNext ? Turn.F1_RED : 0);
            int c = done ? FAINT : TEXT;
            cell(row, Turns.session(ss.kind, ru), 3, 22, c, Gravity.START);
            cell(row, day(ss.start), 4, 20, done ? FAINT : DIM, Gravity.START);
            cell(row, time(ss.start), 2, 22, c, Gravity.END);
            String wx = sessionWeather(hourly, ss.start);
            cell(row, wx, 3, 20, done ? FAINT : DIM, Gravity.END);
        }
    }

    private String sessionWeather(JSONObject hourly, Instant at) {
        if (hourly == null) return "";
        try {
            JSONArray times = hourly.getJSONArray("time");
            String key = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00").format(at.atZone(ZoneId.of("UTC")));
            for (int i = 0; i < times.length(); i++) {
                if (key.equals(times.getString(i))) {
                    double temp = hourly.getJSONArray("temperature_2m").optDouble(i, Double.NaN);
                    int code = hourly.getJSONArray("weather_code").optInt(i, 0);
                    return Weather.icon(code, true) + " " + Weather.degrees(temp);
                }
            }
        } catch (JSONException ignored) {
            // no weather for that hour
        }
        return "";
    }

    private void drivers() {
        updated(Repo.F1_DRIVERS);
        F1.Table d = repo.drivers();
        F1.Season s = repo.season();
        heading(t("Личный зачёт", "Drivers' championship"),
                d == null || d.rows.isEmpty() ? null : t("После этапа ", "After round ") + d.round + " · " + d.season);
        if (d == null || d.rows.isEmpty()) {
            empty(t("Загружаем таблицу…", "Loading the standings…"));
            return;
        }
        boolean champion = s != null && F1.clinched(d, s.remaining(Instant.now()));
        double leader = d.rows.get(0).points;
        for (F1.Standing st : d.rows) {
            LinearLayout r = row(Names.teamColour(st.teamId));
            cell(r, String.valueOf(st.position), 0.6f, 22, TEXT, Gravity.START);
            String name = Names.driverShort(st.id, st.given, st.family, ru);
            if (champion && st.position == 1) name = name + t("  ★ Чемпион", "  ★ Champion");
            cell(r, name, 4, 22, TEXT, Gravity.START);
            cell(r, Names.team(st.teamId, st.teamName, ru), 3, 18, DIM, Gravity.START);
            cell(r, st.wins > 0 ? t("побед: ", "wins: ") + st.wins : "", 1.5f, 18, DIM, Gravity.END);
            cell(r, st.position == 1 ? "" : "−" + num(leader - st.points), 1.2f, 18, DIM, Gravity.END);
            cell(r, num(st.points), 1.2f, 22, TEXT, Gravity.END);
        }
    }

    private void constructors() {
        updated(Repo.F1_TEAMS);
        F1.Table d = repo.teams();
        heading(t("Кубок конструкторов", "Constructors' championship"),
                d == null || d.rows.isEmpty() ? null : t("После этапа ", "After round ") + d.round + " · " + d.season);
        if (d == null || d.rows.isEmpty()) {
            empty(t("Загружаем таблицу…", "Loading the standings…"));
            return;
        }
        double leader = d.rows.get(0).points;
        for (F1.Standing st : d.rows) {
            LinearLayout r = row(Names.teamColour(st.id));
            cell(r, String.valueOf(st.position), 0.6f, 22, TEXT, Gravity.START);
            cell(r, Names.team(st.id, st.teamName, ru), 5, 22, TEXT, Gravity.START);
            cell(r, st.wins > 0 ? t("побед: ", "wins: ") + st.wins : "", 1.5f, 18, DIM, Gravity.END);
            cell(r, st.position == 1 ? "" : "−" + num(leader - st.points), 1.2f, 18, DIM, Gravity.END);
            cell(r, num(st.points), 1.2f, 22, TEXT, Gravity.END);
        }
    }

    private void results() {
        updated(Repo.F1_LAST);
        F1.LastRace last = repo.last();
        F1.Season s = repo.season();
        if (last == null || last.results.isEmpty()) {
            heading(t("Результаты", "Results"), null);
            empty(t("Загружаем результаты…", "Loading the results…"));
            return;
        }
        String gp = last.name;
        if (s != null) for (F1.Race r : s.races) if (r.round == last.round) gp = Names.grandPrix(r, ru);
        heading(t("Этап ", "Round ") + last.round + " · " + gp,
                last.fastestLap.isEmpty() ? null : t("Быстрый круг: ", "Fastest lap: ") + last.fastestLap);
        int shown = 0;
        for (F1.Result res : last.results) {
            if (shown++ >= 20) break;
            LinearLayout r = row(Names.teamColour(res.teamId));
            cell(r, res.position, 0.6f, 22, TEXT, Gravity.START);
            cell(r, Names.driverShort(res.driverId, res.given, res.family, ru) + (res.fastest ? "  ⏱" : ""), 4, 22, TEXT, Gravity.START);
            cell(r, Names.team(res.teamId, res.teamName, ru), 3, 18, DIM, Gravity.START);
            cell(r, res.time, 2.4f, 18, DIM, Gravity.END);
            cell(r, res.points > 0 ? "+" + num(res.points) : "", 1, 20, TEXT, Gravity.END);
        }
    }

    /** No spoilers: standings, results and headlines wait behind one OK for this visit. */
    private boolean revealed;

    private boolean gate() {
        if (!cfg.noSpoilers || revealed) return true;
        heading(t("Без спойлеров", "No spoilers"), t("Таблицы, результаты и новости скрыты", "Standings, results and news are hidden"));
        LinearLayout r = row(Turn.F1_RED);
        cell(r, t("Нажмите ОК, чтобы показать", "Press OK to show them"), 1, 22, TEXT, Gravity.START);
        r.setOnClickListener(v -> {
            revealed = true;
            show(tab);
        });
        return false;
    }

    private void news() {
        updated(Repo.NEWS);
        heading(t("Новости F1", "F1 news"), t("f1news.ru и Чемпионат", "f1news.ru and Championat (in Russian)"));
        List<News.Item> items = repo.news();
        if (items.isEmpty()) {
            empty(t("Загружаем новости…", "Loading the news…"));
            return;
        }
        for (News.Item n : items) {
            LinearLayout r = row(0);
            r.setGravity(Gravity.TOP);
            Instant at = Instant.ofEpochMilli(n.timeMs);
            String when = LocalDate.now(zone).equals(at.atZone(zone).toLocalDate()) ? time(at)
                    : DateTimeFormatter.ofPattern(ru ? "d MMM, HH:mm" : "d MMM, HH:mm", locale()).format(at.atZone(zone));
            TextView left = cell(r, when + "\n" + n.source, 1.3f, 15, DIM, Gravity.START);
            left.setSingleLine(false);
            left.setMaxLines(2);
            TextView title = cell(r, n.title, 5, 20, TEXT, Gravity.START);
            title.setSingleLine(false);
            title.setMaxLines(2);
        }
    }

    private ImageView icon(String kind, boolean day, int sizeDp) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(WeatherIcon.bitmap(kind, day, dp(sizeDp), TEXT));
        return v;
    }

    private void weather() {
        updated(Repo.WEATHER);
        if (cfg.city == null) {
            heading(t("Погода", "Weather"), null);
            empty(t("Город не выбран. Выберите его на телефоне: Clean Your TV RU → Под себя → Виджеты → Погода.",
                    "No city yet. Pick it on the phone: Clean Your TV RU → Make it yours → Widgets → Weather."));
            return;
        }
        Weather.Forecast f = repo.forecast();
        heading(cfg.place(), null);
        if (f == null) {
            empty(t("Загружаем прогноз…", "Loading the forecast…"));
            return;
        }
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.addView(icon(f.nowKind, f.day, 72), new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView temp = text(Weather.degrees(f.temp), 54, TEXT, false);
        temp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        temp.setPadding(dp(16), 0, dp(16), 0);
        now.addView(temp);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(Sky.words(f.nowKind, ru), 24, TEXT, true));
        StringBuilder b = new StringBuilder();
        b.append(t("Ощущается как ", "Feels like ")).append(Weather.degrees(f.feels));
        if (!Double.isNaN(f.wind)) b.append(t(" · ветер ", " · wind ")).append(Math.round(f.wind)).append(t(" м/с", " m/s"));
        if (f.humidity >= 0) b.append(t(" · влажность ", " · humidity ")).append(f.humidity).append('%');
        words.addView(text(b.toString(), 18, DIM, false));
        now.addView(words);
        content.addView(now);
        Instant nowI = Instant.now();
        Object[] sun = Weather.nextSun(f, nowI);
        if (sun != null) {
            Instant at = (Instant) sun[1];
            TextView sunLine = text(((Boolean) sun[0] ? t("Рассвет в ", "Sunrise at ") : t("Закат в ", "Sunset at "))
                    + time(at) + t(", через ", ", in ") + until(nowI, at), 20, 0xFFFFD27A, false);
            sunLine.setPadding(0, dp(10), 0, dp(14));
            content.addView(sunLine);
        }
        LocalDate today = LocalDate.now(f.zone);
        Weather.Period[][] days = Weather.nextDays(f, today, 3);
        for (int i = 0; i < 3; i++) {
            LinearLayout r = row(0);
            cell(r, dayOf(today.plusDays(i + 1)), 3, 20, TEXT, Gravity.START);
            for (int k = 0; k < 2; k++) {
                Weather.Period p = days[i][k];
                if (p == null) { cell(r, "", 4, 18, DIM, Gravity.START); continue; }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
                lp.rightMargin = dp(8);
                r.addView(icon(p.kind, k == 0, 32), lp);
                cell(r, (k == 0 ? t("днём ", "day ") : t("ночью ", "night ")) + Weather.degrees(p.temp) + " · "
                        + Sky.wordsInline(p.kind, ru), 4, 18, k == 0 ? TEXT : DIM, Gravity.START);
            }
        }
        for (Weather.Day dd : f.days) {
            if (!dd.date.isAfter(today.plusDays(3))) continue;
            LinearLayout r = row(0);
            cell(r, dayOf(dd.date), 3, 18, DIM, Gravity.START);
            cell(r, Weather.degrees(dd.max) + " / " + Weather.degrees(dd.min), 2, 18, TEXT, Gravity.START);
            cell(r, dd.rainChance > 0 ? "☂ " + dd.rainChance + "%" : "", 2, 16, DIM, Gravity.END);
        }
    }

    private void holidays() {
        status.setText("");
        LocalDate today = LocalDate.now(zone);
        List<Holidays.Holiday> list = Holidays.on(today, cfg.funHolidays);
        heading(t("Сегодня, ", "Today, ") + dayOf(today), null);
        if (list.isEmpty()) {
            empty(t("Сегодня праздников нет — обычный хороший день.", "No holiday today, just a good ordinary day."));
        }
        for (Holidays.Holiday h : list) {
            LinearLayout r = row(h.kind == Holidays.MAJOR ? Turn.HOLIDAY_GOLD : 0);
            cell(r, h.name(ru), 1, 24, TEXT, Gravity.START);
        }
        Object[] next = Holidays.nextMajor(today);
        if (next != null) {
            Instant at = ((LocalDate) next[0]).atStartOfDay(zone).toInstant();
            TextView tv = text(((Holidays.Holiday) next[1]).name(ru) + t(" через ", " in ") + until(Instant.now(), at), 22, 0xFFFFC145, true);
            tv.setPadding(0, dp(16), 0, dp(10));
            content.addView(tv);
        }
        TextView soon = text(t("Скоро", "Coming up"), 20, DIM, true);
        soon.setPadding(0, dp(6), 0, dp(8));
        content.addView(soon);
        for (Object[] o : Holidays.upcoming(today, cfg.funHolidays, 8)) {
            LinearLayout r = row(((Holidays.Holiday) o[1]).kind == Holidays.MAJOR ? Turn.HOLIDAY_GOLD : 0);
            cell(r, dayOf((LocalDate) o[0]), 2, 20, DIM, Gravity.START);
            cell(r, ((Holidays.Holiday) o[1]).name(ru), 3, 20, TEXT, Gravity.START);
        }
    }
}
