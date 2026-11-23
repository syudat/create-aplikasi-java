package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Sinkronisasi Cloud (Tahap 3F).
 *
 * Dua jalur yang NYATA (bukan tombol mati):
 * (a) Cloud lokal: cadangan proyek ke folder Download/cloud (stempel
 *     waktu, sama seperti ArsipProyek.buatCadangan), daftar cadangan,
 *     dan pulihkan kembali ke Download/project (konflik nama otomatis
 *     menjadi "Nama 2", "Nama 3", ...).
 * (b) GitHub: unggah ZIP proyek ke repo privat pengguna (Contents API,
 *     repo bawaan "pembuat-aplikasi-build", folder "cadangan") dan unduh
 *     kembali. Token dibaca dari GithubSettings (berkas github.cfg yang
 *     sama); nama pemilik diambil dari GET /user.
 *
 * KETERBATASAN YANG JUJUR: ini cadangan + pulihkan manual, BUKAN
 * sinkronisasi otomatis dua arah (butuh server yang membandingkan
 * perubahan di dua perangkat). Nama "Sinkronisasi Cloud" dipakai karena
 * itu nama fiturnya; di layar dijelaskan bahwa cadangannya manual.
 *
 * HTTP dibuat lewat antarmuka PengirimHttp supaya logika GitHub bisa
 * diuji di JRE dengan pengirim palsu; implementasi bawaan memakai
 * HttpURLConnection. Murni java.io/java.net/java.util (tanpa API Android).
 */
public class SinkronCloud {

    /** Nama folder cloud lokal di bawah Download. */
    public static final String DIR_CLOUD = "cloud";

    /** Repo privat bawaan untuk cadangan (sama seperti di GithubSettings). */
    public static final String REPO_CADANGAN = "pembuat-aplikasi-build";

    /** Folder cadangan di dalam repo. */
    public static final String JALUR_CLOUD = "cadangan";

    /** API GitHub bawaan (bisa diganti alamat server palsu saat pengujian). */
    public static final String API_GITHUB = "https://api.github.com";

    // ------------------------------------------------------------ HTTP

    /** Satu balasan HTTP: kode + tubuh (teks). */
    public static class Respon {
        public final int kode;
        public final String tubuh;
        public Respon(int kode, String tubuh) {
            this.kode = kode;
            this.tubuh = tubuh == null ? "" : tubuh;
        }
    }

    /** Pengirim HTTP (bisa diganti pengirim palsu saat pengujian). */
    public interface PengirimHttp {
        Respon kirim(String metode, String url, String token, String tubuh) throws IOException;
    }

    /** Pengirim HTTP bawaan (HttpURLConnection, seperti kirimPermintaanGitHub di Lua). */
    public static class PengirimHttpBawaan implements PengirimHttp {
        @Override
        public Respon kirim(String metode, String url, String token, String tubuh) throws IOException {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod(metode);
                c.setConnectTimeout(30000);
                c.setReadTimeout(60000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", TeksUI.t("Create-Aplikasi"));
                if (token != null && !token.isEmpty()) {
                    c.setRequestProperty("Authorization", "token " + token);
                }
                if (tubuh != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] data = tubuh.getBytes("UTF-8");
                    c.setRequestProperty("Content-Length", String.valueOf(data.length));
                    OutputStream out = c.getOutputStream();
                    try {
                        out.write(data);
                    } finally {
                        out.close();
                    }
                }
                int kode = c.getResponseCode();
                InputStream masuk = kode >= 200 && kode < 300
                        ? c.getInputStream() : c.getErrorStream();
                String isi = "";
                if (masuk != null) {
                    isi = bacaAliran(masuk);
                    masuk.close();
                }
                return new Respon(kode, isi);
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    private static String bacaAliran(InputStream masuk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = masuk.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), "UTF-8");
    }

    // ------------------------------------------------------- cloud lokal

    /** Cadangan proyek ke folder cloud lokal; berkas cadangan bila berhasil, null bila gagal. */
    public static File buatCadanganCloud(File dirProyek, File dirCloud) {
        return ArsipProyek.buatCadangan(dirProyek, dirCloud);
    }

    /** Daftar cadangan cloud lokal (*.zip), terbaru dulu. */
    public static List<File> daftarCadanganCloud(File dirCloud) {
        return ArsipProyek.daftarCadangan(dirCloud);
    }

    /** Nama tujuan yang belum dipakai di Download/project ("Nama", "Nama 2", "Nama 3", ...). */
    public static String namaTujuanUnik(File dirRoot, String nama) {
        if (dirRoot == null || nama == null || nama.isEmpty()) return TeksUI.t("Proyek");
        if (!new File(dirRoot, nama).exists()) return nama;
        int i = 2;
        while (new File(dirRoot, nama + " " + i).exists()) i++;
        return nama + " " + i;
    }

