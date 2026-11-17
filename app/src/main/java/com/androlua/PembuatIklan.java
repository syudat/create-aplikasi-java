package com.androlua;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * Pembuat Iklan (Tahap 3I).
 *
 * Menghasilkan potongan kode iklan SIAP TEMPEL untuk proyek pengguna
 * (Java / Kotlin / Lua) + perubahan manifest + panduan langkah demi
 * langkah, untuk 3 penyedia (AdMob, Unity Ads, AppLovin) x 3 jenis
 * iklan (Banner, Interstitial, Rewarded). Hasil disimpan sebagai
 * berkas teks di proyek (mis. iklan-admob-banner.txt).
 *
 * KETERBATASAN YANG JUJUR (ditulis juga di IklanActivity dan di berkas
 * hasil): ini TEMPLATE KODE + PANDUAN, BUKAN SDK iklan — SDK penyedia
 * (play-services-ads / Unity Ads / AppLovin MAX) TIDAK disertakan di
 * IDE karena menambah ukuran dan butuh kunci/ID sendiri; iklan
 * sungguhan baru tampil setelah pengembang: (1) membuat akun penyedia,
 * (2) membuat aplikasi + unit iklan, (3) menempel ID unit sendiri,
 * (4) menambah dependensi SDK di build.gradle proyek, (5) membangun
 * APK. ID yang disertakan di sini adalah ID UJI resmi (AdMob) atau
 * tempat kosong (Unity/AppLovin) — jangan dipakai untuk iklan
 * sungguhan.
 *
 * Murni Java (tanpa API Android) supaya bisa diuji di JRE biasa.
 */
public class PembuatIklan {

    /** Penyedia iklan yang didukung. */
    public static final String[] PENYEDIA = {"AdMob", "Unity Ads", "AppLovin"};

    /** Jenis iklan yang didukung. */
    public static final String[] JENIS = {"Banner", "Interstitial", "Rewarded"};

    /** Nama berkas hasil di proyek: iklan-admob-banner.txt */
    public static String namaBerkas(String penyedia, String jenis) {
        String p = penyedia == null ? "admob" : penyedia.toLowerCase().replace(" ", "-");
        String j = jenis == null ? "banner" : jenis.toLowerCase();
        return "iklan-" + p + "-" + j + ".txt";
    }

    /**
     * ID unit iklan untuk PENGUJIAN (bukan ID sungguhan). AdMob memakai
     * ID uji resmi Google; Unity memakai Game ID contoh + nama placement
     * uji; AppLovin tidak punya ID uji publik, jadi dikembalikan
     * tempat kosong yang harus diganti.
     */
    public static String idUji(String penyedia, String jenis) {
        if ("AdMob".equals(penyedia)) {
            if ("Interstitial".equals(jenis)) {
                return "ca-app-pub-3940256099942544/1033173712";
            }
            if ("Rewarded".equals(jenis)) {
                return "ca-app-pub-3940256099942544/5224354917";
            }
            return "ca-app-pub-3940256099942544/6300978111";
        }
        if ("Unity Ads".equals(penyedia)) {
            if ("Interstitial".equals(jenis)) return "Interstitial_Android";
            if ("Rewarded".equals(jenis)) return "Rewarded_Android";
            return "Banner_Android";
        }
        // AppLovin: tidak ada ID uji publik.
        if ("Interstitial".equals(jenis)) return "MASUKKAN_AD_UNIT_ID_INTERSTITIAL";
        if ("Rewarded".equals(jenis)) return "MASUKKAN_AD_UNIT_ID_REWARDED";
        return "MASUKKAN_AD_UNIT_ID_BANNER";
    }

    /** Game ID contoh Unity Ads (untuk pengujian; ganti dengan Game ID sendiri). */
    public static final String UNITY_GAME_ID_UJI = "1234567";

    /** Kunci SDK contoh AppLovin (tempat kosong; ganti dengan SDK Key sendiri). */
    public static final String APPLOVIN_SDK_KEY_UJI = "MASUKKAN_SDK_KEY_APPLOVIN";

    /** ID aplikasi contoh AdMob (untuk meta-data manifest; ganti dengan ID aplikasi sendiri). */
    public static final String ADMOB_APP_ID_UJI = "ca-app-pub-3940256099942544~3347511713";

