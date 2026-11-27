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
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pabrik Android App Bundle dan Pemeriksa Kesiapan Google Play Store
 * (Fitur 13, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Bangun Bundel AAB" — IDE menyusun berkas .aab
 * dari manifest, sumber daya, dan aset proyek, mensimulasikan
 * pecahan ukuran per arsitektur dan bahasa, lalu mengaudit kesiapan
 * kebijakan Play Store. Logika ada di PabrikAAB (murni Java, sudah
 * diuji di komputer).
 *
 * BATAS: bundel adalah arsip berstruktur AAB standar; verifikasi
 * akhir bundletool dan penandatanganan bundel tetap memakai jalur
 * build yang sama dengan APK. Ukuran pecahan adalah perkiraan.
 */
public class PabrikAABActivity extends Activity {
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
        setTitle(TeksUI.t("Pabrik App Bundle dan Kesiapan Play Store"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pabrik App Bundle dan Kesiapan Play Store"), 20);
        label(r, TeksUI.t("Bangun paket AAB di HP, simulasikan pecahan ukuran per arsitektur dan bahasa, dan periksa kesiapan kebijakan Google Play sebelum mengunggah."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Bangun Bundel AAB"), new View.OnClickListener() {
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
                + TeksUI.t("Bangun Bundel AAB") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pabrik App Bundle dan Kesiapan Play Store") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Bangun Bundel AAB") + ".");
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
        tvHasil.setText(TeksUI.t("Bangun Bundel AAB") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "PabrikAAB").start();
    }

    private String jalankan(File dir) {
        File modul = new File(dir, "app/src/main");
        if (!modul.isDirectory()) modul = dir;
        String nama = InfoProyek.namaAplikasi(dir).replaceAll("\\s+", "");
        if (nama.length() == 0) nama = dir.getName();
        File keluar = new File(new File(dir, "bin"), nama + ".aab");
        File aab = PabrikAAB.bangunAAB(modul, keluar);
        if (aab == null) return TeksUI.t("Bundel AAB tidak bisa dibuat.");
        long ukuranBersama = PabrikAAB.ukuranFolder(new File(modul, "res"))
                + PabrikAAB.ukuranFolder(new File(modul, "assets"));
        long ukuranLib = PabrikAAB.ukuranFolder(new File(modul, "jniLibs"));
        List<PabrikAAB.Varian> varian = PabrikAAB.simulasiPecahan(ukuranBersama,
                ukuranLib / 2, ukuranBersama / 10, PabrikAAB.ABI,
                new String[]{"id", "en"});
        String manifest = bacaTeks(new File(modul, "AndroidManifest.xml"));
        int target = 34;
        try {
            target = Integer.parseInt(InfoProyek.bacaKunci(dir, "targetsdk", "34"));
        } catch (Throwable ignored) {
        }
        boolean ada64 = new File(dir, "app/src/main/jniLibs/arm64-v8a").isDirectory()
                || new File(dir, "lib/arm64-v8a").isDirectory();
        boolean ada32 = new File(dir, "app/src/main/jniLibs/armeabi-v7a").isDirectory()
                || new File(dir, "lib/armeabi-v7a").isDirectory();
        List<PabrikAAB.Temuan> temuan = PabrikAAB.auditPlay(manifest, target, ada64, ada32);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Ukuran unduhan diperkirakan:")).append(" ");
        long terkecil = -1;
        for (int i = 0; i < varian.size(); i++) {
            if (terkecil < 0 || varian.get(i).ukuran < terkecil) terkecil = varian.get(i).ukuran;
        }
        b.append(terkecil < 0 ? "-" : terkecil + " " + TeksUI.t("bita")).append(". ");
        b.append(TeksUI.t("Pemeriksaan Play:")).append(" ").append(temuan.size()).append(" temuan.");
        b.append("\n").append(PabrikAAB.ringkasan(aab.length(), varian, temuan));
        for (int i = 0; i < varian.size(); i++) b.append("\n- ").append(varian.get(i).toString());
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
