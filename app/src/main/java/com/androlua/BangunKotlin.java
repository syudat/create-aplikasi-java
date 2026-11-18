package com.androlua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mengompilasi proyek jenis "kotlin" di perangkat (offline), tanpa GitHub Actions.
 *
 * Alur: ECJ mengompilasi .java proyek (R.java hasil aapt2 + berkas .java pengguna)
 * -> kotlinc 1.9.24 mengompilasi .kt -> .class -> D8 mengemas semuanya
 * (+ kotlin-stdlib) menjadi classes.dex, yang kemudian digabung ke APK kerangka
 * oleh BangunLokal (lihat parameter dexTambahan pada rakit()).
 *
 * Kenapa dua compiler: kotlinc membaca .java hanya untuk resolusi simbol, tidak
 * mengeluarkan bytecode-nya; jadi .java HARUS dikompilasi ECJ dulu dan hasilnya
 * ada di classpath kotlinc. Sebaliknya, .java yang merujuk kelas Kotlin tidak
 * bisa dikompilasi (ECJ jalan duluan) -- batasan V1, dicatat di panduan.
 *
 * kotlinc dimuat dari assets/alat/kotlin-compiler-dex.jar (compiler + stdlib +
 * trove4j + annotations + script-runtime, sudah di-dex) lewat DexClassLoader,
 * lalu dipanggil lewat refleksi (CLICompiler.exec, BUKAN main, supaya tidak
 * memanggil System.exit yang akan mematikan aplikasi). ServiceLoader di dalam
 * compiler memakai thread context ClassLoader, jadi sebelum exec, context
 * ClassLoader thread ini diganti ke DexClassLoader compiler, lalu dikembalikan.
 *
 * Compiler Kotlin berat: dex-nya ~56 MB di assets, dan kompilasi memakai
 * ~256 MB heap (diuji di desktop dengan -Xmx256m untuk MainActivity template).
 * Aplikasi ini sudah android:largeHeap="true", jadi di HP 8 GB masih ada ruang.
 * Build Kotlin pertama akan terasa lebih lambat (memuat dex compiler + kompilasi
 * ~8 detik di desktop; di HP bisa lebih lama).
 *
 * Konvensi V1: proyek Kotlin harus punya persis satu kelas yang menjadi Activity
 * peluncur, bernama "<packagename>.MainActivity", turunan android.app.Activity.
 */
public class BangunKotlin {

    public interface Listener {
        void onEvent(int type, String msg);
    }

    public static final int PROGRESS = 0, SUCCESS = 1, FAILED = 2;

    private final Context ctx;
    private final File projectDir;
    /** Berkas .java tambahan untuk ECJ (mis. R.java hasil aapt2, lihat BangunLokal);
     *  tidak disimpan di folder proyek, jadi tidak ikut terkumpul oleh kumpulJava. */
    private final List<File> sumberTambahan;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();

    /** Ringkasan kompilasi terakhir (daftar berkas .kt yang dikompilasi kotlinc),
     *  supaya BangunLokal bisa menulisnya ke log-build.txt. */
    private static volatile String ringkasanTerakhir = "";

    static String ringkasanKompilasiTerakhir() {
        return ringkasanTerakhir;
    }

    private BangunKotlin(Context ctx, File projectDir, Listener l, List<File> sumberTambahan) {
        this.ctx = ctx.getApplicationContext();
        this.projectDir = projectDir;
        this.listener = l;
        this.sumberTambahan = sumberTambahan;
    }