    /**
     * Potongan kode Java untuk menampilkan iklan. idUnit kosong memakai
     * idUji(). Mengembalikan "" bila penyedia/jenis tidak dikenal.
     */
    public static String kodeJava(String penyedia, String jenis, String idUnit) {
        String id = idUnit == null || idUnit.trim().isEmpty()
                ? idUji(penyedia, jenis) : idUnit.trim();
        if ("AdMob".equals(penyedia)) return kodeJavaAdMob(jenis, id);
        if ("Unity Ads".equals(penyedia)) return kodeJavaUnity(jenis, id);
        if ("AppLovin".equals(penyedia)) return kodeJavaAppLovin(jenis, id);
        return "";
    }

    /**
     * Potongan kode Kotlin untuk menampilkan iklan. idUnit kosong memakai
     * idUji(). Mengembalikan "" bila penyedia/jenis tidak dikenal.
     */
    public static String kodeKotlin(String penyedia, String jenis, String idUnit) {
        String id = idUnit == null || idUnit.trim().isEmpty()
                ? idUji(penyedia, jenis) : idUnit.trim();
        if ("AdMob".equals(penyedia)) return kodeKotlinAdMob(jenis, id);
        if ("Unity Ads".equals(penyedia)) return kodeKotlinUnity(jenis, id);
        if ("AppLovin".equals(penyedia)) return kodeKotlinAppLovin(jenis, id);
        return "";
    }

    /**
     * Potongan kode Lua (luajava) untuk menampilkan iklan. idUnit kosong
     * memakai idUji(). Mengembalikan "" bila penyedia/jenis tidak dikenal.
     */
    public static String kodeLua(String penyedia, String jenis, String idUnit) {
        String id = idUnit == null || idUnit.trim().isEmpty()
                ? idUji(penyedia, jenis) : idUnit.trim();
        if ("AdMob".equals(penyedia)) return kodeLuaAdMob(jenis, id);
        if ("Unity Ads".equals(penyedia)) return kodeLuaUnity(jenis, id);
        if ("AppLovin".equals(penyedia)) return kodeLuaAppLovin(jenis, id);
        return "";
    }

    /** Perubahan AndroidManifest.xml yang dibutuhkan penyedia. */
    public static String perubahanManifest(String penyedia) {
        if ("AdMob".equals(penyedia)) {
            return "Tambahkan di dalam <application> di AndroidManifest.xml:\n"
                    + "    <meta-data\n"
                    + "        android:name=\"com.google.android.gms.ads.APPLICATION_ID\"\n"
                    + "        android:value=\"" + ADMOB_APP_ID_UJI + "\" />\n"
                    + "Ganti nilai di atas dengan ID aplikasi AdMob sendiri.\n"
                    + "Izin INTERNET sudah ada di proyek (atau tambahkan\n"
                    + "<uses-permission android:name=\"android.permission.INTERNET\" />).";
        }
        if ("Unity Ads".equals(penyedia)) {
            return "Tidak ada meta-data khusus di manifest. Pastikan izin ini ada:\n"
                    + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                    + "    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />";
        }
        if ("AppLovin".equals(penyedia)) {
            return "Tambahkan di dalam <application> di AndroidManifest.xml:\n"
                    + "    <meta-data\n"
                    + "        android:name=\"applovin.sdk.key\"\n"
                    + "        android:value=\"" + APPLOVIN_SDK_KEY_UJI + "\" />\n"
                    + "Ganti nilai di atas dengan SDK Key AppLovin sendiri.\n"
                    + "Izin INTERNET sudah ada di proyek (atau tambahkan\n"
                    + "<uses-permission android:name=\"android.permission.INTERNET\" />).";
        }
        return "";
    }

