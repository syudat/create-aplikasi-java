package com.androlua;

import android.app.Activity;
import android.content.SharedPreferences;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Audit Aksesibilitas TalkBack/Jieshuo (Fitur Gemini 5.7 nomor 1).
 *
 * Pilih proyek dan berkas layout-nya, lalu "Mulai Audit" — IDE memindai
 * XML layout secara statis, menampilkan laporan temuan beserta transkrip
 * urutan fokus gesek kanan persis seperti yang diucapkan Jieshuo atau
 * TalkBack. Tombol "Sisipkan Label Otomatis" menulis
 * android:contentDescription ke temuan tanpa label yang pertama,
 * memakai label yang diisi di kotak teks. Logika auditnya ada di
 * AuditAkses (murni Java, sudah diuji di komputer).
 */
public class AuditAksesActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spLayout;
    private ArrayAdapter<String> adapterLayout;
    private final List<String> daftarLayout = new ArrayList<String>();
    private EditText eLabel;
    private TextView tvHasil;

    private File berkasLayoutAktif;
    private String xmlAktif;
    private AuditAkses.DaftarTemuan temuanAktif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences pref = PengaturanActivity.baca(this);
        TeksUI.kodeAktif = Bahasa.kodeEfektif(
                pref.getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM),
                Locale.getDefault().getLanguage());
        setTitle(TeksUI.t("Audit Aksesibilitas"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Audit Aksesibilitas"), 20);
        label(r, TeksUI.t("Periksa apakah layout proyek ramah pembaca layar (TalkBack/Jieshuo) sebelum dirilis: tombol tanpa label, label yang sama, dan elemen yang saling menutupi akan ditandai beserta nomor barisnya."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Muat Daftar Layout"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                muatDaftarLayout();
            }
        });

        label(r, TeksUI.t("Pilih berkas layout:"), false);
        spLayout = new Spinner(this);
        adapterLayout = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarLayout);
        adapterLayout.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLayout.setAdapter(adapterLayout);
        r.addView(spLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Mulai Audit"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audit();
            }
        });

        label(r, TeksUI.t("Label untuk tombol tanpa label:"), false);
        eLabel = new EditText(this);
        eLabel.setHint(TeksUI.t("Contoh: Tombol simpan"));
        eLabel.setSingleLine(true);
        GayaStudio.edit(eLabel);
        r.addView(eLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Sisipkan Label Otomatis"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sisipkanLabel();
            }
        });

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
        announce(TeksUI.t("Audit Aksesibilitas. Pilih proyek, pilih berkas layout, lalu tekan Mulai Audit."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ganti bahasa langsung tanpa restart (Fitur 11).
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
    }

    /** Folder layout proyek: res/layout, atau app/src/main/res/layout. */
    static File folderLayout(File dirProyek) {
        File langsung = new File(dirProyek, "res/layout");
        if (langsung.isDirectory()) return langsung;
        File gradle = new File(dirProyek, "app/src/main/res/layout");
        if (gradle.isDirectory()) return gradle;
        return langsung;
    }

    /** Nama berkas *.xml di folder layout, urut abjad. */
    static List<String> daftarLayout(File dirProyek) {
        List<String> hasil = new ArrayList<String>();
        File[] isi = folderLayout(dirProyek).listFiles();
        if (isi == null) return hasil;
        for (int i = 0; i < isi.length; i++) {
            if (isi[i].isFile() && isi[i].getName().toLowerCase().endsWith(".xml")) {
                hasil.add(isi[i].getName());
            }
        }
        Collections.sort(hasil);
        return hasil;
    }

    private void muatDaftarLayout() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        daftarLayout.clear();
        daftarLayout.addAll(daftarLayout(dir));
        if (adapterLayout != null) adapterLayout.notifyDataSetChanged();
        if (daftarLayout.isEmpty()) {
            tvHasil.setText(TeksUI.t("Belum ada berkas layout di proyek ini."));
            announce(TeksUI.t("Belum ada berkas layout di proyek ini."));
        } else {
            announce(daftarLayout.size() + TeksUI.t(" berkas layout ditemukan."));
        }
    }

    private void audit() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        if (spLayout == null || spLayout.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu berkas layoutnya."));
            return;
        }
        File dir = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
        berkasLayoutAktif = new File(folderLayout(dir),
                String.valueOf(spLayout.getSelectedItem()));
        xmlAktif = SumberDaya.teks(berkasLayoutAktif);
        if (xmlAktif == null) {
            tvHasil.setText(TeksUI.t("Berkas layout tidak bisa dibaca."));
            announce(TeksUI.t("Berkas layout tidak bisa dibaca."));
            return;
        }
        temuanAktif = AuditAkses.auditLayout(xmlAktif);
        String transkrip = AuditAkses.transkripFokus(xmlAktif);
        StringBuilder b = new StringBuilder();
        b.append(AuditAkses.laporan(temuanAktif, berkasLayoutAktif.getName()));
        b.append('\n');
        b.append(TeksUI.t("Transkrip urutan fokus (gesek kanan):")).append('\n');
        b.append(transkrip.length() == 0
                ? TeksUI.t("Tidak ada elemen fokus di layout ini.") + "\n"
                : transkrip);
        tvHasil.setText(b.toString());
        announce(TeksUI.t("Audit aksesibilitas selesai. ")
                + temuanAktif.temuan.size() + TeksUI.t(" temuan ditemukan."));
    }

    private void sisipkanLabel() {
        String label = eLabel.getText().toString().trim();
        if (label.length() == 0) {
            toast(TeksUI.t("Isi dulu labelnya."));
            return;
        }
        if (temuanAktif == null || xmlAktif == null || berkasLayoutAktif == null) {
            toast(TeksUI.t("Pilih dulu berkas layoutnya."));
            return;
        }
        AuditAkses.Temuan pertama = null;
        for (int i = 0; i < temuanAktif.temuan.size(); i++) {
            AuditAkses.Temuan t = temuanAktif.temuan.get(i);
            if (AuditAkses.TANPA_LABEL.equals(t.jenis)) {
                pertama = t;
                break;
            }
        }
        if (pertama == null) {
            toast(TeksUI.t("Tidak ada tombol tanpa label. Pilih temuan lain atau isi label dulu."));
            announce(TeksUI.t("Tidak ada tombol tanpa label."));
            return;
        }
        String xmlBaru = AuditAkses.sisipLabel(xmlAktif, pertama.baris, label);
        if (xmlBaru == null || xmlBaru.equals(xmlAktif)) {
            toast(TeksUI.t("Gagal menulis berkas layout."));
            return;
        }
        if (!SumberDaya.tulis(berkasLayoutAktif, xmlBaru)) {
            toast(TeksUI.t("Gagal menulis berkas layout."));
            return;
        }
        xmlAktif = xmlBaru;
        announce(TeksUI.t("Label disisipkan ke baris ") + pertama.baris + ".");
        audit();
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
