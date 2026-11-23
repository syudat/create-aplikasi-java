package com.androlua;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import java.io.File;

/**
 * Menjalankan proyek (port Java dari jembatan jalankanProyek + runProject
 * di main.lua).
 *
 * Perilaku sama persis dengan Lua:
 * - HTML/Situs Web: buka HtmlActivity sebagai pratinjau, memakai berkas
 *   "app/src/main/assets/index.html" bila ada, kalau tidak "index.html"
 *   (jembatan Lua selalu menyetel curFile = nil dulu, jadi berkas yang
 *   sedang dibuka di editor TIDAK dipakai sebagai pratinjau).
 * - Java/Kotlin/Compose: toast bahwa proyek belum bisa dipratinjau
 *   langsung, tekan "Bangun" untuk membuat APK. (Untuk Compose, Lua lama
 *   mencoba newActivity(main.lua) yang tidak ada lalu toast "Gagal
 *   menjalankan"; pesan baru ini hasil akhirnya sama — tidak bisa
 *   dipratinjau, bangun dulu — tapi lebih jelas.)
 * - Lua (proyek lama yang sudah ada di HP): mesin Lua dihapus (Ronde 3);
 *   pengguna diarahkan membuka proyek Lua lewat aplikasi
 *   Create Aplikasi Lua Manager.
 */
public class JalankanProyek {

    /** Jalankan proyek di dirProyek (pengganti runFunc("jalankanProyek", dir)). */
    public static void jalankan(Activity activity, File dirProyek) {
        if (activity == null || dirProyek == null) return;
        String tipe = InfoProyek.tipe(dirProyek);
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            String berkasHtml = "app/src/main/assets/index.html";
            if (!new File(dirProyek, berkasHtml).isFile()) berkasHtml = "index.html";
            try {
                Intent it = new Intent(activity, HtmlActivity.class);
                it.putExtra("jalur_pratinjau",
                        new File(dirProyek, berkasHtml).getAbsolutePath());
                activity.startActivity(it);
            } catch (Throwable t) {
                toast(activity, TeksUI.t("Gagal menjalankan pratinjau: ") + t.getMessage());
            }
        } else if ("java".equals(tipe) || "kotlin".equals(tipe) || "compose".equals(tipe)
                || "python".equals(tipe)) {
            toast(activity, TeksUI.t("Proyek ") + tipe + TeksUI.t(" belum bisa dipratinjau langsung. ")
                    + TeksUI.t("Tekan \"Bangun\" untuk membuat APK, lalu pasang untuk mencoba."));
        } else {
            // Ronde 3: mesin Lua dihapus — proyek Lua lama dibuka lewat
            // aplikasi Create Aplikasi Lua Manager, bukan dari sini.
            toast(activity, TeksUI.t("Proyek Lua dibuka lewat aplikasi ")
                    + TeksUI.t("Create Aplikasi Lua Manager."));
        }
    }

    private static void toast(Activity activity, String teks) {
        Toast.makeText(activity, teks, Toast.LENGTH_SHORT).show();
    }
}
