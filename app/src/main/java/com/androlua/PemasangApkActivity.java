package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * APK Installer milik Create Aplikasi.
 *
 * Didaftarkan sebagai penangan berkas APK (intent filter
 * application/vnd.android.package-archive) sehingga muncul di dialog
 * "Buka dengan" saat pengguna mengetuk berkas APK — mis. APK yang
 * diterima lewat WhatsApp. Pemasangan lewat PackageInstaller (sesi):
 * sistem tetap menampilkan dialog konfirmasi pemasangan Android.
 *
 * Alur: terima Uri APK -> salin ke cache privat -> periksa APK
 * (paket, versi, tanda tangan, izin berbahaya, bentrok tanda tangan
 * dengan aplikasi yang sudah terpasang, sumber pengirim) -> minta
 * izin pasang dari sumber tak dikenal bila belum ada -> pasang lewat
 * sesi PackageInstaller -> umumkan hasil lewat pembaca layar.
 *
 * Jalur kedua, "Pasang lewat adb": bila pairing debugging nirkabel
 * sudah tersimpan (layar Debugger), APK dipasang lewat koneksi adb di
 * perangkat ini dengan perintah "pm install -i com.android.vending
 * -r". Flag -i itu hanya memberi tahu Android bahwa pemasang yang
 * dicatat adalah Play Store — aplikasinya TETAP bukan dari Play
 * Store, hanya label pemasangnya saja yang tertulis begitu. Jalur ini
 * ada karena ada aplikasi lain yang memakai cara itu; di sini caranya
 * dijelaskan apa adanya, bukan disembunyikan.
 *
 * Semua teks lewat TeksUI (50 bahasa). Tanpa gambar — ramah Jieshuo.
 */
public class PemasangApkActivity extends Activity {

    public static final String AKSI_STATUS = "com.androlua.PEMASANG_STATUS";

    private static final int MINTA_IZIN_PASANG = 901;

    private TextView tvInfo;
    private TextView tvHasil;
    private TextView tvStatus;
    private Button bPasang;
    private Button bPasangAdb;
    private Button bBatalkan;
    private Button bIzin;
    private Button bBuka;
    private Button bSelesai;
    private LinearLayout barisMemasang;
    private LinearLayout barisSukses;
    private boolean sedangMemasang = false;
    private Uri uriApk;
    private File berkasSalinan;
    private File berkasLuar;
    private List<File> berkasBagian = new ArrayList<File>();
    private List<File> berkasLuarBagian = new ArrayList<File>();
    private List<PembacaBundel.Obb> berkasObb = new ArrayList<PembacaBundel.Obb>();
    private File dirBundel;
    private boolean adalahBundel = false;
    private String formatBundel = "";
    private long ukuranTotal = 0;
    private PackageInfo infoArsip;
    private String namaPaket;
    private String namaBerkas = "";
    private long ukuranBerkas = 0;
    private boolean sudahPasang = false;
    private BroadcastReceiver penerimaStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("APK Installer"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("APK Installer"), 20);
        label(r, TeksUI.t("Pasang berkas APK lewat installer ini. Installer memeriksa dulu isi APK: nama paket, versi, tanda tangan, dan izin berbahaya, lalu meminta izin pasang dari sumber tak dikenal bila belum ada. Pasang hanya APK dari pengirim yang Anda percaya."), false);
        label(r, TeksUI.t("Ada juga tombol Pasang lewat adb: bila pairing debugging nirkabel sudah tersimpan di layar Debugger, APK dipasang lewat koneksi adb dengan catatan pemasang Play Store. Itu hanya label pemasang — aplikasinya tetap bukan dari Play Store."), false);
        label(r, TeksUI.t("Installer ini juga memasang paket bundel: .apks (split APK), .xapk, dan .apkm. Semua bagiannya dipasang sekaligus dalam satu sesi pemasangan, beserta berkas OBB bila ada."), false);

        label(r, TeksUI.t("Berkas APK:"), false);
        tvInfo = new TextView(this);
        tvInfo.setTextSize(14);
        tvInfo.setTextIsSelectable(true);
        tvInfo.setPadding(0, dp(4), 0, dp(4));
        tvInfo.setText(TeksUI.t("Memeriksa berkas APK..."));
        r.addView(tvInfo);

        label(r, TeksUI.t("Hasil pemeriksaan:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText("");
        r.addView(tvHasil);

        bPasang = button(r, TeksUI.t("Pasang APK"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiInstalasi();
            }
        });
        bPasang.setEnabled(false);
        bPasang.setVisibility(View.GONE);

        bPasangAdb = button(r, TeksUI.t("Pasang lewat adb (tercatat dari Play Store)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiInstalasi();
            }
        });
        bPasangAdb.setEnabled(false);
        bPasangAdb.setVisibility(View.GONE);

