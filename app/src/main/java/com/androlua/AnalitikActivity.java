package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Layar Analitik & Kualitas — App Quality Insights (Pro #6 + AS #5).
 *
 * Dibuka dari tombol "Analitik & Kualitas" di layar utama (di samping
 * tombol "Donasi ke Pengembang"). Isi layar, semuanya teks yang bisa
 * dibaca Jieshuo:
 *
 * (1) Ringkasan: berapa kali aplikasi dibuka, layar yang paling sering
 * dibuka, dan jumlah crash.
 * (2) Daftar layar yang paling sering dibuka (paling banyak 10).
 * (3) Daftar crash terakhir (paling banyak 5), tiap crash berisi tanggal,
 * layar terakhir, pesan error, dan saran perbaikan singkat.
 * (4) Tombol "Hapus Data Analitik" (dengan dialog konfirmasi).
 *
 * PRIVASI (ditulis jujur di layar): semua data ini HANYA tersimpan di HP
 * pengguna (SharedPreferences "analitik_aplikasi") dan TIDAK PERNAH
 * dikirim ke server mana pun. Layar terakhir hanya tercatat untuk layar
 * utama dan layar ini; crash yang terjadi di layar lain tercatat dengan
 * layar "Tidak tercatat" bila layarnya belum sempat dicatat.
 *
 * Logika hitung/simpan/muat/saran ada di AnalitikAplikasi (murni Java,
 * bisa diuji JRE); Activity ini hanya membaca SharedPreferences,
 * menampilkan teks, dan menangani tombol.
 *
 * UI dibuat programatik (tanpa layout XML), mengikuti pola DonasiActivity
 * dan helper GayaStudio (tema terang ala Android Studio, Tahap 3J).
 */
public class AnalitikActivity extends Activity {
    private TextView tvLaporan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Analitik & Kualitas"));
        catatLayar(this, TeksUI.t("Analitik & Kualitas"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        TextView judul = new TextView(this);
        judul.setText("Analitik & Kualitas");
        GayaStudio.judul(judul, 20);
        judul.setPadding(0, dp(4), 0, dp(8));
        r.addView(judul);

        labelKecil(r, TeksUI.t("Data ini hanya tersimpan di HP kamu dan tidak ")
                + TeksUI.t("dikirim ke mana pun. Layar terakhir hanya tercatat ")
                + TeksUI.t("untuk layar utama dan layar ini."));

        tvLaporan = new TextView(this);
        tvLaporan.setText("Memuat data analitik...");
        GayaStudio.label(tvLaporan, 14);
        tvLaporan.setPadding(0, dp(8), 0, dp(8));
        tvLaporan.setContentDescription(TeksUI.t("Laporan analitik dan kualitas"));
        r.addView(tvLaporan);

        button(r, TeksUI.t("Hapus Data Analitik"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tanyaHapus();
            }
        });

        button(r, TeksUI.t("Kembali"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce(TeksUI.t("Analitik dan Kualitas"));
        muatUlang();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PasangHelper.lanjutkanPasangTertunda(this);
        muatUlang();
    }

    // ------------------------------------------------- pencatatan (dipakai layar lain)

    /** Baca SharedPreferences "analitik_aplikasi" (dipakai layar lain). */
    public static SharedPreferences baca(Context ctx) {
        return ctx.getSharedPreferences(AnalitikAplikasi.PREFS,
                Context.MODE_PRIVATE);
    }

    /** Catat satu pembukaan aplikasi (dipanggil Aplikasi sekali per nyala). */
    public static void catatBuka(Context ctx) {
        try {
            SharedPreferences pref = baca(ctx);
            int kini = AnalitikAplikasi.bacaBuka(
                    pref.getString(AnalitikAplikasi.KUNCI_BUKA, "0"));
            pref.edit().putString(AnalitikAplikasi.KUNCI_BUKA,
                    String.valueOf(AnalitikAplikasi.tambahBuka(kini))).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Catat satu kunjungan layar + jadikan layar terakhir (dipanggil layar utama). */
    public static void catatLayar(Context ctx, String nama) {
        try {
            SharedPreferences pref = baca(ctx);
            List<AnalitikAplikasi.Kunjungan> daftar =
                    AnalitikAplikasi.decodeLayar(
                            pref.getString(AnalitikAplikasi.KUNCI_LAYAR, ""));
            if (AnalitikAplikasi.catatLayar(daftar, nama)) {
                pref.edit()
                        .putString(AnalitikAplikasi.KUNCI_LAYAR,
                                AnalitikAplikasi.encodeLayar(daftar))
                        .putString(AnalitikAplikasi.KUNCI_LAYAR_TERAKHIR,
                                AnalitikAplikasi.bersihkan(nama))
                        .apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Nama layar terakhir yang tercatat ("" bila belum ada). */
    public static String layarTerakhir(Context ctx) {
        try {
            return baca(ctx).getString(
                    AnalitikAplikasi.KUNCI_LAYAR_TERAKHIR, "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Catat satu laporan crash (dipanggil CrashHandler, tidak boleh melempar). */
    public static void catatCrash(Context ctx, String tanggal, String layar,
            String pesan, String jejak) {
        try {
            SharedPreferences pref = baca(ctx);
            List<AnalitikAplikasi.Crash> daftar =
                    AnalitikAplikasi.decodeCrash(
                            pref.getString(AnalitikAplikasi.KUNCI_CRASH, ""));
            if (AnalitikAplikasi.tambahCrash(daftar, tanggal, layar, pesan, jejak)) {
                pref.edit().putString(AnalitikAplikasi.KUNCI_CRASH,
                        AnalitikAplikasi.encodeCrash(daftar)).apply();
            }
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

    private void labelKecil(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.labelKecil(tv, 12);
        tv.setPadding(0, dp(8), 0, dp(2));
        parent.addView(tv);
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

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // -------------------------------------------------------------- aksi

    /** Susun teks laporan dari data tersimpan, lalu tampilkan. */
    private void muatUlang() {
        if (tvLaporan == null) return;
        try {
            SharedPreferences pref = baca(this);
            int buka = AnalitikAplikasi.bacaBuka(
                    pref.getString(AnalitikAplikasi.KUNCI_BUKA, "0"));
            List<AnalitikAplikasi.Kunjungan> layar =
                    AnalitikAplikasi.decodeLayar(
                            pref.getString(AnalitikAplikasi.KUNCI_LAYAR, ""));
            List<AnalitikAplikasi.Crash> crash =
                    AnalitikAplikasi.decodeCrash(
                            pref.getString(AnalitikAplikasi.KUNCI_CRASH, ""));
            StringBuilder sb = new StringBuilder();
            sb.append(AnalitikAplikasi.formatRingkasan(buka, layar, crash));
            sb.append("\n" + TeksUI.t("Layar yang sering dibuka:") + "\n");
            if (layar.isEmpty()) {
                sb.append(TeksUI.t("- Belum ada.") + "\n");
            } else {
                int tampil = Math.min(layar.size(), AnalitikAplikasi.TAMPIL_LAYAR);
                // Urutkan salinan dari yang terbanyak (daftar simpanan tetap apa adanya).
                List<AnalitikAplikasi.Kunjungan> urut =
                        new java.util.ArrayList<>(layar);
                for (int i = 0; i < urut.size(); i++) {
                    for (int j = i + 1; j < urut.size(); j++) {
                        if (urut.get(j).jumlah > urut.get(i).jumlah) {
                            AnalitikAplikasi.Kunjungan t = urut.get(i);
                            urut.set(i, urut.get(j));
                            urut.set(j, t);
                        }
                    }
                }
                for (int i = 0; i < tampil; i++) {
                    AnalitikAplikasi.Kunjungan k = urut.get(i);
                    sb.append("- ").append(k.nama).append(": ")
                            .append(k.jumlah).append(TeksUI.t(" kali.") + "\n");
                }
            }
            sb.append("\n" + TeksUI.t("Crash terakhir:") + "\n");
            if (crash.isEmpty()) {
                sb.append(TeksUI.t("- Belum ada crash tercatat.") + "\n");
            } else {
                int tampil = Math.min(crash.size(), AnalitikAplikasi.TAMPIL_CRASH);
                for (int i = 0; i < tampil; i++) {
                    AnalitikAplikasi.Crash c = crash.get(i);
                    sb.append("- ").append(c.tanggal).append(TeksUI.t(", layar "))
                            .append(c.layar).append("." + "\n" + TeksUI.t("  Pesan: "))
                            .append(c.pesan).append("\n  ")
                            .append(c.saran()).append("\n");
                }
            }
            tvLaporan.setText(sb.toString());
        } catch (Throwable t) {
            tvLaporan.setText("Gagal memuat data analitik: " + t.getMessage());
        }
    }

    /** Dialog konfirmasi sebelum menghapus semua data analitik. */
    private void tanyaHapus() {
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Hapus Data Analitik?"))
                .setMessage(TeksUI.t("Semua data analitik di HP ini akan dihapus: ")
                        + TeksUI.t("jumlah pembukaan, kunjungan layar, dan laporan ")
                        + TeksUI.t("crash. Tindakan ini tidak bisa dibatalkan."))
                .setPositiveButton(TeksUI.t("Ya, hapus"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        hapusSemua();
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                    }
                })
                .show();
    }

    private void hapusSemua() {
        try {
            baca(this).edit()
                    .putString(AnalitikAplikasi.KUNCI_BUKA, "0")
                    .putString(AnalitikAplikasi.KUNCI_LAYAR, "")
                    .putString(AnalitikAplikasi.KUNCI_CRASH, "")
                    .putString(AnalitikAplikasi.KUNCI_LAYAR_TERAKHIR, "")
                    .apply();
            toast(TeksUI.t("Data analitik dihapus"));
            announce(TeksUI.t("Data analitik dihapus."));
            muatUlang();
        } catch (Throwable t) {
            toast("Gagal menghapus data analitik: " + t.getMessage());
        }
    }
}
