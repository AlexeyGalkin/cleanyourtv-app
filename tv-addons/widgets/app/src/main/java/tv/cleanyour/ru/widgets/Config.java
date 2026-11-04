package tv.cleanyour.ru.widgets;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The settings the phone app writes over ADB (ConfigProvider, config.json), spec 1:
 * <pre>
 * {"spec":1, "lang":"ru", "turn_seconds":20,
 *  "f1":{"under_clock":true},
 *  "weather":{"under_clock":true, "name":"Москва", "admin1":"Москва", "country":"Россия",
 *             "latitude":55.75, "longitude":37.62, "timezone":"Europe/Moscow"},
 *  "holidays":{"under_clock":true, "fun":true}}
 * </pre>
 * A missing section turns that widget's line under the clock off; the screens behind the card always show.
 */
final class Config {
    static final int MAX_BYTES = 16 * 1024;

    final String lang;          // "ru", "en" or "" (the TV's language)
    final int turnSeconds;      // 10 to 120
    final boolean f1;
    final boolean weather;
    final boolean holidays;
    final boolean funHolidays;
    final City city;            // null until the phone (or the TV) picks one

    static final class City {
        final String name;
        final String admin1;
        final String country;
        final double latitude;
        final double longitude;
        final String timezone;

        City(String name, String admin1, String country, double latitude, double longitude, String timezone) {
            this.name = name;
            this.admin1 = admin1;
            this.country = country;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timezone = timezone;
        }
    }

    Config(String lang, int turnSeconds, boolean f1, boolean weather, boolean holidays, boolean funHolidays, City city) {
        this.lang = lang;
        this.turnSeconds = turnSeconds;
        this.f1 = f1;
        this.weather = weather;
        this.holidays = holidays;
        this.funHolidays = funHolidays;
        this.city = city;
    }

    /** Before the phone has written anything: F1 and holidays on, no city, the TV's language. */
    static Config defaults() {
        return new Config("", 20, true, false, true, true, null);
    }

    /** Parses and validates; throws on anything the phone would never write, so a bad file never replaces a good one. */
    static Config parse(String text) throws JSONException {
        if (text == null || text.length() > MAX_BYTES) throw new JSONException("missing or too big");
        JSONObject o = new JSONObject(text);
        if (o.optInt("spec", 0) != 1) throw new JSONException("spec must be 1");
        String lang = o.optString("lang", "");
        if (!lang.isEmpty() && !lang.equals("ru") && !lang.equals("en")) throw new JSONException("lang");
        int turn = o.optInt("turn_seconds", 20);
        if (turn < 10 || turn > 120) turn = 20;
        JSONObject f1 = o.optJSONObject("f1");
        JSONObject w = o.optJSONObject("weather");
        JSONObject h = o.optJSONObject("holidays");
        City city = null;
        if (w != null && w.has("latitude")) {
            String name = w.optString("name", "").trim();
            double lat = w.getDouble("latitude");
            double lon = w.getDouble("longitude");
            if (name.isEmpty() || name.length() > 80) throw new JSONException("city name");
            if (Double.isNaN(lat) || lat < -90 || lat > 90 || Double.isNaN(lon) || lon < -180 || lon > 180) {
                throw new JSONException("coordinates");
            }
            city = new City(name, w.optString("admin1", ""), w.optString("country", ""), lat, lon,
                    w.optString("timezone", ""));
        }
        return new Config(lang, turn,
                f1 != null && f1.optBoolean("under_clock", true),
                w != null && city != null && w.optBoolean("under_clock", true),
                h != null && h.optBoolean("under_clock", true),
                h == null || h.optBoolean("fun", true),
                city);
    }

    /** "Москва, Россия" (admin1 only when it adds something). */
    String place() {
        if (city == null) return "";
        StringBuilder b = new StringBuilder(city.name);
        if (!city.admin1.isEmpty() && !city.admin1.equals(city.name)) b.append(", ").append(city.admin1);
        if (!city.country.isEmpty()) b.append(", ").append(city.country);
        return b.toString();
    }
}
