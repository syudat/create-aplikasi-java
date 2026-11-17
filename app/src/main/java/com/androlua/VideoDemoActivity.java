package com.androlua;

import android.app.Activity;
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
 * Pembuat Video Demo (Tahap 3I).
 *
 * Pilih proyek, isi judul video, lalu: "Buat Skrip Video Demo" menulis
 * video-demo.txt (urutan adegan + teks narasi + durasi), dan "Buat
 * Slideshow HTML" menulis video-demo.html (gambar proyek berganti
 * otomatis tiap 5 detik, bisa dibuka di browser HP).
 *
 * Batasan jujur yang ditulis di layar: ini BUKAN video .mp4 — untuk
 * video sungguhan, buka video-demo.html di browser lalu rekam layar
 * HP, atau baca narasi di video-demo.txt sambil merekam aplikasi yang
 * sedang berjalan.
 */
public class VideoDemoActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eJudul;
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Pembuat Video Demo");

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, "Pembuat Video Demo", 20);
        label(r, "Buat skrip video demo (urutan adegan + narasi + durasi, "
                + "5 adegan baku) dan slideshow HTML dari gambar-gambar "
                + "proyek. Ini BUKAN video .mp4 — untuk video sungguhan, "
                + "buka video-demo.html di browser HP lalu rekam layar, "
                + "atau baca narasi di video-demo.txt sambil merekam "
                + "aplikasi yang sedang berjalan.", false);

        label(r, "Pilih proyek:", false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, "Judul video:", false);
        eJudul = new EditText(this);
        GayaStudio.edit(eJudul);
        eJudul.setHint("Contoh: Demo Aplikasi Kalkulator Saya");
        r.addView(eJudul, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Buat Skrip Video Demo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatSkrip();
            }
        });
        button(r, "Buat Slideshow HTML", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatSlideshow();
            }
        });

        label(r, "Hasil:", false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(13);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        r.addView(tvHasil);
        button(r, "Kembali ke menu sebelumnya", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce("Pembuat Video Demo. Pilih proyek dan isi judul, lalu buat skrip atau slideshow.");
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

    private File dirTerpilih() {
        if (spProyek == null || spProyek.getSelectedItem() == null) return null;
        return new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
    }

    private String judulVideo() {
        String j = eJudul == null ? "" : eJudul.getText().toString().trim();
        if (j.isEmpty()) {
            File dir = dirTerpilih();
            j = dir == null ? "Video Demo" : "Video Demo " + dir.getName();
        }
        return j;
    }

    private void buatSkrip() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        String judul = judulVideo();
        File hasil = VideoDemo.tulisSkrip(dir, judul, VideoDemo.adeganBaku(dir.getName()));
        if (hasil == null) {
            toast("Gagal menulis skrip video demo.");
            return;
        }
        String ok = "Skrip video demo disimpan ke video-demo.txt di proyek "
                + dir.getName() + " (total "
                + VideoDemo.durasiTotal(VideoDemo.adeganBaku(dir.getName())) + " detik, 5 adegan).";
        toast(ok);
        announce(ok);
        if (tvHasil != null) tvHasil.setText(VideoDemo.teksSkrip(judul,
                VideoDemo.adeganBaku(dir.getName())));
    }

    private void buatSlideshow() {
        File dir = dirTerpilih();
        if (dir == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        List<String> gambar = VideoDemo.kumpulkanGambar(dir);
        if (gambar.isEmpty()) {
            toast("Tidak ada gambar di proyek ini (tambahkan ikon atau tangkapan layar dulu).");
            return;
        }
        File hasil = VideoDemo.tulisSlideshow(dir, judulVideo(), gambar);
        if (hasil == null) {
            toast("Gagal menulis slideshow HTML.");
            return;
        }
        String ok = "Slideshow HTML disimpan ke video-demo.html di proyek "
                + dir.getName() + " (" + gambar.size()
                + " gambar, berganti tiap 5 detik). Buka di browser HP lalu rekam layar.";
        toast(ok);
        announce(ok);
        if (tvHasil != null) {
            tvHasil.setText("Slideshow video-demo.html dibuat dari gambar:\n"
                    + gabungGambar(gambar)
                    + "\n\nBuka berkas itu di browser HP — gambar berganti otomatis tiap 5 detik.");
        }
    }

    private String gabungGambar(List<String> gambar) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gambar.size(); i++) {
            sb.append("- ").append(gambar.get(i)).append("\n");
        }
        return sb.toString();
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
