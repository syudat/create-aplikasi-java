package com.androlua;

import android.app.Activity;
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
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Narator Alur Logika Kode dan Peninjau Semantik Berbasis AI
 * (Fitur 12, daftar Gemini 2026-11-27).
 *
 * Pilih proyek dan berkas kode, tekan "Narasi Kode" — IDE mengubah
 * kode menjadi narasi alur berbahasa Indonesia, menampilkan peta
 * panggilan antar-metode, dan menandai potensi bug halus beserta
 * nomor barisnya. Logika ada di NaratorLogika (murni Java, sudah
 * diuji di komputer).
 *
 * BATAS: narasi memakai pola teks luring, bukan model AI online
 * (lihat NaratorLogika).
 */
public class NaratorLogikaActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spBerkas;
    private ArrayAdapter<String> adapterBerkas;
    private final List<String> daftarBerkas = new ArrayList<String>();
    private TextView tvHasil;
    private Button bJalankan;
    private File dirAktif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Narator Alur Logika Kode"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Narator Alur Logika Kode"), 20);
        label(r, TeksUI.t("Ubah kode panjang menjadi narasi alur berbahasa Indonesia yang runtut, lengkap dengan percabangan penting dan peringatan bug halus beserta nomor barisnya."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        spProyek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                muatBerkas();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih berkas kode:"), false);
        spBerkas = new Spinner(this);
        adapterBerkas = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarBerkas);
        adapterBerkas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBerkas.setAdapter(adapterBerkas);
        r.addView(spBerkas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Narasi Kode"), new View.OnClickListener() {
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
        tvHasil.setText(TeksUI.t("Belum ada narasi. Pilih proyek dan berkas, lalu tekan") + " "
                + TeksUI.t("Narasi Kode") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Narator Alur Logika Kode") + ". " + TeksUI.t("Belum ada narasi. Pilih proyek dan berkas, lalu tekan")
                + " " + TeksUI.t("Narasi Kode") + ".");
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
        muatBerkas();
    }

    private void muatBerkas() {
        daftarBerkas.clear();
        dirAktif = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
            cariKode(dirAktif, dirAktif);
        }
        if (adapterBerkas != null) adapterBerkas.notifyDataSetChanged();
    }

    private void cariKode(File akar, File kini) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) cariKode(akar, f);
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")
                    || f.getName().endsWith(".py")) {
                daftarBerkas.add(f.getAbsolutePath().substring(akar.getAbsolutePath().length() + 1));
            }
        }
    }

    private void mulai() {
        if (dirAktif == null || spBerkas == null || spBerkas.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File berkas = new File(dirAktif, String.valueOf(spBerkas.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Narasi Kode") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(berkas);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "NaratorLogika").start();
    }

    private String jalankan(File berkas) {
        String kode = bacaTeks(berkas);
        if (kode == null) return TeksUI.t("Berkas kode tidak bisa dibaca.");
        List<NaratorLogika.Langkah> langkah = NaratorLogika.narasi(kode);
        List<String> peta = NaratorLogika.petaPanggilan(kode);
        List<NaratorLogika.Peringatan> peringatan = NaratorLogika.periksaBug(kode);
        StringBuilder b = new StringBuilder();
        b.append(NaratorLogika.tujuanUtama(kode)).append("\n");
        b.append(TeksUI.t("Langkah alur:")).append(" ").append(langkah.size()).append(". ");
        b.append(TeksUI.t("Peringatan ditemukan:")).append(" ").append(peringatan.size()).append(".");
        for (int i = 0; i < langkah.size(); i++) b.append("\n- ").append(langkah.get(i).toString());
        if (peta.size() > 0) {
            b.append("\n").append(TeksUI.t("Peta panggilan:"));
            for (int i = 0; i < peta.size(); i++) b.append("\n- ").append(peta.get(i));
        }
        for (int i = 0; i < peringatan.size(); i++) {
            b.append("\n! ").append(peringatan.get(i).toString());
        }
        return b.toString();
    }

    private String bacaTeks(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
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
