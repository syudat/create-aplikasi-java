package com.androlua;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import novan.terminal.Env;
import novan.terminal.Pty;
import novan.terminal.Screen;

/**
 * Layanan foreground yang memegang sesi shell Terminal (Tahap 4,
 * KeepAlive).
 *
 * Shell TIDAK mati saat TerminalActivity ditutup: sesi hidup di dalam
 * layanan ini, pengguna bisa pindah layar (mis. ke Kelola Project)
 * lalu kembali ke Terminal tanpa kehilangan sesi, riwayat perintah
 * di layar, atau proses yang sedang berjalan. Notifikasi tetap
 * tampil selama sesi hidup supaya pengguna tahu shell masih jalan
 * dan bisa kembali dengan satu ketukan.
 *
 * Batasan jujur: bila Android mematikan proses aplikasi (mis. sistem
 * butuh memori), layanan ikut mati dan shell harus dibuka ulang.
 * Itu perilaku normal Android untuk layanan foreground.
 */
public class TerminalService extends Service {
    public static final String CHANNEL_ID = "terminal-tetap-hidup";
    private static final int NOTIF_ID = 7001;

    /** Sesi shell yang sedang hidup (null bila belum ada / sudah ditutup). */
    public static Sesi sesi = null;

    /** Pendengar perubahan layar (satu Activity aktif). */
    public interface PendengarLayar {
        void layarBerubah();
    }

    /** Satu sesi shell: PTY + parser VT (Screen 24x80) + utas pembaca. */
    public static class Sesi {
        /** Layar grid VT (24 baris x 80 kolom). */
        public final Screen layar;
        /** Folder kerja shell ini. */
        public final String cwd;
        private Pty pty;
        private Thread pembaca;
        private volatile boolean jalan = false;
        private PendengarLayar pendengar;
        private final Handler utama = new Handler(Looper.getMainLooper());
        private volatile boolean segarkanTertunda = false;

        Sesi(String cwd) {
            this.cwd = cwd;
            this.layar = new Screen(24, 80);
        }

        /** Buka shell (sekali saja). Mengembalikan true bila shell jalan. */
        public synchronized boolean mulai(Context ctx) {
            if (jalan) return true;
            try {
                Env.init(ctx);
            } catch (Throwable ignored) {
            }
            File dir = new File(cwd);
            if (!dir.isDirectory()) dir.mkdirs();
            final Pty dibuka;
            try {
                dibuka = Pty.open(Env.shell(), dir.getAbsolutePath(), 24, 80);
            } catch (Throwable t) {
                layar.write("\r" + "\n" + TeksUI.t("Gagal membuka shell: ") + t.getMessage() + "\r\n");
                return false;
            }
            if (dibuka == null) {
                layar.write("\r" + "\n" + TeksUI.t("Gagal membuka shell. Pustaka ptyterm tidak termuat.") + "\r" + "\n");
                return false;
            }
            pty = dibuka;
            jalan = true;
            pembaca = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStreamReader r = new InputStreamReader(pty.in, "UTF-8");
                        char[] buf = new char[4096];
                        int n;
                        while (jalan && (n = r.read(buf)) > 0) {
                            layar.write(new String(buf, 0, n));
                            mintaSegarkan();
                        }
                    } catch (Throwable ignored) {
                    }
                    jalan = false;
                    try {
                        layar.write("\r" + "\n" + TeksUI.t("[Shell selesai]") + "\r" + "\n");
                    } catch (Throwable ignored) {
                    }
                    mintaSegarkan();
                }
            });
            pembaca.start();
            return true;
        }

        /** true bila shell masih hidup. */
        public boolean masihJalan() {
            return jalan && pty != null;
        }

        /** Kirim teks ke shell (perintah diakhiri "\n" oleh pemanggil). */
        public void kirim(String teks) {
            Pty p = pty;
            if (p == null || !jalan || teks == null) return;
            try {
                p.send(teks);
            } catch (Throwable ignored) {
            }
        }

        /** Isi layar saat ini (baris-baris teks, tanpa baris kosong di ujung). */
        public ArrayList<String> isiLayar() {
            return layar.snapshot();
        }

        /** Baris tempat kursor berada (untuk dibacakan Jieshuo). */
        public String barisKursor() {
            return layar.cursorLine();
        }

        /** Riwayat gulir (baris yang sudah tergulir keluar layar). */
        public ArrayList<String> riwayatGulir() {
            return layar.scrollback();
        }

        /** Kosongkan layar + riwayat gulir. */
        public void bersihkanLayar() {
            layar.clear();
            mintaSegarkan();
        }

        /** true bila libkautsarexec.so ada (jalur exec biner aktif). */
        public boolean adaKautsarExec(Context ctx) {
            return Env.adaKautsarExec(ctx);
        }

        /** Uji jalan tidaknya exec biner (Env.selfTest). */
        public String ujiExec(Context ctx) {
            return Env.ujiExec(ctx);
        }

        public void setPendengar(PendengarLayar p) {
            pendengar = p;
        }

        private void mintaSegarkan() {
            if (segarkanTertunda) return;
            segarkanTertunda = true;
            utama.post(new Runnable() {
                @Override
                public void run() {
                    segarkanTertunda = false;
                    PendengarLayar p = pendengar;
                    if (p != null) p.layarBerubah();
                }
            });
        }

        /** Matikan shell dan utas pembaca. */
        public synchronized void berhenti() {
            jalan = false;
            Pty p = pty;
            pty = null;
            if (p != null) {
                try {
                    p.hangup();
                } catch (Throwable ignored) {
                }
                try {
                    p.close();
                } catch (Throwable ignored) {
                }
            }
            Thread t = pembaca;
            pembaca = null;
            if (t != null) t.interrupt();
        }
    }

    /**
     * Ambil sesi yang sudah hidup, atau buat + jalankan sesi baru.
     * Memulai layanan foreground supaya proses tidak dimatikan Android
     * saat pengguna pindah layar.
     */
    public static synchronized Sesi ambilAtauBuat(Context ctx, String cwd) {
        if (sesi != null && sesi.masihJalan()) return sesi;
        Intent it = new Intent(ctx, TerminalService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(it);
            } else {
                ctx.startService(it);
            }
        } catch (Throwable ignored) {
        }
        sesi = new Sesi(cwd);
        sesi.mulai(ctx);
        return sesi;
    }

    /** true bila ada sesi shell yang masih hidup. */
    public static synchronized boolean sesiJalan() {
        return sesi != null && sesi.masihJalan();
    }

    /** Matikan sesi shell dan hentikan layanan. */
    public static synchronized void tutupSesi(Context ctx) {
        if (sesi != null) {
            sesi.berhenti();
            sesi = null;
        }
        try {
            ctx.stopService(new Intent(ctx, TerminalService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        buatChannel();
        startForeground(NOTIF_ID, buatNotifikasi());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (sesi != null) {
            sesi.berhenti();
            sesi = null;
        }
        super.onDestroy();
    }

    private void buatChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, TeksUI.t("Terminal tetap hidup"),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(TeksUI.t("Menampilkan bahwa shell Terminal Create Aplikasi masih berjalan."));
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
        }
    }

    private Notification buatNotifikasi() {
        Intent kembali = new Intent(this, TerminalActivity.class);
        kembali.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, kembali,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle(TeksUI.t("Terminal Create Aplikasi"))
                .setContentText("Shell tetap berjalan. Ketuk untuk kembali ke Terminal.")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
