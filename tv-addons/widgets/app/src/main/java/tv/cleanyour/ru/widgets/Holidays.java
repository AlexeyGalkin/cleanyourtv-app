package tv.cleanyour.ru.widgets;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "What are we celebrating today?": Russia's public holidays, well-known professional and folk days, and light-hearted
 * international days for the rest of the year. Days of mourning and remembrance are left out on purpose: this is a
 * cheerful line on the home screen. Offline; every rule is a fixed date, an n-th weekday, or an offset from Orthodox
 * Easter.
 */
final class Holidays {
    private Holidays() {
    }

    /** Public holidays (non-working days in Russia), the ones counted down to. */
    static final int MAJOR = 0;
    /** Well-known days: professional, folk, church and international days. */
    static final int NOTABLE = 1;
    /** Light-hearted days, shown only when the "fun" option is on. */
    static final int FUN = 2;

    static final class Holiday {
        final String ru;
        final String en;
        final int kind;

        Holiday(String ru, String en, int kind) {
            this.ru = ru;
            this.en = en;
            this.kind = kind;
        }

        String name(boolean ruLang) {
            return ruLang ? ru : en;
        }
    }

    private interface Rule {
        boolean on(LocalDate d);
    }

    private static final class Entry {
        final Rule rule;
        final Holiday holiday;

        Entry(Rule rule, Holiday holiday) {
            this.rule = rule;
            this.holiday = holiday;
        }
    }

    private static final List<Entry> ALL = new ArrayList<>();

    private static void fixed(int month, int day, int kind, String ru, String en) {
        ALL.add(new Entry(d -> d.getMonthValue() == month && d.getDayOfMonth() == day, new Holiday(ru, en, kind)));
    }

    /** The n-th (1..4) or last (-1) given weekday of a month. */
    private static void nth(int month, int n, DayOfWeek dow, int kind, String ru, String en) {
        ALL.add(new Entry(d -> {
            if (d.getMonthValue() != month || d.getDayOfWeek() != dow) return false;
            LocalDate first = d.withDayOfMonth(1);
            LocalDate target = n < 0 ? d.with(TemporalAdjusters.lastInMonth(dow))
                    : first.with(TemporalAdjusters.dayOfWeekInMonth(n, dow));
            return d.equals(target);
        }, new Holiday(ru, en, kind)));
    }

    /** Days from Orthodox Easter, {@code length} days long. */
    private static void easter(int offset, int length, int kind, String ru, String en) {
        ALL.add(new Entry(d -> {
            long from = orthodoxEaster(d.getYear()).plusDays(offset).toEpochDay();
            long x = d.toEpochDay();
            return x >= from && x < from + length;
        }, new Holiday(ru, en, kind)));
    }

