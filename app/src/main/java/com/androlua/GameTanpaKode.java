package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pembuat Game Tanpa Kode (Tahap 3E).
 *
 * Pengguna memilih jenis game + mengisi pengaturan (nama game, jumlah
 * soal/level/bintang, pesan menang) — kelas ini membuat proyek game
 * Java sungguhan yang bisa langsung dibangun. Game dibuat dengan
 * menimpa MainActivity.java proyek Java yang sudah dibuat (lewat
 * BuatProyekHelper.buat di GameActivity) dengan kode game.
 *
 * Ronde 3: Lua dihapus — game yang dulu ditulis sebagai main.lua kini
 * ditulis sebagai MainActivity.java (pola TemplateProyek ISI_KALKULATOR_JAVA:
 * Activity Java murni, UI programatik, OnClickListener anonim). Isi game
 * disimpan sebagai konstanta String[] di bawah. Placeholder di sumber:
 * {{PAKET}} (nama paket proyek), {{NAMA}} (nama game),
 * {{JUMLAH}} (jumlah soal/level/bintang) dan {{PESAN_MENANG}} (pesan menang)
 * diganti saat penerapan.
 *
 * Murni Java (java.io, tanpa API Android) supaya bisa diuji ulang di JRE.
 */
public class GameTanpaKode {

    /** Satu jenis game yang bisa dibuat. */
    public static class Info {
        /** Id unik jenis game. */
        public final String id;
        /** Nama tampil. */
        public final String nama;
        /** Deskripsi singkat. */
        public final String deskripsi;
        /** Label pengaturan jumlah ("Jumlah level" / "Jumlah soal" / ...). */
        public final String labelJumlah;
        /** Batas jumlah (maksimal). */
        public final int jumlahMaks;
        /** Jumlah bawaan. */
        public final int jumlahBawaan;

        public Info(String id, String nama, String deskripsi, String labelJumlah,
                    int jumlahMaks, int jumlahBawaan) {
            this.id = id;
            this.nama = nama;
            this.deskripsi = deskripsi;
            this.labelJumlah = labelJumlah;
            this.jumlahMaks = jumlahMaks;
            this.jumlahBawaan = jumlahBawaan;
        }
    }

    public static final Info[] SEMUA = {
        new Info("tebak-angka", "Tebak Angka",
                "Tebak angka rahasia. Setiap level, rentang angkanya membesar.",
                "Jumlah level", 10, 3),
        new Info("kuis", "Kuis",
                "Kuis pilihan ganda: jawab soal, kumpulkan skor.",
                "Jumlah soal", 10, 5),
        new Info("tangkap-bintang", "Tangkap Bintang",
                "Ketuk bintang yang berpindah-pindah sampai target tercapai.",
                "Jumlah bintang", 20, 10),
    };

