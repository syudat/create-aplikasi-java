package com.androlua;

import android.app.Activity;
import android.widget.Toast;
import java.io.File;
import java.util.Locale;

/**
 * Pembuat proyek bersama (Tahap 3D): dipakai VoiceActivity, AiActivity,
 * dan MarketActivity untuk membuat proyek — pemanggilan yang sama persis seperti
 * CreateProjectActivity (nama, paket, "1.0", kode 1, min 21, target 33,
 * tipe, skrip kosong, tanpa pisah arsitektur, tanpa tanda tangan,
 * hanya izin INTERNET, tanpa lisensi).
 *
 * Ronde 3: jembatan Lua buatProyek dihapus bersama mesin Lua; pembuatan
 * proyek kini murni Java lewat BuatProyek.buat. Tipe "lua" dari pemanggil
 * lama dipetakan ke "java" (keputusan pengguna 2026-11-17: fitur pembuat
 * proyek Lua diubah jadi membuat proyek Java).
 *
 * Metode paketDariNama() dan namaUnik() murni Java supaya bisa diuji
 * ulang di JRE; buat() memakai API Android (Activity + Toast).
 */
public class BuatProyekHelper {

    /**
     * Folder proyek (Download/project). Dihitung lewat metode (bukan
     * konstanta statis) supaya memuat kelas ini di JRE tidak menyentuh
     * android.os.Environment — paketDariNama()/namaUnik() bisa diuji
     * di JRE tanpa Android.
     */
    public static String root() {
        return android.os.Environment.getExternalStorageDirectory().getPath()
                + "/Download/project";
    }

    /**
     * Menurunkan nama paket dari nama proyek ("Kalkulator Saya" ->
     * "com.buat.kalkulatorsaya"). Hasil selalu cocok dengan regex paket
     * di CreateProjectActivity.
     */
    public static String paketDariNama(String nama) {
        String t = nama == null ? "" : nama.toLowerCase(new Locale("id", "ID"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        if (sb.length() == 0) sb.append("aplikasi");
        if (sb.charAt(0) >= '0' && sb.charAt(0) <= '9') sb.insert(0, "app");
        return "com.buat." + sb.toString();
    }

    /**
     * Nama proyek yang belum dipakai di folder proyek (menambah " 2",
     * " 3", ... bila sudah ada). Memakai File root supaya bisa diuji
     * di JRE dengan folder sementara.
     */
    public static String namaUnik(File dirRoot, String nama) {
        if (dirRoot == null) return nama;
        String calon = nama;
        int nomor = 2;
        while (new File(dirRoot, calon).exists()) {
            calon = nama + " " + nomor;
            nomor++;
        }
        return calon;
    }

    /**
     * Membuat proyek lewat BuatProyek Java murni, lalu menerapkan template
     * bila templateId bukan null/"kosong". Mengembalikan folder proyek
     * (jalur absolut) bila berhasil, null bila gagal (pesan galat sudah
     * ditampilkan lewat toast). Tipe "lua" dipetakan ke "java" (Lua dihapus).
     */
    public static String buat(Activity ctx, String nama, String tipe, String templateId) {
        String namaUnik = namaUnik(new File(root()), nama);
        String paket = paketDariNama(namaUnik);
        // Ronde 3: Lua dihapus — proyek Lua lama diubah jadi proyek Java.
        if ("lua".equals(tipe)) tipe = "java";
        TemplateDasar.Param param = new TemplateDasar.Param();
        param.name = namaUnik;
        param.packageName = paket;
        param.ver = "1.0";
        param.code = 1;
        param.min = 21;
        param.target = 33;
        param.tipe = tipe;
        param.skripHtml = null;
        param.split = false;
        param.signing = false;
        param.lisensi = false;
        param.wa = "";
        param.seed = "";
        java.util.Map<String, Boolean> permsMap =
                new java.util.LinkedHashMap<String, Boolean>();
        permsMap.put("INTERNET", Boolean.TRUE);
        param.perms = permsMap;
        String s;
        try {
            s = BuatProyek.buat(param);
        } catch (Throwable t) {
            Toast.makeText(ctx, "Gagal membuat proyek: " + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        if (s == null || !s.startsWith("OK:")) {
            String pesan = (s != null && s.startsWith("GAGAL:")) ? s.substring(6) : s;
            Toast.makeText(ctx, pesan == null ? "Gagal membuat proyek." : pesan,
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        String dir = s.substring("OK:".length());
        if (templateId != null && !"kosong".equals(templateId)) {
            String galat = TemplateProyek.terapkan(new File(dir), templateId, paket, namaUnik);
            if (galat != null) {
                Toast.makeText(ctx, "Template gagal diterapkan: " + galat,
                        Toast.LENGTH_SHORT).show();
            }
        }
        return dir;
    }
}
