package com.androlua;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pabrik Pengujian Unit Otomatis dan Generator Fuzzing Batas
 * (Fitur 9, daftar Gemini 2026-11-27).
 *
 * Pilih proyek dan berkas kode, tekan "Buat dan Jalankan Uji" — IDE
 * membaca tanda tangan fungsi, menghasilkan uji yang BISA DIJALANKAN,
 * mengompilasi salinan proyek (yang terinstrumentasi untuk cakupan)
 * dengan ECJ + D8, memuatnya dengan DexClassLoader, lalu menjalankan
 * uji itu sungguhan, mem-fuzz metode proyek sungguhan, dan menghitung
 * persentase cakupan baris yang sesungguhnya. Logika ada di PabrikUji
 * (murni Java, sudah diuji di komputer).
 *
 * BATAS: jalankan nyata hanya untuk berkas .java. Pustaka JUnit asli
 * tidak dibundel; uji yang dihasilkan memakai API yang sama dengan
 * JUnit (anotasi @Test + asersi) yang ditulis di dalam berkas uji itu
 * sendiri. Fuzzing menjalankan metode proyek di dalam proses IDE —
 * jalankan hanya pada proyek yang dipercaya.
 */
public class PabrikUjiActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private Spinner spProyek;
    private ArrayAdapter<String> adapterProyek;
    private final List<String> daftarProyek = new ArrayList<String>();
    private Spinner spBerkas;
    private ArrayAdapter<String> adapterBerkas;
    private final List<String> daftarBerkas = new ArrayList<String>();
    private TextView tvHasil;
    private Button bJalankan;
    private File dirAktif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            String pilihan = PengaturanActivity.baca(this)
                    .getString(PengaturanActivity.KUNCI_BAHASA, Bahasa.SISTEM);
            TeksUI.kodeAktif = Bahasa.kodeEfektif(pilihan, Locale.getDefault().getLanguage());
        } catch (Throwable ignored) {
        }
        setTitle(TeksUI.t("Pabrik Pengujian Unit dan Fuzzing"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Pabrik Pengujian Unit dan Fuzzing"), 20);
        label(r, TeksUI.t("Hasilkan uji unit dari tanda tangan fungsi dan gempur fungsi dengan masukan ekstrem — teks kosong, angka batas, dan karakter aneh — lalu lihat cakupannya."), false);

        label(r, TeksUI.t("Pilih proyek:"), false);
        spProyek = new Spinner(this);
        adapterProyek = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarProyek);
        adapterProyek.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProyek.setAdapter(adapterProyek);
        spProyek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                muatBerkas();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        r.addView(spProyek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        label(r, TeksUI.t("Pilih berkas kode:"), false);
        spBerkas = new Spinner(this);
        adapterBerkas = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, daftarBerkas);
        adapterBerkas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBerkas.setAdapter(adapterBerkas);
        r.addView(spBerkas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bJalankan = button(r, TeksUI.t("Buat dan Jalankan Uji"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mulai();
            }
        });

        label(r, TeksUI.t("Hasil:"), false);
        tvHasil = new TextView(this);
        tvHasil.setTextSize(14);
        tvHasil.setTextIsSelectable(true);
        tvHasil.setPadding(0, dp(4), 0, dp(4));
        tvHasil.setText(TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan") + " "
                + TeksUI.t("Buat dan Jalankan Uji") + ".");
        r.addView(tvHasil);

        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        muatDaftar();
        announce(TeksUI.t("Pabrik Pengujian Unit dan Fuzzing") + ". " + TeksUI.t("Belum ada hasil. Pilih proyek, lalu tekan")
                + " " + TeksUI.t("Buat dan Jalankan Uji") + ".");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SegarBahasa.periksa(this)) return;
        muatDaftar();
    }

    private void muatDaftar() {
        daftarProyek.clear();
        daftarProyek.addAll(InfoProyek.daftarNama(new File(ROOT)));
        if (adapterProyek != null) adapterProyek.notifyDataSetChanged();
        muatBerkas();
    }

    private void muatBerkas() {
        daftarBerkas.clear();
        dirAktif = null;
        if (spProyek != null && spProyek.getSelectedItem() != null) {
            dirAktif = new File(ROOT + "/" + String.valueOf(spProyek.getSelectedItem()));
            cariKode(dirAktif, dirAktif);
        }
        if (adapterBerkas != null) adapterBerkas.notifyDataSetChanged();
    }

    private void cariKode(File akar, File kini) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) cariKode(akar, f);
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")
                    || f.getName().endsWith(".py")) {
                daftarBerkas.add(f.getAbsolutePath().substring(akar.getAbsolutePath().length() + 1));
            }
        }
    }

    private void mulai() {
        if (dirAktif == null || spBerkas == null || spBerkas.getSelectedItem() == null) {
            toast(TeksUI.t("Pilih dulu proyeknya."));
            return;
        }
        final File berkas = new File(dirAktif, String.valueOf(spBerkas.getSelectedItem()));
        tvHasil.setText(TeksUI.t("Menjalankan uji proyek. Tunggu sebentar."));
        bJalankan.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String hasil = jalankan(berkas);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bJalankan.setEnabled(true);
                        tvHasil.setText(hasil);
                        announce(hasil);
                    }
                });
            }
        }, "PabrikUji").start();
    }

    /** Ambil deklarasi paket dari sumber ("" bila tanpa paket). */
    static String bacaPaket(String kode) {
        if (kode == null) return "";
        Matcher m = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;")
                .matcher(kode);
        if (m.find()) return m.group(1);
        return "";
    }

    /** Salin semua .java proyek ke dir kerja (rekursif, lewati bin/). */
    private int salinJava(File asal, File tujuan, File akar) {
        int jumlah = 0;
        File[] daftar = asal.listFiles();
        if (daftar == null) return 0;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (f.getName().equals("bin") || f.getName().startsWith(".")) continue;
                jumlah += salinJava(f, tujuan, akar);
            } else if (f.getName().endsWith(".java") && f.length() <= 500 * 1024) {
                String relatif = f.getAbsolutePath().substring(akar.getAbsolutePath().length() + 1);
                File keluar = new File(tujuan, relatif);
                if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
                if (salinBerkas(f, keluar)) jumlah++;
            }
        }
        return jumlah;
    }

    private boolean salinBerkas(File asal, File tujuan) {
        try {
            FileInputStream in = new FileInputStream(asal);
            FileOutputStream out = new FileOutputStream(tujuan);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void hapusRekursif(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] daftar = f.listFiles();
            if (daftar != null) {
                for (int i = 0; i < daftar.length; i++) hapusRekursif(daftar[i]);
            }
        }
        f.delete();
    }

    private String jalankan(File berkas) {
        String kode = bacaTeks(berkas);
        if (kode == null) return TeksUI.t("Berkas kode tidak bisa dibaca.");
        String namaKelas = berkas.getName().replace(".java", "").replace(".kt", "").replace(".py", "");
        String paket = bacaPaket(kode);
        List<PabrikUji.KasusUji> kasus = new ArrayList<PabrikUji.KasusUji>();
        int jumlahFungsi = 0;
        Matcher m = Pattern.compile(
                "\\b(?:public|private|protected|static|final|void|int|long|double|float|boolean|String)\\b[^;\\{\\}]*?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\)]*\\)")
                .matcher(kode);
        while (m.find()) {
            jumlahFungsi++;
            String tanda = m.group(0);
            kasus.addAll(PabrikUji.ujiDariTandaTangan(tanda));
            if (kasus.size() > 60) break;
        }
        // Fuzz contoh bawaan (fungsi contoh di PabrikUji) — tetap jalan.
        List<PabrikUji.HasilFuzz> fuzzTel = PabrikUji.fuzzTelepon();
        List<PabrikUji.HasilFuzz> fuzzDis = PabrikUji.fuzzDiskon();
        int contohLulus = 0;
        int contohGagal = 0;
        StringBuilder contohGagalTeks = new StringBuilder();
        for (int i = 0; i < fuzzTel.size(); i++) {
            if (fuzzTel.get(i).lulus) contohLulus++;
            else {
                contohGagal++;
                contohGagalTeks.append("\n- telepon ").append(fuzzTel.get(i).masukan)
                        .append(": ").append(fuzzTel.get(i).pesan);
            }
        }
        for (int i = 0; i < fuzzDis.size(); i++) {
            if (fuzzDis.get(i).lulus) contohLulus++;
            else {
                contohGagal++;
                contohGagalTeks.append("\n- diskon ").append(fuzzDis.get(i).masukan)
                        .append(": ").append(fuzzDis.get(i).pesan);
            }
        }
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Baris kode:")).append(" ").append(PabrikUji.hitungBarisKode(kode)).append(". ");
        b.append(TeksUI.t("Fungsi ditemukan:")).append(" ").append(jumlahFungsi).append(". ");
        b.append(TeksUI.t("Kasus uji dibuat:")).append(" ").append(kasus.size()).append(".");

        boolean hanyaJava = berkas.getName().endsWith(".java");
        if (!hanyaJava) {
            b.append("\n").append(TeksUI.t("Jalankan nyata hanya untuk berkas Java. Untuk Kotlin dan Python, yang ditampilkan hanya kasus uji dan fuzz contoh."));
            b.append("\n").append(TeksUI.t("Fuzz contoh:")).append(" ")
                    .append(TeksUI.t("Uji lulus:")).append(" ").append(contohLulus).append(". ")
                    .append(TeksUI.t("Uji gagal:")).append(" ").append(contohGagal).append(".");
            b.append(contohGagalTeks.toString());
            return b.toString();
        }

        // 1) Tulis berkas uji yang dihasilkan ke proyek (perilaku lama).
        String sumber = PabrikUji.hasilkanSumberUji(paket, namaKelas, kasus);
        File keluar = new File(berkas.getParentFile(), "Uji" + namaKelas + ".java");
        boolean tulisOk = tulisTeks(keluar, sumber);
        String namaBerkasUji = "Uji" + namaKelas;
        String namaKelasUji = (paket.length() > 0 ? paket + "." : "") + namaBerkasUji;
        String namaKelasProyek = (paket.length() > 0 ? paket + "." : "") + namaKelas;

        // 2) Salin proyek ke dir kerja; timpa salinan berkas terpilih dengan
        // versi terinstrumentasi (untuk cakupan baris).
        File dirKerja = new File(getCacheDir(), "pabrik-uji/kerja");
        hapusRekursif(dirKerja);
        dirKerja.mkdirs();
        int disalin = salinJava(dirAktif, dirKerja, dirAktif);
        PabrikUji.HasilInstrumentasi instr = PabrikUji.instrumentasi(
                kode, berkas.getName(), namaBerkasUji);
        String relatifTerpilih = berkas.getAbsolutePath()
                .substring(dirAktif.getAbsolutePath().length() + 1);
        File salinanTerpilih = new File(dirKerja, relatifTerpilih);
        boolean instrOk = false;
        if (salinanTerpilih.getParentFile() != null) salinanTerpilih.getParentFile().mkdirs();
        instrOk = tulisTeks(salinanTerpilih, instr.kode);

        // 3) Kompilasi ECJ + D8 atas salinan kerja.
        byte[] dex;
        try {
            dex = BangunJava.kompilasiUntukRakit(this, dirKerja);
        } catch (Throwable t) {
            String pesan = t.getMessage();
            if (pesan == null) pesan = t.toString();
            if (pesan.length() > 1500) pesan = pesan.substring(0, 1500);
            b.append("\n").append(TeksUI.t("Kompilasi gagal. Galat:")).append("\n").append(pesan);
            b.append("\n").append(TeksUI.t("Berkas uji tetap ditulis:")).append(" ")
                    .append(tulisOk ? keluar.getName() : TeksUI.t("gagal ditulis"));
            b.append("\n").append(TeksUI.t("Fuzz contoh:")).append(" ")
                    .append(TeksUI.t("Uji lulus:")).append(" ").append(contohLulus).append(". ")
                    .append(TeksUI.t("Uji gagal:")).append(" ").append(contohGagal).append(".");
            b.append(contohGagalTeks.toString());
            hapusRekursif(dirKerja);
            return b.toString();
        }
        b.append("\n").append(TeksUI.t("Kompilasi berhasil:")).append(" ")
                .append(disalin).append(" ").append(TeksUI.t("berkas Java,"))
                .append(" ").append(dex.length).append(" ").append(TeksUI.t("bita dex."));

        // 4) Muat dex dan jalankan uji + fuzz + cakupan.
        File dirDex = new File(getCacheDir(), "pabrik-uji");
        dirDex.mkdirs();
        File berkasDex = new File(dirDex, "uji.dex");
        File dirOdex = new File(dirDex, "odex");
        dirOdex.mkdirs();
        try {
            FileOutputStream o = new FileOutputStream(berkasDex);
            o.write(dex);
            o.close();
        } catch (Throwable t) {
            b.append("\n").append(TeksUI.t("Dex tidak bisa ditulis:")).append(" ").append(t.toString());
            hapusRekursif(dirKerja);
            return b.toString();
        }
        ClassLoader pemuat;
        try {
            pemuat = new DexClassLoader(berkasDex.getAbsolutePath(),
                    dirOdex.getAbsolutePath(), null, getClassLoader());
        } catch (Throwable t) {
            b.append("\n").append(TeksUI.t("Dex tidak bisa dimuat:")).append(" ").append(t.toString());
            hapusRekursif(dirKerja);
            return b.toString();
        }
        Class<?> kelasUji;
        try {
            kelasUji = Class.forName(namaKelasUji, true, pemuat);
        } catch (Throwable t) {
            b.append("\n").append(TeksUI.t("Kelas uji tidak ditemukan setelah kompilasi."));
            hapusRekursif(dirKerja);
            return b.toString();
        }
        List<PabrikUji.HasilUji> hasilUji = PabrikUji.jalankanUji(kelasUji);
        int ujiLulus = 0;
        int ujiGagal = 0;
        StringBuilder ujiGagalTeks = new StringBuilder();
        for (int i = 0; i < hasilUji.size(); i++) {
            if (hasilUji.get(i).lulus) ujiLulus++;
            else {
                ujiGagal++;
                ujiGagalTeks.append("\n- ").append(hasilUji.get(i).nama).append(": ")
                        .append(hasilUji.get(i).pesan);
            }
        }
        b.append("\n").append(TeksUI.t("Uji yang dijalankan:")).append(" ")
                .append(TeksUI.t("Uji lulus:")).append(" ").append(ujiLulus).append(". ")
                .append(TeksUI.t("Uji gagal:")).append(" ").append(ujiGagal).append(".");
        b.append(ujiGagalTeks.toString());
        List<String> aktual = PabrikUji.ambilHasilAktual(kelasUji);
        if (!aktual.isEmpty()) {
            b.append("\n").append(TeksUI.t("Nilai aktual:"));
            int tampil = Math.min(aktual.size(), 10);
            for (int i = 0; i < tampil; i++) {
                b.append("\n- ").append(aktual.get(i));
            }
            if (aktual.size() > tampil) {
                b.append("\n- ... ").append(aktual.size() - tampil).append(" ")
                        .append(TeksUI.t("lagi"));
            }
        }
        // Cakupan baris.
        Set<String> kena = PabrikUji.ambilCakupan(kelasUji);
        if (instrOk && instr.barisTerukur.size() > 0) {
            PabrikUji.HasilCakupan cakupan = PabrikUji.hitungCakupan(
                    instr.barisTerukur, kena, berkas.getName());
            b.append("\n").append(TeksUI.t("Cakupan baris:")).append(" ")
                    .append(cakupan.persen).append("% (").append(cakupan.terkena)
                    .append("/").append(cakupan.terukur).append(" ")
                    .append(TeksUI.t("baris terukur")).append(").");
            if (!cakupan.takTerkena.isEmpty()) {
                b.append("\n").append(TeksUI.t("Baris tidak tercakup:")).append(" ");
                int tampil = Math.min(cakupan.takTerkena.size(), 20);
                for (int i = 0; i < tampil; i++) {
                    if (i > 0) b.append(", ");
                    b.append(cakupan.takTerkena.get(i));
                }
                if (cakupan.takTerkena.size() > tampil) {
                    b.append(", ... ").append(cakupan.takTerkena.size() - tampil)
                            .append(" ").append(TeksUI.t("lagi"));
                }
            }
        } else {
            b.append("\n").append(TeksUI.t("Cakupan baris:")).append(" ")
                    .append(TeksUI.t("tidak dapat diukur (tidak ada baris yang bisa disisipi pemanggil)."));
        }
        if (instr.barisTakTerukur.size() > 0) {
            b.append("\n").append(TeksUI.t("Baris tidak terukur:")).append(" ")
                    .append(instr.barisTakTerukur.size()).append(" ")
                    .append(TeksUI.t("baris (lanjutan pernyataan, deklarasi, atau anotasi)."));
        }
        // Fuzz atas metode proyek sungguhan.
        try {
            Class<?> kelasProyek = Class.forName(namaKelasProyek, true, pemuat);
            PabrikUji.HasilFuzzKelas fuzz = PabrikUji.fuzzKelas(kelasProyek);
            int fuzzLulus = 0;
            int fuzzGagal = 0;
            StringBuilder fuzzGagalTeks = new StringBuilder();
            for (int i = 0; i < fuzz.hasil.size(); i++) {
                if (fuzz.hasil.get(i).lulus) fuzzLulus++;
                else {
                    fuzzGagal++;
                    if (fuzzGagal <= 10) {
                        fuzzGagalTeks.append("\n- ").append(fuzz.hasil.get(i).metode)
                                .append("(").append(fuzz.hasil.get(i).masukan).append("): ")
                                .append(fuzz.hasil.get(i).pesan);
                    }
                }
            }
            b.append("\n").append(TeksUI.t("Fuzz fungsi proyek:")).append(" ")
                    .append(fuzzLulus).append(" ").append(TeksUI.t("lulus,"))
                    .append(" ").append(fuzzGagal).append(" ").append(TeksUI.t("gagal."))
                    .append(" ").append(TeksUI.t("Dilewati:")).append(" ")
                    .append(fuzz.dilewati.size()).append(".");
            b.append(fuzzGagalTeks.toString());
            if (!fuzz.dilewati.isEmpty()) {
                b.append("\n").append(TeksUI.t("Fuzz dilewati:"));
                int tampil = Math.min(fuzz.dilewati.size(), 5);
                for (int i = 0; i < tampil; i++) {
                    b.append("\n- ").append(fuzz.dilewati.get(i));
                }
            }
        } catch (Throwable t) {
            b.append("\n").append(TeksUI.t("Fuzz fungsi proyek:")).append(" ")
                    .append(TeksUI.t("kelas proyek tidak bisa dimuat:")).append(" ")
                    .append(t.toString());
        }
        b.append("\n").append(TeksUI.t("Fuzz contoh:")).append(" ")
                .append(TeksUI.t("Uji lulus:")).append(" ").append(contohLulus).append(". ")
                .append(TeksUI.t("Uji gagal:")).append(" ").append(contohGagal).append(".");
        b.append(contohGagalTeks.toString());
        b.append("\n").append(TeksUI.t("Berkas uji ditulis:")).append(" ").append(keluar.getName());
        hapusRekursif(dirKerja);
        return b.toString();
    }

    private String bacaTeks(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean tulisTeks(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            out.write(isi);
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

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
