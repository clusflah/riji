package app.birthdayreminder;

import android.graphics.drawable.Icon;
import android.os.VibratorManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONException;
import org.json.JSONObject;

/** Foreground service that rings an alarm: plays the chosen sound, vibrates and shows a full-screen notification. */
public class AlarmService extends Service {
    static final String ACTION_START = "app.birthdayreminder.ALARM_START";
    static final String ACTION_SNOOZE = "app.birthdayreminder.ALARM_SNOOZE";
    static final String ACTION_DISMISS = "app.birthdayreminder.ALARM_DISMISS";
    static final String ACTION_STOPPED = "app.birthdayreminder.ALARM_STOPPED";
    static final String EXTRA_PAYLOAD = "payload";

    private static final String CHANNEL_RING = "li_alarm_ring";
    private static final String CHANNEL_MISSED = "li_alarm_missed";
    private static final int NOTIF_RING = 5001;
    private static final int NOTIF_MISSED = 5002;
    private static final long RING_TIMEOUT_MS = 3 * 60 * 1000L;

    static volatile boolean ringing = false;
    static volatile String currentLabel = "";
    static volatile int currentSnooze = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            finishRinging(true);
        }
    };

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private JSONObject current = new JSONObject();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            startRinging(intent.getStringExtra(EXTRA_PAYLOAD));
        } else if (ACTION_SNOOZE.equals(action)) {
            snooze();
        } else if (ACTION_DISMISS.equals(action)) {
            finishRinging(false);
        } else if (!ringing) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startRinging(String payload) {
        try {
            current = new JSONObject(payload == null ? "{}" : payload);
        } catch (JSONException e) {
            current = new JSONObject();
        }
        String label = current.optString("label", "");
        if (label.isEmpty()) label = getString(R.string.alarm_default_label);
        currentLabel = label;
        currentSnooze = current.optInt("snooze", 0);

        ensureChannels();
        Notification n = buildRingNotification(label);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_RING, n);
        }
        ringing = true;
        showRingScreen();

        acquireWakeLock();
        stopPlayback();
        startSound();
        startVibration();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, RING_TIMEOUT_MS);
    }

    private void snooze() {
        int minutes = current.optInt("snooze", 0);
        if (minutes > 0) {
            AlarmStore.addSnooze(this, current, System.currentTimeMillis() + minutes * 60000L);
        }
        finishRinging(false);
    }

    private void finishRinging(boolean missed) {
        handler.removeCallbacks(timeout);
        stopPlayback();
        releaseWakeLock();
        boolean wasRinging = ringing;
        ringing = false;
        if (missed && wasRinging) showMissed();
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        sendBroadcast(new Intent(ACTION_STOPPED).setPackage(getPackageName()));
        AlarmStore.armNext(this);
        stopSelf();
    }

    // ---------- sound / vibration ----------

    private void startSound() {
        String path = current.optString("sound", "");
        MediaPlayer mp = AlarmSound.prepare(this, path, true);
        if (mp == null) return;
        try {
            mp.start();
            player = mp;
        } catch (RuntimeException e) {
            try { mp.release(); } catch (RuntimeException ignored) { }
        }
    }

    private void startVibration() {
        if (!current.optBoolean("vibrate", true)) return;
        vibrator = defaultVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 700, 500};
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            legacyVibrate(vibrator, pattern);
        }
    }

    /** 取振动器：Android 12 起通过 VibratorManager，更老的系统直接取 */
    private Vibrator defaultVibrator() {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return legacyVibrator();
    }

    @SuppressWarnings("deprecation")
    private Vibrator legacyVibrator() {
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Android 7.x 没有 VibrationEffect */
    @SuppressWarnings("deprecation")
    private static void legacyVibrate(Vibrator v, long[] pattern) {
        v.vibrate(pattern, 0);
    }

    private void stopPlayback() {
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) { }
            try { player.release(); } catch (RuntimeException ignored) { }
            player = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (RuntimeException ignored) { }
            vibrator = null;
        }
    }

    private void acquireWakeLock() {
        releaseWakeLock();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "birthdayreminder:alarm");
        wakeLock.acquire(RING_TIMEOUT_MS + 10000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            try { if (wakeLock.isHeld()) wakeLock.release(); } catch (RuntimeException ignored) { }
            wakeLock = null;
        }
    }

    /**
     * 锁屏 / 黑屏时直接打开响铃界面：全屏通知在部分国产手机上只变成一条横幅。
     * 安卓 10 起后台不能随便开界面，用户允许了“显示在其他应用上层”才行；正在用手机时不打扰，只弹通知。
     */
    private void showRingScreen() {
        if (Build.VERSION.SDK_INT < 29 || !android.provider.Settings.canDrawOverlays(this)) return;
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean locked = (km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive());
        if (!locked) return;
        try {
            startActivity(new Intent(this, AlarmActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException ignored) { }   // 打不开就算了，全屏通知照样在
    }

    // ---------- notifications ----------

    private void ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ring = new NotificationChannel(CHANNEL_RING,
                getString(R.string.alarm_channel_name), NotificationManager.IMPORTANCE_HIGH);
        ring.setSound(null, null);
        ring.enableVibration(false);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ring);
        NotificationChannel missed = new NotificationChannel(CHANNEL_MISSED,
                getString(R.string.alarm_missed_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(missed);
    }

    private Notification.Builder newBuilder(String channel) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, channel);
        }
        return legacyBuilder();
    }

    /** Android 7.x 没有通知渠道，只能用不带渠道的构造方法 */
    @SuppressWarnings("deprecation")
    private Notification.Builder legacyBuilder() {
        return new Notification.Builder(this);
    }

    /** Android 7.x 靠优先级让闹钟通知弹出来（8.0 起由渠道的重要程度决定） */
    @SuppressWarnings("deprecation")
    private static void legacyMaxPriority(Notification.Builder b) {
        b.setPriority(Notification.PRIORITY_MAX);
    }

    /** 通知上的按钮（不带图标）：用 Notification.Action（旧的 addAction(图标编号, 文字, 意图) 从 Android 6.0 起已过时） */
    private static Notification.Action action(CharSequence title, PendingIntent pi) {
        return new Notification.Action.Builder((Icon) null, title, pi).build();
    }

    private static final String CHANNEL_FALLBACK = "li_alarm_fallback";
    private static final int NOTIF_FALLBACK = 7302;

    /**
     * 保底：响铃服务启动不了时，发一条闹钟级别的通知代替——用闹钟铃声和闹钟音量、一直响到用户点掉、能弹到锁屏上。
     * 同时把这次失败记下来，“提醒检查”里会提示去放开后台限制。
     */
    public static void postFallback(Context ctx, JSONObject payload, String reason) {
        Context app = ctx.getApplicationContext();
        app.getSharedPreferences("li_native_prefs", Context.MODE_PRIVATE).edit()
                .putLong("alarmFallbackAt", System.currentTimeMillis()).putString("alarmFallbackReason", reason).apply();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        android.net.Uri sound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_FALLBACK) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_FALLBACK, "闹钟（保底提醒）", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("响铃服务启动不了时，用通知代替闹钟响铃");
            ch.setSound(sound, new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 700, 500, 700, 500});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(app, MainActivityCompose.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(app, NOTIF_FALLBACK, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String label = payload == null ? "" : payload.optString("label", "");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, CHANNEL_FALLBACK) : legacyBuilderFor(app, sound);
        b.setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(label.isEmpty() ? "闹钟" : label)
                .setContentText("闹钟时间到了（响铃界面没能启动，已改用通知提醒）")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true);
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;      // 一直响，直到用户点掉或划掉
        try { nm.notify(NOTIF_FALLBACK, n); } catch (SecurityException ignored) { }
    }

    /** Android 7.x 没有通知渠道：铃声和优先级写在通知自己身上 */
    @SuppressWarnings("deprecation")
    private static Notification.Builder legacyBuilderFor(Context app, android.net.Uri sound) {
        return new Notification.Builder(app).setSound(sound, android.media.AudioManager.STREAM_ALARM)
                .setPriority(Notification.PRIORITY_MAX).setVibrate(new long[]{0, 700, 500, 700, 500});
    }

    private PendingIntent servicePi(String action, int req) {
        Intent i = new Intent(this, AlarmService.class).setAction(action);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildRingNotification(String label) {
        Intent open = new Intent(this, AlarmActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = newBuilder(CHANNEL_RING)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(label)
                .setContentText(getString(R.string.alarm_ringing))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(contentPi)
                .setFullScreenIntent(contentPi, true);
        if (Build.VERSION.SDK_INT < 26) legacyMaxPriority(b);
        int snooze = current.optInt("snooze", 0);
        if (snooze > 0) {
            b.addAction(action(getString(R.string.alarm_snooze_fmt, snooze), servicePi(ACTION_SNOOZE, 2)));
        }
        b.addAction(action(getString(R.string.alarm_dismiss), servicePi(ACTION_DISMISS, 3)));
        return b.build();
    }

    private void showMissed() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, MainActivityCompose.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 4, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = newBuilder(CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(currentLabel)
                .setContentText(getString(R.string.alarm_missed_text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIF_MISSED, n);
        } catch (SecurityException ignored) {
            // notifications not allowed
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        stopPlayback();
        releaseWakeLock();
        ringing = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
