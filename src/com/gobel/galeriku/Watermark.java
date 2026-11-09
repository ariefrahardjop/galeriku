package com.gobel.galeriku;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Galeriku v1.3 — render watermark Klasik (GPS Map Camera 1.8.2)
 * dan Job Timestamp (1.9.41) mengikuti Stempel Foto GPS Web.
 *
 * Klasik: kartu gelap translusen 83% W, peta satelit Esri zoom 17 persegi
 * 18,8% W, pill "GPS Map Camera" menyatu di kanan atas kartu, judul +
 * bendera Indonesia, alamat max 3 baris, "Lat x° Long y°", tanggal
 * "Kamis, 24/09/2026 09:22 GMT +07:00", baris cuaca teks.
 * Job: strip abu-abu #7d7d7d selebar foto sebagai OVERLAY di bawah foto
 * (ukuran foto TIDAK bertambah), peta jalan OSM zoom 15 lebar 25% W,
 * alamat 1 baris, tempat max 3 baris besar rata tengah, Lat/Long,
 * tanggal "2026-11-02(Sen) 07:23(AM)", ikon cuaca digambar + °C/°F.
 * Tile peta dijahit dari beberapa tile (bukan satu tile ditarik);
 * bila offline/tile gagal dipakai peta sketsa. Tile diunduh sinkron
 * (pemanggil wajib di thread latar).
 */
public class Watermark {

    public static final String[] DAY = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
    public static final String[] DAYAB = {"Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"};

    /** Semua isian form watermark (vals() di web). */
    public static class Opts {
        public double lat = Double.NaN, lon = Double.NaN;
        public boolean hasGps = false;
        public String place = "", address = "", weatherText = "";
        public String dateStr = "", timeStr = ""; // yyyy-MM-dd, HH:mm
        public double scale = 1.0; // 0.70 - 1.50
        public boolean showMap = true, showAddr = true, showCoord = true;
        public boolean showLogo = true, showFlag = true, showWeather = true;
        public String cond = "cloudy"; // sunny | partly | cloudy | rain
        public Double tempC = 28.0;
        public int rotation = 0; // 0/90/180/270
        public int jobTransparency = 40; // Job: 0-90 persen (0=padat, 90=hampir tembus)
        public Bitmap appIcon = null; // boleh null -> fallback digambar
    }

    private static Bitmap cachedIcon = null;
    private static boolean iconTried = false;

    /** Muat assets/app-icon.png sekali, cache. Null bila gagal. */
    public static Bitmap loadAppIcon(Context ctx) {
        if (iconTried) return cachedIcon;
        iconTried = true;
        InputStream in = null;
        try {
            in = ctx.getAssets().open("app-icon.png");
            cachedIcon = BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            cachedIcon = null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
        return cachedIcon;
    }

    private static Paint textPaint(float size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setFakeBoldText(bold);
        return p;
    }

    private static String pad2(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** Kalender dari isian tanggal+jam (zona waktu perangkat, seperti web). */
    public static Calendar parseDateTime(String dateStr, String timeStr) {
        Calendar c = Calendar.getInstance();
        try {
            String[] dp = (dateStr == null ? "" : dateStr.trim()).split("-");
            if (dp.length == 3) {
                c.set(Calendar.YEAR, Integer.parseInt(dp[0]));
                c.set(Calendar.MONTH, Integer.parseInt(dp[1]) - 1);
                c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dp[2]));
            }
            String t = (timeStr == null || timeStr.trim().isEmpty()) ? "00:00" : timeStr.trim();
            String[] tp = t.split(":");
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(tp[0]));
            c.set(Calendar.MINUTE, tp.length > 1 ? Integer.parseInt(tp[1]) : 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        } catch (Exception ignored) {
        }
        return c;
    }

    /** "GMT +07:00" dari offset zona waktu perangkat pada tanggal itu. */
    public static String gmtLabel(Calendar c) {
        int offMin = TimeZone.getDefault().getOffset(c.getTimeInMillis()) / 60000;
        String sign = offMin >= 0 ? "+" : "-";
        int a = Math.abs(offMin);
        return "GMT " + sign + pad2(a / 60) + ":" + pad2(a % 60);
    }

