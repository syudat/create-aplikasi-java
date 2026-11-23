package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
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
 * Mode Anak (Tahap 3I).
 *
 * Tampilan IDE yang disederhanakan untuk anak: tombol BESAR, hanya
 * 4 aksi aman — Buat Proyek (dari template aman: Kalkulator, Tebak
 * Angka, Kuis, Tangkap Bintang), Buka Proyek, Jalankan, Belajar.
 * TIDAK ADA hapus, ekspor, cadangan, pengaturan token, atau
 * pengaturan GitHub di layar ini.
 *
 * Keluar dari Mode Anak memakai PIN 4 angka sederhana (dibuat saat
 * pertama kali masuk bila belum ada). PIN ini pengaman ringan untuk
 * anak kecil, BUKAN keamanan sungguhan.
 */
public class ModeAnakActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spTemplate;
    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eNama;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Mode Anak"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Mode Anak"), 26);
        label(r, TeksUI.t("Mode sederhana untuk anak. Hanya ada 4 tombol aman di ")
                + TeksUI.t("sini: buat proyek, buka proyek, jalankan, dan belajar. ")
                + TeksUI.t("Tidak ada tombol hapus atau pengaturan."), false);

        label(r, TeksUI.t("Buat proyek baru:"), false);
        eNama = new EditText(this);
        GayaStudio.edit(eNama);
        eNama.setHint("Nama proyek, contoh: Game Saya");
        eNama.setTextSize(18);
        r.addView(eNama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        spTemplate = new Spinner(this);
        String[] namaTemplate = new String[ModeAnak.TEMPLATE_AMAN.length];
        for (int i = 0; i < namaTemplate.length; i++) {
            namaTemplate[i] = ModeAnak.TEMPLATE_AMAN[i].nama;
        }
        ArrayAdapter<String> adapterTemplate = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, namaTemplate);
        adapterTemplate.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTemplate.setAdapter(adapterTemplate);
        r.addView(spTemplate, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buttonBesar(r, TeksUI.t("Buat Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatProyekAnak();
            }
        });

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buttonBesar(r, TeksUI.t("Buka Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaProyek();
            }
        });
        buttonBesar(r, "Jalankan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jalankanProyek();
            }
        });
        buttonBesar(r, TeksUI.t("Belajar"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(ModeAnakActivity.this, BelajarActivity.class));
                } catch (Throwable t) {
                    toast("Gagal membuka Belajar: " + t.getMessage());
                }
            }
        });
        buttonBesar(r, TeksUI.t("Keluar dari Mode Anak"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mintaPinKeluar();
            }
        });

        muatDaftar();
        // PIN dibuat saat pertama kali masuk bila belum ada.
        if (bacaPin().isEmpty()) {
            mintaBuatPin();
        }
        announce(TeksUI.t("Mode Anak. Ada empat tombol: Buat Proyek, Buka Proyek, Jalankan, dan Belajar."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        muatDaftar();
    }

    @Override
    public void onBackPressed() {
        // Back di Mode Anak TIDAK langsung keluar — harus lewat PIN.
        mintaPinKeluar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private String bacaPin() {
        SharedPreferences pref = getSharedPreferences(ModeAnak.NAMA_PREF, MODE_PRIVATE);
        return pref.getString(ModeAnak.KUNCI_PIN, "");
    }

    private void simpanPin(String pin) {
        getSharedPreferences(ModeAnak.NAMA_PREF, MODE_PRIVATE)
                .edit().putString(ModeAnak.KUNCI_PIN, pin).apply();
    }

    private void mintaBuatPin() {
        final EditText ePin = new EditText(this);
        GayaStudio.edit(ePin);
        ePin.setHint("4 angka, contoh: 1234");
        ePin.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Buat PIN Mode Anak"))
                .setMessage(TeksUI.t("Buat PIN 4 angka. PIN ini dipakai orang tua untuk ")
                        + TeksUI.t("keluar dari Mode Anak. Ingat baik-baik PIN-nya."))
                .setView(ePin)
                .setPositiveButton(TeksUI.t("Simpan PIN"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pin = ePin.getText().toString().trim();
                        if (!ModeAnak.pinValid(pin)) {
                            toast("PIN harus tepat 4 angka.");
                            mintaBuatPin();
                            return;
                        }
                        simpanPin(pin);
                        toast("PIN Mode Anak disimpan.");
                    }
                })
                .setNegativeButton(TeksUI.t("Nanti saja"), null)
                .show();
    }

    private void mintaPinKeluar() {
        if (bacaPin().isEmpty()) {
            finish();
            return;
        }
        final EditText ePin = new EditText(this);
        GayaStudio.edit(ePin);
        ePin.setHint("Masukkan PIN 4 angka");
        ePin.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Keluar dari Mode Anak"))
                .setMessage(TeksUI.t("Masukkan PIN 4 angka untuk kembali ke menu utama."))
                .setView(ePin)
                .setPositiveButton(TeksUI.t("Keluar"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String masuk = ePin.getText().toString().trim();
                        if (ModeAnak.pinCocok(bacaPin(), masuk)) {
                            toast("Keluar dari Mode Anak.");
                            finish();
                        } else {
                            toast("PIN salah, coba lagi.");
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void buatProyekAnak() {
        String nama = eNama == null ? "" : eNama.getText().toString().trim();
        if (nama.isEmpty()) {
            toast("Isi dulu nama proyeknya.");
            return;
        }
        int pos = spTemplate == null ? 0 : spTemplate.getSelectedItemPosition();
        if (pos < 0 || pos >= ModeAnak.TEMPLATE_AMAN.length) pos = 0;
        ModeAnak.TemplateAman t = ModeAnak.TEMPLATE_AMAN[pos];
        String tipe;
        String templateId = null;
        if ("game".equals(t.sumber)) {
            tipe = "lua";
        } else {
            TemplateProyek.Info info = TemplateProyek.cari(t.id);
            if (info == null) {
                toast("Template tidak dikenal: " + t.id);
                return;
            }
            tipe = info.tipe;
            templateId = t.id;
        }
        String dir = BuatProyekHelper.buat(this, nama, tipe, templateId);
        if (dir == null) return;
        if ("game".equals(t.sumber)) {
            String galat = GameTanpaKode.terapkanGame(new File(dir), t.id, nama, 5,
                    TeksUI.t("Selamat, kamu menang!"));
            if (galat != null) {
                toast("Game gagal diterapkan: " + galat);
                return;
            }
        }
        toast("Proyek " + nama + TeksUI.t(" dibuat. Buka di Kelola Project untuk melihatnya."));
        announce(TeksUI.t("Proyek ") + nama + " dibuat.");
        muatDaftar();
    }

    private void bukaProyek() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        String nama = String.valueOf(spProyek.getSelectedItem());
        try {
            Intent it = new Intent(this, EditorActivity.class);
            it.putExtra(EditorActivity.EXTRA_DIR, ROOT + "/" + nama);
            startActivity(it);
        } catch (Throwable t) {
            toast("Gagal membuka proyek: " + t.getMessage());
        }
    }

    // Jalankan kini Java murni (Tahap 3I).
    private void jalankanProyek() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        String dir = ROOT + "/" + String.valueOf(spProyek.getSelectedItem());
        try {
            JalankanProyek.jalankan(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal menjalankan: " + t.getMessage());
            return;
        }
        finish();
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
            GayaStudio.labelKecil(tv, 16);
        } else {
            GayaStudio.label(tv, 18);
        }
        tv.setPadding(0, dp(small ? 2 : 10), 0, dp(2));
        parent.addView(tv);
        return tv;
    }

    /** Tombol besar untuk anak (teks 20sp, jarak lebih lega). */
    private Button buttonBesar(LinearLayout parent, String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        GayaStudio.tombolBesar(b);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
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
