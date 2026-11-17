package com.androlua;

/**
 * Mode Anak (Tahap 3I).
 *
 * Logika murni-Java untuk mode IDE yang disederhanakan untuk anak:
 * hanya aksi AMAN yang ditampilkan (buat proyek dari template aman,
 * buka proyek, jalankan, belajar) — tanpa hapus, tanpa ekspor, tanpa
 * cadangan, tanpa pengaturan token, tanpa pengaturan GitHub.
 *
 * Keluar dari Mode Anak memakai PIN 4 angka sederhana (disimpan di
 * SharedPreferences oleh ModeAnakActivity, bukan di kelas ini — kelas
 * ini hanya memvalidasi bentuk PIN dan mencocokkannya). PIN ini
 * pengaman ringan untuk anak kecil, BUKAN keamanan sungguhan: siapa
 * pun yang bisa membuka pengaturan aplikasi bisa menghapus datanya.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JRE biasa.
 */
public class ModeAnak {

    /** Nama SharedPreferences untuk Mode Anak. */
    public static final String NAMA_PREF = "mode_anak";

    /** Kunci PIN di SharedPreferences. */
    public static final String KUNCI_PIN = "pin";

    /** Aksi aman yang boleh tampil di Mode Anak (hanya 4). */
    public static final String[] AKSI_AMAN = {
        "Buat Proyek", "Buka Proyek", "Jalankan", "Belajar"
    };

    /** Satu template aman untuk anak: id, nama tampil, sumber. */
    public static class TemplateAman {
        public final String id;
        public final String nama;
        /** "template" = TemplateProyek, "game" = GameTanpaKode. */
        public final String sumber;
        public TemplateAman(String id, String nama, String sumber) {
            this.id = id;
            this.nama = nama;
            this.sumber = sumber;
        }
    }

    /** Template yang aman untuk anak (kalkulator + 3 game). */
    public static final TemplateAman[] TEMPLATE_AMAN = {
        new TemplateAman("kalkulator", "Kalkulator", "template"),
        new TemplateAman("tebak-angka", "Tebak Angka", "game"),
        new TemplateAman("kuis", "Kuis", "game"),
        new TemplateAman("tangkap-bintang", "Tangkap Bintang", "game"),
    };

    /** Cari template aman berdasarkan id; null bila tidak ada. */
    public static TemplateAman cariTemplate(String id) {
        if (id == null) return null;
        for (int i = 0; i < TEMPLATE_AMAN.length; i++) {
            if (TEMPLATE_AMAN[i].id.equals(id)) return TEMPLATE_AMAN[i];
        }
        return null;
    }

    /** True bila PIN berupa tepat 4 angka (0-9). */
    public static boolean pinValid(String pin) {
        if (pin == null || pin.length() != 4) return false;
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** True bila PIN yang dimasukkan sama dengan PIN tersimpan. */
    public static boolean pinCocok(String tersimpan, String masuk) {
        if (tersimpan == null || masuk == null) return false;
        return tersimpan.equals(masuk);
    }

    /** True bila aksi boleh tampil di Mode Anak. */
    public static boolean aksiAman(String aksi) {
        if (aksi == null) return false;
        for (int i = 0; i < AKSI_AMAN.length; i++) {
            if (AKSI_AMAN[i].equals(aksi)) return true;
        }
        return false;
    }
}
