package com.androlua;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Notifikasi status debugging nirkabel (pola TerminalService):
 * satu notifikasi tetap yang menemani pengguna saat ia membuka Opsi
 * Pengembang — tarik bilah notifikasi ke bawah (dua jari) untuk
 * melihat status dan tombol "Masukkan kode pairing".
 *
 * Status:
 * - MENUNGGU_KODE: menunggu pengguna mengambil kode pairing di Opsi
 *   Pengembang; aksi "Masukkan kode pairing" membuka layar Debugger
 *   dengan kolom kode terfokus.
 * - TERSAMBUNG: debugging nirkabel tersambung; aksi "Buka Debugger".
 * - MENUNGGU_WIFI: WiFi mati; akan tersambung otomatis saat WiFi
 *   menyala (pairing tersimpan, tanpa kode lagi); aksi "Buka Debugger".
 * - MENUNGGU_SAMBUNG: sedang mencari layanan adb / menyambung ulang.
 */
public class NotifikasiDebug {

    public static final String CHANNEL_ID = "debug-nirkabel";
    public static final int NOTIF_ID = 4201;

    public static final int MENUNGGU_KODE = 1;
    public static final int TERSAMBUNG = 2;
    public static final int MENUNGGU_WIFI = 3;
    public static final int MENUNGGU_SAMBUNG = 4;

    public static final String EXTRA_BUKA_PAIRING = "buka_pairing";

    private static void buatChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, TeksUI.t("Debugging nirkabel"),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(TeksUI.t("Status debugging nirkabel Create Aplikasi."));
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
        }
    }

    /** Judul status menurut keadaan (sudah diterjemahkan). */
    public static String judulStatus(int status) {
        switch (status) {
            case TERSAMBUNG:
                return TeksUI.t("Debugging nirkabel tersambung");
            case MENUNGGU_WIFI:
                return TeksUI.t("Menunggu WiFi, debugging akan tersambung otomatis");
            case MENUNGGU_SAMBUNG:
                return TeksUI.t("Mencari layanan debugging nirkabel");
            case MENUNGGU_KODE:
            default:
                return TeksUI.t("Menunggu kode pairing, debugging belum tersambung");
        }
    }

    /** Isi notifikasi menurut keadaan (sudah diterjemahkan). */
    public static String isiStatus(int status) {
        switch (status) {
            case TERSAMBUNG:
                return TeksUI.t("Pairing tersimpan. WiFi mati lalu menyala lagi tetap tersambung otomatis tanpa kode baru.");
            case MENUNGGU_WIFI:
                return TeksUI.t("WiFi mati. Nyalakan WiFi, sambungan tersambung kembali otomatis tanpa kode pairing lagi.");
            case MENUNGGU_SAMBUNG:
                return TeksUI.t("Mencari layanan adb di perangkat ini. Pastikan debugging nirkabel aktif di Opsi Pengembang.");
            case MENUNGGU_KODE:
            default:
                return TeksUI.t("Buka Opsi Pengembang, ketuk Pasangkan perangkat dengan kode pairing, lalu masukkan kodenya di sini.");
        }
    }

    /** Bangun objek Notification status (dipakai juga startForeground). */
    public static Notification bangun(Context ctx, int status) {
        buatChannel(ctx);
        Intent buka = new Intent(ctx, DebuggerLangkahActivity.class);
        buka.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (status == MENUNGGU_KODE) {
            buka.putExtra(EXTRA_BUKA_PAIRING, true);
        }
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, buka,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(ctx, CHANNEL_ID);
        } else {
            b = new Notification.Builder(ctx);
        }
        String aksi = status == MENUNGGU_KODE
                ? TeksUI.t("Masukkan kode pairing")
                : TeksUI.t("Buka Debugger");
        return b.setContentTitle(judulStatus(status))
                .setContentText(isiStatus(status))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, aksi, pi).build())
                .build();
    }

    /**
     * Tampilkan/perbarui notifikasi status. Bila status MENUNGGU_KODE,
     * tombol aksinya "Masukkan kode pairing"; bila lain, "Buka Debugger".
     */
    public static void tampilkan(Context ctx, int status) {
        try {
            buatChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.notify(NOTIF_ID, bangun(ctx, status));
        } catch (Throwable ignored) {
        }
    }

    /** Hapus notifikasi status debugging. */
    public static void hapus(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (Throwable ignored) {
        }
    }
}
