package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uji Aplikasi Otomatis (Tahap 3E).
 *
 * Memeriksa sebuah proyek secara otomatis, tanpa membangunnya:
 * (1) init.lua ada, (2) AndroidManifest.xml ada, (3) berkas utama ada,
 * (4) layout activity_main.xml ada, (5) strings.xml ada,
 * (6) ikon tidak 1x1 transparan, (7) nama paket valid,
 * (8) versi lengkap, (9) izin yang dipakai dikenal,
 * (10) sintaks berkas utama seimbang (kurung/tanda).
 *
 * Semua pemeriksaan membaca berkas proyek saja (tanpa membangun APK),
 * jadi hasilnya cepat. Laporan per item LULUS/GAGAL + pesan — dibacakan
 * ke Jieshuo oleh ProjectsActivity.
 *
 * Murni Java (java.io/java.util/regex, tanpa API Android) supaya bisa
 * diuji ulang di JRE.
 */
public class UjiOtomatis {

    /** Satu hasil pemeriksaan. */
    public static class Hasil {
        /** Nama pemeriksaan. */
        public final String nama;
        /** true bila lulus. */
        public final boolean lulus;
        /** Penjelasan singkat. */
        public final String pesan;

        public Hasil(String nama, boolean lulus, String pesan) {
            this.nama = nama;
            this.lulus = lulus;
            this.pesan = pesan;
        }
    }

    /** Pola nama paket yang sama seperti validasi di CreateProjectActivity. */
    public static final Pattern POLA_PAKET = Pattern.compile(
            "^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*[a-z0-9_.]*$");

    /** Pola nama izin Android yang dikenal. */
    public static final Pattern POLA_IZIN = Pattern.compile(
            "^android\\.permission\\.[A-Z_]+$");

    /** Jumlah pemeriksaan yang dijalankan uji(). */
    public static final int JUMLAH_UJI = 10;

