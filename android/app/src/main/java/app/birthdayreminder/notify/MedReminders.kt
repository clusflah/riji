package app.birthdayreminder.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.birthdayreminder.MainActivityCompose
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.MedLog
import app.birthdayreminder.data.Medication
import app.birthdayreminder.domain.DeliveryPlan
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.domain.MedicalLogic
import app.birthdayreminder.domain.Modules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** 服药的记录操作（界面和通知按钮共用）：已服用（扣库存、低库存提醒）、跳过、撤销、临时加一次 */
object MedActions {
    suspend fun take(ctx: Context, medId: String, date: String, time: String, amount: Double, scheduled: Boolean = true) {
        val app = ctx.applicationContext
        val dao = AppDatabase.get(app).medDao()
        val med = dao.getMed(medId) ?: return
        val id = if (scheduled) MedLogic.doseKey(medId, date, time) else UUID.randomUUID().toString()
        val old = if (scheduled) dao.getLogs().firstOrNull { it.id == id } else null
        dao.upsertLog(MedLog(id, medId, med.name, date, time, amount, "taken", System.currentTimeMillis(), scheduled))
        // 之前没吃过（或之前是跳过）才扣库存，避免重复扣
        if (old?.status != "taken") {
            val after = med.copy(stock = MedLogic.afterTake(med.stock, amount), updatedAt = System.currentTimeMillis())
            dao.upsertMed(after)
            if (MedLogic.isLow(after) && !MedLogic.isLow(med)) MedNotify.lowStock(app, after)
        }
        MedScheduler.reschedule(app)
    }

    suspend fun skip(ctx: Context, medId: String, date: String, time: String, amount: Double) {
        val app = ctx.applicationContext
        val dao = AppDatabase.get(app).medDao()
        val med = dao.getMed(medId) ?: return
        val id = MedLogic.doseKey(medId, date, time)
        val old = dao.getLogs().firstOrNull { it.id == id }
        // 从“已服用”改成“跳过”：把扣掉的库存加回来
        if (old?.status == "taken" && med.stock != null) dao.upsertMed(med.copy(stock = med.stock + old.amount))
        dao.upsertLog(MedLog(id, medId, med.name, date, time, amount, "skipped", System.currentTimeMillis(), true))
        MedScheduler.reschedule(app)
    }

    /** 记成某个时间吃的（避孕药历史里改时间、补记用）；之前没吃过才扣库存 */
    suspend fun takeAt(ctx: Context, medId: String, date: String, time: String, amount: Double, actionAt: Long) {
        val app = ctx.applicationContext
        val dao = AppDatabase.get(app).medDao()
        val med = dao.getMed(medId) ?: return
        val id = MedLogic.doseKey(medId, date, time)
        val old = dao.getLogs().firstOrNull { it.id == id }
        dao.upsertLog(MedLog(id, medId, med.name, date, time, amount, "taken", actionAt, true))
        if (old?.status != "taken") dao.upsertMed(med.copy(stock = MedLogic.afterTake(med.stock, amount)))
        MedScheduler.reschedule(app)
    }

    /** 撤销一条记录（吃过的话把库存加回来） */
    suspend fun undo(ctx: Context, log: MedLog) {
        val app = ctx.applicationContext
        val dao = AppDatabase.get(app).medDao()
        dao.deleteLog(log.id)
        if (log.status == "taken") dao.getMed(log.medId)?.let { m -> if (m.stock != null) dao.upsertMed(m.copy(stock = m.stock + log.amount)) }
        MedScheduler.reschedule(app)
    }
}

/** 用药通知：单独的通知渠道（可以单独设置铃声、重要程度）。私密的药品和就医记录照样提醒，但不写药名、病情 */
object MedNotify {
    /** 通知里显示的药名：私密药品不写名字（空字符串），免得在锁屏和通知栏露出来 */
    fun shownName(m: Medication) = if (m.isPrivate) "" else m.name

