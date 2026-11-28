package com.androlua;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SPAKE2 (Edwards25519) murni-Java untuk pairing debugging nirkabel adb
 * (Android 11+), ditulis dari spesifikasi BoringSSL (spake25519.cc,
 * lisensi Apache-2.0) — BUKAN salinan kode spake2-java (GPL-3.0) atau
 * pustaka lain. Dipakai AdbPasangan untuk bertukar kunci dengan adbd
 * lewat layanan _adb-tls-pairing._tcp.
 *
 * Parameter (BoringSSL):
 * - Kurva Edwards25519, a = -1, d = -121665/121666 mod p, p = 2^255-19.
 * - Titik M (Alice) = 5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e
 *   Titik N (Bob)   = 10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778
 *   (dibangkitkan dari benih 'edwards25519 point generation seed (M/N)'
 *   lewat SHA-256 + decode-as-point TANPA perkalian kofaktor).
 * - Nama: "adb pair client\0" (Alice/klien) dan "adb pair server\0"
 *   (Bob/server), NUL ikut dihitung dalam transkrip.
 * - hash sandi = SHA-512(sandi) penuh 64 bita; skalar sandi =
 *   sc_reduce(SHA-512) lalu "password scalar hack": tambah L bila bit 0
 *   menyala, 2L bila bit 1 menyala, 4L bila bit 2 menyala (diperiksa
 *   berurutan SETELAH tiap penjumlahan) sehingga kelipatan 8.
 * - Skalar privat: 64 bita acak -> sc_reduce -> kali 8 (left_shift_3).
 * - Kunci bersama: SHA-512 atas transkrip dengan awalan panjang 8 bita
 *   little-endian per ruas: Alice [namaSaya, namaLawan, pesanSaya,
 *   pesanLawan, dh, hashSandi]; Bob [namaLawan, namaSaya, pesanLawan,
 *   pesanSaya, dh, hashSandi]. Keluaran 64 bita.
 *
 * Kelas murni (tanpa API Android) supaya bisa diuji di JVM.
 */
public class Spake2 {

    /** Peran: true = Alice (klien pairing adb), false = Bob (server). */
    public static final boolean ALICE = true;
    public static final boolean BOB = false;

    /** Nama klien pairing adb (dengan NUL akhir, ikut transkrip). */
    public static final byte[] NAMA_KLIEN = "adb pair client\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    /** Nama server pairing adb (dengan NUL akhir, ikut transkrip). */
    public static final byte[] NAMA_SERVER = "adb pair server\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

