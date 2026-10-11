package app.birthdayreminder.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.DiaryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DiaryListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    /** 普通页面用：不含私密 */
    val all: StateFlow<List<DiaryEntity>> =
        db.diaryDao().observeAll().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 隐私空间用：只有私密 */
    val privateAll: StateFlow<List<DiaryEntity>> =
        db.diaryDao().observeAll().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
