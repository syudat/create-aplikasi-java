package com.androlua;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesin Refactoring Semantik Lintas-Berkas dan Navigasi Pohon Sintaks AST
 * (Fitur 5, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 * pengindeks berbasis pola teks untuk Java/Kotlin/Python — definisi
 * kelas, metode, dan fungsi; pencarian pemanggil; ganti nama aman;
 * ekstraksi blok menjadi fungsi baru.
 *
 * BATAS JUJUR: ini pengindeks POLA TEKS, bukan parser AST penuh —
 * ganti nama hanya menyentuh kemunculan kata utuh di berkas yang
 * sama kelas pemiliknya (untuk metode) atau di semua berkas (untuk
 * kelas). Kode yang memakai refleksi atau nama yang sama di kelas lain
 * tidak disentuh; selalu periksa pratinjau sebelum menerapkan.
 */
public class RefaktorSemantik {

    /** Satu simbol: definisi kelas/metode/fungsi/variabel. */
    public static class Simbol {
        public String jenis = "";
        public String nama = "";
        public String kelasPemilik = "";
        public String berkas = "";
        public int baris = 0;
        public String toString() {
            return jenis + " " + nama + " (" + berkas + ":" + baris + ")";
        }
    }

    /** Satu kemunculan pemanggil: berkas + baris + cuplikan. */
    public static class Pemanggil {
        public String berkas = "";
        public int baris = 0;
        public String cuplikan = "";
        public String toString() {
            return berkas + ":" + baris + " " + cuplikan.trim();
        }
    }

    private static final Pattern KELAS = Pattern.compile(
            "\\b(?:class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern METODE_JAVA = Pattern.compile(
            "\\b(?:public|private|protected|static|final|synchronized|abstract|void|int|long|double|float|boolean|String|char|byte|short|\\w+)\\b[^;\\{\\}]*?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern FUNGSI_PY = Pattern.compile(
            "^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern FUNGSI_KT = Pattern.compile(
            "\\bfun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    /** Pindai satu berkas kode menjadi daftar simbol. */
    public static List<Simbol> pindaiBerkas(String namaBerkas, String isi) {
        List<Simbol> hasil = new ArrayList<Simbol>();
        if (isi == null) return hasil;
        String[] baris = isi.split("\n");
        String kelasKini = "";
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            String rapi = b.trim();
            if (rapi.startsWith("//") || rapi.startsWith("#") || rapi.startsWith("*")) continue;
            Matcher m = KELAS.matcher(b);
            if (m.find()) {
                kelasKini = m.group(1);
                Simbol s = new Simbol();
                s.jenis = "kelas";
                s.nama = kelasKini;
                s.kelasPemilik = "";
                s.berkas = namaBerkas;
                s.baris = i + 1;
                hasil.add(s);
                continue;
            }
            Matcher f = null;
            String jenis = "metode";
            if (namaBerkas != null && namaBerkas.endsWith(".py")) {
                f = FUNGSI_PY.matcher(b);
                jenis = "fungsi";
            } else if (namaBerkas != null && namaBerkas.endsWith(".kt")) {
                f = FUNGSI_KT.matcher(b);
                jenis = "fungsi";
            } else {
                f = METODE_JAVA.matcher(b);
            }
            if (f != null && f.find()) {
                String nama = f.group(1);
                if ("if".equals(nama) || "for".equals(nama) || "while".equals(nama)
                        || "switch".equals(nama) || "return".equals(nama)
                        || "catch".equals(nama)) continue;
                Simbol s = new Simbol();
                s.jenis = jenis;
                s.nama = nama;
                s.kelasPemilik = kelasKini;
                s.berkas = namaBerkas;
                s.baris = i + 1;
                hasil.add(s);
            }
        }
        return hasil;
    }

    /** Pindai seluruh berkas kode di bawah folder (rekursif, 6 ekstensi). */
    public static List<Simbol> pindaiProyek(File dir) {
        List<Simbol> hasil = new ArrayList<Simbol>();
        if (dir == null || !dir.isDirectory()) return hasil;
        pindaiKe(dir, dir, hasil);
        return hasil;
    }

    private static void pindaiKe(File akar, File kini, List<Simbol> keluar) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) pindaiKe(akar, f, keluar);
            } else if (akhirKode(f.getName())) {
                String isi = bacaTeks(f);
                if (isi == null) continue;
                String relatif = f.getAbsolutePath().substring(
                        akar.getAbsolutePath().length() + 1);
                keluar.addAll(pindaiBerkas(relatif, isi));
            }
        }
    }

    /** True bila nama berkas adalah kode sumber yang dipindai. */
    public static boolean akhirKode(String nama) {
        if (nama == null) return false;
        return nama.endsWith(".java") || nama.endsWith(".kt")
                || nama.endsWith(".py") || nama.endsWith(".js")
                || nama.endsWith(".lua") || nama.endsWith(".xml");
    }

    static String bacaTeks(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Cari definisi simbol bernama tertentu (pertama yang cocok). */
    public static Simbol cariDefinisi(List<Simbol> semua, String nama) {
        if (semua == null || nama == null) return null;
        for (int i = 0; i < semua.size(); i++) {
            if (nama.equals(semua.get(i).nama)) return semua.get(i);
        }
        return null;
    }

    /**
     * Cari semua pemanggil metode/fungsi: baris yang memanggil
     * nama(...) di luar baris definisinya sendiri.
     */
    public static List<Pemanggil> cariPemanggil(List<String[]> berkasIsi, Simbol target) {
        List<Pemanggil> hasil = new ArrayList<Pemanggil>();
        if (berkasIsi == null || target == null) return hasil;
        Pattern p = Pattern.compile("\\b" + Pattern.quote(target.nama) + "\\s*\\(");
        for (int i = 0; i < berkasIsi.size(); i++) {
            String namaBerkas = berkasIsi.get(i)[0];
            String isi = berkasIsi.get(i)[1];
            if (isi == null) continue;
            String[] baris = isi.split("\n");
            for (int j = 0; j < baris.length; j++) {
                if (namaBerkas.equals(target.berkas) && j + 1 == target.baris) continue;
                if (p.matcher(baris[j]).find()) {
                    Pemanggil k = new Pemanggil();
                    k.berkas = namaBerkas;
                    k.baris = j + 1;
                    k.cuplikan = baris[j].trim();
                    hasil.add(k);
                }
            }
        }
        return hasil;
    }

    /**
     * Ganti nama simbol secara aman di satu isi berkas: hanya kata
     * utuh (batas kata) yang diganti. Untuk metode, pemakaian di luar
     * berkas pemilik TIDAK diubah di sini — pemanggil lintas berkas
     * harus diganti lewat daftar pemanggil satu per satu.
     */
    public static String gantiNama(String isi, String lama, String baru) {
        if (isi == null || lama == null || baru == null) return isi;
        if (lama.length() == 0 || lama.equals(baru)) return isi;
        return isi.replaceAll("\\b" + Pattern.quote(lama) + "\\b",
                Matcher.quoteReplacement(baru));
    }

    /** Hitung kemunculan kata utuh di isi berkas. */
    public static int hitungKemunculan(String isi, String kata) {
        if (isi == null || kata == null || kata.length() == 0) return 0;
        Matcher m = Pattern.compile("\\b" + Pattern.quote(kata) + "\\b").matcher(isi);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /**
     * Ekstrak blok baris menjadi fungsi baru: baris awal..akhir
     * (1-based, inklusif) dipindah ke fungsi baru, dan di tempat asal
     * tinggal pemanggilan. Mengembalikan kode baru (null bila rentang
     * tidak sah).
     */
    public static String ekstrakFungsi(String isi, int barisAwal, int barisAkhir,
                                       String namaFungsi) {
        if (isi == null || namaFungsi == null || namaFungsi.length() == 0) return null;
        String[] baris = isi.split("\n");
        if (barisAwal < 1 || barisAkhir < barisAwal || barisAkhir > baris.length) return null;
        StringBuilder keluar = new StringBuilder();
        StringBuilder fungsi = new StringBuilder();
        fungsi.append("    public static void ").append(namaFungsi).append("() {\n");
        for (int i = 0; i < baris.length; i++) {
            int nomor = i + 1;
            if (nomor >= barisAwal && nomor <= barisAkhir) {
                fungsi.append("        ").append(baris[i].trim()).append("\n");
                if (nomor == barisAwal) {
                    keluar.append("        ").append(namaFungsi).append("();\n");
                }
            } else {
                keluar.append(baris[i]).append("\n");
            }
        }
        fungsi.append("    }\n");
        keluar.append("\n").append(fungsi.toString());
        return keluar.toString();
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahSimbol, int jumlahPemanggil) {
        return "Simbol ditemukan: " + jumlahSimbol + ". Pemanggil ditemukan: "
                + jumlahPemanggil + ".";
    }
}
