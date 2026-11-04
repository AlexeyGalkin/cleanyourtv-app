package tv.cleanyour.ru.widgets;

import java.util.HashMap;
import java.util.Map;

/**
 * Russian names for the F1 calendar and grid, keyed by Jolpica's ids, as Russian F1 media write them (sports.ru,
 * championat). An id that is not here keeps Jolpica's English name, so a new driver or circuit still shows.
 */
final class Names {
    private Names() {
    }

    private static final Map<String, String[]> CIRCUITS = new HashMap<>(); // id -> {Grand Prix, city}
    private static final Map<String, String[]> DRIVERS = new HashMap<>();  // id -> {given, family}
    private static final Map<String, String> TEAMS = new HashMap<>();
    private static final Map<String, Integer> COLOURS = new HashMap<>();

    private static void c(String id, String gp, String city) {
        CIRCUITS.put(id, new String[] {gp, city});
    }

    private static void d(String id, String given, String family) {
        DRIVERS.put(id, new String[] {given, family});
    }

    private static void t(String id, String ru, int colour) {
        TEAMS.put(id, ru);
        COLOURS.put(id, colour);
    }

    static {
        c("albert_park", "Гран-при Австралии", "Мельбурн");
        c("shanghai", "Гран-при Китая", "Шанхай");
        c("suzuka", "Гран-при Японии", "Сузука");
        c("bahrain", "Гран-при Бахрейна", "Сахир");
        c("jeddah", "Гран-при Саудовской Аравии", "Джидда");
        c("miami", "Гран-при Майами", "Майами");
        c("villeneuve", "Гран-при Канады", "Монреаль");
        c("monaco", "Гран-при Монако", "Монте-Карло");
        c("catalunya", "Гран-при Барселоны", "Барселона");
        c("red_bull_ring", "Гран-при Австрии", "Шпильберг");
        c("silverstone", "Гран-при Великобритании", "Сильверстоун");
        c("spa", "Гран-при Бельгии", "Спа");
        c("hungaroring", "Гран-при Венгрии", "Будапешт");
        c("zandvoort", "Гран-при Нидерландов", "Зандворт");
        c("monza", "Гран-при Италии", "Монца");
        c("madring", "Гран-при Испании", "Мадрид");
        c("baku", "Гран-при Азербайджана", "Баку");
        c("sepang", "Гран-при Бахрейна в Малайзии", "Сепанг");
        c("marina_bay", "Гран-при Сингапура", "Сингапур");
        c("americas", "Гран-при США", "Остин");
        c("rodriguez", "Гран-при Мехико", "Мехико");
        c("interlagos", "Гран-при Сан-Паулу", "Сан-Паулу");
        c("vegas", "Гран-при Лас-Вегаса", "Лас-Вегас");
        c("losail", "Гран-при Катара", "Лусаил");
        c("yas_marina", "Гран-при Абу-Даби", "Абу-Даби");
        c("imola", "Гран-при Эмилии-Романьи", "Имола");

        d("antonelli", "Андреа Кими", "Антонелли");
        d("max_verstappen", "Макс", "Ферстаппен");
        d("hamilton", "Льюис", "Хэмилтон");
        d("russell", "Джордж", "Расселл");
        d("leclerc", "Шарль", "Леклер");
        d("norris", "Ландо", "Норрис");
        d("piastri", "Оскар", "Пиастри");
        d("hadjar", "Исак", "Хаджар");
        d("lawson", "Лиам", "Лоусон");
        d("gasly", "Пьер", "Гасли");
        d("arvid_lindblad", "Арвид", "Линдблад");
        d("colapinto", "Франко", "Колапинто");
        d("bearman", "Оливер", "Берман");
        d("bortoleto", "Габриэл", "Бортолето");
        d("hulkenberg", "Нико", "Хюлькенберг");
        d("ocon", "Эстебан", "Окон");
        d("alonso", "Фернандо", "Алонсо");
        d("sainz", "Карлос", "Сайнс");
        d("albon", "Александер", "Албон");
        d("tsunoda", "Юки", "Цунода");
        d("stroll", "Лэнс", "Стролл");
        d("bottas", "Валттери", "Боттас");
        d("perez", "Серхио", "Перес");
        d("doohan", "Джек", "Духан");
        d("ryo_hirakawa", "Рё", "Хиракава");
        d("paul_aron", "Пауль", "Арон");
        d("ayumu_iwasa", "Аюму", "Иваса");
        d("colton_herta", "Колтон", "Херта");
        d("frederik_vesti", "Фредерик", "Вести");

        t("mercedes", "Мерседес", 0xFF27F4D2);
        t("ferrari", "Феррари", 0xFFE8002D);
        t("red_bull", "Ред Булл", 0xFF3671C6);
        t("mclaren", "Макларен", 0xFFFF8000);
        t("rb", "Рейсинг Буллз", 0xFF6692FF);
        t("alpine", "Альпин", 0xFF0093CC);
        t("haas", "Хаас", 0xFFB6BABD);
        t("audi", "Ауди", 0xFFBB0A30);
        t("sauber", "Заубер", 0xFF52E252);
        t("williams", "Уильямс", 0xFF64C4FF);
        t("aston_martin", "Астон Мартин", 0xFF229971);
        t("cadillac", "Кадиллак", 0xFFC0C0C0);
    }

    static String grandPrix(F1.Race r, boolean ru) {
        String[] n = ru ? CIRCUITS.get(r.circuitId) : null;
        return n != null ? n[0] : r.name;
    }

    static String city(F1.Race r, boolean ru) {
        String[] n = ru ? CIRCUITS.get(r.circuitId) : null;
        return n != null ? n[1] : r.locality;
    }

    /** "М. Ферстаппен" / "M. Verstappen". */
    static String driverShort(String id, String given, String family, boolean ru) {
        String[] n = ru ? DRIVERS.get(id) : null;
        String g = n != null ? n[0] : given;
        String f = n != null ? n[1] : family;
        return g.isEmpty() ? f : g.charAt(0) + ". " + f;
    }

    static String team(String id, String fallback, boolean ru) {
        String n = ru ? TEAMS.get(id) : null;
        return n != null ? n : fallback;
    }

    static int teamColour(String id) {
        Integer c = COLOURS.get(id);
        return c != null ? c : 0xFF9AA0A6;
    }
}
