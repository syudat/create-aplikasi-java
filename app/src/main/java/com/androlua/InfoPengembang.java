package com.androlua;

/**
 * Data pengembang untuk layar Donasi ke Pengembang (Batch pasca-v2).
 *
 * Murni Java (tanpa import android) supaya logika pesan sapaan + tautan
 * WhatsApp bisa diuji di JRE tanpa perangkat.
 *
 * Data asli dari al kautsar (2026-11-14 malam): nomor WA dari 087794777041
 * (nol depan diganti 62); rekening BRI dari nomor tercetak di buku BRI
 * "0050-01-213070-50-2" (tanda hubung dibuang; 15 digit). HANYA nomor
 * rekening yang diambil dari foto itu — data lain dari foto (alamat,
 * dan lain-lain) TIDAK dipakai dan TIDAK disimpan di sini.
 * Bila Profil Pengembang di Pengaturan sudah diisi, nilai itu yang dipakai
 * (DonasiActivity membaca lewat pakaiProfil di bawah).
 */
public class InfoPengembang {
    public static final String NAMA_PENGEMBANG = "Al Kautsar";

    /** Nomor WA pengembang (dari 087794777041; nol depan diganti 62). */
    public static final String NOMOR_WA = "6287794777041";

    /** Rekening BRI pengembang (dari buku BRI "0050-01-213070-50-2"). */
    public static final String REKENING_BRI = "005001213070502";

    /**
     * Pesan sapaan untuk WhatsApp — teks persis yang disetujui al kautsar.
     */
    public static String pesanSapa(String nama, String kota) {
        String n = nama == null ? "" : nama.trim();
        String k = kota == null ? "" : kota.trim();
        return "Halo Al Kausar, perkenalkan nama saya " + n
                + ". Saya berasal dari " + k
                + ". Saya ingin mengapresiasi. Semangat ya Bang Al Kausar. "
                + "Semoga karya-karyamu bermanfaat untuk semua orang.";
    }

    /** Nama dan kota wajib diisi sebelum membuka WhatsApp. */
    public static boolean namaKotaValid(String nama, String kota) {
        return nama != null && !nama.trim().isEmpty()
                && kota != null && !kota.trim().isEmpty();
    }

    /**
     * Tautan wa.me dengan pesan ter-encode (spasi jadi %20, pola yang sama
     * seperti Pengemas). WhatsApp TIDAK mengizinkan aplikasi lain mengirim
     * pesan otomatis — tautan ini hanya membuka WhatsApp dengan pesan sudah
     * terisi; pengguna menekan Kirim sendiri di WhatsApp.
     */
    public static String tautanWa(String nomorWa, String nama, String kota) {
        try {
            String pesan = pesanSapa(nama, kota);
            String encoded = java.net.URLEncoder.encode(pesan, "UTF-8")
                    .replace("+", "%20");
            return "https://wa.me/" + nomorWa + "?text=" + encoded;
        } catch (Throwable t) {
            return "https://wa.me/" + nomorWa;
        }
    }

    /** Pakai nilai Profil Pengembang bila sudah diisi, kalau tidak bawaan. */
    public static String pakaiProfil(String nilaiProfil, String bawaan) {
        if (nilaiProfil != null && !nilaiProfil.trim().isEmpty()) {
            return nilaiProfil.trim();
        }
        return bawaan;
    }
}
