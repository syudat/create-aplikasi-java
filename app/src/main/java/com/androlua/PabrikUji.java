package com.androlua;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pabrik Pengujian Unit Otomatis dan Generator Fuzzing Batas
 * (Fitur 9, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer.
 * Yang sudah NYATA di versi ini (bukan kerangka):
 *  - Uji yang dihasilkan adalah uji yang bisa DIJALANKAN: memanggil
 *    metode proyek sungguhan dengan masukan normal + batas, memeriksa
 *    determinisme (dua kali panggil hasilnya sama) dan mencatat nilai
 *    aktualnya. Dijalankan di ponsel oleh PelariUji (lihat bawah)
 *    atas kelas yang dikompilasi ECJ + D8 lalu dimuat DexClassLoader
 *    (alur di PabrikUjiActivity).
 *  - Fuzzing sungguhan atas FUNGSI PROYEK (fuzzKelas): metode proyek
 *    dimuat lewat refleksi lalu dipanggil dengan masukan ekstrem
 *    nyata; galat / hasil bukan-angka dicatat sebagai gagal.
 *  - Cakupan baris sungguhan (instrumentasi): salinan sumber proyek
 *    disisipi pemanggil catat() di tiap baris pernyataan (nomor baris
 *    ASLI dipertahankan — tidak ada baris yang ditambah/dihapus),
 *    lalu hitungCakupan membandingkan baris yang terukur dengan
 *    baris yang benar-benar tereksekusi saat uji + fuzz berjalan.
 *
 * BATAS JUJUR:
 *  - Pustaka JUnit asli TIDAK dibundel di aplikasi dan tidak bisa
 *    diunduh saat luring. Uji yang dihasilkan memakai API yang sama
 *    persis dengan JUnit (anotasi @Test + asersi assertEquals,
 *    assertTrue, assertNull, ...) tetapi definisi anotasi dan asersi
 *    itu ditulis DI DALAM berkas uji yang dihasilkan, supaya bisa
 *    dikompilasi ECJ tanpa classpath tambahan. Bila proyek sudah
 *    memakai JUnit asli, ganti dua baris impor/definisi itu dengan
 *    org.junit.Test dan org.junit.Assert — badan ujinya sama.
 *  - Fuzzing menjalankan metode proyek sungguhan di dalam proses IDE
 *    (dengan batas waktu per panggilan). Metode yang menghapus berkas,
 *    keluar dari aplikasi, atau berputar tanpa henti dapat berdampak;
 *    jalankan fuzz hanya pada proyek yang dipercaya.
 *  - Cakupan mengukur baris yang DAPAT disisipi pemanggil dengan aman
 *    (baris pernyataan utuh). Baris lanjutan (sambungan pernyataan
 *    multi-baris), deklarasi, dan anotasi dilaporkan sebagai
 *    "tidak terukur", bukan ditebak.
 */
public class PabrikUji {

    /** Satu kasus uji yang dihasilkan. */
    public static class KasusUji {
        public String nama = "";
        public String masukan = "";
        public String jenis = "";
        /** Daftar argumen lengkap pemanggilan (teks sumber), mis. "0, 10.0". */
        public String argumen = "";
        /** Benar bila metode yang diuji bersifat statis. */
        public boolean statis = false;
        /** Tipe kembalian metode ("" bila konstruktor / tak diketahui). */
        public String kembali = "";
        public String toString() {
            return jenis + " " + nama + "(" + masukan + ")";
        }
    }

    /** Satu hasil fuzz: masukan + lulus/gagal + pesan. */
    public static class HasilFuzz {
        public String masukan = "";
        public boolean lulus = true;
        public String pesan = "";
    }

    /** Uraian lengkap satu tanda tangan metode. */
    public static class TandaTangan {
        public String kembali = "";
        public String nama = "";
        public List<String> tipeParam = new ArrayList<String>();
        public boolean statis = false;
        public boolean publik = true;
    }

    /** Masukan ekstrem untuk satu tipe parameter (teks sumber Java). */
    public static List<String> masukanFuzz(String tipe) {
        List<String> hasil = new ArrayList<String>();
        if (tipe == null) return hasil;
        String t = tipe.trim();
        if ("String".equals(t) || "CharSequence".equals(t)) {
            hasil.add("\"\"");
            hasil.add("null");
            hasil.add("\" \"");
            hasil.add("\"+628123456789\"");
            hasil.add("\"++62812\"");
            hasil.add("\"0812abc345\"");
            String panjang = "";
            for (int i = 0; i < 500; i++) panjang += "9";
            hasil.add("\"" + panjang + "\"");
            hasil.add("\"\\u2603\\u00e9\\u4e2d\"");
        } else if ("int".equals(t) || "Integer".equals(t) || "long".equals(t)) {
            hasil.add("0");
            hasil.add("1");
            hasil.add("-1");
            hasil.add("2147483647");
            hasil.add("-2147483648");
            hasil.add("100000000");
        } else if ("double".equals(t) || "float".equals(t)) {
            hasil.add("0.0");
            hasil.add("-0.5");
            hasil.add("100.0");
            hasil.add("1.7976931348623157E308");
        } else if ("boolean".equals(t)) {
            hasil.add("true");
            hasil.add("false");
        } else {
            hasil.add("null");
        }
        return hasil;
    }

    /** Nilai bawaan (netral) satu tipe, sebagai teks sumber Java. */
    public static String literalBawaan(String tipe) {
        if (tipe == null) return "null";
        String t = tipe.trim();
        if ("String".equals(t) || "CharSequence".equals(t)) return "\"\"";
        if ("char".equals(t) || "Character".equals(t)) return "'a'";
        if ("long".equals(t) || "Long".equals(t)) return "0L";
        if ("double".equals(t) || "Double".equals(t)) return "0.0";
        if ("float".equals(t) || "Float".equals(t)) return "0.0f";
        if ("boolean".equals(t) || "Boolean".equals(t)) return "false";
        if ("byte".equals(t) || "Byte".equals(t)) return "(byte) 0";
        if ("short".equals(t) || "Short".equals(t)) return "(short) 0";
        if ("int".equals(t) || "Integer".equals(t)) return "0";
        return "null";
    }

    /**
     * Nilai fuzz NYATA (objek Java, bukan teks sumber) untuk satu tipe,
     * dipakai fuzzKelas saat memanggil metode proyek lewat refleksi.
     */
    public static List<Object> nilaiFuzz(String tipe) {
        List<Object> hasil = new ArrayList<Object>();
        if (tipe == null) {
            hasil.add(null);
            return hasil;
        }
        String t = tipe.trim();
        if ("String".equals(t) || "CharSequence".equals(t)) {
            hasil.add("");
            hasil.add(null);
            hasil.add(" ");
            hasil.add("+628123456789");
            hasil.add("++62812");
            hasil.add("0812abc345");
            String panjang = "";
            for (int i = 0; i < 500; i++) panjang += "9";
            hasil.add(panjang);
            hasil.add("\u2603\u00e9\u4e2d");
        } else if ("int".equals(t) || "Integer".equals(t)) {
            hasil.add(Integer.valueOf(0));
            hasil.add(Integer.valueOf(1));
            hasil.add(Integer.valueOf(-1));
            hasil.add(Integer.valueOf(Integer.MAX_VALUE));
            hasil.add(Integer.valueOf(Integer.MIN_VALUE));
            hasil.add(Integer.valueOf(100000000));
        } else if ("long".equals(t) || "Long".equals(t)) {
            hasil.add(Long.valueOf(0L));
            hasil.add(Long.valueOf(1L));
            hasil.add(Long.valueOf(-1L));
            hasil.add(Long.valueOf(Long.MAX_VALUE));
            hasil.add(Long.valueOf(Long.MIN_VALUE));
        } else if ("double".equals(t) || "Double".equals(t)) {
            hasil.add(Double.valueOf(0.0));
            hasil.add(Double.valueOf(-0.5));
            hasil.add(Double.valueOf(100.0));
            hasil.add(Double.valueOf(Double.MAX_VALUE));
            hasil.add(Double.valueOf(Double.NaN));
            hasil.add(Double.valueOf(Double.POSITIVE_INFINITY));
        } else if ("float".equals(t) || "Float".equals(t)) {
            hasil.add(Float.valueOf(0.0f));
            hasil.add(Float.valueOf(-0.5f));
            hasil.add(Float.valueOf(100.0f));
            hasil.add(Float.valueOf(Float.MAX_VALUE));
            hasil.add(Float.valueOf(Float.NaN));
        } else if ("boolean".equals(t) || "Boolean".equals(t)) {
            hasil.add(Boolean.TRUE);
            hasil.add(Boolean.FALSE);
        } else if ("char".equals(t) || "Character".equals(t)) {
            hasil.add(Character.valueOf('a'));
            hasil.add(Character.valueOf('\0'));
            hasil.add(Character.valueOf('\u2603'));
        } else if ("byte".equals(t) || "Byte".equals(t)) {
            hasil.add(Byte.valueOf((byte) 0));
            hasil.add(Byte.valueOf((byte) 1));
            hasil.add(Byte.valueOf((byte) -1));
            hasil.add(Byte.valueOf(Byte.MAX_VALUE));
            hasil.add(Byte.valueOf(Byte.MIN_VALUE));
        } else if ("short".equals(t) || "Short".equals(t)) {
            hasil.add(Short.valueOf((short) 0));
            hasil.add(Short.valueOf((short) 1));
            hasil.add(Short.valueOf((short) -1));
            hasil.add(Short.valueOf(Short.MAX_VALUE));
            hasil.add(Short.valueOf(Short.MIN_VALUE));
        } else {
            hasil.add(null);
        }
        return hasil;
    }

    /** Nilai bawaan (netral) satu tipe, sebagai objek Java. */
    public static Object nilaiBawaan(String tipe) {
        if (tipe == null) return null;
        String t = tipe.trim();
        if ("String".equals(t) || "CharSequence".equals(t)) return "";
        if ("char".equals(t) || "Character".equals(t)) return Character.valueOf('a');
        if ("long".equals(t) || "Long".equals(t)) return Long.valueOf(0L);
        if ("double".equals(t) || "Double".equals(t)) return Double.valueOf(0.0);
        if ("float".equals(t) || "Float".equals(t)) return Float.valueOf(0.0f);
        if ("boolean".equals(t) || "Boolean".equals(t)) return Boolean.FALSE;
        if ("byte".equals(t) || "Byte".equals(t)) return Byte.valueOf((byte) 0);
        if ("short".equals(t) || "Short".equals(t)) return Short.valueOf((short) 0);
        if ("int".equals(t) || "Integer".equals(t)) return Integer.valueOf(0);
        return null;
    }

    /** Benar bila tipe ini didukung fuzz refleksi (punya nilai fuzz). */
    public static boolean tipeDidukungFuzz(String tipe) {
        if (tipe == null) return false;
        String t = tipe.trim();
        return "String".equals(t) || "CharSequence".equals(t) || "char".equals(t)
                || "Character".equals(t) || "long".equals(t) || "Long".equals(t)
                || "double".equals(t) || "Double".equals(t) || "float".equals(t)
                || "Float".equals(t) || "boolean".equals(t) || "Boolean".equals(t)
                || "byte".equals(t) || "Byte".equals(t) || "short".equals(t)
                || "Short".equals(t) || "int".equals(t) || "Integer".equals(t);
    }

    /** Urai tanda tangan metode Java sederhana menjadi nama + tipe param. */
    public static String[] uraiTandaTangan(String tanda) {
        if (tanda == null) return null;
        Matcher m = Pattern.compile(
                "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)").matcher(tanda.trim());
        if (!m.find()) return null;
        String nama = m.group(1);
        String param = m.group(2).trim();
        List<String> keluar = new ArrayList<String>();
        keluar.add(nama);
        if (param.length() > 0) {
            String[] bagian = param.split(",");
            for (int i = 0; i < bagian.length; i++) {
                String[] kata = bagian[i].trim().split("\\s+");
                if (kata.length > 0) keluar.add(kata[0]);
            }
        }
        return keluar.toArray(new String[keluar.size()]);
    }

    /** Pisahkan daftar parameter dengan koma, hormati <...> dan [...]. */
    static List<String> pisahParam(String param) {
        List<String> keluar = new ArrayList<String>();
        if (param == null) return keluar;
        String p = param.trim();
        if (p.length() == 0) return keluar;
        int dalamSudut = 0;
        int dalamKurung = 0;
        StringBuilder kini = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '<') dalamSudut++;
            else if (c == '>') dalamSudut--;
            else if (c == '[') dalamKurung++;
            else if (c == ']') dalamKurung--;
            if (c == ',' && dalamSudut == 0 && dalamKurung == 0) {
                keluar.add(kini.toString().trim());
                kini.setLength(0);
            } else {
                kini.append(c);
            }
        }
        if (kini.toString().trim().length() > 0) keluar.add(kini.toString().trim());
        return keluar;
    }

    /** Urai lengkap: tipe kembalian, nama, tipe param, statis, publik. */
    public static TandaTangan uraiLengkap(String tanda) {
        if (tanda == null) return null;
        String[] dasar = uraiTandaTangan(tanda);
        if (dasar == null) return null;
        TandaTangan hasil = new TandaTangan();
        hasil.nama = dasar[0];
        for (int i = 1; i < dasar.length; i++) hasil.tipeParam.add(dasar[i]);
        String t = tanda.trim();
        hasil.statis = Pattern.compile("\\bstatic\\b").matcher(t).find();
        hasil.publik = !Pattern.compile("\\b(private|protected)\\b").matcher(t).find();
        // Tipe kembalian: teks sebelum nama metode, tanpa kata pengubah.
        Matcher m = Pattern.compile(
                "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)").matcher(t);
        if (m.find()) {
            String depan = t.substring(0, m.start(1)).trim();
            String[] kata = depan.split("\\s+");
            StringBuilder kembali = new StringBuilder();
            for (int i = 0; i < kata.length; i++) {
                String k = kata[i];
                if (k.length() == 0) continue;
                if ("public".equals(k) || "private".equals(k) || "protected".equals(k)
                        || "static".equals(k) || "final".equals(k) || "abstract".equals(k)
                        || "synchronized".equals(k) || "native".equals(k)
                        || "strictfp".equals(k)) continue;
                if (kembali.length() > 0) kembali.append(' ');
                kembali.append(k);
            }
            hasil.kembali = kembali.toString();
            // Bila tidak ada tipe kembalian, ini konstruktor: namanya sama
            // dengan kata terakhir sebelum '(' — biarkan kembali kosong.
        }
        return hasil;
    }

    /** Hasilkan kasus uji normal + batas dari tanda tangan fungsi. */
    public static List<KasusUji> ujiDariTandaTangan(String tanda) {
        List<KasusUji> hasil = new ArrayList<KasusUji>();
        TandaTangan urai = uraiLengkap(tanda);
        if (urai == null) return hasil;
        // Metode privat/terlindungi tidak bisa dipanggil dari kelas uji
        // (beda kelas, paket sama) — jangan hasilkan kasus yang pasti
        // gagal kompilasi.
        if (!urai.publik) return hasil;
        String nama = urai.nama;
        if (urai.tipeParam.size() == 0) {
            KasusUji k = new KasusUji();
            k.nama = nama;
            k.masukan = "";
            k.argumen = "";
            k.jenis = "normal";
            k.statis = urai.statis;
            k.kembali = urai.kembali;
            hasil.add(k);
            return hasil;
        }
        for (int i = 0; i < urai.tipeParam.size(); i++) {
            List<String> masukan = masukanFuzz(urai.tipeParam.get(i));
            for (int j = 0; j < masukan.size(); j++) {
                KasusUji k = new KasusUji();
                k.nama = nama;
                k.masukan = masukan.get(j);
                k.jenis = j == 0 ? "normal" : "batas";
                k.statis = urai.statis;
                k.kembali = urai.kembali;
                StringBuilder arg = new StringBuilder();
                for (int p = 0; p < urai.tipeParam.size(); p++) {
                    if (p > 0) arg.append(", ");
                    if (p == i) arg.append(masukan.get(j));
                    else arg.append(literalBawaan(urai.tipeParam.get(p)));
                }
                k.argumen = arg.toString();
                hasil.add(k);
            }
        }
        return hasil;
    }

    /** Fungsi contoh 1: normalisasi nomor telepon Indonesia. */
    public static String normalisasiTelepon(String nomor) {
        if (nomor == null) return null;
        String bersih = nomor.replaceAll("[^0-9+]", "");
        if (bersih.startsWith("+62")) return bersih;
        if (bersih.startsWith("62")) return "+" + bersih;
        if (bersih.startsWith("0")) return "+62" + bersih.substring(1);
        if (bersih.length() == 0) return "";
        return "+62" + bersih;
    }

    /** Fungsi contoh 2: diskon bertingkat (harga, persen 0-100). */
    public static double hitungDiskon(double harga, double persen) {
        if (harga < 0) throw new IllegalArgumentException("harga negatif");
        if (persen < 0 || persen > 100) {
            throw new IllegalArgumentException("persen di luar 0-100");
        }
        return harga - (harga * persen / 100.0);
    }

    /** Jalankan fuzz atas normalisasi telepon dengan masukan ekstrem. */
    public static List<HasilFuzz> fuzzTelepon() {
        List<HasilFuzz> hasil = new ArrayList<HasilFuzz>();
        List<String> masukan = masukanFuzz("String");
        String[] mentah = {"", null, " ", "+628123456789", "++62812",
                "0812abc345", null, null};
        mentah[6] = new String(new char[500]).replace("\0", "9");
        mentah[7] = "\u2603\u00e9\u4e2d";
        for (int i = 0; i < masukan.size(); i++) {
            HasilFuzz h = new HasilFuzz();
            h.masukan = masukan.get(i);
            try {
                String keluar = normalisasiTelepon(mentah[i]);
                h.lulus = keluar != null;
                h.pesan = keluar == null ? "hasil null" : keluar;
            } catch (Throwable t) {
                h.lulus = false;
                h.pesan = t.getClass().getSimpleName();
            }
            hasil.add(h);
        }
        return hasil;
    }

    /** Jalankan fuzz atas hitung diskon dengan pasangan ekstrem. */
    public static List<HasilFuzz> fuzzDiskon() {
        List<HasilFuzz> hasil = new ArrayList<HasilFuzz>();
        double[][] pasangan = {{100000, 10}, {0, 0}, {100000, 0}, {100000, 100},
                {100000, -5}, {100000, 150}, {-5000, 10}, {100000, 50}};
        for (int i = 0; i < pasangan.length; i++) {
            HasilFuzz h = new HasilFuzz();
            h.masukan = pasangan[i][0] + ", " + pasangan[i][1];
            try {
                double keluar = hitungDiskon(pasangan[i][0], pasangan[i][1]);
                h.lulus = keluar >= 0 && keluar <= pasangan[i][0];
                h.pesan = String.valueOf(keluar);
            } catch (Throwable t) {
                h.lulus = false;
                h.pesan = t.getMessage();
            }
            hasil.add(h);
        }
        return hasil;
    }

    // ------------------------------------------------------------------
    // Pelari uji (JUnit-gaya): menjalankan metode @Test di kelas hasil
    // kompilasi, dengan batas waktu per metode. Metode uji dikenali dari
    // anotasi bernama "Test" (apa pun paketnya — termasuk @Test di dalam
    // berkas uji yang dihasilkan) atau dari nama yang diawali "uji".
    // ------------------------------------------------------------------

    /** Satu hasil eksekusi uji. */
    public static class HasilUji {
        public String nama = "";
        public boolean lulus = true;
        public String pesan = "";
        public long ms = 0;
    }

    private static ThreadFactory pabrikUtasDaemon(final String nama) {
        return new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, nama);
                t.setDaemon(true);
                return t;
            }
        };
    }

    /** Panggil metode statis tanpa argumen bila ada; abaikan bila tidak ada. */
    static void panggilStatisBilaAda(Class<?> kelas, String namaMetode) {
        try {
            Method m = kelas.getMethod(namaMetode);
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0) {
                m.invoke(null);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Panggil metode statis tanpa argumen yang mengembalikan objek; null bila tidak ada. */
    static Object panggilStatisHasilBilaAda(Class<?> kelas, String namaMetode) {
        try {
            Method m = kelas.getMethod(namaMetode);
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0) {
                return m.invoke(null);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Jalankan semua metode @Test di kelas uji; tiap metode dibatasi 5 detik. */
    public static List<HasilUji> jalankanUji(Class<?> kelasUji) {
        List<HasilUji> hasil = new ArrayList<HasilUji>();
        if (kelasUji == null) return hasil;
        panggilStatisBilaAda(kelasUji, "bersihCakupan");
        Method[] metode = kelasUji.getDeclaredMethods();
        for (int i = 0; i < metode.length; i++) {
            final Method m = metode[i];
            if (!Modifier.isPublic(m.getModifiers())) continue;
            if (!"void".equals(m.getReturnType().getName())) continue;
            if (m.getParameterTypes().length != 0) continue;
            boolean adaTest = false;
            Annotation[] anotasi = m.getDeclaredAnnotations();
            for (int a = 0; a < anotasi.length; a++) {
                if ("Test".equals(anotasi[a].annotationType().getSimpleName())) {
                    adaTest = true;
                    break;
                }
            }
            if (!adaTest && !m.getName().startsWith("uji")) continue;
            final HasilUji h = new HasilUji();
            h.nama = m.getName();
            final Object[] wadah;
            try {
                wadah = new Object[]{kelasUji.getConstructor().newInstance()};
            } catch (Throwable t) {
                h.lulus = false;
                h.pesan = "tidak bisa membuat objek uji: " + t.getClass().getSimpleName();
                hasil.add(h);
                continue;
            }
            final long mulai = System.currentTimeMillis();
            ExecutorService ex = Executors.newSingleThreadExecutor(
                    pabrikUtasDaemon("PabrikUji-" + m.getName()));
            Future<?> f = ex.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    m.invoke(wadah[0]);
                    return null;
                }
            });
            try {
                f.get(5, TimeUnit.SECONDS);
                h.lulus = true;
                h.pesan = "OK";
            } catch (java.util.concurrent.TimeoutException te) {
                h.lulus = false;
                h.pesan = "waktu habis (lebih dari 5 detik)";
                f.cancel(true);
            } catch (java.util.concurrent.ExecutionException ee) {
                h.lulus = false;
                Throwable penyebab = ee.getCause();
                if (penyebab instanceof InvocationTargetException
                        && penyebab.getCause() != null) {
                    penyebab = penyebab.getCause();
                }
                String pesan = penyebab.getMessage();
                h.pesan = penyebab.getClass().getSimpleName()
                        + (pesan == null ? "" : ": " + pesan);
            } catch (Throwable t) {
                h.lulus = false;
                h.pesan = t.getClass().getSimpleName();
            } finally {
                ex.shutdownNow();
            }
            h.ms = System.currentTimeMillis() - mulai;
            hasil.add(h);
        }
        return hasil;
    }

    /** Nilai aktual yang dicatat uji (ambilHasilAktual di kelas uji), kosong bila tidak ada. */
    public static List<String> ambilHasilAktual(Class<?> kelasUji) {
        List<String> keluar = new ArrayList<String>();
        Object o = panggilStatisHasilBilaAda(kelasUji, "ambilHasilAktual");
        if (o instanceof List) {
            List<?> daftar = (List<?>) o;
            for (int i = 0; i < daftar.size(); i++) {
                if (daftar.get(i) != null) keluar.add(String.valueOf(daftar.get(i)));
            }
        }
        return keluar;
    }

    /** Baris yang terkena pemanggil cakupan (ambilCakupan di kelas uji), kosong bila tidak ada. */
    public static Set<String> ambilCakupan(Class<?> kelasUji) {
        Set<String> keluar = new HashSet<String>();
        Object o = panggilStatisHasilBilaAda(kelasUji, "ambilCakupan");
        if (o instanceof Set) {
            Set<?> set = (Set<?>) o;
            for (Object e : set) {
                if (e != null) keluar.add(String.valueOf(e));
            }
        }
        return keluar;
    }

    // ------------------------------------------------------------------
    // Fuzzing sungguhan atas metode proyek (refleksi + batas waktu).
    // ------------------------------------------------------------------

    /** Satu hasil fuzz atas satu pemanggilan metode proyek. */
    public static class HasilFuzzProyek {
        public String metode = "";
        public String masukan = "";
        public boolean lulus = true;
        public String pesan = "";
    }

    /** Hasil fuzz satu kelas: yang dijalankan + yang dilewati + alasannya. */
    public static class HasilFuzzKelas {
        public List<HasilFuzzProyek> hasil = new ArrayList<HasilFuzzProyek>();
        public List<String> dilewati = new ArrayList<String>();
    }

    /** Nama tipe yang enak dibaca untuk laporan fuzz. */
    static String namaTipeSingkat(Class<?> c) {
        if (c == null) return "?";
        if (c.isArray()) return namaTipeSingkat(c.getComponentType()) + "[]";
        return c.getSimpleName();
    }

    /** Fuzz semua metode publik kelas proyek yang parameternya didukung. */
    public static HasilFuzzKelas fuzzKelas(Class<?> kelas) {
        HasilFuzzKelas keluar = new HasilFuzzKelas();
        if (kelas == null) return keluar;
        Method[] metode = kelas.getDeclaredMethods();
        int jumlahMetode = 0;
        for (int i = 0; i < metode.length; i++) {
            Method m = metode[i];
            if (m.isSynthetic() || m.isBridge()) continue;
            int mod = m.getModifiers();
            if (!Modifier.isPublic(mod)) continue;
            if (Modifier.isAbstract(mod) || Modifier.isNative(mod)) continue;
            if ("main".equals(m.getName())) {
                keluar.dilewati.add(m.getName() + ": metode main dilewati");
                continue;
            }
            Class<?>[] param = m.getParameterTypes();
            boolean dukung = true;
            for (int p = 0; p < param.length; p++) {
                if (!tipeDidukungFuzz(param[p].getSimpleName())) {
                    dukung = false;
                    break;
                }
            }
            if (!dukung) {
                keluar.dilewati.add(m.getName() + ": tipe parameter tidak didukung fuzz");
                continue;
            }
            jumlahMetode++;
            if (jumlahMetode > 20) {
                keluar.dilewati.add("... metode selebihnya dilewati (batas 20 metode per jalan)");
                break;
            }
            boolean statis = Modifier.isStatic(mod);
            // Rencana pemanggilan: tiap parameter digilir dengan nilai
            // ekstremnya, parameter lain memakai nilai bawaan.
            List<Object[]> rencana = new ArrayList<Object[]>();
            if (param.length == 0) {
                rencana.add(new Object[0]);
            } else {
                for (int p = 0; p < param.length; p++) {
                    List<Object> nilai = nilaiFuzz(param[p].getSimpleName());
                    for (int v = 0; v < nilai.size(); v++) {
                        Object[] arg = new Object[param.length];
                        for (int q = 0; q < param.length; q++) {
                            arg[q] = (q == p) ? nilai.get(v)
                                    : nilaiBawaan(param[q].getSimpleName());
                        }
                        rencana.add(arg);
                        if (rencana.size() >= 24) break;
                    }
                    if (rencana.size() >= 24) break;
                }
            }
            for (int r = 0; r < rencana.size(); r++) {
                final Object[] arg = rencana.get(r);
                final Method mm = m;
                final Object target;
                if (statis) {
                    target = null;
                } else {
                    Object obj = null;
                    try {
                        Constructor<?> ctor = kelas.getConstructor();
                        obj = ctor.newInstance();
                    } catch (Throwable t) {
                        keluar.dilewati.add(m.getName()
                                + ": tanpa konstruktor tanpa argumen");
                        break;
                    }
                    target = obj;
                }
                final HasilFuzzProyek h = new HasilFuzzProyek();
                h.metode = m.getName();
                StringBuilder sb = new StringBuilder();
                for (int a = 0; a < arg.length; a++) {
                    if (a > 0) sb.append(", ");
                    sb.append(arg[a] == null ? "null" : potong(String.valueOf(arg[a]), 40));
                }
                h.masukan = sb.toString();
                ExecutorService ex = Executors.newSingleThreadExecutor(
                        pabrikUtasDaemon("PabrikUji-fuzz-" + m.getName()));
                Future<Object> f = ex.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        return mm.invoke(target, arg);
                    }
                });
                try {
                    Object kembali = f.get(2, TimeUnit.SECONDS);
                    h.pesan = "-> " + (kembali == null ? "null"
                            : potong(String.valueOf(kembali), 60));
                    Class<?> tipeKembali = m.getReturnType();
                    if (tipeKembali == double.class || tipeKembali == Double.class) {
                        double d = ((Number) kembali).doubleValue();
                        if (Double.isNaN(d) || Double.isInfinite(d)) {
                            h.lulus = false;
                            h.pesan = "hasil bukan angka berhingga: " + d;
                        } else {
                            h.lulus = true;
                        }
                    } else if (tipeKembali == float.class || tipeKembali == Float.class) {
                        float fl = ((Number) kembali).floatValue();
                        if (Float.isNaN(fl) || Float.isInfinite(fl)) {
                            h.lulus = false;
                            h.pesan = "hasil bukan angka berhingga: " + fl;
                        } else {
                            h.lulus = true;
                        }
                    } else {
                        h.lulus = true;
                    }
                } catch (java.util.concurrent.TimeoutException te) {
                    h.lulus = false;
                    h.pesan = "waktu habis (lebih dari 2 detik)";
                    f.cancel(true);
                } catch (java.util.concurrent.ExecutionException ee) {
                    h.lulus = false;
                    Throwable penyebab = ee.getCause();
                    if (penyebab instanceof InvocationTargetException
                            && penyebab.getCause() != null) {
                        penyebab = penyebab.getCause();
                    }
                    String pesan = penyebab.getMessage();
                    h.pesan = penyebab.getClass().getSimpleName()
                            + (pesan == null ? "" : ": " + potong(pesan, 80));
                } catch (Throwable t) {
                    h.lulus = false;
                    h.pesan = t.getClass().getSimpleName();
                } finally {
                    ex.shutdownNow();
                }
                keluar.hasil.add(h);
            }
        }
        return keluar;
    }

    static String potong(String s, int maks) {
        if (s == null) return "";
        if (s.length() <= maks) return s;
        return s.substring(0, maks) + "...";
    }

    // ------------------------------------------------------------------
    // Cakupan baris lewat instrumentasi sumber (nomor baris asli utuh).
    // ------------------------------------------------------------------

    /** Hasil instrumentasi satu berkas sumber. */
    public static class HasilInstrumentasi {
        /** Kode sumber sesudah disisipi pemanggil catat(). */
        public String kode = "";
        /** Nomor baris (1-based, sesuai sumber asli) yang bisa diukur. */
        public List<Integer> barisTerukur = new ArrayList<Integer>();
        /** Nomor baris berisi pernyataan yang tidak bisa disisipi aman. */
        public List<Integer> barisTakTerukur = new ArrayList<Integer>();
        public int totalBaris = 0;
    }

    /** Hasil hitung cakupan: baris terukur vs baris yang tereksekusi. */
    public static class HasilCakupan {
        public int terukur = 0;
        public int terkena = 0;
        public int persen = 0;
        public List<Integer> takTerkena = new ArrayList<Integer>();
    }

    /** Hitung persentase cakupan dari baris terukur dan baris yang kena. */
    public static HasilCakupan hitungCakupan(List<Integer> barisTerukur,
                                             Set<String> kena, String namaBerkas) {
        HasilCakupan h = new HasilCakupan();
        if (barisTerukur == null) return h;
        h.terukur = barisTerukur.size();
        if (h.terukur == 0) return h;
        for (int i = 0; i < barisTerukur.size(); i++) {
            int baris = barisTerukur.get(i);
            if (kena != null && kena.contains(namaBerkas + ":" + baris)) h.terkena++;
            else h.takTerkena.add(Integer.valueOf(baris));
        }
        h.persen = (h.terkena * 100) / h.terukur;
        return h;
    }

    /** Keadaan pemindai yang dibawa antar-baris (komentar blok). */
    static class KeadaanPindai {
        boolean dalamKomentarBlok = false;
    }

    /**
     * Bersihkan satu baris dari komentar dan isi string/karakter (diganti
     * spasi, panjang tetap) supaya '{', ';', dan kata kunci di dalamnya
     * tidak salah dibaca. Posisi tiap karakter dipertahankan.
     */
    static String bersihkanBaris(String baris, KeadaanPindai keadaan) {
        char[] c = baris.toCharArray();
        int n = c.length;
        boolean dalamString = false;
        boolean dalamKarakter = false;
        for (int i = 0; i < n; i++) {
            if (keadaan.dalamKomentarBlok) {
                if (c[i] == '*' && i + 1 < n && c[i + 1] == '/') {
                    c[i] = ' ';
                    c[i + 1] = ' ';
                    i++;
                    keadaan.dalamKomentarBlok = false;
                } else {
                    c[i] = ' ';
                }
                continue;
            }
            if (dalamString) {
                if (c[i] == '\\' && i + 1 < n) {
                    c[i] = ' ';
                    c[i + 1] = ' ';
                    i++;
                } else if (c[i] == '"') {
                    dalamString = false;
                } else {
                    c[i] = ' ';
                }
                continue;
            }
            if (dalamKarakter) {
                if (c[i] == '\\' && i + 1 < n) {
                    c[i] = ' ';
                    c[i + 1] = ' ';
                    i++;
                } else if (c[i] == '\'') {
                    dalamKarakter = false;
                } else {
                    c[i] = ' ';
                }
                continue;
            }
            if (c[i] == '"') {
                dalamString = true;
            } else if (c[i] == '\'') {
                dalamKarakter = true;
            } else if (c[i] == '/' && i + 1 < n && c[i + 1] == '*') {
                c[i] = ' ';
                c[i + 1] = ' ';
                i++;
                keadaan.dalamKomentarBlok = true;
            } else if (c[i] == '/' && i + 1 < n && c[i + 1] == '/') {
                for (int j = i; j < n; j++) c[j] = ' ';
                break;
            }
        }
        return new String(c);
    }

    static int hitungDalam(String bersih, char cari) {
        int n = 0;
        for (int i = 0; i < bersih.length(); i++) if (bersih.charAt(i) == cari) n++;
        return n;
    }

    static boolean mulaiDenganKata(String s, String kata) {
        if (s == null) return false;
        if (!s.startsWith(kata)) return false;
        if (s.length() == kata.length()) return true;
        char c = s.charAt(kata.length());
        return c == ' ' || c == '\t' || c == '(' || c == '{' || c == ';';
    }

    /**
     * Sisipi pemanggil catat() ke tiap baris pernyataan yang aman.
     * Aturan (konservatif — bila ragu, baris dilewati dan dilaporkan
     * sebagai tidak terukur, bukan ditebak):
     *  - Deklarasi metode/konstruktor dan baris "} else/catch/finally {":
     *    pemanggil disisip SETELAH '{' (masuk ke badan).
     *  - Label case/default: pemanggil disisip SETELAH ':'.
     *  - Baris pernyataan lain di dalam metode (baris sebelumnya
     *    mengakhiri pernyataan, kedalaman kurung 0): pemanggil disisip
     *    DI DEPAN baris yang sama — nomor baris asli tetap sama.
     *  - Baris lanjutan, deklarasi kelas, anotasi, komentar, dan baris
     *    "else/catch/finally/while;" yang berdiri sendiri: dilewati.
     * Pemanggil yang disisip: KelasUji.catat("namaberkas", nomorBaris);
     */
    public static HasilInstrumentasi instrumentasi(String kode, String namaBerkas,
                                                   String kelasUji) {
        HasilInstrumentasi hasil = new HasilInstrumentasi();
        if (kode == null) return hasil;
        String[] baris = kode.split("\n", -1);
        hasil.totalBaris = baris.length;
        StringBuilder keluar = new StringBuilder();
        KeadaanPindai keadaan = new KeadaanPindai();
        int dalamKurung = 0;
        int dalamKurawal = 0;
        List<Integer> tumpukanMetode = new ArrayList<Integer>();
        boolean sebelumAkhiriPernyataan = true;
        for (int nomor = 1; nomor <= baris.length; nomor++) {
            String asli = baris[nomor - 1];
            if (asli.endsWith("\r")) asli = asli.substring(0, asli.length() - 1);
            String bersih = bersihkanBaris(asli, keadaan);
            String rapih = bersih.trim();
            int kurungAwal = dalamKurung;
            int kurawalAwal = dalamKurawal;
            // Keluarkan metode yang sudah ditutup dari tumpukan.
            while (!tumpukanMetode.isEmpty()
                    && kurawalAwal < tumpukanMetode.get(tumpukanMetode.size() - 1).intValue()) {
                tumpukanMetode.remove(tumpukanMetode.size() - 1);
            }
            boolean dalamMetode = !tumpukanMetode.isEmpty();
            boolean kosong = rapih.length() == 0;
            boolean komentar = rapih.startsWith("*") || rapih.startsWith("/*")
                    || rapih.startsWith("//");
            boolean anotasi = rapih.startsWith("@");
            boolean paketAtauImpor = mulaiDenganKata(rapih, "package")
                    || mulaiDenganKata(rapih, "import");
            String barisBaru = asli;
            boolean terukur = false;
            boolean kodeBerarti = !kosong && !komentar && !anotasi && !paketAtauImpor;
            if (kodeBerarti) {
                String panggil = kelasUji + ".catat(\"" + namaBerkas + "\", " + nomor + ");";
                boolean akhirKurawal = rapih.endsWith("{");
                boolean akhirTitikKoma = rapih.endsWith(";");
                boolean akhirTitikDua = rapih.endsWith(":");
                boolean mulaiTutup = rapih.startsWith("}");
                boolean adalahCase = mulaiDenganKata(rapih, "case")
                        || "default:".equals(rapih) || rapih.startsWith("default:");
                boolean adalahDeklarasiMetode = false;
                if (akhirKurawal && kurungAwal == 0 && rapih.indexOf('(') >= 0
                        && rapih.indexOf(')') > rapih.indexOf('(')
                        && rapih.indexOf(')') < rapih.length() - 1
                        && !mulaiDenganKata(rapih, "if") && !mulaiDenganKata(rapih, "for")
                        && !mulaiDenganKata(rapih, "while") && !mulaiDenganKata(rapih, "switch")
                        && !mulaiDenganKata(rapih, "catch") && !mulaiDenganKata(rapih, "synchronized")
                        && !mulaiDenganKata(rapih, "try") && !mulaiDenganKata(rapih, "do")
                        && !mulaiDenganKata(rapih, "else") && !mulaiDenganKata(rapih, "static")
                        && !mulaiDenganKata(rapih, "class") && !mulaiDenganKata(rapih, "interface")
                        && !mulaiDenganKata(rapih, "enum") && !mulaiDenganKata(rapih, "new")
                        && !mulaiDenganKata(rapih, "return") && !mulaiDenganKata(rapih, "throw")
                        && rapih.indexOf("->") < 0 && rapih.indexOf("= {") < 0
                        && !Pattern.compile("\\bnew\\b").matcher(rapih).find()) {
                    adalahDeklarasiMetode = true;
                }
                boolean tutupLaluCabang = mulaiTutup && akhirKurawal
                        && (rapih.indexOf("else") >= 0 || rapih.indexOf("catch") >= 0
                        || rapih.indexOf("finally") >= 0);
                if (adalahDeklarasiMetode) {
                    int idx = bersih.lastIndexOf('{');
                    barisBaru = asli.substring(0, idx + 1) + " " + panggil
                            + asli.substring(idx + 1);
                    terukur = true;
                } else if (tutupLaluCabang) {
                    int idx = bersih.lastIndexOf('{');
                    barisBaru = asli.substring(0, idx + 1) + " " + panggil
                            + asli.substring(idx + 1);
                    terukur = true;
                } else if (adalahCase && akhirTitikDua) {
                    int idx = bersih.lastIndexOf(':');
                    barisBaru = asli.substring(0, idx + 1) + " " + panggil
                            + asli.substring(idx + 1);
                    terukur = true;
                } else if (mulaiDenganKata(rapih, "else")
                        || mulaiDenganKata(rapih, "catch")
                        || mulaiDenganKata(rapih, "finally")) {
                    // Cabang yang berdiri sendiri: sisipan di depannya akan
                    // memutus rangkaian if-else / try-catch.
                } else if (mulaiDenganKata(rapih, "while") && akhirTitikKoma
                        && !akhirKurawal) {
                    // Ekor do-while: sisipan di depannya memutus do-while.
                } else if (dalamMetode && kurungAwal == 0 && sebelumAkhiriPernyataan
                        && !mulaiTutup
                        && (akhirTitikKoma || akhirKurawal || "{".equals(rapih))) {
                    barisBaru = panggil + " " + asli;
                    terukur = true;
                }
                if (terukur) {
                    hasil.barisTerukur.add(Integer.valueOf(nomor));
                } else if (dalamMetode && !"}".equals(rapih) && !"};".equals(rapih)) {
                    // Baris penutup '}' murni bukan pernyataan — jangan
                    // dilaporkan sebagai tak terukur.
                    hasil.barisTakTerukur.add(Integer.valueOf(nomor));
                }
                // Perbarui penanda akhir-pernyataan dari baris berarti ini.
                if (akhirTitikKoma || akhirKurawal || rapih.endsWith("}")) {
                    sebelumAkhiriPernyataan = true;
                } else if (adalahCase && akhirTitikDua) {
                    sebelumAkhiriPernyataan = true;
                } else {
                    sebelumAkhiriPernyataan = false;
                }
                if (adalahDeklarasiMetode) {
                    int kurawalSesudah = kurawalAwal + hitungDalam(bersih, '{')
                            - hitungDalam(bersih, '}');
                    tumpukanMetode.add(Integer.valueOf(kurawalSesudah));
                }
            }
            keluar.append(barisBaru);
            if (nomor < baris.length) keluar.append('\n');
            dalamKurung += hitungDalam(bersih, '(') - hitungDalam(bersih, ')');
            dalamKurawal += hitungDalam(bersih, '{') - hitungDalam(bersih, '}');
            if (dalamKurung < 0) dalamKurung = 0;
            if (dalamKurawal < 0) dalamKurawal = 0;
        }
        hasil.kode = keluar.toString();
        return hasil;
    }

    /** Hasilkan teks sumber uji dari daftar kasus (tanpa paket). */
    public static String hasilkanSumberUji(String namaKelas, List<KasusUji> kasus) {
        return hasilkanSumberUji("", namaKelas, kasus);
    }

    /**
     * Hasilkan teks sumber uji JUnit-gaya yang BISA DIJALANKAN: tiap kasus
     * memanggil metode proyek sungguhan, memeriksa determinisme, dan
     * mencatat nilai aktualnya. Anotasi @Test dan asersi ditulis di
     * dalam berkas ini supaya ECJ bisa mengompilasinya tanpa JUnit.
     */
    public static String hasilkanSumberUji(String paket, String namaKelas,
                                           List<KasusUji> kasus) {
        StringBuilder b = new StringBuilder();
        if (paket != null && paket.trim().length() > 0) {
            b.append("package ").append(paket.trim()).append(";\n\n");
        }
        b.append("import java.lang.annotation.Retention;\n");
        b.append("import java.lang.annotation.RetentionPolicy;\n");
        b.append("import java.util.ArrayList;\n");
        b.append("import java.util.HashSet;\n");
        b.append("import java.util.List;\n");
        b.append("import java.util.Set;\n\n");
        b.append("/** Uji yang dihasilkan Pabrik Uji (Create Aplikasi).\n");
        b.append(" * Tiap uji memanggil metode proyek sungguhan dan memeriksa\n");
        b.append(" * determinisme: dua kali panggil dengan masukan yang sama\n");
        b.append(" * harus memberi hasil yang sama. Nilai aktual dicatat di\n");
        b.append(" * ambilHasilAktual() supaya bisa dikunci sebagai harapan.\n");
        b.append(" * Anotasi @Test dan asersi di bawah ini memakai API yang sama\n");
        b.append(" * dengan JUnit; bila proyek memakai JUnit asli, ganti dengan\n");
        b.append(" * org.junit.Test dan org.junit.Assert. */\n");
        b.append("public class Uji").append(namaKelas).append(" {\n");
        b.append("    @Retention(RetentionPolicy.RUNTIME)\n");
        b.append("    public @interface Test {\n    }\n\n");
        b.append("    private static final Set<String> cakupan = new HashSet<String>();\n");
        b.append("    public static void catat(String berkas, int baris) {\n");
        b.append("        cakupan.add(berkas + \":\" + baris);\n    }\n");
        b.append("    public static Set<String> ambilCakupan() {\n");
        b.append("        return new HashSet<String>(cakupan);\n    }\n");
        b.append("    public static void bersihCakupan() {\n        cakupan.clear();\n    }\n\n");
        b.append("    private static final List<String> hasilAktual = new ArrayList<String>();\n");
        b.append("    public static List<String> ambilHasilAktual() {\n");
        b.append("        return new ArrayList<String>(hasilAktual);\n    }\n\n");
        b.append("    public static void assertTrue(boolean k) {\n");
        b.append("        if (!k) throw new AssertionError(\"assertTrue gagal\");\n    }\n");
        b.append("    public static void assertTrue(String pesan, boolean k) {\n");
        b.append("        if (!k) throw new AssertionError(pesan);\n    }\n");
        b.append("    public static void assertFalse(boolean k) {\n");
        b.append("        if (k) throw new AssertionError(\"assertFalse gagal\");\n    }\n");
        b.append("    public static void assertEquals(Object a, Object b) {\n");
        b.append("        if (a == null ? b != null : !a.equals(b)) {\n");
        b.append("            throw new AssertionError(\"assertEquals gagal: \" + a + \" lawan \" + b);\n");
        b.append("        }\n    }\n");
        b.append("    public static void assertEquals(long a, long b) {\n");
        b.append("        if (a != b) {\n");
        b.append("            throw new AssertionError(\"assertEquals gagal: \" + a + \" lawan \" + b);\n");
        b.append("        }\n    }\n");
        b.append("    public static void assertEquals(double a, double b, double delta) {\n");
        b.append("        if (Double.isNaN(a) || Double.isNaN(b) || Math.abs(a - b) > delta) {\n");
        b.append("            throw new AssertionError(\"assertEquals gagal: \" + a + \" lawan \" + b);\n");
        b.append("        }\n    }\n");
        b.append("    public static void assertNull(Object o) {\n");
        b.append("        if (o != null) throw new AssertionError(\"assertNull gagal: \" + o);\n    }\n");
        b.append("    public static void assertNotNull(Object o) {\n");
        b.append("        if (o == null) throw new AssertionError(\"assertNotNull gagal\");\n    }\n");
        b.append("    public static void fail(String pesan) {\n");
        b.append("        throw new AssertionError(pesan);\n    }\n\n");
        if (kasus != null) {
            for (int i = 0; i < kasus.size(); i++) {
                KasusUji k = kasus.get(i);
                b.append("    @Test public void uji").append(i).append("() {\n");
                b.append("        // ").append(k.jenis).append(": ").append(k.nama)
                        .append("(").append(k.masukan).append(")\n");
                String panggil = (k.statis ? namaKelas + "." + k.nama
                        : "new " + namaKelas + "()." + k.nama)
                        + "(" + k.argumen + ")";
                String kembali = k.kembali == null ? "" : k.kembali.trim();
                if (kembali.length() == 0 || "void".equals(kembali)) {
                    b.append("        ").append(panggil).append(";\n");
                    b.append("        hasilAktual.add(\"uji").append(i)
                            .append(": selesai tanpa galat\");\n");
                } else if ("double".equals(kembali) || "float".equals(kembali)
                        || "Double".equals(kembali) || "Float".equals(kembali)) {
                    b.append("        ").append(kembali).append(" hasil1 = ")
                            .append(panggil).append(";\n");
                    b.append("        ").append(kembali).append(" hasil2 = ")
                            .append(panggil).append(";\n");
                    b.append("        assertTrue(\"hasil tidak deterministik\", ");
                    if ("double".equals(kembali) || "Double".equals(kembali)) {
                        b.append("Double.compare(hasil1, hasil2) == 0);\n");
                    } else {
                        b.append("Float.compare(hasil1, hasil2) == 0);\n");
                    }
                    b.append("        hasilAktual.add(\"uji").append(i)
                            .append(": \" + hasil1);\n");
                } else if ("int".equals(kembali) || "long".equals(kembali)
                        || "short".equals(kembali) || "byte".equals(kembali)
                        || "char".equals(kembali) || "boolean".equals(kembali)
                        || "Integer".equals(kembali) || "Long".equals(kembali)
                        || "Short".equals(kembali) || "Byte".equals(kembali)
                        || "Character".equals(kembali) || "Boolean".equals(kembali)) {
                    b.append("        ").append(kembali).append(" hasil1 = ")
                            .append(panggil).append(";\n");
                    b.append("        ").append(kembali).append(" hasil2 = ")
                            .append(panggil).append(";\n");
                    b.append("        assertEquals(hasil1, hasil2);\n");
                    b.append("        hasilAktual.add(\"uji").append(i)
                            .append(": \" + hasil1);\n");
                } else {
                    b.append("        ").append(kembali).append(" hasil1 = ")
                            .append(panggil).append(";\n");
                    b.append("        ").append(kembali).append(" hasil2 = ")
                            .append(panggil).append(";\n");
                    b.append("        assertTrue(\"hasil tidak deterministik\", ");
                    b.append("hasil1 == null ? hasil2 == null : hasil1.equals(hasil2));\n");
                    b.append("        hasilAktual.add(\"uji").append(i)
                            .append(": \" + hasil1);\n");
                }
                b.append("    }\n");
            }
        }
        b.append("}\n");
        return b.toString();
    }

    /** Hitung baris tak kosong dalam teks sumber (0 bila null). */
    public static int hitungBarisKode(String kode) {
        if (kode == null) return 0;
        int n = 0;
        String[] baris = kode.split("\n");
        for (int i = 0; i < baris.length; i++) {
            if (baris[i].trim().length() > 0) n++;
        }
        return n;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(int lulus, int gagal) {
        return "Uji lulus: " + lulus + ". Uji gagal: " + gagal + ".";
    }
}