    const val CHANNEL = "medication"
    private const val LOW_BASE = 32000
    private const val EXP_BASE = 33000

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "用药提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "到点吃药、库存不足、药品过期"
            })
        }
    }

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context) = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, MainActivityCompose::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("li_open", "med"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 到点吃药：带“已服用 / 跳过 / 10 分钟后”三个按钮 */
    fun dose(ctx: Context, notifId: Int, medId: String, name: String, dosage: String, date: String, time: String, amount: Double, unit: String) {
        ensureChannel(ctx)
        if (!allowed(ctx)) return
        fun action(act: String, code: Int): PendingIntent {
            val i = Intent(ctx, MedReceiver::class.java).setAction(act)
                .putExtra("nid", notifId).putExtra("medId", medId).putExtra("name", name).putExtra("dosage", dosage)
                .putExtra("date", date).putExtra("time", time).putExtra("amount", amount).putExtra("unit", unit)
            return PendingIntent.getBroadcast(ctx, notifId * 4 + code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (name.isBlank()) "💊 该吃药了" else "💊 该吃药了：$name")
            .setContentText("$time · " + (dosage.ifBlank { MedLogic.fmt(amount) + unit }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "已服用", action(MedReceiver.ACTION_TAKE, 1))
            .addAction(0, "跳过", action(MedReceiver.ACTION_SKIP, 2))
            .addAction(0, "10 分钟后", action(MedReceiver.ACTION_SNOOZE, 3))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(notifId, n)
    }

    /** 复诊提醒 */
    fun followUp(ctx: Context, nid: Int, title: String, text: String) {
        ensureChannel(ctx)
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(nid, n)
    }

    fun lowStock(ctx: Context, m: Medication) {
        ensureChannel(ctx)
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (m.isPrivate) "💊 有一种药快吃完了" else "💊 ${m.name} 快吃完了")
            .setContentText("还剩 ${MedLogic.fmt(m.stock ?: 0.0)}${m.unit}，记得补药")
            .setAutoCancel(true).setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(LOW_BASE + (m.id.hashCode() and 0x3FF), n)
    }

    fun expired(ctx: Context, m: Medication) {
        ensureChannel(ctx)
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (m.isPrivate) "💊 有一种药已过有效期" else "💊 ${m.name} 已过有效期")
            .setContentText("有效期到 ${m.expiry}，请检查后再服用")
            .setAutoCancel(true).setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(EXP_BASE + (m.id.hashCode() and 0x3FF), n)
    }
}

/**
 * 用药提醒的排程：接下来 48 小时里还没记录的每一次服药，各排一个准时闹钟。
 * 每次提醒响了都会再往后排一次，所以不打开 App 也能一直提醒下去。
 */
object MedScheduler {
    private const val BASE = 30000
    private const val MAX = 60
    private const val PREF = "med_sched"
    private const val FU_BASE = 35000
    private const val FU_MAX = 40
    const val GROUP = "med"

