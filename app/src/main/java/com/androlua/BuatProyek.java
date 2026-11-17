package com.androlua;

import android.os.Environment;
import android.util.Base64;
import com.androlua.TemplateDasar.Param;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pembuat proyek baru (port Java dari createProject + jembatan buatProyek
 * di main.lua). Menghasilkan folder + berkas proyek untuk tipe Java, Kotlin,
 * HTML, Situs Web, Compose, dan Python. Tipe Lua SUDAH DIHAPUS (keputusan final
 * al kautsar 2026-11-17 00:16: Lua tidak dipakai lagi).
 * Mengembalikan "OK:&lt;folder&gt;" bila berhasil, "GAGAL:&lt;pesan&gt;" bila gagal
 * (format sama seperti jembatan Lua, supaya pemanggil tidak berubah).
 */
public class BuatProyek {

    /** PNG 1x1 transparan untuk icon.png bawaan (sama seperti main.lua). */
    public static final String PNG_1X1_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    /** Aksara kata sandi lisensi (tanpa huruf yang mudah tertukar). */
    private static final String AKSARA_SEED =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    /** Folder proyek: Download/project (sama seperti ROOT di main.lua). */
    public static File folderProyek() {
        return new File(Environment.getExternalStorageDirectory(), "Download/project");
    }

