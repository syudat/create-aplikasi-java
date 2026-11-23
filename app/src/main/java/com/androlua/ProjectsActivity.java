package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.FileProvider;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Layar Kelola Project (Projects) Create Aplikasi dalam Java murni (Tahap 2C).
 *
 * Mereplikasi persis showProjects() di main.lua: daftar proyek dari
 * Download/project (folder yang berisi init.lua), tiap baris "nama" +
 * "packagename  ·  v<appver>  ·  <Tipe>". Ketuk proyek -> EditorActivity
 * (Java murni, Tahap 2C). Tahan lama -> menu proyek (Buka / Jalankan /
 * Bangun proyek / Ekspor ZIP / Ganti Ikon (Tahap 3A) / Cadangkan /
 * Bagikan / Pratinjau ke HP Teman (Tahap 3D) / Uji Otomatis (Tahap 3E) / Hapus), sama seperti showProjectMenu() di main.lua.
 *
 * Daftar proyek dibaca lewat jembatan global daftarProyek() di main.lua
 * (runFunc, seperti buatProyek di Tahap 2B) karena readInfo() memakai
 * loadfile Lua. Jalankan / Bangun didelegasikan ke jembatan jalankanProyek()
 * / bangunProyek(); Ekspor ZIP memanggil jembatan global eksporProyekJava()
 * (eksporProyek sendiri adalah local chunk di main.lua, tidak terlihat oleh
 * runFunc). Hapus dikerjakan murni di Java (hapus rekursif).
 * Mesin Lua TIDAK disentuh: libluajava.so dan runtime Lua tetap utuh.
 *
 * Tahap 3C:
 * - Pencarian &amp; Filter: kotak pencarian (nama, tanpa memperhatikan huruf
 *   besar/kecil) + penyaring jenis (Semua/Lua/HTML/Situs Web/Java/Kotlin/
 *   Compose, dibaca dari init.lua tiap proyek lewat kolom ke-4 jembatan
 *   daftarProyek).
 * - Cadangan &amp; Pulihkan: Cadangkan membuat ZIP proyek (format sama dengan
 *   Ekspor ZIP) ke Download/backup/&lt;Nama&gt;-&lt;tanggal&gt;.zip;
 *   Pulihkan memilih cadangan lalu mengekstraknya kembali ke
 *   Download/project (memverifikasi init.lua di dalam ZIP).
 * - Bagikan &amp; Impor: Bagikan mengirim ZIP proyek lewat Intent berbagi
 *   (ACTION_SEND, application/zip, FileProvider); Impor memilih berkas ZIP
 *   proyek lalu mengekstraknya (menolak ZIP tanpa init.lua dengan pesan
 *   jelas). Bila nama proyek sudah ada, ditanya: Timpa / Nama baru / Batal.
 *
 * UI dibuat programatik (tanpa layout XML), mengikuti pola JavaHomeActivity
 * dan helper UI di main.lua (newRoot / heading / label / button).
 */
public class ProjectsActivity extends Activity {
    /** Kata kunci pencarian awal (dari perintah suara "cari proyek <nama>"). */
    public static final String EXTRA_CARI = "cari";
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final String DIR_BACKUP = Environment.getExternalStorageDirectory().getPath()
            + "/Download/backup";
    // Kunci Indonesia mentah; terjemahan diterapkan saat ditampilkan
    // (menuProyekTampil / adaptor penyaring) dan pilihan dijalankan
    // berdasarkan INDEKS, bukan membandingkan teks, supaya menu tetap
    // berfungsi di semua bahasa.
    private static final String[] MENU_PROYEK = {
        "Buka", "Jalankan", "Bangun proyek", "Ekspor ZIP", "Ganti Ikon",
        "Cadangkan", "Bagikan", "Pratinjau ke HP Teman", "Uji Otomatis", "Hapus",
        "Bangun di Terminal"
    };
    private static final String[] FILTER_LABELS = {
        "Semua", "HTML", "Situs Web", "Java", "Kotlin", "Compose", "Python"
    };
    private static final String[] FILTER_TIPE = {
        "semua", "html", "situs", "java", "kotlin", "compose", "python"
    };
    private static final int MINTA_IMPOR = 2;

