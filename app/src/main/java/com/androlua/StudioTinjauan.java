package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Studio Tinjauan Kode Tim, Penggabungan Tiga-Arah, dan Papan Tugas
 * Asinkron (Fitur 15, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - gabungTigaArah: penggabungan baris demi baris atas dasar,
 *    lokal, dan rekan — blok yang hanya diubah satu pihak diambil
 *    otomatis; blok yang diubah kedua pihak menjadi konflik dengan
 *    tiga pilihan (lokal / rekan / gabung berurutan).
 *  - gabungTigaArahSintaks: penggabungan berbasis SINTAKS untuk kode
 *    Java — teks dipecah menjadi satuan sintaks (deklarasi, pernyataan,
 *    blok pembuka/penutup) lewat pemindai yang menghormati string,
 *    karakter, dan komentar; dua perubahan pada BARIS YANG SAMA tetapi
 *    pada pernyataan yang berbeda tidak lagi dianggap konflik.
 *  - Komentar tinjauan yang tertambat pada nomor baris.
 *  - Papan tugas teks: belum / berjalan / selesai, tersimpan sebagai
 *    teks "status|judul" di folder proyek, dan disinkronkan lewat Git
 *    di dalam aplikasi (GitLokal): berkas papan di-commit sehingga
 *    ikut riwayat Git proyek dan bisa dibaca kembali dari commit lama.
 *
 * BATAS JUJUR: pemecah sintaks memakai pemindai Java ringan (bukan
 * parser AST penuh dengan tabel simbol) — ia memisahkan pernyataan
 * dan blok, tetapi tidak memahami makna nama (dua pernyataan yang
 * sama persis di dua tempat tetap dibedakan lewat jangkar, bukan
 * lewat identitas simbol). Git di dalam aplikasi menyimpan snapshot
 * utuh per commit (bukan delta git penuh) dan sinkronisasi jarak jauh
 * memakai jalur snapshot GitHub yang sudah ada di GitLokal/GitActivity;
 * tidak ada server sinkronisasi khusus.
 */
public class StudioTinjauan {

    /** Jalan berkas papan tugas di dalam proyek. */
    public static final String JALAN_PAPAN = ".tugas/papan.txt";

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
        /** True bila baris berisi satuan sintaks (digabung tanpa pemisah). */
        public boolean sintaks = false;

        public String teks() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < baris.size(); i++) {
                if (i > 0 && !sintaks) b.append("\n");
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

    /** Hasil sinkronisasi papan tugas ke Git. */
    public static class HasilSinkron {
        /** "commit-baru" | "tidak-ada-perubahan" | "gagal". */
        public String status = "";
        public String idCommit = "";
        public String pesan = "";
        public String toString() {
            return status + (idCommit.length() > 0 ? " " + idCommit : "") + ": " + pesan;
        }
    }

    /** Satu satuan gabung: teks asli + kunci banding (teks yang dinormalisasi). */
    static class Butir {
        String teks;
        String kunci;
        Butir(String teks, String kunci) {
            this.teks = teks;
            this.kunci = kunci;
        }
    }

    // ------------------------------------------------------- gabung tiga-arah

    /**
     * Penggabungan tiga-arah berbasis baris. Algoritme diff3: hitung
     * diff dasar->lokal dan dasar->rekan (LCS), lalu gabungkan hunk
     * yang tidak tumpang tindih; hunk yang tumpang tindih menjadi
     * konflik. Bila berkas terlalu besar untuk LCS, jatuh kembali ke
     * penggabungan jangkar lama.
     */
    public static HasilGabung gabungTigaArah(String dasar, String lokal, String rekan) {
        return gabungDaftar(keButirBaris(pecah(dasar)), keButirBaris(pecah(lokal)),
                keButirBaris(pecah(rekan)), false);
    }

    /** Pecah teks menjadi baris (tanpa pemisah baris). */
    private static String[] pecah(String teks) {
        if (teks == null || teks.length() == 0) return new String[0];
        return teks.split("\n", -1);
    }

    private static List<Butir> keButirBaris(String[] baris) {
        List<Butir> hasil = new ArrayList<Butir>();
        for (int i = 0; i < baris.length; i++) hasil.add(new Butir(baris[i], baris[i]));
        return hasil;
    }

    /** Satu hunk diff: rentang dasar [awal, akhir) diganti daftar ganti. */
    static class Hunk {
        int awal;
        int akhir;
        List<Butir> ganti = new ArrayList<Butir>();
    }

    /** Inti penggabungan atas daftar satuan (baris atau satuan sintaks). */
    static HasilGabung gabungDaftar(List<Butir> d, List<Butir> l, List<Butir> r,
                                    boolean sintaks) {
        HasilGabung hasil = gabungDiff3(d, l, r, sintaks);
        if (hasil != null) return hasil;
        return gabungDaftarJangkar(d, l, r, sintaks);
    }

    /** Diff dasar -> lain sebagai daftar hunk (LCS); null bila terlalu besar. */
    static List<Hunk> diff(List<Butir> d, List<Butir> lain) {
        int n = d.size();
        int m = lain.size();
        if ((long) (n + 1) * (m + 1) > 4000000L) return null;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (d.get(i).kunci.equals(lain.get(j).kunci)) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        List<Hunk> hasil = new ArrayList<Hunk>();
        int i = 0;
        int j = 0;
        Hunk kini = null;
        while (i < n || j < m) {
            if (i < n && j < m && d.get(i).kunci.equals(lain.get(j).kunci)) {
                if (kini != null) {
                    hasil.add(kini);
                    kini = null;
                }
                i++;
                j++;
            } else if (j < m && (i >= n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                if (kini == null) {
                    kini = new Hunk();
                    kini.awal = i;
                    kini.akhir = i;
                }
                kini.ganti.add(lain.get(j));
                j++;
            } else {
                if (kini == null) {
                    kini = new Hunk();
                    kini.awal = i;
                    kini.akhir = i;
                }
                kini.akhir = i + 1;
                i++;
            }
        }
        if (kini != null) hasil.add(kini);
        return hasil;
    }

    static List<String> kunciDaftar(List<Butir> daftar) {
        List<String> hasil = new ArrayList<String>();
        for (int i = 0; i < daftar.size(); i++) hasil.add(daftar.get(i).kunci);
        return hasil;
    }

    static void tambahTeks(List<String> keluar, List<Butir> ganti) {
        for (int i = 0; i < ganti.size(); i++) keluar.add(ganti.get(i).teks);
    }

    static List<String> subDaftar(List<Butir> d, int awal, int akhir) {
        List<String> hasil = new ArrayList<String>();
        for (int i = awal; i < akhir && i < d.size(); i++) hasil.add(d.get(i).teks);
        return hasil;
    }

    /** Versi satu pihak atas rentang dasar [awalSpan, akhirSpan). */
    static List<String> versiSisi(int awalSpan, int akhirSpan, Hunk hunk, List<Butir> d) {
        List<String> hasil = new ArrayList<String>();
        int p = awalSpan;
        if (hunk != null) {
            while (p < hunk.awal) {
                hasil.add(d.get(p).teks);
                p++;
            }
            tambahTeks(hasil, hunk.ganti);
            if (hunk.akhir > p) p = hunk.akhir;
        }
        while (p < akhirSpan) {
            hasil.add(d.get(p).teks);
            p++;
        }
        return hasil;
    }

    /** True bila dua hunk tumpang tindih (sisipan di dalam rentang pihak lain ikut dihitung). */
    static boolean tumpangTindih(Hunk a, Hunk b) {
        boolean sisipA = a.awal == a.akhir;
        boolean sisipB = b.awal == b.akhir;
        if (sisipA && sisipB) return a.awal == b.awal;
        if (sisipA) return b.awal < a.awal && a.awal < b.akhir;
        if (sisipB) return a.awal < b.awal && b.awal < a.akhir;
        return Math.max(a.awal, b.awal) < Math.min(a.akhir, b.akhir);
    }

    /**
     * Penggabungan diff3 atas daftar satuan. Mengembalikan null bila
     * diff tidak bisa dihitung (berkas terlalu besar) supaya pemanggil
     * jatuh ke penggabungan jangkar.
     */
    static HasilGabung gabungDiff3(List<Butir> d, List<Butir> l, List<Butir> r,
                                   boolean sintaks) {
        List<Hunk> hl = diff(d, l);
        List<Hunk> hr = diff(d, r);
        if (hl == null || hr == null) return null;
        HasilGabung hasil = new HasilGabung();
        hasil.sintaks = sintaks;
        int i = 0;
        int a = 0;
        int b = 0;
        while (i < d.size() || a < hl.size() || b < hr.size()) {
            Hunk hL = a < hl.size() ? hl.get(a) : null;
            Hunk hR = b < hr.size() ? hr.get(b) : null;
            int posBerikut = d.size();
            if (hL != null) posBerikut = Math.min(posBerikut, hL.awal);
            if (hR != null) posBerikut = Math.min(posBerikut, hR.awal);
            while (i < posBerikut) {
                hasil.baris.add(d.get(i).teks);
                i++;
            }
            if (hL == null && hR == null) break;
            if (hL != null && hR != null && tumpangTindih(hL, hR)) {
                int awalSpan = Math.min(hL.awal, hR.awal);
                int akhirSpan = Math.max(hL.akhir, hR.akhir);
                if (hL.awal == hR.awal && hL.akhir == hR.akhir
                        && kunciDaftar(hL.ganti).equals(kunciDaftar(hR.ganti))) {
                    tambahTeks(hasil.baris, hL.ganti);
                } else {
                    Konflik c = new Konflik();
                    c.barisDasar = awalSpan + 1;
                    c.posisi = hasil.baris.size();
                    c.dasar.addAll(subDaftar(d, awalSpan, akhirSpan));
                    c.lokal.addAll(versiSisi(awalSpan, akhirSpan, hL, d));
                    c.rekan.addAll(versiSisi(awalSpan, akhirSpan, hR, d));
                    hasil.konflik.add(c);
                }
                a++;
                b++;
                i = akhirSpan;
                continue;
            }
            // Tidak tumpang tindih: terapkan hunk yang mulai lebih dulu
            // (sisipan di tepi perubahan pihak lain diterapkan dulu).
            if (hL != null && (hR == null || hL.awal < hR.awal
                    || (hL.awal == hR.awal && hL.awal == hL.akhir))) {
                tambahTeks(hasil.baris, hL.ganti);
                a++;
                if (hL.akhir > i) i = hL.akhir;
            } else if (hR != null) {
                tambahTeks(hasil.baris, hR.ganti);
                b++;
                if (hR.akhir > i) i = hR.akhir;
            } else {
                break;
            }
        }
        return hasil;
    }

    /**
     * Penggabungan jangkar lama (fallback untuk berkas besar): cari
     * baris dasar yang sama di kedua versi (jangkar); daerah di antara
     * jangkar yang hanya berubah di satu pihak diambil; yang berubah
     * di kedua pihak menjadi konflik.
     */
    static HasilGabung gabungDaftarJangkar(List<Butir> d, List<Butir> l, List<Butir> r,
                                           boolean sintaks) {
        HasilGabung hasil = new HasilGabung();
        hasil.sintaks = sintaks;
        int i = 0;
        int j = 0;
        int k = 0;
        while (i < d.size() || j < l.size() || k < r.size()) {
            if (i < d.size() && j < l.size() && k < r.size()
                    && d.get(i).kunci.equals(l.get(j).kunci)
                    && d.get(i).kunci.equals(r.get(k).kunci)) {
                hasil.baris.add(d.get(i).teks);
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
                while (i < d.size()) {
                    c.dasar.add(d.get(i).teks);
                    i++;
                }
                while (j < l.size()) {
                    c.lokal.add(l.get(j).teks);
                    j++;
                }
                while (k < r.size()) {
                    c.rekan.add(r.get(k).teks);
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
            List<String> kunciL = new ArrayList<String>();
            List<String> kunciR = new ArrayList<String>();
            List<String> kunciD = new ArrayList<String>();
            int targetL = posisiDi(l, d.get(jangkarD).kunci, j);
            int targetR = posisiDi(r, d.get(jangkarD).kunci, k);
            while (i < jangkarD) {
                segD.add(d.get(i).teks);
                kunciD.add(d.get(i).kunci);
                i++;
            }
            while (j < targetL) {
                segL.add(l.get(j).teks);
                kunciL.add(l.get(j).kunci);
                j++;
            }
            while (k < targetR) {
                segR.add(r.get(k).teks);
                kunciR.add(r.get(k).kunci);
                k++;
            }
            if (kunciL.equals(kunciR)) {
                hasil.baris.addAll(segL);
            } else if (kunciL.equals(kunciD)) {
                hasil.baris.addAll(segR);
            } else if (kunciR.equals(kunciD)) {
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

    /** Cari indeks dasar berikutnya yang muncul di kedua versi (menurut kunci). */
    private static int cariJangkar(List<Butir> d, List<Butir> l, List<Butir> r,
                                   int i, int j, int k) {
        for (int x = i; x < d.size(); x++) {
            if (posisiDi(l, d.get(x).kunci, j) >= 0 && posisiDi(r, d.get(x).kunci, k) >= 0) return x;
        }
        return -1;
    }

    private static int posisiDi(List<Butir> daftar, String kunci, int dari) {
        for (int x = dari; x < daftar.size(); x++) {
            if (daftar.get(x).kunci.equals(kunci)) return x;
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
            if (i > 0 && !hasil.sintaks) b.append("\n");
            b.append(keluar.get(i));
        }
        return b.toString();
    }

    // ------------------------------------------------------- gabung berbasis sintaks

    /** Heuristik: true bila teks tampak seperti kode Java. */
    public static boolean apakahJava(String kode) {
        if (kode == null) return false;
        return kode.contains("class ") || kode.contains("interface ")
                || kode.contains("enum ") || kode.contains("import ")
                || kode.contains("package ") || kode.contains("public void ")
                || kode.contains("private void ") || kode.contains("protected void ")
                || kode.contains("public static ");
    }

    /**
     * Pecah kode Java menjadi satuan sintaks: tiap pernyataan (berakhir
     * ';'), tiap kepala blok (berakhir '{'), dan tiap penutup blok
     * ('}'). Pemindai menghormati string ("..."), karakter ('...'),
     * komentar baris (//...) dan komentar blok (/*...*\/); titik koma
     * di dalam tanda kurung (mis. for(;;)) tidak memisah. Satuan
     * menyimpan teks aslinya persis (termasuk spasi/baris baru di
     * depannya) sehingga gabungan satuan = teks asli. Satuan yang
     * hanya berisi spasi kosong dibuang.
     */
    public static List<String> pecahSintaks(String kode) {
        List<String> hasil = new ArrayList<String>();
        if (kode == null || kode.length() == 0) return hasil;
        int n = kode.length();
        int awal = 0;
        boolean diString = false;
        boolean diChar = false;
        boolean diKomenBaris = false;
        boolean diKomenBlok = false;
        boolean lolos = false;
        int dalamKurung = 0;
        int dalamKurungSiku = 0;
        for (int i = 0; i < n; i++) {
            char c = kode.charAt(i);
            char berikut = i + 1 < n ? kode.charAt(i + 1) : 0;
            if (diKomenBaris) {
                if (c == '\n') diKomenBaris = false;
                continue;
            }
            if (diKomenBlok) {
                if (c == '*' && berikut == '/') {
                    diKomenBlok = false;
                    i++;
                }
                continue;
            }
            if (diString) {
                if (lolos) {
                    lolos = false;
                } else if (c == '\\') {
                    lolos = true;
                } else if (c == '"') {
                    diString = false;
                }
                continue;
            }
            if (diChar) {
                if (lolos) {
                    lolos = false;
                } else if (c == '\\') {
                    lolos = true;
                } else if (c == '\'') {
                    diChar = false;
                }
                continue;
            }
            if (c == '/' && berikut == '/') {
                diKomenBaris = true;
                i++;
                continue;
            }
            if (c == '/' && berikut == '*') {
                diKomenBlok = true;
                i++;
                continue;
            }
            if (c == '"') {
                diString = true;
                continue;
            }
            if (c == '\'') {
                diChar = true;
                continue;
            }
            if (c == '(') {
                dalamKurung++;
                continue;
            }
            if (c == ')') {
                if (dalamKurung > 0) dalamKurung--;
                continue;
            }
            if (c == '[') {
                dalamKurungSiku++;
                continue;
            }
            if (c == ']') {
                if (dalamKurungSiku > 0) dalamKurungSiku--;
                continue;
            }
            boolean batas = false;
            if (c == ';' && dalamKurung == 0 && dalamKurungSiku == 0) batas = true;
            if (c == '{' || c == '}') batas = true;
            if (batas) {
                String satuan = kode.substring(awal, i + 1);
                if (kunciSatuan(satuan).length() > 0) hasil.add(satuan);
                awal = i + 1;
            }
        }
        if (awal < n) {
            String ekor = kode.substring(awal);
            if (kunciSatuan(ekor).length() > 0) hasil.add(ekor);
        }
        return hasil;
    }

    /** Kunci banding satuan: spasi diringkas, ujung dipangkas. */
    static String kunciSatuan(String satuan) {
        if (satuan == null) return "";
        StringBuilder b = new StringBuilder();
        boolean spasi = true;
        for (int i = 0; i < satuan.length(); i++) {
            char c = satuan.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                if (!spasi) {
                    b.append(' ');
                    spasi = true;
                }
            } else {
                b.append(c);
                spasi = false;
            }
        }
        String hasil = b.toString();
        if (hasil.endsWith(" ")) hasil = hasil.substring(0, hasil.length() - 1);
        return hasil;
    }

    /**
     * Penggabungan tiga-arah berbasis SINTAKS (kode Java): tiap versi
     * dipecah menjadi satuan sintaks, lalu digabungkan dengan
     * algoritme jangkar yang sama. Dua perubahan pada baris yang sama
     * tetapi pada pernyataan berbeda tidak lagi dianggap konflik.
     */
    public static HasilGabung gabungTigaArahSintaks(String dasar, String lokal, String rekan) {
        List<Butir> d = keButirSintaks(pecahSintaks(dasar));
        List<Butir> l = keButirSintaks(pecahSintaks(lokal));
        List<Butir> r = keButirSintaks(pecahSintaks(rekan));
        return gabungDaftar(d, l, r, true);
    }

    private static List<Butir> keButirSintaks(List<String> satuan) {
        List<Butir> hasil = new ArrayList<Butir>();
        for (int i = 0; i < satuan.size(); i++) {
            hasil.add(new Butir(satuan.get(i), kunciSatuan(satuan.get(i))));
        }
        return hasil;
    }

    /**
     * Gabung otomatis: sintaks bila ketiga versi tampak seperti kode
     * Java, baris bila bukan. Dipakai layar saat pengguna memilih
     * mode "Otomatis".
     */
    public static HasilGabung gabungOtomatis(String dasar, String lokal, String rekan) {
        if (apakahJava(dasar) && apakahJava(lokal) && apakahJava(rekan)) {
            return gabungTigaArahSintaks(dasar, lokal, rekan);
        }
        return gabungTigaArah(dasar, lokal, rekan);
    }

    // ------------------------------------------------------- papan tugas

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

    /** Tulis papan tugas ke berkas papan proyek (.tugas/papan.txt). */
    public static boolean tulisPapan(File dirProyek, List<Tugas> papan) {
        if (dirProyek == null || !dirProyek.isDirectory()) return false;
        try {
            File f = new File(dirProyek, JALAN_PAPAN);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                out.write(teksPapan(papan));
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Baca papan tugas dari berkas papan proyek (kosong bila belum ada). */
    public static List<Tugas> bacaPapan(File dirProyek) {
        if (dirProyek == null) return new ArrayList<Tugas>();
        return uraiPapan(bacaTeks(new File(dirProyek, JALAN_PAPAN)));
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

    // ------------------------------------------------------- sinkronisasi papan lewat Git

    /**
     * Sinkronkan papan tugas ke Git proyek memakai Git di dalam
     * aplikasi (GitLokal): tulis .tugas/papan.txt, buat repo bila
     * belum ada, lalu commit — berkas papan ikut snapshot commit dan
     * tercatat di riwayat Git proyek. Status: "commit-baru" (dengan
     * id commit), "tidak-ada-perubahan" (papan sudah sama dengan
     * commit terakhir), atau "gagal" (dengan pesan).
     */
    public static HasilSinkron sinkronPapanGit(File dirProyek, List<Tugas> papan,
                                               String pesan) {
        HasilSinkron h = new HasilSinkron();
        if (dirProyek == null || !dirProyek.isDirectory()) {
            h.status = "gagal";
            h.pesan = "Folder proyek tidak ditemukan.";
            return h;
        }
        if (!GitLokal.adaGit(dirProyek)) {
            String galat = GitLokal.init(dirProyek);
            if (galat != null) {
                h.status = "gagal";
                h.pesan = galat;
                return h;
            }
        }
        if (!tulisPapan(dirProyek, papan)) {
            h.status = "gagal";
            h.pesan = "Papan tugas tidak bisa ditulis.";
            return h;
        }
        String pesanCommit = pesan == null || pesan.trim().length() == 0
                ? "Perbarui papan tugas" : pesan.trim();
        GitLokal.HasilCommit hc = GitLokal.commit(dirProyek, pesanCommit);
        if (hc.berhasil()) {
            h.status = "commit-baru";
            h.idCommit = hc.id;
            String pendek = hc.id.length() > 7 ? hc.id.substring(0, 7) : hc.id;
            h.pesan = "Papan tugas masuk commit " + pendek + ".";
            return h;
        }
        if (hc.galat != null && hc.galat.contains("Tidak ada perubahan")) {
            h.status = "tidak-ada-perubahan";
            h.idCommit = GitLokal.kepala(dirProyek);
            h.pesan = "Papan tugas sudah sama dengan commit terakhir.";
            return h;
        }
        h.status = "gagal";
        h.pesan = hc.galat == null ? "Commit gagal." : hc.galat;
        return h;
    }

    /** Riwayat commit proyek (terbaru dulu); tiap commit membawa snapshot papan tugas. */
    public static List<GitLokal.Commit> riwayatPapan(File dirProyek) {
        if (dirProyek == null) return new ArrayList<GitLokal.Commit>();
        return GitLokal.log(dirProyek);
    }

    /**
     * Baca papan tugas seperti yang tersimpan di satu commit lama:
     * buka snapshot ZIP commit itu (.git/snapshots/<id>.zip) dan baca
     * .tugas/papan.txt di dalamnya. Null bila commit/berkas tidak ada.
     */
    public static List<Tugas> papanPadaCommit(File dirProyek, String idCommit) {
        if (dirProyek == null || idCommit == null || idCommit.length() == 0) return null;
        File zip = new File(dirProyek, ".git/snapshots/" + idCommit + ".zip");
        if (!zip.isFile()) return null;
        ZipFile zf;
        try {
            zf = new ZipFile(zip);
        } catch (Throwable t) {
            return null;
        }
        try {
            ZipEntry e = zf.getEntry(JALAN_PAPAN);
            if (e == null) return null;
            java.io.InputStream masuk = zf.getInputStream(e);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = masuk.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                masuk.close();
            }
            return uraiPapan(new String(buf.toByteArray(), "UTF-8"));
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahPerubahan, int jumlahKonflik,
                                   int tugasSelesai, int tugasTotal) {
        return "Perubahan ditinjau: " + jumlahPerubahan + ". Konflik ditemukan: "
                + jumlahKonflik + ". Tugas selesai: " + tugasSelesai
                + " dari " + tugasTotal + ".";
    }
}
