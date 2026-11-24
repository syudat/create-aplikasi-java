package com.androlua;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Katalog 50 bahasa aplikasi (perintah al kautsar 2026-11-23) — logika
 * murni tanpa Android, bisa diuji di JVM.
 *
 * Pilihan bahasa disimpan di Pengaturan (kunci "bahasa"); nilai khusus
 * SISTEM berarti mengikuti bahasa sistem Android. Bahasa efektif
 * dihitung di sini: pilihan pengguna bila sah, kalau tidak bahasa
 * sistem bila ada di katalog, terakhir Indonesia sebagai bahasa asal
 * aplikasi ini.
 */
public class Bahasa {

    /** Nilai pilihan "ikuti sistem". */
    public static final String SISTEM = "sistem";

    public static class Entri {
        public final String kode;
        public final String nama;
        public final String namaAsli;

        Entri(String kode, String nama, String namaAsli) {
            this.kode = kode;
            this.nama = nama;
            this.namaAsli = namaAsli;
        }

        /** Label satu baris untuk daftar: "Spanyol — Español". */
        public String label() {
            return nama + " — " + namaAsli;
        }
    }

    private static final String[][] DATA = {
            {"id", "Indonesia", "Bahasa Indonesia"},
            {"en", "Inggris", "English"},
            {"es", "Spanyol", "Español"},
            {"ar", "Arab", "العربية"},
            {"zh", "Mandarin", "中文"},
            {"hi", "Hindi", "हिन्दी"},
            {"pt", "Portugis", "Português"},
            {"ru", "Rusia", "Русский"},
            {"ja", "Jepang", "日本語"},
            {"de", "Jerman", "Deutsch"},
            {"fr", "Prancis", "Français"},
            {"ko", "Korea", "한국어"},
            {"it", "Italia", "Italiano"},
            {"tr", "Turki", "Türkçe"},
            {"vi", "Vietnam", "Tiếng Việt"},
            {"th", "Thailand", "ไทย"},
            {"pl", "Polandia", "Polski"},
            {"uk", "Ukraina", "Українська"},
            {"nl", "Belanda", "Nederlands"},
            {"fa", "Persia", "فارسی"},
            {"ur", "Urdu", "اردو"},
            {"bn", "Bengali", "বাংলা"},
            {"ta", "Tamil", "தமிழ்"},
            {"te", "Telugu", "తెలుగు"},
            {"mr", "Marathi", "मराठी"},
            {"jv", "Jawa", "Basa Jawa"},
            {"su", "Sunda", "Basa Sunda"},
            {"ms", "Melayu", "Bahasa Melayu"},
            {"fil", "Filipina", "Filipino"},
            {"sv", "Swedia", "Svenska"},
            {"no", "Norwegia", "Norsk"},
            {"da", "Denmark", "Dansk"},
            {"fi", "Finlandia", "Suomi"},
            {"el", "Yunani", "Ελληνικά"},
            {"cs", "Ceko", "Čeština"},
            {"ro", "Rumania", "Română"},
            {"hu", "Hungaria", "Magyar"},
            {"he", "Ibrani", "עברית"},
            {"sw", "Swahili", "Kiswahili"},
            {"am", "Amhara", "አማርኛ"},
            {"ha", "Hausa", "Hausa"},
            {"kk", "Kazakh", "Қазақша"},
            {"uz", "Uzbek", "O'zbek"},
            {"az", "Azerbaijan", "Azərbaycan"},
            {"my", "Myanmar", "မြန်မာ"},
            {"km", "Khmer", "ខ្មែរ"},
            {"lo", "Laos", "ລາວ"},
            {"ne", "Nepal", "नेपाली"},
            {"si", "Sinhala", "සිංහල"},
            {"mn", "Mongolia", "Монгол"},
    };

    private static final List<Entri> SEMUA = new ArrayList<Entri>();
    static {
        for (String[] d : DATA) SEMUA.add(new Entri(d[0], d[1], d[2]));
    }

    /** Semua 50 bahasa, urutan tetap. */
    public static List<Entri> semua() {
        return Collections.unmodifiableList(SEMUA);
    }

    /** Entri dari kode bahasa ("es", atau "es-ES" -> "es"); null bila tidak ada. */
    public static Entri dariKode(String kode) {
        if (kode == null) return null;
        String k = kode.trim().toLowerCase();
        // Android memakai kode lama "in" untuk bahasa Indonesia
        // (bukan "id"); samakan supaya label "Ikuti Sistem" benar.
        if ("in".equals(k)) k = "id";
        int strip = k.indexOf('-');
        if (strip > 0) k = k.substring(0, strip);
        int garis = k.indexOf('_');
        if (garis > 0) k = k.substring(0, garis);
        for (Entri e : SEMUA) if (e.kode.equals(k)) return e;
        return null;
    }

    /** Menyaring daftar bahasa dari kata pencarian (nama, nama asli, kode). */
    public static List<Entri> cari(String kunci) {
        if (kunci == null || kunci.trim().length() == 0) return semua();
        String k = kunci.trim().toLowerCase();
        List<Entri> hasil = new ArrayList<Entri>();
        for (Entri e : SEMUA) {
            if (e.kode.contains(k) || e.nama.toLowerCase().contains(k)
                    || e.namaAsli.toLowerCase().contains(k)) {
                hasil.add(e);
            }
        }
        return hasil;
    }

    /**
     * Bahasa yang benar-benar dipakai: pilihan pengguna bila ada di
     * katalog; untuk pilihan "sistem"/kosong, bahasa sistem bila ada di
     * katalog; selain itu Indonesia.
     */
    public static String kodeEfektif(String pilihan, String kodeSistem) {
        if (pilihan != null && !SISTEM.equals(pilihan)) {
            Entri e = dariKode(pilihan);
            if (e != null) return e.kode;
        }
        Entri s = dariKode(kodeSistem);
        if (s != null) return s.kode;
        return "id";
    }

    /** Label pilihan yang tampil di Pengaturan. */
    public static String labelPilihan(String pilihan, String kodeSistem) {
        if (pilihan == null || SISTEM.equals(pilihan)) {
            Entri s = dariKode(kodeSistem);
            return "Ikuti Sistem" + (s != null ? " (" + s.nama + ")" : "");
        }
        Entri e = dariKode(pilihan);
        return e != null ? e.label() : "Ikuti Sistem";
    }
}
