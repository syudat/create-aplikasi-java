package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
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
 *    dipakai; kunci diturunkan dari kata sandi pengembang dengan
 *    PBKDF2 (bukan lagi dari nama proyek).
 *  - Tulis ulang DEX: nama kelas/metode/variabel proyek ditulis ulang
 *    DI DALAM berkas classes*.dex hasil D8 (penulisan ulang string
 *    yang panjangnya dipertahankan supaya semua offset tetap sah),
 *    lalu checksum dan tanda tangan SHA-1 kepala DEX dihitung ulang.
 *
 * TITIK SISIP DI JALUR BUILD (BangunJava/BangunKotlin dibaca saja):
 *  - BangunJava.jalankan(): sesudah D8 menulis dexOut/classes.dex
 *    dan sebelum kirimDex() — panggil tulisUlangDex() atas dex itu.
 *  - BangunLokal.rakit(): sesudah BangunJava.gabungDex() mengembalikan
 *    peta dex gabungan dan sebelum tambah() menulis tiap dex ke ZIP —
 *    panggil tulisUlangDex() atas tiap byte[] dex gabungan.
 *  - Keduanya BERADA SEBELUM penandatanganan (Penandatangan), jadi
 *    tanda tangan v1/v2/v3 tetap sah atas dex yang sudah diacak.
 *  - Layar PerisaiPolimorfikActivity juga memakai terapkanKeApk()
 *    untuk menulis ulang dex di dalam APK belum-ditandatangani di
 *    antara build dan penandatanganan, tanpa mengubah berkas lain.
 *
 * BATAS JUJUR: penulisan ulang memakai penggantian string yang
 * panjangnya dipertahankan (nama acak dipadatkan ke panjang byte
 * yang sama dengan nama asli), jadi struktur DEX tidak dibangun
 * ulang dan semua offset tetap sah. String yang juga dipakai
 * sebagai konstanta string di kode, nama elemen anotasi, atau nilai
 * anotasi TIDAK ditulis ulang (supaya perilaku aplikasi tidak
 * berubah). Nama yang panjangnya 1 karakter tetap 1 karakter.
 * Refleksi dan nama yang dibaca dari luar (mis. nama Activity di
 * AndroidManifest.xml) tidak ikut ditulis ulang; bila proyek memakai
 * refleksi atas nama yang diacak, aplikasi hasil bisa gagal —
 * periksa log dan pulihkan nama itu dari peta.
 */
public class PerisaiPolimorfik {

    /** Nama berkas peta pemulihan terenkripsi di folder proyek. */
    public static final String NAMA_PETA = "perisai-peta.brankas";

    /** Nama folder brankas aset di dalam proyek. */
    public static final String FOLDER_BRANKAS = "brankas-aset";

    /** Awalan nama acak (satu huruf + heksadesimal). */
    public static final String AWALAN_ACAK = "x";

    /** Panjang garam PBKDF2 (bita). */
    public static final int PANJANG_GARAM = 16;

    /** Iterasi PBKDF2 (sama dengan konvensi proyek: 20.000). */
    public static final int ITERASI_PBKDF2 = 20000;

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

    /** Garam acak untuk PBKDF2. */
    public static byte[] garamAcak() {
        byte[] garam = new byte[PANJANG_GARAM];
        new SecureRandom().nextBytes(garam);
        return garam;
    }

