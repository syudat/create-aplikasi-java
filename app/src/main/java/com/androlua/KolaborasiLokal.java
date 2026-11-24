package com.androlua;

import java.util.Random;

/**
 * Kolaborasi Kode Waktu-Nyata Peer-to-Peer Tanpa Internet (Fitur 8).
 *
 * Protokol teks di atas soket TCP (satu baris = satu pesan):
 * bingkai "JENIS|isi" dengan jenis GABUNG, SISIP, HAPUS, APK, dan
 * LAPORAN_CRASH. Kelas ini MURNI Java (tanpa soket, tanpa Android)
 * supaya bisa diuji di JVM: soketnya (ServerSocket/Socket, utas
 * terima) ada di KolaborasiActivity yang tipis.
 *
 * Format isi per jenis:
 *   GABUNG        | PIN 6 digit, mis. "GABUNG|123456"
 *   SISIP         | posisi|teks (teks diloloskan: \n, \r, \\ — supaya
 *                   satu pesan tetap satu baris)
 *   HAPUS         | posisi|panjang
 *   APK           | nama-apk|ukuran-bita (isi biner menyusul di soket,
 *                   di luar bingkai teks ini)
 *   LAPORAN_CRASH | id-crash|log (log diloloskan seperti teks SISIP)
 *
 * Penyelerasan di sini sengaja ringan (operasi sisip/hapus berposisi,
 * bukan OT/CRDT penuh): dua perangkat menyunting bergantian lewat
 * tombol "Kirim Perubahan" — bila keduanya menyunting serentak,
 * perubahan terakhir yang tiba yang menang di posisi itu. Posisi di
 * luar rentang dokumen DIJEPIT, bukan crash.
 */
public class KolaborasiLokal {

    /** Port bawaan sesi Ruang Kolaborasi Lokal. */
    public static final int PORT_BAWAAN = 57811;

    public static final String JENIS_GABUNG = "GABUNG";
    public static final String JENIS_SISIP = "SISIP";
    public static final String JENIS_HAPUS = "HAPUS";
    public static final String JENIS_APK = "APK";
    public static final String JENIS_CRASH = "LAPORAN_CRASH";

    private static final String[] SEMUA_JENIS = {
            JENIS_GABUNG, JENIS_SISIP, JENIS_HAPUS, JENIS_APK, JENIS_CRASH
    };

    /** Satu pesan protokol: jenis + isi mentah sesudah pemisah pertama. */
    public static class Pesan {
        public final String jenis;
        public final String isi;

        public Pesan(String jenis, String isi) {
            this.jenis = jenis;
            this.isi = isi;
        }
    }

    /** Satu operasi penyuntingan dokumen (SISIP atau HAPUS). */
    public static class Operasi {
        public final String jenis;
        public final int posisi;
        /** Teks yang disisipkan (SISIP); null untuk HAPUS. */
        public final String teks;
        /** Panjang yang dihapus (HAPUS); 0 untuk SISIP. */
        public final int panjang;

        public Operasi(String jenis, int posisi, String teks, int panjang) {
            this.jenis = jenis;
            this.posisi = posisi;
            this.teks = teks;
            this.panjang = panjang;
        }
    }

    /** Header pengiriman APK (nama + ukuran; isi biner menyusul). */
    public static class InfoApk {
        public final String nama;
        public final long ukuran;

        public InfoApk(String nama, long ukuran) {
            this.nama = nama;
            this.ukuran = ukuran;
        }
    }

    /** true bila PIN tepat 6 digit angka ("123456"); null/salah -> false. */
    public static boolean pinValid(String pin) {
        return pin != null && pin.matches("[0-9]{6}");
    }

    /** Membuat PIN sesi baru: 6 digit angka (boleh diawali 0). */
    public static String buatPin() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    /** Membingkai pesan "JENIS|isi"; null bila jenis tidak dikenal. */
    public static String buatPesan(String jenis, String isi) {
        if (!jenisDikenal(jenis)) return null;
        if (isi == null) isi = "";
        return jenis + "|" + isi;
    }

    /** Mengurai satu baris menjadi Pesan; null bila baris rusak. */
    public static Pesan uraiPesan(String baris) {
        if (baris == null || baris.length() == 0) return null;
        int pisah = baris.indexOf('|');
        if (pisah <= 0) return null;
        String jenis = baris.substring(0, pisah);
        if (!jenisDikenal(jenis)) return null;
        return new Pesan(jenis, baris.substring(pisah + 1));
    }

    private static boolean jenisDikenal(String jenis) {
        if (jenis == null) return false;
        for (int i = 0; i < SEMUA_JENIS.length; i++) {
            if (SEMUA_JENIS[i].equals(jenis)) return true;
        }
        return false;
    }

    /** Pesan GABUNG untuk PIN; null bila PIN tidak valid. */
    public static String buatGabung(String pin) {
        if (!pinValid(pin)) return null;
        return buatPesan(JENIS_GABUNG, pin);
    }

    /** PIN dari pesan GABUNG; null bila bukan GABUNG atau PIN tidak valid. */
    public static String pinDariGabung(Pesan pesan) {
        if (pesan == null || !JENIS_GABUNG.equals(pesan.jenis)) return null;
        return pinValid(pesan.isi) ? pesan.isi : null;
    }

    /** Pesan SISIP: sisipkan teks pada posisi (teks diloloskan). */
    public static String buatSisip(int posisi, String teks) {
        return buatPesan(JENIS_SISIP, posisi + "|" + lolos(teks));
    }

    /** Pesan HAPUS: hapus sejumlah karakter mulai posisi. */
    public static String buatHapus(int posisi, int panjang) {
        return buatPesan(JENIS_HAPUS, posisi + "|" + panjang);
    }

