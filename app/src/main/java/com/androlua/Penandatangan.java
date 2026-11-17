package com.androlua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.apksig.ApkSigner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Keystore (kunci tanda tangan) dan penandatanganan APK.
 * - buatKeystore: membuat pasangan kunci RSA + sertifikat X.509 sendiri, disimpan sebagai PKCS12.
 * - tandatangani: memisah arsitektur (opsional) lalu menandatangani APK dengan kunci pribadi atau kunci bawaan.
 */
public class Penandatangan {
    private final Context ctx;
    private final BangunLokal.Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Penandatangan(Context ctx, BangunLokal.Listener l) {
        this.ctx = ctx.getApplicationContext();
        this.listener = l;
    }

    private void kirim(final int type, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                listener.onEvent(type, msg);
            }
        });
    }

    // ------------------------------------------------------------------ keystore

    /** Membuat keystore PKCS12 di folder dir (berkas: kunci.p12 dan info.txt). */
    public static void buatKeystore(final Context ctx, final String dir, final String alias, final String sandi,
                                    final String cn, final String ou, final String o, final String kota,
                                    final String provinsi, final String negara, final int tahun,
                                    final BangunLokal.Listener l) {
        final Penandatangan p = new Penandatangan(ctx, l);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    p.kirim(BangunLokal.PROGRESS, "Membuat kunci RSA 2048 bit, mohon tunggu...");
                    File folder = new File(dir);
                    if (new File(folder, "kunci.p12").exists()) throw new IOException("Tanda tangan ini sudah ada.");
                    folder.mkdirs();
                    byte[] p12 = buatP12(alias, sandi, cn, ou, o, kota, provinsi, negara, tahun);
                    BangunLokal.tulis(new File(folder, "kunci.p12"), p12);
                    String info = "Nama samaran (alias): " + alias + "\nNama lengkap (CN): " + cn
                            + "\nUnit organisasi (OU): " + ou + "\nOrganisasi (O): " + o + "\nKota (L): " + kota
                            + "\nProvinsi (ST): " + provinsi + "\nNegara (C): " + negara
                            + "\nMasa berlaku: " + tahun + " tahun\nKata sandi: (tidak disimpan di berkas ini)\n";
                    BangunLokal.tulis(new File(folder, "info.txt"), info.getBytes("UTF-8"));
                    p.kirim(BangunLokal.SUCCESS, new File(folder, "kunci.p12").getPath());
                } catch (Throwable e) {
                    p.kirim(BangunLokal.FAILED, "Gagal membuat keystore: " + e.getMessage());
                }
            }
        }, "BuatKeystore").start();
    }

    /** Bagian yang dapat diuji di komputer (tanpa Context). */
    public static byte[] buatP12(String alias, String sandi, String cn, String ou, String o, String kota,
                                 String provinsi, String negara, int tahun) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();
        X509Certificate cert = buatSertifikat(kp, cn, ou, o, kota, provinsi, negara, tahun);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, kp.getPrivate(), sandi.toCharArray(), new Certificate[]{cert});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, sandi.toCharArray());
        return out.toByteArray();
    }

    // --- pembuat sertifikat X.509 (DER) tanpa pustaka tambahan ---

    private static byte[] tlv(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] p : parts) body.write(p, 0, p.length);
        byte[] b = body.toByteArray();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        if (b.length < 128) {
            o.write(b.length);
        } else if (b.length < 256) {
            o.write(0x81);
            o.write(b.length);
        } else if (b.length < 65536) {
            o.write(0x82);
            o.write(b.length >> 8);
            o.write(b.length);
        } else {
            o.write(0x83);
            o.write(b.length >> 16);
            o.write(b.length >> 8);
            o.write(b.length);
        }
        o.write(b, 0, b.length);
        return o.toByteArray();
    }

    private static byte[] oid(int... a) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(a[0] * 40 + a[1]);
        for (int i = 2; i < a.length; i++) {
            int v = a[i];
            if (v < 128) {
                o.write(v);
            } else {
                List<Integer> parts = new ArrayList<Integer>();
                while (v > 0) {
                    parts.add(0, v & 0x7f);
                    v >>= 7;
                }
                for (int j = 0; j < parts.size(); j++) o.write(parts.get(j) | (j < parts.size() - 1 ? 0x80 : 0));
            }
        }
        return tlv(0x06, o.toByteArray());
    }

    private static byte[] nama(String cn, String ou, String o, String l, String st, String c) throws Exception {
        List<byte[]> rdn = new ArrayList<byte[]>();
        String[] oidNames = {"C", "ST", "L", "O", "OU", "CN"};
        int[][] oids = {{2, 5, 4, 6}, {2, 5, 4, 8}, {2, 5, 4, 7}, {2, 5, 4, 10}, {2, 5, 4, 11}, {2, 5, 4, 3}};
        String[] vals = {c, st, l, o, ou, cn};
        for (int i = 0; i < vals.length; i++) {
            String v = vals[i] == null ? "" : vals[i].trim();
            if (v.length() == 0) continue;
            byte[] str = oidNames[i].equals("C")
                    ? tlv(0x13, v.getBytes("US-ASCII"))      // PrintableString
                    : tlv(0x0c, v.getBytes("UTF-8"));        // UTF8String
            rdn.add(tlv(0x31, tlv(0x30, oid(oids[i]), str)));
        }
        if (rdn.isEmpty()) throw new IOException("Isi minimal satu data identitas (misalnya nama lengkap).");
        return tlv(0x30, rdn.toArray(new byte[0][]));
    }

    private static byte[] waktu(Date d) throws Exception {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTime(d);
        boolean utc = c.get(Calendar.YEAR) < 2050;
        SimpleDateFormat f = new SimpleDateFormat(utc ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(utc ? 0x17 : 0x18, f.format(d).getBytes("US-ASCII"));
    }

    private static X509Certificate buatSertifikat(KeyPair kp, String cn, String ou, String o, String kota,
                                                  String provinsi, String negara, int tahun) throws Exception {
        if (tahun < 1) tahun = 1;
        if (tahun > 100) tahun = 100;
        byte[] algoritma = tlv(0x30, oid(1, 2, 840, 113549, 1, 1, 11), tlv(0x05)); // sha256WithRSAEncryption
        byte[] nm = nama(cn, ou, o, kota, provinsi, negara);
        byte[] serial = new byte[8];
        new SecureRandom().nextBytes(serial);
        serial[0] &= 0x7f;
        serial[0] |= 0x40;
        Calendar awal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        awal.add(Calendar.MINUTE, -5);
        Calendar akhir = (Calendar) awal.clone();
        akhir.add(Calendar.YEAR, tahun);
        byte[] tbs = tlv(0x30,
                tlv(0xa0, tlv(0x02, new byte[]{2})),                 // versi 3
                tlv(0x02, serial),
                algoritma,
                nm,
                tlv(0x30, waktu(awal.getTime()), waktu(akhir.getTime())),
                nm,
                kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        byte[] der = tlv(0x30, tbs, algoritma, tlv(0x03, bits));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    // ------------------------------------------------------------------ tanda tangan APK

    /**
     * Menandatangani APK mentah. p12 kosong = kunci bawaan aplikasi.
     * pisah = true -> dua APK (64 bit dan 32 bit), false -> satu APK berisi keduanya.
     * Pesan sukses: daftar jalur APK, satu per baris.
     */
    public static void tandatangani(final Context ctx, final String mentah, final String outDir, final String slug,
                                    final boolean pisah, final String p12, final String sandi, final int minSdk,
                                    final BangunLokal.Listener l) {
        final Penandatangan p = new Penandatangan(ctx, l);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrivateKey key;
                    X509Certificate cert;
                    if (p12 == null || p12.length() == 0) {
                        key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(p.aset("alat/debug.pk8")));
                        cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                                .generateCertificate(new ByteArrayInputStream(p.aset("alat/debug.der")));
                    } else {
                        KeyStore ks = KeyStore.getInstance("PKCS12");
                        FileInputStream in = new FileInputStream(p12);
                        try {
                            ks.load(in, sandi.toCharArray());
                        } catch (IOException e) {
                            throw new IOException("Kata sandi salah atau berkas keystore rusak.");
                        } finally {
                            in.close();
                        }
                        Enumeration<String> en = ks.aliases();
                        if (!en.hasMoreElements()) throw new IOException("Keystore kosong.");
                        String alias = en.nextElement();
                        key = (PrivateKey) ks.getKey(alias, sandi.toCharArray());
                        cert = (X509Certificate) ks.getCertificate(alias);
                        if (key == null || cert == null) throw new IOException("Kunci tidak ditemukan di keystore.");
                    }
                    File in = new File(mentah);
                    if (!in.isFile()) throw new IOException("APK mentah tidak ditemukan: " + mentah);
                    File out = new File(outDir);
                    out.mkdirs();
                    List<String> hasil = new ArrayList<String>();
                    if (pisah) {
                        p.kirim(BangunLokal.PROGRESS, "Menyusun APK 64 bit...");
                        hasil.add(p.satu(in, new File(out, slug + "-64bit.apk"), "armeabi-v7a", key, cert, minSdk));
                        p.kirim(BangunLokal.PROGRESS, "Menyusun APK 32 bit...");
                        hasil.add(p.satu(in, new File(out, slug + "-32bit.apk"), "arm64-v8a", key, cert, minSdk));
                    } else {
                        p.kirim(BangunLokal.PROGRESS, "Menandatangani APK...");
                        hasil.add(p.satu(in, new File(out, slug + ".apk"), null, key, cert, minSdk));
                    }
                    in.delete();
                    StringBuilder sb = new StringBuilder();
                    for (String h : hasil) sb.append(sb.length() > 0 ? "\n" : "").append(h);
                    p.kirim(BangunLokal.SUCCESS, sb.toString());
                } catch (Throwable e) {
                    p.kirim(BangunLokal.FAILED, "Gagal menandatangani: " + e.getMessage());
                }
            }
        }, "Penandatangan").start();
    }

    /** Salin APK tanpa tanda tangan lama dan tanpa pustaka ABI yang dibuang, lalu tandatangani. */
    private String satu(File in, File out, String buangAbi, PrivateKey key, X509Certificate cert, int minSdk)
            throws Exception {
        File tmp = new File(out.getPath() + ".tmp");
        ZipFile zf = new ZipFile(in);
        BangunLokal.Count cnt = new BangunLokal.Count(new java.io.BufferedOutputStream(new FileOutputStream(tmp), 65536));
        ZipOutputStream zos = new ZipOutputStream(cnt);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (isTandaTangan(n)) continue;
                if (buangAbi != null && n.startsWith("lib/" + buangAbi + "/")) continue;
                InputStream is = zf.getInputStream(e);
                byte[] data = BangunLokal.bacaStream(is);
                is.close();
                BangunLokal.tambah(zos, cnt, n, data, e.getMethod() != ZipEntry.STORED);
            }
        } finally {
            zos.close();
            zf.close();
        }
        if (out.exists()) out.delete();
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("pembuat", key,
                Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(tmp)
                .setOutputApk(out)
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build()
                .sign();
        tmp.delete();
        return out.getPath();
    }

    private static boolean isTandaTangan(String n) {
        if (!n.startsWith("META-INF/")) return false;
        String u = n.toUpperCase(Locale.US);
        return u.equals("META-INF/MANIFEST.MF") || u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA")
                || u.endsWith(".EC");
    }

    private byte[] aset(String name) throws IOException {
        InputStream in = ctx.getAssets().open(name);
        try {
            return BangunLokal.bacaStream(in);
        } finally {
            in.close();
        }
    }
}
