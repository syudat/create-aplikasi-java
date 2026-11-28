package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Klien transpor adb murni-Java untuk dipakai DARI dalam perangkat
 * (jembatan adb ala Shizuku, ditulis dari protokol AOSP protocol.txt
 * dan perilaku Shizuku AdbClient — bukan salinan kodenya).
 *
 * Protokol: header 24 bita little-endian (perintah, arg0, arg1,
 * panjang data, checksum, magic = perintah ^ 0xFFFFFFFF). Checksum
 * adalah JUMLAH bita data (bukan CRC32 — nama ruasnya menyesatkan;
 * implementasi Shizuku yang berjalan memakai penjumlahan bita).
 * Perintah: A_CNXN 0x4e584e43, A_AUTH 0x48545541, A_OPEN 0x4e45504f,
 * A_OKAY 0x59414b4f, A_CLSE 0x45534c43, A_WRTE 0x45545257,
 * A_STLS 0x534C5453. A_VERSION 0x01000000, A_MAXDATA 4096.
 *
 * Jabat tangan: kirim CNXN(A_VERSION, A_MAXDATA, "host::\0"); adbd
 * menjawab CNXN (langsung tersambung), A_AUTH TOKEN (tanda tangani
 * token 20 bita lalu kirim AUTH SIGNATURE; bila ditolak kirim AUTH
 * RSAPUBLICKEY), atau A_STLS (naikkan ke TLS 1.3 di dalam aliran lalu
 * kirim CNXN lagi).
 *
 * Dua mode sambung:
 * - TCP polos (adbd mode TCP port 5555, mis. setelah "adb tcpip"):
 *   sambungTcp() — jabat tangan di atas, dengan naik TLS bila diminta.
 * - TLS langsung (debugging nirkabel, layanan _adb-tls-connect._tcp):
 *   sambungTls() — TLS 1.3 dulu dari bita pertama, lalu jabat tangan.
 *
 * Layanan yang dipakai: "track-jdwp" (daftar PID yang bisa di-debug),
 * "jdwp:<pid>" (aliran JDWP proses itu — diserahkan ke
 * DebuggerLangkah.JdwpKlien.sambungAliran), "shell:<perintah>".
 *
 * Kontrol aliran adb: satu WRTE per OKAY. Utas pembaca tunggal
 * membagi pesan masuk ke aliran yang tepat.
 *
 * Kelas murni (tanpa API Android) supaya bisa diuji di JVM lewat
 * sambungAliran() melawan adbd palsu di atas pipa. Bagian soket
 * (connect TCP/TLS) tidak bisa diuji di VM ini (proksi sandbox
 * mencegat loopback) — hanya bisa dibuktikan di ponsel.
 */
public class AdbKlien {

    public static final int A_CNXN = 0x4e584e43;
    public static final int A_AUTH = 0x48545541;
    public static final int A_OPEN = 0x4e45504f;
    public static final int A_OKAY = 0x59414b4f;
    public static final int A_CLSE = 0x45534c43;
    public static final int A_WRTE = 0x45545257;
    public static final int A_STLS = 0x534C5453;
    public static final int A_VERSION = 0x01000000;
    public static final int A_MAXDATA = 4096;
    public static final int AUTH_TOKEN = 1;
    public static final int AUTH_SIGNATURE = 2;
    public static final int AUTH_RSAPUBLICKEY = 3;

    /** Satu pesan adb (header 24 bita + data). */
    static class Pesan {
        int perintah;
        int arg0;
        int arg1;
        byte[] data;
    }

    /** Satu aliran adb (satu layanan yang dibuka). */
    public class Aliran {
        final int lokalId;
        int jauhId = 0;
        volatile boolean tertutup = false;
        final BlockingQueue<byte[]> antreMasuk = new LinkedBlockingQueue<byte[]>();
        final Semaphore izinTulis = new Semaphore(0);
        private final ByteArrayKeluar keluarBuf = new ByteArrayKeluar();

        Aliran(int lokalId) { this.lokalId = lokalId; }

