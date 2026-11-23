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
 * Keamanan Otomatis (Tahap 3G).
 *
 * Pilih proyek, tekan Periksa Keamanan. IDE memeriksa keamanan proyek
 * secara otomatis dan lokal (tanpa internet): izin berbahaya, kirim
 * data via HTTP, kirim SMS, telepon keluar, kata sandi/kunci hardcoded,
 * WebView tanpa perlindungan, komponen diekspor, dan lalu lintas teks
 * biasa (http). Laporan per jenis pemeriksaan (AMAN/PERINGATAN/BAHAYA)
 * beserta saran perbaikan, dan dibacakan ringkasannya oleh Jieshuo.
 *
 * Berbeda dari Periksa Kode (3E) yang memeriksa pola kode umum —
 * Keamanan fokus pada izin, data yang keluar, dan rahasia.
 */
public class KeamananActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spinnerProyek;
    private TextView hasilView;
    private final List<String> daftarProyek = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Keamanan Otomatis"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Keamanan Otomatis"), 20);
        label(r, TeksUI.t("Periksa keamanan proyekmu secara otomatis. IDE memeriksa ")
                + TeksUI.t("izin berbahaya, data yang dikirim keluar, kata sandi yang ")
                + TeksUI.t("tertulis di kode, WebView tanpa perlindungan, komponen ")
                + TeksUI.t("yang diekspor, dan alamat http tanpa enkripsi. Semua ")
                + TeksUI.t("berjalan di dalam HP (tanpa internet)."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Periksa Keamanan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                periksa();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        hasilView = new TextView(this);
        hasilView.setText("Belum ada hasil. Pilih proyek, lalu tekan Periksa Keamanan.");
        hasilView.setTextSize(14);
        hasilView.setTextIsSelectable(true);
        hasilView.setPadding(dp(4), dp(4), dp(4), dp(4));
        r.addView(hasilView);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce(TeksUI.t("Keamanan Otomatis. Pilih proyek, lalu tekan Periksa Keamanan."));
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private void periksa() {
        if (spinnerProyek.getSelectedItem() == null) return;
        String nama = String.valueOf(spinnerProyek.getSelectedItem());
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(ROOT + "/" + nama);
        List<KeamananOtomatis.Temuan> temuan = KeamananOtomatis.periksa(dir);
        String lap = KeamananOtomatis.laporan(dir, temuan);
        hasilView.setText(lap);
        int bahaya = 0;
        int peringatan = 0;
        for (int i = 0; i < temuan.size(); i++) {
            if ("BAHAYA".equals(temuan.get(i).level)) bahaya++;
            else if ("PERINGATAN".equals(temuan.get(i).level)) peringatan++;
        }
        String ringkas = TeksUI.t("Periksa keamanan selesai: ") + bahaya + TeksUI.t(" BAHAYA, ")
                + peringatan + TeksUI.t(" PERINGATAN di proyek ") + nama + ".";
        Toast.makeText(this, ringkas, Toast.LENGTH_SHORT).show();
        announce(ringkas + TeksUI.t(" Laporan lengkap ada di layar."));
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
