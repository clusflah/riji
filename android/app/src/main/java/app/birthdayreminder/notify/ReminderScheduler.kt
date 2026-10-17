package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.DeliveryPlan
import app.birthdayreminder.domain.ScheduleBuilder
import app.birthdayreminder.domain.Modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 把 ScheduleBuilder 算出来的通知交给 AlarmManager 定时。
 * 每次数据有变化、开机、升级、打开 App、每天凌晨的例行检查、每弹出一条之后都会整套取消重排。
 */
object ReminderScheduler {
    private const val MAX_NOTIF = ScheduleBuilder.MAX_NOTIF
    private const val BASE_CODE = 7000
    const val ACTION = "app.birthdayreminder.NOTIFY"
    const val GROUP = "reminder"

    /** 通知编号按这一条提醒固定（40000–49999）：以前用排序序号，重排后后一条会顶掉还没处理的前一条通知 */
    fun notifId(key: String) = DeliveryPlan.notifId(key, 40000, 10000)

    private fun pendingIntent(ctx: Context, code: Int, item: ScheduleBuilder.Item?, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, NotifyReceiver::class.java).apply {
            action = ACTION
            if (item != null) {
                putExtra("id", notifId(item.key))
                putExtra("title", item.title)
                putExtra("body", item.body)
                putExtra(NotifyReceiver.EXTRA_GROUP, GROUP)
                putExtra(Delivery.EXTRA_KEY, item.key)
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
        Delivery.lock.withLock { rescheduleLocked(ctx.applicationContext) }
    }

    private suspend fun rescheduleLocked(app: Context) {
        val db = AppDatabase.get(app)
        val settings = SettingsStore.get(app)
        val now = System.currentTimeMillis()
        // 关掉“系统通知”就一条都不排（下面会把之前排的全部取消）
        // 关掉的功能（生日 / 提醒）不排通知
        val birthdays = if (Prefs.moduleOn(app, Modules.BIRTHDAY)) db.birthdayDao().getAll() else emptyList()
        val reminders = if (Prefs.moduleOn(app, Modules.REMINDER)) db.reminderDao().getAll() else emptyList()
        // 往前多算 6 小时：过了点、却没弹出来的要补发（哪些该补见 DeliveryPlan）
        val list = if (!settings.notify) emptyList() else ScheduleBuilder.build(
            birthdays, reminders, settings.dailyTime, now = now - DeliveryPlan.GRACE_MS,
            holidays = HolidayRepository.current(app), holidayAlert = settings.holidayAlert
        )
        val byKey = list.associateBy { it.key }
        val slots = DeliveryPlan.plan(list.map { it.key to it.at }, Delivery.promised(app, GROUP), now).take(MAX_NOTIF)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        NotificationHelper.ensureChannel(app)

        for (i in 0 until MAX_NOTIF) {
            pendingIntent(app, BASE_CODE + i, null, false)?.let { am.cancel(it) }
        }

        slots.forEachIndexed { i, slot ->
            val item = byKey[slot.key] ?: return@forEachIndexed
            val pi = pendingIntent(app, BASE_CODE + i, item, true) ?: return@forEachIndexed
            Delivery.setExact(app, slot.fireAt, pi)
        }
        Delivery.savePromised(app, GROUP, slots.map { it.key })
    }
}
