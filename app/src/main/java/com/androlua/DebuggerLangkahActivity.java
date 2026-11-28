package com.androlua;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Debugger Langkah-Demi-Langkah dan Inspektur Memori Waktu-Nyata
 * (Fitur 6, daftar Gemini 2026-11-27) — kini debugger JDWP SUNGGUHAN.
 *
 * Bagian atas (tidak diubah): jejak eksekusi — tulis jejak, pasang
 * titik henti, jalankan baris demi baris, masuk/keluar fungsi.
 *
 * Bagian JDWP di bawah memakai klien protokol JDWP murni-Java
 * (DebuggerLangkah.JdwpKlien, tanpa JDI) + jembatan adb di dalam
 * aplikasi (AdbKlien/AdbKunci/AdbPasangan/Spake2):
 * - "Cari layanan pairing" menemukan layanan _adb-tls-pairing._tcp;
 *   notifikasi status tampil di atas saat pengguna membuka Opsi
 *   Pengembang, dengan tombol "Masukkan kode pairing".
 * - "Pasangkan" menukar kode 6 digit lewat SPAKE2 + TLS; pairing
 *   cukup SEKALI (kunci RSA tersimpan, adbd mengenalinya).
 * - "Sambung ke adb nirkabel" tersambung TLS ke adbd di perangkat
 *   yang sama; "Muat daftar proses debug" memakai track-jdwp +
 *   ActivityManager untuk nama paket; "Sambung ke proses terpilih"
 *   membuka jdwp:<pid> lalu menahan VM target.
 * - Titik henti / langkah / bingkai / nilai variabel dibaca langsung
 *   dari VM target (ThreadReference.Frames, StackFrame.GetValues).
 * - "Sambung ulang otomatis" menyalakan layanan latar: bila WiFi
 *   mati lalu menyala lagi, atau HP restart, sambungan tersambung
 *   kembali sendiri tanpa kode pairing lagi (WiFi harus menyala
 *   saat debugging dipakai — jalurnya lewat WiFi).
 *
 * "Sambung JDWP" (TCP) dan "Dengarkan JDWP" (mode dengar
 * --attach-agent) yang lama tetap ada, tidak dihapus.
 * Logika ada di DebuggerLangkah + AdbKlien/AdbPasangan/Spake2
 * (murni Java, diuji di komputer melawan adbd/agen palsu).
 */
