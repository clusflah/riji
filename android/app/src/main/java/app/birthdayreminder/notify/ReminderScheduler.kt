package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.ScheduleBuilder
import app.birthdayreminder.domain.Modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把 ScheduleBuilder 算出来的通知交给 AlarmManager 定时。
 * 每次数据有变化、开机、升级、打开 App 都会整套取消重排。
 */
object ReminderScheduler {
    private const val MAX_NOTIF = ScheduleBuilder.MAX_NOTIF
    private const val BASE_CODE = 7000
    private const val ACTION = "app.birthdayreminder.NOTIFY"

    private fun pendingIntent(ctx: Context, code: Int, item: ScheduleBuilder.Item?, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, NotifyReceiver::class.java).apply {
            action = ACTION
            putExtra("id", code)
            if (item != null) {
                putExtra("title", item.title)
                putExtra("body", item.body)
                // 提醒事项才有：通知上的“完成 / 稍后提醒”按钮要知道是哪条、哪一天
                item.reminderId?.let { putExtra("rid", it) }
                item.occurrence?.let { putExtra("occ", it) }
            }
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, code, intent, flags)
    }

    /** 取消旧的、按最新数据重新排。可以在任意线程调用。 */
    suspend fun reschedule(ctx: Context) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val db = AppDatabase.get(app)
        val settings = SettingsStore.get(app)
        // 关掉“系统通知”就一条都不排（下面会把之前排的全部取消）
        // 关掉的功能（生日 / 提醒）不排通知
        val birthdays = if (Prefs.moduleOn(app, Modules.BIRTHDAY)) db.birthdayDao().getAll() else emptyList()
        val reminders = if (Prefs.moduleOn(app, Modules.REMINDER)) db.reminderDao().getAll() else emptyList()
        val list = if (!settings.notify) emptyList() else ScheduleBuilder.build(
            birthdays, reminders, settings.dailyTime,
            holidays = HolidayRepository.current(app), holidayAlert = settings.holidayAlert
        )
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        NotificationHelper.ensureChannel(app)

        for (i in 0 until MAX_NOTIF) {
            pendingIntent(app, BASE_CODE + i, null, false)?.let { am.cancel(it) }
        }

        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        list.forEachIndexed { i, item ->
            val pi = pendingIntent(app, BASE_CODE + i, item, true) ?: return@forEachIndexed
            try {
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            }
        }
    }
}
