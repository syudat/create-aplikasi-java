package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auditor Keamanan Statis (SAST) dan Penambal Celah Otomatis (Fitur 7, daftar Gemini).
 *
 * Kelas murni tanpa Android, bisa diuji di JVM. Memindai seluruh berkas
 * proyek (kode, manifes, konfigurasi) terhadap aturan kerentanan Android
 * dengan pencocokan pola, lalu menyajikan temuan urut tingkat risiko
 * (TINGGI, SEDANG, RENDAH) lengkap dengan berkas, baris, penjelasan
 * dampak, dan teks perbaikan aman.
 *
 * Aturan yang diperiksa:
 * - KUNCI_API: kunci API tertulis terbuka (api_key="...", sk-...,
 *   AKIA..., AIza...).
 * - KOMPONEN_TEREKSPOS: activity/service/receiver/provider dengan
 *   android:exported="true" tanpa android:permission di manifes
 *   (kecuali activity launcher).
 * - WEBVIEW: setJavaScriptEnabled(true) bersama addJavascriptInterface
 *   dalam berkas yang sama (TINGGI); JavaScript aktif saja (SEDANG).
 * - KRIPTO_LEMAH: MessageDigest MD5/SHA1, Cipher DES/ECB (TINGGI);
 *   new Random() untuk keperluan keamanan (SEDANG).
 * - HTTP_TANPA_TLS: alamat http:// bukan lokal (SEDANG).
 * - KATA_SANDI: kata sandi tertulis di kode (password="...") (TINGGI).
 *
 * Berbeda dari Keamanan Otomatis (Tahap 3G) yang memeriksa izin dan
 * data keluar secara umum: auditor ini memeriksa pola kode sumber
 * baris demi baris dan bisa menerapkan perbaikan aman otomatis pada
 * baris yang lokasinya persis. Perbaikan otomatis HANYA untuk
 * KRIPTO_LEMAH, HTTP_TANPA_TLS, dan KOMPONEN_TEREKSPOS — kunci API dan
 * kata sandi tidak pernah ditulis ulang otomatis karena rahasianya
 * harus dipindahkan oleh pengembang sendiri.
 */
public class AuditKeamanan {

    public static final String TINGGI = "TINGGI";
    public static final String SEDANG = "SEDANG";
    public static final String RENDAH = "RENDAH";

    public static final String AT_KUNCI_API = "KUNCI_API";
    public static final String AT_KOMPONEN_TEREKSPOS = "KOMPONEN_TEREKSPOS";
    public static final String AT_WEBVIEW = "WEBVIEW";
    public static final String AT_KRIPTO_LEMAH = "KRIPTO_LEMAH";
    public static final String AT_HTTP_TANPA_TLS = "HTTP_TANPA_TLS";
    public static final String AT_KATA_SANDI = "KATA_SANDI";

    /** Satu temuan audit: tingkat, lokasi, aturan, penjelasan, dan perbaikan. */
    public static class Temuan {
        public final String tingkat;
        public final String berkas;
        public final int baris;
        public final String aturan;
        public final String penjelasan;
        public final String perbaikan;

        Temuan(String tingkat, String berkas, int baris, String aturan,
               String penjelasan, String perbaikan) {
            this.tingkat = tingkat;
            this.berkas = berkas;
            this.baris = baris;
            this.aturan = aturan;
            this.penjelasan = penjelasan;
            this.perbaikan = perbaikan;
        }
    }

