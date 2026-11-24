package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Kelola Kunci API (perintah al kautsar 2026-11-24 13:16 WITA).
 *
 * Kunci API disimpan LOKAL di HP saja (penyimpanan privat aplikasi),
 * tidak pernah dikirim ke mana pun kecuali ke API provider yang
 * dipilih, dan TIDAK PERNAH dicatat di log. Kelas ini murni tanpa
 * Android (penyimpanan disuntik lewat antarmuka Penyimpanan), jadi
 * bisa diuji di JVM.
 *
 * Model: beberapa kunci per provider (kunci cadangan). Bila satu kunci
 * gagal, pemanggil mencoba kunci berikutnya untuk provider yang sama
 * lewat kunciBerikutnya(provider, sesudahNama).
 *
 * Format simpan (satu String): satu baris per kunci,
 * "nama<TAB>provider<TAB>kunci". Nama divalidasi PenyediaAi (tanpa
 * \n \r \t |); nilai kunci divalidasi tanpa spasi — jadi format tidak
 * bisa rusak oleh isi yang sah.
 *
 * KEAMANAN: tidak satu pun metode di kelas ini mengembalikan atau
 * mencetak nilai kunci penuh. tampilTersamar hanya menunjukkan
 * 4 huruf terakhir (atau titik-titik bila terlalu pendek); daftar
 * tampil hanya berisi nama + provider + tersamar. Nilai kunci penuh
 * hanya dibaca lewat cari()/kunciBerikutnya() oleh kode yang memang
 * akan memakainya untuk memanggil API provider.
 */
public class KunciApi {

    /** Antarmuka penyimpanan (di Android: SharedPreferences privat). */
    public interface Penyimpanan {
        String baca(String kunci, String bawaan);
        void tulis(String kunci, String nilai);
    }

    /** Satu kunci tersimpan. */
    public static class Entri {
        public final String nama;
        public final String provider;
        public final String kunci;

        public Entri(String nama, String provider, String kunci) {
            this.nama = nama;
            this.provider = provider;
            this.kunci = kunci;
        }
    }

    /** Kunci penyimpanan di SharedPreferences privat. */
    public static final String KUNCI_PREF = "kunci_api_tersimpan";

