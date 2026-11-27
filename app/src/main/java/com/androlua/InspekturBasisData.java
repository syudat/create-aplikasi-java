package com.androlua;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspektur Basis Data SQLite, Room, dan Penyimpanan Lokal Langsung
 * (Fitur 8, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Baca skema dari berkas .db: pernyataan CREATE TABLE diambil dari
 *    teks yang tersimpan di berkas (halaman pertama SQLite menyimpan
 *    skema sebagai teks).
 *  - Model tabel di memori: baris + kolom, ubah sel, tambah/hapus
 *    baris, cadangan teks, dan eksekutor kueri SELECT sederhana
 *    (SELECT kolom FROM tabel WHERE kolom = 'nilai').
 *
 * BATAS JUJUR: pembacaan skema memakai pemindaian teks berkas .db,
 * bukan parser halaman SQLite penuh — tabel yang skemanya tidak
 * terbaca sebagai teks tidak muncul. Di HP, isi baris dibaca lewat
 * SQLiteDatabase Android; kelas ini memodelkan tabel, kueri, dan
 * pengeditannya. Pengeditan nilai di HP menulis balik ke berkas .db
 * lewat SQLiteDatabase; di sini modelnya diuji tanpa Android.
 */
public class InspekturBasisData {

    /** Satu tabel di memori. */
    public static class Tabel {
        public String nama = "";
        public List<String> kolom = new ArrayList<String>();
        public List<List<String>> baris = new ArrayList<List<String>>();

        public int indeksKolom(String namaKolom) {
            for (int i = 0; i < kolom.size(); i++) {
                if (kolom.get(i).equalsIgnoreCase(namaKolom)) return i;
            }
            return -1;
        }
    }

    /** Ambil teks yang bisa dibaca dari bita (untuk pemindaian skema). */
    static String teksTerbaca(byte[] data) {
        if (data == null) return "";
        StringBuilder b = new StringBuilder();
        int jalan = 0;
        for (int i = 0; i < data.length; i++) {
            int c = data[i] & 0xff;
            if (c >= 32 && c < 127) {
                b.append((char) c);
                jalan++;
            } else {
                if (jalan > 0) b.append('\n');
                jalan = 0;
            }
        }
        return b.toString();
    }

    /**
     * Baca nama tabel + pernyataan CREATE TABLE dari berkas .db.
     * Mengembalikan pernyataan CREATE lengkap per tabel yang ketemu.
     */
    public static List<String> bacaSkema(byte[] dbBytes) {
        List<String> hasil = new ArrayList<String>();
        if (dbBytes == null) return hasil;
        String teks = teksTerbaca(dbBytes);
        String[] baris = teks.split("\n");
        StringBuilder kini = null;
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (kini == null) {
                if (b.toUpperCase().startsWith("CREATE TABLE")) {
                    kini = new StringBuilder(b);
                    if (b.contains(";")) {
                        hasil.add(kini.toString());
                        kini = null;
                    }
                }
            } else {
                kini.append(" ").append(b);
                if (b.contains(";")) {
                    hasil.add(kini.toString());
                    kini = null;
                }
            }
        }
        return hasil;
    }

    /** Nama tabel dari pernyataan CREATE TABLE ("" bila tidak ketemu). */
    public static String namaTabelDariSkema(String create) {
        if (create == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "CREATE TABLE (?:IF NOT EXISTS )?\"?([A-Za-z_][A-Za-z0-9_]*)\"?",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(create);
        if (m.find()) return m.group(1);
        return "";
    }

    /** Daftar kolom dari pernyataan CREATE TABLE (di dalam kurung). */
    public static List<String> kolomDariSkema(String create) {
        List<String> hasil = new ArrayList<String>();
        if (create == null) return hasil;
        int awal = create.indexOf('(');
        int akhir = create.lastIndexOf(')');
        if (awal < 0 || akhir <= awal) return hasil;
        String dalam = create.substring(awal + 1, akhir);
        String[] bagian = dalam.split(",");
        for (int i = 0; i < bagian.length; i++) {
            String b = bagian[i].trim();
            if (b.length() == 0) continue;
            String atas = b.toUpperCase();
            if (atas.startsWith("PRIMARY KEY") || atas.startsWith("FOREIGN KEY")
                    || atas.startsWith("UNIQUE") || atas.startsWith("CHECK")
                    || atas.startsWith("CONSTRAINT")) continue;
            String[] kata = b.split("\\s+");
            if (kata.length > 0) {
                String nama = kata[0].replace("\"", "").replace("`", "")
                        .replace("[", "").replace("]", "");
                if (nama.length() > 0) hasil.add(nama);
            }
        }
        return hasil;
    }

    /** Buat tabel kosong dari pernyataan CREATE TABLE. */
    public static Tabel tabelDariSkema(String create) {
        Tabel t = new Tabel();
        t.nama = namaTabelDariSkema(create);
        t.kolom = kolomDariSkema(create);
        return t;
    }

    /** Ubah nilai satu sel (false bila baris/kolom tidak sah). */
    public static boolean ubahSel(Tabel t, int baris, int kolom, String nilaiBaru) {
        if (t == null || baris < 0 || baris >= t.baris.size()) return false;
        if (kolom < 0 || kolom >= t.kolom.size()) return false;
        List<String> r = t.baris.get(baris);
        while (r.size() < t.kolom.size()) r.add("");
        r.set(kolom, nilaiBaru == null ? "" : nilaiBaru);
        return true;
    }

    /** Tambah baris baru (nilai kurang diisi kosong). */
    public static void tambahBaris(Tabel t, String[] nilai) {
        if (t == null) return;
        List<String> r = new ArrayList<String>();
        for (int i = 0; i < t.kolom.size(); i++) {
            r.add(nilai != null && i < nilai.length && nilai[i] != null ? nilai[i] : "");
        }
        t.baris.add(r);
    }

    /** Hapus baris (false bila nomor tidak sah). */
    public static boolean hapusBaris(Tabel t, int baris) {
        if (t == null || baris < 0 || baris >= t.baris.size()) return false;
        t.baris.remove(baris);
        return true;
    }

    /**
     * Kueri SELECT sederhana: "SELECT a, b FROM tabel WHERE c = 'x'".
     * Tanpa WHERE = semua baris. Kolom "*" = semua kolom. Mengembalikan
     * baris hasil (nilai teks) atau null bila kueri tidak dikenali.
     */
    public static List<List<String>> jalankanKueri(Tabel t, String sql) {
        if (t == null || sql == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\S+)(?:\\s+WHERE\\s+(\\S+)\\s*=\\s*'([^']*)')?\\s*;?\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sql.trim());
        if (!m.find()) return null;
        if (!m.group(2).equalsIgnoreCase(t.nama)) return null;
        List<Integer> kolomPilih = new ArrayList<Integer>();
        String daftar = m.group(1).trim();
        if ("*".equals(daftar)) {
            for (int i = 0; i < t.kolom.size(); i++) kolomPilih.add(Integer.valueOf(i));
        } else {
            String[] nama = daftar.split(",");
            for (int i = 0; i < nama.length; i++) {
                int idx = t.indeksKolom(nama[i].trim());
                if (idx < 0) return null;
                kolomPilih.add(Integer.valueOf(idx));
            }
        }
        int kolomWhere = -1;
        String nilaiWhere = null;
        if (m.group(3) != null) {
            kolomWhere = t.indeksKolom(m.group(3));
            if (kolomWhere < 0) return null;
            nilaiWhere = m.group(4);
        }
        List<List<String>> hasil = new ArrayList<List<String>>();
        for (int i = 0; i < t.baris.size(); i++) {
            List<String> r = t.baris.get(i);
            if (kolomWhere >= 0) {
                String v = kolomWhere < r.size() ? r.get(kolomWhere) : "";
                if (!nilaiWhere.equals(v)) continue;
            }
            List<String> keluar = new ArrayList<String>();
            for (int j = 0; j < kolomPilih.size(); j++) {
                int idx = kolomPilih.get(j).intValue();
                keluar.add(idx < r.size() ? r.get(idx) : "");
            }
            hasil.add(keluar);
        }
        return hasil;
    }

    /** Cadangan tabel sebagai teks ("kolom1|kolom2" + baris "a|b"). */
    public static String cadangan(Tabel t) {
        if (t == null) return "";
        StringBuilder b = new StringBuilder();
        b.append("TABEL:").append(t.nama).append("\n");
        for (int i = 0; i < t.kolom.size(); i++) {
            if (i > 0) b.append("|");
            b.append(t.kolom.get(i));
        }
        b.append("\n");
        for (int i = 0; i < t.baris.size(); i++) {
            List<String> r = t.baris.get(i);
            for (int j = 0; j < t.kolom.size(); j++) {
                if (j > 0) b.append("|");
                b.append(j < r.size() ? r.get(j).replace("|", " ") : "");
            }
            b.append("\n");
        }
        return b.toString();
    }

    /** Pulihkan tabel dari teks cadangan (null bila bentuk salah). */
    public static Tabel pulihkan(String teks) {
        if (teks == null) return null;
        String[] baris = teks.split("\n");
        if (baris.length < 2 || !baris[0].startsWith("TABEL:")) return null;
        Tabel t = new Tabel();
        t.nama = baris[0].substring(6).trim();
        String[] kolom = baris[1].split("\\|", -1);
        for (int i = 0; i < kolom.length; i++) t.kolom.add(kolom[i]);
        for (int i = 2; i < baris.length; i++) {
            if (baris[i].length() == 0) continue;
            String[] nilai = baris[i].split("\\|", -1);
            tambahBaris(t, nilai);
        }
        return t;
    }

    /**
     * Pernyataan UPDATE untuk mengubah satu sel pada baris ke-n
     * (0 = baris pertama yang tampil) berdasarkan rowid. Dua "?"
     * diisi nilai baru lalu nomor baris. Subkueri rowid dipakai
     * supaya baris yang diubah tepat baris yang tampil, walau
     * rowid di tabel tidak urut (mis. sesudah ada baris dihapus).
     * Null bila nama tabel/kolom tidak sah (hanya huruf, angka,
     * dan garis bawah yang diizinkan — nama tidak pernah ditempel
     * mentah dari masukan pengguna tanpa pemeriksaan ini).
     */
    public static String sqlUbahSel(String namaTabel, String namaKolom) {
        if (namaTabel == null || namaKolom == null) return null;
        String t = namaTabel.trim();
        String k = namaKolom.trim();
        if (!t.matches("[A-Za-z_][A-Za-z0-9_]*")
                || !k.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
        return "UPDATE \"" + t + "\" SET \"" + k + "\" = ? WHERE rowid IN "
                + "(SELECT rowid FROM \"" + t + "\" LIMIT 1 OFFSET ?)";
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahTabel, int barisDiubah) {
        return "Tabel ditemukan: " + jumlahTabel + ". Baris diubah: " + barisDiubah + ".";
    }
}
