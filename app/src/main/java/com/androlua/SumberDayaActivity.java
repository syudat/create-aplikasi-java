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
 * Pengelola Sumber Daya (fitur pilihan al kautsar dari daftar Android
 * Studio kedua, nomor 9).
 *
 * Pilih proyek untuk melihat semua sumber dayanya (berkas per kategori,
 * daftar string, warna, dan gaya) dalam satu laporan teks, dan untuk
 * menambah string baru ke strings.xml atau warna baru ke colors.xml
 * tanpa menyunting XML dengan tangan. Logikanya ada di SumberDaya
 * (murni Java, sudah diuji di komputer).
 */
public class SumberDayaActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;
    private EditText eNamaString, eNilaiString, eNamaWarna, eNilaiWarna;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Pengelola Sumber Daya"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pengelola Sumber Daya"), 20);
        label(r, TeksUI.t("Lihat semua sumber daya proyek dalam satu tempat: berkas ")
                + TeksUI.t("gambar, layout, string, warna, dan gaya, lengkap dengan ")
                + TeksUI.t("ukurannya. Di sini juga bisa menambah string dan warna ")
                + TeksUI.t("baru tanpa menyunting XML dengan tangan."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Lihat Sumber Daya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lihat();
            }
        });

        heading(r, TeksUI.t("Tambah String Baru"), 16);
        eNamaString = field(r, TeksUI.t("Nama string (huruf kecil, contoh: judul_utama)"));
        eNilaiString = field(r, TeksUI.t("Isi string"));
        button(r, TeksUI.t("Tambah String ke strings.xml"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambahString();
            }
        });

        heading(r, TeksUI.t("Tambah Warna Baru"), 16);
        eNamaWarna = field(r, TeksUI.t("Nama warna (huruf kecil, contoh: warna_aksen)"));
        eNilaiWarna = field(r, TeksUI.t("Nilai warna (contoh: #FF5722)"));
        button(r, TeksUI.t("Tambah Warna ke colors.xml"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambahWarna();
            }
        });

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
        announce(TeksUI.t("Pengelola Sumber Daya. Pilih proyek, lalu tekan Lihat Sumber Daya."));
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

    private File dirTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return null;
        }
        return new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
    }

    private void lihat() {
        File dir = dirTerpilih();
        if (dir == null) return;
        SumberDaya.Ringkasan h = SumberDaya.baca(dir);
        tvHasil.setText(SumberDaya.laporan(h, dir.getName()));
        if (h.galat == null) {
            announce(TeksUI.t("Selesai. ") + h.jumlahBerkas + TeksUI.t(" berkas sumber daya, ")
                    + h.strings.size() + TeksUI.t(" string, ") + h.colors.size() + TeksUI.t(" warna, ")
                    + h.styles.size() + TeksUI.t(" gaya."));
        } else {
            announce(h.galat);
        }
    }

    private void tambahString() {
        File dir = dirTerpilih();
        if (dir == null) return;
        File berkas = new File(SumberDaya.folderValues(dir), "strings.xml");
        try {
            String baru = SumberDaya.tambahString(SumberDaya.teks(berkas),
                    eNamaString.getText().toString().trim(),
                    eNilaiString.getText().toString());
            if (!SumberDaya.tulis(berkas, baru)) {
                toast(TeksUI.t("Gagal menulis strings.xml."));
                announce(TeksUI.t("Gagal menulis strings.xml."));
                return;
            }
            toast("String ditambahkan.");
            announce(TeksUI.t("String ") + eNamaString.getText().toString().trim() + TeksUI.t(" ditambahkan ke strings.xml."));
            eNamaString.setText("");
            eNilaiString.setText("");
            lihat();
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
            announce(e.getMessage());
        }
    }

    private void tambahWarna() {
        File dir = dirTerpilih();
        if (dir == null) return;
        File berkas = new File(SumberDaya.folderValues(dir), "colors.xml");
        try {
            String baru = SumberDaya.tambahWarna(SumberDaya.teks(berkas),
                    eNamaWarna.getText().toString().trim(),
                    eNilaiWarna.getText().toString().trim());
            if (!SumberDaya.tulis(berkas, baru)) {
                toast(TeksUI.t("Gagal menulis colors.xml."));
                announce(TeksUI.t("Gagal menulis colors.xml."));
                return;
            }
            toast("Warna ditambahkan.");
            announce(TeksUI.t("Warna ") + eNamaWarna.getText().toString().trim() + TeksUI.t(" ditambahkan ke colors.xml."));
            eNamaWarna.setText("");
            eNilaiWarna.setText("");
            lihat();
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
            announce(e.getMessage());
        }
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

    private EditText field(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        parent.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
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
