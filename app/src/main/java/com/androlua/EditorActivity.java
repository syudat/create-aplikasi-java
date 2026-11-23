package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor kode proyek Create Aplikasi dalam Java murni (Tahap 2C).
 *
 * Mereplikasi persis showEditor() di main.lua: bilah berkas (tombol per
 * berkas, rekursif, disaring TIPE_EXT per jenis proyek), kotak edit teks
 * besar (monospace 13sp, multi-baris, tanpa saran, gulir horizontal), bilah
 * simbol (Tab, kurung, tanda baca, geser kursor), dan tombol Simpan /
 * Jalankan / Bangun di bawah. Alat editor khusus Lua (Perbaiki impor /
 * Perbaiki sintaks / Rapikan) DIHAPUS pada Tahap 3I — hanya berlaku untuk
 * proyek Lua, dan proyek Lua tidak lagi dibuat di aplikasi ini.
 *
 * Daftar berkas kini Java murni (DaftarBerkas, Ronde 3 — dulu jembatan
 * global daftarBerkas di main.lua). Jalankan / Bangun / Ekspor kini Java
 * murni (JalankanProyek / BangunProyek / EksporProyekJava, Tahap 3I).
 * Buka + simpan berkas dikerjakan murni di Java (baca/tulis UTF-8 +
 * verifikasi ukuran seperti saveCurrent()). Mesin Lua DIHAPUS pada
 * Ronde 3 — aplikasi ini sepenuhnya Java.
 *
 * UI dibuat programatik (tanpa layout XML), mengikuti pola JavaHomeActivity
 * dan helper UI di main.lua.
 */
public class EditorActivity extends Activity {
    public static final String EXTRA_DIR = "dir";

    // Berkas pertama bila daftar berkas kosong — sama persis dengan LEGACY
    // di showEditor() (compose tidak punya entri, jadi tidak ada fallback).
    // Ronde 3: baris Lua dihapus (proyek Lua tidak lagi dibuat/dibuka).
    private static final String[][] LEGACY = {
        {"html", "index.html"},
        {"situs", "index.html"},
        {"java", "MainActivity.java"},
        {"kotlin", "MainActivity.kt"},
        {"python", "main.py"}
    };

    private String dir;
    private String tipeProyek = "java";
    private String curFile;
    private EditText editBox;
    private LinearLayout fileRow;
    private final List<String> semuaBerkas = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dir = getIntent().getStringExtra(EXTRA_DIR);
        if (dir == null || dir.isEmpty() || !(new File(dir).isDirectory())) {
            toast("Folder proyek tidak ditemukan");
            finish();
            return;
        }
        setTitle(new File(dir).getName());

        LinearLayout r = new LinearLayout(this);
        GayaStudio.latar(r);
        r.setOrientation(LinearLayout.VERTICAL);
        setContentView(r);

