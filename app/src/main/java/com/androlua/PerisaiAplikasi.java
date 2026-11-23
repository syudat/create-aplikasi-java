package com.androlua;

import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Debug;
import android.view.WindowManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Kelas Application Perisai Anti Tamper untuk APLIKASI HASIL BUILD.
 *
 * Kelas ini ikut di dalam setiap APK hasil (bagian dari kerangka), tetapi
 * hanya menjadi kelas Application bila proyek dibangun dengan anti tamper
 * aktif: BangunLokal mengganti nama Application pada manifest hasil dari
 * com.androlua.Aplikasi menjadi com.androlua.PerisaiAplikasi, dan
 * Penandatangan menulis catatan "assets/perisai.dat" (hash sertifikat
 * penandatangan yang sah + CRC32 semua dex) saat APK ditandatangani.
 *
 * Pemeriksaan saat aplikasi dibuka (dokumen RASP al kautsar):
 * 1) sertifikat tanda tangan yang sedang berjalan vs catatan,
 * 2) checksum classes*.dex vs catatan,
 * 3) debugger menempel (Debug + TracerPid di /proc/self/status),
 * 4) jejak injeksi Frida/Xposed di /proc/self/maps,
 * 5) FLAG_SECURE pada semua jendela (anti rekam layar / anti tapjacking).
 * Bila ada yang tidak cocok, aplikasi menolak berjalan (proses dimatikan),
 * persis seperti yang diminta: aplikasi yang ditandatangani ulang atau
 * diubah orang lain akan berhenti.
 */
public class PerisaiAplikasi extends Aplikasi {

    @Override
    public void onCreate() {
        super.onCreate();
        if (!periksa()) {
            berhenti();
            return;
        }
        kunciLayar();
    }

    /** Jalankan semua pemeriksaan. False = ada gangguan / catatan tidak sah. */
    private boolean periksa() {
        try {
            Perisai.Data data = bacaCatatan();
            if (data == null) return false; // anti tamper aktif tapi catatan hilang = tidak sah
            if (!periksaSertifikat(data)) return false;
            if (!periksaDex(data)) return false;
            if (!periksaDebugger()) return false;
            if (!periksaHook()) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Perisai.Data bacaCatatan() {
        InputStream in = null;
        try {
            in = getAssets().open("perisai.dat");
            byte[] b = Perisai.bacaSemua(in);
            return Perisai.uraiData(new String(b, "UTF-8"));
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private boolean periksaSertifikat(Perisai.Data data) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(
                    getPackageName(), PackageManager.GET_SIGNATURES);
            if (pi == null || pi.signatures == null || pi.signatures.length == 0) return false;
            String hash = Perisai.sha256Hex(pi.signatures[0].toByteArray());
            return Perisai.sertifikatCocok(data, hash);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean periksaDex(Perisai.Data data) {
        ZipFile zf = null;
        try {
            String sumber = getApplicationInfo().sourceDir;
            if (sumber == null) return false;
            zf = new ZipFile(new File(sumber));
            Map<String, Long> aktual = new HashMap<String, Long>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (Perisai.namaDex(e.getName())) {
                    aktual.put(e.getName(), Perisai.crc32Entri(zf, e));
                }
            }
            return Perisai.dexCocok(data, aktual);
        } catch (Throwable t) {
            return false;
        } finally {
            if (zf != null) {
                try { zf.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private boolean periksaDebugger() {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false;
            String status = bacaBerkasProc("/proc/self/status");
            int tracer = Perisai.tracerPid(status);
            return tracer <= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean periksaHook() {
        try {
            String maps = bacaBerkasProc("/proc/self/maps");
            return Perisai.jejakHook(maps) == null;
        } catch (Throwable t) {
            return false;
        }
    }

    private String bacaBerkasProc(String jalur) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(jalur);
            return new String(Perisai.bacaSemua(in), "UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) { }
            }
        }
    }

    /** Pasang FLAG_SECURE pada setiap Activity yang dibuat. */
    private void kunciLayar() {
        try {
            registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity a, Bundle b) {
                    try {
                        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onActivityStarted(Activity a) { }

                @Override
                public void onActivityResumed(Activity a) { }

                @Override
                public void onActivityPaused(Activity a) { }

                @Override
                public void onActivityStopped(Activity a) { }

                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) { }

                @Override
                public void onActivityDestroyed(Activity a) { }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Tolak berjalan: matikan proses aplikasi sekarang juga. */
    private void berhenti() {
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Throwable ignored) {
        }
        System.exit(0);
    }
}
