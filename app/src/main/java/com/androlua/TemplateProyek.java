package com.androlua;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Template Proyek Siap Pakai (Tahap 3C).
 *
 * Di layar Buat Proyek, pengguna bisa memilih template (Kalkulator, Catatan,
 * Daftar Tugas, Kalkulator Web, Situs Profil, Penghitung) selain proyek
 * kosong bawaan. Template diterapkan SETELAH proyek dibuat (BuatProyek),
 * dengan menimpa berkas utama proyek dengan kode awal template:
 * MainActivity.java untuk Java, MainActivity.kt untuk Kotlin/Compose,
 * dan index.html untuk HTML/Situs Web. (Lua sudah dihapus dari IDE.)
 *
 * Isi template disimpan sebagai konstanta String[] di bawah (dihasilkan dari
 * berkas sumber polos di template-sumber/ lewat gen-template-proyek.py —
 * jangan edit manual, ubah sumbernya lalu jalankan generatornya).
 *
 * Lisensi (Tahap 3B) TETAP dijaga: bila proyek memakai lisensi, gerbang
 * lisensi dipertahankan — untuk Java/Kotlin/Compose, badan onCreate dibungkus
 * tampilkanUtama() dan gerbang Lisensi dipasang di onCreate (sama persis
 * seperti templateJavaMain/templateKotlinMain/templateComposeMain di
 * TemplateDasar).
 * Untuk HTML/Situs Web tidak ada yang perlu dijaga (gerbang lisensi ada di
 * MainActivity.java, sedangkan template hanya menimpa index.html).
 *
 * Kelas ini murni java.io/java.util (tanpa API Android) supaya logikanya
 * bisa diuji ulang di JRE biasa.
 */
public class TemplateProyek {

    /** Satu template: id, nama tampil, deskripsi, dan jenis proyeknya. */
    public static class Info {
        public final String id;
        public final String nama;
        public final String deskripsi;
        public final String tipe; // java | kotlin | html | situs | compose
        public Info(String id, String nama, String deskripsi, String tipe) {
            this.id = id;
            this.nama = nama;
            this.deskripsi = deskripsi;
            this.tipe = tipe;
        }
    }

    public static final Info KOSONG = new Info("kosong",
            "Kosong (bawaan)",
            "Proyek kosong dengan kode contoh bawaan.",
            null);

    public static final Info[] SEMUA = {
        new Info("kalkulator", "Kalkulator",
                "Aplikasi kalkulator sederhana (tambah, kurang, kali, bagi). Proyek Java.",
                "java"),
        new Info("catatan", "Catatan",
                "Aplikasi catatan: tulis, simpan, dan muat lagi. Proyek Java.",
                "java"),
        new Info("tugas", "Daftar Tugas",
                "Daftar tugas: tambah dan hapus dengan ketukan. Proyek Kotlin.",
                "kotlin"),
        new Info("kalkulator-web", "Kalkulator Web",
                "Kalkulator di halaman web (HTML + JavaScript). Proyek HTML. Mengganti index.html, termasuk skrip HTML yang diketik di form.",
                "html"),
        new Info("profil", "Situs Profil",
                "Situs profil sederhana (Beranda, Tentang, Kontak). Proyek Situs Web. Mengganti index.html.",
                "situs"),
        new Info("penghitung", "Penghitung",
                "Penghitung angka dengan tombol tambah, kurang, dan nol (Jetpack Compose). Proyek Compose.",
                "compose"),
    };

    public static Info cari(String id) {
        if (id == null) return null;
        if ("kosong".equals(id)) return KOSONG;
        for (int i = 0; i < SEMUA.length; i++) {
            if (SEMUA[i].id.equals(id)) return SEMUA[i];
        }
        return null;
    }

