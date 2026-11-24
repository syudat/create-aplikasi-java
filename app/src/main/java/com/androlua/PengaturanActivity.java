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
    /** Kode bahasa efektif layar ini (tahap tampilan B, 2026-11-23). */
    private String kodeBahasa = "id";

    /** Teks antarmuka dalam bahasa efektif (TeksUI, fallback Indonesia). */
    private String t(String kunci) {
        return TeksUI.teks(kodeBahasa, kunci);
    }

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
        kodeBahasa = Bahasa.kodeEfektif(
                getSharedPreferences(NAMA_PREF, MODE_PRIVATE).getString(
                        KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;
        setTitle(t("Pengaturan"));
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

        judul(t("Pengaturan"));
        labelKecil(t("Pilih kategori pengaturan. Setiap kategori punya tombol ")
                + t("\"Kembali ke Sebelumnya\" untuk kembali ke menu ini."));
        tombolKategori(t("Pengaturan GitHub"), "github",
                t("Keamanan, tanda tangan pribadi, history, dan token GitHub."));
        // Pengaturan AI di SAMPING Pengaturan GitHub (perintah al kautsar
        // 2026-11-24): provider AI, model cadangan, dan kunci API.
        tombolKategori(t("Pengaturan AI"), "ai",
                t("Penyedia AI untuk pembuatan aplikasi (online menyusul; saat ini masih AI lokal)"));
        tombolKategori(t("Pengaturan Bangun APK Perangkat"), "bangun",
                t("Simpan kata sandi keystore dan history build di perangkat."));
        tombolKategori(t("Tema"), "tema",
                t("Warna aplikasi (aksen tombol)."));
        tombolKategori(t("Tanda Tangan"), "tandatangan",
                t("Buat keystore untuk tanda tangan pribadi."));
        tombolKategori(t("Folder Proyek"), "folder",
                t("Lokasi folder proyek."));
        tombolKategori(t("Bahasa dan Suara"), "bahasa",
                t("Bahasa antarmuka dan kecepatan suara Mode Suara."));
        tombolKategori(t("Ukuran Teks dan Tampilan"), "font",
                t("Ukuran font editor kode."));
        tombolKategori(t("Build"), "build",
                t("SDK target bawaan dan pisah arsitektur bawaan."));
        tombolKategori(t("Notifikasi"), "notifikasi",
                t("Bunyi saat build selesai."));
        tombolKategori(t("Cadangan Otomatis"), "cadangan",
                t("Jadwal cadangan proyek."));
        tombolKategori(t("Profil Pengembang"), "profil",
                t("Nama, nomor WhatsApp, dan rekening di layar Donasi."));
        tombolKategori(t("Privasi dan Data"), "privasi",
                t("Reset pengaturan dan hapus data aplikasi."));

        // Bahasa Aplikasi di PALING BAWAH Pengaturan (perintah al kautsar
        // 2026-11-23): 50 bahasa + pencarian, bawaan mengikuti sistem.
        judul(t("Bahasa Aplikasi"));
        label(t("Bahasa saat ini:") + " " + Bahasa.labelPilihan(
                pref.getString(KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage()));
        labelKecil(t("Bawaan mengikuti sistem Android. Bahasa terpilih dipakai ")
                + t("di Tentang Alat dan menjadi bahasa aplikasi. Tersedia 50 ")
                + t("bahasa dengan pencarian."));
        button(t("Pilih Bahasa Aplikasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(PengaturanActivity.this, BahasaActivity.class));
                } catch (Throwable t) {
                    toast("Gagal membuka pemilih bahasa: " + t.getMessage());
                }
            }
        });

        button(t("Kembali ke Menu Utama"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        announce(t("Pengaturan") + t(". Pilih kategori."));
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
        // Pengaturan AI adalah layar sendiri, bukan bagian di sini:
        // buka aktivitasnya dan biarkan menu kategori tetap seperti semula.
        if ("ai".equals(id)) {
            try {
                startActivity(new Intent(this, PengaturanAiActivity.class));
            } catch (Throwable e) {
                Toast.makeText(this, t("Gagal membuka Pengaturan AI: ") + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
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
        if ("ai".equals(id)) return t("Pengaturan AI");
        if ("github".equals(id)) return t("Pengaturan GitHub");
        if ("bangun".equals(id)) return t("Pengaturan Bangun APK Perangkat");
        if ("tema".equals(id)) return t("Tema");
        if ("tandatangan".equals(id)) return t("Tanda Tangan");
        if ("folder".equals(id)) return t("Folder Proyek");
        if ("bahasa".equals(id)) return t("Bahasa dan Suara");
        if ("font".equals(id)) return t("Ukuran Teks dan Tampilan");
        if ("build".equals(id)) return t("Build");
        if ("notifikasi".equals(id)) return t("Notifikasi");
        if ("cadangan".equals(id)) return t("Cadangan Otomatis");
        if ("profil".equals(id)) return t("Profil Pengembang");
        if ("privasi".equals(id)) return t("Privasi dan Data");
        return t("Pengaturan");
    }

    /** Tombol kembali ke menu kategori (bukan ke menu utama). */
    private void tombolKembaliKeSebelumnya() {
        button(t("Kembali ke Sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanMenuKategori();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
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
        announce(judul + ". " + opsi.length + TeksUI.t(" pilihan."));
        new AlertDialog.Builder(this)
                .setTitle(judul)
                .setSingleChoiceItems(opsi, terpilih, pendengar)
                .setNegativeButton(t("Batal"), null)
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
            nilaiBahasa.setText(t("Bahasa saat ini:") + " " + labelBahasa(
                    pref.getString(KUNCI_BAHASA, Bahasa.SISTEM)));
        }
        if (nilaiSuara != null) {
            nilaiSuara.setText(t("Kecepatan saat ini:") + " " + labelSuara(
                    pref.getFloat(KUNCI_KECEPATAN_SUARA, 1.0f)));
        }
        if (nilaiFont != null) {
            nilaiFont.setText(t("Ukuran saat ini:") + " " + labelFont(
                    pref.getString(KUNCI_FONT_EDITOR, "sedang")));
        }
        if (nilaiCadangan != null) {
            nilaiCadangan.setText(t("Cadangan saat ini:") + " " + labelCadangan(
                    pref.getString(KUNCI_CADANGAN, "mati")));
        }
        if (nilaiFolder != null) {
            nilaiFolder.setText(t("Folder saat ini:") + " " + folderProyek());
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
        if (v <= 0.5f) return TeksUI.t("0,5x (lambat)");
        if (v <= 0.75f) return "0,75x";
        if (v <= 1.0f) return TeksUI.t("1x (normal)");
        if (v <= 1.25f) return "1,25x";
        if (v <= 1.5f) return "1,5x";
        return TeksUI.t("2x (cepat)");
    }

    private static String labelFont(String v) {
        if ("kecil".equals(v)) return TeksUI.t("Kecil");
        if ("besar".equals(v)) return TeksUI.t("Besar");
        return TeksUI.t("Sedang");
    }

    private static String labelCadangan(String v) {
        if ("harian".equals(v)) return TeksUI.t("Harian");
        if ("mingguan".equals(v)) return TeksUI.t("Mingguan");
        return TeksUI.t("Mati");
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
        judul(t("Pengaturan GitHub"));
        labelKecil(t("Pengaturan build lewat GitHub Actions. Token yang sama ")
                + t("dipakai menu Bangun Proyek (online)."));

        // 1) Keamanan — enkripsi keamanan aplikasi.
        kotakCentang(t("Keamanan (enkripsi keamanan aplikasi)"),
                pref.getBoolean(KUNCI_GITHUB_KEAMANAN, true),
                KUNCI_GITHUB_KEAMANAN);
        labelKecil(t("Dicentang: skrip Lua aplikasi hasil build dienkripsi ")
                + t("sehingga tidak langsung bisa dibaca orang lain. ")
                + t("Bila dimatikan, skrip Lua aplikasi hasil build tidak ")
                + t("dienkripsi dan bisa dibaca dari APK."));

        // 2) Tanda tangan pribadi (bawaan proyek baru).
        kotakCentang(t("Gunakan tanda tangan pribadi"),
                pref.getBoolean(KUNCI_GITHUB_TANDA_TANGAN, false),
                KUNCI_GITHUB_TANDA_TANGAN);
        labelKecil(t("Dicentang: centang \"Gunakan tanda tangan pribadi\" di ")
                + t("layar Buat Proyek sudah aktif sejak awal. Buat keystore ")
                + t("dulu di kategori Tanda Tangan bila belum ada."));

        // 3) Buat ke history.
        kotakCentang(t("Simpan build ke history"),
                pref.getBoolean(KUNCI_GITHUB_HISTORY, true),
                KUNCI_GITHUB_HISTORY);
        labelKecil(t("Dicentang: setiap build GitHub yang berhasil dicatat ")
                + t("ke history build."));
        button(t("Lihat History Build"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanHistory(RiwayatBuild.JENIS_GITHUB);
            }
        });

        // 4) Token — PALING AKHIR.
        judul(t("Token akses GitHub"));
        label(t("Token akses GitHub"));
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
        bTempel.setText(t("Tempel Token"));
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
        bSimpanToken.setText(t("Simpan Token"));
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
        labelKecil(t("Cukup token. Repo privat \"pembuat-aplikasi-build\" dibuat ")
                + t("otomatis di akun GitHub Anda, lalu workflow dan mesin build ")
                + TeksUI.t("diunggah sendiri."));
        labelKecil(t("Cara membuat token: GitHub > Settings > Developer settings ")
                + "> Personal access tokens > Tokens (classic) > Generate new "
                + t("token. Centang izin \"repo\" dan \"workflow\"."));
        labelKecil(t("Token disimpan hanya di penyimpanan privat aplikasi ini."));
    }

    /** Tempel token dari papan klip ke kotak token. */
    private void tempelToken() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                toast(t("Papan klip kosong"));
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                toast(t("Papan klip kosong"));
                return;
            }
            CharSequence t = clip.getItemAt(0).getText();
            if (t == null || t.toString().trim().isEmpty()) {
                toast(t("Papan klip kosong"));
                return;
            }
            if (eTokenGithub != null) eTokenGithub.setText(t.toString().trim());
            toast(t("Token ditempel dari papan klip"));
            announce(t("Token ditempel dari papan klip") + ".");
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
                toast(t("Token tersimpan"));
                announce(t("Token tersimpan") + ".");
            } else {
                toast(t("Gagal menyimpan token"));
            }
        } catch (Throwable t) {
            toast(t("Gagal menyimpan token") + ": " + t.getMessage());
        }
    }

    /** Tampilkan history build (GitHub / perangkat) dalam dialog. */
    private void tampilkanHistory(String jenis) {
        try {
            java.util.List<String> entri = RiwayatBuild.muat(this, jenis);
            String isi;
            if (entri.isEmpty()) {
                isi = t("Belum ada history build")
                        + ("github".equals(jenis) ? TeksUI.t(" GitHub") : TeksUI.t(" perangkat"))
                        + t(". History tercatat setiap build berhasil bila ")
                        + t("centang \"Simpan build ke history\" aktif.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = entri.size() - 1; i >= 0; i--) {
                    sb.append(entri.get(i)).append("\n");
                }
                isi = sb.toString().trim();
            }
            new AlertDialog.Builder(this)
                    .setTitle("github".equals(jenis)
                            ? t("History Build GitHub") : t("History Build Perangkat"))
                    .setMessage(isi)
                    .setPositiveButton(t("Tutup"), null)
                    .setNeutralButton(t("Hapus"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            RiwayatBuild.hapus(PengaturanActivity.this, jenis);
                            toast(TeksUI.t("History build dihapus"));
                        }
                    })
                    .show();
        } catch (Throwable t) {
            toast("Gagal membuka history: " + t.getMessage());
        }
    }

    // -------------------------------------------- bagian Bangun APK Perangkat

    private void bagianBangunPerangkat() {
        judul(t("Pengaturan Bangun APK Perangkat"));
        labelKecil(t("Pengaturan build di perangkat (tanpa internet). ")
                + t("Keystore dibuat di kategori Tanda Tangan (menu Buat Keystore)."));

        kotakCentang(t("Simpan kata sandi"),
                pref.getBoolean(KUNCI_BANGUN_SIMPAN_SANDI, false),
                KUNCI_BANGUN_SIMPAN_SANDI);
        labelKecil(t("Dicentang: kata sandi keystore tersimpan otomatis ")
                + t("sehingga saat menandatangani APK tidak perlu memasukkan ")
                + t("kata sandi lagi. Kata sandi hanya tersimpan di penyimpanan ")
                + TeksUI.t("privat aplikasi ini; matikan centang ini bila HP dipakai ")
                + t("bersama orang lain."));

        kotakCentang(t("Simpan build ke history"),
                pref.getBoolean(KUNCI_BANGUN_HISTORY, true),
                KUNCI_BANGUN_HISTORY);
        labelKecil(t("Dicentang: setiap build di perangkat yang berhasil ")
                + t("dicatat ke history build."));
        button(t("Lihat History Build"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanHistory(RiwayatBuild.JENIS_PERANGKAT);
            }
        });
    }

    // ---------------------------------------------------------------- bagian Tema

    private void bagianTema() {
        judul(t("Tema"));
        labelKecil(t("Warna aplikasi (aksen tombol). Latar tetap terang supaya ")
                + t("semua teks sistem tetap terbaca. Tema tersimpan dan berlaku ")
                + t("mulai layar berikutnya dibuka."));
        label(t("Tema saat ini:") + " " + PaletStudio.namaTema(temaAplikasi(this)));
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
                    + t(". Berlaku mulai layar berikutnya dibuka."));
            announce(t("Tema tersimpan: ") + PaletStudio.namaTema(idTema) + ".");
            tampilkanKategori("tema");
        } catch (Throwable t) {
            toast("Gagal menyimpan tema: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------- bagian 2

    private void bagianTandaTangan() {
        judul(t("Tanda Tangan"));
        labelKecil(t("Keystore untuk tanda tangan pribadi saat build. ")
                + t("Bila belum ada keystore, buat dulu di sini."));
        kotakCentang(t("Anti tamper aplikasi hasil build"),
                pref.getBoolean(KUNCI_ANTI_TAMPER, false),
                KUNCI_ANTI_TAMPER);
        labelKecil(t("Dicentang: aplikasi hasil build di perangkat menjaga ")
                + t("dirinya sendiri. Saat dibuka, aplikasi memeriksa tanda ")
                + TeksUI.t("tangannya dan isi berkas dex-nya; bila ada orang lain ")
                + t("yang menandatangani ulang atau mengubah isinya, aplikasi ")
                + t("menolak berjalan. Debugger dan alat injeksi seperti ")
                + t("Frida/Xposed juga dideteksi, dan jendela aplikasi ")
                + t("dikunci dari rekaman layar. Setelan ini tersimpan juga ")
                + t("di berkas settings.gradle tiap proyek yang dibangun."));
        button(t("Buka Buat Keystore"), new View.OnClickListener() {
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
        judul(t("Folder Proyek"));
        nilaiFolder = label(t("Folder saat ini:") + " " + folderProyek());
        labelKecil(t("Tersimpan. Folder proyek di aplikasi ini masih tetap ")
                + t("Download/project di versi ini — pengaturan disimpan untuk ")
                + t("pembaruan berikutnya. Pemilihan folder lewat penjelajah ")
                + TeksUI.t("berkas belum tersedia; tulis jalur secara manual bila mau."));
        button(t("Ubah Folder Proyek"), new View.OnClickListener() {
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
                .setTitle(t("Folder Proyek"))
                .setMessage("Tulis jalur folder proyek, contoh: /storage/emulated/0/Download/project")
                .setView(e)
                .setPositiveButton(t("Simpan"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String v = e.getText().toString().trim();
                        if (v.isEmpty()) {
                            toast("Jalur folder tidak boleh kosong");
                            return;
                        }
                        simpan(KUNCI_FOLDER_PROYEK, v);
                        muatUlangNilai();
                        toast(t("Folder proyek tersimpan"));
                        announce(t("Folder proyek tersimpan") + ": " + v);
                    }
                })
                .setNegativeButton(t("Batal"), null)
                .show();
    }

    // ------------------------------------------------------------- bagian 4

    private void bagianBahasaSuara() {
        judul(t("Bahasa dan Suara"));
        nilaiBahasa = label(t("Bahasa saat ini:") + " " + labelBahasa(
                pref.getString(KUNCI_BAHASA, Bahasa.SISTEM)));
        labelKecil(t("Bahasa aplikasi kini dipilih dari pemilih bahasa berisi ")
                + t("50 bahasa dengan pencarian (juga ada di paling bawah menu ")
                + t("Pengaturan). Bawaan mengikuti sistem Android. Bahasa ")
                + t("terpilih dipakai di Tentang Alat dan menjadi bahasa ")
                + TeksUI.t("aplikasi; pilihan berlaku penuh setelah aplikasi dibuka ")
                + TeksUI.t("kembali."));
        button(t("Ubah Bahasa"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(PengaturanActivity.this, BahasaActivity.class));
                } catch (Throwable t) {
                    toast("Gagal membuka pemilih bahasa: " + t.getMessage());
                }
            }
        });
        nilaiSuara = label(t("Kecepatan saat ini:") + " " + labelSuara(
                pref.getFloat(KUNCI_KECEPATAN_SUARA, 1.0f)));
        labelKecil(t("Berlaku di Mode Suara — kecepatan bicara jawaban suara."));
        button(t("Ubah Kecepatan Suara"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final float[] nilai = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
                final String[] opsi = {TeksUI.t("0,5x (lambat)"), "0,75x", TeksUI.t("1x (normal)"),
                        "1,25x", "1,5x", TeksUI.t("2x (cepat)")};
                float kini = pref.getFloat(KUNCI_KECEPATAN_SUARA, 1.0f);
                int terpilih = 2;
                for (int i = 0; i < nilai.length; i++) {
                    if (Math.abs(nilai[i] - kini) < 0.01f) terpilih = i;
                }
                pilihNilai(t("Kecepatan Suara"), opsi, terpilih,
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
        judul(t("Ukuran Teks dan Tampilan"));
        nilaiFont = label(t("Ukuran saat ini:") + " " + labelFont(
                pref.getString(KUNCI_FONT_EDITOR, "sedang")));
        labelKecil(t("Berlaku di editor kode (layar Edit Proyek) — berlaku ")
                + t("mulai proyek berikutnya dibuka."));
        button(t("Ubah Ukuran Font Editor"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String[] nilai = {"kecil", "sedang", "besar"};
                final String[] opsi = {"Kecil", "Sedang", "Besar"};
                pilihNilai(t("Ukuran Font Editor"), opsi,
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
        judul(t("Build"));
        label(TeksUI.t("SDK target bawaan (21–36)"));
        eTarget = new EditText(this);
        GayaStudio.edit(eTarget);
        eTarget.setHint(TeksUI.t("Contoh: 33"));
        eTarget.setTextSize(14);
        eTarget.setSingleLine(true);
        eTarget.setInputType(InputType.TYPE_CLASS_NUMBER);
        eTarget.setText(String.valueOf(pref.getInt(KUNCI_BUILD_TARGET, 33)));
        root.addView(eTarget, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labelKecil(t("Dipakai sebagai nilai awal kolom SDK target di layar ")
                + t("Buat Proyek (masih bisa diubah tiap kali membuat proyek)."));
        final CheckBox cbPisah = new CheckBox(this);
        cbPisah.setText(t("Pisah arsitektur sebagai bawaan"));
        cbPisah.setChecked(pref.getBoolean(KUNCI_BUILD_PISAH, false));
        root.addView(cbPisah);
        labelKecil(t("Dicentang: centang \"arsitektur terpisah\" di layar Buat ")
                + t("Proyek sudah aktif sejak awal (dua APK: 64 bit dan 32 bit)."));
        button(t("Simpan Pengaturan Build"), new View.OnClickListener() {
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
                toast(t("Pengaturan Build tersimpan"));
                announce(t("Pengaturan Build tersimpan") + TeksUI.t(". SDK target bawaan: ") + target + ".");
            }
        });
    }

    // ------------------------------------------------------------- bagian 7

    private void bagianNotifikasi() {
        judul(t("Notifikasi"));
        final CheckBox cbBunyi = new CheckBox(this);
        cbBunyi.setText(t("Bunyi saat build selesai"));
        cbBunyi.setChecked(pref.getBoolean(KUNCI_BUNYI_BUILD, true));
        root.addView(cbBunyi);
        labelKecil(t("Tersimpan. Bunyi saat build selesai belum diterapkan di ")
                + t("versi ini — pengaturan disimpan untuk pembaruan berikutnya."));
        cbBunyi.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(
                            android.widget.CompoundButton buttonView, boolean isChecked) {
                        simpan(KUNCI_BUNYI_BUILD, isChecked);
                        toast(isChecked ? TeksUI.t("Bunyi build diaktifkan")
                                : TeksUI.t("Bunyi build dimatikan"));
                    }
                });
    }

    // ------------------------------------------------------------- bagian 8

    private void bagianCadangan() {
        judul(t("Cadangan Otomatis"));
        nilaiCadangan = label(t("Cadangan saat ini:") + " " + labelCadangan(
                pref.getString(KUNCI_CADANGAN, "mati")));
        labelKecil(t("Tersimpan. Penjadwalan cadangan otomatis belum aktif di ")
                + t("versi ini — jalankan cadangan manual dari menu Kelola ")
                + t("Project (Cadangkan). Pengaturan disimpan untuk pembaruan ")
                + TeksUI.t("berikutnya."));
        button(t("Ubah Cadangan Otomatis"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String[] nilai = {"mati", "harian", "mingguan"};
                final String[] opsi = {TeksUI.t("Mati"), TeksUI.t("Harian"), TeksUI.t("Mingguan")};
                pilihNilai(t("Cadangan Otomatis"), opsi,
                        indeks(nilai, pref.getString(KUNCI_CADANGAN, "mati"), 0),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                simpan(KUNCI_CADANGAN, nilai[which]);
                                muatUlangNilai();
                                toast(TeksUI.t("Cadangan otomatis tersimpan: ") + opsi[which]);
                                dialog.dismiss();
                            }
                        });
            }
        });
    }

    // ------------------------------------------------------------- bagian 9

    private void bagianProfil() {
        judul(t("Profil Pengembang"));
        labelKecil(t("Nama, nomor WhatsApp, dan rekening yang tampil di layar ")
                + t("Donasi ke Pengembang. Bila diisi di sini, data ini yang ")
                + TeksUI.t("dipakai (menggantikan data bawaan)."));
        label(t("Nama pengembang"));
        eNamaProfil = new EditText(this);
        GayaStudio.edit(eNamaProfil);
        eNamaProfil.setHint(TeksUI.t("Contoh: Al Kautsar"));
        eNamaProfil.setTextSize(14);
        eNamaProfil.setSingleLine(true);
        eNamaProfil.setText(pref.getString(KUNCI_PROFIL_NAMA, ""));
        root.addView(eNamaProfil, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        label(t("Nomor WhatsApp pengembang"));
        eWaProfil = new EditText(this);
        GayaStudio.edit(eWaProfil);
        eWaProfil.setHint("Diawali kode negara tanpa +, contoh: 628123456789");
        eWaProfil.setTextSize(14);
        eWaProfil.setSingleLine(true);
        eWaProfil.setInputType(InputType.TYPE_CLASS_PHONE);
        eWaProfil.setText(pref.getString(KUNCI_PROFIL_WA, ""));
        root.addView(eWaProfil, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        label(t("Rekening BRI pengembang"));
        eRekeningProfil = new EditText(this);
        GayaStudio.edit(eRekeningProfil);
        eRekeningProfil.setHint("Nomor rekening BRI, hanya angka");
        eRekeningProfil.setTextSize(14);
        eRekeningProfil.setSingleLine(true);
        eRekeningProfil.setInputType(InputType.TYPE_CLASS_NUMBER);
        eRekeningProfil.setText(pref.getString(KUNCI_PROFIL_REKENING, ""));
        root.addView(eRekeningProfil, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(t("Simpan Profil Pengembang"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpan(KUNCI_PROFIL_NAMA, eNamaProfil.getText().toString().trim());
                simpan(KUNCI_PROFIL_WA, eWaProfil.getText().toString().trim()
                        .replaceAll("[\\s\\-]", "").replace("+", ""));
                simpan(KUNCI_PROFIL_REKENING, eRekeningProfil.getText().toString().trim()
                        .replaceAll("[\\s\\-]", ""));
                toast(t("Profil pengembang tersimpan"));
                announce(t("Profil pengembang tersimpan") + ".");
            }
        });
    }

    // ------------------------------------------------------------ bagian 10

    private void bagianPrivasi() {
        judul(t("Privasi dan Data"));
        labelKecil(t("Reset hanya menghapus pengaturan di layar ini. Hapus data ")
                + t("juga menghapus token GitHub dan keadaan Mode Suara. ")
                + t("Proyek di folder Download TIDAK ikut dihapus."));
        button(t("Reset Pengaturan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PengaturanActivity.this)
                        .setTitle(t("Reset Pengaturan"))
                        .setMessage(t("Semua pengaturan di layar ini kembali ke bawaan. Lanjutkan?"))
                        .setPositiveButton(t("Reset"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                pref.edit().clear().apply();
                                if (eTarget != null) eTarget.setText("33");
                                if (eNamaProfil != null) eNamaProfil.setText("");
                                if (eWaProfil != null) eWaProfil.setText("");
                                if (eRekeningProfil != null) eRekeningProfil.setText("");
                                muatUlangNilai();
                                toast(t("Pengaturan sudah direset"));
                                announce(t("Pengaturan sudah direset") + ".");
                            }
                        })
                        .setNegativeButton(t("Batal"), null)
                        .show();
            }
        });
        button(t("Hapus Data Aplikasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PengaturanActivity.this)
                        .setTitle(t("Hapus Data Aplikasi"))
                        .setMessage(t("Pengaturan, token GitHub, dan keadaan Mode Suara ")
                                + t("akan dihapus. Proyek di folder Download tidak ikut ")
                                + TeksUI.t("dihapus. Lanjutkan?"))
                        .setPositiveButton(t("Hapus"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                hapusData();
                            }
                        })
                        .setNegativeButton(t("Batal"), null)
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
        announce(t("Data aplikasi sudah dihapus."));
    }
}
