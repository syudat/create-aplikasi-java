package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Mengekspor satu proyek pengguna menjadi berkas ZIP.
 *
 * Isi ZIP: seluruh isi folder proyek, dengan nama folder proyek sebagai entri
 * paling atas (mis. "ContohJava/init.lua", "ContohJava/app/src/...").
 * Yang sengaja TIDAK ikut:
 *   - folder bin/ di tingkat atas proyek (hasil build: APK + log, ukurannya
 *     besar dan bisa dibuat ulang kapan saja),
 *   - berkas *.zip yang sudah ada di dalam proyek (supaya ekspor ulang tidak
 *     membengkak karena ZIP lama ikut terzip lagi).
 *
 * Hasil disimpan di sebelah folder proyek: Download/project/<NamaProyek>.zip
 * (bukan di dalam folder proyek itu sendiri, supaya tidak ikut terzip).
 * Mengembalikan jalur absolut ZIP bila berhasil, null bila gagal.
 */
public class EksporProyek {

    public static String ekspor(String projectDir) {
        File proyek = new File(projectDir);
        if (!proyek.isDirectory()) return null;
        File keluar = new File(proyek.getParentFile(), proyek.getName() + ".zip");
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(keluar)));
            tambah(zos, proyek, proyek.getName() + "/");
            zos.finish();
            return keluar.getAbsolutePath();
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

    private static void tambah(ZipOutputStream zos, File file, String entri) throws IOException {
        if (file.isDirectory()) {
            File[] anak = file.listFiles();
            if (anak == null) return;
            for (File a : anak) {
                // Lewati folder hasil build (bin/) di tingkat atas proyek.
                if (a.isDirectory() && a.getName().equals("bin") && a.getParentFile().equals(file)
                        && entri.equals(file.getName() + "/")) continue;
                tambah(zos, a, entri + a.getName() + (a.isDirectory() ? "/" : ""));
            }
            return;
        }
        if (file.getName().endsWith(".zip")) return;
        ZipEntry e = new ZipEntry(entri);
        zos.putNextEntry(e);
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
        } finally {
            in.close();
        }
        zos.closeEntry();
    }
}
