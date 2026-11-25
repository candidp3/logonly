package com.odaylab.logonly;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * 홈 화면 위젯 '지금 할 일' (2026-11-24 v2)
 * 잘 만든 할 일 위젯(Todoist·TickTick·MS To Do)처럼: 위젯마다 테마·배경 투명도·글씨 크기·보여줄 범위·다 한 항목 숨기기,
 * 위젯 오른쪽 위 설정 버튼, 설정 화면 맨 위에 미리보기.
 */
public class RoutineWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "logonly_widget";
    static final String KEY_DATA = "data";
    static final String KEY_OPEN = "open";
    static final String ACTION_TAP = "com.odaylab.logonly.WIDGET_TAP";
    static final String[] WD = {"일", "월", "화", "수", "목", "금", "토"};

    /* ---------- 설정 ---------- */
    static JSONObject cfg(Context ctx, int id) {
        try { return new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cfg_" + id, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    static void saveCfg(Context ctx, int id, JSONObject c) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("cfg_" + id, c.toString()).apply();
    }
    static boolean dark(Context ctx, JSONObject c) {
        String t = c.optString("theme", "system");
        if ("light".equals(t)) return false;
        if ("dark".equals(t)) return true;
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    /** {배경, 글자, 흐린 글자, 선, 강조} */
    static int[] colors(Context ctx, JSONObject c) {
        boolean dk = dark(ctx, c);
        int[] col = dk
            ? new int[]{0xFF1C2321, 0xFFE6ECE9, 0xFF8E9C95, 0xFF4A5752, 0xFF5FBF9B}
            : new int[]{0xFFFFFFFF, 0xFF26302B, 0xFF8A958F, 0xFFC9D3CE, 0xFF3F9C7C};
        /* 앱에서 고른 테마 색 */
        try {
            String a = data(ctx).optString(dk ? "accentD" : "accentL", "");
            if (a.startsWith("#") && a.length() == 7) col[4] = 0xFF000000 | Integer.parseInt(a.substring(1), 16);
        } catch (Exception ignored) {}
        return col;
    }
    static float[] sizes(JSONObject c) {
        String f = c.optString("font", "m"); /* 제목, 항목, 작은 글씨 */
        if ("s".equals(f)) return new float[]{14, 13, 11};
        if ("l".equals(f)) return new float[]{17, 16, 13};
        return new float[]{15, 14, 12};
    }

    /* ---------- 데이터 ---------- */
    static JSONObject data(Context ctx) {
        try { return new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    static Calendar logicalDay(JSONObject d) {
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) < d.optInt("dayStart", 0)) c.add(Calendar.DATE, -1);
        return c;
    }
    static String todayKey(Context ctx) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(logicalDay(data(ctx)).getTime());
    }
    /** 앱 TODAY와 같은 규칙 (lbNowState):
     *  1) 정한 시간 안 → 그 시간대 (now)  2) 시간대 사이 빈 시간 → 다음 시간대 (next)
     *  3) 마지막 시간대가 끝난 뒤 ~ 하루가 바뀌기 전 → 그날 마지막 시간대 (late) */
    static String nowMode = "now";
    static JSONObject currentBlock(JSONObject data) {
        nowMode = "now";
        JSONArray blocks = data.optJSONArray("blocks");
        if (blocks == null || blocks.length() == 0) return null;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), ds = data.optInt("dayStart", 0);
        JSONObject hit = null, next = null, last = null;
        int nextStart = 99, lastEnd = -1;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject b = blocks.optJSONObject(i);
            JSONArray hr = b == null ? null : b.optJSONArray("hours");
            if (hr == null || hr.length() < 2) continue;
            int s0 = hr.optInt(0), e0 = hr.optInt(1);
            if (hit == null && h >= s0 && h < e0) hit = b;
            if (s0 > h && s0 < nextStart) { nextStart = s0; next = b; }
            if (e0 > lastEnd) { lastEnd = e0; last = b; }
        }
        if (hit != null) return hit;
        if (h < ds && last != null) { nowMode = "late"; return last; }
        if (next != null) { nowMode = "next"; return next; }
        if (last != null) { nowMode = "late"; return last; }
        return blocks.optJSONObject(0);
    }
    /** 위젯에 보일 줄 목록 (전체 보기면 시간대 제목 줄 포함) */
    static JSONArray rows(Context ctx, JSONObject c) {
        JSONArray out = new JSONArray();
        JSONObject d = data(ctx);
        if (!todayKey(ctx).equals(d.optString("date"))) return out;
        boolean hide = c.optBoolean("hideDone", false);
        try {
            if ("all".equals(c.optString("scope", "now"))) {
                JSONArray blocks = d.optJSONArray("blocks");
                JSONObject now = currentBlock(d);
                for (int i = 0; blocks != null && i < blocks.length(); i++) {
                    JSONObject b = blocks.optJSONObject(i);
                    JSONArray its = b.optJSONArray("items");
                    JSONArray keep = new JSONArray();
                    int dn = 0, n = its == null ? 0 : its.length();
                    for (int k = 0; k < n; k++) { JSONObject it = its.optJSONObject(k); if (it.optBoolean("done")) dn++; if (!(hide && it.optBoolean("done"))) keep.put(it); }
                    if (keep.length() == 0) continue;
                    JSONObject h = new JSONObject();
                    h.put("header", true);
                    String tag = "next".equals(nowMode) ? " · 다음" : "late".equals(nowMode) ? " · 마무리" : " · 지금";
                    h.put("name", b.optString("label") + (now != null && b.optString("id").equals(now.optString("id")) ? tag : ""));
                    h.put("meta", dn + "/" + n);
                    out.put(h);
                    for (int k = 0; k < keep.length(); k++) out.put(keep.get(k));
                }
            } else {
                JSONObject b = currentBlock(d);
                JSONArray its = b == null ? null : b.optJSONArray("items");
                for (int k = 0; its != null && k < its.length(); k++) {
                    JSONObject it = its.optJSONObject(k);
                    if (!(hide && it.optBoolean("done"))) out.put(it);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /* ---------- 그리기 ---------- */
    static void refresh(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, RoutineWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) m.updateAppWidget(id, build(ctx, id, cfg(ctx, id), false));
        m.notifyAppWidgetViewDataChanged(ids, R.id.widgetList);
    }

    static RemoteViews itemView(Context ctx, JSONObject it, JSONObject c) {
        int[] col = colors(ctx, c);
        float[] sz = sizes(c);
        if (it.optBoolean("header")) {
            RemoteViews h = new RemoteViews(ctx.getPackageName(), R.layout.widget_header_row);
            h.setTextViewText(R.id.widgetHeadName, it.optString("name"));
            h.setTextViewText(R.id.widgetHeadMeta, it.optString("meta"));
            h.setTextColor(R.id.widgetHeadName, col[2]);
            h.setTextColor(R.id.widgetHeadMeta, col[2]);
            h.setTextViewTextSize(R.id.widgetHeadName, TypedValue.COMPLEX_UNIT_SP, sz[2]);
            h.setTextViewTextSize(R.id.widgetHeadMeta, TypedValue.COMPLEX_UNIT_SP, sz[2]);
            return h;
        }
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
        boolean done = it.optBoolean("done");
        v.setTextViewText(R.id.widgetItemName, it.optString("name"));
        String meta = it.optString("meta", "");
        if (meta.isEmpty() && !"check".equals(it.optString("kind", "check")) && !done) meta = "기록 ›";
        v.setTextViewText(R.id.widgetItemMeta, meta);
        v.setViewVisibility(R.id.widgetItemMeta, meta.isEmpty() ? View.GONE : View.VISIBLE);
        v.setImageViewResource(R.id.widgetItemCheck, done ? R.drawable.widget_check_on : R.drawable.widget_check_off);
        if (!done) v.setInt(R.id.widgetItemCheck, "setColorFilter", col[3]);
        else v.setInt(R.id.widgetItemCheck, "setColorFilter", 0);
        v.setTextColor(R.id.widgetItemName, done ? col[2] : col[1]);
        v.setTextColor(R.id.widgetItemMeta, col[2]);
        v.setTextViewTextSize(R.id.widgetItemName, TypedValue.COMPLEX_UNIT_SP, sz[1] + 1);
        v.setTextViewTextSize(R.id.widgetItemMeta, TypedValue.COMPLEX_UNIT_SP, sz[2]);
        return v;
    }

    static RemoteViews build(Context ctx, int widgetId, JSONObject c, boolean preview) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_routines);
        int[] col = colors(ctx, c);
        float[] sz = sizes(c);
        int alpha = Math.max(0, Math.min(100, c.optInt("opacity", 100)));
        v.setInt(R.id.widgetBgImg, "setColorFilter", col[0]);
        v.setInt(R.id.widgetBgImg, "setImageAlpha", Math.round(alpha * 2.55f));

        JSONObject d = data(ctx);
        boolean fresh = todayKey(ctx).equals(d.optString("date"));
        boolean all = "all".equals(c.optString("scope", "now"));
        String title = all ? "오늘 할 일" : "지금 할 일";
        int n = 0, done = 0;
        try {
            if (fresh) {
                if (all) {
                    JSONArray blocks = d.optJSONArray("blocks");
                    for (int i = 0; blocks != null && i < blocks.length(); i++) {
                        JSONArray its = blocks.optJSONObject(i).optJSONArray("items");
                        for (int k = 0; its != null && k < its.length(); k++) { n++; if (its.optJSONObject(k).optBoolean("done")) done++; }
                    }
                } else {
                    JSONObject b = currentBlock(d);
                    if (b != null) {
                        String lb = b.optString("label");
                        title = "next".equals(nowMode) ? "곧 " + lb : "late".equals(nowMode) ? "하루 마무리" : "지금은 " + lb;
                        JSONArray its = b.optJSONArray("items");
                        for (int k = 0; its != null && k < its.length(); k++) { n++; if (its.optJSONObject(k).optBoolean("done")) done++; }
                    }
                }
            }
        } catch (Exception ignored) {}
        Calendar day = logicalDay(d);
        String sub = (day.get(Calendar.MONTH) + 1) + "." + day.get(Calendar.DATE) + " " + WD[day.get(Calendar.DAY_OF_WEEK) - 1] + (n > 0 ? "  ·  " + done + "/" + n : "");
        v.setTextViewText(R.id.widgetTitle, title);
        v.setTextViewText(R.id.widgetSub, sub);
        v.setTextColor(R.id.widgetTitle, col[1]);
        v.setTextColor(R.id.widgetSub, col[2]);
        v.setTextViewTextSize(R.id.widgetTitle, TypedValue.COMPLEX_UNIT_SP, sz[0]);
        v.setTextViewTextSize(R.id.widgetSub, TypedValue.COMPLEX_UNIT_SP, sz[2]);
        v.setInt(R.id.widgetGear, "setColorFilter", col[2]);
        v.setProgressBar(R.id.widgetProgress, Math.max(1, n), done, false);
        v.setViewVisibility(R.id.widgetProgress, n > 0 ? View.VISIBLE : View.INVISIBLE);
        String empty = !fresh ? "앱을 한 번 열면 오늘 루틴이 보여요"
            : (n > 0 && done == n) ? (all ? "오늘 할 일을 다 했어요" : "이 시간대는 다 했어요") : "이 시간대에는 할 일이 없어요";
        v.setTextViewText(R.id.widgetEmpty, empty);
        v.setTextColor(R.id.widgetEmpty, col[2]);
        v.setTextViewTextSize(R.id.widgetEmpty, TypedValue.COMPLEX_UNIT_SP, sz[1] - 1);

        /* DAILY CARE 요약 (최대 3개) */
        int[] cn = {R.id.care1N, R.id.care2N, R.id.care3N}, cv = {R.id.care1V, R.id.care2V, R.id.care3V}, cc = {R.id.care1, R.id.care2, R.id.care3};
        JSONArray care = fresh ? d.optJSONArray("care") : null;
        int shown = 0;
        for (int i = 0; i < 3; i++) {
            JSONObject x = care != null && i < care.length() ? care.optJSONObject(i) : null;
            if (x == null) { v.setViewVisibility(cc[i], View.GONE); continue; }
            shown++;
            v.setViewVisibility(cc[i], View.VISIBLE);
            v.setTextViewText(cn[i], x.optString("name"));
            String val = x.optString("val"), goal = x.optString("goal").replace("목표 ", "");
            v.setTextViewText(cv[i], goal.isEmpty() ? val : val + " / " + goal);
            v.setTextColor(cn[i], col[2]); v.setTextColor(cv[i], col[1]);
            v.setTextViewTextSize(cn[i], TypedValue.COMPLEX_UNIT_SP, sz[2] - 1);
            v.setTextViewTextSize(cv[i], TypedValue.COMPLEX_UNIT_SP, sz[2] + 1);
            v.setInt(cc[i], "setBackgroundResource", dark(ctx, c) ? R.drawable.widget_cell_dark : R.drawable.widget_cell_light);
        }
        v.setViewVisibility(R.id.widgetCare, shown > 0 ? View.VISIBLE : View.GONE);
        /* 아래 바로가기: FREE TIME · PROJECTS · QUICK LOG */
        int[] nv = {R.id.nav1, R.id.nav2, R.id.nav3}, ni = {R.id.nav1I, R.id.nav2I, R.id.nav3I}, nt = {R.id.nav1T, R.id.nav2T, R.id.nav3T};
        String[] scr = {"freetime", "project", "quicklog"}, lab = {"FREE TIME", "PROJECTS", "QUICK LOG"};
        for (int i = 0; i < 3; i++) {
            v.setImageViewResource(ni[i], WidgetKit.iconOf(scr[i]));
            v.setInt(ni[i], "setColorFilter", col[4]);
            v.setTextViewText(nt[i], lab[i]);
            v.setTextColor(nt[i], col[1]);
            v.setTextViewTextSize(nt[i], TypedValue.COMPLEX_UNIT_SP, sz[2]);
            v.setInt(nv[i], "setBackgroundResource", dark(ctx, c) ? R.drawable.widget_cell_dark : R.drawable.widget_cell_light);
            if (!preview) v.setOnClickPendingIntent(nv[i], WidgetKit.openScreen(ctx, scr[i], 8600 + widgetId * 4 + i));
        }

        if (preview) {
            /* 설정 화면 미리보기: 목록을 직접 그림 */
            v.setViewVisibility(R.id.widgetList, View.GONE);
            v.removeAllViews(R.id.widgetPreviewList);
            JSONArray rs = rows(ctx, c);
            if (rs.length() == 0 && !fresh) {
                /* 아직 데이터가 없으면 예시로 */
                try {
                    String[][] ex = {{"물 한 잔", "1"}, {"스트레칭", "0"}, {"독서", "0"}};
                    for (String[] e : ex) { JSONObject o = new JSONObject(); o.put("name", e[0]); o.put("done", "1".equals(e[1])); o.put("kind", "check"); rs.put(o); }
                    v.setTextViewText(R.id.widgetTitle, "지금은 저녁");
                } catch (Exception ignored) {}
            }
            for (int i = 0; i < Math.min(rs.length(), 4); i++) v.addView(R.id.widgetPreviewList, itemView(ctx, rs.optJSONObject(i), c));
            v.setViewVisibility(R.id.widgetPreviewList, rs.length() > 0 ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.widgetEmpty, rs.length() > 0 ? View.GONE : View.VISIBLE);
            return v;
        }

        Intent svc = new Intent(ctx, RoutineWidgetService.class)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.widgetList, svc);
        v.setEmptyView(R.id.widgetList, R.id.widgetEmpty);

        /* 목록 누르기: 체크는 바로, 그 밖은 홈 화면 위 기록창 (위젯에서 창을 여는 건 화면 쪽으로만 가능해서 창을 거쳐 감) */
        Intent tap = new Intent(ctx, WidgetInputActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        v.setPendingIntentTemplate(R.id.widgetList, PendingIntent.getActivity(ctx, 7701, tap, fl));

        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent po = PendingIntent.getActivity(ctx, 7701, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widgetHeadText, WidgetKit.openScreen(ctx, "routine", 8590 + widgetId));
        v.setOnClickPendingIntent(R.id.widgetEmpty, po);

        Intent conf = new Intent(ctx, WidgetConfigActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        conf.setData(Uri.parse("logonly://widget/" + widgetId));
        v.setOnClickPendingIntent(R.id.widgetGear, PendingIntent.getActivity(ctx, 7800 + widgetId, conf, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id, cfg(ctx, id), false));
        m.notifyAppWidgetViewDataChanged(ids, R.id.widgetList);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove("cfg_" + id);
        e.apply();
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_TAP.equals(intent.getAction())) handleTap(ctx, intent);
    }

    static void handleTap(Context ctx, Intent intent) {
        String id = intent.getStringExtra("rid");
        String kind = intent.getStringExtra("kind");
        if (id == null) return;
        if (kind == null) kind = "check";
        SharedPreferences wp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!"check".equals(kind) && !"count".equals(kind) && !"start".equals(kind) && !"stop".equals(kind)) {
            /* 그 밖의 기록은 홈 화면 위 기록창에서 */
            try { ctx.startActivity(WidgetInputActivity.intent(ctx, id)); } catch (Exception ignored) {}
            return;
        }
        long now = System.currentTimeMillis();
        try {
            JSONObject data = data(ctx);
            String date = data.optString("date");
            /* 앱이 다음에 열릴 때 반영하기 전까지 위젯에 먼저 보여 줄 모습 */
            JSONArray blocks = data.optJSONArray("blocks");
            String name = "";
            if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
                JSONArray items = blocks.optJSONObject(i).optJSONArray("items");
                if (items == null) continue;
                for (int k = 0; k < items.length(); k++) {
                    JSONObject it = items.optJSONObject(k);
                    if (!id.equals(it.optString("id"))) continue;
                    name = it.optString("name");
                    if ("check".equals(kind)) it.put("done", !it.optBoolean("done"));
                    else if ("count".equals(kind)) {
                        int c = it.optInt("cnt") + 1, t = it.optInt("target");
                        it.put("cnt", c);
                        if (t <= 0 || c >= t) it.put("done", true);
                    } else if ("stop".equals(kind)) it.put("done", true);
                }
            }
            String a;
            String slot = id;
            if ("start".equals(kind)) {
                JSONObject tm = data.optJSONObject("timer");
                if (tm != null && tm.optLong("start") > 0) { WidgetKit.refreshAll(ctx); return; } /* 이미 재는 중 */
                tm = new JSONObject(); tm.put("rid", id); tm.put("name", name); tm.put("start", now);
                data.put("timer", tm);
                a = "tstart";
            } else if ("stop".equals(kind)) {
                data.put("timer", JSONObject.NULL);
                a = "stop"; slot = "routine";
                try { androidx.core.app.NotificationManagerCompat.from(ctx).cancel(TimerActionReceiver.notiIdOf("routine")); } catch (Exception ignored) {}
            } else a = "count".equals(kind) ? "rcount" : "rcheck";
            wp.edit().putString(KEY_DATA, data.toString()).apply();
            SharedPreferences p = ctx.getSharedPreferences(TimerActionReceiver.PREFS, Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(TimerActionReceiver.KEY_PENDING, "[]")); } catch (Exception e) { arr = new JSONArray(); }
            JSONObject o = new JSONObject();
            o.put("a", a); o.put("slot", slot); o.put("date", date); o.put("at", now);
            arr.put(o);
            p.edit().putString(TimerActionReceiver.KEY_PENDING, arr.toString()).apply();
        } catch (Exception ignored) {}
        WidgetKit.refreshAll(ctx);
    }
}
