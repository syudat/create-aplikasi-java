package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Mode Belajar Interaktif (Tahap 3E).
 *
 * Lima pelajaran langkah demi langkah. Ketuk satu pelajaran untuk membaca
 * penjelasan dan tugasnya, lalu tekan "Periksa tugas" — IDE memeriksa
 * BENAR-BENAR apakah tugasnya sudah dikerjakan (memindai proyek di
 * Download/project dan APK di Download). Pelajaran yang lulus ditandai
 * selesai dan disimpan.
 */
public class BelajarActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final String DOWNLOAD = Environment.getExternalStorageDirectory().getPath()
            + "/Download";

    private ListView lv;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Belajar"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Belajar"), 20);
        label(r, TeksUI.t("Belajar membuat aplikasi langkah demi langkah. Ketuk satu ")
                + TeksUI.t("pelajaran untuk membaca penjelasannya, kerjakan tugasnya, ")
                + TeksUI.t("lalu tekan Periksa tugas — saya memeriksa sungguhan apakah ")
                + TeksUI.t("tugasnya sudah dikerjakan."), false);

        lv = new ListView(this);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, baris);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < Belajar.SEMUA.length) {
                    tampilkanPelajaran(Belajar.SEMUA[pos]);
                }
            }
        });
        r.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce("Belajar. " + Belajar.SEMUA.length + " pelajaran tersedia, "
                + Belajar.jumlahSelesai(berkasProgres()) + TeksUI.t(" sudah selesai."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        muatDaftar();
    }

    private File berkasProgres() {
        return new File(getFilesDir(), "belajar.txt");
    }

    private void muatDaftar() {
        List<String> selesai = Belajar.bacaProgres(berkasProgres());
        baris.clear();
        for (int i = 0; i < Belajar.SEMUA.length; i++) {
            Belajar.Pelajaran p = Belajar.SEMUA[i];
            baris.add((selesai.contains(p.id) ? TeksUI.t("✓ Selesai — ") : TeksUI.t("Belum — ")) + p.judul);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void tampilkanPelajaran(final Belajar.Pelajaran p) {
        boolean sudah = Belajar.bacaProgres(berkasProgres()).contains(p.id);
        String isi = p.penjelasan + "\n\nTugas: " + p.tugas
                + "\n\nStatus: " + (sudah ? TeksUI.t("Selesai.") : TeksUI.t("Belum selesai."));
        new AlertDialog.Builder(this)
                .setTitle(p.judul)
                .setMessage(isi)
                .setPositiveButton(TeksUI.t("Periksa tugas"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        periksaTugas(p);
                    }
                })
                .setNegativeButton(TeksUI.t("Tutup"), null)
                .show();
        announce(p.judul + ". " + p.tugas);
    }

    private void periksaTugas(Belajar.Pelajaran p) {
        Belajar.HasilTugas h = Belajar.periksaTugas(
                new File(ROOT), new File(DOWNLOAD), p.id);
        if (h.lulus) {
            Belajar.tandaiSelesai(berkasProgres(), p.id);
            muatDaftar();
            String ok = TeksUI.t("Tugas selesai! ") + h.bukti;
            Toast.makeText(this, ok, Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Tugas selesai. ") + h.bukti);
        } else {
            String belum = TeksUI.t("Belum selesai: ") + h.bukti;
            Toast.makeText(this, belum, Toast.LENGTH_SHORT).show();
            announce(belum);
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
