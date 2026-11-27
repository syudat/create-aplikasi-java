package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Studio Sistem Desain Adaptif dan Pabrik Komponen Antar-Proyek
 * (Fitur 4, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Token desain: skala jarak + huruf turunan dari nilai dasar,
 *    radius sudut, dan batas area sentuh 48 dp.
 *  - Komponen siap pakai (kartu, navigasi bawah, formulir masuk)
 *    sebagai teks XML yang bisa disuntikkan ke proyek.
 *  - Ekspor tema ke XML (values) atau tema Compose.
 *
 * BATAS JUJUR: komponen dihasilkan sebagai TEKS XML/Compose, bukan
 * pratinjau visual — pengembang menempelkannya ke layout lalu
 * memeriksa dengan Arsitek Tata Letak. Penyesuaian otomatis dua kolom
 * di tablet memakai qualifier lebar layar (values-sw600dp), bukan
 * deteksi perangkat saat jalan.
 */
public class StudioDesain {

    /** Batas minimal area sentuh (dp) menurut pedoman Android. */
    public static final int SENTUH_MIN_DP = 48;

    /** Token desain satu proyek. */
    public static class Token {
        public int jarakDasar = 8;
        public int hurufDasar = 16;
        public int sudut = 12;
        public int sentuhMin = SENTUH_MIN_DP;
    }

    /** Urai "jarak=8, huruf=16, sudut=12" menjadi Token (nilai kosong = bawaan). */
    public static Token uraiToken(String teks) {
        Token t = new Token();
        if (teks == null) return t;
        String[] bagian = teks.split("[,;\n]");
        for (int i = 0; i < bagian.length; i++) {
            String[] kv = bagian[i].split("=");
            if (kv.length != 2) continue;
            String k = kv[0].trim().toLowerCase();
            int v;
            try {
                v = Integer.parseInt(kv[1].trim());
            } catch (Throwable e) {
                continue;
            }
            if ("jarak".equals(k)) t.jarakDasar = v;
            else if ("huruf".equals(k)) t.hurufDasar = v;
            else if ("sudut".equals(k)) t.sudut = v;
            else if ("sentuh".equals(k)) t.sentuhMin = v;
        }
        return t;
    }

    /** Skala jarak turunan: 0.5x, 1x, 1.5x, 2x, 3x, 4x, 6x dari dasar. */
    public static int[] skalaJarak(int dasar) {
        if (dasar <= 0) dasar = 8;
        return new int[]{dasar / 2, dasar, dasar + dasar / 2, dasar * 2,
                dasar * 3, dasar * 4, dasar * 6};
    }

    /** Skala huruf turunan: kecil, dasar, judul, judul besar. */
    public static int[] skalaHuruf(int dasar) {
        if (dasar <= 0) dasar = 16;
        return new int[]{dasar - 2, dasar, dasar + 4, dasar + 8};
    }

    /** Validasi token: daftar masalah (kosong bila valid). */
    public static List<String> validasi(Token t) {
        List<String> masalah = new ArrayList<String>();
        if (t == null) {
            masalah.add("Token tidak valid: token kosong.");
            return masalah;
        }
        if (t.jarakDasar < 4 || t.jarakDasar > 16) {
            masalah.add("Token tidak valid: jarak dasar harus 4 sampai 16 dp.");
        }
        if (t.hurufDasar < 12 || t.hurufDasar > 20) {
            masalah.add("Token tidak valid: huruf dasar harus 12 sampai 20 sp.");
        }
        if (t.sudut < 0 || t.sudut > 32) {
            masalah.add("Token tidak valid: sudut harus 0 sampai 32 dp.");
        }
        if (t.sentuhMin < SENTUH_MIN_DP) {
            masalah.add("Token tidak valid: area sentuh minimal " + SENTUH_MIN_DP + " dp.");
        }
        return masalah;
    }

    /** True bila ukuran elemen memenuhi area sentuh minimal. */
    public static boolean sentuhCukup(int lebarDp, int tinggiDp, int sentuhMin) {
        return lebarDp >= sentuhMin && tinggiDp >= sentuhMin;
    }

    /** Jenis komponen yang bisa dihasilkan. */
    public static String[] jenisKomponen() {
        return new String[]{"kartu", "navigasi", "masuk"};
    }

    /**
     * Hasilkan teks XML satu komponen dari token. "kartu" = kartu info,
     * "navigasi" = bilah navigasi bawah 3 tombol, "masuk" = formulir
     * masuk (nama + kata sandi + tombol).
     */
    public static String komponen(String jenis, Token t) {
        if (t == null) t = new Token();
        int[] j = skalaJarak(t.jarakDasar);
        int[] h = skalaHuruf(t.hurufDasar);
        if ("navigasi".equals(jenis)) {
            return "<LinearLayout\n"
                    + "    android:layout_width=\"match_parent\"\n"
                    + "    android:layout_height=\"" + t.sentuhMin + "dp\"\n"
                    + "    android:orientation=\"horizontal\">\n"
                    + "    <Button android:layout_width=\"0dp\" android:layout_height=\"match_parent\"\n"
                    + "        android:layout_weight=\"1\" android:text=\"Beranda\" />\n"
                    + "    <Button android:layout_width=\"0dp\" android:layout_height=\"match_parent\"\n"
                    + "        android:layout_weight=\"1\" android:text=\"Cari\" />\n"
                    + "    <Button android:layout_width=\"0dp\" android:layout_height=\"match_parent\"\n"
                    + "        android:layout_weight=\"1\" android:text=\"Profil\" />\n"
                    + "</LinearLayout>";
        }
        if ("masuk".equals(jenis)) {
            return "<LinearLayout\n"
                    + "    android:layout_width=\"match_parent\"\n"
                    + "    android:layout_height=\"wrap_content\"\n"
                    + "    android:orientation=\"vertical\"\n"
                    + "    android:padding=\"" + j[1] + "dp\">\n"
                    + "    <EditText android:layout_width=\"match_parent\"\n"
                    + "        android:layout_height=\"" + t.sentuhMin + "dp\"\n"
                    + "        android:hint=\"Nama pengguna\" android:textSize=\"" + h[1] + "sp\" />\n"
                    + "    <EditText android:layout_width=\"match_parent\"\n"
                    + "        android:layout_height=\"" + t.sentuhMin + "dp\"\n"
                    + "        android:layout_marginTop=\"" + j[1] + "dp\"\n"
                    + "        android:hint=\"Kata sandi\" android:textSize=\"" + h[1] + "sp\" />\n"
                    + "    <Button android:layout_width=\"match_parent\"\n"
                    + "        android:layout_height=\"" + t.sentuhMin + "dp\"\n"
                    + "        android:layout_marginTop=\"" + j[2] + "dp\"\n"
                    + "        android:text=\"Masuk\" />\n"
                    + "</LinearLayout>";
        }
        return "<LinearLayout\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"wrap_content\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:padding=\"" + j[1] + "dp\">\n"
                + "    <TextView android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Judul kartu\" android:textSize=\"" + h[2] + "sp\" />\n"
                + "    <TextView android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:layout_marginTop=\"" + j[0] + "dp\"\n"
                + "        android:text=\"Isi kartu\" android:textSize=\"" + h[1] + "sp\" />\n"
                + "</LinearLayout>";
    }

    /** Ekspor token sebagai berkas values/dimens.xml + styles (teks XML). */
    public static String eksporTemaXml(Token t) {
        if (t == null) t = new Token();
        int[] j = skalaJarak(t.jarakDasar);
        int[] h = skalaHuruf(t.hurufDasar);
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        for (int i = 0; i < j.length; i++) {
            b.append("    <dimen name=\"jarak_").append(i).append("\">")
                    .append(j[i]).append("dp</dimen>\n");
        }
        for (int i = 0; i < h.length; i++) {
            b.append("    <dimen name=\"huruf_").append(i).append("\">")
                    .append(h[i]).append("sp</dimen>\n");
        }
        b.append("    <dimen name=\"sudut\">").append(t.sudut).append("dp</dimen>\n");
        b.append("    <dimen name=\"sentuh_min\">").append(t.sentuhMin).append("dp</dimen>\n");
        b.append("</resources>");
        return b.toString();
    }

    /** Ekspor token sebagai tema Compose (teks Kotlin). */
    public static String eksporTemaCompose(Token t) {
        if (t == null) t = new Token();
        int[] j = skalaJarak(t.jarakDasar);
        int[] h = skalaHuruf(t.hurufDasar);
        return "object Tema {\n"
                + "    val jarakKecil = " + j[0] + ".dp\n"
                + "    val jarak = " + j[1] + ".dp\n"
                + "    val jarakBesar = " + j[3] + ".dp\n"
                + "    val hurufBadan = " + h[1] + ".sp\n"
                + "    val hurufJudul = " + h[2] + ".sp\n"
                + "    val sudut = " + t.sudut + ".dp\n"
                + "    val sentuhMin = " + t.sentuhMin + ".dp\n"
                + "}";
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahKomponen, List<String> masalah) {
        int m = masalah == null ? 0 : masalah.size();
        return "Komponen dihasilkan: " + jumlahKomponen + ". Token tidak valid: " + m + ".";
    }
}
