package app.birthdayreminder.ai

import app.birthdayreminder.data.Alarm
import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.domain.AlarmTriggers
import app.birthdayreminder.domain.Occurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** AI 想添加一篇日记（正文是纯文字，落库时由 Android 侧包成一个文本块） */
data class NewDiary(val date: String, val text: String)

/** 工具校验结果：要么是可以落库的新数据 + 回给 AI 的结果，要么是错误信息 */
sealed class ToolOutcome {
    data class AddBirthday(val item: Birthday, val result: Map<String, Any?>) : ToolOutcome()
    data class AddReminder(val item: Reminder, val result: Map<String, Any?>) : ToolOutcome()
    data class AddAlarm(val item: Alarm, val result: Map<String, Any?>) : ToolOutcome()
    data class AddDiary(val item: NewDiary, val result: Map<String, Any?>) : ToolOutcome()
    /** 查询类工具不改数据，result 直接回给 AI（Map 或 List） */
    data class Info(val result: Any?) : ToolOutcome()
    data class Error(val message: String) : ToolOutcome() {
        val result: Map<String, Any?> get() = mapOf("error" to message)
    }
}

/**
 * AI 可以调用的工具：定义、参数校验、以及查询类工具的结果。
 * 规则、字段名、错误提示逐条对应旧版 buildTools / aiAdd* / aiList*。
 * 需要读写数据库的部分留给 Android 侧（AiToolRunner），这里只做纯逻辑。
 */
object AiTools {

    fun specs(includeAlarm: Boolean = true, includeAllergy: Boolean = true): List<ToolSpec> {
        val list = mutableListOf(
            ToolSpec("add_birthday", "添加一个生日", listOf(
                ToolParam("name", "string", "姓名", true),
                ToolParam("type", "string", "日期类型", true, enum = listOf("solar", "lunar")),
                ToolParam("month", "integer", "月，1-12", true),
                ToolParam("day", "integer", "日", true),
                ToolParam("year", "integer", "出生年份，选填"),
                ToolParam("leap", "boolean", "是否闰月（仅农历）"),
                ToolParam("note", "string", "备注，选填")
            )),
            ToolSpec("add_reminder", "添加一条提醒事项", listOf(
                ToolParam("title", "string", "提醒内容", true),
                ToolParam("date", "string", "日期，格式 YYYY-MM-DD；不填表示没有日期的待办事项"),
                ToolParam("time", "string", "时间，格式 HH:MM，不填为全天"),
                ToolParam("repeat", "string", "重复方式", enum = listOf("none", "daily", "weekly", "monthly", "yearly", "interval")),
                ToolParam("interval_unit", "string", "当 repeat=interval 时的周期单位", enum = listOf("day", "month", "year")),
                ToolParam("interval_n", "integer", "当 repeat=interval 时，每隔多少个单位"),
                ToolParam("note", "string", "备注，选填")
            )),
            ToolSpec("add_diary", "写一篇日记", listOf(
                ToolParam("text", "string", "日记内容", true),
                ToolParam("date", "string", "日期 YYYY-MM-DD，不填默认今天")
            )),
            ToolSpec("list_birthdays", "查看已保存的生日列表"),
            ToolSpec("list_reminders", "查看未来的提醒事项", listOf(ToolParam("days", "integer", "查看未来多少天，默认30"))),
            ToolSpec("list_diary", "查看最近的日记", listOf(ToolParam("limit", "integer", "返回条数，默认5"))),
            ToolSpec("query_fitness", "查询健身和身体数据（体重、身高、三围、心率、睡眠、血压、血氧、各项训练次数和组数、散步时间、跑步距离、自定义项目），按日期范围返回每天的数据、合计和单日最多", listOf(
                ToolParam("start_date", "string", "开始日期 YYYY-MM-DD，不填默认 30 天前"),
                ToolParam("end_date", "string", "结束日期 YYYY-MM-DD，不填默认今天")
            )),
            ToolSpec("period_data", "取某段时间的日记、健身、提醒数据，用来写周报或月报总结", listOf(
                ToolParam("period", "string", "时间段", true, enum = listOf("this_week", "last_week", "this_month", "last_month"))
            ))
        )
        if (includeAllergy) {
            list.add(ToolSpec("query_allergy", "查询过敏记录（眼睛过敏、鼻子过敏、咳嗽，程度 无/轻/中/重），按日期范围返回每天的记录和各症状出现天数", listOf(
                ToolParam("start_date", "string", "开始日期 YYYY-MM-DD，不填默认 30 天前"),
                ToolParam("end_date", "string", "结束日期 YYYY-MM-DD，不填默认今天")
            )))
        }
        if (includeAlarm) {
            list.add(ToolSpec("add_alarm", "添加一个闹钟", listOf(
                ToolParam("time", "string", "时间 HH:MM", true),
                ToolParam("label", "string", "名称，选填"),
                ToolParam("repeat", "string", "重复方式", enum = listOf("once", "daily", "workday", "restday", "custom")),
                ToolParam("days", "array", "当 repeat=custom 时，星期几（0=周日..6=周六）", itemsType = "integer"),
                ToolParam("snooze", "integer", "贪睡分钟数，可选 0/5/10/15/30")
            )))
            list.add(ToolSpec("list_alarms", "查看已设置的闹钟"))
        }
        return list
    }

