package com.androlua;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Riwayat + perintah favorit Terminal (Tahap 4).
 *
 * Murni Java (tanpa API Android) supaya bisa diuji lewat JRE.
 * Disimpan sebagai berkas teks biasa di folder pribadi aplikasi:
 *  - terminal-riwayat.txt  (maks 200 perintah terakhir)
 *  - terminal-favorit.txt  (perintah yang ditandai favorit)
 */
public class RiwayatTerminal {
    public static final String NAMA_RIWAYAT = "terminal-riwayat.txt";
    public static final String NAMA_FAVORIT = "terminal-favorit.txt";
    public static final int MAKS_RIWAYAT = 200;

    private RiwayatTerminal() {
    }

    /** Muat semua baris dari berkas (berkas kosong/tidak ada = daftar kosong). */
    public static List<String> muat(File berkas) {
        List<String> hasil = new ArrayList<>();
        if (berkas == null || !berkas.exists()) return hasil;
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(berkas), "UTF-8"));
            String baris;
            while ((baris = r.readLine()) != null) {
                if (!baris.trim().isEmpty()) hasil.add(baris);
            }
            r.close();
        } catch (Exception ignored) {
        }
        return hasil;
    }

    /** Tulis seluruh daftar ke berkas (menimpa). */
    public static boolean simpan(File berkas, List<String> daftar) {
        if (berkas == null) return false;
        try {
            File induk = berkas.getParentFile();
            if (induk != null && !induk.exists()) induk.mkdirs();
            BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(berkas, false), "UTF-8"));
            for (String s : daftar) {
                w.write(s);
                w.write("\n");
            }
            w.close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Tambah satu perintah ke riwayat: perintah kosong diabaikan,
     * perintah yang sama dengan yang terakhir tidak diduplikasi,
     * daftar dipotong ke 200 terakhir.
     */
    public static List<String> tambah(File berkas, String perintah) {
        List<String> daftar = muat(berkas);
        if (perintah == null) return daftar;
        String p = perintah.trim();
        if (p.isEmpty()) return daftar;
        if (!daftar.isEmpty() && daftar.get(daftar.size() - 1).equals(p)) return daftar;
        daftar.add(p);
        while (daftar.size() > MAKS_RIWAYAT) daftar.remove(0);
        simpan(berkas, daftar);
        return daftar;
    }

    /** Tambah perintah ke favorit (tanpa duplikat). */
    public static List<String> tambahFavorit(File berkas, String perintah) {
        List<String> daftar = muat(berkas);
        if (perintah == null) return daftar;
        String p = perintah.trim();
        if (p.isEmpty()) return daftar;
        if (!daftar.contains(p)) {
            daftar.add(p);
            simpan(berkas, daftar);
        }
        return daftar;
    }

    /** Hapus satu perintah dari favorit. */
    public static List<String> hapusFavorit(File berkas, String perintah) {
        List<String> daftar = muat(berkas);
        if (daftar.remove(perintah)) simpan(berkas, daftar);
        return daftar;
    }

    /** true bila perintah sudah ada di daftar favorit. */
    public static boolean sudahFavorit(File berkas, String perintah) {
        return perintah != null && muat(berkas).contains(perintah.trim());
    }
}
