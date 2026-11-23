package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Penganalisis APK (Fitur nomor 1 dari daftar Android Studio, 5.7).
 *
 * Pilih proyek, lalu "Analisis APK Hasil Build" — IDE membaca APK terbaru
 * di folder bin proyek itu dan melaporkan nama paket, versi, SDK, daftar
 * izin, jumlah dex, rincian ukuran per bagian, dan entri terbesar,
 * seperti APK Analyzer di Android Studio. Logika bacanya ada di
 * AnalisisApk (murni Java, sudah diuji di komputer dengan APK sungguhan).
 */
public class AnalisisApkActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Penganalisis APK"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Penganalisis APK"), 20);
        label(r, TeksUI.t("Baca isi APK hasil build sebuah proyek: nama paket, versi, ")
                + TeksUI.t("SDK, daftar izin, jumlah berkas dex, rincian ukuran per ")
                + TeksUI.t("bagian (kode, resource, aset, pustaka native), dan entri ")
                + TeksUI.t("terbesar. Berguna untuk tahu bagian mana yang membuat ")
                + TeksUI.t("APK besar."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Analisis APK Hasil Build"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analisis();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(13);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvHasil);
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Penganalisis APK. Pilih proyek, lalu tekan Analisis APK Hasil Build."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    /** APK terbaru (paling akhir diubah) di folder bin proyek, atau null. */
    static File apkTerbaru(File dirProyek) {
        File bin = new File(dirProyek, "bin");
        File[] isi = bin.listFiles();
        if (isi == null) return null;
        File terbaik = null;
        for (int i = 0; i < isi.length; i++) {
            File f = isi[i];
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (!n.endsWith(".apk")) continue;
            if (n.contains("belum-ditandatangani")) continue;
            if (terbaik == null || f.lastModified() > terbaik.lastModified()) terbaik = f;
        }
        return terbaik;
    }

    private void analisis() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final File apk = apkTerbaru(dir);
        if (apk == null) {
            tvHasil.setText("Belum ada APK hasil build di folder bin proyek ini. "
                    + TeksUI.t("Bangun dulu proyeknya sampai APK-nya jadi."));
            announce(TeksUI.t("Belum ada APK hasil build di proyek ini."));
            return;
        }
        tvHasil.setText(TeksUI.t("Menganalisis ") + apk.getName() + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AnalisisApk.Hasil h = AnalisisApk.analisis(apk);
                final String teks = AnalisisApk.laporan(h, apk.getName());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(teks);
                        if (h.galat == null) {
                            announce(TeksUI.t("Analisis selesai. Paket ") + h.namaPaket
                                    + TeksUI.t(", versi ") + h.versiNama
                                    + TeksUI.t(", ukuran ") + AnalisisApk.formatUkuran(h.ukuranBerkas)
                                    + ", " + h.jumlahDex + TeksUI.t(" berkas dex, ")
                                    + h.izin.size() + TeksUI.t(" izin."));
                        } else {
                            announce(h.galat);
                        }
                    }
                });
            }
        }, "AnalisisApk").start();
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

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
