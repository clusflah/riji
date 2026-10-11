package app.birthdayreminder.ui.med

import kotlinx.coroutines.flow.map
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.MedLog
import app.birthdayreminder.data.Medication
import app.birthdayreminder.data.MedicalRecord
import app.birthdayreminder.data.ImageStorage
import app.birthdayreminder.domain.MedicalLogic
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.notify.MedActions
import app.birthdayreminder.notify.MedScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MedViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).medDao()
    // 私密的药品、就医记录只在隐私空间里（提醒照常）
    val meds: StateFlow<List<Medication>> = dao.observeMeds().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val privateMeds: StateFlow<List<Medication>> = dao.observeMeds().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs: StateFlow<List<MedLog>> = dao.observeLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records: StateFlow<List<MedicalRecord>> = dao.observeRecords().map { l -> l.filter { !it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val privateRecords: StateFlow<List<MedicalRecord>> = dao.observeRecords().map { l -> l.filter { it.isPrivate } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------------- 就医记录 ----------------
    suspend fun getRecord(id: String): MedicalRecord? = dao.getRecord(id)

    fun saveRecord(r: MedicalRecord, removedImages: List<String>, onDone: () -> Unit) = viewModelScope.launch {
        dao.upsertRecord(r.copy(updatedAt = System.currentTimeMillis()))
        removedImages.forEach { ImageStorage.delete(getApplication(), it) }
        MedScheduler.reschedule(getApplication())      // 复诊提醒跟着变
        onDone()
    }

    fun deleteRecord(r: MedicalRecord, onDone: () -> Unit) = viewModelScope.launch {
        dao.deleteRecord(r.id)
        MedicalLogic.listFromJson(r.images).forEach { ImageStorage.delete(getApplication(), it) }
        MedScheduler.reschedule(getApplication())
        onDone()
    }

    /** 选的照片复制进 App（和日记图片放在一起）；返回相对路径，失败返回 null */
    suspend fun addImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        ImageStorage.saveFromUri(app, uri, app.contentResolver.getType(uri))
    }

    /** 编辑时新加了照片又取消：把这次新加的删掉 */
    fun discardImages(paths: List<String>) = viewModelScope.launch(Dispatchers.IO) { paths.forEach { ImageStorage.delete(getApplication(), it) } }

    suspend fun get(id: String): Medication? = dao.getMed(id)

    fun save(m: Medication, onDone: () -> Unit) = viewModelScope.launch {
        dao.upsertMed(m.copy(updatedAt = System.currentTimeMillis()))
        MedScheduler.reschedule(getApplication())
        onDone()
    }

    /** 删除药品（服药记录保留，历史里还能看到） */
    fun delete(id: String, onDone: () -> Unit) = viewModelScope.launch {
        dao.deleteMed(id)
        MedScheduler.reschedule(getApplication())
        onDone()
    }

    fun take(d: MedLogic.Dose) = viewModelScope.launch { MedActions.take(getApplication(), d.medId, d.date.toString(), d.time, d.amount) }
    fun skip(d: MedLogic.Dose) = viewModelScope.launch { MedActions.skip(getApplication(), d.medId, d.date.toString(), d.time, d.amount) }
    fun undo(l: MedLog) = viewModelScope.launch { MedActions.undo(getApplication(), l) }

    /** 临时加一次：记在今天、现在的时间 */
    fun extra(m: Medication, amount: Double) = viewModelScope.launch {
        val now = LocalTime.now()
        MedActions.take(getApplication(), m.id, LocalDate.now().toString(), "%02d:%02d".format(now.hour, now.minute), amount, scheduled = false)
    }

    /** 导出最近 days 天的服药记录为 CSV；返回 null 表示成功 */
    suspend fun exportCsv(uri: Uri, days: Long = 90): String? = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val meds = dao.getMeds().filter { !it.isPrivate }
            val rows = MedLogic.history(meds, dao.getLogs(), today.minusDays(days - 1), today, LocalDateTime.now())
            val from = today.minusDays(days - 1).toString()
            val visits = dao.getRecords().filter { it.date >= from && !it.isPrivate }
            // 两张表：服药记录，空一行，就医记录
            val text = MedLogic.csv(rows, ZoneId.systemDefault()) + "\n" + MedicalLogic.csvSection(visits, meds)
            getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: return@withContext "无法写入所选位置"
            null
        } catch (e: Exception) { "导出失败：" + (e.message ?: "未知错误") }
    }
}