    /** Panduan langkah demi langkah (buat akun sampai iklan tampil). */
    public static String panduan(String penyedia, String jenis) {
        StringBuilder sb = new StringBuilder();
        sb.append("Langkah-langkah memakai iklan ").append(penyedia)
                .append(" (").append(jenis).append("):\n");
        if ("AdMob".equals(penyedia)) {
            sb.append("1. Buka admob.google.com, buat akun, lalu buat Aplikasi baru.\n");
            sb.append("2. Di aplikasi itu, buat Unit iklan (").append(jenis)
                    .append(") dan salin ID unitnya.\n");
            sb.append("3. Ganti ID uji di kode di atas dengan ID unit sendiri.\n");
            sb.append("4. Ganti ID aplikasi di meta-data manifest dengan ID aplikasi sendiri.\n");
            sb.append("5. Tambah dependensi di app/build.gradle proyek:\n");
            sb.append("   implementation 'com.google.android.gms:play-services-ads:23.0.0'\n");
            sb.append("6. Tempel kode Java/Kotlin di MainActivity (atau kode Lua di main.lua).\n");
            sb.append("7. Bangun APK dan uji dulu dengan ID uji sebelum memakai ID sendiri.");
        } else if ("Unity Ads".equals(penyedia)) {
            sb.append("1. Buka dashboard.unity3d.com, buat akun, lalu buat Proyek Unity.\n");
            sb.append("2. Aktifkan Unity Ads untuk proyek itu dan catat Game ID.\n");
            sb.append("3. Buat placement ").append(jenis)
                    .append(" (nama placement uji: ").append(idUji(penyedia, jenis)).append(").\n");
            sb.append("4. Ganti Game ID uji (").append(UNITY_GAME_ID_UJI)
                    .append(") dan placement dengan milik sendiri.\n");
            sb.append("5. Tambah dependensi di app/build.gradle proyek:\n");
            sb.append("   implementation 'com.unity3d.ads:unity-ads:4.12.0'\n");
            sb.append("6. Tempel kode Java/Kotlin di MainActivity (atau kode Lua di main.lua).\n");
            sb.append("7. Bangun APK dan uji dulu dengan mode uji (testMode = true).");
        } else if ("AppLovin".equals(penyedia)) {
            sb.append("1. Buka applovin.com, buat akun, lalu buat Aplikasi baru.\n");
            sb.append("2. Buat Ad Unit (").append(jenis)
                    .append(") dan salin Ad Unit ID + SDK Key.\n");
            sb.append("3. Ganti tempat kosong SDK Key dan Ad Unit ID di kode di atas.\n");
            sb.append("4. Ganti SDK Key di meta-data manifest dengan SDK Key sendiri.\n");
            sb.append("5. Tambah dependensi di app/build.gradle proyek:\n");
            sb.append("   implementation 'com.applovin:applovin-sdk:12.0.0'\n");
            sb.append("6. Tempel kode Java/Kotlin di MainActivity (atau kode Lua di main.lua).\n");
            sb.append("7. Bangun APK dan uji dulu sebelum memakai ID sendiri.");
        } else {
            sb.append("Penyedia tidak dikenal.");
        }
        sb.append("\n\nCATATAN JUJUR: berkas ini hanya template kode + panduan. "
                + "SDK iklan TIDAK disertakan di IDE (menambah ukuran APK dan "
                + "butuh kunci/ID sendiri). Iklan sungguhan baru tampil setelah "
                + "langkah di atas selesai semua.");
        return sb.toString();
    }

    /** Seluruh isi berkas hasil (kode + manifest + panduan + catatan). */
    public static String isiBerkas(String penyedia, String jenis, String idUnit) {
        String id = idUnit == null || idUnit.trim().isEmpty()
                ? idUji(penyedia, jenis) : idUnit.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("Pembuat Iklan — ").append(penyedia).append(" (").append(jenis).append(")\n");
        sb.append("ID unit: ").append(id).append("\n\n");
        sb.append("=== Kode Java (MainActivity.java) ===\n");
        sb.append(kodeJava(penyedia, jenis, id)).append("\n\n");
        sb.append("=== Kode Kotlin (MainActivity.kt) ===\n");
        sb.append(kodeKotlin(penyedia, jenis, id)).append("\n\n");
        sb.append("=== Kode Lua (main.lua) ===\n");
        sb.append(kodeLua(penyedia, jenis, id)).append("\n\n");
        sb.append("=== Perubahan AndroidManifest.xml ===\n");
        sb.append(perubahanManifest(penyedia)).append("\n\n");
        sb.append("=== Panduan ===\n");
        sb.append(panduan(penyedia, jenis)).append("\n");
        return sb.toString();
    }

