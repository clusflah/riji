package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import app.birthdayreminder.data.Alarm
import app.birthdayreminder.data.AllergyLog
import app.birthdayreminder.data.FitnessLog
import app.birthdayreminder.data.MedLog
import app.birthdayreminder.data.Medication
import app.birthdayreminder.data.MedicalRecord
import app.birthdayreminder.data.CycleDay
import app.birthdayreminder.data.CycleProfiles
import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.ContentBlock
import app.birthdayreminder.data.DiaryLocation
import app.birthdayreminder.data.Phrase
import app.birthdayreminder.data.Reminder
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** 备份里的一篇日记：正文是 Markdown 文本（和旧版一样），图片用 ![](路径) 一行表示 */
data class BackupDiary(val id: String, val date: String, val text: String, val updatedAt: Long, val location: DiaryLocation?, val isPrivate: Boolean = false)

/** 备份里带的设置（都是可选的：文件里有才恢复） */
data class BackupSettings(
    val dailyTime: String? = null,
    val defAdvance: List<Int>? = null,
    val weekStart: Int? = null,
    val showLunar: Boolean? = null,
    val holidayAlert: Boolean? = null,
    val theme: String? = null
)

/** alarms / diary / phrases 为 null 表示备份文件里没有这一项，恢复时不动现有数据（和旧版一致） */
data class BackupData(
    val birthdays: List<Birthday>,
    val reminders: List<Reminder>,
    val alarms: List<Alarm>?,
    val diary: List<BackupDiary>?,
    val phrases: List<Phrase>?,
    val settings: BackupSettings,
    val fitness: List<FitnessLog>? = null,
    val fitnessFields: List<FitnessFields.Field>? = null,
    val allergy: List<AllergyLog>? = null,
    val medications: List<Medication>? = null,
    val medLogs: List<MedLog>? = null,
    val medicalRecords: List<MedicalRecord>? = null,
    val cycleDays: List<CycleDay>? = null,
    val cycleSettings: CycleSettings? = null,
    /** App 的偏好设置（含 AI / 语音 / 高德的 Key、功能开关、生理期设置等），值是 String / Boolean / Int / Long / Float / Set<String> */
    val prefs: Map<String, Any>? = null
)

/** 生理期设置（备份用） */
data class CycleSettings(
    val birthControl: Boolean = false, val cycleLength: Int = 28, val periodLength: Int = 5,
    val remindBefore: Int = 2, val remindPms: Boolean = true, val remindOvulation: Boolean = false, val pillMedId: String = ""
)

/**
 * 备份文件的导出和读取。文件格式和旧版“历”完全一致：
 *   {"app":"li-calendar","version":1,"exportedAt":"…","data":{birthdays,reminders,alarms,diary,phrases,settings}}
 * 所以旧版导出的备份能直接导进原生版；原生版导出的备份，字段也都是旧版认得的。
 * 读取时的校验规则逐条对应旧版 sanitizeBackup。纯逻辑，可单独测试。
 */
object BackupCodec {
    const val APP_ID = "li-calendar"

    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val HM = Regex("^\\d{2}:\\d{2}$")
    private val REPEATS = listOf("none", "daily", "weekly", "monthly", "yearly", "interval")
    private val REMINDER_ADVANCE = listOf(0, 10, 60, 1440)
    private val ALARM_REPEATS = listOf("once", "daily", "workday", "restday", "custom")
    private val SNOOZES = listOf(0, 5, 10, 15, 30)

    // ---------------- 导出 ----------------

