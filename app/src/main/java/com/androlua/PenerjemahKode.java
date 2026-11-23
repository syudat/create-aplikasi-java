package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Penerjemah Kode (Tahap 3H) — menerjemahkan kode antar bahasa secara
 * lokal (aturan pola, tanpa internet).
 *
 * Arah yang didukung:
 *  - "lua-java"    : Lua -> Java
 *  - "java-lua"    : Java -> Lua
 *  - "java-kotlin" : Java -> Kotlin
 *
 * Murni Java (tanpa API Android) supaya bisa diuji lewat JRE.
 * Aturan: baris demi baris, menjaga indentasi. Baris yang tidak
 * dikenali TIDAK dihapus: disimpan apa adanya dengan penanda
 * "TIDAK DITERJEMAHKAN" dan dicatat di Hasil.tidakDiterjemahkan
 * beserta nomor barisnya. Perkiraan (mis. for pairs, static di
 * Kotlin) ditandai "PERKIRAAN" di baris yang sama.
 *
 * Batasan jujur: ini penerjemah pola, bukan kompiler. Struktur
 * bersarang yang dalam, string berisi kata kunci, dan pola di luar
 * daftar di bawah bisa salah diterjemahkan — selalu periksa hasil
 * sebelum dipakai (dan uji dengan luac/ECJ bila memungkinkan).
 */
public class PenerjemahKode {
    public static final String LUA_JAVA = "lua-java";
    public static final String JAVA_LUA = "java-lua";
    public static final String JAVA_KOTLIN = "java-kotlin";

    public static final String[] ARAH = {LUA_JAVA, JAVA_LUA, JAVA_KOTLIN};

    /** Hasil terjemahan satu berkas. */
    public static class Hasil {
        /** Kode hasil terjemahan (termasuk baris kepala penjelasan). */
        public final String kode;
        /** "baris N: <isi>" untuk tiap baris yang tidak bisa diterjemahkan. */
        public final List<String> tidakDiterjemahkan;
        public final int barisMasuk;
        public final int barisKeluar;

        public Hasil(String kode, List<String> tidakDiterjemahkan,
                     int barisMasuk, int barisKeluar) {
            this.kode = kode;
            this.tidakDiterjemahkan = tidakDiterjemahkan;
            this.barisMasuk = barisMasuk;
            this.barisKeluar = barisKeluar;
        }

        public boolean adaYangGagal() {
            return !tidakDiterjemahkan.isEmpty();
        }
    }

    private PenerjemahKode() {
    }

    /** Label tampilan untuk tiap arah (dipakai dialog pilih arah). */
    public static String labelArah(String arah) {
        if (LUA_JAVA.equals(arah)) return "Lua \u2192 Java";
        if (JAVA_LUA.equals(arah)) return "Java \u2192 Lua";
        if (JAVA_KOTLIN.equals(arah)) return "Java \u2192 Kotlin";
        return arah;
    }

    /** Ekstensi berkas hasil untuk arah tertentu. */
    public static String ekstensiHasil(String arah) {
        if (LUA_JAVA.equals(arah)) return "java";
        if (JAVA_LUA.equals(arah)) return "lua";
        if (JAVA_KOTLIN.equals(arah)) return "kt";
        return "txt";
    }

    /** Nama berkas hasil dari nama sumber (tidak pernah menimpa asli). */
    public static String namaBerkasHasil(String namaSumber, String arah) {
        String dasar = namaSumber == null ? "kode" : namaSumber;
        int titik = dasar.lastIndexOf('.');
        if (titik > 0) dasar = dasar.substring(0, titik);
        return dasar + "-terjemahan." + ekstensiHasil(arah);
    }

    /** Terjemahkan kode sumber ke arah yang diminta. */
    public static Hasil terjemah(String kode, String arah) {
        if (kode == null) kode = "";
        if (LUA_JAVA.equals(arah)) return terjemahLuaKeJava(kode);
        if (JAVA_LUA.equals(arah)) return terjemahJavaKeLua(kode);
        if (JAVA_KOTLIN.equals(arah)) return terjemahJavaKeKotlin(kode);
        List<String> gagal = new ArrayList<>();
        gagal.add(TeksUI.t("arah tidak dikenal: ") + arah);
        return new Hasil(kode, gagal, 0, 0);
    }

