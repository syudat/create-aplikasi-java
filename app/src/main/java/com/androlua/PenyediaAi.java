package com.androlua;

/**
 * Katalog provider AI untuk Pengaturan AI (perintah al kautsar
 * 2026-11-24 13:16 WITA): DeepSeek, Grok, Gemini, OpenRouter, Mistral.
 *
 * Kelas murni tanpa Android, bisa diuji di JVM.
 *
 * VERIFIKASI MODEL (aturan skill verify_llm_provider_before_adding_it,
 * dicek dari sumber live 2026-11-24 sebelum daftar ini ditulis):
 * - Gemini: rantai Lite diuji langsung dengan kunci al kautsar 2026-11-22
 *   (gemini-3.5-flash-lite, gemini-3.1-flash-lite,
 *   gemini-3.1-flash-lite-preview, gemini-flash-lite-latest). PERHATIAN:
 *   gemini-1.5-flash yang disebut al kautsar sebagai contoh cadangan
 *   SUDAH DIMATIKAN Google di API Gemini pada 29 September 2025 — TIDAK
 *   ADA di rantai ini; cadangan yang dipakai adalah rantai Lite di atas.
 * - Grok: base https://api.x.ai/v1 (docs.x.ai, OpenAI-compatible, kunci
 *   dari console.x.ai). grok-4.7 flagship per docs.x.ai 2026-09-21;
 *   grok-4.3 dan grok-4.1-fast masih tercantum di sumber live Juni–
 *   November 2026; satu sumber mencatat grok-4.1-fast mungkin
 *   dialihkan xAI ke grok-4.3 (catatan pensiun 2026-05-15).
 * - DeepSeek: base https://api.deepseek.com (api-docs.deepseek.com,
 *   kunci dari platform.deepseek.com). deepseek-chat dan
 *   deepseek-reasoner SUDAH DIPENSIUNKAN 24 Juli 2026 — TIDAK ADA di
 *   daftar ini; model yang berlaku: deepseek-flash (bawaan) dan
 *   deepseek-v4-pro (cadangan).
 * - OpenRouter: base https://openrouter.ai/api/v1, kunci dari
 *   openrouter.ai. Ketiga model di bawah DITEMUKAN LANGSUNG di daftar
 *   live https://openrouter.ai/api/v1/models 2026-11-24:
 *   google/gemini-2.5-flash, google/gemini-2.5-flash-lite,
 *   openai/gpt-4o-mini.
 * - Mistral: base https://api.mistral.ai/v1, kunci dari
 *   console.mistral.ai. mistral-large-latest (Large 4, rilis
 *   2026-11-05), mistral-small-latest (Small 4), codestral-latest —
 *   terverifikasi di dokumen Mistral/Docker. Catatan: akses API gratis
 *   Mistral berakhir awal November 2026 — kunci API butuh penagihan
 *   aktif (pay-as-you-go).
 *
 * Rantai model: indeks 0 = model utama, sisanya cadangan berurutan.
 * Bila model utama error dan kotak "Beralih model saat provider error"
 * dicentang, aplikasi mencoba model berikutnya di rantai yang sama.
 */
public class PenyediaAi {

    public static class Provider {
        public final String kode;
        public final String nama;
        public final String baseUrl;
        public final String penerbitKunci;
        public final String[] model;
        public final String catatan;

        public Provider(String kode, String nama, String baseUrl,
                String penerbitKunci, String[] model, String catatan) {
            this.kode = kode;
            this.nama = nama;
            this.baseUrl = baseUrl;
            this.penerbitKunci = penerbitKunci;
            this.model = model;
            this.catatan = catatan;
        }

        public String modelUtama() {
            return model.length > 0 ? model[0] : null;
        }
    }

    public static final Provider[] SEMUA = {
        new Provider("gemini", "Gemini",
                "https://generativelanguage.googleapis.com/v1beta",
                "Google AI Studio (aistudio.google.com)",
                new String[]{
                    "gemini-3.5-flash-lite",
                    "gemini-3.1-flash-lite",
                    "gemini-3.1-flash-lite-preview",
                    "gemini-flash-lite-latest"},
                "gemini-1.5-flash dimatikan Google 2025-09-29; "
                        + "rantai cadangan memakai model Lite yang teruji."),
        new Provider("grok", "Grok",
                "https://api.x.ai/v1",
                "xAI Console (console.x.ai)",
                new String[]{"grok-4.7", "grok-4.3", "grok-4.1-fast"},
                "grok-4.7 flagship per docs.x.ai 2026-09-21; "
                        + "grok-4.1-fast mungkin dialihkan xAI ke grok-4.3."),
        new Provider("deepseek", "DeepSeek",
                "https://api.deepseek.com",
                "DeepSeek Platform (platform.deepseek.com)",
                new String[]{"deepseek-flash", "deepseek-v4-pro"},
                "deepseek-chat dan deepseek-reasoner dipensiunkan "
                        + "2026-07-24; tidak dipakai."),
        new Provider("openrouter", "OpenRouter",
                "https://openrouter.ai/api/v1",
                "OpenRouter (openrouter.ai)",
                new String[]{
                    "google/gemini-2.5-flash",
                    "google/gemini-2.5-flash-lite",
                    "openai/gpt-4o-mini"},
                "Ketiga model ditemukan langsung di daftar live "
                        + "/v1/models 2026-11-24."),
        new Provider("mistral", "Mistral",
                "https://api.mistral.ai/v1",
                "Mistral Console (console.mistral.ai)",
                new String[]{
                    "mistral-small-latest",
                    "mistral-large-latest",
                    "codestral-latest"},
                "Akses API gratis berakhir awal November 2026; "
                        + "kunci API butuh penagihan aktif."),
    };

