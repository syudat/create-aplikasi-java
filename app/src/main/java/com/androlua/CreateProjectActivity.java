package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Layar Buat Proyek (Create) Create Aplikasi dalam Java murni (Tahap 2B).
 *
 * Mereplikasi persis showCreate() di main.lua: jenis proyek dipilih HANYA
 * lewat tombol "Pilih Jenis Aplikasi" (dialog 6 jenis: HTML / Situs Web /
 * Java / Kotlin / Compose / Python — Lua sudah dihapus, keputusan final al kautsar
 * 2026-11-17 00:16: Lua tidak dipakai lagi) — kotak centang/radio pilihan
 * jenis di layar ini sudah dihapus (Batch pasca-v2, permintaan al kautsar),
 * jenis yang sedang dipilih ditampilkan sebagai label teks. Kolom nama
 * aplikasi, nama paket, versi, kode versi, SDK minimum/target, kotak Skrip
 * HTML (hanya muncul untuk HTML/Situs Web), tombol pilih izin, centang
 * arsitektur terpisah (split_abi), centang tanda tangan pribadi, tombol
 * Buat Proyek dan Batal.
 *
 * Tahap 3B (Lisensi, versi berbayar): centang "Aktifkan Lisensi" + kotak
 * nomor WhatsApp pengembang (hanya tampil bila dicentang). Nomor divalidasi
 * (angka, diawali kode negara tanpa +, contoh: 628123456789) lalu dikirim
 * ke BuatProyek sebagai parameter lisensi (lisensi, wa).
 *
 * Validasi dilakukan di Java dengan aturan dan pesan yang sama persis seperti
 * tombol Buat di showCreate(). Pembuatan proyek sendiri murni Java
 * (BuatProyek.buat + TemplateDasar) — tanpa Lua, tanpa mesin Lua.
 *
 * UI dibuat programatik (tanpa layout XML), mengikuti pola JavaHomeActivity
 * dan helper UI di main.lua (newRoot / label / field / button).
 */
public class CreateProjectActivity extends Activity {
    private static final int DEFAULT_MIN = 21;
    private static final int DEFAULT_TARGET = 33;

    private static final String[] TIPE_IDS = {
        "html", "situs", "java", "kotlin", "compose", "python"
    };
    private static final String[] TIPE_LABELS = {
        "HTML (WebView + JavaScript, dibangun di perangkat)",
        "Situs Web (website HTML+CSS+JavaScript jadi APK)",
        "Java (dibangun di perangkat)",
        "Kotlin (bisa offline di HP atau lewat GitHub Actions)",
        "Compose (Jetpack Compose, dibangun di perangkat)",
        // Python (Chaquopy 17.2.0 + Python 3.11): kode Python di main.py,
        // host MainActivity Java memanggilnya lewat Chaquopy. Bisa dibangun
        // di perangkat (runtime Chaquopy dikemas di IDE) maupun lewat
        // GitHub Actions. SDK minimum 24 (syarat Chaquopy).
        "Python (Chaquopy, dibangun di perangkat atau lewat GitHub Actions)"
    };