    // @@TEMPLATE_AWAL@@
    private static final String[] ISI_KALKULATOR_JAVA = {
        "package {{PAKET}};",
        "",
        "import android.app.Activity;",
        "import android.os.Bundle;",
        "import android.text.InputType;",
        "import android.view.View;",
        "import android.widget.Button;",
        "import android.widget.EditText;",
        "import android.widget.LinearLayout;",
        "import android.widget.TextView;",
        "",
        "// Kalkulator — template siap pakai dari Create Aplikasi (Tahap 3C).",
        "// Dua angka + empat tombol operasi (tambah, kurang, kali, bagi).",
        "// Dulu proyek Lua; kini proyek Java (Lua dihapus dari IDE).",
        "public class MainActivity extends Activity {",
        "    private EditText angka1;",
        "    private EditText angka2;",
        "    private TextView hasil;",
        "",
        "    @Override",
        "    protected void onCreate(Bundle savedInstanceState) {",
        "        super.onCreate(savedInstanceState);",
        "// @@AWAL_BADAN@@",
        "        LinearLayout r = new LinearLayout(this);",
        "        r.setOrientation(LinearLayout.VERTICAL);",
        "        r.setPadding(48, 36, 48, 36);",
        "",
        "        TextView judul = new TextView(this);",
        "        judul.setText(\"Kalkulator\");",
        "        judul.setTextSize(20);",
        "        r.addView(judul);",
        "",
        "        angka1 = new EditText(this);",
        "        angka1.setHint(\"Angka pertama\");",
        "        angka1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);",
        "        r.addView(angka1);",
        "",
        "        angka2 = new EditText(this);",
        "        angka2.setHint(\"Angka kedua\");",
        "        angka2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);",
        "        r.addView(angka2);",
        "",
        "        hasil = new TextView(this);",
        "        hasil.setText(\"Hasil: -\");",
        "        hasil.setTextSize(18);",
        "        r.addView(hasil);",
        "",
        "        LinearLayout baris = new LinearLayout(this);",
        "        baris.setOrientation(LinearLayout.HORIZONTAL);",
        "        String[] operasi = {\"+\", \"-\", \"x\", \"/\"};",
        "        for (int i = 0; i < operasi.length; i++) {",
        "            final String op = operasi[i];",
        "            Button t = new Button(this);",
        "            t.setText(op);",
        "            t.setTextSize(20);",
        "            t.setOnClickListener(new View.OnClickListener() {",
        "                @Override",
        "                public void onClick(View v) {",
        "                    hitung(op);",
        "                }",
        "            });",
        "            baris.addView(t);",
        "        }",
        "        r.addView(baris);",
        "",
        "        setContentView(r);",
        "// @@AKHIR_BADAN@@",
        "    }",
        "",
        "    private void hitung(String op) {",
        "        double a;",
        "        double b;",
        "        try {",
        "            a = Double.parseDouble(angka1.getText().toString());",
        "            b = Double.parseDouble(angka2.getText().toString());",
        "        } catch (Exception e) {",
        "            hasil.setText(\"Isi kedua angka dulu.\");",
        "            return;",
        "        }",
        "        double h = 0;",
        "        if (\"+\".equals(op)) {",
        "            h = a + b;",
        "        } else if (\"-\".equals(op)) {",
        "            h = a - b;",
        "        } else if (\"x\".equals(op)) {",
        "            h = a * b;",
        "        } else if (\"/\".equals(op)) {",
        "            if (b == 0) {",
        "                hasil.setText(\"Tidak bisa dibagi nol.\");",
        "                return;",
        "            }",
        "            h = a / b;",
        "        }",
        "        hasil.setText(\"Hasil: \" + h);",
        "    }",
        "}"
    };

