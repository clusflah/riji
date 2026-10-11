package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import app.birthdayreminder.data.MedicalRecord
import app.birthdayreminder.data.Medication
import java.time.LocalDate

/** 生病就医记录的规则（纯逻辑）：检查、说明、和服药记录合成一条时间线、复诊、导出 */
object MedicalLogic {
    // ---------------- 列表字段的保存 / 读取（JSON 字符串列表） ----------------
    fun listToJson(l: List<String>): String = if (l.isEmpty()) "" else MiniJson.write(l)

    @Suppress("UNCHECKED_CAST")
    fun listFromJson(s: String?): List<String> = try {
        (MiniJson.parse(s ?: "") as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.takeIf { v -> v.isNotBlank() } }.distinct()
    } catch (e: Exception) { emptyList() }

    private fun day(s: String?): LocalDate? = s?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }

    /** 检查，有问题返回提示 */
    fun validate(r: MedicalRecord): String? = when {
        r.illness.isBlank() -> "请填写病情或诊断"
        day(r.date) == null -> "就诊日期不对"
        r.followUp != null && day(r.followUp) == null -> "复诊日期不对"
        r.followUp != null && r.followUp < r.date -> "复诊日期不能早于就诊日期"
        else -> null
    }

    /** 列表里的第二行：医院 · 科室 · 医生 */
    fun where(r: MedicalRecord): String = listOf(r.hospital, r.department, r.doctor.let { if (it.isBlank()) "" else "$it 医生" })
        .filter { it.isNotBlank() }.joinToString(" · ")

    /** 这次开的药的名字（已删的药不列） */
    fun medNames(r: MedicalRecord, meds: List<Medication>): List<String> {
        val byId = meds.associateBy { it.id }
        return listFromJson(r.medIds).mapNotNull { byId[it]?.name }
    }

    // ---------------- 时间线：服药 + 就医 ----------------
    sealed class Entry {
        abstract val date: String
        data class Visit(val record: MedicalRecord) : Entry() { override val date get() = record.date }
        data class Dose(val row: MedLogic.HistoryRow) : Entry() { override val date get() = row.date }
    }

    /** 某段时间里的就医记录和服药记录合在一起：新的日期在前；同一天就医排最前，服药按时间从晚到早 */
    fun timeline(rows: List<MedLogic.HistoryRow>, records: List<MedicalRecord>, from: LocalDate, to: LocalDate): List<Entry> {
        val f = from.toString(); val t = to.toString()
        val visits = records.filter { it.date in f..t }.map { Entry.Visit(it) }
        val doses = rows.filter { it.date in f..t }.map { Entry.Dose(it) }
        return (visits + doses).sortedWith(
            compareByDescending<Entry> { it.date }
                .thenBy { if (it is Entry.Visit) 0 else 1 }
                .thenByDescending { (it as? Entry.Dose)?.row?.time ?: "" }
        )
    }

    // ---------------- 复诊 ----------------
    data class FollowUp(val date: LocalDate, val record: MedicalRecord)

    /** 从今天起 days 天内的复诊，按日期排 */
    fun upcomingFollowUps(records: List<MedicalRecord>, today: LocalDate, days: Long = 365): List<FollowUp> =
        records.mapNotNull { r -> day(r.followUp)?.let { FollowUp(it, r) } }
            .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(days)) }
            .sortedBy { it.date }

    /** 复诊的一句话：复诊：高血压（人民医院 · 心内科） */
    fun followUpText(r: MedicalRecord): String = "复诊：${r.illness}" + where(r).let { if (it.isBlank()) "" else "（$it）" }

    // ---------------- 导出 ----------------
    fun csvSection(records: List<MedicalRecord>, meds: List<Medication>): String {
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder("就诊日期,病情/诊断,症状,医院,科室,医生,医嘱/处理,开的药,复诊日期,备注\n")
        for (r in records.sortedByDescending { it.date }) {
            sb.append(listOf(r.date, q(r.illness), q(r.symptoms), q(r.hospital), q(r.department), q(r.doctor), q(r.advice),
                q(medNames(r, meds).joinToString("、")), r.followUp ?: "", q(r.note)).joinToString(",")).append('\n')
        }
        return sb.toString()
    }
}
