package com.odaylab.logonly;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
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
            // 알림: 타이머가 돌아가는 동안 알림바에 흐르는 시간 표시
            getBridge().getWebView().addJavascriptInterface(new Object() {
                @JavascriptInterface
                public void showTimer(final String title, final String text, final double startMs) {
                    runOnUiThread(() -> showTimerNotification(title, text, (long) startMs));
                }
                @JavascriptInterface
                public void clearTimer() {
                    runOnUiThread(() -> NotificationManagerCompat.from(MainActivity.this).cancel(TIMER_NOTI_ID));
                }
                @JavascriptInterface
                public boolean canNotify() {
                    return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                }
                @JavascriptInterface
                public void askPermission() {
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7301);
                    });
                }
            }, "LogonlyNotify");
        }
    }

    private static final int TIMER_NOTI_ID = 4401;
    private static final String TIMER_CHANNEL = "timer";

    private void showTimerNotification(String title, String text, long startMs) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(TIMER_CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(TIMER_CHANNEL, "타이머", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("타이머가 돌아가는 동안 알림바에 시간을 보여줘요");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(this, TIMER_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_logonly)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(startMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setColor(0xFF3F9C7C)
                .setContentIntent(pi);
            NotificationManagerCompat.from(this).notify(TIMER_NOTI_ID, b.build());
        } catch (Exception ignored) {}
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
