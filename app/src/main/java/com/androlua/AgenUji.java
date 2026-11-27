package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robot Penguji Antarmuka Otonom dan Perekam Skenario Teks (Fitur 3
 * dari daftar Gemini, 5.7) — mesin skenario + dry run + laporan crash.
 *
 * DUA CARA MENJALANKAN SKENARIO:
 * 1) Dry run (jalankanSkenarioDryRun): simulasi urutan langkah saja,
 *    tanpa perangkat dan tanpa menekan tombol sungguhan.
 * 2) Agen sungguhan (suntikkanAgen): IDE menyuntikkan
 *    AgenUjiRunnerActivity + aset skenario ke dalam proyek, lalu proyek
 *    di-build seperti biasa (BangunJava mengompilasi SEMUA .java di
 *    proyek; BangunLokal menyalin blok &lt;activity&gt; dari manifest
 *    proyek ke manifest APK hasil). Agen itu berjalan DI DALAM proses
 *    aplikasi hasil build: ia membuka activity target, lalu menekan
 *    tombol sungguhan (View.performClick), mengisi kolom sungguhan
 *    (EditText.setText), dan menggulir (ScrollView.scrollBy) — semua
 *    dispatch UI nyata di proses aplikasi itu sendiri, tanpa perlu
 *    layanan aksesibilitas dan tanpa izin sistem apa pun. Crash yang
 *    terjadi selama skenario ditangkap handler-nya dan dicatat beserta
 *    nomor langkahnya; laporan tampil di layar agen (dibaca Jieshuo)
 *    dan ditulis ke berkas log.
 *
 * Model skenario (teks, satu langkah per baris):
 *   KETUK &lt;label&gt;            — ketuk tombol berlabel itu
 *   ISI &lt;label&gt; = &lt;nilai&gt;    — isi kolom berlabel itu
 *   GULIR BAWAH | GULIR ATAS — gulir layar
 *   TUNGGU &lt;ms&gt; ms            — jeda
 *   KEMBALI                  — tombol kembali
 *
 * Skenario dibangun otomatis dari manifest proyek (teks) + daftar
 * deskriptor antarmuka ("TOMBOL &lt;label&gt;" / "INPUT &lt;label&gt;" /
 * "AKTIVITAS &lt;nama kelas&gt;", satu per baris — deskriptor ini
 * dihasilkan dari pemindaian layout XML proyek dan, bila manifest
 * sumber tidak ada, dari daftar aktivitas APK hasil AnalisisApk).
 * Setiap kolom input diisi tiga nilai ekstrem: kosong, angka 20 digit,
 * dan karakter khusus — supaya kolom yang rapuh ketahuan sebelum
 * dirilis.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JVM.
 */
public class AgenUji {

    /** Nilai ekstrem untuk mengisi kolom input (kosong, panjang, khusus). */
    public static final String[] NILAI_EKSTREM = {
            "",
            "12345678901234567890",
            "!@#$%^&*"
    };

    /** Jeda antar langkah dalam skenario otomatis (ms, hanya simulasi). */
    public static final long TUNGGU_ANTAR_LANGKAH_MS = 500;

    /** Satu langkah skenario. */
    public static class Langkah {
        /** Jenis: KETUK | ISI | GULIR | TUNGGU | KEMBALI. */
        public final String jenis;
        /** Label tombol/kolom (kosong untuk TUNGGU/KEMBALI). */
        public final String label;
        /** Nilai isian (hanya untuk ISI). */
        public final String nilai;
        /** Jeda (hanya untuk TUNGGU, dalam ms). */
        public final long tungguMs;

        public Langkah(String jenis, String label, String nilai, long tungguMs) {
            this.jenis = jenis == null ? "" : jenis;
            this.label = label == null ? "" : label;
            this.nilai = nilai == null ? "" : nilai;
            this.tungguMs = tungguMs;
        }

        public static Langkah ketuk(String label) {
            return new Langkah("KETUK", label, "", 0);
        }

        public static Langkah isi(String label, String nilai) {
            return new Langkah("ISI", label, nilai, 0);
        }

        public static Langkah gulir(String arah) {
            return new Langkah("GULIR", arah == null ? "" : arah, "", 0);
        }

        public static Langkah tunggu(long ms) {
            return new Langkah("TUNGGU", "", "", ms);
        }

        public static Langkah kembali() {
            return new Langkah("KEMBALI", "", "", 0);
        }

        /** Satu baris teks skenario, mis. "ISI Nama = 123" atau "KETUK Simpan". */
        public String teks() {
            if ("KETUK".equals(jenis)) return "KETUK " + label;
            if ("ISI".equals(jenis)) return "ISI " + label + " = " + nilai;
            if ("GULIR".equals(jenis)) return "GULIR " + label;
            if ("TUNGGU".equals(jenis)) return "TUNGGU " + tungguMs + " ms";
            if ("KEMBALI".equals(jenis)) return "KEMBALI";
            return jenis + " " + label;
        }

