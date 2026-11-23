package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pembuat Aplikasi Wear OS & TV (Tahap 3I).
 *
 * Menyesuaikan proyek Java yang sudah ada (dibuat lewat
 * BuatProyekHelper) menjadi proyek Wear OS dan/atau Android TV:
 * - Wear OS: uses-feature android.hardware.type.watch + layout layar
 *   kecil (teks besar, tombol di tengah, ramah layar bundar/persegi).
 * - Android TV: uses-feature android.hardware.type.television +
 *   kategori LEANBACK_LAUNCHER di intent-filter launcher + atribut
 *   android:banner di <application> + layout layar besar (teks besar,
 *   tombol bisa difokus, ramah navigasi D-pad).
 * - Keduanya: kedua penyesuaian di atas (satu APK yang bisa jalan di
 *   jam dan TV — manifest memakai required="false" supaya APK tetap
 *   bisa dipasang di HP biasa).
 * Hasil penyesuaian ditulis ke berkas proyek (manifest + layout) dan
 * dijelaskan di berkas wear-tv.txt di akar proyek.
 *
 * KETERBATASAN YANG JUJUR (ditulis juga di WearTvActivity dan di
 * wear-tv.txt): ini TEMPLATE PROYEK — build + uji tetap butuh
 * perangkat/emulator Wear OS atau Android TV; banner TV
 * (@drawable/banner, 320x180) TIDAK dibuat otomatis karena butuh
 * gambar bitmap — buat sendiri (mis. lewat Pembuat Ikon) lalu simpan
 * sebagai app/src/main/res/drawable/banner.png; layout yang ditulis
 * adalah activity_main.xml bawaan proyek (layout lama ditimpa).
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JRE biasa.
 */
public class ProyekWearTv {

    /** Target Wear OS saja. */
    public static final String TARGET_WEAR = "wear";

    /** Target Android TV saja. */
    public static final String TARGET_TV = "tv";

    /** Target Wear OS + Android TV. */
    public static final String TARGET_KEDUA = "keduanya";

    /** Semua target yang dikenal. */
    public static final String[] TARGET = {TARGET_WEAR, TARGET_TV, TARGET_KEDUA};

    /** Nama tampil target. */
    public static String namaTarget(String target) {
        if (TARGET_WEAR.equals(target)) return "Wear OS (jam)";
        if (TARGET_TV.equals(target)) return "Android TV";
        if (TARGET_KEDUA.equals(target)) return "Wear OS + Android TV";
        return "Tidak dikenal";
    }

    /** True bila target dikenal. */
    public static boolean targetValid(String target) {
        return TARGET_WEAR.equals(target) || TARGET_TV.equals(target)
                || TARGET_KEDUA.equals(target);
    }

    /** Jalur manifest proyek (relatif dari akar proyek). */
    public static final String JALUR_MANIFEST = "app/src/main/AndroidManifest.xml";

    /** Jalur layout utama proyek (relatif dari akar proyek). */
    public static final String JALUR_LAYOUT = "app/src/main/res/layout/activity_main.xml";

    /** Nama berkas panduan di akar proyek. */
    public static final String NAMA_PANDUAN = "wear-tv.txt";

