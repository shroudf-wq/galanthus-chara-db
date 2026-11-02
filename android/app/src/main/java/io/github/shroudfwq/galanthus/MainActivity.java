package io.github.shroudfwq.galanthus;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Galanthus Android 版：同梱のWebアプリを専用の画面で開く */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/web/index.html";
    private static final int FILE_REQ = 1;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setGeolocationEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        // 同梱ファイルを https://appassets.androidplatform.net/assets/ として配信する（IndexedDB や fetch が普通のサイトと同じように使える）
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost()) && "https".equals(u.getScheme())) return false;
                // wiki や GitHub などの外部リンクは普段使いのブラウザで開く
                String sc = u.getScheme();
                if ("https".equals(sc) || "http".equals(sc)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent i = params.createIntent();
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(i, FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // カメラ・マイクなどは使わない
                request.deny();
            }
        });

        web.addJavascriptInterface(new Bridge(), "GalanthusAndroid");

        if (state != null) web.restoreState(state);
        if (web.getUrl() == null) web.loadUrl(START);
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        if (req != FILE_REQ) { super.onActivityResult(req, code, data); return; }
        if (fileCb == null) return;
        Uri[] res = null;
        if (code == RESULT_OK && data != null) {
            ClipData cd = data.getClipData();
            if (cd != null && cd.getItemCount() > 0) {
                res = new Uri[cd.getItemCount()];
                for (int i = 0; i < cd.getItemCount(); i++) res[i] = cd.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                res = new Uri[]{data.getData()};
            }
        }
        fileCb.onReceiveValue(res);
        fileCb = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("GalanthusAndroid"); web.destroy(); }
        super.onDestroy();
    }

    /** Webアプリから呼ぶ窓口：バックアップ・CSV・画像を「ダウンロード」フォルダへ保存する */
    private class Bridge {
        @JavascriptInterface
        public String saveFile(String name, String mime, String base64) {
            String safe = (name == null ? "galanthus" : name).replaceAll("[^A-Za-z0-9._-]", "_");
            if (safe.length() > 80) safe = safe.substring(safe.length() - 80);
            if (safe.isEmpty() || safe.startsWith(".")) safe = "galanthus" + safe;
            String type = "application/octet-stream";
            if ("application/json".equals(mime) || "image/png".equals(mime) || "text/csv".equals(mime)) type = mime;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (bytes.length > 200 * 1024 * 1024) return "too large";
                String where;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, type);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Galanthus");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "error";
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) return "error";
                        os.write(bytes);
                    }
                    where = "ダウンロード/Galanthus";
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return "error";
                    try (FileOutputStream os = new FileOutputStream(new File(dir, safe))) { os.write(bytes); }
                    where = dir.getAbsolutePath();
                }
                final String msg = "保存しました：" + where + "/" + safe;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                return "ok";
            } catch (Exception e) {
                return "error";
            }
        }
    }
}