    // Daftar izin persis permission.lua (permission_info), diurutkan seperti
    // table.sort(keys) di pickPermissions() (urutan byte ASCII).
    private static final String[] PERM_KEYS = {
        "ACCESSIBILITY_SERVICE",
        "ACCESS_COARSE_LOCATION",
        "ACCESS_FINE_LOCATION",
        "ACCESS_NETWORK_STATE",
        "ACCESS_WIFI_STATE",
        "ANSWER_PHONE_CALLS",
        "BLUETOOTH",
        "BLUETOOTH_ADMIN",
        "BLUETOOTH_CONNECT",
        "BLUETOOTH_SCAN",
        "CALL_PHONE",
        "CAMERA",
        "CHANGE_WIFI_STATE",
        "FOREGROUND_SERVICE",
        "INTERNET",
        "KILL_BACKGROUND_PROCESSES",
        "MANAGE_EXTERNAL_STORAGE",
        "NEARBY_WIFI_DEVICES",
        "PEMINDAIAN_WIFI",
        "POST_NOTIFICATIONS",
        "READ_CALL_LOG",
        "READ_CONTACTS",
        "READ_EXTERNAL_STORAGE",
        "READ_MEDIA_AUDIO",
        "READ_MEDIA_IMAGES",
        "READ_MEDIA_VIDEO",
        "READ_PHONE_STATE",
        "READ_SMS",
        "RECEIVE_BOOT_COMPLETED",
        "RECEIVE_SMS",
        "RECORD_AUDIO",
        "REQUEST_INSTALL_PACKAGES",
        "SEND_SMS",
        "SET_WALLPAPER",
        "SYSTEM_ALERT_WINDOW",
        "VIBRATE",
        "WAKE_LOCK",
        "WRITE_CALL_LOG",
        "WRITE_CONTACTS",
        "WRITE_EXTERNAL_STORAGE",
        "WRITE_SETTINGS"
    };
    private static final String[] PERM_LABELS = {
        "Layanan Aksesibilitas (pembaca layar / otomatisasi)",
        "Lokasi perkiraan",
        "Lokasi akurat (GPS)",
        "Lihat status jaringan",
        "Lihat status Wi-Fi",
        "Jawab panggilan telepon",
        "Bluetooth",
        "Kelola Bluetooth",
        "Sambung Bluetooth (Android 12+)",
        "Pindai Bluetooth (Android 12+)",
        "Lakukan panggilan telepon",
        "Kamera",
        "Ubah status Wi-Fi",
        "Layanan latar depan",
        "Internet",
        "Tutup proses latar belakang",
        "Akses semua file (Android 11+)",
        "Perangkat Wi-Fi sekitar (Android 13+)",
        "Pemindaian Wi-Fi (status Wi-Fi, jaringan sekitar, lokasi untuk memindai)",
        "Tampilkan notifikasi (Android 13+)",
        "Baca riwayat panggilan",
        "Baca kontak",
        "Baca penyimpanan",
        "Baca audio (Android 13+)",
        "Baca foto (Android 13+)",
        "Baca video (Android 13+)",
        "Baca status telepon",
        "Baca SMS",
        "Jalan otomatis saat perangkat menyala",
        "Terima SMS",
        "Mikrofon (rekam suara)",
        "Pasang aplikasi (APK)",
        "Kirim SMS",
        "Atur wallpaper",
        "Tampil di atas aplikasi lain",
        "Getar",
        "Cegah layar/perangkat tidur",
        "Tulis riwayat panggilan",
        "Tulis kontak",
        "Tulis penyimpanan",
        "Ubah pengaturan sistem"
    };
    // Sama seperti WIFI_PERMS di main.lua: dipilih bila PEMINDAIAN_WIFI dicentang.
    private static final String[] WIFI_PERMS = {
        "ACCESS_WIFI_STATE", "ACCESS_NETWORK_STATE",
        "ACCESS_FINE_LOCATION", "NEARBY_WIFI_DEVICES"
    };

    // Batch pasca-v2: pilihan jenis TIDAK LAGI radio — hanya satu nilai
    // + label teks. Pemilihan jenis hanya lewat dialog "Pilih Jenis
    // Aplikasi". Bawaan "java" (Lua sudah dihapus dari IDE).
    private String jenisTerpilih = "java";
    private TextView labelJenis;
    // Batch pasca-v2: nilai awal dari Pengaturan (Build) — target SDK
    // default + pisah arsitektur default; bila belum diatur, 33 / mati.
    private int defaultTarget = DEFAULT_TARGET;
    private boolean defaultPisah = false;
    /** Bawaan "Gunakan tanda tangan pribadi" dari Pengaturan GitHub. */
    private boolean defaultTandaTangan = false;
    private LinearLayout wadahSkrip;
    private EditText eName;
    private EditText ePkg;
    private EditText eVer;
    private EditText eCode;
    private EditText eMin;
    private EditText eTarget;
    private EditText eSkrip;
    private Button permBtn;
    private CheckBox cbSplit;
    private CheckBox cbSign;
    // Tahap 3B (Lisensi): centang aktifkan lisensi + wadah nomor WA.
    private CheckBox cbLisensi;
    private LinearLayout wadahWa;
    private EditText eWa;
    // Tahap 3C (Template siap pakai): id template terpilih (null = kosong).
    private String templateTerpilih = null;
    private Button templateBtn;
    // Izin terpilih; bawaan seperti defaultPerms() di main.lua: hanya INTERNET.
    private final Set<String> selectedPerms = new LinkedHashSet<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Buat Proyek Baru");
        selectedPerms.add("INTERNET");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        // Batch pasca-v2: nilai awal Build dari Pengaturan (bila ada).
        try {
            android.content.SharedPreferences prefPengaturan =
                    getSharedPreferences(PengaturanActivity.NAMA_PREF, MODE_PRIVATE);
            int targetSimpan = prefPengaturan.getInt(
                    PengaturanActivity.KUNCI_BUILD_TARGET, DEFAULT_TARGET);
            if (targetSimpan >= 21 && targetSimpan <= 34) defaultTarget = targetSimpan;
            defaultPisah = prefPengaturan.getBoolean(
                    PengaturanActivity.KUNCI_BUILD_PISAH, false);
            // Reorganisasi pengaturan 2026-11-17: bawaan tanda tangan
            // pribadi dari Pengaturan GitHub.
            defaultTandaTangan = prefPengaturan.getBoolean(
                    PengaturanActivity.KUNCI_GITHUB_TANDA_TANGAN, false);
        } catch (Throwable ignored) {
        }

