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
        if (mode.contains("w")) {
            File received = new File(ctx.getCacheDir(), "config-received.json");
            try {
                return ParcelFileDescriptor.open(received, ParcelFileDescriptor.MODE_WRITE_ONLY
                                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                        new Handler(Looper.getMainLooper()), e -> apply(ctx, received));
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        File saved = repo.file(Repo.CONFIG);
        if (!saved.isFile()) throw new FileNotFoundException("no settings yet");
        return ParcelFileDescriptor.open(saved, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void apply(Context ctx, File received) {
        Context app = ctx.getApplicationContext();
        Background.execute(() -> {
            try {
                if (received.length() > Config.MAX_BYTES) throw new IOException("too big");
                String text = new String(Files.readAllBytes(received.toPath()), StandardCharsets.UTF_8);
                if (text.startsWith("﻿")) text = text.substring(1);
                Config.parse(text);
                Repo repo = new Repo(app);
                repo.write(Repo.CONFIG, text);
                // A new city: the old forecast belongs to another place.
                //noinspection ResultOfMethodCallIgnored
                repo.file(Repo.WEATHER).delete();
                GamesProvider.changed(app);
                Background.refresh(app, false, null);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "settings not applied: " + e.getMessage());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                received.delete();
            }
        });
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

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
