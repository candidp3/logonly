package com.odaylab.logonly;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 알림바의 '기록'·'취소'를 앱을 열지 않고 처리 (2026-11-23).
 * 누른 시각을 저장해 두면, 앱이 다음에 열릴 때(이미 열려 있으면 바로) 그 시각으로 기록함.
 * 기록한 경우에는 '독서 42분 기록했어요' 알림을 잠깐 보여줌.
 */
public class TimerActionReceiver extends BroadcastReceiver {
    static final String PREFS = "logonly_timer";
    static final String KEY_PENDING = "pending";

    static int notiIdOf(String slot) {
        if (slot == null || slot.equals("routine")) return 4401;
        return 4410 + (Math.abs(slot.hashCode()) % 80);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            String action = intent.getStringExtra("timerAction");
            String slot = intent.getStringExtra("timerSlot");
            if (slot == null) slot = "routine";
            if (action == null) return;
            long now = System.currentTimeMillis();
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(p.getString(KEY_PENDING, "[]")); } catch (Exception e) { arr = new JSONArray(); }
            JSONObject o = new JSONObject();
            o.put("a", action); o.put("slot", slot); o.put("at", now);
            arr.put(o);
            p.edit().putString(KEY_PENDING, arr.toString()).apply();

            int id = notiIdOf(slot);
            NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
            nm.cancel(id);

            if ("stop".equals(action)) {
                long start = p.getLong("start_" + slot, 0);
                String name = p.getString("name_" + slot, "");
                int min = start > 0 ? (int) Math.max(0, Math.round((now - start) / 60000.0)) : -1;
                String when = min < 0 ? "" : (min >= 60 ? (min / 60) + "시간" + (min % 60 > 0 ? " " + (min % 60) + "분" : "") : min + "분");
                String text = (name.isEmpty() ? "" : name + " ") + (when.isEmpty() ? "" : when + " ") + "기록했어요";
                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "timer")
                    .setSmallIcon(R.drawable.ic_stat_logonly)
                    .setContentTitle(text)
                    .setColor(0xFF3F9C7C)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
                if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(6000);
                try { nm.notify(id + 500, b.build()); } catch (SecurityException ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