    fun export(
        birthdays: List<Birthday>, reminders: List<Reminder>, alarms: List<Alarm>,
        diary: List<BackupDiary>, phrases: List<Phrase>, settings: AppSettings, exportedAt: String,
        fitness: List<FitnessLog> = emptyList(), allergy: List<AllergyLog> = emptyList(),
        medications: List<Medication> = emptyList(), medLogs: List<MedLog> = emptyList(),
        medicalRecords: List<MedicalRecord> = emptyList(),
        cycleDays: List<CycleDay> = emptyList(), cycleSettings: CycleSettings? = null,
        prefs: Map<String, Any?> = emptyMap()
    ): String {
        val data = linkedMapOf<String, Any?>(
            "birthdays" to birthdays.map { b ->
                mapOf("id" to b.id, "name" to b.name, "type" to b.type, "year" to b.year, "month" to b.month,
                    "day" to b.day, "leap" to b.leap, "advance" to b.advance, "note" to b.note, "private" to b.isPrivate)
            },
            "reminders" to reminders.map { r ->
                val m = linkedMapOf<String, Any?>(
                    "id" to r.id, "title" to r.title, "date" to r.date, "time" to r.time, "repeat" to r.repeat,
                    "advance" to r.advance, "note" to r.note, "done" to r.done.filterValues { it }, "private" to r.isPrivate
                )
                if (r.repeat == "interval") { m["intervalUnit"] = r.intervalUnit; m["intervalN"] = r.intervalN }
                m
            },
            "alarms" to alarms.map { a ->
                mapOf("id" to a.id, "time" to a.time, "label" to a.label, "repeat" to a.repeat, "days" to a.days,
                    "snooze" to a.snooze, "vibrate" to a.vibrate, "sound" to null, "enabled" to a.enabled, "onceAt" to a.onceAt,
                    // 自定义铃声：只记文件名，文件本身在备份压缩包的 alarm_sounds/ 里
                    "soundFile" to a.soundPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }, "soundName" to a.soundName, "private" to a.isPrivate)
            },
            "diary" to diary.map { d ->
                mapOf("id" to d.id, "date" to d.date, "text" to d.text, "updatedAt" to d.updatedAt, "images" to emptyList<Any>(),
                    "location" to d.location?.let { mapOf("lat" to it.lat, "lng" to it.lng, "address" to it.address) }, "private" to d.isPrivate)
            },
            "phrases" to phrases.map { p ->
                mapOf("id" to p.id, "category" to p.category, "title" to p.title, "value" to p.value, "updatedAt" to p.updatedAt, "private" to p.isPrivate)
            },
            "fitnessFields" to FitnessConfig.toMaps(FitnessFields.ALL),
            "medications" to medications.map { m ->
                mapOf("id" to m.id, "name" to m.name, "dosage" to m.dosage, "note" to m.note, "plan" to m.plan, "stock" to m.stock,
                    "unit" to m.unit, "lowStock" to m.lowStock, "expiry" to m.expiry, "active" to m.active, "updatedAt" to m.updatedAt, "private" to m.isPrivate)
            },
            "cycleDays" to cycleDays.map { c ->
                mapOf("date" to c.date, "flow" to c.flow, "symptoms" to MedicalLogic.listFromJson(c.symptoms), "mood" to c.mood,
                    "discharge" to c.discharge, "note" to c.note, "updatedAt" to c.updatedAt) +
                    (if (c.profile.isNotEmpty()) mapOf("profile" to c.profile) else emptyMap())      // 隐私空间那份才写，旧版读到也不受影响
            },
            "cycleSettings" to cycleSettings?.let { c ->
                mapOf("birthControl" to c.birthControl, "cycleLength" to c.cycleLength, "periodLength" to c.periodLength,
                    "remindBefore" to c.remindBefore, "remindPms" to c.remindPms, "remindOvulation" to c.remindOvulation, "pillMedId" to c.pillMedId)
            },
            // 偏好设置和 Key：每项记下类型，恢复时按原类型写回
            "prefs" to prefs.mapNotNull { (k, v) ->
                val t = when (v) { is String -> "s"; is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Set<*> -> "ss"; else -> null }
                if (t == null) null else k to mapOf("t" to t, "v" to (if (v is Set<*>) v.map { it.toString() } else v))
            }.toMap(),
            "medicalRecords" to medicalRecords.map { r ->
                mapOf("id" to r.id, "date" to r.date, "illness" to r.illness, "symptoms" to r.symptoms, "hospital" to r.hospital,
                    "department" to r.department, "doctor" to r.doctor, "advice" to r.advice, "followUp" to r.followUp,
                    "medIds" to MedicalLogic.listFromJson(r.medIds), "images" to MedicalLogic.listFromJson(r.images), "note" to r.note, "updatedAt" to r.updatedAt, "private" to r.isPrivate)
            },
            "medLogs" to medLogs.map { l ->
                mapOf("id" to l.id, "medId" to l.medId, "medName" to l.medName, "date" to l.date, "time" to l.time,
                    "amount" to l.amount, "status" to l.status, "actionAt" to l.actionAt, "scheduled" to l.scheduled)
            },
            "fitness" to fitness.map { f ->
                mapOf("date" to f.date, "metrics" to f.metrics, "note" to f.note, "updatedAt" to f.updatedAt, "private" to f.isPrivate)
            },
            "allergy" to allergy.map { a ->
                mapOf("date" to a.date, "eye" to a.eye, "nose" to a.nose, "cough" to a.cough, "note" to a.note, "updatedAt" to a.updatedAt, "private" to a.isPrivate)
            },
            "settings" to mapOf(
                "notify" to settings.notify, "dailyTime" to settings.dailyTime, "defAdvance" to settings.defAdvance,
                "weekStart" to settings.weekStart, "showLunar" to settings.showLunar, "theme" to settings.theme,
                "holidayAlert" to settings.holidayAlert, "autoHoliday" to settings.autoHoliday
            )
        )
        return MiniJson.write(mapOf("app" to APP_ID, "version" to 1, "exportedAt" to exportedAt, "data" to data))
    }

    // ---------------- 读取 ----------------

    @Suppress("UNCHECKED_CAST")
    private fun asMap(x: Any?): Map<String, Any?>? = x as? Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    private fun asList(x: Any?): List<Any?>? = x as? List<Any?>

    private fun num(x: Any?): Double? = when (x) {
        is Number -> x.toDouble()
        is String -> x.trim().toDoubleOrNull()
        else -> null
    }
    private fun int(x: Any?): Int? = num(x)?.takeIf { !it.isNaN() && !it.isInfinite() }?.toInt()
    private fun str(x: Any?): String = if (x == null) "" else if (x is String) x else x.toString()
    private fun id(x: Any?): String = str(x).ifBlank { UUID.randomUUID().toString() }

    /** 无法识别（不是本 App 的备份）返回 null */
    fun parse(text: String, now: Long = System.currentTimeMillis()): BackupData? {
        val root = try { asMap(MiniJson.parse(text)) } catch (e: Exception) { null } ?: return null
        if (root["app"] != APP_ID) return null
        val d = asMap(root["data"]) ?: return null

        val birthdays = (asList(d["birthdays"]) ?: emptyList()).mapNotNull { x ->
            val b = asMap(x) ?: return@mapNotNull null
            val name = b["name"] as? String ?: return@mapNotNull null
            val type = b["type"] as? String
            if (type != "solar" && type != "lunar") return@mapNotNull null
            val month = int(b["month"]) ?: return@mapNotNull null
            val day = int(b["day"]) ?: return@mapNotNull null
            if (month !in 1..12) return@mapNotNull null
            val maxDay = if (type == "lunar") 30 else YearMonth.of(2000, month).lengthOfMonth()
            if (day !in 1..maxDay) return@mapNotNull null
            val year = int(b["year"])?.takeIf { it in 1..3000 }
            Birthday(
                id = id(b["id"]), name = name.take(30), type = type, year = year, month = month, day = day,
                leap = type == "lunar" && b["leap"] == true,
                advance = (asList(b["advance"]) ?: emptyList()).mapNotNull { int(it) }.filter { it in 0..7 },
                note = str(b["note"]).take(60),
                isPrivate = b["private"] == true
            )
        }

        val reminders = (asList(d["reminders"]) ?: emptyList()).mapNotNull { x ->
            val r = asMap(x) ?: return@mapNotNull null
            val title = r["title"] as? String ?: return@mapNotNull null
            val date = (r["date"] as? String)?.trim() ?: ""
            val isTodo = date.isEmpty()                         // 没有日期 = 无时间待办
            if (!isTodo && (!DATE.matches(date) || !try { LocalDate.parse(date); true } catch (e: Exception) { false })) return@mapNotNull null
            val time = if (isTodo) "" else (r["time"] as? String)?.takeIf { HM.matches(it) } ?: ""
            val repeat = if (isTodo) "none" else (r["repeat"] as? String)?.takeIf { it in REPEATS } ?: "none"
            val advance = int(r["advance"])?.takeIf { it in REMINDER_ADVANCE } ?: 0
            val done = LinkedHashMap<String, Boolean>()
            asMap(r["done"])?.forEach { (k, v) -> if (v == true) done[k] = true }
            var unit: String? = null
            var n: Int? = null
            if (repeat == "interval") {
                unit = (r["intervalUnit"] as? String)?.takeIf { it in listOf("day", "month", "year") } ?: "day"
                n = int(r["intervalN"])?.takeIf { it in 1..365 } ?: 1
            }
            Reminder(id = id(r["id"]), title = title.take(60), date = date, time = time, repeat = repeat,
                advance = advance, note = str(r["note"]).take(80), done = done, intervalUnit = unit, intervalN = n,
                isPrivate = r["private"] == true)
        }

        val alarms = asList(d["alarms"])?.mapNotNull { x ->
            val a = asMap(x) ?: return@mapNotNull null
            val time = a["time"] as? String ?: return@mapNotNull null
            if (!HM.matches(time)) return@mapNotNull null
            val repeat = a["repeat"] as? String ?: return@mapNotNull null
            if (repeat !in ALARM_REPEATS) return@mapNotNull null
            Alarm(
                id = id(a["id"]), time = time, label = str(a["label"]).take(20), repeat = repeat,
                days = (asList(a["days"]) ?: emptyList()).mapNotNull { int(it) }.filter { it in 0..6 },
                snooze = int(a["snooze"])?.takeIf { it in SNOOZES } ?: 10,
                vibrate = a["vibrate"] != false,
                // 铃声：备份压缩包里带了文件就接上（先记成相对路径，恢复时换成这台手机上的完整路径）；文件名不合法就连名字一起不要
                soundPath = soundRel(a["soundFile"]),
                soundName = if (soundRel(a["soundFile"]) != null) (a["soundName"] as? String)?.take(60)?.takeIf { it.isNotBlank() } else null,
                enabled = a["enabled"] != false,
                onceAt = (a["onceAt"] as? Number)?.toLong(),
                isPrivate = a["private"] == true
            )
        }

        val diary = asList(d["diary"])?.mapNotNull { x ->
            val e = asMap(x) ?: return@mapNotNull null
            val date = e["date"] as? String ?: return@mapNotNull null
            val text = e["text"] as? String ?: return@mapNotNull null
            if (!DATE.matches(date) || text.isBlank()) return@mapNotNull null
            val loc = asMap(e["location"])?.let { l ->
                val lat = (l["lat"] as? Number)?.toDouble()
                val lng = (l["lng"] as? Number)?.toDouble()
                if (lat != null && lng != null) DiaryLocation(lat, lng, str(l["address"])) else null
            }
            BackupDiary(id(e["id"]), date, text.take(6000), (e["updatedAt"] as? Number)?.toLong() ?: now, loc, e["private"] == true)
        }

        val phrases = asList(d["phrases"])?.mapNotNull { x ->
            val p = asMap(x) ?: return@mapNotNull null
            val title = p["title"] as? String ?: return@mapNotNull null
            val value = p["value"] as? String ?: return@mapNotNull null
            if (title.isBlank() || value.isBlank()) return@mapNotNull null
            Phrase(id(p["id"]), str(p["category"]).take(20), title.take(40), value.take(500), (p["updatedAt"] as? Number)?.toLong() ?: now, p["private"] == true)
        }

        fun validDay(x: Any?): String? = (x as? String)?.takeIf { DATE.matches(it) && try { LocalDate.parse(it); true } catch (e: Exception) { false } }

        // 健身项目设置（含自定义项目）；读健身数据时，备份里的项目和本机的项目都认
        val fitnessFields = asList(d["fitnessFields"])?.let { FitnessConfig.merge(FitnessConfig.fromMaps(it)) }
        val knownFields = (FitnessFields.ALL + (fitnessFields ?: emptyList())).associateBy { it.key }
        val fitness = asList(d["fitness"])?.mapNotNull { x ->
            val f = asMap(x) ?: return@mapNotNull null
            val date = validDay(f["date"]) ?: return@mapNotNull null
            val metrics = LinkedHashMap<String, Double>()
            asMap(f["metrics"])?.forEach { (k, v) ->
                // 组数键（如 pullupSets）：1–100 的整数
                // 分组 / 极限组：每组的次数，整数，和次数项目同样的上限
                FitnessFields.partOfKey(k, knownFields)?.let { (pf, _) ->
                    val n = num(v)
                    if (n != null && !n.isNaN() && n > 0 && n <= pf.max) metrics[k] = Math.floor(n)
                    return@forEach
                }
                FitnessFields.fieldOfSetsKey(k, knownFields)?.let {
                    val n = num(v)
                    if (n != null && !n.isNaN() && n >= 1 && n <= FitnessFields.MAX_SETS) metrics[k] = Math.floor(n)
                    return@forEach
                }
                val field = knownFields[k] ?: return@forEach           // 不认识的项目忽略
                val n = num(v) ?: return@forEach
                if (n.isNaN() || n.isInfinite() || n <= 0 || n > field.max) return@forEach
                metrics[k] = FitnessFields.round(field, n)
            }
            val note = str(f["note"]).take(200)
            if (metrics.isEmpty() && note.isBlank()) return@mapNotNull null
            FitnessLog(date, metrics, note, (f["updatedAt"] as? Number)?.toLong() ?: now, isPrivate = f["private"] == true)
        }?.distinctBy { it.date }

        val allergy = asList(d["allergy"])?.mapNotNull { x ->
            val a = asMap(x) ?: return@mapNotNull null
            val date = validDay(a["date"]) ?: return@mapNotNull null
            fun lv(k: String) = (int(a[k]) ?: 0).coerceIn(0, 3)
            AllergyLog(date, lv("eye"), lv("nose"), lv("cough"), str(a["note"]).take(200), (a["updatedAt"] as? Number)?.toLong() ?: now, isPrivate = a["private"] == true)
        }?.distinctBy { it.date }

        // 用药：药品（计划重新整理一遍，防止坏数据）和服药记录
        val medications = asList(d["medications"])?.mapNotNull { x ->
            val m = asMap(x) ?: return@mapNotNull null
            val name = str(m["name"]).trim().take(30)
            if (name.isEmpty()) return@mapNotNull null
            fun dbl(k: String) = num(m[k])?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 }
            Medication(id(m["id"]), name, str(m["dosage"]).take(30), str(m["note"]).take(200),
                MedLogic.planToJson(MedLogic.planFromJson(m["plan"] as? String)), dbl("stock"), str(m["unit"]).take(6).ifEmpty { "片" },
                dbl("lowStock"), validDay(m["expiry"]), m["active"] != false, (m["updatedAt"] as? Number)?.toLong() ?: now, isPrivate = m["private"] == true)
        }?.distinctBy { it.id }
        val hm = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val medLogs = asList(d["medLogs"])?.mapNotNull { x ->
            val l = asMap(x) ?: return@mapNotNull null
            val date = validDay(l["date"]) ?: return@mapNotNull null
            val time = (l["time"] as? String)?.takeIf { hm.matches(it) } ?: return@mapNotNull null
            val status = (l["status"] as? String)?.takeIf { it == "taken" || it == "skipped" } ?: return@mapNotNull null
            val logId = (l["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MedLog(logId, str(l["medId"]), str(l["medName"]).take(30), date, time, (num(l["amount"]) ?: 1.0).coerceIn(0.0, 1000.0),
                status, (l["actionAt"] as? Number)?.toLong() ?: now, l["scheduled"] != false)
        }?.distinctBy { it.id }

        // 就医记录：照片文件本身不在备份里（同一台手机恢复时还在就能看到）
        val medicalRecords = asList(d["medicalRecords"])?.mapNotNull { x ->
            val r = asMap(x) ?: return@mapNotNull null
            val date = validDay(r["date"]) ?: return@mapNotNull null
            val illness = str(r["illness"]).trim().take(40)
            if (illness.isEmpty()) return@mapNotNull null
            val fu = validDay(r["followUp"])?.takeIf { it >= date }
            fun strs(k: String) = asList(r[k]).orEmpty().mapNotNull { (it as? String)?.takeIf { v -> v.isNotBlank() } }
            MedicalRecord(id(r["id"]), date, illness, str(r["symptoms"]).take(300), str(r["hospital"]).take(40), str(r["department"]).take(20),
                str(r["doctor"]).take(20), str(r["advice"]).take(500), fu, MedicalLogic.listToJson(strs("medIds")),
                MedicalLogic.listToJson(strs("images").filter { it.startsWith("diary_images/") }), str(r["note"]).take(300),
                (r["updatedAt"] as? Number)?.toLong() ?: now, isPrivate = r["private"] == true)
        }?.distinctBy { it.id }

        // 生理期：每日记录（流量 0~4、只认识的症状 / 心情 / 分泌物）和设置
        val symKeys = CycleLogic.SYMPTOMS.map { it.first }.toSet()
        val cycleDays = asList(d["cycleDays"])?.mapNotNull { x ->
            val c = asMap(x) ?: return@mapNotNull null
            val date = validDay(c["date"]) ?: return@mapNotNull null
            CycleDay(date, ((c["flow"] as? Number)?.toInt() ?: 0).coerceIn(0, CycleLogic.PERIOD_ONLY),
                MedicalLogic.listToJson(asList(c["symptoms"]).orEmpty().mapNotNull { (it as? String)?.takeIf { k -> k in symKeys } }),
                (c["mood"] as? String)?.takeIf { m -> CycleLogic.MOODS.any { it.first == m } } ?: "",
                (c["discharge"] as? String)?.takeIf { m -> CycleLogic.DISCHARGES.any { it.first == m } } ?: "",
                str(c["note"]).take(200), (c["updatedAt"] as? Number)?.toLong() ?: now,
                profile = if (c["profile"] == CycleProfiles.PRIVATE) CycleProfiles.PRIVATE else CycleProfiles.MAIN)
        }?.distinctBy { it.profile to it.date }
        val cycleSettings = asMap(d["cycleSettings"])?.let { c ->
            CycleSettings(c["birthControl"] == true, ((c["cycleLength"] as? Number)?.toInt() ?: 28).coerceIn(15, 60),
                ((c["periodLength"] as? Number)?.toInt() ?: 5).coerceIn(1, 12), ((c["remindBefore"] as? Number)?.toInt() ?: 2).coerceIn(0, 7),
                c["remindPms"] != false, c["remindOvulation"] == true, str(c["pillMedId"]).take(60))
        }

        // 偏好设置和 Key：只认合法的键名和类型，太长的值丢掉
        val prefs = asMap(d["prefs"])?.mapNotNull { (k, raw) ->
            if (!Regex("[A-Za-z0-9_.\\-]{1,80}").matches(k)) return@mapNotNull null
            val e = asMap(raw) ?: return@mapNotNull null
            val v = e["v"]
            val value: Any? = when (e["t"]) {
                "s" -> (v as? String)?.takeIf { it.length <= 8000 }
                "b" -> v as? Boolean
                "i" -> (v as? Number)?.toInt()
                "l" -> (v as? Number)?.toLong()
                "f" -> (v as? Number)?.toFloat()
                "ss" -> asList(v)?.mapNotNull { (it as? String)?.takeIf { x -> x.length <= 2000 } }?.take(500)?.toSet()
                else -> null
            }
            value?.let { k to it }
        }?.toMap()

        val st = asMap(d["settings"]) ?: emptyMap()
        val settings = BackupSettings(
            dailyTime = (st["dailyTime"] as? String)?.takeIf { AppSettings.validTime(it) },
            defAdvance = asList(st["defAdvance"])?.mapNotNull { int(it) }?.filter { it in 0..7 },
            weekStart = int(st["weekStart"])?.takeIf { it == 0 || it == 1 },
            showLunar = st["showLunar"] as? Boolean,
            holidayAlert = st["holidayAlert"] as? Boolean,
            theme = (st["theme"] as? String)?.takeIf { it in listOf("auto", "light", "dark") }
        )
        return BackupData(birthdays, reminders, alarms, diary, phrases, settings, fitness = fitness, fitnessFields = fitnessFields, allergy = allergy,
            medications = medications, medLogs = medLogs, medicalRecords = medicalRecords,
            cycleDays = cycleDays, cycleSettings = cycleSettings, prefs = prefs)
    }

    /** 恢复前确认框里的一句话 */
    /** 备份里有几条私密内容（恢复确认框里提示用） */
    fun privateCount(d: BackupData): Int =
        d.birthdays.count { it.isPrivate } + d.reminders.count { it.isPrivate } +
            (d.diary?.count { it.isPrivate } ?: 0) + (d.phrases?.count { it.isPrivate } ?: 0) +
            (d.cycleDays?.count { it.profile.isNotEmpty() } ?: 0)

    fun summary(d: BackupData): String {
        val sb = StringBuilder("备份里有 ${d.birthdays.size} 个生日、${d.reminders.size} 项提醒")
        d.alarms?.let { sb.append("、${it.size} 个闹钟") }
        d.diary?.let { sb.append("、${it.size} 篇日记") }
        d.phrases?.let { sb.append("、${it.size} 条常用语") }
        d.fitness?.let { sb.append("、${it.size} 天健身数据") }
        d.allergy?.let { sb.append("、${it.size} 天过敏记录") }
        d.medications?.let { sb.append("、${it.size} 种药品") }
        d.medLogs?.let { sb.append("、${it.size} 条服药记录") }
        d.medicalRecords?.let { sb.append("、${it.size} 条就医记录") }
        d.cycleDays?.let { sb.append("、${it.size} 天生理期记录") }
        return sb.toString()
    }

    /** 把备份里的设置合并到现有设置：文件里有的才覆盖 */
    fun applySettings(cur: AppSettings, s: BackupSettings): AppSettings = cur.copy(
        dailyTime = s.dailyTime ?: cur.dailyTime,
        defAdvance = s.defAdvance ?: cur.defAdvance,
        weekStart = s.weekStart ?: cur.weekStart,
        showLunar = s.showLunar ?: cur.showLunar,
        holidayAlert = s.holidayAlert ?: cur.holidayAlert,
        theme = s.theme ?: cur.theme
    )

    /** 备份里的铃声文件名 -> alarm_sounds/ 下的相对路径；带路径、以点开头、有奇怪字符的都不认 */
    private fun soundRel(v: Any?): String? =
        (v as? String)?.takeIf { Regex("[A-Za-z0-9_.\\-]{1,120}").matches(it) && !it.startsWith(".") }?.let { "alarm_sounds/$it" }

    fun fileName(now: java.time.LocalDateTime): String =
        "li-backup-%04d%02d%02d-%02d%02d.zip".format(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute)
}

