package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Integrasi Media Sosial (Tahap 3G).
 *
 * Membuat kode berbagi sungguhan untuk aplikasi hasil, untuk 3 platform
 * (WhatsApp, Facebook, Telegram) dan 6 tipe proyek (Lua, HTML, Situs Web,
 * Java, Kotlin, Compose). Kode disisipkan ke berkas utama proyek
 * (main.lua / MainActivity.java / MainActivity.kt / index.html) oleh
 * tambahBagikan(); bila kode berbagi sudah ada, tidak ditambah dua kali.
 *
 * WhatsApp: Intent ACTION_SEND dengan paket com.whatsapp, cadangan
 * chooser bila WhatsApp tidak terpasang. Facebook: ACTION_SEND teks
 * biasa (chooser). Telegram: Intent ACTION_SEND dengan paket
 * org.telegram.messenger, cadangan chooser. HTML/Situs Web: membuka
 * tautan berbagi (wa.me / facebook sharer / t.me/share) lewat
 * window.open.
 *
 * Murni Java (java.io, tanpa API Android) supaya bisa diuji ulang di JRE.
 * Kode hasil adalah kode proyek pengguna — bukan kode IDE.
 */
public class MediaSosial {

    /** Ketiga platform berbagi. */
    public static final String[] PLATFORM = {"WhatsApp", "Facebook", "Telegram"};

    /** Teks contoh untuk uji berbagi dari IDE. */
    public static String teksContoh(String namaAplikasi) {
        return TeksUI.t("Coba aplikasi ") + namaAplikasi + TeksUI.t(" buatan saya! Dibuat dengan Create Aplikasi.");
    }

    /**
     * Kode berbagi untuk satu platform dan satu tipe proyek.
     * Tipe: "lua", "html", "situs", "java", "kotlin", "compose", "python"
     * (Python memakai kode Java karena host-nya Java).
     */
    public static String kodeBagikan(String tipe, String platform, String namaAplikasi) {
        if (tipe == null) tipe = "lua";
        if (platform == null) platform = "WhatsApp";
        if ("html".equals(tipe) || "situs".equals(tipe)) return kodeHtml(platform);
        if ("lua".equals(tipe)) return kodeLua(platform);
        if ("kotlin".equals(tipe) || "compose".equals(tipe)) return kodeKotlin(platform);
        return kodeJava(platform);
    }

