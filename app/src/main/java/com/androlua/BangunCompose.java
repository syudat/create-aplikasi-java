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
 * Mengompilasi proyek jenis "compose" di perangkat (offline), tanpa GitHub Actions.
 *
 * Alur: ECJ mengompilasi .java proyek (R.java hasil aapt2 — R aplikasi
 * com.androlua.R + R pustaka androidx.compose.* hasil --extra-packages,
 * lihat BangunLokal) -> kotlinc 1.9.24 + plugin compiler Compose 1.5.14
 * mengompilasi .kt -> .class -> D8 mengemas semuanya (+ runtime Compose
 * + kotlin-stdlib) menjadi classes.dex (bisa lebih dari satu: runtime
 * Compose besar, jadi D8 memecahnya ke classes.dex, classes2.dex, ...).
 * Hasil dikembalikan sebagai daftar byte[] dan digabung ke APK kerangka
 * oleh BangunLokal.rakit() lewat BangunJava.gabungDex().
 *
 * Kenapa dua compiler: sama seperti BangunKotlin — kotlinc membaca .java
 * hanya untuk resolusi simbol, tidak mengeluarkan bytecode-nya; jadi .java
 * HARUS dikompilasi ECJ dulu dan hasilnya ada di classpath kotlinc.
 *
 * Plugin compiler Compose dimuat lewat -Xplugin ke kotlinc. Plugin ini
 * (androidx.compose.compiler:compiler:1.5.14, cocok dengan Kotlin 1.9.24)
 * sudah di-dex menjadi assets/alat/compose/compose-compiler-dex.jar
 * (berisi classes.dex + META-INF/services). Cara muatnya:
 *  - DexClassLoader compiler dibuat dari kotlin-compiler-dex.jar DAN
 *    compose-compiler-dex.jar (dipisah File.pathSeparator), supaya kelas
 *    plugin (ComposePluginRegistrar, ComposeCommandLineProcessor, ...)
 *    bisa dimuat lewat delegasi ke induk (parent) ClassLoader.
 *  - kotlinc dipanggil dengan -Xplugin=<jalur compose-compiler-dex.jar>.
 *    ServiceLoaderLite di dalam compiler membaca META-INF/services
 *    langsung dari berkas jar itu (ZipFile, bukan getResources), lalu
 *    memuat kelas implementasinya lewat Class.forName yang mendelegasikan
 *    ke DexClassLoader di atas. Jadi plugin ditemukan dan aktif.
 *  Tanpa plugin, @Composable tidak diubah (LayarUtama tetap
 *  ()V, bukan (Composer;I)V) dan setContent gagal saat jalan.
 *
 * Runtime Compose (compose-ui, foundation, material3, activity-compose,
 * dll. — BOM 2023.06.01: ui/foundation/runtime 1.4.3, material3 1.1.1,
 * activity-compose 1.7.2) sudah digabung menjadi satu
 * assets/alat/compose/compose-runtime.jar (16 MB, 50 artefak). Berkas ini
 * dipakai sebagai classpath kotlinc DAN sebagai program file D8, sehingga
 * kelas-kelas Compose ikut ke APK hasil. Resource pustaka (strings
 * material3, id view-tag androidx.compose.ui, dst.) ada di
 * assets/alat/compose/compose-res.zip; BangunLokal mengekstraknya dan
 * menggabungkannya ke aapt2 link dengan --extra-packages, supaya R.java
 * pustaka (androidx.compose.ui.R, androidx.compose.material3.R, ...)
 * dibuat dengan ID yang cocok dengan resources.arsc.
 *
 * Compiler Kotlin berat: dex-nya ~56 MB di assets, plugin ~400 KB,
 * runtime ~16 MB; kompilasi memakai ~256 MB heap (diuji di desktop
 * dengan -Xmx256m untuk MainActivity template). Aplikasi ini sudah
 * android:largeHeap="true", jadi di HP 8 GB masih ada ruang. Build
 * Compose pertama akan terasa lebih lambat (memuat dex compiler +
 * plugin + runtime, kompilasi kotlinc ~26 detik di desktop; di HP bisa
 * lebih lama).
 *
 * Konvensi V1: proyek Compose harus punya persis satu kelas yang menjadi
 * Activity peluncur, bernama "<packagename>.MainActivity", turunan
 * androidx.activity.ComponentActivity (lihat TemplateDasar.templateComposeMain).
 */
