package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Git di Dalam Aplikasi (Tahap 3I).
 *
 * Tiga jalur yang NYATA (bukan tombol mati):
 * (a) Git LOKAL murni-Java (tanpa JGit, tanpa biner git): init repo
 *     (.git/HEAD, config, refs, index, commits, snapshots), status
 *     (bandingkan hash SHA-1 berkas kerja dengan index), commit (snapshot
 *     ZIP + metadata di .git/commits/), log (baca metadata commit).
 * (b) Git SISTEM bila ada (Runtime.exec "git ..."): clone/pull/push
 *     memakai biner git HP — di HP biasanya TIDAK ada, jadi setiap
 *     pemanggilan memeriksa dulu dan mengembalikan pesan jujur bila
 *     tidak ada.
 * (c) GitHub via Contents API (pola SinkronCloud 3F): push = unggah
 *     snapshot ZIP commit terakhir + berkas HEAD; pull = unduh HEAD
 *     lalu snapshot ZIP terbaru dan ekstrak ke proyek. Butuh token
 *     (GithubSettings) dan repo "pembuat-aplikasi-build".
 *
 * KETERBATASAN YANG JUJUR (ditulis juga di GitActivity): ini BUKAN git
 * penuh — tidak ada merge, branch, diff, atau riwayat delta (setiap
 * commit menyimpan snapshot ZIP utuh, bukan perubahan); push/pull lewat
 * GitHub hanya snapshot utuh, bukan protokol git; operasi git sistem
 * hanya jalan bila biner git terpasang di HP.
 *
 * Murni java.io/java.security/java.util.zip (tanpa API Android) supaya
 * bisa diuji di JRE biasa.
 */
public class GitLokal {

    /** Nama folder repo lokal di dalam proyek. */
    public static final String DIR_GIT = ".git";

    /** Cabang bawaan repo lokal. */
    public static final String CABANG_BAWAAN = "main";

    /** Folder snapshot Git di dalam repo GitHub (Contents API). */
    public static final String JALUR_GIT_REPO = "git";

    /** Satu baris status: jalan relatif + status baru/berubah/dihapus. */
    public static class StatusItem {
        public final String jalan;
        public final String status; // baru | berubah | dihapus
        public StatusItem(String jalan, String status) {
            this.jalan = jalan;
            this.status = status;
        }
    }

