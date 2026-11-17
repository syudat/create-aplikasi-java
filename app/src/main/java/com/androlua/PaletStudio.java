package com.androlua;

/**
 * Palet gaya "Android Studio" untuk IDE Create Aplikasi — TEMA TERANG.
 *
 * Alasan memilih tema terang (bukan Darcula gelap): tema aplikasi di
 * styles.xml adalah Theme.DeviceDefault.Light, jadi semua widget sistem
 * (dialog AlertDialog, Spinner, baris ListView simple_list_item_1,
 * CheckBox, RadioButton) menggambar teks GELAP dengan asumsi latar
 * TERANG. Latar gelap akan membuat teks sistem itu tidak terbaca
 * (gelap di atas gelap). Tema terang Android Studio (IntelliJ Light)
 * menjaga semua widget sistem tetap terbaca tanpa mengubah satu pun
 * adapter/dialog — dan kontrasnya tetap tinggi untuk Jieshuo.
 *
 * Pengecualian yang disengaja: panel Terminal memakai latar gelap
 * Darcula (#2B2D30 + teks #A9B7C6) — seperti jendela Terminal di
 * Android Studio yang memang gelap di kedua tema — dengan kontras
 * yang tetap memadai (diuji di Uji3J).
 *
 * MURNI Java — tanpa satu pun import android — supaya bisa diuji di
 * JRE biasa (driver Uji3J memakai rasioKontras + pasanganTeks).
 * Penerapan ke View ada di GayaStudio.java.
 */
public final class PaletStudio {
    private PaletStudio() {
    }

    // ------------------------------------------------------------ latar
    /** Latar jendela/layar (abu terang IntelliJ). */
    public static final int LATAR = 0xFFF2F2F2;
    /** Latar kartu/kotak isi (putih). */
    public static final int PANEL = 0xFFFFFFFF;
    /** Garis pemisah / bingkai tipis. */
    public static final int GARIS = 0xFFD1D1D1;

    // ------------------------------------------------------------ teks
    /** Judul layar (hitam). */
    public static final int TEKS_JUDUL = 0xFF000000;
    /** Teks isi (hampir hitam). */
    public static final int TEKS_ISI = 0xFF1E1E1E;
    /** Teks kecil/keterangan (abu gelap — masih kontras di latar terang). */
    public static final int TEKS_SEKUNDER = 0xFF595959;
    /** Teks petunjuk (hint) di kotak isi putih. */
    public static final int TEKS_PETUNJUK = 0xFF737373;

    // ------------------------------------------------------------ aksen
    /**
     * Aksen tombol utama. TIDAK final (reorganisasi pengaturan 2026-11-17):
     * kategori Tema mengubahnya lewat terapkanTema(). Latar tetap terang
     * (semua tema aksen di bawah cukup gelap untuk teks putih di atasnya,
     * supaya widget sistem tetap terbaca).
     */
    public static int AKSEN = 0xFF0B6EAD;
    /** Aksen yang lebih gelap (variasi). Ikut berubah bersama AKSEN. */
    public static int AKSEN_GELAP = 0xFF095A8F;
    /** Teks di atas tombol aksen (putih). */
    public static final int TEKS_DI_ATAS_AKSEN = 0xFFFFFFFF;

    /** Satu tema aksen aplikasi (latar tetap terang). */
    public static final class TemaAksen {
        public final String id;
        public final String nama;
        public final int aksen;
        public final int aksenGelap;

        public TemaAksen(String id, String nama, int aksen, int aksenGelap) {
            this.id = id;
            this.nama = nama;
            this.aksen = aksen;
            this.aksenGelap = aksenGelap;
        }
    }

    /** Semua tema aksen yang bisa dipilih di Pengaturan > Tema. */
    public static final TemaAksen[] SEMUA_TEMA = {
        new TemaAksen("biru", "Biru", 0xFF0B6EAD, 0xFF095A8F),
        new TemaAksen("hijau", "Hijau", 0xFF2E7D32, 0xFF1B5E20),
        new TemaAksen("ungu", "Ungu", 0xFF6A1B9A, 0xFF4A148C),
        new TemaAksen("merah", "Merah", 0xFFC62828, 0xFFB71C1C),
        new TemaAksen("oranye", "Oranye", 0xFFEF6C00, 0xFFE65100),
    };