    // ======================= util bersama =======================

    private static String indent(String baris) {
        int i = 0;
        while (i < baris.length() && (baris.charAt(i) == ' ' || baris.charAt(i) == '\t')) i++;
        return baris.substring(0, i);
    }

    private static String gantiKata(String teks, String dari, String ke) {
        return teks.replaceAll("(?<!\\w)" + Pattern.quote(dari) + "(?!\\w)",
                Matcher.quoteReplacement(ke));
    }

    /** Pustaka umum Lua <-> Java (string/math/konversi). */
    private static String gantiPustaka(String ekspr, boolean keJava) {
        String[][] pasangan = {
                {"string.format(", "String.format("},
                {"math.floor", "Math.floor"},
                {"math.ceil", "Math.ceil"},
                {"math.abs", "Math.abs"},
                {"math.sqrt", "Math.sqrt"},
                {"math.random", "Math.random"},
                {"math.max", "Math.max"},
                {"math.min", "Math.min"},
                {"tostring(", "String.valueOf("},
                {"tonumber(", "Double.parseDouble("},
        };
        for (String[] p : pasangan) {
            ekspr = keJava
                    ? ekspr.replace(p[0], p[1])
                    : ekspr.replace(p[1], p[0]);
        }
        // string.len(s) -> s.length() / sebaliknya
        if (keJava) {
            ekspr = ekspr.replaceAll("string\\.len\\(([^()]+)\\)", "$1.length()");
        } else {
            ekspr = ekspr.replaceAll("([A-Za-z_][A-Za-z0-9_]*)\\.length\\(\\)", "string.len($1)");
            ekspr = ekspr.replaceAll("([A-Za-z_][A-Za-z0-9_]*)\\.size\\(\\)", "#$1");
        }
        return ekspr;
    }

    // ======================= Lua -> Java =======================

    private static String eksprLuaKeJava(String ekspr) {
        String e = ekspr;
        // String berpetik satu -> berpetik ganda (bila tidak ada petik ganda).
        if (e.indexOf('"') < 0) {
            e = e.replaceAll("'([^']*)'", "\"$1\"");
        }
        e = e.replace("print(", "System.out.println(");
        e = gantiPustaka(e, true);
        e = e.replaceAll("#([A-Za-z_][A-Za-z0-9_]*)", "$1.size()");
        e = e.replace("~=", "!=");
        e = gantiKata(e, "and", "&&");
        e = gantiKata(e, "or", "||");
        e = e.replaceAll("(?<!\\w)not\\s+", "!");
        e = gantiKata(e, "nil", "null");
        // ".." jadi "+" tanpa menambah spasi (spasi di sekitar ".."
        // sudah ada di sumber, jadi "a .. b" -> "a + b", bukan "a  +  b").
        e = e.replace("..", "+");
        e = e.replaceAll("([A-Za-z_][A-Za-z0-9_]*):([A-Za-z_][A-Za-z0-9_]*)\\(", "$1.$2(");
        return e;
    }

