#!/usr/bin/env python3
"""Menyiapkan proyek Gradle untuk membangun APK dari satu proyek Pembuat Aplikasi.

Pemakaian: pack.py SLUG FOLDER_PROYEK_GRADLE [FOLDER_REPO]
Membaca  : <repo>/proyek/SLUG/init.lua  (+ semua file proyek)
Mengubah : app/build.gradle, AndroidManifest.xml, app/src/main/assets
"""
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

MAGIC = b"AES1"


def _openssl(mode, data, key, iv):
    with tempfile.TemporaryDirectory() as td:
        fin, fout = Path(td) / "in", Path(td) / "out"
        fin.write_bytes(data)
        subprocess.run(
            ["openssl", "enc", "-aes-256-cbc", mode, "-K", key.hex(), "-iv", iv.hex(),
             "-nosalt", "-in", str(fin), "-out", str(fout)],
            check=True, capture_output=True,
        )
        return fout.read_bytes()


def enkripsi(data, key):
    """AES-256-CBC, IV acak per berkas, awalan 4 byte 'AES1' (cocok dengan Kripto.java)."""
    iv = secrets.token_bytes(16)
    return MAGIC + iv + _openssl("-e", data, key, iv)


def dekripsi(data, key):
    if data[:4] != MAGIC:
        return data
    iv, ct = data[4:20], data[20:]
    return _openssl("-d", ct, key, iv)


def terenkripsi(data):
    return data[:4] == MAGIC


# ------------------------------------------------------------ validasi res/
# Aturan resource Android (aapt2) — diperiksa SEBELUM penyalinan supaya
# build di GitHub Actions gagal cepat dengan pesan yang jelas, bukan gagal
# belakangan di Gradle dengan pesan yang sulit dibaca.
KATA_KUNCI_JAVA = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
    "var", "record", "sealed", "permits", "yield",
}
_EKSTENSI_GAMBAR = {".png", ".webp", ".jpg", ".jpeg", ".gif", ".xml"}
_NAMA_RES = re.compile(r"^[a-z_][a-z0-9_.]*$")


def _saran_nama(nama):
    saran = re.sub(r"[^a-z0-9_.]+", "_", nama.lower().replace(" ", "_").replace("-", "_"))
    saran = re.sub(r"_+", "_", saran).strip("._")
    if not saran or not re.match(r"[a-z_]", saran):
        saran = "berkas_" + saran
    return saran


def _cek_magic(nama, data, rel):
    """Kembalikan pesan kesalahan atau None bila magic bytes cocok."""
    if len(data) == 0:
        return "berkas gambar kosong (0 byte)"
    if nama.endswith(".9.png"):
        if data[:4] != b"\x89PNG":
            return "bukan berkas PNG yang sah (magic bytes tidak cocok)"
        return None
    akhir = Path(nama).suffix
    if akhir == ".png":
        if data[:4] != b"\x89PNG":
            return "bukan berkas PNG yang sah (magic bytes tidak cocok)"
    elif akhir in (".jpg", ".jpeg"):
        if data[:3] != b"\xff\xd8\xff":
            return "bukan berkas JPEG yang sah (magic bytes tidak cocok)"
    elif akhir == ".webp":
        if not (data[:4] == b"RIFF" and len(data) >= 12 and data[8:12] == b"WEBP"):
            return "bukan berkas WebP yang sah (magic bytes tidak cocok)"
    elif akhir == ".gif":
        if data[:4] != b"GIF8":
            return "bukan berkas GIF yang sah (magic bytes tidak cocok)"
    return None


def _cek_xml(data, rel):
    if len(data) == 0:
        return "berkas XML kosong (0 byte)"
    try:
        ET.fromstring(data)
    except ET.ParseError as e:
        return "XML tidak valid (%s)" % e
    return None