    /**
     * Pulihkan cadangan cloud lokal ke Download/project. Nama proyek
     * diambil dari isi ZIP (bila ada pembungkus) atau nama berkas; bila
     * nama sudah ada, dipakai nama unik ("Nama 2"). Mengembalikan null
     * bila berhasil, atau pesan galat.
     */
    public static String pulihkan(File zip, File dirRoot) {
        if (zip == null || !zip.isFile()) return TeksUI.t("Berkas cadangan tidak ditemukan.");
        if (dirRoot == null) return TeksUI.t("Folder proyek tidak ditentukan.");
        String nama = ArsipProyek.namaProyekDariIsi(zip);
        if (nama == null || nama.isEmpty()) {
            nama = ArsipProyek.namaProyekDariBerkas(zip.getName());
        }
        String tujuan = namaTujuanUnik(dirRoot, nama);
        return ArsipProyek.ekstrakZip(zip, new File(dirRoot, tujuan));
    }

    // ------------------------------------------------------------ GitHub

    /** Nama pengguna GitHub dari token (GET /user); "" bila gagal. */
    public static String namaPengguna(String token, String apiDasar, PengirimHttp http) {
        if (token == null || token.isEmpty() || http == null) return "";
        try {
            Respon r = http.kirim("GET", apiDasar + "/user", token, null);
            if (r.kode != 200) return "";
            return ambilString(r.tubuh, "login");
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Unggah ZIP proyek ke repo GitHub (PUT Contents API). Bila berkas
     * sudah ada di repo (422), diambil sha-nya lalu diunggah ulang sebagai
     * pembaruan. Mengembalikan null bila berhasil, atau pesan galat.
     */
    public static String unggah(String token, String apiDasar, String pemilik, String repo,
                                String jalurRepo, File zip, String pesan, PengirimHttp http) {
        if (token == null || token.isEmpty()) return TeksUI.t("Token GitHub belum diisi.");
        if (pemilik == null || pemilik.isEmpty()) return TeksUI.t("Nama pengguna GitHub tidak diketahui.");
        if (zip == null || !zip.isFile()) return TeksUI.t("Berkas ZIP tidak ditemukan.");
        if (http == null) return TeksUI.t("Pengirim HTTP tidak ada.");
        String isi64 = bacaBase64(zip);
        if (isi64 == null) return TeksUI.t("Gagal membaca berkas ZIP.");
        String url = apiDasar + "/repos/" + pemilik + "/" + repo + "/contents/" + jalurRepo;
        try {
            String tubuh = "{\"message\":" + jsonKutip(pesan == null ? TeksUI.t("Cadangan proyek") : pesan)
                    + ",\"content\":" + jsonKutip(isi64) + "}";
            Respon r = http.kirim("PUT", url, token, tubuh);
            if (r.kode == 200 || r.kode == 201) return null;
            if (r.kode == 422) {
                // Berkas sudah ada: ambil sha lalu unggah ulang sebagai pembaruan.
                String sha = shaBerkas(token, apiDasar, pemilik, repo, jalurRepo, http);
                if (sha == null || sha.isEmpty()) {
                    return TeksUI.t("Gagal mengunggah (HTTP 422) dan sha lama tidak bisa dibaca.");
                }
                String tubuh2 = "{\"message\":" + jsonKutip(pesan == null ? TeksUI.t("Cadangan proyek") : pesan)
                        + ",\"content\":" + jsonKutip(isi64)
                        + ",\"sha\":" + jsonKutip(sha) + "}";
                Respon r2 = http.kirim("PUT", url, token, tubuh2);
                if (r2.kode == 200 || r2.kode == 201) return null;
                return TeksUI.t("Gagal mengunggah ke GitHub (HTTP ") + r2.kode + ").";
            }
            if (r.kode == 401) return TeksUI.t("Token GitHub tidak valid (HTTP 401). Periksa token di Pengaturan GitHub.");
            if (r.kode == 404) return "Repo \"" + repo + TeksUI.t("\" tidak ditemukan (HTTP 404). Buat dulu repo privat itu di GitHub.");
            return TeksUI.t("Gagal mengunggah ke GitHub (HTTP ") + r.kode + ").";
        } catch (Exception e) {
            return TeksUI.t("Gagal mengunggah ke GitHub: ") + e.getMessage();
        }
    }

    /** sha berkas yang sudah ada di repo ("" bila tidak ada / gagal). */
    public static String shaBerkas(String token, String apiDasar, String pemilik, String repo,
                                   String jalurRepo, PengirimHttp http) {
        if (http == null) return "";
        try {
            Respon r = http.kirim("GET", apiDasar + "/repos/" + pemilik + "/" + repo
                    + "/contents/" + jalurRepo, token, null);
            if (r.kode != 200) return "";
            return ambilString(r.tubuh, "sha");
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Daftar nama berkas cadangan (*.zip) di folder repo GitHub. */
    public static List<String> daftarGithub(String token, String apiDasar, String pemilik,
                                            String repo, String jalurRepo, PengirimHttp http) {
        List<String> hasil = new ArrayList<String>();
        if (token == null || token.isEmpty() || http == null) return hasil;
        try {
            Respon r = http.kirim("GET", apiDasar + "/repos/" + pemilik + "/" + repo
                    + "/contents/" + jalurRepo, token, null);
            if (r.kode != 200) return hasil;
            for (String nama : ambilDaftarNama(r.tubuh)) {
                if (nama.toLowerCase().endsWith(".zip")) hasil.add(nama);
            }
        } catch (Exception ignored) {
        }
        return hasil;
    }

    /**
     * Unduh satu berkas cadangan dari repo GitHub ke berkas tujuan.
     * Mengembalikan berkas tujuan bila berhasil, null bila gagal.
     */
    public static File unduh(String token, String apiDasar, String pemilik, String repo,
                             String jalurRepo, File tujuan, PengirimHttp http) {
        if (token == null || token.isEmpty() || http == null || tujuan == null) return null;
        try {
            Respon r = http.kirim("GET", apiDasar + "/repos/" + pemilik + "/" + repo
                    + "/contents/" + jalurRepo, token, null);
            if (r.kode != 200) return null;
            String isi64 = ambilString(r.tubuh, "content");
            if (isi64 == null || isi64.isEmpty()) return null;
            isi64 = isi64.replaceAll("\\s", "");
            byte[] data = Base64.getDecoder().decode(isi64);
            File induk = tujuan.getParentFile();
            if (induk != null) induk.mkdirs();
            FileOutputStream out = new FileOutputStream(tujuan);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            if (tujuan.isFile() && tujuan.length() > 0) return tujuan;
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ------------------------------------------------------- JSON mini

    /** Nilai string untuk kunci "kunci" di JSON sederhana; "" bila tidak ada. */
    public static String ambilString(String json, String kunci) {
        if (json == null || kunci == null) return "";
        String cari = "\"" + kunci + "\"";
        int i = json.indexOf(cari);
        if (i < 0) return "";
        int titikDua = json.indexOf(':', i + cari.length());
        if (titikDua < 0) return "";
        int awal = titikDua + 1;
        while (awal < json.length()
                && (json.charAt(awal) == ' ' || json.charAt(awal) == '\t'
                || json.charAt(awal) == '\n' || json.charAt(awal) == '\r')) {
            awal++;
        }
        if (awal >= json.length() || json.charAt(awal) != '"') return "";
        StringBuilder sb = new StringBuilder();
        int j = awal + 1;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(j + 1);
                if (n == 'n') sb.append('\n');
                else if (n == 't') sb.append('\t');
                else if (n == 'r') sb.append('\r');
                else sb.append(n);
                j += 2;
                continue;
            }
            if (c == '"') return sb.toString();
            sb.append(c);
            j++;
        }
        return sb.toString();
    }

    /** Daftar nilai "name" dari array JSON objek (untuk daftar isi folder GitHub). */
    public static List<String> ambilDaftarNama(String json) {
        List<String> hasil = new ArrayList<String>();
        if (json == null) return hasil;
        int dari = 0;
        while (true) {
            String cari = "\"name\"";
            int i = json.indexOf(cari, dari);
            if (i < 0) break;
            int titikDua = json.indexOf(':', i + cari.length());
            if (titikDua < 0) break;
            int awal = titikDua + 1;
            while (awal < json.length() && (json.charAt(awal) == ' ' || json.charAt(awal) == '\t')) {
                awal++;
            }
            if (awal >= json.length() || json.charAt(awal) != '"') {
                dari = titikDua + 1;
                continue;
            }
            int akhir = json.indexOf('"', awal + 1);
            if (akhir < 0) break;
            hasil.add(json.substring(awal + 1, akhir));
            dari = akhir + 1;
        }
        return hasil;
    }

    /** String Java menjadi literal string JSON (dengan tanda petik). */
    public static String jsonKutip(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /** Isi berkas sebagai string Base64 (tanpa baris baru); null bila gagal. */
    public static String bacaBase64(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(f));
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
            } finally {
                masuk.close();
            }
            return Base64.getEncoder().encodeToString(keluar.toByteArray());
        } catch (Exception ignored) {
            return null;
        }
    }
}
