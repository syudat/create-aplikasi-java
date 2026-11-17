package com.androlua;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Offline (Tahap 3G).
 *
 * Paket alat "AI" lokal tanpa internet dan tanpa model AI sungguhan —
 * semuanya aturan sederhana di dalam HP, dan itu ditulis jujur di layar.
 * Empat alat:
 * (a) perbaiki teks otomatis (kapital awal kalimat, tanda baca, spasi
 * ganda, spasi sebelum tanda baca), (b) ringkas teks panjang (ambil
 * kalimat yang paling penting menurut frekuensi kata), (c) buat nama
 * aplikasi dari deskripsi (gabung kata kunci), (d) saran perbaikan nama
 * variabel yang terlalu pendek di kode.
 *
 * Murni Java (java.util/regex, tanpa API Android) supaya bisa diuji
 * ulang di JRE.
 */
public class AiOffline {

    /** Satu saran nama variabel. */
    public static class SaranNama {
        /** Berkas relatif terhadap akar proyek ("" bila dari teks langsung). */
        public final String berkas;
        /** Nomor baris (1-based). */
        public final int baris;
        /** Nama variabel yang terlalu pendek. */
        public final String nama;
        /** Saran perbaikan. */
        public final String saran;

        public SaranNama(String berkas, int baris, String nama, String saran) {
            this.berkas = berkas;
            this.baris = baris;
            this.nama = nama;
            this.saran = saran;
        }
    }

    /** Kata yang tidak dihitung sebagai kata kunci (Indonesia). */
    private static final String[] KATA_PENGISI = {
        "aplikasi", "aplikasinya", "buat", "buatkan", "saya", "mau", "ingin",
        "yang", "untuk", "dengan", "dan", "atau", "sebuah", "ini", "itu",
        "adalah", "dari", "ke", "di", "ada", "bisa", "dapat", "tolong",
        "sederhana", "baru", "punya", "memiliki", "tampilkan", "menampilkan",
        "simpan", "menyimpan", "buka", "membuka", "tambah", "menambah",
    };

    /** Kata yang tidak dihitung saat merangkum (Indonesia). */
    private static final String[] KATA_BERHENTI = {
        "yang", "dan", "atau", "adalah", "dari", "ke", "di", "ada", "ini",
        "itu", "dengan", "untuk", "saya", "kamu", "dia", "mereka", "kita",
        "akan", "sudah", "telah", "juga", "tidak", "bisa", "dapat", "agar",
        "supaya", "karena", "tetapi", "namun", "lalu", "kemudian", "saat",
        "ketika", "oleh", "pada", "sebagai", "dalam", "antara", "setelah",
        "sebelum", "sangat", "lebih", "paling", "semua", "setiap", "hanya",
    };

    private static boolean dalam(String[] daftar, String kata) {
        for (int i = 0; i < daftar.length; i++) {
            if (daftar[i].equals(kata)) return true;
        }
        return false;
    }

