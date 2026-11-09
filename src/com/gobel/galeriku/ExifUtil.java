package com.gobel.galeriku;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.json.JSONArray;

/**
 * Galeriku — helper EXIF / penyimpanan / jaringan (tile + geocode).
 * Semua hasil ubah disimpan sebagai file BARU ke DCIM/Galeriku,
 * tidak pernah menimpa file asli.
 */
public class ExifUtil {

    public static final String GALERIKU_RELATIVE = Environment.DIRECTORY_DCIM + "/Galeriku";

    /** Tag yang disalin saat "Salin EXIF dari Foto Lain". */
    public static final String[] COPY_TAGS = new String[]{
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH, ExifInterface.TAG_USER_COMMENT
    };


    /**
     * android.media.ExifInterface (tanpa androidx) tidak punya setLatLong/
     * setAltitude — tulis tag GPS manual sebagai rasional EXIF
     * ("deg/1,min/1,sec/1000") + ref N/S/E/W, persis format yang dibaca
     * getLatLong() kembali.
     */
    public static void setLatLong(ExifInterface exif, double lat, double lon) {
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, rationalCoord(lat));
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat >= 0 ? "N" : "S");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, rationalCoord(lon));
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon >= 0 ? "E" : "W");
    }

    public static void setAltitude(ExifInterface exif, double alt) {
        long num = Math.round(Math.abs(alt) * 100);
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, num + "/100");
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, alt >= 0 ? "0" : "1");
    }

    private static String rationalCoord(double v) {
        double a = Math.abs(v);
        int deg = (int) Math.floor(a);
        double minF = (a - deg) * 60.0;
        int min = (int) Math.floor(minF);
        long sec1000 = Math.round((minF - min) * 60.0 * 1000);
        return deg + "/1," + min + "/1," + sec1000 + "/1000";
    }

    public static class ExifData {
        public double lat = Double.NaN;
        public double lon = Double.NaN;
        public double alt = Double.NaN;
        public boolean hasGps = false;
        public String dateTimeOriginal = null;
        public String gpsDate = null;   // yyyy:MM:dd
        public String gpsTime = null;   // HH:mm:ss
        public String make = null;
        public String model = null;
        // Info kamera (v1.5, baris "ISO 100 | 24mm | 0,0ev | F1,8 | 1/50 s").
        public int iso = -1;
        public double focalMm = Double.NaN;
        public double fNumber = Double.NaN;
        public double expTime = Double.NaN; // detik
        public double expBias = Double.NaN;
        public HashMap<String, String> tags = new HashMap<String, String>();

        public String latLonText() {
            if (!hasGps) return "-";
            return String.format(Locale.US, "%.6f, %.6f", lat, lon);
        }
        public String dateText() { // yyyy-MM-dd dari DateTimeOriginal
            if (dateTimeOriginal != null && dateTimeOriginal.length() >= 10) {
                return dateTimeOriginal.substring(0, 10).replace(':', '-');
            }
            if (gpsDate != null) return gpsDate.replace(':', '-');
            return "";
        }
        public String timeText() { // HH:mm:ss
            if (dateTimeOriginal != null && dateTimeOriginal.length() >= 19) {
                return dateTimeOriginal.substring(11, 19);
            }
            if (gpsTime != null) return gpsTime;
            return "";
        }
    }

    /** Baca EXIF dari URI MediaStore/SAF; pakai berkas asli (setRequireOriginal). */
    public static ExifData read(ContentResolver cr, Uri uri) {
        ExifData d = new ExifData();
        Uri readUri = uri;
        if (Build.VERSION.SDK_INT >= 29) {
            try { readUri = MediaStore.setRequireOriginal(uri); } catch (Exception ignored) {}
        }
        InputStream in = null;
        try {
            in = cr.openInputStream(readUri);
            if (in == null) return d;
            ExifInterface exif = new ExifInterface(in);
            float[] ll = new float[2];
            if (exif.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)) {
                d.lat = ll[0]; d.lon = ll[1]; d.hasGps = true;
            }
            d.alt = exif.getAttributeDouble(ExifInterface.TAG_GPS_ALTITUDE, Double.NaN);
            d.dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            d.gpsDate = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
            d.gpsTime = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
            d.make = exif.getAttribute(ExifInterface.TAG_MAKE);
            d.model = exif.getAttribute(ExifInterface.TAG_MODEL);
            d.iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, -1);
            d.focalMm = parseRational(exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH));
            d.fNumber = parseRational(exif.getAttribute(ExifInterface.TAG_F_NUMBER));
            d.expTime = parseRational(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME));
            d.expBias = parseRational(exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE));
            for (String t : COPY_TAGS) {
                String v = exif.getAttribute(t);
                if (v != null) d.tags.put(t, v);
            }
        } catch (Exception ignored) {
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
        return d;
    }

    /** Terapkan ExifData (lat/lon/alt/tanggal/jam/make/model) ke file di disk. */
    public static void applyToFile(ExifData d, String filePath) throws Exception {
        ExifInterface exif = new ExifInterface(filePath);
        if (d.hasGps) {
            setLatLong(exif, d.lat, d.lon);
            if (!Double.isNaN(d.alt)) setAltitude(exif, d.alt);
        }
        if (d.dateTimeOriginal != null) {
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, d.dateTimeOriginal);
            exif.setAttribute(ExifInterface.TAG_DATETIME, d.dateTimeOriginal);
        }
        if (d.gpsDate != null) exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, d.gpsDate);
        if (d.gpsTime != null) exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, d.gpsTime);
        if (d.make != null) exif.setAttribute(ExifInterface.TAG_MAKE, d.make);
        if (d.model != null) exif.setAttribute(ExifInterface.TAG_MODEL, d.model);
        exif.saveAttributes();
    }

    /** Salin semua tag sumber ke file tujuan (untuk Salin EXIF). */
    public static void copyTagsToFile(ExifData src, String filePath) throws Exception {
        ExifInterface exif = new ExifInterface(filePath);
        for (String t : COPY_TAGS) {
            String v = src.tags.get(t);
            if (v != null) exif.setAttribute(t, v);
        }
        if (src.hasGps) setLatLong(exif, src.lat, src.lon);
        exif.saveAttributes();
    }

    /** Pertahankan GPS + DateTimeOriginal foto asli pada hasil watermark. */
    public static void preserveGpsAndDate(ExifData orig, String filePath) throws Exception {
        ExifInterface exif = new ExifInterface(filePath);
        if (orig.hasGps) {
            setLatLong(exif, orig.lat, orig.lon);
            if (!Double.isNaN(orig.alt)) setAltitude(exif, orig.alt);
        }
        if (orig.dateTimeOriginal != null) {
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, orig.dateTimeOriginal);
        }
        if (orig.gpsDate != null) exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, orig.gpsDate);
        if (orig.gpsTime != null) exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, orig.gpsTime);
        if (orig.make != null) exif.setAttribute(ExifInterface.TAG_MAKE, orig.make);
        if (orig.model != null) exif.setAttribute(ExifInterface.TAG_MODEL, orig.model);
        exif.saveAttributes();
    }

    /** "24/1" atau "18/10" atau "1/50" -> double; NaN bila kosong/gagal. */
    public static double parseRational(String s) {
        if (s == null) return Double.NaN;
        try {
            s = s.trim();
            int slash = s.indexOf('/');
            if (slash >= 0) {
                double num = Double.parseDouble(s.substring(0, slash).trim());
                double den = Double.parseDouble(s.substring(slash + 1).trim());
                if (den == 0) return Double.NaN;
                return num / den;
            }
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public static String stem(String name) {
        if (name == null) return "foto";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static String extOf(String name) {
        if (name == null) return "jpg";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "jpg";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    public static String mimeFor(String name) {
        String e = extOf(name);
        if ("png".equals(e)) return "image/png";
        if ("webp".equals(e)) return "image/webp";
        return "image/jpeg";
    }

    /** Salin isi URI ke file cache, kembalikan File-nya. */
    public static File copyUriToCache(Context ctx, Uri uri, String cacheName) throws Exception {
        File out = new File(ctx.getCacheDir(), cacheName);
        InputStream in = null;
        OutputStream os = null;
        try {
            Uri readUri = uri;
            if (Build.VERSION.SDK_INT >= 29) {
                try { readUri = MediaStore.setRequireOriginal(uri); } catch (Exception ignored) {}
            }
            in = ctx.getContentResolver().openInputStream(readUri);
            if (in == null) throw new Exception("Tidak bisa membuka foto");
            os = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (os != null) os.close(); } catch (Exception ignored) {}
        }
        return out;
    }

    /**
     * Simpan file cache sebagai file BARU ke DCIM/Galeriku via MediaStore.
     * Mengembalikan Uri hasil, atau null bila gagal.
     */
    public static Uri saveFileToGaleriku(Context ctx, File cacheFile,
                                         String newName, String mime) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, newName);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, GALERIKU_RELATIVE);
        Uri uri = ctx.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Gagal membuat file di DCIM/Galeriku");
        InputStream in = null;
        OutputStream os = null;
        try {
            in = new FileInputStream(cacheFile);
            os = ctx.getContentResolver().openOutputStream(uri);
            if (os == null) throw new Exception("Gagal membuka output");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (os != null) os.close(); } catch (Exception ignored) {}
        }
        return uri;
    }

    // ---------- tile peta & reverse geocode (dipakai watermark, ada fallback) ----------

    public static int[] latLonToTile(double lat, double lon, int zoom) {
        double latRad = Math.toRadians(lat);
        int n = 1 << zoom;
        int x = (int) Math.floor((lon + 180.0) / 360.0 * n);
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
        return new int[]{x, y};
    }

    /** Unduh satu tile peta; null bila offline/gagal (pemanggil pakai fallback). */
    public static Bitmap downloadTile(String urlStr) {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Galeriku/1.0 (Android)");
            if (c.getResponseCode() != 200) return null;
            in = c.getInputStream();
            return BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

    /** Reverse geocode Nominatim; null bila gagal (pemanggil pakai teks lat/lon). */
    public static String reverseGeocode(double lat, double lon) {
        HttpURLConnection c = null;
        BufferedReader r = null;
        try {
            String u = "https://nominatim.openstreetmap.org/reverse?format=json&lat="
                    + lat + "&lon=" + lon + "&zoom=18&addressdetails=0";
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Galeriku/1.0 (Android)");
            if (c.getResponseCode() != 200) return null;
            r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            String json = sb.toString();
            String key = "\"display_name\":\"";
            int i = json.indexOf(key);
            if (i < 0) return null;
            int start = i + key.length();
            int end = json.indexOf('"', start);
            if (end < 0) return null;
            return json.substring(start, end).replace("\\\"", "\"");
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }
        /** Hasil reverse geocode: alamat lengkap + baris judul (place). */
    public static class GeoResult {
        public String address;
        public String place;
    }

    /** "Kecamatan X, Provinsi, Negara" (awalan "Kecamatan " ditambah bila perlu). */
    public static String placeFromParts(String district, String state, String country) {
        StringBuilder sb = new StringBuilder();
        if (district != null && !district.trim().isEmpty()) {
            String d = district.trim();
            sb.append(d.toLowerCase(Locale.US).startsWith("kec") ? d : "Kecamatan " + d);
        }
        if (state != null && !state.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state.trim());
        }
        if (country != null && !country.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(country.trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String jsonStr(org.json.JSONObject o, String... keys) {
        if (o == null) return "";
        for (int i = 0; i < keys.length; i++) {
            String v = o.optString(keys[i], "");
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    private static GeoResult nominatimLookup(String host, double lat, double lon) {
        HttpURLConnection c = null;
        BufferedReader r = null;
        try {
            String u = "https://" + host + "/reverse?format=jsonv2&zoom=18&addressdetails=1&lat="
                    + lat + "&lon=" + lon;
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(9000);
            c.setReadTimeout(9000);
            c.setRequestProperty("User-Agent", "Galeriku/1.0 (Android)");
            if (c.getResponseCode() != 200) return null;
            r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            org.json.JSONObject j = new org.json.JSONObject(sb.toString());
            String display = j.optString("display_name", "");
            if (display.isEmpty()) return null;
            org.json.JSONObject a = j.optJSONObject("address");
            String district = jsonStr(a, "city_district", "municipality", "suburb", "borough", "town");
            String state = jsonStr(a, "state");
            String country = jsonStr(a, "country");
            GeoResult g = new GeoResult();
            g.address = display;
            g.place = placeFromParts(district, state, country);
            return g;
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

    /** Reverse geocode Nominatim (.de lalu .org) -> alamat + place; null bila gagal. */
    public static GeoResult reverseGeocodeDetailed(double lat, double lon) {
        GeoResult g = nominatimLookup("nominatim.openstreetmap.de", lat, lon);
        if (g != null) return g;
        return nominatimLookup("nominatim.openstreetmap.org", lat, lon);
    }

    /** Satu hasil pencarian alamat Nominatim. */
    public static class SearchResult {
        public String display;
        public double lat, lon;
    }

    /** Cari alamat (Nominatim search, maks 5 hasil); list kosong bila gagal.
     *  Pemanggil WAJIB di thread latar. */
    public static ArrayList<SearchResult> searchAddress(String q) {
        ArrayList<SearchResult> out = new ArrayList<SearchResult>();
        HttpURLConnection c = null;
        BufferedReader r = null;
        try {
            String enc = URLEncoder.encode(q, "UTF-8");
            String u = "https://nominatim.openstreetmap.org/search?q=" + enc
                    + "&format=json&limit=5&addressdetails=1";
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(9000);
            c.setReadTimeout(9000);
            c.setRequestProperty("User-Agent", "Galeriku/1.6 (Android)");
            if (c.getResponseCode() != 200) return out;
            r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                SearchResult s = new SearchResult();
                s.display = o.optString("display_name", "");
                s.lat = o.optDouble("lat", Double.NaN);
                s.lon = o.optDouble("lon", Double.NaN);
                if (!s.display.isEmpty() && !Double.isNaN(s.lat) && !Double.isNaN(s.lon)) out.add(s);
            }
            return out;
        } catch (Exception ignored) {
            return out;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
            if (c != null) c.disconnect();
        }
    }

}