    private static Hasil terjemahLuaKeJava(String kode) {
        String[] baris = kode.split("\n", -1);
        List<String> gagal = new ArrayList<>();
        StringBuilder keluar = new StringBuilder();
        keluar.append("// Hasil terjemahan otomatis Lua -> Java (aturan pola, periksa sebelum dipakai).\n");
        int diterjemahkan = 0;
        for (int n = 0; n < baris.length; n++) {
            String barisAsli = baris[n];
            String ind = indent(barisAsli);
            String t = barisAsli.trim();
            String hasil;
            if (t.isEmpty()) {
                hasil = "";
            } else if (t.startsWith("--")) {
                hasil = ind + "//" + t.substring(2);
                diterjemahkan++;
            } else if (t.startsWith("local function ") || t.startsWith("function ")) {
                String sisa = t.startsWith("local ") ? t.substring("local function ".length()) : t.substring("function ".length());
                hasil = ind + fungsiLuaKeJava(sisa);
                diterjemahkan++;
            } else if (t.startsWith("local ")) {
                hasil = ind + lokalLuaKeJava(t.substring("local ".length()).trim());
                diterjemahkan++;
            } else if (t.startsWith("if ") && t.endsWith("then")) {
                hasil = ind + "if (" + eksprLuaKeJava(t.substring(3, t.length() - 4).trim()) + ") {";
                diterjemahkan++;
            } else if (t.startsWith("elseif ") && t.endsWith("then")) {
                hasil = ind + "} else if (" + eksprLuaKeJava(t.substring(7, t.length() - 4).trim()) + ") {";
                diterjemahkan++;
            } else if ("else".equals(t)) {
                hasil = ind + "} else {";
                diterjemahkan++;
            } else if (t.startsWith("while ") && t.endsWith("do")) {
                hasil = ind + "while (" + eksprLuaKeJava(t.substring(6, t.length() - 2).trim()) + ") {";
                diterjemahkan++;
            } else if (t.startsWith("for ") && t.endsWith("do")) {
                String f = forLuaKeJava(t);
                if (f.startsWith("// TIDAK")) {
                    hasil = ind + f;
                    gagal.add("baris " + (n + 1) + ": " + t);
                } else {
                    hasil = ind + f;
                    diterjemahkan++;
                }
            } else if (t.startsWith("return ")) {
                hasil = ind + "return " + eksprLuaKeJava(t.substring(7).trim()) + ";";
                diterjemahkan++;
            } else if ("return".equals(t)) {
                hasil = ind + "return;";
                diterjemahkan++;
            } else if ("end".equals(t)) {
                hasil = ind + "}";
                diterjemahkan++;
            } else if ("break".equals(t)) {
                hasil = ind + "break;";
                diterjemahkan++;
            } else if (t.contains("=") && !t.contains("==") && !t.startsWith("print")) {
                int samaDengan = t.indexOf('=');
                String kiri = t.substring(0, samaDengan).trim();
                String kanan = t.substring(samaDengan + 1).trim();
                hasil = ind + kiri + " = " + eksprLuaKeJava(kanan) + ";";
                diterjemahkan++;
            } else {
                String e = eksprLuaKeJava(t);
                if (!e.endsWith(";")) e = e + ";";
                if (mengandungKataLua(e)) {
                    hasil = ind + e + " // TIDAK DITERJEMAHKAN";
                    gagal.add("baris " + (n + 1) + ": " + t);
                } else {
                    // Pernyataan biasa (mis. print/pemanggilan) yang
                    // ekspresinya sudah diterjemahkan: dianggap berhasil.
                    hasil = ind + e;
                    diterjemahkan++;
                }
            }
            keluar.append(hasil).append("\n");
        }
        return new Hasil(keluar.toString(), gagal, baris.length, baris.length + 1);
    }

    /** true bila teks hasil masih mengandung kata kunci Lua (tidak bisa jadi Java). */
    private static boolean mengandungKataLua(String e) {
        if (e.contains("::")) return true;
        String[] kata = {"goto", "repeat", "until", "then", "do", "end", "local", "function"};
        for (int i = 0; i < kata.length; i++) {
            if (Pattern.compile("(?<!\\w)" + kata[i] + "(?!\\w)").matcher(e).find()) return true;
        }
        return false;
    }

    /** "nama(a, b)" -> "static Object nama(Object a, Object b) {" */
    private static String fungsiLuaKeJava(String sisa) {
        int buka = sisa.indexOf('(');
        int tutup = sisa.lastIndexOf(')');
        if (buka < 0 || tutup < 0) return "// TIDAK DITERJEMAHKAN: " + sisa;
        String nama = sisa.substring(0, buka).trim();
        String param = sisa.substring(buka + 1, tutup).trim();
        StringBuilder p = new StringBuilder();
        if (!param.isEmpty()) {
            String[] bagian = param.split(",");
            for (int i = 0; i < bagian.length; i++) {
                if (i > 0) p.append(", ");
                p.append("Object ").append(bagian[i].trim());
            }
        }
        return "static Object " + nama + "(" + p + ") {";
    }

    /** "x = nilai" (tanpa kata local) -> "var x = nilai;". */
    private static String lokalLuaKeJava(String sisa) {
        int samaDengan = sisa.indexOf('=');
        if (samaDengan < 0) return "var " + sisa + ";";
        String nama = sisa.substring(0, samaDengan).trim();
        String nilai = sisa.substring(samaDengan + 1).trim();
        if ("{}".equals(nilai)) return "var " + nama + " = new java.util.ArrayList<>();";
        if (nilai.startsWith("{") && nilai.endsWith("}")) {
            return "var " + nama + " = java.util.Arrays.asList("
                    + nilai.substring(1, nilai.length() - 1).trim() + ");";
        }
        return "var " + nama + " = " + eksprLuaKeJava(nilai) + ";";
    }

