package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

/**
 * Layar home Create Aplikasi dalam Java murni (Tahap 2A fondasi port Java).
 *
 * Activity ini adalah LAUNCHER aplikasi. Back di home keluar dari aplikasi
 * (finishAffinity). Ronde 3: mesin Lua + Welcome dihapus — tidak ada lagi
 * boot aset Lua atau host jembatan Lua; semua fitur di bawah ini Java murni.
 *
 * Menampilkan persis seperti showHome() di main.lua: judul aplikasi,
 * pemberitahuan izin di atas bila belum ada akses penyimpanan (dengan tombol
 * "Berikan Izin Penyimpanan"), lalu tombol Buat Project, Kelola Project,
 * Buat Keystore, Pembuat Ikon (Tahap 3A), Mode Suara,
 * Penerjemah Kode (Tahap 3H), Terminal, Tentang Alat.
 *
 * Delegasi: tombol Terminal langsung membuka TerminalActivity (Java murni),
 * tombol Buat Project membuka CreateProjectActivity (Java murni, Tahap 2B),
 * tombol Kelola Project membuka ProjectsActivity (Java murni, Tahap 2C),
 * tombol Buat Keystore membuka KeystoreActivity (Java murni, Tahap 2E),
 * tombol Tentang Alat membuka AboutActivity (Java murni, Tahap 2E).
 * tombol Pembuat Ikon membuka IconMakerActivity (Java murni, Tahap 3A).
 * tombol Bangun dengan Suara membuka VoiceActivity (Java murni, Tahap 3D),
 * tombol AI Pembuat Aplikasi membuka AiActivity (Java murni, Tahap 3D),
 * tombol Pasar Proyek membuka MarketActivity (Java murni, Tahap 3D),
 * tombol Kolaborasi membuka KolaborasiActivity (Java murni, Tahap 3D).
 * tombol Penerjemah Aplikasi membuka TerjemahActivity (Java murni, Tahap 3E),
 * tombol Pembuat Game membuka GameActivity (Java murni, Tahap 3E),
 * tombol Periksa Kode membuka PeriksaActivity (Java murni, Tahap 3E),
 * tombol Belajar membuka BelajarActivity (Java murni, Tahap 3E).
 * tombol Sinkronisasi Cloud membuka CloudActivity (Java murni, Tahap 3F),
 * tombol Pembuat Backend membuka BackendActivity (Java murni, Tahap 3F),
 * tombol Desainer Tema membuka TemaActivity (Java murni, Tahap 3F),
 * tombol Pembuat Animasi membuka AnimasiActivity (Java murni, Tahap 3F),
 * tombol Perekam Aksi membuka PerekamAksiActivity (Java murni, Tahap 3F).
 * tombol Keamanan Otomatis membuka KeamananActivity (Java murni, Tahap 3G),
 * tombol AI Offline membuka AiOfflineActivity (Java murni, Tahap 3G),
 * tombol Widget & Wallpaper membuka WidgetActivity (Java murni, Tahap 3G),
 * tombol Media Sosial membuka MediaSosialActivity (Java murni, Tahap 3G),
 * tombol Aplikasi dari Gambar membuka DariGambarActivity (Java murni, Tahap 3G).
 * tombol Mode Suara membuka ModeSuaraActivity (Java murni, Tahap 3H),
 * tombol Penerjemah Kode membuka PenerjemahKodeActivity (Java murni, Tahap 3H).
 * tombol Git di Dalam Aplikasi membuka GitActivity (Java murni, Tahap 3I),
 * tombol Pembuat Iklan membuka IklanActivity (Java murni, Tahap 3I),
 * tombol Mode Anak membuka ModeAnakActivity (Java murni, Tahap 3I),
 * tombol Wear OS & TV membuka WearTvActivity (Java murni, Tahap 3I),
 * tombol Peta Proyek membuka PetaActivity (Java murni, Tahap 3I),
 * tombol Kalkulator Ukuran APK membuka UkuranApkActivity (Java murni, Tahap 3I),
 * tombol Pembuat Video Demo membuka VideoDemoActivity (Java murni, Tahap 3I).
 *
 * Menu opsi (Tahap 2E, sama seperti onCreateOptionsMenu di main.lua untuk
 * layar home/projects): Buat proyek, Muat ulang, Pengaturan GitHub, Izin
 * penyimpanan, Izin pasang APK, Tentang. Pengaturan GitHub memakai dialog
 * GithubSettings (Java murni, berkas token yang sama dengan Lua);
 * Izin pasang APK memakai PasangHelper (Java murni).
 *
 * Izin penyimpanan diimplementasi murni di Java (tanpa Lua), mengikuti
 * hasAccess() / pesanIzinPenyimpanan() / requestAccess() di main.lua.
 * Ronde 3: mesin Lua DIHAPUS — libluajava.so dan runtime Lua tidak ada lagi;
 * proyek Lua lama dibuka lewat aplikasi Create Aplikasi Lua Manager.
 */
