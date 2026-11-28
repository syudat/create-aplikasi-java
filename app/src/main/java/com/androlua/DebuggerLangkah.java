package com.androlua;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Debugger Langkah-Demi-Langkah dan Inspektur Memori Waktu-Nyata
 * (Fitur 6, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer.
 * Dua bagian:
 *
 * 1) Debugger JEJAK (tetap ada, tidak dihapus): menelusuri jejak
 *    eksekusi yang direkam atau ditulis tangan baris demi baris, dengan
 *    titik henti, langkah satu baris, lanjutkan, ulang, dan kini MASUK
 *    KE FUNGSI / KELUAR DARI FUNGSI dengan tumpukan panggilan. Jejak
 *    boleh membawa kolom ke-4 "fungsi=NamaFungsi" per baris supaya
 *    masuk/keluar fungsi dapat mengikuti bingkai; tanpa kolom itu,
 *    masuk ke fungsi melangkah satu baris dan membacakan nama fungsi
 *    yang dipanggil pada baris aktif.
 *
 * 2) Klien JDWP (JdwpKlien, baru): klien protokol kabel JDWP murni-Java
 *    tanpa JDI — JDI adalah komponen JDK desktop dan TIDAK ADA di
 *    Android (android-full.jar tidak memuat com.sun.jdi; diperiksa
 *    langsung). Klien ini bisa jabat tangan, membaca versi VM, mencari
 *    kelas, membaca tabel baris, memasang titik henti, meminta langkah
 *    (masuk/lewat/keluar), menahan/melanjutkan VM, dan membaca peristiwa
 *    jeda — lewat TCP (bila adb forward sudah dibuat dari luar) atau
 *    lewat mode dengar (agen JDWP target yang diluncurkan dengan
 *    --attach-agent menelepon balik ke IDE).
 *
 * BATAS YANG SUDAH TERATASI — JEMBATAN ADB DI DALAM APLIKASI:
 * Sejak 5.8 (debugger sungguhan), aplikasi ini MEMBAWA jembatan adb
 * sendiri (AdbKlien + AdbKunci + AdbPasangan + Spake2, murni-Java):
 * lewat debugging nirkabel (TLS + pairing SPAKE2 lewat Opsi
 * Pengembang) atau adb TCP, aplikasi tersambung ke adbd di perangkat
 * yang sama, membuka "jdwp:<pid>" untuk proses target yang
 * debuggable, lalu JdwpKlien di bawah berbicara JDWP langsung dengan
 * VM target: tahan, titik henti, langkah, bingkai, dan nilai variabel.
 * Yang tetap butuh perangkat sungguhan: pairing dan sambungan adb
 * (tidak bisa diuji penuh di komputer — loopback VM ini dicegat
 * proksi sandbox); logika protokolnya diuji di JVM melawan adbd
 * palsu dan agen JDWP palsu di atas pipa.
 */
public class DebuggerLangkah {

    /** Satu langkah jejak: baris sumber + variabel saat itu. */
    public static class Langkah {
        public int baris = 0;
        public String kode = "";
        public Map<String, String> variabel = new LinkedHashMap<String, String>();
        /** Nama fungsi/bingkai langkah ini (boleh kosong). */
        public String fungsi = "";
        public String toString() {
            return baris + ": " + kode;
        }
    }

    /** Satu sesi debug atas sebuah jejak. */
    public static class Sesi {
        public List<Langkah> jejak = new ArrayList<Langkah>();
        public List<Integer> titikHenti = new ArrayList<Integer>();
        public int posisi = -1;
        /** Tumpukan panggilan: nama fungsi yang sedang dimasuki. */
        public List<String> tumpukan = new ArrayList<String>();

        /** Langkah aktif (null bila belum mulai / sudah habis). */
        public Langkah aktif() {
            if (posisi < 0 || posisi >= jejak.size()) return null;
            return jejak.get(posisi);
        }

        /** True bila posisi aktif adalah titik henti. */
        public boolean diTitikHenti() {
            Langkah a = aktif();
            if (a == null) return false;
            return titikHenti.contains(Integer.valueOf(a.baris));
        }

        /** Maju satu langkah; true bila masih ada langkah. */
        public boolean langkah() {
            if (posisi + 1 >= jejak.size()) return false;
            posisi++;
            return true;
        }

        /** Lanjut sampai titik henti berikutnya (atau akhir jejak). */
        public boolean lanjut() {
            while (posisi + 1 < jejak.size()) {
                posisi++;
                if (diTitikHenti()) return true;
            }
            return false;
        }

        /** Mundur ke awal jejak. */
        public void ulang() {
            posisi = -1;
            tumpukan.clear();
        }

