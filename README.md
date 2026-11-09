# Galeriku

Aplikasi Android tunggal (Java murni, tanpa Gradle) yang menggabungkan **Baca GPS Foto** dan **Stempel GPS** — galeri foto dengan EXIF + watermark, plus browser universal.

- **Tab Galeri**: grid foto MediaStore (terbaru dulu, filter folder, cari nama), detail foto + panel EXIF, Edit EXIF, Salin EXIF dari Foto Lain (SAF), Watermark Klasik (peta satelit Esri) dan Job Timestamp (peta jalan OSM).
- **Tab Browser**: browser universal — hanya address bar + Go + halaman awal kosong. Tanpa shortcut, tanpa bookmark, tanpa URL situs yang di-hardcode. File chooser memakai SAF `ACTION_OPEN_DOCUMENT` (bukan Photo Picker) supaya file yang diunggah tetap membawa GPS EXIF utuh.

## Aturan penyimpanan

Setiap file yang diubah disimpan sebagai **file BARU** di `DCIM/Galeriku` via MediaStore (`_exifedit` / `_exifcopy` / `_klasik` / `_job`). File asli tidak pernah ditimpa. Output watermark mempertahankan GPS + DateTimeOriginal aslinya.

## Build

```sh
./build.sh
```

Tanpa Gradle, memakai toolchain lokal: JDK 17, build-tools 34, platform android-34 (lihat `TOOL` di `build.sh`). Hasilnya `Galeriku.apk`, ditandatangani kunci debug lokal.

## Catatan repo

- `assets/` (ikon aplikasi, font Font Awesome) dan `Galeriku.apk` adalah file biner — tidak ikut snapshot ini; tambahkan lewat upload web GitHub bila diperlukan.
- `debug.keystore` adalah kunci tanda tangan debug lokal dan **tidak pernah** di-commit.
- Lisensi aset lihat `ASSETS-LICENSE.txt`.
