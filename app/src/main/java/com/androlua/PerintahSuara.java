package com.androlua;

import java.util.Locale;

/**
 * Parser perintah suara untuk "Bangun dengan Suara" (Tahap 3D),
 * diperluas untuk Mode Suara Penuh (Tahap 3H): semua menu utama,
 * semua aksi proyek, navigasi layar, dan baca layar.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji ulang di JRE.
 * VoiceActivity dan ModeSuaraActivity memakai RecognizerIntent /
 * SpeechRecognizer untuk mengubah ucapan menjadi teks, lalu
 * PerintahSuara.parse() mengubah teks itu menjadi aksi.
 *
 * Perintah yang dikenali (tanpa memperhatikan huruf besar/kecil, spasi
 * ganda dan tanda baca di ujung diabaikan):
 *   buat proyek kalkulator | buat proyek catatan | buat proyek <nama>
 *   buat proyek                      -> buka form Buat Proyek
 *   bangun proyek <nama> | bangun <nama>
 *   bangun di terminal <nama>        -> buka Terminal + perintah bangun
 *   buka proyek <nama> | buka <nama>
 *   jalankan proyek <nama> | jalankan <nama>
 *   daftar proyek | lihat proyek | proyek saya
 *   cari proyek <nama> | cari <nama>
 *   hapus proyek <nama> | hapus <nama>
 *   uji proyek <nama> | uji <nama>
 *   periksa proyek <nama> | periksa <nama>
 *   ekspor proyek <nama> | ekspor <nama>
 *   cadangkan proyek <nama> | cadangkan <nama>
 *   bagikan proyek <nama> | bagikan <nama>
 *   buat ikon | buat ikon proyek <nama>
 *   terjemahkan proyek <nama> | terjemahkan <nama>
 *   baca layar | bacakan layar | baca hasil
 *   buka ikon | buka terminal | buka pasar | buka ai | buka kolaborasi
 *   buka keamanan | buka ai offline | buka widget | buka media sosial
 *   buka dari gambar | buka game | buka uji | buka periksa
 *   buka belajar | buka cloud | buka backend | buka tema
 *   buka animasi | buka perekam | buka mode suara | buka penerjemah kode
 *   kembali | menu utama | beranda
 *   tutup | keluar
 *   bantuan | perintah apa saja
 *
 * Kata kunci template sama dengan TemplateProyek (kalkulator, catatan,
 * tugas, kalkulator web, profil, penghitung) ditambah sinonim yang biasa
 * diucapkan. Frasa yang lebih panjang diperiksa dulu ("kalkulator web"
 * sebelum "web") supaya tidak salah cocok.
 */
public class PerintahSuara {

    /** Satu perintah hasil parsing. */
    public static class Perintah {
        /** BUAT, BUAT_FORM, BANGUN, BANGUN_TERMINAL, BUKA, JALANKAN, DAFTAR,
         *  CARI, HAPUS, UJI, PERIKSA, EKSPOR, CADANGKAN, BAGIKAN, IKON,
         *  TERJEMAH, BACA, LAYAR, KEMBALI, TUTUP, BANTUAN, TIDAK_KENAL */
        public final String aksi;
        /** Nama proyek (BUAT/BANGUN/BUKA/JALANKAN) atau id layar (LAYAR). */
        public final String argumen;
        /** Id template bila perintah BUAT memakai kata kunci template. */
        public final String templateId;
        /** Teks asli ucapan (untuk pesan galat / pengumuman). */
        public final String ucapan;

        public Perintah(String aksi, String argumen, String templateId, String ucapan) {
            this.aksi = aksi;
            this.argumen = argumen;
            this.templateId = templateId;
            this.ucapan = ucapan;
        }
    }

    /** Daftar perintah untuk layar bantuan (ditampilkan + dibacakan). */
    public static final String[] DAFTAR_PERINTAH = {
        "Buat proyek kalkulator",
        "Buat proyek catatan",
        "Buat proyek tugas",
        "Buat proyek <nama aplikasi>",
        "Bangun proyek <nama>",
        "Bangun di terminal <nama>",
        "Buka proyek <nama>",
        "Jalankan proyek <nama>",
        "Daftar proyek",
        "Cari proyek <nama>",
        "Hapus proyek <nama>",
        "Uji proyek <nama>",
        "Periksa proyek <nama>",
        "Ekspor proyek <nama>",
        "Cadangkan proyek <nama>",
        "Bagikan proyek <nama>",
        "Buat ikon proyek <nama>",
        "Terjemahkan proyek <nama>",
        "Baca layar",
        "Buka ikon, terminal, pasar, AI, atau kolaborasi",
        "Buka keamanan, AI offline, widget, media sosial, atau dari gambar",
        "Buka game, uji, periksa, belajar, cloud, backend, tema, animasi, atau perekam",
        "Buka mode suara atau penerjemah kode",
        "Kembali",
        "Tutup",
        "Bantuan",
    };

