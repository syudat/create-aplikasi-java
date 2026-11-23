package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Cadangan &amp; Pulihkan, Bagikan &amp; Impor proyek (Tahap 3C).
 *
 * Format ZIP sama persis dengan Ekspor ZIP yang sudah ada
 * (EksporProyek.ekspor): seluruh isi folder proyek, dengan nama folder
 * proyek sebagai entri paling atas ("NamaProyek/init.lua",
 * "NamaProyek/app/src/..."), tanpa folder bin/ dan tanpa berkas *.zip di
 * dalamnya. Pembuatan ZIP dipakai ulang dari EksporProyek supaya tidak ada
 * dua format yang berbeda; kelas ini hanya menambah: penamaan cadangan,
 * daftar cadangan, pemeriksaan isi ZIP, ekstraksi yang aman (lindung
 * zip-slip), dan penurunan nama proyek dari ZIP.
 *
 * Cadangan disimpan di Download/backup/&lt;Nama&gt;-&lt;tanggal&gt;.zip.
 * Impor menerima ZIP proyek apa pun (dari Ekspor/Cadangan/Bagikan, atau
 * buatan tangan) selama berisi init.lua — di tingkat atas, atau di dalam
 * satu folder pembungkus ("NamaProyek/init.lua").
 *
 * Kelas ini murni java.io/java.util.zip (tanpa API Android) supaya
 * logikanya bisa diuji ulang di JRE biasa. Berbagi lewat Intent
 * (ACTION_SEND + FileProvider) ada di ProjectsActivity, bukan di sini.
 */
public class ArsipProyek {

    /** Nama folder cadangan di bawah Download. */
    public static final String DIR_CADANGAN = "backup";

    // ------------------------------------------------------- penamaan

