package com.androlua;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulator Kondisi Perangkat Ekstrem dan Auditor Kebocoran Memori
 * (Fitur 10, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Jalankan Simulasi" — IDE membaca keadaan
 * simpan-pulihkan DARI SUMBER PROYEK (bukan contoh tetap), lalu
 * memeriksa data mana yang hilang saat proses mati atau layar
 * diputar, menguji jaringan putus pada soket loopback sungguhan,
 * dan memindai pekerjaan latar di sumber. Logika ada di
 * SimulatorEkstrem (murni Java, sudah diuji di komputer).
 *
 * Tombol lain (semuanya nyata, bukan model):
 *  - "Ambil Heap Dump dan Lacak Kebocoran": mengambil heap dump
 *    proses ini (Debug.dumpHprofData), mengurainya, menahan 50 objek
 *    contoh, mengambil dump kedua, lalu melepasnya dan mengambil
 *    dump ketiga — dilaporkan kelas yang tumbuh/menyusut dan rantai
 *    penahannya dari akar GC.
 *  - "Uji Mati Proses Aplikasi Ini": menyimpan keadaan ke berkas
 *    lalu benar-benar mematikan proses aplikasi ini; hasilnya
 *    tampil saat layar ini dibuka lagi.
 *  - "Matikan Proses Aplikasi Proyek": meluncurkan aplikasi proyek
 *    bila terpasang, memindahkannya ke latar, lalu mematikannya
 *    lewat killBackgroundProcesses.
 *
 * BATAS: force-stop aplikasi lain butuh izin sistem (tidak dimiliki
 * aplikasi biasa); mematikan radio/Wi-Fi juga tidak bisa dari
 * aplikasi biasa — uji jaringan di sini mematikan konektivitas proses
 * uji (server loopback ditutup), dan status konektivitas perangkat
 * yang sebenarnya dilaporkan dari ConnectivityManager.
 */
public class SimulatorEkstremActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;
    private Button bJalankan;
    private Button bHeap;
    private Button bMatiProses;
    private Button bMatikanProyek;
    private File dirAktif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Simulator Kondisi Ekstrem dan Kebocoran Memori"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Simulator Kondisi Ekstrem dan Kebocoran Memori"), 20);
        label(r, TeksUI.t("Uji aplikasi saat proses dimatikan sistem, jaringan putus-nyambung, layar diputar, dan hemat baterai aktif, serta lacak objek yang tertahan di memori."), false);
        label(r, TeksUI.t("Simulasi memakai keadaan simpan-pulihkan dari sumber proyek, bukan contoh tetap."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        spProyek.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (spProyek.getSelectedItem() != null) {
                    dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Jalankan Simulasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        bHeap = button(r, TeksUI.t("Ambil Heap Dump dan Lacak Kebocoran"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiHeap();
            }
        });

        bMatiProses = button(r, TeksUI.t("Uji Mati Proses Aplikasi Ini"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ujiMatiProsesSendiri();
            }
        });

        bMatikanProyek = button(r, TeksUI.t("Matikan Proses Aplikasi Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiMatikanProyek();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Jalankan Simulasi") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        periksaHasilMatiProses();
        announce(TeksUI.t("Simulator Kondisi Ekstrem dan Kebocoran Memori") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Jalankan Simulasi") + ".");
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
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        }
    }

    /** Bila ada berkas hasil uji mati proses (dari sebelum aplikasi ditutup),
     *  tampilkan dan hapus. */
    private void periksaHasilMatiProses() {
        File pending = new File(getFilesDir(), "simulator/mati-proses.txt");
        if (!pending.isFile()) return;
        String isi = bacaTeks(pending);
        pending.delete();
        if (isi == null || isi.length() == 0) return;
        String waktu = "";
        String simpan = "";
        String hilang = "";
        String[] baris = isi.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].startsWith("WAKTU=")) waktu = baris[i].substring(6);
            else if (baris[i].startsWith("SIMPAN=")) simpan = baris[i].substring(7);
            else if (baris[i].startsWith("HILANG=")) hilang = baris[i].substring(7);
        }
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Hasil uji mati proses:")).append("\n");
        b.append(TeksUI.t("Aplikasi dimatikan pada:")).append(" ").append(waktu).append("\n");
        b.append(TeksUI.t("Data tersimpan pulih:")).append(" ")
                .append(simpan.length() == 0 ? TeksUI.t("tidak ada") : simpan).append("\n");
        b.append(TeksUI.t("Data hanya-memori hilang:")).append(" ")
                .append(hilang.length() == 0 ? TeksUI.t("tidak ada") : hilang);
        tvHasil.setText(b.toString());
        announce(b.toString());
    }

    private void mulai() {
        if (dirAktif == null || spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        tvHasil.setText(TeksUI.t("Jalankan Simulasi") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "SimulatorEkstrem").start();
    }

    /** Kumpulkan berkas sumber Activity di proyek (maks 20 berkas). */
    private void cariActivity(File kini, List<File> keluar) {
        if (keluar.size() >= 20) return;
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            if (keluar.size() >= 20) return;
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".") && !f.getName().equals("bin")) {
                    cariActivity(f, keluar);
                }
            } else if ((f.getName().endsWith(".java") || f.getName().endsWith(".kt"))
                    && f.length() <= 500 * 1024) {
                String isi = bacaTeks(f);
                if (isi != null && (isi.contains("onSaveInstanceState")
                        || isi.contains("extends Activity")
                        || isi.contains("extends AppCompatActivity"))) {
                    keluar.add(f);
                }
            }
        }
    }

    private String gabung(List<String> daftar) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < daftar.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(daftar.get(i));
        }
        return b.toString();
    }

    private String jalankan() {
        List<File> sumber = new ArrayList<File>();
        cariActivity(dirAktif, sumber);
        StringBuilder b = new StringBuilder();
        if (sumber.isEmpty()) {
            b.append(TeksUI.t("Berkas Activity tidak ditemukan di proyek ini."));
            return b.toString();
        }
        SimulatorEkstrem.HasilAnalisis gabung = null;
        StringBuilder semuaKode = new StringBuilder();
        for (int i = 0; i < sumber.size(); i++) {
            String kode = bacaTeks(sumber.get(i));
            if (kode == null) continue;
            semuaKode.append(kode).append("\n");
            SimulatorEkstrem.HasilAnalisis h = SimulatorEkstrem.analisisKeadaanDariSumber(kode);
            gabung = SimulatorEkstrem.gabungAnalisis(gabung, h);
        }
        if (gabung == null) {
            b.append(TeksUI.t("Berkas Activity tidak ditemukan di proyek ini."));
            return b.toString();
        }
        SimulatorEkstrem.KeadaanLayar keadaan = SimulatorEkstrem.keadaanDariAnalisis(gabung);
        b.append(TeksUI.t("Berkas Activity diperiksa:")).append(" ").append(sumber.size()).append(". ");
        if (!gabung.adaOnSave) {
            b.append(TeksUI.t("Tidak ada onSaveInstanceState di sumber proyek.")).append(" ");
        }
        b.append("\n").append(TeksUI.t("Kunci disimpan:")).append(" ")
                .append(gabung.kunciDisimpan.isEmpty() ? TeksUI.t("tidak ada")
                        : gabung(gabung.kunciDisimpan));
        b.append("\n").append(TeksUI.t("Kolom tidak disimpan:")).append(" ")
                .append(gabung.kolomTakDisimpan.isEmpty() ? TeksUI.t("tidak ada")
                        : gabung(gabung.kolomTakDisimpan));
        if (!gabung.dipulihkanTakDisimpan.isEmpty()) {
            b.append("\n").append(TeksUI.t("Kunci dipulihkan tanpa disimpan:")).append(" ")
                    .append(gabung(gabung.dipulihkanTakDisimpan));
        }
        if (!gabung.disimpanTakDipulihkan.isEmpty()) {
            b.append("\n").append(TeksUI.t("Kunci disimpan tapi tidak pernah dipulihkan:")).append(" ")
                    .append(gabung(gabung.disimpanTakDipulihkan));
        }
        List<String> hilangMati = SimulatorEkstrem.simulasiMatiProses(keadaan);
        b.append("\n").append(TeksUI.t("Mati proses (dari sumber proyek):")).append(" ")
                .append(hilangMati.isEmpty() ? TeksUI.t("semua data tersimpan pulih")
                        : hilangMati.size() + " " + TeksUI.t("kolom hilang:")
                        + " " + gabung(hilangMati));
        List<String> hilangPutar = SimulatorEkstrem.simulasiPutarLayar(keadaan);
        b.append("\n").append(TeksUI.t("Putar layar (dari sumber proyek):")).append(" ")
                .append(hilangPutar.isEmpty() ? TeksUI.t("semua data tersimpan pulih")
                        : hilangPutar.size() + " " + TeksUI.t("kolom hilang:")
                        + " " + gabung(hilangPutar));
        // Jaringan putus SUNGGUHAN (soket loopback dimatikan).
        SimulatorEkstrem.HasilJaring jaring = SimulatorEkstrem.ujiPutusSambung();
        b.append("\n").append(TeksUI.t("Jaringan putus (soket nyata):")).append(" ");
        if (jaring.terhubungAwal && jaring.gagalSetelahPutus) {
            b.append(TeksUI.t("sambungan awal berhasil, sambungan sesudah putus gagal"))
                    .append(" (").append(jaring.pesan).append(", ")
                    .append(jaring.msGagal).append(" ms).");
        } else if (!jaring.terhubungAwal) {
            b.append(TeksUI.t("sambungan awal gagal:")).append(" ").append(jaring.pesan);
        } else {
            b.append(TeksUI.t("sambungan sesudah putus tidak gagal:")).append(" ")
                    .append(jaring.pesan);
        }
        b.append("\n").append(TeksUI.t("Antrean luring:")).append(" ")
                .append(jaring.antreanDipulihkan
                        ? TeksUI.t("permintaan yang diantre terkirim setelah server hidup lagi")
                        : TeksUI.t("permintaan yang diantre tidak terkirim lagi"));
        // Pekerjaan latar di sumber (info hemat baterai).
        List<String> latar = SimulatorEkstrem.pindaiPekerjaanLatar(semuaKode.toString());
        b.append("\n").append(TeksUI.t("Pekerjaan latar di sumber:")).append(" ")
                .append(latar.isEmpty() ? TeksUI.t("tidak ada") : gabung(latar));
        // Status konektivitas perangkat yang sebenarnya.
        b.append("\n").append(TeksUI.t("Koneksi perangkat saat ini:")).append(" ")
                .append(statusKoneksi());
        return b.toString();
    }

    /** Status konektivitas perangkat yang sebenarnya (ConnectivityManager). */
    private String statusKoneksi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (cm == null) return TeksUI.t("tidak diketahui");
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) return TeksUI.t("tidak ada koneksi");
            return info.getTypeName() + " " + TeksUI.t("(tersambung)");
        } catch (Throwable t) {
            return TeksUI.t("tidak diketahui");
        }
    }

    private void mulaiHeap() {
        tvHasil.setText(TeksUI.t("Mengambil heap dump. Tunggu sebentar."));
        bHeap.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanHeap();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bHeap.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "SimulatorHeap").start();
    }

    private String jalankanHeap() {
        File dir = new File(getCacheDir(), "simulator");
        dir.mkdirs();
        File dump1 = new File(dir, "heap-sebelum.hprof");
        File dump2 = new File(dir, "heap-sesudah.hprof");
        File dump3 = new File(dir, "heap-bersih.hprof");
        if (dump1.exists()) dump1.delete();
        if (dump2.exists()) dump2.delete();
        if (dump3.exists()) dump3.delete();
        StringBuilder b = new StringBuilder();
        try {
            Debug.dumpHprofData(dump1.getAbsolutePath());
        } catch (Throwable t) {
            return TeksUI.t("Heap dump gagal:") + " " + t.toString();
        }
        SimulatorEkstrem.HasilHprof h1;
        try {
            h1 = SimulatorEkstrem.uraiHprof(dump1);
        } catch (Throwable t) {
            return TeksUI.t("Heap dump tidak bisa diurai:") + " " + t.toString();
        }
        b.append(TeksUI.t("Heap dump sebelum:")).append(" ").append(dump1.length())
                .append(" ").append(TeksUI.t("bita,")).append(" ").append(h1.totalObjek)
                .append(" ").append(TeksUI.t("objek."));
        int ditahan = SimulatorEkstrem.tahanContoh(50);
        b.append("\n").append(TeksUI.t("Objek ditahan:")).append(" ").append(ditahan).append(".");
        try {
            Debug.dumpHprofData(dump2.getAbsolutePath());
        } catch (Throwable t) {
            SimulatorEkstrem.lepasContoh();
            return b.toString() + "\n" + TeksUI.t("Heap dump kedua gagal:") + " " + t.toString();
        }
        SimulatorEkstrem.HasilHprof h2;
        try {
            h2 = SimulatorEkstrem.uraiHprof(dump2);
        } catch (Throwable t) {
            SimulatorEkstrem.lepasContoh();
            return b.toString() + "\n" + TeksUI.t("Heap dump tidak bisa diurai:") + " " + t.toString();
        }
        b.append("\n").append(TeksUI.t("Heap dump sesudah menahan objek:")).append(" ")
                .append(dump2.length()).append(" ").append(TeksUI.t("bita,")).append(" ")
                .append(h2.totalObjek).append(" ").append(TeksUI.t("objek."));
        List<SimulatorEkstrem.DeltaKelas> tumbuh = SimulatorEkstrem.bandingkan(h1, h2);
        b.append("\n").append(TeksUI.t("Kelas yang tumbuh:"));
        int tampil = 0;
        for (int i = 0; i < tumbuh.size() && tampil < 5; i++) {
            if (tumbuh.get(i).delta <= 0) continue;
            b.append("\n- ").append(tumbuh.get(i).nama).append(": ")
                    .append(tumbuh.get(i).sebelum).append(" -> ")
                    .append(tumbuh.get(i).sesudah).append(" (+")
                    .append(tumbuh.get(i).delta).append(")");
            tampil++;
        }
        if (tampil == 0) b.append(" ").append(TeksUI.t("tidak ada"));
        // Lacak rantai penahan satu objek yang tumbuh paling besar.
        String kelasTeratas = null;
        for (int i = 0; i < tumbuh.size(); i++) {
            if (tumbuh.get(i).delta > 0) {
                kelasTeratas = tumbuh.get(i).nama;
                break;
            }
        }
        if (kelasTeratas != null) {
            try {
                List<Long> contoh = SimulatorEkstrem.contohObjek(dump2, kelasTeratas, 1);
                if (!contoh.isEmpty()) {
                    List<String> rantai = SimulatorEkstrem.lacakPenahan(
                            dump2, contoh.get(0).longValue(), 6);
                    if (rantai.isEmpty()) {
                        b.append("\n").append(TeksUI.t("Rantai penahan:")).append(" ")
                                .append(TeksUI.t("tidak sampai ke akar dalam 6 langkah."));
                    } else {
                        b.append("\n").append(TeksUI.t("Rantai penahan:"));
                        for (int i = 0; i < rantai.size(); i++) {
                            b.append("\n").append(i + 1).append(". ")
                                    .append(rantai.get(i));
                        }
                    }
                }
            } catch (Throwable t) {
                b.append("\n").append(TeksUI.t("Rantai penahan:")).append(" ")
                        .append(TeksUI.t("gagal dilacak:")).append(" ").append(t.toString());
            }
        }
        // Lepas + GC + dump ketiga.
        SimulatorEkstrem.lepasContoh();
        System.gc();
        try {
            Thread.sleep(500);
        } catch (Throwable ignored) {
        }
        System.gc();
        try {
            Debug.dumpHprofData(dump3.getAbsolutePath());
        } catch (Throwable t) {
            return b.toString() + "\n" + TeksUI.t("Heap dump ketiga gagal:") + " " + t.toString();
        }
        SimulatorEkstrem.HasilHprof h3;
        try {
            h3 = SimulatorEkstrem.uraiHprof(dump3);
        } catch (Throwable t) {
            return b.toString() + "\n" + TeksUI.t("Heap dump tidak bisa diurai:") + " " + t.toString();
        }
        b.append("\n").append(TeksUI.t("Heap dump sesudah melepas objek:")).append(" ")
                .append(dump3.length()).append(" ").append(TeksUI.t("bita,")).append(" ")
                .append(h3.totalObjek).append(" ").append(TeksUI.t("objek."));
        List<SimulatorEkstrem.DeltaKelas> susut = SimulatorEkstrem.bandingkan(h2, h3);
        b.append("\n").append(TeksUI.t("Kelas yang menyusut:"));
        tampil = 0;
        for (int i = 0; i < susut.size() && tampil < 5; i++) {
            if (susut.get(i).delta >= 0) continue;
            b.append("\n- ").append(susut.get(i).nama).append(": ")
                    .append(susut.get(i).sebelum).append(" -> ")
                    .append(susut.get(i).sesudah).append(" (")
                    .append(susut.get(i).delta).append(")");
            tampil++;
        }
        if (tampil == 0) b.append(" ").append(TeksUI.t("tidak ada"));
        return b.toString();
    }

    /** Simpan keadaan ke berkas lalu matikan proses aplikasi ini sungguhan. */
    private void ujiMatiProsesSendiri() {
        if (dirAktif == null || spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        List<File> sumber = new ArrayList<File>();
        cariActivity(dirAktif, sumber);
        SimulatorEkstrem.HasilAnalisis gabung = null;
        for (int i = 0; i < sumber.size(); i++) {
            String kode = bacaTeks(sumber.get(i));
            if (kode == null) continue;
            gabung = SimulatorEkstrem.gabungAnalisis(gabung,
                    SimulatorEkstrem.analisisKeadaanDariSumber(kode));
        }
        SimulatorEkstrem.KeadaanLayar keadaan = SimulatorEkstrem.keadaanDariAnalisis(gabung);
        List<String> hilang = SimulatorEkstrem.simulasiMatiProses(keadaan);
        String waktu = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        StringBuilder isi = new StringBuilder();
        isi.append("WAKTU=").append(waktu).append("\n");
        isi.append("SIMPAN=").append(gabung(keadaan.tersimpan.keySet())).append("\n");
        isi.append("HILANG=").append(gabung(hilang)).append("\n");
        File pending = new File(getFilesDir(), "simulator/mati-proses.txt");
        tulisTeks(pending, isi.toString());
        Toast.makeText(this,
                TeksUI.t("Aplikasi akan ditutup sekarang. Buka lagi Simulator untuk melihat hasilnya."),
                Toast.LENGTH_LONG).show();
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }, 1200);
    }

    private String gabung(java.util.Collection<String> daftar) {
        StringBuilder b = new StringBuilder();
        boolean pertama = true;
        for (String s : daftar) {
            if (!pertama) b.append(", ");
            pertama = false;
            b.append(s);
        }
        return b.toString();
    }

    private void mulaiMatikanProyek() {
        if (dirAktif == null || spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        tvHasil.setText(TeksUI.t("Matikan Proses Aplikasi Proyek") + "...");
        bMatikanProyek.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanMatikanProyek();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bMatikanProyek.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "SimulatorMatikan").start();
    }

    private String jalankanMatikanProyek() {
        File manifest = new File(dirAktif, "app/src/main/AndroidManifest.xml");
        if (!manifest.isFile()) manifest = new File(dirAktif, "AndroidManifest.xml");
        if (!manifest.isFile()) {
            return TeksUI.t("Paket proyek tidak ditemukan di AndroidManifest.xml.");
        }
        String isi = bacaTeks(manifest);
        if (isi == null) {
            return TeksUI.t("Paket proyek tidak ditemukan di AndroidManifest.xml.");
        }
        Matcher m = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(isi);
        if (!m.find()) {
            return TeksUI.t("Paket proyek tidak ditemukan di AndroidManifest.xml.");
        }
        final String paket = m.group(1);
        Intent luncur = getPackageManager().getLaunchIntentForPackage(paket);
        if (luncur == null) {
            return TeksUI.t("Aplikasi proyek belum terpasang di ponsel ini.") + " (" + paket + ")";
        }
        try {
            luncur.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(luncur);
            Thread.sleep(1500);
            Intent rumah = new Intent(Intent.ACTION_MAIN);
            rumah.addCategory(Intent.CATEGORY_HOME);
            rumah.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(rumah);
            Thread.sleep(1500);
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return TeksUI.t("ActivityManager tidak tersedia.");
            am.killBackgroundProcesses(paket);
            Thread.sleep(1000);
            boolean masihAda = false;
            try {
                List<ActivityManager.RunningAppProcessInfo> jalan = am.getRunningAppProcesses();
                if (jalan != null) {
                    for (int i = 0; i < jalan.size(); i++) {
                        if (paket.equals(jalan.get(i).processName)) {
                            masihAda = true;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            StringBuilder b = new StringBuilder();
            if (masihAda) {
                b.append(TeksUI.t("Proses aplikasi proyek masih terlihat berjalan."));
            } else {
                b.append(TeksUI.t("Proses aplikasi proyek dimatikan (latar)."));
            }
            b.append(" ").append(TeksUI.t("Bukan force-stop: sistem dapat menghidupkan lagi aplikasi yang punya service."));
            b.append(" (").append(paket).append(")");
            return b.toString();
        } catch (Throwable t) {
            return TeksUI.t("Gagal mematikan proses aplikasi proyek:") + " " + t.toString();
        }
    }

    private String bacaTeks(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
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
