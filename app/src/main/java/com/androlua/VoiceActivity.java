package com.androlua;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
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
import java.util.Locale;

/**
 * Bangun Aplikasi dengan Suara (Tahap 3D).
 *
 * Pengguna menekan tombol "Dengarkan", mengucapkan perintah, lalu IDE
 * menjalankan perintah itu dan menjawab dengan suara (TextToSpeech) +
 * teks (untuk pembaca layar). Pengenalan suara memakai aplikasi
 * pengenal suara bawaan HP (RecognizerIntent, gratis; offline bila
 * paket bahasa Indonesia terpasang dan dipilih "offline" oleh aplikasi
 * pengenalnya). Bila HP tidak punya pengenal suara, perintah bisa
 * ditulis di kotak teks.
 *
 * Perintah dikenali oleh PerintahSuara (murni Java, bisa diuji di JRE).
 * Pembuatan proyek memakai BuatProyekHelper (jembatan buatProyek yang
 * sama seperti CreateProjectActivity); Bangun/Jalankan memakai jembatan
 * bangunProyek/jalankanProyek yang sama seperti ProjectsActivity.
 */
public class VoiceActivity extends Activity {
    private static final int MINTA_SUARA = 1;

    private TextToSpeech tts;
    private boolean ttsSiap = false;
    private EditText ePerintah;
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Bangun dengan Suara"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Bangun dengan Suara"), 20);
        label(r, TeksUI.t("Tekan Dengarkan, lalu ucapkan perintah. Saya menjawab ")
                + TeksUI.t("dengan suara dan teks."), false);

        button(r, "Dengarkan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dengarkan();
            }
        });

        label(r, TeksUI.t("Atau tulis perintah di sini:"), false);
        ePerintah = new EditText(this);
        GayaStudio.edit(ePerintah);
        ePerintah.setHint("Contoh: buat proyek kalkulator");
        ePerintah.setSingleLine(true);
        r.addView(ePerintah, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, "Jalankan perintah", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jalankanUcapan(ePerintah.getText().toString());
            }
        });

        label(r, "Hasil:", false);
        tvHasil = new TextView(this);
        tvHasil.setText("Belum ada perintah.");
        tvHasil.setTextSize(14);
        r.addView(tvHasil);

        label(r, TeksUI.t("Perintah yang bisa diucapkan:"), false);
        StringBuilder daftar = new StringBuilder();
        for (int i = 0; i < PerintahSuara.DAFTAR_PERINTAH.length; i++) {
            if (i > 0) daftar.append("\n");
            daftar.append("\u2022 ").append(PerintahSuara.DAFTAR_PERINTAH[i]);
        }
        label(r, daftar.toString(), true);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // TextToSpeech bahasa Indonesia (bila tersedia di HP).
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        try {
                            int hasil = tts.setLanguage(new Locale("id", "ID"));
                            ttsSiap = hasil >= 0;
                        } catch (Throwable ignored) {
                            ttsSiap = false;
                        }
                    }
                }
            });
        } catch (Throwable t) {
            tts = null;
            ttsSiap = false;
        }
        announce(TeksUI.t("Bangun dengan Suara. Tekan Dengarkan, lalu ucapkan perintah."));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MINTA_SUARA) return;
        if (resultCode != RESULT_OK || data == null) {
            tampilkan(TeksUI.t("Tidak ada ucapan yang dikenali. Coba lagi."));
            return;
        }
        ArrayList<String> hasil = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
        if (hasil == null || hasil.isEmpty()) {
            tampilkan(TeksUI.t("Tidak ada ucapan yang dikenali. Coba lagi."));
            return;
        }
        jalankanUcapan(hasil.get(0));
    }

    private void dengarkan() {
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        it.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan perintah");
        try {
            startActivityForResult(it, MINTA_SUARA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    TeksUI.t("Tidak ada aplikasi pengenal suara di HP ini. Tulis perintah di kotak teks."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Menjalankan satu ucapan (dari suara atau dari kotak teks). */
    private void jalankanUcapan(String ucapan) {
        if (ucapan == null) ucapan = "";
        String teks = ucapan.trim();
        if (teks.isEmpty()) {
            tampilkan(TeksUI.t("Perintah masih kosong. Ucapkan atau tulis perintah dulu."));
            return;
        }
        ePerintah.setText(teks);
        PerintahSuara.Perintah p = PerintahSuara.parse(teks);
        String aksi = p.aksi;
        if ("BANTUAN".equals(aksi)) {
            StringBuilder sb = new StringBuilder("Perintah yang bisa diucapkan:\n");
            for (int i = 0; i < PerintahSuara.DAFTAR_PERINTAH.length; i++) {
                sb.append("\n\u2022 ").append(PerintahSuara.DAFTAR_PERINTAH[i]);
            }
            tampilkan(sb.toString());
        } else if ("BUAT_FORM".equals(aksi)) {
            tampilkan(TeksUI.t("Membuka form Buat Proyek."));
            try {
                startActivity(new Intent(this, CreateProjectActivity.class));
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal membuka Buat Proyek: ") + t.getMessage());
            }
        } else if ("BUAT".equals(aksi)) {
            String tipe = "lua";
            if (p.templateId != null) {
                TemplateProyek.Info info = TemplateProyek.cari(p.templateId);
                if (info != null && info.tipe != null) tipe = info.tipe;
            }
            String dir = BuatProyekHelper.buat(this, p.argumen, tipe, p.templateId);
            if (dir != null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" berhasil dibuat."));
            } else {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" gagal dibuat."));
            }
        } else if ("BANGUN".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            bangunProyek(dir);
        } else if ("BUKA".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            try {
                Intent it = new Intent(this, EditorActivity.class);
                it.putExtra(EditorActivity.EXTRA_DIR, dir.getAbsolutePath());
                startActivity(it);
                tampilkan(TeksUI.t("Membuka proyek ") + p.argumen + TeksUI.t(" di editor."));
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal membuka proyek: ") + t.getMessage());
            }
        } else if ("JALANKAN".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            // Jalankan kini Java murni (Tahap 3I).
            try {
                JalankanProyek.jalankan(this, dir);
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal menjalankan: ") + t.getMessage());
                return;
            }
            tampilkan(TeksUI.t("Menjalankan proyek ") + p.argumen + ".");
            finish();
        } else if ("DAFTAR".equals(aksi)) {
            daftarProyek();
        } else if ("LAYAR".equals(aksi)) {
            bukaLayar(p.argumen);
        } else if ("KEMBALI".equals(aksi)) {
            tampilkan(TeksUI.t("Kembali ke menu sebelumnya."));
            finish();
        } else if ("TUTUP".equals(aksi)) {
            tampilkan(TeksUI.t("Menutup aplikasi."));
            finishAffinity();
        } else {
            tampilkan("Perintah \"" + teks + TeksUI.t("\" tidak saya kenali. Ucapkan ")
                    + TeksUI.t("\"bantuan\" untuk mendengar daftar perintah."));
        }
    }

    // Bangun kini Java murni (Tahap 3I). Dialog bangun tampil di
    // Activity ini sendiri — jangan tampilkanMain, jangan finish
    // (dialog harus tetap hidup).
    private void bangunProyek(File dir) {
        try {
            BangunProyek.bangun(this, dir);
        } catch (Throwable t) {
            tampilkan(TeksUI.t("Gagal membangun: ") + t.getMessage());
            return;
        }
        tampilkan(TeksUI.t("Membangun proyek ") + dir.getName() + ".");
    }

    // Daftar proyek kini Java murni (Tahap 3I): InfoProyek.daftar membaca
    // init.lua sebagai teks dan mengembalikan format yang sama dengan
    // jembatan Lua dulu, jadi parsing di bawah tidak berubah.
    private void daftarProyek() {
        String hasil = "";
        try {
            hasil = InfoProyek.daftar(new File(BuatProyekHelper.root()));
        } catch (Throwable t) {
            tampilkan(TeksUI.t("Gagal memuat daftar proyek: ") + t.getMessage());
            return;
        }
        if (hasil == null || hasil.isEmpty()) {
            tampilkan(TeksUI.t("Belum ada proyek. Buat dulu dengan perintah \"buat proyek kalkulator\"."));
            return;
        }
        StringBuilder sb = new StringBuilder("Proyek kamu:\n");
        String[] baris = hasil.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].isEmpty()) continue;
            String[] p = baris[i].split("\\|", -1);
            sb.append("\n\u2022 ").append(p[0]);
        }
        tampilkan(sb.toString());
    }

    private void bukaLayar(String layar) {
        Class<?> tujuan = null;
        String nama = "";
        if ("ikon".equals(layar)) {
            tujuan = IconMakerActivity.class;
            nama = TeksUI.t("Pembuat Ikon");
        } else if ("terminal".equals(layar)) {
            tujuan = TerminalActivity.class;
            nama = TeksUI.t("Terminal");
        } else if ("pasar".equals(layar)) {
            tujuan = MarketActivity.class;
            nama = TeksUI.t("Pasar Proyek");
        } else if ("ai".equals(layar)) {
            tujuan = AiActivity.class;
            nama = TeksUI.t("AI Pembuat Aplikasi");
        } else if ("kolaborasi".equals(layar)) {
            tujuan = KolaborasiActivity.class;
            nama = TeksUI.t("Kolaborasi");
        } else if ("keystore".equals(layar)) {
            tujuan = KeystoreActivity.class;
            nama = TeksUI.t("Buat Keystore");
        } else if ("tentang".equals(layar)) {
            tujuan = AboutActivity.class;
            nama = TeksUI.t("Tentang Alat");
        } else if ("kelola".equals(layar)) {
            tujuan = ProjectsActivity.class;
            nama = TeksUI.t("Kelola Project");
        }
        if (tujuan == null) {
            tampilkan(TeksUI.t("Layar tidak dikenal."));
            return;
        }
        try {
            startActivity(new Intent(this, tujuan));
            tampilkan(TeksUI.t("Membuka ") + nama + ".");
        } catch (Throwable t) {
            tampilkan(TeksUI.t("Gagal membuka ") + nama + ": " + t.getMessage());
        }
    }

    /** Mencari folder proyek berdasarkan nama (tanpa memperhatikan huruf besar/kecil).
     * Ronde 3: folder proyek Lua dilewati (proyek Lua lama disembunyikan dari
     * daftar dan dibuka lewat Lua Manager). */
    private File cariFolderProyek(String nama) {
        if (nama == null || nama.isEmpty()) return null;
        File root = new File(BuatProyekHelper.root());
        File[] anak = root.listFiles();
        if (anak == null) return null;
        for (int i = 0; i < anak.length; i++) {
            if (anak[i].isDirectory() && anak[i].getName().equalsIgnoreCase(nama)
                    && new File(anak[i], "init.lua").isFile()
                    && !"lua".equals(InfoProyek.tipe(anak[i]))) {
                return anak[i];
            }
        }
        return null;
    }

    /** Menampilkan hasil di teks + mengumumkan + membacakan lewat TTS. */
    private void tampilkan(String teks) {
        if (tvHasil != null) tvHasil.setText(teks);
        announce(teks);
        if (ttsSiap && tts != null) {
            try {
                tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "suara");
            } catch (Throwable ignored) {
            }
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

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }
}
