package com.androlua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Membangun APK sepenuhnya di dalam aplikasi (tanpa internet, tanpa Termux, tanpa GitHub).
 *
 * Alur: init.lua -> manifest dari template -> aapt2 (pustaka native aplikasi ini) mengubahnya
 * menjadi manifest biner -> APK aplikasi ini sendiri dipakai sebagai kerangka (dex, pustaka native,
 * resources.arsc) -> aset diganti dengan berkas proyek -> penjajaran zip -> tanda tangan (apksig).
 */
public class BangunLokal {
    public static final int PROGRESS = 0, SUCCESS = 1, FAILED = 2;

    public interface Listener {
        void onEvent(int type, String msg);
    }

    /** Aset khusus IDE yang tidak ikut ke APK hasil. */
    private static final String[] BUANG_ASET = {"help.lua", "layout.lua", "logcat.lua", "luadoc.lua", "luadoc",
            "javaapi", "layouthelper", "projectinfo", "plugin", "project", "garuda", "alat", "alat_editor.lua",
            "main.lua", "init.lua", "engine.zip", "bangun-proyek.yml",
            // Perbaikan pasca-penyerahan (mesin Lua di APK proyek):
            // autotheme.lua hanya dipakai berkas bantu IDE; permission.lua
            // hanya data picker izin layar Buat Proyek; keys/ = keystore
            // penandatangan IDE; play/undo/redo.png = ikon toolbar editor.
            "autotheme.lua", "permission.lua", "keys", "play.png", "undo.png", "redo.png"};

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    private final Context ctx;
    private final File projectDir;
    private final Listener listener;
    private final StringBuilder log = new StringBuilder();
    private final Handler main = new Handler(Looper.getMainLooper());

    private BangunLokal(Context ctx, String projectDir, Listener l) {
        this.ctx = ctx.getApplicationContext();
        this.projectDir = new File(projectDir);
        this.listener = l;
    }

