// ==UserScript==
// @name        Upload ke Jimpl (EXIF viewer)
// @description Isi otomatis input file jimpl.com dengan foto lewat bridge Galeriku, lalu situs membaca EXIF-nya (termasuk GPS). Coba dulu 20261120_071827.jpg; kalau tidak ada, buka pemilih manual.
// @match       *://jimpl.com/*
// ==/UserScript==
//
// CARA PASANG (Galeriku v1.29+):
//   Menu titik tiga browser > Userscript > Tambah dari URL, tempel URL file ini,
//   lalu set Pola URL: *://jimpl.com/*
//   dan pastikan saklar skrip AKTIF. Buka https://jimpl.com/ - foto terisi otomatis.
//   Tombol melayang "Upload foto" di kanan bawah halaman memanggil pemilih manual.
//   Perlu Galeriku v1.30+ (selectImage) - pickImage jalan sejak v1.29.
//
// CARA KERJA:
// - Skrip mencari input file situs: document.querySelector('input[type="file"]')
//   (jimpl.com hanya punya SATU input file di seluruh halaman; id/class/accept-nya
//   tidak dipublikasikan situs, jadi jangan andalkan selector lain).
// - Data URL dari bridge diubah jadi File, dimasukkan ke input lewat DataTransfer,
//   lalu event 'change' di-dispatch supaya situs memprosesnya.
// - Kalau situs ternyata mendengarkan event 'drop' (bukan 'change'), unggahan tidak
//   jalan - kabari saya, nanti saya tambah varian drop event.
// - File diproses di server Jimpl (ExifTool) dan dihapus situs setelah 24 jam.
// - Nama file untuk foto yang dipilih manual tidak diketahui bridge, jadi dipakai
//   nama generik 'foto-galeriku.jpg' (isi file + EXIF-nya tetap asli).

(function () {
  var FILE_NAME = '20261120_071827.jpg';

  function log(msg) { console.log('[upload-jimpl] ' + msg); }

  function findInput() {
    return document.querySelector('input[type="file"]');
  }

  function fileFromDataUrl(dataUrl, name) {
    return fetch(dataUrl).then(function (res) { return res.blob(); }).then(function (blob) {
      var ext = (blob.type || '').split('/')[1] || 'jpg';
      var fileName = name || ('foto-galeriku.' + ext);
      return new File([blob], fileName, { type: blob.type || 'image/jpeg' });
    });
  }

  function fillInput(file) {
    var input = findInput();
    if (!input) { log('Input file tidak ditemukan di halaman.'); return false; }
    var dt = new DataTransfer();
    dt.items.add(file);
    input.files = dt.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
    log('File dimasukkan ke input, event change dikirim: ' + file.name + ' (' + file.size + ' byte).');
    return true;
  }

  window.onGalerikuImagePicked = function (dataUrl) {
    if (!dataUrl) {
      log('File tidak ditemukan (null). Ketuk tombol "Upload foto" di kanan bawah untuk pilih manual.');
      return;
    }
    fileFromDataUrl(dataUrl, window.__jimplFileName || null).then(function (file) {
      fillInput(file);
    }).catch(function (e) {
      log('Gagal mengubah data menjadi file: ' + e.message);
    });
  };

  function addButton() {
    if (document.getElementById('galeriku-upload-btn')) return;
    var btn = document.createElement('button');
    btn.id = 'galeriku-upload-btn';
    btn.textContent = 'Upload foto';
    btn.style.cssText = 'position:fixed;right:12px;bottom:12px;z-index:99999;padding:10px 14px;border-radius:8px;border:0;background:#111;color:#fff;font-size:14px;';
    btn.addEventListener('click', function () {
      if (window.Galeriku && typeof Galeriku.pickImage === 'function') {
        window.__jimplFileName = null;
        Galeriku.pickImage();
      } else {
        log('Bridge Galeriku.pickImage tidak ada. Buka halaman ini di browser Galeriku v1.29+.');
      }
    });
    document.body.appendChild(btn);
  }

  addButton();

  if (!window.Galeriku || typeof Galeriku.selectImage !== 'function') {
    log('Bridge Galeriku.selectImage tidak ada. Buka halaman ini di browser Galeriku v1.30+ (pickImage butuh v1.29+).');
    return;
  }
  window.__jimplFileName = FILE_NAME;
  log('selectImage: ' + FILE_NAME);
  Galeriku.selectImage(FILE_NAME);
})();
