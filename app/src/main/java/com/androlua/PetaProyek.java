package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Peta Proyek (Tahap 3I).
 *
 * Memindai seluruh berkas proyek lalu menghasilkan peta struktur:
 * daftar folder + berkas + ukuran + hubungan antar berkas (berkas
 * utama, manifest, layout, strings, ikon, index.html) — sebagai teks
 * yang bisa ditampilkan di layar maupun disimpan sebagai
 * peta-proyek.txt di akar proyek.
 *
 * Folder bin/ (hasil build) dan .git/ (repo lokal) TIDAK ikut dipetakan
 * karena isinya bisa dibuat ulang; pengecualian itu ditulis di peta.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JRE biasa.
 */
public class PetaProyek {

    /** Nama berkas peta di akar proyek. */
    public static final String NAMA_PETA = "peta-proyek.txt";

    /** Satu berkas di peta: jalan relatif, ukuran, jenis. */
    public static class Entri {
        public final String jalan;
        public final long ukuran;
        public final String jenis;
        public Entri(String jalan, long ukuran, String jenis) {
            this.jalan = jalan;
            this.ukuran = ukuran;
            this.jenis = jenis;
        }
    }

    /** Jumlah + ukuran total untuk satu jenis berkas. */
    public static class Jumlah {
        public int jumlah;
        public long ukuran;
        public Jumlah() {
            jumlah = 0;
            ukuran = 0;
        }
    }

    /** Hasil pemetaan satu proyek. */
    public static class Peta {
        public final long totalUkuran;
        public final int jumlahBerkas;
        public final Map<String, Jumlah> perJenis;
        public final List<Entri> daftar;
        public Peta(long totalUkuran, int jumlahBerkas,
                    Map<String, Jumlah> perJenis, List<Entri> daftar) {
            this.totalUkuran = totalUkuran;
            this.jumlahBerkas = jumlahBerkas;
            this.perJenis = perJenis;
            this.daftar = daftar;
        }
    }

