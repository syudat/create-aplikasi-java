package com.androlua;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Membangun proyek jenis "python" (Chaquopy) di perangkat (offline),
 * tanpa Gradle, dengan meniru perakitan runtime Chaquopy oleh plugin
 * Gradle-nya (lihat product/gradle-plugin/src/main/kotlin/PythonTasks.kt
 * di repo chaquo/chaquopy — TaskBuilder.createConfigs, MiscAssetsTask,
 * JniLibsTask, BuildAssetsTask — dan runtime AndroidPlatform.java).
 *
 * Runtime Chaquopy terdiri dari artefak Maven (com.chaquo.python) yang
 * sudah dikemas sebagai aset IDE di assets/alat/python/:
 *   chaquopy_java-17.2.0.jar        (kelas Java com.chaquo.python.*, di-dex)
 *   bootstrap-17.2.0-3.11.imy      (kode bootstrap Python, .pyc)
 *   target-3.11.17-0-stdlib-pyc.zip (pustaka standar Python, .pyc)
 *   cacert.pem                      (bundel sertifikat CA)
 *   target-3.11.17-0-<abi>.zip     (libpython + modul native per ABI)
 *   libchaquopy_java-17.2.0-3.11-<abi>.so (jembatan JNI Java<->Python)
 *   chaquopy-17.2.0-3.11-<abi>.so  (modul native "chaquopy")
 *
 * Hasil siapkanRuntime() di folder kerja (di bawah cache BangunLokal):
 *   chaquopy/                       -> disalin ke assets/chaquopy/ di APK hasil
 *     build.json                    (python_version + hash SHA-1 semua aset)
 *     bootstrap.imy
 *     stdlib-common.imy             (dari stdlib-pyc.zip)
 *     stdlib-<abi>.imy              (modul native, kecuali modul bootstrap)
 *     bootstrap-native/<abi>/       (modul native yang dibutuhkan saat bootstrap)
 *     bootstrap-native/<abi>/java/chaquopy.so
 *     app.imy                       (kode Python proyek, dari app/src/main/python/)
 *     cacert.pem
 *   lib/<abi>/                      -> disalin ke lib/<abi>/ di APK hasil
 *     libpython3.so, libpython3.11.so, libcrypto_python.so,
 *     libssl_python.so, libsqlite3_python.so, libcrypto_chaquopy.so,
 *     libssl_chaquopy.so, libsqlite3_chaquopy.so, libchaquopy_java.so
 *
 * Berkas .imy dan .so ditulis STORED (tanpa kompresi) di APK hasil, sama
 * seperti plugin Chaquopy (lihat Common.assetZip: .imy dipakai supaya aapt2
 * tidak mengompres ZIP aset, karena AssetFinder butuh seek cepat).
 *
 * Versi yang dipakai: Chaquopy 17.2.0 + Python 3.11.17 (3.11 dipilih karena
 * masih mendukung ABI 32-bit armeabi-v7a; Python 3.12+ hanya 64-bit).
 * SDK minimum 24 (syarat Chaquopy, Common.MIN_SDK_VERSION).
 */
public class BangunPython {

    /** Versi plugin Chaquopy (Maven Central: com.chaquo.python:gradle). */
    public static final String CHAQUOPY_VERSION = "17.2.0";
    /** Versi Python (pendek, untuk build.json "python_version"). */
    public static final String PYTHON_VERSION = "3.11";
    /** Versi Python lengkap (untuk nama artefak target). */
    public static final String PYTHON_FULL = "3.11.17";
    /** SDK minimum yang disyaratkan Chaquopy. */
    public static final int MIN_SDK = 24;

    /** ABI yang dikemas (ARM 64-bit + 32-bit, sama seperti aapt2 IDE). */
    public static final String[] ABI = {"arm64-v8a", "armeabi-v7a"};

    /** Folder aset runtime Chaquopy di dalam APK IDE. */
    private static final String ASET_DIR = "alat/python/";