    /**
     * Kunci AES-256 dari kata sandi pengembang dengan PBKDF2
     * (PBKDF2WithHmacSHA256; bila tidak ada, PBKDF2WithHmacSHA1).
     * Kata sandi yang sama + garam yang sama = kunci yang sama;
     * garam berbeda = kunci berbeda. Kunci TIDAK pernah diturunkan
     * dari nama proyek lagi.
     */
    public static byte[] kunciDariKataSandi(String kataSandi, byte[] garam, int iterasi) {
        try {
            if (kataSandi == null) kataSandi = "";
            if (garam == null || garam.length == 0) garam = new byte[PANJANG_GARAM];
            if (iterasi <= 0) iterasi = ITERASI_PBKDF2;
            SecretKeyFactory pabrik = null;
            try {
                pabrik = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            } catch (Throwable t2) {
                pabrik = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            }
            PBEKeySpec spec = new PBEKeySpec(kataSandi.toCharArray(), garam, iterasi, 256);
            return pabrik.generateSecret(spec).getEncoded();
        } catch (Throwable t) {
            return null;
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
     * Nama acak yang panjang bytenya SAMA PERSIS dengan nama asli
     * (untuk penulisan ulang DEX: "x" + heksadesimal, dipadatkan atau
     * dipotong ke panjang yang sama). Nama 1 karakter tetap "x".
     */
    public static String acakNamaPanjangSama(String asli, String benih) {
        if (asli == null || asli.length() == 0) return "";
        if (benih == null) benih = "";
        String acak = acakNama(asli, benih);
        return sesuaikanPanjangNama(acak, panjangByte(asli));
    }

    /** Panjang byte UTF-8 sebuah teks (-1 bila gagal). */
    private static int panjangByte(String teks) {
        try {
            return teks.getBytes("UTF-8").length;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Sesuaikan nama acak dari peta ke panjang byte yang diminta:
     * dipotong bila terlalu panjang, dipadatkan dengan '0' bila
     * terlalu pendek. Jadi nama di dalam DEX selalu berhubungan
     * dengan nama di peta pemulihan (awalan yang sama).
     */
    public static String sesuaikanPanjangNama(String acak, int panjang) {
        if (acak == null || acak.length() == 0 || panjang <= 0) return "";
        if (panjang == 1) return "x";
        if (acak.length() == panjang) return acak;
        if (acak.length() > panjang) return acak.substring(0, panjang);
        StringBuilder b = new StringBuilder(panjang);
        b.append(acak);
        while (b.length() < panjang) b.append('0');
        return b.toString();
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

    /**
     * Tulis brankas dengan kata sandi pengembang. Format berkas:
     * garam 16 bita + IV 12 bita + ciphertext + tag GCM. Kunci
     * diturunkan dari kata sandi dengan PBKDF2; garam disimpan di
     * berkas supaya kunci bisa diturunkan lagi saat membuka.
     */
    public static File tulisBrankasKataSandi(File dir, String nama, byte[] data, String kataSandi) {
        try {
            if (dir == null || nama == null || kataSandi == null || kataSandi.length() == 0) return null;
            dir.mkdirs();
            byte[] garam = garamAcak();
            byte[] kunci = kunciDariKataSandi(kataSandi, garam, ITERASI_PBKDF2);
            if (kunci == null) return null;
            byte[] paket = enkripsiAset(data, kunci);
            if (paket == null) return null;
            byte[] keluar = new byte[garam.length + paket.length];
            System.arraycopy(garam, 0, keluar, 0, garam.length);
            System.arraycopy(paket, 0, keluar, garam.length, paket.length);
            File tujuan = new File(dir, nama);
            OutputStream out = new FileOutputStream(tujuan);
            try {
                out.write(keluar);
            } finally {
                out.close();
            }
            return tujuan;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Baca brankas kata sandi (null bila gagal / kata sandi salah). */
    public static byte[] bacaBrankasKataSandi(File berkas, String kataSandi) {
        try {
            if (berkas == null || !berkas.isFile() || kataSandi == null) return null;
            InputStream in = new FileInputStream(berkas);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                in.close();
            }
            byte[] semua = buf.toByteArray();
            if (semua.length < PANJANG_GARAM + 13) return null;
            byte[] garam = new byte[PANJANG_GARAM];
            System.arraycopy(semua, 0, garam, 0, PANJANG_GARAM);
            byte[] paket = new byte[semua.length - PANJANG_GARAM];
            System.arraycopy(semua, PANJANG_GARAM, paket, 0, paket.length);
            byte[] kunci = kunciDariKataSandi(kataSandi, garam, ITERASI_PBKDF2);
            if (kunci == null) return null;
            return dekripsiAset(paket, kunci);
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

    // ==================================================================
    // Penulisan ulang DEX (pengolah DEX minimal, tanpa pustaka luar)
    // ==================================================================
    //
    // Cara kerja: DEX dibaca sebagai biner little-endian. Nama ditulis
    // ulang HANYA di tabel string_ids, dengan pengganti yang panjang
    // bytenya sama persis dengan nama asli, jadi tidak satu pun offset
    // di berkas berubah. Sesudah itu checksum Adler32 dan tanda tangan
    // SHA-1 di kepala DEX dihitung ulang (keduanya wajib, kalau tidak
    // runtime menolak dex).
    //
    // Yang ditulis ulang: nama kelas (deskriptor tipe L...;), nama
    // metode, dan nama variabel anggota — tetapi HANYA bila string itu
    // tidak dipakai di tempat lain sebagai nilai (konstanta string di
    // kode, nama/sig lokal di info debug, nama elemen dan nilai string
    // di anotasi, nilai statis, dan call site). Jadi perilaku aplikasi
    // tidak berubah: semua rujukan memakai indeks yang sama.
    //
    // Bila paketProyek diisi (mis. "com.contoh.game"), hanya kelas /
    // metode / variabel milik paket itu yang ditulis ulang; bila null,
    // semua yang cocok dengan peta ditulis ulang.

    /** Hasil penulisan ulang dex di dalam satu APK. */
    public static class HasilDex {
        public int dexDiubah = 0;
        public int namaDiubah = 0;
        public String galat = "";
        public String toString() {
            if (galat != null && galat.length() > 0) return galat;
            return "DEX diubah: " + dexDiubah + " berkas, " + namaDiubah + " nama ditulis ulang.";
        }
    }

    /** Tulis ulang dex dari bita; null bila dex tidak sah / gagal. */
    public static byte[] tulisUlangDex(byte[] dex, Map<String, String> peta) {
        return tulisUlangDex(dex, peta, null);
    }

    /**
     * Tulis ulang dex dari bita dengan batas paket proyek.
     * Mengembalikan salinan dex baru, atau null bila gagal.
     */
    public static byte[] tulisUlangDex(byte[] dex, Map<String, String> peta, String paketProyek) {
        HasilTulis h = tulisUlangDexDalam(dex, peta, paketProyek);
        return h == null ? null : h.dex;
    }

    /** Jumlah nama yang ditulis ulang oleh tulisUlangDex terakhir (-1 bila gagal). */
    public static int hitungTulisUlangDex(byte[] dex, Map<String, String> peta, String paketProyek) {
        HasilTulis h = tulisUlangDexDalam(dex, peta, paketProyek);
        return h == null ? -1 : h.jumlah;
    }

    private static class HasilTulis {
        byte[] dex;
        int jumlah;
    }

    private static HasilTulis tulisUlangDexDalam(byte[] dex, Map<String, String> peta, String paketProyek) {
        try {
            if (dex == null || peta == null || peta.size() == 0) return null;
            if (periksaDex(dex) != null) return null;
            Dex d = new Dex(dex);
            Set<Integer> larang = d.kumpulStringTerlarang();
            Map<Integer, byte[]> ganti = new LinkedHashMap<Integer, byte[]>();
            Map<String, String> sudahDipakai = new HashMap<String, String>();
            // Nama kelas dari deskriptor tipe.
            for (int i = 0; i < d.typeIdsSize; i++) {
                int idxString = d.u32(d.typeIdsOff + i * 4);
                String desk = d.stringKeTeks(idxString);
                if (desk == null || desk.length() < 3) continue;
                if (!desk.startsWith("L") || !desk.endsWith(";")) continue;
                if (paketProyek != null && paketProyek.length() > 0) {
                    String awalan = "L" + paketProyek.replace('.', '/') + "/";
                    if (!desk.startsWith(awalan)) continue;
                }
                int garis = desk.lastIndexOf('/');
                String sederhana = desk.substring(garis + 1, desk.length() - 1);
                String acak = peta.get(sederhana);
                if (acak == null) continue;
                if (larang.contains(Integer.valueOf(idxString))) continue;
                if (!hanyaAscii(sederhana)) continue;
                String baru = sesuaikanPanjangNama(acak, sederhana.length());
                if (baru.equals(sederhana)) continue;
                if (!namaBaruAman(desk, sederhana, baru, sudahDipakai)) continue;
                String deskBaru = desk.substring(0, garis + 1) + baru + ";";
                byte[] b = deskBaru.getBytes("UTF-8");
                if (!d.tulisString(idxString, b)) continue;
                ganti.put(Integer.valueOf(idxString), b);
                sudahDipakai.put(deskBaru, desk);
            }
            // Nama metode dan variabel anggota.
            tulisUlangNamaId(d, d.fieldIdsOff, d.fieldIdsSize, 8, 4, peta, larang, ganti, sudahDipakai, paketProyek);
            tulisUlangNamaId(d, d.methodIdsOff, d.methodIdsSize, 8, 4, peta, larang, ganti, sudahDipakai, paketProyek);
            if (ganti.size() == 0) return null;
            d.perbaikiKepala();
            HasilTulis h = new HasilTulis();
            h.dex = d.d;
            h.jumlah = ganti.size();
            return h;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Tulis ulang nama metode/variabel dari tabel field_ids atau
     * method_ids. Tiap entri 8 bita: class_idx u16, type/proto u16,
     * name_idx u32 di offset 4.
     */
    private static void tulisUlangNamaId(Dex d, int offTabel, int jumlah, int ukuranEntri,
            int offNama, Map<String, String> peta, Set<Integer> larang,
            Map<Integer, byte[]> ganti, Map<String, String> sudahDipakai, String paketProyek) {
        for (int i = 0; i < jumlah; i++) {
            int dasar = offTabel + i * ukuranEntri;
            int idxKelas = d.u16(dasar);
            int idxString = d.u32(dasar + offNama);
            if (ganti.containsKey(Integer.valueOf(idxString))) continue;
            if (larang.contains(Integer.valueOf(idxString))) continue;
            if (paketProyek != null && paketProyek.length() > 0) {
                String deskKelas = d.stringKeTeks(d.u32(d.typeIdsOff + idxKelas * 4));
                if (deskKelas == null) continue;
                String awalan = "L" + paketProyek.replace('.', '/') + "/";
                if (!deskKelas.startsWith(awalan)) continue;
            }
            String nama = d.stringKeTeks(idxString);
            if (nama == null || nama.length() == 0) continue;
            String acak = peta.get(nama);
            if (acak == null) continue;
            if (!hanyaAscii(nama)) continue;
            String baru = sesuaikanPanjangNama(acak, nama.length());
            if (baru.equals(nama)) continue;
            if (!namaBaruAman(nama, nama, baru, sudahDipakai)) continue;
            byte[] b;
            try {
                b = baru.getBytes("UTF-8");
            } catch (Throwable t) {
                continue;
            }
            if (!d.tulisString(idxString, b)) continue;
            ganti.put(Integer.valueOf(idxString), b);
            sudahDipakai.put(baru, nama);
        }
    }

    /**
     * Cegah dua nama asli berbeda menjadi satu nama baru yang sama
     * (tabrakan akan merusak pemanggilan). Nama baru yang sudah
     * dipakai untuk nama asli lain ditolak.
     */
    private static boolean namaBaruAman(String konteks, String asli, String baru,
            Map<String, String> sudahDipakai) {
        String pemilik = sudahDipakai.get(baru);
        if (pemilik == null) return true;
        return pemilik.equals(asli) || pemilik.equals(konteks);
    }

    /** True bila semua karakter ASCII (syarat penggantian sama panjang). */
    private static boolean hanyaAscii(String teks) {
        for (int i = 0; i < teks.length(); i++) {
            if (teks.charAt(i) > 127) return false;
        }
        return true;
    }

    /** Tulis ulang berkas dex; mengembalikan jumlah nama diubah (-1 bila gagal). */
    public static int tulisUlangDex(File masuk, File keluar, Map<String, String> peta) {
        try {
            if (masuk == null || keluar == null || !masuk.isFile()) return -1;
            byte[] dex = bacaSemuaBerkas(masuk);
            byte[] baru = tulisUlangDex(dex, peta, null);
            if (baru == null) return -1;
            OutputStream out = new FileOutputStream(keluar);
            try {
                out.write(baru);
            } finally {
                out.close();
            }
            return 1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Tulis ulang semua classes*.dex di dalam APK (zip disalin utuh;
     * hanya entri dex yang diganti isinya). Dipakai di antara build
     * dan penandatanganan: APK masuk harus BELUM ditandatangani.
     */
    public static HasilDex terapkanKeApk(File apkMasuk, File apkKeluar, Map<String, String> peta) {
        return terapkanKeApk(apkMasuk, apkKeluar, peta, null);
    }

    /** Seperti terapkanKeApk, dengan batas paket proyek. */
    public static HasilDex terapkanKeApk(File apkMasuk, File apkKeluar,
            Map<String, String> peta, String paketProyek) {
        HasilDex hasil = new HasilDex();
        try {
            if (apkMasuk == null || !apkMasuk.isFile() || apkKeluar == null || peta == null) {
                hasil.galat = "APK tidak ditemukan atau peta kosong.";
                return hasil;
            }
            ZipFile zf = new ZipFile(apkMasuk);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apkKeluar));
            try {
                java.util.Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    InputStream in = zf.getInputStream(e);
                    byte[] data = bacaSemua(in);
                    in.close();
                    if (Perisai.namaDex(e.getName())) {
                        HasilTulis tulis = null;
                        try {
                            tulis = tulisUlangDexDalam(data, peta, paketProyek);
                        } catch (Throwable ignored) {
                        }
                        if (tulis != null) {
                            data = tulis.dex;
                            hasil.dexDiubah++;
                            hasil.namaDiubah += tulis.jumlah;
                        }
                    }
                    ZipEntry ne = new ZipEntry(e.getName());
                    if (e.getMethod() == ZipEntry.STORED) {
                        ne.setMethod(ZipEntry.STORED);
                        ne.setSize(data.length);
                        ne.setCompressedSize(data.length);
                        CRC32 c = new CRC32();
                        c.update(data);
                        ne.setCrc(c.getValue());
                    }
                    zos.putNextEntry(ne);
                    zos.write(data);
                    zos.closeEntry();
                }
            } finally {
                zos.close();
                zf.close();
            }
            if (hasil.dexDiubah == 0) {
                hasil.galat = "Tidak ada nama yang ditulis ulang di dex APK ini.";
                return hasil;
            }
            return hasil;
        } catch (Throwable t) {
            hasil.galat = "Gagal menulis ulang DEX: " + String.valueOf(t.getMessage());
            return hasil;
        }
    }

    /**
     * Periksa struktur DEX secara mandiri: magic, checksum Adler32,
     * tanda tangan SHA-1 kepala, dan semua offset bagian di dalam
     * berkas. Mengembalikan null bila sah, atau pesan masalah.
     */
    public static String periksaDex(byte[] dex) {
        try {
            if (dex == null) return "dex kosong";
            if (dex.length < 112) return "dex terlalu pendek";
            if (dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') {
                return "magic dex tidak dikenal";
            }
            Dex d = new Dex(dex);
            if (d.fileSize != dex.length) return "file_size kepala tidak cocok";
            if (d.u32(8) != (int) d.adler(12, dex.length - 12)) return "checksum Adler32 tidak cocok";
            byte[] sha = d.sha1(32, dex.length - 32);
            for (int i = 0; i < 20; i++) {
                if (dex[12 + i] != sha[i]) return "tanda tangan SHA-1 kepala tidak cocok";
            }
            int[][] bagian = {
                    {d.stringIdsOff, d.stringIdsSize * 4},
                    {d.typeIdsOff, d.typeIdsSize * 4},
                    {d.protoIdsOff, d.protoIdsSize * 12},
                    {d.fieldIdsOff, d.fieldIdsSize * 8},
                    {d.methodIdsOff, d.methodIdsSize * 8},
                    {d.classDefsOff, d.classDefsSize * 32},
            };
            for (int i = 0; i < bagian.length; i++) {
                int off = bagian[i][0];
                int len = bagian[i][1];
                if (off < 0 || len < 0 || off + len > dex.length) {
                    return "offset bagian di luar berkas";
                }
            }
            for (int i = 0; i < d.stringIdsSize; i++) {
                int off = d.u32(d.stringIdsOff + i * 4);
                if (off < 0 || off >= dex.length) return "offset string di luar berkas";
            }
            return null;
        } catch (Throwable t) {
            return "dex rusak: " + String.valueOf(t.getMessage());
        }
    }

    private static byte[] bacaSemuaBerkas(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            return bacaSemua(in);
        } finally {
            in.close();
        }
    }

    private static byte[] bacaSemua(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        return buf.toByteArray();
    }

    // ---------------------------------------------------------------
    // Pembaca DEX minimal (little-endian, hanya baca + tambal string)
    // ---------------------------------------------------------------
    private static class Dex {
        byte[] d;
        int fileSize;
        int stringIdsSize, stringIdsOff;
        int typeIdsSize, typeIdsOff;
        int protoIdsSize, protoIdsOff;
        int fieldIdsSize, fieldIdsOff;
        int methodIdsSize, methodIdsOff;
        int classDefsSize, classDefsOff;
        int mapOff;

        Dex(byte[] dex) {
            d = dex;
            fileSize = u32(32);
            stringIdsSize = u32(56);
            stringIdsOff = u32(60);
            typeIdsSize = u32(64);
            typeIdsOff = u32(68);
            protoIdsSize = u32(72);
            protoIdsOff = u32(76);
            fieldIdsSize = u32(80);
            fieldIdsOff = u32(84);
            methodIdsSize = u32(88);
            methodIdsOff = u32(92);
            classDefsSize = u32(96);
            classDefsOff = u32(100);
            mapOff = u32(52);
        }

        int u16(int off) {
            return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8);
        }

        int u32(int off) {
            return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8)
                    | ((d[off + 2] & 0xff) << 16) | ((d[off + 3] & 0xff) << 24);
        }

        /** Baca ULEB128 pada posisi; posisi baru di pos[0]. */
        int uleb(int off, int[] pos) {
            int hasil = 0;
            int geser = 0;
            int p = off;
            while (true) {
                int b = d[p++] & 0xff;
                hasil |= (b & 0x7f) << geser;
                if ((b & 0x80) == 0) break;
                geser += 7;
            }
            pos[0] = p;
            return hasil;
        }

        int sleb(int off, int[] pos) {
            int hasil = 0;
            int geser = 0;
            int p = off;
            int b;
            while (true) {
                b = d[p++] & 0xff;
                hasil |= (b & 0x7f) << geser;
                geser += 7;
                if ((b & 0x80) == 0) break;
            }
            if ((b & 0x40) != 0) hasil |= -(1 << geser);
            pos[0] = p;
            return hasil;
        }

        /** Offset data string ke-idx (awal uleb panjang). */
        int stringDataOff(int idx) {
            return u32(stringIdsOff + idx * 4);
        }

        /** Isi string ke-idx sebagai teks (decode MUTF-8 sederhana). */
        String stringKeTeks(int idx) {
            if (idx < 0 || idx >= stringIdsSize) return null;
            int off = stringDataOff(idx);
            int[] pos = new int[1];
            uleb(off, pos); // utf16_size: diabaikan, baca sampai NUL
            int awal = pos[0];
            int akhir = awal;
            while (akhir < d.length && d[akhir] != 0) akhir++;
            // MUTF-8: urutan 0xC0 0x80 = NUL; selain itu sama dengan UTF-8.
            byte[] mentah = new byte[akhir - awal];
            int n = 0;
            int p = awal;
            while (p < akhir) {
                int b0 = d[p] & 0xff;
                if (b0 == 0xC0 && p + 1 < akhir && (d[p + 1] & 0xff) == 0x80) {
                    mentah[n++] = 0;
                    p += 2;
                } else {
                    mentah[n++] = d[p++];
                }
            }
            try {
                return new String(mentah, 0, n, "UTF-8");
            } catch (Throwable t) {
                return null;
            }
        }

        /** Panjang byte isi string (tanpa awalan panjang dan NUL akhir). */
        int stringPanjangByte(int idx) {
            int off = stringDataOff(idx);
            int[] pos = new int[1];
            uleb(off, pos);
            int awal = pos[0];
            int p = awal;
            while (p < d.length && d[p] != 0) p++;
            return p - awal;
        }

        /**
         * Tulis bita baru ke isi string (panjang byte HARUS sama
         * dengan yang lama; mengembalikan false bila tidak sama).
         */
        boolean tulisString(int idx, byte[] baru) {
            if (idx < 0 || idx >= stringIdsSize) return false;
            int off = stringDataOff(idx);
            int[] pos = new int[1];
            uleb(off, pos);
            int awal = pos[0];
            int lama = stringPanjangByte(idx);
            if (baru.length != lama) return false;
            // Tolak bita NUL di dalam pengganti.
            for (int i = 0; i < baru.length; i++) {
                if (baru[i] == 0) return false;
            }
            System.arraycopy(baru, 0, d, awal, baru.length);
            return true;
        }

        /** Hitung ulang checksum Adler32 dan tanda tangan SHA-1 kepala. */
        void perbaikiKepala() throws Exception {
            // URUTAN WAJIB: SHA-1 dulu (mencakup bita 32..akhir, tidak
            // termasuk kepala), lalu Adler32 (mencakup bita 12..akhir,
            // TERMASUK tanda tangan SHA-1 yang baru). Terbalik =
            // checksum tidak cocok dengan tanda tangan baru.
            byte[] sha = sha1(32, d.length - 32);
            System.arraycopy(sha, 0, d, 12, 20);
            long adler = adler(12, d.length - 12);
            d[8] = (byte) (adler & 0xff);
            d[9] = (byte) ((adler >> 8) & 0xff);
            d[10] = (byte) ((adler >> 16) & 0xff);
            d[11] = (byte) ((adler >> 24) & 0xff);
        }

        long adler(int off, int len) {
            Adler32 a = new Adler32();
            a.update(d, off, len);
            return a.getValue();
        }

        byte[] sha1(int off, int len) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(d, off, len);
            return md.digest();
        }

        /**
         * Kumpulkan indeks string yang TIDAK BOLEH ditulis ulang:
         * dipakai sebagai konstanta string di kode (const-string),
         * nama/sig lokal di info debug, nama elemen atau nilai string
         * di anotasi, nilai statis kelas, dan call site.
         */
        Set<Integer> kumpulStringTerlarang() {
            Set<Integer> larang = new HashSet<Integer>();
            int[] pos = new int[1];
            // 1) Kode tiap metode: const-string + info debug.
            for (int c = 0; c < classDefsSize; c++) {
                int dasar = classDefsOff + c * 32;
                int dataOff = u32(dasar + 24);
                int nilaiStatisOff = u32(dasar + 28);
                int anotasiOff = u32(dasar + 20);
                if (nilaiStatisOff != 0) kumpulNilaiTerkode(nilaiStatisOff, larang);
                if (anotasiOff != 0) kumpulAnotasiDirektori(anotasiOff, larang);
                if (dataOff == 0) continue;
                int p = dataOff;
                int statis = uleb(p, pos); p = pos[0];
                int instans = uleb(p, pos); p = pos[0];
                int metodeLangsung = uleb(p, pos); p = pos[0];
                int metodeVirtual = uleb(p, pos); p = pos[0];
                for (int i = 0; i < statis + instans; i++) {
                    uleb(p, pos); p = pos[0]; // field_idx_diff
                    uleb(p, pos); p = pos[0]; // access_flags
                }
                int idxMetode = 0;
                for (int i = 0; i < metodeLangsung + metodeVirtual; i++) {
                    int diff = uleb(p, pos); p = pos[0];
                    idxMetode += diff;
                    uleb(p, pos); p = pos[0]; // access_flags
                    int codeOff = uleb(p, pos); p = pos[0];
                    if (codeOff != 0) kumpulKode(codeOff, larang);
                }
            }
            // 2) Call site (dex 038+): tiap entri = encoded array.
            if (mapOff != 0) {
                int jumlah = u32(mapOff);
                for (int i = 0; i < jumlah; i++) {
                    int e = mapOff + 4 + i * 12;
                    int tipe = u16(e);
                    int ukuran = u32(e + 4);
                    int off = u32(e + 8);
                    if (tipe == 0x0007) { // TYPE_CALL_SITE_ID_ITEM
                        for (int j = 0; j < ukuran; j++) {
                            int itemOff = u32(off + j * 4);
                            if (itemOff != 0) kumpulNilaiTerkode(itemOff, larang);
                        }
                    }
                }
            }
            return larang;
        }

        /** Pindai satu code_item: const-string + info debug. */
        void kumpulKode(int codeOff, Set<Integer> larang) {
            try {
                int debugOff = u32(codeOff + 8);
                int insnsSize = u32(codeOff + 12);
                int awal = codeOff + 16;
                for (int i = 0; i < insnsSize; i++) {
                    int unit = u16(awal + i * 2);
                    int op = unit & 0xff;
                    if (op == 0x1a) { // const-string (21c)
                        if (i + 1 < insnsSize) {
                            larang.add(Integer.valueOf(u16(awal + (i + 1) * 2)));
                        }
                    } else if (op == 0x1b) { // const-string/jumbo (31c)
                        if (i + 2 < insnsSize) {
                            int idx = u16(awal + (i + 1) * 2)
                                    | (u16(awal + (i + 2) * 2) << 16);
                            larang.add(Integer.valueOf(idx));
                        }
                    }
                }
                if (debugOff != 0) kumpulDebug(debugOff, larang);
            } catch (Throwable ignored) {
            }
        }

        /** Pindai info debug: nama lokal, sig, nama parameter, nama berkas. */
        void kumpulDebug(int off, Set<Integer> larang) {
            try {
                int[] pos = new int[1];
                int p = off;
                uleb(p, pos); p = pos[0]; // line_start
                int paramSize = uleb(p, pos); p = pos[0];
                for (int i = 0; i < paramSize; i++) {
                    int v = uleb(p, pos); p = pos[0]; // uleb128p1
                    if (v != 0) larang.add(Integer.valueOf(v - 1));
                }
                while (p < d.length) {
                    // Pengaman: info debug asli selalu pendek; bila
                    // pengurai kehilangan jejak, berhenti daripada
                    // mengembara sampai akhir berkas.
                    if (p - off > 65536) break;
                    int op = d[p++] & 0xff;
                    if (op == 0x00) break; // DBG_END_SEQUENCE
                    switch (op) {
                        case 0x01: // DBG_ADVANCE_PC
                            uleb(p, pos); p = pos[0];
                            break;
                        case 0x02: // DBG_ADVANCE_LINE
                            sleb(p, pos); p = pos[0];
                            break;
                        case 0x03: // DBG_START_LOCAL
                            uleb(p, pos); p = pos[0]; // register
                            int nama = uleb(p, pos); p = pos[0];
                            if (nama != 0) larang.add(Integer.valueOf(nama - 1));
                            uleb(p, pos); p = pos[0]; // type
                            break;
                        case 0x04: // DBG_START_LOCAL_EXTENDED
                            uleb(p, pos); p = pos[0];
                            int nama2 = uleb(p, pos); p = pos[0];
                            if (nama2 != 0) larang.add(Integer.valueOf(nama2 - 1));
                            uleb(p, pos); p = pos[0];
                            int sig = uleb(p, pos); p = pos[0];
                            if (sig != 0) larang.add(Integer.valueOf(sig - 1));
                            break;
                        case 0x05: // DBG_END_LOCAL
                        case 0x06: // DBG_RESTART_LOCAL
                            uleb(p, pos); p = pos[0];
                            break;
                        case 0x09: // DBG_SET_FILE
                            int berkas = uleb(p, pos); p = pos[0];
                            if (berkas != 0) larang.add(Integer.valueOf(berkas - 1));
                            break;
                        case 0x07: // DBG_SET_PROLOGUE_END
                        case 0x08: // DBG_SET_EPILOGUE_BEGIN
                            break;
                        default:
                            // DBG_SPECIAL_OPCODE (0x0a..0xff): tanpa argumen.
                            break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        /** Pindai annotations_directory_item beserta semua set anotasi. */
        void kumpulAnotasiDirektori(int off, Set<Integer> larang) {
            try {
                int anotasiKelasOff = u32(off);
                int jmlLapangan = u32(off + 4);
                int jmlMetode = u32(off + 8);
                int jmlParameter = u32(off + 12);
                if (anotasiKelasOff != 0) kumpulSetAnotasi(anotasiKelasOff, larang);
                int p = off + 16;
                for (int i = 0; i < jmlLapangan; i++) {
                    int setOff = u32(p + 4);
                    if (setOff != 0) kumpulSetAnotasi(setOff, larang);
                    p += 8;
                }
                for (int i = 0; i < jmlMetode; i++) {
                    int setOff = u32(p + 4);
                    if (setOff != 0) kumpulSetAnotasi(setOff, larang);
                    p += 8;
                }
                for (int i = 0; i < jmlParameter; i++) {
                    int daftarOff = u32(p + 4);
                    if (daftarOff != 0) {
                        int ukuran = u32(daftarOff);
                        for (int j = 0; j < ukuran; j++) {
                            int setOff = u32(daftarOff + 4 + j * 4);
                            if (setOff != 0) kumpulSetAnotasi(setOff, larang);
                        }
                    }
                    p += 8;
                }
            } catch (Throwable ignored) {
            }
        }

        void kumpulSetAnotasi(int off, Set<Integer> larang) {
            try {
                int ukuran = u32(off);
                for (int i = 0; i < ukuran; i++) {
                    int itemOff = u32(off + 4 + i * 4);
                    if (itemOff != 0) kumpulAnotasi(itemOff, larang);
                }
            } catch (Throwable ignored) {
            }
        }

        void kumpulAnotasi(int off, Set<Integer> larang) {
            try {
                int[] pos = new int[1];
                // annotation_item diawali satu bita visibilitas
                // (BUILD/RUNTIME/SYSTEM), baru type_idx + size.
                int p = off + 1;
                uleb(p, pos); p = pos[0]; // type_idx
                int ukuran = uleb(p, pos); p = pos[0];
                for (int i = 0; i < ukuran; i++) {
                    int namaIdx = uleb(p, pos); p = pos[0];
                    larang.add(Integer.valueOf(namaIdx));
                    p = kumpulNilaiTerkodeDi(p, larang);
                }
            } catch (Throwable ignored) {
            }
        }

        /** Baca indeks value_arg+1 bita little-endian pada posisi p. */
        int bacaIndeksLE(int p, int argNilai) {
            int hasil = 0;
            for (int i = 0; i <= argNilai && p + i < d.length; i++) {
                hasil |= (d[p + i] & 0xff) << (i * 8);
            }
            return hasil;
        }

        /** Pindai encoded_array pada offset (dipakai nilai statis + call site). */
        void kumpulNilaiTerkode(int off, Set<Integer> larang) {
            try {
                int[] pos = new int[1];
                int p = off;
                int ukuran = uleb(p, pos); p = pos[0];
                for (int i = 0; i < ukuran; i++) {
                    p = kumpulNilaiTerkodeDi(p, larang);
                }
            } catch (Throwable ignored) {
            }
        }

        /**
         * Pindai satu encoded_value pada posisi p; mengembalikan
         * posisi sesudah nilai itu. Mengumpulkan indeks string dari
         * nilai string dan nama elemen anotasi bersarang.
         */
        int kumpulNilaiTerkodeDi(int p, Set<Integer> larang) {
            int[] pos = new int[1];
            int b = d[p++] & 0xff;
            int tipeNilai = b & 0x1f;
            int argNilai = (b >> 5) & 0x7;
            switch (tipeNilai) {
                case 0x00: // byte
                case 0x02: // short
                case 0x03: // char
                case 0x04: // int
                case 0x06: // long
                case 0x10: // float
                case 0x11: // double
                    return p + argNilai + 1;
                case 0x17: // string
                    larang.add(Integer.valueOf(bacaIndeksLE(p, argNilai)));
                    return p + argNilai + 1;
                case 0x15: // method type
                case 0x16: // method handle
                case 0x18: // type
                case 0x19: // field
                case 0x1a: // method
                case 0x1b: // enum
                    // Indeks dikodekan sebagai value_arg+1 bita
                    // little-endian (bukan uleb128) — lihat
                    // ValueEncoder.writeUnsignedIntegralValue di dx.
                    return p + argNilai + 1;
                case 0x1c: // array
                    int ukuran = uleb(p, pos);
                    p = pos[0];
                    for (int i = 0; i < ukuran; i++) {
                        p = kumpulNilaiTerkodeDi(p, larang);
                    }
                    return p;
                case 0x1d: // annotation
                    uleb(p, pos); p = pos[0]; // type_idx
                    int jml = uleb(p, pos); p = pos[0];
                    for (int i = 0; i < jml; i++) {
                        int namaIdx = uleb(p, pos); p = pos[0];
                        larang.add(Integer.valueOf(namaIdx));
                        p = kumpulNilaiTerkodeDi(p, larang);
                    }
                    return p;
                case 0x1e: // null
                case 0x1f: // boolean
                default:
                    return p;
            }
        }
    }
}
