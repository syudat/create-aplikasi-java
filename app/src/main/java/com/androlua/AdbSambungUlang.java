package com.androlua;

/**
 * Mesin keadaan sambung-ulang otomatis debugging nirkabel (murni Java,
 * bisa diuji di JVM). Layanan latar (LayananAdbOtomatis) menggerakkan
 * mesin ini: saat WiFi mati, keadaan MENUNGGU_WIFI; saat WiFi menyala
 * lagi, kembali MENCARI dan menyambung tanpa kode pairing (kunci RSA
 * yang sama tersimpan di AdbKunci, adbd sudah mengenalinya).
 *
 * Penundaan antar percobaan: 15 dtk, 30 dtk, 1 mnt, 2 mnt, lalu
 * mentok 5 mnt; berhasil tersambung mengulang hitungan dari awal.
 */
public class AdbSambungUlang {

    public static final int NONAKTIF = 0;
    public static final int MENUNGGU_WIFI = 1;
    public static final int MENCARI = 2;
    public static final int TERSAMBUNG = 3;
    public static final int TUNGGU_COBA_LAGI = 4;

    public static final long JEDA_AWAL_MS = 15000;
    public static final long JEDA_MAKS_MS = 300000;

    private int keadaan = NONAKTIF;
    private int gagalBeruntun = 0;
    private boolean wifiMenyala = true;

    public int keadaan() { return keadaan; }
    public int gagalBeruntun() { return gagalBeruntun; }
    public boolean apakahWifiMenyala() { return wifiMenyala; }

    /** Aktifkan sambung-ulang otomatis (dipanggil dari layar Debugger). */
    public void aktifkan() {
        gagalBeruntun = 0;
        keadaan = wifiMenyala ? MENCARI : MENUNGGU_WIFI;
    }

    /** Matikan sambung-ulang otomatis (putuskan + hentikan layanan). */
    public void nonaktifkan() {
        keadaan = NONAKTIF;
        gagalBeruntun = 0;
    }

    /** WiFi dimatikan: berhenti mencoba, tunggu WiFi menyala lagi. */
    public void wifiMati() {
        wifiMenyala = false;
        if (keadaan == MENCARI || keadaan == TERSAMBUNG || keadaan == TUNGGU_COBA_LAGI) {
            keadaan = MENUNGGU_WIFI;
        }
    }

    /** WiFi dinyalakan: mulai mencari layanan adb lagi. */
    public void wifiMenyala() {
        wifiMenyala = true;
        if (keadaan == MENUNGGU_WIFI) {
            gagalBeruntun = 0;
            keadaan = MENCARI;
        }
    }

    /** Sambungan adb berhasil: catat tersambung, ulang hitungan gagal. */
    public void tersambung() {
        keadaan = TERSAMBUNG;
        gagalBeruntun = 0;
    }

    /**
     * Sambungan putus/gagal: masuk jeda backoff lalu coba lagi.
     * Mengembalikan jeda (ms) sebelum percobaan berikutnya.
     */
    public long terputus() {
        if (keadaan == NONAKTIF) return 0;
        gagalBeruntun++;
        if (!wifiMenyala) {
            keadaan = MENUNGGU_WIFI;
            return 0;
        }
        keadaan = TUNGGU_COBA_LAGI;
        return jedaCobaLagiMs();
    }

    /** Jeda sebelum percobaan berikutnya (dipakai layanan latar). */
    public long jedaCobaLagiMs() {
        if (gagalBeruntun <= 0) return JEDA_AWAL_MS;
        long jeda = JEDA_AWAL_MS;
        for (int i = 1; i < gagalBeruntun; i++) {
            jeda *= 2;
            if (jeda >= JEDA_MAKS_MS) return JEDA_MAKS_MS;
        }
        return Math.min(jeda, JEDA_MAKS_MS);
    }

    /** Jeda habis: kembali mencari layanan adb. */
    public void cobaLagi() {
        if (keadaan == TUNGGU_COBA_LAGI) keadaan = MENCARI;
    }

    /** Nama keadaan untuk log/pengumuman. */
    public static String namaKeadaan(int keadaan) {
        switch (keadaan) {
            case NONAKTIF: return "nonaktif";
            case MENUNGGU_WIFI: return "menunggu WiFi";
            case MENCARI: return "mencari";
            case TERSAMBUNG: return "tersambung";
            case TUNGGU_COBA_LAGI: return "tunggu coba lagi";
            default: return "?";
        }
    }
}
