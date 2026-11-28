package com.androlua;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Identitas adb aplikasi ini: pasangan kunci RSA 2048 yang dipakai
 * untuk (a) otentikasi AUTH adb (tanda tangan token SHA-1 PKCS#1 v1.5),
 * (b) sertifikat klien TLS 1.3 untuk debugging nirkabel, dan (c) kunci
 * publik yang dikirim sebagai PeerInfo saat pairing.
 *
 * Format kunci publik adb (AOSP adb_auth_host.cpp): 524 bita —
 * modulus_size_words (64, LE u32), n0inv = -1/N[0] mod 2^32 (LE u32),
 * modulus sebagai 64 kata LE u32, rr = (2^2048)^2 mod N sebagai 64 kata
 * LE u32, eksponen (LE u32) — lalu Base64 + " nama". Kunci privat
 * disimpan sebagai DER PKCS#8 di berkas privat aplikasi (BUKAN format
 * PEM adbkey desktop; tidak perlu interoperabilitas dengan adb desktop).
 *
 * Sertifikat X.509 dibuat tangan dalam DER (tanpa BouncyCastle):
 * v1, CN=00, berlaku 10 tahun, sha256WithRSAEncryption. adbd tidak
 * memvalidasi rantai sertifikat (trust dibangun lewat pairing, bukan
 * CA) — yang dibandingkan adalah kunci publik di dalam sertifikat.
 *
 * Kelas murni (tanpa API Android) supaya bisa diuji di JVM, kecuali
 * bahanKunciTls() yang memakai refleksi ke sesi TLS (hanya jalan di
 * perangkat Android; di JVM melempar IOException yang jelas).
 */
public class AdbKunci {

    public static final int UKURAN_RSA = 2048;
    public static final String NAMA_KUNCI = "CreateAplikasi";
    private static final String BERKAS_KUNCI = "adbkey.p8";

    private final KeyPair pasangan;

    public AdbKunci(KeyPair pasangan) {
        this.pasangan = pasangan;
    }

    public KeyPair pasangan() { return pasangan; }
    public PublicKey publik() { return pasangan.getPublic(); }
    public PrivateKey privat() { return pasangan.getPrivate(); }