        /** InputStream: baca bita yang dikirim adbd lewat WRTE. */
        public InputStream masuk() {
            return new InputStream() {
                private byte[] sisa = null;
                private int pos = 0;

                @Override
                public int read() throws IOException {
                    byte[] satu = new byte[1];
                    int n = read(satu, 0, 1);
                    return n < 0 ? -1 : (satu[0] & 0xFF);
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (len <= 0) return 0;
                    while (true) {
                        if (sisa != null && pos < sisa.length) {
                            int n = Math.min(len, sisa.length - pos);
                            System.arraycopy(sisa, pos, b, off, n);
                            pos += n;
                            if (pos >= sisa.length) sisa = null;
                            return n;
                        }
                        if (tertutup && antreMasuk.isEmpty()) return -1;
                        try {
                            sisa = antreMasuk.poll(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new IOException("Bacaan aliran adb terputus.");
                        }
                        if (sisa == null) {
                            if (tertutup) return -1;
                            throw new IOException("Bacaan aliran adb kehabisan waktu.");
                        }
                        pos = 0;
                    }
                }

                @Override
                public void close() {
                    tutupAliran(Aliran.this);
                }
            };
        }

        /** OutputStream: tulis bita ke adbd lewat WRTE (satu per OKAY). */
        public OutputStream keluar() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    write(new byte[]{(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    int sisa = len;
                    int p = off;
                    while (sisa > 0) {
                        int n = Math.min(sisa, kirimMaks);
                        boolean dapat;
                        try {
                            dapat = izinTulis.tryAcquire(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new IOException("Tulisan aliran adb terputus.");
                        }
                        if (!dapat) {
                            throw new IOException("adbd tidak membalas OKAY.");
                        }
                        try {
                            byte[] potong = new byte[n];
                            System.arraycopy(b, p, potong, 0, n);
                            kirimPesan(A_WRTE, lokalId, jauhId, potong);
                        } catch (IOException e) {
                            izinTulis.release();
                            throw e;
                        }
                        p += n;
                        sisa -= n;
                    }
                }

                @Override
                public void close() {
                    tutupAliran(Aliran.this);
                }
            };
        }

        /** Baca seluruh isi aliran sampai tutup (untuk shell/track-jdwp). */
        public byte[] bacaSemua() throws IOException {
            InputStream in = masuk();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    /** ByteArrayOutputStream tanpa import tambahan di tengah kelas. */
    static class ByteArrayKeluar extends java.io.ByteArrayOutputStream {
    }

    private Socket soket;
    private InputStream masuk;
    private OutputStream keluar;
    private AdbKunci kunci;
    private String host;
    private int kirimMaks = A_MAXDATA;
    private volatile boolean tersambung = false;
    private Thread utasBaca;
    private int idBerikut = 1;
    private final Map<Integer, Aliran> aliran = new HashMap<Integer, Aliran>();
    private final Object kunciTulis = new Object();

    public boolean tersambung() { return tersambung; }

    // ==================== Penyambungan ====================

    /**
     * Sambung ke adbd TCP polos (mis. 127.0.0.1:5555 setelah adb tcpip).
     * Bila adbd meminta STLS, aliran dinaikkan ke TLS 1.3.
     */
    public void sambungTcp(String host, int port, AdbKunci kunci, int timeoutMs)
            throws IOException {
        if (host == null || host.length() == 0) host = "127.0.0.1";
        this.host = host;
        this.kunci = kunci;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), timeoutMs > 0 ? timeoutMs : 5000);
            s.setSoTimeout(timeoutMs > 0 ? timeoutMs : 5000);
        } catch (IOException e) {
            try { s.close(); } catch (Throwable ignored) {
            }
            throw new IOException("Tidak bisa tersambung ke adbd " + host + ":" + port
                    + " (" + e.getMessage() + "). Pastikan debugging USB/nirkabel aktif.", e);
        }
        soket = s;
        masuk = s.getInputStream();
        keluar = s.getOutputStream();
        jabatTangan(false);
        s.setSoTimeout(0);
        mulaiBacaAliran();
        tersambung = true;
    }

    /**
     * Sambung ke adbd debugging nirkabel (port layanan
     * _adb-tls-connect._tcp): TLS 1.3 dari bita pertama, lalu jabat tangan.
     */
    public void sambungTls(String host, int port, AdbKunci kunci, int timeoutMs)
            throws IOException {
        if (host == null || host.length() == 0) host = "127.0.0.1";
        this.host = host;
        this.kunci = kunci;
        try {
            SSLSocketFactory pabrik = kunci.konteksSsl().getSocketFactory();
            Socket polos = new Socket();
            polos.connect(new InetSocketAddress(host, port), timeoutMs > 0 ? timeoutMs : 5000);
            polos.setSoTimeout(timeoutMs > 0 ? timeoutMs : 5000);
            SSLSocket ssl = (SSLSocket) pabrik.createSocket(polos, host, port, true);
            ssl.setEnabledProtocols(new String[]{"TLSv1.3"});
            ssl.startHandshake();
            soket = ssl;
            masuk = ssl.getInputStream();
            keluar = ssl.getOutputStream();
        } catch (IOException e) {
            throw new IOException("Tidak bisa tersambung TLS ke adbd " + host + ":" + port
                    + " (" + e.getMessage() + ").", e);
        } catch (Exception e) {
            throw new IOException("Gagal menyiapkan TLS adb (" + e.getMessage() + ").", e);
        }
        jabatTangan(true);
        try { soket.setSoTimeout(0); } catch (Throwable ignored) {
        }
        mulaiBacaAliran();
        tersambung = true;
    }

