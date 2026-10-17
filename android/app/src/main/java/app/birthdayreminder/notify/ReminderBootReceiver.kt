package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.birthdayreminder.AlarmEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 所有提醒的“总检修”：生日/提醒、用药、生理期、闹钟全部按最新数据重排，稍后提醒重新排上。
 *
 * 各类提醒都只提前排了一段（提醒每条 8 次、用药 48 小时、闹钟 40 次……），以前只有打开 App 才会续上，
 * 很久不打开 App 就会慢慢不响了。现在开机、升级、改时间、回到 App、每天凌晨都检修一遍。
 */
object Upkeep {
    const val ACTION_DAILY = "app.birthdayreminder.UPKEEP"
    private const val CODE = 6980

    /** 一项出错不影响别的 */
    suspend fun runAll(ctx: Context) {
        val app = ctx.applicationContext
        try { ReminderScheduler.reschedule(app) } catch (e: Exception) { }
        try { MedScheduler.rescheduleNow(app) } catch (e: Exception) { }
        try { CycleScheduler.rescheduleNow(app) } catch (e: Exception) { }
        try { AlarmEngine.sync(app) } catch (e: Exception) { }
        try { Delivery.rearmSnoozes(app) } catch (e: Exception) { }
        armDaily(app)
    }

    /** 在后台检修一遍（回到 App 时用） */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runAll(app) }
    }

    /** 每天凌晨 0:20 左右醒一次做检修；不要求准时，省电模式下晚一点也没关系 */
    fun armDaily(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = LocalDate.now().plusDays(1).atTime(0, 20).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(app, CODE, Intent(app, ReminderBootReceiver::class.java).setAction(ACTION_DAILY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

/** 开机、升级、改系统时间/时区、准时权限变化之后（AlarmManager 里的定时会丢或者不准），以及每天凌晨的检修，都在这里重排一遍。 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 先把明天的检修排上，后面哪一步出问题也不会断掉
                Upkeep.armDaily(context)
                if (intent.action != Upkeep.ACTION_DAILY) LegacyNotifications.cancelAll(context)     // 从网页版升级上来的：清掉旧版排过的定时通知
                Upkeep.runAll(context)
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
