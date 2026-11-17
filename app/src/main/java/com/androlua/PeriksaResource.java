package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pemeriksa berkas resource proyek sebelum build (port Java dari
 * periksaResourceProyek + kumpulRes + KATA_KUNCI_JAVA di main.lua).
 *
 * Mengembalikan null bila OK, atau pesan berbahasa Indonesia yang menyebut
 * nama berkas + sebab + cara memperbaikinya (sama persis dengan Lua,
 * termasuk urutan pemeriksaan: (d) subfolder dulu untuk semua entri,
 * lalu (a)/(f)/(b)/(c)/(e) per berkas dalam urutan kumpulRes).
 *
 * Murni java.io/java.util (tanpa API Android) supaya bisa diuji ulang
 * di JRE biasa, seperti InfoProyek.
 */
public class PeriksaResource {

    /** Folder tipe resource yang tidak boleh berisi subfolder (aapt menolaknya). */
    private static final Set<String> TANPA_SUB = new HashSet<String>(Arrays.asList(
            "drawable", "layout", "mipmap", "raw", "xml", "values", "anim",
            "animator", "color", "font", "menu", "transition", "navigation",
            "interpolator"));

    /** Format gambar yang didukung aapt di drawable/ dan mipmap/. */
    private static final Set<String> GAMBAR_OK = new HashSet<String>(Arrays.asList(
            "png", "webp", "jpg", "jpeg", "gif"));

