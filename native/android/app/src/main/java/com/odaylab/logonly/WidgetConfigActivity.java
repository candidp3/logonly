package com.odaylab.logonly;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

/** 위젯 설정 (2026-11-24 v2): 위 미리보기 · 아래 카드로 묶은 설정 · 맨 아래 저장 */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID, kind = WidgetKit.KIND_TODAY;
    private JSONObject cfg = new JSONObject();
    private FrameLayout preview;
    private int cText, cMuted, cBg, cCard, cLine, cAcc;

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Intent in = getIntent();
        if (in != null && in.getExtras() != null) widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        kind = WidgetKit.kindOf(this, widgetId);
        cfg = RoutineWidgetProvider.cfg(this, widgetId);
        cText = getResources().getColor(R.color.widgetText, null);
        cMuted = getResources().getColor(R.color.widgetMuted, null);
        cBg = getResources().getColor(R.color.appBg, null);
        cCard = getResources().getColor(R.color.widgetBg, null);
        cLine = getResources().getColor(R.color.widgetLine, null);
        cAcc = getResources().getColor(R.color.widgetAccent, null);
        getWindow().setStatusBarColor(cBg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setFitsSystemWindows(true);

        /* 머리: 닫기 · 제목 · 위젯 종류 */
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(10), dp(20), dp(6));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(cMuted);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("위젯 설정");
        title.setTextColor(cText);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(this);
        sub.setText(WidgetKit.kindName(kind));
        sub.setTextColor(cMuted);
        sub.setTextSize(12.5f);
        tt.addView(title); tt.addView(sub);
        head.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(head);

        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), dp(16));
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        /* 미리보기: 둥근 배경화면 위에 */
        FrameLayout stage = new FrameLayout(this);
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF8FB8D8, 0xFFC7B3E0, 0xFFF2B8A6});
        sg.setCornerRadius(dp(22));
        stage.setBackground(sg);
        stage.setClipToOutline(true);
        int ph = kind == WidgetKit.KIND_TODAY ? 300 : 72;
        stage.setPadding(dp(18), dp(kind == WidgetKit.KIND_TODAY ? 18 : 28), dp(18), dp(kind == WidgetKit.KIND_TODAY ? 18 : 28));
        preview = new FrameLayout(this);
        int pw = kind == WidgetKit.KIND_SMALL ? dp(170) : ViewGroup.LayoutParams.MATCH_PARENT;
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(pw, dp(ph));
        plp.gravity = Gravity.CENTER;
        stage.addView(preview, plp);
        box.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView ph2 = new TextView(this);
        ph2.setText("미리보기 · 고르는 대로 바로 바뀌어요");
        ph2.setTextColor(cMuted);
        ph2.setTextSize(12);
        ph2.setGravity(Gravity.CENTER);
        ph2.setPadding(0, dp(8), 0, dp(4));
        box.addView(ph2);

        /* 카드 1: 보이는 내용 (투데이만) */
        if (kind == WidgetKit.KIND_TODAY) {
            LinearLayout c1 = card(box, "보이는 내용");
            c1.addView(chips("scope", new String[]{"now", "all"}, new String[]{"지금 시간대", "오늘 전체"}, "now"));
            Switch sw = new Switch(this);
            sw.setText("다 한 항목 숨기기");
            sw.setTextColor(cText);
            sw.setTextSize(15);
            sw.setPadding(0, dp(14), 0, dp(2));
            sw.setChecked(cfg.optBoolean("hideDone", false));
            try { sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{cAcc, 0xFFFFFFFF})); } catch (Exception ignored) {}
            sw.setOnCheckedChangeListener((CompoundButton v, boolean on) -> { put("hideDone", on); render(); });
            c1.addView(sw);
        }

        /* 카드 2: 모양 */
        LinearLayout c2 = card(box, "모양");
        c2.addView(small("테마"));
        c2.addView(chips("theme", new String[]{"system", "light", "dark"}, new String[]{"폰 설정 따라", "밝게", "어둡게"}, "system"));
        c2.addView(small("글씨 크기"));
        c2.addView(chips("font", new String[]{"s", "m", "l"}, new String[]{"작게", "보통", "크게"}, "m"));
        final TextView opLabel = small("배경 진하기 · " + cfg.optInt("opacity", 100) + "%");
        c2.addView(opLabel);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(cfg.optInt("opacity", 100));
        try { sb.setProgressTintList(ColorStateList.valueOf(cAcc)); sb.setThumbTintList(ColorStateList.valueOf(cAcc)); } catch (Exception ignored) {}
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { put("opacity", p); opLabel.setText("배경 진하기 · " + p + "%"); render(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        c2.addView(sb);

        TextView hint = new TextView(this);
        hint.setText(kind == WidgetKit.KIND_PROJECT_BAR
            ? "프로젝트를 누르면 PROJECTS가, 오른쪽 동그라미를 누르면 그 화면이 바로 열려요."
            : "체크만 하는 루틴은 위젯에서 바로 체크돼요. 시간·횟수를 남기는 루틴은 누르면 앱이 열려요.");
        hint.setTextColor(cMuted);
        hint.setTextSize(12.5f);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(dp(4), dp(14), dp(4), 0);
        box.addView(hint);

        Button save = new Button(this);
        save.setText("저장");
        save.setTextColor(0xFFFFFFFF);
        save.setTextSize(16);
        save.setAllCaps(false);
        save.setStateListAnimator(null);
        save.setBackgroundResource(R.drawable.cfg_btn);
        save.setOnClickListener(v -> done());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(dp(16), dp(6), dp(16), dp(14));
        root.addView(save, lp);

        setContentView(root);
        render();
    }

    private LinearLayout card(LinearLayout parent, String name) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(cMuted);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(6), dp(18), 0, dp(8));
        parent.addView(t);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cCard);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), cLine & 0x66FFFFFF);
        c.setBackground(g);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        parent.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private TextView small(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(cMuted);
        t.setTextSize(13);
        t.setPadding(0, dp(12), 0, dp(8));
        return t;
    }

    private void put(String k, Object v) { try { cfg.put(k, v); } catch (Exception ignored) {} }

    private View chips(final String key, final String[] vals, String[] names, String def) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        String cur = cfg.optString(key, def);
        for (int i = 0; i < vals.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(View.generateViewId());
            r.setText(names[i]);
            r.setButtonDrawable(null);
            r.setBackgroundResource(R.drawable.cfg_chip);
            r.setTextColor(cText);
            r.setTextSize(14);
            r.setGravity(Gravity.CENTER);
            r.setPadding(dp(6), 0, dp(6), 0);
            RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(0, dp(40), 1);
            if (i > 0) lp.setMargins(dp(8), 0, 0, 0);
            g.addView(r, lp);
            final String val = vals[i];
            if (val.equals(cur)) r.setChecked(true);
            r.setOnCheckedChangeListener((CompoundButton b, boolean on) -> { if (on) { put(key, val); render(); } });
        }
        return g;
    }

    private void render() {
        try {
            preview.removeAllViews();
            android.widget.RemoteViews rv = kind == WidgetKit.KIND_TODAY
                ? RoutineWidgetProvider.build(this, widgetId, cfg, true)
                : WidgetKit.buildSimple(this, widgetId, cfg, kind);
            preview.addView(rv.apply(this, preview));
        } catch (Exception ignored) {}
    }

    private void done() {
        RoutineWidgetProvider.saveCfg(this, widgetId, cfg);
        WidgetKit.refreshAll(this);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}