    static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    static final BigInteger L = BigInteger.ONE.shiftLeft(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));
    static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
    /** Titik dasar Ed25519 (G). */
    static final Titik G = new Titik(
            new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202"),
            new BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960"));
    /** Titik M (mask Alice). */
    static final Titik M = dekode(heks("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"));
    /** Titik N (mask Bob). */
    static final Titik N = dekode(heks("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"));

    /** Satu titik kurva Edwards dalam koordinat affine. */
    static class Titik {
        final BigInteger x;
        final BigInteger y;
        Titik(BigInteger x, BigInteger y) { this.x = x; this.y = y; }
        static final Titik IDENTITAS = new Titik(BigInteger.ZERO, BigInteger.ONE);
    }

    private final boolean alice;
    private final byte[] namaSaya;
    private final byte[] namaLawan;
    private final byte[] sandi;
    private byte[] hashSandi;
    private byte[] skalarSandi;
    private byte[] skalarPrivat;
    private byte[] pesanSaya;
    private boolean pesanDibuat = false;

    public Spake2(boolean alice, byte[] namaSaya, byte[] namaLawan, byte[] sandi) {
        this.alice = alice;
        this.namaSaya = namaSaya.clone();
        this.namaLawan = namaLawan.clone();
        this.sandi = sandi.clone();
    }

    /** Buat pesan SPAKE2 dengan 64 bita acak dari SecureRandom. */
    public byte[] buatPesan() throws Exception {
        byte[] acak = new byte[64];
        new SecureRandom().nextBytes(acak);
        return buatPesan(acak);
    }

    /**
     * Buat pesan SPAKE2 dengan 64 bita acak yang diberikan (untuk uji
     * deterministik; pemakaian nyata harus memakai buatPesan()).
     */
    public byte[] buatPesan(byte[] acak64) throws Exception {
        if (acak64 == null || acak64.length != 64) {
            throw new IllegalArgumentException("Acak harus 64 bita.");
        }
        if (pesanDibuat) throw new IllegalStateException("Pesan sudah dibuat.");
        // Skalar privat: sc_reduce(acak 64 bita) lalu kali 8.
        byte[] privat = scReduce(acak64);
        privat = kali8(privat);
        skalarPrivat = privat;
        Titik p = kaliSkalar(new BigInteger(1, balik(privat)), G);
        // Hash sandi + skalar sandi.
        MessageDigest sha = MessageDigest.getInstance("SHA-512");
        hashSandi = sha.digest(sandi);
        byte[] pw = scReduce(hashSandi);
        BigInteger s = new BigInteger(1, balik(pw));
        // Password scalar hack (BoringSSL spake25519.cc): periksa berurutan.
        if (s.testBit(0)) s = s.add(L);
        if (s.testBit(1)) s = s.add(L.shiftLeft(1));
        if (s.testBit(2)) s = s.add(L.shiftLeft(2));
        skalarSandi = le32(s);
        Titik mask = kaliSkalar(s, alice ? M : N);
        Titik bintang = tambah(p, mask);
        pesanSaya = enkode(bintang);
        pesanDibuat = true;
        return pesanSaya.clone();
    }

    /**
     * Proses pesan lawan; mengembalikan 64 bita bahan kunci bersama.
     * Bila sandi salah, kedua pihak mendapat kunci BERBEDA (tanpa galat)
     * — itulah sifat SPAKE2; AdbPasangan mendeteksinya lewat gagal
     * dekripsi PeerInfo lawan.
     */
    public byte[] prosesPesan(byte[] pesanLawan) throws Exception {
        if (!pesanDibuat) throw new IllegalStateException("Buat pesan dulu.");
        if (pesanLawan == null || pesanLawan.length != 32) {
            throw new IllegalArgumentException("Pesan lawan harus 32 bita.");
        }
        Titik qBintang = dekode(pesanLawan);
        if (qBintang == null) throw new IllegalArgumentException("Pesan lawan bukan titik kurva.");
        BigInteger pw = new BigInteger(1, balik(skalarSandi));
        Titik maskLawan = kaliSkalar(pw, alice ? N : M);
        Titik q = tambah(qBintang, negasi(maskLawan));
        Titik dh = kaliSkalar(new BigInteger(1, balik(skalarPrivat)), q);
        byte[] dhEnk = enkode(dh);
        MessageDigest sha = MessageDigest.getInstance("SHA-512");
        if (alice) {
            tambahPanjang(sha, namaSaya);
            tambahPanjang(sha, namaLawan);
            tambahPanjang(sha, pesanSaya);
            tambahPanjang(sha, pesanLawan);
        } else {
            tambahPanjang(sha, namaLawan);
            tambahPanjang(sha, namaSaya);
            tambahPanjang(sha, pesanLawan);
            tambahPanjang(sha, pesanSaya);
        }
        tambahPanjang(sha, dhEnk);
        tambahPanjang(sha, hashSandi);
        return sha.digest();
    }

    // ==================== Aritmetika Edwards25519 ====================

    /** Tambah dua titik (rumus Edwards lengkap, a = -1). */
    static Titik tambah(Titik a, Titik b) {
        BigInteger x1y2 = a.x.multiply(b.y).mod(P);
        BigInteger x2y1 = b.x.multiply(a.y).mod(P);
        BigInteger y1y2 = a.y.multiply(b.y).mod(P);
        BigInteger x1x2 = a.x.multiply(b.x).mod(P);
        BigInteger penyebutX = BigInteger.ONE.add(D.multiply(x1x2).multiply(y1y2)).mod(P);
        BigInteger penyebutY = BigInteger.ONE.subtract(D.multiply(x1x2).multiply(y1y2)).mod(P);
        BigInteger x3 = x1y2.add(x2y1).multiply(penyebutX.modInverse(P)).mod(P);
        BigInteger y3 = y1y2.add(x1x2).multiply(penyebutY.modInverse(P)).mod(P);
        return new Titik(x3, y3);
    }

    static Titik negasi(Titik a) {
        return new Titik(P.subtract(a.x).mod(P), a.y);
    }

    /** Perkalian skalar double-and-add (skalar >= 0). */
    static Titik kaliSkalar(BigInteger k, Titik p) {
        Titik hasil = Titik.IDENTITAS;
        Titik tambah = p;
        int bit = k.bitLength();
        for (int i = 0; i < bit; i++) {
            if (k.testBit(i)) hasil = tambah(hasil, tambah);
            tambah = tambah(tambah, tambah);
        }
        return hasil;
    }

    /** Enkode titik: y little-endian 32 bita + bit tanda x di bit 255. */
    static byte[] enkode(Titik p) {
        byte[] keluar = le32(p.y);
        if (p.x.testBit(0)) keluar[31] |= (byte) 0x80;
        return keluar;
    }

    /** Dekode titik dari 32 bita; null bila bukan titik kurva. */
    static Titik dekode(byte[] masuk) {
        if (masuk == null || masuk.length != 32) return null;
        byte[] b = masuk.clone();
        boolean tanda = (b[31] & 0x80) != 0;
        b[31] &= 0x7F;
        BigInteger y = new BigInteger(1, balik(b));
        if (y.compareTo(P) >= 0) return null;
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger xx = y2.subtract(BigInteger.ONE)
                .multiply(D.multiply(y2).add(BigInteger.ONE).modInverse(P)).mod(P);
        BigInteger x = xx.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (!x.multiply(x).subtract(xx).mod(P).equals(BigInteger.ZERO)) {
            x = x.multiply(BigInteger.valueOf(2).modPow(
                    P.subtract(BigInteger.ONE).shiftRight(2), P)).mod(P);
        }
        if (!x.multiply(x).subtract(xx).mod(P).equals(BigInteger.ZERO)) return null;
        if (x.testBit(0) != tanda) x = P.subtract(x);
        // Periksa persamaan kurva Edwards25519: -x^2 + y^2 = 1 + d x^2 y^2.
        BigInteger x2 = x.multiply(x).mod(P);
        if (!y2.subtract(x2).mod(P).equals(
                BigInteger.ONE.add(D.multiply(x2).multiply(y2)).mod(P))) return null;
        return new Titik(x, y);
    }

    /** sc_reduce: 64 bita little-endian mod L -> 32 bita little-endian. */
    static byte[] scReduce(byte[] masuk64) {
        BigInteger v = new BigInteger(1, balik(masuk64)).mod(L);
        return le32(v);
    }

    /** Kali 8 pada skalar 32 bita little-endian (left_shift_3). */
    static byte[] kali8(byte[] skalar32) {
        BigInteger v = new BigInteger(1, balik(skalar32)).shiftLeft(3)
                .mod(BigInteger.ONE.shiftLeft(256));
        return le32(v);
    }

    static void tambahPanjang(MessageDigest sha, byte[] data) {
        long n = data.length;
        byte[] len = new byte[8];
        for (int i = 0; i < 8; i++) {
            len[i] = (byte) (n & 0xFF);
            n >>= 8;
        }
        sha.update(len);
        sha.update(data);
    }

    /** Balik urutan bita (LE <-> BE). */
    static byte[] balik(byte[] masuk) {
        byte[] keluar = new byte[masuk.length];
        for (int i = 0; i < masuk.length; i++) keluar[i] = masuk[masuk.length - 1 - i];
        return keluar;
    }

    /** BigInteger -> 32 bita little-endian (nol di depan bila pendek). */
    static byte[] le32(BigInteger v) {
        byte[] be = v.toByteArray();
        byte[] keluar = new byte[32];
        int n = Math.min(32, be.length);
        for (int i = 0; i < n; i++) keluar[i] = be[be.length - 1 - i];
        return keluar;
    }

    static byte[] heks(String s) {
        byte[] keluar = new byte[s.length() / 2];
        for (int i = 0; i < keluar.length; i++) {
            keluar[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return keluar;
    }

    static String keHeks(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            s.append(String.format("%02x", b[i] & 0xFF));
        }
        return s.toString();
    }
}