    /** Modul native stdlib yang dibutuhkan SELAMA bootstrap dan diekstrak
     *  dulu ke bootstrap-native/<abi>/ (bukan lewat AssetFinder). Daftar
     *  persis MiscAssetsTask.BOOTSTRAP_NATIVE_STDLIB untuk Python 3.11
     *  (versionInt 311: tanpa tambahan _sha2/_opcode/_math_integer). */
    private static final String[] BOOTSTRAP_NATIVE_STDLIB = {
            "_bz2.*",      // zipfile < importer
            "_ctypes.*",   // java.primitive dan importer
            "_datetime.*", // calendar < importer
            "_lzma.*",     // zipfile < importer
            "_random.*",   // random < tempfile < importer
            "_sha512.*",   // random < tempfile < importer
            "_struct.*",   // zipfile < importer
            "binascii.*",  // zipfile < importer
            "math.*",      // datetime < calendar < importer
            "mmap.*",      // elftools < importer
            "zlib.*"       // zipimport
    };

    /** Stempel waktu tetap untuk entri ZIP yang dibuat (1980-02-01, sama
     *  seperti makeZip() plugin Chaquopy — supaya hasil deterministik). */
    private static final long STEMPEL_ZIP =
            new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

    // ------------------------------------------------------------------ kompilasi

    /** Ekstrak chaquopy_java.jar dari aset IDE ke folder kerja (untuk ECJ+D8). */
    static File jarChaquo(Context ctx, File kerja) throws IOException {
        File jar = new File(kerja, "chaquopy_java.jar");
        if (!jar.isFile()) {
            salinAset(ctx, ASET_DIR + "chaquopy_java-" + CHAQUOPY_VERSION + ".jar", jar);
        }
        return jar;
    }

    /** Kompilasi proyek Python untuk rakit(): host MainActivity.java (+ Lisensi.java
     *  bila ada) dikompilasi ECJ, lalu di-dex D8 bersama kelas-kelas
     *  com.chaquo.python.* dari chaquopy_java.jar. Lihat
     *  BangunJava.kompilasiUntukRakitPython. */
    static byte[] kompilasiUntukRakit(Context ctx, File projectDir, List<File> rJava,
                                      File kerja) throws Exception {
        File jar = jarChaquo(ctx, kerja);
        return BangunJava.kompilasiUntukRakitPython(ctx, projectDir, rJava,
                Arrays.asList(jar));
    }

    // ------------------------------------------------------------------ aset runtime

    /** Siapkan seluruh runtime Chaquopy untuk satu proyek Python di folder
     *  kerja. Menghasilkan subfolder "chaquopy" (untuk assets/chaquopy/ di
     *  APK hasil) dan "lib" (untuk lib/<abi>/ di APK hasil). Mengembalikan
     *  folder kerja itu sendiri. */
    static File siapkanRuntime(Context ctx, File projectDir, File kerja) throws Exception {
        File dirChaquopy = new File(kerja, "chaquopy");
        File dirLib = new File(kerja, "lib");
        dirChaquopy.mkdirs();
        dirLib.mkdirs();

        // 1) bootstrap.imy + stdlib-common.imy + cacert.pem (salin apa adanya).
        salinAset(ctx, ASET_DIR + "bootstrap-" + CHAQUOPY_VERSION + "-" + PYTHON_VERSION + ".imy",
                new File(dirChaquopy, "bootstrap.imy"));
        salinAset(ctx, ASET_DIR + "target-" + PYTHON_FULL + "-0-stdlib-pyc.zip",
                new File(dirChaquopy, "stdlib-common.imy"));
        salinAset(ctx, ASET_DIR + "cacert.pem", new File(dirChaquopy, "cacert.pem"));

        // 2) Per ABI: stdlib-<abi>.imy + bootstrap-native/<abi>/ + lib/<abi>/.
        for (String abi : ABI) {
            siapkanAbi(ctx, abi, dirChaquopy, dirLib);
        }

        // 3) app.imy: kode Python proyek (app/src/main/python/) di-zip.
        File dirPython = new File(projectDir, "app/src/main/python");
        File mainPy = new File(dirPython, "main.py");
        if (!mainPy.isFile()) {
            throw new IOException("main.py tidak ditemukan di app/src/main/python/ "
                    + "(proyek Python harus punya main.py)");
        }
        zipFolder(dirPython, new File(dirChaquopy, "app.imy"));

        // 4) build.json — TERAKHIR, setelah semua aset lain ditulis, karena
        //    isinya hash SHA-1 semua berkas di bawah chaquopy/ (kecuali
        //    build.json sendiri). Persis BuildAssetsTask plugin Chaquopy.
        tulisBuildJson(dirChaquopy);

        return kerja;
    }