    /** Terapkan tema aksen berdasarkan id (id tak dikenal = Biru). */
    public static void terapkanTema(String id) {
        for (int i = 0; i < SEMUA_TEMA.length; i++) {
            if (SEMUA_TEMA[i].id.equals(id)) {
                AKSEN = SEMUA_TEMA[i].aksen;
                AKSEN_GELAP = SEMUA_TEMA[i].aksenGelap;
                return;
            }
        }
        AKSEN = 0xFF0B6EAD;
        AKSEN_GELAP = 0xFF095A8F;
    }

    /** Nama tema berdasarkan id (id tak dikenal = "Biru"). */
    public static String namaTema(String id) {
        for (int i = 0; i < SEMUA_TEMA.length; i++) {
            if (SEMUA_TEMA[i].id.equals(id)) return SEMUA_TEMA[i].nama;
        }
        return "Biru";
    }

    // ------------------------------------------------------------ terminal
    /** Latar panel Terminal (Darcula — seperti Terminal Android Studio). */
    public static final int TERMINAL_LATAR = 0xFF2B2D30;
    /** Teks keluaran Terminal (abu terang Darcula). */
    public static final int TERMINAL_TEKS = 0xFFA9B7C6;
    /** Teks petunjuk di baris perintah Terminal. */
    public static final int TERMINAL_PETUNJUK = 0xFF8A99A8;

    // ------------------------------------------------------------ editor kode
    /** Latar editor kode (putih — editor terang Android Studio). */
    public static final int EDITOR_LATAR = 0xFFFFFFFF;
    /** Teks editor kode (hitam). */
    public static final int EDITOR_TEKS = 0xFF000000;

    // ------------------------------------------------------------ ukuran (sp)
    public static final float TEKS_JUDUL_SP = 20f;
    public static final float TEKS_LABEL_SP = 14f;
    public static final float TEKS_LABEL_KECIL_SP = 12f;
    public static final float TEKS_TOMBOL_SP = 16f;
    public static final float TEKS_EDIT_SP = 16f;

    // ------------------------------------------------------------ jarak (dp)
    public static final int PADDING_ROOT_H_DP = 16;
    public static final int PADDING_ROOT_V_DP = 12;
    public static final int JARAK_TOMBOL_ATAS_DP = 8;
    public static final int RADIUS_TOMBOL_DP = 4;

    // ------------------------------------------------------------ kontras WCAG
    /** Luminansi relatif (WCAG 2.1) dari warna ARGB (alfa diabaikan). */
    public static double luminansi(int warna) {
        double r = ((warna >> 16) & 0xFF) / 255.0;
        double g = ((warna >> 8) & 0xFF) / 255.0;
        double b = (warna & 0xFF) / 255.0;
        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** Rasio kontras WCAG 2.1 antara warna depan dan warna belakang. */
    public static double rasioKontras(int depan, int belakang) {
        double l1 = luminansi(depan);
        double l2 = luminansi(belakang);
        double terang = Math.max(l1, l2);
        double gelap = Math.min(l1, l2);
        return (terang + 0.05) / (gelap + 0.05);
    }

    /**
     * Semua pasangan warna teks/latar yang dipakai IDE.
     * Setiap pasangan HARUS punya rasio kontras >= 4.5 (WCAG AA untuk
     * teks biasa) — diuji di Uji3J.
     * Kolom: {warna teks, warna latar, keterangan}.
     */
    public static int[][] pasanganTeks() {
        return new int[][]{
                {TEKS_JUDUL, LATAR, 0},
                {TEKS_ISI, LATAR, 0},
                {TEKS_SEKUNDER, LATAR, 0},
                {TEKS_ISI, PANEL, 0},
                {TEKS_SEKUNDER, PANEL, 0},
                {TEKS_PETUNJUK, PANEL, 0},
                {TEKS_DI_ATAS_AKSEN, AKSEN, 0},
                {TERMINAL_TEKS, TERMINAL_LATAR, 0},
                {TERMINAL_PETUNJUK, TERMINAL_LATAR, 0},
                {EDITOR_TEKS, EDITOR_LATAR, 0},
        };
    }
}
