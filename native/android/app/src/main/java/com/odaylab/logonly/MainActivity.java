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
import android.os.SystemClock;
import android.widget.RemoteViews;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.speech.tts.TextToSpeech;
import android.media.AudioAttributes;
import android.view.WindowManager;
import java.util.Locale;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
        watchKeyboard();
        // 폰의 글자 크기 설정과 상관없이 앱 안 글자 크기는 앱 설정(작게·기본·크게)만 따르게 고정
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().getSettings().setTextZoom(100);
            // 숨참기 끝 알림음(웹 오디오)이 터치 없이도 나도록
            getBridge().getWebView().getSettings().setMediaPlaybackRequiresUserGesture(false);
            // 숨참기 음성 안내: 폰의 음성 엔진으로 읽어 줌 (앱 화면에서는 브라우저 음성 기능이 없어서)
            getBridge().getWebView().addJavascriptInterface(new Object() {
                @JavascriptInterface
                public String status() { return ttsStatus; }
                @JavascriptInterface
                public void prepare() { runOnUiThread(() -> initTts()); }
                @JavascriptInterface
                public void speak(final String text) {
                    runOnUiThread(() -> {
                        if (tts == null) { initTts(); pendingSay = text; return; }
                        if (!"ok".equals(ttsStatus) && !"nolang".equals(ttsStatus)) { pendingSay = text; return; }
                        Bundle b = new Bundle();
                        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, b, "st" + SystemClock.elapsedRealtime());
                    });
                }
                @JavascriptInterface
                public void stop() { runOnUiThread(() -> { if (tts != null) tts.stop(); }); }
                @JavascriptInterface
                public void keepScreen(final boolean on) {
                    runOnUiThread(() -> {
                        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    });
                }
                @JavascriptInterface
                public void openTtsSettings() {
                    runOnUiThread(() -> {
                        try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
                        catch (Exception e) {
                            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
                        }
                    });
                }
            }, "LogonlyTTS");
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
                    runOnUiThread(() -> showTimerNotification("routine", title, (long) startMs, "기록하기", "취소"));
                }
                @JavascriptInterface
                public void showTimerEx(final String slot, final String title, final double startMs, final String primary, final String cancel) {
                    runOnUiThread(() -> showTimerNotification(slot, title, (long) startMs, primary, cancel));
                }
                @JavascriptInterface
                public void clearTimer() {
                    runOnUiThread(() -> NotificationManagerCompat.from(MainActivity.this).cancel(notiIdOf("routine")));
                }
                @JavascriptInterface
                public void clearTimerEx(final String slot) {
                    runOnUiThread(() -> NotificationManagerCompat.from(MainActivity.this).cancel(notiIdOf(slot)));
                }
                @JavascriptInterface
                public boolean canExact() {
                    if (Build.VERSION.SDK_INT < 31) return true;
                    try {
                        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
                        return am != null && am.canScheduleExactAlarms();
                    } catch (Exception e) { return true; }
                }
                @JavascriptInterface
                public void openExactSettings() {
                    runOnUiThread(() -> {
                        try {
                            if (Build.VERSION.SDK_INT >= 31) {
                                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            }
                        } catch (Exception e) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName())));
                            } catch (Exception ignored) {}
                        }
                    });
                }
                @JavascriptInterface
                public String takePending() {
                    android.content.SharedPreferences p = getSharedPreferences(TimerActionReceiver.PREFS, MODE_PRIVATE);
                    String v = p.getString(TimerActionReceiver.KEY_PENDING, "[]");
                    p.edit().putString(TimerActionReceiver.KEY_PENDING, "[]").apply();
                    return v;
                }
                @JavascriptInterface
                public String takeAction() {
                    String a = pendingTimerAction;
                    pendingTimerAction = null;
                    return a == null ? "" : a;
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
        handleTimerIntent(getIntent());
    }

    /** 음성 엔진: 처음 쓸 때 준비. 영어(미국) 음성이 없으면 폰 기본 언어로 읽음 */
    private TextToSpeech tts;
    private String ttsStatus = "none";
    private String pendingSay;
    private void initTts() {
        if (tts != null) return;
        ttsStatus = "init";
        tts = new TextToSpeech(getApplicationContext(), st -> {
            if (st != TextToSpeech.SUCCESS) { ttsStatus = "fail"; return; }
            try {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            } catch (Exception ignored) {}
            int r = tts.setLanguage(Locale.US);
            ttsStatus = (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) ? "nolang" : "ok";
            tts.setSpeechRate(1.0f);
            if (pendingSay != null) {
                String t = pendingSay; pendingSay = null;
                tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "st0");
            }
        });
    }

    @Override
    public void onDestroy() {
        try { if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } } catch (Exception ignored) {}
        super.onDestroy();
    }

    /** 키보드가 웹 화면을 얼마나 가리는지(CSS px) 웹에 알려줌 → 아래쪽 입력칸이 키보드 위로 보이게 */
    private int lastKbPx = -1;
    private void watchKeyboard() {
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            try {
                if (getBridge() == null || getBridge().getWebView() == null) return;
                WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(decor);
                if (wi == null) return;
                int ime = wi.isVisible(WindowInsetsCompat.Type.ime()) ? wi.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
                View wv = getBridge().getWebView();
                int[] loc = new int[2];
                wv.getLocationOnScreen(loc);
                int[] dloc = new int[2];
                decor.getLocationOnScreen(dloc);
                int kbTop = dloc[1] + decor.getHeight() - ime;
                int overlap = ime > 0 ? Math.max(0, loc[1] + wv.getHeight() - kbTop) : 0;
                float d = getResources().getDisplayMetrics().density;
                int css = Math.round(overlap / d);
                if (css == lastKbPx) return;
                lastKbPx = css;
                getBridge().getWebView().evaluateJavascript("window.__lgKb&&window.__lgKb(" + css + ")", null);
            } catch (Exception ignored) {}
        });
    }

    private static final int TIMER_NOTI_ID = 4401;
    private static final String TIMER_CHANNEL = "timer";

    /** 알림 자리: routine(일상 루틴·FREE TIME 타이머) = 4401, care:항목(데일리케어 시간 기록) = 4410~4489 */
    private static int notiIdOf(String slot) {
        if (slot == null || slot.equals("routine")) return TIMER_NOTI_ID;
        return 4410 + (Math.abs(slot.hashCode()) % 80);
    }

    private void showTimerNotification(String slot, String title, long startMs, String primary, String cancel) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(TIMER_CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(TIMER_CHANNEL, "타이머", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("타이머가 돌아가는 동안 알림바에 시간을 보여줘요");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            int id = notiIdOf(slot);
            int rc = (id - 4400) * 10;
            getSharedPreferences(TimerActionReceiver.PREFS, MODE_PRIVATE).edit()
                .putLong("start_" + (slot == null ? "routine" : slot), startMs)
                .putString("name_" + (slot == null ? "routine" : slot), title == null ? "" : title.split(" · ")[0])
                .apply();
            RemoteViews rv = new RemoteViews(getPackageName(), R.layout.noti_timer);
            rv.setTextViewText(R.id.noti_name, title);
            long base = SystemClock.elapsedRealtime() - Math.max(0, System.currentTimeMillis() - startMs);
            rv.setChronometer(R.id.noti_chrono, base, null, true);
            rv.setTextViewText(R.id.noti_stop, primary == null || primary.isEmpty() ? "기록하기" : primary);
            rv.setOnClickPendingIntent(R.id.noti_stop, actionIntent("stop", slot, rc + 1));
            if (cancel == null || cancel.isEmpty()) {
                rv.setViewVisibility(R.id.noti_cancel, android.view.View.GONE);
            } else {
                rv.setViewVisibility(R.id.noti_cancel, android.view.View.VISIBLE);
                rv.setTextViewText(R.id.noti_cancel, cancel);
                rv.setOnClickPendingIntent(R.id.noti_cancel, actionIntent("cancel", slot, rc + 2));
            }
            NotificationCompat.Builder b = new NotificationCompat.Builder(this, TIMER_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_logonly)
                .setContentTitle(title)
                .setContentText("진행 중")
                .setCustomContentView(rv)
                .setCustomBigContentView(rv)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setWhen(startMs)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setColor(0xFF3F9C7C)
                .setContentIntent(actionIntent("open", slot, rc));
            NotificationManagerCompat.from(this).notify(id, b.build());
        } catch (Exception ignored) {}
    }

    private String pendingTimerAction;

    private PendingIntent actionIntent(String action, String slot, int code) {
        if ("stop".equals(action) || "cancel".equals(action)) {
            /* 기록·취소는 앱을 열지 않고 처리 */
            Intent b = new Intent(this, TimerActionReceiver.class)
                .putExtra("timerAction", action)
                .putExtra("timerSlot", slot == null ? "routine" : slot);
            return PendingIntent.getBroadcast(this, code, b, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        Intent i = new Intent(this, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("timerAction", action)
            .putExtra("timerSlot", slot == null ? "routine" : slot);
        return PendingIntent.getActivity(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 알림의 '종료하고 기록'·'취소'를 눌러 앱이 열렸을 때: 화면(웹)에 전달 */
    private void handleTimerIntent(Intent intent) {
        if (intent == null) return;
        String a = intent.getStringExtra("timerAction");
        if (a == null) return;
        String slot = intent.getStringExtra("timerSlot");
        intent.removeExtra("timerAction");
        intent.removeExtra("timerSlot");
        pendingTimerAction = a + "|" + (slot == null ? "routine" : slot);
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().evaluateJavascript("window.lgTimerAction&&window.lgTimerAction()", null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleTimerIntent(intent);
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
