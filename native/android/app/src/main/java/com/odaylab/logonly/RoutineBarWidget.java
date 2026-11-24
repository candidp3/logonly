package com.odaylab.logonly;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class RoutineBarWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, WidgetKit.buildSimple(ctx, id, RoutineWidgetProvider.cfg(ctx, id), WidgetKit.KIND_ROUTINE_BAR));
    }
    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (RoutineWidgetProvider.ACTION_TAP.equals(intent.getAction())) RoutineWidgetProvider.handleTap(ctx, intent);
    }
}
