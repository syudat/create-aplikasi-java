package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keamanan Otomatis (Tahap 3G).
 *
 * Memeriksa keamanan proyek secara otomatis, lokal (tanpa internet).
 * Delapan pemeriksaan — berbeda dari Pemeriksa Kode (3E) yang memeriksa
 * pola kode umum; Keamanan fokus pada izin, data yang keluar, rahasia,
 * dan konfigurasi yang membahayakan:
 * (1) izin berbahaya yang dipakai (lokasi, SMS, panggilan, kamera,
 * mikrofon, kontak), (2) kirim data via HTTP, (3) kirim SMS,
 * (4) telepon keluar, (5) kata sandi/kunci hardcoded,
 * (6) WebView tanpa perlindungan, (7) komponen diekspor,
 * (8) lalu lintas teks biasa (http).
 *
 * Level: AMAN (tidak ada temuan), PERINGATAN (berisiko tapi bisa jadi
 * dibutuhkan — pengguna harus tahu), BAHAYA (harus diperbaiki).
 * Izin berbahaya dan pengiriman data hanya PERINGATAN karena proyek yang
 * sah bisa membutuhkannya; BAHAYA hanya untuk rahasia yang tertulis di
 * kode, WebView yang membuka akses berkas, komponen diekspor tanpa
 * perlindungan, dan lalu lintas teks biasa yang dipaksa aktif.
 *
 * Murni Java (java.io/java.util/regex, tanpa API Android) supaya bisa
 * diuji ulang di JRE.
 */
public class KeamananOtomatis {

    /** Satu temuan keamanan. */
    public static class Temuan {
        /** Berkas relatif terhadap akar proyek. */
        public final String berkas;
        /** Nomor baris (1-based; 0 bila tidak terkait satu baris). */
        public final int baris;
        /** Jenis pemeriksaan, salah satu dari NAMA_JENIS. */
        public final String jenis;
        /** "AMAN", "PERINGATAN", atau "BAHAYA". */
        public final String level;
        /** Penjelasan temuan. */
        public final String pesan;
        /** Saran perbaikan. */
        public final String saran;

        public Temuan(String berkas, int baris, String jenis, String level,
                      String pesan, String saran) {
            this.berkas = berkas;
            this.baris = baris;
            this.jenis = jenis;
            this.level = level;
            this.pesan = pesan;
            this.saran = saran;
        }
    }

    /** Jumlah jenis pemeriksaan. */
    public static final int JUMLAH_JENIS = 8;

    /** Nama kedelapan jenis pemeriksaan (untuk laporan). */
    public static final String[] NAMA_JENIS = {
        "Izin berbahaya",
        "Kirim data via HTTP",
        "Kirim SMS",
        "Telepon keluar",
        "Kata sandi/kunci hardcoded",
        "WebView tanpa perlindungan",
        "Komponen diekspor",
        "Lalu lintas teks biasa (http)",
    };

    /** Izin berbahaya beserta penjelasan risikonya. */
    private static final String[][] IZIN_BERBAHAYA = {
        {"ACCESS_FINE_LOCATION", "lokasi tepat pengguna"},
        {"ACCESS_COARSE_LOCATION", "lokasi kasar pengguna"},
        {"ACCESS_BACKGROUND_LOCATION", "lokasi pengguna di latar belakang"},
        {"SEND_SMS", "mengirim SMS (bisa berbayar)"},
        {"READ_SMS", "membaca SMS pengguna"},
        {"RECEIVE_SMS", "menerima SMS pengguna"},
        {"CALL_PHONE", "menelpon tanpa lewat aplikasi telepon"},
        {"READ_CALL_LOG", "membaca riwayat panggilan"},
        {"CAMERA", "membuka kamera"},
        {"RECORD_AUDIO", "merekam suara/mikrofon"},
        {"READ_CONTACTS", "membaca kontak pengguna"},
        {"WRITE_CONTACTS", "mengubah kontak pengguna"},
        {"READ_EXTERNAL_STORAGE", "membaca penyimpanan pengguna"},
        {"WRITE_EXTERNAL_STORAGE", "menulis penyimpanan pengguna"},
        {"READ_MEDIA_IMAGES", "membaca foto pengguna"},
        {"BODY_SENSORS", "membaca sensor tubuh pengguna"},
    };

