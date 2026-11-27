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
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pabrik Pengujian Unit Otomatis dan Generator Fuzzing Batas
 * (Fitur 9, daftar Gemini 2026-11-27).
 *
 * Pilih proyek dan berkas kode, tekan "Buat dan Jalankan Uji" — IDE
 * membaca tanda tangan fungsi, menghasilkan kasus uji normal dan
 * batas, menjalankan fuzzing atas fungsi contoh, dan menyimpan
 * kerangka uji JUnit di proyek. Logika ada di PabrikUji (murni Java,
 * sudah diuji di komputer).
 *
 * BATAS: uji yang dihasilkan adalah kerangka + nilai batas — harapan
 * (expected) ditulis pengembang. Fuzzing sungguhan berjalan atas
 * fungsi contoh di kelas ini.
 */
public class PabrikUjiActivity extends Activity {
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
        setTitle(TeksUI.t("Pabrik Pengujian Unit dan Fuzzing"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pabrik Pengujian Unit dan Fuzzing"), 20);
        label(r, TeksUI.t("Hasilkan uji unit dari tanda tangan fungsi dan gempur fungsi dengan masukan ekstrem — teks kosong, angka batas, dan karakter aneh — lalu lihat cakupannya."), false);

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

        bJalankan = button(r, TeksUI.t("Buat dan Jalankan Uji"), new View.OnClickListener() {
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
                + TeksUI.t("Buat dan Jalankan Uji") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pabrik Pengujian Unit dan Fuzzing") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Buat dan Jalankan Uji") + ".");
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
        tvHasil.setText(TeksUI.t("Buat dan Jalankan Uji") + "...");
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
        }, "PabrikUji").start();
    }

    private String jalankan(File berkas) {
        String kode = bacaTeks(berkas);
        if (kode == null) return TeksUI.t("Berkas kode tidak bisa dibaca.");
        List<PabrikUji.KasusUji> kasus = new ArrayList<PabrikUji.KasusUji>();
        String namaKelas = berkas.getName().replace(".java", "").replace(".kt", "").replace(".py", "");
        int jumlahFungsi = 0;
        Matcher m = Pattern.compile(
                "\\b(?:public|private|protected|static|final|void|int|long|double|float|boolean|String)\\b[^;\\{\\}]*?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\)]*\\)")
                .matcher(kode);
        while (m.find()) {
            jumlahFungsi++;
            String tanda = m.group(0);
            kasus.addAll(PabrikUji.ujiDariTandaTangan(tanda));
            if (kasus.size() > 60) break;
        }
        List<PabrikUji.HasilFuzz> fuzzTel = PabrikUji.fuzzTelepon();
        List<PabrikUji.HasilFuzz> fuzzDis = PabrikUji.fuzzDiskon();
        int lulus = 0;
        int gagal = 0;
        StringBuilder gagalTeks = new StringBuilder();
        for (int i = 0; i < fuzzTel.size(); i++) {
            if (fuzzTel.get(i).lulus) lulus++;
            else {
                gagal++;
                gagalTeks.append("\n- telepon ").append(fuzzTel.get(i).masukan)
                        .append(": ").append(fuzzTel.get(i).pesan);
            }
        }
        for (int i = 0; i < fuzzDis.size(); i++) {
            if (fuzzDis.get(i).lulus) lulus++;
            else {
                gagal++;
                gagalTeks.append("\n- diskon ").append(fuzzDis.get(i).masukan)
                        .append(": ").append(fuzzDis.get(i).pesan);
            }
        }
        String sumber = PabrikUji.hasilkanSumberUji(namaKelas, kasus);
        File keluar = new File(berkas.getParentFile(), "Uji" + namaKelas + ".java");
        tulisTeks(keluar, sumber);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Uji lulus:")).append(" ").append(lulus).append(". ");
        b.append(TeksUI.t("Uji gagal:")).append(" ").append(gagal).append(". ");
        b.append(TeksUI.t("Baris kode:")).append(" ").append(PabrikUji.hitungBarisKode(kode)).append(". ");
        b.append(TeksUI.t("Fungsi ditemukan:")).append(" ").append(jumlahFungsi).append(". ");
        b.append(TeksUI.t("Kasus uji dibuat:")).append(" ").append(kasus.size()).append(".");
        b.append(gagalTeks.toString());
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
