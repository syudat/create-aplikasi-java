package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
 * Pembuat Game Tanpa Kode (Tahap 3E).
 *
 * Pilih jenis game (Tebak Angka / Kuis / Tangkap Bintang), isi nama game,
 * jumlah (level/soal/bintang), dan pesan menang, lalu tekan Buat Game.
 * IDE membuat proyek game Lua sungguhan yang bisa langsung dijalankan
 * dan dibangun — tanpa menulis satu baris kode pun.
 */
public class GameActivity extends Activity {

    private Spinner spinnerGame;
    private EditText eNama;
    private EditText eJumlah;
    private EditText ePesan;
    private TextView labelJumlah;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Pembuat Game"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pembuat Game"), 20);
        label(r, TeksUI.t("Buat game tanpa menulis kode: pilih jenis game, isi nama ")
                + TeksUI.t("dan pengaturannya, lalu tekan Buat Game. Game dibuat ")
                + TeksUI.t("sebagai proyek Lua dan bisa langsung dijalankan di ")
                + TeksUI.t("Kelola Project."), false);

        label(r, TeksUI.t("Jenis game:"), false);
        spinnerGame = new Spinner(this);
        List<String> jenis = new ArrayList<String>();
        for (int i = 0; i < GameTanpaKode.SEMUA.length; i++) {
            jenis.add(GameTanpaKode.SEMUA[i].nama + " — "
                    + GameTanpaKode.SEMUA[i].deskripsi);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, jenis);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGame.setAdapter(adapter);
        spinnerGame.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                perbaruiLabelJumlah();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        r.addView(spinnerGame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama game:"), false);
        eNama = new EditText(this);
        GayaStudio.edit(eNama);
        eNama.setHint(TeksUI.t("Contoh: Tebak Angkaku"));
        eNama.setSingleLine(true);
        r.addView(eNama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        labelJumlah = label(r, TeksUI.t("Jumlah level:"), false);
        eJumlah = new EditText(this);
        GayaStudio.edit(eJumlah);
        eJumlah.setHint(TeksUI.t("Contoh: 3"));
        eJumlah.setSingleLine(true);
        eJumlah.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        r.addView(eJumlah, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pesan menang:"), false);
        ePesan = new EditText(this);
        GayaStudio.edit(ePesan);
        ePesan.setHint("Contoh: Hebat, kamu menang!");
        ePesan.setSingleLine(true);
        r.addView(ePesan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Game"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatGame();
            }
        });

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        perbaruiLabelJumlah();
        announce(TeksUI.t("Pembuat Game. Pilih jenis game, isi nama dan pengaturannya, lalu tekan Buat Game."));
    }

    private void perbaruiLabelJumlah() {
        int pos = spinnerGame.getSelectedItemPosition();
        if (pos < 0 || pos >= GameTanpaKode.SEMUA.length) return;
        GameTanpaKode.Info info = GameTanpaKode.SEMUA[pos];
        labelJumlah.setText(info.labelJumlah + TeksUI.t(" (maksimal ") + info.jumlahMaks + "):");
        if (eJumlah.getText().toString().trim().length() == 0) {
            eJumlah.setText(String.valueOf(info.jumlahBawaan));
        }
    }

    private void buatGame() {
        int pos = spinnerGame.getSelectedItemPosition();
        if (pos < 0 || pos >= GameTanpaKode.SEMUA.length) return;
        GameTanpaKode.Info info = GameTanpaKode.SEMUA[pos];
        String nama = eNama.getText().toString().trim();
        if (nama.length() == 0) nama = info.nama;
        int jumlah = info.jumlahBawaan;
        try {
            jumlah = Integer.parseInt(eJumlah.getText().toString().trim());
        } catch (NumberFormatException ignored) {
        }
        if (jumlah < 1) jumlah = 1;
        if (jumlah > info.jumlahMaks) jumlah = info.jumlahMaks;
        String pesan = ePesan.getText().toString().trim();
        if (pesan.length() == 0) pesan = TeksUI.t("Selamat, kamu menang!");

        // Ronde 3: Lua dihapus — game tanpa kode kini membuat proyek Java
        // (keputusan pengguna 2026-11-17 00:22). GameTanpaKode menulis
        // MainActivity.java, bukan main.lua lagi.
        String dir = BuatProyekHelper.buat(this, nama, "java", "kosong");
        if (dir == null) return;
        String galat = GameTanpaKode.terapkanGame(new File(dir), info.id, nama, jumlah, pesan);
        if (galat != null) {
            Toast.makeText(this, TeksUI.t("Game gagal dibuat: ") + galat, Toast.LENGTH_SHORT).show();
            return;
        }
        String ok = TeksUI.t("Game \"") + new File(dir).getName()
                + TeksUI.t("\" berhasil dibuat. Buka di Kelola Project untuk memainkannya.");
        Toast.makeText(this, ok, Toast.LENGTH_SHORT).show();
        announce(ok);
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