    private static final Pattern POLA_KUNCI_API_KEY =
            Pattern.compile("(?i)api[_-]?key\\s*[:=]\\s*\"[^\"]{8,}\"");
    private static final Pattern POLA_KUNCI_SK =
            Pattern.compile("sk-[A-Za-z0-9]{16,}");
    private static final Pattern POLA_KUNCI_AKIA =
            Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern POLA_KUNCI_AIZA =
            Pattern.compile("AIza[0-9A-Za-z_\\-]{10,}");
    private static final Pattern POLA_KATA_SANDI =
            Pattern.compile("(?i)(?<![A-Za-z0-9_:])password\\s*=\\s*\"[^\"]+\"");
    private static final Pattern POLA_MD5_SHA1 =
            Pattern.compile("MessageDigest\\s*\\.\\s*getInstance\\s*\\(\\s*\"(MD5|SHA-?1)\"\\s*\\)");
    private static final Pattern POLA_CIPHER_LEMAH =
            Pattern.compile("Cipher\\s*\\.\\s*getInstance\\s*\\(\\s*\"[^\"]*(DES|ECB)[^\"]*\"\\s*\\)");
    private static final Pattern POLA_RANDOM =
            Pattern.compile("new\\s+(?:[A-Za-z0-9_]+\\.)*Random\\s*\\(");
    private static final Pattern POLA_HTTP =
            Pattern.compile("http://[^\\s\"'<>]+");
    private static final Pattern POLA_JS_AKTIF =
            Pattern.compile("setJavaScriptEnabled\\s*\\(\\s*true\\s*\\)");
    private static final Pattern POLA_JS_INTERFACE =
            Pattern.compile("addJavascriptInterface\\s*\\(");
    private static final Pattern POLA_ELEMEN_MANIFES =
            Pattern.compile("<(activity|service|receiver|provider)\\b[^>]*>");

    private static final String[] EKSTENSI_DIPINDAI = {
            "java", "kt", "xml", "js", "gradle", "properties", "json"
    };

