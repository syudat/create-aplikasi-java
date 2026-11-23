package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
 * Sinkronisasi Cloud (Tahap 3F).
 *
 * Dua jalur nyata:
 * (a) Cloud lokal: cadangan proyek ke Download/cloud (stempel waktu),
 *     daftar cadangan, pulihkan kembali ke Download/project.
 * (b) GitHub: unggah ZIP proyek ke repo privat "pembuat-aplikasi-build"
 *     (folder "cadangan") dan unduh kembali — memakai token dari
 *     Pengaturan GitHub (GithubSettings). Operasi GitHub berjalan di
 *     thread latar supaya layar tidak membeku.
 *
 * JUJUR: ini cadangan + pulihkan manual, BUKAN sinkronisasi otomatis
 * dua arah (butuh server yang membandingkan perubahan). Penjelasan ini
 * juga tertulis di layar.
 */
public class CloudActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final String DIR_CLOUD = Environment.getExternalStorageDirectory().getPath()
            + "/Download/" + SinkronCloud.DIR_CLOUD;

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Sinkronisasi Cloud"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Sinkronisasi Cloud"), 20);
        label(r, TeksUI.t("Cadangkan proyekmu ke cloud supaya aman bila HP hilang atau rusak. ")
                + TeksUI.t("Ada dua jalan: Cloud Lokal (cadangan tersimpan di folder Download/cloud di HP ini) ")
                + TeksUI.t("dan GitHub (cadangan diunggah ke repo privatmu di GitHub). ")
                + TeksUI.t("Ini cadangan manual — tekan tombol untuk mencadangkan; belum ada sinkronisasi otomatis dua arah."),
                false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvStatus = label(r, "", false);

        button(r, TeksUI.t("Cadangkan ke Cloud Lokal"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cadangkanLokal();
            }
        });
        button(r, TeksUI.t("Pulihkan dari Cloud Lokal"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pulihkanLokal();
            }
        });
        button(r, TeksUI.t("Unggah ke GitHub"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unggahGithub();
            }
        });
        button(r, TeksUI.t("Unduh dari GitHub"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unduhGithub();
            }
        });
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Sinkronisasi Cloud. Pilih proyek, lalu cadangkan ke Cloud Lokal atau GitHub."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
        if (tvStatus != null) {
            tvStatus.setText(daftarProyek.isEmpty()
                    ? TeksUI.t("Belum ada proyek di Download/project.")
                    : daftarProyek.size() + TeksUI.t(" proyek tersedia. Cadangan cloud lokal: ")
                    + SinkronCloud.daftarCadanganCloud(new File(DIR_CLOUD)).size() + TeksUI.t(" berkas."));
        }
    }

    private String proyekTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        return String.valueOf(spProyek.getSelectedItem());
    }

    // ------------------------------------------------------- cloud lokal

    private void cadangkanLokal() {
        String nama = proyekTerpilih();
        if (nama == null) {
            toast("Pilih dulu proyek yang mau dicadangkan.");
            return;
        }
        File hasil = SinkronCloud.buatCadanganCloud(new File(ROOT + "/" + nama),
                new File(DIR_CLOUD));
        if (hasil != null) {
            String ok = "Cadangan tersimpan: " + hasil.getName();
            toast(ok);
            announce(ok);
            muatDaftar();
        } else {
            toast("Gagal membuat cadangan. Periksa izin penyimpanan.");
        }
    }

    private void pulihkanLokal() {
        final List<File> cadangan = SinkronCloud.daftarCadanganCloud(new File(DIR_CLOUD));
        if (cadangan.isEmpty()) {
            toast("Belum ada cadangan di Download/cloud.");
            return;
        }
        final String[] nama = new String[cadangan.size()];
        for (int i = 0; i < cadangan.size(); i++) nama[i] = cadangan.get(i).getName();
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pulihkan dari Cloud Lokal"))
                .setItems(nama, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File zip = cadangan.get(which);
                        String galat = SinkronCloud.pulihkan(zip, new File(ROOT));
                        if (galat == null) {
                            String ok = TeksUI.t("Proyek dipulihkan dari ") + zip.getName();
                            toast(ok);
                            announce(ok);
                            muatDaftar();
                        } else {
                            toast(galat);
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    // ------------------------------------------------------------ GitHub

    private void unggahGithub() {
        final String nama = proyekTerpilih();
        if (nama == null) {
            toast("Pilih dulu proyek yang mau diunggah.");
            return;
        }
        final String token = GithubSettings.bacaToken(this);
        if (token.isEmpty()) {
            GithubSettings.tampilkan(this, new Runnable() {
                @Override
                public void run() {
                    unggahGithub();
                }
            });
            return;
        }
        toast(TeksUI.t("Mengunggah ") + nama + TeksUI.t(" ke GitHub..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = unggahGithubLatar(nama, token);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasil == null) {
                            String ok = TeksUI.t("Proyek ") + nama + TeksUI.t(" terunggah ke GitHub.");
                            toast(ok);
                            announce(ok);
                        } else {
                            toast(hasil);
                            announce(hasil);
                        }
                    }
                });
            }
        }).start();
    }

    private String unggahGithubLatar(String nama, String token) {
        SinkronCloud.PengirimHttp http = new SinkronCloud.PengirimHttpBawaan();
        String pemilik = SinkronCloud.namaPengguna(token, SinkronCloud.API_GITHUB, http);
        if (pemilik.isEmpty()) {
            return TeksUI.t("Gagal membaca akun GitHub. Periksa token di Pengaturan GitHub.");
        }
        String jalurZip = EksporProyek.ekspor(ROOT + "/" + nama);
        if (jalurZip == null || jalurZip.isEmpty()) {
            return TeksUI.t("Gagal membuat ZIP proyek. Periksa izin penyimpanan.");
        }
        File zip = new File(jalurZip);
        try {
            String namaBerkas = ArsipProyek.namaBerkasCadangan(nama);
            return SinkronCloud.unggah(token, SinkronCloud.API_GITHUB, pemilik,
                    SinkronCloud.REPO_CADANGAN, SinkronCloud.JALUR_CLOUD + "/" + namaBerkas,
                    zip, TeksUI.t("Cadangan proyek ") + nama, http);
        } finally {
            zip.delete();
        }
    }

    private void unduhGithub() {
        final String token = GithubSettings.bacaToken(this);
        if (token.isEmpty()) {
            GithubSettings.tampilkan(this, new Runnable() {
                @Override
                public void run() {
                    unduhGithub();
                }
            });
            return;
        }
        toast("Mengambil daftar cadangan dari GitHub...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                SinkronCloud.PengirimHttp http = new SinkronCloud.PengirimHttpBawaan();
                final String pemilik = SinkronCloud.namaPengguna(token, SinkronCloud.API_GITHUB, http);
                if (pemilik.isEmpty()) {
                    tampilkanGalat("Gagal membaca akun GitHub. Periksa token di Pengaturan GitHub.");
                    return;
                }
                final List<String> daftar = SinkronCloud.daftarGithub(token,
                        SinkronCloud.API_GITHUB, pemilik, SinkronCloud.REPO_CADANGAN,
                        SinkronCloud.JALUR_CLOUD, http);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pilihUnduhanGithub(token, pemilik, daftar);
                    }
                });
            }
        }).start();
    }

    private void pilihUnduhanGithub(final String token, final String pemilik,
                                    final List<String> daftar) {
        if (daftar.isEmpty()) {
            toast("Belum ada cadangan di repo GitHub " + SinkronCloud.REPO_CADANGAN + ".");
            return;
        }
        final String[] nama = daftar.toArray(new String[daftar.size()]);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Unduh dari GitHub"))
                .setItems(nama, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        unduhSatuGithub(token, pemilik, nama[which]);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void unduhSatuGithub(final String token, final String pemilik, final String namaBerkas) {
        toast(TeksUI.t("Mengunduh ") + namaBerkas + TeksUI.t(" dari GitHub..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                SinkronCloud.PengirimHttp http = new SinkronCloud.PengirimHttpBawaan();
                File tujuan = new File(DIR_CLOUD, namaBerkas);
                final File hasil = SinkronCloud.unduh(token, SinkronCloud.API_GITHUB, pemilik,
                        SinkronCloud.REPO_CADANGAN, SinkronCloud.JALUR_CLOUD + "/" + namaBerkas,
                        tujuan, http);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasil == null) {
                            toast("Gagal mengunduh " + namaBerkas + TeksUI.t(" dari GitHub."));
                            return;
                        }
                        String galat = SinkronCloud.pulihkan(hasil, new File(ROOT));
                        if (galat == null) {
                            String ok = TeksUI.t("Proyek dipulihkan dari GitHub: ") + namaBerkas;
                            toast(ok);
                            announce(ok);
                            muatDaftar();
                        } else {
                            toast(galat);
                        }
                    }
                });
            }
        }).start();
    }

    private void tampilkanGalat(final String pesan) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toast(pesan);
                announce(pesan);
            }
        });
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
