package app.birthdayreminder.data

import android.content.Context
import app.birthdayreminder.domain.Holiday
import app.birthdayreminder.domain.HolidayDayEntry
import app.birthdayreminder.domain.HolidayIndex
import app.birthdayreminder.domain.Holidays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 法定节假日 / 调休数据：内置 2025、2026 年离线数据，其余年份联网从 holiday-cn 获取，
 * 缓存到本机，每 3 天最多刷新一次。数据源、刷新间隔、合并规则都和旧版一致。
 * 联网数据覆盖同一年的内置数据。
 */
object HolidayRepository {
    private const val PREFS = "li_native_holidays"
    private const val REFRESH_MS = 3L * 24 * 3600 * 1000
    private val SOURCES = listOf(
        "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/",
        "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/"
    )

    private val _index = MutableStateFlow(Holidays.merge(emptyMap()))
    val index: StateFlow<HolidayIndex> = _index

    /** 当前可用的索引（同步取，给通知/闹钟排程用）。第一次调用前先从缓存加载一次。 */
    fun current(ctx: Context): HolidayIndex {
        if (!loaded) reloadFromCache(ctx)
        return _index.value
    }

    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun holidayToJson(h: Holiday): JSONObject = JSONObject()
        .put("n", h.name).put("s", h.start.toString()).put("e", h.end.toString())
        .put("w", JSONArray(h.workdays.map { it.toString() }))

    private fun holidayFromJson(o: JSONObject): Holiday {
        val w = o.optJSONArray("w") ?: JSONArray()
        return Holiday(
            o.getString("n"), LocalDate.parse(o.getString("s")), LocalDate.parse(o.getString("e")),
            (0 until w.length()).map { LocalDate.parse(w.getString(it)) }
        )
    }

    /** 从缓存读出各年数据，和内置数据合并后更新 index */
    @Synchronized
    fun reloadFromCache(ctx: Context) {
        val cached = HashMap<Int, List<Holiday>>()
        for ((k, v) in prefs(ctx).all) {
            if (!k.startsWith("y") || v !is String) continue
            val year = k.substring(1).toIntOrNull() ?: continue
            try {
                val o = JSONObject(v)
                val list = o.optJSONArray("list") ?: continue      // list 为空 = 该年份没有数据，不覆盖内置
                cached[year] = (0 until list.length()).map { holidayFromJson(list.getJSONObject(it)) }
            } catch (e: Exception) {
                // 缓存坏了就忽略这一年，下次刷新会重新覆盖
            }
        }
        _index.value = Holidays.merge(cached)
        loaded = true
    }

    private fun fetchText(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.useCaches = false
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }

    /** 刷新某一年：返回 "cached"(还新鲜，没请求) / "ok" / "empty"(线上没有这一年) / "error" */
    suspend fun refreshYear(ctx: Context, year: Int, force: Boolean = false): String = withContext(Dispatchers.IO) {
        val p = prefs(ctx)
        val key = "y$year"
        val old = p.getString(key, null)
        if (!force && old != null) {
            val at = try { JSONObject(old).optLong("fetchedAt", 0L) } catch (e: Exception) { 0L }
            if (System.currentTimeMillis() - at < REFRESH_MS) return@withContext "cached"
        }
        var text: String? = null
        for (src in SOURCES) {
            text = fetchText("$src$year.json")
            if (text != null) break
        }
        val body = text ?: return@withContext "error"
        try {
            val json = JSONObject(body)
            val arr = json.optJSONArray("days") ?: JSONArray()
            if (arr.length() == 0) {
                p.edit().putString(key, JSONObject().put("fetchedAt", System.currentTimeMillis()).toString()).apply()
                return@withContext "empty"
            }
            val days = (0 until arr.length()).map {
                val d = arr.getJSONObject(it)
                HolidayDayEntry(d.getString("name"), LocalDate.parse(d.getString("date")), d.getBoolean("isOffDay"))
            }
            val list = Holidays.groupDays(days)
            val o = JSONObject()
                .put("fetchedAt", System.currentTimeMillis())
                .put("list", JSONArray(list.map { holidayToJson(it) }))
            p.edit().putString(key, o.toString()).apply()
            reloadFromCache(ctx)
            "ok"
        } catch (e: Exception) {
            "error"
        }
    }

    /** 打开 App 时顺手刷新今年和明年，有更新返回 true */
    suspend fun refreshOpportunistic(ctx: Context): Boolean {
        val y = LocalDate.now().year
        var changed = false
        for (year in listOf(y, y + 1)) if (refreshYear(ctx, year) == "ok") changed = true
        return changed
    }
}
