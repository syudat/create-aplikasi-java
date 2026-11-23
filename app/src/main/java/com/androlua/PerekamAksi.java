package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Perekam Aksi (Tahap 3F).
 *
 * Merekam urutan aksi pengguna di dalam IDE ke berkas rekaman.txt
 * (satu aksi per baris: waktu|jenis|target|detail) dan merencanakan
 * pemutaran ulang: aksi yang bisa diotomatisasi dijalankan kembali
 * (buka, jalankan, bangun, ekspor, cadangkan, buat), aksi lain
 * dilaporkan sebagai manual.
 *
 * Format rekaman: "waktuEpoch|jenis|target|detail" — karakter '|' dan
 * baris baru di dalam isi diganti supaya satu aksi tetap satu baris.
 *
 * Murni java.io/java.util (tanpa API Android) supaya bisa diuji ulang
 * di JRE biasa. Pemutaran ulang yang sebenarnya (membuka Activity,
 * memanggil jembatan Lua) ada di PerekamAksiActivity, bukan di sini.
 */
public class PerekamAksi {

    /** Nama berkas rekaman di folder data aplikasi. */
    public static final String NAMA_BERKAS = "rekaman.txt";

    /** Jenis aksi yang bisa diputar ulang secara otomatis. */
    public static final String[] JENIS_DAPAT_DIPUTAR = {
        "buka", "jalankan", "bangun", "ekspor", "cadangkan", "buat",
    };

    /** Satu aksi yang terekam. */
    public static class Aksi {
        /** Waktu perekaman (epoch ms). */
        public final long waktu;
        /** Jenis aksi: buka/jalankan/bangun/ekspor/cadangkan/buat/simpan/hapus. */
        public final String jenis;
        /** Sasaran aksi (biasanya nama proyek). */
        public final String target;
        /** Data tambahan (mis. jenis proyek untuk "buat", nama berkas untuk "simpan"). */
        public final String detail;
        public Aksi(long waktu, String jenis, String target, String detail) {
            this.waktu = waktu;
            this.jenis = jenis == null ? "" : jenis;
            this.target = target == null ? "" : target;
            this.detail = detail == null ? "" : detail;
        }
    }

    /** True bila jenis aksi bisa diputar ulang secara otomatis. */
    public static boolean dapatDiputar(String jenis) {
        if (jenis == null) return false;
        for (int i = 0; i < JENIS_DAPAT_DIPUTAR.length; i++) {
            if (JENIS_DAPAT_DIPUTAR[i].equals(jenis)) return true;
        }
        return false;
    }

