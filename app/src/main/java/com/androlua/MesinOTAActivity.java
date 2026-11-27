package com.androlua;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mesin Pembaruan Over-The-Air dan Manajemen Rilis Bertahap Mandiri
 * (Fitur 14, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tulis isi versi lama dan versi baru (satu baris per
 * baris), tekan "Buat Paket Patch" — IDE menghitung selisih, membuat
 * paket patch bertanda tangan di folder proyek, dan memeriksa apakah
 * perangkat ini masuk tahap awal peluncuran 10 persen. Logika ada di
 * MesinOTA (murni Java, sudah diuji di komputer).
 *
 * BATAS: patch bekerja atas berkas TEKS (skrip, aset, konfigurasi)
 * DAN berkas BINER (DEX/APK) lewat delta biner yang ditandatangani
 * kunci privat RSA dan diverifikasi sebelum diterapkan. Patch TIDAK
 * bisa mengganti kode aplikasi yang sedang berjalan dari dalam
 * prosesnya — APK hasil patch ditandatangani ulang dengan kunci
 * proyek lalu dipasang lewat PackageInstaller, sehingga sistem
 * membuka ulang aplikasi dengan kode baru. Peluncuran bertahap
 * memakai hash ID perangkat; unduhan patch memakai alamat peladen
 * yang ditentukan pengembang.
 */
public class MesinOTAActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eLama;
    private EditText eBaru;
    private EditText eBerkasLama;
    private EditText eBerkasBaru;
    private EditText eServer;
    private EditText eRilis;
    private TextView tvHasil;
    private Button bJalankan;
    private File dirAktif;
    private File patchAktif;
    private File apkHasilAktif;
    private BroadcastReceiver penerimaPasang;
    private static final String AKSI_PASANG = "com.androlua.OTA_PASANG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Mesin Pembaruan OTA dan Rilis Bertahap"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Mesin Pembaruan OTA dan Rilis Bertahap"), 20);
        label(r, TeksUI.t("Buat paket patch kecil dengan sidik jari SHA-256 dari selisih dua versi, atur persentase peluncuran bertahap, dan kembali ke versi lama bila patch gagal."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Isi versi lama:"), false);
        eLama = new EditText(this);
        GayaStudio.edit(eLama);
        eLama.setHint(TeksUI.t("Contoh: tarif=5000"));
        eLama.setText("tarif=5000\nrute=makassar-gowa\njam=06.00");
        eLama.setTextSize(14);
        eLama.setMinLines(3);
        r.addView(eLama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Isi versi baru:"), false);
        eBaru = new EditText(this);
        GayaStudio.edit(eBaru);
        eBaru.setHint(TeksUI.t("Contoh: tarif=6000"));
        eBaru.setText("tarif=6000\nrute=makassar-gowa\njam=06.00");
        eBaru.setTextSize(14);
        eBaru.setMinLines(3);
        r.addView(eBaru, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Buat Paket Patch"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        heading(r, TeksUI.t("Patch Biner (DEX atau APK)"), 16);
        label(r, TeksUI.t("Patch biner bekerja atas bita mentah (bukan hanya teks). Patch ditandatangani kunci privat RSA dan diverifikasi sebelum diterapkan; hasil patch diperiksa SHA-256-nya. APK hasil patch ditandatangani ulang dengan kunci proyek lalu dipasang lewat pemasang sistem, sehingga aplikasi dibuka ulang dengan kode baru."), false);

        label(r, TeksUI.t("Berkas lama di proyek:"), false);
        eBerkasLama = new EditText(this);
        GayaStudio.edit(eBerkasLama);
        eBerkasLama.setHint(TeksUI.t("Contoh: bin/apk-lama.apk"));
        eBerkasLama.setText("bin/apk-lama.apk");
        eBerkasLama.setTextSize(14);
        eBerkasLama.setSingleLine(true);
        r.addView(eBerkasLama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Berkas baru di proyek:"), false);
        eBerkasBaru = new EditText(this);
        GayaStudio.edit(eBerkasBaru);
        eBerkasBaru.setHint(TeksUI.t("Contoh: bin/apk-baru.apk"));
        eBerkasBaru.setText("bin/apk-baru.apk");
        eBerkasBaru.setTextSize(14);
        eBerkasBaru.setSingleLine(true);
        r.addView(eBerkasBaru, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Alamat peladen patch:"), false);
        eServer = new EditText(this);
        GayaStudio.edit(eServer);
        eServer.setHint(TeksUI.t("Contoh: https://server-anda.com/ota"));
        eServer.setTextSize(14);
        eServer.setSingleLine(true);
        r.addView(eServer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama rilis patch:"), false);
        eRilis = new EditText(this);
        GayaStudio.edit(eRilis);
        eRilis.setHint(TeksUI.t("Contoh: patch-1"));
        eRilis.setText("patch-1");
        eRilis.setTextSize(14);
        eRilis.setSingleLine(true);
        r.addView(eRilis, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Kunci OTA"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiKunci();
            }
        });

        button(r, TeksUI.t("Buat Patch Biner Tertanda"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiPatchBiner();
            }
        });

        button(r, TeksUI.t("Unduh Patch dari Server"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiUnduh();
            }
        });

        button(r, TeksUI.t("Verifikasi dan Terapkan Patch Biner"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiTerapkanBiner();
            }
        });

        button(r, TeksUI.t("Pasang APK Hasil Patch"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiPasang();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Tulis isi dua versi, lalu tekan") + " "
                + TeksUI.t("Buat Paket Patch") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        daftarkanPenerimaPasang();
        announce(TeksUI.t("Mesin Pembaruan OTA dan Rilis Bertahap") + ". " + TeksUI.t("Belum ada hasil. Tulis isi dua versi, lalu tekan")
                + " " + TeksUI.t("Buat Paket Patch") + ".");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (penerimaPasang != null) unregisterReceiver(penerimaPasang);
        } catch (Throwable ignored) {
        }
    }

    /** Terima hasil pemasangan APK dari PackageInstaller dan umumkan. */
    private void daftarkanPenerimaPasang() {
        penerimaPasang = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                String pesan = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                String hasil;
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    hasil = TeksUI.t("APK hasil patch berhasil dipasang. Aplikasi akan dibuka ulang dengan kode baru.");
                } else {
                    hasil = TeksUI.t("Pemasangan APK gagal: ") + (pesan == null ? "" : pesan);
                }
                tvHasil.setText(hasil);
                announce(hasil);
                toast(hasil);
            }
        };
        registerReceiver(penerimaPasang, new IntentFilter(AKSI_PASANG));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private void mulai() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final String lama = eLama.getText().toString();
        final String baru = eBaru.getText().toString();
        tvHasil.setText(TeksUI.t("Buat Paket Patch") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir, lama, baru);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTA").start();
    }

    private String jalankan(File dir, String lama, String baru) {
        List<MesinOTA.BarisPatch> patch = MesinOTA.selisih(lama, baru);
        File berkasLama = new File(dir, "ota-lama.txt");
        File berkasBaru = new File(dir, "ota-baru.txt");
        tulisTeks(berkasLama, lama);
        tulisTeks(berkasBaru, baru);
        File paket = MesinOTA.buatPatch(berkasLama, berkasBaru, "patch-1",
                new File(dir, "patch-ota.zip"));
        if (paket == null) return TeksUI.t("Paket patch tidak bisa dibuat.");
        String idPerangkat = "";
        try {
            idPerangkat = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Throwable ignored) {
        }
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Ukuran patch:")).append(" ").append(paket.length())
                .append(" ").append(TeksUI.t("bita")).append(". ");
        b.append(TeksUI.t("Pengguna tahap awal:")).append(" 10% (");
        b.append(MesinOTA.kelompokPeluncuran(idPerangkat, "patch-1", 10)
                ? TeksUI.t("perangkat ini masuk") : TeksUI.t("perangkat ini belum masuk"));
        b.append(").");
        b.append("\n").append(MesinOTA.ringkasan(paket.length(),
                lama.length() + baru.length(), 10, idPerangkat, "patch-1"));
        b.append("\n").append(TeksUI.t("Baris patch:")).append(" ").append(patch.size()).append(".");
        return b.toString();
    }

    private File proyekAktif() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        return dirAktif;
    }

    private void mulaiKunci() {
        final File dir = proyekAktif();
        if (dir == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        tvHasil.setText(TeksUI.t("Buat Kunci OTA") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil;
                File folder = new File(dir, "ota-kunci");
                if (new File(folder, "ota-privat.pk8").isFile()
                        && new File(folder, "ota-publik.der").isFile()) {
                    hasil = TeksUI.t("Kunci OTA sudah ada di folder ota-kunci. Kunci privat jangan pernah dibagikan.");
                } else {
                    KeyPair kp = MesinOTA.buatKunciOta(folder);
                    if (kp == null) {
                        hasil = TeksUI.t("Kunci OTA tidak bisa dibuat.");
                    } else {
                        hasil = TeksUI.t("Kunci OTA dibuat di folder ota-kunci: ota-privat.pk8 (kunci privat, rahasia) dan ota-publik.der (kunci publik). Sidik jari kunci publik: ")
                                + MesinOTA.sidikJariKunciPublik(kp.getPublic()) + ".";
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTAKunci").start();
    }

    private void mulaiPatchBiner() {
        final File dir = proyekAktif();
        if (dir == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File lama = new File(dir, eBerkasLama.getText().toString().trim());
        final File baru = new File(dir, eBerkasBaru.getText().toString().trim());
        final String rilis = eRilis.getText().toString().trim();
        tvHasil.setText(TeksUI.t("Buat Patch Biner Tertanda") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanPatchBiner(dir, lama, baru, rilis);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTAPatch").start();
    }

    private String jalankanPatchBiner(File dir, File lama, File baru, String rilis) {
        if (!lama.isFile()) return TeksUI.t("Berkas lama tidak ditemukan: ") + lama.getPath();
        if (!baru.isFile()) return TeksUI.t("Berkas baru tidak ditemukan: ") + baru.getPath();
        File folder = new File(dir, "ota-kunci");
        PrivateKey privat = MesinOTA.bacaKunciPrivat(new File(folder, "ota-privat.pk8"));
        PublicKey publik = MesinOTA.bacaKunciPublik(new File(folder, "ota-publik.der"));
        if (privat == null || publik == null) {
            return TeksUI.t("Kunci OTA belum ada. Tekan dulu Buat Kunci OTA.");
        }
        File paket = MesinOTA.buatPatchBiner(lama, baru, rilis, privat, publik,
                new File(dir, "patch-ota-biner.zip"));
        if (paket == null) return TeksUI.t("Paket patch biner tidak bisa dibuat.");
        patchAktif = paket;
        StringBuilder b = new StringBuilder();
        b.append(MesinOTA.ringkasanBiner(paket.length(), baru.length()));
        b.append("\n").append(TeksUI.t("Paket patch:")).append(" ").append(paket.getPath());
        b.append("\n").append(TeksUI.t("Baris untuk rilis.txt di peladen:")).append(" ")
                .append(rilis).append("|").append("1.0").append("|")
                .append(MesinOTA.sha256Berkas(paket));
        return b.toString();
    }

    private void mulaiUnduh() {
        final File dir = proyekAktif();
        if (dir == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final String server = eServer.getText().toString().trim();
        final String rilis = eRilis.getText().toString().trim();
        if (server.length() == 0) {
            toast(TeksUI.t("Isi dulu alamat peladen patch."));
            return;
        }
        tvHasil.setText(TeksUI.t("Unduh Patch dari Server") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanUnduh(dir, server, rilis);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTAUnduh").start();
    }

    private String jalankanUnduh(File dir, String server, String rilis) {
        String shaHarapan = null;
        List<MesinOTA.InfoRilis> daftar = MesinOTA.bacaDaftarRilis(server);
        for (int i = 0; i < daftar.size(); i++) {
            if (rilis.equals(daftar.get(i).nama)) shaHarapan = daftar.get(i).sha256;
        }
        File tujuan = new File(new File(dir, "ota"), rilis + ".zip");
        File hasil = MesinOTA.unduhDanPeriksa(server, rilis, shaHarapan, tujuan);
        if (hasil == null) {
            return TeksUI.t("Patch tidak bisa diunduh dari peladen, atau SHA-256-nya tidak cocok dengan rilis.txt.");
        }
        patchAktif = hasil;
        return TeksUI.t("Patch diunduh:") + " " + hasil.getPath() + " (" + hasil.length()
                + " " + TeksUI.t("bita") + "). " + TeksUI.t("SHA-256 cocok dengan daftar rilis.");
    }

    private void mulaiTerapkanBiner() {
        final File dir = proyekAktif();
        if (dir == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File lama = new File(dir, eBerkasLama.getText().toString().trim());
        final File baru = new File(dir, eBerkasBaru.getText().toString().trim());
        final String rilis = eRilis.getText().toString().trim();
        tvHasil.setText(TeksUI.t("Verifikasi dan Terapkan Patch Biner") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanTerapkanBiner(dir, lama, baru, rilis);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTATerap").start();
    }

    private String jalankanTerapkanBiner(File dir, File lama, File baru, String rilis) {
        File paket = patchAktif;
        if (paket == null || !paket.isFile()) {
            paket = new File(new File(dir, "ota"), rilis + ".zip");
        }
        if (!paket.isFile()) paket = new File(dir, "patch-ota-biner.zip");
        if (!paket.isFile()) {
            return TeksUI.t("Paket patch belum ada. Buat dulu patch biner atau unduh dari server.");
        }
        PublicKey tepercaya = MesinOTA.bacaKunciPublikTepercaya(dir);
        if (tepercaya == null) {
            return TeksUI.t("Tidak ada kunci publik tepercaya di proyek (ota-kunci-tepercaya atau ota-kunci). Patch ditolak.");
        }
        File keluar = new File(new File(dir, "ota"), "aplikasi-baru.apk");
        MesinOTA.HasilTerap h = MesinOTA.terapkanPatchBiner(lama, paket, tepercaya, keluar);
        if (!h.berhasil) return TeksUI.t("Patch ditolak: ") + h.galat;
        apkHasilAktif = keluar;
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Tanda tangan patch sah. Patch diterapkan:")).append(" ")
                .append(keluar.getPath()).append(" (").append(h.ukuranBaru).append(" ")
                .append(TeksUI.t("bita")).append(").");
        if (baru.isFile()) {
            if (h.sha256Baru.equalsIgnoreCase(MesinOTA.sha256Berkas(baru))) {
                b.append(" ").append(TeksUI.t("Hasil patch sama persis dengan berkas baru."));
            } else {
                b.append(" ").append(TeksUI.t("Peringatan: hasil patch tidak sama dengan berkas baru."));
            }
        }
        b.append("\n").append(TeksUI.t("Tekan Pasang APK Hasil Patch untuk memasang dan membuka ulang aplikasi dengan kode baru."));
        return b.toString();
    }

    private void mulaiPasang() {
        final File dir = proyekAktif();
        if (dir == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        if (apkHasilAktif == null || !apkHasilAktif.isFile()) {
            toast(TeksUI.t("Belum ada APK hasil patch. Terapkan dulu patch biner."));
            return;
        }
        tvHasil.setText(TeksUI.t("Pasang APK Hasil Patch") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanPasang(dir, apkHasilAktif);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "MesinOTAPasang").start();
    }

    /**
     * Pasang APK hasil patch: tandatangani ulang dengan kunci proyek
     * (patch biner membatalkan tanda tangan lama), lalu pasang lewat
     * PackageInstaller. Sistem mengganti aplikasi dan membukanya ulang
     * dengan kode baru — inilah penerapan patch ke aplikasi yang
     * sedang berjalan.
     */
    private String jalankanPasang(File dir, File apkHasil) {
        String nama = apkHasil.getName();
        if (nama.endsWith(".apk")) nama = nama.substring(0, nama.length() - 4);
        File mentah = new File(new File(dir, "ota"), nama + "-mentah.apk");
        if (!salinBerkas(apkHasil, mentah)) {
            return TeksUI.t("APK hasil patch tidak bisa disiapkan untuk ditandatangani.");
        }
        final String[] jalanKeluar = new String[1];
        final String[] galat = new String[1];
        final Object kunci = new Object();
        Penandatangan.tandatangani(this, mentah.getPath(),
                new File(dir, "ota").getPath(), nama, false, "", "", 21,
                false, dir.getPath(), new BangunLokal.Listener() {
                    @Override
                    public void onEvent(int type, String msg) {
                        synchronized (kunci) {
                            if (type == BangunLokal.SUCCESS) jalanKeluar[0] = msg;
                            else if (type == BangunLokal.FAILED) galat[0] = msg;
                            kunci.notifyAll();
                        }
                    }
                });
        synchronized (kunci) {
            long batas = System.currentTimeMillis() + 120000;
            while (jalanKeluar[0] == null && galat[0] == null
                    && System.currentTimeMillis() < batas) {
                try {
                    kunci.wait(1000);
                } catch (Throwable ignored) {
                }
            }
        }
        if (galat[0] != null) return TeksUI.t("Penandatanganan ulang gagal: ") + galat[0];
        if (jalanKeluar[0] == null) return TeksUI.t("Penandatanganan ulang kehabisan waktu.");
        File apkTanda = new File(jalanKeluar[0].trim().split("\n")[0].trim());
        if (!apkTanda.isFile()) return TeksUI.t("APK bertanda tangan tidak ditemukan.");
        String pasang = pasangApk(apkTanda);
        if (pasang != null) return pasang;
        return TeksUI.t("Pemasangan dimulai. Tunggu hasil pemasangan dari sistem.");
    }

    /** Pasang APK lewat PackageInstaller; null bila sesi berhasil dimulai. */
    private String pasangApk(File apk) {
        PackageInstaller.Session sesi = null;
        try {
            PackageInstaller pemasang = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams param =
                    new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int idSesi = pemasang.createSession(param);
            sesi = pemasang.openSession(idSesi);
            OutputStream out = sesi.openWrite("apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                in.close();
            }
            sesi.fsync(out);
            out.close();
            Intent niat = new Intent(AKSI_PASANG);
            int bendera = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) bendera |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent tertunda = PendingIntent.getBroadcast(this, idSesi, niat, bendera);
            sesi.commit(tertunda.getIntentSender());
            return null;
        } catch (Throwable t) {
            return TeksUI.t("Pemasangan tidak bisa dimulai: ") + t.getMessage();
        } finally {
            try {
                if (sesi != null) sesi.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean salinBerkas(File dari, File ke) {
        try {
            if (ke.getParentFile() != null) ke.getParentFile().mkdirs();
            FileInputStream in = new FileInputStream(dari);
            FileOutputStream out = new FileOutputStream(ke);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                in.close();
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean tulisTeks(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            out.write(isi);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
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
