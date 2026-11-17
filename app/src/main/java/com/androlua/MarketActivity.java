package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
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
 * Pasar Proyek (Tahap 3D).
 *
 * Daftar proyek contoh bawaan IDE yang bisa dipasang ke Download/project
 * dengan satu ketuk. Pasar ONLINE sungguhan butuh server — pasar ini
 * lokal dulu (ditulis jujur di layar). Enam item memakai template siap
 * pakai (TemplateProyek); dua item hanya ada di pasar (Jam Digital,
 * Kuis Web).
 *
 * Pemasangan memakai BuatProyekHelper (jembatan buatProyek yang sama
 * seperti CreateProjectActivity) + PasarProyek.terapkanPasar() untuk
 * item pasar saja.
 */
public class MarketActivity extends Activity {

    private ListView lv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Pasar Proyek");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, "Pasar Proyek", 20);
        label(r, "Proyek contoh bawaan aplikasi — ketuk satu untuk memasangnya "
                + "ke folder proyekmu. Pasar online (unduh dari internet) belum "
                + "tersedia; pasar ini berisi contoh yang sudah ada di aplikasi.",
                false);

        List<String> baris = new ArrayList<String>();
        for (int i = 0; i < PasarProyek.SEMUA.length; i++) {
            PasarProyek.Item item = PasarProyek.SEMUA[i];
            baris.add(item.nama + "\n" + item.deskripsi);
        }
        lv = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, baris);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < PasarProyek.SEMUA.length) {
                    tanyaPasang(PasarProyek.SEMUA[pos]);
                }
            }
        });
        r.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

        label(r, "Ketuk proyek contoh untuk memasangnya. Proyek yang sudah "
                + "dipasang bisa dibuka di Kelola Project.", true);

        button(r, "Kembali ke menu sebelumnya", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce("Pasar Proyek. " + PasarProyek.SEMUA.length
                + " proyek contoh tersedia.");
    }

    private void tanyaPasang(final PasarProyek.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("Pasang " + item.nama + "?")
                .setMessage(item.deskripsi + "\n\nProyek akan dibuat di folder "
                        + "Download/project dengan nama \"" + item.nama + "\".")
                .setPositiveButton("Pasang", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pasang(item);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
        announce("Pasang proyek contoh " + item.nama + "?");
    }

    private void pasang(PasarProyek.Item item) {
        String dir = BuatProyekHelper.buat(this, item.nama, item.tipe, item.templateId);
        if (dir == null) {
            Toast.makeText(this, "Gagal memasang " + item.nama,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // Item pasar saja (templateId null) diterapkan oleh PasarProyek.
        if (item.templateId == null) {
            String paket = BuatProyekHelper.paketDariNama(new File(dir).getName());
            String galat = PasarProyek.terapkanPasar(new File(dir), item.id,
                    paket, new File(dir).getName());
            if (galat != null) {
                Toast.makeText(this, "Item pasar gagal diterapkan: " + galat,
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Proyek \"" + new File(dir).getName()
                + "\" berhasil dipasang dari pasar", Toast.LENGTH_SHORT).show();
        announce("Proyek " + new File(dir).getName()
                + " berhasil dipasang dari pasar. Buka di Kelola Project.");
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
