package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.FileProvider;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Kolaborasi Langsung (Tahap 3D).
 *
 * Kirim: pilih proyek, tulis catatan perubahan (apa yang diubah), lalu
 * bagikan paket kolaborasi (ZIP proyek + CATATAN.txt di dalamnya) lewat
 * aplikasi berbagi (WhatsApp, Telegram, dll.).
 *
 * Terima: pilih ZIP balasan dari teman — catatannya ditampilkan dulu,
 * lalu pengguna memilih Terapkan (timpa proyek lama dengan isi ZIP)
 * atau Batal. ZIP tanpa CATATAN.txt ditolak dengan pesan jelas (itu
 * bukan paket kolaborasi — pakai Impor proyek untuk ZIP biasa).
 *
 * Memakai Kolaborasi (murni Java) + ArsipProyek.ekstrakZip() untuk
 * menerapkan balasan (verifikasi init.lua di dalam ZIP).
 */
public class KolaborasiActivity extends Activity {
    private static final int MINTA_BALASAN = 3;

    private Spinner spinnerProyek;
    private EditText ePengirim;
    private EditText eCatatan;
    private final List<String> daftarProyek = new ArrayList<String>();

    // Ruang Kolaborasi Lokal (Fitur 8, tanpa internet): soket TCP tipis
    // di activity; protokol + operasi dokumen ada di KolaborasiLokal.
    private TextView tvSesi;
    private TextView tvCrash;
    private EditText eIp;
    private EditText ePin;
    private EditText eDokumen;
    private String pinSesi;
    private String dokumenTerkirim = "";
    private ServerSocket server;
    private Socket soket;
    private OutputStream keluar;
    private Thread utasServer;
    private volatile boolean sesiJalan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Kolaborasi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Kolaborasi"), 20);
        label(r, TeksUI.t("Kerja bareng teman: kirim proyek beserta catatan perubahanmu, ")
                + TeksUI.t("lalu terima balasannya. Catatan perubahan selalu ditampilkan ")
                + TeksUI.t("sebelum balasan diterapkan."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama kamu (pengirim, boleh kosong):"), false);
        ePengirim = new EditText(this);
        GayaStudio.edit(ePengirim);
        ePengirim.setHint(TeksUI.t("Contoh: Al Kautsar"));
        ePengirim.setSingleLine(true);
        r.addView(ePengirim, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Catatan perubahan (apa yang kamu ubah?):"), false);
        eCatatan = new EditText(this);
        GayaStudio.edit(eCatatan);
        eCatatan.setHint("Contoh: Menambah tombol simpan di layar utama");
        eCatatan.setSingleLine(false);
        eCatatan.setMinLines(3);
        r.addView(eCatatan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Bagikan untuk Kolaborasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bagikan();
            }
        });

        button(r, TeksUI.t("Terima Balasan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terimaBalasan();
            }
        });

        // ---- Ruang Kolaborasi Lokal (Fitur 8, tanpa internet) ----
        heading(r, TeksUI.t("Ruang Kolaborasi Lokal (tanpa internet)"), 18);
        label(r, TeksUI.t("Satu HP membuka sesi, HP lain bergabung lewat alamat IP dan PIN. ")
                + TeksUI.t("Perubahan teks dikirim waktu-nyata lewat Wi-Fi atau hotspot yang sama, ")
                + TeksUI.t("tanpa internet."), false);

        tvSesi = label(r, TeksUI.t("Sesi belum dibuka."), false);