    private static final String[] ISI_CATATAN_JAVA = {
        "package {{PAKET}};",
        "",
        "import android.app.Activity;",
        "import android.os.Bundle;",
        "import android.view.View;",
        "import android.view.ViewGroup;",
        "import android.widget.Button;",
        "import android.widget.EditText;",
        "import android.widget.LinearLayout;",
        "import android.widget.TextView;",
        "import android.widget.Toast;",
        "import java.io.BufferedReader;",
        "import java.io.File;",
        "import java.io.FileInputStream;",
        "import java.io.FileOutputStream;",
        "import java.io.InputStreamReader;",
        "import java.io.OutputStreamWriter;",
        "",
        "// Catatan — template siap pakai dari Create Aplikasi (Tahap 3C).",
        "// Ketik catatan, tekan Simpan untuk menyimpan ke berkas di dalam aplikasi,",
        "// tekan Muat untuk membacanya lagi. Catatan tersimpan walaupun aplikasi",
        "// ditutup lalu dibuka lagi.",
        "public class MainActivity extends Activity {",
        "    private EditText isi;",
        "    private TextView status;",
        "",
        "    @Override",
        "    protected void onCreate(Bundle savedInstanceState) {",
        "        super.onCreate(savedInstanceState);",
        "// @@AWAL_BADAN@@",
        "        LinearLayout r = new LinearLayout(this);",
        "        r.setOrientation(LinearLayout.VERTICAL);",
        "        r.setPadding(48, 36, 48, 36);",
        "",
        "        TextView judul = new TextView(this);",
        "        judul.setText(\"Catatan\");",
        "        judul.setTextSize(20);",
        "        r.addView(judul);",
        "",
        "        isi = new EditText(this);",
        "        isi.setHint(\"Tulis catatan di sini...\");",
        "        isi.setMinLines(6);",
        "        isi.setGravity(android.view.Gravity.TOP);",
        "        r.addView(isi, new LinearLayout.LayoutParams(",
        "                ViewGroup.LayoutParams.MATCH_PARENT,",
        "                ViewGroup.LayoutParams.WRAP_CONTENT));",
        "",
        "        status = new TextView(this);",
        "        status.setText(\"Belum ada catatan tersimpan.\");",
        "        r.addView(status);",
        "",
        "        Button simpan = new Button(this);",
        "        simpan.setText(\"Simpan\");",
        "        simpan.setOnClickListener(new View.OnClickListener() {",
        "            @Override",
        "            public void onClick(View v) {",
        "                simpanCatatan();",
        "            }",
        "        });",
        "        r.addView(simpan);",
        "",
        "        Button muat = new Button(this);",
        "        muat.setText(\"Muat\");",
        "        muat.setOnClickListener(new View.OnClickListener() {",
        "            @Override",
        "            public void onClick(View v) {",
        "                muatCatatan();",
        "            }",
        "        });",
        "        r.addView(muat);",
        "",
        "        setContentView(r);",
        "        muatCatatan();",
        "// @@AKHIR_BADAN@@",
        "    }",
        "",
        "    private File berkasCatatan() {",
        "        return new File(getFilesDir(), \"catatan.txt\");",
        "    }",
        "",
        "    private void simpanCatatan() {",
        "        try {",
        "            FileOutputStream out = new FileOutputStream(berkasCatatan());",
        "            OutputStreamWriter w = new OutputStreamWriter(out, \"UTF-8\");",
        "            w.write(isi.getText().toString());",
        "            w.close();",
        "            status.setText(\"Catatan tersimpan.\");",
        "            Toast.makeText(this, \"Catatan tersimpan\", Toast.LENGTH_SHORT).show();",
        "        } catch (Exception e) {",
        "            status.setText(\"Gagal menyimpan: \" + e.getMessage());",
        "        }",
        "    }",
        "",
        "    private void muatCatatan() {",
        "        try {",
        "            File f = berkasCatatan();",
        "            if (!f.exists()) {",
        "                status.setText(\"Belum ada catatan tersimpan.\");",
        "                return;",
        "            }",
        "            BufferedReader r = new BufferedReader(new InputStreamReader(",
        "                    new FileInputStream(f), \"UTF-8\"));",
        "            StringBuilder sb = new StringBuilder();",
        "            String baris;",
        "            while ((baris = r.readLine()) != null) {",
        "                if (sb.length() > 0) sb.append('\\n');",
        "                sb.append(baris);",
        "            }",
        "            r.close();",
        "            isi.setText(sb.toString());",
        "            status.setText(\"Catatan dimuat.\");",
        "        } catch (Exception e) {",
        "            status.setText(\"Gagal memuat: \" + e.getMessage());",
        "        }",
        "    }",
        "}",
    };

