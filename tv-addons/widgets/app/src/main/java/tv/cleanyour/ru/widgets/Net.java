package tv.cleanyour.ru.widgets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS GET with short timeouts and a size cap; tries each URL in turn (Jolpica, then the fork's GitHub mirror). */
final class Net {
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private Net() {
    }

    static String get(String... urls) throws IOException {
        IOException last = null;
        for (String u : urls) {
            if (u == null) continue;
            try {
                return getOne(u);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no url");
    }

    private static String getOne(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("https only");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "CleanYourTV-RU-Widgets/" + BuildConfig.VERSION_NAME);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("too big");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
