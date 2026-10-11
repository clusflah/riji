package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.birthdayreminder.WidgetUpdater
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.domain.ReminderActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 处理提醒通知上的两个按钮：
 *  - 完成：给那一天那次打勾，关掉通知，重排（同一次的其它通知——比如提前提醒之后的准时提醒——就不会再弹）
 *  - N 分钟后提醒：关掉通知，N 分钟后再弹一次一模一样的（带同样的按钮）
 */
class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "app.birthdayreminder.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.birthdayreminder.REMINDER_SNOOZE"
        private const val SNOOZE_BASE = 9000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val notifId = intent.getIntExtra("id", 0)
        app.getSystemService(NotificationManager::class.java).cancel(notifId)
        when (intent.action) {
            ACTION_SNOOZE -> snooze(app, intent, notifId)
            ACTION_DONE -> {
                val rid = intent.getStringExtra("rid") ?: return
                val occ = intent.getStringExtra("occ")
                val pending = goAsync()          // 要读写数据库，放后台做完再结束
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = AppDatabase.get(app).reminderDao()
                        dao.getById(rid)?.let { dao.upsert(ReminderActions.markDone(it, occ)) }
                        ReminderScheduler.reschedule(app)
                        try { WidgetUpdater.sync(app) } catch (e: Exception) { }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun snooze(app: Context, src: Intent, notifId: Int) {
        val i = Intent(app, NotifyReceiver::class.java).apply {
            action = "app.birthdayreminder.NOTIFY_SNOOZE"
            putExtra("id", notifId)
            putExtra("title", src.getStringExtra("title"))
            putExtra("body", src.getStringExtra("body"))
            putExtra("rid", src.getStringExtra("rid"))
            putExtra("occ", src.getStringExtra("occ"))
        }
        // 请求码和正常排程(7000 起)分开，重排时不会被误取消
        val pi = PendingIntent.getBroadcast(app, SNOOZE_BASE + (notifId % 1000), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = ReminderActions.snoozeAt(System.currentTimeMillis())
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
