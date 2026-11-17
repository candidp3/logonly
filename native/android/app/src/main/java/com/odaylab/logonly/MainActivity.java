package com.odaylab.logonly;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import androidx.core.view.WindowCompat;
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
        }
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
