package app.birthdayreminder.ui.calendar

import app.birthdayreminder.data.CycleProfiles
import app.birthdayreminder.ui.common.ModuleStore
import app.birthdayreminder.domain.AlarmTriggers
import app.birthdayreminder.data.Alarm
import app.birthdayreminder.domain.AgendaRange
import app.birthdayreminder.data.CycleDay
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.domain.Modules
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.Features
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.MedicalRecord
import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.data.DiaryEntity
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.HolidayIndex
import app.birthdayreminder.data.AllergyLog
import app.birthdayreminder.data.FitnessLog
import app.birthdayreminder.domain.AllergyLogic
import app.birthdayreminder.domain.Festivals
import app.birthdayreminder.domain.Upcoming
import app.birthdayreminder.domain.MedicalLogic
import app.birthdayreminder.domain.UpcomingItem
import app.birthdayreminder.domain.FitnessFields
import app.birthdayreminder.domain.AppSettings
import app.birthdayreminder.notify.SettingsStore
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.domain.LunarCache
import app.birthdayreminder.domain.Occurrence
import app.birthdayreminder.lunar.LunarCalendar
import app.birthdayreminder.notify.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDayUi(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val lunarLabel: String,
    val hasBirthday: Boolean,
    val hasReminder: Boolean,
    val hasDiary: Boolean,
    val holidayTag: String?,     // "休" 法定假期 / "班" 调休补班 / null 普通日
    val festivalLabel: String?,  // 节日或节气（有的话格子里代替农历显示）
    val cycleMark: CycleLogic.Mark = CycleLogic.Mark.NONE   // 生理期：经期 / 预测经期 / 排卵日 / 易孕期
)

data class BirthdayItemUi(val birthday: Birthday, val desc: String, val age: Int?)

data class ReminderItemUi(
    val reminder: Reminder,
    val done: Boolean,
    val timeLabel: String,
    val repeatLabel: String
)

private data class DataSnapshot(
    val birthdays: List<Birthday>,
    val reminders: List<Reminder>,
    val diaries: List<DiaryEntity>,
    val holidays: HolidayIndex,
    val settings: AppSettings,
    val fitness: List<FitnessLog>,
    val allergy: List<AllergyLog>,
    val records: List<MedicalRecord> = emptyList(),
    val cycle: List<CycleDay> = emptyList()
)

data class DiaryItemUi(val entity: DiaryEntity, val snippet: String, val hasImage: Boolean, val hasLocation: Boolean)

