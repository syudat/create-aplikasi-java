package com.androlua;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat Widget & Wallpaper (Tahap 3G).
 *
 * Dua bagian dalam satu layar:
 * (a) Pembuat Widget: pilih proyek, isi teks widget, tekan Buat Widget —
 * IDE menulis WidgetSaya.java + layout widget + info widget ke proyek
 * dan menyisipkan receiver ke AndroidManifest.xml proyek. Widget bisa
 * dipasang dari layar utama HP setelah aplikasi dibangun.
 * (b) Pembuat Wallpaper: isi tulisan + warna latar/tulisan, lihat
 * pratinjau, simpan sebagai PNG atau langsung pasang sebagai wallpaper
 * lewat WallpaperManager.
 */
public class WidgetActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spinnerProyek;
    private EditText teksWidgetEdit;
    private EditText teksWallpaperEdit;
    private EditText hexLatarEdit;
    private EditText hexTulisanEdit;
    private ImageView pratinjauView;
    private TextView hasilView;
    private Bitmap bitmapWallpaper;
    private final List<String> daftarProyek = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Widget & Wallpaper"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Widget & Wallpaper"), 20);
        label(r, TeksUI.t("Buat widget layar utama untuk proyekmu, atau buat gambar ")
                + TeksUI.t("wallpaper dari tulisan dan warna. Semua berjalan di dalam ")
                + TeksUI.t("HP (tanpa internet)."), false);

        // ---------------- Bagian widget ----------------
        heading(r, TeksUI.t("Pembuat Widget"), 16);
        label(r, TeksUI.t("Pilih proyek:"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Teks widget:"), false);
        teksWidgetEdit = new EditText(this);
        GayaStudio.edit(teksWidgetEdit);
        teksWidgetEdit.setHint("Contoh: Aplikasi Saya");
        r.addView(teksWidgetEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Widget"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatWidget();
            }
        });

        // ---------------- Bagian wallpaper ----------------
        heading(r, TeksUI.t("Pembuat Wallpaper"), 16);
        label(r, TeksUI.t("Tulisan wallpaper:"), false);
        teksWallpaperEdit = new EditText(this);
        GayaStudio.edit(teksWallpaperEdit);
        teksWallpaperEdit.setHint(TeksUI.t("Contoh: Semangat!"));
        r.addView(teksWallpaperEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Warna latar (heksa, contoh #2196F3):"), false);
        hexLatarEdit = new EditText(this);
        GayaStudio.edit(hexLatarEdit);
        hexLatarEdit.setText("#2196F3");
        r.addView(hexLatarEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Warna tulisan (heksa, contoh #FFFFFF):"), false);
        hexTulisanEdit = new EditText(this);
        GayaStudio.edit(hexTulisanEdit);
        hexTulisanEdit.setText("#FFFFFF");
        r.addView(hexTulisanEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Pratinjau Wallpaper"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatPratinjau();
            }
        });

        pratinjauView = new ImageView(this);
        pratinjauView.setContentDescription(TeksUI.t("Pratinjau wallpaper."));
        r.addView(pratinjauView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        button(r, TeksUI.t("Simpan Wallpaper (Download)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanWallpaper();
            }
        });

        button(r, TeksUI.t("Pasang sebagai Wallpaper"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasangWallpaper();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        hasilView = new TextView(this);
        hasilView.setText("Belum ada hasil.");
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

        announce(TeksUI.t("Widget dan Wallpaper. Pilih proyek untuk membuat widget, ")
                + TeksUI.t("atau isi tulisan dan warna untuk membuat wallpaper."));
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private void buatWidget() {
        if (spinnerProyek.getSelectedItem() == null) return;
        String nama = String.valueOf(spinnerProyek.getSelectedItem());
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(ROOT + "/" + nama);
        String paket = InfoProyek.namaPaket(dir);
        if (paket == null || paket.length() == 0) {
            Toast.makeText(this, TeksUI.t("Nama paket proyek tidak ditemukan."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String teks = teksWidgetEdit.getText().toString().trim();
        if (teks.length() == 0) teks = nama;
        String galat = PembuatWidget.buatWidget(dir, paket, teks);
        if (galat != null) {
            Toast.makeText(this, galat, Toast.LENGTH_SHORT).show();
            return;
        }
        galat = PembuatWidget.tambahKeManifest(
                new File(dir, "app/src/main/AndroidManifest.xml"), paket);
        String lap;
        if (galat != null) {
            lap = TeksUI.t("Widget dibuat, tapi manifest proyek belum diperbarui: ") + galat
                    + "\n" + TeksUI.t("Berkas yang dibuat:") + "\n" + "- WidgetSaya.java" + "\n" + "- res/layout/widget_saya.xml"
                    + "\n- res/xml/widget_info.xml";
        } else {
            lap = TeksUI.t("Widget berhasil dibuat di proyek ") + nama + ":\n"
                    + "- WidgetSaya.java\n- res/layout/widget_saya.xml\n"
                    + "- res/xml/widget_info.xml\n"
                    + TeksUI.t("- receiver sudah ditambahkan ke AndroidManifest.xml proyek.") + "\n"
                    + TeksUI.t("Bangun proyek, lalu pasang widget dari layar utama HP.");
        }
        hasilView.setText(lap);
        Toast.makeText(this, TeksUI.t("Widget berhasil dibuat."), Toast.LENGTH_SHORT).show();
        announce(TeksUI.t("Widget berhasil dibuat di proyek ") + nama + ". " + lap);
    }

    private int parseWarna(String heksa, int bawaan) {
        try {
            String h = heksa.trim();
            if (h.startsWith("#")) h = h.substring(1);
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (Throwable ignored) {
        }
        return bawaan;
    }

    private void buatPratinjau() {
        String teks = teksWallpaperEdit.getText().toString().trim();
        if (teks.length() == 0) teks = TeksUI.t("Aplikasi Saya");
        int latar = parseWarna(hexLatarEdit.getText().toString(), 0xFF2196F3);
        int tulis = parseWarna(hexTulisanEdit.getText().toString(), 0xFFFFFFFF);
        bitmapWallpaper = PembuatWallpaper.buatWallpaper(teks, latar, tulis, 1080, 1920);
        pratinjauView.setImageBitmap(bitmapWallpaper);
        pratinjauView.setContentDescription(TeksUI.t("Pratinjau wallpaper: tulisan ") + teks
                + TeksUI.t(", latar ") + AnalisisGambar.heksa(latar) + TeksUI.t(", tulisan ")
                + AnalisisGambar.heksa(tulis) + ".");
        hasilView.setText(TeksUI.t("Pratinjau wallpaper dibuat: tulisan \"") + teks
                + TeksUI.t("\", latar ") + AnalisisGambar.heksa(latar)
                + TeksUI.t(", tulisan ") + AnalisisGambar.heksa(tulis) + ".");
        announce(TeksUI.t("Pratinjau wallpaper dibuat."));
    }

    private void simpanWallpaper() {
        if (bitmapWallpaper == null) {
            Toast.makeText(this, TeksUI.t("Buat pratinjau dulu."), Toast.LENGTH_SHORT).show();
            return;
        }
        File target = new File(Environment.getExternalStorageDirectory().getPath()
                + "/Download/wallpaper-" + System.currentTimeMillis() + ".png");
        if (PembuatWallpaper.simpanPng(bitmapWallpaper, target)) {
            hasilView.setText("Wallpaper disimpan di " + target.getPath() + ".");
            Toast.makeText(this, TeksUI.t("Wallpaper disimpan."), Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Wallpaper disimpan di folder Download."));
        } else {
            Toast.makeText(this, TeksUI.t("Gagal menyimpan wallpaper."), Toast.LENGTH_SHORT).show();
        }
    }

    private void pasangWallpaper() {
        if (bitmapWallpaper == null) {
            Toast.makeText(this, TeksUI.t("Buat pratinjau dulu."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (PembuatWallpaper.pasangWallpaper(this, bitmapWallpaper)) {
            hasilView.setText("Wallpaper sudah dipasang sebagai wallpaper HP.");
            Toast.makeText(this, TeksUI.t("Wallpaper dipasang."), Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Wallpaper sudah dipasang sebagai wallpaper HP."));
        } else {
            Toast.makeText(this, TeksUI.t("Gagal memasang wallpaper."), Toast.LENGTH_SHORT).show();
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
