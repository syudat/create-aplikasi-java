package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

/**
 * Layar Pengaturan Create Aplikasi dalam Java murni (Batch pasca-v2).
 *
 * Dibuka dari menu Opsi di JavaHomeActivity ("Pengaturan"). Semua nilai
 * disimpan di SharedPreferences "pengaturan" (MODE_PRIVATE) dengan kunci
 * yang didefinisikan di bawah — kunci dan nilai bawaan dipakai juga oleh
 * layar lain (CreateProjectActivity membaca Build, EditorActivity membaca
 * ukuran font, ModeSuaraActivity membaca kecepatan suara, DonasiActivity
 * membaca Profil Pengembang lewat InfoPengembang).
 *
 * JUJUR: tidak semua pengaturan langsung diterapkan. Yang hanya tersimpan
 * (Bahasa, Bunyi Build, Cadangan Otomatis, Folder Proyek) ditulis
 * keterangannya di layar — tersimpan untuk pembaruan berikutnya, bukan
 * dipalsukan seolah sudah jalan. Yang sudah diterapkan: GitHub Actions
 * (membuka dialog GithubSettings), Tanda Tangan (membuka KeystoreActivity),
 * Kecepatan Suara (dipakai ModeSuaraActivity), Ukuran Font Editor (dipakai
 * EditorActivity), Build (dipakai CreateProjectActivity sebagai nilai awal),
 * Profil Pengembang (dipakai DonasiActivity), Reset/Hapus Data (aksi nyata).
 *
 * UI dibuat programatik (tanpa layout XML), mengikuti pola JavaHomeActivity
 * dan helper GayaStudio (tema terang ala Android Studio, Tahap 3J).
 */
public class PengaturanActivity extends Activity {
    public static final String NAMA_PREF = "pengaturan";

    public static final String KUNCI_BAHASA = "bahasa";
    public static final String KUNCI_KECEPATAN_SUARA = "kecepatan_suara";
    public static final String KUNCI_FONT_EDITOR = "ukuran_font_editor";
    public static final String KUNCI_BUILD_TARGET = "build_target_sdk";
    public static final String KUNCI_BUILD_PISAH = "build_pisah_default";
    public static final String KUNCI_BUNYI_BUILD = "bunyi_build_selesai";
    public static final String KUNCI_CADANGAN = "cadangan_otomatis";
    public static final String KUNCI_FOLDER_PROYEK = "folder_proyek";
    public static final String KUNCI_PROFIL_NAMA = "profil_nama";
    public static final String KUNCI_PROFIL_WA = "profil_wa";
    public static final String KUNCI_PROFIL_REKENING = "profil_rekening";

    // Reorganisasi pengaturan 2026-11-17 (kategori GitHub / Bangun / Tema).
    /** GitHub: enkripsi keamanan aplikasi saat build (bawaan: aktif). */
    public static final String KUNCI_GITHUB_KEAMANAN = "github_keamanan";
    /** GitHub: tanda tangan pribadi sebagai bawaan proyek baru. */
    public static final String KUNCI_GITHUB_TANDA_TANGAN = "github_tanda_tangan_pribadi";
    /** GitHub: simpan build yang berhasil ke history. */
    public static final String KUNCI_GITHUB_HISTORY = "github_simpan_history";
    /** Bangun perangkat: simpan kata sandi keystore (bawaan: mati). */
    public static final String KUNCI_BANGUN_SIMPAN_SANDI = "bangun_simpan_kata_sandi";
    /** Bangun perangkat: simpan build yang berhasil ke history. */
    public static final String KUNCI_BANGUN_HISTORY = "bangun_simpan_history";
    /** Tema aplikasi: id tema aksen (biru/hijau/ungu/merah/oranye). */
    public static final String KUNCI_TEMA = "tema_aplikasi";
    /** Anti tamper aplikasi hasil build (bawaan: mati). Diatur di kategori Tanda Tangan. */
    public static final String KUNCI_ANTI_TAMPER = "anti_tamper";