def periksa_res(proj):
    """Validasi app/src/main/res/ proyek sebelum disalin ke tree Gradle.

    Hanya memeriksa folder res/ di struktur baru (app/src/main/res/) karena
    hanya itu yang disalin ke tree Gradle; res/ lama di root proyek masuk
    ke assets dan tidak memengaruhi build. Bila ada pelanggaran, mencetak
    ringkasan + rincian berbahasa Indonesia lalu SystemExit (gagal cepat,
    sebelum penyalinan dan sebelum build.gradle/manifest diubah).
    """
    res_dir = proj / "app" / "src" / "main" / "res"
    if not res_dir.is_dir():
        return
    masalah = []  # (rel_tampil, sebab, perbaikan)

    def tambah(rel, sebab, perbaikan):
        masalah.append(("res/" + str(rel), sebab, perbaikan))

    berkas = sorted(p for p in res_dir.rglob("*") if p.is_file())
    # (e) duplikat: nama dasar sama + ekstensi berbeda dalam SATU folder
    _kelompok = {}
    for p in berkas:
        rel = p.relative_to(res_dir)
        if len(rel.parts) >= 2:
            dasar = rel.parts[-1].split(".")[0]
            _kelompok.setdefault((rel.parts[0], dasar), []).append(rel)
    for (_folder, dasar), daftar in sorted(_kelompok.items()):
        if len(daftar) > 1:
            nama_nama = ", ".join("res/" + str(d) for d in daftar)
            tambah(daftar[0],
                   "nama dasar %r dipakai oleh %d berkas dalam folder %s yang sama (%s) — "
                   "nama resource hasil (R.%s.%s) akan bentrok" % (
                       dasar, len(daftar), _folder, nama_nama, _folder.split("-")[0], dasar),
                   "hapus salah satunya, atau gabungkan jadi satu berkas (mis. hanya %s)" % daftar[0].name)

    for p in berkas:
        rel = p.relative_to(res_dir)
        tampil = "res/" + str(rel)
        if len(rel.parts) == 1:
            tambah(rel, "berkas langsung di res/ — berkas resource harus berada di dalam "
                   "folder tipe (drawable/, layout/, values/, ...)",
                   "pindahkan berkas ke folder tipenya, mis. res/drawable/%s" % rel.parts[0])
            continue
        folder, nama = rel.parts[0], rel.parts[-1]
        tipe = folder.split("-")[0]  # drawable-hdpi -> drawable
        # (d) tidak boleh ada subfolder di folder resource (semua tipe datar)
        if len(rel.parts) > 2:
            tambah(rel, "subfolder %r di dalam %s/ tidak diizinkan — semua folder resource "
                   "Android harus datar (tanpa subfolder)" % (rel.parts[1], folder),
                   "pindahkan %s langsung ke %s/ (atau ke folder qualifier seperti %s-hdpi/)"
                   % (nama, folder, folder))
            continue
        dasar = nama.split(".")[0]
        # (f) bukan kata kunci Java (nama resource menjadi pengenal di R.java)
        if dasar in KATA_KUNCI_JAVA:
            tambah(rel, "nama dasar %r adalah kata kunci Java — tidak bisa menjadi "
                   "nama resource di R.java" % dasar,
                   "ganti nama dasarnya, mis. %r" % _saran_nama("x_" + nama))
            continue
        # (a) nama berkas (di luar values/) hanya [a-z0-9_.], diawali huruf/_
        if tipe != "values" and not _NAMA_RES.fullmatch(nama):
            tambah(rel, "nama berkas tidak valid: hanya huruf kecil (a-z), angka, garis bawah, "
                   "dan titik; diawali huruf atau _; tanpa spasi, huruf besar, atau tanda hubung",
                   "ganti namanya menjadi %r" % _saran_nama(nama))
            continue
        akhir = Path(nama).suffix
        # (b) drawable/mipmap: hanya ekstensi gambar yang didukung
        if tipe in ("drawable", "mipmap"):
            if "." not in nama:
                tambah(rel, "berkas di drawable/mipmap tanpa ekstensi — aapt2 butuh ekstensi "
                       "untuk menentukan jenis gambar",
                       "beri ekstensi yang sesuai, mis. %s.png" % nama)
                continue
            if not (nama.endswith(".9.png") or akhir in _EKSTENSI_GAMBAR):
                tambah(rel, "ekstensi %r tidak didukung di drawable/mipmap — yang diterima "
                       "hanya .png, .webp, .jpg, .jpeg, .gif, .9.png, dan .xml "
                       "(ditolak: .svg, .bmp, .ico, .tiff, dan tanpa ekstensi)" % akhir,
                       "ubah berkas ke PNG/WebP/JPEG (mis. %s.png)" % dasar)
                continue
        # layout/xml/values hanya boleh .xml
        elif tipe in ("layout", "xml", "values"):
            if akhir != ".xml":
                tambah(rel, "berkas di %s/ harus berformat .xml" % folder,
                       "pindahkan berkas non-XML keluar dari %s/, atau ubah ke .xml" % folder)
                continue
        try:
            data = p.read_bytes()
        except OSError as e:
            tambah(rel, "berkas tidak bisa dibaca (%s)" % e,
                   "periksa izin/keberadaan berkasnya lalu coba lagi")
            continue
        # (c) gambar bukan 0 byte + magic bytes cocok; XML harus valid
        if tipe in ("drawable", "mipmap"):
            if akhir == ".xml":
                salah = _cek_xml(data, rel)
            else:
                salah = _cek_magic(nama, data, rel)
            if salah:
                tambah(rel, salah,
                       "ganti dengan berkas gambar yang utuh (ekspor ulang dari editor gambar); "
                       "jangan ganti nama berkas gambar yang rusak")
        elif tipe in ("layout", "xml", "values"):
            salah = _cek_xml(data, rel)
            if salah:
                tambah(rel, salah,
                       "perbaiki isi XML-nya (pastikan tag tertutup dan tidak ada karakter liar)")

    if masalah:
        baris = ["Validasi resource GAGAL: %d masalah di app/src/main/res/ — "
                 "build dihentikan sebelum penyalinan." % len(masalah)]
        for rel, sebab, perbaikan in masalah:
            baris.append("  - %s: %s. Perbaiki: %s." % (rel, sebab, perbaikan))
        sys.exit("\n".join(baris))
    print("Validasi resource: OK (%d berkas res diperiksa)." % len(berkas))

