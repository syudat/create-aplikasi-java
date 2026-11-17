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
 * Peta Proyek (Tahap 3I).
 *
 * Pilih proyek, lalu "Tampilkan Peta" untuk melihat struktur proyek
 * (daftar folder + berkas + ukuran + ukuran per jenis + berkas
 * penting) di layar, atau "Simpan peta-proyek.txt" untuk menulis peta
 * yang sama ke akar proyek.
 *
 * Folder bin/ (hasil build) dan .git/ (repo lokal) tidak ikut
 * dipetakan karena isinya bisa dibuat ulang.
 */
public class PetaActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Peta Proyek");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, "Peta Proyek", 20);
        label(r, "Lihat struktur proyek: daftar folder dan berkas beserta "
                + "ukurannya, total ukuran, ukuran per jenis berkas, dan "
                + "berkas penting (berkas utama, manifest, layout, strings, "
                + "ikon). Folder bin/ dan .git/ tidak ikut dipetakan.", false);

        label(r, "Pilih proyek:", false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Tampilkan Peta", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanPeta();
            }
        });
        button(r, "Simpan peta-proyek.txt", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanPeta();
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
        announce("Peta Proyek. Pilih proyek, lalu tekan Tampilkan Peta.");
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

    private File dirTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        return new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
    }

    private void tampilkanPeta() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        String teks = PetaProyek.teks(dir);
        if (tvHasil != null) tvHasil.setText(teks);
        PetaProyek.Peta p = PetaProyek.peta(dir);
        announce("Peta proyek " + dir.getName() + ": " + p.jumlahBerkas
                + " berkas, total " + PetaProyek.formatUkuran(p.totalUkuran) + ".");
    }

    private void simpanPeta() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        File hasil = PetaProyek.tulisPeta(dir);
        if (hasil == null) {
            toast("Gagal menyimpan peta proyek.");
            return;
        }
        String ok = "Peta disimpan ke peta-proyek.txt di proyek " + dir.getName() + ".";
        toast(ok);
        announce(ok);
        if (tvHasil != null) tvHasil.setText(PetaProyek.teks(dir));
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
