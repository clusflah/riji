package app.birthdayreminder.ui.study

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.data.StudyLog
import app.birthdayreminder.data.StudyPreset
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.domain.StudyItem
import app.birthdayreminder.domain.StudyLogic
import app.birthdayreminder.notify.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 首页 / 数据页的“+”要新建一条学习记录时，在这里放下日期；学习页面看到了就打开编辑框 */
object StudyUi {
    val newRequest = mutableStateOf<LocalDate?>(null)
    fun requestNew(date: LocalDate) { newRequest.value = date }
    /** 首页时间轴点了某条学习记录：学习页面打开它的编辑框 */
    val editRequest = mutableStateOf<String?>(null)
    fun requestEdit(id: String) { editRequest.value = id }
}

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val logs: StateFlow<List<StudyLog>> = db.studyDao().observeLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presets: StateFlow<List<StudyPreset>> = db.studyDao().observePresets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** 学习提醒：就是普通的提醒（每天重复），id 以 study- 开头，这样能在学习页里单独列出来 */
    val reminders: StateFlow<List<Reminder>> = db.reminderDao().observeAll().map { l -> l.filter { it.id.startsWith(REMINDER_PREFIX) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(log: StudyLog) = viewModelScope.launch { db.studyDao().upsertLog(log.copy(updatedAt = System.currentTimeMillis())) }
    fun delete(id: String) = viewModelScope.launch { db.studyDao().deleteLog(id) }

    /** 存成常用项目：同名的就更新单位和数量，不重复添加 */
    fun savePreset(item: StudyItem) = viewModelScope.launch {
        val all = db.studyDao().getPresets()
        val old = all.firstOrNull { it.name == item.name.trim() }
        db.studyDao().upsertPreset(StudyPreset(old?.id ?: UUID.randomUUID().toString(), item.name.trim(), item.unit.trim(), item.amount,
            old?.sort ?: ((all.maxOfOrNull { it.sort } ?: 0) + 1)))
    }
    fun deletePreset(id: String) = viewModelScope.launch { db.studyDao().deletePreset(id) }

    fun addReminder(title: String, time: String) = viewModelScope.launch {
        db.reminderDao().upsert(Reminder(REMINDER_PREFIX + UUID.randomUUID(), title.trim().ifEmpty { "该学习了" }, LocalDate.now().toString(), time, "daily"))
        ReminderScheduler.reschedule(getApplication())
    }
    fun deleteReminder(r: Reminder) = viewModelScope.launch {
        db.reminderDao().delete(r)
        ReminderScheduler.reschedule(getApplication())
    }

    // ---------------- 从日记导入 ----------------
    data class ImportCandidate(val date: String, val text: String, val items: List<StudyItem>)

    /** 正在识别 / 识别结果（null = 还没开始） */
    val importCandidates = mutableStateOf<List<ImportCandidate>?>(null)

    /** 找出写着“名称 数字”的日记（私密日记不碰）；这一天已经有同样内容的学习记录就跳过 */
    fun scanDiary() = viewModelScope.launch {
        val existing = db.studyDao().getLogs().map { it.date to StudyLogic.summary(StudyLogic.itemsFromJson(it.items)) }.toSet()
        importCandidates.value = db.diaryDao().getAll().filter { !it.isPrivate }.mapNotNull { e ->
            val text = DiaryText.plainText(BlockConverter.fromJson(e.content)).trim()
            val items = StudyLogic.parseDiaryText(text)
            if (items.isEmpty() || (e.date to StudyLogic.summary(items)) in existing) null else ImportCandidate(e.date, text, items)
        }.sortedByDescending { it.date }
    }

    /** 导入选中的：每篇日记变成一条学习记录（时长不知道，记 0），出现过的内容名称顺便存成常用项目；日记本身不动 */
    fun importSelected(list: List<ImportCandidate>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.studyDao().upsertLogs(list.map { c -> StudyLog(UUID.randomUUID().toString(), c.date, StudyLogic.itemsToJson(c.items), note = "从日记导入", updatedAt = now) })
        val have = db.studyDao().getPresets().map { it.name }.toMutableSet()
        var sort = (db.studyDao().getPresets().maxOfOrNull { it.sort } ?: 0)
        for (i in list.flatMap { it.items }) if (have.add(i.name)) db.studyDao().upsertPreset(StudyPreset(UUID.randomUUID().toString(), i.name, i.unit, null, ++sort))
        importCandidates.value = null
    }

    companion object { const val REMINDER_PREFIX = "study-" }
}
