package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Pengaturan GitHub (token) dalam Java murni (Tahap 2E).
 *
 * Mereplikasi persis showGithubSettings() di main.lua: dialog dengan kolom
 * "Token akses GitHub" (teks tersembunyi), tiga label penjelasan, tombol
 * Simpan / Batal. Token dibaca/ditulis dari berkas yang SAMA dengan Lua:
 * <getFilesDir()>/github.cfg (localDir di LuaApplication =
 * getFilesDir().getAbsolutePath()), isinya hanya token polos.
 *
 * Dipakai dari menu opsi semua layar Java ("Pengaturan GitHub"). Callback
 * setelahSimpan (boleh null) sama seperti parameter `after` di Lua — dipakai
 * alur bangun online bila token tadinya kosong.
 */
public class GithubSettings {

    private static File berkasToken(Activity activity) {
        return new File(activity.getFilesDir(), "github.cfg");
    }

    /** Baca token tersimpan ("" bila belum ada). */
    public static String bacaToken(Activity activity) {
        File f = berkasToken(activity);
        if (!f.exists()) return "";
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            int n = in.read(data);
            in.close();
            if (n <= 0) return "";
            // Sama seperti loadConfig() di Lua: pangkas spasi depan/belakang.
            return new String(data, 0, n, "UTF-8").trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Tulis token (timpa berkas). */
    public static boolean simpanToken(Activity activity, String token) {
        try {
            FileOutputStream out = new FileOutputStream(berkasToken(activity));
            out.write(token.getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Tampilkan dialog Pengaturan GitHub, sama seperti showGithubSettings()
     * di Lua. Setelah Simpan: toast "Token tersimpan" + jalankan
     * setelahSimpan bila ada (sama seperti `after` di Lua).
     */
    public static void tampilkan(final Activity activity, final Runnable setelahSimpan) {
        LinearLayout r = new LinearLayout(activity);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));

        TextView judul = new TextView(activity);
        judul.setText("Token akses GitHub");
        judul.setTextSize(14);
        judul.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
        r.addView(judul);

        final EditText eToken = new EditText(activity);
        eToken.setHint("Tempel token di sini, contoh: ghp_xxxxxxxx");
        eToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        eToken.setText(bacaToken(activity));
        r.addView(eToken, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView p1 = new TextView(activity);
        p1.setText("Cukup token. Repo privat \"pembuat-aplikasi-build\" dibuat otomatis di akun GitHub Anda, lalu workflow dan mesin build diunggah sendiri.");
        p1.setTextSize(12);
        p1.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
        r.addView(p1);

        TextView p2 = new TextView(activity);
        p2.setText("Cara membuat token: GitHub > Settings > Developer settings > Personal access tokens > Tokens (classic) > Generate new token. Centang izin \"repo\" dan \"workflow\".");
        p2.setTextSize(12);
        p2.setPadding(0, dp(activity, 2), 0, dp(activity, 2));
        r.addView(p2);

        TextView p3 = new TextView(activity);
        p3.setText("Token disimpan hanya di penyimpanan privat aplikasi ini.");
        p3.setTextSize(12);
        p3.setPadding(0, dp(activity, 2), 0, dp(activity, 2));
        r.addView(p3);

        new AlertDialog.Builder(activity)
                .setTitle(TeksUI.t("Pengaturan GitHub"))
                .setView(r)
                .setPositiveButton(TeksUI.t("Simpan"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        simpanToken(activity, eToken.getText().toString().trim());
                        Toast.makeText(activity, TeksUI.t("Token tersimpan"), Toast.LENGTH_SHORT).show();
                        if (setelahSimpan != null) setelahSimpan.run();
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private static int dp(Activity activity, float v) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (v * density + 0.5f);
    }
}