    /** Buat kata sandi proyek acak 16 karakter (lisensi). */
    public static String buatSeed() {
        Random acak = new Random(System.currentTimeMillis());
        StringBuilder b = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            b.append(AKSARA_SEED.charAt(acak.nextInt(AKSARA_SEED.length())));
        }
        return b.toString();
    }

    /** Nama paket -> jalur folder (titik jadi garis miring). */
    public static String jalurPaket(String pkg) {
        return pkg.replace(".", "/");
    }

    /** Tulis berkas teks (UTF-8), buat folder induk bila perlu. */
    private static boolean tulisTeks(File dasar, String rel, String isi, StringBuilder galat) {
        File f = new File(dasar, rel);
        File induk = f.getParentFile();
        if (induk != null && !induk.exists() && !induk.mkdirs()) {
            galat.append("gagal membuat folder ").append(induk.getAbsolutePath());
            return false;
        }
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"));
            w.write(isi);
            w.flush();
            return true;
        } catch (Throwable t) {
            galat.append(t.getMessage());
            return false;
        } finally {
            if (w != null) {
                try { w.close(); } catch (Throwable ignored) { }
            }
        }
    }

    /** Tulis berkas biner, buat folder induk bila perlu. */
    private static boolean tulisBiner(File dasar, String rel, byte[] isi, StringBuilder galat) {
        File f = new File(dasar, rel);
        File induk = f.getParentFile();
        if (induk != null && !induk.exists() && !induk.mkdirs()) {
            galat.append("gagal membuat folder ").append(induk.getAbsolutePath());
            return false;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(isi);
            out.flush();
            return true;
        } catch (Throwable t) {
            galat.append(t.getMessage());
            return false;
        } finally {
            if (out != null) {
                try { out.close(); } catch (Throwable ignored) { }
            }
        }
    }

    /** Satu berkas proyek: jalur relatif + isi (teks atau biner). */
    private static class Berkas {
        final String rel;
        final String teks;
        final byte[] biner;
        Berkas(String rel, String teks) { this.rel = rel; this.teks = teks; this.biner = null; }
        Berkas(String rel, byte[] biner) { this.rel = rel; this.teks = null; this.biner = biner; }
    }

    /**
     * Buat proyek baru dari parameter. Tipe harus java/kotlin/html/situs/
     * compose/python (bukan lua). Kembalikan "OK:&lt;folder&gt;" atau "GAGAL:&lt;pesan&gt;".
     */
    public static String buat(Param p, File akar) {
        File dir = new File(akar, p.name);
        if (dir.exists()) {
            return "GAGAL:Proyek dengan nama \"" + p.name + "\" sudah ada.";
        }
        if (!dir.mkdirs()) {
            return "GAGAL:Gagal membuat folder. Pastikan izin penyimpanan sudah aktif.";
        }
        // Lisensi: buat kata sandi proyek acak SEBELUM templateInit dipanggil.
        if (p.lisensi) {
            p.seed = buatSeed();
        }
        String tipe = p.tipe != null ? p.tipe : "java";
        String pkgPath = jalurPaket(p.packageName);
        List<Berkas> files = new ArrayList<Berkas>();
        files.add(new Berkas("init.lua", TemplateDasar.templateInit(p)));
        // Berkas res standar, sama untuk HTML/Situs Web/Java/Kotlin/Compose.
        List<Berkas> resFiles = new ArrayList<Berkas>();
        resFiles.add(new Berkas("app/src/main/res/layout/activity_main.xml",
                TemplateDasar.templateResLayout(p)));
        resFiles.add(new Berkas("app/src/main/res/values/strings.xml",
                TemplateDasar.templateResStrings(p)));
        resFiles.add(new Berkas("app/src/main/res/values/styles.xml",
                TemplateDasar.templateResStyles(p)));
        byte[] ikon = Base64.decode(PNG_1X1_B64, Base64.DEFAULT);
        resFiles.add(new Berkas("app/src/main/res/drawable/icon.png", ikon));
        // Skrip HTML kustom dari formulir (hanya HTML/Situs Web).
        String skripHtml = null;
        if (p.skripHtml != null && !p.skripHtml.trim().isEmpty()) {
            skripHtml = p.skripHtml;
        }
        String manifest = TemplateDasar.templateAndroidManifest(p, p.packageName + ".MainActivity");
        if ("html".equals(tipe)) {
            files.add(new Berkas("app/src/main/AndroidManifest.xml", manifest));
            files.addAll(resFiles);
            if (p.lisensi) {
                files.add(new Berkas("app/src/main/java/" + pkgPath + "/Lisensi.java",
                        TemplateDasar.templateLisensiJava(p)));
            }
            files.add(new Berkas("app/src/main/java/" + pkgPath + "/MainActivity.java",
                    TemplateDasar.templateHtmlMain(p)));
            files.add(new Berkas("app/src/main/assets/index.html",
                    skripHtml != null ? skripHtml : TemplateDasar.templateHtmlIndex(p)));
            files.add(new Berkas("app/src/main/assets/style.css",
                    TemplateDasar.templateHtmlCss(p)));
            files.add(new Berkas("app/src/main/assets/script.js",
                    TemplateDasar.templateHtmlJs(p)));
        } else if ("situs".equals(tipe)) {
            files.add(new Berkas("app/src/main/AndroidManifest.xml", manifest));
            files.addAll(resFiles);
            if (p.lisensi) {
                files.add(new Berkas("app/src/main/java/" + pkgPath + "/Lisensi.java",
                        TemplateDasar.templateLisensiJava(p)));
            }
            files.add(new Berkas("app/src/main/java/" + pkgPath + "/MainActivity.java",
                    TemplateDasar.templateHtmlMain(p)));
            files.add(new Berkas("app/src/main/assets/index.html",
                    skripHtml != null ? skripHtml : TemplateDasar.templateSitusIndex(p)));
            files.add(new Berkas("app/src/main/assets/tentang.html",
                    TemplateDasar.templateSitusTentang(p)));
            files.add(new Berkas("app/src/main/assets/kontak.html",
                    TemplateDasar.templateSitusKontak(p)));
            files.add(new Berkas("app/src/main/assets/style.css",
                    TemplateDasar.templateSitusCss(p)));
            files.add(new Berkas("app/src/main/assets/script.js",
                    TemplateDasar.templateSitusJs(p)));
        } else if ("java".equals(tipe)) {
            files.add(new Berkas("app/src/main/AndroidManifest.xml", manifest));
            files.addAll(resFiles);
            if (p.lisensi) {
                files.add(new Berkas("app/src/main/java/" + pkgPath + "/Lisensi.java",
                        TemplateDasar.templateLisensiJava(p)));
            }
            files.add(new Berkas("app/src/main/java/" + pkgPath + "/MainActivity.java",
                    TemplateDasar.templateJavaMain(p)));
        } else if ("kotlin".equals(tipe)) {
            files.add(new Berkas("app/src/main/AndroidManifest.xml", manifest));
            files.addAll(resFiles);
            if (p.lisensi) {
                files.add(new Berkas("app/src/main/java/" + pkgPath + "/Lisensi.kt",
                        TemplateDasar.templateLisensiKotlin(p)));
            }
            files.add(new Berkas("app/src/main/java/" + pkgPath + "/MainActivity.kt",
                    TemplateDasar.templateKotlinMain(p)));
        } else if ("compose".equals(tipe)) {
            files.add(new Berkas("app/src/main/AndroidManifest.xml", manifest));
            files.addAll(resFiles);
            if (p.lisensi) {
                files.add(new Berkas("app/src/main/java/" + pkgPath + "/Lisensi.kt",
                        TemplateDasar.templateLisensiKotlin(p)));
            }
            files.add(new Berkas("app/src/main/java/" + pkgPath + "/MainActivity.kt",
                    TemplateDasar.templateComposeMain(p)));
        } else if ("python".equals(tipe)) {
            // Python (Chaquopy 17.2.0 + Python 3.11): host MainActivity.java
            // (templatePythonMain) memanggil main.py di app/src/main/python/
            // lewat Chaquopy. Lisensi memakai templateLisensiJava karena
            // host-nya Java. SDK minimum 24 sudah divalidasi di
            // CreateProjectActivity; BangunLokal menaikkan ke 24 bila perlu.
            files.add(new Berkas("app/src/main/AndroidManifest.xml", manifest));
            files.addAll(resFiles);
            if (p.lisensi) {
                files.add(new Berkas("app/src/main/java/" + pkgPath + "/Lisensi.java",
                        TemplateDasar.templateLisensiJava(p)));
            }
            files.add(new Berkas("app/src/main/java/" + pkgPath + "/MainActivity.java",
                    TemplateDasar.templatePythonMain(p)));
            files.add(new Berkas("app/src/main/python/main.py",
                    TemplateDasar.templatePythonPy(p)));
        } else {
            return "GAGAL:Jenis proyek tidak dikenal: " + tipe;
        }
        for (Berkas f : files) {
            StringBuilder galat = new StringBuilder();
            boolean ok;
            if (f.biner != null) {
                ok = tulisBiner(dir, f.rel, f.biner, galat);
            } else {
                ok = tulisTeks(dir, f.rel, f.teks, galat);
            }
            if (!ok) {
                return "GAGAL:Gagal menulis " + f.rel + ": " + galat.toString();
            }
        }
        return "OK:" + dir.getAbsolutePath();
    }

    /** Buat proyek di folder Download/project (dipakai layar Buat Proyek). */
    public static String buat(Param p) {
        return buat(p, folderProyek());
    }
}
