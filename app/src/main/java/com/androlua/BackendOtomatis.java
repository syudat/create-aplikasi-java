package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pembuat Backend Otomatis (Tahap 3F).
 *
 * Membuat proyek backend SUNGGUHAN (tipe HTML/WebView): satu halaman
 * index.html yang berisi formulir + daftar data, dengan "database"
 * lokal berupa localStorage di WebView (data tersimpan di HP dan bisa
 * dibaca kembali), ditambah berkas backend-klien.js berisi fungsi
 * simpan/ambil yang bisa dipakai aplikasi lain, dan BACA-SAYA.txt yang
 * menjelaskan cara memakainya.
 *
 * Tiga jenis backend: Catatan, Daftar Tugas, Papan Pesan. Backend
 * dibuat di atas proyek HTML biasa (lewat BuatProyekHelper.buat di
 * Activity, lalu terapkanBackend menimpa index.html-nya).
 *
 * KETERBATASAN YANG JUJUR: backend ini lokal (data hanya di HP yang
 * menjalankan aplikasinya), bukan server internet — tidak ada server
 * yang disewa, jadi dua HP tidak bisa berbagi data yang sama. Untuk
 * berbagi data antar HP, backend harus dihosting di server sungguhan.
 *
 * Murni java.io/java.util (tanpa API Android) supaya bisa diuji ulang
 * di JRE biasa; logika JavaScript-nya diuji lewat Node di suite uji.
 */
public class BackendOtomatis {

    /** Satu jenis backend. */
    public static class Jenis {
        public final String id;
        public final String nama;
        public final String penjelasan;
        /** Kunci localStorage ("database" lokal). */
        public final String kunci;
        public Jenis(String id, String nama, String penjelasan, String kunci) {
            this.id = id;
            this.nama = nama;
            this.penjelasan = penjelasan;
            this.kunci = kunci;
        }
    }

    public static final Jenis[] SEMUA = {
        new Jenis("catatan", "Catatan",
                "Simpan catatan singkat (judul + isi) dan baca lagi kapan saja.",
                "ca_backend_catatan"),
        new Jenis("tugas", "Daftar Tugas",
                "Daftar tugas yang bisa dicentang selesai dan dihapus.",
                "ca_backend_tugas"),
        new Jenis("pesan", "Papan Pesan",
                "Papan pesan sederhana: tulis nama dan pesan, semua pesan tampil berurutan.",
                "ca_backend_pesan"),
    };

