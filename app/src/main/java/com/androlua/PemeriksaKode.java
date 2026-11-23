package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Pemeriksa Kode (Tahap 3E).
 *
 * Menganalisis kode proyek secara lokal (tanpa internet, tanpa model
 * AI sungguhan — pemeriksa berbasis pola, dan itu ditulis jujur di
 * layar). Tujuh pemeriksaan:
 * (1) variabel lokal tak terpakai (Lua), (2) fungsi/metode kosong,
 * (3) perulangan tanpa henti (while true tanpa break),
 * (4) teks hardcoded di setText (sebaiknya pakai strings.xml),
 * (5) izin/API berbahaya yang dipakai, (6) penanda TODO/FIXME/XXX,
 * (7) eksekusi dinamis/perintah shell (os.execute, Runtime.exec,
 * loadstring).
 *
 * Setiap temuan berisi berkas + nomor baris + jenis + pesan + saran
 * perbaikan. Bila tidak ada temuan, laporannya berkata demikian —
 * bukan pura-pura menemukan masalah.
 *
 * Murni Java (java.io/java.util/regex, tanpa API Android) supaya bisa
 * diuji ulang di JRE.
 */
public class PemeriksaKode {

    /** Satu temuan pemeriksaan. */
    public static class Masalah {
        /** Berkas relatif terhadap akar proyek. */
        public final String berkas;
        /** Nomor baris (1-based). */
        public final int baris;
        /** Jenis pemeriksaan, contoh: "Variabel tak terpakai". */
        public final String jenis;
        /** Penjelasan temuan. */
        public final String pesan;
        /** Saran perbaikan. */
        public final String saran;

        public Masalah(String berkas, int baris, String jenis, String pesan, String saran) {
            this.berkas = berkas;
            this.baris = baris;
            this.jenis = jenis;
            this.pesan = pesan;
            this.saran = saran;
        }
    }

    /** Jumlah jenis pemeriksaan. */
    public static final int JUMLAH_JENIS = 7;

    /** Nama ketujuh jenis pemeriksaan (untuk laporan). */
    public static final String[] NAMA_JENIS = {
        "Variabel lokal tak terpakai",
        "Fungsi/metode kosong",
        "Perulangan tanpa henti",
        "Teks hardcoded di setText",
        "Izin/API berbahaya",
        "Penanda TODO/FIXME",
        "Eksekusi dinamis/perintah shell",
    };

    /** Kata kunci API/izin berbahaya beserta izin Android yang terkait. */
    private static final String[][] API_BERBAHAYA = {
        {"CAMERA", "android.permission.CAMERA"},
        {"MediaRecorder", "android.permission.RECORD_AUDIO"},
        {"RECORD_AUDIO", "android.permission.RECORD_AUDIO"},
        {"SmsManager", "android.permission.SEND_SMS"},
        {"ACCESS_FINE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"},
        {"getLastKnownLocation", "android.permission.ACCESS_FINE_LOCATION"},
        {"SYSTEM_ALERT_WINDOW", "android.permission.SYSTEM_ALERT_WINDOW"},
        {"READ_CONTACTS", "android.permission.READ_CONTACTS"},
    };

    /** Pola eksekusi dinamis/perintah shell. */
    private static final String[] POLA_SHELL = {
        "os.execute", "Runtime.getRuntime().exec", "loadstring(",
    };

    /**
     * Memeriksa semua berkas kode (.lua/.java/.kt) di proyek (lewati
     * folder bin/ dan .git). Mengembalikan daftar temuan (bisa kosong
     * bila kode bersih).
     */
    public static List<Masalah> periksa(File dirProyek) {
        List<Masalah> hasil = new ArrayList<Masalah>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        List<File> kode = new ArrayList<File>();
        kumpulKode(dirProyek, kode);
        for (int i = 0; i < kode.size(); i++) {
            periksaBerkas(dirProyek, kode.get(i), hasil);
        }
        return hasil;
    }

    /** Laporan lengkap untuk ditampilkan/dibacakan. */
    public static String laporan(File dirProyek, List<Masalah> masalah) {
        StringBuilder sb = new StringBuilder();
        sb.append(TeksUI.t("Hasil periksa kode: ")).append(masalah.size())
                .append(masalah.size() == 1 ? " temuan" : " temuan")
                .append(TeksUI.t(" di proyek ")).append(dirProyek.getName()).append(".\n");
        if (masalah.isEmpty()) {
            sb.append("Kode bersih — tidak ada pola bermasalah yang ditemukan.\n");
            sb.append("Catatan: pemeriksa ini berbasis pola (bukan AI sungguhan); ")
                    .append("masalah yang tidak berpola tetap bisa lolos.\n");
            return sb.toString();
        }
        for (int i = 0; i < masalah.size(); i++) {
            Masalah m = masalah.get(i);
            sb.append(m.berkas).append(":").append(m.baris)
                    .append(" [").append(m.jenis).append("] ").append(m.pesan)
                    .append(" Saran: ").append(m.saran).append("\n");
        }
        // Catatan jujur yang sama seperti pada laporan kode bersih:
        // pemeriksa ini berbasis pola, bukan AI sungguhan.
        sb.append("Catatan: pemeriksa ini berbasis pola (bukan AI sungguhan); ")
                .append("masalah yang tidak berpola tetap bisa lolos.\n");
        return sb.toString();
    }

