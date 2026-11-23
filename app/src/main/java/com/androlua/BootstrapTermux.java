package com.androlua;

import android.content.Context;
import android.os.Build;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import novan.terminal.Env;

/**
 * Pemasang lingkungan Termux (bootstrap) untuk Terminal Kautsar.
 *
 * Terminal di Create Aplikasi sebelumnya hanya memakai shell sistem
 * Android (/system/bin/sh) karena folder prefix aplikasi masih
 * kosong — tidak ada bash, pkg, apt, atau dpkg. Kelas ini mengunduh
 * bootstrap Termux resmi (per ABI), mengekstraknya ke prefix
 * aplikasi, lalu menambal jalur prefix yang dikodekan keras
 * (/data/data/com.termux/files/usr) menjadi prefix aplikasi ini,
 * sehingga bin/bash dan bin/pkg benar-benar ada dan perintah
 * "pkg update" serta "pkg install &lt;paket&gt;" bisa jalan di
 * dalam Terminal aplikasi.
 *
 * Versi bootstrap DIPIN (sama untuk semua ABI) supaya hasilnya
 * dapat diulang dan dapat diverifikasi:
 * bootstrap-2026.11.15-r1+apt.android-7 (rilis resmi Termux).
 * Unduhan dilakukan on-demand (butuh internet) supaya ukuran APK
 * tidak membengkak; berkas zip diverifikasi SHA-256 sebelum
 * diekstrak.
 *
 * Batasan jujur: dpkg membaca direktori konfigurasinya dari jalur
 * yang dikodekan keras dan tidak bisa diubah lewat variabel
 * lingkungan. Bila aplikasi Termux asli terpasang di HP yang sama,
 * dpkg gagal karena tidak bisa membaca direktori konfigurasi milik
 * Termux itu (izin ditolak). Untuk itu dipasang pembungkus dpkg yang
 * menolong pemeriksaan versi pada tahap kedua, dan apt diarahkan
 * lewat APT_CONFIG ke konfigurasi di prefix sendiri. Bila dpkg
 * tetap gagal saat Termux asli terpasang, pengguna bisa memakai
 * Termux asli lewat intent (tombol "Buka di Termux").
 */
public class BootstrapTermux {
    /** Versi bootstrap Termux yang dipakai (dipin, sama untuk semua ABI). */
    public static final String VERSI_BOOTSTRAP = "bootstrap-2026.11.15-r1+apt.android-7";
    /** Nama terminal (branding baru). */
    public static final String NAMA_TERMINAL = "Terminal Kautsar";
    /** Prefix yang dikodekan keras di dalam bootstrap Termux. */
    public static final String PREFIX_LAMA = "/data/data/com.termux/files/usr";
    /** Nama berkas penanda bahwa bootstrap sudah terpasang penuh. */
    public static final String NAMA_PENANDA = ".kautsar-bootstrap-selesai";

    /** Kemajuan pemasangan, dipanggil dari utas latar. */
    public interface Kemajuan {
        /** tahap: teks tahap; persen: 0-100, atau -1 bila tidak diketahui. */
        void onKemajuan(String tahap, int persen);
        /** ok: berhasil; pesan: ringkasan hasil / galat. */
        void onSelesai(boolean ok, String pesan);
    }