    /** Format Klasik: "Kamis, 24/09/2026 09:22 GMT +07:00". */
    public static String formatClassicDate(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK) - 1; // Minggu=0
        return DAY[dow] + ", " + pad2(c.get(Calendar.DAY_OF_MONTH)) + "/"
                + pad2(c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.YEAR) + " "
                + pad2(c.get(Calendar.HOUR_OF_DAY)) + ":" + pad2(c.get(Calendar.MINUTE))
                + " " + gmtLabel(c);
    }

    /** Format Job: "2026-11-02(Sen) 07:23(AM)". */
    public static String formatJobDate(Calendar c) {
        int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
        int h12 = c.get(Calendar.HOUR_OF_DAY) % 12;
        if (h12 == 0) h12 = 12;
        return c.get(Calendar.YEAR) + "-" + pad2(c.get(Calendar.MONTH) + 1) + "-"
                + pad2(c.get(Calendar.DAY_OF_MONTH)) + "(" + DAYAB[dow] + ")  "
                + pad2(h12) + ":" + pad2(c.get(Calendar.MINUTE)) + "("
                + (c.get(Calendar.HOUR_OF_DAY) < 12 ? "AM" : "PM") + ")";
    }

    /** "Lat -7,276971° Long 112,747416°" — 6 desimal, "—" bila tanpa GPS. */
    public static String latLngLine(Opts o) {
        String la = Double.isNaN(o.lat) ? "—" : String.format(Locale.US, "%.6f", o.lat);
        String lo = Double.isNaN(o.lon) ? "—" : String.format(Locale.US, "%.6f", o.lon);
        return "Lat " + la + "° Long " + lo + "°";
    }

    // ---------- helper teks ----------

    /** Bagi teks jadi max maxLines baris selebar maxW (ellipsis di baris akhir). */
    private static String[] wrapText(Paint p, String text, float maxW, int maxLines) {
        if (text == null) text = "";
        ArrayList<String> lines = new ArrayList<String>();
        String[] words = text.trim().split("\\s+");
        String cur = "";
        for (int i = 0; i < words.length; i++) {
            String test = cur.isEmpty() ? words[i] : cur + " " + words[i];
            if (p.measureText(test) > maxW && !cur.isEmpty()) {
                lines.add(cur);
                cur = words[i];
                if (lines.size() == maxLines) break;
            } else {
                cur = test;
            }
        }
        if (lines.size() < maxLines && !cur.isEmpty()) {
            lines.add(cur);
        } else if (lines.size() == maxLines && !cur.isEmpty()
                && !lines.get(maxLines - 1).equals(cur)) {
            String last = lines.get(maxLines - 1);
            while (last.length() > 0 && p.measureText(last + "…") > maxW) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(maxLines - 1, last + "…");
        }
        return lines.toArray(new String[0]);
    }

    /** Kecilkan ukuran font sampai teks muat di maxW (faktor 0,94, seperti web). */
    private static float fitSize(String text, float size, float maxW, boolean bold) {
        Paint p = textPaint(size, Color.WHITE, bold);
        int guard = 0;
        while (p.measureText(text) > maxW && size > 9 && guard < 60) {
            size *= 0.94f;
            p.setTextSize(size);
            guard++;
        }
        return size;
    }

    private static void roundRectPath(Path path, float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        path.moveTo(x + r, y);
        path.lineTo(x + w - r, y);
        path.arcTo(new RectF(x + w - 2 * r, y, x + w, y + 2 * r), 270, 90);
        path.lineTo(x + w, y + h - r);
        path.arcTo(new RectF(x + w - 2 * r, y + h - 2 * r, x + w, y + h), 0, 90);
        path.lineTo(x + r, y + h);
        path.arcTo(new RectF(x, y + h - 2 * r, x + 2 * r, y + h), 90, 90);
        path.lineTo(x, y + r);
        path.arcTo(new RectF(x, y, x + 2 * r, y + 2 * r), 180, 90);
        path.close();
    }

    // ---------- peta ----------

    private static double[] tileXY(double lat, double lng, int z) {
        double n = Math.pow(2, z);
        double x = (lng + 180.0) / 360.0 * n;
        double r = Math.toRadians(lat);
        double y = (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * n;
        return new double[]{x, y};
    }

    private static Bitmap tryTile(String[] urls) {
        for (int i = 0; i < urls.length; i++) {
            Bitmap b = ExifUtil.downloadTile(urls[i]);
            if (b != null) return b;
        }
        return null;
    }

    /**
     * Jahit beberapa tile agar memenuhi kotak (lat/lon di tengah).
     * style "sat": Esri zoom 17; "road": OSM-de zoom 15, fallback ArcGIS jalan.
     * Bila tanpa GPS / semua tile gagal: peta sketsa (bukan kotak gelap polos).
     * showLogo=false -> logo "Google" tidak digambar (pin tetap).
     * mapAlpha 0-255: tile + sketsa digambar dengan alpha ini (v1.5: Job 200,
     * agar foto di bawah strip samar terlihat); pin + logo tetap opaque.
     */
    private static void drawMap(Canvas cv, float x, float y, float w, float h,
                                Opts o, boolean sat, boolean showLogo, int mapAlpha) {
        float radius = Math.min(12, w * 0.05f);
        Path clip = new Path();
        roundRectPath(clip, x, y, w, h, radius);
        cv.save();
        cv.clipPath(clip);
        Paint tilePaint = new Paint();
        tilePaint.setAlpha(mapAlpha);
        boolean drawn = false;
        if (o.hasGps && !Double.isNaN(o.lat) && !Double.isNaN(o.lon)) {
            int z = sat ? 17 : 15;
            int ts = 256;
            double[] c = tileXY(o.lat, o.lon, z);
            double cx = c[0] * ts, cy = c[1] * ts;
            float scaleF = Math.max(w, h) / ts;
            float viewW = w / scaleF, viewH = h / scaleF;
            int x0 = (int) Math.floor((cx - viewW / 2) / ts);
            int x1 = (int) Math.floor((cx + viewW / 2) / ts);
            int y0 = (int) Math.floor((cy - viewH / 2) / ts);
            int y1 = (int) Math.floor((cy + viewH / 2) / ts);
            int maxT = (1 << z) - 1;
            for (int tx = x0; tx <= x1; tx++) {
                for (int ty = y0; ty <= y1; ty++) {
                    if (ty < 0 || ty > maxT) continue;
                    int wx = ((tx % (maxT + 1)) + maxT + 1) % (maxT + 1);
                    String[] urls;
                    if (sat) {
                        urls = new String[]{
                                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" + z + "/" + ty + "/" + wx,
                                "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" + z + "/" + ty + "/" + wx};
                    } else {
                        urls = new String[]{
                                "https://tile.openstreetmap.de/" + z + "/" + wx + "/" + ty + ".png",
                                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/" + z + "/" + ty + "/" + wx};
                    }
                    Bitmap tile = tryTile(urls);
                    if (tile == null) continue;
                    float dx = (float) (x + (tx * ts - (cx - viewW / 2)) * scaleF);
                    float dy = (float) (y + (ty * ts - (cy - viewH / 2)) * scaleF);
                    cv.drawBitmap(tile, null,
                            new RectF(dx, dy, dx + ts * scaleF + 1, dy + ts * scaleF + 1), tilePaint);
                    tile.recycle();
                    drawn = true;
                }
            }
        }
        if (!drawn) {
            Paint fill = new Paint();
            fill.setColor(sat ? Color.rgb(0x5d, 0x70, 0x52) : Color.rgb(0xe8, 0xef, 0xe4));
            fill.setAlpha(mapAlpha);
            cv.drawRect(x, y, x + w, y + h, fill);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(sat ? Color.argb(89, 255, 255, 255) : Color.WHITE);
            line.setAlpha(sat ? (89 * mapAlpha) / 255 : mapAlpha);
            line.setStrokeWidth(Math.max(3, w * 0.045f));
            for (int i = -1; i < 4; i++) {
                cv.drawLine(x + i * w * 0.32f, y + h, x + i * w * 0.32f + w * 0.25f, y, line);
                cv.drawLine(x, y + i * h * 0.34f, x + w, y + i * h * 0.34f - h * 0.18f, line);
            }
            if (!sat) {
                Paint el = new Paint(Paint.ANTI_ALIAS_FLAG);
                el.setColor(Color.argb(128, 140, 180, 230));
                el.setAlpha((128 * mapAlpha) / 255);
                cv.save();
                cv.rotate(28.6f, x + w * 0.82f, y + h * 0.2f);
                cv.drawOval(new RectF(x + w * 0.62f, y + h * 0.07f, x + w * 1.02f, y + h * 0.33f), el);
                cv.restore();
            }
        }
        drawPin(cv, x + w / 2f, y + h / 2f, Math.min(w, h) * 0.085f);
        if (showLogo) {
            drawGoogle(cv, x + w * 0.055f, y + h - w * 0.045f,
                    Math.min(w * 0.17f, h * 0.2f), sat);
        }
        cv.restore();
        Path border = new Path();
        roundRectPath(border, x, y, w, h, radius);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setColor(Color.argb(71, 0, 0, 0));
        bp.setStrokeWidth(2);
        cv.drawPath(border, bp);
    }

    /** Pin tetesan merah (bezier) + titik gelap di dalam. */
    private static void drawPin(Canvas cv, float cx, float cy, float r) {
        Paint pin = new Paint(Paint.ANTI_ALIAS_FLAG);
        pin.setColor(Color.rgb(0xe6, 0x21, 0x17));
        pin.setShadowLayer(r * 0.25f, 0, r * 0.12f, Color.argb(89, 0, 0, 0));
        Path p = new Path();
        p.moveTo(cx, cy + r * 1.55f);
        p.cubicTo(cx - r * 1.02f, cy + r * 0.35f, cx - r * 1.02f, cy - r * 0.55f, cx, cy - r * 0.55f);
        p.cubicTo(cx + r * 1.02f, cy - r * 0.55f, cx + r * 1.02f, cy + r * 0.35f, cx, cy + r * 1.55f);
        p.close();
        cv.drawPath(p, pin);
        pin.clearShadowLayer();
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.rgb(0x7a, 0x0d, 0x08));
        cv.drawCircle(cx, cy - r * 0.12f, r * 0.44f, dot);
    }

    /** Bitmap peta jalan OSM (zoom 15, pin merah di tengah) untuk panel detail.
     *  Tile diunduh sinkron — pemanggil WAJIB di thread latar. Null bila tanpa GPS. */
    public static Bitmap mapBitmap(Context ctx, double lat, double lon, int pxW, int pxH) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || pxW <= 0 || pxH <= 0) return null;
        Bitmap out = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Opts o = new Opts();
        o.lat = lat; o.lon = lon; o.hasGps = true;
        drawMap(cv, 0, 0, pxW, pxH, o, false, false, 255);
        return out;
    }

    /** Balik ketukan (px di bitmap peta jalan zoom 15) menjadi lat/lon.
     *  Kebalikan persis dari pemetaan piksel di drawMap (scaleF + tileXY). */
    public static double[] mapTapToLatLon(double lat, double lon, int pxW, int pxH,
                                          float tapX, float tapY) {
        int z = 15, ts = 256;
        double[] c = tileXY(lat, lon, z);
        double cx = c[0] * ts, cy = c[1] * ts;
        float scaleF = Math.max(pxW, pxH) / (float) ts;
        float viewW = pxW / scaleF, viewH = pxH / scaleF;
        double n = Math.pow(2, z);
        double world = n * ts; // lebar dunia dalam piksel tile (bukan jumlah tile)
        double tx = cx - viewW / 2 + tapX / scaleF;
        double ty = cy - viewH / 2 + tapY / scaleF;
        double lng = tx / world * 360.0 - 180.0;
        double lat2 = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * ty / world))));
        return new double[]{lat2, lng};
    }

    /** Logo "Google" huruf per huruf (warna Google di peta jalan, putih ber-outline di satelit). */
    private static void drawGoogle(Canvas cv, float x, float baselineY, float size, boolean satellite) {
        String word = "Google";
        int[] colors = {0x4285F4, 0xEA4335, 0xFBBC05, 0x4285F4, 0x34A853, 0xEA4335};
        Paint p = textPaint(size, Color.WHITE, true);
        float cx = x;
        for (int i = 0; i < word.length(); i++) {
            String ch = word.substring(i, i + 1);
            float wch = p.measureText(ch);
            if (satellite) {
                Paint stroke = textPaint(size, Color.argb(217, 0, 0, 0), true);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(size * 0.16f);
                stroke.setStrokeJoin(Paint.Join.ROUND);
                cv.drawText(ch, cx, baselineY, stroke);
                p.setColor(Color.WHITE);
                cv.drawText(ch, cx, baselineY, p);
            } else {
                p.setColor(colors[i]);
                cv.drawText(ch, cx, baselineY, p);
            }
            cx += wch * 0.98f;
        }
    }

    /** Bendera Indonesia (merah #e70011 / putih, rasio 1,5:1, sudut membulat). */
    private static void drawFlagID(Canvas cv, float x, float y, float w, float h) {
        Path clip = new Path();
        roundRectPath(clip, x, y, w, h, h * 0.14f);
        cv.save();
        cv.clipPath(clip);
        Paint p = new Paint();
        p.setColor(Color.rgb(0xe7, 0x00, 0x11));
        cv.drawRect(x, y, x + w, y + h / 2, p);
        p.setColor(Color.WHITE);
        cv.drawRect(x, y + h / 2, x + w, y + h, p);
        LinearGradient gr = new LinearGradient(x, y, x + w, y,
                new int[]{Color.argb(36, 0, 0, 0), Color.argb(26, 255, 255, 255), Color.argb(36, 0, 0, 0)},
                new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
        p.setShader(gr);
        cv.drawRect(x, y, x + w, y + h, p);
        cv.restore();
        Path border = new Path();
        roundRectPath(border, x, y, w, h, h * 0.14f);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setColor(Color.argb(77, 0, 0, 0));
        bp.setStrokeWidth(Math.max(1, h * 0.05f));
        cv.drawPath(border, bp);
    }

    /** Ikon aplikasi asli (assets/app-icon.png); fallback gradien biru + pin kuning + lensa. */
    private static void drawAppIcon(Canvas cv, float x, float y, float s, Bitmap icon) {
        Path clip = new Path();
        roundRectPath(clip, x, y, s, s, s * 0.2f);
        if (icon != null) {
            cv.save();
            cv.clipPath(clip);
            cv.drawBitmap(icon, null, new RectF(x, y, x + s, y + s), null);
            cv.restore();
        } else {
            cv.save();
            cv.clipPath(clip);
            LinearGradient gr = new LinearGradient(x, y, x, y + s,
                    Color.rgb(0x35, 0xb6, 0xe8), Color.rgb(0x16, 0x68, 0xb4),
                    Shader.TileMode.CLAMP);
            Paint p = new Paint();
            p.setShader(gr);
            cv.drawRect(x, y, x + s, y + s, p);
            p.setShader(null);
            p.setColor(Color.argb(235, 255, 255, 255));
            cv.drawOval(new RectF(x + s * 0.16f, y + s * 0.72f, x + s * 0.84f, y + s * 0.92f), p);
            Paint pin = new Paint(Paint.ANTI_ALIAS_FLAG);
            pin.setColor(Color.rgb(0xff, 0xc6, 0x1a));
            Path pp = new Path();
            pp.moveTo(x + s * 0.5f, y + s * 0.78f);
            pp.cubicTo(x + s * 0.22f, y + s * 0.5f, x + s * 0.24f, y + s * 0.16f, x + s * 0.5f, y + s * 0.16f);
            pp.cubicTo(x + s * 0.76f, y + s * 0.16f, x + s * 0.78f, y + s * 0.5f, x + s * 0.5f, y + s * 0.78f);
            pp.close();
            cv.drawPath(pp, pin);
            Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
            lens.setColor(Color.rgb(0x14, 0x32, 0x4f));
            cv.drawCircle(x + s * 0.5f, y + s * 0.42f, s * 0.15f, lens);
            lens.setColor(Color.rgb(0x7f, 0xd4, 0xff));
            cv.drawCircle(x + s * 0.45f, y + s * 0.37f, s * 0.05f, lens);
            cv.restore();
        }
        Path border = new Path();
        roundRectPath(border, x, y, s, s, s * 0.2f);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setColor(Color.argb(128, 255, 255, 255));
        bp.setStrokeWidth(Math.max(1, s * 0.04f));
        cv.drawPath(border, bp);
    }

    // ---------- ikon cuaca (digambar, bukan emoji) ----------

    private static void cloudPath(Path path, float cx, float cy, float w) {
        float h = w * 0.52f;
        path.moveTo(cx - w / 2, cy + h / 2);
        path.cubicTo(cx - w / 2, cy - h * 0.05f, cx - w * 0.32f, cy - h * 0.12f, cx - w * 0.27f, cy - h * 0.28f);
        path.cubicTo(cx - w * 0.2f, cy - h * 0.62f, cx + w * 0.12f, cy - h * 0.66f, cx + w * 0.24f, cy - h * 0.34f);
        path.cubicTo(cx + w * 0.44f, cy - h * 0.3f, cx + w / 2, cy - h * 0.02f, cx + w / 2, cy + h / 2);
        path.close();
    }

    private static void drawWeather(Canvas cv, String kind, float cx, float cy, float w) {
        if (kind == null) kind = "cloudy";
        boolean sunny = "sunny".equals(kind);
        boolean partly = "partly".equals(kind);
        if (sunny || partly) {
            float sx = sunny ? cx : cx - w * 0.22f;
            float sy = sunny ? cy : cy - w * 0.18f;
            float sr = w * 0.2f;
            Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
            ray.setColor(Color.rgb(0xf7, 0xb5, 0x00));
            ray.setStrokeWidth(w * 0.045f);
            ray.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                cv.drawLine((float) (sx + Math.cos(a) * sr * 1.2), (float) (sy + Math.sin(a) * sr * 1.2),
                        (float) (sx + Math.cos(a) * sr * 1.55), (float) (sy + Math.sin(a) * sr * 1.55), ray);
            }
            RadialGradient sg = new RadialGradient(sx - sr * 0.3f, sy - sr * 0.3f, sr,
                    new int[]{Color.rgb(0xff, 0xe0, 0x82), Color.rgb(0xff, 0xb3, 0x00)},
                    new float[]{0, 1}, Shader.TileMode.CLAMP);
            Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
            sun.setShader(sg);
            cv.drawCircle(sx, sy, sr, sun);
        }
        if (!sunny) {
            if ("cloudy".equals(kind) || "rain".equals(kind)) {
                Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
                back.setColor(Color.rgb(0x5b, 0x66, 0x70));
                Path bp = new Path();
                cloudPath(bp, cx + w * 0.17f, cy - w * 0.07f, w * 0.72f);
                cv.drawPath(bp, back);
            }
            LinearGradient fg = new LinearGradient(cx, cy - w * 0.3f, cx, cy + w * 0.3f,
                    Color.WHITE, Color.rgb(0xcf, 0xd8, 0xdc), Shader.TileMode.CLAMP);
            Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
            front.setShader(fg);
            front.setShadowLayer(w * 0.05f, 0, w * 0.03f, Color.argb(64, 0, 0, 0));
            Path fp = new Path();
            cloudPath(fp, partly ? cx + w * 0.1f : cx, cy + w * 0.05f, w * 0.92f);
            cv.drawPath(fp, front);
            front.clearShadowLayer();
            if ("rain".equals(kind)) {
                Paint rain = new Paint(Paint.ANTI_ALIAS_FLAG);
                rain.setColor(Color.rgb(0x4f, 0xc3, 0xf7));
                rain.setStrokeWidth(w * 0.05f);
                rain.setStrokeCap(Paint.Cap.ROUND);
                float[] offs = {-0.28f, 0f, 0.28f};
                for (int i = 0; i < offs.length; i++) {
                    cv.drawLine(cx + offs[i] * w, cy + w * 0.34f,
                            cx + offs[i] * w - w * 0.07f, cy + w * 0.52f, rain);
                }
            }
        }
    }

    // ---------- putar + gambar dasar ----------

    /** Gambar sumber (diputar bila rotation != 0) ke kanvas ukuran akhir W x H. */
    private static Bitmap baseWithRotation(Bitmap src, int rotation) {
        int w = src.getWidth(), h = src.getHeight();
        int outW = (rotation % 180 != 0) ? h : w;
        int outH = (rotation % 180 != 0) ? w : h;
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        if (rotation != 0) {
            cv.save();
            cv.translate(outW / 2f, outH / 2f);
            cv.rotate(rotation);
            cv.translate(-w / 2f, -h / 2f);
            cv.drawBitmap(src, 0, 0, null);
            cv.restore();
        } else {
            cv.drawBitmap(src, 0, 0, null);
        }
        return out;
    }

    // ---------------- Klasik ----------------

    public static Bitmap renderKlasik(Context ctx, Bitmap src, ExifUtil.ExifData ex, Opts o) {
        if (o.appIcon == null && ctx != null) o.appIcon = loadAppIcon(ctx);
        Bitmap out = baseWithRotation(src, o.rotation);
        Canvas cv = new Canvas(out);
        int W = out.getWidth(), H = out.getHeight();
        float s = (float) o.scale;
        float margin = W * 0.085f;
        float bottomMargin = W * 0.05f;
        float titleS = W * 0.0223f * s, bodyS = W * 0.0155f * s, smallS = W * 0.0155f * s;
        float lineGap = 1.38f;
        Calendar cal = parseDateTime(o.dateStr, o.timeStr);
        String dateLine = formatClassicDate(cal);

        boolean showMap = o.showMap;
        float mapW = showMap ? W * 0.188f : 0;
        float gap = showMap ? W * 0.016f : 0;
        float cardX = margin + mapW + gap;
        float cardW = W - margin - cardX;
        float padIn = W * 0.014f;
        float textW = cardW - padIn * 2;

        ArrayList<String> lineTexts = new ArrayList<String>();
        ArrayList<Float> lineSizes = new ArrayList<Float>();
        ArrayList<Boolean> lineTitle = new ArrayList<Boolean>();
        if (o.place != null && !o.place.trim().isEmpty()) {
            lineTexts.add(o.place.trim()); lineSizes.add(titleS); lineTitle.add(true);
        }
        if (o.showAddr && o.address != null && !o.address.trim().isEmpty()) {
            Paint bp = textPaint(bodyS, Color.WHITE, false);
            String[] wl = wrapText(bp, o.address.trim(), textW, 3);
            for (int i = 0; i < wl.length; i++) {
                lineTexts.add(wl[i]); lineSizes.add(bodyS); lineTitle.add(false);
            }
        }
        if (o.showCoord) {
            lineTexts.add(latLngLine(o)); lineSizes.add(smallS); lineTitle.add(false);
        }
        lineTexts.add(dateLine); lineSizes.add(bodyS); lineTitle.add(false);
        if (o.weatherText != null && !o.weatherText.trim().isEmpty()) {
            lineTexts.add(o.weatherText.trim()); lineSizes.add(smallS); lineTitle.add(false);
        }

        float textH = 0;
        for (int i = 0; i < lineSizes.size(); i++) textH += lineSizes.get(i) * lineGap;
        float contentH = textH + padIn * 1.25f;
        float mapH = showMap ? mapW : 0;
        float cardH = showMap ? Math.max(mapH, contentH) : contentH;
        float cardY = H - bottomMargin - cardH;
        float radius = W * 0.009f;

        // Pill "GPS Map Camera" (menyatu dengan kartu, satu fill).
        float pillW = 0, pillH = 0, pillX = 0, pillY = 0, pillFS = 0, pillIconS = 0;
        if (o.showLogo) {
            pillFS = W * 0.0144f * s;
            Paint pp = textPaint(pillFS, Color.WHITE, false);
            pillIconS = pillFS * 1.5f;
            pillW = pillIconS + pillFS * 0.55f + pp.measureText("GPS Map Camera") + W * 0.014f;
            pillH = pillIconS + W * 0.009f;
            pillX = W - margin - pillW;
            pillY = cardY - pillH;
        }

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.argb(148, 15, 15, 15));
        shadow.setShadowLayer(W * 0.008f, 0, W * 0.0025f, Color.argb(115, 0, 0, 0));
        Path combined = new Path();
        roundRectPath(combined, cardX, cardY, cardW, cardH, radius);
        if (o.showLogo) {
            // Pill: sudut atas membulat, bawah lurus masuk ke kartu.
            Path pill = new Path();
            float r = Math.min(radius, Math.min(pillW / 2, (pillH + radius) / 2));
            float pillBottom = pillY + pillH + radius;
            pill.moveTo(pillX, pillBottom);
            pill.lineTo(pillX, pillY + r);
            pill.arcTo(new RectF(pillX, pillY, pillX + 2 * r, pillY + 2 * r), 180, 90);
            pill.lineTo(pillX + pillW - r, pillY);
            pill.arcTo(new RectF(pillX + pillW - 2 * r, pillY, pillX + pillW, pillY + 2 * r), 270, 90);
            pill.lineTo(pillX + pillW, pillBottom);
            pill.close();
            combined.addPath(pill);
        }
        cv.drawPath(combined, shadow);

        if (o.showLogo) {
            drawAppIcon(cv, pillX + W * 0.006f, pillY + (pillH - pillIconS) / 2, pillIconS, o.appIcon);
            Paint tp = textPaint(pillFS, Color.WHITE, false);
            cv.drawText("GPS Map Camera",
                    pillX + W * 0.006f + pillIconS + pillFS * 0.55f,
                    pillY + pillH / 2 + pillFS * 0.35f, tp);
        }

        if (showMap) {
            drawMap(cv, margin, cardY + cardH - mapH, mapW, mapH, o, true, o.showLogo, 255);
        }

        float ty = cardY + padIn * 0.55f + (cardH - contentH) / 2;
        for (int i = 0; i < lineTexts.size(); i++) {
            float sz = lineSizes.get(i);
            boolean isTitle = lineTitle.get(i);
            Paint tp = textPaint(sz, Color.WHITE, isTitle);
            ty += sz;
            cv.drawText(lineTexts.get(i), cardX + padIn, ty, tp);
            if (isTitle && o.showFlag) {
                float tw = tp.measureText(lineTexts.get(i));
                float fh = sz * 0.62f, fw = fh * 1.5f;
                if (cardX + padIn + tw + fw + padIn <= cardX + cardW) {
                    drawFlagID(cv, cardX + padIn + tw + sz * 0.35f, ty - sz * 0.78f, fw, fh);
                }
            }
            ty += sz * (lineGap - 1);
        }
        return out;
    }

    // ---------------- Job Timestamp ----------------
    // Strip abu-abu #7d7d7d selebar foto sebagai OVERLAY di bawah foto
    // (ukuran foto TIDAK bertambah).

    public static Bitmap renderJob(Context ctx, Bitmap src, ExifUtil.ExifData ex, Opts o) {
        if (o.appIcon == null && ctx != null) o.appIcon = loadAppIcon(ctx);
        Bitmap out = baseWithRotation(src, o.rotation);
        Canvas cv = new Canvas(out);
        int W = out.getWidth(), H = out.getHeight();
        float s = (float) o.scale;
        Calendar cal = parseDateTime(o.dateStr, o.timeStr);
        float mapW = o.showMap ? W * 0.25f : 0;
        float bigS = W * 0.0235f * s, smallS = W * 0.0108f * s;
        float dateS = W * 0.02f * s, tempS = W * 0.0235f * s;
        float coordS = W * 0.0135f * s;
        float lineGap = 1.28f;
        float panelX = mapW, panelW = W - mapW;
        float weatherW = o.showWeather ? W * 0.135f : W * 0.02f;
        float centerW = panelW - weatherW - W * 0.015f;

        ArrayList<String> parts = new ArrayList<String>();
        if (o.place != null && !o.place.trim().isEmpty()) {
            String[] sp = o.place.split(",");
            for (int i = 0; i < sp.length; i++) {
                String t = sp[i].trim();
                if (!t.isEmpty()) parts.add(t);
            }
        }
        if (parts.isEmpty() && o.address != null && !o.address.trim().isEmpty()) {
            parts.add(o.address.trim());
        }
        ArrayList<String> midLines = new ArrayList<String>();
        for (int i = 0; i < Math.min(3, parts.size()); i++) midLines.add(parts.get(i));

        float contentH = smallS * 1.7f + midLines.size() * bigS * lineGap
                + (o.showCoord ? coordS * 1.5f : 0) + dateS * 1.55f;
        float barH = Math.max(contentH, W * 0.155f * s);
        float y0 = H - barH;

        // v1.8: transparansi strip Job bisa diatur user (0-90%).
        // stripAlpha = (100-t)*255/100; mapAlpha = min(255, stripAlpha+50).
        // t=40 -> strip 153, peta 203 (sama seperti v1.5); t=0 -> 255/255 padat.
        int stripAlpha = (int) Math.round((100 - o.jobTransparency) * 255.0 / 100.0);
        int mapAlpha = Math.min(255, stripAlpha + 50);
        Paint strip = new Paint();
        strip.setColor(Color.argb(stripAlpha, 0x7d, 0x7d, 0x7d));
        cv.drawRect(panelX, y0, panelX + panelW, y0 + barH, strip);
        // Peta jalan ikut transparan; pin + teks + ikon cuaca tetap opaque.
        if (o.showMap) drawMap(cv, 0, y0, mapW, barH, o, false, o.showLogo, mapAlpha);

        Paint white = textPaint(smallS, Color.WHITE, false);
        if (o.showAddr && o.address != null && !o.address.trim().isEmpty()) {
            float fsA = fitSize(o.address.trim(), smallS,
                    panelW - W * 0.03f - (o.showWeather ? weatherW * 0.55f : 0), false);
            Paint ap = textPaint(fsA, Color.WHITE, false);
            cv.drawText(o.address.trim(), panelX + W * 0.026f, y0 + smallS * 1.55f, ap);
        }
        float ty = y0 + smallS * 1.7f + (barH - contentH) / 2;
        for (int i = 0; i < midLines.size(); i++) {
            String t = midLines.get(i);
            float fs = fitSize(t, bigS, centerW, false);
            Paint bp = textPaint(fs, Color.WHITE, false);
            float w2 = bp.measureText(t);
            ty += fs;
            cv.drawText(t, panelX + (centerW - w2) / 2 + W * 0.008f, ty, bp);
            ty += fs * (lineGap - 1);
        }
        if (o.showCoord) {
            String coordLine = latLngLine(o);
            float fsC = fitSize(coordLine, coordS, centerW, false);
            Paint cp = textPaint(fsC, Color.WHITE, false);
            float cw = cp.measureText(coordLine);
            ty += fsC * 1.35f;
            cv.drawText(coordLine, panelX + (centerW - cw) / 2 + W * 0.008f, ty, cp);
        }
        String dtLine = formatJobDate(cal);
        float fsD = fitSize(dtLine, dateS, centerW, false);
        Paint dp = textPaint(fsD, Color.WHITE, false);
        float dw = dp.measureText(dtLine);
        cv.drawText(dtLine, panelX + (centerW - dw) / 2 + W * 0.008f, y0 + barH - dateS * 0.42f, dp);

        if (o.showWeather) {
            float wx = panelX + panelW - weatherW / 2 - W * 0.008f;
            drawWeather(cv, o.cond, wx, y0 + barH * 0.24f, weatherW * 0.72f);
            if (o.tempC != null) {
                long tf = Math.round(o.tempC * 9.0 / 5.0 + 32.0);
                Paint tp = textPaint(tempS, Color.WHITE, false);
                String t1 = Math.round(o.tempC) + "°C";
                String t2 = tf + "°F";
                cv.drawText(t1, panelX + panelW - W * 0.014f - tp.measureText(t1),
                        y0 + barH * 0.68f, tp);
                cv.drawText(t2, panelX + panelW - W * 0.014f - tp.measureText(t2),
                        y0 + barH * 0.68f + tempS * 1.3f, tp);
            }
        }
        return out;
    }
}
