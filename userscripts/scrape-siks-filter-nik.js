// == Userscript Galeriku ==
// Nama: SIKS Filter NIK (scrape Verifikasi Komitmen)
// Sumber: konversi dari scrape-siks-filter-nik.js (Playwright / Node.js)
// Target: https://siks.kemensos.go.id/pkh/bnba-pendamping-verifikasi-komitmen
// Pola URL: *://siks.kemensos.go.id/pkh/bnba-pendamping-verifikasi-komitmen*
// Cara pakai: buka halaman Verifikasi Komitmen di browser Galeriku (pastikan
// sudah login SIKS), panel "SIKS Filter NIK" muncul di kanan bawah, tekan
// "Mulai". Hasil tampil di panel (textarea CSV + tombol Salin/Unduh).
// Progres (NIK yang sudah diproses + hasil) tersimpan di localStorage, jadi
// bila halaman di-refresh atau aplikasi ditutup, tekan "Mulai" lagi untuk
// melanjutkan dari NIK yang belum diproses.
//
// APA YANG BERUBAH DARI VERSI PLAYWRIGHT (baca sebelum memakai):
// - Tidak ada lagi chromium.launch / page.goto / page.locator / keyboard.
//   Semua aksi dilakukan langsung di DOM halaman (querySelector, click).
// - Input NIK diisi lewat "native setter" React + event input, supaya React
//   membaca nilai yang diketik (set .value biasa diabaikan React).
// - waitForSelector diganti polling (waitFor).
// - Tombol ESC keyboard diganti dispatch KeyboardEvent ke dialog (MUI
//   menutup dialog saat menerima keydown Escape), dengan fallback klik
//   tombol tutup dialog bila ada.
// - Simpan CSV ke file (fs.writeFile) diganti: hasil tampil di panel
//   (textarea + Salin), tombol Unduh CSV (Blob, tergantung WebView), dan
//   progres tersimpan di localStorage untuk resume. Tombol "Impor CSV"
//   bisa membaca file CSV progres dari run Playwright lama (lewat bridge
//   Galeriku.pickFile) supaya NIK yang sudah diproses di sana ikut di-skip.
// - Skrip TIDAK jalan otomatis saat halaman dibuka; tekan "Mulai" di panel.
//   Tombol "Berhenti" menghentikan di antara NIK (bukan di tengah baris).
// - Mode melayang (v1.37 aplikasi): skrip melaporkan progres tiap NIK dan
//   saat selesai/dihentikan lewat Galeriku.reportProgress() (no-op bila
//   mode melayang tidak aktif); tombol Berhenti di panel melayang/notifikasi
//   menyetel window.__siksStopRequested yang diperiksa di antara NIK.
(function () {
  'use strict';

  // Bila skrip dimuat ulang (refresh halaman), hapus panel lama dulu.
  var oldPanel = document.getElementById('siks-filter-nik-panel');
  if (oldPanel) oldPanel.remove();
  var oldFab = document.getElementById('siks-filter-nik-fab');
  if (oldFab) oldFab.remove();

  // ====== KONFIGURASI (sama seperti versi Playwright) ======
  var TIMEOUT = 30000;
  var LS_KEY = 'siksFilterNik.progress.v1';

  var delay = function (ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  };

  // ====== STATE ======
  var running = false;
  var stopRequested = false;
  var results = [];          // semua baris hasil (termasuk NIK tanpa hasil)
  var processedNik = {};     // map nik_pengurus -> true (sudah diproses)

  // ====== PANEL UI ======
  var panel, logBox, statusLine, progressLine, csvArea, btnMulai, btnBerhenti;

  function log(msg) {
    var line = document.createElement('div');
    line.textContent = msg;
    logBox.appendChild(line);
    logBox.scrollTop = logBox.scrollHeight;
    if (window.console && console.log) console.log('[SIKS] ' + msg);
  }

  function setStatus(msg) { statusLine.textContent = msg; if (msg) reportFloat(msg); }
  function setProgress(msg) { progressLine.textContent = msg; if (msg) reportFloat(msg); }

  // v1.37 (mode melayang): laporkan progres ke panel melayang + notifikasi
  // aplikasi. No-op bila bridge tidak ada / mode melayang tidak aktif.
  function reportFloat(msg) {
    try {
      if (window.Galeriku && Galeriku.reportProgress) Galeriku.reportProgress(msg);
    } catch (e) {}
  }
  // Flag berhenti gabungan: tombol Berhenti di panel skrip (stopRequested)
  // ATAU tombol Berhenti di panel melayang/notifikasi (disetel aplikasi
  // lewat window.__siksStopRequested).
  function stopRequestedNow() {
    return stopRequested || window.__siksStopRequested === true;
  }

  function buildPanel() {
    panel = document.createElement('div');
    panel.id = 'siks-filter-nik-panel';
    panel.style.cssText = 'position:fixed;right:8px;bottom:8px;width:330px;max-width:92vw;' +
      'max-height:62vh;overflow:auto;background:#111;color:#fff;border:1px solid #555;' +
      'border-radius:10px;padding:10px;font:12px/1.5 sans-serif;z-index:999999;' +
      'box-shadow:0 4px 18px rgba(0,0,0,.5);';
    panel.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:center;">' +
      '<b>SIKS Filter NIK</b>' +
      '<button id="siks-fn-min" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:2px 8px;">—</button>' +
      '</div>' +
      '<div id="siks-fn-status" style="margin:6px 0 2px;color:#9fd;">Siap. Pastikan sudah login SIKS.</div>' +
      '<div id="siks-fn-progress" style="color:#fc6;"></div>' +
      '<div style="display:flex;flex-wrap:wrap;gap:6px;margin:8px 0;">' +
      '<button id="siks-fn-mulai" style="background:#0a7;color:#fff;border:0;border-radius:6px;padding:6px 10px;">Mulai</button>' +
      '<button id="siks-fn-berhenti" style="background:#a33;color:#fff;border:0;border-radius:6px;padding:6px 10px;" disabled>Berhenti</button>' +
      '<button id="siks-fn-salin" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:6px 10px;">Salin CSV</button>' +
      '<button id="siks-fn-unduh" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:6px 10px;">Unduh CSV</button>' +
      '<button id="siks-fn-impor" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:6px 10px;">Impor CSV</button>' +
      '<button id="siks-fn-hapus" style="background:#333;color:#fff;border:1px solid #666;border-radius:6px;padding:6px 10px;">Hapus progres</button>' +
      '</div>' +
      '<textarea id="siks-fn-csv" readonly style="width:100%;height:110px;background:#000;color:#cfc;' +
      'border:1px solid #555;border-radius:6px;font:11px monospace;display:none;"></textarea>' +
      '<div id="siks-fn-log" style="max-height:140px;overflow:auto;background:#000;' +
      'border:1px solid #444;border-radius:6px;padding:6px;margin-top:8px;font:11px monospace;"></div>';

    document.body.appendChild(panel);
    logBox = panel.querySelector('#siks-fn-log');
    statusLine = panel.querySelector('#siks-fn-status');
    progressLine = panel.querySelector('#siks-fn-progress');
    csvArea = panel.querySelector('#siks-fn-csv');
    btnMulai = panel.querySelector('#siks-fn-mulai');
    btnBerhenti = panel.querySelector('#siks-fn-berhenti');

    var fab = document.createElement('button');
    fab.id = 'siks-filter-nik-fab';
    fab.textContent = 'SIKS';
    fab.style.cssText = 'position:fixed;right:8px;bottom:8px;z-index:999999;background:#0a7;' +
      'color:#fff;border:0;border-radius:20px;padding:8px 14px;font:13px sans-serif;display:none;';
    document.body.appendChild(fab);

    panel.querySelector('#siks-fn-min').addEventListener('click', function () {
      panel.style.display = 'none';
      fab.style.display = 'block';
    });
    fab.addEventListener('click', function () {
      fab.style.display = 'none';
      panel.style.display = 'block';
    });

    btnMulai.addEventListener('click', function () { run(); });
    btnBerhenti.addEventListener('click', function () {
      stopRequested = true;
      setStatus('Berhenti diminta — menyelesaikan NIK yang sedang berjalan...');
    });
    panel.querySelector('#siks-fn-salin').addEventListener('click', copyCsv);
    panel.querySelector('#siks-fn-unduh').addEventListener('click', downloadCsv);
    panel.querySelector('#siks-fn-impor').addEventListener('click', importCsv);
    panel.querySelector('#siks-fn-hapus').addEventListener('click', function () {
      try { localStorage.removeItem(LS_KEY); } catch (e) {}
      results = [];
      processedNik = {};
      csvArea.value = '';
      csvArea.style.display = 'none';
      setProgress('');
      setStatus('Progres dihapus. Siap mulai dari awal.');
      log('Progres dihapus.');
    });
  }

  // ====== HELPER DOM ======
  function waitFor(selector, timeout) {
    timeout = timeout || 10000;
    var start = Date.now();
    return new Promise(function (resolve) {
      (function check() {
        var el = document.querySelector(selector);
        if (el) return resolve(el);
        if (Date.now() - start > timeout) return resolve(null);
        setTimeout(check, 200);
      })();
    });
  }

  function waitForFn(fn, timeout) {
    timeout = timeout || 10000;
    var start = Date.now();
    return new Promise(function (resolve) {
      (function check() {
        var v = null;
        try { v = fn(); } catch (e) {}
        if (v) return resolve(v);
        if (Date.now() - start > timeout) return resolve(null);
        setTimeout(check, 200);
      })();
    });
  }

  // Isi input React (set .value biasa diabaikan React; pakai native setter).
  function setReactInput(input, value) {
    var setter = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype, 'value').set;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function findButtonByText(text) {
    var btns = document.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      if ((btns[i].innerText || '').indexOf(text) !== -1) return btns[i];
    }
    return null;
  }

  // Tutup dialog MUI: kirim keydown Escape ke dialog (cara MUI menutup),
  // fallback klik tombol tutup bila ada.
  function pressEscapeOnDialog(dialogEl) {
    var ev = function () {
      return new KeyboardEvent('keydown', {
        key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true, cancelable: true
      });
    };
    if (dialogEl) dialogEl.dispatchEvent(ev());
    document.dispatchEvent(ev());
    if (dialogEl) {
      var closeBtn = dialogEl.querySelector(
        'button[aria-label*="close" i], button[aria-label*="tutup" i]');
      if (closeBtn) closeBtn.click();
    }
  }

  function topDialog() {
    var dialogs = document.querySelectorAll('.MuiDialog-root');
    return dialogs.length ? dialogs[dialogs.length - 1] : null;
  }

  // v1.37 (perbaikan hover): tiru hover mouse sebelum klik.
  // Di Playwright, click() menggerakkan mouse sungguhan ke elemen
  // (mouseover/mouseenter + :hover aktif) sebelum klik; di userscript,
  // el.click() adalah klik sintetis tanpa gerakan mouse — elemen yang
  // hanya muncul/aktif saat hover (mis. span NIK di cell modal) tidak
  // terungkap sebelum diklik. Helper ini men-dispatch mousemove /
  // mouseover / mouseenter ke elemen (dengan koordinat dari
  // getBoundingClientRect) supaya listener JS yang menunggu hover jalan.
  // CATATAN JUJUR: event sintetis memicu listener JS (mouseover /
  // mouseenter), tapi TIDAK memicu CSS :hover — itu hanya merespons
  // posisi mouse sungguhan. Bila unmask di halaman ini ternyata
  // digerakkan CSS :hover (bukan listener JS), hover sintetis tidak
  // cukup dan NIK tetap tidak ter-unmask; satu-satunya jalan saat itu
  // adalah gerakan mouse sungguhan (tidak bisa dari userscript).
  // v1.37c: tiru gerakan mouse sungguhan sedekat mungkin. Playwright
  // menggerakkan mouse beneran: mouseout di elemen lama (relatedTarget =
  // elemen baru) lalu mouseover di elemen baru (relatedTarget = elemen
  // lama). Tanpa relatedTarget, React tidak bisa menghitung jalur
  // leave/enter dengan benar — hover baris 2 tidak pernah "tiba" di
  // React. Event tetap sintetis (tidak memicu CSS :hover), tapi untuk
  // listener JS React ini sedekat mungkin dengan mouse sungguhan.
  var lastHoverEl = null;
  function hoverEl(el) {
    if (!el) return;
    try {
      var r = el.getBoundingClientRect();
      var x = r.left + r.width / 2;
      var y = r.top + r.height / 2;
      if (lastHoverEl && lastHoverEl !== el) {
        try {
          var ro = lastHoverEl.getBoundingClientRect();
          var oo = { bubbles: true, cancelable: true,
            clientX: ro.left + ro.width / 2, clientY: ro.top + ro.height / 2,
            relatedTarget: el };
          lastHoverEl.dispatchEvent(new MouseEvent('mouseout', oo));
          lastHoverEl.dispatchEvent(new MouseEvent('mouseleave', oo));
        } catch (e2) {}
      }
      var opts = { bubbles: true, cancelable: true, clientX: x, clientY: y,
        relatedTarget: lastHoverEl };
      el.dispatchEvent(new MouseEvent('mousemove', opts));
      el.dispatchEvent(new MouseEvent('mouseover', opts));
      el.dispatchEvent(new MouseEvent('mouseenter', opts));
      lastHoverEl = el;
    } catch (e) {}
  }

  // ====== PROGRES (localStorage, pengganti file CSV progres) ======
  function loadProgress() {
    try {
      var raw = localStorage.getItem(LS_KEY);
      if (!raw) return;
      var data = JSON.parse(raw);
      results = data.results || [];
      processedNik = {};
      (data.processed || []).forEach(function (nik) { processedNik[nik] = true; });
      log('Resume: ' + Object.keys(processedNik).length +
        ' NIK sudah diproses (' + results.length + ' baris).');
    } catch (e) {
      log('Gagal baca progres tersimpan, mulai dari awal.');
    }
  }

  function saveProgress() {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify({
        processed: Object.keys(processedNik),
        results: results
      }));
    } catch (e) {}
  }

  // ====== CSV ======
  var CSV_HEADERS = ['No', 'NIK Pengurus', 'NO KK', 'Nama Pengurus', 'Desa',
    'Kecamatan', 'NIK Penerima', 'Nama Penerima', 'Komponen', 'Status',
    'Nama Sekolah', 'Kelas'];

  function csvCell(v) {
    return '"' + String(v === undefined || v === null ? '' : v).replace(/"/g, '""') + '"';
  }

  function buildCsv(data) {
    var lines = [CSV_HEADERS.join(',')];
    data.forEach(function (item, index) {
      var row = [index + 1, item.nik_pengurus, item.nokk, item.nama_pengurus,
        item.kelurahan, item.kecamatan, item.nikPenerima, item.namaPenerima,
        item.komponen, item.status, item.namaSekolah, item.kelas];
      lines.push(row.map(csvCell).join(','));
    });
    return lines.join('\n') + '\n';
  }

  function showCsv() {
    csvArea.value = buildCsv(results);
    csvArea.style.display = 'block';
  }

  function copyCsv() {
    showCsv();
    var text = csvArea.value;
    function done(ok) {
      setStatus(ok ? 'CSV disalin (' + results.length + ' baris).' : 'Gagal menyalin — salin manual dari kotak teks.');
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { done(true); }, function () {
        csvArea.select();
        try { done(document.execCommand('copy')); } catch (e) { done(false); }
      });
    } else {
      csvArea.select();
      try { done(document.execCommand('copy')); } catch (e) { done(false); }
    }
  }

  function downloadCsv() {
    showCsv();
    try {
      var blob = new Blob([csvArea.value], { type: 'text/csv;charset=utf-8' });
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'hasil-verifikasi-filter-nik.csv';
      document.body.appendChild(a);
      a.click();
      setTimeout(function () {
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      }, 1000);
      setStatus('Unduhan CSV diminta. Bila tidak muncul, pakai tombol Salin CSV.');
      log('Unduh CSV diminta (bila WebView tidak mendukung unduhan, pakai Salin CSV).');
    } catch (e) {
      setStatus('Unduh gagal: ' + e.message + ' — pakai Salin CSV.');
    }
  }

  // Impor CSV progres dari run Playwright lama (lewat bridge Galeriku).
  function importCsv() {
    if (typeof Galeriku === 'undefined' || !Galeriku.pickFile) {
      setStatus('Bridge Galeriku.pickFile tidak tersedia di halaman ini.');
      return;
    }
    setStatus('Pilih file CSV progres (hasil-verifikasi-filter-nik.csv)...');
    window.onGalerikuFilePicked = function (dataUrl) {
      if (!dataUrl) { setStatus('Impor dibatalkan.'); return; }
      try {
        var base64 = dataUrl.split(',')[1] || '';
        var text = decodeURIComponent(escape(window.atob(base64)));
        var lines = text.split(/\r?\n/).slice(1);
        var count = 0;
        lines.forEach(function (line) {
          if (!line.trim()) return;
          var cells = [];
          var re = /"([^"]*)"|([^,]+)/g, m;
          while ((m = re.exec(line)) !== null) {
            cells.push(m[1] !== undefined ? m[1] : m[2]);
          }
          var row = {
            nik_pengurus: cells[1] || '', nokk: cells[2] || '',
            nama_pengurus: cells[3] || '', kelurahan: cells[4] || '',
            kecamatan: cells[5] || '', nikPenerima: cells[6] || '',
            namaPenerima: cells[7] || '', komponen: cells[8] || '',
            status: cells[9] || '', namaSekolah: cells[10] || '',
            kelas: cells[11] || ''
          };
          if (row.nik_pengurus) {
            processedNik[String(row.nik_pengurus)] = true;
            results.push(row);
            count++;
          }
        });
        saveProgress();
        setStatus('Impor selesai: ' + count + ' baris (' +
          Object.keys(processedNik).length + ' NIK sudah diproses).');
        log('Impor CSV: ' + count + ' baris.');
      } catch (e) {
        setStatus('Gagal impor CSV: ' + e.message);
      }
    };
    Galeriku.pickFile();
  }

  // ====== FASE 1: AMBIL SEMUA NIK DARI REACT STATE (SEMUA HALAMAN) ======
  function getAllNikFromReactState() {
    return new Promise(function (resolve, reject) {
      var allNikData = [];
      var pageNum = 1;
      var maxPages = 50;

      function step() {
        if (stopRequestedNow()) return resolve(allNikData);
        if (pageNum > maxPages) return resolve(allNikData);

        log('Halaman ' + pageNum + '...');
        var pageData = (function () {
          var gridRoot = document.querySelector('.MuiDataGrid-root');
          if (!gridRoot) return { success: false, message: 'Grid not found' };
          var reactKey = Object.keys(gridRoot).find(function (key) {
            return key.indexOf('__react') === 0;
          });
          if (!reactKey || !gridRoot[reactKey]) {
            return { success: false, message: 'React fiber not found' };
          }
          var fiber = gridRoot[reactKey];
          var depth = 0;
          var rowsData = null;
          while (fiber && depth < 10) {
            if (fiber.memoizedProps && fiber.memoizedProps.rows) {
              rowsData = fiber.memoizedProps.rows;
              break;
            }
            if (fiber.memoizedState && fiber.memoizedState.props &&
                fiber.memoizedState.props.rows) {
              rowsData = fiber.memoizedState.props.rows;
              break;
            }
            fiber = fiber.return;
            depth++;
          }
          if (!rowsData) return { success: false, message: 'Rows data not found' };
          var extracted = rowsData.map(function (row) {
            return {
              nik_pengurus: row.nik_pengurus,
              nama_pengurus: row.nama_pengurus,
              nokk: row.nokk,
              kelurahan: row.kelurahan,
              kecamatan: row.kecamatan
            };
          });
          return { success: true, data: extracted, total: extracted.length };
        })();

        if (!pageData.success) {
          return reject(new Error('Gagal ambil data halaman ' + pageNum +
            ': ' + pageData.message));
        }
        log(pageData.total + ' NIK dari halaman ' + pageNum);
        allNikData.push.apply(allNikData, pageData.data);

        var nextButton = document.querySelector('button[aria-label="Go to next page"]');
        if (!nextButton || nextButton.disabled ||
            nextButton.getAttribute('disabled') !== null) {
          log('Halaman ' + pageNum + ' adalah halaman terakhir');
          return resolve(allNikData);
        }
        nextButton.click();
        pageNum++;
        delay(2000).then(step);
      }
      step();
    });
  }

  // ====== FASE 2a: FILTER BY NIK ======
  function filterByNik(nik) {
    return new Promise(function (resolve) {
      log('Filter NIK: ' + nik);
      var nikInput = document.querySelector('input');
      if (!nikInput) { log('Input NIK tidak ditemukan'); return resolve(false); }
      setReactInput(nikInput, '');
      delay(300).then(function () {
        setReactInput(nikInput, String(nik));
        return delay(500);
      }).then(function () {
        var cariButton = findButtonByText('Cari Data');
        if (!cariButton) { log('Tombol Cari Data tidak ditemukan'); return resolve(false); }
        cariButton.click();
        return delay(3000);
      }).then(function () {
        return waitFor('.MuiDataGrid-row', 5000);
      }).then(function (row) {
        if (!row) { log('Tidak ada hasil untuk NIK ini'); return resolve(false); }
        var count = document.querySelectorAll('.MuiDataGrid-row').length;
        log('Ditemukan ' + count + ' hasil');
        resolve(count > 0);
      });
    });
  }

  // ====== FASE 2b: SCRAPE MODAL DETAILS ======
  // v1.37b: JANGAN simpan referensi baris sekali di awal. Klik span NIK
  // baris 1 memicu React me-render ulang isi tabel modal, sehingga
  // elemen <tr> yang sudah ditangkap jadi node lepas (detached): hover
  // dan klik sesudahnya mengenai salinan lama yang tidak ada di layar,
  // dan innerText yang dibaca tetap yang lama (NIK tetap termasking).
  // Karena itu tiap baris diambil FRESH dari DOM berdasarkan indeksnya,
  // dan cell/span dibaca ulang tepat sebelum dipakai.
  // v1.37c: hover + klik harus ATOMIK (satu blok sinkron, tanpa delay
  // di antaranya) — jeda 300ms di antara hover dan klik memberi React
  // kesempatan me-render ulang, sehingga span yang di-hover belum tentu
  // span yang diklik. Mouseout baris lama kini ditangani di dalam
  // hoverEl (relatedTarget), bukan sebagai langkah terpisah.
  function modalRowAt(idx) {
    var tb = document.querySelector('.MuiDialogContent-root table tbody');
    if (!tb) return null;
    var rs = tb.querySelectorAll('tr');
    return rs[idx] || null;
  }

  // v1.37c: hover + klik NIK dalam SATU blok sinkron (atomik). Jeda di
  // antara hover dan klik memberi React kesempatan me-render ulang —
  // span yang di-hover belum tentu span yang diklik.
  function hoverAndClickNik(idx) {
    var freshRow = modalRowAt(idx);
    if (!freshRow) return false;
    var freshCells = freshRow.querySelectorAll('td, th');
    if (freshCells.length < 7) return false;
    var nikSpan = freshCells[0].querySelector('span');
    hoverEl(freshRow);
    hoverEl(freshCells[0]);
    hoverEl(nikSpan);
    if (nikSpan) nikSpan.click();
    return true;
  }

  function hoverAndClickUp(idx) {
    var freshRow = modalRowAt(idx);
    if (!freshRow) return false;
    var freshCells = freshRow.querySelectorAll('td, th');
    if (freshCells.length < 7) return false;
    var upButton = freshCells[3].querySelector('button');
    if (!upButton) return false;
    hoverEl(freshRow);
    hoverEl(freshCells[3]);
    hoverEl(upButton);
    upButton.click();
    return true;
  }

  // Apakah cell NIK baris idx sudah ter-unmask (16 digit, tanpa '*')?
  function cellUnmasked(idx) {
    var r = modalRowAt(idx);
    if (!r) return false;
    var c = r.querySelectorAll('td, th');
    if (!c.length) return false;
    var t = (c[0].innerText || '').replace(/content_copy/g, '').trim();
    return t.replace(/\D/g, '').length >= 16;
  }

  // Poll sampai NIK ter-unmask atau timeout; resolve true/false.
  function waitUnmasked(idx, ms) {
    var start = Date.now();
    return new Promise(function (resolve) {
      (function poll() {
        if (cellUnmasked(idx)) return resolve(true);
        if (Date.now() - start >= ms) return resolve(false);
        setTimeout(poll, 150);
      })();
    });
  }

  function scrapeModalDetails(rowData) {
    return new Promise(function (resolve) {
      var out = [];
      waitFor('.MuiDialogContent-root', TIMEOUT).then(function (dlg) {
        if (!dlg) { log('Modal detail tidak muncul'); return resolve(out); }
        return delay(1000);
      }).then(function () {
        var tbody = document.querySelector('.MuiDialogContent-root table tbody');
        if (!tbody) { log('Tabel modal tidak ditemukan'); return resolve(out); }
        var rowCount = tbody.querySelectorAll('tr').length;
        log('Ditemukan ' + rowCount + ' komponen');
        var chain = Promise.resolve();
        for (var i = 0; i < rowCount; i++) {
          (function (idx) {
            chain = chain.then(function () {
              if (stopRequestedNow()) return;
              return scrapeModalRow(idx, rowData, out);
            });
          })(i);
        }
        return chain.then(function () { resolve(out); });
      }).catch(function (e) {
        log('Error scraping modal: ' + e.message);
        resolve(out);
      });
    });
  }

  function scrapeModalRow(idx, rowData, out) {
    return new Promise(function (resolve) {
      var row = modalRowAt(idx);
      if (!row) return resolve();
      if (row.querySelectorAll('td, th').length < 7) return resolve();
      row.scrollIntoView({ block: 'center' });
      delay(300).then(function () {
        // v1.37c: hover + klik ATOMIK, lalu VERIFIKASI dengan polling:
        // baca ulang cell sampai NIK ter-unmask (16 digit, tanpa '*')
        // atau timeout; bila masih termasking, ulangi hover+klik sekali
        // lagi (tombol salin bisa saja baru ter-render sesudah hover).
        hoverAndClickNik(idx);
        return waitUnmasked(idx, 1500);
      }).then(function (ok) {
        if (ok) return;
        log('NIK baris ' + (idx + 1) + ' masih termasking, coba hover+klik ulang...');
        hoverAndClickNik(idx);
        return waitUnmasked(idx, 1500);
      }).then(function () {
        var freshRow = modalRowAt(idx);
        if (!freshRow) return resolve();
        var freshCells = freshRow.querySelectorAll('td, th');
        if (freshCells.length < 7) return resolve();
        var nikPenerima = (freshCells[0].innerText || '')
          .replace(/content_copy/g, '').trim();
        if (nikPenerima.replace(/\D/g, '').length < 16) {
          log('PERINGATAN baris ' + (idx + 1) + ': NIK masih termasking (' + nikPenerima + ')');
        }
        var namaPenerima = (freshCells[1].innerText || '').trim();
        var komponen = (freshCells[2].innerText || '').trim();
        var status = (freshCells[6].innerText || '').trim();
        log('- ' + namaPenerima + ' | Komponen: ' + komponen + ' | Status: ' + status);
        var item = {
          nik_pengurus: rowData.nik_pengurus, nokk: rowData.nokk,
          nama_pengurus: rowData.nama_pengurus, kelurahan: rowData.kelurahan,
          kecamatan: rowData.kecamatan, nikPenerima: nikPenerima,
          namaPenerima: namaPenerima, komponen: komponen, status: status,
          namaSekolah: '', kelas: ''
        };
        var komponenLower = komponen.toLowerCase();
        var isSekolah = komponenLower.indexOf('sekolah') !== -1 &&
          komponenLower.indexOf('lansia') === -1 &&
          komponenLower.indexOf('disabilitas') === -1 &&
          komponenLower.indexOf('aba') === -1;
        if (!isSekolah) { out.push(item); return resolve(); }
        log('Komponen Sekolah - membuka detail UP...');
        // v1.37c: hover + klik UP atomik juga (pola yang sama).
        if (!hoverAndClickUp(idx)) { out.push(item); return resolve(); }
        return delay(1500).then(function () {
          return scrapeDataSekolah();
        }).then(function (dataSekolah) {
          item.namaSekolah = dataSekolah.namaSekolah;
          item.kelas = dataSekolah.kelas;
          log('Sekolah: ' + item.namaSekolah + ' | Kelas: ' + item.kelas);
          out.push(item);
        }).catch(function (e) {
          log('Error ambil data sekolah: ' + e.message);
          out.push(item);
        }).then(function () {
          var dialogs = document.querySelectorAll('.MuiDialog-root');
          var upDialog = dialogs.length > 1 ? dialogs[1] : null;
          pressEscapeOnDialog(upDialog);
          return delay(1500);
        }).then(function () {
          var dialogs = document.querySelectorAll('.MuiDialog-root');
          if (dialogs.length > 1) {
            log('Modal UP masih ada, coba ESC lagi...');
            pressEscapeOnDialog(dialogs[1]);
            return delay(1000);
          }
        }).then(function () { resolve(); });
      }).catch(function (e) {
        log('Error scraping detail item ' + (idx + 1) + ': ' + e.message);
        resolve();
      });
    });
  }

  // ====== FASE 2c: SCRAPE DATA SEKOLAH (modal UP, dialog ke-2) ======
  function scrapeDataSekolah() {
    return new Promise(function (resolve, reject) {
      waitForFn(function () {
        var dialogs = document.querySelectorAll('.MuiDialog-root');
        return dialogs.length > 1 ? dialogs[1] : null;
      }, 5000).then(function (upModal) {
        if (!upModal) throw new Error('Modal UP tidak muncul');
        return delay(500).then(function () { return upModal; });
      }).then(function (upModal) {
        var container = upModal.querySelector('.css-1y2xum5');
        if (!container) {
          var divs = upModal.querySelectorAll('div');
          for (var i = 0; i < divs.length; i++) {
            if ((divs[i].innerText || '').indexOf('Data Sekolah') !== -1) {
              container = divs[i];
              break;
            }
          }
        }
        if (!container) throw new Error('Container Data Sekolah tidak ditemukan');
        var ps = container.querySelectorAll('p');
        resolve({
          namaSekolah: ps[1] ? (ps[1].innerText || '').trim() : '',
          kelas: ps[2] ? (ps[2].innerText || '').trim() : ''
        });
      }).catch(function (e) {
        reject(new Error('Gagal ambil data sekolah: ' + e.message));
      });
    });
  }

  // ====== MAIN FLOW ======
  function run() {
    if (running) { setStatus('Scrape sedang berjalan.'); return; }
    running = true;
    stopRequested = false;
    // v1.37: reset juga flag berhenti dari panel melayang supaya run baru
    // tidak langsung berhenti (flag disetel aplikasi saat Berhenti ditekan).
    window.__siksStopRequested = false;
    btnMulai.disabled = true;
    btnBerhenti.disabled = false;
    setStatus('Memulai scraping...');
    log('Memulai scraping dengan metode filter NIK...');

    loadProgress();

    var allNikData = [];
    var pendingNikData = [];

    getAllNikFromReactState().then(function (data) {
      allNikData = data;
      log('Total NIK yang akan diproses: ' + allNikData.length);
      pendingNikData = allNikData.filter(function (n) {
        return !processedNik[String(n.nik_pengurus)];
      });
      if (Object.keys(processedNik).length > 0) {
        log('Resume: ' + Object.keys(processedNik).length +
          ' NIK sudah diproses, sisa ' + pendingNikData.length + ' NIK');
      }
      setStatus('Memproses ' + pendingNikData.length + ' NIK...');
      var chain = Promise.resolve();
      pendingNikData.forEach(function (nikData, idx) {
        chain = chain.then(function () {
          if (stopRequestedNow()) return;
          return processOneNik(nikData, idx + 1, pendingNikData.length);
        });
      });
      return chain.then(function () { return { allNikData: allNikData }; });
    }).then(function (ctx) {
      if (stopRequestedNow()) {
        setStatus('Dihentikan. Progres tersimpan — tekan Mulai untuk lanjut.');
        log('Dihentikan oleh pengguna. Progres tersimpan.');
      } else {
        setStatus('Selesai: ' + results.length + ' baris.');
        log('SCRAPING SELESAI — total ' + results.length + ' baris, ' +
          ctx.allNikData.length + ' NIK diproses.');
      }
      showCsv();
      saveProgress();
    }).catch(function (e) {
      setStatus('ERROR: ' + e.message);
      log('ERROR: ' + e.message);
      // v1.37: run berakhir karena error — laporkan sebagai "Dihentikan"
      // supaya panel melayang menutup sendiri (auto-stop service).
      reportFloat('Dihentikan: ' + e.message);
      showCsv();
      saveProgress();
    }).then(function () {
      running = false;
      btnMulai.disabled = false;
      btnBerhenti.disabled = true;
    });
  }

  function processOneNik(nikData, currentNum, total) {
    setProgress('PROSES ' + currentNum + '/' + total + ': ' + nikData.nama_pengurus);
    log('PROSES ' + currentNum + '/' + total + ': ' + nikData.nama_pengurus);
    return filterByNik(nikData.nik_pengurus).then(function (hasResult) {
      if (!hasResult) {
        log('Skip ke NIK berikutnya');
        ensureNikRow(nikData);
        saveProgress();
        return;
      }
      var firstRow = document.querySelector('.MuiDataGrid-row');
      if (!firstRow) { ensureNikRow(nikData); saveProgress(); return; }
      var detailButton = firstRow.querySelector('button[aria-label="detail"]');
      if (!detailButton) {
        log('Tombol detail tidak ditemukan');
        ensureNikRow(nikData);
        saveProgress();
        return;
      }
      detailButton.click();
      return delay(1500).then(function () {
        log('Modal detail terbuka');
        return scrapeModalDetails(nikData);
      }).then(function (modalResults) {
        results.push.apply(results, modalResults);
        log('Berhasil scrape ' + modalResults.length + ' komponen');
        pressEscapeOnDialog(topDialog());
        return delay(1000);
      }).then(function () {
        ensureNikRow(nikData);
        saveProgress();
        showCsv();
      }).catch(function (e) {
        log('Error pada NIK ' + nikData.nik_pengurus + ': ' + e.message);
        pressEscapeOnDialog(topDialog());
        return delay(500).then(function () {
          ensureNikRow(nikData);
          saveProgress();
        });
      });
    });
  }

  // Catat NIK (termasuk yang tanpa hasil) supaya run berikutnya skip.
  function ensureNikRow(nikData) {
    var exists = results.some(function (r) {
      return String(r.nik_pengurus) === String(nikData.nik_pengurus);
    });
    if (!exists) results.push({ nik_pengurus: nikData.nik_pengurus, nokk: nikData.nokk,
      nama_pengurus: nikData.nama_pengurus, kelurahan: nikData.kelurahan,
      kecamatan: nikData.kecamatan, nikPenerima: '', namaPenerima: '',
      komponen: '', status: '', namaSekolah: '', kelas: '' });
    processedNik[String(nikData.nik_pengurus)] = true;
  }

  // ====== INIT ======
  buildPanel();
  log('SIKS Filter NIK v1.37c — hover+klik atomik, verifikasi unmask + retry');
  loadProgress();
  if (Object.keys(processedNik).length > 0) {
    setStatus('Siap. ' + Object.keys(processedNik).length +
      ' NIK sudah diproses sebelumnya — tekan Mulai untuk lanjut.');
  }
})();
