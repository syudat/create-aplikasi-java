# Contoh proyek Python (Chaquopy) — dibuat otomatis oleh Create Aplikasi.
# Berkas ini: app/src/main/python/main.py. Fungsi main() di bawah
# dipanggil oleh host MainActivity.java lewat Chaquopy.
#
# Pustaka standar Python (os, json, math, dan lain-lain) tersedia.
# Untuk memakai kelas Android/Java dari Python, pakai jclass:
#   from java import jclass
#   Toast = jclass("android.widget.Toast")
# Konteks aplikasi didapat dari Python.getPlatform().getApplication().

def main():
    # Tampilkan Toast Android sebagai bukti Python memanggil Java.
    from com.chaquo.python import Python
    from java import jclass
    aplikasi = Python.getPlatform().getApplication()
    Toast = jclass("android.widget.Toast")
    Toast.makeText(aplikasi, "Halo dari Python!", Toast.LENGTH_LONG).show()
    print("Halo dari Python! Aplikasi Uji Python berjalan.")