    /** Sinonim kata kunci template: {kata kunci, id template}. */
    private static final String[][] SINONIM_TEMPLATE = {
        {"kalkulator web", "kalkulator-web"},
        {"kalkulator di web", "kalkulator-web"},
        {"hitung di web", "kalkulator-web"},
        {"kalkulator", "kalkulator"},
        {"hitung", "kalkulator"},
        {"hitungan", "kalkulator"},
        {"tambah kurang", "kalkulator"},
        {"catatan", "catatan"},
        {"note", "catatan"},
        {"memo", "catatan"},
        {"jurnal", "catatan"},
        {"diari", "catatan"},
        {"daftar tugas", "tugas"},
        {"tugas", "tugas"},
        {"todo", "tugas"},
        {"to do", "tugas"},
        {"jadwal", "tugas"},
        {"pengingat", "tugas"},
        {"daftar belanja", "tugas"},
        {"belanja", "tugas"},
        {"penghitung", "penghitung"},
        {"counter", "penghitung"},
        {"hitung mundur", "penghitung"},
        {"profil", "profil"},
        {"portofolio", "profil"},
        {"biodata", "profil"},
        {"tentang saya", "profil"},
        {"situs", "profil"},
        {"website", "profil"},
        {"halaman web", "profil"},
        {"web", "profil"},
    };

    /** Layar yang bisa dibuka lewat suara: {kata kunci, id layar}.
     *  Frasa panjang dulu ("mode suara" sebelum "suara") supaya
     *  cariLayar tidak salah cocok. */
    private static final String[][] LAYAR = {
        {"pembuat ikon", "ikon"},
        {"ikon", "ikon"},
        {"terminal", "terminal"},
        {"pasar proyek", "pasar"},
        {"pasar", "pasar"},
        {"kecerdasan buatan", "ai"},
        {"pembuat aplikasi", "ai"},
        {"ai offline", "ai-offline"},
        {"ai", "ai"},
        {"kerja sama", "kolaborasi"},
        {"kolaborasi", "kolaborasi"},
        {"ekstensi", "ekstensi"},
        {"tanda tangan", "keystore"},
        {"keystore", "keystore"},
        {"tentang", "tentang"},
        {"kelola proyek", "kelola"},
        {"daftar proyek", "kelola"},
        // Tahap 3E/3F/3G.
        {"keamanan otomatis", "keamanan"},
        {"keamanan", "keamanan"},
        {"pembuat widget", "widget"},
        {"widget", "widget"},
        {"wallpaper", "widget"},
        {"media sosial", "medsos"},
        {"sosial", "medsos"},
        {"aplikasi dari gambar", "dari-gambar"},
        {"dari gambar", "dari-gambar"},
        {"penerjemah kode", "penerjemah-kode"},
        {"penerjemah", "terjemah"},
        {"terjemah", "terjemah"},
        {"game tanpa kode", "game"},
        {"game", "game"},
        {"uji otomatis", "uji"},
        {"uji", "uji"},
        {"ai pemeriksa", "periksa"},
        {"pemeriksa kode", "periksa"},
        {"periksa", "periksa"},
        {"mode belajar", "belajar"},
        {"belajar", "belajar"},
        {"sinkronisasi cloud", "cloud"},
        {"sinkronisasi", "cloud"},
        {"cloud", "cloud"},
        {"backend", "backend"},
        {"tema", "tema"},
        {"pembuat animasi", "animasi"},
        {"animasi", "animasi"},
        {"perekam aksi", "perekam"},
        {"perekam", "perekam"},
        // Tahap 3H.
        {"mode suara", "mode-suara"},
        {"suara", "mode-suara"},
    };

