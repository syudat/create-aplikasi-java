package com.androlua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pembaca paket bundel APK: .apks (split APK, mis. dari SAI),
 * .xapk (APKPure: APK + manifest.json + OBB), .apkm (APKMirror).
 *
 * Semua format itu adalah ZIP berisi satu atau beberapa berkas .apk
 * (base + split) dan, untuk .xapk, kadang berkas .obb. Kelas ini murni
 * Java (tanpa API Android) supaya bisa diuji di komputer: membuka ZIP,
 * mengeluarkan isinya ke folder aman (menolak entri dengan ".." atau
 * jalur absolut), mengurutkan bagian (base.apk dulu, lalu sesuai
 * daftar split_apks di manifest.json bila ada), dan mengumpulkan OBB.
 *
 * Pemasangan semua bagian dilakukan dalam SATU sesi (PackageInstaller
 * dengan beberapa openWrite, atau "pm install-create/write/commit"
 * lewat adb) supaya hasilnya satu aplikasi utuh, bukan bagian yang
 * terpisah.
 */
public class PembacaBundel {

    /** Satu berkas OBB di dalam bundel. */
    public static class Obb {
        /** Nama berkas OBB (mis. main.123.com.contoh.obb). */
        public String nama = "";
        /** Berkas hasil ekstrak. */
        public File berkas;
    }

    /** Hasil pembacaan sebuah bundel. */
    public static class Bundel {
        /** Berkas APK bagian, base dulu. */
        public List<File> apk = new ArrayList<File>();
        /** Berkas OBB (bisa kosong). */
        public List<Obb> obb = new ArrayList<Obb>();
        /** Nama paket dari manifest.json (.xapk), "" bila tidak ada. */
        public String paketDariManifest = "";
        /** Jumlah seluruh bita APK bagian. */
        public long ukuranTotal = 0;
    }

    /** Ekstensi bundel yang dikenali (huruf kecil, termasuk titik). */
    public static boolean adalahBundel(String namaBerkas) {
        if (namaBerkas == null) return false;
        String n = namaBerkas.toLowerCase(java.util.Locale.US);
        return n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm");
    }

