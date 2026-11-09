package com.gobel.galeriku;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.DownloadListener;
import android.widget.DatePicker;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Galeriku v1.5 — UI galeri gaya Samsung Gallery (tema gelap, minimal teks).
 * Layar: Album (2 kolom) -> Grid folder (4 kolom) -> Viewer (swipe kiri/kanan,
 * filmstrip bawah, swipe ke atas = panel detail).
 * Logika inti v1.0 TIDAK berubah: Edit EXIF, Salin EXIF, Watermark Klasik/Job
 * (hasil BARU ke DCIM/Galeriku, asli tidak ditimpa), Bagikan/ZIP, Browser
 * universal (address bar + Go, file chooser SAF).
 * v1.5: panel detail ala Samsung (peta OSM + alamat + info kamera, 3 tombol
 *   dihapus), ikon viewer 18sp + hati Favorit, Album penting + Lihat semua +
 *   Tambah album, lembar Edit tinggal Salin EXIF / Watermark / Tambah ke album,
 *   strip + peta Job transparan.
 * v1.6: Edit EXIF dapat cari alamat (Nominatim) + peta titik OSM yang bisa
 *   diketuk; SEMUA button abu-abu membulat teks putih (tanpa hijau); file
 *   chooser browser disanitasi agar accept ".jpg,.jpeg,.png" (e-SDM) bisa
 *   membuka pemilih Dokumen SAF; semua dialog jadi bottom sheet membulat
 *   ala Samsung Gallery (Tambah album, Bagikan, Tambah ke album).
 * v1.4: ikon viewer 20sp, Album/Grid pakai status-bar padding, kartu mode
 *   watermark menurun, watermark auto reverse-geocode dari EXIF, browser
 *   ala Chrome (pill, progress, multi-tab, perbaikan Google).
 * v1.3: watermark Klasik/Job mengikuti Stempel Foto GPS Web (pill, bendera,
 * peta dijahit, strip Job overlay, ikon cuaca digambar, toggle, skala,
 * putar 90°); field Nama Pekerjaan/Catatan/Altitude/Akurasi dihapus dari
 * form Job (tidak ada di web).
 */
public class MainActivity extends Activity {

    private static final int REQ_PERM = 10;
    private static final int REQ_FILE = 101;
    private static final int REQ_COPY = 201;

    private static final int BLACK = Color.BLACK;
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(150, 150, 150);
    private static final int PANEL = Color.rgb(17, 17, 17);
    private static final int BLUE = Color.rgb(80, 140, 255);
    private static final int RED_HEART = Color.rgb(255, 64, 80);
    private static final int ORANGE_DOT = Color.rgb(255, 140, 0);
    private static final int BRAND = Color.rgb(46, 166, 127); // #2ea67f
    private static final int BTN_GRAY = Color.rgb(95, 99, 104); // #5f6368 — satu-satunya warna tombol (v1.6)
    private static final int SURFACE = Color.rgb(28, 28, 28);
    private static final int SURFACE2 = Color.rgb(40, 40, 40);
    private static final int STROKE_GRAY = Color.rgb(68, 68, 68);

    // Font Awesome 6 Free Solid (fa-solid-900.ttf, OFL 1.1) — kode private-use.
    private static final String FA_CHEVRON_LEFT = "\uf053";
    private static final String FA_SEARCH = "\uf002";
    private static final String FA_ELLIPSIS_V = "\uf142";
    private static final String FA_PENCIL = "\uf303";
    private static final String FA_INFO = "\uf129";
    private static final String FA_SHARE = "\uf1e0";
    private static final String FA_IMAGE = "\uf03e";
    private static final String FA_FOLDER = "\uf07b";
    private static final String FA_GLOBE = "\uf0ac";
    private static final String FA_XMARK = "\uf00d";
    private static final String FA_COPY = "\uf0c5";
    private static final String FA_STAMP = "\uf5a5";
    private static final String FA_FILE_ARCHIVE = "\uf1c6";
    private static final String FA_CLOCK = "\uf017";
    private static final String FA_ROTATE_RIGHT = "\uf2f1";
    private static final String FA_DOWNLOAD = "\uf019";
    private static final String FA_FLOPPY = "\uf0c7";
    private static final String FA_ARROWS_ROTATE = "\uf021";
    private static final String FA_LOCATION_DOT = "\uf601";
    private static final String FA_MAP_PIN = "\uf041";
    private static final String FA_CALENDAR = "\uf073";
    private static final String FA_CHECK = "\uf00c";
    private static final String FA_LOCK = "\uf023";
    private static final String FA_PLUS = "\uf067";
    private static final String FA_HEART = "\uf004";

    private static class PhotoItem {
        long id;
        Uri uri;
        String name;
        String bucket;
        String relPath;
        long dateAdded;
        long size;
        int width;
        int height;
        String mime;
        boolean isVideo;
    }

    private static class Album {
        String name;
        ArrayList<PhotoItem> items = new ArrayList<PhotoItem>();
        PhotoItem cover() { return items.isEmpty() ? null : items.get(0); }
        int imageCount() {
            int n = 0;
            for (PhotoItem p : items) if (!p.isVideo) n++;
            return n;
        }
        int videoCount() {
            int n = 0;
            for (PhotoItem p : items) if (p.isVideo) n++;
            return n;
        }
    }

    /** ImageView dengan sudut membulat (kartu album). */
    private static class RoundedImageView extends ImageView {
        private float radius;
        RoundedImageView(Context ctx, float radiusDp) {
            super(ctx);
            radius = radiusDp * ctx.getResources().getDisplayMetrics().density;
        }
        @Override protected void onDraw(Canvas canvas) {
            Path path = new Path();
            path.addRoundRect(new RectF(0, 0, getWidth(), getHeight()),
                    radius, radius, Path.Direction.CW);
            canvas.clipPath(path);
            super.onDraw(canvas);
        }
    }

    private LinearLayout container;
    private LinearLayout bottomNav;
    private LinearLayout navGambar, navAlbum, navBrowser;
    private TextView navGambarIcon, navAlbumIcon, navBrowserIcon;
    private TextView navGambarLabel, navAlbumLabel, navBrowserLabel;
    private Typeface faTypeface;
    private int currentTab = 0; // 0 galeri, 1 browser
    // screen: album | grid | viewer | edit | copy | wm
    private String screen = "album";

    private ArrayList<PhotoItem> allMedia = new ArrayList<PhotoItem>();
    private ArrayList<Album> albums = new ArrayList<Album>();
    private Album currentAlbum;
    private ArrayList<PhotoItem> currentList = new ArrayList<PhotoItem>();
    private ArrayList<PhotoItem> gridFiltered = new ArrayList<PhotoItem>();
    private String currentQuery = "";
    // v1.7: pencarian grid 3 mode — 0 Nama, 1 Tanggal, 2 Hari (saling mengganti).
    private int searchMode = 0;
    private long filterDateMillis = -1; // awal hari yang dipilih (millis)
    private int filterDayOfWeek = -1; // Calendar.DAY_OF_WEEK yang dipilih
    private static final String[] DAY_NAMES_ID = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
    private EditText gridSearchBox;
    private LinearLayout gridDateField, gridSearchWrap, gridDateWrap, gridDayWrap;
    private TextView gridDateFieldLabel;
    private Spinner gridModeSpinner, gridDaySpinner;
    private TextView gridDateFieldClear;
    private TextView gridSubView;
    private PhotoGridAdapter gridAdapterView;
    private boolean showAllAlbums = false; // v1.5: Album penting ringkas / semua
    private int currentIndex = 0;

    private LruCache<String, Bitmap> thumbCache;
    private ExecutorService thumbPool = Executors.newFixedThreadPool(4);

    private PhotoItem currentPhoto;
    private ExifUtil.ExifData currentExif;
    private ExifUtil.ExifData copySourceExif;
    private String copySourceName = "";

    // Viewer
    private FrameLayout viewerRoot;
    private ImageView viewerImage;
    private LinearLayout filmstripRow;
    private HorizontalScrollView filmstripScroll;
    private LinearLayout detailPanel;
    private LinearLayout editSheet;
    private boolean detailOpen = false;
    private TextView favBtn; // hati Favorit (v1.5), pertama di bar bawah viewer
    private DetailMapView detailMapView; // peta OSM di panel detail (v1.5)
    private TextView detailAddrView; // alamat di bawah peta (v1.5)
    private HashMap<Long, String> detailAddrCache = new HashMap<Long, String>();
    private Bitmap detailMapBitmap;
    private long detailMapPhotoId = -1;
    private float touchDownX, touchDownY;

    // Form edit EXIF
    private EditText eLat, eLon, eAlt, eDate, eTime, eMake, eModel;
    // v1.6: peta + cari alamat di form Edit EXIF.
    private ImageView editMapView;
    private TextView editAddrText;
    private LinearLayout editSearchResults;
    private EditText editSearchBox;
    private double editMapLat = Double.NaN, editMapLon = Double.NaN;
    private int editMapW = 0;
    // Form salin EXIF
    private TextView copySummary;
    // Form watermark (v1.3, mengikuti Stempel Foto GPS Web)
    private boolean wmIsJob = false;
    private EditText wmLat, wmLon, wmPlace, wmAddress, wmDate, wmTime, wmWeatherText, wmTemp;
    private Spinner wmCond;
    private SeekBar wmScale;
    private SeekBar wmJobTrans;
    private TextView wmScaleVal, wmJobTransVal, wmDtHelp, wmInfo;
    private TextView wmPillMap, wmPillAddr, wmPillCoord, wmPillLogo, wmPillFlag, wmPillWeather;
    private LinearLayout wmCardKlasik, wmCardJob;
    private View wmDotKlasik, wmDotJob;
    private ArrayList<View> wmClassicOnly = new ArrayList<View>();
    private ArrayList<View> wmJobOnly = new ArrayList<View>();
    private boolean wmShowMap = true, wmShowAddr = true, wmShowCoord = true;
    private boolean wmShowLogo = true, wmShowFlag = true, wmShowWeather = true;
    private int wmScalePct = 100;
    private int wmJobTransPct = 40; // v1.8: transparansi strip Job, 0-90 (bawaan 40 = v1.5)
    private int wmRotation = 0;
    // Pratinjau watermark (v1.2)
    private ImageView wmPreview;
    private TextView wmPreviewStatus;
    private Bitmap wmPreviewBitmap;
    private Handler wmPreviewHandler = new Handler();
    private Runnable wmPreviewRunnable;
    private boolean wmPreviewRunning = false;
    private boolean wmAutoGeoDone = false;
    private TextView wmGeoStatus;

    // Browser (universal, tanpa URL hardcode) — v1.4: multi-tab ala Chrome.
    private static class BrowserTab {
        WebView webView;
        String url;
        String title;
        ValueCallback<Uri[]> fileCallback;
        String lastErrorUrl;
        long lastErrorAt;
    }
    private static final String BROWSER_START_HTML =
            "<html><body style='background:#000;color:#fff;font-family:sans-serif;padding:24px'>"
            + "<h3>Galeriku Browser</h3>"
            + "<p>Ketik alamat situs atau kata kunci di bar alamat.</p>"
            + "<p>Upload foto lewat browser ini memakai pemilih Dokumen (SAF), "
            + "jadi GPS foto tidak hilang seperti di Chrome.</p></body></html>";
    private LinearLayout browserRoot;
    private LinearLayout browserContent;
    private ArrayList<BrowserTab> browserTabs = new ArrayList<BrowserTab>();
    private int activeTabIndex = -1;
    private boolean showingTabList = false;
    private EditText urlInput;
    private TextView urlBarIcon;
    private TextView tabCountBtn;
    private ProgressBar progressBar;