    /**
     * (a) Perbaiki teks: buang spasi ganda, buang spasi sebelum tanda
     * baca, pastikan ada spasi sesudah tanda baca, kapital di awal
     * kalimat, dan titik di akhir bila kalimat berakhir dengan huruf.
     */
    public static String perbaikiTeks(String teks) {
        if (teks == null) return "";
        String t = teks.trim();
        if (t.length() == 0) return "";
        // Spasi ganda (termasuk tab/baris baru berlebih) jadi satu spasi.
        t = t.replaceAll("\\s+", " ");
        // Buang spasi sebelum tanda baca.
        t = t.replaceAll("\\s+([,.!?;:])", "$1");
        // Pastikan satu spasi sesudah tanda baca bila diikuti huruf.
        t = t.replaceAll("([,.!?;:])(\\S)", "$1 $2");
        // Kapital di awal kalimat (awal teks + sesudah . ! ?).
        StringBuilder sb = new StringBuilder();
        boolean awalKalimat = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (awalKalimat && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                awalKalimat = false;
            } else {
                sb.append(c);
            }
            if (c == '.' || c == '!' || c == '?') awalKalimat = true;
        }
        t = sb.toString();
        // Titik di akhir bila berakhir dengan huruf atau angka.
        char akhir = t.charAt(t.length() - 1);
        if (Character.isLetterOrDigit(akhir)) t = t + ".";
        return t;
    }

    /**
     * (b) Ringkas teks: pecah jadi kalimat, beri skor tiap kalimat dari
     * frekuensi kata pentingnya, ambil N kalimat terbaik dengan urutan
     * asli tetap terjaga.
     */
    public static String ringkas(String teks, int maxKalimat) {
        if (teks == null) return "";
        if (maxKalimat < 1) maxKalimat = 1;
        String[] kalimat = teks.trim().split("(?<=[.!?])\\s+");
        List<String> bersih = new ArrayList<String>();
        for (int i = 0; i < kalimat.length; i++) {
            String k = kalimat[i].trim();
            if (k.length() > 0) bersih.add(k);
        }
        if (bersih.size() <= maxKalimat) return gabungKalimat(bersih);
        // Frekuensi kata (tanpa kata berhenti, tanpa memperhatikan huruf).
        Map<String, Integer> frek = new HashMap<String, Integer>();
        for (int i = 0; i < bersih.size(); i++) {
            String[] kata = bersih.get(i).toLowerCase().split("[^a-z0-9_]+");
            for (int j = 0; j < kata.length; j++) {
                if (kata[j].length() < 3 || dalam(KATA_BERHENTI, kata[j])) continue;
                Integer n = frek.get(kata[j]);
                frek.put(kata[j], Integer.valueOf(n == null ? 1 : n.intValue() + 1));
            }
        }
        // Skor tiap kalimat = jumlah frekuensi kata-katanya.
        int[] skor = new int[bersih.size()];
        for (int i = 0; i < bersih.size(); i++) {
            String[] kata = bersih.get(i).toLowerCase().split("[^a-z0-9_]+");
            for (int j = 0; j < kata.length; j++) {
                Integer n = frek.get(kata[j]);
                if (n != null) skor[i] += n.intValue();
            }
        }
        // Ambil indeks N terbaik (skor tertinggi; seri -> kalimat awal menang).
        List<Integer> pilih = new ArrayList<Integer>();
        for (int k = 0; k < maxKalimat; k++) {
            int terbaik = -1;
            for (int i = 0; i < skor.length; i++) {
                if (pilih.contains(Integer.valueOf(i))) continue;
                if (terbaik < 0 || skor[i] > skor[terbaik]) terbaik = i;
            }
            if (terbaik >= 0) pilih.add(Integer.valueOf(terbaik));
        }
        // Urutkan kembali sesuai urutan asli.
        for (int i = 0; i < pilih.size(); i++) {
            for (int j = i + 1; j < pilih.size(); j++) {
                if (pilih.get(j).intValue() < pilih.get(i).intValue()) {
                    Integer tmp = pilih.get(i);
                    pilih.set(i, pilih.get(j));
                    pilih.set(j, tmp);
                }
            }
        }
        List<String> hasil = new ArrayList<String>();
        for (int i = 0; i < pilih.size(); i++) hasil.add(bersih.get(pilih.get(i).intValue()));
        return gabungKalimat(hasil);
    }

    private static String gabungKalimat(List<String> kalimat) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kalimat.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(kalimat.get(i));
        }
        return sb.toString();
    }

    /**
     * (c) Buat nama aplikasi dari deskripsi: ambil kata kunci (bukan
     * kata pengisi), gabung jadi nama PascalCase. Mengembalikan sampai
     * 3 saran: gabungan 2 kata pertama, kata pertama saja, gabungan
     * semua kata kunci (maks. 3).
     */
    public static List<String> buatNama(String deskripsi) {
        List<String> hasil = new ArrayList<String>();
        if (deskripsi == null) return hasil;
        List<String> kunci = kataKunci(deskripsi);
        if (kunci.isEmpty()) return hasil;
        // Saran 1: gabungan 2 kata pertama.
        hasil.add(gabungNama(kunci, 0, Math.min(2, kunci.size())));
        // Saran 2: kata pertama saja (kapital).
        if (kunci.size() >= 2) hasil.add(kapital(kunci.get(0)));
        // Saran 3: gabungan semua kata kunci (maks. 3).
        if (kunci.size() >= 3) hasil.add(gabungNama(kunci, 0, 3));
        return hasil;
    }

    private static List<String> kataKunci(String teks) {
        List<String> kunci = new ArrayList<String>();
        String[] kata = teks.toLowerCase().split("[^a-z0-9_]+");
        for (int i = 0; i < kata.length; i++) {
            String k = kata[i];
            if (k.length() < 3) continue;
            if (dalam(KATA_PENGISI, k)) continue;
            if (kunci.contains(k)) continue;
            kunci.add(k);
        }
        return kunci;
    }

    private static String gabungNama(List<String> kata, int dari, int sampai) {
        StringBuilder sb = new StringBuilder();
        for (int i = dari; i < sampai; i++) sb.append(kapital(kata.get(i)));
        return sb.toString();
    }

    private static String kapital(String kata) {
        if (kata == null || kata.length() == 0) return kata;
        return Character.toUpperCase(kata.charAt(0)) + kata.substring(1);
    }

    /**
     * (d) Saran perbaikan nama variabel yang terlalu pendek (1-2 huruf)
     * di kode Lua/Java/Kotlin. Nama i/j/k (penghitung perulangan) tidak
     * disarankan. Saran nama diambil dari nilai yang diberikan: string
     * -> "teks...", angka -> "angka...", boolean -> "apakah...".
     */
    public static List<SaranNama> saranNamaVariabel(String kode, boolean lua) {
        List<SaranNama> hasil = new ArrayList<SaranNama>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n", -1);
        Pattern pLua = Pattern.compile("^\\s*local\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");
        Pattern pJava = Pattern.compile(
                "^\\s*(?:private|public|protected|static|final|\\s)*"
                + "(?:String|int|long|double|float|boolean|char|var)\\s+"
                + "([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");
        for (int i = 0; i < baris.length; i++) {
            Matcher m = (lua ? pLua : pJava).matcher(baris[i]);
            if (!m.find()) continue;
            String nama = m.group(1);
            if (nama.length() > 2) continue;
            if ("i".equals(nama) || "j".equals(nama) || "k".equals(nama)) continue;
            String nilai = m.group(2).trim();
            String saran;
            if (nilai.startsWith("\"") || nilai.startsWith("'")) {
                saran = "Ganti \"" + nama + "\" jadi \"teks" + kapital(nama)
                        + "\" — namanya menjelaskan bahwa isinya teks.";
            } else if (nilai.matches("^[0-9].*")) {
                saran = "Ganti \"" + nama + "\" jadi \"angka" + kapital(nama)
                        + "\" — namanya menjelaskan bahwa isinya angka.";
            } else if ("true".equals(nilai) || "false".equals(nilai)) {
                saran = "Ganti \"" + nama + "\" jadi \"apakah" + kapital(nama)
                        + "\" — namanya menjelaskan bahwa isinya benar/salah.";
            } else {
                saran = "Ganti \"" + nama + "\" jadi nama yang menjelaskan isinya, "
                        + "misalnya \"nilai" + kapital(nama) + "\" — nama 1-2 huruf "
                        + "susah dibaca saat kode sudah panjang.";
            }
            hasil.add(new SaranNama("", i + 1, nama, saran));
        }
        return hasil;
    }
}