    /** Dipakai BangunLokal (sinkron, di thread sendiri): kompilasi .kt+.java -> dex tanpa UI progress. */
    static byte[] kompilasiUntukRakit(Context ctx, File projectDir, List<File> sumberTambahan) throws Exception {
        return new BangunKotlin(ctx, projectDir, null, sumberTambahan).jalankan();
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

    // ------------------------------------------------------------------ alur utama

    private byte[] jalankan() throws Exception {
        if (!new File(ctx.getApplicationInfo().nativeLibraryDir).isDirectory())
            throw new IOException("Direktori native tidak ditemukan");

        kirim(PROGRESS, "Menyiapkan compiler Kotlin (kotlinc 1.9.24 + ECJ + D8)...");
        File cache = new File(ctx.getCacheDir(), "bangun-kotlin");
        hapus(cache);
        cache.mkdirs();
        File odex = new File(cache, "odex");
        odex.mkdirs();

        File kotlinDexJar;
        try {
            kotlinDexJar = ekstrakAset("alat/kotlin-compiler-dex.jar", new File(cache, "kotlin-compiler-dex.jar"));
        } catch (IOException e) {
            // FileNotFoundException dari AssetManager hanya berisi nama aset,
            // sehingga pengguna tidak tahu maksudnya; jelaskan langsung.
            throw new IOException("Compiler Kotlin tidak ditemukan di aplikasi (alat/kotlin-compiler-dex.jar hilang). Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        // Berkas compiler seharusnya ~56 MB; bila jauh lebih kecil, hasil
        // ekstraknya terpotong/rusak dan DexClassLoader akan gagal samar.
        if (kotlinDexJar.length() < 10L * 1024 * 1024)
            throw new IOException("Compiler Kotlin rusak (alat/kotlin-compiler-dex.jar hanya "
                    + kotlinDexJar.length() + " bita, seharusnya sekitar 56 MB). Coba pasang ulang aplikasi Create Aplikasi.");
        File stdlibJar = ekstrakAset("alat/kotlin-stdlib-1.9.24.jar", new File(cache, "kotlin-stdlib-1.9.24.jar"));
        File scriptJar = ekstrakAset("alat/kotlin-script-runtime-1.9.24.jar", new File(cache, "kotlin-script-runtime-1.9.24.jar"));
        File ecjJar = ekstrakAset("alat/ecj-dex.jar", new File(cache, "ecj-dex.jar"));
        File stubJar = ekstrakAset("alat/javax-stub-dex.jar", new File(cache, "javax-stub-dex.jar"));
        File r8Jar = ekstrakAset("alat/r8-dex.jar", new File(cache, "r8-dex.jar"));
        File androidJar = ekstrakAset("alat/android-full.jar", new File(cache, "android-full.jar"));

        ClassLoader induk = BangunKotlin.class.getClassLoader();
        Object kotlinCl;
        try {
            // DexClassLoader memvalidasi dex saat dibuat; dex yang rusak
            // melempar IOException berbahasa Inggris yang samar bagi pengguna.
            kotlinCl = buatDexClassLoader(kotlinDexJar.getPath(), odex, induk);
        } catch (Exception e) {
            throw new IOException("Compiler Kotlin rusak/tidak bisa dimuat (kotlin-compiler-dex.jar mungkin rusak). Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        Object eclCl = buatDexClassLoader(ecjJar.getPath() + File.pathSeparator + stubJar.getPath(), odex, induk);
        Object r8Cl = buatDexClassLoader(r8Jar, odex, induk);

        // kotlin-home tiruan: compiler mencari kotlin-stdlib.jar di <kotlin-home>/lib.
        // Isinya salinan aset (bukan dari compiler-dex, supaya kotlinc bisa membacanya sebagai jar biasa).
        File kotlinHome = new File(cache, "kotlin-home");
        File kotlinHomeLib = new File(kotlinHome, "lib");
        kotlinHomeLib.mkdirs();
        salinBerkas(stdlibJar, new File(kotlinHomeLib, "kotlin-stdlib.jar"));
        salinBerkas(scriptJar, new File(kotlinHomeLib, "kotlin-script-runtime.jar"));

        // 1) kumpulkan berkas .kt proyek (SEMUA .kt di seluruh folder proyek,
        // bukan hanya MainActivity; hanya folder bin/ yang dilewati)
        List<File> ktFiles = new ArrayList<File>();
        kumpulBerakhiran(projectDir, ".kt", ktFiles);
        if (ktFiles.isEmpty())
            throw new IOException("Tidak ada berkas .kt di proyek ini");

        // Berkas .java proyek (dipakai ECJ; R.java hasil aapt2 ditambah dari sumberTambahan)
        List<File> javaFiles = new ArrayList<File>();
        kumpulJava(projectDir, javaFiles);
        int jumlahR = sumberTambahan == null ? 0 : sumberTambahan.size();
        if (sumberTambahan != null) javaFiles.addAll(sumberTambahan);

        StringBuilder ringkasan = new StringBuilder();
        ringkasan.append("Berkas .kt proyek yang dikompilasi kotlinc (").append(ktFiles.size()).append("):\n");
        String akar = projectDir.getAbsolutePath();
        for (File f : ktFiles) {
            String p = f.getAbsolutePath();
            if (p.startsWith(akar)) p = p.substring(akar.length() + 1);
            ringkasan.append("  - ").append(p).append('\n');
        }
        if (!javaFiles.isEmpty())
            ringkasan.append("Berkas .java proyek yang dikompilasi ECJ (").append(javaFiles.size() - jumlahR).append(" + ").append(jumlahR).append(" R.java hasil aapt2).\n");
        ringkasanTerakhir = ringkasan.toString();

        File javaOut = new File(cache, "classes-java");
        javaOut.mkdirs();
        File kotlinOut = new File(cache, "classes-kotlin");
        kotlinOut.mkdirs();

        // 2) ECJ untuk .java (R.java + .java pengguna) -> javaOut.
        // kotlinc butuh hasil ini di classpath-nya (R + kelas Java pengguna).
        if (!javaFiles.isEmpty()) {
            kirim(PROGRESS, "Mengompilasi " + javaFiles.size() + " berkas .java (ECJ)...");
            kompilasiECJ(eclCl, javaFiles, androidJar, javaOut);
        }

        // 3) kotlinc untuk .kt -> kotlinOut
        kirim(PROGRESS, "Mengompilasi " + ktFiles.size() + " berkas .kt (kotlinc)...");
        kompilasiKotlin(kotlinCl, ktFiles, androidJar, stdlibJar, javaOut, kotlinHome, kotlinOut);

        // 4) dex hasil kompilasi (ECJ + kotlinc + stdlib) dengan D8 (R8 dalam mode dexer saja)
        kirim(PROGRESS, "Mengemas bytecode Kotlin (D8)...");
        List<File> classFiles = new ArrayList<File>();
        kumpulBerakhiran(javaOut, ".class", classFiles);
        kumpulBerakhiran(kotlinOut, ".class", classFiles);
        // Kelas R hasil aapt2 jangan ikut di-dex (lihat BangunJava.buangKelasR):
        // dex kerangka sudah berisi com.androlua.R yang sama.
        BangunJava.buangKelasR(classFiles);
        if (classFiles.isEmpty()) throw new IOException("kotlinc/ECJ tidak menghasilkan berkas .class");

        // Proyek Kotlin WAJIB punya kelas MainActivity (peluncur di manifest).
        // Tanpa pemeriksaan ini, build "berhasil" tetapi APK hasil langsung
        // crash saat dibuka karena Activity peluncur tidak ada. Periksa nama
        // berkas .class-nya saja: MainActivity boleh didefinisikan di .kt
        // maupun .java, tetapi harus ada setelah kompilasi.
        boolean adaMainActivity = false;
        for (File f : classFiles) {
            if (f.getName().equals("MainActivity.class")) { adaMainActivity = true; break; }
        }
        if (!adaMainActivity)
            throw new IOException("Proyek Kotlin tidak punya MainActivity: buat MainActivity.kt "
                    + "(atau MainActivity.java) berisi kelas MainActivity turunan android.app.Activity "
                    + "di paket proyek, lalu bangun lagi.");

        List<Path> programPaths = new ArrayList<Path>();
        for (File f : classFiles) programPaths.add(f.toPath());
        programPaths.add(stdlibJar.toPath());

        File dexOut = new File(cache, "dexout");
        dexOut.mkdirs();
        jalankanD8(r8Cl, programPaths, androidJar.toPath(), dexOut);

        File hasil = new File(dexOut, "classes.dex");
        if (!hasil.isFile()) throw new IOException("D8 tidak menghasilkan classes.dex");
        byte[] data = BangunLokal.baca(hasil);
        kirim(PROGRESS, "Kompilasi Kotlin selesai (" + data.length + " bita dex).");
        return data;
    }

    // ------------------------------------------------------------------ ECJ (.java)

    /** Kompilasi .java dengan ECJ (pola yang sama seperti BangunJava.jalankan). */
    private void kompilasiECJ(Object eclCl, List<File> javaFiles, File androidJar, File outDir) throws Exception {
        List<String> args = new ArrayList<String>();
        args.add("-nowarn");
        // -proc:none: matikan annotation processing (lihat BangunJava).
        args.add("-proc:none");
        args.add("-source");
        args.add("1.8");
        args.add("-target");
        args.add("1.8");
        args.add("-cp");
        args.add(androidJar.getPath());
        args.add("-d");
        args.add(outDir.getPath());
        for (File f : javaFiles) args.add(f.getPath());

        Class<?> cBatch = loadClass(eclCl, "org.eclipse.jdt.core.compiler.batch.BatchCompiler");
        Class<?> cProgress = loadClass(eclCl, "org.eclipse.jdt.core.compiler.CompilationProgress");
        Method mCompile = cBatch.getMethod("compile", String[].class, java.io.PrintWriter.class, java.io.PrintWriter.class, cProgress);
        java.io.StringWriter outSw = new java.io.StringWriter(), errSw = new java.io.StringWriter();
        java.io.PrintWriter outPw = new java.io.PrintWriter(outSw), errPw = new java.io.PrintWriter(errSw);
        boolean ok;
        try {
            ok = (Boolean) mCompile.invoke(null, (Object) args.toArray(new String[0]), outPw, errPw, null);
        } catch (java.lang.reflect.InvocationTargetException ite) {
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
            throw new IOException("Kompilasi Java (untuk Kotlin) gagal:\n" + errSw + (outSw.getBuffer().length() > 0 ? "\n" + outSw : ""));
    }

    // ------------------------------------------------------------------ kotlinc (.kt)

    /**
     * Kompilasi .kt dengan kotlinc 1.9.24 lewat refleksi.
     * Dipanggil CLICompiler.exec (bukan main) supaya tidak System.exit.
     * Pesan compiler ditangkap dari System.err selama exec, lalu dikembalikan.
     */
    private void kompilasiKotlin(Object kotlinCl, List<File> ktFiles, File androidJar,
            File stdlibJar, File javaOut, File kotlinHome, File outDir) throws Exception {
        Class<?> cCompiler;
        try {
            cCompiler = loadClass(kotlinCl, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler");
        } catch (Exception e) {
            throw new IOException("Compiler Kotlin rusak (kelas K2JVMCompiler tidak ditemukan di kotlin-compiler-dex.jar). Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        Object compiler = cCompiler.getDeclaredConstructor().newInstance();

        List<String> args = new ArrayList<String>();
        args.add("-kotlin-home");
        args.add(kotlinHome.getPath());
        args.add("-cp");
        args.add(androidJar.getPath() + File.pathSeparator + stdlibJar.getPath() + File.pathSeparator + javaOut.getPath());
        args.add("-d");
        args.add(outDir.getPath());
        args.add("-jvm-target");
        args.add("1.8");
        args.add("-no-reflect");
        for (File f : ktFiles) args.add(f.getPath());

        // ServiceLoader di dalam compiler memakai thread context ClassLoader;
        // arahkan ke DexClassLoader compiler supaya META-INF/services di
        // kotlin-compiler-dex.jar (mis. BuiltInsLoader) ditemukan.
        Thread threadIni = Thread.currentThread();
        ClassLoader clLama = threadIni.getContextClassLoader();
        PrintStream errLama = System.err;
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream errBaru = new PrintStream(errBuf, true, "UTF-8");
        Object exitCode;
        try {
            threadIni.setContextClassLoader((ClassLoader) kotlinCl);
            System.setErr(errBaru);
            Method mExec = cCompiler.getMethod("exec", String[].class);
            exitCode = mExec.invoke(compiler, (Object) args.toArray(new String[0]));
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable penyebab = ite.getCause();
            if (penyebab != null) {
                log.append("Disebabkan oleh: ").append(penyebab).append('\n');
                if (penyebab instanceof Exception) throw (Exception) penyebab;
                if (penyebab instanceof Error) throw (Error) penyebab;
            }
            throw ite;
        } finally {
            System.setErr(errLama);
            threadIni.setContextClassLoader(clLama);
            try { errBaru.close(); } catch (Exception ignored) {}
        }
        String errText = errBuf.toString("UTF-8");
        log.append(errText);
        if (!"OK".equals(String.valueOf(exitCode))) {
            String rincian = errText.trim().length() > 0 ? errText
                    : "(compiler tidak menulis rincian; lihat log-build.txt)";
            throw new IOException("Kompilasi Kotlin gagal (" + exitCode + "):\n" + rincian);
        }
    }

    // ------------------------------------------------------------------ D8 (dex)

    /** Dex dengan D8 (pola yang sama seperti BangunJava.jalankan / gabungDex). */
    private static void jalankanD8(Object r8Cl, List<Path> programPaths, Path androidJar, File dexOut) throws Exception {
        Class<?> cD8Command = loadClass(r8Cl, "com.android.tools.r8.D8Command");
        Class<?> cD8 = loadClass(r8Cl, "com.android.tools.r8.D8");
        Class<?> cOutputMode = loadClass(r8Cl, "com.android.tools.r8.OutputMode");

        Object builder = cD8Command.getMethod("builder").invoke(null);
        Class<?> cBuilder = builder.getClass();
        cBuilder.getMethod("addProgramFiles", Path[].class).invoke(builder, (Object) programPaths.toArray(new Path[0]));
        cBuilder.getMethod("addLibraryFiles", Path[].class).invoke(builder, (Object) new Path[]{androidJar});
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
    }

    // ------------------------------------------------------------------ util (pola yang sama seperti BangunJava)

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
            if (f.isDirectory()) {
                if (f.getName().equals("bin")) continue;
                kumpulBerakhiran(f, akhiran, out);
            } else if (f.getName().endsWith(akhiran)) {
                out.add(f);
            }
        }
    }

    private File ekstrakAset(String nama, File tujuan) throws IOException {
        if (tujuan.isFile()) return tujuan; // sudah diekstrak sebelumnya di sesi/cache ini
        InputStream in = ctx.getAssets().open(nama);
        try {
            FileOutputStream o = new FileOutputStream(tujuan);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
            } finally {
                o.close();
            }
        } finally {
            in.close();
        }
        return tujuan;
    }

    private static void salinBerkas(File asal, File tujuan) throws IOException {
        InputStream in = new java.io.FileInputStream(asal);
        try {
            FileOutputStream o = new FileOutputStream(tujuan);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
            } finally {
                o.close();
            }
        } finally {
            in.close();
        }
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