    /**
     * Mengubah ucapan menjadi perintah. Tidak pernah mengembalikan null;
     * ucapan yang tidak dikenali menghasilkan aksi TIDAK_KENAL.
     */
    public static Perintah parse(String ucapan) {
        String asli = ucapan == null ? "" : ucapan;
        String s = normalisasi(asli);
        if (s.isEmpty()) {
            return new Perintah("TIDAK_KENAL", "", null, asli);
        }

        // Bantuan dulu: "bantuan", "perintah apa saja", "apa yang bisa ...".
        if (s.equals("bantuan") || s.startsWith("bantuan")
                || s.indexOf("perintah apa") >= 0
                || s.indexOf("apa yang bisa") >= 0
                || s.indexOf("apa saja yang bisa") >= 0) {
            return new Perintah("BANTUAN", "", null, asli);
        }
        // Kembali / tutup.
        if (s.equals("kembali") || s.equals("menu utama") || s.equals("beranda")
                || s.equals("pulang") || s.equals("balik")) {
            return new Perintah("KEMBALI", "", null, asli);
        }
        if (s.equals("tutup") || s.equals("keluar") || s.equals("tutup aplikasi")
                || s.equals("keluar aplikasi")) {
            return new Perintah("TUTUP", "", null, asli);
        }
        // Daftar proyek.
        if (s.equals("daftar proyek") || s.equals("lihat proyek")
                || s.equals("proyek saya") || s.equals("proyekku")
                || s.equals("tampilkan proyek")) {
            return new Perintah("DAFTAR", "", null, asli);
        }
        // Baca layar / baca hasil (Mode Suara Penuh, Tahap 3H).
        if (s.equals("baca layar") || s.equals("bacakan layar")
                || s.equals("baca hasil") || s.equals("bacakan hasil")
                || s.equals("bacakan") || s.equals("baca")) {
            return new Perintah("BACA", "", null, asli);
        }
        // Bangun di Terminal (sebelum "bangun " umum, karena awalan sama).
        if (s.startsWith("bangun di terminal") || s.startsWith("bangun terminal")) {
            String nama = s.startsWith("bangun di terminal")
                    ? s.substring("bangun di terminal".length()).trim()
                    : s.substring("bangun terminal".length()).trim();
            if (nama.startsWith("proyek ")) nama = nama.substring(7).trim();
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("BANGUN_TERMINAL", kapitalNama(nama), null, asli);
        }
        // Buat ikon (sebelum "buat proyek", karena sama-sama "buat").
        if (s.startsWith("buat ikon")) {
            String nama = s.substring("buat ikon".length()).trim();
            if (nama.startsWith("proyek ")) nama = nama.substring(7).trim();
            return new Perintah("IKON", kapitalNama(nama), null, asli);
        }
        // Terjemahkan proyek.
        if (s.startsWith("terjemahkan")) {
            String nama = s.substring("terjemahkan".length()).trim();
            if (nama.startsWith("proyek ")) nama = nama.substring(7).trim();
            return new Perintah("TERJEMAH", kapitalNama(nama), null, asli);
        }
        // Aksi proyek: cari / hapus / uji / periksa / ekspor / cadangkan / bagikan.
        if (s.startsWith("cari proyek") || s.startsWith("cari ")) {
            String nama = ambilNama(s, "cari proyek", "cari ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("CARI", nama, null, asli);
        }
        if (s.startsWith("hapus proyek") || s.startsWith("hapus ")) {
            String nama = ambilNama(s, "hapus proyek", "hapus ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("HAPUS", nama, null, asli);
        }
        if (s.startsWith("uji proyek") || s.startsWith("uji ")) {
            String nama = ambilNama(s, "uji proyek", "uji ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("UJI", nama, null, asli);
        }
        if (s.startsWith("periksa proyek") || s.startsWith("periksa ")) {
            String nama = ambilNama(s, "periksa proyek", "periksa ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("PERIKSA", nama, null, asli);
        }
        if (s.startsWith("ekspor proyek") || s.startsWith("ekspor ")) {
            String nama = ambilNama(s, "ekspor proyek", "ekspor ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("EKSPOR", nama, null, asli);
        }
        if (s.startsWith("cadangkan proyek") || s.startsWith("cadangkan ")) {
            String nama = ambilNama(s, "cadangkan proyek", "cadangkan ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("CADANGKAN", nama, null, asli);
        }
        if (s.startsWith("bagikan proyek") || s.startsWith("bagikan ")) {
            String nama = ambilNama(s, "bagikan proyek", "bagikan ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("BAGIKAN", nama, null, asli);
        }
        // Buat proyek.
        if (s.startsWith("buat proyek") || s.startsWith("bikin proyek")
                || s.startsWith("buat aplikasi") || s.startsWith("bikin aplikasi")) {
            String sisa = s.substring(s.indexOf("proyek") + 6).trim();
            if (s.startsWith("buat aplikasi") || s.startsWith("bikin aplikasi")) {
                sisa = s.substring(s.indexOf("aplikasi") + 8).trim();
            }
            if (sisa.isEmpty()) {
                return new Perintah("BUAT_FORM", "", null, asli);
            }
            String templateId = cariTemplate(sisa);
            if (templateId != null) {
                TemplateProyek.Info info = TemplateProyek.cari(templateId);
                String nama = info != null ? info.nama : sisa;
                return new Perintah("BUAT", nama, templateId, asli);
            }
            return new Perintah("BUAT", kapitalNama(sisa), null, asli);
        }
        // Bangun proyek.
        if (s.startsWith("bangun proyek") || s.startsWith("bangun ")) {
            String nama = ambilNama(s, "bangun proyek", "bangun ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("BANGUN", nama, null, asli);
        }
        // Jalankan proyek.
        if (s.startsWith("jalankan proyek") || s.startsWith("jalankan ")) {
            String nama = ambilNama(s, "jalankan proyek", "jalankan ");
            if (nama.isEmpty()) return new Perintah("TIDAK_KENAL", "", null, asli);
            return new Perintah("JALANKAN", nama, null, asli);
        }
        // Buka proyek / buka layar.
        if (s.startsWith("buka proyek") || s.startsWith("buka ")) {
            String nama = ambilNama(s, "buka proyek", "buka ");
            if (!nama.isEmpty()) {
                String layar = cariLayar(nama);
                if (layar != null) {
                    return new Perintah("LAYAR", layar, null, asli);
                }
                return new Perintah("BUKA", nama, null, asli);
            }
            return new Perintah("TIDAK_KENAL", "", null, asli);
        }
        return new Perintah("TIDAK_KENAL", "", null, asli);
    }

    /** Normalisasi ucapan: huruf kecil, spasi tunggal, buang tanda baca ujung. */
    static String normalisasi(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(new Locale("id", "ID")).trim();
        t = t.replaceAll("[\\s]+", " ");
        while (!t.isEmpty() && (t.endsWith(".") || t.endsWith(",")
                || t.endsWith("!") || t.endsWith("?"))) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /** Mengambil nama setelah awalan (coba awalan panjang dulu). */
    private static String ambilNama(String s, String awalanPanjang, String awalanPendek) {
        String nama = "";
        if (s.startsWith(awalanPanjang)) {
            nama = s.substring(awalanPanjang.length()).trim();
        } else if (s.startsWith(awalanPendek)) {
            nama = s.substring(awalanPendek.length()).trim();
        }
        // "proyek" yang tertinggal di tengah ("bangun proyek Kalkulator").
        if (nama.startsWith("proyek ")) nama = nama.substring(7).trim();
        return kapitalNama(nama);
    }

    /** Mencari id template dari kata kunci di dalam teks (frasa panjang dulu). */
    static String cariTemplate(String teks) {
        String t = " " + normalisasi(teks) + " ";
        for (int i = 0; i < SINONIM_TEMPLATE.length; i++) {
            if (t.indexOf(" " + SINONIM_TEMPLATE[i][0] + " ") >= 0
                    || t.equals(" " + SINONIM_TEMPLATE[i][0] + " ")) {
                return SINONIM_TEMPLATE[i][1];
            }
        }
        // Cocok persis satu kata kunci saja.
        for (int i = 0; i < SINONIM_TEMPLATE.length; i++) {
            if (t.trim().equals(SINONIM_TEMPLATE[i][0])) {
                return SINONIM_TEMPLATE[i][1];
            }
        }
        return null;
    }

    /** Mencari id layar dari kata kunci di dalam teks. */
    private static String cariLayar(String teks) {
        String t = " " + normalisasi(teks) + " ";
        for (int i = 0; i < LAYAR.length; i++) {
            if (t.indexOf(" " + LAYAR[i][0] + " ") >= 0) {
                return LAYAR[i][1];
            }
        }
        return null;
    }

    /** Kapital tiap kata ("kalkulator saya" -> "Kalkulator Saya"). */
    static String kapitalNama(String nama) {
        if (nama == null) return "";
        String t = normalisasi(nama);
        if (t.isEmpty()) return "";
        String[] kata = t.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kata.length; i++) {
            if (kata[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(kata[i].charAt(0)));
            if (kata[i].length() > 1) sb.append(kata[i].substring(1));
        }
        return sb.toString();
    }
}
