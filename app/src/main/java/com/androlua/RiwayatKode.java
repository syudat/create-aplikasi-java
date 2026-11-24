package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Mesin Perjalanan Waktu Kode dan Sandbox Eksperimen (Fitur 5 dari
 * daftar Gemini, 5.7) — rekam jejak otomatis proyek tanpa Git CLI.
 *
 * Setiap titik waktu menyimpan arsip ZIP isi proyek + metadata
 * (daftar berkas + hash SHA-256 + jumlah baris) di dalam folder
 * proyek sendiri: &lt;proyek&gt;/.riwayat/&lt;yyyyMMdd-HHmmss&gt;_&lt;label&gt;/.
 * Pembanding menghasilkan ringkasan teks (tanpa warna): berkas yang
 * ditambah / dihapus / diubah, dan fungsi yang ditambah / dihapus.
 * Pemulihan SELALU merekam titik cadangan dari keadaan saat ini dulu,
 * jadi tidak ada pemulihan yang menghapus tanpa cadangan.
 *
 * Yang dilacak: berkas sumber .java/.kt/.xml/.lua/.py/.html. Folder
 * bin/ (hasil build), .riwayat/ (arsip titik waktu sendiri), dan .git/
 * (repo Git lokal) tidak ikut direkam. Berkas lain (gambar, suara)
 * tidak ikut — titik waktu hanya soal kode.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JVM.
 */
public class RiwayatKode {

    /** Folder arsip titik waktu di dalam proyek. */
    public static final String DIR_RIWAYAT = ".riwayat";

    /** Nama berkas metadata di dalam satu titik waktu. */
    public static final String NAMA_METADATA = "titik.txt";

    /** Nama arsip ZIP isi proyek di dalam satu titik waktu. */
    public static final String NAMA_ARSIP = "arsip.zip";

    /** Label otomatis titik cadangan yang dibuat sebelum pemulihan. */
    public static final String LABEL_CADANGAN_PULIH = "cadangan-sebelum-pulih";

    /** Ekstensi berkas sumber yang dilacak. */
    private static final String[] EKSTENSI = {".java", ".kt", ".xml", ".lua", ".py", ".html"};

    /** Nama folder yang tidak pernah direkam/disalin. */
    private static final String[] ABAIKAN_DIR = {"bin", ".riwayat", ".git"};

    /** Satu berkas sumber di dalam titik waktu. */
    public static class EntriBerkas {
        /** Jalan relatif dari akar proyek, mis. "app/src/main/java/MainActivity.java". */
        public final String jalan;
        /** Hash SHA-256 isi berkas (heksadesimal huruf kecil). */
        public final String sha256;
        /** Jumlah baris isi berkas. */
        public final int baris;

        public EntriBerkas(String jalan, String sha256, int baris) {
            this.jalan = jalan;
            this.sha256 = sha256 == null ? "" : sha256;
            this.baris = baris;
        }
    }

    /** Satu titik waktu (satu folder di .riwayat/). */
    public static class Titik {
        /** Label dari pengguna (atau dari nama folder bila metadata rusak). */
        public String label = "";
        /** Waktu perekaman (epoch ms, 0 bila tidak terbaca). */
        public long waktu;
        /** Folder titik waktu. */
        public File dir;
        /** Jumlah berkas sumber yang terekam. */
        public int jumlahBerkas;
        /** Jumlah baris semua berkas sumber. */
        public int jumlahBaris;

        /** Nama folder titik waktu. */
        public String namaDir() {
            return dir == null ? "" : dir.getName();
        }

        /** Tanggal perekaman "yyyy-MM-dd HH:mm" ("" bila waktu tidak terbaca). */
        public String tanggal() {
            if (waktu <= 0) return "";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    .format(new Date(waktu));
        }

        /** Satu baris daftar: "label — tanggal — N berkas, M baris". */
        public String barisDaftar() {
            StringBuilder b = new StringBuilder();
            b.append(label.length() == 0 ? namaDir() : label);
            if (tanggal().length() > 0) b.append(" — ").append(tanggal());
            b.append(" — ").append(jumlahBerkas).append(TeksUI.t(" berkas, "))
             .append(jumlahBaris).append(TeksUI.t(" baris"));
            return b.toString();
        }
    }

