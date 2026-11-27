package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generator Server Mikro Lokal dan Backend Tersemat (Fitur 4, daftar Gemini).
 *
 * Kelas murni tanpa Android (tanpa soket) supaya bisa diuji di JVM:
 * model rute teks, pencocokan jalur beserta parameter {nama}, pembangunan
 * respons JSON, penyimpanan JSON datar, kunci API sederhana, dan log
 * permintaan/respons berstempel waktu.
 *
 * Format definisi rute, satu baris per rute:
 *   METODE /jalur -> respons
 * Contoh:
 *   GET /halo -> {"pesan": "halo"}
 *   GET /halo/{nama} -> {"pesan": "halo {nama}"}
 *   POST /tambah {nama} -> tersimpan
 *   GET /rahasia -> {"pesan": "rahasia"} [kunci]
 * {nama} di jalur adalah parameter dari segmen jalur; {nama} sesudah jalur
 * adalah parameter dari query atau isi permintaan. "[kunci]" di akhir baris
 * berarti rute hanya menjawab bila kunci API-nya benar. Respons yang
 * diawali "{" dipakai apa adanya sebagai JSON; respons teks biasa
 * dibungkus menjadi {"pesan": "..."}.
 *
 * Soket (ServerSocket, utas latar) TIDAK ada di sini — ada di
 * ServerMikroActivity sebagai pembungkus tipis. Pembundelan peladen ke
 * dalam APK hasil build BELUM diimplementasikan di putaran ini.
 */
public class ServerMikro {

    /** Satu rute API: metode, jalur, pola respons, dan perlu kunci atau tidak. */
    public static class Rute {
        public final String metode;
        public final String jalur;
        public final String polaRespons;
        public final boolean butuhKunci;
        public final List<String> paramNama;

        Rute(String metode, String jalur, String polaRespons, boolean butuhKunci,
             List<String> paramNama) {
            this.metode = metode;
            this.jalur = jalur;
            this.polaRespons = polaRespons;
            this.butuhKunci = butuhKunci;
            this.paramNama = paramNama;
        }

        /** Segmen jalur tanpa garis miring kosong, mis. "/halo/{nama}" -> ["halo", "{nama}"]. */
        public String[] segmen() {
            return segmenJalur(jalur);
        }
    }

    /** Hasil penanganan satu permintaan: kode status HTTP dan badan JSON. */
    public static class Hasil {
        public final int kode;
        public final String badan;
        public final Rute rute;

        Hasil(int kode, String badan, Rute rute) {
            this.kode = kode;
            this.badan = badan;
            this.rute = rute;
        }
    }

    /** Log permintaan/respons sebagai teks berstempel waktu. */
    public static class LogServer {
        private final List<String> baris = new ArrayList<String>();

        public synchronized void catat(String metode, String jalur, int kode, String badan) {
            String jam = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String b = badan == null ? "" : badan;
            if (b.length() > 200) b = b.substring(0, 200) + "...";
            baris.add("[" + jam + "] " + metode + " " + jalur + " -> " + kode + " " + b);
        }

