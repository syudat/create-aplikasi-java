package com.androlua;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Pabrik Modul Ekstensi Mandiri dan Penggabung Fitur Otomatis (Fitur 10).
 *
 * Mengemas layar, kelas utilitas, dependensi, string multibahasa, dan
 * izin tertentu dari satu proyek menjadi satu arsip ZIP mandiri
 * (modul.json + berkas-berkas dengan jalur relatif), lalu memasangnya
 * ke proyek lain tanpa menimpa kode yang sudah ada.
 *
 * Struktur ZIP modul:
 *   modul.json            metadata {nama, versi, berkas[],
 *                          stringDipakai[], izin[], stringEntri[]}
 *   <jalur relatif berkas, mis. app/src/main/java/.../Layar.java>
 *
 * stringDipakai dipindai dari kode: literal TeksUI.t("...") dan nama
 * R.string.*. stringEntri berisi pasangan [nama, nilai] yang bisa
 * digabung ke strings.xml proyek tujuan: entri R.string.* diambil
 * nilainya dari strings.xml proyek sumber; literal TeksUI.t("...")
 * diberi nama hasil "modul_teks_<n>". Izin dipindai dari kode
 * ("android.permission.X") dan dari AndroidManifest proyek sumber.
 *
 * Pasang TIDAK PERNAH menimpa: berkas yang sudah ada di proyek tujuan
 * dilewati dan dihitung; entri string dan izin yang sudah ada juga
 * dilewati (tidak duplikat).
 *
 * Murni Java (java.io/java.util/java.util.zip, tanpa Android) supaya
 * bisa diuji di JVM. PabrikModulActivity hanya memilih proyek/berkas
 * dan menampilkan ringkasannya.
 */
public class PabrikModul {

    /** Nama berkas metadata di dalam ZIP modul. */
    public static final String NAMA_METADATA = "modul.json";

    /** Folder modul di samping folder proyek (Download/project/modul). */
    public static final String FOLDER_MODUL = "modul";

    /** Metadata modul (dibaca dari / ditulis ke modul.json). */
    public static class Metadata {
        public String nama;
        public String versi;
        public final List<String> berkas = new ArrayList<String>();
        public final List<String> stringDipakai = new ArrayList<String>();
        public final List<String> izin = new ArrayList<String>();
        /** Pasangan {nama-string, nilai} yang bisa digabung ke strings.xml. */
        public final List<String[]> stringEntri = new ArrayList<String[]>();
    }

    /** Hasil pemindaian ketergantungan satu berkas kode. */
    public static class Ketergantungan {
        public final List<String> stringDipakai = new ArrayList<String>();
        public final List<String> izinDipakai = new ArrayList<String>();
    }

    /** Hasil pemasangan modul ke proyek tujuan. */
    public static class Hasil {
        public int disalin;
        public int dilewati;
        public int stringDigabung;
        public int izinDigabung;
        public String galat;
    }

    // ------------------------------------------------------------------ pindai

