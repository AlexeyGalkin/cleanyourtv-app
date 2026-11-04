package tv.cleanyour.ru.widgets;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** The one thread that fetches. Nothing is scheduled: work starts only when the launcher or a screen asks. */
final class Background {
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private Background() {
    }

    /** Fetch what is due; tell the launcher and {@code done} (may be null) when something new came in. */
    static void refresh(Context ctx, boolean all, Runnable done) {
        Context app = ctx.getApplicationContext();
        if (!BUSY.compareAndSet(false, true)) return;
        ONE.execute(() -> {
            try {
                if (new Repo(app).refresh(System.currentTimeMillis(), all)) {
                    GamesProvider.changed(app);
                    if (done != null) done.run();
                }
            } finally {
                BUSY.set(false);
            }
        });
    }

    static void execute(Runnable r) {
        ONE.execute(r);
    }
}
