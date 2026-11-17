package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pratinjau Langsung ke HP Teman (Tahap 3D).
 *
 * Membuat paket pratinjau KECIL dari satu proyek: hanya berkas utama
 * (main.lua / index.html / MainActivity.java|kt) + BACA-SAYA.txt berisi
 * petunjuk + info.txt. Teman yang menerima bisa langsung membuka
 * pratinjau: halaman HTML dibuka di browser, kode Lua/Java/Kotlin
 * dibaca sebagai teks.
 *
 * Murni Java (java.io/java.util.zip, tanpa API Android) supaya bisa
 * diuji ulang di JRE. ProjectsActivity membagikan hasil lewat
 * ACTION_SEND (FileProvider), sama seperti Bagikan di Tahap 3C.
 */
public class Pratinjau {

    /** Membaca satu kunci dari init.lua (kunci="nilai", komentar -- dibuang). */
    public static String bacaKunci(File dirProyek, String kunci) {
        if (dirProyek == null || kunci == null) return null;
        File init = new File(dirProyek, "init.lua");
        if (!init.isFile()) return null;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(init), "UTF-8"));
            String baris;
            while ((baris = r.readLine()) != null) {
                String t = baris.trim();
                int komentar = t.indexOf("--");
                if (komentar >= 0) t = t.substring(0, komentar).trim();
                if (!t.startsWith(kunci + "=")) continue;
                String nilai = t.substring(kunci.length() + 1).trim();
                // Nilai berbentuk "teks" (kutip ganda) atau angka/boolean.
                if (nilai.startsWith("\"") && nilai.endsWith("\"") && nilai.length() >= 2) {
                    nilai = nilai.substring(1, nilai.length() - 1);
                }
                return nilai;
            }
        } catch (IOException ignored) {
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    /** Jenis proyek dari init.lua ("lua" bila tidak tertulis). */
    public static String bacaTipe(File dirProyek) {
        String tipe = bacaKunci(dirProyek, "tipe");
        if (tipe == null || tipe.isEmpty()) return "lua";
        return tipe;
    }

    /** Jalur relatif berkas utama proyek menurut jenisnya. */
    public static String jalurBerkasUtama(File dirProyek) {
        String tipe = bacaTipe(dirProyek);
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            return "app/src/main/assets/index.html";
        }
        if ("lua".equals(tipe)) {
            return "main.lua";
        }
        if ("python".equals(tipe)) {
            return "app/src/main/python/main.py";
        }
        String paket = bacaKunci(dirProyek, "packagename");
        String jalurPaket = paket == null ? "" : paket.replace('.', '/');
        if ("kotlin".equals(tipe) || "compose".equals(tipe)) {
            return "app/src/main/java/" + jalurPaket + "/MainActivity.kt";
        }
        // java (dan jenis lain yang tidak dikenal: coba MainActivity.java).
        return "app/src/main/java/" + jalurPaket + "/MainActivity.java";
    }

    /** Nama berkas pratinjau untuk satu proyek. */
    public static String namaBerkas(String namaProyek) {
        return namaProyek + "-pratinjau.zip";
    }

    /**
     * Membuat paket pratinjau di dalam dirTujuan (biasanya folder induk
     * proyek, Download/project). Mengembalikan berkas ZIP bila berhasil,
     * null bila gagal (mis. proyek tidak ada atau berkas utama hilang).
     */
    public static File buatPaket(File dirProyek, File dirTujuan) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        if (dirTujuan == null) return null;
        String nama = dirProyek.getName();
        String tipe = bacaTipe(dirProyek);
        File utama = new File(dirProyek, jalurBerkasUtama(dirProyek));
        if (!utama.isFile()) return null;
        dirTujuan.mkdirs();
        File keluar = new File(dirTujuan, namaBerkas(nama));
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(
                    new FileOutputStream(keluar)));
            tambahBerkas(zos, utama, nama + "/" + utama.getName());
            tambahTeks(zos, nama + "/BACA-SAYA.txt", isiBacaSaya(nama, tipe, utama.getName()));
            tambahTeks(zos, nama + "/info.txt", isiInfo(dirProyek, nama, tipe));
            zos.finish();
            return keluar;
        } catch (IOException e) {
            if (keluar.exists()) keluar.delete();
            return null;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Isi BACA-SAYA.txt: petunjuk membuka pratinjau di HP teman. */
    static String isiBacaSaya(String nama, String tipe, String namaBerkasUtama) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pratinjau proyek \"").append(nama).append("\" dari Create Aplikasi.\n\n");
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            sb.append("Cara membuka: ketuk berkas ").append(namaBerkasUtama)
                    .append(" — halaman akan terbuka di browser HP Anda.\n");
        } else if ("lua".equals(tipe)) {
            sb.append("Cara membuka: berkas ").append(namaBerkasUtama)
                    .append(" adalah kode Lua — buka sebagai teks untuk membacanya. ")
                    .append("Untuk menjalankannya, buka proyek ini di aplikasi ")
                    .append("Create Aplikasi.\n");
        } else {
            sb.append("Cara membuka: berkas ").append(namaBerkasUtama)
                    .append(" adalah kode sumber ").append(labelTipe(tipe))
                    .append(" — buka sebagai teks untuk membacanya. Untuk membangun ")
                    .append("aplikasinya, buka proyek ini di aplikasi Create Aplikasi.\n");
        }
        sb.append("\nDikirim dari Create Aplikasi by Al Kautsar.\n");
        return sb.toString();
    }

    /** Isi info.txt: nama, jenis, paket, versi, tanggal. */
    static String isiInfo(File dirProyek, String nama, String tipe) {
        String paket = bacaKunci(dirProyek, "packagename");
        String ver = bacaKunci(dirProyek, "appver");
        String tanggal = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("Nama: ").append(nama).append("\n");
        sb.append("Jenis: ").append(labelTipe(tipe)).append("\n");
        sb.append("Paket: ").append(paket == null ? "-" : paket).append("\n");
        sb.append("Versi: ").append(ver == null ? "-" : ver).append("\n");
        sb.append("Dibuat: ").append(tanggal).append("\n");
        return sb.toString();
    }

    private static String labelTipe(String tipe) {
        if ("lua".equals(tipe)) return "Lua";
        if ("html".equals(tipe)) return "HTML";
        if ("situs".equals(tipe)) return "Situs Web";
        if ("java".equals(tipe)) return "Java";
        if ("kotlin".equals(tipe)) return "Kotlin";
        if ("compose".equals(tipe)) return "Compose";
        if ("python".equals(tipe)) return "Python";
        return tipe;
    }

    private static void tambahBerkas(ZipOutputStream zos, File f, String entri)
            throws IOException {
        ZipEntry e = new ZipEntry(entri);
        zos.putNextEntry(e);
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
        } finally {
            in.close();
        }
        zos.closeEntry();
    }

    private static void tambahTeks(ZipOutputStream zos, String entri, String isi)
            throws IOException {
        ZipEntry e = new ZipEntry(entri);
        zos.putNextEntry(e);
        zos.write(isi.getBytes("UTF-8"));
        zos.closeEntry();
    }
}