        label(r, "Jenis proyek");
        labelJenis = new TextView(this);
        GayaStudio.label(labelJenis, 14);
        labelJenis.setPadding(0, dp(2), 0, dp(2));
        r.addView(labelJenis);
        perbaruiLabelJenis();
        button(r, "Pilih Jenis Aplikasi", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihJenisAplikasi();
            }
        });
        label(r, "HTML/Situs Web/Java/Kotlin/Compose/Python dibangun langsung di HP (offline). "
                + "Kotlin dan Compose juga bisa lewat menu \"Bangun proyek\" (GitHub Actions) kalau mau. "
                + "Python memakai runtime Chaquopy (SDK minimum 24) dan juga bisa lewat GitHub Actions.", true);

        // Tahap 3C (Template siap pakai): pilih template, jenis proyek
        // mengikuti template (mis. Kalkulator = Java). Memilih jenis lain
        // setelah itu membatalkan template (lihat perbaruiTemplateSesuaiTipe).
        label(r, "Template siap pakai");
        templateBtn = button(r, templateLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihTemplate();
            }
        });
        label(r, "Template mengisi proyek dengan kode awal yang sudah jadi "
                + "(bukan proyek kosong). Kosong berarti memakai kode contoh bawaan.", true);

        eName = field(r, "Nama aplikasi",
                "Masukkan nama aplikasi di sini, contoh: Garuda Decode", false);
        ePkg = field(r, "Nama paket (package name)",
                "Masukkan nama paket di sini, diawali com.android, contoh: com.android.garudadecode", false);
        eVer = field(r, "Versi aplikasi (kosong = 1.0)",
                "Masukkan versi aplikasi, contoh: 1.0", false);

        // Kode versi (tombol "Pilih Jenis Aplikasi" sudah pindah ke bagian
        // Jenis proyek di atas — Batch pasca-v2).
        label(r, "Kode versi (kosong = 1)");
        eCode = new EditText(this);
        GayaStudio.edit(eCode);
        eCode.setHint("Masukkan kode versi berupa angka, contoh: 1");
        eCode.setTextSize(14);
        eCode.setSingleLine(true);
        eCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        r.addView(eCode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        eMin = field(r, "SDK minimum (kosong = " + DEFAULT_MIN + ")",
                "Android terendah yang didukung, contoh: 21 (21=Android 5.0, 26=8.0, 29=10, 31=12)", true);
        eTarget = field(r, "SDK target (kosong = " + defaultTarget + ")",
                "Versi Android yang dituju, contoh: 33 (maksimum 33; 30=Android 11, 31=12, 33=13). "
                        + "Android 14-16 tetap bisa menjalankannya", true);

        // Kotak Skrip HTML (opsional): hanya untuk proyek HTML / Situs Web.
        wadahSkrip = new LinearLayout(this);
        wadahSkrip.setOrientation(LinearLayout.VERTICAL);
        label(wadahSkrip, "Skrip HTML (opsional)");
        eSkrip = new EditText(this);
        GayaStudio.edit(eSkrip);
        eSkrip.setHint("Tempel kode HTML lengkap di sini bila mau memakai halaman sendiri, "
                + "contoh: <!DOCTYPE html> ... Kosongkan untuk memakai template bawaan.");
        eSkrip.setTextSize(14);
        eSkrip.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        eSkrip.setMinLines(6);
        eSkrip.setGravity(Gravity.TOP);
        wadahSkrip.addView(eSkrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(wadahSkrip);
        perbaruiSkrip();

        label(r, "Izin aplikasi");
        permBtn = button(r, permSummary() + " (ketuk untuk mengubah)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickPermissions();
            }
        });

        cbSplit = new CheckBox(this);
        cbSplit.setText("Gunakan arsitektur terpisah 64 bit dan 32 bit");
        cbSplit.setChecked(defaultPisah);
        r.addView(cbSplit);
        label(r, "Tidak dicentang: satu APK yang berjalan di perangkat 64 bit maupun 32 bit. "
                + "Dicentang: dua APK terpisah yang lebih kecil.", true);
        cbSign = new CheckBox(this);
        cbSign.setText("Gunakan tanda tangan pribadi");
        cbSign.setChecked(defaultTandaTangan);
        r.addView(cbSign);
        label(r, "Dicentang: setelah build, Anda diminta memilih keystore buatan sendiri "
                + "(menu Buat Keystore). Tidak dicentang: memakai kunci bawaan.", true);

        // Tahap 3B (Lisensi, versi berbayar): centang + kotak nomor WA.
        // Kotak nomor hanya tampil bila centang aktif (pola yang sama
        // seperti kotak Skrip HTML yang hanya tampil untuk HTML/Situs Web).
        cbLisensi = new CheckBox(this);
        cbLisensi.setText("Aktifkan Lisensi (versi berbayar)");
        r.addView(cbLisensi);
        label(r, "Dicentang: aplikasi hasil build meminta kode lisensi saat pertama dibuka. "
                + "Pengguna mengirim ID perangkat ke WhatsApp Anda, Anda membalas kode lisensinya.", true);
        wadahWa = new LinearLayout(this);
        wadahWa.setOrientation(LinearLayout.VERTICAL);
        eWa = field(wadahWa, "Nomor WhatsApp pengembang",
                "Nomor WA diawali kode negara tanpa +, contoh: 628123456789", false);
        eWa.setInputType(InputType.TYPE_CLASS_PHONE);
        r.addView(wadahWa);
        wadahWa.setVisibility(View.GONE);
        cbLisensi.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                perbaruiWa();
            }
        });

        button(r, "Buat Proyek", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatProyek();
            }
        });
        button(r, "Batal", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce("Buat Proyek Baru");
    }

    // ------------------------------------------------------------------ UI

    private LinearLayout newRoot() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        GayaStudio.latar(r);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        return r;
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

    private EditText field(LinearLayout parent, String title, String hint, boolean numeric) {
        label(parent, title);
        EditText e = new EditText(this);
        GayaStudio.edit(e);
        e.setHint(hint);
        e.setTextSize(14);
        e.setSingleLine(true);
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        parent.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
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

    @Override
    protected void onResume() {
        super.onResume();
        // Sama seperti onResume() di Lua: lanjutkan pemasangan APK yang
        // tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
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

    // ------------------------------------------------------- jenis + skrip

    // Batch pasca-v2: jenis hanya satu nilai String (dulu dibaca dari
    // RadioGroup yang sudah dihapus).
    private String tipeTerpilih() {
        return jenisTerpilih;
    }

    private String labelUntukJenis(String id) {
        for (int i = 0; i < TIPE_IDS.length; i++) {
            if (TIPE_IDS[i].equals(id)) return TIPE_LABELS[i];
        }
        return TIPE_LABELS[0];
    }

    private void perbaruiLabelJenis() {
        if (labelJenis != null) {
            labelJenis.setText("Jenis: " + labelUntukJenis(jenisTerpilih));
            labelJenis.setContentDescription("Jenis proyek yang dipilih: "
                    + labelUntukJenis(jenisTerpilih));
        }
    }

    // Atur jenis tanpa memicu perbaruiSkrip (dipakai pilihTemplate:
    // jenis mengikuti template dulu, baru id template dicatat).
    private void aturJenis(String id) {
        if (id == null) return;
        for (int i = 0; i < TIPE_IDS.length; i++) {
            if (TIPE_IDS[i].equals(id)) {
                jenisTerpilih = id;
                perbaruiLabelJenis();
                return;
            }
        }
    }

    // Atur jenis dari dialog "Pilih Jenis Aplikasi".
    private void setJenis(String id) {
        aturJenis(id);
        perbaruiSkrip();
    }

    private void perbaruiSkrip() {
        String t = tipeTerpilih();
        if ("html".equals(t) || "situs".equals(t)) {
            wadahSkrip.setVisibility(View.VISIBLE);
        } else {
            wadahSkrip.setVisibility(View.GONE);
        }
        perbaruiTemplateSesuaiTipe();
    }

    // Tahap 3C (Template siap pakai): bila jenis proyek diubah manual ke
    // jenis lain setelah template dipilih, template dibatalkan (tiap
    // template hanya berlaku untuk satu jenis proyek).
    private void perbaruiTemplateSesuaiTipe() {
        if (templateTerpilih == null) return;
        TemplateProyek.Info info = TemplateProyek.cari(templateTerpilih);
        if (info == null || info.tipe == null) return;
        if (info.tipe.equals(tipeTerpilih())) return;
        String namaTemplate = info.nama;
        String tipeTemplate = info.tipe;
        templateTerpilih = null;
        if (templateBtn != null) templateBtn.setText(templateLabel());
        toast("Template \"" + namaTemplate + "\" hanya untuk proyek "
                + tipeTemplate + "; template dibatalkan karena jenis diubah.");
    }

    private String templateLabel() {
        if (templateTerpilih == null) return "Kosong (bawaan) (ketuk untuk mengubah)";
        TemplateProyek.Info info = TemplateProyek.cari(templateTerpilih);
        if (info == null) return "Kosong (bawaan) (ketuk untuk mengubah)";
        return info.nama + " (ketuk untuk mengubah)";
    }

    private void pilihTemplate() {
        final String[] pilihan = new String[TemplateProyek.SEMUA.length + 1];
        pilihan[0] = "Kosong (bawaan) — kode contoh bawaan";
        for (int i = 0; i < TemplateProyek.SEMUA.length; i++) {
            pilihan[i + 1] = TemplateProyek.SEMUA[i].nama + " — "
                    + TemplateProyek.SEMUA[i].deskripsi;
        }
        announce("Pilih Template. " + pilihan.length + " pilihan.");
        new AlertDialog.Builder(this)
                .setTitle("Pilih Template")
                .setItems(pilihan, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            templateTerpilih = null;
                            templateBtn.setText(templateLabel());
                            return;
                        }
                        int idx = which - 1;
                        if (idx < 0 || idx >= TemplateProyek.SEMUA.length) return;
                        TemplateProyek.Info info = TemplateProyek.SEMUA[idx];
                        // Jenis proyek mengikuti template dulu, baru id
                        // template dicatat (supaya perbaruiSkrip tidak
                        // membatalkannya karena jenis belum cocok).
                        aturJenis(info.tipe);
                        templateTerpilih = info.id;
                        templateBtn.setText(templateLabel());
                        perbaruiSkrip();
                        announce("Template " + info.nama + " dipilih. Jenis proyek: "
                                + info.tipe + ".");
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // Tahap 3B (Lisensi): kotak nomor WA hanya tampil bila centang aktif.
    private void perbaruiWa() {
        if (cbLisensi != null && wadahWa != null) {
            wadahWa.setVisibility(cbLisensi.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    // Tahap 3B (Lisensi): normalisasi nomor WA — buang spasi/dasis, buang
    // "+" di depan. Sah bila 9-15 angka dan tidak diawali 0 (harus diawali
    // kode negara, contoh: 628123456789).
    private static String normalisasiWa(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("[\\s\\-]", "");
        if (t.startsWith("+")) t = t.substring(1);
        return t;
    }

    private static boolean waValid(String t) {
        return t != null && t.matches("^[1-9][0-9]{8,14}$");
    }

    private void pilihJenisAplikasi() {
        announce("Pilih Jenis Aplikasi. " + TIPE_LABELS.length + " pilihan.");
        new AlertDialog.Builder(this)
                .setTitle("Pilih Jenis Aplikasi")
                .setItems(TIPE_LABELS, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < TIPE_IDS.length) {
                            setJenis(TIPE_IDS[which]);
                        }
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ------------------------------------------------------------- izin

    private String permSummary() {
        return selectedPerms.size() + " izin dipilih";
    }

    private void pickPermissions() {
        final boolean[] kerja = new boolean[PERM_KEYS.length];
        for (int i = 0; i < PERM_KEYS.length; i++) {
            kerja[i] = selectedPerms.contains(PERM_KEYS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Pilih Izin")
                .setMultiChoiceItems(PERM_LABELS, kerja,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                kerja[which] = isChecked;
                            }
                        })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedPerms.clear();
                        for (int i = 0; i < PERM_KEYS.length; i++) {
                            if (kerja[i]) selectedPerms.add(PERM_KEYS[i]);
                        }
                        permBtn.setText(permSummary() + " (ketuk untuk mengubah)");
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
        announce("Pilih Izin. " + PERM_KEYS.length
                + " izin tersedia. Centang hanya izin yang memang dibutuhkan aplikasi.");
    }

    // ------------------------------------------------------- buat proyek

    private Double parseDouble(String s) {
        if (s == null) return null;
        try {
            return Double.valueOf(Double.parseDouble(s.trim()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void buatProyek() {
        String name = eName.getText().toString().trim();
        String pkg = ePkg.getText().toString().trim();
        String ver = eVer.getText().toString().trim();
        Double codeD = parseDouble(eCode.getText().toString());
        Double minD = parseDouble(eMin.getText().toString());
        Double targetD = parseDouble(eTarget.getText().toString());
        if (ver.isEmpty()) ver = "1.0";
        // Sama seperti Lua: math.floor(nilai atau bawaan).
        double code = Math.floor(codeD != null ? codeD.doubleValue() : 1);
        double min = Math.floor(minD != null ? minD.doubleValue() : DEFAULT_MIN);
        double target = Math.floor(targetD != null ? targetD.doubleValue() : defaultTarget);

        // Validasi: urutan dan pesan sama persis seperti tombol Buat di showCreate().
        if (name.isEmpty()) {
            toast("Nama aplikasi belum diisi");
            return;
        }
        if ("signing".equals(name.toLowerCase(Locale.US))) {
            toast("Nama \"signing\" dipakai untuk folder tanda tangan, pilih nama lain");
            return;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.indexOf('*') >= 0 || name.indexOf('?') >= 0 || name.indexOf('"') >= 0
                || name.indexOf('<') >= 0 || name.indexOf('>') >= 0 || name.indexOf('|') >= 0
                || ".".equals(name) || "..".equals(name)) {
            toast("Nama aplikasi tidak boleh memuat karakter / \\ : * ? \" < > |");
            return;
        }
        if (!pkg.matches("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*[a-z0-9_.]*$")
                || pkg.contains("..") || pkg.endsWith(".")) {
            toast("Nama paket tidak valid. Contoh: com.android.garudadecode "
                    + "(huruf kecil, minimal dua bagian dipisah titik)");
            return;
        }
        if (min < 1 || target < 1) {
            toast("SDK harus berupa angka positif");
            return;
        }
        if (target > 33) {
            toast("SDK target maksimum 33 (aplikasi tetap berjalan di Android 14-16)");
            return;
        }
        if (min > target) {
            toast("SDK minimum tidak boleh lebih besar dari SDK target");
            return;
        }
        // Python (Chaquopy): runtime Chaquopy mensyaratkan SDK minimum 24
        // (Android 7.0). Bila pengguna mengisi lebih kecil, tolak dengan
        // pesan yang jelas supaya tidak gagal belakangan saat build.
        if ("python".equals(tipeTerpilih()) && min < 24) {
            toast("Proyek Python (Chaquopy) butuh SDK minimum 24 (Android 7.0). Naikkan SDK minimum ke 24 atau lebih.");
            return;
        }
        if (code < 1 || code > 2100000000) {
            toast("Kode versi harus angka 1 sampai 2100000000");
            return;
        }
        // Tahap 3B (Lisensi): bila dicentang, nomor WA wajib valid.
        boolean lisensi = cbLisensi.isChecked();
        String wa = "";
        if (lisensi) {
            wa = normalisasiWa(eWa.getText().toString());
            if (!waValid(wa)) {
                toast("Nomor WhatsApp tidak valid. Tulis angka diawali kode negara tanpa +, contoh: 628123456789");
                return;
            }
        }

        // Izin: PEMINDAIAN_WIFI dikembangkan menjadi WIFI_PERMS (seperti Lua),
        // lalu dikirim sebagai satu string dipisah koma.
        Set<String> perms = new LinkedHashSet<String>(selectedPerms);
        perms.remove("PEMINDAIAN_WIFI");
        if (selectedPerms.contains("PEMINDAIAN_WIFI")) {
            for (String k : WIFI_PERMS) perms.add(k);
        }
        List<String> kunci = new ArrayList<String>(perms);
        Collections.sort(kunci);
        StringBuilder csv = new StringBuilder();
        for (String k : kunci) {
            if (csv.length() > 0) csv.append(',');
            csv.append(k);
        }

        String tipe = tipeTerpilih();
        String skripHtml = "";
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            String isi = eSkrip.getText().toString().trim();
            if (!isi.isEmpty()) skripHtml = isi;
        }

        // Buat proyek murni Java (tanpa Lua): BuatProyek.buat() mengembalikan
        // "OK:<folder>" atau "GAGAL:<pesan>" (format sama seperti jembatan Lua).
        TemplateDasar.Param param = new TemplateDasar.Param();
        param.name = name;
        param.packageName = pkg;
        param.ver = ver;
        param.code = (int) code;
        param.min = (int) min;
        param.target = (int) target;
        param.tipe = tipe;
        param.skripHtml = skripHtml.isEmpty() ? null : skripHtml;
        param.split = cbSplit.isChecked();
        param.signing = cbSign.isChecked();
        param.lisensi = lisensi;
        param.wa = wa;
        java.util.Map<String, Boolean> permsMap = new java.util.LinkedHashMap<String, Boolean>();
        if (csv.length() > 0) {
            for (String k : csv.toString().split(",")) {
                if (!k.isEmpty()) permsMap.put(k, Boolean.TRUE);
            }
        }
        param.perms = permsMap;
        Object hasil;
        try {
            hasil = BuatProyek.buat(param);
        } catch (Throwable t) {
            toast("Gagal membuat proyek: " + t.getMessage());
            return;
        }
        if (hasil instanceof String) {
            String s = (String) hasil;
            if (s.startsWith("OK:")) {
                // Tahap 3A: proyek berhasil dibuat — tawarkan pembuatan ikon
                // (ikon bawaan proyek masih 1x1 transparan). "Buat Ikon"
                // membuka IconMakerActivity dengan folder proyek; "Nanti"
                // kembali ke home seperti dulu.
                final String dir = s.substring("OK:".length());
                // Tahap 3F (Perekam Aksi): catat pembuatan proyek (untuk putar ulang).
                PerekamAksi.catat(new File(getFilesDir(), PerekamAksi.NAMA_BERKAS),
                        "buat", name, tipe);
                // Tahap 3C (Template siap pakai): timpa berkas utama proyek
                // dengan kode awal template. Bila gagal, proyek tetap jadi
                // (kode bawaan) — hanya diberitahu lewat toast.
                if (templateTerpilih != null) {
                    String galatTemplate = TemplateProyek.terapkan(
                            new java.io.File(dir), templateTerpilih, pkg, name);
                    if (galatTemplate != null) {
                        toast("Template gagal diterapkan: " + galatTemplate);
                    } else {
                        TemplateProyek.Info infoT = TemplateProyek.cari(templateTerpilih);
                        if (infoT != null) {
                            toast("Template \"" + infoT.nama + "\" diterapkan");
                        }
                    }
                }
                toast("Proyek \"" + name + "\" berhasil dibuat");
                announce("Proyek " + name + " berhasil dibuat. Buat ikon untuk proyek ini?");
                new AlertDialog.Builder(this)
                        .setTitle("Proyek berhasil dibuat")
                        .setMessage("Buat ikon untuk proyek \"" + name
                                + "\" sekarang? Ikon bawaan masih transparan.")
                        .setPositiveButton("Buat Ikon", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent it = new Intent(CreateProjectActivity.this,
                                            IconMakerActivity.class);
                                    it.putExtra(IconMakerActivity.EXTRA_DIR, dir);
                                    startActivity(it);
                                } catch (Throwable t) {
                                    toast("Gagal membuka Pembuat Ikon: " + t.getMessage());
                                }
                                finish();
                            }
                        })
                        .setNegativeButton("Nanti", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
                return;
            }
            if (s.startsWith("GAGAL:")) {
                String msg = s.substring("GAGAL:".length());
                new AlertDialog.Builder(this)
                        .setTitle("Gagal").setMessage(msg)
                        .setPositiveButton("OK", null).show();
                announce("Gagal. " + msg);
                return;
            }
        }
        toast("Gagal membuat proyek");
    }
}
