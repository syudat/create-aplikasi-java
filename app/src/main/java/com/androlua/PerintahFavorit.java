package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Perintah favorit Terminal (fitur Terminal #2).
 *
 * Murni Java (tanpa API Android) supaya bisa diuji lewat JRE.
 * Satu favorit = nama yang tampil + perintah yang dijalankan.
 * Disimpan di SharedPreferences "terminal_favorit" (kunci "daftar")
 * sebagai satu String: tiap entri "nama\u0001perintah", entri
 * dipisah "\n". Nama/perintah dibersihkan dulu (tanpa \n, \r,
 * \u0001) supaya format simpanan tidak rusak.
 */
public class PerintahFavorit {
    /** Nama SharedPreferences untuk favorit terminal. */
    public static final String PREFS = "terminal_favorit";
    /** Kunci String daftar ter-encode di SharedPreferences. */
    public static final String KUNCI_DAFTAR = "daftar";
    /** Kunci penanda migrasi dari berkas favorit lama (sudah diimpor). */
    public static final String KUNCI_MIGRASI = "migrasi_file";
    /** Batas jumlah favorit supaya dialog tetap mudah dibaca Jieshuo. */
    public static final int MAKS_FAVORIT = 50;

    private static final char PISAH_NAMA = '\u0001';
    private static final char PISAH_ENTRI = '\n';

    private PerintahFavorit() {
    }

    /** Satu perintah favorit: nama yang tampil + perintah yang dijalankan. */
    public static class Favorit {
        public final String nama;
        public final String perintah;

        public Favorit(String nama, String perintah) {
            this.nama = nama;
            this.perintah = perintah;
        }
    }

    /** Tiga favorit bawaan yang berguna (pengguna boleh menghapusnya). */
    public static List<Favorit> bawaan() {
        List<Favorit> d = new ArrayList<>();
        d.add(new Favorit("Lihat berkas", "ls -la"));
        d.add(new Favorit(TeksUI.t("Cek ruang"), "df -h"));
        d.add(new Favorit("Bersihkan layar", "clear"));
        return d;
    }

    /** Bersihkan teks: pangkas spasi, buang \n \r dan pemisah internal. */
    public static String bersihkan(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replace('\n', ' ').replace('\r', ' ').replace(PISAH_NAMA, ' ');
        return t.trim();
    }

    /** Cari favorit berdasarkan nama (nama dibersihkan dulu). */
    public static Favorit cari(List<Favorit> daftar, String nama) {
        if (daftar == null) return null;
        String n = bersihkan(nama);
        if (n.isEmpty()) return null;
        for (Favorit f : daftar) {
            if (f.nama.equals(n)) return f;
        }
        return null;
    }

    /**
     * Tambah favorit baru; nama yang sama MENIMPA perintah lama.
     * false bila nama/perintah kosong, atau daftar sudah penuh (50)
     * dengan nama yang belum ada.
     */
    public static boolean tambah(List<Favorit> daftar, String nama, String perintah) {
        if (daftar == null) return false;
        String n = bersihkan(nama);
        String p = bersihkan(perintah);
        if (n.isEmpty() || p.isEmpty()) return false;
        Favorit lama = cari(daftar, n);
        if (lama != null) {
            daftar.set(daftar.indexOf(lama), new Favorit(n, p));
            return true;
        }
        if (daftar.size() >= MAKS_FAVORIT) return false;
        daftar.add(new Favorit(n, p));
        return true;
    }

    /** Hapus favorit berdasarkan nama; false bila nama tidak ada. */
    public static boolean hapus(List<Favorit> daftar, String nama) {
        if (daftar == null) return false;
        Favorit f = cari(daftar, nama);
        if (f == null) return false;
        daftar.remove(f);
        return true;
    }

    /** Encode daftar jadi satu String untuk SharedPreferences. */
    public static String encode(List<Favorit> daftar) {
        StringBuilder sb = new StringBuilder();
        if (daftar == null) return "";
        for (Favorit f : daftar) {
            if (sb.length() > 0) sb.append(PISAH_ENTRI);
            sb.append(f.nama).append(PISAH_NAMA).append(f.perintah);
        }
        return sb.toString();
    }

    /** Decode String simpanan; entri rusak dilewati (tidak crash). */
    public static List<Favorit> decode(String teks) {
        List<Favorit> d = new ArrayList<>();
        if (teks == null || teks.isEmpty()) return d;
        for (String entri : teks.split("\n")) {
            int p = entri.indexOf(PISAH_NAMA);
            if (p <= 0) continue;
            String n = bersihkan(entri.substring(0, p));
            String c = bersihkan(entri.substring(p + 1));
            if (n.isEmpty() || c.isEmpty()) continue;
            if (cari(d, n) == null) d.add(new Favorit(n, c));
        }
        return d;
    }
}
