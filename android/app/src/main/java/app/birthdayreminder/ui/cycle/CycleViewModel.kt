package app.birthdayreminder.ui.cycle

import kotlinx.coroutines.flow.map
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.CycleDay
import app.birthdayreminder.data.CycleProfiles
import app.birthdayreminder.data.MedLog
import app.birthdayreminder.data.Medication
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.notify.CycleScheduler
import app.birthdayreminder.notify.MedActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 生理期页面的数据。profile 决定用哪一份：外面（CycleProfiles.MAIN）或隐私空间（CycleProfiles.PRIVATE），两份互不影响。
 * 隐私空间那份的避孕药也只从私密药品里选。
 */
class CycleViewModel(app: Application, val profile: String) : AndroidViewModel(app) {
    // 系统默认的 ViewModel 工厂只认 (Application) 这个构造函数：外面的页面用它
    constructor(app: Application) : this(app, CycleProfiles.MAIN)

    private val db = AppDatabase.get(app)
    private val isPrivate = profile != CycleProfiles.MAIN
    val days: StateFlow<List<CycleDay>> = db.cycleDao().observeAll(profile).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val meds: StateFlow<List<Medication>> = db.medDao().observeMeds().map { l -> l.filter { it.isPrivate == isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs: StateFlow<List<MedLog>> = db.medDao().observeLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 保存一天的记录；什么都没填就删掉这天。保存后重排生理期提醒（预测变了） */
    fun saveDay(d: CycleDay) = viewModelScope.launch {
        val empty = d.flow == 0 && d.symptoms.isBlank() && d.mood.isBlank() && (d.discharge.isBlank() || d.discharge == "none") && d.note.isBlank()
        if (empty) db.cycleDao().delete(profile, d.date) else db.cycleDao().upsert(d.copy(profile = profile, updatedAt = System.currentTimeMillis()))
        if (!isPrivate) CycleScheduler.reschedule(getApplication())
    }

    /** “今天来了”：今天记成流量中等（已经记了经期的不动） */
    fun periodStartedToday() = viewModelScope.launch {
        val today = LocalDate.now().toString()
        val old = db.cycleDao().get(profile, today)
        if (old == null || old.flow < CycleLogic.PERIOD_FLOW) db.cycleDao().upsert((old ?: CycleDay(today, profile = profile)).copy(flow = 3, updatedAt = System.currentTimeMillis()))
        if (!isPrivate) CycleScheduler.reschedule(getApplication())
    }

    // ---------------- 避孕药（用“用药就医”的数据） ----------------
    private fun firstTime(m: Medication) = MedLogic.planFromJson(m.plan).times.first()

    fun pillTake(m: Medication, date: LocalDate) = viewModelScope.launch {
        val t = firstTime(m); MedActions.take(getApplication(), m.id, date.toString(), t.time, t.amount)
    }

    fun pillTakeAt(m: Medication, date: LocalDate, actionAt: Long) = viewModelScope.launch {
        val t = firstTime(m); MedActions.takeAt(getApplication(), m.id, date.toString(), t.time, t.amount, actionAt)
    }

    fun pillMissed(m: Medication, date: LocalDate) = viewModelScope.launch {
        val t = firstTime(m); MedActions.skip(getApplication(), m.id, date.toString(), t.time, t.amount)
    }

    fun pillClear(m: Medication, date: LocalDate) = viewModelScope.launch {
        val id = MedLogic.doseKey(m.id, date.toString(), firstTime(m).time)
        db.medDao().getLogs().firstOrNull { it.id == id }?.let { MedActions.undo(getApplication(), it) }
    }

    suspend fun exportCsv(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val all = db.cycleDao().getAll(profile)
            getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use {
                it.write(CycleLogic.csv(all, CycleLogic.periods(all)).toByteArray(Charsets.UTF_8))
            } ?: return@withContext "无法写入所选位置"
            null
        } catch (e: Exception) { "导出失败：" + (e.message ?: "未知错误") }
    }
}
