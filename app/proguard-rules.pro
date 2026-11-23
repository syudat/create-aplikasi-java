# Aturan R8/ProGuard Create Aplikasi — disiapkan 2026-11-23 (Tahap 4).
#
# STATUS: minifyEnabled masih false di build.gradle. Berkas ini sudah
# disambungkan lewat proguardFiles supaya siap; menyalakan minify WAJIB
# lewat build GitHub Actions + uji HP dulu, karena aplikasi ini sarat
# pemanggilan reflektif (mesin Lua memanggil kelas Java menurut nama,
# JNI terminal, dan kelas kerangka bawaan di paket android.*).
# Mengaktifkan tanpa uji berisiko membuang kelas yang dipakai diam-diam.
#
# Prinsip aman: pertahankan SEMUA nama kelas paket aplikasi dan paket
# bawaan; R8 hanya boleh membuang kode yang terbukti tidak terjangkau
# dari titik masuk ini — dan itupun setelah uji HP lolos.

# Titik masuk sistem
-keep class com.androlua.Aplikasi { *; }
-keep class com.androlua.PerisaiAplikasi { *; }

# Semua kelas aplikasi: dipanggil menurut nama oleh Lua/JNI/refleksi
-keep class com.androlua.** { *; }
-keep class com.nirenr.** { *; }
-keep class novan.** { *; }

# Kelas kerangka bawaan (paket android.* milik AndroLua) jangan diubah
-keep class android.** { *; }

# Metode native (JNI) tidak boleh berganti nama
-keepclasseswithmembernames class * {
    native <methods>;
}

# Atribut untuk refleksi dan anotasi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
