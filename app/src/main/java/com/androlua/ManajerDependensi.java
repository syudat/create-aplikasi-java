package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manajer Dependensi Maven Luring dan Penyelesai Konflik Pustaka
 * (Fitur 7, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Brankas bersama antar-proyek: indeks teks
 *    "koordinat|ukuran|dep1,dep2|sha1|namaBerkas|repo" plus berkas
 *    AAR/JAR/WHL yang benar-benar tersimpan di
 *    brankas-bersama/arsip/grup/artefak/versi/.
 *  - Unduhan nyata saat daring: Maven Central, Google Maven, dan
 *    PyPI (JSON API + berkas WHL). Berkas yang gagal diunduh TIDAK
 *    dicatat sebagai tersedia — hanya koordinatnya saja tidak
 *    cukup.
 *  - Penyelesaian grafik dependensi transitif penuh secara
 *    rekursif: POM Maven diurai (termasuk properti ${...} dan
 *    pengabaian scope test/provided serta dependensi opsional),
 *    dependensi PyPI dibaca dari requires_dist; bentrokan versi
 *    diselaraskan ke versi tertinggi, siklus diputus, kedalaman
 *    dibatasi 25 tingkat.
 *  - Penyelesai konflik dan pengurai baris Gradle seperti semula.
 *
 * BATAS JUJUR: pengunduhan butuh internet — saat luring hanya
 * pustaka yang SUDAH ada di brankas bersama yang bisa dipakai,
 * dan grafik transitif tidak bisa diperluas tanpa POM dari
 * jaringan (yang sudah tercatat di indeks tetap dipakai).
 * Rentang versi Maven ("[1.0,2.0)") tidak diselesaikan — butuh
 * metadata-maven.xml dan tidak ditebak. Verifikasi tanda tangan
 * PGP tidak dilakukan; yang diperiksa hanya sha1 berkas hasil
 * unduhan terhadap bita yang diterima.
 */
public class ManajerDependensi {

    /** Satu pustaka: koordinat Maven "grup:artefak:versi". */
    public static class Pustaka {
        public String grup = "";
        public String artefak = "";
        public String versi = "";
        public long ukuran = 0;
        public List<String> dependensi = new ArrayList<String>();
        /** Sidik jari SHA-1 berkas yang tersimpan di brankas ("" bila belum ada berkas). */
        public String sha1 = "";
        /** Nama berkas di brankas (mis. "gson-2.10.1.jar", "" bila belum ada berkas). */
        public String namaBerkas = "";
        /** Repositori asal: "maven" (Central), "google", atau "pypi". */
        public String repo = "";

        public String koordinat() {
            return grup + ":" + artefak + ":" + versi;
        }

        public String kunci() {
            return grup + ":" + artefak;
        }

        public String toString() {
            return koordinat() + " (" + ukuran + " bita)";
        }
    }

    /** Satu bentrokan versi: artefak + versi-versi yang bertabrakan. */
    public static class Bentrokan {
        public String kunci = "";
        public List<String> versi = new ArrayList<String>();
        public String dipilih = "";
        public String toString() {
            return kunci + ": " + versi + " -> " + dipilih;
        }
    }

    /** Hasil satu unduhan berkas artefak. */
    public static class HasilUnduh {
        public boolean berhasil = false;
        public String url = "";
        public File berkas = null;
        public long ukuran = 0;
        public String sha1 = "";
        public String galat = "";
    }

    /** Nama folder brankas bersama (di samping folder proyek, bukan di dalam satu proyek). */
    public static final String FOLDER_BRANKAS_BERSAMA = "brankas-bersama";
    /** Nama berkas indeks brankas. */
    public static final String NAMA_INDEKS = "brankas-maven.txt";
    /** Nama subfolder tempat berkas AAR/JAR/WHL disimpan. */
    public static final String FOLDER_ARSIP = "arsip";

    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2/";
    public static final String GOOGLE_MAVEN = "https://maven.google.com/";
    public static final String PYPI_JSON = "https://pypi.org/pypi/";

    /** Batas ukuran unduhan artefak (150 MB) supaya ponsel tidak kehabisan memori/ruang. */
    public static final long BATAS_UNDUHAN = 150L * 1024L * 1024L;
    /** Batas ukuran teks yang diambil (POM/JSON, 2 MB). */
    public static final int BATAS_TEKS = 2 * 1024 * 1024;

    /** Bandingkan dua versi angka ("1.2.3" vs "1.10.0"): -1/0/1. */
    public static int bandingVersi(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            int na = angkaAtau(sa);
            int nb = angkaAtau(sb);
            if (na != nb) return na < nb ? -1 : 1;
            if (na == -1 && !sa.equals(sb)) return sa.compareTo(sb);
        }
        return 0;
    }

    private static int angkaAtau(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Urai koordinat "grup:artefak:versi" (null bila bentuk salah). */
    public static Pustaka uraiKoordinat(String koordinat) {
        if (koordinat == null) return null;
        String[] bagian = koordinat.trim().split(":");
        if (bagian.length != 3) return null;
        Pustaka p = new Pustaka();
        p.grup = bagian[0].trim();
        p.artefak = bagian[1].trim();
        p.versi = bagian[2].trim();
        if (p.grup.length() == 0 || p.artefak.length() == 0 || p.versi.length() == 0) return null;
        return p;
    }

    /** Urai baris Gradle: implementation 'g:a:v' / api("g:a:v"). */
    public static Pustaka uraiGradle(String baris) {
        if (baris == null) return null;
        int awal = baris.indexOf('\'');
        int akhir = awal >= 0 ? baris.indexOf('\'', awal + 1) : -1;
        if (awal < 0 || akhir < 0) {
            awal = baris.indexOf('"');
            akhir = awal >= 0 ? baris.indexOf('"', awal + 1) : -1;
        }
        if (awal < 0 || akhir < 0) return null;
        return uraiKoordinat(baris.substring(awal + 1, akhir));
    }

    /** Serialisasi satu pustaka ke baris indeks brankas. */
    public static String keBarisIndeks(Pustaka p) {
        StringBuilder b = new StringBuilder();
        b.append(p.koordinat()).append("|").append(p.ukuran).append("|");
        for (int i = 0; i < p.dependensi.size(); i++) {
            if (i > 0) b.append(",");
            b.append(p.dependensi.get(i));
        }
        if ((p.sha1 != null && p.sha1.length() > 0)
                || (p.namaBerkas != null && p.namaBerkas.length() > 0)
                || (p.repo != null && p.repo.length() > 0)) {
            b.append("|").append(p.sha1 == null ? "" : p.sha1);
            b.append("|").append(p.namaBerkas == null ? "" : p.namaBerkas);
            b.append("|").append(p.repo == null ? "" : p.repo);
        }
        return b.toString();
    }

    /** Urai baris indeks brankas (null bila bentuk salah). */
    public static Pustaka dariBarisIndeks(String baris) {
        if (baris == null) return null;
        String[] bagian = baris.split("\\|", -1);
        if (bagian.length < 2) return null;
        Pustaka p = uraiKoordinat(bagian[0]);
        if (p == null) return null;
        try {
            p.ukuran = Long.parseLong(bagian[1].trim());
        } catch (Throwable t) {
            p.ukuran = 0;
        }
        if (bagian.length >= 3 && bagian[2].trim().length() > 0) {
            String[] dep = bagian[2].split(",");
            for (int i = 0; i < dep.length; i++) {
                if (dep[i].trim().length() > 0) p.dependensi.add(dep[i].trim());
            }
        }
        if (bagian.length >= 4) p.sha1 = bagian[3].trim();
        if (bagian.length >= 5) p.namaBerkas = bagian[4].trim();
        if (bagian.length >= 6) p.repo = bagian[5].trim();
        return p;
    }

    /** Urai seluruh isi indeks brankas menjadi daftar pustaka. */
    public static List<Pustaka> uraiIndeks(String isi) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (isi == null) return hasil;
        String[] baris = isi.split("\n");
        for (int i = 0; i < baris.length; i++) {
            Pustaka p = dariBarisIndeks(baris[i].trim());
            if (p != null) hasil.add(p);
        }
        return hasil;
    }

    /** Cari pustaka di brankas berdasarkan artefak (tidak peka huruf). */
    public static List<Pustaka> cari(List<Pustaka> brankas, String kata) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (brankas == null || kata == null) return hasil;
        String kecil = kata.toLowerCase();
        for (int i = 0; i < brankas.size(); i++) {
            Pustaka p = brankas.get(i);
            if (p.koordinat().toLowerCase().contains(kecil)) hasil.add(p);
        }
        return hasil;
    }

    /** True bila pustaka sudah ada di brankas (koordinat sama persis). */
    public static boolean sudahAda(List<Pustaka> brankas, Pustaka p) {
        if (brankas == null || p == null) return false;
        for (int i = 0; i < brankas.size(); i++) {
            if (p.koordinat().equals(brankas.get(i).koordinat())) return true;
        }
        return false;
    }

    /**
     * Selesaikan bentrokan: untuk tiap artefak yang diminta dalam
     * lebih dari satu versi, pilih versi tertinggi dan catat
     * bentrokannya. Mengembalikan daftar pustaka terselesaikan
     * (satu per artefak).
     */
    public static List<Pustaka> selesaikan(List<Pustaka> diminta,
                                           List<Bentrokan> bentrokanKeluar) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (diminta == null) return hasil;
        for (int i = 0; i < diminta.size(); i++) {
            Pustaka p = diminta.get(i);
            Pustaka ada = null;
            for (int j = 0; j < hasil.size(); j++) {
                if (hasil.get(j).kunci().equals(p.kunci())) ada = hasil.get(j);
            }
            if (ada == null) {
                hasil.add(p);
                continue;
            }
            if (bandingVersi(p.versi, ada.versi) > 0) {
                Bentrokan b = cariBentrokan(bentrokanKeluar, p.kunci());
                if (b == null) {
                    b = new Bentrokan();
                    b.kunci = p.kunci();
                    b.versi.add(ada.versi);
                    b.versi.add(p.versi);
                    if (bentrokanKeluar != null) bentrokanKeluar.add(b);
                } else if (!b.versi.contains(p.versi)) {
                    b.versi.add(p.versi);
                }
                b.dipilih = p.versi;
                hasil.remove(ada);
                hasil.add(p);
            } else if (bandingVersi(p.versi, ada.versi) < 0) {
                Bentrokan b = cariBentrokan(bentrokanKeluar, p.kunci());
                if (b == null) {
                    b = new Bentrokan();
                    b.kunci = p.kunci();
                    b.versi.add(ada.versi);
                    b.versi.add(p.versi);
                    if (bentrokanKeluar != null) bentrokanKeluar.add(b);
                } else if (!b.versi.contains(p.versi)) {
                    b.versi.add(p.versi);
                }
                b.dipilih = ada.versi;
            }
        }
        return hasil;
    }

    private static Bentrokan cariBentrokan(List<Bentrokan> daftar, String kunci) {
        if (daftar == null) return null;
        for (int i = 0; i < daftar.size(); i++) {
            if (kunci.equals(daftar.get(i).kunci)) return daftar.get(i);
        }
        return null;
    }

    private static void catatBentrokan(List<Bentrokan> daftar, String kunci,
                                       String vLama, String vBaru, String dipilih) {
        if (daftar == null) return;
        Bentrokan b = cariBentrokan(daftar, kunci);
        if (b == null) {
            b = new Bentrokan();
            b.kunci = kunci;
            daftar.add(b);
        }
        if (vLama != null && vLama.length() > 0 && !b.versi.contains(vLama)) b.versi.add(vLama);
        if (vBaru != null && vBaru.length() > 0 && !b.versi.contains(vBaru)) b.versi.add(vBaru);
        b.dipilih = dipilih;
    }

    // ==================== Brankas bersama ====================

    /** Folder brankas bersama untuk sebuah akar (mis. folder Download). */
    public static File dirBrankasBersama(File akar) {
        return new File(akar, FOLDER_BRANKAS_BERSAMA);
    }

    /** Berkas indeks brankas bersama. */
    public static File berkasIndeksBersama(File akar) {
        return new File(dirBrankasBersama(akar), NAMA_INDEKS);
    }

    /**
     * Gabung indeks brankas bersama dengan indeks lama per proyek:
     * entri bersama didahulukan, entri proyek yang belum ada ikut
     * masuk (migrasi satu arah ke brankas bersama).
     */
    public static List<Pustaka> gabungBrankas(List<Pustaka> bersama, List<Pustaka> perProyek) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (bersama != null) hasil.addAll(bersama);
        if (perProyek != null) {
            for (int i = 0; i < perProyek.size(); i++) {
                if (!sudahAda(hasil, perProyek.get(i))) hasil.add(perProyek.get(i));
            }
        }
        return hasil;
    }

    /** Jalur berkas artefak di dalam brankas: arsip/grup/artefak/versi/namaBerkas. */
    public static File jalurArsip(File dirBrankas, Pustaka p, String namaBerkas) {
        String grupJalur = p.grup.replace('.', '/').replace(':', '/');
        File dir = new File(new File(new File(new File(dirBrankas, FOLDER_ARSIP),
                grupJalur), p.artefak), p.versi);
        return new File(dir, namaBerkas);
    }

    /** True bila berkas artefak pustaka ini benar-benar ada di brankas. */
    public static boolean berkasAda(File dirBrankas, Pustaka p) {
        if (p == null || p.namaBerkas == null || p.namaBerkas.length() == 0) return false;
        return jalurArsip(dirBrankas, p, p.namaBerkas).isFile();
    }

    /** Serialisasi seluruh indeks brankas menjadi teks. */
    public static String keTeksIndeks(List<Pustaka> brankas) {
        StringBuilder b = new StringBuilder();
        if (brankas == null) return "";
        for (int i = 0; i < brankas.size(); i++) {
            b.append(keBarisIndeks(brankas.get(i))).append("\n");
        }
        return b.toString();
    }

    // ==================== Alamat unduhan ====================

    /** True bila koordinat ini paket Python (grup "pypi"). */
    public static boolean adalahPypi(Pustaka p) {
        return p != null && "pypi".equalsIgnoreCase(p.grup);
    }

    /** Jalur Maven "grup/artefak/versi" (titik grup jadi garis miring). */
    public static String jalurMaven(Pustaka p) {
        return p.grup.replace('.', '/') + "/" + p.artefak + "/" + p.versi + "/"
                + p.artefak + "-" + p.versi;
    }

    /** Alamat POM di Maven Central. */
    public static String urlPomCentral(Pustaka p) {
        return MAVEN_CENTRAL + jalurMaven(p) + ".pom";
    }

    /** Alamat POM di Google Maven. */
    public static String urlPomGoogle(Pustaka p) {
        return GOOGLE_MAVEN + jalurMaven(p) + ".pom";
    }

    /**
     * Daftar alamat kandidat berkas artefak, urut dicoba: AAR lalu
     * JAR di Central, lalu AAR lalu JAR di Google Maven. Repo
     * "google" hanya mencoba Google Maven.
     */
    public static List<String> urlKandidat(Pustaka p) {
        List<String> hasil = new ArrayList<String>();
        if (p == null) return hasil;
        String dasar = jalurMaven(p);
        if ("google".equalsIgnoreCase(p.repo)) {
            hasil.add(GOOGLE_MAVEN + dasar + ".aar");
            hasil.add(GOOGLE_MAVEN + dasar + ".jar");
            return hasil;
        }
        hasil.add(MAVEN_CENTRAL + dasar + ".aar");
        hasil.add(MAVEN_CENTRAL + dasar + ".jar");
        hasil.add(GOOGLE_MAVEN + dasar + ".aar");
        hasil.add(GOOGLE_MAVEN + dasar + ".jar");
        return hasil;
    }

    /** Alamat JSON PyPI untuk satu paket + versi ("latest" bila versi kosong). */
    public static String urlJsonPypi(String nama, String versi) {
        String v = versi == null || versi.length() == 0 ? "" : versi + "/";
        return PYPI_JSON + nama + "/" + v + "json";
    }

    /** Nama berkas dari alamat (bagian sesudah garis miring terakhir). */
    public static String namaBerkasDariUrl(String url) {
        if (url == null) return "";
        int i = url.lastIndexOf('/');
        if (i < 0) return url;
        String nama = url.substring(i + 1);
        int tanya = nama.indexOf('?');
        if (tanya >= 0) nama = nama.substring(0, tanya);
        return nama;
    }

    // ==================== Pengambil berkas (jaringan) ====================

    /**
     * Pengambil berkas lewat jaringan. Dipisah sebagai antarmuka
     * supaya uji JVM bisa memakai pengambil palsu tanpa internet.
     */
    public interface PengambilBerkas {
        /** Ambil isi URL sebagai teks UTF-8; null bila gagal/tidak ada. */
        String ambilTeks(String url);

        /**
         * Unduh URL ke berkas tujuan (menimpa bila ada); kembalikan
         * jumlah bita yang ditulis, atau -1 bila gagal.
         */
        long salinKeBerkas(String url, File tujuan);
    }

    /** Pengambil lewat HttpURLConnection (jalan di JVM maupun Android). */
    public static class PengambilHttp implements PengambilBerkas {
        public String ambilTeks(String url) {
            byte[] data = ambilBita(url, BATAS_TEKS);
            if (data == null) return null;
            try {
                return new String(data, "UTF-8");
            } catch (Throwable t) {
                return null;
            }
        }

        public long salinKeBerkas(String url, File tujuan) {
            HttpURLConnection k = null;
            InputStream in = null;
            OutputStream out = null;
            try {
                k = buka(url);
                if (k == null) return -1;
                if (k.getResponseCode() != HttpURLConnection.HTTP_OK) return -1;
                if (tujuan.getParentFile() != null) tujuan.getParentFile().mkdirs();
                in = k.getInputStream();
                out = new FileOutputStream(tujuan);
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > BATAS_UNDUHAN) return -1;
                    out.write(buf, 0, n);
                }
                out.flush();
                return total;
            } catch (Throwable t) {
                return -1;
            } finally {
                try { if (out != null) out.close(); } catch (Throwable ignored) { }
                try { if (in != null) in.close(); } catch (Throwable ignored) { }
                if (k != null) k.disconnect();
            }
        }

        private byte[] ambilBita(String url, int batas) {
            HttpURLConnection k = null;
            InputStream in = null;
            try {
                k = buka(url);
                if (k == null) return null;
                if (k.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                in = k.getInputStream();
                byte[] buf = new byte[8192];
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (b.size() + n > batas) return null;
                    b.write(buf, 0, n);
                }
                return b.toByteArray();
            } catch (Throwable t) {
                return null;
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) { }
                if (k != null) k.disconnect();
            }
        }

        private HttpURLConnection buka(String url) {
            try {
                HttpURLConnection k = (HttpURLConnection) new URL(url).openConnection();
                k.setConnectTimeout(15000);
                k.setReadTimeout(30000);
                k.setInstanceFollowRedirects(true);
                k.setRequestProperty("User-Agent", "Create-Aplikasi/5.8");
                k.setRequestProperty("Accept", "*/*");
                return k;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    // ==================== Sidik jari ====================

    /** SHA-1 heksadesimal dari bita ("" bila gagal). */
    public static String sha1Hex(byte[] data) {
        if (data == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(data);
            return keHex(md.digest());
        } catch (Throwable t) {
            return "";
        }
    }

    /** SHA-1 heksadesimal dari isi berkas ("" bila gagal). */
    public static String sha1Berkas(File f) {
        if (f == null || !f.isFile()) return "";
        InputStream in = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            in = new FileInputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return keHex(md.digest());
        } catch (Throwable t) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
        }
    }

    private static String keHex(byte[] bita) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bita.length; i++) {
            int v = bita[i] & 0xff;
            if (v < 16) b.append('0');
            b.append(Integer.toHexString(v));
        }
        return b.toString();
    }

    // ==================== Unduhan artefak ====================

    /**
     * Unduh berkas AAR/JAR (Maven/Google) atau WHL (PyPI) untuk satu
     * pustaka ke brankas bersama. Bila berkas sudah ada di brankas,
     * dipakai ulang tanpa mengunduh. Mengisi ukuran, sha1, dan
     * namaBerkas pada pustaka bila berhasil; bila gagal, pustaka
     * TIDAK diubah dan hasil.galat menjelaskan sebabnya.
     */
    public static HasilUnduh unduh(Pustaka p, File dirBrankas, PengambilBerkas pengambil) {
        HasilUnduh h = new HasilUnduh();
        if (p == null || dirBrankas == null || pengambil == null) {
            h.galat = "Pustaka, brankas, atau pengambil tidak ada.";
            return h;
        }
        if (adalahPypi(p)) return unduhPypi(p, dirBrankas, pengambil);
        List<String> kandidat = urlKandidat(p);
        String galatTerakhir = "tidak ditemukan di repositori.";
        for (int i = 0; i < kandidat.size(); i++) {
            String url = kandidat.get(i);
            String nama = namaBerkasDariUrl(url);
            File tujuan = jalurArsip(dirBrankas, p, nama);
            if (tujuan.isFile() && tujuan.length() > 0) {
                h.berhasil = true;
                h.url = url;
                h.berkas = tujuan;
                h.ukuran = tujuan.length();
                h.sha1 = sha1Berkas(tujuan);
                p.ukuran = h.ukuran;
                p.sha1 = h.sha1;
                p.namaBerkas = nama;
                return h;
            }
            long n = pengambil.salinKeBerkas(url, tujuan);
            if (n > 0) {
                h.berhasil = true;
                h.url = url;
                h.berkas = tujuan;
                h.ukuran = n;
                h.sha1 = sha1Berkas(tujuan);
                p.ukuran = n;
                p.sha1 = h.sha1;
                p.namaBerkas = nama;
                return h;
            }
            if (tujuan.isFile()) tujuan.delete();
            galatTerakhir = "tidak ditemukan di " + url + ".";
        }
        h.galat = galatTerakhir;
        return h;
    }

    /** Unduh WHL dari PyPI: baca JSON paket, pilih berkas .whl, unduh. */
    private static HasilUnduh unduhPypi(Pustaka p, File dirBrankas, PengambilBerkas pengambil) {
        HasilUnduh h = new HasilUnduh();
        String versi = p.versi;
        if (versi == null || versi.length() == 0 || "latest".equalsIgnoreCase(versi)) {
            String json = pengambil.ambilTeks(urlJsonPypi(p.artefak, ""));
            versi = versiTerbaruDariJson(json);
            if (versi == null || versi.length() == 0) {
                h.galat = "Paket " + p.artefak + " tidak ditemukan di PyPI.";
                return h;
            }
            p.versi = versi;
        }
        String json = pengambil.ambilTeks(urlJsonPypi(p.artefak, versi));
        String url = urlWhlDariJson(json);
        if (url == null || url.length() == 0) {
            h.galat = "Tidak ada berkas WHL untuk " + p.artefak + " " + versi + " di PyPI.";
            return h;
        }
        String nama = namaBerkasDariUrl(url);
        File tujuan = jalurArsip(dirBrankas, p, nama);
        if (tujuan.isFile() && tujuan.length() > 0) {
            h.berhasil = true;
            h.url = url;
            h.berkas = tujuan;
            h.ukuran = tujuan.length();
            h.sha1 = sha1Berkas(tujuan);
            p.ukuran = h.ukuran;
            p.sha1 = h.sha1;
            p.namaBerkas = nama;
            return h;
        }
        long n = pengambil.salinKeBerkas(url, tujuan);
        if (n > 0) {
            h.berhasil = true;
            h.url = url;
            h.berkas = tujuan;
            h.ukuran = n;
            h.sha1 = sha1Berkas(tujuan);
            p.ukuran = n;
            p.sha1 = h.sha1;
            p.namaBerkas = nama;
            return h;
        }
        if (tujuan.isFile()) tujuan.delete();
        h.galat = "Gagal mengunduh " + url + ".";
        return h;
    }

    /** Ambil teks POM satu pustaka (Central dulu, lalu Google Maven); null bila tidak ada. */
    public static String unduhPom(Pustaka p, PengambilBerkas pengambil) {
        if (p == null || pengambil == null) return null;
        String pom = pengambil.ambilTeks(urlPomCentral(p));
        if (pom != null && pom.contains("<project")) return pom;
        pom = pengambil.ambilTeks(urlPomGoogle(p));
        if (pom != null && pom.contains("<project")) return pom;
        return null;
    }

    // ==================== Urai POM Maven ====================

    /**
     * Urai dependensi langsung dari teks POM: blok
     * &lt;dependency&gt; di luar dependencyManagement dan plugin,
     * dengan substitusi properti ${...}. Dependensi scope
     * test/provided/system/import dan optional=true dilewati.
     * Rentang versi ("[1.0,2.0)") dilewati karena tidak bisa
     * diputuskan tanpa metadata.
     */
    public static List<Pustaka> uraiPom(String pom) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (pom == null) return hasil;
        String bersih = hapusKomentarXml(pom);
        bersih = hapusBagian(bersih, "dependencyManagement");
        bersih = hapusBagian(bersih, "plugin");
        bersih = hapusBagian(bersih, "build");
        Map<String, String> properti = new LinkedHashMap<String, String>();
        Matcher mp = Pattern.compile("<properties>(.*?)</properties>",
                Pattern.DOTALL).matcher(bersih);
        if (mp.find()) {
            Matcher mk = Pattern.compile("<([A-Za-z0-9_.\\-]+)>([^<]*)</\\1>")
                    .matcher(mp.group(1));
            while (mk.find()) properti.put(mk.group(1).trim(), mk.group(2).trim());
        }
        String versiProyek = cariVersiProyek(bersih);
        if (versiProyek != null) properti.put("project.version", versiProyek);
        String versiInduk = cariVersiInduk(bersih);
        if (versiInduk != null) properti.put("parent.version", versiInduk);
        Matcher md = Pattern.compile("<dependency>(.*?)</dependency>",
                Pattern.DOTALL).matcher(bersih);
        while (md.find()) {
            String blok = md.group(1);
            String grup = ambilTag(blok, "groupId");
            String artefak = ambilTag(blok, "artifactId");
            String versi = ambilTag(blok, "version");
            String scope = ambilTag(blok, "scope");
            String opsional = ambilTag(blok, "optional");
            if (grup == null || artefak == null || versi == null) continue;
            grup = gantiProperti(grup.trim(), properti);
            artefak = gantiProperti(artefak.trim(), properti);
            versi = gantiProperti(versi.trim(), properti);
            if (grup.length() == 0 || artefak.length() == 0 || versi.length() == 0) continue;
            if (versi.contains("${")) continue;
            if (versi.indexOf('[') >= 0 || versi.indexOf('(') >= 0 || versi.indexOf(',') >= 0) continue;
            if (scope != null) {
                String s = scope.trim().toLowerCase();
                if ("test".equals(s) || "provided".equals(s) || "system".equals(s)
                        || "import".equals(s)) continue;
            }
            if (opsional != null && "true".equalsIgnoreCase(opsional.trim())) continue;
            Pustaka p = new Pustaka();
            p.grup = grup;
            p.artefak = artefak;
            p.versi = versi;
            hasil.add(p);
        }
        return hasil;
    }

    private static String hapusKomentarXml(String s) {
        return s.replaceAll("(?s)<!--.*?-->", "");
    }

    private static String hapusBagian(String s, String tag) {
        return s.replaceAll("(?s)<" + tag + "(\\s[^>]*)?>.*?</" + tag + ">", "");
    }

    private static String ambilTag(String blok, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(blok);
        return m.find() ? m.group(1) : null;
    }

    private static String gantiProperti(String nilai, Map<String, String> properti) {
        if (nilai == null) return "";
        String hasil = nilai;
        for (int i = 0; i < 5; i++) {
            Matcher m = Pattern.compile("\\$\\{([^}]+)\\}").matcher(hasil);
            StringBuffer b = new StringBuffer();
            boolean ketemu = false;
            while (m.find()) {
                String ganti = properti.get(m.group(1).trim());
                if (ganti == null) ganti = m.group(0);
                else ketemu = true;
                m.appendReplacement(b, Matcher.quoteReplacement(ganti));
            }
            m.appendTail(b);
            hasil = b.toString();
            if (!ketemu) break;
        }
        return hasil;
    }

    private static String cariVersiProyek(String pom) {
        Matcher m = Pattern.compile("<project[^>]*>\\s*(?:<modelVersion>[^<]*</modelVersion>)?\\s*"
                + "(?:<parent>.*?</parent>)?\\s*(?:<groupId>[^<]*</groupId>)?\\s*"
                + "(?:<artifactId>[^<]*</artifactId>)?\\s*<version>([^<]+)</version>",
                Pattern.DOTALL).matcher(pom);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String cariVersiInduk(String pom) {
        Matcher m = Pattern.compile("<parent>.*?<version>([^<]+)</version>.*?</parent>",
                Pattern.DOTALL).matcher(pom);
        return m.find() ? m.group(1).trim() : null;
    }

    // ==================== Urai JSON PyPI ====================

    /** Versi terbaru paket dari JSON PyPI ("info.version"); null bila tidak ketemu. */
    public static String versiTerbaruDariJson(String json) {
        if (json == null) return null;
        Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Alamat berkas .whl dari JSON PyPI (pasangan filename/url di
     * dalam daftar "urls"); null bila tidak ada WHL.
     */
    public static String urlWhlDariJson(String json) {
        if (json == null) return null;
        Matcher m = Pattern.compile("\"(filename|url)\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        String namaTerakhir = null;
        while (m.find()) {
            if ("filename".equals(m.group(1))) {
                namaTerakhir = m.group(2);
            } else if ("url".equals(m.group(1)) && namaTerakhir != null
                    && namaTerakhir.toLowerCase().endsWith(".whl")) {
                return m.group(2);
            }
        }
        return null;
    }

    /**
     * Versi rilis tertinggi dari JSON PyPI yang memenuhi batas bawah
     * (">="); null bila tidak ada yang memenuhi.
     */
    public static String versiRilisDariJson(String json, String batasBawah) {
        if (json == null) return null;
        int awal = json.indexOf("\"releases\"");
        if (awal < 0) return null;
        Matcher m = Pattern.compile("\"(\\d[\\dA-Za-z.\\-]*)\"\\s*:\\s*\\[").matcher(json);
        m.region(awal, json.length());
        String terbaik = null;
        while (m.find()) {
            String v = m.group(1);
            if (batasBawah != null && bandingVersi(v, batasBawah) < 0) continue;
            if (terbaik == null || bandingVersi(v, terbaik) > 0) terbaik = v;
        }
        return terbaik;
    }

    /** Satu syarat dependensi Python ("nama (>=1.2)"). */
    public static class SyaratPython {
        public String nama = "";
        public String operator = "";
        public String versi = "";
    }

    /** Urai satu baris Requires-Dist ("nama (>=1.2); python_version..."). */
    public static SyaratPython uraiRequiresDist(String baris) {
        if (baris == null) return null;
        String b = baris.trim();
        int titikKoma = b.indexOf(';');
        if (titikKoma >= 0) b = b.substring(0, titikKoma).trim();
        if (b.length() == 0) return null;
        SyaratPython s = new SyaratPython();
        int awal = b.indexOf('(');
        if (awal < 0) {
            s.nama = b.replaceAll("\\s+.*$", "").trim();
            return s.nama.length() > 0 ? s : null;
        }
        s.nama = b.substring(0, awal).trim();
        int akhir = b.indexOf(')', awal);
        if (akhir < 0) return null;
        String isi = b.substring(awal + 1, akhir).trim();
        int koma = isi.indexOf(',');
        if (koma >= 0) isi = isi.substring(0, koma).trim();
        if (isi.length() == 0) return s;
        String[] operator = {">=", "<=", "==", "~=", "!=", ">", "<"};
        for (int i = 0; i < operator.length; i++) {
            if (isi.startsWith(operator[i])) {
                s.operator = operator[i];
                s.versi = isi.substring(operator[i].length()).trim();
                return s.nama.length() > 0 ? s : null;
            }
        }
        s.versi = isi;
        return s.nama.length() > 0 ? s : null;
    }

    /** Urai semua baris Requires-Dist dari teks METADATA sebuah WHL. */
    public static List<SyaratPython> uraiMetadataWhl(String metadata) {
        List<SyaratPython> hasil = new ArrayList<SyaratPython>();
        if (metadata == null) return hasil;
        String[] baris = metadata.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.toLowerCase().startsWith("requires-dist:")) {
                SyaratPython s = uraiRequiresDist(b.substring("requires-dist:".length()).trim());
                if (s != null) hasil.add(s);
            }
        }
        return hasil;
    }

    /**
     * Baca dependensi langsung dari berkas WHL (METADATA di dalam
     * zip) dan selesaikan versinya lewat JSON PyPI.
     */
    public static List<Pustaka> dependensiWhl(File whl, PengambilBerkas pengambil) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (whl == null || !whl.isFile()) return hasil;
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new FileInputStream(whl));
            ZipEntry e;
            String metadata = null;
            while ((e = zin.getNextEntry()) != null) {
                String nama = e.getName();
                if (nama.endsWith(".dist-info/METADATA")) {
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zin.read(buf)) > 0) b.write(buf, 0, n);
                    metadata = new String(b.toByteArray(), "UTF-8");
                    break;
                }
            }
            if (metadata == null) return hasil;
            List<SyaratPython> syarat = uraiMetadataWhl(metadata);
            for (int i = 0; i < syarat.size(); i++) {
                SyaratPython s = syarat.get(i);
                Pustaka p = new Pustaka();
                p.grup = "pypi";
                p.artefak = s.nama;
                p.versi = selesaikanVersiPypi(s, pengambil);
                if (p.versi == null) p.versi = "";
                if (p.versi.length() == 0) continue;
                hasil.add(p);
            }
        } catch (Throwable t) {
            return hasil;
        } finally {
            try { if (zin != null) zin.close(); } catch (Throwable ignored) { }
        }
        return hasil;
    }

    /** Selesaikan versi paket PyPI dari syarat ("==", ">=", "~=", atau tanpa syarat). */
    static String selesaikanVersiPypi(SyaratPython s, PengambilBerkas pengambil) {
        if (s == null) return "";
        if (s.versi == null || s.versi.length() == 0) {
            if (pengambil == null) return "";
            return versiTerbaruDariJson(pengambil.ambilTeks(urlJsonPypi(s.nama, "")));
        }
        if ("==".equals(s.operator)) return s.versi;
        if (pengambil == null) return "";
        String json = pengambil.ambilTeks(urlJsonPypi(s.nama, ""));
        if (">=".equals(s.operator) || "~=".equals(s.operator)) {
            return versiRilisDariJson(json, s.versi);
        }
        return versiTerbaruDariJson(json);
    }

    // ==================== Grafik transitif ====================

    /** Sumber dependensi langsung satu pustaka (POM/JSON/METADATA). */
    public interface SumberDependensi {
        /** Dependensi langsung; null bila tidak bisa dibaca (mis. luring). */
        List<Pustaka> dependensiLangsung(Pustaka p);
    }

    /** Sumber lewat jaringan: POM Maven/Google untuk Java, JSON PyPI untuk Python. */
    public static class SumberJaringan implements SumberDependensi {
        private final PengambilBerkas pengambil;

        public SumberJaringan(PengambilBerkas pengambil) {
            this.pengambil = pengambil;
        }

        public List<Pustaka> dependensiLangsung(Pustaka p) {
            if (p == null || pengambil == null) return null;
            if (adalahPypi(p)) {
                String json = pengambil.ambilTeks(urlJsonPypi(p.artefak, p.versi));
                if (json == null) return null;
                List<Pustaka> hasil = new ArrayList<Pustaka>();
                Matcher m = Pattern.compile("\"requires_dist\"\\s*:\\s*\\[(.*?)\\]",
                        Pattern.DOTALL).matcher(json);
                if (!m.find()) return hasil;
                Matcher mk = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
                while (mk.find()) {
                    SyaratPython s = uraiRequiresDist(mk.group(1));
                    if (s == null) continue;
                    Pustaka d = new Pustaka();
                    d.grup = "pypi";
                    d.artefak = s.nama;
                    d.versi = selesaikanVersiPypi(s, pengambil);
                    if (d.versi == null || d.versi.length() == 0) continue;
                    hasil.add(d);
                }
                return hasil;
            }
            String pom = unduhPom(p, pengambil);
            if (pom == null) return null;
            return uraiPom(pom);
        }
    }

    /** Kedalaman maksimum penelusuran transitif (pemutus siklus yang dalam). */
    public static final int BATAS_KEDALAMAN_TRANSITIF = 25;

    /**
     * Selesaikan grafik dependensi transitif penuh secara rekursif
     * dari daftar akar: dependensi turunan ikut diselesaikan, versi
     * tertinggi menang di tiap artefak, bentrokan dicatat, siklus
     * diputus lewat peta "sudah dipilih", dan pustaka yang
     * dependensinya tidak bisa dibaca dicatat di masalahKeluar
     * (bukan digagalkan seluruhnya).
     */
    public static List<Pustaka> selesaikanTransitif(List<Pustaka> akar,
            SumberDependensi sumber, List<Bentrokan> bentrokanKeluar,
            List<String> masalahKeluar) {
        List<Pustaka> hasil = new ArrayList<Pustaka>();
        if (akar == null) return hasil;
        Map<String, Pustaka> dipilih = new LinkedHashMap<String, Pustaka>();
        List<Pustaka> antre = new ArrayList<Pustaka>();
        List<Integer> dalam = new ArrayList<Integer>();
        for (int i = 0; i < akar.size(); i++) {
            if (akar.get(i) == null) continue;
            antre.add(akar.get(i));
            dalam.add(Integer.valueOf(0));
        }
        int posisi = 0;
        while (posisi < antre.size()) {
            Pustaka p = antre.get(posisi);
            int kedalaman = dalam.get(posisi).intValue();
            posisi++;
            if (kedalaman > BATAS_KEDALAMAN_TRANSITIF) {
                if (masalahKeluar != null) {
                    masalahKeluar.add("Rantai terlalu dalam, diputus di " + p.koordinat() + ".");
                }
                continue;
            }
            Pustaka ada = dipilih.get(p.kunci());
            if (ada != null && bandingVersi(ada.versi, p.versi) >= 0) continue;
            if (ada != null) {
                catatBentrokan(bentrokanKeluar, p.kunci(), ada.versi, p.versi, p.versi);
            }
            dipilih.put(p.kunci(), p);
            List<Pustaka> turunan = null;
            try {
                turunan = sumber == null ? null : sumber.dependensiLangsung(p);
            } catch (Throwable t) {
                turunan = null;
            }
            if (turunan == null) {
                if (masalahKeluar != null) {
                    masalahKeluar.add("Dependensi " + p.koordinat()
                            + " tidak bisa dibaca (luring atau tidak ada di repositori).");
                }
                continue;
            }
            for (int i = 0; i < turunan.size(); i++) {
                if (turunan.get(i) == null) continue;
                antre.add(turunan.get(i));
                dalam.add(Integer.valueOf(kedalaman + 1));
            }
        }
        hasil.addAll(dipilih.values());
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahBrankas, List<Bentrokan> bentrokan) {
        int b = bentrokan == null ? 0 : bentrokan.size();
        return "Pustaka di brankas: " + jumlahBrankas + ". Bentrokan ditemukan: " + b + ".";
    }
}
