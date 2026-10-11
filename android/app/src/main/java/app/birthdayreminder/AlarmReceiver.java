package app.birthdayreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Fired by AlarmManager when an alarm is due: starts the ringing service, then arms the next alarm. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "app.birthdayreminder.ALARM_FIRE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_FIRE.equals(intent.getAction())) return;
        List<JSONObject> due = AlarmStore.popDue(context, System.currentTimeMillis());
        if (!due.isEmpty()) {
            JSONObject first = due.get(0);
            if (due.size() > 1) {
                StringBuilder sb = new StringBuilder(first.optString("label", ""));
                for (int i = 1; i < due.size(); i++) {
                    String l = due.get(i).optString("label", "");
                    if (!l.isEmpty() && sb.indexOf(l) < 0) {
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(l);
                    }
                }
                try { first.put("label", sb.toString()); } catch (JSONException ignored) { }
            }
            Intent s = new Intent(context, AlarmService.class)
                    .setAction(AlarmService.ACTION_START)
                    .putExtra(AlarmService.EXTRA_PAYLOAD, first.toString());
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(s);
                } else {
                    context.startService(s);
                }
            } catch (RuntimeException e) {
                // 系统拒绝启动响铃服务（厂商后台限制、权限被收回等）：改发一条一直响的闹钟通知，保证闹钟仍然会响，并记下原因
                AlarmService.postFallback(context, first, e.getClass().getSimpleName());
            }
        }
        AlarmStore.armNext(context);
    }
}
