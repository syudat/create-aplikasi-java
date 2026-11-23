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
import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat Iklan (Tahap 3I).
 *
 * Pilih proyek, penyedia (AdMob / Unity Ads / AppLovin), dan jenis
 * iklan (Banner / Interstitial / Rewarded), isi ID unit bila punya
 * (kosong = pakai ID uji), lalu tekan "Buat Kode Iklan" — kode Java,
 * Kotlin, dan Lua + perubahan manifest + panduan ditampilkan di layar.
 * "Simpan ke Proyek" menulis semuanya ke berkas iklan-<penyedia>-
 * <jenis>.txt di akar proyek.
 *
 * Batasan jujur yang ditulis di layar: ini TEMPLATE KODE + PANDUAN,
 * bukan SDK iklan — SDK tidak disertakan di IDE; iklan sungguhan baru
 * tampil setelah akun + ID unit sendiri + dependensi SDK di
 * build.gradle proyek + bangun APK.
 */
public class IklanActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private Spinner spPenyedia;
    private Spinner spJenis;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eIdUnit;
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Pembuat Iklan"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pembuat Iklan"), 20);
        label(r, TeksUI.t("IDE membuatkan kode iklan siap tempel (Java, Kotlin, dan ")
                + "Lua) + perubahan manifest + panduan langkah demi langkah. "
                + TeksUI.t("Ini template kode + panduan — SDK iklan TIDAK disertakan ")
                + TeksUI.t("di IDE. Iklan sungguhan baru tampil setelah kamu membuat ")
                + TeksUI.t("akun penyedia, membuat unit iklan, menempel ID unit ")
                + TeksUI.t("sendiri, dan menambah dependensi SDK di build.gradle ")
                + TeksUI.t("proyek. ID yang dipakai bila kolom kosong adalah ID UJI ")
                + TeksUI.t("(AdMob) atau tempat kosong (Unity/AppLovin)."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Penyedia iklan:", false);
        spPenyedia = new Spinner(this);
        ArrayAdapter<String> adapterPenyedia = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, PembuatIklan.PENYEDIA);
        adapterPenyedia.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPenyedia.setAdapter(adapterPenyedia);
        r.addView(spPenyedia, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Jenis iklan:", false);
        spJenis = new Spinner(this);
        ArrayAdapter<String> adapterJenis = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, PembuatIklan.JENIS);
        adapterJenis.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spJenis.setAdapter(adapterJenis);
        r.addView(spJenis, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("ID unit iklan (kosong = pakai ID uji):"), false);
        eIdUnit = new EditText(this);
        GayaStudio.edit(eIdUnit);
        eIdUnit.setHint("Contoh AdMob: ca-app-pub-3940256099942544/6300978111");
        r.addView(eIdUnit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Kode Iklan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatKode();
            }
        });
        button(r, TeksUI.t("Simpan ke Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanKeProyek();
            }
        });

        label(r, "Hasil:", false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(13);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvHasil);
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pembuat Iklan. Pilih proyek, penyedia, dan jenis iklan, lalu tekan Buat Kode Iklan."));
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

    private String penyediaTerpilih() {
        if (spPenyedia == null || spPenyedia.getSelectedItem() == null) return "AdMob";
        return String.valueOf(spPenyedia.getSelectedItem());
    }

    private String jenisTerpilih() {
        if (spJenis == null || spJenis.getSelectedItem() == null) return "Banner";
        return String.valueOf(spJenis.getSelectedItem());
    }

    private String idUnit() {
        if (eIdUnit == null) return "";
        return eIdUnit.getText().toString().trim();
    }

    private File dirTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        return new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
    }

    private void buatKode() {
        String isi = PembuatIklan.isiBerkas(penyediaTerpilih(), jenisTerpilih(), idUnit());
        if (tvHasil != null) tvHasil.setText(isi);
        announce(TeksUI.t("Kode iklan ") + penyediaTerpilih() + " " + jenisTerpilih() + " dibuat.");
    }

    private void simpanKeProyek() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        File hasil = PembuatIklan.tulisKeProyek(dir, penyediaTerpilih(),
                jenisTerpilih(), idUnit());
        if (hasil == null) {
            toast("Gagal menyimpan kode iklan ke proyek.");
            return;
        }
        String ok = TeksUI.t("Kode iklan disimpan ke ") + hasil.getName()
                + TeksUI.t(" di proyek ") + dir.getName()
                + TeksUI.t(". Buka berkas itu di editor untuk melihat kode + panduan.");
        toast(ok);
        announce(ok);
        if (tvHasil != null) tvHasil.setText(PembuatIklan.isiBerkas(
                penyediaTerpilih(), jenisTerpilih(), idUnit()));
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
