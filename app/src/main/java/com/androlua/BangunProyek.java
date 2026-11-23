package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Membangun proyek (port Java dari jembatan bangunProyek + buildProject +
 * buildOffline + startBuild + onBuildEvent + doSign + askSigning +
 * askPassword + afterBuild + showProgress + pasangApk +
 * mintaIzinPasangDialog di main.lua).
 *
 * Alur sama persis dengan Lua: periksa resource -> baca init.lua
 * (packagename wajib) -> dialog pilih cara membangun (offline / online /
 * jalankan dulu) -> BangunLokal / Pengemas -> tandatangani (Penandatangan)
 * -> pasang (PemasangApk, dengan jalur tertunda bila izin pasang belum aktif).
 *
 * Perbedaan yang disengaja (dicatat, bukan dihilangkan diam-diam):
 * - Langkah periksa sintaks skrip (checkScripts di Lua) TIDAK dibawa:
 *   di proyek non-Lua satu-satunya berkas .lua di akar proyek adalah
 *   init.lua, dan keabsahannya sudah dicakup pemeriksaan packagename di
 *   bawah; berkas .aly hanya ada di proyek Lua (yang sudah dihapus).
 *   Bila init.lua rusak, tetap muncul toast "init.lua tidak valid".
 * - Dialog kini tampil di Activity pemanggil (Java), bukan di Main (Lua):
 *   pemanggil TIDAK boleh memanggil MesinLua.tampilkanMain() atau
 *   finish() sesudah bangun()/ekspor() — Activity harus tetap hidup
 *   supaya dialognya tidak ikut tertutup.
 *
 * Jalur APK yang menunggu izin pasang disimpan di sini (pengganti global
 * jalurPasangTertunda di Lua); PasangHelper membacanya lewat
 * ambilJalurPasangTertunda() dari onResume, sama seperti dulu.
 */
public class BangunProyek {

    /** Folder proyek: Download/project (sama seperti ROOT di main.lua). */
    public static final File FOLDER_PROYEK =
            new File(Environment.getExternalStorageDirectory(), "Download/project");

    /** Jalur APK yang menunggu izin pasang (pengganti global jalurPasangTertunda di Lua). */
    private static String jalurPasangTertunda = null;

    /**
     * Ambil + bersihkan jalur APK tertunda (pengganti jembatan Lua
     * ambilJalurPasangTertunda, dipakai PasangHelper dari onResume).
     * Kembali: "" (tidak ada / izin belum aktif), jalur APK, atau
     * "HILANG:<jalur>" bila berkasnya sudah tidak ada.
     */
    public static String ambilJalurPasangTertunda(Activity activity) {
        if (jalurPasangTertunda == null || jalurPasangTertunda.isEmpty()) return "";
        boolean sudahBisa = false;
        try {
            sudahBisa = PemasangApk.bisaPasang(activity);
        } catch (Throwable ignored) {
        }
        if (!sudahBisa) return "";
        String jalur = jalurPasangTertunda;
        jalurPasangTertunda = null;
        if (new File(jalur).exists()) return jalur;
        return "HILANG:" + jalur;
    }

    /** Pendengar dialog pilihan (pengganti callback chooseDialog di Lua; posisi mulai 0). */
    public interface PilihListener {
        void padaPilih(int pos);
    }

