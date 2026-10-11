package app.birthdayreminder.privacy

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.birthdayreminder.ui.common.TileSheet
import app.birthdayreminder.ui.common.SheetItem
import app.birthdayreminder.ui.common.EventCard
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.birthdayreminder.ui.common.rememberScrollHideFab
import app.birthdayreminder.ui.common.HideOnScroll
import app.birthdayreminder.ui.common.SwipeTabs
import app.birthdayreminder.ui.common.AppTabs
import app.birthdayreminder.ui.common.TgFab
import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.CardListItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.data.CycleProfiles
import app.birthdayreminder.ui.cycle.CycleContent
import app.birthdayreminder.ui.cycle.rememberCycleViewModel
import app.birthdayreminder.ui.common.ModuleStore
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.data.DiaryEntity
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.domain.Occurrence
import app.birthdayreminder.domain.PrivacyText
import app.birthdayreminder.ui.diary.DiaryListViewModel
import app.birthdayreminder.ui.edit.BirthdayViewModel
import app.birthdayreminder.ui.edit.ReminderViewModel
import app.birthdayreminder.ui.phrase.PhraseViewModel
import app.birthdayreminder.ui.phrase.copyToClipboard
import java.time.LocalDate

private val TABS = listOf("日记", "提醒", "生日", "常用语", "健身", "过敏", "闹钟", "用药就医", "生理期")
/** 每个标签属于哪个功能（功能关掉了，隐私空间里也不显示） */
private val TAB_MODULES = listOf(Modules.DIARY, Modules.REMINDER, Modules.BIRTHDAY, Modules.PHRASE,
    Modules.FITNESS, Modules.ALLERGY, Modules.ALARM, Modules.MED, Modules.CYCLE)
private const val TAB_MED = 7
private const val TAB_CYCLE = 8
/** onNew 用的“新建就医记录”（和药品同在“用药就医”标签里，所以单独给个编号） */
const val PRIVACY_NEW_VISIT = 9

