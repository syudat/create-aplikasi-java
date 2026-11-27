package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autopsi ID Crash dan Mesin Penambal Kode Mandiri (Fitur Gemini 5.7
 * nomor 9) — logika murni tanpa Android, bisa diuji di JVM.
 *
 * Mengurai log crash ber-ID unik (format CrashHandler/IdCrash:
 * "CR-..." di baris pertama, lalu jejak tumpukan), mengenali pola
 * kesalahan umum dari jenis galatnya, menjelaskan penyebabnya dalam
 * bahasa Indonesia yang lugas, mencocokkan kelas+baris ke berkas
 * sumber proyek, dan menyusun potongan kode perbaikan siap pakai.
 * Log rusak tidak pernah membuat crash: uraiLogCrash mengembalikan
 * null bila tidak ada ID maupun jejak galat yang bisa dibaca.
 */
public class AutopsiCrash {

    /** Pola diagnosis. */
    public static final String REFERENSI_KOSONG = "REFERENSI_KOSONG";
    public static final String IZIN_BELUM_DIMINTA = "IZIN_BELUM_DIMINTA";
    public static final String GALAT_JSON = "GALAT_JSON";
    public static final String INDEKS = "INDEKS";
    public static final String KELAS_TIDAK_ADA = "KELAS_TIDAK_ADA";
    public static final String UMUM = "UMUM";

    /** Hasil urai satu log crash. */
    public static class HasilUrai {
        /** ID crash, mis. CR-20261123-141500-1A2B3C; null bila tak terbaca. */
        public String idCrash;
        /** Nama kelas sederhana, mis. MainActivity; null bila tak terbaca. */
        public String kelas;
        /** Nama kelas lengkap dengan paket; null bila tak terbaca. */
        public String kelasLengkap;
        /** Nomor baris di sumber (-1 bila tak terbaca). */
        public int baris = -1;
        /** Jenis galat sederhana, mis. NullPointerException. */
        public String jenisGalat;
        /** Pesan galat sesudah titik dua; "" bila tidak ada. */
        public String pesan = "";
        /** Baris "at ..." pertama yang memuat kelas+baris. */
        public String barisJejak;
    }

    /** Diagnosis satu crash: pola + penjelasan berbahasa Indonesia. */
    public static class Diagnosis {
        public String pola;
        public String penjelasan;
    }

    private static final Pattern POLA_ID =
            Pattern.compile("CR-\\d{8}-\\d{6}-[0-9A-Fa-f]{6}");
    private static final Pattern POLA_GALAT = Pattern.compile(
            "^([A-Za-z_$][\\w.$]*?(?:Exception|Error))\\s*(?::\\s*(.*))?$");
    private static final Pattern POLA_JEJAK = Pattern.compile(
            "^\\s*at\\s+([\\w.$]+)\\(([\\w.$]+\\.java):(\\d+)\\)");