    public static void bangun(final Context ctx, final String projectDir, final Listener l) {
        final BangunLokal b = new BangunLokal(ctx, projectDir, l);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File bin = new File(b.projectDir, "bin");
                try {
                    String apk = b.jalankan();
                    b.simpanLog(bin);
                    b.kirim(SUCCESS, apk);
                } catch (Throwable e) {
                    b.log.append("\nGAGAL: ").append(e).append('\n');
                    // Bila gagal saat/setelah kompilasi Java, tetap catat berkas
                    // apa saja yang sempat dikumpulkan ECJ (supaya terlihat di
                    // log-build.txt apakah berkas di samping MainActivity ikut).
                    String ringkasan = BangunJava.ringkasanKompilasiTerakhir();
                    if (ringkasan != null && ringkasan.length() > 0) b.log.append(ringkasan);
                    // InvocationTargetException dari refleksi hanya pembungkus:
                    // penyebab aslinya ada di getCause() dan sebelumnya tidak
                    // pernah ditulis ke log, sehingga log-build.txt tidak
                    // pernah berisi "Caused by". Tulis seluruh rantai penyebab
                    // beserta stack trace-nya.
                    tulisRantaiPenyebab(b.log, e);
                    b.simpanLog(bin);
                    String pesan = e.getMessage();
                    if (pesan == null && e.getCause() != null) pesan = e.getCause().toString();
                    if (pesan == null) pesan = e.toString();
                    b.kirim(FAILED, "Gagal: " + pesan + "\n\nRincian: " + new File(bin, "log-build.txt"));
                }
            }
        }, "BangunLokal").start();
    }

    private void kirim(final int type, final String msg) {
        log.append("[").append(type).append("] ").append(msg).append('\n');
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onEvent(type, msg);
            }
        });
    }

    private void simpanLog(File bin) {
        try {
            bin.mkdirs();
            FileOutputStream o = new FileOutputStream(new File(bin, "log-build.txt"));
            o.write(log.toString().getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) {
        }
    }

    /** Tulis rantai penyebab (getCause) beserta stack trace tiap penyebab ke
     *  log, dengan pengaman terhadap rantai penyebab yang berputar. */
    private static void tulisRantaiPenyebab(StringBuilder log, Throwable e) {
        Set<Throwable> sudah = new HashSet<Throwable>();
        for (Throwable c = e.getCause(); c != null && sudah.add(c); c = c.getCause()) {
            log.append("Disebabkan oleh: ").append(c).append('\n');
            for (StackTraceElement s : c.getStackTrace()) {
                log.append("    di ").append(s).append('\n');
            }
        }
    }

    // ------------------------------------------------------------------ init.lua

    private static class Info {
        String appname, appver, pkg;
        String tipe = "java"; // "java" | "kotlin" | "html" | "situs" | "compose" | "python" (Lua dihapus)
        int code = 1, min = 21, target = 36;
        List<String> perms = new ArrayList<String>();
        // Komponen tambahan (mis. <service> keyboard) dari AndroidManifest.xml
        // proyek, disisipkan ke template lewat {{EXTRA_COMPONENTS}}.
        String extraComponents = "";
    }

    /** Nama kelas Activity peluncur di dalam manifest, sesuai jenis proyek. */
    static String aktivitasUtama(String tipe, String pkg) {
        // Semua jenis proyek (HTML/Situs Web/Java/Kotlin/Compose) memakai
        // MainActivity di paket proyek. Ronde 3: fallback "com.androlua.Main"
        // (mesin Lua) dihapus — tidak ada lagi proyek Lua.
        // Konvensi V1 proyek Java: kelas "<packagename>.MainActivity" (lihat BangunJava).
        return pkg + ".MainActivity";
    }

    private static String luaStr(String src, String key, String def) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(src);
        if (!m.find()) return def;
        return m.group(1).replace("\\n", "\n").replaceAll("\\\\(.)", "$1");
    }

    private static int luaInt(String src, String key, int def) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*(\\d+)").matcher(src);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    private Info bacaInit() throws IOException {
        File f = new File(projectDir, "init.lua");
        if (!f.isFile()) throw new IOException("Berkas data proyek tidak ditemukan di " + projectDir);
        String s = new String(baca(f), "UTF-8").replaceAll("--[^\\n]*", "");
        Info i = new Info();
        i.appname = luaStr(s, "appname", projectDir.getName());
        i.appver = luaStr(s, "appver", "1.0");
        i.pkg = luaStr(s, "packagename", "");
        i.tipe = luaStr(s, "tipe", "java"); // Java IDE: tidak ada lagi proyek Lua; tanpa tipe eksplisit, anggap Java
        i.code = luaInt(s, "appcode", 1);
        i.min = luaInt(s, "appsdk", 21);
        i.target = luaInt(s, "targetsdk", 33);
        Matcher m = Pattern.compile("user_permission\\s*=\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(s);
        if (m.find()) {
            Matcher q = Pattern.compile("\"([A-Za-z0-9_.]+)\"").matcher(m.group(1));
            while (q.find()) if (!i.perms.contains(q.group(1))) i.perms.add(q.group(1));
        }
        // Izin: untuk Java/HTML/Kotlin dibaca dari AndroidManifest.xml di
        // app/src/main/ (di samping folder java, seperti proyek Android pada
        // umumnya); untuk proyek lama yang manifest-nya masih di root proyek
        // (di samping folder app/), dibaca dari sana; untuk proyek Lua dan
        // proyek lama tanpa manifest, tetap dari user_permission di init.lua.
        // Nama "android.permission.X" dinormalkan ke "X" supaya buatManifest()
        // (khususnya pengecualian ACCESSIBILITY_SERVICE) tetap seperti semula.
        if (i.tipe.equals("java") || i.tipe.equals("html") || i.tipe.equals("situs") || i.tipe.equals("kotlin")
                || i.tipe.equals("compose") || i.tipe.equals("python")) {
            File dm = new File(projectDir, "app/src/main/AndroidManifest.xml");
            if (!dm.isFile()) dm = new File(projectDir, "AndroidManifest.xml");
            if (dm.isFile()) {
                String xml = new String(baca(dm), "UTF-8");
                Matcher pm = Pattern.compile("<uses-permission\\s+android:name=\"([^\"]+)\"").matcher(xml);
                i.perms.clear();
                while (pm.find()) {
                    String nama = pm.group(1);
                    if (nama.startsWith("android.permission.")) nama = nama.substring("android.permission.".length());
                    if (!i.perms.contains(nama)) i.perms.add(nama);
                }
                // Komponen tambahan: semua blok <service>...</service> dari
                // manifest proyek (mis. layanan keyboard) disisipkan apa adanya
                // ke template lewat {{EXTRA_COMPONENTS}}, dengan saringan
                // layanan aksesibilitas (lihat panenLayanan).
                StringBuilder ec = new StringBuilder();
                String layanan = panenLayanan(xml, i.perms.contains("ACCESSIBILITY_SERVICE"));
                if (layanan.length() > 0) ec.append(layanan);
                // Komponen tambahan: semua blok <activity>...</activity> dari
                // manifest proyek (mis. Pengaturan AI) juga disisipkan, KECUALI
                // activity utama (sudah ada di template lewat {{MAIN_ACTIVITY}}).
                // Tanpa ini, activity pengaturan tidak terdaftar di manifest APK
                // hasil sehingga startActivity() melempar ActivityNotFoundException
                // dan layar nge-blank/macet saat membuka Pengaturan AI.
                String utama = aktivitasUtama(i.tipe, i.pkg);
                Matcher am = Pattern.compile("<activity\\b[^>]*?(?:/\\s*>|>.*?</activity>)", Pattern.DOTALL).matcher(xml);
                while (am.find()) {
                    String blok = am.group();
                    Matcher nm = Pattern.compile("android:name=\"([^\"]+)\"").matcher(blok);
                    if (nm.find()) {
                        String nama = nm.group(1);
                        String penuh = nama.startsWith(".") ? i.pkg + nama
                                : (nama.contains(".") ? nama : i.pkg + "." + nama);
                        if (penuh.equals(utama)) continue;
                    }
                    if (ec.length() > 0) ec.append('\n');
                    ec.append(blok);
                }
                // Komponen tambahan: semua blok <receiver>...</receiver> dan
                // <provider>...</provider> dari manifest proyek juga disisipkan,
                // supaya aplikasi teman yang memakai BroadcastReceiver (mis.
                // BOOT_COMPLETED, alarm, notifikasi) atau ContentProvider tetap
                // jalan. Provider kelas FileProvider dilewati karena template
                // sudah mendeklarasikan FileProvider sendiri.
                Matcher rm = Pattern.compile("<receiver\\b[^>]*?(?:/\\s*>|>.*?</receiver>)", Pattern.DOTALL).matcher(xml);
                while (rm.find()) {
                    if (ec.length() > 0) ec.append('\n');
                    ec.append(rm.group());
                }
                Matcher pv = Pattern.compile("<provider\\b[^>]*?(?:/\\s*>|>.*?</provider>)", Pattern.DOTALL).matcher(xml);
                while (pv.find()) {
                    String blok = pv.group();
                    Matcher nm2 = Pattern.compile("android:name=\"([^\"]+)\"").matcher(blok);
                    if (nm2.find()) {
                        String nama = nm2.group(1);
                        if (nama.equals("android.content.FileProvider")
                                || nama.equals("android.support.v4.content.FileProvider")
                                || nama.equals("androidx.core.content.FileProvider")) continue;
                    }
                    if (ec.length() > 0) ec.append('\n');
                    ec.append(blok);
                }
                i.extraComponents = ec.toString();
            }
        }
        if (!i.pkg.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))
            throw new IOException("Nama paket tidak valid: \"" + i.pkg + "\" (contoh: com.contoh.aplikasi)");
        if (i.min < 21) i.min = 21;
        // Python (Chaquopy): SDK minimum 24 (syarat runtime Chaquopy).
        // Bila init.lua proyek Python masih 21-23, naikkan diam-diam ke 24
        // (CreateProjectActivity sudah menolak <24 saat proyek dibuat).
        if ("python".equals(i.tipe) && i.min < BangunPython.MIN_SDK) i.min = BangunPython.MIN_SDK;
        if (i.target > 36) i.target = 36;
        if (i.target < i.min) i.target = i.min;
        return i;
    }

    /** True bila blok <service> ini mendeklarasikan layanan aksesibilitas. */
    static boolean layananAksesibilitas(String blok) {
        return blok.contains("android.accessibilityservice.AccessibilityService")
                || blok.contains("BIND_ACCESSIBILITY_SERVICE");
    }

    /**
     * Panen blok <service> dari manifest proyek untuk {{EXTRA_COMPONENTS}}.
     * Perbaikan bug (perintah al kautsar 2026-11-23): blok layanan
     * aksesibilitas dibuang bila izin ACCESSIBILITY_SERVICE tidak dicentang
     * di proyek, supaya aplikasi hasil tidak muncul di Pengaturan
     * Aksesibilitas HP pengguna; bila dicentang, bloknya dipertahankan.
     */
    static String panenLayanan(String xml, boolean izinAksesibilitas) {
        Matcher sm = Pattern.compile("<service\\b[^>]*?(?:/\\s*>|>.*?</service>)", Pattern.DOTALL).matcher(xml);
        StringBuilder ec = new StringBuilder();
        while (sm.find()) {
            String blok = sm.group();
            if (!izinAksesibilitas && layananAksesibilitas(blok)) continue;
            if (ec.length() > 0) ec.append('\n');
            ec.append(blok);
        }
        return ec.toString();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String buatManifest(Info i) throws IOException {
        String t = new String(bacaAset("alat/AndroidManifest.template.xml"), "UTF-8");
        StringBuilder p = new StringBuilder();
        for (String name : i.perms) {
            if (name.equals("ACCESSIBILITY_SERVICE")) continue; // bukan uses-permission; layanannya disaring di readInfo
            String full = name.contains(".") ? name : "android.permission." + name;
            if (p.length() > 0) p.append('\n');
            p.append("    <uses-permission android:name=\"").append(xml(full)).append("\" />");
        }
        String hasil = t.replace("{{PACKAGE}}", i.pkg)
                .replace("{{VERSION_CODE}}", String.valueOf(i.code))
                .replace("{{VERSION_NAME}}", xml(i.appver))
                .replace("{{LABEL}}", xml(i.appname))
                .replace("{{MIN_SDK}}", String.valueOf(i.min))
                .replace("{{TARGET_SDK}}", String.valueOf(i.target))
                .replace("{{MAIN_ACTIVITY}}", aktivitasUtama(i.tipe, i.pkg))
                .replace("{{PERMISSIONS}}", p.toString())
                .replace("{{EXTRA_COMPONENTS}}", i.extraComponents == null ? "" : i.extraComponents);
        // Anti tamper (5.7): kelas Application hasil diganti PerisaiAplikasi
        // (pemeriksa sertifikat + dex + debugger + hook + FLAG_SECURE).
        // Catatan perisai.dat-nya ditulis Penandatangan saat penandatanganan.
        if (antiTamperAktif()) {
            hasil = hasil.replace("com.androlua.Aplikasi", "com.androlua.PerisaiAplikasi");
            log.append("Anti tamper aktif untuk proyek ini.\n");
        }
        return hasil;
    }

    /** Anti tamper untuk proyek ini? settings.gradle proyek menang atas bawaan Pengaturan. */
    private boolean antiTamperAktif() {
        try {
            SetelanProyek.Setelan s = SetelanProyek.baca(projectDir);
            if (s.adaAntiTamper) return s.antiTamper;
        } catch (Throwable ignored) {
        }
        try {
            return PengaturanActivity.antiTamperBawaan(ctx);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------ alur utama

    private String jalankan() throws Exception {
        kirim(PROGRESS, "Membaca data proyek...");
        Info info = bacaInit();
        log.append("Aplikasi ").append(info.appname).append(" | ").append(info.pkg).append(" | SDK ")
                .append(info.min).append('/').append(info.target).append(" | izin ").append(info.perms).append('\n');

        File lib = new File(ctx.getApplicationInfo().nativeLibraryDir);
        File aapt2 = new File(lib, "libaapt2.so");
        if (!aapt2.isFile())
            throw new IOException("aapt2 tidak ada di aplikasi (" + aapt2 + "). Alat bangun tersedia untuk arm64-v8a dan armeabi-v7a (HP ARM 64-bit dan 32-bit).");

        File work = new File(ctx.getCacheDir(), "bangun-lokal");
        hapus(work);
        File res = new File(work, "res");
        res.mkdirs();

        // 1) resource contoh + id yang sama dengan resources.arsc aplikasi ini
        kirim(PROGRESS, "Menyiapkan manifest...");
        tulis(new File(res, "drawable/icon.png"), android.util.Base64.decode(PNG_1X1, 0));
        tulis(new File(res, "values/styles.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources><style name=\"app_theme\"/></resources>\n");
        tulis(new File(res, "xml/accessibility_service_config.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<accessibility-service xmlns:android=\"http://schemas.android.com/apk/res/android\"/>\n");
        tulis(new File(res, "xml/androlua_filepaths.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<paths xmlns:android=\"http://schemas.android.com/apk/res/android\"><root-path name=\"root\" path=\".\"/></paths>\n");

        // Resource milik proyek (app/src/main/res/: layout, values/strings.xml,
        // values/styles.xml, dst.) digabung ke atas resource bawaan di atas,
        // supaya aapt2 mengompilasinya dan R.layout / R.string bisa dipakai.
        // Berkas proyek menang bila namanya sama (mis. values/styles.xml).
        salinRes(new File(projectDir, "app/src/main/res"), res);

        String[][] ids = {{"drawable", "icon"}, {"style", "app_theme"}, {"xml", "accessibility_service_config"},
                {"xml", "androlua_filepaths"}};
        StringBuilder idTxt = new StringBuilder();
        int iconId = 0;
        for (String[] d : ids) {
            int id = ctx.getResources().getIdentifier(d[1], d[0], ctx.getPackageName());
            if (id == 0) throw new IOException("Resource " + d[0] + "/" + d[1] + " tidak ditemukan di aplikasi");
            if (d[1].equals("icon")) iconId = id;
            idTxt.append(info.pkg).append(':').append(d[0]).append('/').append(d[1]).append(" = 0x")
                    .append(Integer.toHexString(id)).append('\n');
        }
        tulis(new File(work, "ids.txt"), idTxt.toString());
        tulis(new File(work, "AndroidManifest.xml"), buatManifest(info));
        File androidJar = new File(work, "android-min.jar");
        tulis(androidJar, bacaAset("alat/android-min.jar"));

        // 2) aapt2 compile + link (--java membuat R.java di paket com.androlua,
        // supaya sama dengan namespace build.gradle di build online — R dibuat
        // Gradle di com.androlua, bukan di paket proyek. Template MainActivity
        // mengimpor com.androlua.R (lihat TemplateDasar.java); tanpa
        // --custom-package ini, R.java lahir di paket proyek dan import itu
        // gagal di perangkat. --stable-ids tidak terpengaruh (hanya ID resource).
        File javaDir = new File(work, "java");
        File flat = new File(work, "compiled.zip");
        File base = new File(work, "base.apk");
        jalankanAapt2(lib, aapt2, "compile", "--dir", res.getPath(), "-o", flat.getPath());
        kirim(PROGRESS, "Mengompilasi manifest (aapt2)...");
        // Argumen link dibangun dinamis: proyek Compose menambah -R + --extra-packages
        // untuk tiap pustaka Compose (lihat siapkanResCompose), supaya aapt2
        // membuat R.java pustaka (androidx.compose.ui.R, dst.) dengan ID yang
        // cocok dengan resources.arsc.
        List<String> linkArgs = new ArrayList<String>();
        linkArgs.add("link");
        linkArgs.add("-I"); linkArgs.add(androidJar.getPath());
        linkArgs.add("--manifest"); linkArgs.add(new File(work, "AndroidManifest.xml").getPath());
        linkArgs.add("--stable-ids"); linkArgs.add(new File(work, "ids.txt").getPath());
        linkArgs.add("--custom-package"); linkArgs.add("com.androlua");
        linkArgs.add("-R"); linkArgs.add(flat.getPath());
        if ("compose".equals(info.tipe)) {
            linkArgs.addAll(siapkanResCompose(lib, aapt2, work));
        }
        linkArgs.add("--auto-add-overlay");
        linkArgs.add("--java"); linkArgs.add(javaDir.getPath());
        linkArgs.add("-o"); linkArgs.add(base.getPath());
        jalankanAapt2(lib, aapt2, linkArgs.toArray(new String[0]));
        byte[] manifest = bacaDariZip(base, "AndroidManifest.xml");
        if (manifest == null) throw new IOException("aapt2 tidak menghasilkan AndroidManifest.xml");
        if (!berisiInt(manifest, iconId))
            throw new IOException("Id resource pada manifest tidak cocok (aapt2 mengabaikan --stable-ids)");

        // 2b) proyek Java/HTML/Situs Web/Kotlin/Python/Compose: kompilasi .java/.kt -> dex
        // (ECJ + D8 untuk Java; proyek HTML/Situs Web kini punya MainActivity.java
        // sendiri sehingga dikompilasi sama seperti Java; ECJ + kotlinc + D8
        // untuk Kotlin; ECJ + D8 + kelas Chaquopy untuk Python — lihat
        // BangunPython; ECJ + kotlinc + plugin Compose + D8 untuk Compose —
        // lihat BangunCompose), digabung ke kerangka pada rakit().
        // dexProyek berupa daftar (bisa lebih dari satu): proyek Compose
        // menghasilkan beberapa classes.dex karena runtime Compose besar.
        List<byte[]> dexProyek = null;
        // Runtime Chaquopy (chaquopy/ + lib/) untuk proyek Python; null untuk
        // tipe lain. Diisi BangunPython.siapkanRuntime() lalu dibaca rakit().
        File dirPython = null;
        if ("java".equals(info.tipe) || "html".equals(info.tipe) || "situs".equals(info.tipe)) {
            kirim(PROGRESS, "Mengompilasi proyek Java...");
            List<File> rJava = new ArrayList<File>();
            kumpulBerakhiran(javaDir, ".java", rJava);
            dexProyek = java.util.Collections.singletonList(BangunJava.kompilasiUntukRakit(ctx, projectDir, rJava));
            log.append(BangunJava.ringkasanKompilasiTerakhir());
        } else if ("kotlin".equals(info.tipe)) {
            kirim(PROGRESS, "Mengompilasi proyek Kotlin...");
            List<File> rJava = new ArrayList<File>();
            kumpulBerakhiran(javaDir, ".java", rJava);
            dexProyek = java.util.Collections.singletonList(BangunKotlin.kompilasiUntukRakit(ctx, projectDir, rJava));
            log.append(BangunKotlin.ringkasanKompilasiTerakhir());
        } else if ("python".equals(info.tipe)) {
            kirim(PROGRESS, "Mengompilasi proyek Python (Chaquopy)...");
            List<File> rJava = new ArrayList<File>();
            kumpulBerakhiran(javaDir, ".java", rJava);
            File kerjaPython = new File(work, "python");
            kerjaPython.mkdirs();
            dexProyek = java.util.Collections.singletonList(BangunPython.kompilasiUntukRakit(ctx, projectDir, rJava, kerjaPython));
            log.append(BangunJava.ringkasanKompilasiTerakhir());
            kirim(PROGRESS, "Menyiapkan runtime Python (Chaquopy)...");
            dirPython = BangunPython.siapkanRuntime(ctx, projectDir, kerjaPython);
            log.append("Runtime Python (Chaquopy ").append(BangunPython.CHAQUOPY_VERSION)
                    .append(" + Python ").append(BangunPython.PYTHON_VERSION)
                    .append(") disiapkan di ").append(dirPython.getPath()).append('\n');
        } else if ("compose".equals(info.tipe)) {
            kirim(PROGRESS, "Mengompilasi proyek Compose (kotlinc + plugin Compose)...");
            List<File> rJava = new ArrayList<File>();
            kumpulBerakhiran(javaDir, ".java", rJava);
            dexProyek = BangunCompose.kompilasiUntukRakit(ctx, projectDir, rJava);
            log.append(BangunCompose.ringkasanKompilasiTerakhir());
        }

        // 3) rakit APK dari kerangka
        kirim(PROGRESS, "Merakit APK...");
        File unsigned = new File(work, "unsigned.apk");
        rakit(new File(ctx.getPackageCodePath()), manifest, unsigned, lib, dexProyek, aktivitasUtama(info.tipe, info.pkg), base, dirPython);

        // 4) simpan APK yang belum ditandatangani (tanda tangan dilakukan terpisah: lihat Penandatangan)
        kirim(PROGRESS, "Menyimpan APK...");
        File bin = new File(projectDir, "bin");
        bin.mkdirs();
        File out = new File(bin, info.pkg + "-belum-ditandatangani.apk");
        if (out.exists()) out.delete();
        salinBerkas(unsigned, out);

        hapus(work);
        return out.getPath();
    }

    private void jalankanAapt2(File lib, File aapt2, String... args) throws Exception {
        List<String> cmd = new ArrayList<String>();
        cmd.add(aapt2.getPath());
        Collections.addAll(cmd, args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("LD_LIBRARY_PATH", lib.getPath());
        pb.directory(ctx.getCacheDir());
        pb.redirectErrorStream(true);
        log.append("$ ").append(cmd.get(1)).append(' ');
        for (int i = 2; i < cmd.size(); i++) log.append(cmd.get(i)).append(' ');
        log.append('\n');
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        StringBuilder o = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) o.append(line).append('\n');
        int code = p.waitFor();
        log.append(o).append("keluar: ").append(code).append('\n');
        // Output mentah lengkap sudah dicatat di log (log-build.txt); pesan
        // yang dilempar ke pengguna hanya SATU baris pertama yang relevan,
        // diterjemahkan ke Bahasa Indonesia (lihat terjemahkanAapt2), supaya
        // dialog "Build gagal" tidak menampilkan seluruh log mentah aapt2.
        if (code != 0)
            throw new IOException("aapt2 " + cmd.get(1) + " gagal (kode " + code + "). "
                    + terjemahkanAapt2(o.toString(), cmd.get(1)));
    }

    /** Menerjemahkan baris error pertama yang relevan dari output aapt2 ke
     *  Bahasa Indonesia (nama berkas + sebab + perbaikan). Dipakai untuk
     *  compile (--dir res: nama berkas ilegal, gambar rusak, XML rusak)
     *  maupun link (resource tidak ditemukan, resource duplikat).
     *  Bila tidak ada pola yang dikenali, kembalikan baris "error" pertama
     *  apa adanya (dipotong); output mentah lengkap tetap ada di log. */
    private static String terjemahkanAapt2(String mentah, String sub) {
        if (mentah == null || mentah.trim().length() == 0)
            return "Tidak ada rincian dari aapt2. Lihat log-build.txt.";
        String[] baris = mentah.split("\n");
        for (String b : baris) {
            String kecil = b.toLowerCase(java.util.Locale.ROOT);
            if (kecil.indexOf("error") < 0 && kecil.indexOf("libpng") < 0
                    && kecil.indexOf("failed") < 0) continue;
            String berkas = ambilBerkasAapt2(b);
            String barisNo = ambilNomorBaris(b);
            String di = berkas == null ? "" : " di " + berkas + barisNo;
            if (kecil.contains("invalid file name"))
                return "Nama berkas resource tidak valid" + di
                        + ". Nama berkas di folder res hanya boleh huruf kecil (a-z), angka, garis bawah (_), dan titik. Ganti nama berkasnya, mis. \"IconKu.png\" menjadi \"icon_ku.png\".";
            if (kecil.contains("failed to crunch") || kecil.contains("libpng error")
                    || kecil.contains("not a png") || kecil.contains("failed to read png"))
                return "Gambar rusak atau bukan PNG yang valid" + di
                        + ". Simpan ulang gambar itu sebagai PNG yang benar (atau hapus bila tidak dipakai).";
            if (kecil.contains("duplicate resource"))
                return "Resource duplikat" + di
                        + ". Ada dua resource dengan nama yang sama; hapus salah satunya.";
            if (kecil.contains("not found") || kecil.contains("no resource found"))
                return "Resource tidak ditemukan" + di
                        + ". Periksa rujukan @drawable/@layout/@string/@color di berkas itu - nama resource yang dirujuk harus ada di folder res.";
            if (kecil.contains("failed to parse") || kecil.contains("failed parsing")
                    || kecil.contains("not well-formed") || kecil.contains("error parsing xml"))
                return "Berkas XML rusak" + di
                        + ". Periksa tanda kurung/tag yang belum ditutup di berkas itu.";
            // Pola tidak dikenal: pakai baris error ini apa adanya (dipotong).
            return potongPesan("aapt2 " + sub + ": " + b.trim());
        }
        // Tidak ada baris error sama sekali: ringkas baris terakhir yang tidak kosong.
        for (int i = baris.length - 1; i >= 0; i--) {
            if (baris[i].trim().length() > 0) return potongPesan("aapt2 " + sub + ": " + baris[i].trim());
        }
        return "aapt2 " + sub + " gagal tanpa rincian. Lihat log-build.txt.";
    }

    /** Ambil jalur res/... dari satu baris error aapt2 (bila ada), mis.
     *  "res/layout/activity_main.xml" dari
     *  "/data/.../cache/bangun-lokal/res/layout/activity_main.xml:10: error: ...".
     *  Bila tidak ada awalan res/, kembalikan null. */
    private static String ambilBerkasAapt2(String baris) {
        int i = baris.indexOf("res/");
        if (i < 0) return null;
        int j = i;
        while (j < baris.length()) {
            char c = baris.charAt(j);
            if (c == ' ' || c == '\t' || c == ':' || c == '\'' || c == '"' || c == '(') break;
            j++;
        }
        if (j <= i + 4) return null;
        return baris.substring(i, j);
    }

    /** Ambil nomor baris dari "res/.../nama.xml:12: error: ..." (bila ada). */
    private static String ambilNomorBaris(String baris) {
        Matcher m = Pattern.compile("res/[^ :]+:(\\d+):").matcher(baris);
        if (m.find()) return " (baris " + m.group(1) + ")";
        return "";
    }

    private static String potongPesan(String s) {
        if (s.length() > 240) return s.substring(0, 240) + "...";
        return s;
    }

    // ------------------------------------------------------------------ resource pustaka Compose

    /**
     * Menyiapkan resource pustaka Jetpack Compose untuk aapt2 link (hanya
     * untuk proyek Compose). Mengembalikan argumen tambahan untuk perintah
     * link: untuk tiap pustaka, "-R <flat>" dan "--extra-packages <paket>".
     *
     * Isi: aset alat/compose/compose-res.zip diekstrak ke work/compose-res/
     * (satu folder per paket Android, mis. androidx.compose.ui/res/ ...),
     * lalu tiap folder res/ dikompilasi aapt2 menjadi flat zip. Perintah
     * link yang memakai flat-flat itu + --extra-packages membuat aapt2
     * menghasilkan R.java untuk tiap paket pustaka (androidx.compose.ui.R,
     * androidx.compose.material3.R, androidx.core.R, ...) dengan ID yang
     * cocok dengan resources.arsc di base.apk. Tanpa ini, bytecode pustaka
     * yang merujuk R$id / R$string pustaka akan gagal dimuat (kelas R
     * tidak ada) atau membaca string yang salah.
     *
     * Catatan: aapt2 compile TIDAK bisa menerima banyak --dir sekaligus
     * (hanya --dir terakhir yang dipakai), jadi tiap pustaka dikompilasi
     * terpisah. Pustaka yang res-nya hanya values.xml kosong tetap ikut,
     * supaya R.java-nya tetap dibuat bila bytecode merujuknya.
     */
    private List<String> siapkanResCompose(File lib, File aapt2, File work) throws Exception {
        File dirRes = new File(work, "compose-res");
        hapus(dirRes);
        dirRes.mkdirs();
        ekstrakZipAset("alat/compose/compose-res.zip", dirRes);
        File[] paket = dirRes.listFiles();
        if (paket == null || paket.length == 0)
            throw new IOException("Resource pustaka Compose tidak ditemukan (alat/compose/compose-res.zip kosong). Coba pasang ulang aplikasi Create Aplikasi.");
        java.util.Arrays.sort(paket);
        List<String> tambahan = new ArrayList<String>();
        int dihitung = 0;
        for (File p : paket) {
            if (!p.isDirectory()) continue;
            File resDir = new File(p, "res");
            if (!resDir.isDirectory()) continue;
            File flat = new File(work, "compose-" + p.getName() + ".flat");
            jalankanAapt2(lib, aapt2, "compile", "--dir", resDir.getPath(), "-o", flat.getPath());
            tambahan.add("-R");
            tambahan.add(flat.getPath());
            tambahan.add("--extra-packages");
            tambahan.add(p.getName());
            dihitung++;
        }
        if (dihitung == 0)
            throw new IOException("Resource pustaka Compose tidak ditemukan (alat/compose/compose-res.zip tidak berisi folder paket). Coba pasang ulang aplikasi Create Aplikasi.");
        log.append("Resource pustaka Compose: ").append(dihitung).append(" paket digabung ke aapt2 link.\n");
        return tambahan;
    }

    /** Mengekstrak satu aset zip (dari assets aplikasi) ke folder tujuan. */
    private void ekstrakZipAset(String namaAset, File tujuan) throws IOException {
        InputStream in = ctx.getAssets().open(namaAset);
        try {
            ZipInputStream zin = new ZipInputStream(in);
            byte[] buf = new byte[65536];
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(tujuan, e.getName());
                if (e.isDirectory()) {
                    f.mkdirs();
                } else {
                    f.getParentFile().mkdirs();
                    FileOutputStream o = new FileOutputStream(f);
                    try {
                        int n;
                        while ((n = zin.read(buf)) != -1) o.write(buf, 0, n);
                    } finally {
                        o.close();
                    }
                }
                zin.closeEntry();
            }
            zin.close();
        } finally {
            in.close();
        }
    }

    // ------------------------------------------------------------------ rakit zip

    private void rakit(File template, byte[] manifest, File out, File libDir, List<byte[]> dexTambahan, String aktivitasUtama, File baseApk, File dirPython) throws Exception {
        Set<String> buang = new HashSet<String>();
        Collections.addAll(buang, BUANG_ASET);

        ZipFile zf = new ZipFile(template);
        Count cnt = new Count(new java.io.BufferedOutputStream(new FileOutputStream(out), 65536));
        ZipOutputStream zos = new ZipOutputStream(cnt);
        Set<String> ditulis = new HashSet<String>();
        // Ronde 3: mesin Lua dihapus — tidak ada lagi proyek Lua, jadi
        // library native Lua (.so) tidak pernah disalin dan runtime Lua
        // (import.lua) selalu dibuang supaya APK hasil lebih kecil.
        buang.add("import.lua");
        try {
            // manifest dulu
            tambah(zos, cnt, "AndroidManifest.xml", manifest, false);
            ditulis.add("AndroidManifest.xml");

            // proyek Java: gabungkan dex hasil kompilasi dengan dex kerangka (classes.dex, classes2.dex, ...)
            // lewat D8, supaya Activity buatan pengguna bisa dimuat bersama kelas mesin yang sudah ada.
            if (dexTambahan != null) {
                List<byte[]> dexLama = new ArrayList<byte[]>();
                int idx = 1;
                while (true) {
                    String nama = idx == 1 ? "classes.dex" : "classes" + idx + ".dex";
                    ZipEntry de = zf.getEntry(nama);
                    if (de == null) break;
                    InputStream din = zf.getInputStream(de);
                    dexLama.add(bacaStream(din));
                    din.close();
                    idx++;
                }
                Map<String, byte[]> gabungan = BangunJava.gabungDex(ctx, dexLama, dexTambahan);
                for (Map.Entry<String, byte[]> e : gabungan.entrySet()) {
                    tambah(zos, cnt, e.getKey(), e.getValue(), true);
                    ditulis.add(e.getKey());
                }
            }

            // berkas proyek -> assets/ (skrip Lua/layout dienkripsi, lihat Kripto)
            byte[] kunci = Kripto.kunciAcak();
            tambah(zos, cnt, "assets/kuncilua.bin", kunci, false);
            ditulis.add("assets/kuncilua.bin");
            Map<String, File> proyek = new LinkedHashMap<String, File>();
            kumpul(projectDir, "", proyek);
            for (Map.Entry<String, File> e : proyek.entrySet()) {
                String kunciRel = e.getKey();
                // Struktur baru Java/HTML/Kotlin (Lua dibiarkan datar):
                //   app/src/main/assets/*  -> assets/* (mis. index.html untuk HtmlActivity)
                //   app/src/main/java|kotlin/*, AndroidManifest.xml -> dilewati
                //     (salinan di app/src/main/ maupun di root proyek;
                //     .java dikompilasi terpisah oleh BangunJava; manifest build
                //     dibuat dari template di buatManifest()).
                //   app/src/main/res/* -> dilewati di sini (sudah dikompilasi
                //     aapt2 di jalankan() dan masuk lewat resources.arsc).
                if (kunciRel.equals("app/src/main/AndroidManifest.xml")
                        || kunciRel.equals("AndroidManifest.xml")
                        || kunciRel.startsWith("app/src/main/java/")
                        || kunciRel.startsWith("app/src/main/kotlin/")
                        || kunciRel.endsWith(".java") || kunciRel.endsWith(".kt")) continue;
                // Setelan proyek + kunci bawaan proyek adalah berkas milik
                // IDE, bukan isi aplikasi hasil — jangan ikut disalin.
                if (kunciRel.equals("settings.gradle")
                        || kunciRel.startsWith("tanda-tangan-bawaan/")) continue;
                // Python: kode .py di app/src/main/python/ sudah di-zip ke
                // assets/chaquopy/app.imy oleh BangunPython — jangan salin
                // mentah-mentah ke assets/ (juga jangan enkripsi seperti .lua).
                if (kunciRel.startsWith("app/src/main/python/")
                        || kunciRel.endsWith(".py")) continue;
                String name;
                String awalanAset = "app/src/main/assets/";
                if (kunciRel.startsWith(awalanAset)) {
                    name = "assets/" + kunciRel.substring(awalanAset.length());
                } else if (kunciRel.startsWith("app/")) {
                    continue;
                } else {
                    name = "assets/" + kunciRel;
                }
                if (!ditulis.add(name)) continue;
                byte[] data = baca(e.getValue());
                if (name.endsWith(".lua") || name.endsWith(".aly")) data = Kripto.enkripsi(data, kunci);
                // Audio pengguna disimpan TANPA kompresi (STORED) supaya
                // MediaPlayer/SoundPool bisa memutar langsung dari APK dan
                // berkas audio pasti ikut ter-build apa adanya. Format audio
                // (mp3/ogg) sudah terkompresi sehingga DEFLATE hampir tidak
                // menghemat ukuran.
                boolean audio = name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav")
                        || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".aac")
                        || name.endsWith(".mid") || name.endsWith(".amr") || name.endsWith(".opus");
                tambah(zos, cnt, name, data, !audio);
            }

            // Python (Chaquopy): salin runtime ke APK hasil — aset ke
            // assets/chaquopy/ dan pustaka native ke lib/<abi>/. Semua
            // ditulis STORED (tanpa kompresi): berkas .imy WAJIB stored
            // supaya AssetFinder Chaquopy bisa seek cepat (lihat Common.
            // assetZip di runtime Chaquopy), dan .so di lib/ WAJIB stored
            // supaya System.loadLibrary bisa memuatnya. Berkas proyek
            // menang bila namanya sama (ditulis.add).
            if (dirPython != null) {
                File dirChaquopy = new File(dirPython, "chaquopy");
                for (String rel : BangunPython.daftarBerkas(dirChaquopy, "")) {
                    String name = "assets/chaquopy/" + rel;
                    if (!ditulis.add(name)) continue;
                    tambah(zos, cnt, name, baca(new File(dirChaquopy, rel)), false);
                }
                File dirLibPy = new File(dirPython, "lib");
                for (String rel : BangunPython.daftarBerkas(dirLibPy, "")) {
                    String name = "lib/" + rel;
                    if (!ditulis.add(name)) continue;
                    tambah(zos, cnt, name, baca(new File(dirLibPy, rel)), false);
                }
                log.append("Runtime Python disalin ke APK: ")
                        .append(BangunPython.daftarBerkas(dirChaquopy, "").size())
                        .append(" aset chaquopy + ")
                        .append(BangunPython.daftarBerkas(dirLibPy, "").size())
                        .append(" pustaka native\n");
            }

            // Ronde 3: kunci lama IDE (kuncilua.bin) tidak ada lagi — aset Lua
            // mesin IDE sudah dihapus, jadi tidak ada lagi aset kerangka
            // .lua/.aly yang perlu dibongkar lalu dikunci ulang. Enkripsi
            // init.lua/skrip proyek pengguna (kunci baru per build, di atas)
            // tetap dipertahankan.

            // kerangka: dex, lib/, aset mesin (resources.arsc dan res/ TIDAK
            // diambil dari kerangka — lihat di bawah, diambil dari base.apk
            // hasil aapt2 supaya resource proyek ikut ke APK hasil)
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || n.equals("AndroidManifest.xml") || n.startsWith("META-INF/")) continue;
                // resources.arsc + res/ proyek sudah dikompilasi aapt2 ke base.apk;
                // bila diambil dari kerangka (APK IDE), resource proyek (layout,
                // string, style) tidak ikut sehingga MainActivity nge-blank/ANR
                // karena R.layout.activity_main tidak ketemu.
                if (n.equals("resources.arsc") || n.startsWith("res/")) continue;
                if (n.startsWith("lib/")) {
                    // Ronde 3: mesin Lua dihapus — tidak ada lagi proyek yang
                    // memuat library native, jadi semua entri lib/ dilewati
                    // supaya APK hasil lebih kecil (perilaku lama proyek non-Lua).
                    continue;
                }
                if (n.startsWith("assets/")) {
                    String rel = n.substring(7);
                    String top = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
                    if (buang.contains(top)) continue;
                }
                if (!ditulis.add(n)) continue; // berkas proyek menang
                InputStream in = zf.getInputStream(e);
                byte[] data = bacaStream(in);
                in.close();
                boolean simpan = e.getMethod() == ZipEntry.STORED;
                tambah(zos, cnt, n, data, !simpan);
            }

            // resources.arsc + res/ dari base.apk hasil aapt2 (berisi resource
            // proyek yang sudah dikompilasi: layout, string, style, warna).
            // Tanpa ini, APK hasil hanya berisi resource kerangka IDE sehingga
            // setContentView(R.layout.activity_main) gagal (resource tidak ada).
            if (baseApk != null && baseApk.isFile()) {
                ZipFile bf = new ZipFile(baseApk);
                try {
                    Enumeration<? extends ZipEntry> be = bf.entries();
                    while (be.hasMoreElements()) {
                        ZipEntry e = be.nextElement();
                        String n = e.getName();
                        if (e.isDirectory()) continue;
                        if (!n.equals("resources.arsc") && !n.startsWith("res/")) continue;
                        if (!ditulis.add(n)) continue;
                        InputStream in = bf.getInputStream(e);
                        byte[] data = bacaStream(in);
                        in.close();
                        boolean simpan = e.getMethod() == ZipEntry.STORED;
                        tambah(zos, cnt, n, data, !simpan);
                    }
                } finally {
                    bf.close();
                }
            }
        } finally {
            zos.close();
            zf.close();
        }
        log.append("Rakit selesai: ").append(ditulis.size()).append(" entri, ").append(out.length()).append(" bita\n");
    }

    private void kumpul(File dir, String prefix, Map<String, File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String rel = prefix.length() == 0 ? f.getName() : prefix + "/" + f.getName();
            if (f.isDirectory()) {
                if (prefix.length() == 0 && f.getName().equals("bin")) continue;
                kumpul(f, rel, out);
            } else {
                out.put(rel, f);
            }
        }
    }

    /** Menyalin seluruh isi folder res proyek ke folder res build (rekursif,
     *  menimpa berkas bawaan yang namanya sama). Folder proyek yang tidak ada
     *  dibiarkan begitu saja. */
    private static void salinRes(File asal, File tujuan) throws IOException {
        File[] fs = asal.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            File t = new File(tujuan, f.getName());
            if (f.isDirectory()) {
                t.mkdirs();
                salinRes(f, t);
            } else {
                salinBerkas(f, t);
            }
        }
    }

    /** Mengumpulkan berkas berakhiran tertentu dari sebuah folder (rekursif). */
    private static void kumpulBerakhiran(File dir, String akhiran, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) kumpulBerakhiran(f, akhiran, out);
            else if (f.getName().endsWith(akhiran)) out.add(f);
        }
    }

    /** Menulis satu entri; entri STORED dijajarkan ke 4 bita (syarat resources.arsc pada targetSdk 30+). */
    static void tambah(ZipOutputStream zos, Count cnt, String name, byte[] data, boolean kompres)
            throws IOException {
        zos.closeEntry();
        ZipEntry e = new ZipEntry(name);
        if (kompres) {
            e.setMethod(ZipEntry.DEFLATED);
        } else {
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            CRC32 c = new CRC32();
            c.update(data);
            e.setCrc(c.getValue());
            long header = 30 + name.getBytes("UTF-8").length;
            int pad = (int) ((4 - ((cnt.n + header) % 4)) % 4);
            if (pad > 0) e.setExtra(new byte[pad]);
        }
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    static class Count extends FilterOutputStream {
        long n;

        Count(OutputStream o) {
            super(o);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            n++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            n += len;
        }
    }

    static void salinBerkas(File from, File to) throws IOException {
        tulis(to, baca(from));
    }

    // ------------------------------------------------------------------ utilitas

    private static boolean berisiInt(byte[] d, int v) {
        byte b0 = (byte) v, b1 = (byte) (v >> 8), b2 = (byte) (v >> 16), b3 = (byte) (v >> 24);
        for (int i = 0; i + 3 < d.length; i++)
            if (d[i] == b0 && d[i + 1] == b1 && d[i + 2] == b2 && d[i + 3] == b3) return true;
        return false;
    }

    private static byte[] bacaDariZip(File zip, String name) throws IOException {
        ZipFile z = new ZipFile(zip);
        try {
            ZipEntry e = z.getEntry(name);
            if (e == null) return null;
            InputStream in = z.getInputStream(e);
            byte[] d = bacaStream(in);
            in.close();
            return d;
        } finally {
            z.close();
        }
    }

    private byte[] bacaAset(String name) throws IOException {
        InputStream in = ctx.getAssets().open(name);
        try {
            return bacaStream(in);
        } finally {
            in.close();
        }
    }

    static byte[] baca(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return bacaStream(in);
        } finally {
            in.close();
        }
    }

    static byte[] bacaStream(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    private static void tulis(File f, String s) throws IOException {
        tulis(f, s.getBytes("UTF-8"));
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

    static void hapus(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) hapus(c);
        f.delete();
    }
}