    public class BlobHandler {
        @JavascriptInterface
        public void save(final String dataUrl) {
            new Thread(new Runnable() {
                @Override public void run() { saveDataUrl(dataUrl); }
            }).start();
        }
        @JavascriptInterface
        public void error(final String message) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Toast.makeText(MainActivity.this, "Unduhan gagal: " + message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int maxMemKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        thumbCache = new LruCache<String, Bitmap>(maxMemKb / 8) {
            @Override protected int sizeOf(String k, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };

        try {
            faTypeface = Typeface.createFromAsset(getAssets(), "fonts/fa-solid-900.ttf");
        } catch (Exception ignored) {
            faTypeface = null;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(BLACK);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(container, cLp);

        buildBottomNav();
        root.addView(bottomNav);

        setContentView(root);
        buildBrowserView();
        restoreBrowserTabs(savedInstanceState);
        requestPermissions();
        loadAllMedia();
        showAlbum();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Tinggi status bar sistem (fallback 28dp bila identifier tidak ada). */
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            try { return getResources().getDimensionPixelSize(id); } catch (Exception ignored) {}
        }
        return dp(28);
    }

    /** TextView ikon Font Awesome (bukan glyph Unicode sistem). */
    private TextView makeIcon(String faCode, float sp) {
        TextView t = new TextView(this);
        t.setText(faCode);
        t.setTextSize(sp);
        t.setTextColor(WHITE);
        t.setGravity(Gravity.CENTER);
        if (faTypeface != null) t.setTypeface(faTypeface);
        return t;
    }
    private TextView makeIcon(String faCode) { return makeIcon(faCode, 24); }

    private void buildBottomNav() {
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(BLACK);
        bottomNav.setPadding(0, dp(8), 0, dp(8));
        navGambar = makeNavItem(FA_IMAGE, "Gambar");
        navGambar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openTerkini(); }
        });
        navAlbum = makeNavItem(FA_FOLDER, "Album");
        navAlbum.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAlbum(); }
        });
        navBrowser = makeNavItem(FA_GLOBE, "Browser");
        navBrowser.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(1); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bottomNav.addView(navGambar, lp);
        bottomNav.addView(navAlbum, lp);
        bottomNav.addView(navBrowser, lp);
    }

    private LinearLayout makeNavItem(String icon, String text) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView ic = makeIcon(icon, 20);
        ic.setTextColor(GRAY);
        item.addView(ic);
        TextView lb = new TextView(this);
        lb.setText(text);
        lb.setTextSize(12);
        lb.setGravity(Gravity.CENTER);
        lb.setTextColor(GRAY);
        item.addView(lb);
        if ("Gambar".equals(text)) { navGambarIcon = ic; navGambarLabel = lb; }
        else if ("Album".equals(text)) { navAlbumIcon = ic; navAlbumLabel = lb; }
        else { navBrowserIcon = ic; navBrowserLabel = lb; }
        return item;
    }

    private void setNavActive(LinearLayout active) {
        LinearLayout[] items = new LinearLayout[]{navGambar, navAlbum, navBrowser};
        TextView[] icons = new TextView[]{navGambarIcon, navAlbumIcon, navBrowserIcon};
        TextView[] labels = new TextView[]{navGambarLabel, navAlbumLabel, navBrowserLabel};
        for (int i = 0; i < items.length; i++) {
            boolean on = (items[i] == active);
            if (icons[i] != null) icons[i].setTextColor(on ? WHITE : GRAY);
            if (labels[i] != null) {
                labels[i].setTextColor(on ? WHITE : GRAY);
                labels[i].setTypeface(null, on ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void switchTab(int tab) {
        currentTab = tab;
        container.removeAllViews();
        if (tab == 1) {
            screen = "browser";
            setNavActive(navBrowser);
            bottomNav.setVisibility(View.VISIBLE);
            showActiveTab();
            container.addView(browserRoot, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            showAlbum();
        }
    }

    private void openTerkini() {
        Album t = findAlbum("Terkini");
        if (t != null) showGrid(t);
        else showAlbum();
    }

    private Album findAlbum(String name) {
        for (Album a : albums) if (a.name.equals(name)) return a;
        return null;
    }

    // ---------- helper UI (tema gelap) ----------
    /** SATU gaya baku semua Button (v1.6): abu-abu membulat radius 14dp,
     *  teks putih tebal, lebar penuh. Tidak ada lagi button hijau. */
    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(WHITE);
        b.setTextSize(15);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackground(roundedBg(BTN_GRAY, 0, 0, 14));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }
    private TextView makeLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(WHITE);
        return t;
    }
    private EditText makeEdit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(GRAY);
        e.setTextColor(WHITE);
        e.setTextSize(14);
        return e;
    }
    private LinearLayout makeBackButton(final Runnable back) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(makeIcon(FA_CHEVRON_LEFT, 18));
        TextView label = makeLabel("Kembali");
        label.setPadding(dp(8), 0, 0, 0);
        row.addView(label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { back.run(); }
        });
        return row;
    }
    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show(); }
        });
    }

    // ---------- izin ----------
    private void requestPermissions() {
        ArrayList<String> perms = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        perms.add(android.Manifest.permission.ACCESS_MEDIA_LOCATION);
        perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        ArrayList<String> missing = new ArrayList<String>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERM);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        loadAllMedia();
        if (currentTab == 0) showAlbum();
    }

    // ---------- muat media: foto + video, kolom seperlunya ----------
    private void loadAllMedia() {
        allMedia.clear();
        try {
            String[] proj = new String[]{
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.MediaColumns.MIME_TYPE};
            Cursor c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                    null, null, null);
            fillFromCursor(c, false, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            Cursor v = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
                    null, null, null);
            fillFromCursor(v, true, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membaca galeri: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        Collections.sort(allMedia, new Comparator<PhotoItem>() {
            @Override public int compare(PhotoItem a, PhotoItem b) {
                return Long.compare(b.dateAdded, a.dateAdded);
            }
        });
        buildAlbums();
    }

    private void fillFromCursor(Cursor c, boolean isVideo, Uri base) {
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                PhotoItem p = new PhotoItem();
                p.id = c.getLong(0);
                p.name = c.getString(1);
                p.bucket = c.getString(2);
                p.relPath = c.getString(3);
                p.dateAdded = c.getLong(4);
                p.size = c.getLong(5);
                p.width = c.getInt(6);
                p.height = c.getInt(7);
                p.mime = c.getString(8);
                p.isVideo = isVideo;
                p.uri = Uri.withAppendedPath(base, String.valueOf(p.id));
                allMedia.add(p);
            }
        } finally {
            c.close();
        }
    }

    private void buildAlbums() {
        albums.clear();
        Album terkini = new Album();
        terkini.name = "Terkini";
        terkini.items.addAll(allMedia);
        albums.add(terkini);
        // v1.5: Favorit — foto yang ditandai hati di viewer (persisten di prefs).
        Album fav = new Album();
        fav.name = "Favorit";
        HashSet<Long> favIds = favoriteIds();
        for (PhotoItem p : allMedia) if (favIds.contains(p.id)) fav.items.add(p);
        albums.add(fav);
        LinkedHashMap<String, Album> byBucket = new LinkedHashMap<String, Album>();
        for (PhotoItem p : allMedia) {
            String b = (p.bucket == null || p.bucket.isEmpty()) ? "Lainnya" : p.bucket;
            Album a = byBucket.get(b);
            if (a == null) {
                a = new Album();
                a.name = b;
                byBucket.put(b, a);
            }
            a.items.add(p);
        }
        ArrayList<Album> rest = new ArrayList<Album>(byBucket.values());
        Collections.sort(rest, new Comparator<Album>() {
            @Override public int compare(Album a, Album b) {
                return Integer.compare(b.items.size(), a.items.size());
            }
        });
        albums.addAll(rest);
        // v1.5: album buatan user — folder DCIM/<nama>, nama tersimpan di prefs.
        for (String name : userAlbumNames()) {
            Album ua = new Album();
            ua.name = name;
            String rel = Environment.DIRECTORY_DCIM + "/" + name + "/";
            for (PhotoItem p : allMedia) {
                if (rel.equals(p.relPath)) ua.items.add(p);
            }
            albums.add(ua);
        }
    }

    // ---------- preferensi: album buatan user + favorit (v1.5) ----------

    private SharedPreferences prefs() {
        return getSharedPreferences("galeriku", MODE_PRIVATE);
    }

    /** Nama album buatan user (disimpan sebagai daftar dipisah baris baru). */
    private ArrayList<String> userAlbumNames() {
        ArrayList<String> out = new ArrayList<String>();
        String raw = prefs().getString("user_albums", "");
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split("\n")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void saveUserAlbumNames(ArrayList<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(names.get(i));
        }
        prefs().edit().putString("user_albums", sb.toString()).apply();
    }

    /** ID MediaStore foto favorit (disimpan sebagai StringSet). */
    private HashSet<Long> favoriteIds() {
        HashSet<Long> out = new HashSet<Long>();
        Set<String> s = prefs().getStringSet("favorites", null);
        if (s != null) {
            for (String v : s) {
                try { out.add(Long.parseLong(v)); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    private boolean isFavorite(long id) {
        return favoriteIds().contains(id);
    }

    private void setFavorite(long id, boolean fav) {
        HashSet<Long> ids = favoriteIds();
        if (fav) ids.add(id); else ids.remove(id);
        HashSet<String> s = new HashSet<String>();
        for (Long v : ids) s.add(String.valueOf(v));
        prefs().edit().putStringSet("favorites", s).apply();
        loadAllMedia(); // bangun ulang album Favorit
    }

    private void updateFavBtn() {
        if (favBtn == null || currentPhoto == null) return;
        favBtn.setTextColor(isFavorite(currentPhoto.id) ? RED_HEART : WHITE);
    }

    private void reloadAndShowGrid() {
        String name = currentAlbum == null ? "Terkini" : currentAlbum.name;
        loadAllMedia();
        Album a = findAlbum(name);
        if (a != null) showGrid(a);
        else showAlbum();
    }

    // ---------- thumbnail lazy + cache ----------
    private void loadThumbAsync(final ImageView iv, final PhotoItem p, final int px) {
        final String key = p.uri.toString() + "_" + px;
        Bitmap cached = thumbCache.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setTag(key);
        iv.setImageBitmap(null);
        iv.setBackgroundColor(Color.rgb(30, 30, 30));
        thumbPool.execute(new Runnable() {
            @Override public void run() {
                final Bitmap b = loadThumb(p.uri, px);
                if (b != null) thumbCache.put(key, b);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (key.equals(iv.getTag()) && b != null) {
                            iv.setBackgroundColor(Color.TRANSPARENT);
                            iv.setImageBitmap(b);
                        }
                    }
                });
            }
        });
    }

    private Bitmap loadThumb(Uri uri, int px) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return getContentResolver().loadThumbnail(uri,
                        new android.util.Size(px, px), null);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            InputStream in = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            return b;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap decodeSampled(Uri uri, int maxDim) {
        try {
            Uri readUri = uri;
            if (Build.VERSION.SDK_INT >= 29) {
                try { readUri = MediaStore.setRequireOriginal(uri); } catch (Exception ignored) {}
            }
            InputStream in = getContentResolver().openInputStream(readUri);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            if (in != null) in.close();
            int sample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / sample > maxDim) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            InputStream in2 = getContentResolver().openInputStream(readUri);
            Bitmap b = BitmapFactory.decodeStream(in2, null, o);
            if (in2 != null) in2.close();
            return b;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap decodeFull(Uri uri) {
        try {
            Uri readUri = uri;
            if (Build.VERSION.SDK_INT >= 29) {
                try { readUri = MediaStore.setRequireOriginal(uri); } catch (Exception ignored) {}
            }
            InputStream in = getContentResolver().openInputStream(readUri);
            Bitmap b = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            return b;
        } catch (OutOfMemoryError e) {
            return decodeSampled(uri, 2000);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---------- layar ALBUM (awal) ----------
    private void showAlbum() {
        screen = "album";
        currentTab = 0;
        setNavActive(navAlbum);
        bottomNav.setVisibility(View.VISIBLE);
        container.removeAllViews();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(16), statusBarHeight() + dp(12), dp(16), dp(8));

        // Bar atas: tambah album (+) + cari + menu.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.END);
        TextView addBtn = makeIcon(FA_PLUS, 22);
        addBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddAlbumDialog(); }
        });
        TextView searchBtn = makeIcon(FA_SEARCH, 22);
        searchBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextView menuBtn = makeIcon(FA_ELLIPSIS_V, 22);
        menuBtn.setPadding(dp(12), dp(4), dp(4), dp(4));
        top.addView(addBtn);
        top.addView(searchBtn);
        top.addView(menuBtn);
        root.addView(top);

        // Header "Album penting" + "Lihat semua" ala Samsung (v1.5).
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0, dp(8), 0, dp(8));
        TextView hdrTitle = makeLabel("Album penting");
        hdrTitle.setTextSize(20);
        hdrTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hdr.addView(hdrTitle, hlp);
        final TextView seeAll = new TextView(this);
        seeAll.setText(showAllAlbums ? "Sembunyikan" : "Lihat semua");
        seeAll.setTextSize(14);
        seeAll.setTextColor(BLUE);
        hdr.addView(seeAll);
        final TextView dot = new TextView(this);
        dot.setText("\u25CF");
        dot.setTextSize(8);
        dot.setTextColor(ORANGE_DOT);
        dot.setPadding(dp(2), 0, 0, 0);
        dot.setVisibility(showAllAlbums ? View.GONE : View.VISIBLE);
        hdr.addView(dot);
        View.OnClickListener toggleAll = new View.OnClickListener() {
            @Override public void onClick(View v) {
                showAllAlbums = !showAllAlbums;
                showAlbum();
            }
        };
        seeAll.setOnClickListener(toggleAll);
        dot.setOnClickListener(toggleAll);
        root.addView(hdr);

        final EditText searchBox = makeEdit("Cari album...");
        searchBox.setVisibility(View.GONE);
        searchBox.setText(currentQuery);
        root.addView(searchBox);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = searchBox.getVisibility() != View.VISIBLE;
                searchBox.setVisibility(show ? View.VISIBLE : View.GONE);
                if (!show) { currentQuery = ""; searchBox.setText(""); showAlbum(); }
            }
        });
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                loadAllMedia();
                showAlbum();
                Toast.makeText(MainActivity.this, "Galeri disegarkan", Toast.LENGTH_SHORT).show();
            }
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentQuery = s.toString();
                showAlbumGridFiltered(root, searchBox);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        showAlbumGridFiltered(root, searchBox);
        container.addView(root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private GridView albumGridView;

    private void showAlbumGridFiltered(LinearLayout root, EditText searchBox) {
        if (albumGridView != null) root.removeView(albumGridView);
        final ArrayList<Album> shown = new ArrayList<Album>();
        String q = currentQuery == null ? "" : currentQuery.toLowerCase(Locale.US);
        if (q.isEmpty() && !showAllAlbums) {
            // v1.5 ringkas: Terkini, Favorit, Kamera, Unduh + 2 album berikutnya.
            for (int i = 0; i < Math.min(6, albums.size()); i++) shown.add(albums.get(i));
        } else {
            for (Album a : albums) {
                if (!q.isEmpty() && !a.name.toLowerCase(Locale.US).contains(q)) continue;
                shown.add(a);
            }
        }
        albumGridView = new GridView(this);
        albumGridView.setNumColumns(2);
        albumGridView.setVerticalSpacing(dp(12));
        albumGridView.setHorizontalSpacing(dp(12));
        albumGridView.setBackgroundColor(BLACK);
        final AlbumAdapter adapter = new AlbumAdapter(shown);
        albumGridView.setAdapter(adapter);
        albumGridView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                currentQuery = "";
                showGrid(shown.get(pos));
            }
        });
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(albumGridView, gLp);
    }

    private class AlbumAdapter extends BaseAdapter {
        private ArrayList<Album> list;
        AlbumAdapter(ArrayList<Album> list) { this.list = list; }
        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int pos) { return list.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(BLACK);
            Album a = list.get(pos);
            // Bingkai cover: foto, atau placeholder abu-abu + ikon bila kosong (v1.5).
            FrameLayout frame = new FrameLayout(MainActivity.this);
            frame.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
            RoundedImageView iv = new RoundedImageView(MainActivity.this, 28);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(iv);
            PhotoItem cover = a.cover();
            if (cover != null) loadThumbAsync(iv, cover, 480);
            else {
                iv.setBackgroundColor(Color.rgb(40, 40, 40));
                TextView ph = makeIcon("Favorit".equals(a.name) ? FA_HEART : FA_IMAGE, 40);
                ph.setTextColor(Color.rgb(90, 90, 90));
                FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                plp.gravity = Gravity.CENTER;
                frame.addView(ph, plp);
            }
            card.addView(frame);
            TextView name = makeLabel(a.name);
            name.setTextSize(17);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, dp(8), 0, 0);
            card.addView(name);
            TextView count = new TextView(MainActivity.this);
            count.setText(String.valueOf(a.items.size()));
            count.setTextSize(14);
            count.setTextColor(GRAY);
            card.addView(count);
            return card;
        }
    }

    // ---------- grid folder (4 kolom) ----------
    private void showGrid(final Album album) {
        screen = "grid";
        currentTab = 0;
        currentAlbum = album;
        currentList = album.items;
        // v1.7: masuk grid baru — pencarian kembali ke mode Nama tanpa filter.
        searchMode = 0;
        currentQuery = "";
        filterDateMillis = -1;
        filterDayOfWeek = -1;
        setNavActive(navGambar);
        bottomNav.setVisibility(View.VISIBLE);
        container.removeAllViews();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(12), statusBarHeight() + dp(12), dp(12), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = makeIcon(FA_CHEVRON_LEFT, 22);
        back.setPadding(dp(4), dp(4), dp(16), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                currentQuery = "";
                filterDateMillis = -1;
                filterDayOfWeek = -1;
                searchMode = 0;
                showAlbum();
            }
        });
        head.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = makeLabel(album.name);
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(title);
        final TextView sub = new TextView(this);
        sub.setTextSize(14);
        sub.setTextColor(GRAY);
        titles.addView(sub);
        head.addView(titles);
        root.addView(head);

        // v1.11: SATU baris pencarian 70:30 — [input sesuai mode] + [dropdown mode].
        // Seluruh baris kini field/dropdown polos bergaris bawah, tanpa pill abu-abu.
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 7f);
        inputLp.setMargins(0, dp(8), dp(8), dp(4));
        LinearLayout.LayoutParams inputLp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 7f);
        inputLp2.setMargins(0, dp(8), dp(8), dp(4));
        LinearLayout.LayoutParams inputLp3 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 7f);
        inputLp3.setMargins(0, dp(8), dp(8), dp(4));
        LinearLayout.LayoutParams dropLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 3f);
        dropLp.setMargins(0, dp(8), 0, dp(4));

        // KIRI 70% — Mode Nama: kolom teks polos (latar transparan + garis bawah).
        gridSearchBox = makeEdit("Cari nama file...");
        gridSearchBox.setText(currentQuery);
        gridSearchBox.setTextSize(16);
        gridSearchBox.setMinimumHeight(dp(48));
        gridSearchBox.setBackground(null);
        gridSearchWrap = plainFieldWrap(gridSearchBox);
        searchRow.addView(gridSearchWrap, inputLp);

        // KIRI 70% — Mode Tanggal: field polos (ikon + tanggal / "Pilih tanggal" + ✕).
        gridDateField = makeGridValueField(FA_CALENDAR);
        gridDateFieldLabel = (TextView) gridDateField.getChildAt(1);
        gridDateFieldClear = (TextView) gridDateField.getChildAt(2);
        gridDateField.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openGridDatePicker(); }
        });
        gridDateFieldClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                filterDateMillis = -1;
                updateGridDateField();
                applyGridFilter(gridSubView, gridAdapterView);
            }
        });
        gridDateField.setMinimumHeight(dp(48));
        gridDateWrap = plainFieldWrap(gridDateField);
        gridDateWrap.setVisibility(View.GONE);
        searchRow.addView(gridDateWrap, inputLp2);

        // KIRI 70% — Mode Hari: dropdown biasa (Spinner standar, transparan + garis bawah).
        // Bukan lagi pill abu-abu + bottom sheet; "Pilih hari" = tanpa filter.
        gridDaySpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        gridDaySpinner.setMinimumHeight(dp(48));
        final String[] dayNames = {"Pilih hari", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"};
        final int[] dayOrder = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, dayNames) {
            @Override public View getView(int pos, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convertView, parent);
                t.setTextColor(pos == 0 ? GRAY : WHITE);
                t.setTextSize(16);
                t.setTypeface(null, android.graphics.Typeface.BOLD);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                return t;
            }
            @Override public View getDropDownView(int pos, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, convertView, parent);
                t.setTextColor(pos == 0 ? GRAY : WHITE);
                t.setTextSize(18);
                t.setBackgroundColor(0xFF2C2C2E);
                t.setPadding(dp(16), dp(14), dp(16), dp(14));
                t.setMinHeight(dp(48));
                t.setTypeface(null, pos == 0 || gridDaySpinner.getSelectedItemPosition() == pos
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                return t;
            }
        };
        gridDaySpinner.setAdapter(dayAdapter);
        gridDaySpinner.setPopupBackgroundDrawable(new ColorDrawable(0xFF2C2C2E));
        android.graphics.drawable.Drawable dayBg = gridDaySpinner.getBackground();
        if (dayBg != null) {
            dayBg = dayBg.mutate();
            dayBg.setColorFilter(WHITE, PorterDuff.Mode.SRC_IN);
            gridDaySpinner.setBackground(dayBg);
        }
        gridDaySpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view, int pos, long id) {
                int newDow = pos == 0 ? -1 : dayOrder[pos - 1];
                if (newDow != filterDayOfWeek) {
                    filterDayOfWeek = newDow;
                    applyGridFilter(gridSubView, gridAdapterView);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        gridDayWrap = plainFieldWrap(gridDaySpinner);
        gridDayWrap.setVisibility(View.GONE);
        searchRow.addView(gridDayWrap, inputLp3);

        // v1.10: KANAN 30% — Spinner STANDAR Android (MODE_DROPDOWN), bukan button:
        // latar transparan, teks putih mode aktif, panah bawaan Spinner, garis
        // bawah tipis abu-abu. Daftar dropdown menempel di bawah Spinner.
        // v1.11: lebar 30% (dulu 40%). v1.12: teks field naik ke 16sp
        // ("Tanggal" tetap muat di 30%), item dropdown 18sp, tinggi min 48dp,
        // popup mode diperlebar ke 50% lebar layar (dropdown terlalu kecil).
        LinearLayout modeWrap = new LinearLayout(this);
        modeWrap.setOrientation(LinearLayout.VERTICAL);
        modeWrap.setGravity(Gravity.CENTER_VERTICAL);
        gridModeSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        gridModeSpinner.setMinimumHeight(dp(48));
        // v1.12: popup mode lebih lebar dari field 30% (50% lebar layar),
        // digeser ke kiri supaya tepi kanan popup sejajar tepi kanan field.
        int sw = getResources().getDisplayMetrics().widthPixels;
        int modePopupW = sw / 2;
        int modeSpinnerW = (int) ((sw - dp(24) - dp(8)) * 0.30);
        gridModeSpinner.setDropDownWidth(modePopupW);
        gridModeSpinner.setDropDownHorizontalOffset(-(modePopupW - modeSpinnerW));
        final String[] modeNames = {"Nama", "Tanggal", "Hari"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, modeNames) {
            @Override public View getView(int pos, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convertView, parent);
                t.setTextColor(WHITE);
                t.setTextSize(16);
                t.setTypeface(null, android.graphics.Typeface.BOLD);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                return t;
            }
            @Override public View getDropDownView(int pos, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, convertView, parent);
                t.setTextColor(WHITE);
                t.setTextSize(18);
                t.setBackgroundColor(0xFF2C2C2E);
                t.setPadding(dp(16), dp(14), dp(16), dp(14));
                t.setMinHeight(dp(48));
                t.setTypeface(null, pos == searchMode
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                return t;
            }
        };
        gridModeSpinner.setAdapter(modeAdapter);
        // Popup dropdown gelap supaya teks putih terbaca.
        gridModeSpinner.setPopupBackgroundDrawable(new ColorDrawable(0xFF2C2C2E));
        // Panah bawaan Spinner di tema terang berwarna abu-abu gelap — putihkan
        // supaya terlihat di latar hitam (tanpa mengganti background bawaan).
        android.graphics.drawable.Drawable spBg = gridModeSpinner.getBackground();
        if (spBg != null) {
            spBg = spBg.mutate();
            spBg.setColorFilter(WHITE, PorterDuff.Mode.SRC_IN);
            gridModeSpinner.setBackground(spBg);
        }
        gridModeSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view, int pos, long id) {
                if (pos != searchMode) setGridSearchMode(pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        modeWrap.addView(gridModeSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View modeUnderline = new View(this);
        modeUnderline.setBackgroundColor(BTN_GRAY);
        modeWrap.addView(modeUnderline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        searchRow.addView(modeWrap, dropLp);
        root.addView(searchRow);

        final GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(3));
        grid.setHorizontalSpacing(dp(3));
        grid.setBackgroundColor(BLACK);
        final PhotoGridAdapter adapter = new PhotoGridAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                PhotoItem p = gridFiltered.get(pos);
                if (p.isVideo) {
                    playVideo(p);
                } else {
                    showViewer(gridFiltered, pos);
                }
            }
        });
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(grid, gLp);
        container.addView(root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        gridSubView = sub;
        gridAdapterView = adapter;
        gridSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                currentQuery = s.toString();
                applyGridFilter(sub, adapter);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateGridModeDropdown();
        updateGridDateField();
        updateGridDayField();
        applyGridFilter(sub, adapter);
    }

    private void applyGridFilter(TextView sub, PhotoGridAdapter adapter) {
        gridFiltered.clear();
        String q = currentQuery == null ? "" : currentQuery.toLowerCase(Locale.US);
        int imgs = 0, vids = 0;
        int totalImgs = 0, totalVids = 0;
        for (PhotoItem p : currentList) {
            if (p.isVideo) totalVids++; else totalImgs++;
            if (!q.isEmpty() && (p.name == null || !p.name.toLowerCase(Locale.US).contains(q))) continue;
            if (filterDateMillis >= 0 && !sameDay(p.dateAdded, filterDateMillis)) continue;
            if (filterDayOfWeek > 0 && photoDayOfWeek(p.dateAdded) != filterDayOfWeek) continue;
            gridFiltered.add(p);
            if (p.isVideo) vids++; else imgs++;
        }
        if (sub != null) {
            boolean filtered = !q.isEmpty() || filterDateMillis >= 0 || filterDayOfWeek > 0;
            String s;
            if (gridFiltered.isEmpty() && filtered) {
                s = "Tidak ada foto";
                if (filterDateMillis >= 0) s += " pada " + fmtGridDate(filterDateMillis);
                else if (filterDayOfWeek > 0) s += " pada hari " + DAY_NAMES_ID[filterDayOfWeek - 1];
                else s += " untuk \"" + currentQuery + "\"";
            } else {
                s = imgs + " gambar";
                if (vids > 0) s += " " + vids + " video";
                if (filtered) {
                    s += " dari " + totalImgs + " gambar";
                    if (totalVids > 0) s += " " + totalVids + " video";
                    if (filterDateMillis >= 0) s += " \u00b7 " + fmtGridDate(filterDateMillis);
                    else if (filterDayOfWeek > 0) s += " \u00b7 " + DAY_NAMES_ID[filterDayOfWeek - 1];
                }
            }
            sub.setText(s);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ---------- pencarian grid 3 mode (v1.7; UI 60:40 + dropdown v1.9) ----------

    /** Field nilai tampil untuk mode Tanggal/Hari: [ikon] [label, lebar 1] [✕].
     *  Latar abu-abu membulat radius 14dp seperti field dropdown mode. */
    private LinearLayout makeGridValueField(String fa) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setBackground(null);
        f.setPadding(dp(2), dp(10), dp(2), dp(10));
        f.addView(makeIcon(fa, 16));
        TextView label = new TextView(this);
        label.setTextColor(WHITE);
        label.setTextSize(16);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(8), 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        f.addView(label, lp);
        TextView x = makeIcon(FA_XMARK, 14);
        x.setPadding(dp(10), 0, 0, 0);
        x.setVisibility(View.GONE);
        f.addView(x);
        return f;
    }

    /** Bungkus field polos: latar transparan + garis bawah tipis 1dp (v1.11). */
    private LinearLayout plainFieldWrap(View child) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setGravity(Gravity.CENTER_VERTICAL);
        w.addView(child, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View u = new View(this);
        u.setBackgroundColor(BTN_GRAY);
        w.addView(u, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return w;
    }

    /** Pilihan Spinner mode mengikuti mode aktif (v1.10). */
    private void updateGridModeDropdown() {
        if (gridModeSpinner == null) return;
        if (gridModeSpinner.getSelectedItemPosition() != searchMode) {
            gridModeSpinner.setSelection(searchMode);
        }
    }

    /** Pindah mode pencarian — hanya satu filter aktif; filter mode lama dibersihkan. */
    private void setGridSearchMode(int mode) {
        searchMode = mode;
        if (mode != 0) currentQuery = "";
        if (mode != 1) filterDateMillis = -1;
        if (mode != 2) filterDayOfWeek = -1;
        if (gridSearchWrap != null) {
            gridSearchWrap.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
            if (mode == 0 && gridSearchBox != null) gridSearchBox.setText("");
        }
        if (gridDateWrap != null) gridDateWrap.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        if (gridDayWrap != null) gridDayWrap.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        updateGridModeDropdown();
        updateGridDateField();
        updateGridDayField();
        applyGridFilter(gridSubView, gridAdapterView);
    }

    /** DatePicker bawaan Android (tema perangkat) untuk mode Tanggal. */
    private void openGridDatePicker() {
        Calendar c = Calendar.getInstance();
        if (filterDateMillis >= 0) c.setTimeInMillis(filterDateMillis);
        DatePickerDialog dlg = new DatePickerDialog(this,
                android.R.style.Theme_DeviceDefault_Dialog,
                new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker view, int y, int m, int d) {
                        Calendar sel = Calendar.getInstance();
                        sel.set(y, m, d, 0, 0, 0);
                        sel.set(Calendar.MILLISECOND, 0);
                        filterDateMillis = sel.getTimeInMillis();
                        updateGridDateField();
                        applyGridFilter(gridSubView, gridAdapterView);
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    /** Isi field Tanggal: tanggal terpilih, atau "Pilih tanggal" bila belum ada. */
    private void updateGridDateField() {
        if (gridDateFieldLabel == null || gridDateFieldClear == null) return;
        if (filterDateMillis < 0) {
            gridDateFieldLabel.setText("Pilih tanggal");
            gridDateFieldLabel.setTextColor(GRAY);
            gridDateFieldClear.setVisibility(View.GONE);
            return;
        }
        gridDateFieldLabel.setText(fmtGridDate(filterDateMillis));
        gridDateFieldLabel.setTextColor(WHITE);
        gridDateFieldClear.setVisibility(View.VISIBLE);
    }

    /** Sinkronkan Spinner hari dengan filter aktif (v1.11): posisi 0 = "Pilih hari". */
    private void updateGridDayField() {
        if (gridDaySpinner == null) return;
        int pos = 0;
        if (filterDayOfWeek > 0) {
            int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
            for (int i = 0; i < order.length; i++) {
                if (order[i] == filterDayOfWeek) { pos = i + 1; break; }
            }
        }
        if (gridDaySpinner.getSelectedItemPosition() != pos) gridDaySpinner.setSelection(pos);
    }

    private String fmtGridDate(long millis) {
        return new SimpleDateFormat("dd MMM yyyy", new Locale("id")).format(new java.util.Date(millis));
    }

    /** Sama hari kalender? dateAdded MediaStore dalam detik, zona waktu perangkat. */
    private boolean sameDay(long dateAddedSec, long dayMillis) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(dateAddedSec * 1000L);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(dayMillis);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private int photoDayOfWeek(long dateAddedSec) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(dateAddedSec * 1000L);
        return a.get(Calendar.DAY_OF_WEEK);
    }

    private void playVideo(PhotoItem p) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(p.uri, "video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa memutar video: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class PhotoGridAdapter extends BaseAdapter {
        @Override public int getCount() { return gridFiltered.size(); }
        @Override public Object getItem(int pos) { return gridFiltered.get(pos); }
        @Override public long getItemId(int pos) { return gridFiltered.get(pos).id; }
        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            ImageView iv;
            if (convertView instanceof ImageView) {
                iv = (ImageView) convertView;
            } else {
                iv = new ImageView(MainActivity.this);
                iv.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            PhotoItem p = gridFiltered.get(pos);
            loadThumbAsync(iv, p, 320);
            return iv;
        }
    }

    // ---------- viewer ----------
    private void showViewer(ArrayList<PhotoItem> list, int index) {
        screen = "viewer";
        currentTab = 0;
        currentList = list;
        currentIndex = index;
        detailOpen = false;
        bottomNav.setVisibility(View.GONE);
        container.removeAllViews();

        viewerRoot = new FrameLayout(this);
        viewerRoot.setBackgroundColor(BLACK);

        // Foto tengah (swipe kiri/kanan = pindah, swipe atas = detail).
        viewerImage = new ImageView(this);
        viewerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewerImage.setBackgroundColor(BLACK);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imgLp.bottomMargin = dp(150);
        imgLp.topMargin = dp(56);
        viewerRoot.addView(viewerImage, imgLp);
        viewerImage.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    touchDownX = e.getX();
                    touchDownY = e.getY();
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dx = e.getX() - touchDownX;
                    float dy = e.getY() - touchDownY;
                    if (Math.abs(dx) > 120 && Math.abs(dx) > Math.abs(dy)) {
                        if (dx < 0) moveViewer(1);
                        else moveViewer(-1);
                    } else if (dy < -120 && Math.abs(dy) > Math.abs(dx)) {
                        openDetailPanel();
                    } else if (dy > 120 && Math.abs(dy) > Math.abs(dx)) {
                        if (detailOpen) closeDetailPanel();
                    } else {
                        // Ketuk: video diputar, foto tidak ada aksi.
                        PhotoItem p = currentList.get(currentIndex);
                        if (p.isVideo) playVideo(p);
                    }
                    return true;
                }
                return true;
            }
        });

        // Bar atas: hanya tombol kembali.
        TextView back = makeIcon(FA_CHEVRON_LEFT, 18);
        back.setPadding(dp(16), dp(12), dp(24), dp(12));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { backFromViewer(); }
        });
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.gravity = Gravity.TOP | Gravity.START;
        viewerRoot.addView(back, backLp);

        // Filmstrip bawah (scroll horizontal).
        filmstripScroll = new HorizontalScrollView(this);
        filmstripScroll.setHorizontalScrollBarEnabled(false);
        filmstripScroll.setBackgroundColor(BLACK);
        FrameLayout.LayoutParams fsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        fsLp.gravity = Gravity.BOTTOM;
        fsLp.bottomMargin = dp(48);
        filmstripRow = new LinearLayout(this);
        filmstripRow.setOrientation(LinearLayout.HORIZONTAL);
        filmstripRow.setPadding(dp(4), 0, dp(4), 0);
        filmstripScroll.addView(filmstripRow);
        viewerRoot.addView(filmstripScroll, fsLp);

        // Bar bawah: ikon (Hati, Edit, Info, Share). Tanpa tombol Hapus.
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(BLACK);
        FrameLayout.LayoutParams bbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        bbLp.gravity = Gravity.BOTTOM;
        favBtn = makeViewerIcon(FA_HEART);
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentPhoto == null) return;
                boolean fav = !isFavorite(currentPhoto.id);
                setFavorite(currentPhoto.id, fav);
                updateFavBtn();
                Toast.makeText(MainActivity.this,
                        fav ? "Ditambahkan ke Favorit" : "Dihapus dari Favorit",
                        Toast.LENGTH_SHORT).show();
            }
        });
        TextView editBtn = makeViewerIcon(FA_PENCIL);
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleEditSheet(); }
        });
        TextView infoBtn = makeViewerIcon(FA_INFO);
        infoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (detailOpen) closeDetailPanel(); else openDetailPanel();
            }
        });
        TextView shareBtn = makeViewerIcon(FA_SHARE);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showShareChooser(); }
        });
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        bottomBar.addView(favBtn, ibLp);
        bottomBar.addView(editBtn, ibLp);
        bottomBar.addView(infoBtn, ibLp);
        bottomBar.addView(shareBtn, ibLp);
        viewerRoot.addView(bottomBar, bbLp);

        // Panel detail (swipe-up), awalnya tersembunyi.
        detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setBackgroundColor(PANEL);
        detailPanel.setPadding(dp(20), dp(16), dp(20), dp(20));
        detailPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams dpLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dpLp.gravity = Gravity.BOTTOM;
        viewerRoot.addView(detailPanel, dpLp);
        detailPanel.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    touchDownY = e.getY();
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (e.getY() - touchDownY > 120) closeDetailPanel();
                    return true;
                }
                return true;
            }
        });

        // Lembar aksi Edit (4 pilihan), awalnya tersembunyi.
        editSheet = new LinearLayout(this);
        editSheet.setOrientation(LinearLayout.VERTICAL);
        editSheet.setBackgroundColor(PANEL);
        editSheet.setPadding(dp(20), dp(12), dp(20), dp(20));
        editSheet.setVisibility(View.GONE);
        FrameLayout.LayoutParams esLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        esLp.gravity = Gravity.BOTTOM;
        viewerRoot.addView(editSheet, esLp);
        buildEditSheet();

        container.addView(viewerRoot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadViewerPhoto();
    }

    private TextView makeViewerIcon(String faCode) {
        return makeIcon(faCode, 18);
    }

    private void backFromViewer() {
        if (detailOpen) { closeDetailPanel(); return; }
        if (editSheet != null && editSheet.getVisibility() == View.VISIBLE) {
            editSheet.setVisibility(View.GONE);
            return;
        }
        currentQuery = "";
        if (currentAlbum != null) showGrid(currentAlbum);
        else showAlbum();
    }

    private void moveViewer(int delta) {
        int next = currentIndex + delta;
        if (next < 0 || next >= currentList.size()) return;
        currentIndex = next;
        closeDetailPanel();
        if (editSheet != null) editSheet.setVisibility(View.GONE);
        loadViewerPhoto();
    }

    private void loadViewerPhoto() {
        if (currentList.isEmpty()) return;
        final PhotoItem p = currentList.get(currentIndex);
        currentPhoto = p;
        viewerImage.setImageBitmap(null);
        viewerImage.setBackgroundColor(BLACK);
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap b = p.isVideo ? loadThumb(p.uri, 800) : decodeSampled(p.uri, 1600);
                final ExifUtil.ExifData ex = p.isVideo ? new ExifUtil.ExifData()
                        : ExifUtil.read(getContentResolver(), p.uri);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (currentPhoto != p) return;
                        currentExif = ex;
                        if (b != null) viewerImage.setImageBitmap(b);
                        buildFilmstrip();
                        fillDetailPanel(p, ex);
                        updateFavBtn();
                    }
                });
            }
        }).start();
    }

    private void buildFilmstrip() {
        filmstripRow.removeAllViews();
        int start = Math.max(0, currentIndex - 40);
        int end = Math.min(currentList.size(), currentIndex + 41);
        for (int i = start; i < end; i++) {
            final int idx = i;
            final PhotoItem p = currentList.get(i);
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
            lp.setMargins(dp(3), dp(6), dp(3), dp(6));
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (i == currentIndex) {
                iv.setPadding(dp(2), dp(2), dp(2), dp(2));
                iv.setBackgroundColor(WHITE);
            } else {
                iv.setBackgroundColor(Color.TRANSPARENT);
            }
            loadThumbAsync(iv, p, 200);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    currentIndex = idx;
                    closeDetailPanel();
                    if (editSheet != null) editSheet.setVisibility(View.GONE);
                    loadViewerPhoto();
                }
            });
            filmstripRow.addView(iv);
        }
        final int scrollX = Math.max(0, (currentIndex - start - 2) * dp(70));
        filmstripScroll.post(new Runnable() {
            @Override public void run() { filmstripScroll.scrollTo(scrollX, 0); }
        });
    }

    private void fillDetailPanel(final PhotoItem p, final ExifUtil.ExifData ex) {
        if (detailPanel == null) return;
        detailPanel.removeAllViews();
        // Baris tanggal + Ubah.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView date = makeLabel(formatDetailDate(p, ex));
        date.setTextSize(17);
        date.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(date, dl);
        TextView ubah = new TextView(this);
        ubah.setText("Ubah");
        ubah.setTextSize(16);
        ubah.setTextColor(BLUE);
        ubah.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showEditForm(); }
        });
        row.addView(ubah);
        detailPanel.addView(row);

        TextView name = new TextView(this);
        name.setText(p.name == null ? "-" : p.name);
        name.setTextSize(14);
        name.setTextColor(GRAY);
        detailPanel.addView(name);
        TextView path = new TextView(this);
        path.setText("/Penyimpanan internal/" + (p.relPath == null ? "" : p.relPath)
                + (p.name == null ? "" : p.name));
        path.setTextSize(14);
        path.setTextColor(GRAY);
        detailPanel.addView(path);

        // Info kamera ala Samsung (v1.5): nama kamera tebal, ukuran | dimensi | MP,
        // lalu baris ISO | focal | ev | F | exposure — hanya yang ada di EXIF.
        String camName = cameraName(ex);
        if (!camName.isEmpty() || (p.width > 0 && p.height > 0)) {
            if (!camName.isEmpty()) {
                TextView camTitle = makeLabel(camName);
                camTitle.setTextSize(16);
                camTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                camTitle.setPadding(0, dp(16), 0, 0);
                detailPanel.addView(camTitle);
            }
            String mp = (p.width > 0 && p.height > 0)
                    ? Math.round(p.width * p.height / 1000000.0) + "MP" : "-";
            TextView info = new TextView(this);
            info.setText(sizeText(p.size) + "   |   " + p.width + "x" + p.height + "   |   " + mp);
            info.setTextSize(14);
            info.setTextColor(GRAY);
            detailPanel.addView(info);
            String camLine = cameraSettingsLine(ex);
            if (camLine != null) {
                TextView cam = new TextView(this);
                cam.setText(camLine);
                cam.setTextSize(14);
                cam.setTextColor(GRAY);
                detailPanel.addView(cam);
            }
        }

        // Peta OSM + alamat (v1.5, hanya bila foto punya GPS). Ketuk peta/alamat:
        // buka Maps + salin koordinat. 3 tombol lama (Salin Koordinat / Maps /
        // Bagikan ZIP) dihapus dari panel ini — ZIP pindah ke ikon Share.
        if (ex.hasGps && !p.isVideo) {
            detailMapView = new DetailMapView(this);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
            mlp.setMargins(0, dp(16), 0, 0);
            detailPanel.addView(detailMapView, mlp);
            detailAddrView = new TextView(this);
            detailAddrView.setTextSize(14);
            detailAddrView.setTextColor(WHITE);
            detailAddrView.setPadding(0, dp(8), 0, 0);
            String cachedAddr = detailAddrCache.get(p.id);
            detailAddrView.setText(cachedAddr != null ? cachedAddr
                    : "Lat " + String.format(Locale.US, "%.6f", ex.lat)
                    + ", Long " + String.format(Locale.US, "%.6f", ex.lon));
            detailPanel.addView(detailAddrView);
            View.OnClickListener mapTap = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    copyCoords();
                    openMaps();
                }
            };
            detailMapView.setOnClickListener(mapTap);
            detailAddrView.setOnClickListener(mapTap);
            loadDetailMapAsync(p, ex);
        }
    }

    private String formatDetailDate(PhotoItem p, ExifUtil.ExifData ex) {
        Date d = null;
        if (ex.dateTimeOriginal != null) {
            try {
                d = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                        .parse(ex.dateTimeOriginal);
            } catch (Exception ignored) {}
        }
        if (d == null && p.dateAdded > 0) d = new Date(p.dateAdded * 1000L);
        if (d == null) return "-";
        return new SimpleDateFormat("EEEE, dd MMMM yyyy \u00B7 HH.mm",
                new Locale("id", "ID")).format(d);
    }

    // ---------- info kamera + peta panel detail (v1.5, ala Samsung) ----------

    /** "2,23 MB" (koma desimal ala Indonesia) atau "2177 KB". */
    private String sizeText(long size) {
        if (size >= 1024 * 1024) return fmtComma(size / 1048576.0, 2) + " MB";
        if (size > 0) return (size / 1024) + " KB";
        return "-";
    }

    private String fmtComma(double v, int dec) {
        return String.format(Locale.US, "%." + dec + "f", v).replace('.', ',');
    }

    /** "Samsung SM-A525F" (make dikapitalisasi); "" bila make+model kosong. */
    private String cameraName(ExifUtil.ExifData ex) {
        String make = "";
        if (ex.make != null && !ex.make.trim().isEmpty()) {
            String m = ex.make.trim();
            make = Character.toUpperCase(m.charAt(0))
                    + m.substring(1).toLowerCase(Locale.US);
        }
        String model = ex.model == null ? "" : ex.model.trim();
        if (make.isEmpty()) return model;
        if (model.isEmpty()) return make;
        return make + " " + model;
    }

    /** "ISO 100 | 24mm | 0,0ev | F1,8 | 1/50 s" — hanya field yang ada di EXIF. */
    private String cameraSettingsLine(ExifUtil.ExifData ex) {
        ArrayList<String> parts = new ArrayList<String>();
        if (ex.iso > 0) parts.add("ISO " + ex.iso);
        if (!Double.isNaN(ex.focalMm)) {
            parts.add((ex.focalMm == Math.floor(ex.focalMm)
                    ? String.valueOf((int) ex.focalMm) : fmtComma(ex.focalMm, 1)) + "mm");
        }
        if (!Double.isNaN(ex.expBias)) parts.add(fmtComma(ex.expBias, 1) + "ev");
        if (!Double.isNaN(ex.fNumber)) parts.add("F" + fmtComma(ex.fNumber, 1));
        if (!Double.isNaN(ex.expTime) && ex.expTime > 0) {
            if (ex.expTime < 1) parts.add("1/" + Math.round(1.0 / ex.expTime) + " s");
            else parts.add(fmtComma(ex.expTime, 1) + " s");
        }
        if (parts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append("   |   ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /** Muat peta OSM + thumbnail balon + alamat (thread latar, ada cache). */
    private void loadDetailMapAsync(final PhotoItem p, final ExifUtil.ExifData ex) {
        final long pid = p.id;
        // Alamat (cache per foto).
        if (!detailAddrCache.containsKey(pid)) {
            new Thread(new Runnable() {
                @Override public void run() {
                    final ExifUtil.GeoResult g = ExifUtil.reverseGeocodeDetailed(ex.lat, ex.lon);
                    if (g != null && g.address != null) {
                        detailAddrCache.put(pid, g.address);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (currentPhoto == p && detailAddrView != null) {
                                    detailAddrView.setText(g.address);
                                }
                            }
                        });
                    }
                }
            }).start();
        }
        // Peta + thumbnail balon.
        final boolean needMap = (detailMapPhotoId != pid || detailMapBitmap == null);
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap thumb = loadThumb(p.uri, 320);
                Bitmap map = null;
                if (needMap) {
                    int mw = getResources().getDisplayMetrics().widthPixels - dp(40);
                    map = Watermark.mapBitmap(MainActivity.this, ex.lat, ex.lon, mw, dp(180));
                }
                final Bitmap mapBmp = map;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (currentPhoto != p || detailMapView == null) return;
                        if (mapBmp != null) {
                            detailMapBitmap = mapBmp;
                            detailMapPhotoId = pid;
                            detailMapView.setMapBitmap(mapBmp);
                        } else if (!needMap) {
                            detailMapView.setMapBitmap(detailMapBitmap);
                        }
                        detailMapView.setThumb(thumb);
                    }
                });
            }
        }).start();
    }

    /** Peta OSM di panel detail: tile + pin tengah + thumbnail balon di atas pin. */
    private class DetailMapView extends View {
        private Bitmap mapBmp;
        private Bitmap thumbBmp;
        private Paint attrPaint;
        DetailMapView(Context ctx) {
            super(ctx);
            attrPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            attrPaint.setColor(Color.rgb(200, 200, 200));
            attrPaint.setTextSize(dp(10));
            setClickable(true);
        }
        void setMapBitmap(Bitmap b) { mapBmp = b; invalidate(); }
        void setThumb(Bitmap b) { thumbBmp = b; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float r = dp(16);
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), r, r, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            if (mapBmp != null) {
                canvas.drawBitmap(mapBmp, null,
                        new RectF(0, 0, getWidth(), getHeight()), null);
            } else {
                // Offline / peta belum termuat: abu-abu + pin merah di tengah.
                canvas.drawColor(Color.rgb(40, 40, 40));
                Paint pin = new Paint(Paint.ANTI_ALIAS_FLAG);
                pin.setColor(Color.rgb(0xe6, 0x21, 0x17));
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(10), pin);
            }
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (thumbBmp != null) {
                // Balon thumbnail di atas pin (pin: ujung bawah di cy + r*1,55,
                // atas bulat di cy - r*0,55; balon duduk di atasnya).
                float ts = dp(72);
                float pinR = Math.min(getWidth(), getHeight()) * 0.085f;
                float bottom = cy - pinR * 0.55f - dp(6);
                float top = bottom - ts;
                if (top < dp(4)) top = dp(4);
                float left = cx - ts / 2;
                Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
                frame.setColor(Color.WHITE);
                Path fp = new Path();
                fp.addRoundRect(new RectF(left - dp(3), top - dp(3),
                        left + ts + dp(3), top + ts + dp(3)), dp(8), dp(8), Path.Direction.CW);
                canvas.drawPath(fp, frame);
                Path tp = new Path();
                tp.addRoundRect(new RectF(left, top, left + ts, top + ts),
                        dp(6), dp(6), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(tp);
                canvas.drawBitmap(thumbBmp, null,
                        new RectF(left, top, left + ts, top + ts), null);
                canvas.restore();
            }
            canvas.drawText("\u00A9 OpenStreetMap", dp(8), getHeight() - dp(8), attrPaint);
            canvas.restore();
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setColor(Color.argb(71, 0, 0, 0));
            border.setStrokeWidth(2);
            Path bp = new Path();
            bp.addRoundRect(new RectF(1, 1, getWidth() - 1, getHeight() - 1),
                    r, r, Path.Direction.CW);
            canvas.drawPath(bp, border);
        }
    }

    private void openDetailPanel() {
        if (detailPanel == null) return;
        if (editSheet != null) editSheet.setVisibility(View.GONE);
        detailOpen = true;
        detailPanel.setVisibility(View.VISIBLE);
    }

    private void closeDetailPanel() {
        detailOpen = false;
        if (detailPanel != null) detailPanel.setVisibility(View.GONE);
    }

    private void toggleEditSheet() {
        if (editSheet == null) return;
        if (editSheet.getVisibility() != View.VISIBLE) closeDetailPanel();
        editSheet.setVisibility(editSheet.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE);
    }

    /** Lembar Edit (v1.5): Salin EXIF, Watermark (satu menu — gaya Klasik/Job
     *  dipilih di dalam form), Tambah ke album (bila ada album buatan user),
     *  Batal. Edit EXIF tidak ada di sini lagi — pakai tombol "Ubah" di panel
     *  detail. Ikon 18sp, teks 15sp. */
    private void buildEditSheet() {
        editSheet.removeAllViews();
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<String> icons = new ArrayList<String>();
        final ArrayList<Runnable> runs = new ArrayList<Runnable>();
        labels.add("Salin EXIF"); icons.add(FA_COPY);
        runs.add(new Runnable() { @Override public void run() { showCopyForm(); } });
        labels.add("Watermark"); icons.add(FA_STAMP);
        runs.add(new Runnable() { @Override public void run() { showWatermarkForm(wmIsJob); } });
        final ArrayList<String> userAlbums = userAlbumNames();
        if (!userAlbums.isEmpty()) {
            labels.add("Tambah ke album"); icons.add(FA_FOLDER);
            runs.add(new Runnable() {
                @Override public void run() { showAddToAlbumChooser(userAlbums); }
            });
        }
        for (int i = 0; i < labels.size(); i++) {
            final Runnable run = runs.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            row.addView(makeIcon(icons.get(i), 18));
            TextView label = makeLabel(labels.get(i));
            label.setTextSize(15);
            label.setPadding(dp(12), 0, 0, 0);
            row.addView(label);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    editSheet.setVisibility(View.GONE);
                    run.run();
                }
            });
            editSheet.addView(row);
        }
        LinearLayout cancelRow = new LinearLayout(this);
        cancelRow.setOrientation(LinearLayout.HORIZONTAL);
        cancelRow.setGravity(Gravity.CENTER_VERTICAL);
        cancelRow.setPadding(0, dp(10), 0, dp(10));
        cancelRow.addView(makeIcon(FA_XMARK, 18));
        TextView cancelLabel = makeLabel("Batal");
        cancelLabel.setTextSize(15);
        cancelLabel.setPadding(dp(12), 0, 0, 0);
        cancelRow.addView(cancelLabel);
        cancelRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editSheet.setVisibility(View.GONE); }
        });
        editSheet.addView(cancelRow);
    }

    // ---------- aksi foto (dipakai viewer + panel detail) ----------
    private void copyCoords() {
        if (currentExif == null || !currentExif.hasGps) return;
        String text = String.format(Locale.US, "%.6f, %.6f", currentExif.lat, currentExif.lon);
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("koordinat", text));
        Toast.makeText(this, "Koordinat disalin", Toast.LENGTH_SHORT).show();
    }

    private void openMaps() {
        if (currentExif == null || !currentExif.hasGps) return;
        String geo = "geo:" + currentExif.lat + "," + currentExif.lon
                + "?q=" + currentExif.lat + "," + currentExif.lon;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(geo)));
    }

    private void sharePhoto() {
        if (currentPhoto == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(ExifUtil.mimeFor(currentPhoto.name));
        i.putExtra(Intent.EXTRA_STREAM, currentPhoto.uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Bagikan foto"));
    }

    // ---------- v1.6: bottom sheet membulat ala Samsung Gallery ----------
    /** Dialog lembar bawah: sudut atas membulat 28dp, muncul dari bawah,
     *  ketuk di luar menutup. Dipakai semua dialog aplikasi (ganti AlertDialog kotak). */
    private Dialog makeBottomSheet(LinearLayout content) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    /** Akar lembar bawah: bg #2c2c2e sudut atas membulat + handle kecil di tengah. */
    private LinearLayout sheetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        float r = dp(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setColor(Color.rgb(0x2c, 0x2c, 0x2e));
        root.setBackground(bg);
        root.setPadding(dp(20), dp(10), dp(20), dp(20));
        View handle = new View(this);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(40), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, 0, 0, dp(14));
        handle.setLayoutParams(hp);
        GradientDrawable hb = new GradientDrawable();
        hb.setShape(GradientDrawable.RECTANGLE);
        hb.setCornerRadius(dp(2));
        hb.setColor(Color.rgb(0x5f, 0x63, 0x68));
        handle.setBackground(hb);
        root.addView(handle);
        return root;
    }

    /** Judul lembar bawah (tebal 18sp). */
    private TextView sheetTitle(String text) {
        TextView t = makeLabel(text);
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    /** Baris opsi di lembar (teks putih 16sp). */
    private TextView sheetOption(String text) {
        TextView t = makeLabel(text);
        t.setTextSize(16);
        t.setPadding(dp(4), dp(14), dp(4), dp(14));
        return t;
    }

    /** Garis pemisah tipis antar baris lembar. */
    private View sheetDivider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(0x3a, 0x3a, 0x3a));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    /** Baris aksi ala Samsung: "Batal" | "Buat" — teks putih dipisah garis vertikal.
     *  Kiri selalu menutup; kanan menjalankan aksi (aksi menutup sendiri bila
     *  berhasil, supaya validasi gagal bisa membiarkan lembar tetap terbuka). */
    private LinearLayout sheetActions(final Dialog d, String left, final Runnable leftRun,
                                      String right, final Runnable rightRun, boolean rightBold) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView l = new TextView(this);
        l.setText(left);
        l.setTextSize(16);
        l.setTextColor(WHITE);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(8), dp(14), dp(8), dp(14));
        l.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                if (leftRun != null) leftRun.run();
            }
        });
        row.addView(l, lp);
        View div = new View(this);
        div.setBackgroundColor(Color.rgb(0x4a, 0x4a, 0x4a));
        row.addView(div, new LinearLayout.LayoutParams(dp(1), dp(24)));
        TextView r2 = new TextView(this);
        r2.setText(right);
        r2.setTextSize(16);
        r2.setTextColor(WHITE);
        if (rightBold) r2.setTypeface(null, android.graphics.Typeface.BOLD);
        r2.setGravity(Gravity.CENTER);
        r2.setPadding(dp(8), dp(14), dp(8), dp(14));
        r2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (rightRun != null) rightRun.run();
            }
        });
        row.addView(r2, lp);
        return row;
    }

    /** v1.6: Share sebagai bottom sheet membulat — 2 tombol abu-abu gaya baku
     *  (Bagikan Foto / Bagikan ZIP Dokumen) + baris Batal. */
    private void showShareChooser() {
        if (currentPhoto == null) return;
        LinearLayout root = sheetRoot();
        root.addView(sheetTitle("Bagikan"));
        final Dialog dlg = makeBottomSheet(root);
        LinearLayout fotoBtn = makeFaButton(FA_IMAGE, "Bagikan Foto");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(12), 0, dp(6));
        fotoBtn.setLayoutParams(bp);
        fotoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); sharePhoto(); }
        });
        root.addView(fotoBtn);
        LinearLayout zipBtn = makeFaButton(FA_FILE_ARCHIVE, "Bagikan ZIP Dokumen (GPS utuh)");
        LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zp.setMargins(0, dp(6), 0, dp(6));
        zipBtn.setLayoutParams(zp);
        zipBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); shareZip(); }
        });
        root.addView(zipBtn);
        TextView cancel = sheetOption("Batal");
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        root.addView(cancel);
        dlg.show();
    }

    /** v1.6: "Tambah album" sebagai bottom sheet membulat ala Samsung Gallery
     *  (bukan AlertDialog kotak). Fungsi sama seperti v1.5. */
    private void showAddAlbumDialog() {
        final EditText input = new EditText(this);
        input.setHint("Nama album");
        input.setHintTextColor(GRAY);
        input.setTextColor(WHITE);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackground(roundedBg(SURFACE2, STROKE_GRAY, 1, 14));
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout root = sheetRoot();
        root.addView(sheetTitle("Tambah album"));
        root.addView(input);
        final Dialog dlg = makeBottomSheet(root);
        root.addView(sheetActions(dlg, "Batal", null, "Buat", new Runnable() {
            @Override public void run() {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(MainActivity.this,
                            "Nama album tidak boleh kosong", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (name.contains("/") || name.contains("\\") || name.contains("\n")) {
                    Toast.makeText(MainActivity.this,
                            "Nama album tidak boleh mengandung /", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (findAlbum(name) != null) {
                    Toast.makeText(MainActivity.this,
                            "Album sudah ada", Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayList<String> names = userAlbumNames();
                names.add(name);
                saveUserAlbumNames(names);
                loadAllMedia();
                dlg.dismiss();
                showAlbum();
                Toast.makeText(MainActivity.this,
                        "Album " + name + " dibuat", Toast.LENGTH_SHORT).show();
            }
        }, true));
        dlg.show();
    }

    /** v1.6: pilih album tujuan sebagai bottom sheet membulat (daftar nama album). */
    private void showAddToAlbumChooser(final ArrayList<String> names) {
        LinearLayout root = sheetRoot();
        root.addView(sheetTitle("Tambah ke album"));
        final Dialog dlg = makeBottomSheet(root);
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            TextView row = sheetOption(name);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dlg.dismiss();
                    addPhotoToAlbum(name);
                }
            });
            root.addView(row);
            if (i < names.size() - 1) root.addView(sheetDivider());
        }
        TextView cancel = sheetOption("Batal");
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        root.addView(sheetDivider());
        root.addView(cancel);
        dlg.show();
    }

    /** v1.5: salin byte asli foto saat ini ke folder DCIM/<album> (EXIF utuh).
     *  Bukan pindah — foto asli tetap ada. Hasil watermark tetap ke DCIM/Galeriku. */
    private void addPhotoToAlbum(final String albumName) {
        if (currentPhoto == null) return;
        Toast.makeText(this, "Menyalin ke album " + albumName + "...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, currentPhoto.name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, ExifUtil.mimeFor(currentPhoto.name));
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_DCIM + "/" + albumName);
                    Uri uri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Gagal membuat file");
                    Uri readUri = currentPhoto.uri;
                    if (Build.VERSION.SDK_INT >= 29) {
                        try { readUri = MediaStore.setRequireOriginal(readUri); } catch (Exception ignored) {}
                    }
                    InputStream in = getContentResolver().openInputStream(readUri);
                    if (in == null) throw new Exception("Tidak bisa membaca foto asli");
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os == null) throw new Exception("Gagal membuka tujuan");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close();
                    os.close();
                    loadAllMedia();
                    toast("Ditambahkan ke album " + albumName);
                } catch (final Exception e) {
                    toast("Gagal menambahkan: " + e.getMessage());
                }
            }
        }).start();
    }

    private void shareZip() {
        if (currentPhoto == null) return;
        Toast.makeText(this, "Membuat ZIP dari byte asli...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() { makeAndShareZip(); }
        }).start();
    }

    private void makeAndShareZip() {
        try {
            String baseName = (currentPhoto.name == null || currentPhoto.name.isEmpty())
                    ? "foto.jpg" : currentPhoto.name;
            String zipName = ExifUtil.stem(baseName) + ".zip";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, zipName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Galeriku");
            Uri zipUri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (zipUri == null) throw new Exception("Gagal membuat ZIP");
            OutputStream out = getContentResolver().openOutputStream(zipUri);
            ZipOutputStream zip = new ZipOutputStream(out);
            zip.putNextEntry(new ZipEntry(baseName));
            Uri readUri = currentPhoto.uri;
            if (Build.VERSION.SDK_INT >= 29) {
                try { readUri = MediaStore.setRequireOriginal(readUri); } catch (Exception ignored) {}
            }
            InputStream in = getContentResolver().openInputStream(readUri);
            if (in == null) throw new Exception("Tidak bisa membaca foto asli");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
            in.close();
            zip.closeEntry();
            zip.close();
            final Uri shareUri = zipUri;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/zip");
                    i.putExtra(Intent.EXTRA_STREAM, shareUri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Bagikan ZIP sebagai dokumen"));
                    Toast.makeText(MainActivity.this,
                            "Tersimpan di Download/Galeriku/" + ExifUtil.stem(currentPhoto.name) + ".zip — kirim sebagai dokumen, GPS tetap utuh.",
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (final Exception e) {
            toast("Gagal membuat ZIP: " + e.getMessage());
        }
    }

    private String buildExifText(PhotoItem p, ExifUtil.ExifData ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nama: ").append(p.name == null ? "-" : p.name).append("\n");
        sb.append("Folder: ").append(p.relPath == null ? (p.bucket == null ? "-" : p.bucket) : p.relPath).append("\n");
        sb.append("Ukuran: ").append(p.width).append(" x ").append(p.height);
        if (p.size > 0) sb.append("  (").append(p.size / 1024).append(" KB)");
        sb.append("\n");
        if (p.dateAdded > 0) {
            sb.append("Ditambahkan: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(new Date(p.dateAdded * 1000L))).append("\n");
        }
        sb.append("GPS: ").append(ex.latLonText()).append("\n");
        if (!Double.isNaN(ex.alt)) sb.append("Altitude: ").append(ex.alt).append(" m\n");
        sb.append("DateTimeOriginal: ").append(ex.dateTimeOriginal == null ? "-" : ex.dateTimeOriginal).append("\n");
        sb.append("Tanggal GPS: ").append(ex.gpsDate == null ? "-" : ex.gpsDate).append("\n");
        sb.append("Jam GPS: ").append(ex.gpsTime == null ? "-" : ex.gpsTime).append("\n");
        sb.append("Kamera: ").append(ex.make == null ? "" : ex.make).append(" ")
                .append(ex.model == null ? "" : ex.model).append("\n");
        return sb.toString();
    }

    // ---------- galeri: edit EXIF (form v1.0, kembali ke viewer) ----------
    private void showEditForm() {
        screen = "edit";
        if (currentPhoto == null) return;
        if (currentExif == null) currentExif = ExifUtil.read(getContentResolver(), currentPhoto.uri);
        bottomNav.setVisibility(View.GONE);
        container.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(16, 16, 16, 16);
        scroll.addView(root);
        root.addView(makeBackButton(new Runnable() {
            @Override public void run() { showViewer(currentList, currentIndex); }
        }));
        TextView title = makeLabel("Edit EXIF — " + currentPhoto.name);
        title.setTextSize(16);
        root.addView(title);
        TextView note = makeLabel("Hasil disimpan sebagai file BARU di DCIM/Galeriku. File asli tidak diubah.");
        note.setTextSize(12);
        note.setTextColor(GRAY);
        root.addView(note);

        // v1.6: LOKASI — cari alamat + peta titik (di atas Latitude).
        root.addView(makeSectionLabel("LOKASI — CARI ALAMAT / PETA"));
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        editSearchBox = makeEdit("cth: Jl. Darmokali, Surabaya");
        editSearchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        editSearchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    runEditAddressSearch();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins(0, 0, dp(8), 0);
        searchRow.addView(editSearchBox, sbLp);
        LinearLayout searchBtn = makeFaButton(FA_SEARCH, "Cari");
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runEditAddressSearch(); }
        });
        searchRow.addView(searchBtn);
        root.addView(searchRow);
        editSearchResults = new LinearLayout(this);
        editSearchResults.setOrientation(LinearLayout.VERTICAL);
        root.addView(editSearchResults);

        editMapW = getResources().getDisplayMetrics().widthPixels - 32;
        editMapView = new ImageView(this);
        editMapView.setScaleType(ImageView.ScaleType.FIT_XY);
        editMapView.setBackgroundColor(Color.rgb(40, 40, 40));
        LinearLayout.LayoutParams mwLp = new LinearLayout.LayoutParams(editMapW, dp(220));
        mwLp.setMargins(0, dp(8), 0, 0);
        editMapView.setLayoutParams(mwLp);
        // Ketuk peta = pilih titik (geser < dp(12) dianggap ketuk, bukan scroll).
        editMapView.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getX(); downY = e.getY(); moved = false;
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(e.getX() - downX) > dp(12)
                            || Math.abs(e.getY() - downY) > dp(12)) moved = true;
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (!moved && !Double.isNaN(editMapLat) && !Double.isNaN(editMapLon)) {
                        double[] ll = Watermark.mapTapToLatLon(editMapLat, editMapLon,
                                editMapW, dp(220), e.getX(), e.getY());
                        setEditLatLon(ll[0], ll[1]);
                    }
                    return true;
                }
                return true;
            }
        });
        root.addView(editMapView);
        editAddrText = makeLabel("Memuat peta…");
        editAddrText.setTextSize(13);
        editAddrText.setTextColor(GRAY);
        editAddrText.setPadding(0, dp(8), 0, 0);
        root.addView(editAddrText);
        LinearLayout mapsBtn = makeFaButton(FA_MAP_PIN, "Lihat di Maps");
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.setMargins(0, dp(8), 0, 0);
        mapsBtn.setLayoutParams(mbLp);
        mapsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEditMaps(); }
        });
        root.addView(mapsBtn);

        root.addView(makeLabel("Latitude"));
        eLat = makeEdit("cth: -7.276971");
        eLat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (currentExif.hasGps) eLat.setText(String.format(Locale.US, "%.6f", currentExif.lat));
        root.addView(eLat);

        root.addView(makeLabel("Longitude"));
        eLon = makeEdit("cth: 112.747416");
        eLon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (currentExif.hasGps) eLon.setText(String.format(Locale.US, "%.6f", currentExif.lon));
        root.addView(eLon);

        root.addView(makeLabel("Altitude (meter)"));
        eAlt = makeEdit("cth: 34");
        eAlt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (!Double.isNaN(currentExif.alt)) eAlt.setText(String.valueOf(currentExif.alt));
        root.addView(eAlt);

        root.addView(makeLabel("Tanggal (yyyy-MM-dd)"));
        eDate = makeEdit("cth: 2026-11-09");
        eDate.setText(currentExif.dateText());
        root.addView(eDate);

        root.addView(makeLabel("Jam (HH:mm:ss)"));
        eTime = makeEdit("cth: 18:17:00");
        eTime.setText(currentExif.timeText());
        root.addView(eTime);

        root.addView(makeLabel("Make (opsional)"));
        eMake = makeEdit("cth: samsung");
        if (currentExif.make != null) eMake.setText(currentExif.make);
        root.addView(eMake);

        root.addView(makeLabel("Model (opsional)"));
        eModel = makeEdit("cth: SM-A525F");
        if (currentExif.model != null) eModel.setText(currentExif.model);
        root.addView(eModel);

        Button save = makeButton("Simpan ke DCIM/Galeriku");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveEditedExif(); }
        });
        root.addView(save);
        // v1.6: muat peta titik + alamat awal (pusat = GPS foto, atau Surabaya bila tanpa GPS).
        if (currentExif.hasGps) {
            editMapLat = currentExif.lat;
            editMapLon = currentExif.lon;
            renderEditMap();
            fetchEditAddress();
        } else {
            editMapLat = -7.2575;
            editMapLon = 112.7521;
            if (editAddrText != null) editAddrText.setText(
                    "Foto ini belum punya GPS — ketuk peta atau cari alamat untuk memilih titik.");
            renderEditMap();
        }
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void saveEditedExif() {
        final String latS = eLat.getText().toString().trim();
        final String lonS = eLon.getText().toString().trim();
        final String altS = eAlt.getText().toString().trim();
        final String dateS = eDate.getText().toString().trim();
        final String timeS = eTime.getText().toString().trim();
        final String makeS = eMake.getText().toString().trim();
        final String modelS = eModel.getText().toString().trim();
        Toast.makeText(this, "Menyimpan...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ExifUtil.ExifData d = new ExifUtil.ExifData();
                    if (!latS.isEmpty() && !lonS.isEmpty()) {
                        d.lat = Double.parseDouble(latS.replace(',', '.'));
                        d.lon = Double.parseDouble(lonS.replace(',', '.'));
                        d.hasGps = true;
                    }
                    if (!altS.isEmpty()) d.alt = Double.parseDouble(altS.replace(',', '.'));
                    String dateExif = dateS.replace('-', ':');
                    String timeExif = timeS;
                    if (timeExif.length() == 5) timeExif = timeExif + ":00";
                    if (!dateExif.isEmpty() && !timeExif.isEmpty()) {
                        d.dateTimeOriginal = dateExif + " " + timeExif;
                    } else if (!dateExif.isEmpty()) {
                        d.dateTimeOriginal = dateExif + " 00:00:00";
                    }
                    if (!dateExif.isEmpty()) d.gpsDate = dateExif;
                    if (!timeExif.isEmpty()) d.gpsTime = timeExif;
                    if (!makeS.isEmpty()) d.make = makeS;
                    if (!modelS.isEmpty()) d.model = modelS;

                    String ext = ExifUtil.extOf(currentPhoto.name);
                    File cache = ExifUtil.copyUriToCache(MainActivity.this, currentPhoto.uri, "edit." + ext);
                    ExifUtil.applyToFile(d, cache.getAbsolutePath());
                    String newName = ExifUtil.stem(currentPhoto.name) + "_exifedit." + ext;
                    ExifUtil.saveFileToGaleriku(MainActivity.this, cache, newName, ExifUtil.mimeFor(newName));
                    toast("Tersimpan di DCIM/Galeriku/" + newName);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { reloadAndShowGrid(); }
                    });
                } catch (final Exception e) {
                    toast("Gagal menyimpan: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------- v1.6: peta + cari alamat di Edit EXIF ----------
    private void renderEditMap() {
        if (editMapView == null || editMapW <= 0) return;
        final double lat = editMapLat, lon = editMapLon;
        if (Double.isNaN(lat) || Double.isNaN(lon)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bm = Watermark.mapBitmap(MainActivity.this, lat, lon, editMapW, dp(220));
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (editMapView != null && bm != null) editMapView.setImageBitmap(bm);
                    }
                });
            }
        }).start();
    }

    /** Isi Latitude/Longitude dari titik baru, geser peta, cari alamatnya. */
    private void setEditLatLon(double lat, double lon) {
        editMapLat = lat;
        editMapLon = lon;
        if (eLat != null) eLat.setText(String.format(Locale.US, "%.6f", lat));
        if (eLon != null) eLon.setText(String.format(Locale.US, "%.6f", lon));
        renderEditMap();
        fetchEditAddress();
    }

    private void fetchEditAddress() {
        final double lat = editMapLat, lon = editMapLon;
        if (Double.isNaN(lat) || Double.isNaN(lon)) return;
        if (editAddrText != null) editAddrText.setText("Mencari alamat…");
        new Thread(new Runnable() {
            @Override public void run() {
                final ExifUtil.GeoResult g = ExifUtil.reverseGeocodeDetailed(lat, lon);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (editAddrText == null) return;
                        if (g != null && g.address != null) editAddrText.setText(g.address);
                        else editAddrText.setText(String.format(Locale.US,
                                "Lat %.6f, Long %.6f", lat, lon));
                    }
                });
            }
        }).start();
    }

    private void runEditAddressSearch() {
        if (editSearchBox == null || editSearchResults == null) return;
        final String q = editSearchBox.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Ketik dulu alamat yang dicari.", Toast.LENGTH_SHORT).show();
            return;
        }
        editSearchResults.removeAllViews();
        TextView st = makeLabel("Mencari \"" + q + "\"…");
        st.setTextSize(13);
        st.setTextColor(GRAY);
        editSearchResults.addView(st);
        new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<ExifUtil.SearchResult> res = ExifUtil.searchAddress(q);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (editSearchResults == null) return;
                        editSearchResults.removeAllViews();
                        if (res == null || res.isEmpty()) {
                            TextView none = makeLabel("Tidak ada hasil untuk \"" + q
                                    + "\". Coba kata kunci lain.");
                            none.setTextSize(13);
                            none.setTextColor(GRAY);
                            editSearchResults.addView(none);
                            return;
                        }
                        for (final ExifUtil.SearchResult r : res) {
                            TextView row = makeLabel(r.display);
                            row.setTextSize(13);
                            row.setPadding(dp(12), dp(10), dp(12), dp(10));
                            row.setBackground(roundedBg(SURFACE2, STROKE_GRAY, 1, 10));
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                            lp.setMargins(0, dp(6), 0, 0);
                            row.setLayoutParams(lp);
                            row.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    editSearchResults.removeAllViews();
                                    setEditLatLon(r.lat, r.lon);
                                    Toast.makeText(MainActivity.this,
                                            "Titik dipilih", Toast.LENGTH_SHORT).show();
                                }
                            });
                            editSearchResults.addView(row);
                        }
                    }
                });
            }
        }).start();
    }

    private void openEditMaps() {
        double lat = editMapLat, lon = editMapLon;
        try {
            if (eLat != null && !eLat.getText().toString().trim().isEmpty())
                lat = Double.parseDouble(eLat.getText().toString().trim().replace(',', '.'));
            if (eLon != null && !eLon.getText().toString().trim().isEmpty())
                lon = Double.parseDouble(eLon.getText().toString().trim().replace(',', '.'));
        } catch (Exception ignored) {}
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            Toast.makeText(this, "Belum ada titik yang dipilih.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada aplikasi Maps di perangkat.", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- galeri: salin EXIF ----------
    private void showCopyForm() {
        screen = "copy";
        if (currentPhoto == null) return;
        if (currentExif == null) currentExif = ExifUtil.read(getContentResolver(), currentPhoto.uri);
        copySourceExif = null;
        copySourceName = "";
        bottomNav.setVisibility(View.GONE);
        container.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(16, 16, 16, 16);
        scroll.addView(root);
        root.addView(makeBackButton(new Runnable() {
            @Override public void run() { showViewer(currentList, currentIndex); }
        }));
        TextView title = makeLabel("Salin EXIF dari Foto Lain");
        title.setTextSize(16);
        root.addView(title);
        TextView note = makeLabel("Pilih foto sumber (SAF). Semua tag EXIF sumber — GPS, tanggal, kamera — disalin ke foto ini. Hasil disimpan BARU di DCIM/Galeriku.");
        note.setTextSize(12);
        note.setTextColor(GRAY);
        root.addView(note);

        copySummary = makeLabel("Belum ada foto sumber dipilih.\nFoto saat ini: " + buildExifText(currentPhoto, currentExif));
        copySummary.setTextSize(13);
        root.addView(copySummary);

        Button pick = makeButton("Pilih Foto Sumber...");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQ_COPY);
            }
        });
        root.addView(pick);

        Button apply = makeButton("Terapkan & Simpan ke DCIM/Galeriku");
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyCopyExif(); }
        });
        root.addView(apply);
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void applyCopyExif() {
        if (copySourceExif == null) {
            Toast.makeText(this, "Pilih dulu foto sumber.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Menyalin EXIF...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String ext = ExifUtil.extOf(currentPhoto.name);
                    File cache = ExifUtil.copyUriToCache(MainActivity.this, currentPhoto.uri, "copy." + ext);
                    ExifUtil.copyTagsToFile(copySourceExif, cache.getAbsolutePath());
                    String newName = ExifUtil.stem(currentPhoto.name) + "_exifcopy." + ext;
                    ExifUtil.saveFileToGaleriku(MainActivity.this, cache, newName, ExifUtil.mimeFor(newName));
                    toast("Tersimpan di DCIM/Galeriku/" + newName);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { reloadAndShowGrid(); }
                    });
                } catch (final Exception e) {
                    toast("Gagal menyalin EXIF: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------- galeri: watermark ----------
    // ---------- layar WATERMARK (v1.3, mengikuti Stempel Foto GPS Web) ----------

    private GradientDrawable roundedBg(int fill, int stroke, int strokeWdp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radiusDp));
        d.setColor(fill);
        if (strokeWdp > 0) d.setStroke(dp(strokeWdp), stroke);
        return d;
    }

    private GradientDrawable dotBg(boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (on) d.setColor(BRAND);
        else { d.setColor(Color.TRANSPARENT); d.setStroke(dp(2), GRAY); }
        return d;
    }

    /** Tombol baris berikon Font Awesome — SATU gaya baku (v1.6): abu-abu
     *  membulat radius 14dp, teks putih tebal. Tidak ada lagi tombol hijau. */
    private LinearLayout makeFaButton(String fa, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(roundedBg(BTN_GRAY, 0, 0, 14));
        TextView ic = makeIcon(fa, 16);
        row.addView(ic);
        TextView lb = new TextView(this);
        lb.setText(label);
        lb.setTextSize(14);
        lb.setTypeface(null, android.graphics.Typeface.BOLD);
        lb.setTextColor(WHITE);
        lb.setPadding(dp(8), 0, 0, 0);
        row.addView(lb);
        return row;
    }

    /** Legend seksi kecil huruf kapital (KOORDINAT, TANGGAL & JAM, ...). */
    private TextView makeSectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(GRAY);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    /** Pill toggle tampilan stempel (default ON semua). */
    private TextView makePill(final String label, final Runnable onToggle) {
        final TextView p = new TextView(this);
        p.setText(label);
        p.setTextSize(13);
        p.setTextColor(WHITE);
        p.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), dp(8));
        p.setLayoutParams(lp);
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onToggle.run(); }
        });
        return p;
    }

    private void paintPill(TextView p, boolean on) {
        p.setBackground(roundedBg(on ? Color.rgb(31, 111, 87) : SURFACE2, on ? BRAND : STROKE_GRAY, 1, 16));
        p.setTextColor(on ? WHITE : GRAY);
    }

    private void refreshPills() {
        if (wmPillMap != null) paintPill(wmPillMap, wmShowMap);
        if (wmPillAddr != null) paintPill(wmPillAddr, wmShowAddr);
        if (wmPillCoord != null) paintPill(wmPillCoord, wmShowCoord);
        if (wmPillLogo != null) paintPill(wmPillLogo, wmShowLogo);
        if (wmPillFlag != null) paintPill(wmPillFlag, wmShowFlag);
        if (wmPillWeather != null) paintPill(wmPillWeather, wmShowWeather);
    }

    private TextView makeChip(String text) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextSize(11);
        c.setTextColor(WHITE);
        c.setPadding(dp(8), dp(4), dp(8), dp(4));
        c.setBackground(roundedBg(SURFACE2, 0, 0, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout buildStyleCard(final boolean isJobCard, String title, String version,
                                        String desc, String[] chips) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dlp.setMargins(0, 0, dp(8), 0);
        dot.setLayoutParams(dlp);
        top.addView(dot);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(WHITE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(t, tlp);
        TextView v = new TextView(this);
        v.setText(version);
        v.setTextSize(11);
        v.setTextColor(GRAY);
        v.setPadding(dp(6), dp(2), dp(6), dp(2));
        v.setBackground(roundedBg(SURFACE2, 0, 0, 10));
        top.addView(v);
        card.addView(top);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(GRAY);
        d.setPadding(0, dp(6), 0, dp(8));
        card.addView(d);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < chips.length; i++) {
            if (i < 2) row1.addView(makeChip(chips[i]));
            else row2.addView(makeChip(chips[i]));
        }
        card.addView(row1);
        if (chips.length > 2) card.addView(row2);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setWmStyle(isJobCard); }
        });
        if (isJobCard) { wmCardJob = card; wmDotJob = dot; }
        else { wmCardKlasik = card; wmDotKlasik = dot; }
        return card;
    }

    private void refreshStyleCards() {
        if (wmCardKlasik != null) {
            wmCardKlasik.setBackground(roundedBg(SURFACE, wmIsJob ? STROKE_GRAY : BRAND, 2, 12));
        }
        if (wmCardJob != null) {
            wmCardJob.setBackground(roundedBg(SURFACE, wmIsJob ? BRAND : STROKE_GRAY, 2, 12));
        }
        if (wmDotKlasik != null) wmDotKlasik.setBackground(dotBg(!wmIsJob));
        if (wmDotJob != null) wmDotJob.setBackground(dotBg(wmIsJob));
    }

    private void applyStyleVisibility() {
        for (int i = 0; i < wmClassicOnly.size(); i++) {
            wmClassicOnly.get(i).setVisibility(wmIsJob ? View.GONE : View.VISIBLE);
        }
        for (int i = 0; i < wmJobOnly.size(); i++) {
            wmJobOnly.get(i).setVisibility(wmIsJob ? View.VISIBLE : View.GONE);
        }
    }

    private void setWmStyle(boolean isJob) {
        wmIsJob = isJob;
        refreshStyleCards();
        applyStyleVisibility();
        updDtHelp();
        scheduleWmPreview();
    }

    /** Helper tanggal-jam hidup: format Klasik + Job dari isian saat ini. */
    private void updDtHelp() {
        if (wmDtHelp == null) return;
        Calendar cal = Watermark.parseDateTime(wmText(wmDate), wmText(wmTime));
        wmDtHelp.setText("Di stempel tampil sebagai:\nKlasik: " + Watermark.formatClassicDate(cal)
                + "\nJob: " + Watermark.formatJobDate(cal));
    }

    private void showWatermarkForm(final boolean isJob) {
        screen = "wm";
        wmIsJob = isJob;
        wmRotation = 0;
        wmAutoGeoDone = false;
        wmShowMap = true; wmShowAddr = true; wmShowCoord = true;
        wmShowLogo = true; wmShowFlag = true; wmShowWeather = true;
        wmScalePct = 100;
        wmJobTransPct = 40;
        wmClassicOnly.clear();
        wmJobOnly.clear();
        if (currentPhoto == null) return;
        if (currentExif == null) currentExif = ExifUtil.read(getContentResolver(), currentPhoto.uri);
        bottomNav.setVisibility(View.GONE);
        container.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BLACK);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(16, 16, 16, 16);
        scroll.addView(root);
        root.addView(makeBackButton(new Runnable() {
            @Override public void run() { showViewer(currentList, currentIndex); }
        }));
        TextView title = makeLabel("Watermark GPS");
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        wmInfo = makeLabel("GPS foto: " + currentExif.latLonText()
                + "\nHasil disimpan BARU di DCIM/Galeriku dan tetap membawa GPS + DateTimeOriginal foto asli.");
        wmInfo.setTextSize(12);
        wmInfo.setTextColor(GRAY);
        root.addView(wmInfo);
        wmGeoStatus = makeLabel("");
        wmGeoStatus.setTextSize(12);
        wmGeoStatus.setTextColor(GRAY);
        wmGeoStatus.setVisibility(View.GONE);
        root.addView(wmGeoStatus);

        // --- kartu pratinjau + tombol berikon ---
        TextView pvTitle = makeLabel("Pratinjau");
        pvTitle.setTextSize(14);
        pvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        pvTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(pvTitle);
        wmPreview = new RoundedImageView(this, 12);
        wmPreview.setAdjustViewBounds(true);
        wmPreview.setBackgroundColor(Color.rgb(30, 30, 30));
        wmPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(wmPreview);
        wmPreviewStatus = makeLabel("Memuat pratinjau...");
        wmPreviewStatus.setTextSize(12);
        wmPreviewStatus.setTextColor(GRAY);
        root.addView(wmPreviewStatus);
        LinearLayout pvBtns = new LinearLayout(this);
        pvBtns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.setMargins(dp(4), 0, dp(4), 0);
        LinearLayout rotateBtn = makeFaButton(FA_ROTATE_RIGHT, "Putar 90°");
        rotateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wmRotation = (wmRotation + 90) % 360;
                scheduleWmPreview();
            }
        });
        pvBtns.addView(rotateBtn, btnLp);
        LinearLayout saveBtn = makeFaButton(FA_FLOPPY, "Simpan");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { renderWatermark(); }
        });
        pvBtns.addView(saveBtn, btnLp);
        LinearLayout refreshBtn = makeFaButton(FA_ARROWS_ROTATE, "Perbarui");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { renderWmPreview(); }
        });
        pvBtns.addView(refreshBtn, btnLp);
        root.addView(pvBtns);

        // --- 1 · Pilih gaya stempel ---
        root.addView(makeSectionLabel("1 · PILIH GAYA STEMPEL"));
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(6), 0, dp(6));
        cards.addView(buildStyleCard(false, "GPS Map Camera Klasik", "v1.8.2",
                "Kartu gelap + peta satelit di bawah foto.",
                new String[]{"Peta satelit", "Logo Google", "Pin merah", "Bendera"}), cardLp);
        cards.addView(buildStyleCard(true, "GPS Map Camera — Job Timestamp", "v1.9.41",
                "Strip transparan menutupi bagian bawah foto.",
                new String[]{"Peta jalan", "Strip transparan", "Lat° Long°", "Ikon cuaca", "28°C / 82°F"}), cardLp);
        root.addView(cards);

        // --- 3 · Isi data stempel ---
        root.addView(makeSectionLabel("3 · ISI DATA STEMPEL"));

        root.addView(makeSectionLabel("KOORDINAT"));
        LinearLayout latLonRow = new LinearLayout(this);
        latLonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams llLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llLp.setMargins(0, 0, dp(8), 0);
        wmLat = makeEdit("Latitude");
        wmLat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (currentExif.hasGps) wmLat.setText(String.format(Locale.US, "%.6f", currentExif.lat));
        latLonRow.addView(wmLat, llLp);
        wmLon = makeEdit("Longitude");
        wmLon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (currentExif.hasGps) wmLon.setText(String.format(Locale.US, "%.6f", currentExif.lon));
        latLonRow.addView(wmLon, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(latLonRow);
        LinearLayout locBtns = new LinearLayout(this);
        locBtns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout myLocBtn = makeFaButton(FA_LOCATION_DOT, "Lokasi saya saat ini");
        myLocBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fetchMyLocation(); }
        });
        locBtns.addView(myLocBtn, btnLp);
        LinearLayout geoBtn = makeFaButton(FA_SEARCH, "Cari alamat dari koordinat");
        geoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fetchAddress(); }
        });
        locBtns.addView(geoBtn, btnLp);
        root.addView(locBtns);
        TextView coordNote = makeLabel("Desimal boleh pakai koma, mis. 112,747129.");
        coordNote.setTextSize(12);
        coordNote.setTextColor(GRAY);
        root.addView(coordNote);

        root.addView(makeSectionLabel("TANGGAL & JAM"));
        LinearLayout dtRow = new LinearLayout(this);
        dtRow.setOrientation(LinearLayout.HORIZONTAL);
        wmDate = makeEdit("Tanggal (yyyy-MM-dd)");
        wmDate.setText(currentExif.dateText());
        dtRow.addView(wmDate, llLp);
        wmTime = makeEdit("Jam (HH:mm)");
        String tt = currentExif.timeText();
        if (tt != null && tt.length() >= 5) tt = tt.substring(0, 5);
        wmTime.setText(tt);
        dtRow.addView(wmTime, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(dtRow);
        LinearLayout nowBtn = makeFaButton(FA_CLOCK, "Pakai waktu sekarang");
        nowBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Date now = new Date();
                wmDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now));
                wmTime.setText(new SimpleDateFormat("HH:mm", Locale.US).format(now));
                updDtHelp();
                scheduleWmPreview();
            }
        });
        root.addView(nowBtn);
        wmDtHelp = makeLabel("");
        wmDtHelp.setTextSize(12);
        wmDtHelp.setTextColor(GRAY);
        wmDtHelp.setPadding(0, dp(6), 0, 0);
        root.addView(wmDtHelp);

        root.addView(makeSectionLabel("LOKASI"));
        root.addView(makeLabel("Baris judul — kecamatan, provinsi, negara"));
        wmPlace = makeEdit("cth: Kecamatan Tambaksari, Jawa Timur, Indonesia");
        root.addView(wmPlace);
        root.addView(makeLabel("Alamat lengkap"));
        wmAddress = makeEdit("Alamat lengkap");
        if (currentExif.hasGps) wmAddress.setText(String.format(Locale.US, "%.6f, %.6f", currentExif.lat, currentExif.lon));
        root.addView(wmAddress);

        // Hanya Klasik: cuaca teks
        LinearLayout classicWeatherBox = new LinearLayout(this);
        classicWeatherBox.setOrientation(LinearLayout.VERTICAL);
        classicWeatherBox.addView(makeSectionLabel("TAMBAHAN GAYA KLASIK"));
        classicWeatherBox.addView(makeLabel("Cuaca (teks)"));
        wmWeatherText = makeEdit("cth: Cerah 31°C");
        classicWeatherBox.addView(wmWeatherText);
        root.addView(classicWeatherBox);
        wmClassicOnly.add(classicWeatherBox);

        // Hanya Job: suhu + kondisi cuaca
        LinearLayout jobWeatherBox = new LinearLayout(this);
        jobWeatherBox.setOrientation(LinearLayout.VERTICAL);
        jobWeatherBox.addView(makeSectionLabel("CUACA DI STRIP"));
        jobWeatherBox.addView(makeLabel("Suhu (°C)"));
        wmTemp = makeEdit("cth: 28");
        wmTemp.setText("28");
        wmTemp.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        jobWeatherBox.addView(wmTemp);
        jobWeatherBox.addView(makeLabel("Kondisi cuaca"));
        wmCond = new Spinner(this);
        final String[] condLabels = new String[]{"Berawan (awan)", "Sebagian berawan (matahari + awan)",
                "Cerah (matahari)", "Hujan (awan + rintik)"};
        ArrayAdapter<String> ca = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, condLabels);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wmCond.setAdapter(ca);
        jobWeatherBox.addView(wmCond);
        root.addView(jobWeatherBox);
        wmJobOnly.add(jobWeatherBox);

        // Hanya Job (v1.8): transparansi strip — slider 0-90%.
        LinearLayout jobTransBox = new LinearLayout(this);
        jobTransBox.setOrientation(LinearLayout.VERTICAL);
        jobTransBox.addView(makeSectionLabel("TRANSPARANSI STRIP"));
        jobTransBox.addView(makeLabel("Transparansi strip"));
        LinearLayout transRow = new LinearLayout(this);
        transRow.setOrientation(LinearLayout.HORIZONTAL);
        transRow.setGravity(Gravity.CENTER_VERTICAL);
        wmJobTrans = new SeekBar(this);
        wmJobTrans.setMax(90);
        wmJobTrans.setProgress(40);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        transRow.addView(wmJobTrans, tLp);
        wmJobTransVal = makeLabel("40%");
        wmJobTransVal.setPadding(dp(12), 0, 0, 0);
        transRow.addView(wmJobTransVal);
        jobTransBox.addView(transRow);
        TextView transNote = makeLabel("0% = strip padat, 90% = hampir tembus pandang");
        transNote.setTextSize(12);
        transNote.setTextColor(GRAY);
        transNote.setPadding(0, dp(4), 0, 0);
        jobTransBox.addView(transNote);
        root.addView(jobTransBox);
        wmJobOnly.add(jobTransBox);
        wmJobTrans.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                wmJobTransPct = progress;
                if (wmJobTransVal != null) wmJobTransVal.setText(progress + "%");
                scheduleWmPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // --- Tampilan stempel (pill toggle) ---
        root.addView(makeSectionLabel("TAMPILAN STEMPEL"));
        LinearLayout pills1 = new LinearLayout(this);
        pills1.setOrientation(LinearLayout.HORIZONTAL);
        wmPillMap = makePill("Peta mini + logo Google", new Runnable() {
            @Override public void run() { wmShowMap = !wmShowMap; refreshPills(); scheduleWmPreview(); }
        });
        pills1.addView(wmPillMap);
        wmPillAddr = makePill("Alamat", new Runnable() {
            @Override public void run() { wmShowAddr = !wmShowAddr; refreshPills(); scheduleWmPreview(); }
        });
        pills1.addView(wmPillAddr);
        wmPillCoord = makePill("Koordinat", new Runnable() {
            @Override public void run() { wmShowCoord = !wmShowCoord; refreshPills(); scheduleWmPreview(); }
        });
        pills1.addView(wmPillCoord);
        root.addView(pills1);
        LinearLayout pills2 = new LinearLayout(this);
        pills2.setOrientation(LinearLayout.HORIZONTAL);
        wmPillLogo = makePill("Ikon GPS Map Camera", new Runnable() {
            @Override public void run() { wmShowLogo = !wmShowLogo; refreshPills(); scheduleWmPreview(); }
        });
        pills2.addView(wmPillLogo);
        wmPillFlag = makePill("Bendera Indonesia", new Runnable() {
            @Override public void run() { wmShowFlag = !wmShowFlag; refreshPills(); scheduleWmPreview(); }
        });
        pills2.addView(wmPillFlag);
        wmPillWeather = makePill("Ikon cuaca + suhu", new Runnable() {
            @Override public void run() { wmShowWeather = !wmShowWeather; refreshPills(); scheduleWmPreview(); }
        });
        pills2.addView(wmPillWeather);
        root.addView(pills2);
        wmClassicOnly.add(wmPillLogo);
        wmClassicOnly.add(wmPillFlag);
        wmJobOnly.add(wmPillWeather);
        refreshPills();

        // --- Ukuran stempel ---
        root.addView(makeSectionLabel("UKURAN STEMPEL"));
        LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleRow.setGravity(Gravity.CENTER_VERTICAL);
        wmScale = new SeekBar(this);
        if (Build.VERSION.SDK_INT >= 26) wmScale.setMin(70);
        wmScale.setMax(150);
        wmScale.setProgress(100);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        scaleRow.addView(wmScale, sLp);
        wmScaleVal = makeLabel("100%");
        wmScaleVal.setPadding(dp(12), 0, 0, 0);
        scaleRow.addView(wmScaleVal);
        root.addView(scaleRow);
        wmScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                wmScalePct = progress;
                if (wmScaleVal != null) wmScaleVal.setText(progress + "%");
                scheduleWmPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Simpan (hijau penuh) di bawah form
        LinearLayout bigSave = makeFaButton(FA_FLOPPY, "Simpan ke DCIM/Galeriku");
        LinearLayout.LayoutParams bsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bsLp.setMargins(0, dp(16), 0, 0);
        bigSave.setLayoutParams(bsLp);
        bigSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { renderWatermark(); }
        });
        root.addView(bigSave);

        watchWmField(wmLat);
        watchWmField(wmLon);
        watchWmField(wmPlace);
        watchWmField(wmAddress);
        watchWmField(wmDate);
        watchWmField(wmTime);
        watchWmField(wmWeatherText);
        watchWmField(wmTemp);
        if (wmCond != null) {
            wmCond.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) { scheduleWmPreview(); }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
        refreshStyleCards();
        applyStyleVisibility();
        updDtHelp();
        scheduleWmPreview();
        autoGeocodeWm();
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** "Lokasi saya saat ini": lokasi terakhir perangkat (GPS lalu jaringan). */
    private void fetchMyLocation() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin lokasi belum diberikan.", Toast.LENGTH_LONG).show();
                return;
            }
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location loc = null;
            if (lm != null) {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc == null) {
                Toast.makeText(this, "Lokasi belum tersedia, nyalakan GPS lalu coba lagi.", Toast.LENGTH_LONG).show();
                return;
            }
            wmLat.setText(String.format(Locale.US, "%.6f", loc.getLatitude()));
            wmLon.setText(String.format(Locale.US, "%.6f", loc.getLongitude()));
            scheduleWmPreview();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membaca lokasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void fetchAddress() {
        final double lat = parseCoord(wmText(wmLat), currentExif == null ? Double.NaN : currentExif.lat);
        final double lon = parseCoord(wmText(wmLon), currentExif == null ? Double.NaN : currentExif.lon);
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            Toast.makeText(this, "Isi latitude & longitude dulu.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Mencari alamat...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final ExifUtil.GeoResult g = ExifUtil.reverseGeocodeDetailed(lat, lon);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (g != null && g.address != null) {
                            if (wmAddress != null) wmAddress.setText(g.address);
                            if (g.place != null && wmPlace != null) wmPlace.setText(g.place);
                            Toast.makeText(MainActivity.this, "Alamat ditemukan", Toast.LENGTH_SHORT).show();
                            scheduleWmPreview();
                        } else {
                            Toast.makeText(MainActivity.this, "Alamat tidak ditemukan, isi manual.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** v1.4: saat form watermark dibuka dan foto punya GPS, cari alamat otomatis
     *  sekali saja per pembukaan form (memakai logika fetchAddress yang sama).
     *  Gagal/offline: wmAddress dibiarkan apa adanya (fallback "lat, lon" dari EXIF).
     *  Tanpa GPS: field dibiarkan kosong + toast singkat, alamat tidak dikarang. */
    private void autoGeocodeWm() {
        if (wmAutoGeoDone) return;
        wmAutoGeoDone = true;
        if (currentExif == null || !currentExif.hasGps) {
            Toast.makeText(this, "Foto ini tidak punya GPS, alamat tidak bisa dicari otomatis.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final double lat = currentExif.lat;
        final double lon = currentExif.lon;
        if (wmGeoStatus != null) {
            wmGeoStatus.setText("Mencari alamat dari GPS foto\u2026");
            wmGeoStatus.setVisibility(View.VISIBLE);
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final ExifUtil.GeoResult g = ExifUtil.reverseGeocodeDetailed(lat, lon);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (wmGeoStatus != null) wmGeoStatus.setVisibility(View.GONE);
                        if (!"wm".equals(screen)) return;
                        if (g != null && g.address != null) {
                            if (g.place != null && wmPlace != null) wmPlace.setText(g.place);
                            if (wmAddress != null) wmAddress.setText(g.address);
                            scheduleWmPreview();
                        }
                        // Gagal/offline: biarkan wmAddress = "lat, lon" dari EXIF.
                    }
                });
            }
        }).start();
    }

    /** Parse koordinat; koma desimal diterima ("112,747129"). fallback bila gagal. */
    private double parseCoord(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static class WmParams {
        double lat = Double.NaN, lon = Double.NaN;
        String place, address, weatherText, date, time, cond;
        Double temp;
        double scale = 1.0;
        boolean showMap, showAddr, showCoord, showLogo, showFlag, showWeather;
        int rotation;
        int jobTransparency = 40;
    }

    private String wmText(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    /** Baca semua field form watermark (dipakai pratinjau dan render final). */
    private WmParams readWmParams() {
        WmParams p = new WmParams();
        double flat = currentExif == null ? Double.NaN : currentExif.lat;
        double flon = currentExif == null ? Double.NaN : currentExif.lon;
        p.lat = parseCoord(wmText(wmLat), flat);
        p.lon = parseCoord(wmText(wmLon), flon);
        p.place = wmText(wmPlace);
        p.address = wmText(wmAddress);
        p.weatherText = wmText(wmWeatherText);
        p.date = wmText(wmDate);
        p.time = wmText(wmTime);
        String ts = wmText(wmTemp);
        try {
            p.temp = ts.isEmpty() ? null : Double.parseDouble(ts.replace(',', '.'));
        } catch (Exception ignored) {
            p.temp = null;
        }
        String[] condVals = new String[]{"cloudy", "partly", "sunny", "rain"};
        int ci = wmCond == null ? 0 : wmCond.getSelectedItemPosition();
        p.cond = (ci >= 0 && ci < condVals.length) ? condVals[ci] : "cloudy";
        p.scale = wmScalePct / 100.0;
        p.jobTransparency = wmJobTransPct;
        p.showMap = wmShowMap; p.showAddr = wmShowAddr; p.showCoord = wmShowCoord;
        p.showLogo = wmShowLogo; p.showFlag = wmShowFlag; p.showWeather = wmShowWeather;
        p.rotation = wmRotation;
        return p;
    }

    private Watermark.Opts toOpts(WmParams p) {
        Watermark.Opts o = new Watermark.Opts();
        o.lat = p.lat; o.lon = p.lon;
        o.hasGps = !Double.isNaN(p.lat) && !Double.isNaN(p.lon) && !(p.lat == 0 && p.lon == 0);
        o.place = p.place; o.address = p.address; o.weatherText = p.weatherText;
        o.dateStr = p.date; o.timeStr = p.time;
        o.scale = p.scale;
        o.showMap = p.showMap; o.showAddr = p.showAddr; o.showCoord = p.showCoord;
        o.showLogo = p.showLogo; o.showFlag = p.showFlag; o.showWeather = p.showWeather;
        o.cond = p.cond; o.tempC = p.temp;
        o.jobTransparency = p.jobTransparency;
        o.rotation = p.rotation;
        return o;
    }

    private void watchWmField(EditText e) {
        if (e == null) return;
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { scheduleWmPreview(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void scheduleWmPreview() {
        updDtHelp(); // helper format tanggal ikut berubah saat field berubah
        if (wmPreviewHandler != null && wmPreviewRunnable != null) {
            wmPreviewHandler.removeCallbacks(wmPreviewRunnable);
        }
        wmPreviewRunnable = new Runnable() {
            @Override public void run() { renderWmPreview(); }
        };
        wmPreviewHandler.postDelayed(wmPreviewRunnable, 600);
    }

    /** Render pratinjau watermark (lebar ~800px) di background thread. */
    private void renderWmPreview() {
        if (wmPreviewRunning || currentPhoto == null || wmPreview == null) return;
        if (!"wm".equals(screen)) return;
        wmPreviewRunning = true;
        final WmParams p = readWmParams();
        final boolean isJob = wmIsJob;
        final ExifUtil.ExifData ex = currentExif;
        if (wmPreviewStatus != null) wmPreviewStatus.setText("Memuat pratinjau...");
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap out = null;
                try {
                    Bitmap small = decodeSampled(currentPhoto.uri, 800);
                    if (small != null) {
                        Watermark.Opts o = toOpts(p);
                        if (isJob) out = Watermark.renderJob(MainActivity.this, small, ex, o);
                        else out = Watermark.renderKlasik(MainActivity.this, small, ex, o);
                        small.recycle();
                    }
                } catch (Exception ignored) {
                    out = null;
                }
                final Bitmap preview = out;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        wmPreviewRunning = false;
                        if (wmPreview == null) return;
                        if (wmPreviewBitmap != null) {
                            wmPreviewBitmap.recycle();
                            wmPreviewBitmap = null;
                        }
                        if (preview != null) {
                            wmPreviewBitmap = preview;
                            wmPreview.setImageBitmap(preview);
                            if (wmPreviewStatus != null) {
                                wmPreviewStatus.setText("Pratinjau: " + (isJob ? "Job Timestamp" : "Klasik") + " (bukan resolusi penuh)");
                            }
                        } else if (wmPreviewStatus != null) {
                            wmPreviewStatus.setText("Pratinjau gagal dimuat");
                        }
                    }
                });
            }
        }).start();
    }

    private void renderWatermark() {
        final WmParams wp = readWmParams();
        final boolean isJob = wmIsJob;
        Toast.makeText(this, "Merender watermark (unduh peta bila online)...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Bitmap src = decodeFull(currentPhoto.uri);
                    if (src == null) throw new Exception("Tidak bisa membaca foto");
                    Bitmap out;
                    Watermark.Opts o = toOpts(wp);
                    if (isJob) {
                        out = Watermark.renderJob(MainActivity.this, src, currentExif, o);
                    } else {
                        out = Watermark.renderKlasik(MainActivity.this, src, currentExif, o);
                    }
                    src.recycle();
                    String ext = "jpg";
                    File cache = new File(getCacheDir(), "wm." + ext);
                    FileOutputStream fos = new FileOutputStream(cache);
                    out.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    fos.close();
                    out.recycle();
                    ExifUtil.preserveGpsAndDate(currentExif, cache.getAbsolutePath());
                    String suffix = isJob ? "_job" : "_klasik";
                    String newName = ExifUtil.stem(currentPhoto.name) + suffix + ".jpg";
                    ExifUtil.saveFileToGaleriku(MainActivity.this, cache, newName, "image/jpeg");
                    toast("Tersimpan di DCIM/Galeriku/" + newName);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { reloadAndShowGrid(); }
                    });
                } catch (final Exception e) {
                    toast("Gagal render watermark: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------- browser universal (v1.4: pill ala Chrome, multi-tab, perbaikan Google) ----------
    private void buildBrowserView() {
        browserRoot = new LinearLayout(this);
        browserRoot.setOrientation(LinearLayout.VERTICAL);
        browserRoot.setBackgroundColor(BLACK);
        browserRoot.setPadding(8, 8, 8, 8);

        // Bar alamat ala Chrome: pill membulat + kotak jumlah tab.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(dp(20));
        pillBg.setColor(Color.rgb(43, 43, 43)); // #2b2b2b
        pill.setBackground(pillBg);
        pill.setPadding(dp(10), dp(2), dp(4), dp(2));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pillLp.setMargins(0, 0, dp(8), 0);

        urlBarIcon = makeIcon(FA_SEARCH, 14);
        urlBarIcon.setPadding(0, 0, dp(8), 0);
        pill.addView(urlBarIcon);

        urlInput = new EditText(this);
        urlInput.setHint("Cari atau ketik alamat web");
        urlInput.setHintTextColor(GRAY);
        urlInput.setTextColor(WHITE);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setTextSize(14);
        urlInput.setBackgroundColor(Color.TRANSPARENT);
        urlInput.setSingleLine(true);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pill.addView(urlInput, uLp);
        urlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    loadUrlFromBar();
                    return true;
                }
                return false;
            }
        });

        TextView reloadBtn = makeIcon(FA_ROTATE_RIGHT, 16);
        reloadBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        reloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                WebView wv = activeWebView();
                if (wv != null) wv.reload();
            }
        });
        pill.addView(reloadBtn);
        TextView menuBtn = makeIcon(FA_ELLIPSIS_V, 16);
        menuBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newBrowserTab(); }
        });
        pill.addView(menuBtn);
        bar.addView(pill, pillLp);

        tabCountBtn = new TextView(this);
        tabCountBtn.setText("1");
        tabCountBtn.setTextSize(14);
        tabCountBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        tabCountBtn.setTextColor(WHITE);
        tabCountBtn.setGravity(Gravity.CENTER);
        tabCountBtn.setBackground(roundedBg(SURFACE2, STROKE_GRAY, 1, 8));
        tabCountBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
        tabCountBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showingTabList = !showingTabList;
                showActiveTab();
            }
        });
        bar.addView(tabCountBtn);
        browserRoot.addView(bar);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        browserRoot.addView(progressBar, pLp);

        browserContent = new LinearLayout(this);
        browserContent.setOrientation(LinearLayout.VERTICAL);
        browserContent.setBackgroundColor(BLACK);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        browserRoot.addView(browserContent, cLp);
    }

    private BrowserTab activeTab() {
        if (activeTabIndex < 0 || activeTabIndex >= browserTabs.size()) return null;
        return browserTabs.get(activeTabIndex);
    }

    private WebView activeWebView() {
        BrowserTab t = activeTab();
        return t == null ? null : t.webView;
    }

    private void updateTabCount() {
        if (tabCountBtn != null) {
            tabCountBtn.setText(String.valueOf(Math.max(1, browserTabs.size())));
        }
    }

    private void updateUrlBarIcon() {
        if (urlBarIcon == null) return;
        BrowserTab t = activeTab();
        String u = t == null ? null : t.url;
        urlBarIcon.setText(u != null && u.startsWith("https://") ? FA_LOCK : FA_SEARCH);
    }

    /** Buat tab (WebView + client) tanpa mengaktifkannya. */
    private BrowserTab createBrowserTab() {
        final BrowserTab tab = new BrowserTab();
        tab.webView = new WebView(this);
        configureWebView(tab);
        browserTabs.add(tab);
        return tab;
    }

    /** Tab baru kosong + jadikan aktif. */
    private BrowserTab newBrowserTab() {
        BrowserTab tab = createBrowserTab();
        activeTabIndex = browserTabs.size() - 1;
        showingTabList = false;
        tab.webView.loadData(BROWSER_START_HTML, "text/html", "UTF-8");
        showActiveTab();
        return tab;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= browserTabs.size()) return;
        activeTabIndex = index;
        showingTabList = false;
        showActiveTab();
    }

    private void closeBrowserTab(int index) {
        if (index < 0 || index >= browserTabs.size()) return;
        BrowserTab t = browserTabs.remove(index);
        if (t.webView.getParent() != null) {
            ((ViewGroup) t.webView.getParent()).removeView(t.webView);
        }
        t.webView.destroy();
        if (browserTabs.isEmpty()) {
            activeTabIndex = -1;
            newBrowserTab();
            return;
        }
        if (activeTabIndex >= browserTabs.size()) activeTabIndex = browserTabs.size() - 1;
        else if (index < activeTabIndex) activeTabIndex--;
        showActiveTab();
    }

    /** Tampilkan tab aktif, atau daftar tab bila showingTabList. */
    private void showActiveTab() {
        if (browserContent == null) return;
        browserContent.removeAllViews();
        if (showingTabList) {
            browserContent.addView(buildTabListView(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            updateTabCount();
            return;
        }
        BrowserTab t = activeTab();
        if (t == null) return;
        if (t.webView.getParent() != null) {
            ((ViewGroup) t.webView.getParent()).removeView(t.webView);
        }
        browserContent.addView(t.webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateTabCount();
        updateUrlBarIcon();
        if (urlInput != null && !urlInput.hasFocus()) {
            urlInput.setText(t.url == null ? "" : t.url);
        }
    }

    /** Layar daftar tab: kartu vertikal (judul + URL + tombol tutup) + "Tab baru". */
    private ScrollView buildTabListView() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(BLACK);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout newTabBtn = makeFaButton(FA_PLUS, "Tab baru");
        newTabBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newBrowserTab(); }
        });
        list.addView(newTabBtn);
        for (int i = 0; i < browserTabs.size(); i++) {
            final int idx = i;
            final BrowserTab t = browserTabs.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackground(roundedBg(idx == activeTabIndex ? SURFACE : SURFACE2, 0, 0, 12));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, dp(6), 0, dp(6));
            card.setLayoutParams(clp);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            TextView title = makeLabel(t.title == null || t.title.isEmpty() ? "Tab baru" : t.title);
            title.setTextSize(15);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            TextView url = new TextView(this);
            url.setText(t.url == null ? "" : t.url);
            url.setTextSize(12);
            url.setTextColor(GRAY);
            url.setSingleLine(true);
            url.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(url);
            card.addView(texts, tlp);
            TextView x = makeIcon(FA_XMARK, 18);
            x.setPadding(dp(12), dp(12), dp(4), dp(12));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { closeBrowserTab(idx); }
            });
            card.addView(x);
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchToTab(idx); }
            });
            list.addView(card);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BLACK);
        scroll.addView(list);
        return scroll;
    }

    /** Pulihkan tab dari savedInstanceState; bila tidak ada, buat 1 tab kosong. */
    private void restoreBrowserTabs(Bundle saved) {
        if (saved != null) {
            int n = saved.getInt("browserTabCount", 0);
            for (int i = 0; i < n; i++) {
                Bundle b = saved.getBundle("browserTab_" + i);
                BrowserTab tab = createBrowserTab();
                if (b != null) {
                    tab.webView.restoreState(b);
                    tab.url = tab.webView.getUrl();
                    tab.title = tab.webView.getTitle();
                }
            }
            if (!browserTabs.isEmpty()) {
                activeTabIndex = Math.min(saved.getInt("browserActiveTab", 0),
                        browserTabs.size() - 1);
                showingTabList = false;
                showActiveTab();
                return;
            }
        }
        newBrowserTab();
    }

    /** Semua setting + client WebView per tab (file chooser SAF, blob, geolocation,
     *  User-Agent modern, shouldOverrideUrlLoading, onCreateWindow -> tab baru,
     *  halaman error di dalam WebView). */
    private void configureWebView(final BrowserTab tab) {
        final WebView wv = tab.webView;
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // User-Agent modern: buang "Version/4.0" lama, pastikan ada Chrome Mobile.
        String ua = s.getUserAgentString();
        if (ua == null) ua = "";
        ua = ua.replace("Version/4.0 ", "").replace("Version/4.0", "");
        if (!ua.contains("Chrome/")) {
            ua += " Chrome/120.0.0.0 Mobile Safari/537.36";
        } else if (!ua.contains("Mobile")) {
            ua = ua.replace("Safari/537.36", "Mobile Safari/537.36");
        }
        s.setUserAgentString(ua);
        wv.addJavascriptInterface(new BlobHandler(), "AndroidBlob");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false; // navigasi tetap di WebView
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // navigasi tetap di WebView
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url != null && !url.startsWith("data:")) tab.url = url;
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);
                }
                updateUrlBarIcon();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && !url.startsWith("data:")) tab.url = url;
                tab.title = view.getTitle();
                if (progressBar != null) {
                    progressBar.setProgress(100);
                    progressBar.setVisibility(View.GONE);
                }
                if (urlInput != null && !urlInput.hasFocus()
                        && url != null && !url.startsWith("data:")) {
                    urlInput.setText(url);
                }
                updateUrlBarIcon();
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showBrowserError(tab, view, errorCode, description, failingUrl);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame()) return;
                String url = request == null ? tab.url : request.getUrl().toString();
                showBrowserError(tab, view, error == null ? -1 : error.getErrorCode(),
                        error == null ? "" : String.valueOf(error.getDescription()), url);
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (tab.fileCallback != null) tab.fileCallback.onReceiveValue(null);
                tab.fileCallback = callback;
                // v1.6 (e-SDM): accept situs bisa berupa EKSTENSI (".jpg,.jpeg,.png"),
                // bukan MIME. setType mentah dengan itu = MIME tidak valid -> sistem
                // menampilkan "Tidak ada apl yang dapat melakukan tindakan".
                // Sanitasi dulu ke MIME yang valid + EXTRA_MIME_TYPES.
                ArrayList<String> mimes = new ArrayList<String>();
                try {
                    String[] accept = params.getAcceptTypes();
                    if (accept != null) {
                        for (int ai = 0; ai < accept.length; ai++) {
                            if (accept[ai] == null) continue;
                            String[] parts = accept[ai].split(",");
                            for (int pi = 0; pi < parts.length; pi++) {
                                String m = mimeFromAccept(parts[pi]);
                                if (m != null && !mimes.contains(m)) mimes.add(m);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (mimes.isEmpty()) mimes.add("image/*");
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mimes.get(0));
                if (mimes.size() > 1) {
                    i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                }
                try {
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                } catch (Exception ignored) {}
                try {
                    startActivityForResult(Intent.createChooser(i, "Pilih foto"), REQ_FILE);
                } catch (android.content.ActivityNotFoundException e) {
                    // Fallback terakhir: image/* murni.
                    try {
                        Intent f = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        f.addCategory(Intent.CATEGORY_OPENABLE);
                        f.setType("image/*");
                        startActivityForResult(Intent.createChooser(f, "Pilih foto"), REQ_FILE);
                    } catch (Exception e2) {
                        if (tab.fileCallback != null) {
                            tab.fileCallback.onReceiveValue(null);
                            tab.fileCallback = null;
                        }
                        Toast.makeText(MainActivity.this,
                                "Tidak ada pemilih file di perangkat — coba lewat aplikasi Galeri",
                                Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            }
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                BrowserTab nt = createBrowserTab();
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(nt.webView);
                resultMsg.sendToTarget();
                activeTabIndex = browserTabs.size() - 1;
                showingTabList = false;
                showActiveTab();
                return true;
            }
        });
        wv.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (url != null && url.startsWith("blob:")) {
                    String script = "(function(){fetch('" + url + "')"
                            + ".then(function(r){return r.blob();})"
                            + ".then(function(b){var fr=new FileReader();"
                            + "fr.onloadend=function(){AndroidBlob.save(fr.result);};"
                            + "fr.readAsDataURL(b);})"
                            + ".catch(function(e){AndroidBlob.error(String(e));});})()";
                    tab.webView.evaluateJavascript(script, null);
                } else {
                    downloadWithManager(url, contentDisposition, mimeType);
                }
            }
        });
    }

    /** Halaman error DI DALAM WebView tab aktif + Toast singkat (v1.4).
     *  "Coba lagi" adalah link ke URL yang gagal, jadi ketuk link memuat ulang. */
    private void showBrowserError(final BrowserTab tab, WebView view, int errorCode,
                                  String description, String failingUrl) {
        if (failingUrl == null || failingUrl.startsWith("data:")) return;
        long now = System.currentTimeMillis();
        if (failingUrl.equals(tab.lastErrorUrl) && now - tab.lastErrorAt < 2000) return;
        tab.lastErrorUrl = failingUrl;
        tab.lastErrorAt = now;
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        Toast.makeText(this, "Gagal memuat halaman: " + description, Toast.LENGTH_LONG).show();
        String safeUrl = failingUrl.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
        String safeDesc = (description == null ? "" : description).replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        String html = "<html><body style='background:#000;color:#fff;font-family:sans-serif;padding:24px'>"
                + "<h3>Halaman gagal dimuat</h3>"
                + "<p>Kode: " + errorCode + "</p>"
                + "<p>" + safeDesc + "</p>"
                + "<p>" + safeUrl + "</p>"
                + "<p><a href=\"" + safeUrl + "\" style='display:inline-block;background:#5f6368;color:#fff;padding:12px 22px;border-radius:14px;text-decoration:none;font-weight:bold'>Coba lagi</a></p>"
                + "</body></html>";
        view.loadData(html, "text/html", "UTF-8");
    }

    /** v1.6: ubah satu entri accept file chooser menjadi MIME valid.
     *  ".jpg"/"jpg" -> image/jpeg, "image/jpg" -> image/jpeg,
     *  "image/*"/"image/jpeg" -> apa adanya, tidak dikenal -> null. */
    private String mimeFromAccept(String a) {
        if (a == null) return null;
        a = a.trim().toLowerCase(Locale.US);
        if (a.isEmpty()) return null;
        if (a.equals("*/*")) return "*/*";
        if (a.equals("image/jpg")) return "image/jpeg";
        if (a.contains("/")) {
            String t = a.substring(0, a.indexOf('/'));
            if (t.equals("image") || t.equals("video") || t.equals("audio")
                    || t.equals("application") || t.equals("text")) return a;
            return null;
        }
        if (a.startsWith(".")) a = a.substring(1);
        if (a.equals("jpg") || a.equals("jpeg")) return "image/jpeg";
        if (a.equals("png")) return "image/png";
        if (a.equals("gif")) return "image/gif";
        if (a.equals("webp")) return "image/webp";
        if (a.equals("bmp")) return "image/bmp";
        if (a.equals("heic") || a.equals("heif")) return "image/heic";
        if (a.equals("pdf")) return "application/pdf";
        if (a.equals("mp4")) return "video/mp4";
        if (a.equals("mov")) return "video/quicktime";
        if (a.equals("mp3")) return "audio/mpeg";
        return null;
    }

    private void loadUrlFromBar() {
        BrowserTab tab = activeTab();
        if (tab == null) tab = newBrowserTab();
        String input = urlInput.getText().toString().trim();
        String u = resolveBrowserInput(input);
        if (u == null) {
            Toast.makeText(this, "Ketik dulu alamat situs atau kata kunci.", Toast.LENGTH_SHORT).show();
            return;
        }
        tab.url = u;
        tab.webView.loadUrl(u);
        if (showingTabList) {
            showingTabList = false;
            showActiveTab();
        }
    }

    /** Resolusi input bar alamat (v1.4): kosong -> null; http(s) apa adanya;
     *  ada spasi / tanpa titik -> pencarian Google; mirip domain -> tambah https://. */
    private String resolveBrowserInput(String input) {
        if (input == null || input.isEmpty()) return null;
        if (input.startsWith("http://") || input.startsWith("https://")
                || input.startsWith("about:")) {
            return input;
        }
        if (input.contains(" ") || !input.contains(".")) {
            try {
                return "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8");
            } catch (Exception e) {
                return "https://www.google.com/search?q=" + input.replace(" ", "+");
            }
        }
        return "https://" + input;
    }

    private void downloadWithManager(String url, String contentDisposition, String mimeType) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);
            req.addRequestHeader("User-Agent", "Galeriku");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Galeriku/" + fileName);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Toast.makeText(this, "Mengunduh ke Download/Galeriku: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unduhan gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveDataUrl(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            String header = dataUrl.substring(0, comma);
            String base64 = dataUrl.substring(comma + 1);
            String ext = "bin";
            String mime = "application/octet-stream";
            if (header.contains("image/jpeg")) { ext = "jpg"; mime = "image/jpeg"; }
            else if (header.contains("image/png")) { ext = "png"; mime = "image/png"; }
            else if (header.contains("application/zip")) { ext = "zip"; mime = "application/zip"; }
            else if (header.contains("image/webp")) { ext = "webp"; mime = "image/webp"; }
            else if (header.contains("application/pdf")) { ext = "pdf"; mime = "application/pdf"; }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final String fileName = "browser_" + stamp + "." + ext;
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            boolean isImage = mime.startsWith("image/");
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            Uri uri;
            if (isImage) {
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, ExifUtil.GALERIKU_RELATIVE);
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Galeriku");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
            if (uri == null) throw new Exception("Gagal membuat file unduhan");
            OutputStream out = getContentResolver().openOutputStream(uri);
            out.write(bytes);
            out.close();
            final String loc = isImage ? "DCIM/Galeriku/" + fileName : "Download/Galeriku/" + fileName;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Toast.makeText(MainActivity.this, "Tersimpan di " + loc, Toast.LENGTH_LONG).show();
                }
            });
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Toast.makeText(MainActivity.this, "Unduhan gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // ---------- hasil pemilih ----------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            BrowserTab tab = activeTab();
            if (tab == null || tab.fileCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<Uri> list = new ArrayList<Uri>();
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri u = clip.getItemAt(i).getUri();
                        if (u != null) list.add(u);
                    }
                } else if (data.getData() != null) {
                    list.add(data.getData());
                }
                for (Uri u : list) {
                    try {
                        getContentResolver().takePersistableUriPermission(u,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                }
                if (!list.isEmpty()) results = list.toArray(new Uri[0]);
            }
            tab.fileCallback.onReceiveValue(results);
            tab.fileCallback = null;
            return;
        }
        if (requestCode == REQ_COPY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri srcUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(srcUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                String srcName = "";
                try {
                    Cursor c = getContentResolver().query(srcUri, null, null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (ni >= 0) srcName = c.getString(ni);
                        }
                        c.close();
                    }
                } catch (Exception ignored) {}
                copySourceName = srcName;
                copySourceExif = ExifUtil.read(getContentResolver(), srcUri);
                if (copySummary != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Foto sumber: ").append(copySourceName).append("\n");
                    sb.append("GPS sumber: ").append(copySourceExif.latLonText()).append("\n");
                    sb.append("Waktu sumber: ").append(copySourceExif.dateTimeOriginal == null ? "-" : copySourceExif.dateTimeOriginal).append("\n");
                    sb.append("Kamera sumber: ").append(copySourceExif.make == null ? "" : copySourceExif.make).append(" ")
                            .append(copySourceExif.model == null ? "" : copySourceExif.model).append("\n");
                    sb.append("Tag sumber terisi: ").append(copySourceExif.tags.size()).append("\n\n");
                    sb.append("SEBELUM (foto saat ini):\n").append(buildExifText(currentPhoto, currentExif));
                    sb.append("\nSESUDAH (akan diterapkan):\n");
                    sb.append("GPS: ").append(copySourceExif.latLonText()).append("\n");
                    sb.append("DateTimeOriginal: ").append(copySourceExif.dateTimeOriginal == null ? "-" : copySourceExif.dateTimeOriginal).append("\n");
                    copySummary.setText(sb.toString());
                }
                Toast.makeText(this, "Foto sumber dibaca: " + copySourceName, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (currentTab == 1) {
            if (showingTabList) {
                showingTabList = false;
                showActiveTab();
                return;
            }
            WebView wv = activeWebView();
            if (wv != null && wv.canGoBack()) {
                wv.goBack();
                return;
            }
            if (browserTabs.size() > 1) {
                closeBrowserTab(activeTabIndex);
                return;
            }
            showAlbum();
            return;
        }
        if ("viewer".equals(screen)) {
            backFromViewer();
            return;
        }
        if ("grid".equals(screen)) {
            currentQuery = "";
            showAlbum();
            return;
        }
        if ("edit".equals(screen) || "copy".equals(screen) || "wm".equals(screen)) {
            if (currentPhoto != null) showViewer(currentList, currentIndex);
            else showAlbum();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("browserTabCount", browserTabs.size());
        outState.putInt("browserActiveTab", activeTabIndex);
        for (int i = 0; i < browserTabs.size(); i++) {
            Bundle b = new Bundle();
            browserTabs.get(i).webView.saveState(b);
            outState.putBundle("browserTab_" + i, b);
        }
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab t : browserTabs) {
            if (t.webView != null) {
                if (t.webView.getParent() != null) {
                    ((ViewGroup) t.webView.getParent()).removeView(t.webView);
                }
                t.webView.destroy();
            }
        }
        browserTabs.clear();
        super.onDestroy();
    }

    @SuppressWarnings("unused")
    private ContentResolver resolver() { return getContentResolver(); }
    @SuppressWarnings("unused")
    private Context context() { return this; }
}
