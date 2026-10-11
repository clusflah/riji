package app.birthdayreminder.ui.assistant

import app.birthdayreminder.domain.Modules
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birthdayreminder.AlarmEngine
import app.birthdayreminder.ai.AiChatStore
import app.birthdayreminder.ai.AiClient
import app.birthdayreminder.ai.AiProviders
import app.birthdayreminder.ai.AiTools
import app.birthdayreminder.ai.ChatMsg
import app.birthdayreminder.ai.ToolCall
import app.birthdayreminder.ai.ToolOutcome
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.data.ContentBlock
import app.birthdayreminder.data.DiaryEntity
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.domain.TableOps
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.notify.ReminderScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import app.birthdayreminder.Features
import app.birthdayreminder.ai.AzureTts
import app.birthdayreminder.ai.TtsPlayer
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val chat = mutableStateListOf<ChatMsg>().also { it.addAll(AiChatStore.fromJson(Prefs.aiChatJson(app))) }
    var busy by mutableStateOf(false); private set
    var provider by mutableStateOf(Prefs.aiProvider(app)); private set

    fun changeProvider(p: String) { provider = p; Prefs.setAiProvider(getApplication(), p) }

    /** 等用户确认的添加操作（界面据此显示确认卡片）；为 null 表示没有在等 */
    var pendingConfirm by mutableStateOf<List<String>?>(null); private set
    private var confirmAnswer: CompletableDeferred<Boolean>? = null

    /** 确认卡片上点了“确认”或“取消” */
    fun answerConfirm(ok: Boolean) {
        pendingConfirm = null
        confirmAnswer?.complete(ok)
        confirmAnswer = null
    }

    /** “本周总结 / 本月总结”按钮：发给 AI 的是完整要求，对话里只显示短标题 */
    fun report(period: String) {
        send(AiTools.reportPrompt(period), display = if (period == "this_month") "📊 本月总结" else "📊 本周总结")
    }

    private fun persist() {
        val trimmed = AiChatStore.trim(chat.toList())
        if (trimmed.size != chat.size) { chat.clear(); chat.addAll(trimmed) }
        Prefs.setAiChatJson(getApplication(), AiChatStore.toJson(chat.toList()))
    }

    fun clear() { answerConfirm(false); TtsPlayer.stop(); chat.clear(); persist() }

    override fun onCleared() { TtsPlayer.stop(); super.onCleared() }

    /** 这一轮“AI 说了什么、做了什么”，界面按这个画（工具结果那条不单独显示，并进 assistant 那条下面） */
    fun toolResultFor(callId: String): Any? = chat.firstOrNull { it.role == "tool" && it.callId == callId }?.result

    /** 第几条消息的朗读标识（气泡上的 🔊 和自动朗读用同一个，方便显示“正在念哪条”） */
    fun ttsKey(index: Int) = "msg$index"

    fun send(text: String, display: String? = null, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val app = getApplication<Application>()
        val p = provider
        val key = Prefs.aiKey(app, p)
        chat.add(ChatMsg("user", t, display = display))
        if (key.isBlank()) {
            chat.add(ChatMsg("assistant", "还没有填 ${AiProviders.LABEL[p]} 的 API Key，请点右上角“设置”填写。"))
            persist(); return
        }
        busy = true
        persist()
        viewModelScope.launch {
            val model = Prefs.aiModel(app, p, AiProviders.DEFAULT_MODEL.getValue(p))
            val url = Prefs.aiUrl(app, p, AiProviders.DEFAULT_URL.getValue(p))
            // 关掉的功能，AI 也不能查、不能加
            val off = Prefs.disabledModules(app)
            val tools = AiTools.specs(true, Features.allergy).filter { Modules.toolAllowed(off, it.name) }
            var rounds = 0
            try {
                while (true) {
                    rounds++
                    if (rounds > 5) {
                        chat.add(ChatMsg("assistant", "（已达到本次对话的工具调用上限，请换个说法再试一次）"))
                        break
                    }
                    val reply = AiClient.call(p, key, model, url, AiChatStore.sanitize(chat.toList()), tools, AiTools.systemPrompt(LocalDate.now()))
                    chat.add(ChatMsg("assistant", reply.text, reply.toolCalls))
                    if (reply.toolCalls.isEmpty()) {
                        // 最后的回复：按设置自动朗读（默认“语音提问时”才念）
                        if (reply.text.isNotBlank() && AzureTts.shouldAutoSpeak(Prefs.ttsAuto(app), viaVoice)) {
                            TtsPlayer.speak(app, ttsKey(chat.size - 1), reply.text) { msg -> chat.add(ChatMsg("assistant", "（$msg）")) }
                        }
                        break
                    }
                    // 要添加数据的：先在对话里弹出确认卡片，等用户点“确认”才执行；查询类直接执行
                    val writes = reply.toolCalls.filter { AiTools.needsConfirm(it.name) }
                    val approved = if (writes.isEmpty() || !Prefs.aiConfirm(app)) true else {
                        persist()
                        val d = CompletableDeferred<Boolean>()
                        confirmAnswer = d
                        pendingConfirm = writes.map { AiTools.preview(it) }
                        d.await()
                    }
                    for (c in reply.toolCalls) {
                        val result = if (AiTools.needsConfirm(c.name) && !approved) mapOf("error" to "用户取消了这个操作，没有添加")
                        else runTool(c)
                        chat.add(ChatMsg("tool", callId = c.id, name = c.name, result = result))
                    }
                    persist()
                }
            } catch (e: Exception) {
                chat.add(ChatMsg("assistant", AiClient.errorText(e)))
            } finally {
                busy = false
                pendingConfirm = null
                confirmAnswer = null
                persist()
            }
        }
    }

    /** 执行一个工具：校验参数 -> 写数据库 -> 重新排通知/闹钟 -> 把结果回给 AI。小组件会自己跟着数据刷新。 */
    private suspend fun runTool(c: ToolCall): Any? {
        val app = getApplication<Application>()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val a = c.args
        return when (c.name) {
            "add_birthday" -> when (val o = AiTools.addBirthday(a, today)) {
                is ToolOutcome.AddBirthday -> { db.birthdayDao().upsert(o.item); ReminderScheduler.reschedule(app); o.result }
                is ToolOutcome.Error -> o.result
                else -> null
            }
            "add_reminder" -> when (val o = AiTools.addReminder(a, today)) {
                is ToolOutcome.AddReminder -> { db.reminderDao().upsert(o.item); ReminderScheduler.reschedule(app); o.result }
                is ToolOutcome.Error -> o.result
                else -> null
            }
            "add_alarm" -> when (val o = AiTools.addAlarm(a, System.currentTimeMillis(), zone)) {
                is ToolOutcome.AddAlarm -> { db.alarmDao().upsert(o.item); AlarmEngine.sync(app); o.result }
                is ToolOutcome.Error -> o.result
                else -> null
            }
            "add_diary" -> when (val o = AiTools.addDiary(a, today)) {
                is ToolOutcome.AddDiary -> {
                    // AI 写的 Markdown：标题、列表、待办变成对应格式，表格变成表格块
                    val blocks = TableOps.blocksFromMarkdown(o.item.text).ifEmpty { listOf<ContentBlock>(ContentBlock.TextBlock(text = o.item.text)) }
                    db.diaryDao().upsert(
                        DiaryEntity(id = UUID.randomUUID().toString(), date = o.item.date,
                            content = BlockConverter.toJson(blocks), updatedAt = System.currentTimeMillis())
                    )
                    o.result
                }
                is ToolOutcome.Error -> o.result
                else -> null
            }
            "list_birthdays" -> AiTools.listBirthdays(db.birthdayDao().getAll(), today).result
            "list_reminders" -> AiTools.listReminders(db.reminderDao().getAll(), a, today).result
            "list_alarms" -> AiTools.listAlarms(db.alarmDao().getAll().filter { !it.isPrivate }).result
            "query_fitness" -> AiTools.queryFitness(db.fitnessDao().getAll().filter { !it.isPrivate }, a, today).result
            "query_allergy" -> if (Features.allergy) AiTools.queryAllergy(db.allergyDao().getAll().filter { !it.isPrivate }, a, today).result else mapOf("error" to "这个版本没有过敏记录")
            "period_data" -> AiTools.periodData(
                (a["period"] as? String) ?: "this_week", today,
                db.diaryDao().getAll().filter { !it.isPrivate }.map { it.date to DiaryText.plainText(BlockConverter.fromJson(it.content)) },
                db.fitnessDao().getAll().filter { !it.isPrivate },
                db.reminderDao().getAll(),
                if (Features.allergy) db.allergyDao().getAll().filter { !it.isPrivate } else null
            ).result
            "list_diary" -> {
                val entries = db.diaryDao().getAll().filter { !it.isPrivate }     // 私密日记不给 AI 看
                    .sortedWith(compareByDescending<DiaryEntity> { it.date }.thenByDescending { it.updatedAt })
                    .map { it.date to DiaryText.plainText(BlockConverter.fromJson(it.content)).take(2000) }
                AiTools.listDiary(entries, a).result
            }
            else -> mapOf("error" to "未知工具 ${c.name}")
        }
    }
}
