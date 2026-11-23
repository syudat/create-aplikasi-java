package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Penerjemah Kode (Tahap 3H) — menerjemahkan kode antar bahasa secara
 * lokal (aturan pola, tanpa internet) memakai PenerjemahKode.
 *
 * Alur: pilih proyek -> pilih berkas sumber (.lua/.java/.kt) -> pilih
 * arah (Lua->Java, Java->Lua, Java->Kotlin) -> Terjemahkan -> hasil
 * ditampilkan beserta laporan pola yang tidak bisa diterjemahkan ->
 * "Simpan hasil" menulis berkas BARU (nama-terjemahan.<ext>) di folder
 * yang sama; berkas asli TIDAK PERNAH ditimpa.
 *
 * EXTRA_PROYEK ("proyek") memilih folder proyek sejak awal
 * (dipakai perintah suara "terjemahkan proyek <nama>").
 */
public class PenerjemahKodeActivity extends Activity {
    /** Folder proyek yang dipilih sejak awal. */
    public static final String EXTRA_PROYEK = "proyek";

    private File dirProyek;
    private File berkasSumber;
    private String arah = PenerjemahKode.LUA_JAVA;
    private String kodeSumber = "";
    private PenerjemahKode.Hasil hasilTerjemah;

    private TextView tvProyek;
    private TextView tvBerkas;
    private TextView tvArah;
    private TextView tvSumber;
    private TextView tvHasil;
    private Button btnSimpan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Penerjemah Kode"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Penerjemah Kode"), 20);
        label(r, TeksUI.t("Terjemahkan kode antar bahasa di HP ini, tanpa internet. ")
                + TeksUI.t("Hasil disimpan sebagai berkas baru, berkas asli tidak diubah."), false);

        tvProyek = label(r, TeksUI.t("Proyek: belum dipilih."), false);
        button(r, TeksUI.t("Pilih proyek"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihProyek();
            }
        });

        tvBerkas = label(r, TeksUI.t("Berkas: belum dipilih."), false);
        button(r, TeksUI.t("Pilih berkas sumber"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihBerkas();
            }
        });

        tvArah = label(r, "Arah: " + PenerjemahKode.labelArah(arah), false);
        button(r, TeksUI.t("Pilih arah terjemahan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pilihArah();
            }
        });

        button(r, "Terjemahkan", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terjemahkan();
            }
        });

        label(r, TeksUI.t("Kode sumber:"), false);
        tvSumber = new TextView(this);
        tvSumber.setText("Belum ada berkas yang dipilih.");
        tvSumber.setTextSize(13);
        tvSumber.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvSumber.setTextIsSelectable(true);
        tvSumber.setMovementMethod(new ScrollingMovementMethod());
        r.addView(tvSumber);

        label(r, "Hasil terjemahan:", false);
        tvHasil = new TextView(this);
        tvHasil.setText("Belum ada hasil.");
        tvHasil.setTextSize(13);
        tvHasil.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setMovementMethod(new ScrollingMovementMethod());
        r.addView(tvHasil);

        btnSimpan = button(r, TeksUI.t("Simpan hasil sebagai berkas baru"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpanHasil();
            }
        });
        btnSimpan.setEnabled(false);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        String proyekExtra = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_PROYEK) : null;
        if (proyekExtra != null && !proyekExtra.isEmpty()) {
            File f = new File(proyekExtra);
            if (f.isDirectory()) {
                dirProyek = f;
                tvProyek.setText("Proyek: " + f.getName());
            }
        }
        perbaruiArah();
        announce(TeksUI.t("Penerjemah Kode. Pilih proyek, berkas sumber, dan arah terjemahan."));
    }

    private void pilihProyek() {
        final List<String> nama = InfoProyek.daftarNama(new File(BuatProyekHelper.root()));
        if (nama.isEmpty()) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek."), Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Belum ada proyek. Buat dulu lewat Buat Project."));
            return;
        }
        final String[] item = nama.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pilih proyek"))
                .setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        dirProyek = new File(BuatProyekHelper.root(), item[which]);
                        berkasSumber = null;
                        kodeSumber = "";
                        hasilTerjemah = null;
                        tvProyek.setText("Proyek: " + item[which]);
                        tvBerkas.setText("Berkas: belum dipilih.");
                        tvSumber.setText("Belum ada berkas yang dipilih.");
                        tvHasil.setText("Belum ada hasil.");
                        btnSimpan.setEnabled(false);
                        announce(TeksUI.t("Proyek dipilih: ") + item[which]);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void pilihBerkas() {
        if (dirProyek == null) {
            Toast.makeText(this, TeksUI.t("Pilih proyek dulu."), Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Pilih proyek dulu."));
            return;
        }
        final List<File> berkas = cariBerkasKode(dirProyek);
        if (berkas.isEmpty()) {
            Toast.makeText(this, "Tidak ada berkas .lua/.java/.kt di proyek ini.",
                    Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Tidak ada berkas kode di proyek ini."));
            return;
        }
        final String[] item = new String[berkas.size()];
        for (int i = 0; i < berkas.size(); i++) {
            item[i] = jalanRelatif(dirProyek, berkas.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pilih berkas sumber"))
                .setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        berkasSumber = berkas.get(which);
                        kodeSumber = bacaBerkas(berkasSumber);
                        hasilTerjemah = null;
                        tvBerkas.setText("Berkas: " + item[which]);
                        tvSumber.setText(kodeSumber.isEmpty()
                                ? TeksUI.t("(berkas kosong atau gagal dibaca)")
                                : kodeSumber);
                        tvHasil.setText("Belum ada hasil.");
                        btnSimpan.setEnabled(false);
                        // Arah bawaan mengikuti ekstensi sumber.
                        String nama = berkasSumber.getName().toLowerCase(Locale.ROOT);
                        if (nama.endsWith(".lua")) {
                            arah = PenerjemahKode.LUA_JAVA;
                        } else if (nama.endsWith(".java")) {
                            arah = PenerjemahKode.JAVA_KOTLIN;
                        } else if (nama.endsWith(".kt")) {
                            arah = PenerjemahKode.JAVA_LUA;
                        }
                        perbaruiArah();
                        announce(TeksUI.t("Berkas dipilih: ") + item[which]);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void pilihArah() {
        final String[] item = new String[PenerjemahKode.ARAH.length];
        for (int i = 0; i < item.length; i++) item[i] = PenerjemahKode.labelArah(PenerjemahKode.ARAH[i]);
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Pilih arah terjemahan"))
                .setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        arah = PenerjemahKode.ARAH[which];
                        perbaruiArah();
                        announce("Arah dipilih: " + item[which]);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), null)
                .show();
    }

    private void perbaruiArah() {
        if (tvArah != null) tvArah.setText("Arah: " + PenerjemahKode.labelArah(arah));
    }

    private void terjemahkan() {
        if (berkasSumber == null || kodeSumber == null || kodeSumber.isEmpty()) {
            Toast.makeText(this, TeksUI.t("Pilih berkas sumber dulu."), Toast.LENGTH_SHORT).show();
            announce(TeksUI.t("Pilih berkas sumber dulu."));
            return;
        }
        hasilTerjemah = PenerjemahKode.terjemah(kodeSumber, arah);
        StringBuilder sb = new StringBuilder();
        sb.append(hasilTerjemah.kode);
        if (hasilTerjemah.adaYangGagal()) {
            sb.append("\n// ---------------- laporan ----------------\n");
            sb.append("// ").append(hasilTerjemah.tidakDiterjemahkan.size())
                    .append(" baris tidak bisa diterjemahkan (disimpan apa adanya):\n");
            for (int i = 0; i < hasilTerjemah.tidakDiterjemahkan.size(); i++) {
                sb.append("// ").append(hasilTerjemah.tidakDiterjemahkan.get(i)).append("\n");
            }
        }
        tvHasil.setText(sb.toString());
        btnSimpan.setEnabled(true);
        String ringkas = TeksUI.t("Terjemahan selesai. ") + hasilTerjemah.barisMasuk
                + TeksUI.t(" baris masuk, ") + hasilTerjemah.tidakDiterjemahkan.size()
                + TeksUI.t(" baris tidak bisa diterjemahkan.");
        announce(ringkas);
        Toast.makeText(this, ringkas, Toast.LENGTH_SHORT).show();
    }

    private void simpanHasil() {
        if (hasilTerjemah == null || berkasSumber == null) {
            announce(TeksUI.t("Belum ada hasil untuk disimpan."));
            return;
        }
        String namaBaru = PenerjemahKode.namaBerkasHasil(berkasSumber.getName(), arah);
        File tujuan = new File(berkasSumber.getParentFile(), namaBaru);
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(tujuan, false), "UTF-8");
            w.write(hasilTerjemah.kode);
            w.close();
            String pesan = TeksUI.t("Hasil disimpan sebagai ") + namaBaru
                    + TeksUI.t(" di folder ") + berkasSumber.getParentFile().getName() + ".";
            announce(pesan);
            Toast.makeText(this, pesan, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            String pesan = TeksUI.t("Gagal menyimpan hasil: ") + t.getMessage();
            announce(pesan);
            Toast.makeText(this, pesan, Toast.LENGTH_LONG).show();
        }
    }

    /** Cari berkas .lua/.java/.kt di dalam proyek (rekursif, maks 100). */
    private static List<File> cariBerkasKode(File dir) {
        List<File> hasil = new ArrayList<>();
        cariRekursif(dir, hasil);
        return hasil;
    }

    private static void cariRekursif(File dir, List<File> hasil) {
        if (dir == null || hasil.size() >= 100) return;
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            if (hasil.size() >= 100) return;
            File f = anak[i];
            if (f.isDirectory()) {
                if (!"bin".equals(f.getName()) && !".git".equals(f.getName())) {
                    cariRekursif(f, hasil);
                }
            } else {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".lua") || n.endsWith(".java") || n.endsWith(".kt")) {
                    hasil.add(f);
                }
            }
        }
    }

    private static String jalanRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    private static String bacaBerkas(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String baris;
            while ((baris = r.readLine()) != null) {
                sb.append(baris).append("\n");
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return "";
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
