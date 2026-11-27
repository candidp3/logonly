package com.odaylab.logonly;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.webkit.JavascriptInterface;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.TimeZone;

/**
 * 폰의 캘린더(구글 캘린더 포함)와 연결 — JS 이름: LogonlyCal (2026-11-27)
 * 읽기·추가·수정·삭제는 폰 캘린더에 바로 반영되고, 구글 캘린더가 알아서 동기화함.
 */
public class CalendarBridge {
    private final Activity act;
    CalendarBridge(Activity a) { act = a; }

    private boolean ok() {
        return ContextCompat.checkSelfPermission(act, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(act, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface public boolean hasPerm() { return ok(); }

    @JavascriptInterface
    public void requestPerm() {
        act.runOnUiThread(() -> ActivityCompat.requestPermissions(act,
            new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, 7401));
    }

    /** 캘린더 목록: [{id,name,color,account,write,visible}] */
    @JavascriptInterface
    public String calendars() {
        JSONArray out = new JSONArray();
        if (!ok()) return out.toString();
        String[] p = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.SYNC_EVENTS};
        try (Cursor c = act.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, p, null, null, null)) {
            while (c != null && c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("name", c.getString(1));
                o.put("color", String.format("#%06X", 0xFFFFFF & c.getInt(2)));
                o.put("account", c.getString(3));
                o.put("write", c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR);
                o.put("visible", c.getInt(5) == 1);
                o.put("primary", !c.isNull(6) && c.getInt(6) == 1);
                o.put("type", c.getString(7));
                o.put("sync", c.isNull(8) || c.getInt(8) == 1); /* 0이면 폰에서 이 캘린더를 받아 오지 않음 */
                out.put(o);
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    /** 기간 안의 일정(반복 일정은 날짜마다 하나씩): [{id,title,begin,end,allDay,color,cal,loc,memo,rrule}] */
    @JavascriptInterface
    public String events(double fromMs, double toMs) {
        JSONArray out = new JSONArray();
        if (!ok()) return out.toString();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, (long) fromMs);
        ContentUris.appendId(b, (long) toMs);
        String[] p = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.RRULE, CalendarContract.Instances.VISIBLE};
        try (Cursor c = act.getContentResolver().query(b.build(), p, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getLong(0));
                o.put("title", c.isNull(1) ? "" : c.getString(1));
                o.put("begin", c.getLong(2));
                o.put("end", c.getLong(3));
                o.put("allDay", c.getInt(4) == 1);
                o.put("color", String.format("#%06X", 0xFFFFFF & c.getInt(5)));
                o.put("cal", c.getLong(6));
                o.put("loc", c.isNull(7) ? "" : c.getString(7));
                o.put("memo", c.isNull(8) ? "" : c.getString(8));
                o.put("rrule", c.isNull(9) ? "" : c.getString(9));
                out.put(o);
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    /** 지금 동기화: 폰의 계정마다 캘린더 동기화를 바로 요청 */
    @JavascriptInterface
    public boolean sync() {
        if (!ok()) return false;
        boolean any = false;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String[] p = {CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE};
        try (Cursor c = act.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, p, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(0), type = c.getString(1);
                if (name == null || type == null || CalendarContract.ACCOUNT_TYPE_LOCAL.equals(type) || !seen.add(name + "|" + type)) continue;
                android.os.Bundle ex = new android.os.Bundle();
                ex.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                ex.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(new android.accounts.Account(name, type), CalendarContract.AUTHORITY, ex);
                any = true;
            }
        } catch (Exception ignored) {}
        return any;
    }

    private ContentValues values(JSONObject o, boolean insert) {
        ContentValues v = new ContentValues();
        boolean allDay = o.optBoolean("allDay");
        v.put(CalendarContract.Events.TITLE, o.optString("title"));
        v.put(CalendarContract.Events.EVENT_LOCATION, o.optString("loc"));
        v.put(CalendarContract.Events.DESCRIPTION, o.optString("memo"));
        v.put(CalendarContract.Events.ALL_DAY, allDay ? 1 : 0);
        v.put(CalendarContract.Events.DTSTART, o.optLong("begin"));
        v.put(CalendarContract.Events.DTEND, o.optLong("end"));
        v.put(CalendarContract.Events.EVENT_TIMEZONE, allDay ? "UTC" : TimeZone.getDefault().getID());
        if (o.has("cal")) v.put(CalendarContract.Events.CALENDAR_ID, o.optLong("cal"));
        return v;
    }

    /** 추가: 새 일정 id (실패하면 0) */
    @JavascriptInterface
    public double add(String json) {
        if (!ok()) return 0;
        try {
            JSONObject o = new JSONObject(json);
            Uri u = act.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values(o, true));
            return u == null ? 0 : ContentUris.parseId(u);
        } catch (Exception e) { return 0; }
    }

    /** 수정 (반복 일정이면 반복 전체의 제목·장소 등이 바뀜; 날짜·시간은 반복이 아닐 때만) */
    @JavascriptInterface
    public boolean update(String json) {
        if (!ok()) return false;
        try {
            JSONObject o = new JSONObject(json);
            long id = o.optLong("id");
            ContentValues v = values(o, false);
            if (!o.optString("rrule").isEmpty()) {
                v.remove(CalendarContract.Events.DTSTART); v.remove(CalendarContract.Events.DTEND);
                v.remove(CalendarContract.Events.ALL_DAY); v.remove(CalendarContract.Events.EVENT_TIMEZONE);
            }
            ContentResolver r = act.getContentResolver();
            return r.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0;
        } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public boolean remove(double id) {
        if (!ok()) return false;
        try {
            return act.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, (long) id), null, null) > 0;
        } catch (Exception e) { return false; }
    }
}
