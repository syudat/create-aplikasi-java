package com.androlua;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inspektur Basis Data SQLite, Room, dan Penyimpanan Lokal Langsung
 * (Fitur 8, daftar Gemini 2026-11-27).
 *
 * Pilih proyek dan berkas basis data, tekan "Buka Basis Data" — IDE
 * membaca skema dan isi tabel pertama (maksimal 20 baris) sebagai
 * tabel teks. Tulis kueri SELECT lalu tekan "Jalankan Kueri".
 * Logika model tabel dan kueri ada di InspekturBasisData (murni Java,
 * sudah diuji di komputer).
 *
 * BATAS: pembacaan skema memakai pemindaian teks bila berkas tidak
 * bisa dibuka sebagai SQLite; pengeditan nilai di HP menulis balik
 * lewat SQLiteDatabase.
 */
public class InspekturBasisDataActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spDb;
    private ArrayAdapter<String> adapterDb;
    private final List<String> daftarDb = new ArrayList<String>();
    private EditText eKueri;
    private EditText eBaris;
    private EditText eKolom;
    private EditText eNilai;
    private TextView tvHasil;
    private Button bJalankan;
    private Button bKueri;
    private File dirAktif;
    private File berkasDb;
    private InspekturBasisData.Tabel tabelAktif;
    private int barisDiubah = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Inspektur Basis Data dan Penyimpanan"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Inspektur Basis Data dan Penyimpanan"), 20);
        label(r, TeksUI.t("Baca tabel SQLite dan Room sebagai tabel teks, ubah nilai sel untuk menguji skenario, jalankan kueri SQL, dan simpan cadangan keadaan basis data."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        spProyek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                muatDb();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih berkas basis data:"), false);
        spDb = new Spinner(this);
        adapterDb = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarDb);
        adapterDb.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDb.setAdapter(adapterDb);
        r.addView(spDb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Buka Basis Data"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        label(r, TeksUI.t("Kueri SQL:"), false);
        eKueri = new EditText(this);
        GayaStudio.edit(eKueri);
        eKueri.setHint(TeksUI.t("Contoh: SELECT nama FROM pengguna WHERE kota = 'Makassar'"));
        eKueri.setTextSize(14);
        eKueri.setSingleLine(true);
        r.addView(eKueri, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bKueri = button(r, TeksUI.t("Jalankan Kueri"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jalankanKueri();
            }
        });

        label(r, TeksUI.t("Nomor baris:"), false);
        eBaris = new EditText(this);
        GayaStudio.edit(eBaris);
        eBaris.setHint(TeksUI.t("Contoh: 1"));
        eBaris.setTextSize(14);
        eBaris.setSingleLine(true);
        r.addView(eBaris, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama kolom:"), false);
        eKolom = new EditText(this);
        GayaStudio.edit(eKolom);
        eKolom.setHint(TeksUI.t("Contoh: kota"));
        eKolom.setTextSize(14);
        eKolom.setSingleLine(true);
        r.addView(eKolom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nilai baru:"), false);
        eNilai = new EditText(this);
        GayaStudio.edit(eNilai);
        eNilai.setHint(TeksUI.t("Contoh: Maros"));
        eNilai.setTextSize(14);
        eNilai.setSingleLine(true);
        r.addView(eNilai, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Ubah Nilai Sel"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ubahNilaiSel();
            }
        });

        button(r, TeksUI.t("Simpan Cadangan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanCadangan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Buka Basis Data") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Inspektur Basis Data dan Penyimpanan") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Buka Basis Data") + ".");
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
        muatDb();
    }

    private void muatDb() {
        daftarDb.clear();
        dirAktif = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
            cariDb(dirAktif, dirAktif);
        }
        if (adapterDb != null) adapterDb.notifyDataSetChanged();
    }

    private void cariDb(File akar, File kini) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) cariDb(akar, f);
            } else if (f.getName().endsWith(".db") || f.getName().endsWith(".sqlite")
                    || f.getName().endsWith(".sqlite3")) {
                daftarDb.add(f.getAbsolutePath().substring(akar.getAbsolutePath().length() + 1));
            }
        }
    }

    private void mulai() {
        if (dirAktif == null || spDb == null || spDb.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File berkas = new File(dirAktif, String.valueOf(spDb.getSelectedItem()));
        berkasDb = berkas;
        barisDiubah = 0;
        tvHasil.setText(TeksUI.t("Buka Basis Data") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(berkas);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "InspekturBasisData").start();
    }

    private String jalankan(File berkas) {
        byte[] data = bacaBerkas(berkas);
        if (data == null) return TeksUI.t("Berkas basis data tidak bisa dibaca.");
        List<String> skema = InspekturBasisData.bacaSkema(data);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Tabel ditemukan:")).append(" ").append(skema.size()).append(".");
        if (skema.size() == 0) return b.toString();
        InspekturBasisData.Tabel tabel = InspekturBasisData.tabelDariSkema(skema.get(0));
        b.append("\n").append(TeksUI.t("Tabel:")).append(" ").append(tabel.nama);
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(berkas.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READONLY);
            try {
                Cursor c = db.rawQuery("SELECT * FROM \"" + tabel.nama + "\" LIMIT 20", null);
                try {
                    while (c.moveToNext()) {
                        List<String> baris = new ArrayList<String>();
                        for (int i = 0; i < tabel.kolom.size() && i < c.getColumnCount(); i++) {
                            baris.add(c.isNull(i) ? "" : c.getString(i));
                        }
                        tabel.baris.add(baris);
                    }
                } finally {
                    c.close();
                }
            } finally {
                db.close();
            }
        } catch (Throwable t) {
            b.append("\n").append(TeksUI.t("Isi baris tidak bisa dibaca:")).append(" ").append(t.getMessage());
        }
        tabelAktif = tabel;
        b.append("\n").append(TeksUI.t("Baris dibaca:")).append(" ").append(tabel.baris.size()).append(".");
        for (int i = 0; i < tabel.baris.size(); i++) {
            b.append("\n").append(i + 1).append(". ");
            List<String> baris = tabel.baris.get(i);
            for (int j = 0; j < baris.size(); j++) {
                if (j > 0) b.append(" | ");
                b.append(baris.get(j));
            }
        }
        return b.toString();
    }

    private void jalankanKueri() {
        if (tabelAktif == null) {
            toast(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                    + TeksUI.t("Buka Basis Data") + ".");
            return;
        }
        String sql = eKueri.getText().toString().trim();
        List<List<String>> hasil = InspekturBasisData.jalankanKueri(tabelAktif, sql);
        if (hasil == null) {
            tvHasil.setText(TeksUI.t("Kueri tidak dikenali. Contoh: SELECT nama FROM pengguna WHERE kota = 'Makassar'"));
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Hasil kueri:")).append(" ").append(hasil.size()).append(" baris.");
        for (int i = 0; i < hasil.size(); i++) {
            b.append("\n").append(i + 1).append(". ");
            List<String> baris = hasil.get(i);
            for (int j = 0; j < baris.size(); j++) {
                if (j > 0) b.append(" | ");
                b.append(baris.get(j));
            }
        }
        String teks = b.toString();
        tvHasil.setText(teks);
        announce(teks);
    }

    /** Ubah satu sel: tulis balik ke berkas .db, lalu perbarui tampilan. */
    private void ubahNilaiSel() {
        if (tabelAktif == null || berkasDb == null) {
            toast(TeksUI.t("Berkas basis data belum dibuka."));
            return;
        }
        String kolom = eKolom.getText().toString().trim();
        String nilai = eNilai.getText().toString();
        int baris;
        try {
            baris = Integer.parseInt(eBaris.getText().toString().trim()) - 1;
        } catch (Throwable t) {
            toast(TeksUI.t("Nomor baris tidak sah."));
            return;
        }
        if (kolom.length() == 0 || eNilai.getText().toString().length() == 0) {
            toast(TeksUI.t("Isi dulu nomor baris, nama kolom, dan nilai baru."));
            return;
        }
        int idx = tabelAktif.indeksKolom(kolom);
        if (idx < 0 || baris < 0 || baris >= tabelAktif.baris.size()) {
            toast(idx < 0 ? TeksUI.t("Nama kolom tidak ditemukan.")
                    : TeksUI.t("Nomor baris tidak sah."));
            return;
        }
        String sql = InspekturBasisData.sqlUbahSel(tabelAktif.nama, kolom);
        if (sql == null) {
            toast(TeksUI.t("Nama kolom tidak ditemukan."));
            return;
        }
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(berkasDb.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READWRITE);
            try {
                db.execSQL(sql, new Object[]{nilai, Integer.valueOf(baris)});
            } finally {
                db.close();
            }
        } catch (Throwable t) {
            toast(TeksUI.t("Gagal mengubah nilai sel:") + " "
                    + (t.getMessage() == null ? "" : t.getMessage()));
            return;
        }
        InspekturBasisData.ubahSel(tabelAktif, baris, idx, nilai);
        barisDiubah++;
        String hasil = tampilkanTabel();
        tvHasil.setText(hasil);
        announce(TeksUI.t("Nilai sel diubah.") + " " + hasil);
    }

    /** Simpan cadangan teks tabel aktif ke <nama-db>.cadangan.txt di proyek. */
    private void simpanCadangan() {
        if (tabelAktif == null || dirAktif == null || berkasDb == null) {
            toast(TeksUI.t("Berkas basis data belum dibuka."));
            return;
        }
        File keluar = new File(dirAktif, berkasDb.getName() + ".cadangan.txt");
        if (tulisTeks(keluar, InspekturBasisData.cadangan(tabelAktif))) {
            toast(TeksUI.t("Cadangan disimpan:") + " " + keluar.getName());
        } else {
            toast(TeksUI.t("Berkas basis data tidak bisa dibaca."));
        }
    }

    /** Tampilkan ulang tabel aktif sebagai teks bernomor baris. */
    private String tampilkanTabel() {
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Tabel:")).append(" ").append(tabelAktif.nama).append(". ");
        b.append(TeksUI.t("Baris dibaca:")).append(" ").append(tabelAktif.baris.size()).append(". ");
        b.append(TeksUI.t("Baris diubah:")).append(" ").append(barisDiubah).append(".");
        for (int i = 0; i < tabelAktif.baris.size(); i++) {
            b.append("\n").append(i + 1).append(". ");
            List<String> baris = tabelAktif.baris.get(i);
            for (int j = 0; j < baris.size(); j++) {
                if (j > 0) b.append(" | ");
                b.append(baris.get(j));
            }
        }
        return b.toString();
    }

    private boolean tulisTeks(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            out.write(isi);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private byte[] bacaBerkas(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return buf.toByteArray();
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
