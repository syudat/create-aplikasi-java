package com.androlua;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pengemas APK lewat GitHub Actions.
 *
 * Alur: unggah file proyek ke repo (proyek/SLUG/), jalankan workflow bangun-proyek.yml,
 * tunggu selesai, lalu unduh APK ke PROYEK/bin/SLUG.apk.
 * Semua kerja jaringan berjalan di thread latar; Listener dipanggil di thread utama.
 */
public class Pengemas {

    public interface Listener {
        /** type: 0 = kemajuan, 1 = berhasil (message = path APK), 2 = gagal */
        void onEvent(int type, String message);
    }

    public static final int PROGRESS = 0, SUCCESS = 1, FAILED = 2;

    private static final String API = "https://api.github.com";
    private static final String WORKFLOW = "bangun-proyek.yml";

    private static final String NAMA_REPO = "pembuat-aplikasi-build";

    private final String token, slug;
    private String owner, repo, branch;
    private final File projectDir, fileWorkflow, fileMesin;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Enkripsi keamanan aplikasi aktif? (dari Pengaturan GitHub). */
    private final boolean keamanan;

    private Pengemas(String projectDir, String slug, String token, String fileWorkflow,
                      String fileMesin, Listener l, boolean keamanan) {
        this.projectDir = new File(projectDir);
        this.slug = slug;
        this.token = token;
        this.fileWorkflow = new File(fileWorkflow);
        this.fileMesin = new File(fileMesin);
        this.listener = l;
        this.keamanan = keamanan;
    }

