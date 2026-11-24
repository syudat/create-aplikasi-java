package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transpiler Lintas-Paradigma Offline (fitur 6 dari daftar Gemini, 5.7)
 * — mengonversi kode sumber antarbahasa secara luring di perangkat:
 * kelas Java menjadi Kotlin idiomatik, dan tata letak XML tradisional
 * menjadi fungsi antarmuka Jetpack Compose.
 *
 * Kelas murni tanpa Android, bisa diuji di JVM. Aturan berbasis pola
 * baris-per-baris yang aman: komentar // dan blok / * ... * / selalu
 * dipertahankan, dan bila sebuah baris tidak cocok dengan pola apa pun,
 * baris itu dibiarkan apa adanya (dikurangi titik koma akhir dan
 * penyesuaian System.out/new yang aman) supaya tidak ada kode yang
 * rusak oleh konversi.
 *
 * Status pratinjau (kelas Status) dipakai Activity: tombol "Terapkan ke
 * Berkas" hanya boleh aktif sesudah pratinjau ditampilkan, supaya
 * pengguna selalu memeriksa hasil sebelum berkas tujuan ditulis.
 */
public class Transpiler {

    // ------------------------------------------------------------ status

    /** Status pratinjau -> terapan: terapkan hanya boleh sesudah pratinjau. */
    public static class Status {
        private boolean pratinjau;
        private String hasil;

        /** Apakah tombol Terapkan boleh dipakai (pratinjau sudah tampil). */
        public boolean bolehTerapkan() {
            return pratinjau && hasil != null;
        }

        /** Catat bahwa pratinjau hasil ini sudah ditampilkan ke pengguna. */
        public void tandaiPratinjau(String hasilPratinjau) {
            pratinjau = true;
            hasil = hasilPratinjau;
        }

        /** Hasil pratinjau terakhir; null bila belum ada. */
        public String hasil() {
            return hasil;
        }

        /** Awal lagi: berkas atau arah berubah, pratinjau lama tidak berlaku. */
        public void reset() {
            pratinjau = false;
            hasil = null;
        }
    }

    /** Nama berkas tujuan dari nama sumber: .java/.xml -> .kt (sumber tidak disentuh). */
    public static String namaTujuan(String namaSumber) {
        if (namaSumber == null) return "Hasil.kt";
        String n = namaSumber.trim();
        if (n.toLowerCase().endsWith(".java")) return n.substring(0, n.length() - 5) + ".kt";
        if (n.toLowerCase().endsWith(".xml")) return n.substring(0, n.length() - 4) + ".kt";
        return n + ".kt";
    }

