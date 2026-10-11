package app.birthdayreminder

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.AlarmTriggers
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 原生 Kotlin 版闹钟和你旧版里“已经在用”的 Java 响铃引擎之间的桥。
 *
 * 响铃、稍后提醒、锁屏弹出、铃声播放、开机重新定时，全部沿用现有的
 * AlarmStore / AlarmReceiver / AlarmService / AlarmActivity（不改，不重写）。
 * 这里只负责：按新数据库里的闹钟 + 节假日，算出接下来的响铃时刻，交给 AlarmStore。
 *
 * 必须放在 app.birthdayreminder 这个包里，因为 AlarmStore 是包内可见(package-private)的。
 */
object AlarmEngine {

    /** 重新计算并交给系统：新增/修改/删除/开关闹钟、打开 App、节假日数据更新之后都要调一次 */
    suspend fun sync(ctx: Context) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val dao = AppDatabase.get(app).alarmDao()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        // 已经过期的“仅一次”闹钟自动关掉（和旧版 alarmHousekeeping 一致）
        val expired = AlarmTriggers.expiredOnceIds(dao.getAll(), now)
        for (id in expired) dao.getById(id)?.let { dao.upsert(it.copy(enabled = false)) }

        val holidays = HolidayRepository.current(app)
        // 闹钟功能关掉了：一个都不排（下面写入空列表，原来排的就取消了）
        val triggers = if (!Prefs.moduleOn(app, Modules.ALARM)) emptyList()
            else AlarmTriggers.build(dao.getAll(), now, zone, LocalDate.now(zone), holidays::isWorkday)

        val arr = JSONArray()
        for (t in triggers) {
            arr.put(
                JSONObject()
                    .put("aid", t.aid).put("at", t.at).put("label", t.label)
                    .put("sound", t.sound).put("snooze", t.snooze).put("vibrate", t.vibrate)
            )
        }
        AlarmStore.setTriggers(app, arr)
        AlarmStore.armNext(app)
    }

    // ---------------- 自定义铃声：选文件 -> 复制进 App 私有目录 ----------------

    private const val MAX_SOUND_BYTES = 25L * 1024 * 1024

    sealed class SoundResult {
        data class Ok(val path: String, val name: String) : SoundResult()
        data class Fail(val message: String) : SoundResult()
    }

    private fun soundDir(ctx: Context): File = File(ctx.filesDir, "alarm_sounds").apply { mkdirs() }

    /** 把用户选的音频复制到 App 私有目录，并检查确实是音频、没超过 25MB（规则和旧版一致） */
    suspend fun importSound(ctx: Context, uri: Uri): SoundResult = withContext(Dispatchers.IO) {
        var name = "audio"
        var size = -1L
        var cursor: Cursor? = null
        try {
            cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !cursor.isNull(ni)) name = cursor.getString(ni)
                if (si >= 0 && !cursor.isNull(si)) size = cursor.getLong(si)
            }
        } catch (e: RuntimeException) {
            // 拿不到名字/大小就用默认值
        } finally {
            cursor?.close()
        }
        if (size > MAX_SOUND_BYTES) return@withContext SoundResult.Fail("文件太大，请选 25MB 以内的音频")

        var ext = ""
        val dot = name.lastIndexOf('.')
        if (dot >= 0 && name.length - dot <= 6) ext = name.substring(dot).replace(Regex("[^A-Za-z0-9.]"), "")
        val out = File(soundDir(ctx), UUID.randomUUID().toString() + ext)

        var ok = false
        try {
            val input = ctx.contentResolver.openInputStream(uri)
                ?: return@withContext SoundResult.Fail("无法打开这个文件")
            input.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_SOUND_BYTES) return@withContext SoundResult.Fail("文件太大，请选 25MB 以内的音频")
                        os.write(buf, 0, n)
                    }
                }
            }
            ok = true
        } catch (e: Exception) {
            return@withContext SoundResult.Fail("读取文件失败")
        } finally {
            if (!ok) out.delete()
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(out.absolutePath)
            if (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") {
                out.delete()
                return@withContext SoundResult.Fail("这不是音频文件")
            }
        } catch (e: RuntimeException) {
            out.delete()
            return@withContext SoundResult.Fail("这不是音频文件")
        } finally {
            try { mmr.release() } catch (e: Exception) { }
        }
        SoundResult.Ok(out.absolutePath, name)
    }

    /** 只删 App 私有铃声目录里的文件，路径不在这个目录下一律不动 */
    fun deleteSound(ctx: Context, path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            val f = File(path).canonicalFile
            val dir = soundDir(ctx).canonicalFile
            if (f.path.startsWith(dir.path + File.separator)) f.delete()
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ---------------- 试听 ----------------

    private val main = Handler(Looper.getMainLooper())
    private var preview: MediaPlayer? = null
    private val stopTask = Runnable { stopPreview() }
    private const val PREVIEW_MS = 6000L

    /** 试听 6 秒。path 为空就放系统默认闹钟铃声。 */
    fun previewSound(ctx: Context, path: String?) {
        main.post {
            stopPreview()
            val mp = AlarmSound.prepare(ctx.applicationContext, path ?: "", false) ?: return@post
            try {
                mp.start()
                preview = mp
                main.postDelayed(stopTask, PREVIEW_MS)
            } catch (e: RuntimeException) {
                try { mp.release() } catch (ignored: RuntimeException) { }
            }
        }
    }

    fun stopPreview() {
        main.removeCallbacks(stopTask)
        preview?.let {
            try { it.stop() } catch (e: RuntimeException) { }
            try { it.release() } catch (e: RuntimeException) { }
        }
        preview = null
    }
}
