package app.birthdayreminder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 对应 JS 版 S.data.diary。content 存的是块结构 JSON（见 ContentBlock），
 * location 为空表示没有加位置。字段设计特意和当前 JS 版尽量对齐，
 * 阶段8做旧数据迁移时可以少踩坑。
 */
@Entity(tableName = "diary")
@TypeConverters(Converters::class)
data class DiaryEntity(
    @PrimaryKey val id: String,
    val date: String,           // yyyy-MM-dd
    val content: String = "[]", // ContentBlock 列表的 JSON
    val updatedAt: Long = System.currentTimeMillis(),
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationAddress: String? = null,
    /** 私密：只在隐私空间里显示（日历、列表、小组件、AI 助手都看不到） */
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false
)

private data class BlockDto(
    val id: String,
    val type: String,
    val text: String? = null,
    val imagePath: String? = null,
    val filePath: String? = null,
    val fileName: String? = null,
    val rows: List<List<String>>? = null
)

object BlockConverter {
    private val gson = Gson()

    fun toJson(blocks: List<ContentBlock>): String {
        val dtos = blocks.map {
            when (it) {
                is ContentBlock.TextBlock -> BlockDto(it.id, "text", text = it.text)
                is ContentBlock.ImageBlock -> BlockDto(it.id, "image", imagePath = it.imagePath)
                is ContentBlock.FileBlock -> BlockDto(it.id, "file", filePath = it.path, fileName = it.name)
                is ContentBlock.TableBlock -> BlockDto(it.id, "table", rows = it.rows)
            }
        }
        return gson.toJson(dtos)
    }

    fun fromJson(json: String?): MutableList<ContentBlock> {
        if (json.isNullOrBlank()) {
            return mutableListOf(ContentBlock.TextBlock(text = ""))
        }
        val type = object : TypeToken<List<BlockDto>>() {}.type
        val dtos: List<BlockDto> = try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
        if (dtos.isEmpty()) return mutableListOf(ContentBlock.TextBlock(text = ""))
        return dtos.map {
            when {
                it.type == "image" && it.imagePath != null -> ContentBlock.ImageBlock(it.id, it.imagePath)
                it.type == "file" && it.filePath != null -> ContentBlock.FileBlock(it.id, it.filePath, it.fileName ?: "附件")
                it.type == "table" && !it.rows.isNullOrEmpty() -> ContentBlock.TableBlock(it.id, it.rows)
                else -> ContentBlock.TextBlock(it.id, it.text ?: "")
            }
        }.toMutableList()
    }
}
