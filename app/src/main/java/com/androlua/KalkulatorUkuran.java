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
 * Kalkulator Ukuran APK (Tahap 3I).
 *
 * Memperkirakan ukuran APK hasil build dari sebuah proyek dengan
 * menjumlahkan ukuran berkas per kategori memakai MODEL PERKIRAAN
 * yang dinyatakan terbuka di bawah (bukan ukuran pasti):
 * - Kode Java/Kotlin: 12 byte DEX per baris kode + 300 byte per berkas
 *   (overhead kelas). Kode Lua: 1,0x ukuran sumber (bytecode hasil
 *   kompilasi kira-kira sebesar sumbernya).
 * - Resource (res/): 0,6x ukuran (aapt2 memadatkan XML/gambar).
 * - Aset (assets/): 0,9x ukuran (sebagian besar disimpan apa adanya).
 * - Library native (.so) dan .jar: 1,0x ukuran (tidak dikompres).
 * - Manifest + resources.arsc: 10 KB + 0,1x ukuran resource.
 * - Berkas lain (teks/json/dll): 1,0x ukuran.
 * - Overhead APK (header ZIP + tanda tangan): 7 KB.
 * Folder bin/ (hasil build lama) dan .git/ tidak dihitung.
 *
 * KETERBATASAN YANG JUJUR (ditulis juga di UkuranApkActivity dan di
 * hasil): ini PERKIRAAN — ukuran APK sungguhan bisa berbeda karena
 * kompresi aapt2, penghapusan kode tak terpakai (R8), dan resource
 * yang dihasilkan build (R.java, BuildConfig). Angka ini berguna
 * untuk membandingkan dan mencari berkas yang membengkakkan APK,
 * bukan untuk janji ukuran pasti.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JRE biasa.
 */
public class KalkulatorUkuran {

    /** Satu kategori rincian: nama, ukuran perkiraan, keterangan. */
    public static class Rincian {
        public final String kategori;
        public final long ukuran;
        public final String keterangan;
        public Rincian(String kategori, long ukuran, String keterangan) {
            this.kategori = kategori;
            this.ukuran = ukuran;
            this.keterangan = keterangan;
        }
    }

    /** Satu berkas terbesar (jalan relatif + ukuran asli). */
    public static class Terbesar {
        public final String jalan;
        public final long ukuran;
        public Terbesar(String jalan, long ukuran) {
            this.jalan = jalan;
            this.ukuran = ukuran;
        }
    }

    /** Hasil perkiraan: total + rincian + 5 berkas terbesar + catatan. */
    public static class Hasil {
        public final long total;
        public final List<Rincian> rincian;
        public final List<Terbesar> terbesar;
        public final String catatan;
        public Hasil(long total, List<Rincian> rincian, List<Terbesar> terbesar,
                     String catatan) {
            this.total = total;
            this.rincian = rincian;
            this.terbesar = terbesar;
            this.catatan = catatan;
        }
    }

    /** Perkirakan ukuran APK proyek; null bila folder proyek tidak ada. */
    public static Hasil perkirakan(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        long dex = 0;
        long res = 0;
        long aset = 0;
        long lib = 0;
        long lainnya = 0;
        long ukuranResAsli = 0;
        int barisKode = 0;
        int berkasKode = 0;
        List<Terbesar> semua = new ArrayList<Terbesar>();
        List<File> kerja = new ArrayList<File>();
        kumpulkan(dirProyek, dirProyek, kerja);
        for (int i = 0; i < kerja.size(); i++) {
            File f = kerja.get(i);
            String jalan = jalanRelatif(dirProyek, f);
            long ukuran = f.length();
            semua.add(new Terbesar(jalan, ukuran));
            String namaKecil = f.getName().toLowerCase();
            if (namaKecil.endsWith(".java") || namaKecil.endsWith(".kt")) {
                int baris = hitungBaris(f);
                barisKode += baris;
                berkasKode++;
                dex += (long) baris * 12 + 300;
            } else if (namaKecil.endsWith(".lua")) {
                dex += ukuran; // bytecode kira-kira sebesar sumber
                barisKode += hitungBaris(f);
                berkasKode++;
            } else if (jalan.contains("/res/")) {
                res += (long) (ukuran * 0.6);
                ukuranResAsli += ukuran;
            } else if (jalan.contains("/assets/")) {
                aset += (long) (ukuran * 0.9);
            } else if (namaKecil.endsWith(".so") || namaKecil.endsWith(".jar")) {
                lib += ukuran;
            } else if ("app/src/main/AndroidManifest.xml".equals(jalan)) {
                // Manifest biner dihitung di kategori Manifest + arsc.
            } else {
                lainnya += ukuran;
            }
        }
        long manifesArsc = 10240 + (long) (ukuranResAsli * 0.1);
        long overhead = 7168;
        long total = dex + res + aset + lib + lainnya + manifesArsc + overhead;
        List<Rincian> rincian = new ArrayList<Rincian>();
        rincian.add(new Rincian("Kode (DEX)", dex,
                berkasKode + " berkas kode, " + barisKode + " baris "
                        + "(12 byte/baris + 300 byte/berkas; Lua 1,0x sumber)"));
        rincian.add(new Rincian("Resource", res,
                "0,6x ukuran res/ (aapt2 memadatkan XML/gambar)"));
        rincian.add(new Rincian("Aset", aset,
                "0,9x ukuran assets/ (sebagian besar disimpan apa adanya)"));
        rincian.add(new Rincian("Library native", lib,
                "1,0x ukuran .so/.jar (tidak dikompres)"));
        rincian.add(new Rincian("Lainnya", lainnya,
                "1,0x ukuran berkas lain (teks/json/dll)"));
        rincian.add(new Rincian("Manifest + resources.arsc", manifesArsc,
                "10 KB + 0,1x ukuran resource"));
        rincian.add(new Rincian("Overhead APK", overhead,
                "header ZIP + tanda tangan (7 KB)"));
        Collections.sort(semua, new Comparator<Terbesar>() {
            @Override
            public int compare(Terbesar a, Terbesar b) {
                return Long.compare(b.ukuran, a.ukuran);
            }
        });
        List<Terbesar> terbesar = new ArrayList<Terbesar>();
        for (int i = 0; i < semua.size() && i < 5; i++) terbesar.add(semua.get(i));
        String catatan = "PERKIRAAN, bukan ukuran pasti hasil build. Ukuran APK "
                + "sungguhan bisa berbeda karena kompresi aapt2, penghapusan "
                + "kode tak terpakai (R8), dan resource hasil build. "
                + "Gunakan angka ini untuk membandingkan dan mencari berkas "
                + "yang membengkakkan APK.";
        return new Hasil(total, rincian, terbesar, catatan);
    }

