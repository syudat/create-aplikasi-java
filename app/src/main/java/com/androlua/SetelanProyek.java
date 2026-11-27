package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Setelan proyek yang tersimpan DI DALAM folder proyek, pada berkas
 * "settings.gradle" (meniru settings.gradle di proyek GitHub/Gradle).
 *
 * Isi berkas berupa baris "kunci = nilai" yang mudah dibaca manusia:
 *   arsitekturTerpisah = true/false
 *   tandaTanganPribadi = true/false
 *   antiTamper = true/false
 *   namaKeystore = <nama keystore pribadi terpilih, boleh kosong>
 *   appsdk = <angka>
 *
 * Kelas ini murni Java (tanpa API Android) supaya bisa diuji di komputer.
 * Bendera "ada..." menandai kunci yang benar-benar ada di berkas, supaya
 * pemanggil bisa membedakan "tidak diatur" dari "diatur mati".
 */
public class SetelanProyek {

    /** Nama berkas setelan di akar folder proyek. */
    public static final String NAMA_BERKAS = "settings.gradle";

    /** Isi setelan satu proyek. Nilai bawaan = perilaku lama aplikasi. */
    public static class Setelan {
        public boolean arsitekturTerpisah = false;
        public boolean tandaTanganPribadi = false;
        public boolean antiTamper = false;
        public String namaKeystore = "";
        public int appsdk = 21;
        // Bendera keberadaan kunci di berkas.
        public boolean adaArsitekturTerpisah = false;
        public boolean adaTandaTanganPribadi = false;
        public boolean adaAntiTamper = false;
        public boolean adaNamaKeystore = false;
        public boolean adaAppsdk = false;
        /** True bila berkas settings.gradle memang ada dan terbaca. */
        public boolean dariBerkas = false;
    }

    /** Susun teks berkas dari setelan. antiTamper ditulis hanya bila diminta. */
    public static String teks(Setelan s, boolean tulisAntiTamper) {
        StringBuilder b = new StringBuilder();
        b.append("// settings.gradle - setelan proyek Create Aplikasi.\n");
        b.append("// Berkas ini dibuat dan diperbarui otomatis oleh aplikasi.\n");
        b.append("// Nilai di sini menempel pada proyek ini dan terbaca lagi\n");
        b.append("// setiap proyek dibuka atau dibangun.\n");
        b.append("arsitekturTerpisah = ").append(s.arsitekturTerpisah).append('\n');
        b.append("tandaTanganPribadi = ").append(s.tandaTanganPribadi).append('\n');
        if (tulisAntiTamper) {
            b.append("antiTamper = ").append(s.antiTamper).append('\n');
        }
        b.append("namaKeystore = ").append(s.namaKeystore == null ? "" : s.namaKeystore).append('\n');
        b.append("appsdk = ").append(s.appsdk).append('\n');
        return b.toString();
    }

    /**
     * True bila nilai teks berarti "ya": true/1/ya, tanpa peduli huruf
     * besar-kecil dan spasi. Perbaikan fase 3: sebelumnya hanya "true"
     * persis yang diterima — berkas setelan yang ditulis tangan atau
     * versi lama dengan "True"/"1" diam-diam dibaca sebagai mati.
     */
    public static boolean benar(String nilai) {
        if (nilai == null) return false;
        String n = nilai.trim().toLowerCase();
        return "true".equals(n) || "1".equals(n) || "ya".equals(n);
    }

    /** Urai teks berkas menjadi Setelan. Baris rusak dilewati, tidak meledak. */
    public static Setelan urai(String teks) {
        Setelan s = new Setelan();
        if (teks == null) return s;
        s.dariBerkas = true;
        String[] baris = teks.split("\n", -1);
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.length() == 0 || b.startsWith("//") || b.startsWith("#")) continue;
            int eq = b.indexOf('=');
            if (eq < 0) continue;
            String kunci = b.substring(0, eq).trim();
            String nilai = b.substring(eq + 1).trim();
            if (kunci.equals("arsitekturTerpisah")) {
                s.arsitekturTerpisah = benar(nilai);
                s.adaArsitekturTerpisah = true;
            } else if (kunci.equals("tandaTanganPribadi")) {
                s.tandaTanganPribadi = benar(nilai);
                s.adaTandaTanganPribadi = true;
            } else if (kunci.equals("antiTamper")) {
                s.antiTamper = benar(nilai);
                s.adaAntiTamper = true;
            } else if (kunci.equals("namaKeystore")) {
                s.namaKeystore = nilai;
                s.adaNamaKeystore = true;
            } else if (kunci.equals("appsdk")) {
                try {
                    s.appsdk = Integer.parseInt(nilai);
                    s.adaAppsdk = true;
                } catch (Throwable ignored) {
                }
            }
        }
        return s;
    }

    /** Baca settings.gradle dari folder proyek (Setelan kosong bila tidak ada). */
    public static Setelan baca(File dirProyek) {
        Setelan kosong = new Setelan();
        if (dirProyek == null) return kosong;
        File f = new File(dirProyek, NAMA_BERKAS);
        if (!f.isFile()) return kosong;
        try {
            Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            try {
                StringBuilder b = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
                return urai(b.toString());
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return kosong;
        }
    }

    /** Tulis settings.gradle ke folder proyek. Mengembalikan true bila berhasil. */
    public static boolean tulis(File dirProyek, Setelan s, boolean tulisAntiTamper) {
        if (dirProyek == null || s == null) return false;
        try {
            if (!dirProyek.exists()) dirProyek.mkdirs();
            Writer w = new OutputStreamWriter(
                    new FileOutputStream(new File(dirProyek, NAMA_BERKAS)), "UTF-8");
            try {
                w.write(teks(s, tulisAntiTamper));
                w.flush();
            } finally {
                w.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
