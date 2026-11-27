package com.androlua;

import android.app.Activity;
import android.content.SharedPreferences;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Autopsi ID Crash dan Mesin Penambal Kode Mandiri (Fitur Gemini 5.7
 * nomor 9).
 *
 * Pilih ID crash dari folder log (CrashHandler) dan proyek yang kodenya
 * mau dicocokkan, lalu "Tampilkan Diagnosis" — IDE mengurai log,
 * mengenali pola galatnya, menjelaskan penyebabnya dalam bahasa
 * Indonesia, menampilkan baris sumber yang bermasalah bila kelasnya
 * ada di proyek, dan menyusun tambalan kode siap pakai. Tombol
 * "Terapkan Tambalan" menulis tambalan ke berkas sumber, hanya bila
 * lokasi barisnya cocok persis dengan kelas di log; bila tidak cocok,
 * teks tambalan tetap tampil di layar untuk disalin manual.
 * Logika urai/diagnosis/tambalannya ada di AutopsiCrash (murni Java,
 * sudah diuji di komputer).
 */
public class AutopsiCrashActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spCrash;
    private ArrayAdapter<String> adapterCrash;
    private final List<String> daftarCrash = new ArrayList<String>();
    private final List<File> berkasCrash = new ArrayList<File>();
    private TextView tvHasil;

    private AutopsiCrash.HasilUrai hasilAktif;
    private AutopsiCrash.Diagnosis diagnosisAktif;
    private String barisKodeAktif;
    private String tambalanAktif;
    private File berkasSumberAktif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences pref = PengaturanActivity.baca(this);
        TeksUI.kodeAktif = Bahasa.kodeEfektif(
                pref.getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                Locale.getDefault().getLanguage());
        setTitle(TeksUI.t("Autopsi Crash"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Autopsi Crash"), 20);
        label(r, TeksUI.t("Pilih ID crash dari log, lalu lihat diagnosis dan tambalan kodenya. Baris sumber dicocokkan ke berkas proyek bila kelasnya ada di proyek."), false);
        label(r, TeksUI.t("Untuk crash aplikasi HASIL BUILD: tekan Suntikkan Penangkap Crash ke Proyek, lalu build APK seperti biasa. Bila aplikasi hasil build itu crash, log-nya ditulis ke HP dan bisa dicari dengan tombol Cari Log Aplikasi Hasil Build di bawah."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih ID crash:"), false);
        spCrash = new Spinner(this);
        adapterCrash = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarCrash);
        adapterCrash.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCrash.setAdapter(adapterCrash);
        r.addView(spCrash, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Tampilkan Diagnosis"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tampilkan();
            }
        });

        button(r, TeksUI.t("Suntikkan Penangkap Crash ke Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                suntikkanPenangkap();
            }
        });

        button(r, TeksUI.t("Cari Log Aplikasi Hasil Build"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muatLogHasilBuild();
            }
        });

        button(r, TeksUI.t("Terapkan Tambalan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terapkan();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(13);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvHasil);
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        muatDaftarCrash();
        announce(TeksUI.t("Autopsi Crash. Pilih ID crash, lalu tekan Tampilkan Diagnosis."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
        muatDaftarCrash();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    /** ID crash dari nama berkas "crash-<ID>.log"; null bila tak cocok. */
    static String idDariNamaBerkas(String nama) {
        if (nama == null) return null;
        if (!nama.startsWith("crash-") || !nama.endsWith(".log")) return null;
        String id = nama.substring("crash-".length(), nama.length() - 4);
        return id.startsWith("CR-") ? id : null;
    }

    private void muatDaftarCrash() {
        daftarCrash.clear();
        berkasCrash.clear();
        File folder = CrashHandler.folderLogCrash(this);
        File[] semua = folder == null ? null : folder.listFiles();
        if (semua != null) {
            Arrays.sort(semua, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
            for (int i = 0; i < semua.length; i++) {
                File f = semua[i];
                if (!f.isFile() || !f.getName().endsWith(".log")) continue;
                String id = idDariNamaBerkas(f.getName());
                if (id == null) {
                    String isi = CrashHandler.bacaTeks(f);
                    AutopsiCrash.HasilUrai h = AutopsiCrash.uraiLogCrash(isi);
                    id = h == null ? null : h.idCrash;
                }
                if (id == null) id = f.getName();
                daftarCrash.add(id);
                berkasCrash.add(f);
            }
        }
        if (adapterCrash != null) adapterCrash.notifyDataSetChanged();
        if (daftarCrash.isEmpty()) {
            tvHasil.setText(TeksUI.t("Belum ada log crash di HP ini."));
        }
    }

    private void suntikkanPenangkap() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Menyuntikkan penangkap crash ke proyek..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = AutopsiCrash.suntikkanPenangkap(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "AutopsiCrash-Suntik").start();
    }

    /** Menambahkan log crash aplikasi hasil build ke daftar pilihan (berawalan "[hasil build]"). */
    private void muatLogHasilBuild() {
        File dirProyek = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirProyek = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        }
        List<File> tambahan = AutopsiCrash.daftarLogHasilBuild(dirProyek);
        int baru = 0;
        for (int i = 0; i < tambahan.size(); i++) {
            File f = tambahan.get(i);
            if (berkasCrash.contains(f)) continue;
            String isi = AutopsiCrash.bacaTeksLog(f);
            AutopsiCrash.HasilUrai h = isi == null ? null : AutopsiCrash.uraiLogCrash(isi);
            String id = h == null ? null : h.idCrash;
            if (id == null) id = f.getName();
            daftarCrash.add(TeksUI.t("[hasil build] ") + id);
            berkasCrash.add(f);
            baru++;
        }
        if (adapterCrash != null) adapterCrash.notifyDataSetChanged();
        if (baru == 0) {
            tvHasil.setText(TeksUI.t("Tidak ada log crash aplikasi hasil build. Pastikan penangkap crash sudah disuntikkan dan aplikasi hasil build pernah crash."));
            announce(TeksUI.t("Tidak ada log crash aplikasi hasil build."));
        } else {
            tvHasil.setText(TeksUI.t("Ditemukan ") + baru + TeksUI.t(" log crash aplikasi hasil build. Pilih ID-nya, lalu tekan Tampilkan Diagnosis."));
            announce(TeksUI.t("Ditemukan ") + baru + TeksUI.t(" log crash aplikasi hasil build."));
        }
    }

    private void tampilkan() {
        hasilAktif = null;
        diagnosisAktif = null;
        barisKodeAktif = null;
        tambalanAktif = null;
        berkasSumberAktif = null;
        if (spCrash == null || spCrash.getSelectedItem() == null
                || spCrash.getSelectedItemPosition() < 0
                || spCrash.getSelectedItemPosition() >= berkasCrash.size()) {
            toast(TeksUI.t("Pilih dulu ID crashnya."));
            return;
        }
        File log = berkasCrash.get(spCrash.getSelectedItemPosition());
        String isi = CrashHandler.bacaTeks(log);
        if (isi == null) {
            tvHasil.setText(TeksUI.t("Log crash tidak bisa dibaca."));
            announce(TeksUI.t("Log crash tidak bisa dibaca."));
            return;
        }
        hasilAktif = AutopsiCrash.uraiLogCrash(isi);
        if (hasilAktif == null) {
            tvHasil.setText(TeksUI.t("ID crash tidak dikenal di log ini."));
            announce(TeksUI.t("ID crash tidak dikenal di log ini."));
            return;
        }
        File dirProyek = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirProyek = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        }
        if (hasilAktif.kelas != null && hasilAktif.baris > 0 && dirProyek != null) {
            barisKodeAktif = AutopsiCrash.cariBarisSumber(
                    dirProyek, hasilAktif.kelas, hasilAktif.baris);
            berkasSumberAktif = AutopsiCrash.cariBerkasSumber(dirProyek, hasilAktif.kelas);
        }
        diagnosisAktif = AutopsiCrash.diagnosis(hasilAktif.jenisGalat, barisKodeAktif);
        tambalanAktif = AutopsiCrash.tambalan(diagnosisAktif.pola, barisKodeAktif);
        StringBuilder b = new StringBuilder();
        b.append(AutopsiCrash.laporan(hasilAktif, diagnosisAktif,
                barisKodeAktif, tambalanAktif));
        if (barisKodeAktif == null && hasilAktif.kelas != null) {
            b.append('\n');
            b.append(TeksUI.t("Berkas sumber tidak ditemukan di proyek ini. Tambalan di atas bisa disalin manual."));
            b.append('\n');
        }
        tvHasil.setText(b.toString());
        announce(TeksUI.t("Autopsi selesai. Pola: ") + diagnosisAktif.pola + ".");
    }

    private void terapkan() {
        if (hasilAktif == null || diagnosisAktif == null || tambalanAktif == null) {
            toast(TeksUI.t("Pilih dulu ID crashnya."));
            return;
        }
        if (berkasSumberAktif == null || barisKodeAktif == null || hasilAktif.baris < 1) {
            tvHasil.setText(tvHasil.getText().toString() + "\n"
                    + TeksUI.t("Lokasi baris tidak cocok dengan kelas di log. Tambalan tidak diterapkan; salin teks tambalan di atas."));
            announce(TeksUI.t("Lokasi baris tidak cocok dengan kelas di log. Tambalan tidak diterapkan; salin teks tambalan di atas."));
            return;
        }
        String isi = SumberDaya.teks(berkasSumberAktif);
        if (isi == null) {
            toast(TeksUI.t("Gagal menulis berkas sumber."));
            return;
        }
        // Cocokkan persis: baris sumber harus masih sama seperti saat diurai.
        String barisSekarang = AutopsiCrash.cariBarisSumber(
                berkasSumberAktif.getParentFile(), hasilAktif.kelas, hasilAktif.baris);
        if (barisSekarang == null || !barisSekarang.equals(barisKodeAktif)) {
            tvHasil.setText(tvHasil.getText().toString() + "\n"
                    + TeksUI.t("Lokasi baris tidak cocok dengan kelas di log. Tambalan tidak diterapkan; salin teks tambalan di atas."));
            announce(TeksUI.t("Lokasi baris tidak cocok dengan kelas di log. Tambalan tidak diterapkan; salin teks tambalan di atas."));
            return;
        }
        String baru = AutopsiCrash.sisipkanTambalan(isi, hasilAktif.baris, tambalanAktif);
        if (baru == null || baru.equals(isi) || !SumberDaya.tulis(berkasSumberAktif, baru)) {
            toast(TeksUI.t("Gagal menulis berkas sumber."));
            return;
        }
        announce(TeksUI.t("Tambalan diterapkan ke ") + berkasSumberAktif.getName()
                + TeksUI.t(", baris ") + hasilAktif.baris + ".");
        tampilkan();
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
