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
 * Pratinjau Banyak Ukuran Layar (Fitur nomor 10 dari daftar Android
 * Studio, 5.7).
 *
 * Pilih proyek, lalu "Periksa Ukuran Layar" — IDE membaca layout proyek
 * dan melaporkan elemen yang lebar tetapnya (mis. 400dp) meluber pada
 * layar 320, 360, 411, dan 480 dp, seperti pemeriksaan ukuran layar di
 * Android Studio. Logikanya ada di PeriksaUkuran (murni Java, sudah
 * diuji di komputer).
 */
public class PratinjauUkuranActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Pratinjau Ukuran Layar");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, "Pratinjau Ukuran Layar", 20);
        label(r, "Periksa layout proyek pada beberapa lebar layar sekaligus: "
                + "320, 360, 411, dan 480 dp. Elemen yang lebarnya ditulis "
                + "tetap dan melebihi lebar layar akan dilaporkan, karena di "
                + "HP berlayar kecil elemen itu bisa tertutup atau meluber.", false);

        label(r, "Pilih proyek:", false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Periksa Ukuran Layar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                periksa();
            }
        });

        label(r, "Hasil:", false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(13);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvHasil);
        button(r, "Kembali ke menu sebelumnya", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce("Pratinjau Ukuran Layar. Pilih proyek, lalu tekan Periksa Ukuran Layar.");
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

    private void periksa() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        PeriksaUkuran.Hasil h = PeriksaUkuran.periksa(dir);
        tvHasil.setText(PeriksaUkuran.laporan(h, dir.getName()));
        if (h.galat == null) {
            List<String> terkecil = h.masalah.get(PeriksaUkuran.LEBAR_LAYAR[0]);
            int n = terkecil == null ? 0 : terkecil.size();
            announce("Pemeriksaan selesai. " + h.berkasLayout.size() + " berkas layout, "
                    + h.elemen.size() + " elemen lebar tetap. Pada layar terkecil "
                    + PeriksaUkuran.LEBAR_LAYAR[0] + " dp ada " + n + " elemen meluber.");
        } else {
            announce(h.galat);
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
