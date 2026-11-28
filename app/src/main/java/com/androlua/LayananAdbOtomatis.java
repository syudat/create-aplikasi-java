package com.androlua;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import java.io.File;

/**
 * Layanan latar sambung-ulang otomatis debugging nirkabel.
 *
 * Setelah pairing berhasil SEKALI, adbd menyimpan kunci RSA aplikasi
 * ini (AdbKunci, tersimpan di filesDir/adb) sebagai kunci yang
 * dipercaya — pairing TIDAK PERLU diulang. Layanan ini menjaga
 * sambungan adb tetap hidup di latar belakang: bila WiFi dimatikan
 * lalu dinyalakan lagi, atau HP restart (PenerimaBootAdb), layanan
 * mencari lagi layanan _adb-tls-connect._tcp lewat mDNS dan
 * menyambung TLS dengan kunci yang sama, tanpa kode pairing baru.
 *
 * JUJUR: koneksi debugging nirkabel tetap butuh WiFi MENYALA saat
 * dipakai (jalurnya lewat WiFi). Yang otomatis adalah: pairing
 * tersimpan + sambung ulang tanpa kode. Saat WiFi mati, notifikasi
 * menampilkan "Menunggu WiFi" dan layanan diam sampai WiFi menyala.
 *
 * Layanan ini hanya menjaga sambungan adb + notifikasi; debug
 * interaktif (daftar proses, titik henti, langkah, variabel) tetap
 * di DebuggerLangkahActivity dengan sambungan AdbKlien-nya sendiri.
 */
public class LayananAdbOtomatis extends Service {

    public static final String AKSI_MULAI = "com.androlua.ADB_OTOMATIS_MULAI";
    public static final String AKSI_BERHENTI = "com.androlua.ADB_OTOMATIS_BERHENTI";

    public static final String KUNCI_OTOMATIS = "debug_adb_otomatis";
    public static final String KUNCI_TERPASANG = "debug_adb_terpasang";

    private final AdbSambungUlang mesin = new AdbSambungUlang();
    private volatile boolean jalan = false;
    private Thread utas;
    private AdbKlien klien;
    private AdbKunci kunci;
    private ConnectivityManager.NetworkCallback callbackJaringan;