    /**
     * Tulis berkas hasil ke akar proyek. Mengembalikan berkas bila
     * berhasil, null bila gagal (penyedia/jenis tidak dikenal atau
     * folder proyek tidak ada).
     */
    public static File tulisKeProyek(File dirProyek, String penyedia, String jenis,
                                     String idUnit) {
        if (dirProyek == null || !dirProyek.isDirectory()) return null;
        if (!"AdMob".equals(penyedia) && !"Unity Ads".equals(penyedia)
                && !"AppLovin".equals(penyedia)) {
            return null;
        }
        if (!"Banner".equals(jenis) && !"Interstitial".equals(jenis)
                && !"Rewarded".equals(jenis)) {
            return null;
        }
        File keluar = new File(dirProyek, namaBerkas(penyedia, jenis));
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(keluar), "UTF-8");
            try {
                w.write(isiBerkas(penyedia, jenis, idUnit));
            } finally {
                w.close();
            }
            return keluar;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ------------------------------------------------------- AdMob

    private static String kodeJavaAdMob(String jenis, String id) {
        if ("Banner".equals(jenis)) {
            return "// Banner AdMob — tempel di MainActivity sesudah setContentView.\n"
                    + "// Tambahkan AdView di activity_main.xml:\n"
                    + "//   <com.google.android.gms.ads.AdView\n"
                    + "//       android:id=\"@+id/iklanBanner\"\n"
                    + "//       android:layout_width=\"wrap_content\"\n"
                    + "//       android:layout_height=\"wrap_content\"\n"
                    + "//       ads:adSize=\"BANNER\"\n"
                    + "//       ads:adUnitId=\"" + id + "\" />\n"
                    + "import com.google.android.gms.ads.AdRequest;\n"
                    + "import com.google.android.gms.ads.AdView;\n"
                    + "import com.google.android.gms.ads.MobileAds;\n"
                    + "\n"
                    + "MobileAds.initialize(this);\n"
                    + "AdView iklan = findViewById(R.id.iklanBanner);\n"
                    + "iklan.loadAd(new AdRequest.Builder().build());";
        }
        if ("Interstitial".equals(jenis)) {
            return "// Interstitial AdMob — tampilkan di saat jeda (mis. sesudah level).\n"
                    + "import com.google.android.gms.ads.AdRequest;\n"
                    + "import com.google.android.gms.ads.MobileAds;\n"
                    + "import com.google.android.gms.ads.interstitial.InterstitialAd;\n"
                    + "import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;\n"
                    + "\n"
                    + "MobileAds.initialize(this);\n"
                    + "InterstitialAd.load(this, \"" + id + "\",\n"
                    + "        new AdRequest.Builder().build(),\n"
                    + "        new InterstitialAdLoadCallback() {\n"
                    + "            @Override\n"
                    + "            public void onAdLoaded(InterstitialAd iklan) {\n"
                    + "                iklan.show(MainActivity.this);\n"
                    + "            }\n"
                    + "        });";
        }
        // Rewarded
        return "// Rewarded AdMob — beri hadiah setelah iklan ditonton penuh.\n"
                + "import com.google.android.gms.ads.AdRequest;\n"
                + "import com.google.android.gms.ads.MobileAds;\n"
                + "import com.google.android.gms.ads.rewarded.RewardedAd;\n"
                + "import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;\n"
                + "\n"
                + "MobileAds.initialize(this);\n"
                + "RewardedAd.load(this, \"" + id + "\",\n"
                + "        new AdRequest.Builder().build(),\n"
                + "        new RewardedAdLoadCallback() {\n"
                + "            @Override\n"
                + "            public void onAdLoaded(RewardedAd iklan) {\n"
                + "                iklan.show(MainActivity.this, reward -> {\n"
                + "                    // Beri hadiah di sini (mis. +100 koin).\n"
                + "                });\n"
                + "            }\n"
                + "        });";
    }

