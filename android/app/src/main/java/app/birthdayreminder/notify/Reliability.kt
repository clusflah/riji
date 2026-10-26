package app.birthdayreminder.notify

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
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
        // “生日与提醒”“用药提醒”“用药提醒（响铃）”几类通知，哪个被单独关掉都算（还没建的渠道不算）
        val channelOn = Build.VERSION.SDK_INT < 26 || listOf(NotificationHelper.CHANNEL_ID, MedNotify.CHANNEL, MedNotify.CHANNEL_RING).all {
            (nm.getNotificationChannel(it)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
        }
        val notifOk = NotificationManagerCompat.from(app).areNotificationsEnabled() && channelOn
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(app.packageName)
        // 在系统里把日迹设成“限制后台”，或者被系统归到“受限”一档：定时提醒基本收不到
        val bgOk = Build.VERSION.SDK_INT < 28 || (!(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted &&
            (Build.VERSION.SDK_INT < 30 || (app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).appStandbyBucket < UsageStatsManager.STANDBY_BUCKET_RESTRICTED))
        // Android 14 起“全屏通知”可以单独关掉：关了之后锁屏时不会弹出闹钟响铃界面，只剩一条横幅
        val fullScreenOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        // 最近 30 天里闹钟有没有因为系统拒绝而改用保底通知
        val sp = app.getSharedPreferences("li_native_prefs", Context.MODE_PRIVATE)
        val fallbackAt = sp.getLong("alarmFallbackAt", 0L)
        val fallbackOk = fallbackAt == 0L || System.currentTimeMillis() - fallbackAt > 30L * 24 * 3600 * 1000
        val fallbackWhen = if (fallbackAt > 0) java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(fallbackAt)) else ""
        // 最近 30 天里有没有提醒晚了 10 分钟以上才弹出（多半是手机在后台把日迹压住了）
        val late = Delivery.lastLate(app)?.takeIf { System.currentTimeMillis() - it.first < 30L * 24 * 3600 * 1000 }
        val lateWhen = late?.let { java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(it.first)) } ?: ""
        return listOf(
            Item("app", "App 里的“系统通知”开关", settings.notify, "已打开", "已关闭：生日和提醒都不会通知，到下面“通知”一节打开", ""),
            Item("notif", "通知权限", notifOk, "已允许", "没有允许，或者“生日与提醒”这类通知被关掉了：提醒不会弹出来", "去打开"),
            Item("exact", "准时提醒权限", exactOk, "已允许", "没有允许：提醒和闹钟可能晚几分钟甚至更久才响", "去允许"),
            Item("battery", "不受省电限制", batteryOk, "已允许", "没有允许：手机省电时可能推迟提醒，建议允许", "去允许"),
            Item("background", "后台运行", bgOk, "没有被限制", "被限制了：App 不在前台时提醒和闹钟可能完全收不到，请在“应用信息 → 耗电”里改成“不限制”", "去设置"),
            Item("fullscreen", "全屏通知（闹钟响铃界面）", fullScreenOk, "已允许", "被关掉了：锁屏时闹钟不会弹出响铃界面，只有一条横幅", "去允许"),
            Item("late", "提醒准时弹出", late == null, "最近 30 天没有晚弹的提醒",
                "$lateWhen 的提醒晚了 ${late?.second ?: 0} 分钟才弹出：手机在后台把日迹压住了。请打开下面的“闹钟级提醒”，并按下面的说明允许后台运行", "去设置"),
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
            "late" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            "background" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
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

    /** 不同牌子手机放开后台的位置不一样，按牌子给出具体路径 */
    val VENDOR_TIP: String get() = when (Build.MANUFACTURER.lowercase()) {
        "oppo", "realme", "oneplus" ->
            "OPPO / 一加 / 真我：在“设置 → 应用 → 应用管理 → 日迹 → 耗电管理”里打开“允许完全后台行为”“允许自动启动”“允许关联启动”；" +
                "再在最近任务里把日迹下拉加锁，免得清理后台时被关掉。"
        "vivo", "iqoo" ->
            "vivo：在“设置 → 电池 → 后台耗电管理 → 日迹”选“允许后台高耗电”；在“i 管家 → 应用管理 → 权限管理 → 自启动”里允许日迹；最近任务里把日迹加锁。"
        "xiaomi", "redmi" ->
            "小米 / 红米：在“设置 → 应用设置 → 应用管理 → 日迹”里打开“自启动”，“省电策略”选“无限制”；最近任务里把日迹加锁。"
        "huawei", "honor" ->
            "华为 / 荣耀：在“设置 → 电池 → 应用启动管理 → 日迹”关掉“自动管理”，把“允许自启动”“允许关联启动”“允许后台活动”都打开。"
        else -> "部分手机还需在“应用信息 → 耗电”里允许后台运行和自启动；最近任务里把日迹加锁。"
    }

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
        Delivery.setExact(app, System.currentTimeMillis() + TEST_DELAY_SEC * 1000L, pi)
    }
}