    val TOOL_LABEL = mapOf(
        "add_birthday" to "添加生日", "add_reminder" to "添加提醒", "add_alarm" to "添加闹钟", "add_diary" to "写日记",
        "list_birthdays" to "查看生日", "list_reminders" to "查看提醒", "list_alarms" to "查看闹钟", "list_diary" to "查看日记",
        "query_fitness" to "查询健身数据", "query_allergy" to "查询过敏记录", "period_data" to "读取这段时间的数据"
    )

    /** 会改数据的工具：执行前要用户点“确认” */
    val WRITE_TOOLS = setOf("add_birthday", "add_reminder", "add_alarm", "add_diary")

    fun needsConfirm(name: String): Boolean = name in WRITE_TOOLS

    /** 确认卡片上的一行：把 AI 想加的内容用人话写出来 */
    fun preview(c: ToolCall): String {
        val a = c.args
        fun s(k: String) = (a[k] as? String)?.trim().orEmpty()
        fun n(k: String) = when (val v = a[k]) { is Number -> v.toLong().toString(); is String -> v.trim(); else -> "" }
        return when (c.name) {
            "add_birthday" -> "🎂 添加生日：" + s("name") + "，" + (if (a["type"] == "lunar") "农历" + (if (a["leap"] == true) "闰" else "") else "公历") +
                " ${n("month")}月${n("day")}日" + (if (n("year").isNotEmpty() && n("year") != "0") "（${n("year")}年出生）" else "")
            "add_reminder" -> {
                val d = s("date")
                "🔔 添加" + (if (d.isEmpty()) "待办：" + s("title") else "提醒：" + s("title") + "，" + d + " " + s("time").ifEmpty { "全天" } +
                    (if (s("repeat").isNotEmpty() && s("repeat") != "none") "，" + (REPEAT_LABEL[s("repeat")] ?: s("repeat")) else ""))
            }
            "add_alarm" -> "⏰ 添加闹钟：" + s("time") + (if (s("label").isNotEmpty()) "「${s("label")}」" else "") + "，" + (ALARM_REPEAT_LABEL[s("repeat")] ?: "仅一次")
            "add_diary" -> "📝 写日记（" + s("date").ifEmpty { "今天" } + "）：" + s("text").replace(Regex("\\s+"), " ").let { if (it.length > 60) it.take(60) + "…" else it }
            else -> (TOOL_LABEL[c.name] ?: c.name)
        }
    }

    private val REPEAT_LABEL = mapOf("daily" to "每天", "weekly" to "每周", "monthly" to "每月", "yearly" to "每年", "interval" to "自定义周期")
    private val ALARM_REPEAT_LABEL = mapOf("once" to "仅一次", "daily" to "每天", "workday" to "工作日", "restday" to "休息日", "custom" to "自定义")

    fun systemPrompt(today: LocalDate): String {
        val week = "日一二三四五六"[today.dayOfWeek.value % 7]
        return "你是\"日迹\"这个 App 里的助手，可以帮用户记录生日、提醒事项、日记，也可以设置闹钟" +
            "。今天是${today.year}年${today.monthValue}月${today.dayOfMonth}日 周$week" +
            "。需要用工具时直接调用，不用先询问确认；调用前请把用户说的相对日期换算成 YYYY-MM-DD 的绝对日期。" +
            "除非用户要求详细说明，否则回复保持简短，用中文回答。"
    }