    public static Info cari(String id) {
        if (id == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    // @@GAME_AWAL@@
    private static final String[] ISI_TEBAK_ANGKA_JAVA = {
        "package {{PAKET}};",
        "",
        "import android.app.Activity;",
        "import android.os.Bundle;",
        "import android.text.InputType;",
        "import android.view.View;",
        "import android.widget.Button;",
        "import android.widget.EditText;",
        "import android.widget.LinearLayout;",
        "import android.widget.TextView;",
        "",
        "// Tebak Angka — game tanpa kode dari Create Aplikasi (Tahap 3E).",
        "// Tebak angka rahasia. Setiap level, rentang angkanya membesar.",
        "// Pengaturan dari IDE: {{JUMLAH}} = jumlah level, {{PESAN_MENANG}} = pesan menang.",
        "public class MainActivity extends Activity {",
        "    private static final int JUMLAH_LEVEL = {{JUMLAH}};",
        "    private static final String PESAN_MENANG = \"{{PESAN_MENANG}}\";",
        "",
        "    private int level = 1;",
        "    private int target = 0;",
        "    private int tebakan = 0;",
        "    private boolean menang = false;",
        "",
        "    private TextView petunjuk;",
        "    private TextView hasil;",
        "    private EditText angka;",
        "    private Button tombol;",
        "",
        "    @Override",
        "    protected void onCreate(Bundle savedInstanceState) {",
        "        super.onCreate(savedInstanceState);",
        "        setTitle(\"{{NAMA}}\");",
        "",
        "        LinearLayout r = new LinearLayout(this);",
        "        r.setOrientation(LinearLayout.VERTICAL);",
        "        r.setPadding(48, 36, 48, 36);",
        "",
        "        TextView judul = new TextView(this);",
        "        judul.setText(\"Tebak Angka\");",
        "        judul.setTextSize(20);",
        "        r.addView(judul);",
        "",
        "        petunjuk = new TextView(this);",
        "        r.addView(petunjuk);",
        "",
        "        angka = new EditText(this);",
        "        angka.setHint(\"Tulis tebakanmu\");",
        "        angka.setInputType(InputType.TYPE_CLASS_NUMBER);",
        "        r.addView(angka);",
        "",
        "        hasil = new TextView(this);",
        "        hasil.setTextSize(18);",
        "        r.addView(hasil);",
        "",
        "        tombol = new Button(this);",
        "        tombol.setText(\"Tebak\");",
        "        tombol.setOnClickListener(new View.OnClickListener() {",
        "            @Override",
        "            public void onClick(View v) {",
        "                tebak();",
        "            }",
        "        });",
        "        r.addView(tombol);",
        "",
        "        rondeBaru();",
        "        setContentView(r);",
        "    }",
        "",
        "    private int batasAtas() {",
        "        return 100 * level;",
        "    }",
        "",
        "    private void rondeBaru() {",
        "        target = 1 + (int) (Math.random() * batasAtas());",
        "        tebakan = 0;",
        "        petunjuk.setText(\"Level \" + level + \" dari \" + JUMLAH_LEVEL",
        "                + \": tebak angka 1 sampai \" + batasAtas() + \".\");",
        "        hasil.setText(\"Belum ada tebakan.\");",
        "        angka.setText(\"\");",
        "    }",
        "",
        "    private void tebak() {",
        "        if (menang) {",
        "            level = 1;",
        "            menang = false;",
        "            tombol.setText(\"Tebak\");",
        "            rondeBaru();",
        "            return;",
        "        }",
        "        int t;",
        "        try {",
        "            t = Integer.parseInt(angka.getText().toString().trim());",
        "        } catch (Exception e) {",
        "            hasil.setText(\"Isi angka dulu.\");",
        "            return;",
        "        }",
        "        tebakan = tebakan + 1;",
        "        if (t < target) {",
        "            hasil.setText(\"Terlalu kecil! Coba angka yang lebih besar. (Tebakan ke-\" + tebakan + \")\");",
        "        } else if (t > target) {",
        "            hasil.setText(\"Terlalu besar! Coba angka yang lebih kecil. (Tebakan ke-\" + tebakan + \")\");",
        "        } else {",
        "            if (level < JUMLAH_LEVEL) {",
        "                hasil.setText(\"Benar! Lanjut ke level \" + (level + 1) + \".\");",
        "                level = level + 1;",
        "                rondeBaru();",
        "            } else {",
        "                menang = true;",
        "                tombol.setText(\"Main Lagi\");",
        "                hasil.setText(PESAN_MENANG + \" Kamu menang dalam \" + tebakan",
        "                        + \" tebakan di level terakhir!\");",
        "            }",
        "        }",
        "    }",
        "}",
    };

    private static final String[] ISI_KUIS_JAVA = {
        "package {{PAKET}};",
        "",
        "import android.app.Activity;",
        "import android.os.Bundle;",
        "import android.view.View;",
        "import android.widget.Button;",
        "import android.widget.LinearLayout;",
        "import android.widget.TextView;",
        "",
        "// Kuis — game tanpa kode dari Create Aplikasi (Tahap 3E).",
        "// Jawab soal pilihan ganda. Pengaturan dari IDE: {{JUMLAH}} = jumlah soal",
        "// (maksimal 10, diambil dari awal daftar), {{PESAN_MENANG}} = pesan menang.",
        "public class MainActivity extends Activity {",
        "    private static final int JUMLAH_SOAL = {{JUMLAH}};",
        "    private static final String PESAN_MENANG = \"{{PESAN_MENANG}}\";",
        "",
        "    private static final String[][] SOAL = {",
        "        {\"Berapa hasil 7 + 8?\", \"13\", \"14\", \"15\", \"16\"},",
        "        {\"Ibu kota Indonesia?\", \"Jakarta\", \"Bandung\", \"Surabaya\", \"Medan\"},",
        "        {\"Hewan apa yang berkantung?\", \"Kucing\", \"Kanguru\", \"Gajah\", \"Singa\"},",
        "        {\"Berapa 9 x 6?\", \"52\", \"54\", \"56\", \"48\"},",
        "        {\"Warna bendera Indonesia?\", \"Merah Putih\", \"Biru Putih\", \"Hijau Kuning\", \"Hitam Putih\"},",
        "        {\"Planet terdekat dari Matahari?\", \"Venus\", \"Mars\", \"Merkurius\", \"Bumi\"},",
        "        {\"Berapa 100 dibagi 4?\", \"20\", \"25\", \"30\", \"40\"},",
        "        {\"Bahasa resmi Indonesia?\", \"Inggris\", \"Indonesia\", \"Jawa\", \"Arab\"},",
        "        {\"Berapa jumlah hari dalam seminggu?\", \"5\", \"6\", \"7\", \"8\"},",
        "        {\"Sungai terpanjang di dunia?\", \"Amazon\", \"Nil\", \"Yangtze\", \"Mississippi\"},",
        "    };",
        "    // Indeks jawaban benar (0-based) untuk tiap soal di atas.",
        "    private static final int[] JAWABAN = {2, 0, 1, 1, 0, 2, 1, 1, 2, 1};",
        "",
        "    private int nomor = 1;",
        "    private int skor = 0;",
        "    private boolean selesai = false;",
        "",
        "    private TextView soal;",
        "    private TextView status;",
        "    private TextView skorView;",
        "    private Button[] tombol = new Button[4];",
        "",
        "    @Override",
        "    protected void onCreate(Bundle savedInstanceState) {",
        "        super.onCreate(savedInstanceState);",
        "        setTitle(\"{{NAMA}}\");",
        "",
        "        LinearLayout r = new LinearLayout(this);",
        "        r.setOrientation(LinearLayout.VERTICAL);",
        "        r.setPadding(48, 36, 48, 36);",
        "",
        "        TextView judul = new TextView(this);",
        "        judul.setText(\"Kuis\");",
        "        judul.setTextSize(20);",
        "        r.addView(judul);",
        "",
        "        soal = new TextView(this);",
        "        soal.setTextSize(18);",
        "        r.addView(soal);",
        "",
        "        status = new TextView(this);",
        "        r.addView(status);",
        "",
        "        skorView = new TextView(this);",
        "        skorView.setText(\"Skor: 0\");",
        "        r.addView(skorView);",
        "",
        "        for (int i = 0; i < 4; i++) {",
        "            final int pilihan = i;",
        "            Button t = new Button(this);",
        "            t.setTextSize(16);",
        "            t.setOnClickListener(new View.OnClickListener() {",
        "                @Override",
        "                public void onClick(View v) {",
        "                    jawab(pilihan);",
        "                }",
        "            });",
        "            r.addView(t);",
        "            tombol[i] = t;",
        "        }",
        "",
        "        tampilSoal();",
        "        setContentView(r);",
        "    }",
        "",
        "    private void tampilSoal() {",
        "        String[] s = SOAL[nomor - 1];",
        "        soal.setText(\"Soal \" + nomor + \" dari \" + JUMLAH_SOAL + \": \" + s[0]);",
        "        for (int i = 0; i < 4; i++) {",
        "            tombol[i].setText(s[1 + i]);",
        "        }",
        "    }",
        "",
        "    private void jawab(int pilihan) {",
        "        if (selesai) {",
        "            nomor = 1;",
        "            skor = 0;",
        "            selesai = false;",
        "            skorView.setText(\"Skor: 0\");",
        "            status.setText(\"\");",
        "            tampilSoal();",
        "            return;",
        "        }",
        "        String[] s = SOAL[nomor - 1];",
        "        if (pilihan == JAWABAN[nomor - 1]) {",
        "            skor = skor + 1;",
        "            status.setText(\"Benar!\");",
        "        } else {",
        "            status.setText(\"Salah. Jawaban benar: \" + s[1 + JAWABAN[nomor - 1]] + \".\");",
        "        }",
        "        skorView.setText(\"Skor: \" + skor);",
        "        if (nomor < JUMLAH_SOAL) {",
        "            nomor = nomor + 1;",
        "            tampilSoal();",
        "        } else {",
        "            selesai = true;",
        "            for (int i = 0; i < 4; i++) {",
        "                tombol[i].setText(\"Main Lagi\");",
        "            }",
        "            if (skor * 2 >= JUMLAH_SOAL) {",
        "                soal.setText(PESAN_MENANG + \" Skormu \" + skor + \" dari \" + JUMLAH_SOAL + \".\");",
        "            } else {",
        "                soal.setText(\"Kuis selesai. Skormu \" + skor + \" dari \" + JUMLAH_SOAL",
        "                        + \". Coba lagi untuk menang!\");",
        "            }",
        "        }",
        "    }",
        "}",
    };

    private static final String[] ISI_TANGKAP_BINTANG_JAVA = {
        "package {{PAKET}};",
        "",
        "import android.app.Activity;",
        "import android.os.Bundle;",
        "import android.view.View;",
        "import android.widget.Button;",
        "import android.widget.FrameLayout;",
        "import android.widget.LinearLayout;",
        "import android.widget.TextView;",
        "",
        "// Tangkap Bintang — game tanpa kode dari Create Aplikasi (Tahap 3E).",
        "// Ketuk bintang yang berpindah-pindah. Pengaturan dari IDE: {{JUMLAH}} =",
        "// jumlah bintang yang harus ditangkap, {{PESAN_MENANG}} = pesan menang.",
        "public class MainActivity extends Activity {",
        "    private static final int JUMLAH_BINTANG = {{JUMLAH}};",
        "    private static final String PESAN_MENANG = \"{{PESAN_MENANG}}\";",
        "",
        "    private int skor = 0;",
        "    private boolean menang = false;",
        "",
        "    private TextView info;",
        "    private Button bintang;",
        "",
        "    @Override",
        "    protected void onCreate(Bundle savedInstanceState) {",
        "        super.onCreate(savedInstanceState);",
        "        setTitle(\"{{NAMA}}\");",
        "",
        "        LinearLayout r = new LinearLayout(this);",
        "        r.setOrientation(LinearLayout.VERTICAL);",
        "        r.setPadding(48, 36, 48, 36);",
        "",
        "        TextView judul = new TextView(this);",
        "        judul.setText(\"Tangkap Bintang\");",
        "        judul.setTextSize(20);",
        "        r.addView(judul);",
        "",
        "        info = new TextView(this);",
        "        info.setTextSize(18);",
        "        info.setText(\"Tangkap bintang! Skor: 0 dari \" + JUMLAH_BINTANG);",
        "        r.addView(info);",
        "",
        "        FrameLayout area = new FrameLayout(this);",
        "        LinearLayout.LayoutParams lpArea = new LinearLayout.LayoutParams(",
        "                LinearLayout.LayoutParams.MATCH_PARENT,",
        "                LinearLayout.LayoutParams.MATCH_PARENT);",
        "        area.setLayoutParams(lpArea);",
        "        r.addView(area);",
        "",
        "        bintang = new Button(this);",
        "        bintang.setText(\"\\u2605\");",
        "        bintang.setTextSize(36);",
        "        FrameLayout.LayoutParams lpBintang = new FrameLayout.LayoutParams(",
        "                FrameLayout.LayoutParams.WRAP_CONTENT,",
        "                FrameLayout.LayoutParams.WRAP_CONTENT);",
        "        bintang.setLayoutParams(lpBintang);",
        "        bintang.setOnClickListener(new View.OnClickListener() {",
        "            @Override",
        "            public void onClick(View v) {",
        "                ketukBintang();",
        "            }",
        "        });",
        "        area.addView(bintang);",
        "",
        "        pindah();",
        "        setContentView(r);",
        "    }",
        "",
        "    private void pindah() {",
        "        int lebar = getResources().getDisplayMetrics().widthPixels;",
        "        int tinggi = getResources().getDisplayMetrics().heightPixels;",
        "        int x = (int) (Math.random() * Math.max(0, lebar - 240));",
        "        int y = (int) (Math.random() * Math.max(0, tinggi - 700));",
        "        bintang.setX(x);",
        "        bintang.setY(y);",
        "    }",
        "",
        "    private void ketukBintang() {",
        "        if (menang) {",
        "            skor = 0;",
        "            menang = false;",
        "            bintang.setText(\"\\u2605\");",
        "            info.setText(\"Tangkap bintang! Skor: 0 dari \" + JUMLAH_BINTANG);",
        "            pindah();",
        "            return;",
        "        }",
        "        skor = skor + 1;",
        "        if (skor >= JUMLAH_BINTANG) {",
        "            menang = true;",
        "            bintang.setText(\"Main Lagi\");",
        "            info.setText(PESAN_MENANG + \" Kamu menangkap \" + JUMLAH_BINTANG + \" bintang!\");",
        "        } else {",
        "            info.setText(\"Tangkap bintang! Skor: \" + skor + \" dari \" + JUMLAH_BINTANG);",
        "            pindah();",
        "        }",
        "    }",
        "}",
    };
    // @@GAME_AKHIR@@

    /**
     * Menerapkan game ke proyek Java yang SUDAH dibuat (folder proyek ada).
     * Menulis MainActivity.java baru (paket + judul + badan game dengan
     * placeholder terisi), menimpa MainActivity.java bawaan proyek.
     * Mengembalikan null bila berhasil, atau pesan galat bila gagal.
     */
    public static String terapkanGame(File dirProyek, String gameId, String namaGame,
                                     int jumlah, String pesanMenang) {
        Info info = cari(gameId);
        if (info == null) return "Jenis game tidak dikenal: " + gameId;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        if (jumlah < 1) jumlah = 1;
        if (jumlah > info.jumlahMaks) jumlah = info.jumlahMaks;
        if (namaGame == null || namaGame.trim().length() == 0) namaGame = info.nama;
        if (pesanMenang == null || pesanMenang.trim().length() == 0) {
            pesanMenang = "Selamat, kamu menang!";
        }
        String paket = InfoProyek.namaPaket(dirProyek);
        if (paket == null || paket.isEmpty()) {
            return "Nama paket proyek tidak terbaca dari init.lua.";
        }
        String[] badan;
        if ("tebak-angka".equals(gameId)) {
            badan = ISI_TEBAK_ANGKA_JAVA;
        } else if ("kuis".equals(gameId)) {
            badan = ISI_KUIS_JAVA;
        } else if ("tangkap-bintang".equals(gameId)) {
            badan = ISI_TANGKAP_BINTANG_JAVA;
        } else {
            return "Jenis game tidak dikenal: " + gameId;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < badan.length; i++) {
                String baris = badan[i];
                baris = baris.replace("{{PAKET}}", paket);
                baris = baris.replace("{{NAMA}}", escapeJava(namaGame));
                baris = baris.replace("{{JUMLAH}}", Integer.toString(jumlah));
                baris = baris.replace("{{PESAN_MENANG}}", escapeJava(pesanMenang));
                sb.append(baris).append("\n");
            }
            String isi = sb.toString();
            if (isi.indexOf("{{") >= 0) {
                return "Game gagal diterapkan: masih ada placeholder yang belum terisi.";
            }
            File target = new File(dirProyek,
                    "app/src/main/java/" + paket.replace('.', '/') + "/MainActivity.java");
            return tulisBerkas(target, isi);
        } catch (Exception e) {
            return "Gagal menerapkan game: " + e.getMessage();
        }
    }

    /** Loloskan teks supaya aman di dalam string Java ("...", \n, \r). */
    private static String escapeJava(String s) {
        if (s == null) s = "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String tulisBerkas(File f, String isi) {
        try {
            File induk = f.getParentFile();
            if (induk != null) induk.mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            if (!f.isFile() || f.length() == 0) {
                return "Berkas hasil kosong: " + f.getAbsolutePath();
            }
            return null;
        } catch (Exception e) {
            return "Gagal menulis " + f.getAbsolutePath() + ": " + e.getMessage();
        }
    }
}
