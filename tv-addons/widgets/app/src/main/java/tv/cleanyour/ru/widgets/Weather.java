package tv.cleanyour.ru.widgets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The forecast for one city from Open-Meteo (no key, CC BY 4.0), and the sunrise and sunset to count down to. */
final class Weather {
    private Weather() {
    }

    static String url(Config.City c) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,"
                + "cloud_cover,rain,showers,snowfall"
                + "&hourly=temperature_2m,weather_code,cloud_cover,rain,showers,snowfall,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&wind_speed_unit=ms&timezone=auto&forecast_days=7", c.latitude, c.longitude);
    }

    /** Hourly temperature and code at a circuit, for the F1 weekend's sessions (UTC times). */
    static String hourlyUrl(double lat, double lon, LocalDate from, LocalDate to) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&hourly=temperature_2m,weather_code&timezone=UTC&start_date=%s&end_date=%s", lat, lon, from, to);
    }

    static final class Day {
        final LocalDate date;
        final int code;
        final double max;
        final double min;
        final int rainChance; // -1 unknown
        final Instant sunrise;
        final Instant sunset;

        Day(LocalDate date, int code, double max, double min, int rainChance, Instant sunrise, Instant sunset) {
            this.date = date;
            this.code = code;
            this.max = max;
            this.min = min;
            this.rainChance = rainChance;
            this.sunrise = sunrise;
            this.sunset = sunset;
        }
    }

    /** Day (09:00 to 21:00) or night (21:00 to 09:00 the next morning), local time, with its look and temperature. */
    static final class Period {
        final LocalDate date;      // the day it belongs to (a night belongs to the evening it starts)
        final boolean day;
        final String kind;         // Sky kind
        final double temp;         // the day's highest, the night's lowest

        Period(LocalDate date, boolean day, String kind, double temp) {
            this.date = date;
            this.day = day;
            this.kind = kind;
            this.temp = temp;
        }
    }

    static final class Forecast {
        final ZoneId zone;
        final double temp;
        final double feels;
        final int humidity;
        final int code;
        final double wind;
        final boolean day;
        final List<Day> days;
        final String nowKind;      // Sky kind right now
        final List<Period> periods; // from the coming one on, day and night in turn

        Forecast(ZoneId zone, double temp, double feels, int humidity, int code, double wind, boolean day, List<Day> days) {
            this(zone, temp, feels, humidity, code, wind, day, days, Sky.CLOUDY, new ArrayList<>());
        }

        Forecast(ZoneId zone, double temp, double feels, int humidity, int code, double wind, boolean day, List<Day> days,
                 String nowKind, List<Period> periods) {
            this.nowKind = nowKind;
            this.periods = periods;
            this.zone = zone;
            this.temp = temp;
            this.feels = feels;
            this.humidity = humidity;
            this.code = code;
            this.wind = wind;
            this.day = day;
            this.days = days;
        }
    }

    static Forecast parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        ZoneId zone;
        try {
            zone = ZoneId.of(o.optString("timezone", "UTC"));
        } catch (RuntimeException e) {
            zone = ZoneId.of("UTC");
        }
        JSONObject cur = o.getJSONObject("current");
        JSONObject d = o.getJSONObject("daily");
        JSONArray time = d.getJSONArray("time");
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < time.length(); i++) {
            days.add(new Day(LocalDate.parse(time.getString(i)), d.getJSONArray("weather_code").optInt(i, 0),
                    d.getJSONArray("temperature_2m_max").optDouble(i, Double.NaN),
                    d.getJSONArray("temperature_2m_min").optDouble(i, Double.NaN),
                    d.has("precipitation_probability_max") ? d.getJSONArray("precipitation_probability_max").optInt(i, -1) : -1,
                    local(d.optJSONArray("sunrise"), i, zone), local(d.optJSONArray("sunset"), i, zone)));
        }
        String nowKind = Sky.now(cur.optDouble("rain", 0) + cur.optDouble("showers", 0), cur.optDouble("snowfall", 0),
                cur.optInt("weather_code", 0), cur.optDouble("cloud_cover", codeCloud(cur.optInt("weather_code", 0))),
                cur.optDouble("wind_speed_10m", 0));
        return new Forecast(zone, cur.optDouble("temperature_2m", Double.NaN), cur.optDouble("apparent_temperature", Double.NaN),
                cur.optInt("relative_humidity_2m", -1), cur.optInt("weather_code", 0), cur.optDouble("wind_speed_10m", Double.NaN),
                cur.optInt("is_day", 1) == 1, days, nowKind, periods(o.optJSONObject("hourly")));
    }

    /** Cloud cover a code implies, when the answer has no cloud_cover. */
    private static double codeCloud(int code) {
        return code == 0 ? 0 : code == 1 ? 30 : code == 2 ? 70 : 100;
    }

    /** The hours folded into day (09–21) and night (21–09) stretches; only whole ones. */
    static List<Period> periods(JSONObject h) throws JSONException {
        List<Period> out = new ArrayList<>();
        if (h == null) return out;
        JSONArray time = h.getJSONArray("time");
        int n = time.length();
        int i = 0;
        while (i < n) {
            LocalDateTime t = LocalDateTime.parse(time.getString(i));
            int hour = t.getHour();
            boolean day = hour >= 9 && hour < 21;
            boolean start = hour == 9 || hour == 21;
            if (!start) { i++; continue; }
            if (i + 12 > n) break;
            double rain = 0, snow = 0, cloud = 0, wind = 0, hi = -999, lo = 999;
            int worst = 0;
            for (int k = i; k < i + 12; k++) {
                rain += h.getJSONArray("rain").optDouble(k, 0) + (h.has("showers") ? h.getJSONArray("showers").optDouble(k, 0) : 0);
                snow += h.getJSONArray("snowfall").optDouble(k, 0);
                cloud += h.has("cloud_cover") ? h.getJSONArray("cloud_cover").optDouble(k, 0) : codeCloud(h.getJSONArray("weather_code").optInt(k, 0));
                wind = Math.max(wind, h.has("wind_speed_10m") ? h.getJSONArray("wind_speed_10m").optDouble(k, 0) : 0);
                worst = Math.max(worst, h.getJSONArray("weather_code").optInt(k, 0));
                double temp = h.getJSONArray("temperature_2m").optDouble(k, Double.NaN);
                if (!Double.isNaN(temp)) { hi = Math.max(hi, temp); lo = Math.min(lo, temp); }
            }
            out.add(new Period(t.toLocalDate(), day, Sky.kind(rain, snow, worst, cloud / 12, wind), day ? hi : lo));
            i += 12;
        }
        return out;
    }

    /** Tomorrow and the next two days: {day, night} for each (either may be null near the end of the answer). */
    static Period[][] nextDays(Forecast f, LocalDate today, int count) {
        Period[][] out = new Period[count][2];
        for (Period p : f.periods) {
            long d = p.date.toEpochDay() - today.toEpochDay() - 1;
            if (d < 0 || d >= count) continue;
            out[(int) d][p.day ? 0 : 1] = p;
        }
        return out;
    }

    private static Instant local(JSONArray a, int i, ZoneId zone) {
        if (a == null || a.isNull(i)) return null; // polar day or night
        try {
            return LocalDateTime.parse(a.getString(i)).atZone(zone).toInstant();
        } catch (RuntimeException | JSONException e) {
            return null;
        }
    }

    /** The next sunrise or sunset after {@code now}: {true for sunrise, instant}, or null when none is known. */
    static Object[] nextSun(Forecast f, Instant now) {
        for (Day d : f.days) {
            Instant first = d.sunrise, second = d.sunset;
            if (first != null && first.isAfter(now)) return new Object[] {true, first};
            if (second != null && second.isAfter(now)) return new Object[] {false, second};
        }
        return null;
    }

    /** "+3°", "−5°" (a real minus sign), "0°". */
    static String degrees(double t) {
        if (Double.isNaN(t)) return "—";
        long r = Math.round(t);
        if (r == 0) return "0°";
        return (r > 0 ? "+" : "−") + Math.abs(r) + "°";
    }

    /** WMO weather code to a short word (Open-Meteo docs, "Weather variable documentation"). */
    static String text(int code, boolean ru) {
        switch (code) {
            case 0: return ru ? "ясно" : "clear";
            case 1: return ru ? "малооблачно" : "mainly clear";
            case 2: return ru ? "переменная облачность" : "partly cloudy";
            case 3: return ru ? "пасмурно" : "overcast";
            case 45: case 48: return ru ? "туман" : "fog";
            case 51: case 53: case 55: return ru ? "морось" : "drizzle";
            case 56: case 57: return ru ? "ледяная морось" : "freezing drizzle";
            case 61: return ru ? "небольшой дождь" : "light rain";
            case 63: return ru ? "дождь" : "rain";
            case 65: return ru ? "сильный дождь" : "heavy rain";
            case 66: case 67: return ru ? "ледяной дождь" : "freezing rain";
            case 71: return ru ? "небольшой снег" : "light snow";
            case 73: return ru ? "снег" : "snow";
            case 75: return ru ? "сильный снег" : "heavy snow";
            case 77: return ru ? "снежная крупа" : "snow grains";
            case 80: case 81: return ru ? "ливень" : "showers";
            case 82: return ru ? "сильный ливень" : "violent showers";
            case 85: case 86: return ru ? "снегопад" : "snow showers";
            case 95: return ru ? "гроза" : "thunderstorm";
            case 96: case 99: return ru ? "гроза с градом" : "thunderstorm with hail";
            default: return ru ? "погода" : "weather";
        }
    }

    /** A one-character picture for the screens (system emoji font on the TV). */
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 2) return day ? "⛅" : "☁";
        if (code == 3) return "☁";
        if (code == 45 || code == 48) return "≋";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        if (code >= 95) return "⚡";
        return "☂";
    }
}
