package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Perisai Polimorfik Bytecode dan Brankas Aset Terenkripsi (Fitur 1,
 * daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - acakNama: pengenal asli -> nama acak deterministis dari benih,
 *    jadi peta pemulihan bisa dibuat ulang dari benih yang sama.
 *  - Brankas aset AES-GCM: berkas aset proyek dienkripsi (IV acak 12
 *    bita + ciphertext + tag GCM) dan hanya dibuka di memori saat
 *    dipakai; kunci diturunkan dari frasa proyek (SHA-256).
 *
 * BATAS JUJUR: pengacakan ini menghasilkan PETA nama dan mengenkripsi
 * ASET. Penulisan ulang tabel string DEX hasil kompilasi (D8) agar
 * nama kelas/metode di dalam berkas .dex sungguh berubah adalah tahap
 * berikut yang menempel pada jalur build (BangunLokal); kelas ini
 * menyiapkan peta + brankasnya, bukan menulis ulang dex.
 */
public class PerisaiPolimorfik {

    /** Nama berkas peta pemulihan terenkripsi di folder proyek. */
    public static final String NAMA_PETA = "perisai-peta.brankas";

    /** Nama folder brankas aset di dalam proyek. */
    public static final String FOLDER_BRANKAS = "brankas-aset";

    /** Awalan nama acak (satu huruf + heksadesimal). */
    public static final String AWALAN_ACAK = "x";

    /** Kunci AES-256 dari frasa (SHA-256 frasa). */
    public static byte[] kunciDariFrasa(String frasa) {
        try {
            if (frasa == null) frasa = "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(frasa.getBytes("UTF-8"));
        } catch (Throwable t) {
            return new byte[32];
        }
    }

    /** Nama acak deterministis untuk satu pengenal (stabil per benih). */
    public static String acakNama(String asli, String benih) {
        if (asli == null || asli.length() == 0) return "";
        if (benih == null) benih = "";
        String hash = Perisai.sha256Hex((benih + "\n" + asli).getBytes());
        if (hash == null || hash.length() < 10) hash = "0000000000";
        return AWALAN_ACAK + hash.substring(0, 8);
    }

    /**
     * Peta pemulihan pengenal asli -> nama acak. Pengenal yang sama
     * selalu mendapat nama acak yang sama; dua pengenal berbeda yang
     * kebetulan bertabrakan diberi akhiran angka.
     */
    public static Map<String, String> buatPeta(List<String> pengenal, String benih) {
        Map<String, String> peta = new LinkedHashMap<String, String>();
        Map<String, String> balik = new LinkedHashMap<String, String>();
        if (pengenal == null) return peta;
        for (int i = 0; i < pengenal.size(); i++) {
            String asli = pengenal.get(i);
            if (asli == null || asli.length() == 0) continue;
            if (peta.containsKey(asli)) continue;
            String acak = acakNama(asli, benih);
            int akhir = 1;
            while (balik.containsKey(acak)) {
                akhir++;
                acak = acakNama(asli, benih) + akhir;
            }
            peta.put(asli, acak);
            balik.put(acak, asli);
        }
        return peta;
    }

    /** Nama asli dari nama acak menurut peta (null bila tidak ada). */
    public static String pulihkanNama(Map<String, String> peta, String acak) {
        if (peta == null || acak == null) return null;
        for (Map.Entry<String, String> e : peta.entrySet()) {
            if (acak.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    /** Enkripsi AES-GCM: IV 12 bita + ciphertext + tag 128 bit. */
    public static byte[] enkripsiAset(byte[] data, byte[] kunci) {
        try {
            if (data == null) data = new byte[0];
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kunci, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] sandi = c.doFinal(data);
            byte[] keluar = new byte[iv.length + sandi.length];
            System.arraycopy(iv, 0, keluar, 0, iv.length);
            System.arraycopy(sandi, 0, keluar, iv.length, sandi.length);
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Dekripsi paket AES-GCM (null bila kunci salah / paket rusak). */
    public static byte[] dekripsiAset(byte[] paket, byte[] kunci) {
        try {
            if (paket == null || paket.length < 13) return null;
            byte[] iv = new byte[12];
            System.arraycopy(paket, 0, iv, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kunci, "AES"),
                    new GCMParameterSpec(128, iv));
            return c.doFinal(paket, 12, paket.length - 12);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Tulis berkas terenkripsi; mengembalikan berkas tujuan (null bila gagal). */
    public static File tulisBrankas(File dir, String nama, byte[] data, byte[] kunci) {
        try {
            if (dir == null || nama == null) return null;
            dir.mkdirs();
            byte[] paket = enkripsiAset(data, kunci);
            if (paket == null) return null;
            File keluar = new File(dir, nama);
            OutputStream out = new FileOutputStream(keluar);
            try {
                out.write(paket);
            } finally {
                out.close();
            }
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Baca berkas brankas (null bila gagal / kunci salah). */
    public static byte[] bacaBrankas(File berkas, byte[] kunci) {
        try {
            if (berkas == null || !berkas.isFile()) return null;
            InputStream in = new FileInputStream(berkas);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                in.close();
            }
            return dekripsiAset(buf.toByteArray(), kunci);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Teks peta ("asli=acak" per baris) untuk disimpan terenkripsi. */
    public static String teksPeta(Map<String, String> peta) {
        StringBuilder b = new StringBuilder();
        if (peta == null) return "";
        for (Map.Entry<String, String> e : peta.entrySet()) {
            b.append(e.getKey()).append("=").append(e.getValue()).append("\n");
        }
        return b.toString();
    }

    /** Urai teks peta kembali menjadi peta (baris rusak dilewati). */
    public static Map<String, String> uraiPeta(String teks) {
        Map<String, String> peta = new LinkedHashMap<String, String>();
        if (teks == null) return peta;
        String[] baris = teks.split("\n");
        for (int i = 0; i < baris.length; i++) {
            int sama = baris[i].indexOf('=');
            if (sama <= 0) continue;
            peta.put(baris[i].substring(0, sama), baris[i].substring(sama + 1));
        }
        return peta;
    }

    /**
     * Kumpulkan pengenal dari kode sumber proyek: nama kelas, metode,
     * dan variabel (pola sederhana berbasis teks; baris komentar
     * dilewati). Dipakai untuk membangun peta sebelum build.
     */
    public static List<String> kumpulPengenal(String kode) {
        List<String> hasil = new ArrayList<String>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.startsWith("//") || b.startsWith("*")) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\\b(?:class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)")
                    .matcher(b);
            while (m.find()) tambahUnik(hasil, m.group(1));
            m = java.util.regex.Pattern.compile(
                    "\\b(?:public|private|protected|static|final|void|int|long|double|float|boolean|String|char|byte|short)\\b[^;\\(]*?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                    .matcher(b);
            while (m.find()) tambahUnik(hasil, m.group(1));
        }
        return hasil;
    }

    private static void tambahUnik(List<String> daftar, String nilai) {
        if (nilai == null || nilai.length() == 0) return;
        if (!daftar.contains(nilai)) daftar.add(nilai);
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahPengenal, int jumlahAset, long ukuranBrankas) {
        return "Nama diacak: " + jumlahPengenal + " pengenal. Brankas aset: "
                + jumlahAset + " berkas, " + ukuranBrankas + " bita terenkripsi.";
    }
}
