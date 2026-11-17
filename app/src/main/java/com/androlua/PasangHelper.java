package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import java.io.File;

/**
 * Pembantu pasang APK untuk layar-layar Java (Tahap 2E).
 *
 * Mereplikasi dua perilaku main.lua:
 * 1. mintaIzinPasang() — sama seperti pasangApk("") di Lua (dipakai menu
 *    "Izin pasang APK"): bila izin sudah aktif, toast "Izin pasang APK
 *    sudah aktif"; bila belum, tampilkan dialog "Izin pasang belum aktif"
 *    (sama seperti mintaIzinPasangDialog()) dengan tombol "Buka Pengaturan"
 *    yang membuka halaman "Izinkan dari sumber ini".
 * 2. lanjutkanPasangTertunda() — sama seperti blok jalurPasangTertunda di
 *    onResume() Lua: bila pengguna baru kembali dari Pengaturan dan izin
 *    pasang sudah aktif, pasang APK yang tertunda secara otomatis tanpa
 *    perlu menekan "Pasang" lagi. Jalur diambil dari penyimpanan statis
 *    BangunProyek (Tahap 3I; dulu dari jembatan global
 *    ambilJalurPasangTertunda() di main.lua — jalur dibersihkan supaya
 *    hanya dipasang sekali); pemasangan sendiri dijalankan dari Activity
 *    Java yang sedang tampil (foreground) supaya tidak kena batasan
 *    peluncuran Activity dari latar belakang.
 *
 * Dipanggil dari onResume() semua layar Java. Bila tidak ada jalur
 * tertunda, tidak terjadi apa-apa.
 */
public class PasangHelper {

    /** Sama seperti pasangApk("") di Lua: periksa izin pasang APK. */
    public static void mintaIzinPasang(final Activity activity) {
        if (PemasangApk.bisaPasang(activity)) {
            Toast.makeText(activity, "Izin pasang APK sudah aktif", Toast.LENGTH_SHORT).show();
            return;
        }
        // Sama seperti mintaIzinPasangDialog() di Lua.
        new AlertDialog.Builder(activity)
                .setTitle("Izin pasang belum aktif")
                .setMessage("Android 8 ke atas meminta izin \"Pasang aplikasi\" untuk aplikasi ini dulu. "
                        + "Tekan \"Buka Pengaturan\", aktifkan \"Izinkan dari sumber ini\", lalu kembali ke sini. "
                        + "Pemasangan akan lanjut otomatis tanpa perlu menekan \"Pasang\" lagi.")
                .setPositiveButton("Buka Pengaturan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            PemasangApk.mintaIzinPasang(activity);
                        } catch (Throwable ignored) {
                        }
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
        try {
            activity.getWindow().getDecorView().announceForAccessibility(
                    "Izin pasang belum aktif. Tekan Buka Pengaturan, aktifkan Izinkan dari sumber ini, "
                    + "lalu kembali ke sini. Pemasangan akan lanjut otomatis.");
        } catch (Throwable ignored) {
        }
    }

    /**
     * Lanjutkan pemasangan APK yang tertunda izinnya (bila ada).
     * Sama seperti blok jalurPasangTertunda di onResume() Lua.
     */
    public static void lanjutkanPasangTertunda(Activity activity) {
        if (activity == null) return;
        String jalur = BangunProyek.ambilJalurPasangTertunda(activity);
        if (jalur == null || jalur.length() == 0) return;
        if (jalur.startsWith("HILANG:")) {
            // Sama seperti onResume() Lua bila berkasnya sudah tidak ada.
            Toast.makeText(activity, "APK tidak ditemukan: " + jalur.substring(7),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!new File(jalur).exists()) {
            Toast.makeText(activity, "APK tidak ditemukan: " + jalur, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            PemasangApk.pasang(activity, jalur);
        } catch (Throwable t) {
            Toast.makeText(activity, "Gagal memasang: " + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
