package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
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

    // ---- Layar Arsitektur Proyek Lama (butir 16, penyelesaian 5.8) ----
    //
    // Model yang dipakai build (tidak diubah di sini): SATU boolean pisah.
    // split_abi di init.lua, ditimpa arsitekturTerpisah di settings.gradle
    // bila ada. Bila pisah, Penandatangan membuat KEDUA APK sekaligus
    // (-64bit dan -32bit); bila tidak, satu APK universal. Jalur build
    // (BangunProyek/Penandatangan) tidak mengenal "hanya satu varian",
    // jadi layar ini menyimpan pilihan per varian apa adanya di init.lua
    // (kunci arsitektur_64/arsitektur_32) dan menurunkan pisahnya sebagai
    // (64 DAN 32). Pilihan per varian sengaja TIDAK disimpan di
    // settings.gradle: BangunProyek.simpanSetelanProyek() menulis ulang
    // berkas itu dari Setelan baru setiap build berhasil, sehingga kunci
    // per varian di sana akan terhapus. init.lua tidak pernah ditulis
    // ulang oleh build, jadi pilihan layar ini aman di sana.

    /** Kunci init.lua untuk pilihan 64 bit di layar arsitektur. */
    public static final String KUNCI_INIT_64 = "arsitektur_64";

    /** Kunci init.lua untuk pilihan 32 bit di layar arsitektur. */
    public static final String KUNCI_INIT_32 = "arsitektur_32";

    /** Penanda bawaan "kunci tidak ada" untuk membedakan dari nilai asli. */
    private static final String BAWAAN_HILANG = "__HILANG__";

    /** Pilihan arsitektur satu proyek seperti dibaca dari berkasnya. */
    public static class Pilihan {
        /** Centang 64 bit (arm64-v8a). */
        public boolean abi64 = false;
        /** Centang 32 bit (armeabi-v7a). */
        public boolean abi32 = false;
        /** True bila init.lua menyimpan kunci arsitektur_64/32. */
        public boolean adaPilihan = false;
        /** True bila settings.gradle memang ada dan terbaca. */
        public boolean adaSetelan = false;
        /** True bila settings.gradle memuat kunci arsitekturTerpisah. */
        public boolean adaPisahSetelan = false;
        /** True bila init.lua memuat kunci split_abi. */
        public boolean adaPisahInit = false;
        /** Nilai pisah efektif yang dipakai build (settings.gradle menimpa init.lua). */
        public boolean pisahEfektif = false;
    }

    /**
     * True bila build terpisah aktif: hanya bila KEDUA varian dicentang.
     * Satu varian saja tidak menghasilkan APK terpisah satu varian —
     * build memakai satu APK universal (lihat keterangan di atas).
     */
    public static boolean pisah(boolean abi64, boolean abi32) {
        return abi64 && abi32;
    }

    /** True bila pilihan layar cocok dengan nilai pisah efektif di berkas. */
    public static boolean cocok(Pilihan p) {
        if (p == null) return true;
        return pisah(p.abi64, p.abi32) == p.pisahEfektif;
    }

    /**
     * Baca pilihan arsitektur satu proyek. settings.gradle menimpa
     * init.lua untuk nilai pisah efektif (aturan yang sama dengan
     * BangunProyek). Tiap kunci dibaca sendiri-sendiri lewat
     * SetelanProyek.benar (toleran True/1/ya, perbaikan 5.8), jadi satu
     * nilai rusak tidak menggagalkan kunci lainnya. Bila init.lua belum
     * menyimpan pilihan per varian, kedua centang mengikuti pisah efektif.
     */
    public static Pilihan baca(File dirProyek) {
        Pilihan p = new Pilihan();
        if (dirProyek == null) return p;
        SetelanProyek.Setelan sp = SetelanProyek.baca(dirProyek);
        p.adaSetelan = sp.dariBerkas;
        p.adaPisahSetelan = sp.adaArsitekturTerpisah;
        String init = bacaTeks(new File(dirProyek, "init.lua"));
        boolean pisahInit = false;
        if (init != null) {
            p.adaPisahInit = !BAWAAN_HILANG.equals(
                    InfoProyek.bacaKunciTeks(init, "split_abi", BAWAAN_HILANG));
            pisahInit = SetelanProyek.benar(
                    InfoProyek.bacaKunciTeks(init, "split_abi", "false"));
        }
        p.pisahEfektif = p.adaPisahSetelan ? sp.arsitekturTerpisah : pisahInit;
        if (init != null) {
            boolean ada64 = !BAWAAN_HILANG.equals(
                    InfoProyek.bacaKunciTeks(init, KUNCI_INIT_64, BAWAAN_HILANG));
            boolean ada32 = !BAWAAN_HILANG.equals(
                    InfoProyek.bacaKunciTeks(init, KUNCI_INIT_32, BAWAAN_HILANG));
            p.adaPilihan = ada64 || ada32;
            if (p.adaPilihan) {
                String bawaan = p.pisahEfektif ? "true" : "false";
                p.abi64 = SetelanProyek.benar(
                        InfoProyek.bacaKunciTeks(init, KUNCI_INIT_64, bawaan));
                p.abi32 = SetelanProyek.benar(
                        InfoProyek.bacaKunciTeks(init, KUNCI_INIT_32, bawaan));
                return p;
            }
        }
        p.abi64 = p.pisahEfektif;
        p.abi32 = p.pisahEfektif;
        return p;
    }

    /**
     * Tulis pilihan arsitektur ke berkas proyek. init.lua selalu ditulis
     * (split_abi + arsitektur_64/32, di tempat, komentar dipertahankan);
     * settings.gradle hanya ditulis bila berkasnya SUDAH ada — dan hanya
     * kunci arsitekturTerpisah yang diubah, kunci lain dipertahankan lewat
     * SetelanProyek.baca/tulis. Bila settings.gradle belum ada, berkas
     * itu tidak dibuat: init.lua saja sudah cukup karena tidak ada yang
     * menimpanya. Mengembalikan "" bila berhasil, atau pesan galat
     * (bahasa Indonesia) bila gagal.
     */
    public static String tulis(File dirProyek, boolean abi64, boolean abi32) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        File initFile = new File(dirProyek, "init.lua");
        String init = bacaTeks(initFile);
        if (init == null) return "init.lua tidak ditemukan di folder proyek ini.";
        boolean pisahBaru = pisah(abi64, abi32);
        String baru = tulisKunciTeks(init, "split_abi", pisahBaru ? "true" : "false",
                "-- true = APK terpisah 64 bit dan 32 bit");
        baru = tulisKunciTeks(baru, KUNCI_INIT_64, abi64 ? "true" : "false",
                "-- true = sertakan APK 64 bit (arm64-v8a)");
        baru = tulisKunciTeks(baru, KUNCI_INIT_32, abi32 ? "true" : "false",
                "-- true = sertakan APK 32 bit (armeabi-v7a)");
        if (!tulisTeks(initFile, baru)) return "init.lua tidak bisa ditulis.";
        File setelanFile = new File(dirProyek, SetelanProyek.NAMA_BERKAS);
        if (setelanFile.isFile()) {
            SetelanProyek.Setelan sp = SetelanProyek.baca(dirProyek);
            sp.arsitekturTerpisah = pisahBaru;
            sp.adaArsitekturTerpisah = true;
            if (!SetelanProyek.tulis(dirProyek, sp, sp.adaAntiTamper)) {
                return "settings.gradle tidak bisa ditulis, tetapi init.lua sudah tersimpan.";
            }
        }
        return "";
    }

    /**
     * Ubah satu kunci "kunci = nilai" di dalam teks berkas, di tempat:
     * baris lain (termasuk komentar di belakang nilai) dipertahankan
     * persis; bila kunci belum ada, ditambahkan di akhir dengan komentar
     * yang diberikan (null = tanpa komentar). Baris rusak dilewati.
     * Murni teks, tanpa API Android, supaya bisa diuji di komputer.
     */
    public static String tulisKunciTeks(String isi, String kunci, String nilai,
                                        String komentar) {
        if (isi == null) isi = "";
        if (kunci == null || kunci.length() == 0) return isi;
        if (nilai == null) nilai = "false";
        String[] baris = isi.split("\n", -1);
        boolean ketemu = false;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < baris.length; i++) {
            String asal = baris[i];
            String ganti = asal;
            if (!ketemu) {
                String kode = InfoProyek.buangKomentar(asal);
                String komentarBaris = asal.substring(kode.length());
                String k = kode.trim();
                int eq = k.indexOf('=');
                if (eq > 0 && kunci.equals(k.substring(0, eq).trim())) {
                    int eqKode = kode.indexOf('=');
                    ganti = kode.substring(0, eqKode + 1) + " " + nilai
                            + (komentarBaris.length() == 0 ? "" : " " + komentarBaris);
                    ketemu = true;
                }
            }
            if (i > 0) b.append('\n');
            b.append(ganti);
        }
        if (!ketemu) {
            String barisBaru = kunci + "=" + nilai
                    + (komentar == null ? "" : "       " + komentar);
            if (b.length() > 0 && b.charAt(b.length() - 1) != '\n') b.append('\n');
            b.append(barisBaru).append('\n');
        }
        return b.toString();
    }

    /**
     * Daftar ABI yang membawa pustaka native di proyek: memindai
     * lib/&lt;abi&gt;/ dan app/src/main/jniLibs/&lt;abi&gt;/ sampai menemukan
     * berkas .so. Hanya arm64-v8a dan armeabi-v7a yang dilaporkan.
     */
    public static List<String> abiDiProyek(File dirProyek) {
        List<String> hasil = new ArrayList<String>();
        if (dirProyek == null) return hasil;
        File[] akar = new File[] {
            new File(dirProyek, "lib"),
            new File(dirProyek, "app/src/main/jniLibs")
        };
        String[] abi = new String[] { ABI_64, ABI_32 };
        for (int i = 0; i < abi.length; i++) {
            for (int j = 0; j < akar.length; j++) {
                if (adaSo(new File(akar[j], abi[i]))) {
                    hasil.add(abi[i]);
                    break;
                }
            }
        }
        return hasil;
    }

    private static boolean adaSo(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        File[] isi = dir.listFiles();
        if (isi == null) return false;
        for (int i = 0; i < isi.length; i++) {
            if (isi[i].isDirectory()) {
                if (adaSo(isi[i])) return true;
            } else if (isi[i].getName().endsWith(".so")
                    || isi[i].getName().endsWith(".SO")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pra-validasi build pisah di tingkat proyek (aturan yang sama
     * dengan periksaHasil, perbaikan 5.8, dipakai layar ini saat
     * menyimpan): bila build terpisah aktif tetapi proyek hanya membawa
     * pustaka native satu ABI, kembalikan peringatan; bila tidak ada
     * masalah, kembalikan "". Proyek tanpa pustaka native (Java murni)
     * selalu lolos.
     */
    public static String periksaPisahProyek(File dirProyek, boolean abi64, boolean abi32) {
        if (!pisah(abi64, abi32)) return "";
        List<String> ada = abiDiProyek(dirProyek);
        if (ada.size() == 0) return "";
        if (ada.contains(ABI_64) && ada.contains(ABI_32)) return "";
        boolean hanya64 = ada.contains(ABI_64);
        String adaNama = hanya64 ? "64 bit (arm64-v8a)" : "32 bit (armeabi-v7a)";
        String hilangNama = hanya64 ? "32 bit (armeabi-v7a)" : "64 bit (arm64-v8a)";
        String hilangApk = hanya64 ? "32 bit" : "64 bit";
        return "Peringatan: proyek ini hanya membawa pustaka native " + adaNama
                + "; APK " + hilangApk + " akan dibuat tanpa pustaka native"
                + " dan aplikasi menutup sendiri saat dibuka."
                + " Tambahkan pustaka " + hilangNama + " ke proyek atau matikan salah satu centang.";
    }

    private static String bacaTeks(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            try {
                StringBuilder b = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) b.append(buf, 0, n);
                return b.toString();
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean tulisTeks(File f, String isi) {
        if (f == null || isi == null) return false;
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi);
                w.flush();
            } finally {
                w.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