    /** Kata kunci Java (sama seperti KATA_KUNCI_JAVA di main.lua). */
    private static final Set<String> KATA_KUNCI_JAVA = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false",
            "null"));

    /** Satu entri hasil kumpul (berkas atau folder, jalur relatif dari res/). */
    private static class Entri {
        String rel;
        boolean dir;
        Entri(String rel, boolean dir) {
            this.rel = rel;
            this.dir = dir;
        }
    }

    /**
     * Periksa res/ proyek. Null bila OK; pesan kesalahan (Indonesia) bila
     * ada masalah — hanya masalah PERTAMA yang dikembalikan, seperti Lua.
     */
    public static String periksa(File dirProyek) {
        if (dirProyek == null) return null;
        File res = new File(dirProyek, "app/src/main/res");
        if (!res.exists()) return null;
        List<Entri> entri = new ArrayList<Entri>();
        kumpul(res, "", entri);
        // (d) tidak boleh ada subfolder di dalam folder tipe resource.
        for (int i = 0; i < entri.size(); i++) {
            Entri e = entri.get(i);
            if (e.dir && e.rel.indexOf('/') >= 0) {
                String induk = e.rel.substring(0, e.rel.indexOf('/'));
                String tipe = awalHuruf(induk);
                if (TANPA_SUB.contains(tipe)) {
                    return "Folder res/" + e.rel + " tidak boleh ada: di dalam res/" + induk
                            + " tidak boleh ada subfolder. Pindahkan berkasnya langsung ke res/" + induk
                            + "/, lalu bangun lagi.";
                }
            }
        }
        Map<String, String> lihat = new HashMap<String, String>();
        for (int i = 0; i < entri.size(); i++) {
            Entri e = entri.get(i);
            if (e.dir) continue;
            String rel = e.rel;
            int garis = rel.lastIndexOf('/');
            String folder = garis < 0 ? "" : rel.substring(0, garis);
            String nama = garis < 0 ? rel : rel.substring(garis + 1);
            if (folder.length() == 0) {
                return "Berkas res/" + nama + " berada langsung di folder res/. Berkas resource harus "
                        + "berada di dalam folder jenisnya (mis. res/drawable/ untuk gambar atau res/layout/ "
                        + "untuk layout). Pindahkan berkasnya ke folder yang sesuai, lalu bangun lagi.";
            }
            String tipeFolder = awalHuruf(folder);
            String dasar = dasarNama(nama);
            String ekst = ekstensi(nama);
            String ekstKecil = ekst == null ? null : ekst.toLowerCase();
            // (a) nama berkas, kecuali di values/ (nama berkas values tidak
            // jadi nama resource).
            if (!"values".equals(tipeFolder) && !namaSah(nama, ekst, ekstKecil)) {
                List<String> sebab = new ArrayList<String>();
                if (nama.matches(".*\\s.*")) sebab.add("memakai spasi");
                if (nama.matches(".*[A-Z].*")) sebab.add("memakai huruf besar");
                if (nama.indexOf('-') >= 0) sebab.add("memakai tanda hubung");
                if (nama.length() > 0 && Character.isDigit(nama.charAt(0))) sebab.add("diawali angka");
                if (ekst != null && !ekst.equals(ekstKecil)) sebab.add("ekstensinya memakai huruf besar");
                if (sebab.isEmpty()) sebab.add("memakai karakter yang tidak diizinkan");
                String saran = saranNama(nama, ekstKecil);
                return "Berkas res/" + rel + " " + gabung(sebab, ", ") + ". Nama berkas "
                        + "resource hanya boleh huruf kecil, angka, garis bawah, dan titik, diawali huruf "
                        + "atau garis bawah. Ganti namanya menjadi " + saran + ", lalu bangun lagi.";
            }
            // (f) nama berkas tidak boleh kata kunci Java.
            if (KATA_KUNCI_JAVA.contains(dasar)) {
                return "Berkas res/" + rel + " memakai nama \"" + dasar + "\" yang merupakan kata "
                        + "kunci Java, sehingga tidak bisa menjadi nama resource di R.java. Ganti namanya, "
                        + "lalu bangun lagi.";
            }
            // (b) di drawable/ dan mipmap/ hanya format gambar yang didukung aapt.
            if ("drawable".equals(tipeFolder) || "mipmap".equals(tipeFolder)) {
                boolean boleh = nama.endsWith(".9.png") || "xml".equals(ekstKecil)
                        || (ekstKecil != null && GAMBAR_OK.contains(ekstKecil));
                if (!boleh) {
                    if (ekst == null) {
                        return "Berkas res/" + rel + " tidak punya ekstensi. Gambar di res/" + tipeFolder
                                + "/ harus berekstensi .png, .webp, .jpg, .jpeg, .gif, .9.png, atau .xml. "
                                + "Tambahkan ekstensi yang sesuai, lalu bangun lagi.";
                    }
                    return "Berkas res/" + rel + " berekstensi ." + ekstKecil + ", yang tidak didukung "
                            + "sebagai resource Android. Gambar di res/" + tipeFolder + "/ hanya boleh .png, .webp, "
                            + ".jpg, .jpeg, .gif, .9.png, atau .xml (bukan .svg, .bmp, .ico, atau .tiff). "
                            + "Ubah berkasnya ke PNG atau WebP, lalu bangun lagi.";
                }
                // (c) gambar bukan 0 byte dan magic bytes cocok dengan ekstensinya.
                if (ekstKecil != null && GAMBAR_OK.contains(ekstKecil)) {
                    byte[] data = bacaBerkas(new File(res, rel));
                    if (data == null || data.length == 0) {
                        return "Berkas res/" + rel + " kosong atau tidak bisa dibaca. Hapus berkasnya atau "
                                + "ganti dengan gambar yang utuh, lalu bangun lagi.";
                    }
                    if (!cocokMagic(data, ekstKecil)) {
                        return "Berkas res/" + rel + " tampaknya rusak: isinya bukan gambar "
                                + ekstKecil.toUpperCase() + " yang valid. Ganti dengan berkas gambar yang utuh, lalu bangun lagi.";
                    }
                }
            }
            // (e) duplikat: nama dasar sama dengan ekstensi berbeda dalam
            // SATU folder yang sama ditolak; nama sama di folder berbeda boleh.
            String kunci = folder + "\0" + dasar;
            if (lihat.containsKey(kunci)) {
                return "Berkas res/" + rel + " dan res/" + lihat.get(kunci) + " memakai nama dasar yang sama "
                        + "(\"" + dasar + "\") dengan ekstensi berbeda di folder res/" + folder + "/ yang sama. "
                        + "Hapus atau ganti nama salah satunya, lalu bangun lagi.";
            }
            lihat.put(kunci, rel);
        }
        return null;
    }

    /** Kumpulkan semua entri (berkas dan folder) di bawah dir secara rekursif (sama seperti kumpulRes di Lua). */
    private static void kumpul(File dir, String prefix, List<Entri> out) {
        File jalan = prefix.length() == 0 ? dir : new File(dir, prefix);
        File[] daftar = jalan.listFiles();
        if (daftar == null) return;
        // Sama seperti listDir di Lua: urut abjad tanpa membedakan huruf besar/kecil.
        Arrays.sort(daftar, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                int c = a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
                if (c != 0) return c;
                return a.getName().compareTo(b.getName());
            }
        });
        for (int i = 0; i < daftar.length; i++) {
            String rel = prefix.length() == 0 ? daftar[i].getName() : prefix + "/" + daftar[i].getName();
            if (daftar[i].isDirectory()) {
                out.add(new Entri(rel, true));
                kumpul(dir, rel, out);
            } else {
                out.add(new Entri(rel, false));
            }
        }
    }

    /** Huruf kecil di awal teks (pola Lua ^([a-z]+)); "" bila tidak ada. */
    static String awalHuruf(String teks) {
        if (teks == null) return "";
        int i = 0;
        while (i < teks.length() && teks.charAt(i) >= 'a' && teks.charAt(i) <= 'z') i++;
        return teks.substring(0, i);
    }

    /** Nama dasar berkas: buang akhiran ".9.png" lalu buang ekstensi terakhir (sama seperti Lua). */
    static String dasarNama(String nama) {
        String d = nama;
        if (d.endsWith(".9.png")) d = d.substring(0, d.length() - ".9.png".length());
        int titik = d.lastIndexOf('.');
        if (titik >= 0) d = d.substring(0, titik);
        return d;
    }

    /** Ekstensi berkas (sesudah titik terakhir); null bila tidak ada titik. */
    static String ekstensi(String nama) {
        int titik = nama.lastIndexOf('.');
        if (titik < 0) return null;
        return nama.substring(titik + 1);
    }

    /** Nama berkas sah menurut aturan aapt (pola Lua ^[a-z_][a-z0-9_.]*$, tanpa "..", tanpa titik di akhir, ekstensi huruf kecil). */
    static boolean namaSah(String nama, String ekst, String ekstKecil) {
        if (!nama.matches("[a-z_][a-z0-9_.]*")) return false;
        if (nama.indexOf("..") >= 0) return false;
        if (nama.endsWith(".")) return false;
        if (ekst != null && !ekst.equals(ekstKecil)) return false;
        return true;
    }

    /** Saran nama pengganti (sama seperti Lua: huruf kecil, spasi/hubung jadi _, buang karakter lain, rapikan _). */
    static String saranNama(String nama, String ekstKecil) {
        String saran = nama.toLowerCase();
        saran = saran.replaceAll("[\\s-]+", "_");
        saran = saran.replaceAll("[^a-z0-9_.]", "");
        saran = saran.replaceAll("_+", "_");
        saran = saran.replaceAll("^_+", "");
        saran = saran.replaceAll("_+\\.", ".");
        if (saran.length() > 0 && Character.isDigit(saran.charAt(0))) saran = "_" + saran;
        if (saran.length() == 0 || ".".equals(saran)) {
            saran = "berkas" + (ekstKecil != null ? "." + ekstKecil : "");
        }
        return saran;
    }

    /** Magic bytes cocok dengan ekstensi gambar (PNG/JPG/WEBP/GIF, sama seperti Lua). */
    static boolean cocokMagic(byte[] data, String ekstKecil) {
        if ("png".equals(ekstKecil)) {
            return data.length >= 4 && (data[0] & 0xFF) == 0x89
                    && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        }
        if ("jpg".equals(ekstKecil) || "jpeg".equals(ekstKecil)) {
            return data.length >= 3 && (data[0] & 0xFF) == 0xFF
                    && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
        }
        if ("webp".equals(ekstKecil)) {
            return data.length >= 12 && data[0] == 'R' && data[1] == 'I'
                    && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
        }
        if ("gif".equals(ekstKecil)) {
            return data.length >= 4 && data[0] == 'G' && data[1] == 'I'
                    && data[2] == 'F' && data[3] == '8';
        }
        return false;
    }

    /** Baca seluruh isi berkas sebagai byte; null bila gagal. */
    static byte[] bacaBerkas(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] data = new byte[(int) f.length()];
                int dibaca = 0;
                while (dibaca < data.length) {
                    int n = in.read(data, dibaca, data.length - dibaca);
                    if (n < 0) break;
                    dibaca += n;
                }
                if (dibaca != data.length) {
                    byte[] pendek = new byte[dibaca];
                    System.arraycopy(data, 0, pendek, 0, dibaca);
                    return pendek;
                }
                return data;
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Gabung daftar dengan pemisah (table.concat). */
    static String gabung(List<String> daftar, String pemisah) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < daftar.size(); i++) {
            if (i > 0) sb.append(pemisah);
            sb.append(daftar.get(i));
        }
        return sb.toString();
    }

    /** Urutan entri untuk pengujian (tidak dipakai aplikasi). */
    static List<String> urutanEntri(File dirProyek) {
        List<String> hasil = new ArrayList<String>();
        File res = new File(dirProyek, "app/src/main/res");
        if (!res.exists()) return hasil;
        List<Entri> entri = new ArrayList<Entri>();
        kumpul(res, "", entri);
        for (int i = 0; i < entri.size(); i++) {
            hasil.add((entri.get(i).dir ? "D " : "F ") + entri.get(i).rel);
        }
        return hasil;
    }
}
