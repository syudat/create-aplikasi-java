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
}
