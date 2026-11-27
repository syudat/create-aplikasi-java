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
 * kelas dan metode di semua berkas kode dengan parser AST (bukan pola
 * teks). Isi nama simbol untuk mencari semua pemanggilnya beserta
 * berkas dan barisnya. Ganti nama metode memakai resolusi AST sehingga
 * berlaku di SEMUA berkas pemanggil yang penerimanya terbukti merujuk
 * ke kelas pemilik; metode bernama sama di kelas lain tidak disentuh.
 * Bila satu nama metode ada di beberapa kelas, tulis sebagai
 * "Kelas.metode". Blok kode bisa diekstrak menjadi fungsi baru lewat
 * tombol "Ekstrak Blok Menjadi Fungsi". Logika ada di RefaktorSemantik
 * (murni Java, sudah diuji di komputer).
 *
 * BATAS: parser AST minimal memahami struktur kelas/metode/pemanggilan;
 * refleksi dan pemanggilan lewat antarmuka yang tipenya hanya diketahui
 * saat jalan tidak dapat diresolusi — selalu periksa pratinjau sebelum
 * menerapkan.
 */
public class RefaktorSemantikActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eSimbol;
    private EditText eBaru;
    private EditText eBerkas;
    private EditText eAwal;
    private EditText eAkhir;
    private EditText eFungsiBaru;
    private TextView tvHasil;
    private Button bJalankan;
    private Button bPratinjau;
    private Button bTerapkan;
    private Button bEkstrak;

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

        label(r, TeksUI.t("Ekstrak blok menjadi fungsi:"), false);
        label(r, TeksUI.t("Berkas (jalur relatif dari proyek):"), false);
        eBerkas = new EditText(this);
        GayaStudio.edit(eBerkas);
        eBerkas.setHint(TeksUI.t("Contoh: Pembayaran.java"));
        eBerkas.setTextSize(14);
        eBerkas.setSingleLine(true);
        r.addView(eBerkas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Baris awal blok:"), false);
        eAwal = new EditText(this);
        GayaStudio.edit(eAwal);
        eAwal.setHint(TeksUI.t("Contoh: 3"));
        eAwal.setTextSize(14);
        eAwal.setSingleLine(true);
        r.addView(eAwal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Baris akhir blok:"), false);
        eAkhir = new EditText(this);
        GayaStudio.edit(eAkhir);
        eAkhir.setHint(TeksUI.t("Contoh: 5"));
        eAkhir.setTextSize(14);
        eAkhir.setSingleLine(true);
        r.addView(eAkhir, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama fungsi baru:"), false);
        eFungsiBaru = new EditText(this);
        GayaStudio.edit(eFungsiBaru);
        eFungsiBaru.setHint(TeksUI.t("Contoh: hitungTotal"));
        eFungsiBaru.setTextSize(14);
        eFungsiBaru.setSingleLine(true);
        r.addView(eFungsiBaru, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bEkstrak = button(r, TeksUI.t("Ekstrak Blok Menjadi Fungsi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ekstrakBlok();
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
        List<RefaktorSemantik.AstUnit> units = RefaktorSemantik.kumpulkanAstProyek(dir);
        List<RefaktorSemantik.Simbol> semua = RefaktorSemantik.pindaiProyek(dir);
        StringBuilder b = new StringBuilder();
        if (simbol.length() == 0) {
            b.append(TeksUI.t("Simbol ditemukan:")).append(" ").append(semua.size()).append(".");
            int tampil = Math.min(semua.size(), 20);
            for (int i = 0; i < tampil; i++) b.append("\n- ").append(semua.get(i).toString());
            if (semua.size() > tampil) b.append("\n... ").append(semua.size() - tampil).append(" lagi.");
            return b.toString();
        }
        List<RefaktorSemantik.Simbol> cocok =
                RefaktorSemantik.cariDefinisiSemuaAst(units, simbol);
        if (cocok.isEmpty()) {
            return TeksUI.t("Simbol ditemukan:") + " 0. " + simbol + " tidak ditemukan di proyek ini.";
        }
        if (cocok.size() > 1) {
            b.append(TeksUI.t("Nama simbol ada di beberapa kelas:"));
            for (int i = 0; i < cocok.size(); i++) {
                b.append("\n- ").append(cocok.get(i).toString());
            }
            b.append("\n").append(TeksUI.t("Tulis sebagai Kelas.metode untuk memilih salah satunya."));
            return b.toString();
        }
        RefaktorSemantik.Simbol target = cocok.get(0);
        List<RefaktorSemantik.Pemanggil> pemanggil =
                RefaktorSemantik.cariPemanggilAst(units, target);
        b.append(TeksUI.t("Simbol ditemukan:")).append(" 1. ").append(target.toString());
        b.append("\n").append(TeksUI.t("Pemanggil ditemukan:")).append(" ").append(pemanggil.size()).append(".");
        for (int i = 0; i < pemanggil.size(); i++) b.append("\n- ").append(pemanggil.get(i).toString());
        return b.toString();
    }

    /** Pisah "Kelas.metode" menjadi [kelas, nama]; nama polos -> ["", nama]. */
    private String[] pisahSimbol(String simbol) {
        int titik = simbol.lastIndexOf('.');
        if (titik > 0) {
            return new String[]{simbol.substring(0, titik), simbol.substring(titik + 1)};
        }
        return new String[]{"", simbol};
    }

    /**
     * Pratinjau ganti nama AST: hitung kemunculan yang benar-benar akan
     * diubah di tiap berkas (definisi + pemanggil lintas berkas yang
     * teresolusi), tanpa menulis apa pun.
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
        String[] pisah = pisahSimbol(lama);
        String nama = pisah[1];
        List<RefaktorSemantik.AstUnit> units = RefaktorSemantik.kumpulkanAstProyek(dir);
        List<RefaktorSemantik.Simbol> definisi =
                RefaktorSemantik.cariDefinisiSemuaAst(units, lama);
        if (definisi.isEmpty()) return TeksUI.t("Nama simbol tidak ditemukan di proyek ini.");
        if (definisi.size() > 1 && pisah[0].length() == 0) {
            StringBuilder amb = new StringBuilder();
            amb.append(TeksUI.t("Nama simbol ada di beberapa kelas:"));
            for (int i = 0; i < definisi.size(); i++) {
                amb.append("\n- ").append(definisi.get(i).toString());
            }
            amb.append("\n").append(TeksUI.t("Tulis sebagai Kelas.metode untuk memilih salah satunya."));
            return amb.toString();
        }
        RefaktorSemantik.HasilGantiNama h =
                RefaktorSemantik.pratinjauGantiNamaAst(dir, pisah[0], nama, baru);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Pratinjau ganti nama:")).append(" ").append(lama)
                .append(" -> ").append(baru).append(".");
        if (h.jumlah == 0) {
            b.append("\n").append(TeksUI.t("Tidak ada kemunculan di proyek ini."));
            return b.toString();
        }
        for (int i = 0; i < h.perBerkas.size(); i++) {
            b.append("\n- ").append(h.perBerkas.get(i));
        }
        b.append("\n").append(TeksUI.t("Ganti nama metode berlaku di semua berkas pemanggil."));
        return b.toString();
    }

    /** Terapkan ganti nama AST ke semua berkas pemanggil yang teresolusi. */
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
        String[] pisah = pisahSimbol(lama);
        String nama = pisah[1];
        List<RefaktorSemantik.AstUnit> units = RefaktorSemantik.kumpulkanAstProyek(dir);
        List<RefaktorSemantik.Simbol> definisi =
                RefaktorSemantik.cariDefinisiSemuaAst(units, lama);
        if (definisi.isEmpty()) return TeksUI.t("Nama simbol tidak ditemukan di proyek ini.");
        if (definisi.size() > 1 && pisah[0].length() == 0) {
            StringBuilder amb = new StringBuilder();
            amb.append(TeksUI.t("Nama simbol ada di beberapa kelas:"));
            for (int i = 0; i < definisi.size(); i++) {
                amb.append("\n- ").append(definisi.get(i).toString());
            }
            amb.append("\n").append(TeksUI.t("Tulis sebagai Kelas.metode untuk memilih salah satunya."));
            return amb.toString();
        }
        RefaktorSemantik.HasilGantiNama h =
                RefaktorSemantik.terapkanGantiNamaAst(dir, pisah[0], nama, baru);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Ganti nama diterapkan di")).append(":");
        if (h.jumlah == 0) {
            b.append("\n").append(TeksUI.t("Tidak ada kemunculan di proyek ini."));
            return b.toString();
        }
        for (int i = 0; i < h.perBerkas.size(); i++) {
            b.append("\n- ").append(h.perBerkas.get(i));
        }
        b.append("\n").append(TeksUI.t("Ganti nama metode berlaku di semua berkas pemanggil."));
        return b.toString();
    }

    /** Ekstrak blok baris dari berkas proyek menjadi fungsi baru. */
    private void ekstrakBlok() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final String berkas = eBerkas.getText().toString().trim();
        final String awalTeks = eAwal.getText().toString().trim();
        final String akhirTeks = eAkhir.getText().toString().trim();
        final String fungsiBaru = eFungsiBaru.getText().toString().trim();
        if (berkas.length() == 0 || awalTeks.length() == 0 || akhirTeks.length() == 0
                || fungsiBaru.length() == 0) {
            toast(TeksUI.t("Isi dulu berkas, baris awal, baris akhir, dan nama fungsi barunya."));
            return;
        }
        final int awal;
        final int akhir;
        try {
            awal = Integer.parseInt(awalTeks);
            akhir = Integer.parseInt(akhirTeks);
        } catch (Throwable t) {
            toast(TeksUI.t("Ekstraksi gagal. Periksa nomor barisnya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Ekstrak Blok Menjadi Fungsi") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = ekstrak(dir, berkas, awal, akhir, fungsiBaru);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "RefaktorEkstrak").start();
    }

    private String ekstrak(File dir, String berkas, int awal, int akhir, String fungsiBaru) {
        File f = new File(dir, berkas);
        if (!f.isFile()) return TeksUI.t("Berkas tidak ditemukan di proyek ini.");
        String isi = bacaTeks(f);
        if (isi == null) return TeksUI.t("Berkas tidak ditemukan di proyek ini.");
        String hasil = RefaktorSemantik.ekstrakFungsi(isi, awal, akhir, fungsiBaru);
        if (hasil == null) return TeksUI.t("Ekstraksi gagal. Periksa nomor barisnya.");
        if (!tulisTeks(f, hasil)) return TeksUI.t("Ekstraksi gagal. Periksa nomor barisnya.");
        return TeksUI.t("Blok diekstrak menjadi fungsi") + " " + fungsiBaru + " "
                + TeksUI.t("di berkas") + " " + berkas + ".";
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
