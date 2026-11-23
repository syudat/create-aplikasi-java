package com.androlua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Layar Buat Keystore Create Aplikasi dalam Java murni (Tahap 2E).
 *
 * Mereplikasi persis showKeystore() di main.lua: formulir keystore (nama
 * tanda tangan, kata sandi x2, nama samaran/alias, CN, OU, O, kota, provinsi,
 * kode negara, masa berlaku) + tombol "Buat Keystore" + "Kembali ke menu
 * sebelumnya". Validasi di Java, urutan dan pesan SAMA PERSIS seperti tombol
 * Buat di showKeystore(). Pembuatan keystore memanggil langsung
 * Penandatangan.buatKeystore (Java murni, tanpa Lua) dengan callback
 * BangunLokal.Listener — sama seperti yang dipakai main.lua lewat luajava.
 *
 * Keystore tersimpan di Download/project/signing/<nama>/ (kunci.p12 +
 * info.txt). Mesin Lua TIDAK disentuh: libluajava.so dan runtime Lua tetap
 * utuh untuk proyek Lua.
 */
public class KeystoreActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getPath()
            + "/Download/project";

    private EditText eName;
    private EditText ePass;
    private EditText ePass2;
    private EditText eAlias;
    private EditText eCN;
    private EditText eOU;
    private EditText eO;
    private EditText eL;
    private EditText eST;
    private EditText eC;
    private EditText eYears;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(TeksUI.t("Buat Keystore"));

        ScrollView sv = new ScrollView(this);
        LinearLayout r = newRoot();
        sv.addView(r);
        setContentView(sv);

        heading(r, TeksUI.t("Buat Keystore"), 20);
        label(r, TeksUI.t("Keystore adalah kunci tanda tangan pribadi untuk aplikasi Anda. Simpan kata sandinya baik-baik: ")
                + TeksUI.t("tanpa kunci dan kata sandi yang sama, aplikasi yang sudah terpasang tidak dapat diperbarui."), true);

        eName = field(r, TeksUI.t("Nama tanda tangan"), TeksUI.t("Masukkan nama tanda tangan di sini, contoh: Al Kautsar"));
        ePass = field(r, TeksUI.t("Kata sandi"), TeksUI.t("Masukkan kata sandi di sini, minimal 6 karakter, contoh: rahasia123"));
        ePass2 = field(r, TeksUI.t("Ulangi kata sandi"), TeksUI.t("Masukkan kata sandi yang sama sekali lagi"));
        eAlias = field(r, TeksUI.t("Nama samaran (alias)"), TeksUI.t("Masukkan nama samaran di sini, tanpa spasi, contoh: alkautsar"));
        eCN = field(r, TeksUI.t("Nama lengkap (CN)"), TeksUI.t("Masukkan nama lengkap di sini, contoh: Al Kautsar"));
        eOU = field(r, TeksUI.t("Unit organisasi (OU)"), TeksUI.t("Masukkan unit organisasi di sini, contoh: Pengembangan Aplikasi"));
        eO = field(r, TeksUI.t("Organisasi (O)"), TeksUI.t("Masukkan nama organisasi di sini, contoh: Suara Tekno Indonesia"));
        eL = field(r, TeksUI.t("Kota (L)"), TeksUI.t("Masukkan nama kota di sini, contoh: Makassar"));
        eST = field(r, TeksUI.t("Provinsi (ST)"), TeksUI.t("Masukkan nama provinsi di sini, contoh: Sulawesi Selatan"));
        eC = field(r, TeksUI.t("Kode negara (C), kosong = ID"), TeksUI.t("Masukkan kode negara 2 huruf di sini, contoh: ID"));
        eYears = field(r, TeksUI.t("Masa berlaku dalam tahun (kosong = 25)"), TeksUI.t("Masukkan masa berlaku di sini, contoh: 25"), true);
        ePass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ePass2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        button(r, TeksUI.t("Buat Keystore"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buatKeystore();
            }
        });
        button(r, TeksUI.t("Kembali ke menu sebelumnya"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        announce(TeksUI.t("Buat Keystore"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sama seperti onResume() di Lua: lanjutkan pemasangan APK yang
        // tertunda izinnya bila pengguna baru kembali dari Pengaturan.
        PasangHelper.lanjutkanPasangTertunda(this);
    }

    // ------------------------------------------------------------------ UI

    private LinearLayout newRoot() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        GayaStudio.latar(r);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        return r;
    }

    private void heading(LinearLayout parent, String text, float size) {
        TextView tv = new TextView(this);
        tv.setText(text);
        GayaStudio.judul(tv, size);
        tv.setPadding(0, dp(8), 0, dp(4));
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                tv.setAccessibilityHeading(true);
            } catch (Throwable ignored) {
            }
        }
        parent.addView(tv);
    }

    private TextView label(LinearLayout parent, String text, boolean small) {
        TextView tv = new TextView(this);
        tv.setText(text);
        if (small) {
            GayaStudio.labelKecil(tv, 12);
        } else {
            GayaStudio.label(tv, 14);
        }
        tv.setPadding(0, dp(small ? 2 : 10), 0, dp(2));
        parent.addView(tv);
        return tv;
    }

    private EditText field(LinearLayout parent, String title, String hint) {
        return field(parent, title, hint, false);
    }

    private EditText field(LinearLayout parent, String title, String hint, boolean numeric) {
        label(parent, title, false);
        EditText e = new EditText(this);
        GayaStudio.edit(e);
        e.setHint(hint);
        if (numeric) {
            // Sama seperti field(..., true) di Lua: setInputType(2).
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        parent.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button button(LinearLayout parent, String text, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(onClick);
        GayaStudio.tombol(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private int dp(float v) {
        return GayaStudio.dp(this, v);
    }

    private void announce(String text) {
        try {
            getWindow().getDecorView().announceForAccessibility(text);
        } catch (Throwable ignored) {
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------- izin penyimpanan

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // ------------------------------------------------------------- keystore

    // Sama seperti txt(e) di showKeystore(): pangkas spasi depan/belakang.
    private static String txt(EditText e) {
        String s = e.getText().toString();
        int a = 0;
        int b = s.length();
        while (a < b && Character.isWhitespace(s.charAt(a))) a++;
        while (b > a && Character.isWhitespace(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static boolean berisiKarakterTerlarang(String s) {
        // Sama seperti name:find('[/\\:*?"<>|]') di Lua.
        String terlarang = "/\\:*?\"<>|";
        for (int i = 0; i < terlarang.length(); i++) {
            if (s.indexOf(terlarang.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private static boolean aliasValid(String s) {
        // Sama seperti alias:match("^[%w_%-]+$") di Lua (%w = huruf + angka).
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'Z'
                    || c >= 'a' && c <= 'z' || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean kodeNegaraValid(String s) {
        // Sama seperti c:match("^%a%a$") di Lua.
        if (s.length() != 2) return false;
        return Character.isLetter(s.charAt(0)) && Character.isLetter(s.charAt(1));
    }

    private void buatKeystore() {
        if (!hasAccess()) {
            toast("Berikan izin penyimpanan dulu di menu utama");
            return;
        }
        final String name = txt(eName);
        final String pass = ePass.getText().toString();
        final String pass2 = ePass2.getText().toString();
        final String alias = txt(eAlias);
        final String cn = txt(eCN);
        final String ou = txt(eOU);
        final String o = txt(eO);
        final String kota = txt(eL);
        final String provinsi = txt(eST);
        String c = txt(eC).toUpperCase();
        if (c.length() == 0) c = "ID";
        final String negara = c;
        // Sama seperti math.floor(tonumber(txt(eYears)) or 25) di Lua.
        double tahunDouble;
        try {
            String t = txt(eYears);
            tahunDouble = t.length() == 0 ? 25 : Double.parseDouble(t);
        } catch (Throwable ignored) {
            tahunDouble = 25;
        }
        final int years = (int) Math.floor(tahunDouble);

        // Validasi, urutan dan pesan SAMA PERSIS seperti showKeystore().
        if (name.length() == 0) {
            toast("Nama tanda tangan belum diisi");
            return;
        }
        if (berisiKarakterTerlarang(name) || ".".equals(name) || "..".equals(name)) {
            toast("Nama tanda tangan tidak boleh memuat karakter / \\ : * ? \" < > |");
            return;
        }
        if (pass.length() < 6) {
            toast("Kata sandi minimal 6 karakter");
            return;
        }
        if (!pass.equals(pass2)) {
            toast("Kata sandi dan pengulangannya tidak sama");
            return;
        }
        if (!aliasValid(alias)) {
            toast("Nama samaran hanya boleh huruf, angka, garis bawah, dan strip");
            return;
        }
        if (cn.length() == 0) {
            toast("Nama lengkap (CN) belum diisi");
            return;
        }
        if (!kodeNegaraValid(negara)) {
            toast("Kode negara harus 2 huruf, contoh: ID");
            return;
        }
        if (years < 1 || years > 100) {
            toast("Masa berlaku harus 1 sampai 100 tahun");
            return;
        }

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(TeksUI.t("Membuat keystore"))
                .setMessage(TeksUI.t("Menyiapkan..."))
                .setCancelable(false)
                .setPositiveButton(TeksUI.t("Tutup"), null)
                .create();
        dlg.show();
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);

        // Sama seperti pemanggilan Penandatangan.buatKeystore di showKeystore():
        // kind 0 = progres, 1 = berhasil, selain itu = gagal.
        Penandatangan.buatKeystore(this, ROOT + "/signing/" + name, alias, pass,
                cn, ou, o, kota, provinsi, negara, years,
                new BangunLokal.Listener() {
                    @Override
                    public void onEvent(int kind, String msg) {
                        if (kind == BangunLokal.PROGRESS) {
                            dlg.setMessage(msg);
                            return;
                        }
                        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                        if (kind == BangunLokal.SUCCESS) {
                            dlg.setTitle(TeksUI.t("Keystore berhasil dibuat"));
                            dlg.setMessage(TeksUI.t("Tersimpan di:") + "\n" + msg + "\n" + "\n" + TeksUI.t("Saat membangun, centang ")
                                    + TeksUI.t("\"Gunakan tanda tangan pribadi\" pada project, lalu pilih ")
                                    + TeksUI.t("tanda tangan ini dan masukkan kata sandinya."));
                        } else {
                            dlg.setTitle(TeksUI.t("Gagal"));
                            dlg.setMessage(msg);
                        }
                    }
                });
    }
}
