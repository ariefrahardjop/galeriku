// ==UserScript==
// @name        Upload Otomatis - Tes Galeriku
// @description Isi otomatis input file di halaman Tes Upload Galeriku dengan foto 20261120_071827.jpg lewat bridge Galeriku (selectImage). Jalan otomatis saat halaman tes dibuka di browser Galeriku v1.30+.
// @match       *://muse.ai/s/tes-upload-galeriku-sxw78qxd63loxc/*
// ==/UserScript==
//
// CARA PASANG (Galeriku v1.29+):
//   Menu titik tiga browser > Userscript > Tambah dari URL, tempel URL file ini,
//   lalu set Pola URL: *://muse.ai/s/tes-upload-galeriku-sxw78qxd63loxc/*
//   dan pastikan saklar skrip AKTIF. Buka halaman tes - foto terisi otomatis.
//   Bisa juga dijalankan manual dari layar Userscript > ketuk baris skrip > Jalankan sekali.
//
// CARA KERJA:
// - Skrip ini hanya memanggil bridge; pratinjau + pengisian input dilakukan
//   oleh halaman tes sendiri (callback window.onGalerikuImagePicked).
// - Kalau file tidak ada di HP, callback menerima null dan halaman menampilkan
//   "File tidak ditemukan". Pastikan 20261120_071827.jpg ada di HP
//   (cek di Berkas > DCIM > Camera) atau ganti FILE_NAME di bawah.

(function () {
  var FILE_NAME = '20261120_071827.jpg';

  if (window.Galeriku && typeof Galeriku.selectImage === 'function') {
    console.log('[upload-tes] selectImage: ' + FILE_NAME);
    Galeriku.selectImage(FILE_NAME);
  } else {
    console.log('[upload-tes] Bridge Galeriku.selectImage tidak ada. Buka halaman ini di browser Galeriku v1.30+.');
  }
})();