    /** 通知编号按这一次服药固定下来：重排时顺序会变，用序号的话后面的提醒会顶掉还没处理的通知 */
    private fun notifId(doseKey: String) = BASE + 1000 + Math.floorMod(doseKey.hashCode(), 1000)   // 31000–31999，不和低库存（32000 起）撞

    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { try { rescheduleNow(app) } catch (e: Exception) { } }
    }

    suspend fun rescheduleNow(app: Context) = Delivery.lock.withLock { rescheduleLocked(app) }

    private suspend fun rescheduleLocked(app: Context) {
        val dao = AppDatabase.get(app).medDao()
        // 用药就医功能关掉了：取消所有用药和复诊提醒，不再排新的
        val on = Prefs.moduleOn(app, Modules.MED)
        val meds = if (on) dao.getMeds() else emptyList()
        val logs = dao.getLogs()
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        // 先取消上次排的
        val old = sp.getInt("count", MAX)
        for (i in 0 until maxOf(old, 1)) {
            am.cancel(PendingIntent.getBroadcast(app, BASE + i, Intent(app, MedReceiver::class.java).setAction(MedReceiver.ACTION_SHOW),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val zone = ZoneId.systemDefault()
        // 往前多看 6 小时：到点时被推迟、又赶上重排的那一次，还没记录就补发（见 DeliveryPlan）
        val ups = MedLogic.upcoming(meds, logs, LocalDateTime.now().minusHours(6), zone, hours = 54, max = MAX)
        val byKey = ups.associateBy { it.dose.key }
        val slots = DeliveryPlan.plan(ups.map { it.dose.key to it.at }, Delivery.promised(app, GROUP), System.currentTimeMillis()).take(MAX)
        val byId = meds.associateBy { it.id }
        slots.forEachIndexed { i, slot ->
            val u = byKey.getValue(slot.key)
            val m = byId[u.dose.medId] ?: return@forEachIndexed
            val intent = Intent(app, MedReceiver::class.java).setAction(MedReceiver.ACTION_SHOW)
                .putExtra("nid", notifId(u.dose.key)).putExtra("medId", m.id).putExtra("name", MedNotify.shownName(m)).putExtra("dosage", if (m.isPrivate) "" else m.dosage)
                .putExtra("date", u.dose.date.toString()).putExtra("time", u.dose.time).putExtra("amount", u.dose.amount).putExtra("unit", m.unit)
                .putExtra(Delivery.EXTRA_KEY, slot.key).putExtra(Delivery.EXTRA_AT, u.at)
            val pi = PendingIntent.getBroadcast(app, BASE + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Delivery.setExact(app, slot.fireAt, pi)
        }
        sp.edit().putInt("count", slots.size).apply()
        Delivery.savePromised(app, GROUP, slots.map { it.key })

        // 复诊：前一天 20:00、当天 09:00 各提醒一次（复诊不多，提前排好，不受 48 小时限制）
        val oldFu = sp.getInt("fu_count", FU_MAX)
        for (i in 0 until maxOf(oldFu, 1)) {
            am.cancel(PendingIntent.getBroadcast(app, FU_BASE + i, Intent(app, MedReceiver::class.java).setAction(MedReceiver.ACTION_FOLLOWUP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        val nowMs = System.currentTimeMillis()
        var n = 0
        for (f in MedicalLogic.upcomingFollowUps(if (on) dao.getRecords() else emptyList(), LocalDate.now())) {
            val text = if (f.record.isPrivate) "打开日迹查看" else MedicalLogic.followUpText(f.record)
            for ((at, title) in listOf(
                f.date.minusDays(1).atTime(20, 0) to "🏥 明天要复诊",
                f.date.atTime(9, 0) to "🏥 今天要复诊"
            )) {
                val ms = at.atZone(zone).toInstant().toEpochMilli()
                if (ms <= nowMs || n >= FU_MAX) continue
                val intent = Intent(app, MedReceiver::class.java).setAction(MedReceiver.ACTION_FOLLOWUP)
                    .putExtra("nid", FU_BASE + 500 + n).putExtra("title", title).putExtra("text", text)
                    .putExtra("medId", "-").putExtra("date", f.date.toString()).putExtra("time", "09:00")
                val pi = PendingIntent.getBroadcast(app, FU_BASE + n, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                Delivery.setExact(app, ms, pi)
                n++
            }
        }
        sp.edit().putInt("fu_count", n).apply()
        // 有效期：过期的药每天最多提醒一次
        val today = LocalDate.now()
        for (m in meds) {
            if (!m.active) continue
            val past = MedLogic.daysPastExpiry(m, today) ?: continue
            val key = "exp_${m.id}"
            if (past >= 0 && sp.getString(key, "") != today.toString()) {
                MedNotify.expired(app, m)
                sp.edit().putString(key, today.toString()).apply()
            }
        }
    }
}

/** 到点弹通知；处理通知上的“已服用 / 跳过 / 10 分钟后” */
class MedReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SHOW = "app.birthdayreminder.MED_SHOW"
        const val ACTION_TAKE = "app.birthdayreminder.MED_TAKE"
        const val ACTION_SKIP = "app.birthdayreminder.MED_SKIP"
        const val ACTION_SNOOZE = "app.birthdayreminder.MED_SNOOZE"
        const val ACTION_FOLLOWUP = "app.birthdayreminder.MED_FOLLOWUP"
        private const val SNOOZE_BASE = 34000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val nid = intent.getIntExtra("nid", 0)
        if (intent.action == ACTION_FOLLOWUP) {
            MedNotify.followUp(app, nid, intent.getStringExtra("title") ?: "🏥 复诊提醒", intent.getStringExtra("text") ?: "")
            return
        }
        val medId = intent.getStringExtra("medId") ?: return
        val name = intent.getStringExtra("name") ?: ""
        val dosage = intent.getStringExtra("dosage") ?: ""
        val date = intent.getStringExtra("date") ?: return
        val time = intent.getStringExtra("time") ?: return
        val amount = intent.getDoubleExtra("amount", 1.0)
        val unit = intent.getStringExtra("unit") ?: ""
        when (intent.action) {
            ACTION_SHOW -> {
                // 以前只在冷启动 App、点通知按钮时才排后面 48 小时的提醒：App 一直开在后台、通知又没点，
                // 两天后就不再提醒了。现在每响一次就接着往后排
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 补发的和原来那次都到了，只弹一次
                        if (Delivery.claim(app, MedScheduler.GROUP, intent)) MedNotify.dose(app, nid, medId, name, dosage, date, time, amount, unit)
                        MedScheduler.rescheduleNow(app)
                    } catch (e: Exception) { } finally { pending.finish() }
                }
            }
            ACTION_SNOOZE -> {
                app.getSystemService(NotificationManager::class.java).cancel(nid)
                val i = Intent(app, MedReceiver::class.java).setAction(ACTION_SHOW).putExtras(intent)
                // 记下来，重启手机后也会重新排上
                Delivery.snooze(app, SNOOZE_BASE + (nid % 1000), i, System.currentTimeMillis() + 10 * 60_000L)
            }
            ACTION_TAKE, ACTION_SKIP -> {
                app.getSystemService(NotificationManager::class.java).cancel(nid)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (intent.action == ACTION_TAKE) MedActions.take(app, medId, date, time, amount)
                        else MedActions.skip(app, medId, date, time, amount)
                    } finally { pending.finish() }
                }
            }
        }
    }
}
