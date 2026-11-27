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

    public static final String AT_DEX_API_BERBAHAYA = "DEX_API_BERBAHAYA";
    public static final String AT_DEX_RAHASIA = "DEX_RAHASIA_TERTANAM";
    public static final String AT_DEX_IZIN_BERLEBIH = "DEX_IZIN_BERLEBIH";

    // ------------------------------------------------------- analisis DEX

    /**
     * Memindai classes.dex di dalam sebuah APK hasil build (bukan pola
     * teks kode sumber): API berbahaya yang benar-benar dipanggil,
     * string rahasia yang benar-benar tertanam sebagai konstanta, dan
     * — bila manifest proyek (teks) diberikan — izin yang dideklarasikan
     * tetapi tidak dipakai satu pun API di DEX.
     * APK null/hilang/rusak menghasilkan daftar kosong, tidak pernah
     * melempar. Manifest null berarti aturan izin berlebih dilewati.
     */
    public static List<Temuan> pindaiDex(File apk) {
        return pindaiDex(apk, null);
    }

    public static List<Temuan> pindaiDex(File apk, String manifestXml) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (apk == null || !apk.isFile()) return hasil;
        synchronized (IZIN_DIPAKAI_DEX) {
            IZIN_DIPAKAI_DEX.clear();
        }
        try {
            java.util.zip.ZipFile zf = new java.util.zip.ZipFile(apk);
            try {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                List<String> namaDex = new ArrayList<String>();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.equals("classes.dex") || (n.startsWith("classes") && n.endsWith(".dex"))) {
                        namaDex.add(n);
                    }
                }
                Collections.sort(namaDex);
                for (int i = 0; i < namaDex.size(); i++) {
                    String nama = namaDex.get(i);
                    java.util.zip.ZipEntry entri = zf.getEntry(nama);
                    if (entri == null) continue;
                    java.io.InputStream masuk = zf.getInputStream(entri);
                    byte[] dex = bacaSemua(masuk);
                    masuk.close();
                    if (dex == null) continue;
                    String berkas = apk.getName() + "!" + nama;
                    pindaiSatuDex(dex, berkas, hasil);
                }
            } finally {
                zf.close();
            }
        } catch (Throwable ignored) {
            return hasil;
        }
        if (manifestXml != null) {
            pindaiIzinBerlebih(apk.getName(), manifestXml, hasil);
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

    /** Peta API berbahaya: "kelasDescriptor|namaMetode" -> "IZIN|penjelasan". */
    private static String apiBerbahaya(String kelas, String metode) {
        if ("Ljava/lang/Runtime;".equals(kelas) && "exec".equals(metode)) {
            return "TINGGI|Menjalankan perintah sistem (Runtime.exec). Perintah dari luar bisa disisipkan di sini.";
        }
        if ("Ljava/lang/ProcessBuilder;".equals(kelas) && "start".equals(metode)) {
            return "TINGGI|Menjalankan proses sistem (ProcessBuilder.start).";
        }
        if (("Ldalvik/system/DexClassLoader;".equals(kelas)
                || "Ldalvik/system/PathClassLoader;".equals(kelas))
                && "<init>".equals(metode)) {
            return "TINGGI|Memuat kode dari luar APK (DexClassLoader/PathClassLoader). Kode itu bisa diganti pihak lain.";
        }
        if ("Landroid/webkit/WebView;".equals(kelas) && "addJavascriptInterface".equals(metode)) {
            return "TINGGI|Membuka antarmuka Java ke halaman web (addJavascriptInterface). Halaman berbahaya bisa memanggil kode Java.";
        }
        if ("Landroid/webkit/WebView;".equals(kelas) && "setJavaScriptEnabled".equals(metode)) {
            return "SEDANG|Mengaktifkan JavaScript di WebView (setJavaScriptEnabled).";
        }
        if ("Landroid/telephony/TelephonyManager;".equals(kelas)
                && ("getDeviceId".equals(metode) || "getSubscriberId".equals(metode)
                || "getImei".equals(metode) || "getMeid".equals(metode))) {
            return "TINGGI|Membaca identitas perangkat yang tidak bisa diubah (IMEI/IMSI).";
        }
        if ("Landroid/telephony/SmsManager;".equals(kelas)
                && ("sendTextMessage".equals(metode) || "sendMultipartTextMessage".equals(metode))) {
            return "TINGGI|Mengirim SMS (SmsManager). SMS bisa menghabiskan pulsa tanpa sepengetahuan pengguna.";
        }
        if ("Landroid/location/LocationManager;".equals(kelas)
                && ("getLastKnownLocation".equals(metode)
                || "requestLocationUpdates".equals(metode)
                || "requestSingleUpdate".equals(metode))) {
            return "SEDANG|Membaca lokasi perangkat (LocationManager).";
        }
        if ("Landroid/hardware/Camera;".equals(kelas) && "open".equals(metode)) {
            return "SEDANG|Membuka kamera (Camera.open).";
        }
        if ("Landroid/hardware/camera2/CameraManager;".equals(kelas) && "openCamera".equals(metode)) {
            return "SEDANG|Membuka kamera (CameraManager.openCamera).";
        }
        if ("Landroid/media/AudioRecord;".equals(kelas) && "<init>".equals(metode)) {
            return "SEDANG|Merekam suara (AudioRecord).";
        }
        if ("Landroid/accounts/AccountManager;".equals(kelas) && "getAccounts".equals(metode)) {
            return "SEDANG|Membaca daftar akun di HP (AccountManager.getAccounts).";
        }
        if ("Ljava/lang/System;".equals(kelas)
                && ("load".equals(metode) || "loadLibrary".equals(metode))) {
            return "SEDANG|Memuat pustaka native (System.load/loadLibrary).";
        }
        return null;
    }

    /** Izin Android yang dibutuhkan satu API berbahaya ("" bila tidak ada izin khusus). */
    private static String izinUntukApi(String kelas, String metode) {
        if ("Landroid/telephony/SmsManager;".equals(kelas)) return "SEND_SMS";
        if ("Landroid/location/LocationManager;".equals(kelas)) return "ACCESS_FINE_LOCATION";
        if ("Landroid/hardware/Camera;".equals(kelas)
                || "Landroid/hardware/camera2/CameraManager;".equals(kelas)) return "CAMERA";
        if ("Landroid/media/AudioRecord;".equals(kelas)) return "RECORD_AUDIO";
        if ("Landroid/telephony/TelephonyManager;".equals(kelas)) return "READ_PHONE_STATE";
        if ("Landroid/accounts/AccountManager;".equals(kelas)) return "GET_ACCOUNTS";
        if ("Ljava/net/Socket;".equals(kelas) || "Ljava/net/URL;".equals(kelas)) return "INTERNET";
        return "";
    }

    /** Memindai satu berkas classes.dex; temuan ditambahkan ke `keluar`. */
    private static void pindaiSatuDex(byte[] dex, String berkas, List<Temuan> keluar) {
        ParserDex p = new ParserDex(dex);
        if (!p.sah()) return;
        // Agregasi API berbahaya per target: kunci -> jumlah pemanggil + contoh pemanggil.
        java.util.Map<String, Integer> hitungApi = new java.util.LinkedHashMap<String, Integer>();
        java.util.Map<String, String> contohApi = new java.util.LinkedHashMap<String, String>();
        java.util.Map<String, String> tingkatApi = new java.util.LinkedHashMap<String, String>();
        java.util.Map<String, String> jelasApi = new java.util.LinkedHashMap<String, String>();
        java.util.Set<String> izinDipakai = new java.util.HashSet<String>();
        java.util.Set<String> rahasiaDilihat = new java.util.HashSet<String>();
        for (int i = 0; i < p.metodeKode.size(); i++) {
            ParserDex.MetodeKode mk = p.metodeKode.get(i);
            String pemanggil = mk.kelas + "." + mk.nama;
            for (int j = 0; j < mk.panggilan.size(); j++) {
                ParserDex.RefMetode r = mk.panggilan.get(j);
                String info = apiBerbahaya(r.kelas, r.nama);
                if (info != null) {
                    String kunci = r.kelas + "|" + r.nama;
                    Integer n = hitungApi.get(kunci);
                    hitungApi.put(kunci, n == null ? 1 : n.intValue() + 1);
                    if (!contohApi.containsKey(kunci)) contohApi.put(kunci, pemanggil);
                    int pisah = info.indexOf('|');
                    tingkatApi.put(kunci, info.substring(0, pisah));
                    jelasApi.put(kunci, info.substring(pisah + 1));
                }
                String izin = izinUntukApi(r.kelas, r.nama);
                if (izin.length() > 0) izinDipakai.add(izin);
                if ("Ljava/net/Socket;".equals(r.kelas) || "Ljava/net/URL;".equals(r.kelas)) {
                    izinDipakai.add("INTERNET");
                }
            }
            // Rahasia + kripto lemah dari konstanta string di metode ini.
            for (int j = 0; j < mk.stringKonstan.size(); j++) {
                String s = mk.stringKonstan.get(j);
                String jenisRahasia = jenisRahasiaDex(s);
                if (jenisRahasia != null) {
                    String kunci = jenisRahasia + "|" + pemanggil;
                    if (rahasiaDilihat.add(kunci)) {
                        keluar.add(new Temuan(TINGGI, berkas, 0, AT_DEX_RAHASIA,
                                TeksUI.t("String rahasia tertanam di bytecode (")
                                + jenisRahasia + TeksUI.t("), dipakai di ")
                                + pemanggil + TeksUI.t(". Nilainya tidak ditampilkan di sini supaya tidak bocor. ")
                                + TeksUI.t("Siapa pun yang membongkar APK bisa membaca nilai itu."),
                                TeksUI.t("Pindahkan rahasia dari kode ke penyimpanan aman di HP atau ke server. ")
                                + TeksUI.t("Jangan menulis kunci atau kata sandi langsung di kode.")));
                    }
                }
                if (s.contains("MD5") || s.contains("SHA1") || s.contains("SHA-1")
                        || s.contains("DES") || s.contains("ECB")) {
                    boolean pakaiKripto = false;
                    for (int k = 0; k < mk.panggilan.size(); k++) {
                        ParserDex.RefMetode r = mk.panggilan.get(k);
                        if (("Ljava/security/MessageDigest;".equals(r.kelas)
                                || "Ljavax/crypto/Cipher;".equals(r.kelas))
                                && "getInstance".equals(r.nama)) pakaiKripto = true;
                    }
                    if (pakaiKripto) {
                        String algo = s.contains("MD5") ? "MD5" : s.contains("DES") ? "DES" : "ECB";
                        String kunci = "KRIPTO|" + pemanggil + "|" + algo;
                        if (rahasiaDilihat.add(kunci)) {
                            keluar.add(new Temuan(TINGGI, berkas, 0, AT_KRIPTO_LEMAH,
                                    TeksUI.t("Kriptografi lemah di bytecode: ") + algo
                                    + TeksUI.t(" dipakai di ") + pemanggil + ".",
                                    perbaikan(AT_KRIPTO_LEMAH, "\"" + algo + "\"")));
                        }
                    }
                }
            }
            // WebView: JavaScript aktif + antarmuka JS di kelas yang sama.
            boolean jsAktif = false, jsInterface = false;
            for (int j = 0; j < mk.panggilan.size(); j++) {
                ParserDex.RefMetode r = mk.panggilan.get(j);
                if ("Landroid/webkit/WebView;".equals(r.kelas)
                        && "setJavaScriptEnabled".equals(r.nama)) jsAktif = true;
                if ("Landroid/webkit/WebView;".equals(r.kelas)
                        && "addJavascriptInterface".equals(r.nama)) jsInterface = true;
            }
            if (jsAktif && jsInterface) {
                String kunci = "WEBVIEW|" + mk.kelas;
                if (rahasiaDilihat.add(kunci)) {
                    keluar.add(new Temuan(TINGGI, berkas, 0, AT_WEBVIEW,
                            TeksUI.t("WebView di bytecode kelas ") + mk.kelas
                            + TeksUI.t(" mengaktifkan JavaScript dan membuka antarmuka Java ke halaman web."),
                            perbaikan(AT_WEBVIEW, "")));
                }
            }
        }
        for (java.util.Map.Entry<String, Integer> e : hitungApi.entrySet()) {
            String kunci = e.getKey();
            int pisah = kunci.indexOf('|');
            String target = kunci.substring(0, pisah) + "." + kunci.substring(pisah + 1);
            String tingkat = tingkatApi.get(kunci);
            keluar.add(new Temuan(tingkat, berkas, 0, AT_DEX_API_BERBAHAYA,
                    jelasApi.get(kunci) + " " + TeksUI.t("Dipanggil dari ")
                    + e.getValue() + TeksUI.t(" tempat di DEX, contoh: ")
                    + contohApi.get(kunci) + ".",
                    TeksUI.t("Pastikan pemanggilan ") + target
                    + TeksUI.t(" memang dibutuhkan aplikasi ini, dan minta izinnya dulu bila perlu.")));
        }
        p.izinDipakaiDex = izinDipakai;
        // Simpan gabungan izin dari semua dex di daftar statis sementara.
        synchronized (IZIN_DIPAKAI_DEX) {
            IZIN_DIPAKAI_DEX.addAll(izinDipakai);
        }
    }

    /** Izin yang terbukti dipakai di DEX selama satu kali pindaiDex (dipakai aturan izin berlebih). */
    private static final java.util.Set<String> IZIN_DIPAKAI_DEX =
            new java.util.HashSet<String>();

    /** Jenis rahasia dari satu string konstanta DEX; null bila bukan rahasia. */
    private static String jenisRahasiaDex(String s) {
        if (s == null) return null;
        if (POLA_KUNCI_SK.matcher(s).find()) return "kunci sk-...";
        if (POLA_KUNCI_AKIA.matcher(s).find()) return "kunci AKIA...";
        if (POLA_KUNCI_AIZA.matcher(s).find()) return "kunci AIza...";
        if (POLA_KUNCI_API_KEY.matcher(s).find()) return "api_key";
        if (POLA_KATA_SANDI.matcher(s).find()) return "password";
        return null;
    }

    /** Aturan izin berlebih: izin di manifest proyek yang tak dipakai satu pun API di DEX. */
    private static void pindaiIzinBerlebih(String namaApk, String manifestXml, List<Temuan> keluar) {
        if (manifestXml == null) return;
        java.util.Set<String> dipakai;
        synchronized (IZIN_DIPAKAI_DEX) {
            dipakai = new java.util.HashSet<String>(IZIN_DIPAKAI_DEX);
            IZIN_DIPAKAI_DEX.clear();
        }
        Matcher m = Pattern.compile("<uses-permission[^>]*android:name=\"([^\"]+)\"").matcher(manifestXml);
        java.util.Set<String> sudah = new java.util.HashSet<String>();
        while (m.find()) {
            String nama = m.group(1);
            if (nama.startsWith("android.permission.")) {
                nama = nama.substring("android.permission.".length());
            }
            if (!sudah.add(nama)) continue;
            if ("INTERNET".equals(nama) || "ACCESS_NETWORK_STATE".equals(nama)) continue;
            if (dipakai.contains(nama)) continue;
            // Hanya laporkan izin yang punya pemetaan API yang dikenal.
            boolean dikenal = "SEND_SMS".equals(nama) || "ACCESS_FINE_LOCATION".equals(nama)
                    || "ACCESS_COARSE_LOCATION".equals(nama) || "CAMERA".equals(nama)
                    || "RECORD_AUDIO".equals(nama) || "READ_PHONE_STATE".equals(nama)
                    || "GET_ACCOUNTS".equals(nama) || "READ_EXTERNAL_STORAGE".equals(nama)
                    || "WRITE_EXTERNAL_STORAGE".equals(nama) || "READ_CONTACTS".equals(nama);
            if (!dikenal) continue;
            if ("ACCESS_COARSE_LOCATION".equals(nama) && dipakai.contains("ACCESS_FINE_LOCATION")) continue;
            keluar.add(new Temuan(SEDANG, "AndroidManifest.xml", 0, AT_DEX_IZIN_BERLEBIH,
                    TeksUI.t("Izin ") + nama + TeksUI.t(" dideklarasikan di manifest, tetapi tidak ada pemanggilan API di classes.dex yang membutuhkannya. ")
                    + TeksUI.t("Izin berlebih membuat pengguna curiga dan memperluas permukaan serangan."),
                    TeksUI.t("Hapus <uses-permission android:name=\"android.permission.") + nama
                    + TeksUI.t("\" /> dari manifest bila aplikasi ini memang tidak memakai izin itu.")));
        }
    }

    /** Membaca seluruh aliran menjadi byte[]; null bila gagal. */
    private static byte[] bacaSemua(java.io.InputStream masuk) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = masuk.read(buf)) >= 0) b.write(buf, 0, n);
            return b.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Parser DEX minimal (murni Java): header, string/type/method id,
     * class def, class data, dan instruksi tiap metode (invoke +
     * const-string). Tidak memeriksa checksum/signature; dex rusak
     * menghasilkan sah()==false dan daftar metode kosong.
     */
    static class ParserDex {
        static class RefMetode {
            final String kelas;
            final String nama;
            RefMetode(String k, String n) { kelas = k; nama = n; }
        }

        static class MetodeKode {
            String kelas = "";
            String nama = "";
            final List<RefMetode> panggilan = new ArrayList<RefMetode>();
            final List<String> stringKonstan = new ArrayList<String>();
        }

        final List<MetodeKode> metodeKode = new ArrayList<MetodeKode>();
        java.util.Set<String> izinDipakaiDex = new java.util.HashSet<String>();

        private final byte[] d;
        private boolean sah = false;

        ParserDex(byte[] dex) {
            d = dex;
            try {
                urai();
            } catch (Throwable t) {
                metodeKode.clear();
                sah = false;
            }
        }

        boolean sah() {
            return sah;
        }

        private int u8(int p) {
            return d[p] & 0xFF;
        }

        private int u16(int p) {
            return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
        }

        private int u32(int p) {
            return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8)
                    | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
        }

        /** Membaca uleb128 di posisi p; nilai di [0], posisi lanjut di [1]. */
        private int[] uleb(int p) {
            int hasil = 0, geser = 0;
            while (p < d.length && geser < 32) {
                int b = u8(p++);
                hasil |= (b & 0x7F) << geser;
                if ((b & 0x80) == 0) break;
                geser += 7;
            }
            return new int[]{hasil, p};
        }

        private void urai() {
            if (d == null || d.length < 112) return;
            if (d[0] != 'd' || d[1] != 'e' || d[2] != 'x' || d[3] != '\n') return;
            int stringIdsSize = u32(56);
            int stringIdsOff = u32(60);
            int typeIdsSize = u32(64);
            int typeIdsOff = u32(68);
            int methodIdsSize = u32(88);
            int methodIdsOff = u32(92);
            int classDefsSize = u32(96);
            int classDefsOff = u32(100);
            if (stringIdsOff < 0 || typeIdsOff < 0 || methodIdsOff < 0 || classDefsOff < 0) return;
            // String.
            String[] strings = new String[stringIdsSize];
            for (int i = 0; i < stringIdsSize; i++) {
                int off = u32(stringIdsOff + i * 4);
                if (off < 0 || off >= d.length) { strings[i] = ""; continue; }
                int[] r = uleb(off);
                int p = r[1];
                int akhir = p;
                while (akhir < d.length && d[akhir] != 0) akhir++;
                try {
                    strings[i] = new String(d, p, akhir - p, "UTF-8");
                } catch (Throwable t) {
                    strings[i] = "";
                }
            }
            tabelString = strings;
            // Type -> descriptor string.
            String[] types = new String[typeIdsSize];
            for (int i = 0; i < typeIdsSize; i++) {
                int idx = u32(typeIdsOff + i * 4);
                types[i] = (idx >= 0 && idx < strings.length) ? strings[idx] : "";
            }
            // Method id: class_idx u16, proto_idx u16, name_idx u32.
            String[] methodKelas = new String[methodIdsSize];
            String[] methodNama = new String[methodIdsSize];
            for (int i = 0; i < methodIdsSize; i++) {
                int p = methodIdsOff + i * 8;
                int classIdx = u16(p);
                int nameIdx = u32(p + 4);
                methodKelas[i] = (classIdx >= 0 && classIdx < types.length) ? types[classIdx] : "";
                methodNama[i] = (nameIdx >= 0 && nameIdx < strings.length) ? strings[nameIdx] : "";
            }
            // Class def: class_idx u32, ..., class_data_off u32 di +24.
            for (int i = 0; i < classDefsSize; i++) {
                int p = classDefsOff + i * 32;
                int classIdx = u32(p);
                int classDataOff = u32(p + 24);
                String namaKelas = (classIdx >= 0 && classIdx < types.length) ? types[classIdx] : "";
                if (classDataOff == 0) continue;
                uraiClassData(classDataOff, namaKelas, methodKelas, methodNama);
            }
            sah = true;
        }

        private void uraiClassData(int off, String namaKelas,
                                   String[] methodKelas, String[] methodNama) {
            int p = off;
            int[] r = uleb(p); int staticFields = r[0]; p = r[1];
            r = uleb(p); int instanceFields = r[0]; p = r[1];
            r = uleb(p); int directMethods = r[0]; p = r[1];
            r = uleb(p); int virtualMethods = r[0]; p = r[1];
            for (int i = 0; i < staticFields + instanceFields; i++) {
                r = uleb(p); p = r[1];
                r = uleb(p); p = r[1];
            }
            int idxMetode = 0;
            for (int i = 0; i < directMethods + virtualMethods; i++) {
                r = uleb(p); idxMetode += r[0]; p = r[1];
                r = uleb(p); p = r[1];
                r = uleb(p); int codeOff = r[0]; p = r[1];
                if (codeOff != 0 && idxMetode >= 0 && idxMetode < methodKelas.length) {
                    MetodeKode mk = new MetodeKode();
                    mk.kelas = namaKelas;
                    mk.nama = methodNama[idxMetode];
                    uraiCode(codeOff, mk, methodKelas, methodNama);
                    metodeKode.add(mk);
                }
            }
        }

        private void uraiCode(int off, MetodeKode mk,
                              String[] methodKelas, String[] methodNama) {
            if (off + 16 > d.length) return;
            int insnsSize = u32(off + 12);
            int p = off + 16;
            int akhir = Math.min(d.length, p + insnsSize * 2);
            int pc = p;
            while (pc + 1 < akhir) {
                int unit = u16(pc);
                int op = unit & 0xFF;
                if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)) {
                    if (pc + 3 < akhir) {
                        int midx = u16(pc + 2);
                        if (midx >= 0 && midx < methodKelas.length) {
                            mk.panggilan.add(new RefMetode(methodKelas[midx], methodNama[midx]));
                        }
                    }
                } else if (op == 0xfa) {
                    if (pc + 3 < akhir) {
                        int midx = u16(pc + 2);
                        if (midx >= 0 && midx < methodKelas.length) {
                            mk.panggilan.add(new RefMetode(methodKelas[midx], methodNama[midx]));
                        }
                    }
                } else if (op == 0x1a) {
                    if (pc + 3 < akhir) {
                        // const-string: string idx ada di unit kedua; string
                        // aslinya diambil dari tabel string lewat methodNama?
                        // Tidak: string idx merujuk tabel string, bukan metode.
                        // Disimpan sebagai indeks mentah di daftar terpisah.
                        mk.stringKonstan.add("\u0001IDX:" + u16(pc + 2));
                    }
                } else if (op == 0x1b) {
                    if (pc + 5 < akhir) {
                        mk.stringKonstan.add("\u0001IDX:" + u32(pc + 2));
                    }
                }
                int len = panjangInstruksi(op);
                if (len <= 0) len = 1;
                pc += len * 2;
            }
            // Ganti penanda indeks string mentah dengan string aslinya.
            for (int i = 0; i < mk.stringKonstan.size(); i++) {
                String s = mk.stringKonstan.get(i);
                if (s.startsWith("\u0001IDX:")) {
                    try {
                        int idx = Integer.parseInt(s.substring(5));
                        mk.stringKonstan.set(i, stringKe(idx));
                    } catch (Throwable t) {
                        mk.stringKonstan.set(i, "");
                    }
                }
            }
        }

        /** String ke-idx dari tabel string; butuh tabel — diisi saat urai(). */
        private String[] tabelString = new String[0];

        private String stringKe(int idx) {
            if (idx >= 0 && idx < tabelString.length) return tabelString[idx];
            return "";
        }

        /** Panjang instruksi Dalvik dalam unit 16-bit (1 bila tak dikenal). */
        static int panjangInstruksi(int op) {
            switch (op) {
                case 0x02: case 0x03: case 0x05: case 0x06: case 0x08: case 0x09:
                case 0x13: case 0x15: case 0x16: case 0x19: case 0x1a: case 0x1c:
                case 0x1f: case 0x20: case 0x22: case 0x23: case 0x29: case 0x2d:
                case 0x2e: case 0x2f: case 0x30: case 0x31: case 0x32: case 0x33:
                case 0x34: case 0x35: case 0x36: case 0x37: case 0x38: case 0x39:
                case 0x3a: case 0x3b: case 0x3c: case 0x3d: case 0x44: case 0x45:
                case 0x46: case 0x47: case 0x48: case 0x49: case 0x4a: case 0x4b:
                case 0x4c: case 0x4d: case 0x4e: case 0x4f: case 0x50: case 0x51:
                case 0x52: case 0x53: case 0x54: case 0x55: case 0x56: case 0x57:
                case 0x58: case 0x59: case 0x5a: case 0x5b: case 0x5c: case 0x5d:
                case 0x5e: case 0x5f: case 0x60: case 0x61: case 0x62: case 0x63:
                case 0x64: case 0x65: case 0x66: case 0x67: case 0x68: case 0x69:
                case 0x6a: case 0x6b: case 0x6c: case 0x6d: case 0x90: case 0x91:
                case 0x92: case 0x93: case 0x94: case 0x95: case 0x96: case 0x97:
                case 0x98: case 0x99: case 0x9a: case 0x9b: case 0x9c: case 0x9d:
                case 0x9e: case 0x9f: case 0xa0: case 0xa1: case 0xa2: case 0xa3:
                case 0xa4: case 0xa5: case 0xa6: case 0xa7: case 0xa8: case 0xa9:
                case 0xaa: case 0xab: case 0xac: case 0xad: case 0xae: case 0xaf:
                case 0xd0: case 0xd1: case 0xd2: case 0xd3: case 0xd4: case 0xd5:
                case 0xd6: case 0xd7: case 0xd8: case 0xd9: case 0xda: case 0xdb:
                case 0xdc: case 0xdd: case 0xde: case 0xdf: case 0xe0: case 0xe1:
                case 0xe2: case 0xfd: case 0xfe:
                    return 2;
                case 0x14: case 0x17: case 0x18: case 0x1b: case 0x24: case 0x25:
                case 0x26: case 0x2a: case 0x2b: case 0x2c: case 0x6e: case 0x6f:
                case 0x70: case 0x71: case 0x72: case 0x74: case 0x75: case 0x76:
                case 0x77: case 0x78: case 0xfb: case 0xfc:
                    return 3;
                case 0xfa:
                    return 4;
                default:
                    return 1;
            }
        }
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