    /**
     * Terapkan penyesuaian Wear/TV ke proyek. Mengembalikan null bila
     * berhasil, atau pesan galat.
     */
    public static String terapkan(File dirProyek, String target) {
        if (!targetValid(target)) return "Target tidak dikenal: " + target;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return TeksUI.t("Folder proyek tidak ditemukan.");
        }
        File manifes = new File(dirProyek, JALUR_MANIFEST);
        if (!manifes.isFile()) {
            return "AndroidManifest.xml tidak ditemukan di proyek ini.";
        }
        String isi = InfoProyek.bacaBerkas(manifes);
        if (isi == null) return "Gagal membaca AndroidManifest.xml.";
        boolean mauWear = TARGET_WEAR.equals(target) || TARGET_KEDUA.equals(target);
        boolean mauTv = TARGET_TV.equals(target) || TARGET_KEDUA.equals(target);
        if (mauWear) {
            isi = sisipUsesFeature(isi, "android.hardware.type.watch");
        }
        if (mauTv) {
            isi = sisipUsesFeature(isi, "android.hardware.type.television");
            isi = sisipLeanback(isi);
            isi = sisipBanner(isi);
        }
        try {
            tulisTeks(manifes, isi);
        } catch (Exception e) {
            return "Gagal menulis AndroidManifest.xml: " + e.getMessage();
        }
        String nama = InfoProyek.namaAplikasi(dirProyek);
        File layout = new File(dirProyek, JALUR_LAYOUT);
        String isiLayout = mauTv ? layoutTv(nama) : layoutWear(nama);
        try {
            tulisTeks(layout, isiLayout);
        } catch (Exception e) {
            return "Gagal menulis layout: " + e.getMessage();
        }
        try {
            tulisTeks(new File(dirProyek, NAMA_PANDUAN), panduan(target, nama));
        } catch (Exception e) {
            return "Gagal menulis panduan: " + e.getMessage();
        }
        return null;
    }

    /**
     * Sisipkan <uses-feature android:name="..." android:required="false" />
     * sebelum "<application". Idempoten (bila sudah ada, tidak digandakan).
     */
    public static String sisipUsesFeature(String manifes, String namaFitur) {
        if (manifes == null) return manifes;
        if (manifes.contains(namaFitur)) return manifes;
        String sisipan = "    <uses-feature\n"
                + "        android:name=\"" + namaFitur + "\"\n"
                + "        android:required=\"false\" />\n\n";
        int i = manifes.indexOf("    <application");
        if (i < 0) i = manifes.indexOf("<application");
        if (i < 0) return manifes;
        return manifes.substring(0, i) + sisipan + manifes.substring(i);
    }

    /**
     * Tambahkan kategori LEANBACK_LAUNCHER sesudah kategori LAUNCHER
     * pertama di manifest (intent-filter launcher MainActivity proyek). Idempoten.
     */
    public static String sisipLeanback(String manifes) {
        if (manifes == null) return manifes;
        if (manifes.contains("LEANBACK_LAUNCHER")) return manifes;
        String cari = "android.intent.category.LAUNCHER";
        int i = manifes.indexOf(cari);
        if (i < 0) return manifes;
        int akhirBaris = manifes.indexOf('\n', i);
        if (akhirBaris < 0) return manifes;
        String sisipan = "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\" />\n";
        return manifes.substring(0, akhirBaris + 1) + sisipan
                + manifes.substring(akhirBaris + 1);
    }

    /**
     * Tambahkan atribut android:banner="@drawable/banner" ke tag
     * <application (baris yang sama dengan "<application"). Idempoten.
     */
    public static String sisipBanner(String manifes) {
        if (manifes == null) return manifes;
        if (manifes.contains("android:banner")) return manifes;
        int i = manifes.indexOf("<application");
        if (i < 0) return manifes;
        int akhirBaris = manifes.indexOf('\n', i);
        if (akhirBaris < 0) return manifes;
        String sisipan = "        android:banner=\"@drawable/banner\"\n";
        return manifes.substring(0, akhirBaris + 1) + sisipan
                + manifes.substring(akhirBaris + 1);
    }

    /** Layout activity_main.xml untuk Wear OS (layar kecil). */
    public static String layoutWear(String namaAplikasi) {
        String nama = namaAplikasi == null ? TeksUI.t("Aplikasi") : namaAplikasi;
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- Layout Wear OS (Tahap 3I): layar kecil, teks besar, tombol di tengah.\n"
                + "     Ramah layar bundar maupun persegi. -->\n"
                + "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:background=\"#121212\">\n"
                + "    <LinearLayout\n"
                + "        android:layout_width=\"match_parent\"\n"
                + "        android:layout_height=\"match_parent\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        android:gravity=\"center\"\n"
                + "        android:padding=\"16dp\">\n"
                + "        <TextView\n"
                + "            android:id=\"@+id/teksJudul\"\n"
                + "            android:layout_width=\"wrap_content\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"" + escapeXml(nama) + "\"\n"
                + "            android:textSize=\"24sp\"\n"
                + "            android:textColor=\"#FFFFFF\" />\n"
                + "        <Button\n"
                + "            android:id=\"@+id/tombolUtama\"\n"
                + "            android:layout_width=\"wrap_content\"\n"
                + "            android:layout_height=\"wrap_content\"\n"
                + "            android:text=\"Mulai\"\n"
                + "            android:textSize=\"18sp\" />\n"
                + "    </LinearLayout>\n"
                + "</FrameLayout>\n";
    }

    /** Layout activity_main.xml untuk Android TV (layar besar, D-pad). */
    public static String layoutTv(String namaAplikasi) {
        String nama = namaAplikasi == null ? TeksUI.t("Aplikasi") : namaAplikasi;
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- Layout Android TV (Tahap 3I): teks besar, tombol bisa difokus,\n"
                + "     ramah navigasi D-pad (tombol arah remote). -->\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\"\n"
                + "    android:gravity=\"center\"\n"
                + "    android:padding=\"48dp\"\n"
                + "    android:background=\"#121212\">\n"
                + "    <TextView\n"
                + "        android:id=\"@+id/teksJudul\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"" + escapeXml(nama) + "\"\n"
                + "        android:textSize=\"32sp\"\n"
                + "        android:textColor=\"#FFFFFF\" />\n"
                + "    <Button\n"
                + "        android:id=\"@+id/tombolUtama\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Mulai\"\n"
                + "        android:textSize=\"24sp\"\n"
                + "        android:focusable=\"true\"\n"
                + "        android:focusableInTouchMode=\"true\"\n"
                + "        android:nextFocusDown=\"@+id/tombolKedua\" />\n"
                + "    <Button\n"
                + "        android:id=\"@+id/tombolKedua\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Pengaturan\"\n"
                + "        android:textSize=\"24sp\"\n"
                + "        android:focusable=\"true\"\n"
                + "        android:focusableInTouchMode=\"true\"\n"
                + "        android:nextFocusUp=\"@+id/tombolUtama\" />\n"
                + "</LinearLayout>\n";
    }

    /** Isi berkas panduan wear-tv.txt di akar proyek. */
    public static String panduan(String target, String namaAplikasi) {
        String nama = namaAplikasi == null ? TeksUI.t("Aplikasi") : namaAplikasi;
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Proyek ")).append(nama).append(" — target: ")
                .append(namaTarget(target)).append("\n\n");
        sb.append("Yang sudah diubah IDE:\n");
        if (TARGET_WEAR.equals(target) || TARGET_KEDUA.equals(target)) {
            sb.append("- Manifest: uses-feature android.hardware.type.watch (required=false).\n");
        }
        if (TARGET_TV.equals(target) || TARGET_KEDUA.equals(target)) {
            sb.append("- Manifest: uses-feature android.hardware.type.television (required=false).\n");
            sb.append("- Manifest: kategori LEANBACK_LAUNCHER di intent-filter launcher.\n");
            sb.append("- Manifest: atribut android:banner=\"@drawable/banner\" di <application>.\n");
        }
        sb.append("- Layout: app/src/main/res/layout/activity_main.xml ditulis ulang\n");
        sb.append("  (Wear: teks besar di tengah; TV: teks besar + tombol bisa difokus D-pad).\n\n");
        sb.append("Yang masih harus dilakukan sendiri:\n");
        sb.append("1. Banner TV: buat gambar banner 320x180 (mis. lewat Pembuat Ikon),\n");
        sb.append("   simpan sebagai app/src/main/res/drawable/banner.png.\n");
        sb.append("   Tanpa banner.png, build TV bisa gagal atau banner kosong.\n");
        sb.append("2. Bangun APK seperti biasa (menu Bangun di proyek).\n");
        sb.append("3. Uji di perangkat/emulator Wear OS atau Android TV sungguhan —\n");
        sb.append("   IDE tidak punya emulator, jadi tampilan di jam/TV belum terbukti.\n\n");
        sb.append("CATATAN JUJUR: ini template proyek, bukan aplikasi Wear/TV yang\n");
        sb.append("sudah teruji. Kode MainActivity proyek tetap kode bawaan (Java) —\n");
        sb.append("tombol di layout memakai id teksJudul, tombolUtama, dan (TV)\n");
        sb.append("tombolKedua; sambungkan sendiri di MainActivity bila perlu.\n");
        return sb.toString();
    }

    /** Escape & < > " untuk nilai atribut XML. */
    public static String escapeXml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else if (c == '"') sb.append("&quot;");
            else sb.append(c);
        }
        return sb.toString();
    }

    static void tulisTeks(File f, String isi) throws Exception {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(isi);
        } finally {
            w.close();
        }
    }
}
