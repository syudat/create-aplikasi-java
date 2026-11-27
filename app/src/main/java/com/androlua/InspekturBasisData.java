package com.androlua;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspektur Basis Data SQLite, Room, dan Penyimpanan Lokal Langsung
 * (Fitur 8, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - Parser halaman SQLite penuh (format berkas SQLite): baca
 *    tajuk, telusuri pohon-B tabel sqlite_master untuk SEMUA tabel
 *    beserta SQL CREATE-nya, lalu baca baris tiap tabel dari
 *    pohon-B-nya sendiri (termasuk halaman luapan untuk nilai
 *    panjang). Pemindaian teks lama tetap ada sebagai cadangan
 *    bila berkas rusak.
 *  - Model tabel di memori: baris + kolom, ubah sel, tambah/hapus
 *    baris, cadangan teks, dan mesin kueri SELECT atas banyak
 *    tabel: JOIN (dalam dan kiri), WHERE dengan = != &lt; &gt; LIKE
 *    IS NULL IN dan AND/OR, GROUP BY dengan COUNT/SUM/AVG/MIN/MAX,
 *    ORDER BY, LIMIT.
 *  - Baca SharedPreferences (XML) dan DataStore Preferences
 *    (protobuf .preferences_pb, skema androidx: peta string -&gt;
 *    Value) serta tampilan generik berkas proto lain.
 *
 * BATAS JUJUR: parser halaman membaca berkas .db apa adanya —
 * basis data terenkripsi (SQLCipher) dan berkas WAL yang perubah
 * terbarunya masih di -wal tidak bisa dibaca tanpa mesin SQLite
 * (di HP, SQLiteDatabase Android yang dipakai untuk isi baris dan
 * kueri; parser di sini dipakai untuk daftar tabel/skema dan
 * cadangan bila SQLiteDatabase gagal). Tabel WITHOUT ROWID
 * menampilkan skema saja, isinya tidak dibaca parser ini.
 * DataStore Proto bertipe khusus hanya ditampilkan sebagai
 * tampilan medan generik, bukan nilai bertipe.
 */
public class InspekturBasisData {

    /** Satu tabel di memori. */
    public static class Tabel {
        public String nama = "";
        public List<String> kolom = new ArrayList<String>();
        public List<List<String>> baris = new ArrayList<List<String>>();
        /** Catatan bila isi tidak bisa dibaca penuh (mis. tabel tanpa rowid). */
        public String catatan = "";

        public int indeksKolom(String namaKolom) {
            for (int i = 0; i < kolom.size(); i++) {
                if (kolom.get(i).equalsIgnoreCase(namaKolom)) return i;
            }
            return -1;
        }
    }

    /** Satu entri skema dari sqlite_master. */
    public static class InfoTabel {
        public String nama = "";
        public String jenis = "";
        public String sql = "";
        public int rootpage = 0;
    }

    /** Satu entri kunci-nilai dari SharedPreferences / DataStore. */
    public static class EntriPref {
        public String kunci = "";
        public String jenis = "";
        public String nilai = "";
        public String toString() {
            return kunci + " (" + jenis + ") = " + nilai;
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
     * Pemindaian teks — cadangan bila parser halaman gagal.
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

    // ==================== Parser halaman SQLite ====================

    /**
     * Pembaca format berkas SQLite (https://sqlite.org/fileformat2.html):
     * tajuk 100 bita, pohon-B tabel (halaman dalam 0x05, daun 0x0D),
     * rekaman (varint panjang tajuk + tipe serial), dan halaman
     * luapan untuk muatan panjang.
     */
    static class PembacaDb {
        final byte[] data;
        int ukuranHalaman = 0;
        int cadangan = 0;
        int enkodeTeks = 1;
        boolean sah = false;

        PembacaDb(byte[] data) {
            this.data = data;
            if (data == null || data.length < 100) return;
            String sulap = new String(data, 0, 16);
            if (!"SQLite format 3\0".equals(sulap)) return;
            ukuranHalaman = u16(16);
            if (ukuranHalaman == 1) ukuranHalaman = 65536;
            if (ukuranHalaman < 512 || (ukuranHalaman & (ukuranHalaman - 1)) != 0) return;
            cadangan = data[20] & 0xff;
            enkodeTeks = u32(56);
            if (enkodeTeks < 1 || enkodeTeks > 3) enkodeTeks = 1;
            sah = true;
        }

        int u16(int off) {
            if (off < 0 || off + 2 > data.length) return 0;
            return ((data[off] & 0xff) << 8) | (data[off + 1] & 0xff);
        }

        int u32(int off) {
            if (off < 0 || off + 4 > data.length) return 0;
            return ((data[off] & 0xff) << 24) | ((data[off + 1] & 0xff) << 16)
                    | ((data[off + 2] & 0xff) << 8) | (data[off + 3] & 0xff);
        }

        /** Varint SQLite (big-endian base-128, maks 9 bita); panjang ditulis ke len[0]. */
        long varint(int off, int[] len) {
            long hasil = 0;
            for (int i = 0; i < 9; i++) {
                if (off + i >= data.length) {
                    len[0] = i + 1;
                    return hasil;
                }
                int b = data[off + i] & 0xff;
                if (i == 8) {
                    hasil = (hasil << 8) | b;
                    len[0] = 9;
                    return hasil;
                }
                hasil = (hasil << 7) | (b & 0x7f);
                if ((b & 0x80) == 0) {
                    len[0] = i + 1;
                    return hasil;
                }
            }
            len[0] = 9;
            return hasil;
        }

        long varintDari(byte[] buf, int off, int[] len) {
            long hasil = 0;
            for (int i = 0; i < 9; i++) {
                if (off + i >= buf.length) {
                    len[0] = i + 1;
                    return hasil;
                }
                int b = buf[off + i] & 0xff;
                if (i == 8) {
                    hasil = (hasil << 8) | b;
                    len[0] = 9;
                    return hasil;
                }
                hasil = (hasil << 7) | (b & 0x7f);
                if ((b & 0x80) == 0) {
                    len[0] = i + 1;
                    return hasil;
                }
            }
            len[0] = 9;
            return hasil;
        }

        int awalHalaman(int nomor) {
            return (nomor - 1) * ukuranHalaman;
        }

        /** Kumpulkan semua rekaman (daftar nilai) dari pohon-B tabel berakar di halaman ini. */
        List<List<Object>> bacaBtreeTabel(int halaman) {
            return bacaBtreeTabelRowid(halaman, null);
        }

        /** Sama, plus daftar rowid tiap baris (seurutan dengan rekaman). */
        List<List<Object>> bacaBtreeTabelRowid(int halaman, List<Long> rowidKeluar) {
            List<List<Object>> keluar = new ArrayList<List<Object>>();
            kumpulkanTabel(halaman, keluar, rowidKeluar, 0);
            return keluar;
        }

        private void kumpulkanTabel(int halaman, List<List<Object>> keluar,
                                    List<Long> rowidKeluar, int dalam) {
            if (dalam > 100 || halaman < 1) return;
            int awal = awalHalaman(halaman);
            int tajuk = halaman == 1 ? 100 : 0;
            int off = awal + tajuk;
            if (off >= data.length) return;
            int jenis = data[off] & 0xff;
            int jumlahSel = u16(off + 3);
            if (jenis == 0x0d) {
                int larik = off + 8;
                for (int i = 0; i < jumlahSel; i++) {
                    int ptr = u16(larik + i * 2);
                    if (ptr == 0) ptr = 65536;
                    long[] rowid = new long[1];
                    byte[] muatan = bacaMuatan(awal + ptr, rowid);
                    if (muatan == null) continue;
                    List<Object> rek = uraiRekaman(muatan);
                    if (rek != null) {
                        keluar.add(rek);
                        if (rowidKeluar != null) rowidKeluar.add(Long.valueOf(rowid[0]));
                    }
                }
            } else if (jenis == 0x05) {
                int larik = off + 12;
                for (int i = 0; i < jumlahSel; i++) {
                    int ptr = u16(larik + i * 2);
                    if (ptr == 0) ptr = 65536;
                    int anak = u32(awal + ptr);
                    kumpulkanTabel(anak, keluar, rowidKeluar, dalam + 1);
                }
                int kanan = u32(off + 8);
                kumpulkanTabel(kanan, keluar, rowidKeluar, dalam + 1);
            }
        }

        /** Baca muatan satu sel daun (panjang muatan + rowid + isi, mengikuti luapan). */
        private byte[] bacaMuatan(int posSel, long[] rowidKeluar) {
            if (posSel < 0 || posSel >= data.length) return null;
            int[] len = new int[1];
            long panjang = varint(posSel, len);
            int pos = posSel + len[0];
            long rowid = varint(pos, len);
            if (rowidKeluar != null) rowidKeluar[0] = rowid;
            pos += len[0];
            if (panjang < 0 || panjang > Integer.MAX_VALUE) return null;
            int p = (int) panjang;
            int guna = ukuranHalaman - cadangan;
            int x = guna - 35;
            int lokal;
            if (p <= x) {
                lokal = p;
            } else {
                int m = ((guna - 12) * 32 / 255) - 23;
                int k = m + ((p - m) % (guna - 4));
                lokal = k <= x ? k : m;
            }
            if (pos + lokal > data.length) return null;
            byte[] hasil = new byte[p];
            System.arraycopy(data, pos, hasil, 0, lokal);
            if (lokal < p) {
                int halamanLuapan = u32(pos + lokal);
                int tulis = lokal;
                int lompat = 0;
                while (halamanLuapan != 0 && tulis < p && lompat < 100000) {
                    lompat++;
                    int awalL = awalHalaman(halamanLuapan);
                    // Halaman luapan: 4 bita pertama = nomor halaman
                    // luapan berikut (0 = terakhir), sisanya muatan.
                    if (awalL < 0 || awalL + 4 > data.length) return null;
                    int ambil = Math.min(guna - 4, p - tulis);
                    if (awalL + 4 + ambil > data.length) return null;
                    System.arraycopy(data, awalL + 4, hasil, tulis, ambil);
                    tulis += ambil;
                    halamanLuapan = u32(awalL);
                }
                if (tulis < p) return null;
            }
            return hasil;
        }

        /** Urai satu rekaman menjadi daftar nilai (String/Long/Double/byte[]/null). */
        List<Object> uraiRekaman(byte[] muatan) {
            if (muatan == null || muatan.length == 0) return null;
            int[] len = new int[1];
            long panjangTajuk = varintDari(muatan, 0, len);
            if (panjangTajuk < len[0] || panjangTajuk > muatan.length) return null;
            List<Long> tipe = new ArrayList<Long>();
            int pos = len[0];
            while (pos < panjangTajuk) {
                long t = varintDari(muatan, pos, len);
                tipe.add(Long.valueOf(t));
                pos += len[0];
            }
            List<Object> nilai = new ArrayList<Object>();
            int badan = (int) panjangTajuk;
            for (int i = 0; i < tipe.size(); i++) {
                long t = tipe.get(i).longValue();
                int ukuran = ukuranSerial(t);
                if (badan + ukuran > muatan.length) return null;
                nilai.add(bacaSerial(muatan, badan, t, ukuran));
                badan += ukuran;
            }
            return nilai;
        }

        private int ukuranSerial(long t) {
            if (t == 0) return 0;
            if (t == 1) return 1;
            if (t == 2) return 2;
            if (t == 3) return 3;
            if (t == 4) return 4;
            if (t == 5) return 6;
            if (t == 6) return 8;
            if (t == 7) return 8;
            if (t == 8 || t == 9 || t == 10 || t == 11) return 0;
            if (t >= 12) return (int) ((t - 12) / 2);
            return 0;
        }

        private Object bacaSerial(byte[] muatan, int off, long t, int ukuran) {
            if (t == 0) return null;
            if (t == 8) return Long.valueOf(0);
            if (t == 9) return Long.valueOf(1);
            if (t >= 1 && t <= 6) {
                long v = 0;
                for (int i = 0; i < ukuran; i++) v = (v << 8) | (muatan[off + i] & 0xff);
                if (ukuran > 0 && (muatan[off] & 0x80) != 0) v -= (1L << (ukuran * 8));
                return Long.valueOf(v);
            }
            if (t == 7) {
                long bits = 0;
                for (int i = 0; i < 8; i++) bits = (bits << 8) | (muatan[off + i] & 0xff);
                return Double.valueOf(Double.longBitsToDouble(bits));
            }
            if (t >= 12 && (t % 2 == 0)) {
                byte[] b = new byte[ukuran];
                System.arraycopy(muatan, off, b, 0, ukuran);
                return b;
            }
            if (t >= 13 && (t % 2 == 1)) {
                try {
                    String kode = enkodeTeks == 2 ? "UTF-16LE" : enkodeTeks == 3 ? "UTF-16BE" : "UTF-8";
                    return new String(muatan, off, ukuran, kode);
                } catch (UnsupportedEncodingException e) {
                    return new String(muatan, off, ukuran);
                }
            }
            return null;
        }
    }

    static String nilaiKeTeks(Object v) {
        if (v == null) return "";
        if (v instanceof byte[]) return "<blob " + ((byte[]) v).length + " bita>";
        return String.valueOf(v);
    }

    /**
     * Baca SEMUA tabel dari berkas .db lewat parser halaman
     * (sqlite_master): nama, jenis, SQL CREATE, dan rootpage.
     * Tabel sqlite_master sendiri tidak ikut.
     */
    public static List<InfoTabel> bacaSkemaPenuh(byte[] dbBytes) {
        List<InfoTabel> hasil = new ArrayList<InfoTabel>();
        PembacaDb r = new PembacaDb(dbBytes);
        if (!r.sah) return hasil;
        List<List<Object>> baris;
        try {
            baris = r.bacaBtreeTabel(1);
        } catch (Throwable t) {
            return hasil;
        }
        for (int i = 0; i < baris.size(); i++) {
            List<Object> rek = baris.get(i);
            if (rek.size() < 5) continue;
            String jenis = nilaiKeTeks(rek.get(0));
            String nama = nilaiKeTeks(rek.get(1));
            if (!"table".equalsIgnoreCase(jenis)) continue;
            if ("sqlite_master".equalsIgnoreCase(nama)) continue;
            InfoTabel info = new InfoTabel();
            info.jenis = jenis;
            info.nama = nama;
            if (rek.get(3) instanceof Long) info.rootpage = ((Long) rek.get(3)).intValue();
            info.sql = nilaiKeTeks(rek.get(4));
            hasil.add(info);
        }
        return hasil;
    }

    /** Daftar nama semua tabel di berkas .db (parser halaman). */
    public static List<String> daftarNamaTabel(byte[] dbBytes) {
        List<InfoTabel> info = bacaSkemaPenuh(dbBytes);
        List<String> hasil = new ArrayList<String>();
        for (int i = 0; i < info.size(); i++) hasil.add(info.get(i).nama);
        return hasil;
    }

    /**
     * Baca baris satu tabel lewat parser halaman (batas &lt;= 0 =
     * semua baris). Kolom diambil dari SQL CREATE; nilai NULL
     * jadi "", blob jadi "&lt;blob N bita&gt;".
     */
    public static Tabel bacaBaris(byte[] dbBytes, String namaTabel, int batas) {
        if (dbBytes == null || namaTabel == null) return null;
        List<InfoTabel> info = bacaSkemaPenuh(dbBytes);
        InfoTabel ketemu = null;
        for (int i = 0; i < info.size(); i++) {
            if (info.get(i).nama.equalsIgnoreCase(namaTabel)) ketemu = info.get(i);
        }
        if (ketemu == null) return null;
        Tabel t = tabelDariSkema(ketemu.sql);
        if (t.nama.length() == 0) t.nama = ketemu.nama;
        PembacaDb r = new PembacaDb(dbBytes);
        if (!r.sah || ketemu.rootpage < 1) {
            t.catatan = "Isi baris tidak bisa dibaca dari berkas ini.";
            return t;
        }
        int awal = r.awalHalaman(ketemu.rootpage);
        int tajuk = ketemu.rootpage == 1 ? 100 : 0;
        if (awal + tajuk >= dbBytes.length) {
            t.catatan = "Isi baris tidak bisa dibaca dari berkas ini.";
            return t;
        }
        int jenis = dbBytes[awal + tajuk] & 0xff;
        if (jenis != 0x0d && jenis != 0x05) {
            t.catatan = "Tabel tanpa rowid: skema dibaca, isi baris tidak dibaca parser ini.";
            return t;
        }
        List<List<Object>> baris;
        List<Long> rowids = new ArrayList<Long>();
        try {
            baris = r.bacaBtreeTabelRowid(ketemu.rootpage, rowids);
        } catch (Throwable e) {
            t.catatan = "Isi baris tidak bisa dibaca dari berkas ini.";
            return t;
        }
        int pk = indeksPkInteger(ketemu.sql, t.kolom);
        int jumlah = batas > 0 ? Math.min(batas, baris.size()) : baris.size();
        for (int i = 0; i < jumlah; i++) {
            List<Object> rek = baris.get(i);
            List<String> barisTeks = new ArrayList<String>();
            for (int j = 0; j < t.kolom.size(); j++) {
                Object v = j < rek.size() ? rek.get(j) : null;
                if (v == null && j == pk && i < rowids.size()) {
                    barisTeks.add(String.valueOf(rowids.get(i).longValue()));
                } else {
                    barisTeks.add(nilaiKeTeks(v));
                }
            }
            t.baris.add(barisTeks);
        }
        return t;
    }

    /**
     * Indeks kolom yang merupakan INTEGER PRIMARY KEY di SQL CREATE
     * (kolom itu disimpan NULL di rekaman; nilainya adalah rowid).
     * -1 bila tidak ada.
     */
    static int indeksPkInteger(String sql, List<String> kolom) {
        if (sql == null || kolom == null) return -1;
        Matcher m = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s+INTEGER\\s+PRIMARY\\s+KEY",
                Pattern.CASE_INSENSITIVE).matcher(sql);
        String nama = null;
        if (m.find()) {
            nama = m.group(1);
        } else {
            Matcher m2 = Pattern.compile("PRIMARY\\s+KEY\\s*\\(\\s*\"?([A-Za-z_][A-Za-z0-9_]*)\"?\\s*\\)",
                    Pattern.CASE_INSENSITIVE).matcher(sql);
            if (m2.find()) {
                String calon = m2.group(1);
                Matcher m3 = Pattern.compile(Pattern.quote(calon) + "\\s+INTEGER\\b",
                        Pattern.CASE_INSENSITIVE).matcher(sql);
                if (m3.find()) nama = calon;
            }
        }
        if (nama == null) return -1;
        for (int i = 0; i < kolom.size(); i++) {
            if (kolom.get(i).equalsIgnoreCase(nama)) return i;
        }
        return -1;
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

    // ==================== Mesin kueri SELECT ====================

    private static class GagalKueri extends Exception {
        GagalKueri(String pesan) {
            super(pesan);
        }
    }

    private static class Token {
        static final int KATA = 0;
        static final int TEKS = 1;
        static final int ANGKA = 2;
        static final int SIMBOL = 3;
        int jenis;
        String teks;

        Token(int jenis, String teks) {
            this.jenis = jenis;
            this.teks = teks;
        }
    }

    private static class KolomAcuan {
        String tabel = "";
        String kolom = "";
    }

    private static class ItemPilih {
        boolean agregat = false;
        String fungsi = "";
        boolean bintang = false;
        boolean bintangTabel = false;
        KolomAcuan kolom = null;
        String alias = "";
    }

    private static class Predikat {
        KolomAcuan kiri;
        String op = "";
        boolean kananKolom = false;
        KolomAcuan kanan = null;
        String nilai = null;
        boolean nilaiNull = false;
        List<String> daftarNilai = null;
    }

    private static class Urut {
        boolean posisi = false;
        int nomor = 0;
        KolomAcuan kolom = null;
        boolean turun = false;
    }

    /** Mesin kueri SELECT atas daftar tabel di memori. */
    private static class MesinKueri {
        final List<Tabel> tabels;
        final List<Token> token;
        int pos = 0;

        MesinKueri(List<Tabel> tabels, String sql) throws GagalKueri {
            this.tabels = tabels;
            this.token = tokenisasi(sql);
        }

        Token lihat() {
            return pos < token.size() ? token.get(pos) : null;
        }

        Token ambil() {
            Token t = lihat();
            if (t != null) pos++;
            return t;
        }

        boolean kata(String k) {
            Token t = lihat();
            return t != null && t.jenis == Token.KATA && t.teks.equalsIgnoreCase(k);
        }

        boolean makanKata(String k) {
            if (kata(k)) {
                pos++;
                return true;
            }
            return false;
        }

        void harusKata(String k) throws GagalKueri {
            if (!makanKata(k)) throw new GagalKueri("Perlu kata " + k);
        }

        boolean simbol(String s) {
            Token t = lihat();
            return t != null && t.jenis == Token.SIMBOL && t.teks.equals(s);
        }

        boolean makanSimbol(String s) {
            if (simbol(s)) {
                pos++;
                return true;
            }
            return false;
        }

        void harusSimbol(String s) throws GagalKueri {
            if (!makanSimbol(s)) throw new GagalKueri("Perlu tanda " + s);
        }

        static List<Token> tokenisasi(String sql) throws GagalKueri {
            List<Token> hasil = new ArrayList<Token>();
            if (sql == null) throw new GagalKueri("Kueri kosong");
            int i = 0;
            int n = sql.length();
            while (i < n) {
                char c = sql.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    StringBuilder b = new StringBuilder();
                    i++;
                    boolean tutup = false;
                    while (i < n) {
                        char d = sql.charAt(i);
                        if (d == '\'') {
                            if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                                b.append('\'');
                                i += 2;
                            } else {
                                i++;
                                tutup = true;
                                break;
                            }
                        } else {
                            b.append(d);
                            i++;
                        }
                    }
                    if (!tutup) throw new GagalKueri("Teks tidak ditutup");
                    hasil.add(new Token(Token.TEKS, b.toString()));
                    continue;
                }
                if (c == '"' || c == '`') {
                    int awal = i + 1;
                    int akhir = sql.indexOf(c, awal);
                    if (akhir < 0) throw new GagalKueri("Nama tidak ditutup");
                    hasil.add(new Token(Token.KATA, sql.substring(awal, akhir)));
                    i = akhir + 1;
                    continue;
                }
                if ((c >= '0' && c <= '9') || (c == '.' && i + 1 < n
                        && sql.charAt(i + 1) >= '0' && sql.charAt(i + 1) <= '9')) {
                    int awal = i;
                    boolean titik = false;
                    while (i < n) {
                        char d = sql.charAt(i);
                        if (d >= '0' && d <= '9') {
                            i++;
                        } else if (d == '.' && !titik) {
                            titik = true;
                            i++;
                        } else {
                            break;
                        }
                    }
                    hasil.add(new Token(Token.ANGKA, sql.substring(awal, i)));
                    continue;
                }
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_') {
                    int awal = i;
                    while (i < n) {
                        char d = sql.charAt(i);
                        if ((d >= 'A' && d <= 'Z') || (d >= 'a' && d <= 'z')
                                || (d >= '0' && d <= '9') || d == '_' || d == '$') {
                            i++;
                        } else {
                            break;
                        }
                    }
                    hasil.add(new Token(Token.KATA, sql.substring(awal, i)));
                    continue;
                }
                if (i + 1 < n) {
                    String dua = sql.substring(i, i + 2);
                    if ("!=".equals(dua) || "<>".equals(dua) || ">=".equals(dua)
                            || "<=".equals(dua)) {
                        hasil.add(new Token(Token.SIMBOL, dua));
                        i += 2;
                        continue;
                    }
                }
                if ("(),*=<>.;-".indexOf(c) >= 0) {
                    hasil.add(new Token(Token.SIMBOL, String.valueOf(c)));
                    i++;
                    continue;
                }
                throw new GagalKueri("Karakter tidak dikenal: " + c);
            }
            return hasil;
        }

        KolomAcuan bacaKolom() throws GagalKueri {
            Token t = ambil();
            if (t == null || t.jenis != Token.KATA) throw new GagalKueri("Perlu nama kolom");
            KolomAcuan k = new KolomAcuan();
            if (simbol(".")) {
                pos++;
                Token k2 = ambil();
                if (k2 == null || k2.jenis != Token.KATA) throw new GagalKueri("Perlu nama kolom");
                k.tabel = t.teks;
                k.kolom = k2.teks;
            } else {
                k.kolom = t.teks;
            }
            return k;
        }

        /** Operand nilai: literal teks/angka/NULL, atau acuan kolom. */
        Object bacaOperand(boolean[] adalahKolom, KolomAcuan[] kolomKeluar) throws GagalKueri {
            Token t = lihat();
            if (t == null) throw new GagalKueri("Operand hilang");
            if (t.jenis == Token.TEKS) {
                pos++;
                adalahKolom[0] = false;
                return t.teks;
            }
            if (t.jenis == Token.ANGKA) {
                pos++;
                adalahKolom[0] = false;
                return t.teks;
            }
            if (simbol("-")) {
                pos++;
                Token angka = ambil();
                if (angka == null || angka.jenis != Token.ANGKA) throw new GagalKueri("Angka hilang");
                adalahKolom[0] = false;
                return "-" + angka.teks;
            }
            if (t.jenis == Token.KATA && "NULL".equalsIgnoreCase(t.teks)) {
                pos++;
                adalahKolom[0] = false;
                return null;
            }
            KolomAcuan k = bacaKolom();
            adalahKolom[0] = true;
            kolomKeluar[0] = k;
            return null;
        }

        Predikat bacaPredikat() throws GagalKueri {
            Predikat p = new Predikat();
            p.kiri = bacaKolom();
            if (kata("LIKE")) {
                pos++;
                p.op = "LIKE";
                boolean[] kol = new boolean[1];
                KolomAcuan[] kk = new KolomAcuan[1];
                Object v = bacaOperand(kol, kk);
                if (kol[0]) {
                    p.kananKolom = true;
                    p.kanan = kk[0];
                } else {
                    p.nilai = v == null ? null : String.valueOf(v);
                }
                return p;
            }
            if (kata("IS")) {
                pos++;
                boolean bukan = makanKata("NOT");
                harusKata("NULL");
                p.op = bukan ? "IS NOT NULL" : "IS NULL";
                return p;
            }
            if (kata("IN")) {
                pos++;
                harusSimbol("(");
                p.op = "IN";
                p.daftarNilai = new ArrayList<String>();
                while (true) {
                    boolean[] kol = new boolean[1];
                    KolomAcuan[] kk = new KolomAcuan[1];
                    Object v = bacaOperand(kol, kk);
                    if (kol[0]) throw new GagalKueri("IN hanya menerima nilai");
                    p.daftarNilai.add(v == null ? null : String.valueOf(v));
                    if (makanSimbol(",")) continue;
                    break;
                }
                harusSimbol(")");
                return p;
            }
            Token op = ambil();
            if (op == null || op.jenis != Token.SIMBOL
                    || !("=".equals(op.teks) || "!=".equals(op.teks) || "<>".equals(op.teks)
                    || ">".equals(op.teks) || "<".equals(op.teks) || ">=".equals(op.teks)
                    || "<=".equals(op.teks))) {
                throw new GagalKueri("Operator tidak dikenal");
            }
            p.op = op.teks;
            boolean[] kol = new boolean[1];
            KolomAcuan[] kk = new KolomAcuan[1];
            Object v = bacaOperand(kol, kk);
            if (kol[0]) {
                p.kananKolom = true;
                p.kanan = kk[0];
            } else {
                p.nilai = v == null ? null : String.valueOf(v);
                p.nilaiNull = v == null;
            }
            return p;
        }

        /** WHERE: daftar OR, tiap OR berisi daftar AND. */
        List<List<Predikat>> bacaWhere() throws GagalKueri {
            List<List<Predikat>> hasil = new ArrayList<List<Predikat>>();
            List<Predikat> kini = new ArrayList<Predikat>();
            kini.add(bacaPredikat());
            while (true) {
                if (makanKata("AND")) {
                    kini.add(bacaPredikat());
                } else if (makanKata("OR")) {
                    hasil.add(kini);
                    kini = new ArrayList<Predikat>();
                    kini.add(bacaPredikat());
                } else {
                    break;
                }
            }
            hasil.add(kini);
            return hasil;
        }

        List<List<String>> jalankan() throws GagalKueri {
            harusKata("SELECT");
            List<ItemPilih> pilih = new ArrayList<ItemPilih>();
            if (simbol("*")) {
                pos++;
                ItemPilih it = new ItemPilih();
                it.bintang = true;
                pilih.add(it);
            } else {
                while (true) {
                    pilih.add(bacaItemPilih());
                    if (makanSimbol(",")) continue;
                    break;
                }
            }
            harusKata("FROM");
            List<Tabel> sumber = new ArrayList<Tabel>();
            List<String> alias = new ArrayList<String>();
            List<List<Predikat>> syaratGabung = new ArrayList<List<Predikat>>();
            List<Boolean> gabungKiri = new ArrayList<Boolean>();
            sumber.add(cariTabel());
            alias.add(bacaAlias());
            while (true) {
                boolean kiri = false;
                if (kata("LEFT")) {
                    pos++;
                    makanKata("OUTER");
                    harusKata("JOIN");
                    kiri = true;
                } else if (kata("INNER")) {
                    pos++;
                    harusKata("JOIN");
                } else if (kata("JOIN")) {
                    pos++;
                } else {
                    break;
                }
                sumber.add(cariTabel());
                alias.add(bacaAlias());
                harusKata("ON");
                List<Predikat> syarat = new ArrayList<Predikat>();
                syarat.add(bacaPredikat());
                while (makanKata("AND")) syarat.add(bacaPredikat());
                syaratGabung.add(syarat);
                gabungKiri.add(Boolean.valueOf(kiri));
            }
            List<List<Predikat>> where = null;
            if (makanKata("WHERE")) where = bacaWhere();
            List<KolomAcuan> kelompok = null;
            if (makanKata("GROUP")) {
                harusKata("BY");
                kelompok = new ArrayList<KolomAcuan>();
                while (true) {
                    kelompok.add(bacaKolom());
                    if (makanSimbol(",")) continue;
                    break;
                }
            }
            List<Urut> urut = null;
            if (makanKata("ORDER")) {
                harusKata("BY");
                urut = new ArrayList<Urut>();
                while (true) {
                    Urut u = new Urut();
                    Token t = lihat();
                    if (t != null && t.jenis == Token.ANGKA) {
                        pos++;
                        u.posisi = true;
                        try {
                            u.nomor = Integer.parseInt(t.teks);
                        } catch (Throwable e) {
                            throw new GagalKueri("Nomor urut tidak sah");
                        }
                    } else {
                        u.kolom = bacaKolom();
                    }
                    if (kata("DESC")) {
                        pos++;
                        u.turun = true;
                    } else {
                        makanKata("ASC");
                    }
                    urut.add(u);
                    if (makanSimbol(",")) continue;
                    break;
                }
            }
            int batas = -1;
            if (makanKata("LIMIT")) {
                Token t = ambil();
                if (t == null || t.jenis != Token.ANGKA) throw new GagalKueri("LIMIT perlu angka");
                try {
                    batas = Integer.parseInt(t.teks);
                } catch (Throwable e) {
                    throw new GagalKueri("LIMIT tidak sah");
                }
            }
            makanSimbol(";");
            if (lihat() != null) throw new GagalKueri("Sisa kueri tidak dikenal");
            for (int i = 0; i < pilih.size(); i++) {
                ItemPilih it = pilih.get(i);
                if (!it.bintang && !it.bintangTabel && it.kolom != null) {
                    periksaKolomAda(it.kolom, sumber, alias);
                }
            }
            for (int s = 0; s < syaratGabung.size(); s++) {
                List<Predikat> syarat = syaratGabung.get(s);
                for (int i = 0; i < syarat.size(); i++) periksaPredikat(syarat.get(i), sumber, alias);
            }
            if (where != null) {
                for (int o = 0; o < where.size(); o++) {
                    List<Predikat> dan = where.get(o);
                    for (int i = 0; i < dan.size(); i++) periksaPredikat(dan.get(i), sumber, alias);
                }
            }
            if (kelompok != null) {
                for (int i = 0; i < kelompok.size(); i++) periksaKolomAda(kelompok.get(i), sumber, alias);
            }
            if (urut != null) {
                for (int i = 0; i < urut.size(); i++) {
                    if (urut.get(i).posisi) continue;
                    boolean adalahAlias = false;
                    for (int j = 0; j < pilih.size(); j++) {
                        if (pilih.get(j).alias != null
                                && pilih.get(j).alias.equalsIgnoreCase(urut.get(i).kolom.kolom)) {
                            adalahAlias = true;
                        }
                    }
                    if (!adalahAlias) periksaKolomAda(urut.get(i).kolom, sumber, alias);
                }
            }

            List<Map<String, String>> gabung = gabungkanBaris(sumber, alias,
                    syaratGabung, gabungKiri);
            if (where != null) gabung = saring(gabung, where);
            boolean adaAgregat = false;
            for (int i = 0; i < pilih.size(); i++) {
                if (pilih.get(i).agregat) adaAgregat = true;
            }
            List<ItemPilih> pilihAkhir = perluasBintang(pilih, sumber, alias);
            List<List<String>> hasil = new ArrayList<List<String>>();
            List<Map<String, String>> sumberUrut = new ArrayList<Map<String, String>>();
            if (adaAgregat || kelompok != null) {
                Map<String, List<Map<String, String>>> grup = kelompokkan(gabung, kelompok);
                for (Map.Entry<String, List<Map<String, String>>> e : grup.entrySet()) {
                    List<String> baris = new ArrayList<String>();
                    Map<String, String> sumberBaris = new LinkedHashMap<String, String>();
                    for (int i = 0; i < pilihAkhir.size(); i++) {
                        ItemPilih it = pilihAkhir.get(i);
                        String nilai;
                        if (it.agregat) {
                            nilai = hitungAgregat(it, e.getValue());
                        } else {
                            nilai = nilaiKolom(e.getValue().get(0), it.kolom);
                        }
                        baris.add(nilai == null ? "" : nilai);
                        sumberBaris.put(namaHasil(it), nilai == null ? "" : nilai);
                    }
                    hasil.add(baris);
                    sumberUrut.add(sumberBaris);
                }
            } else {
                for (int i = 0; i < gabung.size(); i++) {
                    Map<String, String> barisGabung = gabung.get(i);
                    List<String> baris = new ArrayList<String>();
                    for (int j = 0; j < pilihAkhir.size(); j++) {
                        String nilai = nilaiKolom(barisGabung, pilihAkhir.get(j).kolom);
                        baris.add(nilai == null ? "" : nilai);
                    }
                    hasil.add(baris);
                    sumberUrut.add(barisGabung);
                }
            }
            if (urut != null) {
                urutkan(hasil, sumberUrut, pilihAkhir, urut);
            }
            if (batas >= 0 && hasil.size() > batas) {
                hasil = new ArrayList<List<String>>(hasil.subList(0, batas));
            }
            return hasil;
        }

        private void periksaPredikat(Predikat p, List<Tabel> sumber, List<String> alias)
                throws GagalKueri {
            periksaKolomAda(p.kiri, sumber, alias);
            if (p.kananKolom) periksaKolomAda(p.kanan, sumber, alias);
        }

        /** Lempar GagalKueri bila kolom tidak ada di tabel sumber (nama/alias diperhitungkan). */
        private void periksaKolomAda(KolomAcuan k, List<Tabel> sumber, List<String> alias)
                throws GagalKueri {
            if (k == null) return;
            if (k.tabel != null && k.tabel.length() > 0) {
                for (int s = 0; s < sumber.size(); s++) {
                    String namaSumber = alias.get(s).length() > 0 ? alias.get(s) : sumber.get(s).nama;
                    if (namaSumber.equalsIgnoreCase(k.tabel)) {
                        if (sumber.get(s).indeksKolom(k.kolom) >= 0) return;
                        throw new GagalKueri("Kolom tidak ada: " + k.tabel + "." + k.kolom);
                    }
                }
                throw new GagalKueri("Tabel tidak ada: " + k.tabel);
            }
            for (int s = 0; s < sumber.size(); s++) {
                if (sumber.get(s).indeksKolom(k.kolom) >= 0) return;
            }
            throw new GagalKueri("Kolom tidak ada: " + k.kolom);
        }

        private Tabel cariTabel() throws GagalKueri {
            Token t = ambil();
            if (t == null || t.jenis != Token.KATA) throw new GagalKueri("Perlu nama tabel");
            for (int i = 0; i < tabels.size(); i++) {
                if (tabels.get(i).nama.equalsIgnoreCase(t.teks)) return tabels.get(i);
            }
            throw new GagalKueri("Tabel tidak dikenal: " + t.teks);
        }

        private String bacaAlias() {
            makanKata("AS");
            Token t = lihat();
            if (t != null && t.jenis == Token.KATA
                    && !("WHERE".equalsIgnoreCase(t.teks) || "JOIN".equalsIgnoreCase(t.teks)
                    || "LEFT".equalsIgnoreCase(t.teks) || "INNER".equalsIgnoreCase(t.teks)
                    || "GROUP".equalsIgnoreCase(t.teks) || "ORDER".equalsIgnoreCase(t.teks)
                    || "LIMIT".equalsIgnoreCase(t.teks) || "ON".equalsIgnoreCase(t.teks))) {
                pos++;
                return t.teks;
            }
            return "";
        }

        private ItemPilih bacaItemPilih() throws GagalKueri {
            ItemPilih it = new ItemPilih();
            Token t = lihat();
            if (t != null && t.jenis == Token.KATA
                    && ("COUNT".equalsIgnoreCase(t.teks) || "SUM".equalsIgnoreCase(t.teks)
                    || "AVG".equalsIgnoreCase(t.teks) || "MIN".equalsIgnoreCase(t.teks)
                    || "MAX".equalsIgnoreCase(t.teks))) {
                pos++;
                it.agregat = true;
                it.fungsi = t.teks.toUpperCase();
                harusSimbol("(");
                if (simbol("*")) {
                    pos++;
                    it.bintang = true;
                } else {
                    it.kolom = bacaKolom();
                }
                harusSimbol(")");
            } else if (t != null && t.jenis == Token.KATA) {
                if (pos + 2 < token.size() && token.get(pos + 1).jenis == Token.SIMBOL
                        && ".".equals(token.get(pos + 1).teks)
                        && token.get(pos + 2).jenis == Token.SIMBOL
                        && "*".equals(token.get(pos + 2).teks)) {
                    it.bintangTabel = true;
                    it.kolom = new KolomAcuan();
                    it.kolom.tabel = t.teks;
                    pos += 3;
                } else {
                    it.kolom = bacaKolom();
                }
            } else if (simbol("*")) {
                pos++;
                it.bintang = true;
            } else {
                throw new GagalKueri("Item pilih tidak dikenal");
            }
            if (makanKata("AS")) {
                Token a = ambil();
                if (a == null || a.jenis != Token.KATA) throw new GagalKueri("Alias hilang");
                it.alias = a.teks;
            }
            return it;
        }

        private List<ItemPilih> perluasBintang(List<ItemPilih> pilih, List<Tabel> sumber,
                                               List<String> alias) {
            List<ItemPilih> hasil = new ArrayList<ItemPilih>();
            for (int i = 0; i < pilih.size(); i++) {
                ItemPilih it = pilih.get(i);
                if (it.bintang && !it.agregat) {
                    for (int s = 0; s < sumber.size(); s++) {
                        Tabel tb = sumber.get(s);
                        for (int k = 0; k < tb.kolom.size(); k++) {
                            ItemPilih baru = new ItemPilih();
                            baru.kolom = new KolomAcuan();
                            baru.kolom.tabel = alias.get(s).length() > 0 ? alias.get(s) : tb.nama;
                            baru.kolom.kolom = tb.kolom.get(k);
                            hasil.add(baru);
                        }
                    }
                } else if (it.bintangTabel) {
                    String nama = it.kolom.tabel;
                    for (int s = 0; s < sumber.size(); s++) {
                        String namaSumber = alias.get(s).length() > 0 ? alias.get(s) : sumber.get(s).nama;
                        if (!namaSumber.equalsIgnoreCase(nama)) continue;
                        Tabel tb = sumber.get(s);
                        for (int k = 0; k < tb.kolom.size(); k++) {
                            ItemPilih baru = new ItemPilih();
                            baru.kolom = new KolomAcuan();
                            baru.kolom.tabel = namaSumber;
                            baru.kolom.kolom = tb.kolom.get(k);
                            hasil.add(baru);
                        }
                    }
                } else {
                    hasil.add(it);
                }
            }
            return hasil;
        }

        private String namaHasil(ItemPilih it) {
            if (it.alias != null && it.alias.length() > 0) return it.alias;
            if (it.agregat) {
                return it.fungsi + "(" + (it.bintang ? "*" : it.kolom.kolom) + ")";
            }
            return it.kolom.kolom;
        }

        private List<Map<String, String>> gabungkanBaris(List<Tabel> sumber, List<String> alias,
                List<List<Predikat>> syaratGabung, List<Boolean> gabungKiri) {
            List<Map<String, String>> kini = new ArrayList<Map<String, String>>();
            Tabel pertama = sumber.get(0);
            String namaPertama = alias.get(0).length() > 0 ? alias.get(0) : pertama.nama;
            for (int i = 0; i < pertama.baris.size(); i++) {
                kini.add(petaBaris(pertama, namaPertama, pertama.baris.get(i)));
            }
            for (int s = 1; s < sumber.size(); s++) {
                Tabel kanan = sumber.get(s);
                String namaKanan = alias.get(s).length() > 0 ? alias.get(s) : kanan.nama;
                List<Predikat> syarat = syaratGabung.get(s - 1);
                boolean kiri = gabungKiri.get(s - 1).booleanValue();
                List<Map<String, String>> berikut = new ArrayList<Map<String, String>>();
                for (int i = 0; i < kini.size(); i++) {
                    Map<String, String> barisKiri = kini.get(i);
                    boolean cocok = false;
                    for (int j = 0; j < kanan.baris.size(); j++) {
                        Map<String, String> gabung = new LinkedHashMap<String, String>();
                        gabung.putAll(barisKiri);
                        gabung.putAll(petaBaris(kanan, namaKanan, kanan.baris.get(j)));
                        if (cocokSemua(gabung, syarat)) {
                            berikut.add(gabung);
                            cocok = true;
                        }
                    }
                    if (!cocok && kiri) {
                        Map<String, String> gabung = new LinkedHashMap<String, String>();
                        gabung.putAll(barisKiri);
                        gabung.putAll(petaBarisKosong(kanan, namaKanan));
                        berikut.add(gabung);
                    }
                }
                kini = berikut;
            }
            return kini;
        }

        private Map<String, String> petaBaris(Tabel t, String namaTabel, List<String> baris) {
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 0; i < t.kolom.size(); i++) {
                String v = i < baris.size() ? baris.get(i) : "";
                m.put(namaTabel + "." + t.kolom.get(i), v);
                if (!m.containsKey(t.kolom.get(i))) m.put(t.kolom.get(i), v);
            }
            return m;
        }

        private Map<String, String> petaBarisKosong(Tabel t, String namaTabel) {
            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 0; i < t.kolom.size(); i++) {
                m.put(namaTabel + "." + t.kolom.get(i), null);
                if (!m.containsKey(t.kolom.get(i))) m.put(t.kolom.get(i), null);
            }
            return m;
        }

        private boolean cocokSemua(Map<String, String> baris, List<Predikat> syarat) {
            for (int i = 0; i < syarat.size(); i++) {
                if (!cocokPredikat(baris, syarat.get(i))) return false;
            }
            return true;
        }

        private List<Map<String, String>> saring(List<Map<String, String>> baris,
                                                 List<List<Predikat>> where) {
            List<Map<String, String>> hasil = new ArrayList<Map<String, String>>();
            for (int i = 0; i < baris.size(); i++) {
                Map<String, String> b = baris.get(i);
                boolean lolos = false;
                for (int o = 0; o < where.size(); o++) {
                    List<Predikat> dan = where.get(o);
                    boolean semua = true;
                    for (int j = 0; j < dan.size(); j++) {
                        if (!cocokPredikat(b, dan.get(j))) {
                            semua = false;
                            break;
                        }
                    }
                    if (semua) {
                        lolos = true;
                        break;
                    }
                }
                if (lolos) hasil.add(b);
            }
            return hasil;
        }

        private String nilaiKolom(Map<String, String> baris, KolomAcuan k) {
            if (baris == null || k == null) return null;
            if (k.tabel != null && k.tabel.length() > 0) {
                String v = baris.get(k.tabel + "." + k.kolom);
                if (v != null) return v;
                for (Map.Entry<String, String> e : baris.entrySet()) {
                    if (e.getKey().endsWith("." + k.kolom)
                            && e.getKey().substring(0, e.getKey().length() - k.kolom.length() - 1)
                            .equalsIgnoreCase(k.tabel)) return e.getValue();
                }
                return null;
            }
            String v = baris.get(k.kolom);
            if (v != null) return v;
            for (Map.Entry<String, String> e : baris.entrySet()) {
                if (e.getKey().endsWith("." + k.kolom)) return e.getValue();
            }
            return null;
        }

        private boolean cocokPredikat(Map<String, String> baris, Predikat p) {
            String kiri = nilaiKolom(baris, p.kiri);
            if ("IS NULL".equals(p.op)) return kiri == null || kiri.length() == 0;
            if ("IS NOT NULL".equals(p.op)) return kiri != null && kiri.length() > 0;
            String kanan;
            if (p.kananKolom) {
                kanan = nilaiKolom(baris, p.kanan);
            } else {
                kanan = p.nilai;
            }
            if ("LIKE".equals(p.op)) {
                if (kiri == null || kanan == null) return false;
                return cocokLike(kiri, kanan);
            }
            if ("IN".equals(p.op)) {
                if (kiri == null) return false;
                for (int i = 0; i < p.daftarNilai.size(); i++) {
                    String v = p.daftarNilai.get(i);
                    if (v != null && bandingNilai(kiri, v) == 0) return true;
                }
                return false;
            }
            if (kiri == null || kanan == null) return false;
            int banding = bandingNilai(kiri, kanan);
            if ("=".equals(p.op)) return banding == 0;
            if ("!=".equals(p.op) || "<>".equals(p.op)) return banding != 0;
            if (">".equals(p.op)) return banding > 0;
            if ("<".equals(p.op)) return banding < 0;
            if (">=".equals(p.op)) return banding >= 0;
            if ("<=".equals(p.op)) return banding <= 0;
            return false;
        }

        private Map<String, List<Map<String, String>>> kelompokkan(
                List<Map<String, String>> baris, List<KolomAcuan> kelompok) {
            Map<String, List<Map<String, String>>> grup = new LinkedHashMap<String, List<Map<String, String>>>();
            if (kelompok == null) {
                grup.put("", new ArrayList<Map<String, String>>(baris));
                return grup;
            }
            for (int i = 0; i < baris.size(); i++) {
                Map<String, String> b = baris.get(i);
                StringBuilder kunci = new StringBuilder();
                for (int j = 0; j < kelompok.size(); j++) {
                    if (j > 0) kunci.append("\u0001");
                    String v = nilaiKolom(b, kelompok.get(j));
                    kunci.append(v == null ? "" : v);
                }
                String k = kunci.toString();
                List<Map<String, String>> daftar = grup.get(k);
                if (daftar == null) {
                    daftar = new ArrayList<Map<String, String>>();
                    grup.put(k, daftar);
                }
                daftar.add(b);
            }
            return grup;
        }

        private String hitungAgregat(ItemPilih it, List<Map<String, String>> grup) {
            if ("COUNT".equals(it.fungsi)) {
                if (it.bintang) return String.valueOf(grup.size());
                int hitung = 0;
                for (int i = 0; i < grup.size(); i++) {
                    String v = nilaiKolom(grup.get(i), it.kolom);
                    if (v != null && v.length() > 0) hitung++;
                }
                return String.valueOf(hitung);
            }
            if ("SUM".equals(it.fungsi) || "AVG".equals(it.fungsi)) {
                double jumlah = 0;
                int hitung = 0;
                for (int i = 0; i < grup.size(); i++) {
                    String v = nilaiKolom(grup.get(i), it.kolom);
                    if (v == null || v.length() == 0) continue;
                    try {
                        jumlah += Double.parseDouble(v);
                        hitung++;
                    } catch (Throwable ignored) {
                    }
                }
                if (hitung == 0) return "";
                if ("SUM".equals(it.fungsi)) return formatAngka(jumlah);
                return formatAngka(jumlah / hitung);
            }
            if ("MIN".equals(it.fungsi) || "MAX".equals(it.fungsi)) {
                String terbaik = null;
                for (int i = 0; i < grup.size(); i++) {
                    String v = nilaiKolom(grup.get(i), it.kolom);
                    if (v == null || v.length() == 0) continue;
                    if (terbaik == null) {
                        terbaik = v;
                    } else {
                        int banding = bandingNilai(v, terbaik);
                        if ("MIN".equals(it.fungsi) && banding < 0) terbaik = v;
                        if ("MAX".equals(it.fungsi) && banding > 0) terbaik = v;
                    }
                }
                return terbaik == null ? "" : terbaik;
            }
            return "";
        }

        private void urutkan(List<List<String>> hasil, List<Map<String, String>> sumber,
                             List<ItemPilih> pilih, List<Urut> urut) {
            final List<List<String>> h = hasil;
            final List<Map<String, String>> s = sumber;
            final List<ItemPilih> p = pilih;
            final List<Urut> u = urut;
            List<Integer> indeks = new ArrayList<Integer>();
            for (int i = 0; i < h.size(); i++) indeks.add(Integer.valueOf(i));
            java.util.Collections.sort(indeks, new java.util.Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    for (int k = 0; k < u.size(); k++) {
                        Urut ur = u.get(k);
                        String va = kunciUrut(h.get(a.intValue()), s.get(a.intValue()), p, ur);
                        String vb = kunciUrut(h.get(b.intValue()), s.get(b.intValue()), p, ur);
                        int banding = bandingNilai(va == null ? "" : va, vb == null ? "" : vb);
                        if (banding != 0) return ur.turun ? -banding : banding;
                    }
                    return 0;
                }
            });
            List<List<String>> hasilUrut = new ArrayList<List<String>>();
            List<Map<String, String>> sumberUrut = new ArrayList<Map<String, String>>();
            for (int i = 0; i < indeks.size(); i++) {
                hasilUrut.add(h.get(indeks.get(i).intValue()));
                sumberUrut.add(s.get(indeks.get(i).intValue()));
            }
            hasil.clear();
            hasil.addAll(hasilUrut);
            sumber.clear();
            sumber.addAll(sumberUrut);
        }

        private String kunciUrut(List<String> baris, Map<String, String> sumber,
                                 List<ItemPilih> pilih, Urut ur) {
            if (ur.posisi) {
                int idx = ur.nomor - 1;
                if (idx >= 0 && idx < baris.size()) return baris.get(idx);
                return "";
            }
            for (int i = 0; i < pilih.size(); i++) {
                ItemPilih it = pilih.get(i);
                if (it.alias != null && it.alias.equalsIgnoreCase(ur.kolom.kolom)) {
                    return baris.get(i);
                }
                if (it.kolom != null && it.kolom.kolom.equalsIgnoreCase(ur.kolom.kolom)
                        && (ur.kolom.tabel == null || ur.kolom.tabel.length() == 0
                        || (it.kolom.tabel != null && it.kolom.tabel.equalsIgnoreCase(ur.kolom.tabel)))) {
                    return baris.get(i);
                }
            }
            return nilaiKolom(sumber, ur.kolom);
        }
    }

    static int bandingNilai(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        Double da = keAngka(a);
        Double db = keAngka(b);
        if (da != null && db != null) return da.compareTo(db);
        return a.compareTo(b);
    }

    static Double keAngka(String s) {
        if (s == null) return null;
        try {
            return Double.valueOf(s.trim());
        } catch (Throwable t) {
            return null;
        }
    }

    static String formatAngka(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 9007199254740992.0) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /** Cocokkan teks dengan pola LIKE (% = bebas, _ = satu huruf, tanpa peka huruf). */
    static boolean cocokLike(String teks, String pola) {
        StringBuilder b = new StringBuilder();
        b.append("(?i)");
        for (int i = 0; i < pola.length(); i++) {
            char c = pola.charAt(i);
            if (c == '%') {
                b.append(".*");
            } else if (c == '_') {
                b.append(".");
            } else {
                b.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(b.toString(), Pattern.DOTALL).matcher(teks).matches();
    }

    /**
     * Kueri SELECT atas banyak tabel: JOIN, WHERE (AND/OR, LIKE,
     * IS NULL, IN), GROUP BY + agregat, ORDER BY, LIMIT.
     * Mengembalikan baris hasil (nilai teks) atau null bila kueri
     * tidak dikenali / tabel tidak ada.
     */
    public static List<List<String>> jalankanKueri(List<Tabel> tabels, String sql) {
        if (tabels == null || sql == null) return null;
        try {
            return new MesinKueri(tabels, sql).jalankan();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Kueri SELECT atas satu tabel (kompatibel dengan pemakaian
     * lama: "SELECT a, b FROM tabel WHERE c = 'x'").
     */
    public static List<List<String>> jalankanKueri(Tabel t, String sql) {
        if (t == null || sql == null) return null;
        List<Tabel> daftar = new ArrayList<Tabel>();
        daftar.add(t);
        return jalankanKueri(daftar, sql);
    }

    // ==================== SharedPreferences (XML) ====================

    /** Lepas escape entitas XML (&amp; &lt; &gt; &quot; &apos; &#...;). */
    static String lepasXml(String s) {
        if (s == null) return "";
        String hasil = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
        Matcher m = Pattern.compile("&#(x[0-9A-Fa-f]+|\\d+);").matcher(hasil);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String angka = m.group(1);
            try {
                int kode = angka.startsWith("x") || angka.startsWith("X")
                        ? Integer.parseInt(angka.substring(1), 16)
                        : Integer.parseInt(angka);
                m.appendReplacement(b, Matcher.quoteReplacement(String.valueOf((char) kode)));
            } catch (Throwable t) {
                m.appendReplacement(b, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String atributXml(String tag, String nama) {
        Matcher m = Pattern.compile(Pattern.quote(nama) + "\\s*=\\s*\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Baca berkas SharedPreferences XML menjadi daftar entri
     * kunci-jenis-nilai (string, int, long, float, boolean, set).
     */
    public static List<EntriPref> bacaSharedPrefs(String xml) {
        List<EntriPref> hasil = new ArrayList<EntriPref>();
        if (xml == null) return hasil;
        Matcher mSet = Pattern.compile("<set\\s+([^>]*)>(.*?)</set>", Pattern.DOTALL).matcher(xml);
        while (mSet.find()) {
            String nama = atributXml(mSet.group(1), "name");
            if (nama == null) continue;
            List<String> anggota = new ArrayList<String>();
            Matcher mAng = Pattern.compile("<string>(.*?)</string>", Pattern.DOTALL)
                    .matcher(mSet.group(2));
            while (mAng.find()) anggota.add(lepasXml(mAng.group(1)));
            EntriPref e = new EntriPref();
            e.kunci = lepasXml(nama);
            e.jenis = "set";
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < anggota.size(); i++) {
                if (i > 0) b.append(", ");
                b.append(anggota.get(i));
            }
            e.nilai = b.toString();
            hasil.add(e);
        }
        String tanpaSet = mSet.reset(xml).replaceAll("");
        Matcher mStr = Pattern.compile("<string\\s+([^>]*)>(.*?)</string>", Pattern.DOTALL)
                .matcher(tanpaSet);
        while (mStr.find()) {
            String nama = atributXml(mStr.group(1), "name");
            if (nama == null) continue;
            EntriPref e = new EntriPref();
            e.kunci = lepasXml(nama);
            e.jenis = "string";
            e.nilai = lepasXml(mStr.group(2));
            hasil.add(e);
        }
        Matcher mStrKosong = Pattern.compile("<string\\s+([^>]*)/>").matcher(tanpaSet);
        while (mStrKosong.find()) {
            String nama = atributXml(mStrKosong.group(1), "name");
            if (nama == null) continue;
            EntriPref e = new EntriPref();
            e.kunci = lepasXml(nama);
            e.jenis = "string";
            e.nilai = "";
            hasil.add(e);
        }
        String[] jenisSederhana = {"int", "long", "float", "boolean"};
        for (int j = 0; j < jenisSederhana.length; j++) {
            String jenis = jenisSederhana[j];
            Matcher m = Pattern.compile("<" + jenis + "\\s+([^>]*)/>").matcher(tanpaSet);
            while (m.find()) {
                String nama = atributXml(m.group(1), "name");
                String nilai = atributXml(m.group(1), "value");
                if (nama == null || nilai == null) continue;
                EntriPref e = new EntriPref();
                e.kunci = lepasXml(nama);
                e.jenis = jenis;
                e.nilai = lepasXml(nilai);
                hasil.add(e);
            }
        }
        return hasil;
    }

    // ==================== DataStore Preferences (protobuf) ====================

    /**
     * Pembaca protobuf minimal (varint, fixed32/64, length-delimited)
     * untuk berkas .preferences_pb dan proto lain.
     */
    static class PembacaProto {
        final byte[] data;
        final int akhir;
        int pos;

        PembacaProto(byte[] data, int awal, int akhir) {
            this.data = data;
            this.pos = awal;
            this.akhir = Math.min(akhir, data.length);
        }

        boolean habis() {
            return pos >= akhir;
        }

        long bacaVarint() {
            long hasil = 0;
            int geser = 0;
            while (pos < akhir && geser < 70) {
                int b = data[pos++] & 0xff;
                hasil |= (long) (b & 0x7f) << geser;
                if ((b & 0x80) == 0) return hasil;
                geser += 7;
            }
            return hasil;
        }

        /** Baca tag; kembalikan nomor medan, jenis kawat ditulis ke kawat[0]. */
        int bacaTag(int[] kawat) {
            if (pos >= akhir) return -1;
            long tag = bacaVarint();
            kawat[0] = (int) (tag & 7);
            return (int) (tag >> 3);
        }

        byte[] bacaBita() {
            long n = bacaVarint();
            if (n < 0 || n > akhir - pos) return null;
            byte[] b = new byte[(int) n];
            System.arraycopy(data, pos, b, 0, (int) n);
            pos += (int) n;
            return b;
        }

        long bacaFixed64() {
            if (pos + 8 > akhir) return 0;
            long v = 0;
            for (int i = 0; i < 8; i++) v |= (long) (data[pos++] & 0xff) << (i * 8);
            return v;
        }

        int bacaFixed32() {
            if (pos + 4 > akhir) return 0;
            int v = 0;
            for (int i = 0; i < 4; i++) v |= (data[pos++] & 0xff) << (i * 8);
            return v;
        }

        boolean lewati(int kawat) {
            if (kawat == 0) {
                bacaVarint();
                return true;
            }
            if (kawat == 1) {
                if (pos + 8 > akhir) return false;
                pos += 8;
                return true;
            }
            if (kawat == 2) {
                byte[] b = bacaBita();
                return b != null;
            }
            if (kawat == 5) {
                if (pos + 4 > akhir) return false;
                pos += 4;
                return true;
            }
            return false;
        }
    }

    static String bitaKeHex(byte[] b) {
        if (b == null) return "";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff;
            if (v < 16) s.append('0');
            s.append(Integer.toHexString(v));
        }
        return s.toString();
    }

    static boolean teksTercetak(byte[] b) {
        if (b == null || b.length == 0) return false;
        for (int i = 0; i < b.length; i++) {
            int c = b[i] & 0xff;
            if (c < 9 || (c > 13 && c < 32) || c == 127) return false;
        }
        return true;
    }

    /**
     * Baca berkas DataStore Preferences (.preferences_pb) menjadi
     * daftar entri. Skema androidx datastore-preferences-proto:
     * Preferences { map&lt;string, Value&gt; preferences = 1; }
     * Value { bool boolean = 1; float float = 2; int32 integer = 3;
     * int64 long = 4; string string = 5; StringSet string_set = 6;
     * double double = 7; bytes bytes = 8; }
     * StringSet { repeated string strings = 1; }
     */
    public static List<EntriPref> bacaDataStore(byte[] data) {
        List<EntriPref> hasil = new ArrayList<EntriPref>();
        if (data == null || data.length == 0) return hasil;
        PembacaProto r = new PembacaProto(data, 0, data.length);
        int[] kawat = new int[1];
        while (!r.habis()) {
            int medan = r.bacaTag(kawat);
            if (medan < 0) break;
            if (medan == 1 && kawat[0] == 2) {
                byte[] entri = r.bacaBita();
                if (entri == null) break;
                EntriPref e = uraiEntriDataStore(entri);
                if (e != null) hasil.add(e);
            } else if (!r.lewati(kawat[0])) {
                break;
            }
        }
        return hasil;
    }

    private static EntriPref uraiEntriDataStore(byte[] entri) {
        PembacaProto r = new PembacaProto(entri, 0, entri.length);
        int[] kawat = new int[1];
        String kunci = null;
        byte[] nilaiBita = null;
        while (!r.habis()) {
            int medan = r.bacaTag(kawat);
            if (medan < 0) break;
            if (medan == 1 && kawat[0] == 2) {
                byte[] b = r.bacaBita();
                if (b == null) break;
                try {
                    kunci = new String(b, "UTF-8");
                } catch (Throwable t) {
                    kunci = new String(b);
                }
            } else if (medan == 2 && kawat[0] == 2) {
                nilaiBita = r.bacaBita();
                if (nilaiBita == null) break;
            } else if (!r.lewati(kawat[0])) {
                break;
            }
        }
        if (kunci == null || nilaiBita == null) return null;
        return uraiNilaiDataStore(kunci, nilaiBita);
    }

    private static EntriPref uraiNilaiDataStore(String kunci, byte[] nilaiBita) {
        PembacaProto r = new PembacaProto(nilaiBita, 0, nilaiBita.length);
        int[] kawat = new int[1];
        EntriPref e = new EntriPref();
        e.kunci = kunci;
        while (!r.habis()) {
            int medan = r.bacaTag(kawat);
            if (medan < 0) break;
            if (medan == 1 && kawat[0] == 0) {
                e.jenis = "boolean";
                e.nilai = r.bacaVarint() != 0 ? "true" : "false";
            } else if (medan == 2 && kawat[0] == 5) {
                e.jenis = "float";
                e.nilai = String.valueOf(Float.intBitsToFloat(r.bacaFixed32()));
            } else if (medan == 3 && kawat[0] == 0) {
                e.jenis = "integer";
                e.nilai = String.valueOf((int) r.bacaVarint());
            } else if (medan == 4 && kawat[0] == 0) {
                e.jenis = "long";
                e.nilai = String.valueOf(r.bacaVarint());
            } else if (medan == 5 && kawat[0] == 2) {
                byte[] b = r.bacaBita();
                if (b == null) break;
                e.jenis = "string";
                try {
                    e.nilai = new String(b, "UTF-8");
                } catch (Throwable t) {
                    e.nilai = new String(b);
                }
            } else if (medan == 6 && kawat[0] == 2) {
                byte[] b = r.bacaBita();
                if (b == null) break;
                e.jenis = "string_set";
                e.nilai = bacaStringSet(b);
            } else if (medan == 7 && kawat[0] == 1) {
                e.jenis = "double";
                e.nilai = String.valueOf(Double.longBitsToDouble(r.bacaFixed64()));
            } else if (medan == 8 && kawat[0] == 2) {
                byte[] b = r.bacaBita();
                if (b == null) break;
                e.jenis = "bytes";
                e.nilai = bitaKeHex(b);
            } else if (!r.lewati(kawat[0])) {
                break;
            }
        }
        if (e.jenis == null || e.jenis.length() == 0) return null;
        return e;
    }

    private static String bacaStringSet(byte[] data) {
        PembacaProto r = new PembacaProto(data, 0, data.length);
        int[] kawat = new int[1];
        StringBuilder b = new StringBuilder();
        int hitung = 0;
        while (!r.habis()) {
            int medan = r.bacaTag(kawat);
            if (medan < 0) break;
            if (medan == 1 && kawat[0] == 2) {
                byte[] s = r.bacaBita();
                if (s == null) break;
                if (hitung > 0) b.append(", ");
                try {
                    b.append(new String(s, "UTF-8"));
                } catch (Throwable t) {
                    b.append(new String(s));
                }
                hitung++;
            } else if (!r.lewati(kawat[0])) {
                break;
            }
        }
        return b.toString();
    }

    /** Coba uraikan bita sebagai pesan protobuf (semua tag sah dan habis tepat). */
    static boolean cobaProto(byte[] data) {
        if (data == null || data.length < 2) return false;
        PembacaProto r = new PembacaProto(data, 0, data.length);
        int[] kawat = new int[1];
        int hitung = 0;
        while (!r.habis()) {
            int awal = r.pos;
            int medan = r.bacaTag(kawat);
            if (medan <= 0 || medan > 100000) return false;
            if (kawat[0] != 0 && kawat[0] != 1 && kawat[0] != 2 && kawat[0] != 5) return false;
            if (!r.lewati(kawat[0])) return false;
            if (r.pos == awal) return false;
            hitung++;
            if (hitung > 10000) return false;
        }
        return hitung > 0;
    }

    /**
     * Tampilan generik berkas protobuf (mis. DataStore Proto
     * bertipe khusus): satu baris per medan, dengan indentasi
     * untuk pesan bersarang. Nilai yang bukan pesan tampil apa
     * adanya (angka, teks, atau heksadesimal).
     */
    public static List<String> dumpProto(byte[] data) {
        List<String> hasil = new ArrayList<String>();
        dumpProtoKe(data, 0, hasil, 0);
        return hasil;
    }

    private static void dumpProtoKe(byte[] data, int dalam, List<String> hasil, int hitung) {
        if (data == null || dalam > 3 || hitung > 200) return;
        PembacaProto r = new PembacaProto(data, 0, data.length);
        int[] kawat = new int[1];
        String menjorok = dalam == 0 ? "" : dalam == 1 ? "  " : dalam == 2 ? "    " : "      ";
        while (!r.habis() && hasil.size() < 200) {
            int medan = r.bacaTag(kawat);
            if (medan < 0) break;
            if (kawat[0] == 0) {
                hasil.add(menjorok + "medan " + medan + " (angka): " + r.bacaVarint());
            } else if (kawat[0] == 1) {
                hasil.add(menjorok + "medan " + medan + " (64-bit): "
                        + Long.toHexString(r.bacaFixed64()));
            } else if (kawat[0] == 5) {
                hasil.add(menjorok + "medan " + medan + " (32-bit): "
                        + Integer.toHexString(r.bacaFixed32()));
            } else if (kawat[0] == 2) {
                byte[] b = r.bacaBita();
                if (b == null) break;
                if (cobaProto(b)) {
                    hasil.add(menjorok + "medan " + medan + " (pesan):");
                    dumpProtoKe(b, dalam + 1, hasil, hasil.size());
                } else if (teksTercetak(b)) {
                    String teks;
                    try {
                        teks = new String(b, "UTF-8");
                    } catch (Throwable t) {
                        teks = new String(b);
                    }
                    hasil.add(menjorok + "medan " + medan + " (teks): " + teks);
                } else {
                    String hex = bitaKeHex(b);
                    if (hex.length() > 64) hex = hex.substring(0, 64) + "...";
                    hasil.add(menjorok + "medan " + medan + " (bita): " + hex);
                }
            } else {
                break;
            }
        }
    }

    /** Jenis berkas penyimpanan dari namanya: "db", "shared_prefs", "datastore", "proto", atau "". */
    public static String jenisBerkasPenyimpanan(String nama) {
        if (nama == null) return "";
        String kecil = nama.toLowerCase();
        if (kecil.endsWith(".db") || kecil.endsWith(".sqlite") || kecil.endsWith(".sqlite3")) {
            return "db";
        }
        if (kecil.endsWith(".preferences_pb")) return "datastore";
        if (kecil.endsWith(".pb")) return "proto";
        if (kecil.endsWith(".xml")) return "shared_prefs";
        return "";
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
