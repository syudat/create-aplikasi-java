package com.androlua;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pabrik Modul Ekstensi Mandiri dan Penggabung Fitur Otomatis (Fitur 10).
 *
 * Kemas: pilih proyek sumber, centang berkas kode/sumber daya yang mau
 * dijadikan modul, isi nama + versi, tekan "Kemas sebagai Modul" —
 * sistem memindai string dan izin yang dipakai lalu membungkusnya ke
 * ZIP modul (modul.json + berkas) di folder Download/project/modul.
 *
 * Pasang: pilih proyek tujuan, pilih berkas .zip modul, tekan
 * "Pasang Modul" — berkas disalin tanpa menimpa yang sudah ada,
 * entri string dan izin digabung (tidak duplikat).
 *
 * Logika kemas/pasang/pindai ada di PabrikModul (murni Java, sudah
 * diuji di komputer); activity ini hanya memilih dan menampilkan
 * ringkasan hasil.
 */
public class PabrikModulActivity extends Activity {
    private static final int MINTA_MODUL = 4;
    private static final int BATAS_BERKAS = 200;

    private Spinner spSumber;
    private Spinner spTujuan;
    private LinearLayout wadahBerkas;
    private EditText eNamaModul;
    private EditText eVersi;
    private TextView tvHasil;
    private TextView tvModulDipilih;