        bIzin = button(r, TeksUI.t("Buka pengaturan izin pasang"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPengaturanIzin();
            }
        });
        bIzin.setVisibility(View.GONE);

        // Baris pemasangan: keterangan + tombol Batalkan di sebelahnya.
        barisMemasang = new LinearLayout(this);
        barisMemasang.setOrientation(LinearLayout.HORIZONTAL);
        barisMemasang.setVisibility(View.GONE);
        tvStatus = new TextView(this);
        tvStatus.setTextSize(14);
        tvStatus.setPadding(0, dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams lpStatus = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        barisMemasang.addView(tvStatus, lpStatus);
        bBatalkan = new Button(this);
        bBatalkan.setText(TeksUI.t("Batalkan"));
        bBatalkan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                batalkanMemasang();
            }
        });
        GayaStudio.tombol(bBatalkan);
        barisMemasang.addView(bBatalkan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(barisMemasang, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Baris sukses: Buka + Selesai di sebelah-sebelahan.
        barisSukses = new LinearLayout(this);
        barisSukses.setOrientation(LinearLayout.HORIZONTAL);
        barisSukses.setVisibility(View.GONE);
        bBuka = new Button(this);
        bBuka.setText(TeksUI.t("Buka aplikasi yang dipasang"));
        bBuka.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAplikasi();
            }
        });
        GayaStudio.tombol(bBuka);
        LinearLayout.LayoutParams lpBuka = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpBuka.rightMargin = dp(8);
        barisSukses.addView(bBuka, lpBuka);
        bSelesai = new Button(this);
        bSelesai.setText(TeksUI.t("Selesai"));
        bSelesai.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        GayaStudio.tombol(bSelesai);
        barisSukses.addView(bSelesai, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(barisSukses, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Batal"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        daftarPenerimaStatus();
        prosesIntent(getIntent());
        announce(TeksUI.t("APK Installer") + ". " + TeksUI.t("Memeriksa berkas APK..."));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        sudahPasang = false;
        sedangMemasang = false;
        adalahBundel = false;
        formatBundel = "";
        berkasBagian.clear();
        berkasLuarBagian.clear();
        berkasObb.clear();
        ukuranTotal = 0;
        bPasang.setEnabled(false);
        bPasangAdb.setEnabled(false);
        barisMemasang.setVisibility(View.GONE);
        barisSukses.setVisibility(View.GONE);
        bIzin.setVisibility(View.GONE);
        prosesIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (penerimaStatus != null) unregisterReceiver(penerimaStatus);
        } catch (Throwable ignored) {
        }
        hapusSalinan();
    }

    @Override
    protected void onActivityResult(int kode, int hasil, Intent data) {
        super.onActivityResult(kode, hasil, data);
        if (kode == MINTA_IZIN_PASANG) {
            periksaIzinPasang();
        }
    }

    // ---------------- Penerimaan intent ----------------

    private void prosesIntent(Intent intent) {
        uriApk = intent == null ? null : intent.getData();
        if (uriApk == null) {
            tvInfo.setText(TeksUI.t("Tidak ada berkas APK yang diterima."));
            announce(TeksUI.t("Tidak ada berkas APK yang diterima."));
            return;
        }
        namaBerkas = namaDariUri(uriApk);
        new Thread(new Runnable() {
            @Override
            public void run() {
                salinDanPeriksa();
            }
        }, "PemasangSalin").start();
    }

    private String namaDariUri(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String n = c.getString(0);
                        if (n != null && n.length() > 0) return n;
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignored) {
        }
        String p = uri.getLastPathSegment();
        return p == null || p.length() == 0 ? "aplikasi.apk" : p;
    }

    /** Salin berkas ke cache privat lalu periksa isinya (APK tunggal atau bundel). */
    private void salinDanPeriksa() {
        String galat = null;
        try {
            File dir = new File(getCacheDir(), "pemasang");
            if (!dir.exists()) dir.mkdirs();
            String namaAman = namaBerkas.replaceAll("[^A-Za-z0-9._-]", "_");
            berkasSalinan = new File(dir, namaAman);
            InputStream in = getContentResolver().openInputStream(uriApk);
            if (in == null) throw new java.io.IOException("Tidak bisa membaca berkas.");
            OutputStream out = new FileOutputStream(berkasSalinan);
            try {
                byte[] buf = new byte[65536];
                int n;
                long total = 0;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
                ukuranBerkas = total;
            } finally {
                try { in.close(); } catch (Throwable ignored) {
                }
                try { out.close(); } catch (Throwable ignored) {
                }
            }
            adalahBundel = PembacaBundel.adalahBundel(namaBerkas);
            if (adalahBundel) {
                formatBundel = PembacaBundel.formatDariNama(namaBerkas);
                dirBundel = new File(getCacheDir(), "bundel");
                PembacaBundel.Bundel bundel = PembacaBundel.baca(berkasSalinan, dirBundel);
                berkasBagian = bundel.apk;
                berkasObb = bundel.obb;
                ukuranTotal = bundel.ukuranTotal;
                // Semua bagian harus satu paket yang sama; bagian pertama = base.
                infoArsip = bacaArsip(berkasBagian.get(0).getAbsolutePath());
                if (infoArsip == null) {
                    throw new java.io.IOException("Berkas APK utama di bundel rusak.");
                }
                namaPaket = infoArsip.packageName;
                for (int i = 1; i < berkasBagian.size(); i++) {
                    PackageInfo pi = bacaArsip(berkasBagian.get(i).getAbsolutePath());
                    if (pi == null || !namaPaket.equals(pi.packageName)) {
                        throw new java.io.IOException("Semua bagian bundel harus dari paket yang sama.");
                    }
                }
                if (bundel.paketDariManifest.length() > 0
                        && !bundel.paketDariManifest.equals(namaPaket)) {
                    throw new java.io.IOException("Paket di manifest bundel berbeda dengan isi APK.");
                }
            } else {
                if (!namaAman.toLowerCase(Locale.US).endsWith(".apk")) {
                    throw new java.io.IOException("Berkas bukan APK atau bundel yang dikenal.");
                }
                infoArsip = bacaArsip(berkasSalinan.getAbsolutePath());
                if (infoArsip == null) {
                    throw new java.io.IOException("Berkas bukan APK yang sah atau rusak.");
                }
                namaPaket = infoArsip.packageName;
                berkasBagian.clear();
                berkasBagian.add(berkasSalinan);
                berkasObb.clear();
                ukuranTotal = ukuranBerkas;
            }
        } catch (final Throwable t) {
            galat = pesanGalat(t.getMessage() == null ? t.toString() : t.getMessage());
        }
        final String galatAkhir = galat;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (galatAkhir != null) {
                    tvInfo.setText(TeksUI.t("Berkas APK:") + " " + namaBerkas);
                    tampilkanHasil(TeksUI.t("APK tidak bisa dipasang:") + " " + galatAkhir);
                } else {
                    tampilkanInfo();
                }
            }
        });
    }

    /**
     * Terjemahkan pesan galat pemeriksaan berkas ke bahasa aktif.
     * Galat sistem lain (mis. IO) tampil apa adanya sebagai diagnosis.
     */
    private String pesanGalat(String mentah) {
        if (mentah == null) return "";
        if (mentah.equals("Tidak bisa membaca berkas.")) {
            return TeksUI.t("Tidak bisa membaca berkas.");
        }
        if (mentah.equals("Berkas bukan APK yang sah atau rusak.")) {
            return TeksUI.t("Berkas bukan APK yang sah atau rusak.");
        }
        if (mentah.equals("Berkas bukan APK atau bundel yang dikenal.")) {
            return TeksUI.t("Berkas bukan APK atau bundel yang dikenal.");
        }
        if (mentah.equals("Berkas APK utama di bundel rusak.")) {
            return TeksUI.t("Berkas APK utama di bundel rusak.");
        }
        if (mentah.equals("Semua bagian bundel harus dari paket yang sama.")) {
            return TeksUI.t("Semua bagian bundel harus dari paket yang sama.");
        }
        if (mentah.equals("Paket di manifest bundel berbeda dengan isi APK.")) {
            return TeksUI.t("Paket di manifest bundel berbeda dengan isi APK.");
        }
        if (mentah.equals("Bundel tidak berisi berkas APK.")) {
            return TeksUI.t("Bundel tidak berisi berkas APK.");
        }
        if (mentah.startsWith("Entri bundel tidak aman")) {
            return TeksUI.t("Entri bundel tidak aman.");
        }
        if (mentah.equals("Berkas bundel tidak ditemukan.")) {
            return TeksUI.t("Berkas bundel tidak ditemukan.");
        }
        return mentah;
    }

    private PackageInfo bacaArsip(String path) {
        try {
            int bendera = PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS;
            if (Build.VERSION.SDK_INT >= 28) {
                bendera |= PackageManager.GET_SIGNING_CERTIFICATES;
            } else {
                bendera |= PackageManager.GET_SIGNATURES;
            }
            return getPackageManager().getPackageArchiveInfo(path, bendera);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- Pemeriksaan keamanan ----------------

    private void tampilkanInfo() {
        StringBuilder info = new StringBuilder();
        info.append(TeksUI.t("Berkas APK:")).append(" ").append(namaBerkas);
        if (ukuranBerkas > 0) {
            info.append(" (").append(ukuranTeks(ukuranBerkas)).append(")");
        }
        if (adalahBundel) {
            info.append("\n").append(TeksUI.t("Paket bundel:")).append(" ").append(formatBundel);
            info.append("\n").append(TeksUI.t("Jumlah bagian:")).append(" ").append(berkasBagian.size());
            for (int i = 0; i < berkasBagian.size(); i++) {
                File bagian = berkasBagian.get(i);
                info.append("\n- ").append(bagian.getName())
                        .append(" (").append(ukuranTeks(bagian.length())).append(")");
            }
            if (!berkasObb.isEmpty()) {
                info.append("\n").append(TeksUI.t("Berkas OBB:")).append(" ").append(berkasObb.size());
                for (int i = 0; i < berkasObb.size(); i++) {
                    info.append("\n- ").append(berkasObb.get(i).nama);
                }
            }
        }
        String label = "";
        try {
            ApplicationInfo ai = infoArsip.applicationInfo;
            ai.sourceDir = berkasSalinan.getAbsolutePath();
            ai.publicSourceDir = berkasSalinan.getAbsolutePath();
            CharSequence l = ai.loadLabel(getPackageManager());
            if (l != null) label = l.toString();
        } catch (Throwable ignored) {
        }
        if (label.length() > 0) info.append("\n").append(TeksUI.t("Nama aplikasi:")).append(" ").append(label);
        info.append("\n").append(TeksUI.t("Paket:")).append(" ").append(namaPaket);
        if (infoArsip.versionName != null) {
            info.append("\n").append(TeksUI.t("Versi:")).append(" ").append(infoArsip.versionName);
        }
        info.append(" (").append(TeksUI.t("kode")).append(" ").append(kodeVersi(infoArsip)).append(")");
        try {
            ApplicationInfo ai = infoArsip.applicationInfo;
            info.append("\n").append(TeksUI.t("Android minimal:")).append(" ")
                    .append("API ").append(ai.minSdkVersion);
            if (Build.VERSION.SDK_INT >= 24) {
                info.append(", ").append(TeksUI.t("target:")).append(" API ")
                        .append(ai.targetSdkVersion);
            }
        } catch (Throwable ignored) {
        }
        String sumber = sumberPengirim();
        info.append("\n").append(TeksUI.t("Sumber:")).append(" ").append(sumber);
        tvInfo.setText(info.toString());

        StringBuilder hasil = new StringBuilder();
        // Tanda tangan.
        boolean adaTanda = adaTandaTangan(infoArsip);
        hasil.append(adaTanda ? TeksUI.t("Sudah ditandatangani.")
                : TeksUI.t("PERINGATAN: APK ini tidak ada tanda tangannya."));
        // Sudah terpasang?
        try {
            PackageInfo terpasang = getPackageManager().getPackageInfo(namaPaket, 0);
            hasil.append("\n").append(TeksUI.t("Aplikasi ini sudah terpasang (versi"))
                    .append(" ").append(terpasang.versionName).append(").");
            if (!adaTanda) {
                hasil.append(" ").append(TeksUI.t("Pemasangan kemungkinan gagal."));
            } else if (!tandaSama(infoArsip, terpasang)) {
                hasil.append("\n").append(TeksUI.t("PERINGATAN: tanda tangan APK ini BERBEDA dengan aplikasi yang sudah terpasang. Android akan menolak pemasangan kecuali aplikasi lama dihapus dulu."));
            } else {
                hasil.append(" ").append(TeksUI.t("Tanda tangan cocok, bisa dipasang menimpa."));
            }
        } catch (PackageManager.NameNotFoundException e) {
            hasil.append("\n").append(TeksUI.t("Belum terpasang di perangkat ini."));
        } catch (Throwable ignored) {
        }
        // Izin berbahaya: gabungan semua bagian (bundel bisa meminta izin berbeda per bagian).
        List<String> berbahaya = new ArrayList<String>();
        for (int i = 0; i < berkasBagian.size(); i++) {
            PackageInfo pi = i == 0 ? infoArsip
                    : bacaArsip(berkasBagian.get(i).getAbsolutePath());
            List<String> bagian = izinBerbahaya(pi);
            for (int j = 0; j < bagian.size(); j++) {
                if (!berbahaya.contains(bagian.get(j))) berbahaya.add(bagian.get(j));
            }
        }
        if (!berbahaya.isEmpty()) {
            hasil.append("\n").append(TeksUI.t("PERINGATAN: APK ini meminta izin berbahaya:"));
            for (int i = 0; i < berbahaya.size(); i++) {
                hasil.append("\n- ").append(berbahaya.get(i));
            }
            hasil.append("\n").append(TeksUI.t("Periksa lagi sebelum memasang."));
        } else {
            hasil.append("\n").append(TeksUI.t("Tidak meminta izin berbahaya."));
        }
        hasil.append("\n").append(TeksUI.t("Hati-hati: pasang hanya APK dari pengirim yang Anda percaya."));
        if (!LayananAdbOtomatis.apakahTerpasang(this)) {
            hasil.append("\n").append(TeksUI.t("Pasang lewat adb belum bisa: pairing debugging nirkabel belum tersimpan. Pasangkan dulu di layar Debugger."));
        }
        tampilkanHasil(hasil.toString());
        bPasang.setEnabled(true);
        bPasangAdb.setEnabled(true);
        tampilkanDialogPasang(info.toString(), hasil.toString());
    }

    /**
     * Dialog pemasangan: "Anda ingin menginstall aplikasi ini" dengan
     * tombol Batal dan Install di sebelahnya. Tanpa izin dulu — izin
     * pasang hanya diminta bila jalur adb tidak tersedia dan pengguna
     * sudah menekan Install.
     */
    private void tampilkanDialogPasang(String info, String hasil) {
        String pesan = info + "\n\n" + hasil;
        if (!LayananAdbOtomatis.apakahTerpasang(this)) {
            pesan += "\n" + TeksUI.t("Catatan: pairing debugging nirkabel belum tersimpan, jadi pemasangan ini dicatat atas nama installer ini, bukan Play Store.");
        }
        announce(TeksUI.t("Anda ingin menginstall aplikasi ini") + ". " + pesan);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Anda ingin menginstall aplikasi ini"))
                .setMessage(pesan)
                .setPositiveButton(TeksUI.t("Install"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        mulaiInstalasi();
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        finish();
                    }
                })
                .show();
    }

    /**
     * "Install" ditekan: tampilkan baris "sedang memasang aplikasi"
     * dengan tombol Batalkan di sebelahnya. Begitu pemasangan mulai
     * berjalan, tombol Batalkan dimatikan — pemasangan yang sudah
     * berjalan tidak bisa dibatalkan. Jalur utama: adb (catatan
     * pemasang Play Store); bila pairing belum tersimpan, jatuh ke
     * PackageInstaller (di situ izin pasang diminta bila belum ada).
     */
    private void mulaiInstalasi() {
        if (berkasSalinan == null || !berkasSalinan.exists() || namaPaket == null) {
            toast(TeksUI.t("Berkas APK belum siap."));
            return;
        }
        sedangMemasang = true;
        barisSukses.setVisibility(View.GONE);
        bIzin.setVisibility(View.GONE);
        tvStatus.setText(TeksUI.t("sedang memasang aplikasi"));
        bBatalkan.setEnabled(false);
        barisMemasang.setVisibility(View.VISIBLE);
        announce(TeksUI.t("sedang memasang aplikasi") + ". "
                + TeksUI.t("Pemasangan sudah berjalan, tidak bisa dibatalkan."));
        if (LayananAdbOtomatis.apakahTerpasang(this)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    pasangLewatAdb();
                }
            }, "PemasangAdb").start();
        } else {
            mulaiPasang();
        }
    }

    /** Tombol Batalkan: hanya bisa sebelum pemasangan mulai berjalan. */
    private void batalkanMemasang() {
        if (sedangMemasang) {
            announce(TeksUI.t("Pemasangan sudah berjalan, tidak bisa dibatalkan."));
            toast(TeksUI.t("Pemasangan sudah berjalan, tidak bisa dibatalkan."));
            return;
        }
        finish();
    }

    /** Layar sukses: toast + tombol Buka dan Selesai. */
    private void tampilkanSukses() {
        tampilkanSukses(null);
    }

    private void tampilkanSukses(String catatan) {
        sedangMemasang = false;
        sudahPasang = true;
        barisMemasang.setVisibility(View.GONE);
        barisSukses.setVisibility(View.VISIBLE);
        bPasang.setEnabled(false);
        bPasangAdb.setEnabled(false);
        Toast.makeText(this, TeksUI.t("aplikasi berhasil diinstall"),
                Toast.LENGTH_LONG).show();
        String t = TeksUI.t("APK berhasil dipasang:") + " " + namaPaket + ".";
        if (catatan != null && catatan.length() > 0) t += " " + catatan;
        tampilkanHasil(t);
        announce(TeksUI.t("aplikasi berhasil diinstall"));
        hapusSalinan();
    }

    /** Pemasangan gagal: sembunyikan baris pemasangan, tampilkan tombol coba lagi. */
    private void tampilkanGagal(final String pesanTampil) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sedangMemasang = false;
                barisMemasang.setVisibility(View.GONE);
                bPasang.setEnabled(true);
                bPasangAdb.setEnabled(true);
                bPasang.setVisibility(View.VISIBLE);
                bPasangAdb.setVisibility(View.VISIBLE);
                tampilkanHasil(pesanTampil);
            }
        });
    }

    private long kodeVersi(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return info.getLongVersionCode();
            } catch (Throwable ignored) {
            }
        }
        return info.versionCode;
    }

    private boolean adaTandaTangan(PackageInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return info.signingInfo != null && info.signingInfo.getApkContentsSigners() != null
                        && info.signingInfo.getApkContentsSigners().length > 0;
            }
            return info.signatures != null && info.signatures.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean tandaSama(PackageInfo arsip, PackageInfo terpasang) {
        try {
            byte[] a = tandaPertama(arsip);
            byte[] b = tandaPertama(terpasang);
            if (a == null || b == null) return false;
            if (a.length != b.length) return false;
            for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private byte[] tandaPertama(PackageInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                if (info.signingInfo == null) return null;
                android.content.pm.Signature[] s = info.signingInfo.getApkContentsSigners();
                if (s == null || s.length == 0) return null;
                return s[0].toByteArray();
            }
            if (info.signatures == null || info.signatures.length == 0) return null;
            return info.signatures[0].toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Izin yang diminta APK dengan perlindungan berbahaya. */
    private List<String> izinBerbahaya(PackageInfo info) {
        List<String> hasil = new ArrayList<String>();
        if (info == null || info.requestedPermissions == null) return hasil;
        for (int i = 0; i < info.requestedPermissions.length; i++) {
            String nama = info.requestedPermissions[i];
            try {
                PermissionInfo pi = getPackageManager().getPermissionInfo(nama, 0);
                int tingkat;
                if (Build.VERSION.SDK_INT >= 28) {
                    tingkat = pi.getProtection();
                } else {
                    tingkat = pi.protectionLevel;
                }
                if (tingkat == PermissionInfo.PROTECTION_DANGEROUS) {
                    hasil.add(nama);
                }
            } catch (Throwable ignored) {
            }
        }
        return hasil;
    }

    /** Paket aplikasi pengirim (dari referrer), atau "tidak diketahui". */
    private String sumberPengirim() {
        try {
            Uri rujuk = getReferrer();
            if (rujuk != null) {
                String s = rujuk.toString();
                if (s.startsWith("android-app://")) {
                    String paket = s.substring("android-app://".length());
                    int miring = paket.indexOf('/');
                    if (miring >= 0) paket = paket.substring(0, miring);
                    if (paket.length() > 0) return paket;
                }
                return s;
            }
        } catch (Throwable ignored) {
        }
        return TeksUI.t("tidak diketahui");
    }

    private String ukuranTeks(long bita) {
        if (bita < 1024) return bita + " B";
        if (bita < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bita / 1024.0);
        return String.format(Locale.US, "%.1f MB", bita / 1048576.0);
    }

    private void tampilkanHasil(String teks) {
        tvHasil.setText(teks);
        announce(teks);
    }

    // ---------------- Izin pasang ----------------

    private void periksaIzinPasang() {
        boolean boleh = false;
        try {
            boleh = getPackageManager().canRequestPackageInstalls();
        } catch (Throwable ignored) {
        }
        bIzin.setVisibility(boleh ? View.GONE : View.VISIBLE);
        if (!boleh) {
            tampilkanHasil(tvHasil.getText().toString() + "\n"
                    + TeksUI.t("Izin pasang dari sumber tak dikenal belum diberikan. Tekan tombol pengaturan izin pasang, aktifkan Izinkan dari sumber ini, lalu kembali ke sini."));
        }
    }

    private void bukaPengaturanIzin() {
        try {
            Intent it = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            it.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(it, MINTA_IZIN_PASANG);
        } catch (Throwable t) {
            toast(TeksUI.t("Tidak bisa membuka pengaturan izin pasang."));
        }
    }

    // ---------------- Pemasangan ----------------

    /** Jalur PackageInstaller (dipakai bila pairing adb belum tersimpan). */
    private void mulaiPasang() {
        if (berkasSalinan == null || !berkasSalinan.exists() || namaPaket == null) {
            toast(TeksUI.t("Berkas APK belum siap."));
            return;
        }
        boolean boleh = false;
        try {
            boleh = getPackageManager().canRequestPackageInstalls();
        } catch (Throwable ignored) {
        }
        if (!boleh) {
            // Izin baru diminta di sini — sesudah pengguna menekan Install,
            // bukan sebelum dialog pemasangan.
            sedangMemasang = false;
            barisMemasang.setVisibility(View.GONE);
            toast(TeksUI.t("Aktifkan dulu izin pasang dari sumber tak dikenal."));
            periksaIzinPasang();
            bPasang.setVisibility(View.VISIBLE);
            bPasangAdb.setVisibility(View.VISIBLE);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                pasangLewatSesi();
            }
        }, "PemasangPasang").start();
    }

    private void pasangLewatSesi() {
        String galat = null;
        try {
            PackageInstaller pemasang = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams param = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    param.setAppPackageName(namaPaket);
                } catch (Throwable ignored) {
                }
            }
            int sesiId = pemasang.createSession(param);
            PackageInstaller.Session sesi = pemasang.openSession(sesiId);
            try {
                // Satu sesi untuk semua bagian (bundel) atau satu APK.
                for (int i = 0; i < berkasBagian.size(); i++) {
                    File bagian = berkasBagian.get(i);
                    OutputStream out = sesi.openWrite("bagian" + i, 0, bagian.length());
                    InputStream in = new java.io.FileInputStream(bagian);
                    try {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                        sesi.fsync(out);
                    } finally {
                        try { in.close(); } catch (Throwable ignored) {
                        }
                        try { out.close(); } catch (Throwable ignored) {
                        }
                    }
                }
                Intent status = new Intent(AKSI_STATUS);
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                        PemasangApkActivity.this, sesiId, status,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_MUTABLE);
                sesi.commit(pi.getIntentSender());
            } finally {
                try { sesi.close(); } catch (Throwable ignored) {
                }
            }
        } catch (final Throwable t) {
            galat = t.getMessage() == null ? t.toString() : t.getMessage();
            tampilkanGagal(TeksUI.t("Gagal memasang APK:") + " " + galat);
        }
    }

    private void daftarPenerimaStatus() {
        penerimaStatus = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                String pesan = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent aksi = (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (aksi != null) {
                        try {
                            startActivity(aksi);
                        } catch (Throwable t) {
                            tampilkanHasil(TeksUI.t("Gagal menampilkan dialog konfirmasi pemasangan."));
                        }
                    }
                } else if (status == PackageInstaller.STATUS_SUCCESS) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final String catatanObb = salinObb();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tampilkanSukses(catatanObb);
                                }
                            });
                        }
                    }, "PemasangObb").start();
                } else {
                    tampilkanGagal(TeksUI.t("Gagal memasang APK:") + " "
                            + (pesan == null ? "" : pesan));
                }
            }
        };
        IntentFilter f = new IntentFilter(AKSI_STATUS);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(penerimaStatus, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(penerimaStatus, f);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bukaAplikasi() {
        if (namaPaket == null) return;
        try {
            Intent buka = getPackageManager().getLaunchIntentForPackage(namaPaket);
            if (buka != null) {
                startActivity(buka);
            } else {
                toast(TeksUI.t("Aplikasi ini tidak punya layar utama untuk dibuka."));
            }
        } catch (Throwable t) {
            toast(TeksUI.t("Tidak bisa membuka aplikasi yang dipasang."));
        }
    }

    // ---------------- Pemasangan lewat adb (catatan pemasang Play Store) ----------------

    /**
     * Salin APK ke cache luar (bisa dibaca pengguna shell), sambung adb
     * nirkabel dengan kunci tersimpan, lalu "pm install -i
     * com.android.vending -r <path>". Flag -i hanya mengubah catatan
     * pemasang; aplikasi tetap bukan dari Play Store.
     */
    private void pasangLewatAdb() {
        try {
            File luar = getExternalCacheDir();
            if (luar == null) throw new java.io.IOException("Penyimpanan luar tidak tersedia.");
            File dir = new File(luar, "pemasang");
            if (!dir.exists()) dir.mkdirs();
            berkasLuarBagian.clear();
            for (int i = 0; i < berkasBagian.size(); i++) {
                File bagian = berkasBagian.get(i);
                File salinanLuar = new File(dir, bagian.getName());
                salinBerkas(bagian, salinanLuar);
                berkasLuarBagian.add(salinanLuar);
            }
            berkasLuar = berkasLuarBagian.isEmpty() ? null : berkasLuarBagian.get(0);
            final AdbKlien[] pemegang = new AdbKlien[1];
            final String[] keluaran = new String[1];
            final Throwable[] galat = new Throwable[1];
            final Object kunciSelesai = new Object();
            AdbMdns.cari(this, AdbMdns.TIPE_SAMBUNG, 12000, new AdbMdns.Pendengar() {
                @Override
                public void ketemu(String host, int port) {
                    if (adalahBundel) {
                        jalankanPmBundel(port, new String[]{"127.0.0.1", host},
                                pemegang, keluaran, galat, kunciSelesai);
                    } else {
                        String jalur = berkasLuarBagian.get(0).getAbsolutePath()
                                .replace("'", "'\\''");
                        jalankanPm(port, new String[]{"127.0.0.1", host},
                                jalur, pemegang, keluaran, galat, kunciSelesai);
                    }
                }

                @Override
                public void selesai(String g) {
                    if (adalahBundel) {
                        jalankanPmBundel(5555, new String[]{"127.0.0.1"},
                                pemegang, keluaran, galat, kunciSelesai);
                    } else {
                        String jalur = berkasLuarBagian.get(0).getAbsolutePath()
                                .replace("'", "'\\''");
                        jalankanPm(5555, new String[]{"127.0.0.1"},
                                jalur, pemegang, keluaran, galat, kunciSelesai);
                    }
                }
            });
            synchronized (kunciSelesai) {
                kunciSelesai.wait(180000);
            }
            if (pemegang[0] != null) {
                pemegang[0].tutup();
            }
            if (galat[0] != null) throw galat[0];
            final String hasil = keluaran[0] == null ? "" : keluaran[0];
            if (hasil.contains("Success")) {
                final String catatan = TeksUI.t("Di Info aplikasi akan tertulis terinstal dari Google Play Store. Itu hanya label pemasang — aplikasinya tetap bukan dari Play Store.")
                        + " " + salinObb();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tampilkanSukses(catatan.trim());
                    }
                });
            } else {
                tampilkanGagal(TeksUI.t("Gagal memasang APK lewat adb:") + " " + hasil);
            }
        } catch (final Throwable t) {
            tampilkanGagal(TeksUI.t("Gagal memasang APK lewat adb:") + " "
                    + (t.getMessage() == null ? t.toString() : t.getMessage()));
        }
    }

    /**
     * Pasang bundel lewat adb: "cmd package install-create -S <total>
     * -i com.android.vending -r", lalu install-write per bagian, lalu
     * install-commit — semua dalam satu sesi, satu aplikasi utuh.
     */
    private void jalankanPmBundel(final int port, final String[] hostCoba,
                                  final AdbKlien[] pemegang, final String[] keluaran,
                                  final Throwable[] galat, final Object kunciSelesai) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(getFilesDir(), "adb");
                    dir.mkdirs();
                    AdbKunci kunci = AdbKunci.muatAtauBuat(dir);
                    AdbKlien klien = new AdbKlien();
                    Throwable terakhir = null;
                    for (int i = 0; i < hostCoba.length; i++) {
                        try {
                            klien.sambungTls(hostCoba[i], port, kunci, 5000);
                            terakhir = null;
                            break;
                        } catch (Throwable e) {
                            terakhir = e;
                        }
                    }
                    if (terakhir != null) throw terakhir;
                    pemegang[0] = klien;
                    String buat = klien.jalankanShell("cmd package install-create -S "
                            + ukuranTotal + " -i com.android.vending -r");
                    long sesi = PembacaBundel.parseIdSesi(buat);
                    if (sesi < 0) {
                        keluaran[0] = buat;
                        return;
                    }
                    for (int i = 0; i < berkasLuarBagian.size(); i++) {
                        String jalur = berkasLuarBagian.get(i).getAbsolutePath()
                                .replace("'", "'\\''");
                        String tulis = klien.jalankanShell("cmd package install-write "
                                + sesi + " bagian" + i + " '" + jalur + "'");
                        if (tulis == null || !tulis.contains("Success")) {
                            keluaran[0] = tulis == null ? "" : tulis;
                            return;
                        }
                    }
                    keluaran[0] = klien.jalankanShell(
                            "cmd package install-commit " + sesi);
                } catch (Throwable t) {
                    galat[0] = t;
                } finally {
                    synchronized (kunciSelesai) {
                        kunciSelesai.notifyAll();
                    }
                }
            }
        }, "PemasangPmBundel").start();
    }

    /**
     * Salin berkas OBB bundel ke /sdcard/Android/obb/<paket>/.
     * Mengembalikan catatan hasil ("" bila tidak ada OBB).
     */
    private String salinObb() {
        if (berkasObb.isEmpty()) return "";
        try {
            if (Build.VERSION.SDK_INT >= 30
                    && !android.os.Environment.isExternalStorageManager()) {
                return TeksUI.t("Berkas OBB tidak disalin: izin akses semua berkas belum diberikan.");
            }
            File obbDir = new File(android.os.Environment.getExternalStorageDirectory(),
                    "Android/obb/" + namaPaket);
            if (!obbDir.exists()) obbDir.mkdirs();
            int ok = 0;
            for (int i = 0; i < berkasObb.size(); i++) {
                PembacaBundel.Obb o = berkasObb.get(i);
                salinBerkas(o.berkas, new File(obbDir, o.nama));
                ok++;
            }
            return TeksUI.t("Berkas OBB disalin:") + " " + ok + ".";
        } catch (Throwable t) {
            return TeksUI.t("Berkas OBB gagal disalin:")
                    + " " + (t.getMessage() == null ? "" : t.getMessage());
        }
    }

    /** Sambung TLS ke adbd lalu jalankan pm install; beri tahu lewat kunciSelesai. */
    private void jalankanPm(final int port, final String[] hostCoba, final String jalur,
                            final AdbKlien[] pemegang, final String[] keluaran,
                            final Throwable[] galat, final Object kunciSelesai) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(getFilesDir(), "adb");
                    dir.mkdirs();
                    AdbKunci kunci = AdbKunci.muatAtauBuat(dir);
                    AdbKlien klien = new AdbKlien();
                    Throwable terakhir = null;
                    for (int i = 0; i < hostCoba.length; i++) {
                        try {
                            klien.sambungTls(hostCoba[i], port, kunci, 5000);
                            terakhir = null;
                            break;
                        } catch (Throwable e) {
                            terakhir = e;
                        }
                    }
                    if (terakhir != null) throw terakhir;
                    pemegang[0] = klien;
                    keluaran[0] = klien.jalankanShell(
                            "pm install -i com.android.vending -r '" + jalur + "'");
                } catch (Throwable t) {
                    galat[0] = t;
                } finally {
                    synchronized (kunciSelesai) {
                        kunciSelesai.notifyAll();
                    }
                }
            }
        }, "PemasangPm").start();
    }

    private void salinBerkas(File dari, File ke) throws java.io.IOException {
        InputStream in = new java.io.FileInputStream(dari);
        OutputStream out = new FileOutputStream(ke);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (Throwable ignored) {
            }
            try { out.close(); } catch (Throwable ignored) {
            }
        }
    }

    private void hapusSalinan() {
        try {
            if (berkasSalinan != null && berkasSalinan.exists()) berkasSalinan.delete();
        } catch (Throwable ignored) {
        }
        try {
            if (berkasLuar != null && berkasLuar.exists()) berkasLuar.delete();
        } catch (Throwable ignored) {
        }
        try {
            for (int i = 0; i < berkasLuarBagian.size(); i++) {
                File f = berkasLuarBagian.get(i);
                if (f != null && f.exists()) f.delete();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (dirBundel != null && dirBundel.exists()) {
                PembacaBundel.hapusRekursif(dirBundel);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------- Utilitas tampilan ----------------

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