slug = sys.argv[1]
gradle_dir = Path(sys.argv[2]).resolve()
repo_dir = Path(sys.argv[3]).resolve() if len(sys.argv) > 3 else Path.cwd()

if not re.fullmatch(r"[A-Za-z0-9_.]+", slug):
    sys.exit("Slug tidak valid: %r" % slug)

proj = repo_dir / "proyek" / slug
if not (proj / "init.lua").is_file():
    sys.exit("init.lua tidak ditemukan di %s" % proj)

# Gagal cepat: resource tidak valid ditolak sebelum build.gradle, manifest,
# atau assets diubah (lihat periksa_res di atas).
periksa_res(proj)

app = gradle_dir / "app"
gradle_file = app / "build.gradle"
manifest_file = app / "src" / "main" / "AndroidManifest.xml"
assets = app / "src" / "main" / "assets"

# ------------------------------------------------------------ baca init.lua
init = (proj / "init.lua").read_text(encoding="utf-8")
init_nocomment = re.sub(r"--[^\n]*", "", init)


def lua_str(key, default):
    m = re.search(r'(?m)^\s*%s\s*=\s*"((?:[^"\\]|\\.)*)"' % key, init_nocomment)
    if not m:
        return default
    return re.sub(r"\\(.)", lambda x: {"n": "\n"}.get(x.group(1), x.group(1)), m.group(1))


def lua_int(key, default):
    m = re.search(r"(?m)^\s*%s\s*=\s*(\d+)" % key, init_nocomment)
    return int(m.group(1)) if m else default


def lua_bool(key, default):
    # Nilai boolean Lua: true / false (tanpa tanda kutip).
    m = re.search(r"(?m)^\s*%s\s*=\s*(true|false)" % key, init_nocomment)
    if not m:
        return default
    return m.group(1) == "true"


appname = lua_str("appname", slug)
appver = lua_str("appver", "1.0")
package = lua_str("packagename", slug)
appcode = lua_int("appcode", 1)
min_sdk = lua_int("appsdk", 21)
target_sdk = lua_int("targetsdk", 34)
tipe = lua_str("tipe", "lua")
# Centang "Gunakan arsitektur terpisah 64 bit dan 32 bit" di menu Buat Project.
# true = dua APK terpisah (arm64-v8a dan armeabi-v7a); false = satu APK berisi keduanya.
split_abi = lua_bool("split_abi", False)

MAIN_ACTIVITY = {
    # Proyek HTML/Situs Web memakai MainActivity.java sendiri di paket proyek
    # (WebView yang memuat file:///android_asset/index.html), bukan lagi
    # com.androlua.HtmlActivity; proyek Compose memakai MainActivity.kt.
    # Proyek Python memakai MainActivity.java host (Chaquopy) di paket proyek.
    # Konvensi V1: proyek Java/Kotlin harus punya kelas "<packagename>.MainActivity"
    # (lihat BangunJava.java / proyek/*/MainActivity.java|kt).
    "html": "%s.MainActivity" % package,
    "situs": "%s.MainActivity" % package,
    "java": "%s.MainActivity" % package,
    "kotlin": "%s.MainActivity" % package,
    "compose": "%s.MainActivity" % package,
    "python": "%s.MainActivity" % package,
}.get(tipe, "com.androlua.Main")

# Izin: untuk Java/HTML/Situs Web/Kotlin dibaca dari AndroidManifest.xml di
# app/src/main/ (di samping folder java); untuk proyek lama yang manifest-nya
# masih di root proyek (di samping folder app/), dibaca dari sana; untuk
# proyek Lua dan proyek lama tanpa manifest, fallback ke user_permission
# di init.lua.
# Nama "android.permission.X" dinormalkan ke "X" supaya logika di bawah
# (SPECIAL, MANAGE_EXTERNAL_STORAGE) tetap seperti semula; izin bertitik lain
# dipakai apa adanya.
def _pendek(nama):
    return nama[len("android.permission."):] if nama.startswith("android.permission.") else nama


perms = []
_display = proj / "app" / "src" / "main" / "AndroidManifest.xml"
if not _display.is_file():
    _display = proj / "AndroidManifest.xml"
if tipe in ("java", "html", "situs", "kotlin", "compose", "python") and _display.is_file():
    perms = list(dict.fromkeys(_pendek(n) for n in re.findall(
        r'<uses-permission\s+android:name="([^"]+)"', _display.read_text(encoding="utf-8"))))
else:
    m = re.search(r"user_permission\s*=\s*\{(.*?)\}", init_nocomment, re.S)
    if m:
        perms = re.findall(r'"([A-Za-z0-9_.]+)"', m.group(1))

