package com.androlua;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arsitek Tata Letak Berbasis Hirarki Pohon Teks dan Wireframe Spasial
 * (Fitur 3, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - urai: XML layout -> pohon simpul (nama, atribut, anak).
 *  - uraiCompose: kode Kotlin Jetpack Compose -> pohon simpul yang
 *    sama (Column/Row/Box + Modifier ukuran), supaya narasi dan
 *    pemeriksaan tabrakan/keluar layar bekerja untuk Compose juga.
 *  - hitungKotak: simulasi tata letak matematis pada lebar layar
 *    tertentu untuk:
 *      * LinearLayout / Column / Row, termasuk layout_weight dan
 *        bobot bersarang (weightSum induk ikut dihormati);
 *      * ConstraintLayout: semua constraint (start/end/left/right/
 *        top/bottom/baseline ke induk, ke simpul lain, ke Guideline
 *        dan ke Barrier), bias horizontal/vertikal, rantai
 *        (spread / spread_inside / packed), lebar/tinggi persen,
 *        0dp match_constraint, dan rasio dimensi;
 *      * FrameLayout/Box/RelativeLayout: anak menumpuk di titik
 *        yang sama (di sanalah tabrakan bisa terjadi).
 *  - narasi: tiap elemen dibacakan sebagai kalimat posisi
 *    ("di kanan bawah, 16 dp di bawah ...").
 *  - periksa: tabrakan antar-elemen dan elemen keluar layar.
 *
 * BATAS JUJUR (simulasi matematis dari teks, bukan mesin ukur
 * Android sungguhan): ukuran wrap_content diperkirakan dari teks
 * (7 dp per huruf, min 48 dp); padding Compose diperlakukan sebagai
 * margin; Flow hanya dihitung bila anak yang dirujuknya tidak punya
 * constraint sendiri (kalau tidak, constraint anak itu yang dipakai);
 * MotionLayout / ConstraintSet yang diubah saat berjalan tidak
 * dihitung — layout seperti itu dilaporkan "posisi tidak tentu".
 * Nilai dimensi dari sumber daya (@dimen/...) dan dari tema tidak
 * bisa dibaca dari berkas layout saja, jadi diperlakukan sebagai
 * wrap_content.
 */
public class ArsitekTataLetak {

    /** Satu simpul pohon layout. */
    public static class Simpul {
        public String nama = "";
        public String id = "";
        public String teks = "";
        public int lebarDp = -1;
        public int tinggiDp = -1;
        public boolean lebarMatch = false;
        public boolean tinggiMatch = false;
        public boolean lebarWrap = false;
        public boolean tinggiWrap = false;
        /** 0dp di ConstraintLayout = match_constraint. */
        public boolean lebarNolConstraint = false;
        public boolean tinggiNolConstraint = false;
        public int marginKiri = 0;
        public int marginAtas = 0;
        public int marginKanan = 0;
        public int marginBawah = 0;
        public String orientasi = "";
        public String diBawah = "";
        /** layout_weight anak; -1 = tidak berbobot. */
        public float bobot = -1;
        /** android:weightSum induk; -1 = tidak diatur. */
        public float jumlahBobot = -1;
        // ---- ConstraintLayout ----
        /** Target constraint, id simpul lain atau "parent". "" = tidak ada. */
        public String kAwal = "";
        public String kAkhir = "";
        public String kAtas = "";
        public String kBawah = "";
        public String kBaseline = "";
        /** Sisi target: "start"/"end"/"left"/"right"/"top"/"bottom"/"baseline". */
        public String kAwalSisi = "";
        public String kAkhirSisi = "";
        public String kAtasSisi = "";
        public String kBawahSisi = "";
        /** Bias 0..1, bawaan 0.5. */
        public float biasH = 0.5f;
        public float biasV = 0.5f;
        /** Gaya rantai: "spread", "spread_inside", "packed", atau "". */
        public String rantaiH = "";
        public String rantaiV = "";
        /** layout_constraintWidth_percent / Height_percent, -1 = tidak ada. */
        public float persenLebar = -1;
        public float persenTinggi = -1;
        /** Rasio dimensi w:h (mis. 16:9 -> 16/9), -1 = tidak ada. */
        public float rasioDimensi = -1;
        // ---- Guideline / Barrier / Flow ----
        /** "vertical"/"horizontal" untuk Guideline, "" bila bukan. */
        public String panduanOrientasi = "";
        /** layout_constraintGuide_percent (0..1), -1 = tidak ada. */
        public float panduanPersen = -1;
        /** layout_constraintGuide_begin / _end (dp), -1 = tidak ada. */
        public int panduanAwalDp = -1;
        public int panduanAkhirDp = -1;
        /** "left"/"right"/"top"/"bottom" untuk Barrier, "" bila bukan. */
        public String penghalangArah = "";
        /** Id-id yang dirujuk Barrier/Flow, dipisah koma. */
        public String rujukan = "";
        /** Jarak antar-elemen Flow (dp). */
        public int celahH = 0;
        public int celahV = 0;
        public int x = -1;
        public int y = -1;
        public int w = -1;
        public int h = -1;
        public List<Simpul> anak = new ArrayList<Simpul>();
    }

    /** Satu temuan pemeriksaan: jenis + penjelasan. */
    public static class Temuan {
        public String jenis = "";
        public String penjelasan = "";
        public Temuan(String jenis, String penjelasan) {
            this.jenis = jenis;
            this.penjelasan = penjelasan;
        }
        public String toString() {
            return jenis + ": " + penjelasan;
        }
    }

    private static final Pattern TAG = Pattern.compile(
            "<(/?)([A-Za-z0-9_\\.]+)((?:\\s+[A-Za-z0-9_:.-]+\\s*=\\s*\"[^\"]*\")*)\\s*(/?)>");
    private static final Pattern ATR = Pattern.compile(
            "([A-Za-z0-9_:.-]+)\\s*=\\s*\"([^\"]*)\"");

    private static String ambilAtr(String mentah, String nama) {
        Matcher m = ATR.matcher(mentah);
        while (m.find()) {
            if (nama.equals(m.group(1))) return m.group(2);
        }
        return null;
    }

    private static int ambilDp(String nilai) {
        if (nilai == null) return -1;
        nilai = nilai.trim();
        if (nilai.startsWith("@")) return -1;
        if (nilai.endsWith("dp") || nilai.endsWith("dip")) {
            try {
                return (int) Math.round(Double.parseDouble(nilai.replace("dp", "")
                        .replace("dip", "").trim()));
            } catch (Throwable t) {
                return -1;
            }
        }
        return -1;
    }

    private static float ambilPecahan(String nilai, float bawaan) {
        if (nilai == null) return bawaan;
        try {
            float f = Float.parseFloat(nilai.trim());
            if (f < 0) return 0;
            if (f > 1) return 1;
            return f;
        } catch (Throwable t) {
            return bawaan;
        }
    }

    /** "parent", "@+id/x", "@id/x" -> "parent" atau "x". */
    private static String idTarget(String nilai) {
        if (nilai == null) return "";
        nilai = nilai.trim();
        if ("parent".equals(nilai)) return "parent";
        int garis = nilai.lastIndexOf('/');
        return garis >= 0 ? nilai.substring(garis + 1) : nilai;
    }

    private static void isiUkuran(Simpul s, String nilai, boolean lebar) {
        if (nilai == null) return;
        nilai = nilai.trim();
        if ("match_parent".equals(nilai) || "fill_parent".equals(nilai)) {
            if (lebar) s.lebarMatch = true; else s.tinggiMatch = true;
        } else if ("wrap_content".equals(nilai)) {
            if (lebar) s.lebarWrap = true; else s.tinggiWrap = true;
        } else if ("0dp".equals(nilai) || "0dip".equals(nilai)) {
            if (lebar) s.lebarNolConstraint = true; else s.tinggiNolConstraint = true;
        } else {
            int dp = ambilDp(nilai);
            if (dp >= 0) {
                if (lebar) s.lebarDp = dp; else s.tinggiDp = dp;
            }
        }
    }

    /** Satu constraint: target + sisi target. */
    private static void isiConstraint(Simpul s, String mentah, String namaAtr,
            String sisi, boolean awal) {
        String v = ambilAtr(mentah, namaAtr);
        if (v == null) return;
        String target = idTarget(v);
        if (target.length() == 0) return;
        if (awal) {
            // Awal = start/left/top. Bila sudah terisi oleh start,
            // left tidak menimpanya (start lebih diutamakan di RTL).
            if ("start".equals(sisi) || s.kAwal.length() == 0) {
                s.kAwal = target;
                s.kAwalSisi = sisi;
            }
        } else {
            if ("end".equals(sisi) || s.kAkhir.length() == 0) {
                s.kAkhir = target;
                s.kAkhirSisi = sisi;
            }
        }
    }

    private static void isiConstraintV(Simpul s, String mentah, String namaAtr,
            String sisi, boolean atas) {
        String v = ambilAtr(mentah, namaAtr);
        if (v == null) return;
        String target = idTarget(v);
        if (target.length() == 0) return;
        if (atas) {
            if ("top".equals(sisi) || s.kAtas.length() == 0) {
                s.kAtas = target;
                s.kAtasSisi = sisi;
            }
        } else {
            if ("bottom".equals(sisi) || s.kBawah.length() == 0) {
                s.kBawah = target;
                s.kBawahSisi = sisi;
            }
        }
    }

    /** Urai "16:9", "H,16:9", "W,1.5" atau "1.5" menjadi rasio lebar:tinggi. */
    static float uraiRasio(String nilai) {
        if (nilai == null) return -1;
        nilai = nilai.trim();
        int koma = nilai.indexOf(',');
        if (koma >= 0) nilai = nilai.substring(koma + 1).trim();
        int titikDua = nilai.indexOf(':');
        try {
            if (titikDua >= 0) {
                float a = Float.parseFloat(nilai.substring(0, titikDua).trim());
                float b = Float.parseFloat(nilai.substring(titikDua + 1).trim());
                if (a > 0 && b > 0) return a / b;
                return -1;
            }
            float f = Float.parseFloat(nilai);
            return f > 0 ? f : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Urai XML layout menjadi pohon simpul (akar = elemen terluar). */
    public static Simpul urai(String xml) {
        if (xml == null) return null;
        List<Simpul> tumpukan = new ArrayList<Simpul>();
        Simpul akar = null;
        Matcher m = TAG.matcher(xml);
        while (m.find()) {
            boolean tutup = "/".equals(m.group(1));
            String nama = m.group(2);
            String atr = m.group(3) == null ? "" : m.group(3);
            boolean tutupSendiri = "/".equals(m.group(4));
            if (nama.startsWith("?") || nama.startsWith("!")) continue;
            if (tutup) {
                if (tumpukan.size() > 0) tumpukan.remove(tumpukan.size() - 1);
                continue;
            }
            Simpul s = new Simpul();
            s.nama = nama;
            String id = ambilAtr(atr, "android:id");
            if (id != null) {
                int garis = id.lastIndexOf('/');
                s.id = garis >= 0 ? id.substring(garis + 1) : id;
            }
            String teks = ambilAtr(atr, "android:text");
            if (teks != null) s.teks = teks;
            isiUkuran(s, ambilAtr(atr, "android:layout_width"), true);
            isiUkuran(s, ambilAtr(atr, "android:layout_height"), false);
            String o = ambilAtr(atr, "android:orientation");
            if (o != null) s.orientasi = o;
            s.marginKiri = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginLeft")));
            s.marginAtas = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginTop")));
            s.marginKanan = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginRight")));
            s.marginBawah = Math.max(0, ambilDp(ambilAtr(atr, "android:layout_marginBottom")));
            int margin = ambilDp(ambilAtr(atr, "android:layout_margin"));
            if (margin >= 0) {
                s.marginKiri = margin;
                s.marginAtas = margin;
                s.marginKanan = margin;
                s.marginBawah = margin;
            }
            String bawah = ambilAtr(atr, "android:layout_below");
            if (bawah != null) {
                int garis = bawah.lastIndexOf('/');
                s.diBawah = garis >= 0 ? bawah.substring(garis + 1) : bawah;
            }
            // Bobot LinearLayout.
            String bobot = ambilAtr(atr, "android:layout_weight");
            if (bobot != null) {
                try { s.bobot = Float.parseFloat(bobot.trim()); } catch (Throwable t) { }
            }
            String jumlahBobot = ambilAtr(atr, "android:weightSum");
            if (jumlahBobot != null) {
                try { s.jumlahBobot = Float.parseFloat(jumlahBobot.trim()); } catch (Throwable t) { }
            }
            // ConstraintLayout: semua constraint.
            isiConstraint(s, atr, "app:layout_constraintStart_toStartOf", "start", true);
            isiConstraint(s, atr, "app:layout_constraintStart_toEndOf", "end", true);
            isiConstraint(s, atr, "app:layout_constraintLeft_toLeftOf", "left", true);
            isiConstraint(s, atr, "app:layout_constraintLeft_toRightOf", "right", true);
            isiConstraint(s, atr, "app:layout_constraintEnd_toEndOf", "end", false);
            isiConstraint(s, atr, "app:layout_constraintEnd_toStartOf", "start", false);
            isiConstraint(s, atr, "app:layout_constraintRight_toRightOf", "right", false);
            isiConstraint(s, atr, "app:layout_constraintRight_toLeftOf", "left", false);
            isiConstraintV(s, atr, "app:layout_constraintTop_toTopOf", "top", true);
            isiConstraintV(s, atr, "app:layout_constraintTop_toBottomOf", "bottom", true);
            isiConstraintV(s, atr, "app:layout_constraintBottom_toBottomOf", "bottom", false);
            isiConstraintV(s, atr, "app:layout_constraintBottom_toTopOf", "top", false);
            String baseline = ambilAtr(atr, "app:layout_constraintBaseline_toBaselineOf");
            if (baseline != null) s.kBaseline = idTarget(baseline);
            s.biasH = ambilPecahan(ambilAtr(atr, "app:layout_constraintHorizontal_bias"), 0.5f);
            s.biasV = ambilPecahan(ambilAtr(atr, "app:layout_constraintVertical_bias"), 0.5f);
            String rantaiH = ambilAtr(atr, "app:layout_constraintHorizontal_chainStyle");
            if (rantaiH != null) s.rantaiH = rantaiH.trim();
            String rantaiV = ambilAtr(atr, "app:layout_constraintVertical_chainStyle");
            if (rantaiV != null) s.rantaiV = rantaiV.trim();
            String persenL = ambilAtr(atr, "app:layout_constraintWidth_percent");
            if (persenL != null) {
                try { s.persenLebar = Float.parseFloat(persenL.trim()); } catch (Throwable t) { }
            }
            String persenT = ambilAtr(atr, "app:layout_constraintHeight_percent");
            if (persenT != null) {
                try { s.persenTinggi = Float.parseFloat(persenT.trim()); } catch (Throwable t) { }
            }
            s.rasioDimensi = uraiRasio(ambilAtr(atr, "app:layout_constraintDimensionRatio"));
            // Guideline / Barrier / Flow.
            if (nama.endsWith("Guideline")) {
                String go = ambilAtr(atr, "android:orientation");
                if (go != null) s.panduanOrientasi = go.trim();
                String gp = ambilAtr(atr, "app:layout_constraintGuide_percent");
                if (gp != null) {
                    try { s.panduanPersen = Float.parseFloat(gp.trim()); } catch (Throwable t) { }
                }
                s.panduanAwalDp = ambilDp(ambilAtr(atr, "app:layout_constraintGuide_begin"));
                if (s.panduanAwalDp < 0) s.panduanAwalDp = ambilDp(ambilAtr(atr, "android:layout_constraintGuide_begin"));
                s.panduanAkhirDp = ambilDp(ambilAtr(atr, "app:layout_constraintGuide_end"));
            }
            if (nama.endsWith("Barrier")) {
                String arah = ambilAtr(atr, "app:barrierDirection");
                if (arah != null) s.penghalangArah = arah.trim();
                String rujuk = ambilAtr(atr, "app:constraint_referenced_ids");
                if (rujuk != null) s.rujukan = rujuk.trim();
            }
            if (nama.endsWith("Flow")) {
                String rujuk = ambilAtr(atr, "app:constraint_referenced_ids");
                if (rujuk != null) s.rujukan = rujuk.trim();
                int ch = ambilDp(ambilAtr(atr, "app:flow_horizontalGap"));
                if (ch >= 0) s.celahH = ch;
                int cv = ambilDp(ambilAtr(atr, "app:flow_verticalGap"));
                if (cv >= 0) s.celahV = cv;
                String fo = ambilAtr(atr, "app:flow_orientation");
                if (fo != null) s.orientasi = fo.trim();
            }
            if (tumpukan.size() == 0) {
                akar = s;
            } else {
                tumpukan.get(tumpukan.size() - 1).anak.add(s);
            }
            if (!tutupSendiri) tumpukan.add(s);
        }
        return akar;
    }

    // ================================================================
    // Jetpack Compose (teks Kotlin) -> pohon simpul yang sama.
    // ================================================================

    /** Urai kode Kotlin Compose menjadi pohon simpul (akar = wadah terluar). */
    public static Simpul uraiCompose(String kode) {
        if (kode == null) return null;
        PenguraiCompose p = new PenguraiCompose(kode);
        List<Simpul> atas = p.uraiDaftar((char) 0);
        if (atas.size() == 0) return null;
        if (atas.size() == 1) return atas.get(0);
        Simpul bungkus = new Simpul();
        bungkus.nama = "Column";
        bungkus.orientasi = "vertical";
        bungkus.lebarMatch = true;
        bungkus.anak = atas;
        return bungkus;
    }

    /** Pengurai kecil untuk panggilan composable + rantai Modifier. */
    private static class PenguraiCompose {
        private final String src;
        private int pos = 0;

        PenguraiCompose(String src) {
            this.src = src;
        }

        private static boolean wadah(String nama) {
            return "Column".equals(nama) || "Row".equals(nama) || "Box".equals(nama)
                    || "LazyColumn".equals(nama) || "LazyRow".equals(nama)
                    || "Scaffold".equals(nama) || "Surface".equals(nama)
                    || "Card".equals(nama) || "ElevatedCard".equals(nama)
                    || "OutlinedCard".equals(nama) || "Button".equals(nama)
                    || "OutlinedButton".equals(nama) || "TextButton".equals(nama)
                    || "IconButton".equals(nama) || "FloatingActionButton".equals(nama);
        }

        private static boolean daun(String nama) {
            return "Text".equals(nama) || "TextField".equals(nama)
                    || "OutlinedTextField".equals(nama) || "BasicTextField".equals(nama)
                    || "Image".equals(nama) || "Icon".equals(nama) || "Spacer".equals(nama)
                    || "Divider".equals(nama) || "HorizontalDivider".equals(nama)
                    || "VerticalDivider".equals(nama) || "Checkbox".equals(nama)
                    || "Switch".equals(nama) || "Slider".equals(nama)
                    || "CircularProgressIndicator".equals(nama)
                    || "LinearProgressIndicator".equals(nama);
        }

        private void lewatiSpasi() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { pos++; continue; }
                if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                    while (pos < src.length() && src.charAt(pos) != '\n') pos++;
                    continue;
                }
                if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                    pos += 2;
                    while (pos + 1 < src.length()
                            && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) pos++;
                    pos = Math.min(src.length(), pos + 2);
                    continue;
                }
                break;
            }
        }

        private char lihat() {
            lewatiSpasi();
            return pos < src.length() ? src.charAt(pos) : (char) 0;
        }

        private String bacaIdent() {
            lewatiSpasi();
            int awal = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9') || c == '_') pos++;
                else break;
            }
            return src.substring(awal, pos);
        }

        /** Baca argumen "(...)" yang seimbang (tahu string) -> isi mentah. */
        private String bacaArgumen() {
            lewatiSpasi();
            if (pos >= src.length() || src.charAt(pos) != '(') return "";
            int awal = pos;
            int dalam = 0;
            boolean dalamString = false;
            boolean lolos = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (dalamString) {
                    if (lolos) lolos = false;
                    else if (c == '\\') lolos = true;
                    else if (c == '"') dalamString = false;
                } else if (c == '"') {
                    dalamString = true;
                } else if (c == '(') {
                    dalam++;
                } else if (c == ')') {
                    dalam--;
                    if (dalam == 0) {
                        pos++;
                        return src.substring(awal + 1, pos - 1);
                    }
                }
                pos++;
            }
            return src.substring(awal + 1);
        }

        /** String literal pertama di argumen (untuk Text/Button). */
        private static String literalPertama(String argumen) {
            if (argumen == null) return "";
            boolean lolos = false;
            for (int i = 0; i < argumen.length(); i++) {
                char c = argumen.charAt(i);
                if (lolos) { lolos = false; continue; }
                if (c == '\\') { lolos = true; continue; }
                if (c == '"') {
                    int j = i + 1;
                    StringBuilder b = new StringBuilder();
                    while (j < argumen.length()) {
                        char d = argumen.charAt(j);
                        if (d == '\\' && j + 1 < argumen.length()) {
                            b.append(argumen.charAt(j + 1));
                            j += 2;
                            continue;
                        }
                        if (d == '"') break;
                        b.append(d);
                        j++;
                    }
                    return b.toString();
                }
            }
            return "";
        }

        private static int cariDp(String teks, int dari) {
            // Angka diikuti ".dp" mulai dari indeks tertentu.
            java.util.regex.Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\.dp")
                    .matcher(teks);
            if (m.find(dari)) {
                try { return (int) Math.round(Double.parseDouble(m.group(1))); }
                catch (Throwable t) { return -1; }
            }
            return -1;
        }

        private static float cariPecahan(String teks, int dari) {
            java.util.regex.Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*f")
                    .matcher(teks);
            if (m.find(dari)) {
                try { return Float.parseFloat(m.group(1)); } catch (Throwable t) { return -1; }
            }
            return -1;
        }

        /** Terapkan rantai Modifier pada simpul (ukuran, bobot, isi). */
        private static void terapkanModifier(Simpul s, String argumen) {
            if (argumen == null) return;
            int m = argumen.indexOf("Modifier");
            if (m < 0) return;
            String rantai = argumen.substring(m);
            // fillMaxSize / fillMaxWidth / fillMaxHeight
            int idx = rantai.indexOf(".fillMaxSize");
            if (idx >= 0) { s.lebarMatch = true; s.tinggiMatch = true; }
            idx = rantai.indexOf(".fillMaxWidth");
            if (idx >= 0) {
                int buka = rantai.indexOf('(', idx);
                int tutup = buka >= 0 ? rantai.indexOf(')', buka) : -1;
                s.lebarMatch = true;
                if (buka >= 0 && tutup > buka) {
                    float f = cariPecahan(rantai.substring(buka, tutup), 0);
                    if (f > 0 && f < 1) s.persenLebar = f;
                    else if (f == 1) s.persenLebar = -1;
                }
            }
            idx = rantai.indexOf(".fillMaxHeight");
            if (idx >= 0) {
                int buka = rantai.indexOf('(', idx);
                int tutup = buka >= 0 ? rantai.indexOf(')', buka) : -1;
                s.tinggiMatch = true;
                if (buka >= 0 && tutup > buka) {
                    float f = cariPecahan(rantai.substring(buka, tutup), 0);
                    if (f > 0 && f < 1) s.persenTinggi = f;
                }
            }
            // size / width / height — ambil segmennya satu per satu.
            int dari = 0;
            while (true) {
                int titik = rantai.indexOf('.', dari);
                if (titik < 0) break;
                int buka = rantai.indexOf('(', titik);
                if (buka < 0) break;
                int tutup = rantai.indexOf(')', buka);
                if (tutup < 0) break;
                String namaSeg = rantai.substring(titik + 1, buka).trim();
                String isi = rantai.substring(buka + 1, tutup);
                if ("size".equals(namaSeg)) {
                    java.util.regex.Matcher mm = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\.dp").matcher(isi);
                    int n = 0;
                    while (mm.find()) {
                        try {
                            int v = (int) Math.round(Double.parseDouble(mm.group(1)));
                            if (n == 0) s.lebarDp = v;
                            else if (n == 1) s.tinggiDp = v;
                        } catch (Throwable t) { }
                        n++;
                    }
                } else if ("width".equals(namaSeg)) {
                    int v = cariDp(isi, 0);
                    if (v >= 0) s.lebarDp = v;
                } else if ("height".equals(namaSeg)) {
                    int v = cariDp(isi, 0);
                    if (v >= 0) s.tinggiDp = v;
                } else if ("weight".equals(namaSeg)) {
                    try { s.bobot = Float.parseFloat(isi.split(",")[0].trim().replace("f", "")); }
                    catch (Throwable t) { }
                } else if ("padding".equals(namaSeg)) {
                    // Padding Compose menambah ukuran kotak: perlakukan
                    // sebagai margin supaya posisi anak tetap bergeser.
                    int v = cariDp(isi, 0);
                    if (v >= 0) {
                        s.marginKiri = v; s.marginAtas = v;
                        s.marginKanan = v; s.marginBawah = v;
                    }
                }
                dari = tutup + 1;
            }
        }

        /** Urai daftar panggilan sampai '}' penutup (atau akhir teks). */
        List<Simpul> uraiDaftar(char akhir) {
            List<Simpul> hasil = new ArrayList<Simpul>();
            while (pos < src.length()) {
                lewatiSpasi();
                if (pos >= src.length()) break;
                char c = src.charAt(pos);
                if (akhir != 0 && c == akhir) { pos++; break; }
                if (c == '}' || c == ')' || c == ';' || c == ',') { pos++; continue; }
                if (c == '@') {
                    // Anotasi (@Composable dls.): lewati.
                    pos++;
                    bacaIdent();
                    if (lihat() == '(') bacaArgumen();
                    continue;
                }
                List<Simpul> satu = uraiSatu();
                if (satu != null) hasil.addAll(satu);
                else if (pos < src.length() && src.charAt(pos) != '}' && src.charAt(pos) != 0) {
                    // Token tak dikenal: lewati satu karakter supaya
                    // pengurai tidak macet di tempat.
                    if (pos < src.length()) pos++;
                }
            }
            return hasil;
        }

        /** Urai satu panggilan; kembalikan 0..n simpul (splice bila transparan). */
        private List<Simpul> uraiSatu() {
            lewatiSpasi();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_')) return null;
            String nama = bacaIdent();
            if (nama.length() == 0) return null;
            if ("package".equals(nama) || "import".equals(nama)) {
                // Lewati sampai akhir baris.
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
                return new ArrayList<Simpul>();
            }
            if ("fun".equals(nama)) {
                // fun Nama(...) { isi } — isi disambung ke atas.
                bacaIdent();
                if (lihat() == '(') bacaArgumen();
                lewatiSpasi();
                if (pos < src.length() && src.charAt(pos) == '{') {
                    pos++;
                    return uraiDaftar('}');
                }
                return new ArrayList<Simpul>();
            }
            if ("if".equals(nama) || "when".equals(nama) || "for".equals(nama)
                    || "while".equals(nama) || "else".equals(nama) || "items".equals(nama)
                    || "item".equals(nama)) {
                if (lihat() == '(') bacaArgumen();
                lewatiSpasi();
                if (pos < src.length() && src.charAt(pos) == '{') {
                    pos++;
                    return uraiDaftar('}');
                }
                return new ArrayList<Simpul>();
            }
            // Panggilan biasa: nama(...)
            if (lihat() != '(') {
                // Bukan panggilan (mis. "val x = ..."): lewati sampai
                // akhir baris agar tidak macet.
                while (pos < src.length() && src.charAt(pos) != '\n'
                        && src.charAt(pos) != ';') pos++;
                return new ArrayList<Simpul>();
            }
            String argumen = bacaArgumen();
            List<Simpul> anakLambda = new ArrayList<Simpul>();
            lewatiSpasi();
            if (pos < src.length() && src.charAt(pos) == '{') {
                pos++;
                anakLambda = uraiDaftar('}');
            }
            boolean dikenal = wadah(nama) || daun(nama);
            if (!dikenal) {
                // Panggilan composable buatan pengguna (mis. LayarUtama()):
                // isinya tetap dipakai, simpulnya sendiri tidak dibuat.
                return anakLambda;
            }
            Simpul s = new Simpul();
            s.nama = nama;
            if ("Column".equals(nama) || "LazyColumn".equals(nama)) s.orientasi = "vertical";
            else if ("Row".equals(nama) || "LazyRow".equals(nama)) s.orientasi = "horizontal";
            else if ("Scaffold".equals(nama)) s.orientasi = "vertical";
            s.teks = literalPertama(argumen);
            terapkanModifier(s, argumen);
            if ("Text".equals(nama) || "Button".equals(nama) || "OutlinedButton".equals(nama)
                    || "TextButton".equals(nama)) {
                // Teks tombol ada di anak Text-nya.
                if (s.teks.length() == 0 && anakLambda.size() > 0) {
                    s.teks = anakLambda.get(0).teks;
                }
            }
            if ("Spacer".equals(nama) && s.lebarDp < 0 && s.tinggiDp < 0
                    && !s.lebarMatch && !s.tinggiMatch && s.bobot < 0) {
                s.lebarDp = 0;
                s.tinggiDp = 0;
            }
            s.anak = anakLambda;
            List<Simpul> hasil = new ArrayList<Simpul>();
            hasil.add(s);
            return hasil;
        }
    }

    /** Hitung jumlah simpul di pohon (termasuk akar). */
    public static int hitungSimpul(Simpul s) {
        if (s == null) return 0;
        int n = 1;
        for (int i = 0; i < s.anak.size(); i++) n += hitungSimpul(s.anak.get(i));
        return n;
    }

    /** Lebar perkiraan wrap_content dari teks (7 dp per huruf, min 48 dp). */
    static int lebarTeks(String teks) {
        if (teks == null) return 48;
        return Math.max(48, teks.length() * 7 + 24);
    }

    /** Tinggi perkiraan wrap_content (48 dp tombol/teks, 56 dp kolom input). */
    static int tinggiTeks(Simpul s) {
        if (s.nama.endsWith("EditText") || "TextField".equals(s.nama)
                || "OutlinedTextField".equals(s.nama)) return 56;
        return 48;
    }

    private static boolean adalahConstraint(Simpul s) {
        return s.nama.endsWith("ConstraintLayout");
    }

    private static boolean adalahKolom(Simpul s) {
        return s.nama.endsWith("LinearLayout") && !"horizontal".equals(s.orientasi)
                || "Column".equals(s.nama) || "LazyColumn".equals(s.nama)
                || "Scaffold".equals(s.nama);
    }

    private static boolean adalahBaris(Simpul s) {
        return s.nama.endsWith("LinearLayout") && "horizontal".equals(s.orientasi)
                || "Row".equals(s.nama) || "LazyRow".equals(s.nama);
    }

    /** Lebar intrinsik anak (tanpa bobot): dp / persen / match / wrap. */
    private static int lebarIntrinsik(Simpul s, int lebarInduk) {
        if (s.lebarDp >= 0) return s.lebarDp;
        if (s.persenLebar >= 0) return (int) Math.round(lebarInduk * s.persenLebar);
        if (s.lebarMatch) return Math.max(0, lebarInduk - s.marginKiri - s.marginKanan);
        return lebarTeks(s.teks);
    }

    private static int tinggiIntrinsik(Simpul s, int tinggiInduk) {
        if (s.tinggiDp >= 0) return s.tinggiDp;
        if (s.persenTinggi >= 0 && tinggiInduk > 0) {
            return (int) Math.round(tinggiInduk * s.persenTinggi);
        }
        if (s.tinggiMatch) return tinggiInduk > 0 ? Math.max(0, tinggiInduk - s.marginAtas - s.marginBawah) : 48;
        return tinggiTeks(s);
    }

    /**
     * Simulasikan kotak tiap simpul pada layar selebar lebarLayarDp.
     * Mengisi x/y/w/h; simpul yang posisinya tidak bisa dihitung
     * mendapat x = -1.
     */
    public static void hitungKotak(Simpul akar, int lebarLayarDp) {
        if (akar == null) return;
        akar.x = 0;
        akar.y = 0;
        akar.w = akar.lebarDp >= 0 ? akar.lebarDp
                : akar.persenLebar >= 0 ? (int) Math.round(lebarLayarDp * akar.persenLebar)
                : lebarLayarDp;
        akar.h = akar.tinggiDp >= 0 ? akar.tinggiDp : -1;
        hitungAnak(akar, lebarLayarDp);
        if (akar.h < 0) {
            int bawah = 0;
            for (int i = 0; i < akar.anak.size(); i++) {
                Simpul a = akar.anak.get(i);
                if (a.y >= 0 && a.h >= 0) bawah = Math.max(bawah, a.y + a.h);
            }
            akar.h = bawah > 0 ? bawah : tinggiTeks(akar);
        }
    }

    private static void hitungAnak(Simpul induk, int lebarLayarDp) {
        if (adalahConstraint(induk)) {
            hitungConstraint(induk, lebarLayarDp);
            return;
        }
        boolean linear = adalahKolom(induk) || adalahBaris(induk);
        boolean vertikal = !adalahBaris(induk);
        if (!linear) {
            // FrameLayout / Box / RelativeLayout / wadah lain: anak
            // menumpuk di titik yang sama — di sanalah tabrakan bisa
            // terjadi dan harus dilaporkan.
            for (int i = 0; i < induk.anak.size(); i++) {
                Simpul s = induk.anak.get(i);
                s.w = lebarIntrinsik(s, induk.w);
                s.h = tinggiIntrinsik(s, induk.h);
                // layout_below (RelativeLayout) digeser ke bawah target.
                Simpul target = null;
                if (s.diBawah.length() > 0) target = cariAnak(induk, s.diBawah);
                if (target != null && target.y >= 0 && target.h >= 0) {
                    s.x = induk.x + s.marginKiri;
                    s.y = target.y + target.h + s.marginAtas;
                } else {
                    s.x = induk.x + s.marginKiri;
                    s.y = induk.y + s.marginAtas;
                }
                hitungAnak(s, lebarLayarDp);
            }
            return;
        }
        // ---- LinearLayout / Column / Row dengan bobot ----
        int n = induk.anak.size();
        int[] utama = new int[n];   // ukuran sumbu utama (tanpa bobot)
        int[] silang = new int[n];  // ukuran sumbu silang
        float totalBobot = 0;
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            if (vertikal) {
                boolean nolBobot = s.bobot > 0 && s.tinggiDp < 0 && !s.tinggiMatch
                        && !s.tinggiWrap && s.tinggiNolConstraint;
                // 0dp + bobot: ukuran dasar 0, sisanya dari bobot.
                utama[i] = (s.bobot > 0 && s.tinggiDp < 0 && !s.tinggiMatch)
                        ? 0 : tinggiIntrinsik(s, induk.h);
                if (s.tinggiDp < 0 && s.tinggiMatch && s.bobot > 0) utama[i] = 0;
                silang[i] = lebarIntrinsik(s, induk.w);
                if (nolBobot) utama[i] = 0;
            } else {
                utama[i] = (s.bobot > 0 && s.lebarDp < 0 && !s.lebarMatch)
                        ? 0 : lebarIntrinsik(s, induk.w);
                if (s.lebarDp < 0 && s.lebarMatch && s.bobot > 0) utama[i] = 0;
                silang[i] = tinggiIntrinsik(s, induk.h);
            }
            if (s.bobot > 0) totalBobot += s.bobot;
        }
        int ukuranInduk = vertikal ? induk.h : induk.w;
        int tetap = 0;
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            tetap += utama[i] + (vertikal ? s.marginAtas + s.marginBawah
                    : s.marginKiri + s.marginKanan);
        }
        float penyebut = induk.jumlahBobot > 0 ? induk.jumlahBobot : totalBobot;
        int sisa = ukuranInduk > 0 ? Math.max(0, ukuranInduk - tetap) : 0;
        int kursor = 0;
        int tinggiInduk = 0;
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            int ukuranUtama = utama[i];
            if (s.bobot > 0 && penyebut > 0 && sisa > 0) {
                ukuranUtama += (int) Math.round(sisa * s.bobot / penyebut);
            }
            if (vertikal) {
                s.w = silang[i];
                s.h = ukuranUtama;
                s.x = induk.x + s.marginKiri;
                s.y = induk.y + kursor + s.marginAtas;
                kursor += s.marginAtas + s.h + s.marginBawah;
            } else {
                s.w = ukuranUtama;
                s.h = silang[i];
                s.x = induk.x + kursor + s.marginKiri;
                s.y = induk.y + s.marginAtas;
                kursor += s.marginKiri + s.w + s.marginKanan;
            }
            tinggiInduk = Math.max(tinggiInduk, kursor);
            hitungAnak(s, lebarLayarDp);
        }
        if (induk.tinggiDp < 0 && !induk.tinggiMatch && vertikal) {
            induk.h = n == 0 ? tinggiTeks(induk) : tinggiInduk;
        }
        if (induk.tinggiDp < 0 && !induk.tinggiMatch && !vertikal) {
            int maks = 0;
            for (int i = 0; i < n; i++) {
                maks = Math.max(maks, induk.anak.get(i).h
                        + induk.anak.get(i).marginAtas + induk.anak.get(i).marginBawah);
            }
            induk.h = n == 0 ? tinggiTeks(induk) : maks;
        }
    }

    // ================================================================
    // ConstraintLayout
    // ================================================================

    private static Simpul cariAnak(Simpul induk, String id) {
        if (id == null || id.length() == 0) return null;
        for (int i = 0; i < induk.anak.size(); i++) {
            if (id.equals(induk.anak.get(i).id)) return induk.anak.get(i);
        }
        return null;
    }

    private static boolean adalahGuideline(Simpul s) {
        return s.nama.endsWith("Guideline");
    }

    private static boolean adalahBarrier(Simpul s) {
        return s.nama.endsWith("Barrier");
    }

    private static boolean adalahFlow(Simpul s) {
        return s.nama.endsWith("Flow");
    }

    /** Posisi Guideline: x (vertikal) atau y (horizontal). */
    private static int posisiGuideline(Simpul g, Simpul induk) {
        if ("vertical".equals(g.panduanOrientasi)) {
            if (g.panduanPersen >= 0) return (int) Math.round(induk.w * g.panduanPersen);
            if (g.panduanAwalDp >= 0) return g.panduanAwalDp;
            if (g.panduanAkhirDp >= 0) return Math.max(0, induk.w - g.panduanAkhirDp);
            return 0;
        }
        if (g.panduanPersen >= 0) return (int) Math.round((induk.h > 0 ? induk.h : 0) * g.panduanPersen);
        if (g.panduanAwalDp >= 0) return g.panduanAwalDp;
        if (g.panduanAkhirDp >= 0) return Math.max(0, (induk.h > 0 ? induk.h : 0) - g.panduanAkhirDp);
        return 0;
    }

    /**
     * Tepi jangkar horizontal: target "parent" -> 0 / lebar induk;
     * id simpul -> x / x+w menurut sisi; Guideline vertikal -> posisinya;
     * Barrier kiri/kanan -> tepinya. null bila target belum terhitung.
     */
    private static Integer jangkarX(Simpul induk, String target, String sisi) {
        if (target == null || target.length() == 0) return null;
        if ("parent".equals(target)) {
            if ("start".equals(sisi) || "left".equals(sisi) || "top".equals(sisi)) return Integer.valueOf(induk.x);
            return Integer.valueOf(induk.x + induk.w);
        }
        Simpul t = cariAnak(induk, target);
        if (t == null || t.x < 0) return null;
        if (adalahGuideline(t)) {
            int p = induk.x + posisiGuideline(t, induk);
            return Integer.valueOf(p);
        }
        if (adalahBarrier(t)) {
            Integer tepi = tepiBarrier(induk, t, true);
            if (tepi == null) return null;
            return tepi;
        }
        if ("start".equals(sisi) || "left".equals(sisi)) return Integer.valueOf(t.x);
        return Integer.valueOf(t.x + t.w);
    }

    private static Integer jangkarY(Simpul induk, String target, String sisi) {
        if (target == null || target.length() == 0) return null;
        if ("parent".equals(target)) {
            if ("top".equals(sisi)) return Integer.valueOf(induk.y);
            return Integer.valueOf(induk.y + (induk.h > 0 ? induk.h : 0));
        }
        Simpul t = cariAnak(induk, target);
        if (t == null || t.y < 0) return null;
        if (adalahGuideline(t)) {
            int p = induk.y + posisiGuideline(t, induk);
            return Integer.valueOf(p);
        }
        if (adalahBarrier(t)) {
            Integer tepi = tepiBarrier(induk, t, false);
            if (tepi == null) return null;
            return tepi;
        }
        if ("top".equals(sisi)) return Integer.valueOf(t.y);
        return Integer.valueOf(t.y + t.h);
    }

    /** Tepi Barrier dari id-id yang dirujuknya (null bila ada yang belum terhitung). */
    private static Integer tepiBarrier(Simpul induk, Simpul barrier, boolean horizontal) {
        String[] id = barrier.rujukan.split(",");
        boolean ada = false;
        int tepi = 0;
        for (int i = 0; i < id.length; i++) {
            String nama = id[i].trim();
            if (nama.length() == 0) continue;
            Simpul t = cariAnak(induk, nama);
            if (t == null || t.x < 0 || t.y < 0) return null;
            int nilai;
            if (horizontal) {
                if ("left".equals(barrier.penghalangArah)) nilai = t.x;
                else nilai = t.x + t.w;
            } else {
                if ("top".equals(barrier.penghalangArah)) nilai = t.y;
                else nilai = t.y + t.h;
            }
            if (!ada) { tepi = nilai; ada = true; continue; }
            if (("left".equals(barrier.penghalangArah) || "top".equals(barrier.penghalangArah))) {
                tepi = Math.min(tepi, nilai);
            } else {
                tepi = Math.max(tepi, nilai);
            }
        }
        return ada ? Integer.valueOf(tepi) : null;
    }

    /** Lebar anak ConstraintLayout dari constraint yang sudah bisa dihitung. */
    private static int lebarConstraint(Simpul s, Simpul induk) {
        if (s.lebarDp >= 0) return s.lebarDp;
        if (s.persenLebar >= 0) return (int) Math.round(induk.w * s.persenLebar);
        if (s.lebarMatch) return Math.max(0, induk.w - s.marginKiri - s.marginKanan);
        if (s.lebarNolConstraint) {
            Integer awal = jangkarX(induk, s.kAwal, s.kAwalSisi);
            Integer akhir = jangkarX(induk, s.kAkhir, s.kAkhirSisi);
            if (awal != null && akhir != null) {
                int jarak = akhir.intValue() - awal.intValue() - s.marginKiri - s.marginKanan;
                if (jarak >= 0) return jarak;
            }
            // 0dp dengan satu jangkar saja: berperilaku seperti wrap.
            return lebarTeks(s.teks);
        }
        return lebarTeks(s.teks);
    }

    private static int tinggiConstraint(Simpul s, Simpul induk) {
        if (s.tinggiDp >= 0) return s.tinggiDp;
        if (s.persenTinggi >= 0 && induk.h > 0) return (int) Math.round(induk.h * s.persenTinggi);
        if (s.tinggiMatch) return induk.h > 0 ? Math.max(0, induk.h - s.marginAtas - s.marginBawah) : 48;
        if (s.tinggiNolConstraint) {
            Integer atas = jangkarY(induk, s.kAtas, s.kAtasSisi);
            Integer bawah = jangkarY(induk, s.kBawah, s.kBawahSisi);
            if (atas != null && bawah != null) {
                int jarak = bawah.intValue() - atas.intValue() - s.marginAtas - s.marginBawah;
                if (jarak >= 0) return jarak;
            }
            return tinggiTeks(s);
        }
        return tinggiTeks(s);
    }

    /** Terapkan rasio dimensi bila satu sisi 0dp dan sisi lain sudah diketahui.
     *  Bila kedua jangkar sisi itu sudah terikat dua arah, jarak jangkar
     *  yang menang (rasio tidak menimpa ukuran yang sudah dijepit). */
    private static void terapkanRasio(Simpul s, Simpul induk) {
        if (s.rasioDimensi <= 0) return;
        boolean yDua = s.kAtas.length() > 0 && s.kBawah.length() > 0
                && jangkarY(induk, s.kAtas, s.kAtasSisi) != null
                && jangkarY(induk, s.kBawah, s.kBawahSisi) != null;
        boolean xDua = s.kAwal.length() > 0 && s.kAkhir.length() > 0
                && jangkarX(induk, s.kAwal, s.kAwalSisi) != null
                && jangkarX(induk, s.kAkhir, s.kAkhirSisi) != null;
        if (s.tinggiNolConstraint && s.w > 0 && !yDua) {
            s.h = (int) Math.round(s.w / s.rasioDimensi);
        } else if (s.lebarNolConstraint && s.h > 0 && !xDua) {
            s.w = (int) Math.round(s.h * s.rasioDimensi);
        }
    }

    private static boolean punyaConstraint(Simpul s) {
        return s.kAwal.length() > 0 || s.kAkhir.length() > 0
                || s.kAtas.length() > 0 || s.kBawah.length() > 0
                || s.kBaseline.length() > 0;
    }

    private static void hitungConstraint(Simpul induk, int lebarLayarDp) {
        int n = induk.anak.size();
        // 1. Guideline / Barrier / Flow diposisikan dulu (kotak nol).
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            if (adalahGuideline(s)) {
                s.w = 0;
                s.h = 0;
                if ("vertical".equals(s.panduanOrientasi)) {
                    s.x = induk.x + posisiGuideline(s, induk);
                    s.y = induk.y;
                } else {
                    s.x = induk.x;
                    s.y = induk.y + posisiGuideline(s, induk);
                }
            } else if (adalahBarrier(s) || adalahFlow(s)) {
                s.w = 0;
                s.h = 0;
                s.x = induk.x;
                s.y = induk.y;
            }
        }
        // 2. Flow: anak yang dirujuk Flow dan tidak punya constraint
        //    sendiri disusun membungkus (baris mendatar, pindah baris
        //    bila melewati lebar induk).
        for (int i = 0; i < n; i++) {
            Simpul f = induk.anak.get(i);
            if (!adalahFlow(f) || f.rujukan.length() == 0) continue;
            String[] id = f.rujukan.split(",");
            int cx = induk.x;
            int cy = induk.y;
            int tinggiBaris = 0;
            boolean horizontal = !"vertical".equals(f.orientasi);
            for (int k = 0; k < id.length; k++) {
                Simpul s = cariAnak(induk, id[k].trim());
                if (s == null || punyaConstraint(s)) continue;
                s.w = lebarConstraint(s, induk);
                s.h = tinggiConstraint(s, induk);
                if (horizontal) {
                    if (cx > induk.x && cx + s.w > induk.x + induk.w) {
                        cx = induk.x;
                        cy += tinggiBaris + f.celahV;
                        tinggiBaris = 0;
                    }
                    s.x = cx + s.marginKiri;
                    s.y = cy + s.marginAtas;
                    cx += s.w + s.marginKiri + s.marginKanan + f.celahH;
                    tinggiBaris = Math.max(tinggiBaris, s.h + s.marginAtas + s.marginBawah);
                } else {
                    if (cy > induk.y && induk.h > 0 && cy + s.h > induk.y + induk.h) {
                        cy = induk.y;
                        cx += 0;
                    }
                    s.x = cx + s.marginKiri;
                    s.y = cy + s.marginAtas;
                    cy += s.h + s.marginAtas + s.marginBawah + f.celahV;
                }
            }
        }
        // 3. Rantai: kelompokkan anak yang saling terikat berurutan.
        boolean[] rantaiX = new boolean[n];
        boolean[] rantaiY = new boolean[n];
        selesaikanRantai(induk, true, rantaiX);
        selesaikanRantai(induk, false, rantaiY);
        // 4. Iterasi: selesaikan x/y tiap anak dari jangkarnya.
        //    Anak yang jangkarnya menunjuk simpul lain menunggu giliran
        //    berikutnya sampai simpul itu terhitung.
        boolean[] selesaiX = new boolean[n];
        boolean[] selesaiY = new boolean[n];
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            if (adalahGuideline(s) || adalahBarrier(s) || adalahFlow(s)) {
                selesaiX[i] = true;
                selesaiY[i] = true;
            }
            if (s.x >= 0 && s.y >= 0 && s.w >= 0 && s.h >= 0) {
                selesaiX[i] = true;
                selesaiY[i] = true;
            }
            if (rantaiX[i]) selesaiX[i] = true;
            if (rantaiY[i]) selesaiY[i] = true;
        }
        for (int putaran = 0; putaran <= n + 2; putaran++) {
            boolean maju = false;
            for (int i = 0; i < n; i++) {
                Simpul s = induk.anak.get(i);
                if (adalahGuideline(s) || adalahBarrier(s) || adalahFlow(s)) continue;
                if (!selesaiX[i]) {
                    s.w = lebarConstraint(s, induk);
                    terapkanRasio(s, induk);
                    Integer awal = jangkarX(induk, s.kAwal, s.kAwalSisi);
                    Integer akhir = jangkarX(induk, s.kAkhir, s.kAkhirSisi);
                    if (awal != null && akhir != null) {
                        int ruang = akhir.intValue() - awal.intValue()
                                - s.marginKiri - s.marginKanan;
                        if (s.lebarNolConstraint && ruang >= 0) s.w = ruang;
                        s.x = awal.intValue() + s.marginKiri
                                + (int) Math.round(s.biasH * Math.max(0, ruang - s.w));
                        selesaiX[i] = true;
                        maju = true;
                    } else if (awal != null) {
                        s.x = awal.intValue() + s.marginKiri;
                        selesaiX[i] = true;
                        maju = true;
                    } else if (akhir != null) {
                        s.x = akhir.intValue() - s.marginKanan - s.w;
                        selesaiX[i] = true;
                        maju = true;
                    } else if (s.kAwal.length() == 0 && s.kAkhir.length() == 0) {
                        s.x = induk.x + s.marginKiri;
                        selesaiX[i] = true;
                        maju = true;
                    }
                }
                if (!selesaiY[i]) {
                    s.h = tinggiConstraint(s, induk);
                    terapkanRasio(s, induk);
                    Integer atas = jangkarY(induk, s.kAtas, s.kAtasSisi);
                    Integer bawah = jangkarY(induk, s.kBawah, s.kBawahSisi);
                    if (s.kBaseline.length() > 0) {
                        Simpul t = cariAnak(induk, s.kBaseline);
                        if (t != null && t.y >= 0) {
                            s.y = t.y + s.marginAtas;
                            selesaiY[i] = true;
                            maju = true;
                        }
                    } else if (atas != null && bawah != null) {
                        int ruang = bawah.intValue() - atas.intValue()
                                - s.marginAtas - s.marginBawah;
                        if (s.tinggiNolConstraint && ruang >= 0) s.h = ruang;
                        s.y = atas.intValue() + s.marginAtas
                                + (int) Math.round(s.biasV * Math.max(0, ruang - s.h));
                        selesaiY[i] = true;
                        maju = true;
                    } else if (atas != null) {
                        s.y = atas.intValue() + s.marginAtas;
                        selesaiY[i] = true;
                        maju = true;
                    } else if (bawah != null) {
                        s.y = bawah.intValue() - s.marginBawah - s.h;
                        selesaiY[i] = true;
                        maju = true;
                    } else if (s.kAtas.length() == 0 && s.kBawah.length() == 0
                            && s.kBaseline.length() == 0) {
                        s.y = induk.y + s.marginAtas;
                        selesaiY[i] = true;
                        maju = true;
                    }
                }
                if (selesaiX[i] && selesaiY[i]) {
                    // Ukuran 0dp dihitung ulang setelah kedua jangkar
                    // diketahui (mis. tinggi dari jarak atas-bawah).
                    int wBaru = lebarConstraint(s, induk);
                    int hBaru = tinggiConstraint(s, induk);
                    if (s.lebarNolConstraint) s.w = wBaru;
                    if (s.tinggiNolConstraint) s.h = hBaru;
                    terapkanRasio(s, induk);
                }
            }
            if (!maju) break;
        }
        // 5. Barrier: posisinya mengikuti tepi rujukannya.
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            if (!adalahBarrier(s)) continue;
            Integer tepiH = tepiBarrier(induk, s, true);
            Integer tepiV = tepiBarrier(induk, s, false);
            if ("left".equals(s.penghalangArah) || "right".equals(s.penghalangArah)) {
                if (tepiH != null) s.x = tepiH.intValue();
            } else if (tepiV != null) {
                s.y = tepiV.intValue();
            }
        }
        // 6. Anak yang masih belum terhitung: benar-benar tidak tentu
        //    (mis. jangkar menunjuk id yang tidak ada).
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            if (adalahGuideline(s) || adalahBarrier(s) || adalahFlow(s)) continue;
            if (!selesaiX[i] || !selesaiY[i]) {
                s.x = -1;
                s.y = -1;
                if (s.w < 0) s.w = lebarTeks(s.teks);
                if (s.h < 0) s.h = tinggiTeks(s);
            }
        }
        // 7. Rekursi ke anak + tinggi induk bila wrap_content.
        for (int i = 0; i < n; i++) {
            Simpul s = induk.anak.get(i);
            if (adalahGuideline(s) || adalahBarrier(s) || adalahFlow(s)) continue;
            hitungAnak(s, lebarLayarDp);
        }
        if (induk.tinggiDp < 0 && !induk.tinggiMatch) {
            int bawah = 0;
            for (int i = 0; i < n; i++) {
                Simpul s = induk.anak.get(i);
                if (s.y >= 0 && s.h >= 0) bawah = Math.max(bawah, s.y + s.h + s.marginBawah);
            }
            induk.h = bawah > induk.y ? bawah - induk.y : tinggiTeks(induk);
        }
    }

    /**
     * Selesaikan satu arah rantai (horizontal bila mendatar).
     * Menandai anggota rantai di sudah[] dan mengisi x (atau y).
     */
    private static void selesaikanRantai(Simpul induk, boolean mendatar, boolean[] sudah) {
        int n = induk.anak.size();
        for (int i = 0; i < n; i++) {
            Simpul kepala = induk.anak.get(i);
            if (adalahGuideline(kepala) || adalahBarrier(kepala) || adalahFlow(kepala)) continue;
            if (mendatar ? sudah[i] : sudah[i]) continue;
            // Kepala rantai: punya gaya rantai ATAU terikat ke induk di
            // satu sisi dan ke simpul lain di sisi lainnya.
            String gaya = mendatar ? kepala.rantaiH : kepala.rantaiV;
            // Kumpulkan anggota berurutan: tiap anggota berikutnya
            // jangkar awalnya menunjuk anggota sebelumnya.
            List<Simpul> anggota = new ArrayList<Simpul>();
            List<Integer> indeks = new ArrayList<Integer>();
            anggota.add(kepala);
            indeks.add(Integer.valueOf(i));
            Simpul kini = kepala;
            while (true) {
                String targetAkhir = mendatar ? kini.kAkhir : kini.kBawah;
                if (targetAkhir.length() == 0 || "parent".equals(targetAkhir)) break;
                Simpul berikut = cariAnak(induk, targetAkhir);
                if (berikut == null) break;
                String awalBerikut = mendatar ? berikut.kAwal : berikut.kAtas;
                if (!kini.id.equals(awalBerikut) && !kini.id.equals(idTarget(awalBerikut))) break;
                // Berikut harus menunjuk balik ke kini sebagai jangkar awal.
                if (!kini.id.equals(berikut.kAwal) && mendatar) break;
                if (!kini.id.equals(berikut.kAtas) && !mendatar) break;
                anggota.add(berikut);
                for (int k = 0; k < n; k++) {
                    if (induk.anak.get(k) == berikut) indeks.add(Integer.valueOf(k));
                }
                kini = berikut;
            }
            if (anggota.size() < 2) continue;
            // Ekor rantai harus terikat ke induk (atau Guideline).
            String ekorAkhir = mendatar ? kini.kAkhir : kini.kBawah;
            if (!"parent".equals(ekorAkhir)) {
                Simpul ekorTarget = cariAnak(induk, ekorAkhir);
                if (ekorTarget == null || !adalahGuideline(ekorTarget)) continue;
            }
            String awalKepala = mendatar ? kepala.kAwal : kepala.kAtas;
            if (!"parent".equals(awalKepala)) {
                Simpul awalTarget = cariAnak(induk, awalKepala);
                if (awalTarget == null || !adalahGuideline(awalTarget)) continue;
            }
            if (gaya.length() == 0) gaya = "spread";
            // Ukuran tiap anggota.
            int total = 0;
            for (int k = 0; k < anggota.size(); k++) {
                Simpul s = anggota.get(k);
                if (mendatar) {
                    s.w = lebarConstraint(s, induk);
                    total += s.w + s.marginKiri + s.marginKanan;
                } else {
                    s.h = tinggiConstraint(s, induk);
                    total += s.h + s.marginAtas + s.marginBawah;
                }
            }
            Integer awalPos = mendatar
                    ? jangkarX(induk, awalKepala, mendatar ? kepala.kAwalSisi : "")
                    : jangkarY(induk, awalKepala, mendatar ? "" : kepala.kAtasSisi);
            Integer akhirPos = mendatar
                    ? jangkarX(induk, ekorAkhir, mendatar ? kini.kAkhirSisi : "")
                    : jangkarY(induk, ekorAkhir, mendatar ? "" : kini.kBawahSisi);
            if (awalPos == null || akhirPos == null) continue;
            int ruang = akhirPos.intValue() - awalPos.intValue();
            int sisa = Math.max(0, ruang - total);
            int jumlah = anggota.size();
            int celah;
            int mulai;
            if ("spread_inside".equals(gaya)) {
                celah = jumlah > 1 ? sisa / (jumlah - 1) : 0;
                mulai = awalPos.intValue();
            } else if ("packed".equals(gaya)) {
                celah = 0;
                float bias = mendatar ? kepala.biasH : kepala.biasV;
                mulai = awalPos.intValue() + (int) Math.round(bias * sisa);
            } else { // spread
                celah = sisa / (jumlah + 1);
                mulai = awalPos.intValue() + celah;
            }
            int kursor = mulai;
            for (int k = 0; k < anggota.size(); k++) {
                Simpul s = anggota.get(k);
                if (mendatar) {
                    s.x = kursor + s.marginKiri;
                    kursor += s.marginKiri + s.w + s.marginKanan + celah;
                } else {
                    s.y = kursor + s.marginAtas;
                    kursor += s.marginAtas + s.h + s.marginBawah + celah;
                }
                sudah[indeks.get(k).intValue()] = true;
            }
        }
    }

    /** Nama tampilan simpul: id bila ada, kalau tidak nama + teks. */
    public static String namaTampil(Simpul s) {
        if (s.id != null && s.id.length() > 0) return s.id;
        if (s.teks != null && s.teks.length() > 0) return s.nama + " \"" + s.teks + "\"";
        return s.nama;
    }

    /** Narasi posisi tiap elemen (satu kalimat per simpul, urut pohon). */
    public static List<String> narasi(Simpul akar, int lebarLayarDp) {
        List<String> hasil = new ArrayList<String>();
        if (akar == null) return hasil;
        narasiKe(akar, lebarLayarDp, hasil);
        return hasil;
    }

    private static void narasiKe(Simpul s, int lebarLayarDp, List<String> hasil) {
        if (s.x < 0) {
            hasil.add(namaTampil(s) + ": posisi tidak tentu.");
        } else {
            String vertikal = s.y < 100 ? "di atas" : s.y > 400 ? "di bawah" : "di tengah";
            String horizontal = s.x + s.w > lebarLayarDp - 20 ? "di kanan"
                    : s.x < 20 ? "di kiri" : "di tengah";
            int persen = lebarLayarDp > 0 ? (s.w * 100 / lebarLayarDp) : 0;
            hasil.add(namaTampil(s) + " " + vertikal + " " + horizontal
                    + ", lebar " + persen + " persen layar, " + s.w + " dp kali "
                    + s.h + " dp, " + s.y + " dp dari atas.");
        }
        for (int i = 0; i < s.anak.size(); i++) narasiKe(s.anak.get(i), lebarLayarDp, hasil);
    }

    /** Periksa tabrakan dan elemen keluar layar (butuh hitungKotak dulu). */
    public static List<Temuan> periksa(Simpul akar, int lebarLayarDp) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (akar == null) return hasil;
        List<Simpul> semua = new ArrayList<Simpul>();
        kumpul(akar, semua);
        for (int i = 0; i < semua.size(); i++) {
            Simpul a = semua.get(i);
            if (adalahGuideline(a) || adalahBarrier(a) || adalahFlow(a)) continue;
            if (a.x < 0) {
                hasil.add(new Temuan("Tidak tentu",
                        namaTampil(a) + " posisinya tidak bisa dihitung (jangkar menunjuk id yang tidak ada, atau ConstraintSet/MotionLayout yang diubah saat berjalan)."));
                continue;
            }
            if (a.x + a.w > lebarLayarDp) {
                hasil.add(new Temuan("Keluar layar",
                        namaTampil(a) + " melebar sampai " + (a.x + a.w)
                        + " dp, padahal layar hanya " + lebarLayarDp + " dp."));
            }
            for (int j = i + 1; j < semua.size(); j++) {
                Simpul b = semua.get(j);
                if (b.x < 0) continue;
                if (adalahGuideline(b) || adalahBarrier(b) || adalahFlow(b)) continue;
                if (bersaudara(a, b, akar) && tumpangTindih(a, b)) {
                    hasil.add(new Temuan("Tabrakan",
                            namaTampil(a) + " bertabrakan dengan " + namaTampil(b) + "."));
                }
            }
        }
        return hasil;
    }

    private static void kumpul(Simpul s, List<Simpul> keluar) {
        keluar.add(s);
        for (int i = 0; i < s.anak.size(); i++) kumpul(s.anak.get(i), keluar);
    }

    private static boolean bersaudara(Simpul a, Simpul b, Simpul akar) {
        return indukDari(akar, a) == indukDari(akar, b);
    }

    private static Simpul indukDari(Simpul kini, Simpul cari) {
        for (int i = 0; i < kini.anak.size(); i++) {
            if (kini.anak.get(i) == cari) return kini;
            Simpul dalam = indukDari(kini.anak.get(i), cari);
            if (dalam != null) return dalam;
        }
        return null;
    }

    private static boolean tumpangTindih(Simpul a, Simpul b) {
        return a.x < b.x + b.w && b.x < a.x + a.w
                && a.y < b.y + b.h && b.y < a.y + a.h;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int jumlahElemen, List<Temuan> temuan) {
        int tabrakan = 0;
        int keluar = 0;
        if (temuan != null) {
            for (int i = 0; i < temuan.size(); i++) {
                if ("Tabrakan".equals(temuan.get(i).jenis)) tabrakan++;
                if ("Keluar layar".equals(temuan.get(i).jenis)) keluar++;
            }
        }
        int total = temuan == null ? 0 : temuan.size();
        return "Elemen diperiksa: " + jumlahElemen + ". Tabrakan ditemukan: "
                + tabrakan + ". Keluar layar: " + keluar + ". Total temuan: " + total + ".";
    }
}
