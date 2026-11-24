package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mesin Pengodean Suara Berbasis Struktur Kode (Voice-to-AST, fitur 2
 * dari daftar Gemini, 5.7) — pengurai tata bahasa luring berbasis
 * aturan (bukan AI): ucapan bahasa Indonesia dipecah menjadi simpul
 * (jenis + nama + parameter/tipe/nilai) lalu ditulis ulang menjadi blok
 * kode Java, Kotlin, Compose (Kotlin), atau Python yang sah secara
 * sintaksis, lengkap dengan tanda baca dan indentasi otomatis.
 *
 * Kelas murni tanpa Android, bisa diuji di JVM. Ucapan yang tidak cocok
 * dengan pola apa pun mengembalikan "" supaya pemanggil mengumumkan
 * "perintah tidak dikenali" dan tidak menyisipkan apa pun ke editor.
 *
 * Pola yang dikenali (kata kunci dicocokkan tanpa memperhatikan
 * huruf besar/kecil; nama pengenal boleh berspasi, mis. "hitung
 * diskon" menjadi hitungDiskon):
 * - buat fungsi publik &lt;nama&gt; dengan parameter &lt;nama&gt; &lt;tipe&gt; dan ...
 * - buat fungsi &lt;nama&gt; mengembalikan &lt;tipe&gt;
 * - buat kelas &lt;nama&gt;
 * - buat variabel &lt;tipe&gt; &lt;nama&gt; sama dengan &lt;nilai&gt;
 * - jika &lt;kondisi&gt; maka
 * - cetak &lt;teks&gt;
 * - untuk &lt;nama&gt; dari &lt;a&gt; sampai &lt;b&gt;
 * - kembalikan &lt;nilai&gt;
 *
 * Tipe ucapan Indonesia: integer/bilangan bulat/angka -&gt; int,
 * double/desimal -&gt; double, teks/string -&gt; String, benar/salah -&gt;
 * boolean, karakter -&gt; char, panjang -&gt; long.
 */
public class DikteKode {

    /** Satu simpul hasil urai: kode jadi + jenis + nama untuk ringkasan. */
    public static class Hasil {
        /** Blok kode jadi ("" bila tidak dikenali). */
        public final String kode;
        /** Jenis struktur: fungsi, kelas, variabel, jika, cetak, perulangan, kembalikan. */
        public final String jenis;
        /** Nama struktur (nama fungsi/kelas/variabel, kondisi, teks, atau nilai). */
        public final String nama;

        public Hasil(String kode, String jenis, String nama) {
            this.kode = kode == null ? "" : kode;
            this.jenis = jenis == null ? "" : jenis;
            this.nama = nama == null ? "" : nama;
        }
    }

    /** Mengurai ucapan menjadi blok kode; "" bila tidak dikenali. */
    public static String urai(String ucapan, String bahasaTarget) {
        Hasil h = uraiHasil(ucapan, bahasaTarget);
        return h == null ? "" : h.kode;
    }

    /** Mengurai ucapan menjadi simpul (kode + jenis + nama); null bila tidak dikenali. */
    public static Hasil uraiHasil(String ucapan, String bahasaTarget) {
        if (ucapan == null) return null;
        String rapi = ucapan.trim().replaceAll("\\s+", " ");
        if (rapi.length() == 0) return null;
        // Titik akhir kalimat dari pengenal suara bukan bagian perintah.
        while (rapi.endsWith(".") || rapi.endsWith("?") || rapi.endsWith("!")) {
            rapi = rapi.substring(0, rapi.length() - 1).trim();
        }
        if (rapi.length() == 0) return null;
        String kecil = rapi.toLowerCase(Locale.ROOT);
        String target = targetBersih(bahasaTarget);

        if (kecil.startsWith("buat fungsi publik ")) {
            return buatFungsi(rapi.substring("buat fungsi publik ".length()),
                    kecil.substring("buat fungsi publik ".length()), target);
        }
        if (kecil.startsWith("buat fungsi ")) {
            return buatFungsi(rapi.substring("buat fungsi ".length()),
                    kecil.substring("buat fungsi ".length()), target);
        }
        if (kecil.startsWith("buat kelas ")) {
            String nama = jadiNama(rapi.substring("buat kelas ".length()), true);
            if (nama.length() == 0) return null;
            return new Hasil(kodeKelas(nama, target), "kelas", nama);
        }
        if (kecil.startsWith("buat variabel ")) {
            return buatVariabel(rapi.substring("buat variabel ".length()),
                    kecil.substring("buat variabel ".length()), target);
        }
        if (kecil.startsWith("jika ") && kecil.endsWith(" maka")) {
            String kondisi = rapi.substring("jika ".length(), rapi.length() - " maka".length()).trim();
            if (kondisi.length() == 0) return null;
            String k = terjemahKondisi(kondisi, target);
            return new Hasil(kodeJika(k, target), "jika", k);
        }
        if (kecil.startsWith("cetak ")) {
            String teks = rapi.substring("cetak ".length()).trim();
            if (teks.length() == 0) return null;
            String isi = teksLiteral(teks);
            return new Hasil(kodeCetak(isi, target), "cetak", isi);
        }
        if (kecil.startsWith("untuk ")) {
            return buatPerulangan(rapi.substring("untuk ".length()),
                    kecil.substring("untuk ".length()), target);
        }
        if (kecil.startsWith("kembalikan ")) {
            String nilai = rapi.substring("kembalikan ".length()).trim();
            if (nilai.length() == 0) return null;
            String v = nilaiKembali(nilai, target);
            return new Hasil(kodeKembali(v, target), "kembalikan", v);
        }
        return null;
    }

    // ------------------------------------------------------------ target

    /** Normalisasi bahasa target: java, kotlin, compose, python (lainnya -> java). */
    static String targetBersih(String bahasaTarget) {
        if (bahasaTarget == null) return "java";
        String t = bahasaTarget.trim().toLowerCase(Locale.ROOT);
        if (t.equals("kotlin") || t.equals("compose") || t.equals("python")) return t;
        return "java";
    }

    static boolean adalahPython(String target) {
        return "python".equals(target);
    }

    static boolean adalahKotlin(String target) {
        return "kotlin".equals(target) || "compose".equals(target);
    }

    // ------------------------------------------------------------ nama & tipe

    /**
     * Mengubah kata-kata ucapan menjadi pengenal Java/Kotlin/Python yang
     * sah: "hitung diskon" -&gt; hitungDiskon (camel) atau HitungDiskon
     * (Pascal untuk kelas). Satu kata berkas besar-kecil dipertahankan
     * kecuali huruf pertamanya. "" bila tidak ada kata yang sah.
     */
    static String jadiNama(String mentah, boolean pascal) {
        if (mentah == null) return "";
        String[] kata = mentah.trim().split("[^A-Za-z0-9]+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kata.length; i++) {
            if (kata[i].length() == 0) continue;
            String k = kata[i];
            if (pascal || b.length() > 0) {
                b.append(Character.toUpperCase(k.charAt(0)));
                if (k.length() > 1) b.append(k.substring(1));
            } else {
                b.append(Character.toLowerCase(k.charAt(0)));
                if (k.length() > 1) b.append(k.substring(1));
            }
        }
        String nama = b.toString();
        if (nama.length() == 0) return "";
        char awal = nama.charAt(0);
        if (!Character.isLetter(awal) && awal != '_') return "";
        return nama;
    }

    /** Kata tipe ucapan -> tipe Java; null bila tidak dikenal. */
    static String tipeJava(String kata) {
        if (kata == null) return null;
        String k = kata.trim().toLowerCase(Locale.ROOT);
        if (k.equals("integer") || k.equals("bilangan bulat") || k.equals("angka")
                || k.equals("int")) return "int";
        if (k.equals("double") || k.equals("desimal")) return "double";
        if (k.equals("teks") || k.equals("string")) return "String";
        if (k.equals("boolean") || k.equals("benar") || k.equals("salah")) return "boolean";
        if (k.equals("karakter") || k.equals("char")) return "char";
        if (k.equals("panjang") || k.equals("long")) return "long";
        if (k.equals("float") || k.equals("pecahan")) return "float";
        return null;
    }

    /** Tipe Java -> tipe Kotlin. */
    static String tipeKotlin(String tipeJava) {
        if (tipeJava == null) return null;
        if (tipeJava.equals("int")) return "Int";
        if (tipeJava.equals("double")) return "Double";
        if (tipeJava.equals("boolean")) return "Boolean";
        if (tipeJava.equals("char")) return "Char";
        if (tipeJava.equals("long")) return "Long";
        if (tipeJava.equals("float")) return "Float";
        return tipeJava;
    }

    /** Nilai awal bawaan sebuah tipe (untuk fungsi yang mengembalikan tipe). */
    static String nilaiBawaan(String tipeJava, String target) {
        if (tipeJava == null) return "0";
        if (tipeJava.equals("String")) return "\"\"";
        if (tipeJava.equals("boolean")) return adalahPython(target) ? "False" : "false";
        if (tipeJava.equals("double")) return "0.0";
        if (tipeJava.equals("float")) return adalahPython(target) ? "0.0" : "0.0f";
        if (tipeJava.equals("char")) return "'\\0'";
        if (tipeJava.equals("long")) return adalahPython(target) ? "0" : "0L";
        return "0";
    }

    /**
     * Tipe di awal teks: "bilangan bulat ..." (dua kata) dulu, lalu satu
     * kata. Mengembalikan {tipeJava, sisa} atau null bila tidak dikenal.
     */
    static String[] tipeDariAwal(String teksRapi, String teksKecil) {
        if (teksKecil.startsWith("bilangan bulat ")) {
            return new String[]{"int", teksRapi.substring("bilangan bulat ".length()).trim()};
        }
        if (teksKecil.startsWith("bilangan bulat")) {
            return new String[]{"int", ""};
        }
        int spasi = teksKecil.indexOf(' ');
        String pertama = spasi < 0 ? teksKecil : teksKecil.substring(0, spasi);
        String tipe = tipeJava(pertama);
        if (tipe == null) return null;
        String sisa = spasi < 0 ? "" : teksRapi.substring(spasi + 1).trim();
        return new String[]{tipe, sisa};
    }

    // ------------------------------------------------------------ nilai

    /** Apakah teks berupa angka (koma desimal Indonesia diterima). */
    static boolean adalahAngka(String teks) {
        return teks != null && teks.matches("-?[0-9]+([.,][0-9]+)?");
    }

    /** Koma desimal Indonesia -> titik desimal kode. */
    static String normalAngka(String teks) {
        return teks == null ? "" : teks.replace(',', '.');
    }

    /** Loloskan teks untuk diletakkan di dalam string "..." kode. */
    static String lolosTeks(String teks) {
        if (teks == null) return "";
        return teks.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Teks cetak: bila sudah berpetik, pakai apa adanya; bila tidak, beri petik. */
    static String teksLiteral(String teks) {
        String t = teks.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) return t;
        return "\"" + lolosTeks(t) + "\"";
    }

    /** Nilai variabel: benar/salah -> boolean, angka tetap, teks dipetik. */
    static String nilaiLiteral(String nilai, String tipeJava, String target) {
        String v = nilai.trim();
        String kecil = v.toLowerCase(Locale.ROOT);
        if (kecil.equals("benar")) return adalahPython(target) ? "True" : "true";
        if (kecil.equals("salah")) return adalahPython(target) ? "False" : "false";
        if (adalahAngka(v)) return normalAngka(v);
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) return v;
        if ("String".equals(tipeJava)) return "\"" + lolosTeks(v) + "\"";
        return v;
    }

    /** Nilai kembalikan: boolean/angka/pengenal tetap, kata lain dipetik. */
    static String nilaiKembali(String nilai, String target) {
        String v = nilai.trim();
        String kecil = v.toLowerCase(Locale.ROOT);
        if (kecil.equals("benar")) return adalahPython(target) ? "True" : "true";
        if (kecil.equals("salah")) return adalahPython(target) ? "False" : "false";
        if (adalahAngka(v)) return normalAngka(v);
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) return v;
        if (v.matches("[A-Za-z_][A-Za-z0-9_]*")) return v;
        return "\"" + lolosTeks(v) + "\"";
    }

    /** Kondisi "jika": sama dengan -> ==, dan/atau, benar/salah -> boolean. */
    static String terjemahKondisi(String kondisi, String target) {
        String k = kondisi;
        boolean python = adalahPython(target);
        k = k.replaceAll("(?i)lebih besar atau sama dengan", ">=");
        k = k.replaceAll("(?i)lebih kecil atau sama dengan", "<=");
        k = k.replaceAll("(?i)tidak sama dengan", "!=");
        k = k.replaceAll("(?i)sama dengan", "==");
        k = k.replaceAll("(?i)lebih besar dari", ">");
        k = k.replaceAll("(?i)lebih kecil dari", "<");
        k = k.replaceAll("(?i)\\s+dan\\s+", python ? " and " : " && ");
        k = k.replaceAll("(?i)\\s+atau\\s+", python ? " or " : " || ");
        k = k.replaceAll("(?i)\\bbenar\\b", python ? "True" : "true");
        k = k.replaceAll("(?i)\\bsalah\\b", python ? "False" : "false");
        k = k.replaceAll("([0-9]),([0-9])", "$1.$2");
        return k.trim().replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------ fungsi

    /**
     * Mengurai "buat fungsi [publik] &lt;nama&gt; [dengan parameter ...]
     * [mengembalikan &lt;tipe&gt;]". Parameter berbentuk
     * "&lt;nama&gt; &lt;tipe&gt; dan &lt;nama&gt; &lt;tipe&gt;".
     */
    static Hasil buatFungsi(String sisaRapi, String sisaKecil, String target) {
        String sisa = sisaRapi;
        String kecil = sisaKecil;
        String kembali = null;
        int idxKembali = kecil.indexOf(" mengembalikan ");
        if (idxKembali >= 0) {
            String teksTipe = sisa.substring(idxKembali + " mengembalikan ".length()).trim();
            String kecilTipe = teksTipe.toLowerCase(Locale.ROOT);
            String[] t = tipeDariAwal(teksTipe, kecilTipe);
            if (t == null) return null;
            kembali = t[0];
            sisa = sisa.substring(0, idxKembali).trim();
            kecil = kecil.substring(0, idxKembali).trim();
        }
        String paramTeks = null;
        int idxParam = kecil.indexOf(" dengan parameter ");
        if (idxParam >= 0) {
            paramTeks = sisa.substring(idxParam + " dengan parameter ".length()).trim();
            sisa = sisa.substring(0, idxParam).trim();
        }
        String nama = jadiNama(sisa, false);
        if (nama.length() == 0) return null;
        List<String[]> param = new ArrayList<String[]>();
        if (paramTeks != null) {
            param = uraiParameter(paramTeks);
            if (param == null) return null;
        }
        return new Hasil(kodeFungsi(nama, param, kembali, target), "fungsi", nama);
    }

    /** "harga integer dan persen double" -> [[harga,int],[persen,double]]. */
    static List<String[]> uraiParameter(String teks) {
        List<String[]> hasil = new ArrayList<String[]>();
        String[] bagian = teks.split("(?i)\\s+dan\\s+");
        for (int i = 0; i < bagian.length; i++) {
            String seg = bagian[i].trim();
            if (seg.length() == 0) return null;
            String kecil = seg.toLowerCase(Locale.ROOT);
            String[] kata = kecil.split("\\s+");
            if (kata.length < 2) return null;
            String tipeKata = kata[kata.length - 1];
            // Tipe dua kata ("bilangan bulat") di akhir segmen.
            String tipe = null;
            String namaMentah = null;
            if (kata.length >= 3 && kata[kata.length - 2].equals("bilangan")
                    && kata[kata.length - 1].equals("bulat")) {
                tipe = "int";
                int akhirNama = seg.toLowerCase(Locale.ROOT).lastIndexOf("bilangan bulat");
                namaMentah = akhirNama <= 0 ? "" : seg.substring(0, akhirNama).trim();
            } else {
                tipe = tipeJava(tipeKata);
                int akhirNama = seg.toLowerCase(Locale.ROOT).lastIndexOf(tipeKata);
                namaMentah = akhirNama <= 0 ? "" : seg.substring(0, akhirNama).trim();
            }
            if (tipe == null) return null;
            String nama = jadiNama(namaMentah, false);
            if (nama.length() == 0) return null;
            hasil.add(new String[]{nama, tipe});
        }
        return hasil;
    }

    static String kodeFungsi(String nama, List<String[]> param, String kembali, String target) {
        if (adalahPython(target)) {
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < param.size(); i++) {
                if (i > 0) p.append(", ");
                p.append(param.get(i)[0]);
            }
            StringBuilder b = new StringBuilder();
            b.append("def ").append(nama).append("(").append(p).append("):\n");
            if (kembali == null) {
                b.append("    pass");
            } else {
                b.append("    return ").append(nilaiBawaan(kembali, target));
            }
            return b.toString();
        }
        if (adalahKotlin(target)) {
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < param.size(); i++) {
                if (i > 0) p.append(", ");
                p.append(param.get(i)[0]).append(": ").append(tipeKotlin(param.get(i)[1]));
            }
            StringBuilder b = new StringBuilder();
            b.append("fun ").append(nama).append("(").append(p).append(")");
            if (kembali != null) b.append(": ").append(tipeKotlin(kembali));
            b.append(" {\n");
            if (kembali == null) {
                b.append("    \n}");
            } else {
                b.append("    return ").append(nilaiBawaan(kembali, target)).append("\n}");
            }
            return b.toString();
        }
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < param.size(); i++) {
            if (i > 0) p.append(", ");
            p.append(param.get(i)[1]).append(" ").append(param.get(i)[0]);
        }
        StringBuilder b = new StringBuilder();
        b.append("public ").append(kembali == null ? "void" : kembali)
         .append(" ").append(nama).append("(").append(p).append(") {\n");
        if (kembali == null) {
            b.append("    \n}");
        } else {
            b.append("    return ").append(nilaiBawaan(kembali, target)).append(";\n}");
        }
        return b.toString();
    }

    // ------------------------------------------------------------ pola lain

    static Hasil buatVariabel(String sisaRapi, String sisaKecil, String target) {
        String[] t = tipeDariAwal(sisaRapi, sisaKecil);
        if (t == null) return null;
        String tipe = t[0];
        String rest = t[1];
        String restKecil = rest.toLowerCase(Locale.ROOT);
        int idxSama = restKecil.indexOf(" sama dengan ");
        if (idxSama < 0) return null;
        String nama = jadiNama(rest.substring(0, idxSama).trim(), false);
        if (nama.length() == 0) return null;
        String nilai = nilaiLiteral(rest.substring(idxSama + " sama dengan ".length()).trim(),
                tipe, target);
        String kode;
        if (adalahPython(target)) {
            kode = nama + " = " + nilai;
        } else if (adalahKotlin(target)) {
            kode = "var " + nama + ": " + tipeKotlin(tipe) + " = " + nilai;
        } else {
            kode = tipe + " " + nama + " = " + nilai + ";";
        }
        return new Hasil(kode, "variabel", nama);
    }

    static Hasil buatPerulangan(String sisaRapi, String sisaKecil, String target) {
        int idxDari = sisaKecil.indexOf(" dari ");
        int idxSampai = sisaKecil.indexOf(" sampai ");
        if (idxDari < 0 || idxSampai < 0 || idxSampai <= idxDari) return null;
        String nama = jadiNama(sisaRapi.substring(0, idxDari).trim(), false);
        if (nama.length() == 0) return null;
        String awal = normalAngka(sisaRapi.substring(idxDari + " dari ".length(), idxSampai).trim());
        String akhir = normalAngka(sisaRapi.substring(idxSampai + " sampai ".length()).trim());
        if (awal.length() == 0 || akhir.length() == 0) return null;
        String kode;
        if (adalahPython(target)) {
            kode = "for " + nama + " in range(" + awal + ", " + akhir + " + 1):\n    pass";
        } else if (adalahKotlin(target)) {
            kode = "for (" + nama + " in " + awal + ".." + akhir + ") {\n    \n}";
        } else {
            kode = "for (int " + nama + " = " + awal + "; " + nama + " <= " + akhir
                    + "; " + nama + "++) {\n    \n}";
        }
        return new Hasil(kode, "perulangan", nama);
    }

    static String kodeKelas(String nama, String target) {
        if (adalahPython(target)) return "class " + nama + ":\n    pass";
        if (adalahKotlin(target)) return "class " + nama + " {\n    \n}";
        return "public class " + nama + " {\n    \n}";
    }

    static String kodeJika(String kondisi, String target) {
        if (adalahPython(target)) return "if " + kondisi + ":\n    pass";
        return "if (" + kondisi + ") {\n    \n}";
    }

    static String kodeCetak(String isiBerpetik, String target) {
        if (adalahPython(target)) return "print(" + isiBerpetik + ")";
        if (adalahKotlin(target)) return "println(" + isiBerpetik + ")";
        return "System.out.println(" + isiBerpetik + ");";
    }

    static String kodeKembali(String nilai, String target) {
        if (adalahPython(target) || adalahKotlin(target)) return "return " + nilai;
        return "return " + nilai + ";";
    }
}
