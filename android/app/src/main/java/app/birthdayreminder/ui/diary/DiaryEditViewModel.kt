package app.birthdayreminder.ui.diary

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.ai.AiClient
import app.birthdayreminder.ai.AiProviders
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.data.ContentBlock
import app.birthdayreminder.data.DiaryEntity
import app.birthdayreminder.data.DiaryLocation
import app.birthdayreminder.data.ImageStorage
import app.birthdayreminder.data.FileStorage
import app.birthdayreminder.domain.RichText
import app.birthdayreminder.domain.TableOps
import app.birthdayreminder.domain.BlockOps
import app.birthdayreminder.domain.EditResult
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * 编辑一篇日记时用到的全部状态，专门用 Navigation Compose 的
 * “父路由 ViewModel”方式在“编辑页 <-> 选点页”之间共享——
 * 不然跳去选完位置回来，正文会被重置成初始状态。
 */
class DiaryEditViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val blocks = mutableStateListOf<ContentBlock>(ContentBlock.TextBlock(text = ""))
    var existingId: String? = null; private set
    var date: LocalDate by mutableStateOf(LocalDate.now())
    var location: DiaryLocation? by mutableStateOf(null)
    var focusedId: String? by mutableStateOf(null)
    var focusedCursor: Int by mutableStateOf(0)
    var loaded: Boolean by mutableStateOf(false); private set
    var isPrivate: Boolean by mutableStateOf(false)

    // ---- AI 润色（用 Gemini，Key 和模型在“AI 助手”的设置里填） ----
    var polishing: Boolean by mutableStateOf(false); private set
    var polishResult: String? by mutableStateOf(null)
    var polishError: String? by mutableStateOf(null)
    private var polishBlockId: String? = null

    fun startPolish() {
        val ctx = getApplication<Application>()
        val key = Prefs.aiKey(ctx, "gemini")
        if (key.isBlank()) { polishError = "请先在“AI 助手 → 设置”里选 Gemini，并填写 API Key"; return }
        // 润色当前正在写的那一段；没点过输入框就取第一段有字的
        val target = (blocks.firstOrNull { it.id == focusedId && it is ContentBlock.TextBlock && it.text.isNotBlank() }
            ?: blocks.firstOrNull { it is ContentBlock.TextBlock && it.text.isNotBlank() }) as? ContentBlock.TextBlock
        if (target == null) { polishError = "先写点内容再润色"; return }
        polishBlockId = target.id
        polishing = true
        viewModelScope.launch {
            try {
                polishResult = AiClient.polishDiary(
                    key,
                    Prefs.aiModel(ctx, "gemini", AiProviders.DEFAULT_MODEL.getValue("gemini")),
                    Prefs.aiUrl(ctx, "gemini", AiProviders.DEFAULT_URL.getValue("gemini")),
                    RichText.toPlain(target.text)      // 发给 AI 的是好读的文字（不带隐藏的格式标记）
                )
            } catch (e: Exception) {
                polishError = AiClient.errorText(e)
            } finally {
                polishing = false
            }
        }
    }

    /** 用户确认后，把润色结果替换回原来那一段 */
    fun applyPolish() {
        val r = RichText.fromMarkdown(polishResult ?: return)
        val idx = blocks.indexOfFirst { it.id == polishBlockId }
        val target = blocks.getOrNull(idx) as? ContentBlock.TextBlock
        if (target != null) blocks[idx] = target.copy(text = r)
        polishResult = null
    }

    fun dismissPolish() { polishResult = null; polishError = null }

    private var initializedFor: String? = null

    /** 幂等：同一个编辑会话内被多次调用（比如从选点页返回时重组）也不会重新加载覆盖用户输入 */
    fun ensureLoaded(id: String?, fallbackDate: LocalDate, initialPrivate: Boolean = false) {
        val key = id ?: "new:$fallbackDate"
        if (initializedFor == key) return
        initializedFor = key
        date = fallbackDate

        if (id == null) {
            existingId = null
            blocks.clear(); blocks.add(ContentBlock.TextBlock(text = ""))
            location = null
            isPrivate = initialPrivate
            focusedId = blocks.first().id
            loaded = true
            return
        }
        viewModelScope.launch {
            db.diaryDao().getById(id)?.let { e ->
                existingId = e.id
                isPrivate = e.isPrivate
                // 以前用 Markdown 写的（或上一版转成的 ☐ • 纯文字）：打开时变成对应的格式，表格变成表格块；已经带格式的不动
                val parsed = BlockOps.ensureTrailingText(BlockConverter.fromJson(e.content).flatMap { b ->
                    if (b is ContentBlock.TextBlock && !RichText.hasMarks(b.text)) {
                        val conv = TableOps.blocksFromMarkdown(b.text)
                        if (conv.size == 1 && conv[0] is ContentBlock.TextBlock) listOf(b.copy(text = (conv[0] as ContentBlock.TextBlock).text))
                        else if (conv.isEmpty()) listOf(b) else conv
                    } else listOf(b)
                })
                blocks.clear(); blocks.addAll(parsed)
                focusedId = parsed.lastOrNull()?.id
                date = try { LocalDate.parse(e.date) } catch (ex: Exception) { fallbackDate }
                location = if (e.locationLat != null && e.locationLng != null) {
                    DiaryLocation(e.locationLat, e.locationLng, e.locationAddress ?: "")
                } else null
            }
            loaded = true
        }
    }

    fun mutateFocused(edit: (text: String, cursor: Int) -> EditResult) {
        val idx = blocks.indexOfFirst { it.id == focusedId }
        val target = blocks.getOrNull(idx) as? ContentBlock.TextBlock ?: return
        val result = edit(target.text, focusedCursor.coerceIn(0, target.text.length))
        blocks[idx] = target.copy(text = result.text)
        focusedCursor = result.selEnd
    }

    fun updateBlockText(index: Int, block: ContentBlock.TextBlock, newText: String, cursor: Int, selEnd: Int = cursor) {
        if (index !in blocks.indices || blocks[index].id != block.id) return
        blocks[index] = block.copy(text = newText)
        focusedId = block.id
        focusedCursor = cursor
        focusedSelEnd = selEnd
    }

    /** 选区的另一端（工具栏“划线”、多行设格式要用）；没选中文字时和光标一样 */
    var focusedSelEnd: Int by mutableStateOf(0)

    /** 工具栏改格式：作用在当前段落的选区 / 光标所在行 */
    fun mutateRich(edit: (text: String, selStart: Int, selEnd: Int) -> RichText.EditResult) {
        val idx = blocks.indexOfFirst { it.id == focusedId }
        val target = blocks.getOrNull(idx) as? ContentBlock.TextBlock ?: return
        val len = target.text.length
        val r = edit(target.text, focusedCursor.coerceIn(0, len), focusedSelEnd.coerceIn(0, len))
        blocks[idx] = target.copy(text = r.text)
        focusedCursor = r.selStart
        focusedSelEnd = r.selEnd
        richTick++
    }

    /** 工具栏改了文字：编辑框据此把文字和选区同步过去 */
    var richTick: Int by mutableStateOf(0)

    /** 在光标处插入表格 */
    fun insertTable() {
        val r = BlockOps.insertTable(blocks.toList(), focusedId, focusedCursor)
        blocks.clear(); blocks.addAll(r.blocks)
        focusedId = r.focusId
        focusedCursor = 0; focusedSelEnd = 0
    }

    /** 按 id 改一段文字（点方框勾选时用：不依赖它现在排第几） */
    fun updateTextById(blockId: String, newText: String, selStart: Int, selEnd: Int) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val b = blocks.getOrNull(idx) as? ContentBlock.TextBlock ?: return
        blocks[idx] = b.copy(text = newText)
        focusedId = blockId; focusedCursor = selStart; focusedSelEnd = selEnd
    }

    /** 删除表格（前后文字合并），不涉及任何文件 */
    fun removeTable(blockId: String) {
        val newBlocks = BlockOps.removeImage(blocks.toList(), blockId)
        blocks.clear(); blocks.addAll(newBlocks)
    }

    fun updateTable(blockId: String, rows: List<List<String>>) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        val b = blocks.getOrNull(idx) as? ContentBlock.TableBlock ?: return
        blocks[idx] = b.copy(rows = rows)
    }

    fun insertImage(path: String) {
        val r = BlockOps.insertImage(blocks.toList(), focusedId, focusedCursor, path)
        blocks.clear(); blocks.addAll(r.blocks)
        focusedId = r.focusId
        focusedCursor = 0
    }

    fun removeImage(imageId: String, path: String) {
        ImageStorage.delete(getApplication(), path)
        val newBlocks = BlockOps.removeImage(blocks.toList(), imageId)
        blocks.clear(); blocks.addAll(newBlocks)
    }

    /** 在光标处插入附件（先复制进 App 私有目录）；失败返回原因 */
    fun insertFile(uri: android.net.Uri): String? {
        return when (val r = FileStorage.saveFromUri(getApplication(), uri)) {
            is FileStorage.Saved.Fail -> r.message
            is FileStorage.Saved.Ok -> {
                val res = BlockOps.insertFile(blocks.toList(), focusedId, focusedCursor, r.path, r.name)
                blocks.clear(); blocks.addAll(res.blocks)
                focusedId = res.focusId
                focusedCursor = 0
                null
            }
        }
    }

    fun removeFile(blockId: String, path: String) {
        FileStorage.delete(getApplication(), path)
        val newBlocks = BlockOps.removeImage(blocks.toList(), blockId)
        blocks.clear(); blocks.addAll(newBlocks)
    }


    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val trimmed = BlockOps.trimForSave(blocks)
            val entity = DiaryEntity(
                id = existingId ?: UUID.randomUUID().toString(),
                date = date.toString(),
                content = BlockConverter.toJson(trimmed),
                updatedAt = System.currentTimeMillis(),
                locationLat = location?.lat,
                locationLng = location?.lng,
                locationAddress = location?.address,
                isPrivate = isPrivate
            )
            db.diaryDao().upsert(entity)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val eid = existingId
        if (eid == null) { onDone(); return }
        viewModelScope.launch {
            db.diaryDao().getById(eid)?.let { e ->
                val bl = BlockConverter.fromJson(e.content)
                BlockOps.imagePaths(bl).forEach { ImageStorage.delete(getApplication(), it) }
                BlockOps.filePaths(bl).forEach { FileStorage.delete(getApplication(), it) }
                db.diaryDao().delete(e)
            }
            onDone()
        }
    }
}
