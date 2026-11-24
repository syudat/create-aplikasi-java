package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
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
}
