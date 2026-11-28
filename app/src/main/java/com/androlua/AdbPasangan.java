package com.androlua;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pairing debugging nirkabel adb (Android 11+) murni-Java, ditulis
 * dari AOSP pairing_connection.cpp / pairing_auth.cpp / aes_128_gcm.cpp
 * (lisensi Apache-2.0) — bukan salinan kode Kadb/Shizuku.
 *
 * Alur (klien = Alice):
 * 1. TLS 1.3 ke port pairing (dibuka pemanggil lewat AdbKunci).
 * 2. Sandi = bita kode pairing (6 digit, UTF-8) + 64 bita bahan kunci
 *    TLS (label "adb-label\0", dari AdbKunci.bahanKunciTls).
 * 3. Tukar pesan SPAKE2 (paket 6 bita: versi=1, tipe, panjang BE u32;
 *    tipe 0 = SPAKE2_MSG, tipe 1 = PEER_INFO).
 * 4. Turunkan kunci AES dari bahan kunci SPAKE2 (HKDF-SHA256, salt
 *    null, info "adb pairing_auth aes-128-gcm key", 16 bita).
 * 5. Tukar PeerInfo terenkripsi AES-128-GCM: nonce 12 bita (8 bita
 *    penghitung little-endian + 4 nol), penghitung tiap arah mulai 0.
 *    PeerInfo 8192 bita: bita 0 = tipe 0 (RSA), sisanya baris kunci
 *    publik adb (Base64 + " nama", NUL, sisanya nol).
 * 6. Bila dekripsi PeerInfo lawan gagal, sandi salah.
 *
 * Kelas murni (tanpa API Android) supaya bisa diuji di JVM: pemanggil
 * menyediakan aliran TLS + bahan kunci TLS.
 */
public class AdbPasangan {

    public static final int VERSI_PAKET = 1;
    public static final int TIPE_SPAKE2 = 0;
    public static final int TIPE_PEER_INFO = 1;
    public static final int UKURAN_PEER_INFO = 8192;
    public static final String INFO_HKDF = "adb pairing_auth aes-128-gcm key";

    /** Hasil pairing: kunci publik RSA lawan (baris adb) bila ada. */
    public static class Hasil {
        public String kunciPublikLawan = "";
    }

    /** Satu paket pairing (header 6 bita + muatan). */
    static class Paket {
        int tipe;
        byte[] muatan;
    }