    /** Hasil perekaman satu titik waktu. */
    public static class HasilRekam {
        public Titik titik;
        public String galat;

        public boolean berhasil() {
            return galat == null && titik != null;
        }
    }

    /** Hasil pembuatan salinan eksperimen. */
    public static class HasilSalinan {
        public File dir;
        public String galat;

        public boolean berhasil() {
            return galat == null && dir != null;
        }
    }

    // ------------------------------------------------------- rekam + daftar

    /**
     * Rekam titik waktu proyek sekarang: hitung SHA-256 tiap berkas
     * sumber, simpan arsip ZIP + metadata di
     * &lt;proyek&gt;/.riwayat/&lt;yyyyMMdd-HHmmss&gt;_&lt;label&gt;/.
     * Label kosong menjadi "tanpa-label". Mengembalikan HasilRekam
     * (titik / galat); tidak pernah melempar.
     */
    public static HasilRekam rekamTitik(File dirProyek, String label) {
        HasilRekam h = new HasilRekam();
        if (dirProyek == null || !dirProyek.isDirectory()) {
            h.galat = TeksUI.t("Folder proyek tidak ditemukan.");
            return h;
        }
        String labelBersih = bersihkanLabel(label);
        List<EntriBerkas> entri = kumpulkanEntri(dirProyek);
        File dirRiwayat = new File(dirProyek, DIR_RIWAYAT);
        String cap = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
        String nama = cap + "_" + labelBersih;
        File dirTitik = new File(dirRiwayat, nama);
        int coba = 2;
        while (dirTitik.exists()) {
            dirTitik = new File(dirRiwayat, nama + "-" + coba);
            coba++;
        }
        try {
            dirTitik.mkdirs();
            File zip = new File(dirTitik, NAMA_ARSIP);
            if (!tulisArsip(dirProyek, entri, zip)) {
                h.galat = TeksUI.t("Gagal menulis arsip titik waktu.");
                return h;
            }
            long waktu = System.currentTimeMillis();
            int totalBaris = 0;
            StringBuilder meta = new StringBuilder();
            meta.append("label=").append(labelBersih).append('\n');
            meta.append("waktu=").append(waktu).append('\n');
            for (int i = 0; i < entri.size(); i++) {
                EntriBerkas e = entri.get(i);
                totalBaris += e.baris;
                meta.append("berkas ").append(e.sha256).append(' ')
                    .append(e.baris).append(' ').append(e.jalan).append('\n');
            }
            tulisTeks(new File(dirTitik, NAMA_METADATA), meta.toString());
            Titik t = new Titik();
            t.label = labelBersih;
            t.waktu = waktu;
            t.dir = dirTitik;
            t.jumlahBerkas = entri.size();
            t.jumlahBaris = totalBaris;
            h.titik = t;
            return h;
        } catch (Exception e) {
            h.galat = TeksUI.t("Gagal merekam titik waktu: ") + e.getMessage();
            return h;
        }
    }

    /**
     * Daftar titik waktu proyek, terbaru dulu. Titik yang metadatanya
     * rusak tetap terdaftar (label dari nama folder, jumlah 0).
     */
    public static List<Titik> daftarTitik(File dirProyek) {
        List<Titik> hasil = new ArrayList<Titik>();
        if (dirProyek == null || !dirProyek.isDirectory()) return hasil;
        File dirRiwayat = new File(dirProyek, DIR_RIWAYAT);
        File[] daftar = dirRiwayat.listFiles();
        if (daftar == null) return hasil;
        for (int i = 0; i < daftar.length; i++) {
            if (!daftar[i].isDirectory()) continue;
            hasil.add(bacaTitik(daftar[i]));
        }
        Collections.sort(hasil, new Comparator<Titik>() {
            @Override
            public int compare(Titik a, Titik b) {
                if (a.waktu != b.waktu) return Long.compare(b.waktu, a.waktu);
                return b.namaDir().compareTo(a.namaDir());
            }
        });
        return hasil;
    }

