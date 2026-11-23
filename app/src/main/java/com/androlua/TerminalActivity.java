package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import novan.terminal.Env;

/**
 * Terminal di dalam Create Aplikasi (Tahap 1-4).
 *
 * Tahap 2: keluaran dirender lewat parser VT (kelas Screen,
 * grid 24x80) sehingga program interaktif (nano/vim/top/htop) tampil
 * benar — bukan lagi teks mentah yang dibersihkan dari escape.
 * Tahap 3: biner di folder aplikasi bisa dieksekusi lewat LD_PRELOAD
 * libkautsarexec.so (diatur Env.vars di dalam Pty.open); tombol
 * "Uji Exec" menjalankan Env.selfTest untuk membuktikannya.
 * Tahap 4: shell hidup di TerminalService (foreground) sehingga tetap
 * berjalan saat layar ini ditutup; ada riwayat perintah (tombol
 * Riwayat + panah atas/bawah), perintah favorit bernama (tombol
 * Favorit: simpan dengan nama, ketuk untuk langsung menjalankan,
 * hapus dengan konfirmasi, tersimpan permanen di SharedPreferences
 * "terminal_favorit"), dan tombol khusus (Tab, panah, Esc) untuk
 * program interaktif. "Bangun di Terminal" membuka layar ini dengan
 * perintah bangun proyek yang sudah diisi.
 *
 * Terminal Kautsar: bila lingkungan Termux belum dipasang, tombol
 * "Pasang Termux" mengunduh bootstrap Termux resmi (per ABI) dan
 * mengekstraknya ke prefix aplikasi, sehingga perintah "pkg update"
 * dan "pkg install" benar-benar jalan (BootstrapTermux). Tombol
 * "Pasang Alat Terminal" (uji coba) melakukan hal yang sama dari
 * cermin di repositori alat-termux milik pengembang, ditambah alat
 * skrip tambahan dari indeks repositori itu.
 *
 * Aksesibilitas: keluaran adalah teks di TextView yang bisa dibaca
 * Jieshuo; tombol "Bacakan layar" membacakan baris kursor + baris
 * terakhir; semua tombol punya contentDescription.
 */
public class TerminalActivity extends Activity implements TerminalService.PendengarLayar {
    /** Folder kerja shell (bila tidak diisi: /sdcard/Download/project). */
    public static final String EXTRA_FOLDER = "folder";
    /** Perintah yang diisi ke kolom input saat dibuka. */
    public static final String EXTRA_PERINTAH = "perintah";
    /** true = jalankan EXTRA_PERINTAH otomatis setelah shell siap. */
    public static final String EXTRA_JALANKAN = "jalankan";

    private TextView outputView;
    private ScrollView outputScroll;
    private EditText inputView;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TerminalService.Sesi sesi;
    private String cwd;
    private List<String> riwayat = new ArrayList<>();
    private int posRiwayat = -1;
    private String teksDiketik = "";
    private List<PerintahFavorit.Favorit> favorit = new ArrayList<>();
    /** Dialog kemajuan saat memasang lingkungan Termux. */
    private AlertDialog dialogPasang;
    /** true bila pemasangan Termux sedang berjalan. */
    private boolean sedangPasang = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Terminal"));

        Intent it = getIntent();
        String folderExtra = it != null ? it.getStringExtra(EXTRA_FOLDER) : null;
        final String perintahExtra = it != null ? it.getStringExtra(EXTRA_PERINTAH) : null;
        final boolean jalankanExtra = it != null && it.getBooleanExtra(EXTRA_JALANKAN, false);

        cwd = folderExtra;
        if (cwd == null || cwd.isEmpty()) {
            cwd = "/sdcard/Download/project";
            if (!new File(cwd).isDirectory()) {
                try {
                    cwd = Env.home.getPath();
                } catch (Throwable t) {
                    cwd = getFilesDir().getAbsolutePath();
                }
            }
        }