data class CalendarUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val days: List<CalendarDayUi> = emptyList(),
    val prevDays: List<CalendarDayUi> = emptyList(),   // 上个月（跟手翻页时拖出来就能看到）
    val nextDays: List<CalendarDayUi> = emptyList(),   // 下个月
    val selectedLunar: String = "",
    val selectedHoliday: String? = null,
    val weekStart: Int = 0,
    val showLunar: Boolean = true,
    val selectedFestival: String? = null,
    val selectedCycle: String? = null,          // 生理期：经期第几天 / 预测经期 / 易孕期 … + 当天记录
    val birthdays: List<BirthdayItemUi> = emptyList(),
    val reminders: List<ReminderItemUi> = emptyList(),
    val diaries: List<DiaryItemUi> = emptyList(),
    val todos: List<Reminder> = emptyList(),         // 没做完的无时间待办（主界面下方一直显示）
    val upcoming: List<UpcomingItem> = emptyList(),  // 未来 30 天摘要
    val agenda: List<UpcomingItem> = emptyList(),    // “日程”视图：从今天往后更长一段时间（滑到底会继续往后加载）
    val fitness: FitnessLog? = null,
    val fitnessSummary: String? = null,
    val allergy: AllergyLog? = null,
    val allergySummary: String? = null,
    val alarms: List<Alarm> = emptyList()           // 选中这天会响的闹钟（时间轴用）
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    private var allBirthdays: List<Birthday> = emptyList()
    private var allReminders: List<Reminder> = emptyList()
    private var allDiaries: List<DiaryEntity> = emptyList()
    private var allFitness: List<FitnessLog> = emptyList()
    private var allAllergy: List<AllergyLog> = emptyList()
    private var allRecords: List<MedicalRecord> = emptyList()
    private var allCycle: List<CycleDay> = emptyList()
    private var allAlarms: List<Alarm> = emptyList()
    private var holidays: HolidayIndex = HolidayRepository.index.value
    private var settings: AppSettings = SettingsStore.get(app)
    private var month: YearMonth = YearMonth.now()
    private var selected: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state

    init {
        rebuild()
        // 闹钟：时间轴上显示选中这天会响的闹钟（私密的不显示）
        viewModelScope.launch { db.alarmDao().observeAll().collect { l -> allAlarms = l.filter { !it.isPrivate }; rebuild() } }
        viewModelScope.launch {
            // combine 最多一次合 5 个，分两组再合起来
            val main = combine(
                db.birthdayDao().observeAll(),
                db.reminderDao().observeAll(),
                db.diaryDao().observeAll(),
                HolidayRepository.index,
                SettingsStore.flow
            ) { b, r, d, h, st -> DataSnapshot(b, r, d, h, st, emptyList(), emptyList()) }
            combine(main, db.fitnessDao().observeAll(), db.allergyDao().observeAll(), db.medDao().observeRecords(), db.cycleDao().observeAll(CycleProfiles.MAIN)) { m, f, a, r, c ->
                m.copy(fitness = f, allergy = a, records = r, cycle = c)
            }
                .collect { snap ->
                    lastSnap = snap
                    applySnap(snap)
                    holidays = snap.holidays
                    settings = snap.settings
                    rebuild()
                }
        }
    }

    fun goToToday() {
        selected = LocalDate.now()
        month = YearMonth.from(selected)
        rebuild()
    }

    fun nextMonth() { month = month.plusMonths(1); rebuild() }
    fun prevMonth() { month = month.minusMonths(1); rebuild() }

    fun select(date: LocalDate) {
        selected = date
        if (YearMonth.from(date) != month) month = YearMonth.from(date)
        rebuild()
    }

    /** 给某条提醒在选中的这一天打勾 / 取消打勾（重复提醒逐次打勾） */
    fun toggleDone(reminder: Reminder, date: LocalDate) {
        viewModelScope.launch {
            val key = date.toString()
            val newDone = reminder.done.toMutableMap()
            if (newDone[key] == true) newDone.remove(key) else newDone[key] = true
            db.reminderDao().upsert(reminder.copy(done = newDone))
            ReminderScheduler.reschedule(getApplication())
        }
    }

    private var lastSnap: DataSnapshot? = null

    /** 日程视图：往后看多少天（先 180 天，滑到底再加 180 天，最多两年）、往前看多少天（先 90 天，滑到顶再加 180 天，最多五年） */
    private var agendaDays = 180
    private var agendaPastDays = 90
    fun loadMoreAgenda() { if (agendaDays < 730) { agendaDays += 180; rebuild() } }
    fun loadEarlierAgenda() { if (agendaPastDays < 1825) { agendaPastDays += 180; rebuild() } }

    /**
     * 把数据按“私密”和“功能开关”过滤后放进来：
     * 日历上不出现私密内容（连小圆点也不画），关掉的功能也不出现。
     */
    private fun applySnap(snap: DataSnapshot) {
        val app = getApplication<Application>()
        fun on(k: String) = Prefs.moduleOn(app, k)
        allBirthdays = if (on(Modules.BIRTHDAY)) snap.birthdays.filter { !it.isPrivate } else emptyList()
        allReminders = if (on(Modules.REMINDER)) snap.reminders.filter { !it.isPrivate } else emptyList()
        allDiaries = if (on(Modules.DIARY)) snap.diaries.filter { !it.isPrivate } else emptyList()
        allFitness = if (on(Modules.FITNESS)) snap.fitness.filter { !it.isPrivate } else emptyList()
        allAllergy = if (on(Modules.ALLERGY)) snap.allergy.filter { !it.isPrivate } else emptyList()
        allRecords = if (on(Modules.MED)) snap.records.filter { !it.isPrivate } else emptyList()
        allCycle = if (on(Modules.CYCLE)) snap.cycle else emptyList()
    }

    /** 功能开关改了：按新的开关重新过滤、刷新 */
    fun refreshModules() { lastSnap?.let { applySnap(it); rebuild() } }

    /** 某个月的 6 周格子（农历、节假日、生日 / 提醒 / 日记圆点、生理期标记） */
    private fun daysFor(m: YearMonth, today: LocalDate): List<CalendarDayUi> {
        val firstOfMonth = m.atDay(1)
        // 第一格往前退几天：周日开头时周日=0…周六=6；周一开头时周一=0…周日=6
        val firstDow = (firstOfMonth.dayOfWeek.value % 7 - settings.weekStart + 7) % 7
        val gridStart = firstOfMonth.minusDays(firstDow.toLong())
        val app = getApplication<Application>()
        val cycleMarks = if (allCycle.isEmpty()) emptyMap() else CycleLogic.marks(allCycle, gridStart, gridStart.plusDays(41), today,
            Prefs.cycleBirthControl(app), Prefs.cycleDefaultLength(app), Prefs.cycleDefaultPeriod(app))
        return (0 until 42).map { i ->
            val date = gridStart.plusDays(i.toLong())
            CalendarDayUi(
                date = date,
                inCurrentMonth = date.monthValue == m.monthValue,
                isToday = date == today,
                lunarLabel = LunarCalendar.cellLabel(LunarCache.of(date)),
                hasBirthday = allBirthdays.any { Occurrence.birthdayOn(it, date) },
                hasReminder = allReminders.any { Occurrence.reminderOn(it, date) },
                hasDiary = allDiaries.any { it.date == date.toString() },
                holidayTag = when {
                    holidays.off.containsKey(date) -> "休"
                    holidays.work.containsKey(date) -> "班"
                    else -> null
                },
                festivalLabel = if (settings.showFestival) Festivals.of(date).cellText else null,
                cycleMark = cycleMarks[date] ?: CycleLogic.Mark.NONE
            )
        }
    }

    /** 跟手翻页停在哪个月，就显示哪个月（选中的日期不变） */
    fun showMonth(m: YearMonth) { if (m != month) { month = m; rebuild() } }

    /** 无时间待办打勾 */
    fun toggleTodo(r: Reminder) {
        viewModelScope.launch {
            val done = r.done.toMutableMap()
            if (done[Occurrence.TODO_DONE_KEY] == true) done.remove(Occurrence.TODO_DONE_KEY) else done[Occurrence.TODO_DONE_KEY] = true
            db.reminderDao().upsert(r.copy(done = done))
        }
    }

    private fun rebuild() {
        val today = LocalDate.now()
        val days = daysFor(month, today)
        // 跟手翻页：相邻两个月也备好，拖动时就能看到完整内容
        val prevDays = daysFor(month.minusMonths(1), today)
        val nextDays = daysFor(month.plusMonths(1), today)
        val app = getApplication<Application>()
        val bc = Prefs.cycleBirthControl(app)

        val lunar = LunarCache.of(selected)
        val off = holidays.off[selected]
        val selectedHoliday = when {
            off != null -> off.name + (if (off.total > 1) "（放假第${off.index}天，共${off.total}天）" else "（放假）")
            holidays.work.containsKey(selected) -> "调休补班（" + holidays.work[selected] + "）"
            else -> null
        }
        val selectedLunar = "农历 " + LunarCalendar.monthLabel(lunar.month, lunar.isLeap) + LunarCalendar.dayLabel(lunar.day)

        val bItems = allBirthdays
            .filter { Occurrence.birthdayOn(it, selected) }
            .map { BirthdayItemUi(it, Occurrence.birthdayDesc(it), Occurrence.ageAt(it, selected)) }

        val rItems = allReminders
            .filter { Occurrence.reminderOn(it, selected) }
            .sortedBy { it.time }
            .map {
                ReminderItemUi(
                    reminder = it,
                    done = it.done[selected.toString()] == true,
                    timeLabel = it.time.ifBlank { "全天" },
                    repeatLabel = Occurrence.repeatLabel(it)
                )
            }

        val dItems = allDiaries
            .filter { it.date == selected.toString() }
            .map { e ->
                val blocks = BlockConverter.fromJson(e.content)
                DiaryItemUi(
                    entity = e,
                    snippet = DiaryText.snippet(blocks).ifBlank { "（这篇日记只有图片）" },
                    hasImage = DiaryText.imageCount(blocks) > 0,
                    hasLocation = e.locationAddress != null
                )
            }

        val key = selected.toString()
        val fit = allFitness.firstOrNull { it.date == key }
        val alg = if (Features.allergy) allAllergy.firstOrNull { it.date == key } else null   // 无过敏记录版不显示
        val todos = allReminders.filter { Occurrence.isTodo(it) && !Occurrence.todoDone(it) }.sortedBy { it.title }
        // 未来 30 天里也列出复诊
        val followUps = MedicalLogic.upcomingFollowUps(allRecords, today, Upcoming.DAYS.toLong()).map { it.date to MedicalLogic.followUpText(it.record) }
        val upcoming = Upcoming.build(allBirthdays, allReminders, holidays, today, followUps = followUps)
        // 日程视图：从过去一段到将来一段，每一次发生的事；过去的日子还有日记和就医记录
        val aFrom = today.minusDays(agendaPastDays.toLong()); val aTo = today.plusDays(agendaDays.toLong())
        val agendaFollow = MedicalLogic.upcomingFollowUps(allRecords, today, agendaDays.toLong()).map { it.date to MedicalLogic.followUpText(it.record) }
        val fromS = aFrom.toString(); val toS = aTo.toString()
        val agendaDiaries = allDiaries.filter { it.date in fromS..toS }.mapNotNull { e ->
            val d = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
            AgendaRange.Note(d, DiaryText.snippet(BlockConverter.fromJson(e.content)).ifBlank { "日记（只有图片）" }.take(60), e.id)
        }
        val agendaVisits = allRecords.filter { it.date in fromS..toS }.mapNotNull { r ->
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@mapNotNull null
            AgendaRange.Note(d, r.illness, r.id)
        }
        val agenda = AgendaRange.build(allBirthdays, allReminders, holidays, aFrom, aTo,
            followUps = agendaFollow, diaries = agendaDiaries, visits = agendaVisits)

        _state.update {
            it.copy(
                selectedFestival = if (settings.showFestival) Festivals.of(selected).panelText else null,
                selectedCycle = if (allCycle.isEmpty()) null else run {
                    val mk = CycleLogic.marks(allCycle, selected, selected, today, bc, Prefs.cycleDefaultLength(app), Prefs.cycleDefaultPeriod(app))[selected]
                    val rec = allCycle.firstOrNull { c -> c.date == key }?.let { c -> CycleLogic.daySummary(c) }?.takeIf { s -> s.isNotBlank() }
                    listOfNotNull(when (mk) {
                        CycleLogic.Mark.PERIOD -> "🩸 经期"
                        CycleLogic.Mark.PREDICTED -> "🩸 预计经期"
                        CycleLogic.Mark.OVULATION -> "🌱 可能是排卵日"
                        CycleLogic.Mark.FERTILE -> "🌱 易孕期"
                        else -> null
                    }, rec).joinToString(" · ").ifEmpty { null }
                },
                todos = todos,
                upcoming = upcoming,
                agenda = agenda,
                alarms = if (Modules.isOn(ModuleStore.disabled(getApplication()), Modules.ALARM, Features.allergy)) allAlarms.filter { a -> a.enabled && AlarmTriggers.rings(a, selected) { d -> holidays.isWorkday(d) } }.sortedBy { a -> a.time } else emptyList(),
                fitness = fit,
                fitnessSummary = fit?.let { f -> FitnessFields.summary(f) },
                allergy = alg,
                allergySummary = alg?.let { a -> AllergyLogic.summary(a) },
                yearMonth = month,
                selected = selected,
                days = days,
                prevDays = prevDays,
                nextDays = nextDays,
                selectedLunar = selectedLunar,
                selectedHoliday = selectedHoliday,
                weekStart = settings.weekStart,
                showLunar = settings.showLunar,
                birthdays = bItems,
                reminders = rItems,
                diaries = dItems
            )
        }
    }
}
