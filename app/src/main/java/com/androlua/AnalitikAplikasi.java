package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Analitik aplikasi + App Quality Insights (Pro #6 + AS #5, satu paket).
 *
 * Murni Java (tanpa API Android) supaya bisa diuji lewat JRE.
 * Semua data HANYA tersimpan lokal di HP pengguna (SharedPreferences
 * "analitik_aplikasi") dan TIDAK PERNAH dikirim ke server mana pun:
 * jumlah pembukaan aplikasi, kunjungan per layar, dan laporan crash
 * (tanggal, layar terakhir, pesan error, jejak ringkas).
 *
 * Penyimpanan (satu String per kunci, supaya SharedPreferences cukup):
 * - "buka": jumlah pembukaan aplikasi (int, disimpan sebagai String).
 * - "layar": tiap entri "nama\u0001jumlah", entri dipisah "\n".
 * - "crash": tiap entri "tanggal\u0002layar\u0002pesan\u0002jejak",
 *   entri dipisah "\n", entri terbaru di URUTAN PERTAMA.
 * - "layar_terakhir": nama layar yang terakhir dibuka.
 * Teks dibersihkan dulu (tanpa \n, \r, \u0001, \u0002) supaya format
 * simpanan tidak rusak; entri rusak saat dimuat dilewati, tidak crash.
 *
 * Saran perbaikan dihitung dari pesan + jejak crash memakai pola yang
 * SUDAH ADA di proyek ini (aturan thread utama + Runnable dari
 * main.lua / pembuat skrip) — tidak ada aturan baru yang dikarang.
 */
public class AnalitikAplikasi {
    /** Nama SharedPreferences untuk analitik (dipakai Activity). */
    public static final String PREFS = "analitik_aplikasi";
    /** Kunci jumlah pembukaan aplikasi. */
    public static final String KUNCI_BUKA = "buka";
    /** Kunci String kunjungan layar ter-encode. */
    public static final String KUNCI_LAYAR = "layar";
    /** Kunci String laporan crash ter-encode. */
    public static final String KUNCI_CRASH = "crash";
    /** Kunci nama layar terakhir yang dibuka. */
    public static final String KUNCI_LAYAR_TERAKHIR = "layar_terakhir";
    /** Batas laporan crash yang disimpan (yang terbaru dipertahankan). */
    public static final int MAKS_CRASH = 20;
    /** Batas layar berbeda yang dicatat. */
    public static final int MAKS_LAYAR = 50;
    /** Batas baris jejak yang disimpan per crash. */
    public static final int MAKS_JEJAK_BARIS = 6;
    /** Batas panjang tiap baris jejak (karakter). */
    public static final int MAKS_JEJAK_PANJANG = 200;
    /** Jumlah crash terbaru yang ditampilkan di layar. */
    public static final int TAMPIL_CRASH = 5;
    /** Jumlah layar teratas yang ditampilkan di layar. */
    public static final int TAMPIL_LAYAR = 10;

    private static final char PISAH_NAMA = '\u0001';
    private static final char PISAH_BIDANG = '\u0002';
    private static final char PISAH_ENTRI = '\n';

    private AnalitikAplikasi() {
    }

    /** Satu layar + jumlah kunjungannya. */
    public static class Kunjungan {
        public final String nama;
        public final int jumlah;

        public Kunjungan(String nama, int jumlah) {
            this.nama = nama;
            this.jumlah = jumlah;
        }
    }

    /** Satu laporan crash: tanggal, layar terakhir, pesan, jejak ringkas. */
    public static class Crash {
        public final String tanggal;
        public final String layar;
        public final String pesan;
        public final String jejak;

        public Crash(String tanggal, String layar, String pesan, String jejak) {
            this.tanggal = tanggal;
            this.layar = layar;
            this.pesan = pesan;
            this.jejak = jejak;
        }

        /** Saran perbaikan singkat untuk crash ini (dihitung dari pola). */
        public String saran() {
            return saranPerbaikan(pesan, jejak);
        }
    }

    /** Bersihkan teks: pangkas spasi, buang \n \r dan pemisah internal. */
    public static String bersihkan(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\n', ' ').replace('\r', ' ')
                .replace(PISAH_NAMA, ' ').replace(PISAH_BIDANG, ' ');
        return t.trim();
    }

    /** Tambah 1 ke jumlah pembukaan; angka rusak dianggap 0. */
    public static int tambahBuka(int kini) {
        if (kini < 0) return 1;
        if (kini == Integer.MAX_VALUE) return kini;
        return kini + 1;
    }

    /** Baca jumlah pembukaan dari String simpanan; rusak/kosong -> 0. */
    public static int bacaBuka(String teks) {
        if (teks == null) return 0;
        try {
            int n = Integer.parseInt(teks.trim());
            return n < 0 ? 0 : n;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Cari kunjungan berdasarkan nama (nama dibersihkan dulu). */
    public static Kunjungan cari(List<Kunjungan> daftar, String nama) {
        if (daftar == null) return null;
        String n = bersihkan(nama);
        if (n.isEmpty()) return null;
        for (Kunjungan k : daftar) {
            if (k.nama.equals(n)) return k;
        }
        return null;
    }

    /**
     * Catat satu kunjungan layar; layar baru ditambah, layar lama
     * jumlahnya +1. false bila nama kosong, atau daftar sudah penuh (50)
     * dengan nama yang belum ada.
     */
    public static boolean catatLayar(List<Kunjungan> daftar, String nama) {
        if (daftar == null) return false;
        String n = bersihkan(nama);
        if (n.isEmpty()) return false;
        Kunjungan lama = cari(daftar, n);
        if (lama != null) {
            daftar.set(daftar.indexOf(lama),
                    new Kunjungan(n, lama.jumlah + 1));
            return true;
        }
        if (daftar.size() >= MAKS_LAYAR) return false;
        daftar.add(new Kunjungan(n, 1));
        return true;
    }

    /** Layar yang paling sering dibuka (seri: yang pertama tercatat). */
    public static Kunjungan layarFavorit(List<Kunjungan> daftar) {
        if (daftar == null || daftar.isEmpty()) return null;
        Kunjungan terbaik = daftar.get(0);
        for (Kunjungan k : daftar) {
            if (k.jumlah > terbaik.jumlah) terbaik = k;
        }
        return terbaik;
    }

    /** Encode daftar kunjungan jadi satu String untuk SharedPreferences. */
    public static String encodeLayar(List<Kunjungan> daftar) {
        StringBuilder sb = new StringBuilder();
        if (daftar == null) return "";
        for (Kunjungan k : daftar) {
            if (sb.length() > 0) sb.append(PISAH_ENTRI);
            sb.append(k.nama).append(PISAH_NAMA).append(k.jumlah);
        }
        return sb.toString();
    }

    /** Decode String simpanan kunjungan; entri rusak dilewati. */
    public static List<Kunjungan> decodeLayar(String teks) {
        List<Kunjungan> d = new ArrayList<>();
        if (teks == null || teks.isEmpty()) return d;
        for (String entri : teks.split("\n")) {
            int p = entri.indexOf(PISAH_NAMA);
            if (p <= 0) continue;
            String n = bersihkan(entri.substring(0, p));
            int j;
            try {
                j = Integer.parseInt(entri.substring(p + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (n.isEmpty() || j <= 0) continue;
            if (cari(d, n) == null) d.add(new Kunjungan(n, j));
        }
        return d;
    }

    /**
     * Ringkas jejak (stack trace) jadi paling banyak 6 baris, tiap baris
     * paling panjang 200 karakter; baris kosong dibuang.
     */
    public static String ringkasJejak(String jejak) {
        if (jejak == null) return "";
        StringBuilder sb = new StringBuilder();
        int hitung = 0;
        for (String baris : jejak.split("\n")) {
            if (hitung >= MAKS_JEJAK_BARIS) break;
            String b = baris.trim();
            if (b.isEmpty()) continue;
            if (b.length() > MAKS_JEJAK_PANJANG) {
                b = b.substring(0, MAKS_JEJAK_PANJANG);
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(b);
            hitung++;
        }
        return sb.toString();
    }

    /**
     * Tambah laporan crash di URUTAN PERTAMA (terbaru dulu); bila sudah
     * 20, yang paling lama dibuang. false bila tanggal/pesan kosong.
     */
    public static boolean tambahCrash(List<Crash> daftar, String tanggal,
            String layar, String pesan, String jejak) {
        if (daftar == null) return false;
        String t = bersihkan(tanggal);
        String p = bersihkan(pesan);
        if (t.isEmpty() || p.isEmpty()) return false;
        String l = bersihkan(layar);
        if (l.isEmpty()) l = TeksUI.t("Tidak tercatat");
        daftar.add(0, new Crash(t, l, p, ringkasJejak(jejak)));
        while (daftar.size() > MAKS_CRASH) {
            daftar.remove(daftar.size() - 1);
        }
        return true;
    }

    /** Encode daftar crash jadi satu String untuk SharedPreferences. */
    public static String encodeCrash(List<Crash> daftar) {
        StringBuilder sb = new StringBuilder();
        if (daftar == null) return "";
        for (Crash c : daftar) {
            if (sb.length() > 0) sb.append(PISAH_ENTRI);
            sb.append(c.tanggal).append(PISAH_BIDANG)
                    .append(c.layar).append(PISAH_BIDANG)
                    .append(c.pesan).append(PISAH_BIDANG)
                    .append(c.jejak.replace('\n', ' '));
        }
        return sb.toString();
    }

    /** Decode String simpanan crash; entri rusak dilewati. */
    public static List<Crash> decodeCrash(String teks) {
        List<Crash> d = new ArrayList<>();
        if (teks == null || teks.isEmpty()) return d;
        for (String entri : teks.split("\n")) {
            String[] bagian = entri.split("\u0002", -1);
            if (bagian.length < 4) continue;
            String t = bersihkan(bagian[0]);
            String l = bersihkan(bagian[1]);
            String p = bersihkan(bagian[2]);
            if (t.isEmpty() || p.isEmpty()) continue;
            if (l.isEmpty()) l = TeksUI.t("Tidak tercatat");
            d.add(new Crash(t, l, p, bersihkan(bagian[3])));
        }
        return d;
    }

    /**
     * Saran perbaikan singkat dari pesan + jejak crash, memakai pola
     * yang sudah ada di proyek (aturan thread utama + Runnable).
     */
    public static String saranPerbaikan(String pesan, String jejak) {
        String gabung = (bersihkan(pesan) + " " + bersihkan(jejak)).toLowerCase();
        if (gabung.contains("invalid parameters")
                || (gabung.contains("handler") && gabung.contains("post"))) {
            return TeksUI.t("Saran: ini pola Handler.post yang salah. Selalu pakai ")
                    + TeksUI.t("Handler.post(Runnable{run=function() ... end}), jangan ")
                    + TeksUI.t("pernah memberi fungsi mentah ke post atau postDelayed, ")
                    + TeksUI.t("karena fungsi mentah gagal diubah jadi Runnable di ")
                    + TeksUI.t("sebagian perangkat.");
        }
        if (gabung.contains("luadialog") || gabung.contains("dialog")
                || gabung.contains("badtoken")
                || gabung.contains("calledfromwrongthread")
                || gabung.contains("setview")) {
            return TeksUI.t("Saran: dialog hanya boleh dibuat dan ditampilkan dari ")
                    + TeksUI.t("thread utama lewat post(Runnable{...}), dengan urutan ")
                    + TeksUI.t("setTitle lalu setView lalu show(), dan show() serta ")
                    + TeksUI.t("dismiss() dibungkus pcall. Jangan pernah membuat dialog ")
                    + TeksUI.t("dari callback jaringan atau thread latar.");
        }
        if (gabung.contains("nullpointer") || gabung.contains("null pointer")) {
            return TeksUI.t("Saran: ada nilai kosong yang dipakai sebelum diperiksa. ")
                    + TeksUI.t("Periksa dulu nilainya tidak kosong sebelum dipakai, ")
                    + TeksUI.t("dan bungkus pemanggilan yang berisiko dengan pcall ")
                    + TeksUI.t("supaya gagalnya memberi pesan yang jelas.");
        }
        if (gabung.contains("outofmemory")) {
            return TeksUI.t("Saran: memori HP habis. Kecilkan ukuran gambar atau suara ")
                    + TeksUI.t("yang dimuat, dan lepaskan pemutar, listener, dan ")
                    + TeksUI.t("handler saat layar selesai dipakai.");
        }
        if (gabung.contains("classnotfound")
                || gabung.contains("noclassdeffound")) {
            return TeksUI.t("Saran: ada kelas yang tidak ditemukan di APK. Periksa ")
                    + TeksUI.t("nama paket dan import kelas itu, pastikan kelasnya ")
                    + TeksUI.t("ikut terbawa saat build.");
        }
        if (gabung.contains("unknownhost") || gabung.contains("timeout")
                || gabung.contains("network") || gabung.contains("http")) {
            return TeksUI.t("Saran: masalah jaringan. Periksa internet HP, jangan ")
                    + TeksUI.t("menyentuh tampilan langsung dari callback Http, dan ")
                    + TeksUI.t("semua perubahan tampilan dari callback wajib lewat ")
                    + "post(Runnable{...}).";
        }
        return TeksUI.t("Saran: catat langkah terakhir sebelum crash ini, lalu coba ")
                + TeksUI.t("ulangi langkah itu. Bila crash berulang di langkah yang ")
                + TeksUI.t("sama, laporkan pesan error di atas persis seperti tertulis ")
                + TeksUI.t("supaya penyebabnya bisa dicari.");
    }

    /**
     * Teks ringkasan untuk layar (dipakai AnalitikActivity): total
     * pembukaan, layar favorit, jumlah crash, dan crash terakhir.
     */
    public static String formatRingkasan(int buka, List<Kunjungan> layar,
            List<Crash> crash) {
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Dibuka: ")).append(buka).append(TeksUI.t(" kali.") + "\n");
        int jmlLayar = layar == null ? 0 : layar.size();
        sb.append(TeksUI.t("Layar tercatat: ")).append(jmlLayar).append(".\n");
        Kunjungan favorit = layarFavorit(layar);
        if (favorit == null) {
            sb.append(TeksUI.t("Layar favorit: belum ada.") + "\n");
        } else {
            sb.append(TeksUI.t("Layar favorit: ")).append(favorit.nama)
                    .append(" (").append(favorit.jumlah).append(TeksUI.t(" kali).") + "\n");
        }
        int jmlCrash = crash == null ? 0 : crash.size();
        sb.append(TeksUI.t("Jumlah crash: ")).append(jmlCrash).append(".\n");
        if (jmlCrash > 0) {
            Crash terakhir = crash.get(0);
            sb.append(TeksUI.t("Crash terakhir: ")).append(terakhir.tanggal)
                    .append(TeksUI.t(", layar ")).append(terakhir.layar)
                    .append(", ").append(terakhir.pesan).append("\n");
        } else {
            sb.append(TeksUI.t("Crash terakhir: belum ada.") + "\n");
        }
        return sb.toString();
    }
}
