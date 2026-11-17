package com.androlua;

/**
 * Analisis Gambar (Tahap 3G).
 *
 * Menganalisis piksel gambar secara lokal (tanpa internet): warna
 * dominan (rata-rata piksel yang tidak transparan, diambil tiap
 * beberapa piksel supaya cepat), warna teks yang terbaca di atas warna
 * itu (putih atau hitam menurut kecerahan), dan heksa warna.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji ulang di JRE.
 * Activity mengambil piksel Bitmap lewat getPixels() lalu memanggil
 * warnaDominan() dengan larik int ARGB itu.
 */
public class AnalisisGambar {

    /**
     * Warna dominan: rata-rata komponen R/G/B dari piksel yang tidak
     * transparan (alpha >= 128), diambil tiap {@code langkah} piksel.
     * Mengembalikan warna ARGB (alpha selalu 255).
     */
    public static int warnaDominan(int[] argb, int langkah) {
        if (argb == null || argb.length == 0) return 0xFF808080;
        if (langkah < 1) langkah = 1;
        long r = 0;
        long g = 0;
        long b = 0;
        long hitung = 0;
        for (int i = 0; i < argb.length; i += langkah) {
            int p = argb[i];
            int alpha = (p >> 24) & 0xFF;
            if (alpha < 128) continue;
            r += (p >> 16) & 0xFF;
            g += (p >> 8) & 0xFF;
            b += p & 0xFF;
            hitung++;
        }
        if (hitung == 0) return 0xFF808080;
        int rr = (int) (r / hitung);
        int gg = (int) (g / hitung);
        int bb = (int) (b / hitung);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    /**
     * Warna teks yang terbaca di atas warna latar: putih bila latar
     * gelap, hitam bila latar terang (ambang kecerahan 140).
     */
    public static int warnaTeksUntuk(int warna) {
        int r = (warna >> 16) & 0xFF;
        int g = (warna >> 8) & 0xFF;
        int b = warna & 0xFF;
        int cerah = (r * 299 + g * 587 + b * 114) / 1000;
        return cerah < 140 ? 0xFFFFFFFF : 0xFF000000;
    }

    /** Heksa warna tanpa alpha ("#RRGGBB"). */
    public static String heksa(int warna) {
        return String.format("#%06X", warna & 0xFFFFFF);
    }

    /** Heksa warna dengan alpha ("#AARRGGBB"). */
    public static String heksaAlpha(int warna) {
        return String.format("#%08X", warna);
    }
}
