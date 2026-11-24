package com.androlua;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pengaturan AI (perintah al kautsar 2026-11-24 13:16 WITA).
 *
 * Layar di Pengaturan, di samping "Pengaturan GitHub": pilih provider AI
 * yang dipakai fitur pembuat aplikasi berbasis AI online (DeepSeek, Grok,
 * Gemini, OpenRouter, Mistral), kotak centang "Beralih model saat
 * provider error" (bila dicentang dan model utama error, aplikasi
 * otomatis mencoba model cadangan dari provider yang sama — rantai di
 * PenyediaAi), dan tombol "Kelola Kunci API" (kunci cadangan).
 *
 * Pilihan disimpan di SharedPreferences privat "pengaturan":
 * ai_provider (bawaan "gemini"), ai_beralih_model (bawaan true).
 * Kunci API disimpan terpisah (KunciApi, pref "kunci_api").
 */
public class PengaturanAiActivity extends Activity {

    public static final String KUNCI_PROVIDER = "ai_provider";
    public static final String KUNCI_BERALIH_MODEL = "ai_beralih_model";

    private String kodeBahasa;
    private RadioGroup rgProvider;
    private TextView tvProviderSaatIni;
    private TextView tvModelUtama;
    private TextView tvModelCadangan;
    private CheckBox cbBeralih;

    /** Provider terpilih; bawaan PenyediaAi.KODE_BAWAAN ("gemini"). */
    public static String providerTerpilih(Context ctx) {
        String kode = PengaturanActivity.baca(ctx).getString(
                KUNCI_PROVIDER, PenyediaAi.KODE_BAWAAN);
        return PenyediaAi.dariKode(kode) == null
                ? PenyediaAi.KODE_BAWAAN : kode;
    }

    /** Apakah pengalihan otomatis ke model cadangan aktif? (bawaan true). */
    public static boolean beralihModelAktif(Context ctx) {
        return PengaturanActivity.baca(ctx).getBoolean(KUNCI_BERALIH_MODEL, true);
    }

    /** Model utama provider terpilih saat ini. */
    public static String modelUtamaAktif(Context ctx) {
        return PenyediaAi.modelUtama(providerTerpilih(ctx));
    }

    /** Apakah ada minimal satu kunci tersimpan untuk provider ini? */
    public static boolean kunciTersedia(Context ctx, String kodeProvider) {
        if (ctx == null || kodeProvider == null) return false;
        final SharedPreferences pref = ctx.getSharedPreferences(
                "kunci_api", Context.MODE_PRIVATE);
        KunciApi.Penyimpanan s = new KunciApi.Penyimpanan() {
            @Override
            public String baca(String kunci, String bawaan) {
                return pref.getString(kunci, bawaan);
            }

            @Override
            public void tulis(String kunci, String nilai) {
                pref.edit().putString(kunci, nilai).apply();
            }
        };
        return KunciApi.jumlahKunci(s, kodeProvider) > 0;
    }

    /**
     * Model berikutnya yang harus dicoba bila modelSaatIni error.
     * Mengembalikan null bila pengalihan dimatikan, provider/model
     * tidak dikenal, atau rantai sudah habis.
     */
    public static String modelCadanganBerikutnya(Context ctx, String modelSaatIni) {
        if (!beralihModelAktif(ctx)) return null;
        return PenyediaAi.modelCadanganSesudah(providerTerpilih(ctx), modelSaatIni);
    }

    private String t(String kunci) {
        return TeksUI.teks(kodeBahasa, kunci);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kodeBahasa = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;
        setTitle(t("Pengaturan AI"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, t("Pengaturan AI"), 20);
        label(r, t("Penyedia AI untuk pembuatan aplikasi (online menyusul; saat ini masih AI lokal)"), false);

        // ---- Pilih provider (RadioGroup; nama + model utama) ----
        rgProvider = new RadioGroup(this);
        rgProvider.setOrientation(RadioGroup.VERTICAL);
        final String providerAwal = providerTerpilih(this);
        PenyediaAi.Provider[] semua = PenyediaAi.SEMUA;
        for (int i = 0; i < semua.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(semua[i].nama + " — " + semua[i].modelUtama());
            rb.setTag(semua[i].kode);
            rb.setTextSize(15);
            rb.setPadding(0, dp(4), 0, dp(4));
            rb.setChecked(semua[i].kode.equals(providerAwal));
            rgProvider.addView(rb);
        }
        r.addView(rgProvider);
        rgProvider.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton rb = (RadioButton) group.findViewById(checkedId);
                if (rb == null) return;
                String kode = (String) rb.getTag();
                SharedPreferences.Editor ed = PengaturanActivity.baca(
                        PengaturanAiActivity.this).edit();
                ed.putString(KUNCI_PROVIDER, kode);
                ed.apply();
                perbaruiInfoModel(kode);
                announce(t("Provider saat ini:") + " "
                        + PenyediaAi.dariKode(kode).nama);
            }
        });

        tvProviderSaatIni = label(r, "", false);
        tvModelUtama = label(r, "", false);
        tvModelCadangan = label(r, "", false);
        perbaruiInfoModel(providerAwal);

        // ---- Kotak centang beralih model saat provider error ----
        cbBeralih = new CheckBox(this);
        cbBeralih.setText(t("Beralih model saat provider error"));
        cbBeralih.setTextSize(15);
        cbBeralih.setPadding(0, dp(10), 0, dp(2));
        cbBeralih.setChecked(beralihModelAktif(this));
        cbBeralih.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor ed = PengaturanActivity.baca(
                        PengaturanAiActivity.this).edit();
                ed.putBoolean(KUNCI_BERALIH_MODEL, isChecked);
                ed.apply();
            }
        });
        r.addView(cbBeralih);
        label(r, t("Bila dicentang, bila model utama error, aplikasi akan mencoba model cadangan dari provider yang sama (berlaku saat pemakaian penyedia online sudah aktif)."), true);

        // ---- Kelola Kunci API ----
        button(r, t("Kelola Kunci API"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(PengaturanAiActivity.this,
                            KelolaKunciActivity.class));
                } catch (Throwable e) {
                    Toast.makeText(PengaturanAiActivity.this,
                            t("Gagal membuka Kelola Kunci API: ") + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        int jumlah = KunciApi.jumlahKunci(penyimpananKunci(),
                providerTerpilih(this));
        label(r, t("Kunci API tersimpan") + ": " + jumlah, true);
        label(r, t("Kunci API hanya tersimpan di HP ini"), true);
    }

    /** Perbarui label provider/model sesudah pilihan berubah. */
    private void perbaruiInfoModel(String kode) {
        PenyediaAi.Provider p = PenyediaAi.dariKode(kode);
        if (p == null) return;
        tvProviderSaatIni.setText(t("Provider saat ini:") + " " + p.nama);
        tvModelUtama.setText(t("Model utama: ") + p.modelUtama());
        String[] rantai = PenyediaAi.rantaiModel(kode);
        StringBuilder cadangan = new StringBuilder();
        for (int i = 1; i < rantai.length; i++) {
            if (i > 1) cadangan.append(", ");
            cadangan.append(rantai[i]);
        }
        tvModelCadangan.setText(t("Model cadangan: ")
                + (cadangan.length() == 0 ? "-" : cadangan.toString()));
    }

    /** Penyimpanan kunci (SharedPreferences privat "kunci_api"). */
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
