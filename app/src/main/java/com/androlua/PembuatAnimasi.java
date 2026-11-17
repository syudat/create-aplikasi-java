package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pembuat Animasi (Tahap 3F).
 *
 * Membuat berkas animasi SUNGGUHAN untuk proyek yang dipilih:
 * - Proyek Java/Kotlin/Compose/Lua: berkas res/anim/anim_&lt;jenis&gt;.xml
 *   (animasi view Android: alpha/translate/rotate/scale) + contoh kode
 *   pemakaian (AnimationUtils.loadAnimation + startAnimation).
 * - Proyek HTML/Situs Web: berkas assets/animasi.css (@keyframes) +
 *   contoh pemakaian CSS.
 *
 * Lima jenis: Fade (memudar), Geser, Putar, Pantul (membesar dari kecil),
 * Berdenyut (membesar-mengecil berulang). Durasi dijepit 100..10000 ms
 * (bawaan 500 ms). Target animasi: tombol, teks, atau gambar — dipakai
 * di contoh kode sebagai nama variabel/kelas.
 *
 * Murni java.io/java.util (tanpa API Android) supaya bisa diuji ulang
 * di JRE biasa; XML hasil diverifikasi aapt2 compile di suite uji.
 */
public class PembuatAnimasi {

    /** Satu jenis animasi. */
    public static class Jenis {
        public final String id;
        public final String nama;
        public final String penjelasan;
        public Jenis(String id, String nama, String penjelasan) {
            this.id = id;
            this.nama = nama;
            this.penjelasan = penjelasan;
        }
    }

    public static final Jenis[] SEMUA = {
        new Jenis("fade", "Fade (memudar)",
                "Tampilan muncul perlahan dari transparan menjadi jelas."),
        new Jenis("geser", "Geser",
                "Tampilan bergeser masuk dari kiri ke posisinya."),
        new Jenis("putar", "Putar",
                "Tampilan berputar satu putaran penuh di tempatnya."),
        new Jenis("pantul", "Pantul",
                "Tampilan membesar dari setengah ukuran ke ukuran penuh."),
        new Jenis("berdenyut", "Berdenyut",
                "Tampilan membesar-mengecil berulang (seperti detak)."),
    };

    /** Target animasi yang bisa dipilih pengguna. */
    public static final String[] TARGET = {"tombol", "teks", "gambar"};

