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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transpiler Lintas-Paradigma Offline (fitur 6 dari daftar Gemini, 5.7).
 *
 * Pilih proyek, lalu berkas .java atau .xml, lalu arah konversi
 * (Java ke Kotlin, atau XML ke Compose). Tombol "Pratinjau" menampilkan
 * hasil konversi di layar — WAJIB tampil dulu — dan tombol
 * "Terapkan ke Berkas" baru aktif sesudah pratinjau ditampilkan.
 * Terapkan menulis berkas tujuan .kt di folder yang sama dengan sumber
 * tanpa menimpa berkas sumber. Logika konversinya ada di Transpiler
 * (murni Java, sudah diuji di komputer).
 */
public class TranspilerActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final int MAKS_BERKAS = 100;

    private String kodeBahasa = "id";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spBerkas;
    private ArrayAdapter<String> adapterBerkas;
    private final List<File> daftarBerkas = new ArrayList<File>();
    private final List<String> namaBerkas = new ArrayList<String>();
    private Spinner spArah;
    private TextView tvHasil;
    private Button bPratinjau, bTerapkan;

    /** Status pratinjau -> terapan (kelas murni, sudah diuji di JVM). */
    private final Transpiler.Status status = new Transpiler.Status();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kodeBahasa = Bahasa.kodeEfektif(
                getSharedPreferences(PengaturanActivity.NAMA_PREF, MODE_PRIVATE)
                        .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;
        setTitle(TeksUI.t("Transpiler Lintas-Paradigma"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Transpiler Lintas-Paradigma"), 20);
        label(r, TeksUI.t("Mengubah kode Java menjadi Kotlin atau tata letak XML menjadi Jetpack Compose, ")
                + TeksUI.t("langsung di HP tanpa internet. Tekan Pratinjau dulu, periksa hasilnya, ")
                + TeksUI.t("baru tekan Terapkan ke Berkas."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        spProyek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                muatBerkas();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih berkas:"), false);
        spBerkas = new Spinner(this);
        adapterBerkas = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, namaBerkas);
        adapterBerkas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBerkas.setAdapter(adapterBerkas);
        spBerkas.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                berkasBerubah();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        r.addView(spBerkas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Arah konversi:"), false);
        spArah = new Spinner(this);
        List<String> arah = new ArrayList<String>();
        arah.add(TeksUI.t("Java ke Kotlin"));
        arah.add(TeksUI.t("XML ke Compose"));
        ArrayAdapter<String> adapterArah = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, arah);
        adapterArah.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spArah.setAdapter(adapterArah);
        r.addView(spArah, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bPratinjau = button(r, TeksUI.t("Pratinjau"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pratinjau();
            }
        });
        bTerapkan = button(r, TeksUI.t("Terapkan ke Berkas"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terapkan();
            }
        });
        bTerapkan.setEnabled(false);

        label(r, TeksUI.t("Hasil:"), false);
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
        announce(TeksUI.t("Transpiler. Pilih proyek dan berkas, lalu tekan Pratinjau."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    /** Berkas sumber terpilih, atau null bila belum ada. */
    private File berkasTerpilih() {
        if (spBerkas == null || spBerkas.getSelectedItem() == null) return null;
        int pos = spBerkas.getSelectedItemPosition();
        if (pos < 0 || pos >= daftarBerkas.size()) return null;
        return daftarBerkas.get(pos);
    }

    /** Daftar berkas .java/.xml proyek terpilih (rekursif, maks 100). */
    private void muatBerkas() {
        daftarBerkas.clear();
        namaBerkas.clear();
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
            cariBerkas(dir, dir, daftarBerkas);
            for (int i = 0; i < daftarBerkas.size(); i++) {
                namaBerkas.add(jalanRelatif(dir, daftarBerkas.get(i)));
            }
        }
        if (adapterBerkas != null) adapterBerkas.notifyDataSetChanged();
        berkasBerubah();
    }

    private static void cariBerkas(File dir, File akar, List<File> hasil) {
        if (dir == null || hasil.size() >= MAKS_BERKAS) return;
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            if (hasil.size() >= MAKS_BERKAS) return;
            File f = anak[i];
            if (f.isDirectory()) {
                if (!"bin".equals(f.getName()) && !".git".equals(f.getName())) {
                    cariBerkas(f, akar, hasil);
                }
            } else {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".java") || n.endsWith(".xml")) hasil.add(f);
            }
        }
    }

    private static String jalanRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    /**
     * Berkas atau arah berubah: pratinjau lama tidak berlaku lagi,
     * Terapkan dimatikan sampai Pratinjau ditekan lagi. Arah ikut
     * jenis berkas terpilih supaya tidak salah pilih.
     */
    private void berkasBerubah() {
        status.reset();
        if (bTerapkan != null) bTerapkan.setEnabled(false);
        if (tvHasil != null) tvHasil.setText("");
        File sumber = berkasTerpilih();
        if (sumber != null && spArah != null) {
            String arah = Transpiler.arahUntuk(sumber.getName());
            if ("xml".equals(arah)) spArah.setSelection(1);
            else if ("java".equals(arah)) spArah.setSelection(0);
        }
    }

    private void pratinjau() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        File sumber = berkasTerpilih();
        if (sumber == null) {
            toast("Pilih dulu berkasnya.");
            return;
        }
        if (!sumber.isFile()) {
            toast("Berkas sumber tidak ditemukan.");
            return;
        }
        String arah = Transpiler.arahUntuk(sumber.getName());
        boolean mauCompose = spArah != null && spArah.getSelectedItemPosition() == 1;
        if (("java".equals(arah) && mauCompose) || ("xml".equals(arah) && !mauCompose)) {
            toast("Arah konversi tidak cocok dengan jenis berkas.");
            announce(TeksUI.t("Arah konversi tidak cocok dengan jenis berkas."));
            return;
        }
        String isi = SumberDaya.teks(sumber);
        if (isi == null) {
            tvHasil.setText(TeksUI.t("Gagal membaca berkas: ") + sumber.getName());
            announce(TeksUI.t("Gagal membaca berkas: ") + sumber.getName());
            return;
        }
        String hasil = "xml".equals(arah)
                ? Transpiler.xmlKeCompose(isi)
                : Transpiler.javaKeKotlin(isi);
        status.tandaiPratinjau(hasil);
        tvHasil.setText(hasil);
        bTerapkan.setEnabled(true);
        announce(TeksUI.t("Pratinjau ditampilkan. Periksa hasilnya sebelum menerapkan."));
    }

    private void terapkan() {
        if (!status.bolehTerapkan()) {
            toast("Tekan Pratinjau dulu sebelum menerapkan.");
            return;
        }
        File sumber = berkasTerpilih();
        if (sumber == null) {
            toast("Pilih dulu berkasnya.");
            return;
        }
        File tujuan = new File(sumber.getParentFile(),
                Transpiler.namaTujuan(sumber.getName()));
        if (!SumberDaya.tulis(tujuan, status.hasil())) {
            String pesan = TeksUI.t("Gagal menyimpan hasil: ") + tujuan.getName();
            toast(pesan);
            announce(pesan);
            return;
        }
        String pesan = TeksUI.t("Hasil ditulis ke ") + tujuan.getName();
        toast(pesan);
        announce(pesan);
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