    private static void periksaBerkas(File akar, File f, List<Masalah> hasil) {
        String isi = bacaBerkas(f);
        if (isi == null) return;
        String rel = namaRelatif(akar, f);
        boolean lua = f.getName().endsWith(".lua");
        String[] baris = isi.split("\n", -1);

        // (1) Variabel lokal tak terpakai (Lua saja).
        if (lua) {
            Pattern pLocal = Pattern.compile("^\\s*local\\s+([A-Za-z_][A-Za-z0-9_]*)");
            for (int i = 0; i < baris.length; i++) {
                Matcher m = pLocal.matcher(baris[i]);
                if (!m.find()) continue;
                String nama = m.group(1);
                if ("true".equals(nama) || "false".equals(nama) || "nil".equals(nama)) continue;
                // "local function Nama()" adalah definisi fungsi, bukan
                // variabel — ditangani pemeriksaan fungsi kosong, bukan di sini.
                if ("function".equals(nama)) continue;
                int hitung = 0;
                Pattern pNama = Pattern.compile("\\b" + Pattern.quote(nama) + "\\b");
                for (int j = 0; j < baris.length; j++) {
                    Matcher mm = pNama.matcher(baris[j]);
                    while (mm.find()) hitung++;
                }
                if (hitung <= 1) {
                    hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[0],
                            "Variabel lokal \"" + nama + TeksUI.t("\" dideklarasikan tapi tidak dipakai."),
                            TeksUI.t("Hapus deklarasinya bila memang tidak dibutuhkan.")));
                }
            }
        }

        // (2) Fungsi/metode kosong.
        for (int i = 0; i < baris.length; i++) {
            String t = baris[i].trim();
            boolean fungsiLua = lua && (t.startsWith("function ") || t.startsWith("local function ")
                    || t.matches(".*=\\s*function\\s*\\(.*"));
            boolean metodeJava = !lua && Pattern.compile(
                    ".*\\b(public|private|protected|void|int|String|boolean|fun)\\b.*"
                    + "\\(.*\\)\\s*\\{\\s*$").matcher(t).matches();
            if (!fungsiLua && !metodeJava) continue;
            int j = i + 1;
            while (j < baris.length) {
                String u = baris[j].trim();
                if (u.length() == 0 || u.startsWith("--") || u.startsWith("//")) { j++; continue; }
                break;
            }
            if (j < baris.length) {
                String u = baris[j].trim();
                if ("end".equals(u) || "}".equals(u)) {
                    hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[1],
                            TeksUI.t("Fungsi/metode kosong (tidak ada isi)."),
                            TeksUI.t("Isi fungsinya, atau hapus bila memang tidak dibutuhkan.")));
                }
            }
        }

        // (3) Perulangan tanpa henti: while true tanpa break di sisa berkas.
        for (int i = 0; i < baris.length; i++) {
            String t = baris[i].trim();
            boolean loop = (lua && t.contains("while true do"))
                    || (!lua && t.contains("while (true)"));
            if (!loop) continue;
            boolean adaBreak = false;
            for (int j = i + 1; j < baris.length; j++) {
                if (baris[j].contains("break")) { adaBreak = true; break; }
            }
            if (!adaBreak) {
                hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[2],
                        TeksUI.t("Perulangan \"while true\" tanpa break di sisa berkas — berisiko jalan tanpa henti."),
                        TeksUI.t("Tambahkan kondisi berhenti dan break, atau ubah jadi perulangan berbatas.")));
            }
        }

        // (4)-(7) Pemeriksaan per baris.
        for (int i = 0; i < baris.length; i++) {
            String t = baris[i];
            // (4) Teks hardcoded di setText("...").
            if (t.contains("setText(\"")) {
                hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[3],
                        TeksUI.t("Teks ditulis langsung di setText(\"...\")."),
                        "Pindahkan ke strings.xml (proyek Java) atau tabel terjemahan, supaya bisa diterjemahkan."));
            }
            // (5) Izin/API berbahaya.
            for (int k = 0; k < API_BERBAHAYA.length; k++) {
                if (t.contains(API_BERBAHAYA[k][0])) {
                    hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[4],
                            "Memakai \"" + API_BERBAHAYA[k][0] + TeksUI.t("\" (izin ")
                                    + API_BERBAHAYA[k][1] + ").",
                            TeksUI.t("Pastikan izin itu memang dibutuhkan dan sudah diminta ke pengguna.")));
                    break;
                }
            }
            // (6) Penanda TODO/FIXME/XXX.
            if (t.contains("TODO") || t.contains("FIXME") || t.contains("XXX")) {
                hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[5],
                        "Ada penanda TODO/FIXME/XXX — pekerjaan yang belum selesai.",
                        TeksUI.t("Selesaikan dulu sebelum membagikan aplikasi.")));
            }
            // (7) Eksekusi dinamis/perintah shell.
            for (int k = 0; k < POLA_SHELL.length; k++) {
                if (t.contains(POLA_SHELL[k])) {
                    hasil.add(new Masalah(rel, i + 1, NAMA_JENIS[6],
                            "Memakai \"" + POLA_SHELL[k] + TeksUI.t("\" — kode/perintah dari luar."),
                            "Hindari menjalankan perintah dari teks yang bisa diubah pengguna; bisa membahayakan."));
                    break;
                }
            }
        }
    }

    private static void kumpulKode(File dir, List<File> keluar) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if ("bin".equals(f.getName()) || ".git".equals(f.getName())) continue;
                kumpulKode(f, keluar);
            } else {
                String n = f.getName();
                if (n.endsWith(".lua") || n.endsWith(".java") || n.endsWith(".kt")) {
                    keluar.add(f);
                }
            }
        }
    }

    private static String namaRelatif(File akar, File f) {
        String a = akar.getAbsolutePath();
        String b = f.getAbsolutePath();
        if (b.startsWith(a)) return b.substring(a.length() + 1);
        return f.getName();
    }

    private static String bacaBerkas(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            try {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            } finally {
                r.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