    /** Pola pengiriman data via HTTP (kode). */
    private static final String[] POLA_HTTP = {
        "HttpURLConnection", "http.request", "HttpClient", "OkHttpClient",
        "URL(", "URLConnection", ".post(", ".enqueue(",
    };

    /** Pola kirim SMS (kode). */
    private static final String[] POLA_SMS = {
        "SmsManager", "sendTextMessage", "sendMultipartTextMessage",
        "ACTION_SENDTO", "smsto:",
    };

    /** Pola telepon keluar (kode). ACTION_DIAL tidak dihitung karena
     *  hanya membuka aplikasi telepon dan pengguna yang menekan panggil. */
    private static final String[] POLA_TELEPON = {
        "ACTION_CALL", "Intent(\"tel:", "Intent('tel:", "tel:",
    };

    /** Pola kata sandi/kunci yang tertulis langsung di kode. */
    private static final Pattern POLA_RAHASIA =
            Pattern.compile("(?i)(password|passwd|pwd|api[_-]?key|secret|token|access[_-]?key)"
                    + "\\s*=\\s*[\"'][^\"']{4,}[\"']");

    /** Pola rahasia di XML (name="api_key">nilai<). */
    private static final Pattern POLA_RAHASIA_XML =
            Pattern.compile("name=\"(password|api[_-]?key|secret|token)\"[^>]*>"
                    + "([^<]{4,})<");

