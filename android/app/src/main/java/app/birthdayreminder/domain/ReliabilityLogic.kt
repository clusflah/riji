package app.birthdayreminder.domain

import app.birthdayreminder.data.Reminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 通知上的“完成 / 稍后提醒”按钮背后的规则（纯逻辑） */
object ReminderActions {
    const val SNOOZE_MINUTES = 10

    /** 点“完成”：把那一天那次打勾；无时间待办（理论上不会有通知）就打待办的勾 */
    fun markDone(r: Reminder, occurrence: String?): Reminder {
        val done = r.done.toMutableMap()
        if (Occurrence.isTodo(r) || occurrence.isNullOrBlank()) done[Occurrence.TODO_DONE_KEY] = true
        else done[occurrence] = true
        return r.copy(done = done)
    }

    fun snoozeAt(now: Long): Long = now + SNOOZE_MINUTES * 60_000L
}

/** 自动备份的规则（纯逻辑）：什么时候该备份、文件叫什么、旧文件留几份 */
object AutoBackupPlan {
    val FREQS = listOf("off" to "关闭", "daily" to "每天", "weekly" to "每周")
    const val KEEP = 7
    const val PREFIX = "li-auto-backup-"

    /** 距上次超过“一天/一周”减 1 小时就该备份（留 1 小时余量，避免每天打开 App 的时间稍有前后就跳过一天） */
    fun isDue(freq: String, last: Long, now: Long): Boolean {
        val period = when (freq) {
            "daily" -> 24 * 3600_000L
            "weekly" -> 7 * 24 * 3600_000L
            else -> return false
        }
        return last <= 0L || now - last >= period - 3600_000L
    }

    fun fileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        return PREFIX + "%04d%02d%02d-%02d%02d%02d.zip".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second)
    }

    /** 文件夹里的文件名 -> 该删掉的旧自动备份（只动自己生成的文件，按时间留最新 keep 份） */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) && (it.endsWith(".json") || it.endsWith(".zip")) }
            .sortedByDescending { it.removeSuffix(".json").removeSuffix(".zip") }.drop(keep)
}
