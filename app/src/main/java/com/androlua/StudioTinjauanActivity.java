package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Studio Tinjauan Kode Tim, Penggabungan Tiga-Arah, dan Papan Tugas
 * Asinkron (Fitur 15, daftar Gemini 2026-11-27).
 *
 * Pilih proyek, tulis tiga versi teks (dasar, lokal, rekan), tekan
 * "Buka Tinjauan Tim" — IDE menggabungkan ketiganya dan menampilkan
 * tiap konflik sebagai pilihan suara (lokal / rekan / gabung).
 * Papan tugas di bawahnya menyimpan tugas proyek sebagai teks.
 * Logika ada di StudioTinjauan (murni Java, sudah diuji di komputer).
 *
 * BATAS: penggabungan memakai pencocokan baris untuk teks biasa,
 * dan satuan sintaks Java (pernyataan/blok) untuk kode Java; papan
 * tugas disinkronkan lewat Git di dalam aplikasi — berkas papan
 * di-commit sehingga ikut riwayat Git proyek.
 */
public class StudioTinjauanActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eDasar;
    private EditText eLokal;
    private EditText eRekan;
    private EditText eTugas;
    private TextView tvHasil;
    private TextView tvPapan;
    private TextView tvGit;
    private Spinner spMode;
    private LinearLayout daftarKonflik;
    private Button bJalankan;
    private StudioTinjauan.HasilGabung gabungAktif;
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
        setTitle(TeksUI.t("Studio Tinjauan Kode Tim"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Studio Tinjauan Kode Tim"), 20);
        label(r, TeksUI.t("Tinjau perubahan rekan baris demi baris, selesaikan konflik Git tiga-arah dengan pilihan suara yang jelas, dan kelola papan tugas proyek."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Teks dasar (versi sebelum diubah):"), false);
        eDasar = new EditText(this);
        GayaStudio.edit(eDasar);
        eDasar.setHint(TeksUI.t("Contoh: baris satu"));
        eDasar.setText("baris satu\nbaris dua\nbaris tiga");
        eDasar.setTextSize(14);
        eDasar.setMinLines(2);
        r.addView(eDasar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Teks lokal (versi saya):"), false);
        eLokal = new EditText(this);
        GayaStudio.edit(eLokal);
        eLokal.setHint(TeksUI.t("Contoh: baris satu diubah"));
        eLokal.setText("baris satu diubah\nbaris dua\nbaris tiga");
        eLokal.setTextSize(14);
        eLokal.setMinLines(2);
        r.addView(eLokal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Teks rekan (versi teman):"), false);
        eRekan = new EditText(this);
        GayaStudio.edit(eRekan);
        eRekan.setHint(TeksUI.t("Contoh: baris satu dari rekan"));
        eRekan.setText("baris satu dari rekan\nbaris dua\nbaris tiga");
        eRekan.setTextSize(14);
        eRekan.setMinLines(2);
        r.addView(eRekan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Buka Tinjauan Tim"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        label(r, TeksUI.t("Mode gabung:"), false);
        spMode = new Spinner(this);
        ArrayAdapter<String> adapterMode = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                new String[]{TeksUI.t("Otomatis"), TeksUI.t("Java (sintaks)"),
                        TeksUI.t("Teks (baris)")});
        adapterMode.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMode.setAdapter(adapterMode);
        r.addView(spMode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Buka Tinjauan Tim") + ".");
        r.addView(tvHasil);

        daftarKonflik = new LinearLayout(this);
        daftarKonflik.setOrientation(LinearLayout.VERTICAL);
        r.addView(daftarKonflik, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Papan tugas:"), false);
        tvPapan = new TextView(this);
        tvPapan.setTextSize(14);
        tvPapan.setTextIsSelectable(true);
        tvPapan.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvPapan);

        eTugas = new EditText(this);
        GayaStudio.edit(eTugas);
        eTugas.setHint(TeksUI.t("Contoh: perbaiki layar masuk"));
        eTugas.setTextSize(14);
        eTugas.setSingleLine(true);
        r.addView(eTugas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Tambah Tugas"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tambahTugas();
            }
        });

        label(r, TeksUI.t("Papan tugas di Git:"), false);
        tvGit = new TextView(this);
        tvGit.setTextSize(14);
        tvGit.setTextIsSelectable(true);
        tvGit.setPadding(0, dp(4), 0, dp(4));
        tvGit.setText(TeksUI.t("Papan tugas belum disinkronkan ke Git."));
        r.addView(tvGit);

        button(r, TeksUI.t("Simpan Papan ke Git"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanPapanGit();
            }
        });

        button(r, TeksUI.t("Lihat Riwayat Papan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lihatRiwayat();
            }
        });

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Studio Tinjauan Kode Tim") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Buka Tinjauan Tim") + ".");
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
        muatPapan();
    }

    private File berkasPapan() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        return new File(dirAktif, ".tugas/papan.txt");
    }

    private void muatPapan() {
        File f = berkasPapan();
        if (f == null) return;
        List<StudioTinjauan.Tugas> papan = StudioTinjauan.uraiPapan(bacaTeks(f));
        tvPapan.setText(StudioTinjauan.teksPapan(papan));
    }

    private void tambahTugas() {
        File f = berkasPapan();
        if (f == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        List<StudioTinjauan.Tugas> papan = StudioTinjauan.uraiPapan(bacaTeks(f));
        if (!StudioTinjauan.tambahTugas(papan, eTugas.getText().toString())) {
            toast(TeksUI.t("Isi dulu judul tugas."));
            return;
        }
        tulisTeks(f, StudioTinjauan.teksPapan(papan));
        eTugas.setText("");
        muatPapan();
        announce(TeksUI.t("Tugas ditambahkan."));
    }

    private void mulai() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        final String dasar = eDasar.getText().toString();
        final String lokal = eLokal.getText().toString();
        final String rekan = eRekan.getText().toString();
        final int mode = spMode == null ? 0 : spMode.getSelectedItemPosition();
        tvHasil.setText(TeksUI.t("Buka Tinjauan Tim") + "...");
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mode == 1) {
                    gabungAktif = StudioTinjauan.gabungTigaArahSintaks(dasar, lokal, rekan);
                } else if (mode == 2) {
                    gabungAktif = StudioTinjauan.gabungTigaArah(dasar, lokal, rekan);
                } else {
                    gabungAktif = StudioTinjauan.gabungOtomatis(dasar, lokal, rekan);
                }
                final String hasil = tampilkanGabung();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        tampilkanTombolKonflik();
                        announce(hasil);
                    }
                });
            }
        }, "StudioTinjauan").start();
    }

    private void simpanPapanGit() {
        File f = berkasPapan();
        if (f == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = dirAktif;
        final List<StudioTinjauan.Tugas> papan = StudioTinjauan.uraiPapan(bacaTeks(f));
        tvGit.setText(TeksUI.t("Simpan Papan ke Git") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StudioTinjauan.HasilSinkron h =
                        StudioTinjauan.sinkronPapanGit(dir, papan, "Perbarui papan tugas");
                final String hasil = h.toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvGit.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "StudioTinjauanGit").start();
    }

    private void lihatRiwayat() {
        File f = berkasPapan();
        if (f == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File dir = dirAktif;
        tvGit.setText(TeksUI.t("Lihat Riwayat Papan") + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<GitLokal.Commit> riwayat = StudioTinjauan.riwayatPapan(dir);
                final StringBuilder b = new StringBuilder();
                if (riwayat.isEmpty()) {
                    b.append(TeksUI.t("Belum ada commit Git di proyek ini."));
                } else {
                    b.append(TeksUI.t("Riwayat commit (terbaru dulu):"));
                    int tampil = Math.min(5, riwayat.size());
                    for (int i = 0; i < tampil; i++) {
                        GitLokal.Commit c = riwayat.get(i);
                        b.append("\n- ").append(c.idPendek()).append(" ").append(c.tanggal())
                                .append(" ").append(c.pesan);
                    }
                    b.append("\n").append(TeksUI.t("Jumlah commit:")).append(" ")
                            .append(riwayat.size()).append(".");
                }
                final String hasil = b.toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvGit.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "StudioTinjauanRiwayat").start();
    }

    private String tampilkanGabung() {
        if (gabungAktif == null) return "";
        int perubahan = gabungAktif.baris.size();
        List<StudioTinjauan.Tugas> papan = StudioTinjauan.uraiPapan(bacaTeks(berkasPapan()));
        int selesai = 0;
        for (int i = 0; i < papan.size(); i++) if ("selesai".equals(papan.get(i).status)) selesai++;
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Perubahan ditinjau:")).append(" ").append(perubahan).append(". ");
        b.append(TeksUI.t("Konflik ditemukan:")).append(" ").append(gabungAktif.konflik.size()).append(". ");
        b.append(TeksUI.t("Tugas selesai:")).append(" ").append(selesai)
                .append(" dari ").append(papan.size()).append(".");
        b.append("\n").append(gabungAktif.teks());
        for (int i = 0; i < gabungAktif.konflik.size(); i++) {
            b.append("\n! ").append(gabungAktif.konflik.get(i).toString());
        }
        return b.toString();
    }

    private void tampilkanTombolKonflik() {
        daftarKonflik.removeAllViews();
        if (gabungAktif == null) return;
        for (int i = 0; i < gabungAktif.konflik.size(); i++) {
            final StudioTinjauan.Konflik c = gabungAktif.konflik.get(i);
            TextView tv = new TextView(this);
            tv.setTextSize(13);
            tv.setTextIsSelectable(true);
            tv.setPadding(0, dp(8), 0, dp(2));
            GayaStudio.label(tv, 13);
            tv.setText(c.toString());
            daftarKonflik.addView(tv);
            button(daftarKonflik, TeksUI.t("Gunakan versi lokal"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pilihKonflik(c, "lokal");
                }
            });
            button(daftarKonflik, TeksUI.t("Gunakan versi rekan"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pilihKonflik(c, "rekan");
                }
            });
            button(daftarKonflik, TeksUI.t("Gabung keduanya"), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pilihKonflik(c, "gabung");
                }
            });
        }
    }

    private void pilihKonflik(StudioTinjauan.Konflik c, String pilihan) {
        if (gabungAktif == null) return;
        List<String> selesai = StudioTinjauan.selesaikanKonflik(c, pilihan);
        gabungAktif.konflik.remove(c);
        // Perbaikan fase 3: baris hasil disisipkan pada posisi konflik
        // semula, bukan ditempel di akhir — urutan baris tetap benar.
        // Konflik yang masih tersisa di bawahnya digeser posisinya.
        int pos = c.posisi;
        if (pos < 0) pos = 0;
        if (pos > gabungAktif.baris.size()) pos = gabungAktif.baris.size();
        gabungAktif.baris.addAll(pos, selesai);
        for (int i = 0; i < gabungAktif.konflik.size(); i++) {
            StudioTinjauan.Konflik lain = gabungAktif.konflik.get(i);
            if (lain.posisi >= pos) lain.posisi += selesai.size();
        }
        if (dirAktif != null) {
            tulisTeks(new File(dirAktif, "gabung-hasil.txt"), gabungAktif.teks());
        }
        tvHasil.setText(tampilkanGabung());
        tampilkanTombolKonflik();
        announce(TeksUI.t("Konflik diselesaikan."));
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
