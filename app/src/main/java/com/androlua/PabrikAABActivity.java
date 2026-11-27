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
 * Pabrik Android App Bundle dan Pemeriksa Kesiapan Google Play Store
 * (Fitur 13, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Bangun Bundel AAB" — IDE menyusun berkas .aab
 * dari manifest, sumber daya, dan aset proyek, mensimulasikan
 * pecahan ukuran per arsitektur dan bahasa, lalu mengaudit kesiapan
 * kebijakan Play Store. Logika ada di PabrikAAB (murni Java, sudah
 * diuji di komputer).
 *
 * BATAS: bundletool sungguhan tidak bisa dijalankan di HP (butuh JVM
 * — lihat PabrikAAB.penilaianBundletool()). Yang dilakukan di sini
 * adalah jalur terdekat yang nyata: validasi struktur bundel,
 * pemecahan nyata menjadi APK varian dengan ukuran terukur, dan
 * tanda tangan JAR v1 (seperti jarsigner) memakai ApkSigner.
 */
public class PabrikAABActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;
    private Button bJalankan;
    private File aabAktif;
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
        setTitle(TeksUI.t("Pabrik App Bundle dan Kesiapan Play Store"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pabrik App Bundle dan Kesiapan Play Store"), 20);
        label(r, TeksUI.t("Bangun paket AAB di HP, simulasikan pecahan ukuran per arsitektur dan bahasa, dan periksa kesiapan kebijakan Google Play sebelum mengunggah."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Bangun Bundel AAB"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        button(r, TeksUI.t("Bangun AAB dari APK Jadi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiDariApk();
            }
        });

        button(r, TeksUI.t("Tandatangani Bundel"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiTandaTangan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Bangun Bundel AAB") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pabrik App Bundle dan Kesiapan Play Store") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Bangun Bundel AAB") + ".");
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
        tvHasil.setText(TeksUI.t("Bangun Bundel AAB") + "...");
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
        }, "PabrikAAB").start();
    }

    private String jalankan(File dir) {
        dirAktif = dir;
        File modul = new File(dir, "app/src/main");
        if (!modul.isDirectory()) modul = dir;
        String nama = InfoProyek.namaAplikasi(dir).replaceAll("\\s+", "");
        if (nama.length() == 0) nama = dir.getName();
        File keluar = new File(new File(dir, "bin"), nama + ".aab");
        File aab = PabrikAAB.bangunAAB(modul, keluar);
        if (aab == null) return TeksUI.t("Bundel AAB tidak bisa dibuat.");
        aabAktif = aab;
        return ringkasHasil(dir, aab);
    }

    private String jalankanDariApk(File dir) {
        dirAktif = dir;
        String nama = InfoProyek.namaAplikasi(dir).replaceAll("\\s+", "");
        if (nama.length() == 0) nama = dir.getName();
        File apk = new File(new File(dir, "bin"), nama + ".apk");
        if (!apk.isFile()) {
            File bin = new File(dir, "bin");
            File[] daftar = bin.listFiles();
            if (daftar != null) {
                for (int i = 0; i < daftar.length; i++) {
                    if (daftar[i].getName().endsWith(".apk")) {
                        apk = daftar[i];
                        break;
                    }
                }
            }
        }
        if (!apk.isFile()) {
            return TeksUI.t("APK jadi tidak ditemukan di folder bin. Bangun dulu APK proyeknya.");
        }
        File keluar = new File(new File(dir, "bin"), nama + ".aab");
        File aab = PabrikAAB.bangunAABDariApk(apk, keluar);
        if (aab == null) return TeksUI.t("Bundel AAB tidak bisa dibuat dari APK.");
        aabAktif = aab;
        return ringkasHasil(dir, aab);
    }

    /** Validasi bundel + pecahan nyata + audit Play, dirangkai jadi teks hasil. */
    private String ringkasHasil(File dir, File aab) {
        File modul = new File(dir, "app/src/main");
        if (!modul.isDirectory()) modul = dir;
        List<PabrikAAB.Temuan> validasi = PabrikAAB.validasiBundel(aab);
        List<PabrikAAB.Varian> varian = PabrikAAB.bangunPecahan(aab,
                new File(new File(dir, "bin"), "pecahan"));
        String manifest = bacaTeks(new File(modul, "AndroidManifest.xml"));
        int target = 34;
        try {
            target = Integer.parseInt(InfoProyek.bacaKunci(dir, "targetsdk", "34"));
        } catch (Throwable ignored) {
        }
        boolean ada64 = new File(dir, "app/src/main/jniLibs/arm64-v8a").isDirectory()
                || new File(dir, "lib/arm64-v8a").isDirectory();
        boolean ada32 = new File(dir, "app/src/main/jniLibs/armeabi-v7a").isDirectory()
                || new File(dir, "lib/armeabi-v7a").isDirectory();
        List<PabrikAAB.Temuan> temuan = PabrikAAB.auditPlay(manifest, target, ada64, ada32);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Bundel dibuat:")).append(" ").append(aab.getPath())
                .append(" (").append(aab.length()).append(" ").append(TeksUI.t("bita")).append(").");
        b.append("\n").append(TeksUI.t("Pemeriksaan bundel:")).append(" ");
        if (validasi.isEmpty()) {
            b.append(TeksUI.t("Tidak ada temuan."));
        } else {
            b.append(validasi.size()).append(" ").append(TeksUI.t("temuan."));
            for (int i = 0; i < validasi.size(); i++) b.append("\n! ").append(validasi.get(i).toString());
        }
        b.append("\n").append(PabrikAAB.ringkasanPecahan(varian));
        b.append("\n").append(TeksUI.t("bundletool tidak bisa dijalankan di HP ini (butuh JVM). Ukuran pecahan di atas adalah hasil pemecahan nyata menjadi APK varian, bukan perkiraan."));
        b.append("\n").append(TeksUI.t("Pemeriksaan Play:")).append(" ").append(temuan.size()).append(" temuan.");
        for (int i = 0; i < temuan.size(); i++) b.append("\n! ").append(temuan.get(i).toString());
        return b.toString();
    }

    private void mulaiDariApk() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Bangun AAB dari APK Jadi") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanDariApk(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "PabrikAABApk").start();
    }

    private void mulaiTandaTangan() {
        if (aabAktif == null || !aabAktif.isFile()) {
            toast(TeksUI.t("Bundel belum dibuat. Tekan dulu Bangun Bundel AAB atau Bangun AAB dari APK Jadi."));
            return;
        }
        if (dirAktif == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        tvHasil.setText(TeksUI.t("Menandatangani bundel") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankanTandaTangan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "PabrikAABTanda").start();
    }

    private String jalankanTandaTangan() {
        PabrikAAB.KunciProyek kunci = PabrikAAB.muatKunciProyek(dirAktif);
        if (kunci == null) kunci = PabrikAAB.buatKunciProyek(dirAktif);
        if (kunci == null) return TeksUI.t("Kunci proyek tidak bisa dibuat atau dibaca.");
        String nama = aabAktif.getName();
        if (nama.endsWith(".aab")) nama = nama.substring(0, nama.length() - 4);
        File keluar = new File(aabAktif.getParentFile(), nama + "-ditandatangani.aab");
        File hasil = PabrikAAB.tandatanganiBundel(aabAktif, kunci.kunci, kunci.sertifikat, keluar);
        if (hasil == null) return TeksUI.t("Tanda tangan bundel tidak bisa dibuat.");
        PabrikAAB.HasilVerifikasi verifikasi = PabrikAAB.verifikasiTandaTanganBundel(hasil);
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Bundel ditandatangani:")).append(" ").append(hasil.getPath())
                .append(" (").append(hasil.length()).append(" ").append(TeksUI.t("bita")).append(").");
        b.append("\n").append(verifikasi.toString());
        b.append("\n").append(TeksUI.t("Kunci yang dipakai sama dengan kunci APK proyek ini, jadi tanda tangan bundel cocok dengan tanda tangan APK."));
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
            return null;
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
