package novan.terminal;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* loaded from: classes.dex */
public class Env {
    public static File home;
    static String nativeDir;
    static File prefix;
    static int target;
    static File tmp;

    public static void init(Context context) {
        File filesDir = context.getFilesDir();
        prefix = new File(filesDir, "usr");
        home = new File(filesDir, "home");
        tmp = new File(prefix, "tmp");
        File[] fileArr = {prefix, new File(prefix, "bin"), new File(prefix, "lib"), new File(prefix, "etc"), new File(prefix, "share"), home, tmp};
        for (int i = 0; i < 7; i++) {
            fileArr[i].mkdirs();
        }
        target = context.getApplicationInfo().targetSdkVersion;
        nativeDir = context.getApplicationInfo().nativeLibraryDir;
    }

    static String path() {
        return prefix.getPath() + "/bin:/system/bin:/system/xbin:/vendor/bin";
    }

    /* Dipulihkan dari smali (Env.smali, metode apply) — jadx hanya memberi stub. */
    static void apply(java.util.Map<java.lang.String, java.lang.String> r4) {
        r4.put("PREFIX", prefix.getPath());
        r4.put("HOME", home.getPath());
        r4.put("TMPDIR", tmp.getPath());
        r4.put("PATH", path());
        r4.put("LD_LIBRARY_PATH", prefix.getPath() + "/lib");
        r4.put("TERM", "xterm-256color");
        r4.put("LANG", "en_US.UTF-8");
        r4.put("ANDROID_DATA", "/data");
        r4.put("ANDROID_ROOT", "/system");
        r4.put("EXTERNAL_STORAGE", "/sdcard");
        java.io.File sdk = new java.io.File(home, "android-sdk");
        if (sdk.isDirectory()) {
            r4.put("ANDROID_HOME", sdk.getPath());
            r4.put("ANDROID_SDK_ROOT", sdk.getPath());
        }
        String preload;
        if (target >= 29 && nativeDir != null) {
            java.io.File kautsarexec = new java.io.File(nativeDir, "libkautsarexec.so");
            preload = kautsarexec.exists() ? kautsarexec.getPath() : "";
        } else {
            preload = "";
        }
        java.io.File preloadLib = new java.io.File(prefix, "lib/libtermux-exec-ld-preload.so");
        if (!preloadLib.exists()) {
            preloadLib = new java.io.File(prefix, "lib/libtermux-exec.so");
        }
        if (preloadLib.exists()) {
            preload = preload.isEmpty() ? preloadLib.getPath() : preload + ":" + preloadLib.getPath();
        }
        if (!preload.isEmpty()) {
            r4.put("LD_PRELOAD", preload);
        }
        // Lingkungan Termux (aktif bila bootstrap sudah dipasang):
        // TERMUX_PREFIX dipakai skrip tahap kedua bootstrap; APT_CONFIG
        // mengarahkan apt ke konfigurasi di prefix sendiri (bukan jalur
        // Termux asli yang dikodekan keras); DPKG_ADMINDIR/DPKG_DATADIR
        // mengarahkan dpkg ke data di prefix sendiri.
        r4.put("TERMUX_PREFIX", prefix.getPath());
        java.io.File aptConf = new java.io.File(prefix, "etc/apt/apt.conf");
        if (aptConf.isFile()) {
            r4.put("APT_CONFIG", aptConf.getPath());
        }
        java.io.File dpkgAdmin = new java.io.File(prefix, "var/lib/dpkg");
        if (dpkgAdmin.isDirectory()) {
            r4.put("DPKG_ADMINDIR", dpkgAdmin.getPath());
        }
        java.io.File dpkgData = new java.io.File(prefix, "share/dpkg");
        if (dpkgData.isDirectory()) {
            r4.put("DPKG_DATADIR", dpkgData.getPath());
        }
    }

    public static String shell() {
        File file = new File(prefix, "bin/bash");
        return file.exists() ? file.getPath() : "/system/bin/sh";
    }

