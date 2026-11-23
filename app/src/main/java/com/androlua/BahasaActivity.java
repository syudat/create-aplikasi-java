package com.androlua;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pemilih Bahasa Aplikasi (perintah al kautsar 2026-11-23): 50 bahasa
 * dengan kotak pencarian, di paling bawah Pengaturan. Bawaan "Ikuti
 * Sistem" — bahasa mengikuti sistem Android. Pilihan disimpan di
 * SharedPreferences "pengaturan" kunci "bahasa" dan dipakai oleh
 * Tentang Alat serta bahasa aplikasi (lihat Aplikasi.attachBaseContext
 * dan Bahasa.kodeEfektif).
 */
public class BahasaActivity extends Activity {

    private TextView tvSaatIni;
    private ListView lvBahasa;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<String>();
    private final List<String> kodeBaris = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Bahasa Aplikasi");

        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        GayaStudio.latar(r);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        setContentView(r);

        TextView judul = new TextView(this);
        judul.setText("Bahasa Aplikasi");
        GayaStudio.judul(judul, 20);
        judul.setPadding(0, dp(8), 0, dp(4));
        r.addView(judul);

        tvSaatIni = new TextView(this);
        GayaStudio.label(tvSaatIni, 14);
        r.addView(tvSaatIni);
        perbaruiLabelSaatIni();

        TextView petunjuk = new TextView(this);
        petunjuk.setText("Cari bahasa, lalu ketuk untuk memilih. Bawaan mengikuti sistem Android.");
        GayaStudio.labelKecil(petunjuk, 12);
        petunjuk.setPadding(0, dp(6), 0, dp(2));
        r.addView(petunjuk);

        EditText cari = new EditText(this);
        cari.setHint("Cari bahasa (contoh: spanyol, arab, english)");
        cari.setSingleLine(true);
        r.addView(cari, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cari.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                muatDaftar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        lvBahasa = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, baris);
        lvBahasa.setAdapter(adapter);
        r.addView(lvBahasa, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        lvBahasa.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int posisi, long id) {
                pilih(kodeBaris.get(posisi));
            }
        });

        Button kembali = new Button(this);
        kembali.setText("Kembali ke menu sebelumnya");
        GayaStudio.tombol(kembali);
        kembali.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        r.addView(kembali, lp);

        muatDaftar("");
        announce("Bahasa Aplikasi. Cari bahasa, lalu ketuk untuk memilih.");
    }

    private void perbaruiLabelSaatIni() {
        String pilihan = PengaturanActivity.baca(this)
                .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
        tvSaatIni.setText("Bahasa saat ini: " + Bahasa.labelPilihan(
                pilihan, Locale.getDefault().getLanguage()));
    }

    private void muatDaftar(String kunci) {
        baris.clear();
        kodeBaris.clear();
        if (kunci == null || kunci.trim().length() == 0) {
            baris.add("Ikuti Sistem — " + Bahasa.labelPilihan(
                    Bahasa.SISTEM, Locale.getDefault().getLanguage()));
            kodeBaris.add(Bahasa.SISTEM);
        }
        for (Bahasa.Entri e : Bahasa.cari(kunci)) {
            baris.add(e.label());
            kodeBaris.add(e.kode);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void pilih(String kode) {
        SharedPreferences.Editor ed = PengaturanActivity.baca(this).edit();
        ed.putString(PengaturanActivity.KUNCI_BAHASA, kode);
        ed.apply();
        String label = Bahasa.labelPilihan(kode, Locale.getDefault().getLanguage());
        Toast.makeText(this, "Bahasa dipilih: " + label, Toast.LENGTH_SHORT).show();
        announce("Bahasa dipilih: " + label + ". Bahasa dipakai di Tentang Alat dan menjadi bahasa aplikasi.");
        perbaruiLabelSaatIni();
        finish();
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