    /**
     * Pairing penuh lewat TLS langsung ke port pairing adbd
     * (host:port layanan _adb-tls-pairing._tcp): buka SSLSocket TLS 1.3,
     * ambil bahan kunci TLS (exportKeyingMaterial "adb-label"), lalu
     * jalankan pasangkan() di atas aliran itu. Soket ditutup setelahnya;
     * yang tersimpan adalah kunci RSA di AdbKunci (adbd mengenalinya).
     */
    public static Hasil pasangkanTls(String host, int port, AdbKunci kunci,
                                     String kode, int timeoutMs) throws IOException {
        if (host == null || host.length() == 0) host = "127.0.0.1";
        javax.net.ssl.SSLSocket ssl = null;
        try {
            javax.net.ssl.SSLSocketFactory pabrik =
                    kunci.konteksSsl().getSocketFactory();
            java.net.Socket polos = new java.net.Socket();
            polos.connect(new java.net.InetSocketAddress(host, port),
                    timeoutMs > 0 ? timeoutMs : 5000);
            polos.setSoTimeout(timeoutMs > 0 ? timeoutMs : 15000);
            ssl = (javax.net.ssl.SSLSocket) pabrik.createSocket(polos, host, port, true);
            ssl.setEnabledProtocols(new String[]{"TLSv1.3"});
            ssl.startHandshake();
            byte[] bahan = AdbKunci.bahanKunciTls(ssl.getSession());
            return pasangkan(ssl.getInputStream(), ssl.getOutputStream(),
                    kunci, kode, bahan);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Gagal menyiapkan TLS pairing (" + e.getMessage() + ").", e);
        } finally {
            if (ssl != null) {
                try {
                    ssl.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Jalankan pairing penuh di atas aliran TLS yang sudah berjabat
     * tangan. bahanKunciTls = 64 bita dari AdbKunci.bahanKunciTls.
     * Mengembalikan kunci publik lawan; melempar IOException bila
     * kode salah atau lawan menutup.
     */
    public static Hasil pasangkan(InputStream masuk, OutputStream keluar,
                                  AdbKunci kunci, String kode, byte[] bahanKunciTls)
            throws IOException {
        if (kode == null || kode.trim().length() == 0) {
            throw new IOException("Kode pairing kosong.");
        }
        if (bahanKunciTls == null || bahanKunciTls.length != 64) {
            throw new IOException("Bahan kunci TLS harus 64 bita.");
        }
        if (kunci == null) throw new IOException("Kunci adb null.");
        byte[] kodeBita;
        byte[] sandi;
        try {
            kodeBita = kode.trim().getBytes("UTF-8");
            sandi = new byte[kodeBita.length + 64];
            System.arraycopy(kodeBita, 0, sandi, 0, kodeBita.length);
            System.arraycopy(bahanKunciTls, 0, sandi, kodeBita.length, 64);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Gagal menyiapkan sandi pairing.", e);
        }
        Spake2 spake;
        byte[] pesanSaya;
        byte[] bahanKunci;
        try {
            spake = new Spake2(Spake2.ALICE, Spake2.NAMA_KLIEN, Spake2.NAMA_SERVER, sandi);
            pesanSaya = spake.buatPesan();
        } catch (Exception e) {
            throw new IOException("Gagal membuat pesan SPAKE2 (" + e.getMessage() + ").", e);
        }
        tulisPaket(keluar, TIPE_SPAKE2, pesanSaya);
        Paket balas = bacaPaket(masuk);
        if (balas.tipe != TIPE_SPAKE2 || balas.muatan.length != 32) {
            throw new IOException("Jawaban SPAKE2 lawan tidak sah.");
        }
        try {
            bahanKunci = spake.prosesPesan(balas.muatan);
        } catch (Exception e) {
            throw new IOException("Gagal memproses pesan SPAKE2 lawan (" + e.getMessage() + ").", e);
        }
        byte[] kunciAes;
        try {
            kunciAes = turunkanKunciAes(bahanKunci);
        } catch (Exception e) {
            throw new IOException("Gagal menurunkan kunci AES pairing.", e);
        }
        // PeerInfo kita: tipe 0 + baris kunci publik adb.
        byte[] infoSaya = bangunPeerInfo(kunci.kunciPublikAdb());
        byte[] infoSayaEnk;
        try {
            infoSayaEnk = enkripsiAes(kunciAes, infoSaya, 0);
        } catch (Exception e) {
            throw new IOException("Gagal mengenkripsi PeerInfo.", e);
        }
        tulisPaket(keluar, TIPE_PEER_INFO, infoSayaEnk);
        Paket balasInfo = bacaPaket(masuk);
        if (balasInfo.tipe != TIPE_PEER_INFO) {
            throw new IOException("Jawaban PeerInfo lawan tidak sah.");
        }
        byte[] infoLawan;
        try {
            infoLawan = dekripsiAes(kunciAes, balasInfo.muatan, 0);
        } catch (Exception e) {
            throw new IOException("Kode pairing salah atau PeerInfo lawan rusak.", e);
        }
        if (infoLawan.length != UKURAN_PEER_INFO || infoLawan[0] != 0) {
            throw new IOException("PeerInfo lawan tidak sah.");
        }
        Hasil hasil = new Hasil();
        hasil.kunciPublikLawan = bacaStringNul(infoLawan, 1);
        return hasil;
    }

    /** Bangun PeerInfo 8192 bita dari baris kunci publik adb. */
    static byte[] bangunPeerInfo(String kunciPublik) throws IOException {
        byte[] info = new byte[UKURAN_PEER_INFO];
        info[0] = 0; // tipe RSA
        byte[] baris = kunciPublik.getBytes("UTF-8");
        int n = Math.min(baris.length, UKURAN_PEER_INFO - 1);
        System.arraycopy(baris, 0, info, 1, n);
        return info;
    }

    /** Baca string NUL-terminated dari buf mulai offset (sisanya nol). */
    static String bacaStringNul(byte[] buf, int off) throws IOException {
        int akhir = off;
        while (akhir < buf.length && buf[akhir] != 0) akhir++;
        return new String(buf, off, akhir - off, "UTF-8");
    }

    /** HKDF-SHA256 (salt nol, info tetap) -> kunci AES 16 bita. */
    static byte[] turunkanKunciAes(byte[] bahanKunci) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] garam = new byte[32]; // salt null = 32 nol (RFC 5869)
        mac.init(new SecretKeySpec(garam, "HmacSHA256"));
        byte[] prk = mac.doFinal(bahanKunci);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(INFO_HKDF.getBytes("UTF-8"));
        mac.update((byte) 1);
        byte[] okm = mac.doFinal();
        byte[] kunci = new byte[16];
        System.arraycopy(okm, 0, kunci, 0, 16);
        return kunci;
    }

    /** AES-128-GCM: nonce = penghitung LE 8 bita + 4 nol. */
    static byte[] enkripsiAes(byte[] kunciAes, byte[] data, long penghitung) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kunciAes, "AES"),
                new GCMParameterSpec(128, nonce(penghitung)));
        return c.doFinal(data);
    }

    static byte[] dekripsiAes(byte[] kunciAes, byte[] data, long penghitung) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kunciAes, "AES"),
                new GCMParameterSpec(128, nonce(penghitung)));
        return c.doFinal(data);
    }

    static byte[] nonce(long penghitung) {
        byte[] n = new byte[12];
        for (int i = 0; i < 8; i++) n[i] = (byte) ((penghitung >> (i * 8)) & 0xFF);
        return n;
    }

    /** Tulis paket pairing: versi(1) + tipe(1) + panjang BE u32 + muatan. */
    static void tulisPaket(OutputStream keluar, int tipe, byte[] muatan) throws IOException {
        byte[] kepala = new byte[6];
        kepala[0] = (byte) VERSI_PAKET;
        kepala[1] = (byte) tipe;
        kepala[2] = (byte) ((muatan.length >> 24) & 0xFF);
        kepala[3] = (byte) ((muatan.length >> 16) & 0xFF);
        kepala[4] = (byte) ((muatan.length >> 8) & 0xFF);
        kepala[5] = (byte) (muatan.length & 0xFF);
        keluar.write(kepala);
        keluar.write(muatan);
        keluar.flush();
    }

    /** Baca satu paket pairing (blokir sampai lengkap). */
    static Paket bacaPaket(InputStream masuk) throws IOException {
        byte[] kepala = new byte[6];
        bacaTepat(masuk, kepala);
        if (kepala[0] != VERSI_PAKET) {
            throw new IOException("Versi paket pairing tidak didukung: " + (kepala[0] & 0xFF) + ".");
        }
        int tipe = kepala[1] & 0xFF;
        if (tipe != TIPE_SPAKE2 && tipe != TIPE_PEER_INFO) {
            throw new IOException("Tipe paket pairing tidak dikenal: " + tipe + ".");
        }
        int panjang = ((kepala[2] & 0xFF) << 24) | ((kepala[3] & 0xFF) << 16)
                | ((kepala[4] & 0xFF) << 8) | (kepala[5] & 0xFF);
        if (panjang <= 0 || panjang > 2 * UKURAN_PEER_INFO) {
            throw new IOException("Panjang paket pairing tidak sah: " + panjang + ".");
        }
        Paket p = new Paket();
        p.tipe = tipe;
        p.muatan = new byte[panjang];
        bacaTepat(masuk, p.muatan);
        return p;
    }

    private static void bacaTepat(InputStream in, byte[] buf) throws IOException {
        int baca = 0;
        while (baca < buf.length) {
            int n = in.read(buf, baca, buf.length - baca);
            if (n < 0) throw new IOException("Sambungan pairing terputus.");
            baca += n;
        }
    }
}
