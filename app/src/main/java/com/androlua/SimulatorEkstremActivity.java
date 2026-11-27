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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Simulator Kondisi Perangkat Ekstrem dan Auditor Kebocoran Memori
 * (Fitur 10, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Jalankan Simulasi" — IDE menjalankan tiga
 * skenario tekanan (mati proses, jaringan putus, hemat baterai) atas
 * model keadaan layar contoh, lalu melacak objek yang tertahan
 * setelah layarnya ditutup. Logika ada di SimulatorEkstrem (murni
 * Java, sudah diuji di komputer).
 *
 * BATAS: ini simulasi atas model keadaan, bukan mematikan proses
 * sungguhan atau heap dump nyata (lihat SimulatorEkstrem).
 */
public class SimulatorEkstremActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
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
        setTitle(TeksUI.t("Simulator Kondisi Ekstrem dan Kebocoran Memori"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Simulator Kondisi Ekstrem dan Kebocoran Memori"), 20);
        label(r, TeksUI.t("Uji aplikasi saat proses dimatikan sistem, jaringan putus-nyambung, layar diputar, dan hemat baterai aktif, serta lacak objek yang tertahan di memori."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Jalankan Simulasi"), new View.OnClickListener() {
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
                + TeksUI.t("Jalankan Simulasi") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Simulator Kondisi Ekstrem dan Kebocoran Memori") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Jalankan Simulasi") + ".");
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
        tvHasil.setText(TeksUI.t("Jalankan Simulasi") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "SimulatorEkstrem").start();
    }

    private String jalankan() {
        SimulatorEkstrem.KeadaanLayar keadaan = new SimulatorEkstrem.KeadaanLayar();
        keadaan.tersimpan.put("nama", "Budi");
        keadaan.tersimpan.put("alamat", "Makassar");
        keadaan.hanyaMemori.put("catatan", "belum disimpan");
        keadaan.hanyaMemori.put("pilihan", "belum disimpan");
        List<String> permintaan = Arrays.asList("kirim-pesanan", "muat-katalog");
        List<String> antrean = Arrays.asList("kirim-pesanan");
        List<String> thread = Arrays.asList("sinkronisasi", "pemutar-musik");
        List<String> penting = Arrays.asList("pemutar-musik");
        List<SimulatorEkstrem.HasilSkenario> skenario = SimulatorEkstrem.jalankanSemua(
                keadaan, permintaan, antrean, thread, penting);
        SimulatorEkstrem.Pelacak pelacak = new SimulatorEkstrem.Pelacak();
        pelacak.daftarkan("LayarFormulir", "AktivitasUtama");
        pelacak.daftarkan("TugasLatar", "AktivitasUtama");
        pelacak.lepaskan("TugasLatar");
        List<SimulatorEkstrem.Kebocoran> bocor = pelacak.periksa("AktivitasUtama");
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Skenario lulus:")).append(" ");
        int lulus = 0;
        for (int i = 0; i < skenario.size(); i++) if (skenario.get(i).lulus) lulus++;
        b.append(lulus).append(" dari ").append(skenario.size()).append(". ");
        b.append(TeksUI.t("Kebocoran ditemukan:")).append(" ").append(bocor.size()).append(".");
        for (int i = 0; i < skenario.size(); i++) {
            b.append("\n- ").append(skenario.get(i).toString());
        }
        List<String> hilangPutar = SimulatorEkstrem.simulasiPutarLayar(keadaan);
        b.append("\n- ").append(TeksUI.t("Putar layar:")).append(" ")
                .append(hilangPutar.size()).append(" ").append(TeksUI.t("Kolom hilang:"))
                .append(" ").append(hilangPutar);
        for (int i = 0; i < bocor.size(); i++) {
            b.append("\n! ").append(bocor.get(i).toString());
        }
        return b.toString();
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
