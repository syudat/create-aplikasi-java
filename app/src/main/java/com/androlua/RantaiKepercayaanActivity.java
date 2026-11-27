package com.androlua;

import android.app.Activity;
import android.os.Bundle;
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
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rantai Kepercayaan Penandatanganan Bertingkat dan Penjaga Lingkungan
 * Waktu-Nyata (Fitur 2, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Periksa Rantai dan Lingkungan" — IDE membangun
 * rantai kunci 5 generasi dari nama paket proyek, memeriksa keutuhannya,
 * lalu memindai peta memori proses sendiri dan properti perangkat untuk
 * jejak Frida, Xposed, atau emulator, serta membandingkan isi memori
 * dengan berkas di disk. Tombol "Putar Kunci Penandatanganan v3"
 * membuat kunci baru, membangun silsilah rotasi dari kunci proyek yang
 * lama, dan menandatangani APK belum-ditandatangani dengan skema v3.
 * Logika ada di RantaiKepercayaan (murni Java, sudah diuji di komputer).
 */
public class RantaiKepercayaanActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;
    private Button bJalankan;
    private Button bRotasi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Rantai Kepercayaan dan Penjaga Lingkungan"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Rantai Kepercayaan dan Penjaga Lingkungan"), 20);
        label(r, TeksUI.t("Periksa rantai kunci penandatanganan, bandingkan isi memori dengan berkas di disk, dan deteksi Frida, Xposed, atau emulator dari jejak proses."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Periksa Rantai dan Lingkungan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        bRotasi = button(r, TeksUI.t("Putar Kunci Penandatanganan v3"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiRotasi();
            }
        });
        label(r, TeksUI.t("Rotasi v3 memakai kunci bawaan proyek di folder tanda-tangan-bawaan. Kunci lama disimpan sebagai kunci-lama.p12, kunci baru menjadi kunci proyek, dan silsilah rotasi disimpan sebagai silsilah-v3.bin. APK hasil berakhiran -rotasi-v3.apk."), true);

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Periksa Rantai dan Lingkungan") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Rantai Kepercayaan dan Penjaga Lingkungan") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Periksa Rantai dan Lingkungan") + ".");
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
        tvHasil.setText(TeksUI.t("Periksa Rantai dan Lingkungan") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "RantaiKepercayaan").start();
    }

    private void mulaiRotasi() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Putar Kunci Penandatanganan v3") + "...");
        bRotasi.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanRotasi(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bRotasi.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "RantaiRotasi").start();
    }

    private String jalankanRotasi(File dir) {
        try {
            File folderKunci = new File(dir, "tanda-tangan-bawaan");
            File p12Lama = new File(folderKunci, "kunci.p12");
            File sandiLama = new File(folderKunci, "sandi.txt");
            if (!p12Lama.isFile() || !sandiLama.isFile()) {
                return TeksUI.t("Kunci bawaan proyek belum ada. Bangun dan tandatangani dulu proyek ini sekali, supaya kunci proyek dibuat.");
            }
            String sandiProyek = bacaTeks(sandiLama);
            if (sandiProyek == null) sandiProyek = "";
            sandiProyek = sandiProyek.trim();
            RantaiKepercayaan.KunciMasuk lama =
                    RantaiKepercayaan.muatKunciP12(p12Lama, sandiProyek);
            if (lama == null) {
                return TeksUI.t("Kunci bawaan proyek tidak bisa dibaca.");
            }
            File bin = new File(dir, "bin");
            File mentah = null;
            if (bin.isDirectory()) {
                File[] daftar = bin.listFiles();
                if (daftar != null) {
                    for (int i = 0; i < daftar.length; i++) {
                        if (daftar[i].getName().endsWith("-belum-ditandatangani.apk")) {
                            mentah = daftar[i];
                            break;
                        }
                    }
                }
            }
            if (mentah == null) {
                return TeksUI.t("APK belum-ditandatangani tidak ditemukan di folder bin. Bangun dulu proyeknya.");
            }
            String sandiBaru = sandiAcak();
            byte[] p12Baru = Penandatangan.buatP12("bawaan", sandiBaru,
                    TeksUI.t("Create Aplikasi"), "", "", "", "", "", 25);
            RantaiKepercayaan.KunciMasuk baru =
                    RantaiKepercayaan.muatKunciP12(p12Baru, sandiBaru);
            if (baru == null) {
                return TeksUI.t("Kunci baru gagal dibuat.");
            }
            String paket = InfoProyek.namaPaket(dir);
            if (paket == null || paket.length() == 0) paket = dir.getName();
            int minSdk = 21;
            com.android.apksig.SigningCertificateLineage silsilah =
                    RantaiKepercayaan.buatSilsilahRotasi(
                            lama.kunci, lama.sertifikat, baru.kunci, baru.sertifikat, minSdk);
            if (silsilah == null) {
                return TeksUI.t("Silsilah rotasi gagal dibuat.");
            }
            String namaKeluar = mentah.getName().replace("-belum-ditandatangani.apk", "-rotasi-v3.apk");
            File keluar = new File(bin, namaKeluar);
            String galat = RantaiKepercayaan.tandatanganiDenganRotasi(
                    mentah, keluar, lama.kunci, lama.sertifikat,
                    baru.kunci, baru.sertifikat, silsilah, minSdk);
            if (galat != null) return galat;
            // Simpan kunci lama, ganti kunci proyek dengan kunci baru, simpan silsilah.
            salinBerkas(p12Lama, new File(folderKunci, "kunci-lama.p12"));
            tulisBerkas(new File(folderKunci, "kunci.p12"), p12Baru);
            tulisBerkas(new File(folderKunci, "sandi.txt"), sandiBaru.getBytes("UTF-8"));
            try {
                silsilah.writeToFile(new File(folderKunci, "silsilah-v3.bin"));
            } catch (Throwable ignored) {
            }
            StringBuilder b = new StringBuilder();
            b.append(TeksUI.t("Rotasi v3 selesai. APK:")).append(" ").append(keluar.getName()).append(".\n");
            b.append(RantaiKepercayaan.periksaRotasi(keluar)).append("\n");
            b.append(TeksUI.t("Kunci lama disimpan sebagai kunci-lama.p12; kunci baru menjadi kunci proyek."));
            return b.toString();
        } catch (Throwable t) {
            return TeksUI.t("Rotasi gagal:") + " " + String.valueOf(t.getMessage());
        }
    }

    private String sandiAcak() {
        String abjad = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder b = new StringBuilder(16);
        for (int i = 0; i < 16; i++) b.append(abjad.charAt(r.nextInt(abjad.length())));
        return b.toString();
    }

    private void salinBerkas(File dari, File ke) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(dari);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            } finally {
                in.close();
            }
            tulisBerkas(ke, buf.toByteArray());
        } catch (Throwable ignored) {
        }
    }

    private void tulisBerkas(File f, byte[] data) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            try {
                out.write(data);
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private String jalankan(File dir) {
        String paket = InfoProyek.namaPaket(dir);
        if (paket == null || paket.length() == 0) paket = dir.getName();
        List<String> rantai = RantaiKepercayaan.buatRantai(paket, 5);
        String isiMaps = bacaTeks(new File("/proc/self/maps"));
        StringBuilder prop = new StringBuilder();
        prop.append(android.os.Build.BRAND).append("\n");
        prop.append(android.os.Build.MODEL).append("\n");
        prop.append(android.os.Build.PRODUCT).append("\n");
        prop.append(android.os.Build.DEVICE).append("\n");
        prop.append(android.os.Build.HARDWARE).append("\n");
        prop.append(android.os.Build.FINGERPRINT).append("\n");
        List<RantaiKepercayaan.Temuan> temuan =
                RantaiKepercayaan.deteksiLingkungan(isiMaps, prop.toString());
        RantaiKepercayaan.HasilMemori memori = RantaiKepercayaan.periksaMemoriAsli();
        temuan.addAll(memori.temuan);
        String chip = RantaiKepercayaan.statusKunciChip();
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Rantai kunci:")).append(" ").append(rantai.size()).append(" generasi, ")
                .append(RantaiKepercayaan.periksaRantai(rantai) ? "utuh" : "rusak").append(".\n");
        b.append(RantaiKepercayaan.ringkasan(rantai, temuan)).append("\n");
        b.append(memori.toString()).append("\n");
        b.append(TeksUI.t("Kunci chip:")).append(" ").append(chip).append(".");
        for (int i = 0; i < temuan.size(); i++) {
            b.append("\n- ").append(temuan.get(i).toString());
        }
        return b.toString();
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
            return "";
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
