package com.uji.python;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.androlua.R;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

// Kelas ini WAJIB bernama MainActivity dan berada tepat di package com.uji.python
// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).
// Host Java untuk proyek Python (Chaquopy): logika aplikasi ada di
// app/src/main/python/main.py, dipanggil lewat Python.start() di bawah.
// Build di perangkat: runtime Chaquopy dikemas BangunPython (lihat
// BangunLokal.java). Build GitHub Actions: plugin Chaquopy di
// build.gradle (lihat tools/pack.py). SDK minimum 24 (syarat Chaquopy).
// Proyek ini memakai LISENSI (versi berbayar): layar lisensi tampil
// saat pertama dibuka; kelas Lisensi.java ada di paket yang sama.
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan
        // layar lisensi dulu; layar utama baru tampil setelah kode
        // lisensi yang benar dimasukkan.
        if (!Lisensi.sudahAktif(this)) {
            Lisensi.tampilkanLayarLisensi(this, new Runnable() {
                @Override public void run() { tampilkanUtama(); }
            });
            return;
        }
        tampilkanUtama();
    }

    private void tampilkanUtama() {
        setContentView(R.layout.activity_main);
        // Teks contoh di layar utama (isinya di res/values/strings.xml).
        TextView judul = (TextView) findViewById(R.id.judul);
        if (judul != null) judul.setText(R.string.teks_contoh);
        // Mulai runtime Python (Chaquopy) lalu panggil main() di main.py.
        // Python.start hanya boleh dipanggil sekali; bila sudah jalan,
        // langsung ambil modulnya.
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        Python.getInstance().getModule("main").callAttr("main");
    }
}
