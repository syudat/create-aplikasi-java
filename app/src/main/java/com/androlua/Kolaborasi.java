package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Kolaborasi Langsung (Tahap 3D).
 *
 * Alur kirim: proyek di-zip (format sama seperti Ekspor ZIP: folder
 * proyek sebagai entri atas, tanpa bin/, tanpa *.zip) DITAMBAH berkas
 * CATATAN.txt di dalam ZIP yang berisi catatan perubahan dari pengirim
 * (apa yang diubah, siapa, kapan). Alur terima: baca CATATAN.txt dari
 * ZIP balasan, tampilkan catatannya, lalu pengguna memilih Terapkan
 * (timpa proyek lama) atau Batal.
 *
 * Murni Java (java.io/java.util.zip, tanpa API Android) supaya bisa
 * diuji ulang di JRE. KolaborasiActivity memakai buatPaket() +
 * ACTION_SEND (FileProvider) untuk mengirim, dan bacaCatatan() +
 * ArsipProyek.ekstrakZip() untuk menerapkan balasan.
 */
public class Kolaborasi {

    /** Nama berkas catatan perubahan di dalam ZIP kolaborasi. */
    public static final String NAMA_CATATAN = "CATATAN.txt";

    /** Nama berkas ZIP kolaborasi untuk satu proyek. */
    public static String namaBerkas(String namaProyek) {
        return namaProyek + "-kolaborasi.zip";
    }

    /** Isi CATATAN.txt: proyek, tanggal, pengirim, dan catatan perubahan. */
    static String isiCatatan(String namaProyek, String pengirim, String catatan) {
        String tanggal = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Catatan perubahan proyek \"")).append(namaProyek).append("\"\n");
        sb.append(TeksUI.t("Tanggal: ")).append(tanggal).append("\n");
        sb.append(TeksUI.t("Dari: ")).append(pengirim == null || pengirim.isEmpty()
                ? "-" : pengirim).append("\n");
        sb.append("\n");
        sb.append(catatan == null || catatan.isEmpty()
                ? TeksUI.t("(tidak ada catatan perubahan)") : catatan);
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Membuat paket kolaborasi di dalam dirTujuan (biasanya folder induk
     * proyek, Download/project). Mengembalikan berkas ZIP bila berhasil,
     * null bila gagal.
     */
    public static File buatPaket(File dirProyek, String catatan, String pengirim,
                                 File dirTujuan) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        if (dirTujuan == null) return null;
        String nama = dirProyek.getName();
        dirTujuan.mkdirs();
        File keluar = new File(dirTujuan, namaBerkas(nama));
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(
                    new FileOutputStream(keluar)));
            tambah(zos, dirProyek, nama + "/");
            ZipEntry e = new ZipEntry(nama + "/" + NAMA_CATATAN);
            zos.putNextEntry(e);
            zos.write(isiCatatan(nama, pengirim, catatan).getBytes("UTF-8"));
            zos.closeEntry();
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

    /** true bila ZIP berisi CATATAN.txt (di akar atau satu folder pembungkus). */
    public static boolean adaCatatan(File zip) {
        return bacaCatatan(zip) != null;
    }

    /**
     * Membaca CATATAN.txt dari ZIP kolaborasi. Mengembalikan isi catatan
     * bila ada, null bila ZIP tidak berisi CATATAN.txt atau tidak bisa dibaca.
     */
    public static String bacaCatatan(File zip) {
        if (zip == null || !zip.isFile()) return null;
        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            java.util.Enumeration<? extends ZipEntry> entri = zf.entries();
            ZipEntry target = null;
            while (entri.hasMoreElements()) {
                ZipEntry e = entri.nextElement();
                String nama = e.getName();
                if (nama.equals(NAMA_CATATAN) || nama.endsWith("/" + NAMA_CATATAN)) {
                    target = e;
                    break;
                }
            }
            if (target == null) return null;
            BufferedInputStream in = new BufferedInputStream(zf.getInputStream(target));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                in.close();
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (IOException ignored) {
            return null;
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // Penyalin folder ke ZIP, pola yang sama seperti EksporProyek.tambah:
    // folder bin/ di tingkat atas proyek dan berkas *.zip tidak ikut.
    private static void tambah(ZipOutputStream zos, File file, String entri)
            throws IOException {
        if (file.isDirectory()) {
            File[] anak = file.listFiles();
            if (anak == null) return;
            for (File a : anak) {
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