    /** Arah konversi dari nama berkas: "java", "xml", atau "" bila tidak dikenal. */
    public static String arahUntuk(String namaBerkas) {
        if (namaBerkas == null) return "";
        String n = namaBerkas.trim().toLowerCase();
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".xml")) return "xml";
        return "";
    }

    // ------------------------------------------------------------ Java -> Kotlin

    private static final Pattern POLA_KELAS = Pattern.compile(
            "^(?:(?:public|final|abstract)\\s+)*class\\s+(\\w+)"
            + "(?:\\s+extends\\s+(\\w+))?"
            + "(?:\\s+implements\\s+([\\w\\s,]+))?\\s*\\{\\s*$");
    private static final Pattern POLA_METODE = Pattern.compile(
            "^(public|private|protected)?\\s*(static\\s+)?(final\\s+)?"
            + "([\\w<>\\[\\], ?]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(\\{?)\\s*$");
    private static final Pattern POLA_FIELD_DASAR = Pattern.compile(
            "^(public|private|protected)?\\s*(static\\s+)?(final\\s+)?"
            + "(String|int|boolean|double|long|float|char|byte|short)"
            + "\\s+(\\w+)\\s*(=\\s*(.+?))?;\\s*$");
    private static final Pattern POLA_FIELD_UMUM = Pattern.compile(
            "^(public|private|protected)?\\s*(static\\s+)?(final\\s+)?"
            + "([A-Z]\\w*(?:<[^;]+>)?)\\s+(\\w+)\\s*(=\\s*(.+?))?;\\s*$");
    private static final Pattern POLA_FOR = Pattern.compile(
            "^for\\s*\\(\\s*int\\s+(\\w+)\\s*=\\s*(.+?)\\s*;\\s*\\1\\s*(<=|<)\\s*(.+?)"
            + "\\s*;\\s*\\1\\+\\+\\s*\\)\\s*\\{\\s*$");
    private static final Pattern POLA_FOR_EACH = Pattern.compile(
            "^for\\s*\\(\\s*([\\w<>\\[\\], ?]+?)\\s+(\\w+)\\s*:\\s*(.+)\\)\\s*\\{\\s*$");
    private static final Pattern POLA_KONSTRUKTOR = Pattern.compile(
            "^(public|private|protected)?\\s*(\\w+)\\s*\\(([^)]*)\\)\\s*(\\{?)\\s*$");

    /** Kata kunci Java yang tidak boleh dibaca sebagai tipe/nama dalam pola. */
    private static final String[] KATA_KUNCI = {
        "return", "if", "else", "for", "while", "new", "this", "super",
        "throw", "switch", "case", "catch", "synchronized", "do", "try"
    };

    private static boolean adalahKataKunci(String kata) {
        if (kata == null) return false;
        for (int i = 0; i < KATA_KUNCI.length; i++) {
            if (KATA_KUNCI[i].equals(kata)) return true;
        }
        return false;
    }

    /** Keadaan selama satu kali konversi berkas Java. */
    private static class Keadaan {
        String namaKelas;
        boolean overrideBerikutnya;
    }

    /** Mengonversi seluruh isi berkas Java menjadi Kotlin. Null -> "". */
    public static String javaKeKotlin(String java) {
        if (java == null) return "";
        String[] baris = java.split("\n", -1);
        List<String> keluar = new ArrayList<String>();
        Keadaan k = new Keadaan();
        boolean dalamKomentar = false;
        for (int i = 0; i < baris.length; i++) {
            String barisAsli = baris[i];
            String t = barisAsli.trim();
            if (dalamKomentar) {
                keluar.add(barisAsli);
                if (t.contains("*/")) dalamKomentar = false;
                continue;
            }
            if (t.startsWith("/*")) {
                keluar.add(barisAsli);
                if (!t.contains("*/")) dalamKomentar = true;
                continue;
            }
            if (t.startsWith("//") || t.length() == 0) {
                keluar.add(barisAsli);
                continue;
            }
            int idxKomentar = cariKomentar(barisAsli);
            String kode = idxKomentar >= 0 ? barisAsli.substring(0, idxKomentar) : barisAsli;
            String komentar = idxKomentar >= 0 ? barisAsli.substring(idxKomentar) : "";
            String hasil = ubahBarisJava(kode.trim(), k);
            if (hasil == null) continue; // baris @Override: dihapus, ditandai ke metode berikut
            String indent = indentasi(barisAsli);
            keluar.add(indent + hasil + komentar);
        }
        return gabung(keluar);
    }

    /** Indeks "//" di luar literal string/char; -1 bila tidak ada. */
    static int cariKomentar(String baris) {
        boolean dalamString = false;
        boolean dalamChar = false;
        for (int i = 0; i + 1 < baris.length(); i++) {
            char c = baris.charAt(i);
            if (dalamString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') dalamString = false;
                continue;
            }
            if (dalamChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') dalamChar = false;
                continue;
            }
            if (c == '"') { dalamString = true; continue; }
            if (c == '\'') { dalamChar = true; continue; }
            if (c == '/' && baris.charAt(i + 1) == '/') return i;
        }
        return -1;
    }

    static String indentasi(String baris) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < baris.length(); i++) {
            char c = baris.charAt(i);
            if (c == ' ' || c == '\t') b.append(c);
            else break;
        }
        return b.toString();
    }

    static String gabung(List<String> baris) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < baris.size(); i++) {
            if (i > 0) b.append("\n");
            b.append(baris.get(i));
        }
        return b.toString();
    }

    /** Satu baris kode Java (tanpa indent/komentar) -> Kotlin. Null = buang baris. */
    static String ubahBarisJava(String kode, Keadaan k) {
        if (kode.equals("@Override")) {
            k.overrideBerikutnya = true;
            return null;
        }
        // Paket dan impor: buang titik koma.
        if (kode.startsWith("package ") || kode.startsWith("import ")) {
            return buangTitikKoma(kode);
        }
        // Kelas.
        Matcher mKelas = POLA_KELAS.matcher(kode);
        if (mKelas.matches()) {
            String nama = mKelas.group(1);
            k.namaKelas = nama;
            StringBuilder b = new StringBuilder("class ").append(nama);
            String induk = mKelas.group(2);
            String antar = mKelas.group(3);
            if (induk != null) b.append(" : ").append(induk).append("()");
            if (antar != null) {
                String[] daftar = antar.split(",");
                for (int i = 0; i < daftar.length; i++) {
                    String it = daftar[i].trim();
                    if (it.length() == 0) continue;
                    b.append(induk == null && i == 0 ? " : " : ", ").append(it);
                }
            }
            b.append(" {");
            return b.toString();
        }
        // Konstruktor (namanya sama dengan kelas terakhir yang dilihat).
        if (k.namaKelas != null) {
            Matcher mK = POLA_KONSTRUKTOR.matcher(kode);
            if (mK.matches() && mK.group(2).equals(k.namaKelas)) {
                String kurung = mK.group(4);
                return "constructor(" + paramKotlin(mK.group(3)) + ")"
                        + (kurung != null && kurung.length() > 0 ? " {" : "");
            }
        }
        // Metode.
        Matcher mMetode = POLA_METODE.matcher(kode);
        if (mMetode.matches()) {
            String tipe = mMetode.group(4).trim();
            String nama = mMetode.group(5);
            if (!adalahKataKunci(tipe) && !adalahKataKunci(nama)
                    && !tipe.contains(" ")) {
                String vis = mMetode.group(1);
                StringBuilder b = new StringBuilder();
                if ("private".equals(vis)) b.append("private ");
                else if ("protected".equals(vis)) b.append("protected ");
                if (k.overrideBerikutnya) b.append("override ");
                k.overrideBerikutnya = false;
                b.append("fun ").append(nama).append("(")
                 .append(paramKotlin(mMetode.group(6))).append(")");
                if (!tipe.equals("void")) b.append(": ").append(petaTipe(tipe));
                String kurung = mMetode.group(7);
                if (kurung != null && kurung.length() > 0) b.append(" {");
                return b.toString();
            }
            k.overrideBerikutnya = false;
        } else {
            k.overrideBerikutnya = false;
        }
        // Field / deklarasi lokal tipe dasar.
        String field = ubahField(POLA_FIELD_DASAR.matcher(kode));
        if (field != null) return field;
        // Field / deklarasi lokal tipe objek/generik (List<String> dst).
        Matcher mUmum = POLA_FIELD_UMUM.matcher(kode);
        if (mUmum.matches()) {
            String tipe = mUmum.group(4).trim();
            String kataPertama = tipe.split("[<\\s]")[0];
            if (!adalahKataKunci(kataPertama)) {
                return bangunField(mUmum.group(1), mUmum.group(3) != null,
                        tipe, mUmum.group(5), mUmum.group(7));
            }
        }
        // For hitung dan for-each.
        Matcher mFor = POLA_FOR.matcher(kode);
        if (mFor.matches()) {
            String v = mFor.group(1);
            String awal = mFor.group(2).trim();
            String akhir = mFor.group(4).trim();
            if (mFor.group(3).equals("<=")) {
                return "for (" + v + " in " + awal + ".." + akhir + ") {";
            }
            return "for (" + v + " in " + awal + " until " + akhir + ") {";
        }
        Matcher mEach = POLA_FOR_EACH.matcher(kode);
        if (mEach.matches()) {
            return "for (" + mEach.group(2) + " in " + mEach.group(3).trim() + ") {";
        }
        // Baris lain: penyesuaian aman saja, isi dipertahankan.
        return penyesuaianUmum(kode);
    }

    /** Field/deklarasi dari pola dasar; null bila tidak cocok. */
    static String ubahField(Matcher m) {
        if (!m.matches()) return null;
        String tipe = m.group(4);
        String kataPertama = tipe.split("[<\\s]")[0];
        if (adalahKataKunci(kataPertama)) return null;
        return bangunField(m.group(1), m.group(3) != null, tipe, m.group(5), m.group(7));
    }

    /** "private final String nama = ..." -> "private val nama: String = ...". */
    static String bangunField(String vis, boolean fin, String tipeJava,
                              String nama, String nilai) {
        StringBuilder b = new StringBuilder();
        if ("private".equals(vis)) b.append("private ");
        else if ("protected".equals(vis)) b.append("protected ");
        boolean adaNilai = nilai != null && nilai.trim().length() > 0;
        boolean pakaiVal = fin && adaNilai;
        b.append(pakaiVal ? "val " : "var ").append(nama).append(": ")
         .append(petaTipe(tipeJava));
        if (adaNilai) {
            b.append(" = ").append(penyesuaianUmum(nilai.trim()));
        } else {
            b.append("? = null");
        }
        return b.toString();
    }

    /** "String nama, int umur" -> "nama: String, umur: Int". */
    static String paramKotlin(String paramJava) {
        if (paramJava == null) return "";
        String p = paramJava.trim();
        if (p.length() == 0) return "";
        String[] bagian = p.split(",");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < bagian.length; i++) {
            String seg = bagian[i].trim();
            if (seg.length() == 0) continue;
            if (i > 0 && b.length() > 0) b.append(", ");
            if (seg.endsWith("...")) {
                String[] kata = seg.substring(0, seg.length() - 3).trim().split("\\s+");
                if (kata.length >= 2) {
                    b.append("vararg ").append(kata[kata.length - 1]).append(": ")
                     .append(petaTipe(gabungKata(kata, kata.length - 1)));
                    continue;
                }
            }
            String[] kata = seg.split("\\s+");
            if (kata.length >= 2) {
                String nama = kata[kata.length - 1];
                String tipe = gabungKata(kata, kata.length - 1);
                b.append(nama).append(": ").append(petaTipe(tipe));
            } else {
                b.append(seg);
            }
        }
        return b.toString();
    }

    static String gabungKata(String[] kata, int sampai) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < sampai; i++) {
            if (i > 0) b.append(" ");
            b.append(kata[i]);
        }
        return b.toString();
    }

    /** Tipe Java -> tipe Kotlin (primitif, String, array, generik). */
    static String petaTipe(String tipe) {
        String t = tipe.trim();
        if (t.equals("String[]")) return "Array<String>";
        if (t.equals("int[]")) return "IntArray";
        if (t.equals("boolean[]")) return "BooleanArray";
        if (t.equals("double[]")) return "DoubleArray";
        if (t.equals("long[]")) return "LongArray";
        t = t.replaceAll("\\bint\\b", "Int");
        t = t.replaceAll("\\bboolean\\b", "Boolean");
        t = t.replaceAll("\\bdouble\\b", "Double");
        t = t.replaceAll("\\blong\\b", "Long");
        t = t.replaceAll("\\bfloat\\b", "Float");
        t = t.replaceAll("\\bchar\\b", "Char");
        t = t.replaceAll("\\bbyte\\b", "Byte");
        t = t.replaceAll("\\bshort\\b", "Short");
        t = t.replaceAll("\\bInteger\\b", "Int");
        t = t.replaceAll("\\bObject\\b", "Any");
        return t;
    }

    /** Penyesuaian yang aman di baris apa pun: println, new, titik koma akhir. */
    static String penyesuaianUmum(String kode) {
        String k = kode;
        k = k.replace("System.out.println(", "println(");
        k = k.replace("System.out.print(", "print(");
        k = k.replaceAll("\\bnew\\s+(\\w+(?:<[^>]*>)?)\\s*\\(", "$1(");
        return buangTitikKoma(k);
    }

    /** Buang satu titik koma di akhir baris (bukan di dalam string). */
    static String buangTitikKoma(String kode) {
        String k = kode.trim();
        if (k.endsWith(";")) return k.substring(0, k.length() - 1);
        return k;
    }

    // ------------------------------------------------------------ XML -> Compose

    /** Nama tag dari baris XML ("<TextView ..." -> "TextView"). */
    static String namaTag(String baris) {
        String t = baris.trim();
        if (t.startsWith("</")) t = t.substring(2);
        else if (t.startsWith("<")) t = t.substring(1);
        else return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ' || c == '/' || c == '>') break;
            b.append(c);
        }
        return b.toString();
    }

    /** Apakah tag ini wadah (tata letak) yang menjadi Column/Row. */
    static boolean adalahWadah(String nama) {
        if (nama == null) return false;
        if (nama.endsWith("Layout")) return true;
        if (nama.equals("ScrollView") || nama.equals("HorizontalScrollView")) return true;
        if (nama.equals("CardView")) return true;
        return false;
    }

    /** Nilai atribut android:... dari baris tag; null bila tidak ada. */
    static String atribut(String baris, String nama) {
        Pattern p = Pattern.compile(Pattern.quote(nama) + "\\s*=\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(baris);
        return m.find() ? m.group(1) : null;
    }

    /** Isi teks di antara <Tag ...>isi</Tag> pada satu baris; null bila kosong. */
    static String isiTag(String baris) {
        int awal = baris.indexOf('>');
        int akhir = baris.indexOf("</", awal + 1);
        if (awal < 0 || akhir < 0) return null;
        String isi = baris.substring(awal + 1, akhir).trim();
        return isi.length() == 0 ? null : isi;
    }

    /** Entitas XML -> karakter aslinya. */
    static String lepasXml(String teks) {
        if (teks == null) return "";
        return teks.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** Loloskan teks untuk string Kotlin "...". */
    static String lolosKotlin(String teks) {
        return teks.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    /** Nilai teks untuk Compose: @string/nama -> stringResource, lainnya string "...". */
    static String nilaiTeks(String teks) {
        if (teks == null) return "\"\"";
        String t = lepasXml(teks);
        if (t.startsWith("@string/")) {
            String nama = t.substring("@string/".length()).trim();
            if (nama.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return "stringResource(R.string." + nama + ")";
            }
        }
        return "\"" + lolosKotlin(t) + "\"";
    }

    static String indent(int dalam) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < dalam; i++) b.append("    ");
        return b.toString();
    }

    /** Mengonversi seluruh isi berkas layout XML menjadi kode Compose. Null -> "". */
    public static String xmlKeCompose(String xml) {
        if (xml == null) return "";
        String[] baris = xml.split("\n", -1);
        List<String> keluar = new ArrayList<String>();
        int dalam = 0;
        for (int i = 0; i < baris.length; i++) {
            String barisAsli = baris[i];
            String t = barisAsli.trim();
            if (t.length() == 0) continue;
            if (t.startsWith("<?")) continue;
            // Tag buka yang terbagi beberapa baris (atribut di baris
            // berikut): gabung sampai tanda '>' supaya atribut terbaca.
            if (t.startsWith("<") && !t.startsWith("</") && !t.startsWith("<!--")
                    && t.indexOf('>') < 0) {
                StringBuilder tag = new StringBuilder(t);
                while (i + 1 < baris.length && tag.indexOf(">") < 0) {
                    i++;
                    tag.append(" ").append(baris[i].trim());
                }
                t = tag.toString();
                barisAsli = t;
            }
            if (t.startsWith("<!--")) {
                String isi = t.replace("<!--", "").replace("-->", "").trim();
                keluar.add(indent(dalam) + "// " + isi);
                continue;
            }
            if (t.startsWith("</")) {
                String nama = namaTag(t);
                if (adalahWadah(nama)) {
                    if (dalam > 0) dalam--;
                    keluar.add(indent(dalam) + "}");
                }
                // Penutup TextView/Button/EditText: sudah satu baris, lewati.
                continue;
            }
            if (!t.startsWith("<")) continue; // teks bebas antar tag diabaikan
            String nama = namaTag(t);
            if (nama.length() == 0) continue;
            boolean tutupSendiri = t.endsWith("/>");
            if (adalahWadah(nama)) {
                String orientasi = atribut(t, "android:orientation");
                boolean horizontal = orientasi != null
                        && orientasi.trim().equalsIgnoreCase("horizontal");
                keluar.add(indent(dalam) + (horizontal ? "Row {" : "Column {"));
                if (tutupSendiri) {
                    keluar.add(indent(dalam) + "}");
                } else {
                    dalam++;
                }
                continue;
            }
            if (nama.equals("TextView")) {
                String teks = atribut(t, "android:text");
                if (teks == null) teks = isiTag(t);
                keluar.add(indent(dalam) + "Text(text = " + nilaiTeks(teks) + ")");
                continue;
            }
            if (nama.equals("Button")) {
                String teks = atribut(t, "android:text");
                if (teks == null) teks = isiTag(t);
                keluar.add(indent(dalam) + "Button(onClick = { }) {");
                keluar.add(indent(dalam + 1) + "Text(" + nilaiTeks(teks) + ")");
                keluar.add(indent(dalam) + "}");
                continue;
            }
            if (nama.equals("EditText")) {
                String petunjuk = atribut(t, "android:hint");
                if (petunjuk != null) {
                    keluar.add(indent(dalam) + "TextField(value = \"\", onValueChange = {}, "
                            + "placeholder = { Text(" + nilaiTeks(petunjuk) + ") })");
                } else {
                    keluar.add(indent(dalam) + "TextField(value = \"\", onValueChange = {})");
                }
                continue;
            }
            // Tag tak dikenal: biarkan baris apa adanya, jangan dirusak.
            keluar.add(baris[i]);
        }
        return gabung(keluar);
    }
}