    /**
     * Sambung lewat aliran yang sudah dibuka (dipakai uji di JVM:
     * adbd palsu di atas pipa; tanpa soket, tanpa TLS).
     */
    public void sambungAliran(InputStream in, OutputStream out, AdbKunci kunci)
            throws IOException {
        this.masuk = in;
        this.keluar = out;
        this.kunci = kunci;
        jabatTangan(false);
        mulaiBacaAliran();
        tersambung = true;
    }

    /** Tutup sambungan adb (semua aliran ikut tertutup). */
    public void tutup() {
        tersambung = false;
        try {
            if (soket != null) soket.close();
        } catch (Throwable ignored) {
        }
        try {
            if (masuk != null) masuk.close();
        } catch (Throwable ignored) {
        }
        try {
            if (keluar != null) keluar.close();
        } catch (Throwable ignored) {
        }
        synchronized (aliran) {
            for (Aliran a : aliran.values()) {
                a.tertutup = true;
                a.antreMasuk.offer(new byte[0]);
            }
            aliran.clear();
        }
    }

    // ==================== Jabat tangan ====================
    //
    // Jabat tangan dibaca SINKRON di utas pemanggil (bukan utas baca
    // terpisah): bila utas baca jabat tangan dan utas baca aliran sama
    // sama membaca aliran yang sama, pesan pertama sesudah jabat tangan
    // (mis. OKAY untuk OPEN pertama) bisa tertelan antrean jabat tangan
    // dan buka() kehabisan waktu 15 detik. Utas baca aliran
    // (mulaiBacaAliran) baru dinyalakan setelah jabat tangan selesai.
    // Ini juga membuat naik-TLS (STLS) aman: tidak ada utas lain yang
    // membaca aliran polos saat aliran diganti TLS.

    private Pesan tungguJabat() throws IOException {
        try {
            Pesan p = bacaPesan();
            if (p == null) throw new IOException("adbd menutup sambungan saat jabat tangan.");
            return p;
        } catch (java.net.SocketTimeoutException e) {
            throw new IOException("adbd tidak menjawab jabat tangan.");
        }
    }

    /**
     * Putaran jabat tangan. sudahTls=true bila aliran sudah TLS dari awal
     * (mode nirkabel) sehingga STLS tidak perlu ditangani.
     */
    private void jabatTangan(boolean sudahTls) throws IOException {
        kirimPesan(A_CNXN, A_VERSION, A_MAXDATA, "host::\0".getBytes("ISO-8859-1"));
        boolean sudahTanda = false;
        for (int putaran = 0; putaran < 6; putaran++) {
            Pesan p = tungguJabat();
            if (p.perintah == A_CNXN) {
                kirimMaks = Math.min(A_MAXDATA, p.arg1 > 0 ? p.arg1 : A_MAXDATA);
                return;
            }
            if (p.perintah == A_STLS && !sudahTls) {
                naikTls();
                kirimPesan(A_CNXN, A_VERSION, A_MAXDATA, "host::\0".getBytes("ISO-8859-1"));
                continue;
            }
            if (p.perintah == A_AUTH) {
                if (p.arg0 == AUTH_TOKEN) {
                    if (sudahTanda) {
                        // Token lagi setelah SIGNATURE: adbd belum mengenal
                        // kunci ini — kirim kunci publik supaya di HP muncul
                        // dialog otorisasi ("Izinkan debugging USB?").
                        kirimPesan(A_AUTH, AUTH_RSAPUBLICKEY, 0,
                                (kunci.kunciPublikAdb() + "\0").getBytes("ISO-8859-1"));
                        continue;
                    }
                    byte[] tanda;
                    try {
                        tanda = kunci.tanda(p.data);
                    } catch (Exception e) {
                        throw new IOException("Gagal menandatangani token adb.", e);
                    }
                    kirimPesan(A_AUTH, AUTH_SIGNATURE, 0, tanda);
                    sudahTanda = true;
                    continue;
                }
                if (p.arg0 == AUTH_RSAPUBLICKEY) {
                    throw new IOException("adbd menolak kunci ini. Pasangkan dulu lewat "
                            + "debugging nirkabel (kode pairing), lalu coba lagi.");
                }
                throw new IOException("Jawaban AUTH adbd tidak dikenal: " + p.arg0 + ".");
            }
            if (p.perintah == A_CLSE) {
                throw new IOException("adbd menutup sambungan saat jabat tangan.");
            }
            throw new IOException("Jawaban adbd tidak dikenal saat jabat tangan: 0x"
                    + Integer.toHexString(p.perintah) + ".");
        }
        throw new IOException("Jabat tangan adb gagal setelah beberapa putaran.");
    }

