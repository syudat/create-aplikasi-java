package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Manajer Dependensi Maven Luring dan Penyelesai Konflik Pustaka
 * (Fitur 7, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Brankas luring: indeks teks "koordinat|ukuran|dep1,dep2" per
 *    baris; tambah, cari, dan daftar pustaka yang sudah diunduh.
 *  - Penyelesai konflik: dua pustaka yang meminta versi berbeda dari
 *    artefak yang sama diselaraskan ke versi tertinggi yang cocok.
 *  - Urai baris dependensi Gradle ("implementation 'g:a:v'").
 *
 * BATAS JUJUR: pengunduhan dari Maven/Google/PyPI butuh internet —
 * saat luring hanya pustaka yang SUDAH ada di brankas yang bisa
 * dipakai. Pemeriksaan kecocokan memakai perbandingan versi angka
 * (semantik sederhana), bukan grafik dependensi transitif penuh.
 */
public class ManajerDependensi {

    /** Satu pustaka: koordinat Maven "grup:artefak:versi". */
    public static class Pustaka {
        public String grup = "";
        public String artefak = "";
        public String versi = "";
        public long ukuran = 0;
        public List<String> dependensi = new ArrayList<String>();

        public String koordinat() {
            return grup + ":" + artefak + ":" + versi;
        }

        public String kunci() {
            return grup + ":" + artefak;
        }

        public String toString() {
            return koordinat() + " (" + ukuran + " bita)";
        }
    }

    /** Satu bentrokan versi: artefak + versi-versi yang bertabrakan. */
    public static class Bentrokan {
        public String kunci = "";
        public List<String> versi = new ArrayList<String>();
        public String dipilih = "";
        public String toString() {
            return kunci + ": " + versi + " -> " + dipilih;
        }
    }

    /** Bandingkan dua versi angka ("1.2.3" vs "1.10.0"): -1/0/1. */
    public static int bandingVersi(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            int na = angkaAtau(sa);
            int nb = angkaAtau(sb);
            if (na != nb) return na < nb ? -1 : 1;
            if (na == -1 && !sa.equals(sb)) return sa.compareTo(sb);
        }
        return 0;
    }

    private static int angkaAtau(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Urai koordinat "grup:artefak:versi" (null bila bentuk salah). */
    public static Pustaka uraiKoordinat(String koordinat) {
        if (koordinat == null) return null;
        String[] bagian = koordinat.trim().split(":");
        if (bagian.length != 3) return null;
        Pustaka p = new Pustaka();
        p.grup = bagian[0].trim();
        p.artefak = bagian[1].trim();
        p.versi = bagian[2].trim();
        if (p.grup.length() == 0 || p.artefak.length() == 0 || p.versi.length() == 0) return null;
        return p;
    }

    /** Urai baris Gradle: implementation 'g:a:v' / api("g:a:v"). */
    public static Pustaka uraiGradle(String baris) {
        if (baris == null) return null;
        int awal = baris.indexOf('\'');
        int akhir = awal >= 0 ? baris.indexOf('\'', awal + 1) : -1;
        if (awal < 0 || akhir < 0) {
            awal = baris.indexOf('"');
            akhir = awal >= 0 ? baris.indexOf('"', awal + 1) : -1;
        }
        if (awal < 0 || akhir < 0) return null;
        return uraiKoordinat(baris.substring(awal + 1, akhir));
    }

    /** Serialisasi satu pustaka ke baris indeks brankas. */
    public static String keBarisIndeks(Pustaka p) {
        StringBuilder b = new StringBuilder();
        b.append(p.koordinat()).append("|").append(p.ukuran).append("|");
        for (int i = 0; i < p.dependensi.size(); i++) {
            if (i > 0) b.append(",");
            b.append(p.dependensi.get(i));
        }
        return b.toString();
    }

    /** Urai baris indeks brankas (null bila bentuk salah). */
    public static Pustaka dariBarisIndeks(String baris) {
        if (baris == null) return null;
        String[] bagian = baris.split("\\|", -1);
        if (bagian.length < 2) return null;
        Pustaka p = uraiKoordinat(bagian[0]);
        if (p == null) return null;
        try {
            p.ukuran = Long.parseLong(bagian[1].trim());
        } catch (Throwable t) {
            p.ukuran = 0;
        }
        if (bagian.length >= 3 && bagian[2].trim().length() > 0) {
            String[] dep = bagian[2].split(",");
            for (int i = 0; i < dep.length; i++) {
                if (dep[i].trim().length() > 0) p.dependensi.add(dep[i].trim());
            }
        }
        return p;
    }

    /** Urai seluruh isi indeks brankas menjadi daftar pustaka. */
    public static List<Pustaka> uraiIndeks(String isi) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (isi == null) return hasil;
        String[] baris = isi.split("\n");
        for (int i = 0; i < baris.length; i++) {
            Pustaka p = dariBarisIndeks(baris[i].trim());
            if (p != null) hasil.add(p);
        }
        return hasil;
    }

    /** Cari pustaka di brankas berdasarkan artefak (tidak peka huruf). */
    public static List<Pustaka> cari(List<Pustaka> brankas, String kata) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (brankas == null || kata == null) return hasil;
        String kecil = kata.toLowerCase();
        for (int i = 0; i < brankas.size(); i++) {
            Pustaka p = brankas.get(i);
            if (p.koordinat().toLowerCase().contains(kecil)) hasil.add(p);
        }
        return hasil;
    }

    /** True bila pustaka sudah ada di brankas (koordinat sama persis). */
    public static boolean sudahAda(List<Pustaka> brankas, Pustaka p) {
        if (brankas == null || p == null) return false;
        for (int i = 0; i < brankas.size(); i++) {
            if (p.koordinat().equals(brankas.get(i).koordinat())) return true;
        }
        return false;
    }

    /**
     * Selesaikan bentrokan: untuk tiap artefak yang diminta dalam
     * lebih dari satu versi, pilih versi tertinggi dan catat
     * bentrokannya. Mengembalikan daftar pustaka terselesaikan
     * (satu per artefak).
     */
    public static List<Pustaka> selesaikan(List<Pustaka> diminta,
                                           List<Bentrokan> bentrokanKeluar) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (diminta == null) return hasil;
        for (int i = 0; i < diminta.size(); i++) {
            Pustaka p = diminta.get(i);
            Pustaka ada = null;
            for (int j = 0; j < hasil.size(); j++) {
                if (hasil.get(j).kunci().equals(p.kunci())) ada = hasil.get(j);
            }
            if (ada == null) {
                hasil.add(p);
                continue;
            }
            if (bandingVersi(p.versi, ada.versi) > 0) {
                Bentrokan b = cariBentrokan(bentrokanKeluar, p.kunci());
                if (b == null) {
                    b = new Bentrokan();
                    b.kunci = p.kunci();
                    b.versi.add(ada.versi);
                    b.versi.add(p.versi);
                    if (bentrokanKeluar != null) bentrokanKeluar.add(b);
                } else if (!b.versi.contains(p.versi)) {
                    b.versi.add(p.versi);
                }
                b.dipilih = p.versi;
                hasil.remove(ada);
                hasil.add(p);
            } else if (bandingVersi(p.versi, ada.versi) < 0) {
                Bentrokan b = cariBentrokan(bentrokanKeluar, p.kunci());
                if (b == null) {
                    b = new Bentrokan();
                    b.kunci = p.kunci();
                    b.versi.add(ada.versi);
                    b.versi.add(p.versi);
                    if (bentrokanKeluar != null) bentrokanKeluar.add(b);
                } else if (!b.versi.contains(p.versi)) {
                    b.versi.add(p.versi);
                }
                b.dipilih = ada.versi;
            }
        }
        return hasil;
    }

    private static Bentrokan cariBentrokan(List<Bentrokan> daftar, String kunci) {
        if (daftar == null) return null;
        for (int i = 0; i < daftar.size(); i++) {
            if (kunci.equals(daftar.get(i).kunci)) return daftar.get(i);
        }
        return null;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahBrankas, List<Bentrokan> bentrokan) {
        int b = bentrokan == null ? 0 : bentrokan.size();
        return "Pustaka di brankas: " + jumlahBrankas + ". Bentrokan ditemukan: " + b + ".";
    }
}
