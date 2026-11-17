package com.androlua;

import android.app.Application;

/**
 * Application Java murni (Ronde 3).
 *
 * Menggantikan LuaApplication: hanya CrashHandler + pencatatan pembukaan
 * aplikasi (Analitik, lokal di HP). Semua inisialisasi direktori AndroLua
 * (luaExtDir, localDir, odexDir, libDir, luaMdDir, luaCpath, luaLpath),
 * getUriForPath/getUriForFile, dan getSharedPreferences milik mesin Lua
 * dibuang bersama LuaApplication — tidak ada lagi kode Lua.
 */
public class Aplikasi extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler crashHandler = CrashHandler.getInstance();
        // Daftarkan crashHandler
        crashHandler.init(getApplicationContext());
        // Analitik + App Quality Insights: catat satu pembukaan aplikasi
        // (lokal di HP, tidak dikirim ke mana pun).
        AnalitikActivity.catatBuka(getApplicationContext());
    }
}
