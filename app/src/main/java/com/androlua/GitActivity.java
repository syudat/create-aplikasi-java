package com.androlua;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Git di Dalam Aplikasi (Tahap 3I).
 *
 * Pilih proyek, lalu: buat repo Git lokal, lihat status (baru/berubah/
 * dihapus), commit dengan pesan, lihat log commit, push/pull ke GitHub
 * (snapshot, butuh token), clone dari URL, atau push/pull memakai
 * biner git sistem bila ada.
 *
 * Batasan jujur yang ditulis di layar: ini BUKAN git penuh (tidak ada
 * merge/branch/diff; tiap commit menyimpan snapshot ZIP utuh); push/
 * pull GitHub hanya snapshot utuh lewat Contents API; git sistem hanya
 * jalan bila biner git terpasang di HP (biasanya tidak ada — pakai
 * Termux "pkg install git" atau unduh ZIP repo dan impor).
 */
public class GitActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private TextView tvHasil;
    private EditText ePesan;
    private EditText eUrlClone;
    private EditText eNamaClone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Git di Dalam Aplikasi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Git di Dalam Aplikasi"), 20);
        label(r, TeksUI.t("Git lokal (init, status, commit, log) jalan tanpa internet. ")
                + TeksUI.t("Push/pull ke GitHub memakai snapshot lewat token GitHub. ")
                + TeksUI.t("Clone dan push/pull git sistem hanya jalan bila biner git ")
                + TeksUI.t("terpasang di HP (biasanya tidak ada). Ini bukan git penuh: ")
                + TeksUI.t("tidak ada merge, branch, atau diff — tiap commit menyimpan ")
                + TeksUI.t("snapshot ZIP utuh proyek."), false);
        label(r, TeksUI.t("Git sistem di HP ini: ")
                + (GitLokal.adaGitSistem() ? "TERSEDIA" : TeksUI.t("TIDAK TERSEDIA")), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Buat Repo Git"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatRepo();
            }
        });
        button(r, TeksUI.t("Periksa Status"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                periksaStatus();
            }
        });

        label(r, TeksUI.t("Pesan commit:"), false);
        ePesan = new EditText(this);
        GayaStudio.edit(ePesan);
        ePesan.setHint("Contoh: Tambah tombol simpan");
        r.addView(ePesan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, "Commit", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatCommit();
            }
        });
        button(r, TeksUI.t("Lihat Log Commit"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lihatLog();
            }
        });

        button(r, TeksUI.t("Push ke GitHub (snapshot)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pushGithub();
            }
        });
        button(r, TeksUI.t("Pull dari GitHub (snapshot)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pullGithub();
            }
        });
        button(r, TeksUI.t("Push (git sistem)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pushSistem();
            }
        });
        button(r, TeksUI.t("Pull (git sistem)"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pullSistem();
            }
        });

        label(r, TeksUI.t("Clone dari URL:"), false);
        eUrlClone = new EditText(this);
        GayaStudio.edit(eUrlClone);
        eUrlClone.setHint("https://github.com/pemilik/repo.git");
        r.addView(eUrlClone, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        eNamaClone = new EditText(this);
        GayaStudio.edit(eNamaClone);
        eNamaClone.setHint("Nama folder hasil clone");
        r.addView(eNamaClone, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, "Clone", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatClone();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
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
        announce(TeksUI.t("Git di Dalam Aplikasi. Pilih proyek, lalu buat repo, commit, atau push."));
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
    }

    private String proyekTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        return String.valueOf(spProyek.getSelectedItem());
    }

    private File dirTerpilih() {
        String nama = proyekTerpilih();
        if (nama == null) return null;
        return new File(ROOT + "/" + nama);
    }

    private void tampil(String teks) {
        if (tvHasil != null) tvHasil.setText(teks);
        announce(teks);
    }

    private void buatRepo() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        String galat = GitLokal.init(dir);
        if (galat == null) {
            tampil(TeksUI.t("Repo Git dibuat di proyek ") + dir.getName()
                    + TeksUI.t(" (cabang main). Silakan commit perubahan pertama."));
            toast(TeksUI.t("Repo Git dibuat."));
        } else {
            tampil(galat);
            toast(galat);
        }
    }

    private void periksaStatus() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        if (!GitLokal.adaGit(dir)) {
            tampil(TeksUI.t("Proyek ini belum punya repo Git. Tekan \"Buat Repo Git\" dulu."));
            return;
        }
        List<GitLokal.StatusItem> st = GitLokal.status(dir);
        if (st == null || st.isEmpty()) {
            tampil(TeksUI.t("Bersih — tidak ada perubahan sejak commit terakhir."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Status proyek ")).append(dir.getName()).append(" (")
                .append(st.size()).append(TeksUI.t(" perubahan):") + "\n");
        for (int i = 0; i < st.size(); i++) {
            sb.append(st.get(i).status).append(": ").append(st.get(i).jalan).append("\n");
        }
        tampil(sb.toString());
    }

    private void buatCommit() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        GitLokal.HasilCommit h = GitLokal.commit(dir,
                ePesan == null ? "" : ePesan.getText().toString());
        if (h.berhasil()) {
            tampil(TeksUI.t("Commit berhasil: ") + h.id.substring(0, 7)
                    + " — " + (ePesan == null ? "" : ePesan.getText().toString().trim()));
            toast("Commit berhasil.");
            if (ePesan != null) ePesan.setText("");
        } else {
            tampil(h.galat);
            toast(h.galat);
        }
    }

    private void lihatLog() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        if (!GitLokal.adaGit(dir)) {
            tampil(TeksUI.t("Proyek ini belum punya repo Git. Tekan \"Buat Repo Git\" dulu."));
            return;
        }
        List<GitLokal.Commit> daftar = GitLokal.log(dir);
        if (daftar.isEmpty()) {
            tampil(TeksUI.t("Belum ada commit di proyek ini."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Log commit ")).append(dir.getName()).append(" (")
                .append(daftar.size()).append("):\n");
        for (int i = 0; i < daftar.size(); i++) {
            GitLokal.Commit c = daftar.get(i);
            sb.append(c.idPendek()).append(" — ").append(c.tanggal())
                    .append(" — ").append(c.pesan)
                    .append(" (").append(c.berkas).append(TeksUI.t(" berkas)") + "\n");
        }
        tampil(sb.toString());
    }

    private void pushGithub() {
        final File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        final String token = GithubSettings.bacaToken(this);
        if (token.isEmpty()) {
            toast("Token GitHub belum diisi. Buka Pengaturan GitHub di menu opsi.");
            GithubSettings.tampilkan(this, null);
            return;
        }
        tampil(TeksUI.t("Mengunggah snapshot ke GitHub..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String pemilik = SinkronCloud.namaPengguna(token,
                        SinkronCloud.API_GITHUB, new SinkronCloud.PengirimHttpBawaan());
                final String galat = GitLokal.pushGithub(token, SinkronCloud.API_GITHUB,
                        pemilik, SinkronCloud.REPO_CADANGAN, dir,
                        new SinkronCloud.PengirimHttpBawaan());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (galat == null) {
                            tampil(TeksUI.t("Push ke GitHub berhasil (snapshot commit terakhir)."));
                            toast("Push berhasil.");
                        } else {
                            tampil(galat);
                            toast(galat);
                        }
                    }
                });
            }
        }).start();
    }

    private void pullGithub() {
        final File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        final String token = GithubSettings.bacaToken(this);
        if (token.isEmpty()) {
            toast("Token GitHub belum diisi. Buka Pengaturan GitHub di menu opsi.");
            GithubSettings.tampilkan(this, null);
            return;
        }
        tampil(TeksUI.t("Mengunduh snapshot dari GitHub..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String pemilik = SinkronCloud.namaPengguna(token,
                        SinkronCloud.API_GITHUB, new SinkronCloud.PengirimHttpBawaan());
                final String galat = GitLokal.pullGithub(token, SinkronCloud.API_GITHUB,
                        pemilik, SinkronCloud.REPO_CADANGAN, dir,
                        new SinkronCloud.PengirimHttpBawaan(),
                        new File(getCacheDir(), "git-pull"));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (galat == null) {
                            tampil(TeksUI.t("Pull dari GitHub berhasil. Berkas proyek sudah diperbarui."));
                            toast("Pull berhasil.");
                        } else {
                            tampil(galat);
                            toast(galat);
                        }
                    }
                });
            }
        }).start();
    }

    private void pushSistem() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        tampil(TeksUI.t("Menjalankan git push..."));
        final File d = dir;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String galat = GitLokal.pushSistem(d);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tampil(galat == null ? TeksUI.t("git push berhasil.") : galat);
                    }
                });
            }
        }).start();
    }

    private void pullSistem() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        tampil(TeksUI.t("Menjalankan git pull..."));
        final File d = dir;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String galat = GitLokal.pullSistem(d);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tampil(galat == null ? TeksUI.t("git pull berhasil.") : galat);
                    }
                });
            }
        }).start();
    }

    private void buatClone() {
        String url = eUrlClone == null ? "" : eUrlClone.getText().toString().trim();
        String nama = eNamaClone == null ? "" : eNamaClone.getText().toString().trim();
        if (url.isEmpty() || nama.isEmpty()) {
            toast("Isi dulu URL repo dan nama folder hasil clone.");
            return;
        }
        tampil(TeksUI.t("Meng-clone ") + url + "...");
        final String u = url;
        final String n = nama;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String galat = GitLokal.clone(u, new File(ROOT), n);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (galat == null) {
                            tampil(TeksUI.t("Clone berhasil ke folder ") + n
                                    + TeksUI.t(". Buka di Kelola Project."));
                            toast("Clone berhasil.");
                            muatDaftar();
                        } else {
                            tampil(galat);
                            toast(galat);
                        }
                    }
                });
            }
        }).start();
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

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(TeksUI.t("Pengaturan GitHub"));
        menu.add(TeksUI.t("Tentang"));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        String t = String.valueOf(item.getTitle());
        if ((TeksUI.t("Pengaturan GitHub").equals(t) || TeksUI.t("Pengaturan GitHub").equals(t))) {
            GithubSettings.tampilkan(this, null);
        } else if ((TeksUI.t("Tentang").equals(t) || TeksUI.t("Tentang").equals(t))) {
            startActivity(new Intent(this, AboutActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
