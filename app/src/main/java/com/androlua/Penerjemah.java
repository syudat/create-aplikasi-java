package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Penerjemah Aplikasi Otomatis (Tahap 3E).
 *
 * Membaca semua teks yang tampil di sebuah proyek (strings.xml, teks di
 * layout, teks di kode Lua/Java/Kotlin) lalu menerjemahkannya ke bahasa
 * target memakai kamus bawaan lokal — TANPA internet, TANPA biaya.
 * Kamus berisi kata-kata umum antarmuka aplikasi (Indonesia -> Inggris /
 * Arab / Jawa). Kata yang tidak ada di kamus dibiarkan apa adanya dan
 * dilaporkan dengan jujur (tidak pura-pura diterjemahkan).
 *
 * Hasil: values-&lt;bahasa&gt;/strings.xml baru (bila proyek punya
 * strings.xml), tabel Lua terjemahan-&lt;bahasa&gt;.lua (bila proyek Lua),
 * dan laporan terjemahan-&lt;bahasa&gt;.txt di akar proyek.
 *
 * Murni Java (java.io/java.util/regex, tanpa API Android) supaya bisa
 * diuji ulang di JRE.
 */
public class Penerjemah {

    /** Kode bahasa yang didukung. */
    public static final String[] BAHASA_KODE = {"en", "ar", "jv"};
    /** Nama tampil bahasa. */
    public static final String[] BAHASA_NAMA = {"Inggris", "Arab", "Jawa"};
    /** Folder values untuk tiap bahasa. */
    public static final String[] BAHASA_FOLDER = {"values-en", "values-ar", "values-jv"};

