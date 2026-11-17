package com.androlua;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Template dasar proyek (port Java dari template Lua di main.lua).
 * Menghasilkan berkas-berkas proyek baru (init.lua, AndroidManifest.xml,
 * MainActivity, res, assets) untuk tipe Java, Kotlin, HTML, Situs Web,
 * Compose, dan Python. Tipe Lua SUDAH DIHAPUS (tidak ada templateMain/templateLayout).
 * Keluaran tiap template byte-identik dengan versi Lua-nya (terverifikasi).
 */
public class TemplateDasar {

    /** Parameter pembuatan proyek (p di Lua). */
    public static class Param {
        public String name;        // nama aplikasi
        public String packageName; // nama paket (p.package di Lua)
        public String ver = "1.0"; // versi aplikasi
        public int code = 1;       // kode versi
        public int min = 21;       // SDK minimum
        public int target = 33;    // SDK target
        public String tipe = "java"; // java | kotlin | html | situs | compose | python
        public Map<String, Boolean> perms; // izin yang dicentang
        public boolean split;      // pisah arsitektur (split_abi)
        public boolean signing;    // tanda tangan pribadi
        public boolean lisensi;    // memakai lisensi berbayar
        public String wa = "";     // nomor WhatsApp pengembang (lisensi)
        public String seed = "";   // kata sandi proyek (lisensi)
        public String skripHtml;   // skrip HTML kustom (HTML/Situs Web)
    }

    /** Izin Wi-Fi hasil jabaran PEMINDAIAN_WIFI (sama seperti Lua). */
    public static final List<String> WIFI_PERMS = Arrays.asList(
            "ACCESS_WIFI_STATE", "ACCESS_NETWORK_STATE",
            "ACCESS_FINE_LOCATION", "NEARBY_WIFI_DEVICES");

    /** Gabung baris dengan pemisah (table.concat(t, sep) di Lua). */
    public static String gabung(String sep, String... bagian) {
        return String.join(sep, bagian);
    }