    /**
     * Memeriksa proyek (lewati folder bin/ dan .git). Mengembalikan
     * daftar temuan (bisa kosong bila semua pemeriksaan AMAN).
     */
    public static List<Temuan> periksa(File dirProyek) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        List<File> berkas = new ArrayList<File>();
        kumpulBerkas(dirProyek, berkas);
        for (int i = 0; i < berkas.size(); i++) {
            periksaBerkas(dirProyek, berkas.get(i), hasil);
        }
        return hasil;
    }

    /** Level terburuk untuk satu jenis pemeriksaan ("AMAN" bila tidak ada temuan jenis itu). */
    public static String levelJenis(List<Temuan> temuan, String jenis) {
        String level = "AMAN";
        for (int i = 0; i < temuan.size(); i++) {
            Temuan t = temuan.get(i);
            if (!jenis.equals(t.jenis)) continue;
            if ("BAHAYA".equals(t.level)) return "BAHAYA";
            if ("PERINGATAN".equals(t.level)) level = "PERINGATAN";
        }
        return level;
    }

    /** Laporan lengkap: ringkasan per jenis + daftar temuan. */
    public static String laporan(File dirProyek, List<Temuan> temuan) {
        StringBuilder sb = new StringBuilder();
        int bahaya = 0;
        int peringatan = 0;
        for (int i = 0; i < temuan.size(); i++) {
            if ("BAHAYA".equals(temuan.get(i).level)) bahaya++;
            else if ("PERINGATAN".equals(temuan.get(i).level)) peringatan++;
        }
        sb.append("Hasil periksa keamanan proyek ").append(dirProyek.getName())
                .append(": ").append(bahaya).append(" BAHAYA, ")
                .append(peringatan).append(" PERINGATAN.\n");
        sb.append("Ringkasan per pemeriksaan:\n");
        for (int i = 0; i < NAMA_JENIS.length; i++) {
            sb.append("- ").append(NAMA_JENIS[i]).append(": ")
                    .append(levelJenis(temuan, NAMA_JENIS[i])).append("\n");
        }
        if (temuan.isEmpty()) {
            sb.append("Semua pemeriksaan AMAN — tidak ada temuan keamanan.\n");
            return sb.toString();
        }
        sb.append("Temuan:\n");
        for (int i = 0; i < temuan.size(); i++) {
            Temuan t = temuan.get(i);
            sb.append(t.berkas);
            if (t.baris > 0) sb.append(":").append(t.baris);
            sb.append(" [").append(t.level).append("] [").append(t.jenis)
                    .append("] ").append(t.pesan)
                    .append(" Saran: ").append(t.saran).append("\n");
        }
        return sb.toString();
    }

    private static void periksaBerkas(File akar, File f, List<Temuan> hasil) {
        String isi = bacaBerkas(f);
        if (isi == null) return;
        String rel = namaRelatif(akar, f);
        String nama = f.getName();
        boolean manifest = "AndroidManifest.xml".equals(nama);
        boolean initLua = "init.lua".equals(nama);
        boolean kode = nama.endsWith(".lua") || nama.endsWith(".java")
                || nama.endsWith(".kt") || nama.endsWith(".xml")
                || nama.endsWith(".html") || nama.endsWith(".js");
        if (!kode && !manifest && !initLua) return;
        String[] baris = isi.split("\n", -1);

        // (1) Izin berbahaya — dari manifest dan init.lua.
        if (manifest || initLua) {
            for (int i = 0; i < IZIN_BERBAHAYA.length; i++) {
                String izin = IZIN_BERBAHAYA[i][0];
                int barisKe = cariBaris(baris, izin);
                if (barisKe >= 0) {
                    hasil.add(new Temuan(rel, barisKe + 1, NAMA_JENIS[0], "PERINGATAN",
                            "Memakai izin " + izin + " (" + IZIN_BERBAHAYA[i][1] + ").",
                            "Pastikan izin itu memang dibutuhkan; jelaskan ke pengguna "
                                    + "kenapa aplikasi membutuhkannya sebelum meminta izin."));
                }
            }
        }

        // (7) Komponen diekspor — hanya manifest.
        if (manifest) {
            for (int i = 0; i < baris.length; i++) {
                String t = baris[i];
                if (t.contains("android:exported=\"true\"")) {
                    boolean launcher = false;
                    for (int j = i; j < Math.min(baris.length, i + 12); j++) {
                        if (baris[j].contains("android.intent.action.MAIN")) {
                            launcher = true;
                            break;
                        }
                    }
                    boolean adaIzin = t.contains("android:permission=");
                    if (launcher) continue;
                    hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[6],
                            adaIzin ? "PERINGATAN" : "BAHAYA",
                            "Komponen diekspor (android:exported=\"true\")"
                                    + (adaIzin ? " dengan izin." : " TANPA izin pelindung")
                                    + " — aplikasi lain bisa membukanya.",
                            adaIzin
                                    ? "Periksa lagi apakah komponen ini memang harus bisa dibuka aplikasi lain."
                                    : "Tambahkan android:permission, atau ubah jadi exported=\"false\" "
                                            + "bila tidak perlu dibuka aplikasi lain."));
                }
                // (8b) Lalu lintas teks biasa dipaksa aktif.
                if (t.contains("android:usesCleartextTraffic=\"true\"")) {
                    hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[7], "BAHAYA",
                            "usesCleartextTraffic=\"true\" — aplikasi diizinkan memakai http tanpa enkripsi.",
                            "Hapus atribut ini dan pakai https; data lewat http bisa dibaca orang lain."));
                }
            }
        }

        // (2)-(6) dan (8a) — per baris kode.
        boolean jsAktif = false;
        int barisJs = -1;
        for (int i = 0; i < baris.length; i++) {
            String t = baris[i];
            // (2) Kirim data via HTTP.
            for (int k = 0; k < POLA_HTTP.length; k++) {
                if (t.contains(POLA_HTTP[k])) {
                    hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[1], "PERINGATAN",
                            "Mengirim data via HTTP (\"" + POLA_HTTP[k] + "\").",
                            "Pastikan data yang dikirim memang perlu dan tujuannya tepercaya; "
                                    + "pakai https, bukan http."));
                    break;
                }
            }
            // (3) Kirim SMS.
            for (int k = 0; k < POLA_SMS.length; k++) {
                if (t.contains(POLA_SMS[k])) {
                    hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[2], "PERINGATAN",
                            "Mengirim SMS (\"" + POLA_SMS[k] + "\").",
                            "SMS bisa berbayar; pastikan pengguna tahu dan menyetujui sebelum SMS dikirim."));
                    break;
                }
            }
            // (4) Telepon keluar.
            for (int k = 0; k < POLA_TELEPON.length; k++) {
                if (t.contains(POLA_TELEPON[k])) {
                    hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[3], "PERINGATAN",
                            "Menelpon langsung (\"" + POLA_TELEPON[k] + "\").",
                            "Telepon langsung bisa berbayar; lebih aman pakai ACTION_DIAL "
                                    + "(pengguna yang menekan tombol panggil)."));
                    break;
                }
            }
            // (5) Kata sandi/kunci hardcoded.
            Matcher m = POLA_RAHASIA.matcher(t);
            if (m.find()) {
                hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[4], "BAHAYA",
                        "Kata sandi/kunci tertulis langsung di kode (\"" + m.group(1) + "\").",
                        "Jangan tulis kata sandi di kode — siapa pun yang membongkar APK bisa "
                                + "membacanya. Minta pengguna memasukkannya sendiri, atau simpan di server."));
            } else if (nama.endsWith(".xml")) {
                Matcher mx = POLA_RAHASIA_XML.matcher(t);
                if (mx.find()) {
                    hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[4], "BAHAYA",
                            "Kata sandi/kunci tertulis di XML (\"" + mx.group(1) + "\").",
                            "Jangan tulis kata sandi di berkas proyek — siapa pun yang membongkar "
                                    + "APK bisa membacanya."));
                }
            }
            // (6) WebView tanpa perlindungan.
            if (t.contains("setJavaScriptEnabled(true)")) {
                jsAktif = true;
                barisJs = i;
                hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[5], "PERINGATAN",
                        "WebView mengaktifkan JavaScript.",
                        "JavaScript di WebView bisa dipakai halaman jahat; aktifkan hanya "
                                + "bila memang dibutuhkan."));
            }
            if (t.contains("setAllowFileAccess(true)")
                    || t.contains("addJavascriptInterface")) {
                hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[5], "BAHAYA",
                        "WebView " + (t.contains("addJavascriptInterface")
                                ? "memasang addJavascriptInterface"
                                : "mengizinkan akses berkas")
                                + " — halaman web bisa mengakses kode/berkas aplikasi.",
                        "Matikan akses berkas (setAllowFileAccess(false)) dan hindari "
                                + "addJavascriptInterface kecuali benar-benar perlu dan terpercaya."));
            }
            // (8a) URL http:// di kode — lewati deklarasi xmlns Android
            // (http://schemas.android.com/...) yang ada di setiap manifest;
            // itu pengenal namespace, bukan alamat jaringan.
            if (t.contains("http://") && !t.contains("schemas.android.com")) {
                hasil.add(new Temuan(rel, i + 1, NAMA_JENIS[7], "PERINGATAN",
                        "Memakai alamat http:// (tanpa enkripsi).",
                        "Ganti ke https:// supaya data tidak bisa dibaca orang lain di jalan."));
            }
        }
        // Catatan gabungan: JS aktif + akses berkas sudah ditandai BAHAYA di
        // atas; bila hanya JS aktif, levelnya tetap PERINGATAN (sudah dicatat).
        if (jsAktif && barisJs >= 0) {
            // Tidak ada temuan tambahan — baris JS sudah dicatat sebagai PERINGATAN.
        }
    }

    /** Nomor baris pertama yang mengandung teks (-1 bila tidak ada). */
    private static int cariBaris(String[] baris, String teks) {
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].contains(teks)) return i;
        }
        return -1;
    }

    private static void kumpulBerkas(File dir, List<File> keluar) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if ("bin".equals(f.getName()) || ".git".equals(f.getName())) continue;
                kumpulBerkas(f, keluar);
            } else {
                String n = f.getName();
                if (n.endsWith(".lua") || n.endsWith(".java") || n.endsWith(".kt")
                        || n.endsWith(".xml") || n.endsWith(".html") || n.endsWith(".js")) {
                    keluar.add(f);
                }
            }
        }
    }

    private static String namaRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    private static String bacaBerkas(File f) {
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