        public synchronized String teks() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < baris.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(baris.get(i));
            }
            return sb.toString();
        }

        public synchronized int jumlah() {
            return baris.size();
        }

        public synchronized void bersihkan() {
            baris.clear();
        }
    }

    private static final Pattern POLA_PARAM = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");

    /**
     * Mengurai teks definisi menjadi daftar rute. Baris kosong, baris
     * komentar (#), dan baris rusak (tanpa "->", metode tidak dikenal,
     * jalur tidak diawali "/") dilewati diam-diam — teks yang seluruhnya
     * rusak menghasilkan daftar kosong, bukan galat.
     */
    public static List<Rute> uraiDefinisi(String teks) {
        List<Rute> hasil = new ArrayList<Rute>();
        if (teks == null) return hasil;
        String[] baris = teks.split("\n");
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i].trim();
            if (b.length() == 0 || b.startsWith("#")) continue;
            int panah = b.indexOf("->");
            if (panah < 0) continue;
            String kiri = b.substring(0, panah).trim();
            String kanan = b.substring(panah + 2).trim();
            boolean butuhKunci = b.contains("[kunci]");
            kiri = kiri.replace("[kunci]", "").trim();
            kanan = kanan.replace("[kunci]", "").trim();
            if (kanan.length() == 0) continue;
            String[] token = kiri.split("\\s+");
            if (token.length < 2) continue;
            String metode = token[0].toUpperCase(Locale.US);
            if (!"GET".equals(metode) && !"POST".equals(metode) && !"PUT".equals(metode)
                    && !"DELETE".equals(metode) && !"PATCH".equals(metode)) continue;
            String jalur = normalisasiJalur(token[1]);
            if (jalur == null) continue;
            List<String> paramNama = new ArrayList<String>();
            Matcher m = POLA_PARAM.matcher(jalur);
            while (m.find()) {
                if (!paramNama.contains(m.group(1))) paramNama.add(m.group(1));
            }
            for (int t = 2; t < token.length; t++) {
                Matcher m2 = POLA_PARAM.matcher(token[t]);
                while (m2.find()) {
                    if (!paramNama.contains(m2.group(1))) paramNama.add(m2.group(1));
                }
            }
            hasil.add(new Rute(metode, jalur, kanan, butuhKunci, paramNama));
        }
        return hasil;
    }

    /** Jalur harus diawali "/"; garis miring akhir dibuang (kecuali "/"). Null bila tidak sah. */
    public static String normalisasiJalur(String jalur) {
        if (jalur == null) return null;
        String j = jalur.trim();
        int tanya = j.indexOf('?');
        if (tanya >= 0) j = j.substring(0, tanya);
        int pagar = j.indexOf('#');
        if (pagar >= 0) j = j.substring(0, pagar);
        if (!j.startsWith("/")) return null;
        while (j.length() > 1 && j.endsWith("/")) j = j.substring(0, j.length() - 1);
        return j;
    }

    /** Segmen jalur tanpa bagian kosong. */
    public static String[] segmenJalur(String jalur) {
        if (jalur == null) return new String[0];
        String j = normalisasiJalur(jalur);
        if (j == null || "/".equals(j)) return new String[0];
        String[] mentah = j.substring(1).split("/");
        List<String> seg = new ArrayList<String>();
        for (int i = 0; i < mentah.length; i++) {
            if (mentah[i].length() > 0) seg.add(mentah[i]);
        }
        return seg.toArray(new String[seg.size()]);
    }

    /**
     * Mencocokkan satu rute dengan metode dan jalur permintaan. Metode
     * tidak peka huruf besar/kecil; segmen "{nama}" cocok dengan segmen
     * apa pun yang tidak kosong. Rute itu sendiri bila cocok, null bila tidak.
     */
    public static Rute cocokkan(Rute r, String metode, String jalur) {
        if (r == null || metode == null || jalur == null) return null;
        if (!r.metode.equalsIgnoreCase(metode.trim())) return null;
        String[] perlu = r.segmen();
        String[] ada = segmenJalur(jalur);
        if (perlu.length != ada.length) return null;
        for (int i = 0; i < perlu.length; i++) {
            if (perlu[i].startsWith("{") && perlu[i].endsWith("}")) {
                if (ada[i].length() == 0) return null;
            } else if (!perlu[i].equals(ada[i])) {
                return null;
            }
        }
        return r;
    }

    /** Rute pertama yang cocok dalam daftar, atau null. */
    public static Rute cari(List<Rute> daftar, String metode, String jalur) {
        if (daftar == null) return null;
        for (int i = 0; i < daftar.size(); i++) {
            Rute r = cocokkan(daftar.get(i), metode, jalur);
            if (r != null) return r;
        }
        return null;
    }

    /** Parameter jalur dari segmen "{nama}" (nilai di-decode persen). Kosong bila tidak cocok. */
    public static Map<String, String> ekstrakParam(Rute r, String jalur) {
        Map<String, String> hasil = new LinkedHashMap<String, String>();
        if (cocokkan(r, r == null ? null : r.metode, jalur) == null) return hasil;
        String[] perlu = r.segmen();
        String[] ada = segmenJalur(jalur);
        for (int i = 0; i < perlu.length; i++) {
            if (perlu[i].startsWith("{") && perlu[i].endsWith("}")) {
                String nama = perlu[i].substring(1, perlu[i].length() - 1);
                hasil.put(nama, urlDecode(ada[i], false));
            }
        }
        return hasil;
    }

    /**
     * Membangun badan respons JSON: setiap {nama} diganti nilai dari
     * param (didahulukan) lalu penyimpanan; penanda yang tidak dikenal
     * diganti teks kosong. Pola yang diawali "{" dipakai apa adanya,
     * pola teks biasa dibungkus menjadi {"pesan": "..."}.
     */
    public static String bangunRespons(Rute r, Map<String, String> param,
                                       Map<String, String> penyimpanan) {
        if (r == null) return "{\"galat\": \"" + TeksUI.t("tidak ditemukan") + "\"}";
        Map<String, String> semua = new LinkedHashMap<String, String>();
        if (penyimpanan != null) semua.putAll(penyimpanan);
        if (param != null) semua.putAll(param);
        String t = r.polaRespons;
        for (Map.Entry<String, String> e : semua.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            t = t.replace("{" + e.getKey() + "}", v);
        }
        t = POLA_PARAM.matcher(t).replaceAll("");
        if (t.trim().startsWith("{")) return t;
        return "{\"pesan\": \"" + escapeJson(t) + "\"}";
    }

    /**
     * Menangani satu permintaan sampai selesai: cari rute (404 bila tidak
     * ada), periksa kunci API (401 bila salah atau belum diatur padahal
     * rute membutuhkannya), gabung parameter jalur + query/isi, simpan
     * parameter permintaan POST/PUT ke penyimpanan, lalu bangun respons.
     * Setiap penanganan dicatat ke log bila log tidak null.
     */
    public static Hasil tangani(List<Rute> daftar, String metode, String jalur,
                                Map<String, String> paramQuery, String kunciDiberikan,
                                String kunciBenar, Map<String, String> penyimpanan,
                                LogServer log) {
        String m = metode == null ? "" : metode.trim().toUpperCase(Locale.US);
        String j = jalur == null ? "" : jalur;
        Rute r = cari(daftar, m, j);
        if (r == null) {
            Hasil h = new Hasil(404,
                    "{\"galat\": \"" + TeksUI.t("tidak ditemukan") + "\"}", null);
            if (log != null) log.catat(m, j, h.kode, h.badan);
            return h;
        }
        if (r.butuhKunci) {
            boolean benar = kunciBenar != null && kunciBenar.length() > 0
                    && kunciBenar.equals(kunciDiberikan);
            if (!benar) {
                String pesan = (kunciBenar == null || kunciBenar.length() == 0)
                        ? TeksUI.t("kunci API belum diatur")
                        : TeksUI.t("kunci API salah");
                Hasil h = new Hasil(401, "{\"galat\": \"" + escapeJson(pesan) + "\"}", r);
                if (log != null) log.catat(m, j, h.kode, h.badan);
                return h;
            }
        }
        Map<String, String> param = new LinkedHashMap<String, String>();
        if (paramQuery != null) param.putAll(paramQuery);
        param.putAll(ekstrakParam(r, j));
        if (("POST".equals(m) || "PUT".equals(m)) && penyimpanan != null) {
            for (Map.Entry<String, String> e : param.entrySet()) {
                if (e.getValue() != null && e.getValue().length() > 0) {
                    penyimpanan.put(e.getKey(), e.getValue());
                }
            }
        }
        String badan = bangunRespons(r, param, penyimpanan);
        Hasil h = new Hasil(200, badan, r);
        if (log != null) log.catat(m, j, h.kode, h.badan);
        return h;
    }

    // ------------------------------------------------------- penyimpanan JSON

    /** Mengubah map datar menjadi JSON {"kunci": "nilai"} (kunci urut abjad). */
    public static String keJson(Map<String, String> data) {
        if (data == null) return "{}";
        Map<String, String> urut = new TreeMap<String, String>(data);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean pertama = true;
        for (Map.Entry<String, String> e : urut.entrySet()) {
            if (!pertama) sb.append(", ");
            pertama = false;
            sb.append('"').append(escapeJson(e.getKey())).append("\": \"")
              .append(escapeJson(e.getValue() == null ? "" : e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    /** Mengurai JSON datar {"kunci": "nilai"}; teks rusak menghasilkan map kosong. */
    public static Map<String, String> dariJson(String json) {
        Map<String, String> hasil = new LinkedHashMap<String, String>();
        if (json == null) return hasil;
        String t = json.trim();
        if (!t.startsWith("{") || !t.endsWith("}")) return hasil;
        Pattern p = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(t);
        while (m.find()) {
            hasil.put(unescapeJson(m.group(1)), unescapeJson(m.group(2)));
        }
        return hasil;
    }

    /** Menyimpan map ke berkas sebagai JSON datar (UTF-8). */
    public static boolean simpan(File berkas, Map<String, String> data) {
        if (berkas == null) return false;
        try {
            if (berkas.getParentFile() != null) berkas.getParentFile().mkdirs();
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(berkas), "UTF-8");
            out.write(keJson(data));
            out.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Memuat map dari berkas JSON datar; berkas hilang/rusak menghasilkan map kosong. */
    public static Map<String, String> muat(File berkas) {
        Map<String, String> hasil = new LinkedHashMap<String, String>();
        if (berkas == null || !berkas.isFile()) return hasil;
        try {
            FileInputStream in = new FileInputStream(berkas);
            InputStreamReader r = new InputStreamReader(in, "UTF-8");
            BufferedReader br = new BufferedReader(r);
            StringBuilder sb = new StringBuilder();
            String b;
            while ((b = br.readLine()) != null) sb.append(b).append('\n');
            br.close();
            return dariJson(sb.toString());
        } catch (Throwable t) {
            return hasil;
        }
    }

    // ------------------------------------------------------------- pembantu

    /** Escape JSON: garis miring terbalik, kutip, dan karakter kendali. */
    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Balikan escapeJson (kutip, garis miring, baris baru, tab, dan kode uXXXX). */
    public static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '"') { sb.append('"'); i++; }
                else if (n == '\\') { sb.append('\\'); i++; }
                else if (n == '/') { sb.append('/'); i++; }
                else if (n == 'n') { sb.append('\n'); i++; }
                else if (n == 'r') { sb.append('\r'); i++; }
                else if (n == 't') { sb.append('\t'); i++; }
                else if (n == 'u' && i + 5 < s.length()) {
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 5;
                    } catch (NumberFormatException e) {
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Decode persen (%20 dan sejenis); bila plusJadiSpasi, '+' menjadi spasi (form). */
    public static String urlDecode(String s, boolean plusJadiSpasi) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+' && plusJadiSpasi) {
                sb.append(' ');
            } else if (c == '%' && i + 2 < s.length()) {
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                } catch (NumberFormatException e) {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Mengurai "kunci=nilai&kunci2=nilai2" (query atau form) menjadi map. */
    public static Map<String, String> uraiForm(String s) {
        Map<String, String> hasil = new LinkedHashMap<String, String>();
        if (s == null || s.trim().length() == 0) return hasil;
        String[] pasang = s.split("&");
        for (int i = 0; i < pasang.length; i++) {
            String p = pasang[i];
            if (p.length() == 0) continue;
            int samaDengan = p.indexOf('=');
            if (samaDengan < 0) {
                hasil.put(urlDecode(p, true), "");
            } else {
                hasil.put(urlDecode(p.substring(0, samaDengan), true),
                        urlDecode(p.substring(samaDengan + 1), true));
            }
        }
        return hasil;
    }

    /** Contoh definisi untuk kolom isi di layar (dipakai bila belum ada yang tersimpan). */
    public static String contohDefinisi() {
        return "# Contoh definisi rute Server Mikro\n"
                + "# Format: METODE /jalur -> respons\n"
                + "# {nama} di jalur atau sesudah jalur adalah parameter.\n"
                + "# Tambahkan [kunci] di akhir baris bila rute butuh kunci API.\n"
                + "GET /halo -> {\"pesan\": \"halo\"}\n"
                + "GET /halo/{nama} -> {\"pesan\": \"halo {nama}\"}\n"
                + "POST /tambah {nama} -> tersimpan\n"
                + "GET /rahasia -> {\"pesan\": \"rahasia\"} [kunci]\n";
    }

    // ------------------------------------------------------- bundel ke APK

    /** Nama kelas peladen yang dibundel ke proyek. */
    public static final String NAMA_KELAS_PELADEN = "PeladenMikroActivity";
    /** Nama aset definisi rute di dalam proyek / APK hasil. */
    public static final String NAMA_ASET_RUTE = "server-mikro-rute.txt";

    /**
     * Kode sumber peladen (PeladenMikroActivity) untuk paket tertentu.
     * Peladen ini mandiri: hanya memakai API Android + java.net/java.io
     * sehingga bisa dikompilasi ECJ bersama sumber proyek apa pun, dan
     * membaca definisi rute dari aset server-mikro-rute.txt (bila aset
     * tidak ada, memakai definisi bawaan yang dibakar di kode).
     * Kunci API yang dibakar di sini hanyalah kunci server lokal untuk
     * pengujian — jangan dipakai untuk data sungguhan.
     */
    public static String hasilkanKodePeladen(String paket, String definisi, int port, String kunci) {
        StringBuilder b = new StringBuilder();
        b.append("package ").append(paket).append(";\n\n");
        b.append("import android.app.Activity;\n");
        b.append("import android.os.Bundle;\n");
        b.append("import android.view.View;\n");
        b.append("import android.view.ViewGroup;\n");
        b.append("import android.widget.Button;\n");
        b.append("import android.widget.LinearLayout;\n");
        b.append("import android.widget.ScrollView;\n");
        b.append("import android.widget.TextView;\n");
        b.append("import java.io.BufferedReader;\n");
        b.append("import java.io.ByteArrayOutputStream;\n");
        b.append("import java.io.File;\n");
        b.append("import java.io.FileInputStream;\n");
        b.append("import java.io.FileOutputStream;\n");
        b.append("import java.io.InputStream;\n");
        b.append("import java.io.InputStreamReader;\n");
        b.append("import java.io.OutputStream;\n");
        b.append("import java.io.OutputStreamWriter;\n");
        b.append("import java.net.Inet4Address;\n");
        b.append("import java.net.InetAddress;\n");
        b.append("import java.net.NetworkInterface;\n");
        b.append("import java.net.ServerSocket;\n");
        b.append("import java.net.Socket;\n");
        b.append("import java.text.SimpleDateFormat;\n");
        b.append("import java.util.ArrayList;\n");
        b.append("import java.util.Date;\n");
        b.append("import java.util.Enumeration;\n");
        b.append("import java.util.LinkedHashMap;\n");
        b.append("import java.util.List;\n");
        b.append("import java.util.Locale;\n");
        b.append("import java.util.Map;\n\n");
        b.append("/** Server mikro yang dibundel Create Aplikasi ke proyek.\n");
        b.append(" * Berjalan di dalam aplikasi hasil build: membuka ServerSocket\n");
        b.append(" * dan menjawab rute dari aset server-mikro-rute.txt. Alamatnya\n");
        b.append(" * tampil di layar ini. Dibangkitkan oleh ServerMikro.bundelKeProyek;\n");
        b.append(" * jangan diubah dengan tangan. */\n");
        b.append("public class PeladenMikroActivity extends Activity {\n");
        b.append("    public static final int PORT = ").append(port).append(";\n");
        b.append("    public static final String KUNCI = \"").append(escapeJava(kunci == null ? "" : kunci)).append("\";\n");
        b.append("    public static final String DEFINISI_BAWAAN = \"").append(escapeJava(definisi == null ? "" : definisi)).append("\";\n");
        b.append("    private TextView tvLog;\n");
        b.append("    private TextView tvAlamat;\n");
        b.append("    private volatile boolean jalan;\n");
        b.append("    private ServerSocket server;\n");
        b.append("    private final Map<String, String> penyimpanan = new LinkedHashMap<String, String>();\n");
        b.append("    private final List<String[]> rute = new ArrayList<String[]>();\n\n");
        b.append("    @Override protected void onCreate(Bundle savedInstanceState) {\n");
        b.append("        super.onCreate(savedInstanceState);\n");
        b.append("        LinearLayout r = new LinearLayout(this);\n");
        b.append("        r.setOrientation(LinearLayout.VERTICAL);\n");
        b.append("        r.setPadding(24, 16, 24, 16);\n");
        b.append("        TextView judul = new TextView(this);\n");
        b.append("        judul.setText(\"Server Mikro\");\n");
        b.append("        judul.setTextSize(20);\n");
        b.append("        r.addView(judul);\n");
        b.append("        tvAlamat = new TextView(this);\n");
        b.append("        tvAlamat.setTextSize(14);\n");
        b.append("        tvAlamat.setTextIsSelectable(true);\n");
        b.append("        tvAlamat.setText(\"Server belum berjalan.\");\n");
        b.append("        r.addView(tvAlamat);\n");
        b.append("        tvLog = new TextView(this);\n");
        b.append("        tvLog.setTextSize(13);\n");
        b.append("        tvLog.setTextIsSelectable(true);\n");
        b.append("        r.addView(tvLog);\n");
        b.append("        Button bBerhenti = new Button(this);\n");
        b.append("        bBerhenti.setText(\"Berhenti\");\n");
        b.append("        bBerhenti.setOnClickListener(new View.OnClickListener() {\n");
        b.append("            @Override public void onClick(View v) { berhenti(); finish(); }\n");
        b.append("        });\n");
        b.append("        r.addView(bBerhenti, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n");
        b.append("        ScrollView sv = new ScrollView(this);\n");
        b.append("        sv.addView(r);\n");
        b.append("        setContentView(sv);\n");
        b.append("        setTitle(\"Server Mikro\");\n");
        b.append("        muatRute();\n");
        b.append("        muatPenyimpanan();\n");
        b.append("        mulai();\n");
        b.append("    }\n\n");
        b.append("    @Override protected void onDestroy() { super.onDestroy(); berhenti(); }\n\n");
        b.append("    private void muatRute() {\n");
        b.append("        String teks = null;\n");
        b.append("        try {\n");
        b.append("            BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(\"").append(NAMA_ASET_RUTE).append("\"), \"UTF-8\"));\n");
        b.append("            StringBuilder sb = new StringBuilder();\n");
        b.append("            String s; while ((s = r.readLine()) != null) sb.append(s).append('\\n');\n");
        b.append("            r.close(); teks = sb.toString();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("        if (teks == null || teks.trim().length() == 0) teks = DEFINISI_BAWAAN;\n");
        b.append("        for (String baris : teks.split(\"\\n\")) {\n");
        b.append("            String b2 = baris.trim();\n");
        b.append("            if (b2.length() == 0 || b2.startsWith(\"#\")) continue;\n");
        b.append("            int panah = b2.indexOf(\"->\");\n");
        b.append("            if (panah < 0) continue;\n");
        b.append("            boolean kunci = b2.contains(\"[kunci]\");\n");
        b.append("            String kiri = b2.substring(0, panah).trim().replace(\"[kunci]\", \"\").trim();\n");
        b.append("            String kanan = b2.substring(panah + 2).trim().replace(\"[kunci]\", \"\").trim();\n");
        b.append("            String[] token = kiri.split(\"\\\\s+\");\n");
        b.append("            if (token.length < 2 || !token[1].startsWith(\"/\")) continue;\n");
        b.append("            rute.add(new String[]{token[0].toUpperCase(Locale.US), token[1], kanan, kunci ? \"1\" : \"0\"});\n");
        b.append("        }\n");
        b.append("    }\n\n");
        b.append("    private void mulai() {\n");
        b.append("        try { server = new ServerSocket(PORT); } catch (Throwable t) {\n");
        b.append("            tvAlamat.setText(\"Gagal memulai server di port \" + PORT + \": \" + t.getMessage());\n");
        b.append("            return;\n");
        b.append("        }\n");
        b.append("        jalan = true;\n");
        b.append("        tvAlamat.setText(\"http://\" + ipLokal() + \":\" + PORT + \"/halo\");\n");
        b.append("        catat(\"Server berjalan, \" + rute.size() + \" rute.\");\n");
        b.append("        try { getWindow().getDecorView().announceForAccessibility(\"Server mikro berjalan di port \" + PORT); } catch (Throwable ignored) {}\n");
        b.append("        new Thread(new Runnable() {\n");
        b.append("            @Override public void run() {\n");
        b.append("                while (jalan) {\n");
        b.append("                    try {\n");
        b.append("                        final Socket s = server.accept();\n");
        b.append("                        new Thread(new Runnable() {\n");
        b.append("                            @Override public void run() { tangani(s); }\n");
        b.append("                        }).start();\n");
        b.append("                    } catch (Throwable ignored) {}\n");
        b.append("                }\n");
        b.append("            }\n");
        b.append("        }, \"PeladenMikro\").start();\n");
        b.append("    }\n\n");
        b.append("    private void berhenti() {\n");
        b.append("        jalan = false;\n");
        b.append("        try { if (server != null) server.close(); } catch (Throwable ignored) {}\n");
        b.append("        server = null;\n");
        b.append("    }\n\n");
        b.append("    private String ipLokal() {\n");
        b.append("        try {\n");
        b.append("            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();\n");
        b.append("            String cadangan = null;\n");
        b.append("            while (nis.hasMoreElements()) {\n");
        b.append("                NetworkInterface ni = nis.nextElement();\n");
        b.append("                if (!ni.isUp() || ni.isLoopback()) continue;\n");
        b.append("                Enumeration<InetAddress> as = ni.getInetAddresses();\n");
        b.append("                while (as.hasMoreElements()) {\n");
        b.append("                    InetAddress a = as.nextElement();\n");
        b.append("                    if (a.isLoopbackAddress() || !(a instanceof Inet4Address)) continue;\n");
        b.append("                    if (a.isSiteLocalAddress()) return a.getHostAddress();\n");
        b.append("                    if (cadangan == null) cadangan = a.getHostAddress();\n");
        b.append("                }\n");
        b.append("            }\n");
        b.append("            if (cadangan != null) return cadangan;\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("        return \"localhost\";\n");
        b.append("    }\n\n");
        b.append("    private void tangani(Socket soket) {\n");
        b.append("        try {\n");
        b.append("            InputStream masuk = soket.getInputStream();\n");
        b.append("            OutputStream keluar = soket.getOutputStream();\n");
        b.append("            String baris = bacaBaris(masuk);\n");
        b.append("            if (baris == null || baris.trim().length() == 0) return;\n");
        b.append("            String[] bagian = baris.trim().split(\"\\\\s+\");\n");
        b.append("            if (bagian.length < 2) return;\n");
        b.append("            String metode = bagian[0].toUpperCase(Locale.US);\n");
        b.append("            String jalurPenuh = bagian[1];\n");
        b.append("            int panjang = 0;\n");
        b.append("            String kunciHeader = null;\n");
        b.append("            String tipeIsi = \"\";\n");
        b.append("            String h;\n");
        b.append("            while ((h = bacaBaris(masuk)) != null && h.trim().length() > 0) {\n");
        b.append("                String rendah = h.toLowerCase(Locale.US);\n");
        b.append("                if (rendah.startsWith(\"content-length:\")) { try { panjang = Integer.parseInt(h.substring(15).trim()); } catch (NumberFormatException ignored) {} }\n");
        b.append("                else if (rendah.startsWith(\"content-type:\")) tipeIsi = h.substring(13).trim();\n");
        b.append("                else if (rendah.startsWith(\"x-kunci-api:\")) kunciHeader = h.substring(12).trim();\n");
        b.append("            }\n");
        b.append("            String jalur = jalurPenuh;\n");
        b.append("            Map<String, String> param = new LinkedHashMap<String, String>();\n");
        b.append("            int tanya = jalurPenuh.indexOf('?');\n");
        b.append("            if (tanya >= 0) { jalur = jalurPenuh.substring(0, tanya); uraiFormKe(jalurPenuh.substring(tanya + 1), param); }\n");
        b.append("            if (panjang > 0 && panjang <= 1048576) {\n");
        b.append("                byte[] isiBita = new byte[panjang];\n");
        b.append("                int dibaca = 0;\n");
        b.append("                while (dibaca < panjang) { int n = masuk.read(isiBita, dibaca, panjang - dibaca); if (n < 0) break; dibaca += n; }\n");
        b.append("                String isi = new String(isiBita, 0, dibaca, \"UTF-8\");\n");
        b.append("                if (tipeIsi.contains(\"application/json\")) uraiJsonDatarKe(isi, param);\n");
        b.append("                else uraiFormKe(isi, param);\n");
        b.append("            }\n");
        b.append("            String kunciDiberikan = kunciHeader != null ? kunciHeader : param.get(\"kunci\");\n");
        b.append("            int kode; String badan;\n");
        b.append("            String[] cocok = null;\n");
        b.append("            Map<String, String> paramJalur = new LinkedHashMap<String, String>();\n");
        b.append("            for (int i = 0; i < rute.size(); i++) {\n");
        b.append("                String[] rt = rute.get(i);\n");
        b.append("                if (!rt[0].equals(metode)) continue;\n");
        b.append("                if (cocokJalur(rt[1], jalur, paramJalur)) { cocok = rt; break; }\n");
        b.append("            }\n");
        b.append("            if (cocok == null) { kode = 404; badan = \"{\\\"galat\\\": \\\"tidak ditemukan\\\"}\"; }\n");
        b.append("            else if (\"1\".equals(cocok[3]) && !KUNCI.equals(kunciDiberikan)) {\n");
        b.append("                kode = 401;\n");
        b.append("                badan = \"{\\\"galat\\\": \\\"\" + (KUNCI.length() == 0 ? \"kunci API belum diatur\" : \"kunci API salah\") + \"\\\"}\";\n");
        b.append("            } else {\n");
        b.append("                param.putAll(paramJalur);\n");
        b.append("                if ((\"POST\".equals(metode) || \"PUT\".equals(metode))) {\n");
        b.append("                    synchronized (penyimpanan) { for (Map.Entry<String, String> e : param.entrySet()) if (e.getValue() != null && e.getValue().length() > 0) penyimpanan.put(e.getKey(), e.getValue()); }\n");
        b.append("                    simpanPenyimpanan();\n");
        b.append("                }\n");
        b.append("                String t = cocok[2];\n");
        b.append("                Map<String, String> semua = new LinkedHashMap<String, String>(penyimpanan);\n");
        b.append("                semua.putAll(param);\n");
        b.append("                for (Map.Entry<String, String> e : semua.entrySet()) t = t.replace(\"{\" + e.getKey() + \"}\", e.getValue() == null ? \"\" : e.getValue());\n");
        b.append("                t = t.replaceAll(\"\\\\{[A-Za-z0-9_]+\\\\}\", \"\");\n");
        b.append("                badan = t.trim().startsWith(\"{\") ? t : \"{\\\"pesan\\\": \\\"\" + escapeJson(t) + \"\\\"}\";\n");
        b.append("                kode = 200;\n");
        b.append("            }\n");
        b.append("            catat(metode + \" \" + jalur + \" -> \" + kode);\n");
        b.append("            byte[] badanBita = badan.getBytes(\"UTF-8\");\n");
        b.append("            String status = kode == 200 ? \"200 OK\" : kode == 401 ? \"401 Unauthorized\" : \"404 Not Found\";\n");
        b.append("            String kepala = \"HTTP/1.1 \" + status + \"\\r\\nContent-Type: application/json; charset=utf-8\\r\\nContent-Length: \" + badanBita.length + \"\\r\\nConnection: close\\r\\n\\r\\n\";\n");
        b.append("            keluar.write(kepala.getBytes(\"UTF-8\"));\n");
        b.append("            keluar.write(badanBita);\n");
        b.append("            keluar.flush();\n");
        b.append("        } catch (Throwable ignored) {\n");
        b.append("        } finally { try { soket.close(); } catch (Throwable ignored) {} }\n");
        b.append("    }\n\n");
        b.append("    private static boolean cocokJalur(String pola, String jalur, Map<String, String> keluar) {\n");
        b.append("        String[] perlu = pola.split(\"/\");\n");
        b.append("        String[] ada = jalur.split(\"/\");\n");
        b.append("        java.util.List<String> p = new ArrayList<String>();\n");
        b.append("        java.util.List<String> a = new ArrayList<String>();\n");
        b.append("        for (int i = 0; i < perlu.length; i++) if (perlu[i].length() > 0) p.add(perlu[i]);\n");
        b.append("        for (int i = 0; i < ada.length; i++) if (ada[i].length() > 0) a.add(ada[i]);\n");
        b.append("        if (p.size() != a.size()) return false;\n");
        b.append("        for (int i = 0; i < p.size(); i++) {\n");
        b.append("            String seg = p.get(i);\n");
        b.append("            if (seg.startsWith(\"{\") && seg.endsWith(\"}\")) keluar.put(seg.substring(1, seg.length() - 1), a.get(i));\n");
        b.append("            else if (!seg.equals(a.get(i))) return false;\n");
        b.append("        }\n");
        b.append("        return true;\n");
        b.append("    }\n\n");
        b.append("    private static void uraiFormKe(String s, Map<String, String> keluar) {\n");
        b.append("        if (s == null) return;\n");
        b.append("        for (String pasang : s.split(\"&\")) {\n");
        b.append("            if (pasang.length() == 0) continue;\n");
        b.append("            int samaDengan = pasang.indexOf('=');\n");
        b.append("            if (samaDengan < 0) keluar.put(decode(pasang), \"\");\n");
        b.append("            else keluar.put(decode(pasang.substring(0, samaDengan)), decode(pasang.substring(samaDengan + 1)));\n");
        b.append("        }\n");
        b.append("    }\n\n");
        b.append("    private static void uraiJsonDatarKe(String json, Map<String, String> keluar) {\n");
        b.append("        if (json == null) return;\n");
        b.append("        java.util.regex.Matcher m = java.util.regex.Pattern.compile(\"\\\"((?:\\\\\\\\.|[^\\\"\\\\\\\\])*)\\\"\\\\s*:\\\\s*\\\"((?:\\\\\\\\.|[^\\\"\\\\\\\\])*)\\\"\").matcher(json);\n");
        b.append("        while (m.find()) keluar.put(m.group(1), m.group(2));\n");
        b.append("    }\n\n");
        b.append("    private static String decode(String s) {\n");
        b.append("        StringBuilder sb = new StringBuilder();\n");
        b.append("        for (int i = 0; i < s.length(); i++) {\n");
        b.append("            char c = s.charAt(i);\n");
        b.append("            if (c == '+') sb.append(' ');\n");
        b.append("            else if (c == '%' && i + 2 < s.length()) { try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16)); i += 2; } catch (NumberFormatException e) { sb.append(c); } }\n");
        b.append("            else sb.append(c);\n");
        b.append("        }\n");
        b.append("        return sb.toString();\n");
        b.append("    }\n\n");
        b.append("    private static String escapeJson(String s) {\n");
        b.append("        if (s == null) return \"\";\n");
        b.append("        StringBuilder sb = new StringBuilder();\n");
        b.append("        for (int i = 0; i < s.length(); i++) {\n");
        b.append("            char c = s.charAt(i);\n");
        b.append("            if (c == '\"') sb.append(\"\\\\\\\"\");\n");
        b.append("            else if (c == '\\\\') sb.append(\"\\\\\\\\\");\n");
        b.append("            else if (c == '\\n') sb.append(\"\\\\n\");\n");
        b.append("            else if (c < 0x20) sb.append(String.format(\"\\\\u%04x\", (int) c));\n");
        b.append("            else sb.append(c);\n");
        b.append("        }\n");
        b.append("        return sb.toString();\n");
        b.append("    }\n\n");
        b.append("    private static String bacaBaris(InputStream masuk) throws java.io.IOException {\n");
        b.append("        ByteArrayOutputStream buf = new ByteArrayOutputStream();\n");
        b.append("        int c;\n");
        b.append("        while ((c = masuk.read()) >= 0) { if (c == '\\n') break; buf.write(c); }\n");
        b.append("        if (c < 0 && buf.size() == 0) return null;\n");
        b.append("        String s = new String(buf.toByteArray(), \"UTF-8\");\n");
        b.append("        if (s.endsWith(\"\\r\")) s = s.substring(0, s.length() - 1);\n");
        b.append("        return s;\n");
        b.append("    }\n\n");
        b.append("    private void catat(final String s) {\n");
        b.append("        final String baris = \"[\" + new SimpleDateFormat(\"HH:mm:ss\", Locale.US).format(new Date()) + \"] \" + s + \"\\n\";\n");
        b.append("        runOnUiThread(new Runnable() {\n");
        b.append("            @Override public void run() { tvLog.append(baris); }\n");
        b.append("        });\n");
        b.append("    }\n\n");
        b.append("    private void muatPenyimpanan() {\n");
        b.append("        try {\n");
        b.append("            File f = new File(getFilesDir(), \"server-data.json\");\n");
        b.append("            if (!f.isFile()) return;\n");
        b.append("            byte[] data = new byte[(int) f.length()];\n");
        b.append("            FileInputStream in = new FileInputStream(f);\n");
        b.append("            int n = in.read(data); in.close();\n");
        b.append("            if (n > 0) uraiJsonDatarKe(new String(data, 0, n, \"UTF-8\"), penyimpanan);\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("    }\n\n");
        b.append("    private void simpanPenyimpanan() {\n");
        b.append("        try {\n");
        b.append("            StringBuilder sb = new StringBuilder(\"{\");\n");
        b.append("            boolean pertama = true;\n");
        b.append("            synchronized (penyimpanan) {\n");
        b.append("                for (Map.Entry<String, String> e : penyimpanan.entrySet()) {\n");
        b.append("                    if (!pertama) sb.append(\", \");\n");
        b.append("                    pertama = false;\n");
        b.append("                    sb.append('\"').append(escapeJson(e.getKey())).append(\"\\\": \\\"\").append(escapeJson(e.getValue())).append('\"');\n");
        b.append("                }\n");
        b.append("            }\n");
        b.append("            sb.append('}');\n");
        b.append("            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(new File(getFilesDir(), \"server-data.json\")), \"UTF-8\");\n");
        b.append("            w.write(sb.toString()); w.close();\n");
        b.append("        } catch (Throwable ignored) {}\n");
        b.append("    }\n");
        b.append("}\n");
        return b.toString();
    }

    /** Escape teks menjadi literal string Java (kutip, backslash, baris baru). */
    static String escapeJava(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Membundel server mikro ke proyek: menulis sumber peladen +
     * aset definisi rute, mendaftarkan activity peladen di manifest
     * proyek (ikon peluncur "Server Mikro"), dan menambahkan izin
     * INTERNET bila belum ada. Build berikutnya mengompilasi peladen
     * itu ke APK hasil, dan server berjalan dari dalam aplikasi hasil
     * build saat activity itu dibuka. Mengembalikan pesan hasil
     * (diawali "OK" bila berhasil).
     */
    public static String bundelKeProyek(File dirProyek, String definisi, int port, String kunci) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "GAGAL: folder proyek tidak ditemukan.";
        }
        if (port < 1024 || port > 65535) {
            return "GAGAL: port harus 1024 sampai 65535.";
        }
        List<Rute> rute = uraiDefinisi(definisi);
        if (rute.isEmpty()) {
            return "GAGAL: definisi rute kosong atau rusak.";
        }
        File manifest = AgenUji.cariManifest(dirProyek);
        if (manifest == null) {
            return "GAGAL: AndroidManifest.xml proyek tidak ditemukan.";
        }
        String xml = AgenUji.bacaTeks(manifest);
        if (xml == null) return "GAGAL: manifest proyek tidak bisa dibaca.";
        String paket = AgenUji.paketDariManifest(xml);
        if (paket.length() == 0) return "GAGAL: package di manifest tidak terbaca.";
        File dirJava = new File(dirProyek, "app/src/main/java/" + paket.replace('.', '/'));
        dirJava.mkdirs();
        if (!AgenUji.tulisTeks(new File(dirJava, NAMA_KELAS_PELADEN + ".java"),
                hasilkanKodePeladen(paket, definisi, port, kunci))) {
            return "GAGAL: tidak bisa menulis sumber peladen.";
        }
        File dirAset = new File(dirProyek, "app/src/main/assets");
        dirAset.mkdirs();
        if (!AgenUji.tulisTeks(new File(dirAset, NAMA_ASET_RUTE), definisi)) {
            return "GAGAL: tidak bisa menulis aset rute.";
        }
        String blok = "<activity android:name=\"." + NAMA_KELAS_PELADEN + "\"\n"
                + "            android:exported=\"true\"\n"
                + "            android:label=\"Server Mikro\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>";
        String baru = AgenUji.sisipAtauGantiAktivitas(xml, NAMA_KELAS_PELADEN, blok);
        if (baru == null) return "GAGAL: tidak bisa mendaftarkan peladen di manifest proyek.";
        baru = tambahIzin(baru, "INTERNET");
        if (!AgenUji.tulisTeks(manifest, baru)) {
            return "GAGAL: tidak bisa menulis manifest proyek.";
        }
        return "OK: server dibundel (" + rute.size() + " rute, port " + port
                + "). Build APK proyek ini, pasang, lalu buka ikon \"Server Mikro\" — "
                + "server berjalan dari dalam aplikasi hasil build.";
    }

    /** Melepas bundel server dari proyek (sumber + aset + blok activity). Izin INTERNET dibiarkan. */
    public static String lepasBundel(File dirProyek) {
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "GAGAL: folder proyek tidak ditemukan.";
        }
        File manifest = AgenUji.cariManifest(dirProyek);
        if (manifest == null) return "GAGAL: AndroidManifest.xml proyek tidak ditemukan.";
        String xml = AgenUji.bacaTeks(manifest);
        if (xml == null) return "GAGAL: manifest proyek tidak bisa dibaca.";
        String paket = AgenUji.paketDariManifest(xml);
        File sumber = null;
        if (paket.length() > 0) {
            sumber = new File(dirProyek, "app/src/main/java/" + paket.replace('.', '/')
                    + "/" + NAMA_KELAS_PELADEN + ".java");
        }
        boolean hapusSumber = sumber != null && sumber.isFile() && sumber.delete();
        File aset = new File(dirProyek, "app/src/main/assets/" + NAMA_ASET_RUTE);
        boolean hapusAset = aset.isFile() && aset.delete();
        String baru = AgenUji.hapusBlokAktivitas(xml, NAMA_KELAS_PELADEN);
        boolean hapusManifest = baru != null && !baru.equals(xml) && AgenUji.tulisTeks(manifest, baru);
        if (!hapusSumber && !hapusAset && !hapusManifest) {
            return "GAGAL: bundel server tidak ditemukan di proyek ini.";
        }
        return "OK: bundel server dilepas dari proyek (izin INTERNET dibiarkan).";
    }

    /** Tambahkan <uses-permission android:name="android.permission.X" /> bila belum ada. */
    static String tambahIzin(String xml, String izin) {
        if (xml == null) return null;
        if (xml.contains("android.permission." + izin + "\"")) return xml;
        String baris = "    <uses-permission android:name=\"android.permission." + izin + "\" />\n";
        int app = xml.indexOf("<application");
        if (app < 0) return xml + "\n" + baris;
        return xml.substring(0, app) + baris + xml.substring(app);
    }
}
