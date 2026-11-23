package com.androlua;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Configuration;
import android.os.Build;
import java.util.Locale;

/**
 * Application Java murni (Ronde 3).
 *
 * Menggantikan LuaApplication: hanya CrashHandler + pencatatan pembukaan
 * aplikasi (Analitik, lokal di HP). Semua inisialisasi direktori AndroLua
 * (luaExtDir, localDir, odexDir, libDir, luaMdDir, luaCpath, luaLpath),
 * getUriForPath/getUriForFile, dan getSharedPreferences milik mesin Lua
 * dibuang bersama LuaApplication — tidak ada lagi kode Lua.
 */
public class Aplikasi extends Application {

    /**
     * Bahasa aplikasi (perintah al kautsar 2026-11-23): terapkan bahasa
     * terpilih dari Pengaturan sebagai lokal konteks dasar. Hanya untuk
     * paket IDE sendiri — kelas ini juga menjadi kelas Application
     * aplikasi hasil build, dan di sana perilaku bawaan Android (ikuti
     * sistem) tidak diubah sama sekali.
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(bungkusBahasa(base));
    }

    private static Context bungkusBahasa(Context base) {
        try {
            if (base == null || !"com.pembuataplikasi".equals(base.getPackageName())) return base;
            String pilihan = PengaturanActivity.baca(base)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            String kode = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
            TeksUI.kodeAktif = kode;
            Locale lokal = new Locale(kode);
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.setLocale(lokal);
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            return base;
        }
    }

    /**
     * Hash SHA-256 sertifikat penanda tangan resmi Create Aplikasi
     * (keystore yang tersimpan di repo, alias debugkey). Dipakai oleh
     * pemeriksaan anti-tamper IDE di bawah.
     */
    private static final String HASH_SERTIFIKAT_RESMI =
            "b8c16ab8faf3bc559b699b931cc79d9ec371fa24302318b5a0802a0b3c9d58ce";

    @Override
    public void onCreate() {
        super.onCreate();
        if (!periksaTandaTanganSendiri()) {
            // Ditandatangani ulang oleh pihak lain: tolak berjalan.
            try {
                android.widget.Toast.makeText(this,
                        TeksUI.t("Aplikasi ini telah diubah atau ditandatangani ulang oleh pihak lain, sehingga tidak dapat dijalankan."),
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                // abaikan: tetap berhenti di bawah
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
            return;
        }
        if (!periksaKeutuhanString()) {
            // String/panduan diubah oleh pihak lain: tolak berjalan.
            try {
                android.widget.Toast.makeText(this,
                        TeksUI.t("Aplikasi ini telah diubah sehingga tidak dapat dijalankan."),
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                // abaikan: tetap berhenti di bawah
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
            return;
        }
        CrashHandler crashHandler = CrashHandler.getInstance();
        // Daftarkan crashHandler
        crashHandler.init(getApplicationContext());
        // Analitik + App Quality Insights: catat satu pembukaan aplikasi
        // (lokal di HP, tidak dikirim ke mana pun).
        AnalitikActivity.catatBuka(getApplicationContext());
    }

    /**
     * Anti-tamper IDE (perintah al kautsar 2026-11-23: aplikasi dienkripsi /
     * dilindungi supaya tidak dikembangkan ulang oleh orang jahat).
     * Hanya berlaku untuk paket IDE sendiri; aplikasi hasil build tidak
     * terpengaruh (mereka punya PerisaiAplikasi sendiri bila anti tamper
     * dicentang). Arah gagal yang aman: bila sertifikat tidak dapat dibaca
     * sama sekali, aplikasi TETAP berjalan — yang ditolak hanya sertifikat
     * yang terbaca dan jelas berbeda dari sertifikat resmi.
     */
    private boolean periksaTandaTanganSendiri() {
        try {
            if (!"com.pembuataplikasi".equals(getPackageName())) return true;
            byte[] sertifikat = ambilSertifikatSendiri();
            if (sertifikat == null) return true;
            return HASH_SERTIFIKAT_RESMI.equals(Perisai.sha256Hex(sertifikat));
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * DebugKausar (perintah al kautsar 2026-11-23 20:21 WITA): periksa
     * keutuhan string antarmuka (tabel TeksUI) dan berkas panduan di aset.
     * Hanya berlaku untuk paket IDE sendiri. Gagal AMAN: bila pemeriksaan
     * tidak bisa berjalan sama sekali (mis. aset tidak terbaca), aplikasi
     * TETAP berjalan — yang ditolak hanya ketidakcocokan yang terbukti
     * (satu huruf berubah di string atau di berkas panduan yang ada).
     */
    private boolean periksaKeutuhanString() {
        try {
            if (!"com.pembuataplikasi".equals(getPackageName())) return true;
            final android.content.res.AssetManager aset = getAssets();
            StringBuilder catatan = new StringBuilder();
            boolean ok = DebugKausar.periksaSemua(new DebugKausar.PenyediaPanduan() {
                public boolean ada(String namaBerkas) {
                    java.io.InputStream in = null;
                    try {
                        in = aset.open("panduan/" + namaBerkas);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    } finally {
                        if (in != null) try { in.close(); } catch (Throwable ignored) {}
                    }
                }
                public byte[] baca(String namaBerkas) {
                    java.io.InputStream in = null;
                    try {
                        in = aset.open("panduan/" + namaBerkas);
                        java.io.ByteArrayOutputStream bos =
                                new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
                        return bos.toByteArray();
                    } catch (Throwable t) {
                        return null;
                    } finally {
                        if (in != null) try { in.close(); } catch (Throwable ignored) {}
                    }
                }
            }, catatan);
            if (!ok) {
                try {
                    android.util.Log.w("DebugKausar", catatan.toString());
                } catch (Throwable ignored) {
                }
            }
            return ok;
        } catch (Throwable t) {
            return true;
        }
    }

    private byte[] ambilSertifikatSendiri() throws Exception {        PackageManager pm = getPackageManager();
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo pi = pm.getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo si = pi.signingInfo;
            if (si == null) return null;
            Signature[] sigs = si.hasMultipleSigners()
                    ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            if (sigs == null || sigs.length == 0) return null;
            return sigs[0].toByteArray();
        }
        PackageInfo pi = pm.getPackageInfo(getPackageName(),
                PackageManager.GET_SIGNATURES);
        if (pi.signatures == null || pi.signatures.length == 0) return null;
        return pi.signatures[0].toByteArray();
    }
}