    /** Keamanan GitHub aktif? (bawaan true = skrip Lua hasil build dienkripsi). */
    public static boolean keamananGithub(Context ctx) {
        try {
            return baca(ctx).getBoolean(KUNCI_GITHUB_KEAMANAN, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Tanda tangan pribadi sebagai bawaan proyek baru? (bawaan false). */
    public static boolean tandaTanganPribadiBawaan(Context ctx) {
        try {
            return baca(ctx).getBoolean(KUNCI_GITHUB_TANDA_TANGAN, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Simpan build GitHub yang berhasil ke history? (bawaan true). */
    public static boolean simpanHistoryGithub(Context ctx) {
        try {
            return baca(ctx).getBoolean(KUNCI_GITHUB_HISTORY, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Simpan kata sandi keystore saat menandatangani di perangkat? (bawaan false). */
    public static boolean simpanKataSandiBangun(Context ctx) {
        try {
            return baca(ctx).getBoolean(KUNCI_BANGUN_SIMPAN_SANDI, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Simpan build perangkat yang berhasil ke history? (bawaan true). */
    public static boolean simpanHistoryBangun(Context ctx) {
        try {
            return baca(ctx).getBoolean(KUNCI_BANGUN_HISTORY, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Anti tamper sebagai bawaan build? (bawaan false). Nilai ini bawaan
     * umum; setelan per proyek di settings.gradle proyek menimpanya bila
     * proyek itu punya setelan antiTamper sendiri.
     */
    public static boolean antiTamperBawaan(Context ctx) {
        try {
            return baca(ctx).getBoolean(KUNCI_ANTI_TAMPER, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Id tema aplikasi saat ini (bawaan "biru"). */
    public static String temaAplikasi(Context ctx) {
        try {
            String v = baca(ctx).getString(KUNCI_TEMA, "biru");
            return v == null || v.isEmpty() ? "biru" : v;
        } catch (Throwable ignored) {
            return "biru";
        }
    }

    // ------------------------------------------------- kata sandi keystore
    // Disimpan di SharedPreferences "pengaturan" (penyimpanan privat aplikasi,
    // hanya aplikasi ini yang bisa membaca). Dipakai bila centang "Simpan kata
    // sandi" di Pengaturan Bangun APK Perangkat aktif. Kata sandi hanya dipakai
    // bila centang itu aktif; bila dimatikan, kata sandi tersimpan tidak dipakai.

    private static String kunciSandiKeystore(String nama) {
        return "sandi_keystore_" + (nama == null ? "" : nama);
    }

    /** Simpan kata sandi untuk satu keystore (dipanggil saat menandatangani). */
    public static void simpanKataSandiKeystore(Context ctx, String nama, String sandi) {
        try {
            if (sandi == null || sandi.isEmpty()) return;
            baca(ctx).edit().putString(kunciSandiKeystore(nama), sandi).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Baca kata sandi tersimpan untuk satu keystore ("" bila tidak ada). */
    public static String bacaKataSandiKeystore(Context ctx, String nama) {
        try {
            String v = baca(ctx).getString(kunciSandiKeystore(nama), "");
            return v == null ? "" : v;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Hapus kata sandi tersimpan untuk satu keystore. */
    public static void hapusKataSandiKeystore(Context ctx, String nama) {
        try {
            baca(ctx).edit().remove(kunciSandiKeystore(nama)).apply();
        } catch (Throwable ignored) {
        }
    }

    public static final String FOLDER_PROYEK_BAWAAN =
            Environment.getExternalStorageDirectory().getPath() + "/Download/project";

    /** Baca SharedPreferences "pengaturan" (dipakai layar lain). */
    public static SharedPreferences baca(Context ctx) {
        return ctx.getSharedPreferences(NAMA_PREF, Context.MODE_PRIVATE);
    }

    /** Kecepatan suara Mode Suara (0.5–2.0; bawaan 1.0). */
    public static float kecepatanSuara(Context ctx) {
        try {
            float v = baca(ctx).getFloat(KUNCI_KECEPATAN_SUARA, 1.0f);
            if (v < 0.5f) return 0.5f;
            if (v > 2.0f) return 2.0f;
            return v;
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    /** Ukuran font editor dalam sp: Kecil 11, Sedang 13, Besar 16. */
    public static int ukuranFontEditor(Context ctx) {
        try {
            String v = baca(ctx).getString(KUNCI_FONT_EDITOR, "sedang");
            if ("kecil".equals(v)) return 11;
            if ("besar".equals(v)) return 16;
            return 13;
        } catch (Throwable ignored) {
            return 13;
        }
    }

    private SharedPreferences pref;
    private LinearLayout root;

    private TextView nilaiBahasa;
    private TextView nilaiSuara;
    private TextView nilaiFont;
    private TextView nilaiCadangan;
    private TextView nilaiFolder;
    private EditText eTarget;
    private EditText eNamaProfil;
    private EditText eWaProfil;
    private EditText eRekeningProfil;
    private EditText eTokenGithub;

    /** Kategori yang sedang dibuka; null = menu kategori. */
    private String kategoriAktif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Pengaturan");
        pref = getSharedPreferences(NAMA_PREF, MODE_PRIVATE);
        // Terapkan tema aplikasi yang tersimpan (reorganisasi pengaturan 2026-11-17).
        PaletStudio.terapkanTema(temaAplikasi(this));
        tampilkanMenuKategori();
    }

    @Override
    public void onBackPressed() {
        // Di dalam kategori: tombol kembali perangkat kembali ke menu
        // kategori (bukan keluar / bukan ke menu utama). Di menu kategori:
        // keluar seperti biasa.
        if (kategoriAktif != null) {
            tampilkanMenuKategori();
            return;
        }
        super.onBackPressed();
    }

    /** Kosongkan acuan ke tampilan bagian (supaya muatUlangNilai tidak menyentuh tampilan lama). */
    private void resetFieldTampilan() {
        nilaiBahasa = null;
        nilaiSuara = null;
        nilaiFont = null;
        nilaiCadangan = null;
        nilaiFolder = null;
        eTarget = null;
        eNamaProfil = null;
        eWaProfil = null;
        eRekeningProfil = null;
        eTokenGithub = null;
    }

    /** Menu kategori pengaturan (reorganisasi 2026-11-17). */
    private void tampilkanMenuKategori() {
        kategoriAktif = null;
        resetFieldTampilan();
        ScrollView sv = new ScrollView(this);
        root = newRoot();
        sv.addView(root);
        setContentView(sv);

        judul("Pengaturan");
        labelKecil("Pilih kategori pengaturan. Setiap kategori punya tombol "
                + "\"Kembali ke Sebelumnya\" untuk kembali ke menu ini.");
        tombolKategori("Pengaturan GitHub", "github",
                "Keamanan, tanda tangan pribadi, history, dan token GitHub.");
        tombolKategori("Pengaturan Bangun APK Perangkat", "bangun",
                "Simpan kata sandi keystore dan history build di perangkat.");
        tombolKategori("Tema", "tema",
                "Warna aplikasi (aksen tombol).");
        tombolKategori("Tanda Tangan", "tandatangan",
                "Buat keystore untuk tanda tangan pribadi.");
        tombolKategori("Folder Proyek", "folder",
                "Lokasi folder proyek.");
        tombolKategori("Bahasa dan Suara", "bahasa",
                "Bahasa antarmuka dan kecepatan suara Mode Suara.");
        tombolKategori("Ukuran Teks dan Tampilan", "font",
                "Ukuran font editor kode.");
        tombolKategori("Build", "build",
                "SDK target bawaan dan pisah arsitektur bawaan.");
        tombolKategori("Notifikasi", "notifikasi",
                "Bunyi saat build selesai.");
        tombolKategori("Cadangan Otomatis", "cadangan",
                "Jadwal cadangan proyek.");
        tombolKategori("Profil Pengembang", "profil",
                "Nama, nomor WhatsApp, dan rekening di layar Donasi.");
        tombolKategori("Privasi dan Data", "privasi",
                "Reset pengaturan dan hapus data aplikasi.");

        // Bahasa Aplikasi di PALING BAWAH Pengaturan (perintah al kautsar
        // 2026-11-23): 50 bahasa + pencarian, bawaan mengikuti sistem.
        judul("Bahasa Aplikasi");
        label("Bahasa saat ini: " + Bahasa.labelPilihan(
                pref.getString(KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage()));
        labelKecil("Bawaan mengikuti sistem Android. Bahasa terpilih dipakai "
                + "di Tentang Alat dan menjadi bahasa aplikasi. Tersedia 50 "
                + "bahasa dengan pencarian.");
        button("Pilih Bahasa Aplikasi", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(PengaturanActivity.this, BahasaActivity.class));
                } catch (Throwable t) {
                    toast("Gagal membuka pemilih bahasa: " + t.getMessage());
                }
            }
        });

        button("Kembali ke Menu Utama", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        announce("Pengaturan. Pilih kategori.");
    }

    /** Satu baris kategori di menu: tombol + keterangan kecil. */
    private void tombolKategori(String nama, final String id, String keterangan) {
        button(nama, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanKategori(id);
            }
        });
        labelKecil(keterangan);
    }

    /** Buka satu kategori (isinya + tombol Kembali ke Sebelumnya). */
    private void tampilkanKategori(String id) {
        kategoriAktif = id;
        resetFieldTampilan();
        ScrollView sv = new ScrollView(this);
        root = newRoot();
        sv.addView(root);
        setContentView(sv);

        if ("github".equals(id)) {
            bagianGithub();
        } else if ("bangun".equals(id)) {
            bagianBangunPerangkat();
        } else if ("tema".equals(id)) {
            bagianTema();
        } else if ("tandatangan".equals(id)) {
            bagianTandaTangan();
        } else if ("folder".equals(id)) {
            bagianFolder();
        } else if ("bahasa".equals(id)) {
            bagianBahasaSuara();
        } else if ("font".equals(id)) {
            bagianFont();
        } else if ("build".equals(id)) {
            bagianBuild();
        } else if ("notifikasi".equals(id)) {
            bagianNotifikasi();
        } else if ("cadangan".equals(id)) {
            bagianCadangan();
        } else if ("profil".equals(id)) {
            bagianProfil();
        } else if ("privasi".equals(id)) {
            bagianPrivasi();
        } else {
            tampilkanMenuKategori();
            return;
        }

        tombolKembaliKeSebelumnya();
        announce(judulKategori(id));
    }

    private String judulKategori(String id) {
        if ("github".equals(id)) return "Pengaturan GitHub";
        if ("bangun".equals(id)) return "Pengaturan Bangun APK Perangkat";
        if ("tema".equals(id)) return "Tema";
        if ("tandatangan".equals(id)) return "Tanda Tangan";
        if ("folder".equals(id)) return "Folder Proyek";
        if ("bahasa".equals(id)) return "Bahasa dan Suara";
        if ("font".equals(id)) return "Ukuran Teks dan Tampilan";
        if ("build".equals(id)) return "Build";
        if ("notifikasi".equals(id)) return "Notifikasi";
        if ("cadangan".equals(id)) return "Cadangan Otomatis";
        if ("profil".equals(id)) return "Profil Pengembang";
        if ("privasi".equals(id)) return "Privasi dan Data";
        return "Pengaturan";
    }

    /** Tombol kembali ke menu kategori (bukan ke menu utama). */
    private void tombolKembaliKeSebelumnya() {
        button("Kembali ke Sebelumnya", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanMenuKategori();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        PasangHelper.lanjutkanPasangTertunda(this);
        muatUlangNilai();
    }

    // ------------------------------------------------------------------ UI

    private LinearLayout newRoot() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        GayaStudio.latar(r);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        return r;
    }

    private void judul(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.judul(tv, 18);
        tv.setPadding(0, dp(14), 0, dp(4));
        root.addView(tv);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.label(tv, 14);
        tv.setPadding(0, dp(8), 0, dp(2));
        root.addView(tv);
        return tv;
    }

    private void labelKecil(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.labelKecil(tv, 12);
        tv.setPadding(0, dp(2), 0, dp(2));
        root.addView(tv);
    }

    private Button button(String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(onClick);
        GayaStudio.tombol(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        root.addView(b, lp);
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

    private void simpan(String kunci, String nilai) {
        pref.edit().putString(kunci, nilai).apply();
    }

    private void simpan(String kunci, boolean nilai) {
        pref.edit().putBoolean(kunci, nilai).apply();
    }

    private void simpan(String kunci, int nilai) {
        pref.edit().putInt(kunci, nilai).apply();
    }

    private void simpan(String kunci, float nilai) {
        pref.edit().putFloat(kunci, nilai).apply();
    }

    /** Dialog pilihan umum: opsi ditampilkan, yang dipilih disimpan. */
    private void pilihNilai(String judul, final String[] opsi, int terpilih,
                            final DialogInterface.OnClickListener pendengar) {
        announce(judul + ". " + opsi.length + " pilihan.");
        new AlertDialog.Builder(this)
                .setTitle(judul)
                .setSingleChoiceItems(opsi, terpilih, pendengar)
                .setNegativeButton("Batal", null)
                .show();
    }

    private int indeks(String[] nilai, String cari, int bawaan) {
        if (cari == null) return bawaan;
        for (int i = 0; i < nilai.length; i++) {
            if (nilai[i].equals(cari)) return i;
        }
        return bawaan;
    }

    // ------------------------------------------------------- nilai tampilan

    private void muatUlangNilai() {
        if (nilaiBahasa != null) {
            nilaiBahasa.setText("Bahasa saat ini: " + labelBahasa(
                    pref.getString(KUNCI_BAHASA, Bahasa.SISTEM)));
        }
        if (nilaiSuara != null) {
            nilaiSuara.setText("Kecepatan saat ini: " + labelSuara(
                    pref.getFloat(KUNCI_KECEPATAN_SUARA, 1.0f)));
        }
        if (nilaiFont != null) {
            nilaiFont.setText("Ukuran saat ini: " + labelFont(
                    pref.getString(KUNCI_FONT_EDITOR, "sedang")));
        }
        if (nilaiCadangan != null) {
            nilaiCadangan.setText("Cadangan saat ini: " + labelCadangan(
                    pref.getString(KUNCI_CADANGAN, "mati")));
        }
        if (nilaiFolder != null) {
            nilaiFolder.setText("Folder saat ini: " + folderProyek());
        }
    }

    private String folderProyek() {
        String v = pref.getString(KUNCI_FOLDER_PROYEK, "");
        if (v == null || v.trim().isEmpty()) return FOLDER_PROYEK_BAWAAN;
        return v.trim();
    }

    private static String labelBahasa(String v) {
        return Bahasa.labelPilihan(v, java.util.Locale.getDefault().getLanguage());
    }

    private static String labelSuara(float v) {
        if (v <= 0.5f) return "0,5x (lambat)";
        if (v <= 0.75f) return "0,75x";
        if (v <= 1.0f) return "1x (normal)";
        if (v <= 1.25f) return "1,25x";
        if (v <= 1.5f) return "1,5x";
        return "2x (cepat)";
    }

    private static String labelFont(String v) {
        if ("kecil".equals(v)) return "Kecil";
        if ("besar".equals(v)) return "Besar";
        return "Sedang";
    }

    private static String labelCadangan(String v) {
        if ("harian".equals(v)) return "Harian";
        if ("mingguan".equals(v)) return "Mingguan";
        return "Mati";
    }

    // ------------------------------------------------------------- bagian 1

    // ------------------------------------------------------- bagian GitHub

    /** Kotak centang yang langsung disimpan ke SharedPreferences "pengaturan". */
    private CheckBox kotakCentang(String teks, boolean tercentang,
                                  final String kunci) {
        final CheckBox cb = new CheckBox(this);
        cb.setText(teks);
        cb.setChecked(tercentang);
        root.addView(cb);
        cb.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(
                            android.widget.CompoundButton buttonView, boolean isChecked) {
                        simpan(kunci, isChecked);
                    }
                });
        return cb;
    }

    private void bagianGithub() {
        judul("Pengaturan GitHub");
        labelKecil("Pengaturan build lewat GitHub Actions. Token yang sama "
                + "dipakai menu Bangun Proyek (online).");

        // 1) Keamanan — enkripsi keamanan aplikasi.
        kotakCentang("Keamanan (enkripsi keamanan aplikasi)",
                pref.getBoolean(KUNCI_GITHUB_KEAMANAN, true),
                KUNCI_GITHUB_KEAMANAN);
        labelKecil("Dicentang: skrip Lua aplikasi hasil build dienkripsi "
                + "sehingga tidak langsung bisa dibaca orang lain. "
                + "Bila dimatikan, skrip Lua aplikasi hasil build tidak "
                + "dienkripsi dan bisa dibaca dari APK.");

        // 2) Tanda tangan pribadi (bawaan proyek baru).
        kotakCentang("Gunakan tanda tangan pribadi",
                pref.getBoolean(KUNCI_GITHUB_TANDA_TANGAN, false),
                KUNCI_GITHUB_TANDA_TANGAN);
        labelKecil("Dicentang: centang \"Gunakan tanda tangan pribadi\" di "
                + "layar Buat Proyek sudah aktif sejak awal. Buat keystore "
                + "dulu di kategori Tanda Tangan bila belum ada.");

        // 3) Buat ke history.
        kotakCentang("Simpan build ke history",
                pref.getBoolean(KUNCI_GITHUB_HISTORY, true),
                KUNCI_GITHUB_HISTORY);
        labelKecil("Dicentang: setiap build GitHub yang berhasil dicatat "
                + "ke history build.");
        button("Lihat History Build", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanHistory(RiwayatBuild.JENIS_GITHUB);
            }
        });

        // 4) Token — PALING AKHIR.
        judul("Token akses GitHub");
        label("Token akses GitHub");
        eTokenGithub = new EditText(this);
        GayaStudio.edit(eTokenGithub);
        eTokenGithub.setHint("Tempel token di sini, contoh: ghp_xxxxxxxx");
        eTokenGithub.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        eTokenGithub.setText(GithubSettings.bacaToken(this));
        eTokenGithub.setTextSize(14);
        eTokenGithub.setSingleLine(true);
        root.addView(eTokenGithub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout barisToken = new LinearLayout(this);
        barisToken.setOrientation(LinearLayout.HORIZONTAL);
        Button bTempel = new Button(this);
        bTempel.setText("Tempel Token");
        bTempel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tempelToken();
            }
        });
        GayaStudio.tombol(bTempel);
        LinearLayout.LayoutParams lpTempel = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTempel.rightMargin = dp(8);
        barisToken.addView(bTempel, lpTempel);
        Button bSimpanToken = new Button(this);
        bSimpanToken.setText("Simpan Token");
        bSimpanToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanTokenGithub();
            }
        });
        GayaStudio.tombol(bSimpanToken);
        barisToken.addView(bSimpanToken, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(barisToken, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labelKecil("Cukup token. Repo privat \"pembuat-aplikasi-build\" dibuat "
                + "otomatis di akun GitHub Anda, lalu workflow dan mesin build "
                + "diunggah sendiri.");
        labelKecil("Cara membuat token: GitHub > Settings > Developer settings "
                + "> Personal access tokens > Tokens (classic) > Generate new "
                + "token. Centang izin \"repo\" dan \"workflow\".");
        labelKecil("Token disimpan hanya di penyimpanan privat aplikasi ini.");
    }

    /** Tempel token dari papan klip ke kotak token. */
    private void tempelToken() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                toast("Papan klip kosong");
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                toast("Papan klip kosong");
                return;
            }
            CharSequence t = clip.getItemAt(0).getText();
            if (t == null || t.toString().trim().isEmpty()) {
                toast("Papan klip kosong");
                return;
            }
            if (eTokenGithub != null) eTokenGithub.setText(t.toString().trim());
            toast("Token ditempel dari papan klip");
            announce("Token ditempel dari papan klip.");
        } catch (Throwable t) {
            toast("Gagal menempel token: " + t.getMessage());
        }
    }

    /** Simpan token dari kotak token ke github.cfg. */
    private void simpanTokenGithub() {
        try {
            String token = eTokenGithub == null ? ""
                    : eTokenGithub.getText().toString().trim();
            if (GithubSettings.simpanToken(this, token)) {
                toast("Token tersimpan");
                announce("Token tersimpan.");
            } else {
                toast("Gagal menyimpan token");
            }
        } catch (Throwable t) {
            toast("Gagal menyimpan token: " + t.getMessage());
        }
    }

    /** Tampilkan history build (GitHub / perangkat) dalam dialog. */
    private void tampilkanHistory(String jenis) {
        try {
            java.util.List<String> entri = RiwayatBuild.muat(this, jenis);
            String isi;
            if (entri.isEmpty()) {
                isi = "Belum ada history build"
                        + ("github".equals(jenis) ? " GitHub" : " perangkat")
                        + ". History tercatat setiap build berhasil bila "
                        + "centang \"Simpan build ke history\" aktif.";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = entri.size() - 1; i >= 0; i--) {
                    sb.append(entri.get(i)).append("\n");
                }
                isi = sb.toString().trim();
            }
            new AlertDialog.Builder(this)
                    .setTitle("History Build"
                            + ("github".equals(jenis) ? " GitHub" : " Perangkat"))
                    .setMessage(isi)
                    .setPositiveButton("Tutup", null)
                    .setNeutralButton("Hapus History", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            RiwayatBuild.hapus(PengaturanActivity.this, jenis);
                            toast("History build dihapus");
                        }
                    })
                    .show();
        } catch (Throwable t) {
            toast("Gagal membuka history: " + t.getMessage());
        }
    }

    // -------------------------------------------- bagian Bangun APK Perangkat

    private void bagianBangunPerangkat() {
        judul("Pengaturan Bangun APK Perangkat");
        labelKecil("Pengaturan build di perangkat (tanpa internet). "
                + "Keystore dibuat di kategori Tanda Tangan (menu Buat Keystore).");

        kotakCentang("Simpan kata sandi",
                pref.getBoolean(KUNCI_BANGUN_SIMPAN_SANDI, false),
                KUNCI_BANGUN_SIMPAN_SANDI);
        labelKecil("Dicentang: kata sandi keystore tersimpan otomatis "
                + "sehingga saat menandatangani APK tidak perlu memasukkan "
                + "kata sandi lagi. Kata sandi hanya tersimpan di penyimpanan "
                + "privat aplikasi ini; matikan centang ini bila HP dipakai "
                + "bersama orang lain.");

        kotakCentang("Simpan build ke history",
                pref.getBoolean(KUNCI_BANGUN_HISTORY, true),
                KUNCI_BANGUN_HISTORY);
        labelKecil("Dicentang: setiap build di perangkat yang berhasil "
                + "dicatat ke history build.");
        button("Lihat History Build", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanHistory(RiwayatBuild.JENIS_PERANGKAT);
            }
        });
    }

    // ---------------------------------------------------------------- bagian Tema

    private void bagianTema() {
        judul("Tema");
        labelKecil("Warna aplikasi (aksen tombol). Latar tetap terang supaya "
                + "semua teks sistem tetap terbaca. Tema tersimpan dan berlaku "
                + "mulai layar berikutnya dibuka.");
        label("Tema saat ini: " + PaletStudio.namaTema(temaAplikasi(this)));
        for (int i = 0; i < PaletStudio.SEMUA_TEMA.length; i++) {
            final PaletStudio.TemaAksen t = PaletStudio.SEMUA_TEMA[i];
            button(t.nama, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pilihTema(t.id);
                }
            });
        }
    }

    /** Simpan tema pilihan dan terapkan warnanya. */
    private void pilihTema(String idTema) {
        try {
            simpan(KUNCI_TEMA, idTema);
            PaletStudio.terapkanTema(idTema);
            toast("Tema tersimpan: " + PaletStudio.namaTema(idTema)
                    + ". Berlaku mulai layar berikutnya dibuka.");
            announce("Tema tersimpan: " + PaletStudio.namaTema(idTema) + ".");
            tampilkanKategori("tema");
        } catch (Throwable t) {
            toast("Gagal menyimpan tema: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------- bagian 2

    private void bagianTandaTangan() {
        judul("Tanda Tangan");
        labelKecil("Keystore untuk tanda tangan pribadi saat build. "
                + "Bila belum ada keystore, buat dulu di sini.");
        kotakCentang("Anti tamper aplikasi hasil build",
                pref.getBoolean(KUNCI_ANTI_TAMPER, false),
                KUNCI_ANTI_TAMPER);
        labelKecil("Dicentang: aplikasi hasil build di perangkat menjaga "
                + "dirinya sendiri. Saat dibuka, aplikasi memeriksa tanda "
                + "tangannya dan isi berkas dex-nya; bila ada orang lain "
                + "yang menandatangani ulang atau mengubah isinya, aplikasi "
                + "menolak berjalan. Debugger dan alat injeksi seperti "
                + "Frida/Xposed juga dideteksi, dan jendela aplikasi "
                + "dikunci dari rekaman layar. Setelan ini tersimpan juga "
                + "di berkas settings.gradle tiap proyek yang dibangun.");
        button("Buka Buat Keystore", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(PengaturanActivity.this,
                            KeystoreActivity.class));
                } catch (Throwable t) {
                    toast("Gagal membuka Buat Keystore: " + t.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------- bagian 3

    private void bagianFolder() {
        judul("Folder Proyek");
        nilaiFolder = label("Folder saat ini: " + folderProyek());
        labelKecil("Tersimpan. Folder proyek di aplikasi ini masih tetap "
                + "Download/project di versi ini — pengaturan disimpan untuk "
                + "pembaruan berikutnya. Pemilihan folder lewat penjelajah "
                + "berkas belum tersedia; tulis jalur secara manual bila mau.");
        button("Ubah Folder Proyek", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ubahFolder();
            }
        });
    }

    private void ubahFolder() {
        final EditText e = new EditText(this);
        GayaStudio.edit(e);
        e.setText(folderProyek());
        e.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Folder Proyek")
                .setMessage("Tulis jalur folder proyek, contoh: /storage/emulated/0/Download/project")
                .setView(e)
                .setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String v = e.getText().toString().trim();
                        if (v.isEmpty()) {
                            toast("Jalur folder tidak boleh kosong");
                            return;
                        }
                        simpan(KUNCI_FOLDER_PROYEK, v);
                        muatUlangNilai();
                        toast("Folder proyek tersimpan");
                        announce("Folder proyek tersimpan: " + v);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ------------------------------------------------------------- bagian 4

    private void bagianBahasaSuara() {
        judul("Bahasa dan Suara");
        nilaiBahasa = label("Bahasa saat ini: " + labelBahasa(
                pref.getString(KUNCI_BAHASA, Bahasa.SISTEM)));
        labelKecil("Bahasa aplikasi kini dipilih dari pemilih bahasa berisi "
                + "50 bahasa dengan pencarian (juga ada di paling bawah menu "
                + "Pengaturan). Bawaan mengikuti sistem Android. Bahasa "
                + "terpilih dipakai di Tentang Alat dan menjadi bahasa "
                + "aplikasi; pilihan berlaku penuh setelah aplikasi dibuka "
                + "kembali.");
        button("Ubah Bahasa", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(PengaturanActivity.this, BahasaActivity.class));
                } catch (Throwable t) {
                    toast("Gagal membuka pemilih bahasa: " + t.getMessage());
                }
            }
        });
        nilaiSuara = label("Kecepatan saat ini: " + labelSuara(
                pref.getFloat(KUNCI_KECEPATAN_SUARA, 1.0f)));
        labelKecil("Berlaku di Mode Suara — kecepatan bicara jawaban suara.");
        button("Ubah Kecepatan Suara", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final float[] nilai = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
                final String[] opsi = {"0,5x (lambat)", "0,75x", "1x (normal)",
                        "1,25x", "1,5x", "2x (cepat)"};
                float kini = pref.getFloat(KUNCI_KECEPATAN_SUARA, 1.0f);
                int terpilih = 2;
                for (int i = 0; i < nilai.length; i++) {
                    if (Math.abs(nilai[i] - kini) < 0.01f) terpilih = i;
                }
                pilihNilai("Kecepatan Suara", opsi, terpilih,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                simpan(KUNCI_KECEPATAN_SUARA, nilai[which]);
                                muatUlangNilai();
                                toast("Kecepatan suara tersimpan: " + opsi[which]);
                                dialog.dismiss();
                            }
                        });
            }
        });
    }