    private static final String[] ISI_TUGAS_KT = {
        "package {{PAKET}}",
        "",
        "import android.app.Activity",
        "import android.content.SharedPreferences",
        "import android.os.Bundle",
        "import android.view.View",
        "import android.view.ViewGroup",
        "import android.widget.ArrayAdapter",
        "import android.widget.Button",
        "import android.widget.EditText",
        "import android.widget.LinearLayout",
        "import android.widget.ListView",
        "import android.widget.TextView",
        "import android.widget.Toast",
        "import java.util.LinkedHashSet",
        "",
        "// Daftar Tugas — template siap pakai dari Create Aplikasi (Tahap 3C).",
        "// Ketik tugas, tekan Tambah. Ketuk tugas di daftar untuk menghapusnya.",
        "// Daftar tersimpan di SharedPreferences, jadi tetap ada setelah aplikasi",
        "// ditutup lalu dibuka lagi.",
        "class MainActivity : Activity() {",
        "    private lateinit var isi: EditText",
        "    private lateinit var daftar: ArrayAdapter<String>",
        "    private val tugas = ArrayList<String>()",
        "",
        "    override fun onCreate(savedInstanceState: Bundle?) {",
        "        super.onCreate(savedInstanceState)",
        "// @@AWAL_BADAN@@",
        "        val r = LinearLayout(this)",
        "        r.orientation = LinearLayout.VERTICAL",
        "        r.setPadding(48, 36, 48, 36)",
        "",
        "        val judul = TextView(this)",
        "        judul.text = \"Daftar Tugas\"",
        "        judul.textSize = 20f",
        "        r.addView(judul)",
        "",
        "        isi = EditText(this)",
        "        isi.hint = \"Tulis tugas baru...\"",
        "        r.addView(isi, LinearLayout.LayoutParams(",
        "                ViewGroup.LayoutParams.MATCH_PARENT,",
        "                ViewGroup.LayoutParams.WRAP_CONTENT))",
        "",
        "        val tambah = Button(this)",
        "        tambah.text = \"Tambah\"",
        "        tambah.setOnClickListener { tambahTugas() }",
        "        r.addView(tambah)",
        "",
        "        val ket = TextView(this)",
        "        ket.text = \"Ketuk tugas untuk menghapusnya.\"",
        "        r.addView(ket)",
        "",
        "        daftar = ArrayAdapter(this, android.R.layout.simple_list_item_1, tugas)",
        "        val lv = ListView(this)",
        "        lv.adapter = daftar",
        "        lv.setOnItemClickListener { _, _, pos, _ -> hapusTugas(pos) }",
        "        r.addView(lv, LinearLayout.LayoutParams(",
        "                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))",
        "",
        "        setContentView(r)",
        "        muatTugas()",
        "// @@AKHIR_BADAN@@",
        "    }",
        "",
        "    private fun prefs(): SharedPreferences {",
        "        return getSharedPreferences(\"daftar_tugas\", MODE_PRIVATE)",
        "    }",
        "",
        "    private fun tambahTugas() {",
        "        val teks = isi.text.toString().trim()",
        "        if (teks.isEmpty()) {",
        "            Toast.makeText(this, \"Tulis tugas dulu\", Toast.LENGTH_SHORT).show()",
        "            return",
        "        }",
        "        tugas.add(teks)",
        "        daftar.notifyDataSetChanged()",
        "        isi.setText(\"\")",
        "        simpanTugas()",
        "    }",
        "",
        "    private fun hapusTugas(pos: Int) {",
        "        if (pos < 0 || pos >= tugas.size) return",
        "        val hapus = tugas.removeAt(pos)",
        "        daftar.notifyDataSetChanged()",
        "        simpanTugas()",
        "        Toast.makeText(this, \"Dihapus: \" + hapus, Toast.LENGTH_SHORT).show()",
        "    }",
        "",
        "    private fun simpanTugas() {",
        "        prefs().edit().putStringSet(\"tugas\", LinkedHashSet(tugas)).apply()",
        "    }",
        "",
        "    private fun muatTugas() {",
        "        val simpan = prefs().getStringSet(\"tugas\", null)",
        "        if (simpan != null) {",
        "            tugas.clear()",
        "            tugas.addAll(simpan)",
        "            daftar.notifyDataSetChanged()",
        "        }",
        "    }",
        "}",
    };

