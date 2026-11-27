package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *  - Pustaka token antar-proyek: satu set token bernama disimpan di
 *    lokasi bersama (di luar folder tiap proyek) dan dapat dipakai
 *    semua proyek; proyek yang memakai suatu set menandai dirinya
 *    di token-studio.txt, dan satu perintah sinkronisasi menerapkan
 *    token pustaka terbaru ke semua proyek yang memakainya.
 *
 * BATAS JUJUR: komponen dihasilkan sebagai TEKS XML/Compose, bukan
 * pratinjau visual — pengembang menempelkannya ke layout lalu
 * memeriksa dengan Arsitek Tata Letak. Penyesuaian otomatis dua kolom
 * di tablet memakai qualifier lebar layar (values-sw600dp), bukan
 * deteksi perangkat saat jalan. Sinkronisasi pustaka bekerja lewat
 * berkas di penyimpanan yang sama (luring, tanpa server): proyek
 * yang tokennya diubah di luar Create Aplikasi, atau yang
 * token-studio.txt-nya dihapus tangan, tidak ikut tersinkron sampai
 * ia memakai set itu lagi.
 */
public class StudioDesain {

    /** Batas minimal area sentuh (dp) menurut pedoman Android. */
    public static final int SENTUH_MIN_DP = 48;

    /** Nama folder pustaka token bersama, di dalam akar proyek. */
    public static final String DIR_PUSTAKA = ".pustaka-token";
    /** Nama berkas pustaka token bersama. */
    public static final String BERKAS_PUSTAKA = "pustaka-token.txt";
    /** Nama berkas token di dalam tiap proyek. */
    public static final String BERKAS_TOKEN_PROYEK = "token-studio.txt";

    /** Token desain satu proyek. */
    public static class Token {
        public int jarakDasar = 8;
        public int hurufDasar = 16;
        public int sudut = 12;
        public int sentuhMin = SENTUH_MIN_DP;
        /** Nama set token ini di pustaka ("" = belum bernama). */
        public String nama = "";
        /** Asal token: "" (lokal) atau "pustaka:<nama set>". */
        public String sumber = "";
        /** Waktu terakhir diubah (milidetik sejak epoch), 0 = tidak tahu. */
        public long diubahPada = 0;
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
            if ("diubah".equals(k)) {
                try { t.diubahPada = Long.parseLong(kv[1].trim()); } catch (Throwable e) { }
                continue;
            }
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
        // Kunci teks (nama/sumber) tidak bisa lewat split "=" angka.
        String[] bagianTeks = teks.split("[,;\n]");
        for (int i = 0; i < bagianTeks.length; i++) {
            String[] kv = bagianTeks[i].split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().toLowerCase();
            String v = kv[1].trim();
            if ("nama".equals(k)) t.nama = v;
            else if ("sumber".equals(k)) t.sumber = v;
        }
        return t;
    }

    /** Ubah Token menjadi satu baris teks "kunci=nilai, ..." (dapat diurai lagi). */
    public static String tokenKeTeks(Token t) {
        if (t == null) t = new Token();
        StringBuilder b = new StringBuilder();
        b.append("jarak=").append(t.jarakDasar);
        b.append(", huruf=").append(t.hurufDasar);
        b.append(", sudut=").append(t.sudut);
        b.append(", sentuh=").append(t.sentuhMin);
        if (t.nama != null && t.nama.length() > 0) b.append(", nama=").append(t.nama);
        if (t.sumber != null && t.sumber.length() > 0) b.append(", sumber=").append(t.sumber);
        if (t.diubahPada > 0) b.append(", diubah=").append(t.diubahPada);
        return b.toString();
    }

