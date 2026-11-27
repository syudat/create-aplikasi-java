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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.CRC32;
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
                    p.kirim(BangunLokal.PROGRESS, TeksUI.t("Membuat kunci RSA 2048 bit, mohon tunggu..."));
                    File folder = new File(dir);
                    if (new File(folder, "kunci.p12").exists()) throw new IOException(TeksUI.t("Tanda tangan ini sudah ada."));
                    folder.mkdirs();
                    byte[] p12 = buatP12(alias, sandi, cn, ou, o, kota, provinsi, negara, tahun);
                    BangunLokal.tulis(new File(folder, "kunci.p12"), p12);
                    String info = TeksUI.t("Nama samaran (alias): ") + alias + "\nNama lengkap (CN): " + cn
                            + "\nUnit organisasi (OU): " + ou + "\nOrganisasi (O): " + o + "\nKota (L): " + kota
                            + "\nProvinsi (ST): " + provinsi + "\nNegara (C): " + negara
                            + "\nMasa berlaku: " + tahun + " tahun\nKata sandi: (tidak disimpan di berkas ini)\n";
                    BangunLokal.tulis(new File(folder, "info.txt"), info.getBytes("UTF-8"));
                    p.kirim(BangunLokal.SUCCESS, new File(folder, "kunci.p12").getPath());
                } catch (Throwable e) {
                    p.kirim(BangunLokal.FAILED, TeksUI.t("Gagal membuat keystore: ") + e.getMessage());
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
        if (rdn.isEmpty()) throw new IOException(TeksUI.t("Isi minimal satu data identitas (misalnya nama lengkap)."));
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
        tandatangani(ctx, mentah, outDir, slug, pisah, p12, sandi, minSdk, false, null, l);
    }

    /**
     * Menandatangani APK mentah dengan pilihan 5.7.
     * - p12 kosong + dirProyek terisi: kunci bawaan UNIK milik proyek itu
     *   sendiri (dibuat sekali di folder tanda-tangan-bawaan di dalam
     *   proyek, lalu dipakai seterusnya), jadi tanda tangan bawaan
     *   berbeda-beda antarproyek, tidak lagi satu kunci debug bersama.
     * - p12 kosong + dirProyek kosong: kunci debug bersama lama.
     * - antiTamper true: catatan assets/perisai.dat (hash sertifikat +
     *   CRC32 dex) ditulis ke APK sebelum ditandatangani.
     */
    public static void tandatangani(final Context ctx, final String mentah, final String outDir, final String slug,
                                    final boolean pisah, final String p12, final String sandi, final int minSdk,
                                    final boolean antiTamper, final String dirProyek,
                                    final BangunLokal.Listener l) {
        final Penandatangan p = new Penandatangan(ctx, l);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrivateKey key;
                    X509Certificate cert;
                    if (p12 == null || p12.length() == 0) {
                        if (dirProyek != null && dirProyek.length() > 0) {
                            // Kunci bawaan unik per proyek (dibuat sekali, dipakai seterusnya).
                            File folder = new File(new File(dirProyek), "tanda-tangan-bawaan");
                            File berkasP12 = new File(folder, "kunci.p12");
                            File berkasSandi = new File(folder, "sandi.txt");
                            String sandiProyek;
                            if (berkasP12.isFile() && berkasSandi.isFile()) {
                                sandiProyek = new String(BangunLokal.baca(berkasSandi), "UTF-8").trim();
                            } else {
                                p.kirim(BangunLokal.PROGRESS,
                                        TeksUI.t("Membuat tanda tangan bawaan khusus proyek ini..."));
                                folder.mkdirs();
                                sandiProyek = sandiAcak();
                                BangunLokal.tulis(berkasP12, buatP12("bawaan", sandiProyek,
                                        TeksUI.t("Create Aplikasi"), "", "", "", "", "", 25));
                                BangunLokal.tulis(berkasSandi, sandiProyek.getBytes("UTF-8"));
                                BangunLokal.tulis(new File(folder, "info.txt"),
                                        ("Tanda tangan bawaan khusus proyek ini.\n"
                                                + TeksUI.t("Dibuat otomatis oleh Create Aplikasi supaya tanda tangan ")
                                                + "bawaan proyek ini berbeda dari proyek lain.\n"
                                                + TeksUI.t("Jangan hapus folder ini: tanpa kunci ini, APK baru ")
                                                + "tidak bisa menimpa APK lama proyek yang sama.\n")
                                                .getBytes("UTF-8"));
                            }
                            KeyStore ksP = KeyStore.getInstance("PKCS12");
                            FileInputStream inP = new FileInputStream(berkasP12);
                            try {
                                ksP.load(inP, sandiProyek.toCharArray());
                            } finally {
                                inP.close();
                            }
                            Enumeration<String> enP = ksP.aliases();
                            if (!enP.hasMoreElements()) throw new IOException(TeksUI.t("Kunci bawaan proyek kosong."));
                            String aliasP = enP.nextElement();
                            key = (PrivateKey) ksP.getKey(aliasP, sandiProyek.toCharArray());
                            cert = (X509Certificate) ksP.getCertificate(aliasP);
                            if (key == null || cert == null)
                                throw new IOException(TeksUI.t("Kunci bawaan proyek tidak bisa dibaca."));
                        } else {
                            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(p.aset("alat/debug.pk8")));
                            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                                    .generateCertificate(new ByteArrayInputStream(p.aset("alat/debug.der")));
                        }
                    } else {
                        KeyStore ks = KeyStore.getInstance("PKCS12");
                        FileInputStream in = new FileInputStream(p12);
                        try {
                            ks.load(in, sandi.toCharArray());
                        } catch (IOException e) {
                            throw new IOException(TeksUI.t("Kata sandi salah atau berkas keystore rusak."));
                        } finally {
                            in.close();
                        }
                        Enumeration<String> en = ks.aliases();
                        if (!en.hasMoreElements()) throw new IOException(TeksUI.t("Keystore kosong."));
                        String alias = en.nextElement();
                        key = (PrivateKey) ks.getKey(alias, sandi.toCharArray());
                        cert = (X509Certificate) ks.getCertificate(alias);
                        if (key == null || cert == null) throw new IOException(TeksUI.t("Kunci tidak ditemukan di keystore."));
                    }
                    File in = new File(mentah);
                    if (!in.isFile()) throw new IOException(TeksUI.t("APK mentah tidak ditemukan: ") + mentah);
                    File out = new File(outDir);
                    out.mkdirs();
                    // Perbaikan fase 3: pra-validasi pisah SEBELUM satu pun
                    // APK ditulis atau ditandatangani — bila satu varian
                    // kehilangan pustaka native-nya, build gagal cepat
                    // dengan pesan jelas dan tidak ada APK setengah jadi
                    // yang tertinggal di folder bin. Pemeriksaan di dalam
                    // satu() tetap ada sebagai jaring pengaman.
                    if (pisah) {
                        java.util.List<String> entriMentah =
                                new java.util.ArrayList<String>();
                        ZipFile zfAwal = new ZipFile(in);
                        try {
                            Enumeration<? extends ZipEntry> enAwal = zfAwal.entries();
                            while (enAwal.hasMoreElements()) {
                                ZipEntry eAwal = enAwal.nextElement();
                                if (!eAwal.isDirectory()) entriMentah.add(eAwal.getName());
                            }
                        } finally {
                            zfAwal.close();
                        }
                        String masalah64 = PisahAbi.periksaHasil(entriMentah,
                                PisahAbi.entriSetelahBuang(entriMentah,
                                        PisahAbi.abiYangDibuang(
                                                PisahAbi.namaBerkas(slug, PisahAbi.ABI_64))),
                                PisahAbi.ABI_64);
                        if (masalah64.length() > 0) throw new IOException(masalah64);
                        String masalah32 = PisahAbi.periksaHasil(entriMentah,
                                PisahAbi.entriSetelahBuang(entriMentah,
                                        PisahAbi.abiYangDibuang(
                                                PisahAbi.namaBerkas(slug, PisahAbi.ABI_32))),
                                PisahAbi.ABI_32);
                        if (masalah32.length() > 0) throw new IOException(masalah32);
                    }
                    List<String> hasil = new ArrayList<String>();
                    if (pisah) {
                        // Pemetaan ABI terpusat di PisahAbi (perbaikan 5.8):
                        // -64bit menyimpan arm64-v8a (membuang armeabi-v7a),
                        // -32bit menyimpan armeabi-v7a (membuang arm64-v8a).
                        String berkas64 = PisahAbi.namaBerkas(slug, PisahAbi.ABI_64);
                        String berkas32 = PisahAbi.namaBerkas(slug, PisahAbi.ABI_32);
                        p.kirim(BangunLokal.PROGRESS, "Menyusun APK 64 bit...");
                        hasil.add(p.satu(in, new File(out, berkas64), PisahAbi.abiYangDibuang(berkas64), key, cert, minSdk, antiTamper));
                        p.kirim(BangunLokal.PROGRESS, "Menyusun APK 32 bit...");
                        hasil.add(p.satu(in, new File(out, berkas32), PisahAbi.abiYangDibuang(berkas32), key, cert, minSdk, antiTamper));
                    } else {
                        p.kirim(BangunLokal.PROGRESS, "Menandatangani APK...");
                        hasil.add(p.satu(in, new File(out, slug + ".apk"), null, key, cert, minSdk, antiTamper));
                    }
                    in.delete();
                    StringBuilder sb = new StringBuilder();
                    for (String h : hasil) sb.append(sb.length() > 0 ? "\n" : "").append(h);
                    p.kirim(BangunLokal.SUCCESS, sb.toString());
                } catch (Throwable e) {
                    p.kirim(BangunLokal.FAILED, TeksUI.t("Gagal menandatangani: ") + e.getMessage());
                }
            }
        }, "Penandatangan").start();
    }

    /** Kata sandi acak untuk kunci bawaan proyek (huruf/angka tanpa yang mudah tertukar). */
    private static String sandiAcak() {
        String aksara = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder(20);
        for (int i = 0; i < 20; i++) b.append(aksara.charAt(r.nextInt(aksara.length())));
        return b.toString();
    }

    /** Salin APK tanpa tanda tangan lama dan tanpa pustaka ABI yang dibuang, lalu tandatangani. */
    private String satu(File in, File out, String buangAbi, PrivateKey key, X509Certificate cert, int minSdk,
                        boolean antiTamper)
            throws Exception {
        File tmp = new File(out.getPath() + ".tmp");
        ZipFile zf = new ZipFile(in);
        BangunLokal.Count cnt = new BangunLokal.Count(new java.io.BufferedOutputStream(new FileOutputStream(tmp), 65536));
        ZipOutputStream zos = new ZipOutputStream(cnt);
        Map<String, Long> crcDex = antiTamper ? new LinkedHashMap<String, Long>() : null;
        // Perbaikan 5.8: catat entri lib/ mentah dan hasil, supaya APK
        // pisah yang kehilangan semua pustaka native bisa ditolak
        // dengan pesan jelas (bukan diam-diam menjadi APK yang crash).
        java.util.List<String> entriMentah = new java.util.ArrayList<String>();
        java.util.List<String> entriHasil = new java.util.ArrayList<String>();
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                entriMentah.add(n);
                if (isTandaTangan(n)) continue;
                if (antiTamper && n.equals(Perisai.NAMA_ASET)) continue; // ditulis ulang di bawah
                if (PisahAbi.buangEntri(n, buangAbi)) continue;
                InputStream is = zf.getInputStream(e);
                byte[] data = BangunLokal.bacaStream(is);
                is.close();
                if (crcDex != null && Perisai.namaDex(n)) {
                    CRC32 c = new CRC32();
                    c.update(data);
                    crcDex.put(n, c.getValue());
                }
                BangunLokal.tambah(zos, cnt, n, data, e.getMethod() != ZipEntry.STORED);
                entriHasil.add(n);
            }
            if (antiTamper) {
                // Catatan perisai: hash sertifikat penandatangan + CRC dex.
                // Ditulis SEBELUM penandatanganan; sertifikat tidak berubah
                // oleh penandatanganan, jadi catatan tetap sah sesudahnya.
                String dataPerisai = Perisai.buatData(Perisai.sha256Hex(cert.getEncoded()), crcDex);
                BangunLokal.tambah(zos, cnt, Perisai.NAMA_ASET, dataPerisai.getBytes("UTF-8"), true);
            }
        } finally {
            zos.close();
            zf.close();
        }
        // Perbaikan 5.8: tolak APK pisah yang kehilangan pustaka native
        // ABI-nya (misalnya proyek hanya membawa .so arm64-v8a, lalu
        // APK -32bit dibuat tanpa pustaka apa pun dan crash saat dibuka).
        String masalahPisah = PisahAbi.periksaHasil(entriMentah, entriHasil,
                PisahAbi.abiYangDisimpan(out.getName()));
        if (masalahPisah.length() > 0) {
            tmp.delete();
            throw new IOException(masalahPisah);
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