    public static Jenis cari(String id) {
        if (id == null) return null;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    /**
     * Menerapkan backend ke proyek HTML yang sudah dibuat: menimpa
     * app/src/main/assets/index.html, menulis backend-klien.js dan
     * BACA-SAYA.txt. Mengembalikan null bila berhasil, atau pesan galat.
     */
    public static String terapkanBackend(File dirProyek, String jenisId) {
        Jenis j = cari(jenisId);
        if (j == null) return "Jenis backend tidak dikenal: " + jenisId;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        File assets = new File(dirProyek, "app/src/main/assets");
        assets.mkdirs();
        String galat = tulisBerkas(new File(assets, "index.html"), htmlUntuk(j));
        if (galat != null) return galat;
        galat = tulisBerkas(new File(assets, "backend-klien.js"), klienJsUntuk(j));
        if (galat != null) return galat;
        galat = tulisBerkas(new File(dirProyek, "BACA-SAYA.txt"), bacaSayaUntuk(j));
        if (galat != null) return galat;
        return null;
    }

    /** Isi index.html untuk jenis backend (halaman kerja + UI). */
    public static String htmlUntuk(Jenis j) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"id\">\n<head>\n<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<title>").append(j.nama).append(" - Backend</title>\n");
        sb.append("<style>body{font-family:sans-serif;margin:16px} "
                + "input,textarea,button{font-size:16px;margin:4px 0;padding:8px;width:100%;box-sizing:border-box} "
                + "button{background:#2196F3;color:#fff;border:0;border-radius:4px} "
                + "li{margin:8px 0;padding:8px;border:1px solid #ccc;border-radius:4px;list-style:none} "
                + "ul{padding:0}</style>\n</head>\n<body>\n");
        sb.append("<h1>").append(j.nama).append("</h1>\n");
        sb.append("<p>").append(j.penjelasan).append(" Data tersimpan di HP ini (localStorage).</p>\n");
        sb.append("<script src=\"backend-klien.js\"></script>\n");
        if ("catatan".equals(j.id)) {
            sb.append("<input id=\"judul\" placeholder=\"Judul catatan\">\n");
            sb.append("<textarea id=\"isi\" placeholder=\"Isi catatan\"></textarea>\n");
            sb.append("<button onclick=\"tambahCatatan()\">Simpan Catatan</button>\n");
            sb.append("<ul id=\"daftar\"></ul>\n<script>\n");
            sb.append("function muat(){var d=ambilCatatan();var u=document.getElementById('daftar');u.innerHTML='';"
                    + "for(var i=0;i<d.length;i++){var li=document.createElement('li');"
                    + "li.textContent=d[i].judul+' — '+d[i].isi+' ';var b=document.createElement('button');"
                    + "b.textContent='Hapus';b.onclick=(function(n){return function(){hapusCatatan(n);muat();};})(i);"
                    + "li.appendChild(b);u.appendChild(li);}}\n");
            sb.append("function tambahCatatan(){var j=document.getElementById('judul').value;"
                    + "var s=document.getElementById('isi').value;if(!j&&!s)return;"
                    + "simpanCatatan(j,s);document.getElementById('judul').value='';"
                    + "document.getElementById('isi').value='';muat();}\nmuat();\n</script>\n");
        } else if ("tugas".equals(j.id)) {
            sb.append("<input id=\"teks\" placeholder=\"Tugas baru\">\n");
            sb.append("<button onclick=\"tambahTugas()\">Tambah Tugas</button>\n");
            sb.append("<ul id=\"daftar\"></ul>\n<script>\n");
            sb.append("function muat(){var d=ambilTugas();var u=document.getElementById('daftar');u.innerHTML='';"
                    + "for(var i=0;i<d.length;i++){var li=document.createElement('li');"
                    + "var c=document.createElement('input');c.type='checkbox';c.checked=!!d[i].selesai;"
                    + "c.onclick=(function(n){return function(){ubahSelesai(n,this.checked);muat();};})(i);"
                    + "li.appendChild(c);var s=document.createElement('span');"
                    + "s.textContent=' '+d[i].teks+(d[i].selesai?' (selesai)':'');li.appendChild(s);"
                    + "var b=document.createElement('button');b.textContent='Hapus';"
                    + "b.onclick=(function(n){return function(){hapusTugas(n);muat();};})(i);"
                    + "li.appendChild(b);u.appendChild(li);}}\n");
            sb.append("function tambahTugas(){var t=document.getElementById('teks').value;"
                    + "if(!t)return;simpanTugas(t);document.getElementById('teks').value='';muat();}\nmuat();\n</script>\n");
        } else { // pesan
            sb.append("<input id=\"nama\" placeholder=\"Nama kamu\">\n");
            sb.append("<textarea id=\"teks\" placeholder=\"Tulis pesan\"></textarea>\n");
            sb.append("<button onclick=\"tambahPesan()\">Kirim Pesan</button>\n");
            sb.append("<ul id=\"daftar\"></ul>\n<script>\n");
            sb.append("function muat(){var d=ambilPesan();var u=document.getElementById('daftar');u.innerHTML='';"
                    + "for(var i=0;i<d.length;i++){var li=document.createElement('li');"
                    + "li.textContent=d[i].nama+': '+d[i].teks;u.appendChild(li);}}\n");
            sb.append("function tambahPesan(){var n=document.getElementById('nama').value||'Tanpa nama';"
                    + "var t=document.getElementById('teks').value;if(!t)return;"
                    + "kirimPesan(n,t);document.getElementById('teks').value='';muat();}\nmuat();\n</script>\n");
        }
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /** Isi backend-klien.js: fungsi simpan/ambil (dipakai index.html dan aplikasi lain). */
    public static String klienJsUntuk(Jenis j) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Klien backend ").append(j.nama).append(" (Tahap 3F).\n");
        sb.append("// Database lokal: localStorage dengan kunci '").append(j.kunci).append("'.\n");
        sb.append("// Fungsi-fungsi di bawah bisa dipakai dari halaman lain dengan menyertakan berkas ini.\n");
        sb.append("function _baca(){try{var s=localStorage.getItem('").append(j.kunci).append("');"
                + "return s?JSON.parse(s):[];}catch(e){return [];}}\n");
        sb.append("function _tulis(d){try{localStorage.setItem('").append(j.kunci)
                .append("',JSON.stringify(d));}catch(e){}}\n");
        if ("catatan".equals(j.id)) {
            sb.append("function simpanCatatan(judul,isi){var d=_baca();d.push({judul:judul||'',isi:isi||''});_tulis(d);return d.length;}\n");
            sb.append("function hapusCatatan(i){var d=_baca();if(i>=0&&i<d.length){d.splice(i,1);_tulis(d);}return d.length;}\n");
            sb.append("function ambilCatatan(){return _baca();}\n");
        } else if ("tugas".equals(j.id)) {
            sb.append("function simpanTugas(teks){var d=_baca();d.push({teks:teks||'',selesai:false});_tulis(d);return d.length;}\n");
            sb.append("function ubahSelesai(i,selesai){var d=_baca();if(i>=0&&i<d.length){d[i].selesai=!!selesai;_tulis(d);}return d.length;}\n");
            sb.append("function hapusTugas(i){var d=_baca();if(i>=0&&i<d.length){d.splice(i,1);_tulis(d);}return d.length;}\n");
            sb.append("function ambilTugas(){return _baca();}\n");
        } else { // pesan
            sb.append("function kirimPesan(nama,teks){var d=_baca();d.push({nama:nama||'Tanpa nama',teks:teks||'',waktu:new Date().toISOString()});_tulis(d);return d.length;}\n");
            sb.append("function ambilPesan(){return _baca();}\n");
        }
        return sb.toString();
    }

    /** Isi BACA-SAYA.txt untuk proyek backend. */
    public static String bacaSayaUntuk(Jenis j) {
        StringBuilder sb = new StringBuilder();
        sb.append("Backend ").append(j.nama).append(" (dibuat oleh Create Aplikasi)\n\n");
        sb.append(j.penjelasan).append("\n\n");
        sb.append("CARA KERJA\n");
        sb.append("Halaman index.html adalah backend-nya: formulir menyimpan data, daftar menampilkan data.\n");
        sb.append("Data disimpan di localStorage WebView dengan kunci '").append(j.kunci).append("'.\n");
        sb.append("Data tersimpan di HP yang menjalankan aplikasi dan bisa dibaca kembali setelah aplikasi ditutup.\n\n");
        sb.append("FUNGSI KLIEN (di backend-klien.js, sertakan berkas ini di halaman lain):\n");
        if ("catatan".equals(j.id)) {
            sb.append("- simpanCatatan(judul, isi)\n- hapusCatatan(nomor)\n- ambilCatatan() -> daftar {judul, isi}\n");
        } else if ("tugas".equals(j.id)) {
            sb.append("- simpanTugas(teks)\n- ubahSelesai(nomor, true/false)\n- hapusTugas(nomor)\n- ambilTugas() -> daftar {teks, selesai}\n");
        } else {
            sb.append("- kirimPesan(nama, teks)\n- ambilPesan() -> daftar {nama, teks, waktu}\n");
        }
        sb.append("\nBATASAN: backend ini lokal (satu HP). Dua HP tidak berbagi data yang sama;\n");
        sb.append("untuk berbagi data antar HP, backend harus dihosting di server sungguhan.\n");
        return sb.toString();
    }

    private static String tulisBerkas(File f, String isi) {
        try {
            File induk = f.getParentFile();
            if (induk != null) induk.mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            return null;
        } catch (Exception e) {
            return "Gagal menulis " + f.getAbsolutePath() + ": " + e.getMessage();
        }
    }
}
