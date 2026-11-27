package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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
 * Mesin Pembaruan Over-The-Air dan Manajemen Rilis Bertahap Mandiri
 * (Fitur 14, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tulis isi versi lama dan versi baru (satu baris per
 * baris), tekan "Buat Paket Patch" — IDE menghitung selisih, membuat
 * paket patch bertanda tangan di folder proyek, dan memeriksa apakah
 * perangkat ini masuk tahap awal peluncuran 10 persen. Logika ada di
 * MesinOTA (murni Java, sudah diuji di komputer).
 *
 * BATAS: patch bekerja atas berkas TEKS (skrip, aset, konfigurasi);
 * patch biner DEX/APK belum didukung dan patch diterapkan saat
 * aplikasi dibuka ulang.
 */
public class MesinOTAActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eLama;
    private EditText eBaru;
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
        setTitle(TeksUI.t("Mesin Pembaruan OTA dan Rilis Bertahap"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Mesin Pembaruan OTA dan Rilis Bertahap"), 20);
        label(r, TeksUI.t("Buat paket patch kecil dengan sidik jari SHA-256 dari selisih dua versi, atur persentase peluncuran bertahap, dan kembali ke versi lama bila patch gagal."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Isi versi lama:"), false);
        eLama = new EditText(this);
        GayaStudio.edit(eLama);
        eLama.setHint(TeksUI.t("Contoh: tarif=5000"));
        eLama.setText("tarif=5000\nrute=makassar-gowa\njam=06.00");
        eLama.setTextSize(14);
        eLama.setMinLines(3);
        r.addView(eLama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Isi versi baru:"), false);
        eBaru = new EditText(this);
        GayaStudio.edit(eBaru);
        eBaru.setHint(TeksUI.t("Contoh: tarif=6000"));
        eBaru.setText("tarif=6000\nrute=makassar-gowa\njam=06.00");
        eBaru.setTextSize(14);
        eBaru.setMinLines(3);
        r.addView(eBaru, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Buat Paket Patch"), new View.OnClickListener() {
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
        tvHasil.setText(TeksUI.t("Belum ada hasil. Tulis isi dua versi, lalu tekan") + " "
                + TeksUI.t("Buat Paket Patch") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Mesin Pembaruan OTA dan Rilis Bertahap") + ". " + TeksUI.t("Belum ada hasil. Tulis isi dua versi, lalu tekan")
                + " " + TeksUI.t("Buat Paket Patch") + ".");
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
        final String lama = eLama.getText().toString();
        final String baru = eBaru.getText().toString();
        tvHasil.setText(TeksUI.t("Buat Paket Patch") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir, lama, baru);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTA").start();
    }

    private String jalankan(File dir, String lama, String baru) {
        List<MesinOTA.BarisPatch> patch = MesinOTA.selisih(lama, baru);
        File berkasLama = new File(dir, "ota-lama.txt");
        File berkasBaru = new File(dir, "ota-baru.txt");
        tulisTeks(berkasLama, lama);
        tulisTeks(berkasBaru, baru);
        File paket = MesinOTA.buatPatch(berkasLama, berkasBaru, "patch-1",
                new File(dir, "patch-ota.zip"));
        if (paket == null) return TeksUI.t("Paket patch tidak bisa dibuat.");
        String idPerangkat = "";
        try {
            idPerangkat = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Throwable ignored) {
        }
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Ukuran patch:")).append(" ").append(paket.length())
                .append(" ").append(TeksUI.t("bita")).append(". ");
        b.append(TeksUI.t("Pengguna tahap awal:")).append(" 10% (");
        b.append(MesinOTA.kelompokPeluncuran(idPerangkat, "patch-1", 10)
                ? TeksUI.t("perangkat ini masuk") : TeksUI.t("perangkat ini belum masuk"));
        b.append(").");
        b.append("\n").append(MesinOTA.ringkasan(paket.length(),
                lama.length() + baru.length(), 10, idPerangkat, "patch-1"));
        b.append("\n").append(TeksUI.t("Baris patch:")).append(" ").append(patch.size()).append(".");
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
