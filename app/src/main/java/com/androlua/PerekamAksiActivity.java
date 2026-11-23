package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Perekam Aksi (Tahap 3F).
 *
 * Menampilkan urutan aksi yang terekam di IDE (buka/jalankan/bangun/
 * ekspor/cadangkan/buat/simpan) beserta rencananya, dan tombol
 * "Putar Ulang" yang menjalankan kembali aksi yang bisa diotomatisasi:
 * cadangkan dan buat dijalankan di tempat; jalankan/bangun/ekspor
 * didelegasikan ke jembatan Lua lalu layar Main ditampilkan (pemutaran
 * berhenti di situ supaya dialognya terlihat); buka/simpan membuka
 * editor proyek lalu pemutaran berhenti. Aksi yang tidak bisa otomatis
 * (mis. hapus) dilewati dan dilaporkan. Laporan hasil ditampilkan di
 * dialog dan dibacakan Jieshuo.
 *
 * Perekaman sendiri dipasang di CreateProjectActivity (buat),
 * ProjectsActivity (buka/jalankan/bangun/ekspor/cadangkan), dan
 * EditorActivity (simpan) — lewat PerekamAksi.catat(). Pemutaran ulang
 * TIDAK ikut terekam (supaya tidak berputar).
 */
public class PerekamAksiActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";
    private static final String DIR_BACKUP = Environment.getExternalStorageDirectory().getPath()
            + "/Download/backup";

    private ListView lv;
    private ArrayAdapter<String> adapter;
    private final List<String> baris = new ArrayList<String>();
    private List<PerekamAksi.Aksi> daftar = new ArrayList<PerekamAksi.Aksi>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Perekam Aksi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Perekam Aksi"), 20);
        label(r, TeksUI.t("IDE merekam aksi yang kamu lakukan (buka, jalankan, bangun, ekspor, ")
                + TeksUI.t("cadangkan, buat proyek, simpan berkas). Tekan Putar Ulang untuk ")
                + TeksUI.t("menjalankan kembali aksi yang bisa diotomatisasi — aksi yang tidak ")
                + TeksUI.t("bisa otomatis akan dilewati dan dilaporkan."), false);

        lv = new ListView(this);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, baris);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < daftar.size()) {
                    PerekamAksi.Aksi a = daftar.get(pos);
                    new AlertDialog.Builder(PerekamAksiActivity.this)
                            .setTitle(PerekamAksi.namaJenis(a.jenis) + " " + a.target)
                            .setMessage(PerekamAksi.rencana(a))
                            .setPositiveButton("OK", null)
                            .show();
                    announce(PerekamAksi.rencana(a));
                }
            }
        });
        r.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));

        button(r, "Putar Ulang", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                putarUlang();
            }
        });
        button(r, TeksUI.t("Hapus Rekaman"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(PerekamAksiActivity.this)
                        .setTitle(TeksUI.t("Hapus Rekaman"))
                        .setMessage(TeksUI.t("Hapus seluruh rekaman aksi?"))
                        .setPositiveButton(TeksUI.t("Hapus"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PerekamAksi.hapus(berkasRekaman());
                                muatDaftar();
                                toast("Rekaman dihapus.");
                            }
                        })
                        .setNegativeButton(TeksUI.t("Batal"), null)
                        .show();
            }
        });
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce("Perekam Aksi. " + daftar.size() + " aksi terekam, "
                + PerekamAksi.jumlahDapatDiputar(berkasRekaman()) + TeksUI.t(" bisa diputar ulang."));
    }

    @Override
    protected void onResume() {
        super.onResume();
        muatDaftar();
    }

    private File berkasRekaman() {
        return new File(getFilesDir(), PerekamAksi.NAMA_BERKAS);
    }

    private void muatDaftar() {
        daftar = PerekamAksi.baca(berkasRekaman());
        baris.clear();
        for (int i = 0; i < daftar.size(); i++) {
            baris.add(PerekamAksi.tampilkan(daftar.get(i)));
        }
        if (baris.isEmpty()) {
            baris.add(TeksUI.t("Belum ada aksi yang terekam. Lakukan dulu sesuatu di IDE ")
                    + TeksUI.t("(buka proyek, bangun, simpan berkas), lalu kembali ke sini."));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------- putar ulang

    /**
     * Memutar ulang rekaman: aksi yang bisa otomatis dijalankan berurutan;
     * bila menemui aksi navigasi (buka/simpan/jalankan/bangun/ekspor),
     * aksi itu dijalankan lalu pemutaran BERHENTI di situ (layar berpindah
     * ke editor, atau dialog bangun/ekspor tampil di layar ini supaya
     * terlihat). Aksi manual dilewati.
     * Laporan ditampilkan di dialog dan dibacakan.
     */
    private void putarUlang() {
        List<PerekamAksi.Aksi> semua = PerekamAksi.baca(berkasRekaman());
        if (semua.isEmpty()) {
            toast("Belum ada aksi yang terekam.");
            return;
        }
        StringBuilder log = new StringBuilder();
        int jalan = 0;
        int lewati = 0;
        boolean berhenti = false;
        for (int i = 0; i < semua.size() && !berhenti; i++) {
            PerekamAksi.Aksi a = semua.get(i);
            String hasil = jalankanSatu(a);
            if (hasil == null) {
                // Navigasi: aksi dijalankan, pemutaran berhenti di sini.
                log.append("• ").append(PerekamAksi.tampilkan(a)).append(" — dijalankan, pemutaran berhenti di sini.\n");
                jalan++;
                berhenti = true;
            } else if (hasil.isEmpty()) {
                log.append("• ").append(PerekamAksi.tampilkan(a)).append(" — OK.\n");
                jalan++;
            } else if (hasil.startsWith("LEWATI:")) {
                log.append("• ").append(PerekamAksi.tampilkan(a)).append(" — dilewati (")
                        .append(hasil.substring(7)).append(").\n");
                lewati++;
            } else {
                log.append("• ").append(PerekamAksi.tampilkan(a)).append(TeksUI.t(" — GAGAL: "))
                        .append(hasil).append(".\n");
            }
        }
        String ringkas = TeksUI.t("Putar ulang selesai: ") + jalan + " aksi dijalankan, "
                + lewati + " dilewati."
                + (berhenti ? TeksUI.t(" Pemutaran berhenti di aksi navigasi.") : "");
        log.append("\n").append(ringkas);
        final String laporan = log.toString();
        final boolean berhentiAkhir = berhenti;
        new AlertDialog.Builder(this)
                .setTitle("Hasil Putar Ulang")
                .setMessage(laporan)
                .setPositiveButton("OK", null)
                .show();
        announce(ringkas);
        if (berhentiAkhir) {
            // Editor/Main sudah dibuka oleh jalankanSatu; layar ini ditutup
            // supaya pengguna langsung melihat hasil navigasinya.
            finish();
        }
    }

    /**
     * Menjalankan satu aksi.
     * @return "" bila OK dan lanjut; null bila aksi navigasi (dijalankan,
     *         pemutaran harus berhenti); "LEWATI:alasan" bila dilewati;
     *         pesan galat bila gagal.
     */
    private String jalankanSatu(PerekamAksi.Aksi a) {
        if (a == null) return TeksUI.t("LEWATI:aksi kosong");
        String dir = ROOT + "/" + a.target;
        if ("buka".equals(a.jenis) || "simpan".equals(a.jenis)) {
            try {
                Intent it = new Intent(this, EditorActivity.class);
                it.putExtra(EditorActivity.EXTRA_DIR, dir);
                startActivity(it);
                return null;
            } catch (Throwable t) {
                return TeksUI.t("Gagal membuka editor: ") + t.getMessage();
            }
        }
        if ("jalankan".equals(a.jenis)) {
            try {
                JalankanProyek.jalankan(this, new File(dir));
            } catch (Throwable t) {
                return TeksUI.t("Gagal menjalankan: ") + t.getMessage();
            }
            return null;
        }
        if ("bangun".equals(a.jenis)) {
            try {
                BangunProyek.bangun(this, new File(dir));
            } catch (Throwable t) {
                return TeksUI.t("Gagal membangun: ") + t.getMessage();
            }
            return null;
        }
        if ("ekspor".equals(a.jenis)) {
            try {
                EksporProyekJava.ekspor(this, new File(dir));
            } catch (Throwable t) {
                return TeksUI.t("Gagal mengekspor: ") + t.getMessage();
            }
            return null;
        }
        if ("cadangkan".equals(a.jenis)) {
            File hasil = ArsipProyek.buatCadangan(new File(dir), new File(DIR_BACKUP));
            if (hasil == null) return TeksUI.t("Gagal membuat cadangan");
            return "";
        }
        if ("buat".equals(a.jenis)) {
            String tipe = a.detail == null || a.detail.isEmpty() ? "lua" : a.detail;
            String dibuat = BuatProyekHelper.buat(this, a.target, tipe, "kosong");
            if (dibuat == null || dibuat.isEmpty()) return TeksUI.t("Gagal membuat proyek");
            return "";
        }
        return "LEWATI:aksi \"" + a.jenis + TeksUI.t("\" tidak bisa diputar ulang otomatis");
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