    static {
        // January
        fixed(1, 1, MAJOR, "Новый год", "New Year's Day");
        fixed(1, 7, MAJOR, "Рождество Христово", "Orthodox Christmas");
        fixed(1, 11, FUN, "Международный день «спасибо»", "International Thank You Day");
        fixed(1, 13, NOTABLE, "День российской печати", "Russian Press Day");
        fixed(1, 14, NOTABLE, "Старый Новый год", "Old New Year");
        fixed(1, 19, NOTABLE, "Крещение Господне", "Epiphany");
        fixed(1, 21, FUN, "Международный день объятий", "National Hugging Day");
        fixed(1, 25, NOTABLE, "Татьянин день, день студентов", "Tatiana Day, Students' Day");
        // February
        fixed(2, 8, NOTABLE, "День российской науки", "Russian Science Day");
        fixed(2, 9, FUN, "Международный день пиццы", "World Pizza Day");
        fixed(2, 10, NOTABLE, "День дипломатического работника", "Diplomats' Day");
        fixed(2, 14, NOTABLE, "День святого Валентина", "Valentine's Day");
        fixed(2, 21, NOTABLE, "Международный день родного языка", "International Mother Language Day");
        fixed(2, 23, MAJOR, "День защитника Отечества", "Defender of the Fatherland Day");
        fixed(2, 27, FUN, "Международный день полярного медведя", "International Polar Bear Day");
        // March
        fixed(3, 1, FUN, "День кошек", "Cat Day");
        fixed(3, 3, NOTABLE, "Всемирный день дикой природы", "World Wildlife Day");
        fixed(3, 8, MAJOR, "Международный женский день", "International Women's Day");
        fixed(3, 14, FUN, "День числа π", "Pi Day");
        fixed(3, 20, FUN, "Международный день счастья", "International Day of Happiness");
        fixed(3, 21, NOTABLE, "Всемирный день поэзии", "World Poetry Day");
        fixed(3, 22, NOTABLE, "Всемирный день водных ресурсов", "World Water Day");
        fixed(3, 27, NOTABLE, "Всемирный день театра", "World Theatre Day");
        // April
        fixed(4, 1, FUN, "День смеха", "April Fools' Day");
        fixed(4, 2, NOTABLE, "Международный день детской книги", "International Children's Book Day");
        fixed(4, 7, NOTABLE, "Всемирный день здоровья", "World Health Day");
        fixed(4, 12, NOTABLE, "День космонавтики", "Cosmonautics Day");
        fixed(4, 22, NOTABLE, "Международный день Матери-Земли", "Earth Day");
        fixed(4, 23, NOTABLE, "Всемирный день книги", "World Book Day");
        fixed(4, 29, FUN, "Международный день танца", "International Dance Day");
        // May
        fixed(5, 1, MAJOR, "Праздник Весны и Труда", "Spring and Labour Day");
        fixed(5, 4, FUN, "День «Звёздных войн»", "Star Wars Day");
        fixed(5, 7, NOTABLE, "День радио", "Radio Day");
        fixed(5, 9, MAJOR, "День Победы", "Victory Day");
        fixed(5, 15, NOTABLE, "Международный день семьи", "International Day of Families");
        fixed(5, 18, NOTABLE, "Международный день музеев", "International Museum Day");
        fixed(5, 20, FUN, "Всемирный день пчёл", "World Bee Day");
        fixed(5, 24, NOTABLE, "День славянской письменности и культуры", "Day of Slavonic Literature and Culture");
        fixed(5, 25, FUN, "День полотенца", "Towel Day");
        fixed(5, 27, NOTABLE, "Общероссийский день библиотек", "All-Russian Library Day");
        // June
        fixed(6, 1, NOTABLE, "Международный день защиты детей", "International Children's Day");
        fixed(6, 3, FUN, "Всемирный день велосипеда", "World Bicycle Day");
        fixed(6, 5, NOTABLE, "Всемирный день окружающей среды", "World Environment Day");
        fixed(6, 6, NOTABLE, "Пушкинский день, день русского языка", "Russian Language Day");
        fixed(6, 12, MAJOR, "День России", "Russia Day");
        nth(6, 3, DayOfWeek.SUNDAY, NOTABLE, "День медицинского работника", "Medical Workers' Day");
        fixed(6, 21, FUN, "Международный день йоги", "International Day of Yoga");
        fixed(6, 27, NOTABLE, "День молодёжи", "Youth Day");
        // July
        fixed(7, 7, NOTABLE, "Иван Купала", "Ivan Kupala Day");
        fixed(7, 8, NOTABLE, "День семьи, любви и верности", "Day of Family, Love and Fidelity");
        nth(7, 2, DayOfWeek.SUNDAY, FUN, "День рыбака", "Fisherman's Day");
        fixed(7, 17, FUN, "Всемирный день эмодзи", "World Emoji Day");
        fixed(7, 20, FUN, "Международный день шахмат", "International Chess Day");
        fixed(7, 28, NOTABLE, "День Крещения Руси", "Baptism of Rus' Day");
        nth(7, -1, DayOfWeek.SUNDAY, NOTABLE, "День Военно-морского флота", "Navy Day");
        fixed(7, 30, FUN, "Международный день дружбы", "International Day of Friendship");
        // August
        fixed(8, 2, NOTABLE, "День Воздушно-десантных войск", "Airborne Forces Day");
        nth(8, 2, DayOfWeek.SATURDAY, NOTABLE, "День физкультурника", "Sports Day");
        nth(8, 2, DayOfWeek.SUNDAY, NOTABLE, "День строителя", "Builders' Day");
        fixed(8, 14, NOTABLE, "Медовый Спас", "Honey Saviour");
        nth(8, 3, DayOfWeek.SUNDAY, NOTABLE, "День Воздушного флота России", "Air Fleet Day");
        fixed(8, 19, NOTABLE, "Яблочный Спас", "Apple Saviour");
        fixed(8, 22, NOTABLE, "День Государственного флага России", "Russian Flag Day");
        fixed(8, 27, NOTABLE, "День российского кино", "Russian Cinema Day");
        // September
        fixed(9, 1, NOTABLE, "День знаний", "Knowledge Day");
        fixed(9, 8, NOTABLE, "Международный день грамотности", "International Literacy Day");
        nth(9, 2, DayOfWeek.SUNDAY, NOTABLE, "День танкиста", "Tankmen's Day");
        ALL.add(new Entry(d -> d.getDayOfYear() == 256, new Holiday("День программиста", "Programmers' Day", FUN)));
        fixed(9, 19, FUN, "Международный день разговоров по-пиратски", "Talk Like a Pirate Day");
        fixed(9, 21, NOTABLE, "Международный день мира", "International Day of Peace");
        fixed(9, 26, NOTABLE, "Европейский день языков", "European Day of Languages");
        fixed(9, 27, NOTABLE, "День воспитателя", "Preschool Teachers' Day");
        fixed(9, 30, NOTABLE, "День интернета в России", "Russian Internet Day");
        // October
        fixed(10, 1, NOTABLE, "Международный день музыки", "International Music Day");
        fixed(10, 1, FUN, "Международный день кофе", "International Coffee Day");
        fixed(10, 4, FUN, "Всемирный день животных", "World Animal Day");
        fixed(10, 5, NOTABLE, "День учителя", "Teachers' Day");
        fixed(10, 14, NOTABLE, "Покров Пресвятой Богородицы", "Feast of the Intercession");
        fixed(10, 16, NOTABLE, "Всемирный день продовольствия", "World Food Day");
        nth(10, 3, DayOfWeek.SUNDAY, NOTABLE, "День отца", "Father's Day");
        fixed(10, 31, FUN, "Хеллоуин", "Halloween");
        // November
        fixed(11, 4, MAJOR, "День народного единства", "Unity Day");
        fixed(11, 10, NOTABLE, "День сотрудника органов внутренних дел", "Police Day");
        fixed(11, 13, FUN, "Всемирный день доброты", "World Kindness Day");
        fixed(11, 19, FUN, "Международный мужской день", "International Men's Day");
        fixed(11, 21, FUN, "Всемирный день телевидения", "World Television Day");
        nth(11, -1, DayOfWeek.SUNDAY, NOTABLE, "День матери", "Mother's Day");
        // December
        fixed(12, 3, NOTABLE, "День юриста", "Lawyers' Day");
        fixed(12, 5, NOTABLE, "Международный день волонтёра", "International Volunteer Day");
        fixed(12, 9, NOTABLE, "День Героев Отечества", "Heroes of the Fatherland Day");
        fixed(12, 12, NOTABLE, "День Конституции", "Constitution Day");
        fixed(12, 22, NOTABLE, "День энергетика", "Power Engineers' Day");
        fixed(12, 25, NOTABLE, "Католическое Рождество", "Christmas (Western)");
        fixed(12, 28, NOTABLE, "Международный день кино", "International Cinema Day");
        fixed(12, 31, NOTABLE, "Канун Нового года", "New Year's Eve");
        // Orthodox Easter and the days that move with it
        easter(-55, 7, NOTABLE, "Масленица", "Maslenitsa");
        easter(0, 1, NOTABLE, "Пасха", "Orthodox Easter");
        easter(49, 1, NOTABLE, "Троица", "Trinity Sunday");
    }