        /**
         * Urai satu baris teks skenario kembali menjadi langkah.
         * Null bila baris tidak bisa diurai (baris kosong / rusak).
         */
        public static Langkah urai(String baris) {
            if (baris == null) return null;
            String b = baris.trim();
            // Buang penomoran "1. " di depan bila ada.
            int titik = b.indexOf(". ");
            if (titik > 0 && titik <= 4) {
                String depan = b.substring(0, titik).trim();
                boolean angka = depan.length() > 0;
                for (int i = 0; i < depan.length(); i++) {
                    if (!Character.isDigit(depan.charAt(i))) angka = false;
                }
                if (angka) b = b.substring(titik + 2).trim();
            }
            if (b.startsWith("KETUK ")) return ketuk(b.substring(6).trim());
            if (b.startsWith("GULIR ")) {
                String arah = b.substring(6).trim().toUpperCase(Locale.US);
                if ("BAWAH".equals(arah) || "ATAS".equals(arah)) return gulir(arah);
                return null;
            }
            if (b.startsWith("ISI ")) {
                String sisa = b.substring(4);
                int samaDengan = sisa.indexOf(" = ");
                if (samaDengan >= 0) {
                    return isi(sisa.substring(0, samaDengan).trim(),
                            sisa.substring(samaDengan + 3));
                }
                // Nilai kosong: sesudah trim, barisnya berakhir di "=".
                if (sisa.endsWith("=")) {
                    return isi(sisa.substring(0, sisa.length() - 1).trim(), "");
                }
                return isi(sisa.trim(), "");
            }
            if (b.startsWith("TUNGGU ")) {
                String sisa = b.substring(7).trim();
                if (sisa.endsWith(" ms")) sisa = sisa.substring(0, sisa.length() - 3).trim();
                try {
                    return tunggu(Long.parseLong(sisa));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (b.equals("KEMBALI")) return kembali();
            return null;
        }
    }

    /** Ringkasan hasil uraian sebuah log uji. */
    public static class Ringkasan {
        public int jumlahLangkah;
        public int jumlahTemuan;
        public boolean adaCrash;
        public String idCrash = "";

        /** Laporan teks satu baris per angka (tanpa warna). */
        public String teks() {
            StringBuilder b = new StringBuilder();
            b.append(TeksUI.t("Ringkasan log uji: ")).append(jumlahLangkah)
             .append(TeksUI.t(" langkah, ")).append(jumlahTemuan)
             .append(TeksUI.t(" temuan."));
            if (adaCrash) {
                b.append(' ').append(TeksUI.t("Ada crash, ID: ")).append(idCrash);
            } else {
                b.append(' ').append(TeksUI.t("Tidak ada crash."));
            }
            return b.toString();
        }
    }

    // ------------------------------------------------------- bangun skenario

    /**
     * Bangun skenario otomatis dari manifest proyek (teks XML) + daftar
     * deskriptor antarmuka (satu per baris: "TOMBOL &lt;label&gt;",
     * "INPUT &lt;label&gt;", atau "AKTIVITAS &lt;nama kelas&gt;").
     * Satu KETUK per aktivitas di manifest, satu KETUK per tombol, tiga
     * ISI ekstrem per kolom input, lalu KEMBALI di akhir. Masukan null
     * atau kosong menghasilkan skenario kosong (bukan galat).
     */
    public static List<Langkah> bangunSkenarioDariManifest(String manifestXml,
                                                           String daftarAktivitas) {
        List<Langkah> langkah = new ArrayList<Langkah>();
        Set<String> ketukSudah = new LinkedHashSet<String>();
        if (manifestXml != null) {
            Matcher m = Pattern.compile(
                    "<activity[^>]*android:name=\"([^\"]+)\"").matcher(manifestXml);
            while (m.find()) {
                String nama = namaSingkat(m.group(1));
                if (nama.length() == 0) continue;
                String label = TeksUI.t("Buka ") + nama;
                if (ketukSudah.add(label)) langkah.add(Langkah.ketuk(label));
            }
        }
        if (daftarAktivitas != null) {
            for (String barisMentah : daftarAktivitas.split("\n", -1)) {
                String baris = barisMentah.trim();
                if (baris.length() == 0) continue;
                if (baris.startsWith("TOMBOL ")) {
                    String label = baris.substring(7).trim();
                    if (label.length() > 0 && ketukSudah.add(label)) {
                        langkah.add(Langkah.ketuk(label));
                    }
                } else if (baris.startsWith("INPUT ")) {
                    String label = baris.substring(6).trim();
                    if (label.length() == 0) continue;
                    for (int i = 0; i < NILAI_EKSTREM.length; i++) {
                        langkah.add(Langkah.isi(label, NILAI_EKSTREM[i]));
                    }
                } else if (baris.startsWith("AKTIVITAS ")) {
                    String nama = namaSingkat(baris.substring(10).trim());
                    if (nama.length() == 0) continue;
                    String label = TeksUI.t("Buka ") + nama;
                    if (ketukSudah.add(label)) langkah.add(Langkah.ketuk(label));
                } else if (baris.indexOf('.') >= 0 && baris.indexOf(' ') < 0) {
                    // Nama kelas polos (mis. dari daftar aktivitas APK).
                    String nama = namaSingkat(baris);
                    String label = TeksUI.t("Buka ") + nama;
                    if (ketukSudah.add(label)) langkah.add(Langkah.ketuk(label));
                } else {
                    // Baris lain dianggap label tombol.
                    if (ketukSudah.add(baris)) langkah.add(Langkah.ketuk(baris));
                }
                if (!langkah.isEmpty()
                        && !"TUNGGU".equals(langkah.get(langkah.size() - 1).jenis)) {
                    langkah.add(Langkah.tunggu(TUNGGU_ANTAR_LANGKAH_MS));
                }
            }
        }
        // Buang TUNGGU di ujung bila ada, lalu gulir sekali dan tutup dengan KEMBALI.
        while (!langkah.isEmpty()
                && "TUNGGU".equals(langkah.get(langkah.size() - 1).jenis)) {
            langkah.remove(langkah.size() - 1);
        }
        if (!langkah.isEmpty()) {
            langkah.add(Langkah.gulir("BAWAH"));
            langkah.add(Langkah.kembali());
        }
        return langkah;
    }

    /**
     * Pindai satu berkas layout XML (teks) menjadi deskriptor antarmuka
     * ("TOMBOL &lt;label&gt;" per Button, "INPUT &lt;label&gt;" per
     * EditText). Label diambil dari android:text, lalu android:hint,
     * lalu nama id. Null/ XML kosong menghasilkan daftar kosong.
     */
    public static List<String> uraiLayout(String xmlLayout) {
        List<String> hasil = new ArrayList<String>();
        if (xmlLayout == null) return hasil;
        Matcher m = Pattern.compile("<(Button|EditText)\\b[^>]*>").matcher(xmlLayout);
        while (m.find()) {
            String tag = m.group(0);
            String jenis = m.group(1);
            String label = atribut(tag, "android:text");
            if (label == null || label.length() == 0) label = atribut(tag, "android:hint");
            if (label == null || label.length() == 0) {
                String id = atribut(tag, "android:id");
                if (id != null && id.startsWith("@+id/")) label = id.substring(5);
                else if (id != null && id.startsWith("@id/")) label = id.substring(4);
            }
            if (label == null || label.length() == 0) label = jenis;
            hasil.add(("Button".equals(jenis) ? "TOMBOL " : "INPUT ") + label);
        }
        return hasil;
    }

    /** Skenario menjadi teks bernomor ("1. KETUK Simpan", ...). */
    public static String teksSkenario(List<Langkah> langkah) {
        if (langkah == null || langkah.isEmpty()) {
            return TeksUI.t("Belum ada skenario. Tekan Buat Skenario Otomatis dulu.");
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < langkah.size(); i++) {
            b.append(i + 1).append(". ").append(langkah.get(i).teks()).append('\n');
        }
        return b.toString();
    }

    /** Urai teks skenario bernomor kembali menjadi daftar langkah. */
    public static List<Langkah> uraiSkenario(String teks) {
        List<Langkah> hasil = new ArrayList<Langkah>();
        if (teks == null) return hasil;
        for (String baris : teks.split("\n", -1)) {
            Langkah l = Langkah.urai(baris);
            if (l != null) hasil.add(l);
        }
        return hasil;
    }

    // ------------------------------------------------------- dry run + log

    /** Stempel waktu "yyyy-MM-dd HH:mm:ss.SSS" untuk baris log. */
    public static String stempelWaktu() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
    }

