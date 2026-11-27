package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
 * Layar Arsitektur Proyek Lama (butir 16, penyelesaian 5.8).
 *
 * Mengubah centang arsitektur 64 bit dan 32 bit pada proyek yang SUDAH
 * ADA — sebelumnya pengaturan itu hanya bisa diubah dengan menyunting
 * settings.gradle atau init.lua proyek secara manual. Layar ini:
 * menampilkan daftar proyek yang sudah ada, membaca pengaturan
 * arsitektur tiap proyek dari settings.gradle/init.lua-nya, membiarkan
 * pengembang mencentang/menghapus centang 64 bit dan 32 bit, lalu
 * menulis kembali perubahan itu ke berkas proyek (PisahAbi.tulis).
 *
 * Aturan build yang berlaku (tidak diubah di sini, sama seperti
 * CreateProjectActivity dan BangunProyek): build terpisah hanya aktif
 * bila KEDUA varian dicentang — hasilnya dua APK sekaligus (-64bit dan
 * -32bit). Bila hanya satu yang dicentang, build menghasilkan satu APK
 * universal yang tetap berjalan di perangkat 64 bit maupun 32 bit;
 * APK terpisah untuk satu varian saja belum didukung jalur build
 * (BangunProyek/Penandatangan hanya mengenal satu boolean pisah).
 * Pilihan per varian tetap disimpan apa adanya di init.lua
 * (arsitektur_64/arsitektur_32) supaya tidak hilang saat layar dibuka
 * lagi, dan layar menjelaskan aturan ini dengan teks biasa.
 *
 * Perbaikan 5.8 yang ikut berlaku di layar ini: pembacaan boolean
 * toleran (True/1/ya lewat SetelanProyek.benar), tiap kunci dibaca
 * sendiri-sendiri sehingga satu nilai rusak tidak menggagalkan kunci
 * lain, centang dipertahankan saat layar dibuat ulang
 * (onSaveInstanceState), dan pra-validasi build pisah — saat menyimpan,
 * layar memperingatkan bila proyek hanya membawa pustaka native satu
 * ABI (aturan yang sama dengan PisahAbi.periksaHasil).
 *
 * Aksesibilitas: seluruhnya teks dan kontrol standar (Spinner,
 * CheckBox berlabel, Button, TextView) di dalam ScrollView — tanpa
 * gambar, tanpa seret, tanpa arti yang hanya disampaikan warna.
 * Keadaan saat ini diumumkan lewat pembaca layar setiap kali proyek
 * berganti, dan hasil simpan diumumkan juga.
 *
 * Dibuka dari menu (disiapkan untuk tombol di layar utama / Kelola
 * Project): Intent biasa ke kelas ini; extra "proyek" (nama folder)
 * membuka langsung proyek tersebut, tanpa extra tampil daftar proyek.
 */
