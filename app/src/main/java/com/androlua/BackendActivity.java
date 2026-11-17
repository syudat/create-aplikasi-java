package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

/**
 * Pembuat Backend Otomatis (Tahap 3F).
 *
 * Pilih jenis backend (Catatan / Daftar Tugas / Papan Pesan) dan nama
 * proyek, lalu IDE membuat proyek HTML sungguhan yang menjadi backend:
 * halaman index.html berisi formulir + daftar data dengan localStorage
 * sebagai database lokal, plus backend-klien.js dan BACA-SAYA.txt.
 * Backend lokal: data hanya di HP yang menjalankan aplikasi (dua HP
 * tidak berbagi data) — dijelaskan di layar dan di BACA-SAYA.txt.
 */
public class BackendActivity extends Activity {

    private EditText eNama;
    private Spinner spJenis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Pembuat Backend");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, "Pembuat Backend", 20);
        label(r, "Buat backend untuk aplikasimu tanpa menulis kode server. "
                + "Backend ini berjalan di HP (data tersimpan di localStorage) — "
                + "cocok untuk catatan, daftar tugas, dan papan pesan di satu HP. "
                + "Dua HP belum bisa berbagi data yang sama karena tidak ada server internet.",
                false);

        label(r, "Nama proyek backend:", false);
        eNama = new EditText(this);
        GayaStudio.edit(eNama);
        eNama.setHint("Contoh: Catatan Saya");
        r.addView(eNama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Jenis backend:", false);
        spJenis = new Spinner(this);
        String[] jenis = new String[BackendOtomatis.SEMUA.length];
        for (int i = 0; i < jenis.length; i++) {
            jenis[i] = BackendOtomatis.SEMUA[i].nama + " — " + BackendOtomatis.SEMUA[i].penjelasan;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, jenis);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spJenis.setAdapter(adapter);
        r.addView(spJenis, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Buat Backend", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatBackend();
            }
        });
        button(r, "Kembali ke menu sebelumnya", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce("Pembuat Backend. Pilih jenis backend dan nama proyek, lalu tekan Buat Backend.");
    }

    private void buatBackend() {
        String nama = eNama.getText().toString().trim();
        if (nama.isEmpty()) {
            toast("Nama proyek backend belum diisi.");
            return;
        }
        int pos = spJenis.getSelectedItemPosition();
        if (pos < 0 || pos >= BackendOtomatis.SEMUA.length) {
            toast("Pilih dulu jenis backend.");
            return;
        }
        final BackendOtomatis.Jenis jenis = BackendOtomatis.SEMUA[pos];
        String dir = BuatProyekHelper.buat(this, nama, "html", "kosong");
        if (dir == null || dir.isEmpty()) return;
        String galat = BackendOtomatis.terapkanBackend(new File(dir), jenis.id);
        if (galat != null) {
            toast(galat);
            return;
        }
        String ok = "Backend " + jenis.nama + " berhasil dibuat: " + nama
                + ". Buka di Kelola Project untuk menjalankannya.";
        toast(ok);
        announce(ok);
        final String dirAkhir = dir;
        new AlertDialog.Builder(this)
                .setTitle("Backend berhasil dibuat")
                .setMessage(ok + "\n\n" + BackendOtomatis.bacaSayaUntuk(jenis))
                .setPositiveButton("Buka di Editor", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent it = new Intent(BackendActivity.this, EditorActivity.class);
                            it.putExtra(EditorActivity.EXTRA_DIR, dirAkhir);
                            startActivity(it);
                        } catch (Throwable t) {
                            toast("Gagal membuka editor: " + t.getMessage());
                        }
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
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
