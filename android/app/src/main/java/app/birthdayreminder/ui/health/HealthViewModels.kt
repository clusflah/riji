package app.birthdayreminder.ui.health

import kotlinx.coroutines.flow.map
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AllergyLog
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.FitnessLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FitnessViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    /** 普通列表、统计只用不私密的；私密的只在隐私空间里 */
    val all: StateFlow<List<FitnessLog>> =
        db.fitnessDao().observeAll().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val privateAll: StateFlow<List<FitnessLog>> =
        db.fitnessDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun get(date: String): FitnessLog? = db.fitnessDao().getByDate(date)

    /** 同一天只有一条：保存时如果换了日期，原来那天的记录删掉（相当于“挪到新日期”） */
    fun save(log: FitnessLog, originalDate: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            if (originalDate != null && originalDate != log.date) db.fitnessDao().deleteByDate(originalDate)
            db.fitnessDao().upsert(log.copy(updatedAt = System.currentTimeMillis()))
            onDone()
        }
    }

    fun delete(date: String, onDone: () -> Unit) {
        viewModelScope.launch { db.fitnessDao().deleteByDate(date); onDone() }
    }
}

class AllergyViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val all: StateFlow<List<AllergyLog>> =
        db.allergyDao().observeAll().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val privateAll: StateFlow<List<AllergyLog>> =
        db.allergyDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun get(date: String): AllergyLog? = db.allergyDao().getByDate(date)

    fun save(log: AllergyLog, originalDate: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            if (originalDate != null && originalDate != log.date) db.allergyDao().deleteByDate(originalDate)
            db.allergyDao().upsert(log.copy(updatedAt = System.currentTimeMillis()))
            onDone()
        }
    }

    fun delete(date: String, onDone: () -> Unit) {
        viewModelScope.launch { db.allergyDao().deleteByDate(date); onDone() }
    }
}
