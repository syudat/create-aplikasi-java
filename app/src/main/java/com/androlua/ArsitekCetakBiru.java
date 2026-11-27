package com.androlua;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Arsitek Cetak Biru Proyek dan Pengurai Tugas Bertahap Berbasis AI
 * (Fitur 11, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - buat: gagasan teks -> cetak biru (modul, berkas, tonggak) dari
 *    pola kata kunci + kerangka umum; pengembang meninjau lalu
 *    membangun satu tonggak demi satu tonggak.
 *  - buatDenganAi: cetak biru disusun oleh penyedia AI AKTIF di
 *    Pengaturan AI (DeepSeek, Grok, Gemini, OpenRouter, Mistral)
 *    lewat PemanggilAiHttp di bawah; jawaban AI harus mengikuti
 *    format eksporTeks lalu diurai kembali dengan uraiTeks.
 *  - Tonggak bisa ditandai selesai; kemajuan dihitung dari tonggak.
 *
 * Pemanggil AI bersama (dipakai juga fitur 12, NaratorLogika):
 * antarmuka PemanggilAi + PemanggilAiHttp + tanyaDenganCadangan
 * memakai katalog PenyediaAi dan penyimpanan KunciApi yang sudah
 * ada — TIDAK ada sistem kunci baru. Nilai kunci API tidak pernah
 * dicatat di log, tidak pernah masuk pesan galat, dan tidak pernah
 * ditampilkan di layar. Bila tidak ada kunci tersimpan atau AI
 * tidak dapat dihubungi, pemanggil mengembalikan galat dan layar
 * jatuh kembali ke pola luring dengan menyatakan batas itu.
 *
 * BATAS JUJUR: cetak biru AI hanya sebagus jawaban model; jawaban
 * yang tidak mengikuti format akan ditolak dan diganti pola luring.
 * Pemeriksaan jawaban AI adalah pemeriksaan format (modul dan
 * tonggak ada), bukan pemeriksaan kebenaran isi — pengembang tetap
 * harus meninjau cetak biru sebelum membangun.
 */
public class ArsitekCetakBiru {

    /** Satu tonggak kerja. */
    public static class Tonggak {
        public String nama = "";
        public List<String> berkas = new ArrayList<String>();
        public boolean selesai = false;
        public String toString() {
            return (selesai ? "[selesai] " : "[belum] ") + nama;
        }
    }

    /** Satu cetak biru proyek. */
    public static class CetakBiru {
        public String gagasan = "";
        public List<String> modul = new ArrayList<String>();
        public List<String> berkas = new ArrayList<String>();
        public List<Tonggak> tonggak = new ArrayList<Tonggak>();
        /** Asal cetak biru, mis. "AI Gemini (gemini-3.5-flash-lite)" atau "Luring: pola kata kunci di perangkat". */
        public String sumber = "";
    }

    private static void tambahTonggak(CetakBiru b, String nama, String[] berkas) {
        Tonggak t = new Tonggak();
        t.nama = nama;
        for (int i = 0; i < berkas.length; i++) {
            t.berkas.add(berkas[i]);
            if (!b.berkas.contains(berkas[i])) b.berkas.add(berkas[i]);
        }
        b.tonggak.add(t);
    }

    /** Susun cetak biru dari gagasan (pola kata kunci + kerangka umum). */
    public static CetakBiru buat(String gagasan) {
        CetakBiru b = new CetakBiru();
        b.gagasan = gagasan == null ? "" : gagasan.trim();
        b.sumber = "Luring: pola kata kunci di perangkat";
        String kecil = b.gagasan.toLowerCase();
        boolean kasir = kecil.contains("kasir") || kecil.contains("jualan")
                || kecil.contains("toko") || kecil.contains("belanja");
        boolean chat = kecil.contains("chat") || kecil.contains("pesan")
                || kecil.contains("obrol");
        boolean simpanPinjam = kecil.contains("koperasi") || kecil.contains("pinjam")
                || kecil.contains("cicil") || kecil.contains("tabung");
        boolean jadwal = kecil.contains("jadwal") || kecil.contains("transport")
                || kecil.contains("rute") || kecil.contains("tiket");
        boolean kamus = kecil.contains("kamus") || kecil.contains("belajar")
                || kecil.contains("kuis") || kecil.contains("soal");

        b.modul.add("Model data");
        b.modul.add("Penyimpanan lokal");
        b.modul.add("Layar utama");
        b.modul.add("Logika bisnis");
        if (kasir) b.modul.add("Keranjang dan pembayaran");
        if (chat) b.modul.add("Pesan dan notifikasi");
        if (simpanPinjam) b.modul.add("Perhitungan bunga");
        if (jadwal) b.modul.add("Jadwal dan rute");
        if (kamus) b.modul.add("Bank soal");

        tambahTonggak(b, "Model data dan penyimpanan",
                new String[]{"model/Model.java", "data/Penyimpanan.java"});
        if (simpanPinjam || kasir) {
            tambahTonggak(b, "Perhitungan",
                    new String[]{"logika/Hitungan.java"});
        }
        if (chat) {
            tambahTonggak(b, "Pesan",
                    new String[]{"logika/Pesan.java", "layar/LayarChat.java"});
        }
        if (jadwal) {
            tambahTonggak(b, "Jadwal dan rute",
                    new String[]{"model/Jadwal.java", "layar/LayarJadwal.java"});
        }
        if (kamus) {
            tambahTonggak(b, "Bank soal dan kuis",
                    new String[]{"model/Soal.java", "logika/Kuis.java"});
        }
        tambahTonggak(b, "Layar utama",
                new String[]{"layar/LayarUtama.java", "res/layout/utama.xml"});
        tambahTonggak(b, "Logika bisnis",
                new String[]{"logika/Logika.java"});
        tambahTonggak(b, "Pengujian dan rilis",
                new String[]{"uji/UjiLogika.java"});
        return b;
    }

    /** Tandai tonggak selesai (false bila nomor tidak sah). */
    public static boolean tandaiSelesai(CetakBiru b, int nomorTonggak) {
        if (b == null || nomorTonggak < 1 || nomorTonggak > b.tonggak.size()) return false;
        b.tonggak.get(nomorTonggak - 1).selesai = true;
        return true;
    }

    /** Kemajuan: persen tonggak selesai (0-100). */
    public static int kemajuan(CetakBiru b) {
        if (b == null || b.tonggak.size() == 0) return 0;
        int selesai = 0;
        for (int i = 0; i < b.tonggak.size(); i++) {
            if (b.tonggak.get(i).selesai) selesai++;
        }
        return selesai * 100 / b.tonggak.size();
    }

    /** Tonggak berikutnya yang belum selesai (null bila semua selesai). */
    public static Tonggak tonggakBerikutnya(CetakBiru b) {
        if (b == null) return null;
        for (int i = 0; i < b.tonggak.size(); i++) {
            if (!b.tonggak.get(i).selesai) return b.tonggak.get(i);
        }
        return null;
    }

    /** Ekspor cetak biru sebagai teks terstruktur. */
    public static String eksporTeks(CetakBiru b) {
        if (b == null) return "";
        StringBuilder s = new StringBuilder();
        s.append("Cetak biru: ").append(b.gagasan).append("\n");
        s.append("Modul:\n");
        for (int i = 0; i < b.modul.size(); i++) {
            s.append("- ").append(b.modul.get(i)).append("\n");
        }
        s.append("Tonggak:\n");
        for (int i = 0; i < b.tonggak.size(); i++) {
            Tonggak t = b.tonggak.get(i);
            s.append((i + 1)).append(". ").append(t.toString()).append("\n");
            for (int j = 0; j < t.berkas.size(); j++) {
                s.append("   - ").append(t.berkas.get(j)).append("\n");
            }
        }
        s.append("Kemajuan: ").append(kemajuan(b)).append("%\n");
        return s.toString();
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(CetakBiru b) {
        if (b == null) return "Tonggak dibuat: 0. Berkas direncanakan: 0.";
        return "Tonggak dibuat: " + b.tonggak.size() + ". Berkas direncanakan: "
                + b.berkas.size() + ". Kemajuan: " + kemajuan(b) + "%.";
    }

    /**
     * Urai kembali teks hasil eksporTeks (cetak-biru.txt tersimpan)
     * menjadi cetak biru, termasuk status selesai tiap tonggak dan
     * daftar berkasnya. Baris yang tidak dikenali dilewati.
     */
    public static CetakBiru uraiTeks(String teks) {
        CetakBiru b = new CetakBiru();
        if (teks == null) return b;
        String[] baris = teks.split("\n", -1);
        Tonggak kini = null;
        boolean diModul = false;
        boolean diTonggak = false;
        for (int i = 0; i < baris.length; i++) {
            String s = baris[i];
            if (s.startsWith("Cetak biru: ")) {
                b.gagasan = s.substring("Cetak biru: ".length());
                continue;
            }
            if (s.equals("Modul:")) {
                diModul = true;
                diTonggak = false;
                continue;
            }
            if (s.equals("Tonggak:")) {
                diModul = false;
                diTonggak = true;
                continue;
            }
            if (s.startsWith("Kemajuan: ")) {
                diModul = false;
                diTonggak = false;
                continue;
            }
            if (diModul && s.startsWith("- ")) {
                b.modul.add(s.substring(2));
                continue;
            }
            if (diTonggak) {
                if (s.startsWith("   - ")) {
                    String berkas = s.substring(5);
                    if (kini != null) kini.berkas.add(berkas);
                    if (!b.berkas.contains(berkas)) b.berkas.add(berkas);
                    continue;
                }
                int titik = s.indexOf(". ");
                if (titik > 0) {
                    String isi = s.substring(titik + 2);
                    Tonggak t = new Tonggak();
                    if (isi.startsWith("[selesai] ")) {
                        t.selesai = true;
                        t.nama = isi.substring("[selesai] ".length());
                    } else if (isi.startsWith("[belum] ")) {
                        t.selesai = false;
                        t.nama = isi.substring("[belum] ".length());
                    } else {
                        t.nama = isi;
                    }
                    b.tonggak.add(t);
                    kini = t;
                }
            }
        }
        return b;
    }

    // ------------------------------------------------------- pemanggil AI
    //
    // Pemanggil AI bersama untuk fitur 11 (cetak biru) dan fitur 12
    // (narator). Sengaja diletakkan di sini sebagai kelas bersarang,
    // bukan berkas baru, karena batch ini tidak membuat berkas baru
    // di pohon kerja; koordinator boleh memindahkannya ke
    // PemanggilAi.java bila diperlukan. Semua pemakaian memakai
    // katalog PenyediaAi + penyimpanan KunciApi yang sudah ada.

    /** Satu pertanyaan teks ke model AI -> satu jawaban teks. */
    public interface PemanggilAi {
        String tanya(String perintah) throws Exception;
    }

    /** Pabrik pemanggil (dipakai tanyaDenganCadangan; mudah dipalsukan di uji). */
    public interface PabrikPemanggil {
        PemanggilAi buat(String kodeProvider, String model, String kunci);
    }

    /** Hasil tanya dengan cadangan (kunci/model). Tidak pernah memuat nilai kunci. */
    public static class JawabanAi {
        public String teks = "";
        public String model = "";
        public String penyedia = "";
        public boolean dariAi = false;
        /** Alasan gagal bila dariAi false (tanpa nilai kunci). */
        public String galat = "";
    }

    /**
     * Pengirim HTTP (bisa diganti pengirim palsu saat pengujian —
     * pola yang sama dengan SinkronCloud.PengirimHttp). kepala
     * adalah pasangan {nama, nilai}. Tubuh boleh null (tanpa isi).
     */
    public interface PengirimHttp {
        ResponHttp kirim(String metode, String url, String[][] kepala, byte[] tubuh)
                throws Exception;
    }

    /** Satu tanggapan HTTP: kode status + tubuh (dibatasi saat dibaca). */
    public static class ResponHttp {
        public final int kode;
        public final String tubuh;

        public ResponHttp(int kode, String tubuh) {
            this.kode = kode;
            this.tubuh = tubuh == null ? "" : tubuh;
        }
    }

    /** Pengirim HTTP bawaan (HttpURLConnection). */
    public static class PengirimHttpBawaan implements PengirimHttp {
        public int batasSambungMs = 15000;
        public int batasBacaMs = 60000;

        @Override
        public ResponHttp kirim(String metode, String url, String[][] kepala, byte[] tubuh)
                throws Exception {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod(metode);
                c.setConnectTimeout(batasSambungMs);
                c.setReadTimeout(batasBacaMs);
                if (kepala != null) {
                    for (int i = 0; i < kepala.length; i++) {
                        if (kepala[i] != null && kepala[i].length >= 2) {
                            c.setRequestProperty(kepala[i][0], kepala[i][1]);
                        }
                    }
                }
                if (tubuh != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Length", String.valueOf(tubuh.length));
                    OutputStream out = c.getOutputStream();
                    try {
                        out.write(tubuh);
                    } finally {
                        out.close();
                    }
                }
                int kode = c.getResponseCode();
                InputStream masuk = (kode >= 200 && kode < 300)
                        ? c.getInputStream() : c.getErrorStream();
                String isi = "";
                if (masuk != null) {
                    isi = bacaAliran(masuk, PemanggilAiHttp.BATAS_JAWABAN_HURUF);
                    masuk.close();
                }
                return new ResponHttp(kode, isi);
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    /**
     * Pemanggil AI lewat HTTP. Gemini memakai :generateContent
     * dengan kunci di kueri (cara resmi Gemini); DeepSeek, Grok,
     * OpenRouter, dan Mistral memakai /chat/completions yang
     * kompatibel dengan OpenAI (Bearer).
     * Tidak ada pustaka JSON: permintaan dibuat dengan escapeJson
     * dan jawaban dibaca dengan ambilStringJson (cukup untuk
     * mengambil teks jawaban dari kedua bentuk tanggapan).
     */
    public static class PemanggilAiHttp implements PemanggilAi {
        public final String kodeProvider;
        public final String model;
        public final String kunci;
        public final String baseUrl;
        /** Pengirim HTTP (bawaan HttpURLConnection; bisa diganti pengirim palsu di uji). */
        public PengirimHttp pengirim = new PengirimHttpBawaan();
        /** Batas baca tubuh tanggapan supaya ringan di ponsel (200 ribu huruf). */
        public static final int BATAS_JAWABAN_HURUF = 200000;

        public PemanggilAiHttp(String kodeProvider, String model, String kunci) {
            this(kodeProvider, model, kunci, null);
        }

        /** baseUrl diisi untuk menunjuk ujung lain (bukan bawaan provider). */
        public PemanggilAiHttp(String kodeProvider, String model, String kunci, String baseUrl) {
            this.kodeProvider = kodeProvider == null ? "" : kodeProvider;
            this.model = model == null ? "" : model;
            this.kunci = kunci == null ? "" : kunci;
            if (baseUrl != null && baseUrl.length() > 0) {
                this.baseUrl = baseUrl;
            } else {
                PenyediaAi.Provider p = PenyediaAi.dariKode(this.kodeProvider);
                this.baseUrl = p == null ? "" : p.baseUrl;
            }
        }

        @Override
        public String tanya(String perintah) throws Exception {
            if (baseUrl.length() == 0) throw new Exception("Provider AI tidak dikenal");
            if (kunci.length() == 0) throw new Exception("Kunci API kosong");
            if (perintah == null) perintah = "";
            boolean gemini = "gemini".equals(kodeProvider);
            String url;
            String tubuh;
            String[][] kepala;
            if (gemini) {
                // Kunci di kueri adalah cara resmi API Gemini; kunci TIDAK
                // pernah dimasukkan ke pesan galat atau log.
                url = baseUrl + "/models/" + model + ":generateContent?key="
                        + URLEncoder.encode(kunci, "UTF-8");
                tubuh = "{\"contents\":[{\"parts\":[{\"text\":"
                        + escapeJson(perintah) + "}]}]}";
                kepala = new String[][]{
                        {"Content-Type", "application/json; charset=utf-8"},
                        {"Accept", "application/json"}};
            } else {
                url = baseUrl + "/chat/completions";
                tubuh = "{\"model\":" + escapeJson(model)
                        + ",\"messages\":[{\"role\":\"user\",\"content\":"
                        + escapeJson(perintah) + "}]}";
                kepala = new String[][]{
                        {"Content-Type", "application/json; charset=utf-8"},
                        {"Accept", "application/json"},
                        {"Authorization", "Bearer " + kunci}};
            }
            try {
                ResponHttp r = pengirim.kirim("POST", url, kepala, tubuh.getBytes("UTF-8"));
                if (r.kode < 200 || r.kode >= 300) {
                    throw new Exception("AI HTTP " + r.kode + ": " + pesanGalatJson(r.tubuh));
                }
                String teks = ambilStringJson(r.tubuh, gemini ? "text" : "content");
                if (teks == null || teks.trim().length() == 0) {
                    throw new Exception("Jawaban AI kosong");
                }
                return teks;
            } catch (UnknownHostException e) {
                throw new Exception("Tidak ada internet atau nama host AI tidak dapat dihubungi");
            } catch (SocketTimeoutException e) {
                throw new Exception("Waktu habis saat menghubungi AI");
            }
        }
    }

    /** Baca aliran HTTP sampai batas huruf (UTF-8). */
    static String bacaAliran(InputStream masuk, int batasHuruf) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = masuk.read(buf)) > 0) out.write(buf, 0, n);
        String s = new String(out.toByteArray(), "UTF-8");
        if (s.length() > batasHuruf) s = s.substring(0, batasHuruf);
        return s;
    }

    /** Pesan "message" dari tubuh galat JSON, dipangkas 200 huruf. */
    public static String pesanGalatJson(String json) {
        String m = ambilStringJson(json, "message");
        if (m == null || m.length() == 0) return "tanpa keterangan";
        if (m.length() > 200) m = m.substring(0, 200);
        return m;
    }

    /** Ubah teks menjadi string JSON berpetik (dengan escape). */
    public static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder();
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c < 0x20) {
                b.append("\\u");
                String h = Integer.toHexString(c);
                for (int j = h.length(); j < 4; j++) b.append('0');
                b.append(h);
            } else b.append(c);
        }
        b.append('"');
        return b.toString();
    }

    /**
     * Ambil nilai string pertama untuk kunci JSON tertentu
     * (mis. "text" pada Gemini, "content" pada chat/completions).
     * null bila kunci tidak ada atau nilainya bukan string.
     */
    public static String ambilStringJson(String json, String kunci) {
        if (json == null || kunci == null) return null;
        String cari = "\"" + kunci + "\"";
        int idx = json.indexOf(cari);
        while (idx >= 0) {
            int p = idx + cari.length();
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
            if (p < json.length() && json.charAt(p) == ':') {
                p++;
                while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
                if (p < json.length() && json.charAt(p) == '"') {
                    return bacaStringJson(json, p);
                }
                return null;
            }
            idx = json.indexOf(cari, idx + 1);
        }
        return null;
    }

    /** Baca isi string JSON mulai dari tanda petik pembuka (urai escape). */
    private static String bacaStringJson(String json, int awal) {
        StringBuilder b = new StringBuilder();
        for (int i = awal + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                if (n == 'n') b.append('\n');
                else if (n == 'r') b.append('\r');
                else if (n == 't') b.append('\t');
                else if (n == 'u' && i + 5 < json.length()) {
                    try {
                        b.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                    } catch (Exception e) {
                        // abaikan escape rusak
                    }
                    i += 4;
                } else b.append(n);
                i++;
            } else if (c == '"') {
                return b.toString();
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Tanya AI dengan cadangan kunci dan model: coba tiap kunci
     * provider itu (KunciApi.kunciBerikutnya), dan tiap model di
     * rantai PenyediaAi bila beralihModel. Berhenti pada jawaban
     * pertama yang berhasil. Bila semua gagal, dariAi false dan
     * galat berisi alasan terakhir (tanpa nilai kunci).
     */
    public static JawabanAi tanyaDenganCadangan(KunciApi.Penyimpanan simpan,
            String kodeProvider, boolean beralihModel, String perintah,
            PabrikPemanggil pabrik) {
        JawabanAi hasil = new JawabanAi();
        PenyediaAi.Provider p = PenyediaAi.dariKode(kodeProvider);
        if (p == null) {
            hasil.galat = "Provider AI tidak dikenal";
            return hasil;
        }
        hasil.penyedia = p.nama;
        if (simpan == null || pabrik == null) {
            hasil.galat = "Penyimpanan kunci tidak tersedia";
            return hasil;
        }
        List<String> namaKunci = new ArrayList<String>();
        String namaAwal = null;
        KunciApi.Entri e = KunciApi.kunciBerikutnya(simpan, kodeProvider, null);
        while (e != null) {
            if (namaAwal == null) namaAwal = e.nama;
            else if (e.nama.equals(namaAwal)) break;
            namaKunci.add(e.nama);
            if (namaKunci.size() > 20) break; // pengaman
            e = KunciApi.kunciBerikutnya(simpan, kodeProvider, e.nama);
        }
        if (namaKunci.isEmpty()) {
            hasil.galat = "Tidak ada kunci API untuk " + p.nama + " di Pengaturan AI";
            return hasil;
        }
        String[] rantai = PenyediaAi.rantaiModel(kodeProvider);
        if (rantai.length == 0) rantai = new String[]{p.modelUtama()};
        String galatTerakhir = "";
        for (int k = 0; k < namaKunci.size(); k++) {
            KunciApi.Entri entri = KunciApi.cari(simpan, namaKunci.get(k));
            if (entri == null) continue;
            for (int m = 0; m < rantai.length; m++) {
                if (m > 0 && !beralihModel) break;
                try {
                    PemanggilAi pemanggil = pabrik.buat(kodeProvider, rantai[m], entri.kunci);
                    String teks = pemanggil.tanya(perintah);
                    if (teks != null && teks.trim().length() > 0) {
                        hasil.dariAi = true;
                        hasil.teks = teks;
                        hasil.model = rantai[m];
                        return hasil;
                    }
                    galatTerakhir = "Jawaban AI kosong";
                } catch (Exception ex) {
                    galatTerakhir = ex.getMessage() == null ? "Galat AI" : ex.getMessage();
                }
            }
        }
        hasil.galat = galatTerakhir.length() == 0
                ? "AI tidak dapat dihubungi" : galatTerakhir;
        return hasil;
    }

    // ------------------------------------------------------- cetak biru AI

    /** Perintah ke AI untuk menyusun cetak biru (gagasan dipangkas 500 huruf). */
    public static String perintahCetakBiru(String gagasan) {
        String g = gagasan == null ? "" : gagasan.trim();
        if (g.length() > 500) g = g.substring(0, 500);
        return "Susun cetak biru proyek aplikasi dari gagasan berikut: " + g + "\n"
                + "Jawab HANYA dengan format berikut, tanpa penjelasan lain, tanpa blok kode:\n"
                + "Cetak biru: " + g + "\n"
                + "Modul:\n- <nama modul>\n"
                + "Tonggak:\n1. [belum] <nama tonggak>\n   - <berkas, contoh: model/Model.java>\n"
                + "Kemajuan: 0%\n"
                + "Aturan: 3 sampai 8 tonggak, urut dari fondasi (model dan penyimpanan) "
                + "sampai pengujian dan rilis. Tiap tonggak berisi 1 sampai 4 berkas "
                + "dengan jalur relatif. Tulis semua dalam bahasa Indonesia.";
    }

    /**
     * Cetak biru dari jawaban AI (harus mengikuti format eksporTeks).
     * null bila jawaban kosong, bukan dari AI, atau tidak memuat
     * modul dan tonggak yang bisa diurai — pemanggil lalu memakai
     * pola luring buat().
     */
    public static CetakBiru buatDenganAi(String gagasan, JawabanAi jawaban) {
        if (jawaban == null || !jawaban.dariAi || jawaban.teks == null) return null;
        CetakBiru b = uraiTeks(jawaban.teks);
        if (b.tonggak.size() == 0 || b.modul.size() == 0) return null;
        if (gagasan != null && gagasan.trim().length() > 0) b.gagasan = gagasan.trim();
        b.sumber = "AI " + jawaban.penyedia
                + (jawaban.model.length() > 0 ? " (" + jawaban.model + ")" : "");
        return b;
    }
}