    /** "for ..." Lua -> for Java (tanpa kata "do" di akhir). */
    private static String forLuaKeJava(String t) {
        String isi = t.substring(4, t.length() - 2).trim();
        Matcher mAngka = Pattern.compile("^(\\w+)\\s*=\\s*(.+?)\\s*,\\s*(.+?)(?:\\s*,\\s*(.+?))?$")
                .matcher(isi);
        if (mAngka.matches()) {
            String v = mAngka.group(1);
            String a = eksprLuaKeJava(mAngka.group(2).trim());
            String b = eksprLuaKeJava(mAngka.group(3).trim());
            String c = mAngka.group(4);
            if (c == null) {
                return "for (int " + v + " = " + a + "; " + v + " <= " + b + "; " + v + "++) {";
            }
            return "for (int " + v + " = " + a + "; " + v + " <= " + b + "; " + v + " += "
                    + eksprLuaKeJava(c.trim()) + ") {";
        }
        Matcher mPairs = Pattern.compile("^(\\w+)\\s*,\\s*(\\w+)\\s+in\\s+pairs\\((\\w+)\\)$").matcher(isi);
        if (mPairs.matches()) {
            return "for (var entri : " + mPairs.group(3) + ".entrySet()) {"
                    + " // PERKIRAAN: " + mPairs.group(1) + "/" + mPairs.group(2)
                    + " diganti entri.getKey()/getValue()";
        }
        Matcher mIpairs = Pattern.compile("^(\\w+)\\s+in\\s+ipairs\\((\\w+)\\)$").matcher(isi);
        if (mIpairs.matches()) {
            return "for (var " + mIpairs.group(1) + " : " + mIpairs.group(2) + ") {";
        }
        return "// TIDAK DITERJEMAHKAN: " + t;
    }

    // ======================= Java -> Lua =======================

    private static String eksprJavaKeLua(String ekspr) {
        String e = ekspr;
        e = e.replace("System.out.println(", "print(");
        e = e.replace("System.out.print(", "io.write(");
        e = gantiPustaka(e, false);
        e = e.replace("!=", "~=");
        e = gantiKata(e, "&&", "and");
        e = gantiKata(e, "||", "or");
        e = e.replaceAll("!(?!=)", "not ");
        e = gantiKata(e, "null", "nil");
        e = e.replaceAll("new\\s+(ArrayList|HashMap)\\s*<[^>]*>\\s*\\(\\s*\\)", "{}");
        e = e.replaceAll("new\\s+int\\s*\\[[^\\]]*\\]", "{}");
        // '+' jadi '..' hanya bila baris berisi string (penggabungan teks).
        if (e.indexOf('"') >= 0) e = e.replace(" + ", " .. ");
        return e;
    }