    /**
     * Kamus kata umum antarmuka aplikasi: {Indonesia, Inggris, Arab, Jawa}.
     * Kata Indonesia ditulis huruf kecil; pencarian tidak memperhatikan
     * huruf besar/kecil.
     */
    private static final String[][] KAMUS = {
        {"simpan", "save", "حفظ", "simpen"},
        {"batal", "cancel", "إلغاء", "batal"},
        {"buka", "open", "فتح", "bukak"},
        {"tutup", "close", "إغلاق", "tutup"},
        {"hapus", "delete", "حذف", "busak"},
        {"tambah", "add", "إضافة", "tambah"},
        {"ubah", "edit", "تعديل", "owah"},
        {"cari", "search", "بحث", "golek"},
        {"kembali", "back", "رجوع", "bali"},
        {"lanjut", "continue", "متابعة", "terus"},
        {"ya", "yes", "نعم", "yo"},
        {"tidak", "no", "لا", "ora"},
        {"masuk", "login", "دخول", "mlebu"},
        {"keluar", "exit", "خروج", "metu"},
        {"daftar", "list", "قائمة", "dhaptar"},
        {"beranda", "home", "الرئيسية", "ngarep"},
        {"pengaturan", "settings", "إعدادات", "setelan"},
        {"tentang", "about", "حول", "bab"},
        {"bantuan", "help", "مساعدة", "pitulungan"},
        {"profil", "profile", "الملف الشخصي", "profil"},
        {"nama", "name", "اسم", "jeneng"},
        {"kata sandi", "password", "كلمة المرور", "tembung sandhi"},
        {"email", "email", "بريد إلكتروني", "email"},
        {"telepon", "phone", "هاتف", "telpon"},
        {"alamat", "address", "عنوان", "alamat"},
        {"tanggal", "date", "تاريخ", "tanggal"},
        {"jam", "clock", "ساعة", "jam"},
        {"hari", "day", "يوم", "dina"},
        {"minggu", "week", "أسبوع", "minggu"},
        {"bulan", "month", "شهر", "wulan"},
        {"tahun", "year", "سنة", "taun"},
        {"sukses", "success", "نجاح", "kasil"},
        {"gagal", "failed", "فشل", "gagal"},
        {"kesalahan", "error", "خطأ", "kaluputan"},
        {"peringatan", "warning", "تحذير", "elingan"},
        {"informasi", "information", "معلومات", "informasi"},
        {"konfirmasi", "confirm", "تأكيد", "konfirmasi"},
        {"setuju", "agree", "موافق", "setuju"},
        {"tolak", "decline", "رفض", "tulak"},
        {"kirim", "send", "إرسال", "kirim"},
        {"terima", "receive", "استلام", "tampa"},
        {"unduh", "download", "تنزيل", "undhuh"},
        {"unggah", "upload", "رفع", "unggah"},
        {"bagikan", "share", "مشاركة", "bagi"},
        {"salin", "copy", "نسخ", "salin"},
        {"tempel", "paste", "لصق", "templek"},
        {"pilih", "select", "اختيار", "pilih"},
        {"semua", "all", "الكل", "kabeh"},
        {"baru", "new", "جديد", "anyar"},
        {"lama", "old", "قديم", "lawas"},
        {"besar", "big", "كبير", "gedhe"},
        {"kecil", "small", "صغير", "cilik"},
        {"mulai", "start", "ابدأ", "miwiti"},
        {"selesai", "done", "تم", "rampung"},
        {"berhenti", "stop", "توقف", "mandheg"},
        {"main", "play", "العب", "dolan"},
        {"skor", "score", "نقاط", "skor"},
        {"level", "level", "مستوى", "level"},
        {"menang", "win", "فوز", "menang"},
        {"kalah", "lose", "خسارة", "kalah"},
        {"permainan", "game", "لعبة", "dolanan"},
        {"aplikasi", "app", "تطبيق", "aplikasi"},
        {"proyek", "project", "مشروع", "proyek"},
        {"berkas", "file", "ملف", "berkas"},
        {"folder", "folder", "مجلد", "folder"},
        {"gambar", "picture", "صورة", "gambar"},
        {"suara", "sound", "صوت", "swara"},
        {"musik", "music", "موسيقى", "musik"},
        {"video", "video", "فيديو", "video"},
        {"teks", "text", "نص", "teks"},
        {"tombol", "button", "زر", "tombol"},
        {"menu", "menu", "قائمة", "menu"},
        {"layar", "screen", "شاشة", "layar"},
        {"bahasa", "language", "لغة", "basa"},
        {"benar", "correct", "صحيح", "bener"},
        {"salah", "wrong", "خطأ", "salah"},
        {"mudah", "easy", "سهل", "gampang"},
        {"sulit", "difficult", "صعب", "angel"},
        {"selamat", "congratulations", "مبروك", "sugeng"},
        {"pagi", "morning", "صباح", "esuk"},
        {"malam", "night", "مساء", "bengi"},
        {"siang", "afternoon", "ظهر", "awan"},
    };

    /**
     * Frasa (lebih dari satu kata) — diperiksa SEBELUM kata per kata,
     * supaya "selamat datang" tidak diterjemahkan kata demi kata.
     * {Indonesia, Inggris, Arab, Jawa}.
     */
    private static final String[][] FRASA = {
        {"selamat datang", "welcome", "أهلا وسهلا", "sugeng rawuh"},
        {"terima kasih", "thank you", "شكرا", "matur nuwun"},
        {"kata sandi", "password", "كلمة المرور", "tembung sandhi"},
        {"belum ada", "not yet", "لا يوجد بعد", "durung ana"},
        {"selamat pagi", "good morning", "صباح الخير", "sugeng enjang"},
        {"selamat malam", "good night", "مساء الخير", "sugeng dalu"},
    };

    /** Jumlah entri kamus (untuk uji: minimal 50 kata). */
    public static int jumlahKata() {
        return KAMUS.length;
    }

    /** Kolom terjemahan untuk kode bahasa (en=1, ar=2, jv=3), -1 bila tidak dikenal. */
    public static int kolomBahasa(String bahasa) {
        if (bahasa == null) return -1;
        for (int i = 0; i < BAHASA_KODE.length; i++) {
            if (BAHASA_KODE[i].equals(bahasa)) return i + 1;
        }
        return -1;
    }

