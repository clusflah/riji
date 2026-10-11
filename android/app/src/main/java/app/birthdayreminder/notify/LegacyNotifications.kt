package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 清掉旧版（网页那套）用 Capacitor 通知插件排好的定时通知。
 *
 * 旧版每次打开时会把未来 180 天的生日/提醒通知交给系统排好；切换到原生版之后旧版界面不再运行，
 * 这些通知不会再更新，会继续按旧数据弹，还会和原生版自己排的通知重复。
 * 这里按插件自己的存储（名为 NOTIFICATION_STORE 的记录，键 = 通知 id）逐条取消并清空。
 * 插件的类只通过名字引用，不依赖它的代码；哪怕之后把旧版整个删掉，这个函数也不会报错，只是什么都不做。
 */
object LegacyNotifications {
    private const val STORE = "NOTIFICATION_STORE"
    private const val PUBLISHER = "com.capacitorjs.plugins.localnotifications.TimedNotificationPublisher"

    /** 返回取消了多少条 */
    fun cancelAll(ctx: Context): Int {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val ids = prefs.all.keys.mapNotNull { it.toIntOrNull() }
        if (ids.isEmpty()) return 0
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var flags = PendingIntent.FLAG_NO_CREATE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        var n = 0
        for (id in ids) {
            try {
                val intent = Intent().setClassName(app, PUBLISHER)
                val pi = PendingIntent.getBroadcast(app, id, intent, flags)
                if (pi != null) { am.cancel(pi); pi.cancel(); n++ }
            } catch (e: Exception) {
                // 某一条取消失败不影响其它条
            }
        }
        // 清空记录，不然插件在手机重启后会按记录把它们重新排上
        prefs.edit().clear().apply()
        return n
    }
}
