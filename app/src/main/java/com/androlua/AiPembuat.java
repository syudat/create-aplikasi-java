package com.androlua;

import java.util.Locale;

/**
 * "AI" Pembuat Aplikasi (Tahap 3D) — penganalisis deskripsi lokal.
 *
 * Bukan API berbayar dan bukan internet: deskripsi pengguna dicocokkan
 * dengan pola kata kunci (tanpa memperhatikan huruf besar/kecil) lalu
 * dipetakan ke template siap pakai yang sudah ada (TemplateProyek).
 * Bila tidak ada pola yang cocok, dibuat proyek kosong dan dijelaskan
 * dengan jujur — tidak pernah pura-pura mengerti.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji ulang di JRE.
 * AiActivity memakai hasil analisis untuk benar-benar membuat proyek
 * lewat jembatan buatProyek + TemplateProyek.terapkan().
 */
public class AiPembuat {

    /** Hasil analisis satu deskripsi. */
    public static class Hasil {
        /** Id template (atau "kosong" bila tidak ada pola yang cocok). */
        public final String templateId;
        /** Nama proyek yang disarankan. */
        public final String namaProyek;
        /** Jenis proyek template ("java", "kotlin", "html", "situs", "compose", "python"). */
        public final String tipe;
        /** Penjelasan untuk pengguna (dibacakan lewat TTS di AiActivity). */
        public final String penjelasan;
        /** true bila tidak ada pola yang cocok (proyek kosong). */
        public final boolean tanpaTemplate;

        public Hasil(String templateId, String namaProyek, String tipe,
                     String penjelasan, boolean tanpaTemplate) {
            this.templateId = templateId;
            this.namaProyek = namaProyek;
            this.tipe = tipe;
            this.penjelasan = penjelasan;
            this.tanpaTemplate = tanpaTemplate;
        }
    }

    /** Contoh deskripsi yang ditampilkan di layar (semuanya berfungsi). */
    public static final String[] CONTOH = {
        "aplikasi kalkulator sederhana",
        "aplikasi catatan dengan tombol simpan",
        "daftar tugas harian",
        "kalkulator web",
        "situs profil saya",
        "penghitung angka",
        "aplikasi hitung belanja",
        "jurnal harian saya",
    };

    /** Satu pola: {frasa kunci, id template}. Urutan = prioritas. */
    private static final String[][] POLA = {
        {"kalkulator web", "kalkulator-web"},
        {TeksUI.t("kalkulator di web"), "kalkulator-web"},
        {TeksUI.t("hitung di web"), "kalkulator-web"},
        {"kalkulator", "kalkulator"},
        {"hitung", "kalkulator"},
        {"tambah", "kalkulator"},
        {"kurang", "kalkulator"},
        {"kali", "kalkulator"},
        {"bagi", "kalkulator"},
        {"aritmetika", "kalkulator"},
        {"catatan", "catatan"},
        {"note", "catatan"},
        {"memo", "catatan"},
        {"jurnal", "catatan"},
        {"diari", "catatan"},
        {"tulis", "catatan"},
        {"simpan", "catatan"},
        {TeksUI.t("daftar tugas"), "tugas"},
        {"tugas", "tugas"},
        {"todo", "tugas"},
        {"to do", "tugas"},
        {"jadwal", "tugas"},
        {"pengingat", "tugas"},
        {TeksUI.t("daftar belanja"), "tugas"},
        {"belanja", "tugas"},
        {"penghitung", "penghitung"},
        {"counter", "penghitung"},
        {"hitung mundur", "penghitung"},
        {"profil", "profil"},
        {"portofolio", "profil"},
        {"biodata", "profil"},
        {TeksUI.t("tentang saya"), "profil"},
        {"situs", "profil"},
        {"website", "profil"},
        {"halaman web", "profil"},
        {"web", "profil"},
    };

