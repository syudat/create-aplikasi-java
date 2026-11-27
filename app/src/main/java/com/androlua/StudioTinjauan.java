package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Studio Tinjauan Kode Tim, Penggabungan Tiga-Arah, dan Papan Tugas
 * Asinkron (Fitur 15, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - gabungTigaArah: penggabungan baris demi baris atas dasar,
 *    lokal, dan rekan — blok yang hanya diubah satu pihak diambil
 *    otomatis; blok yang diubah kedua pihak menjadi konflik dengan
 *    tiga pilihan (lokal / rekan / gabung berurutan).
 *  - Komentar tinjauan yang tertambat pada nomor baris.
 *  - Papan tugas teks: belum / berjalan / selesai, tersimpan sebagai
 *    teks "status|judul" di folder proyek.
 *
 * BATAS JUJUR: penggabungan memakai pencocokan BARIS, bukan sintaks
 * — dua perubahan pada baris yang sama selalu dianggap konflik
 * walaupun maknanya tidak bertabrakan. Sinkronisasi papan tugas
 * lewat Git adalah penyalinan berkas teksnya bersama riwayat;
 * tidak ada server sinkronisasi khusus.
 */
public class StudioTinjauan {

    /** Satu blok konflik tiga-arah. */
    public static class Konflik {
        public int barisDasar = 0;
        /** Posisi penyisipan di daftar baris hasil (0 = paling atas). */
        public int posisi = 0;
        public List<String> dasar = new ArrayList<String>();
        public List<String> lokal = new ArrayList<String>();
        public List<String> rekan = new ArrayList<String>();
        public String pilihan = "";
        public String toString() {
            return "Konflik baris " + barisDasar + ": lokal=" + lokal + ", rekan=" + rekan;
        }
    }

    /** Hasil penggabungan: teks + daftar konflik. */
    public static class HasilGabung {
        public List<String> baris = new ArrayList<String>();
        public List<Konflik> konflik = new ArrayList<Konflik>();

        public String teks() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < baris.size(); i++) {
                if (i > 0) b.append("\n");
                b.append(baris.get(i));
            }
            return b.toString();
        }
    }

    /** Satu komentar tinjauan pada nomor baris. */
    public static class Komentar {
        public int baris = 0;
        public String penulis = "";
        public String isi = "";
        public String toString() {
            return baris + " [" + penulis + "]: " + isi;
        }
    }

    /** Satu tugas di papan. */
    public static class Tugas {
        public String status = "belum";
        public String judul = "";
        public String toString() {
            return status + "|" + judul;
        }
    }

    /**
     * Penggabungan tiga-arah berbasis baris. Algoritme: cari baris
     * dasar yang sama di kedua versi (jangkar); daerah di antara
     * jangkar yang hanya berubah di satu pihak diambil; yang berubah
     * di kedua pihak menjadi konflik.
     */
    public static HasilGabung gabungTigaArah(String dasar, String lokal, String rekan) {
        HasilGabung hasil = new HasilGabung();
        String[] d = pecah(dasar);
        String[] l = pecah(lokal);
        String[] r = pecah(rekan);
        int i = 0;
        int j = 0;
        int k = 0;
        while (i < d.length || j < l.length || k < r.length) {
            if (i < d.length && j < l.length && k < r.length
                    && d[i].equals(l[j]) && d[i].equals(r[k])) {
                hasil.baris.add(d[i]);
                i++;
                j++;
                k++;
                continue;
            }
            int jangkarD = cariJangkar(d, l, r, i, j, k);
            if (jangkarD < 0) {
                Konflik c = new Konflik();
                c.barisDasar = i + 1;
                c.posisi = hasil.baris.size();
                while (i < d.length) {
                    c.dasar.add(d[i]);
                    i++;
                }
                while (j < l.length) {
                    c.lokal.add(l[j]);
                    j++;
                }
                while (k < r.length) {
                    c.rekan.add(r[k]);
                    k++;
                }
                if (c.lokal.equals(c.rekan)) {
                    hasil.baris.addAll(c.lokal);
                } else if (c.lokal.size() == 0 && c.dasar.size() == 0) {
                    hasil.baris.addAll(c.rekan);
                } else if (c.rekan.size() == 0 && c.dasar.size() == 0) {
                    hasil.baris.addAll(c.lokal);
                } else {
                    hasil.konflik.add(c);
                }
                break;
            }
            List<String> segL = new ArrayList<String>();
            List<String> segR = new ArrayList<String>();
            List<String> segD = new ArrayList<String>();
            int targetL = posisiDi(l, d[jangkarD], j);
            int targetR = posisiDi(r, d[jangkarD], k);
            while (i < jangkarD) {
                segD.add(d[i]);
                i++;
            }
            while (j < targetL) {
                segL.add(l[j]);
                j++;
            }
            while (k < targetR) {
                segR.add(r[k]);
                k++;
            }
            if (segL.equals(segR)) {
                hasil.baris.addAll(segL);
            } else if (segL.equals(segD)) {
                hasil.baris.addAll(segR);
            } else if (segR.equals(segD)) {
                hasil.baris.addAll(segL);
            } else {
                Konflik c = new Konflik();
                c.barisDasar = i - segD.size() + 1;
                c.posisi = hasil.baris.size();
                c.dasar.addAll(segD);
                c.lokal.addAll(segL);
                c.rekan.addAll(segR);
                hasil.konflik.add(c);
            }
        }
        return hasil;
    }

    private static String[] pecah(String teks) {
        if (teks == null || teks.length() == 0) return new String[0];
        return teks.split("\n", -1);
    }

    /** Cari indeks dasar berikutnya yang muncul di kedua versi. */
    private static int cariJangkar(String[] d, String[] l, String[] r,
                                   int i, int j, int k) {
        for (int x = i; x < d.length; x++) {
            if (posisiDi(l, d[x], j) >= 0 && posisiDi(r, d[x], k) >= 0) return x;
        }
        return -1;
    }

    private static int posisiDi(String[] daftar, String nilai, int dari) {
        for (int x = dari; x < daftar.length; x++) {
            if (daftar[x].equals(nilai)) return x;
        }
        return -1;
    }

    /** Selesaikan satu konflik: "lokal", "rekan", atau "gabung". */
    public static List<String> selesaikanKonflik(Konflik c, String pilihan) {
        if (c == null) return new ArrayList<String>();
        if ("rekan".equals(pilihan)) return new ArrayList<String>(c.rekan);
        if ("gabung".equals(pilihan)) {
            List<String> gabung = new ArrayList<String>(c.lokal);
            for (int i = 0; i < c.rekan.size(); i++) {
                if (!gabung.contains(c.rekan.get(i))) gabung.add(c.rekan.get(i));
            }
            return gabung;
        }
        return new ArrayList<String>(c.lokal);
    }

    /**
     * Terapkan pilihan ke semua konflik dan bangun teks akhir. Baris
     * hasil tiap konflik disisipkan pada posisinya semula (bukan
     * ditempel di akhir), supaya urutan baris hasil tetap sama dengan
     * urutan di ketiga versi. Perbaikan fase 3: sebelumnya baris
     * konflik selalu menempel di akhir teks.
     */
    public static String terapkanPilihan(HasilGabung hasil, String pilihan) {
        if (hasil == null) return "";
        List<String> keluar = new ArrayList<String>(hasil.baris);
        List<Konflik> urut = new ArrayList<Konflik>(hasil.konflik);
        for (int a = 0; a < urut.size(); a++) {
            for (int b = a + 1; b < urut.size(); b++) {
                if (urut.get(b).posisi < urut.get(a).posisi) {
                    Konflik t = urut.get(a);
                    urut.set(a, urut.get(b));
                    urut.set(b, t);
                }
            }
        }
        int geser = 0;
        for (int i = 0; i < urut.size(); i++) {
            List<String> selesai = selesaikanKonflik(urut.get(i), pilihan);
            int pos = urut.get(i).posisi + geser;
            if (pos < 0) pos = 0;
            if (pos > keluar.size()) pos = keluar.size();
            keluar.addAll(pos, selesai);
            geser += selesai.size();
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keluar.size(); i++) {
            if (i > 0) b.append("\n");
            b.append(keluar.get(i));
        }
        return b.toString();
    }

    /** Tambah tugas (status awal "belum"); judul kosong ditolak. */
    public static boolean tambahTugas(List<Tugas> papan, String judul) {
        if (papan == null || judul == null || judul.trim().length() == 0) return false;
        Tugas t = new Tugas();
        t.status = "belum";
        t.judul = judul.trim();
        papan.add(t);
        return true;
    }

    /** Pindah status tugas: "belum" / "berjalan" / "selesai". */
    public static boolean pindahTugas(List<Tugas> papan, int nomor, String status) {
        if (papan == null || nomor < 1 || nomor > papan.size()) return false;
        if (!"belum".equals(status) && !"berjalan".equals(status)
                && !"selesai".equals(status)) return false;
        papan.get(nomor - 1).status = status;
        return true;
    }

    /** Serialisasi papan tugas ke teks. */
    public static String teksPapan(List<Tugas> papan) {
        StringBuilder b = new StringBuilder();
        if (papan == null) return "";
        for (int i = 0; i < papan.size(); i++) {
            b.append(papan.get(i).status).append("|").append(papan.get(i).judul).append("\n");
        }
        return b.toString();
    }

    /** Urai teks papan tugas (baris rusak dilewati). */
    public static List<Tugas> uraiPapan(String teks) {
        List<Tugas> hasil = new ArrayList<Tugas>();
        if (teks == null) return hasil;
        String[] baris = teks.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            int garis = b.indexOf('|');
            if (garis <= 0) continue;
            String status = b.substring(0, garis).trim();
            if (!"belum".equals(status) && !"berjalan".equals(status)
                    && !"selesai".equals(status)) continue;
            Tugas t = new Tugas();
            t.status = status;
            t.judul = b.substring(garis + 1).trim();
            if (t.judul.length() > 0) hasil.add(t);
        }
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahPerubahan, int jumlahKonflik,
                                   int tugasSelesai, int tugasTotal) {
        return "Perubahan ditinjau: " + jumlahPerubahan + ". Konflik ditemukan: "
                + jumlahKonflik + ". Tugas selesai: " + tugasSelesai
                + " dari " + tugasTotal + ".";
    }
}