    /**
     * Mengurai teks log crash. Null bila teks null/kosong, atau bila
     * tidak ada ID crash maupun baris galat yang bisa dibaca.
     */
    public static HasilUrai uraiLogCrash(String teksLog) {
        if (teksLog == null || teksLog.trim().length() == 0) return null;
        try {
            HasilUrai h = new HasilUrai();
            Matcher mid = POLA_ID.matcher(teksLog);
            if (mid.find()) h.idCrash = mid.group();
            String[] baris = teksLog.split("\n", -1);
            for (int i = 0; i < baris.length; i++) {
                String b = baris[i].trim();
                if (b.startsWith("Caused by:")) {
                    b = b.substring("Caused by:".length()).trim();
                }
                if (b.startsWith("ID Crash:")) continue;
                if (b.indexOf('=') >= 0 && b.indexOf('=') < 30
                        && !b.contains("Exception") && !b.contains("Error")) {
                    continue;
                }
                Matcher m = POLA_GALAT.matcher(b);
                if (m.matches()) {
                    String penuh = m.group(1);
                    int titik = penuh.lastIndexOf('.');
                    h.jenisGalat = titik >= 0 ? penuh.substring(titik + 1) : penuh;
                    h.pesan = m.group(2) == null ? "" : m.group(2).trim();
                    break;
                }
            }
            for (int i = 0; i < baris.length; i++) {
                Matcher m = POLA_JEJAK.matcher(baris[i]);
                if (m.find()) {
                    String metode = m.group(1);
                    int titik = metode.lastIndexOf('.');
                    h.kelasLengkap = titik > 0 ? metode.substring(0, titik) : metode;
                    int titikKelas = h.kelasLengkap.lastIndexOf('.');
                    h.kelas = titikKelas >= 0
                            ? h.kelasLengkap.substring(titikKelas + 1) : h.kelasLengkap;
                    // Nama berkas harus cocok dengan nama kelas; bila tidak,
                    // pakai nama berkas (lebih bisa dipercaya untuk pencarian).
                    String berkas = m.group(2);
                    String namaBerkas = berkas.substring(0, berkas.length() - 5);
                    int titikBerkas = namaBerkas.lastIndexOf('.');
                    if (titikBerkas >= 0) namaBerkas = namaBerkas.substring(titikBerkas + 1);
                    h.kelas = namaBerkas;
                    try {
                        h.baris = Integer.parseInt(m.group(3));
                    } catch (NumberFormatException ignored) {
                        h.baris = -1;
                    }
                    h.barisJejak = baris[i].trim();
                    break;
                }
            }
            if (h.idCrash == null && h.jenisGalat == null) return null;
            return h;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Mengenali pola kesalahan dari jenis galat (dan, untuk izin,
     * dari baris kode yang bermasalah) beserta penjelasannya.
     */
    public static Diagnosis diagnosis(String jenisGalat, String barisKode) {
        Diagnosis d = new Diagnosis();
        String jenis = jenisGalat == null ? "" : jenisGalat;
        String kode = barisKode == null ? "" : barisKode.toLowerCase();
        if (jenis.contains("NullPointerException")) {
            d.pola = REFERENSI_KOSONG;
            d.penjelasan = TeksUI.t("Nilai kosong dipakai sebelum diperiksa, sehingga aplikasi berhenti paksa (NullPointerException). Periksa dulu nilainya tidak kosong sebelum dipakai.");
        } else if (jenis.contains("SecurityException")
                || kode.contains("permission")) {
            d.pola = IZIN_BELUM_DIMINTA;
            d.penjelasan = TeksUI.t("Izin belum diminta atau ditolak, sehingga Android menolak aksi ini (SecurityException). Minta izinnya dulu dan jalankan aksi ini hanya bila izin sudah diberikan.");
        } else if (jenis.contains("JSONException")) {
            d.pola = GALAT_JSON;
            d.penjelasan = TeksUI.t("Teks JSON tidak bisa diurai karena formatnya salah atau tidak lengkap. Periksa isi JSON-nya dan tangkap galat urai supaya aplikasi tidak berhenti.");
        } else if (jenis.contains("IndexOutOfBoundsException")
                || jenis.contains("ArrayIndexOutOfBoundsException")
                || jenis.contains("StringIndexOutOfBoundsException")) {
            d.pola = INDEKS;
            d.penjelasan = TeksUI.t("Indeks di luar batas daftar atau array. Periksa dulu indeksnya berada di antara 0 dan jumlah isi dikurangi 1 sebelum mengambil isinya.");
        } else if (jenis.contains("ClassNotFoundException")
                || jenis.contains("NoClassDefFoundError")) {
            d.pola = KELAS_TIDAK_ADA;
            d.penjelasan = TeksUI.t("Kelas tidak ditemukan di APK, mungkin pustaka belum ditambahkan atau nama kelas salah. Periksa nama kelas dan pustaka yang dipakai proyek.");
        } else {
            d.pola = UMUM;
            d.penjelasan = TeksUI.t("Penyebab belum dikenali dari jenis galat ini. Periksa jejak tumpukan di log crash dan bungkus baris yang berisiko dengan try-catch supaya galatnya tercatat jelas.");
        }
        return d;
    }

    /** Nama variabel pertama sebelum titik di baris kode; null bila tak ada. */
    static String variabelSebelumTitik(String barisKode) {
        if (barisKode == null) return null;
        Matcher m = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\.")
                .matcher(barisKode);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Baris kode asal, atau komentar pengganti bila tidak ada. */
    static String barisAsal(String barisKode) {
        if (barisKode == null || barisKode.trim().length() == 0) {
            return "// (baris sumber tidak terbaca dari proyek)";
        }
        return barisKode.trim();
    }

    /**
     * Menyusun teks kode perbaikan siap pakai untuk satu pola.
     * Baris kode asal disisipkan apa adanya di dalam tambalan.
     */
    public static String tambalan(String pola, String barisKode) {
        if (pola == null) pola = UMUM;
        String asal = barisAsal(barisKode);
        StringBuilder b = new StringBuilder();
        if (REFERENSI_KOSONG.equals(pola)) {
            String v = variabelSebelumTitik(barisKode);
            if (v == null) v = "nilai";
            b.append("// Tambalan: periksa null sebelum memakai\n");
            b.append("if (").append(v).append(" != null) {\n");
            b.append("    ").append(asal).append('\n');
            b.append("}\n");
        } else if (IZIN_BELUM_DIMINTA.equals(pola)) {
            b.append("// Tambalan: minta izin dulu sebelum memakai fitur ini\n");
            b.append("// Ganti IZIN dengan izin yang dibutuhkan, mis. android.Manifest.permission.CAMERA\n");
            b.append("if (checkSelfPermission(IZIN) == PackageManager.PERMISSION_GRANTED) {\n");
            b.append("    ").append(asal).append('\n');
            b.append("} else {\n");
            b.append("    requestPermissions(new String[]{IZIN}, 1);\n");
            b.append("}\n");
        } else if (GALAT_JSON.equals(pola)) {
            b.append("// Tambalan: tangkap galat urai JSON supaya aplikasi tidak berhenti\n");
            b.append("try {\n");
            b.append("    ").append(asal).append('\n');
            b.append("} catch (org.json.JSONException e) {\n");
            b.append("    // JSON dari server/simpanan tidak sesuai format; pakai nilai bawaan.\n");
            b.append("    e.printStackTrace();\n");
            b.append("}\n");
        } else if (INDEKS.equals(pola)) {
            String daftar = "daftar";
            String indeks = "indeks";
            if (barisKode != null) {
                Matcher mGet = Pattern.compile(
                        "([A-Za-z_$][\\w$]*)\\.get\\(\\s*([A-Za-z_$][\\w$]*|\\d+)\\s*\\)")
                        .matcher(barisKode);
                Matcher mKurung = Pattern.compile(
                        "([A-Za-z_$][\\w$]*)\\[\\s*([A-Za-z_$][\\w$]*|\\d+)\\s*\\]")
                        .matcher(barisKode);
                if (mGet.find()) {
                    daftar = mGet.group(1);
                    indeks = mGet.group(2);
                } else if (mKurung.find()) {
                    daftar = mKurung.group(1);
                    indeks = mKurung.group(2);
                }
            }
            b.append("// Tambalan: periksa batas indeks sebelum mengambil isi\n");
            b.append("if (").append(daftar).append(" != null && ")
                    .append(indeks).append(" >= 0 && ")
                    .append(indeks).append(" < ").append(daftar)
                    .append(".size()) {\n");
            b.append("    ").append(asal).append('\n');
            b.append("}\n");
        } else if (KELAS_TIDAK_ADA.equals(pola)) {
            b.append("// Tambalan: pastikan kelas ada sebelum dipakai\n");
            b.append("try {\n");
            b.append("    Class.forName(\"com.contoh.KelasYangHilang\"); // ganti dengan nama kelas yang dicari\n");
            b.append("    ").append(asal).append('\n');
            b.append("} catch (ClassNotFoundException e) {\n");
            b.append("    // Tambahkan pustaka yang berisi kelas ini ke proyek, atau perbaiki nama kelasnya.\n");
            b.append("}\n");
        } else {
            b.append("// Tambalan umum: bungkus baris yang berisiko supaya galat tercatat jelas\n");
            b.append("try {\n");
            b.append("    ").append(asal).append('\n');
            b.append("} catch (Exception e) {\n");
            b.append("    e.printStackTrace();\n");
            b.append("}\n");
        }
        return b.toString();
    }

    /** Mencari berkas <NamaKelas>.java di bawah folder proyek (rekursif). */
    public static File cariBerkasSumber(File dirProyek, String namaKelas) {
        if (dirProyek == null || !dirProyek.isDirectory()
                || namaKelas == null || namaKelas.length() == 0) {
            return null;
        }
        try {
            File[] isi = dirProyek.listFiles();
            if (isi == null) return null;
            for (int i = 0; i < isi.length; i++) {
                if (isi[i].isFile()
                        && (namaKelas + ".java").equals(isi[i].getName())) {
                    return isi[i];
                }
            }
            for (int i = 0; i < isi.length; i++) {
                if (isi[i].isDirectory()) {
                    File ketemu = cariBerkasSumber(isi[i], namaKelas);
                    if (ketemu != null) return ketemu;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Membaca baris ke-N (mulai 1) dari berkas sumber kelas di proyek.
     * Null bila berkas tidak ada, baris di luar jangkauan, atau gagal baca.
     */
    public static String cariBarisSumber(File dirProyek, String namaKelas, int baris) {
        if (baris < 1) return null;
        File f = cariBerkasSumber(dirProyek, namaKelas);
        if (f == null) return null;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            try {
                String baca = null;
                for (int i = 1; i <= baris; i++) {
                    baca = r.readLine();
                    if (baca == null) return null;
                }
                return baca.trim();
            } finally {
                r.close();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Mengganti baris ke-N (mulai 1) isi sumber dengan teks tambalan.
     * Mengembalikan isi baru; isi asal bila baris di luar jangkauan.
     */
    public static String sisipkanTambalan(String isiSumber, int baris, String tambalan) {
        if (isiSumber == null || tambalan == null || baris < 1) return isiSumber;
        try {
            String[] barisTeks = isiSumber.split("\n", -1);
            if (baris > barisTeks.length) return isiSumber;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < barisTeks.length; i++) {
                if (i > 0) b.append('\n');
                if (i == baris - 1) b.append(tambalan);
                else b.append(barisTeks[i]);
            }
            return b.toString();
        } catch (Throwable ignored) {
            return isiSumber;
        }
    }

    /** Laporan autopsi satu crash sebagai teks siap tampil. */
    public static String laporan(HasilUrai h, Diagnosis d,
                                 String barisKode, String teksTambalan) {
        StringBuilder b = new StringBuilder();
        if (h.idCrash != null) {
            b.append(TeksUI.t("ID Crash: ")).append(h.idCrash).append('\n');
        }
        if (h.kelas != null) {
            b.append(TeksUI.t("Kelas: ")).append(h.kelas);
            if (h.baris > 0) b.append(TeksUI.t(", baris ")).append(h.baris);
            b.append('\n');
        }
        if (h.jenisGalat != null) {
            b.append(TeksUI.t("Jenis galat: ")).append(h.jenisGalat);
            if (h.pesan != null && h.pesan.length() > 0) {
                b.append(": ").append(h.pesan);
            }
            b.append('\n');
        }
        b.append(TeksUI.t("Diagnosis: ")).append(d.penjelasan).append('\n');
        if (barisKode != null) {
            b.append(TeksUI.t("Baris sumber: ")).append(barisKode).append('\n');
        }
        b.append(TeksUI.t("Tambalan kode:")).append('\n');
        b.append(teksTambalan);
        return b.toString();
    }

    // ------------------------------------------- crash aplikasi hasil build

    /** Nama kelas penangkap crash yang disuntikkan ke proyek. */
    public static final String NAMA_KELAS_PENANGKAP = "PenangkapCrashApp";
    /** Folder di proyek tempat log crash aplikasi hasil build dikumpulkan. */
    public static final String DIR_LOG_HASIL_BUILD = "crash-aplikasi";
    /** Folder publik di Download tempat log crash hasil build bisa ditaruh. */
    public static final String DIR_LOG_UNDUHAN = "/sdcard/Download/crash-aplikasi-hasil-build";

    /**
     * Kode sumber penangkap crash (PenangkapCrashApp, subclass
     * Application) untuk paket tertentu. Mandiri: hanya memakai API
     * Android + java.*. Saat aplikasi hasil build crash, handler ini
     * menulis log berformat sama seperti CrashHandler Create Aplikasi
     * (baris pertama "ID Crash: CR-...") ke folder crash di
     * penyimpanan eksternal aplikasi DAN mencoba salinan ke folder
     * Download publik — lalu meneruskan ke handler bawaan supaya
     * dialog crash Android tetap muncul. Catatan jujur: di Android
     * 11+ salinan publik bisa gagal tanpa izin semua-berkas; salinan
     * di penyimpanan eksternal aplikasi selalu ditulis, dan berkasnya
     * bisa dibagikan ke folder crash-aplikasi proyek lewat aplikasi
     * Berkas.
     */
    public static String hasilkanKodePenangkap(String paket) {
        StringBuilder b = new StringBuilder();
        b.append("package ").append(paket).append(";\n\n");
        b.append("import android.app.Application;\n");
        b.append("import android.content.Context;\n");
        b.append("import android.os.Environment;\n");
        b.append("import java.io.File;\n");
        b.append("import java.io.FileOutputStream;\n");
        b.append("import java.io.OutputStreamWriter;\n");
        b.append("import java.io.PrintWriter;\n");
        b.append("import java.io.StringWriter;\n");
        b.append("import java.text.SimpleDateFormat;\n");
        b.append("import java.util.Date;\n");
        b.append("import java.util.Locale;\n");
        b.append("import java.util.Random;\n\n");
        b.append("/** Penangkap crash yang disuntikkan Create Aplikasi ke proyek.\n");
        b.append(" * Menulis log crash aplikasi hasil build (format ID Crash CR-...)\n");
        b.append(" * supaya Autopsi Crash di IDE bisa membaca dan menambalnya.\n");
        b.append(" * Dibangkitkan oleh AutopsiCrash.suntikkanPenangkap; jangan diubah\n");
        b.append(" * dengan tangan. */\n");
        b.append("public class PenangkapCrashApp extends Application {\n");
        b.append("    @Override public void onCreate() {\n");
        b.append("        super.onCreate();\n");
        b.append("        pasang(this);\n");
        b.append("    }\n\n");
        b.append("    /** Pasang penangkap crash secara manual (bila Application proyek sudah ada). */\n");
        b.append("    public static void pasang(final Context ctx) {\n");
        b.append("        final Thread.UncaughtExceptionHandler lama =\n");
        b.append("                Thread.getDefaultUncaughtExceptionHandler();\n");
        b.append("        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {\n");
        b.append("            @Override public void uncaughtException(Thread t, Throwable e) {\n");
        b.append("                try { tulisLog(ctx, e); } catch (Throwable ignored) {}\n");
        b.append("                if (lama != null) lama.uncaughtException(t, e);\n");
        b.append("            }\n");
        b.append("        });\n");
        b.append("    }\n\n");
        b.append("    private static void tulisLog(Context ctx, Throwable e) {\n");
        b.append("        String id = \"CR-\" + new SimpleDateFormat(\"yyyyMMdd-HHmmss\", Locale.US).format(new Date())\n");
        b.append("                + \"-\" + String.format(Locale.US, \"%06X\", new Random().nextInt(0xFFFFFF));\n");
        b.append("        StringWriter sw = new StringWriter();\n");
        b.append("        e.printStackTrace(new PrintWriter(sw));\n");
        b.append("        StringBuilder isi = new StringBuilder();\n");
        b.append("        isi.append(\"ID Crash: \").append(id).append('\\n');\n");
        b.append("        isi.append(\"package=\").append(ctx.getPackageName()).append('\\n');\n");
        b.append("        isi.append(sw.toString());\n");
        b.append("        String nama = \"crash-\" + id + \".log\";\n");
        b.append("        try {\n");
        b.append("            File dir = new File(ctx.getExternalFilesDir(null), \"crash\");\n");
        b.append("            dir.mkdirs();\n");
        b.append("            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(new File(dir, nama)), \"UTF-8\");\n");
        b.append("            w.write(isi.toString()); w.close();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("        try {\n");
        b.append("            File publik = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), \"crash-\" + ctx.getPackageName());\n");
        b.append("            publik.mkdirs();\n");
        b.append("            OutputStreamWriter w2 = new OutputStreamWriter(new FileOutputStream(new File(publik, nama)), \"UTF-8\");\n");
        b.append("            w2.write(isi.toString()); w2.close();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("    }\n");
        b.append("}\n");
        return b.toString();
    }

    /**
     * Menyuntikkan penangkap crash ke proyek: menulis sumber
     * PenangkapCrashApp dan mendaftarkannya sebagai android:name di
     * tag &lt;application&gt; manifest proyek (bila tag itu belum punya
     * android:name). Bila proyek sudah punya Application sendiri,
     * sumber tetap ditulis tetapi manifest TIDAK diubah — pesan
     * hasilnya memberi satu baris yang harus ditambahkan di
     * onCreate Application itu. Build berikutnya mengompilasi
     * penangkap itu ke APK hasil. Mengembalikan pesan hasil.
     */
    public static String suntikkanPenangkap(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "GAGAL: folder proyek tidak ditemukan.";
        }
        File manifest = AgenUji.cariManifest(dirProyek);
        if (manifest == null) {
            return "GAGAL: AndroidManifest.xml proyek tidak ditemukan.";
        }
        String xml = AgenUji.bacaTeks(manifest);
        if (xml == null) return "GAGAL: manifest proyek tidak bisa dibaca.";
        String paket = AgenUji.paketDariManifest(xml);
        if (paket.length() == 0) return "GAGAL: package di manifest tidak terbaca.";
        File dirJava = new File(dirProyek, "app/src/main/java/" + paket.replace('.', '/'));
        dirJava.mkdirs();
        if (!AgenUji.tulisTeks(new File(dirJava, NAMA_KELAS_PENANGKAP + ".java"),
                hasilkanKodePenangkap(paket))) {
            return "GAGAL: tidak bisa menulis sumber penangkap crash.";
        }
        Matcher mApp = Pattern.compile("<application\\b[^>]*>").matcher(xml);
        if (!mApp.find()) {
            return "GAGAL: tag <application> tidak ditemukan di manifest proyek.";
        }
        String tag = mApp.group();
        Matcher mNama = Pattern.compile("android:name=\"([^\"]+)\"").matcher(tag);
        if (mNama.find()) {
            String nama = mNama.group(1);
            if (nama.equals("." + NAMA_KELAS_PENANGKAP)
                    || nama.equals(paket + "." + NAMA_KELAS_PENANGKAP)) {
                return "OK: penangkap crash sudah terpasang di proyek ini.";
            }
            return "OK (SEBAGIAN): sumber penangkap ditulis, tetapi proyek sudah punya Application sendiri ("
                    + nama + "). Tambahkan satu baris ini di onCreate Application itu: "
                    + NAMA_KELAS_PENANGKAP + ".pasang(this);";
        }
        String tagBaru = tag.substring(0, tag.length() - 1)
                + " android:name=\"." + NAMA_KELAS_PENANGKAP + "\">";
        String baru = xml.substring(0, mApp.start()) + tagBaru + xml.substring(mApp.end());
        baru = ServerMikro.tambahIzin(baru, "WRITE_EXTERNAL_STORAGE");
        if (!AgenUji.tulisTeks(manifest, baru)) {
            return "GAGAL: tidak bisa menulis manifest proyek.";
        }
        return "OK: penangkap crash dipasang. Build APK proyek ini — bila aplikasi hasil build crash, "
                + "log-nya ditulis ke folder crash aplikasi dan (bila diizinkan HP) ke Download/crash-"
                + paket + ", lalu bisa dibaca di Autopsi Crash.";
    }

    /** Melepas penangkap crash dari proyek (sumber + android:name bila itu milik kita). */
    public static String lepasPenangkap(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "GAGAL: folder proyek tidak ditemukan.";
        }
        File manifest = AgenUji.cariManifest(dirProyek);
        if (manifest == null) return "GAGAL: AndroidManifest.xml proyek tidak ditemukan.";
        String xml = AgenUji.bacaTeks(manifest);
        if (xml == null) return "GAGAL: manifest proyek tidak bisa dibaca.";
        String paket = AgenUji.paketDariManifest(xml);
        File sumber = null;
        if (paket.length() > 0) {
            sumber = new File(dirProyek, "app/src/main/java/" + paket.replace('.', '/')
                    + "/" + NAMA_KELAS_PENANGKAP + ".java");
        }
        boolean hapusSumber = sumber != null && sumber.isFile() && sumber.delete();
        boolean hapusManifest = false;
        Matcher mApp = Pattern.compile("<application\\b[^>]*>").matcher(xml);
        if (mApp.find()) {
            String tag = mApp.group();
            String tagBaru = tag.replace(" android:name=\"." + NAMA_KELAS_PENANGKAP + "\"", "")
                    .replace(" android:name=\"" + paket + "." + NAMA_KELAS_PENANGKAP + "\"", "");
            if (!tagBaru.equals(tag)) {
                String baru = xml.substring(0, mApp.start()) + tagBaru + xml.substring(mApp.end());
                hapusManifest = AgenUji.tulisTeks(manifest, baru);
            }
        }
        if (!hapusSumber && !hapusManifest) {
            return "GAGAL: penangkap crash tidak ditemukan di proyek ini.";
        }
        return "OK: penangkap crash dilepas dari proyek.";
    }

    /**
     * Daftar berkas log crash aplikasi hasil build: semua .log di
     * &lt;proyek&gt;/crash-aplikasi/ dan di
     * /sdcard/Download/crash-aplikasi-hasil-build/. Urut terbaru dulu.
     */
    public static List<File> daftarLogHasilBuild(File dirProyek) {
        List<File> hasil = new ArrayList<File>();
        File[] folder = new File[2];
        folder[0] = dirProyek == null ? null : new File(dirProyek, DIR_LOG_HASIL_BUILD);
        folder[1] = new File(DIR_LOG_UNDUHAN);
        for (int i = 0; i < folder.length; i++) {
            File d = folder[i];
            if (d == null) continue;
            File[] isi = d.listFiles();
            if (isi == null) continue;
            for (int j = 0; j < isi.length; j++) {
                if (isi[j].isFile() && isi[j].getName().endsWith(".log")) hasil.add(isi[j]);
            }
        }
        Collections.sort(hasil, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return hasil;
    }

    /**
     * Mengurai semua log crash aplikasi hasil build menjadi daftar
     * HasilUrai (yang tidak bisa diurai dilewati). Log dibaca dengan
     * CrashHandler.bacaTeks bila tersedia, bila tidak dengan pembaca
     * UTF-8 sendiri.
     */
    public static List<HasilUrai> bacaLogHasilBuild(File dirProyek) {
        List<HasilUrai> hasil = new ArrayList<HasilUrai>();
        List<File> berkas = daftarLogHasilBuild(dirProyek);
        for (int i = 0; i < berkas.size(); i++) {
            String isi = bacaTeksLog(berkas.get(i));
            if (isi == null) continue;
            HasilUrai h = uraiLogCrash(isi);
            if (h != null) hasil.add(h);
        }
        return hasil;
    }

    /** Membaca berkas log (UTF-8, maks. 64 KB seperti CrashHandler.bacaTeks). */
    static String bacaTeksLog(File f) {
        try {
            String s = CrashHandler.bacaTeks(f);
            if (s != null) return s;
        } catch (Throwable ignored) {
        }
        try {
            if (f == null || !f.isFile()) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] data = new byte[(int) Math.min(f.length(), 65536)];
            int n = in.read(data);
            in.close();
            if (n <= 0) return null;
            return new String(data, 0, n, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }
}