# Komponen tambahan: semua blok <service>...</service> dan <activity>...</activity>
# dari manifest proyek (mis. layanan keyboard + Pengaturan AI) disisipkan apa
# adanya ke manifest build. Activity utama dikecualikan (sudah ada di template
# lewat MAIN_ACTIVITY); tanpa ini, activity pengaturan tidak terdaftar sehingga
# startActivity() melempar ActivityNotFoundException (layar nge-blank/macet).
extra_components = ""
if tipe in ("java", "html", "situs", "kotlin", "compose", "python") and _display.is_file():
    _xml = _display.read_text(encoding="utf-8")
    _bagian = re.findall(r"<service\b[^>]*?(?:/\s*>|>.*?</service>)", _xml, re.S)
    for _blok in re.findall(r"<activity\b[^>]*?(?:/\s*>|>.*?</activity>)", _xml, re.S):
        _m = re.search(r'android:name="([^"]+)"', _blok)
        if _m:
            _nama = _m.group(1)
            _penuh = package + _nama if _nama.startswith(".") else (_nama if "." in _nama else package + "." + _nama)
            if _penuh == MAIN_ACTIVITY:
                continue
        _bagian.append(_blok)
    # Receiver dan provider proyek juga ikut (supaya aplikasi yang memakai
    # BroadcastReceiver/ContentProvider tetap jalan). Provider kelas FileProvider
    # dilewati karena template sudah mendeklarasikan FileProvider sendiri.
    for _blok in re.findall(r"<receiver\b[^>]*?(?:/\s*>|>.*?</receiver>)", _xml, re.S):
        _bagian.append(_blok)
    for _blok in re.findall(r"<provider\b[^>]*?(?:/\s*>|>.*?</provider>)", _xml, re.S):
        _m = re.search(r'android:name="([^"]+)"', _blok)
        if _m and _m.group(1) in (
                "android.content.FileProvider",
                "android.support.v4.content.FileProvider",
                "androidx.core.content.FileProvider"):
            continue
        _bagian.append(_blok)
    extra_components = "\n".join(_bagian)

if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", package):
    sys.exit("Nama paket tidak valid: %r" % package)
if min_sdk < 21:
    print("Peringatan: SDK minimum %d dinaikkan ke 21 (batas mesin)." % min_sdk)
    min_sdk = 21
# Python (Chaquopy 17.2.0): runtime Chaquopy mensyaratkan SDK minimum 24
# (Android 7.0). Bila init.lua proyek Python masih 21-23, naikkan ke 24.
if tipe == "python" and min_sdk < 24:
    print("Peringatan: proyek Python (Chaquopy) butuh SDK minimum 24; "
          "SDK minimum %d dinaikkan ke 24." % min_sdk)
    min_sdk = 24
if target_sdk > 34:
    print("Peringatan: SDK target %d diturunkan ke 34 (versi compileSdk mesin build)." % target_sdk)
    target_sdk = 34
if target_sdk < min_sdk:
    target_sdk = min_sdk

print("Aplikasi : %s" % appname)
print("Paket    : %s" % package)
print("Versi    : %s (kode %d)" % (appver, appcode))
print("SDK      : min %d / target %d" % (min_sdk, target_sdk))
print("Izin     : %s" % ", ".join(perms))

# ------------------------------------------------------------ build.gradle
g = gradle_file.read_text(encoding="utf-8")


def sub1(pattern, repl, text, label):
    new, n = re.subn(pattern, repl, text, count=1)
    if n != 1:
        sys.exit("Pola tidak ditemukan di build.gradle: " + label)
    return new


g = sub1(r'applicationId\s+["\'][^"\']*["\']', 'applicationId "%s"' % package, g, "applicationId")
g = sub1(r"minSdk\s+\d+", "minSdk %d" % min_sdk, g, "minSdk")
g = sub1(r"targetSdk\s+\d+", "targetSdk %d" % target_sdk, g, "targetSdk")
g = sub1(r"abiFilters[^\n]*", "abiFilters 'arm64-v8a', 'armeabi-v7a'", g, "abiFilters")
# Arsitektur terpisah (split_abi=true di init.lua): Gradle menghasilkan dua APK,
# app-arm64-v8a-*.apk (64 bit) dan app-armeabi-v7a-*.apk (32 bit).
# split_abi=false: satu APK berisi kedua arsitektur (fat APK).
# Blok ini ditulis ulang setiap pack.py jalan, jadi centang yang diubah di
# init.lua langsung berpengaruh pada build berikutnya.
BLOK_SPLIT = (
    "    splits {\n"
    "        abi {\n"
    "            enable true\n"
    "            reset()\n"
    "            include 'arm64-v8a', 'armeabi-v7a'\n"
    "            universalApk false\n"
    "        }\n"
    "    }\n"
)
if split_abi:
    if "splits {" not in g:
        g = sub1(r"    buildTypes \{", BLOK_SPLIT + "    buildTypes {", g, "blok splits ABI")
else:
    if BLOK_SPLIT in g:
        g = g.replace(BLOK_SPLIT, "", 1)
gradle_file.write_text(g, encoding="utf-8")

