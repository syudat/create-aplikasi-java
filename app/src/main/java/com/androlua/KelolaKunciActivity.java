package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Kelola Kunci API (perintah al kautsar 2026-11-24 13:16 WITA).
 *
 * Daftar kunci tersimpan (nama + provider + 4 huruf terakhir saja —
 * nilai kunci TIDAK PERNAH ditampilkan penuh), tombol "Tambah API Key
 * Baru" membuka form dua kolom (nama kunci + kotak edit kunci) dengan
 * tombol Batal dan Simpan. Sesudah Simpan berhasil, layar ini langsung
 * ditutup dan kembali ke Pengaturan AI.
 *
 * Kunci disimpan LOKAL di HP saja (SharedPreferences privat "kunci_api",
 * lewat KunciApi), tidak pernah dikirim ke mana pun kecuali ke API
 * provider yang dipilih, tidak pernah dicatat di log. Beberapa kunci
 * per provider didukung (kunci cadangan): bila satu kunci gagal, coba
 * kunci berikutnya (KunciApi.kunciBerikutnya).
 *
 * Ketuk satu kunci untuk mengubah nilainya; tekan lama untuk menghapus.
 */
public class KelolaKunciActivity extends Activity {

    private String kodeBahasa;
    private ListView lvKunci;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<String>();
    private final List<String> namaBaris = new ArrayList<String>();

    private String t(String kunci) {
        return TeksUI.teks(kodeBahasa, kunci);
    }

