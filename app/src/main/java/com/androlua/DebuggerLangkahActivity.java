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
import java.util.Locale;

/**
 * Debugger Langkah-Demi-Langkah dan Inspektur Memori Waktu-Nyata
 * (Fitur 6, daftar Gemini 2026-11-27).
 *
 * Tulis jejak eksekusi ("baris|kode|variabel=nilai" per baris) dan
 * nomor titik henti, tekan "Jalankan Jejak Debug" — IDE menjalankan
 * jejak sampai titik henti pertama, lalu membacakan baris aktif dan
 * nilai variabelnya. Tekan lagi untuk melangkah satu baris.
 * Logika ada di DebuggerLangkah (murni Java, sudah diuji di komputer).
 *
 * BATAS: ini debugger JEJAK, bukan penjeda proses Android sungguhan
 * lewat JDWP (lihat DebuggerLangkah).
 */
public class DebuggerLangkahActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eJejak;
    private EditText eHenti;
    private TextView tvHasil;
    private Button bJalankan;
    private Button bLangkah;
    private DebuggerLangkah.Sesi sesi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Debugger Langkah dan Inspektur Memori"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Debugger Langkah dan Inspektur Memori"), 20);
        label(r, TeksUI.t("Pasang titik henti, jalankan jejak eksekusi baris demi baris, dan periksa nilai variabel di tiap langkah lewat pembaca layar."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Jejak eksekusi:"), false);
        eJejak = new EditText(this);
        GayaStudio.edit(eJejak);
        eJejak.setHint(TeksUI.t("Contoh: 10|int total = harga * jumlah;|harga=5000, jumlah=3, total=15000"));
        eJejak.setText("10|int total = harga * jumlah;|harga=5000, jumlah=3, total=15000\n11|int pajak = total / 10;|harga=5000, jumlah=3, total=15000, pajak=1500\n12|return total + pajak;|harga=5000, jumlah=3, total=15000, pajak=1500");
        eJejak.setTextSize(14);
        eJejak.setMinLines(4);
        r.addView(eJejak, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Titik henti (nomor baris, pisah koma):"), false);
        eHenti = new EditText(this);
        GayaStudio.edit(eHenti);
        eHenti.setHint(TeksUI.t("Contoh: 11"));
        eHenti.setText("11");
        eHenti.setTextSize(14);
        eHenti.setSingleLine(true);
        r.addView(eHenti, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Jalankan Jejak Debug"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });
        bLangkah = button(r, TeksUI.t("Langkah Satu Baris"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                langkah();
            }
        });
        button(r, TeksUI.t("Lanjutkan Program"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lanjutkan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                + TeksUI.t("Jalankan Jejak Debug") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Debugger Langkah dan Inspektur Memori") + ". " + TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan")
                + " " + TeksUI.t("Jalankan Jejak Debug") + ".");
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
        List<DebuggerLangkah.Langkah> jejak =
                DebuggerLangkah.uraiJejak(eJejak.getText().toString());
        if (jejak.size() == 0) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        sesi = new DebuggerLangkah.Sesi();
        sesi.jejak = jejak;
        String[] henti = eHenti.getText().toString().split(",");
        for (int i = 0; i < henti.length; i++) {
            try {
                DebuggerLangkah.tambahTitikHenti(sesi, Integer.parseInt(henti[i].trim()));
            } catch (Throwable ignored) {
            }
        }
        boolean sampai = sesi.lanjut();
        tampilkan(sampai ? "" : TeksUI.t("Jejak habis.") + " ");
    }

    private void lanjutkan() {
        if (sesi == null) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        boolean sampai = sesi.lanjut();
        tampilkan(sampai ? "" : TeksUI.t("Jejak habis.") + " ");
    }

    private void langkah() {
        if (sesi == null) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        boolean ada = sesi.langkah();
        tampilkan(ada ? "" : TeksUI.t("Jejak habis.") + " ");
    }

    private void tampilkan(String awalan) {
        StringBuilder b = new StringBuilder();
        b.append(awalan);
        b.append(TeksUI.t("Baris aktif:")).append(" ")
                .append(DebuggerLangkah.teksBarisAktif(sesi)).append("\n");
        b.append(TeksUI.t("Variabel:")).append("\n")
                .append(DebuggerLangkah.teksVariabel(sesi.aktif()));
        String hasil = b.toString();
        tvHasil.setText(hasil);
        announce(hasil);
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
