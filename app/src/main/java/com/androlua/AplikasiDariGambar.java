package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Aplikasi dari Gambar (Tahap 3G).
 *
 * Membuat proyek aplikasi galeri sungguhan dari sebuah gambar: gambar
 * disalin ke assets proyek, lalu MainActivity.java galeri ditulis —
 * menampilkan gambar itu memenuhi layar dengan warna latar dari warna
 * dominan gambar (dihitung Activity lewat AnalisisGambar) dan judul
 * dari nama aplikasi.
 *
 * Ronde 3: Lua dihapus — galeri yang dulu ditulis sebagai main.lua kini
 * ditulis sebagai MainActivity.java (Activity Java murni, UI programatik).
 *
 * Murni Java (java.io, tanpa API Android) supaya bisa diuji ulang di JRE.
 * Activity yang memilih gambar (ACTION_GET_CONTENT) dan menghitung
 * warna dominannya; kelas ini hanya menulis berkas proyek.
 */
public class AplikasiDariGambar {

    /** Nama berkas gambar di dalam assets proyek. */
    public static final String NAMA_GAMBAR = "gambar.jpg";

    /**
     * Nama aplikasi dari nama berkas gambar: buang ekstensi, ganti
     * garis bawah/dasis jadi spasi, kapital tiap kata.
     * Contoh: "pantai_sore.jpg" -> "Pantai Sore".
     */
    public static String namaDariBerkas(String namaBerkas) {
        if (namaBerkas == null || namaBerkas.trim().length() == 0) {
            return "Galeri Saya";
        }
        String nama = namaBerkas.trim();
        int titik = nama.lastIndexOf('.');
        if (titik > 0) nama = nama.substring(0, titik);
        nama = nama.replace('_', ' ').replace('-', ' ');
        String[] kata = nama.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kata.length; i++) {
            if (kata[i].length() == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(kata[i].charAt(0)));
            if (kata[i].length() > 1) sb.append(kata[i].substring(1));
        }
        if (sb.length() == 0) return "Galeri Saya";
        return sb.toString();
    }

    /**
     * Membuat aplikasi galeri di proyek Java yang sudah dibuat:
     * menyalin gambar ke assets/gambar.jpg dan menulis MainActivity.java
     * galeri. Mengembalikan null bila berhasil, atau pesan galat.
     *
     * @param dirProyek folder proyek Java (sudah ada MainActivity.java bawaan)
     * @param gambar berkas gambar sumber (jpg/png)
     * @param namaAplikasi nama aplikasi galeri
     * @param warnaDominan warna dominan gambar (ARGB) untuk latar
     */
    public static String buatGaleri(File dirProyek, File gambar,
                                    String namaAplikasi, int warnaDominan) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        if (gambar == null || !gambar.isFile()) {
            return "Berkas gambar tidak ditemukan.";
        }
        if (namaAplikasi == null || namaAplikasi.trim().length() == 0) {
            namaAplikasi = "Galeri Saya";
        }
        String paket = InfoProyek.namaPaket(dirProyek);
        if (paket == null || paket.isEmpty()) {
            return "Nama paket proyek tidak terbaca dari berkas data proyek.";
        }
        File dirAssets = new File(dirProyek, "app/src/main/assets");
        if (!dirAssets.mkdirs() && !dirAssets.isDirectory()) {
            return "Tidak bisa membuat folder assets proyek.";
        }
        String galat = salin(gambar, new File(dirAssets, NAMA_GAMBAR));
        if (galat != null) return galat;
        File target = new File(dirProyek,
                "app/src/main/java/" + paket.replace('.', '/') + "/MainActivity.java");
        galat = tulis(target, kodeGaleri(paket, namaAplikasi, warnaDominan));
        if (galat != null) return galat;
        return null;
    }

    /** Kode MainActivity.java aplikasi galeri (Java murni). */
    public static String kodeGaleri(String paket, String namaAplikasi, int warnaDominan) {
        String heksa = AnalisisGambar.heksa(warnaDominan);
        String teks = AnalisisGambar.warnaTeksUntuk(warnaDominan) == 0xFFFFFFFF
                ? "#FFFFFF" : "#000000";
        String nama = lolosJava(namaAplikasi);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paket).append(";\n");
        sb.append("\n");
        sb.append("import android.app.Activity;\n");
        sb.append("import android.graphics.Bitmap;\n");
        sb.append("import android.graphics.BitmapFactory;\n");
        sb.append("import android.graphics.Color;\n");
        sb.append("import android.os.Bundle;\n");
        sb.append("import android.view.Gravity;\n");
        sb.append("import android.widget.ImageView;\n");
        sb.append("import android.widget.LinearLayout;\n");
        sb.append("import android.widget.TextView;\n");
        sb.append("import java.io.InputStream;\n");
        sb.append("\n");
        sb.append("// Aplikasi galeri — dibuat otomatis oleh Create Aplikasi dari gambar.\n");
        sb.append("// Warna tema diambil dari warna dominan gambar: ").append(heksa).append("\n");
        sb.append("public class MainActivity extends Activity {\n");
        sb.append("    @Override\n");
        sb.append("    protected void onCreate(Bundle savedInstanceState) {\n");
        sb.append("        super.onCreate(savedInstanceState);\n");
        sb.append("        setTitle(\"").append(nama).append("\");\n");
        sb.append("\n");
        sb.append("        LinearLayout r = new LinearLayout(this);\n");
        sb.append("        r.setOrientation(LinearLayout.VERTICAL);\n");
        sb.append("        r.setBackgroundColor(Color.parseColor(\"").append(heksa).append("\"));\n");
        sb.append("\n");
        sb.append("        TextView judul = new TextView(this);\n");
        sb.append("        judul.setText(\"").append(nama).append("\");\n");
        sb.append("        judul.setTextSize(22);\n");
        sb.append("        judul.setTextColor(Color.parseColor(\"").append(teks).append("\"));\n");
        sb.append("        judul.setGravity(Gravity.CENTER);\n");
        sb.append("        LinearLayout.LayoutParams lpJudul = new LinearLayout.LayoutParams(\n");
        sb.append("                LinearLayout.LayoutParams.MATCH_PARENT,\n");
        sb.append("                LinearLayout.LayoutParams.WRAP_CONTENT);\n");
        sb.append("        judul.setLayoutParams(lpJudul);\n");
        sb.append("        r.addView(judul);\n");
        sb.append("\n");
        sb.append("        ImageView gambar = new ImageView(this);\n");
        sb.append("        LinearLayout.LayoutParams lpGambar = new LinearLayout.LayoutParams(\n");
        sb.append("                LinearLayout.LayoutParams.MATCH_PARENT,\n");
        sb.append("                LinearLayout.LayoutParams.MATCH_PARENT);\n");
        sb.append("        gambar.setLayoutParams(lpGambar);\n");
        sb.append("        gambar.setScaleType(ImageView.ScaleType.FIT_CENTER);\n");
        sb.append("        r.addView(gambar);\n");
        sb.append("\n");
        sb.append("        setContentView(r);\n");
        sb.append("\n");
        sb.append("        // Muat gambar dari assets proyek.\n");
        sb.append("        try {\n");
        sb.append("            InputStream masuk = getAssets().open(\"gambar.jpg\");\n");
        sb.append("            Bitmap bmp = BitmapFactory.decodeStream(masuk);\n");
        sb.append("            masuk.close();\n");
        sb.append("            if (bmp != null) gambar.setImageBitmap(bmp);\n");
        sb.append("        } catch (Exception ignored) {\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Loloskan teks supaya aman di dalam string Java ("...", \n, \r). */
    private static String lolosJava(String teks) {
        return teks.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String salin(File dari, File ke) {
        try {
            FileInputStream masuk = new FileInputStream(dari);
            try {
                FileOutputStream keluar = new FileOutputStream(ke);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
                } finally {
                    keluar.close();
                }
            } finally {
                masuk.close();
            }
            return null;
        } catch (Exception e) {
            return "Tidak bisa menyalin gambar: " + e.getMessage();
        }
    }

    private static String tulis(File target, String isi) {
        try {
            java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(target), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            return null;
        } catch (Exception e) {
            return "Tidak bisa menulis " + target.getName() + ": " + e.getMessage();
        }
    }
}