    /** Nama tampil bahasa dari kodenya, atau kode itu sendiri bila tidak dikenal. */
    public static String namaBahasa(String bahasa) {
        int k = kolomBahasa(bahasa);
        return k < 0 ? bahasa : BAHASA_NAMA[k - 1];
    }

    /** Terjemahan satu kata (huruf kecil), atau null bila tidak ada di kamus. */
    public static String terjemahKata(String kata, int kolom) {
        if (kata == null || kolom < 1 || kolom > 3) return null;
        String cari = kata.toLowerCase(new java.util.Locale("id", "ID"));
        for (int i = 0; i < KAMUS.length; i++) {
            if (KAMUS[i][0].equals(cari)) return KAMUS[i][kolom];
        }
        return null;
    }

    /** Satu frasa utuh (huruf kecil), atau null bila tidak ada di tabel frasa. */
    public static String terjemahFrasa(String frasa, int kolom) {
        if (frasa == null || kolom < 1 || kolom > 3) return null;
        String cari = frasa.toLowerCase(new java.util.Locale("id", "ID")).trim();
        for (int i = 0; i < FRASA.length; i++) {
            if (FRASA[i][0].equals(cari)) return FRASA[i][kolom];
        }
        return null;
    }

    /** Hasil menerjemahkan satu teks. */
    public static class Hasil {
        /** Teks hasil terjemahan. */
        public final String teks;
        /** Jumlah kata yang berhasil diterjemahkan. */
        public final int kataDiterjemahkan;
        /** Jumlah kata yang belum diterjemahkan. */
        public final int kataBelum;
        /** Kata yang belum diterjemahkan (unik, urutan kemunculan). */
        public final List<String> belum;

        public Hasil(String teks, int kataDiterjemahkan, int kataBelum, List<String> belum) {
            this.teks = teks;
            this.kataDiterjemahkan = kataDiterjemahkan;
            this.kataBelum = kataBelum;
            this.belum = belum;
        }
    }

    /**
     * Menerjemahkan satu teks: frasa (2-3 kata) diperiksa dulu, lalu kata
     * per kata. Tanda baca di ujung kata dipertahankan; huruf besar di
     * awal kata dipertahankan (untuk aksara Latin). Kata yang tidak ada
     * di kamus dibiarkan apa adanya dan dicatat di hasil.belum.
     */
    public static Hasil terjemahkan(String teks, String bahasa) {
        int kolom = kolomBahasa(bahasa);
        List<String> belum = new ArrayList<String>();
        LinkedHashSet<String> belumUnik = new LinkedHashSet<String>();
        if (teks == null) teks = "";
        if (kolom < 0 || teks.trim().length() == 0) {
            return new Hasil(teks, 0, 0, belum);
        }
        String[] token = teks.trim().split("\\s+");
        // Uraikan tiap token jadi (awalan tanda baca, kata inti, akhiran tanda baca).
        String[] inti = new String[token.length];
        String[] awal = new String[token.length];
        String[] akhir = new String[token.length];
        for (int i = 0; i < token.length; i++) {
            int a = 0;
            int b = token[i].length();
            while (a < b && !Character.isLetterOrDigit(token[i].charAt(a))) a++;
            while (b > a && !Character.isLetterOrDigit(token[i].charAt(b - 1))) b--;
            awal[i] = token[i].substring(0, a);
            inti[i] = token[i].substring(a, b);
            akhir[i] = token[i].substring(b);
        }
        StringBuilder keluar = new StringBuilder();
        int diterjemahkan = 0;
        int belumHitung = 0;
        int i = 0;
        while (i < token.length) {
            String ganti = null;
            int lompat = 1;
            boolean kapital = inti[i].length() > 0
                    && Character.isUpperCase(inti[i].charAt(0));
            // Coba frasa 3 kata, lalu 2 kata.
            for (int n = 3; n >= 2; n--) {
                if (i + n > token.length) continue;
                StringBuilder gab = new StringBuilder();
                boolean kosong = false;
                for (int j = 0; j < n; j++) {
                    if (inti[i + j].length() == 0) { kosong = true; break; }
                    if (j > 0) gab.append(" ");
                    gab.append(inti[i + j].toLowerCase(new java.util.Locale("id", "ID")));
                }
                if (kosong) continue;
                String f = terjemahFrasa(gab.toString(), kolom);
                if (f != null) {
                    ganti = f;
                    lompat = n;
                    break;
                }
            }
            if (ganti == null) {
                String kata = inti[i];
                if (kata.length() == 0) {
                    ganti = "";
                } else {
                    String t = terjemahKata(kata, kolom);
                    if (t != null) {
                        ganti = t;
                        diterjemahkan++;
                    } else {
                        ganti = kata;
                        belumHitung++;
                        belumUnik.add(kata.toLowerCase(new java.util.Locale("id", "ID")));
                    }
                }
            } else {
                diterjemahkan += lompat;
            }
            if (kapital && ganti.length() > 0) {
                ganti = Character.toUpperCase(ganti.charAt(0)) + ganti.substring(1);
            }
            if (keluar.length() > 0) keluar.append(" ");
            keluar.append(awal[i]).append(ganti).append(akhir[i + lompat - 1]);
            i += lompat;
        }
        belum.addAll(belumUnik);
        return new Hasil(keluar.toString(), diterjemahkan, belumHitung, belum);
    }