    /** Ambil id sesi dari "Success: created install session [123456]". */
    public static long parseIdSesi(String keluaran) {
        if (keluaran == null) return -1;
        int buka = keluaran.indexOf('[');
        int tutup = keluaran.indexOf(']', buka + 1);
        if (buka < 0 || tutup < 0) return -1;
        try {
            return Long.parseLong(keluaran.substring(buka + 1, tutup).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Nama format untuk ditampilkan ("APKS", "XAPK", "APKM"). */
    public static String formatDariNama(String namaBerkas) {
        if (namaBerkas == null) return "";
        String n = namaBerkas.toLowerCase(java.util.Locale.US);
        if (n.endsWith(".apks")) return "APKS";
        if (n.endsWith(".xapk")) return "XAPK";
        if (n.endsWith(".apkm")) return "APKM";
        return "";
    }

    /**
     * Baca sebuah berkas bundel (ZIP) dan ekstrak isinya ke dirKeluar
     * (dibuat bila belum ada; isi lama dihapus dulu). Mengembalikan
     * daftar bagian APK (base dulu) dan OBB.
     */
    public static Bundel baca(File zip, File dirKeluar) throws IOException {
        if (zip == null || !zip.exists()) {
            throw new IOException("Berkas bundel tidak ditemukan.");
        }
        if (dirKeluar.exists()) {
            hapusRekursif(dirKeluar);
        }
        dirKeluar.mkdirs();
        Bundel hasil = new Bundel();
        List<String> namaApk = new ArrayList<String>();
        String manifestJson = null;
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry entri;
            while ((entri = zin.getNextEntry()) != null) {
                String nama = entri.getName();
                if (entri.isDirectory() || nama == null || nama.length() == 0) {
                    continue;
                }
                String namaBawah = nama.toLowerCase(java.util.Locale.US);
                boolean apk = namaBawah.endsWith(".apk");
                boolean obb = namaBawah.endsWith(".obb");
                boolean manifest = namaBawah.equals("manifest.json")
                        || namaBawah.endsWith("/manifest.json");
                if (!apk && !obb && !manifest) {
                    continue;
                }
                String namaAman = namaAman(nama);
                File keluar = new File(dirKeluar, namaAman);
                // Tolak traversal: hasil harus tetap di dalam dirKeluar.
                if (!keluar.getCanonicalPath().startsWith(
                        dirKeluar.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entri bundel tidak aman: " + nama);
                }
                File induk = keluar.getParentFile();
                if (induk != null) induk.mkdirs();
                FileOutputStream fout = new FileOutputStream(keluar);
                try {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = zin.read(buf)) >= 0) fout.write(buf, 0, n);
                } finally {
                    fout.close();
                }
                if (apk) {
                    namaApk.add(namaAman);
                } else if (obb) {
                    Obb o = new Obb();
                    o.nama = namaAman;
                    o.berkas = keluar;
                    hasil.obb.add(o);
                } else if (manifest) {
                    manifestJson = bacaTeks(keluar);
                }
            }
        } finally {
            zin.close();
        }
        if (namaApk.isEmpty()) {
            throw new IOException("Bundel tidak berisi berkas APK.");
        }
        if (manifestJson != null) {
            hasil.paketDariManifest = ambilStringJson(manifestJson, "package_name");
            List<String> urutan = urutanSplit(manifestJson);
            if (!urutan.isEmpty()) {
                namaApk = urutkanSesuaiManifest(namaApk, urutan);
            }
        }
        if (manifestJson == null || urutanSplitKosong(manifestJson)) {
            namaApk = urutkanBawaan(namaApk);
        }
        for (int i = 0; i < namaApk.size(); i++) {
            File f = new File(dirKeluar, namaApk.get(i));
            hasil.apk.add(f);
            hasil.ukuranTotal += f.length();
        }
        return hasil;
    }

    /** base.apk dulu, lalu sisanya menurut abjad. */
    static List<String> urutkanBawaan(List<String> nama) {
        List<String> salin = new ArrayList<String>(nama);
        Collections.sort(salin, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean aBase = a.toLowerCase(java.util.Locale.US).endsWith("base.apk");
                boolean bBase = b.toLowerCase(java.util.Locale.US).endsWith("base.apk");
                if (aBase && !bBase) return -1;
                if (!aBase && bBase) return 1;
                return a.compareToIgnoreCase(b);
            }
        });
        return salin;
    }

    /** Urutkan mengikuti daftar "file" di split_apks; yang tak ada di daftar tetap di belakang. */
    static List<String> urutkanSesuaiManifest(List<String> nama, List<String> urutan) {
        List<String> hasil = new ArrayList<String>();
        List<String> sisa = new ArrayList<String>(nama);
        for (int i = 0; i < urutan.size(); i++) {
            String dicari = urutan.get(i);
            for (int j = 0; j < sisa.size(); j++) {
                String s = sisa.get(j);
                if (s.equals(dicari) || s.endsWith("/" + dicari)
                        || namaBawah(s).endsWith("/" + dicari.toLowerCase(java.util.Locale.US))) {
                    hasil.add(s);
                    sisa.remove(j);
                    break;
                }
            }
        }
        hasil.addAll(urutkanBawaan(sisa));
        return hasil;
    }

    static boolean urutanSplitKosong(String json) {
        return urutanSplit(json).isEmpty();
    }

    /** Daftar nilai "file" di dalam larik split_apks pada manifest.json. */
    static List<String> urutanSplit(String json) {
        List<String> hasil = new ArrayList<String>();
        if (json == null) return hasil;
        int p = json.indexOf("\"split_apks\"");
        if (p < 0) return hasil;
        int awal = json.indexOf('[', p);
        int akhir = json.indexOf(']', awal);
        if (awal < 0 || akhir < 0 || akhir < awal) return hasil;
        String larik = json.substring(awal, akhir);
        int cari = 0;
        while (true) {
            int k = larik.indexOf("\"file\"", cari);
            if (k < 0) break;
            int titik = larik.indexOf(':', k);
            if (titik < 0) break;
            String nilai = ambilStringSesudah(larik, titik + 1);
            if (nilai == null) break;
            hasil.add(nilai);
            cari = titik + 1;
        }
        return hasil;
    }

    /** Nilai string untuk kunci "kunci": "..." di JSON sederhana. */
    static String ambilStringJson(String json, String kunci) {
        if (json == null) return "";
        int p = json.indexOf("\"" + kunci + "\"");
        if (p < 0) return "";
        int titik = json.indexOf(':', p + kunci.length() + 2);
        if (titik < 0) return "";
        String nilai = ambilStringSesudah(json, titik + 1);
        return nilai == null ? "" : nilai;
    }

    /** Baca nilai string JSON ("...") mulai dari posisi, tanpa pustaka JSON. */
    static String ambilStringSesudah(String teks, int dari) {
        int i = dari;
        while (i < teks.length() && (teks.charAt(i) == ' ' || teks.charAt(i) == '\t'
                || teks.charAt(i) == '\n' || teks.charAt(i) == '\r')) {
            i++;
        }
        if (i >= teks.length() || teks.charAt(i) != '"') return null;
        StringBuilder b = new StringBuilder();
        i++;
        while (i < teks.length()) {
            char c = teks.charAt(i);
            if (c == '\\' && i + 1 < teks.length()) {
                char n = teks.charAt(i + 1);
                if (n == 'n') b.append('\n');
                else if (n == 't') b.append('\t');
                else if (n == 'r') b.append('\r');
                else b.append(n);
                i += 2;
            } else if (c == '"') {
                return b.toString();
            } else {
                b.append(c);
                i++;
            }
        }
        return null;
    }

    static String bacaTeks(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    /** Nama entri ZIP yang aman dipakai sebagai nama berkas (tanpa "../"). */
    static String namaAman(String nama) {
        String n = nama.replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        while (n.contains("../")) n = n.replace("../", "");
        while (n.contains("..\\")) n = n.replace("..\\", "");
        int miring = n.lastIndexOf('/');
        return miring >= 0 ? n.substring(miring + 1) : n;
    }

    static String namaBawah(String s) {
        return s.toLowerCase(java.util.Locale.US);
    }

    static void hapusRekursif(File f) {
        if (f.isDirectory()) {
            File[] isi = f.listFiles();
            if (isi != null) {
                for (int i = 0; i < isi.length; i++) hapusRekursif(isi[i]);
            }
        }
        f.delete();
    }
}
