package com.androlua;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Penganalisis APK (seperti APK Analyzer di Android Studio): membaca satu
 * berkas APK lalu melaporkan nama paket, versi, SDK, daftar izin, jumlah dex,
 * dan rincian ukuran per bagian (dex, resource, aset, pustaka native, dll.)
 * beserta entri-entri terbesar.
 *
 * Murni Java (tanpa API Android): manifest biner (AXML) diurai sendiri di
 * kelas ini, jadi logikanya bisa diuji di komputer dengan APK sungguhan.
 */
public class AnalisisApk {

    public static class Hasil {
        public String namaPaket = "";
        public String versiNama = "";
        public int versiKode = -1;
        public int minSdk = -1;
        public int targetSdk = -1;
        public List<String> izin = new ArrayList<String>();
        public int jumlahDex = 0;
        public long ukuranBerkas = 0;
        /** Kategori -> total ukuran terkompresi (bita). */
        public Map<String, Long> perBagian = new LinkedHashMap<String, Long>();
        /** Entri terbesar: [nama, ukuranAsli]. */
        public List<String[]> terbesar = new ArrayList<String[]>();
        public String galat = null;
    }

    /** Analisis satu berkas APK. Tidak melempar; kegagalan ada di Hasil.galat. */
    public static Hasil analisis(File apk) {
        Hasil h = new Hasil();
        if (apk == null || !apk.isFile()) {
            h.galat = "Berkas APK tidak ditemukan.";
            return h;
        }
        h.ukuranBerkas = apk.length();
        ZipFile zf = null;
        try {
            zf = new ZipFile(apk);
            byte[] manifest = null;
            List<String[]> semua = new ArrayList<String[]>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String n = e.getName();
                long tekan = e.getCompressedSize() >= 0 ? e.getCompressedSize() : e.getSize();
                String kat = kategori(n);
                Long lama = h.perBagian.get(kat);
                h.perBagian.put(kat, (lama == null ? 0 : lama.longValue()) + tekan);
                if (Perisai.namaDex(n)) h.jumlahDex++;
                semua.add(new String[]{n, String.valueOf(e.getSize())});
                if (n.equals("AndroidManifest.xml")) {
                    InputStream in = zf.getInputStream(e);
                    try {
                        manifest = Perisai.bacaSemua(in);
                    } finally {
                        in.close();
                    }
                }
            }
            Collections.sort(semua, new Comparator<String[]>() {
                @Override
                public int compare(String[] a, String[] b) {
                    long x = Long.parseLong(a[1]);
                    long y = Long.parseLong(b[1]);
                    return x < y ? 1 : (x > y ? -1 : 0);
                }
            });
            for (int i = 0; i < semua.size() && i < 10; i++) h.terbesar.add(semua.get(i));
            if (manifest != null) uraiManifest(manifest, h);
        } catch (Throwable t) {
            h.galat = "Gagal membaca APK: " + t.getMessage();
        } finally {
            if (zf != null) {
                try { zf.close(); } catch (Throwable ignored) { }
            }
        }
        return h;
    }

    /** Kategori satu entri APK untuk rincian ukuran. */
    public static String kategori(String nama) {
        if (Perisai.namaDex(nama)) return "Kode program (dex)";
        if (nama.equals("resources.arsc")) return "Tabel resource (resources.arsc)";
        if (nama.startsWith("res/")) return "Resource (res)";
        if (nama.startsWith("assets/")) return "Aset (assets)";
        if (nama.startsWith("lib/")) return "Pustaka native (lib)";
        if (nama.startsWith("META-INF/")) return "Tanda tangan dan meta (META-INF)";
        if (nama.equals("AndroidManifest.xml")) return "Manifest";
        return "Lainnya";
    }

    /** Format ukuran ramah dengar: "12,4 MB" / "830 KB" / "512 bita". */
    public static String formatUkuran(long bita) {
        if (bita < 1024) return bita + " bita";
        double kb = bita / 1024.0;
        if (kb < 1024) return satuDesimal(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return satuDesimal(mb) + " MB";
        return satuDesimal(mb / 1024.0) + " GB";
    }

    private static String satuDesimal(double v) {
        long bulat = Math.round(v * 10);
        return (bulat / 10) + "," + (bulat % 10);
    }

    /** Susun laporan teks (Bahasa Indonesia) dari hasil analisis. */
    public static String laporan(Hasil h, String namaTampil) {
        if (h.galat != null) return h.galat;
        StringBuilder b = new StringBuilder();
        b.append("Analisis APK: ").append(namaTampil).append('\n');
        b.append("Nama paket: ").append(h.namaPaket.length() == 0 ? "(tidak terbaca)" : h.namaPaket).append('\n');
        b.append("Versi: ").append(h.versiNama.length() == 0 ? "-" : h.versiNama);
        if (h.versiKode >= 0) b.append(" (kode ").append(h.versiKode).append(')');
        b.append('\n');
        if (h.minSdk >= 0) b.append("SDK minimum: ").append(h.minSdk).append('\n');
        if (h.targetSdk >= 0) b.append("SDK target: ").append(h.targetSdk).append('\n');
        b.append("Ukuran berkas: ").append(formatUkuran(h.ukuranBerkas)).append('\n');
        b.append("Jumlah berkas dex: ").append(h.jumlahDex).append('\n');
        b.append('\n').append("Rincian ukuran per bagian (terkompresi):\n");
        for (Map.Entry<String, Long> e : h.perBagian.entrySet()) {
            b.append("- ").append(e.getKey()).append(": ").append(formatUkuran(e.getValue())).append('\n');
        }
        b.append('\n').append("Izin yang dipakai (").append(h.izin.size()).append("):\n");
        if (h.izin.isEmpty()) {
            b.append("- (tidak ada izin terbaca)\n");
        } else {
            for (String z : h.izin) b.append("- ").append(z).append('\n');
        }
        b.append('\n').append("Entri terbesar:\n");
        for (String[] t : h.terbesar) {
            b.append("- ").append(t[0]).append(": ").append(formatUkuran(Long.parseLong(t[1]))).append('\n');
        }
        return b.toString();
    }

    // ---------------------------------------------------------- pengurai AXML

    private static int u16(byte[] d, int o) {
        return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8);
    }

    private static int u32(byte[] d, int o) {
        return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8) | ((d[o + 2] & 0xff) << 16) | ((d[o + 3] & 0xff) << 24);
    }

    /** Urai manifest biner: isi namaPaket, versi, SDK, dan izin pada Hasil. */
    public static void uraiManifest(byte[] d, Hasil h) {
        if (d == null || d.length < 8) return;
        List<String> pool = null;
        int pos = 8; // sesudah header berkas
        while (pos + 8 <= d.length) {
            int tipe = u16(d, pos);
            int ukuran = u32(d, pos + 4);
            if (ukuran <= 0) break;
            if (tipe == 0x0001) { // string pool
                pool = bacaStringPool(d, pos);
            } else if (tipe == 0x0102 && pool != null) { // start element
                bacaElemen(d, pos, pool, h);
            }
            pos += ukuran;
        }
    }

    private static List<String> bacaStringPool(byte[] d, int awal) {
        List<String> hasil = new ArrayList<String>();
        int jumlah = u32(d, awal + 8);
        int flags = u32(d, awal + 16);
        int awalString = u32(d, awal + 20);
        boolean utf8 = (flags & 0x100) != 0;
        for (int i = 0; i < jumlah; i++) {
            int off = u32(d, awal + 28 + i * 4);
            int p = awal + awalString + off;
            if (p < 0 || p >= d.length) {
                hasil.add("");
                continue;
            }
            if (utf8) {
                // lewati panjang karakter (1-2 bita), lalu panjang bita (1-2 bita)
                int q = p;
                int b1 = d[q++] & 0xff;
                if ((b1 & 0x80) != 0) q++;
                int b2 = d[q++] & 0xff;
                int len = b2;
                if ((b2 & 0x80) != 0) {
                    len = ((b2 & 0x7f) << 8) | (d[q++] & 0xff);
                }
                int akhir = Math.min(d.length, q + len);
                try {
                    hasil.add(new String(d, q, Math.max(0, akhir - q), "UTF-8"));
                } catch (Throwable t) {
                    hasil.add("");
                }
            } else {
                int len = u16(d, p);
                int q = p + 2;
                if ((len & 0x8000) != 0) {
                    len = ((len & 0x7fff) << 16) | u16(d, q);
                    q += 2;
                }
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < len && q + 1 < d.length; k++, q += 2) {
                    sb.append((char) u16(d, q));
                }
                hasil.add(sb.toString());
            }
        }
        return hasil;
    }

    private static String poolStr(List<String> pool, int idx) {
        if (idx < 0 || idx >= pool.size()) return "";
        String s = pool.get(idx);
        return s == null ? "" : s;
    }

    private static void bacaElemen(byte[] d, int awal, List<String> pool, Hasil h) {
        if (awal + 36 > d.length) return;
        String nama = poolStr(pool, u32(d, awal + 20));
        int awalAttr = u16(d, awal + 24);
        int jumlahAttr = u16(d, awal + 28);
        // attributeStart dihitung dari awal struktur attrExt (awal + 16).
        int p = awal + 16 + awalAttr;
        boolean manifest = nama.equals("manifest");
        boolean usesSdk = nama.equals("uses-sdk");
        boolean izin = nama.equals("uses-permission") || nama.equals("uses-permission-sdk-23");
        if (!manifest && !usesSdk && !izin) return;
        for (int i = 0; i < jumlahAttr; i++, p += 20) {
            if (p + 20 > d.length) break;
            String namaAttr = poolStr(pool, u32(d, p + 4));
            int raw = u32(d, p + 8);
            int tipeVal = d[p + 15] & 0xff;
            int data = u32(d, p + 16);
            String nilaiStr = raw >= 0 ? poolStr(pool, raw) : (tipeVal == 0x03 ? poolStr(pool, data) : null);
            if (manifest) {
                if (namaAttr.equals("package") && nilaiStr != null) h.namaPaket = nilaiStr;
                else if (namaAttr.equals("versionName") && nilaiStr != null) h.versiNama = nilaiStr;
                else if (namaAttr.equals("versionCode")) h.versiKode = data;
            } else if (usesSdk) {
                if (namaAttr.equals("minSdkVersion")) h.minSdk = data;
                else if (namaAttr.equals("targetSdkVersion")) h.targetSdk = data;
            } else if (izin) {
                if (namaAttr.equals("name") && nilaiStr != null && nilaiStr.length() > 0
                        && !h.izin.contains(nilaiStr)) {
                    h.izin.add(nilaiStr);
                }
            }
        }
    }
}