    /** Satu ABI: baca target-<abi>.zip, pisah lib-dynload menjadi
     *  stdlib-<abi>.imy + bootstrap-native/<abi>/, salin jniLibs ke lib/<abi>/,
     *  dan salin modul native chaquopy + libchaquopy_java. */
    private static void siapkanAbi(Context ctx, String abi, File dirChaquopy, File dirLib)
            throws Exception {
        String target = ASET_DIR + "target-" + PYTHON_FULL + "-0-" + abi + ".zip";
        File targetZip = new File(dirChaquopy.getParentFile(), "target-" + abi + ".zip");
        salinAset(ctx, target, targetZip);

        File dirBootstrapNative = new File(dirChaquopy, "bootstrap-native/" + abi);
        File dirBootstrapJava = new File(dirBootstrapNative, "java");
        dirBootstrapJava.mkdirs();
        File dirLibAbi = new File(dirLib, abi);
        dirLibAbi.mkdirs();

        // stdlib-<abi>.imy: zip modul native KECUALI modul bootstrap.
        File stdlibAbi = new File(dirChaquopy, "stdlib-" + abi + ".imy");
        ZipOutputStream zipStdlib = new ZipOutputStream(new FileOutputStream(stdlibAbi));
        try {
            ZipInputStream zin = new ZipInputStream(new FileInputStream(targetZip));
            try {
                ZipEntry entri;
                while ((entri = zin.getNextEntry()) != null) {
                    String nama = entri.getName();
                    if (nama.startsWith("lib-dynload/" + abi + "/") && nama.endsWith(".so")) {
                        String dasar = nama.substring(("lib-dynload/" + abi + "/").length());
                        byte[] data = bacaStream(zin);
                        if (cocokBootstrap(dasar)) {
                            tulis(new File(dirBootstrapNative, dasar), data);
                        } else {
                            ZipEntry e = new ZipEntry(dasar);
                            e.setTime(STEMPEL_ZIP);
                            zipStdlib.putNextEntry(e);
                            zipStdlib.write(data);
                            zipStdlib.closeEntry();
                        }
                    } else if (nama.startsWith("jniLibs/" + abi + "/") && nama.endsWith(".so")) {
                        // libpython + OpenSSL/SQLite: ke lib/<abi>/ APK hasil.
                        String dasar = nama.substring(("jniLibs/" + abi + "/").length());
                        tulis(new File(dirLibAbi, dasar), bacaStream(zin));
                    }
                    zin.closeEntry();
                }
            } finally {
                zin.close();
            }
        } finally {
            zipStdlib.close();
        }

        // Modul native "chaquopy" -> bootstrap-native/<abi>/java/chaquopy.so.
        salinAset(ctx, ASET_DIR + "chaquopy-" + CHAQUOPY_VERSION + "-" + PYTHON_VERSION
                        + "-" + abi + ".so",
                new File(dirBootstrapJava, "chaquopy.so"));
        // Jembatan JNI Java<->Python -> lib/<abi>/libchaquopy_java.so.
        salinAset(ctx, ASET_DIR + "libchaquopy_java-" + CHAQUOPY_VERSION + "-"
                        + PYTHON_VERSION + "-" + abi + ".so",
                new File(dirLibAbi, "libchaquopy_java.so"));
    }

    /** Apakah nama berkas modul native termasuk daftar bootstrap
     *  (pola "nama.*" = diawali "nama."). */
    private static boolean cocokBootstrap(String nama) {
        for (String pola : BOOTSTRAP_NATIVE_STDLIB) {
            String batang = pola.substring(0, pola.length() - 2); // buang ".*"
            if (nama.startsWith(batang + ".")) return true;
        }
        return false;
    }

