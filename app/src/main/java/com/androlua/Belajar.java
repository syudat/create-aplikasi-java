package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Mode Belajar Interaktif (Tahap 3E).
 *
 * Lima pelajaran langkah demi langkah di dalam IDE. Tiap pelajaran
 * berisi penjelasan + tugas praktik, dan IDE memeriksa BENAR-BENAR
 * apakah tugasnya sudah dikerjakan (memindai proyek di Download/project
 * dan APK di Download) — bukan sekadar menandai selesai karena dibaca.
 *
 * Progres disimpan di berkas belajar.txt (satu id pelajaran per baris)
 * di folder data aplikasi.
 *
 * Murni Java (java.io/java.util/regex, tanpa API Android) supaya bisa
 * diuji ulang di JRE.
 */
public class Belajar {

    /** Satu pelajaran. */
    public static class Pelajaran {
        /** Id unik (disimpan di belajar.txt bila selesai). */
        public final String id;
        /** Judul pelajaran. */
        public final String judul;
        /** Penjelasan langkah demi langkah. */
        public final String penjelasan;
        /** Tugas praktik yang harus dikerjakan. */
        public final String tugas;

        public Pelajaran(String id, String judul, String penjelasan, String tugas) {
            this.id = id;
            this.judul = judul;
            this.penjelasan = penjelasan;
            this.tugas = tugas;
        }
    }

    public static final Pelajaran[] SEMUA = {
        new Pelajaran("proyek-pertama", "1. Membuat proyek pertama",
                "Proyek adalah wadah aplikasimu. Dari menu utama, tekan Buat Project, "
                + "isi nama aplikasi dan nama paket, pilih jenis (Lua paling mudah untuk "
                + "pemula), lalu tekan Buat Proyek. Proyekmu tersimpan di folder "
                + "Download/project dan bisa dibuka lagi di Kelola Project.",
                "Buat satu proyek apa saja (jenis apa pun) lewat Buat Project."),
        new Pelajaran("tambah-tombol", "2. Menambah tombol",
                "Tombol adalah cara pengguna menyentuh aplikasimu. Di proyek Lua, "
                + "tombol dibuat dengan Button(activity) lalu ditambah ke layar dengan "
                + "addView, dan aksinya ditulis di tombol.onClick. Di proyek Java, "
                + "tombol dibuat dengan new Button(this). Coba template Kalkulator "
                + "di Buat Project — di dalamnya sudah ada empat tombol operasi.",
                "Buat proyek Kalkulator dari template, atau tambahkan satu tombol "
                + "ke proyekmu sendiri."),
        new Pelajaran("ubah-warna", "3. Mengubah warna",
                "Warna membuat aplikasimu tidak polos. Di kode, warna diubah dengan "
                + "setBackgroundColor (latar) dan setTextColor (tulisan), memakai kode "
                + "warna heksa seperti #2196F3 (biru) atau #FF0000 (merah). Buka "
                + "proyekmu di editor, cari baris warna, ubah kodenya, lalu jalankan "
                + "untuk melihat hasilnya.",
                "Ubah satu warna di salah satu proyekmu (latar atau tulisan)."),
        new Pelajaran("tambah-suara", "4. Menambah suara",
                "Suara membuat aplikasi terasa hidup. Di Android, suara diputar "
                + "dengan MediaPlayer (berkas suara) atau TextToSpeech (membacakan "
                + "teks dengan suara). Letakkan berkas suara (.mp3/.ogg) di folder "
                + "assets proyek, lalu putar dari kode. Proyek Bus Simulator dan "
                + "Flight Simulator memakai cara ini.",
                "Tambahkan suara ke salah satu proyekmu (MediaPlayer, SoundPool, "
                + "TextToSpeech, atau berkas .mp3/.ogg)."),
        new Pelajaran("bangun-apk", "5. Membangun APK",
                "APK adalah berkas aplikasi yang bisa dipasang di HP. Dari Kelola "
                + "Project, tahan lama nama proyek lalu pilih Bangun proyek. Pilih "
                + "bangun di HP (offline, cepat) atau lewat GitHub Actions. Setelah "
                + "selesai, APK tersimpan di folder Download dan bisa dipasang dengan "
                + "menekannya.",
                "Bangun satu proyek sampai jadi APK (tersimpan di folder Download)."),
    };

