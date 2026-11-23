package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pembuat Video Demo (Tahap 3I).
 *
 * TIDAK ADA API rekam layar dan TIDAK ADA ffmpeg di HP, jadi fitur ini
 * TIDAK menghasilkan berkas .mp4. Yang NYATA dihasilkan:
 * (a) SKRIP / storyboard video demo (video-demo.txt): urutan adegan +
 *     teks narasi + durasi per adegan + total durasi, siap dibacakan
 *     sambil merekam layar dengan aplikasi perekam layar HP;
 * (b) SLIDESHOW HTML (video-demo.html): halaman web yang menampilkan
 *     gambar-gambar proyek (ikon + tangkapan layar bila ada) bergantian
 *     otomatis tiap 5 detik, bisa dibuka di browser HP lalu direkam
 *     layarnya.
 * Adegan baku ada 5 (pembuka, tampilan utama, fitur 1, fitur 2,
 * penutup); durasi bisa diubah lewat daftar adegan.
 *
 * KETERBATASAN YANG JUJUR (ditulis juga di VideoDemoActivity, di
 * video-demo.txt, dan di video-demo.html): ini BUKAN video .mp4 —
 * untuk video sungguhan, buka video-demo.html di browser lalu rekam
 * layar HP, atau baca narasi di video-demo.txt sambil merekam aplikasi
 * yang sedang berjalan.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JRE biasa.
 */
public class VideoDemo {

    /** Nama berkas skrip di akar proyek. */
    public static final String NAMA_SKRIP = "video-demo.txt";

    /** Nama berkas slideshow di akar proyek. */
    public static final String NAMA_SLIDESHOW = "video-demo.html";

    /** Durasi tiap gambar di slideshow (detik). */
    public static final int DURASI_GAMBAR_DETIK = 5;

    /** Satu adegan video demo. */
    public static class Adegan {
        public final String judul;
        public final int durasiDetik;
        public final String narasi;
        public final String gambar;
        public Adegan(String judul, int durasiDetik, String narasi, String gambar) {
            this.judul = judul == null ? "" : judul;
            this.durasiDetik = durasiDetik < 1 ? 1 : durasiDetik;
            this.narasi = narasi == null ? "" : narasi;
            this.gambar = gambar == null ? "" : gambar;
        }
    }

    /** 5 adegan baku untuk satu aplikasi. */
    public static List<Adegan> adeganBaku(String namaAplikasi) {
        String nama = namaAplikasi == null || namaAplikasi.isEmpty()
                ? TeksUI.t("Aplikasi Saya") : namaAplikasi;
        List<Adegan> hasil = new ArrayList<Adegan>();
        hasil.add(new Adegan("Pembuka", 3,
                TeksUI.t("Halo, ini aplikasi ") + nama + TeksUI.t(" yang saya buat sendiri di HP."),
                TeksUI.t("ikon aplikasi")));
        hasil.add(new Adegan("Tampilan Utama", 5,
                TeksUI.t("Ini tampilan utama ") + nama + TeksUI.t(". Semua menu ada di sini."),
                TeksUI.t("tangkapan layar utama")));
        hasil.add(new Adegan("Fitur 1", 5,
                TeksUI.t("Fitur pertama: coba tekan tombol utamanya dan lihat hasilnya."),
                TeksUI.t("tangkapan layar fitur 1")));
        hasil.add(new Adegan("Fitur 2", 5,
                TeksUI.t("Fitur kedua: bagian ini yang paling saya suka dari ") + nama + ".",
                TeksUI.t("tangkapan layar fitur 2")));
        hasil.add(new Adegan("Penutup", 3,
                TeksUI.t("Terima kasih sudah menonton. ") + nama + TeksUI.t(" dibuat dengan Create Aplikasi."),
                TeksUI.t("ikon aplikasi")));
        return hasil;
    }

    /** Total durasi semua adegan (detik). */
    public static int durasiTotal(List<Adegan> adegan) {
        if (adegan == null) return 0;
        int total = 0;
        for (int i = 0; i < adegan.size(); i++) total += adegan.get(i).durasiDetik;
        return total;
    }

    /** Teks skrip lengkap (untuk video-demo.txt dan pratinjau di layar). */
    public static String teksSkrip(String judul, List<Adegan> adegan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Skrip Video Demo: ").append(judul == null ? "" : judul).append("\n");
        sb.append("Total durasi: ").append(durasiTotal(adegan)).append(TeksUI.t(" detik ("))
                .append(adegan == null ? 0 : adegan.size()).append(" adegan)\n\n");
        if (adegan != null) {
            for (int i = 0; i < adegan.size(); i++) {
                Adegan a = adegan.get(i);
                sb.append("Adegan ").append(i + 1).append(": ").append(a.judul)
                        .append(" (").append(a.durasiDetik).append(" detik)\n");
                sb.append("  Gambar: ").append(a.gambar).append("\n");
                sb.append("  Narasi: ").append(a.narasi).append("\n\n");
            }
        }
        sb.append("CATATAN JUJUR: berkas ini adalah SKRIP video, bukan video .mp4.\n");
        sb.append("Untuk video sungguhan: buka video-demo.html di browser HP lalu\n");
        sb.append("rekam layar, atau baca narasi di atas sambil merekam aplikasi\n");
        sb.append("yang sedang berjalan dengan aplikasi perekam layar.\n");
        return sb.toString();
    }

