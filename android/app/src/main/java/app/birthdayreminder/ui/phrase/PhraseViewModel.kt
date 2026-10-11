package app.birthdayreminder.ui.phrase

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.Phrase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhraseViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    /** 全部（含私密）：编辑页里列出用过的分类用 */
    val everything: StateFlow<List<Phrase>> =
        db.phraseDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 普通页面用：不含私密 */
    val all: StateFlow<List<Phrase>> =
        db.phraseDao().observeAll().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 隐私空间用：只有私密 */
    val privateAll: StateFlow<List<Phrase>> =
        db.phraseDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun get(id: String): Phrase? = db.phraseDao().getById(id)

    fun save(p: Phrase, onDone: () -> Unit) {
        viewModelScope.launch { db.phraseDao().upsert(p.copy(updatedAt = System.currentTimeMillis())); onDone() }
    }

    fun delete(p: Phrase, onDone: () -> Unit) {
        viewModelScope.launch { db.phraseDao().delete(p); onDone() }
    }
}