    private final List<String> daftarProyek = new ArrayList<String>();
    private final List<String> jalurBerkas = new ArrayList<String>();
    private final List<CheckBox> centangBerkas = new ArrayList<CheckBox>();
    private File modulDipilih;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TeksUI.kodeAktif = Bahasa.kodeEfektif(
                getSharedPreferences(PengaturanActivity.NAMA_PREF, MODE_PRIVATE)
                        .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        setTitle(TeksUI.t("Pabrik Modul"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pabrik Modul"), 20);
        label(r, TeksUI.t("Kemas layar dan kelas utilitas dari satu proyek menjadi modul mandiri, ")
                + TeksUI.t("lalu pasang modul itu ke proyek lain tanpa menimpa kode yang sudah ada."), false);

        label(r, TeksUI.t("Proyek sumber (yang dikemas jadi modul):"), false);
        spSumber = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSumber.setAdapter(adapter);
        r.addView(spSumber, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Muat Berkas Proyek Sumber"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muatBerkas();
            }
        });

        label(r, TeksUI.t("Berkas yang dikemas (centang yang mau ikut):"), false);
        wadahBerkas = new LinearLayout(this);
        wadahBerkas.setOrientation(LinearLayout.VERTICAL);
        r.addView(wadahBerkas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama modul:"), false);
        eNamaModul = new EditText(this);
        GayaStudio.edit(eNamaModul);
        eNamaModul.setHint(TeksUI.t("Contoh: layar-pengaturan"));
        eNamaModul.setSingleLine(true);
        r.addView(eNamaModul, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Versi modul:"), false);
        eVersi = new EditText(this);
        GayaStudio.edit(eVersi);
        eVersi.setHint("1.0");
        eVersi.setSingleLine(true);
        eVersi.setText("1.0");
        r.addView(eVersi, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Kemas sebagai Modul"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kemas();
            }
        });

        heading(r, TeksUI.t("Pasang Modul"), 18);
        label(r, TeksUI.t("Proyek tujuan (yang dipasangi modul):"), false);
        spTujuan = new Spinner(this);
        ArrayAdapter<String> adapterTujuan = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterTujuan.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTujuan.setAdapter(adapterTujuan);
        r.addView(spTujuan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvModulDipilih = label(r, TeksUI.t("Modul belum dipilih."), false);
        button(r, TeksUI.t("Pilih Modul (.zip)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihModul();
            }
        });

        button(r, TeksUI.t("Pasang Modul"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasang();
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

        muatDaftarProyek();
        announce(TeksUI.t("Pabrik Modul. Pilih proyek sumber, centang berkasnya, lalu tekan Kemas sebagai Modul."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MINTA_MODUL) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        salinModulDariUri(data.getData());
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(BuatProyekHelper.root())));
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private String proyekTerpilih(Spinner sp) {
        if (sp == null || sp.getSelectedItem() == null) return null;
        String nama = String.valueOf(sp.getSelectedItem());
        if (TeksUI.t("(belum ada proyek)").equals(nama)) return null;
        return nama;
    }

    /** Memuat berkas .java/.kt/.xml proyek sumber sebagai daftar centang. */
    private void muatBerkas() {
        String nama = proyekTerpilih(spSumber);
        if (nama == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        wadahBerkas.removeAllViews();
        jalurBerkas.clear();
        centangBerkas.clear();
        List<String> semua = new ArrayList<String>();
        kumpulBerkas(new File(BuatProyekHelper.root() + "/" + nama), "", semua);
        for (int i = 0; i < semua.size(); i++) {
            final String jalur = semua.get(i);
            CheckBox cb = new CheckBox(this);
            cb.setText(jalur);
            cb.setChecked(true);
            wadahBerkas.addView(cb);
            jalurBerkas.add(jalur);
            centangBerkas.add(cb);
        }
        if (semua.isEmpty()) {
            Toast.makeText(this, TeksUI.t("Tidak ada berkas kode di proyek ini."),
                    Toast.LENGTH_SHORT).show();
        } else {
            announce(semua.size() + TeksUI.t(" berkas ditemukan. Centang yang mau dikemas."));
        }
    }

    /** Mengumpulkan berkas kode/sumber daya (rekursif, tanpa bin/ dan *.zip). */
    private void kumpulBerkas(File dir, String awalan, List<String> keluar) {
        if (dir == null || !dir.isDirectory()) return;
        if (keluar.size() >= BATAS_BERKAS) return;
        File[] anak = dir.listFiles();
        if (anak == null) return;
        java.util.Arrays.sort(anak);
        for (int i = 0; i < anak.length; i++) {
            if (keluar.size() >= BATAS_BERKAS) return;
            File a = anak[i];
            String jalur = awalan.length() == 0 ? a.getName() : awalan + "/" + a.getName();
            if (a.isDirectory()) {
                if (a.getName().equals("bin")) continue;
                kumpulBerkas(a, jalur, keluar);
            } else if (a.isFile()) {
                String n = a.getName();
                if (n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml")) {
                    if (n.endsWith(".zip")) continue;
                    keluar.add(jalur);
                }
            }
        }
    }

    private void kemas() {
        String namaProyek = proyekTerpilih(spSumber);
        if (namaProyek == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> dipilih = new ArrayList<String>();
        for (int i = 0; i < jalurBerkas.size(); i++) {
            if (centangBerkas.get(i).isChecked()) dipilih.add(jalurBerkas.get(i));
        }
        if (dipilih.isEmpty()) {
            Toast.makeText(this, TeksUI.t("Centang dulu minimal satu berkas."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String namaModul = eNamaModul.getText().toString().trim();
        String versi = eVersi.getText().toString().trim();
        if (namaModul.length() == 0) {
            Toast.makeText(this, TeksUI.t("Isi dulu nama modul."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File dirSumber = new File(BuatProyekHelper.root() + "/" + namaProyek);
        File zip = PabrikModul.kemasModul(dirSumber, dipilih, namaModul, versi);
        if (zip == null) {
            tvHasil.setText(TeksUI.t("Gagal mengemas modul. Periksa nama modul (huruf, angka, garis bawah, titik, strip) dan berkas yang dipilih."));
            announce(TeksUI.t("Gagal mengemas modul."));
            return;
        }
        PabrikModul.Metadata meta = PabrikModul.bacaMetadata(zip);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Modul dikemas: ")).append(zip.getName()).append("\n");
        if (meta != null) {
            b.append(meta.berkas.size()).append(TeksUI.t(" berkas, "));
            b.append(meta.stringDipakai.size()).append(TeksUI.t(" string dipakai, "));
            b.append(meta.izin.size()).append(TeksUI.t(" izin dipakai."));
        }
        tvHasil.setText(b.toString());
        announce(TeksUI.t("Modul dikemas: ") + zip.getName());
    }

    private void pilihModul() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("application/zip");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(it, TeksUI.t("Pilih Modul (.zip)")),
                    MINTA_MODUL);
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Gagal membuka pemilih berkas: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void salinModulDariUri(Uri uri) {
        File sementara = new File(getCacheDir(), "modul-dipilih.zip");
        String galat = null;
        InputStream masuk = null;
        try {
            masuk = getContentResolver().openInputStream(uri);
            galat = ArsipProyek.salinAliran(masuk, sementara);
        } catch (Throwable t) {
            galat = TeksUI.t("Gagal membaca berkas: ") + t.getMessage();
        }
        if (galat != null) {
            Toast.makeText(this, galat, Toast.LENGTH_SHORT).show();
            return;
        }
        PabrikModul.Metadata meta = PabrikModul.bacaMetadata(sementara);
        if (meta == null) {
            Toast.makeText(this, TeksUI.t("Berkas ini bukan modul (tidak ada modul.json di dalamnya)."),
                    Toast.LENGTH_SHORT).show();
            sementara.delete();
            return;
        }
        modulDipilih = sementara;
        tvModulDipilih.setText(TeksUI.t("Modul dipilih: ") + meta.nama
                + TeksUI.t(" versi ") + meta.versi);
        announce(TeksUI.t("Modul dipilih: ") + meta.nama);
    }

    private void pasang() {
        String namaProyek = proyekTerpilih(spTujuan);
        if (namaProyek == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (modulDipilih == null || !modulDipilih.isFile()) {
            Toast.makeText(this, TeksUI.t("Pilih dulu berkas modul (.zip)."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        PabrikModul.Metadata meta = PabrikModul.bacaMetadata(modulDipilih);
        String namaModul = meta == null ? modulDipilih.getName() : meta.nama;
        PabrikModul.Hasil hasil = PabrikModul.pasangModul(modulDipilih,
                new File(BuatProyekHelper.root() + "/" + namaProyek));
        tvHasil.setText(PabrikModul.ringkasan(hasil, namaModul));
        announce(PabrikModul.ringkasan(hasil, namaModul));
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