# ------------------------------------------------------------ AndroidManifest.xml
mf = manifest_file.read_text(encoding="utf-8")
mf = sub1(r'android:versionCode="[^"]*"', 'android:versionCode="%d"' % appcode, mf, "versionCode")
mf = sub1(r'android:versionName="[^"]*"', "android:versionName=%s" % quoteattr(appver), mf, "versionName")
_label_attr = "android:label=%s" % quoteattr(appname)
# Ronde 3: hanya label <application> yang diganti nama proyek (count=1).
# Label activity LAUNCHER sudah benar lewat BLOK_MAIN di bawah (tidak perlu
# count=2 seperti dulu, saat activity Main masih menempel di template).
mf = re.sub(r'android:label="[^"]*"', lambda _m: _label_attr, mf, count=1)
# Ronde 3: mesin Lua + Welcome dihapus dari manifest IDE. APK hasil kini
# langsung meluncurkan MainActivity proyek (bukan lewat Welcome yang membaca
# assets/aktivitas_utama.txt — berkas itu tidak ditulis lagi).
# 1) Hapus filter LAUNCHER dari JavaHomeActivity (layar home IDE, tidak
#    dipakai aplikasi hasil).
# 2) Sisipkan blok activity baru untuk MainActivity proyek tepat setelah
#    JavaHomeActivity, lengkap dengan filter MAIN/LAUNCHER (label = nama
#    proyek, tampil di drawer) dan filter VIEW/EDIT .alp (dulu menempel di
#    entri com.androlua.Main yang sudah dihapus dari manifest IDE).
mf = sub1(
    r'(android:name="com\.androlua\.JavaHomeActivity"[^>]*>\s*)'
    r'<intent-filter>\s*<action android:name="android\.intent\.action\.MAIN" />\s*'
    r'<category android:name="android\.intent\.category\.LAUNCHER" />\s*</intent-filter>\s*',
    r'\1', mf, "filter LAUNCHER di JavaHomeActivity")
BLOK_MAIN = (
    '        <activity\n'
    '            android:name="%s"\n'
    '            android:configChanges="orientation|screenSize|keyboardHidden"\n'
    '            %s\n'
    '            android:launchMode="singleTask"\n'
    '            android:theme="@style/app_theme"\n'
    '            android:exported="true">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.MAIN" />\n'
    '                <category android:name="android.intent.category.LAUNCHER" />\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <action android:name="android.intent.action.EDIT" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="file" />\n'
    '                <data android:host="*" />\n'
    '                <data android:pathPattern=".*\\.alp" />\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <action android:name="android.intent.action.EDIT" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="content" />\n'
    '                <data android:host="*" />\n'
    '                <data android:pathPattern=".*\\.alp" />\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <action android:name="android.intent.action.EDIT" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="file" />\n'
    '                <data android:mimeType="application/*" />\n'
    '                <data android:mimeType="audio/*" />\n'
    '                <data android:mimeType="video/*" />\n'
    '                <data android:mimeType="text/*" />\n'
    '                <data android:mimeType="*/*" />\n'
    '                <data android:host="*" />\n'
    '                <data android:pathPattern=".*\\.alp" />\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <action android:name="android.intent.action.EDIT" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="content" />\n'
    '                <data android:mimeType="application/*" />\n'
    '                <data android:mimeType="audio/*" />\n'
    '                <data android:mimeType="video/*" />\n'
    '                <data android:mimeType="text/*" />\n'
    '                <data android:mimeType="*/*" />\n'
    '                <data android:pathPattern=".*\\.alp" />\n'
    '            </intent-filter>\n'
    '        </activity>'
    % (MAIN_ACTIVITY, _label_attr))
# repl berupa fungsi supaya backslash di BLOK_MAIN (".*\\.alp") tidak
# ditafsirkan re.sub sebagai escape.
mf = sub1(
    r'(<activity\s+android:name="com\.androlua\.JavaHomeActivity"[^>]*>\s*</activity>)',
    lambda _m: _m.group(1) + "\n" + BLOK_MAIN, mf,
    "sisipkan activity utama proyek setelah JavaHomeActivity")

# izin khusus yang bukan "android.permission.*" biasa
SPECIAL = {"ACCESSIBILITY_SERVICE": None}  # layanan sudah dideklarasikan di manifest
lines = []
for p in dict.fromkeys(perms):
    if p in SPECIAL:
        continue
    full = p if "." in p else "android.permission." + p
    extra = ' tools:ignore="ScopedStorage"' if p == "MANAGE_EXTERNAL_STORAGE" else ""
    lines.append('    <uses-permission android:name="%s"%s />' % (escape(full), extra))

perm_re = re.compile(r"[ \t]*<uses-permission\b[^>]*?/>[ \t]*\n?", re.S)
first = perm_re.search(mf)
if not first:
    sys.exit("Blok uses-permission tidak ditemukan di manifest")
mf = mf[: first.start()] + "\n".join(lines) + "\n" + perm_re.sub("", mf[first.start():])
if extra_components:
    mf, _n = re.subn(r"[ \t]*</application>",
                     lambda _m: extra_components + "\n    </application>", mf, count=1)
    if _n == 0:
        sys.exit("Tag </application> tidak ditemukan di manifest")