    /**
     * Dialog pilihan daftar (pengganti chooseDialog di Lua): ListView +
     * ArrayAdapter + pengumuman aksesibilitas "judul. N pilihan."
     */
    public static void pilih(final Activity activity, String judul,
                             List<String> pilihan, final PilihListener cb) {
        ListView lv = new ListView(activity);
        lv.setAdapter(new ArrayAdapter<String>(activity,
                android.R.layout.simple_list_item_1, pilihan));
        final AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle(judul).setView(lv)
                .setNegativeButton(TeksUI.t("Batal"), null).show();
        try {
            activity.getWindow().getDecorView().announceForAccessibility(
                    judul + ". " + pilihan.size() + TeksUI.t(" pilihan."));
        } catch (Throwable ignored) {
        }
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                dlg.dismiss();
                cb.padaPilih(pos);
            }
        });
    }

    /** Titik masuk: bangun proyek di dirProyek (pengganti runFunc("bangunProyek", dir)). */
    public static void bangun(Activity activity, File dirProyek) {
        if (activity == null || dirProyek == null) return;
        new BangunProyek(activity, dirProyek).mulai();
    }

    private final Activity activity;
    private final File dir;
    private String appname = "";
    private String packagename = "";
    private String appver = "1.0";
    private boolean splitAbi = false;
    private boolean tandaTanganPribadi = false;
    private boolean antiTamper = false;
    private String namaKeystoreTerpilih = "";
    private int appsdk = 21;
    private AlertDialog progressDlg;
    /** Build ini lewat GitHub (true) atau perangkat (false) — untuk history. */
    private boolean buildGithub = false;

    private BangunProyek(Activity activity, File dir) {
        this.activity = activity;
        this.dir = dir;
    }

    /** Alur buildProject di Lua: periksa resource -> baca init.lua -> pilih cara membangun. */
    private void mulai() {
        // 1b) periksa resource sebelum build (satu-satunya titik masuk build).
        String masalahRes = PeriksaResource.periksa(dir);
        if (masalahRes != null) {
            new AlertDialog.Builder(activity).setTitle(TeksUI.t("Resource bermasalah"))
                    .setMessage(masalahRes + "\n" + "\n" + TeksUI.t("Perbaiki dulu sebelum membangun."))
                    .setPositiveButton("OK", null).show();
            return;
        }
        // 2) data proyek (init.lua dibaca sebagai teks, tanpa menjalankan Lua).
        packagename = InfoProyek.namaPaket(dir);
        if (packagename == null || packagename.isEmpty()) {
            toast(TeksUI.t("init.lua tidak valid (packagename tidak ditemukan)"));
            return;
        }
        appname = InfoProyek.namaAplikasi(dir);
        appver = InfoProyek.bacaKunci(dir, "appver", "1.0");
        splitAbi = InfoProyek.kunciTrue(dir, "split_abi");
        tandaTanganPribadi = InfoProyek.kunciTrue(dir, "tanda_tangan_pribadi");
        appsdk = angka(InfoProyek.bacaKunci(dir, "appsdk", "21"), 21);
        // Setelan per proyek di settings.gradle menimpa init.lua bila ada
        // (permintaan al kautsar: setelan menempel di proyek itu sendiri).
        SetelanProyek.Setelan sp = SetelanProyek.baca(dir);
        if (sp.adaArsitekturTerpisah) splitAbi = sp.arsitekturTerpisah;
        if (sp.adaTandaTanganPribadi) tandaTanganPribadi = sp.tandaTanganPribadi;
        if (sp.adaAppsdk) appsdk = sp.appsdk;
        if (sp.adaNamaKeystore) namaKeystoreTerpilih = sp.namaKeystore;
        antiTamper = sp.adaAntiTamper ? sp.antiTamper
                : PengaturanActivity.antiTamperBawaan(activity);
        // 4) pilih cara membangun.
        List<String> pilihan = new ArrayList<String>();
        pilihan.add(TeksUI.t("Bangun di perangkat (offline)"));
        pilihan.add(TeksUI.t("Bangun online (GitHub Actions)"));
        pilihan.add(TeksUI.t("Jalankan dulu"));
        pilih(activity, TeksUI.t("Bangun APK - ") + appname, pilihan, new PilihListener() {
            @Override
            public void padaPilih(int pos) {
                if (pos == 0) {
                    bangunOffline();
                } else if (pos == 1) {
                    bangunOnline();
                } else if (pos == 2) {
                    JalankanProyek.jalankan(activity, dir);
                }
            }
        });
    }

    /** Membangun APK langsung di perangkat (tanpa internet, tanpa Termux). */
    private void bangunOffline() {
        tampilkanProgress(TeksUI.t("Bangun di perangkat: ") + appname);
        buildGithub = false;
        BangunLokal.bangun(activity, dir.getAbsolutePath(), pendengarBangunLokal);
    }

    /** Bangun online (GitHub Actions): token dulu, lalu konfirmasi, lalu unggah. */
    private void bangunOnline() {
        String token = GithubSettings.bacaToken(activity);
        if (token == null || token.isEmpty()) {
            GithubSettings.tampilkan(activity, new Runnable() {
                @Override
                public void run() {
                    bangun(activity, dir);
                }
            });
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(TeksUI.t("Bangun online"))
                .setMessage(TeksUI.t("Proyek diunggah ke repo privat \"pembuat-aplikasi-build\" di akun GitHub Anda ")
                        + TeksUI.t("(dibuat otomatis bila belum ada), lalu dibangun di GitHub Actions. Hanya file yang berubah yang diunggah."))
                .setPositiveButton(TeksUI.t("Bangun"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mulaiBangunOnline();
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    /** Unggah proyek lalu bangun di GitHub Actions (pengganti startBuild di Lua). */
    private void mulaiBangunOnline() {
        tampilkanProgress(TeksUI.t("Bangun: ") + appname);
        buildGithub = true;
        String fdir = activity.getFilesDir().getAbsolutePath();
        Pengemas.bangun(dir.getAbsolutePath(), packagename,
                GithubSettings.bacaToken(activity),
                fdir + "/bangun-proyek.yml", fdir + "/engine.zip",
                pendengarPengemas,
                PengaturanActivity.keamananGithub(activity));
    }

    /** Pendengar BangunLokal (dipanggil di thread utama). */
    private final BangunLokal.Listener pendengarBangunLokal = new BangunLokal.Listener() {
        @Override
        public void onEvent(int type, String msg) {
            onBuildEvent(type, msg);
        }
    };

    /** Pendengar Pengemas (dipanggil di thread utama). */
    private final Pengemas.Listener pendengarPengemas = new Pengemas.Listener() {
        @Override
        public void onEvent(int type, String message) {
            onBuildEvent(type, message);
        }
    };

    /** Dipanggil setelah build selesai: APK masih belum ditandatangani. */
    private void afterBuild(String raw) {
        if (tandaTanganPribadi) {
            tanyaTandaTangan(raw);
        } else {
            tandatangani(raw, "", "");
        }
    }

    /** Peristiwa build (pengganti onBuildEvent di Lua): 0 = kemajuan, 1 = berhasil, lain = gagal. */
    private void onBuildEvent(int kind, String msg) {
        if (progressDlg == null) return;
        if (kind == BangunLokal.PROGRESS) {
            progressDlg.setMessage(String.valueOf(msg));
        } else if (kind == BangunLokal.SUCCESS) {
            try {
                progressDlg.dismiss();
            } catch (Throwable ignored) {
            }
            progressDlg = null;
            afterBuild(String.valueOf(msg));
        } else {
            try {
                progressDlg.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            } catch (Throwable ignored) {
            }
            progressDlg.setTitle(TeksUI.t("Build gagal"));
            progressDlg.setMessage(String.valueOf(msg));
        }
    }

    /** Pilih keystore pribadi bila proyek memakai tanda tangan pribadi. */
    private void tanyaTandaTangan(final String raw) {
        File folderSigning = new File(FOLDER_PROYEK, "signing");
        List<String> daftar = new ArrayList<String>();
        File[] isi = folderSigning.listFiles();
        if (isi != null) {
            for (int i = 0; i < isi.length; i++) {
                if (isi[i].isDirectory()
                        && new File(isi[i], "kunci.p12").isFile()) {
                    daftar.add(isi[i].getName());
                }
            }
        }
        // Sama seperti listDir di Lua: urut abjad tanpa membedakan huruf besar/kecil.
        Collections.sort(daftar, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int c = a.toLowerCase(Locale.US).compareTo(b.toLowerCase(Locale.US));
                if (c != 0) return c;
                return a.compareTo(b);
            }
        });
        if (daftar.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle(TeksUI.t("Belum ada tanda tangan pribadi"))
                    .setMessage("Project ini memakai tanda tangan pribadi, tetapi belum ada keystore di Download/project/signing. "
                            + TeksUI.t("Buat dulu lewat menu utama, Buat Keystore, lalu bangun lagi.") + "\n" + "\n" + TeksUI.t("APK yang belum ditandatangani ada di:") + "\n" + raw)
                    .setPositiveButton(TeksUI.t("Pakai kunci bawaan"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            tandatangani(raw, "", "");
                        }
                    })
                    .setNegativeButton(TeksUI.t("Tutup"), null).show();
            return;
        }
        if (daftar.size() == 1) {
            tanyaKataSandi(raw, daftar.get(0));
            return;
        }
        final List<String> pilihan = daftar;
        pilih(activity, TeksUI.t("Pilih tanda tangan"), pilihan, new PilihListener() {
            @Override
            public void padaPilih(int pos) {
                tanyaKataSandi(raw, pilihan.get(pos));
            }
        });
    }

    /** Minta kata sandi keystore pribadi. */
    private void tanyaKataSandi(final String raw, final String nama) {
        namaKeystoreTerpilih = nama == null ? "" : nama;
        // Reorganisasi pengaturan 2026-11-17: bila "Simpan kata sandi" aktif
        // dan kata sandi keystore ini tersimpan, pakai langsung tanpa bertanya.
        if (PengaturanActivity.simpanKataSandiBangun(activity)) {
            String tersimpan = PengaturanActivity.bacaKataSandiKeystore(activity, nama);
            if (tersimpan != null && !tersimpan.isEmpty()) {
                tandatangani(raw,
                        new File(new File(FOLDER_PROYEK, "signing"), nama).getAbsolutePath()
                                + "/kunci.p12",
                        tersimpan);
                return;
            }
        }
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        final EditText pass = new EditText(activity);
        pass.setHint("Masukkan kata sandi tanda tangan di sini");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pass, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity).setTitle(TeksUI.t("Kata sandi: ") + nama).setView(box)
                .setPositiveButton(TeksUI.t("Tandatangani"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String sandi = String.valueOf(pass.getText());
                        // Reorganisasi pengaturan 2026-11-17: simpan kata
                        // sandi bila centang "Simpan kata sandi" aktif.
                        if (PengaturanActivity.simpanKataSandiBangun(activity)) {
                            PengaturanActivity.simpanKataSandiKeystore(activity, nama, sandi);
                        }
                        tandatangani(raw,
                                new File(new File(FOLDER_PROYEK, "signing"), nama).getAbsolutePath()
                                        + "/kunci.p12",
                                sandi);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null).show();
    }

    /** Catat build yang berhasil ke history (bila centang history aktif). */
    private void catatHistoryBuild() {
        try {
            if (buildGithub) {
                if (PengaturanActivity.simpanHistoryGithub(activity)) {
                    RiwayatBuild.tambah(activity, RiwayatBuild.JENIS_GITHUB,
                            appname, "berhasil", TeksUI.t("GitHub Actions"));
                }
            } else {
                if (PengaturanActivity.simpanHistoryBangun(activity)) {
                    RiwayatBuild.tambah(activity, RiwayatBuild.JENIS_PERANGKAT,
                            appname, "berhasil", "perangkat");
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Simpan setelan efektif proyek ini ke settings.gradle di folder proyek. */
    private void simpanSetelanProyek() {
        try {
            SetelanProyek.Setelan s = new SetelanProyek.Setelan();
            s.arsitekturTerpisah = splitAbi;
            s.tandaTanganPribadi = tandaTanganPribadi;
            s.antiTamper = antiTamper;
            s.namaKeystore = namaKeystoreTerpilih == null ? "" : namaKeystoreTerpilih;
            s.appsdk = appsdk;
            SetelanProyek.tulis(dir, s, true);
        } catch (Throwable ignored) {
        }
    }

    /** Menandatangani APK mentah (pengganti doSign di Lua). p12 kosong = kunci bawaan proyek. */
    private void tandatangani(final String raw, String p12, String sandi) {
        tampilkanProgress(TeksUI.t("Menandatangani APK"));
        Penandatangan.tandatangani(activity, raw, new File(dir, "bin").getAbsolutePath(),
                namaFile(), splitAbi, p12 == null ? "" : p12, sandi == null ? "" : sandi,
                appsdk, antiTamper, dir.getAbsolutePath(), new BangunLokal.Listener() {
                    @Override
                    public void onEvent(int kind, String msg) {
                        if (progressDlg == null) return;
                        if (kind == BangunLokal.PROGRESS) {
                            progressDlg.setMessage(String.valueOf(msg));
                            return;
                        }
                        try {
                            progressDlg.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                        } catch (Throwable ignored) {
                        }
                        if (kind == BangunLokal.SUCCESS) {
                            List<String> daftar = new ArrayList<String>();
                            String teks = String.valueOf(msg);
                            for (String baris : teks.split("\n", -1)) {
                                if (!baris.isEmpty()) daftar.add(baris);
                            }
                            try {
                                progressDlg.dismiss();
                            } catch (Throwable ignored) {
                            }
                            progressDlg = null;
                            // Reorganisasi pengaturan 2026-11-17: catat build
                            // yang berhasil ke history (bila centang aktif).
                            catatHistoryBuild();
                            // Setelan efektif build ini menempel di proyek
                            // (settings.gradle), terbaca lagi build berikutnya.
                            simpanSetelanProyek();
                            final List<String> daftarApk = daftar;
                            new AlertDialog.Builder(activity).setTitle(TeksUI.t("APK siap dipasang"))
                                    .setMessage(TeksUI.t("APK sudah ditandatangani dan tersimpan di:") + "\n"
                                            + labelArsitektur(teks))
                                    .setPositiveButton(TeksUI.t("Pasang"), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (daftarApk.size() > 1) {
                                                List<String> pilihan = new ArrayList<String>();
                                                for (int i = 0; i < daftarApk.size(); i++) {
                                                    pilihan.add(new File(daftarApk.get(i)).getName());
                                                }
                                                pilih(activity, TeksUI.t("Pilih APK untuk dipasang"), pilihan,
                                                        new PilihListener() {
                                                            @Override
                                                            public void padaPilih(int pos) {
                                                                pasangApk(daftarApk.get(pos));
                                                            }
                                                        });
                                            } else {
                                                pasangApk(daftarApk.isEmpty() ? "" : daftarApk.get(0));
                                            }
                                        }
                                    })
                                    .setNegativeButton(TeksUI.t("Tutup"), null).show();
                            umumkan(TeksUI.t("APK siap dipasang. APK sudah ditandatangani dan tersimpan. Tekan Pasang untuk memasang."));
                            return;
                        }
                        progressDlg.setTitle(TeksUI.t("Gagal menandatangani"));
                        progressDlg.setMessage(String.valueOf(msg)
                                + "\n" + "\n" + TeksUI.t("APK yang belum ditandatangani tetap tersimpan di:") + "\n" + raw);
                    }
                });
    }

    /** Pasang APK hasil build (pengganti pasangApk di Lua). */
    private void pasangApk(String jalur) {
        if (!PemasangApk.bisaPasang(activity)) {
            if (jalur != null && !jalur.isEmpty()) jalurPasangTertunda = jalur;
            mintaIzinPasangDialog();
            return;
        }
        jalurPasangTertunda = null;
        if (jalur == null || jalur.isEmpty()) {
            toast("Izin pasang APK sudah aktif");
            return;
        }
        if (!new File(jalur).exists()) {
            toast("APK tidak ditemukan: " + jalur);
            return;
        }
        try {
            PemasangApk.pasang(activity, jalur);
        } catch (Throwable ignored) {
        }
    }

    /** Minta izin "Pasang aplikasi" lewat Pengaturan (pengganti mintaIzinPasangDialog di Lua). */
    private void mintaIzinPasangDialog() {
        new AlertDialog.Builder(activity).setTitle(TeksUI.t("Izin pasang belum aktif"))
                .setMessage(TeksUI.t("Android 8 ke atas meminta izin \"Pasang aplikasi\" untuk aplikasi ini dulu. ")
                        + TeksUI.t("Tekan \"Buka Pengaturan\", aktifkan \"Izinkan dari sumber ini\", lalu kembali ke sini. ")
                        + TeksUI.t("Pemasangan akan lanjut otomatis tanpa perlu menekan \"Pasang\" lagi."))
                .setPositiveButton(TeksUI.t("Buka Pengaturan"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            PemasangApk.mintaIzinPasang(activity);
                        } catch (Throwable ignored) {
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null).show();
        umumkan(TeksUI.t("Izin pasang belum aktif. Tekan Buka Pengaturan, aktifkan Izinkan dari sumber ini, lalu kembali ke sini. Pemasangan akan lanjut otomatis."));
    }

    /** Dialog kemajuan (pengganti showProgress di Lua): tidak bisa dibatalkan, tombol "Tutup" mati dulu. */
    private void tampilkanProgress(String judul) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(judul)
                .setMessage(TeksUI.t("Menyiapkan..."))
                .setCancelable(false)
                .setPositiveButton(TeksUI.t("Tutup"), null);
        progressDlg = b.create();
        progressDlg.show();
        try {
            progressDlg.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        } catch (Throwable ignored) {
        }
    }

    /** Nama berkas APK: nama aplikasi + versi, tanpa nama paket (sama seperti namaFile di Lua). */
    String namaFile() {
        String n = appname == null ? "aplikasi" : appname.toLowerCase(Locale.US);
        n = n.replaceAll("\\s+", ".");
        n = n.replaceAll("[^a-zA-Z0-9.\\-_]", "");
        n = n.replaceAll("\\.{2,}", ".");
        n = n.replaceAll("^\\.+", "");
        n = n.replaceAll("\\.+$", "");
        if (n.isEmpty()) n = "aplikasi";
        String v = appver == null ? "1.0" : appver.replaceAll("\\s+", "");
        return n + v;
    }

    /** Tambahkan keterangan arsitektur di belakang tiap baris jalur APK (sama seperti labelArsitektur di Lua). */
    static String labelArsitektur(String daftarJalur) {
        if (daftarJalur == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String baris : daftarJalur.split("\n", -1)) {
            if (baris.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(baris);
            if (baris.endsWith("-64bit.apk")) {
                sb.append(TeksUI.t("  (64 bit, arm64-v8a)"));
            } else if (baris.endsWith("-32bit.apk")) {
                sb.append(TeksUI.t("  (32 bit, armeabi-v7a)"));
            }
        }
        return sb.toString();
    }

    private int angka(String teks, int bawaan) {
        if (teks == null) return bawaan;
        try {
            return Integer.parseInt(teks.trim());
        } catch (Throwable ignored) {
            return bawaan;
        }
    }

    private int dp(int nilai) {
        return (int) (nilai * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void umumkan(String teks) {
        try {
            activity.getWindow().getDecorView().announceForAccessibility(teks);
        } catch (Throwable ignored) {
        }
    }

    private void toast(String teks) {
        Toast.makeText(activity, teks, Toast.LENGTH_SHORT).show();
    }
}
