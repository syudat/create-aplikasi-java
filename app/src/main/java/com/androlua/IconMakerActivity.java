package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputFilter;
import android.view.Gravity;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat Ikon Otomatis (Tahap 3A — fitur pilihan al kautsar #5).
 *
 * Masalah yang dipecahkan: proyek baru ikonnya masih PNG 1x1 transparan
 * (app/src/main/res/drawable/icon.png, manifest template memakai
 * android:icon="@drawable/icon"). Activity ini membuat ikon dari tulisan
 * (1-2 huruf, mis. inisial nama aplikasi) + warna latar + warna tulisan,
 * digambar dengan Canvas/Bitmap Java (latar sudut membulat, huruf tebal di
 * tengah), ukuran 512x512 (ukuran standar ikon launcher), lalu disimpan
 * sebagai PNG ke proyek (mengganti icon.png 1x1 itu) atau ke Download.
 *
 * Integrasi: (a) tombol "Pembuat Ikon" di menu utama (JavaHomeActivity);
 * (b) setelah proyek berhasil dibuat, CreateProjectActivity menawarkan
 * pembuatan ikon untuk proyek itu (EXTRA_DIR = folder proyek);
 * (c) menu proyek di ProjectsActivity ("Ganti Ikon") membuka activity ini
 * dengan EXTRA_DIR = folder proyek yang dipilih.
 *
 * 100% Java murni — main.lua TIDAK diubah. Mesin Lua TIDAK disentuh.
 * UI programatik (tanpa layout XML), mengikuti pola Activity Java lain
 * (JavaHomeActivity / CreateProjectActivity): judul diumumkan ke
 * aksesibilitas, tombol lebar penuh, Back kembali ke layar sebelumnya.
 * Pratinjau berupa ImageView dengan contentDescription yang dibacakan
 * Jieshuo/TalkBack ("Pratinjau ikon: huruf GD, latar biru").
 */
public class IconMakerActivity extends Activity {
    /** Extra Intent: folder proyek (absolut). Bila ada, ikon disimpan ke
     *  folder itu (app/src/main/res/drawable/icon.png). Bila tidak ada,
     *  pengguna memilih proyek dulu lewat tombol "Pilih Proyek". */
    public static final String EXTRA_DIR = "dir";

    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final int UKURAN_IKON = 512;

    // Warna latar siap pakai (nama + kode hex). Urutan disusun dari yang
    // paling umum dipakai untuk ikon aplikasi.
    private static final String[] BG_NAMA = {
        "Biru", "Hijau", "Merah", "Oranye", "Ungu", "Teal", "Abu-abu", "Hitam"
    };
    private static final String[] BG_HEX = {
        "#2196F3", "#4CAF50", "#F44336", "#FF9800",
        "#9C27B0", "#009688", "#607D8B", "#212121"
    };
    // Warna tulisan siap pakai.
    private static final String[] FG_NAMA = { "Putih", "Hitam", "Kuning" };
    private static final String[] FG_HEX = { "#FFFFFF", "#212121", "#FFEB3B" };

    private static final String BG_BAWAAN = "#2196F3";
    private static final String FG_BAWAAN = "#FFFFFF";

    private String dirProyek;
    private EditText eTeks;
    private EditText eBgHex;
    private EditText eFgHex;
    private ImageView pratinjau;
    private Button btnSimpanProyek;
    private Button btnPilihProyek;
    private TextView labelProyek;
    private Bitmap bitmapIkon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Pembuat Ikon Otomatis"));

        dirProyek = getIntent().getStringExtra(EXTRA_DIR);

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pembuat Ikon Otomatis"), 20);
        label(r, TeksUI.t("Buat ikon aplikasi dari tulisan dan warna. Ikon berukuran ")
                + TeksUI.t("512x512 dan menggantikan icon.png 1x1 transparan di proyek ")
                + TeksUI.t("(manifest memakai @drawable/icon)."), false);

        labelProyek = label(r, labelProyekTeks(), false);

        label(r, TeksUI.t("Tulisan ikon (1-2 huruf, contoh: GD untuk Garuda Decode)"));
        eTeks = new EditText(this);
        GayaStudio.edit(eTeks);
        eTeks.setHint(TeksUI.t("Contoh: GD"));
        eTeks.setTextSize(14);
        eTeks.setSingleLine(true);
        eTeks.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(4) });
        if (dirProyek != null) {
            String awal = inisial(new File(dirProyek).getName());
            if (!awal.isEmpty()) eTeks.setText(awal);
        }
        r.addView(eTeks, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Warna latar (kode hex, contoh: #2196F3)"));
        eBgHex = new EditText(this);
        GayaStudio.edit(eBgHex);
        eBgHex.setHint("#2196F3");
        eBgHex.setText(BG_BAWAAN);
        eBgHex.setTextSize(14);
        eBgHex.setSingleLine(true);
        r.addView(eBgHex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Pilih warna latar"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihWarna(TeksUI.t("Pilih warna latar"), BG_NAMA, BG_HEX, eBgHex);
            }
        });

        label(r, TeksUI.t("Warna tulisan (kode hex, contoh: #FFFFFF)"));
        eFgHex = new EditText(this);
        GayaStudio.edit(eFgHex);
        eFgHex.setHint("#FFFFFF");
        eFgHex.setText(FG_BAWAAN);
        eFgHex.setTextSize(14);
        eFgHex.setSingleLine(true);
        r.addView(eFgHex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Pilih warna tulisan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihWarna(TeksUI.t("Pilih warna tulisan"), FG_NAMA, FG_HEX, eFgHex);
            }
        });

        button(r, TeksUI.t("Buat Ikon"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatPratinjau();
            }
        });

        label(r, TeksUI.t("Pratinjau ikon"));
        pratinjau = new ImageView(this);
        pratinjau.setContentDescription(TeksUI.t("Belum ada pratinjau ikon."));
        LinearLayout.LayoutParams lpPratinjau = new LinearLayout.LayoutParams(
                dp(192), dp(192));
        lpPratinjau.gravity = Gravity.CENTER_HORIZONTAL;
        lpPratinjau.topMargin = dp(8);
        r.addView(pratinjau, lpPratinjau);

        btnSimpanProyek = button(r, TeksUI.t("Simpan ke Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanKeProyek();
            }
        });
        btnPilihProyek = button(r, TeksUI.t("Pilih Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihProyek();
            }
        });
        button(r, TeksUI.t("Simpan ke Download"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanKeDownload();
            }
        });
        button(r, TeksUI.t("Kembali"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        perbaruiTombolProyek();
        announce(TeksUI.t("Pembuat Ikon Otomatis"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sama seperti onResume() di Activity Java lain: lanjutkan pemasangan
        // APK yang tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
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

    private TextView label(LinearLayout parent, String text) {
        return label(parent, text, false);
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

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private String labelProyekTeks() {
        if (dirProyek == null) return TeksUI.t("Proyek: belum dipilih.");
        return TeksUI.t("Proyek: ") + new File(dirProyek).getName();
    }

    private void perbaruiTombolProyek() {
        if (btnSimpanProyek != null) {
            btnSimpanProyek.setVisibility(dirProyek == null ? View.GONE : View.VISIBLE);
        }
        if (btnPilihProyek != null) {
            btnPilihProyek.setVisibility(dirProyek == null ? View.VISIBLE : View.GONE);
        }
        if (labelProyek != null) labelProyek.setText(labelProyekTeks());
    }

    // ------------------------------------------------------- pilih warna

    private void pilihWarna(String judul, final String[] nama, final String[] hex,
                            final EditText target) {
        final String[] tampil = new String[nama.length];
        for (int i = 0; i < nama.length; i++) tampil[i] = TeksUI.t(nama[i]);
        announce(judul + ". " + nama.length + TeksUI.t(" pilihan."));
        new AlertDialog.Builder(this)
                .setTitle(judul)
                .setItems(tampil, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < hex.length) {
                            target.setText(hex[which]);
                            toast(TeksUI.t("Warna dipilih: ") + tampil[which] + " (" + hex[which] + ")");
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    // ------------------------------------------------------- pilih proyek

    private void pilihProyek() {
        final List<String> namaProyek = daftarNamaProyek();
        if (namaProyek.isEmpty()) {
            toast("Belum ada proyek. Buat proyek dulu dari menu Buat Project.");
            return;
        }
        final String[] items = namaProyek.toArray(new String[namaProyek.size()]);
        announce(TeksUI.t("Pilih Proyek. ") + items.length + TeksUI.t(" proyek tersedia."));
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pilih Proyek"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < items.length) {
                            dirProyek = ROOT + "/" + items[which];
                            if (eTeks.getText().toString().trim().isEmpty()) {
                                eTeks.setText(inisial(items[which]));
                            }
                            perbaruiTombolProyek();
                            toast("Proyek dipilih: " + items[which]);
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    /** Daftar nama proyek (folder di Download/project yang berisi init.lua),
     *  murni Java — sama kriterianya dengan daftarProyek() di main.lua. */
    private List<String> daftarNamaProyek() {
        List<String> hasil = new ArrayList<String>();
        try {
            File root = new File(ROOT);
            File[] anak = root.listFiles();
            if (anak != null) {
                for (File f : anak) {
                    if (f.isDirectory()
                            && new File(f, "init.lua").exists()) {
                        hasil.add(f.getName());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return hasil;
    }

    // ------------------------------------------------------- buat + simpan

    /** Validasi masukan lalu gambar ikon; mengembalikan null bila tidak valid
     *  (toast sudah ditampilkan). Dipakai pratinjau dan kedua tombol simpan. */
    private Bitmap buatIkonJikaValid() {
        String teks = eTeks.getText().toString().trim();
        if (teks.isEmpty()) {
            if (dirProyek != null) {
                teks = inisial(new File(dirProyek).getName());
            }
            if (teks.isEmpty()) {
                toast("Tulisan ikon belum diisi (1-2 huruf, contoh: GD)");
                return null;
            }
        }
        String bg = eBgHex.getText().toString().trim();
        String fg = eFgHex.getText().toString().trim();
        if (!warnaValid(bg)) {
            toast("Warna latar tidak valid. Pakai kode hex, contoh: #2196F3");
            return null;
        }
        if (!warnaValid(fg)) {
            toast("Warna tulisan tidak valid. Pakai kode hex, contoh: #FFFFFF");
            return null;
        }
        return buatIkon(teks, parseWarna(bg, 0xFF2196F3),
                parseWarna(fg, 0xFFFFFFFF), UKURAN_IKON);
    }

    private void buatPratinjau() {
        Bitmap bmp = buatIkonJikaValid();
        if (bmp == null) return;
        bitmapIkon = bmp;
        pratinjau.setImageBitmap(bmp);
        String deskripsi = TeksUI.t("Pratinjau ikon: huruf ")
                + eTeks.getText().toString().trim()
                + TeksUI.t(", latar ") + eBgHex.getText().toString().trim()
                + TeksUI.t(", tulisan ") + eFgHex.getText().toString().trim() + ".";
        pratinjau.setContentDescription(deskripsi);
        announce(deskripsi);
        toast("Ikon dibuat. Ukuran 512x512.");
    }

    private void simpanKeProyek() {
        if (dirProyek == null) {
            toast("Pilih proyek dulu.");
            return;
        }
        Bitmap bmp = bitmapIkon;
        if (bmp == null) {
            bmp = buatIkonJikaValid();
            if (bmp == null) return;
            bitmapIkon = bmp;
        }
        File target = new File(dirProyek + "/app/src/main/res/drawable/icon.png");
        if (simpanPng(bmp, target)) {
            String pesan = TeksUI.t("Ikon disimpan ke proyek ") + new File(dirProyek).getName()
                    + TeksUI.t(" (res/drawable/icon.png, 512x512).");
            toast(pesan);
            announce(pesan);
            finish();
        } else {
            toast("Gagal menyimpan ikon. Pastikan izin penyimpanan sudah aktif.");
        }
    }

    private void simpanKeDownload() {
        Bitmap bmp = bitmapIkon;
        if (bmp == null) {
            bmp = buatIkonJikaValid();
            if (bmp == null) return;
            bitmapIkon = bmp;
        }
        String teks = eTeks.getText().toString().trim().toLowerCase();
        if (teks.isEmpty()) teks = "ikon";
        File target = new File(Environment.getExternalStorageDirectory().getPath()
                + "/Download/icon-" + teks + ".png");
        if (simpanPng(bmp, target)) {
            String pesan = TeksUI.t("Ikon disimpan ke Download/icon-") + teks + TeksUI.t(".png.");
            toast(pesan);
            announce(pesan);
        } else {
            toast("Gagal menyimpan ikon. Pastikan izin penyimpanan sudah aktif.");
        }
    }

    // ------------------------------------------------------- logika murni
    // (static, tanpa Android Context — bisa diuji ulang lewat simulasi)

    /** Gambar ikon: latar sudut membulat + huruf tebal di tengah.
     *  Ukuran teks menyesuaikan otomatis (mengecil sampai muat). */
    public static Bitmap buatIkon(String teks, int warnaLatar, int warnaTulisan,
                                  int ukuran) {
        Bitmap bmp = Bitmap.createBitmap(ukuran, ukuran, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint latar = new Paint(Paint.ANTI_ALIAS_FLAG);
        latar.setColor(warnaLatar);
        float r = ukuran / 8f;
        c.drawRoundRect(new RectF(0, 0, ukuran, ukuran), r, r, latar);
        Paint tulis = new Paint(Paint.ANTI_ALIAS_FLAG);
        tulis.setColor(warnaTulisan);
        tulis.setTypeface(Typeface.DEFAULT_BOLD);
        tulis.setTextAlign(Paint.Align.CENTER);
        ukuranTeksCocok(tulis, teks, ukuran * 0.78f, ukuran * 0.55f);
        Paint.FontMetrics fm = tulis.getFontMetrics();
        float y = ukuran / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(teks, ukuran / 2f, y, tulis);
        return bmp;
    }

    /** Kecilkan ukuran teks sampai lebarnya muat (maks. 10% mengecil per
     *  langkah, berhenti di 10 piksel). */
    static float ukuranTeksCocok(Paint p, String teks, float maksLebar, float awal) {
        float size = awal;
        p.setTextSize(size);
        while (size > 10 && p.measureText(teks) > maksLebar) {
            size *= 0.9f;
            p.setTextSize(size);
        }
        return size;
    }

    /** Huruf awal tiap kata, maks. 2 huruf, huruf besar.
     *  Contoh: "Garuda Decode" -> "GD", "Aplikasi" -> "A". */
    public static String inisial(String nama) {
        if (nama == null) return "";
        String[] kata = nama.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String k : kata) {
            if (k.isEmpty()) continue;
            sb.append(Character.toUpperCase(k.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.toString();
    }

    /** Parse "#RRGGBB" / "RRGGBB" / "#AARRGGBB" menjadi int ARGB.
     *  Tidak valid -> warna bawaan. Murni Java (tanpa android.graphics.Color)
     *  supaya logikanya bisa diuji. */
    public static int parseWarna(String s, int bawaan) {
        if (s == null) return bawaan;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            if (h.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(h, 16);
            }
            if (h.length() == 8) {
                return (int) Long.parseLong(h, 16);
            }
        } catch (Throwable ignored) {
        }
        return bawaan;
    }

    /** Benar bila berupa 6 atau 8 digit hex (boleh diawali #). */
    public static boolean warnaValid(String s) {
        if (s == null) return false;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6 && h.length() != 8) return false;
        for (int i = 0; i < h.length(); i++) {
            char ch = h.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /** Tulis Bitmap sebagai PNG. Mengembalikan true bila berkas ada dan
     *  tidak kosong. Membuat folder induk bila belum ada. */
    public static boolean simpanPng(Bitmap bmp, File target) {
        if (bmp == null || target == null) return false;
        try {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            FileOutputStream out = new FileOutputStream(target);
            try {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            return target.exists() && target.length() > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