manifest_file.write_text(mf, encoding="utf-8")

# ------------------------------------------------------------ assets
# buang aset khusus IDE agar APK hasil ringan
# Perbaikan pasca-penyerahan (mesin Lua di APK proyek): berkas mesin Lua
# milik IDE yang lolos dari daftar lama ikut dibuang — main.lua/init.lua
# milik IDE (proyek Lua menimpa keduanya dengan berkasnya sendiri di
# bawah), autotheme.lua (hanya dipakai berkas bantu IDE yang sudah
# dibuang di atas), permission.lua (data picker izin di layar Buat
# Proyek IDE, bukan runtime), keys/ (keystore penandatangan IDE),
# play/undo/redo.png (ikon toolbar editor IDE). Tidak satu pun dipakai
# saat aplikasi hasil berjalan, untuk semua tipe proyek.
for name in ["help.lua", "layout.lua", "logcat.lua", "luadoc.lua", "luadoc", "javaapi",
             "layouthelper", "projectinfo", "plugin", "project", "garuda", "alat", "alat_editor.lua",
             "main.lua", "init.lua", "autotheme.lua", "permission.lua", "keys",
             "play.png", "undo.png", "redo.png"]:
    p = assets / name
    if p.is_dir():
        shutil.rmtree(p)
    elif p.exists():
        p.unlink()

# alat bangun-di-perangkat (aapt2 dll.) tidak diperlukan di APK proyek
shutil.rmtree(app / "src" / "main" / "jniLibs", ignore_errors=True)

if tipe != "lua":
    # Proyek non-Lua (HTML/Situs Web/Java/Kotlin/Compose) tidak pernah
    # menjalankan Lua: buang runtime Lua (import.lua, penyedia import()
    # dan loadlayout()) beserta kunci enkripsinya, dan jangan kompilasi
    # modul native Lua — hapus app/src/main/jni/ (11 modul C: lua,
    # luajava, lsqlite3, gl, cjson, luv, lluv, lfs, crypt, canvas, bson)
    # dan blok ndkBuild di app/build.gradle supaya Gradle tidak
    # membangun .so mati ke dalam APK. Proyek Lua mempertahankannya
    # (libluajava.so dikompilasi NDK dari jni/ saat build Actions).
    for name in ["import.lua", "kuncilua.bin"]:
        p = assets / name
        if p.exists():
            p.unlink()
    shutil.rmtree(app / "src" / "main" / "jni", ignore_errors=True)
    g = gradle_file.read_text(encoding="utf-8")
    # Ronde 3: blok externalNativeBuild sudah dihapus dari build.gradle IDE,
    # jadi pola ini tidak ada lagi — hanya hapus bila masih ada (toleran).
    if "externalNativeBuild" in g:
        g = sub1(r"    externalNativeBuild \{\n        ndkBuild \{\n            path 'src/main/jni/Android\.mk'\n        \}\n    \}\n",
                 "", g, "blok externalNativeBuild ndkBuild di app/build.gradle (proyek non-Lua)")
    gradle_file.write_text(g, encoding="utf-8")

# ------------------------------------------------------------ enkripsi
# Reorganisasi pengaturan 2026-11-17: PACK_KEAMANAN dari workflow GitHub
# ("ya" = skrip Lua proyek dienkripsi; "tidak" = tidak dienkripsi).
# Ronde 3: IDE tidak punya lagi aset Lua (mesin dihapus), jadi loop di bawah
# hanya mengenkripsi skrip .lua/.aly milik PROYEK (init.lua proyek lama).
# kunci_lama None bila kuncilua.bin tidak ada di assets/ (sudah dihapus).
KEAMANAN = os.environ.get("PACK_KEAMANAN", "ya").strip().lower() != "tidak"
# Supaya orang yang membuka APK hasil tidak langsung membaca skrip Lua-nya, semua
# .lua/.aly di assets dienkripsi (AES-256-CBC, lihat Kripto.java). Satu APK = satu kunci
# (assets/kuncilua.bin), ditulis ke APK hasil bersama semua file proyek.
kunci_lama_file = assets / "kuncilua.bin"
kunci_lama = kunci_lama_file.read_bytes() if kunci_lama_file.is_file() else None
kunci_baru = secrets.token_bytes(32)

for f in list(assets.rglob("*")):
    if f.is_dir() or f.name == "kuncilua.bin" or f.suffix not in (".lua", ".aly"):
        continue
    data = f.read_bytes()
    if kunci_lama is not None and terenkripsi(data):
        data = dekripsi(data, kunci_lama)
    f.write_bytes(enkripsi(data, kunci_baru))

