package tv.cleanyour.ru.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Minimal line icons for the weather, one colour, drawn on a 64×64 grid (docs/fork/widgets-mockup.html draws the same
 * ones): a cloud, sun or moon, and what falls. Strength shows as more and longer drops or more and bigger flakes; sleet
 * mixes drops and flakes; a blizzard adds wind. Kinds are {@link Sky}'s.
 */
final class WeatherIcon {
    private WeatherIcon() {
    }

    static Bitmap bitmap(String kind, boolean day, int sizePx, int colour) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, sizePx), Math.max(1, sizePx), Bitmap.Config.ARGB_8888);
        draw(new Canvas(b), kind, day, 0, 0, sizePx, colour);
        return b;
    }

    /** Draws the icon with its 64×64 grid scaled into a square of {@code size} at (x, y). */
    static void draw(Canvas c, String kind, boolean day, float x, float y, float size, int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colour);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        c.save();
        c.translate(x, y);
        c.scale(size / 64f, size / 64f);
        switch (kind) {
            case Sky.CLEAR:
                if (day) sun(c, p, 32, 32, 10); else moon(c, p, 30, 32, 14);
                break;
            case Sky.PARTLY:
                behind(c, cloud(10, 12, 0.8f, 3.5f), () -> sky(c, p, day, 26, 24, 9));
                c.drawPath(cloud(10, 12, 0.8f, 0), p);
                break;
            case Sky.CLOUDY:
                behind(c, cloud(-4, 4, 1, 3.5f), () -> sky(c, p, day, 40, 18, 7));
                c.drawPath(cloud(-4, 4, 1, 0), p);
                break;
            case Sky.OVERCAST:
                behind(c, cloud(-4, 8, 1, 3.5f), () -> c.drawPath(cloud(10, -6, 0.8f, 0), p));
                c.drawPath(cloud(-4, 8, 1, 0), p);
                break;
            case Sky.FOG:
                c.drawPath(cloud(0, -4, 1, 0), p);
                c.drawLine(14, 52, 50, 52, p);
                c.drawLine(18, 58, 46, 58, p);
                break;
            case Sky.DRIZZLE:
                c.drawPath(cloud(0, -2, 1, 0), p);
                dots(c, p, new float[] {22, 50, 32, 54, 42, 50});
                break;
            case Sky.RAIN1: rainy(c, p, -2, new float[] {26, 50, 38, 50}, "dd", false); break;
            case Sky.RAIN2: rainy(c, p, -2, new float[] {20, 50, 32, 50, 44, 50}, "ddd", false); break;
            case Sky.RAIN3: rainy(c, p, -4, new float[] {18, 48, 28, 48, 38, 48, 48, 48}, "dddd", true); break;
            case Sky.SLEET1: rainy(c, p, -2, new float[] {22, 50, 33, 50, 44, 50}, "dfd", false); break;
            case Sky.SLEET2: rainy(c, p, -4, new float[] {17, 48, 27, 48, 37, 48, 47, 48}, "fdfd", true); break;
            case Sky.SNOW1: rainy(c, p, -2, new float[] {26, 50, 38, 50}, "ff", false); break;
            case Sky.SNOW2: rainy(c, p, -2, new float[] {20, 50, 32, 50, 44, 50}, "fff", false); break;
            case Sky.SNOW3: rainy(c, p, -6, new float[] {17, 45, 29, 45, 41, 45, 23, 54, 35, 54, 47, 54}, "ffffff", true); break;
            case Sky.BLIZZARD: {
                rainy(c, p, -6, new float[] {20, 44, 32, 44, 44, 44}, "fff", false);
                Paint w = new Paint(p);
                w.setStrokeWidth(2.6f);
                Path wind = new Path();
                wind.moveTo(10, 55);
                wind.lineTo(40, 55);
                wind.arcTo(new RectF(37, 49, 43, 55), 90, -270, false);
                wind.moveTo(14, 58);
                wind.lineTo(46, 58);
                wind.arcTo(new RectF(43, 58, 49, 64), -90, 270, false);
                c.drawPath(wind, w);
                break;
            }
            case Sky.THUNDER:
                c.drawPath(cloud(0, -4, 1, 0), p);
                c.drawPath(bolt(), p);
                break;
            case Sky.HAIL:
                c.drawPath(cloud(0, -4, 1, 0), p);
                c.drawPath(bolt(), p);
                Paint h = new Paint(p);
                h.setStrokeWidth(2.4f);
                c.drawCircle(20, 51, 2.6f, h);
                c.drawCircle(44, 51, 2.6f, h);
                c.drawCircle(44, 59, 2.6f, h);
                break;
            default:
                c.drawPath(cloud(0, 0, 1, 0), p);
        }
        c.restore();
    }

    private static void sky(Canvas c, Paint p, boolean day, float cx, float cy, float r) {
        if (day) sun(c, p, cx, cy, r); else moon(c, p, cx, cy, r + 1);
    }

    private static void sun(Canvas c, Paint p, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, p);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float co = (float) Math.cos(a), si = (float) Math.sin(a);
            c.drawLine(cx + co * (r + 4), cy + si * (r + 4), cx + co * (r + 8), cy + si * (r + 8), p);
        }
    }

    /** A crescent: a disc less a second disc up and to the right. */
    private static void moon(Canvas c, Paint p, float cx, float cy, float r) {
        Path disc = new Path();
        disc.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        c.drawPath(disc, p);
    }

    /**
     * The cloud outline: a flat bottom from (20, 46) to (46, 46), a bump of 10 on the right, a top arc of 14 and a bump
     * of 11.5 on the left, as the union of three circles and a box; {@code grow} widens it (the gap behind a cloud).
     */
    static Path cloud(float dx, float dy, float k, float grow) {
        Path u = new Path();
        u.addCircle(33.6f, 29.5f, 14 + grow, Path.Direction.CW);
        Path r = new Path();
        r.addCircle(46, 36, 10 + grow, Path.Direction.CW);
        u.op(r, Path.Op.UNION);
        Path l = new Path();
        l.addCircle(20, 34.5f, 11.5f + grow, Path.Direction.CW);
        u.op(l, Path.Op.UNION);
        Path box = new Path();
        box.addRect(20, 30, 46, 46 + grow, Path.Direction.CW);
        u.op(box, Path.Op.UNION);
        Matrix m = new Matrix();
        m.setScale(k, k);
        m.postTranslate(dx, dy);
        u.transform(m);
        return u;
    }

    /** Draws something with the (widened) cloud cut out, so it seems to sit behind the cloud. */
    private static void behind(Canvas c, Path cloud, Runnable draw) {
        c.save();
        c.clipOutPath(cloud);
        draw.run();
        c.restore();
    }

    private static Path bolt() {
        Path b = new Path();
        b.moveTo(34, 44);
        b.lineTo(28, 53);
        b.lineTo(35, 53);
        b.lineTo(31, 62);
        return b;
    }

    private static void dots(Canvas c, Paint p, float[] xy) {
        Paint f = new Paint(p);
        f.setStyle(Paint.Style.FILL);
        for (int i = 0; i < xy.length; i += 2) c.drawCircle(xy[i], xy[i + 1] + 2, 1.8f, f);
    }

    /** A cloud lifted by {@code lift} and under it, at each point, a drop ('d') or a flake ('f'). */
    private static void rainy(Canvas c, Paint p, float lift, float[] xy, String what, boolean big) {
        c.drawPath(cloud(0, lift, 1, 0), p);
        Paint f = new Paint(p);
        f.setStrokeWidth(big ? 3f : 2.6f);
        for (int i = 0; i < what.length(); i++) {
            float x = xy[2 * i], y = xy[2 * i + 1];
            if (what.charAt(i) == 'd') {
                c.drawLine(x, y, x - 2, y + (big ? 9 : 6), f);
            } else {
                float r = big ? 4.2f : 3.2f;
                for (int a = 0; a < 180; a += 60) {
                    double d = Math.toRadians(a);
                    float co = (float) (Math.cos(d) * r), si = (float) (Math.sin(d) * r);
                    c.drawLine(x - co, y + 3 - si, x + co, y + 3 + si, f);
                }
            }
        }
    }
}
