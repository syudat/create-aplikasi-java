package com.androlua;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
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
 * Pemantau Kinerja Ringan (fitur pilihan al kautsar dari daftar Android
 * Studio kedua, nomor 10).
 *
 * Memantau pemakaian RAM (PSS) dan CPU sebuah aplikasi yang sedang
 * berjalan — aplikasi hasil build yang sedang dicoba, atau aplikasi ini
 * sendiri — setiap 2 detik, lalu melaporkan ringkasannya sebagai teks.
 * Tanpa izin tambahan: RAM dibaca lewat ActivityManager, CPU lewat
 * /proc/<pid>/stat bila Android mengizinkan (di Android baru akses
 * /proc proses lain dibatasi, dan itu dilaporkan apa adanya).
 * Logika hitungnya ada di Kinerja (murni Java, sudah diuji di komputer).
 */
public class KinerjaActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final long JEDA_MS = 2000;

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText ePaket;
    private TextView tvHasil;
    private Button bMulai, bBerhenti;

    private final Handler handler = new Handler();
    private boolean memantau;
    private String paketAktif;
    private int pidAktif = -1;
    private Kinerja.Catatan catatan;
    private long jiffySebelum = -1;
    private long dindingSebelum = 0;

    private final Runnable putaran = new Runnable() {
        @Override
        public void run() {
            if (!memantau) return;
            ambilContoh();
            handler.postDelayed(this, JEDA_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Pemantau Kinerja Ringan"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pemantau Kinerja Ringan"), 20);
        label(r, TeksUI.t("Pantau pemakaian RAM dan CPU aplikasi yang sedang berjalan, ")
                + TeksUI.t("misalnya aplikasi hasil build yang sedang kamu coba. ")
                + TeksUI.t("Jalankan dulu aplikasinya, isi nama paketnya, lalu tekan ")
                + TeksUI.t("Mulai Pantau. Contoh diambil setiap 2 detik; tekan ")
                + TeksUI.t("Berhenti untuk melihat ringkasannya."), false);

        label(r, TeksUI.t("Pilih proyek (untuk mengisi nama paket):"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Isi Nama Paket dari Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isiPaketDariProyek();
            }
        });

        label(r, TeksUI.t("Nama paket yang dipantau:"), false);
        ePaket = new EditText(this);
        ePaket.setHint("contoh: com.contoh.aplikasi");
        ePaket.setSingleLine(true);
        ePaket.setText(getPackageName());
        r.addView(ePaket, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bMulai = button(r, TeksUI.t("Mulai Pantau"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });
        bBerhenti = button(r, TeksUI.t("Berhenti"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                berhenti(true);
            }
        });
        bBerhenti.setEnabled(false);

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
        announce(TeksUI.t("Pemantau Kinerja Ringan. Isi nama paket aplikasi yang sedang berjalan, lalu tekan Mulai Pantau."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        muatDaftar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        berhenti(false);
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    private void isiPaketDariProyek() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        String paket = InfoProyek.namaPaket(dir);
        if (paket == null || paket.length() == 0) {
            toast("Nama paket proyek tidak terbaca.");
            announce(TeksUI.t("Nama paket proyek tidak terbaca."));
            return;
        }
        ePaket.setText(paket);
        announce(TeksUI.t("Nama paket diisi: ") + paket);
    }

    private void mulai() {
        String paket = ePaket.getText().toString().trim();
        if (paket.length() == 0) {
            toast("Isi dulu nama paketnya.");
            return;
        }
        int pid = cariPid(paket);
        if (pid < 0) {
            tvHasil.setText(TeksUI.t("Proses ") + paket + TeksUI.t(" tidak ditemukan. Jalankan dulu aplikasinya, lalu coba lagi."));
            announce(TeksUI.t("Proses tidak ditemukan. Jalankan dulu aplikasinya."));
            return;
        }
        paketAktif = paket;
        pidAktif = pid;
        catatan = new Kinerja.Catatan();
        jiffySebelum = -1;
        memantau = true;
        bMulai.setEnabled(false);
        bBerhenti.setEnabled(true);
        announce(TeksUI.t("Mulai memantau ") + paket + ".");
        ambilContoh();
        handler.postDelayed(putaran, JEDA_MS);
    }

    private void berhenti(boolean tampilkan) {
        if (!memantau) return;
        memantau = false;
        handler.removeCallbacks(putaran);
        bMulai.setEnabled(true);
        bBerhenti.setEnabled(false);
        if (tampilkan && catatan != null) {
            String ringkasan = Kinerja.laporanRingkasan(paketAktif, catatan);
            tvHasil.setText(ringkasan);
            announce(TeksUI.t("Pemantauan berhenti. ") + catatan.contoh + TeksUI.t(" contoh tercatat."));
        }
    }

    private void ambilContoh() {
        int pid = cariPid(paketAktif);
        if (pid < 0) {
            tvHasil.setText(TeksUI.t("Proses ") + paketAktif + TeksUI.t(" tidak lagi berjalan. Pemantauan berhenti.") + "\n" + "\n"
                    + Kinerja.laporanRingkasan(paketAktif, catatan));
            announce(TeksUI.t("Proses berhenti. Pemantauan selesai."));
            berhenti(false);
            bMulai.setEnabled(true);
            bBerhenti.setEnabled(false);
            return;
        }
        if (pid != pidAktif) {
            pidAktif = pid;
            jiffySebelum = -1;
        }
        long pss = -1;
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(new int[]{pid});
            if (mi != null && mi.length > 0) pss = mi[0].getTotalPss();
        } catch (Throwable ignored) {
        }
        double cpu = -1;
        long jiffy = bacaJiffy(pid);
        long sekarang = System.currentTimeMillis();
        if (jiffy >= 0 && jiffySebelum >= 0) {
            cpu = Kinerja.persenCpu(jiffySebelum, jiffy, dindingSebelum, sekarang,
                    hz(), Runtime.getRuntime().availableProcessors());
        }
        if (jiffy >= 0) {
            jiffySebelum = jiffy;
            dindingSebelum = sekarang;
        }
        catatan.tambah(pss, cpu);
        long totalKB = 0, bebasKB = 0;
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            totalKB = info.totalMem / 1024;
            bebasKB = info.availMem / 1024;
        } catch (Throwable ignored) {
        }
        tvHasil.setText(Kinerja.laporanContoh(paketAktif, pid, catatan, totalKB, bebasKB));
    }

    /** Mencari pid proses sebuah paket tanpa izin tambahan. */
    private int cariPid(String paket) {
        if (paket.equals(getPackageName())) return Process.myPid();
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> daftar = am.getRunningAppProcesses();
            if (daftar != null) {
                for (ActivityManager.RunningAppProcessInfo p : daftar) {
                    if (p.processName != null
                            && (p.processName.equals(paket) || p.processName.startsWith(paket + ":"))) {
                        return p.pid;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            File proc = new File("/proc");
            File[] anak = proc.listFiles();
            if (anak != null) {
                for (File d : anak) {
                    if (!d.isDirectory()) continue;
                    String nama = d.getName();
                    if (nama.length() == 0 || !Character.isDigit(nama.charAt(0))) continue;
                    String cmd = SumberDaya.teks(new File(d, "cmdline"));
                    if (cmd == null) continue;
                    int nol = cmd.indexOf('\0');
                    String pertama = (nol >= 0 ? cmd.substring(0, nol) : cmd).trim();
                    if (pertama.equals(paket) || pertama.startsWith(paket + ":")) {
                        return Integer.parseInt(nama);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /** Membaca utime+stime proses dari /proc/<pid>/stat; -1 bila tidak bisa. */
    private long bacaJiffy(int pid) {
        String isi = SumberDaya.teks(new File("/proc/" + pid + "/stat"));
        long[] us = Kinerja.uraiStat(isi);
        return us == null ? -1 : us[0] + us[1];
    }

    private int hz() {
        try {
            long v = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK);
            if (v > 0 && v <= 10000) return (int) v;
        } catch (Throwable ignored) {
        }
        return 100;
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