    /**
     * Mulai membangun di thread latar. Hanya perlu token: repo "pembuat-aplikasi-build"
     * dibuat otomatis (privat) bila belum ada, lalu workflow dan mesin build diunggah.
     */
    public static void bangun(final String projectDir, final String slug, final String token,
                              final String fileWorkflow, final String fileMesin,
                              final Listener listener, final boolean keamanan) {
        final Pengemas p = new Pengemas(projectDir, slug, token, fileWorkflow, fileMesin, listener, keamanan);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    p.jalankan();
                } catch (java.net.UnknownHostException e) {
                    p.kirim(FAILED, TeksUI.t("Tidak bisa menjangkau GitHub (") + e.getMessage() + ").\n\n"
                            + TeksUI.t("Periksa koneksi internet: pastikan data seluler/Wi-Fi aktif dan benar-benar punya akses internet, ")
                            + TeksUI.t("matikan VPN atau DNS pribadi yang bermasalah, lalu coba lagi."));
                } catch (java.net.SocketTimeoutException e) {
                    p.kirim(FAILED, TeksUI.t("Koneksi ke GitHub terlalu lambat (waktu habis). Coba lagi saat sinyal lebih baik."));
                } catch (Throwable e) {
                    p.kirim(FAILED, TeksUI.t("Gagal: ") + e.getMessage());
                }
            }
        }, "Pengemas").start();
    }

    private void kirim(final int type, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onEvent(type, msg);
            }
        });
    }

    // ------------------------------------------------------------------ alur utama

    private void jalankan() throws Exception {
        if (token == null || token.length() < 10) throw new IOException(TeksUI.t("Token GitHub belum diisi"));
        if (!fileWorkflow.isFile() || !fileMesin.isFile())
            throw new IOException(TeksUI.t("Berkas mesin build tidak ditemukan di aplikasi (bangun-proyek.yml / engine.zip). ")
                    + TeksUI.t("Pasang ulang aplikasi versi terbaru."));

        // 1) siapkan repo
        kirim(PROGRESS, TeksUI.t("Memeriksa akun GitHub..."));
        Resp r = request("GET", "/user", null);
        if (r.code == 401) throw gagal(TeksUI.t("Token tidak valid"), r);
        if (r.code != 200) throw gagal(TeksUI.t("Membaca akun"), r);
        owner = new JSONObject(r.body).optString("login");
        repo = NAMA_REPO;
        String base = "/repos/" + owner + "/" + repo;

        r = request("GET", base, null);
        if (r.code == 404) {
            kirim(PROGRESS, TeksUI.t("Membuat repo privat ") + owner + "/" + repo + "...");
            JSONObject b = new JSONObject();
            b.put("name", repo);
            b.put("private", true);
            b.put("auto_init", true);
            b.put("description", "Mesin build Pembuat Aplikasi (dibuat otomatis)");
            Resp c = request("POST", "/user/repos", b.toString());
            if (c.code != 201) throw gagal(TeksUI.t("Membuat repo"), c);
            branch = new JSONObject(c.body).optString("default_branch", "main");
            Thread.sleep(3000);
        } else if (r.code == 200) {
            branch = new JSONObject(r.body).optString("default_branch", "main");
        } else {
            throw gagal("Memeriksa repo", r);
        }

        kirim(PROGRESS, "Membaca isi repo...");
        Map<String, String> remote = remoteTree(base);

        // 2) mesin build (sekali saja; diunggah ulang hanya jika berubah)
        String prefix = "proyek/" + slug + "/";
        kirim(PROGRESS, TeksUI.t("Memeriksa workflow dan mesin build..."));
        if (unggah(base, remote, ".github/workflows/bangun-proyek.yml", baca(fileWorkflow),
                TeksUI.t("Workflow bangun-proyek.yml"))) {
            // workflow baru: beri waktu GitHub mendaftarkannya
            Thread.sleep(3000);
        }
        unggah(base, remote, "mesin/engine.zip", baca(fileMesin),
                TeksUI.t("Mesin build (sekali saja, beberapa MB)"));

        // 3) kumpulkan dan unggah file proyek yang berubah (hemat internet)
        List<File> files = new ArrayList<File>();
        kumpulkan(projectDir, files, true);
        if (files.isEmpty()) throw new IOException(TeksUI.t("Tidak ada file proyek untuk diunggah"));
        int n = 0, diunggah = 0;
        Map<String, Boolean> lokal = new HashMap<String, Boolean>();
        for (File f : files) {
            n++;
            String rel = relatif(f);
            String path = prefix + rel;
            lokal.put(path, true);
            if (unggah(base, remote, path, baca(f), rel + " (" + n + "/" + files.size() + ")")) diunggah++;
        }

        // 4) hapus file yang sudah tidak ada di proyek
        for (Map.Entry<String, String> e : remote.entrySet()) {
            String path = e.getKey();
            if (path.startsWith(prefix) && !lokal.containsKey(path)) {
                kirim(PROGRESS, TeksUI.t("Menghapus ") + path.substring(prefix.length()));
                JSONObject body = new JSONObject();
                body.put("message", "Pembuat Aplikasi: hapus " + path);
                body.put("sha", e.getValue());
                body.put("branch", branch);
                request("DELETE", base + "/contents/" + enc(path), body.toString());
            }
        }
        kirim(PROGRESS, TeksUI.t("Unggah selesai (") + diunggah + TeksUI.t(" file proyek berubah)."));

        // 5) jalankan workflow
        long idTerakhir = idRunTerakhir(base);
        kirim(PROGRESS, TeksUI.t("Memulai build di GitHub Actions..."));
        JSONObject inputs = new JSONObject();
        inputs.put("slug", slug);
        // Reorganisasi pengaturan 2026-11-17: teruskan pilihan Keamanan
        // (enkripsi keamanan aplikasi) dari Pengaturan GitHub ke workflow.
        inputs.put("keamanan", keamanan ? "ya" : "tidak");
        JSONObject body = new JSONObject();
        body.put("ref", branch);
        body.put("inputs", inputs);
        r = request("POST", base + "/actions/workflows/" + WORKFLOW + "/dispatches", body.toString());
        for (int coba = 0; r.code == 404 && coba < 5; coba++) { // workflow baru belum terdaftar
            Thread.sleep(4000);
            r = request("POST", base + "/actions/workflows/" + WORKFLOW + "/dispatches", body.toString());
        }
        if (r.code != 204) throw gagal("Menjalankan workflow", r);

        // 6) tunggu
        long runId = 0;
        String statusTerakhir = "";
        long mulai = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - mulai > 30L * 60 * 1000)
                throw new IOException(TeksUI.t("Build melebihi 30 menit, dihentikan menunggu"));
            Thread.sleep(5000);
            r = request("GET", base + "/actions/workflows/" + WORKFLOW
                    + "/runs?event=workflow_dispatch&per_page=5", null);
            if (r.code != 200) continue;
            JSONArray runs = new JSONObject(r.body).optJSONArray("workflow_runs");
            JSONObject run = null;
            if (runs != null) {
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject o = runs.getJSONObject(i);
                    if (o.optLong("id") > idTerakhir && (run == null || o.optLong("id") > run.optLong("id")))
                        run = o;
                }
            }
            if (run == null) {
                if (!statusTerakhir.equals("antre")) kirim(PROGRESS, "Menunggu GitHub memulai build...");
                statusTerakhir = "antre";
                continue;
            }
            runId = run.optLong("id");
            String status = run.optString("status");
            if (!status.equals(statusTerakhir)) {
                statusTerakhir = status;
                kirim(PROGRESS, "completed".equals(status) ? TeksUI.t("Build selesai, memeriksa hasil...")
                        : "queued".equals(status) ? TeksUI.t("Build antre di GitHub...") : TeksUI.t("Build sedang berjalan (bisa beberapa menit)..."));
            }
            if ("completed".equals(status)) {
                if ("success".equals(run.optString("conclusion"))) break;
                gagalBuild(base, runId, run.optString("conclusion"));
                return;
            }
        }

        // 7) unduh APK
        kirim(PROGRESS, "Mengunduh APK...");
        r = request("GET", base + "/actions/runs/" + runId + "/artifacts", null);
        if (r.code != 200) throw gagal(TeksUI.t("Mengambil daftar artifact"), r);
        JSONArray arts = new JSONObject(r.body).optJSONArray("artifacts");
        long artId = 0;
        if (arts != null) {
            for (int i = 0; i < arts.length(); i++) {
                JSONObject a = arts.getJSONObject(i);
                if (!a.optBoolean("expired")) { artId = a.optLong("id"); break; }
            }
        }
        if (artId == 0) throw new IOException(TeksUI.t("Build sukses tetapi APK tidak ditemukan"));
        File bin = new File(projectDir, "bin");
        bin.mkdirs();
        File zip = new File(bin, ".unduhan.zip");
        unduh(API + base + "/actions/artifacts/" + artId + "/zip", zip);
        File apk = new File(bin, slug + "-belum-ditandatangani.apk");
        boolean ada = ekstrakApk(zip, apk);
        zip.delete();
        if (!ada) throw new IOException(TeksUI.t("Arsip hasil build tidak berisi APK"));
        kirim(SUCCESS, apk.getAbsolutePath());
    }

    /** Unggah satu berkas bila isinya beda dengan di repo. Mengembalikan true jika diunggah. */
    private boolean unggah(String base, Map<String, String> remote, String path, byte[] data,
                           String label) throws Exception {
        String sha = remote.get(path);
        if (sha != null && sha.equals(gitSha(data))) return false;
        kirim(PROGRESS, "Mengunggah " + label);
        JSONObject body = new JSONObject();
        body.put("message", "Pembuat Aplikasi: " + path);
        body.put("content", Base64.encodeToString(data, Base64.NO_WRAP));
        body.put("branch", branch);
        if (sha != null) body.put("sha", sha);
        Resp r = request("PUT", base + "/contents/" + enc(path), body.toString());
        if (r.code == 404 && path.startsWith(".github/"))
            throw new IOException(TeksUI.t("Token tidak boleh menulis workflow. Buat token klasik dengan izin 'repo' dan 'workflow'."));
        if (r.code != 200 && r.code != 201) throw gagal(TeksUI.t("Unggah ") + path, r);
        return true;
    }

    private void gagalBuild(String base, long runId, String kesimpulan) throws Exception {
        kirim(PROGRESS, TeksUI.t("Build gagal, mengambil log..."));
        StringBuilder ringkas = new StringBuilder();
        try {
            File bin = new File(projectDir, "bin");
            bin.mkdirs();
            File zip = new File(bin, ".log.zip");
            unduh(API + base + "/actions/runs/" + runId + "/logs", zip);
            File logFile = new File(bin, "log-build.txt");
            ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
            OutputStream out = new FileOutputStream(logFile);
            ZipEntry ze;
            int dihitung = 0;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory() || !ze.getName().endsWith(".txt")) continue;
                String teks = new String(bacaStream(zin), "UTF-8");
                for (String baris : teks.split("\n")) {
                    String b = baris.replaceFirst("^\\S+Z\\s", "");
                    String low = b.toLowerCase();
                    if (low.contains("error") || low.contains("failed") || low.contains("not found")
                            || low.contains("exception")) {
                        out.write((b + "\n").getBytes("UTF-8"));
                        if (dihitung < 12) {
                            ringkas.append(b.length() > 200 ? b.substring(0, 200) : b).append("\n");
                            dihitung++;
                        }
                    }
                }
            }
            out.close();
            zin.close();
            zip.delete();
            ringkas.append("\nLog lengkap: ").append(logFile.getAbsolutePath());
        } catch (Exception e) {
            ringkas.append(TeksUI.t("(log tidak dapat diambil: ")).append(e.getMessage()).append(")");
        }
        kirim(FAILED, TeksUI.t("Build gagal (") + kesimpulan + ").\n\n" + ringkas);
    }

    // ------------------------------------------------------------------ GitHub API

    private Map<String, String> remoteTree(String base) throws Exception {
        Map<String, String> m = new HashMap<String, String>();
        Resp r = request("GET", base + "/git/trees/" + enc(branch) + "?recursive=1", null);
        for (int coba = 0; (r.code == 404 || r.code == 409) && coba < 5; coba++) { // repo baru
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
            r = request("GET", base + "/git/trees/" + enc(branch) + "?recursive=1", null);
        }
        if (r.code == 401 || r.code == 403) throw gagal("Token ditolak GitHub", r);
        if (r.code == 404) throw gagal(TeksUI.t("Repo atau cabang tidak ditemukan"), r);
        if (r.code != 200) throw gagal("Membaca isi repo", r);
        JSONArray tree = new JSONObject(r.body).optJSONArray("tree");
        if (tree != null) {
            for (int i = 0; i < tree.length(); i++) {
                JSONObject o = tree.getJSONObject(i);
                if ("blob".equals(o.optString("type"))) m.put(o.optString("path"), o.optString("sha"));
            }
        }
        return m;
    }

    private long idRunTerakhir(String base) {
        try {
            Resp r = request("GET", base + "/actions/workflows/" + WORKFLOW + "/runs?per_page=1", null);
            if (r.code == 200) {
                JSONArray a = new JSONObject(r.body).optJSONArray("workflow_runs");
                if (a != null && a.length() > 0) return a.getJSONObject(0).optLong("id");
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static class Resp {
        int code;
        String body;
    }

    private Resp request(String method, String path, String body) throws IOException {
        URL url = new URL(path.startsWith("http") ? path : API + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(180000);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "PembuatAplikasi");
        if (body != null) {
            byte[] b = body.getBytes("UTF-8");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(b.length);
            OutputStream o = c.getOutputStream();
            o.write(b);
            o.close();
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        r.body = in == null ? "" : new String(bacaStream(in), "UTF-8");
        c.disconnect();
        return r;
    }

    /** Unduh file; pengalihan (redirect) diikuti manual agar token tidak ikut terkirim ke host lain. */
    private void unduh(String alamat, File tujuan) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(alamat).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "PembuatAplikasi");
        int code = c.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String lokasi = c.getHeaderField("Location");
            c.disconnect();
            if (lokasi == null) throw new IOException("Pengalihan tanpa lokasi");
            c = (HttpURLConnection) new URL(lokasi).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setRequestProperty("User-Agent", "PembuatAplikasi");
            code = c.getResponseCode();
        }
        if (code != 200) throw new IOException(TeksUI.t("Unduhan gagal (HTTP ") + code + ")");
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(tujuan);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        c.disconnect();
    }

    private IOException gagal(String langkah, Resp r) {
        String pesan = r.body;
        try {
            pesan = new JSONObject(r.body).optString("message", r.body);
        } catch (Exception ignored) {
        }
        return new IOException(langkah + " (HTTP " + r.code + "): " + pesan);
    }

    // ------------------------------------------------------------------ berkas

    private void kumpulkan(File dir, List<File> out, boolean atas) {
        File[] ls = dir.listFiles();
        if (ls == null) return;
        for (File f : ls) {
            String nama = f.getName();
            if (nama.startsWith(".")) continue;
            if (atas && nama.equals("bin")) continue;
            if (f.isDirectory()) kumpulkan(f, out, false);
            else out.add(f);
        }
    }

    private String relatif(File f) {
        String p = f.getAbsolutePath().substring(projectDir.getAbsolutePath().length());
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private static String enc(String path) throws IOException {
        String[] bagian = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bagian.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(bagian[i], "UTF-8").replace("+", "%20"));
        }
        return sb.toString();
    }

    private static byte[] baca(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            return bacaStream(in);
        } finally {
            in.close();
        }
    }

    private static byte[] bacaStream(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    /** SHA-1 gaya Git ("blob <ukuran>\0<isi>") agar bisa dibandingkan dengan sha di GitHub. */
    private static String gitSha(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(("blob " + data.length + "\0").getBytes("UTF-8"));
        md.update(data);
        byte[] h = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static boolean ekstrakApk(File zip, File apk) throws IOException {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().endsWith(".apk")) {
                    OutputStream out = new FileOutputStream(apk);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                    out.close();
                    return true;
                }
            }
        } finally {
            zin.close();
        }
        return false;
    }
}
