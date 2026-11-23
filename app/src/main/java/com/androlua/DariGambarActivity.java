package com.androlua;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Aplikasi dari Gambar (Tahap 3G).
 *
 * Pilih gambar dari galeri/berkas, IDE menganalisisnya (ukuran dan
 * warna dominan) lalu membuat proyek aplikasi galeri sungguhan dari
 * gambar itu: gambar disalin ke assets proyek, warna tema diambil dari
 * warna dominan gambar, dan nama aplikasi diambil dari nama berkas
 * gambar. Semua berjalan di dalam HP (tanpa internet).
 */
public class DariGambarActivity extends Activity {
    private static final int MINTA_GAMBAR = 1;

    private EditText namaEdit;
    private ImageView pratinjauView;
    private TextView hasilView;
    private Button buatButton;
    private File gambarPilihan;
    private int warnaDominan = 0xFF2196F3;
    private int lebarGambar;
    private int tinggiGambar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Aplikasi dari Gambar"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Aplikasi dari Gambar"), 20);
        label(r, TeksUI.t("Pilih gambar dari galerimu. IDE akan menganalisis warna ")
                + TeksUI.t("dominannya lalu membuat aplikasi galeri dari gambar itu: ")
                + TeksUI.t("gambar ditampilkan memenuhi layar, warna tema diambil ")
                + TeksUI.t("dari warna dominan gambar, dan nama aplikasi diambil dari ")
                + TeksUI.t("nama berkas gambar."), false);

        button(r, TeksUI.t("Pilih Gambar dari Galeri"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihGambar();
            }
        });

        pratinjauView = new ImageView(this);
        pratinjauView.setContentDescription(TeksUI.t("Pratinjau gambar yang dipilih."));
        r.addView(pratinjauView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        label(r, TeksUI.t("Nama aplikasi (bawaan dari nama berkas gambar):"), false);
        namaEdit = new EditText(this);
        GayaStudio.edit(namaEdit);
        namaEdit.setHint(TeksUI.t("Contoh: Pantai Sore"));
        r.addView(namaEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buatButton = button(r, TeksUI.t("Buat Aplikasi Galeri"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatAplikasi();
            }
        });
        buatButton.setEnabled(false);

        label(r, TeksUI.t("Hasil analisis:"), false);
        hasilView = new TextView(this);
        hasilView.setText("Belum ada gambar yang dipilih.");
        hasilView.setTextSize(14);
        hasilView.setTextIsSelectable(true);
        hasilView.setPadding(dp(4), dp(4), dp(4), dp(4));
        r.addView(hasilView);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce(TeksUI.t("Aplikasi dari Gambar. Tekan Pilih Gambar dari Galeri dulu."));
    }

    private void pilihGambar() {
        try {
            Intent ambil = new Intent(Intent.ACTION_GET_CONTENT);
            ambil.setType("image/*");
            startActivityForResult(Intent.createChooser(ambil, TeksUI.t("Pilih Gambar")),
                    MINTA_GAMBAR);
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Tidak bisa membuka pemilih gambar: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MINTA_GAMBAR || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            File salinan = new File(getCacheDir(), "gambar-pilihan.jpg");
            InputStream masuk = getContentResolver().openInputStream(uri);
            if (masuk == null) {
                Toast.makeText(this, TeksUI.t("Tidak bisa membaca gambar itu."),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            FileOutputStream keluar = new FileOutputStream(salinan);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
            } finally {
                keluar.close();
                masuk.close();
            }
            gambarPilihan = salinan;
            analisisGambar();
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Gagal memproses gambar: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void analisisGambar() {
        Bitmap bmp = BitmapFactory.decodeFile(gambarPilihan.getPath());
        if (bmp == null) {
            Toast.makeText(this, TeksUI.t("Berkas itu bukan gambar yang bisa dibaca."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        lebarGambar = bmp.getWidth();
        tinggiGambar = bmp.getHeight();
        int[] piksel = new int[lebarGambar * tinggiGambar];
        bmp.getPixels(piksel, 0, lebarGambar, 0, 0, lebarGambar, tinggiGambar);
        warnaDominan = AnalisisGambar.warnaDominan(piksel, 4);
        String heksa = AnalisisGambar.heksa(warnaDominan);
        String namaSaran = AplikasiDariGambar.namaDariBerkas(gambarPilihan.getName());
        if (namaEdit.getText().toString().trim().length() == 0) {
            namaEdit.setText(namaSaran);
        }
        pratinjauView.setImageBitmap(bmp);
        pratinjauView.setContentDescription(TeksUI.t("Gambar yang dipilih, ukuran ")
                + lebarGambar + TeksUI.t(" kali ") + tinggiGambar + TeksUI.t(" piksel, warna dominan ")
                + heksa + ".");
        String lap = TeksUI.t("Gambar dianalisis:") + "\n" + TeksUI.t("- Ukuran: ") + lebarGambar + " x " + tinggiGambar
                + TeksUI.t(" piksel.") + "\n" + TeksUI.t("- Warna dominan: ") + heksa + "." + "\n" + TeksUI.t("- Nama aplikasi yang ")
                + TeksUI.t("disarankan: ") + namaSaran + "." + "\n" + TeksUI.t("Tekan Buat Aplikasi Galeri.");
        hasilView.setText(lap);
        buatButton.setEnabled(true);
        announce(TeksUI.t("Gambar dianalisis. Ukuran ") + lebarGambar + TeksUI.t(" kali ") + tinggiGambar
                + TeksUI.t(" piksel, warna dominan ") + heksa + TeksUI.t(". Tekan Buat Aplikasi Galeri."));
    }

    private void buatAplikasi() {
        if (gambarPilihan == null || !gambarPilihan.isFile()) {
            Toast.makeText(this, TeksUI.t("Pilih gambar dulu."), Toast.LENGTH_SHORT).show();
            return;
        }
        String nama = namaEdit.getText().toString().trim();
        if (nama.length() == 0) nama = TeksUI.t("Galeri Saya");
        // Ronde 3: Lua dihapus — aplikasi dari gambar kini membuat proyek Java
        // (keputusan pengguna 2026-11-17 00:22). AplikasiDariGambar menulis
        // MainActivity.java, bukan main.lua lagi.
        String dir = BuatProyekHelper.buat(this, nama, "java", "kosong");
        if (dir == null) {
            Toast.makeText(this, TeksUI.t("Gagal membuat proyek galeri."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String galat = AplikasiDariGambar.buatGaleri(new File(dir), gambarPilihan,
                nama, warnaDominan);
        if (galat != null) {
            Toast.makeText(this, galat, Toast.LENGTH_SHORT).show();
            return;
        }
        String lap = TeksUI.t("Aplikasi galeri \"") + nama + TeksUI.t("\" berhasil dibuat dari gambar.") + "\n"
                + TeksUI.t("- Ukuran gambar: ") + lebarGambar + " x " + tinggiGambar + TeksUI.t(" piksel.") + "\n"
                + TeksUI.t("- Warna tema: ") + AnalisisGambar.heksa(warnaDominan) + ".\n"
                + TeksUI.t("Buka Kelola Project untuk menjalankan atau membangunnya.");
        hasilView.setText(lap);
        Toast.makeText(this, TeksUI.t("Aplikasi galeri berhasil dibuat."), Toast.LENGTH_SHORT).show();
        announce(TeksUI.t("Aplikasi galeri ") + nama + TeksUI.t(" berhasil dibuat dari gambar."));
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
