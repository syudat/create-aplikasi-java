package com.androlua;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Penerap gaya bersama "Android Studio" (tema terang) untuk semua layar IDE.
 *
 * SATU tempat untuk semua warna/ukuran/jarak visual. Setiap Activity TIDAK
 * lagi mengatur warna sendiri — helper-nya (newRoot/heading/label/button)
 * mendelegasikan ke sini, jadi semua layar punya judul, label, tombol,
 * dan kotak isi yang SAMA.
 *
 * Yang TIDAK diubah di sini (sengaja): teks tombol/label, urutan tombol,
 * aksi klik, alur layar, menu, dialog, contentDescription, dan
 * announceForAccessibility — hanya tampilan.
 *
 * Pengecualian: Mode Anak memakai tombolBesar() (warna sama, ukuran 20sp
 * tetap dipertahankan); Terminal memakai terminal()/terminalEdit()
 * (panel gelap Darcula seperti Terminal Android Studio); editor kode
 * memakai editor() (putih-hitam, monospace dipertahankan pemanggil).
 */
public final class GayaStudio {
    private GayaStudio() {
    }

    /** Konversi dp ke piksel (sama seperti helper dp() lama di Activity). */
    public static int dp(Context ctx, float v) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (v * density + 0.5f);
    }

    /** Latar layar (abu terang). */
    public static void latar(View v) {
        v.setBackgroundColor(PaletStudio.LATAR);
    }

    /** Latar kartu/kotak isi (putih). */
    public static void panel(View v) {
        v.setBackgroundColor(PaletStudio.PANEL);
    }

    /** Judul layar: tebal + hitam, ukuran mengikuti pemanggil. */
    public static void judul(TextView tv, float sizeSp) {
        tv.setTextSize(sizeSp);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(PaletStudio.TEKS_JUDUL);
    }

    /** Label isi biasa. */
    public static void label(TextView tv, float sizeSp) {
        tv.setTextSize(sizeSp);
        tv.setTextColor(PaletStudio.TEKS_ISI);
    }

    /** Label kecil/keterangan (abu gelap). */
    public static void labelKecil(TextView tv, float sizeSp) {
        tv.setTextSize(sizeSp);
        tv.setTextColor(PaletStudio.TEKS_SEKUNDER);
    }

    /** Latar tombol aksen biru dengan sudut membulat (dipakai semua tombol). */
    private static GradientDrawable latarTombol(Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(PaletStudio.AKSEN);
        d.setCornerRadius(dp(ctx, PaletStudio.RADIUS_TOMBOL_DP));
        return d;
    }

    /** Tombol utama: biru aksen, teks putih 16sp, padding baku. */
    public static void tombol(Button b) {
        Context ctx = b.getContext();
        b.setBackground(latarTombol(ctx));
        b.setTextColor(PaletStudio.TEKS_DI_ATAS_AKSEN);
        b.setTextSize(PaletStudio.TEKS_TOMBOL_SP);
        b.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
    }

    /** Tombol besar Mode Anak: warna sama dengan tombol utama, ukuran teks TIDAK diubah (tetap 20sp). */
    public static void tombolBesar(Button b) {
        Context ctx = b.getContext();
        b.setBackground(latarTombol(ctx));
        b.setTextColor(PaletStudio.TEKS_DI_ATAS_AKSEN);
        b.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
    }

    /** Bingkai kotak isi: putih dengan garis tipis. */
    private static GradientDrawable bingkaiKotak(Context ctx, int isi) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(isi);
        d.setStroke(dp(ctx, 1), PaletStudio.GARIS);
        d.setCornerRadius(dp(ctx, PaletStudio.RADIUS_TOMBOL_DP));
        return d;
    }

    /** Kotak isi biasa: putih, teks gelap, petunjuk abu-abu. */
    public static void edit(EditText e) {
        Context ctx = e.getContext();
        e.setBackground(bingkaiKotak(ctx, PaletStudio.PANEL));
        e.setTextColor(PaletStudio.TEKS_ISI);
        e.setHintTextColor(PaletStudio.TEKS_PETUNJUK);
        e.setTextSize(PaletStudio.TEKS_EDIT_SP);
        e.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
    }

    /** Editor kode: putih, teks hitam (monospace + ukuran dipertahankan pemanggil). */
    public static void editor(EditText e) {
        Context ctx = e.getContext();
        e.setBackground(bingkaiKotak(ctx, PaletStudio.EDITOR_LATAR));
        e.setTextColor(PaletStudio.EDITOR_TEKS);
        e.setHintTextColor(PaletStudio.TEKS_PETUNJUK);
        e.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
    }

    /** Keluaran Terminal: panel gelap Darcula, teks terang. */
    public static void terminal(TextView tv) {
        Context ctx = tv.getContext();
        tv.setBackgroundColor(PaletStudio.TERMINAL_LATAR);
        tv.setTextColor(PaletStudio.TERMINAL_TEKS);
        tv.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
    }

    /** Baris perintah Terminal: gelap, teks terang. */
    public static void terminalEdit(EditText e) {
        Context ctx = e.getContext();
        e.setBackgroundColor(PaletStudio.TERMINAL_LATAR);
        e.setTextColor(PaletStudio.TERMINAL_TEKS);
        e.setHintTextColor(PaletStudio.TERMINAL_PETUNJUK);
        e.setTextSize(PaletStudio.TEKS_EDIT_SP);
        e.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
    }
}
