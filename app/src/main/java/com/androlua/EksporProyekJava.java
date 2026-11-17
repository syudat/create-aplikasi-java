package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.FileProvider;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;
import java.io.File;

/**
 * Ekspor proyek menjadi ZIP (port Java dari jembatan eksporProyekJava +
 * eksporProyek di main.lua).
 *
 * Perilaku sama persis dengan Lua: EksporProyek.ekspor(dir) membuat ZIP di
 * Download/project/&lt;NamaProyek&gt;.zip (tanpa folder bin/); bila gagal
 * muncul toast; bila berhasil muncul dialog selesai lengkap dengan jalur,
 * ukuran, dan tombol Bagikan (Intent ACTION_SEND, application/zip,
 * FileProvider). Pemanggil tidak perlu menyimpan dulu — pemanggil Java
 * (EditorActivity) sudah menyimpan sebelum memanggil.
 */
public class EksporProyekJava {

    /** Ekspor proyek di dirProyek (pengganti runFunc("eksporProyekJava", dir)). */
    public static void ekspor(final Activity activity, final File dirProyek) {
        if (activity == null || dirProyek == null) return;
        final String hasil = EksporProyek.ekspor(dirProyek.getAbsolutePath());
        if (hasil == null || hasil.isEmpty()) {
            Toast.makeText(activity, "Ekspor gagal. Periksa izin penyimpanan.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        long ukuran = new File(hasil).length();
        new AlertDialog.Builder(activity).setTitle("Ekspor ZIP selesai")
                .setMessage("Proyek tersimpan sebagai:\n" + hasil
                        + "\nUkuran: " + ukuran + " byte."
                        + "\n\nFolder bin/ (APK hasil build) tidak ikut, supaya file tetap kecil dan mudah dibagikan.")
                .setPositiveButton("Bagikan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent it = new Intent(Intent.ACTION_SEND);
                            it.setType("application/zip");
                            it.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                    activity, activity.getPackageName(), new File(hasil)));
                            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            activity.startActivity(Intent.createChooser(it,
                                    "Bagikan " + dirProyek.getName() + ".zip"));
                        } catch (Throwable t) {
                            Toast.makeText(activity, "Gagal membagikan: " + t.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Tutup", null).show();
    }
}