    /** Semua kunci tersimpan, urutan ditambahkan. */
    public static List<Entri> daftar(Penyimpanan s) {
        List<Entri> hasil = new ArrayList<Entri>();
        if (s == null) return hasil;
        String mentah = s.baca(KUNCI_PREF, "");
        if (mentah == null || mentah.length() == 0) return hasil;
        String[] baris = mentah.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].length() == 0) continue;
            String[] bagian = baris[i].split("\t", -1);
            if (bagian.length != 3) continue;
            if (bagian[0].length() == 0 || bagian[1].length() == 0
                    || bagian[2].length() == 0) continue;
            hasil.add(new Entri(bagian[0], bagian[1], bagian[2]));
        }
        return hasil;
    }

    private static void simpanSemua(Penyimpanan s, List<Entri> daftar) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < daftar.size(); i++) {
            Entri e = daftar.get(i);
            if (i > 0) b.append('\n');
            b.append(e.nama).append('\t').append(e.provider).append('\t').append(e.kunci);
        }
        s.tulis(KUNCI_PREF, b.toString());
    }

    /**
     * Tambah kunci baru. Nama harus unik (tidak boleh ada kunci lain
     * dengan nama yang sama, provider apa pun). Provider harus dikenal
     * PenyediaAi. Mengembalikan null bila berhasil, pesan galat bila
     * gagal. Nilai kunci TIDAK PERNAH masuk pesan galat.
     */
    public static String tambah(Penyimpanan s, String nama, String provider, String kunci) {
        if (s == null) return "Penyimpanan tidak ada";
        String galatNama = PenyediaAi.galatNamaKunci(nama);
        if (galatNama != null) return galatNama;
        String galatNilai = PenyediaAi.galatNilaiKunci(kunci);
        if (galatNilai != null) return galatNilai;
        if (PenyediaAi.dariKode(provider) == null) return "Provider tidak dikenal";
        String n = nama.trim();
        String k = kunci.trim();
        List<Entri> semua = daftar(s);
        for (int i = 0; i < semua.size(); i++) {
            if (semua.get(i).nama.equals(n)) return "Nama kunci sudah ada";
        }
        semua.add(new Entri(n, provider, k));
        simpanSemua(s, semua);
        return null;
    }

    /** Ubah nilai kunci yang sudah ada (nama dan provider tetap). */
    public static String ubahNilai(Penyimpanan s, String nama, String kunciBaru) {
        if (s == null) return "Penyimpanan tidak ada";
        String galatNilai = PenyediaAi.galatNilaiKunci(kunciBaru);
        if (galatNilai != null) return galatNilai;
        List<Entri> semua = daftar(s);
        for (int i = 0; i < semua.size(); i++) {
            Entri e = semua.get(i);
            if (e.nama.equals(nama)) {
                semua.set(i, new Entri(e.nama, e.provider, kunciBaru.trim()));
                simpanSemua(s, semua);
                return null;
            }
        }
        return "Kunci tidak ditemukan";
    }

    /** Hapus kunci menurut namanya. true bila ada yang dihapus. */
    public static boolean hapus(Penyimpanan s, String nama) {
        if (s == null || nama == null) return false;
        List<Entri> semua = daftar(s);
        boolean kena = false;
        for (int i = semua.size() - 1; i >= 0; i--) {
            if (semua.get(i).nama.equals(nama)) {
                semua.remove(i);
                kena = true;
            }
        }
        if (kena) simpanSemua(s, semua);
        return kena;
    }

    /** Cari satu kunci menurut namanya; null bila tidak ada. */
    public static Entri cari(Penyimpanan s, String nama) {
        if (s == null || nama == null) return null;
        List<Entri> semua = daftar(s);
        for (int i = 0; i < semua.size(); i++) {
            if (semua.get(i).nama.equals(nama)) return semua.get(i);
        }
        return null;
    }

    /** Jumlah kunci untuk satu provider. */
    public static int jumlahKunci(Penyimpanan s, String provider) {
        if (s == null || provider == null) return 0;
        int jumlah = 0;
        List<Entri> semua = daftar(s);
        for (int i = 0; i < semua.size(); i++) {
            if (semua.get(i).provider.equals(provider)) jumlah++;
        }
        return jumlah;
    }

    /**
     * Kunci berikutnya untuk satu provider (rotasi cadangan).
     * Bila sesudahNama null/kosong: kunci pertama provider itu.
     * Bila sesudahNama diisi: kunci provider itu SESUDAH nama tersebut;
     * bila tidak ada lagi, kembali ke kunci pertama (putaran penuh) —
     * pemanggil yang membedakan "sudah mencoba semua" dengan mencatat
     * nama awal. Mengembalikan null bila provider itu tidak punya kunci.
     */
    public static Entri kunciBerikutnya(Penyimpanan s, String provider, String sesudahNama) {
        if (s == null || provider == null) return null;
        List<Entri> semua = daftar(s);
        List<Entri> milik = new ArrayList<Entri>();
        for (int i = 0; i < semua.size(); i++) {
            if (semua.get(i).provider.equals(provider)) milik.add(semua.get(i));
        }
        if (milik.isEmpty()) return null;
        if (sesudahNama == null || sesudahNama.length() == 0) return milik.get(0);
        for (int i = 0; i < milik.size(); i++) {
            if (milik.get(i).nama.equals(sesudahNama)) {
                return milik.get((i + 1) % milik.size());
            }
        }
        return milik.get(0);
    }

    /**
     * Tampilan tersamar satu nilai kunci: titik-titik + 4 huruf
     * terakhir. Kunci pendek (<= 4 huruf) hanya titik-titik.
     * TIDAK PERNAH mengembalikan nilai penuh.
     */
    public static String tampilTersamar(String kunci) {
        if (kunci == null || kunci.length() == 0) return "....";
        if (kunci.length() <= 4) return "....";
        return "...." + kunci.substring(kunci.length() - 4);
    }

    /**
     * Teks laporan daftar kunci untuk layar (nama + provider +
     * tersamar). Dijamin TIDAK mengandung satu pun nilai kunci penuh —
     * dipakai juga oleh uji untuk membuktikan tidak ada kebocoran.
     */
    public static String daftarTampil(Penyimpanan s) {
        List<Entri> semua = daftar(s);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < semua.size(); i++) {
            Entri e = semua.get(i);
            if (i > 0) b.append('\n');
            PenyediaAi.Provider p = PenyediaAi.dariKode(e.provider);
            String namaProvider = p == null ? e.provider : p.nama;
            b.append(e.nama).append(" (").append(namaProvider).append(") ")
                    .append(tampilTersamar(e.kunci));
        }
        return b.toString();
    }
}