    /** Kata yang dibuang saat mengambil nama proyek dari deskripsi. */
    private static final String[] KATA_BUANG = {
        "aplikasi", "aplikasi yang", "saya mau", "saya ingin", "buatkan",
        "tolong buatkan", "buatin", "bikin", "buat", "dengan", "tombol",
        "sederhana", "yang", "untuk", "dan", "saya", "aku", "mau", "ingin",
        "di", "ke", "dari", "sebuah", "satu", "punya", "ada",
    };

    /**
     * Menganalisis deskripsi. Tidak pernah mengembalikan null; bila tidak
     * ada pola yang cocok, templateId = "kosong" dan penjelasannya jujur.
     */
    public static Hasil analisis(String deskripsi) {
        String asli = deskripsi == null ? "" : deskripsi;
        String t = " " + normalisasi(asli) + " ";
        String templateId = null;
        for (int i = 0; i < POLA.length; i++) {
            if (t.indexOf(" " + POLA[i][0] + " ") >= 0) {
                templateId = POLA[i][1];
                break;
            }
        }
        if (templateId == null) {
            String nama = ambilNama(asli, null);
            if (nama.isEmpty()) nama = TeksUI.t("Aplikasi Saya");
            return new Hasil("kosong", nama, "java",
                    TeksUI.t("Saya belum punya template untuk \"") + asli.trim()
                    + TeksUI.t("\", jadi saya buatkan proyek kosong. Kamu bisa mengisi ")
                    + TeksUI.t("kodenya sendiri di editor."),
                    true);
        }
        TemplateProyek.Info info = TemplateProyek.cari(templateId);
        String nama = ambilNama(asli, templateId);
        if (nama.isEmpty()) {
            nama = info != null ? info.nama : TeksUI.t("Aplikasi Saya");
        }
        String tipe = info != null && info.tipe != null ? info.tipe : "java";
        String penjelasan = TeksUI.t("Saya buatkan proyek ")
                + (info != null ? info.nama : templateId)
                + " (" + labelTipe(tipe) + TeksUI.t(") dari deskripsimu. Kode awalnya ")
                + TeksUI.t("sudah jadi — tinggal dijalankan atau diubah di editor.");
        return new Hasil(templateId, nama, tipe, penjelasan, false);
    }

    /** Normalisasi: huruf kecil, spasi tunggal. */
    static String normalisasi(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(new Locale("id", "ID")).trim();
        t = t.replaceAll("[\\s]+", " ");
        t = t.replaceAll("[.,!?]+$", "");
        return t;
    }

    /**
     * Mengambil nama proyek dari deskripsi: buang kata kunci template yang
     * cocok + kata umum, sisanya (maks. 3 kata) dijadikan nama.
     */
    private static String ambilNama(String deskripsi, String templateId) {
        String t = " " + normalisasi(deskripsi) + " ";
        // Buang frasa kunci template yang cocok.
        if (templateId != null) {
            for (int i = 0; i < POLA.length; i++) {
                if (templateId.equals(POLA[i][1])) {
                    t = t.replace(" " + POLA[i][0] + " ", " ");
                }
            }
        }
        for (int i = 0; i < KATA_BUANG.length; i++) {
            t = t.replace(" " + KATA_BUANG[i] + " ", " ");
        }
        // Buang kata kunci template lain yang tersisa (mis. "web" di
        // "kalkulator web" bila yang cocok "kalkulator").
        for (int i = 0; i < POLA.length; i++) {
            t = t.replace(" " + POLA[i][0] + " ", " ");
        }
        String[] kata = t.trim().split(" ");
        StringBuilder sb = new StringBuilder();
        int ambil = 0;
        for (int i = 0; i < kata.length && ambil < 3; i++) {
            if (kata[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(kata[i].charAt(0)));
            if (kata[i].length() > 1) sb.append(kata[i].substring(1));
            ambil++;
        }
        return sb.toString();
    }

    private static String labelTipe(String tipe) {
        if ("html".equals(tipe)) return "HTML";
        if ("situs".equals(tipe)) return "Situs Web";
        if ("java".equals(tipe)) return "Java";
        if ("kotlin".equals(tipe)) return "Kotlin";
        if ("compose".equals(tipe)) return "Compose";
        if ("python".equals(tipe)) return "Python";
        return tipe;
    }
}