    public static Pelajaran cari(String id) {
        if (id == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    /** Hasil memeriksa satu tugas: lulus + penjelasan bukti. */
    public static class HasilTugas {
        public final boolean lulus;
        public final String bukti;
        public HasilTugas(boolean lulus, String bukti) {
            this.lulus = lulus;
            this.bukti = bukti;
        }
    }

    /**
     * Memeriksa apakah tugas pelajaran sudah dikerjakan, dengan memindai
     * proyek sungguhan (bukan sekadar menandai dibaca).
     *
     * @param dirRoot folder Download/project
     * @param dirDownload folder Download (untuk pelajaran bangun-apk)
     * @param idPelajaran id pelajaran
     */
    public static HasilTugas periksaTugas(File dirRoot, File dirDownload, String idPelajaran) {
        if ("proyek-pertama".equals(idPelajaran)) {
            List<File> proyek = daftarProyek(dirRoot);
            if (!proyek.isEmpty()) {
                return new HasilTugas(true, "Ditemukan " + proyek.size()
                        + " proyek, contoh: " + proyek.get(0).getName() + ".");
            }
            return new HasilTugas(false, "Belum ada proyek di Download/project.");
        }
        if ("tambah-tombol".equals(idPelajaran)) {
            String bukti = cariDiProyek(dirRoot, "Button\\(");
            if (bukti != null) return new HasilTugas(true, "Tombol ditemukan di " + bukti + ".");
            return new HasilTugas(false,
                    "Belum ada tombol di proyekmu (cari \"Button(\" di kode).");
        }
        if ("ubah-warna".equals(idPelajaran)) {
            String bukti = cariDiProyek(dirRoot,
                    "setBackgroundColor|setTextColor|setBackground|#[0-9a-fA-F]{6}");
            if (bukti != null) return new HasilTugas(true, "Pengaturan warna ditemukan di " + bukti + ".");
            return new HasilTugas(false,
                    "Belum ada pengaturan warna di proyekmu (setBackgroundColor/setTextColor/kode #RRGGBB).");
        }
        if ("tambah-suara".equals(idPelajaran)) {
            String bukti = cariDiProyek(dirRoot,
                    "MediaPlayer|SoundPool|TextToSpeech|\\.mp3|\\.ogg|\\.wav");
            if (bukti != null) return new HasilTugas(true, "Suara ditemukan di " + bukti + ".");
            return new HasilTugas(false,
                    "Belum ada suara di proyekmu (MediaPlayer/SoundPool/TextToSpeech/.mp3/.ogg).");
        }
        if ("bangun-apk".equals(idPelajaran)) {
            List<File> apk = cariApk(dirDownload, 0);
            if (!apk.isEmpty()) {
                return new HasilTugas(true, "APK ditemukan: " + apk.get(0).getName() + ".");
            }
            return new HasilTugas(false, "Belum ada berkas .apk di folder Download.");
        }
        return new HasilTugas(false, "Pelajaran tidak dikenal: " + idPelajaran);
    }

    /** Daftar folder proyek (berisi init.lua) di Download/project. */
    public static List<File> daftarProyek(File dirRoot) {
        List<File> hasil = new ArrayList<File>();
        if (dirRoot == null || !dirRoot.isDirectory()) return hasil;
        File[] daftar = dirRoot.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            if (daftar[i].isDirectory()
                    && new File(daftar[i], "init.lua").isFile()) {
                hasil.add(daftar[i]);
            }
        }
        return hasil;
    }

    /** Mencari pola (regex) di semua berkas kode proyek; mengembalikan "berkas:baris" pertama. */
    static String cariDiProyek(File dirRoot, String pola) {
        List<File> proyek = daftarProyek(dirRoot);
        Pattern p = Pattern.compile(pola);
        for (int i = 0; i < proyek.size(); i++) {
            List<File> kode = new ArrayList<File>();
            kumpulKode(proyek.get(i), kode);
            for (int j = 0; j < kode.size(); j++) {
                String isi = bacaBerkas(kode.get(j));
                if (isi == null) continue;
                String[] baris = isi.split("\n", -1);
                for (int k = 0; k < baris.length; k++) {
                    if (p.matcher(baris[k]).find()) {
                        return namaRelatif(dirRoot, kode.get(j)) + ":" + (k + 1);
                    }
                }
            }
        }
        return null;
    }

    /** Mencari berkas .apk di folder Download (rekursif, kedalaman maks 3). */
    static List<File> cariApk(File dir, int kedalaman) {
        List<File> hasil = new ArrayList<File>();
        if (dir == null || !dir.isDirectory() || kedalaman > 3) return hasil;
        File[] daftar = dir.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            if (daftar[i].isDirectory()) {
                hasil.addAll(cariApk(daftar[i], kedalaman + 1));
            } else if (daftar[i].getName().toLowerCase().endsWith(".apk")) {
                hasil.add(daftar[i]);
            }
        }
        return hasil;
    }

    // ------------------------------------------------------------ progres

    /** Id pelajaran yang sudah selesai (dari berkas progres). */
    public static List<String> bacaProgres(File berkasProgres) {
        List<String> hasil = new ArrayList<String>();
        if (berkasProgres == null || !berkasProgres.isFile()) return hasil;
        String isi = bacaBerkas(berkasProgres);
        if (isi == null) return hasil;
        for (String baris : isi.split("\n")) {
            String id = baris.trim();
            if (id.length() > 0 && cari(id) != null && !hasil.contains(id)) {
                hasil.add(id);
            }
        }
        return hasil;
    }

    /** Menandai satu pelajaran selesai (menulis ulang berkas progres). */
    public static void tandaiSelesai(File berkasProgres, String idPelajaran) {
        if (berkasProgres == null || cari(idPelajaran) == null) return;
        List<String> selesai = bacaProgres(berkasProgres);
        if (selesai.contains(idPelajaran)) return;
        selesai.add(idPelajaran);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selesai.size(); i++) sb.append(selesai.get(i)).append("\n");
        tulisBerkas(berkasProgres, sb.toString());
    }

    /** Jumlah pelajaran yang sudah selesai. */
    public static int jumlahSelesai(File berkasProgres) {
        return bacaProgres(berkasProgres).size();
    }

    // ------------------------------------------------------------ pembantu

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
                if (n.endsWith(".lua") || n.endsWith(".java") || n.endsWith(".kt")
                        || n.endsWith(".xml") || n.endsWith(".html")) {
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
            return null;
        } catch (Exception e) {
            return "Gagal menulis " + f.getAbsolutePath() + ": " + e.getMessage();
        }
    }
}
