package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pembuat Widget (Tahap 3G).
 *
 * Membuat widget layar utama sungguhan untuk proyek yang dipilih:
 * (1) kelas AppWidgetProvider (WidgetSaya.java) di paket proyek,
 * (2) layout widget (res/layout/widget_saya.xml),
 * (3) info widget (res/xml/widget_info.xml),
 * (4) cuplikan receiver untuk AndroidManifest.xml proyek
 * (tambahKeManifest menyisipkannya sebelum </application>).
 *
 * Widget menampilkan teks yang diisi pengguna (bawaan: nama aplikasi)
 * dan bisa diketuk untuk membuka aplikasi. Teks widget dibaca dari
 * strings.xml proyek (widget_teks) bila ada.
 *
 * Murni Java (java.io, tanpa API Android) supaya bisa diuji ulang di JRE.
 * Berkas hasil adalah kode/XML proyek pengguna — bukan kode IDE.
 */
public class PembuatWidget {

    /** Nama kelas widget. */
    public static final String NAMA_KELAS = "WidgetSaya";

    /** Nama layout widget. */
    public static final String NAMA_LAYOUT = "widget_saya";

    /** Cuplikan receiver untuk AndroidManifest.xml proyek. */
    public static String cuplikanManifest(String paket) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <!-- Widget layar utama — dibuat oleh Create Aplikasi. -->\n");
        sb.append("        <receiver android:name=\"").append(paket).append(".WidgetSaya\"\n");
        sb.append("            android:exported=\"false\"\n");
        sb.append("            android:label=\"Widget Saya\">\n");
        sb.append("            <intent-filter>\n");
        sb.append("                <action android:name=\"android.appwidget.action.APPWIDGET_UPDATE\" />\n");
        sb.append("            </intent-filter>\n");
        sb.append("            <meta-data\n");
        sb.append("                android:name=\"android.appwidget.provider\"\n");
        sb.append("                android:resource=\"@xml/widget_info\" />\n");
        sb.append("        </receiver>\n");
        return sb.toString();
    }

    /**
     * Membuat semua berkas widget di proyek. Mengembalikan null bila
     * berhasil, atau pesan galat (bahasa Indonesia) bila gagal.
     */
    public static String buatWidget(File dirProyek, String paket, String teks) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        if (paket == null || paket.length() == 0) {
            return "Nama paket proyek tidak ditemukan.";
        }
        if (teks == null || teks.trim().length() == 0) teks = "Aplikasi Saya";
        String jalurPaket = paket.replace('.', '/');
        File dirJava = new File(dirProyek, "app/src/main/java/" + jalurPaket);
        File dirLayout = new File(dirProyek, "app/src/main/res/layout");
        File dirXml = new File(dirProyek, "app/src/main/res/xml");
        if (!dirJava.mkdirs() && !dirJava.isDirectory()) {
            return "Tidak bisa membuat folder " + dirJava.getPath() + ".";
        }
        if (!dirLayout.mkdirs() && !dirLayout.isDirectory()) {
            return "Tidak bisa membuat folder " + dirLayout.getPath() + ".";
        }
        if (!dirXml.mkdirs() && !dirXml.isDirectory()) {
            return "Tidak bisa membuat folder " + dirXml.getPath() + ".";
        }
        String galat;
        galat = tulis(new File(dirJava, NAMA_KELAS + ".java"), kodeWidget(paket, teks));
        if (galat != null) return galat;
        galat = tulis(new File(dirLayout, NAMA_LAYOUT + ".xml"), layoutWidget());
        if (galat != null) return galat;
        galat = tulis(new File(dirXml, "widget_info.xml"), infoWidget());
        if (galat != null) return galat;
        return null;
    }

    /**
     * Menyisipkan receiver widget ke AndroidManifest.xml proyek (sebelum
     * </application>). Bila sudah ada, tidak diubah lagi. Mengembalikan
     * null bila berhasil, atau pesan galat.
     */
    public static String tambahKeManifest(File manifest, String paket) {
        if (manifest == null || !manifest.isFile()) {
            return "AndroidManifest.xml proyek tidak ditemukan.";
        }
        String isi = baca(manifest);
        if (isi == null) return "Tidak bisa membaca AndroidManifest.xml proyek.";
        if (isi.contains("WidgetSaya")) return null;
        int pos = isi.lastIndexOf("</application>");
        if (pos < 0) return "AndroidManifest.xml proyek tidak berisi </application>.";
        String baru = isi.substring(0, pos) + cuplikanManifest(paket)
                + isi.substring(pos);
        String galat = tulis(manifest, baru);
        return galat;
    }

    /** Kode AppWidgetProvider untuk proyek pengguna. */
    public static String kodeWidget(String paket, String teks) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(paket).append(";\n\n");
        sb.append("import android.app.PendingIntent;\n");
        sb.append("import android.appwidget.AppWidgetManager;\n");
        sb.append("import android.appwidget.AppWidgetProvider;\n");
        sb.append("import android.content.Context;\n");
        sb.append("import android.content.Intent;\n");
        sb.append("import android.widget.RemoteViews;\n\n");
        sb.append("/** Widget layar utama — dibuat otomatis oleh Create Aplikasi. */\n");
        sb.append("public class WidgetSaya extends AppWidgetProvider {\n\n");
        sb.append("    @Override\n");
        sb.append("    public void onUpdate(Context context, AppWidgetManager manager,\n");
        sb.append("            int[] widgetIds) {\n");
        sb.append("        for (int i = 0; i < widgetIds.length; i++) {\n");
        sb.append("            RemoteViews tampilan = new RemoteViews(context.getPackageName(),\n");
        sb.append("                    R.layout.widget_saya);\n");
        sb.append("            tampilan.setTextViewText(R.id.teks_widget, \"")
                .append(lolosJava(teks)).append("\");\n");
        sb.append("            Intent buka = new Intent(context, MainActivity.class);\n");
        sb.append("            PendingIntent ketuk = PendingIntent.getActivity(context, 0,\n");
        sb.append("                    buka, PendingIntent.FLAG_IMMUTABLE);\n");
        sb.append("            tampilan.setOnClickPendingIntent(R.id.widget_akar, ketuk);\n");
        sb.append("            manager.updateAppWidget(widgetIds[i], tampilan);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Layout widget (teks di tengah, latar warna primer). */
    public static String layoutWidget() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Widget layar utama — dibuat otomatis oleh Create Aplikasi. -->\n");
        sb.append("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:id=\"@+id/widget_akar\"\n");
        sb.append("    android:layout_width=\"match_parent\"\n");
        sb.append("    android:layout_height=\"match_parent\"\n");
        sb.append("    android:background=\"@color/colorPrimary\"\n");
        sb.append("    android:gravity=\"center\"\n");
        sb.append("    android:orientation=\"vertical\"\n");
        sb.append("    android:padding=\"12dp\">\n\n");
        sb.append("    <TextView\n");
        sb.append("        android:id=\"@+id/teks_widget\"\n");
        sb.append("        android:layout_width=\"wrap_content\"\n");
        sb.append("        android:layout_height=\"wrap_content\"\n");
        sb.append("        android:text=\"Widget Saya\"\n");
        sb.append("        android:textColor=\"#FFFFFF\"\n");
        sb.append("        android:textSize=\"18sp\"\n");
        sb.append("        android:textStyle=\"bold\" />\n\n");
        sb.append("</LinearLayout>\n");
        return sb.toString();
    }

    /** Info widget (ukuran, tata letak awal, kategori). */
    public static String infoWidget() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Info widget — dibuat otomatis oleh Create Aplikasi. -->\n");
        sb.append("<appwidget-provider xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    android:minWidth=\"180dp\"\n");
        sb.append("    android:minHeight=\"110dp\"\n");
        sb.append("    android:updatePeriodMillis=\"0\"\n");
        sb.append("    android:initialLayout=\"@layout/widget_saya\"\n");
        sb.append("    android:widgetCategory=\"home_screen\" />\n");
        return sb.toString();
    }

    private static String lolosJava(String teks) {
        return teks.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String tulis(File target, String isi) {
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(target), "UTF-8");
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

    private static String baca(File f) {
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                int n = 0;
                int r;
                while ((r = in.read(data, n, data.length - n)) > 0) n += r;
                return new String(data, 0, n, "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
