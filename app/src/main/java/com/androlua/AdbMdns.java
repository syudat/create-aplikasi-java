package com.androlua;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

/**
 * Penemuan layanan adb lewat mDNS (NsdManager) untuk debugging
 * nirkabel: "_adb-tls-pairing._tcp" (port pairing, hanya muncul saat
 * dialog pairing di Opsi Pengembang terbuka) dan
 * "_adb-tls-connect._tcp" (port sambung TLS, muncul saat debugging
 * nirkabel aktif). Butuh izin CHANGE_WIFI_MULTICAST_STATE +
 * MulticastLock supaya paket mDNS sampai.
 *
 * Callback ketemu() dipanggil sekali untuk layanan pertama yang
 * berhasil di-resolve (host hasil resolve + port). Pemanggil mencoba
 * 127.0.0.1:port dulu (adbd mendengarkan di semua antarmuka), lalu
 * host hasil resolve bila gagal.
 */
public class AdbMdns {

    public static final String TIPE_PAIRING = "_adb-tls-pairing._tcp";
    public static final String TIPE_SAMBUNG = "_adb-tls-connect._tcp";

    /** Kode galat yang diteruskan ke Pendengar.selesai (bukan teks, supaya bisa diterjemahkan di pemanggil). */
    public static final String GALAT_TIDAK_TERSEDIA = "TIDAK_TERSEDIA";
    public static final String GALAT_TIDAK_DITEMUKAN = "TIDAK_DITEMUKAN";
    public static final String GALAT_GAGAL = "GAGAL";
    public static final String GALAT_TIDAK_BISA_MULAI = "TIDAK_BISA_MULAI";

    public interface Pendengar {
        /** Layanan ditemukan: host hasil resolve + port. */
        void ketemu(String host, int port);
        /** Penemuan berakhir tanpa layanan (timeout/galat). */
        void selesai(String galat);
    }

    /**
     * Cari satu layanan mDNS. Berhenti otomatis setelah layanan
     * pertama ter-resolve atau setelah timeoutMs.
     */
    public static void cari(final Context ctx, final String tipe,
                            final long timeoutMs, final Pendengar pendengar) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final WifiManager wifi = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        final WifiManager.MulticastLock kunci = wifi == null ? null
                : wifi.createMulticastLock("adb-mdns");
        if (kunci != null) {
            try {
                kunci.setReferenceCounted(false);
                kunci.acquire();
            } catch (Throwable ignored) {
            }
        }
        final NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            if (kunci != null) {
                try { kunci.release(); } catch (Throwable ignored) {
                }
            }
            pendengar.selesai(GALAT_TIDAK_TERSEDIA);
            return;
        }
        final boolean[] selesai = new boolean[]{false};
        final NsdManager.DiscoveryListener[] penemuan = new NsdManager.DiscoveryListener[1];

        final Runnable akhiri = new Runnable() {
            @Override
            public void run() {
                if (selesai[0]) return;
                selesai[0] = true;
                try {
                    if (penemuan[0] != null) nsd.stopServiceDiscovery(penemuan[0]);
                } catch (Throwable ignored) {
                }
                if (kunci != null) {
                    try { kunci.release(); } catch (Throwable ignored) {
                    }
                }
            }
        };
        final Runnable batasWaktu = new Runnable() {
            @Override
            public void run() {
                if (selesai[0]) return;
                akhiri.run();
                pendengar.selesai(GALAT_TIDAK_DITEMUKAN);
            }
        };
        handler.postDelayed(batasWaktu, timeoutMs > 0 ? timeoutMs : 15000);

        NsdManager.ResolveListener resolve = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo info, int kode) {
                // Biarkan penemuan berjalan sampai timeout (layanan lain
                // mungkin muncul); jangan akhiri di sini.
            }

            @Override
            public void onServiceResolved(NsdServiceInfo info) {
                if (selesai[0]) return;
                handler.removeCallbacks(batasWaktu);
                akhiri.run();
                String host = "";
                try {
                    if (info.getHost() != null) host = info.getHost().getHostAddress();
                } catch (Throwable ignored) {
                }
                pendengar.ketemu(host, info.getPort());
            }
        };
        final NsdManager.ResolveListener resolveAkhir = resolve;

        penemuan[0] = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String tipeLayanan) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                if (selesai[0]) return;
                try {
                    nsd.resolveService(info, resolveAkhir);
                } catch (Throwable ignored) {
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
            }

            @Override
            public void onDiscoveryStopped(String tipeLayanan) {
            }

            @Override
            public void onStartDiscoveryFailed(String tipeLayanan, int kode) {
                if (selesai[0]) return;
                handler.removeCallbacks(batasWaktu);
                akhiri.run();
                pendengar.selesai(GALAT_GAGAL + ":" + kode);
            }

            @Override
            public void onStopDiscoveryFailed(String tipeLayanan, int kode) {
            }
        };
        try {
            nsd.discoverServices(tipe, NsdManager.PROTOCOL_DNS_SD, penemuan[0]);
        } catch (Throwable t) {
            handler.removeCallbacks(batasWaktu);
            akhiri.run();
            pendengar.selesai(GALAT_TIDAK_BISA_MULAI);
        }
    }
}
