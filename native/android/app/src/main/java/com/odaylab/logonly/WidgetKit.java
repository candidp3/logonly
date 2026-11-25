package com.odaylab.logonly;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** 위젯 4종이 함께 쓰는 것 (2026-11-24) */
public class WidgetKit {
    static final int KIND_TODAY = 0, KIND_SMALL = 1, KIND_ROUTINE_BAR = 2, KIND_PROJECT_BAR = 3;

    static int kindOf(Context ctx, int widgetId) {
        try {
            android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(widgetId);
            String c = info == null ? "" : info.provider.getClassName();
            if (c.endsWith("RoutineSmallWidget")) return KIND_SMALL;
            if (c.endsWith("RoutineBarWidget")) return KIND_ROUTINE_BAR;
            if (c.endsWith("ProjectBarWidget")) return KIND_PROJECT_BAR;
        } catch (Exception ignored) {}
        return KIND_TODAY;
    }
    static String kindName(int k) {
        return k == KIND_SMALL ? "루틴 2×1" : k == KIND_ROUTINE_BAR ? "루틴 4×1" : k == KIND_PROJECT_BAR ? "프로젝트 4×1" : "투데이 4×4";
    }

    /** 앱의 그 화면(루틴·FREE TIME·PROJECTS·QUICK LOG)으로 바로 열기 */
    static PendingIntent openScreen(Context ctx, String screen, int code) {
        Intent i = new Intent(ctx, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("widgetOpen", screen == null ? "" : "screen:" + screen);
        i.setData(Uri.parse("logonly://open/" + screen + "/" + code));
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent openRoutine(Context ctx, String rid, int code) {
        Intent i = new Intent(ctx, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("widgetOpen", rid);
        i.setData(Uri.parse("logonly://routine/" + rid + "/" + code));
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent openApp(Context ctx, int code) {
        Intent i = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    /** 체크형 루틴 하나를 바로 체크 (시간·횟수형은 앱으로) */
    static PendingIntent tapItem(Context ctx, Class<?> cls, JSONObject it, int code) {
        if (!"check".equals(it.optString("kind", "check"))) return openRoutine(ctx, it.optString("id"), code);
        Intent i = new Intent(ctx, cls).setAction(RoutineWidgetProvider.ACTION_TAP)
            .putExtra("rid", it.optString("id")).putExtra("kind", "check");
        i.setData(Uri.parse("logonly://tap/" + it.optString("id") + "/" + code));
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent settings(Context ctx, int widgetId) {
        Intent conf = new Intent(ctx, WidgetConfigActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        conf.setData(Uri.parse("logonly://widget/" + widgetId));
        return PendingIntent.getActivity(ctx, 7800 + widgetId, conf, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void bg(Context ctx, RemoteViews v, JSONObject c) {
        int[] col = RoutineWidgetProvider.colors(ctx, c);
        int alpha = Math.max(0, Math.min(100, c.optInt("opacity", 100)));
        v.setInt(R.id.widgetBgImg, "setColorFilter", col[0]);
        v.setInt(R.id.widgetBgImg, "setImageAlpha", Math.round(alpha * 2.55f));
    }
    static int circleBg(Context ctx, JSONObject c) {
        return RoutineWidgetProvider.dark(ctx, c) ? R.drawable.widget_circle_dark : R.drawable.widget_circle_light;
    }
    static void navButton(Context ctx, RemoteViews v, int viewId, int icon, String screen, JSONObject c, int code) {
        int[] col = RoutineWidgetProvider.colors(ctx, c);
        v.setImageViewResource(viewId, icon);
        v.setInt(viewId, "setColorFilter", col[4]);
        v.setInt(viewId, "setBackgroundResource", circleBg(ctx, c));
        v.setOnClickPendingIntent(viewId, openScreen(ctx, screen, code));
    }
    static int iconOf(String screen) {
        if ("freetime".equals(screen)) return R.drawable.ic_w_timer;
        if ("project".equals(screen)) return R.drawable.ic_w_project;
        if ("quicklog".equals(screen)) return R.drawable.ic_w_pencil;
        return R.drawable.ic_w_routine;
    }

    /** 지금 시간대 정보: [제목, 완료/전체, 다음 할 항목(없으면 null)] */
    static Object[] now(Context ctx) {
        JSONObject d = RoutineWidgetProvider.data(ctx);
        if (!RoutineWidgetProvider.todayKey(ctx).equals(d.optString("date"))) return new Object[]{"오늘", "", null, false};
        JSONObject b = RoutineWidgetProvider.currentBlock(d);
        if (b == null) return new Object[]{"오늘", "", null, true};
        String m = RoutineWidgetProvider.nowMode, lb = b.optString("label");
        String title = "next".equals(m) ? "곧 " + lb : "late".equals(m) ? "하루 마무리" : "지금은 " + lb;
        JSONArray its = b.optJSONArray("items");
        int n = its == null ? 0 : its.length(), done = 0;
        JSONObject next = null;
        for (int i = 0; i < n; i++) { JSONObject it = its.optJSONObject(i); if (it.optBoolean("done")) done++; else if (next == null) next = it; }
        return new Object[]{title, n > 0 ? done + "/" + n : "", next, true};
    }

    static void refreshAll(Context ctx) {
        RoutineWidgetProvider.refresh(ctx);
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        for (Class<?> cls : new Class<?>[]{RoutineSmallWidget.class, RoutineBarWidget.class, ProjectBarWidget.class}) {
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, cls));
            if (ids == null) continue;
            for (int id : ids) m.updateAppWidget(id, buildSimple(ctx, id, RoutineWidgetProvider.cfg(ctx, id), kindOf(ctx, id)));
        }
    }

    static void ts(RemoteViews v, int id, float sp) { v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp); }

    /** 2×1 · 4×1 위젯 */
    static RemoteViews buildSimple(Context ctx, int widgetId, JSONObject c, int kind) {
        int[] col = RoutineWidgetProvider.colors(ctx, c);
        float[] sz = RoutineWidgetProvider.sizes(c);
        if (kind == KIND_SMALL) return buildSmall(ctx, widgetId, c);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_bar);
        bg(ctx, v, c);
        v.setTextColor(R.id.barTitle, col[2]);
        v.setTextColor(R.id.barText, col[1]);
        ts(v, R.id.barTitle, sz[1] - 1); ts(v, R.id.barText, sz[0] + 2);
        String[] nav;
        if (kind == KIND_PROJECT_BAR) {
            nav = new String[]{"routine", "freetime", "quicklog"};
            JSONObject d = RoutineWidgetProvider.data(ctx);
            JSONArray ps = d.optJSONArray("projects");
            int cnt = d.optInt("projectCount", ps == null ? 0 : ps.length());
            v.setViewVisibility(R.id.barCheck, View.GONE);
            v.setTextViewText(R.id.barTitle, cnt > 0 ? "PROJECTS · 진행 중 " + cnt : "PROJECTS");
            String main = "진행 중인 프로젝트가 없어요";
            if (ps != null && ps.length() > 0) {
                JSONObject p = ps.optJSONObject(0);
                String nx = p.optString("next");
                main = nx.isEmpty() ? p.optString("name") : (p.optString("due").isEmpty() ? "" : p.optString("due") + "  ") + nx;
            }
            v.setTextViewText(R.id.barText, main);
            v.setOnClickPendingIntent(R.id.barMain, openScreen(ctx, "project", 9300 + widgetId));
        } else {
            nav = new String[]{"freetime", "project", "quicklog"};
            Object[] n = now(ctx);
            v.setTextViewText(R.id.barTitle, (String) n[0] + (((String) n[1]).isEmpty() ? "" : "  ·  " + n[1]));
            JSONObject next = (JSONObject) n[2];
            if (next != null) {
                v.setViewVisibility(R.id.barCheck, View.VISIBLE);
                v.setImageViewResource(R.id.barCheck, R.drawable.widget_check_off);
                v.setInt(R.id.barCheck, "setColorFilter", col[3]);
                v.setTextViewText(R.id.barText, next.optString("name"));
                v.setOnClickPendingIntent(R.id.barMain, tapItem(ctx, RoutineBarWidget.class, next, 9400 + widgetId));
            } else {
                v.setViewVisibility(R.id.barCheck, View.GONE);
                v.setTextViewText(R.id.barText, !(Boolean) n[3] ? "앱을 한 번 열면 보여요" : ((String) n[1]).isEmpty() ? "이 시간대엔 할 일이 없어요" : "이 시간대는 다 했어요");
                v.setOnClickPendingIntent(R.id.barMain, openScreen(ctx, "routine", 9400 + widgetId));
            }
        }
        navButton(ctx, v, R.id.barB1, iconOf(nav[0]), nav[0], c, 9500 + widgetId * 4);
        navButton(ctx, v, R.id.barB2, iconOf(nav[1]), nav[1], c, 9501 + widgetId * 4);
        navButton(ctx, v, R.id.barB3, iconOf(nav[2]), nav[2], c, 9502 + widgetId * 4);
        return v;
    }

    /* ---------- 2×1 ROUTINE (2026-11-25) ---------- */
    static int hex(String h, int def) {
        try { if (h != null && h.startsWith("#") && h.length() == 7) return 0xFF000000 | Integer.parseInt(h.substring(1), 16); } catch (Exception ignored) {}
        return def;
    }
    /** {연한 테마색, 진한 테마색} */
    static int[] tint(Context ctx, JSONObject c) {
        boolean dk = RoutineWidgetProvider.dark(ctx, c);
        JSONObject d = RoutineWidgetProvider.data(ctx);
        return new int[]{
            hex(d.optString(dk ? "tintD" : "tintL"), dk ? 0xFF223A31 : 0xFFE2F4EC),
            hex(d.optString(dk ? "accentDarkD" : "accentDarkL"), dk ? 0xFFBFE6D4 : 0xFF2F6B55)
        };
    }
    /** 위젯에서 바로: check(체크) · count(+1) · start(시작) · stop(종료) */
    static PendingIntent act(Context ctx, Class<?> cls, String kind, String rid, int code) {
        Intent i = new Intent(ctx, cls).setAction(RoutineWidgetProvider.ACTION_TAP)
            .putExtra("rid", rid).putExtra("kind", kind);
        i.setData(Uri.parse("logonly://act/" + kind + "/" + rid + "/" + code));
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    /** 홈 화면 위에 앱의 기록창 */
    static PendingIntent input(Context ctx, String rid, int code) {
        return PendingIntent.getActivity(ctx, code, WidgetInputActivity.intent(ctx, rid), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews buildSmall(Context ctx, int widgetId, JSONObject c) {
        int[] col = RoutineWidgetProvider.colors(ctx, c);
        int[] tn = tint(ctx, c);
        float big = "s".equals(c.optString("font", "m")) ? 14.5f : "l".equals(c.optString("font", "m")) ? 17f : 15.5f;
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_small);
        bg(ctx, v, c);
        JSONObject d = RoutineWidgetProvider.data(ctx);
        boolean fresh = RoutineWidgetProvider.todayKey(ctx).equals(d.optString("date"));
        v.setInt(R.id.smallBar, "setColorFilter", col[4]);
        v.setTextColor(R.id.smallTitle, col[1]);
        v.setTextColor(R.id.smallCount, col[2]);
        v.setTextColor(R.id.smallName, col[1]);
        v.setTextColor(R.id.smallChrono, col[1]);
        ts(v, R.id.smallName, big);
        v.setViewVisibility(R.id.smallChrono, View.GONE);
        v.setViewVisibility(R.id.smallName, View.VISIBLE);
        v.setViewVisibility(R.id.smallBtnIc, View.GONE);
        v.setTextViewText(R.id.smallBtnText, "");
        int code = 9000 + widgetId * 8;

        /* 1) 타이머가 돌아가는 중: 연한 테마색 바탕 · 흐르는 시간 · 종료 */
        JSONObject tm = d.optJSONObject("timer");
        if (tm != null && tm.optLong("start") > 0) {
            v.setInt(R.id.widgetBgImg, "setColorFilter", tn[0]);
            v.setTextViewText(R.id.smallTitle, tm.optString("name", "타이머"));
            v.setTextViewText(R.id.smallCount, "");
            v.setViewVisibility(R.id.smallName, View.GONE);
            v.setViewVisibility(R.id.smallChrono, View.VISIBLE);
            ts(v, R.id.smallChrono, big + 1);
            long base = android.os.SystemClock.elapsedRealtime() - Math.max(0, System.currentTimeMillis() - tm.optLong("start"));
            v.setChronometer(R.id.smallChrono, base, null, true);
            v.setImageViewResource(R.id.smallBtnBg, R.drawable.widget_dot_fill);
            v.setInt(R.id.smallBtnBg, "setColorFilter", col[4]);
            v.setTextViewText(R.id.smallBtnText, "종료");
            v.setTextColor(R.id.smallBtnText, RoutineWidgetProvider.dark(ctx, c) ? 0xFF14201B : 0xFFFFFFFF);
            v.setOnClickPendingIntent(R.id.smallBtn, act(ctx, RoutineSmallWidget.class, "stop", tm.optString("rid"), code));
            v.setOnClickPendingIntent(R.id.smallMain, input(ctx, tm.optString("rid"), code + 1));
            return v;
        }

        JSONObject blk = fresh ? RoutineWidgetProvider.currentBlock(d) : null;
        JSONArray its = blk == null ? null : blk.optJSONArray("items");
        int n = its == null ? 0 : its.length(), done = 0;
        JSONObject next = null;
        for (int i = 0; i < n; i++) { JSONObject it = its.optJSONObject(i); if (it.optBoolean("done")) done++; else if (next == null) next = it; }
        v.setTextViewText(R.id.smallTitle, !fresh ? "오늘" : blk == null ? "오늘" : blk.optString("label"));
        v.setTextViewText(R.id.smallCount, n > 0 ? done + "/" + n : "");

        /* 2) 다 했거나 할 게 없을 때: 잠깐 쉬어가요 */
        if (next == null) {
            v.setTextViewText(R.id.smallName, !fresh ? "앱을 한 번 열어 주세요" : "잠깐 쉬어가요");
            v.setImageViewResource(R.id.smallBtnBg, R.drawable.widget_dot_fill);
            v.setInt(R.id.smallBtnBg, "setColorFilter", tn[0]);
            v.setViewVisibility(R.id.smallBtnIc, View.VISIBLE);
            v.setInt(R.id.smallBtnIc, "setColorFilter", tn[1]);
            PendingIntent op = openScreen(ctx, "routine", code + 2);
            v.setOnClickPendingIntent(R.id.smallBtn, op);
            v.setOnClickPendingIntent(R.id.smallMain, op);
            return v;
        }

        /* 3) 다음 할 루틴 */
        String kind = next.optString("kind", "check"), rid = next.optString("id"), name = next.optString("name");
        if ("count".equals(kind) && next.optInt("cnt") > 0) name = name + " · " + next.optInt("cnt") + next.optString("unit", "회");
        v.setTextViewText(R.id.smallName, name);
        if ("check".equals(kind)) {
            v.setImageViewResource(R.id.smallBtnBg, R.drawable.widget_ring_big);
            v.setInt(R.id.smallBtnBg, "setColorFilter", col[4]);
            PendingIntent ck = act(ctx, RoutineSmallWidget.class, "check", rid, code + 3);
            v.setOnClickPendingIntent(R.id.smallBtn, ck);
            v.setOnClickPendingIntent(R.id.smallMain, ck);
        } else {
            v.setImageViewResource(R.id.smallBtnBg, R.drawable.widget_dot_fill);
            v.setInt(R.id.smallBtnBg, "setColorFilter", tn[0]);
            v.setTextColor(R.id.smallBtnText, tn[1]);
            v.setTextViewText(R.id.smallBtnText, "count".equals(kind) ? "+1" : "time".equals(kind) ? "시작" : "기록");
            v.setOnClickPendingIntent(R.id.smallBtn,
                "count".equals(kind) ? act(ctx, RoutineSmallWidget.class, "count", rid, code + 3)
                : "time".equals(kind) ? act(ctx, RoutineSmallWidget.class, "start", rid, code + 3)
                : input(ctx, rid, code + 3));
            v.setOnClickPendingIntent(R.id.smallMain, input(ctx, rid, code + 4));
        }
        return v;
    }
}