    /** Baca satu folder titik waktu (metadata bila ada, nama folder bila tidak). */
    static Titik bacaTitik(File dirTitik) {
        Titik t = new Titik();
        t.dir = dirTitik;
        t.label = labelDariNama(dirTitik.getName());
        String isi = bacaTeks(new File(dirTitik, NAMA_METADATA));
        if (isi != null) {
            Map<String, EntriBerkas> entri = uraiMetadata(isi);
            for (String baris : isi.split("\n", -1)) {
                if (baris.startsWith("label=")) {
                    String l = baris.substring(6).trim();
                    if (l.length() > 0) t.label = l;
                } else if (baris.startsWith("waktu=")) {
                    try {
                        t.waktu = Long.parseLong(baris.substring(6).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            t.jumlahBerkas = entri.size();
            int total = 0;
            for (EntriBerkas e : entri.values()) total += e.baris;
            t.jumlahBaris = total;
        }
        return t;
    }

    // ------------------------------------------------------- banding

    /**
     * Bandingkan dua titik waktu: berkas ditambah / dihapus / diubah
     * (dari metadata hash) + fungsi ditambah / dihapus (dari isi arsip
     * ZIP; deteksi baris "nama(...) {" sederhana per bahasa). Hasilnya
     * ringkasan teks terstruktur tanpa warna. Bila salah satu arsip
     * hilang, perbandingan berkas tetap jalan dan fungsi dilaporkan
     * tidak bisa dibandingkan.
     */
    public static String bandingTitik(File titikA, File titikB) {
        StringBuilder b = new StringBuilder();
        b.append(TeksUI.t("Perbandingan titik waktu")).append('\n');
        Titik a = titikA == null ? null : bacaTitik(titikA);
        Titik bb = titikB == null ? null : bacaTitik(titikB);
        b.append(TeksUI.t("Dari: ")).append(a == null ? "-" : a.barisDaftar()).append('\n');
        b.append(TeksUI.t("Ke: ")).append(bb == null ? "-" : bb.barisDaftar()).append('\n');
        Map<String, EntriBerkas> metaA = metaTitik(titikA);
        Map<String, EntriBerkas> metaB = metaTitik(titikB);
        List<String> tambah = new ArrayList<String>();
        List<String> hapus = new ArrayList<String>();
        List<String> ubah = new ArrayList<String>();
        for (String jalan : metaB.keySet()) {
            EntriBerkas lama = metaA.get(jalan);
            if (lama == null) tambah.add(jalan);
            else if (!lama.sha256.equals(metaB.get(jalan).sha256)) ubah.add(jalan);
        }
        for (String jalan : metaA.keySet()) {
            if (!metaB.containsKey(jalan)) hapus.add(jalan);
        }
        Collections.sort(tambah);
        Collections.sort(hapus);
        Collections.sort(ubah);
        tulisBagian(b, TeksUI.t("Berkas ditambah"), tambah);
        tulisBagian(b, TeksUI.t("Berkas dihapus"), hapus);
        tulisBagian(b, TeksUI.t("Berkas diubah"), ubah);
        // Fungsi: bandingkan isi arsip ZIP kedua titik.
        Map<String, String> isiA = bacaArsip(titikA);
        Map<String, String> isiB = bacaArsip(titikB);
        if (isiA == null || isiB == null) {
            b.append(TeksUI.t("Fungsi tidak bisa dibandingkan: arsip salah satu titik waktu tidak ditemukan."))
             .append('\n');
            return b.toString();
        }
        Map<String, String> fungsiA = semuaFungsi(isiA);
        Map<String, String> fungsiB = semuaFungsi(isiB);
        List<String> fungsiTambah = new ArrayList<String>();
        List<String> fungsiHapus = new ArrayList<String>();
        for (String kunci : fungsiB.keySet()) {
            if (!fungsiA.containsKey(kunci)) fungsiTambah.add(kunci);
        }
        for (String kunci : fungsiA.keySet()) {
            if (!fungsiB.containsKey(kunci)) fungsiHapus.add(kunci);
        }
        Collections.sort(fungsiTambah);
        Collections.sort(fungsiHapus);
        tulisBagian(b, TeksUI.t("Fungsi ditambah"), fungsiTambah);
        tulisBagian(b, TeksUI.t("Fungsi dihapus"), fungsiHapus);
        return b.toString();
    }

    /**
     * Daftar nama fungsi di satu berkas sumber, dari isinya (teks).
     * Java/Kotlin: baris "… nama(…) {"; Lua: "function nama("; Python:
     * "def nama(". XML/HTML tidak punya fungsi. Heuristik sederhana —
     * baris if/for/while/switch, komentar, dan pemanggilan (ada '=' di
     * depan '(') dilewati.
     */
    public static List<String> fungsiDariIsi(String jalan, String isi) {
        List<String> hasil = new ArrayList<String>();
        if (jalan == null || isi == null) return hasil;
        String kecil = jalan.toLowerCase(Locale.US);
        boolean lua = kecil.endsWith(".lua");
        boolean py = kecil.endsWith(".py");
        boolean kode = kecil.endsWith(".java") || kecil.endsWith(".kt");
        if (!lua && !py && !kode) return hasil;
        for (String barisMentah : isi.split("\n", -1)) {
            String baris = barisMentah.trim();
            if (baris.length() == 0) continue;
            String nama = null;
            if (lua) {
                Matcher m = Pattern.compile(
                        "^(?:local\\s+)?function\\s+([A-Za-z_][A-Za-z0-9_.:]*)\\s*\\(")
                        .matcher(baris);
                if (m.find()) nama = m.group(1);
                if (nama == null) {
                    Matcher m2 = Pattern.compile(
                            "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*function\\s*\\(")
                            .matcher(baris);
                    if (m2.find()) nama = m2.group(1);
                }
            } else if (py) {
                Matcher m = Pattern.compile("^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                        .matcher(baris);
                if (m.find()) nama = m.group(1);
            } else {
                if (baris.startsWith("//") || baris.startsWith("*")
                        || baris.startsWith("/*") || baris.startsWith("@")) continue;
                if (!baris.endsWith("{")) continue;
                int buka = baris.indexOf('(');
                int tutup = baris.indexOf(')', buka < 0 ? 0 : buka);
                if (buka < 0 || tutup < 0) continue;
                String sebelum = baris.substring(0, buka).trim();
                // Pemanggilan / penugasan (ada '=' sebelum '(') bukan definisi.
                if (sebelum.indexOf('=') >= 0) continue;
                String kataPertama = sebelum.split("\\s+")[0];
                if ("if".equals(kataPertama) || "for".equals(kataPertama)
                        || "while".equals(kataPertama) || "switch".equals(kataPertama)
                        || "catch".equals(kataPertama) || "return".equals(kataPertama)
                        || "new".equals(kataPertama)) continue;
                String[] kata = sebelum.split("\\s+");
                String calon = kata[kata.length - 1];
                // Buang generic / pewarisan di belakang nama ("Foo<T>").
                int sudut = calon.indexOf('<');
                if (sudut >= 0) calon = calon.substring(0, sudut);
                // "class Foo" / "interface Foo" bukan fungsi.
                if ("class".equals(kataPertama) || "interface".equals(kataPertama)
                        || "enum".equals(kataPertama) || "object".equals(kataPertama)) continue;
                if (!calon.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
                nama = calon;
            }
            if (nama != null && !hasil.contains(nama)) hasil.add(nama);
        }
        return hasil;
    }

    // ------------------------------------------------------- pulih + salinan

    /**
     * Pulihkan proyek ke keadaan satu titik waktu. SEBELUM memulihkan,
     * keadaan saat ini direkam dulu sebagai titik cadangan
     * "cadangan-sebelum-pulih"; bila cadangan gagal dibuat, pemulihan
     * DIBATALKAN (tidak ada hapus tanpa cadangan). Mengembalikan null
     * bila berhasil, atau pesan galat.
     */
    public static String pulihkanTitik(File dirProyek, File titik) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return TeksUI.t("Folder proyek tidak ditemukan.");
        }
        if (titik == null || !titik.isDirectory()
                || !new File(titik, NAMA_ARSIP).isFile()) {
            return TeksUI.t("Arsip titik waktu tidak ditemukan.");
        }
        HasilRekam cadangan = rekamTitik(dirProyek, LABEL_CADANGAN_PULIH);
        if (!cadangan.berhasil()) {
            return TeksUI.t("Gagal membuat cadangan sebelum memulihkan: ") + cadangan.galat;
        }
        hapusBerkasTerlacak(dirProyek);
        String galat = ekstrakArsip(new File(titik, NAMA_ARSIP), dirProyek);
        if (galat != null) {
            return TeksUI.t("Gagal memulihkan titik waktu: ") + galat;
        }
        return null;
    }

    /**
     * Buat salinan eksperimen proyek di sampingnya:
     * &lt;nama&gt;-eksperimen (tambah -2, -3 bila sudah ada). Menyalin
     * seluruh proyek kecuali folder bin/, .riwayat/, dan .git/.
     */
    public static HasilSalinan buatSalinanEksperimen(File dirProyek, String nama) {
        HasilSalinan h = new HasilSalinan();
        if (dirProyek == null || !dirProyek.isDirectory()) {
            h.galat = TeksUI.t("Folder proyek tidak ditemukan.");
            return h;
        }
        String namaBersih = bersihkanLabel(nama);
        if (namaBersih.length() == 0) namaBersih = "tanpa-nama";
        File induk = dirProyek.getParentFile();
        if (induk == null) {
            h.galat = TeksUI.t("Folder proyek tidak ditemukan.");
            return h;
        }
        File tujuan = new File(induk, namaBersih + "-eksperimen");
        int coba = 2;
        while (tujuan.exists()) {
            tujuan = new File(induk, namaBersih + "-eksperimen-" + coba);
            coba++;
        }
        try {
            salinRekursif(dirProyek, tujuan);
            h.dir = tujuan;
            return h;
        } catch (Exception e) {
            h.galat = TeksUI.t("Gagal membuat salinan eksperimen: ") + e.getMessage();
            return h;
        }
    }

    // ------------------------------------------------------- pembantu

    /** Label aman untuk nama folder: huruf/angka/-/_/. saja, maks. 40 karakter. */
    static String bersihkanLabel(String label) {
        if (label == null) label = "";
        String l = label.trim();
        if (l.length() == 0) return "tanpa-label";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
                b.append(c);
            } else if (c == ' ') {
                b.append('-');
            }
            // Karakter lain dibuang.
        }
        String hasil = b.toString();
        // Buang titik/strip di depan (nama folder tersembunyi / tidak sah).
        while (hasil.startsWith(".") || hasil.startsWith("-")) {
            hasil = hasil.substring(1);
        }
        if (hasil.length() == 0) return "tanpa-label";
        if (hasil.length() > 40) hasil = hasil.substring(0, 40);
        return hasil;
    }

    /** Label dari nama folder titik ("20261124-143000_label" -> "label"). */
    static String labelDariNama(String namaDir) {
        if (namaDir == null) return "";
        int garis = namaDir.indexOf('_');
        if (garis >= 0 && garis + 1 < namaDir.length()) {
            return namaDir.substring(garis + 1);
        }
        return namaDir;
    }

    /** True bila nama berkas adalah berkas sumber yang dilacak. */
    static boolean berkasTerlacak(String nama) {
        if (nama == null) return false;
        String kecil = nama.toLowerCase(Locale.US);
        for (int i = 0; i < EKSTENSI.length; i++) {
            if (kecil.endsWith(EKSTENSI[i])) return true;
        }
        return false;
    }

    static boolean abaikanDir(String nama) {
        for (int i = 0; i < ABAIKAN_DIR.length; i++) {
            if (ABAIKAN_DIR[i].equals(nama)) return true;
        }
        return false;
    }

    /** Kumpulkan semua berkas sumber proyek (jalan relatif, urut abjad). */
    static List<EntriBerkas> kumpulkanEntri(File dirProyek) {
        List<String> jalan = new ArrayList<String>();
        kumpulkanRekursif(dirProyek, dirProyek, "", jalan);
        Collections.sort(jalan);
        List<EntriBerkas> hasil = new ArrayList<EntriBerkas>();
        for (int i = 0; i < jalan.size(); i++) {
            File f = new File(dirProyek, jalan.get(i));
            byte[] isi = bacaBita(f);
            if (isi == null) continue;
            hasil.add(new EntriBerkas(jalan.get(i), Perisai.sha256Hex(isi),
                    hitungBaris(isi)));
        }
        return hasil;
    }

    private static void kumpulkanRekursif(File akar, File dir, String awalan,
                                          List<String> hasil) {
        File[] anak = dir.listFiles();
        if (anak == null) return;
        for (int i = 0; i < anak.length; i++) {
            File a = anak[i];
            if (a.isDirectory()) {
                if (abaikanDir(a.getName())) continue;
                kumpulkanRekursif(akar, a, awalan + a.getName() + "/", hasil);
            } else if (berkasTerlacak(a.getName())) {
                hasil.add(awalan + a.getName());
            }
        }
    }

    /** Jumlah baris dari isi bita (hitung '\n'; baris terakhir tanpa '\n' tetap dihitung). */
    static int hitungBaris(byte[] isi) {
        if (isi == null || isi.length == 0) return 0;
        int n = 0;
        for (int i = 0; i < isi.length; i++) {
            if (isi[i] == '\n') n++;
        }
        if (isi[isi.length - 1] != '\n') n++;
        return n;
    }

    /** Metadata titik waktu (jalan -> entri); kosong bila titik/metadata tidak ada. */
    static Map<String, EntriBerkas> metaTitik(File titik) {
        Map<String, EntriBerkas> hasil = new TreeMap<String, EntriBerkas>();
        if (titik == null) return hasil;
        String isi = bacaTeks(new File(titik, NAMA_METADATA));
        if (isi == null) return hasil;
        return uraiMetadata(isi);
    }

    /** Urai isi titik.txt menjadi peta jalan -> entri (baris "berkas <sha> <baris> <jalan>"). */
    static Map<String, EntriBerkas> uraiMetadata(String isi) {
        Map<String, EntriBerkas> hasil = new TreeMap<String, EntriBerkas>();
        if (isi == null) return hasil;
        for (String baris : isi.split("\n", -1)) {
            if (!baris.startsWith("berkas ")) continue;
            String[] bagian = baris.split(" ", 4);
            if (bagian.length < 4) continue;
            try {
                int barisJumlah = Integer.parseInt(bagian[2].trim());
                hasil.put(bagian[3], new EntriBerkas(bagian[3], bagian[1].trim(), barisJumlah));
            } catch (NumberFormatException ignored) {
            }
        }
        return hasil;
    }

    /** Isi arsip ZIP satu titik waktu (jalan -> teks UTF-8); null bila arsip tidak ada. */
    static Map<String, String> bacaArsip(File titik) {
        if (titik == null) return null;
        File zip = new File(titik, NAMA_ARSIP);
        if (!zip.isFile()) return null;
        Map<String, String> hasil = new LinkedHashMap<String, String>();
        ZipFile zf = null;
        try {
            zf = new ZipFile(zip);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                InputStream in = zf.getInputStream(e);
                try {
                    hasil.put(e.getName(), new String(Perisai.bacaSemua(in), "UTF-8"));
                } finally {
                    in.close();
                }
            }
            return hasil;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Semua fungsi di semua berkas arsip: kunci "jalan::namaFungsi" -> nama berkas. */
    static Map<String, String> semuaFungsi(Map<String, String> isiArsip) {
        Map<String, String> hasil = new TreeMap<String, String>();
        for (Map.Entry<String, String> e : isiArsip.entrySet()) {
            for (String nama : fungsiDariIsi(e.getKey(), e.getValue())) {
                hasil.put(e.getKey() + "::" + nama, e.getKey());
            }
        }
        return hasil;
    }

    /** Tulis satu bagian laporan perbandingan ("Judul (N):" + daftar "- ..." / "(tidak ada)"). */
    static void tulisBagian(StringBuilder b, String judul, List<String> daftar) {
        b.append(judul).append(" (").append(daftar.size()).append("):\n");
        if (daftar.isEmpty()) {
            b.append("- ").append(TeksUI.t("(tidak ada)")).append('\n');
        } else {
            for (int i = 0; i < daftar.size(); i++) {
                b.append("- ").append(daftar.get(i)).append('\n');
            }
        }
    }

    /** Tulis arsip ZIP berisi berkas-berkas entri (jalan relatif). */
    static boolean tulisArsip(File dirProyek, List<EntriBerkas> entri, File zip) {
        if (zip.getParentFile() != null) zip.getParentFile().mkdirs();
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)));
            for (int i = 0; i < entri.size(); i++) {
                File f = new File(dirProyek, entri.get(i).jalan);
                zos.putNextEntry(new ZipEntry(entri.get(i).jalan));
                BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(f));
                try {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = masuk.read(buf)) > 0) zos.write(buf, 0, n);
                } finally {
                    masuk.close();
                }
                zos.closeEntry();
            }
            zos.finish();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Hapus semua berkas sumber terlacak di proyek (kecuali folder yang diabaikan). */
    static void hapusBerkasTerlacak(File dirProyek) {
        List<String> jalan = new ArrayList<String>();
        kumpulkanRekursif(dirProyek, dirProyek, "", jalan);
        for (int i = 0; i < jalan.size(); i++) {
            new File(dirProyek, jalan.get(i)).delete();
        }
    }

    /** Ekstrak arsip ZIP ke folder tujuan (dengan pelindung zip-slip). Null bila berhasil. */
    static String ekstrakArsip(File zip, File tujuan) {
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
            String akar = tujuan.getCanonicalPath();
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File keluar = new File(tujuan, e.getName());
                String kanon = keluar.getCanonicalPath();
                if (!kanon.startsWith(akar + File.separator)) continue;
                if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
                BufferedOutputStream out = new BufferedOutputStream(
                        new FileOutputStream(keluar));
                try {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    out.close();
                }
                zin.closeEntry();
            }
            return null;
        } catch (Exception ex) {
            return ex.getMessage();
        } finally {
            if (zin != null) {
                try {
                    zin.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Salin satu folder rekursif (kecuali bin/.riwayat/.git di dalamnya). */
    static void salinRekursif(File dari, File ke) throws Exception {
        if (dari.isDirectory()) {
            ke.mkdirs();
            File[] anak = dari.listFiles();
            if (anak == null) return;
            for (int i = 0; i < anak.length; i++) {
                if (anak[i].isDirectory() && abaikanDir(anak[i].getName())) continue;
                salinRekursif(anak[i], new File(ke, anak[i].getName()));
            }
        } else {
            if (ke.getParentFile() != null) ke.getParentFile().mkdirs();
            BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(dari));
            BufferedOutputStream keluar = new BufferedOutputStream(new FileOutputStream(ke));
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
            } finally {
                try {
                    masuk.close();
                } catch (Exception ignored) {
                }
                try {
                    keluar.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Baca seluruh isi berkas menjadi bita; null bila gagal. */
    static byte[] bacaBita(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            ByteArrayOutputStream keluar = new ByteArrayOutputStream();
            BufferedInputStream masuk = new BufferedInputStream(new FileInputStream(f));
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = masuk.read(buf)) > 0) keluar.write(buf, 0, n);
            } finally {
                masuk.close();
            }
            return keluar.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Tulis teks ke berkas (UTF-8). */
    static void tulisTeks(File f, String isi) throws Exception {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(isi);
        } finally {
            w.close();
        }
    }

    /** Baca berkas teks (UTF-8); null bila tidak bisa dibaca. */
    static String bacaTeks(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            byte[] data = bacaBita(f);
            return data == null ? null : new String(data, "UTF-8");
        } catch (Exception ignored) {
            return null;
        }
    }
}