        // Bilah berkas (gulir horizontal).
        HorizontalScrollView bar = new HorizontalScrollView(this);
        fileRow = new LinearLayout(this);
        fileRow.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(fileRow);
        r.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Kotak edit.
        editBox = new EditText(this);
        GayaStudio.editor(editBox);
        editBox.setGravity(Gravity.TOP);
        editBox.setTypeface(Typeface.MONOSPACE);
        // Batch pasca-v2: ukuran font editor dari Pengaturan
        // (Kecil 11 / Sedang 13 / Besar 16; bawaan 13 seperti dulu).
        editBox.setTextSize(PengaturanActivity.ukuranFontEditor(this));
        editBox.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editBox.setHorizontallyScrolling(true);
        r.addView(editBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bilah simbol (seperti AndroLua): sisip tanda baca di posisi kursor.
        HorizontalScrollView symBar = new HorizontalScrollView(this);
        LinearLayout symRow = new LinearLayout(this);
        symRow.setOrientation(LinearLayout.HORIZONTAL);
        symBar.addView(symRow);
        tambahSimbol(symRow, "Tab", null, "  ");
        tambahSimbol(symRow, "( )", Integer.valueOf(1), "()");
        tambahSimbol(symRow, "[ ]", Integer.valueOf(1), "[]");
        tambahSimbol(symRow, "{ }", Integer.valueOf(1), "{}");
        tambahSimbol(symRow, "\" \"", Integer.valueOf(1), "\"\"");
        tambahSimbol(symRow, "' '", Integer.valueOf(1), "''");
        tambahSimbol(symRow, "<-", null, null);
        tambahSimbol(symRow, "->", null, null);
        for (String c : new String[]{"=", ".", ",", ":", ";", "_", "+", "-", "*",
                "/", "\\", "%", "#", "^", "$", "?", "<", ">", "!", "|", "&",
                "~", "@", "`"}) {
            tambahSimbol(symRow, c, null, c);
        }
        r.addView(symBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Daftar berkas.
        if (!muatBerkas()) {
            finish();
            return;
        }

        // Tombol aksi di bawah.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        tambahAksi(bottom, TeksUI.t("Simpan"));
        tambahAksi(bottom, TeksUI.t("Jalankan"));
        tambahAksi(bottom, TeksUI.t("Bangun"));
        r.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Buka berkas pertama (atau fallback LEGACY bila daftar kosong).
        String awal = semuaBerkas.isEmpty() ? null : semuaBerkas.get(0);
        if (awal == null) {
            for (String[] leg : LEGACY) {
                if (leg[0].equals(tipeProyek)
                        && new File(dir + "/" + leg[1]).exists()) {
                    awal = leg[1];
                    break;
                }
            }
        }
        if (awal != null) bukaBerkasTanpaSimpan(awal);
        announce(new File(dir).getName());
    }

    @Override
    public void onBackPressed() {
        // Sama seperti onKeyDown Lua (editor): simpan lalu kembali ke daftar.
        simpanSaatIni();
        finish();
    }

    // ------------------------------------------------------------------ UI

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

    // ------------------------------------------------------- daftar berkas

    // Mengisi fileRow + semuaBerkas dari DaftarBerkas Java murni (Ronde 3).
    // Baris 1 hasil = tipe proyek, baris 2.. = jalur relatif berkas.
    private boolean muatBerkas() {
        String hasil = "";
        try {
            hasil = DaftarBerkas.daftar(new File(dir));
        } catch (Throwable t) {
            toast("Gagal memuat daftar berkas: " + t.getMessage());
        }
        String[] baris = (hasil == null || hasil.isEmpty())
                ? new String[0] : hasil.split("\n");
        if (baris.length > 0) tipeProyek = baris[0];
        semuaBerkas.clear();
        for (int i = 1; i < baris.length; i++) {
            if (baris[i].isEmpty()) continue;
            final String nama = baris[i];
            semuaBerkas.add(nama);
            Button b = new Button(this);
            GayaStudio.tombol(b);
            b.setText(nama);
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    simpanSaatIni();
                    bukaBerkasTanpaSimpan(nama);
                }
            });
            fileRow.addView(b);
        }
        return true;
    }

    // ------------------------------------------------------- buka + simpan

    private byte[] bacaBytes(File f) {
        try {
            InputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            in.close();
            return out.toByteArray();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void bukaBerkasTanpaSimpan(String nama) {
        curFile = nama;
        byte[] data = bacaBytes(new File(dir + "/" + nama));
        if (data == null) data = new byte[0];
        String teks = new String(data, Charset.forName("UTF-8"));
        editBox.setText(teks);
        setTitle(new File(dir).getName() + " / " + nama + " (" + data.length + TeksUI.t(" byte)"));
    }

    // Sama seperti saveCurrent() di main.lua: tulis, lalu verifikasi ukuran
    // file di disk sama dengan teks di editor. Mengembalikan false bila gagal.
    private boolean simpanSaatIni() {
        if (dir == null || curFile == null || editBox == null) return true;
        String teks = editBox.getText().toString();
        byte[] data;
        try {
            data = teks.getBytes("UTF-8");
        } catch (Throwable t) {
            toast("Gagal menyimpan: " + t.getMessage());
            return false;
        }
        try {
            FileOutputStream out = new FileOutputStream(new File(dir + "/" + curFile));
            out.write(data);
            out.close();
        } catch (Throwable t) {
            toast("Gagal menyimpan: " + t.getMessage());
            return false;
        }
        byte[] disk = bacaBytes(new File(dir + "/" + curFile));
        int diDisk = disk == null ? 0 : disk.length;
        if (disk == null || diDisk != data.length) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Peringatan: file tidak utuh"))
                    .setMessage(curFile + TeksUI.t(" seharusnya ") + data.length + " byte, tetapi tersimpan "
                            + diDisk + TeksUI.t(" byte. Coba simpan lagi atau pecah skrip menjadi ")
                            + TeksUI.t("beberapa file yang lebih kecil."))
                    .setPositiveButton("OK", null).show();
            return false;
        }
        // Tahap 3F (Perekam Aksi): catat simpan berkas (untuk putar ulang).
        PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                "simpan", new File(dir).getName(), curFile);
        return true;
    }

    // ------------------------------------------------------- bilah simbol

    private void sisipTeks(String t, int back) {
        int a = editBox.getSelectionStart();
        int b = editBox.getSelectionEnd();
        if (a < 0) {
            a = editBox.length();
            b = a;
        }
        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }
        editBox.getText().replace(a, b, t);
        editBox.setSelection(a + t.length() - back);
    }

    private void geserKursor(int d) {
        int a = editBox.getSelectionStart() + d;
        if (a < 0) a = 0;
        if (a > editBox.length()) a = editBox.length();
        editBox.setSelection(a);
    }

    private void tambahSimbol(LinearLayout row, final String label,
                              final Integer back, final String teks) {
        Button b = new Button(this);
        GayaStudio.tombol(b);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ("<-".equals(label)) {
                    geserKursor(-1);
                } else if ("->".equals(label)) {
                    geserKursor(1);
                } else if ("Tab".equals(label)) {
                    sisipTeks("  ", 0);
                } else {
                    sisipTeks(teks, back == null ? 0 : back.intValue());
                }
            }
        });
        row.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
    }

    // ------------------------------------------------------- aksi bawah

    private void tambahAksi(LinearLayout bottom, final String teksTombol) {
        Button b = new Button(this);
        GayaStudio.tombol(b);
        b.setText(teksTombol);
        b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((TeksUI.t("Simpan").equals(teksTombol) || TeksUI.t("Simpan").equals(teksTombol))) {
                    if (simpanSaatIni()) toast(TeksUI.t("Tersimpan"));
                } else if (TeksUI.t("Jalankan").equals(teksTombol)) {
                    jalankan();
                } else if (TeksUI.t("Bangun").equals(teksTombol)) {
                    bangun();
                }
            }
        });
        bottom.addView(b, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    // Jalankan / Bangun kini Java murni (Tahap 3I). Jalankan membuka
    // Activity baru (pratinjau proyek) di atas; setelah itu Activity ini
    // ditutup. Bangun/Ekspor menampilkan dialog di Activity ini sendiri,
    // jadi Main TIDAK dibawa ke depan dan Activity ini TIDAK ditutup
    // (dialognya harus tetap hidup).
    private void jalankan() {
        if (!simpanSaatIni()) return;
        try {
            JalankanProyek.jalankan(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal menjalankan: " + t.getMessage());
            return;
        }
        finish();
    }

    private void bangun() {
        if (!simpanSaatIni()) return;
        try {
            BangunProyek.bangun(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal membangun: " + t.getMessage());
            return;
        }
        // Dialog pilih cara bangun tampil di Activity ini — jangan
        // tampilkanMain, jangan finish (dialog harus tetap hidup).
    }

    // Tahap 3H: "Bangun di Terminal" — simpan dulu, lalu buka Terminal
    // di folder proyek dengan perintah bangun yang sudah diisi.
    private void bangunDiTerminal() {
        if (!simpanSaatIni()) return;
        PerintahBangun.Hasil hb = PerintahBangun.untuk(new File(dir));
        try {
            Intent it = new Intent(this, TerminalActivity.class);
            it.putExtra(TerminalActivity.EXTRA_FOLDER, dir);
            it.putExtra(TerminalActivity.EXTRA_PERINTAH, hb.perintah);
            it.putExtra(TerminalActivity.EXTRA_JALANKAN, hb.adaGradle);
            startActivity(it);
            if (!hb.adaGradle) toast(hb.petunjuk);
        } catch (Throwable t) {
            toast("Gagal membuka Terminal: " + t.getMessage());
        }
    }

    // Ekspor ZIP kini Java murni (Tahap 3I). Dialog hasil tampil di
    // Activity ini sendiri — jangan tampilkanMain, jangan finish.
    private void ekspor() {
        if (!simpanSaatIni()) return;
        try {
            EksporProyekJava.ekspor(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal mengekspor: " + t.getMessage());
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sama seperti onResume() di Lua: lanjutkan pemasangan APK yang
        // tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Sama seperti onCreateOptionsMenu di main.lua (layar editor).
        menu.add(TeksUI.t("Simpan"));
        menu.add(TeksUI.t("Jalankan"));
        menu.add(TeksUI.t("Bangun proyek"));
        menu.add(TeksUI.t("Bangun di Terminal"));
        menu.add(TeksUI.t("Ekspor ZIP"));
        menu.add(TeksUI.t("Pengaturan GitHub"));
        menu.add(TeksUI.t("Tutup proyek"));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        // Sama seperti onOptionsItemSelected di main.lua (layar editor).
        String t = String.valueOf(item.getTitle());
        if ((TeksUI.t("Simpan").equals(t) || TeksUI.t("Simpan").equals(t))) {
            if (simpanSaatIni()) toast(TeksUI.t("Tersimpan"));
        } else if (TeksUI.t("Jalankan").equals(t)) {
            jalankan();
        } else if ((TeksUI.t("Bangun proyek").equals(t) || TeksUI.t("Bangun proyek").equals(t))) {
            bangun();
        } else if ((TeksUI.t("Bangun di Terminal").equals(t) || TeksUI.t("Bangun di Terminal").equals(t))) {
            bangunDiTerminal();
        } else if (TeksUI.t("Ekspor ZIP").equals(t)) {
            ekspor();
        } else if ((TeksUI.t("Pengaturan GitHub").equals(t) || TeksUI.t("Pengaturan GitHub").equals(t))) {
            GithubSettings.tampilkan(this, null);
        } else if ((TeksUI.t("Tutup proyek").equals(t) || TeksUI.t("Tutup proyek").equals(t))) {
            simpanSaatIni();
            finish();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