    private static String kodeKotlinAdMob(String jenis, String id) {
        if ("Banner".equals(jenis)) {
            return "// Banner AdMob — tempel di MainActivity sesudah setContentView.\n"
                    + "// AdView di activity_main.xml sama seperti versi Java.\n"
                    + "import com.google.android.gms.ads.AdRequest\n"
                    + "import com.google.android.gms.ads.AdView\n"
                    + "import com.google.android.gms.ads.MobileAds\n"
                    + "\n"
                    + "MobileAds.initialize(this)\n"
                    + "val iklan = findViewById<AdView>(R.id.iklanBanner)\n"
                    + "iklan.loadAd(AdRequest.Builder().build())";
        }
        if ("Interstitial".equals(jenis)) {
            return "// Interstitial AdMob — tampilkan di saat jeda.\n"
                    + "import com.google.android.gms.ads.AdRequest\n"
                    + "import com.google.android.gms.ads.MobileAds\n"
                    + "import com.google.android.gms.ads.interstitial.InterstitialAd\n"
                    + "import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback\n"
                    + "\n"
                    + "MobileAds.initialize(this)\n"
                    + "InterstitialAd.load(this, \"" + id + "\", AdRequest.Builder().build(),\n"
                    + "    object : InterstitialAdLoadCallback() {\n"
                    + "        override fun onAdLoaded(iklan: InterstitialAd) {\n"
                    + "            iklan.show(this@MainActivity)\n"
                    + "        }\n"
                    + "    })";
        }
        return "// Rewarded AdMob — beri hadiah setelah iklan ditonton penuh.\n"
                + "import com.google.android.gms.ads.AdRequest\n"
                + "import com.google.android.gms.ads.MobileAds\n"
                + "import com.google.android.gms.ads.rewarded.RewardedAd\n"
                + "import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback\n"
                + "\n"
                + "MobileAds.initialize(this)\n"
                + "RewardedAd.load(this, \"" + id + "\", AdRequest.Builder().build(),\n"
                + "    object : RewardedAdLoadCallback() {\n"
                + "        override fun onAdLoaded(iklan: RewardedAd) {\n"
                + "            iklan.show(this@MainActivity) { reward ->\n"
                + "                // Beri hadiah di sini (mis. +100 koin).\n"
                + "            }\n"
                + "        }\n"
                + "    })";
    }

    private static String kodeLuaAdMob(String jenis, String id) {
        if ("Banner".equals(jenis)) {
            return "-- Banner AdMob (Lua, lewat luajava) — tempel sesudah layout dibuat.\n"
                    + "require \"import\"\n"
                    + "import \"com.google.android.gms.ads.*\"\n"
                    + "MobileAds.initialize(activity)\n"
                    + "local iklan = AdView(activity)\n"
                    + "iklan.setAdUnitId(\"" + id + "\")\n"
                    + "iklan.setAdSize(AdSize.BANNER)\n"
                    + "iklan.loadAd(AdRequest.Builder().build())\n"
                    + "-- Tambahkan iklan ke layout dengan layout.addView(iklan).";
        }
        if ("Interstitial".equals(jenis)) {
            return "-- Interstitial AdMob (Lua) — tampilkan di saat jeda.\n"
                    + "require \"import\"\n"
                    + "import \"com.google.android.gms.ads.*\"\n"
                    + "import \"com.google.android.gms.ads.interstitial.*\"\n"
                    + "MobileAds.initialize(activity)\n"
                    + "InterstitialAd.load(activity, \"" + id + "\",\n"
                    + "  AdRequest.Builder().build(), luajava.createProxy(\n"
                    + "  \"com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback\", {\n"
                    + "  onAdLoaded = function(iklan) iklan.show(activity) end }))";
        }
        return "-- Rewarded AdMob (Lua) — beri hadiah setelah iklan ditonton penuh.\n"
                + "require \"import\"\n"
                + "import \"com.google.android.gms.ads.*\"\n"
                + "import \"com.google.android.gms.ads.rewarded.*\"\n"
                + "MobileAds.initialize(activity)\n"
                + "RewardedAd.load(activity, \"" + id + "\",\n"
                + "  AdRequest.Builder().build(), luajava.createProxy(\n"
                + "  \"com.google.android.gms.ads.rewarded.RewardedAdLoadCallback\", {\n"
                + "  onAdLoaded = function(iklan)\n"
                + "    iklan.show(activity, function(reward)\n"
                + "      -- Beri hadiah di sini (mis. +100 koin).\n"
                + "    end)\n"
                + "  end }))";
    }

    // ------------------------------------------------------- Unity Ads

    private static String kodeJavaUnity(String jenis, String id) {
        return "// Unity Ads (" + jenis + ") — tempel di MainActivity.\n"
                + "// Game ID uji: " + UNITY_GAME_ID_UJI
                + " (ganti dengan Game ID sendiri).\n"
                + "import com.unity3d.ads.IUnityAdsInitializationListener;\n"
                + "import com.unity3d.ads.UnityAds;\n"
                + "\n"
                + "UnityAds.initialize(this, \"" + UNITY_GAME_ID_UJI + "\", true,\n"
                + "        new IUnityAdsInitializationListener() {\n"
                + "            @Override\n"
                + "            public void onInitializationComplete() {\n"
                + "                UnityAds.load(\"" + id + "\");\n"
                + "            }\n"
                + "            @Override\n"
                + "            public void onInitializationFailed(UnityAds.UnityAdsInitializationError e,\n"
                + "                    String pesan) {\n"
                + "            }\n"
                + "        });\n"
                + "// Tampilkan saat dibutuhkan:\n"
                + "// UnityAds.show(this, \"" + id + "\");";
    }

