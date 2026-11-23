package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat Animasi (Tahap 3F).
 *
 * Pilih proyek, jenis animasi (Fade/Geser/Putar/Pantul/Berdenyut),
 * durasi, dan target (tombol/teks/gambar), lalu tekan "Buat Animasi" —
 * IDE menulis berkas animasi sungguhan: res/anim/anim_&lt;jenis&gt;.xml
 * untuk proyek Java/Kotlin/Compose/Lua, atau animasi.css untuk proyek
 * HTML/Situs Web. Contoh kode pemakaian ditampilkan dan bisa disalin.
 */
public class AnimasiActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private Spinner spJenis;
    private Spinner spTarget;
    private EditText eDurasi;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvContoh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Pembuat Animasi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pembuat Animasi"), 20);
        label(r, TeksUI.t("Buat animasi untuk proyekmu tanpa menulis kode animasi. ")
                + TeksUI.t("Pilih proyek, jenis animasi, durasi (100 sampai 10000 milidetik), ")
                + TeksUI.t("dan targetnya — IDE menulis berkas animasi dan contoh kodenya."),
                false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Jenis animasi:", false);
        spJenis = new Spinner(this);
        String[] jenis = new String[PembuatAnimasi.SEMUA.length];
        for (int i = 0; i < jenis.length; i++) {
            jenis[i] = PembuatAnimasi.SEMUA[i].nama + " — " + PembuatAnimasi.SEMUA[i].penjelasan;
        }
        ArrayAdapter<String> adapterJenis = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, jenis);
        adapterJenis.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spJenis.setAdapter(adapterJenis);
        r.addView(spJenis, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Durasi (milidetik, 100-10000):", false);
        eDurasi = new EditText(this);
        GayaStudio.edit(eDurasi);
        eDurasi.setText("500");
        eDurasi.setInputType(InputType.TYPE_CLASS_NUMBER);
        r.addView(eDurasi, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Target animasi:", false);
        spTarget = new Spinner(this);
        ArrayAdapter<String> adapterTarget = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, PembuatAnimasi.TARGET);
        adapterTarget.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTarget.setAdapter(adapterTarget);
        r.addView(spTarget, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Contoh kode pemakaian:"), false);
        tvContoh = new TextView(this);
        tvContoh.setTextSize(13);
        tvContoh.setTextIsSelectable(true);
        tvContoh.setPadding(0, dp(4), 0, dp(4));
        tvContoh.setText("Contoh kode muncul di sini setelah animasi dibuat.");
        r.addView(tvContoh);

        button(r, TeksUI.t("Buat Animasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatAnimasi();
            }
        });
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pembuat Animasi. Pilih proyek dan jenis animasi, lalu tekan Buat Animasi."));
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

    private void buatAnimasi() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyek yang mau diberi animasi.");
            return;
        }
        String nama = String.valueOf(spProyek.getSelectedItem());
        int pos = spJenis.getSelectedItemPosition();
        if (pos < 0 || pos >= PembuatAnimasi.SEMUA.length) {
            toast("Pilih dulu jenis animasi.");
            return;
        }
        PembuatAnimasi.Jenis jenis = PembuatAnimasi.SEMUA[pos];
        int durasi = 500;
        try {
            durasi = Integer.parseInt(eDurasi.getText().toString().trim());
        } catch (Exception ignored) {
        }
        durasi = PembuatAnimasi.jepitDurasi(durasi);
        String target = String.valueOf(spTarget.getSelectedItem());
        File dirProyek = new File(ROOT + "/" + nama);
        String galat = PembuatAnimasi.buatAnimasi(dirProyek, jenis.id, durasi);
        if (galat != null) {
            toast(galat);
            return;
        }
        String tipe = InfoProyek.tipe(dirProyek);
        String contoh = PembuatAnimasi.contohKode(tipe, jenis.id, target);
        tvContoh.setText(contoh);
        String berkas = ("html".equals(tipe) || "situs".equals(tipe))
                ? "app/src/main/assets/animasi.css"
                : "app/src/main/res/anim/" + PembuatAnimasi.namaBerkas(jenis.id);
        String ok = "Animasi " + jenis.nama + TeksUI.t(" dibuat di ") + berkas
                + " (durasi " + durasi + TeksUI.t(" ms). Contoh kode pemakaian ditampilkan di bawah.");
        toast(ok);
        announce(ok);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Animasi berhasil dibuat"))
                .setMessage(ok + "\n\nContoh kode:\n" + contoh)
                .setPositiveButton("OK", null)
                .show();
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
