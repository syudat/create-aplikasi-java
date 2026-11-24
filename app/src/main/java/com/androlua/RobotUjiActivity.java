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
import java.util.ArrayList;
import java.util.List;

/**
 * Robot Penguji Antarmuka Otonom (Fitur 3 dari daftar Gemini, 5.7).
 *
 * Pilih proyek, lalu "Buat Skenario Otomatis" — IDE membaca manifest
 * proyek dan layout XML-nya, lalu menyusun skenario teks: satu KETUK
 * per tombol dan per aktivitas, tiga ISI ekstrem per kolom input.
 * "Jalankan Dry Run" menjalankan skenario itu sebagai simulasi urutan
 * dan menyimpan log teksnya; "Baca Laporan Uji" membuka log terakhir.
 *
 * JUJUR: yang berjalan di sini adalah dry run simulasi — penyuntikan
 * agen penguji ke dalam APK (agen menekan tombol sungguhan di
 * perangkat) adalah tahap berikut dan BELUM aktif. Tidak ada tombol
 * sungguhan yang ditekan dari layar ini.
 *
 * Logika skenarionya ada di AgenUji (murni Java, sudah diuji di komputer).
 */
public class RobotUjiActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final String DIR_LOG = "uji-robot";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvSkenario;
    private TextView tvLog;

    private List<AgenUji.Langkah> skenario = new ArrayList<AgenUji.Langkah>();
    private File dirProyekAktif;
    private File logTerakhir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TeksUI.kodeAktif = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        setTitle(TeksUI.t("Robot Penguji Antarmuka"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Robot Penguji Antarmuka"), 20);
        label(r, TeksUI.t("Robot penguji menulis skenario teks dari proyekmu berupa langkah-langkah simulasi: menekan setiap tombol, mengisi kolom input dengan data ekstrem, dan mencatat urutannya dalam laporan teks. Pilih proyek, buat skenarionya, lalu jalankan dry run untuk melihat lognya."), false);
        label(r, TeksUI.t("Catatan jujur: eksekusi langsung di perangkat (penyuntikan agen penguji ke dalam APK) adalah tahap berikut dan belum aktif. Yang berjalan di sini adalah dry run simulasi urutan langkah, tanpa menekan tombol sungguhan."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Skenario Otomatis"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatSkenario();
            }
        });
        button(r, TeksUI.t("Jalankan Dry Run"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jalankanDryRun();
            }
        });
        button(r, TeksUI.t("Baca Laporan Uji"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bacaLaporan();
            }
        });

        label(r, TeksUI.t("Skenario:"), false);
        tvSkenario = new TextView(this);
        tvSkenario.setTextSize(13);
        tvSkenario.setTextIsSelectable(true);
        tvSkenario.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvSkenario);

        label(r, TeksUI.t("Hasil:"), false);
        tvLog = new TextView(this);
        tvLog.setTextSize(13);
        tvLog.setTextIsSelectable(true);
        tvLog.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvLog);
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Robot Penguji Antarmuka. Pilih proyek, lalu tekan Buat Skenario Otomatis."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
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

    private void buatSkenario() {
        final File dir = proyekTerpilih();
        if (dir == null) return;
        dirProyekAktif = dir;
        tvLog.setText("");
        tvSkenario.setText(TeksUI.t("Membuat skenario..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Manifest sumber (teks) + layout XML proyek.
                String manifest = SumberDaya.teks(
                        new File(dir, "app/src/main/AndroidManifest.xml"));
                StringBuilder deskriptor = new StringBuilder();
                File dirLayout = new File(dir, "app/src/main/res/layout");
                File[] layout = dirLayout.listFiles();
                if (layout != null) {
                    for (int i = 0; i < layout.length; i++) {
                        if (!layout[i].isFile()
                                || !layout[i].getName().endsWith(".xml")) continue;
                        String isi = SumberDaya.teks(layout[i]);
                        List<String> d = AgenUji.uraiLayout(isi);
                        for (int j = 0; j < d.size(); j++) {
                            deskriptor.append(d.get(j)).append('\n');
                        }
                    }
                }
                // Bila manifest sumber tidak ada, pakai daftar aktivitas APK.
                if (manifest == null) {
                    File apk = AnalisisApkActivity.apkTerbaru(dir);
                    if (apk != null) {
                        AnalisisApk.Hasil h = AnalisisApk.analisis(apk);
                        for (int i = 0; i < h.aktivitas.size(); i++) {
                            deskriptor.append("AKTIVITAS ").append(h.aktivitas.get(i))
                                    .append('\n');
                        }
                    }
                }
                final List<AgenUji.Langkah> hasil =
                        AgenUji.bangunSkenarioDariManifest(manifest, deskriptor.toString());
                final String teks = AgenUji.teksSkenario(hasil);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        skenario = hasil;
                        tvSkenario.setText(teks);
                        if (hasil.isEmpty()) {
                            announce(TeksUI.t("Tidak ada tombol atau kolom input yang ditemukan di proyek ini."));
                        } else {
                            announce(TeksUI.t("Skenario dibuat: ") + hasil.size()
                                    + TeksUI.t(" langkah."));
                        }
                    }
                });
            }
        }, "RobotUji-Skenario").start();
    }

    private void jalankanDryRun() {
        if (dirProyekAktif == null) {
            File dir = proyekTerpilih();
            if (dir == null) return;
            dirProyekAktif = dir;
        }
        if (skenario == null || skenario.isEmpty()) {
            toast("Belum ada skenario. Tekan Buat Skenario Otomatis dulu.");
            announce(TeksUI.t("Belum ada skenario. Tekan Buat Skenario Otomatis dulu."));
            return;
        }
        final String log = AgenUji.jalankanSkenarioDryRun(skenario);
        tvLog.setText(log);
        File dirLog = new File(dirProyekAktif, DIR_LOG);
        File berkas = new File(dirLog, AgenUji.namaBerkasLog());
        if (AgenUji.tulisTeks(berkas, log)) {
            logTerakhir = berkas;
            announce(TeksUI.t("Dry run selesai: ") + skenario.size()
                    + TeksUI.t(" langkah. Log tersimpan di ") + berkas.getName() + ".");
        } else {
            announce(TeksUI.t("Dry run selesai: ") + skenario.size()
                    + TeksUI.t(" langkah, tetapi log gagal disimpan."));
        }
    }

    private void bacaLaporan() {
        File target = logTerakhir;
        if (target == null && dirProyekAktif != null) {
            target = logTerbaru(new File(dirProyekAktif, DIR_LOG));
        }
        if (target == null) {
            File dir = proyekTerpilih();
            if (dir == null) return;
            target = logTerbaru(new File(dir, DIR_LOG));
        }
        if (target == null) {
            tvLog.setText(TeksUI.t("Belum ada laporan uji. Jalankan dry run dulu."));
            announce(TeksUI.t("Belum ada laporan uji. Jalankan dry run dulu."));
            return;
        }
        String isi = AgenUji.bacaTeks(target);
        if (isi == null) {
            tvLog.setText(TeksUI.t("Gagal membaca laporan uji: ") + target.getName());
            return;
        }
        logTerakhir = target;
        tvLog.setText(isi);
        AgenUji.Ringkasan ringkasan = AgenUji.uraiLog(isi);
        announce(ringkasan.teks());
    }

    /** Log uji terbaru di folder log, atau null bila belum ada. */
    static File logTerbaru(File dirLog) {
        File[] isi = dirLog.listFiles();
        if (isi == null) return null;
        File terbaik = null;
        for (int i = 0; i < isi.length; i++) {
            File f = isi[i];
            if (!f.isFile() || !f.getName().endsWith(".txt")) continue;
            if (terbaik == null || f.lastModified() > terbaik.lastModified()) terbaik = f;
        }
        return terbaik;
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