/**
 * 日记正文 <-> 备份里的 Markdown 文本。
 *  - 导出：文字块原样，图片块写成一行 ![](diary_images/xxx.jpg)（只是路径，图片文件本身不在备份里）
 *  - 导入：整篇文本按行拆开，独占一行的 ![](…) 变成图片块：
 *      data: 开头的（网页版内嵌的图片）交给 resolve 去解码保存；
 *      指向 …/diary_images/文件名 的，如果这台手机上文件还在（旧版 App 同一台手机迁移），直接接上；
 *      其它（找不到文件）整行丢掉，不留破图。
 */
object DiaryBackupText {
    private val imageLine = Regex("^\\s*!\\[[^\\]]*\\]\\(([^)]*)\\)\\s*$")
    private val fileLine = Regex("^\\s*📎\\[([^\\]]*)\\]\\(([^)]*)\\)\\s*$")
    private val attachRef = Regex("(?:^|/)diary_files/([^/?#)\\s]+)")

    /** 附件地址里取出 diary_files/文件名，取不到返回 null */
    fun filePathOf(src: String): String? = attachRef.find(src)?.let { "diary_files/" + it.groupValues[1] }
    private val fileRef = Regex("(?:^|/)diary_images/([^/?#)\\s]+)")

    fun blocksToText(blocks: List<ContentBlock>): String {
        val parts = ArrayList<String>()
        for (b in blocks) when (b) {
            is ContentBlock.TextBlock -> if (b.text.isNotBlank()) parts.add(RichText.toMarkdown(b.text).trim('\n'))
            is ContentBlock.ImageBlock -> parts.add("![](${b.imagePath})")
            is ContentBlock.FileBlock -> parts.add("📎[${b.name.replace("]", "")}](${b.path})")
            is ContentBlock.TableBlock -> parts.add(TableOps.toMarkdown(b.rows))
        }
        return parts.joinToString("\n\n")
    }

    /** 从图片地址里取出 diary_images/文件名，取不到返回 null */
    fun imagePathOf(src: String): String? = fileRef.find(src)?.let { "diary_images/" + it.groupValues[1] }

    /**
     * resolve：图片地址 -> 本机路径；resolveFile：附件地址 -> 本机路径（文件已经不在就返回 null，整行丢掉）
     */
    fun toBlocks(text: String, resolveFile: (String) -> String? = { null }, resolve: (String) -> String?): List<ContentBlock> {
        val out = ArrayList<ContentBlock>()
        val buf = ArrayList<String>()
        fun flush() {
            val t = buf.joinToString("\n").trim('\n', '\r')
            buf.clear()
            // 表格行变成表格块，其余文字还原成带格式的文字
            if (t.isNotBlank()) out.addAll(TableOps.blocksFromMarkdown(t))
        }
        for (line in text.replace("\r\n", "\n").split("\n")) {
            val fm = fileLine.find(line)
            if (fm != null) {
                flush()
                resolveFile(fm.groupValues[2])?.let { out.add(ContentBlock.FileBlock(path = it, name = fm.groupValues[1].ifBlank { "附件" })) }
                continue
            }
            val m = imageLine.find(line)
            if (m == null) { buf.add(line); continue }
            flush()
            resolve(m.groupValues[1])?.let { out.add(ContentBlock.ImageBlock(imagePath = it)) }
        }
        flush()
        return BlockOps.ensureTrailingText(out)
    }
}

/**
 * 备份压缩包（.zip）：backup.json（全部数据、设置和 Key）+ 图片、附件、自定义铃声文件。
 * 恢复时旧的 .json 备份也照样认。只解出这几个文件夹里的文件，防止压缩包里的路径写到别处。
 */
object BackupArchive {
    const val JSON_ENTRY = "backup.json"
    val FOLDERS = listOf("diary_images", "diary_files", "alarm_sounds")

    /** 文件开头是 “PK” 就是 zip */
    fun isZip(head: ByteArray): Boolean = head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

    /** 压缩包里的一个文件名 -> 可以安全解出的相对路径；不在这几个文件夹里、带 “..”、绝对路径的都拒绝 */
    fun safePath(name: String): String? {
        val n = name.replace('\\', '/')
        if (n.startsWith("/") || n.split('/').any { it == ".." || it == "." } || n.contains('\u0000')) return null
        val top = n.substringBefore('/')
        if (top !in FOLDERS || !n.contains('/') || n.endsWith("/")) return null
        val file = n.substringAfter('/')
        if (file.isEmpty() || file.length > 200) return null
        return n
    }
}