    /**
     * Jalankan skenario sebagai dry run: simulasi urutan langkah saja,
     * tanpa perangkat dan tanpa menekan tombol sungguhan (TUNGGU tidak
     * menunggu sungguhan). Mengembalikan log teks berstempel waktu.
     */
    public static String jalankanSkenarioDryRun(List<Langkah> langkah) {
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Laporan dry run robot uji")).append('\n');
        b.append(TeksUI.t("Waktu mulai: ")).append(stempelWaktu()).append('\n');
        if (langkah == null || langkah.isEmpty()) {
            b.append(TeksUI.t("Tidak ada langkah dalam skenario.")).append('\n');
            b.append(TeksUI.t("Dry run selesai: 0 langkah, 0 temuan.")).append('\n');
            return b.toString();
        }
        for (int i = 0; i < langkah.size(); i++) {
            Langkah l = langkah.get(i);
            b.append('[').append(stempelWaktu()).append("] ")
             .append(TeksUI.t("Langkah ")).append(i + 1).append('/')
             .append(langkah.size()).append(": ").append(l.teks());
            if ("TUNGGU".equals(l.jenis)) {
                b.append(TeksUI.t(" (simulasi, tidak menunggu sungguhan)"));
            }
            b.append('\n');
        }
        b.append(TeksUI.t("Dry run selesai: ")).append(langkah.size())
         .append(TeksUI.t(" langkah, 0 temuan.")).append('\n');
        b.append(TeksUI.t("Ini simulasi urutan saja; tidak ada tombol sungguhan yang ditekan."))
         .append('\n');
        return b.toString();
    }

    /**
     * Susun blok laporan crash: ID crash + urutan langkah pemicunya,
     * supaya bisa dibaca ulang di dalam Create Aplikasi.
     */
    public static String catatCrash(String idCrash, List<Langkah> pemicu) {
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Laporan crash robot uji")).append('\n');
        b.append(TeksUI.t("ID crash: "))
         .append(idCrash == null || idCrash.trim().length() == 0
                 ? TeksUI.t("(tidak diketahui)") : idCrash.trim()).append('\n');
        b.append(TeksUI.t("Waktu: ")).append(stempelWaktu()).append('\n');
        if (pemicu == null || pemicu.isEmpty()) {
            b.append(TeksUI.t("Tidak ada langkah pemicu tercatat.")).append('\n');
        } else {
            b.append(TeksUI.t("Langkah pemicu (")).append(pemicu.size())
             .append("):\n");
            for (int i = 0; i < pemicu.size(); i++) {
                b.append("  ").append(i + 1).append(". ")
                 .append(pemicu.get(i).teks()).append('\n');
            }
        }
        b.append(TeksUI.t("Ulangi langkah di atas untuk memicu crash ini lagi."))
         .append('\n');
        return b.toString();
    }

