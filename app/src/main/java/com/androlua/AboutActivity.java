package com.androlua;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Layar Tentang Alat Create Aplikasi dalam Java murni (Tahap 2E).
 *
 * Mereplikasi persis showTools() di main.lua: dialog "Tentang Alat" berisi
 * teks panduan TENTANG. Di sini tampil sebagai layar (bukan dialog) supaya
 * bisa dibuka dari menu opsi semua layar Java: TextView yang sama (15sp,
 * bisa dipilih, padding 16/8) di dalam ScrollView, judul "Tentang Alat",
 * tombol "Tutup" (sama seperti tombol Tutup pada dialog Lua) untuk kembali.
 *
 * Isi teks panduan ditulis ulang dalam bahasa baku/formal gaya deskripsi
 * Play Store (restrukturisasi tanpa Lua, 2026-11-17): tanpa referensi Lua,
 * jenis proyek hanya HTML / Situs Web / Java / Kotlin / Compose / Python, dan
 * Compose dapat dibangun lewat GitHub Actions atau di Termux (Gradle).
 * Python memakai runtime Chaquopy (SDK minimum 24) dan dapat dibangun di
 * perangkat maupun lewat GitHub Actions.
 * Semua fitur dan panduan tetap lengkap — hanya bahasanya yang diubah.
 */
public class AboutActivity extends Activity {

    /** Kode bahasa efektif layar ini (tahap tampilan B, 2026-11-23). */
    private String kodeBahasa = "id";

    /** Teks antarmuka dalam bahasa efektif (TeksUI, fallback Indonesia). */
    private String t(String kunci) {
        return TeksUI.teks(kodeBahasa, kunci);
    }

