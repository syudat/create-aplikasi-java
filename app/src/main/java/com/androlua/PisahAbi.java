package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Pemisah arsitektur 64 bit / 32 bit (perbaikan 5.8).
 *
 * Satu-satunya tempat yang memetakan nama berkas APK pisah ke ABI:
 *  - "nama-64bit.apk" menyimpan arm64-v8a dan MEMBUANG armeabi-v7a.
 *  - "nama-32bit.apk" menyimpan armeabi-v7a dan MEMBUANG arm64-v8a.
 *
 * Temuan penyelidikan 2026-11-27 (verifikasi sendiri, bukan percaya
 * mentah-mentah): dugaan "argumen ABI tertukar" di Penandatangan
 * SALAH — parameter ketiganya adalah ABI yang DIBUANG, jadi
 * -64bit membuang armeabi-v7a dan -32bit membuang arm64-v8a sudah
 * benar. Cacat NYATA yang ditemukan:
 *  (a) hasil pisah tidak pernah divalidasi: bila APK mentah hanya
 *      membawa pustaka native satu ABI (misalnya proyek menambah
 *      .so sendiri hanya untuk arm64-v8a), APK pisah untuk ABI
 *      satunya tetap dibuat tanpa satu pun pustaka native — APK itu
 *      terpasang, lalu aplikasi menutup sendiri saat memuat pustaka
 *      (UnsatisfiedLinkError). Inilah "error saat mencentang 64/32
 *      bit" yang dilaporkan pengguna.
 *  (b) bacaan bawaan centang di CreateProjectActivity memakai satu
 *      blok try untuk tiga kunci preferensi sekaligus, sehingga satu
 *      nilai preferensi yang rusak (tipe salah) menggagalkan bacaan
 *      dua kunci lainnya.
 *  (c) centang tidak dipertahankan saat layar dibuat ulang (misalnya
 *      ganti bahasa) — selalu kembali ke bawaan.
 * Kelas ini murni Java (tanpa API Android) supaya bisa diuji di
 * komputer; Penandatangan memakai pemetaan dan pemeriksaannya.
 */
public class PisahAbi {

    /** ABI 64 bit. */
    public static final String ABI_64 = "arm64-v8a";

    /** ABI 32 bit. */
    public static final String ABI_32 = "armeabi-v7a";

    /** Akhiran berkas APK 64 bit. */
    public static final String AKHIR_64 = "-64bit.apk";

    /** Akhiran berkas APK 32 bit. */
    public static final String AKHIR_32 = "-32bit.apk";

    /** ABI yang DISIMPAN untuk berkas pisah (null bila bukan berkas pisah). */
    public static String abiYangDisimpan(String namaBerkas) {
        if (namaBerkas == null) return null;
        if (namaBerkas.endsWith(AKHIR_64)) return ABI_64;
        if (namaBerkas.endsWith(AKHIR_32)) return ABI_32;
        return null;
    }

    /** ABI yang DIBUANG untuk berkas pisah (null bila bukan berkas pisah). */
    public static String abiYangDibuang(String namaBerkas) {
        if (namaBerkas == null) return null;
        if (namaBerkas.endsWith(AKHIR_64)) return ABI_32;
        if (namaBerkas.endsWith(AKHIR_32)) return ABI_64;
        return null;
    }

    /** Nama berkas APK pisah dari nama dasar + ABI yang disimpan. */
    public static String namaBerkas(String dasar, String abiDisimpan) {
        if (dasar == null) dasar = "aplikasi";
        if (ABI_64.equals(abiDisimpan)) return dasar + AKHIR_64;
        if (ABI_32.equals(abiDisimpan)) return dasar + AKHIR_32;
        return dasar + ".apk";
    }

    /** True bila entri zip harus dibuang untuk APK pisah tertentu. */
    public static boolean buangEntri(String namaEntri, String buangAbi) {
        if (namaEntri == null || buangAbi == null) return false;
        return namaEntri.startsWith("lib/" + buangAbi + "/");
    }

    /**
     * Daftar entri yang tersisa sesudah entri ABI yang dibuang
     * disingkirkan (dipakai pra-validasi sebelum penandatanganan:
     * masalah pisah ketahuan sebelum satu pun APK ditulis).
     */
    public static List<String> entriSetelahBuang(List<String> entriMentah,
                                                 String buangAbi) {
        List<String> hasil = new ArrayList<String>();
        if (entriMentah == null) return hasil;
        for (int i = 0; i < entriMentah.size(); i++) {
            if (!buangEntri(entriMentah.get(i), buangAbi)) hasil.add(entriMentah.get(i));
        }
        return hasil;
    }

    /** Keterangan arsitektur untuk satu nama berkas ("(64 bit, ...)" atau ""). */
    public static String label(String namaBerkas) {
        String simpan = abiYangDisimpan(namaBerkas);
        if (ABI_64.equals(simpan)) return "  (64 bit, arm64-v8a)";
        if (ABI_32.equals(simpan)) return "  (32 bit, armeabi-v7a)";
        return "";
    }

    /** Daftar ABI yang muncul sebagai lib/<abi>/ di daftar entri. */
    public static List<String> abiDiEntri(List<String> entri) {
        List<String> hasil = new ArrayList<String>();
        if (entri == null) return hasil;
        for (int i = 0; i < entri.size(); i++) {
            String n = entri.get(i);
            if (n == null || !n.startsWith("lib/")) continue;
            int garis = n.indexOf('/', 4);
            if (garis <= 4) continue;
            String abi = n.substring(4, garis);
            if (!hasil.contains(abi)) hasil.add(abi);
        }
        return hasil;
    }

    /**
     * Periksa hasil pisah: bila APK mentah membawa pustaka native,
     * tetapi hasil pisah tidak membawa satu pun pustaka native, atau
     * tidak membawa pustaka ABI yang disimpannya, kembalikan pesan
     * masalah; bila tidak ada masalah, kembalikan "". Proyek tanpa
     * pustaka native (Java murni) selalu lolos — kedua APK pisahnya
     * identik isinya dan jalan di semua perangkat.
     */
    public static String periksaHasil(List<String> entriMentah,
                                     List<String> entriHasil, String abiDisimpan) {
        if (abiDisimpan == null) return "";
        List<String> abiMentah = abiDiEntri(entriMentah);
        if (abiMentah.size() == 0) return "";
        List<String> abiHasil = abiDiEntri(entriHasil);
        String namaApk = ABI_64.equals(abiDisimpan) ? "64 bit" : "32 bit";
        if (abiHasil.contains(abiDisimpan)) return "";
        if (abiHasil.size() == 0) {
            return "APK " + namaApk + " tidak membawa pustaka native sama sekali"
                    + " padahal APK mentah membawa " + abiMentah
                    + "; tambahkan pustaka " + abiDisimpan
                    + " ke proyek atau matikan arsitektur terpisah.";
        }
        return "APK " + namaApk + " tidak membawa pustaka native " + abiDisimpan
                + " padahal APK mentah membawa " + abiMentah
                + "; tambahkan pustaka " + abiDisimpan
                + " ke proyek atau matikan arsitektur terpisah.";
    }
}