    /**
     * Urai sebuah log uji menjadi ringkasan (jumlah langkah / temuan /
     * crash). Log rusak atau null menghasilkan ringkasan kosong yang
     * aman (0 langkah, 0 temuan, tanpa crash), tidak pernah melempar.
     */
    public static Ringkasan uraiLog(String log) {
        Ringkasan r = new Ringkasan();
        if (log == null) return r;
        try {
            Pattern polaLangkah = Pattern.compile("Langkah \\d+/\\d+:");
            for (String baris : log.split("\n", -1)) {
                if (polaLangkah.matcher(baris).find()) r.jumlahLangkah++;
                String atas = baris.toUpperCase(Locale.US);
                // Temuan hanya baris bertanda "[TEMUAN]"/"TEMUAN:"/"[GAGAL]" —
                // baris ringkasan "0 temuan" di kaki log bukan temuan.
                if (atas.contains("[TEMUAN]") || atas.contains("TEMUAN:")
                        || atas.contains("[GAGAL]")) r.jumlahTemuan++;
                if (baris.contains("ID crash:")) {
                    r.adaCrash = true;
                    String id = baris.substring(baris.indexOf("ID crash:") + 9).trim();
                    if (id.length() > 0 && !TeksUI.t("(tidak diketahui)").equals(id)) {
                        r.idCrash = id;
                    }
                } else if (atas.contains("CRASH")) {
                    r.adaCrash = true;
                }
            }
        } catch (Throwable ignored) {
        }
        return r;
    }

    // ------------------------------------------------------- berkas log

    /** Nama berkas log uji baru ("log-uji-20261124-143000.txt"). */
    public static String namaBerkasLog() {
        return "log-uji-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + ".txt";
    }