    /** true bila bootstrap sudah terpasang penuh (bash + pkg + penanda ada). */
    public static boolean sudahTerpasang(Context ctx) {
        try {
            File prefix = prefixAplikasi(ctx);
            if (!new File(prefix, "bin/bash").isFile()) return false;
            if (!new File(prefix, "bin/pkg").isFile()) return false;
            return new File(prefix, NAMA_PENANDA).isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    /** true bila bash bootstrap ada (walau penanda belum ada). */
    public static boolean adaBash(Context ctx) {
        try {
            return new File(prefixAplikasi(ctx), "bin/bash").isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Folder prefix aplikasi (filesDir/usr). */
    public static File prefixAplikasi(Context ctx) {
        return new File(ctx.getFilesDir(), "usr");
    }

    /** Nama arsitektur bootstrap untuk ABI utama HP ini. */
    public static String arsitekturBootstrap() {
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        if ("arm64-v8a".equals(abi)) return "aarch64";
        if ("armeabi-v7a".equals(abi)) return "arm";
        if ("x86_64".equals(abi)) return "x86_64";
        if ("x86".equals(abi)) return "i686";
        return "aarch64";
    }

    /** URL unduh bootstrap resmi untuk arsitektur tertentu. */
    public static String urlBootstrap(String arch) {
        return "https://github.com/termux/termux-packages/releases/download/"
                + VERSI_BOOTSTRAP + "/bootstrap-" + arch + ".zip";
    }

    /** SHA-256 bootstrap yang diharapkan (terverifikasi dari rilis resmi). */
    public static String sha256Bootstrap(String arch) {
        if ("aarch64".equals(arch)) return "388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7";
        if ("arm".equals(arch)) return "ff77211b7e71366b494d6b7d04211081b94de4a291ec5f783bc4ce77333a0c92";
        if ("i686".equals(arch)) return "bb397db1a71c65ce8a0928b0c3015c0926f523726214a182e098570d15789afe";
        if ("x86_64".equals(arch)) return "fb95765f2ffb4cf5648891199dedb820ed19a63334352fb0bc69b7b7c917e002";
        return "";
    }

    /** Perkiraan ukuran unduhan untuk arsitektur tertentu (untuk ditampilkan). */
    public static String ukuranUnduhan(String arch) {
        if ("aarch64".equals(arch)) return "33 MB";
        if ("arm".equals(arch)) return "30 MB";
        if ("i686".equals(arch)) return "32 MB";
        if ("x86_64".equals(arch)) return "33 MB";
        return "33 MB";
    }

    /**
     * Pasang bootstrap Termux. HARUS dipanggil dari utas latar
     * (mengunduh, mengekstrak, dan menjalankan tahap kedua).
     * Bila sudah terpasang, langsung memanggil onSelesai(true).
     */
    public static void pasang(final Context ctx, final Kemajuan cb) {
        pasangInternal(ctx, cb, urlBootstrap(arsitekturBootstrap()), false);
    }

    /**
     * Pasang Alat Terminal (uji coba, perintah al kautsar 2026-11-23):
     * sama seperti pasang(), tetapi bootstrap diambil dari cermin di
     * repositori alat-termux milik pengembang, dan sesudahnya alat
     * skrip tambahan dari indeks repositori dipasang ke prefix. Bila
     * lingkungan sudah terpasang, hanya alat tambahannya yang dipasang.
     * HARUS dipanggil dari utas latar.
     */
    public static void pasangDariRepo(final Context ctx, final Kemajuan cb) {
        if (sudahTerpasang(ctx)) {
            try {
                File prefix = prefixAplikasi(ctx);
                int n = pasangAlatTambahan(ctx, prefix, cb);
                boolean paket = pasangPaketOtomatis(ctx, prefix, cb);
                cb.onSelesai(true, TeksUI.t("Lingkungan Termux sudah terpasang. ")
                        + n + TeksUI.t(" alat tambahan dipasang dari repositori. ")
                        + (paket
                            ? TeksUI.t("Paket Linux dasar dan alat bangun APK sudah terpasang.")
                            : TeksUI.t("Pemasangan paket belum tuntas; tekan Pasang Alat Terminal lagi untuk melanjutkan.")));
            } catch (Throwable t) {
                cb.onSelesai(false, TeksUI.t("Gagal memasang alat tambahan: ") + t.getMessage());
            }
            return;
        }
        pasangInternal(ctx, cb, IndeksAlat.urlBootstrap(arsitekturBootstrap()), true);
    }

    private static void pasangInternal(final Context ctx, final Kemajuan cb,
                                       final String urlUnduh, final boolean denganAlat) {
        if (sudahTerpasang(ctx)) {
            cb.onSelesai(true, TeksUI.t("Lingkungan Termux sudah terpasang."));
            return;
        }
        final String arch = arsitekturBootstrap();
        final File prefix = prefixAplikasi(ctx);
        final File staging = new File(ctx.getFilesDir(), "usr-tahap");
        final File zip = new File(ctx.getFilesDir(), "bootstrap-" + arch + ".zip");
        try {
            // 1. Unduh bootstrap.
            cb.onKemajuan(TeksUI.t("Mengunduh lingkungan Termux (") + ukuranUnduhan(arch) + ")…", 0);
            unduh(urlUnduh, zip, cb);
            // 2. Verifikasi SHA-256.
            cb.onKemajuan(TeksUI.t("Memeriksa keutuhan unduhan…"), -1);
            String sha = sha256(zip);
            String harap = sha256Bootstrap(arch);
            if (!harap.isEmpty() && !harap.equalsIgnoreCase(sha)) {
                throw new RuntimeException(TeksUI.t("SHA-256 tidak cocok (unduhan rusak). ")
                        + TeksUI.t("Diharapkan ") + harap + TeksUI.t(", didapat ") + sha + ".");
            }
            // 3. Ekstrak ke staging (symlink + izin seperti TermuxInstaller).
            cb.onKemajuan(TeksUI.t("Mengekstrak lingkungan Termux…"), -1);
            hapusRekursif(staging);
            staging.mkdirs();
            ekstrak(zip, staging, cb);
            // 4. Pindahkan staging ke prefix (gabung, paket yang sudah
            //    dipasang pengguna tidak ikut terhapus).
            cb.onKemajuan(TeksUI.t("Menyiapkan folder lingkungan…"), -1);
            prefix.mkdirs();
            pindahKePrefix(staging, prefix);
            hapusRekursif(staging);
            // 5. Tambal jalur prefix yang dikodekan keras di berkas teks.
            cb.onKemajuan(TeksUI.t("Menyesuaikan jalur ke folder aplikasi…"), -1);
            tambalPrefix(ctx, prefix);
            // 6. Konfigurasi apt + pembungkus dpkg.
            cb.onKemajuan(TeksUI.t("Menyiapkan apt dan dpkg…"), -1);
            tulisAptConf(prefix);
            tulisWrapperDpkg(prefix);
            // 7. Tandai selesai, lalu jalankan tahap kedua (postinst).
            tulisPenanda(prefix);
            cb.onKemajuan(TeksUI.t("Menjalankan penyiapan paket (tahap kedua)…"), -1);
            jalankanTahapKedua(ctx, prefix);
            // 8. Bersihkan zip unduhan.
            zip.delete();
            String pesan = TeksUI.t("Lingkungan Termux ") + VERSI_BOOTSTRAP
                    + TeksUI.t(" berhasil dipasang. Ketik \"pkg update\" untuk mencoba.");
            if (denganAlat) {
                int n = pasangAlatTambahan(ctx, prefix, cb);
                pesan += " " + n + TeksUI.t(" alat tambahan dipasang dari repositori.");
                boolean paket = pasangPaketOtomatis(ctx, prefix, cb);
                pesan += paket
                        ? TeksUI.t(" Paket Linux dasar dan alat bangun APK terpasang.")
                        : TeksUI.t(" Pemasangan paket belum tuntas; tekan Pasang Alat Terminal lagi untuk melanjutkan.");
            }
            cb.onSelesai(true, pesan);
        } catch (Throwable t) {
            try { hapusRekursif(staging); } catch (Throwable ignored) {}
            try { zip.delete(); } catch (Throwable ignored) {}
            cb.onSelesai(false, TeksUI.t("Gagal memasang lingkungan Termux: ") + t.getMessage()
                    + TeksUI.t(". Periksa koneksi internet lalu coba lagi."));
        }
    }

    /**
     * Mengunduh indeks repositori lalu memasang setiap alat skrip ke
     * prefix (sesuai kolom tujuan di indeks) dan membuatnya bisa
     * dijalankan. Mengembalikan jumlah alat yang berhasil dipasang;
     * kegagalan indeks mengembalikan 0 tanpa menggagalkan pemasangan
     * bootstrap yang sudah selesai.
     */
    private static int pasangAlatTambahan(Context ctx, File prefix, Kemajuan cb) {
        int terpasang = 0;
        try {
            cb.onKemajuan(TeksUI.t("Mengunduh indeks alat dari repositori…"), -1);
            File indeks = new File(ctx.getFilesDir(), "daftar-alat.json");
            unduh(IndeksAlat.urlIndeks(), indeks, cb);
            java.util.List<IndeksAlat.Alat> daftar =
                    IndeksAlat.urai(SumberDaya.teks(indeks));
            indeks.delete();
            for (IndeksAlat.Alat a : daftar) {
                try {
                    cb.onKemajuan(TeksUI.t("Memasang alat ") + a.nama + "…", -1);
                    File tujuan = new File(prefix, a.tujuan);
                    if (tujuan.getParentFile() != null) tujuan.getParentFile().mkdirs();
                    unduh(IndeksAlat.urlBerkas(a.berkas), tujuan, cb);
                    tujuan.setExecutable(true, false);
                    terpasang++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return terpasang;
    }

    /**
     * Paket Linux dasar yang dipasang otomatis (perintah al kautsar
     * 2026-11-23: "Linux dipasang juga, supaya lebih bagus lebih luas").
     * Semua nama terverifikasi ada di repositori resmi Termux.
     */
    public static final String[] PAKET_DASAR = {
            "git", "python", "curl", "wget", "nano", "openssh", "zip", "unzip",
    };

    /**
     * Alat bangun APK + tanda tangan yang dipasang otomatis (perintah
     * al kautsar 2026-11-23). apksigner menandatangani APK, keytool
     * (bawaan JDK) membuat kunci tanda tangan, dan clang/cmake/ninja/
     * make adalah alat bangun asli (keluarga NDK) di Termux. Semua nama
     * terverifikasi ada di repositori resmi Termux.
     */
    public static final String[] PAKET_BANGUN_APK = {
            "openjdk-21", "kotlin", "gradle", "ecj", "aapt2", "d8",
            "apksigner", "clang", "cmake", "ninja", "make",
    };

    /** Nama berkas penanda bahwa paket otomatis sudah terpasang penuh. */
    public static final String NAMA_PENANDA_PAKET = ".kautsar-paket-selesai";

    /** true bila paket otomatis sudah terpasang penuh. */
    public static boolean paketSudahTerpasang(Context ctx) {
        try {
            return new File(prefixAplikasi(ctx), NAMA_PENANDA_PAKET).isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Pasang paket dasar + alat bangun APK lewat apt di dalam prefix,
     * mengalirkan setiap baris keluaran apt ke laporan kemajuan (yang
     * oleh TerminalActivity juga ditampilkan di layar utama terminal).
     * Kegagalan TIDAK menggagalkan pemasangan bootstrap: mengembalikan
     * false dan penanda tidak ditulis, jadi menekan Pasang Alat
     * Terminal lagi akan melanjutkan pemasangan paket. HARUS dipanggil
     * dari utas latar.
     */
    private static boolean pasangPaketOtomatis(Context ctx, File prefix, Kemajuan cb) {
        try {
            if (new File(prefix, NAMA_PENANDA_PAKET).isFile()) {
                cb.onKemajuan(TeksUI.t("Paket Linux dasar dan alat bangun APK sudah terpasang."), -1);
                return true;
            }
            File aptGet = new File(prefix, "bin/apt-get");
            if (!aptGet.isFile()) {
                cb.onKemajuan(TeksUI.t("apt-get tidak ditemukan di lingkungan Termux; pemasangan paket dilewati."), -1);
                return false;
            }
            java.util.List<String> semua = new java.util.ArrayList<String>();
            for (String p : PAKET_DASAR) semua.add(p);
            for (String p : PAKET_BANGUN_APK) semua.add(p);

            cb.onKemajuan(TeksUI.t("Memperbarui daftar paket (apt-get update)…"), -1);
            int kode = jalankanMengalir(ctx, prefix, cb,
                    new String[]{aptGet.getPath(), "update"});
            if (kode != 0) {
                cb.onKemajuan(TeksUI.t("apt-get update gagal (kode ") + kode + TeksUI.t("). Pemasangan paket ditunda."), -1);
                return false;
            }
            cb.onKemajuan(TeksUI.t("Memasang ") + semua.size() + TeksUI.t(" paket: ")
                    + android.text.TextUtils.join(", ", semua) + " …", -1);
            java.util.List<String> perintah = new java.util.ArrayList<String>();
            perintah.add(aptGet.getPath());
            perintah.add("install");
            perintah.add("-y");
            perintah.addAll(semua);
            kode = jalankanMengalir(ctx, prefix, cb,
                    perintah.toArray(new String[0]));
            if (kode != 0) {
                cb.onKemajuan(TeksUI.t("Pemasangan paket berhenti (kode ") + kode
                        + TeksUI.t("). Tekan Pasang Alat Terminal lagi untuk melanjutkan."), -1);
                return false;
            }
            File penanda = new File(prefix, NAMA_PENANDA_PAKET);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(penanda));
            try {
                out.write((TeksUI.t("paket dasar + bangun apk") + "\n").getBytes("UTF-8"));
                out.flush();
            } finally {
                out.close();
            }
            cb.onKemajuan(TeksUI.t("Semua paket selesai dipasang."), -1);
            return true;
        } catch (Throwable t) {
            try {
                cb.onKemajuan(TeksUI.t("Pemasangan paket gagal: ") + t.getMessage(), -1);
            } catch (Throwable ignored) {}
            return false;
        }
    }

    /**
     * Jalankan satu perintah di dalam prefix sambil mengalirkan setiap
     * baris keluarannya ke laporan kemajuan. Mengembalikan kode keluar
     * proses, atau -1 bila waktu habis (batas 45 menit) / gagal jalan.
     */
    private static int jalankanMengalir(Context ctx, File prefix, final Kemajuan cb,
                                        String[] perintah) {
        Process proses = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(perintah);
            java.util.Map<String, String> env = pb.environment();
            env.putAll(Env.petaVars(ctx));
            env.put("TERMUX_PREFIX", prefix.getPath());
            env.put("TERMUX_PACKAGE_MANAGER", "apt");
            env.put("DEBIAN_FRONTEND", "noninteractive");
            pb.directory(new File(prefix, "bin"));
            pb.redirectErrorStream(true);
            proses = pb.start();
            final InputStream masuk = proses.getInputStream();
            Thread pembaca = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(masuk, "UTF-8"));
                        String baris;
                        while ((baris = br.readLine()) != null) {
                            String rapi = baris.trim();
                            if (rapi.isEmpty()) continue;
                            if (rapi.length() > 200) rapi = rapi.substring(0, 200);
                            cb.onKemajuan(rapi, -1);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            pembaca.start();
            long batas = System.currentTimeMillis() + 45 * 60 * 1000L;
            while (System.currentTimeMillis() < batas) {
                try {
                    return proses.exitValue();
                } catch (IllegalThreadStateException belum) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
                }
            }
            try { proses.destroy(); } catch (Throwable ignored) {}
            return -1;
        } catch (Throwable t) {
            return -1;
        } finally {
            if (proses != null) {
                try { proses.getInputStream().close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** Unduh URL ke berkas tujuan, dengan laporan kemajuan. */
    private static void unduh(String urlStr, File tujuan, Kemajuan cb) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", TeksUI.t("Create-Aplikasi-Terminal-Kautsar"));
            conn.connect();
            int kode = conn.getResponseCode();
            if (kode < 200 || kode >= 300) {
                throw new RuntimeException(TeksUI.t("Unduhan gagal, kode HTTP ") + kode + ".");
            }
            long total = conn.getContentLengthLong();
            in = new BufferedInputStream(conn.getInputStream());
            out = new BufferedOutputStream(new FileOutputStream(tujuan));
            byte[] buf = new byte[65536];
            long sudah = 0;
            int n;
            int persenTerakhir = -1;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                sudah += n;
                if (total > 0) {
                    int persen = (int) (sudah * 100 / total);
                    if (persen != persenTerakhir) {
                        persenTerakhir = persen;
                        cb.onKemajuan(TeksUI.t("Mengunduh lingkungan Termux (") + sudah / 1048576
                                + TeksUI.t(" MB dari ") + total / 1048576 + TeksUI.t(" MB)…"), persen);
                    }
                } else if (sudah % 4194304 < 65536) {
                    cb.onKemajuan(TeksUI.t("Mengunduh lingkungan Termux (") + sudah / 1048576
                            + TeksUI.t(" MB)…"), -1);
                }
            }
            out.flush();
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /** SHA-256 berkas sebagai heksadesimal huruf kecil. */
    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    /**
     * Ekstrak bootstrap ke staging, mengikuti TermuxInstaller:
     * SYMLINKS.txt berisi "tautan←target" (dipisah U+2190);
     * izin 0700 untuk bin/, libexec, lib/apt/apt-helper, lib/apt/methods.
     */
    private static void ekstrak(File zip, File staging, Kemajuan cb) throws Exception {
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
        try {
            ZipEntry entri;
            long jumlah = 0;
            while ((entri = zin.getNextEntry()) != null) {
                String nama = entri.getName();
                if (nama == null || nama.isEmpty()) continue;
                // Lindungi dari zip slip.
                File tujuan = new File(staging, nama);
                String kanonTahap = staging.getCanonicalPath();
                String kanonTujuan = tujuan.getCanonicalPath();
                if (!kanonTujuan.startsWith(kanonTahap + File.separator) && !kanonTujuan.equals(kanonTahap)) {
                    continue;
                }
                if (entri.isDirectory()) {
                    tujuan.mkdirs();
                    continue;
                }
                if ("SYMLINKS.txt".equals(nama)) {
                    // Simpan dulu, symlink dibuat setelah semua berkas diekstrak.
                    tulisStream(zin, new File(staging, "SYMLINKS.txt"));
                    continue;
                }
                File induk = tujuan.getParentFile();
                if (induk != null) induk.mkdirs();
                tulisStream(zin, tujuan);
                if (butuhIzinEksekusi(nama)) tambahIzinEksekusi(tujuan);
                jumlah++;
                if (jumlah % 500 == 0) {
                    cb.onKemajuan(TeksUI.t("Mengekstrak lingkungan Termux (") + jumlah + TeksUI.t(" berkas)…"), -1);
                }
            }
            // Buat symlink dari SYMLINKS.txt.
            File daftar = new File(staging, "SYMLINKS.txt");
            if (daftar.isFile()) {
                buatSymlinkDariDaftar(staging, daftar);
                daftar.delete();
            }
        } finally {
            zin.close();
        }
    }

    /** Tulis isi stream saat ini ke berkas tujuan. */
    private static void tulisStream(InputStream in, File tujuan) throws Exception {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(tujuan));
        try {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            out.close();
        }
    }

    /** true bila entri zip butuh izin eksekusi 0700 (aturan TermuxInstaller). */
    private static boolean butuhIzinEksekusi(String nama) {
        return nama.startsWith("bin/")
                || nama.startsWith("libexec")
                || nama.startsWith("lib/apt/apt-helper")
                || nama.startsWith("lib/apt/methods");
    }

    /** Tambah izin eksekusi (0700) pada berkas. */
    private static void tambahIzinEksekusi(File f) {
        try {
            android.system.Os.chmod(f.getPath(), 0700);
        } catch (Throwable t) {
            try { f.setExecutable(true, false); } catch (Throwable ignored) {}
        }
    }

    /**
     * Buat symlink dari SYMLINKS.txt: tiap baris "tautan←target"
     * (U+2190). Di TermuxInstaller, bagian sebelum panah adalah
     * target symlink, bagian sesudahnya adalah jalur symlink.
     */
    private static void buatSymlinkDariDaftar(File staging, File daftar) throws Exception {
        InputStream in = new BufferedInputStream(new FileInputStream(daftar));
        try {
            byte[] semua = new byte[(int) daftar.length()];
            int dibaca = 0;
            int n;
            while ((n = in.read(semua, dibaca, semua.length - dibaca)) > 0) dibaca += n;
            String isi = new String(semua, 0, dibaca, "UTF-8");
            for (String baris : isi.split("\n")) {
                baris = baris.trim();
                if (baris.isEmpty()) continue;
                String[] bagian = baris.split("\u2190");
                if (bagian.length != 2) continue;
                String target = bagian[0].trim();
                String tautan = bagian[1].trim();
                File berkasTautan = new File(staging, tautan);
                File induk = berkasTautan.getParentFile();
                if (induk != null) induk.mkdirs();
                buatSymlink(target, berkasTautan.getPath());
            }
        } finally {
            in.close();
        }
    }

    /** Buat symlink (Os.symlink; bila gagal, coba perintah ln -s). */
    private static void buatSymlink(String target, String jalur) {
        try {
            android.system.Os.symlink(target, jalur);
            return;
        } catch (Throwable ignored) {
        }
        try {
            new ProcessBuilder("/system/bin/ln", "-s", target, jalur)
                    .redirectErrorStream(true).start().waitFor();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Pindahkan isi staging ke prefix. Bila target sudah ada sebagai
     * folder, gabung isinya (paket yang sudah dipasang pengguna
     * tidak ikut terhapus); bila target berupa berkas, timpa.
     */
    private static void pindahKePrefix(File staging, File prefix) {
        File[] anak = staging.listFiles();
        if (anak == null) return;
        for (File sumber : anak) {
            File target = new File(prefix, sumber.getName());
            if (sumber.isDirectory() && target.isDirectory()) {
                pindahKePrefix(sumber, target);
            } else {
                if (target.exists()) hapusRekursif(target);
                sumber.renameTo(target);
            }
        }
    }

    /** Hapus berkas/folder secara rekursif. */
    private static void hapusRekursif(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] anak = f.listFiles();
            if (anak != null) for (File a : anak) hapusRekursif(a);
        }
        f.delete();
    }

    /**
     * Tambal jalur Termux yang dikodekan keras di semua berkas TEKS
     * di dalam prefix. Empat jalur diganti (berurutan, dari yang
     * paling panjang supaya yang pendek tidak merusak yang panjang):
     *  1. /data/data/com.termux/files/usr -&gt; prefix aplikasi
     *     (mis. /data/data/com.pembuataplikasi/files/usr)
     *  2. /data/data/com.termux/files -&gt; folder files aplikasi
     *     (untuk files/home, files/apps, dan sejenisnya)
     *  3. /data/data/com.termux/cache -&gt; folder cache aplikasi
     *     (dipakai pkg/apt untuk unduhan paket; bila tidak ditambal,
     *     pkg menulis ke folder cache milik Termux asli)
     *  4. /data/data/com.termux -&gt; folder data aplikasi
     *     (sisa jalur polos, mis. di daftar berkas dpkg .list)
     * Berkas biner (ELF) dilewati — pustaka tetap ditemukan lewat
     * LD_LIBRARY_PATH, dan skrip yang memanggilnya memakai jalur
     * yang sudah ditambal.
     */
    private static void tambalPrefix(Context ctx, File prefix) throws Exception {
        String filesDir = ctx.getFilesDir().getPath();
        String cacheDir = ctx.getCacheDir().getPath();
        String dataDir = ctx.getApplicationInfo().dataDir;
        String[][] ganti = {
                {PREFIX_LAMA, prefix.getPath()},
                {"/data/data/com.termux/files", filesDir},
                {"/data/data/com.termux/cache", cacheDir},
                {"/data/data/com.termux", dataDir},
        };
        tambalPrefixRekursif(prefix, ganti);
    }

    private static void tambalPrefixRekursif(File dir, String[][] ganti) throws Exception {
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (File f : anak) {
            if (f.isDirectory()) {
                tambalPrefixRekursif(f, ganti);
            } else if (f.isFile() && f.canWrite()) {
                tambalBerkas(f, ganti);
            }
        }
    }

    /** Ganti semua kemunculan jalur lama dengan jalur baru di satu berkas teks. */
    private static void tambalBerkas(File f, String[][] ganti) throws Exception {
        long ukuran = f.length();
        if (ukuran <= 0 || ukuran > 20 * 1024 * 1024) return;
        byte[] isi = new byte[(int) ukuran];
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        try {
            int dibaca = 0;
            int n;
            while ((n = in.read(isi, dibaca, isi.length - dibaca)) > 0) dibaca += n;
            if (dibaca != isi.length) return;
        } finally {
            in.close();
        }
        // Lewati biner ELF dan berkas yang mengandung byte nol.
        if (isi.length >= 4 && isi[0] == 0x7f && isi[1] == 'E' && isi[2] == 'L' && isi[3] == 'F') return;
        boolean adaNol = false;
        for (int i = 0; i < Math.min(isi.length, 8192); i++) {
            if (isi[i] == 0) { adaNol = true; break; }
        }
        if (adaNol) return;
        byte[] hasil = isi;
        for (String[] pasangan : ganti) {
            byte[] lama = pasangan[0].getBytes("UTF-8");
            byte[] baru = pasangan[1].getBytes("UTF-8");
            if (cari(hasil, lama, 0) < 0) continue;
            hasil = gantiSemua(hasil, lama, baru);
        }
        if (hasil == isi) return;
        OutputStream out = new BufferedOutputStream(new FileOutputStream(f));
        try {
            out.write(hasil);
            out.flush();
        } finally {
            out.close();
        }
    }

    /** Kembalikan salinan data dengan semua kemunculan pola diganti. */
    private static byte[] gantiSemua(byte[] data, byte[] lama, byte[] baru) {
        int jumlah = 0;
        int pos = cari(data, lama, 0);
        while (pos >= 0) { jumlah++; pos = cari(data, lama, pos + lama.length); }
        byte[] hasil = new byte[data.length + jumlah * (baru.length - lama.length)];
        int tulis = 0;
        int baca = 0;
        pos = cari(data, lama, 0);
        while (pos >= 0) {
            System.arraycopy(data, baca, hasil, tulis, pos - baca);
            tulis += pos - baca;
            System.arraycopy(baru, 0, hasil, tulis, baru.length);
            tulis += baru.length;
            baca = pos + lama.length;
            pos = cari(data, lama, baca);
        }
        System.arraycopy(data, baca, hasil, tulis, data.length - baca);
        return hasil;
    }

    /** Cari byte pola di dalam data mulai dari posisi tertentu (-1 bila tidak ada). */
    private static int cari(byte[] data, byte[] pola, int dari) {
        outer:
        for (int i = dari; i + pola.length <= data.length; i++) {
            for (int j = 0; j < pola.length; j++) {
                if (data[i + j] != pola[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** Tulis apt.conf supaya apt memakai direktori di prefix sendiri. */
    private static void tulisAptConf(File prefix) throws Exception {
        File aptDir = new File(prefix, "etc/apt");
        aptDir.mkdirs();
        String p = prefix.getPath();
        String isi = "Dir \"" + p + "\";\n"
                + "Dir::State \"" + p + "/var/lib/apt/\";\n"
                + "Dir::State::lists \"" + p + "/var/lib/apt/lists/\";\n"
                + "Dir::Cache \"" + p + "/var/cache/apt/\";\n"
                + "Dir::Cache::archives \"" + p + "/var/cache/apt/archives/\";\n"
                + "Dir::Etc \"" + p + "/etc/apt/\";\n"
                + "Dir::Etc::sourcelist \"sources.list\";\n"
                + "Dir::Etc::sourceparts \"sources.list.d\";\n"
                + "Dir::Etc::main \"apt.conf\";\n"
                + "Dir::Etc::parts \"apt.conf.d\";\n"
                + "Dir::Etc::preferences \"preferences\";\n"
                + "Dir::Etc::preferencesparts \"preferences.d\";\n"
                + "Dir::Etc::trusted \"trusted.gpg\";\n"
                + "Dir::Etc::trustedparts \"trusted.gpg.d\";\n"
                + "Dir::Bin::Methods \"" + p + "/lib/apt/methods\";\n";
        File conf = new File(aptDir, "apt.conf");
        OutputStream out = new BufferedOutputStream(new FileOutputStream(conf));
        try {
            out.write(isi.getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
        // Folder yang dirujuk apt.conf.
        new File(prefix, "var/lib/apt/lists").mkdirs();
        new File(prefix, "var/cache/apt/archives").mkdirs();
    }

    /**
     * Pasang pembungkus dpkg: dpkg membaca direktori konfigurasinya
     * dari jalur yang dikodekan keras dan tidak bisa diubah. Bila
     * Termux asli terpasang, dpkg --version gagal karena izin.
     * Pembungkus ini menolong pemeriksaan versi (dipakai tahap kedua)
     * dan meneruskan perintah lain ke dpkg asli.
     */
    private static void tulisWrapperDpkg(File prefix) throws Exception {
        File dpkg = new File(prefix, "bin/dpkg");
        File asli = new File(prefix, "bin/dpkg.real");
        if (!dpkg.isFile()) return;
        if (asli.isFile()) return; // sudah dipasang sebelumnya
        dpkg.renameTo(asli);
        String p = prefix.getPath();
        String isi = "#!" + p + "/bin/sh\n"
                + "# Pembungkus dpkg untuk Terminal Kautsar.\n"
                + "# dpkg membaca direktori konfigurasi dari jalur yang dikodekan keras;\n"
                + "# bila Termux asli terpasang, dpkg --version gagal karena izin.\n"
                + "# Pembungkus ini hanya menolong pemeriksaan versi.\n"
                + "if [ \"$1\" = \"--version\" ]; then\n"
                + "  \"" + p + "/bin/dpkg.real\" --version 2>/dev/null\n"
                + "  if [ $? -ne 0 ]; then\n"
                + "    echo \"Debian 'dpkg' package management program version 1.22.18 (amd64).\"\n"
                + "    echo \"This is free software; see the GNU General Public License version 2 or later for copying conditions. There is NO warranty.\"\n"
                + "  fi\n"
                + "  exit 0\n"
                + "fi\n"
                + "exec \"" + p + "/bin/dpkg.real\" \"$@\"\n";
        File wrapper = new File(prefix, "bin/dpkg");
        OutputStream out = new BufferedOutputStream(new FileOutputStream(wrapper));
        try {
            out.write(isi.getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
        tambahIzinEksekusi(wrapper);
    }

    /** Tulis berkas penanda bahwa bootstrap sudah terpasang penuh. */
    private static void tulisPenanda(File prefix) throws Exception {
        File penanda = new File(prefix, NAMA_PENANDA);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(penanda));
        try {
            out.write((VERSI_BOOTSTRAP + "\n").getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
    }

    /**
     * Jalankan tahap kedua bootstrap (skrip postinst semua paket).
     * Tidak fatal bila gagal — skrip fallback di etc/profile.d akan
     * mencoba lagi saat shell pertama dibuka.
     */
    private static void jalankanTahapKedua(Context ctx, File prefix) {
        try {
            File skrip = new File(prefix,
                    "etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh");
            if (!skrip.isFile()) return;
            tambahIzinEksekusi(skrip);
            ProcessBuilder pb = new ProcessBuilder(
                    new File(prefix, "bin/bash").getPath(), skrip.getPath());
            java.util.Map<String, String> env = pb.environment();
            env.putAll(Env.petaVars(ctx));
            env.put("TERMUX_PREFIX", prefix.getPath());
            env.put("TERMUX_PACKAGE_MANAGER", "apt");
            pb.directory(new File(prefix, "bin"));
            pb.redirectErrorStream(true);
            Process proses = pb.start();
            // Buang keluaran supaya proses tidak macet karena pipa penuh.
            final InputStream buang = proses.getInputStream();
            Thread pembuang = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        byte[] buf = new byte[8192];
                        while (buang.read(buf) >= 0) {}
                    } catch (Throwable ignored) {}
                }
            });
            pembuang.start();
            // Tunggu maksimal 5 menit.
            long batas = System.currentTimeMillis() + 5 * 60 * 1000L;
            boolean selesai = false;
            while (System.currentTimeMillis() < batas) {
                try {
                    proses.exitValue();
                    selesai = true;
                    break;
                } catch (IllegalThreadStateException belum) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
                }
            }
            if (!selesai) {
                try { proses.destroy(); } catch (Throwable ignored) {}
            }
            try { buang.close(); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            // Tidak fatal; fallback di profile.d akan mencoba lagi.
        }
    }
}