    /** Satu commit: id penuh, waktu (epoch ms), pesan, induk, jumlah berkas. */
    public static class Commit {
        public final String id;
        public final long waktu;
        public final String pesan;
        public final String induk;
        public final int berkas;
        public Commit(String id, long waktu, String pesan, String induk, int berkas) {
            this.id = id;
            this.waktu = waktu;
            this.pesan = pesan == null ? "" : pesan;
            this.induk = induk == null ? "" : induk;
            this.berkas = berkas;
        }
        /** 7 karakter pertama id (seperti git short hash). */
        public String idPendek() {
            return id.length() > 7 ? id.substring(0, 7) : id;
        }
        /** Tanggal commit "2026-11-14 15:30". */
        public String tanggal() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(new Date(waktu));
        }
    }

    /** Hasil commit: id penuh bila berhasil, atau pesan galat. */
    public static class HasilCommit {
        public final String id;
        public final String galat;
        public HasilCommit(String id, String galat) {
            this.id = id;
            this.galat = galat;
        }
        public boolean berhasil() {
            return galat == null && id != null && !id.isEmpty();
        }
    }

    /** Hasil perintah git sistem: kode keluar + keluaran gabungan. */
    public static class HasilPerintah {
        public final int kode;
        public final String keluar;
        public HasilPerintah(int kode, String keluar) {
            this.kode = kode;
            this.keluar = keluar == null ? "" : keluar;
        }
        public boolean berhasil() {
            return kode == 0;
        }
    }

    // ------------------------------------------------------- repo lokal

    /** True bila folder proyek sudah berisi repo Git lokal (.git/). */
    public static boolean adaGit(File dirProyek) {
        return dirProyek != null && new File(dirProyek, DIR_GIT).isDirectory();
    }

    /**
     * Buat repo Git lokal baru di proyek. Mengembalikan null bila
     * berhasil, atau pesan galat.
     */
    public static String init(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        if (adaGit(dirProyek)) {
            return "Proyek ini sudah punya repo Git.";
        }
        File git = new File(dirProyek, DIR_GIT);
        try {
            new File(git, "refs/heads").mkdirs();
            new File(git, "refs/tags").mkdirs();
            new File(git, "commits").mkdirs();
            new File(git, "snapshots").mkdirs();
            tulisTeks(new File(git, "HEAD"), "ref: refs/heads/" + CABANG_BAWAAN + "\n");
            tulisTeks(new File(git, "config"),
                    "[core]\n\trepositoryformatversion = 0\n\tbare = false\n");
            tulisTeks(new File(git, "index.txt"), "");
            return null;
        } catch (Exception e) {
            return "Gagal membuat repo Git: " + e.getMessage();
        }
    }

    /** Id commit kepala (cabang main) atau "" bila belum ada commit. */
    public static String kepala(File dirProyek) {
        if (!adaGit(dirProyek)) return "";
        String isi = bacaTeks(new File(dirProyek,
                DIR_GIT + "/refs/heads/" + CABANG_BAWAAN));
        return isi == null ? "" : isi.trim();
    }

    /**
     * Status kerja: berkas baru / berubah / dihapus dibanding index.
     * Mengembalikan null bila proyek bukan repo Git.
     */
    public static List<StatusItem> status(File dirProyek) {
        if (!adaGit(dirProyek)) return null;
        Map<String, String> indeks = bacaIndeks(dirProyek);
        List<StatusItem> hasil = new ArrayList<StatusItem>();
        Map<String, String> kerja = new HashMap<String, String>();
        for (String jalan : kumpulkanBerkas(dirProyek)) {
            String sha = sha1(new File(dirProyek, jalan));
            kerja.put(jalan, sha);
            String lama = indeks.get(jalan);
            if (lama == null) {
                hasil.add(new StatusItem(jalan, "baru"));
            } else if (!lama.equals(sha)) {
                hasil.add(new StatusItem(jalan, "berubah"));
            }
        }
        for (String jalan : indeks.keySet()) {
            if (!kerja.containsKey(jalan)) {
                hasil.add(new StatusItem(jalan, "dihapus"));
            }
        }
        Collections.sort(hasil, new Comparator<StatusItem>() {
            @Override
            public int compare(StatusItem a, StatusItem b) {
                return a.jalan.compareTo(b.jalan);
            }
        });
        return hasil;
    }

    /**
     * Commit semua perubahan: tulis snapshot ZIP + metadata commit +
     * perbarui index dan kepala cabang. Pesan kosong menjadi
     * "Commit tanpa pesan". Mengembalikan HasilCommit (id / galat).
     */
    public static HasilCommit commit(File dirProyek, String pesan) {
        if (!adaGit(dirProyek)) {
            return new HasilCommit(null, "Proyek ini belum punya repo Git. Buat repo dulu.");
        }
        List<StatusItem> st = status(dirProyek);
        if (st == null) {
            return new HasilCommit(null, "Gagal membaca status proyek.");
        }
        if (st.isEmpty()) {
            return new HasilCommit(null, "Tidak ada perubahan untuk di-commit.");
        }
        String pesanBersih = pesan == null ? "" : pesan.trim();
        if (pesanBersih.isEmpty()) pesanBersih = "Commit tanpa pesan";
        // Satu baris saja (baris pertama) supaya metadata tetap satu baris.
        int nl = pesanBersih.indexOf('\n');
        if (nl >= 0) pesanBersih = pesanBersih.substring(0, nl).trim();
        if (pesanBersih.isEmpty()) pesanBersih = "Commit tanpa pesan";
        long waktu = System.currentTimeMillis();
        Map<String, String> kerja = new TreeMap<String, String>();
        for (String jalan : kumpulkanBerkas(dirProyek)) {
            kerja.put(jalan, sha1(new File(dirProyek, jalan)));
        }
        StringBuilder pohon = new StringBuilder();
        for (Map.Entry<String, String> e : kerja.entrySet()) {
            pohon.append(e.getValue()).append("  ").append(e.getKey()).append("\n");
        }
        String hashPohon = sha1Teks(pohon.toString());
        String induk = kepala(dirProyek);
        String id = sha1Teks(waktu + "|" + pesanBersih + "|" + hashPohon + "|" + induk);
        File zip = new File(dirProyek, DIR_GIT + "/snapshots/" + id + ".zip");
        if (!tulisSnapshot(dirProyek, zip)) {
            return new HasilCommit(null, "Gagal menulis snapshot commit.");
        }
        String meta = "waktu=" + waktu + "\n"
                + "pesan=" + pesanBersih.replace("|", "/") + "\n"
                + "induk=" + induk + "\n"
                + "berkas=" + kerja.size() + "\n";
        try {
            tulisTeks(new File(dirProyek, DIR_GIT + "/commits/" + id + ".txt"), meta);
            tulisTeks(new File(dirProyek,
                    DIR_GIT + "/refs/heads/" + CABANG_BAWAAN), id + "\n");
            tulisIndeks(dirProyek, kerja);
        } catch (Exception e) {
            return new HasilCommit(null, "Gagal menyimpan commit: " + e.getMessage());
        }
        return new HasilCommit(id, null);
    }

    /** Daftar commit, terbaru dulu. Kosong bila belum ada commit. */
    public static List<Commit> log(File dirProyek) {
        List<Commit> hasil = new ArrayList<Commit>();
        if (!adaGit(dirProyek)) return hasil;
        File dirCommit = new File(dirProyek, DIR_GIT + "/commits");
        File[] daftar = dirCommit.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            if (!daftar[i].isFile() || !daftar[i].getName().endsWith(".txt")) continue;
            String nama = daftar[i].getName();
            String id = nama.substring(0, nama.length() - 4);
            String isi = bacaTeks(daftar[i]);
            if (isi == null) continue;
            long waktu = 0;
            String pesan = "";
            String induk = "";
            int berkas = 0;
            for (String baris : isi.split("\n", -1)) {
                if (baris.startsWith("waktu=")) {
                    try {
                        waktu = Long.parseLong(baris.substring(6).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (baris.startsWith("pesan=")) {
                    pesan = baris.substring(6);
                } else if (baris.startsWith("induk=")) {
                    induk = baris.substring(6).trim();
                } else if (baris.startsWith("berkas=")) {
                    try {
                        berkas = Integer.parseInt(baris.substring(7).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            hasil.add(new Commit(id, waktu, pesan, induk, berkas));
        }
        Collections.sort(hasil, new Comparator<Commit>() {
            @Override
            public int compare(Commit a, Commit b) {
                return Long.compare(b.waktu, a.waktu);
            }
        });
        return hasil;
    }

    /** Nama berkas snapshot di repo GitHub untuk satu commit. */
    public static String namaBerkasSnapshot(String namaProyek, String idPendek) {
        return namaProyek + "-" + idPendek + ".zip";
    }

    /** Nama berkas HEAD di repo GitHub untuk satu proyek. */
    public static String namaBerkasHead(String namaProyek) {
        return namaProyek + "-HEAD.txt";
    }

    // ------------------------------------------------------- git sistem

    /** True bila biner "git" bisa dijalankan di perangkat ini. */
    public static boolean adaGitSistem() {
        try {
            HasilPerintah r = jalankanGit(null, "--version");
            return r.berhasil() && r.keluar.contains("git version");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Jalankan biner git dengan argumen di folder kerja (boleh null =
     * folder saat ini). Keluaran standar + galat digabung. Batas waktu
     * 120 detik supaya clone besar tidak menggantung selamanya.
     */
    public static HasilPerintah jalankanGit(File dirKerja, String... args) {
        List<String> perintah = new ArrayList<String>();
        perintah.add("git");
        if (args != null) {
            for (int i = 0; i < args.length; i++) perintah.add(args[i]);
        }
        ProcessBuilder pb = new ProcessBuilder(perintah);
        pb.redirectErrorStream(true);
        if (dirKerja != null) pb.directory(dirKerja);
        Process proses = null;
        try {
            proses = pb.start();
            final InputStream masuk = proses.getInputStream();
            final StringBuilder keluar = new StringBuilder();
            Thread pembaca = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = masuk.read(buf)) > 0) {
                            keluar.append(new String(buf, 0, n, "UTF-8"));
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            pembaca.start();
            long batas = System.currentTimeMillis() + 120000;
            while (proses.isAlive()) {
                if (System.currentTimeMillis() > batas) {
                    proses.destroy();
                    return new HasilPerintah(-1, "Perintah git kehabisan waktu (120 detik).");
                }
                Thread.sleep(100);
            }
            pembaca.join(5000);
            return new HasilPerintah(proses.exitValue(), keluar.toString());
        } catch (Exception e) {
            if (proses != null) proses.destroy();
            return new HasilPerintah(-1, "Gagal menjalankan git: " + e.getMessage());
        }
    }

    /**
     * Clone repo dari URL ke folder tujuan di dalam dirInduk. Memakai
     * git sistem; bila git sistem tidak ada, kembalikan pesan jujur.
     * Mengembalikan null bila berhasil, atau pesan galat.
     */
    public static String clone(String url, File dirInduk, String nama) {
        if (url == null || url.trim().isEmpty()) {
            return "URL repo Git kosong.";
        }
        if (dirInduk == null || !dirInduk.isDirectory()) {
            return "Folder tujuan clone tidak ditemukan.";
        }
        if (nama == null || nama.trim().isEmpty()) {
            return "Nama folder hasil clone kosong.";
        }
        if (new File(dirInduk, nama).exists()) {
            return "Folder \"" + nama + "\" sudah ada.";
        }
        if (!adaGitSistem()) {
            return "Biner git tidak tersedia di HP ini, jadi clone tidak bisa "
                    + "dijalankan. Pasang Termux lalu \"pkg install git\", atau "
                    + "unduh ZIP repo dari GitHub dan impor lewat menu Impor proyek.";
        }
        HasilPerintah r = jalankanGit(dirInduk, "clone", url.trim(), nama);
        if (r.berhasil()) return null;
        return "Clone gagal: " + r.keluar.trim();
    }

    /** Tarik perubahan dari remote (git pull) memakai git sistem. */
    public static String pullSistem(File dirProyek) {
        if (!adaGit(dirProyek)) return "Proyek ini belum punya repo Git.";
        if (!adaGitSistem()) {
            return "Biner git tidak tersedia di HP ini, jadi pull tidak bisa "
                    + "dijalankan. Pakai \"Pull dari GitHub\" (snapshot) bila "
                    + "proyek ini pernah di-push ke GitHub lewat IDE.";
        }
        HasilPerintah r = jalankanGit(dirProyek, "pull");
        if (r.berhasil()) return null;
        return "Pull gagal: " + r.keluar.trim();
    }

    /** Dorong perubahan ke remote (git push) memakai git sistem. */
    public static String pushSistem(File dirProyek) {
        if (!adaGit(dirProyek)) return "Proyek ini belum punya repo Git.";
        if (!adaGitSistem()) {
            return "Biner git tidak tersedia di HP ini, jadi push tidak bisa "
                    + "dijalankan. Pakai \"Push ke GitHub\" (snapshot) untuk "
                    + "menyimpan commit ke GitHub lewat IDE.";
        }
        HasilPerintah r = jalankanGit(dirProyek, "push");
        if (r.berhasil()) return null;
        return "Push gagal: " + r.keluar.trim();
    }

    // ------------------------------------------------------- GitHub (snapshot)

    /**
     * Push commit terakhir ke GitHub (Contents API, pola SinkronCloud):
     * unggah snapshot ZIP + berkas HEAD berisi id commit. Mengembalikan
     * null bila berhasil, atau pesan galat.
     */
    public static String pushGithub(String token, String apiDasar, String pemilik,
                                    String repo, File dirProyek,
                                    SinkronCloud.PengirimHttp http) {
        if (token == null || token.isEmpty()) return "Token GitHub belum diisi.";
        if (pemilik == null || pemilik.isEmpty()) {
            return "Nama pengguna GitHub tidak diketahui.";
        }
        if (http == null) return "Pengirim HTTP tidak ada.";
        if (!adaGit(dirProyek)) return "Proyek ini belum punya repo Git.";
        String id = kepala(dirProyek);
        if (id.isEmpty()) return "Belum ada commit. Buat commit dulu sebelum push.";
        String nama = dirProyek.getName();
        String idPendek = id.length() > 7 ? id.substring(0, 7) : id;
        File zip = new File(dirProyek, DIR_GIT + "/snapshots/" + id + ".zip");
        if (!zip.isFile()) return "Snapshot commit tidak ditemukan.";
        String galat = SinkronCloud.unggah(token, apiDasar, pemilik, repo,
                JALUR_GIT_REPO + "/" + namaBerkasSnapshot(nama, idPendek),
                zip, "Git: " + nama + " commit " + idPendek, http);
        if (galat != null) return galat;
        // Berkas HEAD supaya pull tahu commit terbaru.
        File head = null;
        try {
            head = File.createTempFile("git-head-", ".txt");
            tulisTeks(head, id + "\n");
            galat = SinkronCloud.unggah(token, apiDasar, pemilik, repo,
                    JALUR_GIT_REPO + "/" + namaBerkasHead(nama),
                    head, "Git: " + nama + " HEAD " + idPendek, http);
        } catch (Exception e) {
            return "Gagal mengunggah HEAD: " + e.getMessage();
        } finally {
            if (head != null) head.delete();
        }
        return galat;
    }

    /**
     * Pull commit terbaru dari GitHub (Contents API): unduh HEAD lalu
     * snapshot ZIP, ekstrak ke proyek (menimpa berkas kerja), lalu
     * catat commit hasil tarikan di repo lokal. Mengembalikan null bila
     * berhasil, atau pesan galat.
     */
    public static String pullGithub(String token, String apiDasar, String pemilik,
                                    String repo, File dirProyek,
                                    SinkronCloud.PengirimHttp http, File dirCache) {
        if (token == null || token.isEmpty()) return "Token GitHub belum diisi.";
        if (pemilik == null || pemilik.isEmpty()) {
            return "Nama pengguna GitHub tidak diketahui.";
        }
        if (http == null) return "Pengirim HTTP tidak ada.";
        if (!adaGit(dirProyek)) return "Proyek ini belum punya repo Git.";
        if (dirCache == null) return "Folder cache tidak ditentukan.";
        dirCache.mkdirs();
        String nama = dirProyek.getName();
        File headLokal = new File(dirCache, nama + "-HEAD.txt");
        File headUnduh = SinkronCloud.unduh(token, apiDasar, pemilik, repo,
                JALUR_GIT_REPO + "/" + namaBerkasHead(nama), headLokal, http);
        if (headUnduh == null) {
            return "Tidak ada push GitHub untuk proyek ini (berkas HEAD tidak ditemukan).";
        }
        String id = bacaTeks(headUnduh);
        if (id == null) return "Berkas HEAD dari GitHub tidak bisa dibaca.";
        id = id.trim();
        if (id.isEmpty()) return "Berkas HEAD dari GitHub kosong.";
        if (id.equals(kepala(dirProyek))) {
            return "Sudah terbaru (commit " + id.substring(0, Math.min(7, id.length()))
                    + " sudah ada di HP ini).";
        }
        String idPendek = id.length() > 7 ? id.substring(0, 7) : id;
        File zipTujuan = new File(dirCache, namaBerkasSnapshot(nama, idPendek));
        File zip = SinkronCloud.unduh(token, apiDasar, pemilik, repo,
                JALUR_GIT_REPO + "/" + namaBerkasSnapshot(nama, idPendek),
                zipTujuan, http);
        if (zip == null) {
            return "Snapshot commit " + idPendek + " tidak ditemukan di GitHub.";
        }
        String galat = ArsipProyek.ekstrakZip(zip, dirProyek);
        if (galat != null) return galat;
        // Catat commit hasil tarikan di repo lokal.
        String indukLama = kepala(dirProyek);
        long waktu = System.currentTimeMillis();
        Map<String, String> kerja = new TreeMap<String, String>();
        for (String jalan : kumpulkanBerkas(dirProyek)) {
            kerja.put(jalan, sha1(new File(dirProyek, jalan)));
        }
        try {
            File snap = new File(dirProyek, DIR_GIT + "/snapshots/" + id + ".zip");
            salinBerkas(zip, snap);
            String meta = "waktu=" + waktu + "\n"
                    + "pesan=Ditarik dari GitHub (commit " + idPendek + ")\n"
                    + "induk=" + indukLama + "\n"
                    + "berkas=" + kerja.size() + "\n";
            tulisTeks(new File(dirProyek, DIR_GIT + "/commits/" + id + ".txt"), meta);
            tulisTeks(new File(dirProyek,
                    DIR_GIT + "/refs/heads/" + CABANG_BAWAAN), id + "\n");
            tulisIndeks(dirProyek, kerja);
        } catch (Exception e) {
            return "Snapshot ditarik tetapi gagal dicatat di repo lokal: " + e.getMessage();
        }
        return null;
    }

    // ------------------------------------------------------- pembantu

    /** Daftar jalan relatif berkas kerja (tanpa .git, bin, dan *.zip). */
    public static List<String> kumpulkanBerkas(File dirProyek) {
        List<String> hasil = new ArrayList<String>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        kumpulkanRekursif(dirProyek, dirProyek, "", hasil);
        Collections.sort(hasil);
        return hasil;
    }

    private static void kumpulkanRekursif(File akar, File dir, String awalan,
                                          List<String> hasil) {
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            File a = anak[i];
            if (a.isDirectory()) {
                if (DIR_GIT.equals(a.getName())) continue;
                // Folder hasil build (bin/) di tingkat atas proyek dilewati,
                // sama seperti EksporProyek.
                if ("bin".equals(a.getName()) && a.getParentFile().equals(akar)) continue;
                kumpulkanRekursif(akar, a, awalan + a.getName() + "/", hasil);
            } else {
                if (a.getName().endsWith(".zip")) continue;
                hasil.add(awalan + a.getName());
            }
        }
    }

    /** SHA-1 heksadesimal isi berkas; "" bila gagal dibaca. */
    public static String sha1(File f) {
        if (f == null || !f.isFile()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(f));
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = masuk.read(buf)) > 0) md.update(buf, 0, n);
            } finally {
                masuk.close();
            }
            return keHeksa(md.digest());
        } catch (Exception ignored) {
            return "";
        }
    }

    /** SHA-1 heksadesimal dari teks UTF-8. */
    public static String sha1Teks(String teks) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(teks == null ? new byte[0] : teks.getBytes("UTF-8"));
            return keHeksa(md.digest());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String keHeksa(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    /** Baca index (.git/index.txt): baris "sha  jalan". */
    public static Map<String, String> bacaIndeks(File dirProyek) {
        Map<String, String> hasil = new HashMap<String, String>();
        String isi = bacaTeks(new File(dirProyek, DIR_GIT + "/index.txt"));
        if (isi == null) return hasil;
        for (String baris : isi.split("\n", -1)) {
            if (baris.length() < 42) continue;
            String sha = baris.substring(0, 40);
            String jalan = baris.substring(40).trim();
            if (!jalan.isEmpty()) hasil.put(jalan, sha);
        }
        return hasil;
    }

    /** Tulis index (.git/index.txt) dari peta jalan->sha (urut abjad). */
    public static void tulisIndeks(File dirProyek, Map<String, String> indeks)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        List<String> kunci = new ArrayList<String>(indeks.keySet());
        Collections.sort(kunci);
        for (int i = 0; i < kunci.size(); i++) {
            sb.append(indeks.get(kunci.get(i))).append("  ").append(kunci.get(i)).append("\n");
        }
        tulisTeks(new File(dirProyek, DIR_GIT + "/index.txt"), sb.toString());
    }

    /** Tulis snapshot ZIP isi proyek (tanpa .git/bin/*.zip) ke berkas tujuan. */
    public static boolean tulisSnapshot(File dirProyek, File zip) {
        if (dirProyek == null || !dirProyek.isDirectory() || zip == null) return false;
        File induk = zip.getParentFile();
        if (induk != null) induk.mkdirs();
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)));
            for (String jalan : kumpulkanBerkas(dirProyek)) {
                File f = new File(dirProyek, jalan);
                ZipEntry e = new ZipEntry(jalan);
                zos.putNextEntry(e);
                BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(f));
                try {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = masuk.read(buf)) > 0) zos.write(buf, 0, n);
                } finally {
                    masuk.close();
                }
                zos.closeEntry();
            }
            zos.finish();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (Exception ignored) {
                }
            }
        }
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

    static String bacaTeks(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(f));
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
            } finally {
                masuk.close();
            }
            return new String(keluar.toByteArray(), "UTF-8");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void salinBerkas(File dari, File ke) throws Exception {
        BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(dari));
        BufferedOutputStream keluar = new BufferedOutputStream(new FileOutputStream(ke));
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
        } finally {
            try {
                masuk.close();
            } catch (Exception ignored) {
            }
            try {
                keluar.close();
            } catch (Exception ignored) {
            }
        }
    }
}