    /** Tulis teks ke berkas (UTF-8). True bila berhasil. */
    public static boolean tulisTeks(File f, String isi) {
        if (f == null) return false;
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi == null ? "" : isi);
            } finally {
                w.close();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Baca berkas teks (UTF-8); null bila tidak bisa dibaca. */
    public static String bacaTeks(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            try {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            } finally {
                r.close();
            }
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ------------------------------------------------------- agen sungguhan

    /** Nama kelas agen yang disuntikkan ke proyek. */
    public static final String NAMA_KELAS_AGEN = "AgenUjiRunnerActivity";
    /** Nama aset skenario di dalam proyek / APK hasil. */
    public static final String NAMA_ASET_SKENARIO = "agen-uji-skenario.txt";

    /**
     * Mencari manifest proyek: app/src/main/AndroidManifest.xml, bila
     * tidak ada, AndroidManifest.xml di root proyek. Null bila tidak ada.
     */
    public static File cariManifest(File dirProyek) {
        if (dirProyek == null) return null;
        File m = new File(dirProyek, "app/src/main/AndroidManifest.xml");
        if (m.isFile()) return m;
        m = new File(dirProyek, "AndroidManifest.xml");
        if (m.isFile()) return m;
        return null;
    }

    /** Nilai atribut package="..." di manifest; "" bila tidak terbaca. */
    public static String paketDariManifest(String xmlManifest) {
        if (xmlManifest == null) return "";
        Matcher m = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(xmlManifest);
        return m.find() ? m.group(1) : "";
    }

    /** Activity pertama di manifest (nama lengkap); "" bila tidak ada. */
    public static String aktivitasPertamaDariManifest(String xmlManifest, String paket) {
        if (xmlManifest == null) return "";
        Matcher m = Pattern.compile("<activity\\b[^>]*android:name=\"([^\"]+)\"")
                .matcher(xmlManifest);
        if (!m.find()) return "";
        String nama = m.group(1);
        if (nama.startsWith(".")) return paket + nama;
        if (nama.contains(".")) return nama;
        return paket + "." + nama;
    }

    /**
     * Kode sumber agen penguji (AgenUjiRunnerActivity) untuk paket dan
     * activity target tertentu. Agen ini mandiri: hanya memakai API
     * Android + java.* sehingga bisa dikompilasi ECJ bersama sumber
     * proyek apa pun. Ia membaca skenario dari aset
     * agen-uji-skenario.txt, membuka activity target, lalu menjalankan
     * tiap langkah dengan dispatch UI nyata di utas UI proses aplikasi
     * itu sendiri (performClick / setText / scrollBy), mencatat
     * berhasil/gagal per langkah, dan menangkap crash selama skenario.
     */
    public static String hasilkanKodeAgen(String paket, String aktivitasTarget) {
        StringBuilder b = new StringBuilder();
        b.append("package ").append(paket).append(";\n\n");
        b.append("import android.app.Activity;\n");
        b.append("import android.app.Application;\n");
        b.append("import android.content.Intent;\n");
        b.append("import android.os.Bundle;\n");
        b.append("import android.os.Environment;\n");
        b.append("import android.view.View;\n");
        b.append("import android.view.ViewGroup;\n");
        b.append("import android.widget.EditText;\n");
        b.append("import android.widget.LinearLayout;\n");
        b.append("import android.widget.ListView;\n");
        b.append("import android.widget.ScrollView;\n");
        b.append("import android.widget.TextView;\n");
        b.append("import java.io.BufferedReader;\n");
        b.append("import java.io.File;\n");
        b.append("import java.io.FileOutputStream;\n");
        b.append("import java.io.InputStreamReader;\n");
        b.append("import java.io.OutputStreamWriter;\n");
        b.append("import java.io.PrintWriter;\n");
        b.append("import java.io.StringWriter;\n");
        b.append("import java.text.SimpleDateFormat;\n");
        b.append("import java.util.ArrayList;\n");
        b.append("import java.util.Date;\n");
        b.append("import java.util.List;\n");
        b.append("import java.util.Locale;\n");
        b.append("import java.util.concurrent.CountDownLatch;\n");
        b.append("import java.util.concurrent.TimeUnit;\n\n");
        b.append("/** Agen penguji yang disuntikkan Create Aplikasi ke proyek.\n");
        b.append(" * Berjalan di dalam proses aplikasi hasil build: membuka activity\n");
        b.append(" * target lalu menekan tombol sungguhan (performClick), mengisi\n");
        b.append(" * kolom sungguhan (setText), dan menggulir (scrollBy). Laporan\n");
        b.append(" * tampil di layar ini dan ditulis ke berkas log. Dibangkitkan\n");
        b.append(" * oleh AgenUji.suntikkanAgen; jangan diubah dengan tangan. */\n");
        b.append("public class AgenUjiRunnerActivity extends Activity {\n");
        b.append("    public static final String TARGET = \"").append(aktivitasTarget).append("\";\n");
        b.append("    private TextView tvLog;\n");
        b.append("    private final StringBuilder log = new StringBuilder();\n");
        b.append("    private volatile Activity aktivitasAktif;\n");
        b.append("    private volatile int langkahCrash = -1;\n");
        b.append("    private Thread.UncaughtExceptionHandler handlerLama;\n\n");
        b.append("    @Override protected void onCreate(Bundle savedInstanceState) {\n");
        b.append("        super.onCreate(savedInstanceState);\n");
        b.append("        LinearLayout r = new LinearLayout(this);\n");
        b.append("        r.setOrientation(LinearLayout.VERTICAL);\n");
        b.append("        r.setPadding(24, 16, 24, 16);\n");
        b.append("        tvLog = new TextView(this);\n");
        b.append("        tvLog.setTextSize(14);\n");
        b.append("        tvLog.setTextIsSelectable(true);\n");
        b.append("        tvLog.setText(\"Agen uji berjalan. Tunggu sampai selesai.\");\n");
        b.append("        r.addView(tvLog);\n");
        b.append("        ScrollView sv = new ScrollView(this);\n");
        b.append("        sv.addView(r);\n");
        b.append("        setContentView(sv);\n");
        b.append("        setTitle(\"Agen Uji\");\n");
        b.append("        handlerLama = Thread.getDefaultUncaughtExceptionHandler();\n");
        b.append("        final Thread.UncaughtExceptionHandler lama = handlerLama;\n");
        b.append("        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {\n");
        b.append("            @Override public void uncaughtException(Thread t, Throwable e) {\n");
        b.append("                StringWriter sw = new StringWriter();\n");
        b.append("                e.printStackTrace(new PrintWriter(sw));\n");
        b.append("                catat(\"[CRASH] pada langkah \" + langkahCrash + \": \" + e.toString());\n");
        b.append("                catat(sw.toString());\n");
        b.append("                tulisLogKeBerkas();\n");
        b.append("                if (lama != null) lama.uncaughtException(t, e);\n");
        b.append("            }\n");
        b.append("        });\n");
        b.append("        getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {\n");
        b.append("            @Override public void onActivityResumed(Activity a) { aktivitasAktif = a; }\n");
        b.append("            @Override public void onActivityCreated(Activity a, Bundle b) {}\n");
        b.append("            @Override public void onActivityStarted(Activity a) {}\n");
        b.append("            @Override public void onActivityPaused(Activity a) {}\n");
        b.append("            @Override public void onActivityStopped(Activity a) {}\n");
        b.append("            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}\n");
        b.append("            @Override public void onActivityDestroyed(Activity a) {}\n");
        b.append("        });\n");
        b.append("        new Thread(new Runnable() {\n");
        b.append("            @Override public void run() { jalankan(); }\n");
        b.append("        }, \"AgenUjiRunner\").start();\n");
        b.append("    }\n\n");
        b.append("    private void jalankan() {\n");
        b.append("        List<String> baris = bacaSkenario();\n");
        b.append("        catat(\"Agen uji mulai: \" + baris.size() + \" langkah. Target: \" + TARGET);\n");
        b.append("        if (baris.isEmpty()) { catat(\"Skenario kosong.\"); tampilkanLog(); return; }\n");
        b.append("        try {\n");
        b.append("            Class<?> cls = Class.forName(TARGET);\n");
        b.append("            Intent it = new Intent(this, cls);\n");
        b.append("            startActivity(it);\n");
        b.append("        } catch (Throwable t) { catat(\"GAGAL membuka target: \" + t.toString()); tampilkanLog(); return; }\n");
        b.append("        int berhasil = 0, gagal = 0;\n");
        b.append("        for (int i = 0; i < baris.size(); i++) {\n");
        b.append("            langkahCrash = i + 1;\n");
        b.append("            String langkah = baris.get(i);\n");
        b.append("            tungguTarget(5000);\n");
        b.append("            boolean ok = jalankanLangkah(langkah);\n");
        b.append("            catat((ok ? \"OK \" : \"GAGAL \") + (i + 1) + \"/\" + baris.size() + \": \" + langkah);\n");
        b.append("            if (ok) berhasil++; else gagal++;\n");
        b.append("            try { Thread.sleep(500); } catch (InterruptedException ignored) {}\n");
        b.append("        }\n");
        b.append("        langkahCrash = -1;\n");
        b.append("        catat(\"Selesai: \" + berhasil + \" berhasil, \" + gagal + \" gagal dari \" + baris.size() + \" langkah.\");\n");
        b.append("        tulisLogKeBerkas();\n");
        b.append("        tampilkanLog();\n");
        b.append("        try { getWindow().getDecorView().announceForAccessibility(\"Agen uji selesai: \" + berhasil + \" berhasil, \" + gagal + \" gagal.\"); } catch (Throwable ignored) {}\n");
        b.append("    }\n\n");
        b.append("    private List<String> bacaSkenario() {\n");
        b.append("        List<String> hasil = new ArrayList<String>();\n");
        b.append("        try {\n");
        b.append("            BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(\"").append(NAMA_ASET_SKENARIO).append("\"), \"UTF-8\"));\n");
        b.append("            String s;\n");
        b.append("            while ((s = r.readLine()) != null) { s = s.trim(); if (s.length() > 0) hasil.add(s); }\n");
        b.append("            r.close();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("        return hasil;\n");
        b.append("    }\n\n");
        b.append("    private void tungguTarget(long batasMs) {\n");
        b.append("        long akhir = System.currentTimeMillis() + batasMs;\n");
        b.append("        while (System.currentTimeMillis() < akhir) {\n");
        b.append("            Activity a = aktivitasAktif;\n");
        b.append("            if (a != null && a != this) return;\n");
        b.append("            try { Thread.sleep(200); } catch (InterruptedException ignored) {}\n");
        b.append("        }\n");
        b.append("    }\n\n");
        b.append("    private boolean jalankanLangkah(final String langkah) {\n");
        b.append("        final boolean[] hasil = new boolean[1];\n");
        b.append("        final CountDownLatch kait = new CountDownLatch(1);\n");
        b.append("        runOnUiThread(new Runnable() {\n");
        b.append("            @Override public void run() {\n");
        b.append("                try { hasil[0] = langkahDiUi(langkah); } catch (Throwable t) { hasil[0] = false; }\n");
        b.append("                kait.countDown();\n");
        b.append("            }\n");
        b.append("        });\n");
        b.append("        try { kait.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}\n");
        b.append("        return hasil[0];\n");
        b.append("    }\n\n");
        b.append("    private boolean langkahDiUi(String langkah) {\n");
        b.append("        Activity a = aktivitasAktif;\n");
        b.append("        if (a == null || a == this) return false;\n");
        b.append("        View akar = a.getWindow().getDecorView();\n");
        b.append("        if (langkah.startsWith(\"KETUK \")) {\n");
        b.append("            View v = cariView(akar, langkah.substring(6).trim());\n");
        b.append("            if (v == null) return false;\n");
        b.append("            return v.performClick();\n");
        b.append("        }\n");
        b.append("        if (langkah.startsWith(\"ISI \")) {\n");
        b.append("            String sisa = langkah.substring(4);\n");
        b.append("            int samaDengan = sisa.indexOf(\" = \");\n");
        b.append("            String label = samaDengan >= 0 ? sisa.substring(0, samaDengan).trim() : sisa.trim();\n");
        b.append("            String nilai = samaDengan >= 0 ? sisa.substring(samaDengan + 3) : \"\";\n");
        b.append("            View v = cariView(akar, label);\n");
        b.append("            if (!(v instanceof EditText)) return false;\n");
        b.append("            ((EditText) v).setText(nilai);\n");
        b.append("            return true;\n");
        b.append("        }\n");
        b.append("        if (langkah.startsWith(\"GULIR \")) {\n");
        b.append("            boolean bawah = langkah.substring(6).trim().equalsIgnoreCase(\"BAWAH\");\n");
        b.append("            ScrollView sv = cariScroll(akar);\n");
        b.append("            if (sv != null) { sv.scrollBy(0, bawah ? 800 : -800); return true; }\n");
        b.append("            ListView lv = cariList(akar);\n");
        b.append("            if (lv != null) { lv.smoothScrollBy(bawah ? 800 : -800, 300); return true; }\n");
        b.append("            return false;\n");
        b.append("        }\n");
        b.append("        if (langkah.startsWith(\"TUNGGU \")) {\n");
        b.append("            try { Thread.sleep(500); } catch (InterruptedException ignored) {}\n");
        b.append("            return true;\n");
        b.append("        }\n");
        b.append("        if (langkah.equals(\"KEMBALI\")) { a.onBackPressed(); return true; }\n");
        b.append("        return false;\n");
        b.append("    }\n\n");
        b.append("    private static View cariView(View v, String label) {\n");
        b.append("        if (v == null || label == null || label.length() == 0) return null;\n");
        b.append("        if (v instanceof TextView) {\n");
        b.append("            CharSequence teks = ((TextView) v).getText();\n");
        b.append("            CharSequence hint = v instanceof EditText ? ((EditText) v).getHint() : null;\n");
        b.append("            if ((teks != null && label.equals(teks.toString()))\n");
        b.append("                    || (hint != null && label.equals(hint.toString()))\n");
        b.append("                    || label.equals(String.valueOf(v.getContentDescription()))) return v;\n");
        b.append("        } else if (label.equals(String.valueOf(v.getContentDescription()))) return v;\n");
        b.append("        if (v instanceof ViewGroup) {\n");
        b.append("            ViewGroup g = (ViewGroup) v;\n");
        b.append("            for (int i = 0; i < g.getChildCount(); i++) {\n");
        b.append("                View ketemu = cariView(g.getChildAt(i), label);\n");
        b.append("                if (ketemu != null) return ketemu;\n");
        b.append("            }\n");
        b.append("        }\n");
        b.append("        return null;\n");
        b.append("    }\n\n");
        b.append("    private static ScrollView cariScroll(View v) {\n");
        b.append("        if (v instanceof ScrollView) return (ScrollView) v;\n");
        b.append("        if (v instanceof ViewGroup) {\n");
        b.append("            ViewGroup g = (ViewGroup) v;\n");
        b.append("            for (int i = 0; i < g.getChildCount(); i++) {\n");
        b.append("                ScrollView ketemu = cariScroll(g.getChildAt(i));\n");
        b.append("                if (ketemu != null) return ketemu;\n");
        b.append("            }\n");
        b.append("        }\n");
        b.append("        return null;\n");
        b.append("    }\n\n");
        b.append("    private static ListView cariList(View v) {\n");
        b.append("        if (v instanceof ListView) return (ListView) v;\n");
        b.append("        if (v instanceof ViewGroup) {\n");
        b.append("            ViewGroup g = (ViewGroup) v;\n");
        b.append("            for (int i = 0; i < g.getChildCount(); i++) {\n");
        b.append("                ListView ketemu = cariList(g.getChildAt(i));\n");
        b.append("                if (ketemu != null) return ketemu;\n");
        b.append("            }\n");
        b.append("        }\n");
        b.append("        return null;\n");
        b.append("    }\n\n");
        b.append("    private synchronized void catat(String s) {\n");
        b.append("        log.append(\"[\").append(new SimpleDateFormat(\"HH:mm:ss\", Locale.US).format(new Date())).append(\"] \").append(s).append('\\n');\n");
        b.append("    }\n\n");
        b.append("    private void tampilkanLog() {\n");
        b.append("        final String teks = log.toString();\n");
        b.append("        runOnUiThread(new Runnable() {\n");
        b.append("            @Override public void run() { tvLog.setText(teks); }\n");
        b.append("        });\n");
        b.append("    }\n\n");
        b.append("    private void tulisLogKeBerkas() {\n");
        b.append("        String nama = \"log-agen-\" + new SimpleDateFormat(\"yyyyMMdd-HHmmss\", Locale.US).format(new Date()) + \".txt\";\n");
        b.append("        try {\n");
        b.append("            File dir = new File(getExternalFilesDir(null), \"uji-robot\");\n");
        b.append("            dir.mkdirs();\n");
        b.append("            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(new File(dir, nama)), \"UTF-8\");\n");
        b.append("            w.write(log.toString()); w.close();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("        try {\n");
        b.append("            File publik = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), \"uji-robot\");\n");
        b.append("            publik.mkdirs();\n");
        b.append("            OutputStreamWriter w2 = new OutputStreamWriter(new FileOutputStream(new File(publik, nama)), \"UTF-8\");\n");
        b.append("            w2.write(log.toString()); w2.close();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("    }\n");
        b.append("}\n");
        return b.toString();
    }

    /**
     * Menyuntikkan agen penguji ke proyek: menulis sumber agen +
     * aset skenario, dan mendaftarkan activity agen di manifest proyek
     * (ikon peluncur "Uji ...") supaya bisa dibuka dari peluncur.
     * Mengembalikan pesan hasil (diawali "OK" bila berhasil, "GAGAL"
     * bila tidak). Build berikutnya mengompilasi agen itu ke APK.
     * Suntikan kedua menimpa sumber/aset dan tidak menggandakan blok
     * manifest.
     */
    public static String suntikkanAgen(File dirProyek, List<Langkah> langkah) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "GAGAL: folder proyek tidak ditemukan.";
        }
        if (langkah == null || langkah.isEmpty()) {
            return "GAGAL: skenario kosong. Buat skenario otomatis dulu.";
        }
        File manifest = cariManifest(dirProyek);
        if (manifest == null) {
            return "GAGAL: AndroidManifest.xml proyek tidak ditemukan.";
        }
        String xml = bacaTeks(manifest);
        if (xml == null) return "GAGAL: manifest proyek tidak bisa dibaca.";
        String paket = paketDariManifest(xml);
        if (paket.length() == 0) return "GAGAL: package di manifest tidak terbaca.";
        String target = aktivitasPertamaDariManifest(xml, paket);
        if (target.length() == 0) return "GAGAL: tidak ada activity di manifest proyek.";
        String jalurPaket = paket.replace('.', '/');
        File dirJava = new File(dirProyek, "app/src/main/java/" + jalurPaket);
        if (!dirJava.isDirectory() && !new File(dirProyek, "java/" + jalurPaket).isDirectory()) {
            dirJava.mkdirs();
        }
        File sumber = new File(dirJava, NAMA_KELAS_AGEN + ".java");
        if (!tulisTeks(sumber, hasilkanKodeAgen(paket, target))) {
            return "GAGAL: tidak bisa menulis " + sumber.getName() + ".";
        }
        File dirAset = new File(dirProyek, "app/src/main/assets");
        dirAset.mkdirs();
        if (!tulisTeks(new File(dirAset, NAMA_ASET_SKENARIO), teksSkenario(langkah))) {
            return "GAGAL: tidak bisa menulis aset skenario.";
        }
        String blok = "<activity android:name=\"." + NAMA_KELAS_AGEN + "\"\n"
                + "            android:exported=\"true\"\n"
                + "            android:label=\"Uji " + dirProyek.getName() + "\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>";
        String baru = sisipAtauGantiAktivitas(xml, NAMA_KELAS_AGEN, blok);
        if (baru == null || baru.equals(xml)) {
            return "GAGAL: tidak bisa mendaftarkan agen di manifest proyek.";
        }
        if (!tulisTeks(manifest, baru)) {
            return "GAGAL: tidak bisa menulis manifest proyek.";
        }
        return "OK: agen disuntikkan. Build APK proyek ini, pasang, lalu buka ikon \"Uji "
                + dirProyek.getName() + "\" — agen membuka " + target
                + " dan menjalankan " + langkah.size() + " langkah sungguhan.";
    }

    /**
     * Melepas agen dari proyek: menghapus sumber + aset skenario dan
     * blok activity-nya dari manifest. Mengembalikan pesan hasil.
     */
    public static String lepasAgen(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "GAGAL: folder proyek tidak ditemukan.";
        }
        File manifest = cariManifest(dirProyek);
        if (manifest == null) return "GAGAL: AndroidManifest.xml proyek tidak ditemukan.";
        String xml = bacaTeks(manifest);
        if (xml == null) return "GAGAL: manifest proyek tidak bisa dibaca.";
        String paket = paketDariManifest(xml);
        File sumber = null;
        if (paket.length() > 0) {
            sumber = new File(dirProyek, "app/src/main/java/" + paket.replace('.', '/')
                    + "/" + NAMA_KELAS_AGEN + ".java");
        }
        boolean hapusSumber = sumber != null && sumber.isFile() && sumber.delete();
        File aset = new File(dirProyek, "app/src/main/assets/" + NAMA_ASET_SKENARIO);
        boolean hapusAset = aset.isFile() && aset.delete();
        String baru = hapusBlokAktivitas(xml, NAMA_KELAS_AGEN);
        boolean hapusManifest = baru != null && !baru.equals(xml) && tulisTeks(manifest, baru);
        if (!hapusSumber && !hapusAset && !hapusManifest) {
            return "GAGAL: agen tidak ditemukan di proyek ini.";
        }
        return "OK: agen dilepas dari proyek.";
    }

    /** Sisipkan blok activity sebelum </application>; bila nama sudah ada, ganti blok lamanya. */
    static String sisipAtauGantiAktivitas(String xml, String namaKelas, String blok) {
        if (xml == null) return null;
        String tanpa = hapusBlokAktivitas(xml, namaKelas);
        if (tanpa == null) return null;
        int tutup = tanpa.lastIndexOf("</application>");
        if (tutup < 0) return null;
        return tanpa.substring(0, tutup) + "        " + blok + "\n    " + tanpa.substring(tutup);
    }

    /** Hapus blok <activity ...> yang android:name-nya cocok (".Nama" atau nama lengkap). */
    static String hapusBlokAktivitas(String xml, String namaKelas) {
        if (xml == null) return null;
        Pattern p = Pattern.compile("<activity\\b[^>]*?(?:/>|>.*?</activity>)",
                Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        StringBuffer sb = new StringBuffer();
        boolean berubah = false;
        while (m.find()) {
            String blok = m.group();
            Matcher nm = Pattern.compile("android:name=\"([^\"]+)\"").matcher(blok);
            boolean cocok = false;
            if (nm.find()) {
                String n = nm.group(1);
                cocok = n.equals(namaKelas) || n.equals("." + namaKelas)
                        || n.endsWith("." + namaKelas);
            }
            if (cocok) {
                berubah = true;
                m.appendReplacement(sb, "");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(blok));
            }
        }
        m.appendTail(sb);
        return berubah ? sb.toString() : xml;
    }

    // ------------------------------------------------------- pembantu

    /** Nama kelas singkat: bagian sesudah titik terakhir. */
    static String namaSingkat(String namaKelas) {
        if (namaKelas == null) return "";
        String n = namaKelas.trim();
        if (n.startsWith(".")) n = n.substring(1);
        int titik = n.lastIndexOf('.');
        return titik >= 0 ? n.substring(titik + 1) : n;
    }

    /** Nilai atribut android:...="..." di dalam satu tag XML; null bila tidak ada. */
    static String atribut(String tag, String nama) {
        if (tag == null) return null;
        Matcher m = Pattern.compile(Pattern.quote(nama) + "\\s*=\\s*\"([^\"]*)\"")
                .matcher(tag);
        return m.find() ? m.group(1) : null;
    }
}
