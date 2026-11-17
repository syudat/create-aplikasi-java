package com.androlua;

import android.app.Activity;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.File;

/**
 * Pembuat Wallpaper (Tahap 3G).
 *
 * Membuat gambar wallpaper dari tulisan + warna: latar penuh warna
 * (tanpa sudut membulat — wallpaper memenuhi layar), tulisan tebal
 * besar di tengah dengan ukuran yang menyesuaikan lebar layar.
 * Wallpaper bisa disimpan sebagai PNG atau langsung dipasang lewat
 * WallpaperManager.
 *
 * Memakai API Android (Bitmap/Canvas/WallpaperManager), jadi tidak
 * bisa diuji di JRE — diuji lewat ECJ + pemeriksaan statis. Logika
 * warna dominan yang dipakai Aplikasi dari Gambar ada di AnalisisGambar
 * (murni Java, bisa diuji di JRE).
 */
public class PembuatWallpaper {

    /**
     * Membuat Bitmap wallpaper: latar penuh warna, tulisan tebal di
     * tengah (ukuran awal 12% dari lebar, mengecil sampai muat di 80%
     * lebar). Mengembalikan Bitmap ARGB_8888.
     */
    public static Bitmap buatWallpaper(String teks, int warnaLatar,
                                       int warnaTulisan, int lebar, int tinggi) {
        if (teks == null || teks.trim().length() == 0) teks = "Aplikasi Saya";
        if (lebar < 100) lebar = 1080;
        if (tinggi < 100) tinggi = 1920;
        Bitmap bmp = Bitmap.createBitmap(lebar, tinggi, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint latar = new Paint(Paint.ANTI_ALIAS_FLAG);
        latar.setColor(warnaLatar);
        c.drawRect(new RectF(0, 0, lebar, tinggi), latar);
        Paint tulis = new Paint(Paint.ANTI_ALIAS_FLAG);
        tulis.setColor(warnaTulisan);
        tulis.setTypeface(Typeface.DEFAULT_BOLD);
        tulis.setTextAlign(Paint.Align.CENTER);
        float ukuran = lebar * 0.12f;
        tulis.setTextSize(ukuran);
        while (ukuran > 20 && tulis.measureText(teks) > lebar * 0.8f) {
            ukuran *= 0.9f;
            tulis.setTextSize(ukuran);
        }
        Paint.FontMetrics fm = tulis.getFontMetrics();
        float y = tinggi / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(teks, lebar / 2f, y, tulis);
        return bmp;
    }

    /** Memasang Bitmap sebagai wallpaper. Mengembalikan true bila berhasil. */
    public static boolean pasangWallpaper(Activity act, Bitmap bmp) {
        if (act == null || bmp == null) return false;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(act);
            wm.setBitmap(bmp);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Menyimpan Bitmap sebagai PNG (pakai ulang simpanPng IconMakerActivity). */
    public static boolean simpanPng(Bitmap bmp, File target) {
        return IconMakerActivity.simpanPng(bmp, target);
    }
}
