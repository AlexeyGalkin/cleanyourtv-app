package tv.cleanyour.ru.widgets;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The phone app's way in, over ADB:
 * <pre>
 * content write --uri content://tv.cleanyour.ru.widgets.config/config.json &lt; config.json
 * content read  --uri content://tv.cleanyour.ru.widgets.config/config.json
 * </pre>
 * Guarded by WRITE_SECURE_SETTINGS (manifest and again here), which only the ADB shell holds. A file that does not
 * parse is dropped and the last good settings stay.
 */
public final class ConfigProvider extends ContentProvider {
    private static final String TAG = "CytWidgets";

    @Override
    public boolean onCreate() {
        return true;
    }

    private void guard() {
        Context ctx = getContext();
        if (ctx == null || ctx.checkCallingPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("ADB shell only");
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        guard();
        Context ctx = getContext();
        if (!Repo.CONFIG.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.getLastPathSegment());
        Repo repo = new Repo(ctx);
        File saved = repo.file(Repo.CONFIG);
        if (mode.contains("w")) {
            // Straight into config.json, so the phone's check (content read | sha256sum) reads what it wrote at once;
            // when the file closes it is checked, and a bad one is replaced by the last good copy.
            try {
                return ParcelFileDescriptor.open(saved, ParcelFileDescriptor.MODE_WRITE_ONLY
                                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                        new Handler(Looper.getMainLooper()), e -> apply(ctx));
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        if (!saved.isFile()) throw new FileNotFoundException("no settings yet");
        return ParcelFileDescriptor.open(saved, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void apply(Context ctx) {
        Context app = ctx.getApplicationContext();
        Background.execute(() -> {
            Repo repo = new Repo(app);
            File saved = repo.file(Repo.CONFIG);
            String before = repo.read(Repo.GOOD);
            try {
                if (saved.length() > Config.MAX_BYTES) throw new IOException("too big");
                String text = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
                if (text.startsWith("﻿")) text = text.substring(1);
                Config next = Config.parse(text);
                repo.write(Repo.GOOD, text);
                // Another city: the saved forecast belongs to the old one.
                Config old = before == null ? null : parseOrNull(before);
                if (old == null || old.city == null || next.city == null || old.city.latitude != next.city.latitude
                        || old.city.longitude != next.city.longitude) {
                    //noinspection ResultOfMethodCallIgnored
                    repo.file(Repo.WEATHER).delete();
                }
                GamesProvider.changed(app);
                PanelProvider.changed(app);
                Background.refresh(app, false, null);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "settings not applied: " + e.getMessage());
                try {
                    if (before != null) repo.write(Repo.CONFIG, before);
                    else //noinspection ResultOfMethodCallIgnored
                        saved.delete();
                } catch (IOException ignored) {
                    // config() falls back to the defaults
                }
            }
        });
    }

    private static Config parseOrNull(String text) {
        try {
            return Config.parse(text);
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    /** The phone's undo: {@code content delete --uri …/config.json} puts the add-on back to its defaults. */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        guard();
        if (!Repo.CONFIG.equals(uri.getLastPathSegment())) return 0;
        Repo repo = new Repo(getContext());
        boolean gone = repo.file(Repo.CONFIG).delete();
        //noinspection ResultOfMethodCallIgnored
        repo.file(Repo.GOOD).delete();
        GamesProvider.changed(getContext());
        return gone ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
