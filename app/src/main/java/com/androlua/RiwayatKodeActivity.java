package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Mesin Perjalanan Waktu Kode (Fitur 5 dari daftar Gemini, 5.7).
 *
 * Pilih proyek, lalu "Rekam Titik Sekarang" untuk menyimpan keadaan
 * kode saat ini sebagai titik waktu berlabel. Daftar titik waktu
 * menampilkan tanggal dan jumlah berkas/baris tiap titik. "Bandingkan"
 * membandingkan dua titik yang dipilih (berkas + fungsi yang ditambah /
 * dihapus, sebagai teks). "Pulihkan" mengembalikan proyek ke titik
 * yang dipilih — keadaan saat ini direkam dulu sebagai cadangan,
 * jadi tidak ada yang hilang. "Buat Salinan Eksperimen" menyalin
 * seluruh proyek ke folder baru di sampingnya untuk dicoba-coba.
 *
 * Tanpa Git CLI: arsip titik waktu tersimpan di folder .riwayat/
 * di dalam proyek sendiri. Logikanya ada di RiwayatKode (murni Java,
 * sudah diuji di komputer).
 */
public class RiwayatKodeActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eLabel;
    private EditText eNamaEksperimen;
    private Spinner spA;
    private Spinner spB;
    private ArrayAdapter<String> adapterA;
    private ArrayAdapter<String> adapterB;
    private final List<String> barisA = new ArrayList<String>();
    private final List<String> barisB = new ArrayList<String>();
    private final List<RiwayatKode.Titik> titikAktif = new ArrayList<RiwayatKode.Titik>();
    private TextView tvDaftar;
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TeksUI.kodeAktif = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        setTitle(TeksUI.t("Perjalanan Waktu Kode"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Perjalanan Waktu Kode"), 20);
        label(r, TeksUI.t("Simpan keadaan kode proyek sebagai titik waktu, bandingkan dua titik, pulihkan keadaan lama, atau buat salinan eksperimen — tanpa mengetik perintah Git. Setiap pemulihan membuat cadangan dulu, jadi tidak ada yang hilang."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Label titik waktu (boleh kosong):"), false);
        eLabel = new EditText(this);
        eLabel.setHint(TeksUI.t("Contoh: sebelum ubah tombol"));
        eLabel.setSingleLine(true);
        GayaStudio.edit(eLabel);
        r.addView(eLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Rekam Titik Sekarang"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rekamTitik();
            }
        });

        label(r, TeksUI.t("Titik waktu:"), false);
        tvDaftar = new TextView(this);
        tvDaftar.setTextSize(13);
        tvDaftar.setTextIsSelectable(true);
        tvDaftar.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvDaftar);

        label(r, TeksUI.t("Titik pertama (dari):"), false);
        spA = new Spinner(this);
        adapterA = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, barisA);
        adapterA.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spA.setAdapter(adapterA);
        r.addView(spA, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Titik kedua (ke):"), false);
        spB = new Spinner(this);
        adapterB = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, barisB);
        adapterB.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spB.setAdapter(adapterB);
        r.addView(spB, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Bandingkan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bandingkan();
            }
        });
        button(r, TeksUI.t("Pulihkan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tanyaPulihkan();
            }
        });

        label(r, TeksUI.t("Nama salinan eksperimen:"), false);
        eNamaEksperimen = new EditText(this);
        eNamaEksperimen.setHint(TeksUI.t("Contoh: coba-tombol-baru"));
        eNamaEksperimen.setSingleLine(true);
        GayaStudio.edit(eNamaEksperimen);
        r.addView(eNamaEksperimen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Buat Salinan Eksperimen"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatSalinan();
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
        announce(TeksUI.t("Perjalanan Waktu Kode. Pilih proyek, lalu tekan Rekam Titik Sekarang."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
        muatTitik();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private File proyekTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return null;
        }
        return new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
    }

    /** Muat ulang daftar titik waktu proyek terpilih ke layar + spinner A/B. */
    private void muatTitik() {
        File dir = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        }
        titikAktif.clear();
        barisA.clear();
        barisB.clear();
        if (dir != null) {
            List<RiwayatKode.Titik> daftar = RiwayatKode.daftarTitik(dir);
            titikAktif.addAll(daftar);
            for (int i = 0; i < daftar.size(); i++) {
                barisA.add(daftar.get(i).barisDaftar());
                barisB.add(daftar.get(i).barisDaftar());
            }
        }
        if (adapterA != null) adapterA.notifyDataSetChanged();
        if (adapterB != null) adapterB.notifyDataSetChanged();
        StringBuilder b = new StringBuilder();
        if (titikAktif.isEmpty()) {
            b.append(TeksUI.t("Belum ada titik waktu. Tekan Rekam Titik Sekarang."));
        } else {
            for (int i = 0; i < titikAktif.size(); i++) {
                b.append(i + 1).append(". ").append(titikAktif.get(i).barisDaftar())
                 .append('\n');
            }
        }
        tvDaftar.setText(b.toString());
    }

    private RiwayatKode.Titik titikTerpilih(Spinner sp) {
        if (sp == null || titikAktif.isEmpty()) return null;
        int pos = sp.getSelectedItemPosition();
        if (pos < 0 || pos >= titikAktif.size()) return null;
        return titikAktif.get(pos);
    }

    private void rekamTitik() {
        final File dir = proyekTerpilih();
        if (dir == null) return;
        final String label = eLabel.getText().toString().trim();
        tvHasil.setText(TeksUI.t("Merekam titik waktu..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final RiwayatKode.HasilRekam h = RiwayatKode.rekamTitik(dir, label);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (h.berhasil()) {
                            tvHasil.setText(TeksUI.t("Titik waktu direkam: ")
                                    + h.titik.barisDaftar());
                            announce(TeksUI.t("Titik waktu direkam: ") + h.titik.label
                                    + TeksUI.t(", ") + h.titik.jumlahBerkas
                                    + TeksUI.t(" berkas."));
                        } else {
                            tvHasil.setText(h.galat);
                            announce(h.galat);
                        }
                        muatTitik();
                    }
                });
            }
        }, "RiwayatKode-Rekam").start();
    }

    private void bandingkan() {
        RiwayatKode.Titik a = titikTerpilih(spA);
        RiwayatKode.Titik b = titikTerpilih(spB);
        if (a == null || b == null) {
            toast(TeksUI.t("Pilih dulu dua titik waktu."));
            announce(TeksUI.t("Pilih dulu dua titik waktu."));
            return;
        }
        if (a.dir.equals(b.dir)) {
            toast(TeksUI.t("Pilih dua titik waktu yang berbeda."));
            announce(TeksUI.t("Pilih dua titik waktu yang berbeda."));
            return;
        }
        final RiwayatKode.Titik fa = a;
        final RiwayatKode.Titik fb = b;
        tvHasil.setText(TeksUI.t("Membandingkan..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String laporan = RiwayatKode.bandingTitik(fa.dir, fb.dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvHasil.setText(laporan);
                        announce(TeksUI.t("Perbandingan selesai. Laporan lengkap ada di layar."));
                    }
                });
            }
        }, "RiwayatKode-Banding").start();
    }

    private void tanyaPulihkan() {
        final File dir = proyekTerpilih();
        if (dir == null) return;
        final RiwayatKode.Titik t = titikTerpilih(spB);
        if (t == null) {
            toast(TeksUI.t("Pilih dulu titik waktu yang mau dipulihkan (titik kedua)."));
            announce(TeksUI.t("Pilih dulu titik waktu yang mau dipulihkan."));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pulihkan titik waktu?"))
                .setMessage(TeksUI.t("Proyek akan dikembalikan ke keadaan titik \"") + t.label
                        + TeksUI.t("\" (") + t.tanggal() + TeksUI.t("). Keadaan proyek saat ini akan direkam dulu sebagai cadangan \"")
                        + RiwayatKode.LABEL_CADANGAN_PULIH + TeksUI.t("\", jadi tidak ada yang hilang. Lanjutkan?"))
                .setPositiveButton(TeksUI.t("Pulihkan"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pulihkan(dir, t);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void pulihkan(final File dir, final RiwayatKode.Titik t) {
        tvHasil.setText(TeksUI.t("Memulihkan..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String galat = RiwayatKode.pulihkanTitik(dir, t.dir);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (galat == null) {
                            tvHasil.setText(TeksUI.t("Proyek dipulihkan ke titik \"") + t.label
                                    + TeksUI.t("\". Cadangan keadaan sebelumnya tersimpan sebagai titik \"")
                                    + RiwayatKode.LABEL_CADANGAN_PULIH + TeksUI.t("\"."));
                            announce(TeksUI.t("Proyek dipulihkan ke titik ") + t.label + ".");
                        } else {
                            tvHasil.setText(galat);
                            announce(galat);
                        }
                        muatTitik();
                    }
                });
            }
        }, "RiwayatKode-Pulih").start();
    }

    private void buatSalinan() {
        final File dir = proyekTerpilih();
        if (dir == null) return;
        final String nama = eNamaEksperimen.getText().toString().trim();
        if (nama.length() == 0) {
            toast(TeksUI.t("Isi dulu nama salinan eksperimen."));
            announce(TeksUI.t("Isi dulu nama salinan eksperimen."));
            return;
        }
        tvHasil.setText(TeksUI.t("Membuat salinan eksperimen..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final RiwayatKode.HasilSalinan h =
                        RiwayatKode.buatSalinanEksperimen(dir, nama);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (h.berhasil()) {
                            tvHasil.setText(TeksUI.t("Salinan eksperimen dibuat: ")
                                    + h.dir.getName());
                            announce(TeksUI.t("Salinan eksperimen dibuat: ")
                                    + h.dir.getName() + ".");
                        } else {
                            tvHasil.setText(h.galat);
                            announce(h.galat);
                        }
                        muatDaftar();
                    }
                });
            }
        }, "RiwayatKode-Salinan").start();
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
