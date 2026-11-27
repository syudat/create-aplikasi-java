package com.androlua;

import com.android.apksig.ApkSigner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Pabrik Android App Bundle dan Pemeriksa Kesiapan Google Play Store
 * (Fitur 13, daftar Gemini 2026-11-27).
 *
 * Logika murni (tanpa API Android) supaya bisa diuji di komputer:
 *  - bangunAAB: susun arsip .aab (zip) berstruktur bundle — berkas
 *    konfigurasi bundle, modul dasar (manifest, dex, res, aset,
 *    pustaka asli) dari folder proyek.
 *  - bangunAABDariApk: kemas ulang APK hasil build (manifest biner,
 *    dex, resources.arsc, res, aset, .so) menjadi .aab.
 *  - validasiBundel: periksa struktur bundel seperti yang diperiksa
 *    bundletool (BundleConfig.pb, modul dasar, manifest biner, dex,
 *    .so tak terkompresi, keutuhan CRC tiap entri).
 *  - bangunPecahan: pecah bundel MENJADI APK varian sungguhan (dasar,
 *    per ABI, per bahasa, universal) dan ukur ukuran berkasnya —
 *    ukuran pecahan NYATA dari pemecahan, bukan perkiraan.
 *  - tandatanganiBundel: tanda tangan JAR (v1, seperti jarsigner
 *    untuk AAB) memakai ApkSigner yang sudah dibundel di aplikasi,
 *    dengan kunci proyek yang sama seperti penandatanganan APK.
 *  - verifikasiTandaTanganBundel: periksa rantai tanda tangan
 *    (digest tiap entri di MANIFEST.MF, digest manifest di .SF,
 *    tanda tangan RSA atas .SF) tanpa jarsigner.
 *  - simulasiPecahan: perkiraan ukuran unduhan per ABI dan bahasa
 *    (cara lama; dipertahankan untuk kompatibilitas).
 *  - auditPlay: periksa target SDK, izin berbahaya, dan arsitektur
 *    64-bit dari teks manifest.
 *
 * BUNDLETOOL SUNGGUHAN: TIDAK BISA dijalankan di perangkat — lihat
 * penilaianBundletool(). bundletool adalah berkas .jar yang butuh
 * JVM ("java -jar", Java 11+); Android/ART tidak punya JVM dan tidak
 * bisa mengeksekusi bytecode JVM. Jalur terdekat yang nyata di
 * perangkat adalah yang dibangun di kelas ini: validasi struktur
 * bundel, pemecahan nyata menjadi APK varian dengan ukuran terukur,
 * dan tanda tangan JAR v1 (skema yang sama dengan jarsigner yang
 * dipakai Play Console untuk AAB) memakai ApkSigner.
 *
 * BATAS JUJUR: manifest di dalam bundel harus XML biner hasil aapt2
 * (validasiBundel menandainya bila masih teks); pemecahan di sini
 * memakai dimensi ABI dan BAHASA dari BundleConfig.pb — dimensi
 * kerapatan layar dan format kompresi tekstur tidak dipecah karena
 * proyek tidak membawa varian itu. Penerimaan akhir bundel oleh Play
 * Console tetap butuh unggah sungguhan (tidak bisa dibuktikan dari
 * ponsel); yang dibuktikan di sini adalah struktur, ukuran pecahan
 * nyata, dan keabsahan kriptografis tanda tangan.
 */
public class PabrikAAB {

    /** ABI yang didukung pemecahan. */
    public static final String[] ABI = {"arm64-v8a", "armeabi-v7a", "x86_64"};

    /** Satu temuan audit kesiapan Play Store / validasi bundel. */
    public static class Temuan {
        public String tingkat = "";
        public String penjelasan = "";
        public Temuan(String tingkat, String penjelasan) {
            this.tingkat = tingkat;
            this.penjelasan = penjelasan;
        }
        public String toString() {
            return tingkat + ": " + penjelasan;
        }
    }

    /** Satu varian pecahan: ABI/bahasa + ukuran unduhan (nyata bila dari bangunPecahan). */
    public static class Varian {
        public String nama = "";
        public long ukuran = 0;
        /** Jalur berkas APK varian (kosong bila varian hanya perkiraan). */
        public String berkas = "";
        public String toString() {
            return nama + ": " + ukuran + " bita";
        }
    }

    /** Hasil verifikasi tanda tangan JAR sebuah bundel. */
    public static class HasilVerifikasi {
        public boolean ditandatangani = false;
        public boolean manifesOk = false;
        public boolean tandaTanganOk = false;
        public String sidikJariSertifikat = "";
        public String rincian = "";
        public boolean sah() {
            return ditandatangani && manifesOk && tandaTanganOk;
        }
        public String toString() {
            return rincian;
        }
    }

    /** Pasangan kunci + sertifikat proyek (kunci penandatanganan). */
    public static class KunciProyek {
        public PrivateKey kunci;
        public X509Certificate sertifikat;
        public String sandi = "";
    }

    // ------------------------------------------------------- BundleConfig

    /** Teks konfigurasi bundle (BundleConfig.pb.json versi teks, untuk dibaca manusia). */
    public static String teksBundleConfig() {
        return "{\n"
                + "  \"optimizations\": {\n"
                + "    \"splitsConfig\": {\n"
                + "      \"splitDimension\": [\n"
                + "        {\"value\": \"ABI\", \"negate\": false, \"suffixStripping\": {\"enabled\": false}},\n"
                + "        {\"value\": \"LANGUAGE\", \"negate\": false, \"suffixStripping\": {\"enabled\": false}}\n"
                + "      ]\n"
                + "    }\n"
                + "  },\n"
                + "  \"compression\": {\"uncompressedGlob\": [\"**/*.so\"]}\n"
                + "}\n";
    }