    /** Catat satu aksi (ditambah di akhir berkas). */
    public static void catat(File berkas, String jenis, String target, String detail) {
        if (berkas == null || jenis == null || jenis.isEmpty()) return;
        try {
            File induk = berkas.getParentFile();
            if (induk != null) induk.mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(berkas, true), "UTF-8");
            try {
                w.write(System.currentTimeMillis() + "|" + bersih(jenis) + "|"
                        + bersih(target) + "|" + bersih(detail) + "\n");
            } finally {
                w.close();
            }
        } catch (Exception ignored) {
        }
    }

    /** Baca semua aksi yang terekam (urut waktu). */
    public static List<Aksi> baca(File berkas) {
        List<Aksi> hasil = new ArrayList<Aksi>();
        if (berkas == null || !berkas.isFile()) return hasil;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(berkas), "UTF-8"));
            try {
                String baris;
                while ((baris = r.readLine()) != null) {
                    if (baris.trim().isEmpty()) continue;
                    String[] bagian = baris.split("\\|", -1);
                    if (bagian.length < 2) continue;
                    long waktu = 0;
                    try {
                        waktu = Long.parseLong(bagian[0]);
                    } catch (Exception ignored) {
                    }
                    String jenis = bagian.length > 1 ? kembalikan(bagian[1]) : "";
                    String target = bagian.length > 2 ? kembalikan(bagian[2]) : "";
                    String detail = bagian.length > 3 ? kembalikan(bagian[3]) : "";
                    hasil.add(new Aksi(waktu, jenis, target, detail));
                }
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
        }
        return hasil;
    }

    /** Hapus seluruh rekaman. */
    public static void hapus(File berkas) {
        if (berkas != null && berkas.isFile()) berkas.delete();
    }

    /** Jumlah aksi yang terekam. */
    public static int jumlah(File berkas) {
        return baca(berkas).size();
    }

    /** Jumlah aksi yang bisa diputar ulang. */
    public static int jumlahDapatDiputar(File berkas) {
        int n = 0;
        for (Aksi a : baca(berkas)) {
            if (dapatDiputar(a.jenis)) n++;
        }
        return n;
    }

    /** Satu baris tampilan untuk aksi ("12:30:05 — Buka proyek Kalkulator"). */
    public static String tampilkan(Aksi a) {
        if (a == null) return "";
        String jam = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(a.waktu));
        return jam + " — " + namaJenis(a.jenis) + " " + a.target
                + (a.detail.isEmpty() ? "" : " (" + a.detail + ")");
    }

    /** Nama jenis aksi dalam bahasa Indonesia. */
    public static String namaJenis(String jenis) {
        if ("buka".equals(jenis)) return TeksUI.t("Buka proyek");
        if ("jalankan".equals(jenis)) return TeksUI.t("Jalankan proyek");
        if ("bangun".equals(jenis)) return TeksUI.t("Bangun proyek");
        if ("ekspor".equals(jenis)) return TeksUI.t("Ekspor ZIP proyek");
        if ("cadangkan".equals(jenis)) return TeksUI.t("Cadangkan proyek");
        if ("buat".equals(jenis)) return TeksUI.t("Buat proyek");
        if ("simpan".equals(jenis)) return TeksUI.t("Simpan berkas");
        if ("hapus".equals(jenis)) return TeksUI.t("Hapus proyek");
        return jenis;
    }

    /**
     * Rencana pemutaran ulang untuk satu aksi: apa yang akan dilakukan
     * Activity saat memutar ulang (atau kenapa tidak bisa otomatis).
     */
    public static String rencana(Aksi a) {
        if (a == null) return TeksUI.t("Aksi kosong.");
        if ("buka".equals(a.jenis)) {
            return TeksUI.t("Dapat diputar: membuka editor proyek ") + a.target + ".";
        }
        if ("jalankan".equals(a.jenis)) {
            return TeksUI.t("Dapat diputar: menjalankan proyek ") + a.target + ".";
        }
        if ("bangun".equals(a.jenis)) {
            return TeksUI.t("Dapat diputar: membangun proyek ") + a.target + ".";
        }
        if ("ekspor".equals(a.jenis)) {
            return TeksUI.t("Dapat diputar: mengekspor ZIP proyek ") + a.target + ".";
        }
        if ("cadangkan".equals(a.jenis)) {
            return TeksUI.t("Dapat diputar: mencadangkan proyek ") + a.target + ".";
        }
        if ("buat".equals(a.jenis)) {
            return TeksUI.t("Dapat diputar: membuat proyek ") + a.target
                    + " (jenis " + (a.detail.isEmpty() ? "lua" : a.detail) + ").";
        }
        if ("simpan".equals(a.jenis)) {
            return TeksUI.t("Manual: membuka editor proyek ") + a.target
                    + TeksUI.t(" (berkas ") + a.detail + TeksUI.t(" tidak bisa ditulis ulang otomatis).");
        }
        return "Manual: aksi \"" + a.jenis + TeksUI.t("\" tidak bisa diputar ulang otomatis.");
    }

    /** Amankan isi (buang '|' dan baris baru) supaya satu aksi tetap satu baris. */
    static String bersih(String s) {
        if (s == null) return "";
        return s.replace("|", ";").replace("\n", " ").replace("\r", " ");
    }

    /** Kembalikan isi yang sudah dibersihkan (tidak ada yang perlu dikembalikan). */
    static String kembalikan(String s) {
        return s == null ? "" : s;
    }
}
