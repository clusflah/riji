package app.birthdayreminder.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.notify.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BirthdayViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    /** 普通页面用：不含私密 */
    val all: StateFlow<List<Birthday>> =
        db.birthdayDao().observeAll().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 隐私空间用：只有私密 */
    val privateAll: StateFlow<List<Birthday>> =
        db.birthdayDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun get(id: String): Birthday? = db.birthdayDao().getById(id)

    fun save(b: Birthday, onDone: () -> Unit) {
        viewModelScope.launch {
            db.birthdayDao().upsert(b)
            ReminderScheduler.reschedule(getApplication())
            onDone()
        }
    }

    fun delete(b: Birthday, onDone: () -> Unit) {
        viewModelScope.launch {
            db.birthdayDao().delete(b)
            ReminderScheduler.reschedule(getApplication())
            onDone()
        }
    }
}

class ReminderViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    /** 普通页面用：不含私密 */
    val all: StateFlow<List<Reminder>> =
        db.reminderDao().observeAll().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 隐私空间用：只有私密 */
    val privateAll: StateFlow<List<Reminder>> =
        db.reminderDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun get(id: String): Reminder? = db.reminderDao().getById(id)

    fun save(r: Reminder, onDone: () -> Unit) {
        viewModelScope.launch {
            db.reminderDao().upsert(r)
            ReminderScheduler.reschedule(getApplication())
            onDone()
        }
    }

    fun delete(r: Reminder, onDone: () -> Unit) {
        viewModelScope.launch {
            db.reminderDao().delete(r)
            ReminderScheduler.reschedule(getApplication())
            onDone()
        }
    }

    /** 无时间待办：打勾 / 取消打勾（不涉及通知） */
    fun toggleTodo(r: Reminder) {
        viewModelScope.launch {
            val done = r.done.toMutableMap()
            if (done["todo"] == true) done.remove("todo") else done["todo"] = true
            db.reminderDao().upsert(r.copy(done = done))
        }
    }
}
