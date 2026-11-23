package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.FileProvider;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Kolaborasi Langsung (Tahap 3D).
 *
 * Kirim: pilih proyek, tulis catatan perubahan (apa yang diubah), lalu
 * bagikan paket kolaborasi (ZIP proyek + CATATAN.txt di dalamnya) lewat
 * aplikasi berbagi (WhatsApp, Telegram, dll.).
 *
 * Terima: pilih ZIP balasan dari teman — catatannya ditampilkan dulu,
 * lalu pengguna memilih Terapkan (timpa proyek lama dengan isi ZIP)
 * atau Batal. ZIP tanpa CATATAN.txt ditolak dengan pesan jelas (itu
 * bukan paket kolaborasi — pakai Impor proyek untuk ZIP biasa).
 *
 * Memakai Kolaborasi (murni Java) + ArsipProyek.ekstrakZip() untuk
 * menerapkan balasan (verifikasi init.lua di dalam ZIP).
 */
public class KolaborasiActivity extends Activity {
    private static final int MINTA_BALASAN = 3;

    private Spinner spinnerProyek;
    private EditText ePengirim;
    private EditText eCatatan;
    private final List<String> daftarProyek = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Kolaborasi"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Kolaborasi"), 20);
        label(r, TeksUI.t("Kerja bareng teman: kirim proyek beserta catatan perubahanmu, ")
                + TeksUI.t("lalu terima balasannya. Catatan perubahan selalu ditampilkan ")
                + TeksUI.t("sebelum balasan diterapkan."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        muatDaftarProyek();
        spinnerProyek = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProyek.setAdapter(adapter);
        r.addView(spinnerProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Nama kamu (pengirim, boleh kosong):"), false);
        ePengirim = new EditText(this);
        GayaStudio.edit(ePengirim);
        ePengirim.setHint(TeksUI.t("Contoh: Al Kautsar"));
        ePengirim.setSingleLine(true);
        r.addView(ePengirim, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Catatan perubahan (apa yang kamu ubah?):"), false);
        eCatatan = new EditText(this);
        GayaStudio.edit(eCatatan);
        eCatatan.setHint("Contoh: Menambah tombol simpan di layar utama");
        eCatatan.setSingleLine(false);
        eCatatan.setMinLines(3);
        r.addView(eCatatan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        button(r, TeksUI.t("Bagikan untuk Kolaborasi"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bagikan();
            }
        });

        button(r, TeksUI.t("Terima Balasan"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terimaBalasan();
            }
        });

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce(TeksUI.t("Kolaborasi. Pilih proyek, tulis catatan perubahan, lalu bagikan."));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != MINTA_BALASAN) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        terimaDariUri(data.getData());
    }

    private void muatDaftarProyek() {
        daftarProyek.clear();
        File root = new File(BuatProyekHelper.root());
        File[] anak = root.listFiles();
        if (anak != null) {
            for (int i = 0; i < anak.length; i++) {
                if (anak[i].isDirectory()
                        && new File(anak[i], "init.lua").isFile()) {
                    daftarProyek.add(anak[i].getName());
                }
            }
        }
        java.util.Collections.sort(daftarProyek, String.CASE_INSENSITIVE_ORDER);
        if (daftarProyek.isEmpty()) daftarProyek.add(TeksUI.t("(belum ada proyek)"));
    }

    private String proyekTerpilih() {
        if (spinnerProyek == null) return null;
        Object item = spinnerProyek.getSelectedItem();
        if (item == null) return null;
        String nama = String.valueOf(item);
        if ((TeksUI.t("(belum ada proyek)").equals(nama) || TeksUI.t("(belum ada proyek)").equals(nama))) return null;
        return nama;
    }

    private void bagikan() {
        String nama = proyekTerpilih();
        if (nama == null) {
            Toast.makeText(this, TeksUI.t("Belum ada proyek. Buat proyek dulu."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File dirProyek = new File(BuatProyekHelper.root() + "/" + nama);
        String catatan = eCatatan.getText().toString().trim();
        String pengirim = ePengirim.getText().toString().trim();
        File paket = Kolaborasi.buatPaket(dirProyek, catatan, pengirim,
                new File(BuatProyekHelper.root()));
        if (paket == null) {
            Toast.makeText(this, TeksUI.t("Gagal membuat paket kolaborasi."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName(), paket);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("application/zip");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_SUBJECT, TeksUI.t("Kolaborasi proyek ") + nama);
            it.putExtra(Intent.EXTRA_TEXT, TeksUI.t("Paket kolaborasi proyek \"") + nama
                    + TeksUI.t("\" dari Create Aplikasi. Catatan perubahan ada di dalam ")
                    + TeksUI.t("berkas CATATAN.txt di ZIP ini."));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, TeksUI.t("Bagikan paket kolaborasi")));
            announce(TeksUI.t("Paket kolaborasi proyek ") + nama + TeksUI.t(" siap dibagikan."));
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Gagal membagikan: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void terimaBalasan() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("application/zip");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(it, TeksUI.t("Pilih ZIP balasan")),
                    MINTA_BALASAN);
        } catch (Throwable t) {
            Toast.makeText(this, TeksUI.t("Gagal membuka pemilih berkas: ") + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void terimaDariUri(Uri uri) {
        File sementara = new File(getCacheDir(), "kolaborasi-balasan.zip");
        String galatSalin = null;
        InputStream masuk = null;
        try {
            masuk = getContentResolver().openInputStream(uri);
            galatSalin = ArsipProyek.salinAliran(masuk, sementara);
        } catch (Throwable t) {
            galatSalin = TeksUI.t("Gagal membaca berkas: ") + t.getMessage();
        }
        if (galatSalin != null) {
            Toast.makeText(this, galatSalin, Toast.LENGTH_SHORT).show();
            return;
        }
        final String catatan = Kolaborasi.bacaCatatan(sementara);
        if (catatan == null) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Bukan paket kolaborasi"))
                    .setMessage(TeksUI.t("Berkas ini tidak berisi CATATAN.txt, jadi bukan ")
                            + TeksUI.t("paket kolaborasi. Untuk ZIP proyek biasa, pakai ")
                            + TeksUI.t("Impor proyek di Kelola Project."))
                    .setPositiveButton("OK", null)
                    .show();
            announce(TeksUI.t("Berkas ini bukan paket kolaborasi, tidak ada catatan perubahan di dalamnya."));
            sementara.delete();
            return;
        }
        String nama = ArsipProyek.namaProyekDariIsi(sementara);
        if (nama == null || nama.isEmpty()) {
            nama = ArsipProyek.namaProyekDariBerkas(sementara.getName());
        }
        final String namaProyek = nama;
        final File zipBalasan = sementara;
        new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Balasan untuk proyek \"") + namaProyek + "\"")
                .setMessage(TeksUI.t("Catatan perubahan dari teman:") + "\n" + "\n" + catatan
                        + "\n" + "\n" + TeksUI.t("Terapkan balasan ini ke proyek \"") + namaProyek + "\"?")
                .setPositiveButton("Terapkan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        terapkanBalasan(zipBalasan, namaProyek);
                    }
                })
                .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        zipBalasan.delete();
                    }
                })
                .show();
        announce(TeksUI.t("Balasan diterima. Catatan perubahan: ") + catatan);
    }

    private void terapkanBalasan(final File zip, final String nama) {
        final File tujuan = new File(BuatProyekHelper.root() + "/" + nama);
        if (tujuan.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle(TeksUI.t("Proyek sudah ada"))
                    .setMessage(TeksUI.t("Proyek \"") + nama + TeksUI.t("\" sudah ada. Timpa proyek ")
                            + TeksUI.t("lama dengan balasan ini?"))
                    .setPositiveButton("Timpa", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ArsipProyek.hapusRekursif(tujuan);
                            selesaikanTerapkan(zip, tujuan);
                        }
                    })
                    .setNegativeButton(TeksUI.t("Batal"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            zip.delete();
                        }
                    })
                    .show();
            announce(TeksUI.t("Proyek ") + nama + TeksUI.t(" sudah ada. Timpa proyek lama dengan balasan ini?"));
            return;
        }
        selesaikanTerapkan(zip, tujuan);
    }

    private void selesaikanTerapkan(File zip, File tujuan) {
        String galat = ArsipProyek.ekstrakZip(zip, tujuan);
        zip.delete();
        if (galat != null) {
            Toast.makeText(this, galat, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, TeksUI.t("Balasan diterapkan ke proyek \"") + tujuan.getName() + "\"",
                Toast.LENGTH_SHORT).show();
        announce(TeksUI.t("Balasan diterapkan ke proyek ") + tujuan.getName() + ".");
        muatDaftarProyek();
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
