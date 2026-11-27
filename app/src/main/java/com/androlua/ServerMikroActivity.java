package com.androlua;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generator Server Mikro Lokal dan Backend Tersemat (Fitur 4, daftar Gemini).
 *
 * Pembungkus tipis di atas ServerMikro (logika murni, sudah diuji di
 * komputer): menjalankan peladen HTTP ringan berbasis soket lokal di
 * latar belakang untuk pengujian, menampilkan log permintaan/respons,
 * dan menyalin alamat IP lokal. Definisi rute dan data JSON disimpan
 * di penyimpanan privat aplikasi.
 *
 * Pembundelan ke APK hasil build: "Bundel Server ke Proyek"
 * menyuntikkan PeladenMikroActivity + aset definisi rute ke proyek
 * dan mendaftarkannya di manifest proyek (ikon "Server Mikro") plus
 * izin INTERNET. Build APK seperti biasa — server itu berjalan dari
 * dalam aplikasi hasil build saat ikonnya dibuka.
 */
public class ServerMikroActivity extends Activity {

    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eDefinisi;
    private EditText ePort;
    private EditText eKunci;
    private TextView tvAlamat;
    private TextView tvLog;
    private Button bMulai;
    private Button bBerhenti;

    private volatile boolean jalan;
    private ServerSocket server;
    private Thread utasServer;
    private List<ServerMikro.Rute> ruteAktif = new ArrayList<ServerMikro.Rute>();
    private final Map<String, String> penyimpanan = new LinkedHashMap<String, String>();
    private final ServerMikro.LogServer log = new ServerMikro.LogServer();
    private int portAktif = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Server Mikro Lokal"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Server Mikro Lokal"), 20);
        label(r, TeksUI.t("Buat titik akhir REST API yang berjalan di HP ini untuk pengujian. ")
                + TeksUI.t("Tulis definisi rute, tekan Mulai Server, lalu buka alamatnya dari ")
                + TeksUI.t("peramban atau aplikasi lain di jaringan Wi-Fi yang sama. ")
                + TeksUI.t("Data yang dikirim lewat POST tersimpan sebagai JSON di HP ini."), false);

        label(r, TeksUI.t("Definisi rute (satu rute per baris):"), false);
        eDefinisi = new EditText(this);
        eDefinisi.setMinLines(6);
        eDefinisi.setSingleLine(false);
        eDefinisi.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        eDefinisi.setText(muatDefinisi());
        GayaStudio.editor(eDefinisi);
        r.addView(eDefinisi, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Port server:"), false);
        ePort = new EditText(this);
        ePort.setText("8080");
        ePort.setSingleLine(true);
        ePort.setInputType(InputType.TYPE_CLASS_NUMBER);
        GayaStudio.edit(ePort);
        r.addView(ePort, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Kunci API (kosong = rute [kunci] tidak bisa dibuka):"), false);
        eKunci = new EditText(this);
        eKunci.setSingleLine(true);
        GayaStudio.edit(eKunci);
        r.addView(eKunci, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bMulai = button(r, TeksUI.t("Mulai Server"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });
        bBerhenti = button(r, TeksUI.t("Berhenti"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                berhenti(true);
            }
        });
        bBerhenti.setEnabled(false);
        button(r, TeksUI.t("Salin Alamat Server"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salinAlamat();
            }
        });

        label(r, TeksUI.t("Bundel server ke APK hasil build (pilih proyek):"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Bundel Server ke Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bundelKeProyek();
            }
        });
        button(r, TeksUI.t("Lepas Bundel Server"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lepasBundel();
            }
        });

        label(r, TeksUI.t("Alamat server:"), false);
        tvAlamat = new TextView(this);
        tvAlamat.setTextSize(14);
        tvAlamat.setTextIsSelectable(true);
        tvAlamat.setPadding(0, dp(4), 0, dp(4));
        tvAlamat.setText(TeksUI.t("Server belum berjalan."));
        r.addView(tvAlamat);

        label(r, TeksUI.t("Log permintaan:"), false);
        tvLog = new TextView(this);
        tvLog.setTextSize(13);
        tvLog.setTextIsSelectable(true);
        GayaStudio.terminal(tvLog);
        r.addView(tvLog);
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatPenyimpanan();
        muatDaftarProyek();
        announce(TeksUI.t("Server Mikro Lokal. Tulis definisi rute, lalu tekan Mulai Server."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
        muatDaftarProyek();
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private File proyekTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return null;
        }
        return new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
    }

    private void bundelKeProyek() {
        final File dir = proyekTerpilih();
        if (dir == null) return;
        final String definisi = eDefinisi.getText().toString();
        final String kunci = eKunci.getText().toString();
        int port;
        try {
            port = Integer.parseInt(ePort.getText().toString().trim());
        } catch (NumberFormatException e) {
            toast(TeksUI.t("Port tidak valid. Tulis angka 1024 sampai 65535."));
            return;
        }
        final int portAkhir = port;
        tvLog.setText(TeksUI.t("Membundel server ke proyek..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = ServerMikro.bundelKeProyek(dir, definisi, portAkhir, kunci);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvLog.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "ServerMikro-Bundel").start();
    }

    private void lepasBundel() {
        final File dir = proyekTerpilih();
        if (dir == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = ServerMikro.lepasBundel(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvLog.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "ServerMikro-Lepas").start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        berhenti(false);
    }

    private File fileDefinisi() {
        return new File(getFilesDir(), "server-mikro.txt");
    }

    private File fileData() {
        return new File(getFilesDir(), "server-data.json");
    }

    private String muatDefinisi() {
        String isi = SumberDaya.teks(fileDefinisi());
        if (isi != null && isi.trim().length() > 0) return isi;
        return ServerMikro.contohDefinisi();
    }

    private void muatPenyimpanan() {
        synchronized (penyimpanan) {
            penyimpanan.clear();
            penyimpanan.putAll(ServerMikro.muat(fileData()));
        }
    }

    private void mulai() {
        if (jalan) return;
        int port;
        try {
            port = Integer.parseInt(ePort.getText().toString().trim());
        } catch (NumberFormatException e) {
            toast(TeksUI.t("Port tidak valid. Tulis angka 1024 sampai 65535."));
            return;
        }
        if (port < 1024 || port > 65535) {
            toast(TeksUI.t("Port tidak valid. Tulis angka 1024 sampai 65535."));
            return;
        }
        String definisi = eDefinisi.getText().toString();
        List<ServerMikro.Rute> rute = ServerMikro.uraiDefinisi(definisi);
        if (rute.isEmpty()) {
            toast(TeksUI.t("Definisi rute kosong atau rusak. Periksa formatnya."));
            announce(TeksUI.t("Definisi rute kosong atau rusak. Periksa formatnya."));
            return;
        }
        SumberDaya.tulis(fileDefinisi(), definisi);
        ruteAktif = rute;
        final String kunci = eKunci.getText().toString();
        try {
            server = new ServerSocket(port);
        } catch (Throwable t) {
            tvAlamat.setText(TeksUI.t("Gagal memulai server di port ") + port + ": " + t.getMessage());
            announce(TeksUI.t("Gagal memulai server."));
            return;
        }
        portAktif = port;
        jalan = true;
        bMulai.setEnabled(false);
        bBerhenti.setEnabled(true);
        String alamat = alamatServer();
        tvAlamat.setText(alamat);
        tvLog.setText(log.teks());
        announce(TeksUI.t("Server berjalan di ") + alamat);
        utasServer = new Thread(new Runnable() {
            @Override
            public void run() {
                while (jalan) {
                    try {
                        final Socket soket = server.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                tanganiKoneksi(soket, kunci);
                            }
                        }, "ServerMikro-Koneksi").start();
                    } catch (Throwable t) {
                        if (jalan) {
                            log.catat("SERVER", "", 0, t.getMessage());
                            tampilkanLog();
                        }
                    }
                }
            }
        }, "ServerMikro");
        utasServer.start();
    }

    private void berhenti(boolean umumkan) {
        if (!jalan) return;
        jalan = false;
        try {
            if (server != null) server.close();
        } catch (Throwable ignored) {
        }
        server = null;
        portAktif = -1;
        bMulai.setEnabled(true);
        bBerhenti.setEnabled(false);
        tvAlamat.setText(TeksUI.t("Server belum berjalan."));
        if (umumkan) announce(TeksUI.t("Server berhenti."));
    }

    private String alamatServer() {
        String ip = ipLokal();
        return "http://" + ip + ":" + portAktif + "/halo";
    }

    private String ipLokal() {
        // Tanpa WifiManager: stub android-full.jar untuk WifiManager merujuk
        // androidx.annotation.RequiresApi yang tidak ada di classpath ECJ
        // (di build Gradle asli tersedia, tetapi dihindari supaya pohon tetap
        // kompilasi bersih dan alamat terbaca di jaringan apa pun, bukan
        // hanya Wi-Fi). Utamakan alamat IPv4 lokal (mis. 192.168.x.x).
        try {
            java.util.Enumeration<java.net.NetworkInterface> antarmuka =
                    java.net.NetworkInterface.getNetworkInterfaces();
            String cadangan = null;
            while (antarmuka.hasMoreElements()) {
                java.net.NetworkInterface ni = antarmuka.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> alamat =
                        ni.getInetAddresses();
                while (alamat.hasMoreElements()) {
                    java.net.InetAddress a = alamat.nextElement();
                    if (a.isLoopbackAddress()
                            || !(a instanceof java.net.Inet4Address)) continue;
                    if (a.isSiteLocalAddress()) return a.getHostAddress();
                    if (cadangan == null) cadangan = a.getHostAddress();
                }
            }
            if (cadangan != null) return cadangan;
        } catch (Throwable ignored) {
        }
        return "localhost";
    }

    private void salinAlamat() {
        if (!jalan || portAktif < 0) {
            toast(TeksUI.t("Server belum berjalan. Tekan Mulai Server dulu."));
            return;
        }
        String alamat = alamatServer();
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("alamat server", alamat));
            toast(TeksUI.t("Alamat disalin: ") + alamat);
            announce(TeksUI.t("Alamat disalin: ") + alamat);
        } catch (Throwable t) {
            toast(TeksUI.t("Gagal menyalin alamat: ") + t.getMessage());
        }
    }

    /** Menangani satu koneksi HTTP: baca permintaan, jawab JSON, catat log. */
    private void tanganiKoneksi(Socket soket, String kunciBenar) {
        try {
            InputStream masuk = soket.getInputStream();
            OutputStream keluar = soket.getOutputStream();
            String barisPermintaan = bacaBaris(masuk);
            if (barisPermintaan == null || barisPermintaan.trim().length() == 0) return;
            String[] bagian = barisPermintaan.trim().split("\\s+");
            if (bagian.length < 2) return;
            String metode = bagian[0];
            String jalurPenuh = bagian[1];
            int panjangIsi = 0;
            String tipeIsi = "";
            String kunciHeader = null;
            String h;
            while ((h = bacaBaris(masuk)) != null && h.trim().length() > 0) {
                String rendah = h.toLowerCase(Locale.US);
                if (rendah.startsWith("content-length:")) {
                    try {
                        panjangIsi = Integer.parseInt(h.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                    }
                } else if (rendah.startsWith("content-type:")) {
                    tipeIsi = h.substring(13).trim();
                } else if (rendah.startsWith("x-kunci-api:")) {
                    kunciHeader = h.substring(12).trim();
                }
            }
            String jalur = jalurPenuh;
            Map<String, String> param = new LinkedHashMap<String, String>();
            int tanya = jalurPenuh.indexOf('?');
            if (tanya >= 0) {
                jalur = jalurPenuh.substring(0, tanya);
                param.putAll(ServerMikro.uraiForm(jalurPenuh.substring(tanya + 1)));
            }
            if (panjangIsi > 0 && panjangIsi <= 1024 * 1024) {
                byte[] isiBita = new byte[panjangIsi];
                int dibaca = 0;
                while (dibaca < panjangIsi) {
                    int n = masuk.read(isiBita, dibaca, panjangIsi - dibaca);
                    if (n < 0) break;
                    dibaca += n;
                }
                String isi = new String(isiBita, 0, dibaca, "UTF-8");
                if (tipeIsi.contains("application/json")) {
                    param.putAll(ServerMikro.dariJson(isi));
                } else {
                    param.putAll(ServerMikro.uraiForm(isi));
                }
            }
            String kunciDiberikan = kunciHeader;
            if (kunciDiberikan == null) kunciDiberikan = param.get("kunci");
            final ServerMikro.Hasil hasil;
            synchronized (penyimpanan) {
                hasil = ServerMikro.tangani(ruteAktif, metode, jalur, param,
                        kunciDiberikan, kunciBenar, penyimpanan, log);
                ServerMikro.simpan(fileData(), penyimpanan);
            }
            tampilkanLog();
            byte[] badan = hasil.badan.getBytes("UTF-8");
            String status = hasil.kode == 200 ? "200 OK"
                    : hasil.kode == 401 ? "401 Unauthorized" : "404 Not Found";
            String kepala = "HTTP/1.1 " + status + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + badan.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            keluar.write(kepala.getBytes("UTF-8"));
            keluar.write(badan);
            keluar.flush();
        } catch (Throwable ignored) {
        } finally {
            try {
                soket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Membaca satu baris (diakhiri \n) dari aliran bita mentah. */
    private static String bacaBaris(InputStream masuk) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = masuk.read()) >= 0) {
            if (c == '\n') break;
            buf.write(c);
        }
        if (c < 0 && buf.size() == 0) return null;
        String s = new String(buf.toByteArray(), "UTF-8");
        if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void tampilkanLog() {
        final String teks = log.teks();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvLog.setText(teks);
            }
        });
    }

    // ------------------------------------------------------------------ UI

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
