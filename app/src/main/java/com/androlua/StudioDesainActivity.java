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
 * tema XML dan Compose, lalu menyimpannya di folder proyek. Token
 * juga dicatat di token-studio.txt proyek; beri nama set, tekan
 * "Simpan ke Pustaka Bersama", dan set itu tersimpan di folder
 * bersama .pustaka-token di bawah akar proyek sehingga semua proyek
 * dapat memakainya. "Sinkronkan ke Proyek yang Memakai" menerapkan
 * token pustaka terbaru ke semua proyek yang memakai set itu.
 * Logika ada di StudioDesain (murni Java, sudah diuji di komputer).
 *
 * BATAS: komponen dihasilkan sebagai teks; tempelkan ke layout lalu
 * periksa dengan Arsitek Tata Letak. Sinkronisasi bekerja lewat
 * berkas bersama di penyimpanan yang sama (luring, tanpa server).
 */
public class StudioDesainActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eToken;
    private Spinner spKomponen;
    private EditText eNamaSet;
    private Spinner spPustaka;
    private ArrayAdapter<String> adapterPustaka;
    private final List<String> daftarPustaka = new ArrayList<String>();
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

        label(r, TeksUI.t("Nama set token:"), false);
        eNamaSet = new EditText(this);
        GayaStudio.edit(eNamaSet);
        eNamaSet.setHint(TeksUI.t("Contoh: Utama"));
        eNamaSet.setText("Utama");
        eNamaSet.setTextSize(14);
        eNamaSet.setSingleLine(true);
        r.addView(eNamaSet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pustaka token bersama:"), false);
        spPustaka = new Spinner(this);
        adapterPustaka = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarPustaka);
        adapterPustaka.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPustaka.setAdapter(adapterPustaka);
        r.addView(spPustaka, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Simpan ke Pustaka Bersama"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanPustaka();
            }
        });

        button(r, TeksUI.t("Muat dari Pustaka"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muatPustaka();
            }
        });

        button(r, TeksUI.t("Sinkronkan ke Proyek yang Memakai"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sinkronPustaka();
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
        daftarPustaka.clear();
        daftarPustaka.addAll(StudioDesain.daftarPustaka(dirPustaka()));
        if (adapterPustaka != null) adapterPustaka.notifyDataSetChanged();
    }

    private File dirPustaka() {
        return new File(ROOT + "/" + StudioDesain.DIR_PUSTAKA);
    }

    /** Nama set dari kotak isi (kosong = "Utama"). */
    private String namaSet() {
        String nama = eNamaSet == null ? "" : eNamaSet.getText().toString().trim();
        return nama.length() == 0 ? "Utama" : nama;
    }

    /** Nama set yang dipilih di spinner pustaka ("" bila belum ada). */
    private String setTerpilih() {
        if (spPustaka == null || spPustaka.getSelectedItem() == null) return "";
        return String.valueOf(spPustaka.getSelectedItem());
    }

    private void simpanPustaka() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final String tokenTeks = eToken.getText().toString();
        final String nama = namaSet();
        tvHasil.setText(TeksUI.t("Simpan ke Pustaka Bersama") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                StudioDesain.Token token = StudioDesain.uraiToken(tokenTeks);
                token.nama = nama;
                List<String> masalah = StudioDesain.validasi(token);
                String hasil;
                if (masalah.size() > 0) {
                    StringBuilder b = new StringBuilder();
                    b.append(TeksUI.t("Token tidak valid:"));
                    for (int i = 0; i < masalah.size(); i++) b.append("\n- ").append(masalah.get(i));
                    hasil = b.toString();
                } else if (StudioDesain.simpanKePustaka(dirPustaka(), token)
                        && StudioDesain.terapkanKeProyek(dir, token)) {
                    hasil = TeksUI.t("Set token disimpan ke pustaka bersama:") + " " + nama + ".";
                } else {
                    hasil = TeksUI.t("Set token tidak bisa disimpan.");
                }
                final String tampil = hasil;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(tampil);
                        announce(tampil);
                        muatDaftar();
                    }
                });
            }
        }, "StudioDesainPustaka").start();
    }

    private void muatPustaka() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final String nama = setTerpilih();
        if (nama.length() == 0) {
            toast(TeksUI.t("Pilih dulu set tokennya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Muat dari Pustaka") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StudioDesain.Token token = StudioDesain.muatDariPustaka(dirPustaka(), nama);
                final String hasil;
                if (token == null) {
                    hasil = TeksUI.t("Set token tidak ditemukan di pustaka bersama.");
                } else if (StudioDesain.terapkanKeProyek(dir, token)) {
                    hasil = TeksUI.t("Set token diterapkan ke proyek ini:") + " " + nama + ".";
                } else {
                    hasil = TeksUI.t("Set token tidak bisa diterapkan.");
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                        if (token != null) {
                            eToken.setText(StudioDesain.tokenKeTeks(token));
                            eNamaSet.setText(token.nama);
                        }
                    }
                });
            }
        }, "StudioDesainPustaka").start();
    }

    private void sinkronPustaka() {
        final String nama = setTerpilih();
        if (nama.length() == 0) {
            toast(TeksUI.t("Pilih dulu set tokennya."));
            return;
        }
        tvHasil.setText(TeksUI.t("Sinkronkan ke Proyek yang Memakai") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                StudioDesain.HasilSinkron hasil = StudioDesain.sinkronkanKeProyekMemakai(
                        new File(ROOT), dirPustaka(), nama);
                final String tampil = TeksUI.t("Token disinkronkan ke proyek yang memakai:")
                        + " " + nama + ".\n" + hasil.toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(tampil);
                        announce(tampil);
                    }
                });
            }
        }, "StudioDesainSinkron").start();
    }

    private void mulai() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final String tokenTeks = eToken.getText().toString();
        final String jenis = String.valueOf(spKomponen.getSelectedItem());
        final String nama = namaSet();
        tvHasil.setText(TeksUI.t("Hasilkan Komponen") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir, tokenTeks, jenis, nama);
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

    private String jalankan(File dir, String tokenTeks, String jenis, String namaSet) {
        StudioDesain.Token token = StudioDesain.uraiToken(tokenTeks);
        List<String> masalah = StudioDesain.validasi(token);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Komponen dihasilkan:")).append(" ");
        if (masalah.size() > 0) {
            b.append("0. ").append(TeksUI.t("Token tidak valid:"));
            for (int i = 0; i < masalah.size(); i++) b.append("\n- ").append(masalah.get(i));
            return b.toString();
        }
        // Catat token di proyek ini (dengan nama setnya) supaya proyek
        // ini ikut tersinkron bila set itu diubah di pustaka bersama.
        token.nama = namaSet;
        token.sumber = "pustaka:" + namaSet;
        StudioDesain.simpanTokenProyek(dir, token);
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
