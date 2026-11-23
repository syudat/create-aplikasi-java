package com.androlua;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pengelola Sumber Daya (Resource Manager seperti di Android Studio) —
 * logika murni tanpa Android, bisa diuji di JVM.
 *
 * Membaca folder res proyek (app/src/main/res atau res/): menghitung
 * berkas per kategori (drawable, layout, values, mipmap, raw, anim,
 * menu, xml, font), membaca daftar string dan warna dari values, dan
 * menambah string/warna baru ke strings.xml/colors.xml dengan aman
 * (nama diperiksa, duplikat ditolak, nilai diloloskan XML).
 */
public class SumberDaya {

    public static class Ringkasan {
        public String galat;
        /** kategori -> daftar "nama berkas (ukuran)" */
        public final Map<String, List<String>> perKategori = new LinkedHashMap<String, List<String>>();
        /** kategori -> total ukuran bita */
        public final Map<String, Long> ukuranKategori = new LinkedHashMap<String, Long>();
        public final List<String[]> strings = new ArrayList<String[]>(); // {nama, nilai}
        public final List<String[]> colors = new ArrayList<String[]>();  // {nama, nilai}
        public final List<String> styles = new ArrayList<String>();
        public int jumlahBerkas;
        public long totalBita;
    }

    /** Folder res proyek: app/src/main/res didahulukan, lalu res/. */
    public static File folderRes(File dirProyek) {
        File a = new File(dirProyek, "app/src/main/res");
        if (a.isDirectory()) return a;
        File b = new File(dirProyek, "res");
        if (b.isDirectory()) return b;
        return null;
    }

    /** Folder values di dalam folder res (belum tentu ada). */
    public static File folderValues(File dirProyek) {
        File res = folderRes(dirProyek);
        if (res == null) res = new File(dirProyek, "app/src/main/res");
        return new File(res, "values");
    }

    public static Ringkasan baca(File dirProyek) {
        Ringkasan h = new Ringkasan();
        File res = folderRes(dirProyek);
        if (res == null) {
            h.galat = "Folder res tidak ditemukan di proyek ini.";
            return h;
        }
        File[] subs = res.listFiles();
        if (subs != null) {
            for (File sub : subs) {
                if (!sub.isDirectory()) continue;
                String kategori = kategoriDari(sub.getName());
                List<String> daftar = new ArrayList<String>();
                long total = 0;
                File[] berkas = sub.listFiles();
                if (berkas != null) {
                    for (File b : berkas) {
                        if (!b.isFile()) continue;
                        daftar.add(b.getName() + " (" + ukuran(b.length()) + ")");
                        total += b.length();
                        h.jumlahBerkas++;
                    }
                }
                if (!daftar.isEmpty()) {
                    h.perKategori.put(kategori, daftar);
                    h.ukuranKategori.put(kategori, total);
                    h.totalBita += total;
                }
                if (sub.getName().startsWith("values")) {
                    bacaValues(sub, h);
                }
            }
        }
        if (h.jumlahBerkas == 0 && h.galat == null) {
            h.galat = "Folder res proyek ini masih kosong.";
        }
        return h;
    }

    private static void bacaValues(File values, Ringkasan h) {
        String s = teks(new File(values, "strings.xml"));
        if (s != null) h.strings.addAll(daftarString(s));
        String c = teks(new File(values, "colors.xml"));
        if (c != null) h.colors.addAll(daftarWarna(c));
        String st = teks(new File(values, "styles.xml"));
        if (st != null) {
            Matcher m = Pattern.compile("<style\\s+name=\"([^\"]+)\"").matcher(st);
            while (m.find()) h.styles.add(m.group(1));
        }
    }

    /** "drawable-hdpi" -> "drawable", "values-night" -> "values". */
    public static String kategoriDari(String namaFolder) {
        int i = namaFolder.indexOf('-');
        return i > 0 ? namaFolder.substring(0, i) : namaFolder;
    }

    private static final Pattern POLA_STRING =
            Pattern.compile("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL);
    private static final Pattern POLA_WARNA =
            Pattern.compile("<color\\s+name=\"([^\"]+)\"[^>]*>(.*?)</color>", Pattern.DOTALL);

    public static List<String[]> daftarString(String isiXml) {
        List<String[]> hasil = new ArrayList<String[]>();
        if (isiXml == null) return hasil;
        Matcher m = POLA_STRING.matcher(isiXml);
        while (m.find()) hasil.add(new String[]{m.group(1), m.group(2).trim()});
        return hasil;
    }

    public static List<String[]> daftarWarna(String isiXml) {
        List<String[]> hasil = new ArrayList<String[]>();
        if (isiXml == null) return hasil;
        Matcher m = POLA_WARNA.matcher(isiXml);
        while (m.find()) hasil.add(new String[]{m.group(1), m.group(2).trim()});
        return hasil;
    }

    /**
     * Memeriksa nama sumber daya: huruf kecil, angka, garis bawah, titik;
     * harus mulai huruf. Mengembalikan pesan galat atau null bila sah.
     */
    public static String galatNama(String nama) {
        if (nama == null || nama.length() == 0) return "Nama tidak boleh kosong.";
        if (!nama.matches("[a-z][a-z0-9_.]*"))
            return "Nama hanya boleh huruf kecil, angka, garis bawah, dan titik, diawali huruf.";
        return null;
    }

    /** Loloskan nilai untuk isi elemen XML Android. */
    public static String lolosXml(String nilai) {
        return nilai.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "\\'").replace("\"", "\\\"");
    }

