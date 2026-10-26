package app.birthdayreminder.data

import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.io.OutputStream
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import app.birthdayreminder.ui.common.ModuleStore
import app.birthdayreminder.domain.BackupArchive
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.notify.CycleScheduler
import app.birthdayreminder.domain.CycleSettings
import app.birthdayreminder.notify.MedScheduler
import app.birthdayreminder.ui.health.FitnessFieldStore
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import app.birthdayreminder.AlarmEngine
import app.birthdayreminder.domain.BackupCodec
import app.birthdayreminder.domain.BackupData
import app.birthdayreminder.domain.BackupDiary
import app.birthdayreminder.domain.DiaryBackupText
import app.birthdayreminder.notify.ReminderScheduler
import app.birthdayreminder.notify.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * 备份和恢复的落地部分（读写数据库、读写文件）。文件格式、校验规则都在 BackupCodec 里。
 * 恢复的规则和旧版一致：生日、提醒、设置一定替换；闹钟、日记、常用语只有备份里带了才替换。
 */
object BackupRunner {

    /** 生成完整的备份内容（JSON 文本）：手动导出和自动备份共用 */
    suspend fun buildJson(ctx: Context): String = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val db = AppDatabase.get(app)
        val diary = db.diaryDao().getAll().map { e ->
            BackupDiary(
                e.id, e.date, DiaryBackupText.blocksToText(BlockConverter.fromJson(e.content)), e.updatedAt,
                if (e.locationLat != null && e.locationLng != null) DiaryLocation(e.locationLat, e.locationLng, e.locationAddress ?: "") else null,
                e.isPrivate
            )
        }
        BackupCodec.export(
            db.birthdayDao().getAll(), db.reminderDao().getAll(), db.alarmDao().getAll(),
            diary, db.phraseDao().getAll(), SettingsStore.get(app), Instant.now().toString(),
            db.fitnessDao().getAll(), db.allergyDao().getAll(),
            db.medDao().getMeds(), db.medDao().getLogs(), db.medDao().getRecords(),
            db.cycleDao().getEverything(),
            CycleSettings(Prefs.cycleBirthControl(app), Prefs.cycleDefaultLength(app), Prefs.cycleDefaultPeriod(app), Prefs.cycleRemindBefore(app),
                Prefs.cycleRemindPms(app), Prefs.cycleRemindOvulation(app), Prefs.pillMedId(app)),
            db.studyDao().getLogs(), db.studyDao().getPresets(), db.moodDao().getAll(),
            prefs = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).all.filterKeys { k -> SKIP_PREFS.none { k.startsWith(it) } }
        )
    }

    /** 偏好设置文件（AI / 语音 / 高德的 Key、功能开关、生理期设置等都在这里） */
    private const val PREFS_FILE = "li_native_prefs"
    /** 跟这台手机绑定的设置不备份（自动备份文件夹的授权换手机就失效了） */
    private val SKIP_PREFS = listOf("autoBackupDir", "lastAutoBackup", "autoBackupError")

    /** 写备份压缩包：backup.json + 图片、附件、自定义铃声文件 */
    suspend fun writeArchive(ctx: Context, out: OutputStream) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val json = buildJson(app)
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchive.JSON_ENTRY)); zip.write(json.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            for (folder in BackupArchive.FOLDERS) {
                File(app.filesDir, folder).listFiles()?.filter { it.isFile }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("$folder/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** 导出：把全部数据写进用户选的位置，成功返回 null，失败返回原因 */
    suspend fun export(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val app = ctx.applicationContext
            app.contentResolver.openOutputStream(uri, "w")?.use { writeArchive(app, it) }
                ?: return@withContext "无法写入所选位置"
            SettingsStore.update(app) { it.copy(lastBackup = System.currentTimeMillis()) }
            null
        } catch (e: Exception) {
            "备份失败：" + (e.message ?: "未知错误")
        }
    }

    /** 现在有没有私密内容（有的话导出备份前要先验证身份，因为备份文件里是明文） */
    suspend fun hasPrivate(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(ctx.applicationContext)
        db.birthdayDao().getAll().any { it.isPrivate } || db.reminderDao().getAll().any { it.isPrivate } ||
            db.diaryDao().getAll().any { it.isPrivate } || db.phraseDao().getAll().any { it.isPrivate } ||
            db.fitnessDao().getAll().any { it.isPrivate } || db.allergyDao().getAll().any { it.isPrivate } ||
            db.alarmDao().getAll().any { it.isPrivate } || db.medDao().getMeds().any { it.isPrivate } ||
            db.medDao().getRecords().any { it.isPrivate } || db.cycleDao().getAll(CycleProfiles.PRIVATE).isNotEmpty()
    }

    sealed class ReadResult {
        data class Ok(val data: BackupData) : ReadResult()
        data class Fail(val message: String) : ReadResult()
    }

    /** 读备份时解出来、等确认恢复时再放进 App 的文件（图片、附件、铃声） */
    @Volatile private var staged: File? = null

    suspend fun read(ctx: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val stage = File(app.cacheDir, "restore_staging").apply { deleteRecursively() }
        staged = null
        val text = try {
            app.contentResolver.openInputStream(uri)?.use { raw ->
                val input = BufferedInputStream(raw)
                input.mark(4)
                val head = ByteArray(2); val n = input.read(head)
                input.reset()
                if (n == 2 && BackupArchive.isZip(head)) {
                    var json: String? = null
                    var total = 0L
                    ZipInputStream(input).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            if (e.isDirectory) continue
                            if (e.name == BackupArchive.JSON_ENTRY) { json = zip.readBytes().toString(Charsets.UTF_8); continue }
                            val rel = BackupArchive.safePath(e.name) ?: continue          // 只认图片 / 附件 / 铃声文件夹，防止写到别处
                            val dest = File(stage, rel).apply { parentFile?.mkdirs() }
                            dest.outputStream().use { o ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = zip.read(buf); if (r < 0) break
                                    total += r
                                    if (total > MAX_RESTORE_BYTES) throw IllegalStateException("备份太大")
                                    o.write(buf, 0, r)
                                }
                            }
                        }
                    }
                    if (json != null) staged = stage
                    json
                } else input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) { null } ?: return@withContext ReadResult.Fail("读取文件失败")
        BackupCodec.parse(text)?.let { ReadResult.Ok(it) } ?: ReadResult.Fail("这不是有效的备份文件")
    }

    private const val MAX_RESTORE_BYTES = 2L * 1024 * 1024 * 1024     // 解压上限 2GB

    /**
     * 日记里的一张图片地址 -> 这台手机上的相对路径：
     *  data: 开头的（网页版内嵌）解码后存成文件；
     *  指向 …/diary_images/文件名 且文件还在的（旧版 App 在同一台手机上），直接接上；
     *  其它找不到的返回 null（整行丢掉，不留破图）。
     */
    private fun resolveImage(ctx: Context, src: String): String? {
        if (src.startsWith("data:")) {
            return try {
                val comma = src.indexOf(',')
                val mime = src.substring(5, src.indexOf(';').takeIf { it > 0 } ?: comma)
                val bytes = Base64.decode(src.substring(comma + 1), Base64.DEFAULT)
                val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                val dir = File(ctx.filesDir, "diary_images").apply { mkdirs() }
                val f = File(dir, "${UUID.randomUUID()}.$ext")
                f.writeBytes(bytes)
                "diary_images/${f.name}"
            } catch (e: Exception) { null }
        }
        val rel = DiaryBackupText.imagePathOf(src) ?: return null
        return if (File(ctx.filesDir, rel).exists()) rel else null
    }

    /** 恢复：整表替换（放在一个事务里，中途出错不会留下一半的数据），然后重新排通知、闹钟 */
    suspend fun apply(ctx: Context, data: BackupData) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val db = AppDatabase.get(app)
        // 备份压缩包里的图片、附件、铃声：先放进 App（同名覆盖），后面的日记、闹钟才接得上
        staged?.let { stage ->
            stage.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(stage).invariantSeparatorsPath
                if (BackupArchive.safePath(rel) != null) f.copyTo(File(app.filesDir, rel), overwrite = true)
            }
            stage.deleteRecursively()
            staged = null
        }

        val diaryEntities = data.diary?.map { d ->
            val blocks = DiaryBackupText.toBlocks(d.text, resolveFile = { src ->
                // 附件文件本身不在备份里：同一台手机上文件还在就接回，否则这一行丢掉
                DiaryBackupText.filePathOf(src)?.takeIf { File(app.filesDir, it).exists() }      // 备份压缩包带了附件就已经放好了
            }) { src -> resolveImage(app, src) }
            DiaryEntity(
                id = d.id, date = d.date, content = BlockConverter.toJson(blocks), updatedAt = d.updatedAt,
                locationLat = d.location?.lat, locationLng = d.location?.lng, locationAddress = d.location?.address,
                isPrivate = d.isPrivate
            )
        }

        // 闹钟铃声：备份里是 alarm_sounds/文件名，换成这台手机上的完整路径；文件不在（旧的 .json 备份）就用系统默认铃声
        val alarmsToWrite = data.alarms?.map { a ->
            val f = a.soundPath?.let { File(app.filesDir, it) }?.takeIf { it.exists() }
            a.copy(soundPath = f?.absolutePath, soundName = if (f != null) a.soundName else null)
        }
        val oldAlarms = if (data.alarms != null) db.alarmDao().getAll() else emptyList()

        db.withTransaction {
            db.birthdayDao().deleteAll(); db.birthdayDao().upsertAll(data.birthdays)
            db.reminderDao().deleteAll(); db.reminderDao().upsertAll(data.reminders)
            alarmsToWrite?.let { db.alarmDao().deleteAll(); db.alarmDao().upsertAll(it) }
            diaryEntities?.let { db.diaryDao().deleteAll(); db.diaryDao().upsertAll(it) }
            data.phrases?.let { db.phraseDao().deleteAll(); db.phraseDao().upsertAll(it) }
            // 健身项目设置（含自定义项目）先恢复，数据才有名字
            data.fitnessFields?.let { FitnessFieldStore.save(app, it) }
            data.fitness?.let { db.fitnessDao().deleteAll(); db.fitnessDao().upsertAll(it) }
            data.allergy?.let { db.allergyDao().deleteAll(); db.allergyDao().upsertAll(it) }
            data.medications?.let { db.medDao().deleteAllMeds(); db.medDao().upsertMeds(it) }
            data.medLogs?.let { db.medDao().deleteAllLogs(); db.medDao().upsertLogs(it) }
            data.medicalRecords?.let { db.medDao().deleteAllRecords(); db.medDao().upsertRecords(it) }
            if (data.medications != null || data.medicalRecords != null) MedScheduler.rescheduleSafe(app)
            data.cycleDays?.let { db.cycleDao().deleteAll(); db.cycleDao().upsertAll(it) }
            data.cycleSettings?.let { c ->
                Prefs.setCycleBirthControl(app, c.birthControl); Prefs.setCycleDefaultLength(app, c.cycleLength); Prefs.setCycleDefaultPeriod(app, c.periodLength)
                Prefs.setCycleRemindBefore(app, c.remindBefore); Prefs.setCycleRemindPms(app, c.remindPms); Prefs.setCycleRemindOvulation(app, c.remindOvulation)
                Prefs.setPillMedId(app, c.pillMedId)
            }
            if (data.cycleDays != null || data.cycleSettings != null) CycleScheduler.reschedule(app)
            data.studyLogs?.let { db.studyDao().deleteAllLogs(); db.studyDao().upsertLogs(it) }
            data.studyPresets?.let { db.studyDao().deleteAllPresets(); db.studyDao().upsertPresets(it) }
            data.moods?.let { db.moodDao().deleteAll(); db.moodDao().upsertAll(it) }
        }
        // 被换掉的旧闹钟的铃声文件清理掉（新闹钟还在用的不删）
        val keep = alarmsToWrite?.mapNotNull { it.soundPath }?.toSet() ?: emptySet()
        oldAlarms.filter { it.soundPath != null && it.soundPath !in keep }.forEach { AlarmEngine.deleteSound(app, it.soundPath) }

        // 偏好设置和 Key（AI / 语音 / 高德、功能开关等）：按原类型写回
        data.prefs?.let { prefs ->
            val ed = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit()
            for ((k, v) in prefs) {
                if (SKIP_PREFS.any { k.startsWith(it) }) continue
                when (v) {
                    is String -> ed.putString(k, v); is Boolean -> ed.putBoolean(k, v); is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v)
                    is Set<*> -> ed.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            ed.commit()
            ModuleStore.version.intValue++        // 功能开关可能变了，界面跟着刷新
        }

        SettingsStore.update(app) { BackupCodec.applySettings(it, data.settings) }
        ReminderScheduler.reschedule(app)
        AlarmEngine.sync(app)
        // 小组件会自己跟着数据变化刷新
    }
}
