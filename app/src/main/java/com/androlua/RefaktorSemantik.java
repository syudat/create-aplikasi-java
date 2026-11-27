package com.androlua;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesin Refactoring Semantik Lintas-Berkas dan Navigasi Pohon Sintaks AST
 * (Fitur 5, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer.
 *
 * PARSER AST PENUH (bukan pola teks): proyek TIDAK membawa pustaka
 * parser yang bisa dipakai — berkas ecj-dex.jar yang dipakai BangunJava
 * hanya berisi kompilator batch ECJ (org.eclipse.jdt.core.dom TIDAK
 * ADA di dalamnya; diperiksa langsung dari isi jar), dan kotlinc hanya
 * tersedia sebagai kompilator, bukan sebagai pustaka parser. Karena itu
 * parser di sini dibangun sendiri dan luring: sebuah lekser + parser
 * rekursif untuk Java, plus parser subset Kotlin (class/fun/val/var).
 * Lekser membuang komentar dan literal string/karakter, jadi ganti nama
 * TIDAK PERNAH menyentuh isi string atau komentar. Ganti nama metode
 * memakai resolusi pemanggil: definisi + pemanggil di SEMUA berkas yang
 * penerimanya terbukti merujuk ke kelas pemilik (this, variabel yang
 * dideklarasikan bertipe kelas itu, new Kelas(...), Kelas.metode(),
 * pemanggilan tanpa penerima di dalam kelas pemilik/turunannya, serta
 * metode override di kelas turunan). Metode bernama sama di kelas lain
 * TIDAK disentuh.
 *
 * BATAS JUJUR: parser minimal ini memahami struktur kelas/metode/
 * pemanggilan, bukan seluruh bahasa Java: generics kompleks, anotasi
 * berparameter, dan kelas anonim di dalam ekspresi dipindai apa adanya;
 * refleksi (Class.forName, getMethod) dan pemanggilan lewat antarmuka
 * yang tipenya hanya diketahui saat jalan tidak dapat diresolusi dan
 * tidak diubah — selalu periksa pratinjau sebelum menerapkan.
 */
public class RefaktorSemantik {

    /** Satu simbol: definisi kelas/metode/fungsi/variabel. */
    public static class Simbol {
        public String jenis = "";
        public String nama = "";
        public String kelasPemilik = "";
        public String berkas = "";
        public int baris = 0;
        /** Baris akhir definisi (0 bila tidak diketahui). */
        public int barisAkhir = 0;
        /** Tanda tangan parameter, mis. "(int, String)". */
        public String tandaTangan = "";
        /** Bahasa sumber: "java", "kotlin", atau "". */
        public String bahasa = "";
        public String toString() {
            return jenis + " " + nama + " (" + berkas + ":" + baris + ")";
        }
    }

    /** Satu kemunculan pemanggil: berkas + baris + cuplikan. */
    public static class Pemanggil {
        public String berkas = "";
        public int baris = 0;
        public String cuplikan = "";
        public String toString() {
            return berkas + ":" + baris + " " + cuplikan.trim();
        }
    }

    /** Hasil pratinjau / penerapan ganti nama AST lintas berkas. */
    public static class HasilGantiNama {
        public int jumlah = 0;
        public int jumlahBerkas = 0;
        public List<String> perBerkas = new ArrayList<String>();
        public String target = "";
        public String ringkasan() {
            return "Ganti nama " + target + ": " + jumlah + " kemunculan di "
                    + jumlahBerkas + " berkas.";
        }
    }

    // ============================ LEKSER ============================

    private static final int T_IDENT = 1;
    private static final int T_NUM = 2;
    private static final int T_STR = 3;
    private static final int T_CHR = 4;
    private static final int T_SIM = 5;
    private static final int T_EOF = 6;

    /** Satu token beserta posisi baris dan offset karakternya. */
    static class Token {
        int jenis;
        String teks;
        int baris;
        int awal;
        int akhir;
        Token(int j, String t, int b, int a, int e) {
            jenis = j; teks = t; baris = b; awal = a; akhir = e;
        }
    }

    /** Lekser Java/Kotlin: membuang spasi dan komentar; string/char jadi satu token. */
    static class Lekser {
        static List<Token> lex(String isi) {
            List<Token> keluar = new ArrayList<Token>();
            if (isi == null) return keluar;
            int n = isi.length();
            int i = 0;
            int baris = 1;
            while (i < n) {
                char c = isi.charAt(i);
                if (c == '\n') { baris++; i++; continue; }
                if (c == ' ' || c == '\t' || c == '\r' || c == '\f') { i++; continue; }
                if (c == '/' && i + 1 < n && isi.charAt(i + 1) == '/') {
                    while (i < n && isi.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < n && isi.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(isi.charAt(i) == '*' && isi.charAt(i + 1) == '/')) {
                        if (isi.charAt(i) == '\n') baris++;
                        i++;
                    }
                    i = Math.min(n, i + 2);
                    continue;
                }
                int awal = i;
                if (c == '"' || c == '\'') {
                    boolean teks = c == '"';
                    // Blok teks Kotlin/Java """..."""
                    if (teks && i + 2 < n && isi.charAt(i + 1) == '"' && isi.charAt(i + 2) == '"') {
                        i += 3;
                        while (i + 2 < n && !(isi.charAt(i) == '"' && isi.charAt(i + 1) == '"'
                                && isi.charAt(i + 2) == '"')) {
                            if (isi.charAt(i) == '\n') baris++;
                            i++;
                        }
                        i = Math.min(n, i + 3);
                        keluar.add(new Token(T_STR, isi.substring(awal, i), baris, awal, i));
                        continue;
                    }
                    i++;
                    while (i < n && isi.charAt(i) != c && isi.charAt(i) != '\n') {
                        if (isi.charAt(i) == '\\') i++;
                        i++;
                    }
                    if (i < n && isi.charAt(i) == c) i++;
                    keluar.add(new Token(teks ? T_STR : T_CHR,
                            isi.substring(awal, i), baris, awal, i));
                    continue;
                }
                if (Character.isDigit(c)) {
                    while (i < n && (Character.isLetterOrDigit(isi.charAt(i))
                            || isi.charAt(i) == '_' || isi.charAt(i) == '.')) i++;
                    keluar.add(new Token(T_NUM, isi.substring(awal, i), baris, awal, i));
                    continue;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    while (i < n && Character.isJavaIdentifierPart(isi.charAt(i))) i++;
                    keluar.add(new Token(T_IDENT, isi.substring(awal, i), baris, awal, i));
                    continue;
                }
                String tiga = i + 2 < n ? isi.substring(i, i + 3) : "";
                String dua = i + 1 < n ? isi.substring(i, i + 2) : "";
                if ("...".equals(tiga) || ">>>=".equals(tiga)) {
                    i += 3;
                    keluar.add(new Token(T_SIM, tiga, baris, awal, i));
                    continue;
                }
                if ("::".equals(dua) || "->".equals(dua) || "==".equals(dua) || "!=".equals(dua)
                        || "<=".equals(dua) || ">=".equals(dua) || "&&".equals(dua)
                        || "||".equals(dua) || "++".equals(dua) || "--".equals(dua)
                        || "+=".equals(dua) || "-=".equals(dua) || "*=".equals(dua)
                        || "/=".equals(dua) || "%=".equals(dua) || "<<".equals(dua)
                        || ">>".equals(dua) || "&=".equals(dua) || "|=".equals(dua)
                        || "^=".equals(dua) || "?.".equals(dua) || "?:".equals(dua)) {
                    i += 2;
                    keluar.add(new Token(T_SIM, dua, baris, awal, i));
                    continue;
                }
                i++;
                keluar.add(new Token(T_SIM, isi.substring(awal, i), baris, awal, i));
            }
            keluar.add(new Token(T_EOF, "", baris, n, n));
            return keluar;
        }
    }

    // ============================ SIMPUL AST ============================

    /** Unit kompilasi: satu berkas sumber. */
    public static class AstUnit {
        public String berkas = "";
        public String bahasa = "";
        public String paket = "";
        public List<AstTipe> tipe = new ArrayList<AstTipe>();
    }

    /** Deklarasi kelas / interface / enum / object. */
    public static class AstTipe {
        public String jenis = "";
        public String nama = "";
        public String superKelas = "";
        public List<String> antarmuka = new ArrayList<String>();
        public int baris = 0;
        public int barisAkhir = 0;
        public int indeksNama = -1;
        public String berkas = "";
        public List<AstMetode> metode = new ArrayList<AstMetode>();
        public List<AstTipe> dalam = new ArrayList<AstTipe>();
    }

    /** Satu parameter metode/fungsi. */
    public static class AstParam {
        public String tipe = "";
        public String nama = "";
    }

    /** Deklarasi metode / fungsi / konstruktor. */
    public static class AstMetode {
        public String nama = "";
        public String tipeKembali = "";
        public List<AstParam> param = new ArrayList<AstParam>();
        public int baris = 0;
        public int barisAkhir = 0;
        public int indeksNama = -1;
        public String kelasPemilik = "";
        public String berkas = "";
        public boolean konstruktor = false;
        public List<AstPanggilan> panggilan = new ArrayList<AstPanggilan>();
        /** Nama variabel lokal -> tipe sederhananya (termasuk parameter). */
        public Map<String, String> varTipe = new HashMap<String, String>();
    }

    /** Satu pemanggilan metode/fungsi di dalam sebuah metode. */
    public static class AstPanggilan {
        public String nama = "";
        /** Teks penerima: "" (tanpa penerima), "this", "super", nama variabel,
         *  nama kelas (statis), "new Kelas", "(panggilan)" bila tak dapat ditentukan. */
        public String penerima = "";
        public int baris = 0;
        public int indeksNama = -1;
        public String berkas = "";
        public String kelasPemilik = "";
        public String metodePemilik = "";
    }

    private static final Set<String> PENGUBAH = new HashSet<String>();
    private static final Set<String> KATA_KUNCI_PANGGIL = new HashSet<String>();
    static {
        String[] p = {"public", "private", "protected", "static", "final", "abstract",
                "synchronized", "volatile", "transient", "native", "strictfp", "sealed",
                "non-sealed", "open", "override", "suspend", "inline", "lateinit", "data",
                "inner", "external", "tailrec", "operator", "infix", "crossinline",
                "noinline", "reified", "vararg", "const", "companion"};
        for (int i = 0; i < p.length; i++) PENGUBAH.add(p[i]);
        String[] k = {"if", "for", "while", "switch", "catch", "synchronized", "return",
                "throw", "new", "assert", "do", "else", "try", "finally"};
        for (int i = 0; i < k.length; i++) KATA_KUNCI_PANGGIL.add(k[i]);
    }

    // ============================ PARSER ============================

    /** Parser rekursif Java + subset Kotlin di atas token lekser. */
    static class Parser {
        final List<Token> tok;
        final String berkas;
        final String bahasa;
        int pos = 0;

        Parser(List<Token> t, String b, String lang) {
            tok = t; berkas = b; bahasa = lang;
        }

        Token cur() { return tok.get(Math.min(pos, tok.size() - 1)); }
        Token peek(int k) { return tok.get(Math.min(pos + k, tok.size() - 1)); }
        Token next() { Token t = cur(); if (pos < tok.size() - 1) pos++; return t; }
        boolean at(String s) { return cur().jenis != T_EOF && s.equals(cur().teks); }
        boolean atIdent() { return cur().jenis == T_IDENT; }
        boolean eat(String s) { if (at(s)) { next(); return true; } return false; }

        void lewatiPengubah() {
            while (cur().jenis == T_IDENT && PENGUBAH.contains(cur().teks)) next();
            while (at("@")) {
                next();
                if (atIdent()) next();
                if (at("(")) lewatiSeimbang("(", ")");
            }
        }

        /** Lewati token seimbang dari pembuka yang sedang aktif. */
        void lewatiSeimbang(String buka, String tutup) {
            if (!at(buka)) return;
            int dalam = 0;
            while (cur().jenis != T_EOF) {
                if (at(buka)) dalam++;
                else if (at(tutup)) { dalam--; next(); if (dalam == 0) return; continue; }
                next();
            }
        }

        /** Indeks token penutup yang seimbang dengan pembuka di indeks i (atau -1). */
        int cariTutup(int i, String buka, String tutup) {
            int dalam = 0;
            for (int j = i; j < tok.size(); j++) {
                if (buka.equals(tok.get(j).teks)) dalam++;
                else if (tutup.equals(tok.get(j).teks)) {
                    dalam--;
                    if (dalam == 0) return j;
                }
            }
            return -1;
        }

        AstUnit parseUnit() {
            AstUnit u = new AstUnit();
            u.berkas = berkas;
            u.bahasa = bahasa;
            if ("kotlin".equals(bahasa)) { parseKotlin(u); return u; }
            while (cur().jenis != T_EOF) {
                if (at("package")) {
                    next();
                    StringBuilder p = new StringBuilder();
                    while (!at(";") && cur().jenis != T_EOF) {
                        if (cur().jenis == T_IDENT) p.append(cur().teks);
                        else if (at(".")) p.append(".");
                        next();
                    }
                    u.paket = p.toString();
                    eat(";");
                } else if (at("import")) {
                    while (!at(";") && cur().jenis != T_EOF) next();
                    eat(";");
                } else if (at(";")) {
                    next();
                } else {
                    AstTipe t = parseTipe(null);
                    if (t != null) u.tipe.add(t);
                    else if (cur().jenis != T_EOF) next();
                }
            }
            return u;
        }

        AstTipe parseTipe(AstTipe induk) {
            lewatiPengubah();
            if (!at("class") && !at("interface") && !at("enum") && !at("@interface")) return null;
            AstTipe t = new AstTipe();
            t.jenis = next().teks;
            t.berkas = berkas;
            if (!atIdent()) return null;
            t.indeksNama = pos;
            t.nama = next().teks;
            t.baris = tok.get(t.indeksNama).baris;
            if (at("<")) lewatiSeimbang("<", ">");
            // extends / implements
            while (at("extends") || at("implements")) {
                boolean indukKelas = at("extends");
                next();
                while (cur().jenis != T_EOF && !at("{") && !at("implements")
                        && !at("extends") && !at("where")) {
                    if (atIdent()) {
                        String nama = next().teks;
                        if (indukKelas && t.superKelas.length() == 0) t.superKelas = nama;
                        else t.antarmuka.add(nama);
                        if (at("<")) lewatiSeimbang("<", ">");
                    } else if (at(",")) {
                        next();
                    } else {
                        next();
                    }
                }
            }
            if (!at("{")) return t;
            int tutup = cariTutup(pos, "{", "}");
            if (tutup < 0) return t;
            next(); // {
            while (cur().jenis != T_EOF && pos < tutup) {
                parseAnggota(t, tutup);
            }
            t.barisAkhir = tok.get(Math.min(tutup, tok.size() - 1)).baris;
            pos = tutup + 1;
            return t;
        }

        void parseAnggota(AstTipe t, int batasTipe) {
            if (at(";")) { next(); return; }
            if (at("{")) { // blok inisialisasi: lewati
                int tutup = cariTutup(pos, "{", "}");
                pos = tutup < 0 ? tok.size() - 1 : tutup + 1;
                return;
            }
            int awal = pos;
            lewatiPengubah();
            if (at("class") || at("interface") || at("enum") || at("@interface")) {
                AstTipe d = parseTipe(t);
                if (d != null) t.dalam.add(d);
                return;
            }
            // Kumpulkan tajuk sampai { atau ; pada kedalaman kurung 0.
            int dalamKurung = 0;
            int i = pos;
            int bukaKurung = -1;
            while (i < tok.size() && i < batasTipe) {
                String s = tok.get(i).teks;
                if ("(".equals(s)) { if (dalamKurung == 0 && bukaKurung < 0) bukaKurung = i; dalamKurung++; }
                else if (")".equals(s)) dalamKurung--;
                else if (dalamKurung == 0 && ("{".equals(s) || ";".equals(s))) break;
                i++;
            }
            if (i >= tok.size()) { pos = tok.size() - 1; return; }
            boolean adaBadan = "{".equals(tok.get(i).teks);
            if (bukaKurung < 0) {
                // Medan (field): lewati sampai ; atau {
                pos = adaBadan ? i : i + 1;
                if (adaBadan) {
                    int tutup = cariTutup(pos, "{", "}");
                    pos = tutup < 0 ? tok.size() - 1 : tutup + 1;
                }
                return;
            }
            // Metode / konstruktor: nama = IDENT terakhir sebelum '('.
            // Bila ada '=' sebelum '(' pada kedalaman 0, ini medan dengan
            // pemanggilan di penginisialisasi, bukan deklarasi metode.
            boolean adaSamaDengan = false;
            for (int j = awal; j < bukaKurung; j++) {
                if ("=".equals(tok.get(j).teks)) adaSamaDengan = true;
            }
            if (adaSamaDengan) {
                pos = i + 1;
                return;
            }
            int idxNama = -1;
            for (int j = bukaKurung - 1; j >= awal; j--) {
                if (tok.get(j).jenis == T_IDENT) { idxNama = j; break; }
            }
            if (idxNama < 0) { pos = i + 1; return; }
            AstMetode m = new AstMetode();
            m.nama = tok.get(idxNama).teks;
            m.indeksNama = idxNama;
            m.baris = tok.get(idxNama).baris;
            m.kelasPemilik = t.nama;
            m.berkas = berkas;
            StringBuilder kembali = new StringBuilder();
            for (int j = awal; j < idxNama; j++) {
                if (tok.get(j).jenis == T_IDENT && PENGUBAH.contains(tok.get(j).teks)) continue;
                if (tok.get(j).jenis == T_IDENT || ".".equals(tok.get(j).teks)
                        || "<".equals(tok.get(j).teks) || ">".equals(tok.get(j).teks)
                        || "[".equals(tok.get(j).teks) || "]".equals(tok.get(j).teks)) {
                    if (kembali.length() > 0) kembali.append(" ");
                    kembali.append(tok.get(j).teks);
                }
            }
            m.tipeKembali = kembali.toString().trim();
            m.konstruktor = m.tipeKembali.length() == 0;
            int tutupKurung = cariTutup(bukaKurung, "(", ")");
            if (tutupKurung > 0) parseParam(m, bukaKurung + 1, tutupKurung);
            if (!adaBadan) { pos = i + 1; t.metode.add(m); return; }
            int tutupBadan = cariTutup(i, "{", "}");
            if (tutupBadan < 0) { pos = tok.size() - 1; t.metode.add(m); return; }
            m.barisAkhir = tok.get(tutupBadan).baris;
            pindaiBadan(m, i + 1, tutupBadan);
            t.metode.add(m);
            pos = tutupBadan + 1;
        }

        void parseParam(AstMetode m, int dari, int sampai) {
            int awal = dari;
            int dalam = 0;
            for (int j = dari; j <= sampai; j++) {
                String s = j < sampai ? tok.get(j).teks : ",";
                if ("<".equals(s)) dalam++;
                else if (">".equals(s)) dalam--;
                else if (",".equals(s) && dalam == 0) {
                    tambahParam(m, awal, j);
                    awal = j + 1;
                }
            }
        }

        void tambahParam(AstMetode m, int dari, int sampai) {
            // Nama parameter = IDENT terakhir; sisanya tipe.
            int idxNama = -1;
            for (int j = sampai - 1; j >= dari; j--) {
                if (tok.get(j).jenis == T_IDENT && !PENGUBAH.contains(tok.get(j).teks)) {
                    idxNama = j;
                    break;
                }
            }
            if (idxNama < 0) return;
            AstParam p = new AstParam();
            p.nama = tok.get(idxNama).teks;
            StringBuilder tipe = new StringBuilder();
            for (int j = dari; j < idxNama; j++) {
                if (tok.get(j).jenis == T_IDENT || ".".equals(tok.get(j).teks)
                        || "<".equals(tok.get(j).teks) || ">".equals(tok.get(j).teks)
                        || "[".equals(tok.get(j).teks) || "]".equals(tok.get(j).teks)
                        || "?".equals(tok.get(j).teks)) {
                    if (tipe.length() > 0) tipe.append(" ");
                    tipe.append(tok.get(j).teks);
                }
            }
            p.tipe = tipe.toString().trim().replace("...", "[]");
            if (p.tipe.length() == 0) p.tipe = "Object";
            m.param.add(p);
            m.varTipe.put(p.nama, p.tipe);
        }

        /** Pindai badan metode: deklarasi variabel lokal + semua pemanggilan. */
        void pindaiBadan(AstMetode m, int dari, int sampai) {
            // Deklarasi variabel: IDENT [][ ] IDENT (=|;|,) — termasuk for(...)
            for (int j = dari; j < sampai; j++) {
                if (tok.get(j).jenis != T_IDENT) continue;
                int k = j + 1;
                while (k < sampai && "[".equals(tok.get(k).teks)) k += 2;
                if (k < sampai && tok.get(k).jenis == T_IDENT
                        && k + 1 < sampai
                        && ("=".equals(tok.get(k + 1).teks) || ";".equals(tok.get(k + 1).teks)
                        || ",".equals(tok.get(k + 1).teks))) {
                    String tipe = tok.get(j).teks;
                    String nama = tok.get(k).teks;
                    if (!KATA_KUNCI_PANGGIL.contains(tipe) && !"return".equals(tipe)) {
                        m.varTipe.put(nama, tipe);
                    }
                }
            }
            // Pemanggilan: IDENT '(' yang bukan kata kunci.
            for (int j = dari; j < sampai; j++) {
                if (tok.get(j).jenis != T_IDENT) continue;
                if (j + 1 >= sampai || !"(".equals(tok.get(j + 1).teks)) continue;
                String nama = tok.get(j).teks;
                if (KATA_KUNCI_PANGGIL.contains(nama)) continue;
                if (("this".equals(nama) || "super".equals(nama))
                        && (j == dari || !".".equals(tok.get(j - 1).teks))) continue;
                AstPanggilan p = new AstPanggilan();
                p.nama = nama;
                p.indeksNama = j;
                p.baris = tok.get(j).baris;
                p.berkas = berkas;
                p.kelasPemilik = m.kelasPemilik;
                p.metodePemilik = m.nama;
                p.penerima = penerimaSebelum(j);
                m.panggilan.add(p);
            }
        }

        /** Teks penerima sebelum pemanggilan di indeks token j (IDENT '('). */
        String penerimaSebelum(int j) {
            if (j >= 2 && ".".equals(tok.get(j - 1).teks)) {
                // Rantai a.b.c sebelum titik: berjalan mundur.
                int k = j - 2;
                // Kasus new X().m(...) : sebelum titik ada ')'
                if (")".equals(tok.get(k).teks)) {
                    int buka = cariBukaPasangan(k, "(", ")");
                    if (buka > 1 && "new".equals(tok.get(buka - 2).teks)
                            && tok.get(buka - 1).jenis == T_IDENT) {
                        return "new " + tok.get(buka - 1).teks;
                    }
                    return "(panggilan)";
                }
                StringBuilder r = new StringBuilder();
                while (k >= 0 && (tok.get(k).jenis == T_IDENT || ".".equals(tok.get(k).teks))) {
                    r.insert(0, tok.get(k).teks);
                    k--;
                }
                return r.toString();
            }
            return "";
        }

        int cariBukaPasangan(int tutup, String buka, String tutupS) {
            int dalam = 0;
            for (int j = tutup; j >= 0; j--) {
                if (tutupS.equals(tok.get(j).teks)) dalam++;
                else if (buka.equals(tok.get(j).teks)) {
                    dalam--;
                    if (dalam == 0) return j;
                }
            }
            return -1;
        }

        // ---------------- Kotlin (subset) ----------------

        /** Pindai Kotlin: class/object, fun (termasuk extension), val/var, pemanggilan. */
        void parseKotlin(AstUnit u) {
            // Tumpukan bingkai tipe berdasarkan kedalaman kurung kurawal.
            List<AstTipe> tumpukan = new ArrayList<AstTipe>();
            List<Integer> kedalaman = new ArrayList<Integer>();
            int dalam = 0;
            int i = 0;
            while (i < tok.size()) {
                Token t = tok.get(i);
                if (t.jenis == T_EOF) break;
                if ("{".equals(t.teks)) { dalam++; i++; continue; }
                if ("}".equals(t.teks)) {
                    dalam--;
                    while (!kedalaman.isEmpty()
                            && kedalaman.get(kedalaman.size() - 1) >= dalam) {
                        AstTipe tt = tumpukan.remove(tumpukan.size() - 1);
                        kedalaman.remove(kedalaman.size() - 1);
                        tt.barisAkhir = t.baris;
                    }
                    i++;
                    continue;
                }
                if (t.jenis == T_IDENT && ("class".equals(t.teks) || "object".equals(t.teks)
                        || "interface".equals(t.teks))) {
                    if (i + 1 < tok.size() && tok.get(i + 1).jenis == T_IDENT) {
                        AstTipe tip = new AstTipe();
                        tip.jenis = t.teks;
                        tip.nama = tok.get(i + 1).teks;
                        tip.indeksNama = i + 1;
                        tip.baris = tok.get(i + 1).baris;
                        tip.berkas = berkas;
                        // Superclass setelah ':' atau '(' konstruktor primer.
                        int j = i + 2;
                        if (j < tok.size() && ":".equals(tok.get(j).teks)
                                && j + 1 < tok.size() && tok.get(j + 1).jenis == T_IDENT) {
                            tip.superKelas = tok.get(j + 1).teks;
                        }
                        if (tumpukan.isEmpty()) u.tipe.add(tip);
                        else tumpukan.get(tumpukan.size() - 1).dalam.add(tip);
                        tumpukan.add(tip);
                        kedalaman.add(dalam);
                        i += 2;
                        continue;
                    }
                }
                if (t.jenis == T_IDENT && "fun".equals(t.teks)) {
                    AstMetode m = new AstMetode();
                    m.berkas = berkas;
                    m.kelasPemilik = tumpukan.isEmpty() ? ""
                            : tumpukan.get(tumpukan.size() - 1).nama;
                    int j = i + 1;
                    // Anotasi / pengubah sesudah fun dilewati.
                    while (j < tok.size() && tok.get(j).jenis == T_IDENT
                            && PENGUBAH.contains(tok.get(j).teks)) j++;
                    // Receiver extension: Tipe.nama(
                    if (j + 2 < tok.size() && tok.get(j).jenis == T_IDENT
                            && ".".equals(tok.get(j + 1).teks)
                            && tok.get(j + 2).jenis == T_IDENT) {
                        m.kelasPemilik = tok.get(j).teks;
                        m.indeksNama = j + 2;
                        m.nama = tok.get(j + 2).teks;
                        j += 3;
                    } else if (j < tok.size() && tok.get(j).jenis == T_IDENT) {
                        m.indeksNama = j;
                        m.nama = tok.get(j).teks;
                        j++;
                    } else {
                        i++;
                        continue;
                    }
                    m.baris = tok.get(m.indeksNama).baris;
                    if (j < tok.size() && "<".equals(tok.get(j).teks)) {
                        int tutup = cariTutup(j, "<", ">");
                        if (tutup > 0) j = tutup + 1;
                    }
                    if (j < tok.size() && "(".equals(tok.get(j).teks)) {
                        int tutup = cariTutup(j, "(", ")");
                        if (tutup > 0) { parseParamKt(m, j + 1, tutup); j = tutup + 1; }
                    }
                    // Tipe kembali setelah ':'.
                    if (j < tok.size() && ":".equals(tok.get(j).teks) && !"{".equals(tok.get(j).teks)) {
                        j++;
                        StringBuilder tb = new StringBuilder();
                        while (j < tok.size() && !"{".equals(tok.get(j).teks)
                                && !"=".equals(tok.get(j).teks) && tok.get(j).baris == m.baris) {
                            if (tok.get(j).jenis == T_IDENT || "?".equals(tok.get(j).teks)
                                    || ".".equals(tok.get(j).teks)) {
                                if (tb.length() > 0) tb.append(" ");
                                tb.append(tok.get(j).teks);
                            }
                            j++;
                        }
                        m.tipeKembali = tb.toString().trim();
                    }
                    if (j < tok.size() && "{".equals(tok.get(j).teks)) {
                        int tutup = cariTutup(j, "{", "}");
                        if (tutup > 0) {
                            m.barisAkhir = tok.get(tutup).baris;
                            pindaiBadanKt(m, j + 1, tutup);
                            i = tutup + 1;
                        } else {
                            i = j + 1;
                        }
                    } else {
                        i = j;
                    }
                    if (tumpukan.isEmpty()) {
                        // Fungsi tingkat berkas: wadahi dalam tipe semu berkas.
                        AstTipe wadah = null;
                        for (int k = 0; k < u.tipe.size(); k++) {
                            if ("".equals(u.tipe.get(k).nama)) wadah = u.tipe.get(k);
                        }
                        if (wadah == null) {
                            wadah = new AstTipe();
                            wadah.jenis = "berkas";
                            wadah.nama = "";
                            wadah.berkas = berkas;
                            u.tipe.add(wadah);
                        }
                        wadah.metode.add(m);
                    } else {
                        tumpukan.get(tumpukan.size() - 1).metode.add(m);
                    }
                    continue;
                }
                i++;
            }
        }

        void parseParamKt(AstMetode m, int dari, int sampai) {
            int awal = dari;
            int dalam = 0;
            for (int j = dari; j <= sampai; j++) {
                String s = j < sampai ? tok.get(j).teks : ",";
                if ("<".equals(s) || "(".equals(s)) dalam++;
                else if (">".equals(s) || ")".equals(s)) dalam--;
                else if (",".equals(s) && dalam == 0) {
                    tambahParamKt(m, awal, j);
                    awal = j + 1;
                }
            }
        }

        void tambahParamKt(AstMetode m, int dari, int sampai) {
            int idxNama = -1;
            for (int j = dari; j < sampai; j++) {
                if (tok.get(j).jenis == T_IDENT && !PENGUBAH.contains(tok.get(j).teks)
                        && !"vararg".equals(tok.get(j).teks)) { idxNama = j; break; }
            }
            if (idxNama < 0) return;
            AstParam p = new AstParam();
            p.nama = tok.get(idxNama).teks;
            // Tipe sesudah ':'.
            StringBuilder tipe = new StringBuilder();
            for (int j = idxNama + 1; j < sampai; j++) {
                if (":".equals(tok.get(j).teks)) continue;
                if (tok.get(j).jenis == T_IDENT || "?".equals(tok.get(j).teks)
                        || ".".equals(tok.get(j).teks)) {
                    if (tipe.length() > 0) tipe.append(" ");
                    tipe.append(tok.get(j).teks);
                }
                if ("=".equals(tok.get(j).teks)) break;
            }
            p.tipe = tipe.toString().trim();
            if (p.tipe.length() == 0) p.tipe = "Object";
            m.param.add(p);
            m.varTipe.put(p.nama, p.tipe);
        }

        void pindaiBadanKt(AstMetode m, int dari, int sampai) {
            // val/var nama [: Tipe] (=|;|,)
            for (int j = dari; j < sampai; j++) {
                if (tok.get(j).jenis == T_IDENT
                        && ("val".equals(tok.get(j).teks) || "var".equals(tok.get(j).teks))
                        && j + 1 < sampai && tok.get(j + 1).jenis == T_IDENT) {
                    String nama = tok.get(j + 1).teks;
                    String tipe = "Object";
                    if (j + 2 < sampai && ":".equals(tok.get(j + 2).teks)
                            && j + 3 < sampai && tok.get(j + 3).jenis == T_IDENT) {
                        tipe = tok.get(j + 3).teks;
                    }
                    m.varTipe.put(nama, tipe);
                }
            }
            pindaiBadan(m, dari, sampai);
        }
    }

    // ============================ API AST ============================

    /** Urai satu berkas menjadi AST (Java atau Kotlin subset). */
    public static AstUnit pindaiAst(String namaBerkas, String isi) {
        String bahasa = "kotlin";
        if (namaBerkas != null && namaBerkas.endsWith(".java")) bahasa = "java";
        else if (namaBerkas != null && namaBerkas.endsWith(".kt")) bahasa = "kotlin";
        else bahasa = "java";
        List<Token> tok = Lekser.lex(isi);
        Parser p = new Parser(tok, namaBerkas, bahasa);
        return p.parseUnit();
    }

    private static void simbolDariTipe(AstTipe t, String berkas, String bahasa,
                                       List<Simbol> keluar) {
        if (t.nama.length() > 0) {
            Simbol s = new Simbol();
            s.jenis = "kelas";
            s.nama = t.nama;
            s.kelasPemilik = "";
            s.berkas = berkas;
            s.baris = t.baris;
            s.barisAkhir = t.barisAkhir;
            s.bahasa = bahasa;
            keluar.add(s);
        }
        for (int i = 0; i < t.metode.size(); i++) {
            AstMetode m = t.metode.get(i);
            Simbol s = new Simbol();
            s.jenis = m.konstruktor ? "konstruktor" : ("kotlin".equals(bahasa) ? "fungsi" : "metode");
            s.nama = m.nama;
            s.kelasPemilik = t.nama;
            s.berkas = berkas;
            s.baris = m.baris;
            s.barisAkhir = m.barisAkhir;
            s.bahasa = bahasa;
            StringBuilder tt = new StringBuilder("(");
            for (int j = 0; j < m.param.size(); j++) {
                if (j > 0) tt.append(", ");
                tt.append(m.param.get(j).tipe);
            }
            tt.append(")");
            s.tandaTangan = tt.toString();
            keluar.add(s);
        }
        for (int i = 0; i < t.dalam.size(); i++) {
            simbolDariTipe(t.dalam.get(i), berkas, bahasa, keluar);
        }
    }

    /** Daftar simbol dari sebuah AST. */
    public static List<Simbol> simbolDariAst(AstUnit unit) {
        List<Simbol> hasil = new ArrayList<Simbol>();
        if (unit == null) return hasil;
        for (int i = 0; i < unit.tipe.size(); i++) {
            simbolDariTipe(unit.tipe.get(i), unit.berkas, unit.bahasa, hasil);
        }
        return hasil;
    }

    private static final Pattern KELAS_LAMA = Pattern.compile(
            "\\b(?:class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern METODE_JAVA_LAMA = Pattern.compile(
            "\\b(?:public|private|protected|static|final|synchronized|abstract|void|int|long|double|float|boolean|String|char|byte|short|\\w+)\\b[^;\\{\\}]*?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern FUNGSI_PY = Pattern.compile(
            "^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern FUNGSI_KT_LAMA = Pattern.compile(
            "\\bfun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    /**
     * Pindai satu berkas kode menjadi daftar simbol. Java/Kotlin memakai
     * parser AST; bahasa lain (Python/JS/Lua/XML) memakai pemindai pola
     * lama karena parser AST-nya belum ada.
     */
    public static List<Simbol> pindaiBerkas(String namaBerkas, String isi) {
        if (isi == null) return new ArrayList<Simbol>();
        if (namaBerkas != null && (namaBerkas.endsWith(".java") || namaBerkas.endsWith(".kt"))) {
            return simbolDariAst(pindaiAst(namaBerkas, isi));
        }
        return pindaiBerkasPola(namaBerkas, isi);
    }

    /** Pemindai pola lama, dipakai untuk bahasa tanpa parser AST. */
    static List<Simbol> pindaiBerkasPola(String namaBerkas, String isi) {
        List<Simbol> hasil = new ArrayList<Simbol>();
        if (isi == null) return hasil;
        String[] baris = isi.split("\n");
        String kelasKini = "";
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            String rapi = b.trim();
            if (rapi.startsWith("//") || rapi.startsWith("#") || rapi.startsWith("*")) continue;
            Matcher m = KELAS_LAMA.matcher(b);
            if (m.find()) {
                kelasKini = m.group(1);
                Simbol s = new Simbol();
                s.jenis = "kelas";
                s.nama = kelasKini;
                s.kelasPemilik = "";
                s.berkas = namaBerkas;
                s.baris = i + 1;
                hasil.add(s);
                continue;
            }
            Matcher f = null;
            String jenis = "metode";
            if (namaBerkas != null && namaBerkas.endsWith(".py")) {
                f = FUNGSI_PY.matcher(b);
                jenis = "fungsi";
            } else if (namaBerkas != null && namaBerkas.endsWith(".kt")) {
                f = FUNGSI_KT_LAMA.matcher(b);
                jenis = "fungsi";
            } else {
                f = METODE_JAVA_LAMA.matcher(b);
            }
            if (f != null && f.find()) {
                String nama = f.group(1);
                if ("if".equals(nama) || "for".equals(nama) || "while".equals(nama)
                        || "switch".equals(nama) || "return".equals(nama)
                        || "catch".equals(nama)) continue;
                Simbol s = new Simbol();
                s.jenis = jenis;
                s.nama = nama;
                s.kelasPemilik = kelasKini;
                s.berkas = namaBerkas;
                s.baris = i + 1;
                hasil.add(s);
            }
        }
        return hasil;
    }

    /** Pindai seluruh berkas kode di bawah folder (rekursif, 6 ekstensi). */
    public static List<Simbol> pindaiProyek(File dir) {
        List<Simbol> hasil = new ArrayList<Simbol>();
        if (dir == null || !dir.isDirectory()) return hasil;
        pindaiKe(dir, dir, hasil);
        return hasil;
    }

    private static void pindaiKe(File akar, File kini, List<Simbol> keluar) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) pindaiKe(akar, f, keluar);
            } else if (akhirKode(f.getName())) {
                String isi = bacaTeks(f);
                if (isi == null) continue;
                String relatif = f.getAbsolutePath().substring(
                        akar.getAbsolutePath().length() + 1);
                keluar.addAll(pindaiBerkas(relatif, isi));
            }
        }
    }

    /** True bila nama berkas adalah kode sumber yang dipindai. */
    public static boolean akhirKode(String nama) {
        if (nama == null) return false;
        return nama.endsWith(".java") || nama.endsWith(".kt")
                || nama.endsWith(".py") || nama.endsWith(".js")
                || nama.endsWith(".lua") || nama.endsWith(".xml");
    }

    static String bacaTeks(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean tulisTeks(File f, String isi) {
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            try {
                out.write(isi.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Cari definisi simbol bernama tertentu (pertama yang cocok). */
    public static Simbol cariDefinisi(List<Simbol> semua, String nama) {
        if (semua == null || nama == null) return null;
        for (int i = 0; i < semua.size(); i++) {
            if (nama.equals(semua.get(i).nama)) return semua.get(i);
        }
        return null;
    }

    /**
     * Cari definisi dari AST proyek. Nama boleh berbentuk "Kelas.metode"
     * (bila satu nama metode ada di beberapa kelas) atau nama polos.
     */
    public static Simbol cariDefinisiAst(List<AstUnit> semuaUnit, String nama) {
        List<Simbol> semua = cariDefinisiSemuaAst(semuaUnit, nama);
        return semua.isEmpty() ? null : semua.get(0);
    }

    /** Semua definisi yang cocok (untuk melaporkan ambiguitas nama). */
    public static List<Simbol> cariDefinisiSemuaAst(List<AstUnit> semuaUnit, String nama) {
        List<Simbol> hasil = new ArrayList<Simbol>();
        if (semuaUnit == null || nama == null) return hasil;
        String kelas = null;
        String cari = nama;
        int titik = nama.lastIndexOf('.');
        if (titik > 0) {
            kelas = nama.substring(0, titik);
            cari = nama.substring(titik + 1);
        }
        for (int i = 0; i < semuaUnit.size(); i++) {
            List<Simbol> simbol = simbolDariAst(semuaUnit.get(i));
            for (int j = 0; j < simbol.size(); j++) {
                Simbol s = simbol.get(j);
                if (!cari.equals(s.nama)) continue;
                if (kelas != null && !kelas.equals(s.kelasPemilik)) continue;
                hasil.add(s);
            }
        }
        return hasil;
    }

    /** Kumpulkan AST semua berkas Java/Kotlin di bawah folder. */
    public static List<AstUnit> kumpulkanAstProyek(File dir) {
        List<AstUnit> hasil = new ArrayList<AstUnit>();
        if (dir == null || !dir.isDirectory()) return hasil;
        kumpulkanAstKe(dir, dir, hasil);
        return hasil;
    }

    private static void kumpulkanAstKe(File akar, File kini, List<AstUnit> keluar) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) kumpulkanAstKe(akar, f, keluar);
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                String isi = bacaTeks(f);
                if (isi == null) continue;
                String relatif = f.getAbsolutePath().substring(
                        akar.getAbsolutePath().length() + 1);
                keluar.add(pindaiAst(relatif, isi));
            }
        }
    }

    /** Cari semua pemanggil AST dari sebuah simbol (lintas berkas). */
    public static List<Pemanggil> cariPemanggilAst(List<AstUnit> semuaUnit, Simbol target) {
        List<Pemanggil> hasil = new ArrayList<Pemanggil>();
        if (semuaUnit == null || target == null) return hasil;
        for (int i = 0; i < semuaUnit.size(); i++) {
            AstUnit u = semuaUnit.get(i);
            for (int j = 0; j < u.tipe.size(); j++) {
                hasil.addAll(pemanggilDariTipe(u.tipe.get(j), target, u));
            }
        }
        return hasil;
    }

    private static List<Pemanggil> pemanggilDariTipe(AstTipe t, Simbol target, AstUnit u) {
        List<Pemanggil> hasil = new ArrayList<Pemanggil>();
        for (int i = 0; i < t.metode.size(); i++) {
            AstMetode m = t.metode.get(i);
            for (int j = 0; j < m.panggilan.size(); j++) {
                AstPanggilan p = m.panggilan.get(j);
                if (!target.nama.equals(p.nama)) continue;
                // Baris deklarasi tidak pernah tercatat sebagai pemanggilan
                // (pindaiBadan hanya memindai badan metode), jadi tidak
                // perlu pengecualian di sini.
                Pemanggil k = new Pemanggil();
                k.berkas = u.berkas;
                k.baris = p.baris;
                k.cuplikan = (p.penerima.length() > 0 ? p.penerima + "." : "")
                        + p.nama + "() di " + m.nama;
                hasil.add(k);
            }
        }
        for (int i = 0; i < t.dalam.size(); i++) {
            hasil.addAll(pemanggilDariTipe(t.dalam.get(i), target, u));
        }
        return hasil;
    }

    /**
     * Cari semua pemanggil metode/fungsi: baris yang memanggil
     * nama(...) di luar baris definisinya sendiri (pemindai pola lama,
     * dipertahankan untuk kompatibilitas).
     */
    public static List<Pemanggil> cariPemanggil(List<String[]> berkasIsi, Simbol target) {
        List<Pemanggil> hasil = new ArrayList<Pemanggil>();
        if (berkasIsi == null || target == null) return hasil;
        Pattern p = Pattern.compile("\\b" + Pattern.quote(target.nama) + "\\s*\\(");
        for (int i = 0; i < berkasIsi.size(); i++) {
            String namaBerkas = berkasIsi.get(i)[0];
            String isi = berkasIsi.get(i)[1];
            if (isi == null) continue;
            String[] baris = isi.split("\n");
            for (int j = 0; j < baris.length; j++) {
                if (namaBerkas.equals(target.berkas) && j + 1 == target.baris) continue;
                if (p.matcher(baris[j]).find()) {
                    Pemanggil k = new Pemanggil();
                    k.berkas = namaBerkas;
                    k.baris = j + 1;
                    k.cuplikan = baris[j].trim();
                    hasil.add(k);
                }
            }
        }
        return hasil;
    }

    // ============================ GANTI NAMA AST LINTAS BERKAS ============================

    private static void kumpulkanTipeRekursif(AstTipe t, List<AstTipe> keluar) {
        keluar.add(t);
        for (int i = 0; i < t.dalam.size(); i++) kumpulkanTipeRekursif(t.dalam.get(i), keluar);
    }

    private static List<AstTipe> semuaTipe(AstUnit u) {
        List<AstTipe> hasil = new ArrayList<AstTipe>();
        for (int i = 0; i < u.tipe.size(); i++) kumpulkanTipeRekursif(u.tipe.get(i), hasil);
        return hasil;
    }

    /** Peta nama tipe -> nama superclass (untuk mengenali kelas turunan). */
    private static Map<String, String> petaSuper(List<AstUnit> units) {
        Map<String, String> peta = new HashMap<String, String>();
        for (int i = 0; i < units.size(); i++) {
            List<AstTipe> tipe = semuaTipe(units.get(i));
            for (int j = 0; j < tipe.size(); j++) {
                if (tipe.get(j).nama.length() > 0) {
                    peta.put(tipe.get(j).nama, tipe.get(j).superKelas);
                }
            }
        }
        return peta;
    }

    private static Set<String> kelasTurunan(Map<String, String> petaSuper, String pemilik) {
        Set<String> hasil = new HashSet<String>();
        boolean berubah = true;
        while (berubah) {
            berubah = false;
            for (Map.Entry<String, String> e : petaSuper.entrySet()) {
                if (pemilik.equals(e.getValue()) || hasil.contains(e.getValue())) {
                    if (!e.getKey().equals(pemilik) && hasil.add(e.getKey())) berubah = true;
                }
            }
        }
        return hasil;
    }

    /**
     * Apakah pemanggilan ini merujuk ke metode milik kelas pemilik?
     * Aturan: tanpa penerima / this di dalam kelas pemilik atau
     * turunannya; super bila superclass-nya pemilik; Kelas.metode();
     * new Kelas(...).metode(); variabel yang dideklarasikan bertipe
     * kelas pemilik/turunannya. Selain itu TIDAK diganti.
     */
    static boolean panggilanMilikKelas(AstPanggilan p, AstMetode m, String pemilik,
                                       Set<String> turunan, Map<String, String> petaSuper,
                                       String berkasDefinisi) {
        if (p.penerima.length() == 0 || "this".equals(p.penerima)) {
            if (pemilik.length() == 0) return p.berkas.equals(berkasDefinisi);
            return pemilik.equals(m.kelasPemilik) || turunan.contains(m.kelasPemilik);
        }
        if ("super".equals(p.penerima)) {
            String superKelas = petaSuper.get(m.kelasPemilik);
            return pemilik.equals(superKelas);
        }
        if (pemilik.equals(p.penerima)) return true;
        if (p.penerima.startsWith("new ")) {
            String tipe = p.penerima.substring(4).trim();
            return pemilik.equals(tipe) || turunan.contains(tipe);
        }
        if (p.penerima.indexOf('.') < 0 && !"(panggilan)".equals(p.penerima)) {
            String tipe = m.varTipe.get(p.penerima);
            if (tipe != null) {
                String sederhana = tipe;
                int titik = sederhana.lastIndexOf('.');
                if (titik >= 0) sederhana = sederhana.substring(titik + 1);
                int sudut = sederhana.indexOf('<');
                if (sudut >= 0) sederhana = sederhana.substring(0, sudut);
                sederhana = sederhana.replace("[]", "").trim();
                return pemilik.equals(sederhana) || turunan.contains(sederhana);
            }
        }
        return false;
    }

    private static HasilGantiNama gantiNamaAstDalam(File dir, String kelasPemilik,
                                                    String lama, String baru, boolean tulis) {
        HasilGantiNama hasil = new HasilGantiNama();
        hasil.target = lama;
        if (dir == null || lama == null || baru == null || lama.length() == 0
                || lama.equals(baru)) {
            return hasil;
        }
        List<AstUnit> units = kumpulkanAstProyek(dir);
        String kunci = (kelasPemilik != null && kelasPemilik.length() > 0)
                ? kelasPemilik + "." + lama : lama;
        List<Simbol> definisi = cariDefinisiSemuaAst(units, kunci);
        if (definisi.isEmpty()) return hasil;
        boolean gantiKelas = false;
        Simbol target = null;
        for (int i = 0; i < definisi.size(); i++) {
            if ("kelas".equals(definisi.get(i).jenis)) { gantiKelas = true; target = definisi.get(i); }
        }
        if (target == null) target = definisi.get(0);
        if (kelasPemilik != null && kelasPemilik.length() > 0) gantiKelas = false;
        String pemilik = "kelas".equals(target.jenis) ? "" : target.kelasPemilik;
        Map<String, String> petaSuper = petaSuper(units);
        Set<String> turunan = kelasTurunan(petaSuper, pemilik);
        hasil.target = (pemilik.length() > 0 ? pemilik + "." : "") + lama
                + ("kelas".equals(target.jenis) ? " (kelas)" : " (metode)");
        for (int ui = 0; ui < units.size(); ui++) {
            AstUnit u = units.get(ui);
            File f = new File(dir, u.berkas);
            String isi = bacaTeks(f);
            if (isi == null) continue;
            List<Token> tok = Lekser.lex(isi);
            Set<Integer> ganti = new HashSet<Integer>();
            List<AstTipe> tipe = semuaTipe(u);
            if (gantiKelas) {
                for (int i = 0; i < tipe.size(); i++) {
                    if (lama.equals(tipe.get(i).nama)) ganti.add(Integer.valueOf(tipe.get(i).indeksNama));
                    for (int j = 0; j < tipe.get(i).metode.size(); j++) {
                        AstMetode m = tipe.get(i).metode.get(j);
                        if (m.konstruktor && lama.equals(m.nama)) {
                            ganti.add(Integer.valueOf(m.indeksNama));
                        }
                    }
                }
                for (int i = 0; i < tok.size(); i++) {
                    Token t = tok.get(i);
                    if (t.jenis != T_IDENT || !lama.equals(t.teks)) continue;
                    if (ganti.contains(Integer.valueOf(i))) continue;
                    boolean panggilanMetode = i + 1 < tok.size() && "(".equals(tok.get(i + 1).teks)
                            && !(i > 0 && "new".equals(tok.get(i - 1).teks));
                    if (!panggilanMetode) ganti.add(Integer.valueOf(i));
                }
            } else {
                for (int i = 0; i < tipe.size(); i++) {
                    AstTipe t = tipe.get(i);
                    for (int j = 0; j < t.metode.size(); j++) {
                        AstMetode m = t.metode.get(j);
                        if (lama.equals(m.nama)
                                && (pemilik.equals(m.kelasPemilik)
                                || turunan.contains(m.kelasPemilik))) {
                            ganti.add(Integer.valueOf(m.indeksNama));
                        }
                        for (int k = 0; k < m.panggilan.size(); k++) {
                            AstPanggilan p = m.panggilan.get(k);
                            if (lama.equals(p.nama) && panggilanMilikKelas(
                                    p, m, pemilik, turunan, petaSuper, target.berkas)) {
                                ganti.add(Integer.valueOf(p.indeksNama));
                            }
                        }
                    }
                }
            }
            if (ganti.isEmpty()) continue;
            String gantiIsi = terapkanToken(isi, tok, ganti, baru);
            if (tulis) tulisTeks(f, gantiIsi);
            hasil.jumlah += ganti.size();
            hasil.jumlahBerkas++;
            hasil.perBerkas.add(ganti.size() + " x " + u.berkas);
        }
        if (gantiKelas) {
            // Berkas non-Java/Kotlin: ganti kata utuh (pola lama).
            gantiNamaKelasBerkasLain(dir, dir, lama, baru, tulis, hasil);
        }
        return hasil;
    }

    /** Splice penggantian nama pada offset token (format asli dipertahankan). */
    static String terapkanToken(String isi, List<Token> tok, Set<Integer> ganti, String baru) {
        List<Integer> urut = new ArrayList<Integer>(ganti);
        for (int i = 0; i < urut.size(); i++) {
            for (int j = i + 1; j < urut.size(); j++) {
                if (urut.get(j) < urut.get(i)) {
                    Integer tmp = urut.get(i);
                    urut.set(i, urut.get(j));
                    urut.set(j, tmp);
                }
            }
        }
        StringBuilder b = new StringBuilder();
        int posisi = 0;
        for (int i = 0; i < urut.size(); i++) {
            Token t = tok.get(urut.get(i).intValue());
            if (t.awal < posisi) continue;
            b.append(isi.substring(posisi, t.awal));
            b.append(baru);
            posisi = t.akhir;
        }
        b.append(isi.substring(posisi));
        return b.toString();
    }

    private static void gantiNamaKelasBerkasLain(File akar, File kini, String lama,
                                                 String baru, boolean tulis, HasilGantiNama hasil) {
        File[] daftar = kini.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    gantiNamaKelasBerkasLain(akar, f, lama, baru, tulis, hasil);
                }
            } else if (akhirKode(f.getName())
                    && !f.getName().endsWith(".java") && !f.getName().endsWith(".kt")) {
                String isi = bacaTeks(f);
                if (isi == null) continue;
                int n = hitungKemunculan(isi, lama);
                if (n == 0) continue;
                if (tulis) tulisTeks(f, gantiNama(isi, lama, baru));
                String relatif = f.getAbsolutePath().substring(akar.getAbsolutePath().length() + 1);
                hasil.jumlah += n;
                hasil.jumlahBerkas++;
                hasil.perBerkas.add(n + " x " + relatif);
            }
        }
    }

    /** Pratinjau ganti nama AST lintas berkas (tanpa menulis apa pun). */
    public static HasilGantiNama pratinjauGantiNamaAst(File dir, String kelasPemilik,
                                                       String lama, String baru) {
        return gantiNamaAstDalam(dir, kelasPemilik, lama, baru, false);
    }

    /** Terapkan ganti nama AST lintas berkas (menulis ke semua berkas). */
    public static HasilGantiNama terapkanGantiNamaAst(File dir, String kelasPemilik,
                                                      String lama, String baru) {
        return gantiNamaAstDalam(dir, kelasPemilik, lama, baru, true);
    }

    /**
     * Ganti nama simbol secara aman di satu isi berkas: hanya kata
     * utuh (batas kata) yang diganti (pemindai pola lama, dipertahankan
     * untuk berkas tanpa parser AST).
     */
    public static String gantiNama(String isi, String lama, String baru) {
        if (isi == null || lama == null || baru == null) return isi;
        if (lama.length() == 0 || lama.equals(baru)) return isi;
        return isi.replaceAll("\\b" + Pattern.quote(lama) + "\\b",
                Matcher.quoteReplacement(baru));
    }

    /** Hitung kemunculan kata utuh di isi berkas. */
    public static int hitungKemunculan(String isi, String kata) {
        if (isi == null || kata == null || kata.length() == 0) return 0;
        Matcher m = Pattern.compile("\\b" + Pattern.quote(kata) + "\\b").matcher(isi);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // ============================ EKSTRAKSI FUNGSI ============================

    private static String hapusString(String baris) {
        StringBuilder b = new StringBuilder();
        boolean dalamString = false;
        boolean dalamChar = false;
        for (int i = 0; i < baris.length(); i++) {
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
            b.append(c);
        }
        return b.toString();
    }

    private static int hitungKurawal(String bersih, char buka, char tutup) {
        int n = 0;
        for (int i = 0; i < bersih.length(); i++) {
            if (bersih.charAt(i) == buka) n++;
            else if (bersih.charAt(i) == tutup) n--;
        }
        return n;
    }

    /**
     * Ekstrak blok baris menjadi fungsi baru: baris awal..akhir
     * (1-based, inklusif) dipindah ke fungsi baru, dan di tempat asal
     * tinggal pemanggilan. Variabel yang dideklarasikan sebelum blok di
     * dalam metode yang sama dan dipakai di dalam blok menjadi parameter
     * (dengan tipenya); bila blok hanya satu baris penugasan ke variabel
     * luar, fungsi mengembalikan tipe variabel itu. Mengembalikan kode
     * baru (null bila rentang tidak sah).
     */
    public static String ekstrakFungsi(String isi, int barisAwal, int barisAkhir,
                                       String namaFungsi) {
        if (isi == null || namaFungsi == null || namaFungsi.length() == 0) return null;
        String[] baris = isi.split("\n");
        if (barisAwal < 1 || barisAkhir < barisAwal || barisAkhir > baris.length) return null;
        // Kedalaman kurawal sebelum blok, dan deklarasi variabel di dalamnya.
        int dalam = 0;
        for (int i = 0; i < barisAwal - 1; i++) {
            dalam += hitungKurawal(hapusString(baris[i]), '{', '}');
        }
        Pattern deklarasi = Pattern.compile(
                "^\\s*(?:final\\s+)?([A-Za-z_][A-Za-z0-9_<>\\[\\], .?]*?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(=|;|,)");
        List<String[]> deklarasiSebelum = new ArrayList<String[]>();
        int dalamBerjalan = 0;
        for (int i = 0; i < barisAwal - 1; i++) {
            String bersih = hapusString(baris[i]);
            Matcher m = deklarasi.matcher(bersih);
            if (m.find() && dalamBerjalan == dalam) {
                String tipe = m.group(1).trim();
                String nama = m.group(2);
                if (!"if".equals(tipe) && !"for".equals(tipe) && !"while".equals(tipe)
                        && !"return".equals(tipe) && !"new".equals(tipe)
                        && !"switch".equals(tipe) && !"catch".equals(tipe)) {
                    deklarasiSebelum.add(new String[]{tipe, nama});
                }
            }
            dalamBerjalan += hitungKurawal(bersih, '{', '}');
        }
        // Identifikasi yang dipakai di dalam blok.
        StringBuilder teksBlok = new StringBuilder();
        for (int i = barisAwal - 1; i < barisAkhir; i++) {
            teksBlok.append(hapusString(baris[i])).append("\n");
        }
        List<String[]> param = new ArrayList<String[]>();
        for (int i = 0; i < deklarasiSebelum.size(); i++) {
            String nama = deklarasiSebelum.get(i)[1];
            Matcher m = Pattern.compile("\\b" + Pattern.quote(nama) + "\\b")
                    .matcher(teksBlok.toString());
            if (m.find()) param.add(deklarasiSebelum.get(i));
        }
        // Penugasan tunggal ke variabel luar: fungsi mengembalikan
        // tipe variabel itu; variabel itu BUKAN parameter (nilainya
        // dihitung di dalam fungsi, bukan diberikan dari luar).
        String tipeKembali = "void";
        String varKembali = null;
        String ekspresiKembali = null;
        if (barisAwal == barisAkhir) {
            String bersih = hapusString(baris[barisAwal - 1]).trim();
            Matcher tugaskan = Pattern.compile(
                    "^([A-Za-z_][A-Za-z0-9_]*)\\s*=([^=].*);\\s*$").matcher(bersih);
            if (tugaskan.find()) {
                for (int i = 0; i < deklarasiSebelum.size(); i++) {
                    if (deklarasiSebelum.get(i)[1].equals(tugaskan.group(1))) {
                        tipeKembali = deklarasiSebelum.get(i)[0];
                        varKembali = tugaskan.group(1);
                        ekspresiKembali = tugaskan.group(2).trim();
                    }
                }
            }
        }
        if (varKembali != null) {
            for (int i = param.size() - 1; i >= 0; i--) {
                if (varKembali.equals(param.get(i)[1])) param.remove(i);
            }
        }
        StringBuilder daftarParam = new StringBuilder();
        StringBuilder daftarArg = new StringBuilder();
        for (int i = 0; i < param.size(); i++) {
            if (i > 0) { daftarParam.append(", "); daftarArg.append(", "); }
            daftarParam.append(param.get(i)[0]).append(" ").append(param.get(i)[1]);
            daftarArg.append(param.get(i)[1]);
        }
        StringBuilder keluar = new StringBuilder();
        StringBuilder fungsi = new StringBuilder();
        fungsi.append("    public static ").append(tipeKembali).append(" ")
                .append(namaFungsi).append("(").append(daftarParam).append(") {\n");
        for (int i = 0; i < baris.length; i++) {
            int nomor = i + 1;
            if (nomor >= barisAwal && nomor <= barisAkhir) {
                if (varKembali != null && nomor == barisAwal && ekspresiKembali != null) {
                    // Penugasan tunggal: badan fungsi mengembalikan
                    // ekspresinya, bukan menugaskan ke variabel luar.
                    fungsi.append("        return ").append(ekspresiKembali).append(";\n");
                } else {
                    fungsi.append("        ").append(baris[i].trim()).append("\n");
                }
                if (nomor == barisAwal) {
                    keluar.append("        ");
                    if (varKembali != null) keluar.append(varKembali).append(" = ");
                    keluar.append(namaFungsi).append("(").append(daftarArg).append(");\n");
                }
            } else {
                keluar.append(baris[i]).append("\n");
            }
        }
        fungsi.append("    }\n");
        keluar.append("\n").append(fungsi.toString());
        return keluar.toString();
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahSimbol, int jumlahPemanggil) {
        return "Simbol ditemukan: " + jumlahSimbol + ". Pemanggil ditemukan: "
                + jumlahPemanggil + ".";
    }
}