    /**
     * Header pengiriman APK; null bila nama tidak aman (kosong, berisi
     * pemisah jalur, atau "..") atau ukuran tidak positif.
     */
    public static String buatApk(String nama, long ukuran) {
        if (!namaApkAman(nama) || ukuran <= 0) return null;
        return buatPesan(JENIS_APK, nama + "|" + ukuran);
    }

    /** Pesan LAPORAN_CRASH: id crash + teks log (log diloloskan). */
    public static String buatCrash(String id, String log) {
        if (id == null || id.length() == 0) return null;
        return buatPesan(JENIS_CRASH, id + "|" + lolos(log));
    }

    /**
     * Mengurai pesan SISIP/HAPUS menjadi Operasi; null bila bukan
     * operasi, format isi rusak, atau angkanya tidak terbaca.
     */
    public static Operasi uraiOperasi(Pesan pesan) {
        if (pesan == null) return null;
        if (JENIS_SISIP.equals(pesan.jenis)) {
            int pisah = pesan.isi.indexOf('|');
            if (pisah < 0) return null;
            Integer posisi = angka(pesan.isi.substring(0, pisah));
            if (posisi == null) return null;
            return new Operasi(JENIS_SISIP, posisi.intValue(),
                    bukaLolos(pesan.isi.substring(pisah + 1)), 0);
        }
        if (JENIS_HAPUS.equals(pesan.jenis)) {
            int pisah = pesan.isi.indexOf('|');
            if (pisah < 0) return null;
            Integer posisi = angka(pesan.isi.substring(0, pisah));
            Integer panjang = angka(pesan.isi.substring(pisah + 1));
            if (posisi == null || panjang == null || panjang.intValue() < 0) return null;
            return new Operasi(JENIS_HAPUS, posisi.intValue(), null, panjang.intValue());
        }
        return null;
    }

    /**
     * Mengurai header APK menjadi InfoApk; null bila bukan APK, nama
     * tidak aman, atau ukuran tidak terbaca / tidak positif.
     */
    public static InfoApk uraiApk(Pesan pesan) {
        if (pesan == null || !JENIS_APK.equals(pesan.jenis)) return null;
        int pisah = pesan.isi.lastIndexOf('|');
        if (pisah <= 0) return null;
        String nama = pesan.isi.substring(0, pisah);
        Long ukuran = angkaPanjang(pesan.isi.substring(pisah + 1));
        if (!namaApkAman(nama) || ukuran == null || ukuran.longValue() <= 0) return null;
        return new InfoApk(nama, ukuran.longValue());
    }

    /** Id + log dari pesan LAPORAN_CRASH; null bila bukan crash. */
    public static String[] uraiCrash(Pesan pesan) {
        if (pesan == null || !JENIS_CRASH.equals(pesan.jenis)) return null;
        int pisah = pesan.isi.indexOf('|');
        if (pisah < 0) return null;
        String id = pesan.isi.substring(0, pisah);
        if (id.length() == 0) return null;
        return new String[]{id, bukaLolos(pesan.isi.substring(pisah + 1))};
    }

    /**
     * Menerapkan operasi ke dokumen. Posisi di luar rentang DIJEPIT
     * (negatif -> 0, lebih dari panjang -> akhir), panjang hapus
     * dipangkas ke sisa dokumen — tidak pernah crash. Dokumen null
     * dianggap kosong; operasi null mengembalikan dokumen apa adanya.
     */
    public static String terapkanOperasi(String dokumen, Operasi op) {
        if (dokumen == null) dokumen = "";
        if (op == null) return dokumen;
        int posisi = jepit(op.posisi, 0, dokumen.length());
        if (JENIS_SISIP.equals(op.jenis)) {
            String teks = op.teks == null ? "" : op.teks;
            return dokumen.substring(0, posisi) + teks + dokumen.substring(posisi);
        }
        if (JENIS_HAPUS.equals(op.jenis)) {
            int panjang = Math.max(0, op.panjang);
            int akhir = Math.min(dokumen.length(), posisi + panjang);
            return dokumen.substring(0, posisi) + dokumen.substring(akhir);
        }
        return dokumen;
    }

    private static int jepit(int nilai, int bawah, int atas) {
        if (nilai < bawah) return bawah;
        if (nilai > atas) return atas;
        return nilai;
    }

    private static boolean namaApkAman(String nama) {
        if (nama == null || nama.length() == 0) return false;
        if (nama.indexOf('/') >= 0 || nama.indexOf('\\') >= 0) return false;
        if (nama.indexOf("..") >= 0) return false;
        return true;
    }

    private static Integer angka(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long angkaPanjang(String s) {
        try {
            return Long.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Meloloskan teks supaya muat satu baris protokol: backslash dulu,
     * lalu baris baru dan carriage return. Kebalikan: bukaLolos().
     */
    static String lolos(String teks) {
        if (teks == null) return "";
        StringBuilder b = new StringBuilder(teks.length());
        for (int i = 0; i < teks.length(); i++) {
            char c = teks.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else b.append(c);
        }
        return b.toString();
    }

    static String bukaLolos(String teks) {
        if (teks == null) return "";
        StringBuilder b = new StringBuilder(teks.length());
        for (int i = 0; i < teks.length(); i++) {
            char c = teks.charAt(i);
            if (c == '\\' && i + 1 < teks.length()) {
                char d = teks.charAt(i + 1);
                if (d == 'n') {
                    b.append('\n');
                    i++;
                } else if (d == 'r') {
                    b.append('\r');
                    i++;
                } else if (d == '\\') {
                    b.append('\\');
                    i++;
                } else {
                    b.append(c);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