    /**
     * Menjalankan semua pemeriksaan pada satu proyek. Mengembalikan
     * daftar hasil (selalu 10 item, urutan tetap). Bila folder proyek
     * tidak ada, semua hasil GAGAL dengan pesan yang sama.
     */
    public static List<Hasil> uji(File dirProyek) {
        List<Hasil> hasil = new ArrayList<Hasil>();
        if (dirProyek == null || !dirProyek.isDirectory()) {
            for (int i = 0; i < JUMLAH_UJI; i++) {
                hasil.add(new Hasil(namaUji(i), false, TeksUI.t("Folder proyek tidak ditemukan.")));
            }
            return hasil;
        }
        String tipe = bacaKunci(new File(dirProyek, "init.lua"), "type");
        if (tipe == null || tipe.length() == 0) tipe = "lua";
        boolean lua = "lua".equals(tipe);

        // 1) init.lua ada
        File initLua = new File(dirProyek, "init.lua");
        hasil.add(new Hasil(TeksUI.t("berkas data proyek ada"), initLua.isFile(),
                initLua.isFile() ? TeksUI.t("Berkas data proyek ditemukan.")
                        : TeksUI.t("Berkas data proyek tidak ada — proyek bukan proyek Create Aplikasi.")));

        // 2) AndroidManifest.xml ada
        File manifest = new File(dirProyek, "app/src/main/AndroidManifest.xml");
        hasil.add(new Hasil("AndroidManifest.xml ada", manifest.isFile(),
                manifest.isFile() ? "Manifest ditemukan."
                        : "app/src/main/AndroidManifest.xml tidak ada."));

        // 3) berkas utama ada
        File utama = berkasUtama(dirProyek, tipe);
        hasil.add(new Hasil(TeksUI.t("Berkas utama ada"), utama != null && utama.isFile(),
                utama != null && utama.isFile()
                        ? TeksUI.t("Berkas utama ditemukan: ") + namaRelatif(dirProyek, utama) + "."
                        : TeksUI.t("Berkas utama proyek tipe ") + tipe + TeksUI.t(" tidak ditemukan.")));

        // 4) layout activity_main.xml ada (bukan Lua)
        if (lua) {
            hasil.add(new Hasil("Layout activity_main.xml ada", true,
                    TeksUI.t("Proyek Lua tidak memakai layout XML (dilewati).")));
        } else {
            File layout = new File(dirProyek,
                    "app/src/main/res/layout/activity_main.xml");
            hasil.add(new Hasil("Layout activity_main.xml ada", layout.isFile(),
                    layout.isFile() ? "Layout ditemukan."
                            : "app/src/main/res/layout/activity_main.xml tidak ada."));
        }

        // 5) strings.xml ada (bukan Lua)
        if (lua) {
            hasil.add(new Hasil("strings.xml ada", true,
                    "Proyek Lua tidak memakai strings.xml (dilewati)."));
        } else {
            File strings = new File(dirProyek,
                    "app/src/main/res/values/strings.xml");
            hasil.add(new Hasil("strings.xml ada", strings.isFile(),
                    strings.isFile() ? "strings.xml ditemukan."
                            : "app/src/main/res/values/strings.xml tidak ada."));
        }

        // 6) ikon tidak 1x1 transparan
        File ikon = new File(dirProyek, "app/src/main/res/drawable/icon.png");
        hasil.add(ujiIkon(ikon));

        // 7) nama paket valid
        String paket = bacaKunci(initLua, "package");
        if (paket == null || paket.length() == 0) {
            paket = bacaAtributPaket(manifest);
        }
        boolean paketOk = paket != null && POLA_PAKET.matcher(paket).matches()
                && paket.indexOf("..") < 0;
        hasil.add(new Hasil(TeksUI.t("Nama paket valid"), paketOk,
                paketOk ? TeksUI.t("Nama paket \"") + paket + "\" valid."
                        : TeksUI.t("Nama paket \"") + (paket == null ? "" : paket)
                        + TeksUI.t("\" tidak valid (harus huruf kecil, ada titik, contoh: com.buat.aplikasiku).")));

        // 8) versi lengkap
        String versi = bacaKunci(initLua, "appver");
        String kode = bacaKunci(initLua, "versionCode");
        if (versi == null) versi = bacaKunci(initLua, "version");
        boolean kodeOk = false;
        if (kode != null) {
            try {
                long v = Long.parseLong(kode.trim());
                kodeOk = v >= 1 && v <= 2100000000L;
            } catch (NumberFormatException e) {
                kodeOk = false;
            }
        }
        boolean versiOk = versi != null && versi.trim().length() > 0 && kodeOk;
        hasil.add(new Hasil(TeksUI.t("Versi lengkap"), versiOk,
                versiOk ? TeksUI.t("Versi \"") + versi + TeksUI.t("\" kode ") + kode + "."
                        : TeksUI.t("Versi tidak lengkap (butuh nama versi dan kode versi angka 1-2100000000).")));

        // 9) izin dikenal
        hasil.add(ujiIzin(dirProyek, initLua, manifest, lua));

        // 10) sintaks berkas utama seimbang
        if (utama != null && utama.isFile()) {
            hasil.add(ujiSintaks(utama, lua));
        } else {
            hasil.add(new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), false,
                    TeksUI.t("Berkas utama tidak ada, sintaks tidak bisa diperiksa.")));
        }
        return hasil;
    }

    /** Nama pemeriksaan ke-i (0-9), dipakai juga bila folder tidak ada. */
    public static String namaUji(int i) {
        switch (i) {
            case 0: return TeksUI.t("berkas data proyek ada");
            case 1: return "AndroidManifest.xml ada";
            case 2: return TeksUI.t("Berkas utama ada");
            case 3: return "Layout activity_main.xml ada";
            case 4: return "strings.xml ada";
            case 5: return TeksUI.t("Ikon tidak 1x1 transparan");
            case 6: return TeksUI.t("Nama paket valid");
            case 7: return TeksUI.t("Versi lengkap");
            case 8: return TeksUI.t("Izin dikenal");
            case 9: return TeksUI.t("Sintaks berkas utama seimbang");
            default: return "Uji " + (i + 1);
        }
    }

    /** Jumlah hasil yang lulus. */
    public static int jumlahLulus(List<Hasil> hasil) {
        int n = 0;
        for (int i = 0; i < hasil.size(); i++) {
            if (hasil.get(i).lulus) n++;
        }
        return n;
    }

    /** Ringkasan satu baris, contoh: "8 dari 10 lulus". */
    public static String ringkasan(List<Hasil> hasil) {
        return jumlahLulus(hasil) + TeksUI.t(" dari ") + hasil.size() + " lulus";
    }

    /** Laporan lengkap (satu baris per pemeriksaan) untuk dibacakan/ditampilkan. */
    public static String laporan(List<Hasil> hasil) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hasil uji otomatis: ").append(ringkasan(hasil)).append(".\n");
        for (int i = 0; i < hasil.size(); i++) {
            Hasil h = hasil.get(i);
            sb.append(h.lulus ? "[LULUS] " : TeksUI.t("[GAGAL] ")).append(h.nama)
                    .append(" — ").append(h.pesan).append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ pemeriksaan

    /** Berkas utama proyek menurut tipenya (null bila tipenya tidak dikenal). */
    static File berkasUtama(File dirProyek, String tipe) {
        if ("lua".equals(tipe)) {
            return new File(dirProyek, "main.lua");
        }
        if ("java".equals(tipe) || "kotlin".equals(tipe) || "compose".equals(tipe)) {
            String paket = bacaKunci(new File(dirProyek, "init.lua"), "package");
            String akhir = "java".equals(tipe) ? ".java" : ".kt";
            if (paket != null && paket.length() > 0) {
                File f = new File(dirProyek, "app/src/main/java/"
                        + paket.replace('.', '/') + "/MainActivity" + akhir);
                if (f.isFile()) return f;
            }
            // Cari MainActivity di mana saja di bawah java/ (bila paket di
            // init.lua tidak cocok dengan folder sebenarnya).
            File javaDir = new File(dirProyek, "app/src/main/java");
            File ketemu = cariNama(javaDir, "MainActivity" + akhir);
            if (ketemu != null) return ketemu;
            return new File(dirProyek, "app/src/main/java/MainActivity" + akhir);
        }
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            File html = new File(dirProyek, "app/src/main/assets/index.html");
            if (html.isFile()) return html;
            return new File(dirProyek, "app/src/main/java/MainActivity.java");
        }
        if ("python".equals(tipe)) {
            File py = new File(dirProyek, "app/src/main/python/main.py");
            if (py.isFile()) return py;
            return new File(dirProyek, "app/src/main/java/MainActivity.java");
        }
        return null;
    }

    /** Pemeriksaan ikon: ada, PNG, dan bukan 1x1 (ikon bawaan transparan). */
    static Hasil ujiIkon(File ikon) {
        if (ikon == null || !ikon.isFile()) {
            return new Hasil(TeksUI.t("Ikon tidak 1x1 transparan"), false,
                    "app/src/main/res/drawable/icon.png tidak ada.");
        }
        int[] ukuran = bacaUkuranPng(ikon);
        if (ukuran == null) {
            return new Hasil(TeksUI.t("Ikon tidak 1x1 transparan"), false,
                    TeksUI.t("icon.png bukan berkas PNG yang valid."));
        }
        if (ukuran[0] <= 1 && ukuran[1] <= 1) {
            return new Hasil(TeksUI.t("Ikon tidak 1x1 transparan"), false,
                    TeksUI.t("Ikon masih 1x1 transparan bawaan — buat ikon di Pembuat Ikon."));
        }
        return new Hasil(TeksUI.t("Ikon tidak 1x1 transparan"), true,
                "Ikon " + ukuran[0] + "x" + ukuran[1] + " piksel.");
    }

    /** Pemeriksaan izin: semua izin yang dipakai harus bernama android.permission.*. */
    static Hasil ujiIzin(File dirProyek, File initLua, File manifest, boolean lua) {
        List<String> izin = new ArrayList<String>();
        if (lua) {
            String isi = bacaBerkas(initLua);
            if (isi != null) {
                // Buang komentar dulu (pelajaran 3C: kata "true" di komentar
                // pernah membuat proyek tanpa lisensi terbaca berlisensi).
                String bersih = isi.replaceAll("--[^\\n]*", "");
                Matcher m = Pattern.compile("\"(android\\.permission\\.[A-Za-z_0-9]+)\"")
                        .matcher(bersih);
                while (m.find()) izin.add(m.group(1));
                // Izin yang ditulis tanpa awalan android.permission juga ditangkap.
                Matcher m2 = Pattern.compile("user_permission\\s*=\\s*\\{([^}]*)\\}")
                        .matcher(bersih);
                if (m2.find()) {
                    Matcher m3 = Pattern.compile("\"([^\"]+)\"").matcher(m2.group(1));
                    while (m3.find()) {
                        String v = m3.group(1);
                        if (v.length() > 0 && !izin.contains(v)) izin.add(v);
                    }
                }
            }
        } else {
            String isi = bacaBerkas(manifest);
            if (isi != null) {
                Matcher m = Pattern.compile(
                        "uses-permission[^>]*android:name=\"([^\"]+)\"").matcher(isi);
                while (m.find()) izin.add(m.group(1));
            }
        }
        for (int i = 0; i < izin.size(); i++) {
            if (!POLA_IZIN.matcher(izin.get(i)).matches()) {
                return new Hasil(TeksUI.t("Izin dikenal"), false,
                        TeksUI.t("Izin tidak dikenal: \"") + izin.get(i)
                                + "\" (harus android.permission.NAMA_IZIN).");
            }
        }
        return new Hasil(TeksUI.t("Izin dikenal"), true,
                izin.isEmpty() ? TeksUI.t("Tidak ada izin tambahan yang dipakai.")
                        : izin.size() + TeksUI.t(" izin dipakai, semuanya dikenal."));
    }

    /** Pemeriksaan sintaks: kurung (), {}, [] dan tanda kutip harus seimbang. */
    static Hasil ujiSintaks(File utama, boolean lua) {
        String isi = bacaBerkas(utama);
        if (isi == null) {
            return new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), false,
                    TeksUI.t("Berkas utama tidak bisa dibaca."));
        }
        String bersih = buangStringDanKomentar(isi, lua);
        int kurung = 0, kurawal = 0, siku = 0;
        for (int i = 0; i < bersih.length(); i++) {
            char c = bersih.charAt(i);
            if (c == '(') kurung++;
            else if (c == ')') kurung--;
            else if (c == '{') kurawal++;
            else if (c == '}') kurawal--;
            else if (c == '[') siku++;
            else if (c == ']') siku--;
            if (kurung < 0 || kurawal < 0 || siku < 0) {
                return new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), false,
                        TeksUI.t("Ada kurung tutup tanpa pasangan di ") + utama.getName() + ".");
            }
        }
        if (kurung != 0) {
            return new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), false,
                    TeksUI.t("Kurung () tidak seimbang (selisih ") + kurung + ").");
        }
        if (kurawal != 0) {
            return new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), false,
                    "Kurung {} tidak seimbang (selisih " + kurawal + ").");
        }
        if (siku != 0) {
            return new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), false,
                    TeksUI.t("Kurung [] tidak seimbang (selisih ") + siku + ").");
        }
        return new Hasil(TeksUI.t("Sintaks berkas utama seimbang"), true,
                "Kurung (), {}, [] seimbang di " + utama.getName() + ".");
    }

    // ------------------------------------------------------------ pembantu

    /** Ukuran PNG dari chunk IHDR (lebar, tinggi), atau null bila bukan PNG. */
    static int[] bacaUkuranPng(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] kepala = new byte[24];
                int n = in.read(kepala);
                if (n < 24) return null;
                if (kepala[0] != (byte) 0x89 || kepala[1] != (byte) 0x50
                        || kepala[2] != (byte) 0x4E || kepala[3] != (byte) 0x47) {
                    return null;
                }
                if (kepala[12] != (byte) 'I' || kepala[13] != (byte) 'H'
                        || kepala[14] != (byte) 'D' || kepala[15] != (byte) 'R') {
                    return null;
                }
                int lebar = ((kepala[16] & 0xFF) << 24) | ((kepala[17] & 0xFF) << 16)
                        | ((kepala[18] & 0xFF) << 8) | (kepala[19] & 0xFF);
                int tinggi = ((kepala[20] & 0xFF) << 24) | ((kepala[21] & 0xFF) << 16)
                        | ((kepala[22] & 0xFF) << 8) | (kepala[23] & 0xFF);
                return new int[]{lebar, tinggi};
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Membuang string literal dan komentar dari kode, supaya hitungan
     * kurung tidak terkecoh isi string. Lua: komentar -- sampai akhir
     * baris; Java/Kotlin: // dan blok / * ... * /.
     */
    static String buangStringDanKomentar(String isi, boolean lua) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < isi.length()) {
            char c = isi.charAt(i);
            if (c == '"' || c == '\'') {
                char kutip = c;
                i++;
                while (i < isi.length() && isi.charAt(i) != kutip) {
                    if (isi.charAt(i) == '\\') i++;
                    i++;
                }
                if (i < isi.length()) i++;
                sb.append(' ');
            } else if (lua && c == '-' && i + 1 < isi.length() && isi.charAt(i + 1) == '-') {
                while (i < isi.length() && isi.charAt(i) != '\n') i++;
            } else if (!lua && c == '/' && i + 1 < isi.length() && isi.charAt(i + 1) == '/') {
                while (i < isi.length() && isi.charAt(i) != '\n') i++;
            } else if (!lua && c == '/' && i + 1 < isi.length() && isi.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < isi.length()
                        && !(isi.charAt(i) == '*' && isi.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** Membaca kunci string dari init.lua (buang komentar Lua dulu). */
    static String bacaKunci(File initLua, String kunci) {
        if (initLua == null || !initLua.isFile()) return null;
        String isi = bacaBerkas(initLua);
        if (isi == null) return null;
        StringBuilder bersih = new StringBuilder();
        for (String baris : isi.split("\n")) {
            int p = baris.indexOf("--");
            if (p >= 0) baris = baris.substring(0, p);
            bersih.append(baris).append("\n");
        }
        Matcher m = Pattern.compile("\\b" + Pattern.quote(kunci)
                + "\\s*=\\s*\"([^\"]*)\"").matcher(bersih.toString());
        if (m.find()) return m.group(1);
        Matcher m2 = Pattern.compile("\\b" + Pattern.quote(kunci)
                + "\\s*=\\s*([0-9]+)").matcher(bersih.toString());
        if (m2.find()) return m2.group(1);
        return null;
    }

    /** Membaca atribut package="..." dari AndroidManifest.xml. */
    static String bacaAtributPaket(File manifest) {
        String isi = bacaBerkas(manifest);
        if (isi == null) return null;
        Matcher m = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(isi);
        return m.find() ? m.group(1) : null;
    }

    private static File cariNama(File dir, String nama) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] daftar = dir.listFiles();
        if (daftar == null) return null;
        for (int i = 0; i < daftar.length; i++) {
            if (daftar[i].isDirectory()) {
                File ketemu = cariNama(daftar[i], nama);
                if (ketemu != null) return ketemu;
            } else if (nama.equals(daftar[i].getName())) {
                return daftar[i];
            }
        }
        return null;
    }

    private static String namaRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    static String bacaBerkas(File f) {
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
        } catch (Exception e) {
            return null;
        }
    }
}
