package tv.cleanyour.ru.widgets;

/**
 * What the weather looks like over a stretch of time, from amounts rather than one code: rain and showers in mm, snow
 * in cm, the strongest wind, the mean cloud cover and the worst WMO code (Open-Meteo, hourly). The kind picks the icon
 * (WeatherIcon) and the words; strength shows as more drops or flakes, and rain with snow is drawn together.
 */
final class Sky {
    private Sky() {
    }

    static final String CLEAR = "clear", PARTLY = "partly", CLOUDY = "cloudy", OVERCAST = "overcast", FOG = "fog",
            DRIZZLE = "drizzle", RAIN1 = "rain1", RAIN2 = "rain2", RAIN3 = "rain3", SLEET1 = "sleet1", SLEET2 = "sleet2",
            SNOW1 = "snow1", SNOW2 = "snow2", SNOW3 = "snow3", BLIZZARD = "blizzard", THUNDER = "thunder", HAIL = "hail";

    /**
     * The amounts are for a 12-hour stretch; {@link #now} scales one hour up to that. Thresholds: rain under 2 mm is
     * light, over 10 mm heavy; snow under 1 cm is light, over 5 cm a snowfall; snow with wind over 10 m/s a blizzard.
     */
    static String kind(double rainMm, double snowCm, int worstCode, double cloudPct, double windMs) {
        if (worstCode == 96 || worstCode == 99) return HAIL;
        if (worstCode >= 95) return THUNDER;
        boolean snow = snowCm >= 0.1, rain = rainMm >= 0.3;
        if (snow && rain && rainMm >= 0.5 && snowCm >= 0.2) return rainMm + snowCm * 7 >= 6 ? SLEET2 : SLEET1;
        if (snow) {
            if (windMs >= 10 && snowCm >= 1) return BLIZZARD;
            return snowCm < 1 ? SNOW1 : snowCm < 5 ? SNOW2 : SNOW3;
        }
        if (rain) {
            if (worstCode >= 51 && worstCode <= 57 && rainMm < 1) return DRIZZLE;
            return rainMm < 2 ? RAIN1 : rainMm < 10 ? RAIN2 : RAIN3;
        }
        if (worstCode == 45 || worstCode == 48) return FOG;
        if (worstCode >= 51 && worstCode <= 57) return DRIZZLE;
        if (cloudPct < 20) return CLEAR;
        if (cloudPct < 60) return PARTLY;
        if (cloudPct < 85) return CLOUDY;
        return OVERCAST;
    }

    /**
     * Right now: the latest amounts (Open-Meteo's 'current' is a sum over its interval, 15 minutes) scaled by
     * {@code toTwelveHours} so the same thresholds hold; a code that says it is raining or snowing wins over amounts
     * too small to count.
     */
    static String now(double toTwelveHours, double rainMm, double snowCm, int code, double cloudPct, double windMs) {
        String k = kind(rainMm * toTwelveHours, snowCm * toTwelveHours, code, cloudPct, windMs);
        boolean dry = k.equals(CLEAR) || k.equals(PARTLY) || k.equals(CLOUDY) || k.equals(OVERCAST);
        if (dry && (code >= 71 && code <= 77 || code == 85 || code == 86)) return SNOW1;
        if (dry && (code >= 61 && code <= 67 || code >= 80 && code <= 82)) return RAIN1;
        return k;
    }

    static String now(double rainMmHour, double snowCmHour, int code, double cloudPct, double windMs) {
        return now(12, rainMmHour, snowCmHour, code, cloudPct, windMs);
    }

    static String words(String kind, boolean ru) {
        switch (kind) {
            case CLEAR: return ru ? "Ясно" : "Clear";
            case PARTLY: return ru ? "Малооблачно" : "Mostly clear";
            case CLOUDY: return ru ? "Облачно с прояснениями" : "Partly cloudy";
            case OVERCAST: return ru ? "Пасмурно" : "Overcast";
            case FOG: return ru ? "Туман" : "Fog";
            case DRIZZLE: return ru ? "Морось" : "Drizzle";
            case RAIN1: return ru ? "Небольшой дождь" : "Light rain";
            case RAIN2: return ru ? "Дождь" : "Rain";
            case RAIN3: return ru ? "Ливень" : "Heavy rain";
            case SLEET1: return ru ? "Мокрый снег с дождём" : "Sleet";
            case SLEET2: return ru ? "Сильный мокрый снег" : "Heavy sleet";
            case SNOW1: return ru ? "Небольшой снег" : "Light snow";
            case SNOW2: return ru ? "Снег" : "Snow";
            case SNOW3: return ru ? "Снегопад" : "Heavy snow";
            case BLIZZARD: return ru ? "Метель" : "Blizzard";
            case THUNDER: return ru ? "Гроза" : "Thunderstorm";
            case HAIL: return ru ? "Гроза с градом" : "Thunderstorm with hail";
            default: return ru ? "Погода" : "Weather";
        }
    }

    /** Lower case, for the middle of a line: "Москва · +3°, пасмурно". */
    static String wordsInline(String kind, boolean ru) {
        String w = words(kind, ru);
        return w.substring(0, 1).toLowerCase(ru ? new java.util.Locale("ru") : java.util.Locale.ENGLISH) + w.substring(1);
    }
}
