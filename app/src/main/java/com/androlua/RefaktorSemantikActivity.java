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
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mesin Refactoring Semantik Lintas-Berkas dan Navigasi Pohon Sintaks AST
 * (Fitur 5, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Pindai Simbol Proyek" — IDE memindai definisi
 * kelas dan metode di semua berkas kode. Isi nama simbol untuk mencari
 * semua pemanggilnya beserta berkas dan barisnya. Logika ada di
 * RefaktorSemantik (murni Java, sudah diuji di komputer).
 *
 * BATAS: pengindeks memakai pola teks, bukan parser AST penuh — ganti
 * nama hanya menyentuh kata utuh di berkas pemilik (lihat
 * RefaktorSemantik). Selalu periksa pratinjau sebelum menerapkan.
 */
public class RefaktorSemantikActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eSimbol;
    private EditText eBaru;
    private TextView tvHasil;
    private Button bJalankan;
    private Button bPratinjau;
    private Button bTerapkan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Refactoring Semantik dan Navigasi AST"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Refactoring Semantik dan Navigasi AST"), 20);
        label(r, TeksUI.t("Lompat ke definisi, cari semua pemanggil, ganti nama simbol dengan aman lintas berkas, atau jadikan blok kode sebagai fungsi baru."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama simbol (kosongkan untuk memindai semua):"), false);
        eSimbol = new EditText(this);
        GayaStudio.edit(eSimbol);
        eSimbol.setHint(TeksUI.t("Contoh: proses (kosongkan untuk memindai semua simbol)"));
        eSimbol.setTextSize(14);
        eSimbol.setSingleLine(true);
        r.addView(eSimbol, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Pindai Simbol Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        label(r, TeksUI.t("Nama baru:"), false);
        eBaru = new EditText(this);
        GayaStudio.edit(eBaru);
        eBaru.setHint(TeksUI.t("Contoh: verifikasiPembayaran"));
        eBaru.setTextSize(14);
        eBaru.setSingleLine(true);
        r.addView(eBaru, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bPratinjau = button(r, TeksUI.t("Pratinjau Ganti Nama"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pratinjauGantiNama();
            }
        });

        bTerapkan = button(r, TeksUI.t("Terapkan Ganti Nama"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terapkanGantiNama();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Pindai Simbol Proyek") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Refactoring Semantik dan Navigasi AST") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Pindai Simbol Proyek") + ".");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private void mulai() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final String simbol = eSimbol.getText().toString().trim();
        tvHasil.setText(TeksUI.t("Pindai Simbol Proyek") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir, simbol);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "RefaktorSemantik").start();
    }

    private String jalankan(File dir, String simbol) {
        List<RefaktorSemantik.Simbol> semua = RefaktorSemantik.pindaiProyek(dir);
        StringBuilder b = new StringBuilder();
        if (simbol.length() == 0) {
            b.append(TeksUI.t("Simbol ditemukan:")).append(" ").append(semua.size()).append(".");
            int tampil = Math.min(semua.size(), 20);
            for (int i = 0; i < tampil; i++) b.append("\n- ").append(semua.get(i).toString());
            if (semua.size() > tampil) b.append("\n... ").append(semua.size() - tampil).append(" lagi.");
            return b.toString();
        }
        RefaktorSemantik.Simbol target = RefaktorSemantik.cariDefinisi(semua, simbol);
        if (target == null) {
            return TeksUI.t("Simbol ditemukan:") + " 0. " + simbol + " tidak ditemukan di proyek ini.";
        }
        List<String[]> isi = new ArrayList<String[]>();
        kumpulIsi(dir, dir, isi);
        List<RefaktorSemantik.Pemanggil> pemanggil =
                RefaktorSemantik.cariPemanggil(isi, target);
        b.append(TeksUI.t("Simbol ditemukan:")).append(" 1. ").append(target.toString());
        b.append("\n").append(TeksUI.t("Pemanggil ditemukan:")).append(" ").append(pemanggil.size()).append(".");
        for (int i = 0; i < pemanggil.size(); i++) b.append("\n- ").append(pemanggil.get(i).toString());
        return b.toString();
    }

    /**
     * Pratinjau ganti nama: hitung kemunculan kata utuh di tiap berkas
     * kode, tanpa menulis apa pun. Ganti nama kelas berlaku di semua
     * berkas; ganti nama metode/fungsi hanya di berkas pemiliknya.
     */
    private void pratinjauGantiNama() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final String lama = eSimbol.getText().toString().trim();
        final String baru = eBaru.getText().toString().trim();
        if (lama.length() == 0 || baru.length() == 0) {
            toast(TeksUI.t("Isi dulu nama simbol dan nama barunya."));
            return;
        }
        if (lama.equals(baru)) {
            toast(TeksUI.t("Nama baru sama dengan nama lama."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Pratinjau Ganti Nama") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = pratinjau(dir, lama, baru);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "RefaktorPratinjau").start();
    }

    private String pratinjau(File dir, String lama, String baru) {
        List<RefaktorSemantik.Simbol> semua = RefaktorSemantik.pindaiProyek(dir);
        RefaktorSemantik.Simbol target = RefaktorSemantik.cariDefinisi(semua, lama);
        if (target == null) return TeksUI.t("Nama simbol tidak ditemukan di proyek ini.");
        List<String[]> isi = new ArrayList<String[]>();
        kumpulIsi(dir, dir, isi);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Pratinjau ganti nama:")).append(" ").append(lama)
                .append(" -> ").append(baru).append(".");
        int total = 0;
        for (int i = 0; i < isi.size(); i++) {
            if (!"kelas".equals(target.jenis) && !isi.get(i)[0].equals(target.berkas)) continue;
            int n = RefaktorSemantik.hitungKemunculan(isi.get(i)[1], lama);
            if (n > 0) {
                total += n;
                b.append("\n- ").append(n).append(" ").append(TeksUI.t("kemunculan di"))
                        .append(" ").append(isi.get(i)[0]);
            }
        }
        if (total == 0) b.append("\n").append(TeksUI.t("Tidak ada kemunculan di proyek ini."));
        return b.toString();
    }

    /** Terapkan ganti nama ke berkas dalam cakupan (lihat pratinjau). */
    private void terapkanGantiNama() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final String lama = eSimbol.getText().toString().trim();
        final String baru = eBaru.getText().toString().trim();
        if (lama.length() == 0 || baru.length() == 0) {
            toast(TeksUI.t("Isi dulu nama simbol dan nama barunya."));
            return;
        }
        if (lama.equals(baru)) {
            toast(TeksUI.t("Nama baru sama dengan nama lama."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Terapkan Ganti Nama") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = terapkan(dir, lama, baru);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "RefaktorTerapkan").start();
    }

    private String terapkan(File dir, String lama, String baru) {
        List<RefaktorSemantik.Simbol> semua = RefaktorSemantik.pindaiProyek(dir);
        RefaktorSemantik.Simbol target = RefaktorSemantik.cariDefinisi(semua, lama);
        if (target == null) return TeksUI.t("Nama simbol tidak ditemukan di proyek ini.");
        List<String[]> isi = new ArrayList<String[]>();
        kumpulIsi(dir, dir, isi);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Ganti nama diterapkan di")).append(":");
        int jumlahBerkas = 0;
        int total = 0;
        for (int i = 0; i < isi.size(); i++) {
            if (!"kelas".equals(target.jenis) && !isi.get(i)[0].equals(target.berkas)) continue;
            int n = RefaktorSemantik.hitungKemunculan(isi.get(i)[1], lama);
            if (n == 0) continue;
            String ganti = RefaktorSemantik.gantiNama(isi.get(i)[1], lama, baru);
            File f = new File(dir, isi.get(i)[0]);
            if (!tulisTeks(f, ganti)) continue;
            jumlahBerkas++;
            total += n;
            b.append("\n- ").append(n).append(" ").append(TeksUI.t("kemunculan di"))
                    .append(" ").append(isi.get(i)[0]);
        }
        if (total == 0) b.append("\n").append(TeksUI.t("Tidak ada kemunculan di proyek ini."));
        else if (!"kelas".equals(target.jenis)) {
            b.append("\n").append(TeksUI.t("Pemanggil di berkas lain tidak ikut diubah."));
        }
        return b.toString();
    }

    private boolean tulisTeks(File f, String isi) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            try {
                out.write(isi.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void kumpulIsi(File akar, File kini, List<String[]> keluar) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) kumpulIsi(akar, f, keluar);
            } else if (RefaktorSemantik.akhirKode(f.getName())) {
                String isi = bacaTeks(f);
                if (isi == null) continue;
                String relatif = f.getAbsolutePath().substring(akar.getAbsolutePath().length() + 1);
                keluar.add(new String[]{relatif, isi});
            }
        }
    }

    private String bacaTeks(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
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
