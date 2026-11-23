package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Offline (Tahap 3G).
 *
 * Paket alat "AI" lokal tanpa internet dan tanpa model AI sungguhan —
 * semuanya aturan sederhana di dalam HP, dan itu ditulis jujur di layar:
 * (a) perbaiki teks otomatis (kapital, tanda baca, spasi ganda),
 * (b) ringkas teks panjang (ambil kalimat yang paling penting),
 * (c) buat nama aplikasi dari deskripsi, (d) saran perbaikan nama
 * variabel yang terlalu pendek di kode proyek.
 *
 * Hasil bisa dipakai ke proyek bila pengguna setuju: tombol "Terapkan
 * teks yang diperbaiki" menulis teks hasil perbaikan sebagai entri
 * strings.xml proyek (teks_perbaikan).
 */
public class AiOfflineActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spinnerProyek;
    private EditText teksEdit;
    private TextView hasilView;
    private String teksTerakhir = "";
    private final List<String> daftarProyek = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("AI Offline"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("AI Offline"), 20);
        label(r, TeksUI.t("Alat bantu teks dan kode yang berjalan di dalam HP ")
                + TeksUI.t("(tanpa internet). Ini aturan sederhana, bukan model AI ")
                + TeksUI.t("sungguhan — hasilnya jujur dan bisa diperiksa sendiri."),
                false);

        label(r, TeksUI.t("Teks atau deskripsi:"), false);
        teksEdit = new EditText(this);
        GayaStudio.edit(teksEdit);
        teksEdit.setHint("Tulis teks, kalimat panjang, atau deskripsi aplikasi di sini.");
        teksEdit.setMinLines(3);
        r.addView(teksEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Perbaiki Teks"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = teksEdit.getText().toString();
                if (t.trim().length() == 0) {
                    Toast.makeText(AiOfflineActivity.this, TeksUI.t("Tulis teks dulu."),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                teksTerakhir = AiOffline.perbaikiTeks(t);
                tampilkan(TeksUI.t("Teks yang diperbaiki:") + "\n" + teksTerakhir,
                        TeksUI.t("Teks diperbaiki."));
            }
        });

        button(r, TeksUI.t("Ringkas Teks (3 kalimat)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = teksEdit.getText().toString();
                if (t.trim().length() == 0) {
                    Toast.makeText(AiOfflineActivity.this, TeksUI.t("Tulis teks dulu."),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                teksTerakhir = AiOffline.ringkas(t, 3);
                tampilkan(TeksUI.t("Ringkasan:") + "\n" + teksTerakhir, TeksUI.t("Teks diringkas."));
            }
        });

        button(r, TeksUI.t("Buat Nama Aplikasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = teksEdit.getText().toString();
                if (t.trim().length() == 0) {
                    Toast.makeText(AiOfflineActivity.this, TeksUI.t("Tulis deskripsi dulu."),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> nama = AiOffline.buatNama(t);
                if (nama.isEmpty()) {
                    tampilkan(TeksUI.t("Tidak ada kata kunci yang bisa dipakai dari deskripsi itu. ")
                            + TeksUI.t("Coba tulis deskripsi yang lebih jelas, misalnya ")
                            + TeksUI.t("\"aplikasi catatan harian\"."), TeksUI.t("Tidak ada nama yang dibuat."));
                    return;
                }
                StringBuilder sb = new StringBuilder(TeksUI.t("Saran nama aplikasi:") + "\n");
                for (int i = 0; i < nama.size(); i++) {
                    sb.append((i + 1)).append(". ").append(nama.get(i)).append("\n");
                }
                teksTerakhir = nama.get(0);
                tampilkan(sb.toString(), TeksUI.t("Nama aplikasi dibuat: ") + nama.get(0) + ".");
            }
        });

        label(r, TeksUI.t("Pilih proyek (untuk saran nama variabel dan terapkan):"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Saran Nama Variabel di Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saranVariabel();
            }
        });

        button(r, TeksUI.t("Terapkan Teks yang Diperbaiki ke strings.xml"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terapkan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        hasilView = new TextView(this);
        hasilView.setText("Belum ada hasil. Tulis teks, lalu pilih salah satu alat di atas.");
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

        announce(TeksUI.t("AI Offline. Tulis teks, lalu pilih alat: perbaiki teks, ringkas, ")
                + TeksUI.t("buat nama aplikasi, atau saran nama variabel."));
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private String proyekTerpilih() {
        if (spinnerProyek.getSelectedItem() == null) return null;
        String nama = String.valueOf(spinnerProyek.getSelectedItem());
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) return null;
        return nama;
    }

    private void saranVariabel() {
        String nama = proyekTerpilih();
        if (nama == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(ROOT + "/" + nama);
        String tipe = InfoProyek.tipe(dir);
        List<AiOffline.SaranNama> semua = new ArrayList<AiOffline.SaranNama>();
        kumpulKode(dir, semua, "lua".equals(tipe));
        if (semua.isEmpty()) {
            tampilkan(TeksUI.t("Tidak ada nama variabel 1-2 huruf di proyek ") + nama + ".",
                    TeksUI.t("Tidak ada saran nama variabel."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Saran nama variabel (")).append(semua.size()).append("):\n");
        for (int i = 0; i < semua.size(); i++) {
            AiOffline.SaranNama s = semua.get(i);
            sb.append(s.berkas).append(":").append(s.baris)
                    .append(" — ").append(s.saran).append("\n");
        }
        tampilkan(sb.toString(), semua.size() + TeksUI.t(" saran nama variabel ditemukan."));
    }

    private void kumpulKode(File dir, List<AiOffline.SaranNama> keluar, boolean lua) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if ("bin".equals(f.getName()) || ".git".equals(f.getName())) continue;
                kumpulKode(f, keluar, lua);
            } else {
                String n = f.getName();
                boolean cocok = lua ? n.endsWith(".lua")
                        : (n.endsWith(".java") || n.endsWith(".kt"));
                if (!cocok) continue;
                String isi = baca(f);
                if (isi == null) continue;
                List<AiOffline.SaranNama> saran = AiOffline.saranNamaVariabel(isi, lua);
                for (int j = 0; j < saran.size(); j++) {
                    AiOffline.SaranNama s = saran.get(j);
                    keluar.add(new AiOffline.SaranNama(namaRelatif(dir, f),
                            s.baris, s.nama, s.saran));
                }
            }
        }
    }

    private String namaRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    private void terapkan() {
        String nama = proyekTerpilih();
        if (nama == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (teksTerakhir.length() == 0) {
            Toast.makeText(this, TeksUI.t("Belum ada teks yang diperbaiki. Tekan Perbaiki Teks dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File strings = new File(ROOT + "/" + nama
                + "/app/src/main/res/values/strings.xml");
        if (!strings.isFile()) {
            Toast.makeText(this, TeksUI.t("strings.xml proyek tidak ditemukan."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String isi = baca(strings);
        if (isi == null) {
            Toast.makeText(this, TeksUI.t("Tidak bisa membaca strings.xml."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String entri = TeksUI.t("    <string name=\"teks_perbaikan\">")
                + lolosXml(teksTerakhir) + "</string>\n";
        String baru;
        if (isi.contains("name=\"teks_perbaikan\"")) {
            baru = isi.replaceAll(
                    "(?s)<string name=\"teks_perbaikan\">.*?</string>", entri.trim());
        } else if (isi.contains("</resources>")) {
            baru = isi.replace("</resources>", entri + "</resources>");
        } else {
            baru = isi + "\n" + entri;
        }
        if (tulis(strings, baru) == null) {
            tampilkan(TeksUI.t("Teks yang diperbaiki sudah ditulis ke strings.xml proyek ")
                    + nama + TeksUI.t(" sebagai entri teks_perbaikan."),
                    TeksUI.t("Teks diterapkan ke proyek ") + nama + ".");
        } else {
            Toast.makeText(this, TeksUI.t("Gagal menulis strings.xml."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String lolosXml(String teks) {
        return teks.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "\\\"").replace("'", "\\'");
    }

    private void tampilkan(String teks, String umumkan) {
        hasilView.setText(teks);
        Toast.makeText(this, umumkan, Toast.LENGTH_SHORT).show();
        announce(umumkan + TeksUI.t(" Hasil lengkap ada di layar."));
    }

    private String baca(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            try {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            } finally {
                r.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String tulis(File target, String isi) {
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(target), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            return null;
        } catch (Exception e) {
            return TeksUI.t("Tidak bisa menulis ") + target.getName() + ": " + e.getMessage();
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
