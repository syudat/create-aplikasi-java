package com.androlua;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
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

/**
 * Integrasi Media Sosial (Tahap 3G).
 *
 * Pilih proyek, lalu tambahkan kode berbagi sungguhan ke aplikasi hasil
 * untuk 3 platform (WhatsApp, Facebook, Telegram). Kode disisipkan ke
 * berkas utama proyek (main.lua / MainActivity.java / MainActivity.kt /
 * index.html) dan bisa langsung dipakai saat aplikasi dijalankan.
 * Tombol "Uji Berbagi" membuka chooser berbagi dari IDE dengan teks
 * contoh, supaya pengguna bisa merasakan alurnya sebelum membangun.
 */
public class MediaSosialActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spinnerProyek;
    private TextView hasilView;
    private final List<String> daftarProyek = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Media Sosial"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Media Sosial"), 20);
        label(r, TeksUI.t("Tambahkan tombol berbagi ke aplikasimu. IDE menulis kode ")
                + TeksUI.t("berbagi sungguhan ke proyek (WhatsApp, Facebook, Telegram) ")
                + TeksUI.t("dan kamu bisa mengujinya langsung dari sini."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Tambah Berbagi ke WhatsApp"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambah("WhatsApp");
            }
        });

        button(r, TeksUI.t("Tambah Berbagi ke Facebook"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambah("Facebook");
            }
        });

        button(r, TeksUI.t("Tambah Berbagi ke Telegram"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambah("Telegram");
            }
        });

        button(r, TeksUI.t("Tambah Ketiganya Sekaligus"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambahSemua();
            }
        });

        button(r, TeksUI.t("Uji Berbagi dari IDE"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ujiBerbagi();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        hasilView = new TextView(this);
        hasilView.setText("Belum ada hasil. Pilih proyek, lalu pilih platform berbagi.");
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

        announce(TeksUI.t("Media Sosial. Pilih proyek, lalu tambah berbagi ke WhatsApp, ")
                + TeksUI.t("Facebook, atau Telegram."));
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private File dirTerpilih() {
        if (spinnerProyek.getSelectedItem() == null) return null;
        String nama = String.valueOf(spinnerProyek.getSelectedItem());
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) return null;
        return new File(ROOT + "/" + nama);
    }

    private void tambah(String platform) {
        File dir = dirTerpilih();
        if (dir == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String tipe = InfoProyek.tipe(dir);
        String nama = InfoProyek.namaAplikasi(dir);
        if (nama == null || nama.length() == 0) nama = dir.getName();
        String hasil = MediaSosial.tambahBagikan(dir, tipe, platform, nama);
        if (hasil == null) {
            String lap = TeksUI.t("Kode berbagi ") + platform + TeksUI.t(" sudah ditambahkan ke proyek ")
                    + dir.getName() + TeksUI.t(". Bangun proyek, lalu panggil fungsi bagikan")
                    + platform + TeksUI.t(" dari tombol di aplikasimu.");
            hasilView.setText(lap);
            Toast.makeText(this, TeksUI.t("Berbagi ") + platform + " ditambahkan.",
                    Toast.LENGTH_SHORT).show();
            announce(lap);
        } else if (hasil.startsWith("Kode berbagi")) {
            hasilView.setText(hasil);
            Toast.makeText(this, hasil, Toast.LENGTH_SHORT).show();
            announce(hasil);
        } else {
            Toast.makeText(this, hasil, Toast.LENGTH_SHORT).show();
        }
    }

    private void tambahSemua() {
        File dir = dirTerpilih();
        if (dir == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String tipe = InfoProyek.tipe(dir);
        String nama = InfoProyek.namaAplikasi(dir);
        if (nama == null || nama.length() == 0) nama = dir.getName();
        StringBuilder sb = new StringBuilder();
        int berhasil = 0;
        for (int i = 0; i < MediaSosial.PLATFORM.length; i++) {
            String p = MediaSosial.PLATFORM[i];
            String hasil = MediaSosial.tambahBagikan(dir, tipe, p, nama);
            if (hasil == null) {
                berhasil++;
                sb.append("- ").append(p).append(TeksUI.t(": ditambahkan.") + "\n");
            } else {
                sb.append("- ").append(p).append(": ").append(hasil).append("\n");
            }
        }
        hasilView.setText("Hasil tambah berbagi di proyek " + dir.getName() + ":\n" + sb);
        Toast.makeText(this, berhasil + TeksUI.t(" dari 3 platform ditambahkan."),
                Toast.LENGTH_SHORT).show();
        announce(berhasil + TeksUI.t(" dari 3 platform berbagi ditambahkan ke proyek ")
                + dir.getName() + ".");
    }

    private void ujiBerbagi() {
        File dir = dirTerpilih();
        String nama = dir == null ? TeksUI.t("aplikasi saya") : dir.getName();
        Intent kirim = new Intent(Intent.ACTION_SEND);
        kirim.setType("text/plain");
        kirim.putExtra(Intent.EXTRA_TEXT, MediaSosial.teksContoh(nama));
        try {
            startActivity(Intent.createChooser(kirim, TeksUI.t("Uji Berbagi")));
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Tidak ada aplikasi berbagi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
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