    /** Mulai layanan sambung-ulang otomatis. */
    public static void mulai(Context ctx) {
        try {
            Intent it = new Intent(ctx, LayananAdbOtomatis.class);
            it.setAction(AKSI_MULAI);
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(it);
            } else {
                ctx.startService(it);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Hentikan layanan sambung-ulang otomatis. */
    public static void berhenti(Context ctx) {
        try {
            Intent it = new Intent(ctx, LayananAdbOtomatis.class);
            it.setAction(AKSI_BERHENTI);
            ctx.startService(it);
        } catch (Throwable ignored) {
        }
    }

    public static boolean apakahOtomatis(Context ctx) {
        try {
            return PengaturanActivity.baca(ctx).getBoolean(KUNCI_OTOMATIS, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setOtomatis(Context ctx, boolean aktif) {
        try {
            SharedPreferences.Editor e = PengaturanActivity.baca(ctx).edit();
            e.putBoolean(KUNCI_OTOMATIS, aktif);
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean apakahTerpasang(Context ctx) {
        try {
            return PengaturanActivity.baca(ctx).getBoolean(KUNCI_TERPASANG, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setTerpasang(Context ctx, boolean terpasang) {
        try {
            SharedPreferences.Editor e = PengaturanActivity.baca(ctx).edit();
            e.putBoolean(KUNCI_TERPASANG, terpasang);
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && AKSI_BERHENTI.equals(intent.getAction())) {
            jalan = false;
            setOtomatis(this, false);
            tutupKlien();
            NotifikasiDebug.hapus(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (jalan) return START_STICKY;
        jalan = true;
        setOtomatis(this, true);
        mulaiForeground();
        daftarCallbackJaringan();
        mesin.aktifkan();
        utas = new Thread(new Runnable() {
            @Override
            public void run() {
                putaran();
            }
        }, "AdbOtomatis");
        utas.setDaemon(true);
        utas.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        jalan = false;
        tutupKlien();
        lepasCallbackJaringan();
        super.onDestroy();
    }

    private void mulaiForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NotifikasiDebug.NOTIF_ID,
                        NotifikasiDebug.bangun(this, NotifikasiDebug.MENUNGGU_SAMBUNG),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NotifikasiDebug.NOTIF_ID,
                        NotifikasiDebug.bangun(this, NotifikasiDebug.MENUNGGU_SAMBUNG));
            }
        } catch (Throwable t) {
            try {
                startForeground(NotifikasiDebug.NOTIF_ID,
                        NotifikasiDebug.bangun(this, NotifikasiDebug.MENUNGGU_SAMBUNG));
            } catch (Throwable ignored) {
            }
        }
    }

    private void daftarCallbackJaringan() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            NetworkRequest req = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            callbackJaringan = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mesin.wifiMenyala();
                    NotifikasiDebug.tampilkan(LayananAdbOtomatis.this,
                            NotifikasiDebug.MENUNGGU_SAMBUNG);
                }

                @Override
                public void onLost(Network network) {
                    mesin.wifiMati();
                    tutupKlien();
                    NotifikasiDebug.tampilkan(LayananAdbOtomatis.this,
                            NotifikasiDebug.MENUNGGU_WIFI);
                }
            };
            cm.registerNetworkCallback(req, callbackJaringan);
            // Keadaan WiFi saat ini: bila tidak ada jaringan WiFi aktif,
            // langsung masuk mode menunggu WiFi.
            try {
                Network aktif = cm.getActiveNetwork();
                NetworkCapabilities kap = aktif == null ? null
                        : cm.getNetworkCapabilities(aktif);
                boolean wifi = kap != null
                        && kap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                if (!wifi) {
                    mesin.wifiMati();
                    NotifikasiDebug.tampilkan(this, NotifikasiDebug.MENUNGGU_WIFI);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void lepasCallbackJaringan() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && callbackJaringan != null) {
                cm.unregisterNetworkCallback(callbackJaringan);
            }
        } catch (Throwable ignored) {
        }
        callbackJaringan = null;
    }

    private void tutupKlien() {
        try {
            if (klien != null) klien.tutup();
        } catch (Throwable ignored) {
        }
        klien = null;
    }

    /** Putaran utama: cari -> sambung -> pantau -> coba lagi. */
    private void putaran() {
        while (jalan) {
            try {
                int keadaan = mesin.keadaan();
                if (keadaan == AdbSambungUlang.NONAKTIF) {
                    break;
                } else if (keadaan == AdbSambungUlang.MENUNGGU_WIFI) {
                    tidur(5000);
                } else if (keadaan == AdbSambungUlang.TUNGGU_COBA_LAGI) {
                    tidur(mesin.jedaCobaLagiMs());
                    mesin.cobaLagi();
                } else if (keadaan == AdbSambungUlang.MENCARI) {
                    if (cobaSambung()) {
                        mesin.tersambung();
                        NotifikasiDebug.tampilkan(this, NotifikasiDebug.TERSAMBUNG);
                    } else {
                        long jeda = mesin.terputus();
                        if (mesin.keadaan() == AdbSambungUlang.MENUNGGU_WIFI) {
                            NotifikasiDebug.tampilkan(this, NotifikasiDebug.MENUNGGU_WIFI);
                        } else {
                            NotifikasiDebug.tampilkan(this, NotifikasiDebug.MENUNGGU_SAMBUNG);
                        }
                        if (jeda > 0) tidur(jeda);
                        mesin.cobaLagi();
                    }
                } else if (keadaan == AdbSambungUlang.TERSAMBUNG) {
                    // Pantau kesehatan: daftar proses sekali semenit.
                    tidur(60000);
                    boolean sehat = false;
                    try {
                        if (klien != null && klien.tersambung()) {
                            klien.daftarJdwp();
                            sehat = true;
                        }
                    } catch (Throwable ignored) {
                        sehat = false;
                    }
                    if (!sehat) {
                        tutupKlien();
                        long jeda = mesin.terputus();
                        NotifikasiDebug.tampilkan(this,
                                mesin.keadaan() == AdbSambungUlang.MENUNGGU_WIFI
                                        ? NotifikasiDebug.MENUNGGU_WIFI
                                        : NotifikasiDebug.MENUNGGU_SAMBUNG);
                        if (jeda > 0) tidur(jeda);
                        mesin.cobaLagi();
                    }
                } else {
                    tidur(5000);
                }
            } catch (Throwable t) {
                tidur(5000);
            }
        }
    }

    private void tidur(long ms) {
        try {
            Thread.sleep(Math.max(1000, ms));
        } catch (InterruptedException e) {
            // Lanjutkan putaran (jalan mungkin sudah false).
        }
    }

    /**
     * Satu percobaan sambung: cari layanan _adb-tls-connect._tcp lalu
     * TLS ke 127.0.0.1:port (jatuh balik ke host hasil resolve).
     * Mengembalikan true bila CNXN/AUTH berhasil + daftar JDWP terbaca.
     */
    private boolean cobaSambung() {
        final String[] ketemu = new String[]{null, null};
        final Object kunciTunggu = new Object();
        AdbMdns.cari(this, AdbMdns.TIPE_SAMBUNG, 15000, new AdbMdns.Pendengar() {
            @Override
            public void ketemu(String host, int port) {
                synchronized (kunciTunggu) {
                    ketemu[0] = host;
                    ketemu[1] = String.valueOf(port);
                    kunciTunggu.notifyAll();
                }
            }

            @Override
            public void selesai(String galat) {
                synchronized (kunciTunggu) {
                    kunciTunggu.notifyAll();
                }
            }
        });
        synchronized (kunciTunggu) {
            try {
                kunciTunggu.wait(20000);
            } catch (InterruptedException e) {
                return false;
            }
        }
        if (ketemu[1] == null) return false;
        int port;
        try {
            port = Integer.parseInt(ketemu[1]);
        } catch (Throwable t) {
            return false;
        }
        try {
            if (kunci == null) {
                kunci = AdbKunci.muatAtauBuat(new File(getFilesDir(), "adb"));
            }
        } catch (Throwable t) {
            return false;
        }
        // Coba 127.0.0.1 dulu, lalu host hasil resolve.
        String[] hostCoba = new String[]{"127.0.0.1", ketemu[0]};
        for (int i = 0; i < hostCoba.length; i++) {
            String h = hostCoba[i];
            if (h == null || h.length() == 0) continue;
            AdbKlien k = new AdbKlien();
            try {
                k.sambungTls(h, port, kunci, 8000);
                k.daftarJdwp();
                tutupKlien();
                klien = k;
                return true;
            } catch (Throwable t) {
                try { k.tutup(); } catch (Throwable ignored) {
                }
            }
            if (!jalan) return false;
        }
        return false;
    }
}