    /**
     * BundleConfig.pb dalam bentuk protobuf biner (bukan JSON).
     * Susunan mengikuti config.proto bundletool:
     * BundleConfig { Optimizations optimizations = 2; Compression compression = 3; }
     * Optimizations { SplitsConfig splits_config = 1; }
     * SplitsConfig { repeated SplitDimension split_dimension = 1; }
     * SplitDimension { Value value = 1; bool negate = 2; SuffixStripping suffix_stripping = 3; }
     *   Value: ABI = 1, LANGUAGE = 3.
     * SuffixStripping { bool enabled = 1; }
     * Compression { repeated string uncompressed_glob = 1; }
     */
    public static byte[] bytesBundleConfig() {
        byte[] dimAbi = pbPesan(1, pbVarint(1, 1), pbVarint(2, 0),
                pbPesan(3, pbVarint(1, 0)));
        byte[] dimBahasa = pbPesan(1, pbVarint(1, 3), pbVarint(2, 0),
                pbPesan(3, pbVarint(1, 0)));
        byte[] splits = pbPesan(1, dimAbi, dimBahasa);
        byte[] optimasi = pbPesan(2, splits);
        byte[] kompresi = pbPesan(3, pbLen(1, "**/*.so".getBytes()));
        return gabung(optimasi, kompresi);
    }

