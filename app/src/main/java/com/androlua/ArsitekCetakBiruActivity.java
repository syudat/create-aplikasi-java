package com.androlua;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
 * Arsitek Cetak Biru Proyek dan Pengurai Tugas Bertahap Berbasis AI
 * (Fitur 11, daftar Gemini 2026-11-27).
 *
 * Tulis gagasan aplikasi, tekan "Buat Cetak Biru" — IDE meminta
 * cetak biru ke penyedia AI AKTIF di Pengaturan AI (DeepSeek, Grok,
 * Gemini, OpenRouter, Mistral) memakai kunci tersimpan di
 * KelolaKunciActivity, lalu menyimpannya sebagai cetak-biru.txt di
 * folder proyek. Tekan "Tonggak Berikutnya Selesai" untuk menandai
 * tonggak yang sedang dikerjakan. Logika ada di ArsitekCetakBiru
 * (murni Java, sudah diuji di komputer).
 *
 * Bila tidak ada kunci API untuk provider aktif, atau AI tidak
 * dapat dihubungi (tidak ada internet, kunci ditolak, model error),
 * layar jatuh kembali ke pola kata kunci luring dan MENYATAKAN
 * batas itu di layar — tidak diam-diam.
 */
public class ArsitekCetakBiruActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eGagasan;
    private TextView tvHasil;
    private Button bJalankan;
    private Button bTonggak;
    private ArsitekCetakBiru.CetakBiru biruAktif;
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
        setTitle(TeksUI.t("Arsitek Cetak Biru Proyek"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Arsitek Cetak Biru Proyek"), 20);
        label(r, TeksUI.t("Ubah gagasan aplikasi menjadi cetak biru multi-berkas — pohon folder, model data, dan tonggak kerja — lalu bangun satu tonggak demi satu tonggak."), false);
        label(r, TeksUI.t("Bila ada kunci API di Pengaturan AI, cetak biru disusun oleh penyedia AI aktif; bila tidak ada, dipakai pola luring di perangkat."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Gagasan aplikasi:"), false);
        eGagasan = new EditText(this);
        GayaStudio.edit(eGagasan);
        eGagasan.setHint(TeksUI.t("Contoh: aplikasi kasir toko kelontong dengan cicilan"));
        eGagasan.setTextSize(14);
        eGagasan.setMinLines(2);
        r.addView(eGagasan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Buat Cetak Biru"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });
        bTonggak = button(r, TeksUI.t("Tonggak Berikutnya Selesai"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selesaiTonggak();
            }
        });
        // Fase 3: muat kembali cetak biru yang tersimpan di cetak-biru.txt
        // (sebelumnya layar yang dibuka ulang kehilangan tonggaknya).
        button(r, TeksUI.t("Muat Cetak Biru Tersimpan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muatBiru();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada cetak biru. Tulis gagasan aplikasi, lalu tekan") + " "
                + TeksUI.t("Buat Cetak Biru") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Arsitek Cetak Biru Proyek") + ". " + TeksUI.t("Belum ada cetak biru. Tulis gagasan aplikasi, lalu tekan")
                + " " + TeksUI.t("Buat Cetak Biru") + ".");
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
        final String gagasan = eGagasan.getText().toString().trim();
        if (gagasan.length() == 0) {
            toast(TeksUI.t("Belum ada cetak biru. Tulis gagasan aplikasi, lalu tekan") + " "
                    + TeksUI.t("Buat Cetak Biru") + ".");
            return;
        }
        dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Meminta cetak biru ke AI") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanAi(gagasan);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "ArsitekCetakBiru").start();
    }

    /**
     * Susun cetak biru: AI aktif dulu (kunci + model cadangan),
     * jatuh kembali ke pola luring dengan catatan batas yang jelas.
     * Berjalan di utas latar; nilai kunci API tidak pernah ditulis
     * ke layar atau log.
     */
    private String jalankanAi(String gagasan) {
        String kode = PengaturanAiActivity.providerTerpilih(this);
        PenyediaAi.Provider p = PenyediaAi.dariKode(kode);
        String namaProvider = p == null ? kode : p.nama;
        String catatan;
        if (!PengaturanAiActivity.kunciTersedia(this, kode)) {
            biruAktif = ArsitekCetakBiru.buat(gagasan);
            catatan = TeksUI.t("Tidak ada kunci API untuk") + " " + namaProvider + ". "
                    + TeksUI.t("Cetak biru disusun luring dari pola kata kunci di perangkat. Tambahkan kunci di Pengaturan AI, menu Kelola Kunci API, untuk memakai AI.");
        } else {
            ArsitekCetakBiru.JawabanAi jawaban = ArsitekCetakBiru.tanyaDenganCadangan(
                    penyimpananKunci(), kode,
                    PengaturanAiActivity.beralihModelAktif(this),
                    ArsitekCetakBiru.perintahCetakBiru(gagasan),
                    new ArsitekCetakBiru.PabrikPemanggil() {
                        @Override
                        public ArsitekCetakBiru.PemanggilAi buat(
                                String kodeProvider, String model, String kunci) {
                            return new ArsitekCetakBiru.PemanggilAiHttp(
                                    kodeProvider, model, kunci);
                        }
                    });
            if (jawaban.dariAi) {
                ArsitekCetakBiru.CetakBiru ai =
                        ArsitekCetakBiru.buatDenganAi(gagasan, jawaban);
                if (ai != null) {
                    biruAktif = ai;
                    catatan = TeksUI.t("Cetak biru disusun oleh") + " " + jawaban.penyedia
                            + " (" + jawaban.model + ").";
                } else {
                    biruAktif = ArsitekCetakBiru.buat(gagasan);
                    catatan = TeksUI.t("Jawaban AI tidak dapat dipakai sebagai cetak biru, jadi dipakai pola luring.");
                }
            } else {
                biruAktif = ArsitekCetakBiru.buat(gagasan);
                catatan = TeksUI.t("AI tidak dapat dihubungi") + " (" + jawaban.galat + "). "
                        + TeksUI.t("Cetak biru disusun luring dari pola kata kunci di perangkat. Periksa internet dan kunci API di Pengaturan AI.");
            }
        }
        tulisTeks(new File(dirAktif, "cetak-biru.txt"),
                ArsitekCetakBiru.eksporTeks(biruAktif));
        return catatan + "\n" + TeksUI.t("Tonggak dibuat:") + " " + biruAktif.tonggak.size() + ". "
                + ArsitekCetakBiru.ringkasan(biruAktif)
                + "\n" + ArsitekCetakBiru.eksporTeks(biruAktif);
    }

    /** Penyimpanan kunci (SharedPreferences privat "kunci_api", sama seperti PengaturanAiActivity). */
    private KunciApi.Penyimpanan penyimpananKunci() {
        final SharedPreferences pref = getSharedPreferences(
                "kunci_api", Context.MODE_PRIVATE);
        return new KunciApi.Penyimpanan() {
            @Override
            public String baca(String kunci, String bawaan) {
                return pref.getString(kunci, bawaan);
            }

            @Override
            public void tulis(String kunci, String nilai) {
                pref.edit().putString(kunci, nilai).apply();
            }
        };
    }

    private void selesaiTonggak() {
        if (biruAktif == null) {
            toast(TeksUI.t("Belum ada cetak biru. Tulis gagasan aplikasi, lalu tekan") + " "
                    + TeksUI.t("Buat Cetak Biru") + ".");
            return;
        }
        ArsitekCetakBiru.Tonggak berikut = ArsitekCetakBiru.tonggakBerikutnya(biruAktif);
        if (berikut == null) {
            tvHasil.setText(TeksUI.t("Semua tonggak sudah selesai."));
            announce(TeksUI.t("Semua tonggak sudah selesai."));
            return;
        }
        int nomor = biruAktif.tonggak.indexOf(berikut) + 1;
        ArsitekCetakBiru.tandaiSelesai(biruAktif, nomor);
        if (dirAktif != null) {
            tulisTeks(new File(dirAktif, "cetak-biru.txt"),
                    ArsitekCetakBiru.eksporTeks(biruAktif));
        }
        String hasil = TeksUI.t("Tonggak dibuat:") + " " + biruAktif.tonggak.size() + ". "
                + ArsitekCetakBiru.ringkasan(biruAktif)
                + "\n" + ArsitekCetakBiru.eksporTeks(biruAktif);
        tvHasil.setText(hasil);
        announce(hasil);
    }

    private void muatBiru() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        String isi = bacaTeks(new File(dirAktif, "cetak-biru.txt"));
        if (isi == null || isi.trim().length() == 0) {
            toast(TeksUI.t("Cetak biru tersimpan tidak ditemukan di proyek ini."));
            return;
        }
        biruAktif = ArsitekCetakBiru.uraiTeks(isi);
        String hasil = TeksUI.t("Tonggak dibuat:") + " " + biruAktif.tonggak.size() + ". "
                + ArsitekCetakBiru.ringkasan(biruAktif)
                + "\n" + ArsitekCetakBiru.eksporTeks(biruAktif);
        tvHasil.setText(hasil);
        announce(hasil);
    }

    private String bacaTeks(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
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