/**
 * 隐私空间：列出标记为私密的日记、提醒、生日、常用语、健身数据、过敏记录、闹钟、用药就医，
 * 以及隐私空间自己的一份生理期数据（和外面那份完全独立）。
 * 入口是长按日历顶部年月标题 + 验证身份；上锁（手动或离开 App 超过 1 分钟）后自动退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onExit: () -> Unit,
    onNew: (tab: Int) -> Unit,
    onEditDiary: (String) -> Unit,
    onEditReminder: (String) -> Unit,
    onEditBirthday: (String) -> Unit,
    onEditPhrase: (String) -> Unit,
    onEditFitness: (String) -> Unit = {},
    onEditAllergy: (String) -> Unit = {},
    onEditAlarm: (String) -> Unit = {},
    onEditMed: (String) -> Unit = {},
    onEditRecord: (String) -> Unit = {},
    fvm: app.birthdayreminder.ui.health.FitnessViewModel = viewModel(),
    avm: app.birthdayreminder.ui.health.AllergyViewModel = viewModel(),
    alvm: app.birthdayreminder.ui.alarm.AlarmViewModel = viewModel(),
    mvm: app.birthdayreminder.ui.med.MedViewModel = viewModel(),
    dvm: DiaryListViewModel = viewModel(),
    rvm: ReminderViewModel = viewModel(),
    bvm: BirthdayViewModel = viewModel(),
    pvm: PhraseViewModel = viewModel()
) {
    SecureWindow(true)
    val unlocked by PrivacySession.unlocked.collectAsState()
    LaunchedEffect(unlocked) { if (!unlocked) onExit() }

    val diaries by dvm.privateAll.collectAsState()
    val reminders by rvm.privateAll.collectAsState()
    val birthdays by bvm.privateAll.collectAsState()
    val phrases by pvm.privateAll.collectAsState()
    val fitness by fvm.privateAll.collectAsState()
    val allergy by avm.privateAll.collectAsState()
    val alarms by alvm.privateAlarms.collectAsState()
    val meds by mvm.privateMeds.collectAsState()
    val records by mvm.privateRecords.collectAsState()
    val cvm = rememberCycleViewModel(CycleProfiles.PRIVATE)
    val cycleDays by cvm.days.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(-1) }      // -1 = 全部
    var showAdd by remember { mutableStateOf<Int?>(null) }      // 打开的添加面板：-1 = 全部的，TAB_MED = 只有用药和就医记录
    val fabHide = rememberScrollHideFab()
    // 功能开关：隐私空间里也只显示还开着的功能
    val moduleVer by ModuleStore.version
    val ctxP = androidx.compose.ui.platform.LocalContext.current
    val visibleTabs = remember(moduleVer) {
        val off = ModuleStore.disabled(ctxP)
        TAB_MODULES.withIndex().filter { Modules.isOn(off, it.value, app.birthdayreminder.Features.allergy) }.map { it.index }
    }
    // 选中的项目被关掉了就回到“全部”（-1 = 全部，永远可用）
    LaunchedEffect(visibleTabs) { if (tab >= 0 && tab !in visibleTabs) tab = -1 }
    val today = remember { LocalDate.now() }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(fabHide.connection),      // 任何列表上下滑动时隐藏“+”
        topBar = {
            TgTopBar(
                title = { Text("🔒 " + PrivacyText.TITLE) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = { TextButton(onClick = { PrivacySession.lock() }) { Text("上锁") } }
            )
        },
        floatingActionButton = {
            HideOnScroll(fabHide) {
            // 私密内容只能在这里新建：建出来的自动是私密的（编辑页里没有任何开关，平时看不出有这个功能）
            // 和首页一样：“全部”时打开添加面板，具体项目时直接进那一项的添加页；
            // 用药就医里有药品和就医记录两种，让选一下；生理期有自己的记录按钮，不显示“+”
            if (tab != TAB_CYCLE) TgFab(onClick = { if (tab < 0 || tab == TAB_MED) showAdd = tab else onNew(tab) }) { Icon(Icons.Default.Add, contentDescription = "添加私密内容") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 所有私密内容统一成一张表：属于哪个标签、日期（没有就是 null）、类型、标题、说明、点开去哪
            val all = remember(diaries, reminders, birthdays, phrases, fitness, allergy, alarms, meds, records, cycleDays) {
                buildList {
                    diaries.forEach { e -> add(PItem(0, runCatching { LocalDate.parse(e.date) }.getOrNull(), "diary",
                        DiaryText.snippet(BlockConverter.fromJson(e.content), 60).ifBlank { "（只有图片）" }, null) { onEditDiary(e.id) }) }
                    reminders.forEach { r ->
                        val todo = Occurrence.isTodo(r)
                        val d = if (todo) null else Occurrence.nextReminder(r, today) ?: runCatching { LocalDate.parse(r.date) }.getOrNull()
                        val sub = if (todo) (if (Occurrence.todoDone(r)) "待办 · 已完成" else "待办") else "${r.time.ifBlank { "全天" }} · ${Occurrence.repeatLabel(r)}"
                        add(PItem(1, d, if (todo) "todo" else "reminder", r.title, sub) { onEditReminder(r.id) })
                    }
                    birthdays.forEach { b ->
                        val next = Occurrence.nextBirthday(b, today)
                        val age = next?.let { Occurrence.ageAt(b, it.first) }
                        add(PItem(2, next?.first, "birthday", b.name + (age?.let { " ${it}岁生日" } ?: "的生日"), Occurrence.birthdayDesc(b)) { onEditBirthday(b.id) })
                    }
                    phrases.forEach { ph -> add(PItem(3, null, "phrase", ph.title, ph.category.ifBlank { null }) { onEditPhrase(ph.id) }) }
                    fitness.forEach { l -> add(PItem(4, runCatching { LocalDate.parse(l.date) }.getOrNull(), "fitness",
                        app.birthdayreminder.domain.FitnessFields.summary(l).ifBlank { "健身记录" }, l.note.ifBlank { null }) { onEditFitness(l.date) }) }
                    allergy.forEach { l -> add(PItem(5, runCatching { LocalDate.parse(l.date) }.getOrNull(), "allergy",
                        "眼睛 ${l.eye} · 鼻子 ${l.nose} · 咳嗽 ${l.cough}", l.note.ifBlank { null }) { onEditAllergy(l.date) }) }
                    alarms.forEach { al -> add(PItem(6, null, "alarm", al.time + if (al.label.isNotBlank()) "  " + al.label else "",
                        app.birthdayreminder.domain.AlarmTriggers.repeatText(al) + if (al.enabled) "" else " · 已关闭") { onEditAlarm(al.id) }) }
                    meds.forEach { m -> add(PItem(TAB_MED, null, "med", m.name + if (m.dosage.isNotBlank()) " · " + m.dosage else "",
                        if (m.active) "提醒中" else "已停用") { onEditMed(m.id) }) }
                    records.forEach { r -> add(PItem(TAB_MED, runCatching { LocalDate.parse(r.date) }.getOrNull(), "followup", r.illness,
                        app.birthdayreminder.domain.MedicalLogic.where(r).ifBlank { null }) { onEditRecord(r.id) }) }
                    // 生理期：“全部”里按天列出记了的日子，点开跳到生理期标签
                    cycleDays.forEach { c -> add(PItem(TAB_CYCLE, runCatching { LocalDate.parse(c.date) }.getOrNull(), "cycle",
                        CycleLogic.daySummary(c).ifBlank { "生理期记录" }, null) { tab = TAB_CYCLE }) }
                }
            }
            // 标签：全部 + 各项（顺序和首页一样：日记、提醒、健身、闹钟、过敏、用药就医、生理期、常用语、生日；关掉的功能不显示）
            val order = listOf(0, 1, 4, 6, 5, TAB_MED, TAB_CYCLE, 3, 2).filter { it in visibleTabs }
            val pages = listOf(-1) + order
            val titles = pages.map { t -> when {
                t < 0 -> "全部 ${all.count { it.tab in order }}"
                t == TAB_CYCLE -> TABS[t]          // 生理期按天记，数字没意义
                else -> "${TABS[t]} ${all.count { it.tab == t }}"
            } }
            SwipeTabs(titles, pages.indexOf(tab).coerceAtLeast(0), { tab = pages[it] }, scrollableTabs = true, modifier = Modifier.fillMaxSize()) { page ->
                val t = pages[page]
                // 生理期：和外面同样的页面，只是用隐私空间自己的那份数据
                if (t == TAB_CYCLE) CycleContent(cvm)
                else LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    if (t < 0) {
                        item(key = "intro") {
                            Text("这里的内容不会出现在日历、列表、桌面小组件和 AI 助手里；在这里点“+”新建的都是私密的。",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        }
                        val shown = all.filter { it.tab in order }
                        if (shown.isEmpty()) item(key = "none") { Empty("还没有私密内容") }
                        // 有日期的按日期分组（新的在前），日期写在上面；没有日期的放最后“其它”
                        shown.filter { it.date != null }.groupBy { it.date!! }.toSortedMap(compareByDescending { it }).forEach { (d, list) ->
                            item(key = "d$d") {
                                Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                                    Text("${if (d.year != today.year) "${d.year}年" else ""}${d.monthValue}月${d.dayOfMonth}日 · " +
                                        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1] + if (d == today) " · 今天" else "",
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                                    list.forEach { x -> EventCard(x.kind, x.title, subtitle = x.sub, horizontalMargin = 0.dp, onClick = x.onClick) }
                                }
                            }
                        }
                        val undated = shown.filter { it.date == null }
                        if (undated.isNotEmpty()) item(key = "other") {
                            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                                Text("其它", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                                undated.forEach { x -> EventCard(x.kind, x.title, subtitle = x.sub, horizontalMargin = 0.dp, onClick = x.onClick) }
                            }
                        }
                    } else {
                        val list = all.filter { it.tab == t }.sortedWith(compareByDescending<PItem> { it.date ?: LocalDate.MIN })
                        if (list.isEmpty()) item(key = "none") { Empty("还没有私密的" + TABS[t]) }
                        items(list.size) { idx ->
                            val x = list[idx]
                            val dateText = x.date?.let { d -> "${if (d.year != today.year) "${d.year}年" else ""}${d.monthValue}月${d.dayOfMonth}日" }
                            EventCard(x.kind, x.title, subtitle = listOfNotNull(dateText, x.sub).joinToString(" · ").ifBlank { null }, onClick = x.onClick)
                        }
                    }
                }
            }
        }
    }
    showAdd?.let { which ->
        fun go(t: Int): () -> Unit = { showAdd = null; onNew(t) }
        fun item(t: Int, kind: String, name: String, module: Int = t) = if (module in visibleTabs) SheetItem(kind, name, go(t)) else null
        val medItems = listOfNotNull(item(TAB_MED, "med", "用药"), item(PRIVACY_NEW_VISIT, "followup", "就医记录", TAB_MED))
        TileSheet(
            groups = if (which == TAB_MED) listOf("用药就医" to medItems) else listOf(
                "记录" to listOfNotNull(item(0, "diary", "日记"), item(1, "reminder", "提醒"), item(4, "fitness", "健身数据")),
                "健康" to listOfNotNull(item(5, "allergy", "过敏记录")) + medItems +
                    listOfNotNull(if (TAB_CYCLE in visibleTabs) SheetItem("cycle", "生理期", { showAdd = null; tab = TAB_CYCLE }) else null),
                "生活" to listOfNotNull(item(6, "alarm", "闹钟"), item(2, "birthday", "生日")),
                "工具" to listOfNotNull(item(3, "phrase", "常用语"))
            ),
            container = MaterialTheme.colorScheme.surface,
            tile = app.birthdayreminder.ui.common.Tg.card,
            onDismiss = { showAdd = null }
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 隐私空间里的一条：属于哪个标签（TABS 的下标）、日期、类型（决定图标和颜色）、标题、说明、点开去哪 */
private class PItem(val tab: Int, val date: LocalDate?, val kind: String, val title: String, val sub: String?, val onClick: () -> Unit)
