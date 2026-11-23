package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Logika murni Perisai Anti Tamper (RASP) untuk aplikasi hasil build.
 *
 * Saat build ditandatangani dengan anti tamper aktif, Penandatangan menulis
 * berkas "assets/perisai.dat" ke dalam APK berisi:
 *   - hash SHA-256 sertifikat penandatangan yang sah, dan
 *   - CRC32 setiap berkas classes*.dex.
 * Saat aplikasi hasil berjalan, PerisaiAplikasi membandingkan keadaan
 * sebenarnya dengan catatan itu. Bila sertifikat diganti orang lain
 * (APK ditandatangani ulang) atau isi dex diubah, aplikasi menolak berjalan.
 *
 * Kelas ini murni Java (tanpa API Android) supaya bisa diuji di komputer.
 */
public class Perisai {

    /** Nama aset catatan perisai di dalam APK hasil. */
    public static final String NAMA_ASET = "assets/perisai.dat";

    /** Penanda pustaka/jejak injeksi yang dicari di peta memori proses. */
    private static final String[] PENANDA_HOOK = {
            "frida", "xposed", "lsposed", "substrate", "gadget", "edxposed", "riru"
    };

    /** SHA-256 dalam heksadesimal huruf kecil. */
    public static String sha256Hex(byte[] data) {
        try {
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

    /** CRC32 satu berkas bita. */
    public static long crc32(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data);
        return c.getValue();
    }

    /** CRC32 isi satu entri zip (dihitung dari isi, bukan header zip). */
    public static long crc32Entri(ZipFile zf, ZipEntry e) throws Exception {
        InputStream in = zf.getInputStream(e);
        try {
            CRC32 c = new CRC32();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) c.update(buf, 0, n);
            return c.getValue();
        } finally {
            in.close();
        }
    }

    /** True bila nama entri adalah berkas dex utama (classes.dex, classes2.dex, ...). */
    public static boolean namaDex(String nama) {
        if (nama == null) return false;
        if (nama.equals("classes.dex")) return true;
        if (!nama.startsWith("classes") || !nama.endsWith(".dex")) return false;
        String tengah = nama.substring(7, nama.length() - 4);
        if (tengah.length() == 0) return false;
        for (int i = 0; i < tengah.length(); i++) {
            if (!Character.isDigit(tengah.charAt(i))) return false;
        }
        return true;
    }

    /** Susun isi perisai.dat dari hash sertifikat + peta CRC dex. */
    public static String buatData(String hashSertifikat, Map<String, Long> crcDex) {
        StringBuilder b = new StringBuilder();
        b.append("v=1\n");
        b.append("cert=").append(hashSertifikat == null ? "" : hashSertifikat).append('\n');
        if (crcDex != null) {
            for (Map.Entry<String, Long> e : crcDex.entrySet()) {
                b.append("dex:").append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
        }
        return b.toString();
    }

    /** Hasil uraian perisai.dat. */
    public static class Data {
        public String hashSertifikat = "";
        public Map<String, Long> crcDex = new LinkedHashMap<String, Long>();
    }

    /** Urai isi perisai.dat. Mengembalikan null bila teks kosong/rusak total. */
    public static Data uraiData(String teks) {
        if (teks == null || teks.trim().length() == 0) return null;
        Data d = new Data();
        String[] baris = teks.split("\n", -1);
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.startsWith("cert=")) {
                d.hashSertifikat = b.substring(5).trim();
            } else if (b.startsWith("dex:")) {
                int eq = b.indexOf('=');
                if (eq > 4) {
                    String nama = b.substring(4, eq).trim();
                    try {
                        d.crcDex.put(nama, Long.parseLong(b.substring(eq + 1).trim()));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        if (d.hashSertifikat.length() == 0 && d.crcDex.isEmpty()) return null;
        return d;
    }

    /** True bila hash sertifikat yang sedang berjalan cocok dengan catatan. */
    public static boolean sertifikatCocok(Data d, String hashAktual) {
        if (d == null || hashAktual == null) return false;
        return d.hashSertifikat.equalsIgnoreCase(hashAktual);
    }

    /**
     * True bila semua CRC dex pada catatan cocok dengan CRC aktual.
     * Dex pada catatan yang hilang dari APK aktual = tidak cocok.
     */
    public static boolean dexCocok(Data d, Map<String, Long> crcAktual) {
        if (d == null || crcAktual == null) return false;
        for (Map.Entry<String, Long> e : d.crcDex.entrySet()) {
            Long aktual = crcAktual.get(e.getKey());
            if (aktual == null || aktual.longValue() != e.getValue().longValue()) return false;
        }
        return true;
    }

    /** Baca nilai TracerPid dari isi /proc/self/status. -1 bila tidak ketemu. */
    public static int tracerPid(String isiStatus) {
        if (isiStatus == null) return -1;
        String[] baris = isiStatus.split("\n", -1);
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.startsWith("TracerPid:")) {
                try {
                    return Integer.parseInt(b.substring("TracerPid:".length()).trim());
                } catch (Throwable t) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Cari penanda injeksi (Frida/Xposed/dsb.) di isi /proc/self/maps.
     * Mengembalikan penanda yang ketemu, atau null bila bersih.
     */
    public static String jejakHook(String isiMaps) {
        if (isiMaps == null) return null;
        String kecil = isiMaps.toLowerCase();
        for (int i = 0; i < PENANDA_HOOK.length; i++) {
            if (kecil.contains(PENANDA_HOOK[i])) return PENANDA_HOOK[i];
        }
        return null;
    }

    /** Baca seluruh aliran menjadi bita (dipakai pemanggil di Android). */
    public static byte[] bacaSemua(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }
}
