package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mode Suara Penuh (Tahap 3H) — IDE sepenuhnya dikendalikan suara.
 *
 * Pengguna menekan "Mulai Mendengarkan", lalu IDE mendengarkan
 * TERUS-MENERUS: setiap ucapan dikenali, dijalankan, dan dijawab
 * dengan suara (TextToSpeech) + teks (untuk Jieshuo). Tidak perlu
 * menekan tombol tiap perintah. Perintah dikenali PerintahSuara
 * (diperluas di Tahap 3H: semua menu utama, semua aksi proyek,
 * navigasi layar, dan baca layar — 26 perintah).
 *
 * Pengenalan memakai SpeechRecognizer bawaan HP (gratis; offline bila
 * paket bahasa Indonesia terpasang). Bila HP tidak punya layanan
 * pengenal suara, tombol beralih ke RecognizerIntent sekali ucap
 * (pola VoiceActivity) dan perintah tetap bisa ditulis di kotak teks.
 * Izin mikrofon (RECORD_AUDIO) diminta saat pertama kali mendengarkan.
 *
 * Keadaan aktif/tidak disimpan di SharedPreferences ("mode_suara"):
 * bila mode ditinggal aktif, mendengarkan dilanjutkan otomatis saat
 * layar ini dibuka lagi.
 */
