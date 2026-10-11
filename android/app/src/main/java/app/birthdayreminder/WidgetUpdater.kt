package app.birthdayreminder

import app.birthdayreminder.data.CycleProfiles
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.domain.AllergyLogic
import app.birthdayreminder.domain.FitnessFields
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.domain.Modules
import android.content.Context
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.AlarmBrief
import app.birthdayreminder.domain.AlarmTriggers
import app.birthdayreminder.domain.DiaryBrief
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.domain.WidgetSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * 原生 Kotlin 版和你旧版“已经在用”的桌面小组件之间的桥。
 *
 * 小组件怎么画（LiWidgetProvider / 2x2 / 4x2 / 配置页 / 布局 / 颜色）全部沿用现有 Java 和资源，一行没改；
 * 这里只负责用新数据库里的数据生成同一份汇总(JSON)，写进它读取的位置，再叫它刷新。
 *
 * 必须放在 app.birthdayreminder 这个包里，因为 LiWidgetProvider 的常量和方法是包内可见(package-private)的。
 */
object WidgetUpdater {
    /** 记一下“小组件的数据现在由原生版负责”，午夜自动刷新时才会用原生数据重算，不会误覆盖旧版网页推来的数据 */
    private const val KEY_NATIVE_OWNER = "native_owner"

    /** Map / List / 基本类型 -> JSON。null 直接跳过这个字段（Java 那边读缺失字段会用默认值）。 */
    private fun toJson(v: Any?): Any = when (v) {
        is Map<*, *> -> JSONObject().also { o ->
            for ((k, x) in v) if (x != null) o.put(k as String, toJson(x))
        }
        is List<*> -> JSONArray().also { a -> for (x in v) if (x != null) a.put(toJson(x)) }
        else -> v ?: JSONObject.NULL
    }

    private suspend fun buildJson(ctx: Context): String {
        val db = AppDatabase.get(ctx)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val holidays = HolidayRepository.current(ctx)

        // 关掉的功能不上桌面
        fun on(k: String) = Prefs.moduleOn(ctx, k)
        val alarms = (if (on(Modules.ALARM)) db.alarmDao().getAll().filter { !it.isPrivate } else emptyList()).mapNotNull { a ->
            if (!a.enabled) return@mapNotNull null
            AlarmTriggers.triggersFor(a, now, zone, today, holidays::isWorkday).firstOrNull()
                ?.let { AlarmBrief(a.label, it) }
        }
        val diaries = (if (on(Modules.DIARY)) db.diaryDao().getAll() else emptyList()).filter { !it.isPrivate }.map { e ->     // 私密日记不上桌面
            val blocks = BlockConverter.fromJson(e.content)
            val snippet = DiaryText.snippet(blocks, 24).ifBlank { if (DiaryText.imageCount(blocks) > 0) "[图片]" else "" }
            DiaryBrief(e.date, e.updatedAt, snippet)
        }
        val summary = WidgetSummary.build(
            today, zone, now,
            if (on(Modules.BIRTHDAY)) db.birthdayDao().getAll() else emptyList(),
            if (on(Modules.REMINDER)) db.reminderDao().getAll() else emptyList(),
            alarms, diaries, holidays
        )
        val root = toJson(summary) as JSONObject
        // 其它项目（健身、过敏、今天的用药、生理期、常用语）：统一成“左边标签 + 内容”的列表，小组件用同一种样式显示；私密的不上桌面
        fun dayLabel(d: LocalDate): String = when (java.time.temporal.ChronoUnit.DAYS.between(d, today)) {
            0L -> "今天"; 1L -> "昨天"; 2L -> "前天"; else -> "${d.monthValue}月${d.dayOfMonth}日"
        }
        fun item(label: String, text: String) = JSONObject().put("label", label).put("text", text)
        fun day(s: String) = try { LocalDate.parse(s) } catch (e: Exception) { null }
        if (on(Modules.FITNESS)) root.put("fitnessList", JSONArray().also { a ->
            db.fitnessDao().getAll().filter { !it.isPrivate }.sortedByDescending { it.date }.take(6).forEach { l ->
                day(l.date)?.let { a.put(item(dayLabel(it), FitnessFields.summary(l).ifBlank { "健身记录" })) }
            }
        })
        if (on(Modules.ALLERGY) && Features.allergy) root.put("allergyList", JSONArray().also { a ->
            db.allergyDao().getAll().filter { !it.isPrivate }.sortedByDescending { it.date }.take(6).forEach { l ->
                day(l.date)?.let { a.put(item(dayLabel(it), AllergyLogic.summary(l).ifBlank { "过敏记录" })) }
            }
        })
        if (on(Modules.MED)) root.put("medList", JSONArray().also { a ->
            db.medDao().getMeds().filter { it.active && !it.isPrivate }.flatMap { m -> MedLogic.dosesOn(m, today).map { it to m } }
                .sortedBy { it.first.time }.take(6).forEach { (d, m) ->
                    val amt = if (d.amount % 1.0 == 0.0) d.amount.toLong().toString() else d.amount.toString()
                    a.put(item(d.time, "${d.medName} $amt${m.unit}"))
                }
        })
        if (on(Modules.CYCLE)) root.put("cycleList", JSONArray().also { a ->
            val days = db.cycleDao().getAll(CycleProfiles.MAIN)
            if (days.isNotEmpty()) {
                val ps = CycleLogic.periods(days)
                val st = CycleLogic.stats(ps, Prefs.cycleDefaultLength(ctx), Prefs.cycleDefaultPeriod(ctx))
                a.put(item("今天", CycleLogic.todayText(CycleLogic.predict(ps, st, today), today, Prefs.cycleBirthControl(ctx))))
            }
        })
        if (on(Modules.PHRASE)) root.put("phraseList", JSONArray().also { a ->
            db.phraseDao().getAll().filter { !it.isPrivate }.take(6).forEach { p -> a.put(item(p.category.ifBlank { "常用语" }, p.title)) }
        })
        return root.toString()
    }

