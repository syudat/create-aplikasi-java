package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indeks alat Terminal dari repositori alat-termux (perintah al kautsar
 * 2026-11-23, fitur "Pasang Alat Terminal", masih uji coba) — logika
 * murni tanpa Android, bisa diuji di JVM.
 *
 * Repositori berisi cermin bootstrap Termux resmi (sama persis dengan
 * yang diverifikasi BootstrapTermux) dan alat skrip tambahan yang
 * dipasang ke prefix/bin. Berkas daftar-alat.json di akar repositori
 * adalah indeksnya; kelas ini mengurainya.
 */
public class IndeksAlat {

    /** Alamat dasar isi mentah repositori (cabang main). */
    public static final String REPO_RAW =
            "https://raw.githubusercontent.com/syudat/alat-termux/main/";

    /** URL berkas di dalam repositori dari jalur relatifnya. */
    public static String urlBerkas(String berkas) {
        return REPO_RAW + berkas;
    }

    /** URL indeks alat. */
    public static String urlIndeks() {
        return REPO_RAW + "daftar-alat.json";
    }

    /** URL cermin bootstrap untuk arsitektur tertentu (aarch64/arm/i686/x86_64). */
    public static String urlBootstrap(String arch) {
        return REPO_RAW + "bootstrap/bootstrap-" + arch + ".zip";
    }

    public static class Alat {
        public String nama = "";
        public String berkas = "";
        public String tujuan = "";
        public String deskripsi = "";
    }

    private static String ambilString(String objek, String kunci) {
        Matcher m = Pattern.compile(
                "\"" + kunci + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(objek);
        if (!m.find()) return "";
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Mengurai isi daftar-alat.json menjadi daftar alat tambahan.
     * Mengembalikan daftar kosong bila JSON tidak ditemukan bentuknya.
     */
    public static List<Alat> urai(String json) {
        List<Alat> hasil = new ArrayList<Alat>();
        if (json == null) return hasil;
        int i = json.indexOf("\"alat\"");
        if (i < 0) return hasil;
        int awal = json.indexOf('[', i);
        if (awal < 0) return hasil;
        // Pindai objek-objek { ... } di dalam larik (hitung kedalaman kurung).
        int kedalaman = 0;
        int mulaiObjek = -1;
        for (int p = awal + 1; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '{') {
                if (kedalaman == 0) mulaiObjek = p;
                kedalaman++;
            } else if (c == '}') {
                kedalaman--;
                if (kedalaman == 0 && mulaiObjek >= 0) {
                    String objek = json.substring(mulaiObjek, p + 1);
                    Alat a = new Alat();
                    a.nama = ambilString(objek, "nama");
                    a.berkas = ambilString(objek, "berkas");
                    a.tujuan = ambilString(objek, "tujuan");
                    a.deskripsi = ambilString(objek, "deskripsi");
                    if (a.berkas.length() > 0 && a.tujuan.length() > 0) hasil.add(a);
                    mulaiObjek = -1;
                }
            } else if (c == ']' && kedalaman == 0) {
                break;
            }
        }
        return hasil;
    }
}
