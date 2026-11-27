package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narator Alur Logika Kode dan Peninjau Semantik Berbasis AI
 * (Fitur 12, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - narasi: tiap pernyataan kode Java sederhana diubah menjadi
 *    kalimat bahasa Indonesia (tugasan, percabangan, perulangan,
 *    pemanggilan, pengembalian).
 *  - petaPanggilan: metode -> metode yang dipanggilnya.
 *  - periksaBug: pola bug halus — == pada String, field yang ditulis
 *    di dua metode (potensi balapan data), stream yang dibuka tanpa
 *    close, hasil find yang dipakai tanpa cek null.
 *
 * BATAS JUJUR: narasi dan pemeriksaan memakai POLA TEKS luring,
 * bukan model AI online dan bukan analisis aliran data penuh — pola
 * yang tidak dikenali dinarasikan apa adanya sebagai "baris kode".
 * Penyedia AI aktif (Pengaturan AI) belum dipakai untuk meringkas;
 * itu tahap berikut.
 */
public class NaratorLogika {

    /** Satu langkah alur: nomor baris + kalimat narasi. */
    public static class Langkah {
        public int baris = 0;
        public String narasi = "";
        public String toString() {
            return baris + ": " + narasi;
        }
    }

    /** Satu peringatan bug: baris + jenis + penjelasan. */
    public static class Peringatan {
        public int baris = 0;
        public String jenis = "";
        public String penjelasan = "";
        public String toString() {
            return baris + ": [" + jenis + "] " + penjelasan;
        }
    }

    private static final Pattern TUGAS = Pattern.compile(
            "^\\s*(?:\\w+\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+);\\s*$");
    private static final Pattern PANGGIL = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern METODE = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\)]*\\)\\s*\\{");

    /** Ubah satu baris kode menjadi kalimat narasi. */
    public static String narasiBaris(String baris) {
        if (baris == null) return "";
        String b = baris.trim();
        if (b.length() == 0) return "";
        if (b.startsWith("//")) return "Komentar: " + b.substring(2).trim();
        if (b.startsWith("if (") || b.startsWith("if(")) {
            return "Jika " + isiKurung(b) + ", maka jalankan blok berikut.";
        }
        if (b.startsWith("else")) return "Kalau tidak, jalankan blok berikut.";
        if (b.startsWith("for (") || b.startsWith("for(")) {
            return "Untuk setiap pengulangan " + isiKurung(b) + ", jalankan blok berikut.";
        }
        if (b.startsWith("while (") || b.startsWith("while(")) {
            return "Selama " + isiKurung(b) + ", ulangi blok berikut.";
        }
        if (b.startsWith("return ")) {
            return "Kembalikan " + b.substring(7).replace(";", "").trim() + ".";
        }
        if (b.startsWith("throw ")) {
            return "Lempar galat " + b.substring(6).replace(";", "").trim() + ".";
        }
        Matcher t = TUGAS.matcher(b);
        if (t.find()) {
            return "Simpan " + t.group(2).replace(";", "").trim()
                    + " ke variabel " + t.group(1) + ".";
        }
        Matcher p = PANGGIL.matcher(b);
        if (p.find()) {
            return "Panggil " + p.group(1) + ".";
        }
        if (b.equals("{") || b.equals("}") || b.equals("};")) return "";
        return "Baris kode: " + b;
    }

    private static String isiKurung(String b) {
        int awal = b.indexOf('(');
        int akhir = b.lastIndexOf(')');
        if (awal < 0 || akhir <= awal) return b;
        return b.substring(awal + 1, akhir).trim();
    }

    /** Narasi seluruh kode (satu langkah per baris bermakna). */
    public static List<Langkah> narasi(String kode) {
        List<Langkah> hasil = new ArrayList<Langkah>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String n = narasiBaris(baris[i]);
            if (n.length() == 0) continue;
            Langkah l = new Langkah();
            l.baris = i + 1;
            l.narasi = n;
            hasil.add(l);
        }
        return hasil;
    }

    /** Tujuan utama kode: kalimat pertama dari nama kelas/metode pertama. */
    public static String tujuanUtama(String kode) {
        if (kode == null) return "";
        Matcher m = Pattern.compile(
                "\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(kode);
        if (m.find()) return "Kode ini berkisar pada " + m.group(1) + ".";
        m = Pattern.compile(
                "\\b(?:void|int|long|double|boolean|String)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                .matcher(kode);
        if (m.find()) return "Kode ini berkisar pada " + m.group(1) + ".";
        return "Kode ini berisi pernyataan berurutan.";
    }

    /** Peta panggilan: "pemanggil -> dipanggil" per baris metode. */
    public static List<String> petaPanggilan(String kode) {
        List<String> hasil = new ArrayList<String>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        String kini = "";
        for (int i = 0; i < baris.length; i++) {
            Matcher m = METODE.matcher(baris[i]);
            if (m.find() && !baris[i].trim().startsWith("if")
                    && !baris[i].trim().startsWith("for")
                    && !baris[i].trim().startsWith("while")) {
                kini = m.group(1);
                continue;
            }
            Matcher p = PANGGIL.matcher(baris[i]);
            while (p.find()) {
                String nama = p.group(1);
                if (nama.equals(kini) || "if".equals(nama) || "for".equals(nama)
                        || "while".equals(nama) || "return".equals(nama)) continue;
                String entri = (kini.length() == 0 ? "?" : kini) + " -> " + nama;
                if (!hasil.contains(entri)) hasil.add(entri);
            }
        }
        return hasil;
    }

    /** Periksa pola bug halus; kembalikan daftar peringatan. */
    public static List<Peringatan> periksaBug(String kode) {
        List<Peringatan> hasil = new ArrayList<Peringatan>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        List<String> fieldTulis = new ArrayList<String>();
        List<String> metodeTulis = new ArrayList<String>();
        String kini = "";
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            Matcher m = METODE.matcher(b);
            if (m.find() && !b.trim().startsWith("if") && !b.trim().startsWith("for")) {
                kini = m.group(1);
            }
            if (b.contains("==") && (b.contains("\"") || b.matches(".*==\\s*[a-z]\\w*.*"))) {
                Matcher s = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*==\\s*\"").matcher(b);
                if (s.find() || b.matches(".*\"[^\"]*\"\\s*==.*")) {
                    Peringatan p = new Peringatan();
                    p.baris = i + 1;
                    p.jenis = "Perbandingan String";
                    p.penjelasan = "String dibandingkan dengan ==, pakai equals supaya isinya yang dibandingkan.";
                    hasil.add(p);
                }
            }
            if ((b.contains("new FileInputStream") || b.contains("new FileOutputStream")
                    || b.contains(".openConnection()")) && !b.contains("try (")) {
                Peringatan p = new Peringatan();
                p.baris = i + 1;
                p.jenis = "Sumber daya tidak ditutup";
                p.penjelasan = "Stream dibuka tanpa try-with-resources atau close; bisa bocor bila terjadi galat.";
                hasil.add(p);
            }
            Matcher t = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=[^=]").matcher(b);
            if (t.find() && kini.length() > 0 && !b.contains("==")) {
                String nama = t.group(1);
                int idx = fieldTulis.indexOf(nama);
                if (idx >= 0 && !metodeTulis.get(idx).equals(kini)) {
                    Peringatan p = new Peringatan();
                    p.baris = i + 1;
                    p.jenis = "Potensi balapan data";
                    p.penjelasan = "Variabel " + nama + " ditulis di metode "
                            + metodeTulis.get(idx) + " dan " + kini
                            + "; bila dua metode jalan bersamaan, nilainya bisa berebut.";
                    hasil.add(p);
                } else if (idx < 0) {
                    fieldTulis.add(nama);
                    metodeTulis.add(kini);
                }
            }
        }
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahLangkah, int jumlahPeringatan) {
        return "Langkah alur: " + jumlahLangkah + ". Peringatan ditemukan: "
                + jumlahPeringatan + ".";
    }
}
