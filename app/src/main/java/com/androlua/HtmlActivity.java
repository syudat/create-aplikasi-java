package com.androlua;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Activity utama untuk proyek jenis HTML: memuat berkas HTML/CSS/JavaScript proyek
 * (disalin ke assets/ saat dibangun, persis seperti main.lua untuk proyek Lua) ke dalam
 * WebView dengan JavaScript, penyimpanan DOM, dan akses berkas lokal diaktifkan.
 */
public class HtmlActivity extends Activity {
    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);

        // "jalur_pratinjau": path absolut di penyimpanan (dipakai editor untuk pratinjau cepat
        // tanpa build APK dulu). Tanpa extra ini: muat dari assets/ APK terpasang seperti biasa.
        String jalurPratinjau = getIntent().getStringExtra("jalur_pratinjau");
        if (jalurPratinjau != null && jalurPratinjau.length() > 0) {
            web.loadUrl("file://" + jalurPratinjau);
        } else {
            String mulai = getIntent().getStringExtra("berkas_mulai");
            if (mulai == null || mulai.length() == 0) mulai = "index.html";
            web.loadUrl("file:///android_asset/" + mulai);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
