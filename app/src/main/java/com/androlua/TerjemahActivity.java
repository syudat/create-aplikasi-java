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
 * Penerjemah Aplikasi Otomatis (Tahap 3E).
 *
 * Pilih proyek + bahasa target (Inggris/Arab/Jawa), tekan Terjemahkan.
 * IDE membaca teks di strings.xml, layout, dan kode, menerjemahkannya
 * dengan kamus bawaan di dalam HP (tanpa internet, tanpa biaya), lalu
 * menulis values-&lt;bahasa&gt;/strings.xml (bila ada strings.xml),
 * tabel terjemahan-&lt;bahasa&gt;.lua (proyek Lua), dan laporan
 * terjemahan-&lt;bahasa&gt;.txt. Kata yang tidak ada di kamus dibiarkan
 * apa adanya dan dilaporkan dengan jujur.
 */
public class TerjemahActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spinnerProyek;
    private Spinner spinnerBahasa;
    private TextView hasilView;
    private final List<String> daftarProyek = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Penerjemah Aplikasi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Penerjemah Aplikasi"), 20);
        label(r, TeksUI.t("Terjemahkan semua teks di aplikasimu ke bahasa lain. ")
                + TeksUI.t("Kamus ada di dalam HP (tanpa internet, tanpa biaya). Kata ")
                + TeksUI.t("yang belum ada di kamus dilaporkan apa adanya, tidak ")
                + TeksUI.t("pura-pura diterjemahkan."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Bahasa target:"), false);
        spinnerBahasa = new Spinner(this);
        List<String> bahasa = new ArrayList<String>();
        for (int i = 0; i < Penerjemah.BAHASA_NAMA.length; i++) {
            bahasa.add(Penerjemah.BAHASA_NAMA[i] + " (" + Penerjemah.BAHASA_KODE[i] + ")");
        }
        ArrayAdapter<String> adapterBahasa = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, bahasa);
        adapterBahasa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBahasa.setAdapter(adapterBahasa);
        r.addView(spinnerBahasa, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Terjemahkan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terjemahkan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        hasilView = new TextView(this);
        hasilView.setText("Belum ada hasil. Pilih proyek dan bahasa, lalu tekan Terjemahkan.");
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

        announce(TeksUI.t("Penerjemah Aplikasi. Pilih proyek dan bahasa target, lalu tekan Terjemahkan."));
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        File root = new File(ROOT);
        File[] daftar = root.listFiles();
        if (daftar != null) {
            for (int i = 0; i < daftar.length; i++) {
                if (daftar[i].isDirectory()
                        && new File(daftar[i], "init.lua").isFile()) {
                    daftarProyek.add(daftar[i].getName());
                }
            }
        }
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private void terjemahkan() {
        if (spinnerProyek.getSelectedItem() == null) return;
        String nama = String.valueOf(spinnerProyek.getSelectedItem());
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String bahasa = Penerjemah.BAHASA_KODE[spinnerBahasa.getSelectedItemPosition()];
        File dir = new File(ROOT + "/" + nama);
        Penerjemah.Laporan lap = Penerjemah.terjemahkanProyek(dir, bahasa);
        if (lap == null) {
            hasilView.setText("Tidak ada teks yang bisa diterjemahkan di proyek ini.");
            announce(TeksUI.t("Tidak ada teks yang bisa diterjemahkan di proyek ini."));
            return;
        }
        hasilView.setText(lap.laporan);
        String ringkas = TeksUI.t("Selesai menerjemahkan ke ") + Penerjemah.namaBahasa(bahasa)
                + ": " + lap.jumlahTeks + TeksUI.t(" teks, ") + lap.kataDiterjemahkan
                + TeksUI.t(" kata diterjemahkan, ") + lap.kataBelum + TeksUI.t(" kata belum diterjemahkan.");
        Toast.makeText(this, ringkas, Toast.LENGTH_LONG).show();
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