    /** Tulis build.json: {"python_version": "3.11", "assets": {...SHA-1...},
     *  "extract_packages": []}. Peta assets = semua berkas di bawah chaquopy/
     *  (rekursif, jalur relatif dengan "/"), kecuali build.json sendiri.
     *  Hash = SHA-1 heksadesimal huruf kecil (persis hashAssets plugin). */
    private static void tulisBuildJson(File dirChaquopy) throws Exception {
        Map<String, String> peta = new LinkedHashMap<String, String>();
        kumpulHash(dirChaquopy, "", peta);
        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("    \"python_version\": \"").append(PYTHON_VERSION).append("\",\n");
        b.append("    \"assets\": {");
        boolean pertama = true;
        for (Map.Entry<String, String> e : peta.entrySet()) {
            if (!pertama) b.append(",");
            b.append("\n        \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"");
            pertama = false;
        }
        if (!peta.isEmpty()) b.append("\n    ");
        b.append("},\n");
        b.append("    \"extract_packages\": []\n");
        b.append("}\n");
        tulis(new File(dirChaquopy, "build.json"), b.toString().getBytes("UTF-8"));
    }

    private static void kumpulHash(File dir, String awalan, Map<String, String> peta)
            throws Exception {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            String rel = awalan.isEmpty() ? f.getName() : awalan + "/" + f.getName();
            if (f.isDirectory()) {
                kumpulHash(f, rel, peta);
            } else if (!rel.equals("build.json")) {
                peta.put(rel, sha1Hex(f));
            }
        }
    }

    private static String sha1Hex(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally {
            in.close();
        }
        byte[] d = md.digest();
        StringBuilder s = new StringBuilder(d.length * 2);
        for (byte x : d) s.append(String.format("%02x", x & 0xFF));
        return s.toString();
    }

    /** Zip seluruh isi folder (rekursif, jalur relatif, DEFLATED, stempel
     *  tetap 1980-02-01 — seperti makeZip() plugin Chaquopy). */
    static void zipFolder(File dir, File keluar) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(keluar));
        try {
            zipFolderRekursif(dir, "", zos);
        } finally {
            zos.close();
        }
    }

    private static void zipFolderRekursif(File dir, String awalan, ZipOutputStream zos)
            throws IOException {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            String rel = awalan.isEmpty() ? f.getName() : awalan + "/" + f.getName();
            if (f.isDirectory()) {
                // __pycache__/ berisi .pyc hasil kompilasi lokal (bisa dari
                // Python versi lain, mis. 3.12 di komputer) — jangan ikut ke
                // app.imy; runtime memakai main.py sumbernya langsung.
                if (f.getName().equals("__pycache__")) continue;
                zipFolderRekursif(f, rel, zos);
            } else {
                ZipEntry e = new ZipEntry(rel);
                e.setTime(STEMPEL_ZIP);
                zos.putNextEntry(e);
                InputStream in = new FileInputStream(f);
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
    }

    // ------------------------------------------------------------------ util

    private static void salinAset(Context ctx, String namaAset, File tujuan) throws IOException {
        File induk = tujuan.getParentFile();
        if (induk != null) induk.mkdirs();
        InputStream in = ctx.getAssets().open(namaAset);
        try {
            OutputStream out = new FileOutputStream(tujuan);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    static void tulis(File f, byte[] d) throws IOException {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        try {
            o.write(d);
        } finally {
            o.close();
        }
    }

    private static byte[] bacaStream(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    /** Daftar jalur (relatif, dipisah "/") semua berkas di bawah folder
     *  hasil siapkanRuntime, dipakai BangunLokal.rakit() untuk menyalin
     *  runtime ke APK. Dipakai juga oleh uji verifikasi. */
    static List<String> daftarBerkas(File dir, String awalan) {
        List<String> hasil = new ArrayList<String>();
        File[] fs = dir.listFiles();
        if (fs == null) return hasil;
        Arrays.sort(fs);
        for (File f : fs) {
            String rel = awalan.isEmpty() ? f.getName() : awalan + "/" + f.getName();
            if (f.isDirectory()) hasil.addAll(daftarBerkas(f, rel));
            else hasil.add(rel);
        }
        return hasil;
    }
}