        /** Nama fungsi pada langkah aktif (dari kolom fungsi / tumpukan). */
        public String fungsiAktif() {
            Langkah a = aktif();
            if (a != null && a.fungsi.length() > 0) return a.fungsi;
            if (!tumpukan.isEmpty()) return tumpukan.get(tumpukan.size() - 1);
            return "";
        }

        /**
         * Masuk ke fungsi: dari baris aktif yang memanggil fungsi,
         * maju ke langkah pertama di dalam fungsi yang dipanggil.
         * Bila jejak membawa kolom fungsi, majunya mengikuti pergantian
         * nama fungsi; bila tidak, melangkah satu baris dan mencatat
         * nama fungsi yang dipanggil. Mengembalikan true bila bergerak.
         */
        public boolean masukFungsi() {
            Langkah a = aktif();
            if (a == null) return langkah();
            String panggil = panggilDariKode(a.kode);
            String fungsiKini = a.fungsi.length() > 0 ? a.fungsi : fungsiAktif();
            if (panggil != null) tumpukan.add(panggil);
            else if (fungsiKini.length() > 0) tumpukan.add(fungsiKini);
            if (jejakPunyaFungsi()) {
                while (posisi + 1 < jejak.size()) {
                    posisi++;
                    Langkah n = jejak.get(posisi);
                    if (!fungsiKini.equals(n.fungsi)) return true;
                }
                return false;
            }
            return langkah();
        }

        /**
         * Keluar dari fungsi: kembali ke bingkai pemanggil. Bila jejak
         * membawa kolom fungsi, maju sampai langkah yang fungsinya sama
         * dengan pemanggil; bila tidak, melangkah satu baris.
         */
        public boolean keluarFungsi() {
            String dalam = fungsiAktif();
            if (!tumpukan.isEmpty()) tumpukan.remove(tumpukan.size() - 1);
            String pemanggil = tumpukan.isEmpty() ? "" : tumpukan.get(tumpukan.size() - 1);
            if (jejakPunyaFungsi()) {
                while (posisi + 1 < jejak.size()) {
                    posisi++;
                    String f = jejak.get(posisi).fungsi;
                    if (!dalam.equals(f) && (pemanggil.length() == 0 || pemanggil.equals(f))) {
                        return true;
                    }
                }
                return false;
            }
            return langkah();
        }

        private boolean jejakPunyaFungsi() {
            for (int i = 0; i < jejak.size(); i++) {
                if (jejak.get(i).fungsi.length() > 0) return true;
            }
            return false;
        }
    }

    private static final Pattern PANGGILAN_KODE = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    /** Nama fungsi pertama yang dipanggil pada satu baris kode (null bila tidak ada). */
    public static String panggilDariKode(String kode) {
        if (kode == null) return null;
        Matcher m = PANGGILAN_KODE.matcher(kode);
        while (m.find()) {
            String nama = m.group(1);
            if ("if".equals(nama) || "for".equals(nama) || "while".equals(nama)
                    || "switch".equals(nama) || "catch".equals(nama)
                    || "return".equals(nama) || "super".equals(nama)) continue;
            return nama;
        }
        return null;
    }