public class DebuggerLangkahActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eJejak;
    private EditText eHenti;
    private EditText ePaket;
    private EditText ePort;
    private EditText eHost;
    private EditText ePortPairing;
    private EditText eKode;
    private EditText eKelas;
    private EditText eMetode;
    private EditText eBarisDebug;
    private Spinner spProses;
    private ArrayAdapter<String> adapterProses;
    private final List<Integer> pidProses = new ArrayList<Integer>();
    private Button bOtomatis;
    private TextView tvHasil;
    private Button bJalankan;
    private Button bLangkah;
    private DebuggerLangkah.Sesi sesi;
    private DebuggerLangkah.JdwpKlien klienJdwp;
    private AdbKlien klienAdb;
    private AdbKlien.Aliran aliranJdwp;
    private long threadTerpilih = 0;

    private static final int MINTA_NOTIFIKASI = 902;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Debugger Langkah dan Inspektur Memori"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Debugger Langkah dan Inspektur Memori"), 20);
        label(r, TeksUI.t("Pasang titik henti, jalankan jejak eksekusi baris demi baris, dan periksa nilai variabel di tiap langkah lewat pembaca layar."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Jejak eksekusi:"), false);
        eJejak = new EditText(this);
        GayaStudio.edit(eJejak);
        eJejak.setHint(TeksUI.t("Contoh: 10|int total = harga * jumlah;|harga=5000, jumlah=3, total=15000"));
        eJejak.setText("10|int total = harga * jumlah;|harga=5000, jumlah=3, total=15000\n11|int pajak = total / 10;|harga=5000, jumlah=3, total=15000, pajak=1500\n12|return total + pajak;|harga=5000, jumlah=3, total=15000, pajak=1500");
        eJejak.setTextSize(14);
        eJejak.setMinLines(4);
        r.addView(eJejak, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Titik henti (nomor baris, pisah koma):"), false);
        eHenti = new EditText(this);
        GayaStudio.edit(eHenti);
        eHenti.setHint(TeksUI.t("Contoh: 11"));
        eHenti.setText("11");
        eHenti.setTextSize(14);
        eHenti.setSingleLine(true);
        r.addView(eHenti, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Jalankan Jejak Debug"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });
        bLangkah = button(r, TeksUI.t("Langkah Satu Baris"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                langkah();
            }
        });
        button(r, TeksUI.t("Masuk ke Fungsi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                masukFungsi();
            }
        });
        button(r, TeksUI.t("Lanjutkan Program"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lanjutkan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                + TeksUI.t("Jalankan Jejak Debug") + ".");
        r.addView(tvHasil);

        heading(r, TeksUI.t("Debug proses sungguhan (JDWP):"), 16);
        label(r, TeksUI.t("Jeda proses sungguhan butuh aplikasi target yang debuggable dan jembatan adb di perangkat ini. Aktifkan debugging nirkabel dan pasangkan di bawah, lalu sambung ke proses target."), false);

        label(r, TeksUI.t("Paket aplikasi target:"), false);
        ePaket = new EditText(this);
        GayaStudio.edit(ePaket);
        ePaket.setHint(TeksUI.t("Contoh: com.contoh.aplikasiku"));
        ePaket.setTextSize(14);
        ePaket.setSingleLine(true);
        r.addView(ePaket, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Port JDWP:"), false);
        ePort = new EditText(this);
        GayaStudio.edit(ePort);
        ePort.setHint(TeksUI.t("Contoh: 8000"));
        ePort.setText("8000");
        ePort.setTextSize(14);
        ePort.setSingleLine(true);
        r.addView(ePort, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Periksa Target Debug"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                periksaTarget();
            }
        });
        button(r, TeksUI.t("Sambung JDWP"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sambungJdwp();
            }
        });
        button(r, TeksUI.t("Dengarkan JDWP"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dengarkanJdwp();
            }
        });
        button(r, TeksUI.t("Tahan Proses Target"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tahanProses();
            }
        });
        button(r, TeksUI.t("Lanjutkan Proses Target"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lanjutkanProses();
            }
        });

        // ---------------- Debug nirkabel (adb di perangkat ini) ----------------
        heading(r, TeksUI.t("Debug nirkabel (adb di perangkat ini):"), 16);
        label(r, TeksUI.t("Langkah 1: Buka Pengaturan, Tentang ponsel, ketuk Nomor build 7 kali sampai Opsi Pengembang aktif. Langkah 2: Di Opsi Pengembang, nyalakan Debugging nirkabel. Langkah 3: Ketuk Pasangkan perangkat dengan kode pairing, catat kode 6 digit dan portnya. Langkah 4: Tekan Cari layanan pairing di bawah, lalu masukkan kodenya dan tekan Pasangkan. Pairing cukup sekali saja: kunci tersimpan dan sambungan tersambung kembali otomatis bila WiFi mati lalu menyala lagi atau HP restart, tanpa kode baru. Catatan jujur: debugging nirkabel tetap butuh WiFi menyala saat dipakai, karena jalurnya lewat WiFi."), false);

        label(r, TeksUI.t("Host adb:"), false);
        eHost = new EditText(this);
        GayaStudio.edit(eHost);
        eHost.setHint(TeksUI.t("Contoh: 127.0.0.1"));
        eHost.setText("127.0.0.1");
        eHost.setTextSize(14);
        eHost.setSingleLine(true);
        r.addView(eHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Port pairing:"), false);
        ePortPairing = new EditText(this);
        GayaStudio.edit(ePortPairing);
        ePortPairing.setHint(TeksUI.t("Contoh: 37121"));
        ePortPairing.setTextSize(14);
        ePortPairing.setSingleLine(true);
        ePortPairing.setInputType(InputType.TYPE_CLASS_NUMBER);
        r.addView(ePortPairing, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Kode pairing (6 digit):"), false);
        eKode = new EditText(this);
        GayaStudio.edit(eKode);
        eKode.setHint(TeksUI.t("Contoh: 123456"));
        eKode.setTextSize(14);
        eKode.setSingleLine(true);
        eKode.setInputType(InputType.TYPE_CLASS_NUMBER);
        r.addView(eKode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Cari layanan pairing"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cariPairing();
            }
        });
        button(r, TeksUI.t("Buka Opsi Pengembang"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaOpsiPengembang();
            }
        });
        button(r, TeksUI.t("Pasangkan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasangkan();
            }
        });
        button(r, TeksUI.t("Sambung ke adb nirkabel"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sambungAdbNirkabel();
            }
        });
        button(r, TeksUI.t("Muat daftar proses debug"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muatProsesDebug();
            }
        });

        label(r, TeksUI.t("Proses yang bisa di-debug:"), false);
        spProses = new Spinner(this);
        adapterProses = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, new ArrayList<String>());
        adapterProses.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProses.setAdapter(adapterProses);
        r.addView(spProses, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Sambung ke proses terpilih"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sambungProsesTerpilih();
            }
        });

        bOtomatis = button(r, teksTombolOtomatis(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alihkanOtomatis();
            }
        });

        heading(r, TeksUI.t("Titik henti dan variabel proses sungguhan:"), 16);
        label(r, TeksUI.t("Kelas target (nama paket lengkap):"), false);
        eKelas = new EditText(this);
        GayaStudio.edit(eKelas);
        eKelas.setHint(TeksUI.t("Contoh: com.contoh.Pembayaran"));
        eKelas.setTextSize(14);
        eKelas.setSingleLine(true);
        r.addView(eKelas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Metode:"), false);
        eMetode = new EditText(this);
        GayaStudio.edit(eMetode);
        eMetode.setHint(TeksUI.t("Contoh: proses"));
        eMetode.setTextSize(14);
        eMetode.setSingleLine(true);
        r.addView(eMetode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nomor baris:"), false);
        eBarisDebug = new EditText(this);
        GayaStudio.edit(eBarisDebug);
        eBarisDebug.setHint(TeksUI.t("Contoh: 11"));
        eBarisDebug.setTextSize(14);
        eBarisDebug.setSingleLine(true);
        eBarisDebug.setInputType(InputType.TYPE_CLASS_NUMBER);
        r.addView(eBarisDebug, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Pasang titik henti (proses)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasangTitikHentiProses();
            }
        });
        button(r, TeksUI.t("Langkah satu baris (proses)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                langkahProses();
            }
        });
        button(r, TeksUI.t("Tampilkan bingkai dan variabel"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkanBingkai();
            }
        });
        button(r, TeksUI.t("Tunggu jeda (proses)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tungguJeda();
            }
        });

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        if (getIntent() != null && getIntent().getBooleanExtra(
                NotifikasiDebug.EXTRA_BUKA_PAIRING, false)) {
            eKode.requestFocus();
            announce(TeksUI.t("Masukkan kode pairing") + ". "
                    + TeksUI.t("Kode pairing (6 digit):"));
        } else {
            announce(TeksUI.t("Debugger Langkah dan Inspektur Memori") + ". " + TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan")
                    + " " + TeksUI.t("Jalankan Jejak Debug") + ".");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
        segarkanTombolOtomatis();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (klienJdwp != null) klienJdwp.tutup();
        } catch (Throwable ignored) {
        }
        try {
            if (aliranJdwp != null) aliranJdwp.masuk().close();
        } catch (Throwable ignored) {
        }
        try {
            if (klienAdb != null) klienAdb.tutup();
        } catch (Throwable ignored) {
        }
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private void mulai() {
        List<DebuggerLangkah.Langkah> jejak =
                DebuggerLangkah.uraiJejak(eJejak.getText().toString());
        if (jejak.size() == 0) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        sesi = new DebuggerLangkah.Sesi();
        sesi.jejak = jejak;
        String[] henti = eHenti.getText().toString().split(",");
        for (int i = 0; i < henti.length; i++) {
            try {
                DebuggerLangkah.tambahTitikHenti(sesi, Integer.parseInt(henti[i].trim()));
            } catch (Throwable ignored) {
            }
        }
        boolean sampai = sesi.lanjut();
        tampilkan(sampai ? "" : TeksUI.t("Jejak habis.") + " ");
    }

    private void lanjutkan() {
        if (sesi == null) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        boolean sampai = sesi.lanjut();
        tampilkan(sampai ? "" : TeksUI.t("Jejak habis.") + " ");
    }

    private void langkah() {
        if (sesi == null) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        boolean ada = sesi.langkah();
        tampilkan(ada ? "" : TeksUI.t("Jejak habis.") + " ");
    }

    private void masukFungsi() {
        if (sesi == null) {
            toast(TeksUI.t("Belum ada jejak. Tulis jejak eksekusi, lalu tekan") + " "
                    + TeksUI.t("Jalankan Jejak Debug") + ".");
            return;
        }
        boolean ada = sesi.masukFungsi();
        String awalan = "";
        String fungsi = sesi.fungsiAktif();
        if (fungsi.length() > 0) {
            awalan = TeksUI.t("Masuk ke fungsi") + " " + fungsi + ". ";
        }
        tampilkan(ada ? awalan : TeksUI.t("Jejak habis.") + " ");
    }

    private void tampilkan(String awalan) {
        StringBuilder b = new StringBuilder();
        b.append(awalan);
        b.append(TeksUI.t("Baris aktif:")).append(" ")
                .append(DebuggerLangkah.teksBarisAktif(sesi)).append("\n");
        String fungsi = sesi == null ? "" : sesi.fungsiAktif();
        if (fungsi.length() > 0) {
            b.append(TeksUI.t("Fungsi:")).append(" ").append(fungsi).append("\n");
        }
        b.append(TeksUI.t("Variabel:")).append("\n")
                .append(DebuggerLangkah.teksVariabel(sesi.aktif()));
        String hasil = b.toString();
        tvHasil.setText(hasil);
        announce(hasil);
    }

    // ---------------- JDWP ----------------

    private int bacaPort() {
        try {
            int port = Integer.parseInt(ePort.getText().toString().trim());
            if (port < 1 || port > 65535) return -1;
            return port;
        } catch (Throwable t) {
            return -1;
        }
    }

    private void periksaTarget() {
        final String paket = ePaket.getText().toString().trim();
        if (paket.length() == 0) {
            toast(TeksUI.t("Isi dulu paket aplikasi targetnya."));
            return;
        }
        StringBuilder b = new StringBuilder();
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(paket, 0);
            boolean debug = (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            b.append(debug ? TeksUI.t("Target bisa di-debug (debuggable).")
                    : TeksUI.t("Target TIDAK debuggable. Bangun ulang dengan debuggable true."));
        } catch (PackageManager.NameNotFoundException e) {
            b.append(TeksUI.t("Paket tidak ditemukan di perangkat ini."));
        } catch (Throwable t) {
            b.append(TeksUI.t("Paket tidak ditemukan di perangkat ini."));
        }
        b.append("\n").append(DebuggerLangkah.diagnosisTanpaAdb());
        final String hasil = b.toString();
        tvHasil.setText(hasil);
        announce(hasil);
    }

    private void sambungJdwp() {
        final int port = bacaPort();
        if (port < 0) {
            toast(TeksUI.t("Nomor port tidak sah."));
            return;
        }
        tvHasil.setText(TeksUI.t("Sambung JDWP") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String hasil;
                try {
                    if (klienJdwp != null) klienJdwp.tutup();
                    DebuggerLangkah.JdwpKlien klien = new DebuggerLangkah.JdwpKlien();
                    String versi = klien.sambung("127.0.0.1", port, 5000);
                    klienJdwp = klien;
                    hasil = TeksUI.t("Tersambung ke JDWP:") + " " + versi;
                } catch (final Throwable t) {
                    hasil = TeksUI.t("Gagal tersambung ke JDWP:") + " " + t.getMessage()
                            + "\n" + DebuggerLangkah.diagnosisTanpaAdb();
                }
                final String hasilAkhir = hasil;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasilAkhir);
                        announce(hasilAkhir);
                    }
                });
            }
        }, "DebuggerJdwp").start();
    }

    private void dengarkanJdwp() {
        final int port = bacaPort();
        if (port < 0) {
            toast(TeksUI.t("Nomor port tidak sah."));
            return;
        }
        tvHasil.setText(TeksUI.t("Menunggu agen JDWP menelepon balik ke port") + " " + port + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String hasil;
                try {
                    if (klienJdwp != null) klienJdwp.tutup();
                    DebuggerLangkah.JdwpKlien klien = new DebuggerLangkah.JdwpKlien();
                    String versi = klien.dengarkan(port, 60000);
                    klienJdwp = klien;
                    hasil = TeksUI.t("Tersambung ke JDWP:") + " " + versi;
                } catch (final Throwable t) {
                    hasil = TeksUI.t("Gagal tersambung ke JDWP:") + " " + t.getMessage()
                            + "\n" + DebuggerLangkah.diagnosisTanpaAdb();
                }
                final String hasilAkhir = hasil;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasilAkhir);
                        announce(hasilAkhir);
                    }
                });
            }
        }, "DebuggerJdwpDengar").start();
    }

    private void tahanProses() {
        if (klienJdwp == null || !klienJdwp.tersambung()) {
            toast(TeksUI.t("Belum tersambung ke JDWP. Tekan Sambung JDWP atau Dengarkan JDWP dulu."));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String hasil;
                try {
                    klienJdwp.tahan();
                    hasil = TeksUI.t("Proses target ditahan.");
                } catch (final Throwable t) {
                    hasil = TeksUI.t("Gagal menahan proses target:") + " " + t.getMessage();
                }
                final String hasilAkhir = hasil;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasilAkhir);
                        announce(hasilAkhir);
                    }
                });
            }
        }, "DebuggerJdwpTahan").start();
    }

    private void lanjutkanProses() {
        if (klienJdwp == null || !klienJdwp.tersambung()) {
            toast(TeksUI.t("Belum tersambung ke JDWP. Tekan Sambung JDWP atau Dengarkan JDWP dulu."));
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String hasil;
                try {
                    klienJdwp.lanjutkan();
                    hasil = TeksUI.t("Proses target dilanjutkan.");
                } catch (final Throwable t) {
                    hasil = TeksUI.t("Gagal melanjutkan proses target:") + " " + t.getMessage();
                }
                final String hasilAkhir = hasil;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasilAkhir);
                        announce(hasilAkhir);
                    }
                });
            }
        }, "DebuggerJdwpLanjut").start();
    }

    // ---------------- Debug nirkabel: pairing, adb, proses ----------------

    private String teksTombolOtomatis() {
        boolean nyala = LayananAdbOtomatis.apakahOtomatis(this);
        return nyala ? TeksUI.t("Matikan sambung ulang otomatis")
                : TeksUI.t("Nyalakan sambung ulang otomatis");
    }

    private void segarkanTombolOtomatis() {
        if (bOtomatis != null) {
            bOtomatis.setText(teksTombolOtomatis());
        }
    }

    private void tampilkanHasil(final String hasil) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvHasil.setText(hasil);
                announce(hasil);
            }
        });
    }

    /** "Buka Opsi Pengembang": buka layar Opsi Pengembang sistem. */
    private void bukaOpsiPengembang() {
        try {
            Intent it = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            String t = TeksUI.t("Opsi Pengembang dibuka. Nyalakan Debugging nirkabel, lalu ketuk Pasangkan perangkat dengan kode pairing.");
            tvHasil.setText(t);
            announce(t);
        } catch (Throwable e) {
            String t = TeksUI.t("Tidak bisa membuka Opsi Pengembang:") + " " + e.getMessage();
            tvHasil.setText(t);
            announce(t);
        }
    }

    /** "Cari layanan pairing": temukan layanan pairing lewat mDNS. */
    private void cariPairing() {
        tvHasil.setText(TeksUI.t("Mencari layanan pairing debugging nirkabel..."));
        NotifikasiDebug.tampilkan(this, NotifikasiDebug.MENUNGGU_KODE);
        AdbMdns.cari(this, AdbMdns.TIPE_PAIRING, 15000, new AdbMdns.Pendengar() {
            @Override
            public void ketemu(final String host, final int port) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ePortPairing.setText(String.valueOf(port));
                        String t = TeksUI.t("Layanan pairing ditemukan, port") + " " + port
                                + ". " + TeksUI.t("Buka Opsi Pengembang dan ketuk Pasangkan perangkat dengan kode pairing, lalu masukkan kodenya di sini.");
                        tvHasil.setText(t);
                        announce(t);
                    }
                });
            }

            @Override
            public void selesai(final String galat) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String t;
                        if (galat == null || AdbMdns.GALAT_TIDAK_DITEMUKAN.equals(galat)) {
                            t = TeksUI.t("Layanan pairing tidak ditemukan. Pastikan Debugging nirkabel menyala dan jendela pairing sedang terbuka.");
                        } else if (AdbMdns.GALAT_TIDAK_TERSEDIA.equals(galat)) {
                            t = TeksUI.t("Layanan penemuan perangkat tidak tersedia.");
                        } else if (AdbMdns.GALAT_TIDAK_BISA_MULAI.equals(galat)) {
                            t = TeksUI.t("Tidak bisa memulai penemuan layanan.");
                        } else if (galat.startsWith(AdbMdns.GALAT_GAGAL)) {
                            t = TeksUI.t("Penemuan layanan gagal.") + " " + galat;
                        } else {
                            t = TeksUI.t("Gagal mencari layanan pairing:") + " " + galat;
                        }
                        tvHasil.setText(t);
                        announce(t);
                    }
                });
            }
        });
    }

    /** Minta izin notifikasi (Android 13+); pairing tetap jalan tanpa izin. */
    private void mintaIzinNotifikasiBilaPerlu() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    MINTA_NOTIFIKASI);
        }
    }

    @Override
    public void onRequestPermissionsResult(int kode, String[] izin, int[] hasil) {
        super.onRequestPermissionsResult(kode, izin, hasil);
        if (kode == MINTA_NOTIFIKASI) {
            if (hasil.length > 0 && hasil[0] == PackageManager.PERMISSION_GRANTED) {
                NotifikasiDebug.tampilkan(this, NotifikasiDebug.MENUNGGU_KODE);
            } else {
                tvHasil.setText(TeksUI.t("Izin notifikasi tidak diberikan. Pairing tetap bisa jalan, tetapi status tidak tampil di bilah notifikasi."));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(
                NotifikasiDebug.EXTRA_BUKA_PAIRING, false)) {
            eKode.requestFocus();
            announce(TeksUI.t("Masukkan kode pairing") + ". "
                    + TeksUI.t("Kode pairing (6 digit):"));
        }
    }

    /** "Pasangkan": tukar kode 6 digit lewat SPAKE2 + TLS, lalu sambung adb. */
    private void pasangkan() {
        final String host = eHost.getText().toString().trim();
        final String portTeks = ePortPairing.getText().toString().trim();
        final String kode = eKode.getText().toString().trim();
        if (host.isEmpty() || portTeks.isEmpty()) {
            String t = TeksUI.t("Isi dulu host dan port pairing. Tekan Cari layanan pairing atau isi manual.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        if (kode.length() != 6) {
            String t = TeksUI.t("Kode pairing harus 6 digit.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        final int port;
        try {
            port = Integer.parseInt(portTeks);
        } catch (NumberFormatException e) {
            String t = TeksUI.t("Port pairing harus angka.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        mintaIzinNotifikasiBilaPerlu();
        NotifikasiDebug.tampilkan(this, NotifikasiDebug.MENUNGGU_KODE);
        tvHasil.setText(TeksUI.t("Memasangkan..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(getFilesDir(), "adb");
                    dir.mkdirs();
                    AdbKunci kunci = AdbKunci.muatAtauBuat(dir);
                    AdbPasangan.pasangkanTls(host, port, kunci, kode, 15000);
                    LayananAdbOtomatis.setTerpasang(DebuggerLangkahActivity.this, true);
                    tampilkanHasil(TeksUI.t("Pairing berhasil. Kunci tersimpan, tidak perlu pairing ulang. Sekarang menyambung ke adb nirkabel..."));
                    NotifikasiDebug.tampilkan(DebuggerLangkahActivity.this,
                            NotifikasiDebug.MENUNGGU_SAMBUNG);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sambungAdbNirkabel();
                        }
                    });
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Pairing gagal:") + " " + e.getMessage()
                            + " " + TeksUI.t("Periksa kodenya, lalu coba lagi."));
                    NotifikasiDebug.tampilkan(DebuggerLangkahActivity.this,
                            NotifikasiDebug.MENUNGGU_KODE);
                }
            }
        }, "DebuggerPairing").start();
    }

    /** "Sambung ke adb nirkabel": temukan layanan connect lewat mDNS, lalu TLS. */
    private void sambungAdbNirkabel() {
        final String hostAwal = eHost.getText().toString().trim();
        if (hostAwal.isEmpty()) {
            String t = TeksUI.t("Isi dulu host adb.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        tvHasil.setText(TeksUI.t("Menyambung ke adb nirkabel..."));
        AdbMdns.cari(this, AdbMdns.TIPE_SAMBUNG, 12000, new AdbMdns.Pendengar() {
            @Override
            public void ketemu(final String host, final int port) {
                sambungAdbKeHost(port, new String[]{"127.0.0.1", host, hostAwal});
            }

            @Override
            public void selesai(final String galat) {
                // mDNS tidak menemukan apa-apa: coba port connect bawaan.
                sambungAdbKeHost(5555, new String[]{"127.0.0.1", hostAwal});
            }
        });
    }

    /** Sambung TLS ke adbd di host/port, lalu daftar JDWP. */
    private void sambungAdbKeHost(final int port, final String[] hostCoba) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(getFilesDir(), "adb");
                    dir.mkdirs();
                    AdbKunci kunci = AdbKunci.muatAtauBuat(dir);
                    AdbKlien klien = new AdbKlien();
                    Throwable galatTerakhir = null;
                    for (int i = 0; i < hostCoba.length; i++) {
                        try {
                            klien.sambungTls(hostCoba[i], port, kunci, 5000);
                            galatTerakhir = null;
                            break;
                        } catch (Throwable e) {
                            galatTerakhir = e;
                        }
                    }
                    if (galatTerakhir != null) {
                        throw new IOException(galatTerakhir.getMessage(), galatTerakhir);
                    }
                    final List<Integer> pid = klien.daftarJdwp();
                    if (klienAdb != null) {
                        klienAdb.tutup();
                    }
                    klienAdb = klien;
                    final int jumlah = pid.size();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String t = TeksUI.t("Tersambung ke adb nirkabel.") + " "
                                    + TeksUI.t("Ditemukan") + " " + jumlah + " "
                                    + TeksUI.t("proses yang bisa di-debug.")
                                    + " " + TeksUI.t("Tekan Muat daftar proses debug untuk melihat namanya.");
                            tvHasil.setText(t);
                            announce(t);
                            NotifikasiDebug.tampilkan(DebuggerLangkahActivity.this,
                                    NotifikasiDebug.TERSAMBUNG);
                            muatProsesDebug();
                        }
                    });
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal menyambung ke adb nirkabel:") + " " + e.getMessage());
                }
            }
        }, "DebuggerAdbSambung").start();
    }

    /** "Muat daftar proses debug": track-jdwp + nama paket dari ActivityManager. */
    private void muatProsesDebug() {
        if (klienAdb == null) {
            String t = TeksUI.t("Belum tersambung ke adb nirkabel. Tekan Sambung ke adb nirkabel dulu.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        tvHasil.setText(TeksUI.t("Memuat daftar proses debug..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Integer> pid = klienAdb.daftarJdwp();
                    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    final List<String> nama = new ArrayList<String>();
                    pidProses.clear();
                    for (int i = 0; i < pid.size(); i++) {
                        int p = pid.get(i).intValue();
                        String paket = cariNamaPaket(am, p);
                        pidProses.add(Integer.valueOf(p));
                        nama.add(paket == null
                                ? TeksUI.t("Proses") + " " + p
                                : paket + " (PID " + p + ")");
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapterProses.clear();
                            adapterProses.addAll(nama);
                            adapterProses.notifyDataSetChanged();
                            String t = TeksUI.t("Ditemukan") + " " + nama.size() + " "
                                    + TeksUI.t("proses yang bisa di-debug.");
                            tvHasil.setText(t);
                            announce(t);
                        }
                    });
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal memuat daftar proses:") + " " + e.getMessage());
                }
            }
        }, "DebuggerAdbDaftar").start();
    }

    private String cariNamaPaket(ActivityManager am, int pid) {
        if (am == null) {
            return null;
        }
        List<ActivityManager.RunningAppProcessInfo> daftar = am.getRunningAppProcesses();
        if (daftar == null) {
            return null;
        }
        for (int i = 0; i < daftar.size(); i++) {
            if (daftar.get(i).pid == pid) {
                return daftar.get(i).processName;
            }
        }
        return null;
    }

    /** "Sambung ke proses terpilih": buka jdwp:<pid>, tahan VM target. */
    private void sambungProsesTerpilih() {
        if (klienAdb == null) {
            String t = TeksUI.t("Belum tersambung ke adb nirkabel.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        int posisi = spProses.getSelectedItemPosition();
        if (posisi < 0 || posisi >= pidProses.size()) {
            String t = TeksUI.t("Pilih dulu prosesnya di daftar.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        final int pid = pidProses.get(posisi).intValue();
        tvHasil.setText(TeksUI.t("Menyambung ke proses") + " " + pid + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (aliranJdwp != null) {
                        try {
                            aliranJdwp.masuk().close();
                        } catch (Throwable ignored) {
                        }
                        aliranJdwp = null;
                    }
                    if (klienJdwp != null) {
                        try {
                            klienJdwp.tutup();
                        } catch (Throwable ignored) {
                        }
                        klienJdwp = null;
                    }
                    aliranJdwp = klienAdb.bukaJdwp(pid);
                    DebuggerLangkah.JdwpKlien j = new DebuggerLangkah.JdwpKlien();
                    final String versi = j.sambungAliran(
                            new java.io.DataInputStream(aliranJdwp.masuk()),
                            new java.io.DataOutputStream(aliranJdwp.keluar()));
                    final List<Long> thread = j.daftarThread();
                    klienJdwp = j;
                    threadTerpilih = thread.isEmpty() ? 0 : thread.get(0).longValue();
                    j.tahan();
                    final int jumlahThread = thread.size();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String t = TeksUI.t("Tersambung ke proses") + " " + pid + ". "
                                    + TeksUI.t("Versi JDWP:") + " " + versi + ". "
                                    + TeksUI.t("Jumlah thread:") + " " + jumlahThread + ". "
                                    + TeksUI.t("Proses ditahan.");
                            tvHasil.setText(t);
                            announce(t);
                        }
                    });
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal menyambung ke proses:") + " " + e.getMessage());
                }
            }
        }, "DebuggerJdwpProses").start();
    }

    /** "Nyalakan/Matikan sambung ulang otomatis". */
    private void alihkanOtomatis() {
        boolean nyala = LayananAdbOtomatis.apakahOtomatis(this);
        if (nyala) {
            LayananAdbOtomatis.berhenti(this);
            LayananAdbOtomatis.setOtomatis(this, false);
            String t = TeksUI.t("Sambung ulang otomatis dimatikan.");
            tvHasil.setText(t);
            announce(t);
        } else {
            if (!LayananAdbOtomatis.apakahTerpasang(this)) {
                String t = TeksUI.t("Belum ada pairing tersimpan. Pasangkan dulu lewat Pasangkan.");
                tvHasil.setText(t);
                announce(t);
                return;
            }
            LayananAdbOtomatis.setOtomatis(this, true);
            LayananAdbOtomatis.mulai(this);
            String t = TeksUI.t("Sambung ulang otomatis dinyalakan. Bila WiFi mati lalu menyala lagi atau HP restart, sambungan tersambung kembali sendiri tanpa kode pairing lagi.");
            tvHasil.setText(t);
            announce(t);
        }
        segarkanTombolOtomatis();
    }

    // ---------------- Titik henti / langkah / bingkai proses sungguhan ----------------

    private String tandaKelas(String namaKelas) {
        return "L" + namaKelas.replace('.', '/') + ";";
    }

    /** "Pasang titik henti (proses)": cari kelas + metode + baris, pasang, lanjutkan. */
    private void pasangTitikHentiProses() {
        if (klienJdwp == null || !klienJdwp.tersambung()) {
            String t = TeksUI.t("Belum tersambung ke proses target.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        final String kelas = eKelas.getText().toString().trim();
        final String metode = eMetode.getText().toString().trim();
        final String barisTeks = eBarisDebug.getText().toString().trim();
        if (kelas.isEmpty() || metode.isEmpty() || barisTeks.isEmpty()) {
            String t = TeksUI.t("Isi dulu kelas, metode, dan nomor baris.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        final int baris;
        try {
            baris = Integer.parseInt(barisTeks);
        } catch (NumberFormatException e) {
            String t = TeksUI.t("Nomor baris harus angka.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        tvHasil.setText(TeksUI.t("Memasang titik henti..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long classId = klienJdwp.cariKelas(tandaKelas(kelas));
                    long methodId = -1;
                    List<DebuggerLangkah.MetodeJdwp> daftar =
                            klienJdwp.metodeKelas(classId);
                    for (int i = 0; i < daftar.size(); i++) {
                        if (metode.equals(daftar.get(i).nama)) {
                            methodId = daftar.get(i).id;
                            break;
                        }
                    }
                    if (methodId < 0) {
                        tampilkanHasil(TeksUI.t("Metode tidak ditemukan:") + " " + metode);
                        return;
                    }
                    Long indeks = klienJdwp.tabelBaris(classId, methodId)
                            .get(Integer.valueOf(baris));
                    if (indeks == null) {
                        tampilkanHasil(TeksUI.t("Nomor baris tidak ada di metode ini."));
                        return;
                    }
                    final long id = klienJdwp.pasangTitikHenti(classId, methodId,
                            indeks.longValue());
                    klienJdwp.lanjutkan();
                    final int barisAkhir = baris;
                    tampilkanHasil(TeksUI.t("Titik henti dipasang di baris") + " " + barisAkhir
                            + " (" + TeksUI.t("id permintaan") + " " + id + "). "
                            + TeksUI.t("Proses dilanjutkan. Tekan Tunggu jeda (proses) saat aplikasi menjalankan baris itu."));
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal memasang titik henti:") + " " + e.getMessage());
                }
            }
        }, "DebuggerTitikHenti").start();
    }

    /** "Langkah satu baris (proses)": minta langkah, tunggu jeda, tampilkan bingkai. */
    private void langkahProses() {
        if (klienJdwp == null || threadTerpilih == 0) {
            String t = TeksUI.t("Belum tersambung ke proses target.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        tvHasil.setText(TeksUI.t("Melangkah satu baris..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    klienJdwp.mintaLangkah(threadTerpilih,
                            DebuggerLangkah.JdwpKlien.LEWAT);
                    klienJdwp.lanjutkan();
                    DebuggerLangkah.PeristiwaJdwp ev = klienJdwp.bacaPeristiwa();
                    tampilkanBingkaiDiLatar(ev);
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal melangkah:") + " " + e.getMessage());
                }
            }
        }, "DebuggerLangkahProses").start();
    }

    /** "Tampilkan bingkai dan variabel": tahan, baca bingkai + nilai variabel. */
    private void tampilkanBingkai() {
        if (klienJdwp == null || threadTerpilih == 0) {
            String t = TeksUI.t("Belum tersambung ke proses target.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        tvHasil.setText(TeksUI.t("Membaca bingkai dan variabel..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    klienJdwp.tahan();
                    tampilkanBingkaiDiLatar(null);
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal membaca bingkai:") + " " + e.getMessage());
                }
            }
        }, "DebuggerBingkai").start();
    }

    /** "Tunggu jeda (proses)": baca satu peristiwa JDWP, tampilkan bingkai. */
    private void tungguJeda() {
        if (klienJdwp == null) {
            String t = TeksUI.t("Belum tersambung ke proses target.");
            tvHasil.setText(t);
            announce(t);
            return;
        }
        tvHasil.setText(TeksUI.t("Menunggu jeda..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DebuggerLangkah.PeristiwaJdwp ev = klienJdwp.bacaPeristiwa();
                    tampilkanBingkaiDiLatar(ev);
                } catch (final Throwable e) {
                    tampilkanHasil(TeksUI.t("Gagal menunggu jeda:") + " " + e.getMessage());
                }
            }
        }, "DebuggerTungguJeda").start();
    }

    /** Baca bingkai + variabel thread terpilih, tampilkan di UI (jalan di latar). */
    private void tampilkanBingkaiDiLatar(DebuggerLangkah.PeristiwaJdwp ev) {
        try {
            final List<DebuggerLangkah.BingkaiJdwp> bingkai =
                    klienJdwp.bingkai(threadTerpilih, 0, 10);
            StringBuilder sb = new StringBuilder();
            if (ev != null) {
                sb.append(TeksUI.t("Peristiwa:")).append(" ")
                        .append(ev.jenis == 2
                                ? TeksUI.t("titik henti") : TeksUI.t("langkah"))
                        .append("\n");
            }
            if (bingkai.isEmpty()) {
                sb.append(TeksUI.t("Tidak ada bingkai."));
            }
            for (int i = 0; i < bingkai.size(); i++) {
                DebuggerLangkah.BingkaiJdwp b = bingkai.get(i);
                sb.append(TeksUI.t("Bingkai")).append(" ").append(i).append(": ")
                        .append(b.namaKelas).append(".").append(b.namaMetode)
                        .append(" ").append(TeksUI.t("baris")).append(" ")
                        .append(b.baris).append("\n");
                if (i == 0) {
                    List<DebuggerLangkah.VariabelJdwp> var =
                            klienJdwp.tabelVariabel(b.classId, b.methodId);
                    List<DebuggerLangkah.VariabelJdwp> terlihat =
                            new ArrayList<DebuggerLangkah.VariabelJdwp>();
                    for (int j = 0; j < var.size(); j++) {
                        DebuggerLangkah.VariabelJdwp v = var.get(j);
                        if (b.indeksKode >= v.indeksKode
                                && b.indeksKode < v.indeksKode + v.panjang) {
                            terlihat.add(v);
                        }
                    }
                    if (!terlihat.isEmpty()) {
                        int[] slot = new int[terlihat.size()];
                        byte[] penanda = new byte[terlihat.size()];
                        for (int j = 0; j < terlihat.size(); j++) {
                            slot[j] = terlihat.get(j).slot;
                            penanda[j] = terlihat.get(j).penanda();
                        }
                        List<DebuggerLangkah.NilaiJdwp> nilai =
                                klienJdwp.nilaiBingkai(b.idBingkai, slot, penanda);
                        for (int j = 0; j < nilai.size() && j < terlihat.size(); j++) {
                            sb.append("  ").append(terlihat.get(j).nama).append(" = ")
                                    .append(nilai.get(j).teks).append("\n");
                        }
                    }
                }
            }
            tampilkanHasil(sb.toString());
        } catch (final Throwable e) {
            tampilkanHasil(TeksUI.t("Gagal membaca bingkai:") + " " + e.getMessage());
        }
    }

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
