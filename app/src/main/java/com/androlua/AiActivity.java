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
import java.util.ArrayList;
import java.util.Locale;

/**
 * AI Pembuat Aplikasi (Tahap 3D).
 *
 * Pengguna mengetik (atau mendikte) deskripsi aplikasi yang diinginkan,
 * lalu AiPembuat menganalisis kata kuncinya SECARA LOKAL (tanpa internet,
 * tanpa API berbayar) dan membuat proyek sungguhan dari template yang
 * paling cocok. Hasil + penjelasan ditampilkan sebagai teks dan dibacakan
 * lewat TextToSpeech.
 *
 * Pembuatan proyek memakai BuatProyekHelper (jembatan buatProyek yang
 * sama seperti CreateProjectActivity).
 */
public class AiActivity extends Activity {
    private static final int MINTA_DIKTE = 1;

    private TextToSpeech tts;
    private boolean ttsSiap = false;
    private EditText eDeskripsi;
    private TextView tvHasil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("AI Pembuat Aplikasi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("AI Pembuat Aplikasi"), 20);
        label(r, TeksUI.t("Tulis atau diktekan aplikasi yang kamu mau, saya buatkan ")
                + TeksUI.t("proyeknya dari template yang paling cocok."), false);

        eDeskripsi = new EditText(this);

        GayaStudio.edit(eDeskripsi);
        eDeskripsi.setHint("Contoh: aplikasi catatan dengan tombol simpan");
        eDeskripsi.setSingleLine(false);
        eDeskripsi.setMinLines(2);
        r.addView(eDeskripsi, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Dikte deskripsi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dikte();
            }
        });

        button(r, TeksUI.t("Buat dengan AI"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatDariDeskripsi();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setText("Belum ada deskripsi.");
        tvHasil.setTextSize(14);
        r.addView(tvHasil);

        label(r, TeksUI.t("Contoh deskripsi yang bisa dicoba:"), false);
        StringBuilder contoh = new StringBuilder();
        for (int i = 0; i < AiPembuat.CONTOH.length; i++) {
            if (i > 0) contoh.append("\n");
            contoh.append("\u2022 ").append(AiPembuat.CONTOH[i]);
        }
        label(r, contoh.toString(), true);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

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
        announce(TeksUI.t("AI Pembuat Aplikasi. Tulis atau diktekan deskripsi aplikasimu."));
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
        if (requestCode != MINTA_DIKTE) return;
        if (resultCode != RESULT_OK || data == null) return;
        ArrayList<String> hasil = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
        if (hasil == null || hasil.isEmpty()) return;
        eDeskripsi.setText(hasil.get(0));
        tampilkan(TeksUI.t("Deskripsi dari dikte: ") + hasil.get(0));
    }

    private void dikte() {
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        it.putExtra(RecognizerIntent.EXTRA_PROMPT, TeksUI.t("Diktekan deskripsi aplikasimu"));
        try {
            startActivityForResult(it, MINTA_DIKTE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    TeksUI.t("Tidak ada aplikasi pengenal suara di HP ini. Tulis deskripsinya di kotak teks."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void buatDariDeskripsi() {
        String deskripsi = eDeskripsi.getText().toString().trim();
        if (deskripsi.isEmpty()) {
            tampilkan(TeksUI.t("Deskripsi masih kosong. Tulis atau diktekan dulu aplikasimu."));
            return;
        }
        AiPembuat.Hasil hasil = AiPembuat.analisis(deskripsi);
        String dir = BuatProyekHelper.buat(this, hasil.namaProyek, hasil.tipe,
                hasil.templateId);
        if (dir == null) {
            tampilkan(TeksUI.t("Proyek gagal dibuat. Coba lagi dengan deskripsi lain."));
            return;
        }
        String teks = hasil.penjelasan + "\n" + "\n" + TeksUI.t("Proyek \"") + hasil.namaProyek
                + TeksUI.t("\" sudah dibuat dan bisa dibuka di Kelola Project.");
        tampilkan(teks);
    }

    /** Menampilkan hasil di teks + mengumumkan + membacakan lewat TTS. */
    private void tampilkan(String teks) {
        if (tvHasil != null) tvHasil.setText(teks);
        announce(teks);
        if (ttsSiap && tts != null) {
            try {
                tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "ai");
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
