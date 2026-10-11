package app.birthdayreminder.notify

import app.birthdayreminder.data.CycleProfiles
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.domain.Modules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 生理期提醒：经期前几天、经期当天、经前期开始、排卵日（早上 9 点），用普通的提醒通知弹出 */
object CycleScheduler {
    private const val BASE = 36000
    private const val MAX = 8
    private const val PREF = "cycle_sched"

    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { try { rescheduleNow(app) } catch (e: Exception) { } }
    }

    suspend fun rescheduleNow(app: Context) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        for (i in 0 until maxOf(sp.getInt("count", MAX), 1)) {
            am.cancel(PendingIntent.getBroadcast(app, BASE + i, Intent(app, NotifyReceiver::class.java).setAction("app.birthdayreminder.CYCLE"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        if (!Prefs.moduleOn(app, Modules.CYCLE)) { sp.edit().putInt("count", 0).apply(); return }
        val today = LocalDate.now()
        val days = AppDatabase.get(app).cycleDao().getAll(CycleProfiles.MAIN)   // 只提醒外面那份；隐私空间的不发通知
        val ps = CycleLogic.periods(days)
        val st = CycleLogic.stats(ps, Prefs.cycleDefaultLength(app), Prefs.cycleDefaultPeriod(app))
        val alerts = CycleLogic.alerts(CycleLogic.predict(ps, st, today), today, Prefs.cycleRemindBefore(app),
            Prefs.cycleRemindPms(app), Prefs.cycleRemindOvulation(app), Prefs.cycleBirthControl(app))
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var n = 0
        for (a in alerts) {
            val at = a.date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            if (at <= now || n >= MAX) continue
            val i = Intent(app, NotifyReceiver::class.java).setAction("app.birthdayreminder.CYCLE")
                .putExtra("id", BASE + 100 + n).putExtra("title", a.title).putExtra("body", a.body)
            val pi = PendingIntent.getBroadcast(app, BASE + n, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            n++
        }
        sp.edit().putInt("count", n).apply()
    }
}