public class BangunCompose {

    public interface Listener {
        void onEvent(int type, String msg);
    }

    public static final int PROGRESS = 0, SUCCESS = 1, FAILED = 2;

    /** Versi plugin compiler Compose (cocok dengan Kotlin 1.9.24). */
    public static final String COMPOSE_COMPILER_VERSION = "1.5.14";
    /** Versi Kotlin yang dipakai (sama seperti BangunKotlin). */
    public static final String KOTLIN_VERSION = "1.9.24";
    /** Versi runtime Compose (BOM 2023.06.01): ui/foundation/runtime 1.4.3, material3 1.1.1. */
    public static final String COMPOSE_BOM_VERSION = "2023.06.01";
    /** SDK minimum proyek Compose di perangkat (sama seperti Kotlin). */
    public static final int MIN_SDK = 21;

    private final Context ctx;
    private final File projectDir;
    /** Berkas .java tambahan untuk ECJ (R.java hasil aapt2, lihat BangunLokal);
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

    private BangunCompose(Context ctx, File projectDir, Listener l, List<File> sumberTambahan) {
        this.ctx = ctx.getApplicationContext();
        this.projectDir = projectDir;
        this.listener = l;
        this.sumberTambahan = sumberTambahan;
    }

    /** Dipakai BangunLokal (sinkron, di thread sendiri): kompilasi .kt+.java
     *  -> daftar dex (bisa lebih dari satu karena runtime Compose besar). */
    static List<byte[]> kompilasiUntukRakit(Context ctx, File projectDir, List<File> sumberTambahan) throws Exception {
        return new BangunCompose(ctx, projectDir, null, sumberTambahan).jalankan();
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

    private List<byte[]> jalankan() throws Exception {
        if (!new File(ctx.getApplicationInfo().nativeLibraryDir).isDirectory())
            throw new IOException("Direktori native tidak ditemukan");

        kirim(PROGRESS, "Menyiapkan compiler Kotlin + plugin Compose (kotlinc 1.9.24 + Compose 1.5.14)...");
        File cache = new File(ctx.getCacheDir(), "bangun-compose");
        hapus(cache);
        cache.mkdirs();
        File odex = new File(cache, "odex");
        odex.mkdirs();

        File kotlinDexJar;
        try {
            kotlinDexJar = ekstrakAset("alat/kotlin-compiler-dex.jar", new File(cache, "kotlin-compiler-dex.jar"));
        } catch (IOException e) {
            throw new IOException("Compiler Kotlin tidak ditemukan di aplikasi (alat/kotlin-compiler-dex.jar hilang). Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        if (kotlinDexJar.length() < 10L * 1024 * 1024)
            throw new IOException("Compiler Kotlin rusak (alat/kotlin-compiler-dex.jar hanya "
                    + kotlinDexJar.length() + " bita, seharusnya sekitar 56 MB). Coba pasang ulang aplikasi Create Aplikasi.");
        File pluginDexJar;
        try {
            pluginDexJar = ekstrakAset("alat/compose/compose-compiler-dex.jar", new File(cache, "compose-compiler-dex.jar"));
        } catch (IOException e) {
            throw new IOException("Plugin compiler Compose tidak ditemukan di aplikasi (alat/compose/compose-compiler-dex.jar hilang). Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        if (pluginDexJar.length() < 100L * 1024)
            throw new IOException("Plugin compiler Compose rusak (alat/compose/compose-compiler-dex.jar hanya "
                    + pluginDexJar.length() + " bita, seharusnya sekitar 400 KB). Coba pasang ulang aplikasi Create Aplikasi.");
        File runtimeJar;
        try {
            runtimeJar = ekstrakAset("alat/compose/compose-runtime.jar", new File(cache, "compose-runtime.jar"));
        } catch (IOException e) {
            throw new IOException("Runtime Compose tidak ditemukan di aplikasi (alat/compose/compose-runtime.jar hilang). Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        if (runtimeJar.length() < 10L * 1024 * 1024)
            throw new IOException("Runtime Compose rusak (alat/compose/compose-runtime.jar hanya "
                    + runtimeJar.length() + " bita, seharusnya sekitar 16 MB). Coba pasang ulang aplikasi Create Aplikasi.");
        File stdlibJar = ekstrakAset("alat/kotlin-stdlib-1.9.24.jar", new File(cache, "kotlin-stdlib-1.9.24.jar"));
        File scriptJar = ekstrakAset("alat/kotlin-script-runtime-1.9.24.jar", new File(cache, "kotlin-script-runtime-1.9.24.jar"));
        File ecjJar = ekstrakAset("alat/ecj-dex.jar", new File(cache, "ecj-dex.jar"));
        File stubJar = ekstrakAset("alat/javax-stub-dex.jar", new File(cache, "javax-stub-dex.jar"));
        File r8Jar = ekstrakAset("alat/r8-dex.jar", new File(cache, "r8-dex.jar"));
        File androidJar = ekstrakAset("alat/android-full.jar", new File(cache, "android-full.jar"));

        ClassLoader induk = BangunCompose.class.getClassLoader();
        Object kotlinCl;
        try {
            // DexClassLoader compiler memuat kotlin-compiler-dex.jar DAN
            // compose-compiler-dex.jar (dipisah File.pathSeparator). Kelas
            // plugin (ComposePluginRegistrar dkk.) harus ada di ClassLoader
            // ini, karena ServiceLoaderLite memuatnya lewat delegasi ke
            // induk (parent) dari URLClassLoader -Xplugin. Bila plugin
            // tidak ada di sini, kotlinc jalan tanpa plugin Compose.
            kotlinCl = buatDexClassLoader(
                    kotlinDexJar.getPath() + File.pathSeparator + pluginDexJar.getPath(), odex, induk);
        } catch (Exception e) {
            throw new IOException("Compiler Kotlin / plugin Compose rusak atau tidak bisa dimuat. Coba pasang ulang aplikasi Create Aplikasi.", e);
        }
        Object eclCl = buatDexClassLoader(ecjJar.getPath() + File.pathSeparator + stubJar.getPath(), odex, induk);
        Object r8Cl = buatDexClassLoader(r8Jar, odex, induk);

        // kotlin-home tiruan: compiler mencari kotlin-stdlib.jar di <kotlin-home>/lib.
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
        ringkasan.append("Berkas .kt proyek yang dikompilasi kotlinc + plugin Compose 1.5.14 (").append(ktFiles.size()).append("):\n");
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

        // 2) ECJ untuk .java (R.java aplikasi + R.java pustaka Compose) -> javaOut.
        // kotlinc butuh hasil ini di classpath-nya (R + kelas Java pengguna).
        if (!javaFiles.isEmpty()) {
            kirim(PROGRESS, "Mengompilasi " + javaFiles.size() + " berkas .java (ECJ)...");
            kompilasiECJ(eclCl, javaFiles, androidJar, javaOut);
        }

        // 3) kotlinc + plugin Compose untuk .kt -> kotlinOut
        kirim(PROGRESS, "Mengompilasi " + ktFiles.size() + " berkas .kt (kotlinc + plugin Compose)...");
        kompilasiKotlin(kotlinCl, ktFiles, androidJar, stdlibJar, runtimeJar, pluginDexJar, javaOut, kotlinHome, kotlinOut);

        // 4) dex hasil kompilasi (ECJ + kotlinc + runtime Compose + stdlib) dengan D8.
        // Runtime Compose besar (16 MB jar) sehingga D8 memecah ke beberapa
        // classes.dex — kembalikan SEMUANYA (bukan hanya classes.dex pertama).
        kirim(PROGRESS, "Mengemas bytecode Compose (D8)...");
        List<File> classFiles = new ArrayList<File>();
        kumpulBerakhiran(javaOut, ".class", classFiles);
        kumpulBerakhiran(kotlinOut, ".class", classFiles);
        if (classFiles.isEmpty()) throw new IOException("kotlinc/ECJ tidak menghasilkan berkas .class");

        // Proyek Compose WAJIB punya kelas MainActivity (peluncur di manifest).
        // Tanpa pemeriksaan ini, build "berhasil" tetapi APK hasil langsung
        // crash saat dibuka karena Activity peluncur tidak ada.
        boolean adaMainActivity = false;
        for (File f : classFiles) {
            if (f.getName().equals("MainActivity.class")) { adaMainActivity = true; break; }
        }
        if (!adaMainActivity)
            throw new IOException("Proyek Compose tidak punya MainActivity: buat MainActivity.kt "
                    + "berisi kelas MainActivity turunan androidx.activity.ComponentActivity "
                    + "di paket proyek, lalu bangun lagi.");

        List<Path> programPaths = new ArrayList<Path>();
        for (File f : classFiles) programPaths.add(f.toPath());
        programPaths.add(runtimeJar.toPath());
        programPaths.add(stdlibJar.toPath());

        File dexOut = new File(cache, "dexout");
        dexOut.mkdirs();
        jalankanD8(r8Cl, programPaths, androidJar.toPath(), dexOut);

        File[] dexFiles = dexOut.listFiles();
        List<byte[]> hasil = new ArrayList<byte[]>();
        if (dexFiles != null) {
            java.util.Arrays.sort(dexFiles);
            for (File f : dexFiles) {
                if (f.getName().endsWith(".dex")) hasil.add(BangunLokal.baca(f));
            }
        }
        if (hasil.isEmpty()) throw new IOException("D8 tidak menghasilkan classes.dex");
        long total = 0;
        for (byte[] d : hasil) total += d.length;
        kirim(PROGRESS, "Kompilasi Compose selesai (" + hasil.size() + " dex, " + total + " bita).");
        return hasil;
    }

    // ------------------------------------------------------------------ ECJ (.java)

    /** Kompilasi .java dengan ECJ (pola yang sama seperti BangunKotlin). */
    private void kompilasiECJ(Object eclCl, List<File> javaFiles, File androidJar, File outDir) throws Exception {
        List<String> args = new ArrayList<String>();
        args.add("-nowarn");
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
            throw new IOException("Kompilasi Java (untuk Compose) gagal:\n" + errSw + (outSw.getBuffer().length() > 0 ? "\n" + outSw : ""));
    }

    // ------------------------------------------------------------------ kotlinc + plugin Compose (.kt)

    /**
     * Kompilasi .kt dengan kotlinc 1.9.24 + plugin compiler Compose 1.5.14
     * lewat refleksi. Dipanggil CLICompiler.exec (bukan main) supaya tidak
     * System.exit. Pesan compiler ditangkap dari System.err selama exec,
     * lalu dikembalikan.
     *
     * Plugin diaktifkan dengan -Xplugin=<jalur compose-compiler-dex.jar>.
     * DexClassLoader compiler (kotlinCl) sudah memuat jar plugin itu juga,
     * supaya ServiceLoaderLite bisa memuat kelas plugin lewat delegasi
     * ke induk. ServiceLoader di dalam compiler memakai thread context
     * ClassLoader, jadi sebelum exec, context ClassLoader thread ini
     * diganti ke DexClassLoader compiler, lalu dikembalikan.
     */
    private void kompilasiKotlin(Object kotlinCl, List<File> ktFiles, File androidJar,
            File stdlibJar, File runtimeJar, File pluginDexJar, File javaOut,
            File kotlinHome, File outDir) throws Exception {
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
        // Plugin compiler Compose: jar dex yang sama dengan yang dimuat
        // DexClassLoader compiler (lihat jalankan()). ServiceLoaderLite
        // membaca META-INF/services dari jar ini, lalu memuat kelas
        // plugin lewat delegasi ke DexClassLoader compiler.
        args.add("-Xplugin=" + pluginDexJar.getPath());
        args.add("-cp");
        args.add(androidJar.getPath() + File.pathSeparator + stdlibJar.getPath()
                + File.pathSeparator + runtimeJar.getPath() + File.pathSeparator + javaOut.getPath());
        args.add("-d");
        args.add(outDir.getPath());
        args.add("-jvm-target");
        args.add("1.8");
        args.add("-no-reflect");
        for (File f : ktFiles) args.add(f.getPath());

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
            throw new IOException("Kompilasi Compose gagal (" + exitCode + "):\n" + rincian);
        }
    }

    // ------------------------------------------------------------------ D8 (dex)

    /** Dex dengan D8 (pola yang sama seperti BangunKotlin / gabungDex). */
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

    // ------------------------------------------------------------------ util (pola yang sama seperti BangunKotlin)

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
