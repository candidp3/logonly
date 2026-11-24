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
        if (kind == KIND_SMALL) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_small);
            bg(ctx, v, c);
            Object[] n = now(ctx);
            v.setTextViewText(R.id.smallTitle, (String) n[0]);
            v.setTextViewText(R.id.smallCount, (String) n[1]);
            v.setTextColor(R.id.smallTitle, col[2]);
            v.setTextColor(R.id.smallCount, col[2]);
            ts(v, R.id.smallTitle, sz[2]); ts(v, R.id.smallCount, sz[2]);
            JSONObject next = (JSONObject) n[2];
            if (next != null) {
                v.setViewVisibility(R.id.smallRow, View.VISIBLE);
                v.setViewVisibility(R.id.smallDone, View.GONE);
                v.setTextViewText(R.id.smallName, next.optString("name"));
                v.setTextColor(R.id.smallName, col[1]);
                ts(v, R.id.smallName, sz[1] + 1);
                v.setImageViewResource(R.id.smallCheck, R.drawable.widget_check_off);
                v.setInt(R.id.smallCheck, "setColorFilter", col[3]);
                v.setOnClickPendingIntent(R.id.smallRow, tapItem(ctx, RoutineSmallWidget.class, next, 9000 + widgetId));
            } else {
                v.setViewVisibility(R.id.smallRow, View.GONE);
                v.setViewVisibility(R.id.smallDone, View.VISIBLE);
                v.setTextViewText(R.id.smallDone, !(Boolean) n[3] ? "앱을 열면 보여요" : ((String) n[1]).isEmpty() ? "할 일이 없어요" : "다 했어요");
                v.setTextColor(R.id.smallDone, col[2]);
                ts(v, R.id.smallDone, sz[1]);
                v.setOnClickPendingIntent(R.id.smallDone, openScreen(ctx, "routine", 9100 + widgetId));
            }
            v.setOnClickPendingIntent(R.id.smallHead, openScreen(ctx, "routine", 9200 + widgetId));
            return v;
        }
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_bar);
        bg(ctx, v, c);
        v.setTextColor(R.id.barTitle, col[2]);
        v.setTextColor(R.id.barText, col[1]);
        ts(v, R.id.barTitle, sz[2]); ts(v, R.id.barText, sz[1] + 1);
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
}
