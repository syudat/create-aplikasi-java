package com.androlua;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audit Aksesibilitas TalkBack/Jieshuo (Fitur Gemini 5.7 nomor 1) —
 * logika murni tanpa Android, bisa diuji di JVM.
 *
 * Memindai teks XML layout (regex, tanpa parser Android): mengumpulkan
 * elemen yang bisa difokus/diklik, menandai yang tanpa label, menyusun
 * transkrip urutan fokus gesek kanan persis seperti yang diucapkan
 * TalkBack/Jieshuo, mendeteksi label duplikat dan elemen yang saling
 * menutupi (bila layout memakai koordinat layout_x/layout_y).
 * XML rusak tidak pernah membuat crash: hasilnya daftar kosong.
 */
public class AuditAkses {

    /** Jenis temuan. */
    public static final String TANPA_LABEL = "TANPA_LABEL";
    public static final String DUPLIKAT_LABEL = "DUPLIKAT_LABEL";
    public static final String MENUTUPI = "MENUTUPI";

    /** Satu temuan audit. */
    public static class Temuan {
        /** Nomor baris elemen di berkas XML (mulai 1). */
        public int baris;
        /** Nama elemen, mis. Button. */
        public String jenisElemen;
        /** Label yang dibaca ("" bila tidak ada). */
        public String label;
        /** True bila elemen punya label yang bisa dibaca. */
        public boolean adaLabel;
        /** TANPA_LABEL / DUPLIKAT_LABEL / MENUTUPI. */
        public String jenis;
        /** Keterangan tambahan untuk laporan. */
        public String keterangan;
    }

    /** Hasil audit satu berkas layout. */
    public static class DaftarTemuan {
        public List<Temuan> temuan = new ArrayList<Temuan>();
        /** Jumlah elemen fokus yang diperiksa. */
        public int jumlahElemen;
        /** Jumlah temuan tanpa label. */
        public int jumlahTanpaLabel;
        /**
         * True bila tidak ada elemen yang memakai koordinat
         * layout_x/layout_y, sehingga cek elemen menutupi dilewati.
         */
        public boolean cekPosisiDilewati;
    }

    /** Satu elemen layout yang ditemukan saat pemindaian. */
    public static class Elemen {
        public int baris;
        /** Nama sederhana elemen, mis. Button. */
        public String jenis;
        public String label;
        public boolean adaLabel;
        /** True bila elemen bisa difokus/diklik pembaca layar. */
        public boolean bisaFokus;
        /** True bila elemen bisa diklik. */
        public boolean bisaKlik;
        public String posisiX;
        public String posisiY;
    }

    private static final Pattern POLA_ATRIBUT = Pattern.compile(
            "([A-Za-z_][\\w.:-]*)\\s*=\\s*\"([^\"]*)\"");

