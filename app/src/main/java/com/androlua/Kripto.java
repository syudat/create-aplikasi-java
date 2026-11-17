package com.androlua;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enkripsi berkas .lua/.aly yang dikemas ke dalam APK (baik aplikasi ini sendiri maupun aplikasi
 * yang dibangun dengannya), supaya isinya tidak langsung terbaca bila seseorang membuka APK dan
 * melihat ke folder assets. Skema: AES-256-CBC, kunci acak 32 byte per build (disimpan sebagai
 * entri "assets/kuncilua.bin" di dalam APK), IV acak 16 byte per berkas, ditandai awalan 4 byte "AES1".
 *
 * Ini BUKAN perlindungan terhadap pembongkaran yang serius: kunci ikut berada di dalam APK yang
 * sama (dibutuhkan agar aplikasi bisa berjalan tanpa server), jadi orang yang tahu caranya tetap
 * bisa mengambil kunci itu dan membuka isinya. Tujuannya mencegah pencurian kode yang asal comot,
 * bukan menahan pembongkaran oleh orang yang memang berniat dan mengerti caranya.
 */
public class Kripto {
    private static final byte[] MAGIC = {'A', 'E', 'S', '1'};
    private static final int KEY_LEN = 32, IV_LEN = 16;

    public static byte[] kunciAcak() {
        byte[] k = new byte[KEY_LEN];
        new SecureRandom().nextBytes(k);
        return k;
    }

    public static byte[] enkripsi(byte[] data, byte[] key) throws Exception {
        if (key == null || key.length != KEY_LEN) throw new IllegalArgumentException("Kunci harus 32 byte");
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] ct = c.doFinal(data);
        byte[] out = new byte[MAGIC.length + IV_LEN + ct.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(iv, 0, out, MAGIC.length, IV_LEN);
        System.arraycopy(ct, 0, out, MAGIC.length + IV_LEN, ct.length);
        return out;
    }

    /** Mengembalikan data apa adanya bila tidak berawalan "AES1" (berkas lama/tidak terenkripsi). */
    public static byte[] dekripsi(byte[] data, byte[] key) throws Exception {
        if (data == null || data.length < MAGIC.length + IV_LEN || key == null || key.length != KEY_LEN)
            return data;
        for (int i = 0; i < MAGIC.length; i++) if (data[i] != MAGIC[i]) return data;
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(data, MAGIC.length, iv, 0, IV_LEN);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return c.doFinal(data, MAGIC.length + IV_LEN, data.length - MAGIC.length - IV_LEN);
    }

    public static boolean terenkripsi(byte[] data) {
        if (data == null || data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) if (data[i] != MAGIC[i]) return false;
        return true;
    }
}