# salin file proyek ke assets / src tree Gradle.
# Struktur baru (Lua dibiarkan datar):
#   app/src/main/java/<paket>/MainActivity.java|kt  -> src/main/java|kotlin/<paket>/...
#   app/src/main/assets/index.html, style.css, ...  -> assets/index.html, ...
#   app/src/main/res/layout/*.xml, res/values/*.xml -> src/main/res/... (digabung,
#     berkas proyek menang bila namanya sama; res bawaan IDE tetap dipertahankan)
#   AndroidManifest.xml (di app/src/main/ maupun di root proyek)
#     -> dilewati, manifest build dibuat dari template mesin, lihat bagian
#     AndroidManifest.xml di atas (izinnya dibaca dari berkas itu)
# Struktur lama (datar di root proyek) tetap didukung untuk proyek lama.
java_src_dir = app / "src" / "main" / "java"
kotlin_src_dir = app / "src" / "main" / "kotlin"


def _strip_prefix(rel, prefix):
    parts = Path(prefix).parts
    if rel.parts[:len(parts)] == parts:
        return Path(*rel.parts[len(parts):])
    return None


for f in proj.rglob("*"):
    rel = f.relative_to(proj)
    if f.is_dir() or rel.parts[0] == "bin" or any(x.startswith(".") for x in rel.parts):
        continue
    if rel == Path("app/src/main/AndroidManifest.xml"):
        continue
    # Manifest di root proyek (di samping folder app/) juga dilewati:
    # manifest build dibuat dari template, bukan dari berkas ini.
    if rel == Path("AndroidManifest.xml"):
        continue
    # Resource proyek (res/layout, res/values) disalin ke tree Gradle
    # supaya R.layout / R.string tersedia saat Gradle membangun.
    stripped_res = _strip_prefix(rel, "app/src/main/res")
    if stripped_res is not None:
        dest = (app / "src" / "main" / "res") / stripped_res
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(f.read_bytes())
        continue
    if f.suffix in (".java", ".kt"):
        # .java disalin untuk proyek Java/HTML/Situs Web/Python (MainActivity.java),
        # .kt disalin untuk proyek Kotlin/Compose (MainActivity.kt); tipe
        # lain tetap dilewati.
        if f.suffix == ".java" and tipe not in ("java", "html", "situs", "python"):
            continue
        if f.suffix == ".kt" and tipe not in ("kotlin", "compose"):
            continue
        target_dir = kotlin_src_dir if f.suffix == ".kt" else java_src_dir
        dest = None
        for prefix in ("app/src/main/java", "app/src/main/kotlin"):
            stripped = _strip_prefix(rel, prefix)
            if stripped is not None:
                dest = target_dir / stripped
                break
        if dest is None:
            dest = target_dir / f.name  # proyek lama: datar di root
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(f.read_bytes())
        continue
    if f.suffix == ".py":
        # .py disalin hanya untuk proyek Python, ke src/main/python/
        # (direktori sumber Python Chaquopy — plugin mengemasnya ke
        # assets/chaquopy/app.imy); tipe lain dilewati.
        if tipe != "python":
            continue
        python_src_dir = app / "src" / "main" / "python"
        stripped_py = _strip_prefix(rel, "app/src/main/python")
        if stripped_py is not None:
            dest = python_src_dir / stripped_py
        else:
            dest = python_src_dir / f.name  # proyek lama: datar di root
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(f.read_bytes())
        continue
    stripped_assets = _strip_prefix(rel, "app/src/main/assets")
    if stripped_assets is not None:
        dest = assets / stripped_assets
    elif rel.parts[0] == "app":
        continue
    else:
        dest = assets / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    data = f.read_bytes()
    if f.suffix in (".lua", ".aly") and KEAMANAN:
        data = enkripsi(data, kunci_baru)
    dest.write_bytes(data)

# ------------------------------------------------------------ Kotlin (plugin Gradle)
if tipe == "kotlin":
    root_build_file = gradle_dir / "build.gradle"
    rb = root_build_file.read_text(encoding="utf-8")
    if "kotlin-gradle-plugin" not in rb:
        rb = rb.replace(
            "classpath 'com.android.tools.build:gradle:8.5.2'",
            "classpath 'com.android.tools.build:gradle:8.5.2'\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'",
        )
        root_build_file.write_text(rb, encoding="utf-8")
    g2 = gradle_file.read_text(encoding="utf-8")
    if "kotlin-android" not in g2:
        g2 = g2.replace(
            "apply plugin: 'com.android.application'",
            "apply plugin: 'com.android.application'\napply plugin: 'kotlin-android'",
            1,
        )
        g2 = g2.replace(
            "dependencies {",
            "dependencies {\n    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22'",
            1,
        )
        gradle_file.write_text(g2, encoding="utf-8")
    print("Proyek Kotlin: plugin kotlin-android ditambahkan ke build.gradle.")

