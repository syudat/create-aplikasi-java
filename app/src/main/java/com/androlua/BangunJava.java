package com.androlua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mengompilasi proyek jenis "java" di perangkat (offline), tanpa Termux/JDK luar:
 * ECJ (Eclipse Compiler for Java) mengompilasi .java -> .class, lalu R8/D8 mengemasnya
 * menjadi classes.dex tambahan, yang kemudian digabung ke dalam APK kerangka oleh
 * BangunLokal (lihat parameter dexTambahan pada rakit()/jalankan()).
 *
 * ECJ dan D8 TIDAK dijadikan dependensi Gradle aplikasi ini (supaya tidak ikut
 * membengkakkan classes.dex milik aplikasi ini sendiri, yang dipakai ulang sebagai
 * kerangka APK hasil build). Keduanya disimpan sebagai aset biasa yang SUDAH di-dex
 * sebelumnya (assets/alat/ecj-dex.jar, assets/alat/r8-dex.jar) dan dimuat saat
 * dibutuhkan saja memakai DexClassLoader, dipanggil lewat refleksi.
 *
 * Konvensi V1: proyek Java harus punya persis satu kelas yang menjadi Activity
 * peluncur, bernama "<packagename>.MainActivity", turunan android.app.Activity.
 */
public class BangunJava {

    public interface Listener {
        void onEvent(int type, String msg);
    }

    public static final int PROGRESS = 0, SUCCESS = 1, FAILED = 2;

    private final Context ctx;
    private final File projectDir;
    /** Berkas .java tambahan untuk ECJ (mis. R.java hasil aapt2, lihat BangunLokal);
     *  tidak disimpan di folder proyek, jadi tidak ikut terkumpul oleh kumpulJava. */
    private final List<File> sumberTambahan;
    /** Jar tambahan di classpath ECJ (mis. chaquopy_java.jar untuk proyek Python)
     *  dan sebagai masukan program D8 (kelas-kelasnya ikut di-dex). Null = tidak ada. */
    private final List<File> pustakaTambahan;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();

    /** Ringkasan kompilasi terakhir (daftar berkas .java yang dikompilasi ECJ),
     *  supaya BangunLokal bisa menulisnya ke log-build.txt dan pengguna bisa
     *  memastikan semua berkas di paketnya ikut di-build, bukan hanya
     *  MainActivity. */
    private static volatile String ringkasanTerakhir = "";

    static String ringkasanKompilasiTerakhir() {
        return ringkasanTerakhir;
    }

    private BangunJava(Context ctx, File projectDir, Listener l) {
        this(ctx, projectDir, l, null, null);
    }

    private BangunJava(Context ctx, File projectDir, Listener l, List<File> sumberTambahan) {
        this(ctx, projectDir, l, sumberTambahan, null);
    }

    private BangunJava(Context ctx, File projectDir, Listener l, List<File> sumberTambahan,
                       List<File> pustakaTambahan) {
        this.ctx = ctx.getApplicationContext();
        this.projectDir = projectDir;
        this.listener = l;
        this.sumberTambahan = sumberTambahan;
        this.pustakaTambahan = pustakaTambahan;
    }