    // ------------------------------------------------------------ ekstraksi

    /** Satu entri strings.xml: nama + nilai. */
    public static class EntriString {
        public final String nama;
        public final String nilai;
        public EntriString(String nama, String nilai) {
            this.nama = nama;
            this.nilai = nilai;
        }
    }

    /** Satu teks di kode: berkas, baris, dan isi literal. */
    public static class TeksKode {
        public final String berkas;
        public final int baris;
        public final String teks;
        public TeksKode(String berkas, int baris, String teks) {
            this.berkas = berkas;
            this.baris = baris;
            this.teks = teks;
        }
    }

    /** Membaca entri &lt;string&gt; dari strings.xml (lewati translatable="false"). */
    public static List<EntriString> bacaStringsXml(File f) {
        List<EntriString> hasil = new ArrayList<EntriString>();
        if (f == null || !f.isFile()) return hasil;
        String isi = bacaBerkas(f);
        if (isi == null) return hasil;
        Pattern p = Pattern.compile(
                "<string\\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", Pattern.DOTALL);
        Matcher m = p.matcher(isi);
        while (m.find()) {
            if (m.group(2) != null && m.group(2).contains("translatable=\"false\"")) continue;
            hasil.add(new EntriString(m.group(1), unescapeXml(m.group(3))));
        }
        return hasil;
    }

