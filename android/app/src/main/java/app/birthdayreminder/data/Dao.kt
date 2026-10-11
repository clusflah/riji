package app.birthdayreminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays ORDER BY month, day")
    fun observeAll(): Flow<List<Birthday>>

    @Query("SELECT * FROM birthdays")
    suspend fun getAll(): List<Birthday>

    @Query("SELECT * FROM birthdays WHERE id = :id")
    suspend fun getById(id: String): Birthday?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Birthday>)

    @Query("DELETE FROM birthdays")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Birthday)

    @Delete
    suspend fun delete(item: Birthday)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY date, time")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Reminder>)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Reminder)

    @Delete
    suspend fun delete(item: Reminder)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diary")
    suspend fun getAll(): List<DiaryEntity>

    @Query("SELECT * FROM diary WHERE date = :date")
    suspend fun getByDate(date: String): List<DiaryEntity>

    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun getById(id: String): DiaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DiaryEntity>)

    @Query("DELETE FROM diary")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DiaryEntity)

    @Delete
    suspend fun delete(item: DiaryEntity)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY time")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: String): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Alarm>)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Alarm)

    @Delete
    suspend fun delete(item: Alarm)
}

@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases")
    fun observeAll(): Flow<List<Phrase>>

    @Query("SELECT * FROM phrases")
    suspend fun getAll(): List<Phrase>

    @Query("SELECT * FROM phrases WHERE id = :id")
    suspend fun getById(id: String): Phrase?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Phrase)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Phrase>)

    @Query("DELETE FROM phrases")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(item: Phrase)
}

@Dao
interface AllergyDao {
    @Query("SELECT * FROM allergy_log ORDER BY date DESC")
    fun observeAll(): Flow<List<AllergyLog>>

    @Query("SELECT * FROM allergy_log")
    suspend fun getAll(): List<AllergyLog>

    @Query("SELECT * FROM allergy_log WHERE date = :date")
    suspend fun getByDate(date: String): AllergyLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AllergyLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AllergyLog>)

    @Query("DELETE FROM allergy_log WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM allergy_log")
    suspend fun deleteAll()
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM fitness_log ORDER BY date DESC")
    fun observeAll(): Flow<List<FitnessLog>>

    @Query("SELECT * FROM fitness_log")
    suspend fun getAll(): List<FitnessLog>

    @Query("SELECT * FROM fitness_log WHERE date = :date")
    suspend fun getByDate(date: String): FitnessLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FitnessLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FitnessLog>)

    @Query("DELETE FROM fitness_log WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM fitness_log")
    suspend fun deleteAll()
}

@Dao
interface MedDao {
    @Query("SELECT * FROM medications ORDER BY active DESC, name")
    fun observeMeds(): Flow<List<Medication>>

    @Query("SELECT * FROM medications")
    suspend fun getMeds(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMed(id: String): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMed(m: Medication)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeds(list: List<Medication>)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMed(id: String)

    @Query("DELETE FROM medications")
    suspend fun deleteAllMeds()

    @Query("SELECT * FROM med_logs ORDER BY date DESC, time DESC")
    fun observeLogs(): Flow<List<MedLog>>

    @Query("SELECT * FROM med_logs")
    suspend fun getLogs(): List<MedLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(l: MedLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(list: List<MedLog>)

    @Query("DELETE FROM med_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("DELETE FROM med_logs")
    suspend fun deleteAllLogs()

    // ---------------- 就医记录 ----------------
    @Query("SELECT * FROM medical_records ORDER BY date DESC, updatedAt DESC")
    fun observeRecords(): Flow<List<MedicalRecord>>

    @Query("SELECT * FROM medical_records")
    suspend fun getRecords(): List<MedicalRecord>

    @Query("SELECT * FROM medical_records WHERE id = :id")
    suspend fun getRecord(id: String): MedicalRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(r: MedicalRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(list: List<MedicalRecord>)

    @Query("DELETE FROM medical_records WHERE id = :id")
    suspend fun deleteRecord(id: String)

    @Query("DELETE FROM medical_records")
    suspend fun deleteAllRecords()
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycle_days ORDER BY date DESC")
    fun observeAll(): Flow<List<CycleDay>>

    @Query("SELECT * FROM cycle_days")
    suspend fun getAll(): List<CycleDay>

    @Query("SELECT * FROM cycle_days WHERE date = :date")
    suspend fun get(date: String): CycleDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(d: CycleDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<CycleDay>)

    @Query("DELETE FROM cycle_days WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM cycle_days")
    suspend fun deleteAll()
}
