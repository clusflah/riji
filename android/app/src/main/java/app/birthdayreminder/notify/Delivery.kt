package app.birthdayreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import app.birthdayreminder.MainActivityCompose
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
    /**
     * 排给系统的触发时间（不是提醒的原定时间）：弹出时和现在比一比，晚了很多就是系统把定时压住了，记下来在“提醒检查”里提示。
     * 用原定时间的话，关机一晚、开机后补发的提醒也会被算成“晚了”，冤枉了系统
     */
    const val EXTRA_AT = "dat"
    /** 提醒原定的时间。补发的那条 EXTRA_AT 是补发时刻，只看它的话“定时丢了、半夜检修才补出来”永远不算晚 */
    const val EXTRA_DUE = "ddue"
    private const val PREF = "delivery"
    private const val SHOW_CODE = 6970
    /** 晚了这么久才弹出，就算“被系统耽误了” */
    private const val LATE_MS = 10 * 60_000L

    /** 重排和“弹出前划掉”互斥：不然刚弹出的一条会被同时在跑的重排当成漏掉的，再补发一次 */
    val lock = Mutex()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            // 闹钟级：OPPO 等国产系统会把后台 App 的普通定时压到 App 被打开才发，闹钟（setAlarmClock）不会被压
            if (exact && SettingsStore.get(ctx).strongAlarm) {
                val show = PendingIntent.getActivity(ctx, SHOW_CODE,
                    Intent(ctx, MainActivityCompose::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
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

    /** 弹过的 key 记多久：key 里都带着日期时间，同一个 key 不会过几天又该弹 */
    private const val SHOWN_KEEP_MS = 3 * 24 * 3600_000L

    /** 弹过的 key（“弹出时间|key”），去掉太早的 */
    private fun shown(sp: SharedPreferences, group: String, now: Long): Set<String> =
        sp.getStringSet("sh_$group", null).orEmpty().filter { (it.substringBefore('|').toLongOrNull() ?: 0L) > now - SHOWN_KEEP_MS }.toSet()

    /**
     * 到点要弹之前调用，返回这一条该不该弹：
     * 带 key 的，只要以前没弹过就弹，并记成“弹过”、从“答应过”名单上划掉；弹过的（补发和原来那条都到了）返回 false，不重复弹。
     * 以前是“不在答应过名单上就不弹”：名单没来得及存（重排存名单之前进程被杀）的那条，定时到了也被悄悄吞掉。
     * 没有 key 的（测试通知、稍后提醒、升级前排的）一律弹。
     */
    suspend fun claim(ctx: Context, group: String, intent: Intent): Boolean = lock.withLock {
        val sp = prefs(ctx)
        val now = System.currentTimeMillis()
        val snz = intent.getIntExtra(EXTRA_SNOOZE, 0)
        if (snz != 0) sp.edit().remove("s_$snz").commit()
        val key = intent.getStringExtra(EXTRA_KEY)
        if (key != null) {
            val done = shown(sp, group, now)
            if (done.any { it.substringAfter('|') == key }) return@withLock false
            sp.edit().putStringSet("sh_$group", done + "$now|$key").commit()
            savePromised(ctx, group, promised(ctx, group) - key)
        }
        // 原定时间手机是开着的（在这次开机之后），就按原定时间算晚了多久；
        // 原定时间手机还关着，就按补发时刻算，不冤枉系统
        val due = intent.getLongExtra(EXTRA_DUE, 0L)
        val bootAt = now - SystemClock.elapsedRealtime()
        val at = if (due > bootAt) due else intent.getLongExtra(EXTRA_AT, 0L)
        if (at > 0 && now - at > LATE_MS) sp.edit().putLong("lateAt", at).putLong("lateMin", (now - at) / 60_000).apply()
        true
    }

    /** 最近一次晚弹出的提醒：（原定时间, 晚了几分钟），没有记录返回 null */
    fun lastLate(ctx: Context): Pair<Long, Long>? {
        val sp = prefs(ctx)
        val at = sp.getLong("lateAt", 0L)
        return if (at > 0) at to sp.getLong("lateMin", 0L) else null
    }

    /** 打开“闹钟级提醒”后清掉旧的晚弹记录：不然这条提示要挂满 30 天，像是没改好 */
    fun clearLate(ctx: Context) {
        prefs(ctx).edit().remove("lateAt").remove("lateMin").apply()
    }

    /** 稍后提醒：按 code 排上，同时记下来，手机重启后 rearmSnoozes 会重新排 */
    fun snooze(ctx: Context, code: Int, intent: Intent, at: Long) {
        val app = ctx.applicationContext
        intent.removeExtra(EXTRA_KEY)          // 稍后提醒不受“答应过”的名单管，到点一定弹
        intent.removeExtra(EXTRA_AT)           // 也不算“晚了”
        intent.removeExtra(EXTRA_DUE)
        intent.putExtra(EXTRA_SNOOZE, code)
        val pi = PendingIntent.getBroadcast(app, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setExact(app, at, pi)
        prefs(app).edit().putString("s_$code", "$at|" + intent.toUri(Intent.URI_INTENT_SCHEME)).apply()
    }

    /** 取消了的稍后提醒：把记录也删掉，免得开机后又排回来 */
    fun forgetSnooze(ctx: Context, code: Int) {
        prefs(ctx).edit().remove("s_$code").apply()
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