    /** Membangun isi strings.xml dari entri yang sudah diterjemahkan. */
    public static String tulisStringsXml(List<EntriString> entri) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<resources>\n");
        for (int i = 0; i < entri.size(); i++) {
            sb.append("    <string name=\"").append(entri.get(i).nama).append("\">")
                    .append(escapeXml(entri.get(i).nilai)).append("</string>\n");
        }
        sb.append("</resources>\n");
        return sb.toString();
    }

    /** Membaca nilai literal android:text / android:hint di berkas layout XML. */
    public static List<String> bacaTeksLayout(File f) {
        List<String> hasil = new ArrayList<String>();
        if (f == null || !f.isFile()) return hasil;
        String isi = bacaBerkas(f);
        if (isi == null) return hasil;
        Pattern p = Pattern.compile("android:(?:text|hint)=\"([^\"]+)\"");
        Matcher m = p.matcher(isi);
        while (m.find()) {
            String v = m.group(1);
            if (!v.startsWith("@")) hasil.add(v);
        }
        return hasil;
    }

    /**
     * Membaca literal string "..." di kode (.lua/.java/.kt) — untuk laporan
     * saja (tidak diubah otomatis). Literal yang hanya berisi simbol, jalur,
     * atau angka dilewati.
     */
    public static List<TeksKode> bacaTeksKode(File f, String namaRelatif) {
        List<TeksKode> hasil = new ArrayList<TeksKode>();
        if (f == null || !f.isFile()) return hasil;
        String isi = bacaBerkas(f);
        if (isi == null) return hasil;
        String[] baris = isi.split("\n", -1);
        Pattern p = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        for (int i = 0; i < baris.length; i++) {
            Matcher m = p.matcher(baris[i]);
            while (m.find()) {
                String v = unescapeJava(m.group(1));
                if (v.length() < 2) continue;
                if (v.indexOf('/') >= 0 || v.indexOf('.') >= 0 && v.indexOf(' ') < 0) continue;
                boolean adaHuruf = false;
                for (int j = 0; j < v.length(); j++) {
                    if (Character.isLetter(v.charAt(j))) { adaHuruf = true; break; }
                }
                if (!adaHuruf) continue;
                hasil.add(new TeksKode(namaRelatif, i + 1, v));
            }
        }
        return hasil;
    }

    // ------------------------------------------------------- proyek utuh

    /** Laporan menerjemahkan satu proyek. */
    public static class Laporan {
        /** Kode bahasa target. */
        public final String bahasa;
        /** Jumlah teks sumber yang diproses. */
        public final int jumlahTeks;
        /** Jumlah kata yang berhasil diterjemahkan. */
        public final int kataDiterjemahkan;
        /** Jumlah kata yang belum diterjemahkan. */
        public final int kataBelum;
        /** Kata yang belum diterjemahkan (unik). */
        public final List<String> belum;
        /** Berkas hasil yang ditulis (jalur absolut). */
        public final List<String> berkasDitulis;
        /** Teks laporan lengkap (ditulis juga ke terjemahan-&lt;bahasa&gt;.txt). */
        public final String laporan;

        public Laporan(String bahasa, int jumlahTeks, int kataDiterjemahkan,
                       int kataBelum, List<String> belum, List<String> berkasDitulis,
                       String laporan) {
            this.bahasa = bahasa;
            this.jumlahTeks = jumlahTeks;
            this.kataDiterjemahkan = kataDiterjemahkan;
            this.kataBelum = kataBelum;
            this.belum = belum;
            this.berkasDitulis = berkasDitulis;
            this.laporan = laporan;
        }
    }

    /**
     * Menerjemahkan seluruh proyek: strings.xml (bila ada) ditulis ke
     * values-&lt;bahasa&gt;/strings.xml; proyek Lua mendapat tabel
     * terjemahan-&lt;bahasa&gt;.lua; selalu ditulis laporan
     * terjemahan-&lt;bahasa&gt;.txt di akar proyek. Mengembalikan null
     * bila proyek tidak ditemukan / tidak ada teks sama sekali.
     */
    public static Laporan terjemahkanProyek(File dirProyek, String bahasa) {
        int kolom = kolomBahasa(bahasa);
        if (dirProyek == null || !dirProyek.isDirectory() || kolom < 0) return null;
        String tipe = bacaKunci(new File(dirProyek, "init.lua"), "type");
        if (tipe == null || tipe.length() == 0) tipe = "lua";
        int jumlahTeks = 0;
        int kataDiterjemahkan = 0;
        int kataBelum = 0;
        LinkedHashSet<String> belumUnik = new LinkedHashSet<String>();
        List<String> berkasDitulis = new ArrayList<String>();
        StringBuilder lap = new StringBuilder();
        lap.append("Laporan terjemahan — bahasa ").append(namaBahasa(bahasa))
                .append(" (").append(bahasa).append(")\n");
        lap.append("Proyek: ").append(dirProyek.getName()).append("\n\n");

        // 1) strings.xml
        File stringsXml = new File(dirProyek,
                "app/src/main/res/values/strings.xml");
        List<EntriString> entri = bacaStringsXml(stringsXml);
        if (!entri.isEmpty()) {
            List<EntriString> hasil = new ArrayList<EntriString>();
            for (int i = 0; i < entri.size(); i++) {
                Hasil h = terjemahkan(entri.get(i).nilai, bahasa);
                hasil.add(new EntriString(entri.get(i).nama, h.teks));
                jumlahTeks++;
                kataDiterjemahkan += h.kataDiterjemahkan;
                kataBelum += h.kataBelum;
                belumUnik.addAll(h.belum);
                lap.append("strings.xml / ").append(entri.get(i).nama).append(": \"")
                        .append(entri.get(i).nilai).append("\" -> \"").append(h.teks)
                        .append("\"\n");
            }
            File keluar = new File(dirProyek, "app/src/main/res/"
                    + BAHASA_FOLDER[kolom - 1] + "/strings.xml");
            String galat = tulisBerkas(keluar, tulisStringsXml(hasil));
            if (galat == null) berkasDitulis.add(keluar.getAbsolutePath());
            lap.append("\n");
        }

        // 2) layout XML
        List<File> layout = cariBerkas(new File(dirProyek, "app/src/main/res/layout"),
                ".xml");
        for (int i = 0; i < layout.size(); i++) {
            List<String> teks = bacaTeksLayout(layout.get(i));
            for (int j = 0; j < teks.size(); j++) {
                Hasil h = terjemahkan(teks.get(j), bahasa);
                jumlahTeks++;
                kataDiterjemahkan += h.kataDiterjemahkan;
                kataBelum += h.kataBelum;
                belumUnik.addAll(h.belum);
                lap.append("layout / ").append(namaRelatif(dirProyek, layout.get(i)))
                        .append(": \"").append(teks.get(j)).append("\" -> \"")
                        .append(h.teks).append("\"\n");
            }
        }
        if (!layout.isEmpty()) lap.append("\n");

        // 3) kode (laporan saja — tidak diubah otomatis)
        List<File> kode = new ArrayList<File>();
        kumpulKode(dirProyek, kode);
        int teksKode = 0;
        for (int i = 0; i < kode.size(); i++) {
            List<TeksKode> daftar = bacaTeksKode(kode.get(i), namaRelatif(dirProyek, kode.get(i)));
            for (int j = 0; j < daftar.size(); j++) {
                Hasil h = terjemahkan(daftar.get(j).teks, bahasa);
                jumlahTeks++;
                teksKode++;
                kataDiterjemahkan += h.kataDiterjemahkan;
                kataBelum += h.kataBelum;
                belumUnik.addAll(h.belum);
                lap.append("kode / ").append(daftar.get(j).berkas).append(":")
                        .append(daftar.get(j).baris).append(": \"")
                        .append(daftar.get(j).teks).append("\" -> \"").append(h.teks)
                        .append("\"\n");
            }
        }
        if (teksKode > 0) lap.append("\n");

        if (jumlahTeks == 0) return null;

        // 4) tabel Lua untuk proyek Lua
        if ("lua".equals(tipe)) {
            StringBuilder tb = new StringBuilder();
            tb.append("-- Tabel terjemahan (").append(namaBahasa(bahasa))
                    .append(") — dibuat otomatis oleh Penerjemah Aplikasi.\n");
            tb.append("-- Pakai: local T = require \"terjemahan-").append(bahasa)
                    .append("\"\n");
            tb.append("return {\n");
            // Kumpulkan pasangan unik dari strings.xml + kode.
            LinkedHashSet<String> kunci = new LinkedHashSet<String>();
            for (int i = 0; i < entri.size(); i++) {
                Hasil h = terjemahkan(entri.get(i).nilai, bahasa);
                if (!kunci.contains(entri.get(i).nilai)) {
                    kunci.add(entri.get(i).nilai);
                    tb.append("  [\"").append(escapeLua(entri.get(i).nilai)).append("\"] = \"")
                            .append(escapeLua(h.teks)).append("\",\n");
                }
            }
            for (int i = 0; i < kode.size(); i++) {
                List<TeksKode> daftar = bacaTeksKode(kode.get(i),
                        namaRelatif(dirProyek, kode.get(i)));
                for (int j = 0; j < daftar.size(); j++) {
                    if (kunci.contains(daftar.get(j).teks)) continue;
                    kunci.add(daftar.get(j).teks);
                    Hasil h = terjemahkan(daftar.get(j).teks, bahasa);
                    tb.append("  [\"").append(escapeLua(daftar.get(j).teks)).append("\"] = \"")
                            .append(escapeLua(h.teks)).append("\",\n");
                }
            }
            tb.append("}\n");
            File tabel = new File(dirProyek, "terjemahan-" + bahasa + ".lua");
            String galat = tulisBerkas(tabel, tb.toString());
            if (galat == null) berkasDitulis.add(tabel.getAbsolutePath());
        }

        lap.append("Ringkasan: ").append(jumlahTeks).append(" teks, ")
                .append(kataDiterjemahkan).append(" kata diterjemahkan, ")
                .append(kataBelum).append(" kata belum diterjemahkan.\n");
        if (!belumUnik.isEmpty()) {
            lap.append("Kata yang belum ada di kamus: ");
            boolean pertama = true;
            for (String k : belumUnik) {
                if (!pertama) lap.append(", ");
                lap.append(k);
                pertama = false;
            }
            lap.append("\n");
        }
        lap.append("Catatan: teks di kode tidak diubah otomatis — ubah sendiri memakai ")
                .append("tabel di atas bila perlu.\n");
        File lapFile = new File(dirProyek, "terjemahan-" + bahasa + ".txt");
        String galat = tulisBerkas(lapFile, lap.toString());
        if (galat == null) berkasDitulis.add(lapFile.getAbsolutePath());
        List<String> belum = new ArrayList<String>(belumUnik);
        return new Laporan(bahasa, jumlahTeks, kataDiterjemahkan, kataBelum, belum,
                berkasDitulis, lap.toString());
    }

    // ------------------------------------------------------------ pembantu

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
        return null;
    }

    private static List<File> cariBerkas(File dir, String akhiran) {
        List<File> hasil = new ArrayList<File>();
        if (dir == null || !dir.isDirectory()) return hasil;
        File[] daftar = dir.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            if (daftar[i].isFile() && daftar[i].getName().endsWith(akhiran)) {
                hasil.add(daftar[i]);
            }
        }
        return hasil;
    }

    private static void kumpulKode(File dir, List<File> keluar) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if ("bin".equals(f.getName()) || ".git".equals(f.getName())) continue;
                kumpulKode(f, keluar);
            } else {
                String n = f.getName();
                if (n.endsWith(".lua") || n.endsWith(".java") || n.endsWith(".kt")) {
                    keluar.add(f);
                }
            }
        }
    }

    private static String namaRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    private static String bacaBerkas(File f) {
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

    static String tulisBerkas(File f, String isi) {
        try {
            File induk = f.getParentFile();
            if (induk != null) induk.mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            if (!f.isFile() || f.length() == 0) {
                return "Berkas hasil kosong: " + f.getAbsolutePath();
            }
            return null;
        } catch (Exception e) {
            return "Gagal menulis " + f.getAbsolutePath() + ": " + e.getMessage();
        }
    }

    private static String escapeXml(String s) {
        if (s == null) s = "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String unescapeXml(String s) {
        if (s == null) return "";
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&")
                .replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"");
    }

    private static String unescapeJava(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') { sb.append('\n'); i++; }
                else if (n == 't') { sb.append('\t'); i++; }
                else if (n == 'r') { sb.append('\r'); i++; }
                else { sb.append(n); i++; }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeLua(String s) {
        if (s == null) s = "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
