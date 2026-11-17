package com.uji.python;

// Pemeriksa lisensi (versi berbayar) — dibuat otomatis oleh Create Aplikasi.
// Aplikasi ini memakai lisensi: saat pertama dibuka, pengguna harus
// memasukkan kode lisensi yang diberikan pengembang.
//
// Alur: pengguna melihat ID perangkatnya di layar lisensi, menekan
// "Kirim ID Perangkat" (membuka WhatsApp pengembang dengan ID itu),
// pengembang membalas kode lisensi, pengguna memasukkannya di sini.
// Kode lisensi = FNV-1a 32-bit dari (ID perangkat + ":" + kata sandi
// proyek), ditulis 8 huruf hex besar. Kata sandi proyek ada di init.lua
// (lisensi_seed) — RAHASIA, jangan dibagikan. Pengembang bisa
// menghitung kode, contoh dengan Python:
//   h = 2166136261
//   for b in (id_perangkat + ":" + seed).encode():
//       h = ((h ^ b) * 16777619) & 0xFFFFFFFF
//   kode = "%08X" % h
// Setelah kode benar dimasukkan, status aktif tersimpan dan layar
// lisensi tidak muncul lagi.
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.UUID;

public class Lisensi {
    // Nomor WhatsApp pengembang (kode negara, tanpa +).
    private static final String WA = "628123456789";
    // Kata sandi proyek (lisensi_seed di init.lua). RAHASIA.
    private static final String SEED = "Abc123Xyz789QwEr";

    // ID perangkat: ANDROID_ID; cadangan UUID acak yang disimpan sekali
    // supaya stabil bila ANDROID_ID tidak tersedia.
    public static String idPerangkat(Context ctx) {
        String id = null;
        try {
            id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Throwable ignored) {
        }
        if (id == null || id.length() == 0 || "9774d56d682e549c".equals(id)) {
            SharedPreferences sp = ctx.getSharedPreferences("lisensi", Context.MODE_PRIVATE);
            id = sp.getString("id_cadangan", null);
            if (id == null || id.length() == 0) {
                id = UUID.randomUUID().toString();
                sp.edit().putString("id_cadangan", id).apply();
            }
        }
        return id;
    }

    // Kode lisensi untuk satu ID perangkat (8 huruf hex besar).
    public static String kodeLisensi(String id) {
        return String.format("%08X", Integer.valueOf(fnv1a(id + ":" + SEED)));
    }

    // FNV-1a 32-bit (offset 2166136261, prima 16777619).
    private static int fnv1a(String s) {
        byte[] b;
        try {
            b = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            b = s.getBytes();
        }
        int h = 0x811c9dc5;
        for (int i = 0; i < b.length; i++) {
            h ^= (b[i] & 0xFF);
            h *= 0x01000193;
        }
        return h;
    }

    public static boolean sudahAktif(Context ctx) {
        return ctx.getSharedPreferences("lisensi", Context.MODE_PRIVATE).getBoolean("aktif", false);
    }

    public static void tandaiAktif(Context ctx) {
        ctx.getSharedPreferences("lisensi", Context.MODE_PRIVATE).edit().putBoolean("aktif", true).apply();
    }

    // Layar lisensi: ID perangkat + Kirim ID Perangkat (WhatsApp) +
    // kolom kode lisensi. Bila kode benar, tandai aktif lalu lanjut.
    public static void tampilkanLayarLisensi(final Activity a, final Runnable lanjut) {
        final String id = idPerangkat(a);
        ScrollView sv = new ScrollView(a);
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 16);
        r.setPadding(pad, dp(a, 12), pad, dp(a, 12));
        sv.addView(r);

        TextView judul = new TextView(a);
        judul.setText("Aktivasi Lisensi");
        judul.setTextSize(20);
        r.addView(judul);

        TextView ket = new TextView(a);
        ket.setText("Aplikasi berbayar ini memerlukan kode lisensi. Kirim ID perangkat di bawah ini ke pengembang lewat WhatsApp, lalu masukkan kode lisensi yang dibalas pengembang.");
        r.addView(ket);

        TextView labelId = new TextView(a);
        labelId.setText("ID Perangkat:");
        r.addView(labelId);

        TextView teksId = new TextView(a);
        teksId.setText(id);
        teksId.setTextIsSelectable(true);
        teksId.setTextSize(16);
        r.addView(teksId);

        Button kirim = new Button(a);
        kirim.setText("Kirim ID Perangkat");
        kirim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    String pesan = "ID perangkat saya: " + id + "\nMohon kode lisensinya.";
                    String tautan = "https://wa.me/" + WA + "?text=" + URLEncoder.encode(pesan, "UTF-8");
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tautan)));
                } catch (Throwable t) {
                    Toast.makeText(a, "Gagal membuka WhatsApp: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        r.addView(kirim);

        TextView labelKode = new TextView(a);
        labelKode.setText("Kode lisensi dari pengembang:");
        r.addView(labelKode);

        final EditText kodeEdit = new EditText(a);
        kodeEdit.setHint("Masukkan kode lisensi, contoh: A1B2C3D4");
        kodeEdit.setSingleLine(true);
        kodeEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        r.addView(kodeEdit);

        final TextView status = new TextView(a);
        status.setText("");
        r.addView(status);

        Button aktif = new Button(a);
        aktif.setText("Aktifkan");
        aktif.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String kode = kodeEdit.getText().toString().trim().toUpperCase(Locale.US);
                if (kode.equals(kodeLisensi(id))) {
                    tandaiAktif(a);
                    Toast.makeText(a, "Lisensi aktif. Terima kasih!", Toast.LENGTH_SHORT).show();
                    if (lanjut != null) lanjut.run();
                } else {
                    status.setText("Kode lisensi salah. Periksa lagi kode dari pengembang.");
                }
            }
        });
        r.addView(aktif);

        a.setContentView(sv);
        try {
            a.getWindow().getDecorView().announceForAccessibility("Aktivasi Lisensi. ID Perangkat: " + id);
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context ctx, float v) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (v * density + 0.5f);
    }
}
