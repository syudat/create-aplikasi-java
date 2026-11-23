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
 * Pembuat Aplikasi Wear OS & TV (Tahap 3I).
 *
 * Pilih target (Wear OS / Android TV / keduanya), lalu buat proyek
 * baru (Java) yang langsung disesuaikan, atau terapkan penyesuaian
 * ke proyek yang sudah ada. Penyesuaian: uses-feature di manifest,
 * kategori LEANBACK_LAUNCHER + banner untuk TV, dan layout
 * activity_main.xml yang disesuaikan (Wear: layar kecil; TV: layar
 * besar ramah D-pad). Penjelasan lengkap ditulis ke wear-tv.txt di
 * proyek.
 *
 * Batasan jujur yang ditulis di layar: ini TEMPLATE PROYEK — build +
 * uji tetap butuh perangkat/emulator Wear OS atau Android TV; banner
 * TV (banner.png 320x180) tidak dibuat otomatis, harus ditambahkan
 * sendiri.
 */
public class WearTvActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private Spinner spTarget;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private EditText eNama;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Wear OS & TV"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pembuat Aplikasi Wear OS & TV"), 20);
        label(r, TeksUI.t("Buat proyek baru untuk jam (Wear OS) atau TV (Android TV), ")
                + TeksUI.t("atau terapkan penyesuaian ke proyek yang sudah ada. IDE ")
                + TeksUI.t("mengubah manifest (uses-feature, LEANBACK_LAUNCHER, ")
                + "banner) dan layout (Wear: layar kecil; TV: layar besar "
                + TeksUI.t("ramah D-pad). Ini template proyek — build + uji tetap ")
                + TeksUI.t("butuh perangkat/emulator Wear OS atau Android TV. Banner ")
                + TeksUI.t("TV (banner.png 320x180) tidak dibuat otomatis, harus ")
                + "ditambahkan sendiri (mis. lewat Pembuat Ikon).", false);

        label(r, "Target perangkat:", false);
        spTarget = new Spinner(this);
        String[] namaTarget = new String[ProyekWearTv.TARGET.length];
        for (int i = 0; i < namaTarget.length; i++) {
            namaTarget[i] = ProyekWearTv.namaTarget(ProyekWearTv.TARGET[i]);
        }
        ArrayAdapter<String> adapterTarget = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, namaTarget);
        adapterTarget.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTarget.setAdapter(adapterTarget);
        r.addView(spTarget, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama proyek baru:"), false);
        eNama = new EditText(this);
        GayaStudio.edit(eNama);
        eNama.setHint("Contoh: Jam Saya");
        r.addView(eNama, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Buat Proyek Baru Wear/TV"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatProyekBaru();
            }
        });

        label(r, TeksUI.t("Atau terapkan ke proyek yang sudah ada:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button(r, TeksUI.t("Terapkan ke Proyek Ini"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terapkanKeProyek();
            }
        });
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pembuat Aplikasi Wear OS dan TV. Pilih target, lalu buat proyek baru atau terapkan ke proyek yang ada."));
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

    private String targetTerpilih() {
        int pos = spTarget == null ? 0 : spTarget.getSelectedItemPosition();
        if (pos < 0 || pos >= ProyekWearTv.TARGET.length) pos = 0;
        return ProyekWearTv.TARGET[pos];
    }

    private void buatProyekBaru() {
        String nama = eNama == null ? "" : eNama.getText().toString().trim();
        if (nama.isEmpty()) {
            toast("Isi dulu nama proyek barunya.");
            return;
        }
        String dir = BuatProyekHelper.buat(this, nama, "java", "kosong");
        if (dir == null) return;
        String galat = ProyekWearTv.terapkan(new File(dir), targetTerpilih());
        if (galat != null) {
            toast("Proyek dibuat, tetapi penyesuaian Wear/TV gagal: " + galat);
            return;
        }
        String ok = TeksUI.t("Proyek ") + nama + " (" + ProyekWearTv.namaTarget(targetTerpilih())
                + TeksUI.t(") dibuat. Baca wear-tv.txt di proyek untuk langkah berikutnya.");
        toast(ok);
        announce(ok);
        muatDaftar();
    }

    private void terapkanKeProyek() {
        if (spProyek == null || spProyek.getSelectedItem() == null) {
            toast("Pilih dulu proyeknya.");
            return;
        }
        String nama = String.valueOf(spProyek.getSelectedItem());
        String galat = ProyekWearTv.terapkan(new File(ROOT + "/" + nama), targetTerpilih());
        if (galat != null) {
            toast(galat);
            return;
        }
        String ok = "Penyesuaian " + ProyekWearTv.namaTarget(targetTerpilih())
                + TeksUI.t(" diterapkan ke proyek ") + nama
                + TeksUI.t(". Baca wear-tv.txt di proyek untuk langkah berikutnya.");
        toast(ok);
        announce(ok);
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