    private static Hasil terjemahJavaKeLua(String kode) {
        String[] baris = kode.split("\n", -1);
        List<String> gagal = new ArrayList<>();
        List<String> tumpukan = new ArrayList<>();
        StringBuilder keluar = new StringBuilder();
        keluar.append("-- Hasil terjemahan otomatis Java -> Lua (aturan pola, periksa sebelum dipakai).\n");
        int diterjemahkan = 0;
        Pattern polaMetode = Pattern.compile(
                "^(?:(?:public|private|protected|static|final|abstract|synchronized)\\s+)*"
                        + "([A-Za-z_][\\w<>\\[\\], ?]*?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{$");
        Pattern polaKelas = Pattern.compile(
                "^(?:(?:public|private|protected|abstract|final)\\s+)*class\\s+(\\w+).*\\{$");
        Pattern polaDeklarasi = Pattern.compile(
                "^(?:(?:public|private|protected|static|final|transient|volatile)\\s+)*"
                        + "([A-Za-z_][\\w<>\\[\\], ?]*?)\\s+(\\w+)\\s*(?:=\\s*(.+?))?;\\s*$");
        Pattern polaTugas = Pattern.compile("^(\\w+)\\s*=\\s*(.+);\\s*$");
        for (int n = 0; n < baris.length; n++) {
            String barisAsli = baris[n];
            String ind = indent(barisAsli);
            String t = barisAsli.trim();
            String hasil;
            if (t.isEmpty()) {
                hasil = "";
            } else if (t.startsWith("//")) {
                hasil = ind + "--" + t.substring(2);
                diterjemahkan++;
            } else if (t.startsWith("import ")) {
                gagal.add("baris " + (n + 1) + ": impor dihapus (" + t + ")");
                continue;
            } else if (t.startsWith("package ")) {
                gagal.add("baris " + (n + 1) + ": paket dihapus (" + t + ")");
                continue;
            } else if (t.startsWith("@")) {
                gagal.add("baris " + (n + 1) + ": anotasi dihapus (" + t + ")");
                continue;
            } else if (t.equals("public static void main(String[] args) {")) {
                hasil = ind + "local function main()";
                tumpukan.add("function");
                diterjemahkan++;
            } else if (polaKelas.matcher(t).matches()) {
                Matcher m = polaKelas.matcher(t);
                m.matches();
                hasil = ind + "-- kelas " + m.group(1) + " (tidak ada kelas di Lua)";
                tumpukan.add("class");
                diterjemahkan++;
            } else if (t.startsWith("if ") && t.endsWith("{")) {
                String kond = t.substring(3, t.length() - 1).trim();
                kond = kond.substring(1, kond.length() - 1).trim();
                hasil = ind + "if " + eksprJavaKeLua(kond) + " then";
                tumpukan.add("if");
                diterjemahkan++;
            } else if (t.startsWith("} else if (") && t.endsWith("{")) {
                String kond = t.substring("} else if (".length(), t.length() - 2).trim();
                kond = kond.substring(0, kond.length() - 1).trim();
                if (!tumpukan.isEmpty()) tumpukan.remove(tumpukan.size() - 1);
                hasil = ind + "elseif " + eksprJavaKeLua(kond) + " then";
                tumpukan.add("if");
                diterjemahkan++;
            } else if ("} else {".equals(t)) {
                if (!tumpukan.isEmpty()) tumpukan.remove(tumpukan.size() - 1);
                hasil = ind + "else";
                tumpukan.add("if");
                diterjemahkan++;
            } else if (t.startsWith("while ") && t.endsWith("{")) {
                String kond = t.substring(6, t.length() - 1).trim();
                kond = kond.substring(1, kond.length() - 1).trim();
                hasil = ind + "while " + eksprJavaKeLua(kond) + " do";
                tumpukan.add("while");
                diterjemahkan++;
            } else if (t.startsWith("for ") && t.endsWith("{")) {
                String f = forJavaKeLua(t);
                if (f.startsWith("-- TIDAK")) {
                    hasil = ind + f;
                    gagal.add("baris " + (n + 1) + ": " + t);
                } else {
                    hasil = ind + f;
                    tumpukan.add("for");
                    diterjemahkan++;
                }
            } else if ("}".equals(t)) {
                String jenis = tumpukan.isEmpty() ? "" : tumpukan.remove(tumpukan.size() - 1);
                if ("class".equals(jenis)) continue;
                hasil = ind + "end";
                diterjemahkan++;
            } else if (t.startsWith("return ")) {
                hasil = ind + "return " + eksprJavaKeLua(t.substring(7, t.length() - 1).trim());
                diterjemahkan++;
            } else if ("return;".equals(t)) {
                hasil = ind + "return";
                diterjemahkan++;
            } else if ("break;".equals(t)) {
                hasil = ind + "break";
                diterjemahkan++;
            } else if (polaMetode.matcher(t).matches()) {
                Matcher m = polaMetode.matcher(t);
                m.matches();
                hasil = ind + "local function " + m.group(2) + "(" + paramJavaKeLua(m.group(3)) + ")";
                tumpukan.add("function");
                diterjemahkan++;
            } else if (polaDeklarasi.matcher(t).matches()) {
                Matcher m = polaDeklarasi.matcher(t);
                m.matches();
                String nilai = m.group(3);
                hasil = ind + "local " + m.group(2) + " = "
                        + (nilai == null ? "nil" : eksprJavaKeLua(nilai.trim()));
                diterjemahkan++;
            } else if (polaTugas.matcher(t).matches()) {
                Matcher m = polaTugas.matcher(t);
                m.matches();
                hasil = ind + m.group(1) + " = " + eksprJavaKeLua(m.group(2).trim());
                diterjemahkan++;
            } else {
                String e = eksprJavaKeLua(t);
                if (e.endsWith(";")) e = e.substring(0, e.length() - 1);
                if (e.contains(".") && e.contains("(")) {
                    hasil = ind + e + " -- TIDAK DITERJEMAHKAN";
                    gagal.add("baris " + (n + 1) + ": " + t);
                } else {
                    hasil = ind + e;
                    diterjemahkan++;
                }
            }
            keluar.append(hasil).append("\n");
        }
        return new Hasil(keluar.toString(), gagal, baris.length, baris.length + 1);
    }

