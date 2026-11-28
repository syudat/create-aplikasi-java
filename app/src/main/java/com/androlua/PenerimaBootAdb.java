package com.androlua;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Penerima BOOT_COMPLETED untuk sambung-ulang otomatis debugging
 * nirkabel: setelah HP restart, bila sambung-ulang otomatis aktif,
 * coba jalankan LayananAdbOtomatis supaya pairing tersimpan
 * tersambung lagi tanpa kode baru.
 *
 * JUJUR: Android 12+ membatasi layanan latar yang dimulai dari
 * latar belakang; bila sistem menolak, penerima ini menampilkan
 * notifikasi "ketuk untuk menyambung ulang" — satu ketukan tanpa
 * kode pairing (pairing tetap tersimpan di adbd).
 */
public class PenerimaBootAdb extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String aksi = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(aksi)) return;
        try {
            if (!LayananAdbOtomatis.apakahOtomatis(context)) return;
            if (!LayananAdbOtomatis.apakahTerpasang(context)) return;
            try {
                LayananAdbOtomatis.mulai(context);
            } catch (Throwable t) {
                // Sistem menolak mulai layanan dari latar: beri tahu
                // lewat notifikasi supaya pengguna mengetuk sekali.
                NotifikasiDebug.tampilkan(context, NotifikasiDebug.MENUNGGU_SAMBUNG);
            }
        } catch (Throwable ignored) {
        }
    }
}
