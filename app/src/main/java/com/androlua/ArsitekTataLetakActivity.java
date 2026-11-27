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
 * Arsitek Tata Letak Berbasis Hirarki Pohon Teks dan Wireframe Spasial
 * (Fitur 3, daftar Gemini 2026-11-27).
 *
 * Pilih proyek dan berkas layout, tekan "Periksa Tata Letak" — IDE
 * membaca XML sebagai pohon teks, membacakan narasi posisi tiap
 * elemen, dan melaporkan tabrakan serta elemen yang keluar layar
 * pada lebar 360 dp. Logika ada di ArsitekTataLetak (murni Java,
 * sudah diuji di komputer).
 *
 * BATAS: yang dihitung adalah LinearLayout dan atribut dp sederhana;
 * ConstraintLayout dan Compose dilaporkan "posisi tidak tentu".
 */
public class ArsitekTataLetakActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spLayout;
    private ArrayAdapter<String> adapterLayout;
    private final List<String> daftarLayout = new ArrayList<String>();
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
        setTitle(TeksUI.t("Arsitek Tata Letak Pohon Teks"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Arsitek Tata Letak Pohon Teks"), 20);
        label(r, TeksUI.t("Baca layout XML sebagai pohon teks dengan narasi posisi tiap elemen, lalu periksa tabrakan dan elemen yang keluar layar secara matematis."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        spProyek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                muatLayout();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih berkas layout:"), false);
        spLayout = new Spinner(this);
        adapterLayout = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarLayout);
        adapterLayout.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLayout.setAdapter(adapterLayout);
        r.addView(spLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Periksa Tata Letak"), new View.OnClickListener() {
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
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Periksa Tata Letak") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Arsitek Tata Letak Pohon Teks") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Periksa Tata Letak") + ".");
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
        muatLayout();
    }

    private void muatLayout() {
        daftarLayout.clear();
        dirAktif = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
            File[] lokasi = {new File(dirAktif, "app/src/main/res/layout"),
                    new File(dirAktif, "res/layout")};
            for (int i = 0; i < lokasi.length; i++) {
                if (!lokasi[i].isDirectory()) continue;
                File[] daftar = lokasi[i].listFiles();
                if (daftar == null) continue;
                for (int j = 0; j < daftar.length; j++) {
                    if (daftar[j].getName().endsWith(".xml")) {
                        daftarLayout.add(lokasi[i].getName() + "/" + daftar[j].getName());
                    }
                }
            }
        }
        if (adapterLayout != null) adapterLayout.notifyDataSetChanged();
    }

    private void mulai() {
        if (dirAktif == null || spLayout == null || spLayout.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File berkas = new File(dirAktif, "app/src/main/res/layout/"
                + String.valueOf(spLayout.getSelectedItem()).replace("layout/", ""));
        final File berkas2 = new File(dirAktif, "res/layout/"
                + String.valueOf(spLayout.getSelectedItem()).replace("layout/", ""));
        tvHasil.setText(TeksUI.t("Periksa Tata Letak") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File pakai = berkas.isFile() ? berkas : berkas2;
                final String hasil = jalankan(pakai);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "ArsitekTataLetak").start();
    }

    private String jalankan(File berkas) {
        String xml = bacaTeks(berkas);
        if (xml == null) return TeksUI.t("Berkas layout tidak bisa dibaca.");
        ArsitekTataLetak.Simpul akar = ArsitekTataLetak.urai(xml);
        if (akar == null) return TeksUI.t("Berkas layout tidak bisa dibaca.");
        ArsitekTataLetak.hitungKotak(akar, 360);
        List<ArsitekTataLetak.Temuan> temuan = ArsitekTataLetak.periksa(akar, 360);
        List<String> narasi = ArsitekTataLetak.narasi(akar, 360);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Elemen diperiksa:")).append(" ")
                .append(ArsitekTataLetak.hitungSimpul(akar)).append(". ")
                .append(ArsitekTataLetak.ringkasan(ArsitekTataLetak.hitungSimpul(akar), temuan));
        for (int i = 0; i < narasi.size(); i++) b.append("\n- ").append(narasi.get(i));
        for (int i = 0; i < temuan.size(); i++) b.append("\n! ").append(temuan.get(i).toString());
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
