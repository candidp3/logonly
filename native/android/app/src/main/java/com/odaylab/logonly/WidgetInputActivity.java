package com.odaylab.logonly;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.util.HashMap;

/**
 * 위젯에서 루틴 이름을 누르면 홈 화면 위에 앱의 '그 기록창'만 띄움 (2026-11-25, B안).
 * 앱과 같은 주소(https://localhost)로 같은 화면 파일을 열어서 기록이 앱과 똑같이 저장됨.
 * 기록창이 닫히면 이 창도 닫히고 위젯이 새로 그려짐.
 */
public class WidgetInputActivity extends Activity {
    private WebView wv;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        String rid = getIntent().getStringExtra("rid");
        if (rid == null || rid.isEmpty()) { finish(); return; }
        String kind = getIntent().getStringExtra("kind");
        if ("check".equals(kind) || "count".equals(kind) || "start".equals(kind) || "stop".equals(kind)) {
            /* 4×4 목록에서 체크 등: 창 없이 바로 처리 */
            RoutineWidgetProvider.handleTap(this, getIntent());
            finish(); overridePendingTransition(0, 0);
            return;
        }
        wv = new WebView(this);
        wv.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!"localhost".equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
                try {
                    InputStream in = getAssets().open("public" + path);
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Cache-Control", "no-cache");
                    return new WebResourceResponse(mime(path), "UTF-8", 200, "OK", h, in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", new HashMap<>(), null);
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !"localhost".equals(req.getUrl().getHost()); /* 바깥 주소로는 이동하지 않음 */
            }
        });
        wv.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void close() { runOnUiThread(() -> finish()); }
            @JavascriptInterface
            public String takePending() {
                SharedPreferences p = getSharedPreferences(TimerActionReceiver.PREFS, MODE_PRIVATE);
                String v = p.getString(TimerActionReceiver.KEY_PENDING, "[]");
                p.edit().putString(TimerActionReceiver.KEY_PENDING, "[]").apply();
                return v;
            }
        }, "LogonlyWQ");
        wv.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void update(String json) {
                SharedPreferences p = getSharedPreferences(RoutineWidgetProvider.PREFS, MODE_PRIVATE);
                if (json != null && json.equals(p.getString(RoutineWidgetProvider.KEY_DATA, ""))) return;
                p.edit().putString(RoutineWidgetProvider.KEY_DATA, json == null ? "{}" : json).apply();
                try { WidgetKit.refreshAll(WidgetInputActivity.this); } catch (Exception ignored) {}
            }
            @JavascriptInterface
            public String takeOpen() { return ""; }
        }, "LogonlyWidget");
        setContentView(wv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.loadUrl("https://localhost/index.html?wq=" + Uri.encode(rid));
    }

    static String mime(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    @Override
    public void onBackPressed() {
        if (wv != null) wv.evaluateJavascript("try{closeRoutineInputSheet&&closeRoutineInputSheet()}catch(e){}", null);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (wv != null) try { WidgetKit.refreshAll(this); } catch (Exception ignored) {}
        if (wv != null) { try { wv.destroy(); } catch (Exception ignored) {} }
        super.onDestroy();
    }

    static Intent intent(android.content.Context ctx, String rid) {
        return new Intent(ctx, WidgetInputActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("rid", rid)
            .setData(Uri.parse("logonly://wq/" + rid));
    }
}
