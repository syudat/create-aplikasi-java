# Panduan Bangun APK lewat GitHub Actions

## Persiapan satu kali: cukup token
1. GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)**
   → Generate new token. Centang izin **repo** dan **workflow**.
2. Di aplikasi: menu ⋮ → **Pengaturan GitHub**, tempel token, Simpan.

Tidak perlu mengisi nama pengguna atau nama repo. Saat build pertama, aplikasi otomatis:
- membuat repo **privat** `pembuat-aplikasi-build` di akun Anda,
- mengunggah workflow `bangun-proyek.yml` dan mesin build (`mesin/engine.zip`, sekali saja).

## Setiap kali membangun
Tekan **Bangun** di editor (atau tahan lama proyek → Bangun proyek). Aplikasi akan:
1. memeriksa sintaks semua skrip (`.lua`, `.aly`);
2. menawarkan **Jalankan dulu** untuk mencoba skrip;
3. mengunggah hanya file yang berubah ke `proyek/<nama paket>/` di repo;
4. menjalankan workflow, menunggu selesai, lalu mengunduh APK ke
   `Download/project/<nama proyek>/bin/<nama paket>.apk`.

Jika build gagal, ringkasan error tampil di layar dan log lengkap disimpan di
`bin/log-build.txt` dalam folder proyek.

Catatan: repo privat memakai kuota menit GitHub Actions gratis (2000 menit/bulan untuk akun gratis).

## Android 16 & versi SDK
- compileSdk 33, targetSdk 33, minSdk 21. Aplikasi tetap berjalan di Android 14-16.
- Pustaka native dibangun dengan penyelarasan halaman 16 KB (kompatibel perangkat 16 KB).

## Editor: alat bantu
- Bilah simbol: Tab, ( ), [ ], { }, " ", ' ', panah kiri/kanan, dan tanda baca.
- Perbaiki impor: menambah `import` kelas yang dipakai tetapi belum diimpor.
- Perbaiki sintaks: memperbaiki kurung/`end` yang kurang dan menandai baris yang masih salah.
- Rapikan: merapikan indentasi.

## Bangun di perangkat (offline, tanpa GitHub dan tanpa Termux)
Pilih **Bangun > Bangun di perangkat (offline)**. Aplikasi merakit dan menandatangani APK sendiri:
- `aapt2` (arm64 dan armv7) disertakan sebagai pustaka native aplikasi; pustaka pendukungnya ada di `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}`
  (disiapkan dengan `tools/siapkan_jnilibs.py` dari paket alat Termux).
- Kerangka APK = APK Create Aplikasi by Al Kautsar yang terpasang (dex, pustaka native, resources.arsc).
- Tanda tangan memakai pustaka apksig dan kunci debug bawaan (`assets/alat/debug.pk8` + `debug.der`).
- Hasil: `<proyek>/bin/<nama.paket>.apk`, log: `<proyek>/bin/log-build.txt`.
Batas: HP ARM saja, 64-bit (arm64-v8a) dan 32-bit (armeabi-v7a); x86/emulator tidak didukung. Memperbarui aplikasi yang sudah terpasang membutuhkan kunci yang sama (semua APK dari aplikasi ini memakai kunci bawaan yang sama).

## Menu utama, keystore, dan tanda tangan
- Menu utama: Buat Project, Kelola Project, Buat Keystore, Tentang Alat.
- **Buat Keystore** menyimpan kunci di `Download/project/signing/<nama>/kunci.p12` (PKCS12, RSA 2048). Kata sandi tidak disimpan.
- Dua pilihan di form project (disimpan di init.lua):
  - `split_abi` : true = dua APK (`<paket>-64bit.apk`, `<paket>-32bit.apk`); false = satu APK untuk semua (`<paket>.apk`).
  - `tanda_tangan_pribadi` : true = setelah build aplikasi meminta keystore dan kata sandi; false = kunci bawaan.
- Hasil build (perangkat maupun GitHub Actions) mula-mula berupa `<paket>-belum-ditandatangani.apk`; sesudah ditandatangani berkas itu dihapus.
- Izin pemindaian Wi-Fi ada di daftar izin (Pemindaian Wi-Fi) dan terpilih secara bawaan.

## Enkripsi skrip Lua
Semua berkas `.lua` dan `.aly` yang dikemas ke dalam APK (aplikasi ini sendiri maupun aplikasi
yang dibangun dengannya) dienkripsi dengan AES-256-CBC, kunci acak per build (disimpan sebagai
`assets/.sec` di dalam APK itu sendiri, kunci berbeda untuk setiap APK yang dihasilkan). Tujuannya
supaya orang yang membuka APK dan melihat ke folder `assets` tidak langsung membaca kode sumbernya.
Ini **bukan** perlindungan terhadap pembongkaran serius: kunci ikut berada di dalam APK yang sama
(diperlukan agar aplikasi berjalan tanpa server), jadi orang yang mengerti caranya tetap bisa
mengambil kunci itu. Tujuannya mencegah pencurian kode yang asal comot.

Sumber asli skrip IDE ini ada di `app/src/main/assets-src/` (boleh diedit). Berkas terenkripsi di
`app/src/main/assets/` dihasilkan otomatis oleh `tools/enkripsi_aset.py` — jalankan ulang skrip itu
(`python3 tools/enkripsi_aset.py app/src/main`) setiap kali `assets-src/` diedit, sebelum membangun
APK aplikasi ini sendiri. Berkas proyek buatan pengguna dienkripsi otomatis saat Bangun (baik di
perangkat maupun lewat GitHub Actions); pengguna tidak perlu melakukan apa pun.

## Ekspor proyek (ZIP) dan memasang APK

- **Ekspor ZIP:** tahan lama nama proyek di Kelola Project, lalu pilih **Ekspor ZIP** (atau dari menu editor).
  Hasilnya tersimpan rapi di `Download/project/<NamaProyek>.zip`, lengkap dengan jalur dan
  ukurannya, plus tombol **Bagikan** untuk mengirim ke teman. Folder `bin/` (APK hasil
  build) sengaja tidak ikut, supaya file tetap kecil — proyek sumber biasanya hanya
  puluhan KB, bukan puluhan MB.
- **Tombol Pasang:** setelah build dan tanda tangan selesai, dialog **APK siap dipasang**
  kini punya tombol **Pasang**. Kalau APK dipisah 64 bit / 32 bit, pilih dulu yang mana.
- **Izin pasang APK:** Android 8 ke atas meminta izin "Pasang aplikasi" untuk aplikasi ini
  dulu. Izin ini sudah dideklarasikan di manifest (`REQUEST_INSTALL_PACKAGES`). Bila belum
  aktif, tombol Pasang akan membuka Pengaturan **"Izinkan dari sumber ini"** — aktifkan,
  kembali ke aplikasi, lalu tekan **Pasang** lagi. Izin ini juga bisa dicek dari menu
  titik tiga: **Izin pasang APK**.

## Nama berkas APK
Nama berkas hasil (setelah ditandatangani) memakai nama aplikasi + versi, bukan nama paket:
lower-case, spasi diganti titik, lalu versi ditempel langsung. Contoh: aplikasi "Garuda Decode"
versi "1.0" -> `garuda.decode1.0.apk` (atau `garuda.decode1.0-64bit.apk` / `-32bit.apk` bila
arsitektur dipisah).