    /** 重新生成汇总、写入、并刷新桌面上所有已添加的小组件 */
    suspend fun sync(ctx: Context) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val json = buildJson(app)
        app.getSharedPreferences(LiWidgetProvider.PREFS, Context.MODE_PRIVATE).edit()
            .putString(LiWidgetProvider.KEY_DATA, json)
            .putBoolean(KEY_NATIVE_OWNER, true)
            .apply()
        LiWidgetProvider.refreshAll(app)
        LiWidgetProvider.scheduleMidnight(app)
    }

    /** 给午夜自动刷新用（在后台线程里调）：只有原生版接管过小组件数据，才用原生数据重算 */
    @JvmStatic
    fun syncBlockingIfOwner(ctx: Context) {
        val app = ctx.applicationContext
        val owner = app.getSharedPreferences(LiWidgetProvider.PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NATIVE_OWNER, false)
        if (!owner) return
        try {
            runBlocking { sync(app) }
        } catch (e: Exception) {
            // 重算失败就保持旧数据，让调用方继续按已有数据刷新
        }
    }

    /**
     * 只要生日 / 提醒 / 闹钟 / 日记 / 节假日数据有任何变化，就在半秒后自动重算并刷新小组件。
     * 在首页启动时调用一次即可，不用去每个保存/删除的地方单独通知。
     */
    @OptIn(FlowPreview::class)
    fun keepInSync(scope: CoroutineScope, ctx: Context): Job {
        val app = ctx.applicationContext
        val db = AppDatabase.get(app)
        return scope.launch {
            combine(
                db.birthdayDao().observeAll(),
                db.reminderDao().observeAll(),
                db.alarmDao().observeAll(),
                db.diaryDao().observeAll(),
                HolidayRepository.index
            ) { _, _, _, _, _ -> Unit }
                .let { base ->
                    combine(base, db.fitnessDao().observeAll(), db.allergyDao().observeAll(), db.medDao().observeMeds(), db.cycleDao().observeAll(CycleProfiles.MAIN)) { _, _, _, _, _ -> Unit }
                }
                .let { base -> combine(base, db.phraseDao().observeAll()) { _, _ -> Unit } }
                .debounce(500)
                .collect { sync(app) }
        }
    }
}
