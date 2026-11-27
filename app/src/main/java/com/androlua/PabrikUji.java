package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pabrik Pengujian Unit Otomatis dan Generator Fuzzing Batas
 * (Fitur 9, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - ujiDariTandaTangan: dari "String nama(String a, int b)" hasilkan
 *    kasus uji normal + batas (kosong, null, maks, negatif, aneh).
 *  - masukanFuzz: daftar masukan ekstrem per tipe.
 *  - Fuzzing nyata atas dua fungsi contoh (normalisasi telepon dan
 *    hitung diskon) — masukan ekstrem sungguh dijalankan dan kegagalan
 *    (exception / hasil mustahil) dilaporkan.
 *  - Hasilkan teks sumber uji (JUnit) untuk ditempel ke proyek.
 *
 * BATAS JUJUR: generator membaca TANDA TANGAN fungsi (teks), bukan
 * mengeksekusi kode proyek — uji yang dihasilkan adalah kerangka +
 * nilai batas yang harus dilengkapi harapan (expected) oleh
 * pengembang. Fuzzing sungguhan hanya berjalan atas fungsi contoh di
 * kelas ini; menjalankan fuzz atas fungsi proyek butuh kompilasi dan
 * pemuatan kelasnya di tahap berikut.
 */
public class PabrikUji {

    /** Satu kasus uji yang dihasilkan. */
    public static class KasusUji {
        public String nama = "";
        public String masukan = "";
        public String jenis = "";
        public String toString() {
            return jenis + " " + nama + "(" + masukan + ")";
        }
    }

    /** Satu hasil fuzz: masukan + lulus/gagal + pesan. */
    public static class HasilFuzz {
        public String masukan = "";
        public boolean lulus = true;
        public String pesan = "";
    }

    /** Masukan ekstrem untuk satu tipe parameter. */
    public static List<String> masukanFuzz(String tipe) {
        List<String> hasil = new ArrayList<String>();
        if (tipe == null) return hasil;
        String t = tipe.trim();
        if ("String".equals(t) || "CharSequence".equals(t)) {
            hasil.add("\"\"");
            hasil.add("null");
            hasil.add("\" \"");
            hasil.add("\"+628123456789\"");
            hasil.add("\"++62812\"");
            hasil.add("\"0812abc345\"");
            String panjang = "";
            for (int i = 0; i < 500; i++) panjang += "9";
            hasil.add("\"" + panjang + "\"");
            hasil.add("\"\\u2603\\u00e9\\u4e2d\"");
        } else if ("int".equals(t) || "Integer".equals(t) || "long".equals(t)) {
            hasil.add("0");
            hasil.add("1");
            hasil.add("-1");
            hasil.add("2147483647");
            hasil.add("-2147483648");
            hasil.add("100000000");
        } else if ("double".equals(t) || "float".equals(t)) {
            hasil.add("0.0");
            hasil.add("-0.5");
            hasil.add("100.0");
            hasil.add("1.7976931348623157E308");
        } else if ("boolean".equals(t)) {
            hasil.add("true");
            hasil.add("false");
        } else {
            hasil.add("null");
        }
        return hasil;
    }

    /** Urai tanda tangan metode Java sederhana menjadi nama + tipe param. */
    public static String[] uraiTandaTangan(String tanda) {
        if (tanda == null) return null;
        Matcher m = Pattern.compile(
                "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)").matcher(tanda.trim());
        if (!m.find()) return null;
        String nama = m.group(1);
        String param = m.group(2).trim();
        List<String> keluar = new ArrayList<String>();
        keluar.add(nama);
        if (param.length() > 0) {
            String[] bagian = param.split(",");
            for (int i = 0; i < bagian.length; i++) {
                String[] kata = bagian[i].trim().split("\\s+");
                if (kata.length > 0) keluar.add(kata[0]);
            }
        }
        return keluar.toArray(new String[keluar.size()]);
    }

    /** Hasilkan kasus uji normal + batas dari tanda tangan fungsi. */
    public static List<KasusUji> ujiDariTandaTangan(String tanda) {
        List<KasusUji> hasil = new ArrayList<KasusUji>();
        String[] urai = uraiTandaTangan(tanda);
        if (urai == null) return hasil;
        String nama = urai[0];
        if (urai.length == 1) {
            KasusUji k = new KasusUji();
            k.nama = nama;
            k.masukan = "";
            k.jenis = "normal";
            hasil.add(k);
            return hasil;
        }
        for (int i = 1; i < urai.length; i++) {
            List<String> masukan = masukanFuzz(urai[i]);
            for (int j = 0; j < masukan.size(); j++) {
                KasusUji k = new KasusUji();
                k.nama = nama;
                k.masukan = masukan.get(j);
                k.jenis = j == 0 ? "normal" : "batas";
                hasil.add(k);
            }
        }
        return hasil;
    }

    /** Fungsi contoh 1: normalisasi nomor telepon Indonesia. */
    public static String normalisasiTelepon(String nomor) {
        if (nomor == null) return null;
        String bersih = nomor.replaceAll("[^0-9+]", "");
        if (bersih.startsWith("+62")) return bersih;
        if (bersih.startsWith("62")) return "+" + bersih;
        if (bersih.startsWith("0")) return "+62" + bersih.substring(1);
        if (bersih.length() == 0) return "";
        return "+62" + bersih;
    }

    /** Fungsi contoh 2: diskon bertingkat (harga, persen 0-100). */
    public static double hitungDiskon(double harga, double persen) {
        if (harga < 0) throw new IllegalArgumentException("harga negatif");
        if (persen < 0 || persen > 100) {
            throw new IllegalArgumentException("persen di luar 0-100");
        }
        return harga - (harga * persen / 100.0);
    }

    /** Jalankan fuzz atas normalisasi telepon dengan masukan ekstrem. */
    public static List<HasilFuzz> fuzzTelepon() {
        List<HasilFuzz> hasil = new ArrayList<HasilFuzz>();
        List<String> masukan = masukanFuzz("String");
        String[] mentah = {"", null, " ", "+628123456789", "++62812",
                "0812abc345", null, null};
        mentah[6] = new String(new char[500]).replace("\0", "9");
        mentah[7] = "\u2603\u00e9\u4e2d";
        for (int i = 0; i < masukan.size(); i++) {
            HasilFuzz h = new HasilFuzz();
            h.masukan = masukan.get(i);
            try {
                String keluar = normalisasiTelepon(mentah[i]);
                h.lulus = keluar != null;
                h.pesan = keluar == null ? "hasil null" : keluar;
            } catch (Throwable t) {
                h.lulus = false;
                h.pesan = t.getClass().getSimpleName();
            }
            hasil.add(h);
        }
        return hasil;
    }

    /** Jalankan fuzz atas hitung diskon dengan pasangan ekstrem. */
    public static List<HasilFuzz> fuzzDiskon() {
        List<HasilFuzz> hasil = new ArrayList<HasilFuzz>();
        double[][] pasangan = {{100000, 10}, {0, 0}, {100000, 0}, {100000, 100},
                {100000, -5}, {100000, 150}, {-5000, 10}, {100000, 50}};
        for (int i = 0; i < pasangan.length; i++) {
            HasilFuzz h = new HasilFuzz();
            h.masukan = pasangan[i][0] + ", " + pasangan[i][1];
            try {
                double keluar = hitungDiskon(pasangan[i][0], pasangan[i][1]);
                h.lulus = keluar >= 0 && keluar <= pasangan[i][0];
                h.pesan = String.valueOf(keluar);
            } catch (Throwable t) {
                h.lulus = false;
                h.pesan = t.getMessage();
            }
            hasil.add(h);
        }
        return hasil;
    }

    /** Hasilkan teks sumber uji JUnit dari daftar kasus. */
    public static String hasilkanSumberUji(String namaKelas, List<KasusUji> kasus) {
        StringBuilder b = new StringBuilder();
        b.append("import org.junit.Test;\nimport static org.junit.Assert.*;\n\n");
        b.append("public class Uji").append(namaKelas).append(" {\n");
        for (int i = 0; i < kasus.size(); i++) {
            KasusUji k = kasus.get(i);
            b.append("    @Test public void uji").append(i).append("() {\n");
            b.append("        // ").append(k.jenis).append(": ").append(k.nama)
                    .append("(").append(k.masukan).append(")\n");
            b.append("        // TODO: tulis harapan (expected) di sini.\n");
            b.append("    }\n");
        }
        b.append("}\n");
        return b.toString();
    }

    /** Hitung baris tak kosong dalam teks sumber (0 bila null). */
    public static int hitungBarisKode(String kode) {
        if (kode == null) return 0;
        int n = 0;
        String[] baris = kode.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].trim().length() > 0) n++;
        }
        return n;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int lulus, int gagal) {
        return "Uji lulus: " + lulus + ". Uji gagal: " + gagal + ".";
    }
}
