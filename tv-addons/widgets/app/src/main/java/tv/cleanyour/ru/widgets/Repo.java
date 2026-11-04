package tv.cleanyour.ru.widgets;

import android.content.Context;
import android.content.res.Configuration;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * The saved copies (internal storage) and when each is due again. A fetch only happens when the launcher asks for the
 * lines or a screen is open, and only once a copy is old enough, so the TV is never woken and never polled.
 */
final class Repo {
    static final String CONFIG = "config.json";
    /** The last config.json that parsed, for a write that did not. */
    static final String GOOD = "config.good.json";
    static final String F1_SEASON = "f1-current.json";
    static final String F1_DRIVERS = "f1-drivers.json";
    static final String F1_TEAMS = "f1-constructors.json";
    static final String F1_LAST = "f1-last.json";
    static final String WEATHER = "weather.json";
    static final String NEWS = "news.json";

    private static final String JOLPICA = "https://api.jolpi.ca/ergast/f1/";
    private static final String MIRROR = "https://raw.githubusercontent.com/" + BuildConfig.FORK_REPO + "/f1-data/f1/";

    private static final long HOUR = 3_600_000L;

    private final Context ctx;

    Repo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    File file(String name) {
        return new File(ctx.getFilesDir(), name);
    }

    String read(String name) {
        File f = file(name);
        if (!f.isFile()) return null;
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Write through a temporary file, so a reader never sees half a file. */
    synchronized void write(String name, String text) throws IOException {
        File tmp = file(name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(file(name))) throw new IOException("rename " + name);
    }

    long age(String name, long now) {
        File f = file(name);
        return f.isFile() ? now - f.lastModified() : Long.MAX_VALUE;
    }

    long savedAt(String name) {
        File f = file(name);
        return f.isFile() ? f.lastModified() : 0;
    }

    Config config() {
        for (String name : new String[] {CONFIG, GOOD}) {
            String text = read(name);
            if (text == null) continue;
            if (text.startsWith("﻿")) text = text.substring(1);
            try {
                return Config.parse(text);
            } catch (JSONException ignored) {
                // a write still checking, or a bad one: the last good copy
            }
        }
        return Config.defaults();
    }

    /** The language: the phone's choice, else the TV's. */
    boolean russian(Config cfg) {
        if (cfg.lang.equals("ru")) return true;
        if (cfg.lang.equals("en")) return false;
        return Locale.getDefault().getLanguage().equals("ru");
    }

    /** A context whose resources speak the configured language. */
    Context localized(Context base, Config cfg) {
        if (cfg.lang.isEmpty()) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(new Locale(cfg.lang));
        return base.createConfigurationContext(c);
    }

    F1.Season season() {
        String s = read(F1_SEASON);
        try {
            return s == null ? null : F1.season(s);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    F1.Table drivers() {
        String s = read(F1_DRIVERS);
        try {
            return s == null ? null : F1.drivers(s);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    F1.Table teams() {
        String s = read(F1_TEAMS);
        try {
            return s == null ? null : F1.constructors(s);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    F1.LastRace last() {
        String s = read(F1_LAST);
        try {
            return s == null ? null : F1.lastRace(s);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    Weather.Forecast forecast() {
        String s = read(WEATHER);
        try {
            return s == null ? null : Weather.parse(s);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    List<News.Item> news() {
        return News.fromJson(read(NEWS));
    }

    List<Turn> turns(long nowMs) {
        Config cfg = config();
        return Turns.all(cfg, cfg.f1 ? season() : null, cfg.weather ? forecast() : null, Instant.ofEpochMilli(nowMs),
                ZoneId.systemDefault(), russian(cfg));
    }

    /** Whether a race weekend is on (standings change): from a day before the first session to a day after the race. */
    private boolean raceWeekend(long now) {
        F1.Season s = season();
        if (s == null) return false;
        Instant t = Instant.ofEpochMilli(now);
        F1.Race r = s.current(t);
        if (r == null) return false;
        return t.isAfter(r.sessions.get(0).start.minusSeconds(24 * 3600)) && t.isBefore(r.race().start.plusSeconds(24 * 3600));
    }

    /**
     * Fetches whatever is due; true when anything new arrived. {@code all} also fetches what only the screens show
     * (standings and results), which the lines under the clock do not need.
     */
    boolean refresh(long now, boolean all) {
        Config cfg = config();
        boolean changed = false;
        boolean weekend = raceWeekend(now);
        if (cfg.f1 || all) {
            changed |= fetch(F1_SEASON, now, 3 * HOUR, "current.json", "current.json");
        }
        if (all) {
            long every = weekend ? HOUR / 2 : 6 * HOUR;
            changed |= fetch(F1_DRIVERS, now, every, "current/driverstandings.json", "driverstandings.json");
            changed |= fetch(F1_TEAMS, now, every, "current/constructorstandings.json", "constructorstandings.json");
            changed |= fetch(F1_LAST, now, every, "current/last/results.json", "last-results.json");
        }
        if ((cfg.f1 && cfg.news || all) && age(NEWS, now) > HOUR / 3) {
            List<News.Item> a = new java.util.ArrayList<>(), b = new java.util.ArrayList<>();
            try {
                a = News.parse(Net.get(News.F1NEWS), "f1news.ru", false);
            } catch (IOException | RuntimeException ignored) {
                // the other feed may still answer
            }
            try {
                b = News.parse(Net.get(News.CHAMPIONAT), "Чемпионат", true);
            } catch (IOException | RuntimeException ignored) {
                // keep what the first gave
            }
            List<News.Item> merged = News.merge(a, b);
            if (!merged.isEmpty()) {
                try {
                    write(NEWS, News.toJson(merged));
                    changed = true;
                } catch (IOException ignored) {
                    // next time
                }
            }
        }
        if (cfg.city != null && (cfg.weather || all) && age(WEATHER, now) > HOUR / 2) {
            try {
                String w = Net.get(Weather.url(cfg.city));
                Weather.parse(w);
                write(WEATHER, w);
                changed = true;
            } catch (IOException | JSONException | RuntimeException ignored) {
                // keep the saved forecast
            }
        }
        return changed;
    }

    private boolean fetch(String name, long now, long every, String jolpicaPath, String mirrorFile) {
        if (age(name, now) < every) return false;
        try {
            String text = Net.get(JOLPICA + jolpicaPath, MIRROR + mirrorFile);
            new org.json.JSONObject(text).getJSONObject("MRData"); // a page, not an error document
            write(name, text);
            return true;
        } catch (IOException | JSONException | RuntimeException e) {
            return false;
        }
    }

    /** Saves a forecast for an F1 circuit's weekend (hourly, UTC). */
    String circuitWeather(F1.Race r) {
        if (Double.isNaN(r.lat) || Double.isNaN(r.lon)) return null;
        String name = "f1-weather-" + r.round + ".json";
        long now = System.currentTimeMillis();
        if (age(name, now) < 3 * HOUR) return read(name);
        LocalDate from = r.sessions.get(0).start.atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate to = r.race().start.atZone(ZoneId.of("UTC")).toLocalDate();
        try {
            String w = Net.get(Weather.hourlyUrl(r.lat, r.lon, from, to));
            write(name, w);
            return w;
        } catch (IOException e) {
            return read(name);
        }
    }
}
