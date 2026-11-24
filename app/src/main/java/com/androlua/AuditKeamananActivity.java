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
import java.util.Locale;

/**
 * Auditor Keamanan Statis (SAST) dan Penambal Celah Otomatis (Fitur 7, daftar Gemini).
 *
 * Pilih proyek, tekan "Mulai Pemindaian" — IDE memindai seluruh kode
 * sumber, manifes, dan konfigurasi proyek secara luring, lalu menampilkan
 * temuan urut tingkat risiko (tinggi, sedang, rendah) lengkap dengan
 * berkas, baris, dan penjelasan dampaknya. Setiap temuan yang
 * perbaikannya aman dan lokasinya persis mendapat tombol
 * "Terapkan Perbaikan" yang menulis ulang berkasnya; untuk temuan lain
 * hanya teks perbaikannya yang ditampilkan. Logika pindainya ada di
 * AuditKeamanan (murni Java, sudah diuji di komputer).
 */
public class AuditKeamananActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvRingkasan;
    private LinearLayout daftarTemuan;
    private Button bPindai;

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
        setTitle(TeksUI.t("Auditor Keamanan"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Auditor Keamanan"), 20);
        label(r, TeksUI.t("Pindai kode sumber, manifes, dan konfigurasi proyek sebelum APK ")
                + TeksUI.t("ditandatangani. IDE mencari kunci API yang tertulis terbuka, ")
                + TeksUI.t("komponen yang diekspor tanpa izin, celah injeksi WebView, ")
                + TeksUI.t("kriptografi lemah, alamat http tanpa enkripsi, dan kata sandi ")
                + TeksUI.t("yang tertulis di kode. Semua berjalan di dalam HP (tanpa internet)."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bPindai = button(r, TeksUI.t("Mulai Pemindaian"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulaiPindai();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvRingkasan = new TextView(this);
        tvRingkasan.setTextSize(14);
        tvRingkasan.setTextIsSelectable(true);
        tvRingkasan.setPadding(0, dp(4), 0, dp(4));
        tvRingkasan.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan Mulai Pemindaian."));
        r.addView(tvRingkasan);

        daftarTemuan = new LinearLayout(this);
        daftarTemuan.setOrientation(LinearLayout.VERTICAL);
        r.addView(daftarTemuan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Auditor Keamanan. Pilih proyek, lalu tekan Mulai Pemindaian."));
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

    private void mulaiPindai() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        dirAktif = dir;
        tvRingkasan.setText(TeksUI.t("Memindai ") + dir.getName() + "...");
        bPindai.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AuditKeamanan.Temuan> hasil = AuditKeamanan.pindai(dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bPindai.setEnabled(true);
                        tampilkan(hasil);
                    }
                });
            }
        }, "AuditKeamanan").start();
    }

    private void tampilkan(List<AuditKeamanan.Temuan> hasil) {
        tvRingkasan.setText(AuditKeamanan.ringkasan(hasil));
        daftarTemuan.removeAllViews();
        for (int i = 0; i < hasil.size(); i++) {
            final AuditKeamanan.Temuan t = hasil.get(i);
            TextView tv = new TextView(this);
            tv.setTextSize(13);
            tv.setTextIsSelectable(true);
            tv.setPadding(0, dp(8), 0, dp(2));
            GayaStudio.label(tv, 13);
            tv.setText("[" + t.tingkat + "] " + t.berkas + ":" + t.baris
                    + " — " + t.aturan + "\n" + t.penjelasan + "\n"
                    + TeksUI.t("Perbaikan: ") + t.perbaikan);
            daftarTemuan.addView(tv);
            if (AuditKeamanan.bisaDiterapkan(dirAktif, t)) {
                Button b = new Button(this);
                b.setText(TeksUI.t("Terapkan Perbaikan"));
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        terapkan(t);
                    }
                });
                GayaStudio.tombol(b);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                daftarTemuan.addView(b, lp);
            }
        }
        announce(AuditKeamanan.ringkasan(hasil));
    }

    private void terapkan(final AuditKeamanan.Temuan t) {
        if (dirAktif == null) return;
        final File dir = dirAktif;
        bPindai.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = AuditKeamanan.terapkanPerbaikan(dir, t);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bPindai.setEnabled(true);
                        if (ok) {
                            toast(TeksUI.t("Perbaikan diterapkan di ") + t.berkas + ":" + t.baris);
                            announce(TeksUI.t("Perbaikan diterapkan. Memindai ulang."));
                            mulaiPindai();
                        } else {
                            toast(TeksUI.t("Perbaikan tidak bisa diterapkan. Barisnya mungkin sudah berubah."));
                            announce(TeksUI.t("Perbaikan tidak bisa diterapkan."));
                        }
                    }
                });
            }
        }, "AuditKeamanan-Terapkan").start();
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
