package com.androlua;

import android.content.Context;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
 * History build (GitHub Actions / perangkat) — reorganisasi pengaturan 2026-11-17.
 *
 * Murni berkas teks di folder pribadi aplikasi (pola RiwayatTerminal):
 *  - riwayat-build-github.txt    (maks 100 entri terakhir)
 *  - riwayat-build-perangkat.txt (maks 100 entri terakhir)
 * Satu baris per build: "tanggal jam | nama proyek | hasil | keterangan".
 * Dicatat hanya bila centang "Simpan build ke history" di kategori
 * pengaturan yang bersangkutan aktif.
 */
public class RiwayatBuild {
    public static final String JENIS_GITHUB = "github";
    public static final String JENIS_PERANGKAT = "perangkat";
    public static final String NAMA_GITHUB = "riwayat-build-github.txt";
    public static final String NAMA_PERANGKAT = "riwayat-build-perangkat.txt";
    public static final int MAKS_ENTRI = 100;

    private RiwayatBuild() {
    }

    private static File berkas(Context ctx, String jenis) {
        String nama = JENIS_PERANGKAT.equals(jenis) ? NAMA_PERANGKAT : NAMA_GITHUB;
        return new File(ctx.getFilesDir(), nama);
    }

    /** Muat semua entri history (paling lama dulu; tampil dibalik pemanggil). */
    public static List<String> muat(Context ctx, String jenis) {
        return muat(berkas(ctx, jenis));
    }

    /** Muat dari berkas langsung (dipakai uji JRE; tanpa Context). */
    static List<String> muat(File f) {
        List<String> hasil = new ArrayList<>();
        if (f == null || !f.exists()) return hasil;
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String baris;
            while ((baris = r.readLine()) != null) {
                if (!baris.trim().isEmpty()) hasil.add(baris);
            }
            r.close();
        } catch (Exception ignored) {
        }
        return hasil;
    }

    /** Tambah satu entri history (tanggal jam | proyek | hasil | keterangan). */
    public static void tambah(Context ctx, String jenis, String proyek,
                              String hasil, String keterangan) {
        tambah(berkas(ctx, jenis), proyek, hasil, keterangan);
    }

    /** Tambah ke berkas langsung (dipakai uji JRE; tanpa Context). */
    static void tambah(File f, String proyek, String hasil, String keterangan) {
        try {
            List<String> lama = muat(f);
            String waktu = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(new Date());
            String baris = waktu + " | " + proyek + " | " + hasil
                    + (keterangan == null || keterangan.isEmpty() ? ""
                            : " | " + keterangan);
            lama.add(baris);
            while (lama.size() > MAKS_ENTRI) lama.remove(0);
            simpan(f, lama);
        } catch (Exception ignored) {
        }
    }

    /** Tulis seluruh daftar ke berkas (menimpa). */
    public static boolean simpan(Context ctx, String jenis, List<String> daftar) {
        return simpan(berkas(ctx, jenis), daftar);
    }

    /** Tulis ke berkas langsung (dipakai uji JRE; tanpa Context). */
    static boolean simpan(File f, List<String> daftar) {
        if (f == null) return false;
        try {
            File induk = f.getParentFile();
            if (induk != null && !induk.exists()) induk.mkdirs();
            BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f), "UTF-8"));
            for (String s : daftar) {
                w.write(s);
                w.newLine();
            }
            w.close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Hapus seluruh history jenis ini. */
    public static boolean hapus(Context ctx, String jenis) {
        return hapus(berkas(ctx, jenis));
    }

    /** Hapus berkas langsung (dipakai uji JRE; tanpa Context). */
    static boolean hapus(File f) {
        try {
            return f != null && f.exists() && f.delete();
        } catch (Exception ignored) {
            return false;
        }
    }
}