    private static final Pattern POLA_TEKS_UI =
            Pattern.compile("TeksUI\\.t\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern POLA_R_STRING =
            Pattern.compile("R\\.string\\.([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern POLA_IZIN_KODE =
            Pattern.compile("android\\.permission\\.([A-Z][A-Z0-9_]*)");
    private static final Pattern POLA_IZIN_MANIFEST = Pattern.compile(
            "<uses-permission[^>]*android:name=\"android\\.permission\\.([A-Z][A-Z0-9_]*)\"");

    /**
     * Memindai isi satu berkas Java/Kotlin: string yang dipakai
     * (literal TeksUI.t("...") dan nama R.string.*) dan izin yang
     * dipakai (pola "android.permission.X"). Urutan kemunculan
     * dipertahankan, duplikat dibuang.
     */
    public static Ketergantungan pindaiKetergantungan(String isiJava) {
        Ketergantungan k = new Ketergantungan();
        if (isiJava == null) return k;
        LinkedHashSet<String> strings = new LinkedHashSet<String>();
        Matcher m = POLA_TEKS_UI.matcher(isiJava);
        while (m.find()) {
            String teks = bukaLolosJava(m.group(1));
            if (teks.length() > 0) strings.add(teks);
        }
        Matcher r = POLA_R_STRING.matcher(isiJava);
        while (r.find()) strings.add(r.group(1));
        k.stringDipakai.addAll(strings);
        LinkedHashSet<String> izin = new LinkedHashSet<String>();
        Matcher p = POLA_IZIN_KODE.matcher(isiJava);
        while (p.find()) izin.add(p.group(1));
        k.izinDipakai.addAll(izin);
        return k;
    }

    /** Memindai izin dari isi AndroidManifest.xml (uses-permission). */
    public static List<String> pindaiIzinManifest(String isiManifest) {
        LinkedHashSet<String> izin = new LinkedHashSet<String>();
        if (isiManifest == null) return new ArrayList<String>(izin);
        Matcher m = POLA_IZIN_MANIFEST.matcher(isiManifest);
        while (m.find()) izin.add(m.group(1));
        return new ArrayList<String>(izin);
    }

    // ------------------------------------------------------------------ kemas

    /**
     * Mengemas berkas-berkas terpilih dari dirSumber menjadi ZIP modul
     * di folder "modul" di samping folder proyek (induk dirSumber).
     * Mengembalikan berkas ZIP bila berhasil, null bila gagal (nama
     * modul/versi tidak sah, tidak ada berkas, atau berkas sumber
     * tidak ditemukan / keluar dari proyek).
     */
    public static File kemasModul(File dirSumber, List<String> berkasDipilih,
                                  String namaModul, String versi) {
        if (dirSumber == null || !dirSumber.isDirectory()) return null;
        if (!namaModulSah(namaModul) || versi == null || versi.trim().length() == 0) return null;
        if (berkasDipilih == null || berkasDipilih.isEmpty()) return null;
        List<String> jalurBersih = new ArrayList<String>();
        for (String b : berkasDipilih) {
            String jalur = jalurRelatifAman(dirSumber, b);
            if (jalur == null) return null;
            if (!jalurBersih.contains(jalur)) jalurBersih.add(jalur);
        }
        Metadata meta = new Metadata();
        meta.nama = namaModul;
        meta.versi = versi.trim();
        meta.berkas.addAll(jalurBersih);
        // Pindai ketergantungan dari berkas kode terpilih + manifest sumber.
        LinkedHashSet<String> strings = new LinkedHashSet<String>();
        LinkedHashSet<String> izin = new LinkedHashSet<String>();
        for (String jalur : jalurBersih) {
            File f = new File(dirSumber, jalur);
            if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                Ketergantungan k = pindaiKetergantungan(SumberDaya.teks(f));
                strings.addAll(k.stringDipakai);
                izin.addAll(k.izinDipakai);
            }
        }
        File manifestSumber = cariManifest(dirSumber);
        if (manifestSumber != null) {
            izin.addAll(pindaiIzinManifest(SumberDaya.teks(manifestSumber)));
        }
        meta.stringDipakai.addAll(strings);
        meta.izin.addAll(izin);
        // Entri string yang bisa digabung: R.string.* dari strings.xml
        // sumber; literal TeksUI.t("...") diberi nama modul_teks_<n>.
        List<String[]> entriSumber = daftarStringSumber(dirSumber);
        int nomorTeks = 1;
        for (String s : strings) {
            String nilai = nilaiString(entriSumber, s);
            if (nilai != null) {
                meta.stringEntri.add(new String[]{s, nilai});
            } else if (!s.matches("[a-z][a-z0-9_]*")) {
                meta.stringEntri.add(new String[]{"modul_teks_" + nomorTeks, s});
                nomorTeks++;
            }
            // Nama R.string.* yang tidak ada di strings.xml sumber
            // dilewati: nilainya tidak diketahui, jangan mengarang.
        }
        File dirModul = dirSumber.getParentFile() == null ? null
                : new File(dirSumber.getParentFile(), FOLDER_MODUL);
        if (dirModul == null) return null;
        dirModul.mkdirs();
        File keluar = new File(dirModul, namaModul + "-" + meta.versi + ".zip");
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(
                    new FileOutputStream(keluar)));
            ZipEntry e = new ZipEntry(NAMA_METADATA);
            zos.putNextEntry(e);
            zos.write(tulisMetadata(meta).getBytes("UTF-8"));
            zos.closeEntry();
            byte[] buf = new byte[16384];
            for (String jalur : jalurBersih) {
                File f = new File(dirSumber, jalur);
                zos.putNextEntry(new ZipEntry(jalur.replace('\\', '/')));
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                } finally {
                    in.close();
                }
                zos.closeEntry();
            }
            zos.finish();
            return keluar;
        } catch (IOException e) {
            if (keluar.exists()) keluar.delete();
            return null;
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Nama modul: huruf, angka, garis bawah, titik, strip; tanpa spasi/jalur. */
    static boolean namaModulSah(String nama) {
        return nama != null && nama.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*");
    }

    /**
     * Mengembalikan jalur relatif yang aman (pakai '/' ) bila berkas ada
     * di dalam dirSumber; null bila jalur absolut, mengandung "..",
     * atau berkasnya tidak ada / bukan berkas.
     */
    static String jalurRelatifAman(File dirSumber, String jalur) {
        if (jalur == null || jalur.length() == 0) return null;
        if (new File(jalur).isAbsolute()) return null;
        String bersih = jalur.replace('\\', '/');
        if (bersih.startsWith("/") || bersih.contains("..")) return null;
        while (bersih.startsWith("./")) bersih = bersih.substring(2);
        File f = new File(dirSumber, bersih);
        try {
            File kanonSumber = dirSumber.getCanonicalFile();
            File kanon = f.getCanonicalFile();
            if (!kanon.getPath().startsWith(kanonSumber.getPath() + File.separator)) return null;
        } catch (IOException e) {
            return null;
        }
        if (!f.isFile()) return null;
        return bersih;
    }

    // ------------------------------------------------------------------ metadata JSON

    /** Metadata -> JSON (ditulis manual, tanpa pustaka luar). */
    static String tulisMetadata(Metadata meta) {
        StringBuilder b = new StringBuilder();
        b.append("{\"nama\":").append(keJson(meta.nama));
        b.append(",\"versi\":").append(keJson(meta.versi));
        b.append(",\"berkas\":").append(daftarKeJson(meta.berkas));
        b.append(",\"stringDipakai\":").append(daftarKeJson(meta.stringDipakai));
        b.append(",\"izin\":").append(daftarKeJson(meta.izin));
        b.append(",\"stringEntri\":[");
        for (int i = 0; i < meta.stringEntri.size(); i++) {
            if (i > 0) b.append(",");
            b.append("[").append(keJson(meta.stringEntri.get(i)[0]));
            b.append(",").append(keJson(meta.stringEntri.get(i)[1])).append("]");
        }
        b.append("]}");
        return b.toString();
    }

    private static String daftarKeJson(List<String> daftar) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < daftar.size(); i++) {
            if (i > 0) b.append(",");
            b.append(keJson(daftar.get(i)));
        }
        b.append("]");
        return b.toString();
    }

    /** Loloskan string untuk JSON (" \\ dan karakter kendali). */
    static String keJson(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        b.append("\"");
        return b.toString();
    }

    /** Membaca metadata dari ZIP modul; null bila ZIP rusak / tanpa modul.json. */
    public static Metadata bacaMetadata(File zipModul) {
        if (zipModul == null || !zipModul.isFile()) return null;
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipModul);
            ZipEntry e = zf.getEntry(NAMA_METADATA);
            if (e == null) return null;
            String json = bacaAliran(zf.getInputStream(e));
            if (json == null) return null;
            return uraiMetadata(json);
        } catch (IOException ignored) {
            return null;
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Mengurai JSON metadata; null bila nama/versi tidak ada. */
    static Metadata uraiMetadata(String json) {
        if (json == null) return null;
        Metadata meta = new Metadata();
        meta.nama = ambilString(json, "nama");
        meta.versi = ambilString(json, "versi");
        if (meta.nama == null || meta.versi == null) return null;
        meta.berkas.addAll(ambilDaftar(json, "berkas"));
        meta.stringDipakai.addAll(ambilDaftar(json, "stringDipakai"));
        meta.izin.addAll(ambilDaftar(json, "izin"));
        meta.stringEntri.addAll(ambilPasangan(json, "stringEntri"));
        return meta;
    }

    /** Nilai string untuk kunci "kunci":"nilai"; null bila tidak ada. */
    static String ambilString(String json, String kunci) {
        int i = json.indexOf("\"" + kunci + "\"");
        if (i < 0) return null;
        int titik = json.indexOf(':', i + kunci.length() + 2);
        if (titik < 0) return null;
        int awal = titik + 1;
        while (awal < json.length() && Character.isWhitespace(json.charAt(awal))) awal++;
        if (awal >= json.length() || json.charAt(awal) != '"') return null;
        return bacaStringJson(json, awal);
    }

    /** Daftar string untuk kunci "kunci":["a","b"]; kosong bila tidak ada. */
    static List<String> ambilDaftar(String json, String kunci) {
        List<String> hasil = new ArrayList<String>();
        int i = json.indexOf("\"" + kunci + "\"");
        if (i < 0) return hasil;
        int buka = json.indexOf('[', i + kunci.length() + 2);
        if (buka < 0) return hasil;
        int tutup = tutupKurung(json, buka);
        if (tutup < 0) return hasil;
        int p = buka + 1;
        while (p < tutup) {
            char c = json.charAt(p);
            if (c == '"') {
                String s = bacaStringJson(json, p);
                if (s == null) break;
                hasil.add(s);
                p = sesudahStringJson(json, p);
            } else {
                p++;
            }
        }
        return hasil;
    }

    /** Pasangan [nama, nilai] untuk kunci "kunci":[["a","b"],...]. */
    static List<String[]> ambilPasangan(String json, String kunci) {
        List<String[]> hasil = new ArrayList<String[]>();
        List<String> datar = ambilDaftar(json, kunci);
        for (int i = 0; i + 1 < datar.size(); i += 2) {
            hasil.add(new String[]{datar.get(i), datar.get(i + 1)});
        }
        return hasil;
    }

    /** Indeks ']' yang menutup '[' di posisi buka (memperhatikan string). */
    private static int tutupKurung(String json, int buka) {
        int dalam = 0;
        boolean diString = false;
        for (int i = buka; i < json.length(); i++) {
            char c = json.charAt(i);
            if (diString) {
                if (c == '\\') i++;
                else if (c == '"') diString = false;
            } else if (c == '"') {
                diString = true;
            } else if (c == '[') {
                dalam++;
            } else if (c == ']') {
                dalam--;
                if (dalam == 0) return i;
            }
        }
        return -1;
    }

    /** Membaca string JSON mulai dari tanda petik di posisi awal. */
    private static String bacaStringJson(String json, int awal) {
        StringBuilder b = new StringBuilder();
        for (int i = awal + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return b.toString();
            if (c == '\\' && i + 1 < json.length()) {
                char d = json.charAt(i + 1);
                if (d == '"') b.append('"');
                else if (d == '\\') b.append('\\');
                else if (d == '/') b.append('/');
                else if (d == 'n') b.append('\n');
                else if (d == 'r') b.append('\r');
                else if (d == 't') b.append('\t');
                else if (d == 'u' && i + 5 < json.length()) {
                    try {
                        b.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                    i += 4;
                } else {
                    return null;
                }
                i++;
            } else {
                b.append(c);
            }
        }
        return null;
    }

    /** Indeks sesudah string JSON yang mulai di posisi awal. */
    private static int sesudahStringJson(String json, int awal) {
        for (int i = awal + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') i++;
            else if (c == '"') return i + 1;
        }
        return json.length();
    }

    /** Membuka lolos Java di dalam literal string ("\\n" -> baris baru). */
    private static String bukaLolosJava(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(i + 1);
                if (d == 'n') b.append('\n');
                else if (d == 'r') b.append('\r');
                else if (d == 't') b.append('\t');
                else if (d == '"') b.append('"');
                else if (d == '\\') b.append('\\');
                else b.append(d);
                i++;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ pasang

    /**
     * Memasang modul ke proyek tujuan. Berkas disalin dengan jalur
     * relatifnya; berkas yang sudah ada DILEWATI (tidak ditimpa).
     * Entri string digabung ke strings.xml tujuan dan izin digabung
     * ke AndroidManifest tujuan (yang sudah ada tidak diduplikat).
     * Hasil.galat terisi bila ZIP bukan modul atau tujuan tidak sah.
     */
    public static Hasil pasangModul(File zipModul, File dirProyekTujuan) {
        Hasil hasil = new Hasil();
        if (zipModul == null || !zipModul.isFile()) {
            hasil.galat = TeksUI.t("Berkas modul tidak ditemukan.");
            return hasil;
        }
        if (dirProyekTujuan == null || !dirProyekTujuan.isDirectory()) {
            hasil.galat = TeksUI.t("Folder proyek tujuan tidak ditemukan.");
            return hasil;
        }
        Metadata meta = bacaMetadata(zipModul);
        if (meta == null) {
            hasil.galat = TeksUI.t("Berkas ini bukan modul (tidak ada modul.json di dalamnya).");
            return hasil;
        }
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipModul)));
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zin.getNextEntry()) != null) {
                String nama = e.getName().replace('\\', '/');
                if (NAMA_METADATA.equals(nama) || e.isDirectory()) continue;
                if (nama.startsWith("/") || nama.contains("..")) continue;
                File tujuan = new File(dirProyekTujuan, nama);
                try {
                    File kanonTujuan = dirProyekTujuan.getCanonicalFile();
                    File kanon = tujuan.getCanonicalFile();
                    if (!kanon.getPath().startsWith(kanonTujuan.getPath() + File.separator)) {
                        hasil.dilewati++;
                        continue;
                    }
                } catch (IOException ignored) {
                    hasil.dilewati++;
                    continue;
                }
                if (tujuan.exists()) {
                    hasil.dilewati++;
                    continue;
                }
                try {
                    if (tujuan.getParentFile() != null) tujuan.getParentFile().mkdirs();
                    BufferedOutputStream out = new BufferedOutputStream(
                            new FileOutputStream(tujuan));
                    try {
                        int n;
                        while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                    } finally {
                        out.close();
                    }
                    hasil.disalin++;
                } catch (IOException ignored) {
                    hasil.dilewati++;
                }
            }
        } catch (IOException e) {
            if (hasil.galat == null) hasil.galat = TeksUI.t("Gagal membaca modul: ") + e.getMessage();
            return hasil;
        } finally {
            if (zin != null) {
                try {
                    zin.close();
                } catch (IOException ignored) {
                }
            }
        }
        hasil.stringDigabung = gabungString(dirProyekTujuan, meta.stringEntri);
        hasil.izinDigabung = gabungIzin(dirProyekTujuan, meta.izin);
        return hasil;
    }

    /** Menggabungkan entri string ke strings.xml tujuan; mengembalikan jumlah yang ditambah. */
    static int gabungString(File dirProyek, List<String[]> entri) {
        if (entri == null || entri.isEmpty()) return 0;
        File values = SumberDaya.folderValues(dirProyek);
        File stringsXml = new File(values, "strings.xml");
        String isi = SumberDaya.teks(stringsXml);
        int tambah = 0;
        for (String[] e : entri) {
            if (e == null || e.length < 2) continue;
            try {
                isi = SumberDaya.tambahString(isi, e[0], e[1]);
                tambah++;
            } catch (IllegalArgumentException ignored) {
                // Nama tidak sah atau sudah ada: lewati, jangan duplikat.
            }
        }
        if (tambah > 0) SumberDaya.tulis(stringsXml, isi);
        return tambah;
    }

    /** Menggabungkan izin ke AndroidManifest tujuan; jumlah yang ditambah. */
    static int gabungIzin(File dirProyek, List<String> izin) {
        if (izin == null || izin.isEmpty()) return 0;
        File manifest = cariManifest(dirProyek);
        if (manifest == null) return 0;
        String isi = SumberDaya.teks(manifest);
        if (isi == null || isi.indexOf("</manifest>") < 0) return 0;
        int tambah = 0;
        for (String izinNama : izin) {
            String lengkap = "android.permission." + izinNama;
            if (isi.contains(lengkap)) continue;
            String baris = "    <uses-permission android:name=\"" + lengkap + "\" />\n";
            int i = isi.lastIndexOf("</manifest>");
            isi = isi.substring(0, i) + baris + isi.substring(i);
            tambah++;
        }
        if (tambah > 0) SumberDaya.tulis(manifest, isi);
        return tambah;
    }

    /** AndroidManifest.xml proyek: app/src/main/ didahulukan, lalu akar proyek. */
    static File cariManifest(File dirProyek) {
        File a = new File(dirProyek, "app/src/main/AndroidManifest.xml");
        if (a.isFile()) return a;
        File b = new File(dirProyek, "AndroidManifest.xml");
        if (b.isFile()) return b;
        return null;
    }

    /** Semua entri strings.xml proyek sumber (nama + nilai). */
    private static List<String[]> daftarStringSumber(File dirSumber) {
        List<String[]> hasil = new ArrayList<String[]>();
        File values = SumberDaya.folderValues(dirSumber);
        File stringsXml = new File(values, "strings.xml");
        String isi = SumberDaya.teks(stringsXml);
        if (isi != null) hasil.addAll(SumberDaya.daftarString(isi));
        return hasil;
    }

    private static String nilaiString(List<String[]> entri, String nama) {
        for (String[] e : entri) {
            if (e[0].equals(nama)) return e[1];
        }
        return null;
    }

    private static String bacaAliran(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), "UTF-8");
    }

    /** Ringkasan hasil pasang untuk ditampilkan di layar. */
    public static String ringkasan(Hasil hasil, String namaModul) {
        StringBuilder b = new StringBuilder();
        if (hasil.galat != null) {
            b.append(hasil.galat).append('\n');
            return b.toString();
        }
        b.append(TeksUI.t("Modul \"")).append(namaModul).append(TeksUI.t("\" dipasang:") + "\n");
        b.append(hasil.disalin).append(TeksUI.t(" berkas disalin, "));
        b.append(hasil.dilewati).append(TeksUI.t(" berkas dilewati (sudah ada).") + "\n");
        b.append(hasil.stringDigabung).append(TeksUI.t(" string digabung, "));
        b.append(hasil.izinDigabung).append(TeksUI.t(" izin digabung."));
        return b.toString();
    }
}