    /** Orthodox Easter (Meeus' Julian algorithm) as a Gregorian date; right for 1900 to 2099. */
    static LocalDate orthodoxEaster(int year) {
        int a = year % 4, b = year % 7, c = year % 19;
        int d = (19 * c + 15) % 30;
        int e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31;
        int day = (d + e + 114) % 31 + 1;
        return LocalDate.of(year, month, day).plusDays(13);
    }

    /** The day's holidays, public ones first; light-hearted ones only with {@code fun}. */
    static List<Holiday> on(LocalDate d, boolean fun) {
        List<Holiday> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.holiday.kind == FUN && !fun) continue;
            if (e.rule.on(d)) out.add(e.holiday);
        }
        Collections.sort(out, (x, y) -> Integer.compare(x.kind, y.kind));
        return out;
    }

    /** The next public holiday strictly after {@code d} (within a year and a day). */
    static Object[] nextMajor(LocalDate d) {
        for (int i = 1; i <= 367; i++) {
            LocalDate x = d.plusDays(i);
            for (Entry e : ALL) if (e.holiday.kind == MAJOR && e.rule.on(x)) return new Object[] {x, e.holiday};
        }
        return null;
    }

    /** The next day after {@code d} with any holiday, for the screen's "coming up" list. */
    static List<Object[]> upcoming(LocalDate d, boolean fun, int count) {
        List<Object[]> out = new ArrayList<>();
        for (int i = 1; i <= 366 && out.size() < count; i++) {
            LocalDate x = d.plusDays(i);
            for (Holiday h : on(x, fun)) {
                if (out.size() < count) out.add(new Object[] {x, h});
            }
        }
        return out;
    }
}
