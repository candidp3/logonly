package com.odaylab.logonly;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

public class RoutineWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private JSONArray items = new JSONArray();
        Factory(Context c) { ctx = c; }

        private void load() {
            items = new JSONArray();
            try {
                JSONObject data = new JSONObject(ctx.getSharedPreferences(RoutineWidgetProvider.PREFS, Context.MODE_PRIVATE)
                    .getString(RoutineWidgetProvider.KEY_DATA, "{}"));
                if (!RoutineWidgetProvider.todayKey(ctx).equals(data.optString("date"))) return;
                JSONObject b = RoutineWidgetProvider.currentBlock(ctx, data);
                if (b != null && b.optJSONArray("items") != null) items = b.optJSONArray("items");
            } catch (Exception ignored) {}
        }

        @Override public void onCreate() { load(); }
        @Override public void onDataSetChanged() { load(); }
        @Override public void onDestroy() {}
        @Override public int getCount() { return items.length(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public RemoteViews getViewAt(int i) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            JSONObject it = items.optJSONObject(i);
            if (it == null) return v;
            boolean done = it.optBoolean("done");
            v.setTextViewText(R.id.widgetItemName, it.optString("name"));
            String meta = it.optString("meta", "");
            v.setTextViewText(R.id.widgetItemMeta, meta);
            v.setViewVisibility(R.id.widgetItemMeta, meta.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
            v.setImageViewResource(R.id.widgetItemCheck, done ? R.drawable.widget_check_on : R.drawable.widget_check_off);
            v.setTextColor(R.id.widgetItemName, ctx.getResources().getColor(done ? R.color.widgetMuted : R.color.widgetText, null));
            Intent fill = new Intent()
                .putExtra("rid", it.optString("id"))
                .putExtra("kind", it.optString("kind", "check"));
            v.setOnClickFillInIntent(R.id.widgetItemRow, fill);
            return v;
        }
    }
}