    /**
     * Tulis skrip video-demo.txt ke akar proyek. Mengembalikan berkas
     * bila berhasil, null bila gagal.
     */
    public static File tulisSkrip(File dirProyek, String judul, List<Adegan> adegan) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        File keluar = new File(dirProyek, NAMA_SKRIP);
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(keluar), "UTF-8");
            try {
                w.write(teksSkrip(judul, adegan));
            } finally {
                w.close();
            }
            return keluar;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Kumpulkan gambar proyek untuk slideshow: ikon dulu, lalu
     * *.png/*.jpg di proyek (tanpa bin/ dan .git/), maks 10 gambar.
     * Mengembalikan jalan RELATIF dari akar proyek.
     */
    public static List<String> kumpulkanGambar(File dirProyek) {
        List<String> hasil = new ArrayList<String>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        String ikon = "app/src/main/res/drawable/icon.png";
        if (new File(dirProyek, ikon).isFile()) hasil.add(ikon);
        kumpulkanGambarRekursif(dirProyek, dirProyek, "", hasil);
        while (hasil.size() > 10) hasil.remove(hasil.size() - 1);
        return hasil;
    }

    private static void kumpulkanGambarRekursif(File akar, File dir, String awalan,
                                                List<String> hasil) {
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            File a = anak[i];
            if (a.isDirectory()) {
                if (".git".equals(a.getName())) continue;
                if ("bin".equals(a.getName()) && a.getParentFile().equals(akar)) continue;
                kumpulkanGambarRekursif(akar, a, awalan + a.getName() + "/", hasil);
            } else {
                String namaKecil = a.getName().toLowerCase();
                String jalan = awalan + a.getName();
                if (jalan.equals("app/src/main/res/drawable/icon.png")) continue;
                if (namaKecil.endsWith(".png") || namaKecil.endsWith(".jpg")
                        || namaKecil.endsWith(".jpeg")) {
                    hasil.add(jalan);
                }
            }
        }
    }

    /** Teks HTML slideshow (gambar berganti otomatis tiap 5 detik). */
    public static String teksSlideshow(String judul, List<String> gambar) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"id\">\n<head>\n<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<title>Video Demo: ").append(escapeHtml(judul)).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body { margin: 0; background: #121212; color: #fff; "
                + "font-family: sans-serif; text-align: center; }\n");
        sb.append("h1 { font-size: 22px; margin: 16px 8px 4px 8px; }\n");
        sb.append("p { font-size: 14px; color: #bbb; margin: 4px 8px 16px 8px; }\n");
        sb.append("img { max-width: 92%; max-height: 70vh; display: none; "
                + "margin: 0 auto; border-radius: 8px; }\n");
        sb.append("img.aktif { display: block; }\n");
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<h1>").append(escapeHtml(judul)).append("</h1>\n");
        sb.append("<p>Slideshow demo — gambar berganti otomatis tiap "
                + DURASI_GAMBAR_DETIK + " detik. Rekam layar HP untuk menjadi video.</p>\n");
        for (int i = 0; i < gambar.size(); i++) {
            sb.append("<img src=\"").append(escapeHtml(gambar.get(i))).append("\"")
                    .append(i == 0 ? TeksUI.t(" class=\"aktif\"") : "")
                    .append(" alt=\"Gambar ").append(i + 1).append("\">\n");
        }
        sb.append("<script>\n");
        sb.append("var gambar = document.getElementsByTagName('img');\n");
        sb.append("var posisi = 0;\n");
        sb.append("setInterval(function() {\n");
        sb.append("  gambar[posisi].className = '';\n");
        sb.append("  posisi = (posisi + 1) % gambar.length;\n");
        sb.append("  gambar[posisi].className = 'aktif';\n");
        sb.append("}, " + (DURASI_GAMBAR_DETIK * 1000) + ");\n");
        sb.append("</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * Tulis slideshow video-demo.html ke akar proyek. Mengembalikan
     * berkas bila berhasil, null bila gagal (termasuk bila tidak ada
     * gambar sama sekali di proyek).
     */
    public static File tulisSlideshow(File dirProyek, String judul, List<String> gambar) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        if (gambar == null || gambar.isEmpty()) return null;
        File keluar = new File(dirProyek, NAMA_SLIDESHOW);
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(keluar), "UTF-8");
            try {
                w.write(teksSlideshow(judul, gambar));
            } finally {
                w.close();
            }
            return keluar;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Escape & < > " untuk teks HTML. */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else if (c == '"') sb.append("&quot;");
            else sb.append(c);
        }
        return sb.toString();
    }
}