    public static void bangun(final Context ctx, final String projectDir, final Listener l) {
        final BangunJava b = new BangunJava(ctx, new File(projectDir), l);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] dex = b.jalankan();
                    b.kirimDex(dex);
                } catch (Throwable e) {
                    String pesan = e.getMessage();
                    if (pesan == null && e.getCause() != null) pesan = e.getCause().toString();
                    b.kirim(FAILED, "Gagal: " + pesan);
                }
            }
        }, "BangunJava").start();
    }

    /** Dipakai BangunLokal (sinkron, di thread sendiri): kompilasi .java -> dex tanpa UI progress. */
    static byte[] kompilasiUntukRakit(Context ctx, File projectDir) throws Exception {
        return new BangunJava(ctx, projectDir, null).jalankan();
    }

    /** Sama seperti di atas, ditambah berkas .java hasil aapt2 (R.java) supaya
     *  kode proyek yang memakai R.layout / R.string bisa dikompilasi ECJ. */
    static byte[] kompilasiUntukRakit(Context ctx, File projectDir, List<File> sumberTambahan) throws Exception {
        return new BangunJava(ctx, projectDir, null, sumberTambahan).jalankan();
    }

    /** Kompilasi proyek Python (Chaquopy): seperti kompilasiUntukRakit, tapi
     *  pustakaTambahan (mis. chaquopy_java.jar) ikut di classpath ECJ — supaya
     *  host MainActivity yang mengimpor com.chaquo.python.* bisa dikompilasi —
     *  dan kelas-kelasnya ikut di-dex oleh D8, supaya runtime Chaquopy ada di
     *  APK hasil. Dipakai BangunPython (lihat BangunLokal.java). */
    static byte[] kompilasiUntukRakitPython(Context ctx, File projectDir, List<File> sumberTambahan,
                                            List<File> pustakaTambahan) throws Exception {
        return new BangunJava(ctx, projectDir, null, sumberTambahan, pustakaTambahan).jalankan();
    }

    private void kirim(final int type, final String msg) {
        if (listener == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onEvent(type, msg);
            }
        });
    }

    // Build dex berhasil: BangunLokal yang akan merakit APK-nya (lihat main.lua: doBuildJava).
    private File hasilDex;

    private void kirimDex(byte[] dex) throws IOException {
        File f = new File(ctx.getCacheDir(), "bangun-java/hasil.dex");
        f.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(dex);
        o.close();
        hasilDex = f;
        kirim(SUCCESS, f.getPath());
    }

    // ------------------------------------------------------------------ alur utama

    private byte[] jalankan() throws Exception {
        if (!new File(ctx.getApplicationInfo().nativeLibraryDir).isDirectory())
            throw new IOException("Direktori native tidak ditemukan");

        kirim(PROGRESS, "Menyiapkan compiler Java (ECJ + D8)...");
        File cache = new File(ctx.getCacheDir(), "bangun-java");
        hapus(cache);
        cache.mkdirs();
        File odex = new File(cache, "odex");
        odex.mkdirs();

        File ecjJar = ekstrakAset("alat/ecj-dex.jar", new File(cache, "ecj-dex.jar"));
        File stubJar = ekstrakAset("alat/javax-stub-dex.jar", new File(cache, "javax-stub-dex.jar"));
        File r8Jar = ekstrakAset("alat/r8-dex.jar", new File(cache, "r8-dex.jar"));
        File androidJar = ekstrakAset("alat/android-full.jar", new File(cache, "android-full.jar"));

        ClassLoader induk = BangunJava.class.getClassLoader();
        // ECJ butuh javax.lang.model.SourceVersion yang tidak ada di Android;
        // stub minimalnya ada di javax-stub-dex.jar, dimuat bersama ecj-dex.jar.
        Object eclCl = buatDexClassLoader(ecjJar.getPath() + File.pathSeparator + stubJar.getPath(), odex, induk);
        Object r8Cl = buatDexClassLoader(r8Jar, odex, induk);

        // 1) kumpulkan berkas .java proyek (SEMUA .java di seluruh folder
        // proyek, bukan hanya MainActivity: kumpulJava menelusuri rekursif
        // dan hanya melewati folder bin/)
        List<File> javaFiles = new ArrayList<File>();
        ringkasanTerakhir = "";
        kumpulJava(projectDir, javaFiles);
        int jumlahR = sumberTambahan == null ? 0 : sumberTambahan.size();
        if (sumberTambahan != null) javaFiles.addAll(sumberTambahan);
        if (javaFiles.isEmpty())
            throw new IOException("Tidak ada berkas .java di proyek ini");

        // Catat daftar berkas proyek yang dikompilasi supaya BangunLokal bisa
        // menulisnya ke log-build.txt (pengguna bisa memastikan berkas di
        // samping MainActivity ikut di-build).
        int jumlahProyek = javaFiles.size() - jumlahR;
        StringBuilder ringkasan = new StringBuilder();
        ringkasan.append("Berkas .java proyek yang dikompilasi ECJ (").append(jumlahProyek).append(")");
        if (jumlahR > 0) ringkasan.append(" + ").append(jumlahR).append(" R.java hasil aapt2");
        ringkasan.append(":\n");
        String akar = projectDir.getAbsolutePath();
        for (int i = 0; i < jumlahProyek; i++) {
            String p = javaFiles.get(i).getAbsolutePath();
            if (p.startsWith(akar)) p = p.substring(akar.length() + 1);
            ringkasan.append("  - ").append(p).append('\n');
        }
        ringkasanTerakhir = ringkasan.toString();

        File classesOut = new File(cache, "classes");
        classesOut.mkdirs();

        // 2) kompilasi dengan ECJ
        kirim(PROGRESS, "Mengompilasi " + javaFiles.size() + " berkas .java (ECJ)...");
        List<String> args = new ArrayList<String>();
        args.add("-nowarn");
        // -proc:none: matikan annotation processing. Tanpa ini ECJ memuat
        // javax.annotation.processing.* (RoundEnvironment dll.) yang juga
        // tidak ada di Android, dan kompilasi gagal sebelum mulai.
        args.add("-proc:none");
        args.add("-source");
        args.add("1.8");
        args.add("-target");
        args.add("1.8");
        args.add("-cp");
        // Classpath ECJ: android.jar + pustaka tambahan (mis. chaquopy_java.jar
        // untuk proyek Python, supaya impor com.chaquo.python.* teratasi).
        StringBuilder cp = new StringBuilder(androidJar.getPath());
        if (pustakaTambahan != null) {
            for (File lib : pustakaTambahan) {
                if (lib != null && lib.isFile()) cp.append(File.pathSeparator).append(lib.getPath());
            }
        }
        args.add(cp.toString());
        args.add("-d");
        args.add(classesOut.getPath());
        for (File f : javaFiles) args.add(f.getPath());

        Class<?> cBatch = loadClass(eclCl, "org.eclipse.jdt.core.compiler.batch.BatchCompiler");
        Class<?> cProgress = loadClass(eclCl, "org.eclipse.jdt.core.compiler.CompilationProgress");
        Method mCompile = cBatch.getMethod("compile", String[].class, PrintWriter.class, PrintWriter.class, cProgress);
        StringWriter outSw = new StringWriter(), errSw = new StringWriter();
        PrintWriter outPw = new PrintWriter(outSw), errPw = new PrintWriter(errSw);
        boolean ok;
        try {
            ok = (Boolean) mCompile.invoke(null, (Object) args.toArray(new String[0]), outPw, errPw, null);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // mCompile.invoke membungkus error ECJ di InvocationTargetException;
            // lempar penyebab aslinya supaya BangunLokal mencatatnya di
            // log-build.txt (lihat tulisRantaiPenyebab di BangunLokal).
            Throwable penyebab = ite.getCause();
            if (penyebab != null) {
                log.append("Disebabkan oleh: ").append(penyebab).append('\n');
                for (StackTraceElement s : penyebab.getStackTrace()) {
                    log.append("    di ").append(s).append('\n');
                }
                if (penyebab instanceof Exception) throw (Exception) penyebab;
                if (penyebab instanceof Error) throw (Error) penyebab;
            }
            throw ite;
        }
        outPw.flush();
        errPw.flush();
        log.append(outSw).append(errSw);
        if (!ok)
            throw new IOException("Kompilasi Java gagal:\n" + errSw + (outSw.getBuffer().length() > 0 ? "\n" + outSw : ""));

        // 3) dex hasil kompilasi dengan D8 (R8 dalam mode dexer saja)
        kirim(PROGRESS, "Mengemas bytecode Java (D8)...");
        List<File> classFiles = new ArrayList<File>();
        kumpulBerakhiran(classesOut, ".class", classFiles);
        if (classFiles.isEmpty()) throw new IOException("ECJ tidak menghasilkan berkas .class");

        File dexOut = new File(cache, "dexout");
        dexOut.mkdirs();

        Class<?> cD8Command = loadClass(r8Cl, "com.android.tools.r8.D8Command");
        Class<?> cD8 = loadClass(r8Cl, "com.android.tools.r8.D8");
        Class<?> cOutputMode = loadClass(r8Cl, "com.android.tools.r8.OutputMode");

        Object builder = cD8Command.getMethod("builder").invoke(null);
        Class<?> cBuilder = builder.getClass();

        Path[] progPaths = new Path[classFiles.size()];
        for (int k = 0; k < classFiles.size(); k++) progPaths[k] = classFiles.get(k).toPath();
        // Pustaka tambahan (mis. chaquopy_java.jar) ikut sebagai masukan program
        // D8 — D8 menerima jar sebagai program file, jadi kelas-kelasnya
        // (com.chaquo.python.*) ikut di-dex ke dalam classes.dex hasil.
        if (pustakaTambahan != null && !pustakaTambahan.isEmpty()) {
            List<Path> semua = new ArrayList<Path>();
            for (Path p : progPaths) semua.add(p);
            for (File lib : pustakaTambahan) {
                if (lib != null && lib.isFile()) semua.add(lib.toPath());
            }
            progPaths = semua.toArray(new Path[0]);
        }
        cBuilder.getMethod("addProgramFiles", Path[].class).invoke(builder, (Object) progPaths);
        cBuilder.getMethod("addLibraryFiles", Path[].class).invoke(builder, (Object) new Path[]{androidJar.toPath()});
        cBuilder.getMethod("setMinApiLevel", int.class).invoke(builder, 21);
        Object dexIndexed = null;
        for (Object c : cOutputMode.getEnumConstants()) {
            if (c.toString().equals("DexIndexed")) dexIndexed = c;
        }
        if (dexIndexed == null) throw new IOException("OutputMode.DexIndexed tidak ditemukan");
        cBuilder.getMethod("setOutput", Path.class, cOutputMode).invoke(builder, dexOut.toPath(), dexIndexed);
        Object command = cBuilder.getMethod("build").invoke(builder);
        Method mRun = cD8.getMethod("run", cD8Command);
        mRun.invoke(null, command);

        File hasil = new File(dexOut, "classes.dex");
        if (!hasil.isFile()) throw new IOException("D8 tidak menghasilkan classes.dex");
        byte[] data = BangunLokal.baca(hasil);
        kirim(PROGRESS, "Kompilasi Java selesai (" + data.length + " bita dex).");
        return data;
    }

    /**
     * Menggabungkan dex lama (dex kerangka mesin IDE, satu atau beberapa classes*.dex) dengan
     * dex baru (hasil kompilasi proyek Java/Kotlin/Python/Compose) memakai D8 (mode merge),
     * supaya kelas Activity buatan pengguna bisa dimuat berdampingan dengan kelas mesin yang
     * sudah ada di kerangka. dexBaru boleh berisi lebih dari satu berkas (proyek Compose:
     * runtime Compose besar sehingga D8 memecahnya ke beberapa classes.dex).
     * Hasil: peta nama berkas -> isi (classes.dex, classes2.dex, dst. bila D8 memecahnya).
     */
    static java.util.Map<String, byte[]> gabungDex(Context ctx, List<byte[]> dexLama, List<byte[]> dexBaru) throws Exception {
        File cache = new File(ctx.getCacheDir(), "bangun-java-gabung");
        hapus(cache);
        cache.mkdirs();
        File odex = new File(cache, "odex");
        odex.mkdirs();
        File r8Jar = new File(cache, "r8-dex.jar");
        InputStream in = ctx.getAssets().open("alat/r8-dex.jar");
        FileOutputStream o = new FileOutputStream(r8Jar);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
        o.close();
        in.close();

        File androidJar = new File(cache, "android-full.jar");
        in = ctx.getAssets().open("alat/android-full.jar");
        o = new FileOutputStream(androidJar);
        while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
        o.close();
        in.close();

        List<Path> dexPaths = new ArrayList<Path>();
        int idx = 0;
        for (byte[] d : dexLama) {
            File f = new File(cache, "lama" + (idx++) + ".dex");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(d);
            fo.close();
            dexPaths.add(f.toPath());
        }
        // dexBaru bisa lebih dari satu berkas (proyek Compose: runtime Compose
        // besar sehingga D8 memecahnya ke classes.dex, classes2.dex, ...).
        // Tulis semuanya sebagai program file D8; D8 menggabung lalu memecah
        // lagi sesuai batas 64K metode.
        int idxBaru = 0;
        for (byte[] d : dexBaru) {
            File baruFile = new File(cache, "baru" + (idxBaru++) + ".dex");
            FileOutputStream fo = new FileOutputStream(baruFile);
            fo.write(d);
            fo.close();
            dexPaths.add(baruFile.toPath());
        }

        ClassLoader induk = BangunJava.class.getClassLoader();
        Object r8Cl = buatDexClassLoader(r8Jar, odex, induk);
        Class<?> cD8Command = loadClass(r8Cl, "com.android.tools.r8.D8Command");
        Class<?> cD8 = loadClass(r8Cl, "com.android.tools.r8.D8");
        Class<?> cOutputMode = loadClass(r8Cl, "com.android.tools.r8.OutputMode");

        Object builder = cD8Command.getMethod("builder").invoke(null);
        Class<?> cBuilder = builder.getClass();
        cBuilder.getMethod("addProgramFiles", Path[].class).invoke(builder, (Object) dexPaths.toArray(new Path[0]));
        cBuilder.getMethod("addLibraryFiles", Path[].class).invoke(builder, (Object) new Path[]{androidJar.toPath()});
        cBuilder.getMethod("setMinApiLevel", int.class).invoke(builder, 21);
        Object dexIndexed = null;
        for (Object c : cOutputMode.getEnumConstants()) if (c.toString().equals("DexIndexed")) dexIndexed = c;
        File dexOut = new File(cache, "dexout");
        dexOut.mkdirs();
        cBuilder.getMethod("setOutput", Path.class, cOutputMode).invoke(builder, dexOut.toPath(), dexIndexed);
        Object command = cBuilder.getMethod("build").invoke(builder);
        cD8.getMethod("run", cD8Command).invoke(null, command);

        java.util.Map<String, byte[]> hasil = new java.util.LinkedHashMap<String, byte[]>();
        File[] out = dexOut.listFiles();
        if (out != null) {
            for (File f : out) {
                if (f.getName().endsWith(".dex")) hasil.put(f.getName(), BangunLokal.baca(f));
            }
        }
        if (hasil.isEmpty()) throw new IOException("D8 gagal menggabungkan dex");
        return hasil;
    }

    // ------------------------------------------------------------------ util

    private static Object buatDexClassLoader(File jar, File odex, ClassLoader induk) throws Exception {
        return buatDexClassLoader(jar.getPath(), odex, induk);
    }

    private static Object buatDexClassLoader(String dexPath, File odex, ClassLoader induk) throws Exception {
        Class<?> c = Class.forName("dalvik.system.DexClassLoader");
        return c.getConstructor(String.class, String.class, String.class, ClassLoader.class)
                .newInstance(dexPath, odex.getPath(), null, induk);
    }

    private static Class<?> loadClass(Object dexClassLoader, String nama) throws Exception {
        Method m = dexClassLoader.getClass().getMethod("loadClass", String.class);
        return (Class<?>) m.invoke(dexClassLoader, nama);
    }

    private static void kumpulJava(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                if (f.getName().equals("bin")) continue;
                kumpulJava(f, out);
            } else if (f.getName().endsWith(".java")) {
                out.add(f);
            }
        }
    }

    private static void kumpulBerakhiran(File dir, String akhiran, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) kumpulBerakhiran(f, akhiran, out);
            else if (f.getName().endsWith(akhiran)) out.add(f);
        }
    }

    private File ekstrakAset(String nama, File tujuan) throws IOException {
        if (tujuan.isFile()) return tujuan; // sudah diekstrak sebelumnya di sesi/cache ini
        InputStream in = ctx.getAssets().open(nama);
        FileOutputStream o = new FileOutputStream(tujuan);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
        o.close();
        in.close();
        return tujuan;
    }

    private static void hapus(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) hapus(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
