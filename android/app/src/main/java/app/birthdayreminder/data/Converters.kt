package app.birthdayreminder.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room 存储 List<Int> / Map<String,Boolean> 这类复合字段用的转换器，
 * 和 JS 版 S.data 里 birthday.advance / reminder.done 的结构对应，
 * 方便阶段8做数据迁移时字段能直接对上。
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun intListToJson(list: List<Int>?): String = gson.toJson(list ?: emptyList<Int>())

    @TypeConverter
    fun jsonToIntList(json: String?): List<Int> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Int>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    @TypeConverter
    fun boolMapToJson(map: Map<String, Boolean>?): String = gson.toJson(map ?: emptyMap<String, Boolean>())

    @TypeConverter
    fun jsonToBoolMap(json: String?): Map<String, Boolean> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<String, Boolean>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyMap() }
    }

    // 健身数据：项目键 -> 数值
    @TypeConverter
    fun doubleMapToJson(map: Map<String, Double>?): String = gson.toJson(map ?: emptyMap<String, Double>())

    @TypeConverter
    fun jsonToDoubleMap(json: String?): Map<String, Double> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = object : TypeToken<Map<String, Double>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyMap() }
    }
}
