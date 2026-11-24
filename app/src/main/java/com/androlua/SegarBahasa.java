package com.androlua;

import android.app.Activity;

/**
 * Ganti bahasa langsung tanpa restart (perintah al kautsar 2026-11-24 13:12,
 * Fitur 11): saat bahasa efektif berubah sejak layar dibangun, bangun ulang
 * layar supaya semua teks memakai bahasa baru — tanpa menutup aplikasi.
 *
 * Cara pakai: panggil {@link #periksa(Activity)} di awal onResume setiap
 * Activity (sesudah super.onResume()); bila mengembalikan true, layar sedang
 * dibuat ulang, jadi pemanggil sebaiknya langsung return.
 *
 * Perbandingan memakai TeksUI.kodeAktif (bahasa yang dipakai layar saat
 * dibangun). BahasaActivity TIDAK mengubah kodeAktif saat memilih bahasa —
 * ia hanya menyimpan preferensi — supaya perbandingan di sini mendeteksi
 * perubahan dan memicu recreate di layar pemanggil.
 */
public final class SegarBahasa {
    private SegarBahasa() {
    }

    public static boolean periksa(Activity a) {
        try {
            String efektif = Bahasa.kodeEfektif(
                    PengaturanActivity.baca(a).getString(
                            PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                    java.util.Locale.getDefault().getLanguage());
            if (!efektif.equals(TeksUI.kodeAktif)) {
                TeksUI.kodeAktif = efektif;
                a.recreate();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