public class JavaHomeActivity extends Activity {
    private static final String APP_NAME = "Create Aplikasi by Al Kautsar";
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final int MINTA_IZIN = 1;

    private TextView noticeLabel;
    private Button noticeButton;
    /** Kode bahasa efektif layar ini (Tahap tampilan A, 2026-11-23). */
    private String kodeBahasa = "id";

    /** Teks antarmuka dalam bahasa efektif (TeksUI, fallback Indonesia). */
    private String t(String kunci) {
        return TeksUI.teks(kodeBahasa, kunci);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Terapkan tema aplikasi yang tersimpan (reorganisasi pengaturan 2026-11-17).
        PaletStudio.terapkanTema(PengaturanActivity.temaAplikasi(this));
        setTitle(APP_NAME);
        // Tahap tampilan A (2026-11-23): 46 teks menu utama mengikuti
        // bahasa efektif aplikasi (pilihan pengguna -> sistem -> id).
        kodeBahasa = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        TeksUI.kodeAktif = kodeBahasa;

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, APP_NAME, 22);
        // Nama pengembang di samping judul (perintah al kautsar 2026-11-23).
        label(r, "Developer by al kautsar", true);

        noticeLabel = label(r, t("Aplikasi perlu ") + pesanIzinPenyimpanan()
                + t(" untuk menyimpan project di folder Download/project."), false);
        noticeButton = button(r, t("Berikan Izin Penyimpanan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAccess();
            }
        });

        button(r, t("Buat Project"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaBuatProyek();
            }
        });
        button(r, t("Kelola Project"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaKelolaProyek();
            }
        });
        button(r, t("Buat Keystore"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaKeystore();
            }
        });
        // Tahap 3J: kelompok visual menu utama (urutan tombol tidak berubah).
        heading(r, t("Fitur Pembuat"), 16);
        button(r, t("Pembuat Ikon"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPembuatIkon();
            }
        });
        button(r, t("Bangun dengan Suara"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaSuara();
            }
        });
        button(r, t("AI Pembuat Aplikasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAi();
            }
        });
        button(r, t("Pasar Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPasar();
            }
        });
        button(r, t("Kolaborasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaKolaborasi();
            }
        });
        button(r, t("Penerjemah Aplikasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPenerjemah();
            }
        });
        button(r, t("Pembuat Game"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaGame();
            }
        });
        button(r, t("Periksa Kode"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPeriksaKode();
            }
        });
        button(r, t("Belajar"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaBelajar();
            }
        });
        // Tahap 3J: kelompok visual menu utama (urutan tombol tidak berubah).
        heading(r, t("Cloud, Tema & Lainnya"), 16);
        button(r, t("Sinkronisasi Cloud"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaCloud();
            }
        });
        button(r, t("Pembuat Backend"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaBackend();
            }
        });
        button(r, t("Desainer Tema"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaTema();
            }
        });
        button(r, t("Pembuat Animasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAnimasi();
            }
        });
        button(r, t("Perekam Aksi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPerekamAksi();
            }
        });
        button(r, t("Keamanan Otomatis"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaKeamanan();
            }
        });
        button(r, t("AI Offline"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAiOffline();
            }
        });
        button(r, t("Widget & Wallpaper"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaWidget();
            }
        });
        button(r, t("Media Sosial"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaMediaSosial();
            }
        });
        button(r, t("Aplikasi dari Gambar"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaDariGambar();
            }
        });
        // Tahap 3J: kelompok visual menu utama (urutan tombol tidak berubah).
        heading(r, t("Suara, Kode & Fitur Baru"), 16);
        button(r, t("Mode Suara"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaModeSuara();
            }
        });
        button(r, t("Penerjemah Kode"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPenerjemahKode();
            }
        });
        button(r, t("Git di Dalam Aplikasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaGit();
            }
        });
        button(r, t("Pembuat Iklan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaIklan();
            }
        });
        button(r, t("Mode Anak"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaModeAnak();
            }
        });
        button(r, t("Wear OS & TV"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaWearTv();
            }
        });
        button(r, t("Peta Proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPeta();
            }
        });
        button(r, t("Kalkulator Ukuran APK"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaUkuranApk();
            }
        });
        button(r, t("Penganalisis APK"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAnalisisApk();
            }
        });
        button(r, t("Pratinjau Ukuran Layar"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPratinjauUkuran();
            }
        });
        button(r, t("Pengelola Sumber Daya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaSumberDaya();
            }
        });
        button(r, t("Pemantau Kinerja Ringan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaKinerja();
            }
        });
        button(r, t("Pembuat Video Demo"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaVideoDemo();
            }
        });
        // Fitur baru Gemini (2026-11-24): audit, autopsi, transpiler,
        // server mikro, keamanan, robot uji, perjalanan waktu, pabrik modul.
        button(r, t("Audit Aksesibilitas"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAuditAkses();
            }
        });
        button(r, t("Autopsi Crash"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAutopsiCrash();
            }
        });
        button(r, t("Transpiler Lintas-Paradigma"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaTranspiler();
            }
        });
        button(r, t("Server Mikro Lokal"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaServerMikro();
            }
        });
        button(r, t("Auditor Keamanan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAuditKeamanan();
            }
        });
        button(r, t("Robot Penguji Antarmuka"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaRobotUji();
            }
        });
        button(r, t("Perjalanan Waktu Kode"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaRiwayatKode();
            }
        });
        button(r, t("Pabrik Modul"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPabrikModul();
            }
        });
        // Kelompok Pengaturan (koreksi 2026-11-24): Pengaturan AI dan
        // Konektor berdampingan di sini, bukan sesudah Pemantau Kinerja.
        heading(r, t("Pengaturan"), 16);
        barisDuaTombol(r, t("Pengaturan AI"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaPengaturanAi();
            }
        }, t("Konektor"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaKonektor();
            }
        });
        // Tahap 3J: kelompok visual menu utama (urutan tombol tidak berubah).
        heading(r, t("Alat"), 16);
        button(r, t("Terminal"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaTerminal();
            }
        });
        button(r, t("Tentang Alat"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaTentang();
            }
        });
        // Batch pasca-v2: Donasi ke Pengembang, di samping Tentang Alat.
        button(r, t("Donasi ke Pengembang"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaDonasi();
            }
        });
        // Analitik + App Quality Insights: di samping Donasi ke Pengembang.
        button(r, t("Analitik & Kualitas"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bukaAnalitik();
            }
        });

        perbaruiIzin();
        announce(APP_NAME);
        // Analitik: catat kunjungan layar utama (sekali per onCreate).
        AnalitikActivity.catatLayar(this, t("Layar Utama"));
        // Log crash: tampilkan log pembukaan sebelumnya yang berakhir crash.
        tampilkanLogCrashTerakhir();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Perbaikan 2026-11-24 (laporan al kautsar: ganti bahasa tidak
        // langsung terasa): bila bahasa efektif berubah sejak layar ini
        // dibangun (mis. dipilih dari Pengaturan), bangun ulang layar
        // supaya semua teks memakai bahasa baru.
        String bahasaBaru = Bahasa.kodeEfektif(
                PengaturanActivity.baca(this).getString(
                        PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                java.util.Locale.getDefault().getLanguage());
        if (!bahasaBaru.equals(kodeBahasa)) {
            recreate();
            return;
        }
        // Pengguna mungkin baru memberi izin di Pengaturan lalu kembali ke sini:
        // perbarui pemberitahuan seperti onResume() di Lua yang memanggil ulang
        // showHome() saat screen == "home".
        perbaruiIzin();
        // Sama seperti onResume() di Lua: lanjutkan pemasangan APK yang
        // tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
        // Ronde 3: tidak ada lagi boot Welcome / mesin Lua — home ini
        // adalah launcher Java murni.
    }

    @Override
    public void onBackPressed() {
        // Back di home keluar dari aplikasi (bukan menampilkan layar lain).
        finishAffinity();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Opsi Lainnya hanya berisi Muat ulang dan Pengaturan (reorganisasi
        // pengaturan 2026-11-17). "Buat proyek" sudah ada sebagai tombol
        // utama — ganda bila ada di sini. "Pengaturan GitHub" pindah ke
        // Pengaturan > Pengaturan GitHub; "Tentang" ada di tombol
        // "Tentang Alat" menu utama; izin penyimpanan/pasang APK diminta
        // lewat alurnya masing-masing (tombol izin / saat pasang APK).
        menu.add(TeksUI.t("Muat ulang"));
        menu.add(t("Pengaturan"));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        // Opsi Lainnya hanya Muat ulang + Pengaturan (reorganisasi 2026-11-17).
        String t = String.valueOf(item.getTitle());
        if (TeksUI.t("Muat ulang").equals(t)) {
            perbaruiIzin();
        } else if (TeksUI.teks(kodeBahasa, t("Pengaturan")).equals(t)) {
            bukaPengaturan();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MINTA_IZIN) {
            perbaruiIzin();
        }
    }

    // ------------------------------------------------------------- log crash

    /**
     * Log crash (perintah al kautsar 2026-11-23): bila pemakaian sebelumnya
     * berakhir crash, log terakhirnya ditampilkan di layar pada pembukaan
     * ini, lengkap dengan ID crash-nya. Tombol Tutup menandai log sudah
     * dibaca (tidak tampil lagi); Hapus Log membuang berkasnya.
     */
    private void tampilkanLogCrashTerakhir() {
        try {
            final File f = CrashHandler.logCrashTerakhir(this);
            if (f == null) return;
            String isi = CrashHandler.bacaTeks(f);
            if (isi == null || isi.length() == 0) {
                CrashHandler.tandaiDibaca(f);
                return;
            }
            if (isi.length() > 6000) isi = isi.substring(0, 6000) + "\n" + TeksUI.t("...(log dipotong)");
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Log Crash Terakhir"))
                    .setMessage(isi)
                    .setPositiveButton(t("Tutup"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            CrashHandler.tandaiDibaca(f);
                        }
                    })
                    .setNegativeButton(t("Hapus Log"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                f.delete();
                            } catch (Throwable ignored) {
                            }
                        }
                    })
                    .show();
            announce(t("Ada log crash dari pemakaian sebelumnya. Lognya ditampilkan di layar."));
        } catch (Throwable ignored) {
        }
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
        if (Build.VERSION.SDK_INT >= 28) {
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

    /** Baris dua tombol berdampingan (koreksi 2026-11-24: Pengaturan AI + Konektor). */
    private void barisDuaTombol(LinearLayout parent, String teks1,
            View.OnClickListener klik1, String teks2, View.OnClickListener klik2) {
        LinearLayout baris = new LinearLayout(this);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpBaris = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpBaris.topMargin = dp(8);
        parent.addView(baris, lpBaris);
        Button b1 = new Button(this);
        b1.setText(teks1);
        b1.setOnClickListener(klik1);
        GayaStudio.tombol(b1);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(4);
        baris.addView(b1, lp1);
        Button b2 = new Button(this);
        b2.setText(teks2);
        b2.setOnClickListener(klik2);
        GayaStudio.tombol(b2);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(4);
        baris.addView(b2, lp2);
    }

    private int dp(float v) {
        return GayaStudio.dp(this, v);
    }

    // ------------------------------------------------------- izin penyimpanan

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private String pesanIzinPenyimpanan() {
        if (Build.VERSION.SDK_INT >= 30) {
            return t("izin akses semua file");
        } else if (Build.VERSION.SDK_INT >= 23) {
            return t("izin penyimpanan (baca/tulis penyimpanan)");
        } else {
            return t("izin penyimpanan");
        }
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent it = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(it);
            } catch (Throwable t) {
                try {
                    startActivity(new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Throwable ignored) {
                }
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                requestPermissions(new String[]{
                        "android.permission.WRITE_EXTERNAL_STORAGE",
                        "android.permission.READ_EXTERNAL_STORAGE"}, MINTA_IZIN);
            } catch (Throwable ignored) {
            }
        }
    }

    private void perbaruiIzin() {
        if (hasAccess()) {
            if (noticeLabel != null) noticeLabel.setVisibility(View.GONE);
            if (noticeButton != null) noticeButton.setVisibility(View.GONE);
            new File(ROOT).mkdirs();
        } else {
            if (noticeLabel != null) noticeLabel.setVisibility(View.VISIBLE);
            if (noticeButton != null) noticeButton.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------- delegasi

    private void bukaTerminal() {
        try {
            Intent it = new Intent(this, TerminalActivity.class);
            startActivity(it);
            announce(TeksUI.t("Terminal dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Terminal: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3H: Mode Suara Penuh — IDE dikendalikan suara terus-menerus.
    private void bukaModeSuara() {
        try {
            startActivity(new Intent(this, ModeSuaraActivity.class));
            announce(TeksUI.t("Mode Suara dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Mode Suara: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3H: Penerjemah Kode — terjemahkan kode antar bahasa (lokal).
    private void bukaPenerjemahKode() {
        try {
            startActivity(new Intent(this, PenerjemahKodeActivity.class));
            announce(t("Penerjemah Kode dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Penerjemah Kode: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Git di Dalam Aplikasi sudah Java murni (GitActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaGit() {
        try {
            startActivity(new Intent(this, GitActivity.class));
            announce(t("Git di Dalam Aplikasi dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Git: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Pembuat Iklan sudah Java murni (IklanActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaIklan() {
        try {
            startActivity(new Intent(this, IklanActivity.class));
            announce(TeksUI.t("Pembuat Iklan dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pembuat Iklan: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Mode Anak sudah Java murni (ModeAnakActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaModeAnak() {
        try {
            startActivity(new Intent(this, ModeAnakActivity.class));
            announce(TeksUI.t("Mode Anak dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Mode Anak: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Wear OS & TV sudah Java murni (WearTvActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaWearTv() {
        try {
            startActivity(new Intent(this, WearTvActivity.class));
            announce(t("Wear OS dan TV dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Wear OS dan TV: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Peta Proyek sudah Java murni (PetaActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaPeta() {
        try {
            startActivity(new Intent(this, PetaActivity.class));
            announce(t("Peta Proyek dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Peta Proyek: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Kalkulator Ukuran APK sudah Java murni (UkuranApkActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaUkuranApk() {
        try {
            startActivity(new Intent(this, UkuranApkActivity.class));
            announce(t("Kalkulator Ukuran APK dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Kalkulator Ukuran APK: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // 5.7: Penganalisis APK (isi APK hasil build, ala APK Analyzer).
    private void bukaAnalisisApk() {
        try {
            startActivity(new Intent(this, AnalisisApkActivity.class));
            announce(TeksUI.t("Penganalisis APK dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Penganalisis APK: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Pengaturan AI + Konektor (2026-11-24): analisis dan kirim APK.
    private void bukaKonektor() {
        try {
            startActivity(new Intent(this, KonektorActivity.class));
            announce(t("Konektor"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Konektor: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Pengaturan AI dari menu utama (koreksi 2026-11-24: di samping Konektor).
    private void bukaPengaturanAi() {
        try {
            startActivity(new Intent(this, PengaturanAiActivity.class));
            announce(t("Pengaturan AI"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pengaturan AI: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Audit Aksesibilitas.
    private void bukaAuditAkses() {
        try {
            startActivity(new Intent(this, AuditAksesActivity.class));
            announce(t("Audit Aksesibilitas"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Audit Aksesibilitas"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Autopsi Crash.
    private void bukaAutopsiCrash() {
        try {
            startActivity(new Intent(this, AutopsiCrashActivity.class));
            announce(t("Autopsi Crash"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Autopsi Crash"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Transpiler Lintas-Paradigma.
    private void bukaTranspiler() {
        try {
            startActivity(new Intent(this, TranspilerActivity.class));
            announce(t("Transpiler Lintas-Paradigma"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Transpiler Lintas-Paradigma"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Server Mikro Lokal.
    private void bukaServerMikro() {
        try {
            startActivity(new Intent(this, ServerMikroActivity.class));
            announce(t("Server Mikro Lokal"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Server Mikro Lokal"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Auditor Keamanan.
    private void bukaAuditKeamanan() {
        try {
            startActivity(new Intent(this, AuditKeamananActivity.class));
            announce(t("Auditor Keamanan"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Auditor Keamanan"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Robot Penguji Antarmuka.
    private void bukaRobotUji() {
        try {
            startActivity(new Intent(this, RobotUjiActivity.class));
            announce(t("Robot Penguji Antarmuka"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Robot Penguji Antarmuka"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Perjalanan Waktu Kode.
    private void bukaRiwayatKode() {
        try {
            startActivity(new Intent(this, RiwayatKodeActivity.class));
            announce(t("Perjalanan Waktu Kode"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Perjalanan Waktu Kode"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Fitur baru Gemini (2026-11-24): Pabrik Modul.
    private void bukaPabrikModul() {
        try {
            startActivity(new Intent(this, PabrikModulActivity.class));
            announce(t("Pabrik Modul"));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal: ") + t("Pabrik Modul"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // 5.7: Pratinjau Ukuran Layar (periksa layout di banyak ukuran layar).
    private void bukaPratinjauUkuran() {
        try {
            startActivity(new Intent(this, PratinjauUkuranActivity.class));
            announce(t("Pratinjau Ukuran Layar dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pratinjau Ukuran Layar: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Pengelola Sumber Daya (pilihan al kautsar dari daftar Android Studio).
    private void bukaSumberDaya() {
        try {
            startActivity(new Intent(this, SumberDayaActivity.class));
            announce(TeksUI.t("Pengelola Sumber Daya dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pengelola Sumber Daya: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Pemantau Kinerja Ringan (pilihan al kautsar dari daftar Android Studio).
    private void bukaKinerja() {
        try {
            startActivity(new Intent(this, KinerjaActivity.class));
            announce(TeksUI.t("Pemantau Kinerja Ringan dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pemantau Kinerja Ringan: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3I: Pembuat Video Demo sudah Java murni (VideoDemoActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaVideoDemo() {
        try {
            startActivity(new Intent(this, VideoDemoActivity.class));
            announce(TeksUI.t("Pembuat Video Demo dibuka."));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pembuat Video Demo: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 2B: layar Buat Proyek sudah Java murni (CreateProjectActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini,
    // seperti onKeyDown di Lua (layar create -> home).
    private void bukaBuatProyek() {
        try {
            startActivity(new Intent(this, CreateProjectActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Buat Proyek: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 2C: layar Kelola Project sudah Java murni (ProjectsActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini,
    // seperti onKeyDown di Lua (layar projects -> home).
    private void bukaKelolaProyek() {
        try {
            startActivity(new Intent(this, ProjectsActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Kelola Project: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Ronde 3: Ekstensi JAWS dihapus (keputusan pengguna 2026-11-17 00:03).
    // Tahap 2E: layar Buat Keystore sudah Java murni (KeystoreActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini,
    // seperti onKeyDown di Lua (layar keystore -> home).
    private void bukaKeystore() {
        try {
            startActivity(new Intent(this, KeystoreActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Buat Keystore: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 2E: layar Tentang Alat sudah Java murni (AboutActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaTentang() {
        try {
            startActivity(new Intent(this, AboutActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Tentang Alat: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Batch pasca-v2: Pengaturan + Donasi ke Pengembang.
    private void bukaPengaturan() {
        try {
            startActivity(new Intent(this, PengaturanActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pengaturan: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void bukaDonasi() {
        try {
            startActivity(new Intent(this, DonasiActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Donasi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Analitik + App Quality Insights: layar ringkasan analitik lokal.
    private void bukaAnalitik() {
        try {
            startActivity(new Intent(this, AnalitikActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Analitik: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3A: Pembuat Ikon Otomatis sudah Java murni (IconMakerActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaPembuatIkon() {
        try {
            startActivity(new Intent(this, IconMakerActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pembuat Ikon: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3D: Bangun dengan Suara sudah Java murni (VoiceActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaSuara() {
        try {
            startActivity(new Intent(this, VoiceActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Bangun dengan Suara: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3D: AI Pembuat Aplikasi sudah Java murni (AiActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaAi() {
        try {
            startActivity(new Intent(this, AiActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka AI Pembuat Aplikasi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3D: Pasar Proyek sudah Java murni (MarketActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaPasar() {
        try {
            startActivity(new Intent(this, MarketActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pasar Proyek: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3D: Kolaborasi sudah Java murni (KolaborasiActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaKolaborasi() {
        try {
            startActivity(new Intent(this, KolaborasiActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Kolaborasi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3E: Penerjemah Aplikasi sudah Java murni (TerjemahActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaPenerjemah() {
        try {
            startActivity(new Intent(this, TerjemahActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Penerjemah Aplikasi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3E: Pembuat Game sudah Java murni (GameActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaGame() {
        try {
            startActivity(new Intent(this, GameActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pembuat Game: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3E: Periksa Kode sudah Java murni (PeriksaActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaPeriksaKode() {
        try {
            startActivity(new Intent(this, PeriksaActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Periksa Kode: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3E: Belajar sudah Java murni (BelajarActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaBelajar() {
        try {
            startActivity(new Intent(this, BelajarActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Belajar: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3F: Sinkronisasi Cloud sudah Java murni (CloudActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaCloud() {
        try {
            startActivity(new Intent(this, CloudActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Sinkronisasi Cloud: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3F: Pembuat Backend sudah Java murni (BackendActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaBackend() {
        try {
            startActivity(new Intent(this, BackendActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pembuat Backend: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3F: Desainer Tema sudah Java murni (TemaActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaTema() {
        try {
            startActivity(new Intent(this, TemaActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Desainer Tema: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3F: Pembuat Animasi sudah Java murni (AnimasiActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaAnimasi() {
        try {
            startActivity(new Intent(this, AnimasiActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Pembuat Animasi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3F: Perekam Aksi sudah Java murni (PerekamAksiActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaPerekamAksi() {
        try {
            startActivity(new Intent(this, PerekamAksiActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Perekam Aksi: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3G: Keamanan Otomatis sudah Java murni (KeamananActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaKeamanan() {
        try {
            startActivity(new Intent(this, KeamananActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Keamanan Otomatis: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3G: AI Offline sudah Java murni (AiOfflineActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaAiOffline() {
        try {
            startActivity(new Intent(this, AiOfflineActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka AI Offline: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3G: Widget & Wallpaper sudah Java murni (WidgetActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaWidget() {
        try {
            startActivity(new Intent(this, WidgetActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Widget & Wallpaper: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3G: Media Sosial sudah Java murni (MediaSosialActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaMediaSosial() {
        try {
            startActivity(new Intent(this, MediaSosialActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Media Sosial: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Tahap 3G: Aplikasi dari Gambar sudah Java murni (DariGambarActivity).
    // Tidak finish() supaya tombol Back dari sana kembali ke home ini.
    private void bukaDariGambar() {
        try {
            startActivity(new Intent(this, DariGambarActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, t("Gagal membuka Aplikasi dari Gambar: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Ronde 3: metode tampilkanLayar() dihapus — tombol home membuka
    // Activity Java murni; jembatan Lua tampilkanLayar() di main.lua
    // ikut dihapus bersama mesin Lua.
    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
