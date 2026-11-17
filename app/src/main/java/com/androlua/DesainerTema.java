package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Desainer Tema Otomatis (Tahap 3F).
 *
 * Membuat tema SUNGGUHAN untuk proyek yang dipilih: menulis/menimpa
 * app/src/main/res/values/colors.xml (6 warna) dan
 * app/src/main/res/values/styles.xml (style app_theme yang dipakai
 * manifest proyek). Enam tema: Terang, Gelap, Biru, Hijau, Merah, Ungu.
 *
 * styles.xml HANYA memakai atribut android: bawaan framework API 21+
 * (android:colorPrimary, android:colorPrimaryDark, android:colorAccent,
 * android:windowBackground, android:textColorPrimary,
 * android:textColorSecondary, android:statusBarColor) supaya aapt2
 * bisa mengompilasinya tanpa AppCompat. Warna-warna didefinisikan di
 * colors.xml dan dirujuk sebagai @color/warna_*.
 *
 * Murni java.io/java.util (tanpa API Android) supaya bisa diuji ulang
 * di JRE biasa; XML hasil diverifikasi aapt2 compile di suite uji.
 */
public class DesainerTema {

    /** Satu tema. */
    public static class Tema {
        public final String id;
        public final String nama;
        /** Warna primer (bilah atas). */
        public final String primer;
        /** Warna primer gelap (bilah status). */
        public final String primerGelap;
        /** Warna aksen (tombol/penanda). */
        public final String aksen;
        /** Warna latar jendela. */
        public final String latar;
        /** Warna teks utama. */
        public final String teks;
        /** Warna teks sekunder. */
        public final String teksSekunder;
        public Tema(String id, String nama, String primer, String primerGelap,
                    String aksen, String latar, String teks, String teksSekunder) {
            this.id = id;
            this.nama = nama;
            this.primer = primer;
            this.primerGelap = primerGelap;
            this.aksen = aksen;
            this.latar = latar;
            this.teks = teks;
            this.teksSekunder = teksSekunder;
        }
    }

    public static final Tema[] SEMUA = {
        new Tema("terang", "Terang",
                "#2196F3", "#1976D2", "#FF9800", "#FFFFFF", "#212121", "#757575"),
        new Tema("gelap", "Gelap",
                "#212121", "#000000", "#BB86FC", "#121212", "#FFFFFF", "#B0B0B0"),
        new Tema("biru", "Biru",
                "#0D47A1", "#002171", "#40C4FF", "#E3F2FD", "#0D47A1", "#546E7A"),
        new Tema("hijau", "Hijau",
                "#2E7D32", "#1B5E20", "#69F0AE", "#E8F5E9", "#1B5E20", "#558B2F"),
        new Tema("merah", "Merah",
                "#C62828", "#8E0000", "#FF8A80", "#FFEBEE", "#B71C1C", "#795548"),
        new Tema("ungu", "Ungu",
                "#6A1B9A", "#38006B", "#EA80FC", "#F3E5F5", "#4A148C", "#7B1FA2"),
    };

    public static Tema cari(String id) {
        if (id == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    /** True bila string adalah kode warna heksa #RRGGBB. */
    public static boolean warnaValid(String w) {
        if (w == null) return false;
        return w.matches("^#[0-9a-fA-F]{6}$");
    }

    /**
     * Menerapkan tema ke proyek: menulis colors.xml + styles.xml di
     * app/src/main/res/values/. Mengembalikan null bila berhasil, atau
     * pesan galat. Tema lama tertimpa (itu yang diminta: "terapkan").
     */
    public static String terapkanTema(File dirProyek, String idTema) {
        Tema t = cari(idTema);
        if (t == null) return "Tema tidak dikenal: " + idTema;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        File values = new File(dirProyek, "app/src/main/res/values");
        values.mkdirs();
        String galat = tulisBerkas(new File(values, "colors.xml"), colorsXml(t));
        if (galat != null) return galat;
        galat = tulisBerkas(new File(values, "styles.xml"), stylesXml(t));
        if (galat != null) return galat;
        return null;
    }

    /** Isi colors.xml untuk tema. */
    public static String colorsXml(Tema t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Tema ").append(t.nama).append(" (dibuat oleh Desainer Tema Otomatis) -->\n");
        sb.append("<resources>\n");
        sb.append("    <color name=\"warna_primer\">").append(t.primer).append("</color>\n");
        sb.append("    <color name=\"warna_primer_gelap\">").append(t.primerGelap).append("</color>\n");
        sb.append("    <color name=\"warna_aksen\">").append(t.aksen).append("</color>\n");
        sb.append("    <color name=\"warna_latar\">").append(t.latar).append("</color>\n");
        sb.append("    <color name=\"warna_teks\">").append(t.teks).append("</color>\n");
        sb.append("    <color name=\"warna_teks_sekunder\">").append(t.teksSekunder).append("</color>\n");
        sb.append("</resources>\n");
        return sb.toString();
    }

    /** Isi styles.xml untuk tema (style app_theme, sama seperti bawaan proyek). */
    public static String stylesXml(Tema t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Tema ").append(t.nama).append(" (dibuat oleh Desainer Tema Otomatis) -->\n");
        sb.append("<resources>\n");
        sb.append("    <style name=\"app_theme\" parent=\"@android:style/Theme.DeviceDefault.Light\">\n");
        sb.append("        <item name=\"android:colorPrimary\">@color/warna_primer</item>\n");
        sb.append("        <item name=\"android:colorPrimaryDark\">@color/warna_primer_gelap</item>\n");
        sb.append("        <item name=\"android:colorAccent\">@color/warna_aksen</item>\n");
        sb.append("        <item name=\"android:windowBackground\">@color/warna_latar</item>\n");
        sb.append("        <item name=\"android:textColorPrimary\">@color/warna_teks</item>\n");
        sb.append("        <item name=\"android:textColorSecondary\">@color/warna_teks_sekunder</item>\n");
        sb.append("        <item name=\"android:statusBarColor\">@color/warna_primer_gelap</item>\n");
        sb.append("    </style>\n");
        sb.append("</resources>\n");
        return sb.toString();
    }

    private static String tulisBerkas(File f, String isi) {
        try {
            File induk = f.getParentFile();
            if (induk != null) induk.mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            return null;
        } catch (Exception e) {
            return "Gagal menulis " + f.getAbsolutePath() + ": " + e.getMessage();
        }
    }
}