    /** 对话里显示的“AI 做了什么”那一行 */
    fun describeToolCall(name: String, result: Any?): String {
        val label = TOOL_LABEL[name] ?: name
        if (result is Map<*, *> && result["error"] != null) return "⚠️ ${label}失败：${result["error"]}"
        if (name.startsWith("add_")) {
            val next = (result as? Map<*, *>)?.get("next")
            return "✅ ${label}成功" + (if (next != null) "（下次：$next）" else "")
        }
        val n = if (result is List<*>) result.size else 1
        return "🔍 ${label}（$n 条）"
    }

    // ---------------- 参数读取（模型给的数字可能是 3、3.0 或 "3"，都要认） ----------------

    private fun int(x: Any?): Int? = when (x) {
        is Number -> x.toInt()
        is String -> x.trim().toIntOrNull()
        else -> null
    }

    private fun str(x: Any?): String = (x as? String)?.trim().orEmpty()

    private fun bool(x: Any?): Boolean = when (x) {
        is Boolean -> x
        is String -> x.equals("true", true)
        else -> false
    }

    private fun fmtMD(d: LocalDate) = "${d.monthValue}月${d.dayOfMonth}日"

    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val HM = Regex("^\\d{2}:\\d{2}$")

    private fun validDate(s: String): Boolean = DATE.matches(s) && try { LocalDate.parse(s); true } catch (e: Exception) { false }
    private fun validTime(s: String): Boolean = HM.matches(s) && try { java.time.LocalTime.parse(s); true } catch (e: Exception) { false }

    // ---------------- 添加类工具 ----------------

    fun addBirthday(a: Map<String, Any?>, today: LocalDate, defAdvance: List<Int> = listOf(0, 1)): ToolOutcome {
        val name = str(a["name"])
        if (name.isEmpty()) return ToolOutcome.Error("缺少 name")
        val type = if (a["type"] == "lunar") "lunar" else "solar"
        val month = int(a["month"])
        val day = int(a["day"])
        if (month == null || month !in 1..12) return ToolOutcome.Error("month 必须是 1-12")
        val maxD = if (type == "lunar") 30 else YearMonth.of(2000, month).lengthOfMonth()
        if (day == null || day !in 1..maxD) return ToolOutcome.Error("day 超出范围")
        var year: Int? = null
        val yRaw = a["year"]
        if (yRaw != null && yRaw != "" && int(yRaw) != 0) {
            val y = int(yRaw)
            if (y == null || y < 1900 || y > today.year) return ToolOutcome.Error("year 不正确")
            year = y
        }
        val b = Birthday(
            id = UUID.randomUUID().toString(), name = name.take(30), type = type, year = year,
            month = month, day = day, leap = type == "lunar" && bool(a["leap"]),
            advance = defAdvance, note = str(a["note"]).take(60)
        )
        val next = Occurrence.nextBirthday(b, today, if (b.leap) 4000 else 400)
        return ToolOutcome.AddBirthday(b, mapOf("ok" to true, "name" to b.name, "next" to next?.first?.let { fmtMD(it) }))
    }