    /**
     * Menambah <string> ke isi strings.xml. Mengembalikan isi baru;
     * melempar IllegalArgumentException dengan pesan jelas bila nama
     * tidak sah atau sudah ada. isiXml null/kosong = buat kerangka baru.
     */
    public static String tambahString(String isiXml, String nama, String nilai) {
        String g = galatNama(nama);
        if (g != null) throw new IllegalArgumentException(g);
        if (nilai == null) nilai = "";
        for (String[] s : daftarString(isiXml)) {
            if (s[0].equals(nama))
                throw new IllegalArgumentException("String bernama \"" + nama + "\" sudah ada.");
        }
        String baris = "    <string name=\"" + nama + "\">" + lolosXml(nilai) + "</string>\n";
        return sisipkan(isiXml, baris);
    }

    /** Memeriksa nilai warna #RRGGBB atau #AARRGGBB; pesan galat atau null. */
    public static String galatWarna(String hex) {
        if (hex == null) return "Nilai warna kosong.";
        String t = hex.trim();
        if (!t.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?") && !t.matches("#[0-9a-fA-F]{8}"))
            return "Nilai warna harus seperti #FF5722 (6 atau 8 angka hex sesudah #).";
        return null;
    }

    /** Menambah <color> ke isi colors.xml; aturan sama seperti tambahString. */
    public static String tambahWarna(String isiXml, String nama, String hex) {
        String g = galatNama(nama);
        if (g != null) throw new IllegalArgumentException(g);
        String gw = galatWarna(hex);
        if (gw != null) throw new IllegalArgumentException(gw);
        for (String[] s : daftarWarna(isiXml)) {
            if (s[0].equals(nama))
                throw new IllegalArgumentException("Warna bernama \"" + nama + "\" sudah ada.");
        }
        String baris = "    <color name=\"" + nama + "\">" + hex.trim().toUpperCase() + "</color>\n";
        return sisipkan(isiXml, baris);
    }

    private static String sisipkan(String isiXml, String baris) {
        if (isiXml == null || isiXml.trim().length() == 0) {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" + baris + "</resources>\n";
        }
        int i = isiXml.lastIndexOf("</resources>");
        if (i < 0) throw new IllegalArgumentException("Berkas tidak memiliki penutup </resources>.");
        return isiXml.substring(0, i) + baris + isiXml.substring(i);
    }

    /** Membaca berkas teks UTF-8; null bila tidak ada / gagal. */
    public static String teks(File f) {
        try {
            if (f == null || !f.isFile()) return null;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Menulis teks UTF-8 ke berkas (membuat folder induk bila perlu). */
    public static boolean tulis(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(isi.getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Format ukuran bita yang ramah dibaca. */
    public static String ukuran(long bita) {
        if (bita < 1024) return bita + " B";
        if (bita < 1024 * 1024) return String.format("%.1f KB", bita / 1024.0);
        return String.format("%.2f MB", bita / (1024.0 * 1024.0));
    }

    /** Laporan teks bahasa Indonesia untuk dibacakan / ditampilkan. */
    public static String laporan(Ringkasan h, String namaProyek) {
        StringBuilder b = new StringBuilder();
        b.append("Sumber daya proyek ").append(namaProyek).append(":\n");
        if (h.galat != null) {
            b.append(h.galat).append('\n');
            return b.toString();
        }
        b.append(h.jumlahBerkas).append(" berkas, total ").append(ukuran(h.totalBita)).append(".\n");
        for (Map.Entry<String, List<String>> e : h.perKategori.entrySet()) {
            b.append("\nKategori ").append(e.getKey()).append(" (")
             .append(e.getValue().size()).append(" berkas, ")
             .append(ukuran(h.ukuranKategori.get(e.getKey()))).append("):\n");
            for (String s : e.getValue()) b.append("- ").append(s).append('\n');
        }
        if (!h.strings.isEmpty()) {
            b.append("\nString di strings.xml (").append(h.strings.size()).append("):\n");
            for (String[] s : h.strings) b.append("- ").append(s[0]).append(" = ").append(s[1]).append('\n');
        }
        if (!h.colors.isEmpty()) {
            b.append("\nWarna di colors.xml (").append(h.colors.size()).append("):\n");
            for (String[] s : h.colors) b.append("- ").append(s[0]).append(" = ").append(s[1]).append('\n');
        }
        if (!h.styles.isEmpty()) {
            b.append("\nGaya di styles.xml (").append(h.styles.size()).append("):\n");
            for (String s : h.styles) b.append("- ").append(s).append('\n');
        }
        return b.toString();
    }
}