    /** Nama berkas cadangan: "Nama-2026-11-14-123045.zip". */
    public static String namaBerkasCadangan(String namaProyek) {
        String stempel = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US)
                .format(new Date());
        return namaProyek + "-" + stempel + ".zip";
    }

    /**
     * Nama proyek dari nama berkas ZIP: buang ".zip", lalu buang akhiran
     * tanggal cadangan "-YYYY-MM-DD-HHMMSS" bila ada. Bila hasilnya kosong,
     * kembalikan "Proyek".
     */
    public static String namaProyekDariBerkas(String namaBerkas) {
        if (namaBerkas == null) return TeksUI.t("Proyek");
        String nama = namaBerkas;
        if (nama.toLowerCase(Locale.US).endsWith(".zip")) {
            nama = nama.substring(0, nama.length() - 4);
        }
        nama = nama.replaceAll("-\\d{4}-\\d{2}-\\d{2}-\\d{6}$", "");
        if (nama.isEmpty()) return TeksUI.t("Proyek");
        return nama;
    }

    /**
     * Nama proyek dari ISI zip: folder pembungkus paling atas bila semua
     * entri berada di dalam satu folder ("NamaProyek/init.lua" -> "NamaProyek");
     * bila tidak ada pembungkus, null (pemanggil memakai nama berkas).
     */
    public static String namaProyekDariIsi(File zip) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            String pembungkus = null;
            java.util.Enumeration<? extends ZipEntry> semua = zf.entries();
            while (semua.hasMoreElements()) {
                String nama = semua.nextElement().getName();
                if (nama == null || nama.isEmpty()) continue;
                int garis = nama.indexOf('/');
                if (garis <= 0) return null; // ada entri di tingkat atas
                String awal = nama.substring(0, garis);
                if (pembungkus == null) {
                    pembungkus = awal;
                } else if (!pembungkus.equals(awal)) {
                    return null; // lebih dari satu folder atas
                }
            }
            return pembungkus;
        } catch (Exception ignored) {
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

    /** Aturan nama proyek, sama seperti validasi tombol Buat di showCreate. */
    public static boolean namaProyekValid(String nama) {
        if (nama == null) return false;
        String n = nama.trim();
        if (n.isEmpty()) return false;
        if (".".equals(n) || "..".equals(n)) return false;
        if (n.indexOf('/') >= 0 || n.indexOf('\\') >= 0 || n.indexOf(':') >= 0
                || n.indexOf('*') >= 0 || n.indexOf('?') >= 0 || n.indexOf('"') >= 0
                || n.indexOf('<') >= 0 || n.indexOf('>') >= 0 || n.indexOf('|') >= 0) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------- cadangan

    /** Daftar berkas cadangan (*.zip) di folder cadangan, terbaru dulu. */
    public static List<File> daftarCadangan(File dirCadangan) {
        List<File> hasil = new ArrayList<File>();
        if (dirCadangan == null || !dirCadangan.isDirectory()) return hasil;
        File[] anak = dirCadangan.listFiles();
        if (anak == null) return hasil;
        for (int i = 0; i < anak.length; i++) {
            if (anak[i].isFile()
                    && anak[i].getName().toLowerCase(Locale.US).endsWith(".zip")) {
                hasil.add(anak[i]);
            }
        }
        Collections.sort(hasil, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                long d = b.lastModified() - a.lastModified();
                if (d != 0) return d < 0 ? -1 : 1;
                return a.getName().compareTo(b.getName());
            }
        });
        return hasil;
    }

    /**
     * Membuat cadangan proyek: ZIP lewat EksporProyek (format sama dengan
     * Ekspor ZIP), lalu disalin ke folder cadangan dengan nama bertanggal.
     * Mengembalikan berkas cadangan bila berhasil, null bila gagal.
     */
    public static File buatCadangan(File dirProyek, File dirCadangan) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        if (dirCadangan == null) return null;
        dirCadangan.mkdirs();
        String jalurZip = EksporProyek.ekspor(dirProyek.getAbsolutePath());
        if (jalurZip == null || jalurZip.isEmpty()) return null;
        File zipSementara = new File(jalurZip);
        File tujuan = new File(dirCadangan, namaBerkasCadangan(dirProyek.getName()));
        try {
            salinBerkas(zipSementara, tujuan);
            // ZIP sementara di Download/project dihapus supaya tidak menumpuk
            // dan tidak ikut terzip pada ekspor berikutnya.
            zipSementara.delete();
            if (tujuan.isFile() && tujuan.length() > 0) return tujuan;
            return null;
        } catch (Exception e) {
            if (tujuan.exists()) tujuan.delete();
            return null;
        }
    }

    private static void salinBerkas(File dari, File ke) throws IOException {
        BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(dari));
        BufferedOutputStream keluar = new BufferedOutputStream(new FileOutputStream(ke));
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
        } finally {
            try {
                masuk.close();
            } catch (IOException ignored) {
            }
            try {
                keluar.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ------------------------------------------------------- pemeriksaan

    /**
     * True bila ZIP berisi init.lua proyek: entri "init.lua" di tingkat atas,
     * atau "<Folder>/init.lua" di dalam satu folder pembungkus.
     */
    public static boolean zipBerisiInitLua(File zip) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            java.util.Enumeration<? extends ZipEntry> semua = zf.entries();
            while (semua.hasMoreElements()) {
                String nama = semua.nextElement().getName();
                if ("init.lua".equals(nama)) return true;
                if (nama != null && nama.endsWith("/init.lua")
                        && nama.indexOf('/') == nama.lastIndexOf('/')) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------- ekstraksi

    /**
     * Mengekstrak ZIP proyek ke folder tujuan (folder proyek, mis.
     * Download/project/&lt;Nama&gt;). Folder pembungkus paling atas
     * ("NamaProyek/...") dikupas bila ada, supaya isinya langsung menjadi
     * isi folder tujuan. Mengembalikan null bila berhasil, atau pesan galat.
     * Aman dari zip-slip: entri yang keluar dari folder tujuan ditolak.
     */
    public static String ekstrakZip(File zip, File dirTujuan) {
        if (zip == null || !zip.isFile()) return TeksUI.t("Berkas ZIP tidak ditemukan.");
        if (dirTujuan == null) return TeksUI.t("Folder tujuan tidak ditentukan.");
        if (!zipBerisiInitLua(zip)) {
            return TeksUI.t("Berkas ini bukan proyek Create Aplikasi (tidak ada berkas data proyek di dalamnya).");
        }
        ZipInputStream masuk = null;
        try {
            dirTujuan.mkdirs();
            String kanonTujuan = dirTujuan.getCanonicalPath() + File.separator;
            String pembungkus = namaProyekDariIsi(zip);
            String awalan = pembungkus == null ? "" : pembungkus + "/";
            masuk = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
            ZipEntry entri;
            byte[] buf = new byte[16384];
            while ((entri = masuk.getNextEntry()) != null) {
                String nama = entri.getName();
                if (nama == null || nama.isEmpty()) continue;
                if (nama.startsWith(awalan)) nama = nama.substring(awalan.length());
                if (nama.isEmpty()) continue;
                File keluar = new File(dirTujuan, nama);
                String kanonKeluar = keluar.getCanonicalPath();
                if (!kanonKeluar.startsWith(kanonTujuan)) {
                    return TeksUI.t("ZIP ditolak: ada entri yang keluar dari folder proyek (")
                            + entri.getName() + ").";
                }
                if (entri.isDirectory()) {
                    keluar.mkdirs();
                    continue;
                }
                File induk = keluar.getParentFile();
                if (induk != null) induk.mkdirs();
                BufferedOutputStream tulis = new BufferedOutputStream(
                        new FileOutputStream(keluar));
                try {
                    int n;
                    while ((n = masuk.read(buf)) > 0) tulis.write(buf, 0, n);
                } finally {
                    tulis.close();
                }
            }
            if (!new File(dirTujuan, "init.lua").isFile()) {
                return TeksUI.t("Ekstraksi selesai tetapi berkas data proyek tidak ditemukan di hasil.");
            }
            return null;
        } catch (Exception e) {
            return TeksUI.t("Gagal mengekstrak ZIP: ") + e.getMessage();
        } finally {
            if (masuk != null) {
                try {
                    masuk.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Salin aliran masuk ke berkas (untuk impor dari pemilih berkas). */
    public static String salinAliran(InputStream masuk, File tujuan) {
        if (masuk == null) return TeksUI.t("Tidak ada data untuk disalin.");
        if (tujuan == null) return TeksUI.t("Tujuan tidak ditentukan.");
        BufferedOutputStream keluar = null;
        try {
            File induk = tujuan.getParentFile();
            if (induk != null) induk.mkdirs();
            keluar = new BufferedOutputStream(new FileOutputStream(tujuan));
            byte[] buf = new byte[16384];
            int n;
            while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
            keluar.close();
            keluar = null;
            if (!tujuan.isFile() || tujuan.length() == 0) {
                return TeksUI.t("Berkas hasil kosong.");
            }
            return null;
        } catch (Exception e) {
            return TeksUI.t("Gagal menyalin berkas: ") + e.getMessage();
        } finally {
            if (keluar != null) {
                try {
                    keluar.close();
                } catch (IOException ignored) {
                }
            }
            try {
                masuk.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ------------------------------------------------------- hapus

    /** Hapus berkas/folder rekursif. Mengembalikan false bila ada yang gagal. */
    public static boolean hapusRekursif(File f) {
        boolean ok = true;
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] anak = f.listFiles();
            if (anak != null) {
                for (int i = 0; i < anak.length; i++) {
                    if (!hapusRekursif(anak[i])) ok = false;
                }
            }
        }
        if (!f.delete()) ok = false;
        return ok;
    }
}