    /** Bangkitkan pasangan kunci RSA 2048 baru. */
    public static AdbKunci bangkitkan() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(UKURAN_RSA, new SecureRandom());
        return new AdbKunci(gen.generateKeyPair());
    }

    /**
     * Muat kunci dari folder privat aplikasi, atau bangkitkan + simpan
     * bila belum ada. Kunci yang sama dipakai seterusnya supaya adbd
     * tetap mengenali aplikasi ini setelah pairing.
     */
    public static AdbKunci muatAtauBuat(File folder) throws Exception {
        if (folder == null) throw new IllegalArgumentException("Folder null.");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Tidak bisa membuat folder kunci.");
        }
        File f = new File(folder, BERKAS_KUNCI);
        if (f.exists()) {
            byte[] der = bacaBerkas(f);
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
                // Kunci publik diturunkan dari privat (modulus + eksponen
                // publik tersimpan di dalam kunci privat CRT).
                RSAPrivateKey priv = (RSAPrivateKey) pk;
                BigInteger n = priv.getModulus();
                BigInteger e = BigInteger.valueOf(65537);
                if (pk instanceof java.security.interfaces.RSAPrivateCrtKey) {
                    e = ((java.security.interfaces.RSAPrivateCrtKey) pk).getPublicExponent();
                }
                PublicKey pub = kf.generatePublic(
                        new java.security.spec.RSAPublicKeySpec(n, e));
                return new AdbKunci(new KeyPair(pub, pk));
            } catch (Exception ex) {
                // Berkas rusak: bangkitkan baru (pairing lama ikut hilang;
                // pengguna memasangkan ulang — lebih baik daripada macet).
                return simpanBaru(f);
            }
        }
        return simpanBaru(f);
    }

    private static AdbKunci simpanBaru(File f) throws Exception {
        AdbKunci k = bangkitkan();
        tulisBerkas(f, k.privat().getEncoded());
        return k;
    }

    private static byte[] bacaBerkas(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally {
            try { in.close(); } catch (Throwable ignored) {
            }
        }
    }

    private static void tulisBerkas(File f, byte[] isi) throws IOException {
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(isi);
            out.flush();
        } finally {
            try { out.close(); } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Kunci publik dalam format kabel adb: Base64(524 bita) + " nama".
     * (AdbMessage menambahkan NUL akhir saat dikirim sebagai RSAPUBLICKEY.)
     */
    public String kunciPublikAdb() {
        return kunciPublikAdb(publik(), NAMA_KUNCI);
    }

    static String kunciPublikAdb(PublicKey pub, String nama) {
        RSAPublicKey rsa = (RSAPublicKey) pub;
        BigInteger n = rsa.getModulus();
        BigInteger e = rsa.getPublicExponent();
        byte[] kawat = new byte[524];
        tulisLE32(kawat, 0, 64); // modulus_size_words
        // n0inv = -1 / N[0] mod 2^32, N[0] = kata 32-bit pertama modulus (LE).
        long n0 = n.mod(BigInteger.ONE.shiftLeft(32)).longValue() & 0xFFFFFFFFL;
        long n0inv = (0x100000000L - modInvers32(n0)) & 0xFFFFFFFFL;
        tulisLE32(kawat, 4, (int) n0inv);
        // Modulus sebagai 64 kata LE u32 (256 bita little-endian).
        byte[] modBe = keBitaTetap(n, 256);
        for (int i = 0; i < 64; i++) {
            int w = ((modBe[255 - i * 4] & 0xFF))
                    | ((modBe[254 - i * 4] & 0xFF) << 8)
                    | ((modBe[253 - i * 4] & 0xFF) << 16)
                    | ((modBe[252 - i * 4] & 0xFF) << 24);
            tulisLE32(kawat, 8 + i * 4, w);
        }
        // rr = (2^2048)^2 mod N sebagai 64 kata LE u32.
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        byte[] rrBe = keBitaTetap(rr, 256);
        for (int i = 0; i < 64; i++) {
            int w = ((rrBe[255 - i * 4] & 0xFF))
                    | ((rrBe[254 - i * 4] & 0xFF) << 8)
                    | ((rrBe[253 - i * 4] & 0xFF) << 16)
                    | ((rrBe[252 - i * 4] & 0xFF) << 24);
            tulisLE32(kawat, 264 + i * 4, w);
        }
        tulisLE32(kawat, 520, e.intValue()); // eksponen
        return base64(kawat) + " " + nama;
    }

    /** Invers perkalian modulo 2^32 (untuk n0 ganjil). */
    static long modInvers32(long a) {
        // Newton: x_{n+1} = x_n * (2 - a*x_n) mod 2^32, 5 iterasi cukup.
        long x = 1;
        for (int i = 0; i < 5; i++) x = x * (2 - a * x);
        return x & 0xFFFFFFFFL;
    }

    private static void tulisLE32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static byte[] keBitaTetap(BigInteger v, int panjang) {
        byte[] be = v.toByteArray();
        byte[] keluar = new byte[panjang];
        int n = Math.min(panjang, be.length);
        System.arraycopy(be, be.length - n, keluar, panjang - n, n);
        return keluar;
    }

    /** Base64 standar (dengan padding, tanpa jeda baris). */
    static String base64(byte[] isi) {
        final String abjad = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < isi.length; i += 3) {
            int b0 = isi[i] & 0xFF;
            int b1 = (i + 1 < isi.length) ? (isi[i + 1] & 0xFF) : 0;
            int b2 = (i + 2 < isi.length) ? (isi[i + 2] & 0xFF) : 0;
            s.append(abjad.charAt(b0 >> 2));
            s.append(abjad.charAt(((b0 & 0x03) << 4) | (b1 >> 4)));
            s.append(i + 1 < isi.length ? abjad.charAt(((b1 & 0x0F) << 2) | (b2 >> 6)) : '=');
            s.append(i + 2 < isi.length ? abjad.charAt(b2 & 0x3F) : '=');
        }
        return s.toString();
    }

    /**
     * Tanda tangani token AUTH adb (20 bita) dengan SHA1withRSA —
     * hasilnya sama persis dengan konstruksi manual PKCS#1 v1.5
     * (DigestInfo SHA-1) yang dipakai Shizuku: 00 01 FF.. 00 +
     * DigestInfo + hash, dienkripsi kunci privat.
     */
    public byte[] tanda(byte[] token) throws Exception {
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(privat());
        sig.update(token);
        return sig.sign();
    }

    /** Sertifikat X.509 v1 self-signed (DER) untuk TLS klien adb. */
    public byte[] sertifikatDer() throws Exception {
        RSAPublicKey rsa = (RSAPublicKey) publik();
        byte[] spki = spki(rsa);
        byte[] nama = namaX500("00");
        Calendar cal = Calendar.getInstance();
        Date mulai = cal.getTime();
        cal.add(Calendar.YEAR, 10);
        Date akhir = cal.getTime();
        ByteArrayOutputStream tbs = new ByteArrayOutputStream();
        // serial INTEGER 1
        tbs.write(derInteger(BigInteger.ONE));
        // signatureAlgorithm sha256WithRSAEncryption
        tbs.write(derAlgoritmaSha256Rsa());
        // issuer + subject (CN=00)
        tbs.write(nama);
        // validity: UTCTime
        ByteArrayOutputStream val = new ByteArrayOutputStream();
        val.write(derUtc(mulai));
        val.write(derUtc(akhir));
        tbs.write(derUrutan(val.toByteArray()));
        tbs.write(nama);
        tbs.write(spki);
        byte[] tbsDer = derUrutan(tbs.toByteArray());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privat());
        sig.update(tbsDer);
        byte[] tanda = sig.sign();
        ByteArrayOutputStream sert = new ByteArrayOutputStream();
        sert.write(tbsDer);
        sert.write(derAlgoritmaSha256Rsa());
        sert.write(derBitString(tanda));
        return derUrutan(sert.toByteArray());
    }

    public X509Certificate sertifikat() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(sertifikatDer()));
    }

    /**
     * SSLContext TLS untuk adb: kunci klien dari sertifikat di atas,
     * TrustManager yang menerima sertifikat apa pun (kepercayaan
     * dibangun lewat pairing, bukan CA — sama seperti Shizuku/AOSP).
     */
    public SSLContext konteksSsl() throws Exception {
        X509Certificate sert = sertifikat();
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("adb", privat(), new char[0],
                new java.security.cert.Certificate[]{sert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        TrustManager[] percayaSemua = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String t) {
            }
            public void checkServerTrusted(X509Certificate[] c, String t) {
            }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), percayaSemua, new SecureRandom());
        return ctx;
    }

    /**
     * Ekspor 64 bita bahan kunci TLS (label "adb-label\0") dari sesi
     * TLS yang sudah berjabat tangan — dipakai sandi SPAKE2 pairing.
     * Di Android, Conscrypt menyediakan metode tersembunyi
     * exportKeyingMaterial pada sesi; di JVM metode itu tidak ada dan
     * dilempar IOException yang jelas (pairing hanya bisa di perangkat).
     */
    public static byte[] bahanKunciTls(SSLSession sesi) throws IOException {
        if (sesi == null) throw new IOException("Sesi TLS null.");
        try {
            Method m = sesi.getClass().getMethod("exportKeyingMaterial",
                    String.class, byte[].class, int.class);
            Object hasil = m.invoke(sesi, "adb-label\0", null, Integer.valueOf(64));
            if (hasil instanceof byte[]) {
                byte[] b = (byte[]) hasil;
                if (b.length == 64) return b;
            }
            throw new IOException("Bahan kunci TLS kosong.");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Perangkat ini tidak mengizinkan ekspor bahan kunci TLS "
                    + "untuk pairing (" + e.getClass().getSimpleName() + ").", e);
        }
    }

    // ==================== Pembangun DER minimal ====================

    private static byte[] derPanjang(int n) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        if (n < 128) {
            b.write(n);
        } else {
            int tmp = n;
            int jumlah = 0;
            while (tmp > 0) { jumlah++; tmp >>= 8; }
            b.write(0x80 | jumlah);
            for (int i = jumlah - 1; i >= 0; i--) b.write((n >> (i * 8)) & 0xFF);
        }
        return b.toByteArray();
    }

    private static byte[] derTlv(int tag, byte[] isi) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(tag);
        b.write(derPanjang(isi.length));
        b.write(isi);
        return b.toByteArray();
    }

    private static byte[] derUrutan(byte[] isi) throws IOException {
        return derTlv(0x30, isi);
    }

    private static byte[] derSet(byte[] isi) throws IOException {
        return derTlv(0x31, isi);
    }

    private static byte[] derInteger(BigInteger v) throws IOException {
        byte[] be = v.toByteArray();
        return derTlv(0x02, be);
    }

    private static byte[] derOid(String oid) throws IOException {
        String[] bagian = oid.split("\\.");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(Integer.parseInt(bagian[0]) * 40 + Integer.parseInt(bagian[1]));
        for (int i = 2; i < bagian.length; i++) {
            long v = Long.parseLong(bagian[i]);
            if (v < 128) {
                b.write((int) v);
            } else {
                byte[] tmp = new byte[10];
                int n = 0;
                while (v > 0) { tmp[n++] = (byte) (v & 0x7F); v >>= 7; }
                for (int j = n - 1; j >= 0; j--) {
                    b.write(tmp[j] | (j > 0 ? 0x80 : 0));
                }
            }
        }
        return derTlv(0x06, b.toByteArray());
    }

    private static byte[] derAlgoritmaSha256Rsa() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(derOid("1.2.840.113549.1.1.11"));
        b.write(derTlv(0x05, new byte[0])); // NULL
        return derUrutan(b.toByteArray());
    }

    private static byte[] derAlgoritmaRsa() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(derOid("1.2.840.113549.1.1.1"));
        b.write(derTlv(0x05, new byte[0]));
        return derUrutan(b.toByteArray());
    }

    private static byte[] derUtf8(String s) throws IOException {
        return derTlv(0x0C, s.getBytes("UTF-8"));
    }

    /** Nama X.500 dengan satu RDN CN=<cn>. */
    private static byte[] namaX500(String cn) throws IOException {
        ByteArrayOutputStream rdn = new ByteArrayOutputStream();
        rdn.write(derOid("2.5.4.3")); // commonName
        rdn.write(derUtf8(cn));
        return derUrutan(derSet(derUrutan(rdn.toByteArray())));
    }

    private static byte[] derUtc(Date d) throws IOException {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return derTlv(0x17, f.format(d).getBytes("ISO-8859-1"));
    }

    private static byte[] derBitString(byte[] isi) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0); // jumlah bita tak terpakai
        b.write(isi);
        return derTlv(0x03, b.toByteArray());
    }

    /** SubjectPublicKeyInfo RSA dari kunci publik. */
    private static byte[] spki(RSAPublicKey rsa) throws IOException {
        ByteArrayOutputStream kunci = new ByteArrayOutputStream();
        kunci.write(derInteger(rsa.getModulus()));
        kunci.write(derInteger(rsa.getPublicExponent()));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(derAlgoritmaRsa());
        b.write(derBitString(derUrutan(kunci.toByteArray())));
        return derUrutan(b.toByteArray());
    }
}