    /**
     * Memindai seluruh berkas proyek. Mengembalikan daftar temuan urut
     * risiko (TINGGI dulu), lalu nama berkas, lalu nomor baris. Direktori
     * null, hilang, atau bukan direktori menghasilkan daftar kosong.
     */
    public static List<Temuan> pindai(File dirProyek) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        List<File> berkas = new ArrayList<File>();
        kumpulkan(dirProyek, berkas);
        for (int i = 0; i < berkas.size(); i++) {
            File f = berkas.get(i);
            String relatif = jalurRelatif(dirProyek, f);
            String isi = bacaTeks(f);
            if (isi == null) continue;
            if ("AndroidManifest.xml".equals(f.getName())) {
                pindaiManifes(isi, relatif, hasil);
            }
            pindaiBerkas(isi, relatif, hasil);
        }
        Collections.sort(hasil, new Comparator<Temuan>() {
            @Override
            public int compare(Temuan a, Temuan b) {
                int t = tingkatUrutan(a.tingkat) - tingkatUrutan(b.tingkat);
                if (t != 0) return t;
                int f = a.berkas.compareTo(b.berkas);
                if (f != 0) return f;
                return a.baris - b.baris;
            }
        });
        return hasil;
    }

    /** Urutan risiko untuk pengurutan: TINGGI 0, SEDANG 1, RENDAH 2. */
    public static int tingkatUrutan(String tingkat) {
        if (TINGGI.equals(tingkat)) return 0;
        if (SEDANG.equals(tingkat)) return 1;
        return 2;
    }

    /** Mengumpulkan berkas terpindai secara rekursif (lewati .git, build, .gradle). */
    private static void kumpulkan(File dir, List<File> keluar) {
        File[] isi = dir.listFiles();
        if (isi == null) return;
        for (int i = 0; i < isi.length; i++) {
            File f = isi[i];
            if (f.isDirectory()) {
                String n = f.getName();
                if (".git".equals(n) || "build".equals(n) || ".gradle".equals(n)) continue;
                kumpulkan(f, keluar);
            } else if (f.isFile() && f.length() <= 2 * 1024 * 1024
                    && ekstensiDipindai(f.getName())) {
                keluar.add(f);
            }
        }
    }

    private static boolean ekstensiDipindai(String nama) {
        int titik = nama.lastIndexOf('.');
        if (titik < 0) return false;
        String e = nama.substring(titik + 1).toLowerCase();
        for (int i = 0; i < EKSTENSI_DIPINDAI.length; i++) {
            if (EKSTENSI_DIPINDAI[i].equals(e)) return true;
        }
        return false;
    }

    /** Aturan manifes: komponen terekspos tanpa permission (bukan launcher). */
    private static void pindaiManifes(String isi, String relatif, List<Temuan> keluar) {
        Matcher m = POLA_ELEMEN_MANIFES.matcher(isi);
        while (m.find()) {
            String elemen = m.group(0);
            if (!elemen.contains("android:exported=\"true\"")) continue;
            if (elemen.contains("android:permission")) continue;
            String jenis = m.group(1);
            int akhir = isi.indexOf("</" + jenis + ">", m.end());
            int batas = akhir >= 0 ? akhir : Math.min(isi.length(), m.end() + 2000);
            String badan = isi.substring(m.end(), batas);
            if (badan.contains("LAUNCHER")) continue;
            int baris = nomorBaris(isi, m.start());
            String penjelasan = TeksUI.t("Komponen ")
                    + jenis + TeksUI.t(" diekspor (android:exported=\"true\") tanpa izin, ")
                    + TeksUI.t("sehingga aplikasi lain bisa memanggilnya. ")
                    + TeksUI.t("Bila komponen ini tidak harus dipanggil aplikasi lain, ")
                    + TeksUI.t("tutup aksesnya.");
            keluar.add(new Temuan(TINGGI, relatif, baris, AT_KOMPONEN_TEREKSPOS,
                    penjelasan, perbaikan(AT_KOMPONEN_TEREKSPOS, elemen)));
        }
    }

    /** Aturan per baris untuk semua berkas terpindai. */
    private static void pindaiBerkas(String isi, String relatif, List<Temuan> keluar) {
        String[] baris = isi.split("\n", -1);
        int barisJsAktif = -1;
        int barisJsInterface = -1;
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            int no = i + 1;
            if (POLA_JS_AKTIF.matcher(b).find() && barisJsAktif < 0) barisJsAktif = no;
            if (POLA_JS_INTERFACE.matcher(b).find() && barisJsInterface < 0) barisJsInterface = no;
            if (POLA_KUNCI_API_KEY.matcher(b).find()
                    || POLA_KUNCI_SK.matcher(b).find()
                    || POLA_KUNCI_AKIA.matcher(b).find()
                    || POLA_KUNCI_AIZA.matcher(b).find()) {
                keluar.add(new Temuan(TINGGI, relatif, no, AT_KUNCI_API,
                        TeksUI.t("Kunci API tertulis terbuka di kode. ")
                        + TeksUI.t("Siapa pun yang membaca aplikasi ini bisa memakai kunci itu, ")
                        + TeksUI.t("misalnya untuk memakai kuota layanan atas nama kamu."),
                        perbaikan(AT_KUNCI_API, b)));
                continue;
            }
            if (POLA_KATA_SANDI.matcher(b).find()) {
                keluar.add(new Temuan(TINGGI, relatif, no, AT_KATA_SANDI,
                        TeksUI.t("Kata sandi tertulis langsung di kode. ")
                        + TeksUI.t("Siapa pun yang membaca aplikasi ini bisa melihat kata sandi itu."),
                        perbaikan(AT_KATA_SANDI, b)));
                continue;
            }
            Matcher md = POLA_MD5_SHA1.matcher(b);
            if (md.find()) {
                String algo = md.group(1);
                keluar.add(new Temuan(TINGGI, relatif, no, AT_KRIPTO_LEMAH,
                        TeksUI.t("Hash ") + algo
                        + TeksUI.t(" sudah lemah dan mudah dipalsukan. ")
                        + TeksUI.t("Jangan dipakai untuk kata sandi atau tanda tangan."),
                        perbaikan(AT_KRIPTO_LEMAH, b)));
                continue;
            }
            if (POLA_CIPHER_LEMAH.matcher(b).find()) {
                keluar.add(new Temuan(TINGGI, relatif, no, AT_KRIPTO_LEMAH,
                        TeksUI.t("Enkripsi DES atau mode ECB sudah lemah. ")
                        + TeksUI.t("DES mudah dipecahkan, dan ECB membocorkan pola data yang sama."),
                        perbaikan(AT_KRIPTO_LEMAH, b)));
                continue;
            }
            if (POLA_RANDOM.matcher(b).find()) {
                keluar.add(new Temuan(SEDANG, relatif, no, AT_KRIPTO_LEMAH,
                        TeksUI.t("Random biasa mudah ditebak urutannya. ")
                        + TeksUI.t("Jangan dipakai untuk token, kunci, atau kata sandi sekali pakai."),
                        perbaikan(AT_KRIPTO_LEMAH, b)));
                continue;
            }
            Matcher hm = POLA_HTTP.matcher(b);
            if (hm.find() && httpPerluDiperiksa(hm.group(0))) {
                keluar.add(new Temuan(SEDANG, relatif, no, AT_HTTP_TANPA_TLS,
                        TeksUI.t("Alamat memakai http:// tanpa enkripsi TLS. ")
                        + TeksUI.t("Data yang dikirim lewat alamat ini bisa dibaca atau diubah di jalan."),
                        perbaikan(AT_HTTP_TANPA_TLS, b)));
            }
        }
        if (barisJsAktif > 0 && barisJsInterface > 0) {
            keluar.add(new Temuan(TINGGI, relatif, barisJsInterface, AT_WEBVIEW,
                    TeksUI.t("WebView mengaktifkan JavaScript dan membuka antarmuka Java ke halaman web. ")
                    + TeksUI.t("Halaman berbahaya bisa memanggil kode Java di aplikasi ini."),
                    perbaikan(AT_WEBVIEW, "")));
        } else if (barisJsAktif > 0) {
            keluar.add(new Temuan(SEDANG, relatif, barisJsAktif, AT_WEBVIEW,
                    TeksUI.t("WebView mengaktifkan JavaScript. ")
                    + TeksUI.t("Pastikan halaman yang dimuat tepercaya, dan matikan JavaScript bila tidak dipakai."),
                    perbaikan(AT_WEBVIEW, "")));
        }
    }

    /**
     * Alamat http:// yang TIDAK perlu ditandai: alamat lokal (localhost,
     * 127.0.0.1, jaringan privat) dan namespace XML (schemas.android.com,
     * w3.org) yang memang bukan alamat jaringan.
     */
    static boolean httpPerluDiperiksa(String url) {
        String u = url.toLowerCase();
        if (u.contains("localhost") || u.contains("127.0.0.1")) return false;
        if (u.contains("://10.") || u.contains("://192.168.")) return false;
        if (u.matches("http://172\\.(1[6-9]|2[0-9]|3[01])\\..*")) return false;
        if (u.contains("schemas.android.com") || u.contains("w3.org")) return false;
        return true;
    }

    /**
     * Teks perbaikan aman untuk sebuah aturan. Baris sumber dipakai untuk
     * memilih pengganti yang tepat (mis. MD5 -> SHA-256, DES -> AES/GCM).
     */
    public static String perbaikan(String aturan, String baris) {
        String b = baris == null ? "" : baris;
        if (AT_KUNCI_API.equals(aturan)) {
            return TeksUI.t("Pindahkan kunci API dari kode ke BuildConfig atau penyimpanan aman di HP. ")
                    + TeksUI.t("Jangan menulis kunci langsung di kode.");
        }
        if (AT_KOMPONEN_TEREKSPOS.equals(aturan)) {
            return TeksUI.t("Ubah android:exported=\"true\" menjadi android:exported=\"false\", ")
                    + TeksUI.t("atau tambahkan android:permission bila komponen memang harus dipanggil aplikasi lain.");
        }
        if (AT_WEBVIEW.equals(aturan)) {
            return TeksUI.t("Hapus addJavascriptInterface bila tidak perlu, ")
                    + TeksUI.t("atau pastikan antarmuka memakai @JavascriptInterface dan hanya membuka metode yang aman. ")
                    + TeksUI.t("Matikan JavaScript bila tidak dipakai.");
        }
        if (AT_KRIPTO_LEMAH.equals(aturan)) {
            if (b.contains("MD5") || b.contains("SHA1") || b.contains("SHA-1")) {
                return TeksUI.t("Ganti MessageDigest MD5 atau SHA-1 dengan SHA-256.");
            }
            if (b.contains("DES") || b.contains("ECB")) {
                return TeksUI.t("Ganti DES atau mode ECB dengan AES/GCM/NoPadding.");
            }
            if (b.contains("Random")) {
                return TeksUI.t("Ganti new Random() dengan new SecureRandom() untuk token atau kunci.");
            }
            return TeksUI.t("Ganti dengan kriptografi yang kuat: SHA-256, AES/GCM, dan SecureRandom.");
        }
        if (AT_HTTP_TANPA_TLS.equals(aturan)) {
            return TeksUI.t("Ganti http:// dengan https:// pada alamat itu.");
        }
        if (AT_KATA_SANDI.equals(aturan)) {
            return TeksUI.t("Jangan menulis kata sandi di kode. ")
                    + TeksUI.t("Minta kata sandi dari pengguna, atau simpan hash-nya saja.");
        }
        return TeksUI.t("Periksa baris ini dan ganti dengan cara yang aman.");
    }

    /** Aturan yang boleh diterapkan otomatis (penggantiannya persis dan aman). */
    public static boolean bisaDiterapkanOtomatis(String aturan) {
        return AT_KRIPTO_LEMAH.equals(aturan)
                || AT_HTTP_TANPA_TLS.equals(aturan)
                || AT_KOMPONEN_TEREKSPOS.equals(aturan);
    }

    /**
     * Apakah temuan ini benar-benar bisa diterapkan otomatis saat ini:
     * aturannya boleh DAN baris yang tercatat masih berisi pola yang
     * bisa diganti persis. Random tidak bisa (butuh import SecureRandom),
     * jadi tombol "Terapkan Perbaikan" disembunyikan untuknya.
     */
    public static boolean bisaDiterapkan(File dirProyek, Temuan t) {
        if (dirProyek == null || t == null) return false;
        if (!bisaDiterapkanOtomatis(t.aturan)) return false;
        File f = new File(dirProyek, t.berkas);
        String isi = bacaTeks(f);
        if (isi == null) return false;
        String[] baris = isi.split("\n", -1);
        if (t.baris < 1 || t.baris > baris.length) return false;
        if (AT_KOMPONEN_TEREKSPOS.equals(t.aturan)) {
            for (int i = t.baris - 1; i < baris.length && i < t.baris + 20; i++) {
                if (baris[i].contains("android:exported=\"true\"")) return true;
                if (baris[i].contains("</activity") || baris[i].contains("</service")
                        || baris[i].contains("</receiver") || baris[i].contains("</provider")) {
                    return false;
                }
            }
            return false;
        }
        if (AT_KRIPTO_LEMAH.equals(t.aturan)) {
            String b = baris[t.baris - 1];
            if (b.contains("\"MD5\"") || b.contains("\"SHA1\"") || b.contains("\"SHA-1\"")) {
                return true;
            }
            return b.contains("Cipher") && b.contains("getInstance")
                    && (b.contains("DES") || b.contains("ECB"));
        }
        if (AT_HTTP_TANPA_TLS.equals(t.aturan)) {
            Matcher hm = POLA_HTTP.matcher(baris[t.baris - 1]);
            while (hm.find()) {
                if (httpPerluDiperiksa(hm.group(0))) return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Menerapkan perbaikan aman pada berkas temuan, HANYA bila baris yang
     * tercatat masih berisi pola yang sama persis. Mengembalikan true bila
     * berkas berhasil ditulis ulang. Kunci API, kata sandi, WebView, dan
     * Random tidak pernah ditulis otomatis (false) — tampilkan teks
     * perbaikannya saja, karena penggantinya butuh keputusan pengembang
     * (Random butuh import SecureRandom; rahasia harus dipindah manual).
     */
    public static boolean terapkanPerbaikan(File dirProyek, Temuan t) {
        if (dirProyek == null || t == null) return false;
        if (!bisaDiterapkanOtomatis(t.aturan)) return false;
        File f = new File(dirProyek, t.berkas);
        if (!f.isFile()) return false;
        String isi = bacaTeks(f);
        if (isi == null) return false;
        String[] baris = isi.split("\n", -1);
        if (t.baris < 1 || t.baris > baris.length) return false;
        boolean diubah = false;
        if (AT_KOMPONEN_TEREKSPOS.equals(t.aturan)) {
            for (int i = t.baris - 1; i < baris.length && i < t.baris + 20; i++) {
                if (baris[i].contains("android:exported=\"true\"")) {
                    baris[i] = baris[i].replace("android:exported=\"true\"",
                            "android:exported=\"false\"");
                    diubah = true;
                    break;
                }
                if (baris[i].contains("</activity") || baris[i].contains("</service")
                        || baris[i].contains("</receiver") || baris[i].contains("</provider")) break;
            }
        } else if (AT_KRIPTO_LEMAH.equals(t.aturan)) {
            String b = baris[t.baris - 1];
            String ganti = b;
            if (ganti.contains("\"MD5\"")) {
                ganti = ganti.replace("\"MD5\"", "\"SHA-256\"");
            } else if (ganti.contains("\"SHA1\"")) {
                ganti = ganti.replace("\"SHA1\"", "\"SHA-256\"");
            } else if (ganti.contains("\"SHA-1\"")) {
                ganti = ganti.replace("\"SHA-1\"", "\"SHA-256\"");
            } else if (ganti.contains("Cipher") && ganti.contains("getInstance")) {
                ganti = ganti.replace("DES", "AES").replace("ECB", "GCM")
                        .replace("PKCS5Padding", "NoPadding");
            }
            if (!ganti.equals(b)) {
                baris[t.baris - 1] = ganti;
                diubah = true;
            }
        } else if (AT_HTTP_TANPA_TLS.equals(t.aturan)) {
            String b = baris[t.baris - 1];
            Matcher hm = POLA_HTTP.matcher(b);
            StringBuffer sb = new StringBuffer();
            boolean ganti = false;
            while (hm.find()) {
                if (httpPerluDiperiksa(hm.group(0))) {
                    hm.appendReplacement(sb, Matcher.quoteReplacement(
                            "https://" + hm.group(0).substring(7)));
                    ganti = true;
                } else {
                    hm.appendReplacement(sb, Matcher.quoteReplacement(hm.group(0)));
                }
            }
            hm.appendTail(sb);
            if (ganti) {
                baris[t.baris - 1] = sb.toString();
                diubah = true;
            }
        }
        if (!diubah) return false;
        return tulisTeks(f, gabungBaris(baris));
    }

    /** Laporan teks lengkap: ringkasan + semua temuan urut risiko. */
    public static String laporan(List<Temuan> daftar) {
        StringBuilder sb = new StringBuilder();
        sb.append(ringkasan(daftar)).append("\n");
        if (daftar == null || daftar.isEmpty()) {
            sb.append(TeksUI.t("Tidak ada temuan. Proyek ini lolos semua aturan yang diperiksa.")).append("\n");
            return sb.toString();
        }
        for (int i = 0; i < daftar.size(); i++) {
            Temuan t = daftar.get(i);
            sb.append("\n[").append(t.tingkat).append("] ")
              .append(t.berkas).append(":").append(t.baris)
              .append(" — ").append(t.aturan).append("\n");
            sb.append(t.penjelasan).append("\n");
            sb.append(TeksUI.t("Perbaikan: ")).append(t.perbaikan).append("\n");
        }
        return sb.toString();
    }

    /** Ringkasan satu baris: jumlah temuan per tingkat risiko. */
    public static String ringkasan(List<Temuan> daftar) {
        int tinggi = 0, sedang = 0, rendah = 0;
        if (daftar != null) {
            for (int i = 0; i < daftar.size(); i++) {
                if (TINGGI.equals(daftar.get(i).tingkat)) tinggi++;
                else if (SEDANG.equals(daftar.get(i).tingkat)) sedang++;
                else rendah++;
            }
        }
        int total = tinggi + sedang + rendah;
        return TeksUI.t("Pemindaian selesai: ") + total + TeksUI.t(" temuan (")
                + tinggi + TeksUI.t(" tinggi, ")
                + sedang + TeksUI.t(" sedang, ")
                + rendah + TeksUI.t(" rendah).");
    }

    // ------------------------------------------------------------- pembantu

    private static int nomorBaris(String isi, int posisi) {
        int n = 1;
        for (int i = 0; i < posisi && i < isi.length(); i++) {
            if (isi.charAt(i) == '\n') n++;
        }
        return n;
    }

    private static String jalurRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) {
            String r = b.substring(a.length());
            while (r.startsWith("/") || r.startsWith("\\")) r = r.substring(1);
            return r.replace('\\', '/');
        }
        return f.getName();
    }

    /** Membaca berkas teks UTF-8; null bila gagal. */
    static String bacaTeks(File f) {
        try {
            if (f == null || !f.isFile()) return null;
            FileInputStream in = new FileInputStream(f);
            InputStreamReader r = new InputStreamReader(in, "UTF-8");
            BufferedReader br = new BufferedReader(r);
            StringBuilder sb = new StringBuilder();
            String b;
            boolean pertama = true;
            while ((b = br.readLine()) != null) {
                if (!pertama) sb.append('\n');
                pertama = false;
                sb.append(b);
            }
            br.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean tulisTeks(File f, String isi) {
        try {
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            out.write(isi);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String gabungBaris(String[] baris) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < baris.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(baris[i]);
        }
        return sb.toString();
    }
}