    private static final String[] ISI_KALKULATOR_HTML = {
        "<!DOCTYPE html>",
        "<html lang=\"id\">",
        "<head>",
        "<meta charset=\"utf-8\">",
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
        "<title>Kalkulator</title>",
        "<style>",
        "  body { font-family: sans-serif; margin: 16px; background: #f2f2f2; }",
        "  h1 { font-size: 22px; }",
        "  #layar { width: 100%; font-size: 28px; text-align: right; padding: 10px;",
        "    box-sizing: border-box; margin-bottom: 10px; }",
        "  .baris { display: flex; gap: 8px; margin-bottom: 8px; }",
        "  button { flex: 1; font-size: 22px; padding: 14px 0; }",
        "</style>",
        "</head>",
        "<body>",
        "<h1>Kalkulator</h1>",
        "<input id=\"layar\" type=\"text\" readonly value=\"0\">",
        "<div class=\"baris\">",
        "  <button onclick=\"tekan('7')\">7</button>",
        "  <button onclick=\"tekan('8')\">8</button>",
        "  <button onclick=\"tekan('9')\">9</button>",
        "  <button onclick=\"tekan('/')\">/</button>",
        "</div>",
        "<div class=\"baris\">",
        "  <button onclick=\"tekan('4')\">4</button>",
        "  <button onclick=\"tekan('5')\">5</button>",
        "  <button onclick=\"tekan('6')\">6</button>",
        "  <button onclick=\"tekan('*')\">x</button>",
        "</div>",
        "<div class=\"baris\">",
        "  <button onclick=\"tekan('1')\">1</button>",
        "  <button onclick=\"tekan('2')\">2</button>",
        "  <button onclick=\"tekan('3')\">3</button>",
        "  <button onclick=\"tekan('-')\">-</button>",
        "</div>",
        "<div class=\"baris\">",
        "  <button onclick=\"tekan('0')\">0</button>",
        "  <button onclick=\"tekan('.')\">.</button>",
        "  <button onclick=\"bersihkan()\">C</button>",
        "  <button onclick=\"tekan('+')\">+</button>",
        "</div>",
        "<div class=\"baris\">",
        "  <button onclick=\"hitung()\">=</button>",
        "</div>",
        "<script>",
        "  var ekspresi = \"\";",
        "  function tampilkan() {",
        "    document.getElementById(\"layar\").value = ekspresi === \"\" ? \"0\" : ekspresi;",
        "  }",
        "  function tekan(t) {",
        "    ekspresi += t;",
        "    tampilkan();",
        "  }",
        "  function bersihkan() {",
        "    ekspresi = \"\";",
        "    tampilkan();",
        "  }",
        "  function hitung() {",
        "    try {",
        "      var hasil = Function(\"return (\" + ekspresi + \")\")();",
        "      if (typeof hasil !== \"number\" || !isFinite(hasil)) {",
        "        ekspresi = \"\";",
        "        document.getElementById(\"layar\").value = \"Tidak bisa dibagi nol\";",
        "        return;",
        "      }",
        "      ekspresi = String(hasil);",
        "    } catch (e) {",
        "      ekspresi = \"\";",
        "      document.getElementById(\"layar\").value = \"Salah\";",
        "      return;",
        "    }",
        "    tampilkan();",
        "  }",
        "</script>",
        "</body>",
        "</html>",
    };