    /**
     * Urai teks jejak. Format per baris:
     * "12|int total = harga * jumlah;|harga=5000, jumlah=3, total=15000"
     * Bagian variabel boleh kosong. Kolom ke-4 opsional:
     * "fungsi=hitungPajak" menandai bingkai langkah itu.
     */
    public static List<Langkah> uraiJejak(String teks) {
        List<Langkah> hasil = new ArrayList<Langkah>();
        if (teks == null) return hasil;
        String[] baris = teks.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.length() == 0) continue;
            String[] bagian = b.split("\\|", -1);
            if (bagian.length < 2) continue;
            Langkah l = new Langkah();
            try {
                l.baris = Integer.parseInt(bagian[0].trim());
            } catch (Throwable t) {
                continue;
            }
            l.kode = bagian[1].trim();
            if (bagian.length >= 3 && bagian[2].trim().length() > 0) {
                String[] pasangan = bagian[2].split(",");
                for (int j = 0; j < pasangan.length; j++) {
                    int sama = pasangan[j].indexOf('=');
                    if (sama <= 0) continue;
                    l.variabel.put(pasangan[j].substring(0, sama).trim(),
                            pasangan[j].substring(sama + 1).trim());
                }
            }
            if (bagian.length >= 4) {
                String keempat = bagian[3].trim();
                if (keempat.startsWith("fungsi=")) {
                    l.fungsi = keempat.substring("fungsi=".length()).trim();
                }
            }
            hasil.add(l);
        }
        return hasil;
    }

    /** Tambah titik henti (tanpa ganda). */
    public static void tambahTitikHenti(Sesi sesi, int baris) {
        if (sesi == null || baris < 1) return;
        Integer b = Integer.valueOf(baris);
        if (!sesi.titikHenti.contains(b)) sesi.titikHenti.add(b);
    }

    /** Hapus titik henti. */
    public static void hapusTitikHenti(Sesi sesi, int baris) {
        if (sesi == null) return;
        sesi.titikHenti.remove(Integer.valueOf(baris));
    }

    /** Daftar variabel aktif sebagai teks "nama = nilai" per baris. */
    public static String teksVariabel(Langkah langkah) {
        if (langkah == null) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : langkah.variabel.entrySet()) {
            if (b.length() > 0) b.append("\n");
            b.append(e.getKey()).append(" = ").append(e.getValue());
        }
        return b.toString();
    }

    /** Baris aktif sebagai teks "baris: kode". */
    public static String teksBarisAktif(Sesi sesi) {
        Langkah a = sesi == null ? null : sesi.aktif();
        if (a == null) return "";
        return a.baris + ": " + a.kode;
    }

    /** Nilai variabel pada langkah aktif (null bila tidak ada). */
    public static String nilaiVariabel(Sesi sesi, String nama) {
        Langkah a = sesi == null ? null : sesi.aktif();
        if (a == null || nama == null) return null;
        return a.variabel.get(nama);
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(Sesi sesi) {
        if (sesi == null) return "Baris aktif: tidak ada.";
        Langkah a = sesi.aktif();
        if (a == null) return "Baris aktif: belum mulai. Jejak: " + sesi.jejak.size() + " langkah.";
        return "Baris aktif: " + a.baris + ". Variabel: " + a.variabel.size()
                + ". Titik henti: " + sesi.titikHenti.size() + ".";
    }

    /**
     * Penjelasan bila sambungan adb/JDWP gagal: apa yang harus
     * disiapkan pengguna (tetap jujur soal batasnya).
     */
    public static String diagnosisTanpaAdb() {
        return "Sambungan ke proses sungguhan butuh jembatan adb di "
                + "perangkat ini. Yang dibutuhkan: (1) aplikasi target "
                + "dibangun debuggable, (2) debugging nirkabel diaktifkan "
                + "di Opsi Pengembang lalu dipasangkan dengan kode pairing "
                + "di layar ini (atau debugging USB + adb TCP), (3) proses "
                + "target sedang berjalan saat tombol daftar proses ditekan. "
                + "Bila adbd menolak kunci, hapus pairing lama di Opsi "
                + "Pengembang lalu pasangkan ulang dari layar ini.";
    }

    // ============================ KLIEN JDWP ============================

    /** Galat protokol JDWP (kode galat JDWP di dalam pesan). */
    public static class JdwpGalat extends IOException {
        public final int kode;
        public JdwpGalat(int k, String pesan) {
            super(pesan);
            kode = k;
        }
    }

    /** Satu peristiwa jeda dari VM target (titik henti / langkah). */
    public static class PeristiwaJdwp {
        /** 1 = langkah tunggal, 2 = titik henti. */
        public int jenis = 0;
        public int idPermintaan = 0;
        public long threadId = 0;
        public long classId = 0;
        public long methodId = 0;
        public long indeksKode = 0;
    }

    /** Satu metode dari jawaban ReferenceType.Methods. */
    public static class MetodeJdwp {
        public long id = 0;
        public String nama = "";
        public String tanda = "";
    }

    /** Satu variabel lokal dari jawaban Method.VariableTable. */
    public static class VariabelJdwp {
        public long indeksKode = 0;
        public String nama = "";
        public String tanda = "";
        public int panjang = 0;
        public int slot = 0;
        /** Penanda tipe JDWP dari huruf pertama tanda. */
        public byte penanda() {
            if (tanda == null || tanda.length() == 0) return (byte) 'I';
            char c = tanda.charAt(0);
            if (c == 'L' || c == '[') return (byte) (c == '[' ? 'a' : 'L');
            return (byte) c;
        }
    }

    /** Satu bingkai tumpukan (ThreadReference.Frames). */
    public static class BingkaiJdwp {
        public long idBingkai = 0;
        public long classId = 0;
        public long methodId = 0;
        public long indeksKode = 0;
        public String namaKelas = "";
        public String namaMetode = "";
        public int baris = -1;
    }

    /** Satu nilai variabel dari StackFrame.GetValues. */
    public static class NilaiJdwp {
        /** Slot lokal JDWP. */
        public int slot = 0;
        /** Penanda tipe JDWP ('I', 'J', 'Z', 's', 'L', ...). */
        public byte penanda = 0;
        /** Nilai sebagai teks yang bisa dibacakan. */
        public String teks = "";
        /** ID objek bila nilainya objek (0 bila bukan objek). */
        public long idObjek = 0;
    }

    /** Kursor baca big-endian di atas jawaban JDWP. */
    static class Kursor {
        final byte[] d;
        int p = 0;
        Kursor(byte[] data) { d = data; }
        int u8() { return d[p++] & 0xFF; }
        int u16() {
            int v = 0;
            for (int i = 0; i < 2; i++) v = (v << 8) | (d[p++] & 0xFF);
            return v;
        }
        int u32() {
            int v = 0;
            for (int i = 0; i < 4; i++) v = (v << 8) | (d[p++] & 0xFF);
            return v;
        }
        long u64() {
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (d[p++] & 0xFF);
            return v;
        }
        float f32() { return Float.intBitsToFloat(u32()); }
        double f64() { return Double.longBitsToDouble(u64()); }
        String str() throws IOException {
            int n = u32();
            if (n < 0 || p + n > d.length) throw new IOException("String JDWP rusak.");
            String s = new String(d, p, n, "UTF-8");
            p += n;
            return s;
        }
    }

    /**
     * Klien protokol kabel JDWP murni-Java (tanpa JDI). Dipakai lewat
     * TCP bila adb forward sudah dibuat dari luar, atau lewat mode
     * dengar: dengarkan() membuka ServerSocket dan menunggu agen JDWP
     * target (yang diluncurkan dengan --attach-agent) menelepon balik.
     * Semua perintah berjalan di utas pemanggil; panggil dari utas latar.
     */
    public static class JdwpKlien {
        /** Ukuran langkah: per baris sumber. */
        public static final int UKURAN_BARIS = 1;
        /** Kedalaman langkah: masuk ke fungsi. */
        public static final int MASUK = 0;
        /** Kedalaman langkah: lewati pemanggilan. */
        public static final int LEWAT = 1;
        /** Kedalaman langkah: keluar dari fungsi. */
        public static final int KELUAR = 2;

        private Socket soket;
        private DataInputStream masuk;
        private DataOutputStream keluar;
        private int idBerikut = 1;
        private boolean tersambung = false;
        private String versi = "";
        private final List<byte[]> peristiwaTertunda = new ArrayList<byte[]>();

        public boolean tersambung() { return tersambung; }
        public String versi() { return versi; }

        private static byte[] jabat() throws IOException {
            return "JDWP-Handshake".getBytes("ISO-8859-1");
        }

        private static void bacaTepat(DataInputStream in, byte[] buf) throws IOException {
            int baca = 0;
            while (baca < buf.length) {
                int n = in.read(buf, baca, buf.length - baca);
                if (n < 0) throw new IOException("Sambungan JDWP terputus.");
                baca += n;
            }
        }

        private void lakukanJabatTangan() throws IOException {
            byte[] j = jabat();
            keluar.write(j);
            keluar.flush();
            byte[] balas = new byte[j.length];
            bacaTepat(masuk, balas);
            for (int i = 0; i < j.length; i++) {
                if (j[i] != balas[i]) {
                    throw new IOException("Jawaban jabat tangan JDWP tidak cocok.");
                }
            }
        }

        private void terimaJabatTangan() throws IOException {
            byte[] j = jabat();
            byte[] balas = new byte[j.length];
            bacaTepat(masuk, balas);
            for (int i = 0; i < j.length; i++) {
                if (j[i] != balas[i]) {
                    throw new IOException("Jawaban jabat tangan JDWP tidak cocok.");
                }
            }
            keluar.write(j);
            keluar.flush();
        }

        /**
         * Sambung lewat aliran yang sudah dibuka (dipakai uji tanpa
         * soket: klien memulai jabat tangan seperti pada sambung()).
         */
        String sambungAliran(DataInputStream in, DataOutputStream out) throws IOException {
            masuk = in;
            keluar = out;
            lakukanJabatTangan();
            tersambung = true;
            versi = mintaVersi();
            return versi;
        }

        /**
         * Sambung lewat aliran yang sudah dibuka, sebagai pelayan
         * (dipakai uji tanpa soket: agen yang menelepon balik memulai
         * jabat tangan lebih dulu, seperti pada dengarkan()).
         */
        String sambungAliranSebagaiPelayan(DataInputStream in, DataOutputStream out)
                throws IOException {
            masuk = in;
            keluar = out;
            terimaJabatTangan();
            tersambung = true;
            versi = mintaVersi();
            return versi;
        }

        /**
         * Sambung ke agen JDWP target lewat TCP (umumnya 127.0.0.1
         * dengan port yang sudah diteruskan adb dari luar). Mengembalikan
         * versi VM bila jabat tangan dan perintah Version berhasil.
         */
        public String sambung(String host, int port, int timeoutMs) throws IOException {
            if (host == null || host.length() == 0) host = "127.0.0.1";
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(host, port),
                        timeoutMs > 0 ? timeoutMs : 5000);
                s.setSoTimeout(timeoutMs > 0 ? timeoutMs : 5000);
            } catch (IOException e) {
                try { s.close(); } catch (Throwable ignored) {
                }
                throw new IOException("Tidak bisa tersambung ke JDWP " + host + ":" + port
                        + " (" + e.getMessage() + "). Tanpa adb forward atau agen "
                        + "--attach-agent, tidak ada yang mendengarkan di port itu.", e);
            }
            soket = s;
            masuk = new DataInputStream(s.getInputStream());
            keluar = new DataOutputStream(s.getOutputStream());
            lakukanJabatTangan();
            tersambung = true;
            versi = mintaVersi();
            return versi;
        }

        /**
         * Mode dengar: buka port lokal dan tunggu agen JDWP target
         * menelepon balik (arah balik --attach-agent). Mengembalikan
         * versi VM bila agen tersambung dan menjawab Version.
         */
        public String dengarkan(int port, int timeoutMs) throws IOException {
            ServerSocket pelayan = new ServerSocket(port);
            try {
                pelayan.setSoTimeout(timeoutMs > 0 ? timeoutMs : 30000);
                soket = pelayan.accept();
                soket.setSoTimeout(timeoutMs > 0 ? timeoutMs : 30000);
            } catch (IOException e) {
                try { pelayan.close(); } catch (Throwable ignored) {
                }
                throw new IOException("Tidak ada agen JDWP yang menelepon balik ke port "
                        + port + " (" + e.getMessage() + "). Target harus diluncurkan "
                        + "lewat shell dengan am --attach-agent libjdwp.so.", e);
            } finally {
                try { pelayan.close(); } catch (Throwable ignored) {
                }
            }
            masuk = new DataInputStream(soket.getInputStream());
            keluar = new DataOutputStream(soket.getOutputStream());
            terimaJabatTangan();
            tersambung = true;
            versi = mintaVersi();
            return versi;
        }

        /** Tutup sambungan JDWP. */
        public void tutup() {
            tersambung = false;
            try {
                if (soket != null) soket.close();
            } catch (Throwable ignored) {
            }
            soket = null;
        }

        private static byte[] tulisU32(int v) throws IOException {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(v);
            o.flush();
            return b.toByteArray();
        }

        private static byte[] tulisU64(long v) throws IOException {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeLong(v);
            o.flush();
            return b.toByteArray();
        }

        private static byte[] tulisString(String s) throws IOException {
            if (s == null) s = "";
            byte[] isi = s.getBytes("UTF-8");
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(isi.length);
            o.write(isi);
            o.flush();
            return b.toByteArray();
        }

        private static byte[] gabung(byte[]... bagian) throws IOException {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < bagian.length; i++) b.write(bagian[i]);
            return b.toByteArray();
        }

        /** Baca satu paket JDWP utuh (termasuk header 11 bita). */
        private byte[] bacaPaket() throws IOException {
            byte[] kepala = new byte[4];
            bacaTepat(masuk, kepala);
            int panjang = ((kepala[0] & 0xFF) << 24) | ((kepala[1] & 0xFF) << 16)
                    | ((kepala[2] & 0xFF) << 8) | (kepala[3] & 0xFF);
            if (panjang < 11 || panjang > 16 * 1024 * 1024) {
                throw new IOException("Panjang paket JDWP tidak sah: " + panjang + ".");
            }
            byte[] paket = new byte[panjang];
            System.arraycopy(kepala, 0, paket, 0, 4);
            bacaTepat(masuk, paket, 4, panjang - 4);
            return paket;
        }

        private static void bacaTepat(DataInputStream in, byte[] buf, int off, int len)
                throws IOException {
            int baca = 0;
            while (baca < len) {
                int n = in.read(buf, off + baca, len - baca);
                if (n < 0) throw new IOException("Sambungan JDWP terputus.");
                baca += n;
            }
        }

        /**
         * Kirim satu perintah dan kembalikan data balasannya. Peristiwa
         * (flags 0, command set 64) yang datang di sela dibuang ke antrean
         * untuk bacaPeristiwa().
         */
        public byte[] kirim(int setPerintah, int perintah, byte[] data) throws IOException {
            if (!tersambung) throw new IOException("Belum tersambung ke JDWP.");
            int id = idBerikut;
            idBerikut += 2;
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(11 + (data == null ? 0 : data.length));
            o.writeInt(id);
            o.writeByte(0);
            o.writeByte(setPerintah);
            o.writeByte(perintah);
            if (data != null) o.write(data);
            o.flush();
            keluar.write(b.toByteArray());
            keluar.flush();
            while (true) {
                byte[] paket = bacaPaket();
                int idBalas = ((paket[4] & 0xFF) << 24) | ((paket[5] & 0xFF) << 16)
                        | ((paket[6] & 0xFF) << 8) | (paket[7] & 0xFF);
                int flags = paket[8] & 0xFF;
                if (flags == 0) {
                    peristiwaTertunda.add(paket);
                    continue;
                }
                if (idBalas != id) continue;
                int galat = ((paket[9] & 0xFF) << 8) | (paket[10] & 0xFF);
                if (galat != 0) {
                    throw new JdwpGalat(galat, "Perintah JDWP " + setPerintah + "/"
                            + perintah + " ditolak: " + pesanGalat(galat) + " (" + galat + ").");
                }
                byte[] isi = new byte[paket.length - 11];
                System.arraycopy(paket, 11, isi, 0, isi.length);
                return isi;
            }
        }

        /** VirtualMachine.Version. */
        public String mintaVersi() throws IOException {
            byte[] balas = kirim(1, 1, new byte[0]);
            Kursor k = new Kursor(balas);
            String deskripsi = k.str();
            int mayor = k.u32();
            int minor = k.u32();
            String versiVm = k.str();
            String nama = k.str();
            return deskripsi + " " + versiVm + " (" + nama + " " + mayor + "." + minor + ")";
        }

        /** VirtualMachine.ClassesBySignature: cari kelas dari tanda "Lcom/x/Y;". */
        public long cariKelas(String tanda) throws IOException {
            byte[] balas = kirim(1, 2, tulisString(tanda));
            Kursor k = new Kursor(balas);
            int jumlah = k.u32();
            if (jumlah <= 0) return 0;
            k.u8();
            long id = k.u64();
            return id;
        }

        /** ReferenceType.Methods: daftar metode sebuah kelas. */
        public List<MetodeJdwp> metodeKelas(long classId) throws IOException {
            byte[] balas = kirim(2, 5, tulisU64(classId));
            Kursor k = new Kursor(balas);
            int jumlah = k.u32();
            List<MetodeJdwp> hasil = new ArrayList<MetodeJdwp>();
            for (int i = 0; i < jumlah; i++) {
                MetodeJdwp m = new MetodeJdwp();
                m.id = k.u64();
                m.nama = k.str();
                m.tanda = k.str();
                k.u32();
                hasil.add(m);
            }
            return hasil;
        }

        /** Method.LineTable (set 6, perintah 1): peta nomor baris -> indeks kode. */
        public Map<Integer, Long> tabelBaris(long classId, long methodId) throws IOException {
            byte[] balas = kirim(6, 1, gabung(tulisU64(classId), tulisU64(methodId)));
            Kursor k = new Kursor(balas);
            k.u64();
            k.u64();
            int jumlah = k.u32();
            Map<Integer, Long> hasil = new LinkedHashMap<Integer, Long>();
            for (int i = 0; i < jumlah; i++) {
                long indeks = k.u64();
                int baris = k.u32();
                if (!hasil.containsKey(Integer.valueOf(baris))) {
                    hasil.put(Integer.valueOf(baris), Long.valueOf(indeks));
                }
            }
            return hasil;
        }

        /**
         * Pasang titik henti di lokasi (classId, methodId, indeksKode).
         * Mengembalikan id permintaan (untuk menghapusnya nanti).
         */
        public int pasangTitikHenti(long classId, long methodId, long indeksKode)
                throws IOException {
            byte[] lokasi = gabung(new byte[]{1}, tulisU64(classId),
                    tulisU64(methodId), tulisU64(indeksKode));
            byte[] data = gabung(new byte[]{2, 2}, tulisU32(1),
                    gabung(new byte[]{7}, lokasi));
            byte[] balas = kirim(15, 1, data);
            return new Kursor(balas).u32();
        }

        /**
         * Minta langkah tunggal pada satu thread. Kedalaman: MASUK (0),
         * LEWAT (1), KELUAR (2). Mengembalikan id permintaan.
         */
        public int mintaLangkah(long threadId, int kedalaman) throws IOException {
            byte[] langkah = gabung(tulisU64(threadId), tulisU32(UKURAN_BARIS),
                    tulisU32(kedalaman));
            byte[] data = gabung(new byte[]{1, 2}, tulisU32(1),
                    gabung(new byte[]{10}, langkah));
            byte[] balas = kirim(15, 1, data);
            return new Kursor(balas).u32();
        }

        /** Hapus satu permintaan peristiwa (jenis 1 = langkah, 2 = titik henti). */
        public void hapusPermintaan(int jenis, int idPermintaan) throws IOException {
            kirim(15, 2, gabung(new byte[]{(byte) jenis}, tulisU32(idPermintaan)));
        }

        /** VirtualMachine.Suspend: tahan seluruh VM target. */
        public void tahan() throws IOException {
            kirim(1, 8, new byte[0]);
        }

        /** VirtualMachine.Resume: lanjutkan seluruh VM target. */
        public void lanjutkan() throws IOException {
            kirim(1, 9, new byte[0]);
        }

        /** VirtualMachine.AllThreads: daftar ID semua thread target. */
        public List<Long> daftarThread() throws IOException {
            byte[] balas = kirim(1, 4, new byte[0]);
            Kursor k = new Kursor(balas);
            int jumlah = k.u32();
            List<Long> hasil = new ArrayList<Long>();
            for (int i = 0; i < jumlah; i++) hasil.add(Long.valueOf(k.u64()));
            return hasil;
        }

        /** ThreadReference.Name: nama satu thread. */
        public String namaThread(long threadId) throws IOException {
            byte[] balas = kirim(11, 1, tulisU64(threadId));
            return new Kursor(balas).str();
        }

        /** ThreadReference.FrameCount: jumlah bingkai satu thread. */
        public int jumlahBingkai(long threadId) throws IOException {
            byte[] balas = kirim(11, 7, tulisU64(threadId));
            return new Kursor(balas).u32();
        }

        /**
         * ThreadReference.Frames: bingkai tumpukan satu thread (thread
         * harus ditahan dulu). Mengisi nama kelas/metode + nomor baris
         * bila tabel baris tersedia.
         */
        public List<BingkaiJdwp> bingkai(long threadId, int mulai, int jumlah)
                throws IOException {
            byte[] balas = kirim(11, 6, gabung(tulisU64(threadId),
                    tulisU32(mulai), tulisU32(jumlah)));
            Kursor k = new Kursor(balas);
            int n = k.u32();
            List<BingkaiJdwp> hasil = new ArrayList<BingkaiJdwp>();
            for (int i = 0; i < n; i++) {
                BingkaiJdwp b = new BingkaiJdwp();
                b.idBingkai = k.u64();
                k.u8(); // penanda tipe lokasi (1 = classID)
                b.classId = k.u64();
                b.methodId = k.u64();
                b.indeksKode = k.u64();
                hasil.add(b);
            }
            for (int i = 0; i < hasil.size(); i++) {
                BingkaiJdwp b = hasil.get(i);
                try { b.namaKelas = namaKelas(b.classId); } catch (Throwable ignored) {
                }
                try {
                    List<MetodeJdwp> ms = metodeKelas(b.classId);
                    for (int j = 0; j < ms.size(); j++) {
                        if (ms.get(j).id == b.methodId) {
                            b.namaMetode = ms.get(j).nama;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    Map<Integer, Long> tb = tabelBaris(b.classId, b.methodId);
                    long terbaik = -1;
                    int barisTerbaik = -1;
                    for (Map.Entry<Integer, Long> e : tb.entrySet()) {
                        long idx = e.getValue().longValue();
                        if (idx <= b.indeksKode && idx >= terbaik) {
                            terbaik = idx;
                            barisTerbaik = e.getKey().intValue();
                        }
                    }
                    b.baris = barisTerbaik;
                } catch (Throwable ignored) {
                }
            }
            return hasil;
        }

        /** ReferenceType.Signature: tanda kelas ("Lcom/x/Y;") -> "com.x.Y". */
        public String namaKelas(long classId) throws IOException {
            byte[] balas = kirim(2, 1, tulisU64(classId));
            String tanda = new Kursor(balas).str();
            if (tanda.startsWith("L") && tanda.endsWith(";")) {
                return tanda.substring(1, tanda.length() - 1).replace('/', '.');
            }
            return tanda;
        }

        /**
         * StackFrame.GetValues: nilai variabel lokal pada satu bingkai.
         * slot[i] = nomor slot, penanda[i] = penanda tipe JDWP slot itu
         * (dari Method.VariableTable; bila tidak ada, pakai 'I' untuk
         * int, 'J' long, 'Z' boolean, 's' String, 'L' objek).
         * Nilai String dibaca isinya; objek lain ditampilkan sebagai
         * "objek#<id>".
         */
        public List<NilaiJdwp> nilaiBingkai(long frameId, int[] slot, byte[] penanda)
                throws IOException {
            if (slot == null || penanda == null || slot.length != penanda.length) {
                throw new IOException("Daftar slot dan penanda tidak cocok.");
            }
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeLong(frameId);
            o.writeInt(slot.length);
            for (int i = 0; i < slot.length; i++) {
                o.writeInt(slot[i]);
                o.writeByte(penanda[i]);
            }
            o.flush();
            byte[] balas = kirim(16, 1, b.toByteArray());
            Kursor k = new Kursor(balas);
            int jumlah = k.u32();
            List<NilaiJdwp> hasil = new ArrayList<NilaiJdwp>();
            for (int i = 0; i < jumlah; i++) {
                NilaiJdwp v = new NilaiJdwp();
                v.penanda = (byte) k.u8();
                v.teks = bacaNilai(k, v.penanda, v);
                hasil.add(v);
            }
            for (int i = 0; i < hasil.size() && i < slot.length; i++) {
                hasil.get(i).slot = slot[i];
            }
            return hasil;
        }

        /** Baca satu nilai bertanda dari kursor; isi v.idObjek bila objek. */
        private String bacaNilai(Kursor k, byte tag, NilaiJdwp v) throws IOException {
            switch (tag) {
                case 'Z': return k.u8() != 0 ? "true" : "false";
                case 'B': return String.valueOf((byte) k.u8());
                case 'C': return String.valueOf((char) k.u16());
                case 'S': return String.valueOf((short) k.u16());
                case 'I': return String.valueOf(k.u32());
                case 'J': return String.valueOf(k.u64());
                case 'F': return String.valueOf(k.f32());
                case 'D': return String.valueOf(k.f64());
                case 's': {
                    long id = k.u64();
                    v.idObjek = id;
                    if (id == 0) return "null";
                    try { return "\"" + nilaiString(id) + "\""; } catch (Throwable t) {
                        return "String#" + id;
                    }
                }
                case 'L':
                case 't':
                case 'g':
                case 'e':
                case 'c':
                case 'a': {
                    long id = k.u64();
                    v.idObjek = id;
                    if (id == 0) return "null";
                    return (tag == 'a' ? "larik#" : "objek#") + id;
                }
                case 'V': return "void";
                default: return "?";
            }
        }

        /** Method.VariableTable: variabel lokal sebuah metode. */
        public List<VariabelJdwp> tabelVariabel(long classId, long methodId)
                throws IOException {
            byte[] balas = kirim(6, 2, gabung(tulisU64(classId), tulisU64(methodId)));
            Kursor k = new Kursor(balas);
            int argCount = k.u32();
            int jumlah = k.u32();
            List<VariabelJdwp> hasil = new ArrayList<VariabelJdwp>();
            for (int i = 0; i < jumlah; i++) {
                VariabelJdwp v = new VariabelJdwp();
                v.indeksKode = k.u64();
                v.nama = k.str();
                v.tanda = k.str();
                v.panjang = k.u32();
                v.slot = k.u32();
                hasil.add(v);
            }
            return hasil;
        }

        /** StringReference.Value: isi sebuah objek String target. */
        public String nilaiString(long stringId) throws IOException {
            byte[] balas = kirim(10, 1, tulisU64(stringId));
            return new Kursor(balas).str();
        }

        /**
         * Baca satu peristiwa jeda (blokir sampai ada). Peristiwa yang
         * sudah tertampung saat kirim() dipakai lebih dulu.
         */
        public PeristiwaJdwp bacaPeristiwa() throws IOException {
            byte[] paket;
            if (!peristiwaTertunda.isEmpty()) {
                paket = peristiwaTertunda.remove(0);
            } else {
                paket = bacaPaket();
            }
            Kursor k = new Kursor(paket);
            k.p = 11;
            k.u8(); // kebijakan penangguhan
            int jumlah = k.u32();
            if (jumlah <= 0) throw new IOException("Paket peristiwa JDWP kosong.");
            PeristiwaJdwp p = new PeristiwaJdwp();
            p.jenis = k.u8();
            p.idPermintaan = k.u32();
            if (p.jenis == 1 || p.jenis == 2) {
                p.threadId = k.u64();
                k.u8(); // penanda tipe lokasi
                p.classId = k.u64();
                p.methodId = k.u64();
                p.indeksKode = k.u64();
            }
            return p;
        }

        static String pesanGalat(int kode) {
            switch (kode) {
                case 10: return "thread tidak sah";
                case 11: return "thread tidak ditangguhkan";
                case 13: return "objek tidak sah";
                case 20: return "kelas belum dimuat";
                case 21: return "perintah belum diimplementasikan";
                case 23: return "tipe tidak sah";
                case 30: return "tidak ada lagi bingkai";
                case 31: return "informasi tidak tersedia";
                case 32: return "operasi tidak didukung";
                case 35: return "metode tidak sah";
                case 41: return "kelas tidak siap";
                case 51: return "slot tidak sah";
                case 52: return "lokasi tidak sah";
                case 99: return "VM sudah mati";
                case 112: return "tipe tidak ditemukan";
                default: return "galat JDWP";
            }
        }
    }
}
