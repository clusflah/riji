package app.birthdayreminder.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * “提醒检查”：看清楚会让提醒不准时的几个系统开关，并能一键跳到对应的系统设置页。
 */
object Reliability {
    data class Item(
        val key: String,
        val title: String,
        val ok: Boolean,
        val okText: String,
        val badText: String,
        val fixLabel: String
    )

    fun check(ctx: Context): List<Item> {
        val app = ctx.applicationContext
        val settings = SettingsStore.get(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        val channelOn = Build.VERSION.SDK_INT < 26 ||
            (nm.getNotificationChannel(NotificationHelper.CHANNEL_ID)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
        val notifOk = NotificationManagerCompat.from(app).areNotificationsEnabled() && channelOn
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(app.packageName)
        // Android 14 起“全屏通知”可以单独关掉：关了之后锁屏时不会弹出闹钟响铃界面，只剩一条横幅
        val fullScreenOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        // 最近 30 天里闹钟有没有因为系统拒绝而改用保底通知
        val sp = app.getSharedPreferences("li_native_prefs", Context.MODE_PRIVATE)
        val fallbackAt = sp.getLong("alarmFallbackAt", 0L)
        val fallbackOk = fallbackAt == 0L || System.currentTimeMillis() - fallbackAt > 30L * 24 * 3600 * 1000
        val fallbackWhen = if (fallbackAt > 0) java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(fallbackAt)) else ""
        return listOf(
            Item("app", "App 里的“系统通知”开关", settings.notify, "已打开", "已关闭：生日和提醒都不会通知，到下面“通知”一节打开", ""),
            Item("notif", "通知权限", notifOk, "已允许", "没有允许，或者“生日与提醒”这类通知被关掉了：提醒不会弹出来", "去打开"),
            Item("exact", "准时提醒权限", exactOk, "已允许", "没有允许：提醒和闹钟可能晚几分钟甚至更久才响", "去允许"),
            Item("battery", "不受省电限制", batteryOk, "已允许", "没有允许：手机省电时可能推迟提醒，建议允许", "去允许"),
            Item("fullscreen", "全屏通知（闹钟响铃界面）", fullScreenOk, "已允许", "被关掉了：锁屏时闹钟不会弹出响铃界面，只有一条横幅", "去允许"),
            Item("alarmsvc", "闹钟响铃", fallbackOk, "正常",
                "$fallbackWhen 那次闹钟系统没让响铃界面启动，已改用通知提醒。建议在“应用信息 → 耗电”里允许后台运行和自启动", "去设置")
        )
    }

    /** 跳到能修好这一项的系统设置页 */
    @SuppressLint("BatteryLife")
    fun fixIntent(ctx: Context, key: String): Intent? {
        val pkg = ctx.packageName
        return when (key) {
            "notif" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            "exact" -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")) else null
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
            "fullscreen" -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")) else null
            "alarmsvc" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            "vendor" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 某些手机没有对应页面时的备用入口 */
    fun fallbackIntent(ctx: Context, key: String): Intent? = when (key) {
        "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val VENDOR_TIP =
        "部分手机还需在“应用信息 → 耗电”里允许后台运行和自启动。"

    private const val TEST_CODE = 6990
    const val TEST_DELAY_SEC = 10

    /** 10 秒后弹一条测试通知：走的是和真实提醒完全一样的路（定时 -> 到点 -> 弹通知） */
    fun scheduleTest(ctx: Context) {
        val app = ctx.applicationContext
        NotificationHelper.ensureChannel(app)
        val i = Intent(app, NotifyReceiver::class.java).apply {
            action = "app.birthdayreminder.NOTIFY_TEST"
            putExtra("id", TEST_CODE)
            putExtra("title", "🔔 测试提醒")
            putExtra("body", "收到这条通知，说明提醒能正常弹出")
        }
        val pi = PendingIntent.getBroadcast(app, TEST_CODE, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + TEST_DELAY_SEC * 1000L
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
