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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manajer Dependensi Maven Luring dan Penyelesai Konflik Pustaka
 * (Fitur 7, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Periksa Dependensi" — IDE membaca dependensi
 * dari berkas Gradle proyek, memeriksa brankas luring, dan
 * menyelesaikan bentrokan versi ke versi tertinggi. Isi koordinat
 * pustaka untuk menambahkannya ke brankas. Logika ada di
 * ManajerDependensi (murni Java, sudah diuji di komputer).
 *
 * BATAS: pengunduhan butuh internet — saat luring hanya pustaka yang
 * sudah ada di brankas yang bisa dipakai.
 */
public class ManajerDependensiActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText ePustaka;
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
        setTitle(TeksUI.t("Manajer Dependensi Luring"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Manajer Dependensi Luring"), 20);
        label(r, TeksUI.t("Simpan pustaka AAR, JAR, dan WHL di brankas luring HP, lalu selesaikan bentrokan versi sebelum build tanpa internet."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Tambah pustaka ke brankas (kosongkan bila hanya memeriksa):"), false);
        ePustaka = new EditText(this);
        GayaStudio.edit(ePustaka);
        ePustaka.setHint(TeksUI.t("Contoh: com.google.code.gson:gson:2.10.1"));
        ePustaka.setTextSize(14);
        ePustaka.setSingleLine(true);
        r.addView(ePustaka, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Periksa Dependensi"), new View.OnClickListener() {
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
                + TeksUI.t("Periksa Dependensi") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Manajer Dependensi Luring") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Periksa Dependensi") + ".");
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
        final String tambah = ePustaka.getText().toString().trim();
        tvHasil.setText(TeksUI.t("Periksa Dependensi") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir, tambah);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "ManajerDependensi").start();
    }

    private String jalankan(File dir, String tambah) {
        File indeks = new File(dir, "brankas-maven.txt");
        List<ManajerDependensi.Pustaka> brankas =
                ManajerDependensi.uraiIndeks(bacaTeks(indeks));
        StringBuilder b = new StringBuilder();
        if (tambah.length() > 0) {
            ManajerDependensi.Pustaka p = ManajerDependensi.uraiKoordinat(tambah);
            if (p == null) {
                return TeksUI.t("Koordinat pustaka tidak dikenali. Contoh: com.google.code.gson:gson:2.10.1");
            }
            if (!ManajerDependensi.sudahAda(brankas, p)) {
                brankas.add(p);
                StringBuilder isi = new StringBuilder();
                for (int i = 0; i < brankas.size(); i++) {
                    isi.append(ManajerDependensi.keBarisIndeks(brankas.get(i))).append("\n");
                }
                tulisTeks(indeks, isi.toString());
            }
            b.append(TeksUI.t("Ditambahkan:")).append(" ").append(p.koordinat()).append(".\n");
        }
        List<ManajerDependensi.Pustaka> diminta = new ArrayList<ManajerDependensi.Pustaka>();
        cariGradle(dir, diminta);
        List<ManajerDependensi.Bentrokan> bentrokan = new ArrayList<ManajerDependensi.Bentrokan>();
        List<ManajerDependensi.Pustaka> selesai =
                ManajerDependensi.selesaikan(diminta, bentrokan);
        b.append(TeksUI.t("Pustaka di brankas:")).append(" ").append(brankas.size()).append(". ");
        b.append(TeksUI.t("Bentrokan ditemukan:")).append(" ").append(bentrokan.size()).append(".");
        for (int i = 0; i < selesai.size(); i++) {
            b.append("\n- ").append(selesai.get(i).koordinat());
        }
        for (int i = 0; i < bentrokan.size(); i++) {
            b.append("\n! ").append(bentrokan.get(i).toString());
        }
        return b.toString();
    }

    private void cariGradle(File dir, List<ManajerDependensi.Pustaka> keluar) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) cariGradle(f, keluar);
            } else if (f.getName().endsWith(".gradle")) {
                String isi = bacaTeks(f);
                if (isi == null) continue;
                String[] baris = isi.split("\n");
                for (int j = 0; j < baris.length; j++) {
                    ManajerDependensi.Pustaka p = ManajerDependensi.uraiGradle(baris[j]);
                    if (p != null) keluar.add(p);
                }
            }
        }
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
