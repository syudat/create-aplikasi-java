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
 * Kalkulator Ukuran APK (Tahap 3I).
 *
 * Pilih proyek, lalu "Hitung Perkiraan" — IDE menampilkan perkiraan
 * ukuran APK hasil build: rincian per kategori (kode/DEX, resource,
 * aset, library native, manifest + arsc, overhead), total perkiraan,
 * 5 berkas terbesar, dan saran mengecilkan.
 *
 * Batasan jujur yang ditulis di layar: ini PERKIRAAN, bukan ukuran
 * pasti hasil build (kompresi aapt2, R8, dan resource hasil build
 * membuat ukuran sungguhan berbeda).
 */
public class UkuranApkActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Kalkulator Ukuran APK"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Kalkulator Ukuran APK"), 20);
        label(r, TeksUI.t("Perkirakan ukuran APK hasil build dari sebuah proyek: ")
                + TeksUI.t("rincian per kategori, total perkiraan, 5 berkas ")
                + TeksUI.t("terbesar, dan saran mengecilkan. Ini PERKIRAAN, bukan ")
                + TeksUI.t("ukuran pasti — ukuran sungguhan bisa berbeda karena ")
                + TeksUI.t("kompresi aapt2, penghapusan kode tak terpakai (R8), dan ")
                + TeksUI.t("resource hasil build."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Hitung Perkiraan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hitung();
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
        announce(TeksUI.t("Kalkulator Ukuran APK. Pilih proyek, lalu tekan Hitung Perkiraan."));
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

    private void hitung() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        String teks = KalkulatorUkuran.laporan(dir);
        if (tvHasil != null) tvHasil.setText(teks);
        KalkulatorUkuran.Hasil h = KalkulatorUkuran.perkirakan(dir);
        if (h != null) {
            announce(TeksUI.t("Perkiraan ukuran APK proyek ") + dir.getName() + ": "
                    + PetaProyek.formatUkuran(h.total) + ".");
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
