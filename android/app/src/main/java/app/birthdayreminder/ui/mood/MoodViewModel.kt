package app.birthdayreminder.ui.mood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.MoodLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class MoodViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val all: StateFlow<List<MoodLog>> = db.moodDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 一点即记：记在现在这一刻，返回这条的 id（撤销用） */
    fun record(level: Int): String {
        val id = UUID.randomUUID().toString()
        viewModelScope.launch { db.moodDao().upsert(MoodLog(id, LocalDate.now().toString(), System.currentTimeMillis(), level)) }
        return id
    }

    fun delete(id: String) = viewModelScope.launch { db.moodDao().delete(id) }
}