    /** Satu field varint protobuf: nomor field + nilai. */
    static byte[] pbVarint(int nomor, long nilai) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        tulisVarint(o, ((long) nomor << 3) | 0);
        tulisVarint(o, nilai);
        return o.toByteArray();
    }

    /** Satu field length-delimited protobuf: nomor field + bita. */
    static byte[] pbLen(int nomor, byte[] data) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        tulisVarint(o, ((long) nomor << 3) | 2);
        tulisVarint(o, data.length);
        o.write(data, 0, data.length);
        return o.toByteArray();
    }

    /** Satu field pesan (length-delimited berisi gabungan sub-field). */
    static byte[] pbPesan(int nomor, byte[]... bagian) {
        return pbLen(nomor, gabung(bagian));
    }

    static void tulisVarint(ByteArrayOutputStream o, long nilai) {
        long v = nilai;
        while (true) {
            int b = (int) (v & 0x7F);
            v >>>= 7;
            if (v == 0) {
                o.write(b);
                return;
            }
            o.write(b | 0x80);
        }
    }

    static byte[] gabung(byte[]... bagian) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < bagian.length; i++) {
            if (bagian[i] != null) o.write(bagian[i], 0, bagian[i].length);
        }
        return o.toByteArray();
    }

    /** Satu field protobuf hasil urai (untuk validasi BundleConfig.pb). */
    static class PbIsi {
        int nomor;
        int wire;
        long varint;
        byte[] data;
        List<PbIsi> anak;
    }

    /** Urai pesan protobuf; null bila bita tidak membentuk pesan yang sah. */
    static List<PbIsi> uraiPesan(byte[] b) {
        if (b == null) return null;
        List<PbIsi> hasil = new ArrayList<PbIsi>();
        int pos = 0;
        while (pos < b.length) {
            long[] tag = bacaVarint(b, pos);
            if (tag == null) return null;
            pos += (int) tag[1];
            int nomor = (int) (tag[0] >> 3);
            int wire = (int) (tag[0] & 7);
            if (nomor <= 0) return null;
            PbIsi f = new PbIsi();
            f.nomor = nomor;
            f.wire = wire;
            if (wire == 0) {
                long[] v = bacaVarint(b, pos);
                if (v == null) return null;
                f.varint = v[0];
                pos += (int) v[1];
            } else if (wire == 2) {
                long[] n = bacaVarint(b, pos);
                if (n == null) return null;
                pos += (int) n[1];
                if (n[0] < 0 || n[0] > b.length - pos) return null;
                f.data = new byte[(int) n[0]];
                System.arraycopy(b, pos, f.data, 0, (int) n[0]);
                pos += (int) n[0];
                List<PbIsi> sub = uraiPesan(f.data);
                if (sub != null && !sub.isEmpty()) f.anak = sub;
            } else if (wire == 5) {
                if (4 > b.length - pos) return null;
                f.data = new byte[4];
                System.arraycopy(b, pos, f.data, 0, 4);
                pos += 4;
            } else if (wire == 1) {
                if (8 > b.length - pos) return null;
                f.data = new byte[8];
                System.arraycopy(b, pos, f.data, 0, 8);
                pos += 8;
            } else {
                return null;
            }
            hasil.add(f);
        }
        return hasil;
    }

    /** Baca varint protobuf; kembalian {nilai, jumlahBita} atau null. */
    static long[] bacaVarint(byte[] b, int pos) {
        long hasil = 0;
        int geser = 0;
        int p = pos;
        while (p < b.length && geser < 70) {
            int c = b[p] & 0xFF;
            p++;
            hasil |= (long) (c & 0x7F) << geser;
            if ((c & 0x80) == 0) return new long[]{hasil, p - pos};
            geser += 7;
        }
        return null;
    }

    /** Daftar nilai dimensi pemecahan (ABI=1, LANGUAGE=3) dari BundleConfig.pb. */
    static List<Integer> dimensiDariBundleConfig(byte[] pb) {
        List<Integer> hasil = new ArrayList<Integer>();
        List<PbIsi> akar = uraiPesan(pb);
        if (akar == null) return hasil;
        for (int i = 0; i < akar.size(); i++) {
            if (akar.get(i).nomor != 2 || akar.get(i).anak == null) continue;
            for (int j = 0; j < akar.get(i).anak.size(); j++) {
                PbIsi splits = akar.get(i).anak.get(j);
                if (splits.nomor != 1 || splits.anak == null) continue;
                for (int k = 0; k < splits.anak.size(); k++) {
                    PbIsi dim = splits.anak.get(k);
                    if (dim.nomor != 1 || dim.anak == null) continue;
                    for (int m = 0; m < dim.anak.size(); m++) {
                        if (dim.anak.get(m).nomor == 1 && dim.anak.get(m).wire == 0) {
                            hasil.add((int) dim.anak.get(m).varint);
                        }
                    }
                }
            }
        }
        return hasil;
    }

    // ------------------------------------------------------- bangun AAB

    /**
     * Susun berkas .aab dari folder modul dasar. Folder sumber berisi
     * manifest/AndroidManifest.xml, dex/classes.dex, res/, assets/,
     * lib/<abi>/berkas .so (yang ada saja yang dimasukkan). Mengembalikan
     * berkas .aab tujuan (null bila gagal). Berkas .so ditulis TANPA
     * kompresi, sesuai uncompressedGlob untuk .so di BundleConfig.
     */
    public static File bangunAAB(File dirModul, File keluar) {
        try {
            if (dirModul == null || !dirModul.isDirectory() || keluar == null) return null;
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(keluar));
            try {
                tulisEntri(zip, "BundleConfig.pb", bytesBundleConfig());
                tambahFolder(zip, dirModul, "base/");
            } finally {
                zip.close();
            }
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Kemas ulang APK hasil build menjadi .aab: manifest biner ->
     * base/manifest/AndroidManifest.xml, resources.arsc ->
     * base/resources.pb, classes*.dex -> base/dex/, res/ -> base/res/,
     * assets/ -> base/assets/, lib/<abi>/... -> base/lib/<abi>/.
     * Tanda tangan APK lama (META-INF) dibuang; bundel ditandatangani
     * terpisah dengan tandatanganiBundel(). Mengembalikan berkas .aab
     * tujuan (null bila gagal).
     */
    public static File bangunAABDariApk(File apk, File keluar) {
        ZipFile zf = null;
        ZipOutputStream zip = null;
        try {
            if (apk == null || !apk.isFile() || keluar == null) return null;
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            zf = new ZipFile(apk);
            zip = new ZipOutputStream(new FileOutputStream(keluar));
            tulisEntri(zip, "BundleConfig.pb", bytesBundleConfig());
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (n.startsWith("META-INF/")) continue;
                String tujuan = null;
                if (n.equals("AndroidManifest.xml")) {
                    tujuan = "base/manifest/AndroidManifest.xml";
                } else if (n.equals("resources.arsc")) {
                    tujuan = "base/resources.pb";
                } else if (n.startsWith("classes") && n.endsWith(".dex")) {
                    tujuan = "base/dex/" + n;
                } else if (n.startsWith("res/")) {
                    tujuan = "base/" + n;
                } else if (n.startsWith("assets/")) {
                    tujuan = "base/" + n;
                } else if (n.startsWith("lib/")) {
                    tujuan = "base/" + n;
                }
                if (tujuan == null) continue;
                salinEntri(zip, zf, e, tujuan, n.endsWith(".so"));
            }
            zip.close();
            zip = null;
            zf.close();
            zf = null;
            return keluar;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (zip != null) zip.close();
            } catch (Throwable ignored) {
            }
            try {
                if (zf != null) zf.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void tambahFolder(ZipOutputStream zip, File dir, String awalan)
            throws Exception {
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                tambahFolder(zip, f, awalan + f.getName() + "/");
            } else {
                String nama = awalan + f.getName();
                if (nama.endsWith(".so")) {
                    tulisEntriStored(zip, nama, f);
                } else {
                    FileInputStream in = new FileInputStream(f);
                    try {
                        zip.putNextEntry(new ZipEntry(nama));
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                        zip.closeEntry();
                    } finally {
                        in.close();
                    }
                }
            }
        }
    }

    private static void tulisEntri(ZipOutputStream zip, String nama, byte[] data)
            throws Exception {
        zip.putNextEntry(new ZipEntry(nama));
        zip.write(data);
        zip.closeEntry();
    }

    /** Tulis berkas sebagai entri STORED (tanpa kompresi) dengan CRC yang benar. */
    private static void tulisEntriStored(ZipOutputStream zip, String nama, File f)
            throws Exception {
        CRC32 crc = new CRC32();
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) crc.update(buf, 0, n);
        } finally {
            in.close();
        }
        ZipEntry e = new ZipEntry(nama);
        e.setMethod(ZipEntry.STORED);
        e.setSize(f.length());
        e.setCompressedSize(f.length());
        e.setCrc(crc.getValue());
        zip.putNextEntry(e);
        FileInputStream in2 = new FileInputStream(f);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in2.read(buf)) > 0) zip.write(buf, 0, n);
        } finally {
            in2.close();
        }
        zip.closeEntry();
    }

    /** Salin satu entri zip ke zip lain (streaming; .so selalu STORED). */
    private static void salinEntri(ZipOutputStream zip, ZipFile zf, ZipEntry asal,
                                   String namaBaru, boolean stored) throws Exception {
        if (stored) {
            InputStream in = zf.getInputStream(asal);
            CRC32 crc = new CRC32();
            long ukuran = 0;
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    crc.update(buf, 0, n);
                    ukuran += n;
                }
            } finally {
                in.close();
            }
            ZipEntry e = new ZipEntry(namaBaru);
            e.setMethod(ZipEntry.STORED);
            e.setSize(ukuran);
            e.setCompressedSize(ukuran);
            e.setCrc(crc.getValue());
            zip.putNextEntry(e);
            InputStream in2 = zf.getInputStream(asal);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in2.read(buf)) > 0) zip.write(buf, 0, n);
            } finally {
                in2.close();
            }
            zip.closeEntry();
        } else {
            zip.putNextEntry(new ZipEntry(namaBaru));
            InputStream in = zf.getInputStream(asal);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
            } finally {
                in.close();
            }
            zip.closeEntry();
        }
    }

    /** Jumlah ukuran berkas di bawah folder (rekursif). */
    public static long ukuranFolder(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long total = 0;
        File[] daftar = dir.listFiles();
        if (daftar == null) return 0;
        for (int i = 0; i < daftar.length; i++) total += ukuranFolder(daftar[i]);
        return total;
    }

    // ------------------------------------------------------- validasi bundel

    /**
     * Penilaian jujur: bisakah bundletool sungguhan dijalankan di
     * perangkat? Jawabannya TIDAK — bundletool adalah berkas .jar yang
     * butuh JVM ("java -jar", Java 11+); Android/ART tidak punya JVM
     * dan tidak bisa mengeksekusi bytecode JVM. aapt2 memang sudah
     * dibundel di aplikasi ini sebagai pustaka native, tetapi tanpa
     * JVM bundletool sendiri tidak bisa mulai. Yang bisa dilakukan di
     * perangkat adalah validasi struktur, pemecahan nyata, dan tanda
     * tangan JAR v1 di kelas ini.
     */
    public static String penilaianBundletool() {
        return "bundletool TIDAK BISA dijalankan di HP. Alasannya: "
                + "1) bundletool adalah berkas .jar yang butuh JVM (dijalankan "
                + "dengan \"java -jar\", Java 11 atau lebih baru); Android tidak "
                + "punya JVM — ART hanya menjalankan kode DEX, bukan bytecode JVM. "
                + "2) bundletool juga butuh biner aapt2 untuk mengolah sumber daya; "
                + "aapt2 memang sudah dibundel di aplikasi ini sebagai pustaka "
                + "native, tetapi tanpa JVM bundletool sendiri tidak bisa mulai. "
                + "3) bundletool adalah jar gemuk belasan MB (Guava, protobuf) yang "
                + "tidak bisa diubah menjadi DEX di perangkat. "
                + "Jalur terdekat yang nyata di HP: validasi struktur bundel "
                + "(validasiBundel), pemecahan nyata menjadi APK varian dengan "
                + "ukuran terukur (bangunPecahan), dan tanda tangan JAR v1 seperti "
                + "jarsigner memakai ApkSigner (tandatanganiBundel).";
    }

    /**
     * Periksa struktur bundel seperti yang diperiksa bundletool:
     * BundleConfig.pb (protobuf, berisi dimensi ABI + BAHASA), modul
     * dasar, manifest (harus XML biner hasil aapt2), dex, .so tanpa
     * kompresi, dan keutuhan CRC tiap entri. Daftar kosong = lolos.
     */
    public static List<Temuan> validasiBundel(File aab) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (aab == null || !aab.isFile()) {
            hasil.add(new Temuan("Tinggi", "Berkas bundel tidak ditemukan."));
            return hasil;
        }
        ZipFile zf;
        try {
            zf = new ZipFile(aab);
        } catch (Throwable t) {
            hasil.add(new Temuan("Tinggi", "Bundel bukan arsip zip yang sah: " + t.getMessage()));
            return hasil;
        }
        try {
            ZipEntry config = zf.getEntry("BundleConfig.pb");
            if (config == null) {
                if (zf.getEntry("BundleConfig.pb.json") != null) {
                    hasil.add(new Temuan("Tinggi",
                            "Bundel memakai BundleConfig.pb.json (teks JSON); "
                            + "bundletool mewajibkan BundleConfig.pb (protobuf biner)."));
                } else {
                    hasil.add(new Temuan("Tinggi", "BundleConfig.pb tidak ada di bundel."));
                }
            } else {
                byte[] pb = bacaEntri(zf, config);
                if (pb == null) {
                    hasil.add(new Temuan("Tinggi", "BundleConfig.pb tidak bisa dibaca."));
                } else {
                    List<Integer> dimensi = dimensiDariBundleConfig(pb);
                    if (dimensi.isEmpty()) {
                        hasil.add(new Temuan("Tinggi", "BundleConfig.pb tidak bisa diurai sebagai protobuf."));
                    } else {
                        if (!dimensi.contains(Integer.valueOf(1))) {
                            hasil.add(new Temuan("Sedang", "BundleConfig.pb tidak memecah menurut ABI."));
                        }
                        if (!dimensi.contains(Integer.valueOf(3))) {
                            hasil.add(new Temuan("Sedang", "BundleConfig.pb tidak memecah menurut bahasa."));
                        }
                    }
                }
            }
            ZipEntry manifest = zf.getEntry("base/manifest/AndroidManifest.xml");
            if (manifest == null) {
                hasil.add(new Temuan("Tinggi", "base/manifest/AndroidManifest.xml tidak ada."));
            } else {
                byte[] m = bacaEntri(zf, manifest);
                if (m == null) {
                    hasil.add(new Temuan("Tinggi", "Manifest tidak bisa dibaca."));
                } else if (m.length >= 4 && m[0] == 0x03 && m[1] == 0x00 && m[2] == 0x08 && m[3] == 0x00) {
                    // XML biner (AXML) — benar.
                } else if (m.length > 0 && m[0] == '<') {
                    hasil.add(new Temuan("Tinggi",
                            "Manifest masih teks XML; bundel butuh manifest biner hasil "
                            + "aapt2 (bangun dulu APK-nya, lalu kemas dari APK)."));
                } else {
                    hasil.add(new Temuan("Tinggi", "Manifest tidak dikenali (bukan XML teks maupun biner)."));
                }
            }
            if (zf.getEntry("base/dex/classes.dex") == null) {
                hasil.add(new Temuan("Tinggi", "base/dex/classes.dex tidak ada."));
            }
            if (zf.getEntry("base/resources.pb") == null) {
                hasil.add(new Temuan("Rendah",
                        "base/resources.pb tidak ada (wajar bila proyek tanpa sumber daya)."));
            }
            boolean adaModulLain = false;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (n.endsWith(".so") && e.getMethod() != ZipEntry.STORED) {
                    hasil.add(new Temuan("Tinggi",
                            "Pustaka asli terkompresi: " + n + " (harus tanpa kompresi)."));
                }
                if (!n.startsWith("base/") && !n.startsWith("META-INF/")
                        && !n.equals("BundleConfig.pb") && !n.equals("BundleConfig.pb.json")) {
                    adaModulLain = true;
                }
                try {
                    InputStream in = zf.getInputStream(e);
                    byte[] buf = new byte[16384];
                    while (in.read(buf) > 0) {
                    }
                    in.close();
                } catch (Throwable t) {
                    hasil.add(new Temuan("Tinggi", "Entri rusak (CRC tidak cocok): " + n));
                }
            }
            if (adaModulLain) {
                hasil.add(new Temuan("Rendah",
                        "Ada entri di luar modul base/ dan META-INF/ (modul fitur tambahan)."));
            }
        } finally {
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
        }
        return hasil;
    }

    private static byte[] bacaEntri(ZipFile zf, ZipEntry e) {
        if (zf == null || e == null) return null;
        InputStream in = null;
        try {
            in = zf.getInputStream(e);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return buf.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------- pemecahan nyata

    /** ABI dari jalan entri bundel base/lib/<abi>/..., atau "" bila bukan pustaka asli. */
    static String abiDariJalan(String jalan) {
        if (jalan == null || !jalan.startsWith("base/lib/")) return "";
        String sisa = jalan.substring("base/lib/".length());
        int garis = sisa.indexOf('/');
        if (garis <= 0) return "";
        return sisa.substring(0, garis);
    }

    /** Bahasa dari jalan sumber daya base/res/<dir>/..., atau "" bila bukan sumber daya bahasa. */
    static String bahasaDariJalanRes(String jalan) {
        if (jalan == null || !jalan.startsWith("base/res/")) return "";
        String sisa = jalan.substring("base/res/".length());
        int garis = sisa.indexOf('/');
        if (garis <= 0) return "";
        String dir = sisa.substring(0, garis);
        String[] token = dir.split("-");
        if (token.length < 2) return "";
        String pertama = token[1];
        if (pertama.startsWith("b+")) {
            // Bentuk BCP 47: values-b+en+Latn -> bahasa "en".
            String isi = pertama.substring(2);
            int plus = isi.indexOf('+');
            return plus < 0 ? isi : isi.substring(0, plus);
        }
        if (!pertama.matches("[a-z]{2,3}")) return "";
        if (BUKAN_BAHASA.contains(pertama)) return "";
        return pertama;
    }

    private static final List<String> BUKAN_BAHASA = new ArrayList<String>();
    static {
        String[] daftar = {"hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "mdpi", "ldpi",
                "nodpi", "tvdpi", "anydpi", "land", "port", "night", "notnight",
                "car", "television", "watch", "v21", "v23", "v24", "v26", "v28",
                "v29", "v30", "v31", "v33", "sw600dp", "sw720dp"};
        for (int i = 0; i < daftar.length; i++) BUKAN_BAHASA.add(daftar[i]);
    }

    /** Jalan entri APK tujuan dari jalan entri bundel (tanpa awalan base/). */
    static String jalanApkDariBundel(String jalan) {
        if (jalan.equals("base/manifest/AndroidManifest.xml")) return "AndroidManifest.xml";
        if (jalan.equals("base/resources.pb")) return "resources.arsc";
        if (jalan.startsWith("base/dex/")) return jalan.substring("base/dex/".length());
        if (jalan.startsWith("base/")) return jalan.substring("base/".length());
        return jalan;
    }

    /**
     * Pecah bundel MENJADI APK varian sungguhan dan ukur ukuran
     * berkasnya (bukan perkiraan): varian universal (semua isi),
     * varian dasar (tanpa .so dan tanpa sumber daya bahasa), satu
     * varian per ABI (dasar + .so ABI itu), satu varian per bahasa
     * (dasar + sumber daya bahasa itu). APK varian ditulis ke
     * dirKeluar dan TIDAK ditandatangani (hanya untuk mengukur ukuran;
     * pemasangan butuh penandatanganan terpisah).
     */
    public static List<Varian> bangunPecahan(File aab, File dirKeluar) {
        List<Varian> hasil = new ArrayList<Varian>();
        if (aab == null || !aab.isFile() || dirKeluar == null) return hasil;
        ZipFile zf;
        try {
            zf = new ZipFile(aab);
        } catch (Throwable t) {
            return hasil;
        }
        try {
            List<String> semua = new ArrayList<String>();
            List<String> dasar = new ArrayList<String>();
            Map<String, List<String>> perAbi = new LinkedHashMap<String, List<String>>();
            Map<String, List<String>> perBahasa = new LinkedHashMap<String, List<String>>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (n.startsWith("META-INF/") || n.equals("BundleConfig.pb")
                        || n.equals("BundleConfig.pb.json")) continue;
                semua.add(n);
                String abi = abiDariJalan(n);
                String bahasa = bahasaDariJalanRes(n);
                if (abi.length() > 0) {
                    List<String> daftar = perAbi.get(abi);
                    if (daftar == null) {
                        daftar = new ArrayList<String>();
                        perAbi.put(abi, daftar);
                    }
                    daftar.add(n);
                } else if (bahasa.length() > 0) {
                    List<String> daftar = perBahasa.get(bahasa);
                    if (daftar == null) {
                        daftar = new ArrayList<String>();
                        perBahasa.put(bahasa, daftar);
                    }
                    daftar.add(n);
                } else {
                    dasar.add(n);
                }
            }
            String namaDasar = aab.getName();
            if (namaDasar.endsWith(".aab")) namaDasar = namaDasar.substring(0, namaDasar.length() - 4);
            dirKeluar.mkdirs();
            hasil.add(tulisVarian(zf, semua, new File(dirKeluar, namaDasar + "-universal.apk"),
                    "universal"));
            hasil.add(tulisVarian(zf, dasar, new File(dirKeluar, namaDasar + "-dasar.apk"),
                    "dasar"));
            for (Map.Entry<String, List<String>> entri : perAbi.entrySet()) {
                List<String> isi = new ArrayList<String>(dasar);
                isi.addAll(entri.getValue());
                hasil.add(tulisVarian(zf, isi,
                        new File(dirKeluar, namaDasar + "-abi-" + entri.getKey() + ".apk"),
                        "abi-" + entri.getKey()));
            }
            for (Map.Entry<String, List<String>> entri : perBahasa.entrySet()) {
                List<String> isi = new ArrayList<String>(dasar);
                isi.addAll(entri.getValue());
                hasil.add(tulisVarian(zf, isi,
                        new File(dirKeluar, namaDasar + "-bahasa-" + entri.getKey() + ".apk"),
                        "bahasa-" + entri.getKey()));
            }
        } finally {
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
        }
        return hasil;
    }

    private static Varian tulisVarian(ZipFile zf, List<String> jalan, File keluar, String nama) {
        Varian v = new Varian();
        v.nama = nama;
        try {
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(keluar));
            try {
                for (int i = 0; i < jalan.size(); i++) {
                    String j = jalan.get(i);
                    ZipEntry asal = zf.getEntry(j);
                    if (asal == null) continue;
                    salinEntri(zip, zf, asal, jalanApkDariBundel(j), j.endsWith(".so"));
                }
            } finally {
                zip.close();
            }
            v.berkas = keluar.getPath();
            v.ukuran = keluar.length();
        } catch (Throwable t) {
            v.ukuran = 0;
        }
        return v;
    }

    // ------------------------------------------------------- kunci proyek

    /** Muat kunci penandatanganan bawaan proyek (tanda-tangan-bawaan/kunci.p12). */
    public static KunciProyek muatKunciProyek(File dirProyek) {
        try {
            if (dirProyek == null) return null;
            File folder = new File(dirProyek, "tanda-tangan-bawaan");
            File p12 = new File(folder, "kunci.p12");
            File sandiF = new File(folder, "sandi.txt");
            if (!p12.isFile() || !sandiF.isFile()) return null;
            String sandi = new String(bacaBerkas(sandiF), "UTF-8").trim();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            FileInputStream in = new FileInputStream(p12);
            try {
                ks.load(in, sandi.toCharArray());
            } finally {
                in.close();
            }
            Enumeration<String> en = ks.aliases();
            if (!en.hasMoreElements()) return null;
            String alias = en.nextElement();
            KunciProyek k = new KunciProyek();
            k.kunci = (PrivateKey) ks.getKey(alias, sandi.toCharArray());
            k.sertifikat = (X509Certificate) ks.getCertificate(alias);
            k.sandi = sandi;
            if (k.kunci == null || k.sertifikat == null) return null;
            return k;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Buat kunci penandatanganan bawaan proyek (bila belum ada) dengan
     * tata letak yang sama seperti Penandatangan: kunci.p12 + sandi.txt
     * + info.txt di folder tanda-tangan-bawaan. Kunci yang sama dipakai
     * untuk APK proyek, sehingga tanda tangan AAB cocok dengan APK.
     */
    public static KunciProyek buatKunciProyek(File dirProyek) {
        try {
            KunciProyek ada = muatKunciProyek(dirProyek);
            if (ada != null) return ada;
            if (dirProyek == null) return null;
            File folder = new File(dirProyek, "tanda-tangan-bawaan");
            folder.mkdirs();
            String sandi = sandiAcak();
            byte[] p12 = Penandatangan.buatP12("bawaan", sandi,
                    "Create Aplikasi", "", "", "", "", "", 25);
            tulisBerkas(new File(folder, "kunci.p12"), p12);
            tulisBerkas(new File(folder, "sandi.txt"), sandi.getBytes("UTF-8"));
            tulisBerkas(new File(folder, "info.txt"),
                    ("Tanda tangan bawaan khusus proyek ini.\n"
                            + "Dibuat otomatis oleh Create Aplikasi supaya tanda tangan "
                            + "bawaan proyek ini berbeda dari proyek lain.\n"
                            + "Jangan hapus folder ini: tanpa kunci ini, APK baru "
                            + "tidak bisa menimpa APK lama proyek yang sama.\n").getBytes("UTF-8"));
            return muatKunciProyek(dirProyek);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sandiAcak() {
        String aksara = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder(20);
        for (int i = 0; i < 20; i++) b.append(aksara.charAt(r.nextInt(aksara.length())));
        return b.toString();
    }

    static byte[] bacaBerkas(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        } finally {
            in.close();
        }
        return buf.toByteArray();
    }

    static void tulisBerkas(File f, byte[] data) throws Exception {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    // ------------------------------------------------------- tanda tangan bundel

    /**
     * Tandatangani bundel dengan tanda tangan JAR (v1, skema yang sama
     * dengan jarsigner untuk AAB yang diunggah ke Play Console) memakai
     * ApkSigner yang sudah dibundel di aplikasi (hanya v1; skema v2/v3
     * khusus APK dan tidak berlaku untuk AAB). Mengembalikan berkas
     * tujuan (null bila gagal).
     */
    public static File tandatanganiBundel(File aab, PrivateKey kunci,
                                         X509Certificate sertifikat, File keluar) {
        try {
            if (aab == null || !aab.isFile() || kunci == null || sertifikat == null
                    || keluar == null) return null;
            if (keluar.getParentFile() != null) keluar.getParentFile().mkdirs();
            ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("pembuat", kunci,
                    Collections.singletonList(sertifikat)).build();
            new ApkSigner.Builder(Collections.singletonList(sc))
                    .setInputApk(aab)
                    .setOutputApk(keluar)
                    .setMinSdkVersion(21)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(false)
                    .setV3SigningEnabled(false)
                    .build()
                    .sign();
            return keluar;
        } catch (Throwable t) {
            return null;
        }
    }

    /** SHA-256 bita (bukan teks). */
    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data == null ? new byte[0] : data);
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    static String keHeksa(byte[] data) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            if (v < 16) b.append('0');
            b.append(Integer.toHexString(v));
        }
        return b.toString();
    }

    /** Dekode Base64 standar (tanpa pustaka luar, supaya jalan di semua API). */
    static byte[] decodeBase64(String teks) {
        if (teks == null) return new byte[0];
        String t = teks.trim();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int akum = 0;
        int bit = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            int nilai;
            if (c >= 'A' && c <= 'Z') nilai = c - 'A';
            else if (c >= 'a' && c <= 'z') nilai = c - 'a' + 26;
            else if (c >= '0' && c <= '9') nilai = c - '0' + 52;
            else if (c == '+') nilai = 62;
            else if (c == '/') nilai = 63;
            else continue;
            akum = (akum << 6) | nilai;
            bit += 6;
            if (bit >= 8) {
                bit -= 8;
                out.write((akum >> bit) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    static boolean sama(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /**
     * Urai berkas MANIFEST.MF / .SF gaya jarsigner menjadi peta
     * atribut per bagian. Bagian pertama = bagian utama (nama "").
     * Baris lanjutan (diawali spasi) digabung ke nilai sebelumnya.
     */
    static Map<String, Map<String, String>> uraiManifes(byte[] data) {
        Map<String, Map<String, String>> hasil = new LinkedHashMap<String, Map<String, String>>();
        if (data == null) return hasil;
        String teks;
        try {
            teks = new String(data, "UTF-8");
        } catch (Throwable t) {
            return hasil;
        }
        String[] baris = teks.split("\r\n|\r|\n");
        String namaBagian = "";
        Map<String, String> bagian = new LinkedHashMap<String, String>();
        hasil.put(namaBagian, bagian);
        String kunciTerakhir = null;
        for (int i = 0; i < baris.length; i++) {
            String b = baris[i];
            if (b.length() == 0) {
                namaBagian = "";
                bagian = new LinkedHashMap<String, String>();
                kunciTerakhir = null;
                continue;
            }
            if (b.charAt(0) == ' ' && kunciTerakhir != null) {
                bagian.put(kunciTerakhir, bagian.get(kunciTerakhir) + b.substring(1));
                continue;
            }
            int titik = b.indexOf(':');
            if (titik < 0) continue;
            String kunci = b.substring(0, titik).trim();
            String nilai = b.substring(titik + 1).trim();
            if ("Name".equals(kunci)) {
                namaBagian = nilai;
                bagian = hasil.get(namaBagian);
                if (bagian == null) {
                    bagian = new LinkedHashMap<String, String>();
                    hasil.put(namaBagian, bagian);
                }
                kunciTerakhir = null;
            } else {
                bagian.put(kunci, nilai);
                kunciTerakhir = kunci;
            }
        }
        return hasil;
    }

    // ---- pembaca DER minimal (untuk mengambil tanda tangan dari blok PKCS#7 .RSA)

    static class Der {
        int tag;
        int awalIsi;
        int akhir;
    }

    static Der bacaDer(byte[] b, int off) {
        if (b == null || off < 0 || off + 2 > b.length) return null;
        Der d = new Der();
        d.tag = b[off] & 0xFF;
        int p = off + 1;
        int panjang = b[p] & 0xFF;
        p++;
        if ((panjang & 0x80) != 0) {
            int jumlah = panjang & 0x7F;
            if (jumlah <= 0 || jumlah > 4 || p + jumlah > b.length) return null;
            panjang = 0;
            for (int i = 0; i < jumlah; i++) panjang = (panjang << 8) | (b[p++] & 0xFF);
        }
        if (panjang < 0 || p + panjang > b.length) return null;
        d.awalIsi = p;
        d.akhir = p + panjang;
        return d;
    }

    static List<Der> anakDer(byte[] b, Der induk) {
        List<Der> hasil = new ArrayList<Der>();
        int p = induk.awalIsi;
        while (p < induk.akhir) {
            Der c = bacaDer(b, p);
            if (c == null) return hasil;
            hasil.add(c);
            p = c.akhir;
        }
        return hasil;
    }

    /**
     * Ambil bita tanda tangan RSA dari blok PKCS#7 (.RSA): telusuri
     * ContentInfo -> SignedData -> signerInfos -> SignerInfo ->
     * OCTET STRING terakhir. Null bila ada atribut terautentikasi
     * (maka data yang ditandatangani bukan berkas .SF apa adanya).
     */
    static byte[] tandaDariPkcs7(byte[] rsa) {
        try {
            Der akar = bacaDer(rsa, 0);
            if (akar == null || akar.tag != 0x30) return null;
            List<Der> anakAkar = anakDer(rsa, akar);
            if (anakAkar.size() < 2) return null;
            Der pembungkus = anakAkar.get(1);
            if (pembungkus.tag != 0xA0) return null;
            Der signedData = bacaDer(rsa, pembungkus.awalIsi);
            if (signedData == null || signedData.tag != 0x30) return null;
            List<Der> anakSd = anakDer(rsa, signedData);
            Der signerInfos = null;
            for (int i = anakSd.size() - 1; i >= 0; i--) {
                if (anakSd.get(i).tag == 0x31) {
                    signerInfos = anakSd.get(i);
                    break;
                }
            }
            if (signerInfos == null) return null;
            List<Der> infos = anakDer(rsa, signerInfos);
            if (infos.isEmpty()) return null;
            List<Der> anakInfo = anakDer(rsa, infos.get(0));
            Der tanda = null;
            for (int i = 0; i < anakInfo.size(); i++) {
                Der c = anakInfo.get(i);
                if (c.tag == 0xA0) return null; // atribut terautentikasi
                if (c.tag == 0x04) tanda = c;
            }
            if (tanda == null) return null;
            byte[] hasil = new byte[tanda.akhir - tanda.awalIsi];
            System.arraycopy(rsa, tanda.awalIsi, hasil, 0, hasil.length);
            return hasil;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Periksa rantai tanda tangan JAR sebuah bundel tanpa jarsigner:
     * (1) tiap digest entri di MANIFEST.MF cocok dengan isi entri di
     * zip; (2) digest MANIFEST.MF di berkas .SF cocok; (3) tanda
     * tangan RSA di berkas .RSA sah atas bita .SF memakai sertifikat
     * yang tertanam di blok PKCS#7 itu sendiri.
     */
    public static HasilVerifikasi verifikasiTandaTanganBundel(File aab) {
        HasilVerifikasi h = new HasilVerifikasi();
        h.rincian = "Bundel tidak ditandatangani.";
        if (aab == null || !aab.isFile()) {
            h.rincian = "Berkas bundel tidak ditemukan.";
            return h;
        }
        ZipFile zf;
        try {
            zf = new ZipFile(aab);
        } catch (Throwable t) {
            h.rincian = "Bundel bukan arsip zip yang sah.";
            return h;
        }
        try {
            ZipEntry manifes = zf.getEntry("META-INF/MANIFEST.MF");
            if (manifes == null) return h;
            ZipEntry sf = null;
            ZipEntry rsa = null;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (n.startsWith("META-INF/") && n.endsWith(".SF")) sf = e;
                if (n.startsWith("META-INF/") && n.endsWith(".RSA")) rsa = e;
            }
            if (sf == null || rsa == null) {
                h.rincian = "MANIFEST.MF ada tetapi berkas .SF/.RSA tidak lengkap.";
                return h;
            }
            h.ditandatangani = true;
            byte[] bManifes = bacaEntri(zf, manifes);
            byte[] bSf = bacaEntri(zf, sf);
            byte[] bRsa = bacaEntri(zf, rsa);
            if (bManifes == null || bSf == null || bRsa == null) {
                h.rincian = "Berkas tanda tangan (MANIFEST.MF/.SF/.RSA) tidak bisa dibaca.";
                return h;
            }
            Map<String, Map<String, String>> m = uraiManifes(bManifes);
            boolean manifesOk = true;
            StringBuilder masalah = new StringBuilder();
            for (Map.Entry<String, Map<String, String>> entri : m.entrySet()) {
                String nama = entri.getKey();
                if (nama.length() == 0) continue;
                String digest = entri.getValue().get("SHA-256-Digest");
                if (digest == null) continue;
                ZipEntry ze = zf.getEntry(nama);
                if (ze == null) {
                    manifesOk = false;
                    masalah.append("Entri di manifest tidak ada di bundel: ").append(nama).append(". ");
                    continue;
                }
                byte[] isiEntri = bacaEntri(zf, ze);
                if (isiEntri == null) {
                    manifesOk = false;
                    masalah.append("Entri tidak bisa dibaca: ").append(nama).append(". ");
                    continue;
                }
                if (!sama(decodeBase64(digest), sha256(isiEntri))) {
                    manifesOk = false;
                    masalah.append("Digest tidak cocok: ").append(nama).append(". ");
                }
            }
            Map<String, Map<String, String>> s = uraiManifes(bSf);
            Map<String, String> utamaSf = s.get("");
            String digestManifes = utamaSf == null ? null : utamaSf.get("SHA-256-Digest-Manifest");
            if (digestManifes == null || !sama(decodeBase64(digestManifes), sha256(bManifes))) {
                manifesOk = false;
                masalah.append("Digest MANIFEST.MF di berkas .SF tidak cocok. ");
            }
            h.manifesOk = manifesOk;
            boolean tandaOk = false;
            try {
                java.util.Collection<? extends java.security.cert.Certificate> certs =
                        CertificateFactory.getInstance("X.509")
                                .generateCertificates(new java.io.ByteArrayInputStream(bRsa));
                X509Certificate cert = null;
                for (java.security.cert.Certificate c : certs) {
                    if (c instanceof X509Certificate) {
                        cert = (X509Certificate) c;
                        break;
                    }
                }
                if (cert != null) {
                    h.sidikJariSertifikat = keHeksa(sha256(cert.getEncoded()));
                    byte[] tanda = tandaDariPkcs7(bRsa);
                    if (tanda != null) {
                        Signature sig = Signature.getInstance("SHA256withRSA");
                        sig.initVerify(cert.getPublicKey());
                        sig.update(bSf);
                        tandaOk = sig.verify(tanda);
                    }
                }
            } catch (Throwable t) {
                tandaOk = false;
            }
            h.tandaTanganOk = tandaOk;
            if (manifesOk && tandaOk) {
                h.rincian = "Tanda tangan JAR sah. Sertifikat SHA-256: " + h.sidikJariSertifikat + ".";
            } else {
                h.rincian = "Tanda tangan tidak sah. " + masalah.toString()
                        + (tandaOk ? "" : "Verifikasi RSA atas berkas .SF gagal.");
            }
            return h;
        } finally {
            try {
                zf.close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------- perkiraan lama + audit

    /**
     * Simulasikan pecahan ukuran unduhan: tiap ABI mendapat porsi
     * pustaka aslinya + bagian bersama; tiap bahasa mendapat porsi
     * sumber dayanya. Angka adalah perkiraan dari ukuran folder.
     * CARA LAMA — untuk ukuran nyata, pakai bangunPecahan().
     */
    public static List<Varian> simulasiPecahan(long ukuranBersama, long ukuranLibPerAbi,
                                               long ukuranResPerBahasa,
                                               String[] abi, String[] bahasa) {
        List<Varian> hasil = new ArrayList<Varian>();
        if (abi != null) {
            for (int i = 0; i < abi.length; i++) {
                Varian v = new Varian();
                v.nama = abi[i];
                v.ukuran = ukuranBersama + ukuranLibPerAbi;
                hasil.add(v);
            }
        }
        if (bahasa != null) {
            for (int i = 0; i < bahasa.length; i++) {
                Varian v = new Varian();
                v.nama = "bahasa-" + bahasa[i];
                v.ukuran = ukuranBersama + ukuranResPerBahasa;
                hasil.add(v);
            }
        }
        return hasil;
    }

    /** Audit kesiapan Play dari teks manifest + info build. */
    public static List<Temuan> auditPlay(String manifestXml, int targetSdk,
                                         boolean adaLib64, boolean adaLib32) {
        List<Temuan> hasil = new ArrayList<Temuan>();
        if (targetSdk < 35) {
            // Play Store mewajibkan target 35 atau lebih sejak Agustus 2025;
            // target 34 atau lebih lama ditolak saat unggah baru/pembaruan.
            hasil.add(new Temuan("Tinggi",
                    "Target SDK " + targetSdk + " di bawah 35; Play Store mewajibkan target 35 atau lebih."));
        }
        if (!adaLib64) {
            hasil.add(new Temuan("Tinggi",
                    "Tidak ada pustaka asli 64-bit (arm64-v8a); Play Store mewajibkan 64-bit bila ada pustaka 32-bit."));
        }
        if (manifestXml != null) {
            if (manifestXml.contains("android.permission.READ_EXTERNAL_STORAGE")
                    || manifestXml.contains("android.permission.WRITE_EXTERNAL_STORAGE")) {
                hasil.add(new Temuan("Sedang",
                        "Izin penyimpanan eksternal terdaftar; Play Store membatasi pemakaiannya sejak Android 11."));
            }
            if (manifestXml.contains("android.permission.REQUEST_INSTALL_PACKAGES")) {
                hasil.add(new Temuan("Sedang",
                        "Izin pemasangan paket terdaftar; Play Store meminta pernyataan kebijakan untuk izin ini."));
            }
            if (!manifestXml.contains("android:targetSdkVersion")) {
                hasil.add(new Temuan("Tinggi",
                        "Manifest tidak menyebut targetSdkVersion."));
            }
        }
        if (adaLib32 && !adaLib64) {
            hasil.add(new Temuan("Tinggi",
                    "Hanya ada pustaka 32-bit; tambahkan arm64-v8a sebelum mengunggah."));
        }
        return hasil;
    }

    /** Ringkasan hasil dalam bahasa Indonesia. */
    public static String ringkasan(long ukuranBundel, List<Varian> varian,
                                   List<Temuan> temuan) {
        int t = temuan == null ? 0 : temuan.size();
        int v = varian == null ? 0 : varian.size();
        long terkecil = -1;
        if (varian != null) {
            for (int i = 0; i < varian.size(); i++) {
                if (terkecil < 0 || varian.get(i).ukuran < terkecil) {
                    terkecil = varian.get(i).ukuran;
                }
            }
        }
        return "Ukuran unduhan diperkirakan: "
                + (terkecil < 0 ? "tidak diketahui" : terkecil + " bita")
                + " (terkecil dari " + v + " varian). Pemeriksaan Play: "
                + t + " temuan. Ukuran bundel: " + ukuranBundel + " bita.";
    }

    /** Ringkasan pecahan nyata: ukuran tiap varian APK hasil pemecahan. */
    public static String ringkasanPecahan(List<Varian> varian) {
        if (varian == null || varian.isEmpty()) return "Tidak ada varian pecahan.";
        StringBuilder b = new StringBuilder("Ukuran pecahan nyata (hasil pemecahan bundel):");
        for (int i = 0; i < varian.size(); i++) {
            b.append("\n- ").append(varian.get(i).nama).append(": ")
                    .append(varian.get(i).ukuran).append(" bita");
        }
        return b.toString();
    }
}
