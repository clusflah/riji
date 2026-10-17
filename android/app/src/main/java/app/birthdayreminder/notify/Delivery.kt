package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.birthdayreminder.domain.DeliveryPlan
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 让提醒真正弹出来的几道保险，生日/提醒、生理期、用药共用：
 *  - setExact：能准时就准时，没权限或被系统拒绝就退回“大概时间”，总之一定排上
 *  - “答应过的提醒”名单：排好时记下 key，弹出来时划掉；重排时过点了还在名单上的就补发（规则见 DeliveryPlan）
 *  - 稍后提醒记下来：手机重启会清掉所有定时，开机后按记录重新排上
 */
object Delivery {
    const val EXTRA_KEY = "dkey"
    const val EXTRA_SNOOZE = "dsnooze"
    private const val PREF = "delivery"

    /** 重排和“弹出前划掉”互斥：不然刚弹出的一条会被同时在跑的重排当成漏掉的，再补发一次 */
    val lock = Mutex()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** 上次排好、还没弹出来的（调用方要先拿到 lock） */
    fun promised(ctx: Context, group: String): Set<String> =
        prefs(ctx).getStringSet("p_$group", null)?.toSet() ?: emptySet()

    fun savePromised(ctx: Context, group: String, keys: Collection<String>) {
        prefs(ctx).edit().putStringSet("p_$group", keys.toSet()).commit()
    }

    /**
     * 到点要弹之前调用，返回这一条该不该弹：
     * 带 key 的，还在名单上就划掉并返回 true；已经划掉了（补发和原来那条都到了）返回 false，不重复弹。
     * 没有 key 的（测试通知、稍后提醒、升级前排的）一律弹。
     */
    suspend fun claim(ctx: Context, group: String, intent: Intent): Boolean = lock.withLock {
        val sp = prefs(ctx)
        val snz = intent.getIntExtra(EXTRA_SNOOZE, 0)
        if (snz != 0) sp.edit().remove("s_$snz").commit()
        val key = intent.getStringExtra(EXTRA_KEY) ?: return@withLock true
        val set = promised(ctx, group)
        if (key !in set) return@withLock false
        savePromised(ctx, group, set - key)
        true
    }

    /** 稍后提醒：按 code 排上，同时记下来，手机重启后 rearmSnoozes 会重新排 */
    fun snooze(ctx: Context, code: Int, intent: Intent, at: Long) {
        val app = ctx.applicationContext
        intent.removeExtra(EXTRA_KEY)          // 稍后提醒不受“答应过”的名单管，到点一定弹
        intent.putExtra(EXTRA_SNOOZE, code)
        val pi = PendingIntent.getBroadcast(app, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setExact(app, at, pi)
        prefs(app).edit().putString("s_$code", "$at|" + intent.toUri(Intent.URI_INTENT_SCHEME)).apply()
    }

    /** 开机后把还没响的稍后提醒重新排上；关机期间错过的（6 小时以内）马上补一次 */
    fun rearmSnoozes(ctx: Context) {
        val app = ctx.applicationContext
        val sp = prefs(app)
        val now = System.currentTimeMillis()
        var late = 0
        for ((k, v) in sp.all) {
            if (!k.startsWith("s_") || v !is String) continue
            val code = k.removePrefix("s_").toIntOrNull()
            val at = v.substringBefore('|').toLongOrNull()
            val intent = try { Intent.parseUri(v.substringAfter('|'), Intent.URI_INTENT_SCHEME) } catch (e: Exception) { null }
            if (code == null || at == null || intent == null || intent.component?.packageName != app.packageName || at < now - DeliveryPlan.GRACE_MS) {
                sp.edit().remove(k).apply()
                continue
            }
            val pi = PendingIntent.getBroadcast(app, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            setExact(app, if (at > now) at else now + DeliveryPlan.CATCH_UP_GAP_MS * ++late, pi)
        }
    }
}