    private static String kodeKotlinUnity(String jenis, String id) {
        return "// Unity Ads (" + jenis + ") — tempel di MainActivity.\n"
                + "// Game ID uji: " + UNITY_GAME_ID_UJI
                + " (ganti dengan Game ID sendiri).\n"
                + "import com.unity3d.ads.IUnityAdsInitializationListener\n"
                + "import com.unity3d.ads.UnityAds\n"
                + "\n"
                + "UnityAds.initialize(this, \"" + UNITY_GAME_ID_UJI + "\", true,\n"
                + "    object : IUnityAdsInitializationListener {\n"
                + "        override fun onInitializationComplete() {\n"
                + "            UnityAds.load(\"" + id + "\")\n"
                + "        }\n"
                + "        override fun onInitializationFailed(\n"
                + "            e: UnityAds.UnityAdsInitializationError, pesan: String) {\n"
                + "        }\n"
                + "    })\n"
                + "// Tampilkan saat dibutuhkan:\n"
                + "// UnityAds.show(this, \"" + id + "\")";
    }

    private static String kodeLuaUnity(String jenis, String id) {
        return "-- Unity Ads (" + jenis + ") (Lua, lewat luajava).\n"
                + "-- Game ID uji: " + UNITY_GAME_ID_UJI
                + " (ganti dengan Game ID sendiri).\n"
                + "require \"import\"\n"
                + "import \"com.unity3d.ads.*\"\n"
                + "UnityAds.initialize(activity, \"" + UNITY_GAME_ID_UJI + "\", true,\n"
                + "  luajava.createProxy(\"com.unity3d.ads.IUnityAdsInitializationListener\", {\n"
                + "  onInitializationComplete = function() UnityAds.load(\"" + id + "\") end }))\n"
                + "-- Tampilkan saat dibutuhkan:\n"
                + "-- UnityAds.show(activity, \"" + id + "\")";
    }

    // ------------------------------------------------------- AppLovin

    private static String kodeJavaAppLovin(String jenis, String id) {
        if ("Banner".equals(jenis)) {
            return "// Banner AppLovin MAX — tempel di MainActivity.\n"
                    + "// SDK Key: " + APPLOVIN_SDK_KEY_UJI
                    + " (ganti dengan SDK Key sendiri).\n"
                    + "import com.applovin.mediation.ads.MaxAdView;\n"
                    + "import com.applovin.sdk.AppLovinSdk;\n"
                    + "\n"
                    + "AppLovinSdk.getInstance(this).initializeSdk(config -> {\n"
                    + "    MaxAdView iklan = new MaxAdView(\"" + id + "\", this);\n"
                    + "    // Tambahkan iklan ke layout, lalu:\n"
                    + "    iklan.loadAd();\n"
                    + "});";
        }
        if ("Interstitial".equals(jenis)) {
            return "// Interstitial AppLovin MAX — tampilkan di saat jeda.\n"
                    + "import com.applovin.mediation.ads.MaxInterstitialAd;\n"
                    + "import com.applovin.sdk.AppLovinSdk;\n"
                    + "\n"
                    + "AppLovinSdk.getInstance(this).initializeSdk(config -> {\n"
                    + "    MaxInterstitialAd iklan = MaxInterstitialAd.create(\"" + id + "\", this);\n"
                    + "    iklan.loadAd();\n"
                    + "    // Tampilkan saat dibutuhkan: iklan.showAd();\n"
                    + "});";
        }
        return "// Rewarded AppLovin MAX — beri hadiah setelah iklan ditonton penuh.\n"
                + "import com.applovin.mediation.ads.MaxRewardedAd;\n"
                + "import com.applovin.sdk.AppLovinSdk;\n"
                + "\n"
                + "AppLovinSdk.getInstance(this).initializeSdk(config -> {\n"
                + "    MaxRewardedAd iklan = MaxRewardedAd.getInstance(\"" + id + "\", this);\n"
                + "    iklan.loadAd();\n"
                + "    // Tampilkan saat dibutuhkan: iklan.showAd();\n"
                + "    // Hadiah diterima lewat MaxRewardedAdListener.onUserRewarded().\n"
                + "});";
    }