    static String[] wrap(String... strArr) {
        if (target >= 29 && strArr.length != 0) {
            try {
                File file = new File(strArr[0]);
                String canonicalPath = file.getCanonicalPath();
                String str = prefix.getParentFile().getCanonicalPath() + "/";
                if (canonicalPath.startsWith(str) && file.isFile()) {
                    String str2 = Process.is64Bit() ? "/system/bin/linker64" : "/system/bin/linker";
                    byte[] bArr = new byte[256];
                    FileInputStream fileInputStream = new FileInputStream(file);
                    try {
                        int i = fileInputStream.read(bArr);
                        fileInputStream.close();
                        ArrayList arrayList = new ArrayList();
                        arrayList.add(str2);
                        if (i >= 4 && bArr[0] == Byte.MAX_VALUE && bArr[1] == 69 && bArr[2] == 76 && bArr[3] == 70) {
                            arrayList.add(canonicalPath);
                        } else {
                            if (i < 2 || bArr[0] != 35 || bArr[1] != 33) {
                                return strArr;
                            }
                            String str3 = new String(bArr, 2, i - 2, "UTF-8");
                            int iIndexOf = str3.indexOf(10);
                            if (iIndexOf >= 0) {
                                str3 = str3.substring(0, iIndexOf);
                            }
                            String[] strArrSplit = str3.trim().split("\\s+", 2);
                            String path = strArrSplit[0];
                            String[] strArr2 = {"/usr/bin/", "/bin/", "/usr/local/bin/"};
                            for (int i2 = 0; i2 < 3; i2++) {
                                String str4 = strArr2[i2];
                                if (path.startsWith(str4) && !new File(path).exists()) {
                                    File file2 = new File(prefix, "bin/" + path.substring(str4.length()));
                                    if (file2.exists()) {
                                        path = file2.getPath();
                                    }
                                }
                            }
                            File file3 = new File(path);
                            if (!file3.isFile()) {
                                return strArr;
                            }
                            if (file3.getCanonicalPath().startsWith(str)) {
                                arrayList.add(file3.getCanonicalPath());
                            } else {
                                arrayList.clear();
                                arrayList.add(path);
                            }
                            if (strArrSplit.length > 1 && !strArrSplit[1].isEmpty()) {
                                arrayList.add(strArrSplit[1]);
                            }
                            arrayList.add(canonicalPath);
                        }
                        for (int i3 = 1; i3 < strArr.length; i3++) {
                            arrayList.add(strArr[i3]);
                        }
                        return (String[]) arrayList.toArray(new String[0]);
                    } finally {
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return strArr;
    }

    static String[] vars(int i, int i2) {
        HashMap<String, String> map = new HashMap<>();
        apply(map);
        map.put("COLUMNS", String.valueOf(i2));
        map.put("LINES", String.valueOf(i));
        String[] strArr = new String[map.size()];
        int i3 = 0;
        for (Map.Entry entry : map.entrySet()) {
            strArr[i3] = ((String) entry.getKey()) + "=" + ((String) entry.getValue());
            i3++;
        }
        return strArr;
    }

    static String info() {
        return "PREFIX=" + prefix + "\nHOME=" + home + "\nTMPDIR=" + tmp + "\nPATH=" + path() + "\nABI=" + Build.SUPPORTED_ABIS[0] + "\nAndroid API=" + Build.VERSION.SDK_INT + "\ntargetSdk aplikasi=" + target;
    }

    static String selfTest() {
        try {
            File file = new File(prefix, "bin/uji-sh");
            FileInputStream fileInputStream = new FileInputStream("/system/bin/sh");
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                try {
                    byte[] bArr = new byte[8192];
                    while (true) {
                        int i = fileInputStream.read(bArr);
                        if (i <= 0) {
                            break;
                        }
                        fileOutputStream.write(bArr, 0, i);
                    }
                    fileOutputStream.close();
                    fileInputStream.close();
                    file.setExecutable(true, false);
                    ProcessBuilder processBuilder = new ProcessBuilder(wrap(file.getPath(), "-c", "echo eksekusi-ok"));
                    apply(processBuilder.environment());
                    processBuilder.redirectErrorStream(true);
                    java.lang.Process processStart = processBuilder.start();
                    String line = new BufferedReader(new InputStreamReader(processStart.getInputStream())).readLine();
                    processStart.waitFor();
                    file.delete();
                    return "eksekusi-ok".equals(line) ? "Berhasil: biner di folder usr/bin bisa dijalankan. Siap untuk tahap berikutnya." : "Gagal: keluaran tidak sesuai: " + line;
                } finally {
                }
            } finally {
            }
        } catch (Throwable th) {
            return "Gagal: " + th.getMessage() + ". Android menolak eksekusi dari folder aplikasi.";
        }
    }

    // ===== Pembungkus publik untuk Tahap 3 (Terminal exec) =====
    // Metode-metode di atas package-private; TerminalActivity (paket
    // com.androlua) memakai tiga pembungkus ini.

    /** true bila libkautsarexec.so ada di folder pustaka aplikasi ini. */
    public static boolean adaKautsarExec(Context context) {
        try {
            String dir = context.getApplicationInfo().nativeLibraryDir;
            return new File(dir, "libkautsarexec.so").exists();
        } catch (Throwable unused) {
            return false;
        }
    }

    /**
     * Variabel lingkungan shell sebagai Map (untuk ProcessBuilder,
     * mis. saat menjalankan tahap kedua bootstrap). Memanggil
     * init() dulu supaya field statis terisi.
     */
    public static Map<String, String> petaVars(Context context) {
        try {
            init(context);
        } catch (Throwable ignored) {
        }
        HashMap<String, String> map = new HashMap<>();
        try {
            apply(map);
        } catch (Throwable ignored) {
        }
        return map;
    }

    /**
     * Bungkus perintah dengan LD_PRELOAD + linker agar biner di folder
     * aplikasi bisa dieksekusi di Android 10+ (Env.wrap, paket
     * terminal tidak terlihat dari com.androlua). wrap() memakai
     * field statis dari Env.init(), jadi init dipanggil dulu di sini
     * supaya pembungkus ini aman dipakai sendiri.
     */
    public static String[] bungkusPerintah(Context context, String[] perintah) {
        try {
            init(context);
        } catch (Throwable ignored) {
        }
        return wrap(perintah);
    }

    /**
     * Uji jalan tidaknya jalur exec biner (Env.selfTest): mengembalikan
     * "ok" bila biner uji bisa dieksekusi, "NOEXEC" bila exec diblokir
     * sistem, atau pesan galat lainnya. Teks ini ditampilkan apa adanya
     * di layar "Uji Exec" Terminal.
     */
    public static String ujiExec(Context context) {
        try {
            init(context);
        } catch (Throwable ignored) {
        }
        return selfTest();
    }
}
