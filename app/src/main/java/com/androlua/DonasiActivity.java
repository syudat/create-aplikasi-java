package com.androlua;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Layar Donasi ke Pengembang dalam Java murni (Batch pasca-v2).
 *
 * Dibuka dari tombol "Donasi ke Pengembang" di layar utama (di samping
 * tombol "Tentang Alat"). Isi layar:
 *
 * (1) Donasi: teks dengan gaya bicara al kautsar + tombol "Salin Rekening
 * BRI" (menyalin nomor rekening persis ke clipboard + diumumkan ke Jieshuo).
 *
 * (2) Sapa Pengembang di WhatsApp: kotak "Masukkan nama kamu di sini" +
 * kotak "Kota asal kamu" + tombol "Kirim di WhatsApp" yang membuka tautan
 * wa.me dengan pesan sapaan sudah terisi.
 *
 * JUJUR (ditulis sebagai catatan kecil di layar): WhatsApp tidak
 * mengizinkan aplikasi lain mengirim pesan secara otomatis. Pesan sudah
 * terisi otomatis di WhatsApp — pengguna tinggal menekan Kirim di WhatsApp,
 * lalu tombol Kembali untuk pulang ke aplikasi. Layar ini TIDAK menulis
 * seolah pengiriman otomatis.
 *
 * Data pengembang dibaca dari InfoPengembang (konstanta; nomor WA dan
 * rekening BRI ASLI dari al kautsar, 2026-11-14), atau dari Profil
 * Pengembang di Pengaturan bila sudah diisi (Pengaturan menang).
 *
 * UI dibuat programatik (tanpa layout XML), mengikuti pola JavaHomeActivity
 * dan helper GayaStudio (tema terang ala Android Studio, Tahap 3J).
 */
public class DonasiActivity extends Activity {
    private String nomorWa;
    private String rekeningBri;
    private EditText eNama;
    private EditText eKota;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Donasi ke Pengembang");

        SharedPreferences pref = PengaturanActivity.baca(this);
        nomorWa = InfoPengembang.pakaiProfil(
                pref.getString(PengaturanActivity.KUNCI_PROFIL_WA, ""),
                InfoPengembang.NOMOR_WA);
        rekeningBri = InfoPengembang.pakaiProfil(
                pref.getString(PengaturanActivity.KUNCI_PROFIL_REKENING, ""),
                InfoPengembang.REKENING_BRI);

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        TextView judul = new TextView(this);
        judul.setText("Donasi ke Pengembang");
        GayaStudio.judul(judul, 20);
        judul.setPadding(0, dp(4), 0, dp(8));
        r.addView(judul);

        TextView teks = new TextView(this);
        teks.setText("Halo kakak, terima kasih sudah memakai Create Aplikasi. "
                + "Donasi dari kakak membantu pengembang terus memperbaiki "
                + "aplikasi ini supaya makin bermanfaat untuk semua orang. "
                + "Jika kakak ingin berdonasi ke pengembang dan mengapresiasi "
                + "pengembang, tekan tombol Salin Rekening BRI di bawah ini.");
        GayaStudio.label(teks, 14);
        teks.setPadding(0, dp(4), 0, dp(8));
        r.addView(teks);

        label(r, "Rekening BRI pengembang");
        TextView tvRekening = new TextView(this);
        tvRekening.setText(rekeningBri);
        GayaStudio.label(tvRekening, 16);
        tvRekening.setPadding(0, dp(2), 0, dp(4));
        tvRekening.setContentDescription("Rekening BRI: " + rekeningBri);
        r.addView(tvRekening);

        button(r, "Salin Rekening BRI", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salinRekening();
            }
        });

        label(r, "Sapa Pengembang di WhatsApp");
        eNama = new EditText(this);
        GayaStudio.edit(eNama);
        eNama.setHint("Masukkan nama kamu di sini");
        eNama.setTextSize(14);
        eNama.setSingleLine(true);
        r.addView(eNama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        eKota = new EditText(this);
        GayaStudio.edit(eKota);
        eKota.setHint("Kota asal kamu");
        eKota.setTextSize(14);
        eKota.setSingleLine(true);
        r.addView(eKota, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Kirim di WhatsApp", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kirimWhatsapp();
            }
        });

        labelKecil(r, "Catatan: WhatsApp tidak mengizinkan aplikasi lain "
                + "mengirim pesan secara otomatis. Pesanmu sudah terisi "
                + "otomatis di WhatsApp — tinggal tekan Kirim di WhatsApp, "
                + "lalu tekan tombol Kembali untuk pulang ke aplikasi.");

        button(r, "Kembali", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce("Donasi ke Pengembang");
    }

    @Override
    protected void onResume() {
        super.onResume();
        PasangHelper.lanjutkanPasangTertunda(this);
    }

    // ------------------------------------------------------------------ UI

    private LinearLayout newRoot() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        GayaStudio.latar(r);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        return r;
    }

    private void label(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.label(tv, 14);
        tv.setPadding(0, dp(10), 0, dp(2));
        parent.addView(tv);
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

    private void salinRekening() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Rekening BRI", rekeningBri));
            toast("Rekening disalin");
            announce("Rekening disalin: " + rekeningBri);
        } catch (Throwable t) {
            toast("Gagal menyalin rekening: " + t.getMessage());
        }
    }

    private void kirimWhatsapp() {
        String nama = eNama.getText().toString().trim();
        String kota = eKota.getText().toString().trim();
        if (!InfoPengembang.namaKotaValid(nama, kota)) {
            toast("Isi nama dan kota asal dulu");
            announce("Isi nama dan kota asal dulu sebelum mengirim.");
            return;
        }
        String tautan = InfoPengembang.tautanWa(nomorWa, nama, kota);
        try {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(tautan));
            startActivity(it);
            toast("Pesan sudah terisi di WhatsApp — tekan Kirim di sana, "
                    + "lalu kembali ke aplikasi");
            announce("WhatsApp dibuka dengan pesan sudah terisi. "
                    + "Tekan Kirim di WhatsApp, lalu kembali ke aplikasi.");
        } catch (Throwable t) {
            toast("Gagal membuka WhatsApp: " + t.getMessage());
        }
    }
}
