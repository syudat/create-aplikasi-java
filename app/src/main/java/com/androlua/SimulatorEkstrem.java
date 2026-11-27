package com.androlua;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulator Kondisi Perangkat Ekstrem dan Auditor Kebocoran Memori
 * (Fitur 10, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Model keadaan layar (bundle): simulasikan mati proses — data
 *    yang tidak disimpan ke bundle hilang, yang disimpan pulih.
 *  - Skenario jaringan putus-nyambung, putar layar, dan hemat baterai
 *    sebagai pemeriksaan aturan atas model (mis. thread latar yang
 *    harus berhenti saat hemat baterai).
 *  - Pelacak kebocoran: daftarkan objek + pemiliknya; objek yang
 *    masih terdaftar setelah pemiliknya ditutup dilaporkan bocor
 *    beserta rantai pemiliknya.
 *
 * BATAS JUJUR: ini SIMULASI atas model keadaan, bukan mematikan
 * proses sungguhan atau memutus jaringan HP. Kebocoran dilacak dari
 * pendaftaran eksplisit di model, bukan dari heap dump sungguhan —
 * heap dump dan analisis referensi nyata butuh alat profil di HP.
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
}