    /** Kutip string ala Lua string.format("%q", s): "isi" dengan escape. */
    public static String q(String s) {
        if (s == null) s = "";
        StringBuilder b = new StringBuilder();
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                case '\u0007': b.append("\\a"); break;
                case '\u000B': b.append("\\v"); break;
                default:
                    if (c < 32 || c == 127) {
                        b.append(String.format("\\%03d", (int) c));
                    } else {
                        b.append(c);
                    }
                    break;
            }
        }
        b.append('"');
        return b.toString();
    }

    /** Jenis nilai ala Lua type(x): table/string/number/boolean/nil. */
    public static String tipeNilai(Object o) {
        if (o == null) return "nil";
        if (o instanceof Map || o instanceof List) return "table";
        if (o instanceof String) return "string";
        if (o instanceof Number) return "number";
        if (o instanceof Boolean) return "boolean";
        return "userdata";
    }

    /** sub Lua (1-based, inklusif) -> Java substring. */
    public static String subLua(String s, int a) {
        if (s == null) return "";
        int mulai = a >= 1 ? a - 1 : s.length() + a;
        if (mulai < 0) mulai = 0;
        if (mulai > s.length()) return "";
        return s.substring(mulai);
    }

    public static String subLua(String s, int a, int b) {
        if (s == null) return "";
        int mulai = a >= 1 ? a - 1 : s.length() + a;
        int akhir = b >= 1 ? b : s.length() + b + 1;
        if (mulai < 0) mulai = 0;
        if (akhir > s.length()) akhir = s.length();
        if (akhir <= mulai) return "";
        return s.substring(mulai, akhir);
    }

    /** gsub Lua sederhana (pola literal): ganti semua kemunculan. */
    public static String gsubLua(String s, String pola, String ganti) {
        if (s == null) return "";
        return s.replace(pola, ganti);
    }

    /** match Lua sederhana: kembalikan cocok pertama atau null. */
    public static String cocokPola(String s, String pola) {
        if (s == null) return null;
        if (s.contains(pola)) return pola;
        return null;
    }

    /** Buat map dari pasangan kunci-nilai (untuk literal tabel Lua). */
    public static Map<String, Boolean> mapBaru(Object... pasangan) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pasangan.length; i += 2) {
            Object k = pasangan[i];
            Object v = pasangan[i + 1];
            m.put(String.valueOf(k), v instanceof Boolean ? (Boolean) v : Boolean.TRUE);
        }
        return m;
    }

    // init.lua proyek. CATATAN (restrukturisasi tanpa Lua): izin TIDAK lagi
    // ditulis ke init.lua — untuk semua jenis proyek (Java/Kotlin/HTML/
    // Situs/Compose) user_permission={} selalu kosong dan izin hanya ada di
    // AndroidManifest.xml. Blok izin khusus Lua dihapus; keluaran untuk
    // proyek non-Lua sama persis dengan sebelumnya.
    public static String templateInit(Param p) {
        return gabung("\n", ("appname=" + q(p.name)), ("appver=" + q(p.ver)), ("appcode=" + p.code), (("appsdk=" + p.min) + "          -- SDK minimum"), (("targetsdk=" + p.target) + "       -- SDK target"), ("packagename=" + q(p.packageName)), (("tipe=" + q((p.tipe != null ? p.tipe : "lua"))) + "        -- java | kotlin | html | situs | compose | python"), "theme=\"Theme_DeviceDefault_Light_NoActionBar\"", "developer=\"\"", "description=\"\"", "debugmode=true", (("split_abi=" + String.valueOf((p.split == true))) + "       -- true = APK terpisah 64 bit dan 32 bit"), (("tanda_tangan_pribadi=" + String.valueOf((p.signing == true))) + "   -- true = minta tanda tangan pribadi setelah build"), (("lisensi=" + String.valueOf((p.lisensi == true))) + "   -- true = aplikasi hasil build meminta kode lisensi"), (("wa_lisensi=" + q(((p.lisensi == true) ? (p.wa != null ? p.wa : "") : ""))) + "   -- nomor WhatsApp pengembang"), (("lisensi_seed=" + q(((p.lisensi == true) ? (p.seed != null ? p.seed : "") : ""))) + "   -- kata sandi proyek untuk kode lisensi (RAHASIA)"), "user_permission={", "", "}", "");
    }


    public static String menuSitus() {
        return gabung("\n", "      <nav>", "        <a href=\"index.html\">Beranda</a>", "        <a href=\"tentang.html\">Tentang</a>", "        <a href=\"kontak.html\">Kontak</a>", "      </nav>");
    }
    public static String templateHtmlIndex(Param p) {
        return gabung("\n", "<!DOCTYPE html>", "<html lang=\"id\">", "<head>", "  <meta charset=\"UTF-8\">", "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">", (("  <title>" + p.name) + "</title>"), "  <link rel=\"stylesheet\" href=\"style.css\">", "</head>", "<body>", (("  <h1>Halo dari " + p.name) + "</h1>"), "  <p>Ini proyek HTML. JavaScript sudah diaktifkan di WebView aplikasi ini.</p>", "  <button id=\"tombol\">Tekan saya</button>", "  <script src=\"script.js\"></script>", "</body>", "</html>", "");
    }
    public static String templateHtmlCss(Param p) {
        return gabung("\n", "body {", "  font-family: sans-serif;", "  padding: 16px;", "  background: #ffffff;", "  color: #222222;", "}", "");
    }
    public static String templateHtmlJs(Param p) {
        return gabung("\n", "document.getElementById(\"tombol\").addEventListener(\"click\", function () {", "  alert(\"JavaScript berjalan!\");", "});", "");
    }
    public static String templateSitusIndex(Param p) {
        return gabung("\n", "<!DOCTYPE html>", "<html lang=\"id\">", "<head>", "  <meta charset=\"UTF-8\">", "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">", (("  <title>" + p.name) + " - Beranda</title>"), "  <link rel=\"stylesheet\" href=\"style.css\">", "</head>", "<body>", "  <header>", (("    <h1 class=\"logo\">" + p.name) + "</h1>"), "    <button id=\"tombolMenu\" type=\"button\">☰ Menu</button>", menuSitus(), "  </header>", "  <main>", (("    <h2>Selamat datang di " + p.name) + "</h2>"), "    <p>Ini situs web contoh. Ubah teks, halaman, dan menunya sesuka hati, lalu tekan Bangun untuk membuat APK-nya.</p>", "    <div class=\"kartu-kartu\">", "      <section class=\"kartu\"><h3>Beranda</h3><p>Halaman utama situs ini.</p></section>", "      <section class=\"kartu\"><h3>Tentang</h3><p>Cerita di balik situs ini.</p></section>", "      <section class=\"kartu\"><h3>Kontak</h3><p>Kirim pesan lewat formulir.</p></section>", "    </div>", "    <button id=\"tombolSapa\" type=\"button\">Sapa saya</button>", "    <p id=\"sapaan\"></p>", "  </main>", (("  <footer><p>&copy; " + p.name) + "</p></footer>"), "  <script src=\"script.js\"></script>", "</body>", "</html>", "");
    }
    public static String templateSitusTentang(Param p) {
        return gabung("\n", "<!DOCTYPE html>", "<html lang=\"id\">", "<head>", "  <meta charset=\"UTF-8\">", "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">", (("  <title>" + p.name) + " - Tentang</title>"), "  <link rel=\"stylesheet\" href=\"style.css\">", "</head>", "<body>", "  <header>", (("    <h1 class=\"logo\">" + p.name) + "</h1>"), "    <button id=\"tombolMenu\" type=\"button\">☰ Menu</button>", menuSitus(), "  </header>", "  <main>", "    <h2>Tentang</h2>", (("    <p>" + p.name) + " adalah situs web yang dibuat dengan Create Aplikasi. Ganti tulisan ini dengan cerita tentang situsmu.</p>"), "    <p>Situs ini punya tiga halaman: Beranda, Tentang, dan Kontak. Tambah halaman baru dengan menyalin salah satu berkas .html, lalu tambah tautannya di menu.</p>", "  </main>", (("  <footer><p>&copy; " + p.name) + "</p></footer>"), "  <script src=\"script.js\"></script>", "</body>", "</html>", "");
    }
    public static String templateSitusKontak(Param p) {
        return gabung("\n", "<!DOCTYPE html>", "<html lang=\"id\">", "<head>", "  <meta charset=\"UTF-8\">", "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">", (("  <title>" + p.name) + " - Kontak</title>"), "  <link rel=\"stylesheet\" href=\"style.css\">", "</head>", "<body>", "  <header>", (("    <h1 class=\"logo\">" + p.name) + "</h1>"), "    <button id=\"tombolMenu\" type=\"button\">☰ Menu</button>", menuSitus(), "  </header>", "  <main>", "    <h2>Kontak</h2>", "    <p>Kirim pesan lewat formulir di bawah ini.</p>", "    <form id=\"formKontak\">", "      <label>Nama<br>", "      <input type=\"text\" id=\"nama\" required></label>", "      <label>Pesan<br>", "      <textarea id=\"pesan\" rows=\"4\" required></textarea></label>", "      <button type=\"submit\">Kirim pesan</button>", "    </form>", "    <p id=\"pesanTerkirim\"></p>", "  </main>", (("  <footer><p>&copy; " + p.name) + "</p></footer>"), "  <script src=\"script.js\"></script>", "</body>", "</html>", "");
    }
    public static String templateSitusCss(Param p) {
        return gabung("\n", "body {", "  font-family: sans-serif;", "  margin: 0;", "  background: #f5f5f5;", "  color: #222222;", "}", "header {", "  background: #1a73e8;", "  color: #ffffff;", "  padding: 12px 16px;", "}", ".logo {", "  margin: 0 0 8px 0;", "  font-size: 22px;", "}", "nav a {", "  color: #ffffff;", "  text-decoration: none;", "  margin-right: 16px;", "  padding: 6px 2px;", "}", "nav a.aktif {", "  border-bottom: 3px solid #ffffff;", "  font-weight: bold;", "}", "#tombolMenu {", "  display: none;", "}", "main {", "  padding: 16px;", "  max-width: 720px;", "  margin: 0 auto;", "  background: #ffffff;", "}", ".kartu-kartu {", "  display: flex;", "  gap: 12px;", "  flex-wrap: wrap;", "}", ".kartu {", "  flex: 1 1 160px;", "  border: 1px solid #dddddd;", "  border-radius: 8px;", "  padding: 12px;", "}", "button {", "  background: #1a73e8;", "  color: #ffffff;", "  border: none;", "  border-radius: 6px;", "  padding: 10px 16px;", "  font-size: 15px;", "}", "label {", "  display: block;", "  margin-bottom: 12px;", "}", "input, textarea {", "  width: 100%;", "  box-sizing: border-box;", "  padding: 8px;", "  font-size: 15px;", "}", "footer {", "  text-align: center;", "  color: #777777;", "  padding: 16px;", "}", "@media (max-width: 600px) {", "  #tombolMenu {", "    display: block;", "    margin-bottom: 8px;", "  }", "  nav {", "    display: none;", "  }", "  nav.buka {", "    display: block;", "  }", "  nav a {", "    display: block;", "    margin: 4px 0;", "  }", "}", "");
    }
    public static String templateSitusJs(Param p) {
        return gabung("\n", "// Tandai menu yang aktif sesuai halaman yang sedang dibuka.", "(function () {", "  var halaman = location.pathname.split(\"/\").pop() || \"index.html\";", "  var tautan = document.querySelectorAll(\"nav a\");", "  for (var i = 0; i < tautan.length; i++) {", "    if (tautan[i].getAttribute(\"href\") === halaman) {", "      tautan[i].classList.add(\"aktif\");", "    }", "  }", "})();", "", "// Tombol menu (layar kecil): tampilkan/sembunyikan daftar menu.", "var tombolMenu = document.getElementById(\"tombolMenu\");", "if (tombolMenu) {", "  tombolMenu.addEventListener(\"click\", function () {", "    var nav = document.querySelector(\"nav\");", "    if (nav) nav.classList.toggle(\"buka\");", "  });", "}", "", "// Sapaan di Beranda.", "var tombolSapa = document.getElementById(\"tombolSapa\");", "if (tombolSapa) {", "  tombolSapa.addEventListener(\"click\", function () {", "    document.getElementById(\"sapaan\").textContent = \"Halo! JavaScript berjalan.\";", "  });", "}", "", "// Formulir kontak: tampilkan pesan terima kasih tanpa pindah halaman.", "var formKontak = document.getElementById(\"formKontak\");", "if (formKontak) {", "  formKontak.addEventListener(\"submit\", function (e) {", "    e.preventDefault();", "    var nama = document.getElementById(\"nama\").value;", "    document.getElementById(\"pesanTerkirim\").textContent =", "      \"Terima kasih, \" + nama + \"! Pesanmu sudah dicatat.\";", "    formKontak.reset();", "  });", "}", "");
    }
    public static String templateResLayout(Param p) {
        return gabung("\n", "<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"", "    android:layout_width=\"match_parent\"", "    android:layout_height=\"match_parent\"", "    android:orientation=\"vertical\"", "    android:gravity=\"center\">", "", "    <TextView", "        android:id=\"@+id/judul\"", "        android:layout_width=\"wrap_content\"", "        android:layout_height=\"wrap_content\"", "        android:text=\"@string/teks_contoh\"", "        android:textSize=\"20sp\" />", "", "</LinearLayout>", "");
    }
    public static String templateResStrings(Param p) {
        return gabung("\n", "<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<resources>", (("    <string name=\"app_name\">" + p.name) + "</string>"), "    <string name=\"teks_contoh\">aplikasi siap dikembangkan</string>", "</resources>", "");
    }
    public static String templateResStyles(Param p) {
        return gabung("\n", "<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<resources>", "    <style name=\"app_theme\" parent=\"@android:style/Theme.DeviceDefault.Light\" />", "</resources>", "");
    }
    public static String templateJavaMain(Param p) {
        String pkg = p.packageName;
        String gerbang = "";
        if ((p.lisensi == true)) {
            gerbang = gabung("\n", "        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan", "        // layar lisensi dulu; layar utama baru tampil setelah kode", "        // lisensi yang benar dimasukkan.", "        if (!Lisensi.sudahAktif(this)) {", "            Lisensi.tampilkanLayarLisensi(this, new Runnable() {", "                @Override public void run() { tampilkanUtama(); }", "            });", "            return;", "        }", "        tampilkanUtama();");
        }
        String badan = gabung("\n", "        setContentView(R.layout.activity_main);", "        // Teks contoh di layar utama (isinya di res/values/strings.xml).", "        TextView judul = (TextView) findViewById(R.id.judul);", "        if (judul != null) judul.setText(R.string.teks_contoh);");
        if ((p.lisensi == true)) {
            return gabung("\n", (("package " + pkg) + ";"), "", "import android.app.Activity;", "import android.os.Bundle;", "import android.widget.TextView;", "import com.androlua.R;", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).", "// R diimpor dari com.androlua (namespace build.gradle): sama di build", "// perangkat (aapt2 --custom-package, lihat BangunLokal.java) dan", "// GitHub Actions (Gradle). Tanpa import ini, build online gagal", "// \"package R does not exist\" karena R ada di com.androlua, bukan", "// di paket proyek.", "// Proyek ini memakai LISENSI (versi berbayar): layar lisensi tampil", "// saat pertama dibuka; kelas Lisensi.java ada di paket yang sama.", "public class MainActivity extends Activity {", "    @Override", "    protected void onCreate(Bundle savedInstanceState) {", "        super.onCreate(savedInstanceState);", gerbang, "    }", "", "    private void tampilkanUtama() {", badan, "    }", "}", "");
        }
        return gabung("\n", (("package " + pkg) + ";"), "", "import android.app.Activity;", "import android.os.Bundle;", "import android.widget.TextView;", "import com.androlua.R;", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).", "// R diimpor dari com.androlua (namespace build.gradle): sama di build", "// perangkat (aapt2 --custom-package, lihat BangunLokal.java) dan", "// GitHub Actions (Gradle). Tanpa import ini, build online gagal", "// \"package R does not exist\" karena R ada di com.androlua, bukan", "// di paket proyek.", "public class MainActivity extends Activity {", "    @Override", "    protected void onCreate(Bundle savedInstanceState) {", "        super.onCreate(savedInstanceState);", badan, "    }", "}", "");
    }
    public static String templateKotlinMain(Param p) {
        String pkg = p.packageName;
        if ((p.lisensi == true)) {
            return gabung("\n", ("package " + pkg), "", "import android.app.Activity", "import android.os.Bundle", "import android.widget.TextView", "import com.androlua.R", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).", "// Proyek Kotlin bisa dibangun di perangkat (offline) atau lewat menu \"Bangun proyek\" (GitHub Actions).", "// Proyek ini memakai LISENSI (versi berbayar): layar lisensi tampil", "// saat pertama dibuka; kelas Lisensi.kt ada di paket yang sama.", "class MainActivity : Activity() {", "    override fun onCreate(savedInstanceState: Bundle?) {", "        super.onCreate(savedInstanceState)", "        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan", "        // layar lisensi dulu; layar utama baru tampil setelah kode", "        // lisensi yang benar dimasukkan.", "        if (!Lisensi.sudahAktif(this)) {", "            Lisensi.tampilkanLayarLisensi(this) { tampilkanUtama() }", "            return", "        }", "        tampilkanUtama()", "    }", "", "    private fun tampilkanUtama() {", "        setContentView(R.layout.activity_main)", "        // Teks contoh di layar utama (isinya di res/values/strings.xml).", "        findViewById<TextView>(R.id.judul)?.setText(R.string.teks_contoh)", "    }", "}", "");
        }
        return gabung("\n", ("package " + pkg), "", "import android.app.Activity", "import android.os.Bundle", "import android.widget.TextView", "import com.androlua.R", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).", "// Proyek Kotlin bisa dibangun di perangkat (offline) atau lewat menu \"Bangun proyek\" (GitHub Actions).", "class MainActivity : Activity() {", "    override fun onCreate(savedInstanceState: Bundle?) {", "        super.onCreate(savedInstanceState)", "        setContentView(R.layout.activity_main)", "        // Teks contoh di layar utama (isinya di res/values/strings.xml).", "        findViewById<TextView>(R.id.judul)?.setText(R.string.teks_contoh)", "    }", "}", "");
    }
    public static String templateHtmlMain(Param p) {
        String pkg = p.packageName;
        String badanWeb = gabung("\n", "        web = new WebView(this);", "        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));", "        WebSettings s = web.getSettings();", "        s.setJavaScriptEnabled(true);", "        s.setDomStorageEnabled(true);", "        s.setDatabaseEnabled(true);", "        s.setAllowFileAccess(true);", "        s.setAllowFileAccessFromFileURLs(true);", "        s.setAllowUniversalAccessFromFileURLs(true);", "        s.setLoadWithOverviewMode(true);", "        s.setUseWideViewPort(true);", "        s.setSupportZoom(true);", "        s.setBuiltInZoomControls(true);", "        s.setDisplayZoomControls(false);", "        s.setMediaPlaybackRequiresUserGesture(false);", "        web.setWebViewClient(new WebViewClient());", "        web.setWebChromeClient(new WebChromeClient());", "        setContentView(web);", "        web.loadUrl(\"file:///android_asset/index.html\");");
        if ((p.lisensi == true)) {
            return gabung("\n", (("package " + pkg) + ";"), "", "import android.annotation.SuppressLint;", "import android.app.Activity;", "import android.os.Bundle;", "import android.view.ViewGroup;", "import android.webkit.WebChromeClient;", "import android.webkit.WebSettings;", "import android.webkit.WebView;", "import android.webkit.WebViewClient;", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Memuat assets/index.html (plus style.css / script.js) di WebView.", "// Pengaturan WebView sama dengan HtmlActivity milik IDE.", "// Proyek ini memakai LISENSI (versi berbayar): layar lisensi tampil", "// saat pertama dibuka; kelas Lisensi.java ada di paket yang sama.", "public class MainActivity extends Activity {", "    private WebView web;", "", "    @SuppressLint({\"SetJavaScriptEnabled\", \"JavascriptInterface\"})", "    @Override", "    protected void onCreate(Bundle savedInstanceState) {", "        super.onCreate(savedInstanceState);", "        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan", "        // layar lisensi dulu; halaman web baru tampil setelah kode", "        // lisensi yang benar dimasukkan.", "        if (!Lisensi.sudahAktif(this)) {", "            Lisensi.tampilkanLayarLisensi(this, new Runnable() {", "                @Override public void run() { tampilkanWeb(); }", "            });", "            return;", "        }", "        tampilkanWeb();", "    }", "", "    @SuppressLint({\"SetJavaScriptEnabled\", \"JavascriptInterface\"})", "    private void tampilkanWeb() {", badanWeb, "    }", "", "    @Override", "    public void onBackPressed() {", "        if (web != null && web.canGoBack()) {", "            web.goBack();", "        } else {", "            super.onBackPressed();", "        }", "    }", "", "    @Override", "    protected void onDestroy() {", "        if (web != null) {", "            web.destroy();", "            web = null;", "        }", "        super.onDestroy();", "    }", "}", "");
        }
        return gabung("\n", (("package " + pkg) + ";"), "", "import android.annotation.SuppressLint;", "import android.app.Activity;", "import android.os.Bundle;", "import android.view.ViewGroup;", "import android.webkit.WebChromeClient;", "import android.webkit.WebSettings;", "import android.webkit.WebView;", "import android.webkit.WebViewClient;", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Memuat assets/index.html (plus style.css / script.js) di WebView.", "// Pengaturan WebView sama dengan HtmlActivity milik IDE.", "public class MainActivity extends Activity {", "    private WebView web;", "", "    @SuppressLint({\"SetJavaScriptEnabled\", \"JavascriptInterface\"})", "    @Override", "    protected void onCreate(Bundle savedInstanceState) {", "        super.onCreate(savedInstanceState);", badanWeb, "    }", "", "    @Override", "    public void onBackPressed() {", "        if (web != null && web.canGoBack()) {", "            web.goBack();", "        } else {", "            super.onBackPressed();", "        }", "    }", "", "    @Override", "    protected void onDestroy() {", "        if (web != null) {", "            web.destroy();", "            web = null;", "        }", "        super.onDestroy();", "    }", "}", "");
    }
    public static String templateComposeMain(Param p) {
        String pkg = p.packageName;
        if ((p.lisensi == true)) {
            return gabung("\n", ("package " + pkg), "", "import android.os.Bundle", "import androidx.activity.ComponentActivity", "import androidx.activity.compose.setContent", "import androidx.compose.foundation.layout.Column", "import androidx.compose.foundation.layout.fillMaxSize", "import androidx.compose.foundation.layout.padding", "import androidx.compose.material3.Button", "import androidx.compose.material3.MaterialTheme", "import androidx.compose.material3.Text", "import androidx.compose.runtime.Composable", "import androidx.compose.ui.Modifier", "import androidx.compose.ui.unit.dp", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Antarmuka dibuat dengan Jetpack Compose (bukan layout XML).", "// Proyek Compose bisa dibangun di perangkat, lewat GitHub Actions, atau di Termux (Gradle).", "// Proyek ini memakai LISENSI (versi berbayar): layar lisensi tampil", "// saat pertama dibuka; kelas Lisensi.kt ada di paket yang sama.", "class MainActivity : ComponentActivity() {", "    override fun onCreate(savedInstanceState: Bundle?) {", "        super.onCreate(savedInstanceState)", "        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan", "        // layar lisensi dulu; antarmuka Compose baru tampil setelah", "        // kode lisensi yang benar dimasukkan.", "        if (!Lisensi.sudahAktif(this)) {", "            Lisensi.tampilkanLayarLisensi(this) { tampilkanUtama() }", "            return", "        }", "        tampilkanUtama()", "    }", "", "    private fun tampilkanUtama() {", "        setContent {", "            MaterialTheme {", "                LayarUtama()", "            }", "        }", "    }", "}", "", "@Composable", "fun LayarUtama() {", "    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {", "        Text(text = \"aplikasi siap dikembangkan\")", "        Button(onClick = {}) {", "            Text(text = \"Tombol contoh\")", "        }", "    }", "}", "");
        }
        return gabung("\n", ("package " + pkg), "", "import android.os.Bundle", "import androidx.activity.ComponentActivity", "import androidx.activity.compose.setContent", "import androidx.compose.foundation.layout.Column", "import androidx.compose.foundation.layout.fillMaxSize", "import androidx.compose.foundation.layout.padding", "import androidx.compose.material3.Button", "import androidx.compose.material3.MaterialTheme", "import androidx.compose.material3.Text", "import androidx.compose.runtime.Composable", "import androidx.compose.ui.Modifier", "import androidx.compose.ui.unit.dp", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Antarmuka dibuat dengan Jetpack Compose (bukan layout XML).", "// Proyek Compose bisa dibangun di perangkat, lewat GitHub Actions, atau di Termux (Gradle).", "class MainActivity : ComponentActivity() {", "    override fun onCreate(savedInstanceState: Bundle?) {", "        super.onCreate(savedInstanceState)", "        setContent {", "            MaterialTheme {", "                LayarUtama()", "            }", "        }", "    }", "}", "", "@Composable", "fun LayarUtama() {", "    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {", "        Text(text = \"aplikasi siap dikembangkan\")", "        Button(onClick = {}) {", "            Text(text = \"Tombol contoh\")", "        }", "    }", "}", "");
    }
    /** Host Java untuk proyek Python (Chaquopy 17.2.0 + Python 3.11).
     *  MainActivity Java murni: menampilkan layout standar, lalu memulai
     *  runtime Python (Python.start(new AndroidPlatform(...))) dan memanggil
     *  fungsi main() di app/src/main/python/main.py. Kode Python-lah yang
     *  menjadi logika aplikasi; host ini hanya jembatan. Build di perangkat:
     *  kelas com.chaquo.python.* dikompilasi ke dex oleh BangunPython
     *  (lihat BangunLokal.java); build GitHub Actions: plugin Chaquopy di
     *  build.gradle (lihat tools/pack.py). SDK minimum 24 (syarat Chaquopy).
     *  Variasi lisensi sama seperti templateJavaMain. */
    public static String templatePythonMain(Param p) {
        String pkg = p.packageName;
        String gerbang = "";
        if ((p.lisensi == true)) {
            gerbang = gabung("\n", "        // Lisensi (versi berbayar): bila belum diaktifkan, tampilkan", "        // layar lisensi dulu; layar utama baru tampil setelah kode", "        // lisensi yang benar dimasukkan.", "        if (!Lisensi.sudahAktif(this)) {", "            Lisensi.tampilkanLayarLisensi(this, new Runnable() {", "                @Override public void run() { tampilkanUtama(); }", "            });", "            return;", "        }", "        tampilkanUtama();");
        }
        String badan = gabung("\n", "        setContentView(R.layout.activity_main);", "        // Teks contoh di layar utama (isinya di res/values/strings.xml).", "        TextView judul = (TextView) findViewById(R.id.judul);", "        if (judul != null) judul.setText(R.string.teks_contoh);", "        // Mulai runtime Python (Chaquopy) lalu panggil main() di main.py.", "        // Python.start hanya boleh dipanggil sekali; bila sudah jalan,", "        // langsung ambil modulnya.", "        if (!Python.isStarted()) {", "            Python.start(new AndroidPlatform(this));", "        }", "        Python.getInstance().getModule(\"main\").callAttr(\"main\");");
        if ((p.lisensi == true)) {
            return gabung("\n", (("package " + pkg) + ";"), "", "import android.app.Activity;", "import android.os.Bundle;", "import android.widget.TextView;", "import com.chaquo.python.Python;", "import com.chaquo.python.android.AndroidPlatform;", "import com.androlua.R;", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).", "// Host Java untuk proyek Python (Chaquopy): logika aplikasi ada di", "// app/src/main/python/main.py, dipanggil lewat Python.start() di bawah.", "// Build di perangkat: runtime Chaquopy dikemas BangunPython (lihat", "// BangunLokal.java). Build GitHub Actions: plugin Chaquopy di", "// build.gradle (lihat tools/pack.py). SDK minimum 24 (syarat Chaquopy).", "// R diimpor dari com.androlua (namespace build.gradle) supaya build", "// online tidak gagal \"package R does not exist\".", "// Proyek ini memakai LISENSI (versi berbayar): layar lisensi tampil", "// saat pertama dibuka; kelas Lisensi.java ada di paket yang sama.", "public class MainActivity extends Activity {", "    @Override", "    protected void onCreate(Bundle savedInstanceState) {", "        super.onCreate(savedInstanceState);", gerbang, "    }", "", "    private void tampilkanUtama() {", badan, "    }", "}", "");
        }
        return gabung("\n", (("package " + pkg) + ";"), "", "import android.app.Activity;", "import android.os.Bundle;", "import android.widget.TextView;", "import com.chaquo.python.Python;", "import com.chaquo.python.android.AndroidPlatform;", "import com.androlua.R;", "", ("// Kelas ini WAJIB bernama MainActivity dan berada tepat di package " + pkg), "// Tampilannya dari res/layout/activity_main.xml (R.layout.activity_main).", "// Host Java untuk proyek Python (Chaquopy): logika aplikasi ada di", "// app/src/main/python/main.py, dipanggil lewat Python.start() di bawah.", "// Build di perangkat: runtime Chaquopy dikemas BangunPython (lihat", "// BangunLokal.java). Build GitHub Actions: plugin Chaquopy di", "// build.gradle (lihat tools/pack.py). SDK minimum 24 (syarat Chaquopy).", "// R diimpor dari com.androlua (namespace build.gradle) supaya build", "// online tidak gagal \"package R does not exist\".", "public class MainActivity extends Activity {", "    @Override", "    protected void onCreate(Bundle savedInstanceState) {", "        super.onCreate(savedInstanceState);", badan, "    }", "}", "");
    }
    /** Kode Python contoh untuk proyek Python (Chaquopy).
     *  Berkas ini diletakkan di app/src/main/python/main.py dan dipanggil
     *  fungsi main()-nya oleh host MainActivity.java. Contoh di bawah
     *  menampilkan Toast Android ("Halo dari Python!") lewat API Java
     *  Chaquopy, dan menulis pesan ke log (terlihat di logcat). Ganti isi
     *  fungsi main() dengan logika aplikasimu; pustaka standar Python
     *  (os, json, math, dan lain-lain) sudah tersedia. */
    public static String templatePythonPy(Param p) {
        return gabung("\n", "# Contoh proyek Python (Chaquopy) — dibuat otomatis oleh Create Aplikasi.", "# Berkas ini: app/src/main/python/main.py. Fungsi main() di bawah", "# dipanggil oleh host MainActivity.java lewat Chaquopy.", "#", "# Pustaka standar Python (os, json, math, dan lain-lain) tersedia.", "# Untuk memakai kelas Android/Java dari Python, pakai jclass:", "#   from java import jclass", "#   Toast = jclass(\"android.widget.Toast\")", "# Konteks aplikasi didapat dari Python.getPlatform().getApplication().", "", "def main():", "    # Tampilkan Toast Android sebagai bukti Python memanggil Java.", "    from com.chaquo.python import Python", "    from java import jclass", "    aplikasi = Python.getPlatform().getApplication()", "    Toast = jclass(\"android.widget.Toast\")", "    Toast.makeText(aplikasi, \"Halo dari Python!\", Toast.LENGTH_LONG).show()", ("    print(\"Halo dari Python! Aplikasi " + p.name + " berjalan.\")"), "");
    }
    public static String templateLisensiJava(Param p) {
        String pkg = p.packageName;
        return gabung("\n", (("package " + pkg) + ";"), "", "// Pemeriksa lisensi (versi berbayar) — dibuat otomatis oleh Create Aplikasi.", "// Aplikasi ini memakai lisensi: saat pertama dibuka, pengguna harus", "// memasukkan kode lisensi yang diberikan pengembang.", "//", "// Alur: pengguna melihat ID perangkatnya di layar lisensi, menekan", "// \"Kirim ID Perangkat\" (membuka WhatsApp pengembang dengan ID itu),", "// pengembang membalas kode lisensi, pengguna memasukkannya di sini.", "// Kode lisensi = FNV-1a 32-bit dari (ID perangkat + \":\" + kata sandi", "// proyek), ditulis 8 huruf hex besar. Kata sandi proyek ada di init.lua", "// (lisensi_seed) — RAHASIA, jangan dibagikan. Pengembang bisa", "// menghitung kode, contoh dengan Python:", "//   h = 2166136261", "//   for b in (id_perangkat + \":\" + seed).encode():", "//       h = ((h ^ b) * 16777619) & 0xFFFFFFFF", "//   kode = \"%08X\" % h", "// Setelah kode benar dimasukkan, status aktif tersimpan dan layar", "// lisensi tidak muncul lagi.", "import android.app.Activity;", "import android.content.Context;", "import android.content.Intent;", "import android.content.SharedPreferences;", "import android.net.Uri;", "import android.provider.Settings;", "import android.text.InputType;", "import android.view.View;", "import android.view.ViewGroup;", "import android.widget.Button;", "import android.widget.EditText;", "import android.widget.LinearLayout;", "import android.widget.ScrollView;", "import android.widget.TextView;", "import android.widget.Toast;", "import java.io.UnsupportedEncodingException;", "import java.net.URLEncoder;", "import java.util.Locale;", "import java.util.UUID;", "", "public class Lisensi {", "    // Nomor WhatsApp pengembang (kode negara, tanpa +).", (("    private static final String WA = " + q((p.wa != null ? p.wa : ""))) + ";"), "    // Kata sandi proyek (lisensi_seed di init.lua). RAHASIA.", (("    private static final String SEED = " + q((p.seed != null ? p.seed : ""))) + ";"), "", "    // ID perangkat: ANDROID_ID; cadangan UUID acak yang disimpan sekali", "    // supaya stabil bila ANDROID_ID tidak tersedia.", "    public static String idPerangkat(Context ctx) {", "        String id = null;", "        try {", "            id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);", "        } catch (Throwable ignored) {", "        }", "        if (id == null || id.length() == 0 || \"9774d56d682e549c\".equals(id)) {", "            SharedPreferences sp = ctx.getSharedPreferences(\"lisensi\", Context.MODE_PRIVATE);", "            id = sp.getString(\"id_cadangan\", null);", "            if (id == null || id.length() == 0) {", "                id = UUID.randomUUID().toString();", "                sp.edit().putString(\"id_cadangan\", id).apply();", "            }", "        }", "        return id;", "    }", "", "    // Kode lisensi untuk satu ID perangkat (8 huruf hex besar).", "    public static String kodeLisensi(String id) {", "        return String.format(\"%08X\", Integer.valueOf(fnv1a(id + \":\" + SEED)));", "    }", "", "    // FNV-1a 32-bit (offset 2166136261, prima 16777619).", "    private static int fnv1a(String s) {", "        byte[] b;", "        try {", "            b = s.getBytes(\"UTF-8\");", "        } catch (UnsupportedEncodingException e) {", "            b = s.getBytes();", "        }", "        int h = 0x811c9dc5;", "        for (int i = 0; i < b.length; i++) {", "            h ^= (b[i] & 0xFF);", "            h *= 0x01000193;", "        }", "        return h;", "    }", "", "    public static boolean sudahAktif(Context ctx) {", "        return ctx.getSharedPreferences(\"lisensi\", Context.MODE_PRIVATE).getBoolean(\"aktif\", false);", "    }", "", "    public static void tandaiAktif(Context ctx) {", "        ctx.getSharedPreferences(\"lisensi\", Context.MODE_PRIVATE).edit().putBoolean(\"aktif\", true).apply();", "    }", "", "    // Layar lisensi: ID perangkat + Kirim ID Perangkat (WhatsApp) +", "    // kolom kode lisensi. Bila kode benar, tandai aktif lalu lanjut.", "    public static void tampilkanLayarLisensi(final Activity a, final Runnable lanjut) {", "        final String id = idPerangkat(a);", "        ScrollView sv = new ScrollView(a);", "        LinearLayout r = new LinearLayout(a);", "        r.setOrientation(LinearLayout.VERTICAL);", "        int pad = dp(a, 16);", "        r.setPadding(pad, dp(a, 12), pad, dp(a, 12));", "        sv.addView(r);", "", "        TextView judul = new TextView(a);", "        judul.setText(\"Aktivasi Lisensi\");", "        judul.setTextSize(20);", "        r.addView(judul);", "", "        TextView ket = new TextView(a);", "        ket.setText(\"Aplikasi berbayar ini memerlukan kode lisensi. Kirim ID perangkat di bawah ini ke pengembang lewat WhatsApp, lalu masukkan kode lisensi yang dibalas pengembang.\");", "        r.addView(ket);", "", "        TextView labelId = new TextView(a);", "        labelId.setText(\"ID Perangkat:\");", "        r.addView(labelId);", "", "        TextView teksId = new TextView(a);", "        teksId.setText(id);", "        teksId.setTextIsSelectable(true);", "        teksId.setTextSize(16);", "        r.addView(teksId);", "", "        Button kirim = new Button(a);", "        kirim.setText(\"Kirim ID Perangkat\");", "        kirim.setOnClickListener(new View.OnClickListener() {", "            @Override public void onClick(View v) {", "                try {", "                    String pesan = \"ID perangkat saya: \" + id + \"\\nMohon kode lisensinya.\";", "                    String tautan = \"https://wa.me/\" + WA + \"?text=\" + URLEncoder.encode(pesan, \"UTF-8\");", "                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tautan)));", "                } catch (Throwable t) {", "                    Toast.makeText(a, \"Gagal membuka WhatsApp: \" + t.getMessage(), Toast.LENGTH_SHORT).show();", "                }", "            }", "        });", "        r.addView(kirim);", "", "        TextView labelKode = new TextView(a);", "        labelKode.setText(\"Kode lisensi dari pengembang:\");", "        r.addView(labelKode);", "", "        final EditText kodeEdit = new EditText(a);", "        kodeEdit.setHint(\"Masukkan kode lisensi, contoh: A1B2C3D4\");", "        kodeEdit.setSingleLine(true);", "        kodeEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);", "        r.addView(kodeEdit);", "", "        final TextView status = new TextView(a);", "        status.setText(\"\");", "        r.addView(status);", "", "        Button aktif = new Button(a);", "        aktif.setText(\"Aktifkan\");", "        aktif.setOnClickListener(new View.OnClickListener() {", "            @Override public void onClick(View v) {", "                String kode = kodeEdit.getText().toString().trim().toUpperCase(Locale.US);", "                if (kode.equals(kodeLisensi(id))) {", "                    tandaiAktif(a);", "                    Toast.makeText(a, \"Lisensi aktif. Terima kasih!\", Toast.LENGTH_SHORT).show();", "                    if (lanjut != null) lanjut.run();", "                } else {", "                    status.setText(\"Kode lisensi salah. Periksa lagi kode dari pengembang.\");", "                }", "            }", "        });", "        r.addView(aktif);", "", "        a.setContentView(sv);", "        try {", "            a.getWindow().getDecorView().announceForAccessibility(\"Aktivasi Lisensi. ID Perangkat: \" + id);", "        } catch (Throwable ignored) {", "        }", "    }", "", "    private static int dp(Context ctx, float v) {", "        float density = ctx.getResources().getDisplayMetrics().density;", "        return (int) (v * density + 0.5f);", "    }", "}", "");
    }
    public static String templateLisensiKotlin(Param p) {
        String pkg = p.packageName;
        return gabung("\n", ("package " + pkg), "", "// Pemeriksa lisensi (versi berbayar) — dibuat otomatis oleh Create Aplikasi.", "// Aplikasi ini memakai lisensi: saat pertama dibuka, pengguna harus", "// memasukkan kode lisensi yang diberikan pengembang.", "//", "// Alur: pengguna melihat ID perangkatnya di layar lisensi, menekan", "// \"Kirim ID Perangkat\" (membuka WhatsApp pengembang dengan ID itu),", "// pengembang membalas kode lisensi, pengguna memasukkannya di sini.", "// Kode lisensi = FNV-1a 32-bit dari (ID perangkat + \":\" + kata sandi", "// proyek), ditulis 8 huruf hex besar. Kata sandi proyek ada di init.lua", "// (lisensi_seed) — RAHASIA, jangan dibagikan. Pengembang bisa", "// menghitung kode, contoh dengan Python:", "//   h = 2166136261", "//   for b in (id_perangkat + \":\" + seed).encode():", "//       h = ((h ^ b) * 16777619) & 0xFFFFFFFF", "//   kode = \"%08X\" % h", "// Setelah kode benar dimasukkan, status aktif tersimpan dan layar", "// lisensi tidak muncul lagi.", "import android.app.Activity", "import android.content.Context", "import android.content.Intent", "import android.content.SharedPreferences", "import android.net.Uri", "import android.provider.Settings", "import android.text.InputType", "import android.view.View", "import android.view.ViewGroup", "import android.widget.Button", "import android.widget.EditText", "import android.widget.LinearLayout", "import android.widget.ScrollView", "import android.widget.TextView", "import android.widget.Toast", "import java.net.URLEncoder", "import java.util.Locale", "import java.util.UUID", "", "object Lisensi {", "    // Nomor WhatsApp pengembang (kode negara, tanpa +).", ("    private const val WA = " + q((p.wa != null ? p.wa : ""))), "    // Kata sandi proyek (lisensi_seed di init.lua). RAHASIA.", ("    private const val SEED = " + q((p.seed != null ? p.seed : ""))), "", "    // ID perangkat: ANDROID_ID; cadangan UUID acak yang disimpan sekali", "    // supaya stabil bila ANDROID_ID tidak tersedia.", "    @JvmStatic fun idPerangkat(ctx: Context): String {", "        var id: String? = null", "        try {", "            id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)", "        } catch (ignored: Throwable) {", "        }", "        if (id == null || id.isEmpty() || id == \"9774d56d682e549c\") {", "            val sp: SharedPreferences = ctx.getSharedPreferences(\"lisensi\", Context.MODE_PRIVATE)", "            id = sp.getString(\"id_cadangan\", null)", "            if (id == null || id.isEmpty()) {", "                id = UUID.randomUUID().toString()", "                sp.edit().putString(\"id_cadangan\", id).apply()", "            }", "        }", "        return id", "    }", "", "    // Kode lisensi untuk satu ID perangkat (8 huruf hex besar).", "    @JvmStatic fun kodeLisensi(id: String): String = \"%08X\".format(fnv1a(id + \":\" + SEED))", "", "    // FNV-1a 32-bit (offset 2166136261, prima 16777619).", "    private fun fnv1a(s: String): Int {", "        val b: ByteArray = s.toByteArray(Charsets.UTF_8)", "        var h = 0x811c9dc5.toInt()", "        for (x in b) {", "            h = h xor (x.toInt() and 0xFF)", "            h *= 0x01000193", "        }", "        return h", "    }", "", "    @JvmStatic fun sudahAktif(ctx: Context): Boolean {", "        return ctx.getSharedPreferences(\"lisensi\", Context.MODE_PRIVATE).getBoolean(\"aktif\", false)", "    }", "", "    @JvmStatic fun tandaiAktif(ctx: Context) {", "        ctx.getSharedPreferences(\"lisensi\", Context.MODE_PRIVATE).edit().putBoolean(\"aktif\", true).apply()", "    }", "", "    // Layar lisensi: ID perangkat + Kirim ID Perangkat (WhatsApp) +", "    // kolom kode lisensi. Bila kode benar, tandai aktif lalu lanjut.", "    @JvmStatic fun tampilkanLayarLisensi(a: Activity, lanjut: Runnable) {", "        val id: String = idPerangkat(a)", "        val sv = ScrollView(a)", "        val r = LinearLayout(a)", "        r.orientation = LinearLayout.VERTICAL", "        val pad = dp(a, 16f)", "        r.setPadding(pad, dp(a, 12f), pad, dp(a, 12f))", "        sv.addView(r)", "", "        val judul = TextView(a)", "        judul.text = \"Aktivasi Lisensi\"", "        judul.textSize = 20f", "        r.addView(judul)", "", "        val ket = TextView(a)", "        ket.text = \"Aplikasi berbayar ini memerlukan kode lisensi. Kirim ID perangkat di bawah ini ke pengembang lewat WhatsApp, lalu masukkan kode lisensi yang dibalas pengembang.\"", "        r.addView(ket)", "", "        val labelId = TextView(a)", "        labelId.text = \"ID Perangkat:\"", "        r.addView(labelId)", "", "        val teksId = TextView(a)", "        teksId.text = id", "        teksId.setTextIsSelectable(true)", "        teksId.textSize = 16f", "        r.addView(teksId)", "", "        val kirim = Button(a)", "        kirim.text = \"Kirim ID Perangkat\"", "        kirim.setOnClickListener {", "            try {", "                val pesan = \"ID perangkat saya: \" + id + \"\\nMohon kode lisensinya.\"", "                val tautan = \"https://wa.me/\" + WA + \"?text=\" + URLEncoder.encode(pesan, \"UTF-8\")", "                a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tautan)))", "            } catch (t: Throwable) {", "                Toast.makeText(a, \"Gagal membuka WhatsApp: \" + t.message, Toast.LENGTH_SHORT).show()", "            }", "        }", "        r.addView(kirim)", "", "        val labelKode = TextView(a)", "        labelKode.text = \"Kode lisensi dari pengembang:\"", "        r.addView(labelKode)", "", "        val kodeEdit = EditText(a)", "        kodeEdit.hint = \"Masukkan kode lisensi, contoh: A1B2C3D4\"", "        kodeEdit.isSingleLine = true", "        kodeEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS", "        r.addView(kodeEdit)", "", "        val status = TextView(a)", "        status.text = \"\"", "        r.addView(status)", "", "        val aktif = Button(a)", "        aktif.text = \"Aktifkan\"", "        aktif.setOnClickListener {", "            val kode = kodeEdit.text.toString().trim().uppercase(Locale.US)", "            if (kode == kodeLisensi(id)) {", "                tandaiAktif(a)", "                Toast.makeText(a, \"Lisensi aktif. Terima kasih!\", Toast.LENGTH_SHORT).show()", "                lanjut.run()", "            } else {", "                status.text = \"Kode lisensi salah. Periksa lagi kode dari pengembang.\"", "            }", "        }", "        r.addView(aktif)", "", "        a.setContentView(sv)", "        try {", "            a.window.decorView.announceForAccessibility(\"Aktivasi Lisensi. ID Perangkat: \" + id)", "        } catch (ignored: Throwable) {", "        }", "    }", "", "    private fun dp(ctx: Context, v: Float): Int {", "        val density = ctx.resources.displayMetrics.density", "        return (v * density + 0.5f).toInt()", "    }", "}", "");
    }
    public static String templateAndroidManifest(Param p, String mainActivity) {
        java.util.List<String> izin = new java.util.ArrayList<String>();
        if (tipeNilai(p.perms).equals("table")) {
            java.util.Map<String, Boolean> kunci = new java.util.LinkedHashMap<String, Boolean>();
            for (java.util.Map.Entry<String, Boolean> __e : p.perms.entrySet()) {
                String k = __e.getKey();
                boolean v = __e.getValue();
                if (v) {
                    if (k.equals("PEMINDAIAN_WIFI")) {
                        for (String w : WIFI_PERMS) {
                            kunci.put(w, true);
                        }
                    }
                    else {
                        kunci.put(k, true);
                    }
                }
            }
            java.util.List<String> urut = new java.util.ArrayList<String>();
            for (java.util.Map.Entry<String, Boolean> __e : kunci.entrySet()) {
                String k = __e.getKey();
                boolean __abaikan = __e.getValue();
                urut.add(k);
            }
            java.util.Collections.sort(urut);
            for (String k : urut) {
                String nama = ((k.indexOf(".") >= 0) ? k : ("android.permission." + k));
                izin.add((("    <uses-permission android:name=\"" + nama) + "\" />"));
            }
        }
        java.util.List<String> bagian = new java.util.ArrayList<>(java.util.Arrays.asList("<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<!-- Manifest tampilan proyek ini. Manifest yang dipakai saat Bangun dibuat", "     otomatis dari template (alat/AndroidManifest.template.xml) dengan data", "     dari init.lua, jadi ubah nama / paket / versi di init.lua, bukan di sini. -->", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"", (("    package=\"" + p.packageName) + "\""), (("    android:versionCode=\"" + p.code) + "\""), (("    android:versionName=\"" + p.ver) + "\">"), (((("    <uses-sdk android:minSdkVersion=\"" + p.min) + "\" android:targetSdkVersion=\"") + p.target) + "\" />")));
        for (String baris : izin) {
            bagian.add(baris);
        }
        bagian.add((("    <application android:label=\"" + p.name) + "\">"));
        bagian.add((("        <activity android:name=\"" + mainActivity) + "\" android:exported=\"true\">"));
        bagian.add("            <intent-filter>");
        bagian.add("                <action android:name=\"android.intent.action.MAIN\" />");
        bagian.add("                <category android:name=\"android.intent.category.LAUNCHER\" />");
        bagian.add("            </intent-filter>");
        bagian.add("        </activity>");
        bagian.add("    </application>");
        bagian.add("</manifest>");
        bagian.add("");
        return String.join("\n", bagian);
    }
}
