package com.odaylab.logonly;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
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

/** 위젯 설정: 맨 위 미리보기 + 테마·배경 투명도·글씨 크기·보여줄 범위·다 한 항목 숨기기 (2026-11-24) */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private JSONObject cfg = new JSONObject();
    private FrameLayout preview;
    private int cText, cMuted, cBg;

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Intent in = getIntent();
        if (in != null && in.getExtras() != null) widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        cfg = RoutineWidgetProvider.cfg(this, widgetId);
        cText = getResources().getColor(R.color.widgetText, null);
        cMuted = getResources().getColor(R.color.widgetMuted, null);
        cBg = getResources().getColor(R.color.appBg, null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setFitsSystemWindows(true);

        TextView title = new TextView(this);
        title.setText("위젯 설정");
        title.setTextColor(cText);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(20), dp(18), dp(20), dp(10));
        root.addView(title);

        /* 미리보기: 배경화면 위에 올린 것처럼 */
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundResource(R.drawable.cfg_preview_bg);
        stage.setPadding(dp(22), dp(20), dp(22), dp(20));
        preview = new FrameLayout(this);
        stage.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(16));
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        box.addView(label("보여줄 것"));
        box.addView(chips("scope", new String[]{"now", "all"}, new String[]{"지금 시간대", "오늘 전체"}, "now"));
        box.addView(label("테마"));
        box.addView(chips("theme", new String[]{"system", "light", "dark"}, new String[]{"폰 설정 따라", "밝게", "어둡게"}, "system"));
        box.addView(label("글씨 크기"));
        box.addView(chips("font", new String[]{"s", "m", "l"}, new String[]{"작게", "보통", "크게"}, "m"));

        final TextView opLabel = label("배경 진하기 " + cfg.optInt("opacity", 100) + "%");
        box.addView(opLabel);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(cfg.optInt("opacity", 100));
        try {
            int acc = getResources().getColor(R.color.widgetAccent, null);
            sb.setProgressTintList(ColorStateList.valueOf(acc));
            sb.setThumbTintList(ColorStateList.valueOf(acc));
        } catch (Exception ignored) {}
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { put("opacity", p); opLabel.setText("배경 진하기 " + p + "%"); render(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(sb);

        Switch sw = new Switch(this);
        sw.setText("다 한 항목 숨기기");
        sw.setTextColor(cText);
        sw.setTextSize(15);
        sw.setPadding(0, dp(18), 0, dp(6));
        sw.setChecked(cfg.optBoolean("hideDone", false));
        sw.setOnCheckedChangeListener((CompoundButton v, boolean on) -> { put("hideDone", on); render(); });
        box.addView(sw);

        TextView hint = new TextView(this);
        hint.setText("체크만 하는 루틴은 위젯에서 바로 체크돼요. 시간·횟수를 남기는 루틴은 누르면 앱이 열려요.");
        hint.setTextColor(cMuted);
        hint.setTextSize(12.5f);
        hint.setPadding(0, dp(10), 0, 0);
        box.addView(hint);

        Button save = new Button(this);
        save.setText("저장");
        save.setTextColor(0xFFFFFFFF);
        save.setTextSize(16);
        save.setAllCaps(false);
        save.setBackgroundResource(R.drawable.cfg_btn);
        save.setOnClickListener(v -> done());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(dp(20), dp(6), dp(20), dp(16));
        root.addView(save, lp);

        setContentView(root);
        render();
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(cMuted);
        t.setTextSize(13);
        t.setPadding(0, dp(16), 0, dp(8));
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
            r.setPadding(dp(14), 0, dp(14), 0);
            RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            lp.setMargins(0, 0, dp(8), 0);
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
            View v = RoutineWidgetProvider.build(this, widgetId, cfg, true).apply(this, preview);
            preview.addView(v);
        } catch (Exception ignored) {}
    }

    private void done() {
        RoutineWidgetProvider.saveCfg(this, widgetId, cfg);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        m.updateAppWidget(widgetId, RoutineWidgetProvider.build(this, widgetId, cfg, false));
        m.notifyAppWidgetViewDataChanged(new int[]{widgetId}, R.id.widgetList);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}