    fun addReminder(a: Map<String, Any?>, today: LocalDate): ToolOutcome {
        val title = str(a["title"])
        if (title.isEmpty()) return ToolOutcome.Error("缺少 title")
        val date = (a["date"] as? String)?.trim() ?: ""
        if (date.isEmpty()) {
            // 没给日期：记成“无时间待办”
            val r = Reminder(id = UUID.randomUUID().toString(), title = title.take(60), date = "", time = "",
                repeat = "none", advance = 0, note = str(a["note"]).take(80), done = emptyMap())
            return ToolOutcome.AddReminder(r, mapOf("ok" to true, "title" to r.title, "todo" to true))
        }
        if (!validDate(date)) return ToolOutcome.Error("date 需要 YYYY-MM-DD 格式")
        val time = a["time"] as? String
        if (!time.isNullOrEmpty() && !validTime(time)) return ToolOutcome.Error("time 需要 HH:MM 格式")
        val repeat = if (a["repeat"] in listOf("none", "daily", "weekly", "monthly", "yearly", "interval")) a["repeat"] as String else "none"
        var unit: String? = null
        var n: Int? = null
        if (repeat == "interval") {
            unit = if (a["interval_unit"] in listOf("day", "month", "year")) a["interval_unit"] as String else "day"
            val nn = int(a["interval_n"])
            n = if (nn != null && nn in 1..365) nn else 1
        }
        val r = Reminder(
            id = UUID.randomUUID().toString(), title = title.take(60), date = date, time = time ?: "",
            repeat = repeat, advance = 0, note = str(a["note"]).take(80), done = emptyMap(),
            intervalUnit = unit, intervalN = n
        )
        val next = Occurrence.nextReminder(r, today, 400)
        return ToolOutcome.AddReminder(r, mapOf("ok" to true, "title" to r.title, "next" to next?.let { fmtMD(it) }))
    }

    fun addAlarm(a: Map<String, Any?>, now: Long, zone: ZoneId): ToolOutcome {
        val time = a["time"] as? String ?: ""
        if (!validTime(time)) return ToolOutcome.Error("time 需要 HH:MM 格式")
        val repeat = if (a["repeat"] in listOf("once", "daily", "workday", "restday", "custom")) a["repeat"] as String else "once"
        val days = (a["days"] as? List<*>)?.mapNotNull { int(it) }?.filter { it in 0..6 } ?: listOf(1, 2, 3, 4, 5)
        val snooze = int(a["snooze"]).let { if (it != null && it in listOf(0, 5, 10, 15, 30)) it else 10 }
        val al = Alarm(
            id = UUID.randomUUID().toString(), time = time, label = str(a["label"]).take(20), repeat = repeat,
            days = if (repeat == "custom") days else emptyList(), snooze = snooze, vibrate = true, enabled = true,
            onceAt = if (repeat == "once") AlarmTriggers.nextOnceAt(time, now, zone, java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) else null
        )
        return ToolOutcome.AddAlarm(al, mapOf("ok" to true, "time" to al.time))
    }

    fun addDiary(a: Map<String, Any?>, today: LocalDate): ToolOutcome {
        val text = str(a["text"])
        if (text.isEmpty()) return ToolOutcome.Error("缺少 text")
        val d = a["date"] as? String
        val date = if (d != null && validDate(d)) d else today.toString()
        return ToolOutcome.AddDiary(NewDiary(date, text.take(6000)), mapOf("ok" to true, "date" to date))
    }

    // ---------------- 查询类工具 ----------------

    fun listBirthdays(birthdays: List<Birthday>, today: LocalDate): ToolOutcome.Info =
        ToolOutcome.Info(birthdays.filter { !it.isPrivate }.map { b ->
            val next = Occurrence.nextBirthday(b, today, if (b.leap) 4000 else 400)
            mapOf("name" to b.name, "desc" to Occurrence.birthdayDesc(b), "next" to next?.first?.let { fmtMD(it) })
        })

    fun listReminders(all: List<Reminder>, a: Map<String, Any?>, today: LocalDate): ToolOutcome.Info {
        val reminders = all.filter { !it.isPrivate }      // 私密提醒不给 AI 看
        val days = (int(a["days"]) ?: 30).coerceIn(1, 365)
        val out = ArrayList<Map<String, Any?>>()
        // 没做完的无时间待办也列出来（date 为空）
        for (r in reminders) if (Occurrence.isTodo(r) && !Occurrence.todoDone(r)) {
            out.add(mapOf("title" to r.title, "date" to "", "time" to "", "repeat" to "待办"))
        }
        for (r in reminders) {
            if (Occurrence.isTodo(r)) continue
            for (i in 0..days) {
                val k = today.plusDays(i.toLong())
                if (Occurrence.reminderOn(r, k) && r.done[k.toString()] != true) {
                    out.add(mapOf("title" to r.title, "date" to k.toString(), "time" to r.time, "repeat" to Occurrence.repeatLabel(r)))
                    break
                }
            }
        }
        return ToolOutcome.Info(out)
    }

