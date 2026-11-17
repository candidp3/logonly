package com.odaylab.logonly;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import androidx.core.view.WindowCompat;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import java.util.Collections;
import org.json.JSONObject;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    /** 상태바(위)·하단 바(아래) 뒤를 각각 다른 색으로 칠하는 배경 */
    private static class BarsDrawable extends Drawable {
        private final Paint top = new Paint(), bottom = new Paint();
        BarsDrawable(int t, int b) { top.setColor(t); bottom.setColor(b); }
        void set(int t, int b) { top.setColor(t); bottom.setColor(b); invalidateSelf(); }
        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            int mid = r.top + r.height() / 2;
            c.drawRect(r.left, r.top, r.right, mid, top);
            c.drawRect(r.left, mid, r.right, r.bottom, bottom);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private BarsDrawable bars;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 폰의 글자 크기 설정과 상관없이 앱 안 글자 크기는 앱 설정(작게·기본·크게)만 따르게 고정
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().getSettings().setTextZoom(100);
            // 화면(웹) 쪽에서 지금 바탕색을 알려주면 위·아래 바 색을 맞춤 (앱 안 밝게/어둡게 설정도 따라감)
            getBridge().getWebView().addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void setBars(final String topHex, final String bottomHex, final boolean topDark, final boolean bottomDark) {
                    runOnUiThread(() -> applyBars(topHex, bottomHex, topDark, bottomDark));
                }
            }, "LogonlyBars");
            // 구글 드라이브 백업: 사용자 본인 드라이브의 앱 전용 숨김 폴더(drive.appdata)만 쓰는 권한 받기
            getBridge().getWebView().addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void authorize(final String cbId, final boolean interactive) {
                    runOnUiThread(() -> startAuthorize(cbId, interactive));
                }
            }, "LogonlyAuth");
        }
    }

    private static final int RC_AUTH = 9101;
    private static final String DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata";
    private String pendingAuthCb;

    private void startAuthorize(String cbId, boolean interactive) {
        try {
            AuthorizationRequest req = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_APPDATA)))
                .build();
            Identity.getAuthorizationClient(this).authorize(req)
                .addOnSuccessListener(res -> {
                    if (res.hasResolution()) {
                        if (!interactive || res.getPendingIntent() == null) { authJs(cbId, null, "need_consent"); return; }
                        try {
                            pendingAuthCb = cbId;
                            startIntentSenderForResult(res.getPendingIntent().getIntentSender(), RC_AUTH, null, 0, 0, 0, null);
                        } catch (Exception e) { pendingAuthCb = null; authJs(cbId, null, "launch_failed"); }
                    } else {
                        authJs(cbId, res.getAccessToken(), res.getAccessToken() == null ? "no_token" : null);
                    }
                })
                .addOnFailureListener(e -> authJs(cbId, null, "failed:" + e.getMessage()));
        } catch (Exception e) {
            authJs(cbId, null, "failed:" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_AUTH || pendingAuthCb == null) return;
        String cb = pendingAuthCb;
        pendingAuthCb = null;
        if (resultCode != RESULT_OK || data == null) { authJs(cb, null, "canceled"); return; }
        try {
            AuthorizationResult r = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
            authJs(cb, r.getAccessToken(), r.getAccessToken() == null ? "no_token" : null);
        } catch (ApiException e) {
            authJs(cb, null, "failed:" + e.getStatusCode());
        }
    }

    private void authJs(String cbId, String token, String err) {
        final String js = "window.__lgAuthCb&&window.__lgAuthCb(" + JSONObject.quote(cbId) + ","
            + (token == null ? "null" : JSONObject.quote(token)) + ","
            + (err == null ? "null" : JSONObject.quote(err)) + ")";
        runOnUiThread(() -> {
            if (getBridge() != null && getBridge().getWebView() != null) getBridge().getWebView().evaluateJavascript(js, null);
        });
    }

    private void applyBars(String topHex, String bottomHex, boolean topDark, boolean bottomDark) {
        try {
            int t = Color.parseColor(topHex), b = Color.parseColor(bottomHex);
            View decor = getWindow().getDecorView();
            if (bars == null) { bars = new BarsDrawable(t, b); decor.setBackground(bars); }
            else bars.set(t, b);
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), decor);
            c.setAppearanceLightStatusBars(!topDark);
            c.setAppearanceLightNavigationBars(!bottomDark);
        } catch (Exception ignored) {}
    }
}
