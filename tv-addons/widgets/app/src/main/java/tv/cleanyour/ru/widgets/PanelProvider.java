package tv.cleanyour.ru.widgets;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.format.DateFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The pictures for the Clean Your TV RU launcher's widget panel (widget_panel, tv-launcher/panel.patch): the F1 block
 * on the left, the weather and holidays on the right. The launcher asks each minute while Home is on screen; a query
 * draws the pictures again when the minute (a countdown) or the data changed. Read-only and exported without a
 * permission: the pictures show only what Home shows anyway.
 */
public final class PanelProvider extends ContentProvider {
    static final String AUTHORITY = "tv.cleanyour.ru.widgets.panel";
    static final Uri PANEL = Uri.parse("content://" + AUTHORITY + "/panel");
    private static final String[] COLUMNS = {"slot", "uri", "revision"};
    private static final String LEFT = "panel-left.png", RIGHT = "panel-right.png";

    private static long drawnKey = Long.MIN_VALUE;
    private static boolean hasLeft, hasRight;

    static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(PANEL, null);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"panel".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        long key = draw(ctx);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        if (hasLeft) c.addRow(new Object[] {"left", "content://" + AUTHORITY + "/" + LEFT + "?r=" + key, key});
        if (hasRight) c.addRow(new Object[] {"right", "content://" + AUTHORITY + "/" + RIGHT + "?r=" + key, key});
        c.setNotificationUri(ctx.getContentResolver(), PANEL);
        Background.refresh(ctx, true, null);
        return c;
    }

    /** Draws both pictures when this minute or the saved data differ from the last drawing; returns their revision. */
    private static synchronized long draw(Context ctx) {
        Repo repo = new Repo(ctx);
        long now = System.currentTimeMillis();
        long key = now / 60_000L * 31 + repo.savedAt(Repo.CONFIG) + repo.savedAt(Repo.GOOD) + repo.savedAt(Repo.F1_SEASON)
                + repo.savedAt(Repo.F1_DRIVERS) + repo.savedAt(Repo.NEWS) + repo.savedAt(Repo.WEATHER);
        if (key == drawnKey) return key;
        Config cfg = repo.config();
        boolean ru = repo.russian(cfg);
        ZoneId zone = ZoneId.systemDefault();
        Instant t = Instant.ofEpochMilli(now);
        PanelRenderer r = new PanelRenderer(ctx.getResources().getDisplayMetrics().density, ru, zone, DateFormat.is24HourFormat(ctx));
        Bitmap left = null, right = null;
        try {
            left = r.left(cfg, cfg.f1 ? repo.season() : null, cfg.f1 ? repo.drivers() : null, cfg.f1 && cfg.news ? repo.news() : null, t);
            right = r.right(cfg, cfg.weather ? repo.forecast() : null, LocalDate.now(zone), t);
            hasLeft = save(repo.file(LEFT), left);
            hasRight = save(repo.file(RIGHT), right);
            drawnKey = key;
        } catch (RuntimeException e) {
            // keep the last pictures
        } finally {
            if (left != null) left.recycle();
            if (right != null) right.recycle();
        }
        return key;
    }

    private static boolean save(File f, Bitmap b) {
        if (b == null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return false;
        }
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            return f.isFile();
        }
        return tmp.renameTo(f) || f.isFile();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode.contains("w")) throw new FileNotFoundException("read-only");
        String name = uri.getLastPathSegment();
        if (!LEFT.equals(name) && !RIGHT.equals(name)) throw new FileNotFoundException(name);
        File f = new File(getContext().getFilesDir(), name);
        if (!f.isFile()) throw new FileNotFoundException(name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "panel".equals(uri.getLastPathSegment()) ? "vnd.android.cursor.dir/vnd." + AUTHORITY + ".slot" : "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