    /** Berkas utama proyek menurut tipenya (relatif terhadap akar proyek). */
    public static File berkasUtama(File dirProyek, String tipe) {
        if (dirProyek == null) return null;
        if ("lua".equals(tipe)) return new File(dirProyek, "main.lua");
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            return new File(dirProyek, "app/src/main/assets/index.html");
        }
        if ("kotlin".equals(tipe) || "compose".equals(tipe)) {
            File[] cari = cariMainActivity(dirProyek, ".kt");
            return cari.length > 0 ? cari[0] : null;
        }
        File[] cari = cariMainActivity(dirProyek, ".java");
        return cari.length > 0 ? cari[0] : null;
    }

    /**
     * Menyisipkan kode berbagi ke berkas utama proyek. Mengembalikan
     * null bila berhasil, atau pesan galat (bahasa Indonesia).
     * Bila kode berbagi platform itu sudah ada, mengembalikan pesan
     * bahwa kode sudah ada (bukan galat fatal — pemanggil boleh
     * menampilkannya sebagai info).
     */
    public static String tambahBagikan(File dirProyek, String tipe,
                                       String platform, String namaAplikasi) {
        File utama = berkasUtama(dirProyek, tipe);
        if (utama == null || !utama.isFile()) {
            return TeksUI.t("Berkas utama proyek tidak ditemukan.");
        }
        String isi = baca(utama);
        if (isi == null) return TeksUI.t("Tidak bisa membaca berkas utama proyek.");
        String penanda = penanda(platform);
        if (isi.contains(penanda)) {
            return TeksUI.t("Kode berbagi ") + platform + TeksUI.t(" sudah ada di proyek ini.");
        }
        String kode = kodeBagikan(tipe, platform, namaAplikasi);
        String baru;
        if ("html".equals(tipe) || "situs".equals(tipe)) {
            int pos = isi.lastIndexOf("</body>");
            if (pos >= 0) {
                baru = isi.substring(0, pos) + kode + "\n" + isi.substring(pos);
            } else {
                baru = isi + "\n" + kode + "\n";
            }
        } else if ("lua".equals(tipe)) {
            baru = isi + "\n" + kode;
        } else {
            // Java/Kotlin/Compose: sisipkan sebelum kurung kurawal penutup kelas.
            int pos = isi.lastIndexOf("}");
            if (pos < 0) return TeksUI.t("Berkas utama proyek tidak berisi penutup kelas.");
            baru = isi.substring(0, pos) + kode + isi.substring(pos);
            // Metode berbagi memakai Intent — pastikan impornya ada, karena
            // template MainActivity IDE tidak mengimpor android.content.Intent.
            baru = pastikanImpor(baru, tipe);
        }
        return tulis(utama, baru);
    }

    /**
     * Pastikan berkas Java/Kotlin mengimpor android.content.Intent.
     * Disisipkan sesudah baris package bila ada, atau di awal berkas.
     * Bila impor sudah ada (mis. proyek sudah memakainya), tidak diubah.
     */
    private static String pastikanImpor(String isi, String tipe) {
        if (isi.contains("android.content.Intent")) return isi;
        String impor = "kotlin".equals(tipe) || "compose".equals(tipe)
                ? "import android.content.Intent"
                : "import android.content.Intent;";
        int awalPaket = isi.indexOf("package ");
        if (awalPaket >= 0) {
            int akhirBaris = isi.indexOf("\n", awalPaket);
            if (akhirBaris >= 0) {
                return isi.substring(0, akhirBaris + 1) + impor + "\n"
                        + isi.substring(akhirBaris + 1);
            }
        }
        return impor + "\n" + isi;
    }

    /** Penanda unik tiap platform (untuk mendeteksi kode sudah ada). */
    private static String penanda(String platform) {
        if ("WhatsApp".equals(platform)) return "bagikanWhatsApp";
        if ("Facebook".equals(platform)) return "bagikanFacebook";
        return "bagikanTelegram";
    }

    private static String kodeJava(String platform) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n    // Berbagi ke ").append(platform)
                .append(" — dibuat oleh Create Aplikasi.\n");
        if ("WhatsApp".equals(platform)) {
            sb.append("    public void bagikanWhatsApp(String teks) {\n");
            sb.append("        Intent kirim = new Intent(Intent.ACTION_SEND);\n");
            sb.append("        kirim.setType(\"text/plain\");\n");
            sb.append("        kirim.putExtra(Intent.EXTRA_TEXT, teks);\n");
            sb.append("        kirim.setPackage(\"com.whatsapp\");\n");
            sb.append("        try {\n");
            sb.append("            startActivity(kirim);\n");
            sb.append("        } catch (Exception e) {\n");
            sb.append("            kirim.setPackage(null);\n");
            sb.append("            startActivity(Intent.createChooser(kirim, \"Bagikan ke WhatsApp\"));\n");
            sb.append("        }\n");
            sb.append("    }\n");
        } else if ("Facebook".equals(platform)) {
            sb.append("    public void bagikanFacebook(String teks) {\n");
            sb.append("        Intent kirim = new Intent(Intent.ACTION_SEND);\n");
            sb.append("        kirim.setType(\"text/plain\");\n");
            sb.append("        kirim.putExtra(Intent.EXTRA_TEXT, teks);\n");
            sb.append("        startActivity(Intent.createChooser(kirim, \"Bagikan ke Facebook\"));\n");
            sb.append("    }\n");
        } else {
            sb.append("    public void bagikanTelegram(String teks) {\n");
            sb.append("        Intent kirim = new Intent(Intent.ACTION_SEND);\n");
            sb.append("        kirim.setType(\"text/plain\");\n");
            sb.append("        kirim.putExtra(Intent.EXTRA_TEXT, teks);\n");
            sb.append("        kirim.setPackage(\"org.telegram.messenger\");\n");
            sb.append("        try {\n");
            sb.append("            startActivity(kirim);\n");
            sb.append("        } catch (Exception e) {\n");
            sb.append("            kirim.setPackage(null);\n");
            sb.append("            startActivity(Intent.createChooser(kirim, \"Bagikan ke Telegram\"));\n");
            sb.append("        }\n");
            sb.append("    }\n");
        }
        return sb.toString();
    }

    private static String kodeKotlin(String platform) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n    // Berbagi ke ").append(platform)
                .append(" — dibuat oleh Create Aplikasi.\n");
        if ("WhatsApp".equals(platform)) {
            sb.append("    fun bagikanWhatsApp(teks: String) {\n");
            sb.append("        val kirim = Intent(Intent.ACTION_SEND).apply {\n");
            sb.append("            type = \"text/plain\"\n");
            sb.append("            putExtra(Intent.EXTRA_TEXT, teks)\n");
            sb.append("            setPackage(\"com.whatsapp\")\n");
            sb.append("        }\n");
            sb.append("        try {\n");
            sb.append("            startActivity(kirim)\n");
            sb.append("        } catch (e: Exception) {\n");
            sb.append("            kirim.setPackage(null)\n");
            sb.append("            startActivity(Intent.createChooser(kirim, \"Bagikan ke WhatsApp\"))\n");
            sb.append("        }\n");
            sb.append("    }\n");
        } else if ("Facebook".equals(platform)) {
            sb.append("    fun bagikanFacebook(teks: String) {\n");
            sb.append("        val kirim = Intent(Intent.ACTION_SEND).apply {\n");
            sb.append("            type = \"text/plain\"\n");
            sb.append("            putExtra(Intent.EXTRA_TEXT, teks)\n");
            sb.append("        }\n");
            sb.append("        startActivity(Intent.createChooser(kirim, \"Bagikan ke Facebook\"))\n");
            sb.append("    }\n");
        } else {
            sb.append("    fun bagikanTelegram(teks: String) {\n");
            sb.append("        val kirim = Intent(Intent.ACTION_SEND).apply {\n");
            sb.append("            type = \"text/plain\"\n");
            sb.append("            putExtra(Intent.EXTRA_TEXT, teks)\n");
            sb.append("            setPackage(\"org.telegram.messenger\")\n");
            sb.append("        }\n");
            sb.append("        try {\n");
            sb.append("            startActivity(kirim)\n");
            sb.append("        } catch (e: Exception) {\n");
            sb.append("            kirim.setPackage(null)\n");
            sb.append("            startActivity(Intent.createChooser(kirim, \"Bagikan ke Telegram\"))\n");
            sb.append("        }\n");
            sb.append("    }\n");
        }
        return sb.toString();
    }

    private static String kodeLua(String platform) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n-- Berbagi ke ").append(platform)
                .append(" — dibuat oleh Create Aplikasi.\n");
        if ("WhatsApp".equals(platform)) {
            sb.append("function bagikanWhatsApp(teks)\n");
            sb.append("  local Intent = luajava.bindClass(\"android.content.Intent\")\n");
            sb.append("  local kirim = Intent(Intent.ACTION_SEND)\n");
            sb.append("  kirim.setType(\"text/plain\")\n");
            sb.append("  kirim.putExtra(Intent.EXTRA_TEXT, teks)\n");
            sb.append("  kirim.setPackage(\"com.whatsapp\")\n");
            sb.append("  local ok = pcall(function() activity.startActivity(kirim) end)\n");
            sb.append("  if not ok then\n");
            sb.append("    kirim.setPackage(nil)\n");
            sb.append("    activity.startActivity(Intent.createChooser(kirim, \"Bagikan ke WhatsApp\"))\n");
            sb.append("  end\n");
            sb.append("end\n");
        } else if ("Facebook".equals(platform)) {
            sb.append("function bagikanFacebook(teks)\n");
            sb.append("  local Intent = luajava.bindClass(\"android.content.Intent\")\n");
            sb.append("  local kirim = Intent(Intent.ACTION_SEND)\n");
            sb.append("  kirim.setType(\"text/plain\")\n");
            sb.append("  kirim.putExtra(Intent.EXTRA_TEXT, teks)\n");
            sb.append("  activity.startActivity(Intent.createChooser(kirim, \"Bagikan ke Facebook\"))\n");
            sb.append("end\n");
        } else {
            sb.append("function bagikanTelegram(teks)\n");
            sb.append("  local Intent = luajava.bindClass(\"android.content.Intent\")\n");
            sb.append("  local kirim = Intent(Intent.ACTION_SEND)\n");
            sb.append("  kirim.setType(\"text/plain\")\n");
            sb.append("  kirim.putExtra(Intent.EXTRA_TEXT, teks)\n");
            sb.append("  kirim.setPackage(\"org.telegram.messenger\")\n");
            sb.append("  local ok = pcall(function() activity.startActivity(kirim) end)\n");
            sb.append("  if not ok then\n");
            sb.append("    kirim.setPackage(nil)\n");
            sb.append("    activity.startActivity(Intent.createChooser(kirim, \"Bagikan ke Telegram\"))\n");
            sb.append("  end\n");
            sb.append("end\n");
        }
        return sb.toString();
    }

    private static String kodeHtml(String platform) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script>\n");
        sb.append("// Berbagi ke ").append(platform)
                .append(" — dibuat oleh Create Aplikasi.\n");
        if ("WhatsApp".equals(platform)) {
            sb.append("function bagikanWhatsApp(teks) {\n");
            sb.append("  window.open('https://wa.me/?text=' + encodeURIComponent(teks), '_blank');\n");
            sb.append("}\n");
        } else if ("Facebook".equals(platform)) {
            sb.append("function bagikanFacebook(teks) {\n");
            sb.append("  window.open('https://www.facebook.com/sharer/sharer.php?u=' + encodeURIComponent(location.href) + '&quote=' + encodeURIComponent(teks), '_blank');\n");
            sb.append("}\n");
        } else {
            sb.append("function bagikanTelegram(teks) {\n");
            sb.append("  window.open('https://t.me/share/url?url=' + encodeURIComponent(location.href) + '&text=' + encodeURIComponent(teks), '_blank');\n");
            sb.append("}\n");
        }
        sb.append("</script>\n");
        return sb.toString();
    }

    private static File[] cariMainActivity(File dirProyek, String akhiran) {
        File dirJava = new File(dirProyek, "app/src/main/java");
        java.util.List<File> hasil = new java.util.ArrayList<File>();
        cariRekursif(dirJava, akhiran, hasil);
        return hasil.toArray(new File[0]);
    }

    private static void cariRekursif(File dir, String akhiran, java.util.List<File> keluar) {
        if (dir == null || !dir.isDirectory()) return;
        File[] daftar = dir.listFiles();
        if (daftar == null) return;
        for (int i = 0; i < daftar.length; i++) {
            File f = daftar[i];
            if (f.isDirectory()) {
                cariRekursif(f, akhiran, keluar);
            } else if (f.getName().equals("MainActivity" + akhiran)) {
                keluar.add(f);
            }
        }
    }

    private static String baca(File f) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), "UTF-8"));
            try {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) sb.append(buf, 0, n);
            } finally {
                r.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String tulis(File target, String isi) {
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(target), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            return null;
        } catch (Exception e) {
            return TeksUI.t("Tidak bisa menulis ") + target.getName() + ": " + e.getMessage();
        }
    }
}