    /** Provider bawaan bila belum ada pilihan tersimpan. */
    public static final String KODE_BAWAAN = "gemini";

    /** Cari provider dari kodenya; null bila tidak dikenal. */
    public static Provider dariKode(String kode) {
        if (kode == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].kode.equals(kode)) return SEMUA[i];
        }
        return null;
    }

    /** Semua kode provider, urutan tampilan. */
    public static String[] semuaKode() {
        String[] hasil = new String[SEMUA.length];
        for (int i = 0; i < SEMUA.length; i++) hasil[i] = SEMUA[i].kode;
        return hasil;
    }

    /** Rantai model provider (salinan; indeks 0 = utama). Kosong bila kode tak dikenal. */
    public static String[] rantaiModel(String kode) {
        Provider p = dariKode(kode);
        if (p == null) return new String[0];
        String[] salinan = new String[p.model.length];
        for (int i = 0; i < p.model.length; i++) salinan[i] = p.model[i];
        return salinan;
    }

    /** Model utama provider; null bila kode tak dikenal. */
    public static String modelUtama(String kode) {
        Provider p = dariKode(kode);
        return p == null ? null : p.modelUtama();
    }

    /**
     * Model cadangan berikutnya setelah modelSaatIni dalam rantai
     * provider yang sama. Mengembalikan null bila modelSaatIni sudah
     * model terakhir, tidak ada di rantai, atau provider tak dikenal.
     * Dipakai pengalih otomatis saat provider/model utama error.
     */
    public static String modelCadanganSesudah(String kode, String modelSaatIni) {
        Provider p = dariKode(kode);
        if (p == null || modelSaatIni == null) return null;
        for (int i = 0; i < p.model.length; i++) {
            if (p.model[i].equals(modelSaatIni)) {
                return i + 1 < p.model.length ? p.model[i + 1] : null;
            }
        }
        return null;
    }

    /** Apakah masih ada model cadangan sesudah modelSaatIni? */
    public static boolean adaCadanganSesudah(String kode, String modelSaatIni) {
        return modelCadanganSesudah(kode, modelSaatIni) != null;
    }

    /**
     * Validasi nama kunci API. Nama dipakai sebagai identitas kunci
     * (boleh sama antar provider, tidak boleh sama dalam satu provider).
     * Aturan: tidak kosong, paling panjang 40 huruf, tanpa baris baru,
     * tab, atau pemisah "|". Mengembalikan null bila sah, pesan galat
     * (tanpa nilai kunci) bila tidak sah.
     */
    public static String galatNamaKunci(String nama) {
        if (nama == null) return "Nama kunci tidak boleh kosong";
        String n = nama.trim();
        if (n.length() == 0) return "Nama kunci tidak boleh kosong";
        if (n.length() > 40) return "Nama kunci paling panjang 40 huruf";
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == '|') {
                return "Nama kunci tidak boleh memakai baris baru, tab, atau |";
            }
        }
        return null;
    }

    /** Apakah nama kunci sah menurut galatNamaKunci? */
    public static boolean namaKunciSah(String nama) {
        return galatNamaKunci(nama) == null;
    }

    /**
     * Validasi nilai kunci API (tanpa pernah mencatat nilainya):
     * tidak kosong sesudah dipangkas, tanpa spasi di dalamnya.
     * Mengembalikan null bila sah, pesan galat bila tidak sah.
     */
    public static String galatNilaiKunci(String kunci) {
        if (kunci == null) return "Kunci tidak boleh kosong";
        String k = kunci.trim();
        if (k.length() == 0) return "Kunci tidak boleh kosong";
        for (int i = 0; i < k.length(); i++) {
            if (Character.isWhitespace(k.charAt(i))) {
                return "Kunci tidak boleh memakai spasi";
            }
        }
        return null;
    }
}
