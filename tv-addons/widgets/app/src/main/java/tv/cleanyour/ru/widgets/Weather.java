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
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
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

    static final class Forecast {
        final ZoneId zone;
        final double temp;
        final double feels;
        final int humidity;
        final int code;
        final double wind;
        final boolean day;
        final List<Day> days;

        Forecast(ZoneId zone, double temp, double feels, int humidity, int code, double wind, boolean day, List<Day> days) {
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
        return new Forecast(zone, cur.optDouble("temperature_2m", Double.NaN), cur.optDouble("apparent_temperature", Double.NaN),
                cur.optInt("relative_humidity_2m", -1), cur.optInt("weather_code", 0), cur.optDouble("wind_speed_10m", Double.NaN),
                cur.optInt("is_day", 1) == 1, days);
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