    fun listAlarms(alarms: List<Alarm>): ToolOutcome.Info =
        ToolOutcome.Info(alarms.map { mapOf("time" to it.time, "label" to it.label, "enabled" to it.enabled) })

    fun listDiary(entries: List<Pair<String, String>>, a: Map<String, Any?>): ToolOutcome.Info {
        val n = (int(a["limit"]) ?: 5).coerceIn(1, 30)
        return ToolOutcome.Info(entries.take(n).map { mapOf("date" to it.first, "text" to it.second) })
    }

    // ---------------- 查询健身 / 过敏 / 周报月报数据 ----------------

    /** 解析 start_date / end_date：不填默认最近 30 天（含今天）；填错的当没填；最长 366 天 */
    fun range(a: Map<String, Any?>, today: LocalDate, defaultDays: Int = 30): Pair<LocalDate, LocalDate> {
        fun d(k: String) = (a[k] as? String)?.trim()?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
        var end = d("end_date") ?: today
        var start = d("start_date") ?: end.minusDays((defaultDays - 1).toLong())
        if (start.isAfter(end)) { val t = start; start = end; end = t }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365) start = end.minusDays(365)
        return start to end
    }

    /** 周报月报的时间段：本周（周一到今天）、上周、本月（1 号到今天）、上个月 */
    fun periodRange(period: String, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        "last_week" -> { val mon = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(1); mon to mon.plusDays(6) }
        "this_month" -> today.withDayOfMonth(1) to today
        "last_month" -> { val f = today.withDayOfMonth(1).minusMonths(1); f to f.withDayOfMonth(f.lengthOfMonth()) }
        else -> today.minusDays((today.dayOfWeek.value - 1).toLong()) to today
    }

    private fun inRange(date: String, r: Pair<LocalDate, LocalDate>) = date >= r.first.toString() && date <= r.second.toString()

    fun queryFitness(logs: List<app.birthdayreminder.data.FitnessLog>, a: Map<String, Any?>, today: LocalDate): ToolOutcome.Info {
        val r = range(a, today)
        val sel = logs.filter { inRange(it.date, r) }.sortedBy { it.date }
        val totals = LinkedHashMap<String, Any?>()
        val sets = LinkedHashMap<String, Any?>()
        val best = LinkedHashMap<String, Any?>()
        val other = LinkedHashMap<String, Any?>()
        val FF = app.birthdayreminder.domain.FitnessFields
        // 次数类：合计次数、组数；散步 / 跑步 / 自定义的时间、距离、数字：合计（带单位）
        for (f in FF.SUMMABLE) {
            val t = FF.round(f, sel.sumOf { it.metrics[f.key] ?: 0.0 })
            if (t > 0) {
                if (f.hasSets) totals[f.label] = t.toLong() else other[f.label] = FF.format(f, t)
                val b = sel.maxByOrNull { it.metrics[f.key] ?: 0.0 }!!
                val bv = b.metrics.getValue(f.key)
                best[f.label] = mapOf("value" to (if (f.decimals == 0) bv.toLong() else bv), "unit" to f.unit, "date" to b.date)
            }
            if (f.hasSets) {
                val st = sel.sumOf { it.metrics[FF.setsKey(f)] ?: 0.0 }
                if (st > 0) sets[f.label] = st.toLong()
            }
        }
        val weights = sel.mapNotNull { l -> l.metrics["weight"]?.let { l.date to it } }
        return ToolOutcome.Info(mapOf(
            "from" to r.first.toString(), "to" to r.second.toString(), "record_days" to sel.size,
            "totals_reps" to totals, "totals_sets" to sets, "totals_other" to other, "best_single_day" to best,
            // 身体数据（身高、三围、心率、睡眠、血压、血氧…）：最近一次和这段时间的平均
            "body_metrics" to FF.BODY_EXTRA.mapNotNull { f ->
                val vals = sel.mapNotNull { l -> l.metrics[f.key]?.let { l.date to it } }
                if (vals.isEmpty()) null else f.label to mapOf("latest" to vals.maxByOrNull { it.first }!!.second, "latest_date" to vals.maxByOrNull { it.first }!!.first,
                    "avg" to FF.round(f, vals.map { it.second }.average()), "unit" to f.unit, "days" to vals.size)
            }.toMap(),
            // 分组记录里单组最多（含极限组）
            "best_single_set" to app.birthdayreminder.domain.FitnessInsights.bestSets(sel).associate { b -> b.field.label to mapOf("reps" to b.value.toLong(), "date" to b.date) },
            "weight" to if (weights.isEmpty()) null else mapOf(
                "first" to mapOf("date" to weights.first().first, "kg" to weights.first().second),
                "last" to mapOf("date" to weights.last().first, "kg" to weights.last().second),
                "min" to weights.minOf { it.second }, "max" to weights.maxOf { it.second }),
            "days" to sel.map { mapOf("date" to it.date, "summary" to app.birthdayreminder.domain.FitnessFields.summary(it)) }
        ))
    }

    fun queryAllergy(logs: List<app.birthdayreminder.data.AllergyLog>, a: Map<String, Any?>, today: LocalDate): ToolOutcome.Info {
        val r = range(a, today)
        val sel = logs.filter { inRange(it.date, r) }.sortedBy { it.date }
        val counts = app.birthdayreminder.domain.AllergyLogic.SYMPTOMS.associate { (k, label) ->
            label to sel.count { app.birthdayreminder.domain.AllergyLogic.level(it, k) > 0 }
        }
        return ToolOutcome.Info(mapOf(
            "from" to r.first.toString(), "to" to r.second.toString(), "record_days" to sel.size,
            "days_with_symptom" to counts,
            "days" to sel.map { mapOf("date" to it.date, "summary" to app.birthdayreminder.domain.AllergyLogic.summary(it), "note" to it.note) }
        ))
    }

    /**
     * 周报 / 月报要用的数据：那段时间的日记（每篇最多 300 字）、健身摘要、提醒（发生在那段时间的每一次，标出是否已完成）、
     * 没做完的待办，完整版还有过敏摘要。私密内容由调用方事先去掉。
     */
    fun periodData(
        period: String, today: LocalDate,
        diaries: List<Pair<String, String>>,
        fitness: List<app.birthdayreminder.data.FitnessLog>,
        reminders: List<Reminder>,
        allergy: List<app.birthdayreminder.data.AllergyLog>?
    ): ToolOutcome.Info {
        val r = periodRange(period, today)
        val rem = ArrayList<Map<String, Any?>>()
        var d = r.first
        while (!d.isAfter(r.second) && rem.size < 100) {
            for (x in reminders) if (!x.isPrivate && Occurrence.reminderOn(x, d)) {
                rem.add(mapOf("date" to d.toString(), "title" to x.title, "done" to (x.done[d.toString()] == true)))
            }
            d = d.plusDays(1)
        }
        val m = linkedMapOf<String, Any?>(
            "period" to period, "from" to r.first.toString(), "to" to r.second.toString(),
            "diaries" to diaries.filter { inRange(it.first, r) }.sortedBy { it.first }
                .map { mapOf("date" to it.first, "text" to it.second.take(300)) },
            "fitness" to fitness.filter { inRange(it.date, r) }.sortedBy { it.date }
                .map { mapOf("date" to it.date, "summary" to app.birthdayreminder.domain.FitnessFields.summary(it)) },
            "reminders" to rem,
            "open_todos" to reminders.filter { !it.isPrivate && Occurrence.isTodo(it) && !Occurrence.todoDone(it) }.map { it.title }
        )
        if (allergy != null) m["allergy"] = allergy.filter { inRange(it.date, r) }.sortedBy { it.date }
            .map { mapOf("date" to it.date, "summary" to app.birthdayreminder.domain.AllergyLogic.summary(it)) }
        return ToolOutcome.Info(m)
    }

    /** “本周总结 / 本月总结”快捷按钮发给 AI 的话 */
    fun reportPrompt(period: String): String = when (period) {
        "this_month" -> "请用 period_data 工具读取我本月的数据，写一段月度总结：日记里的主要事情和心情、健身情况和变化、完成了哪些提醒、还有哪些待办。语气自然，分几段，不要编造数据里没有的内容。"
        else -> "请用 period_data 工具读取我本周的数据，写一段本周总结：日记里的主要事情和心情、健身情况、完成了哪些提醒、还有哪些待办。语气自然，分几段，不要编造数据里没有的内容。"
    }
}
