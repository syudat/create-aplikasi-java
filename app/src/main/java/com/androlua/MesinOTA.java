package com.androlua;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Mesin Pembaruan Over-The-Air dan Manajemen Rilis Bertahap Mandiri
 * (Fitur 14, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - selisih: perbedaan baris antara dua versi teks (LCS sederhana).
 *  - buatPatch: paket patch (zip) berisi patch.txt + manifest SHA-256
 *    isi baru (sidik jari keutuhan, bukan tanda tangan kunci privat).
 *  - buatPatchTertanda / terapkanPatchTeks: paket patch teks yang
 *    ditandatangani kunci privat RSA (SHA256withRSA) dan diverifikasi
 *    sebelum diterapkan.
 *  - buatDelta / terapkanDelta: patch BINER atas bita mentah
 *    (DEX/APK/berkas apa pun) — delta blok COPY/INSERT gaya rsync
 *    (Adler-32 bergulir + SHA-256 per blok), bukan hanya berkas teks.
 *  - buatPatchBiner / terapkanPatchBiner: paket patch biner zip
 *    (patch.bin + MANIFEST.txt + TANDA-TANGAN.bin + KUNCI-PUBLIK.der);
 *    tanda tangan diverifikasi dengan kunci publik tepercaya SEBELUM
 *    patch diterapkan; hasil penerapan diperiksa SHA-256-nya; bila
 *    gagal, berkas lama tetap utuh (rollback).
 *  - buatKunciOta: pasangan kunci RSA 2048 gaya AOSP (.pk8 + .der);
 *    kunci privat disimpan di proyek pengembang, kunci publik yang
 *    didaftarkan sebagai tepercaya dipakai memverifikasi patch.
 *  - unduhPatch / bacaDaftarRilis: penargetan server sungguhan —
 *    patch diunduh lewat HTTP(S) dari alamat peladen yang ditentukan
 *    pengembang (daftar rilis di rilis.txt), lalu hash-nya diperiksa.
 *  - terapkanPatch: bangun versi baru dari versi lama + patch, lalu
 *    verifikasi hash; bila gagal, versi lama tetap dipakai (rollback).
 *  - kelompokPeluncuran: perangkat masuk tahap awal bila hash
 *    (id perangkat + nama rilis) jatuh dalam persentase target.
 *
 * MENERAPKAN KE APLIKASI YANG BERJALAN: kode DEX yang sudah dimuat
 * tidak bisa diganti dari dalam proses yang sama — Android mengunci
 * berkas APK yang sedang berjalan. Jalur nyata yang dipakai di sini:
 * patch diterapkan ke berkas APK (menghasilkan APK baru yang hash-nya
 * terbukti sama dengan versi baru), APK hasil patch ditandatangani
 * ulang dengan kunci proyek yang sama, lalu dipasang lewat
 * PackageInstaller — sistem mengganti aplikasi dan membukanya ulang
 * dengan kode baru. Langkah pemasangan itu ada di MesinOTAActivity.
 * Patch ke IDE ini sendiri tidak bisa ditandatangani ulang di HP
 * karena kunci debug pembangun tidak ada di perangkat; untuk aplikasi
 * proyek (yang kuncinya ada di folder tanda-tangan-bawaan proyek)
 * jalurnya lengkap.
 */
public class MesinOTA {

    /** Ukuran blok delta biner (bita). */
    public static final int UKURAN_BLOK = 4096;

    /** Batas baca satu sambungan HTTP: sambung 15 detik, baca 60 detik. */
    public static final int BATAS_SAMBUNG_MS = 15000;
    public static final int BATAS_BACA_MS = 60000;

    /** Satu baris patch: operasi + nomor baris + isi. */
    public static class BarisPatch {
        public String operasi = "";
        public int baris = 0;
        public String isi = "";
        public String toString() {
            return operasi + " " + baris + " " + isi;
        }
    }

    /** Hasil penerapan patch biner. */
    public static class HasilTerap {
        public boolean berhasil = false;
        public String galat = "";
        public long ukuranBaru = 0;
        public String sha256Baru = "";
        public String toString() {
            return berhasil ? "Patch diterapkan (" + ukuranBaru + " bita)."
                    : "Gagal: " + galat;
        }
    }

    /** Satu rilis di daftar rilis server (rilis.txt). */
    public static class InfoRilis {
        public String nama = "";
        public String versi = "";
        public String sha256 = "";
        public String toString() {
            return nama + "|" + versi + "|" + sha256;
        }
    }

    /** Isi paket patch (teks maupun biner) sesudah dibaca dari zip. */
    static class PaketPatch {
        byte[] manifes;
        Map<String, String> nilai = new HashMap<String, String>();
        byte[] patchTeks;
        byte[] patchBiner;
        byte[] tandaTangan;
        byte[] kunciPublik;
    }

    // ------------------------------------------------------- selisih teks (lama)

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

    /** SHA-256 heksadesimal dari bita. */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data == null ? new byte[0] : data);
            StringBuilder b = new StringBuilder(h.length * 2);
            for (int i = 0; i < h.length; i++) {
                b.append(Character.forDigit((h[i] >> 4) & 0xf, 16));
                b.append(Character.forDigit(h[i] & 0xf, 16));
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** SHA-256 heksadesimal isi berkas (streaming, hemat memori). */
    public static String sha256Berkas(File f) {
        if (f == null || !f.isFile()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            } finally {
                in.close();
            }
            byte[] h = md.digest();
            StringBuilder b = new StringBuilder(h.length * 2);
            for (int i = 0; i < h.length; i++) {
                b.append(Character.forDigit((h[i] >> 4) & 0xf, 16));
                b.append(Character.forDigit(h[i] & 0xf, 16));
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** True bila SHA-256 berkas sama dengan yang diharapkan. */
    public static boolean cocokSha256(File f, String sha256) {
        if (sha256 == null || sha256.length() == 0) return false;
        return sha256.equalsIgnoreCase(sha256Berkas(f));
    }

    // ------------------------------------------------------- paket patch teks (lama)

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

    static byte[] bacaBerkas(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return buf.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean tulisBerkas(File f, byte[] data) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------- kunci RSA + tanda tangan

    /** Buat pasangan kunci RSA 2048 bit baru (null bila gagal). */
    public static KeyPair buatPasanganKunci() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            return kpg.generateKeyPair();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Buat kunci OTA di folder proyek (gaya kunci OTA AOSP: berkas
     * .pk8 + .der): ota-privat.pk8 (kunci privat PKCS#8) dan
     * ota-publik.der (kunci publik X.509). Kunci privat HANYA untuk
     * pengembang — jangan pernah dibagikan atau diunggah ke server.
     * Mengembalikan pasangan kunci (null bila gagal).
     */
    public static KeyPair buatKunciOta(File dirKunci) {
        try {
            if (dirKunci == null) return null;
            dirKunci.mkdirs();
            KeyPair kp = buatPasanganKunci();
            if (kp == null) return null;
            if (!tulisBerkas(new File(dirKunci, "ota-privat.pk8"),
                    kp.getPrivate().getEncoded())) return null;
            if (!tulisBerkas(new File(dirKunci, "ota-publik.der"),
                    kp.getPublic().getEncoded())) return null;
            return kp;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Baca kunci privat dari berkas .pk8 (PKCS#8). */
    public static PrivateKey bacaKunciPrivat(File f) {
        try {
            byte[] data = bacaBerkas(f);
            if (data == null) return null;
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(data));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Baca kunci publik dari berkas .der (X.509 SubjectPublicKeyInfo). */
    public static PublicKey bacaKunciPublik(File f) {
        try {
            byte[] data = bacaBerkas(f);
            if (data == null) return null;
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(data));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Kunci publik dari bita .der (untuk kunci yang dibawa paket patch). */
    static PublicKey kunciPublikDariBita(byte[] data) {
        try {
            if (data == null) return null;
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(data));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Sidik jari SHA-256 kunci publik (untuk membandingkan kunci tepercaya). */
    public static String sidikJariKunciPublik(PublicKey kunci) {
        if (kunci == null) return "";
        return sha256Hex(kunci.getEncoded());
    }

    /**
     * Kunci publik tepercaya untuk memverifikasi patch: pertama cari
     * ota-kunci-tepercaya/ota-publik.der (kunci pengembang yang
     * didaftarkan), bila tidak ada pakai ota-kunci/ota-publik.der
     * (kunci proyek sendiri). Null bila tidak ada kunci tepercaya.
     */
    public static PublicKey bacaKunciPublikTepercaya(File dirProyek) {
        if (dirProyek == null) return null;
        PublicKey k = bacaKunciPublik(
                new File(new File(dirProyek, "ota-kunci-tepercaya"), "ota-publik.der"));
        if (k != null) return k;
        return bacaKunciPublik(new File(new File(dirProyek, "ota-kunci"), "ota-publik.der"));
    }

    /** Tandatangani data dengan kunci privat (SHA256withRSA). */
    public static byte[] tandaTangani(byte[] data, PrivateKey privat) {
        try {
            if (data == null || privat == null) return null;
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(privat);
            s.update(data);
            return s.sign();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Periksa tanda tangan SHA256withRSA atas data dengan kunci publik. */
    public static boolean verifikasiTandaTangan(byte[] data, byte[] tanda, PublicKey publik) {
        try {
            if (data == null || tanda == null || publik == null) return false;
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(publik);
            s.update(data);
            return s.verify(tanda);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------- delta biner

    /** Konstanta Adler-32. */
    private static final int MOD_ADLER = 65521;

    /** Adler-32 atas jendela bita (a di 16 bit bawah, b di 16 bit atas). */
    static long adler(byte[] data, int off, int len) {
        long a = 1;
        long b = 0;
        for (int i = 0; i < len; i++) {
            a += data[off + i] & 0xFF;
            if (a >= MOD_ADLER) a -= MOD_ADLER;
            b += a;
            if (b >= MOD_ADLER) b %= MOD_ADLER;
        }
        return (b << 16) | a;
    }

    /** Geser jendela Adler-32 satu bita (keluar/masuk), panjang jendela n.
     * Turunan dari definisi a = 1 + jumlah bita, b = jumlah nilai a
     * berjalan: a' = a - keluar + masuk; b' = b - n*keluar + a' - 1. */
    static long adlerGeser(long adler, int keluar, int masuk, int n) {
        long a = adler & 0xFFFF;
        long b = (adler >> 16) & 0xFFFF;
        a = a - keluar + masuk;
        a %= MOD_ADLER;
        if (a < 0) a += MOD_ADLER;
        b = b - (long) n * keluar + a - 1;
        b %= MOD_ADLER;
        if (b < 0) b += MOD_ADLER;
        return (b << 16) | a;
    }

    /**
     * Buat delta biner lama -> baru. Format: "OTAD", int ukuran blok,
     * long panjang lama, long panjang baru, 32 bita SHA-256 lama,
     * 32 bita SHA-256 baru, lalu operasi: 0x00 COPY (int offset lama,
     * int panjang) atau 0x01 INSERT (int panjang, bita). Blok lama
     * dicari lewat Adler-32 bergulir (gaya rsync) lalu dipastikan
     * dengan perbandingan bita penuh.
     */
    public static byte[] buatDelta(byte[] lama, byte[] baru) {
        if (lama == null) lama = new byte[0];
        if (baru == null) baru = new byte[0];
        try {
            Map<Long, List<Integer>> peta = new HashMap<Long, List<Integer>>();
            for (int off = 0; off + UKURAN_BLOK <= lama.length; off += UKURAN_BLOK) {
                long cek = adler(lama, off, UKURAN_BLOK);
                List<Integer> daftar = peta.get(Long.valueOf(cek));
                if (daftar == null) {
                    daftar = new ArrayList<Integer>();
                    peta.put(Long.valueOf(cek), daftar);
                }
                daftar.add(Integer.valueOf(off));
            }
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(keluar);
            out.writeBytes("OTAD");
            out.writeInt(UKURAN_BLOK);
            out.writeLong(lama.length);
            out.writeLong(baru.length);
            out.write(sha256Bita(lama));
            out.write(sha256Bita(baru));
            ByteArrayOutputStream literal = new ByteArrayOutputStream();
            int pos = 0;
            long jendela = -1;
            while (pos < baru.length) {
                int sisa = baru.length - pos;
                if (sisa >= UKURAN_BLOK) {
                    if (jendela < 0) jendela = adler(baru, pos, UKURAN_BLOK);
                    List<Integer> calon = peta.get(Long.valueOf(jendela));
                    int cocok = -1;
                    if (calon != null) {
                        for (int i = 0; i < calon.size(); i++) {
                            int off = calon.get(i).intValue();
                            if (samaBlok(lama, off, baru, pos, UKURAN_BLOK)) {
                                cocok = off;
                                break;
                            }
                        }
                    }
                    if (cocok >= 0) {
                        tulisLiteral(out, literal);
                        out.writeByte(0);
                        out.writeInt(cocok);
                        out.writeInt(UKURAN_BLOK);
                        pos += UKURAN_BLOK;
                        jendela = -1;
                        continue;
                    }
                    literal.write(baru[pos]);
                    if (pos + UKURAN_BLOK < baru.length) {
                        jendela = adlerGeser(jendela, baru[pos] & 0xFF,
                                baru[pos + UKURAN_BLOK] & 0xFF, UKURAN_BLOK);
                    }
                    pos++;
                } else {
                    // Ekor (kurang dari satu blok): bila ekor lama dan baru
                    // sama persis di offset yang sama, salin saja.
                    if (pos < lama.length && lama.length - pos == sisa
                            && samaBlok(lama, pos, baru, pos, sisa)) {
                        tulisLiteral(out, literal);
                        out.writeByte(0);
                        out.writeInt(pos);
                        out.writeInt(sisa);
                    } else {
                        literal.write(baru, pos, sisa);
                    }
                    pos += sisa;
                }
            }
            tulisLiteral(out, literal);
            out.flush();
            return keluar.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void tulisLiteral(DataOutputStream out, ByteArrayOutputStream literal)
            throws Exception {
        if (literal.size() > 0) {
            out.writeByte(1);
            out.writeInt(literal.size());
            out.write(literal.toByteArray());
            literal.reset();
        }
    }

    private static boolean samaBlok(byte[] a, int offA, byte[] b, int offB, int len) {
        for (int i = 0; i < len; i++) {
            if (a[offA + i] != b[offB + i]) return false;
        }
        return true;
    }

    static byte[] sha256Bita(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(data == null ? new byte[0] : data);
        } catch (Throwable t) {
            return new byte[32];
        }
    }

    /**
     * Terapkan delta biner ke bita lama. Memeriksa magic, panjang, dan
     * SHA-256 hasil; melempar IllegalArgumentException bila delta
     * rusak atau hasilnya tidak cocok.
     */
    public static byte[] terapkanDelta(byte[] lama, byte[] delta) {
        if (lama == null) lama = new byte[0];
        if (delta == null) throw new IllegalArgumentException("Delta kosong.");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(delta));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'O' || magic[1] != 'T' || magic[2] != 'A' || magic[3] != 'D') {
                throw new IllegalArgumentException("Bukan delta OTA (magic salah).");
            }
            int blok = in.readInt();
            if (blok <= 0) throw new IllegalArgumentException("Ukuran blok delta tidak sah.");
            long panjangLama = in.readLong();
            long panjangBaru = in.readLong();
            byte[] hashLama = new byte[32];
            byte[] hashBaru = new byte[32];
            in.readFully(hashLama);
            in.readFully(hashBaru);
            if (panjangLama != lama.length) {
                throw new IllegalArgumentException("Delta dibuat untuk berkas lama yang berbeda.");
            }
            if (!samaBita(hashLama, sha256Bita(lama))) {
                throw new IllegalArgumentException("SHA-256 berkas lama tidak cocok dengan delta.");
            }
            if (panjangBaru < 0 || panjangBaru > 512L * 1024L * 1024L) {
                throw new IllegalArgumentException("Panjang hasil delta tidak masuk akal.");
            }
            ByteArrayOutputStream hasil = new ByteArrayOutputStream();
            while (in.available() > 0) {
                int jenis = in.readByte() & 0xFF;
                if (jenis == 0) {
                    int off = in.readInt();
                    int panjang = in.readInt();
                    if (off < 0 || panjang < 0 || (long) off + panjang > lama.length) {
                        throw new IllegalArgumentException("Operasi COPY di luar berkas lama.");
                    }
                    hasil.write(lama, off, panjang);
                } else if (jenis == 1) {
                    int panjang = in.readInt();
                    if (panjang < 0 || panjang > in.available()) {
                        throw new IllegalArgumentException("Operasi INSERT terpotong.");
                    }
                    byte[] buf = new byte[panjang];
                    in.readFully(buf);
                    hasil.write(buf);
                } else {
                    throw new IllegalArgumentException("Jenis operasi delta tidak dikenal: " + jenis);
                }
            }
            byte[] baru = hasil.toByteArray();
            if (baru.length != panjangBaru) {
                throw new IllegalArgumentException("Panjang hasil tidak cocok dengan delta.");
            }
            if (!samaBita(hashBaru, sha256Bita(baru))) {
                throw new IllegalArgumentException("SHA-256 hasil tidak cocok dengan delta.");
            }
            return baru;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalArgumentException("Delta rusak: " + t.getMessage());
        }
    }

    private static boolean samaBita(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    // ------------------------------------------------------- paket patch tertanda

    /** Teks MANIFEST.txt untuk paket patch (teks maupun biner). */
    static byte[] bytesManifes(String namaRilis, String namaBerkas,
                               String shaLama, String shaBaru, long ukuranBaru,
                               String tambahan) {
        StringBuilder b = new StringBuilder();
        b.append("rilis: ").append(namaRilis == null ? "" : namaRilis).append("\n");
        b.append("berkas: ").append(namaBerkas == null ? "" : namaBerkas).append("\n");
        b.append("sha256-lama: ").append(shaLama == null ? "" : shaLama).append("\n");
        b.append("sha256-baru: ").append(shaBaru == null ? "" : shaBaru).append("\n");
        b.append("ukuran-baru: ").append(ukuranBaru).append("\n");
        if (tambahan != null) b.append(tambahan);
        try {
            return b.toString().getBytes("UTF-8");
        } catch (Throwable t) {
            return b.toString().getBytes();
        }
    }

    /** Urai MANIFEST.txt "kunci: nilai" menjadi peta. */
    static Map<String, String> uraiManifes(byte[] data) {
        Map<String, String> hasil = new HashMap<String, String>();
        if (data == null) return hasil;
        String teks;
        try {
            teks = new String(data, "UTF-8");
        } catch (Throwable t) {
            return hasil;
        }
        String[] baris = teks.split("\n");
        for (int i = 0; i < baris.length; i++) {
            int titik = baris[i].indexOf(':');
            if (titik <= 0) continue;
            hasil.put(baris[i].substring(0, titik).trim(),
                    baris[i].substring(titik + 1).trim());
        }
        return hasil;
    }

    /** Baca entri zip menjadi bita (null bila tidak ada/gagal). */
    static byte[] bacaEntriZip(ZipFile zf, String nama) {
        try {
            ZipEntry e = zf.getEntry(nama);
            if (e == null) return null;
            InputStream in = zf.getInputStream(e);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                in.close();
            }
            return buf.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Baca paket patch (zip) menjadi PaketPatch; null bila bukan paket patch. */
    static PaketPatch bacaPaketPatch(File paket) {
        if (paket == null || !paket.isFile()) return null;
        ZipFile zf;
        try {
            zf = new ZipFile(paket);
        } catch (Throwable t) {
            return null;
        }
        try {
            PaketPatch p = new PaketPatch();
            p.manifes = bacaEntriZip(zf, "MANIFEST.txt");
            if (p.manifes == null) return null;
            p.nilai = uraiManifes(p.manifes);
            p.patchTeks = bacaEntriZip(zf, "patch.txt");
            p.patchBiner = bacaEntriZip(zf, "patch.bin");
            p.tandaTangan = bacaEntriZip(zf, "TANDA-TANGAN.bin");
            p.kunciPublik = bacaEntriZip(zf, "KUNCI-PUBLIK.der");
            return p;
        } finally {
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Periksa paket patch: kunci publik di paket harus sama dengan
     * kunci tepercaya (sidik jari cocok), lalu tanda tangan RSA atas
     * MANIFEST.txt harus sah. False bila ada satu saja yang gagal.
     */
    public static boolean verifikasiPaketPatch(File paket, PublicKey tepercaya) {
        if (paket == null || tepercaya == null) return false;
        PaketPatch p = bacaPaketPatch(paket);
        if (p == null || p.tandaTangan == null || p.kunciPublik == null) return false;
        PublicKey kunciPaket = kunciPublikDariBita(p.kunciPublik);
        if (kunciPaket == null) return false;
        if (!sidikJariKunciPublik(kunciPaket).equals(sidikJariKunciPublik(tepercaya))) {
            return false;
        }
        return verifikasiTandaTangan(p.manifes, p.tandaTangan, tepercaya);
    }

    /**
     * Buat paket patch TEKS yang ditandatangani: patch.txt +
     * MANIFEST.txt + TANDA-TANGAN.bin (RSA atas MANIFEST.txt) +
     * KUNCI-PUBLIK.der. Mengembalikan berkas tujuan (null bila gagal).
     */
    public static File buatPatchTertanda(File berkasLama, File berkasBaru,
                                         String namaRilis, PrivateKey privat,
                                         PublicKey publik, File keluar) {
        try {
            if (berkasLama == null || berkasBaru == null || privat == null
                    || publik == null || keluar == null) return null;
            String lama = bacaTeks(berkasLama);
            String baru = bacaTeks(berkasBaru);
            if (lama == null || baru == null) return null;
            List<BarisPatch> patch = selisih(lama, baru);
            byte[] manifes = bytesManifes(namaRilis, berkasBaru.getName(),
                    sha256Hex(lama), sha256Hex(baru), baru.getBytes("UTF-8").length,
                    "baris-patch: " + patch.size() + "\n");
            byte[] tanda = tandaTangani(manifes, privat);
            if (tanda == null) return null;
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(keluar));
            try {
                tulisEntri(zip, "patch.txt", teksPatch(patch).getBytes("UTF-8"));
                tulisEntri(zip, "MANIFEST.txt", manifes);
                tulisEntri(zip, "TANDA-TANGAN.bin", tanda);
                tulisEntri(zip, "KUNCI-PUBLIK.der", publik.getEncoded());
            } finally {
                zip.close();
            }
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Terapkan paket patch TEKS tertanda ke berkas lama: verifikasi
     * tanda tangan dulu, lalu terapkan selisih dan periksa SHA-256
     * hasil. Penulisan atomik lewat berkas sementara — bila gagal,
     * berkas lama tetap utuh.
     */
    public static HasilTerap terapkanPatchTeks(File berkasLama, File paket,
                                               PublicKey tepercaya, File keluar) {
        HasilTerap h = new HasilTerap();
        if (berkasLama == null || !berkasLama.isFile()) {
            h.galat = "Berkas lama tidak ditemukan.";
            return h;
        }
        if (!verifikasiPaketPatch(paket, tepercaya)) {
            h.galat = "Tanda tangan patch tidak sah atau kunci tidak tepercaya.";
            return h;
        }
        PaketPatch p = bacaPaketPatch(paket);
        if (p == null || p.patchTeks == null) {
            h.galat = "Paket patch tidak berisi patch teks.";
            return h;
        }
        String shaLama = p.nilai.get("sha256-lama");
        String shaBaru = p.nilai.get("sha256-baru");
        if (shaLama != null && !shaLama.equalsIgnoreCase(sha256Berkas(berkasLama))) {
            h.galat = "SHA-256 berkas lama tidak cocok dengan manifest patch.";
            return h;
        }
        String lama = bacaTeks(berkasLama);
        if (lama == null) {
            h.galat = "Berkas lama tidak bisa dibaca sebagai teks.";
            return h;
        }
        String baru;
        try {
            baru = terapkanSelisih(lama, uraiPatch(new String(p.patchTeks, "UTF-8")));
        } catch (Throwable t) {
            h.galat = "Patch teks rusak: " + t.getMessage();
            return h;
        }
        if (shaBaru != null && !shaBaru.equalsIgnoreCase(sha256Hex(baru))) {
            h.galat = "SHA-256 hasil tidak cocok dengan manifest patch.";
            return h;
        }
        return tulisHasilAtomik(keluar, baru.getBytes(), shaBaru);
    }

    /**
     * Buat paket patch BINER yang ditandatangani: patch.bin (delta
     * biner) + MANIFEST.txt + TANDA-TANGAN.bin (RSA atas MANIFEST.txt)
     * + KUNCI-PUBLIK.der. Bekerja atas bita mentah (DEX/APK/berkas
     * apa pun). Mengembalikan berkas tujuan (null bila gagal).
     */
    public static File buatPatchBiner(File berkasLama, File berkasBaru,
                                      String namaRilis, PrivateKey privat,
                                      PublicKey publik, File keluar) {
        try {
            if (berkasLama == null || berkasBaru == null || privat == null
                    || publik == null || keluar == null) return null;
            byte[] lama = bacaBerkas(berkasLama);
            byte[] baru = bacaBerkas(berkasBaru);
            if (lama == null || baru == null) return null;
            byte[] delta = buatDelta(lama, baru);
            if (delta == null) return null;
            byte[] manifes = bytesManifes(namaRilis, berkasBaru.getName(),
                    sha256Hex(lama), sha256Hex(baru), baru.length,
                    "ukuran-delta: " + delta.length + "\n");
            byte[] tanda = tandaTangani(manifes, privat);
            if (tanda == null) return null;
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(keluar));
            try {
                tulisEntri(zip, "patch.bin", delta);
                tulisEntri(zip, "MANIFEST.txt", manifes);
                tulisEntri(zip, "TANDA-TANGAN.bin", tanda);
                tulisEntri(zip, "KUNCI-PUBLIK.der", publik.getEncoded());
            } finally {
                zip.close();
            }
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Terapkan paket patch BINER ke berkas lama (DEX/APK/berkas apa
     * pun): verifikasi tanda tangan dengan kunci tepercaya, periksa
     * SHA-256 berkas lama, terapkan delta biner, periksa SHA-256
     * hasil. Penulisan atomik lewat berkas sementara — bila gagal di
     * langkah mana pun, berkas lama tetap utuh (rollback).
     */
    public static HasilTerap terapkanPatchBiner(File berkasLama, File paket,
                                                PublicKey tepercaya, File keluar) {
        HasilTerap h = new HasilTerap();
        if (berkasLama == null || !berkasLama.isFile()) {
            h.galat = "Berkas lama tidak ditemukan.";
            return h;
        }
        if (keluar == null) {
            h.galat = "Berkas tujuan tidak ditentukan.";
            return h;
        }
        if (!verifikasiPaketPatch(paket, tepercaya)) {
            h.galat = "Tanda tangan patch tidak sah atau kunci tidak tepercaya.";
            return h;
        }
        PaketPatch p = bacaPaketPatch(paket);
        if (p == null || p.patchBiner == null) {
            h.galat = "Paket patch tidak berisi patch biner.";
            return h;
        }
        String shaLama = p.nilai.get("sha256-lama");
        String shaBaru = p.nilai.get("sha256-baru");
        if (shaLama != null && !shaLama.equalsIgnoreCase(sha256Berkas(berkasLama))) {
            h.galat = "SHA-256 berkas lama tidak cocok dengan manifest patch.";
            return h;
        }
        byte[] lama = bacaBerkas(berkasLama);
        if (lama == null) {
            h.galat = "Berkas lama tidak bisa dibaca.";
            return h;
        }
        byte[] baru;
        try {
            baru = terapkanDelta(lama, p.patchBiner);
        } catch (IllegalArgumentException e) {
            h.galat = e.getMessage();
            return h;
        }
        if (shaBaru != null && !shaBaru.equalsIgnoreCase(sha256Hex(baru))) {
            h.galat = "SHA-256 hasil tidak cocok dengan manifest patch.";
            return h;
        }
        return tulisHasilAtomik(keluar, baru, shaBaru);
    }

    /** Tulis hasil secara atomik (berkas sementara dulu, lalu ganti nama). */
    private static HasilTerap tulisHasilAtomik(File keluar, byte[] data, String shaBaru) {
        HasilTerap h = new HasilTerap();
        File sementara = new File(keluar.getPath() + ".sementara");
        if (!tulisBerkas(sementara, data)) {
            h.galat = "Hasil patch tidak bisa ditulis.";
            return h;
        }
        if (shaBaru != null && !shaBaru.equalsIgnoreCase(sha256Hex(data))) {
            sementara.delete();
            h.galat = "SHA-256 hasil tidak cocok dengan manifest patch.";
            return h;
        }
        if (keluar.exists() && !keluar.delete()) {
            sementara.delete();
            h.galat = "Berkas tujuan lama tidak bisa diganti.";
            return h;
        }
        if (!sementara.renameTo(keluar)) {
            sementara.delete();
            h.galat = "Hasil patch tidak bisa dipindah ke tujuan.";
            return h;
        }
        h.berhasil = true;
        h.ukuranBaru = data.length;
        h.sha256Baru = sha256Hex(data);
        return h;
    }

    // ------------------------------------------------------- server (unduh sungguhan)

    /**
     * Unduh patch dari peladen pengembang: GET
     * alamatDasar/namaRilis.zip (atau alamatDasar itu sendiri bila
     * sudah berupa URL berkas .zip). Alamat boleh http(s) maupun
     * file: (untuk menguji patch dari penyimpanan lokal). Kode HTTP
     * diperiksa bila memakai HTTP; panjang isi diperiksa bila peladen
     * menyebutkannya. Mengembalikan berkas tujuan (null bila gagal /
     * kode HTTP bukan 200 / ukuran tidak cocok).
     */
    public static File unduhPatch(String alamatDasar, String namaRilis, File tujuan) {
        try {
            if (alamatDasar == null || alamatDasar.trim().length() == 0 || tujuan == null) {
                return null;
            }
            String alamat = alamatDasar.trim();
            if (namaRilis != null && namaRilis.trim().length() > 0
                    && !alamat.endsWith(".zip")) {
                if (!alamat.endsWith("/")) alamat += "/";
                alamat += namaRilis.trim() + ".zip";
            }
            java.net.URLConnection koneksi = new URL(alamat).openConnection();
            long panjangHarapan = -1;
            if (koneksi instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) koneksi;
                http.setConnectTimeout(BATAS_SAMBUNG_MS);
                http.setReadTimeout(BATAS_BACA_MS);
                http.setInstanceFollowRedirects(true);
                http.setRequestProperty("User-Agent", "CreateAplikasi-OTA/5.8");
                int kode = http.getResponseCode();
                if (kode != HttpURLConnection.HTTP_OK) return null;
                panjangHarapan = http.getContentLengthLong();
            } else {
                panjangHarapan = koneksi.getContentLengthLong();
            }
            if (tujuan.getParentFile() != null) tujuan.getParentFile().mkdirs();
            File sementara = new File(tujuan.getPath() + ".unduh");
            FileOutputStream out = new FileOutputStream(sementara);
            long disalin = 0;
            try {
                InputStream in = koneksi.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    disalin += n;
                }
                in.close();
            } finally {
                out.close();
                if (koneksi instanceof HttpURLConnection) {
                    ((HttpURLConnection) koneksi).disconnect();
                }
            }
            if (panjangHarapan >= 0 && disalin != panjangHarapan) {
                sementara.delete();
                return null;
            }
            if (tujuan.exists()) tujuan.delete();
            if (!sementara.renameTo(tujuan)) {
                sementara.delete();
                return null;
            }
            return tujuan;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Baca daftar rilis dari peladen: GET alamatDasar/rilis.txt, satu
     * baris per rilis "nama|versi|sha256-patch". Alamat boleh http(s)
     * maupun file:. Daftar kosong bila peladen tidak bisa dihubungi
     * atau berkasnya tidak ada.
     */
    public static List<InfoRilis> bacaDaftarRilis(String alamatDasar) {
        List<InfoRilis> hasil = new ArrayList<InfoRilis>();
        try {
            if (alamatDasar == null || alamatDasar.trim().length() == 0) return hasil;
            String alamat = alamatDasar.trim();
            if (!alamat.endsWith("/")) alamat += "/";
            alamat += "rilis.txt";
            java.net.URLConnection koneksi = new URL(alamat).openConnection();
            if (koneksi instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) koneksi;
                http.setConnectTimeout(BATAS_SAMBUNG_MS);
                http.setReadTimeout(BATAS_BACA_MS);
                http.setInstanceFollowRedirects(true);
                http.setRequestProperty("User-Agent", "CreateAplikasi-OTA/5.8");
                if (http.getResponseCode() != HttpURLConnection.HTTP_OK) return hasil;
            }
            InputStream in = koneksi.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                in.close();
                if (koneksi instanceof HttpURLConnection) {
                    ((HttpURLConnection) koneksi).disconnect();
                }
            }
            String teks = new String(buf.toByteArray(), "UTF-8");
            for (String baris : teks.split("\n")) {
                String[] bagian = baris.split("\\|");
                if (bagian.length < 3) continue;
                InfoRilis r = new InfoRilis();
                r.nama = bagian[0].trim();
                r.versi = bagian[1].trim();
                r.sha256 = bagian[2].trim();
                if (r.nama.length() > 0) hasil.add(r);
            }
        } catch (Throwable t) {
            // Peladen tidak bisa dihubungi — kembalikan daftar kosong.
        }
        return hasil;
    }

    /** Teks rilis.txt untuk di-host pengembang di peladennya (satu baris per rilis). */
    public static String teksDaftarRilis(List<InfoRilis> daftar) {
        StringBuilder b = new StringBuilder();
        if (daftar == null) return "";
        for (int i = 0; i < daftar.size(); i++) {
            b.append(daftar.get(i).nama).append("|").append(daftar.get(i).versi)
                    .append("|").append(daftar.get(i).sha256).append("\n");
        }
        return b.toString();
    }

    /**
     * Unduh patch dari peladen lalu periksa SHA-256-nya terhadap yang
     * diharapkan (dari rilis.txt). Null bila unduhan gagal atau hash
     * tidak cocok.
     */
    public static File unduhDanPeriksa(String alamatDasar, String namaRilis,
                                       String sha256Harapan, File tujuan) {
        File hasil = unduhPatch(alamatDasar, namaRilis, tujuan);
        if (hasil == null) return null;
        if (sha256Harapan != null && sha256Harapan.length() > 0
                && !cocokSha256(hasil, sha256Harapan)) {
            hasil.delete();
            return null;
        }
        return hasil;
    }

    // ------------------------------------------------------- peluncuran bertahap (lama)

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

    /** Ringkasan patch biner: ukuran delta dibanding ukuran penuh. */
    public static String ringkasanBiner(long ukuranDelta, long ukuranBaru) {
        int hemat = ukuranBaru > 0
                ? (int) ((ukuranBaru - ukuranDelta) * 100 / ukuranBaru) : 0;
        return "Ukuran patch biner: " + ukuranDelta + " bita (hemat " + hemat
                + "% dari " + ukuranBaru + " bita berkas baru).";
    }
}
