package com.androlua;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debugger Langkah-Demi-Langkah dan Inspektur Memori Waktu-Nyata
 * (Fitur 6, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Jejak eksekusi: teks "baris|kode|variabel=nilai, ..." per langkah.
 *  - Sesi debug: titik henti per nomor baris, lanjut sampai titik
 *    henti, langkah satu baris, masuk/keluar fungsi (diwakili jejak),
 *    dan daftar variabel saat ini.
 *
 * BATAS JUJUR: ini debugger JEJAK — ia menelusuri jejak eksekusi
 * yang direkam (atau ditulis tangan) baris demi baris, bukan
 * menjeda proses Android yang sedang berjalan lewat JDWP. Menjeda
 * aplikasi sungguhan di HP yang sama butuh jembatan debug dan izin
 * sistem yang belum ada di tahap ini.
 */
public class DebuggerLangkah {

    /** Satu langkah jejak: baris sumber + variabel saat itu. */
    public static class Langkah {
        public int baris = 0;
        public String kode = "";
        public Map<String, String> variabel = new LinkedHashMap<String, String>();
        public String toString() {
            return baris + ": " + kode;
        }
    }

    /** Satu sesi debug atas sebuah jejak. */
    public static class Sesi {
        public List<Langkah> jejak = new ArrayList<Langkah>();
        public List<Integer> titikHenti = new ArrayList<Integer>();
        public int posisi = -1;

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
        }
    }

    /**
     * Urai teks jejak. Format per baris:
     * "12|int total = harga * jumlah;|harga=5000, jumlah=3, total=15000"
     * Bagian variabel boleh kosong.
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
}