    /** "int a, String b" -> "a, b" (tipe dibuang, Lua tanpa tipe). */
    private static String paramJavaKeLua(String param) {
        if (param == null || param.trim().isEmpty()) return "";
        String[] bagian = param.split(",");
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < bagian.length; i++) {
            String[] kata = bagian[i].trim().split("\\s+");
            if (i > 0) p.append(", ");
            p.append(kata[kata.length - 1]);
        }
        return p.toString();
    }

    /** "for (int i = 1; i <= 10; i++) {" -> "for i = 1, 10 do". */
    private static String forJavaKeLua(String t) {
        Matcher m = Pattern.compile(
                "^for\\s*\\(\\s*int\\s+(\\w+)\\s*=\\s*(.+?)\\s*;\\s*\\1\\s*(<=|<)\\s*(.+?)\\s*;\\s*\\1\\s*(\\+\\+|\\+=\\s*.+?)\\s*\\)\\s*\\{$")
                .matcher(t);
        if (m.matches()) {
            String v = m.group(1);
            String a = m.group(2).trim();
            String op = m.group(3);
            String b = m.group(4).trim();
            String langkah = m.group(5).trim();
            if ("<".equals(op)) b = b + " - 1";
            if ("++".equals(langkah)) {
                return "for " + v + " = " + a + ", " + b + " do";
            }
            String c = langkah.substring(2).trim();
            return "for " + v + " = " + a + ", " + b + ", " + c + " do";
        }
        Matcher mTiap = Pattern.compile("^for\\s*\\(\\s*[A-Za-z_][\\w<>]*\\s+(\\w+)\\s*:\\s*(\\w+)\\s*\\)\\s*\\{$")
                .matcher(t);
        if (mTiap.matches()) {
            return "for _, " + mTiap.group(1) + " in ipairs(" + mTiap.group(2)
                    + ") do -- PERKIRAAN: for-each Java";
        }
        return "-- TIDAK DITERJEMAHKAN: " + t;
    }

    // ======================= Java -> Kotlin =======================

    private static String petakanTipe(String tipe) {
        String t = tipe.trim();
        t = gantiKata(t, "int", "Int");
        t = gantiKata(t, "long", "Long");
        t = gantiKata(t, "double", "Double");
        t = gantiKata(t, "float", "Float");
        t = gantiKata(t, "boolean", "Boolean");
        t = gantiKata(t, "char", "Char");
        t = gantiKata(t, "byte", "Byte");
        t = gantiKata(t, "short", "Short");
        t = gantiKata(t, "Integer", "Int");
        t = gantiKata(t, "Long", "Long");
        t = gantiKata(t, "Double", "Double");
        t = gantiKata(t, "Float", "Float");
        t = gantiKata(t, "Boolean", "Boolean");
        t = gantiKata(t, "Character", "Char");
        t = gantiKata(t, "Object", "Any");
        t = t.replace("ArrayList<", "MutableList<");
        t = t.replace("HashMap<", "MutableMap<");
        return t;
    }

    private static String eksprJavaKeKotlin(String ekspr) {
        String e = ekspr;
        e = e.replace("System.out.println(", "println(");
        e = e.replace("System.out.print(", "print(");
        e = e.replaceAll("new\\s+ArrayList\\s*<([^>]*)>\\s*\\(\\s*\\)", "mutableListOf<$1>()");
        e = e.replaceAll("new\\s+ArrayList\\s*\\(\\s*\\)", "mutableListOf()");
        e = e.replaceAll("new\\s+HashMap\\s*<([^>]*)>\\s*\\(\\s*\\)", "mutableMapOf<$1>()");
        e = e.replaceAll("new\\s+HashMap\\s*\\(\\s*\\)", "mutableMapOf()");
        e = e.replaceAll("new\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(", "$1(");
        e = gantiKata(e, "instanceof", "is");
        return e;
    }

    private static Hasil terjemahJavaKeKotlin(String kode) {
        String[] baris = kode.split("\n", -1);
        List<String> gagal = new ArrayList<>();
        StringBuilder keluar = new StringBuilder();
        keluar.append("// Hasil terjemahan otomatis Java -> Kotlin (aturan pola, periksa sebelum dipakai).\n");
        int diterjemahkan = 0;
        Pattern polaMetode = Pattern.compile(
                "^(?:(public|private|protected)\\s+)?(?:(static|final|abstract|synchronized)\\s+)*"
                        + "([A-Za-z_][\\w<>\\[\\], ?]*?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{$");
        Pattern polaKelas = Pattern.compile(
                "^(?:(public|private|protected)\\s+)?(?:(abstract|open|final)\\s+)*class\\s+(\\w+).*\\{$");
        Pattern polaDeklarasi = Pattern.compile(
                "^(?:(public|private|protected)\\s+)?(?:(static|final|transient|volatile)\\s+)*"
                        + "([A-Za-z_][\\w<>\\[\\], ?]*?)\\s+(\\w+)\\s*(?:=\\s*(.+?))?;\\s*$");
        Pattern polaTugas = Pattern.compile("^(\\w+)\\s*=\\s*(.+);\\s*$");
        for (int n = 0; n < baris.length; n++) {
            String barisAsli = baris[n];
            String ind = indent(barisAsli);
            String t = barisAsli.trim();
            String hasil;
            if (t.isEmpty()) {
                hasil = "";
            } else if (t.startsWith("//")) {
                hasil = barisAsli;
                diterjemahkan++;
            } else if (t.startsWith("import ") && t.endsWith(";")) {
                hasil = ind + t.substring(0, t.length() - 1);
                diterjemahkan++;
            } else if (t.startsWith("package ") && t.endsWith(";")) {
                hasil = ind + t.substring(0, t.length() - 1);
                diterjemahkan++;
            } else if (t.startsWith("@")) {
                gagal.add("baris " + (n + 1) + ": anotasi dihapus (" + t + ")");
                continue;
            } else if (t.equals("public static void main(String[] args) {")) {
                hasil = ind + "fun main(args: Array<String>) {";
                diterjemahkan++;
            } else if (polaKelas.matcher(t).matches()) {
                Matcher m = polaKelas.matcher(t);
                m.matches();
                String awalan = m.group(1) == null ? "" : m.group(1) + " ";
                String sifat = m.group(2) == null ? "" : m.group(2) + " ";
                hasil = ind + awalan + sifat + "class " + m.group(3) + " {";
                diterjemahkan++;
            } else if (t.startsWith("for ") && t.endsWith("{")) {
                String f = forJavaKeKotlin(t);
                if (f.startsWith("// TIDAK")) {
                    hasil = ind + f;
                    gagal.add("baris " + (n + 1) + ": " + t);
                } else {
                    hasil = ind + f;
                    diterjemahkan++;
                }
            } else if (polaMetode.matcher(t).matches()) {
                Matcher m = polaMetode.matcher(t);
                m.matches();
                String visibilitas = m.group(1) == null ? "" : m.group(1) + " ";
                String tipeKembali = m.group(3).trim();
                String param = paramJavaKeKotlin(m.group(5));
                if ("void".equals(tipeKembali)) {
                    hasil = ind + visibilitas + "fun " + m.group(4) + "(" + param + ") {";
                } else {
                    hasil = ind + visibilitas + "fun " + m.group(4) + "(" + param + "): "
                            + petakanTipe(tipeKembali) + " {";
                }
                diterjemahkan++;
            } else if (polaDeklarasi.matcher(t).matches()) {
                Matcher m = polaDeklarasi.matcher(t);
                m.matches();
                String visibilitas = m.group(1) == null ? "" : m.group(1) + " ";
                String sifat = m.group(2) == null ? "" : m.group(2);
                boolean akhir = "final".equals(sifat);
                boolean statis = "static".equals(sifat);
                String nama = m.group(4);
                String nilai = m.group(5);
                String kata = akhir ? "val" : "var";
                if (nilai == null) {
                    hasil = ind + visibilitas + kata + " " + nama + ": "
                            + petakanTipe(m.group(3)) + "? = null"
                            + " // PERKIRAAN: tanpa nilai awal, dibuat nullable";
                } else {
                    hasil = ind + visibilitas + kata + " " + nama + " = "
                            + eksprJavaKeKotlin(nilai.trim());
                    if (statis) hasil += " // PERKIRAAN: static dihapus, pindah ke companion object";
                }
                diterjemahkan++;
            } else if (polaTugas.matcher(t).matches()) {
                Matcher m = polaTugas.matcher(t);
                m.matches();
                hasil = ind + m.group(1) + " = " + eksprJavaKeKotlin(m.group(2).trim());
                diterjemahkan++;
            } else if (t.startsWith("return ")) {
                hasil = ind + "return " + eksprJavaKeKotlin(t.substring(7, t.length() - 1).trim());
                diterjemahkan++;
            } else if ("return;".equals(t)) {
                hasil = ind + "return";
                diterjemahkan++;
            } else if ("break;".equals(t)) {
                hasil = ind + "break";
                diterjemahkan++;
            } else {
                String e = eksprJavaKeKotlin(t);
                if (e.endsWith(";")) e = e.substring(0, e.length() - 1);
                if (e.endsWith("{") || e.endsWith("}") || e.endsWith(",")
                        || e.startsWith("if ") || e.startsWith("} else")) {
                    hasil = ind + e;
                    diterjemahkan++;
                } else if (e.contains("=") || e.contains("(")) {
                    hasil = ind + e;
                    diterjemahkan++;
                } else {
                    hasil = ind + e + " // TIDAK DITERJEMAHKAN";
                    gagal.add("baris " + (n + 1) + ": " + t);
                }
            }
            keluar.append(hasil).append("\n");
        }
        return new Hasil(keluar.toString(), gagal, baris.length, baris.length + 1);
    }

    /** "int a, String b" -> "a: Int, b: String". */
    private static String paramJavaKeKotlin(String param) {
        if (param == null || param.trim().isEmpty()) return "";
        String[] bagian = param.split(",");
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < bagian.length; i++) {
            String[] kata = bagian[i].trim().split("\\s+");
            if (kata.length < 2) continue;
            StringBuilder tipe = new StringBuilder();
            for (int j = 0; j < kata.length - 1; j++) {
                if (j > 0) tipe.append(" ");
                tipe.append(kata[j]);
            }
            if (i > 0) p.append(", ");
            p.append(kata[kata.length - 1]).append(": ").append(petakanTipe(tipe.toString()));
        }
        return p.toString();
    }

    /** "for (int i = 0; i < n; i++) {" -> "for (i in 0 until n) {". */
    private static String forJavaKeKotlin(String t) {
        Matcher m = Pattern.compile(
                "^for\\s*\\(\\s*int\\s+(\\w+)\\s*=\\s*(.+?)\\s*;\\s*\\1\\s*(<=|<)\\s*(.+?)\\s*;\\s*\\1\\s*(\\+\\+|\\+=\\s*.+?)\\s*\\)\\s*\\{$")
                .matcher(t);
        if (m.matches()) {
            String v = m.group(1);
            String a = m.group(2).trim();
            String op = m.group(3);
            String b = m.group(4).trim();
            String langkah = m.group(5).trim();
            String rentang = "<".equals(op) ? a + " until " + b : a + ".." + b;
            if ("++".equals(langkah)) {
                return "for (" + v + " in " + rentang + ") {";
            }
            return "for (" + v + " in " + rentang + " step " + langkah.substring(2).trim() + ") {";
        }
        Matcher mTiap = Pattern.compile("^for\\s*\\(\\s*[A-Za-z_][\\w<>]*\\s+(\\w+)\\s*:\\s*(\\w+)\\s*\\)\\s*\\{$")
                .matcher(t);
        if (mTiap.matches()) {
            return "for (" + mTiap.group(1) + " in " + mTiap.group(2) + ") {";
        }
        return "// TIDAK DITERJEMAHKAN: " + t;
    }
}