    /** Jenis berkas dari namanya (ekstensi). */
    public static String jenisDariNama(String nama) {
        if (nama == null) return "lain";
        String n = nama.toLowerCase(Locale.US);
        if (n.endsWith(".lua")) return "lua";
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".kt")) return "kotlin";
        if (n.endsWith(".xml")) return "xml";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "html";
        if (n.endsWith(".js")) return "js";
        if (n.endsWith(".css")) return "css";
        if (n.endsWith(".png")) return "png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "jpg";
        if (n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".wav")) return "suara";
        if (n.endsWith(".json")) return "json";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "teks";
        if (n.endsWith(".so")) return "native";
        if (n.endsWith(".jar")) return "jar";
        return "lain";
    }

    /** Petakan proyek: kumpulkan semua berkas (tanpa bin/ dan .git/). */
    public static Peta peta(File dirProyek) {
        List<Entri> daftar = new ArrayList<Entri>();
        Map<String, Jumlah> perJenis = new LinkedHashMap<String, Jumlah>();
        long total = 0;
        if (dirProyek != null && dirProyek.isDirectory()) {
            kumpulkan(dirProyek, dirProyek, "", daftar);
        }
        Collections.sort(daftar, new Comparator<Entri>() {
            @Override
            public int compare(Entri a, Entri b) {
                return a.jalan.compareTo(b.jalan);
            }
        });
        for (int i = 0; i < daftar.size(); i++) {
            Entri e = daftar.get(i);
            total += e.ukuran;
            Jumlah j = perJenis.get(e.jenis);
            if (j == null) {
                j = new Jumlah();
                perJenis.put(e.jenis, j);
            }
            j.jumlah++;
            j.ukuran += e.ukuran;
        }
        return new Peta(total, daftar.size(), perJenis, daftar);
    }

    private static void kumpulkan(File akar, File dir, String awalan, List<Entri> daftar) {
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            File a = anak[i];
            if (a.isDirectory()) {
                if (".git".equals(a.getName())) continue;
                if ("bin".equals(a.getName()) && a.getParentFile().equals(akar)) continue;
                kumpulkan(akar, a, awalan + a.getName() + "/", daftar);
            } else {
                daftar.add(new Entri(awalan + a.getName(), a.length(),
                        jenisDariNama(a.getName())));
            }
        }
    }

    /**
     * Hubungan berkas penting proyek: pasangan {peran, jalan relatif}
     * untuk berkas yang ADA (berkas utama, manifest, layout, strings,
     * ikon, index.html). Berkas utama mengikuti pola
     * Pratinjau.jalurBerkasUtama.
     */
    public static List<String[]> berkasUtama(File dirProyek) {
        List<String[]> hasil = new ArrayList<String[]>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        String utama = Pratinjau.jalurBerkasUtama(dirProyek);
        if (utama != null && new File(dirProyek, utama).isFile()) {
            hasil.add(new String[]{"Berkas utama", utama});
        }
        String[][] peran = {
            {"Manifest", "app/src/main/AndroidManifest.xml"},
            {"Layout utama", "app/src/main/res/layout/activity_main.xml"},
            {"Strings", "app/src/main/res/values/strings.xml"},
            {"Ikon", "app/src/main/res/drawable/icon.png"},
            {"Halaman web", "app/src/main/assets/index.html"},
            {"init.lua", "init.lua"},
        };
        for (int i = 0; i < peran.length; i++) {
            if (new File(dirProyek, peran[i][1]).isFile()) {
                hasil.add(new String[]{peran[i][0], peran[i][1]});
            }
        }
        return hasil;
    }

    /** Teks peta lengkap (pohon + ringkasan + hubungan). */
    public static String teks(File dirProyek) {
        Peta p = peta(dirProyek);
        StringBuilder sb = new StringBuilder();
        sb.append("Peta Proyek: ").append(dirProyek == null ? "" : dirProyek.getName()).append("\n");
        sb.append("Jumlah berkas: ").append(p.jumlahBerkas)
                .append(" | Total ukuran: ").append(formatUkuran(p.totalUkuran)).append("\n");
        sb.append("(Folder bin/ dan .git/ tidak ikut dipetakan.)\n\n");
        sb.append("== Struktur ==\n");
        String folderSebelumnya = "";
        for (int i = 0; i < p.daftar.size(); i++) {
            Entri e = p.daftar.get(i);
            int garis = e.jalan.lastIndexOf('/');
            String folder = garis < 0 ? "" : e.jalan.substring(0, garis);
            if (!folder.equals(folderSebelumnya)) {
                if (!folder.isEmpty()) sb.append(folder).append("/\n");
                folderSebelumnya = folder;
            }
            int dalam = 0;
            for (int j = 0; j < e.jalan.length(); j++) {
                if (e.jalan.charAt(j) == '/') dalam++;
            }
            for (int j = 0; j < dalam; j++) sb.append("  ");
            String nama = garis < 0 ? e.jalan : e.jalan.substring(garis + 1);
            sb.append("- ").append(nama).append(" (").append(formatUkuran(e.ukuran)).append(")\n");
        }
        sb.append("\n== Ukuran per jenis ==\n");
        for (Map.Entry<String, Jumlah> e : p.perJenis.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue().jumlah)
                    .append(" berkas, ").append(formatUkuran(e.getValue().ukuran)).append("\n");
        }
        sb.append("\n== Berkas penting ==\n");
        List<String[]> utama = berkasUtama(dirProyek);
        if (utama.isEmpty()) {
            sb.append("(tidak ada berkas utama yang dikenali)\n");
        } else {
            for (int i = 0; i < utama.size(); i++) {
                sb.append(utama.get(i)[0]).append(": ").append(utama.get(i)[1]).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Tulis peta-proyek.txt ke akar proyek. Mengembalikan berkas bila
     * berhasil, null bila gagal.
     */
    public static File tulisPeta(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        // Hitung teks DULU, sebelum berkas keluar dibuat — kalau tidak,
        // peta akan ikut menghitung peta-proyek.txt itu sendiri (0 byte
        // saat jalan) sehingga isinya berbeda dari teks() yang dibaca
        // sebelum menulis.
        String isi = teks(dirProyek);
        File keluar = new File(dirProyek, NAMA_PETA);
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(keluar), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            return keluar;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Ukuran dalam bentuk mudah dibaca ("1,2 KB", "3,4 MB", koma Indonesia). */
    public static String formatUkuran(long ukuran) {
        if (ukuran < 0) ukuran = 0;
        if (ukuran < 1024) return ukuran + " B";
        if (ukuran < 1024 * 1024) {
            return formatSatuDesimal(ukuran / 1024.0) + " KB";
        }
        if (ukuran < 1024L * 1024 * 1024) {
            return formatSatuDesimal(ukuran / (1024.0 * 1024.0)) + " MB";
        }
        return formatSatuDesimal(ukuran / (1024.0 * 1024.0 * 1024.0)) + " GB";
    }

    private static String formatSatuDesimal(double nilai) {
        long bulat = Math.round(nilai * 10);
        return (bulat / 10) + "," + (bulat % 10);
    }
}
