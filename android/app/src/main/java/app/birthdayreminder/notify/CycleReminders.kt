package app.birthdayreminder.notify

import app.birthdayreminder.data.CycleProfiles
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.domain.DeliveryPlan
import app.birthdayreminder.domain.Modules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** 生理期提醒：经期前几天、经期当天、经前期开始、排卵日（早上 9 点），用普通的提醒通知弹出 */
object CycleScheduler {
    private const val BASE = 36000
    private const val MAX = 8
    private const val PREF = "cycle_sched"
    private const val ACTION = "app.birthdayreminder.CYCLE"
    private const val GROUP = "cycle"

    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { try { rescheduleNow(app) } catch (e: Exception) { } }
    }

    suspend fun rescheduleNow(app: Context) = Delivery.lock.withLock {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        // 先排新的（同一个请求码会顶掉旧的），最后只取消多出来的：中途被系统打断也不会一条都不剩
        fun cancelFrom(from: Int) {
            for (i in from until maxOf(sp.getInt("count", MAX), 1)) {
                am.cancel(PendingIntent.getBroadcast(app, BASE + i, Intent(app, NotifyReceiver::class.java).setAction(ACTION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
        }
        if (!Prefs.moduleOn(app, Modules.CYCLE)) {
            cancelFrom(0)
            sp.edit().putInt("count", 0).apply()
            Delivery.savePromised(app, GROUP, emptyList())
            return@withLock
        }
        val today = LocalDate.now()
        val days = AppDatabase.get(app).cycleDao().getAll(CycleProfiles.MAIN)   // 只提醒外面那份；隐私空间的不发通知
        val ps = CycleLogic.periods(days)
        val st = CycleLogic.stats(ps, Prefs.cycleDefaultLength(app), Prefs.cycleDefaultPeriod(app))
        val alerts = CycleLogic.alerts(CycleLogic.predict(ps, st, today), today, Prefs.cycleRemindBefore(app),
            Prefs.cycleRemindPms(app), Prefs.cycleRemindOvulation(app), Prefs.cycleBirthControl(app))
        val zone = ZoneId.systemDefault()
        val byKey = alerts.associateBy { "${it.date}|${it.title}" }
        val entries = byKey.map { (k, a) -> k to a.date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli() }
        // 今天 9 点那条被推迟、又赶上重排的，照样补发（见 DeliveryPlan）
        val slots = DeliveryPlan.plan(entries, Delivery.promised(app, GROUP), System.currentTimeMillis()).take(MAX)
        // 先记“答应过”再排：排到一半被打断，没排上的下次检修会补发
        Delivery.savePromised(app, GROUP, slots.map { it.key })
        slots.forEachIndexed { n, slot ->
            val a = byKey.getValue(slot.key)
            val i = Intent(app, NotifyReceiver::class.java).setAction(ACTION)
                .putExtra("id", DeliveryPlan.notifId(slot.key, BASE + 100, 100)).putExtra("title", a.title).putExtra("body", a.body)
                .putExtra(NotifyReceiver.EXTRA_GROUP, GROUP).putExtra(Delivery.EXTRA_KEY, slot.key).putExtra(Delivery.EXTRA_AT, slot.fireAt)
                .putExtra(Delivery.EXTRA_DUE, slot.due)
            val pi = PendingIntent.getBroadcast(app, BASE + n, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Delivery.setExact(app, slot.fireAt, pi)
        }
        cancelFrom(slots.size)
        sp.edit().putInt("count", slots.size).apply()
    }
}
