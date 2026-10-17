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
        for (i in 0 until maxOf(sp.getInt("count", MAX), 1)) {
            am.cancel(PendingIntent.getBroadcast(app, BASE + i, Intent(app, NotifyReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        if (!Prefs.moduleOn(app, Modules.CYCLE)) {
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
        slots.forEachIndexed { n, slot ->
            val a = byKey.getValue(slot.key)
            val i = Intent(app, NotifyReceiver::class.java).setAction(ACTION)
                .putExtra("id", DeliveryPlan.notifId(slot.key, BASE + 100, 100)).putExtra("title", a.title).putExtra("body", a.body)
                .putExtra(NotifyReceiver.EXTRA_GROUP, GROUP).putExtra(Delivery.EXTRA_KEY, slot.key)
            val pi = PendingIntent.getBroadcast(app, BASE + n, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Delivery.setExact(app, slot.fireAt, pi)
        }
        sp.edit().putInt("count", slots.size).apply()
        Delivery.savePromised(app, GROUP, slots.map { it.key })
    }
}
