package com.androlua;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.FileProvider;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Konektor (perintah al kautsar 2026-11-24 13:16 WITA, tahap 1).
 *
 * Pengguna memilih sebuah APK dari HP, Konektor MENGANALISIS APK itu
 * (paket, versi, SDK, aktivitas, izin, ukuran — memakai ulang
 * AnalisisApk) dan menampilkan laporan analisis. Konektor juga bisa
 * MENGIRIM APK itu lewat berbagi sistem Android (share intent ke
 * aplikasi lain, mis. WhatsApp, Drive, Bluetooth).
 *
 * Berkas yang dipilih disalin dulu ke cache privat
 * (getCacheDir()/konektor-apk.apk) supaya analisis dan berbagi selalu
 * membaca berkas yang sama. Berbagi memakai FileProvider bawaan pohon
 * (android.content.FileProvider) dengan authority
 * <paket>.fileprovider — entri <provider> + res/xml/file_paths.xml
 * harus ditambahkan saat sinkron ke pohon Gradle (lihat TEMUAN).
 */
public class KonektorActivity extends Activity {

    private static final int MINTA_APK = 1;

    private String kodeBahasa;
    private TextView tvDipilih;
    private TextView tvLaporan;
    private File apkTerpilih;
    private String namaApkTerpilih = "";

    private String t(String kunci) {
        return TeksUI.teks(kodeBahasa, kunci);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kodeBahasa = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;
        setTitle(t("Konektor"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, t("Konektor"), 20);
        label(r, t("Pilih APK untuk dianalisis"), false);

        tvDipilih = label(r, t("APK dipilih:") + " -", false);

        button(r, t("Pilih APK untuk dianalisis"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihApk();
            }
        });

        button(r, t("Analisis APK"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analisisApk();
            }
        });

        button(r, t("Kirim APK"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kirimApk();
            }
        });

        label(r, t("Hasil:"), false);
        tvLaporan = new TextView(this);
        tvLaporan.setText("");
        tvLaporan.setTextSize(14);
        tvLaporan.setTextIsSelectable(true);
        r.addView(tvLaporan);
    }

    /** Buka pemilih berkas sistem untuk satu berkas APK. */
    private void pilihApk() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.android.package-archive");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent,
                    t("Pilih APK untuk dianalisis")), MINTA_APK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, t("Tidak ada aplikasi untuk mengirim APK ini."),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, t("Gagal membuka Konektor: ") + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MINTA_APK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        File tujuan = new File(getCacheDir(), "konektor-apk.apk");
        if (!salinUriKeBerkas(uri, tujuan)) {
            Toast.makeText(this, t("Berkas APK tidak ditemukan."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        apkTerpilih = tujuan;
        namaApkTerpilih = namaDariUri(uri);
        tvDipilih.setText(t("APK dipilih:") + " " + namaApkTerpilih);
        tvLaporan.setText("");
        announce(t("APK dipilih:") + " " + namaApkTerpilih);
    }

    /** Salin isi Uri ke berkas tujuan; false bila gagal. */
    private boolean salinUriKeBerkas(Uri uri, File tujuan) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return false;
            out = new FileOutputStream(tujuan);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return tujuan.isFile() && tujuan.length() > 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
            try { if (out != null) out.close(); } catch (Throwable ignored) { }
        }
    }

    /** Nama tampil berkas dari Uri (bagian akhir path). */
    private String namaDariUri(Uri uri) {
        try {
            String path = uri.getLastPathSegment();
            if (path != null && path.length() > 0) return path;
        } catch (Throwable ignored) {
        }
        return "apk.apk";
    }

    /** Analisis APK terpilih di utas latar, tampilkan laporannya. */
    private void analisisApk() {
        if (apkTerpilih == null || !apkTerpilih.isFile()) {
            Toast.makeText(this, t("Pilih dulu berkas APK-nya."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final File apk = apkTerpilih;
        final String nama = namaApkTerpilih;
        tvLaporan.setText(t("Memuat") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AnalisisApk.Hasil hasil = AnalisisApk.analisis(apk);
                final String laporan = AnalisisApk.laporan(hasil, nama);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvLaporan.setText(laporan);
                        announce(t("Analisis APK"));
                    }
                });
            }
        }, "Konektor").start();
    }

    /** Kirim APK terpilih lewat berbagi sistem Android. */
    private void kirimApk() {
        if (apkTerpilih == null || !apkTerpilih.isFile()) {
            Toast.makeText(this, t("Pilih dulu berkas APK-nya."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", apkTerpilih);
            Intent kirim = new Intent(Intent.ACTION_SEND);
            kirim.setType("application/vnd.android.package-archive");
            kirim.putExtra(Intent.EXTRA_STREAM, uri);
            kirim.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(kirim, t("Kirim APK")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, t("Tidak ada aplikasi untuk mengirim APK ini."),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, t("Gagal membuka Konektor: ") + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ UI

    private LinearLayout newRoot() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        GayaStudio.latar(r);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        return r;
    }

    private TextView heading(LinearLayout parent, String text, float size) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.judul(tv, size);
        tv.setPadding(0, dp(8), 0, dp(4));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                tv.setAccessibilityHeading(true);
            } catch (Throwable ignored) {
            }
        }
        parent.addView(tv);
        return tv;
    }

    private TextView label(LinearLayout parent, String text, boolean small) {
        TextView tv = new TextView(this);
        tv.setText(text);
        if (small) {
            GayaStudio.labelKecil(tv, 12);
        } else {
            GayaStudio.label(tv, 14);
        }
        tv.setPadding(0, dp(small ? 2 : 10), 0, dp(2));
        parent.addView(tv);
        return tv;
    }

    private Button button(LinearLayout parent, String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(onClick);
        GayaStudio.tombol(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private int dp(float v) {
        return GayaStudio.dp(this, v);
    }

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
