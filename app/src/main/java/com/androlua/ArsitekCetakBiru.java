package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Arsitek Cetak Biru Proyek dan Pengurai Tugas Bertahap Berbasis AI
 * (Fitur 11, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - buat: gagasan teks -> cetak biru (modul, berkas, tonggak) dari
 *    pola kata kunci + kerangka umum; pengembang meninjau lalu
 *    membangun satu tonggak demi satu tonggak.
 *  - Tonggak bisa ditandai selesai; kemajuan dihitung dari tonggak.
 *
 * BATAS JUJUR: cetak biru disusun dari POLA kata kunci dan kerangka
 * umum di perangkat, bukan dari model AI online — pola yang tidak
 * dikenali memakai kerangka umum (model, penyimpanan, layar, logika).
 * Penyedia AI aktif (Pengaturan AI) belum dipakai untuk menyusun
 * cetak biru; itu tahap berikut.
 */
public class ArsitekCetakBiru {

    /** Satu tonggak kerja. */
    public static class Tonggak {
        public String nama = "";
        public List<String> berkas = new ArrayList<String>();
        public boolean selesai = false;
        public String toString() {
            return (selesai ? "[selesai] " : "[belum] ") + nama;
        }
    }

    /** Satu cetak biru proyek. */
    public static class CetakBiru {
        public String gagasan = "";
        public List<String> modul = new ArrayList<String>();
        public List<String> berkas = new ArrayList<String>();
        public List<Tonggak> tonggak = new ArrayList<Tonggak>();
    }

    private static void tambahTonggak(CetakBiru b, String nama, String[] berkas) {
        Tonggak t = new Tonggak();
        t.nama = nama;
        for (int i = 0; i < berkas.length; i++) {
            t.berkas.add(berkas[i]);
            if (!b.berkas.contains(berkas[i])) b.berkas.add(berkas[i]);
        }
        b.tonggak.add(t);
    }

    /** Susun cetak biru dari gagasan (pola kata kunci + kerangka umum). */
    public static CetakBiru buat(String gagasan) {
        CetakBiru b = new CetakBiru();
        b.gagasan = gagasan == null ? "" : gagasan.trim();
        String kecil = b.gagasan.toLowerCase();
        boolean kasir = kecil.contains("kasir") || kecil.contains("jualan")
                || kecil.contains("toko") || kecil.contains("belanja");
        boolean chat = kecil.contains("chat") || kecil.contains("pesan")
                || kecil.contains("obrol");
        boolean simpanPinjam = kecil.contains("koperasi") || kecil.contains("pinjam")
                || kecil.contains("cicil") || kecil.contains("tabung");
        boolean jadwal = kecil.contains("jadwal") || kecil.contains("transport")
                || kecil.contains("rute") || kecil.contains("tiket");
        boolean kamus = kecil.contains("kamus") || kecil.contains("belajar")
                || kecil.contains("kuis") || kecil.contains("soal");

        b.modul.add("Model data");
        b.modul.add("Penyimpanan lokal");
        b.modul.add("Layar utama");
        b.modul.add("Logika bisnis");
        if (kasir) b.modul.add("Keranjang dan pembayaran");
        if (chat) b.modul.add("Pesan dan notifikasi");
        if (simpanPinjam) b.modul.add("Perhitungan bunga");
        if (jadwal) b.modul.add("Jadwal dan rute");
        if (kamus) b.modul.add("Bank soal");

        tambahTonggak(b, "Model data dan penyimpanan",
                new String[]{"model/Model.java", "data/Penyimpanan.java"});
        if (simpanPinjam || kasir) {
            tambahTonggak(b, "Perhitungan",
                    new String[]{"logika/Hitungan.java"});
        }
        if (chat) {
            tambahTonggak(b, "Pesan",
                    new String[]{"logika/Pesan.java", "layar/LayarChat.java"});
        }
        if (jadwal) {
            tambahTonggak(b, "Jadwal dan rute",
                    new String[]{"model/Jadwal.java", "layar/LayarJadwal.java"});
        }
        if (kamus) {
            tambahTonggak(b, "Bank soal dan kuis",
                    new String[]{"model/Soal.java", "logika/Kuis.java"});
        }
        tambahTonggak(b, "Layar utama",
                new String[]{"layar/LayarUtama.java", "res/layout/utama.xml"});
        tambahTonggak(b, "Logika bisnis",
                new String[]{"logika/Logika.java"});
        tambahTonggak(b, "Pengujian dan rilis",
                new String[]{"uji/UjiLogika.java"});
        return b;
    }

    /** Tandai tonggak selesai (false bila nomor tidak sah). */
    public static boolean tandaiSelesai(CetakBiru b, int nomorTonggak) {
        if (b == null || nomorTonggak < 1 || nomorTonggak > b.tonggak.size()) return false;
        b.tonggak.get(nomorTonggak - 1).selesai = true;
        return true;
    }

    /** Kemajuan: persen tonggak selesai (0-100). */
    public static int kemajuan(CetakBiru b) {
        if (b == null || b.tonggak.size() == 0) return 0;
        int selesai = 0;
        for (int i = 0; i < b.tonggak.size(); i++) {
            if (b.tonggak.get(i).selesai) selesai++;
        }
        return selesai * 100 / b.tonggak.size();
    }

    /** Tonggak berikutnya yang belum selesai (null bila semua selesai). */
    public static Tonggak tonggakBerikutnya(CetakBiru b) {
        if (b == null) return null;
        for (int i = 0; i < b.tonggak.size(); i++) {
            if (!b.tonggak.get(i).selesai) return b.tonggak.get(i);
        }
        return null;
    }

    /** Ekspor cetak biru sebagai teks terstruktur. */
    public static String eksporTeks(CetakBiru b) {
        if (b == null) return "";
        StringBuilder s = new StringBuilder();
        s.append("Cetak biru: ").append(b.gagasan).append("\n");
        s.append("Modul:\n");
        for (int i = 0; i < b.modul.size(); i++) {
            s.append("- ").append(b.modul.get(i)).append("\n");
        }
        s.append("Tonggak:\n");
        for (int i = 0; i < b.tonggak.size(); i++) {
            Tonggak t = b.tonggak.get(i);
            s.append((i + 1)).append(". ").append(t.toString()).append("\n");
            for (int j = 0; j < t.berkas.size(); j++) {
                s.append("   - ").append(t.berkas.get(j)).append("\n");
            }
        }
        s.append("Kemajuan: ").append(kemajuan(b)).append("%\n");
        return s.toString();
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(CetakBiru b) {
        if (b == null) return "Tonggak dibuat: 0. Berkas direncanakan: 0.";
        return "Tonggak dibuat: " + b.tonggak.size() + ". Berkas direncanakan: "
                + b.berkas.size() + ". Kemajuan: " + kemajuan(b) + "%.";
    }

    /**
     * Urai kembali teks hasil eksporTeks (cetak-biru.txt tersimpan)
     * menjadi cetak biru, termasuk status selesai tiap tonggak dan
     * daftar berkasnya. Baris yang tidak dikenali dilewati.
     */
    public static CetakBiru uraiTeks(String teks) {
        CetakBiru b = new CetakBiru();
        if (teks == null) return b;
        String[] baris = teks.split("\n", -1);
        Tonggak kini = null;
        boolean diModul = false;
        boolean diTonggak = false;
        for (int i = 0; i < baris.length; i++) {
            String s = baris[i];
            if (s.startsWith("Cetak biru: ")) {
                b.gagasan = s.substring("Cetak biru: ".length());
                continue;
            }
            if (s.equals("Modul:")) {
                diModul = true;
                diTonggak = false;
                continue;
            }
            if (s.equals("Tonggak:")) {
                diModul = false;
                diTonggak = true;
                continue;
            }
            if (s.startsWith("Kemajuan: ")) {
                diModul = false;
                diTonggak = false;
                continue;
            }
            if (diModul && s.startsWith("- ")) {
                b.modul.add(s.substring(2));
                continue;
            }
            if (diTonggak) {
                if (s.startsWith("   - ")) {
                    String berkas = s.substring(5);
                    if (kini != null) kini.berkas.add(berkas);
                    if (!b.berkas.contains(berkas)) b.berkas.add(berkas);
                    continue;
                }
                int titik = s.indexOf(". ");
                if (titik > 0) {
                    String isi = s.substring(titik + 2);
                    Tonggak t = new Tonggak();
                    if (isi.startsWith("[selesai] ")) {
                        t.selesai = true;
                        t.nama = isi.substring("[selesai] ".length());
                    } else if (isi.startsWith("[belum] ")) {
                        t.selesai = false;
                        t.nama = isi.substring("[belum] ".length());
                    } else {
                        t.nama = isi;
                    }
                    b.tonggak.add(t);
                    kini = t;
                }
            }
        }
        return b;
    }
}
