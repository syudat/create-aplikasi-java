package com.androlua;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
 * Desainer Tema Otomatis (Tahap 3F).
 *
 * Pilih proyek dan tema (Terang/Gelap/Biru/Hijau/Merah/Ungu), lihat
 * pratinjau warna (kotak warna + nama, dibacakan Jieshuo), lalu tekan
 * "Terapkan Tema" — IDE menulis colors.xml + styles.xml (style
 * app_theme) di app/src/main/res/values/ proyek. Tema langsung dipakai
 * saat proyek dijalankan/dibangun.
 */
public class TemaActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private Spinner spTema;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private LinearLayout kotakPratinjau;
    private TextView tvPratinjau;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Desainer Tema");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, "Desainer Tema", 20);
        label(r, "Pilih proyek dan tema warna — IDE menulis tema sungguhan "
                + "(colors.xml + styles.xml) ke proyekmu. Pratinjau di bawah "
                + "menunjukkan enam warna tema yang dipilih.", false);

        label(r, "Pilih proyek:", false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Pilih tema:", false);
        spTema = new Spinner(this);
        String[] tema = new String[DesainerTema.SEMUA.length];
        for (int i = 0; i < tema.length; i++) tema[i] = DesainerTema.SEMUA[i].nama;
        ArrayAdapter<String> adapterTema = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, tema);
        adapterTema.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTema.setAdapter(adapterTema);
        spTema.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                perbaruiPratinjau();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        r.addView(spTema, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Pratinjau tema:", false);
        kotakPratinjau = new LinearLayout(this);
        kotakPratinjau.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(kotakPratinjau, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvPratinjau = label(r, "", false);

        button(r, "Terapkan Tema", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terapkanTema();
            }
        });
        button(r, "Kembali ke menu sebelumnya", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        perbaruiPratinjau();
        announce("Desainer Tema. Pilih proyek dan tema, lalu tekan Terapkan Tema.");
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

    private DesainerTema.Tema temaTerpilih() {
        int pos = spTema == null ? 0 : spTema.getSelectedItemPosition();
        if (pos < 0 || pos >= DesainerTema.SEMUA.length) pos = 0;
        return DesainerTema.SEMUA[pos];
    }

    private void perbaruiPratinjau() {
        if (kotakPratinjau == null) return;
        kotakPratinjau.removeAllViews();
        DesainerTema.Tema t = temaTerpilih();
        String[] warna = {t.primer, t.primerGelap, t.aksen, t.latar, t.teks, t.teksSekunder};
        String[] nama = {"Primer", "Primer gelap", "Aksen", "Latar", "Teks", "Teks sekunder"};
        StringBuilder deskripsi = new StringBuilder("Tema " + t.nama + ": ");
        for (int i = 0; i < warna.length; i++) {
            View kotak = new View(this);
            try {
                kotak.setBackgroundColor(Color.parseColor(warna[i]));
            } catch (Throwable ignored) {
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(4);
            kotakPratinjau.addView(kotak, lp);
            kotak.setContentDescription(nama[i] + " " + warna[i]);
            if (i > 0) deskripsi.append(", ");
            deskripsi.append(nama[i]).append(" ").append(warna[i]);
        }
        if (tvPratinjau != null) tvPratinjau.setText(deskripsi.toString());
        kotakPratinjau.setContentDescription(deskripsi.toString());
    }

    private void terapkanTema() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyek yang mau diberi tema.");
            return;
        }
        String nama = String.valueOf(spProyek.getSelectedItem());
        DesainerTema.Tema t = temaTerpilih();
        String galat = DesainerTema.terapkanTema(new File(ROOT + "/" + nama), t.id);
        if (galat == null) {
            String ok = "Tema " + t.nama + " diterapkan ke proyek " + nama
                    + " (colors.xml + styles.xml).";
            toast(ok);
            announce(ok);
        } else {
            toast(galat);
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