        LinearLayout root = new LinearLayout(this);
        GayaStudio.latar(root);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        outputView = new TextView(this);
        outputView.setTypeface(android.graphics.Typeface.MONOSPACE);
        GayaStudio.terminal(outputView);
        outputView.setTextSize(14);
        outputView.setTextIsSelectable(true);
        outputView.setMovementMethod(new ScrollingMovementMethod());
        outputView.setContentDescription(TeksUI.t("Keluaran terminal"));
        outputScroll = new ScrollView(this);
        outputScroll.addView(outputView);
        LinearLayout.LayoutParams outputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(outputScroll, outputParams);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        inputView = new EditText(this);

        GayaStudio.terminalEdit(inputView);
        inputView.setHint(TeksUI.t("Ketik perintah, mis. ls"));
        inputView.setInputType(InputType.TYPE_CLASS_TEXT);
        inputView.setSingleLine(true);
        inputView.setContentDescription(TeksUI.t("Kolom perintah terminal"));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputRow.addView(inputView, inputParams);

        Button runButton = new Button(this);
        GayaStudio.tombol(runButton);
        runButton.setText(TeksUI.t("Jalankan"));
        runButton.setContentDescription(TeksUI.t("Jalankan perintah"));
        runButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitCommand();
            }
        });
        inputRow.addView(runButton);
        root.addView(inputRow);

        root.addView(buatBarisTombol(new String[][]{
                {TeksUI.t("Berhenti"), TeksUI.t("Berhenti, kirim Ctrl+C")},
                {TeksUI.t("Bersihkan"), TeksUI.t("Bersihkan layar terminal")},
                {TeksUI.t("Bacakan layar"), TeksUI.t("Bacakan layar terminal")},
        }, new View.OnClickListener[]{
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendCtrlC();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (sesi != null) sesi.bersihkanLayar();
                        announce(TeksUI.t("Layar dibersihkan."));
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        readAloud();
                    }
                },
        }));

        root.addView(buatBarisTombol(new String[][]{
                {TeksUI.t("Riwayat"), TeksUI.t("Lihat riwayat perintah")},
                {TeksUI.t("Favorit"), TeksUI.t("Lihat perintah favorit")},
                {TeksUI.t("Uji Exec"), TeksUI.t("Uji exec biner folder aplikasi")},
                {TeksUI.t("Tutup shell"), TeksUI.t("Matikan shell dan tutup terminal")},
        }, new View.OnClickListener[]{
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tampilkanRiwayat();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tampilkanFavorit();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ujiExec();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tutupShell();
                    }
                },
        }));

        root.addView(buatBarisTombol(new String[][]{
                {"Tab", TeksUI.t("Kirim tombol Tab")},
                {TeksUI.t("Atas"), TeksUI.t("Kirim panah atas")},
                {TeksUI.t("Bawah"), TeksUI.t("Kirim panah bawah")},
                {"Esc", TeksUI.t("Kirim tombol Escape")},
        }, new View.OnClickListener[]{
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        kirimTombol("\t");
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        kirimTombol("\u001B[A");
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        kirimTombol("\u001B[B");
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        kirimTombol("\u001B");
                    }
                },
        }));

        root.addView(buatBarisTombol(new String[][]{
                {TeksUI.t("Pasang Termux"), TeksUI.t("Pasang lingkungan Termux supaya pkg bisa dipakai")},
                {TeksUI.t("Pasang Alat Terminal"), TeksUI.t("Uji coba: pasang Termux dan alat dari repositori al kautsar")},
                {TeksUI.t("Buka di Termux"), TeksUI.t("Buka aplikasi Termux asli bila sudah terpasang")},
        }, new View.OnClickListener[]{
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tawaranPasangTermux();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tawaranPasangAlatTerminal();
                    }
                },
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        bukaDiTermux();
                    }
                },
        }));

        setContentView(root);

        inputView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submitCommand();
                return true;
            }
        });
        inputView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    riwayatNaik();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    riwayatTurun();
                    return true;
                }
                return false;
            }
        });

        riwayat = RiwayatTerminal.muat(berkasRiwayat());
        posRiwayat = riwayat.size();
        muatFavorit();

        sesi = TerminalService.ambilAtauBuat(this, cwd);
        if (sesi.isiLayar().isEmpty()) {
            StringBuilder sambutan = new StringBuilder();
            sambutan.append(TeksUI.t("Terminal Kautsar siap. Folder kerja: ")).append(cwd).append("\r\n");
            sambutan.append(TeksUI.t("Exec biner: "));
            try {
                sambutan.append(sesi.adaKautsarExec(this)
                        ? TeksUI.t("aktif (libkautsarexec.so ditemukan).")
                        : TeksUI.t("belum aktif (libkautsarexec.so tidak ditemukan)."));
            } catch (Throwable t) {
                sambutan.append(TeksUI.t("tidak diketahui."));
            }
            sambutan.append("\r" + "\n" + TeksUI.t("Lingkungan Termux: "));
            try {
                sambutan.append(BootstrapTermux.sudahTerpasang(this)
                        ? TeksUI.t("terpasang (") + BootstrapTermux.VERSI_BOOTSTRAP + TeksUI.t("). Ketik \"pkg update\" untuk mencoba.")
                        : TeksUI.t("belum dipasang. Tekan \"Pasang Termux\" supaya perintah pkg bisa dipakai."));
            } catch (Throwable t) {
                sambutan.append(TeksUI.t("tidak diketahui."));
            }
            sambutan.append("\r" + "\n" + TeksUI.t("Ketik perintah lalu tekan Jalankan. Contoh: ls") + "\r" + "\n");
            sambutan.append(TeksUI.t("Shell tetap berjalan walau layar ini ditutup. Tekan Tutup shell untuk mematikannya.") + "\r" + "\n");
            sesi.layar.write(sambutan.toString());
        }
        segarkanLayar();

        if (!BootstrapTermux.sudahTerpasang(this)) {
            tawaranPasangTermux();
        }

        if (perintahExtra != null && !perintahExtra.isEmpty()) {
            inputView.setText(perintahExtra);
            if (jalankanExtra) {
                cobaJalankan(perintahExtra, 0);
            }
        }
        announce(TeksUI.t("Terminal dibuka. Ketik perintah lalu tekan Jalankan."));
    }

    private LinearLayout buatBarisTombol(String[][] label, View.OnClickListener[] aksi) {
        LinearLayout baris = new LinearLayout(this);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < label.length; i++) {
            Button b = new Button(this);
            GayaStudio.tombol(b);
            b.setText(label[i][0]);
            b.setContentDescription(label[i][1]);
            b.setOnClickListener(aksi[i]);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            baris.addView(b, p);
        }
        return baris;
    }

    private File berkasRiwayat() {
        return new File(getFilesDir(), RiwayatTerminal.NAMA_RIWAYAT);
    }

    private File berkasFavorit() {
        return new File(getFilesDir(), RiwayatTerminal.NAMA_FAVORIT);
    }

    /**
     * Muat favorit dari SharedPreferences "terminal_favorit".
     * Bila belum pernah disimpan: isi 3 favorit bawaan, lalu impor
     * sekali favorit lama dari berkas terminal-favorit.txt (nama =
     * perintahnya) supaya favorit lama tidak hilang.
     */
    private void muatFavorit() {
        SharedPreferences pref = getSharedPreferences(
                PerintahFavorit.PREFS, MODE_PRIVATE);
        String tersimpan = pref.getString(PerintahFavorit.KUNCI_DAFTAR, null);
        if (tersimpan == null) {
            favorit = PerintahFavorit.bawaan();
            if (!pref.getBoolean(PerintahFavorit.KUNCI_MIGRASI, false)) {
                List<String> lama = RiwayatTerminal.muat(berkasFavorit());
                for (String cmd : lama) {
                    if (PerintahFavorit.cari(favorit, cmd) == null) {
                        PerintahFavorit.tambah(favorit, cmd, cmd);
                    }
                }
            }
            simpanFavorit();
        } else {
            favorit = PerintahFavorit.decode(tersimpan);
        }
    }

    private void simpanFavorit() {
        getSharedPreferences(PerintahFavorit.PREFS, MODE_PRIVATE).edit()
                .putString(PerintahFavorit.KUNCI_DAFTAR, PerintahFavorit.encode(favorit))
                .putBoolean(PerintahFavorit.KUNCI_MIGRASI, true)
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sesi != null) {
            sesi.setPendengar(this);
            segarkanLayar();
        }
    }

    @Override
    protected void onPause() {
        if (sesi != null) sesi.setPendengar(null);
        super.onPause();
    }

    @Override
    public void layarBerubah() {
        segarkanLayar();
    }

    /** Tampilkan isi layar grid VT di TextView. */
    private void segarkanLayar() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sesi == null) return;
                ArrayList<String> baris = sesi.isiLayar();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < baris.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(baris.get(i));
                }
                outputView.setText(sb.toString());
                outputScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        outputScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void submitCommand() {
        String cmd = inputView.getText().toString().trim();
        if (cmd.isEmpty()) {
            announce(TeksUI.t("Ketik perintah dulu."));
            return;
        }
        TerminalService.Sesi s = sesi;
        if (s == null || !s.masihJalan()) {
            announce(TeksUI.t("Shell belum siap atau sudah selesai. Tutup lalu buka lagi Terminal."));
            return;
        }
        s.kirim(cmd + "\n");
        riwayat = RiwayatTerminal.tambah(berkasRiwayat(), cmd);
        posRiwayat = riwayat.size();
        teksDiketik = "";
        inputView.setText("");
    }

    /** Jalankan perintah otomatis (dari "Bangun di Terminal"), tunggu shell siap. */
    private void cobaJalankan(final String perintah, final int percobaan) {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TerminalService.Sesi s = sesi;
                if (s != null && s.masihJalan()) {
                    inputView.setText(perintah);
                    submitCommand();
                } else if (percobaan < 5) {
                    cobaJalankan(perintah, percobaan + 1);
                } else {
                    announce(TeksUI.t("Shell belum siap. Perintah sudah diisi, tekan Jalankan."));
                }
            }
        }, 600);
    }

    private void sendCtrlC() {
        TerminalService.Sesi s = sesi;
        if (s == null || !s.masihJalan()) {
            announce(TeksUI.t("Tidak ada perintah yang berjalan."));
            return;
        }
        s.kirim("\u0003");
        announce(TeksUI.t("Ctrl+C dikirim, perintah dihentikan."));
    }

    private void kirimTombol(String urutan) {
        TerminalService.Sesi s = sesi;
        if (s == null || !s.masihJalan()) {
            announce(TeksUI.t("Shell belum siap."));
            return;
        }
        s.kirim(urutan);
    }

    private void readAloud() {
        TerminalService.Sesi s = sesi;
        if (s == null) {
            announce(TeksUI.t("Belum ada hasil untuk dibacakan."));
            return;
        }
        ArrayList<String> baris = s.isiLayar();
        if (baris.isEmpty()) {
            announce(TeksUI.t("Layar masih kosong."));
            return;
        }
        int mulai = Math.max(0, baris.size() - 10);
        StringBuilder sb = new StringBuilder();
        for (int i = mulai; i < baris.size(); i++) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(baris.get(i));
        }
        String teks = sb.toString().trim();
        if (teks.isEmpty()) {
            announce(TeksUI.t("Layar masih kosong."));
            return;
        }
        if (teks.length() > 1500) teks = teks.substring(teks.length() - 1500);
        announce(teks);
    }

    private void riwayatNaik() {
        if (riwayat.isEmpty()) {
            announce(TeksUI.t("Riwayat masih kosong."));
            return;
        }
        if (posRiwayat == riwayat.size()) teksDiketik = inputView.getText().toString();
        if (posRiwayat > 0) posRiwayat--;
        inputView.setText(riwayat.get(posRiwayat));
        inputView.setSelection(inputView.getText().length());
    }

    private void riwayatTurun() {
        if (riwayat.isEmpty()) return;
        if (posRiwayat < riwayat.size()) posRiwayat++;
        if (posRiwayat >= riwayat.size()) {
            posRiwayat = riwayat.size();
            inputView.setText(teksDiketik);
        } else {
            inputView.setText(riwayat.get(posRiwayat));
        }
        inputView.setSelection(inputView.getText().length());
    }

    private void tampilkanRiwayat() {
        if (riwayat.isEmpty()) {
            announce(TeksUI.t("Riwayat masih kosong. Jalankan perintah dulu."));
            Toast.makeText(this, TeksUI.t("Riwayat masih kosong."), Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] item = new String[riwayat.size()];
        for (int i = 0; i < riwayat.size(); i++) {
            item[i] = riwayat.get(riwayat.size() - 1 - i);
        }
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Riwayat perintah"))
                .setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        inputView.setText(item[which]);
                        inputView.setSelection(inputView.getText().length());
                        announce(TeksUI.t("Perintah dipilih: ") + item[which]);
                    }
                })
                .setNegativeButton(TeksUI.t("Tutup"), null)
                .show();
    }

    /**
     * Dialog perintah favorit: ketuk satu favorit untuk LANGSUNG
     * menjalankannya; "Tambah favorit baru…" untuk menyimpan;
     * "Hapus favorit…" untuk menghapus (dengan konfirmasi).
     */
    private void tampilkanFavorit() {
        final List<PerintahFavorit.Favorit> daftar = favorit;
        final String[] item = new String[daftar.size() + 2];
        for (int i = 0; i < daftar.size(); i++) {
            item[i] = daftar.get(i).nama + " — " + daftar.get(i).perintah;
        }
        item[daftar.size()] = TeksUI.t("Tambah favorit baru…");
        item[daftar.size() + 1] = TeksUI.t("Hapus favorit…");
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Perintah favorit"))
                .setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == daftar.size()) {
                            dialogTambahFavorit();
                        } else if (which == daftar.size() + 1) {
                            dialogHapusFavorit();
                        } else {
                            jalankanFavorit(daftar.get(which));
                        }
                    }
                })
                .setNegativeButton(TeksUI.t("Tutup"), null)
                .show();
    }

    /** Dialog tambah favorit: nama + perintah (perintah diisi dari kolom input). */
    private void dialogTambahFavorit() {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        final EditText namaView = new EditText(this);
        namaView.setHint("Nama favorit, mis. Lihat berkas");
        lay.addView(namaView);
        final EditText cmdView = new EditText(this);
        cmdView.setHint(TeksUI.t("Perintah, mis. ls -la"));
        cmdView.setText(inputView.getText().toString().trim());
        lay.addView(cmdView);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Tambah favorit baru"))
                .setView(lay)
                .setPositiveButton(TeksUI.t("Simpan"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String nama = namaView.getText().toString();
                        String cmd = cmdView.getText().toString();
                        if (PerintahFavorit.bersihkan(nama).isEmpty()) {
                            Toast.makeText(TerminalActivity.this,
                                    TeksUI.t("Nama favorit masih kosong."), Toast.LENGTH_SHORT).show();
                            announce(TeksUI.t("Nama favorit masih kosong. Isi nama dulu."));
                            return;
                        }
                        if (PerintahFavorit.bersihkan(cmd).isEmpty()) {
                            Toast.makeText(TerminalActivity.this,
                                    TeksUI.t("Perintah masih kosong."), Toast.LENGTH_SHORT).show();
                            announce(TeksUI.t("Perintah masih kosong. Isi perintah dulu."));
                            return;
                        }
                        if (!PerintahFavorit.tambah(favorit, nama, cmd)) {
                            Toast.makeText(TerminalActivity.this,
                                    TeksUI.t("Favorit sudah 50, hapus dulu yang tidak dipakai."),
                                    Toast.LENGTH_SHORT).show();
                            announce(TeksUI.t("Favorit sudah 50, hapus dulu yang tidak dipakai."));
                            return;
                        }
                        simpanFavorit();
                        announce(TeksUI.t("Favorit disimpan: ") + PerintahFavorit.bersihkan(nama));
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    /** Dialog hapus favorit: ketuk satu favorit, lalu konfirmasi. */
    private void dialogHapusFavorit() {
        if (favorit.isEmpty()) {
            Toast.makeText(this, TeksUI.t("Belum ada favorit untuk dihapus."), Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Belum ada favorit untuk dihapus."));
            return;
        }
        final String[] item = new String[favorit.size()];
        for (int i = 0; i < favorit.size(); i++) {
            item[i] = favorit.get(i).nama + " — " + favorit.get(i).perintah;
        }
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Hapus favorit"))
                .setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        final PerintahFavorit.Favorit f = favorit.get(which);
                        new AlertDialog.Builder(TerminalActivity.this)
                                .setTitle(TeksUI.t("Hapus favorit?"))
                                .setMessage(TeksUI.t("Hapus favorit \"") + f.nama
                                        + "\" (" + f.perintah + ")?")
                                .setPositiveButton(TeksUI.t("Ya, hapus"),
                                        new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int which2) {
                                        PerintahFavorit.hapus(favorit, f.nama);
                                        simpanFavorit();
                                        announce(TeksUI.t("Favorit dihapus: ") + f.nama);
                                    }
                                })
                                .setNegativeButton(TeksUI.t("Batal"), null)
                                .show();
                    }
                })
                .setNegativeButton(TeksUI.t("Tutup"), null)
                .show();
    }

    /** Jalankan perintah favorit langsung di shell (tanpa mengisi kolom dulu). */
    private void jalankanFavorit(PerintahFavorit.Favorit f) {
        String cmd = f == null ? "" : PerintahFavorit.bersihkan(f.perintah);
        if (cmd.isEmpty()) {
            Toast.makeText(this, TeksUI.t("Perintah favorit kosong, tidak bisa dijalankan."),
                    Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Perintah favorit kosong, tidak bisa dijalankan."));
            return;
        }
        TerminalService.Sesi s = sesi;
        if (s == null || !s.masihJalan()) {
            Toast.makeText(this, TeksUI.t("Shell belum siap atau sudah selesai."),
                    Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Shell belum siap atau sudah selesai. Tutup lalu buka lagi Terminal."));
            return;
        }
        s.kirim(cmd + "\n");
        riwayat = RiwayatTerminal.tambah(berkasRiwayat(), cmd);
        posRiwayat = riwayat.size();
        announce(TeksUI.t("Menjalankan favorit: ") + f.nama);
    }

    /** Uji jalan tidaknya exec biner (Env.selfTest) di utas latar. */
    private void ujiExec() {
        announce(TeksUI.t("Menguji exec biner, tunggu sebentar."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil;
                try {
                    hasil = sesi == null ? TeksUI.t("Sesi belum siap.") : sesi.ujiExec(TerminalActivity.this);
                } catch (Throwable t) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(TerminalActivity.this,
                                    TeksUI.t("Uji exec gagal: ") + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                final String teksHasil = hasil;
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(TerminalActivity.this)
                                .setTitle(TeksUI.t("Uji Exec"))
                                .setMessage(teksHasil)
                                .setPositiveButton(TeksUI.t("Tutup"), null)
                                .show();
                        announce(teksHasil);
                    }
                });
            }
        }).start();
    }

    /**
     * Tawarkan pemasangan lingkungan Termux bila belum terpasang.
     * Unduhan on-demand (~33 MB, butuh internet) dari rilis resmi
     * Termux; setelah terpasang, "pkg update" dan "pkg install"
     * bisa jalan di Terminal ini.
     */
    private void tawaranPasangTermux() {
        if (BootstrapTermux.sudahTerpasang(this)) {
            announce(TeksUI.t("Lingkungan Termux sudah terpasang. Ketik pkg update untuk mencoba."));
            Toast.makeText(this, TeksUI.t("Lingkungan Termux sudah terpasang."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (sedangPasang) {
            announce(TeksUI.t("Pemasangan Termux sedang berjalan, tunggu sebentar."));
            return;
        }
        final String arch = BootstrapTermux.arsitekturBootstrap();
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pasang lingkungan Termux?"))
                .setMessage(TeksUI.t("Terminal ini belum punya lingkungan Termux, jadi perintah ")
                        + TeksUI.t("\"pkg update\" dan memasang paket belum bisa. Pasang sekarang? ")
                        + TeksUI.t("Unduhan sekitar ") + BootstrapTermux.ukuranUnduhan(arch)
                        + TeksUI.t(" dari rilis resmi Termux (") + BootstrapTermux.VERSI_BOOTSTRAP + "), "
                        + TeksUI.t("butuh internet. Setelah terpasang, shell memakai bash dan perintah pkg bisa dipakai."))
                .setPositiveButton(TeksUI.t("Pasang sekarang"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        mulaiPasangTermux();
                    }
                })
                .setNegativeButton(TeksUI.t("Nanti saja"), null)
                .show();
    }

    /** Pasang lingkungan Termux di utas latar dengan dialog kemajuan. */
    private void mulaiPasangTermux() {
        if (sedangPasang) return;
        sedangPasang = true;
        announce(TeksUI.t("Memasang lingkungan Termux, tunggu sebentar."));
        dialogPasang = new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Memasang Termux"))
                .setMessage(TeksUI.t("Mengunduh lingkungan Termux…"))
                .setCancelable(false)
                .create();
        dialogPasang.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                BootstrapTermux.pasang(TerminalActivity.this, new BootstrapTermux.Kemajuan() {
                    @Override
                    public void onKemajuan(final String tahap, final int persen) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (dialogPasang != null) {
                                    String teks = tahap;
                                    if (persen >= 0) teks += " (" + persen + "%)";
                                    dialogPasang.setMessage(teks);
                                }
                            }
                        });
                    }

                    @Override
                    public void onSelesai(final boolean ok, final String pesan) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                sedangPasang = false;
                                if (dialogPasang != null) {
                                    dialogPasang.dismiss();
                                    dialogPasang = null;
                                }
                                Toast.makeText(TerminalActivity.this, pesan, Toast.LENGTH_LONG).show();
                                announce(pesan);
                                if (ok) {
                                    mulaiUlangShell();
                                }
                            }
                        });
                    }
                });
            }
        }).start();
    }

    /**
     * Pasang Alat Terminal (UJI COBA, perintah al kautsar 2026-11-23):
     * memasang lingkungan Termux dari cermin di repositori alat-termux
     * milik pengembang, lalu alat skrip tambahan dari indeks repositori.
     * Bila lingkungan sudah terpasang, hanya alat tambahannya dipasang.
     */
    private void tawaranPasangAlatTerminal() {
        if (sedangPasang) {
            announce(TeksUI.t("Pemasangan sedang berjalan, tunggu sebentar."));
            return;
        }
        final String arch = BootstrapTermux.arsitekturBootstrap();
        boolean sudah = BootstrapTermux.sudahTerpasang(this);
        String paket = TeksUI.t(" Sesudah itu aplikasi memasang otomatis paket Linux dasar ")
                + TeksUI.t("(git, python, curl, wget, nano, openssh, zip, unzip) dan alat bangun ")
                + TeksUI.t("APK beserta tanda tangan (openjdk-21, kotlin, gradle, ecj, aapt2, d8, ")
                + TeksUI.t("apksigner, clang, cmake, ninja, make). Unduhan paket besar (ratusan MB) ")
                + TeksUI.t("dan bisa lama. Selama pemasangan, kemajuan dan keluaran perintah tampil ")
                + TeksUI.t("di layar utama Terminal, dan hasil akhir dibacakan.");
        String isi = sudah
                ? TeksUI.t("Fitur uji coba: memasang alat tambahan dari repositori alat-termux ")
                  + TeksUI.t("milik pengembang ke terminal ini (info-terminal, bersih-terminal, ")
                  + TeksUI.t("cadangkan-proyek). Lingkungan Termux sudah terpasang, jadi hanya ")
                  + TeksUI.t("alatnya yang diunduh. Butuh internet.") + paket
                : TeksUI.t("Fitur uji coba: memasang lingkungan Termux dari cermin di repositori ")
                  + TeksUI.t("alat-termux milik pengembang (unduhan sekitar ")
                  + BootstrapTermux.ukuranUnduhan(arch) + TeksUI.t("), lalu alat tambahan dari ")
                  + TeksUI.t("repositori yang sama. Sesudah terpasang, perintah pkg bisa dipakai. ")
                  + TeksUI.t("Butuh internet.") + paket;
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pasang Alat Terminal (uji coba)?"))
                .setMessage(isi)
                .setPositiveButton(TeksUI.t("Pasang sekarang"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        mulaiPasangAlatTerminal();
                    }
                })
                .setNegativeButton(TeksUI.t("Nanti saja"), null)
                .show();
    }

    /** Tulis satu baris ke layar utama terminal (aman bila sesi belum ada). */
    private void tulisKeLayar(String teks) {
        try {
            if (sesi != null && sesi.layar != null) {
                sesi.layar.write("\r\n" + teks + "\r\n");
                segarkanLayar();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Jalankan Pasang Alat Terminal di utas latar dengan dialog kemajuan. */
    private void mulaiPasangAlatTerminal() {
        if (sedangPasang) return;
        sedangPasang = true;
        final boolean sudahAda = BootstrapTermux.sudahTerpasang(this);
        announce(TeksUI.t("Memasang alat terminal dari repositori, tunggu sebentar."));
        // Kemajuan juga tampil di layar utama terminal (perintah al
        // kautsar 2026-11-23), bukan hanya di kotak dialog.
        tulisKeLayar(TeksUI.t("=== Memasang Alat Terminal (uji coba) ==="));
        final int[] persenLayar = {-1};
        dialogPasang = new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Memasang Alat Terminal (uji coba)"))
                .setMessage(TeksUI.t("Mengunduh dari repositori…"))
                .setCancelable(false)
                .create();
        dialogPasang.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                BootstrapTermux.pasangDariRepo(TerminalActivity.this, new BootstrapTermux.Kemajuan() {
                    @Override
                    public void onKemajuan(final String tahap, final int persen) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (dialogPasang != null) {
                                    String teks = tahap;
                                    if (persen >= 0) teks += " (" + persen + "%)";
                                    dialogPasang.setMessage(teks);
                                }
                                // Cerminkan ke layar utama: baris tahap
                                // selalu; persen unduhan hanya tiap 25%
                                // supaya layar tidak kebanjiran.
                                if (persen < 0) {
                                    tulisKeLayar(tahap);
                                } else if (persen / 25 != persenLayar[0] / 25) {
                                    persenLayar[0] = persen;
                                    tulisKeLayar(tahap + " (" + persen + "%)");
                                }
                            }
                        });
                    }

                    @Override
                    public void onSelesai(final boolean ok, final String pesan) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                sedangPasang = false;
                                if (dialogPasang != null) {
                                    dialogPasang.dismiss();
                                    dialogPasang = null;
                                }
                                tulisKeLayar((ok ? TeksUI.t("=== Selesai: ") : TeksUI.t("=== Gagal: ")) + pesan);
                                Toast.makeText(TerminalActivity.this, pesan, Toast.LENGTH_LONG).show();
                                announce(pesan);
                                if (ok && !sudahAda) {
                                    mulaiUlangShell();
                                }
                            }
                        });
                    }
                });
            }
        }).start();
    }

    /** Matikan shell lama (sh sistem) lalu buka shell baru (bash Termux). */
    private void mulaiUlangShell() {
        try {
            TerminalService.tutupSesi(this);
        } catch (Throwable ignored) {
        }
        sesi = null;
        sesi = TerminalService.ambilAtauBuat(this, cwd);
        if (sesi != null) {
            sesi.layar.write("\r" + "\n" + TeksUI.t("Lingkungan Termux terpasang. Shell sekarang bash. ")
                    + TeksUI.t("Ketik \"pkg update\" untuk mencoba.") + "\r" + "\n");
        }
        segarkanLayar();
        announce(TeksUI.t("Shell dimulai ulang dengan bash Termux."));
    }

    /** Buka aplikasi Termux asli bila sudah terpasang di HP ini. */
    private void bukaDiTermux() {
        try {
            Intent it = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (it != null) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                announce(TeksUI.t("Membuka Termux."));
                return;
            }
        } catch (Throwable ignored) {
        }
        announce(TeksUI.t("Aplikasi Termux belum terpasang di HP ini. Tekan Pasang Termux untuk memakai pkg di Terminal ini."));
        Toast.makeText(this, TeksUI.t("Aplikasi Termux belum terpasang di HP ini."),
                Toast.LENGTH_LONG).show();
    }

    private void tutupShell() {
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Tutup shell?"))
                .setMessage(TeksUI.t("Shell akan dimatikan dan Terminal ditutup. ")
                        + TeksUI.t("Proses yang sedang berjalan ikut berhenti."))
                .setPositiveButton(TeksUI.t("Ya, tutup"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        TerminalService.tutupSesi(TerminalActivity.this);
                        sesi = null;
                        finish();
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        // Sengaja TIDAK mematikan shell di sini (KeepAlive, Tahap 4):
        // sesi hidup di TerminalService sampai tombol "Tutup shell".
        if (sesi != null) sesi.setPendengar(null);
        super.onDestroy();
    }
}