        label(r, TeksUI.t("Alamat IP rekan:"), false);
        eIp = new EditText(this);
        GayaStudio.edit(eIp);
        eIp.setHint(TeksUI.t("Contoh: 192.168.43.1"));
        eIp.setSingleLine(true);
        r.addView(eIp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("PIN sesi (6 digit):"), false);
        ePin = new EditText(this);
        GayaStudio.edit(ePin);
        ePin.setHint("123456");
        ePin.setSingleLine(true);
        r.addView(ePin, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buka Sesi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaSesi();
            }
        });

        button(r, TeksUI.t("Gabung ke Sesi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gabungSesi();
            }
        });

        button(r, TeksUI.t("Tutup Sesi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tutupSesi();
                tampilSesi(TeksUI.t("Sesi ditutup."));
                announce(TeksUI.t("Sesi ditutup."));
            }
        });

        label(r, TeksUI.t("Dokumen yang disunting bersama:"), false);
        eDokumen = new EditText(this);
        GayaStudio.editor(eDokumen);
        eDokumen.setHint(TeksUI.t("Tulis atau tempel kode di sini, lalu tekan Kirim Perubahan."));
        eDokumen.setSingleLine(false);
        eDokumen.setMinLines(6);
        r.addView(eDokumen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Kirim Perubahan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kirimPerubahan();
            }
        });

        button(r, TeksUI.t("Kirim APK Hasil Build"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kirimApk();
            }
        });

        label(r, TeksUI.t("Laporan crash dari ponsel penguji:"), false);
        tvCrash = new TextView(this);
        tvCrash.setText(TeksUI.t("(belum ada laporan crash)"));
        tvCrash.setTextIsSelectable(true);
        tvCrash.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvCrash);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce(TeksUI.t("Kolaborasi. Pilih proyek, tulis catatan perubahan, lalu bagikan."));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MINTA_BALASAN) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        terimaDariUri(data.getData());
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        File root = new File(BuatProyekHelper.root());
        File[] anak = root.listFiles();
        if (anak != null) {
            for (int i = 0; i < anak.length; i++) {
                if (anak[i].isDirectory()
                        && new File(anak[i], "init.lua").isFile()) {
                    daftarProyek.add(anak[i].getName());
                }
            }
        }
        java.util.Collections.sort(daftarProyek, String.CASE_INSENSITIVE_ORDER);
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private String proyekTerpilih() {
        if (spinnerProyek == null) return null;
        Object item = spinnerProyek.getSelectedItem();
        if (item == null) return null;
        String nama = String.valueOf(item);
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) return null;
        return nama;
    }

    private void bagikan() {
        String nama = proyekTerpilih();
        if (nama == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File dirProyek = new File(BuatProyekHelper.root() + "/" + nama);
        String catatan = eCatatan.getText().toString().trim();
        String pengirim = ePengirim.getText().toString().trim();
        File paket = Kolaborasi.buatPaket(dirProyek, catatan, pengirim,
                new File(BuatProyekHelper.root()));
        if (paket == null) {
            Toast.makeText(this, TeksUI.t("Gagal membuat paket kolaborasi."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName(), paket);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("application/zip");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_SUBJECT, TeksUI.t("Kolaborasi proyek ") + nama);
            it.putExtra(Intent.EXTRA_TEXT, TeksUI.t("Paket kolaborasi proyek \"") + nama
                    + TeksUI.t("\" dari Create Aplikasi. Catatan perubahan ada di dalam ")
                    + TeksUI.t("berkas CATATAN.txt di ZIP ini."));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, TeksUI.t("Bagikan paket kolaborasi")));
            announce(TeksUI.t("Paket kolaborasi proyek ") + nama + TeksUI.t(" siap dibagikan."));
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Gagal membagikan: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void terimaBalasan() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("application/zip");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(it, TeksUI.t("Pilih ZIP balasan")),
                    MINTA_BALASAN);
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Gagal membuka pemilih berkas: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void terimaDariUri(Uri uri) {
        File sementara = new File(getCacheDir(), "kolaborasi-balasan.zip");
        String galatSalin = null;
        InputStream masuk = null;
        try {
            masuk = getContentResolver().openInputStream(uri);
            galatSalin = ArsipProyek.salinAliran(masuk, sementara);
        } catch (Throwable t) {
            galatSalin = TeksUI.t("Gagal membaca berkas: ") + t.getMessage();
        }
        if (galatSalin != null) {
            Toast.makeText(this, galatSalin, Toast.LENGTH_SHORT).show();
            return;
        }
        final String catatan = Kolaborasi.bacaCatatan(sementara);
        if (catatan == null) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Bukan paket kolaborasi"))
                    .setMessage(TeksUI.t("Berkas ini tidak berisi CATATAN.txt, jadi bukan ")
                            + TeksUI.t("paket kolaborasi. Untuk ZIP proyek biasa, pakai ")
                            + TeksUI.t("Impor proyek di Kelola Project."))
                    .setPositiveButton("OK", null)
                    .show();
            announce(TeksUI.t("Berkas ini bukan paket kolaborasi, tidak ada catatan perubahan di dalamnya."));
            sementara.delete();
            return;
        }
        String nama = ArsipProyek.namaProyekDariIsi(sementara);
        if (nama == null || nama.isEmpty()) {
            nama = ArsipProyek.namaProyekDariBerkas(sementara.getName());
        }
        final String namaProyek = nama;
        final File zipBalasan = sementara;
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Balasan untuk proyek \"") + namaProyek + "\"")
                .setMessage(TeksUI.t("Catatan perubahan dari teman:") + "\n" + "\n" + catatan
                        + "\n" + "\n" + TeksUI.t("Terapkan balasan ini ke proyek \"") + namaProyek + "\"?")
                .setPositiveButton(TeksUI.t("Terapkan"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        terapkanBalasan(zipBalasan, namaProyek);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        zipBalasan.delete();
                    }
                })
                .show();
        announce(TeksUI.t("Balasan diterima. Catatan perubahan: ") + catatan);
    }

    private void terapkanBalasan(final File zip, final String nama) {
        final File tujuan = new File(BuatProyekHelper.root() + "/" + nama);
        if (tujuan.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Proyek sudah ada"))
                    .setMessage(TeksUI.t("Proyek \"") + nama + TeksUI.t("\" sudah ada. Timpa proyek ")
                            + TeksUI.t("lama dengan balasan ini?"))
                    .setPositiveButton(TeksUI.t("Timpa"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ArsipProyek.hapusRekursif(tujuan);
                            selesaikanTerapkan(zip, tujuan);
                        }
                    })
                    .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            zip.delete();
                        }
                    })
                    .show();
            announce(TeksUI.t("Proyek ") + nama + TeksUI.t(" sudah ada. Timpa proyek lama dengan balasan ini?"));
            return;
        }
        selesaikanTerapkan(zip, tujuan);
    }

    private void selesaikanTerapkan(File zip, File tujuan) {
        String galat = ArsipProyek.ekstrakZip(zip, tujuan);
        zip.delete();
        if (galat != null) {
            Toast.makeText(this, galat, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, TeksUI.t("Balasan diterapkan ke proyek \"") + tujuan.getName() + "\"",
                Toast.LENGTH_SHORT).show();
        announce(TeksUI.t("Balasan diterapkan ke proyek ") + tujuan.getName() + ".");
        muatDaftarProyek();
    }

    @Override
    protected void onDestroy() {
        tutupSesi();
        super.onDestroy();
    }

    // ------------------------------------------- Ruang Kolaborasi Lokal

    /** Alamat IPv4 HP ini di jaringan lokal; null bila tidak ada. */
    private String ipLokal() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                Enumeration<InetAddress> alamat = ni.getInetAddresses();
                while (alamat.hasMoreElements()) {
                    InetAddress a = alamat.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void tampilSesi(final String teks) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvSesi != null) tvSesi.setText(teks);
            }
        });
    }

    /** Membuka sesi: peladen soket di IP lokal + PIN 6 digit baru. */
    private void bukaSesi() {
        if (sesiJalan) {
            Toast.makeText(this, TeksUI.t("Sesi sudah dibuka."), Toast.LENGTH_SHORT).show();
            return;
        }
        pinSesi = KolaborasiLokal.buatPin();
        ePin.setText(pinSesi);
        final String ip = ipLokal();
        sesiJalan = true;
        utasServer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server = new ServerSocket(KolaborasiLokal.PORT_BAWAAN);
                    tampilSesi(TeksUI.t("Sesi dibuka. IP: ") + ip
                            + TeksUI.t(", PIN: ") + pinSesi
                            + TeksUI.t(", port: ") + KolaborasiLokal.PORT_BAWAAN);
                    announce(TeksUI.t("Sesi dibuka. IP: ") + ip
                            + TeksUI.t(", PIN: ") + pinSesi);
                    while (sesiJalan) {
                        Socket klien = server.accept();
                        if (soket != null && !soket.isClosed()) {
                            try {
                                klien.close();
                            } catch (Throwable ignored) {
                            }
                            continue;
                        }
                        layaniKlien(klien, true);
                    }
                } catch (Throwable t) {
                    if (sesiJalan) {
                        tampilSesi(TeksUI.t("Gagal membuka sesi: ") + t.getMessage());
                    }
                }
            }
        });
        utasServer.start();
        if (ip == null) {
            Toast.makeText(this, TeksUI.t("Alamat IP tidak ditemukan. Sambungkan Wi-Fi atau hotspot dulu."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Bergabung ke sesi rekan lewat IP + PIN yang diisi. */
    private void gabungSesi() {
        if (soket != null && !soket.isClosed()) {
            Toast.makeText(this, TeksUI.t("Sudah terhubung ke sesi."), Toast.LENGTH_SHORT).show();
            return;
        }
        final String ip = eIp.getText().toString().trim();
        final String pin = ePin.getText().toString().trim();
        if (ip.length() == 0) {
            Toast.makeText(this, TeksUI.t("Isi dulu alamat IP rekan."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!KolaborasiLokal.pinValid(pin)) {
            Toast.makeText(this, TeksUI.t("PIN harus 6 digit angka."), Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket(ip, KolaborasiLokal.PORT_BAWAAN);
                    soket = s;
                    keluar = s.getOutputStream();
                    kirimBaris(KolaborasiLokal.buatGabung(pin));
                    tampilSesi(TeksUI.t("Terhubung ke ") + ip + ".");
                    announce(TeksUI.t("Terhubung ke sesi rekan."));
                    layaniKlien(s, false);
                } catch (Throwable t) {
                    tampilSesi(TeksUI.t("Gagal bergabung: ") + t.getMessage());
                    announce(TeksUI.t("Gagal bergabung: ") + t.getMessage());
                }
            }
        }).start();
    }

    /** Menutup peladen dan soket sesi (aman dipanggil berkali-kali). */
    private void tutupSesi() {
        sesiJalan = false;
        try {
            if (server != null) server.close();
        } catch (Throwable ignored) {
        }
        try {
            if (soket != null) soket.close();
        } catch (Throwable ignored) {
        }
        server = null;
        soket = null;
        keluar = null;
    }

    /**
     * Utas penerima satu klien: membaca baris demi baris (bacaBaris
     * mentah, bukan BufferedReader, supaya isi biner APK sesudah header
     * tidak tertelan penyangga). Sebagai peladen, pesan sebelum GABUNG
     * ber-PIN benar diabaikan.
     */
    private void layaniKlien(final Socket s, final boolean sebagaiServer) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean gabungDiterima = !sebagaiServer;
                try {
                    InputStream masuk = s.getInputStream();
                    String baris;
                    while ((baris = bacaBaris(masuk)) != null) {
                        KolaborasiLokal.Pesan pesan = KolaborasiLokal.uraiPesan(baris);
                        if (pesan == null) continue;
                        if (KolaborasiLokal.JENIS_GABUNG.equals(pesan.jenis)) {
                            if (!sebagaiServer) continue;
                            if (pinSesi != null && pinSesi.equals(
                                    KolaborasiLokal.pinDariGabung(pesan))) {
                                gabungDiterima = true;
                                soket = s;
                                keluar = s.getOutputStream();
                                tampilSesi(TeksUI.t("Rekan bergabung."));
                                announce(TeksUI.t("Rekan bergabung."));
                            } else {
                                tampilSesi(TeksUI.t("PIN salah, rekan ditolak."));
                                announce(TeksUI.t("PIN salah, rekan ditolak."));
                                try {
                                    s.close();
                                } catch (Throwable ignored) {
                                }
                            }
                            continue;
                        }
                        if (!gabungDiterima) continue;
                        if (KolaborasiLokal.JENIS_SISIP.equals(pesan.jenis)
                                || KolaborasiLokal.JENIS_HAPUS.equals(pesan.jenis)) {
                            terapkanDariRekan(KolaborasiLokal.uraiOperasi(pesan));
                        } else if (KolaborasiLokal.JENIS_APK.equals(pesan.jenis)) {
                            terimaApk(masuk, KolaborasiLokal.uraiApk(pesan));
                        } else if (KolaborasiLokal.JENIS_CRASH.equals(pesan.jenis)) {
                            tampilCrash(KolaborasiLokal.uraiCrash(pesan));
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (s == soket) {
                        soket = null;
                        keluar = null;
                        tampilSesi(TeksUI.t("Rekan terputus."));
                    }
                    try {
                        s.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }).start();
    }

    /** Membaca satu baris (sampai '\n') dari aliran mentah; null bila EOF. */
    private String bacaBaris(InputStream masuk) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        int c;
        while ((c = masuk.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (c < 0 && b.size() == 0) return null;
        return new String(b.toByteArray(), "UTF-8");
    }

    /** Menerapkan operasi rekan ke dokumen di layar + pemberitahuan suara. */
    private void terapkanDariRekan(final KolaborasiLokal.Operasi op) {
        if (op == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (eDokumen == null) return;
                String dokBaru = KolaborasiLokal.terapkanOperasi(
                        eDokumen.getText().toString(), op);
                dokumenTerkirim = dokBaru;
                eDokumen.setText(dokBaru);
                try {
                    int pos = Math.min(dokBaru.length(),
                            op.posisi + (op.teks == null ? 0 : op.teks.length()));
                    eDokumen.setSelection(Math.max(0, pos));
                } catch (Throwable ignored) {
                }
                announce(TeksUI.t("Rekan menyunting berkas."));
            }
        });
    }

    /** Menerima isi biner APK sesudah header; disimpan di cache. */
    private void terimaApk(InputStream masuk, KolaborasiLokal.InfoApk info) {
        if (info == null) return;
        try {
            File dir = new File(getCacheDir(), "kolaborasi-apk");
            dir.mkdirs();
            File tujuan = new File(dir, info.nama);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tujuan));
            try {
                byte[] buf = new byte[16384];
                long sisa = info.ukuran;
                while (sisa > 0) {
                    int n = masuk.read(buf, 0, (int) Math.min(buf.length, sisa));
                    if (n < 0) break;
                    out.write(buf, 0, n);
                    sisa -= n;
                }
            } finally {
                out.close();
            }
            final String nama = info.nama;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(KolaborasiActivity.this,
                            TeksUI.t("APK diterima: ") + nama, Toast.LENGTH_SHORT).show();
                }
            });
            announce(TeksUI.t("APK diterima: ") + nama);
        } catch (Throwable t) {
            announce(TeksUI.t("Gagal menerima APK: ") + t.getMessage());
        }
    }

    /** Menampilkan laporan crash kiriman ponsel penguji di layar. */
    private void tampilCrash(final String[] crash) {
        if (crash == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tvCrash != null) {
                    tvCrash.setText(TeksUI.t("ID crash: ") + crash[0] + "\n" + crash[1]);
                }
            }
        });
        announce(TeksUI.t("Laporan crash diterima dari ponsel penguji. ID: ") + crash[0]);
    }

    /** Mengirim satu baris protokol ke rekan; false bila belum terhubung. */
    private boolean kirimBaris(String baris) {
        if (baris == null || keluar == null) return false;
        try {
            synchronized (this) {
                keluar.write((baris + "\n").getBytes("UTF-8"));
                keluar.flush();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Mengirim perubahan dokumen sebagai operasi HAPUS lalu SISIP
     * (selisih awalan/akhiran bersama). Tanpa perubahan, tidak mengirim.
     */
    private void kirimPerubahan() {
        if (keluar == null) {
            Toast.makeText(this, TeksUI.t("Belum terhubung. Buka sesi atau gabung dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String baru = eDokumen.getText().toString();
        String lama = dokumenTerkirim == null ? "" : dokumenTerkirim;
        if (baru.equals(lama)) {
            Toast.makeText(this, TeksUI.t("Tidak ada perubahan untuk dikirim."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int awal = 0;
        while (awal < lama.length() && awal < baru.length()
                && lama.charAt(awal) == baru.charAt(awal)) {
            awal++;
        }
        int akhirLama = lama.length();
        int akhirBaru = baru.length();
        while (akhirLama > awal && akhirBaru > awal
                && lama.charAt(akhirLama - 1) == baru.charAt(akhirBaru - 1)) {
            akhirLama--;
            akhirBaru--;
        }
        boolean ok = true;
        if (akhirLama > awal) {
            ok = kirimBaris(KolaborasiLokal.buatHapus(awal, akhirLama - awal)) && ok;
        }
        if (akhirBaru > awal) {
            ok = kirimBaris(KolaborasiLokal.buatSisip(awal,
                    baru.substring(awal, akhirBaru))) && ok;
        }
        if (ok) {
            dokumenTerkirim = baru;
            announce(TeksUI.t("Perubahan dikirim ke rekan."));
        } else {
            Toast.makeText(this, TeksUI.t("Gagal mengirim perubahan. Periksa sambungan."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Mengirim APK hasil build terbaru proyek terpilih ke rekan. */
    private void kirimApk() {
        if (keluar == null) {
            Toast.makeText(this, TeksUI.t("Belum terhubung. Buka sesi atau gabung dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String nama = proyekTerpilih();
        if (nama == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File apk = apkTerbaru(new File(BuatProyekHelper.root() + "/" + nama + "/bin"));
        if (apk == null) {
            Toast.makeText(this, TeksUI.t("Belum ada APK hasil build di folder bin proyek ini. ")
                            + TeksUI.t("Bangun dulu proyeknya sampai APK-nya jadi."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final File berkasApk = apk;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String header = KolaborasiLokal.buatApk(berkasApk.getName(), berkasApk.length());
                    if (header == null) return;
                    synchronized (KolaborasiActivity.this) {
                        keluar.write((header + "\n").getBytes("UTF-8"));
                        java.io.FileInputStream in = new java.io.FileInputStream(berkasApk);
                        try {
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) keluar.write(buf, 0, n);
                        } finally {
                            in.close();
                        }
                        keluar.flush();
                    }
                    announce(TeksUI.t("APK dikirim ke rekan: ") + berkasApk.getName());
                } catch (Throwable t) {
                    tampilSesi(TeksUI.t("Gagal mengirim APK: ") + t.getMessage());
                }
            }
        }).start();
    }

    /** APK *.apk terbaru di folder bin; null bila tidak ada. */
    private File apkTerbaru(File dirBin) {
        if (dirBin == null || !dirBin.isDirectory()) return null;
        File[] anak = dirBin.listFiles();
        File terbaru = null;
        if (anak != null) {
            for (int i = 0; i < anak.length; i++) {
                if (anak[i].isFile() && anak[i].getName().endsWith(".apk")) {
                    if (terbaru == null || anak[i].lastModified() > terbaru.lastModified()) {
                        terbaru = anak[i];
                    }
                }
            }
        }
        return terbaru;
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

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