    /** Teks laporan lengkap (rincian + total + berkas terbesar + saran). */
    public static String laporan(File dirProyek) {
        Hasil h = perkirakan(dirProyek);
        if (h == null) return "Folder proyek tidak ditemukan.";
        StringBuilder sb = new StringBuilder();
        sb.append("Perkiraan Ukuran APK: ").append(dirProyek.getName()).append("\n");
        sb.append("Total perkiraan: ").append(PetaProyek.formatUkuran(h.total)).append("\n\n");
        sb.append("== Rincian ==\n");
        for (int i = 0; i < h.rincian.size(); i++) {
            Rincian r = h.rincian.get(i);
            sb.append(r.kategori).append(": ").append(PetaProyek.formatUkuran(r.ukuran))
                    .append(" (").append(r.keterangan).append(")\n");
        }
        sb.append("\n== 5 berkas terbesar ==\n");
        if (h.terbesar.isEmpty()) {
            sb.append("(proyek kosong)\n");
        } else {
            for (int i = 0; i < h.terbesar.size(); i++) {
                Terbesar t = h.terbesar.get(i);
                sb.append((i + 1)).append(". ").append(t.jalan).append(" (")
                        .append(PetaProyek.formatUkuran(t.ukuran)).append(")\n");
            }
        }
        sb.append("\n== Saran mengecilkan ==\n");
        sb.append(saran(h));
        sb.append("\n").append(h.catatan).append("\n");
        return sb.toString();
    }

    /** Saran mengecilkan berdasarkan berkas terbesar. */
    public static String saran(Hasil h) {
        if (h == null || h.terbesar.isEmpty()) {
            return "Proyek masih kosong — belum ada yang bisa dikecilkan.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.terbesar.size(); i++) {
            Terbesar t = h.terbesar.get(i);
            String namaKecil = t.jalan.toLowerCase();
            sb.append("- ").append(t.jalan).append(" (")
                    .append(PetaProyek.formatUkuran(t.ukuran)).append("): ");
            if (namaKecil.endsWith(".png") || namaKecil.endsWith(".jpg")
                    || namaKecil.endsWith(".jpeg")) {
                sb.append("gambar besar — perkecil resolusi atau kompres sebelum dipakai.\n");
            } else if (namaKecil.endsWith(".mp3") || namaKecil.endsWith(".ogg")
                    || namaKecil.endsWith(".wav")) {
                sb.append("suara besar — potong durasi atau turunkan kualitas (bitrate).\n");
            } else if (namaKecil.endsWith(".so") || namaKecil.endsWith(".jar")) {
                sb.append("library besar — hapus ABI yang tidak dipakai (mis. hanya arm64-v8a).\n");
            } else if (namaKecil.endsWith(".lua") || namaKecil.endsWith(".java")
                    || namaKecil.endsWith(".kt")) {
                sb.append("kode besar — pecah ke beberapa berkas atau hapus kode tak terpakai.\n");
            } else {
                sb.append("pertimbangkan apakah berkas ini perlu ada di APK.\n");
            }
        }
        return sb.toString();
    }

    /** Hitung jumlah baris berkas teks; 0 bila gagal dibaca. */
    public static int hitungBaris(File f) {
        if (f == null || !f.isFile()) return 0;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            int baris = 0;
            while (r.readLine() != null) baris++;
            return baris;
        } catch (Exception ignored) {
            return 0;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void kumpulkan(File akar, File dir, List<File> hasil) {
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            File a = anak[i];
            if (a.isDirectory()) {
                if (".git".equals(a.getName())) continue;
                if ("bin".equals(a.getName()) && a.getParentFile().equals(akar)) continue;
                kumpulkan(akar, a, hasil);
            } else {
                hasil.add(a);
            }
        }
    }

    private static String jalanRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) {
            String r = b.substring(a.length());
            if (r.startsWith(File.separator)) r = r.substring(1);
            return r.replace(File.separatorChar, '/');
        }
        return f.getName();
    }
}