    public static Jenis cari(String id) {
        if (id == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    /** Durasi dijepit ke 100..10000 ms; bukan angka -> 500 ms. */
    public static int jepitDurasi(int ms) {
        if (ms < 100) return 100;
        if (ms > 10000) return 10000;
        return ms;
    }

    /** Nama berkas animasi: "anim_fade.xml". */
    public static String namaBerkas(String jenis) {
        return "anim_" + jenis + ".xml";
    }

    /**
     * Membuat animasi untuk proyek. Proyek HTML/Situs Web mendapat
     * animasi.css (CSS @keyframes); proyek lain mendapat berkas
     * res/anim/anim_&lt;jenis&gt;.xml. Mengembalikan null bila berhasil,
     * atau pesan galat.
     */
    public static String buatAnimasi(File dirProyek, String jenisId, int durasiMs) {
        Jenis j = cari(jenisId);
        if (j == null) return "Jenis animasi tidak dikenal: " + jenisId;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        int durasi = jepitDurasi(durasiMs);
        String tipe = InfoProyek.tipe(dirProyek);
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            File assets = new File(dirProyek, "app/src/main/assets");
            assets.mkdirs();
            return tulisBerkas(new File(assets, "animasi.css"), cssUntuk(j, durasi));
        }
        File anim = new File(dirProyek, "app/src/main/res/anim");
        anim.mkdirs();
        return tulisBerkas(new File(anim, namaBerkas(j.id)), xmlUntuk(j, durasi));
    }

    /** Isi berkas res/anim/anim_&lt;jenis&gt;.xml. */
    public static String xmlUntuk(Jenis j, int durasiMs) {
        int d = jepitDurasi(durasiMs);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!-- Animasi ").append(j.nama).append(" (dibuat oleh Pembuat Animasi) -->\n");
        if ("fade".equals(j.id)) {
            sb.append("<alpha xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            sb.append("    android:fromAlpha=\"0.0\"\n");
            sb.append("    android:toAlpha=\"1.0\"\n");
            sb.append("    android:duration=\"").append(d).append("\" />\n");
        } else if ("geser".equals(j.id)) {
            sb.append("<translate xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            sb.append("    android:fromXDelta=\"-100%p\"\n");
            sb.append("    android:toXDelta=\"0\"\n");
            sb.append("    android:fromYDelta=\"0\"\n");
            sb.append("    android:toYDelta=\"0\"\n");
            sb.append("    android:duration=\"").append(d).append("\" />\n");
        } else if ("putar".equals(j.id)) {
            sb.append("<rotate xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            sb.append("    android:fromDegrees=\"0\"\n");
            sb.append("    android:toDegrees=\"360\"\n");
            sb.append("    android:pivotX=\"50%\"\n");
            sb.append("    android:pivotY=\"50%\"\n");
            sb.append("    android:duration=\"").append(d).append("\" />\n");
        } else if ("pantul".equals(j.id)) {
            sb.append("<scale xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            sb.append("    android:fromXScale=\"0.5\"\n");
            sb.append("    android:fromYScale=\"0.5\"\n");
            sb.append("    android:toXScale=\"1.0\"\n");
            sb.append("    android:toYScale=\"1.0\"\n");
            sb.append("    android:pivotX=\"50%\"\n");
            sb.append("    android:pivotY=\"50%\"\n");
            sb.append("    android:duration=\"").append(d).append("\" />\n");
        } else { // berdenyut
            sb.append("<set xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            sb.append("    android:shareInterpolator=\"true\">\n");
            sb.append("    <scale\n");
            sb.append("        android:fromXScale=\"1.0\"\n");
            sb.append("        android:fromYScale=\"1.0\"\n");
            sb.append("        android:toXScale=\"1.15\"\n");
            sb.append("        android:toYScale=\"1.15\"\n");
            sb.append("        android:pivotX=\"50%\"\n");
            sb.append("        android:pivotY=\"50%\"\n");
            sb.append("        android:duration=\"").append(d).append("\"\n");
            sb.append("        android:repeatCount=\"infinite\"\n");
            sb.append("        android:repeatMode=\"reverse\" />\n");
            sb.append("</set>\n");
        }
        return sb.toString();
    }

    /** Isi animasi.css untuk proyek HTML/Situs Web. */
    public static String cssUntuk(Jenis j, int durasiMs) {
        int d = jepitDurasi(durasiMs);
        StringBuilder sb = new StringBuilder();
        sb.append("/* Animasi ").append(j.nama).append(" (dibuat oleh Pembuat Animasi) */\n");
        sb.append("/* Pakai: class=\"anim-").append(j.id).append("\" pada elemen yang mau dianimasikan. */\n");
        if ("fade".equals(j.id)) {
            sb.append("@keyframes anim-fade { from { opacity: 0; } to { opacity: 1; } }\n");
            sb.append(".anim-fade { animation: anim-fade ").append(d).append("ms ease; }\n");
        } else if ("geser".equals(j.id)) {
            sb.append("@keyframes anim-geser { from { transform: translateX(-100%); } to { transform: translateX(0); } }\n");
            sb.append(".anim-geser { animation: anim-geser ").append(d).append("ms ease; }\n");
        } else if ("putar".equals(j.id)) {
            sb.append("@keyframes anim-putar { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }\n");
            sb.append(".anim-putar { animation: anim-putar ").append(d).append("ms linear; }\n");
        } else if ("pantul".equals(j.id)) {
            sb.append("@keyframes anim-pantul { from { transform: scale(0.5); } to { transform: scale(1); } }\n");
            sb.append(".anim-pantul { animation: anim-pantul ").append(d).append("ms ease; }\n");
        } else { // berdenyut
            sb.append("@keyframes anim-berdenyut { 0% { transform: scale(1); } 50% { transform: scale(1.15); } 100% { transform: scale(1); } }\n");
            sb.append(".anim-berdenyut { animation: anim-berdenyut ").append(d).append("ms ease-in-out infinite; }\n");
        }
        return sb.toString();
    }

    /**
     * Contoh kode pemakaian animasi untuk tipe proyek. target adalah
     * "tombol"/"teks"/"gambar" — dipakai sebagai nama variabel (Java/
     * Kotlin/Lua) atau nama kelas (HTML). Ganti nama variabelnya dengan
     * nama tampilanmu sendiri.
     */
    public static String contohKode(String tipeProyek, String jenisId, String target) {
        Jenis j = cari(jenisId);
        if (j == null) return "";
        String namaAnim = "anim_" + j.id;
        String varTarget = "tombol";
        if ("teks".equals(target)) varTarget = "teksJudul";
        else if ("gambar".equals(target)) varTarget = "gambar";
        if ("html".equals(tipeProyek) || "situs".equals(tipeProyek)) {
            return "// Di index.html, sertakan animasi.css lalu beri kelas pada elemen:\n"
                    + "// <link rel=\"stylesheet\" href=\"animasi.css\">\n"
                    + "// <button class=\"anim-" + j.id + "\">Tombol</button>";
        }
        if ("kotlin".equals(tipeProyek) || "compose".equals(tipeProyek)) {
            if ("compose".equals(tipeProyek)) {
                return "// Compose memakai animasi bawaan Compose, bukan berkas XML.\n"
                        + "// Contoh sederhana (tampilkan perlahan):\n"
                        + "// var tampil by remember { mutableStateOf(false) }\n"
                        + "// val alpha by animateFloatAsState(if (tampil) 1f else 0f)\n"
                        + "// Text(\"Halo\", Modifier.alpha(alpha))";
            }
            return "val anim = AnimationUtils.loadAnimation(this, R.anim." + namaAnim + ")\n"
                    + varTarget + ".startAnimation(anim)";
        }
        if ("lua".equals(tipeProyek)) {
            return "local anim = luajava.bindClass(\"android.view.animation.AnimationUtils\")"
                    + ":loadAnimation(activity, R.anim." + namaAnim + ")\n"
                    + varTarget + ":startAnimation(anim)";
        }
        // java (bawaan)
        return "Animation anim = AnimationUtils.loadAnimation(this, R.anim." + namaAnim + ");\n"
                + varTarget + ".startAnimation(anim);";
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