    /**
     * Mengurai teks XML menjadi daftar elemen (tag pembuka saja).
     * Tag bisa membentang beberapa baris; nomor baris dihitung dari
     * posisi awal tag. Komentar, deklarasi XML, dan tag penutup dilewati.
     * Tidak pernah melempar untuk XML rusak: mengembalikan yang terbaca.
     */
    public static List<Elemen> uraiElemen(String xml) {
        List<Elemen> hasil = new ArrayList<Elemen>();
        if (xml == null || xml.length() == 0) return hasil;
        try {
            int i = 0;
            int n = xml.length();
            while (i < n) {
                char c = xml.charAt(i);
                if (c != '<') {
                    i++;
                    continue;
                }
                if (i + 3 < n && xml.charAt(i + 1) == '!'
                        && xml.charAt(i + 2) == '-' && xml.charAt(i + 3) == '-') {
                    int akhir = xml.indexOf("-->", i + 4);
                    i = akhir < 0 ? n : akhir + 3;
                    continue;
                }
                if (i + 1 < n && (xml.charAt(i + 1) == '!'
                        || xml.charAt(i + 1) == '?'
                        || xml.charAt(i + 1) == '/')) {
                    int akhir = cariAkhirTag(xml, i + 2);
                    i = akhir < 0 ? n : akhir + 1;
                    continue;
                }
                int awalNama = i + 1;
                int akhirNama = awalNama;
                while (akhirNama < n) {
                    char ch = xml.charAt(akhirNama);
                    if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '$') {
                        akhirNama++;
                    } else {
                        break;
                    }
                }
                if (akhirNama == awalNama) {
                    i++;
                    continue;
                }
                String namaPenuh = xml.substring(awalNama, akhirNama);
                int akhirTag = cariAkhirTag(xml, akhirNama);
                if (akhirTag < 0) {
                    // Tag tidak tertutup: berhenti di sini, yang sudah
                    // terbaca tetap dipakai.
                    break;
                }
                String atribut = xml.substring(akhirNama, akhirTag);
                Elemen e = buatElemen(namaPenuh, atribut);
                if (e != null) {
                    e.baris = hitungBaris(xml, i);
                    hasil.add(e);
                }
                i = akhirTag + 1;
            }
        } catch (Throwable ignored) {
            // XML rusak: kembalikan yang sempat terbaca.
        }
        return hasil;
    }

    /** Posisi '>' penutup tag, dengan memperhatikan nilai berpetik. */
    private static int cariAkhirTag(String xml, int dari) {
        boolean diPetikGanda = false;
        boolean diPetikTunggal = false;
        for (int i = dari; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (c == '"' && !diPetikTunggal) diPetikGanda = !diPetikGanda;
            else if (c == '\'' && !diPetikGanda) diPetikTunggal = !diPetikTunggal;
            else if (c == '>' && !diPetikGanda && !diPetikTunggal) return i;
        }
        return -1;
    }

    /** Nomor baris (mulai 1) dari posisi karakter di teks. */
    private static int hitungBaris(String xml, int posisi) {
        int baris = 1;
        for (int i = 0; i < posisi && i < xml.length(); i++) {
            if (xml.charAt(i) == '\n') baris++;
        }
        return baris;
    }

    /** Nama sederhana: bagian sesudah titik terakhir, mis. "Button". */
    static String namaSederhana(String namaPenuh) {
        int titik = namaPenuh.lastIndexOf('.');
        return titik >= 0 ? namaPenuh.substring(titik + 1) : namaPenuh;
    }

    private static String atribut(String teksAtribut, String nama) {
        Matcher m = POLA_ATRIBUT.matcher(teksAtribut);
        while (m.find()) {
            if (nama.equals(m.group(1))) return m.group(2);
        }
        return null;
    }

    /** True bila nama elemen termasuk keluarga tombol (berakhiran Button). */
    static boolean keluargaTombol(String jenis) {
        return jenis.endsWith("Button");
    }

    /** Membangun satu Elemen dari nama tag dan teks atributnya. */
    private static Elemen buatElemen(String namaPenuh, String teksAtribut) {
        String jenis = namaSederhana(namaPenuh);
        boolean tombol = keluargaTombol(jenis)
                || "CheckBox".equals(jenis) || "RadioButton".equals(jenis)
                || "Switch".equals(jenis) || "ToggleButton".equals(jenis)
                || "Spinner".equals(jenis);
        boolean bisaKlik = tombol
                || "true".equals(atribut(teksAtribut, "android:clickable"))
                || atribut(teksAtribut, "android:onClick") != null;
        boolean editTeks = "EditText".equals(jenis);
        boolean teksBiasa = "TextView".equals(jenis);
        if (!bisaKlik && !editTeks && !teksBiasa) return null;

        Elemen e = new Elemen();
        e.jenis = jenis;
        e.bisaKlik = bisaKlik;
        e.bisaFokus = bisaKlik || editTeks || teksBiasa;
        String deskripsi = atribut(teksAtribut, "android:contentDescription");
        String teks = atribut(teksAtribut, "android:text");
        String petunjuk = atribut(teksAtribut, "android:hint");
        if (deskripsi != null && deskripsi.trim().length() > 0) {
            e.label = deskripsi;
            e.adaLabel = true;
        } else if (teks != null && teks.trim().length() > 0) {
            e.label = teks;
            e.adaLabel = true;
        } else if (editTeks && petunjuk != null && petunjuk.trim().length() > 0) {
            e.label = petunjuk;
            e.adaLabel = true;
        } else {
            e.label = "";
            e.adaLabel = false;
        }
        e.posisiX = atribut(teksAtribut, "android:layout_x");
        e.posisiY = atribut(teksAtribut, "android:layout_y");
        return e;
    }

    /**
     * Mengaudit satu berkas layout: tombol tanpa label, label duplikat,
     * dan elemen klik yang saling menutupi di koordinat yang sama.
     * XML null/rusak -> daftar kosong, tanpa crash.
     */
    public static DaftarTemuan auditLayout(String xml) {
        DaftarTemuan hasil = new DaftarTemuan();
        if (xml == null) return hasil;
        try {
            List<Elemen> semua = uraiElemen(xml);
            boolean adaPosisi = false;
            Map<String, Integer> labelPertama = new HashMap<String, Integer>();
            Map<String, Integer> posisiPertama = new HashMap<String, Integer>();
            for (int i = 0; i < semua.size(); i++) {
                Elemen e = semua.get(i);
                if (!e.bisaFokus) continue;
                hasil.jumlahElemen++;
                if (e.posisiX != null && e.posisiY != null) adaPosisi = true;
                if (e.bisaKlik && !e.adaLabel) {
                    Temuan t = new Temuan();
                    t.baris = e.baris;
                    t.jenisElemen = e.jenis;
                    t.label = "";
                    t.adaLabel = false;
                    t.jenis = TANPA_LABEL;
                    t.keterangan = TeksUI.t("tombol tanpa label");
                    hasil.temuan.add(t);
                    hasil.jumlahTanpaLabel++;
                } else if (e.bisaKlik && e.adaLabel) {
                    Integer dulu = labelPertama.get(e.label);
                    if (dulu != null) {
                        Temuan t = new Temuan();
                        t.baris = e.baris;
                        t.jenisElemen = e.jenis;
                        t.label = e.label;
                        t.adaLabel = true;
                        t.jenis = DUPLIKAT_LABEL;
                        t.keterangan = TeksUI.t("label sama dengan baris ") + dulu;
                        hasil.temuan.add(t);
                    } else {
                        labelPertama.put(e.label, Integer.valueOf(e.baris));
                    }
                }
                if (e.bisaKlik && e.posisiX != null && e.posisiY != null) {
                    String kunci = e.posisiX.trim() + "," + e.posisiY.trim();
                    Integer dulu = posisiPertama.get(kunci);
                    if (dulu != null) {
                        Temuan t = new Temuan();
                        t.baris = e.baris;
                        t.jenisElemen = e.jenis;
                        t.label = e.label;
                        t.adaLabel = e.adaLabel;
                        t.jenis = MENUTUPI;
                        t.keterangan = TeksUI.t("menutupi elemen lain di posisi yang sama (baris ") + dulu + ")";
                        hasil.temuan.add(t);
                    } else {
                        posisiPertama.put(kunci, Integer.valueOf(e.baris));
                    }
                }
            }
            hasil.cekPosisiDilewati = !adaPosisi;
        } catch (Throwable ignored) {
            // XML rusak: kembalikan hasil sejauh ini (bisa kosong).
        }
        return hasil;
    }

    /** Peran yang diucapkan pembaca layar untuk satu jenis elemen. */
    static String peran(Elemen e) {
        if (keluargaTombol(e.jenis)) {
            if ("ImageButton".equals(e.jenis)) return TeksUI.t("Tombol gambar");
            return TeksUI.t("Tombol");
        }
        if ("ImageView".equals(e.jenis)) return TeksUI.t("Gambar");
        if ("TextView".equals(e.jenis)) return TeksUI.t("Teks");
        if ("EditText".equals(e.jenis)) return TeksUI.t("Kotak isian");
        if ("CheckBox".equals(e.jenis)) return TeksUI.t("Kotak centang");
        if ("RadioButton".equals(e.jenis)) return TeksUI.t("Tombol pilihan");
        if ("Switch".equals(e.jenis)) return TeksUI.t("Saklar");
        if ("Spinner".equals(e.jenis)) return TeksUI.t("Daftar pilihan");
        return TeksUI.t("Tampilan");
    }

    /**
     * Transkrip urutan fokus gesek kanan: satu baris per elemen fokus,
     * urutan dokumen, format "1. [Tombol] Simpan".
     * XML null/kosong/rusak -> "" tanpa crash.
     */
    public static String transkripFokus(String xml) {
        if (xml == null || xml.length() == 0) return "";
        try {
            List<Elemen> semua = uraiElemen(xml);
            StringBuilder b = new StringBuilder();
            int nomor = 0;
            for (int i = 0; i < semua.size(); i++) {
                Elemen e = semua.get(i);
                if (!e.bisaFokus) continue;
                // TextView tanpa teks tidak dibacakan pembaca layar.
                if ("TextView".equals(e.jenis) && !e.adaLabel) continue;
                nomor++;
                b.append(nomor).append(". [").append(peran(e)).append("] ");
                b.append(e.adaLabel ? e.label : TeksUI.t("(tanpa label)"));
                b.append('\n');
            }
            return b.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Menyisipkan android:contentDescription ke elemen yang tag pembukanya
     * mulai di baris yang diminta (baris mulai 1). Bila atribut sudah ada,
     * nilainya diganti. Bila baris tidak berisi tag, XML dikembalikan
     * apa adanya. Label null/kosong -> XML apa adanya.
     */
    public static String sisipLabel(String xml, int baris, String label) {
        if (xml == null || label == null || label.trim().length() == 0 || baris < 1) {
            return xml;
        }
        try {
            int awalBaris = awalBarisKe(xml, baris);
            if (awalBaris < 0) return xml;
            int awalTag = xml.indexOf('<', awalBaris);
            if (awalTag < 0) return xml;
            int akhirBaris = xml.indexOf('\n', awalBaris);
            if (akhirBaris >= 0 && awalTag > akhirBaris) return xml;
            if (awalTag + 1 >= xml.length()) return xml;
            char berikut = xml.charAt(awalTag + 1);
            if (!Character.isLetter(berikut)) return xml;
            int akhirTag = cariAkhirTag(xml, awalTag + 1);
            if (akhirTag < 0) return xml;
            String isiTag = xml.substring(awalTag, akhirTag);
            String nilai = label.replace("&", "&amp;")
                    .replace("\"", "&quot;").replace("<", "&lt;");
            Matcher m = Pattern.compile(
                    "android:contentDescription\\s*=\\s*\"[^\"]*\"").matcher(isiTag);
            String tagBaru;
            if (m.find()) {
                tagBaru = m.replaceFirst(
                        Matcher.quoteReplacement("android:contentDescription=\"" + nilai + "\""));
            } else {
                String sisipan = " android:contentDescription=\"" + nilai + "\"";
                if (isiTag.endsWith("/")) {
                    tagBaru = isiTag.substring(0, isiTag.length() - 1) + sisipan + "/";
                } else {
                    tagBaru = isiTag + sisipan;
                }
            }
            return xml.substring(0, awalTag) + tagBaru + xml.substring(akhirTag);
        } catch (Throwable ignored) {
            return xml;
        }
    }

    /** Posisi karakter awal baris ke-N (mulai 1); -1 bila tidak ada. */
    private static int awalBarisKe(String xml, int baris) {
        if (baris == 1) return 0;
        int posisi = 0;
        for (int i = 1; i < baris; i++) {
            int nl = xml.indexOf('\n', posisi);
            if (nl < 0) return -1;
            posisi = nl + 1;
        }
        return posisi < xml.length() ? posisi : -1;
    }

    /** Laporan audit satu berkas sebagai teks siap tampil. */
    public static String laporan(DaftarTemuan hasil, String namaBerkas) {
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Audit aksesibilitas: ")).append(namaBerkas).append('\n');
        b.append(hasil.jumlahElemen).append(TeksUI.t(" elemen diperiksa, "));
        b.append(hasil.temuan.size()).append(TeksUI.t(" temuan.")).append('\n');
        if (hasil.temuan.isEmpty()) {
            b.append(TeksUI.t("Tidak ada temuan dari pemeriksaan layout XML ini.")).append('\n');
        } else {
            b.append(TeksUI.t("Temuan:")).append('\n');
            for (int i = 0; i < hasil.temuan.size(); i++) {
                Temuan t = hasil.temuan.get(i);
                b.append(TeksUI.t("Baris ")).append(t.baris).append(": ");
                b.append(t.jenisElemen).append(" — ").append(t.keterangan);
                if (t.label != null && t.label.length() > 0) {
                    b.append(" (\"").append(t.label).append("\")");
                }
                b.append('\n');
            }
        }
        if (hasil.cekPosisiDilewati) {
            b.append(TeksUI.t("Cek posisi dilewati: layout tidak memakai koordinat (layout_x/layout_y), jadi elemen menutupi tidak bisa dideteksi dari berkas ini.")).append('\n');
        }
        return b.toString();
    }
}
