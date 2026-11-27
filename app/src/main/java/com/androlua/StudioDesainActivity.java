package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Studio Sistem Desain Adaptif dan Pabrik Komponen Antar-Proyek
 * (Fitur 4, daftar Gemini 2026-11-27).
 *
 * Tulis token desain, pilih komponen, tekan "Hasilkan Komponen" —
 * IDE memvalidasi token, menghasilkan komponen XML siap pakai beserta
 * tema XML dan Compose, lalu menyimpannya di folder proyek. Logika
 * ada di StudioDesain (murni Java, sudah diuji di komputer).
 *
 * BATAS: komponen dihasilkan sebagai teks; tempelkan ke layout lalu
 * periksa dengan Arsitek Tata Letak.
 */
public class StudioDesainActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eToken;
    private Spinner spKomponen;
    private TextView tvHasil;
    private Button bJalankan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Studio Sistem Desain"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Studio Sistem Desain"), 20);
        label(r, TeksUI.t("Atur token desain sekali — jarak, huruf, sudut, dan area sentuh — lalu hasilkan komponen siap pakai dan tema XML atau Compose untuk semua proyek."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Token desain:"), false);
        eToken = new EditText(this);
        GayaStudio.edit(eToken);
        eToken.setHint(TeksUI.t("Contoh: jarak=8, huruf=16, sudut=12"));
        eToken.setText("jarak=8, huruf=16, sudut=12");
        eToken.setTextSize(14);
        eToken.setSingleLine(true);
        r.addView(eToken, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih komponen:"), false);
        spKomponen = new Spinner(this);
        ArrayAdapter<String> adapterKomponen = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, StudioDesain.jenisKomponen());
        adapterKomponen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spKomponen.setAdapter(adapterKomponen);
        r.addView(spKomponen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Hasilkan Komponen"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Atur token desain, lalu tekan") + " "
                + TeksUI.t("Hasilkan Komponen") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Studio Sistem Desain") + ". " + TeksUI.t("Belum ada hasil. Atur token desain, lalu tekan")
                + " " + TeksUI.t("Hasilkan Komponen") + ".");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private void mulai() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final String tokenTeks = eToken.getText().toString();
        final String jenis = String.valueOf(spKomponen.getSelectedItem());
        tvHasil.setText(TeksUI.t("Hasilkan Komponen") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir, tokenTeks, jenis);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "StudioDesain").start();
    }

    private String jalankan(File dir, String tokenTeks, String jenis) {
        StudioDesain.Token token = StudioDesain.uraiToken(tokenTeks);
        List<String> masalah = StudioDesain.validasi(token);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Komponen dihasilkan:")).append(" ");
        if (masalah.size() > 0) {
            b.append("0. ").append(TeksUI.t("Token tidak valid:"));
            for (int i = 0; i < masalah.size(); i++) b.append("\n- ").append(masalah.get(i));
            return b.toString();
        }
        String komponen = StudioDesain.komponen(jenis, token);
        tulisTeks(new File(dir, "komponen-studio.xml"), komponen);
        tulisTeks(new File(dir, "tema-studio.xml"), StudioDesain.eksporTemaXml(token));
        tulisTeks(new File(dir, "tema-studio.kt"), StudioDesain.eksporTemaCompose(token));
        b.append("1. ").append(StudioDesain.ringkasan(1, masalah));
        b.append("\n").append(komponen);
        return b.toString();
    }

    private boolean tulisTeks(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            out.write(isi);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

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