    // ------------------------------------------------------------- bagian 5

    private void bagianFont() {
        judul("Ukuran Teks dan Tampilan");
        nilaiFont = label("Ukuran saat ini: " + labelFont(
                pref.getString(KUNCI_FONT_EDITOR, "sedang")));
        labelKecil("Berlaku di editor kode (layar Edit Proyek) — berlaku "
                + "mulai proyek berikutnya dibuka.");
        button("Ubah Ukuran Font Editor", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String[] nilai = {"kecil", "sedang", "besar"};
                final String[] opsi = {"Kecil", "Sedang", "Besar"};
                pilihNilai("Ukuran Font Editor", opsi,
                        indeks(nilai, pref.getString(KUNCI_FONT_EDITOR, "sedang"), 1),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                simpan(KUNCI_FONT_EDITOR, nilai[which]);
                                muatUlangNilai();
                                toast("Ukuran font tersimpan: " + opsi[which]);
                                dialog.dismiss();
                            }
                        });
            }
        });
    }

    // ------------------------------------------------------------- bagian 6

    private void bagianBuild() {
        judul("Build");
        label("SDK target bawaan (21–33)");
        eTarget = new EditText(this);
        GayaStudio.edit(eTarget);
        eTarget.setHint("Contoh: 33");
        eTarget.setTextSize(14);
        eTarget.setSingleLine(true);
        eTarget.setInputType(InputType.TYPE_CLASS_NUMBER);
        eTarget.setText(String.valueOf(pref.getInt(KUNCI_BUILD_TARGET, 33)));
        root.addView(eTarget, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labelKecil("Dipakai sebagai nilai awal kolom SDK target di layar "
                + "Buat Proyek (masih bisa diubah tiap kali membuat proyek).");
        final CheckBox cbPisah = new CheckBox(this);
        cbPisah.setText("Pisah arsitektur sebagai bawaan");
        cbPisah.setChecked(pref.getBoolean(KUNCI_BUILD_PISAH, false));
        root.addView(cbPisah);
        labelKecil("Dicentang: centang \"arsitektur terpisah\" di layar Buat "
                + "Proyek sudah aktif sejak awal (dua APK: 64 bit dan 32 bit).");
        button("Simpan Pengaturan Build", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = eTarget.getText().toString().trim();
                int target = 36;
                try {
                    target = Integer.parseInt(t);
                } catch (Throwable ignored) {
                    toast("SDK target harus angka");
                    return;
                }
                if (target < 21 || target > 36) {
                    toast("SDK target harus 21 sampai 36");
                    return;
                }
                simpan(KUNCI_BUILD_TARGET, target);
                simpan(KUNCI_BUILD_PISAH, cbPisah.isChecked());
                toast("Pengaturan Build tersimpan");
                announce("Pengaturan Build tersimpan. SDK target bawaan: " + target + ".");
            }
        });
    }

    // ------------------------------------------------------------- bagian 7

    private void bagianNotifikasi() {
        judul("Notifikasi");
        final CheckBox cbBunyi = new CheckBox(this);
        cbBunyi.setText("Bunyi saat build selesai");
        cbBunyi.setChecked(pref.getBoolean(KUNCI_BUNYI_BUILD, true));
        root.addView(cbBunyi);
        labelKecil("Tersimpan. Bunyi saat build selesai belum diterapkan di "
                + "versi ini — pengaturan disimpan untuk pembaruan berikutnya.");
        cbBunyi.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(
                            android.widget.CompoundButton buttonView, boolean isChecked) {
                        simpan(KUNCI_BUNYI_BUILD, isChecked);
                        toast(isChecked ? "Bunyi build diaktifkan"
                                : "Bunyi build dimatikan");
                    }
                });
    }

    // ------------------------------------------------------------- bagian 8

    private void bagianCadangan() {
        judul("Cadangan Otomatis");
        nilaiCadangan = label("Cadangan saat ini: " + labelCadangan(
                pref.getString(KUNCI_CADANGAN, "mati")));
        labelKecil("Tersimpan. Penjadwalan cadangan otomatis belum aktif di "
                + "versi ini — jalankan cadangan manual dari menu Kelola "
                + "Project (Cadangkan). Pengaturan disimpan untuk pembaruan "
                + "berikutnya.");
        button("Ubah Cadangan Otomatis", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String[] nilai = {"mati", "harian", "mingguan"};
                final String[] opsi = {"Mati", "Harian", "Mingguan"};
                pilihNilai("Cadangan Otomatis", opsi,
                        indeks(nilai, pref.getString(KUNCI_CADANGAN, "mati"), 0),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                simpan(KUNCI_CADANGAN, nilai[which]);
                                muatUlangNilai();
                                toast("Cadangan otomatis tersimpan: " + opsi[which]);
                                dialog.dismiss();
                            }
                        });
            }
        });
    }

    // ------------------------------------------------------------- bagian 9

    private void bagianProfil() {
        judul("Profil Pengembang");
        labelKecil("Nama, nomor WhatsApp, dan rekening yang tampil di layar "
                + "Donasi ke Pengembang. Bila diisi di sini, data ini yang "
                + "dipakai (menggantikan data bawaan).");
        label("Nama pengembang");
        eNamaProfil = new EditText(this);
        GayaStudio.edit(eNamaProfil);
        eNamaProfil.setHint("Contoh: Al Kautsar");
        eNamaProfil.setTextSize(14);
        eNamaProfil.setSingleLine(true);
        eNamaProfil.setText(pref.getString(KUNCI_PROFIL_NAMA, ""));
        root.addView(eNamaProfil, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        label("Nomor WhatsApp pengembang");
        eWaProfil = new EditText(this);
        GayaStudio.edit(eWaProfil);
        eWaProfil.setHint("Diawali kode negara tanpa +, contoh: 628123456789");
        eWaProfil.setTextSize(14);
        eWaProfil.setSingleLine(true);
        eWaProfil.setInputType(InputType.TYPE_CLASS_PHONE);
        eWaProfil.setText(pref.getString(KUNCI_PROFIL_WA, ""));
        root.addView(eWaProfil, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        label("Rekening BRI pengembang");
        eRekeningProfil = new EditText(this);
        GayaStudio.edit(eRekeningProfil);
        eRekeningProfil.setHint("Nomor rekening BRI, hanya angka");
        eRekeningProfil.setTextSize(14);
        eRekeningProfil.setSingleLine(true);
        eRekeningProfil.setInputType(InputType.TYPE_CLASS_NUMBER);
        eRekeningProfil.setText(pref.getString(KUNCI_PROFIL_REKENING, ""));
        root.addView(eRekeningProfil, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button("Simpan Profil Pengembang", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpan(KUNCI_PROFIL_NAMA, eNamaProfil.getText().toString().trim());
                simpan(KUNCI_PROFIL_WA, eWaProfil.getText().toString().trim()
                        .replaceAll("[\\s\\-]", "").replace("+", ""));
                simpan(KUNCI_PROFIL_REKENING, eRekeningProfil.getText().toString().trim()
                        .replaceAll("[\\s\\-]", ""));
                toast("Profil pengembang tersimpan");
                announce("Profil pengembang tersimpan.");
            }
        });
    }

    // ------------------------------------------------------------ bagian 10

    private void bagianPrivasi() {
        judul("Privasi dan Data");
        labelKecil("Reset hanya menghapus pengaturan di layar ini. Hapus data "
                + "juga menghapus token GitHub dan keadaan Mode Suara. "
                + "Proyek di folder Download TIDAK ikut dihapus.");
        button("Reset Pengaturan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PengaturanActivity.this)
                        .setTitle("Reset Pengaturan")
                        .setMessage("Semua pengaturan di layar ini kembali ke bawaan. Lanjutkan?")
                        .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                pref.edit().clear().apply();
                                if (eTarget != null) eTarget.setText("33");
                                if (eNamaProfil != null) eNamaProfil.setText("");
                                if (eWaProfil != null) eWaProfil.setText("");
                                if (eRekeningProfil != null) eRekeningProfil.setText("");
                                muatUlangNilai();
                                toast("Pengaturan sudah direset");
                                announce("Pengaturan sudah direset.");
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            }
        });
        button("Hapus Data Aplikasi", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PengaturanActivity.this)
                        .setTitle("Hapus Data Aplikasi")
                        .setMessage("Pengaturan, token GitHub, dan keadaan Mode Suara "
                                + "akan dihapus. Proyek di folder Download tidak ikut "
                                + "dihapus. Lanjutkan?")
                        .setPositiveButton("Hapus", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                hapusData();
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            }
        });
    }

    private void hapusData() {
        try {
            pref.edit().clear().apply();
            getSharedPreferences("mode_suara", MODE_PRIVATE).edit().clear().apply();
            File token = new File(getFilesDir(), "github.cfg");
            if (token.exists()) token.delete();
        } catch (Throwable ignored) {
        }
        if (eTarget != null) eTarget.setText("33");
        if (eNamaProfil != null) eNamaProfil.setText("");
        if (eWaProfil != null) eWaProfil.setText("");
        if (eRekeningProfil != null) eRekeningProfil.setText("");
        muatUlangNilai();
        toast("Data aplikasi sudah dihapus (proyek tidak ikut dihapus)");
        announce("Data aplikasi sudah dihapus.");
    }
}