    private LinearLayout root;
    private ListView lv;
    private TextView labelKosong;
    private EditText cariEdit;
    private Spinner filterSpinner;
    private ArrayAdapter<String> adapter;
    private final List<Proyek> semua = new ArrayList<Proyek>();
    private final List<Proyek> tampil = new ArrayList<Proyek>();
    private String filterTipe = "semua";

    /** Satu proyek di daftar (nama + info dari init.lua). */
    private static class Proyek {
        final String nama;
        final String paket;
        final String ver;
        final String tipe;
        Proyek(String nama, String paket, String ver, String tipe) {
            this.nama = nama;
            this.paket = paket;
            this.ver = ver;
            this.tipe = tipe;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Kelola Project"));

        root = newRoot();
        setContentView(root);
        heading(root, TeksUI.t("Kelola Project"), 20);

        // Tahap 3C (Pencarian & Filter): kotak pencarian + penyaring jenis.
        cariEdit = new EditText(this);
        GayaStudio.edit(cariEdit);
        cariEdit.setHint("Cari proyek berdasarkan nama...");
        cariEdit.setSingleLine(true);
        cariEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                terapkanFilter();
            }
        });
        root.addView(cariEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filterSpinner = new Spinner(this);
        String[] labelFilterTampil = new String[FILTER_LABELS.length];
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            labelFilterTampil[i] = TeksUI.t(FILTER_LABELS[i]);
        }
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labelFilterTampil);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < FILTER_TIPE.length) filterTipe = FILTER_TIPE[pos];
                terapkanFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(filterSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(root, TeksUI.t("Folder project: Download/project"), true);
        labelKosong = label(root, "", true);
        labelKosong.setVisibility(View.GONE);

        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, new ArrayList<String>());
        lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < tampil.size()) bukaEditor(tampil.get(pos).nama);
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < tampil.size()) tampilkanMenuProyek(tampil.get(pos).nama);
                return true;
            }
        });
        root.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        label(root, TeksUI.t("Ketuk project untuk membuka. Tahan lama untuk Jalankan, Bangun, Ekspor ZIP, Ganti Ikon, Cadangkan, Bagikan, Pratinjau ke HP Teman, Uji Otomatis, atau Hapus."), true);

        button(root, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        // Tahap 3H (Mode Suara Penuh): perintah "cari proyek <nama>"
        // membuka layar ini dengan pencarian yang sudah diisi.
        String cariAwal = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_CARI) : null;
        if (cariAwal != null && !cariAwal.isEmpty() && cariEdit != null) {
            cariEdit.setText(cariAwal);
            terapkanFilter();
        }
        announce(TeksUI.t("Kelola Project"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Muat ulang daftar bila kembali dari editor / setelah hapus,
        // seperti "Muat ulang" di onOptionsItemSelected Lua.
        if (adapter != null) muatDaftar();
        // Sama seperti onResume() di Lua: lanjutkan pemasangan APK yang
        // tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Sama seperti onCreateOptionsMenu di main.lua (layar home/projects).
        menu.add(TeksUI.t("Buat proyek"));
        menu.add(TeksUI.t("Muat ulang"));
        menu.add(TeksUI.t("Pengaturan GitHub"));
        menu.add(TeksUI.t("Izin penyimpanan"));
        menu.add(TeksUI.t("Izin pasang APK"));
        menu.add(TeksUI.t("Tentang"));
        // Tahap 3C: Pulihkan cadangan + Impor proyek.
        menu.add(TeksUI.t("Pulihkan cadangan"));
        menu.add(TeksUI.t("Impor proyek"));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        // Sama seperti onOptionsItemSelected di main.lua (layar projects).
        String t = String.valueOf(item.getTitle());
        if ((TeksUI.t("Buat proyek").equals(t) || TeksUI.t("Buat proyek").equals(t))) {
            try {
                startActivity(new Intent(this, CreateProjectActivity.class));
            } catch (Throwable e) {
                toast("Gagal membuka Buat Proyek: " + e.getMessage());
            }
        } else if (TeksUI.t("Muat ulang").equals(t)) {
            muatDaftar();
        } else if ((TeksUI.t("Pengaturan GitHub").equals(t) || TeksUI.t("Pengaturan GitHub").equals(t))) {
            GithubSettings.tampilkan(this, null);
        } else if ((TeksUI.t("Izin penyimpanan").equals(t) || TeksUI.t("Izin penyimpanan").equals(t))) {
            requestAccess();
        } else if ((TeksUI.t("Izin pasang APK").equals(t) || TeksUI.t("Izin pasang APK").equals(t))) {
            PasangHelper.mintaIzinPasang(this);
        } else if ((TeksUI.t("Tentang").equals(t) || TeksUI.t("Tentang").equals(t))) {
            try {
                startActivity(new Intent(this, AboutActivity.class));
            } catch (Throwable e) {
                toast("Gagal membuka Tentang Alat: " + e.getMessage());
            }
        } else if (TeksUI.t("Pulihkan cadangan").equals(t)) {
            pulihkanCadangan();
        } else if ((TeksUI.t("Impor proyek").equals(t) || TeksUI.t("Impor proyek").equals(t))) {
            imporProyek();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Tahap 3C (Impor): hasil pemilih berkas ZIP.
        if (requestCode == MINTA_IMPOR) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                return;
            }
            imporDariUri(data.getData());
        }
    }

    // Izin penyimpanan, sama seperti requestAccess() di main.lua /
    // JavaHomeActivity (API 30+ lewat Pengaturan, 23-29 lewat izin biasa).
    private static final int MINTA_IZIN = 1;

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent it = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(it);
            } catch (Throwable e) {
                try {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Throwable ignored) {
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                requestPermissions(new String[]{
                        "android.permission.WRITE_EXTERNAL_STORAGE",
                        "android.permission.READ_EXTERNAL_STORAGE"}, MINTA_IZIN);
            } catch (Throwable ignored) {
            }
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
        if (Build.VERSION.SDK_INT >= 28) {
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

    // ------------------------------------------------------- daftar proyek

    private void muatDaftar() {
        semua.clear();
        // Murni Java (tanpa Lua): InfoProyek.daftar membaca init.lua sebagai
        // teks dan mengembalikan format yang sama dengan jembatan Lua dulu
        // ("nama|packagename|appver|tipe" per baris).
        String hasil = "";
        try {
            hasil = InfoProyek.daftar(new File(ROOT));
        } catch (Throwable t) {
            toast("Gagal memuat daftar proyek: " + t.getMessage());
        }
        // Format: satu baris per proyek "nama|packagename|appver|tipe"
        // (proyek tanpa kolom ke-4 dianggap Java).
        if (hasil != null && !hasil.isEmpty()) {
            for (String baris : hasil.split("\n")) {
                if (baris.isEmpty()) continue;
                String[] p = baris.split("\\|", -1);
                String nama = p.length > 0 ? p[0] : "";
                String paket = p.length > 1 ? p[1] : "?";
                String ver = p.length > 2 ? p[2] : "?";
                String tipe = p.length > 3 && !p[3].isEmpty() ? p[3] : "java";
                if (nama.isEmpty()) continue;
                semua.add(new Proyek(nama, paket, ver, tipe));
            }
        }
        terapkanFilter();
    }

    // Tahap 3C (Pencarian & Filter): saring daftar menurut kata kunci
    // (nama, tanpa memperhatikan huruf besar/kecil) dan jenis proyek.
    private void terapkanFilter() {
        String kunci = "";
        if (cariEdit != null) kunci = cariEdit.getText().toString().trim().toLowerCase();
        tampil.clear();
        for (int i = 0; i < semua.size(); i++) {
            Proyek p = semua.get(i);
            if (!"semua".equals(filterTipe) && !filterTipe.equals(p.tipe)) continue;
            if (!kunci.isEmpty()
                    && p.nama.toLowerCase().indexOf(kunci) < 0) continue;
            tampil.add(p);
        }
        adapter.clear();
        for (int i = 0; i < tampil.size(); i++) {
            Proyek p = tampil.get(i);
            adapter.add(p.nama + "\n" + p.paket + "  \u00b7  v" + p.ver
                    + "  \u00b7  " + labelTipe(p.tipe));
        }
        adapter.notifyDataSetChanged();
        if (semua.isEmpty()) {
            labelKosong.setText("Belum ada project. Tekan tombol buat project untuk membuat project baru.");
            labelKosong.setVisibility(View.VISIBLE);
        } else if (tampil.isEmpty()) {
            labelKosong.setText("Tidak ada proyek yang cocok dengan pencarian atau filter.");
            labelKosong.setVisibility(View.VISIBLE);
        } else {
            labelKosong.setVisibility(View.GONE);
        }
    }

    private static String labelTipe(String tipe) {
        if ("html".equals(tipe)) return "HTML";
        if ("situs".equals(tipe)) return TeksUI.t("Situs Web");
        if ("java".equals(tipe)) return "Java";
        if ("kotlin".equals(tipe)) return "Kotlin";
        if ("compose".equals(tipe)) return "Compose";
        if ("python".equals(tipe)) return "Python";
        return tipe;
    }

    // ------------------------------------------------------- aksi proyek

    private void bukaEditor(String name) {
        try {
            Intent it = new Intent(this, EditorActivity.class);
            it.putExtra(EditorActivity.EXTRA_DIR, ROOT + "/" + name);
            startActivity(it);
            // Tahap 3F (Perekam Aksi): catat pembukaan proyek (untuk putar ulang).
            PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                    "buka", name, "");
        } catch (Throwable t) {
            toast("Gagal membuka editor: " + t.getMessage());
        }
    }

    private String[] menuProyekTampil() {
        String[] tampil = new String[MENU_PROYEK.length];
        for (int i = 0; i < MENU_PROYEK.length; i++) tampil[i] = TeksUI.t(MENU_PROYEK[i]);
        return tampil;
    }

    private void tampilkanMenuProyek(final String name) {
        announce(TeksUI.t("Menu proyek ") + name
                + TeksUI.t(". Pilihan: Buka, Jalankan, Bangun proyek, Ekspor ZIP, Ganti Ikon, Cadangkan, Bagikan, Pratinjau ke HP Teman, Uji Otomatis, Hapus, Bangun di Terminal."));
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(menuProyekTampil(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0 || which >= MENU_PROYEK.length) return;
                        String dir = ROOT + "/" + name;
                        // Pilihan dijalankan berdasarkan indeks MENU_PROYEK
                        // (urutan sama dengan menuProyekTampil), bukan teks,
                        // supaya berfungsi di semua bahasa.
                        if (which == 0) {
                            bukaEditor(name);
                        } else if (which == 1) {
                            jalankanProyek(dir);
                        } else if (which == 2) {
                            bangunProyek(dir);
                        } else if (which == 3) {
                            eksporProyek(dir);
                        } else if (which == 4) {
                            gantiIkon(dir);
                        } else if (which == 5) {
                            cadangkanProyek(name);
                        } else if (which == 6) {
                            bagikanProyek(name);
                        } else if (which == 7) {
                            pratinjauProyek(name);
                        } else if (which == 8) {
                            ujiOtomatis(name);
                        } else if (which == 9) {
                            konfirmasiHapus(name);
                        } else if (which == 10) {
                            bangunDiTerminal(dir);
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    // Tahap 3A: buka Pembuat Ikon untuk proyek ini (tanpa finish, kembali
    // ke daftar proyek bila pengguna menekan Kembali di sana).
    private void gantiIkon(String dir) {
        try {
            Intent it = new Intent(this, IconMakerActivity.class);
            it.putExtra(IconMakerActivity.EXTRA_DIR, dir);
            startActivity(it);
        } catch (Throwable t) {
            toast("Gagal membuka Pembuat Ikon: " + t.getMessage());
        }
    }

    // Tahap 3H: "Bangun di Terminal" — buka Terminal di folder proyek
    // dengan perintah bangun yang sudah diisi (PerintahBangun: gradlew
    // bila ada, gradle bila settings.gradle ada; bila tidak ada
    // keduanya, hanya cd + petunjuk jujur, tidak dijalankan otomatis).
    private void bangunDiTerminal(String dir) {
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

    // Jalankan / Bangun / Ekspor kini Java murni (Tahap 3I). Jalankan
    // membuka HtmlActivity / toast langsung; Bangun/Ekspor menampilkan
    // dialog di Activity ini sendiri, jadi Main TIDAK dibawa ke depan
    // dan Activity ini TIDAK ditutup sesudah Bangun/Ekspor (dialognya
    // harus tetap hidup supaya pengguna bisa memilih). Jalankan tetap
    // menutup Activity ini sesudahnya.
    private void jalankanProyek(String dir) {
        try {
            JalankanProyek.jalankan(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal menjalankan: " + t.getMessage());
            return;
        }
        // Tahap 3F (Perekam Aksi): catat jalankan proyek (untuk putar ulang).
        PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                "jalankan", new File(dir).getName(), "");
        finish();
    }

    private void bangunProyek(String dir) {
        try {
            BangunProyek.bangun(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal membangun: " + t.getMessage());
            return;
        }
        // Tahap 3F (Perekam Aksi): catat bangun proyek (untuk putar ulang).
        PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                "bangun", new File(dir).getName(), "");
        // Dialog bangun tampil di Activity ini — jangan tampilkanMain,
        // jangan finish (dialog harus tetap hidup).
    }

    private void eksporProyek(String dir) {
        try {
            EksporProyekJava.ekspor(this, new File(dir));
        } catch (Throwable t) {
            toast("Gagal mengekspor: " + t.getMessage());
            return;
        }
        // Tahap 3F (Perekam Aksi): catat ekspor proyek (untuk putar ulang).
        PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                "ekspor", new File(dir).getName(), "");
        // Dialog hasil tampil di Activity ini — jangan tampilkanMain,
        // jangan finish (dialog harus tetap hidup).
    }

    // Tahap 3D (Pratinjau ke HP Teman): paket pratinjau kecil (berkas utama
    // + BACA-SAYA.txt + info.txt) lalu kirim lewat Intent berbagi
    // (ACTION_SEND, application/zip, FileProvider) — bisa ke WA/Telegram.
    private void pratinjauProyek(String name) {
        File paket = Pratinjau.buatPaket(new File(ROOT + "/" + name),
                new File(ROOT));
        if (paket == null) {
            toast("Gagal membuat paket pratinjau. Periksa isi proyek.");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName(), paket);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("application/zip");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_SUBJECT, TeksUI.t("Pratinjau proyek ") + name);
            it.putExtra(Intent.EXTRA_TEXT, TeksUI.t("Pratinjau proyek \"") + name
                    + TeksUI.t("\" dari Create Aplikasi. Buka BACA-SAYA.txt di dalam ZIP ")
                    + TeksUI.t("untuk petunjuk membukanya."));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, TeksUI.t("Pratinjau ") + name + TeksUI.t(" ke HP teman")));
            announce(TeksUI.t("Paket pratinjau proyek ") + name + TeksUI.t(" siap dibagikan ke HP teman."));
        } catch (Throwable t) {
            toast("Gagal membagikan pratinjau: " + t.getMessage());
        }
    }

    // Tahap 3E (Uji Aplikasi Otomatis): 10 pemeriksaan tanpa membangun
    // APK — hasil per item LULUS/GAGAL ditampilkan di dialog yang bisa
    // di-scroll dan diumumkan ke Jieshuo.
    private void ujiOtomatis(String name) {
        java.util.List<UjiOtomatis.Hasil> hasil =
                UjiOtomatis.uji(new File(ROOT + "/" + name));
        String lap = UjiOtomatis.laporan(hasil);
        TextView tv = new TextView(this);
        tv.setText(lap);
        tv.setTextSize(14);
        tv.setTextIsSelectable(true);
        tv.setPadding(32, 16, 32, 16);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Uji Otomatis — ") + name)
                .setView(sv)
                .setPositiveButton(TeksUI.t("Tutup"), null)
                .show();
        announce(TeksUI.t("Uji otomatis proyek ") + name + ": " + UjiOtomatis.ringkasan(hasil) + ".");
    }

    // ------------------------------------------- Tahap 3C: arsip proyek

    // Cadangkan: ZIP proyek (format sama dengan Ekspor ZIP) ke
    // Download/backup/<Nama>-<tanggal>.zip.
    private void cadangkanProyek(String name) {
        File hasil = ArsipProyek.buatCadangan(new File(ROOT + "/" + name),
                new File(DIR_BACKUP));
        if (hasil != null) {
            toast("Cadangan tersimpan: " + hasil.getAbsolutePath());
            announce(TeksUI.t("Cadangan proyek ") + name + TeksUI.t(" tersimpan di ")
                    + hasil.getAbsolutePath());
            // Tahap 3F (Perekam Aksi): catat cadangkan proyek (untuk putar ulang).
            PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                    "cadangkan", name, "");
        } else {
            toast("Gagal membuat cadangan. Periksa izin penyimpanan.");
        }
    }

    // Bagikan: ZIP proyek (Ekspor ZIP) lalu kirim lewat Intent berbagi
    // (ACTION_SEND, application/zip, FileProvider) — bisa ke WA/Telegram/dll.
    private void bagikanProyek(String name) {
        String jalurZip = EksporProyek.ekspor(ROOT + "/" + name);
        if (jalurZip == null || jalurZip.isEmpty()) {
            toast("Gagal membuat ZIP proyek. Periksa izin penyimpanan.");
            return;
        }
        try {
            File zip = new File(jalurZip);
            Uri uri = FileProvider.getUriForFile(this, getPackageName(), zip);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("application/zip");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, TeksUI.t("Bagikan ") + name + ".zip"));
        } catch (Throwable t) {
            toast("Gagal membagikan: " + t.getMessage());
        }
    }

    // Pulihkan: pilih berkas cadangan di Download/backup, lalu ekstrak
    // kembali ke Download/project (memverifikasi init.lua di dalam ZIP).
    private void pulihkanCadangan() {
        final List<File> cadangan = ArsipProyek.daftarCadangan(new File(DIR_BACKUP));
        if (cadangan.isEmpty()) {
            toast("Belum ada cadangan di Download/backup.");
            return;
        }
        String[] nama = new String[cadangan.size()];
        for (int i = 0; i < cadangan.size(); i++) nama[i] = cadangan.get(i).getName();
        announce(TeksUI.t("Pulihkan cadangan. ") + cadangan.size() + TeksUI.t(" cadangan tersedia."));
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pulihkan cadangan"))
                .setItems(nama, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0 || which >= cadangan.size()) return;
                        pulihkanDariZip(cadangan.get(which));
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void pulihkanDariZip(File zip) {
        if (!ArsipProyek.zipBerisiInitLua(zip)) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Bukan cadangan proyek"))
                    .setMessage(TeksUI.t("Berkas ") + zip.getName()
                            + TeksUI.t(" bukan proyek Create Aplikasi (tidak ada berkas data proyek di dalamnya)."))
                    .setPositiveButton("OK", null)
                    .show();
            announce(TeksUI.t("Berkas ini bukan proyek Create Aplikasi, tidak ada berkas data proyek di dalamnya."));
            return;
        }
        String nama = ArsipProyek.namaProyekDariIsi(zip);
        if (nama == null || nama.isEmpty()) {
            nama = ArsipProyek.namaProyekDariBerkas(zip.getName());
        }
        imporZipKeProyek(zip, nama, "dipulihkan");
    }

    // Impor: pilih berkas ZIP proyek (dari Download/Unduhan) lewat pemilih
    // berkas sistem, lalu ekstrak ke Download/project.
    private void imporProyek() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("application/zip");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(it, TeksUI.t("Pilih ZIP proyek")),
                    MINTA_IMPOR);
        } catch (Throwable t) {
            toast("Gagal membuka pemilih berkas: " + t.getMessage());
        }
    }

    private void imporDariUri(Uri uri) {
        File sementara = new File(getCacheDir(), "impor-proyek.zip");
        String galatSalin = null;
        InputStream masuk = null;
        try {
            masuk = getContentResolver().openInputStream(uri);
            galatSalin = ArsipProyek.salinAliran(masuk, sementara);
        } catch (Throwable t) {
            galatSalin = TeksUI.t("Gagal membaca berkas: ") + t.getMessage();
        }
        if (galatSalin != null) {
            toast(galatSalin);
            return;
        }
        if (!ArsipProyek.zipBerisiInitLua(sementara)) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Bukan proyek Create Aplikasi"))
                    .setMessage(TeksUI.t("Berkas yang dipilih bukan proyek Create Aplikasi ")
                            + TeksUI.t("(tidak ada berkas data proyek di dalamnya)."))
                    .setPositiveButton("OK", null)
                    .show();
            announce(TeksUI.t("Berkas yang dipilih bukan proyek Create Aplikasi, tidak ada berkas data proyek di dalamnya."));
            sementara.delete();
            return;
        }
        String nama = ArsipProyek.namaProyekDariIsi(sementara);
        if (nama == null || nama.isEmpty()) {
            nama = namaDariUri(uri);
        }
        if (nama == null || nama.isEmpty()) nama = TeksUI.t("Proyek");
        imporZipKeProyek(sementara, nama, "diimpor");
        sementara.delete();
    }

    private String namaDariUri(Uri uri) {
        // Nama tampilan berkas dari pemilih (OpenableColumns.DISPLAY_NAME).
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                String nama = c.getString(0);
                if (nama != null && !nama.isEmpty()) {
                    return ArsipProyek.namaProyekDariBerkas(nama);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    // Ekstrak ZIP ke Download/project/<nama>, dengan dialog bila nama sudah
    // ada: Timpa / Nama baru / Batal. Dipakai Pulihkan dan Impor.
    private void imporZipKeProyek(final File zip, final String nama, final String kataKerja) {
        final File tujuan = new File(ROOT + "/" + nama);
        if (!tujuan.exists()) {
            selesaikanImpor(zip, tujuan, kataKerja);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Proyek sudah ada"))
                .setMessage(TeksUI.t("Proyek \"") + nama + TeksUI.t("\" sudah ada. Timpa proyek lama, ")
                        + TeksUI.t("atau simpan dengan nama baru?"))
                .setPositiveButton(TeksUI.t("Timpa"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ArsipProyek.hapusRekursif(tujuan);
                        selesaikanImpor(zip, tujuan, kataKerja);
                    }
                })
                .setNeutralButton(TeksUI.t("Nama baru"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mintaNamaBaru(zip, nama, kataKerja);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
        announce(TeksUI.t("Proyek ") + nama + TeksUI.t(" sudah ada. Timpa proyek lama, atau simpan dengan nama baru?"));
    }

    private void mintaNamaBaru(final File zip, String namaLama, final String kataKerja) {
        final EditText eNama = new EditText(this);
        GayaStudio.edit(eNama);
        eNama.setText(namaLama);
        eNama.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Nama baru"))
                .setMessage(TeksUI.t("Tulis nama baru untuk proyek ini:"))
                .setView(eNama)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String baru = eNama.getText().toString().trim();
                        if (!ArsipProyek.namaProyekValid(baru)) {
                            toast("Nama tidak valid. Nama tidak boleh memuat karakter / \\ : * ? \" < > |");
                            return;
                        }
                        File tujuan = new File(ROOT + "/" + baru);
                        if (tujuan.exists()) {
                            toast("Proyek \"" + baru + TeksUI.t("\" sudah ada."));
                            return;
                        }
                        selesaikanImpor(zip, tujuan, kataKerja);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void selesaikanImpor(File zip, File tujuan, String kataKerja) {
        String galat = ArsipProyek.ekstrakZip(zip, tujuan);
        if (galat != null) {
            toast(galat);
            return;
        }
        toast("Proyek \"" + tujuan.getName() + TeksUI.t("\" berhasil ") + kataKerja);
        announce(TeksUI.t("Proyek ") + tujuan.getName() + TeksUI.t(" berhasil ") + kataKerja + ".");
        muatDaftar();
    }

    private void konfirmasiHapus(final String name) {
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Hapus proyek"))
                .setMessage(TeksUI.t("Hapus proyek \"") + name
                        + TeksUI.t("\" beserta semua filenya? Tindakan ini tidak bisa dibatalkan."))
                .setPositiveButton(TeksUI.t("Hapus"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hapusRekursif(new File(ROOT + "/" + name));
                        toast("Proyek dihapus");
                        muatDaftar();
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
        announce(TeksUI.t("Hapus proyek ") + name
                + TeksUI.t(". Hapus proyek beserta semua filenya? Tindakan ini tidak bisa dibatalkan."));
    }

    private boolean hapusRekursif(File f) {
        boolean ok = true;
        if (f.isDirectory()) {
            File[] anak = f.listFiles();
            if (anak != null) {
                for (File a : anak) {
                    if (!hapusRekursif(a)) ok = false;
                }
            }
        }
        if (!f.delete()) ok = false;
        return ok;
    }
}