    private static final String[] ISI_PROFIL_HTML = {
        "<!DOCTYPE html>",
        "<html lang=\"id\">",
        "<head>",
        "<meta charset=\"utf-8\">",
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
        "<title>{{NAMA}}</title>",
        "<style>",
        "  body { font-family: sans-serif; margin: 0; background: #f2f2f2; color: #222; }",
        "  header { background: #2196F3; color: white; padding: 24px 16px; }",
        "  header h1 { margin: 0; font-size: 24px; }",
        "  nav { background: #1976D2; padding: 8px 16px; }",
        "  nav a { color: white; margin-right: 16px; text-decoration: none; }",
        "  main { padding: 16px; }",
        "  section { background: white; padding: 16px; margin-bottom: 12px; border-radius: 8px; }",
        "  h2 { margin-top: 0; font-size: 18px; }",
        "</style>",
        "</head>",
        "<body>",
        "<header><h1>{{NAMA}}</h1><p>Selamat datang di situs saya.</p></header>",
        "<nav>",
        "  <a href=\"index.html\">Beranda</a>",
        "  <a href=\"tentang.html\">Tentang</a>",
        "  <a href=\"kontak.html\">Kontak</a>",
        "</nav>",
        "<main>",
        "  <section>",
        "    <h2>Beranda</h2>",
        "    <p>Ini adalah situs profil sederhana yang dibuat dengan Create Aplikasi. Ubah teks di berkas index.html, tentang.html, dan kontak.html sesuai kebutuhan Anda.</p>",
        "  </section>",
        "  <section>",
        "    <h2>Tentang saya</h2>",
        "    <p>Tulis perkenalan singkat di sini: siapa Anda, apa yang Anda kerjakan, dan apa yang Anda sukai.</p>",
        "  </section>",
        "  <section>",
        "    <h2>Kontak</h2>",
        "    <p>Hubungi saya lewat halaman <a href=\"kontak.html\">Kontak</a>.</p>",
        "  </section>",
        "</main>",
        "</body>",
        "</html>",
    };

    private static final String[] ISI_PENGHITUNG_KT = {
        "package {{PAKET}}",
        "",
        "import android.os.Bundle",
        "import androidx.activity.ComponentActivity",
        "import androidx.activity.compose.setContent",
        "import androidx.compose.foundation.layout.Arrangement",
        "import androidx.compose.foundation.layout.Column",
        "import androidx.compose.foundation.layout.Row",
        "import androidx.compose.foundation.layout.fillMaxSize",
        "import androidx.compose.foundation.layout.padding",
        "import androidx.compose.material3.Button",
        "import androidx.compose.material3.MaterialTheme",
        "import androidx.compose.material3.Text",
        "import androidx.compose.runtime.Composable",
        "import androidx.compose.runtime.getValue",
        "import androidx.compose.runtime.mutableStateOf",
        "import androidx.compose.runtime.remember",
        "import androidx.compose.runtime.setValue",
        "import androidx.compose.ui.Alignment",
        "import androidx.compose.ui.Modifier",
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.unit.sp",
        "",
        "// Penghitung — template siap pakai dari Create Aplikasi (Tahap 3C).",
        "// Tombol tambah dan kurang mengubah angka di layar, tombol Nol",
        "// mengembalikannya ke 0. Antarmuka dibuat dengan Jetpack Compose.",
        "class MainActivity : ComponentActivity() {",
        "    override fun onCreate(savedInstanceState: Bundle?) {",
        "        super.onCreate(savedInstanceState)",
        "// @@AWAL_BADAN@@",
        "        setContent {",
        "            MaterialTheme {",
        "                LayarUtama()",
        "            }",
        "        }",
        "// @@AKHIR_BADAN@@",
        "    }",
        "}",
        "",
        "@Composable",
        "fun LayarUtama() {",
        "    var hitung by remember { mutableStateOf(0) }",
        "    Column(",
        "        modifier = Modifier.fillMaxSize().padding(16.dp),",
        "        horizontalAlignment = Alignment.CenterHorizontally,",
        "        verticalArrangement = Arrangement.Center",
        "    ) {",
        "        Text(text = \"Penghitung\", fontSize = 24.sp)",
        "        Text(text = hitung.toString(), fontSize = 48.sp)",
        "        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {",
        "            Button(onClick = { hitung-- }) {",
        "                Text(text = \"-\")",
        "            }",
        "            Button(onClick = { hitung = 0 }) {",
        "                Text(text = \"Nol\")",
        "            }",
        "            Button(onClick = { hitung++ }) {",
        "                Text(text = \"+\")",
        "            }",
        "        }",
        "    }",
        "}",
    };
    // @@TEMPLATE_AKHIR@@

