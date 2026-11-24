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
 * BATASAN JUJUR: yang dibangun di sini adalah mesin skenario teks dan
 * simulasi urutannya (dry run). Penyuntikan agen penguji ke dalam APK
 * lewat D8 (agen berjalan di perangkat dan menekan tombol sungguhan)
 * adalah tahap berikut dan BELUM aktif — belum ada kode di sini yang
 * menyuntikkan apa pun ke APK atau menekan tombol di perangkat.
 *
 * Model skenario (teks, satu langkah per baris):
 *   KETUK &lt;label&gt;            — ketuk tombol berlabel itu
 *   ISI &lt;label&gt; = &lt;nilai&gt;    — isi kolom berlabel itu
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
        /** Jenis: KETUK | ISI | TUNGGU | KEMBALI. */
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
        // Buang TUNGGU di ujung bila ada, lalu tutup dengan KEMBALI.
        while (!langkah.isEmpty()
                && "TUNGGU".equals(langkah.get(langkah.size() - 1).jenis)) {
            langkah.remove(langkah.size() - 1);
        }
        if (!langkah.isEmpty()) langkah.add(Langkah.kembali());
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
