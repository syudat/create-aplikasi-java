package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pabrik Android App Bundle dan Pemeriksa Kesiapan Google Play Store
 * (Fitur 13, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - bangunAAB: susun arsip .aab (zip) berstruktur bundle — berkas
 *    konfigurasi bundle, modul dasar (manifest, dex, res, aset,
 *    pustaka asli) dari folder proyek.
 *  - simulasiPecahan: perkiraan ukuran unduhan per ABI dan bahasa.
 *  - auditPlay: periksa target SDK, izin berbahaya, dan arsitektur
 *    64-bit dari teks manifest.
 *
 * BATAS JUJUR: bundel yang dihasilkan adalah arsip berstruktur AAB
 * standar; verifikasi akhir oleh bundletool dan penandatanganan
 * bundel untuk Play Console tetap memakai jalur build yang sama
 * dengan APK (Penandatangan). Ukuran pecahan adalah PERKIRAAN dari
 * ukuran berkas (bukan hasil pemecahan bundletool sungguhan).
 */
public class PabrikAAB {

    /** ABI yang didukung pemecahan. */
    public static final String[] ABI = {"arm64-v8a", "armeabi-v7a", "x86_64"};

    /** Satu temuan audit kesiapan Play Store. */
    public static class Temuan {
        public String tingkat = "";
        public String penjelasan = "";
        public Temuan(String tingkat, String penjelasan) {
            this.tingkat = tingkat;
            this.penjelasan = penjelasan;
        }
        public String toString() {
            return tingkat + ": " + penjelasan;
        }
    }

    /** Satu varian pecahan: ABI/bahasa + perkiraan ukuran unduhan. */
    public static class Varian {
        public String nama = "";
        public long ukuran = 0;
        public String toString() {
            return nama + ": " + ukuran + " bita";
        }
    }

    /** Tulis teks konfigurasi bundle (BundleConfig.pb.json versi teks). */
    public static String teksBundleConfig() {
        return "{\n"
                + "  \"optimizations\": {\n"
                + "    \"splitsConfig\": {\n"
                + "      \"splitDimension\": [\n"
                + "        {\"value\": \"ABI\", \"negate\": false, \"suffixStripping\": {\"enabled\": false}},\n"
                + "        {\"value\": \"LANGUAGE\", \"negate\": false, \"suffixStripping\": {\"enabled\": false}}\n"
                + "      ]\n"
                + "    }\n"
                + "  },\n"
                + "  \"compression\": {\"uncompressedGlob\": [\"**/*.so\"]}\n"
                + "}\n";
    }

    /**
     * Susun berkas .aab dari folder modul dasar. Folder sumber berisi
     * manifest/AndroidManifest.xml, dex/classes.dex, res/, assets/,
     * lib/<abi>/*.so (yang ada saja yang dimasukkan). Mengembalikan
     * berkas .aab tujuan (null bila gagal).
     */
    public static File bangunAAB(File dirModul, File keluar) {
        try {
            if (dirModul == null || !dirModul.isDirectory() || keluar == null) return null;
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(keluar));
            try {
                tulisEntri(zip, "BundleConfig.pb.json",
                        teksBundleConfig().getBytes("UTF-8"));
                tambahFolder(zip, dirModul, "base/");
            } finally {
                zip.close();
            }
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void tambahFolder(ZipOutputStream zip, File dir, String awalan)
            throws Exception {
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                tambahFolder(zip, f, awalan + f.getName() + "/");
            } else {
                FileInputStream in = new FileInputStream(f);
                try {
                    zip.putNextEntry(new ZipEntry(awalan + f.getName()));
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                    zip.closeEntry();
                } finally {
                    in.close();
                }
            }
        }
    }

    private static void tulisEntri(ZipOutputStream zip, String nama, byte[] data)
            throws Exception {
        zip.putNextEntry(new ZipEntry(nama));
        zip.write(data);
        zip.closeEntry();
    }

    /** Jumlah ukuran berkas di bawah folder (rekursif). */
    public static long ukuranFolder(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long total = 0;
        File[] daftar = dir.listFiles();
        if (daftar == null) return 0;
        for (int i = 0; i < daftar.length; i++) total += ukuranFolder(daftar[i]);
        return total;
    }

    /**
     * Simulasikan pecahan ukuran unduhan: tiap ABI mendapat porsi
     * pustaka aslinya + bagian bersama; tiap bahasa mendapat porsi
     * sumber dayanya. Angka adalah perkiraan dari ukuran folder.
     */
    public static List<Varian> simulasiPecahan(long ukuranBersama, long ukuranLibPerAbi,
                                               long ukuranResPerBahasa,
                                               String[] abi, String[] bahasa) {
        List<Varian> hasil = new ArrayList<Varian>();
        if (abi != null) {
            for (int i = 0; i < abi.length; i++) {
                Varian v = new Varian();
                v.nama = abi[i];
                v.ukuran = ukuranBersama + ukuranLibPerAbi;
                hasil.add(v);
            }
        }
        if (bahasa != null) {
            for (int i = 0; i < bahasa.length; i++) {
                Varian v = new Varian();
                v.nama = "bahasa-" + bahasa[i];
                v.ukuran = ukuranBersama + ukuranResPerBahasa;
                hasil.add(v);
            }
        }
        return hasil;
    }

    /** Audit kesiapan Play dari teks manifest + info build. */
    public static List<Temuan> auditPlay(String manifestXml, int targetSdk,
                                         boolean adaLib64, boolean adaLib32) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (targetSdk < 35) {
            // Play Store mewajibkan target 35 atau lebih sejak Agustus 2025;
            // target 34 atau lebih lama ditolak saat unggah baru/pembaruan.
            hasil.add(new Temuan("Tinggi",
                    "Target SDK " + targetSdk + " di bawah 35; Play Store mewajibkan target 35 atau lebih."));
        }
        if (!adaLib64) {
            hasil.add(new Temuan("Tinggi",
                    "Tidak ada pustaka asli 64-bit (arm64-v8a); Play Store mewajibkan 64-bit bila ada pustaka 32-bit."));
        }
        if (manifestXml != null) {
            if (manifestXml.contains("android.permission.READ_EXTERNAL_STORAGE")
                    || manifestXml.contains("android.permission.WRITE_EXTERNAL_STORAGE")) {
                hasil.add(new Temuan("Sedang",
                        "Izin penyimpanan eksternal terdaftar; Play Store membatasi pemakaiannya sejak Android 11."));
            }
            if (manifestXml.contains("android.permission.REQUEST_INSTALL_PACKAGES")) {
                hasil.add(new Temuan("Sedang",
                        "Izin pemasangan paket terdaftar; Play Store meminta pernyataan kebijakan untuk izin ini."));
            }
            if (!manifestXml.contains("android:targetSdkVersion")) {
                hasil.add(new Temuan("Tinggi",
                        "Manifest tidak menyebut targetSdkVersion."));
            }
        }
        if (adaLib32 && !adaLib64) {
            hasil.add(new Temuan("Tinggi",
                    "Hanya ada pustaka 32-bit; tambahkan arm64-v8a sebelum mengunggah."));
        }
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(long ukuranBundel, List<Varian> varian,
                                   List<Temuan> temuan) {
        int t = temuan == null ? 0 : temuan.size();
        int v = varian == null ? 0 : varian.size();
        long terkecil = -1;
        if (varian != null) {
            for (int i = 0; i < varian.size(); i++) {
                if (terkecil < 0 || varian.get(i).ukuran < terkecil) {
                    terkecil = varian.get(i).ukuran;
                }
            }
        }
        return "Ukuran unduhan diperkirakan: "
                + (terkecil < 0 ? "tidak diketahui" : terkecil + " bita")
                + " (terkecil dari " + v + " varian). Pemeriksaan Play: "
                + t + " temuan. Ukuran bundel: " + ukuranBundel + " bita.";
    }
}
