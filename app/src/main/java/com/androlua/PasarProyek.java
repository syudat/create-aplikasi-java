package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pasar Proyek (Tahap 3D) — daftar proyek contoh bawaan IDE.
 *
 * Pasar ONLINE sungguhan butuh server, jadi pasar ini LOKAL dulu:
 * proyek contoh bawaan yang bisa dipasang ke Download/project dengan
 * satu ketuk. Enam item pertama memakai template siap pakai yang sudah
 * ada (TemplateProyek, Tahap 3C); dua item terakhir hanya ada di pasar
 * (Jam Digital Lua + Kuis Web HTML) dan diterapkan oleh kelas ini.
 *
 * Murni Java (java.io, tanpa API Android) supaya bisa diuji ulang di
 * JRE. MarketActivity memakai pasang() lewat jembatan buatProyek yang
 * sama seperti CreateProjectActivity, lalu memanggil terapkanPasar()
 * untuk item pasar saja.
 */
public class PasarProyek {

    /** Satu proyek contoh di pasar. */
    public static class Item {
        /** Id unik item pasar. */
        public final String id;
        /** Nama tampil. */
        public final String nama;
        /** Deskripsi singkat. */
        public final String deskripsi;
        /** Jenis proyek ("lua", "java", "kotlin", "html", "situs", "compose", "python"). */
        public final String tipe;
        /** Id TemplateProyek bila item ini memakai template (null bila pasar saja). */
        public final String templateId;

        public Item(String id, String nama, String deskripsi, String tipe, String templateId) {
            this.id = id;
            this.nama = nama;
            this.deskripsi = deskripsi;
            this.tipe = tipe;
            this.templateId = templateId;
        }
    }

    public static final Item[] SEMUA = {
        new Item("pasar-kalkulator", "Kalkulator",
                "Kalkulator sederhana: tambah, kurang, kali, bagi. Proyek Lua.",
                "lua", "kalkulator"),
        new Item("pasar-catatan", "Catatan",
                "Catatan: tulis, simpan, dan muat lagi. Proyek Java.",
                "java", "catatan"),
        new Item("pasar-tugas", "Daftar Tugas",
                "Daftar tugas: tambah dan hapus dengan ketukan. Proyek Kotlin.",
                "kotlin", "tugas"),
        new Item("pasar-kalkulator-web", "Kalkulator Web",
                "Kalkulator di halaman web (HTML + JavaScript). Proyek HTML.",
                "html", "kalkulator-web"),
        new Item("pasar-profil", "Situs Profil",
                "Situs profil sederhana (Beranda, Tentang, Kontak). Proyek Situs Web.",
                "situs", "profil"),
        new Item("pasar-penghitung", "Penghitung",
                "Penghitung angka dengan tombol tambah, kurang, dan nol. Proyek Compose.",
                "compose", "penghitung"),
        new Item("pasar-jam", "Jam Digital",
                "Jam digital: menampilkan jam sekarang dan tombol Perbarui. Proyek Lua.",
                "lua", null),
        new Item("pasar-kuis", "Kuis Web",
                "Kuis tiga soal dengan nilai langsung. Proyek HTML.",
                "html", null),
    };

    public static Item cari(String id) {
        if (id == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    /** Badan Lua Jam Digital (tanpa kepala — kepala ditambah terapkanPasar). */
    static final String[] BADAN_JAM = {
        "-- Jam Digital — proyek contoh dari Pasar Proyek (Tahap 3D).",
        "local jam = TextView(activity)",
        "jam.setText(os.date(\"%H:%M:%S\"))",
        "jam.setTextSize(32)",
        "",
        "local r = LinearLayout(activity)",
        "r.setOrientation(1)",
        "r.setPadding(48, 36, 48, 36)",
        "r.addView(jam)",
        "",
        "local tombol = Button(activity)",
        "tombol.setText(\"Perbarui Jam\")",
        "tombol.onClick = function() jam.setText(os.date(\"%H:%M:%S\")) end",
        "r.addView(tombol)",
        "",
        "activity.setContentView(r)",
    };

    /** Isi index.html Kuis Web. */
    static final String[] ISI_KUIS = {
        "<!DOCTYPE html>",
        "<html lang=\"id\">",
        "<head><meta charset=\"utf-8\"><title>Kuis Web</title></head>",
        "<body>",
        "<h1>Kuis Web</h1>",
        "<p>1. Berapa 2 + 3? <button onclick=\"jawab(1, 5)\">5</button> <button onclick=\"jawab(1, 4)\">4</button></p>",
        "<p>2. Ibu kota Indonesia? <button onclick=\"jawab(2, 'Jakarta')\">Jakarta</button> <button onclick=\"jawab(2, 'Bandung')\">Bandung</button></p>",
        "<p>3. Warna langit siang hari? <button onclick=\"jawab(3, 'Biru')\">Biru</button> <button onclick=\"jawab(3, 'Hijau')\">Hijau</button></p>",
        "<p id=\"nilai\">Nilai: 0 dari 3</p>",
        "<script>",
        "var benar = [5, 'Jakarta', 'Biru'];",
        "var sudah = [false, false, false];",
        "var nilai = 0;",
        "function jawab(no, j) {",
        "  if (sudah[no - 1]) return;",
        "  sudah[no - 1] = true;",
        "  if (j === benar[no - 1]) nilai++;",
        "  document.getElementById('nilai').textContent = 'Nilai: ' + nilai + ' dari 3';",
        "}",
        "</script>",
        "</body>",
        "</html>",
    };

    /**
     * Menerapkan item pasar ke proyek yang SUDAH dibuat (folder proyek ada).
     * Item template didelegasikan ke TemplateProyek; item pasar saja
     * ditulis langsung oleh kelas ini. Mengembalikan null bila berhasil,
     * atau pesan galat bila gagal.
     */
    public static String terapkanPasar(File dirProyek, String itemId,
                                       String namaPaket, String namaAplikasi) {
        Item item = cari(itemId);
        if (item == null) return "Item pasar tidak dikenal: " + itemId;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        if (item.templateId != null) {
            return TemplateProyek.terapkan(dirProyek, item.templateId, namaPaket, namaAplikasi);
        }
        try {
            if ("pasar-jam".equals(itemId)) {
                StringBuilder sb = new StringBuilder();
                sb.append("require \"import\"\n");
                sb.append("import \"android.widget.*\"\n");
                sb.append("import \"android.view.*\"\n");
                sb.append("\n");
                sb.append("activity.setTitle(\"").append(escapeLua(namaAplikasi)).append("\")\n");
                sb.append("\n");
                for (int i = 0; i < BADAN_JAM.length; i++) {
                    sb.append(BADAN_JAM[i]).append("\n");
                }
                return tulisBerkas(new File(dirProyek, "main.lua"), sb.toString());
            }
            if ("pasar-kuis".equals(itemId)) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ISI_KUIS.length; i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(ISI_KUIS[i]);
                }
                sb.append("\n");
                return tulisBerkas(
                        new File(dirProyek, "app/src/main/assets/index.html"), sb.toString());
            }
            return "Item pasar tidak dikenal: " + itemId;
        } catch (Exception e) {
            return "Gagal menerapkan item pasar: " + e.getMessage();
        }
    }

    private static String escapeLua(String s) {
        if (s == null) s = "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