    /** Naikkan aliran polos ke TLS 1.3 setelah A_STLS. */
    private void naikTls() throws IOException {
        if (soket == null) {
            throw new IOException("STLS diminta tetapi tidak ada soket untuk dinaikkan.");
        }
        try {
            SSLSocketFactory pabrik = kunci.konteksSsl().getSocketFactory();
            SSLSocket ssl = (SSLSocket) pabrik.createSocket(soket, host, soket.getPort(), true);
            ssl.setEnabledProtocols(new String[]{"TLSv1.3"});
            ssl.startHandshake();
            soket = ssl;
            masuk = ssl.getInputStream();
            keluar = ssl.getOutputStream();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Gagal menaikkan adb ke TLS (" + e.getMessage() + ").", e);
        }
    }

    // ==================== Pembaca aliran ====================

    private void mulaiBacaAliran() {
        utasBaca = new Thread(new Runnable() {
            public void run() {
                try {
                    while (tersambung) {
                        Pesan p = bacaPesan();
                        if (p == null) break;
                        if (p.perintah == A_OKAY || p.perintah == A_WRTE || p.perintah == A_CLSE) {
                            Aliran a;
                            synchronized (aliran) {
                                a = aliran.get(Integer.valueOf(p.arg1));
                            }
                            if (a == null) continue;
                            if (p.perintah == A_OKAY) {
                                if (a.jauhId == 0) {
                                    a.jauhId = p.arg0;
                                    a.izinTulis.release();
                                } else {
                                    a.izinTulis.release();
                                }
                            } else if (p.perintah == A_WRTE) {
                                a.antreMasuk.offer(p.data);
                                kirimPesan(A_OKAY, a.lokalId, a.jauhId, new byte[0]);
                            } else {
                                a.tertutup = true;
                                a.antreMasuk.offer(new byte[0]);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    tersambung = false;
                    synchronized (aliran) {
                        for (Aliran a : aliran.values()) {
                            a.tertutup = true;
                            a.antreMasuk.offer(new byte[0]);
                        }
                    }
                }
            }
        }, "AdbBaca");
        utasBaca.setDaemon(true);
        utasBaca.start();
    }

    // ==================== Layanan ====================

    /** Buka satu layanan adb ("track-jdwp", "jdwp:<pid>", "shell:..."). */
    public Aliran buka(String layanan) throws IOException {
        if (!tersambung) throw new IOException("Belum tersambung ke adbd.");
        int lokal;
        Aliran a;
        synchronized (aliran) {
            lokal = idBerikut++;
            a = new Aliran(lokal);
            aliran.put(Integer.valueOf(lokal), a);
        }
        kirimPesan(A_OPEN, lokal, 0, (layanan + "\0").getBytes("ISO-8859-1"));
        try {
            if (!a.izinTulis.tryAcquire(15, TimeUnit.SECONDS)) {
                throw new IOException("adbd tidak membuka layanan " + layanan + ".");
            }
        } catch (InterruptedException e) {
            throw new IOException("Membuka layanan adb terputus.");
        }
        // OKAY pembuka layanan ini JUGA memberi satu kredit tulis (satu
        // WRTE per OKAY). buka() memakainya sebagai sinyal "terbuka", jadi
        // kembalikan kreditnya supaya tulis pertama bisa jalan.
        a.izinTulis.release();
        // JANGAN periksa a.tertutup di sini: perintah shell yang cepat
        // bisa selesai (CLSE) sebelum buka() kembali — layanannya sudah
        // diterima (OKAY sudah melepas izin tulis) dan keluarannya tetap
        // bisa dibaca dari antrean. Penolakan dikenali dari tidak
        // adanya OKAY (kehabisan waktu di atas), bukan dari CLSE.
        return a;
    }

    private void tutupAliran(Aliran a) {
        if (a == null || a.tertutup) return;
        a.tertutup = true;
        a.antreMasuk.offer(new byte[0]);
        synchronized (aliran) {
            aliran.remove(Integer.valueOf(a.lokalId));
        }
        try {
            kirimPesan(A_CLSE, a.lokalId, a.jauhId, new byte[0]);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Daftar PID proses yang bisa di-debug (layanan track-jdwp: bingkai
     * pertama "hex4 + baris <pid>\n"). Mengembalikan daftar PID.
     */
    public List<Integer> daftarJdwp() throws IOException {
        Aliran a = buka("track-jdwp");
        List<Integer> hasil = new ArrayList<Integer>();
        try {
            InputStream in = a.masuk();
            byte[] kepala = new byte[4];
            bacaTepat(in, kepala);
            int panjang;
            try {
                panjang = Integer.parseInt(new String(kepala, "ISO-8859-1"), 16);
            } catch (Exception e) {
                throw new IOException("Jawaban track-jdwp rusak.");
            }
            if (panjang <= 0 || panjang > 65536) {
                throw new IOException("Jawaban track-jdwp rusak.");
            }
            byte[] isi = new byte[panjang];
            bacaTepat(in, isi);
            String teks = new String(isi, "ISO-8859-1");
            for (String baris : teks.split("\n")) {
                baris = baris.trim();
                if (baris.length() == 0) continue;
                try {
                    hasil.add(Integer.valueOf(Integer.parseInt(baris)));
                } catch (Throwable ignored) {
                }
            }
        } finally {
            tutupAliran(a);
        }
        return hasil;
    }

    /** Buka aliran JDWP sebuah proses (untuk JdwpKlien.sambungAliran). */
    public Aliran bukaJdwp(int pid) throws IOException {
        return buka("jdwp:" + pid);
    }

    /** Jalankan perintah shell lewat adbd; kembalikan keluarannya. */
    public String jalankanShell(String perintah) throws IOException {
        Aliran a = buka("shell:" + perintah);
        try {
            return new String(a.bacaSemua(), "UTF-8");
        } finally {
            tutupAliran(a);
        }
    }

    // ==================== Framing pesan ====================

    /** Checksum adb: jumlah semua bita data (unsigned) mod 2^32. */
    static int checksum(byte[] data) {
        long s = 0;
        for (int i = 0; i < data.length; i++) s += (data[i] & 0xFF);
        return (int) (s & 0xFFFFFFFFL);
    }

    /** Bangun satu pesan adb utuh (header 24 bita LE + data). */
    static byte[] bangunPesan(int perintah, int arg0, int arg1, byte[] data) {
        byte[] keluar = new byte[24 + data.length];
        tulisLE32(keluar, 0, perintah);
        tulisLE32(keluar, 4, arg0);
        tulisLE32(keluar, 8, arg1);
        tulisLE32(keluar, 12, data.length);
        tulisLE32(keluar, 16, checksum(data));
        tulisLE32(keluar, 20, perintah ^ 0xFFFFFFFF);
        System.arraycopy(data, 0, keluar, 24, data.length);
        return keluar;
    }

    private void kirimPesan(int perintah, int arg0, int arg1, byte[] data) throws IOException {
        if (data == null) data = new byte[0];
        byte[] pesan = bangunPesan(perintah, arg0, arg1, data);
        synchronized (kunciTulis) {
            keluar.write(pesan);
            keluar.flush();
        }
    }

    /** Baca satu pesan adb utuh; null bila aliran tutup. */
    private Pesan bacaPesan() throws IOException {
        byte[] kepala = new byte[24];
        try {
            bacaTepat(masuk, kepala);
        } catch (EOFException e) {
            return null;
        }
        Pesan p = new Pesan();
        p.perintah = bacaLE32(kepala, 0);
        p.arg0 = bacaLE32(kepala, 4);
        p.arg1 = bacaLE32(kepala, 8);
        int panjang = bacaLE32(kepala, 12);
        int cek = bacaLE32(kepala, 16);
        int magic = bacaLE32(kepala, 20);
        if (magic != (p.perintah ^ 0xFFFFFFFF)) {
            throw new IOException("Magic pesan adb tidak cocok.");
        }
        if (panjang < 0 || panjang > 16 * 1024 * 1024) {
            throw new IOException("Panjang pesan adb tidak sah: " + panjang + ".");
        }
        p.data = new byte[panjang];
        if (panjang > 0) bacaTepat(masuk, p.data);
        if (checksum(p.data) != cek) {
            throw new IOException("Checksum pesan adb tidak cocok.");
        }
        return p;
    }

    private static void bacaTepat(InputStream in, byte[] buf) throws IOException {
        int baca = 0;
        while (baca < buf.length) {
            int n = in.read(buf, baca, buf.length - baca);
            if (n < 0) throw new EOFException("Aliran adb tertutup.");
            baca += n;
        }
    }

    static void tulisLE32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    static int bacaLE32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
