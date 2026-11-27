package com.androlua;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

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
 *
 * BATAS JUJUR: pemeriksaan ini membaca POLA TEKS dari peta memori dan
 * properti sistem, bukan integritas memori asli di tingkat pustaka
 * bawaan, dan tidak mengikat kunci ke chip keamanan perangkat keras
 * (butuh KeyStore + kode native). Alat injeksi yang menyembunyikan
 * jejak teksnya bisa lolos dari pemindaian ini.
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
}
