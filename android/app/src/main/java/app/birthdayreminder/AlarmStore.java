package app.birthdayreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Persists upcoming alarm triggers (sent from the web layer) and pending snoozes,
 * and keeps exactly one system alarm armed for the earliest of them.
 */
final class AlarmStore {
    private static final String PREFS = "li_alarm_store";
    private static final String KEY_TRIGGERS = "triggers";
    private static final String KEY_SNOOZES = "snoozes";
    private static final long STALE_MS = 5 * 60 * 1000L;
    private static final int REQ_FIRE = 7001;
    private static final int REQ_SHOW = 7002;

    private AlarmStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray load(Context c, String key) {
        try {
            return new JSONArray(prefs(c).getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void save(Context c, String key, JSONArray a) {
        prefs(c).edit().putString(key, a.toString()).commit();
    }

    static synchronized void setTriggers(Context c, JSONArray triggers) {
        save(c, KEY_TRIGGERS, triggers);
    }

    /** Adds a one-off snooze trigger (one pending snooze per alarm id). */
    static synchronized void addSnooze(Context c, JSONObject base, long at) {
        try {
            JSONObject o = new JSONObject(base.toString());
            o.put("at", at);
            String aid = o.optString("aid", "");
            JSONArray old = load(c, KEY_SNOOZES);
            JSONArray out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject x = old.optJSONObject(i);
                if (x != null && !aid.equals(x.optString("aid", ""))) out.put(x);
            }
            out.put(o);
            save(c, KEY_SNOOZES, out);
        } catch (JSONException ignored) {
            // nothing to do
        }
    }

    /** Removes and returns every trigger that is due (or within 3 s of being due). Stale ones are dropped. */
    static synchronized List<JSONObject> popDue(Context c, long now) {
        List<JSONObject> due = new ArrayList<>();
        String[] keys = {KEY_TRIGGERS, KEY_SNOOZES};
        for (String key : keys) {
            JSONArray in = load(c, key);
            JSONArray keep = new JSONArray();
            for (int i = 0; i < in.length(); i++) {
                JSONObject o = in.optJSONObject(i);
                if (o == null) continue;
                long at = o.optLong("at", 0L);
                if (at <= now + 3000L) {
                    if (at >= now - STALE_MS) due.add(o);
                } else {
                    keep.put(o);
                }
            }
            save(c, key, keep);
        }
        Collections.sort(due, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(a.optLong("at", 0L), b.optLong("at", 0L));
            }
        });
        return due;
    }

    /** Earliest pending trigger time, or -1. Also drops stale entries. */
    private static long nextAt(Context c, long now) {
        long best = -1L;
        String[] keys = {KEY_TRIGGERS, KEY_SNOOZES};
        for (String key : keys) {
            JSONArray in = load(c, key);
            JSONArray keep = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < in.length(); i++) {
                JSONObject o = in.optJSONObject(i);
                if (o == null) { changed = true; continue; }
                long at = o.optLong("at", 0L);
                if (at < now - STALE_MS) { changed = true; continue; }
                keep.put(o);
                if (best < 0 || at < best) best = at;
            }
            if (changed) save(c, key, keep);
        }
        return best;
    }

    /** (Re)arms the single system alarm for the earliest pending trigger. */
    static synchronized void armNext(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        long next = nextAt(app, now);

        Intent fireIntent = new Intent(app, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_FIRE);
        PendingIntent fire = PendingIntent.getBroadcast(app, REQ_FIRE, fireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (next < 0) {
            am.cancel(fire);
            return;
        }
        long when = Math.max(next, now + 1500L);
        Intent showIntent = new Intent(app, MainActivityCompose.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent show = PendingIntent.getActivity(app, REQ_SHOW, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), fire);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, when, fire);
        }
    }
}
