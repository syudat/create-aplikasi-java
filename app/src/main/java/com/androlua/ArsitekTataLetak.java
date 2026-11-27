package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arsitek Tata Letak Berbasis Hirarki Pohon Teks dan Wireframe Spasial
 * (Fitur 3, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - urai: XML layout -> pohon simpul (nama, atribut, anak).
 *  - hitungKotak: simulasi tata letak matematis untuk LinearLayout
 *    vertikal/horizontal (dp eksplisit, match_parent, wrap_content
 *    diperkirakan dari teks) pada lebar layar tertentu.
 *  - narasi: tiap elemen dibacakan sebagai kalimat posisi
 *    ("di kanan bawah, 16 dp di bawah ...").
 *  - periksa: tabrakan antar-elemen dan elemen keluar layar.
 *
 * BATAS JUJUR: yang dihitung adalah LinearLayout dan atribut dp
 * sederhana. ConstraintLayout (rantai/bias), ukuran berbobot kompleks,
 * dan tampilan Compose belum disimulasikan — elemen yang posisinya
 * tidak bisa dihitung dilaporkan sebagai "posisi tidak tentu", bukan
 * ditebak.
 */
public class ArsitekTataLetak {

    /** Satu simpul pohon layout. */
    public static class Simpul {
        public String nama = "";
        public String id = "";
        public String teks = "";
        public int lebarDp = -1;
        public int tinggiDp = -1;
        public boolean lebarMatch = false;
        public boolean tinggiMatch = false;
        public boolean lebarWrap = false;
        public boolean tinggiWrap = false;
        public int marginKiri = 0;
        public int marginAtas = 0;
        public int marginKanan = 0;
        public int marginBawah = 0;
        public String orientasi = "";
        public String diBawah = "";
        public int x = -1;
        public int y = -1;
        public int w = -1;
        public int h = -1;
        public List<Simpul> anak = new ArrayList<Simpul>();
    }

    /** Satu temuan pemeriksaan: jenis + penjelasan. */
    public static class Temuan {
        public String jenis = "";
        public String penjelasan = "";
        public Temuan(String jenis, String penjelasan) {
            this.jenis = jenis;
            this.penjelasan = penjelasan;
        }
        public String toString() {
            return jenis + ": " + penjelasan;
        }
    }

    private static final Pattern TAG = Pattern.compile(
            "<(/?)([A-Za-z0-9_\\.]+)((?:\\s+[A-Za-z0-9_:.-]+\\s*=\\s*\"[^\"]*\")*)\\s*(/?)>");
    private static final Pattern ATR = Pattern.compile(
            "([A-Za-z0-9_:.-]+)\\s*=\\s*\"([^\"]*)\"");

    private static String ambilAtr(String mentah, String nama) {
        Matcher m = ATR.matcher(mentah);
        while (m.find()) {
            if (nama.equals(m.group(1))) return m.group(2);
        }
        return null;
    }

    private static int ambilDp(String nilai) {
        if (nilai == null) return -1;
        nilai = nilai.trim();
        if (nilai.endsWith("dp") || nilai.endsWith("dip")) {
            try {
                return Integer.parseInt(nilai.replace("dp", "")
                        .replace("dip", "").trim());
            } catch (Throwable t) {
                return -1;
            }
        }
        return -1;
    }

    private static void isiUkuran(Simpul s, String nilai, boolean lebar) {
        if (nilai == null) return;
        nilai = nilai.trim();
        if ("match_parent".equals(nilai) || "fill_parent".equals(nilai)) {
            if (lebar) s.lebarMatch = true; else s.tinggiMatch = true;
        } else if ("wrap_content".equals(nilai)) {
            if (lebar) s.lebarWrap = true; else s.tinggiWrap = true;
        } else {
            int dp = ambilDp(nilai);
            if (dp >= 0) {
                if (lebar) s.lebarDp = dp; else s.tinggiDp = dp;
            }
        }
    }

    /** Urai XML layout menjadi pohon simpul (akar = elemen terluar). */
    public static Simpul urai(String xml) {
        if (xml == null) return null;
        List<Simpul> tumpukan = new ArrayList<Simpul>();
        Simpul akar = null;
        Matcher m = TAG.matcher(xml);
        while (m.find()) {
            boolean tutup = "/".equals(m.group(1));
            String nama = m.group(2);
            String atr = m.group(3) == null ? "" : m.group(3);
            boolean tutupSendiri = "/".equals(m.group(4));
            if (nama.startsWith("?") || nama.startsWith("!")) continue;
            if (tutup) {
                if (tumpukan.size() > 0) tumpukan.remove(tumpukan.size() - 1);
                continue;
            }
            Simpul s = new Simpul();
            s.nama = nama;
            String id = ambilAtr(atr, "android:id");
            if (id != null) {
                int garis = id.lastIndexOf('/');
                s.id = garis >= 0 ? id.substring(garis + 1) : id;
            }
            String teks = ambilAtr(atr, "android:text");
            if (teks != null) s.teks = teks;
            isiUkuran(s, ambilAtr(atr, "android:layout_width"), true);
            isiUkuran(s, ambilAtr(atr, "android:layout_height"), false);
            String o = ambilAtr(atr, "android:orientation");
            if (o != null) s.orientasi = o;
            s.marginKiri = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginLeft")));
            s.marginAtas = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginTop")));
            s.marginKanan = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginRight")));
            s.marginBawah = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginBottom")));
            int margin = ambilDp(ambilAtr(atr, "android:layout_margin"));
            if (margin >= 0) {
                s.marginKiri = margin;
                s.marginAtas = margin;
                s.marginKanan = margin;
                s.marginBawah = margin;
            }
            String bawah = ambilAtr(atr, "android:layout_below");
            if (bawah != null) {
                int garis = bawah.lastIndexOf('/');
                s.diBawah = garis >= 0 ? bawah.substring(garis + 1) : bawah;
            }
            if (tumpukan.size() == 0) {
                akar = s;
            } else {
                tumpukan.get(tumpukan.size() - 1).anak.add(s);
            }
            if (!tutupSendiri) tumpukan.add(s);
        }
        return akar;
    }

    /** Hitung jumlah simpul di pohon (termasuk akar). */
    public static int hitungSimpul(Simpul s) {
        if (s == null) return 0;
        int n = 1;
        for (int i = 0; i < s.anak.size(); i++) n += hitungSimpul(s.anak.get(i));
        return n;
    }

    /** Lebar perkiraan wrap_content dari teks (7 dp per huruf, min 48 dp). */
    static int lebarTeks(String teks) {
        if (teks == null) return 48;
        return Math.max(48, teks.length() * 7 + 24);
    }

    /** Tinggi perkiraan wrap_content (48 dp tombol/teks, 56 dp kolom input). */
    static int tinggiTeks(Simpul s) {
        if (s.nama.endsWith("EditText")) return 56;
        return 48;
    }

    /**
     * Simulasikan kotak tiap simpul pada layar selebar lebarLayarDp.
     * Mengisi x/y/w/h; simpul yang posisinya tidak bisa dihitung
     * mendapat x = -1.
     */
    public static void hitungKotak(Simpul akar, int lebarLayarDp) {
        if (akar == null) return;
        akar.x = 0;
        akar.y = 0;
        akar.w = akar.lebarDp >= 0 ? akar.lebarDp : lebarLayarDp;
        akar.h = akar.tinggiDp >= 0 ? akar.tinggiDp : -1;
        hitungAnak(akar, lebarLayarDp);
    }

    private static void hitungAnak(Simpul induk, int lebarLayarDp) {
        // LinearLayout menumpuk anak; wadah lain (FrameLayout,
        // RelativeLayout) menumpuk anak di titik yang sama (absolut) —
        // di sanalah tabrakan bisa terjadi dan harus dilaporkan.
        boolean linear = induk.nama.endsWith("LinearLayout");
        boolean vertikal = !"horizontal".equals(induk.orientasi);
        int kursor = 0;
        int tinggiInduk = 0;
        for (int i = 0; i < induk.anak.size(); i++) {
            Simpul s = induk.anak.get(i);
            int w = s.lebarDp >= 0 ? s.lebarDp
                    : s.lebarMatch ? Math.max(0, induk.w - s.marginKiri - s.marginKanan)
                    : lebarTeks(s.teks);
            int h = s.tinggiDp >= 0 ? s.tinggiDp
                    : s.tinggiMatch ? 48 : tinggiTeks(s);
            s.w = w;
            s.h = h;
            if (!linear) {
                s.x = induk.x + s.marginKiri;
                s.y = induk.y + s.marginAtas;
            } else if (vertikal) {
                s.x = induk.x + s.marginKiri;
                s.y = induk.y + kursor + s.marginAtas;
                kursor += s.marginAtas + h + s.marginBawah;
            } else {
                s.x = induk.x + kursor + s.marginKiri;
                s.y = induk.y + s.marginAtas;
                kursor += s.marginKiri + w + s.marginKanan;
            }
            tinggiInduk = Math.max(tinggiInduk, kursor);
            hitungAnak(s, lebarLayarDp);
        }
        if (induk.tinggiDp < 0 && !induk.tinggiMatch) {
            induk.h = induk.anak.size() == 0 ? tinggiTeks(induk)
                    : (linear ? tinggiInduk : 48);
        }
    }

    /** Nama tampilan simpul: id bila ada, kalau tidak nama + teks. */
    public static String namaTampil(Simpul s) {
        if (s.id != null && s.id.length() > 0) return s.id;
        if (s.teks != null && s.teks.length() > 0) return s.nama + " \"" + s.teks + "\"";
        return s.nama;
    }

    /** Narasi posisi tiap elemen (satu kalimat per simpul, urut pohon). */
    public static List<String> narasi(Simpul akar, int lebarLayarDp) {
        List<String> hasil = new ArrayList<String>();
        if (akar == null) return hasil;
        narasiKe(akar, lebarLayarDp, hasil);
        return hasil;
    }

    private static void narasiKe(Simpul s, int lebarLayarDp, List<String> hasil) {
        if (s.x < 0) {
            hasil.add(namaTampil(s) + ": posisi tidak tentu.");
        } else {
            String vertikal = s.y < 100 ? "di atas" : s.y > 400 ? "di bawah" : "di tengah";
            String horizontal = s.x + s.w > lebarLayarDp - 20 ? "di kanan"
                    : s.x < 20 ? "di kiri" : "di tengah";
            int persen = lebarLayarDp > 0 ? (s.w * 100 / lebarLayarDp) : 0;
            hasil.add(namaTampil(s) + " " + vertikal + " " + horizontal
                    + ", lebar " + persen + " persen layar, " + s.w + " dp kali "
                    + s.h + " dp, " + s.y + " dp dari atas.");
        }
        for (int i = 0; i < s.anak.size(); i++) narasiKe(s.anak.get(i), lebarLayarDp, hasil);
    }

    /** Periksa tabrakan dan elemen keluar layar (butuh hitungKotak dulu). */
    public static List<Temuan> periksa(Simpul akar, int lebarLayarDp) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (akar == null) return hasil;
        List<Simpul> semua = new ArrayList<Simpul>();
        kumpul(akar, semua);
        for (int i = 0; i < semua.size(); i++) {
            Simpul a = semua.get(i);
            if (a.x < 0) {
                hasil.add(new Temuan("Tidak tentu",
                        namaTampil(a) + " posisinya tidak bisa dihitung (bukan LinearLayout sederhana)."));
                continue;
            }
            if (a.x + a.w > lebarLayarDp) {
                hasil.add(new Temuan("Keluar layar",
                        namaTampil(a) + " melebar sampai " + (a.x + a.w)
                        + " dp, padahal layar hanya " + lebarLayarDp + " dp."));
            }
            for (int j = i + 1; j < semua.size(); j++) {
                Simpul b = semua.get(j);
                if (b.x < 0) continue;
                if (bersaudara(a, b, akar) && tumpangTindih(a, b)) {
                    hasil.add(new Temuan("Tabrakan",
                            namaTampil(a) + " bertabrakan dengan " + namaTampil(b) + "."));
                }
            }
        }
        return hasil;
    }

    private static void kumpul(Simpul s, List<Simpul> keluar) {
        keluar.add(s);
        for (int i = 0; i < s.anak.size(); i++) kumpul(s.anak.get(i), keluar);
    }

    private static boolean bersaudara(Simpul a, Simpul b, Simpul akar) {
        return indukDari(akar, a) == indukDari(akar, b);
    }

    private static Simpul indukDari(Simpul kini, Simpul cari) {
        for (int i = 0; i < kini.anak.size(); i++) {
            if (kini.anak.get(i) == cari) return kini;
            Simpul dalam = indukDari(kini.anak.get(i), cari);
            if (dalam != null) return dalam;
        }
        return null;
    }

    private static boolean tumpangTindih(Simpul a, Simpul b) {
        return a.x < b.x + b.w && b.x < a.x + a.w
                && a.y < b.y + b.h && b.y < a.y + a.h;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahElemen, List<Temuan> temuan) {
        int tabrakan = 0;
        int keluar = 0;
        if (temuan != null) {
            for (int i = 0; i < temuan.size(); i++) {
                if ("Tabrakan".equals(temuan.get(i).jenis)) tabrakan++;
                if ("Keluar layar".equals(temuan.get(i).jenis)) keluar++;
            }
        }
        int total = temuan == null ? 0 : temuan.size();
        return "Elemen diperiksa: " + jumlahElemen + ". Tabrakan ditemukan: "
                + tabrakan + ". Keluar layar: " + keluar + ". Total temuan: " + total + ".";
    }
}
