package com.androlua;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.content.FileProvider;

import java.io.File;

/**
 * Memasang APK hasil build dan meminta izin pasang dari sumber tak dikenal.
 *
 * Android 8 (API 26) ke atas: izin REQUEST_INSTALL_PACKAGES saja tidak cukup.
 * Pengguna harus mengaktifkan "Izinkan dari sumber ini" di Pengaturan untuk
 * aplikasi ini. Itu tidak bisa diminta lewat dialog izin biasa, harus dibuka
 * lewat Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES. Alurnya di main.lua:
 *   bisaPasang() == false  -> tampilkan dialog -> mintaIzinPasang()
 *   setelah pengguna mengaktifkannya dan kembali, tekan "Pasang" lagi.
 *
 * Manifest aplikasi ini sudah mendeklarasikan
 * android.permission.REQUEST_INSTALL_PACKAGES (lihat AndroidManifest.xml).
 */
public class PemasangApk {

    /** True bila aplikasi ini sudah diizinkan memasang APK (atau Android < 8). */
    public static boolean bisaPasang(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return ctx.getPackageManager().canRequestPackageInstalls();
    }

    /** Buka halaman Pengaturan "Izinkan dari sumber ini" untuk aplikasi ini. */
    public static void mintaIzinPasang(Activity activity) {
        Intent it = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(it);
    }

    /** Pasang APK lewat FileProvider (ACTION_VIEW). Panggil setelah bisaPasang(). */
    public static void pasang(Context ctx, String apkPath) {
        File file = new File(apkPath);
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName(), file);
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setDataAndType(uri, "application/vnd.android.package-archive");
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(it);
    }
}
