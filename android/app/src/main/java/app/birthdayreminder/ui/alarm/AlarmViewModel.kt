package app.birthdayreminder.ui.alarm

import kotlinx.coroutines.flow.map
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.AlarmEngine
import app.birthdayreminder.data.Alarm
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.AlarmTriggers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class AlarmRow(val alarm: Alarm, val nextText: String?)

class AlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val zone = ZoneId.systemDefault()

    /** 闹钟列表 + 每个闹钟“下一次响铃”的文字（节假日数据更新后也会自动刷新） */
    val rows: StateFlow<List<AlarmRow>> =
        combine(db.alarmDao().observeAll(), HolidayRepository.index) { alarms, holidays ->
            val now = System.currentTimeMillis()
            val today = LocalDate.now(zone)
            alarms.filter { !it.isPrivate }.map { a ->      // 私密闹钟照常响，只是只在隐私空间里列出
                val next = if (a.enabled) AlarmTriggers.triggersFor(a, now, zone, today, holidays::isWorkday).firstOrNull() else null
                AlarmRow(a, next?.let { AlarmTriggers.whenText(it, zone, today) })
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privateAlarms: StateFlow<List<Alarm>> =
        db.alarmDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun get(id: String): Alarm? = db.alarmDao().getById(id)

    fun save(a: Alarm, onDone: () -> Unit) {
        viewModelScope.launch {
            db.alarmDao().upsert(a)
            AlarmEngine.sync(getApplication())
            onDone()
        }
    }

    fun delete(a: Alarm, onDone: () -> Unit) {
        viewModelScope.launch {
            db.alarmDao().delete(a)
            AlarmEngine.deleteSound(getApplication(), a.soundPath)
            AlarmEngine.sync(getApplication())
            onDone()
        }
    }

    fun toggle(a: Alarm) {
        viewModelScope.launch {
            val on = !a.enabled
            // 单次闹钟重新打开时，要重新算一个下一次的时间（和旧版 toggleAlarm 一致）
            val updated = if (on && a.repeat == "once") {
                a.copy(enabled = true, onceAt = AlarmTriggers.nextOnceAt(a.time, System.currentTimeMillis(), zone))
            } else {
                a.copy(enabled = on)
            }
            db.alarmDao().upsert(updated)
            AlarmEngine.sync(getApplication())
        }
    }
}
