package com.odaylab.logonly;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** 4×1 ROUTINE 오른쪽 목록: 지금 시간대에서 아직 안 한 루틴 (왼쪽에 보이는 '다음 루틴'은 빼고) · 스크롤 */
public class RoutineBarService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
    }

    static ArrayList<JSONObject> rest(Context ctx) {
        ArrayList<JSONObject> out = new ArrayList<>();
        JSONObject d = RoutineWidgetProvider.data(ctx);
        if (!RoutineWidgetProvider.todayKey(ctx).equals(d.optString("date"))) return out;
        JSONObject blk = RoutineWidgetProvider.currentBlock(d);
        JSONArray its = blk == null ? null : blk.optJSONArray("items");
        if (its == null) return out;
        JSONObject tm = d.optJSONObject("timer");
        boolean timer = tm != null && tm.optLong("start") > 0;
        boolean skipped = timer; /* 타이머 중이면 왼쪽이 타이머라서 빼지 않음 */
        for (int i = 0; i < its.length(); i++) {
            JSONObject it = its.optJSONObject(i);
            if (it == null || it.optBoolean("done")) continue;
            if (!skipped) { skipped = true; continue; }
            out.add(it);
        }
        /* 놓친 루틴: 오늘 이미 지나간 시간대에서 아직 안 한 것 — 목록 아래쪽에 '놓침'으로 */
        try {
            JSONArray blocks = d.optJSONArray("blocks");
            int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), ds = d.optInt("dayStart", 0);
            boolean afterMidnight = h < ds;
            if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
                JSONObject b = blocks.optJSONObject(i);
                if (b == null || b == blk) continue;
                JSONArray hr = b.optJSONArray("hours");
                if (hr == null || hr.length() < 2) continue;
                boolean past = afterMidnight ? hr.optInt(1) <= 24 + h && hr.optInt(0) >= ds : hr.optInt(1) <= h;
                if (!past) continue;
                JSONArray bi = b.optJSONArray("items");
                if (bi == null) continue;
                for (int k = 0; k < bi.length(); k++) {
                    JSONObject it = bi.optJSONObject(k);
                    if (it == null || it.optBoolean("done")) continue;
                    it.put("missed", true);
                    out.add(it);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    static class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final int widgetId;
        private ArrayList<JSONObject> rows = new ArrayList<>();
        private JSONObject cfg = new JSONObject();
        Factory(Context c, int id) { ctx = c; widgetId = id; }
        private void load() { cfg = RoutineWidgetProvider.cfg(ctx, widgetId); rows = rest(ctx); }
        @Override public void onCreate() { load(); }
        @Override public void onDataSetChanged() { load(); }
        @Override public void onDestroy() {}
        @Override public int getCount() { return rows.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public RemoteViews getViewAt(int i) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_bar_row);
            if (i >= rows.size()) return v;
            JSONObject it = rows.get(i);
            int[] col = RoutineWidgetProvider.colors(ctx, cfg);
            int[] tn = WidgetKit.tint(ctx, cfg);
            WidgetKit.SYS_FONT = "system".equals(cfg.optString("face", "app"));
            String kind = it.optString("kind", "check");
            boolean check = "check".equals(kind);
            v.setImageViewResource(R.id.rowMark, check ? R.drawable.widget_ring_small : R.drawable.widget_dot_fill);
            v.setInt(R.id.rowMark, "setColorFilter", check ? col[4] : tn[1]);
            if (!check) v.setInt(R.id.rowMark, "setImageAlpha", 150);
            String name = it.optString("name");
            if ("count".equals(kind) && it.optInt("cnt") > 0) name = name + " · " + it.optInt("cnt") + it.optString("unit", "회");
            v.setImageViewBitmap(R.id.rowName, it.optBoolean("missed")
                ? WidgetKit.txtTag(ctx, 14f, col[1], name, "놓침", col[2], WidgetKit.listW(ctx, widgetId))
                : WidgetKit.txt(ctx, 14f, false, col[1], name, WidgetKit.listW(ctx, widgetId)));
            Intent fill = new Intent();
            fill.putExtra("rid", it.optString("id"));
            fill.putExtra("kind", check ? "check" : "input");
            v.setOnClickFillInIntent(R.id.rowRoot, fill);
            return v;
        }
    }
}
