package com.odaylab.logonly;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * 홈 화면 위젯: '지금 시간대'의 루틴을 보여주고 바로 체크 (2026-11-23)
 * - 앱(TODAY)이 그려질 때마다 오늘의 시간대별 루틴을 넘겨줌(LogonlyWidget.update)
 * - 위젯은 지금 시각에 맞는 시간대를 골라 보여줌
 * - 체크형은 누르면 바로 체크(누른 시각을 기억해 두었다가 앱이 반영), 시간·횟수형은 앱을 열어 그 루틴으로
 */
public class RoutineWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "logonly_widget";
    static final String KEY_DATA = "data";
    static final String KEY_OPEN = "open";
    static final String ACTION_TAP = "com.odaylab.logonly.WIDGET_TAP";

    static String todayKey(Context ctx) {
        Calendar c = Calendar.getInstance();
        try {
            JSONObject d = new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, "{}"));
            int ds = d.optInt("dayStart", 0);
            if (c.get(Calendar.HOUR_OF_DAY) < ds) c.add(Calendar.DATE, -1);
        } catch (Exception ignored) {}
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    /** 앱의 thBlockNow()와 같은 규칙 */
    static JSONObject currentBlock(Context ctx, JSONObject data) {
        JSONArray blocks = data.optJSONArray("blocks");
        if (blocks == null || blocks.length() == 0) return null;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < data.optInt("dayStart", 0)) h = 23;
        String pick = h < 6 ? "dawn" : h < 9 ? "breakfast" : h < 12 ? "morning" : h < 14 ? "lunch" : h < 18 ? "afternoon" : h < 22 ? "evening" : "night";
        String fb = "dawn".equals(pick) ? "breakfast" : "night".equals(pick) ? "evening" : null;
        JSONObject found = null, fallback = null;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject b = blocks.optJSONObject(i);
            if (b == null) continue;
            if (pick.equals(b.optString("id"))) found = b;
            if (fb != null && fb.equals(b.optString("id"))) fallback = b;
        }
        if (found != null) return found;
        if (fallback != null) return fallback;
        return blocks.optJSONObject(0);
    }

    static void refresh(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, RoutineWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.widgetList);
    }

    static RemoteViews build(Context ctx, int widgetId) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_routines);
        String title = "지금 할 일", count = "";
        boolean fresh = false;
        try {
            JSONObject data = new JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, "{}"));
            fresh = todayKey(ctx).equals(data.optString("date"));
            JSONObject b = fresh ? currentBlock(ctx, data) : null;
            if (b != null) {
                title = "지금은 " + b.optString("label");
                JSONArray items = b.optJSONArray("items");
                int n = items == null ? 0 : items.length(), done = 0;
                for (int i = 0; i < n; i++) if (items.optJSONObject(i).optBoolean("done")) done++;
                count = n > 0 ? done + "/" + n : "";
            }
        } catch (Exception ignored) {}
        v.setTextViewText(R.id.widgetTitle, title);
        v.setTextViewText(R.id.widgetCount, count);
        v.setTextViewText(R.id.widgetEmpty, fresh ? "이 시간대에는 할 일이 없어요" : "앱을 한 번 열면 오늘 루틴이 보여요");

        Intent svc = new Intent(ctx, RoutineWidgetService.class)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.widgetList, svc);
        v.setEmptyView(R.id.widgetList, R.id.widgetEmpty);

        Intent tap = new Intent(ctx, RoutineWidgetProvider.class).setAction(ACTION_TAP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        v.setPendingIntentTemplate(R.id.widgetList, PendingIntent.getBroadcast(ctx, 7700, tap, fl));

        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent po = PendingIntent.getActivity(ctx, 7701, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widgetHead, po);
        v.setOnClickPendingIntent(R.id.widgetEmpty, po);
        return v;
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(ctx, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.widgetList);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (!ACTION_TAP.equals(intent.getAction())) return;
        String id = intent.getStringExtra("rid");
        String kind = intent.getStringExtra("kind");
        if (id == null) return;
        SharedPreferences wp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!"check".equals(kind)) {
            /* 시간·횟수 기록은 앱에서: 앱을 열고 그 루틴으로 */
            wp.edit().putString(KEY_OPEN, id).apply();
            Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { ctx.startActivity(open); } catch (Exception ignored) {}
            return;
        }
        try {
            JSONObject data = new JSONObject(wp.getString(KEY_DATA, "{}"));
            String date = data.optString("date");
            /* 화면에 바로 반영 (앱이 나중에 진짜로 저장) */
            JSONArray blocks = data.optJSONArray("blocks");
            if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
                JSONArray items = blocks.optJSONObject(i).optJSONArray("items");
                if (items == null) continue;
                for (int k = 0; k < items.length(); k++) {
                    JSONObject it = items.optJSONObject(k);
                    if (id.equals(it.optString("id"))) it.put("done", !it.optBoolean("done"));
                }
            }
            wp.edit().putString(KEY_DATA, data.toString()).apply();
            SharedPreferences p = ctx.getSharedPreferences(TimerActionReceiver.PREFS, Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(TimerActionReceiver.KEY_PENDING, "[]")); } catch (Exception e) { arr = new JSONArray(); }
            JSONObject o = new JSONObject();
            o.put("a", "rcheck"); o.put("slot", id); o.put("date", date); o.put("at", System.currentTimeMillis());
            arr.put(o);
            p.edit().putString(TimerActionReceiver.KEY_PENDING, arr.toString()).apply();
        } catch (Exception ignored) {}
        refresh(ctx);
    }
}
