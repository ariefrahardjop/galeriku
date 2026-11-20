// == Userscript Galeriku ==
// Nama: DTSEN Scraper (SIKS Data BNBA DTSEN)
// Sumber: konversi dari scrape-dtsen-final2.js (Playwright / Node.js)
//   + captcha-solver-claude.js (modul solver CAPTCHA Claude Vision)
// Target: https://siks.kemensos.go.id/data-bnba-dtsen
// Pola URL: *://siks.kemensos.go.id/data-bnba-dtsen*
// Cara pakai: buka halaman Data BNBA DTSEN di browser Galeriku (pastikan
// sudah login SIKS), panel "DTSEN Scraper" muncul di kanan bawah.
//   1. Isi config AI di panel (Endpoint + API Key + Model) lalu Simpan
//      config — bila dikosongkan, CAPTCHA dijawab manual lewat panel.
//   2. Impor CSV berisi kolom "nik" (tombol Impor CSV) atau tempel NIK
//      di kotak teks lalu "Tambah NIK dari teks".
//   3. Tekan "Mulai". Hasil tampil di panel per dataset (5 tab CSV).
// Progres (NIK yang sudah diproses + daftar NIK + SEMUA hasil 5 dataset)
// tersimpan di localStorage SETIAP selesai satu NIK, jadi bila halaman
// di-reload, aplikasi ditutup/di-swipe, atau proses terputus: tekan
// "Mulai" lagi untuk lanjut dari NIK yang belum diproses, tanpa
// mengulang yang sudah selesai dan tanpa kehilangan hasil.
//
// APA YANG BERUBAH DARI VERSI PLAYWRIGHT (baca sebelum memakai):
// - Tidak ada lagi chromium.launch / page.goto / page.locator / keyboard /
//   fs / path / dotenv / readline. Semua aksi langsung di DOM halaman
//   (querySelector, click). Halaman sudah dibuka user dalam keadaan login.
// - TIDAK ADA reload halaman per NIK (versi Playwright me-reload sesudah
//   tiap NIK). Reload akan menghancurkan state run di memori (antrean NIK,
//   flag jalan/berhenti) dan memaksa auto-resume yang bertentangan dengan
//   desain tombol Mulai manual. Penggantinya: reset form lewat DOM sesudah
//   tiap NIK (tutup modal, kosongkan input NIK + CAPTCHA, refresh gambar
//   CAPTCHA). Progres tetap tersimpan ke localStorage tiap NIK, jadi
//   reload manual / aplikasi ditutup pun aman: tekan Mulai untuk lanjut.
// - Input React diisi lewat "native setter" + event input (set .value
//   biasa diabaikan React). waitForSelector diganti polling (waitFor /
//   waitForFn). ESC keyboard diganti dispatch KeyboardEvent ke dialog.
// - Simpan XLSX 5 sheet diganti 5 CSV (satu per dataset: Data KK,
//   Anggota Keluarga, Dapodik, Riwayat Bansos, Aset) — tampil di panel
//   (tab per dataset + Salin/Unduh), progres di localStorage.
// - sharp (preprocessing gambar CAPTCHA) diganti Canvas 2D murni:
//   gambar <img> CAPTCHA digambar ke canvas offscreen 500x150, lalu di JS:
//   greyscale + normalize + threshold 128 + hapus piksel hitam terisolasi
//   (tetangga hitam < 2 dalam 3x3, sama seperti kode asli), lalu
//   canvas.toDataURL('image/png') sebagai base64 untuk API.
// - tesseract.js (fallback OCR) TIDAK dikonversi — butuh worker + data
//   bahasa eksternal yang tidak bisa dibawa ke userscript. Penggantinya:
//   bila AI gagal / tidak ada kunci, run BERHENTI di titik CAPTCHA dan
//   panel meminta user mengetik jawaban CAPTCHA, lalu lanjut (tanpa reload
//   atau refresh gambar saat menunggu).
// - readNIKFromCSV diganti Impor CSV fleksibel: file CSV apa pun asal ada
//   kolom header "nik" (trim + lowercase, persis "nik") — nilai kolom itu
//   yang dipakai, kolom lain diabaikan. Bila file tanpa header (baris
//   pertama berisi angka), diperlakukan sebagai daftar satu NIK per baris
//   (format lama). NIK dibersihkan (trim, hanya digit), duplikat dibuang.
// - KEAMANAN: file ini TIDAK berisi API key / endpoint / nilai .env apa
//   pun (repo GitHub galeriku PUBLIK). Endpoint + API Key + Model diisi
//   lewat field di panel, disimpan di localStorage HP saja (kunci
//   dtsen.cfg.v1), kosong secara default. Yang boleh hardcode hanya URL
//   halaman SIKS, prompt pembaca CAPTCHA, dan nama model DEFAULT
//   (kr/claude-sonnet-4.5) sebagai nilai awal field — nama model bukan
//   rahasia. Endpoint .env di PC adalah http://localhost:20128/v1 — itu
//   hanya jalan di PC; di HP, localhost adalah HP itu sendiri, jadi isi
//   alamat 9Router yang bisa dijangkau HP (alamat Tailscale PC atau URL
//   publik router), bukan localhost.
// - Fetch AI lintas origin dari halaman SIKS bisa gagal karena CORS
//   (tergantung header server endpoint). Bila gagal, skrip mencatatnya
//   jelas di log dan jatuh ke CAPTCHA manual panel.
// - Modal + NIK termasking mengikuti pelajaran v1.37b/c/d dari userscript
//   SIKS: baris diambil FRESH dari DOM per indeks (React me-render ulang
//   sesudah klik); hover + klik ATOMIK dalam satu blok sinkron tanpa jeda;
//   event mouse membawa relatedTarget; scroll MINIMAL (hanya bila baris
//   belum terlihat penuh, block:'nearest', behavior:'instant'); klik
//   lengkap pointerover/pointerdown/mousedown/pointerup/mouseup/click;
//   verifikasi unmask dengan polling (16 digit, tanpa '*') + satu kali
//   retry bila masih termasking; PERINGATAN di log bila tetap termasking.
// - Mode melayang (aplikasi Galeriku v1.37): skrip melaporkan progres
//   lewat Galeriku.reportProgress() (no-op bila tidak aktif); tombol
//   Berhenti di panel melayang/notifikasi menyetel
//   window.__dtsenStopRequested yang diperiksa di antara NIK; status
//   selesai berawalan "Selesai: " supaya service menutup overlay sendiri.
// - Skrip TIDAK jalan otomatis saat halaman dibuka; tekan "Mulai" di panel.
//   Tombol "Berhenti" menghentikan di antara NIK (bukan di tengah baris).
// - CATATAN perilaku asli yang dipertahankan: "NIK KK", RT, dan RW di
//   Data KK selalu kosong — skrip Playwright asli tidak pernah mengisi
//   ketiganya (variabelnya diinisialisasi "" dan tidak pernah di-assign).
//   NIK pengurus yang "Data tidak ditemukan" DICATAT sebagai selesai
//   (berbeda dari asli yang mengulanginya tiap run) supaya resume tidak
//   membuang CAPTCHA untuk NIK yang sudah diketahui tidak ada.
(function () {
  'use strict';

  // Bila skrip dimuat ulang (refresh halaman), hapus panel lama dulu.
  var oldPanel = document.getElementById('dtsen-scraper-panel');
  if (oldPanel) oldPanel.remove();
  var oldFab = document.getElementById('dtsen-scraper-fab');
  if (oldFab) oldFab.remove();

  // ====== KONFIGURASI ======
  var TIMEOUT = 30000;
  var LS_KEY = 'dtsen.progress.v1';   // progres + hasil (localStorage HP)
  var LS_CFG = 'dtsen.cfg.v1';        // endpoint + api key + model (HP saja)
  var DEFAULT_MODEL = 'kr/claude-sonnet-4.5';  // nilai awal field Model (bukan rahasia)
  var CAPTCHA_PROMPT = 'Baca gambar ini dan tulis persis apa yang kamu lihat dalam format: angka operator angka\n\nContoh jawaban yang benar:\n- 14 - 2\n- 5 + 3\n- 8 * 2\n- 12 / 4\n\nHANYA tulis angka, operator, angka. JANGAN hitung hasilnya.';

  var delay = function (ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  };

  // ====== STATE ======
  var running = false;
  var stopRequested = false;
  var nikList = [];           // daftar NIK dari impor/tempel (tersimpan di LS)
  var processedNik = {};      // map nik -> true (sudah diproses)
  var results = {             // 5 dataset (mengganti 5 sheet XLSX)
    dataKK: [], dataART: [], dataDapodik: [], dataBansos: [], dataAset: []
  };

  // ====== PANEL UI ======
  var panel, logBox, statusLine, progressLine, nikCountLine;
  var btnMulai, btnBerhenti;
  var cfgEndpointEl, cfgKeyEl, cfgModelEl, cfgStatusEl;
  var nikTextEl, resultArea, resultTabsEl;
  var captchaBoxEl, captchaImgEl, captchaInputEl;
  var activeDataset = 'dataKK';

  var DATASETS = [
    { key: 'dataKK', label: 'Data KK', file: 'dtsen-data-kk.csv' },
    { key: 'dataART', label: 'Anggota', file: 'dtsen-data-art.csv' },
    { key: 'dataDapodik', label: 'Dapodik', file: 'dtsen-data-dapodik.csv' },
    { key: 'dataBansos', label: 'Bansos', file: 'dtsen-data-bansos.csv' },
    { key: 'dataAset', label: 'Aset', file: 'dtsen-data-aset.csv' }
  ];

  function log(msg) {
    var line = document.createElement('div');
    line.textContent = msg;
    logBox.appendChild(line);
    logBox.scrollTop = logBox.scrollHeight;
    if (window.console && console.log) console.log('[DTSEN] ' + msg);
  }

  function setStatus(msg) { statusLine.textContent = msg; if (msg) reportFloat(msg); }
  function setProgress(msg) { progressLine.textContent = msg; if (msg) reportFloat(msg); }

  // Mode melayang: laporkan progres ke panel melayang + notifikasi
  // aplikasi. No-op bila bridge tidak ada / mode melayang tidak aktif.
  function reportFloat(msg) {
    try {
      if (window.Galeriku && Galeriku.reportProgress) Galeriku.reportProgress(msg);
    } catch (e) {}
  }
  // Flag berhenti gabungan: tombol Berhenti di panel skrip (stopRequested)
  // ATAU tombol Berhenti di panel melayang/notifikasi (disetel aplikasi
  // lewat window.__dtsenStopRequested).
  function stopRequestedNow() {
    return stopRequested || window.__dtsenStopRequested === true;
  }

  function buildPanel() {
    panel = document.createElement('div');
    panel.id = 'dtsen-scraper-panel';
    panel.style.cssText = 'position:fixed;right:8px;bottom:8px;width:340px;max-width:92vw;' +
      'max-height:72vh;overflow:auto;background:#111;color:#fff;border:1px solid #555;' +
      'border-radius:10px;padding:10px;font:12px/1.5 sans-serif;z-index:999999;' +
      'box-shadow:0 4px 18px rgba(0,0,0,.5);';
    panel.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:center;">' +
      '<b>DTSEN Scraper</b>' +
      '<button id="dtsen-min" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:2px 8px;">—</button>' +
      '</div>' +
      '<div id="dtsen-status" style="margin:6px 0 2px;color:#9fd;">Siap. Pastikan sudah login SIKS dan berada di halaman Data BNBA DTSEN.</div>' +
      '<div id="dtsen-progress" style="color:#fc6;"></div>' +
      // --- Config AI ---
      '<div style="border:1px solid #444;border-radius:6px;padding:8px;margin:8px 0;">' +
      '<b>Config AI (CAPTCHA)</b>' +
      '<div style="margin-top:6px;">Endpoint 9Router<br>' +
      '<input id="dtsen-cfg-endpoint" type="text" placeholder="http://192.168.x.x:20128/v1" style="width:100%;background:#000;color:#fff;border:1px solid #555;border-radius:6px;padding:5px;"></div>' +
      '<div style="color:#fc6;font-size:11px;margin:3px 0 6px;">Endpoint .env di PC adalah http://localhost:20128/v1 — itu hanya jalan di PC. Di HP, localhost adalah HP itu sendiri: isi alamat 9Router yang bisa dijangkau HP (alamat Tailscale PC atau URL publik router), bukan localhost.</div>' +
      '<div>API Key<br>' +
      '<input id="dtsen-cfg-key" type="password" placeholder="API key 9Router" style="width:100%;background:#000;color:#fff;border:1px solid #555;border-radius:6px;padding:5px;"></div>' +
      '<div style="margin-top:6px;">Model<br>' +
      '<input id="dtsen-cfg-model" type="text" style="width:100%;background:#000;color:#fff;border:1px solid #555;border-radius:6px;padding:5px;"></div>' +
      '<div style="display:flex;gap:6px;margin-top:6px;">' +
      '<button id="dtsen-cfg-simpan" style="background:#0a7;color:#fff;border:0;border-radius:6px;padding:5px 10px;">Simpan config</button>' +
      '<button id="dtsen-cfg-hapus" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;">Hapus config</button>' +
      '</div>' +
      '<div id="dtsen-cfg-status" style="color:#9fd;font-size:11px;margin-top:4px;">Config disimpan di HP ini saja, tidak pernah masuk ke file skrip.</div>' +
      '</div>' +
      // --- Daftar NIK ---
      '<div style="border:1px solid #444;border-radius:6px;padding:8px;margin:8px 0;">' +
      '<b>Daftar NIK</b>' +
      '<div id="dtsen-nik-count" style="color:#9fd;font-size:11px;margin:3px 0;">Belum ada NIK.</div>' +
      '<textarea id="dtsen-nik-text" placeholder="Tempel NIK di sini — satu per baris, atau teks CSV dengan kolom nik..." style="width:100%;height:56px;background:#000;color:#cfc;border:1px solid #555;border-radius:6px;font:11px monospace;"></textarea>' +
      '<div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:6px;">' +
      '<button id="dtsen-nik-tambah" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;">Tambah NIK dari teks</button>' +
      '<button id="dtsen-nik-impor" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;">Impor CSV</button>' +
      '</div>' +
      '</div>' +
      // --- CAPTCHA manual (disembunyikan sampai dibutuhkan) ---
      '<div id="dtsen-captcha-box" style="border:1px solid #a33;border-radius:6px;padding:8px;margin:8px 0;display:none;">' +
      '<b style="color:#f99;">CAPTCHA manual — ketik jawaban di bawah ini</b>' +
      '<div><img id="dtsen-captcha-img" style="display:block;max-width:100%;background:#fff;border-radius:6px;margin:6px 0;"></div>' +
      '<div style="display:flex;gap:6px;">' +
      '<input id="dtsen-captcha-input" type="text" inputmode="numeric" placeholder="Jawaban (angka)" style="flex:1;background:#000;color:#fff;border:1px solid #555;border-radius:6px;padding:5px;">' +
      '<button id="dtsen-captcha-kirim" style="background:#0a7;color:#fff;border:0;border-radius:6px;padding:5px 10px;">Kirim</button>' +
      '<button id="dtsen-captcha-batal" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;">Batal</button>' +
      '</div>' +
      '<div style="color:#fc6;font-size:11px;margin-top:4px;">Gambar CAPTCHA tidak di-refresh selama menunggu — jawab gambar yang tampil.</div>' +
      '</div>' +
      // --- Tombol utama ---
      '<div style="display:flex;flex-wrap:wrap;gap:6px;margin:8px 0;">' +
      '<button id="dtsen-mulai" style="background:#0a7;color:#fff;border:0;border-radius:6px;padding:6px 10px;">Mulai</button>' +
      '<button id="dtsen-berhenti" style="background:#a33;color:#fff;border:0;border-radius:6px;padding:6px 10px;" disabled>Berhenti</button>' +
      '<button id="dtsen-hapus" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:6px 10px;">Hapus progres</button>' +
      '</div>' +
      // --- Hasil (5 tab CSV) ---
      '<div style="border:1px solid #444;border-radius:6px;padding:8px;margin:8px 0;">' +
      '<b>Hasil (5 CSV)</b>' +
      '<div id="dtsen-tabs" style="display:flex;flex-wrap:wrap;gap:4px;margin:6px 0;"></div>' +
      '<textarea id="dtsen-csv" readonly style="width:100%;height:110px;background:#000;color:#cfc;' +
      'border:1px solid #555;border-radius:6px;font:11px monospace;display:none;"></textarea>' +
      '<div style="display:flex;gap:6px;margin-top:6px;">' +
      '<button id="dtsen-salin" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;">Salin CSV</button>' +
      '<button id="dtsen-unduh" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;">Unduh CSV</button>' +
      '</div>' +
      '</div>' +
      '<div id="dtsen-log" style="max-height:140px;overflow:auto;background:#000;' +
      'border:1px solid #444;border-radius:6px;padding:6px;margin-top:8px;font:11px monospace;"></div>';

    document.body.appendChild(panel);
    logBox = panel.querySelector('#dtsen-log');
    statusLine = panel.querySelector('#dtsen-status');
    progressLine = panel.querySelector('#dtsen-progress');
    nikCountLine = panel.querySelector('#dtsen-nik-count');
    btnMulai = panel.querySelector('#dtsen-mulai');
    btnBerhenti = panel.querySelector('#dtsen-berhenti');
    cfgEndpointEl = panel.querySelector('#dtsen-cfg-endpoint');
    cfgKeyEl = panel.querySelector('#dtsen-cfg-key');
    cfgModelEl = panel.querySelector('#dtsen-cfg-model');
    cfgStatusEl = panel.querySelector('#dtsen-cfg-status');
    nikTextEl = panel.querySelector('#dtsen-nik-text');
    resultArea = panel.querySelector('#dtsen-csv');
    resultTabsEl = panel.querySelector('#dtsen-tabs');
    captchaBoxEl = panel.querySelector('#dtsen-captcha-box');
    captchaImgEl = panel.querySelector('#dtsen-captcha-img');
    captchaInputEl = panel.querySelector('#dtsen-captcha-input');

    var fab = document.createElement('button');
    fab.id = 'dtsen-scraper-fab';
    fab.textContent = 'DTSEN';
    fab.style.cssText = 'position:fixed;right:8px;bottom:8px;z-index:999999;background:#0a7;' +
      'color:#fff;border:0;border-radius:20px;padding:8px 14px;font:13px sans-serif;display:none;';
    document.body.appendChild(fab);

    panel.querySelector('#dtsen-min').addEventListener('click', function () {
      panel.style.display = 'none';
      fab.style.display = 'block';
    });
    fab.addEventListener('click', function () {
      fab.style.display = 'none';
      panel.style.display = 'block';
    });

    // Tabs hasil
    DATASETS.forEach(function (ds) {
      var b = document.createElement('button');
      b.textContent = ds.label + ' (0)';
      b.setAttribute('data-key', ds.key);
      b.style.cssText = 'background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:4px 8px;';
      b.addEventListener('click', function () {
        activeDataset = ds.key;
        showResults();
      });
      resultTabsEl.appendChild(b);
    });

    btnMulai.addEventListener('click', function () { run(); });
    btnBerhenti.addEventListener('click', function () {
      stopRequested = true;
      setStatus('Berhenti diminta — menyelesaikan NIK yang sedang berjalan...');
    });
    panel.querySelector('#dtsen-hapus').addEventListener('click', function () {
      try { localStorage.removeItem(LS_KEY); } catch (e) {}
      results = { dataKK: [], dataART: [], dataDapodik: [], dataBansos: [], dataAset: [] };
      processedNik = {};
      resultArea.value = '';
      resultArea.style.display = 'none';
      setProgress('');
      updateNikCount();
      showResults();
      setStatus('Progres dihapus. Siap mulai dari awal.');
      log('Progres dihapus.');
    });
    panel.querySelector('#dtsen-salin').addEventListener('click', copyActiveCsv);
    panel.querySelector('#dtsen-unduh').addEventListener('click', downloadActiveCsv);
    panel.querySelector('#dtsen-nik-tambah').addEventListener('click', addNikFromText);
    panel.querySelector('#dtsen-nik-impor').addEventListener('click', importCsv);
    panel.querySelector('#dtsen-cfg-simpan').addEventListener('click', saveConfigFromPanel);
    panel.querySelector('#dtsen-cfg-hapus').addEventListener('click', clearConfigFromPanel);
    panel.querySelector('#dtsen-captcha-kirim').addEventListener('click', submitManualCaptcha);
    panel.querySelector('#dtsen-captcha-batal').addEventListener('click', cancelManualCaptcha);

    // v1.1: halaman SIKS (MUI) menahan fokus di dalam dialog/modal-nya
    // (focus trap): begitu fokus pindah ke elemen di luar dialog, halaman
    // menariknya kembali — input panel di luar dialog jadi tidak bisa
    // diisi, padahal di web lain kolom yang sama bisa diisi. Dua pengaman,
    // keduanya hanya menyentuh event dari dalam panel:
    // (1) hentikan event focusin dari panel sebelum sampai ke document,
    //     supaya trap halaman tidak pernah melihat fokus pindah ke panel;
    // (2) bila fokus tetap dicuri (mis. halaman me-refocus inputnya
    //     sendiri), kembalikan fokus ke field panel sekali sesudah 120ms
    //     — hanya bila fokus dicuri <1 dtk sesudah didapat, supaya tidak
    //     menarik balik fokus yang sengaja dipindah user.
    var lastPanelField = null;
    var lastPanelFieldTime = 0;
    var refocusDoneForField = false;
    panel.addEventListener('focusin', function (e) {
      e.stopPropagation();
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) {
        lastPanelField = e.target;
        lastPanelFieldTime = Date.now();
        refocusDoneForField = false;
      }
    });
    panel.addEventListener('focusout', function (e) {
      var to = e.relatedTarget;
      if (to && panel.contains(to)) return;
      if (!lastPanelField || refocusDoneForField) return;
      if (Date.now() - lastPanelFieldTime > 1000) return;
      var field = lastPanelField;
      refocusDoneForField = true;
      setTimeout(function () {
        try {
          if (document.activeElement && !panel.contains(document.activeElement)) {
            field.focus();
          }
        } catch (err) {}
      }, 120);
    });

    fillConfigFields();
  }

  function updateNikCount() {
    var done = Object.keys(processedNik).length;
    nikCountLine.textContent = nikList.length ?
      ('Daftar: ' + nikList.length + ' NIK, ' + done + ' sudah diproses, ' +
        (nikList.length - done) + ' tersisa.') : 'Belum ada NIK.';
  }


  // ====== DOM HELPERS (pola userscript SIKS v1.37b/c/d) ======
  function waitFor(selector, timeout) {
    var start = Date.now();
    return new Promise(function (resolve) {
      (function poll() {
        var el = document.querySelector(selector);
        if (el && isVisible(el)) return resolve(el);
        if (Date.now() - start >= (timeout || 10000)) return resolve(null);
        setTimeout(poll, 200);
      })();
    });
  }

  function waitForFn(fn, timeout) {
    var start = Date.now();
    return new Promise(function (resolve) {
      (function poll() {
        var v = null;
        try { v = fn(); } catch (e) {}
        if (v) return resolve(v);
        if (Date.now() - start >= (timeout || 10000)) return resolve(null);
        setTimeout(poll, 200);
      })();
    });
  }

  function isVisible(el) {
    if (!el || !el.isConnected) return false;
    var r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var cs = window.getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
    return true;
  }

  function isDisabled(el) {
    if (!el) return true;
    if (el.disabled) return true;
    var cls = el.className || '';
    if (typeof cls === 'string' && cls.indexOf('Mui-disabled') !== -1) return true;
    return false;
  }

  // React: set .value biasa diabaikan — pakai native setter + event input.
  function setReactInput(el, value) {
    var proto = (el instanceof HTMLTextAreaElement) ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }

  // v1.2: pencocokan teks dibuat case-insensitive seperti :has-text
  // Playwright ("Cari Data" vs "CARI DATA" vs "Cari data").
  function findButtonByText(text, root) {
    var needle = String(text).toLowerCase();
    var btns = (root || document).querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      if (isVisible(btns[i]) && (btns[i].innerText || '').toLowerCase().indexOf(needle) !== -1) return btns[i];
    }
    return null;
  }

  function findButtonByTextIn(text, root) { return findButtonByText(text, root); }

  function topDialog() {
    var dialogs = document.querySelectorAll('div[role="dialog"]');
    return dialogs.length ? dialogs[dialogs.length - 1] : null;
  }
  // Modal utama DTSEN = dialog pertama (dialog ke-2 dst adalah detail).
  function mainDialog() {
    var dialogs = document.querySelectorAll('div[role="dialog"]');
    return dialogs.length ? dialogs[0] : null;
  }

  // Tekan ESC pada dialog (mengganti keyboard.press('Escape')).
  function pressEscapeOnDialog(dialog) {
    var target = dialog || document.body;
    try {
      target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
      target.dispatchEvent(new KeyboardEvent('keyup', { key: 'Escape', code: 'Escape', bubbles: true }));
    } catch (e) {
      document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    }
  }

  // Hover sintetis (v1.37c): mouse sungguhan Playwright tidak ada di
  // userscript — dispatch mousemove/mouseover/mouseenter dengan
  // relatedTarget supaya React menghitung jalur leave/enter dengan benar.
  function hoverEl(el) {
    if (!el) return;
    try {
      var r = el.getBoundingClientRect();
      var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      var opts = { bubbles: true, cancelable: true, clientX: cx, clientY: cy };
      el.dispatchEvent(new MouseEvent('mousemove', opts));
      el.dispatchEvent(new MouseEvent('mouseover', opts));
      el.dispatchEvent(new MouseEvent('mouseenter', Object.assign({}, opts, { bubbles: false })));
    } catch (e) {}
  }

  // Klik lengkap seperti mouse sungguhan (v1.37d): el.click() saja hanya
  // menembakkan click — beberapa handler MUI/React menunggu
  // mousedown/pointerdown.
  function clickEl(el) {
    if (!el) return;
    try {
      var r = el.getBoundingClientRect();
      var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      var opts = { bubbles: true, cancelable: true, clientX: cx, clientY: cy, button: 0, buttons: 1 };
      el.dispatchEvent(new PointerEvent('pointerover', opts));
      el.dispatchEvent(new PointerEvent('pointerdown', opts));
      el.dispatchEvent(new MouseEvent('mousedown', opts));
      el.dispatchEvent(new PointerEvent('pointerup', Object.assign({}, opts, { buttons: 0 })));
      el.dispatchEvent(new MouseEvent('mouseup', opts));
      el.dispatchEvent(new MouseEvent('click', opts));
    } catch (e) {
      try { el.click(); } catch (e2) {}
    }
  }

  // Scroll MINIMAL (v1.37d, seperti scrollIntoViewIfNeeded Playwright):
  // hanya bila elemen belum terlihat penuh, seminimal mungkin.
  function scrollIntoViewIfNeededEl(el) {
    if (!el) return;
    var container = el.closest('.MuiDialogContent-root') || el.parentElement;
    if (!container) return;
    var er = el.getBoundingClientRect();
    var cr = container.getBoundingClientRect();
    if (er.top < cr.top || er.bottom > cr.bottom) {
      try { el.scrollIntoView({ block: 'nearest', behavior: 'instant' }); } catch (e) {}
    }
  }
  function scrollRowIntoViewIfNeeded(row) { scrollIntoViewIfNeededEl(row); }

  // Baris anggota diambil FRESH dari DOM per indeks (v1.37b) — jangan
  // simpan NodeList sekali di awal, React me-render ulang sesudah klik.
  function anggotaRowAt(idx) {
    var m = mainDialog();
    if (!m) return null;
    var rs = m.querySelectorAll('table tbody tr');
    return rs[idx] || null;
  }

  function collapseInRow(idx) {
    var r = anggotaRowAt(idx);
    if (!r) return null;
    return r.querySelector('div.MuiCollapse-root.MuiCollapse-entered');
  }

  function findIconButtonIn(root, iconText) {
    var btns = root.querySelectorAll('button[aria-label="detail"]');
    for (var i = 0; i < btns.length; i++) {
      var sp = btns[i].querySelector('span.material-icons');
      if (sp && (sp.innerText || '').trim() === iconText && isVisible(btns[i])) return btns[i];
    }
    return null;
  }

  // extractValue (ada di skrip asli, tidak dipakai di mana pun di sana —
  // disertakan sebagai helper; jangan hapus tanpa alasan).
  function extractValue(text, label) {
    var regex = new RegExp(label + '\\s*[:\\-]?\\s*([^\\n]+)', 'i');
    var match = String(text || '').match(regex);
    return match ? match[1].trim() : '';
  }

  // cleanupFiles (asli: hapus file gambar CAPTCHA sementara di disk) —
  // tidak ada file di userscript, jadi no-op.
  function cleanupFiles() {}


  // ====== PROGRES + RESUME (localStorage, simpan SETIAP selesai 1 NIK) ======
  function loadProgress() {
    try {
      var raw = localStorage.getItem(LS_KEY);
      if (!raw) return;
      var data = JSON.parse(raw);
      results.dataKK = data.dataKK || [];
      results.dataART = data.dataART || [];
      results.dataDapodik = data.dataDapodik || [];
      results.dataBansos = data.dataBansos || [];
      results.dataAset = data.dataAset || [];
      processedNik = {};
      (data.processed || []).forEach(function (nik) { processedNik[nik] = true; });
      nikList = data.nikList || [];
      log('Resume: ' + Object.keys(processedNik).length + ' NIK sudah diproses; ' +
        results.dataKK.length + ' KK, ' + results.dataART.length + ' anggota, ' +
        results.dataDapodik.length + ' dapodik, ' + results.dataBansos.length +
        ' bansos, ' + results.dataAset.length + ' aset tersimpan.');
    } catch (e) {
      log('Gagal baca progres tersimpan, mulai dari awal.');
    }
  }

  function saveProgress() {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify({
        processed: Object.keys(processedNik),
        nikList: nikList,
        dataKK: results.dataKK, dataART: results.dataART,
        dataDapodik: results.dataDapodik, dataBansos: results.dataBansos,
        dataAset: results.dataAset
      }));
    } catch (e) {
      log('PERINGATAN: gagal simpan progres (' + e.message + ') — localStorage mungkin penuh.');
    }
  }

  // ====== CONFIG AI (panel -> localStorage HP saja, TIDAK PERNAH ke file) ======
  function getConfig() {
    var cfg = { endpoint: '', apiKey: '', model: DEFAULT_MODEL };
    try {
      var raw = localStorage.getItem(LS_CFG);
      if (raw) {
        var d = JSON.parse(raw);
        if (d.endpoint) cfg.endpoint = String(d.endpoint).trim();
        if (d.apiKey) cfg.apiKey = String(d.apiKey).trim();
        if (d.model) cfg.model = String(d.model).trim();
      }
    } catch (e) {}
    return cfg;
  }

  function fillConfigFields() {
    var cfg = getConfig();
    cfgEndpointEl.value = cfg.endpoint || '';
    cfgKeyEl.value = cfg.apiKey || '';
    cfgModelEl.value = cfg.model || DEFAULT_MODEL;
    cfgStatusEl.textContent = (cfg.endpoint && cfg.apiKey) ?
      'Config tersimpan (endpoint + key + model).' :
      'Endpoint/API key kosong — CAPTCHA dijawab manual lewat panel.';
  }

  function saveConfigFromPanel() {
    var endpoint = (cfgEndpointEl.value || '').trim();
    var apiKey = (cfgKeyEl.value || '').trim();
    var model = (cfgModelEl.value || '').trim() || DEFAULT_MODEL;
    cfgModelEl.value = model;
    try {
      localStorage.setItem(LS_CFG, JSON.stringify({ endpoint: endpoint, apiKey: apiKey, model: model }));
      cfgStatusEl.textContent = (endpoint && apiKey) ?
        'Config tersimpan (endpoint + key + model ' + model + ').' :
        'Config tersimpan, tapi endpoint/API key kosong — CAPTCHA manual.';
      log('Config AI disimpan (hanya di HP ini). Model: ' + model);
    } catch (e) {
      cfgStatusEl.textContent = 'Gagal simpan config: ' + e.message;
    }
  }

  function clearConfigFromPanel() {
    try { localStorage.removeItem(LS_CFG); } catch (e) {}
    cfgEndpointEl.value = '';
    cfgKeyEl.value = '';
    cfgModelEl.value = DEFAULT_MODEL;
    cfgStatusEl.textContent = 'Config dihapus. CAPTCHA dijawab manual lewat panel.';
    log('Config AI dihapus.');
  }

  // ====== CSV: PARSE + BUILD + TAMPIL + SALIN/UNDUH ======
  // Parse satu baris CSV (dukung koma + nilai dalam tanda kutip).
  function parseCsvLine(line) {
    var cells = [];
    var cur = '';
    var inQ = false;
    for (var i = 0; i < line.length; i++) {
      var ch = line.charAt(i);
      if (inQ) {
        if (ch === '"') {
          if (line.charAt(i + 1) === '"') { cur += '"'; i++; }
          else inQ = false;
        } else cur += ch;
      } else {
        if (ch === '"') inQ = true;
        else if (ch === ',') { cells.push(cur); cur = ''; }
        else cur += ch;
      }
    }
    cells.push(cur);
    return cells;
  }

  // Impor NIK fleksibel: file apa pun asal ada kolom header "nik".
  // - Ada kolom "nik" (trim + lowercase persis "nik") -> ambil kolom itu,
  //   kolom lain diabaikan.
  // - Tanpa header (baris pertama berisi angka) -> daftar satu NIK per baris.
  // - NIK dibersihkan (trim, hanya digit), duplikat dibuang.
  function parseNikList(text) {
    var lines = String(text || '').split(/\r?\n/).filter(function (l) { return l.trim().length > 0; });
    var niks = [];
    var seen = {};
    var source = 'kosong';
    function addNik(raw) {
      var d = String(raw || '').replace(/\D/g, '');
      if (!d) return;
      if (!seen[d]) { seen[d] = true; niks.push(d); }
    }
    if (!lines.length) return { niks: niks, source: source };
    var headerCells = parseCsvLine(lines[0]).map(function (c) { return c.trim().toLowerCase(); });
    var nikCol = -1;
    for (var h = 0; h < headerCells.length; h++) {
      if (headerCells[h] === 'nik') { nikCol = h; break; }
    }
    if (nikCol >= 0) {
      source = 'kolom "nik" (kolom ke-' + (nikCol + 1) + ')';
      for (var i = 1; i < lines.length; i++) {
        var cells = parseCsvLine(lines[i]);
        if (nikCol < cells.length) addNik(cells[nikCol]);
      }
    } else if (/^\d+$/.test(lines[0].trim())) {
      source = 'daftar satu NIK per baris (tanpa header)';
      for (var j = 0; j < lines.length; j++) addNik(lines[j]);
    } else {
      // Ada header tapi bukan "nik": lewati baris header, pakai kolom
      // pertama bila berupa angka (fallback toleran).
      source = 'tidak ada kolom "nik" — dibaca kolom pertama yang berupa angka';
      for (var k = 1; k < lines.length; k++) {
        var c2 = parseCsvLine(lines[k]);
        if (c2.length) addNik(c2[0]);
      }
    }
    return { niks: niks, source: source };
  }

  function addNikValues(niks, source) {
    var before = nikList.length;
    var seen = {};
    nikList.forEach(function (n) { seen[n] = true; });
    var added = 0;
    niks.forEach(function (n) {
      if (!seen[n]) { seen[n] = true; nikList.push(n); added++; }
    });
    saveProgress();
    updateNikCount();
    log('Impor NIK: ' + added + ' NIK baru dari ' + source + ' (' + niks.length +
      ' terbaca, ' + (niks.length - added) + ' duplikat dengan daftar lama). Total daftar: ' + nikList.length + ' NIK.');
    setStatus('Impor NIK selesai: ' + nikList.length + ' NIK dalam daftar.');
  }

  function addNikFromText() {
    var text = nikTextEl.value || '';
    if (!text.trim()) { setStatus('Kotak NIK kosong — tempel NIK dulu.'); return; }
    var parsed = parseNikList(text);
    if (!parsed.niks.length) {
      log('Tidak ada NIK yang berhasil dibaca dari teks.');
      setStatus('Tidak ada NIK yang terbaca dari teks.');
      return;
    }
    addNikValues(parsed.niks, parsed.source + ' (tempel teks)');
    nikTextEl.value = '';
  }

  // Impor CSV via bridge Galeriku (seperti userscript SIKS).
  function importCsv() {
    if (!window.Galeriku || !Galeriku.pickFile) {
      setStatus('Bridge Galeriku.pickFile() tidak ada. Pastikan buka halaman ini di browser Galeriku.');
      log('Bridge Galeriku.pickFile() tidak tersedia.');
      return;
    }
    log('Pilih file CSV NIK...');
    window.onGalerikuFilePicked = function (dataUrl, fileName) {
      if (!dataUrl) { log('Tidak ada file dipilih.'); return; }
      try {
        var b64 = dataUrl.split(',').slice(1).join(',');
        var text = atob(b64);
        try { text = decodeURIComponent(escape(text)); } catch (e2) {}
        var parsed = parseNikList(text);
        if (!parsed.niks.length) {
          log('File "' + (fileName || '') + '" tidak berisi NIK yang bisa dibaca.');
          setStatus('Tidak ada NIK yang terbaca dari file.');
          return;
        }
        addNikValues(parsed.niks, parsed.source + ' (file "' + (fileName || '') + '")');
      } catch (e) {
        log('Gagal baca file CSV: ' + e.message);
        setStatus('Gagal baca file CSV.');
      }
    };
    Galeriku.pickFile();
  }

  function csvCell(v) {
    if (v === null || v === undefined) return '';
    var s = String(v).replace(/\r?\n/g, ' ');
    if (/[",]/.test(s)) return '"' + s.replace(/"/g, '""') + '"';
    return s;
  }

  // Build CSV dari array objek (mengganti 1 sheet XLSX). Header = gabungan
  // semua kunci yang muncul di baris mana pun.
  function buildCsvFor(rows) {
    if (!rows || !rows.length) return '';
    var headers = [];
    var seen = {};
    rows.forEach(function (r) {
      Object.keys(r).forEach(function (k) { if (!seen[k]) { seen[k] = true; headers.push(k); } });
    });
    var lines = [headers.map(csvCell).join(',')];
    rows.forEach(function (r) {
      lines.push(headers.map(function (h) { return csvCell(r[h]); }).join(','));
    });
    return lines.join('\n') + '\n';
  }

  function showResults() {
    DATASETS.forEach(function (ds) {
      var b = resultTabsEl.querySelector('button[data-key="' + ds.key + '"]');
      if (b) {
        b.textContent = ds.label + ' (' + results[ds.key].length + ')';
        b.style.background = (ds.key === activeDataset) ? '#0a7' : '#333';
      }
    });
    var rows = results[activeDataset];
    var csv = buildCsvFor(rows);
    resultArea.style.display = 'block';
    resultArea.value = csv || ('(belum ada data ' + activeDataset + ')');
    var total = results.dataKK.length;
    setProgress((total || Object.keys(processedNik).length) ?
      ('Terkumpul: ' + results.dataKK.length + ' KK, ' + results.dataART.length +
        ' anggota, ' + results.dataDapodik.length + ' dapodik, ' +
        results.dataBansos.length + ' bansos, ' + results.dataAset.length +
        ' aset. Diproses: ' + Object.keys(processedNik).length + '/' + nikList.length + ' NIK.') : '');
  }

  function copyActiveCsv() {
    var csv = buildCsvFor(results[activeDataset]);
    if (!csv) { setStatus('Belum ada data untuk disalin.'); return; }
    try {
      resultArea.select();
      document.execCommand('copy');
      setStatus('CSV ' + activeDataset + ' disalin (' + results[activeDataset].length + ' baris).');
    } catch (e) {
      setStatus('Gagal menyalin CSV: ' + e.message);
    }
  }

  function downloadActiveCsv() {
    var ds = null;
    DATASETS.forEach(function (d) { if (d.key === activeDataset) ds = d; });
    var csv = buildCsvFor(results[activeDataset]);
    if (!csv) { setStatus('Belum ada data untuk diunduh.'); return; }
    try {
      var blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = ds.file;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
      setStatus('Mengunduh ' + ds.file + ' (' + results[activeDataset].length + ' baris).');
    } catch (e) {
      setStatus('Gagal unduh CSV: ' + e.message);
    }
  }


  // ====== CAPTCHA ======
  function findCaptchaImg() {
    var img = document.querySelector('img[alt*="Captcha"]');
    if (img && isVisible(img)) return img;
    var imgs = document.querySelectorAll('img[alt*="Security"], img.css-em4fei');
    for (var i = 0; i < imgs.length; i++) {
      if (isVisible(imgs[i])) return imgs[i];
    }
    return null;
  }

  function findRefreshCaptchaBtn() {
    var btns = document.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      if (btns[i].querySelector('[data-testid="RefreshRoundedIcon"]') && isVisible(btns[i])) return btns[i];
    }
    return null;
  }

  function refreshCaptchaOnly() {
    var btn = findRefreshCaptchaBtn();
    if (!btn) { log('Tombol refresh CAPTCHA tidak ditemukan'); return Promise.resolve(false); }
    clickEl(btn);
    return delay(1500).then(function () { return true; });
  }

  // Preprocessing gambar CAPTCHA — pengganti sharp (murni Canvas 2D):
  // gambar <img> digambar ke canvas offscreen 500x150 (contain, latar
  // putih), lalu di JS: greyscale + normalize + threshold 128 + hapus
  // piksel hitam terisolasi (tetangga hitam < 2 dalam 3x3, sama seperti
  // kode asli). Hasil: data URL PNG untuk dikirim ke API.
  function preprocessCaptchaImage(imgEl) {
    return new Promise(function (resolve) {
      var src = imgEl.getAttribute('src') || imgEl.src || '';
      function processWith(img) {
        try {
          var W = 500, H = 150;
          var cv = document.createElement('canvas');
          cv.width = W; cv.height = H;
          var ctx = cv.getContext('2d');
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(0, 0, W, H);
          var iw = img.naturalWidth || img.width;
          var ih = img.naturalHeight || img.height;
          if (!iw || !ih) return resolve(null);
          var scale = Math.min(W / iw, H / ih);
          var dw = iw * scale, dh = ih * scale;
          ctx.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh);
          var imgData = ctx.getImageData(0, 0, W, H);
          var d = imgData.data;
          var gray = new Uint8Array(W * H);
          var min = 255, max = 0;
          var i, j, k, x, y, idx;
          for (i = 0; i < W * H; i++) {
            var g = Math.round(0.299 * d[i * 4] + 0.587 * d[i * 4 + 1] + 0.114 * d[i * 4 + 2]);
            gray[i] = g;
            if (g < min) min = g;
            if (g > max) max = g;
          }
          var range = max - min || 1;
          var bw = new Uint8Array(W * H);
          for (j = 0; j < W * H; j++) {
            var n = Math.round((gray[j] - min) * 255 / range);
            bw[j] = n < 128 ? 0 : 255;
          }
          function px(pxX, pxY) {
            if (pxX < 0 || pxX >= W || pxY < 0 || pxY >= H) return 255;
            return bw[pxY * W + pxX];
          }
          var cleaned = new Uint8Array(bw);
          for (y = 0; y < H; y++) {
            for (x = 0; x < W; x++) {
              idx = y * W + x;
              if (bw[idx] === 0) {
                var black = 0;
                for (var dy = -1; dy <= 1; dy++) {
                  for (var dx = -1; dx <= 1; dx++) {
                    if (dx === 0 && dy === 0) continue;
                    if (px(x + dx, y + dy) === 0) black++;
                  }
                }
                if (black < 2) cleaned[idx] = 255;
              }
            }
          }
          for (k = 0; k < W * H; k++) {
            d[k * 4] = cleaned[k]; d[k * 4 + 1] = cleaned[k]; d[k * 4 + 2] = cleaned[k]; d[k * 4 + 3] = 255;
          }
          ctx.putImageData(imgData, 0, 0);
          resolve(cv.toDataURL('image/png'));
        } catch (e) { resolve(null); }
      }
      if (src.indexOf('data:image') === 0) {
        var im1 = new Image();
        im1.onload = function () { processWith(im1); };
        im1.onerror = function () { resolve(null); };
        im1.src = src;
      } else if (src) {
        fetch(src, { credentials: 'include' }).then(function (r) { return r.blob(); }).then(function (b) {
          var url = URL.createObjectURL(b);
          var im2 = new Image();
          im2.onload = function () { processWith(im2); URL.revokeObjectURL(url); };
          im2.onerror = function () { URL.revokeObjectURL(url); resolve(null); };
          im2.src = url;
        }).catch(function () {
          // Fallback: gambar langsung dari halaman (bila canvas tainted,
          // getImageData melempar dan processWith mengembalikan null).
          try { processWith(imgEl); } catch (e) { resolve(null); }
        });
      } else resolve(null);
    });
  }

  // Solver CAPTCHA via endpoint AI — pengganti captcha-solver-claude.js.
  // Endpoint + API key + model diambil dari config panel (localStorage HP),
  // TIDAK PERNAH hardcode. Fetch lintas origin dari halaman SIKS bisa
  // gagal karena CORS (tergantung header server endpoint) — caller
  // menangkap error ini dan jatuh ke CAPTCHA manual.
  function solveCaptchaWithCustomEndpoint(dataUrl, apiKey, baseURL, model) {
    var endpoint = String(baseURL || '').replace(/\/$/, '') + '/chat/completions';
    return fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + apiKey
      },
      body: JSON.stringify({
        model: model,
        max_tokens: 50,
        temperature: 0,
        messages: [{
          role: 'user',
          content: [
            { type: 'text', text: CAPTCHA_PROMPT },
            { type: 'image_url', image_url: { url: dataUrl } }
          ]
        }]
      })
    }).then(function (resp) {
      if (!resp.ok) {
        return resp.text().then(function (t) {
          throw new Error('API error: ' + resp.status + ' - ' + (t || '').substring(0, 200));
        });
      }
      return resp.text();
    }).then(function (responseText) {
      var rawAnswer = '';
      if (responseText.indexOf('data:') === 0) {
        // Respons SSE (OpenAI-style streaming)
        var lines = responseText.split('\n').filter(function (l) { return l.trim().indexOf('data:') === 0; });
        for (var i = 0; i < lines.length; i++) {
          var line = lines[i];
          if (line.indexOf('[DONE]') !== -1) break;
          try {
            var jsonStr = line.substring(line.indexOf('{'));
            var chunk = JSON.parse(jsonStr);
            if (chunk.choices && chunk.choices[0] && chunk.choices[0].delta && chunk.choices[0].delta.content) {
              rawAnswer += chunk.choices[0].delta.content;
            } else if (chunk.choices && chunk.choices[0] && chunk.choices[0].message && chunk.choices[0].message.content) {
              rawAnswer = chunk.choices[0].message.content;
              break;
            }
          } catch (e) {}
        }
      } else {
        var data = JSON.parse(responseText);
        rawAnswer = data.choices[0].message.content;
      }
      rawAnswer = (rawAnswer || '').trim();
      log('AI membaca: "' + rawAnswer + '"');
      var mathMatch = rawAnswer.match(/(\d+)\s*([\+\-\*\/×÷])\s*(\d+)/);
      if (!mathMatch) throw new Error('AI tidak mengembalikan format yang valid: "' + rawAnswer + '"');
      var num1 = parseInt(mathMatch[1], 10);
      var operator = mathMatch[2];
      var num2 = parseInt(mathMatch[3], 10);
      if (operator === '×') operator = '*';
      if (operator === '÷') operator = '/';
      var result = 0;
      if (operator === '+') result = num1 + num2;
      else if (operator === '-') result = num1 - num2;
      else if (operator === '*') result = num1 * num2;
      else if (operator === '/') result = Math.floor(num1 / num2);
      else throw new Error('Operator tidak dikenal: "' + operator + '"');
      log('Perhitungan: ' + num1 + ' ' + operator + ' ' + num2 + ' = ' + result);
      return String(result);
    });
  }

  // CAPTCHA manual (pengganti readline): run BERHENTI di titik ini dan
  // menunggu user mengetik jawaban di panel. Gambar TIDAK di-refresh dan
  // halaman TIDAK di-reload selama menunggu.
  var manualCaptchaResolver = null;
  function waitManualCaptchaAnswer() {
    var img = findCaptchaImg();
    var src = img ? (img.getAttribute('src') || img.src || '') : '';
    if (src) {
      captchaImgEl.src = src;
      captchaImgEl.style.display = 'block';
    } else captchaImgEl.style.display = 'none';
    captchaInputEl.value = '';
    captchaBoxEl.style.display = 'block';
    setStatus('CAPTCHA manual: ketik jawaban yang terlihat di gambar, lalu tekan Kirim.');
    log('Menunggu jawaban CAPTCHA manual (tidak reload, tidak refresh)...');
    return new Promise(function (resolve, reject) {
      manualCaptchaResolver = { resolve: resolve, reject: reject };
      manualCaptchaResolver._iv = setInterval(function () {
        if (stopRequestedNow()) {
          clearInterval(manualCaptchaResolver._iv);
          manualCaptchaResolver = null;
          captchaBoxEl.style.display = 'none';
          reject(new Error('Dihentikan saat menunggu CAPTCHA manual'));
        }
      }, 300);
    });
  }

  function submitManualCaptcha() {
    var ans = (captchaInputEl.value || '').trim();
    if (!ans) { setStatus('Jawaban CAPTCHA masih kosong.'); return; }
    if (manualCaptchaResolver) {
      clearInterval(manualCaptchaResolver._iv);
      var r = manualCaptchaResolver;
      manualCaptchaResolver = null;
      captchaBoxEl.style.display = 'none';
      log('Jawaban CAPTCHA manual: ' + ans);
      r.resolve(ans);
    }
  }

  function cancelManualCaptcha() {
    if (manualCaptchaResolver) {
      clearInterval(manualCaptchaResolver._iv);
      var r = manualCaptchaResolver;
      manualCaptchaResolver = null;
      captchaBoxEl.style.display = 'none';
      stopRequested = true;
      log('CAPTCHA manual dibatalkan — run berhenti.');
      r.reject(new Error('CAPTCHA manual dibatalkan'));
    } else {
      captchaBoxEl.style.display = 'none';
    }
  }

  // Pecahkan CAPTCHA matematika: maks 5 percobaan (refresh gambar tiap
  // gagal), lalu fallback manual panel. Tanpa config AI -> langsung manual.
  function solveMathCaptcha() {
    var MAX_ATTEMPTS = 5;
    var cfg = getConfig();
    if (!cfg.endpoint || !cfg.apiKey) {
      log('Endpoint/API key belum diisi — CAPTCHA dijawab manual.');
      return waitManualCaptchaAnswer();
    }
    var attempt = 0;
    function tryOnce() {
      attempt++;
      log('Percobaan CAPTCHA ke-' + attempt + ' dari ' + MAX_ATTEMPTS + ' (model: ' + cfg.model + ')...');
      var img = findCaptchaImg();
      if (!img) return Promise.reject(new Error('CAPTCHA image tidak ditemukan di halaman'));
      return preprocessCaptchaImage(img).then(function (dataUrl) {
        if (!dataUrl) throw new Error('Gagal memproses gambar CAPTCHA (canvas)');
        return solveCaptchaWithCustomEndpoint(dataUrl, cfg.apiKey, cfg.endpoint, cfg.model);
      }).then(function (answer) {
        var a = String(answer).trim();
        if (!/^-?\d+$/.test(a)) throw new Error('Jawaban AI bukan angka: "' + a + '"');
        return a;
      }).catch(function (err) {
        if (/Failed to fetch|NetworkError|Network request failed/i.test(err.message || '')) {
          log('Fetch ke endpoint AI gagal — kemungkinan CORS atau alamat endpoint tidak terjangkau dari HP. Cek alamat endpoint di config.');
        }
        log('Percobaan ' + attempt + ' gagal: ' + err.message);
        if (attempt < MAX_ATTEMPTS) {
          log('Klik tombol refresh CAPTCHA...');
          return refreshCaptchaOnly().then(function () { return tryOnce(); });
        }
        return Promise.reject(err);
      });
    }
    return tryOnce().catch(function (err) {
      log('Gagal 5x otomatis. Beralih ke input manual...');
      return waitManualCaptchaAnswer();
    });
  }


  // ====== FUNGSI HALAMAN ======
  function waitForPageReady() {
    return new Promise(function (resolve) {
      var start = Date.now();
      (function check() {
        if (window.location.href.indexOf('/data-bnba-dtsen') === -1) {
          log('Bukan halaman Data BNBA DTSEN. Buka halaman itu dulu (sudah login SIKS).');
          return resolve(false);
        }
        var input = document.querySelector('input[maxlength="17"]');
        if (input && isVisible(input)) {
          log('Halaman data DTSEN sudah siap — form pencarian NIK tersedia.');
          return resolve(true);
        }
        if (Date.now() - start > TIMEOUT) {
          log('Timeout menunggu form pencarian NIK (30 detik). Pastikan sudah login SIKS.');
          return resolve(false);
        }
        setTimeout(check, 1000);
      })();
    });
  }

  function closeAllDialogs() {
    var list = [];
    var btns = document.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      if (isVisible(btns[i]) && (btns[i].innerText || '').indexOf('Tutup') !== -1) list.push(btns[i]);
    }
    var chain = Promise.resolve();
    var closed = false;
    list.forEach(function (btn) {
      chain = chain.then(function () { clickEl(btn); closed = true; return delay(300); });
    });
    return chain.then(function () {
      if (closed) log('Dialog ditutup');
      return closed;
    });
  }

  function findErrorTutupButton() {
    var btns = document.querySelectorAll('button.MuiButton-containedError');
    for (var i = 0; i < btns.length; i++) {
      if (isVisible(btns[i]) && (btns[i].innerText || '').indexOf('Tutup') !== -1) return btns[i];
    }
    return null;
  }

  // Dialog error (h2 "Peringatan" / p "Terjadi Gangguan Koneksi ke
  // Dapodik" / tombol .MuiButton-containedError "Tutup"). Mengembalikan
  // true bila ada dialog error yang ditutup.
  function closeErrorDialog() {
    var h2s = document.querySelectorAll('h2');
    for (var i = 0; i < h2s.length; i++) {
      if (isVisible(h2s[i]) && (h2s[i].innerText || '').indexOf('Peringatan') !== -1) {
        log('Dialog peringatan terdeteksi, menutup...');
        var btn = findErrorTutupButton();
        if (btn) { clickEl(btn); return delay(500).then(function () { return true; }); }
        return closeAllDialogs().then(function () { return true; });
      }
    }
    var ps = document.querySelectorAll('p');
    for (var j = 0; j < ps.length; j++) {
      if (isVisible(ps[j]) && (ps[j].innerText || '').indexOf('Terjadi Gangguan Koneksi ke Dapodik') !== -1) {
        log('Error Dapodik terdeteksi, menutup...');
        var btn2 = findErrorTutupButton();
        if (btn2) { clickEl(btn2); return delay(500).then(function () { return true; }); }
        return closeAllDialogs().then(function () { return true; });
      }
    }
    var btn3 = findErrorTutupButton();
    if (btn3) {
      log('Dialog error terdeteksi, menutup...');
      clickEl(btn3);
      return delay(500).then(function () { return true; });
    }
    return Promise.resolve(false);
  }

  function anyLoadingVisible() {
    var texts = ['memuat', 'memroses', 'memproses', 'tunggu', 'mohon tunggu', 'memproses data', 'sedang memproses data'];
    var els = document.querySelectorAll('h6, p');
    for (var i = 0; i < els.length; i++) {
      var t = (els[i].innerText || '').toLowerCase();
      if (!t) continue;
      if (!isVisible(els[i])) continue;
      for (var j = 0; j < texts.length; j++) {
        if (t.indexOf(texts[j]) !== -1) return true;
      }
    }
    return false;
  }

  function waitForLoadingComplete() {
    return delay(300).then(function () {
      if (!anyLoadingVisible()) return delay(500);
      log('Menunggu "Mohon Tunggu" selesai...');
      var attempts = 0;
      return new Promise(function (resolve) {
        (function poll() {
          if (!anyLoadingVisible() || attempts >= 60) { delay(500).then(resolve); return; }
          attempts++;
          setTimeout(poll, 500);
        })();
      });
    });
  }

  // Cari tombol detail (chevron) di baris hasil pencarian — 4 fallback
  // seperti skrip asli, masing-masing hanya dipakai bila terlihat.
  function findDetailButtonInRow(row) {
    var svg = row.querySelector('svg[data-testid="ChevronRightIcon"]');
    if (svg && isVisible(svg)) return svg;
    var d1 = row.querySelector('div.css-1xp8hvq, div.css-1frqyt');
    if (d1 && isVisible(d1)) return d1;
    var d2 = row.querySelector('div.css-ert4z0');
    if (d2 && isVisible(d2)) return d2;
    var els = row.querySelectorAll('*');
    for (var i = 0; i < els.length; i++) {
      if (isVisible(els[i]) && (els[i].innerText || '').indexOf('Lihat Detail') !== -1) return els[i];
    }
    return null;
  }

  // Cari NIK -> CAPTCHA -> klik Cari Data -> tunggu tabel -> klik detail.
  // Mengembalikan true bila modal terbuka, false bila tidak ditemukan /
  // gagal sesudah 3 percobaan. (Alasan "RETRY_SEARCH" = ulangi percobaan
  // dari awal dengan gambar CAPTCHA yang baru.)
  function searchAndOpenModal(nik) {
    var MAX_SEARCH_ATTEMPTS = 3;
    var attempt = 0;
    function attemptOnce() {
      attempt++;
      log('Percobaan ' + attempt + ' dari ' + MAX_SEARCH_ATTEMPTS + ' untuk NIK ' + nik);
      pressEscapeOnDialog(null);
      return delay(300).then(function () { return closeErrorDialog(); })
        .then(function () { return closeAllDialogs(); })
        .then(function () { return waitFor('input', 5000); })
        .then(function () {
          var nikInput = document.querySelector('input[maxlength="17"][placeholder="Masukkan NIK..."]');
          if (!nikInput) throw new Error('Input NIK tidak ditemukan');
          setReactInput(nikInput, '');
          return delay(200).then(function () {
            setReactInput(nikInput, String(nik));
            return delay(500);
          });
        })
        .then(function () { return solveMathCaptcha(); })
        .then(function (captchaAnswer) {
          var captchaInput = document.querySelector('input[placeholder="Kode Captcha"]');
          if (!captchaInput) throw new Error('Input Kode Captcha tidak ditemukan');
          clickEl(captchaInput);
          setReactInput(captchaInput, '');
          setReactInput(captchaInput, String(captchaAnswer));
          return delay(500);
        })
        .then(function () {
          // v1.2: tombol Cari Data TIDAK BOLEH dicari sekali jalan seperti
          // sebelumnya — sesudah CAPTCHA diisi, React me-render ulang form
          // dan tombol bisa belum muncul / masih tertukar node-nya saat
          // lookup pertama dilakukan (itulah "tidak menemukan button
          // setelah captcha"). Playwright mentolerir ini karena locator
          // tidak menuntut tombol terlihat saat dicari, lalu menunggu
          // enabled sampai 5 dtk. Di sini: tunggu tombol MUNCUL sampai
          // 8 dtk, lalu tunggu ENABLED sampai 5 dtk — dan node tombol
          // dicari ulang FRESH di tiap poll, karena React bisa menggantinya.
          return waitForFn(function () { return findButtonByText('Cari Data'); }, 8000)
            .then(function (found) {
              if (!found) throw new Error('Tombol Cari Data tidak ditemukan');
              return waitForFn(function () {
                var b = findButtonByText('Cari Data');
                return (b && !isDisabled(b)) ? b : null;
              }, 5000);
            })
            .then(function (btn) {
              if (!btn) {
                log('Tombol Cari disabled, me-refresh CAPTCHA...');
                return refreshCaptchaOnly().then(function () { throw new Error('RETRY_SEARCH'); });
              }
              clickEl(btn);
              log('Tombol Cari diklik');
              return delay(0);
            });
        })
        .then(function () { return waitForLoadingComplete(); })
        .then(function () { return delay(1000); })
        .then(function () { return closeErrorDialog(); })
        .then(function (errorClosed) {
          if (errorClosed) {
            log('CAPTCHA Salah / Peringatan muncul!');
            return refreshCaptchaOnly().then(function () { throw new Error('RETRY_SEARCH'); });
          }
        })
        .then(function () {
          var bodyText = document.body ? (document.body.innerText || '') : '';
          if (/data tidak ditemukan|tidak ada data/i.test(bodyText)) {
            log('Pesan "Data tidak ditemukan" terdeteksi untuk NIK ' + nik);
            throw new Error('NO_DATA');
          }
        })
        .then(function () {
          return waitFor('table tbody tr', 5000).then(function (row) {
            if (!row) {
              log('Tabel hasil pencarian tidak muncul.');
              if (attempt >= MAX_SEARCH_ATTEMPTS) throw new Error('NO_DATA');
              return refreshCaptchaOnly().then(function () { throw new Error('RETRY_SEARCH'); });
            }
            log('Hasil pencarian muncul');
          });
        })
        .then(function () { return closeAllDialogs(); })
        .then(function () {
          var firstRow = document.querySelector('table tbody tr');
          if (!firstRow || !isVisible(firstRow)) {
            log('Row hasil tidak ditemukan');
            if (attempt >= MAX_SEARCH_ATTEMPTS) throw new Error('NO_DATA');
            return refreshCaptchaOnly().then(function () { throw new Error('RETRY_SEARCH'); });
          }
          scrollRowIntoViewIfNeeded(firstRow);
          return delay(500).then(function () {
            var detailButton = findDetailButtonInRow(firstRow);
            if (!detailButton) {
              log('Tombol detail tidak ditemukan, mencoba klik row pertama...');
              clickEl(firstRow);
              return delay(1000).then(function () {
                return waitFor('div[role="dialog"]', 3000).then(function (d) {
                  if (d) { log('Modal berhasil dibuka'); return true; }
                  if (attempt >= MAX_SEARCH_ATTEMPTS) throw new Error('NO_DATA');
                  return refreshCaptchaOnly().then(function () { throw new Error('RETRY_SEARCH'); });
                });
              });
            }
            log('Mengklik tombol detail...');
            clickEl(detailButton);
            return waitForLoadingComplete().then(function () {
              return waitFor('div[role="dialog"]', 8000);
            }).then(function (d) {
              if (!d) throw new Error('MODAL_NOT_OPEN');
              return delay(400).then(function () {
                log('Modal berhasil dibuka');
                return true;
              });
            });
          });
        })
        .catch(function (e) {
          if (e && e.message === 'RETRY_SEARCH') {
            if (attempt >= MAX_SEARCH_ATTEMPTS) return false;
            return attemptOnce();
          }
          if (e && e.message === 'NO_DATA') return false;
          if (e && e.message === 'MODAL_NOT_OPEN') {
            log('Modal tidak terbuka');
            if (attempt >= MAX_SEARCH_ATTEMPTS) return false;
            return refreshCaptchaOnly().then(function () { return attemptOnce(); });
          }
          log('Error pada percobaan ' + attempt + ': ' + e.message);
          if (e && /dibatalkan|Dihentikan/.test(e.message)) { stopRequested = true; return false; }
          if (attempt >= MAX_SEARCH_ATTEMPTS) return false;
          return refreshCaptchaOnly().then(function () { return attemptOnce(); });
        });
    }
    return attemptOnce();
  }

  function closeModal() {
    var closeButton = document.querySelector('button[aria-label="close"]');
    if (closeButton && isVisible(closeButton)) {
      clickEl(closeButton);
      return delay(300);
    }
    pressEscapeOnDialog(topDialog());
    return delay(300);
  }

  // PENGGANTI reload halaman per NIK (versi Playwright): reset form lewat
  // DOM. Alasan: location.reload() menghancurkan state run di memori
  // (antrean NIK, flag jalan/berhenti) dan memaksa auto-resume yang
  // bertentangan dengan desain tombol Mulai manual. Reset DOM mencapai
  // keadaan awal yang sama (modal tertutup, form kosong, CAPTCHA baru).
  function resetSearchForm() {
    return closeModal().then(function () { return closeAllDialogs(); }).then(function () {
      var nikInput = document.querySelector('input[maxlength="17"][placeholder="Masukkan NIK..."]');
      if (nikInput) setReactInput(nikInput, '');
      var captchaInput = document.querySelector('input[placeholder="Kode Captcha"]');
      if (captchaInput) setReactInput(captchaInput, '');
      return refreshCaptchaOnly();
    }).then(function () { return delay(500); });
  }


  // ====== EKSTRAKSI: HEADER ======
  function extractHeaderInfo() {
    var dialog = mainDialog();
    var header = {
      noKK: '', nikKK: '', namaKK: '', provinsi: '', kabupaten: '',
      kecamatan: '', kelurahan: '', alamat: '', rt: '', rw: '',
      desilNasional: '', statusPembaruan: 'N/A'
    };
    if (!dialog) return Promise.resolve(header);
    log('Mengambil data header Informasi Keluarga...');
    var namaEl = dialog.querySelector('p.MuiTypography-body1.css-1uw8or1');
    if (namaEl) {
      scrollRowIntoViewIfNeeded(namaEl);
      header.namaKK = (namaEl.innerText || '').trim();
      log('Nama KK: ' + header.namaKK);
    }
    var noKKEl = dialog.querySelector('p.MuiTypography-body1.css-6l5u5r');
    if (!noKKEl) return Promise.resolve(header);
    scrollRowIntoViewIfNeeded(noKKEl);
    header.noKK = (noKKEl.innerText || '').trim();
    log('No KK termasking: ' + header.noKK);
    // Hover + klik ATOMIK (v1.37c): tanpa jeda di antara keduanya.
    hoverEl(noKKEl);
    clickEl(noKKEl);
    return delay(500).then(function () { return waitForLoadingComplete(); }).then(function () {
      return waitForFn(function () {
        var dialogs = document.querySelectorAll('div[role="dialog"]');
        for (var d = 0; d < dialogs.length; d++) {
          var ps = dialogs[d].querySelectorAll('p');
          for (var i = 0; i < ps.length; i++) {
            var t = (ps[i].innerText || '').trim();
            if (/^\d{16}$/.test(t)) return t;
          }
        }
        return null;
      }, 3000);
    }).then(function (full) {
      if (full) { header.noKK = full; log('No KK lengkap: ' + full); }
      else log('No KK lengkap tidak ditemukan, menggunakan yang termasking');
      var d = mainDialog() || dialog;
      var wilayah = d.querySelectorAll('p.MuiTypography-body2.css-nf5lze');
      header.provinsi = wilayah[0] ? (wilayah[0].innerText || '').trim() : '';
      header.kabupaten = wilayah[1] ? (wilayah[1].innerText || '').trim() : '';
      header.kecamatan = wilayah[2] ? (wilayah[2].innerText || '').trim() : '';
      header.kelurahan = wilayah[3] ? (wilayah[3].innerText || '').trim() : '';
      var alamatEl = d.querySelector('p.MuiTypography-body2.css-9courl');
      header.alamat = alamatEl ? (alamatEl.innerText || '').trim() : '';
      log('Wilayah: ' + header.provinsi + ', ' + header.kabupaten + ', ' + header.kecamatan + ', ' + header.kelurahan);
      var h4s = d.querySelectorAll('h4.MuiTypography-h4');
      for (var i = 0; i < h4s.length; i++) {
        var t = (h4s[i].innerText || '').trim();
        if (/^(10|[1-9])$/.test(t)) { header.desilNasional = t; break; }
      }
      log('Desil Nasional: ' + (header.desilNasional || '(kosong)'));
      log('Data header selesai dikumpulkan');
      return header;
    });
  }

  // ====== EKSTRAKSI: ANGGOTA KELUARGA ======
  function findNikElInRow(idx) {
    var row = anggotaRowAt(idx);
    if (!row) return null;
    var els = row.querySelectorAll('p.css-hfe2ew, p.MuiTypography-body1.css-hfe2ew');
    for (var i = 0; i < els.length; i++) {
      var t = (els[i].innerText || '').trim();
      if (t && (t.indexOf('*') !== -1 || /^\d{16}$/.test(t))) return els[i];
    }
    return null;
  }

  // NIK anggota termasking di baris: hover + klik ATOMIK, verifikasi
  // unmask (16 digit tanpa '*') + satu retry. Pola v1.37b/c/d.
  function tryUnmaskNik(idx) {
    var el = findNikElInRow(idx);
    if (!el) return Promise.resolve(null);
    var t0 = (el.innerText || '').trim();
    if (/^\d{16}$/.test(t0)) { log('NIK sudah lengkap dari row: ' + t0); return Promise.resolve(t0); }
    if (t0.indexOf('*') === -1) return Promise.resolve(null);
    log('NIK ditemukan di row: ' + t0);
    log('Klik NIK termasking untuk membuka...');
    var row = anggotaRowAt(idx);
    if (row) hoverEl(row);
    hoverEl(el);
    clickEl(el);
    function pollUnmask() {
      return waitForFn(function () {
        var e2 = findNikElInRow(idx);
        if (!e2) return null;
        var t = (e2.innerText || '').trim();
        return /^\d{16}$/.test(t) ? t : null;
      }, 1500);
    }
    return pollUnmask().then(function (v) {
      if (v) { log('NIK lengkap setelah klik: ' + v); return v; }
      log('NIK masih termasking setelah klik, coba hover+klik ulang...');
      var row2 = anggotaRowAt(idx);
      var el2 = findNikElInRow(idx);
      if (row2 && el2) { hoverEl(row2); hoverEl(el2); clickEl(el2); }
      return pollUnmask();
    }).then(function (v) {
      if (v) log('NIK lengkap setelah klik: ' + v);
      else log('PERINGATAN: NIK anggota baris ' + (idx + 1) + ' masih termasking');
      return v;
    });
  }

  function unmaskNikInCollapse(idx, memberData) {
    var c = collapseInRow(idx);
    if (!c) return Promise.resolve();
    var els = c.querySelectorAll('p.MuiTypography-body1.css-hfe2ew, p.css-hfe2ew');
    for (var i = 0; i < els.length; i++) {
      var t = (els[i].innerText || '').trim();
      if (/^\d{16}$/.test(t)) {
        memberData.nikAnggota = t;
        log('NIK lengkap (dari element): ' + t);
        return Promise.resolve();
      }
    }
    var ps = c.querySelectorAll('p');
    var plist = [];
    for (var j = 0; j < ps.length; j++) {
      var tx = (ps[j].innerText || '');
      if (tx.indexOf('*') !== -1 && /\d{6,}/.test(tx)) plist.push(ps[j]);
    }
    var chain = Promise.resolve(false);
    plist.forEach(function (p) {
      chain = chain.then(function (done) {
        if (done) return true;
        log('NIK termasking ditemukan: ' + (p.innerText || '').trim());
        hoverEl(p);
        clickEl(p);
        return delay(800).then(function () {
          var c2 = collapseInRow(idx);
          if (!c2) return false;
          var els2 = c2.querySelectorAll('p.css-hfe2ew, p.MuiTypography-body1');
          for (var k = 0; k < els2.length; k++) {
            var t2 = (els2[k].innerText || '').trim();
            if (/^\d{16}$/.test(t2)) {
              memberData.nikAnggota = t2;
              log('NIK lengkap setelah klik: ' + t2);
              return true;
            }
          }
          return false;
        });
      });
    });
    return chain.then(function (done) {
      if (done) return;
      var c3 = collapseInRow(idx);
      if (!c3) return;
      if (!memberData.nikAnggota || memberData.nikAnggota.indexOf('*') !== -1) {
        var m = (c3.innerText || '').match(/\b(\d{16})\b/);
        if (m && m[1]) {
          memberData.nikAnggota = m[1];
          log('NIK lengkap (fallback regex): ' + m[1]);
        }
      }
    }).catch(function (e) { log('Error mengambil NIK lengkap: ' + e.message); });
  }

  function extractDetailFromCollapse(idx, memberData, namaAnggota) {
    var c = collapseInRow(idx);
    if (!c) return Promise.resolve();
    var btn = findIconButtonIn(c, 'search');
    if (!btn) return Promise.resolve();
    log('Klik tombol detail anggota...');
    clickEl(btn);
    return delay(500).then(function () { return waitForLoadingComplete(); }).then(function () {
      return extractDetailAnggota(memberData.nikAnggota, namaAnggota);
    }).then(function (detailData) {
      if (detailData) {
        for (var k in detailData) memberData[k] = detailData[k];
        log('Data detail tersimpan');
      }
    }).catch(function (e) { log('Error detail anggota: ' + e.message); });
  }

  function extractDapodikFromCollapse(idx, memberData, namaAnggota) {
    var c = collapseInRow(idx);
    if (!c) return Promise.resolve();
    var schoolBtn = findIconButtonIn(c, 'school');
    if (!schoolBtn) {
      var spans = c.querySelectorAll('button span.material-icons');
      for (var i = 0; i < spans.length; i++) {
        if ((spans[i].innerText || '').trim() === 'school') {
          var pb = spans[i].closest('button');
          if (pb && isVisible(pb)) { schoolBtn = pb; break; }
        }
      }
    }
    if (!schoolBtn) { log('Tombol pendidikan tidak ditemukan'); return Promise.resolve(); }
    var parentButton = schoolBtn.closest('button') || schoolBtn;
    if (isDisabled(parentButton)) {
      log('Tombol pendidikan disabled (tidak bersekolah)');
      return Promise.resolve();
    }
    log('Klik tombol data pendidikan...');
    clickEl(parentButton);
    return delay(1000).then(function () {
      return extractDapodikData(memberData.nikAnggota, namaAnggota);
    }).then(function (dapodikData) {
      if (dapodikData) {
        results.dataDapodik.push(dapodikData);
        log('Data pendidikan tersimpan');
      }
    }).catch(function (e) { log('Error data pendidikan: ' + e.message); });
  }

  function expandAnggotaRow(idx, memberData, namaAnggota) {
    var row = anggotaRowAt(idx);
    if (!row) return Promise.resolve();
    var expandIcon = row.querySelector('svg[data-testid="ExpandMoreIcon"]');
    if (!expandIcon || !isVisible(expandIcon)) {
      log('Row tidak memiliki expand icon');
      return Promise.resolve();
    }
    log('Expand row untuk ' + namaAnggota + '...');
    scrollRowIntoViewIfNeeded(row);
    clickEl(expandIcon);
    return delay(800).then(function () {
      return waitForFn(function () {
        var c = collapseInRow(idx);
        return (c && isVisible(c)) ? c : null;
      }, 3000);
    }).then(function (collapse) {
      if (!collapse) { log('Collapse tidak terbuka'); return; }
      log('Collapse area terbuka');
      return unmaskNikInCollapse(idx, memberData).then(function () {
        return extractDetailFromCollapse(idx, memberData, namaAnggota);
      }).then(function () {
        return extractDapodikFromCollapse(idx, memberData, namaAnggota);
      }).then(function () {
        var r = anggotaRowAt(idx);
        var icon = r ? r.querySelector('svg[data-testid="ExpandMoreIcon"]') : null;
        if (icon && isVisible(icon)) clickEl(icon);
        return delay(500);
      });
    });
  }

  function processAnggotaRow(idx, nikCari, noKK, members) {
    var row = anggotaRowAt(idx);
    if (!row) return Promise.resolve();
    return Promise.resolve().then(function () {
      var r = anggotaRowAt(idx);
      if (!r) return null;
      var firstCell = r.querySelector('td.MuiTableCell-body');
      var cellText = firstCell ? (firstCell.innerText || '') : '';
      var lines = cellText.split('\n').map(function (l) { return l.trim(); }).filter(function (l) { return l; });
      var namaAnggota = '', nikTermasking = '', hubungan = '', pekerjaan = '', status = '';
      for (var j = 0; j < lines.length; j++) {
        if (lines[j] === 'Nama Lengkap' && j + 1 < lines.length) namaAnggota = lines[j + 1];
        if (lines[j] === 'NIK' && j + 1 < lines.length) nikTermasking = lines[j + 1];
        if (lines[j] === 'Hubungan' && j + 1 < lines.length) hubungan = lines[j + 1];
        if (lines[j] === 'Pekerjaan Utama' && j + 1 < lines.length) pekerjaan = lines[j + 1];
        if (lines[j] === 'Status' && j + 1 < lines.length) status = lines[j + 1];
      }
      log('Anggota ' + (idx + 1) + ': ' + namaAnggota + ' (' + hubungan + ')');
      var memberData = {
        nikKK: nikCari, noKK: noKK, nikAnggota: nikTermasking,
        nama: namaAnggota, hubunganKeluarga: hubungan,
        pekerjaanUtama: pekerjaan, statusAktif: status
      };
      return tryUnmaskNik(idx).then(function (nikLengkap) {
        if (nikLengkap) memberData.nikAnggota = nikLengkap;
        else log('NIK lengkap belum didapat dari row, akan coba di collapse area');
        return expandAnggotaRow(idx, memberData, namaAnggota);
      }).then(function () {
        members.push(memberData);
      });
    }).catch(function (e) {
      log('Error parsing row ' + idx + ': ' + e.message);
    });
  }

  function extractDataAnggotaKeluarga(nikCari, noKK) {
    var members = [];
    var modal = mainDialog();
    if (!modal) { log('Modal utama tidak ditemukan'); return Promise.resolve(members); }
    log('Scrolling ke section Daftar Anggota Keluarga...');
    var sc = modal.querySelector('.MuiDialogContent-root');
    if (sc) { try { sc.scrollTop = 600; } catch (e) {} }
    return delay(500).then(function () {
      var m = mainDialog();
      if (!m) return members;
      var rowCount = m.querySelectorAll('table tbody tr').length;
      log('Ditemukan ' + rowCount + ' anggota keluarga');
      var chain = Promise.resolve();
      for (var i = 0; i < rowCount; i++) {
        (function (idx) {
          chain = chain.then(function () {
            if (stopRequestedNow()) return;
            return processAnggotaRow(idx, nikCari, noKK, members);
          });
        })(i);
      }
      return chain.then(function () { return members; });
    });
  }

  // ====== EKSTRAKSI: DETAIL ANGGOTA (modal detail = dialog TERAKHIR) ======
  function extractDetailAnggota(nikAnggota, namaAnggota) {
    return delay(1000).then(function () { return waitForLoadingComplete(); }).then(function () {
      var detailModal = topDialog();
      if (!detailModal) { log('Modal detail anggota tidak ditemukan'); return null; }
      var detailData = {};
      var texts = detailModal.querySelectorAll('p.MuiTypography-body1, h6.MuiTypography-subtitle2');
      for (var i = 0; i < texts.length; i++) {
        var t = (texts[i].innerText || '').trim();
        if (/^\d{16}$/.test(t)) {
          detailData.nikAnggota = t;
          log('NIK lengkap dari modal: ' + t);
          break;
        }
      }
      var sc = detailModal.querySelector('.MuiDialogContent-root');
      if (sc) { try { sc.scrollTop = sc.scrollHeight; } catch (e) {} }
      return delay(500).then(function () {
        var m2 = topDialog() || detailModal;
        var labels = m2.querySelectorAll('span.MuiTypography-caption.css-1mxkmdo');
        var values = m2.querySelectorAll('h6.MuiTypography-subtitle2.css-c7dfze');
        for (var j = 0; j < labels.length && j < values.length; j++) {
          var label = (labels[j].innerText || '').trim();
          var value = (values[j].innerText || '').trim();
          if (label && value && value !== '-') detailData[label] = value;
        }
        log(Object.keys(detailData).length + ' field data detail');
        var closeBtn = null;
        var btns = m2.querySelectorAll('button.MuiButton-fullWidth');
        for (var k = 0; k < btns.length; k++) {
          if ((btns[k].innerText || '').indexOf('Tutup') !== -1) { closeBtn = btns[k]; break; }
        }
        if (closeBtn && isVisible(closeBtn)) clickEl(closeBtn);
        else pressEscapeOnDialog(m2);
        return delay(500).then(function () { return detailData; });
      });
    }).catch(function (e) {
      log('Error detail anggota: ' + e.message);
      pressEscapeOnDialog(topDialog());
      return delay(300).then(function () { return null; });
    });
  }


  // ====== EKSTRAKSI: DAPODIK (modal pendidikan = dialog TERAKHIR) ======
  function finishDapodik(dapodikModal, dapodikData) {
    var closeSvg = dapodikModal.querySelector('button svg[data-testid="CloseIcon"]');
    var closeBtn = closeSvg ? (closeSvg.closest('button') || closeSvg) : null;
    if (closeBtn && isVisible(closeBtn)) clickEl(closeBtn);
    else pressEscapeOnDialog(dapodikModal);
    return delay(500).then(function () { return dapodikData; });
  }

  function extractDapodikData(nikAnggota, namaAnggota) {
    var dapodikData = {
      nikAnggota: nikAnggota, namaAnggota: namaAnggota,
      namaSekolahDtsen: '', jenjangDtsen: '', statusDtsen: '',
      namaSekolahDapodik: '', jenjangDapodik: '', statusDapodik: '',
      sinkronBerhasil: 'Tidak'
    };
    return delay(1000).then(function () { return waitForLoadingComplete(); }).then(function () {
      return closeErrorDialog();
    }).then(function (errorClosed) {
      if (errorClosed) {
        log('Error Dapodik terjadi, data tidak tersedia');
        dapodikData.sinkronBerhasil = 'Error Koneksi';
        return dapodikData;
      }
      var dapodikModal = topDialog();
      if (!dapodikModal) { log('Modal Dapodik tidak ditemukan'); return dapodikData; }
      var sc = dapodikModal.querySelector('.MuiDialogContent-root');
      var scrollEl = sc || dapodikModal;
      try { scrollEl.scrollTop = scrollEl.scrollHeight; } catch (e) {}
      return delay(500).then(function () {
        var m2 = topDialog() || dapodikModal;
        var stacks = m2.querySelectorAll('div.MuiStack-root.css-13tkiz0');
        function parseSection(container) {
          if (!container) return { nama: '', jenjang: '', status: '' };
          var labels = container.querySelectorAll('p.css-ge8wfi, p.MuiTypography-body1.css-ge8wfi');
          var values = container.querySelectorAll('p.css-vyhbtt, p.MuiTypography-body1.css-vyhbtt');
          var out = { nama: '', jenjang: '', status: '' };
          for (var i = 0; i < labels.length; i++) {
            var lab = (labels[i].innerText || '').trim().toUpperCase();
            if (lab.indexOf('NAMA SEKOLAH') !== -1 && i < values.length) out.nama = (values[i].innerText || '').trim();
            if (lab.indexOf('JENJANG PENDIDIKAN') !== -1 && i < values.length) out.jenjang = (values[i].innerText || '').trim();
          }
          var chip = container.querySelector('div.MuiChip-root span.MuiChip-label');
          out.status = chip ? (chip.innerText || '').trim() : 'Tidak Aktif';
          return out;
        }
        var dtsen = parseSection(stacks[0]);
        dapodikData.namaSekolahDtsen = dtsen.nama;
        dapodikData.jenjangDtsen = dtsen.jenjang;
        dapodikData.statusDtsen = dtsen.status;
        log('DTSEN: ' + dtsen.nama + ' - ' + dtsen.jenjang + ' (' + dtsen.status + ')');
        if (stacks.length >= 2) {
          var dapo = parseSection(stacks[1]);
          dapodikData.namaSekolahDapodik = dapo.nama;
          dapodikData.jenjangDapodik = dapo.jenjang;
          dapodikData.statusDapodik = dapo.status;
          log('DAPODIK: ' + dapo.nama + ' - ' + dapo.jenjang + ' (' + dapo.status + ')');
        }
        var syncButton = findButtonByTextIn('Sinkronkan data individu', m2);
        if (!syncButton || !isVisible(syncButton)) return finishDapodik(m2, dapodikData);
        if (isDisabled(syncButton)) {
          log('Data sudah sinkron');
          dapodikData.sinkronBerhasil = 'Sudah Sinkron';
          return finishDapodik(m2, dapodikData);
        }
        log('Melakukan sinkronisasi...');
        clickEl(syncButton);
        return delay(1000).then(function () { return closeErrorDialog(); }).then(function (syncErr) {
          if (syncErr) {
            log('Error sinkronisasi Dapodik');
            dapodikData.sinkronBerhasil = 'Error Sinkronisasi';
            return finishDapodik(topDialog() || m2, dapodikData);
          }
          var n = 0;
          return new Promise(function (resolve) {
            (function poll() {
              var cur = topDialog() || m2;
              var btn = findButtonByTextIn('Sinkronkan data individu', cur);
              if (!btn || !btn.isConnected) {
                log('Sinkronisasi selesai (tombol hilang)');
                dapodikData.sinkronBerhasil = 'Ya';
                resolve(finishDapodik(cur, dapodikData));
                return;
              }
              if (isDisabled(btn)) {
                log('Sinkronisasi berhasil');
                dapodikData.sinkronBerhasil = 'Ya';
                resolve(finishDapodik(cur, dapodikData));
                return;
              }
              if (n >= 15) {
                log('Timeout sinkronisasi');
                dapodikData.sinkronBerhasil = 'Timeout';
                resolve(finishDapodik(cur, dapodikData));
                return;
              }
              n++;
              setTimeout(poll, 500);
            })();
          });
        });
      });
    }).catch(function (e) {
      log('Error Dapodik: ' + e.message);
      return closeErrorDialog().then(function () {
        pressEscapeOnDialog(topDialog());
        return delay(300).then(function () { return null; });
      });
    });
  }

  // ====== EKSTRAKSI: RIWAYAT BANSOS (modal bansos = dialog TERAKHIR) ======
  function bansosModalNow() {
    var ms = document.querySelectorAll('div[role="dialog"]');
    return ms.length >= 2 ? ms[ms.length - 1] : null;
  }

  function extractRiwayatBansos(noKK, nikKK, namaKK) {
    log('Mengambil data Riwayat Bansos...');
    var modal = mainDialog();
    if (!modal) { log('Modal utama tidak ditemukan'); return Promise.resolve(false); }
    var sc = modal.querySelector('.MuiDialogContent-root');
    if (sc) { try { sc.scrollTop = 1200; } catch (e) {} }
    return delay(1000).then(function () {
      var m = mainDialog();
      if (!m) return false;
      var link = null;
      var ps = m.querySelectorAll('p.MuiTypography-body1.detail-text.css-1wzfw41, p.MuiTypography-body1.detail-text.css-19trlz7');
      for (var i = 0; i < ps.length; i++) {
        if ((ps[i].innerText || '').indexOf('Lihat Riwayat Bansos') !== -1 && isVisible(ps[i])) { link = ps[i]; break; }
      }
      if (!link) { log('Link Riwayat Bansos tidak ditemukan'); return false; }
      log('Klik link Riwayat Bansos...');
      clickEl(link);
      return delay(500);
    }).then(function (ok) {
      if (ok === false) return false;
      return waitForLoadingComplete().then(function () { return delay(500); }).then(function () {
        var bansosModal = bansosModalNow();
        if (!bansosModal) { log('Modal Riwayat Bansos tidak terbuka'); return false; }
        log('Modal Riwayat Bansos terbuka');
        var tabs = ['PKH', 'SEMBAKO', 'PBI', 'YAPI', 'BLTS KESRA', 'ATENSI', 'PPSE', 'RST'];
        var totalData = 0;
        var chain = Promise.resolve();
        tabs.forEach(function (tabName) {
          chain = chain.then(function () {
            if (stopRequestedNow()) return;
            var bm = bansosModalNow() || bansosModal;
            if (!bm) return;
            log('Mengambil data tab ' + tabName + '...');
            var tabButton = null;
            var btns = bm.querySelectorAll('button[role="tab"]');
            for (var i = 0; i < btns.length; i++) {
              if ((btns[i].innerText || '').indexOf(tabName) !== -1 && isVisible(btns[i])) { tabButton = btns[i]; break; }
            }
            if (!tabButton) { log('Tab ' + tabName + ' tidak ditemukan'); return; }
            clickEl(tabButton);
            return delay(2000).then(function () { return waitForLoadingComplete(); }).then(function () { return delay(500); }).then(function () {
              var bm2 = bansosModalNow() || bansosModal;
              if (!bm2) return;
              var rows = bm2.querySelectorAll('table tbody tr');
              log('Ditemukan ' + rows.length + ' data');
              for (var r = 0; r < rows.length; r++) {
                var cells = rows[r].querySelectorAll('td.MuiTableCell-alignCenter');
                if (cells.length >= 8) {
                  results.dataBansos.push({
                    noKK: noKK, nikKK: nikKK, namaKK: namaKK, jenisBansos: tabName,
                    noKartu: (cells[0].innerText || '').trim(),
                    bansos: (cells[1].innerText || '').trim(),
                    periode: (cells[2].innerText || '').trim(),
                    penyalur: (cells[3].innerText || '').trim(),
                    noRekening: (cells[4].innerText || '').trim(),
                    atasNama: (cells[5].innerText || '').trim(),
                    nominal: (cells[6].innerText || '').trim(),
                    statusTransaksi: (cells[7].innerText || '').trim()
                  });
                  totalData++;
                }
              }
            });
          });
        });
        return chain.then(function () {
          log('Menutup modal Riwayat Bansos...');
          var bm3 = bansosModalNow() || bansosModal;
          if (bm3) {
            var closeBtn = bm3.querySelector('button.MuiIconButton-sizeSmall');
            if (closeBtn && isVisible(closeBtn)) clickEl(closeBtn);
            else pressEscapeOnDialog(bm3);
          } else pressEscapeOnDialog(topDialog());
          return delay(1000).then(function () {
            log('Total ' + totalData + ' data bansos berhasil diambil');
            return true;
          });
        });
      });
    }).catch(function (e) {
      log('Error Riwayat Bansos: ' + e.message);
      pressEscapeOnDialog(topDialog());
      return delay(500).then(function () { return false; });
    });
  }

  // ====== EKSTRAKSI: ASET (modal hunian = dialog TERAKHIR) ======
  function findHunianButton(modal) {
    var btn = modal.querySelector('div.css-g1lf9k button.css-zrlwxp, div.css-g1lf9k button');
    if (btn && isVisible(btn)) return btn;
    var spans = modal.querySelectorAll('span.MuiTypography-caption');
    for (var i = 0; i < spans.length; i++) {
      if ((spans[i].innerText || '').indexOf('KONDISI HUNIAN DAN ASET') !== -1) {
        var box = spans[i].closest('div.MuiBox-root');
        if (box) {
          var b2 = box.querySelector('button');
          if (b2 && isVisible(b2)) return b2;
        }
      }
    }
    var svg = modal.querySelector('svg[data-testid="HomeOutlinedIcon"]');
    if (svg) {
      var box3 = svg.closest('div.MuiBox-root');
      if (box3) {
        var b3 = box3.querySelector('button');
        if (b3 && isVisible(b3)) return b3;
      }
    }
    var boxes = modal.querySelectorAll('div.css-398azs');
    if (boxes.length >= 2) {
      var b4 = boxes[1].querySelector('button');
      if (b4 && isVisible(b4)) return b4;
    }
    return null;
  }

  function extractAset(noKK, nikKK, namaKK) {
    log('Mengambil data Kondisi Hunian & Aset...');
    var modal = mainDialog();
    if (!modal) return Promise.resolve(false);
    var sc = modal.querySelector('.MuiDialogContent-root');
    if (sc) { try { sc.scrollTop = sc.scrollHeight; } catch (e) {} }
    return delay(1000).then(function () {
      var m = mainDialog();
      if (!m) return false;
      var btn = findHunianButton(m);
      if (!btn) { log('Tombol detail hunian & aset tidak ditemukan'); return false; }
      log('Klik tombol detail hunian & aset...');
      clickEl(btn);
      return delay(500);
    }).then(function (ok) {
      if (ok === false) return false;
      return waitForLoadingComplete().then(function () { return delay(500); }).then(function () {
        var modals = document.querySelectorAll('div[role="dialog"]');
        if (modals.length < 2) { log('Modal Detail Hunian & Aset tidak terbuka'); return false; }
        var hunianModal = modals[modals.length - 1];
        log('Modal Detail Hunian & Aset terbuka');
        var asetData = { noKK: noKK, nikKK: nikKK, namaKK: namaKK };
        try {
          var labels = hunianModal.querySelectorAll('span.MuiTypography-caption.css-iq38is');
          var values = hunianModal.querySelectorAll('p.MuiTypography-body2.css-anaoz7');
          var kondisi = {};
          for (var i = 0; i < labels.length && i < values.length; i++) {
            kondisi[(labels[i].innerText || '').trim()] = (values[i].innerText || '').trim();
          }
          for (var k in kondisi) asetData[k] = kondisi[k];
          log('Data kondisi perumahan: ' + Object.keys(kondisi).length + ' field');
        } catch (e) { log('Error mengambil kondisi perumahan: ' + e.message); }
        try {
          var items = hunianModal.querySelectorAll('div.MuiPaper-root.css-1duh665, div.MuiPaper-root.css-1bp4dwy');
          for (var j = 0; j < items.length; j++) {
            try {
              var h6 = items[j].querySelector('h6');
              var sp = items[j].querySelector('span.MuiTypography-caption');
              if (h6 && sp) asetData[(sp.innerText || '').trim()] = (h6.innerText || '').trim();
            } catch (e2) {}
          }
          log('Data aset bergerak: ' + items.length + ' item');
        } catch (e) { log('Error mengambil data aset: ' + e.message); }
        results.dataAset.push(asetData);
        log('Menutup modal Detail Hunian & Aset...');
        var closeBtn = hunianModal.querySelector('button[aria-label="close"], button.MuiIconButton-sizeSmall');
        if (closeBtn && isVisible(closeBtn)) clickEl(closeBtn);
        else pressEscapeOnDialog(hunianModal);
        return delay(1000).then(function () {
          log('Total ' + (Object.keys(asetData).length - 3) + ' field data aset berhasil diambil');
          return true;
        });
      });
    }).catch(function (e) {
      log('Error Aset: ' + e.message);
      pressEscapeOnDialog(topDialog());
      return delay(500).then(function () { return false; });
    });
  }


  // ====== ALUR UTAMA ======
  function processOneNik(nik, currentNum, total) {
    setProgress('PROSES ' + currentNum + '/' + total + ': ' + nik);
    log('PROSES ' + currentNum + '/' + total + ': ' + nik);
    return searchAndOpenModal(nik).then(function (modalOpened) {
      if (!modalOpened) {
        log('Modal tidak terbuka untuk NIK ' + nik + ' — dicatat sebagai tidak ditemukan.');
        results.dataKK.push({ NIK: nik, 'No KK': 'Data tidak ditemukan' });
        processedNik[nik] = true;
        saveProgress();
        showResults();
        return resetSearchForm();
      }
      return extractHeaderInfo().then(function (headerInfo) {
        log('Kepala Keluarga: ' + headerInfo.namaKK);
        return extractDataAnggotaKeluarga(nik, headerInfo.noKK).then(function (anggota) {
          if (anggota && anggota.length) {
            log('Berhasil mengambil ' + anggota.length + ' anggota keluarga');
            results.dataART.push.apply(results.dataART, anggota);
          }
          return extractRiwayatBansos(headerInfo.noKK, headerInfo.nikKK, headerInfo.namaKK);
        }).then(function () {
          return extractAset(headerInfo.noKK, headerInfo.nikKK, headerInfo.namaKK);
        }).then(function () {
          // NIK KK, RT, RW tetap kosong seperti skrip asli (tidak pernah
          // diisi oleh kode Playwright-nya) — jangan dikarang.
          results.dataKK.push({
            NIK: nik, 'No KK': headerInfo.noKK, 'NIK KK': headerInfo.nikKK,
            'Nama KK': headerInfo.namaKK, Provinsi: headerInfo.provinsi,
            'Kabupaten/Kota': headerInfo.kabupaten, Kecamatan: headerInfo.kecamatan,
            'Kelurahan/Desa': headerInfo.kelurahan, Alamat: headerInfo.alamat,
            RT: headerInfo.rt, RW: headerInfo.rw,
            'Desil Nasional': headerInfo.desilNasional,
            'Status Pembaruan': headerInfo.statusPembaruan
          });
          return closeModal();
        }).then(function () {
          processedNik[nik] = true;
          saveProgress();
          showResults();
          log('Selesai processing NIK ' + nik);
          return resetSearchForm();
        });
      });
    }).catch(function (e) {
      log('Error processing NIK ' + nik + ': ' + e.message);
      saveProgress();
      showResults();
      log('NIK ' + nik + ' TIDAK ditandai selesai (bisa diproses ulang).');
      return closeModal().then(function () { return resetSearchForm(); }).catch(function () {});
    });
  }

  function run() {
    if (running) { setStatus('Scrape sedang berjalan.'); return; }
    running = true;
    stopRequested = false;
    window.__dtsenStopRequested = false;
    btnMulai.disabled = true;
    btnBerhenti.disabled = false;
    setStatus('Memulai scraping...');
    log('Memulai scraping DTSEN...');
    loadProgress();
    updateNikCount();
    waitForPageReady().then(function (ready) {
      if (!ready) throw new Error('Halaman belum siap');
      return closeAllDialogs();
    }).then(function () {
      if (!nikList.length) {
        setStatus('Daftar NIK kosong — impor CSV atau tempel NIK dulu.');
        log('Tidak ada NIK. Impor CSV atau tempel NIK di panel, lalu tekan Mulai.');
        throw new Error('NO_NIK');
      }
      var remaining = nikList.filter(function (n) { return !processedNik[n]; });
      log('Total ' + nikList.length + ' NIK; sudah diproses ' +
        Object.keys(processedNik).length + '; sisa ' + remaining.length + ' NIK.');
      if (!remaining.length) {
        setStatus('Selesai: semua NIK sudah diproses.');
        showResults();
        saveProgress();
        return;
      }
      setStatus('Memproses ' + remaining.length + ' NIK...');
      var chain = Promise.resolve();
      remaining.forEach(function (nik, idx) {
        chain = chain.then(function () {
          if (stopRequestedNow()) return;
          return processOneNik(nik, idx + 1, remaining.length);
        });
      });
      return chain;
    }).then(function () {
      if (stopRequestedNow()) {
        setStatus('Dihentikan. Progres tersimpan — tekan Mulai untuk lanjut.');
        log('Dihentikan oleh pengguna. Progres tersimpan.');
      } else {
        setStatus('Selesai: ' + results.dataKK.length + ' KK, ' + results.dataART.length +
          ' anggota, ' + results.dataDapodik.length + ' dapodik, ' +
          results.dataBansos.length + ' bansos, ' + results.dataAset.length + ' aset.');
        log('SCRAPING SELESAI.');
      }
      showResults();
      saveProgress();
    }).catch(function (e) {
      if (e && e.message === 'NO_NIK') {
        // Sudah dilog di atas.
      } else {
        setStatus('ERROR: ' + e.message);
        log('ERROR: ' + e.message);
        reportFloat('Dihentikan: ' + e.message);
      }
      showResults();
      saveProgress();
    }).then(function () {
      running = false;
      btnMulai.disabled = false;
      btnBerhenti.disabled = true;
    });
  }

  // ====== INIT ======
  buildPanel();
  loadProgress();
  updateNikCount();
  showResults();
  var cfg0 = getConfig();
  if (!cfg0.endpoint || !cfg0.apiKey) {
    log('Config AI (endpoint/API key) belum diisi — CAPTCHA dijawab manual lewat panel. Isi config lalu Simpan config bila ingin pakai AI.');
  }
  log('DTSEN Scraper v1.2 dimuat. Isi daftar NIK (Impor CSV / tempel teks), lalu tekan Mulai.');
  if (window.console && console.log) console.log('[DTSEN] userscript dimuat — panel di kanan bawah.');
})();
