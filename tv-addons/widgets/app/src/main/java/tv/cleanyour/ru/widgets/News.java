package tv.cleanyour.ru.widgets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F1 news in Russian from Russian outlets' RSS: f1news.ru (all F1) and Чемпионат (its motorsport feed, the items in
 * the «Формула-1» category). Headlines, times and links only; no pictures, no article text. Merged, newest first,
 * without repeats.
 */
final class News {
    private News() {
    }

    static final String F1NEWS = "https://www.f1news.ru/export/news.xml";
    static final String CHAMPIONAT = "https://www.championat.com/rss/news/auto/";
    static final int KEEP = 40;

    static final class Item {
        final String title;
        final String source;   // "f1news.ru", "Чемпионат"
        final long timeMs;
        final String link;

        Item(String title, String source, long timeMs, String link) {
            this.title = title;
            this.source = source;
            this.timeMs = timeMs;
            this.link = link;
        }
    }

    private static final Pattern ITEM = Pattern.compile("<item[^>]*>(.*?)</item>", Pattern.DOTALL);

    private static String tag(String item, String name) {
        Matcher m = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">", Pattern.DOTALL).matcher(item);
        return m.find() ? clean(m.group(1)) : "";
    }

    private static List<String> tags(String item, String name) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">", Pattern.DOTALL).matcher(item);
        while (m.find()) out.add(clean(m.group(1)));
        return out;
    }

    /** CDATA unwrapped, the five XML entities and numeric ones decoded, tags dropped, spaces folded. */
    static String clean(String s) {
        s = s.trim();
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) s = s.substring(9, s.length() - 3);
        s = s.replaceAll("<[^>]+>", " ");
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            int code;
            try {
                code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                code = ' ';
            }
            if (code <= 0 || !Character.isValidCodePoint(code) || (code >= 0xD800 && code <= 0xDFFF)) code = ' ';
            m.appendReplacement(b, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        m.appendTail(b);
        s = b.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").replace("&laquo;", "«").replace("&raquo;", "»").replace("&mdash;", "—")
                .replace("&ndash;", "–").replace("&amp;", "&");
        return s.replaceAll("\\s+", " ").trim();
    }

    static long time(String rfc822) {
        try {
            return ZonedDateTime.parse(rfc822.trim(), DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))
                    .toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** One feed's items; {@code f1Only} keeps those whose categories name Формула-1. */
    static List<Item> parse(String xml, String source, boolean f1Only) {
        List<Item> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = ITEM.matcher(xml);
        while (m.find()) {
            String it = m.group(1);
            if (f1Only) {
                boolean f1 = false;
                for (String c : tags(it, "category")) if (c.toLowerCase(Locale.ROOT).contains("формула")) f1 = true;
                if (!f1) continue;
            }
            String title = tag(it, "title");
            long t = time(tag(it, "pubDate"));
            if (title.isEmpty() || t == 0) continue;
            out.add(new Item(title, source, t, tag(it, "link")));
        }
        return out;
    }

    /** Both feeds together, newest first, a headline once, at most {@link #KEEP}. */
    static List<Item> merge(List<Item> a, List<Item> b) {
        List<Item> all = new ArrayList<>(a);
        all.addAll(b);
        all.sort(Comparator.comparingLong((Item i) -> i.timeMs).reversed());
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Item i : all) {
            String key = i.title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
            if (key.length() > 40) key = key.substring(0, 40);
            if (seen.add(key)) out.add(i);
            if (out.size() >= KEEP) break;
        }
        return out;
    }

    static String toJson(List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item i : items) {
                a.put(new JSONObject().put("title", i.title).put("source", i.source).put("time", i.timeMs).put("link", i.link));
            }
        } catch (JSONException ignored) {
            // values are plain strings and numbers
        }
        return a.toString();
    }

    static List<Item> fromJson(String json) {
        List<Item> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.optString("title"), o.optString("source"), o.optLong("time"), o.optString("link")));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    static boolean fresh(Item i, Instant now, long maxAgeMs) {
        return now.toEpochMilli() - i.timeMs <= maxAgeMs;
    }
}
