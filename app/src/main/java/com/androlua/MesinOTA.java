package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Mesin Pembaruan Over-The-Air dan Manajemen Rilis Bertahap Mandiri
 * (Fitur 14, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - selisih: perbedaan baris antara dua versi teks (LCS sederhana).
 *  - buatPatch: paket patch (zip) berisi patch.txt + manifest SHA-256
 *    isi baru (sidik jari keutuhan, bukan tanda tangan kunci privat).
 *  - terapkanPatch: bangun versi baru dari versi lama + patch, lalu
 *    verifikasi hash; bila gagal, versi lama tetap dipakai (rollback).
 *  - kelompokPeluncuran: perangkat masuk tahap awal bila hash
 *    (id perangkat + nama rilis) jatuh dalam persentase target.
 *
 * BATAS JUJUR: patch di sini bekerja atas BERKAS TEKS (skrip, aset,
 * konfigurasi). Patch biner DEX/APK (bsdiff) dan penerapan patch ke
 * aplikasi yang sedang berjalan belum didukung — patch diterapkan
 * saat aplikasi dibuka ulang. Peluncuran bertahap memakai hash ID
 * perangkat, bukan penargetan server sungguhan. Manifest patch hanya
 * membawa sidik jari SHA-256 isi baru, bukan tanda tangan kunci
 * privat, jadi keaslian pengirim belum dibuktikan.
 */
public class MesinOTA {

    /** Satu baris patch: operasi + nomor baris + isi. */
    public static class BarisPatch {
        public String operasi = "";
        public int baris = 0;
        public String isi = "";
        public String toString() {
            return operasi + " " + baris + " " + isi;
        }
    }

    /** Hitung selisih baris lama -> baru (LCS, untuk berkas kecil). */
    public static List<BarisPatch> selisih(String lama, String baru) {
        List<BarisPatch> hasil = new ArrayList<BarisPatch>();
        String[] a = lama == null ? new String[0] : lama.split("\n", -1);
        String[] b = baru == null ? new String[0] : baru.split("\n", -1);
        int n = a.length;
        int m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) lcs[i][j] = lcs[i + 1][j + 1] + 1;
                else lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                BarisPatch p = new BarisPatch();
                p.operasi = "-";
                p.baris = i + 1;
                p.isi = a[i];
                hasil.add(p);
                i++;
            } else {
                BarisPatch p = new BarisPatch();
                p.operasi = "+";
                p.baris = i + 1;
                p.isi = b[j];
                hasil.add(p);
                j++;
            }
        }
        while (i < n) {
            BarisPatch p = new BarisPatch();
            p.operasi = "-";
            p.baris = i + 1;
            p.isi = a[i];
            hasil.add(p);
            i++;
        }
        while (j < m) {
            BarisPatch p = new BarisPatch();
            p.operasi = "+";
            p.baris = i + 1;
            p.isi = b[j];
            hasil.add(p);
            j++;
        }
        return hasil;
    }

    /**
     * Terapkan selisih ke teks lama. Nomor baris pada patch mengacu ke
     * teks LAMA: "-" menghapus baris lama nomor itu, "+" menyisipkan
     * sebelum baris lama nomor itu. Penerapan dilakukan sekali jalan
     * dengan kursor, supaya sisipan tidak bergeser oleh penghapusan
     * yang sudah diterapkan (menerapkan hapus dulu lalu sisip akan
     * menaruh sisipan di posisi yang salah).
     */
    public static String terapkanSelisih(String lama, List<BarisPatch> patch) {
        if (lama == null) lama = "";
        List<String> baris = new ArrayList<String>();
        String[] a = lama.split("\\n", -1);
        for (int i = 0; i < a.length; i++) baris.add(a[i]);
        List<String> hasil = new ArrayList<String>();
        int kursor = 0;
        if (patch != null) {
            for (int p = 0; p < patch.size(); p++) {
                BarisPatch op = patch.get(p);
                if ("-".equals(op.operasi)) {
                    while (kursor < baris.size() && kursor + 1 < op.baris) {
                        hasil.add(baris.get(kursor));
                        kursor++;
                    }
                    if (kursor < baris.size() && kursor + 1 == op.baris) {
                        kursor++;
                    }
                } else {
                    while (kursor < baris.size() && kursor + 1 < op.baris) {
                        hasil.add(baris.get(kursor));
                        kursor++;
                    }
                    hasil.add(op.isi);
                }
            }
        }
        while (kursor < baris.size()) {
            hasil.add(baris.get(kursor));
            kursor++;
        }
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < hasil.size(); k++) {
            if (k > 0) b.append("\n");
            b.append(hasil.get(k));
        }
        return b.toString();
    }

    /** Teks patch: satu baris per operasi ("+12|isi" / "-5|isi"). */
    public static String teksPatch(List<BarisPatch> patch) {
        StringBuilder b = new StringBuilder();
        if (patch == null) return "";
        for (int i = 0; i < patch.size(); i++) {
            BarisPatch p = patch.get(i);
            b.append(p.operasi).append(p.baris).append("|").append(p.isi).append("\n");
        }
        return b.toString();
    }

    /** Urai teks patch kembali (baris rusak dilewati). */
    public static List<BarisPatch> uraiPatch(String teks) {
        List<BarisPatch> hasil = new ArrayList<BarisPatch>();
        if (teks == null) return hasil;
        String[] baris = teks.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            if (b.length() < 3) continue;
            String op = b.substring(0, 1);
            if (!"+".equals(op) && !"-".equals(op)) continue;
            int garis = b.indexOf('|');
            if (garis < 0) continue;
            BarisPatch p = new BarisPatch();
            p.operasi = op;
            try {
                p.baris = Integer.parseInt(b.substring(1, garis));
            } catch (Throwable t) {
                continue;
            }
            p.isi = b.substring(garis + 1);
            hasil.add(p);
        }
        return hasil;
    }

    /** SHA-256 heksadesimal dari teks (dipakai manifest patch). */
    public static String sha256Hex(String teks) {
        return RantaiKepercayaan.sha256Hex(teks);
    }

    /**
     * Buat paket patch (zip): patch.txt + MANIFEST.txt berisi
     * "nama_berkas SHA-256-isi-baru". Sidik jari di sini adalah hash
     * SHA-256 isi baru (bukan tanda tangan kunci privat): penerima
     * bisa memeriksa keutuhan patch, tetapi keasliannya belum
     * dibuktikan kunci penandatangan — itu tahap berikut.
     */
    public static File buatPatch(File berkasLama, File berkasBaru,
                                String namaRilis, File keluar) {
        try {
            if (berkasLama == null || berkasBaru == null || keluar == null) return null;
            String lama = bacaTeks(berkasLama);
            String baru = bacaTeks(berkasBaru);
            if (lama == null || baru == null) return null;
            List<BarisPatch> patch = selisih(lama, baru);
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(keluar));
            try {
                tulisEntri(zip, "patch.txt", teksPatch(patch).getBytes("UTF-8"));
                String manifest = "rilis: " + (namaRilis == null ? "" : namaRilis) + "\n"
                        + "berkas: " + berkasBaru.getName() + "\n"
                        + "sha256-baru: " + sha256Hex(baru) + "\n"
                        + "baris-patch: " + patch.size() + "\n";
                tulisEntri(zip, "MANIFEST.txt", manifest.getBytes("UTF-8"));
            } finally {
                zip.close();
            }
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void tulisEntri(ZipOutputStream zip, String nama, byte[] data)
            throws Exception {
        zip.putNextEntry(new ZipEntry(nama));
        zip.write(data);
        zip.closeEntry();
    }

    static String bacaTeks(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * True bila perangkat masuk tahap awal peluncuran: hash
     * (id perangkat + nama rilis) mod 100 < persen target.
     * Deterministis per perangkat dan rilis.
     */
    public static boolean kelompokPeluncuran(String idPerangkat, String namaRilis,
                                            int persen) {
        if (persen <= 0) return false;
        if (persen >= 100) return true;
        String hash = sha256Hex((idPerangkat == null ? "" : idPerangkat) + "\n"
                + (namaRilis == null ? "" : namaRilis));
        if (hash.length() < 8) return false;
        long nilai = Long.parseLong(hash.substring(0, 8), 16);
        return (nilai % 100) < persen;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(long ukuranPatch, long ukuranPenuh,
                                   int persen, String idPerangkat, String namaRilis) {
        int hemat = ukuranPenuh > 0
                ? (int) ((ukuranPenuh - ukuranPatch) * 100 / ukuranPenuh) : 0;
        boolean tahap = kelompokPeluncuran(idPerangkat, namaRilis, persen);
        return "Ukuran patch: " + ukuranPatch + " bita (hemat " + hemat
                + "% dari " + ukuranPenuh + " bita). Pengguna tahap awal: "
                + persen + "% (" + (tahap ? "perangkat ini masuk" : "perangkat ini belum masuk")
                + ").";
    }
}
