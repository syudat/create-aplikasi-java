package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pratinjau Banyak Ukuran Layar (seperti pemeriksaan ukuran layar di
 * Android Studio): membaca berkas layout XML proyek lalu memeriksa elemen
 * yang lebarnya ditulis tetap (mis. 400dp) terhadap beberapa lebar layar
 * umum. Elemen yang lebih lebar dari layar akan meluber/tertutup di HP
 * dengan layar sekecil itu, dan dilaporkan dengan bahasa yang mudah
 * dimengerti.
 *
 * Murni Java (tanpa API Android): layout dibaca sebagai teks, jadi
 * logikanya bisa diuji di komputer.
 */
public class PeriksaUkuran {

    /** Lebar layar (dp) yang diperiksa, dari terkecil ke terbesar. */
    public static final int[] LEBAR_LAYAR = {320, 360, 411, 480};

    /** Satu elemen berlebar tetap yang ketemu di layout. */
    public static class Elemen {
        public String berkas;
        public String tag;
        public double lebarDp;
    }

    public static class Hasil {
        public List<File> berkasLayout = new ArrayList<File>();
        public List<Elemen> elemen = new ArrayList<Elemen>();
        /** Lebar layar -> daftar baris masalah pada lebar itu. */
        public Map<Integer, List<String>> masalah = new LinkedHashMap<Integer, List<String>>();
        public String galat = null;
    }

    private static final Pattern POLA_TAG = Pattern.compile(
            "<([A-Za-z][A-Za-z0-9_.]*)\\s([^>]*)>", Pattern.DOTALL);
    private static final Pattern POLA_LEBAR = Pattern.compile(
            "layout_width\\s*=\\s*\"([0-9]+(?:\\.[0-9]+)?)(dp|dip)\"");

    /** Kumpulkan semua berkas layout XML dari folder proyek. */
    public static List<File> cariLayout(File dirProyek) {
        List<File> hasil = new ArrayList<File>();
        if (dirProyek == null) return hasil;
        File[] calon = {
                new File(dirProyek, "app/src/main/res"),
                new File(dirProyek, "res"),
        };
        for (int i = 0; i < calon.length; i++) {
            File[] anak = calon[i].listFiles();
            if (anak == null) continue;
            for (int j = 0; j < anak.length; j++) {
                if (anak[j].isDirectory() && anak[j].getName().startsWith("layout")) {
                    File[] isi = anak[j].listFiles();
                    if (isi == null) continue;
                    for (int k = 0; k < isi.length; k++) {
                        if (isi[k].isFile() && isi[k].getName().endsWith(".xml")) {
                            hasil.add(isi[k]);
                        }
                    }
                }
            }
        }
        return hasil;
    }

    /** Ekstrak elemen berlebar tetap dari teks satu berkas layout. */
    public static List<Elemen> ekstrak(String namaBerkas, String teks) {
        List<Elemen> hasil = new ArrayList<Elemen>();
        if (teks == null) return hasil;
        Matcher mt = POLA_TAG.matcher(teks);
        while (mt.find()) {
            Matcher ml = POLA_LEBAR.matcher(mt.group(2));
            if (ml.find()) {
                Elemen e = new Elemen();
                e.berkas = namaBerkas;
                e.tag = mt.group(1);
                try {
                    e.lebarDp = Double.parseDouble(ml.group(1));
                } catch (Throwable t) {
                    continue;
                }
                hasil.add(e);
            }
        }
        return hasil;
    }

    /** Periksa satu folder proyek dan susun hasil lengkapnya. */
    public static Hasil periksa(File dirProyek) {
        Hasil h = new Hasil();
        h.berkasLayout = cariLayout(dirProyek);
        if (h.berkasLayout.isEmpty()) {
            h.galat = TeksUI.t("Tidak ada berkas layout (folder res/layout) di proyek ini.");
            return h;
        }
        for (File f : h.berkasLayout) {
            h.elemen.addAll(ekstrak(f.getName(), bacaTeks(f)));
        }
        for (int i = 0; i < LEBAR_LAYAR.length; i++) {
            int lebar = LEBAR_LAYAR[i];
            List<String> baris = new ArrayList<String>();
            for (Elemen e : h.elemen) {
                if (e.lebarDp > lebar) {
                    baris.add(e.berkas + TeksUI.t(": elemen ") + e.tag + TeksUI.t(" lebar tetap ")
                            + angka(e.lebarDp) + TeksUI.t("dp melebihi lebar layar ") + lebar + "dp");
                }
            }
            h.masalah.put(lebar, baris);
        }
        return h;
    }

    /** Susun laporan teks Bahasa Indonesia. */
    public static String laporan(Hasil h, String namaProyek) {
        if (h.galat != null) return h.galat;
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Pratinjau ukuran layar proyek: ")).append(namaProyek).append('\n');
        b.append(TeksUI.t("Berkas layout diperiksa: ")).append(h.berkasLayout.size()).append('\n');
        b.append(TeksUI.t("Elemen dengan lebar tetap (dp): ")).append(h.elemen.size()).append('\n');
        for (int i = 0; i < LEBAR_LAYAR.length; i++) {
            int lebar = LEBAR_LAYAR[i];
            List<String> baris = h.masalah.get(lebar);
            b.append('\n').append(TeksUI.t("Layar ")).append(lebar).append(TeksUI.t(" dp: "));
            if (baris == null || baris.isEmpty()) {
                b.append(TeksUI.t("aman, tidak ada elemen yang meluber.") + "\n");
            } else {
                b.append(baris.size()).append(TeksUI.t(" elemen meluber:") + "\n");
                for (String s : baris) b.append("- ").append(s).append('\n');
            }
        }
        b.append('\n').append(TeksUI.t("Catatan: elemen dengan lebar match_parent atau "))
                .append(TeksUI.t("wrap_content menyesuaikan layar secara otomatis dan tidak "))
                .append(TeksUI.t("diperiksa di sini. Lebar tetap yang melebihi layar membuat "))
                .append(TeksUI.t("elemen tertutup atau meluber di HP berlayar kecil."));
        return b.toString();
    }

    private static String angka(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static String bacaTeks(File f) {
        try {
            Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            try {
                StringBuilder b = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
                return b.toString();
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return "";
        }
    }
}
