package app.birthdayreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Birthday::class, Reminder::class, DiaryEntity::class, Alarm::class, Phrase::class, AllergyLog::class, FitnessLog::class, Medication::class, MedLog::class, MedicalRecord::class, CycleDay::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun birthdayDao(): BirthdayDao
    abstract fun reminderDao(): ReminderDao
    abstract fun diaryDao(): DiaryDao
    abstract fun alarmDao(): AlarmDao
    abstract fun phraseDao(): PhraseDao
    abstract fun allergyDao(): AllergyDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun medDao(): MedDao
    abstract fun cycleDao(): CycleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** 1 -> 2：新增闹钟表。生日、提醒、日记里已有的数据原样保留。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarms` (" +
                        "`id` TEXT NOT NULL, `time` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`repeat` TEXT NOT NULL, `days` TEXT NOT NULL, `snooze` INTEGER NOT NULL, " +
                        "`vibrate` INTEGER NOT NULL, `soundPath` TEXT, `soundName` TEXT, " +
                        "`enabled` INTEGER NOT NULL, `onceAt` INTEGER, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 2 -> 3：新增常用语表，其余数据原样保留。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `phrases` (" +
                        "`id` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 3 -> 4：新增过敏记录表和健身数据表，其余数据原样保留。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allergy_log` (" +
                        "`date` TEXT NOT NULL, `eye` INTEGER NOT NULL, `nose` INTEGER NOT NULL, `cough` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fitness_log` (" +
                        "`date` TEXT NOT NULL, `metrics` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** 4 -> 5：日记、提醒、生日、常用语各加一个“私密”标记，已有的数据都默认不私密。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (t in listOf("birthdays", "reminders", "diary", "phrases")) {
                    db.execSQL("ALTER TABLE `$t` ADD COLUMN `isPrivate` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        /** 5 -> 6：新增药品表和服药记录表（药物追踪与提醒），其余数据原样保留。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medications` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `note` TEXT NOT NULL, `plan` TEXT NOT NULL, " +
                        "`stock` REAL, `unit` TEXT NOT NULL, `lowStock` REAL, `expiry` TEXT, `active` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `med_logs` (" +
                        "`id` TEXT NOT NULL, `medId` TEXT NOT NULL, `medName` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, `status` TEXT NOT NULL, `actionAt` INTEGER NOT NULL, `scheduled` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 6 -> 7：新增生病就医记录表 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medical_records` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `illness` TEXT NOT NULL, `symptoms` TEXT NOT NULL, `hospital` TEXT NOT NULL, " +
                        "`department` TEXT NOT NULL, `doctor` TEXT NOT NULL, `advice` TEXT NOT NULL, `followUp` TEXT, `medIds` TEXT NOT NULL, " +
                        "`images` TEXT NOT NULL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 7 -> 8：新增生理期每日记录表 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cycle_days` (" +
                        "`date` TEXT NOT NULL, `flow` INTEGER NOT NULL, `symptoms` TEXT NOT NULL, `mood` TEXT NOT NULL, " +
                        "`discharge` TEXT NOT NULL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** 8 -> 9：健身、过敏、闹钟、药品、就医记录也能设为私密（旧数据都不私密） */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (t in listOf("fitness_log", "allergy_log", "alarms", "medications", "medical_records")) {
                    db.execSQL("ALTER TABLE `$t` ADD COLUMN `isPrivate` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "li-native.db" // 特意用新文件名，不会和 App 现有的 localStorage/JSON 数据冲突或互相覆盖
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { instance = it }
            }
    }
}