public class ArsitekturProyekActivity extends Activity {
    /** Extra Intent opsional: nama folder proyek yang langsung dibuka. */
    public static final String EXTRA_PROYEK = "proyek";

    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvInfo;
    private TextView tvKeadaan;
    private CheckBox cb64;
    private CheckBox cb32;
    private TextView tvHasil;
    private Button bSimpan;
    private String proyekTerpilih = null;
    private PisahAbi.Pilihan pilihan = null;
    /** True bila onCreate baru saja memulihkan centang yang belum disimpan. */
    private boolean baruDipulihkan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihanBahasa = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihanBahasa, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Ubah Arsitektur Proyek"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Ubah Arsitektur Proyek"), 20);
        label(r, TeksUI.t("Ubah arsitektur 64 bit dan 32 bit untuk proyek yang sudah ada, tanpa menyunting berkas settings.gradle atau init.lua secara manual."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        spProyek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Object item = spProyek.getSelectedItem();
                if (item != null) muatProyek(String.valueOf(item));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        tvInfo = new TextView(this);
        tvInfo.setTextSize(14);
        tvInfo.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvInfo);

        label(r, TeksUI.t("Keadaan saat ini:"), false);
        tvKeadaan = new TextView(this);
        tvKeadaan.setTextSize(14);
        tvKeadaan.setTextIsSelectable(true);
        tvKeadaan.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvKeadaan);

        cb64 = new CheckBox(this);
        cb64.setText(TeksUI.t("Bangun APK 64 bit (arm64-v8a)"));
        r.addView(cb64);
        cb32 = new CheckBox(this);
        cb32.setText(TeksUI.t("Bangun APK 32 bit (armeabi-v7a)"));
        r.addView(cb32);
        CompoundButton.OnCheckedChangeListener segarkan =
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean c) {
                        segarkanKeadaan();
                    }
                };
        cb64.setOnCheckedChangeListener(segarkan);
        cb32.setOnCheckedChangeListener(segarkan);

        label(r, TeksUI.t("Bila keduanya dicentang, build menghasilkan dua APK terpisah, satu 64 bit dan satu 32 bit. Bila hanya satu yang dicentang, build menghasilkan satu APK universal yang tetap berjalan di perangkat 64 bit maupun 32 bit; APK terpisah untuk satu varian saja belum didukung."), false);

        bSimpan = button(r, TeksUI.t("Simpan Arsitektur"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Simpan Arsitektur") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        String awal = null;
        if (savedInstanceState != null) awal = savedInstanceState.getString("proyek");
        if (awal == null || awal.length() == 0) {
            try {
                awal = getIntent().getStringExtra(EXTRA_PROYEK);
            } catch (Throwable ignored) {
            }
        }
        if (awal != null && daftarProyek.contains(awal)) {
            spProyek.setSelection(daftarProyek.indexOf(awal));
            if (!awal.equals(proyekTerpilih)) muatProyek(awal);
        } else if (!daftarProyek.isEmpty()) {
            spProyek.setSelection(0);
            if (!daftarProyek.get(0).equals(proyekTerpilih)) muatProyek(daftarProyek.get(0));
        } else {
            tvHasil.setText(TeksUI.t("Belum ada proyek. Buat proyek dulu."));
        }
        if (savedInstanceState != null) {
            // Pulihkan centang yang belum sempat disimpan (perbaikan 5.8:
            // centang dipertahankan saat layar dibuat ulang).
            if (savedInstanceState.containsKey("cb64")) {
                cb64.setChecked(savedInstanceState.getBoolean("cb64", cb64.isChecked()));
            }
            if (savedInstanceState.containsKey("cb32")) {
                cb32.setChecked(savedInstanceState.getBoolean("cb32", cb32.isChecked()));
            }
            baruDipulihkan = true;
        }
        announce(TeksUI.t("Ubah Arsitektur Proyek") + ". "
                + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Simpan Arsitektur") + ".");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SegarBahasa.periksa(this)) return;
        // Jangan menimpa centang yang baru dipulihkan sesudah layar dibuat ulang.
        if (baruDipulihkan) {
            baruDipulihkan = false;
            return;
        }
        muatDaftar();
        if (proyekTerpilih != null && daftarProyek.contains(proyekTerpilih)) {
            muatProyek(proyekTerpilih);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (proyekTerpilih != null) outState.putString("proyek", proyekTerpilih);
        if (cb64 != null) outState.putBoolean("cb64", cb64.isChecked());
        if (cb32 != null) outState.putBoolean("cb32", cb32.isChecked());
    }

    private void muatDaftar() {
        String kini = proyekTerpilih;
        if (kini == null && spProyek != null && spProyek.getSelectedItem() != null) {
            kini = String.valueOf(spProyek.getSelectedItem());
        }
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
        if (kini != null && daftarProyek.contains(kini)) {
            spProyek.setSelection(daftarProyek.indexOf(kini));
        }
    }

    /** Muat pengaturan arsitektur satu proyek ke centang dan label keadaan. */
    private void muatProyek(String nama) {
        if (nama == null || nama.length() == 0) return;
        boolean ganti = !nama.equals(proyekTerpilih);
        proyekTerpilih = nama;
        File dir = new File(ROOT + "/" + nama);
        pilihan = PisahAbi.baca(dir);
        cb64.setChecked(pilihan.abi64);
        cb32.setChecked(pilihan.abi32);
        String info = InfoProyek.namaAplikasi(dir);
        String paket = InfoProyek.namaPaket(dir);
        String tipe = InfoProyek.tipe(dir);
        tvInfo.setText(info + " (" + paket + ", " + tipe + ")");
        perbaruiKeadaan();
        if (ganti) announce(tvKeadaan.getText().toString());
    }

    /** Perbarui label keadaan dari centang saat ini (tanpa membaca ulang berkas). */
    private void segarkanKeadaan() {
        if (proyekTerpilih == null || pilihan == null) return;
        perbaruiKeadaan();
    }

    private void perbaruiKeadaan() {
        if (pilihan == null || tvKeadaan == null) return;
        StringBuilder b = new StringBuilder();
        if (pilihan.pisahEfektif) {
            b.append(TeksUI.t("Build terpisah: aktif, dua APK dibuat (64 bit dan 32 bit)."));
        } else {
            b.append(TeksUI.t("Build terpisah: tidak aktif, satu APK universal dibuat."));
        }
        if (!PisahAbi.cocok(pilihan)) {
            b.append(" ").append(TeksUI.t("Pilihan di atas tidak cocok dengan berkas proyek; tekan Simpan Arsitektur untuk menyelaraskan."));
        }
        String peringatan = "";
        if (proyekTerpilih != null && cb64 != null && cb32 != null) {
            peringatan = PisahAbi.periksaPisahProyek(
                    new File(ROOT + "/" + proyekTerpilih),
                    cb64.isChecked(), cb32.isChecked());
        }
        if (peringatan.length() > 0) b.append("\n").append(peringatan);
        tvKeadaan.setText(b.toString());
    }

    /** Simpan centang ke berkas proyek (di utas latar; tulis berkas bisa lambat). */
    private void simpan() {
        if (proyekTerpilih == null || spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + proyekTerpilih);
        final boolean v64 = cb64.isChecked();
        final boolean v32 = cb32.isChecked();
        tvHasil.setText(TeksUI.t("Simpan Arsitektur") + "...");
        bSimpan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String galat = PisahAbi.tulis(dir, v64, v32);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bSimpan.setEnabled(true);
                        if (galat.length() > 0) {
                            String hasil = TeksUI.t("Arsitektur tidak bisa disimpan.")
                                    + " " + galat;
                            tvHasil.setText(hasil);
                            announce(hasil);
                            return;
                        }
                        pilihan = PisahAbi.baca(dir);
                        cb64.setChecked(pilihan.abi64);
                        cb32.setChecked(pilihan.abi32);
                        perbaruiKeadaan();
                        String hasil = TeksUI.t("Arsitektur tersimpan untuk proyek")
                                + " " + proyekTerpilih + ".";
                        String peringatan = PisahAbi.periksaPisahProyek(dir, v64, v32);
                        if (peringatan.length() > 0) hasil += " " + peringatan;
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "ArsitekturProyek").start();
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
