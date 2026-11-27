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
import java.util.Map;

/**
 * Perisai Polimorfik Bytecode dan Brankas Aset Terenkripsi (Fitur 1,
 * daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tekan "Acak Nama dan Enkripsi Aset" — IDE memindai
 * pengenal di kode sumber proyek, membuat peta nama acak, mengenkripsi
 * aset proyek ke brankas AES-GCM, dan menyimpan peta pemulihan
 * terenkripsi di folder proyek. Logika ada di PerisaiPolimorfik
 * (murni Java, sudah diuji di komputer).
 *
 * BATAS: peta + brankas yang dibuat di sini; penulisan ulang tabel
 * string DEX hasil kompilasi adalah tahap berikut (lihat
 * PerisaiPolimorfik).
 */
public class PerisaiPolimorfikActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;
    private Button bJalankan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Perisai Polimorfik dan Brankas Aset"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Perisai Polimorfik dan Brankas Aset"), 20);
        label(r, TeksUI.t("Acak nama pengenal dan enkripsi aset proyek dengan AES-GCM. Peta pemulihan tersimpan terenkripsi di folder proyek, supaya nama asli bisa dipulihkan saat membaca log crash."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Acak Nama dan Enkripsi Aset"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Acak Nama dan Enkripsi Aset") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Perisai Polimorfik dan Brankas Aset") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Acak Nama dan Enkripsi Aset") + ".");
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
        tvHasil.setText(TeksUI.t("Acak Nama dan Enkripsi Aset") + "...");
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
        }, "PerisaiPolimorfik").start();
    }

    private String jalankan(File dir) {
        List<String> pengenal = new ArrayList<String>();
        kumpulKode(dir, pengenal);
        Map<String, String> peta = PerisaiPolimorfik.buatPeta(pengenal, dir.getName());
        byte[] kunci = PerisaiPolimorfik.kunciDariFrasa(dir.getName());
        File brankas = new File(dir, PerisaiPolimorfik.FOLDER_BRANKAS);
        int jumlahAset = 0;
        long ukuran = 0;
        File dirAset = new File(dir, "app/src/main/assets");
        if (dirAset.isDirectory()) {
            File[] daftar = dirAset.listFiles();
            if (daftar != null) {
                for (int i = 0; i < daftar.length; i++) {
                    if (!daftar[i].isFile()) continue;
                    byte[] data = bacaBerkas(daftar[i]);
                    if (data == null) continue;
                    File tulis = PerisaiPolimorfik.tulisBrankas(brankas,
                            daftar[i].getName() + ".brankas", data, kunci);
                    if (tulis != null) {
                        jumlahAset++;
                        ukuran += tulis.length();
                    }
                }
            }
        }
        PerisaiPolimorfik.tulisBrankas(dir, PerisaiPolimorfik.NAMA_PETA,
                PerisaiPolimorfik.teksPeta(peta).getBytes(), kunci);
        return TeksUI.t("Nama diacak:") + " " + peta.size() + ". "
                + PerisaiPolimorfik.ringkasan(peta.size(), jumlahAset, ukuran);
    }

    private void kumpulKode(File dir, List<String> pengenal) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) kumpulKode(f, pengenal);
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                byte[] data = bacaBerkas(f);
                if (data == null) continue;
                try {
                    List<String> dapat = PerisaiPolimorfik.kumpulPengenal(new String(data, "UTF-8"));
                    for (int j = 0; j < dapat.size(); j++) {
                        if (!pengenal.contains(dapat.get(j))) pengenal.add(dapat.get(j));
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private byte[] bacaBerkas(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return buf.toByteArray();
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