public class ModeSuaraActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MINTA_SUARA = 1;
    private static final int MINTA_REKAM = 2;
    private static final String PREF = "mode_suara";
    private static final String KUNCI_AKTIF = "aktif";

    private TextToSpeech tts;
    private boolean ttsSiap = false;
    private SpeechRecognizer pengenal;
    private boolean mendengarkan = false;
    private boolean pengenalTersedia = false;
    private int galatBeruntun = 0;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvHasil;
    private EditText ePerintah;
    private Button btnDengarkan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Mode Suara"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Mode Suara"), 20);
        label(r, TeksUI.t("Tekan Mulai Mendengarkan, lalu ucapkan perintah. ")
                + TeksUI.t("Saya mendengarkan terus-menerus, menjalankan perintah, ")
                + TeksUI.t("dan menjawab dengan suara. Tekan Berhenti Mendengarkan ")
                + TeksUI.t("untuk mematikan mode ini."), false);

        tvStatus = label(r, TeksUI.t("Status: tidak mendengarkan."), false);

        btnDengarkan = button(r, TeksUI.t("Mulai Mendengarkan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alihkanMendengarkan();
            }
        });

        label(r, TeksUI.t("Atau tulis perintah di sini:"), false);
        ePerintah = new EditText(this);
        GayaStudio.edit(ePerintah);
        ePerintah.setHint("Contoh: buat proyek kalkulator");
        ePerintah.setSingleLine(true);
        r.addView(ePerintah, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Jalankan perintah"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jalankanUcapan(ePerintah.getText().toString());
            }
        });

        button(r, TeksUI.t("Bantuan perintah"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jalankanUcapan("bantuan");
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setText(TeksUI.t("Belum ada perintah."));
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

        try {
            tts = new TextToSpeech(this, this);
        } catch (Throwable t) {
            tts = null;
            ttsSiap = false;
        }
        try {
            pengenalTersedia = SpeechRecognizer.isRecognitionAvailable(this);
        } catch (Throwable t) {
            pengenalTersedia = false;
        }

        SharedPreferences pref = getSharedPreferences(PREF, MODE_PRIVATE);
        if (pref.getBoolean(KUNCI_AKTIF, false)) {
            alihkanMendengarkan();
        } else {
            announce(TeksUI.t("Mode Suara. Tekan Mulai Mendengarkan, lalu ucapkan perintah."));
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            try {
                int hasil = tts.setLanguage(new Locale("id", "ID"));
                ttsSiap = hasil >= 0;
                // Batch pasca-v2: kecepatan suara dari Pengaturan
                // (0,5x–2x; bawaan 1x).
                tts.setSpeechRate(PengaturanActivity.kecepatanSuara(this));
            } catch (Throwable ignored) {
                ttsSiap = false;
            }
        }
    }

    @Override
    protected void onDestroy() {
        berhentiMendengarkan();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MINTA_REKAM) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mulaiMendengarkan();
        } else {
            tampilkan(TeksUI.t("Izin mikrofon ditolak. Mode Suara tidak bisa mendengarkan; ")
                    + TeksUI.t("perintah tetap bisa ditulis di kotak teks."));
        }
    }

    // -------------------------------------------------- mendengarkan

    private void alihkanMendengarkan() {
        if (mendengarkan) {
            berhentiMendengarkan();
            tampilkan(TeksUI.t("Mode Suara dimatikan. Tekan Mulai Mendengarkan untuk menghidupkan lagi."));
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                if (checkSelfPermission("android.permission.RECORD_AUDIO")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, MINTA_REKAM);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        mulaiMendengarkan();
    }

    private void mulaiMendengarkan() {
        if (!pengenalTersedia) {
            // Tanpa layanan pengenal: sekali ucap lewat RecognizerIntent.
            tampilkan(TeksUI.t("HP ini tidak punya layanan pengenal suara terus-menerus. ")
                    + TeksUI.t("Membuka pengenal suara sekali ucap."));
            dengarkanSekali();
            return;
        }
        try {
            if (pengenal == null) {
                pengenal = SpeechRecognizer.createSpeechRecognizer(this);
                pengenal.setRecognitionListener(new PendengarSuara());
            }
        } catch (Throwable t) {
            tampilkan(TeksUI.t("Gagal menyiapkan pengenal suara: ") + t.getMessage());
            return;
        }
        mendengarkan = true;
        galatBeruntun = 0;
        simpanKeadaan(true);
        perbaruiTombol();
        tampilkan(TeksUI.t("Mode Suara aktif. Saya mendengarkan terus-menerus. Ucapkan perintah."));
        dengarkanSekaliTerus();
    }

    private void berhentiMendengarkan() {
        mendengarkan = false;
        simpanKeadaan(false);
        try {
            if (pengenal != null) {
                pengenal.cancel();
                pengenal.destroy();
                pengenal = null;
            }
        } catch (Throwable ignored) {
        }
        perbaruiTombol();
    }

    private void simpanKeadaan(boolean aktif) {
        try {
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean(KUNCI_AKTIF, aktif).apply();
        } catch (Throwable ignored) {
        }
    }

    private void perbaruiTombol() {
        if (tvStatus != null) {
            tvStatus.setText(mendengarkan
                    ? TeksUI.t("Status: mendengarkan terus-menerus.")
                    : TeksUI.t("Status: tidak mendengarkan."));
        }
        if (btnDengarkan != null) {
            btnDengarkan.setText(mendengarkan
                    ? TeksUI.t("Berhenti Mendengarkan") : TeksUI.t("Mulai Mendengarkan"));
            btnDengarkan.setContentDescription(mendengarkan
                    ? TeksUI.t("Berhenti mendengarkan") : TeksUI.t("Mulai mendengarkan terus-menerus"));
        }
    }

    /** Satu putaran dengar (dipanggil ulang setelah tiap hasil/galot). */
    private void dengarkanSekaliTerus() {
        if (!mendengarkan || pengenal == null) return;
        try {
            Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
            it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            pengenal.startListening(it);
        } catch (Throwable t) {
            tampilkan(TeksUI.t("Gagal mulai mendengarkan: ") + t.getMessage());
        }
    }

    /** Sekali ucap lewat aplikasi pengenal (cadangan bila tanpa layanan). */
    private void dengarkanSekali() {
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        it.putExtra(RecognizerIntent.EXTRA_PROMPT, TeksUI.t("Ucapkan perintah"));
        try {
            startActivityForResult(it, MINTA_SUARA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    TeksUI.t("Tidak ada aplikasi pengenal suara di HP ini. Tulis perintah di kotak teks."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private class PendengarSuara implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            if (!mendengarkan) return;
            galatBeruntun++;
            if (galatBeruntun >= 5) {
                berhentiMendengarkan();
                tampilkan(TeksUI.t("Mendengarkan dijeda karena galat beruntun ")
                        + TeksUI.t("(mungkin tidak ada suara / layanan sibuk). ")
                        + TeksUI.t("Tekan Mulai Mendengarkan untuk mencoba lagi."));
                return;
            }
            // Galat biasa (tidak ada suara, tidak cocok): dengar lagi.
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    dengarkanSekaliTerus();
                }
            }, 500);
        }

        @Override
        public void onResults(Bundle results) {
            galatBeruntun = 0;
            ArrayList<String> daftar = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (daftar != null && !daftar.isEmpty()) {
                jalankanUcapan(daftar.get(0));
            }
            if (mendengarkan) {
                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        dengarkanSekaliTerus();
                    }
                }, 300);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    // -------------------------------------------------- eksekutor perintah

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
            StringBuilder sb = new StringBuilder(TeksUI.t("Perintah yang bisa diucapkan:") + "\n");
            for (int i = 0; i < PerintahSuara.DAFTAR_PERINTAH.length; i++) {
                sb.append("\n\u2022 ").append(PerintahSuara.DAFTAR_PERINTAH[i]);
            }
            tampilkan(sb.toString());
        } else if ("BACA".equals(aksi)) {
            String isi = tvHasil == null ? "" : tvHasil.getText().toString();
            if (isi.isEmpty() || (TeksUI.t("Belum ada perintah.").equals(isi) || TeksUI.t("Belum ada perintah.").equals(isi))) {
                tampilkan(TeksUI.t("Belum ada hasil untuk dibacakan. Ucapkan perintah dulu."));
            } else {
                tampilkan(isi);
            }
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
        } else if ("BANGUN_TERMINAL".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            PerintahBangun.Hasil hb = PerintahBangun.untuk(dir);
            try {
                Intent it = new Intent(this, TerminalActivity.class);
                it.putExtra(TerminalActivity.EXTRA_FOLDER, dir.getAbsolutePath());
                it.putExtra(TerminalActivity.EXTRA_PERINTAH, hb.perintah);
                it.putExtra(TerminalActivity.EXTRA_JALANKAN, hb.adaGradle);
                startActivity(it);
                tampilkan(hb.adaGradle
                        ? TeksUI.t("Membuka Terminal dan membangun proyek ") + p.argumen + "."
                        : TeksUI.t("Membuka Terminal di folder proyek ") + p.argumen + ". " + hb.petunjuk);
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal membuka Terminal: ") + t.getMessage());
            }
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
        } else if ("CARI".equals(aksi)) {
            try {
                Intent it = new Intent(this, ProjectsActivity.class);
                it.putExtra(ProjectsActivity.EXTRA_CARI, p.argumen);
                startActivity(it);
                tampilkan(TeksUI.t("Mencari proyek ") + p.argumen + ".");
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal membuka Kelola Project: ") + t.getMessage());
            }
        } else if ("HAPUS".equals(aksi)) {
            hapusProyek(p.argumen);
        } else if ("UJI".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            try {
                List<UjiOtomatis.Hasil> hasil = UjiOtomatis.uji(dir);
                tampilkan(TeksUI.t("Uji Otomatis proyek ") + p.argumen + ": "
                        + UjiOtomatis.ringkasan(hasil) + "\n" + UjiOtomatis.laporan(hasil));
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal menguji proyek: ") + t.getMessage());
            }
        } else if ("PERIKSA".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            try {
                List<PemeriksaKode.Masalah> masalah = PemeriksaKode.periksa(dir);
                tampilkan(TeksUI.t("Pemeriksaan kode proyek ") + p.argumen + ":\n"
                        + PemeriksaKode.laporan(dir, masalah));
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal memeriksa proyek: ") + t.getMessage());
            }
        } else if ("EKSPOR".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            String jalur = EksporProyek.ekspor(dir.getAbsolutePath());
            if (jalur == null || jalur.isEmpty()) {
                tampilkan(TeksUI.t("Gagal mengekspor proyek ") + p.argumen + TeksUI.t(". Periksa izin penyimpanan."));
            } else {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" diekspor ke ") + jalur + ".");
            }
        } else if ("CADANGKAN".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            File hasil = ArsipProyek.buatCadangan(dir,
                    new File(Environment.getExternalStorageDirectory(), "Download/backup"));
            if (hasil != null) {
                tampilkan(TeksUI.t("Cadangan proyek ") + p.argumen + TeksUI.t(" tersimpan di ")
                        + hasil.getAbsolutePath() + ".");
            } else {
                tampilkan(TeksUI.t("Gagal membuat cadangan proyek ") + p.argumen
                        + TeksUI.t(". Periksa izin penyimpanan."));
            }
        } else if ("BAGIKAN".equals(aksi)) {
            File dir = cariFolderProyek(p.argumen);
            if (dir == null) {
                tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                return;
            }
            bagikanProyek(dir);
        } else if ("IKON".equals(aksi)) {
            try {
                Intent it = new Intent(this, IconMakerActivity.class);
                if (p.argumen != null && !p.argumen.isEmpty()) {
                    File dir = cariFolderProyek(p.argumen);
                    if (dir == null) {
                        tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                        return;
                    }
                    it.putExtra(IconMakerActivity.EXTRA_DIR, dir.getAbsolutePath());
                }
                startActivity(it);
                tampilkan(p.argumen == null || p.argumen.isEmpty()
                        ? TeksUI.t("Membuka Pembuat Ikon.")
                        : TeksUI.t("Membuka Pembuat Ikon untuk proyek ") + p.argumen + ".");
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal membuka Pembuat Ikon: ") + t.getMessage());
            }
        } else if ("TERJEMAH".equals(aksi)) {
            try {
                Intent it = new Intent(this, PenerjemahKodeActivity.class);
                if (p.argumen != null && !p.argumen.isEmpty()) {
                    File dir = cariFolderProyek(p.argumen);
                    if (dir == null) {
                        tampilkan(TeksUI.t("Proyek ") + p.argumen + TeksUI.t(" tidak ditemukan."));
                        return;
                    }
                    it.putExtra(PenerjemahKodeActivity.EXTRA_PROYEK, dir.getAbsolutePath());
                }
                startActivity(it);
                tampilkan(p.argumen == null || p.argumen.isEmpty()
                        ? TeksUI.t("Membuka Penerjemah Kode.")
                        : TeksUI.t("Membuka Penerjemah Kode untuk proyek ") + p.argumen + ".");
            } catch (Throwable t) {
                tampilkan(TeksUI.t("Gagal membuka Penerjemah Kode: ") + t.getMessage());
            }
        } else if ("LAYAR".equals(aksi)) {
            bukaLayar(p.argumen);
        } else if ("KEMBALI".equals(aksi)) {
            tampilkan(TeksUI.t("Kembali ke menu sebelumnya."));
            finish();
        } else if ("TUTUP".equals(aksi)) {
            tampilkan(TeksUI.t("Menutup aplikasi."));
            finishAffinity();
        } else {
            tampilkan(TeksUI.t("Perintah \"") + teks + TeksUI.t("\" tidak saya kenali. Ucapkan ")
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
        StringBuilder sb = new StringBuilder(TeksUI.t("Proyek kamu:") + "\n");
        String[] baris = hasil.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].isEmpty()) continue;
            String[] p = baris[i].split("\\|", -1);
            sb.append("\n\u2022 ").append(p[0]);
        }
        tampilkan(sb.toString());
    }

    private void hapusProyek(final String nama) {
        final File dir = cariFolderProyek(nama);
        if (dir == null) {
            tampilkan(TeksUI.t("Proyek ") + nama + TeksUI.t(" tidak ditemukan."));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Hapus proyek?"))
                .setMessage(TeksUI.t("Proyek ") + nama + TeksUI.t(" akan dihapus beserta semua berkasnya. ")
                        + TeksUI.t("Tindakan ini tidak bisa dibatalkan."))
                .setPositiveButton(TeksUI.t("Ya, hapus"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        boolean ok = hapusRekursif(dir);
                        tampilkan(ok ? TeksUI.t("Proyek ") + nama + TeksUI.t(" sudah dihapus.")
                                : TeksUI.t("Gagal menghapus proyek ") + nama + ".");
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private boolean hapusRekursif(File f) {
        boolean ok = true;
        if (f.isDirectory()) {
            File[] anak = f.listFiles();
            if (anak != null) {
                for (int i = 0; i < anak.length; i++) {
                    if (!hapusRekursif(anak[i])) ok = false;
                }
            }
        }
        if (!f.delete()) ok = false;
        return ok;
    }

    private void bagikanProyek(File dir) {
        String jalurZip = EksporProyek.ekspor(dir.getAbsolutePath());
        if (jalurZip == null || jalurZip.isEmpty()) {
            tampilkan(TeksUI.t("Gagal membuat ZIP proyek. Periksa izin penyimpanan."));
            return;
        }
        try {
            File zip = new File(jalurZip);
            Uri uri = FileProvider.getUriForFile(this, getPackageName(), zip);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("application/zip");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, TeksUI.t("Bagikan ") + dir.getName() + ".zip"));
            tampilkan(TeksUI.t("Membagikan proyek ") + dir.getName() + ".");
        } catch (Throwable t) {
            tampilkan(TeksUI.t("Gagal membagikan: ") + t.getMessage());
        }
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
        } else if ("keamanan".equals(layar)) {
            tujuan = KeamananActivity.class;
            nama = TeksUI.t("Keamanan Otomatis");
        } else if ("ai-offline".equals(layar)) {
            tujuan = AiOfflineActivity.class;
            nama = TeksUI.t("AI Offline");
        } else if ("widget".equals(layar)) {
            tujuan = WidgetActivity.class;
            nama = TeksUI.t("Pembuat Widget & Wallpaper");
        } else if ("medsos".equals(layar)) {
            tujuan = MediaSosialActivity.class;
            nama = TeksUI.t("Integrasi Media Sosial");
        } else if ("dari-gambar".equals(layar)) {
            tujuan = DariGambarActivity.class;
            nama = TeksUI.t("Aplikasi dari Gambar");
        } else if ("terjemah".equals(layar)) {
            tujuan = TerjemahActivity.class;
            nama = TeksUI.t("Penerjemah Bahasa");
        } else if ("game".equals(layar)) {
            tujuan = GameActivity.class;
            nama = TeksUI.t("Game Tanpa Kode");
        } else if ("uji".equals(layar)) {
            tujuan = ProjectsActivity.class;
            nama = TeksUI.t("Kelola Project (pilih proyek, lalu Uji Otomatis)");
        } else if ("periksa".equals(layar)) {
            tujuan = PeriksaActivity.class;
            nama = TeksUI.t("AI Pemeriksa Kode");
        } else if ("belajar".equals(layar)) {
            tujuan = BelajarActivity.class;
            nama = TeksUI.t("Mode Belajar");
        } else if ("cloud".equals(layar)) {
            tujuan = CloudActivity.class;
            nama = TeksUI.t("Sinkronisasi Cloud");
        } else if ("backend".equals(layar)) {
            tujuan = BackendActivity.class;
            nama = "Backend";
        } else if ("tema".equals(layar)) {
            tujuan = TemaActivity.class;
            nama = TeksUI.t("Tema");
        } else if ("animasi".equals(layar)) {
            tujuan = AnimasiActivity.class;
            nama = TeksUI.t("Pembuat Animasi");
        } else if ("perekam".equals(layar)) {
            tujuan = PerekamAksiActivity.class;
            nama = TeksUI.t("Perekam Aksi");
        } else if ("mode-suara".equals(layar)) {
            tampilkan(TeksUI.t("Anda sudah berada di Mode Suara."));
            return;
        } else if ("penerjemah-kode".equals(layar)) {
            tujuan = PenerjemahKodeActivity.class;
            nama = TeksUI.t("Penerjemah Kode");
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
