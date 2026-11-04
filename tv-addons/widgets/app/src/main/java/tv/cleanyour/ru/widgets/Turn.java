package tv.cleanyour.ru.widgets;

/**
 * One line pair under the launcher's clock (game_day, docs/modules.md): {@code topLine} is the small top line; the
 * launcher draws {@code word} + "in 2h 18m" until {@code startMs}, "Live now" until {@code endMs}, then drops it.
 */
final class Turn {
    static final int F1_RED = 0xFFE10600;
    static final int WEATHER_BLUE = 0xFF4FA3FF;
    static final int HOLIDAY_GOLD = 0xFFFFC145;

    final String topLine;
    final String word;
    final long startMs;
    final long endMs;
    final int colour;
    final String source;

    Turn(String topLine, String word, long startMs, long endMs, int colour, String source) {
        this.topLine = topLine;
        this.word = word;
        this.startMs = startMs;
        this.endMs = endMs;
        this.colour = colour;
        this.source = source;
    }

    @Override
    public String toString() {
        return source + ": " + topLine + " | " + word + " @" + startMs + ".." + endMs;
    }
}