# ------------------------------------------------------------ Compose (plugin Gradle + dependensi)
# Hanya untuk tipe "compose": injeksi dukungan Kotlin + Jetpack Compose ke
# Gradle supaya build GitHub Actions bisa mengompilasi MainActivity.kt.
# Build tipe lain tidak diubah. Setiap injeksi memakai sub1() sehingga
# gagal keras dengan pesan jelas bila pola yang dicari tidak ditemukan.
if tipe == "compose":
    root_build_file = gradle_dir / "build.gradle"
    rb = root_build_file.read_text(encoding="utf-8")
    if "kotlin-gradle-plugin" not in rb:
        rb = sub1(r"classpath 'com\.android\.tools\.build:gradle:8\.5\.2'",
                  "classpath 'com.android.tools.build:gradle:8.5.2'\n"
                  "        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0'",
                  rb, "classpath gradle 8.5.2 di build.gradle top-level (untuk plugin Kotlin Compose)")
        root_build_file.write_text(rb, encoding="utf-8")
    g2 = gradle_file.read_text(encoding="utf-8")
    if "kotlin-android" not in g2:
        g2 = sub1(r"apply plugin: 'com\.android\.application'",
                  "apply plugin: 'com.android.application'\napply plugin: 'kotlin-android'",
                  g2, "apply plugin com.android.application di app/build.gradle (untuk plugin kotlin-android Compose)")
    if "buildFeatures" not in g2:
        g2 = sub1(r"android \{\n",
                  "android {\n"
                  "    buildFeatures {\n"
                  "        compose true\n"
                  "    }\n"
                  "    composeOptions {\n"
                  "        kotlinCompilerExtensionVersion = '1.5.1'\n"
                  "    }\n",
                  g2, "blok android di app/build.gradle (untuk buildFeatures/composeOptions Compose)")
    if "compose-bom" not in g2:
        g2 = sub1(r"dependencies \{\n",
                  "dependencies {\n"
                  "    implementation 'androidx.core:core-ktx:1.10.1'\n"
                  "    implementation 'androidx.activity:activity-compose:1.7.2'\n"
                  "    implementation platform('androidx.compose:compose-bom:2023.06.01')\n"
                  "    implementation 'androidx.compose.ui:ui'\n"
                  "    implementation 'androidx.compose.ui:ui-graphics'\n"
                  "    implementation 'androidx.compose.ui:ui-tooling-preview'\n"
                  "    implementation 'androidx.compose.material3:material3'\n",
                  g2, "blok dependencies di app/build.gradle (untuk dependensi Compose)")
    gradle_file.write_text(g2, encoding="utf-8")
    print("Proyek Compose: plugin Kotlin + Compose ditambahkan ke build.gradle.")

# ------------------------------------------------------------ Python (plugin Chaquopy + runtime)
# Hanya untuk tipe "python": injeksi plugin Chaquopy (com.chaquo.python) ke
# Gradle supaya build GitHub Actions bisa mengompilasi host MainActivity.java
# dan mengemas runtime Python 3.11 + main.py ke APK. Build tipe lain tidak
# diubah. Versi Chaquopy 17.2.0 + Python 3.11 (3.11 masih mendukung ABI 32-bit
# armeabi-v7a; Python 3.12+ hanya 64-bit). SDK minimum sudah dipaksa >= 24
# di atas (syarat Chaquopy). Sintaks buildscript + apply dipakai supaya cocok
# dengan gaya build.gradle mesin ini (legacy, seperti Kotlin/Compose).
if tipe == "python":
    root_build_file = gradle_dir / "build.gradle"
    rb = root_build_file.read_text(encoding="utf-8")
    if "com.chaquo.python:gradle" not in rb:
        rb = sub1(r"classpath 'com\.android\.tools\.build:gradle:8\.5\.2'",
                  "classpath 'com.android.tools.build:gradle:8.5.2'\n"
                  "        classpath 'com.chaquo.python:gradle:17.2.0'",
                  rb, "classpath gradle 8.5.2 di build.gradle top-level (untuk plugin Chaquopy)")
        root_build_file.write_text(rb, encoding="utf-8")
    g2 = gradle_file.read_text(encoding="utf-8")
    if "com.chaquo.python" not in g2:
        g2 = sub1(r"apply plugin: 'com\.android\.application'",
                  "apply plugin: 'com.android.application'\napply plugin: 'com.chaquo.python'",
                  g2, "apply plugin com.android.application di app/build.gradle (untuk plugin Chaquopy)")
        # Blok konfigurasi Chaquopy: versi Python 3.11. Diletakkan setelah
        # blok android supaya mudah ditemukan; Gradle menerima blok
        # ekstensi di tingkat atas berkas.
        g2 = g2 + ("\nchaquopy {\n"
                   "    defaultConfig {\n"
                   "        version = \"3.11\"\n"
                   "    }\n"
                   "}\n")
    gradle_file.write_text(g2, encoding="utf-8")
    print("Proyek Python: plugin Chaquopy 17.2.0 + Python 3.11 ditambahkan ke build.gradle.")

# Tulis kunci baru hanya untuk proyek Lua (proyek non-Lua tidak punya
# berkas .lua/.aly dan kuncilua.bin-nya sudah dibuang di atas).
if tipe == "lua":
    kunci_lama_file.write_bytes(kunci_baru)

print("Selesai menyiapkan proyek (skrip Lua dienkripsi).")
