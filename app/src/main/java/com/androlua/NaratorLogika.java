package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narator Alur Logika Kode dan Peninjau Semantik Berbasis AI
 * (Fitur 12, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - narasi: tiap pernyataan kode Java sederhana diubah menjadi
 *    kalimat bahasa Indonesia (tugasan, percabangan, perulangan,
 *    pemanggilan, pengembalian).
 *  - petaPanggilan: metode -> metode yang dipanggilnya.
 *  - aliranData: analisis aliran nilai variabel ANTAR-metode —
 *    parameter yang masuk, nilai yang diteruskan sebagai argumen
 *    ke metode lain, nilai kembalian yang dipakai pemanggil —
 *    beserta peringatan aliran data (nilai yang diberi tetapi
 *    tidak dipakai, parameter yang tidak dipakai, nilai kembalian
 *    yang diabaikan, pemakaian sebelum penugasan).
 *  - ringkasDenganAi: ringkasan alur dari penyedia AI aktif
 *    (Pengaturan AI) lewat ArsitekCetakBiru.PemanggilAiHttp;
 *    bila AI tidak tersedia, layar memakai narasi luring dan
 *    menyatakan batas itu.
 *  - periksaBug: pola bug halus — == pada String, field yang ditulis
 *    di dua metode (potensi balapan data), stream yang dibuka tanpa
 *    close, hasil find yang dipakai tanpa cek null.
 *
 * BATAS JUJUR: narasi, peta panggilan, dan aliran data memakai
 * analisis POLA TEKS luring, bukan parser AST penuh dan bukan
 * kompilator — metode yang kurung kurawalnya di baris sendiri,
 * field kelas, dan pemanggilan lewat refleksi tidak dilacak;
 * nama yang sama di dua metode diduga field kelas. Ringkasan AI
 * hanya sebagus jawaban model dan harus ditinjau.
 */
public class NaratorLogika {

    /** Satu langkah alur: nomor baris + kalimat narasi. */
    public static class Langkah {
        public int baris = 0;
        public String narasi = "";
        public String toString() {
            return baris + ": " + narasi;
        }
    }

    /** Satu peringatan bug: baris + jenis + penjelasan. */
    public static class Peringatan {
        public int baris = 0;
        public String jenis = "";
        public String penjelasan = "";
        public String toString() {
            return baris + ": [" + jenis + "] " + penjelasan;
        }
    }

    private static final Pattern TUGAS = Pattern.compile(
            "^\\s*(?:\\w+\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+);\\s*$");
    private static final Pattern PANGGIL = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern METODE = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^\\)]*\\)\\s*\\{");

    /** Ubah satu baris kode menjadi kalimat narasi. */
    public static String narasiBaris(String baris) {
        if (baris == null) return "";
        String b = baris.trim();
        if (b.length() == 0) return "";
        if (b.startsWith("//")) return "Komentar: " + b.substring(2).trim();
        if (b.startsWith("if (") || b.startsWith("if(")) {
            return "Jika " + isiKurung(b) + ", maka jalankan blok berikut.";
        }
        if (b.startsWith("else")) return "Kalau tidak, jalankan blok berikut.";
        if (b.startsWith("for (") || b.startsWith("for(")) {
            return "Untuk setiap pengulangan " + isiKurung(b) + ", jalankan blok berikut.";
        }
        if (b.startsWith("while (") || b.startsWith("while(")) {
            return "Selama " + isiKurung(b) + ", ulangi blok berikut.";
        }
        if (b.startsWith("return ")) {
            return "Kembalikan " + b.substring(7).replace(";", "").trim() + ".";
        }
        if (b.startsWith("throw ")) {
            return "Lempar galat " + b.substring(6).replace(";", "").trim() + ".";
        }
        Matcher t = TUGAS.matcher(b);
        if (t.find()) {
            return "Simpan " + t.group(2).replace(";", "").trim()
                    + " ke variabel " + t.group(1) + ".";
        }
        Matcher p = PANGGIL.matcher(b);
        if (p.find()) {
            return "Panggil " + p.group(1) + ".";
        }
        if (b.equals("{") || b.equals("}") || b.equals("};")) return "";
        return "Baris kode: " + b;
    }

    private static String isiKurung(String b) {
        int awal = b.indexOf('(');
        int akhir = b.lastIndexOf(')');
        if (awal < 0 || akhir <= awal) return b;
        return b.substring(awal + 1, akhir).trim();
    }

    /** Narasi seluruh kode (satu langkah per baris bermakna). */
    public static List<Langkah> narasi(String kode) {
        List<Langkah> hasil = new ArrayList<Langkah>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String n = narasiBaris(baris[i]);
            if (n.length() == 0) continue;
            Langkah l = new Langkah();
            l.baris = i + 1;
            l.narasi = n;
            hasil.add(l);
        }
        return hasil;
    }

    /** Tujuan utama kode: kalimat pertama dari nama kelas/metode pertama. */
    public static String tujuanUtama(String kode) {
        if (kode == null) return "";
        Matcher m = Pattern.compile(
                "\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(kode);
        if (m.find()) return "Kode ini berkisar pada " + m.group(1) + ".";
        m = Pattern.compile(
                "\\b(?:void|int|long|double|boolean|String)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                .matcher(kode);
        if (m.find()) return "Kode ini berkisar pada " + m.group(1) + ".";
        return "Kode ini berisi pernyataan berurutan.";
    }

    /** Peta panggilan: "pemanggil -> dipanggil" per baris metode. */
    public static List<String> petaPanggilan(String kode) {
        List<String> hasil = new ArrayList<String>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        String kini = "";
        for (int i = 0; i < baris.length; i++) {
            Matcher m = METODE.matcher(baris[i]);
            if (m.find() && !baris[i].trim().startsWith("if")
                    && !baris[i].trim().startsWith("for")
                    && !baris[i].trim().startsWith("while")) {
                kini = m.group(1);
                continue;
            }
            Matcher p = PANGGIL.matcher(baris[i]);
            while (p.find()) {
                String nama = p.group(1);
                if (nama.equals(kini) || "if".equals(nama) || "for".equals(nama)
                        || "while".equals(nama) || "return".equals(nama)) continue;
                String entri = (kini.length() == 0 ? "?" : kini) + " -> " + nama;
                if (!hasil.contains(entri)) hasil.add(entri);
            }
        }
        return hasil;
    }

    /** Periksa pola bug halus; kembalikan daftar peringatan. */
    public static List<Peringatan> periksaBug(String kode) {
        List<Peringatan> hasil = new ArrayList<Peringatan>();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n");
        List<String> fieldTulis = new ArrayList<String>();
        List<String> metodeTulis = new ArrayList<String>();
        String kini = "";
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            Matcher m = METODE.matcher(b);
            if (m.find() && !b.trim().startsWith("if") && !b.trim().startsWith("for")) {
                kini = m.group(1);
            }
            if (b.contains("==") && (b.contains("\"") || b.matches(".*==\\s*[a-z]\\w*.*"))) {
                Matcher s = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*==\\s*\"").matcher(b);
                if (s.find() || b.matches(".*\"[^\"]*\"\\s*==.*")) {
                    Peringatan p = new Peringatan();
                    p.baris = i + 1;
                    p.jenis = "Perbandingan String";
                    p.penjelasan = "String dibandingkan dengan ==, pakai equals supaya isinya yang dibandingkan.";
                    hasil.add(p);
                }
            }
            if ((b.contains("new FileInputStream") || b.contains("new FileOutputStream")
                    || b.contains(".openConnection()")) && !b.contains("try (")) {
                Peringatan p = new Peringatan();
                p.baris = i + 1;
                p.jenis = "Sumber daya tidak ditutup";
                p.penjelasan = "Stream dibuka tanpa try-with-resources atau close; bisa bocor bila terjadi galat.";
                hasil.add(p);
            }
            Matcher t = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=[^=]").matcher(b);
            if (t.find() && kini.length() > 0 && !b.contains("==")) {
                String nama = t.group(1);
                int idx = fieldTulis.indexOf(nama);
                if (idx >= 0 && !metodeTulis.get(idx).equals(kini)) {
                    Peringatan p = new Peringatan();
                    p.baris = i + 1;
                    p.jenis = "Potensi balapan data";
                    p.penjelasan = "Variabel " + nama + " ditulis di metode "
                            + metodeTulis.get(idx) + " dan " + kini
                            + "; bila dua metode jalan bersamaan, nilainya bisa berebut.";
                    hasil.add(p);
                } else if (idx < 0) {
                    fieldTulis.add(nama);
                    metodeTulis.add(kini);
                }
            }
        }
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahLangkah, int jumlahPeringatan) {
        return "Langkah alur: " + jumlahLangkah + ". Peringatan ditemukan: "
                + jumlahPeringatan + ".";
    }

    // ------------------------------------------------------- aliran data
    //
    // Analisis aliran nilai variabel antar-metode dari pola teks.
    // Bukan AST penuh: lihat batas di Javadoc kelas.

    /** Satu variabel yang alirannya dilacak di satu metode. */
    public static class AlurVariabel {
        public String nama = "";
        public String metode = "";
        public int barisDefinisi = 0;
        /** "parameter" atau "tugasan". */
        public String caraDefinisi = "";
        /** Pernah menjadi sisi kiri penugasan (bukan sekadar deklarasi). */
        public boolean ditugaskan = false;
        public List<Integer> barisPakai = new ArrayList<Integer>();
        /** "metode(param)" yang menerima nilai variabel ini sebagai argumen. */
        public List<String> diteruskanKe = new ArrayList<String>();
        public boolean dikembalikan = false;
        /** Pemanggil yang memakai nilai kembalian, mis. "bayar (disimpan ke x)". */
        public String dikembalikanKe = "";
    }

    /** Hasil analisis aliran data: variabel + peringatan aliran. */
    public static class AliranData {
        public List<AlurVariabel> variabel = new ArrayList<AlurVariabel>();
        public List<String> peringatan = new ArrayList<String>();
    }

    /** Info satu metode yang ditemukan dari pola teks. */
    private static class InfoMetode {
        String nama = "";
        String tipeKembali = "";
        int barisAwal = 0;
        int barisAkhir = 0;
        List<String> parameter = new ArrayList<String>();
    }

    /** Satu tempat pemanggilan metode. */
    private static class SitusPanggil {
        String pemanggil = "";
        int baris = 0;
        String dipanggil = "";
        String[] argumen = new String[0];
        String disimpanKe = "";
        boolean dikembalikanLagi = false;
        String barisPenuh = "";
    }

    private static final Pattern KEPALA_METODE = Pattern.compile(
            "^\\s*(?:(?:public|private|protected|static|final|synchronized|abstract)\\s+)*"
            + "(?:([A-Za-z_][A-Za-z0-9_<>\\[\\],\\s]*?)\\s+)?"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[A-Za-z0-9_,\\s]+)?\\{\\s*$");
    private static final Pattern TUGAS_ALIRAN = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=(?!=)");
    private static final Pattern DEKLARASI = Pattern.compile(
            "^\\s*(?:final\\s+)?([A-Za-z_][A-Za-z0-9_<>\\[\\], ]*?)\\s+"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*;\\s*$");
    private static final Pattern PANGGIL_ALIRAN = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static boolean kataKunciAliran(String nama) {
        return "if".equals(nama) || "for".equals(nama) || "while".equals(nama)
                || "switch".equals(nama) || "catch".equals(nama)
                || "return".equals(nama) || "new".equals(nama)
                || "super".equals(nama) || "this".equals(nama);
    }

    private static int hitungHuruf(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /**
     * Bersihkan kode dari komentar dan literal string/karakter
     * (diganti spasi, baris baru dipertahankan) supaya kurung dan
     * nama di dalamnya tidak mengacaukan analisis.
     */
    private static String bersihkanKode(String kode) {
        StringBuilder b = new StringBuilder();
        int n = kode.length();
        int i = 0;
        while (i < n) {
            char c = kode.charAt(i);
            if (c == '/' && i + 1 < n && kode.charAt(i + 1) == '/') {
                while (i < n && kode.charAt(i) != '\n') {
                    b.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && kode.charAt(i + 1) == '*') {
                b.append("  ");
                i += 2;
                while (i < n && !(kode.charAt(i) == '*'
                        && i + 1 < n && kode.charAt(i + 1) == '/')) {
                    b.append(kode.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    b.append("  ");
                    i += 2;
                }
            } else if (c == '"') {
                b.append(' ');
                i++;
                while (i < n && kode.charAt(i) != '"' && kode.charAt(i) != '\n') {
                    if (kode.charAt(i) == '\\' && i + 1 < n) {
                        b.append("  ");
                        i += 2;
                    } else {
                        b.append(' ');
                        i++;
                    }
                }
                if (i < n && kode.charAt(i) == '"') {
                    b.append(' ');
                    i++;
                }
            } else if (c == '\'') {
                b.append(' ');
                i++;
                while (i < n && kode.charAt(i) != '\'' && kode.charAt(i) != '\n') {
                    if (kode.charAt(i) == '\\' && i + 1 < n) {
                        b.append("  ");
                        i += 2;
                    } else {
                        b.append(' ');
                        i++;
                    }
                }
                if (i < n && kode.charAt(i) == '\'') {
                    b.append(' ');
                    i++;
                }
            } else {
                b.append(c);
                i++;
            }
        }
        return b.toString();
    }

    /** Cari metode dari baris yang sudah dibersihkan (1-based). */
    private static List<InfoMetode> cariMetode(String[] bersih) {
        List<InfoMetode> hasil = new ArrayList<InfoMetode>();
        for (int i = 0; i < bersih.length; i++) {
            String t = bersih[i].trim();
            if (t.startsWith("if") || t.startsWith("for") || t.startsWith("while")
                    || t.startsWith("switch") || t.startsWith("catch")
                    || t.startsWith("synchronized") || t.startsWith("return")
                    || t.startsWith("class") || t.startsWith("new")) continue;
            Matcher m = KEPALA_METODE.matcher(bersih[i]);
            if (!m.find()) continue;
            String tipe = m.group(1);
            String nama = m.group(2);
            String param = m.group(3);
            if (kataKunciAliran(nama)) continue;
            if (param != null && (param.contains("(") || param.contains("new"))) continue;
            if (bersih[i].contains("= new")) continue;
            InfoMetode info = new InfoMetode();
            info.nama = nama;
            info.tipeKembali = tipe == null ? "" : tipe.trim();
            info.barisAwal = i + 1;
            info.parameter = uraikanParameter(param);
            int dalam = 0;
            int akhir = bersih.length;
            for (int j = i; j < bersih.length; j++) {
                dalam += hitungHuruf(bersih[j], '{') - hitungHuruf(bersih[j], '}');
                if (j > i && dalam <= 0) {
                    akhir = j + 1;
                    break;
                }
            }
            info.barisAkhir = akhir;
            hasil.add(info);
        }
        return hasil;
    }

    /** "int a, String b" -> [a, b]; koma di dalam <>/()/[] tidak memisah. */
    private static List<String> uraikanParameter(String param) {
        List<String> hasil = new ArrayList<String>();
        if (param == null) return hasil;
        String[] bagian = pisahKoma(param);
        for (int i = 0; i < bagian.length; i++) {
            String s = bagian[i].trim();
            if (s.length() == 0) continue;
            String[] kata = s.split("\\s+");
            String nama = kata[kata.length - 1].replace("...", "").trim();
            if (nama.matches("[A-Za-z_][A-Za-z0-9_]*")) hasil.add(nama);
        }
        return hasil;
    }

    /** Pisah koma tingkat atas (kurung <>, (), [] dihitung). */
    private static String[] pisahKoma(String s) {
        List<String> hasil = new ArrayList<String>();
        int dalam = 0;
        int awal = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[' || c == '{') dalam++;
            else if (c == '>' || c == ')' || c == ']' || c == '}') dalam--;
            else if (c == ',' && dalam == 0) {
                hasil.add(s.substring(awal, i));
                awal = i + 1;
            }
        }
        hasil.add(s.substring(awal));
        return hasil.toArray(new String[hasil.size()]);
    }

    /** Indeks kurung tutup pasangan dari kurung buka; -1 bila tidak ada. */
    private static int cariTutup(String s, int buka) {
        int dalam = 0;
        for (int i = buka; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') dalam++;
            else if (c == ')') {
                dalam--;
                if (dalam == 0) return i;
            }
        }
        return -1;
    }

    private static InfoMetode cariMetodeDenganNama(List<InfoMetode> semua, String nama) {
        for (int i = 0; i < semua.size(); i++) {
            if (semua.get(i).nama.equals(nama)) return semua.get(i);
        }
        return null;
    }

    private static AlurVariabel cariVar(List<AlurVariabel> semua, String metode, String nama) {
        for (int i = 0; i < semua.size(); i++) {
            AlurVariabel v = semua.get(i);
            if (v.metode.equals(metode) && v.nama.equals(nama)) return v;
        }
        return null;
    }

    /** Baris dengan sisi kiri penugasan/deklarasi dikosongkan (untuk pindai pemakaian). */
    private static String tanpaTulis(String baris) {
        StringBuilder b = new StringBuilder(baris);
        Matcher a = TUGAS_ALIRAN.matcher(baris);
        while (a.find()) {
            for (int i = a.start(1); i < a.end(1); i++) b.setCharAt(i, ' ');
        }
        Matcher d = DEKLARASI.matcher(baris);
        if (d.matches()) {
            String tipe = d.group(1);
            String trim = baris.trim();
            if (!tipe.contains(".") && !trim.startsWith("return")
                    && !trim.startsWith("if") && !trim.startsWith("for")
                    && !trim.startsWith("while") && !trim.startsWith("throw")) {
                for (int i = d.start(2); i < d.end(2); i++) b.setCharAt(i, ' ');
            }
        }
        return b.toString();
    }

    /**
     * Analisis aliran nilai variabel antar-metode. Lacak: parameter
     * yang masuk ke tiap metode, nilai yang diteruskan sebagai
     * argumen ke metode lain, dan nilai kembalian yang dipakai
     * pemanggil — beserta peringatan aliran data.
     */
    public static AliranData aliranData(String kode) {
        AliranData hasil = new AliranData();
        if (kode == null) return hasil;
        String[] bersih = bersihkanKode(kode).split("\n", -1);
        List<InfoMetode> metode = cariMetode(bersih);
        List<AlurVariabel> semua = new ArrayList<AlurVariabel>();
        List<SitusPanggil> situs = new ArrayList<SitusPanggil>();

        for (int mi = 0; mi < metode.size(); mi++) {
            InfoMetode m = metode.get(mi);
            for (int pi = 0; pi < m.parameter.size(); pi++) {
                AlurVariabel v = new AlurVariabel();
                v.nama = m.parameter.get(pi);
                v.metode = m.nama;
                v.barisDefinisi = m.barisAwal;
                v.caraDefinisi = "parameter";
                semua.add(v);
            }
            for (int ln = m.barisAwal + 1; ln <= m.barisAkhir && ln <= bersih.length; ln++) {
                String baris = bersih[ln - 1];
                String trim = baris.trim();
                Matcher a = TUGAS_ALIRAN.matcher(baris);
                while (a.find()) {
                    String nama = a.group(1);
                    AlurVariabel v = cariVar(semua, m.nama, nama);
                    if (v == null) {
                        v = new AlurVariabel();
                        v.nama = nama;
                        v.metode = m.nama;
                        v.barisDefinisi = ln;
                        v.caraDefinisi = "tugasan";
                        semua.add(v);
                    }
                    v.ditugaskan = true;
                }
                if (!a.reset().find()) {
                    Matcher d = DEKLARASI.matcher(baris);
                    if (d.matches()) {
                        String tipe = d.group(1);
                        String nama = d.group(2);
                        if (!tipe.contains(".") && !trim.startsWith("return")
                                && !trim.startsWith("if") && !trim.startsWith("for")
                                && !trim.startsWith("while") && !trim.startsWith("throw")
                                && !trim.contains("(") && cariVar(semua, m.nama, nama) == null) {
                            AlurVariabel v = new AlurVariabel();
                            v.nama = nama;
                            v.metode = m.nama;
                            v.barisDefinisi = ln;
                            v.caraDefinisi = "tugasan";
                            semua.add(v);
                        }
                    }
                }
                if (trim.startsWith("return ")) {
                    String expr = trim.substring(7).replace(";", "").trim();
                    if (expr.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        AlurVariabel v = cariVar(semua, m.nama, expr);
                        if (v != null) v.dikembalikan = true;
                    }
                }
                Matcher p = PANGGIL_ALIRAN.matcher(baris);
                while (p.find()) {
                    String nama = p.group(1);
                    if (kataKunciAliran(nama)) continue;
                    String sebelum = baris.substring(0, p.start()).trim();
                    if (sebelum.endsWith("new")) continue;
                    InfoMetode target = cariMetodeDenganNama(metode, nama);
                    if (target == null) continue;
                    int tutup = cariTutup(baris, p.end() - 1);
                    if (tutup < 0) continue;
                    SitusPanggil s = new SitusPanggil();
                    s.pemanggil = m.nama;
                    s.baris = ln;
                    s.dipanggil = nama;
                    s.argumen = pisahKoma(baris.substring(p.end(), tutup));
                    s.barisPenuh = trim;
                    if (trim.startsWith("return")) {
                        s.dikembalikanLagi = true;
                    } else {
                        Matcher as = TUGAS_ALIRAN.matcher(baris);
                        if (as.find() && as.end() <= p.start()) s.disimpanKe = as.group(1);
                    }
                    situs.add(s);
                }
            }
        }

        // Pemakaian tiap variabel (baris tanpa sisi kiri penugasan).
        for (int mi = 0; mi < metode.size(); mi++) {
            InfoMetode m = metode.get(mi);
            for (int vi = 0; vi < semua.size(); vi++) {
                AlurVariabel v = semua.get(vi);
                if (!v.metode.equals(m.nama)) continue;
                Pattern pola = Pattern.compile("\\b" + Pattern.quote(v.nama) + "\\b");
                for (int ln = m.barisAwal + 1; ln <= m.barisAkhir && ln <= bersih.length; ln++) {
                    if (pola.matcher(tanpaTulis(bersih[ln - 1])).find()) {
                        if (!v.barisPakai.contains(ln)) v.barisPakai.add(ln);
                    }
                }
            }
        }

        // Argumen -> parameter metode yang dipanggil.
        for (int si = 0; si < situs.size(); si++) {
            SitusPanggil s = situs.get(si);
            InfoMetode target = cariMetodeDenganNama(metode, s.dipanggil);
            if (target == null) continue;
            for (int ai = 0; ai < s.argumen.length && ai < target.parameter.size(); ai++) {
                String arg = s.argumen[ai].trim();
                if (!arg.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
                AlurVariabel v = cariVar(semua, s.pemanggil, arg);
                if (v == null) continue;
                String entri = s.dipanggil + "(" + target.parameter.get(ai) + ")";
                if (!v.diteruskanKe.contains(entri)) v.diteruskanKe.add(entri);
            }
        }

        // Nilai kembalian -> pemanggil yang memakainya.
        for (int vi = 0; vi < semua.size(); vi++) {
            AlurVariabel v = semua.get(vi);
            if (!v.dikembalikan) continue;
            List<String> tujuan = new ArrayList<String>();
            for (int si = 0; si < situs.size(); si++) {
                SitusPanggil s = situs.get(si);
                if (!s.dipanggil.equals(v.metode)) continue;
                String t;
                if (s.disimpanKe.length() > 0) {
                    t = s.pemanggil + " (disimpan ke " + s.disimpanKe + ")";
                } else if (s.dikembalikanLagi) {
                    t = s.pemanggil + " (dikembalikan lagi)";
                } else {
                    t = s.pemanggil + " (dipakai langsung)";
                }
                if (!tujuan.contains(t)) tujuan.add(t);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tujuan.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(tujuan.get(i));
            }
            v.dikembalikanKe = sb.toString();
        }

        // Nama yang didefinisikan di lebih dari satu metode diduga field
        // kelas — peringatan lokal (nilai mati, parameter tak dipakai,
        // pakai sebelum tugasan) tidak berlaku untuknya.
        List<String> lintasMetode = new ArrayList<String>();
        for (int i = 0; i < semua.size(); i++) {
            for (int j = 0; j < semua.size(); j++) {
                if (i != j && semua.get(i).nama.equals(semua.get(j).nama)
                        && !semua.get(i).metode.equals(semua.get(j).metode)
                        && !lintasMetode.contains(semua.get(i).nama)) {
                    lintasMetode.add(semua.get(i).nama);
                }
            }
        }

        for (int vi = 0; vi < semua.size(); vi++) {
            AlurVariabel v = semua.get(vi);
            if (lintasMetode.contains(v.nama)) continue;
            if ("tugasan".equals(v.caraDefinisi) && v.ditugaskan
                    && v.barisPakai.isEmpty() && !v.dikembalikan
                    && v.diteruskanKe.isEmpty()) {
                hasil.peringatan.add("Variabel " + v.nama + " di metode " + v.metode
                        + " diberi nilai di baris " + v.barisDefinisi
                        + " tetapi tidak pernah dipakai.");
            }
            if ("tugasan".equals(v.caraDefinisi) && !v.ditugaskan
                    && v.barisPakai.isEmpty()) {
                hasil.peringatan.add("Variabel " + v.nama + " di metode " + v.metode
                        + " dideklarasikan di baris " + v.barisDefinisi
                        + " tetapi tidak pernah dipakai.");
            }
            if ("parameter".equals(v.caraDefinisi)
                    && v.barisPakai.isEmpty() && !v.dikembalikan
                    && v.diteruskanKe.isEmpty()) {
                hasil.peringatan.add("Parameter " + v.nama + " di metode " + v.metode
                        + " tidak pernah dipakai.");
            }
            if ("tugasan".equals(v.caraDefinisi) && v.ditugaskan) {
                for (int ui = 0; ui < v.barisPakai.size(); ui++) {
                    if (v.barisPakai.get(ui) < v.barisDefinisi) {
                        hasil.peringatan.add("Variabel " + v.nama + " di metode " + v.metode
                                + " dipakai di baris " + v.barisPakai.get(ui)
                                + " sebelum diberi nilai di baris " + v.barisDefinisi + ".");
                        break;
                    }
                }
            }
        }

        // Nilai kembalian yang diabaikan di tempat pemanggilan.
        for (int si = 0; si < situs.size(); si++) {
            SitusPanggil s = situs.get(si);
            InfoMetode target = cariMetodeDenganNama(metode, s.dipanggil);
            if (target == null) continue;
            if (target.tipeKembali == null || target.tipeKembali.length() == 0
                    || "void".equals(target.tipeKembali)) continue;
            if (s.disimpanKe.length() == 0 && !s.dikembalikanLagi
                    && s.barisPenuh.matches("[A-Za-z_][A-Za-z0-9_.]*\\s*\\(.*\\)\\s*;")) {
                hasil.peringatan.add("Hasil kembalian " + s.dipanggil + " di baris " + s.baris
                        + " di metode " + s.pemanggil + " tidak disimpan atau dipakai.");
            }
        }

        hasil.variabel.addAll(semua);
        return hasil;
    }

    /** Narasi bagian aliran data (untuk layar). */
    public static String narasiAliran(AliranData a) {
        if (a == null) return "Aliran data: tidak ada data.";
        StringBuilder b = new StringBuilder();
        b.append("Aliran data:");
        boolean ada = false;
        for (int i = 0; i < a.variabel.size(); i++) {
            AlurVariabel v = a.variabel.get(i);
            if (v.diteruskanKe.isEmpty() && !v.dikembalikan) continue;
            ada = true;
            b.append("\n- Nilai ").append(v.nama).append(" ");
            if ("parameter".equals(v.caraDefinisi)) {
                b.append("masuk ke ").append(v.metode).append(" sebagai parameter baris ")
                        .append(v.barisDefinisi);
            } else {
                b.append("dibuat di ").append(v.metode).append(" baris ").append(v.barisDefinisi);
            }
            if (!v.barisPakai.isEmpty()) {
                b.append(", dipakai di baris ");
                for (int j = 0; j < v.barisPakai.size(); j++) {
                    if (j > 0) b.append(", ");
                    b.append(v.barisPakai.get(j));
                }
            }
            if (!v.diteruskanKe.isEmpty()) {
                b.append(", diteruskan ke ");
                for (int j = 0; j < v.diteruskanKe.size(); j++) {
                    if (j > 0) b.append(", ");
                    b.append(v.diteruskanKe.get(j));
                }
            }
            if (v.dikembalikan) {
                b.append(", dikembalikan ke ");
                b.append(v.dikembalikanKe.length() == 0 ? "pemanggil" : v.dikembalikanKe);
            }
            b.append(".");
        }
        if (!ada) b.append(" tidak ada aliran nilai antar-metode yang ditemukan.");
        for (int i = 0; i < a.peringatan.size(); i++) {
            b.append("\n! ").append(a.peringatan.get(i));
        }
        return b.toString();
    }

    // ------------------------------------------------------- ringkasan AI

    /** Perintah ke AI untuk meringkas alur logika (kode dipangkas 12.000 huruf). */
    public static String perintahRingkas(String kode) {
        String k = kode == null ? "" : kode;
        if (k.length() > 12000) k = k.substring(0, 12000);
        return "Ringkas alur logika kode Java berikut dalam bahasa Indonesia, "
                + "paling banyak 5 kalimat pendek. Sebutkan tujuan utama kode, "
                + "alur langkah terpenting, dan bila ada, satu peringatan logika "
                + "yang paling penting. Jangan menambah penjelasan di luar kode.\n\n" + k;
    }

    /**
     * Ringkasan alur dari jawaban AI; null bila jawaban kosong atau
     * bukan dari AI — pemanggil lalu memakai narasi luring.
     */
    public static String ringkasDenganAi(ArsitekCetakBiru.JawabanAi jawaban) {
        if (jawaban == null || !jawaban.dariAi || jawaban.teks == null) return null;
        String t = jawaban.teks.trim();
        return t.length() == 0 ? null : t;
    }
}