    private static String gabung(String[] baris) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < baris.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(baris[i]);
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String isiTemplate(String id) {
        if ("kalkulator".equals(id)) return gabung(ISI_KALKULATOR_JAVA);
        if ("catatan".equals(id)) return gabung(ISI_CATATAN_JAVA);
        if ("tugas".equals(id)) return gabung(ISI_TUGAS_KT);
        if ("kalkulator-web".equals(id)) return gabung(ISI_KALKULATOR_HTML);
        if ("profil".equals(id)) return gabung(ISI_PROFIL_HTML);
        if ("penghitung".equals(id)) return gabung(ISI_PENGHITUNG_KT);
        return null;
    }

    /**
     * Menerapkan template ke proyek yang SUDAH dibuat (folder proyek ada).
     * Mengembalikan null bila berhasil, atau pesan galat bila gagal.
     * Tidak pernah menghapus lisensi: bila proyek berlisensi, gerbang
     * lisensi dipertahankan (lihat keterangan kelas).
     */
    public static String terapkan(File dirProyek, String templateId,
                                  String namaPaket, String namaAplikasi) {
        if ("kosong".equals(templateId)) return null;
        Info info = cari(templateId);
        if (info == null) return "Template tidak dikenal: " + templateId;
        if (dirProyek == null || !dirProyek.isDirectory()) {
            return "Folder proyek tidak ditemukan.";
        }
        String isi = isiTemplate(templateId);
        if (isi == null) return "Isi template tidak ditemukan: " + templateId;
        isi = isi.replace("{{PAKET}}", namaPaket == null ? "" : namaPaket)
                .replace("{{NAMA}}", namaAplikasi == null ? "" : namaAplikasi);
        boolean lisensi = lisensiAktif(dirProyek);
        try {
            if ("java".equals(info.tipe)) {
                File target = berkasMain(dirProyek, namaPaket, "MainActivity.java");
                return tulisBerkas(target, bungkusBadan(isi, "java", lisensi));
            } else if ("kotlin".equals(info.tipe) || "compose".equals(info.tipe)) {
                File target = berkasMain(dirProyek, namaPaket, "MainActivity.kt");
                return tulisBerkas(target, bungkusBadan(isi, "kotlin", lisensi));
            } else if ("html".equals(info.tipe) || "situs".equals(info.tipe)) {
                return tulisBerkas(
                        new File(dirProyek, "app/src/main/assets/index.html"), isi);
            }
            return "Jenis template tidak dikenal: " + info.tipe;
        } catch (Exception e) {
            return "Gagal menerapkan template: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------ pembantu

    private static File berkasMain(File dirProyek, String namaPaket, String namaBerkas) {
        String jalurPaket = (namaPaket == null ? "" : namaPaket).replace('.', '/');
        return new File(dirProyek,
                "app/src/main/java/" + jalurPaket + "/" + namaBerkas);
    }

    /** Membaca init.lua proyek: true bila baris lisensi=true ada. */
    private static boolean lisensiAktif(File dirProyek) {
        try {
            String isi = bacaBerkas(new File(dirProyek, "init.lua"));
            if (isi == null) return false;
            for (String baris : isi.split("\n")) {
                String t = baris.trim();
                // Buang komentar Lua (--) DULU: baris "lisensi=false   -- true =
                // aplikasi hasil build meminta kode lisensi" mengandung kata
                // "true" di komentarnya; tanpa ini proyek tanpa lisensi salah
                // terbaca berlisensi (ditemukan Tahap 3C saat uji template).
                int komentar = t.indexOf("--");
                if (komentar >= 0) t = t.substring(0, komentar).trim();
                if (t.contains("=")) {
                    String kunci = t.substring(0, t.indexOf('=')).trim();
                    String nilai = t.substring(t.indexOf('=') + 1).trim();
                    if ("lisensi".equals(kunci) && "true".equals(nilai)) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String bacaBerkas(File f) throws Exception {
        if (f == null || !f.isFile()) return null;
        BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        try {
            String baris;
            while ((baris = r.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(baris);
            }
        } finally {
            r.close();
        }
        return sb.toString();
    }

    private static String tulisBerkas(File f, String isi) {
        try {
            File induk = f.getParentFile();
            if (induk != null) induk.mkdirs();
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(f), "UTF-8");
            try {
                w.write(isi);
            } finally {
                w.close();
            }
            if (!f.isFile() || f.length() == 0) {
                return "Berkas hasil kosong: " + f.getAbsolutePath();
            }
            return null;
        } catch (Exception e) {
            return "Gagal menulis " + f.getAbsolutePath() + ": " + e.getMessage();
        }
    }

    // ------------------------------------------------------- Java/Kotlin

    private static final String PENANDA_AWAL = "// @@AWAL_BADAN@@";
    private static final String PENANDA_AKHIR = "// @@AKHIR_BADAN@@";

    /**
     * Membungkus badan onCreate template Java/Kotlin/Compose.
     * Tanpa lisensi: penanda dihapus, isi dibiarkan apa adanya.
     * Dengan lisensi: badan onCreate dibungkus tampilkanUtama() dan gerbang
     * Lisensi dipasang di onCreate — sama persis seperti template di
     * main.lua (Java memakai Runnable, Kotlin/Compose memakai lambda).
     */
    private static String bungkusBadan(String isi, String bahasa, boolean lisensi)
            throws Exception {
        String[] baris = isi.split("\n", -1);
        int awal = -1;
        int akhir = -1;
        for (int i = 0; i < baris.length; i++) {
            String t = baris[i].trim();
            if (PENANDA_AWAL.equals(t)) awal = i;
            if (PENANDA_AKHIR.equals(t)) akhir = i;
        }
        if (awal < 0 || akhir < 0 || akhir <= awal) {
            throw new Exception("penanda badan onCreate tidak ditemukan di template");
        }
        List<String> badan = new ArrayList<String>();
        for (int i = awal + 1; i < akhir; i++) badan.add(baris[i]);
        if (!lisensi) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < baris.length; i++) {
                if (i == awal || i == akhir) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(baris[i]);
            }
            sb.append('\n');
            return sb.toString();
        }
        // Dengan lisensi: baris setelah penanda AKHIR harus penutup onCreate.
        int tutup = -1;
        for (int i = akhir + 1; i < baris.length; i++) {
            if (baris[i].trim().isEmpty()) continue;
            if ("}".equals(baris[i].trim())) tutup = i;
            break;
        }
        if (tutup < 0) {
            throw new Exception("penutup onCreate tidak ditemukan di template");
        }
        List<String> keluar = new ArrayList<String>();
        for (int i = 0; i < awal; i++) keluar.add(baris[i]);
        if ("java".equals(bahasa)) {
            keluar.add("        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan");
            keluar.add("        // layar lisensi dulu; layar utama baru tampil setelah kode");
            keluar.add("        // lisensi yang benar dimasukkan.");
            keluar.add("        if (!Lisensi.sudahAktif(this)) {");
            keluar.add("            Lisensi.tampilkanLayarLisensi(this, new Runnable() {");
            keluar.add("                @Override public void run() { tampilkanUtama(); }");
            keluar.add("            });");
            keluar.add("            return;");
            keluar.add("        }");
            keluar.add("        tampilkanUtama();");
            keluar.add("    }");
            keluar.add("");
            keluar.add("    private void tampilkanUtama() {");
        } else {
            keluar.add("        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan");
            keluar.add("        // layar lisensi dulu; layar utama baru tampil setelah kode");
            keluar.add("        // lisensi yang benar dimasukkan.");
            keluar.add("        if (!Lisensi.sudahAktif(this)) {");
            keluar.add("            Lisensi.tampilkanLayarLisensi(this) { tampilkanUtama() }");
            keluar.add("            return");
            keluar.add("        }");
            keluar.add("        tampilkanUtama()");
            keluar.add("    }");
            keluar.add("");
            keluar.add("    private fun tampilkanUtama() {");
        }
        keluar.addAll(badan);
        keluar.add("    }");
        for (int i = tutup + 1; i < baris.length; i++) keluar.add(baris[i]);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keluar.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(keluar.get(i));
        }
        sb.append('\n');
        return sb.toString();
    }
}
