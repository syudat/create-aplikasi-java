package com.androlua;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.android.apksig.KeyConfig;
import com.android.apksig.SigningCertificateLineage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Rantai Kepercayaan Penandatanganan Bertingkat dan Penjaga Lingkungan
 * Waktu-Nyata (Fitur 2, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Rantai kunci: setiap generasi kunci = SHA-256 dari kunci
 *    sebelumnya, jadi rotasi bisa diverifikasi dari ujung ke ujung.
 *  - Penjaga lingkungan: pemindaian pola teks atas isi /proc/self/maps
 *    dan properti build untuk jejak Frida/Xposed/substrate dan
 *    emulator — tanpa membaca memori mentah.
 *  - Pemeriksaan memori asli: isi segmen kode yang bisa dieksekusi
 *    di /proc/self/mem dibandingkan dengan berkas di disk; pemetaan
 *    anonim yang bisa dieksekusi (RWX) dan pustaka asing dilaporkan.
 *    Bila sistem menolak membaca /proc/self/mem (SELinux di sebagian
 *    perangkat), pemeriksaan dilaporkan "tidak dapat diperiksa",
 *    bukan "aman".
 *  - Kunci chip: kunci brankas dibungkus kunci AES di AndroidKeyStore
 *    (hardware-backed bila chip keamanan tersedia); akses KeyStore
 *    memakai refleksi supaya kelas ini tetap bisa diuji di JVM.
 *  - Rotasi kunci v3 SESUNGGUHNYA: silsilah penandatanganan
 *    (SigningCertificateLineage) dari apksig — kunci lama menandatangani
 *    bukti rotasi ke kunci baru, APK ditandatangani skema v3 dengan
 *    silsilah itu, dan v1/v2 tetap memakai kunci lama supaya perangkat
 *    lama masih menerima pembaruan. Bukan lagi rantai hash.
 *
 * BATAS JUJUR: pemeriksaan memori membandingkan isi memori dengan
 * berkas di disk untuk segmen berkas yang bisa dieksekusi; ia tidak
 * bisa membuktikan tidak ada hook di dalam pustaka yang memang
 * dimuat dari disk yang sama (mis. pustaka yang sudah dimodifikasi
 * sebelum dipasang). Rotasi v3 membutuhkan kunci lama dan kunci baru
 * sebagai pasangan kunci RSA; silsilah disimpan di proyek supaya
 * rotasi berikutnya bisa berantai dari kunci terbaru.
 */
public class RantaiKepercayaan {

    /** Penanda pustaka injeksi di peta memori proses. */
    public static final String[] PENANDA_INJEKSI = {
            "frida", "gadget", "xposed", "lsposed", "edxposed",
            "substrate", "riru", "zygisk", "magisk"
    };

    /** Penanda properti emulator. */
    public static final String[] PENANDA_EMULATOR = {
            "ro.kernel.qemu", "generic", "sdk", "emulator", "goldfish",
            "ranchu", "vbox86", "ttvm_huawei"
    };

    /** Awalan jalur tepercaya untuk pustaka .so yang dimuat. */
    public static final String[] AWALAN_SO_TEPERCAYA = {
            "/system/", "/apex/", "/vendor/", "/product/", "/system_ext/",
            "/data/app/", "/data/data/", "/data/user/", "/data/misc/"
    };

    /** Bita yang dibandingkan per segmen memori (batas kerja ringan). */
    public static final int BATAS_BANDING_MEMORI = 4096;

    /** Segmen berkas r-x terbanyak yang diperiksa per pemindaian. */
    public static final int BATAS_SEGMEN_MEMORI = 64;

    /** Alias kunci chip di AndroidKeyStore untuk membungkus kunci brankas. */
    public static final String ALIAS_KUNCI_CHIP = "perisai-brankas";

    /** SDK minimum rotasi v3 (Android 9; di bawah itu v1/v2 kunci lama dipakai). */
    public static final int MIN_SDK_ROTASI_V3 = 28;

    /** SHA-256 heksadesimal huruf kecil. */
    public static String sha256Hex(String teks) {
        try {
            if (teks == null) teks = "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(teks.getBytes("UTF-8"));
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

    /** SHA-256 heksadesimal dari bita. */
    public static String sha256Hex(byte[] data) {
        try {
            if (data == null) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
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

    /**
     * Bangun rantai kunci: generasi 0 = SHA-256(kunci awal), generasi
     * berikutnya = SHA-256(hash sebelumnya). Daftar berisi hash tiap
     * generasi, berurutan.
     */
    public static List<String> buatRantai(String kunciAwal, int generasi) {
        List<String> rantai = new ArrayList<String>();
        if (generasi <= 0) return rantai;
        String kini = sha256Hex(kunciAwal == null ? "" : kunciAwal);
        rantai.add(kini);
        for (int i = 1; i < generasi; i++) {
            kini = sha256Hex(kini);
            rantai.add(kini);
        }
        return rantai;
    }

    /** True bila tiap tautan rantai = SHA-256 dari tautan sebelumnya. */
    public static boolean periksaRantai(List<String> rantai) {
        if (rantai == null || rantai.size() == 0) return false;
        for (int i = 1; i < rantai.size(); i++) {
            if (!sha256Hex(rantai.get(i - 1)).equals(rantai.get(i))) return false;
        }
        return true;
    }

    /** Kunci generasi berikutnya dari kunci saat ini (rotasi). */
    public static String putarKunci(String kunciSaatIni) {
        return sha256Hex(kunciSaatIni == null ? "" : kunciSaatIni);
    }

    /** Satu temuan penjaga lingkungan: jenis + bukti teksnya. */
    public static class Temuan {
        public String jenis = "";
        public String bukti = "";
        public Temuan(String jenis, String bukti) {
            this.jenis = jenis;
            this.bukti = bukti;
        }
        public String toString() {
            return jenis + ": " + bukti;
        }
    }

    /**
     * Pindai isi peta memori (/proc/self/maps) dan properti build
     * (getprop) untuk jejak injeksi dan emulator. Pencocokan tidak
     * peka huruf besar/kecil; satu penanda = satu temuan.
     */
    public static List<Temuan> deteksiLingkungan(String isiMaps, String isiProp) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (isiMaps != null) {
            String kecil = isiMaps.toLowerCase();
            for (int i = 0; i < PENANDA_INJEKSI.length; i++) {
                if (kecil.contains(PENANDA_INJEKSI[i])) {
                    hasil.add(new Temuan("Injeksi", PENANDA_INJEKSI[i]));
                }
            }
        }
        if (isiProp != null) {
            String kecil = isiProp.toLowerCase();
            for (int i = 0; i < PENANDA_EMULATOR.length; i++) {
                if (kecil.contains(PENANDA_EMULATOR[i])) {
                    hasil.add(new Temuan("Emulator", PENANDA_EMULATOR[i]));
                }
            }
        }
        return hasil;
    }

    /** Tingkat risiko dari jumlah temuan: Aman / Waspada / Berbahaya. */
    public static String nilaiRisiko(List<Temuan> temuan) {
        if (temuan == null || temuan.size() == 0) return "Aman";
        boolean injeksi = false;
        for (int i = 0; i < temuan.size(); i++) {
            if ("Injeksi".equals(temuan.get(i).jenis)) injeksi = true;
        }
        if (injeksi) return "Berbahaya";
        return "Waspada";
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(List<String> rantai, List<Temuan> temuan) {
        int generasi = rantai == null ? 0 : rantai.size();
        boolean rantaiOk = periksaRantai(rantai);
        String risiko = nilaiRisiko(temuan);
        int jumlah = temuan == null ? 0 : temuan.size();
        return "Rantai kunci: " + generasi + " generasi, "
                + (rantaiOk ? "utuh" : "rusak") + ". Lingkungan: " + risiko
                + " (" + jumlah + " temuan).";
    }

    // ==================================================================
    // Pemeriksaan memori asli (baca /proc/self/maps + /proc/self/mem)
    // ==================================================================

    /** Hasil pemeriksaan memori asli. */
    public static class HasilMemori {
        /** False bila sistem menolak membaca memori (bukan berarti aman). */
        public boolean dapatDiperiksa = false;
        public List<Temuan> temuan = new ArrayList<Temuan>();
        public int segmenDiperiksa = 0;
        public String toString() {
            if (!dapatDiperiksa) {
                return "Memori tidak dapat diperiksa (izin sistem menolak pembacaan).";
            }
            StringBuilder b = new StringBuilder();
            b.append("Memori diperiksa: ").append(segmenDiperiksa).append(" segmen. ");
            if (temuan.size() == 0) {
                b.append("Tidak ada temuan.");
            } else {
                b.append(temuan.size()).append(" temuan.");
            }
            return b.toString();
        }
    }

    /**
     * Periksa memori proses sendiri secara asli: untuk tiap segmen
     * berkas yang bisa dieksekusi (r-xp), bandingkan bita di memori
     * (/proc/self/mem) dengan bita berkas yang sama di disk. Bila
     * berbeda, kode di memori sudah diubah setelah dimuat (hook /
     * injeksi). Juga laporkan pemetaan anonim RWX dan pustaka .so
     * dari luar jalur tepercaya, serta TracerPid (debugger menempel).
     * Membaca /proc/self/mem di sebagian perangkat ditolak SELinux;
     * saat itu dapatDiperiksa=false dan temuan berisi alasannya.
     */
    public static HasilMemori periksaMemoriAsli() {
        HasilMemori hasil = new HasilMemori();
        try {
            String maps = bacaBerkasTeks(new File("/proc/self/maps"));
            String status = bacaBerkasTeks(new File("/proc/self/status"));
            if (maps == null || maps.length() == 0) {
                hasil.temuan.add(new Temuan("Memori", "/proc/self/maps tidak bisa dibaca"));
                return hasil;
            }
            int tracer = tracerPid(status);
            if (tracer != 0) {
                hasil.temuan.add(new Temuan("Pelacak", "TracerPid=" + tracer));
            }
            RandomAccessFile mem = null;
            boolean memBisaDibaca = false;
            try {
                mem = new RandomAccessFile("/proc/self/mem", "r");
                memBisaDibaca = true;
            } catch (Throwable t) {
                hasil.temuan.add(new Temuan("Memori",
                        "tidak dapat dibaca (izin sistem): " + String.valueOf(t.getMessage())));
            }
            String[] baris = maps.split("\n");
            for (int i = 0; i < baris.length; i++) {
                String b = baris[i].trim();
                if (b.length() == 0) continue;
                String[] bagian = b.split("\\s+");
                if (bagian.length < 5) continue;
                String alamat = bagian[0];
                String izin = bagian[1];
                String offset = bagian[2];
                String jalur = bagian.length >= 6 ? bagian[5] : "";
                int strip = alamat.indexOf('-');
                if (strip <= 0) continue;
                long awal;
                try {
                    awal = Long.parseLong(alamat.substring(0, strip), 16);
                    Long.parseLong(offset, 16);
                } catch (Throwable t) {
                    continue;
                }
                boolean bisaEksekusi = izin.length() >= 3 && izin.charAt(2) == 'x';
                boolean bisaTulis = izin.length() >= 2 && izin.charAt(1) == 'w';
                boolean privat = izin.length() >= 4 && izin.charAt(3) == 'p';
                // Pemetaan anonim yang bisa dieksekusi + ditulis = kode injeksi.
                if (bisaEksekusi && bisaTulis && privat && jalur.length() == 0) {
                    hasil.temuan.add(new Temuan("Memori",
                            "pemetaan anonim RWX di " + alamat));
                    continue;
                }
                if (jalur.length() == 0) continue;
                if (jalur.startsWith("/memfd:")) {
                    hasil.temuan.add(new Temuan("Pustaka Asing", jalur));
                    continue;
                }
                if (jalur.endsWith(".so") && !jalurTepercaya(jalur)) {
                    hasil.temuan.add(new Temuan("Pustaka Asing", jalur));
                }
                if (jalur.endsWith(" (deleted)")) {
                    hasil.temuan.add(new Temuan("Pustaka Asing", jalur));
                }
                // Bandingkan isi memori dengan berkas di disk.
                if (memBisaDibaca && bisaEksekusi && privat && jalur.startsWith("/")
                        && hasil.segmenDiperiksa < BATAS_SEGMEN_MEMORI) {
                    int banding = bandingkanSegmen(mem, awal, offset, jalur);
                    if (banding == 1) {
                        hasil.segmenDiperiksa++;
                    } else if (banding == 2) {
                        hasil.segmenDiperiksa++;
                        hasil.temuan.add(new Temuan("Memori",
                                "isi memori berbeda dari disk: " + jalur + " @" + alamat));
                    }
                }
            }
            if (mem != null) {
                try {
                    mem.close();
                } catch (Throwable ignored) {
                }
            }
            hasil.dapatDiperiksa = memBisaDibaca;
            return hasil;
        } catch (Throwable t) {
            hasil.temuan.add(new Temuan("Memori", "gagal: " + String.valueOf(t.getMessage())));
            return hasil;
        }
    }

    /**
     * Bandingkan BATAS_BANDING_MEMORI bita pertama segmen di memori
     * dengan berkas di disk pada offset yang sama. Mengembalikan 0
     * bila berkas tidak bisa dibandingkan, 1 bila sama, 2 bila beda.
     */
    private static int bandingkanSegmen(RandomAccessFile mem, long awal,
            String offsetHeks, String jalur) {
        try {
            long offBerkas = Long.parseLong(offsetHeks, 16);
            File f = new File(jalur);
            if (!f.isFile() || !f.canRead()) return 0;
            long sisaBerkas = f.length() - offBerkas;
            if (sisaBerkas <= 0) return 0;
            int jumlah = (int) Math.min(BATAS_BANDING_MEMORI, sisaBerkas);
            byte[] dariMem = new byte[jumlah];
            byte[] dariDisk = new byte[jumlah];
            mem.seek(awal);
            int bacaMem = 0;
            while (bacaMem < jumlah) {
                int n = mem.read(dariMem, bacaMem, jumlah - bacaMem);
                if (n <= 0) break;
                bacaMem += n;
            }
            if (bacaMem < jumlah) return 0;
            RandomAccessFile disk = new RandomAccessFile(f, "r");
            try {
                disk.seek(offBerkas);
                int bacaDisk = 0;
                while (bacaDisk < jumlah) {
                    int n = disk.read(dariDisk, bacaDisk, jumlah - bacaDisk);
                    if (n <= 0) break;
                    bacaDisk += n;
                }
                if (bacaDisk < jumlah) return 0;
            } finally {
                disk.close();
            }
            for (int i = 0; i < jumlah; i++) {
                if (dariMem[i] != dariDisk[i]) return 2;
            }
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean jalurTepercaya(String jalur) {
        for (int i = 0; i < AWALAN_SO_TEPERCAYA.length; i++) {
            if (jalur.startsWith(AWALAN_SO_TEPERCAYA[i])) return true;
        }
        return false;
    }

    private static int tracerPid(String isiStatus) {
        if (isiStatus == null) return 0;
        String[] baris = isiStatus.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].startsWith("TracerPid:")) {
                try {
                    return Integer.parseInt(baris[i].substring(10).trim());
                } catch (Throwable t) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String bacaBerkasTeks(File f) {
        try {
            if (f == null || !f.isFile()) return null;
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[8192];
                StringBuilder b = new StringBuilder();
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.append(new String(buf, 0, n, "UTF-8"));
                }
                return b.toString();
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================================================================
    // Kunci chip (AndroidKeyStore, lewat refleksi supaya bisa diuji JVM)
    // ==================================================================

    /**
     * Status kunci chip: "Tidak tersedia" (bukan Android / tanpa
     * AndroidKeyStore), "Perangkat keras" (kunci tersimpan di chip
     * keamanan), atau "Perangkat lunak" (KeyStore ada tapi kunci
     * tidak di dalam perangkat keras).
     */
    public static String statusKunciChip() {
        return statusKunciChip(ALIAS_KUNCI_CHIP);
    }

    public static String statusKunciChip(String alias) {
        try {
            Object ks = muatKeyStoreAndroid();
            if (ks == null) return "Tidak tersedia";
            Method berisi = ks.getClass().getMethod("containsAlias", String.class);
            boolean ada = Boolean.TRUE.equals(berisi.invoke(ks, alias));
            if (!ada) {
                if (!buatKunciChip(alias)) return "Perangkat lunak";
            }
            Boolean keras = kunciDiPerangkatKeras(ks, alias);
            if (keras == null) return "Perangkat lunak";
            return keras.booleanValue() ? "Perangkat keras" : "Perangkat lunak";
        } catch (Throwable t) {
            return "Tidak tersedia";
        }
    }

    /** Muat AndroidKeyStore lewat refleksi; null bila tidak ada. */
    private static Object muatKeyStoreAndroid() {
        try {
            Class<?> kelas = Class.forName("java.security.KeyStore");
            Method dapatkan = kelas.getMethod("getInstance", String.class);
            Object ks = dapatkan.invoke(null, "AndroidKeyStore");
            Method muat = kelas.getMethod("load", java.security.KeyStore.LoadStoreParameter.class);
            muat.invoke(ks, new Object[]{null});
            return ks;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Buat kunci AES-256 GCM di AndroidKeyStore (tanpa otentikasi pengguna). */
    public static boolean buatKunciChip(String alias) {
        try {
            Class<?> kg = Class.forName("javax.crypto.KeyGenerator");
            Method dapatkan = kg.getMethod("getInstance", String.class, String.class);
            Object gen = dapatkan.invoke(null, "AES", "AndroidKeyStore");
            Class<?> builderKelas = Class.forName("android.security.keystore.KeyGenParameterSpec$Builder");
            Class<?> propKelas = Class.forName("android.security.keystore.KeyProperties");
            int tujuan = propKelas.getField("PURPOSE_ENCRYPT").getInt(null)
                    | propKelas.getField("PURPOSE_DECRYPT").getInt(null);
            Object builder = builderKelas.getConstructor(String.class, int.class)
                    .newInstance(alias, Integer.valueOf(tujuan));
            builderKelas.getMethod("setBlockModes", String[].class)
                    .invoke(builder, new Object[]{new String[]{"GCM"}});
            builderKelas.getMethod("setEncryptionPaddings", String[].class)
                    .invoke(builder, new Object[]{new String[]{"NOPADDING"}});
            builderKelas.getMethod("setKeySize", int.class)
                    .invoke(builder, Integer.valueOf(256));
            builderKelas.getMethod("setUserAuthenticationRequired", boolean.class)
                    .invoke(builder, Boolean.FALSE);
            Object spec = builderKelas.getMethod("build").invoke(builder);
            kg.getMethod("init", java.security.spec.AlgorithmParameterSpec.class)
                    .invoke(gen, spec);
            kg.getMethod("generateKey").invoke(gen);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True bila kunci alias tersimpan di dalam perangkat keras (StrongBox/TEE). */
    private static Boolean kunciDiPerangkatKeras(Object ks, String alias) {
        try {
            Method dapatkanKunci = ks.getClass().getMethod("getKey", String.class, char[].class);
            Object kunci = dapatkanKunci.invoke(ks, alias, null);
            if (kunci == null) return null;
            Class<?> pabrikKelas = Class.forName("java.security.KeyFactory");
            Method dapatkanPabrik = pabrikKelas.getMethod("getInstance", String.class, String.class);
            Object pabrik = dapatkanPabrik.invoke(null,
                    ((java.security.Key) kunci).getAlgorithm(), "AndroidKeyStore");
            Class<?> infoKelas = Class.forName("android.security.keystore.KeyInfo");
            Object info = pabrikKelas.getMethod("getKeySpec",
                    java.security.Key.class, Class.class).invoke(pabrik, kunci, infoKelas);
            if (info == null) return null;
            Method diDalam = infoKelas.getMethod("isInsideSecureHardware");
            Object hasil = diDalam.invoke(info);
            return (Boolean) hasil;
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.security.Key kunciChip(String alias) {
        try {
            Object ks = muatKeyStoreAndroid();
            if (ks == null) return null;
            Method dapatkanKunci = ks.getClass().getMethod("getKey", String.class, char[].class);
            Object kunci = dapatkanKunci.invoke(ks, alias, null);
            return (java.security.Key) kunci;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Bungkus kunci brankas (32 bita) dengan kunci chip: keluaran =
     * IV 12 bita + ciphertext AES-GCM. Kunci brankas tidak pernah
     * tersimpan polos; tanpa chip, pembungkusan gagal (null).
     */
    public static byte[] bungkusKunciBrankas(String alias, byte[] kunciBrankas) {
        try {
            if (kunciBrankas == null) return null;
            java.security.Key kunci = kunciChip(alias);
            if (kunci == null) {
                if (!buatKunciChip(alias)) return null;
                kunci = kunciChip(alias);
                if (kunci == null) return null;
            }
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, kunci, new GCMParameterSpec(128, iv));
            byte[] sandi = c.doFinal(kunciBrankas);
            byte[] keluar = new byte[iv.length + sandi.length];
            System.arraycopy(iv, 0, keluar, 0, iv.length);
            System.arraycopy(sandi, 0, keluar, iv.length, sandi.length);
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Buka bungkusan kunci brankas (null bila gagal / chip tidak ada). */
    public static byte[] bukaBungkusanKunci(String alias, byte[] paket) {
        try {
            if (paket == null || paket.length < 13) return null;
            java.security.Key kunci = kunciChip(alias);
            if (kunci == null) return null;
            byte[] iv = new byte[12];
            System.arraycopy(paket, 0, iv, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, kunci, new GCMParameterSpec(128, iv));
            return c.doFinal(paket, 12, paket.length - 12);
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================================================================
    // Rotasi kunci penandatanganan APK skema v3 (apksig, silsilah asli)
    // ==================================================================

    /** Satu pasangan kunci + sertifikat (dari keystore proyek). */
    public static class KunciMasuk {
        public PrivateKey kunci;
        public X509Certificate sertifikat;
        public String alias = "";
    }

    /** Muat pasangan kunci pertama dari berkas PKCS12. */
    public static KunciMasuk muatKunciP12(File p12, String sandi) {
        try {
            if (p12 == null || !p12.isFile() || sandi == null) return null;
            KeyStore ks = KeyStore.getInstance("PKCS12");
            FileInputStream in = new FileInputStream(p12);
            try {
                ks.load(in, sandi.toCharArray());
            } finally {
                in.close();
            }
            Enumeration<String> en = ks.aliases();
            if (!en.hasMoreElements()) return null;
            String alias = en.nextElement();
            KunciMasuk k = new KunciMasuk();
            k.alias = alias;
            k.kunci = (PrivateKey) ks.getKey(alias, sandi.toCharArray());
            k.sertifikat = (X509Certificate) ks.getCertificate(alias);
            if (k.kunci == null || k.sertifikat == null) return null;
            return k;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Muat kunci dari bita P12 (dipakai untuk kunci baru hasil buatP12). */
    public static KunciMasuk muatKunciP12(byte[] p12, String sandi) {
        try {
            if (p12 == null || sandi == null) return null;
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(p12), sandi.toCharArray());
            Enumeration<String> en = ks.aliases();
            if (!en.hasMoreElements()) return null;
            String alias = en.nextElement();
            KunciMasuk k = new KunciMasuk();
            k.alias = alias;
            k.kunci = (PrivateKey) ks.getKey(alias, sandi.toCharArray());
            k.sertifikat = (X509Certificate) ks.getCertificate(alias);
            if (k.kunci == null || k.sertifikat == null) return null;
            return k;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Buat silsilah rotasi: kunci lama menandatangani bukti bahwa
     * kunci baru adalah penerusnya (proof-of-rotation skema v3).
     * Mengembalikan silsilah, atau null bila gagal.
     */
    public static SigningCertificateLineage buatSilsilahRotasi(
            PrivateKey kunciLama, X509Certificate sertLama,
            PrivateKey kunciBaru, X509Certificate sertBaru, int minSdk) {
        try {
            if (kunciLama == null || sertLama == null || kunciBaru == null || sertBaru == null) {
                return null;
            }
            SigningCertificateLineage.SignerCapabilities caps =
                    new SigningCertificateLineage.SignerCapabilities.Builder().build();
            SigningCertificateLineage.SignerConfig lama =
                    new SigningCertificateLineage.SignerConfig.Builder(
                            new KeyConfig.Jca(kunciLama), sertLama).build();
            SigningCertificateLineage.SignerConfig baru =
                    new SigningCertificateLineage.SignerConfig.Builder(
                            new KeyConfig.Jca(kunciBaru), sertBaru).build();
            // Rotasi kunci v3 hanya ada sejak Android 9 (API 28);
            // silsilah di bawah itu tidak dihormati perangkat,
            // jadi jepit ke 28 bila proyek menarget lebih rendah.
            int sdkSilsilah = Math.max(minSdk, MIN_SDK_ROTASI_V3);
            return new SigningCertificateLineage.Builder(lama, baru)
                    .setMinSdkVersion(sdkSilsilah)
                    .setOriginalCapabilities(caps)
                    .setNewCapabilities(caps)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Sertifikat dalam silsilah, dari yang tertua ke yang terbaru (hash SHA-256). */
    public static List<String> sertifikatSilsilah(SigningCertificateLineage silsilah) {
        List<String> hasil = new ArrayList<String>();
        try {
            if (silsilah == null) return hasil;
            List<X509Certificate> daftar = silsilah.getCertificatesInLineage();
            for (int i = 0; i < daftar.size(); i++) {
                hasil.add(sha256Hex(daftar.get(i).getEncoded()));
            }
        } catch (Throwable ignored) {
        }
        return hasil;
    }

    /**
     * Tandatangani APK dengan rotasi kunci v3: v3 memakai kunci baru
     * beserta silsilahnya, sedangkan v1/v2 tetap memakai kunci lama
     * supaya perangkat di bawah MIN_SDK_ROTASI_V3 masih menerima
     * pembaruan. Mengembalikan null bila sukses, atau pesan galat.
     */
    public static String tandatanganiDenganRotasi(File apkMentah, File apkKeluar,
            PrivateKey kunciLama, X509Certificate sertLama,
            PrivateKey kunciBaru, X509Certificate sertBaru,
            SigningCertificateLineage silsilah, int minSdk) {
        try {
            if (apkMentah == null || !apkMentah.isFile() || apkKeluar == null
                    || kunciLama == null || sertLama == null
                    || kunciBaru == null || sertBaru == null || silsilah == null) {
                return "Berkas APK atau kunci rotasi tidak lengkap.";
            }
            // JANGAN panggil setLineageForMinSdkVersion di sini:
            // itu membuat konfigurasi penandatangan "bertarget
            // SDK" (untuk rotasi v3.1 ke rilis lebih baru), dan
            // apksig menolak bila rotation-min-sdk >= SDK target
            // pertama. Untuk rotasi awal, silsilah cukup lewat
            // setSigningCertificateLineage di bawah; mesin apksig
            // sendiri yang membuat konfigurasi bertarget dari
            // kunci baru dengan SDK = rotation-min-sdk.
            // Kedua penandatangan WAJIB disertakan: kunci lama
            // (tertua di silsilah) dipakai v1/v2 dan v3 untuk
            // perangkat lama, kunci baru dipakai v3 sejak
            // rotation-min-sdk. Tanpa kunci lama apksig menolak:
            // "oldest signer in the lineage is missing".
            ApkSigner.SignerConfig scLama = new ApkSigner.SignerConfig.Builder(
                    "pembuat-lama", kunciLama, Collections.singletonList(sertLama))
                    .build();
            ApkSigner.SignerConfig scBaru = new ApkSigner.SignerConfig.Builder(
                    "pembuat", kunciBaru, Collections.singletonList(sertBaru))
                    .build();
            java.util.List<ApkSigner.SignerConfig> daftar =
                    new java.util.ArrayList<ApkSigner.SignerConfig>();
            daftar.add(scLama);
            daftar.add(scBaru);
            new ApkSigner.Builder(daftar)
                    .setInputApk(apkMentah)
                    .setOutputApk(apkKeluar)
                    .setMinSdkVersion(minSdk)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .setSigningCertificateLineage(silsilah)
                    .setMinSdkVersionForRotation(MIN_SDK_ROTASI_V3)
                    .build()
                    .sign();
            return null;
        } catch (Throwable t) {
            return "Gagal menandatangani dengan rotasi v3: " + String.valueOf(t.getMessage());
        }
    }

    /**
     * Baca silsilah dari APK yang sudah ditandatangani v3.
     * Mengembalikan hash SHA-256 sertifikat dari yang tertua ke yang
     * terbaru; daftar kosong bila APK tidak memakai rotasi v3.
     */
    public static List<String> bacaSilsilah(File apk) {
        List<String> hasil = new ArrayList<String>();
        try {
            if (apk == null || !apk.isFile()) return hasil;
            SigningCertificateLineage silsilah =
                    SigningCertificateLineage.readFromApkFile(apk);
            return sertifikatSilsilah(silsilah);
        } catch (Throwable ignored) {
            return hasil;
        }
    }

    /** Periksa APK: terverifikasi + memakai v3 + silsilahnya. */
    public static String periksaRotasi(File apk) {
        try {
            if (apk == null || !apk.isFile()) return "APK tidak ditemukan.";
            ApkVerifier.Result r = new ApkVerifier.Builder(apk).build().verify();
            if (!r.isVerified()) return "APK tidak terverifikasi.";
            List<String> silsilah = bacaSilsilah(apk);
            StringBuilder b = new StringBuilder();
            b.append("Terverifikasi: v1=").append(r.isVerifiedUsingV1Scheme())
                    .append(", v2=").append(r.isVerifiedUsingV2Scheme())
                    .append(", v3=").append(r.isVerifiedUsingV3Scheme())
                    .append(". Silsilah: ").append(silsilah.size()).append(" sertifikat.");
            return b.toString();
        } catch (Throwable t) {
            return "Gagal memeriksa APK: " + String.valueOf(t.getMessage());
        }
    }
}