    // Teks panduan lengkap, bahasa baku/formal gaya deskripsi Play Store
    // (ditulis ulang dari TENTANG di main.lua; 209 baris, tanpa Lua).
    private static final String TENTANG =
    "Selamat datang di Create Aplikasi by Al Kautsar"
    + "\n" +
    "Create Aplikasi adalah aplikasi pembuat aplikasi Android yang berjalan langsung di ponsel, tanpa memerlukan laptop. Aplikasi ini memungkinkan siapa saja membuat aplikasi Android sendiri, termasuk pengguna yang baru belajar membuat aplikasi dan pengguna pembaca layar. Panduan lengkap penggunaan aplikasi ini tersedia di layar Tentang Alat ini."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Apa aplikasi ini dan untuk siapa")
    + "\n" +
    "Create Aplikasi adalah alat pembuat aplikasi Android yang berjalan di ponsel. Aplikasi ini ditujukan bagi siapa saja yang ingin membuat aplikasi Android sendiri, termasuk pemula dan pengguna pembaca layar. Aplikasi ini menyediakan editor kode, tombol Jalankan untuk menguji proyek, tombol Bangun untuk membuat APK, serta menu untuk menandatangani dan memasang APK."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Tujuan aplikasi ini")
    + "\n" +
    TeksUI.t("Aplikasi ini dibuat agar siapa saja dapat membuat aplikasi Android sendiri dengan mudah, langsung dari ponsel. Tampilannya dibuat sesederhana mungkin agar pengguna baru pun dapat langsung mencoba.")
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Cara membuat proyek baru, langkah demi langkah")
    + "\n" +
    TeksUI.t("1. Tekan Buat Project di menu utama.")
    + "\n" +
    TeksUI.t("2. Pilih jenis proyek: HTML, Situs Web, Java, Kotlin, Compose, atau Python.")
    + "\n" +
    TeksUI.t("3. Isi nama aplikasi, contoh: Garuda Decode.")
    + "\n" +
    "4. Isi nama paket (package name), contoh: com.android.garudadecode. Nama paket harus menggunakan huruf kecil dan terdiri dari minimal dua bagian yang dipisahkan titik."
    + "\n" +
    TeksUI.t("5. Isi versi aplikasi, contoh: 1.0, dan kode versi berupa angka, contoh: 1. Apabila dikosongkan, versi akan diisi otomatis dengan 1.0 dan kode versi 1.")
    + "\n" +
    "6. Isi SDK minimum dan SDK target apabila diperlukan. Apabila dikosongkan, nilai bawaan akan digunakan. SDK minimum adalah versi Android terendah yang didukung, contoh: 21 berarti Android 5.0. SDK target maksimum adalah 36 (Android 16)."
    + "\n" +
    TeksUI.t("7. Tekan tombol Pilih Izin untuk memilih izin aplikasi, contoh: INTERNET. Izin INTERNET sudah dicentang secara bawaan. Centang hanya izin yang memang dibutuhkan oleh aplikasi.")
    + "\n" +
    TeksUI.t("8. Apabila diinginkan, centang \"Gunakan arsitektur terpisah 64 bit dan 32 bit\" agar ukuran APK lebih kecil, dan centang \"Gunakan tanda tangan pribadi\" apabila ingin memakai keystore buatan sendiri.")
    + "\n" +
    TeksUI.t("9. Tekan Buat Proyek. Proyek baru tersimpan di folder Download/project dan langsung terlihat di Kelola Project.")
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Jenis proyek yang tersedia")
    + "\n" +
    TeksUI.t("• HTML: tampilan dari index.html, style.css, dan script.js, berjalan melalui WebView. Dibangun langsung di ponsel.")
    + "\n" +
    TeksUI.t("• Situs Web: website dengan menu (Beranda, Tentang, Kontak) dari index.html, tentang.html, kontak.html, style.css, dan script.js, berjalan melalui WebView. Dibangun langsung di ponsel.")
    + "\n" +
    "• Java: dibangun di ponsel. MainActivity berada di app/src/main/java/<nama-paket>/MainActivity.java. Tampilan dari app/src/main/res/layout/activity_main.xml, teks dari app/src/main/res/values/strings.xml, dan tema dari app/src/main/res/values/styles.xml."
    + "\n" +
    "• Kotlin: dapat dibangun di perangkat (offline) menggunakan compiler Kotlin yang disertakan dalam aplikasi, atau melalui GitHub Actions. MainActivity berada di app/src/main/java/<nama-paket>/MainActivity.kt, dan berkas res sama seperti proyek Java. Build Kotlin pertama lebih lambat karena ukuran compiler yang besar, dan berkas .java yang merujuk kelas Kotlin belum dapat dibangun (berkas .java dikompilasi terlebih dahulu sebelum berkas .kt)."
    + "\n" +
    "• Compose: aplikasi Android modern yang antarmukanya dibuat dengan Jetpack Compose (bukan layout XML). MainActivity berada di app/src/main/java/<nama-paket>/MainActivity.kt. Proyek Compose dibangun melalui GitHub Actions (menu Bangun proyek, lalu Bangun online) atau di Termux dengan Gradle, karena Compose membutuhkan pustaka Jetpack Compose yang tidak tersedia di perangkat."
    + "\n" +
    "• Python: logika aplikasi ditulis dalam bahasa Python (Chaquopy) di app/src/main/python/main.py, dan dijalankan oleh host MainActivity.java di app/src/main/java/<nama-paket>/MainActivity.java. Proyek Python dapat dibangun langsung di ponsel (runtime Chaquopy disertakan dalam aplikasi) maupun melalui GitHub Actions. SDK minimum proyek Python adalah 24 (Android 7.0)."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Susunan berkas proyek Java, HTML, Situs Web, Kotlin, Compose, dan Python")
    + "\n" +
    "Proyek Java, HTML, Situs Web, Kotlin, Compose, dan Python tersusun seperti proyek Android pada umumnya. Di dalam folder app terdapat folder src, dan di dalamnya terdapat folder main. Di dalam folder main terdapat AndroidManifest.xml di samping folder java, folder res yang berisi layout/activity_main.xml dan values/strings.xml serta values/styles.xml, dan untuk HTML dan Situs Web terdapat folder assets yang berisi index.html (Situs Web juga memiliki tentang.html dan kontak.html). Proyek HTML dan Situs Web juga memiliki MainActivity sendiri di app/src/main/java/<nama-paket>/MainActivity.java, yang memuat index.html di WebView dan menjadi launcher di AndroidManifest. Proyek Python memiliki kode Python di app/src/main/python/main.py dan host MainActivity.java di app/src/main/java/<nama-paket>/MainActivity.java. AndroidManifest.xml, layout, strings, dan styles dapat dibuka dan diubah di editor, sama seperti MainActivity. Proyek lama yang manifest-nya masih berada di samping folder app tetap dapat dibangun, karena pada saat membangun, aplikasi memeriksa AndroidManifest.xml di app/src/main/ terlebih dahulu, kemudian yang di samping folder app, dan terakhir berkas data proyek."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Aturan izin (penting)")
    + "\n" +
    "Izin yang dicentang saat membuat proyek hanya tercatat di satu tempat, sesuai jenis proyeknya. Untuk proyek Java, HTML, Situs Web, Kotlin, Compose, dan Python, izin yang dicentang tercatat HANYA di AndroidManifest.xml yang berada di app/src/main/, di samping folder java, di dalam folder proyek. Berkas data proyek untuk proyek Java, HTML, Situs Web, Kotlin, Compose, dan Python tidak berisi daftar izin. Apabila memilih Java lalu mencentang INTERNET dan CAMERA, yang tercatat di AndroidManifest.xml hanyalah INTERNET dan CAMERA, tanpa izin lain yang ikut. Pada saat membangun, aplikasi membaca izin dari tempat yang sama: dari AndroidManifest.xml di app/src/main/ untuk proyek Java, HTML, Situs Web, Kotlin, Compose, dan Python (apabila tidak ada, dari AndroidManifest.xml di samping folder app untuk proyek lama), dan dari berkas data proyek untuk proyek lama."
    + "\n" +
    ""
    + "\n" +
    "Cara memakai editor"
    + "\n" +
    "Buka Kelola Project, ketuk nama proyek untuk membuka editor. Di editor, berkas dapat dibuka, kode dapat ditulis, dan perubahan dapat disimpan. Untuk proyek Java, HTML, Situs Web, Kotlin, Compose, dan Python, berkas tersusun di dalam folder app/src/main. Untuk membagikan proyek, gunakan Ekspor ZIP (lihat bagian di bawah)."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Cara menjalankan proyek")
    + "\n" +
    TeksUI.t("Di editor, tekan Jalankan untuk menguji proyek. Untuk proyek HTML dan Situs Web, yang dijalankan adalah index.html melalui WebView. Apabila terjadi kesalahan, baca pesan kesalahannya, perbaiki kode, lalu jalankan lagi.")
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Cara membangun APK")
    + "\n" +
    "Di editor, tekan Bangun. Terdapat dua cara membangun. Pertama, Bangun di perangkat: membangun langsung di ponsel tanpa internet, untuk proyek HTML, Situs Web, Java, Kotlin, dan Python. Kedua, Bangun online melalui GitHub Actions: membangun melalui internet di GitHub, dan dapat digunakan untuk semua jenis proyek. Proyek Compose hanya dapat dibangun dengan cara kedua (Bangun online) atau di Termux dengan Gradle, karena Compose membutuhkan pustaka Jetpack Compose yang tidak tersedia di perangkat. Hasil build pertama belum ditandatangani. Setelah itu aplikasi menandatanganinya: otomatis dengan kunci bawaan, atau dengan tanda tangan pribadi apabila kotak \"Gunakan tanda tangan pribadi\" dicentang saat membuat proyek."
    + "\n" +
    ""
    + "\n" +
    "Cara memasang APK"
    + "\n" +
    "Apabila APK sudah jadi, tekan Pasang untuk memasangnya. Di Android 8 ke atas, ponsel akan meminta \"Izinkan dari sumber ini\" terlebih dahulu, karena aplikasi ini memasang APK dari luar Play Store. Aktifkan izin tersebut satu kali di Pengaturan, lalu kembali ke aplikasi: pemasangan akan berlanjut otomatis tanpa perlu menekan Pasang lagi. Apabila tombol Pasang tidak muncul, pastikan build sudah selesai dan APK sudah dibuat."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Cara menandatangani aplikasi")
    + "\n" +
    "Setiap APK harus ditandatangani agar dapat dipasang. Tersedia dua pilihan. Pertama, kunci bawaan: aplikasi menandatangani sendiri secara otomatis, tanpa perlu melakukan apa pun. Kedua, tanda tangan pribadi: buat terlebih dahulu keystore melalui menu Buat Keystore di menu utama, simpan kata sandinya dengan baik, lalu centang \"Gunakan tanda tangan pribadi\" saat membuat proyek. Keystore tersimpan di Download/project/signing."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Ekspor proyek menjadi ZIP")
    + "\n" +
    "Ekspor ZIP disediakan agar proyek dapat dibagikan kepada teman. Caranya: di Kelola Project, tahan lama nama proyek, lalu pilih Ekspor ZIP. Hasilnya tersimpan di Download/project/<NamaProyek>.zip. Folder bin tidak ikut disertakan, sehingga ukurannya kecil, hanya puluhan KB. Berkas ZIP tersebut dapat dikirim kepada teman atau diunggah ke GitHub."
    + "\n" +
    ""
    + "\n" +
    "Ekstensi JAWS"
    + "\n" +
    "Ekstensi JAWS menggunakan mesin ekstensi dari Jieshuo (CSR), bukan pembaca layarnya. Dengan demikian, rancangan tetap dapat berjalan walaupun pengguna memakai TalkBack. Caranya: buka Ekstensi JAWS di menu utama. Karena klik otomatis berjalan sendiri, layanan harus diaktifkan terlebih dahulu di Pengaturan Aksesibilitas. Setelah itu, rancangan baru dapat dibuat: ketik nama rancangan, tekan OK, lalu isi kotaknya."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Daftar Klik dipisah agar rapi")
    + "\n" +
    "Di dalam rancangan terdapat dua kotak: kotak skrip dan kotak Daftar Klik. Daftar tombol disimpan terpisah di berkas daftar_klik.txt, satu nama per baris. Contoh isinya: tombolSimpan, tombolHapus, tombolBuka. Untuk membuka aplikasi, tulis saja >WhatsApp di daftar tersebut. Rancangan lama yang belum memiliki berkas daftar_klik.txt akan menampilkan contoh bawaan tersebut."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Fungsi sederhana yang disediakan")
    + "\n" +
    TeksUI.t("• klikTombol(\"tombolSimpan\") — klik satu tombol berdasarkan teksnya.")
    + "\n" +
    TeksUI.t("• klikDaftarTombol(daftar) — klik daftar di daftar_klik.txt, satu tombol per baris, berurutan, lalu memberi tahu berapa yang berhasil dan berapa yang tidak ditemukan.")
    + "\n" +
    TeksUI.t("• bukaAplikasi(\"WhatsApp\") — buka aplikasi berdasarkan namanya, atau tulis \">WhatsApp\" di daftar.")
    + "\n" +
    TeksUI.t("• klik(540, 1200) — klik di koordinat layar.")
    + "\n" +
    TeksUI.t("Semua fungsi dibuat sederhana terlebih dahulu. Apabila sudah terbiasa, fungsi tambahan dapat ditambahkan kemudian.")
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Tentang pembaca layar")
    + "\n" +
    "Aplikasi ini dirancang agar ramah pembaca layar. Setiap kolom isian memiliki label dan contoh pengisian, dan semua tombol dapat dijangkau tanpa gestur yang rumit. Ekstensi JAWS dibuat agar tetap berjalan walaupun pengguna memakai TalkBack, karena yang digunakan adalah mesin ekstensinya, bukan pembaca layarnya."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Privasi dan keamanan")
    + "\n" +
    "Proyek dan keystore tersimpan di ponsel Anda. Token GitHub hanya dipakai untuk bangun online dan disimpan di dalam aplikasi ini. Jangan bagikan token atau kata sandi keystore Anda kepada siapa pun. Apabila memakai tanda tangan pribadi, simpan keystore dan kata sandinya di tempat yang aman, karena apabila hilang, APK lama tidak dapat diperbarui dengan tanda tangan yang sama."
    + "\n" +
    ""
    + "\n" +
    "Kebutuhan"
    + "\n" +
    "Android 5.0 atau lebih baru. Izin penyimpanan: di Android 11 ke atas memakai izin akses semua file, di Android 10 ke bawah memakai izin penyimpanan biasa (baca/tulis penyimpanan) karena Android 10 tidak memiliki izin akses semua file. Membangun di perangkat memerlukan ponsel berprosesor ARM (32 atau 64 bit). Membangun online melalui GitHub Actions memerlukan internet dan token GitHub. Proyek Kotlin dapat dibangun di perangkat, tetapi build pertamanya lebih lambat dan memakai memori lebih banyak karena ukuran compiler Kotlin yang besar. Proyek Compose dibangun melalui GitHub Actions atau di Termux dengan Gradle. Proyek Python membutuhkan Android 7.0 atau lebih baru (SDK minimum 24) dan APK hasilnya lebih besar karena menyertakan runtime Python (Chaquopy) beserta pustaka standarnya."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Terminal")
    + "\n" +
    "Menu Terminal di layar utama membuka terminal di dalam aplikasi ini. Perintah seperti ls dapat digunakan untuk melihat isi folder proyek, cd untuk berpindah folder, dan git untuk mengelola kode. Keluaran perintah tampil sebagai teks yang dapat dibaca pembaca layar, dan terdapat tombol Bacakan hasil untuk mendengarkan hasil terakhir. Terminal ini memakai shell sistem ponsel, sehingga program interaktif seperti nano dan vim belum dapat dipakai di sini."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Lisensi (versi berbayar)")
    + "\n" +
    "Untuk menjual aplikasi, centang \"Aktifkan Lisensi\" saat membuat proyek, lalu isi nomor WhatsApp Anda. Aplikasi hasil build akan meminta kode lisensi saat pertama dibuka: pengguna melihat ID perangkatnya, menekan tombol Kirim ID Perangkat untuk mengirim ID tersebut ke WhatsApp Anda, lalu Anda membalas kode lisensinya. Kode lisensi dihitung dari ID perangkat dan kata sandi proyek (lisensi_seed di berkas data proyek) — simpan kata sandi tersebut dengan baik dan jangan dibagikan. Setelah kode yang benar dimasukkan, aplikasi terbuka dan tidak meminta kode lagi."
    + "\n" +
    ""
    + "\n" +
    "Pembuat Ikon Otomatis"
    + "\n" +
    "Ikon proyek baru masih transparan, sehingga tersedia Pembuat Ikon di menu utama. Caranya: tekan Pembuat Ikon, tulis satu atau dua huruf (misalnya inisial nama aplikasi), pilih warna latar dan warna tulisan, lihat pratinjaunya, lalu tekan Simpan ke Proyek untuk memakai ikon tersebut di proyek yang dipilih, atau Simpan ke Download untuk memakai berkas PNG-nya di tempat lain. Setelah proyek dibuat, aplikasi juga menawarkan pembuatan ikon. Ikon lama proyek dapat diganti kapan saja dari menu proyek di Kelola Project (tahan lama nama proyek, lalu pilih Ganti Ikon)."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Template Proyek Siap Pakai")
    + "\n" +
    "Apabila tidak ingin memulai dari proyek kosong, pilih template saat membuat proyek: Kalkulator (Java), Catatan (Java), Daftar Tugas (Kotlin), Kalkulator Web (HTML), Situs Profil (Situs Web), atau Penghitung (Compose). Memilih template otomatis mengubah jenis proyek mengikuti template tersebut, dan proyek dibuat dengan kode awal yang sudah jadi — tinggal dijalankan atau diubah. Apabila jenis proyek diubah lagi secara manual setelah memilih template, template dibatalkan dan proyek kembali kosong. Untuk template HTML dan Situs Web, template mengganti isi index.html, termasuk skrip HTML yang diketik di form."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Cadangan dan Pulihkan Proyek")
    + "\n" +
    "Agar proyek tidak hilang, tersedia fitur cadangan. Caranya: di Kelola Project, tahan lama nama proyek, lalu pilih Cadangkan. Cadangan tersimpan sebagai ZIP di folder Download/backup dengan nama proyek dan tanggal, contoh: GarudaDecode-2026-11-14-123045.zip. Isinya sama seperti Ekspor ZIP (seluruh isi proyek, tanpa folder bin). Untuk memulihkan: buka menu di Kelola Project, pilih Pulihkan cadangan, lalu pilih berkas cadangannya. Apabila nama proyek sudah ada, aplikasi akan menanyakan: Timpa proyek lama, atau simpan dengan Nama baru."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Bagikan dan Impor Proyek")
    + "\n" +
    "Untuk berbagi proyek kepada teman: di Kelola Project, tahan lama nama proyek, lalu pilih Bagikan. Proyek dikirim sebagai ZIP melalui aplikasi berbagi (WhatsApp, Telegram, dan lain-lain). Untuk menerima proyek dari teman: buka menu di Kelola Project, pilih Impor proyek, lalu pilih berkas ZIP-nya. Berkas ZIP tersebut harus berisi berkas data proyek — apabila tidak ada, berkas ditolak dengan pesan yang jelas, karena berkas tersebut bukan proyek Create Aplikasi. Apabila nama proyek sudah ada, aplikasi akan menanyakan: Timpa proyek lama, atau simpan dengan Nama baru."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pencarian dan Filter Proyek")
    + "\n" +
    "Apabila proyek sudah banyak, di Kelola Project terdapat kotak pencarian dan penyaring jenis. Ketik nama proyek di kotak pencarian, daftar langsung tersaring (huruf besar dan kecil tidak dibedakan). Penyaring jenis membatasi daftar ke satu jenis saja: Semua, HTML, Situs Web, Java, Kotlin, Compose, atau Python. Menu tiap proyek tetap dapat dibuka seperti biasa dari daftar yang sudah tersaring."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Bangun Aplikasi dengan Suara")
    + "\n" +
    "Apabila tidak ingin mengetik, gunakan suara: tekan Bangun dengan Suara di menu utama, lalu ucapkan perintah. Perintah yang dapat diucapkan: \"Buat proyek kalkulator\", \"Bangun proyek Kalkulator\", \"Buka proyek Kalkulator\", \"Jalankan proyek Kalkulator\", \"Daftar proyek\", \"Buka ikon\", \"Kembali\", dan \"Bantuan\". Aplikasi menjawab dengan suara dan teks, sehingga tetap dapat dipakai tanpa melihat layar. Pengenalan suara memakai aplikasi pengenal suara bawaan HP (gratis; dapat berjalan offline apabila paket bahasa Indonesia terpasang). Apabila HP tidak memiliki pengenal suara, perintah dapat ditulis di kotak teks di layar yang sama."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("AI Pembuat Aplikasi")
    + "\n" +
    "Tulis atau diktekan aplikasi yang diinginkan di layar AI Pembuat Aplikasi, contoh: \"aplikasi catatan dengan tombol simpan\". Aplikasi menganalisis kata kuncinya di dalam HP sendiri (tanpa internet, tanpa biaya), lalu membuat proyek sungguhan dari template yang paling cocok: kalkulator, catatan, daftar tugas, kalkulator web, situs profil, atau penghitung. Apabila deskripsi Anda belum memiliki template yang cocok, aplikasi membuat proyek kosong dan menjelaskannya dengan jujur — tidak berpura-pura mengerti. Proyek hasil AI dapat langsung dibuka di Kelola Project."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pratinjau Langsung ke HP Teman")
    + "\n" +
    "Untuk menunjukkan proyek kepada teman sebelum dibangun: di Kelola Project, tahan lama nama proyek, lalu pilih Pratinjau ke HP Teman. Aplikasi membuat paket pratinjau kecil berisi berkas utama proyek (index.html, MainActivity, atau main.py) ditambah BACA-SAYA.txt berisi petunjuk dan info.txt. Paket tersebut dikirim sebagai ZIP melalui WhatsApp atau Telegram. Teman yang menerima dapat langsung membuka pratinjau: halaman HTML terbuka di browser, kode Java/Kotlin/Python dibaca sebagai teks."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pasar Proyek")
    + "\n" +
    "Pasar Proyek berisi proyek contoh bawaan aplikasi yang dapat dipasang ke folder proyek dengan satu ketukan: Kalkulator, Catatan, Daftar Tugas, Kalkulator Web, Situs Profil, Penghitung, Jam Digital, dan Kuis Web. Pasar online (unduh contoh dari internet) belum tersedia, sehingga pasar ini berisi contoh yang sudah ada di dalam aplikasi. Proyek yang sudah dipasang dapat dibuka dan diubah di Kelola Project seperti proyek buatan sendiri."
    + "\n" +
    ""
    + "\n" +
    "Kolaborasi Langsung"
    + "\n" +
    "Untuk mengerjakan proyek bersama teman: buka Kolaborasi di menu utama, pilih proyek, tulis catatan perubahan (apa yang diubah), lalu tekan Bagikan untuk Kolaborasi. Proyek dikirim sebagai ZIP beserta berkas CATATAN.txt di dalamnya. Teman mengerjakan lalu membalas ZIP-nya; Anda menekan Terima Balasan, membaca terlebih dahulu catatan perubahannya, lalu memilih Terapkan untuk menimpa proyek lama atau Batal apabila tidak jadi. ZIP yang tidak berisi CATATAN.txt bukan paket kolaborasi — untuk ZIP proyek biasa, gunakan Impor proyek di Kelola Project."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Penerjemah Aplikasi Otomatis")
    + "\n" +
    "Untuk menerjemahkan teks di aplikasi ke bahasa lain: buka Penerjemah Aplikasi di menu utama, pilih proyek dan bahasa target (Inggris, Arab, atau Jawa), lalu tekan Terjemahkan. Kamus sudah ada di dalam HP (tanpa internet, tanpa biaya) dan berisi kata umum sehari-hari; teks dari strings.xml, layout, dan kode dibaca lalu diterjemahkan. Hasilnya ditulis ke folder values-bahasa/strings.xml (apabila proyek memiliki strings.xml) dan laporan terjemahan-bahasa.txt di folder proyek. Kata yang belum ada di kamus dibiarkan apa adanya dan dilaporkan — sehingga Anda mengetahui kata mana yang masih harus diterjemahkan sendiri."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pembuat Game Tanpa Kode")
    + "\n" +
    "Untuk membuat game tanpa menulis kode: buka Pembuat Game di menu utama, pilih jenis game (Tebak Angka, Kuis, atau Tangkap Bintang), isi nama game, jumlah (level/soal/bintang), dan pesan menang, lalu tekan Buat Game. Game dibuat sebagai proyek sungguhan yang dapat langsung dijalankan di Kelola Project dan dibangun menjadi APK seperti proyek lainnya."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Uji Aplikasi Otomatis")
    + "\n" +
    "Untuk memeriksa proyek sebelum dibangun: di Kelola Project, tahan lama nama proyek lalu pilih Uji Otomatis. IDE memeriksa 10 hal tanpa membangun APK — berkas wajib ada (berkas data proyek, manifest, berkas utama, layout, strings), nama paket dan versi, izin yang dipakai, ikon, dan keseimbangan kurung kode. Hasilnya ditampilkan per item (LULUS/GAGAL beserta pesan) dan dibacakan oleh Jieshuo. Perbaiki terlebih dahulu semua yang GAGAL, baru tekan Bangun proyek."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("AI Pemeriksa Kode")
    + "\n" +
    "Untuk memeriksa kode proyek dari pola yang sering bermasalah: buka Periksa Kode di menu utama, pilih proyek, lalu tekan Periksa Kode. Pemeriksa berjalan di dalam HP (tanpa internet) dan mencari variabel tak terpakai, fungsi kosong, perulangan tanpa henti yang jelas, teks yang ditulis langsung di kode (sebaiknya di strings.xml), izin dan API berbahaya, penanda TODO/FIXME, dan eksekusi dinamis. Setiap temuan berisi nama berkas, nomor baris, dan saran perbaikan. Pemeriksa ini berbasis pola, bukan AI sungguhan — masalah yang tidak berpola tetap dapat lolos."
    + "\n" +
    ""
    + "\n" +
    "Mode Belajar Interaktif"
    + "\n" +
    "Untuk belajar membuat aplikasi langkah demi langkah: buka Belajar di menu utama. Terdapat 5 pelajaran: Membuat proyek pertama, Menambah tombol, Mengubah warna, Menambah suara, dan Membangun APK. Ketuk satu pelajaran untuk membaca penjelasannya dan tugas praktiknya, kerjakan tugasnya, lalu tekan Periksa tugas — IDE memeriksa sungguhan apakah tugasnya sudah dikerjakan (dengan memindai proyek dan APK di HP Anda). Pelajaran yang lulus ditandai selesai dan disimpan."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Sinkronisasi Cloud")
    + "\n" +
    "Untuk mencadangkan proyek ke cloud: buka Sinkronisasi Cloud di menu utama. Cadangan lokal menyimpan ZIP proyek ke folder Download/cloud dengan stempel waktu, dan dapat dipulihkan kapan saja dari daftar cadangan. Cadangan GitHub mengunggah ZIP proyek ke repo privat Anda (memakai token dari Pengaturan GitHub) dan dapat diunduh kembali. Ini cadangan dan pulihkan manual — sinkronisasi otomatis dua arah penuh membutuhkan server, sehingga belum tersedia."
    + "\n" +
    ""
    + "\n" +
    "Pembuat Backend Otomatis"
    + "\n" +
    "Untuk membuat backend tanpa menulis kode server: buka Pembuat Backend di menu utama, pilih jenis backend (Catatan, Daftar Tugas, atau Papan Pesan), isi nama proyek, lalu tekan Buat Backend. IDE membuat proyek HTML sungguhan dengan halaman formulir dan daftar data yang tersimpan di penyimpanan lokal HP (localStorage) — data tersimpan dan dapat dibaca kembali saat aplikasi dijalankan. Backend ini berjalan di satu HP; dua HP belum dapat berbagi data yang sama karena tidak ada server internet."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Desainer Tema Otomatis")
    + "\n" +
    "Untuk memberi tema warna pada proyek: buka Desainer Tema di menu utama, pilih proyek dan tema (Terang, Gelap, Biru, Hijau, Merah, atau Ungu). Pratinjau menunjukkan enam warna tema (primer, primer gelap, aksen, latar, teks, teks sekunder) dan dibacakan oleh Jieshuo. Tekan Terapkan Tema — IDE menulis colors.xml dan styles.xml (style app_theme) ke proyek, langsung dipakai saat proyek dijalankan atau dibangun."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pembuat Animasi")
    + "\n" +
    "Untuk memberi animasi pada proyek: buka Pembuat Animasi di menu utama, pilih proyek, jenis animasi (Fade, Geser, Putar, Pantul, atau Berdenyut), durasi (100 sampai 10000 milidetik), dan target (tombol, teks, atau gambar), lalu tekan Buat Animasi. IDE menulis berkas animasi sungguhan (res/anim/anim_<jenis>.xml untuk proyek Java/Kotlin/Compose, atau animasi.css untuk proyek HTML/Situs Web) beserta contoh kode pemakaiannya yang ditampilkan di layar."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Perekam Aksi")
    + "\n" +
    "Untuk mengulang pekerjaan secara otomatis: IDE merekam aksi yang dilakukan (buka proyek, jalankan, bangun, ekspor, cadangkan, buat proyek, simpan berkas). Buka Perekam Aksi di menu utama untuk melihat daftar aksi yang terekam beserta rencananya, lalu tekan Putar Ulang — IDE menjalankan kembali aksi yang dapat diotomatisasi (cadangkan dan buat proyek dijalankan di tempat; jalankan, bangun, dan ekspor membuka layar utamanya; buka dan simpan membuka editor). Aksi yang tidak dapat otomatis dilewati dan dilaporkan di akhir."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Keamanan Otomatis")
    + "\n" +
    "Untuk memeriksa keamanan proyek: buka Keamanan Otomatis di menu utama, pilih proyek, lalu tekan Periksa Keamanan. IDE memeriksa 8 hal secara lokal (tanpa internet): izin berbahaya (lokasi, SMS, panggilan, kamera, mikrofon, kontak), kirim data via HTTP, kirim SMS, telepon keluar, kata sandi atau kunci yang tertulis langsung di kode, WebView tanpa perlindungan, komponen yang diekspor tanpa izin, dan alamat http tanpa enkripsi. Setiap temuan diberi level AMAN, PERINGATAN, atau BAHAYA beserta saran perbaikan, dan ringkasannya dibacakan oleh Jieshuo."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("AI Offline")
    + "\n" +
    "Untuk memakai alat bantu teks dan kode tanpa internet: buka AI Offline di menu utama. Terdapat 4 alat: Perbaiki Teks (kapital, tanda baca, dan spasi ganda), Ringkas Teks (mengambil 3 kalimat yang paling penting), Buat Nama Aplikasi (dari deskripsi yang ditulis), dan Saran Nama Variabel (mencari nama variabel 1-2 huruf di proyek). Ini aturan sederhana di dalam HP, bukan model AI sungguhan. Hasil perbaikan teks dapat diterapkan ke strings.xml proyek dengan tombol Terapkan."
    + "\n" +
    ""
    + "\n" +
    "Pembuat Widget & Wallpaper"
    + "\n" +
    "Untuk membuat widget layar utama: buka Widget & Wallpaper di menu utama, pilih proyek, isi teks widget, lalu tekan Buat Widget. IDE menulis WidgetSaya.java, layout widget, info widget, dan menambahkan receiver ke AndroidManifest.xml proyek — bangun proyek, lalu pasang widget dari layar utama HP. Untuk wallpaper: isi tulisan dan warna latar serta warna tulisan, buat pratinjau, lalu simpan sebagai PNG ke folder Download atau langsung pasang sebagai wallpaper HP."
    + "\n" +
    ""
    + "\n" +
    "Integrasi Media Sosial"
    + "\n" +
    "Untuk menambahkan tombol berbagi ke aplikasi: buka Media Sosial di menu utama, pilih proyek, lalu tambah berbagi ke WhatsApp, Facebook, atau Telegram (atau ketiganya sekaligus). IDE menulis kode berbagi sungguhan ke berkas utama proyek (MainActivity atau index.html). Tombol Uji Berbagi membuka chooser berbagi dari IDE dengan teks contoh, agar alurnya dapat dirasakan sebelum membangun aplikasi."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Aplikasi dari Gambar")
    + "\n" +
    "Untuk membuat aplikasi galeri dari gambar: buka Aplikasi dari Gambar di menu utama, pilih gambar dari galeri. IDE menganalisis ukuran dan warna dominan gambar, lalu membuat proyek aplikasi galeri sungguhan: gambar disalin ke assets proyek, warna tema diambil dari warna dominan gambar, dan nama aplikasi diambil dari nama berkas gambar. Buka Kelola Project untuk menjalankan atau membangun aplikasi galeri tersebut."
    + "\n" +
    ""
    + "\n" +
    "Terminal Interaktif (Parser VT)"
    + "\n" +
    "Untuk memakai program interaktif di Terminal: buka Terminal di menu utama, lalu jalankan nano, vim, top, atau htop. Terminal kini memakai parser VT (layar grid 24 baris x 80 kolom) sehingga tampilan program tersebut benar — kursor bergerak, baris terhapus, dan layar berganti seperti di terminal sungguhan. Terdapat tombol khusus Tab, Panah Atas, Panah Bawah, dan Esc untuk mengendalikan program interaktif. Keluaran tetap berupa teks yang dapat dibaca Jieshuo; tekan Bacakan layar untuk mendengar baris terakhir."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Terminal Exec (Biner Aplikasi)")
    + "\n" +
    "Untuk menjalankan biner di folder aplikasi (mis. aapt2, ECJ, D8) dari Terminal: ketik perintahnya seperti biasa. IDE memakai libkautsarexec.so sehingga biner di folder aplikasi dapat dieksekusi di Android 10 ke atas. Tekan Uji Exec di Terminal untuk memeriksa apakah jalur exec biner berjalan di HP ini — hasilnya ditampilkan apa adanya. Apabila exec diblokir sistem, perintah sistem dan skrip shell tetap berjalan; jalankan biner melalui menu Bangun proyek sebagai gantinya."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Terminal Tetap Hidup (KeepAlive)")
    + "\n" +
    "Untuk membiarkan shell tetap berjalan saat pindah layar: buka Terminal, jalankan perintah, lalu tekan Kembali atau pindah ke layar lain — shell TIDAK mati, dan notifikasi Terminal tetap tampil agar Anda dapat kembali dengan satu ketukan. Perintah yang sudah dijalankan tersimpan di riwayat (tombol Riwayat, atau panah atas/bawah di kolom perintah) dan perintah yang sering dipakai dapat disimpan sebagai favorit (tombol Favorit). Tekan Tutup shell apabila sudah selesai, agar shell benar-benar dimatikan. Terdapat juga tombol Bangun di Terminal di menu proyek dan di menu editor: Terminal dibuka di folder proyek dengan perintah bangun yang sudah diisi."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pasang Alat Terminal (Uji Coba)")
    + "\n" +
    "Fitur yang masih dalam uji coba: di Terminal ada tombol Pasang Alat Terminal untuk memasang lingkungan Termux dari cermin di repositori alat-termux milik pengembang (github.com/syudat/alat-termux), sebagai jalur lain selain tombol Pasang Termux yang mengambil dari rilis resmi Termux. Isi cerminnya sama persis dengan rilis resmi dan diperiksa dengan SHA-256 saat pemasangan. Sesudah lingkungan terpasang, perintah pkg update dan pkg install bisa dipakai, dan alat tambahan dari repositori ikut dipasang ke terminal: info-terminal untuk melihat info lingkungan, bersih-terminal untuk membersihkan cache, dan cadangkan-proyek untuk mengarsipkan folder proyek menjadi satu berkas tar.gz bertanggal. Sesudah itu aplikasi memasang otomatis paket Linux dasar (git, python, curl, wget, nano, openssh, zip, unzip) dan alat bangun APK beserta tanda tangan (openjdk-21, kotlin, gradle, ecj, aapt2, d8, apksigner, clang, cmake, ninja, make) dari repositori resmi Termux, jadi terminal langsung siap dipakai membangun aplikasi. Unduhan paket besar (ratusan MB) dan bisa lama. Selama pemasangan berjalan, kemajuan dan keluaran perintah tampil di layar utama Terminal, dan hasil akhirnya dibacakan oleh pembaca layar. Bila pemasangan paket belum tuntas, tekan Pasang Alat Terminal lagi untuk melanjutkannya."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Mode Suara Penuh")
    + "\n" +
    "Untuk mengendalikan IDE sepenuhnya dengan suara: buka Mode Suara di menu utama, lalu tekan Mulai Mendengarkan. IDE mendengarkan terus-menerus — tidak perlu menekan tombol tiap perintah — menjalankan perintah dan menjawab dengan suara. Terdapat 26 perintah: buat, bangun, buka, jalankan, daftar, cari, hapus, uji, periksa, ekspor, cadangkan, bagikan, buat ikon, terjemahkan, bangun di terminal, baca layar, buka semua layar (termasuk keamanan, AI offline, widget, media sosial, game, belajar, cloud, backend, tema, animasi, perekam), kembali, tutup, dan bantuan. Apabila mode ditinggal aktif, mendengarkan dilanjutkan otomatis saat layar ini dibuka lagi. Tekan Berhenti Mendengarkan untuk mematikan mode ini. Apabila HP tidak memiliki layanan pengenal suara, perintah tetap dapat ditulis di kotak teks."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Penerjemah Kode")
    + "\n" +
    "Untuk menerjemahkan kode antar bahasa tanpa internet: buka Penerjemah Kode di menu utama, pilih proyek, pilih berkas sumber (.java/.kt), lalu pilih arah: Java ke Kotlin. Tekan Terjemahkan — hasilnya ditampilkan beserta laporan baris yang tidak dapat diterjemahkan (baris tersebut disimpan apa adanya dan ditandai). Tekan Simpan hasil untuk menulis berkas BARU (nama-terjemahan.java/.kt) di folder yang sama; berkas asli tidak pernah ditimpa. Ini penerjemah aturan pola, bukan kompiler — selalu periksa hasil sebelum dipakai."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Git di Dalam Aplikasi")
    + "\n" +
    "Untuk memakai Git di dalam IDE tanpa Termux: buka Git di Dalam Aplikasi di menu utama, pilih proyek, lalu tekan Buat Repo Git untuk membuat repo lokal (.git) di proyek tersebut. Tekan Periksa Status untuk melihat berkas baru, berubah, atau dihapus dibanding commit terakhir; isi pesan commit lalu tekan Commit untuk menyimpan snapshot proyek; tekan Lihat Log Commit untuk melihat riwayat commit. Push ke GitHub dan Pull dari GitHub memakai snapshot melalui token GitHub (repo pembuat-aplikasi-build) — bukan protokol git. Clone, Push (git sistem), dan Pull (git sistem) hanya berjalan apabila biner git terpasang di HP (biasanya tidak ada). Ini bukan git penuh: tidak ada merge, branch, atau diff — tiap commit menyimpan snapshot ZIP utuh proyek."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pembuat Iklan")
    + "\n" +
    "Untuk menambahkan iklan ke aplikasi hasil: buka Pembuat Iklan di menu utama, pilih proyek, penyedia (AdMob, Unity Ads, atau AppLovin), dan jenis iklan (Banner, Interstitial, atau Rewarded). Isi ID unit iklan apabila sudah punya — kosong berarti memakai ID uji (AdMob memakai ID uji resmi Google; Unity memakai Game ID contoh; AppLovin memakai tempat kosong). Tekan Buat Kode Iklan untuk melihat kode Java, Kotlin, dan panduan; tekan Simpan ke Proyek untuk menulis semuanya ke berkas iklan-<penyedia>-<jenis>.txt di proyek. Ini template kode + panduan, bukan SDK iklan — SDK tidak disertakan di IDE; iklan sungguhan baru tampil setelah Anda membuat akun penyedia, membuat unit iklan, menempel ID unit sendiri, dan menambah dependensi SDK di build.gradle proyek."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Mode Anak")
    + "\n" +
    "Untuk memakai IDE bersama anak: buka Mode Anak di menu utama. Layarnya disederhanakan dengan tombol besar dan hanya 4 aksi aman: Buat Proyek (dari template aman: Kalkulator, Tebak Angka, Kuis, Tangkap Bintang), Buka Proyek, Jalankan, dan Belajar. Tidak ada tombol hapus, ekspor, cadangan, atau pengaturan token di layar ini. Saat pertama masuk, buat PIN 4 angka — PIN tersebut dipakai orang tua untuk keluar dari Mode Anak (tombol Keluar dari Mode Anak atau tombol Kembali). PIN ini pengaman ringan untuk anak kecil, bukan keamanan sungguhan."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pembuat Aplikasi Wear OS & TV")
    + "\n" +
    "Untuk membuat aplikasi jam (Wear OS) atau TV (Android TV): buka Wear OS & TV di menu utama, pilih target (Wear OS, Android TV, atau keduanya). Isi nama proyek lalu tekan Buat Proyek Baru Wear/TV — IDE membuat proyek Java lalu menyesuaikannya — atau pilih proyek yang sudah ada lalu tekan Terapkan ke Proyek Ini. Penyesuaian: uses-feature di manifest, kategori LEANBACK_LAUNCHER + banner untuk TV, dan layout activity_main.xml yang disesuaikan (Wear: layar kecil; TV: layar besar ramah D-pad). Penjelasan lengkap ditulis ke wear-tv.txt di proyek. Ini template proyek — build + uji tetap membutuhkan perangkat/emulator Wear OS atau Android TV; banner TV (banner.png 320x180) tidak dibuat otomatis, harus ditambahkan sendiri (misalnya melalui Pembuat Ikon)."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Peta Proyek")
    + "\n" +
    "Untuk melihat struktur sebuah proyek: buka Peta Proyek di menu utama, pilih proyek, lalu tekan Tampilkan Peta — IDE menampilkan daftar folder dan berkas beserta ukurannya, total ukuran, ukuran per jenis berkas, dan berkas penting (berkas utama, manifest, layout, strings, ikon, halaman web). Tekan Simpan peta-proyek.txt untuk menulis peta yang sama ke akar proyek. Folder bin/ (hasil build) dan .git/ (repo lokal) tidak ikut dipetakan karena isinya dapat dibuat ulang."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Kalkulator Ukuran APK")
    + "\n" +
    "Untuk memperkirakan ukuran APK hasil build sebelum membangun: buka Kalkulator Ukuran APK di menu utama, pilih proyek, lalu tekan Hitung Perkiraan. IDE menampilkan rincian per kategori (kode/DEX, resource, aset, library native, manifest + resources.arsc, overhead), total perkiraan, 5 berkas terbesar, dan saran mengecilkan (misalnya gambar besar diperkecil resolusinya, suara besar dipotong durasinya). Ini PERKIRAAN, bukan ukuran pasti — ukuran sungguhan dapat berbeda karena kompresi aapt2, penghapusan kode tak terpakai (R8), dan resource hasil build."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Penganalisis APK")
    + "\n" +
    "Untuk membaca isi APK yang sudah jadi (seperti APK Analyzer di Android Studio): buka Penganalisis APK di menu utama, pilih proyek, lalu tekan Analisis APK Hasil Build. IDE membaca APK terbaru di folder bin proyek itu dan melaporkan nama paket, versi, SDK minimum dan target, daftar izin yang dipakai, jumlah berkas dex, rincian ukuran per bagian (kode program, resource, aset, pustaka native, tanda tangan), dan 10 entri terbesar. Berguna untuk tahu bagian mana yang membuat APK besar dan izin apa saja yang dipakai aplikasi hasil."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pratinjau Ukuran Layar")
    + "\n" +
    "Untuk memeriksa tampilan proyek pada banyak ukuran layar sekaligus (seperti di Android Studio): buka Pratinjau Ukuran Layar di menu utama, pilih proyek, lalu tekan Periksa Ukuran Layar. IDE membaca semua layout proyek dan melaporkan elemen yang lebarnya ditulis tetap (misalnya 400dp) dan melebihi lebar layar 320, 360, 411, atau 480 dp, karena di HP berlayar kecil elemen seperti itu bisa tertutup atau meluber. Elemen dengan lebar match_parent atau wrap_content menyesuaikan layar secara otomatis dan tidak perlu diperiksa."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pengelola Sumber Daya")
    + "\n" +
    "Untuk melihat dan mengatur semua sumber daya proyek dalam satu tempat (seperti Resource Manager di Android Studio): buka Pengelola Sumber Daya di menu utama, pilih proyek, lalu tekan Lihat Sumber Daya. IDE melaporkan jumlah dan ukuran berkas per kategori (drawable, layout, values, mipmap, dan lainnya), isi strings.xml, colors.xml, dan styles.xml. Dari layar yang sama, string baru bisa ditambahkan ke strings.xml dan warna baru ke colors.xml dengan mengisi nama dan nilainya — nama diperiksa dan duplikat ditolak, jadi berkas XML tidak rusak."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pemantau Kinerja Ringan")
    + "\n" +
    "Untuk memantau pemakaian RAM dan prosesor aplikasi yang sedang berjalan (seperti Profiler di Android Studio, versi ringan): buka Pemantau Kinerja Ringan di menu utama, jalankan dulu aplikasi yang mau diperiksa (misalnya aplikasi hasil build yang sedang dicoba), isi nama paketnya — atau pilih proyek lalu tekan Isi Nama Paket dari Proyek — kemudian tekan Mulai Pantau. Setiap 2 detik IDE mencatat RAM (PSS) dan CPU aplikasi itu beserta RAM HP yang bebas; tekan Berhenti untuk melihat ringkasan rata-rata dan puncaknya. Tanpa izin tambahan. Di Android versi baru, akses baca prosesor aplikasi lain dibatasi sistem, jadi bila CPU tidak terbaca, laporan menyebutkannya apa adanya dan RAM tetap terpantau."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Tanda Tangan Bawaan per Proyek dan Anti Tamper")
    + "\n" +
    "Sejak versi 5.7, proyek yang tidak memakai tanda tangan pribadi ditandatangani dengan kunci bawaan KHUSUS proyek itu sendiri (dibuat sekali di folder tanda-tangan-bawaan di dalam proyek, lalu dipakai seterusnya), jadi tanda tangan bawaan berbeda-beda antarproyek — tidak lagi satu kunci bersama untuk semua aplikasi. Jangan hapus folder tanda-tangan-bawaan: tanpa kunci itu, APK baru tidak bisa menimpa APK lama proyek yang sama. Setelan tiap proyek (arsitektur terpisah, tanda tangan pribadi, anti tamper, keystore terpilih, SDK) kini tersimpan di berkas settings.gradle di dalam proyek dan terbaca lagi setiap proyek dibangun. Di Pengaturan kategori Tanda Tangan ada centang Anti tamper aplikasi hasil build: bila aktif, aplikasi hasil build di perangkat memeriksa tanda tangan dan isi dex-nya setiap dibuka; bila ditandatangani ulang atau diubah orang lain, aplikasi menolak berjalan. Debugger dan alat injeksi seperti Frida/Xposed juga dideteksi, dan jendela aplikasi dikunci dari rekaman layar."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pembuat Video Demo")
    + "\n" +
    "Untuk membuat video demo aplikasi hasil: buka Pembuat Video Demo di menu utama, pilih proyek, isi judul video, lalu tekan Buat Skrip Video Demo — IDE menulis video-demo.txt berisi 5 adegan baku (pembuka, tampilan utama, fitur 1, fitur 2, penutup) beserta teks narasi dan durasi tiap adegan. Tekan Buat Slideshow HTML untuk membuat video-demo.html dari gambar-gambar proyek (ikon + tangkapan layar apabila ada) yang berganti otomatis tiap 5 detik. Ini BUKAN video .mp4 — untuk video sungguhan, buka video-demo.html di browser HP lalu rekam layar, atau baca narasi di video-demo.txt sambil merekam aplikasi yang sedang berjalan."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Tampilan Baru ala Android Studio")
    + "\n" +
    "Tampilan IDE kini dirapikan menyerupai Android Studio (hanya tampilannya saja — semua tombol, menu, dan alur tetap sama seperti sebelumnya): latar abu terang, judul layar tebal berwarna hitam, tombol biru dengan teks putih, dan kotak isi putih berbingkai tipis. Menu utama dikelompokkan secara visual (Fitur Pembuat, Cloud Tema & Lainnya, Suara Kode & Fitur Baru, Alat) tanpa mengubah urutan tombol. Terminal tetap memakai panel gelap seperti jendela Terminal di Android Studio, dan editor kode tetap berlatar putih dengan teks hitam. Semua teks tetap terbaca jelas oleh Jieshuo."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Pengaturan")
    + "\n" +
    "Untuk mengatur aplikasi: buka menu Opsi di layar utama lalu tekan Pengaturan. Di dalamnya terdapat 12 kategori: Pengaturan GitHub (keamanan, tanda tangan pribadi, history build, dan token GitHub), Pengaturan Bangun APK Perangkat (simpan kata sandi keystore dan history build di perangkat), Tema (warna aplikasi), Tanda Tangan (buat keystore untuk tanda tangan pribadi dan centang anti tamper aplikasi hasil build), Folder Proyek (lokasi folder proyek), Bahasa dan Suara (bahasa antarmuka dan kecepatan suara Mode Suara), Ukuran Teks dan Tampilan (ukuran font editor), Build (SDK target bawaan dan pisah arsitektur bawaan untuk layar Buat Proyek), Notifikasi (bunyi saat build selesai), Cadangan Otomatis (jadwal cadangan proyek), Profil Pengembang (nama, nomor WhatsApp, dan rekening yang tampil di layar Donasi), serta Privasi dan Data (reset pengaturan dan hapus data aplikasi). Setiap kategori memiliki tombol Kembali ke Sebelumnya untuk kembali ke menu kategori. Pengaturan yang baru disimpan saja (belum diterapkan) ditulis keterangannya di layar dengan jujur."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Donasi ke Pengembang")
    + "\n" +
    "Untuk berdonasi atau menyapa pengembang: tekan tombol Donasi ke Pengembang di layar utama, di samping tombol Tentang Alat. Di layar donasi terdapat tombol Salin Rekening BRI — tekan untuk menyalin nomor rekening pengembang, lalu tempel di aplikasi bank Anda. Terdapat juga bagian Sapa Pengembang di WhatsApp: isi nama dan kota asal, lalu tekan Kirim di WhatsApp — WhatsApp terbuka dengan pesan sapaan sudah terisi otomatis, tinggal tekan Kirim di WhatsApp lalu kembali ke aplikasi. WhatsApp tidak mengizinkan aplikasi lain mengirim pesan secara otomatis, sehingga pengiriman tetap dilakukan sendiri di WhatsApp."
    + "\n" +
    ""
    + "\n" +
    "Perintah Favorit Terminal"
    + "\n" +
    "Untuk menyimpan perintah yang sering dipakai di Terminal: buka Terminal lalu tekan Favorit. Ketuk Tambah favorit baru, isi nama (misalnya Lihat berkas) dan perintahnya (misalnya ls -la), lalu tekan Simpan - favorit tersimpan permanen di HP. Ketuk nama favorit untuk langsung menjalankannya di terminal; ketuk Hapus favorit lalu pilih satu untuk menghapusnya (dengan konfirmasi). Sudah terdapat 3 favorit bawaan: Lihat berkas (ls -la), Cek ruang (df -h), dan Bersihkan layar (clear) - semuanya boleh dihapus. Perintah kosong tidak dapat disimpan atau dijalankan; Terminal memberi tahu melalui pesan dan suara apabila hal itu terjadi."
    + "\n" +
    ""
    + "\n" +
    TeksUI.t("Analitik & Kualitas")
    + "\n" +
    "Untuk melihat ringkasan penggunaan aplikasi dan laporan crash: tekan tombol Analitik & Kualitas di layar utama, di samping tombol Donasi ke Pengembang. Layar ini menampilkan berapa kali aplikasi dibuka, layar yang paling sering dibuka, jumlah crash, dan daftar crash terakhir beserta saran perbaikan singkat. Data ini hanya tersimpan di HP Anda dan tidak dikirim ke mana pun. Terdapat tombol Hapus Data Analitik dengan dialog konfirmasi untuk menghapus semua data tersebut. Layar terakhir hanya tercatat untuk layar utama dan layar ini."
    + "\n" +
    ""
    + "\n" +
    "Penutup"
    + "\n" +
    "Terima kasih sudah memakai Create Aplikasi. Semoga alat ini membantu Anda dan siapa saja yang ingin belajar membuat aplikasi Android dari ponsel. Apabila ada kendala, perbaiki langkah demi langkah dari panduan ini, dan jangan ragu untuk mencoba lagi."
    + "\n" +
    ""
    + "\n" +
    "Developed by Suara Tekno Indonesia";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kodeBahasa = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;
        setTitle(t("Tentang Alat"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = new LinearLayout(this);
        GayaStudio.latar(r);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        sv.addView(r);
        setContentView(sv);

        heading(r, t("Tentang Alat"), 20);

        // Deskripsi profesional mengikuti bahasa terpilih (perintah al
        // kautsar 2026-11-23): judul layar + paragraf pembuka Tentang
        // Alat tampil dalam bahasa efektif aplikasi (50 bahasa).
        // Panduan lengkap di bawahnya juga mengikuti bahasa terpilih:
        // terjemahannya dimuat dari aset panduan/panduan-<kode>.txt
        // (perintah al kautsar 2026-11-23 sore: "bagian tentang harus
        // diterjemahkan juga, terjemahkan satu per satu"). Bila berkas
        // terjemahan tidak ada, panduan bahasa Indonesia dipakai dan
        // catatan kecil menjelaskannya dalam bahasa terpilih.
        String panduanTerjemahan = null;
        try {
            kodeBahasa = Bahasa.kodeEfektif(
                    PengaturanActivity.baca(this).getString(
                            PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                    java.util.Locale.getDefault().getLanguage());
            TeksUI.kodeAktif = kodeBahasa;
            if (!"id".equals(kodeBahasa)) {
                panduanTerjemahan = muatPanduanAset(kodeBahasa);
            }
            TextView tvJudulBahasa = new TextView(this);
            tvJudulBahasa.setText(TentangBahasa.judul(kodeBahasa));
            GayaStudio.judul(tvJudulBahasa, 17);
            tvJudulBahasa.setPadding(dp(16), dp(8), dp(16), 0);
            r.addView(tvJudulBahasa);
            TextView tvDeskripsi = new TextView(this);
            tvDeskripsi.setText(TentangBahasa.deskripsi(kodeBahasa));
            GayaStudio.label(tvDeskripsi, 15);
            tvDeskripsi.setTextIsSelectable(true);
            tvDeskripsi.setPadding(dp(16), dp(4), dp(16), 0);
            r.addView(tvDeskripsi);
            if (panduanTerjemahan == null) {
                String catatan = TentangBahasa.catatanPanduan(kodeBahasa);
                if (catatan != null && catatan.length() > 0) {
                    TextView tvCatatan = new TextView(this);
                    tvCatatan.setText(catatan);
                    GayaStudio.labelKecil(tvCatatan, 12);
                    tvCatatan.setPadding(dp(16), dp(4), dp(16), 0);
                    r.addView(tvCatatan);
                }
            }
        } catch (Throwable ignored) {
        }

        TextView tv = new TextView(this);
        tv.setText(panduanTerjemahan != null ? panduanTerjemahan : TENTANG);
        GayaStudio.label(tv, 15);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        r.addView(tv);

        Button tutup = new Button(this);
        GayaStudio.tombol(tutup);
        tutup.setText(t("Tutup"));
        tutup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        r.addView(tutup, lp);

        announce(t("Tentang Alat"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sama seperti onResume() di Lua: lanjutkan pemasangan APK yang
        // tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
    }

    private void heading(LinearLayout parent, String text, float size) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.judul(tv, size);
        tv.setPadding(0, dp(8), 0, dp(4));
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                tv.setAccessibilityHeading(true);
            } catch (Throwable ignored) {
            }
        }
        parent.addView(tv);
    }

    /**
     * Muat terjemahan panduan lengkap dari aset
     * panduan/panduan-<kode>.txt (UTF-8). Mengembalikan null bila
     * berkas tidak ada atau gagal dibaca, supaya pemanggil memakai
     * panduan bahasa Indonesia bawaan.
     */
    private String muatPanduanAset(String kode) {
        java.io.InputStream in = null;
        try {
            in = getAssets().open("panduan/panduan-" + kode + ".txt");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
            String teks = new String(bos.toByteArray(), "UTF-8");
            return teks.trim().length() > 1000 ? teks : null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private int dp(float v) {
        return GayaStudio.dp(this, v);
    }

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