    /** True bila token memakai set pustaka bernama namaSet. */
    public static boolean memakaiSet(Token t, String namaSet) {
        if (t == null || namaSet == null || namaSet.length() == 0) return false;
        if (namaSet.equals(t.nama)) return true;
        return ("pustaka:" + namaSet).equals(t.sumber);
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

    // ================================================================
    // Pustaka token antar-proyek (tersinkron otomatis lewat berkas).
    //
    // Lokasi bersama: <akar proyek>/.pustaka-token/pustaka-token.txt
    // (satu folder di luar folder tiap proyek, jadi semua proyek di
    // ponsel yang sama dapat memakainya). Isi berkas: satu bagian
    // "[nama set]" per set, berisi baris kunci=nilai. Proyek yang
    // memakai suatu set menyimpan token-studio.txt dengan
    // sumber=pustaka:<nama set>; sinkronisasi membaca penanda itu
    // dan menulis ulang token + tema XML/Compose proyek tersebut.
    // ================================================================

    /** Berkas pustaka di dalam folder bersama. */
    public static File berkasPustaka(File dirBersama) {
        return new File(dirBersama, BERKAS_PUSTAKA);
    }

    /** Folder pustaka bersama di bawah akar proyek. */
    public static File dirPustaka(File akarProyek) {
        return new File(akarProyek, DIR_PUSTAKA);
    }

    private static String bacaTeks(File f) {
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder b = new StringBuilder();
            String baris;
            while ((baris = r.readLine()) != null) b.append(baris).append("\n");
            r.close();
            return b.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean tulisTeks(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            out.write(isi);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Muat semua set pustaka (urut penyimpanan, nama -> Token). */
    public static Map<String, Token> muatSemuaPustaka(File dirBersama) {
        Map<String, Token> hasil = new LinkedHashMap<String, Token>();
        String isi = bacaTeks(berkasPustaka(dirBersama));
        if (isi == null) return hasil;
        String namaKini = null;
        StringBuilder blok = new StringBuilder();
        String[] baris = isi.split("\n");
        for (int i = 0; i <= baris.length; i++) {
            String b = i < baris.length ? baris[i].trim() : "";
            boolean bagianBaru = b.startsWith("[") && b.endsWith("]") && b.length() > 2;
            if (bagianBaru || i == baris.length) {
                if (namaKini != null && namaKini.length() > 0) {
                    Token t = uraiToken(blok.toString().replace("\n", ", "));
                    t.nama = namaKini;
                    if (t.sumber == null || t.sumber.length() == 0) {
                        t.sumber = "pustaka:" + namaKini;
                    }
                    hasil.put(namaKini, t);
                }
                if (bagianBaru) {
                    namaKini = b.substring(1, b.length() - 1).trim();
                    blok = new StringBuilder();
                }
                continue;
            }
            if (b.length() == 0 || b.startsWith("#")) continue;
            blok.append(b).append("\n");
        }
        return hasil;
    }

    /** Daftar nama set di pustaka (urut penyimpanan). */
    public static List<String> daftarPustaka(File dirBersama) {
        return new ArrayList<String>(muatSemuaPustaka(dirBersama).keySet());
    }

    /** Muat satu set dari pustaka; null bila tidak ada. */
    public static Token muatDariPustaka(File dirBersama, String namaSet) {
        if (namaSet == null || namaSet.length() == 0) return null;
        return muatSemuaPustaka(dirBersama).get(namaSet);
    }

    private static boolean tulisSemuaPustaka(File dirBersama, Map<String, Token> semua) {
        StringBuilder b = new StringBuilder();
        b.append("# Pustaka token desain Create Aplikasi — satu set per bagian.\n");
        b.append("# Diubah lewat Studio Sistem Desain; jangan sunting tangan bila tidak perlu.\n");
        for (Map.Entry<String, Token> e : semua.entrySet()) {
            Token t = e.getValue();
            b.append("[").append(e.getKey()).append("]\n");
            b.append("jarak=").append(t.jarakDasar).append("\n");
            b.append("huruf=").append(t.hurufDasar).append("\n");
            b.append("sudut=").append(t.sudut).append("\n");
            b.append("sentuh=").append(t.sentuhMin).append("\n");
            b.append("sumber=pustaka:").append(e.getKey()).append("\n");
            if (t.diubahPada > 0) b.append("diubah=").append(t.diubahPada).append("\n");
        }
        return tulisTeks(berkasPustaka(dirBersama), b.toString());
    }

    /**
     * Simpan (atau perbarui) satu set bernama ke pustaka bersama.
     * Token tanpa nama tidak bisa disimpan (kembalikan false).
     * Stempel diubahPada diisi saat ini bila belum ada.
     */
    public static boolean simpanKePustaka(File dirBersama, Token t) {
        if (t == null || t.nama == null || t.nama.trim().length() == 0) return false;
        t.nama = t.nama.trim();
        if (t.diubahPada <= 0) t.diubahPada = System.currentTimeMillis();
        Map<String, Token> semua = muatSemuaPustaka(dirBersama);
        semua.put(t.nama, t);
        return tulisSemuaPustaka(dirBersama, semua);
    }

    /** Hapus satu set dari pustaka; false bila set tidak ada / gagal tulis. */
    public static boolean hapusDariPustaka(File dirBersama, String namaSet) {
        if (namaSet == null || namaSet.length() == 0) return false;
        Map<String, Token> semua = muatSemuaPustaka(dirBersama);
        if (semua.remove(namaSet) == null) return false;
        return tulisSemuaPustaka(dirBersama, semua);
    }

    /** Simpan token ke dalam folder proyek (token-studio.txt). */
    public static boolean simpanTokenProyek(File dirProyek, Token t) {
        if (dirProyek == null || t == null) return false;
        if (t.diubahPada <= 0) t.diubahPada = System.currentTimeMillis();
        return tulisTeks(new File(dirProyek, BERKAS_TOKEN_PROYEK), tokenKeTeks(t));
    }

    /** Muat token proyek; null bila proyek belum punya token-studio.txt. */
    public static Token muatTokenProyek(File dirProyek) {
        if (dirProyek == null) return null;
        String isi = bacaTeks(new File(dirProyek, BERKAS_TOKEN_PROYEK));
        if (isi == null) return null;
        return uraiToken(isi);
    }

    /** True bila proyek ini memakai set pustaka bernama namaSet. */
    public static boolean proyekMemakaiToken(File dirProyek, String namaSet) {
        return memakaiSet(muatTokenProyek(dirProyek), namaSet);
    }

    /**
     * Terapkan satu set pustaka ke satu proyek: tulis token-studio.txt
     * (dengan penanda sumber), tema-studio.xml, dan tema-studio.kt.
     * Kembalikan true bila ketiganya tertulis.
     */
    public static boolean terapkanKeProyek(File dirProyek, Token dariPustaka) {
        if (dirProyek == null || dariPustaka == null) return false;
        Token t = new Token();
        t.jarakDasar = dariPustaka.jarakDasar;
        t.hurufDasar = dariPustaka.hurufDasar;
        t.sudut = dariPustaka.sudut;
        t.sentuhMin = dariPustaka.sentuhMin;
        t.nama = dariPustaka.nama;
        t.sumber = dariPustaka.sumber != null && dariPustaka.sumber.length() > 0
                ? dariPustaka.sumber : "pustaka:" + dariPustaka.nama;
        t.diubahPada = dariPustaka.diubahPada;
        boolean ok = simpanTokenProyek(dirProyek, t);
        ok = tulisTeks(new File(dirProyek, "tema-studio.xml"), eksporTemaXml(t)) && ok;
        ok = tulisTeks(new File(dirProyek, "tema-studio.kt"), eksporTemaCompose(t)) && ok;
        return ok;
    }

    /** Hasil satu perintah sinkronisasi (untuk dibacakan ke pengguna). */
    public static class HasilSinkron {
        public int diperbarui = 0;
        public int dilewati = 0;
        public List<String> proyek = new ArrayList<String>();
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("Proyek diperbarui: ").append(diperbarui)
                    .append(". Proyek dilewati: ").append(dilewati).append(".");
            for (int i = 0; i < proyek.size(); i++) {
                b.append("\n- ").append(proyek.get(i));
            }
            return b.toString();
        }
    }

    /**
     * Sinkronisasi: terapkan set pustaka ke SEMUA proyek di bawah
     * akarProyek yang memakai set itu (penanda sumber di
     * token-studio.txt). Proyek yang tidak memakai set itu dilewati.
     * Kembalikan HasilSinkron (0 diperbarui bila set tidak ada).
     */
    public static HasilSinkron sinkronkanKeProyekMemakai(File akarProyek,
            File dirBersama, String namaSet) {
        HasilSinkron hasil = new HasilSinkron();
        Token t = muatDariPustaka(dirBersama, namaSet);
        if (t == null || akarProyek == null || !akarProyek.isDirectory()) return hasil;
        File[] daftar = akarProyek.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            File dir = daftar[i];
            if (!dir.isDirectory()) continue;
            if (dir.getName().startsWith(".")) continue;
            if (!proyekMemakaiToken(dir, namaSet)) {
                hasil.dilewati++;
                continue;
            }
            if (terapkanKeProyek(dir, t)) {
                hasil.diperbarui++;
                hasil.proyek.add(dir.getName());
            } else {
                hasil.dilewati++;
            }
        }
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahKomponen, List<String> masalah) {
        int m = masalah == null ? 0 : masalah.size();
        return "Komponen dihasilkan: " + jumlahKomponen + ". Token tidak valid: " + m + ".";
    }
}