    /** Penyimpanan kunci (SharedPreferences privat "kunci_api"). */
    private KunciApi.Penyimpanan penyimpananKunci() {
        final SharedPreferences pref = getSharedPreferences(
                "kunci_api", Context.MODE_PRIVATE);
        return new KunciApi.Penyimpanan() {
            @Override
            public String baca(String kunci, String bawaan) {
                return pref.getString(kunci, bawaan);
            }

            @Override
            public void tulis(String kunci, String nilai) {
                pref.edit().putString(kunci, nilai).apply();
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kodeBahasa = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;
        setTitle(t("Kelola Kunci API"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, t("Kelola Kunci API"), 20);
        label(r, t("Kunci API hanya tersimpan di HP ini"), true);

        lvKunci = new ListView(this);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, baris);
        lvKunci.setAdapter(adapter);
        r.addView(lvKunci, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        lvKunci.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (position < 0 || position >= namaBaris.size()) return;
                tampilkanFormUbah(namaBaris.get(position));
            }
        });
        lvKunci.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (position < 0 || position >= namaBaris.size()) return false;
                tampilkanHapus(namaBaris.get(position));
                return true;
            }
        });

        button(r, t("Tambah API Key Baru"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanFormTambah();
            }
        });

        muatDaftar();
    }

    /** Muat ulang daftar kunci ke ListView. */
    private void muatDaftar() {
        baris.clear();
        namaBaris.clear();
        List<KunciApi.Entri> semua = KunciApi.daftar(penyimpananKunci());
        if (semua.isEmpty()) {
            baris.add(t("Belum ada kunci API"));
        } else {
            for (int i = 0; i < semua.size(); i++) {
                KunciApi.Entri e = semua.get(i);
                PenyediaAi.Provider p = PenyediaAi.dariKode(e.provider);
                String namaProvider = p == null ? e.provider : p.nama;
                baris.add(e.nama + " (" + namaProvider + ") "
                        + KunciApi.tampilTersamar(e.kunci));
                namaBaris.add(e.nama);
            }
        }
        adapter.notifyDataSetChanged();
    }

    /** Form tambah kunci baru (dua kolom + Batal + Simpan). */
    private void tampilkanFormTambah() {
        LinearLayout isi = new LinearLayout(this);
        isi.setOrientation(LinearLayout.VERTICAL);
        isi.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView judul = new TextView(this);
        judul.setText(t("Tambah API Key Baru"));
        GayaStudio.judul(judul, 17);
        isi.addView(judul);

        TextView lProvider = new TextView(this);
        lProvider.setText(t("Provider:"));
        GayaStudio.label(lProvider, 14);
        isi.addView(lProvider);

        final Spinner spProvider = new Spinner(this);
        List<String> namaProvider = new ArrayList<String>();
        for (int i = 0; i < PenyediaAi.SEMUA.length; i++) {
            namaProvider.add(PenyediaAi.SEMUA[i].nama);
        }
        ArrayAdapter<String> adProvider = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, namaProvider);
        adProvider.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spProvider.setAdapter(adProvider);
        String providerAktif = PengaturanAiActivity.providerTerpilih(this);
        for (int i = 0; i < PenyediaAi.SEMUA.length; i++) {
            if (PenyediaAi.SEMUA[i].kode.equals(providerAktif)) {
                spProvider.setSelection(i);
                break;
            }
        }
        isi.addView(spProvider);

        TextView lNama = new TextView(this);
        lNama.setText(t("Nama API Key"));
        GayaStudio.label(lNama, 14);
        isi.addView(lNama);
        final EditText eNama = new EditText(this);
        eNama.setHint(t("Contoh: Kunci Gemini utama"));
        GayaStudio.edit(eNama);
        isi.addView(eNama);

        TextView lKunci = new TextView(this);
        lKunci.setText(t("Kunci API"));
        GayaStudio.label(lKunci, 14);
        isi.addView(lKunci);
        final EditText eKunci = new EditText(this);
        eKunci.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        GayaStudio.edit(eKunci);
        isi.addView(eKunci);

        Button bTempel = new Button(this);
        bTempel.setText(t("Tempel kunci"));
        GayaStudio.tombol(bTempel);
        bTempel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = cm == null ? null : cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0
                            && clip.getItemAt(0).getText() != null) {
                        eKunci.setText(clip.getItemAt(0).getText().toString().trim());
                        Toast.makeText(KelolaKunciActivity.this,
                                t("Kunci ditempel dari papan klip"),
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        isi.addView(bTempel);

        LinearLayout barisTombol = new LinearLayout(this);
        barisTombol.setOrientation(LinearLayout.HORIZONTAL);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(isi)
                .setCancelable(true)
                .create();
        Button bBatal = new Button(this);
        bBatal.setText(t("Batal"));
        GayaStudio.tombol(bBatal);
        bBatal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        barisTombol.addView(bBatal, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button bSimpan = new Button(this);
        bSimpan.setText(t("Simpan"));
        GayaStudio.tombol(bSimpan);
        bSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nama = eNama.getText().toString().trim();
                String kunci = eKunci.getText().toString().trim();
                String provider = PenyediaAi.SEMUA[
                        spProvider.getSelectedItemPosition()].kode;
                if (nama.length() == 0 || kunci.length() == 0) {
                    Toast.makeText(KelolaKunciActivity.this,
                            t("Nama dan kunci tidak boleh kosong"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String galat = KunciApi.tambah(penyimpananKunci(),
                        nama, provider, kunci);
                if (galat != null) {
                    Toast.makeText(KelolaKunciActivity.this,
                            galat, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(KelolaKunciActivity.this,
                        t("Kunci tersimpan"), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // Kembali LANGSUNG ke layar Pengaturan AI.
                finish();
            }
        });
        barisTombol.addView(bSimpan, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        isi.addView(barisTombol);

        dialog.show();
    }

    /** Form ubah nilai kunci yang sudah ada (nama tetap, kunci bisa diganti). */
    private void tampilkanFormUbah(final String nama) {
        final KunciApi.Entri entri = KunciApi.cari(penyimpananKunci(), nama);
        if (entri == null) return;
        PenyediaAi.Provider p = PenyediaAi.dariKode(entri.provider);
        String namaProvider = p == null ? entri.provider : p.nama;

        LinearLayout isi = new LinearLayout(this);
        isi.setOrientation(LinearLayout.VERTICAL);
        isi.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView judul = new TextView(this);
        judul.setText(nama);
        GayaStudio.judul(judul, 17);
        isi.addView(judul);

        TextView lProvider = new TextView(this);
        lProvider.setText(t("Provider:") + " " + namaProvider);
        GayaStudio.label(lProvider, 14);
        isi.addView(lProvider);

        TextView lKunciTerakhir = new TextView(this);
        lKunciTerakhir.setText(t("Kunci terakhir:")
                + " " + KunciApi.tampilTersamar(entri.kunci));
        GayaStudio.label(lKunciTerakhir, 14);
        isi.addView(lKunciTerakhir);

        TextView lKunci = new TextView(this);
        lKunci.setText(t("Kunci API"));
        GayaStudio.label(lKunci, 14);
        isi.addView(lKunci);
        final EditText eKunci = new EditText(this);
        eKunci.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        GayaStudio.edit(eKunci);
        isi.addView(eKunci);

        LinearLayout barisTombol = new LinearLayout(this);
        barisTombol.setOrientation(LinearLayout.HORIZONTAL);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(isi)
                .setCancelable(true)
                .create();
        Button bBatal = new Button(this);
        bBatal.setText(t("Batal"));
        GayaStudio.tombol(bBatal);
        bBatal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        barisTombol.addView(bBatal, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button bSimpan = new Button(this);
        bSimpan.setText(t("Simpan"));
        GayaStudio.tombol(bSimpan);
        bSimpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String kunci = eKunci.getText().toString().trim();
                if (kunci.length() == 0) {
                    Toast.makeText(KelolaKunciActivity.this,
                            t("Nama dan kunci tidak boleh kosong"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String galat = KunciApi.ubahNilai(penyimpananKunci(),
                        nama, kunci);
                if (galat != null) {
                    Toast.makeText(KelolaKunciActivity.this,
                            galat, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(KelolaKunciActivity.this,
                        t("Kunci tersimpan"), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                muatDaftar();
            }
        });
        barisTombol.addView(bSimpan, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        isi.addView(barisTombol);

        dialog.show();
    }

    /** Konfirmasi hapus satu kunci (tekan lama pada daftar). */
    private void tampilkanHapus(final String nama) {
        new AlertDialog.Builder(this)
                .setTitle(t("Hapus kunci ini?"))
                .setMessage(t("Kunci ini akan dihapus dari HP. Lanjutkan?"))
                .setPositiveButton(t("Ya"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (KunciApi.hapus(penyimpananKunci(), nama)) {
                            Toast.makeText(KelolaKunciActivity.this,
                                    t("Kunci dihapus"), Toast.LENGTH_SHORT).show();
                            muatDaftar();
                        }
                    }
                })
                .setNegativeButton(t("Tidak"), null)
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

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
