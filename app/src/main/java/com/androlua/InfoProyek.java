package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pembantu baca info proyek dari init.lua (Tahap 3F).
 *
 * Dipakai layar-layar baru (Tema, Animasi, Cloud, Backend) untuk memilih
 * proyek tanpa lewat jembatan Lua: cukup memindai folder Download/project
 * dan membaca kunci init.lua. Format init.lua: kunci="nilai" atau
 * kunci=true/false, dengan komentar Lua (-- ...) di belakang nilai.
 *
 * Murni java.io/java.util (tanpa API Android) supaya bisa diuji ulang
 * di JRE biasa.
 */
public class InfoProyek {

    /**
     * Daftar nama folder proyek (berisi init.lua) di Download/project, urut abjad.
     * Proyek Lua DISEMBUNYIKAN (keputusan final al kautsar 2026-11-17 00:16:
     * Lua tidak dipakai lagi — proyek Lua lama tidak ditampilkan di daftar).
     * Berkasnya di penyimpanan TIDAK dihapus, hanya tidak ditampilkan.
     */
    public static List<String> daftarNama(File dirRoot) {
        List<String> hasil = new ArrayList<String>();
        if (dirRoot == null || !dirRoot.isDirectory()) return hasil;
        File[] daftar = dirRoot.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            if (daftar[i].isDirectory()
                    && new File(daftar[i], "init.lua").isFile()
                    && !"lua".equals(tipe(daftar[i]))) {
                hasil.add(daftar[i].getName());
            }
        }
        Collections.sort(hasil, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.toLowerCase().compareTo(b.toLowerCase());
            }
        });
        return hasil;
    }

    /**
     * Daftar proyek dalam format yang sama dengan jembatan Lua daftarProyek
     * (restrukturisasi tanpa Lua): satu baris per proyek
     * "nama|packagename|appver|tipe", dipisah "\n". Nilai yang tidak terbaca
     * diganti "?" (sama seperti Lua); tipe yang tidak terbaca = "java".
     * Membaca init.lua sebagai teks (tanpa menjalankan Lua).
     * Proyek Lua tidak ikut (disembunyikan di daftarNama).
     */
    public static String daftar(File dirRoot) {
        StringBuilder sb = new StringBuilder();
        List<String> nama = daftarNama(dirRoot);
        for (int i = 0; i < nama.size(); i++) {
            String n = nama.get(i);
            File dir = new File(dirRoot, n);
            String pkg = namaPaket(dir);
            if (pkg == null || pkg.isEmpty()) pkg = "?";
            String ver = bacaKunci(dir, "appver", "");
            if (ver == null || ver.isEmpty()) ver = "?";
            String t = tipe(dir);
            if (sb.length() > 0) sb.append('\n');
            sb.append(n).append('|').append(pkg).append('|')
                    .append(ver).append('|').append(t);
        }
        return sb.toString();
    }

    /** Jenis proyek (java/html/situs/kotlin/compose/python); "java" bila tidak terbaca (Lua dihapus). */
    public static String tipe(File dirProyek) {
        return bacaKunci(dirProyek, "tipe", "java");
    }

    /** Nama paket proyek; "" bila tidak terbaca. */
    public static String namaPaket(File dirProyek) {
        return bacaKunci(dirProyek, "packagename", "");
    }

    /** Nama aplikasi proyek; nama folder bila tidak terbaca. */
    public static String namaAplikasi(File dirProyek) {
        String n = bacaKunci(dirProyek, "appname", "");
        if (n != null && !n.isEmpty()) return n;
        if (dirProyek != null) return dirProyek.getName();
        return "";
    }

    /**
     * Baca satu kunci dari init.lua proyek. Nilai string ("...") diambil
     * isinya; nilai boolean/angka diambil apa adanya. Komentar Lua (-- ...)
     * dibuang dulu supaya kata di komentar tidak ikut terbaca (pelajaran
     * dari bug 3C: "lisensi=false -- true = ..." terbaca sebagai true).
     */
    public static String bacaKunci(File dirProyek, String kunci, String bawaan) {
        if (dirProyek == null || kunci == null) return bawaan;
        File init = new File(dirProyek, "init.lua");
        if (!init.isFile()) return bawaan;
        String isi = bacaBerkas(init);
        if (isi == null) return bawaan;
        return bacaKunciTeks(isi, kunci, bawaan);
    }

    /** Versi teks dari bacaKunci (untuk pengujian di JRE). */
    public static String bacaKunciTeks(String isiInitLua, String kunci, String bawaan) {
        if (isiInitLua == null || kunci == null) return bawaan;
        String[] baris = isiInitLua.split("\n", -1);
        for (int i = 0; i < baris.length; i++) {
            String b = buangKomentar(baris[i]).trim();
            if (b.isEmpty()) continue;
            int samaDengan = b.indexOf('=');
            if (samaDengan < 0) continue;
            String k = b.substring(0, samaDengan).trim();
            if (!kunci.equals(k)) continue;
            String nilai = b.substring(samaDengan + 1).trim();
            // Buang koma di akhir (bila init.lua menulis "kunci = nilai,").
            if (nilai.endsWith(",")) nilai = nilai.substring(0, nilai.length() - 1).trim();
            if (nilai.length() >= 2 && nilai.startsWith("\"") && nilai.endsWith("\"")) {
                return nilai.substring(1, nilai.length() - 1);
            }
            return nilai;
        }
        return bawaan;
    }

    /**
     * Buang komentar Lua (-- ...) dari satu baris, dengan memperhatikan
     * string berpetik ganda supaya "--" di dalam nilai tidak terpotong.
     */
    public static String buangKomentar(String baris) {
        if (baris == null) return "";
        boolean diString = false;
        for (int i = 0; i + 1 < baris.length(); i++) {
            char c = baris.charAt(i);
            if (c == '"' && (i == 0 || baris.charAt(i - 1) != '\\')) {
                diString = !diString;
            }
            if (!diString && c == '-' && baris.charAt(i + 1) == '-') {
                return baris.substring(0, i);
            }
        }
        return baris;
    }

    /** True bila nilai kunci init.lua adalah true (setelah komentar dibuang). */
    public static boolean kunciTrue(File dirProyek, String kunci) {
        return "true".equals(bacaKunci(dirProyek, kunci, "false"));
    }

    static String bacaBerkas(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            try {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            } finally {
                r.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
