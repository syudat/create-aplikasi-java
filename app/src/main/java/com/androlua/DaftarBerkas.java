package com.androlua;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Daftar berkas proyek untuk EditorActivity (port Java dari jembatan
 * daftarBerkas di main.lua, Tahap 2C).
 *
 * Mengembalikan SATU string: baris 1 = tipe proyek, baris 2.. = jalur
 * relatif berkas (urutan kumpulBerkas; berkas pertama = yang dibuka
 * otomatis). Logika sama persis dengan Lua: jenis proyek dibaca dari
 * init.lua (InfoProyek.tipe, bawaan "java"), ekstensi yang boleh dibuka
 * disaring per jenis proyek (TIPE_EXT), berkas dikumpulkan rekursif
 * (folder "bin" dilewati), urutan abjad tanpa memperhatikan huruf
 * besar/kecil (listDir di Lua).
 *
 * Ronde 3: jembatan Lua daftarBerkas dihapus bersama mesin Lua.
 * Jenis "lua" tidak ada lagi (proyek Lua lama disembunyikan dari daftar
 * dan dibuka lewat Lua Manager); tipe tak dikenal memakai ekstensi Java.
 *
 * Murni Java (java.io, tanpa API Android) supaya bisa diuji ulang di JRE.
 */
public class DaftarBerkas {

    /** Daftar berkas proyek (format sama seperti jembatan Lua). */
    public static String daftar(File dirProyek) {
        String tipe = InfoProyek.tipe(dirProyek);
        List<String> semua = new ArrayList<String>();
        kumpulBerkas(dirProyek, "", semua, tipe);
        StringBuilder sb = new StringBuilder();
        sb.append(tipe);
        for (int i = 0; i < semua.size(); i++) {
            sb.append('\n').append(semua.get(i));
        }
        return sb.toString();
    }

    /** Ekstensi berkas yang boleh dibuka di editor, sesuai jenis proyek. */
    static boolean cocokEkstensi(String nama, String tipe) {
        if (nama == null) return false;
        if ("kotlin".equals(tipe) || "compose".equals(tipe)) {
            return nama.endsWith(".kt") || nama.endsWith(".xml");
        }
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            return nama.endsWith(".htm") || nama.endsWith(".html")
                    || nama.endsWith(".css") || nama.endsWith(".js")
                    || nama.endsWith(".xml") || nama.endsWith(".java");
        }
        if ("python".equals(tipe)) {
            // Python: kode .py + host Java + layout XML.
            return nama.endsWith(".py") || nama.endsWith(".java")
                    || nama.endsWith(".xml");
        }
        // java + tipe tak dikenal (termasuk proyek Lua lama): .java/.xml.
        return nama.endsWith(".java") || nama.endsWith(".xml");
    }

    /** Kumpulkan berkas yang cocok secara rekursif (lewati folder "bin"). */
    private static void kumpulBerkas(File dir, String prefix, List<String> out,
                                     String tipe) {
        if (dir == null) return;
        File jalan = prefix.isEmpty() ? dir : new File(dir, prefix);
        String[] nama = jalan.list();
        if (nama == null) return;
        Arrays.sort(nama, String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < nama.length; i++) {
            String n = nama[i];
            String rel = prefix.isEmpty() ? n : prefix + "/" + n;
            File f = new File(dir, rel);
            if (f.isDirectory()) {
                if (!"bin".equals(n)) kumpulBerkas(dir, rel, out, tipe);
            } else if (cocokEkstensi(n, tipe)) {
                out.add(rel);
            }
        }
    }
}