    private static String kodeKotlinAppLovin(String jenis, String id) {
        if ("Banner".equals(jenis)) {
            return "// Banner AppLovin MAX — tempel di MainActivity.\n"
                    + "// SDK Key: " + APPLOVIN_SDK_KEY_UJI
                    + " (ganti dengan SDK Key sendiri).\n"
                    + "import com.applovin.mediation.ads.MaxAdView\n"
                    + "import com.applovin.sdk.AppLovinSdk\n"
                    + "\n"
                    + "AppLovinSdk.getInstance(this).initializeSdk {\n"
                    + "    val iklan = MaxAdView(\"" + id + "\", this)\n"
                    + "    // Tambahkan iklan ke layout, lalu:\n"
                    + "    iklan.loadAd()\n"
                    + "}";
        }
        if ("Interstitial".equals(jenis)) {
            return "// Interstitial AppLovin MAX — tampilkan di saat jeda.\n"
                    + "import com.applovin.mediation.ads.MaxInterstitialAd\n"
                    + "import com.applovin.sdk.AppLovinSdk\n"
                    + "\n"
                    + "AppLovinSdk.getInstance(this).initializeSdk {\n"
                    + "    val iklan = MaxInterstitialAd.create(\"" + id + "\", this)\n"
                    + "    iklan.loadAd()\n"
                    + "    // Tampilkan saat dibutuhkan: iklan.showAd()\n"
                    + "}";
        }
        return "// Rewarded AppLovin MAX — beri hadiah setelah iklan ditonton penuh.\n"
                + "import com.applovin.mediation.ads.MaxRewardedAd\n"
                + "import com.applovin.sdk.AppLovinSdk\n"
                + "\n"
                + "AppLovinSdk.getInstance(this).initializeSdk {\n"
                + "    val iklan = MaxRewardedAd.getInstance(\"" + id + "\", this)\n"
                + "    iklan.loadAd()\n"
                + "    // Tampilkan saat dibutuhkan: iklan.showAd()\n"
                + "    // Hadiah diterima lewat MaxRewardedAdListener.onUserRewarded().\n"
                + "}";
    }

    private static String kodeLuaAppLovin(String jenis, String id) {
        if ("Banner".equals(jenis)) {
            return "-- Banner AppLovin MAX (Lua, lewat luajava).\n"
                    + "-- SDK Key: " + APPLOVIN_SDK_KEY_UJI
                    + " (ganti dengan SDK Key sendiri).\n"
                    + "require \"import\"\n"
                    + "import \"com.applovin.mediation.ads.*\"\n"
                    + "import \"com.applovin.sdk.*\"\n"
                    + "AppLovinSdk.getInstance(activity).initializeSdk(function(config)\n"
                    + "  local iklan = MaxAdView(\"" + id + "\", activity)\n"
                    + "  -- Tambahkan iklan ke layout, lalu:\n"
                    + "  iklan.loadAd()\n"
                    + "end)";
        }
        if ("Interstitial".equals(jenis)) {
            return "-- Interstitial AppLovin MAX (Lua, lewat luajava).\n"
                    + "require \"import\"\n"
                    + "import \"com.applovin.mediation.ads.*\"\n"
                    + "import \"com.applovin.sdk.*\"\n"
                    + "AppLovinSdk.getInstance(activity).initializeSdk(function(config)\n"
                    + "  local iklan = MaxInterstitialAd.create(\"" + id + "\", activity)\n"
                    + "  iklan.loadAd()\n"
                    + "  -- Tampilkan saat dibutuhkan: iklan.showAd()\n"
                    + "end)";
        }
        return "-- Rewarded AppLovin MAX (Lua, lewat luajava).\n"
                + "require \"import\"\n"
                + "import \"com.applovin.mediation.ads.*\"\n"
                + "import \"com.applovin.sdk.*\"\n"
                + "AppLovinSdk.getInstance(activity).initializeSdk(function(config)\n"
                + "  local iklan = MaxRewardedAd.getInstance(\"" + id + "\", activity)\n"
                + "  iklan.loadAd()\n"
                + "  -- Tampilkan saat dibutuhkan: iklan.showAd()\n"
                + "  -- Hadiah diterima lewat MaxRewardedAdListener.onUserRewarded().\n"
                + "end)";
    }
}
