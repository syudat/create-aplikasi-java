package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulator Kondisi Perangkat Ekstrem dan Auditor Kebocoran Memori
 * (Fitur 10, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer.
 * Yang sudah NYATA di versi ini (bukan model contoh tetap):
 *  - Keadaan simpan-pulihkan DIBACA DARI SUMBER PROYEK PENGGUNA
 *    (analisisKeadaanDariSumber): kunci yang disimpan lewat
 *    outState.putX, kunci yang dipulihkan lewat getX, dan kolom
 *    (field) yang tidak pernah disimpan — jadi simulasi mati proses
 *    memakai data proyek sungguhan, bukan "Budi/Makassar".
 *  - Jaringan putus SUNGGUHAN di tingkat soket (ServerUji): server
 *    loopback lokal benar-benar dimatikan di tengah uji, lalu
 *    diukur apakah permintaan gagal, apakah antrean luring menahan
 *    permintaan itu, dan apakah permintaan terkirim lagi setelah
 *    server hidup kembali.
 *  - Heap dump NYATA (PenguraiHprof): mengurai berkas .hprof hasil
 *    Debug.dumpHprofData (dipanggil Activity), menghitung jumlah
 *    objek per kelas, membandingkan dua dump (tumbuh = curiga bocor),
 *    dan melacak rantai penahan dari akar GC ke satu objek.
 *  - Mati proses SUNGGUHAN: Activity menyimpan keadaan ke berkas lalu
 *    memanggil Process.killProcess atas proses sendiri; saat aplikasi
 *    dibuka lagi, Activity memeriksa berkas itu dan melaporkan data
 *    mana yang pulih (lihat SimulatorEkstremActivity).
 *
 * BATAS JUJUR:
 *  - Mematikan proses aplikasi LAIN (force-stop) butuh izin sistem /
 *    privileged yang tidak dimiliki aplikasi biasa. Yang bisa dilakukan
 *    aplikasi biasa: killBackgroundProcesses atas aplikasi proyek yang
 *    sedang di latar (butuh KILL_BACKGROUND_PROCESSES — sudah ada di
 *    manifest) dan mematikan proses SENDIRI (nyata, lihat di atas).
 *    Sistem dapat menghidupkan lagi aplikasi yang dimatikan latarnya
 *    bila aplikasi itu punya service.
 *  - Mematikan radio/Wi-Fi ponsel dari aplikasi biasa TIDAK MUNGKIN
 *    (tidak ada izin untuk itu). Uji jaringan di sini mematikan
 *    konektivitas PROSES UJI (server loopback ditutup), bukan radio
 *    perangkat; status konektivitas perangkat yang sebenarnya dibaca
 *    dari ConnectivityManager dan dilaporkan apa adanya.
 *  - Heap dump hanya bisa diambil atas proses SENDIRI
 *    (Debug.dumpHprofData); heap aplikasi proyek yang sedang berjalan
 *    tidak bisa dibaca aplikasi lain.
 *  - Analisis simpan-pulihkan memakai pola teks atas sumber; kunci
 *    yang disusun dinamis (mis. "kunci_" + i) tidak tertangkap.
 */
public class SimulatorEkstrem {

    /** Keadaan layar yang disimpan (bundle) vs yang hanya di memori. */
    public static class KeadaanLayar {
        public Map<String, String> tersimpan = new LinkedHashMap<String, String>();
        public Map<String, String> hanyaMemori = new LinkedHashMap<String, String>();
    }

    /** Satu skenario tekanan + hasilnya. */
    public static class HasilSkenario {
        public String nama = "";
        public boolean lulus = true;
        public String penjelasan = "";
        public String toString() {
            return (lulus ? "LULUS " : "GAGAL ") + nama + ": " + penjelasan;
        }
    }

    /** Satu kebocoran: objek + rantai pemilik yang menahannya. */
    public static class Kebocoran {
        public String objek = "";
        public String pemilik = "";
        public String toString() {
            return objek + " ditahan oleh " + pemilik;
        }
    }

    /** Simulasikan mati proses: kembalikan daftar data yang HILANG. */
    public static List<String> simulasiMatiProses(KeadaanLayar keadaan) {
        List<String> hilang = new ArrayList<String>();
        if (keadaan == null) return hilang;
        for (Map.Entry<String, String> e : keadaan.hanyaMemori.entrySet()) {
            hilang.add(e.getKey());
        }
        return hilang;
    }

    /** Simulasikan putar layar: data tersimpan pulih, memori hilang. */
    public static List<String> simulasiPutarLayar(KeadaanLayar keadaan) {
        return simulasiMatiProses(keadaan);
    }

    /**
     * Simulasikan jaringan putus: pemeriksaan aturan — permintaan yang
     * tidak punya antrean luring gagal; yang punya antrean ditunda.
     * Mengembalikan daftar permintaan yang GAGAL (tanpa antrean).
     */
    public static List<String> simulasiJaringanPutus(List<String> permintaan,
                                                     List<String> antreanLuring) {
        List<String> gagal = new ArrayList<String>();
        if (permintaan == null) return gagal;
        for (int i = 0; i < permintaan.size(); i++) {
            String p = permintaan.get(i);
            if (antreanLuring == null || !antreanLuring.contains(p)) gagal.add(p);
        }
        return gagal;
    }

    /**
     * Simulasikan hemat baterai ekstrem: thread latar yang tidak
     * didaftarkan sebagai pekerjaan penting harus berhenti.
     * Mengembalikan thread yang TETAP berjalan (pelanggaran).
     */
    public static List<String> simulasiHematBaterai(List<String> threadBerjalan,
                                                    List<String> pekerjaanPenting) {
        List<String> pelanggaran = new ArrayList<String>();
        if (threadBerjalan == null) return pelanggaran;
        for (int i = 0; i < threadBerjalan.size(); i++) {
            String t = threadBerjalan.get(i);
            if (pekerjaanPenting == null || !pekerjaanPenting.contains(t)) {
                pelanggaran.add(t);
            }
        }
        return pelanggaran;
    }

    /** Pelacak referensi: objek -> pemilik yang menahannya. */
    public static class Pelacak {
        public Map<String, String> tertahan = new LinkedHashMap<String, String>();

        public void daftarkan(String objek, String pemilik) {
            if (objek == null || pemilik == null) return;
            tertahan.put(objek, pemilik);
        }

        public void lepaskan(String objek) {
            if (objek == null) return;
            tertahan.remove(objek);
        }

        /** Objek yang masih tertahan setelah pemiliknya ditutup. */
        public List<Kebocoran> periksa(String pemilikDitutup) {
            List<Kebocoran> hasil = new ArrayList<Kebocoran>();
            if (pemilikDitutup == null) return hasil;
            for (Map.Entry<String, String> e : tertahan.entrySet()) {
                if (pemilikDitutup.equals(e.getValue())) {
                    Kebocoran k = new Kebocoran();
                    k.objek = e.getKey();
                    k.pemilik = e.getValue();
                    hasil.add(k);
                }
            }
            return hasil;
        }
    }

    /** Jalankan semua skenario atas satu model; kembalikan hasilnya. */
    public static List<HasilSkenario> jalankanSemua(KeadaanLayar keadaan,
                                                   List<String> permintaan,
                                                   List<String> antreanLuring,
                                                   List<String> threadBerjalan,
                                                   List<String> pekerjaanPenting) {
        List<HasilSkenario> hasil = new ArrayList<HasilSkenario>();
        HasilSkenario mati = new HasilSkenario();
        mati.nama = "Mati proses";
        List<String> hilang = simulasiMatiProses(keadaan);
        mati.lulus = hilang.size() == 0;
        mati.penjelasan = hilang.size() == 0 ? "semua data tersimpan pulih"
                : hilang.size() + " kolom hilang: " + hilang;
        hasil.add(mati);
        HasilSkenario jaring = new HasilSkenario();
        jaring.nama = "Jaringan putus";
        List<String> gagal = simulasiJaringanPutus(permintaan, antreanLuring);
        jaring.lulus = gagal.size() == 0;
        jaring.penjelasan = gagal.size() == 0 ? "semua permintaan antre luring"
                : gagal.size() + " permintaan gagal: " + gagal;
        hasil.add(jaring);
        HasilSkenario baterai = new HasilSkenario();
        baterai.nama = "Hemat baterai";
        List<String> langgar = simulasiHematBaterai(threadBerjalan, pekerjaanPenting);
        baterai.lulus = langgar.size() == 0;
        baterai.penjelasan = langgar.size() == 0 ? "thread latar berhenti semua"
                : langgar.size() + " thread tetap jalan: " + langgar;
        hasil.add(baterai);
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(List<HasilSkenario> skenario, List<Kebocoran> bocor) {
        int lulus = 0;
        if (skenario != null) {
            for (int i = 0; i < skenario.size(); i++) if (skenario.get(i).lulus) lulus++;
        }
        int total = skenario == null ? 0 : skenario.size();
        int b = bocor == null ? 0 : bocor.size();
        return "Skenario lulus: " + lulus + " dari " + total
                + ". Kebocoran ditemukan: " + b + ".";
    }

    // ------------------------------------------------------------------
    // Keadaan simpan-pulihkan dari sumber proyek pengguna.
    // ------------------------------------------------------------------

    /** Hasil analisis simpan-pulihkan atas satu berkas sumber. */
    public static class HasilAnalisis {
        public List<String> kunciDisimpan = new ArrayList<String>();
        public List<String> kunciDipulihkan = new ArrayList<String>();
        public List<String> disimpanTakDipulihkan = new ArrayList<String>();
        public List<String> dipulihkanTakDisimpan = new ArrayList<String>();
        public List<String> kolomTakDisimpan = new ArrayList<String>();
        public boolean adaOnSave = false;
        public boolean adaOnRestore = false;
    }

    static void tambahUnik(List<String> daftar, String nilai) {
        if (nilai == null || nilai.length() == 0) return;
        if (!daftar.contains(nilai)) daftar.add(nilai);
    }

    static boolean mengandungKata(String teks, String kata) {
        if (teks == null || kata == null) return false;
        Matcher m = Pattern.compile("\\b" + Pattern.quote(kata) + "\\b").matcher(teks);
        return m.find();
    }

    /** Kata pertama sebuah baris (untuk menyaring baris bukan field). */
    static String kataPertama(String s) {
        if (s == null) return "";
        String t = s.trim();
        int i = 0;
        while (i < t.length() && (Character.isLetterOrDigit(t.charAt(i))
                || t.charAt(i) == '_')) i++;
        return t.substring(0, i);
    }

    /**
     * Baca keadaan simpan-pulihkan dari teks sumber (Java/Kotlin):
     * kunci putX("kunci", ...) = disimpan, getX("kunci") = dipulihkan,
     * field kelas yang tidak pernah disimpan = kolom tak disimpan.
     */
    public static HasilAnalisis analisisKeadaanDariSumber(String kode) {
        HasilAnalisis h = new HasilAnalisis();
        if (kode == null) return h;
        h.adaOnSave = kode.contains("onSaveInstanceState");
        h.adaOnRestore = kode.contains("onRestoreInstanceState");
        String jenisPut = "String|Int|Long|Float|Double|Boolean|StringArray|IntArray"
                + "|LongArray|BooleanArray|FloatArray|Serializable|Parcelable"
                + "|CharSequence|Bundle";
        Matcher mp = Pattern.compile("\\.put(" + jenisPut + ")\\s*\\(\\s*\"([^\"]+)\"")
                .matcher(kode);
        List<String> nilaiPut = new ArrayList<String>();
        Matcher mpNilai = Pattern.compile("\\.put\\w+\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*([^,;\\)]+)")
                .matcher(kode);
        while (mpNilai.find()) nilaiPut.add(mpNilai.group(1));
        while (mp.find()) tambahUnik(h.kunciDisimpan, mp.group(2));
        Matcher mg = Pattern.compile("\\.get(" + jenisPut + ")\\s*\\(\\s*\"([^\"]+)\"")
                .matcher(kode);
        while (mg.find()) tambahUnik(h.kunciDipulihkan, mg.group(2));
        for (int i = 0; i < h.kunciDisimpan.size(); i++) {
            if (!h.kunciDipulihkan.contains(h.kunciDisimpan.get(i))) {
                h.disimpanTakDipulihkan.add(h.kunciDisimpan.get(i));
            }
        }
        for (int i = 0; i < h.kunciDipulihkan.size(); i++) {
            if (!h.kunciDisimpan.contains(h.kunciDipulihkan.get(i))) {
                h.dipulihkanTakDisimpan.add(h.kunciDipulihkan.get(i));
            }
        }
        // Field kelas: baris "pengubah Tipe nama;" atau "pengubah Tipe nama = ...;"
        // tanpa '(' (bukan metode), bukan kata kunci alur.
        String[] baris = kode.split("\n");
        Set<String> blokir = new HashSet<String>(Arrays.asList(
                "return", "throw", "if", "else", "for", "while", "do", "switch",
                "case", "new", "class", "interface", "enum", "package", "import",
                "try", "catch", "finally", "synchronized", "break", "continue"));
        Pattern pField = Pattern.compile(
                "^\\s*(?:private|protected|public)?\\s*(?:static\\s+)?(?!final\\b)"
                + "[A-Za-z_][\\w<>,\\.\\[\\] \\?]*?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|;)");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            if (b.indexOf('(') >= 0) continue;
            if (blokir.contains(kataPertama(b))) continue;
            Matcher fm = pField.matcher(b);
            if (!fm.find()) continue;
            String nama = fm.group(1);
            if (nama == null || nama.length() == 0) continue;
            if (nama.equals(nama.toUpperCase())) continue; // konstanta
            boolean disimpan = h.kunciDisimpan.contains(nama);
            if (!disimpan) {
                for (int j = 0; j < nilaiPut.size(); j++) {
                    if (mengandungKata(nilaiPut.get(j), nama)) {
                        disimpan = true;
                        break;
                    }
                }
            }
            if (!disimpan) tambahUnik(h.kolomTakDisimpan, nama);
        }
        return h;
    }

    /** Gabungkan dua hasil analisis (beberapa berkas sumber satu proyek). */
    public static HasilAnalisis gabungAnalisis(HasilAnalisis a, HasilAnalisis b) {
        HasilAnalisis h = new HasilAnalisis();
        if (a == null) return b == null ? h : b;
        if (b == null) return a;
        for (int i = 0; i < a.kunciDisimpan.size(); i++) {
            tambahUnik(h.kunciDisimpan, a.kunciDisimpan.get(i));
        }
        for (int i = 0; i < b.kunciDisimpan.size(); i++) {
            tambahUnik(h.kunciDisimpan, b.kunciDisimpan.get(i));
        }
        for (int i = 0; i < a.kunciDipulihkan.size(); i++) {
            tambahUnik(h.kunciDipulihkan, a.kunciDipulihkan.get(i));
        }
        for (int i = 0; i < b.kunciDipulihkan.size(); i++) {
            tambahUnik(h.kunciDipulihkan, b.kunciDipulihkan.get(i));
        }
        for (int i = 0; i < a.kolomTakDisimpan.size(); i++) {
            tambahUnik(h.kolomTakDisimpan, a.kolomTakDisimpan.get(i));
        }
        for (int i = 0; i < b.kolomTakDisimpan.size(); i++) {
            tambahUnik(h.kolomTakDisimpan, b.kolomTakDisimpan.get(i));
        }
        h.adaOnSave = a.adaOnSave || b.adaOnSave;
        h.adaOnRestore = a.adaOnRestore || b.adaOnRestore;
        for (int i = 0; i < h.kunciDisimpan.size(); i++) {
            if (!h.kunciDipulihkan.contains(h.kunciDisimpan.get(i))) {
                h.disimpanTakDipulihkan.add(h.kunciDisimpan.get(i));
            }
        }
        for (int i = 0; i < h.kunciDipulihkan.size(); i++) {
            if (!h.kunciDisimpan.contains(h.kunciDipulihkan.get(i))) {
                h.dipulihkanTakDisimpan.add(h.kunciDipulihkan.get(i));
            }
        }
        return h;
    }

    /** Ubah hasil analisis menjadi KeadaanLayar untuk simulasi. */
    public static KeadaanLayar keadaanDariAnalisis(HasilAnalisis h) {
        KeadaanLayar k = new KeadaanLayar();
        if (h == null) return k;
        for (int i = 0; i < h.kunciDisimpan.size(); i++) {
            k.tersimpan.put(h.kunciDisimpan.get(i), "tersimpan");
        }
        for (int i = 0; i < h.kolomTakDisimpan.size(); i++) {
            k.hanyaMemori.put(h.kolomTakDisimpan.get(i), "tidak disimpan");
        }
        for (int i = 0; i < h.dipulihkanTakDisimpan.size(); i++) {
            k.hanyaMemori.put(h.dipulihkanTakDisimpan.get(i), "dipulihkan tanpa disimpan");
        }
        return k;
    }

    /** Pindai sumber proyek: nama mekanisme latar yang dipakai (info hemat baterai). */
    public static List<String> pindaiPekerjaanLatar(String kode) {
        List<String> hasil = new ArrayList<String>();
        if (kode == null) return hasil;
        if (kode.contains("new Thread") || kode.contains("Thread(")) {
            tambahUnik(hasil, "Thread");
        }
        if (kode.contains("AsyncTask")) tambahUnik(hasil, "AsyncTask");
        if (kode.contains("Executor") || kode.contains("Executors")) {
            tambahUnik(hasil, "Executor");
        }
        if (kode.contains("startService") || kode.contains("Service")) {
            tambahUnik(hasil, "Service");
        }
        if (kode.contains("WorkManager") || kode.contains("WorkRequest")) {
            tambahUnik(hasil, "WorkManager");
        }
        if (kode.contains("AlarmManager")) tambahUnik(hasil, "AlarmManager");
        if (kode.contains("Handler") && kode.contains("postDelayed")) {
            tambahUnik(hasil, "Handler.postDelayed");
        }
        if (kode.contains("Timer") || kode.contains("TimerTask")) {
            tambahUnik(hasil, "Timer");
        }
        return hasil;
    }

    // ------------------------------------------------------------------
    // Jaringan putus SUNGGUHAN di tingkat soket (server loopback lokal).
    // ------------------------------------------------------------------

    /** Hasil uji putus-sambung soket nyata. */
    public static class HasilJaring {
        public boolean terhubungAwal = false;
        public boolean gagalSetelahPutus = false;
        public boolean antreanDipulihkan = false;
        public String pesan = "";
        public long msGagal = 0;
    }

    static void tutupDiam(Socket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    static void tutupDiam(ServerSocket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Uji jaringan putus yang sebenarnya: server loopback lokal
     * benar-benar dimatikan di tengah jalan. Mengembalikan apakah
     * sambungan awal berhasil, apakah sambungan sesudah putus gagal
     * (dengan galat soket nyata), dan apakah permintaan yang diantre
     * saat putus terkirim setelah server hidup lagi.
     */
    public static HasilJaring ujiPutusSambung() {
        HasilJaring h = new HasilJaring();
        ServerSocket server = null;
        ServerSocket server2 = null;
        Socket klien = null;
        Thread pelayan = null;
        try {
            server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            final int port = server.getLocalPort();
            final ServerSocket srv = server;
            pelayan = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (!srv.isClosed()) {
                            Socket s = srv.accept();
                            try {
                                InputStream in = s.getInputStream();
                                OutputStream out = s.getOutputStream();
                                int b;
                                StringBuilder sb = new StringBuilder();
                                while ((b = in.read()) != -1) {
                                    if (b == '\n') break;
                                    sb.append((char) b);
                                }
                                if ("PING".equals(sb.toString())) {
                                    out.write("OK\n".getBytes("UTF-8"));
                                    out.flush();
                                }
                            } catch (Throwable ignored) {
                            } finally {
                                try {
                                    s.close();
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, "ServerUji");
            pelayan.setDaemon(true);
            pelayan.start();

            // 1) Sambungan awal harus berhasil.
            klien = new Socket();
            klien.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            klien.setSoTimeout(2000);
            klien.getOutputStream().write("PING\n".getBytes("UTF-8"));
            klien.getOutputStream().flush();
            int b;
            StringBuilder jawab = new StringBuilder();
            while ((b = klien.getInputStream().read()) != -1) {
                if (b == '\n') break;
                jawab.append((char) b);
            }
            h.terhubungAwal = "OK".equals(jawab.toString());
            tutupDiam(klien);
            klien = null;
            if (!h.terhubungAwal) {
                h.pesan = "sambungan awal gagal";
                return h;
            }

            // 2) Matikan server sungguhan, lalu coba sambung lagi.
            tutupDiam(server);
            server = null;
            long mulai = System.currentTimeMillis();
            Socket klien2 = null;
            try {
                klien2 = new Socket();
                klien2.connect(new InetSocketAddress("127.0.0.1", port), 1500);
                h.pesan = "sambungan sesudah putus MASIH berhasil (tak terduga)";
            } catch (IOException e) {
                h.gagalSetelahPutus = true;
                h.msGagal = System.currentTimeMillis() - mulai;
                h.pesan = e.getClass().getSimpleName();
            } finally {
                tutupDiam(klien2);
            }
            if (!h.gagalSetelahPutus) return h;

            // 3) Antrean luring: permintaan ditahan saat putus, lalu
            // dikirim ulang setelah server hidup lagi di port baru.
            List<String> antrean = new ArrayList<String>();
            antrean.add("PING");
            server2 = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            final int port2 = server2.getLocalPort();
            final ServerSocket srv2 = server2;
            Thread pelayan2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (!srv2.isClosed()) {
                            Socket s = srv2.accept();
                            try {
                                InputStream in = s.getInputStream();
                                OutputStream out = s.getOutputStream();
                                int bb;
                                StringBuilder sb = new StringBuilder();
                                while ((bb = in.read()) != -1) {
                                    if (bb == '\n') break;
                                    sb.append((char) bb);
                                }
                                if ("PING".equals(sb.toString())) {
                                    out.write("OK\n".getBytes("UTF-8"));
                                    out.flush();
                                }
                            } catch (Throwable ignored) {
                            } finally {
                                try {
                                    s.close();
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, "ServerUji2");
            pelayan2.setDaemon(true);
            pelayan2.start();
            Socket klien3 = null;
            try {
                klien3 = new Socket();
                klien3.connect(new InetSocketAddress("127.0.0.1", port2), 2000);
                klien3.setSoTimeout(2000);
                klien3.getOutputStream().write((antrean.get(0) + "\n").getBytes("UTF-8"));
                klien3.getOutputStream().flush();
                StringBuilder jb = new StringBuilder();
                int bb;
                while ((bb = klien3.getInputStream().read()) != -1) {
                    if (bb == '\n') break;
                    jb.append((char) bb);
                }
                h.antreanDipulihkan = "OK".equals(jb.toString());
            } catch (Throwable ignored) {
                h.antreanDipulihkan = false;
            } finally {
                tutupDiam(klien3);
            }
            return h;
        } catch (Throwable t) {
            h.pesan = t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
            return h;
        } finally {
            tutupDiam(klien);
            tutupDiam(server);
            tutupDiam(server2);
        }
    }

    // ------------------------------------------------------------------
    // Penahan statis untuk demo kebocoran heap dump (objek yang sengaja
    // ditahan, lalu dilepas — supaya dump sebelum/sesudah bisa dibedakan).
    // ------------------------------------------------------------------

    /** Objek penanda yang dipakai demo kebocoran. */
    public static class ObjekUji {
        public String nama = "";
        public ObjekUji(String n) {
            nama = n;
        }
    }

    /** Daftar statis yang menahan ObjekUji (simulasi kebocoran nyata). */
    public static final List<Object> penahan = new ArrayList<Object>();

    /** Tahan sejumlah ObjekUji di daftar statis; kembalikan jumlah yang ditahan. */
    public static int tahanContoh(int jumlah) {
        for (int i = 0; i < jumlah; i++) {
            penahan.add(new ObjekUji("uji-" + i));
        }
        return penahan.size();
    }

    /** Lepas semua ObjekUji yang ditahan; kembalikan sisa (0). */
    public static int lepasContoh() {
        penahan.clear();
        return penahan.size();
    }

    // ------------------------------------------------------------------
    // Pengurai heap dump (.hprof) — hitung objek per kelas, bandingkan
    // dua dump, lacak rantai penahan dari akar GC.
    // ------------------------------------------------------------------

    /** Satu hasil urai heap dump. */
    public static class HasilHprof {
        /** Nama kelas -> jumlah objek (instance + larik objeknya). */
        public Map<String, Long> jumlahPerKelas = new LinkedHashMap<String, Long>();
        /** Id objek yang menjadi akar GC. */
        public Set<Long> akar = new HashSet<Long>();
        public long totalObjek = 0;
        public long totalBita = 0;
        public String catatan = "";
        /** Internal: id objek kelas -> nama kelas. */
        public Map<Long, String> namaKelasObjek = new HashMap<Long, String>();
        /** Internal: id objek kelas -> tipe medan instance (2 = referensi objek). */
        public Map<Long, List<Integer>> medanInstance = new HashMap<Long, List<Integer>>();
        /** Internal: id objek kelas -> id objek yang ditahan medan statisnya. */
        public Map<Long, List<Long>> medanStatisObjek = new HashMap<Long, List<Long>>();
    }

    /** Satu baris selisih jumlah objek antar dua dump. */
    public static class DeltaKelas {
        public String nama = "";
        public long sebelum = 0;
        public long sesudah = 0;
        public long delta = 0;
    }

    /** Bandingkan dua hasil urai; kembalikan kelas yang berubah, delta terbesar dulu. */
    public static List<DeltaKelas> bandingkan(HasilHprof sebelum, HasilHprof sesudah) {
        List<DeltaKelas> keluar = new ArrayList<DeltaKelas>();
        Set<String> semua = new LinkedHashSet<String>();
        if (sebelum != null) semua.addAll(sebelum.jumlahPerKelas.keySet());
        if (sesudah != null) semua.addAll(sesudah.jumlahPerKelas.keySet());
        for (String nama : semua) {
            long a = 0;
            long b = 0;
            if (sebelum != null && sebelum.jumlahPerKelas.containsKey(nama)) {
                a = sebelum.jumlahPerKelas.get(nama).longValue();
            }
            if (sesudah != null && sesudah.jumlahPerKelas.containsKey(nama)) {
                b = sesudah.jumlahPerKelas.get(nama).longValue();
            }
            if (a != b) {
                DeltaKelas d = new DeltaKelas();
                d.nama = nama;
                d.sebelum = a;
                d.sesudah = b;
                d.delta = b - a;
                keluar.add(d);
            }
        }
        // Urut menurun berdasarkan |delta| (gelembung sederhana, daftar pendek).
        for (int i = 0; i < keluar.size(); i++) {
            for (int j = i + 1; j < keluar.size(); j++) {
                long ai = Math.abs(keluar.get(i).delta);
                long aj = Math.abs(keluar.get(j).delta);
                if (aj > ai) {
                    DeltaKelas t = keluar.get(i);
                    keluar.set(i, keluar.get(j));
                    keluar.set(j, t);
                }
            }
        }
        return keluar;
    }

    /** Ukuran nilai satu tipe hprof (2=ref, 4=boolean, 5=char, 6=float, 7=double,
     *  8=byte, 9=short, 10=int, 11=long). -1 bila tak dikenal. */
    static int ukuranTipeHprof(int tipe, int ukuranId) {
        switch (tipe) {
            case 2: return ukuranId;
            case 4: return 1;
            case 5: return 2;
            case 6: return 4;
            case 7: return 8;
            case 8: return 1;
            case 9: return 2;
            case 10: return 4;
            case 11: return 8;
            default: return -1;
        }
    }

    /** Nama tipe primitif hprof untuk laporan larik. */
    static String namaTipePrimitif(int tipe) {
        switch (tipe) {
            case 4: return "boolean";
            case 5: return "char";
            case 6: return "float";
            case 7: return "double";
            case 8: return "byte";
            case 9: return "short";
            case 10: return "int";
            case 11: return "long";
            default: return "tipe-" + tipe;
        }
    }

    /** Pembaca biner big-endian di atas aliran, dengan lewati() yang tuntas. */
    static class PembacaHprof {
        private final DataInputStream in;
        int ukuranId = 4;

        PembacaHprof(InputStream sumber) {
            in = new DataInputStream(new BufferedInputStream(sumber, 65536));
        }

        int bacaU1() throws IOException {
            return in.readUnsignedByte();
        }

        int bacaU2() throws IOException {
            return in.readUnsignedShort();
        }

        long bacaU4() throws IOException {
            return in.readInt() & 0xFFFFFFFFL;
        }

        long bacaU8() throws IOException {
            return in.readLong();
        }

        long bacaId() throws IOException {
            if (ukuranId == 8) return in.readLong();
            return in.readInt() & 0xFFFFFFFFL;
        }

        byte[] bacaBita(int n) throws IOException {
            byte[] b = new byte[n];
            in.readFully(b);
            return b;
        }

        void lewati(long n) throws IOException {
            long sisa = n;
            while (sisa > 0) {
                long l = in.skip(sisa);
                if (l <= 0) {
                    if (in.read() == -1) throw new EOFException();
                    l = 1;
                }
                sisa -= l;
            }
        }

        void tutup() {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Satu penangan pemindaian hprof: tiap kejadian bisa diabaikan pewarisnya. */
    static abstract class PenanganHprof {
        void onString(long id, byte[] isi) {
        }

        void onLoadClass(long idObjekKelas, long idStringNama) {
        }

        void onClassDump(long idObjekKelas, List<Integer> tipeMedanInstance,
                         List<Long> statisObjek) {
        }

        void onInstanceDump(long id, long idObjekKelas) {
        }

        void onObjectArray(long id, long idObjekKelas, long[] elemen) {
        }

        void onPrimitiveArray(long id, int tipe, long jumlah) {
        }

        void onRoot(long id) {
        }
    }

    /**
     * Pindai satu berkas .hprof dan panggil penangan untuk tiap kejadian
     * yang dikenal. Sub-rekaman heap yang tidak dikenal menghentikan
     * pemindaian segmen itu (dicatat di penangan lewat onCatatan).
     */
    static void pindaiHprof(File berkas, final PenanganHprof penangan) throws IOException {
        PembacaHprof p = new PembacaHprof(new FileInputStream(berkas));
        try {
            // Header: sihir NUL-terminated, u4 ukuran id, u8 cap waktu.
            int b;
            int hitungSihir = 0;
            while (hitungSihir < 64) {
                b = p.bacaU1();
                hitungSihir++;
                if (b == 0) break;
            }
            p.ukuranId = (int) p.bacaU4();
            if (p.ukuranId != 4 && p.ukuranId != 8) p.ukuranId = 4;
            p.bacaU8();
            while (true) {
                int tag;
                try {
                    tag = p.bacaU1();
                } catch (EOFException e) {
                    break;
                }
                p.bacaU4(); // cap waktu rekaman
                long panjang = p.bacaU4();
                switch (tag) {
                    case 0x01: { // STRING
                        long id = p.bacaId();
                        long isi = panjang - p.ukuranId;
                        if (isi < 0) throw new IOException("rekaman STRING hprof rusak");
                        byte[] data = p.bacaBita((int) isi);
                        penangan.onString(id, data);
                        break;
                    }
                    case 0x02: { // LOAD_CLASS
                        p.bacaU4();
                        long idKelas = p.bacaId();
                        p.bacaU4();
                        long idNama = p.bacaId();
                        penangan.onLoadClass(idKelas, idNama);
                        break;
                    }
                    case 0x0C: // HEAP_DUMP
                    case 0x1C: { // HEAP_DUMP_SEGMENT
                        long akhir = panjang;
                        long sudah = 0;
                        while (sudah < akhir) {
                            int sub = p.bacaU1();
                            sudah += 1;
                            switch (sub) {
                                case 0xFF: // GC_ROOT_UNKNOWN
                                    penangan.onRoot(p.bacaId());
                                    sudah += p.ukuranId;
                                    break;
                                case 0x01: // GC_ROOT_JNI_GLOBAL
                                    penangan.onRoot(p.bacaId());
                                    p.bacaId();
                                    sudah += 2L * p.ukuranId;
                                    break;
                                case 0x02: // GC_ROOT_JNI_LOCAL
                                case 0x03: // GC_ROOT_JAVA_FRAME
                                    penangan.onRoot(p.bacaId());
                                    p.bacaU4();
                                    p.bacaU4();
                                    sudah += p.ukuranId + 8;
                                    break;
                                case 0x04: // GC_ROOT_NATIVE_STACK
                                case 0x06: // GC_ROOT_THREAD_BLOCK
                                    penangan.onRoot(p.bacaId());
                                    p.bacaU4();
                                    sudah += p.ukuranId + 4;
                                    break;
                                case 0x05: // GC_ROOT_STICKY_CLASS
                                case 0x07: // GC_ROOT_MONITOR_USED
                                    penangan.onRoot(p.bacaId());
                                    sudah += p.ukuranId;
                                    break;
                                case 0x08: // GC_ROOT_THREAD_OBJECT
                                    penangan.onRoot(p.bacaId());
                                    p.bacaU4();
                                    p.bacaU4();
                                    sudah += p.ukuranId + 8;
                                    break;
                                case 0x20: { // CLASS_DUMP
                                    long idKelas = p.bacaId();
                                    p.bacaU4();
                                    p.bacaId(); // super
                                    p.bacaId(); // classloader
                                    p.bacaId(); // signers
                                    p.bacaId(); // protection domain
                                    p.bacaId(); // reserved 1
                                    p.bacaId(); // reserved 2
                                    p.bacaU4(); // ukuran instance
                                    sudah += 7L * p.ukuranId + 8;
                                    int konstanta = p.bacaU2();
                                    sudah += 2;
                                    for (int i = 0; i < konstanta; i++) {
                                        p.bacaU2();
                                        int tipe = p.bacaU1();
                                        int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                        if (uk < 0) throw new IOException(
                                                "tipe konstanta hprof tak dikenal: " + tipe);
                                        p.lewati(uk);
                                        sudah += 3L + uk;
                                    }
                                    int statis = p.bacaU2();
                                    sudah += 2;
                                    List<Long> statisObjek = new ArrayList<Long>();
                                    for (int i = 0; i < statis; i++) {
                                        p.bacaId();
                                        int tipe = p.bacaU1();
                                        int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                        if (uk < 0) throw new IOException(
                                                "tipe medan statis hprof tak dikenal: " + tipe);
                                        if (tipe == 2) {
                                            statisObjek.add(Long.valueOf(p.bacaId()));
                                        } else {
                                            p.lewati(uk);
                                        }
                                        sudah += p.ukuranId + 1L + uk;
                                    }
                                    int medan = p.bacaU2();
                                    sudah += 2;
                                    List<Integer> tipeMedan = new ArrayList<Integer>();
                                    for (int i = 0; i < medan; i++) {
                                        p.bacaId();
                                        int tipe = p.bacaU1();
                                        tipeMedan.add(Integer.valueOf(tipe));
                                        sudah += p.ukuranId + 1L;
                                    }
                                    penangan.onClassDump(idKelas, tipeMedan, statisObjek);
                                    break;
                                }
                                case 0x21: { // INSTANCE_DUMP
                                    long id = p.bacaId();
                                    p.bacaU4();
                                    long idKelas = p.bacaId();
                                    long nBita = p.bacaU4();
                                    sudah += 2L * p.ukuranId + 8;
                                    // Nilai medan tidak perlu dibaca di sini
                                    // (kecuali pemindaian penahan — lihat
                                    // pindaiPenahan); lewati saja.
                                    p.lewati(nBita);
                                    sudah += nBita;
                                    penangan.onInstanceDump(id, idKelas);
                                    break;
                                }
                                case 0x22: { // OBJECT_ARRAY_DUMP
                                    long id = p.bacaId();
                                    p.bacaU4();
                                    long jumlah = p.bacaU4();
                                    long idKelas = p.bacaId();
                                    sudah += 2L * p.ukuranId + 8;
                                    long[] elemen = new long[(int) Math.min(jumlah, 100000)];
                                    int baca = 0;
                                    for (long i = 0; i < jumlah; i++) {
                                        long e = p.bacaId();
                                        if (baca < elemen.length) elemen[baca++] = e;
                                    }
                                    sudah += jumlah * p.ukuranId;
                                    long[] potong = new long[baca];
                                    System.arraycopy(elemen, 0, potong, 0, baca);
                                    penangan.onObjectArray(id, idKelas, potong);
                                    break;
                                }
                                case 0x23: { // PRIMITIVE_ARRAY_DUMP
                                    long id = p.bacaId();
                                    p.bacaU4();
                                    long jumlah = p.bacaU4();
                                    int tipe = p.bacaU1();
                                    sudah += p.ukuranId + 9;
                                    int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                    if (uk < 0) throw new IOException(
                                            "tipe larik primitif hprof tak dikenal: " + tipe);
                                    p.lewati(jumlah * uk);
                                    sudah += jumlah * uk;
                                    penangan.onPrimitiveArray(id, tipe, jumlah);
                                    break;
                                }
                                default:
                                    // Sub-rekaman tak dikenal: tidak ada
                                    // panjangnya, jadi segmen ini tidak bisa
                                    // dilanjutkan dengan aman.
                                    return;
                            }
                        }
                        break;
                    }
                    case 0x2C: // HEAP_DUMP_END
                        break;
                    default:
                        p.lewati(panjang);
                        break;
                }
            }
        } finally {
            p.tutup();
        }
    }

    /** Urai satu berkas .hprof menjadi hitungan objek per kelas + akar GC. */
    public static HasilHprof uraiHprof(File berkas) throws IOException {
        final HasilHprof hasil = new HasilHprof();
        if (berkas == null || !berkas.isFile()) {
            hasil.catatan = "berkas heap dump tidak ditemukan";
            return hasil;
        }
        hasil.totalBita = berkas.length();
        // Lewat 1: peta kelas (LOAD_CLASS + CLASS_DUMP).
        final Map<Long, Long> namaStringOlehKelas = new HashMap<Long, Long>();
        pindaiHprof(berkas, new PenanganHprof() {
            void onLoadClass(long idObjekKelas, long idStringNama) {
                namaStringOlehKelas.put(Long.valueOf(idObjekKelas),
                        Long.valueOf(idStringNama));
            }

            void onClassDump(long idObjekKelas, List<Integer> tipeMedanInstance,
                             List<Long> statisObjek) {
                hasil.medanInstance.put(Long.valueOf(idObjekKelas), tipeMedanInstance);
                hasil.medanStatisObjek.put(Long.valueOf(idObjekKelas), statisObjek);
            }
        });
        // Lewat 2: nama kelas dari STRING yang dirujuk LOAD_CLASS.
        final Set<Long> perluNama = new HashSet<Long>(namaStringOlehKelas.values());
        final Map<Long, String> stringOlehId = new HashMap<Long, String>();
        pindaiHprof(berkas, new PenanganHprof() {
            void onString(long id, byte[] isi) {
                if (perluNama.contains(Long.valueOf(id))) {
                    try {
                        stringOlehId.put(Long.valueOf(id), new String(isi, "UTF-8"));
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
        for (Map.Entry<Long, Long> e : namaStringOlehKelas.entrySet()) {
            String nama = stringOlehId.get(e.getValue());
            if (nama != null) {
                hasil.namaKelasObjek.put(e.getKey(), nama.replace('/', '.'));
            }
        }
        // Lewat 3: hitung instance + larik, kumpulkan akar.
        pindaiHprof(berkas, new PenanganHprof() {
            void onInstanceDump(long id, long idObjekKelas) {
                String nama = hasil.namaKelasObjek.get(Long.valueOf(idObjekKelas));
                if (nama == null) nama = "tak-dikenal";
                tambahHitung(hasil, nama);
                hasil.totalObjek++;
            }

            void onObjectArray(long id, long idObjekKelas, long[] elemen) {
                String nama = hasil.namaKelasObjek.get(Long.valueOf(idObjekKelas));
                if (nama == null) nama = "larik";
                else if (nama.startsWith("[")) nama = "larik:" + nama;
                else nama = "larik:" + nama;
                tambahHitung(hasil, nama);
                hasil.totalObjek++;
            }

            void onPrimitiveArray(long id, int tipe, long jumlah) {
                tambahHitung(hasil, "larik-primitif:" + namaTipePrimitif(tipe));
                hasil.totalObjek++;
            }

            void onRoot(long id) {
                hasil.akar.add(Long.valueOf(id));
            }
        });
        // Urutkan jumlahPerKelas menurun (untuk laporan).
        List<Map.Entry<String, Long>> entri =
                new ArrayList<Map.Entry<String, Long>>(hasil.jumlahPerKelas.entrySet());
        for (int i = 0; i < entri.size(); i++) {
            for (int j = i + 1; j < entri.size(); j++) {
                if (entri.get(j).getValue().longValue()
                        > entri.get(i).getValue().longValue()) {
                    Map.Entry<String, Long> t = entri.get(i);
                    entri.set(i, entri.get(j));
                    entri.set(j, t);
                }
            }
        }
        hasil.jumlahPerKelas.clear();
        for (int i = 0; i < entri.size(); i++) {
            hasil.jumlahPerKelas.put(entri.get(i).getKey(), entri.get(i).getValue());
        }
        return hasil;
    }

    static void tambahHitung(HasilHprof hasil, String nama) {
        Long lama = hasil.jumlahPerKelas.get(nama);
        hasil.jumlahPerKelas.put(nama, Long.valueOf(lama == null ? 1L : lama.longValue() + 1L));
    }

    /** Ambil contoh id objek dari kelas bernama (maks N), untuk dilacak penahannya. */
    public static List<Long> contohObjek(File berkas, final String namaKelas, final int maks)
            throws IOException {
        final HasilHprof h = uraiHprof(berkas);
        Long idKelas = null;
        for (Map.Entry<Long, String> e : h.namaKelasObjek.entrySet()) {
            if (namaKelas.equals(e.getValue())) {
                idKelas = e.getKey();
                break;
            }
        }
        final List<Long> keluar = new ArrayList<Long>();
        if (idKelas == null) return keluar;
        final long cari = idKelas.longValue();
        pindaiHprof(berkas, new PenanganHprof() {
            void onInstanceDump(long id, long idObjekKelas) {
                if (idObjekKelas == cari && keluar.size() < maks) {
                    keluar.add(Long.valueOf(id));
                }
            }
        });
        return keluar;
    }

    /**
     * Lacak rantai penahan satu objek sampai ke akar GC: tiap putaran
     * memindai berkas untuk mencari siapa yang merujuk objek di
     * perbatasan kini (medan instance, elemen larik, atau medan statis).
     * Mengembalikan langkah rantai dari akar ke objek, atau daftar
     * kosong bila tidak ketemu dalam batas kedalaman.
     */
    public static List<String> lacakPenahan(File berkas, long idObjek, int maksDalam)
            throws IOException {
        List<String> kosong = new ArrayList<String>();
        if (berkas == null || !berkas.isFile()) return kosong;
        final HasilHprof h = uraiHprof(berkas);
        // anakOlehPenahan: id penahan -> id objek yang ditahannya.
        final Map<Long, Long> anakOlehPenahan = new HashMap<Long, Long>();
        // deskripsiPenahan: id penahan -> cara ia menahan anaknya.
        final Map<Long, String> deskripsiPenahan = new HashMap<Long, String>();
        Set<Long> perbatasan = new HashSet<Long>();
        perbatasan.add(Long.valueOf(idObjek));
        Set<Long> sudahDilihat = new HashSet<Long>();
        sudahDilihat.add(Long.valueOf(idObjek));
        for (int dalam = 1; dalam <= maksDalam; dalam++) {
            final Set<Long> cari = new HashSet<Long>(perbatasan);
            final Set<Long> penahanBaru = new HashSet<Long>();
            // Medan statis: kelas yang menahan lewat medan statisnya.
            for (Map.Entry<Long, List<Long>> e : h.medanStatisObjek.entrySet()) {
                List<Long> target = e.getValue();
                if (target == null) continue;
                for (int i = 0; i < target.size(); i++) {
                    if (cari.contains(target.get(i))) {
                        String namaKelas = h.namaKelasObjek.get(e.getKey());
                        if (namaKelas == null) namaKelas = "kelas";
                        penahanBaru.add(e.getKey());
                        if (!anakOlehPenahan.containsKey(e.getKey())) {
                            anakOlehPenahan.put(e.getKey(), target.get(i));
                            deskripsiPenahan.put(e.getKey(),
                                    "medan statis di " + namaKelas);
                        }
                    }
                }
            }
            // Pindai instance + larik untuk rujukan ke perbatasan.
            pindaiHprofPenahan(berkas, cari, h.medanInstance, penahanBaru,
                    deskripsiPenahan, anakOlehPenahan, h.namaKelasObjek);
            // Saring yang sudah dilihat.
            Set<Long> berikut = new HashSet<Long>();
            for (Long p : penahanBaru) {
                if (sudahDilihat.contains(p)) continue;
                sudahDilihat.add(p);
                berikut.add(p);
            }
            if (berikut.isEmpty()) return kosong;
            // Apakah ada penahan yang merupakan akar GC?
            Long akarKetemu = null;
            for (Long p : berikut) {
                if (h.akar.contains(p)) {
                    akarKetemu = p;
                    break;
                }
            }
            if (akarKetemu != null) {
                // Susun rantai dari akar ke objek asal dengan mengikuti
                // pasangan penahan -> anak.
                List<String> rantai = new ArrayList<String>();
                Long kini = akarKetemu;
                Set<Long> sudahJalan = new HashSet<Long>();
                while (kini != null && !sudahJalan.contains(kini)) {
                    sudahJalan.add(kini);
                    String nm = h.namaKelasObjek.get(kini);
                    if (nm == null) nm = "objek";
                    String d = deskripsiPenahan.get(kini);
                    if (kini.equals(akarKetemu)) {
                        rantai.add("akar GC: " + nm
                                + (d == null ? "" : " (" + d + ")"));
                    } else {
                        rantai.add(nm + (d == null ? "" : " (" + d + ")"));
                    }
                    Long anak = anakOlehPenahan.get(kini);
                    if (anak == null || anak.longValue() == idObjek) break;
                    kini = anak;
                }
                String nmAkhir = h.namaKelasObjek.get(Long.valueOf(idObjek));
                if (nmAkhir == null) nmAkhir = "objek";
                rantai.add(nmAkhir + " (objek yang dilacak)");
                return rantai;
            }
            perbatasan = berikut;
        }
        return kosong;
    }

    /**
     * Pindai heap untuk mencari instance/larik yang merujuk salah satu id
     * di cari. Mengisi penahanBaru + deskripsiPenahan + anakOlehPenahan.
     * Dipisah dari pindaiHprof karena INSTANCE_DUMP perlu dibaca nilai
     * medannya.
     */
    static void pindaiHprofPenahan(File berkas, final Set<Long> cari,
                                   final Map<Long, List<Integer>> medan,
                                   final Set<Long> penahanBaru,
                                   final Map<Long, String> deskripsiPenahan,
                                   final Map<Long, Long> anakOlehPenahan,
                                   final Map<Long, String> namaKelas) throws IOException {
        PembacaHprof p = new PembacaHprof(new FileInputStream(berkas));
        try {
            int b;
            int hitungSihir = 0;
            while (hitungSihir < 64) {
                b = p.bacaU1();
                hitungSihir++;
                if (b == 0) break;
            }
            p.ukuranId = (int) p.bacaU4();
            if (p.ukuranId != 4 && p.ukuranId != 8) p.ukuranId = 4;
            p.bacaU8();
            while (true) {
                int tag;
                try {
                    tag = p.bacaU1();
                } catch (EOFException e) {
                    break;
                }
                p.bacaU4();
                long panjang = p.bacaU4();
                if (tag == 0x0C || tag == 0x1C) {
                    long sudah = 0;
                    while (sudah < panjang) {
                        int sub = p.bacaU1();
                        sudah += 1;
                        switch (sub) {
                            case 0x20: { // CLASS_DUMP (lewati, sudah diurai)
                                long idKelas = p.bacaId();
                                p.bacaU4();
                                for (int i = 0; i < 6; i++) p.bacaId();
                                p.bacaU4();
                                sudah += 7L * p.ukuranId + 8;
                                int konstanta = p.bacaU2();
                                sudah += 2;
                                for (int i = 0; i < konstanta; i++) {
                                    p.bacaU2();
                                    int tipe = p.bacaU1();
                                    int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                    if (uk < 0) return;
                                    p.lewati(uk);
                                    sudah += 3L + uk;
                                }
                                int statis = p.bacaU2();
                                sudah += 2;
                                for (int i = 0; i < statis; i++) {
                                    p.bacaId();
                                    int tipe = p.bacaU1();
                                    int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                    if (uk < 0) return;
                                    p.lewati(uk);
                                    sudah += p.ukuranId + 1L + uk;
                                }
                                int medanN = p.bacaU2();
                                sudah += 2;
                                for (int i = 0; i < medanN; i++) {
                                    p.bacaId();
                                    p.bacaU1();
                                    sudah += p.ukuranId + 1L;
                                }
                                break;
                            }
                            case 0x21: { // INSTANCE_DUMP: baca nilai medan
                                long id = p.bacaId();
                                p.bacaU4();
                                long idKelas = p.bacaId();
                                long nBita = p.bacaU4();
                                sudah += 2L * p.ukuranId + 8;
                                List<Integer> tipeMedan = medan.get(Long.valueOf(idKelas));
                                if (tipeMedan == null) {
                                    p.lewati(nBita);
                                    sudah += nBita;
                                    break;
                                }
                                long dibaca = 0;
                                for (int i = 0; i < tipeMedan.size(); i++) {
                                    int tipe = tipeMedan.get(i).intValue();
                                    int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                    if (uk < 0) {
                                        p.lewati(nBita - dibaca);
                                        sudah += nBita;
                                        dibaca = nBita;
                                        break;
                                    }
                                    if (tipe == 2) {
                                        long nilai = p.bacaId();
                                        if (cari.contains(Long.valueOf(nilai))) {
                                            penahanBaru.add(Long.valueOf(id));
                                            String nm = namaKelas.get(Long.valueOf(idKelas));
                                            if (nm == null) nm = "objek";
                                            deskripsiPenahan.put(Long.valueOf(id),
                                                    "medan di " + nm);
                                            if (!anakOlehPenahan.containsKey(
                                                    Long.valueOf(id))) {
                                                anakOlehPenahan.put(Long.valueOf(id),
                                                        Long.valueOf(nilai));
                                            }
                                        }
                                    } else {
                                        p.lewati(uk);
                                    }
                                    dibaca += uk;
                                }
                                if (dibaca < nBita) p.lewati(nBita - dibaca);
                                sudah += nBita;
                                break;
                            }
                            case 0x22: { // OBJECT_ARRAY_DUMP
                                long id = p.bacaId();
                                p.bacaU4();
                                long jumlah = p.bacaU4();
                                long idKelas = p.bacaId();
                                sudah += 2L * p.ukuranId + 8;
                                boolean kena = false;
                                long kenaElemen = 0;
                                for (long i = 0; i < jumlah; i++) {
                                    long e = p.bacaId();
                                    if (cari.contains(Long.valueOf(e))) {
                                        kena = true;
                                        kenaElemen = e;
                                    }
                                }
                                sudah += jumlah * p.ukuranId;
                                if (kena) {
                                    penahanBaru.add(Long.valueOf(id));
                                    String nm = namaKelas.get(Long.valueOf(idKelas));
                                    if (nm == null) nm = "larik";
                                    deskripsiPenahan.put(Long.valueOf(id),
                                            "elemen larik " + nm);
                                    if (!anakOlehPenahan.containsKey(Long.valueOf(id))) {
                                        anakOlehPenahan.put(Long.valueOf(id),
                                                Long.valueOf(kenaElemen));
                                    }
                                }
                                break;
                            }
                            case 0x23: { // PRIMITIVE_ARRAY_DUMP (tak berisi ref)
                                p.bacaId();
                                p.bacaU4();
                                long jumlah = p.bacaU4();
                                int tipe = p.bacaU1();
                                sudah += p.ukuranId + 9;
                                int uk = ukuranTipeHprof(tipe, p.ukuranId);
                                if (uk < 0) return;
                                p.lewati(jumlah * uk);
                                sudah += jumlah * uk;
                                break;
                            }
                            case 0xFF:
                                p.bacaId();
                                sudah += p.ukuranId;
                                break;
                            case 0x01:
                                p.bacaId();
                                p.bacaId();
                                sudah += 2L * p.ukuranId;
                                break;
                            case 0x02:
                            case 0x03:
                                p.bacaId();
                                p.bacaU4();
                                p.bacaU4();
                                sudah += p.ukuranId + 8;
                                break;
                            case 0x04:
                            case 0x06:
                                p.bacaId();
                                p.bacaU4();
                                sudah += p.ukuranId + 4;
                                break;
                            case 0x05:
                            case 0x07:
                                p.bacaId();
                                sudah += p.ukuranId;
                                break;
                            case 0x08:
                                p.bacaId();
                                p.bacaU4();
                                p.bacaU4();
                                sudah += p.ukuranId + 8;
                                break;
                            default:
                                return;
                        }
                    }
                } else {
                    p.lewati(panjang);
                }
            }
        } finally {
            p.tutup();
        }
    }

    // ------------------------------------------------------------------
    // Util kecil.
    // ------------------------------------------------------------------

    /** Tulis teks ke berkas (UTF-8), buat folder bila perlu. */
    public static boolean tulisTeks(File f, String isi) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            OutputStream out = new BufferedOutputStream(new FileOutputStream(f));
            out.write(isi.getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Baca berkas teks (UTF-8); null bila gagal. */
    public static String bacaTeks(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) > 0) data.write(buf, 0, n);
            in.close();
            return new String(data.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }
}
