package app.birthdayreminder.ui.list

import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.birthdayreminder.ui.common.rememberScrollHideFab
import app.birthdayreminder.ui.common.HideOnScroll
import app.birthdayreminder.ui.theme.LiColors
import app.birthdayreminder.ui.common.SwipeTabs
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import app.birthdayreminder.ui.common.TgSectionTitle
import app.birthdayreminder.ui.common.EventCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import app.birthdayreminder.ui.common.reminderKind
import app.birthdayreminder.ui.common.EventIcon
import app.birthdayreminder.ui.common.AppTabs
import androidx.compose.ui.text.font.FontWeight
import app.birthdayreminder.ui.common.TgAvatar
import app.birthdayreminder.ui.common.TgFab
import app.birthdayreminder.ui.common.TgTopBar
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.ui.common.ModuleStore
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.Features
import app.birthdayreminder.domain.Occurrence
import app.birthdayreminder.domain.OpenTarget
import app.birthdayreminder.ui.alarm.AlarmListContent
import app.birthdayreminder.ui.diary.DiaryListContent
import app.birthdayreminder.ui.edit.BirthdayViewModel
import app.birthdayreminder.ui.edit.ReminderViewModel
import app.birthdayreminder.ui.health.AllergyListContent
import app.birthdayreminder.ui.health.FitnessListContent
import app.birthdayreminder.ui.phrase.PhraseListContent
import app.birthdayreminder.ui.med.MedContent
import app.birthdayreminder.ui.cycle.CycleContent
import java.time.LocalDate

private data class BirthdayRow(val birthday: Birthday, val next: LocalDate?, val days: Int?)
private data class ReminderRow(val reminder: Reminder, val next: LocalDate?)

/** 标签名，按 OpenTarget 里的 TAB_xxx 编号：日记、提醒、健身数据、闹钟、过敏记录、常用语、生日（无过敏记录版不显示“过敏记录”） */
private val TAB_NAMES = listOf("日记", "提醒", "健身数据", "闹钟", "过敏记录", "用药就医", "生理期", "常用语", "生日", "学习")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    initialTab: Int = 0,
    onBack: () -> Unit,
    onNewDiary: () -> Unit,
    onNewReminder: () -> Unit,
    onNewFitness: () -> Unit,
    onNewAlarm: () -> Unit,
    onNewAllergy: () -> Unit,
    onNewMed: () -> Unit,
    onNewPhrase: () -> Unit,
    onNewBirthday: () -> Unit,
    onEditDiary: (String) -> Unit,
    onEditReminder: (String) -> Unit,
    onEditFitness: (String) -> Unit,
    onEditAlarm: (String) -> Unit,
    onEditAllergy: (String) -> Unit,
    onEditMed: (String) -> Unit,
    onEditRecord: (String) -> Unit,
    onEditPhrase: (String) -> Unit,
    onEditBirthday: (String) -> Unit,
    // 首页底部导航的一个区域只显示其中几个标签（不传就是全部标签）
    allowedTabs: List<Int>? = null,
    title: String = "全部",
    showBack: Boolean = true,
    bvm: BirthdayViewModel = viewModel(),
    rvm: ReminderViewModel = viewModel()
) {
    val birthdays by bvm.all.collectAsState()
    val reminders by rvm.all.collectAsState()
    // 功能开关：关掉的功能不出现标签
    val moduleVer by ModuleStore.version
    val ctx = LocalContext.current
    val fabHide = rememberScrollHideFab()      // 任何列表上下滑动时隐藏“+”
    val tabIds = remember(moduleVer, allowedTabs) {
        Modules.tabs(ModuleStore.disabled(ctx), Features.allergy).filter { allowedTabs == null || it in allowedTabs }
            .let { l -> if (allowedTabs == null) l else allowedTabs.filter { it in l } }      // 按区域里规定的顺序
    }
    // tab 存的是标签编号（TAB_xxx），不是第几个位置
    var tab by remember { mutableIntStateOf(if (initialTab in tabIds) initialTab else tabIds.firstOrNull() ?: OpenTarget.TAB_DIARY) }
    // 当前标签的功能被关掉了：换到第一个还开着的
    LaunchedEffect(tabIds) { if (tabIds.isNotEmpty() && tab !in tabIds) tab = tabIds.first() }


    Scaffold(
        modifier = Modifier.nestedScroll(fabHide.connection),      // 任何列表上下滑动时隐藏“+”
        topBar = {
            TgTopBar(
                title = { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                actions = {
                    // 常用语不用右下角的大按钮，添加放在右上角
                    if (tab == OpenTarget.TAB_PHRASE) IconButton(onClick = onNewPhrase) { Icon(Icons.Default.Add, contentDescription = "添加常用语") }
                }
            )
        },
        floatingActionButton = {
            HideOnScroll(fabHide) {
            // 按页面区分：生理期有自己的按钮、常用语在顶栏右上角，都不需要；健身是“＋ 记录”；闹钟用小号
            if (tabIds.isNotEmpty() && tab == OpenTarget.TAB_FITNESS) {
                androidx.compose.material3.ExtendedFloatingActionButton(onClick = onNewFitness, shape = RoundedCornerShape(20.dp),
                    containerColor = app.birthdayreminder.ui.common.tgFabColor(), contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("记录") })
            } else if (tabIds.isNotEmpty() && tab == OpenTarget.TAB_ALARM) {
                androidx.compose.material3.SmallFloatingActionButton(onClick = onNewAlarm, shape = RoundedCornerShape(14.dp),
                    containerColor = app.birthdayreminder.ui.common.tgFabColor(), contentColor = Color.White) { Icon(Icons.Default.Add, contentDescription = "添加闹钟") }
            } else if (tabIds.isNotEmpty() && tab != OpenTarget.TAB_CYCLE && tab != OpenTarget.TAB_PHRASE) TgFab(onClick = {
                when (tab) {
                    OpenTarget.TAB_DIARY -> onNewDiary()
                    OpenTarget.TAB_REMINDER -> onNewReminder()
                    OpenTarget.TAB_FITNESS -> onNewFitness()
                    OpenTarget.TAB_ALARM -> onNewAlarm()
                    OpenTarget.TAB_ALLERGY -> onNewAllergy()
                    OpenTarget.TAB_MED -> onNewMed()
                    OpenTarget.TAB_PHRASE -> onNewPhrase()
                    OpenTarget.TAB_STUDY -> app.birthdayreminder.ui.study.StudyUi.requestNew(LocalDate.now())
                    else -> onNewBirthday()
                }
            }) { Icon(Icons.Default.Add, contentDescription = "添加") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (tabIds.isEmpty()) {
                EmptyHint("所有功能都关掉了，可以在设置 → 功能开关里打开")
                return@Column
            }
            // 标签和页面同步，可以左右滑动切换项目
            SwipeTabs(
                titles = tabIds.map { i ->
                    val name = TAB_NAMES[i]
                    when (i) {
                        OpenTarget.TAB_REMINDER -> "$name ${reminders.size}"
                        OpenTarget.TAB_BIRTHDAY -> "$name ${birthdays.size}"
                        else -> name
                    }
                },
                selected = tabIds.indexOf(tab).coerceAtLeast(0),
                onSelectedChange = { tab = tabIds[it] },
                scrollableTabs = true,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                DataTabContent(
                    tab = tabIds[page],
                    onEditDiary = onEditDiary, onEditReminder = onEditReminder, onEditFitness = onEditFitness, onEditAlarm = onEditAlarm,
                    onEditAllergy = onEditAllergy, onEditMed = onEditMed, onEditRecord = onEditRecord, onEditPhrase = onEditPhrase,
                    onEditBirthday = onEditBirthday, bvm = bvm, rvm = rvm
                )
            }
        
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    TgSectionTitle(text)
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * “数据”里某一项的内容（日记、提醒、生日、健身、闹钟、过敏、用药就医、生理期、常用语）。
 * 数据页的每个标签、首页日程区域的标签都用它，保证两边内容一样。
 */
@Composable
fun DataTabContent(
    tab: Int,
    onEditDiary: (String) -> Unit,
    onEditReminder: (String) -> Unit,
    onEditFitness: (String) -> Unit,
    onEditAlarm: (String) -> Unit,
    onEditAllergy: (String) -> Unit,
    onEditMed: (String) -> Unit,
    onEditRecord: (String) -> Unit,
    onEditPhrase: (String) -> Unit,
    onEditBirthday: (String) -> Unit,
    selectedDate: LocalDate? = null,      // 首页月历上选中的日子（生理期“今天”页用来改那一天）
    bvm: BirthdayViewModel = viewModel(),
    rvm: ReminderViewModel = viewModel()
) {
    val birthdays by bvm.all.collectAsState()
    val reminders by rvm.all.collectAsState()
    val bRows = remember(birthdays) {
        val today = LocalDate.now()
        birthdays.map {
            val n = Occurrence.nextBirthday(it, today)
            BirthdayRow(it, n?.first, n?.second)
        }.sortedBy { it.days ?: Int.MAX_VALUE }
    }
    var showDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 待办：没做完的在前（按标题），做完的在后；有日期的提醒按下一次日期
    val todos = remember(reminders) {
        reminders.filter { Occurrence.isTodo(it) }.sortedWith(compareBy({ Occurrence.todoDone(it) }, { it.title }))
    }
    val rRows = remember(reminders) {
        val today = LocalDate.now()
        reminders.filter { !Occurrence.isTodo(it) }.map { ReminderRow(it, Occurrence.nextReminder(it, today)) }
            .sortedBy { it.next?.toEpochDay() ?: Long.MAX_VALUE }
    }
    when (tab) {
            OpenTarget.TAB_DIARY -> DiaryListContent(onEdit = onEditDiary)
            OpenTarget.TAB_FITNESS -> FitnessListContent(onEdit = onEditFitness)
            OpenTarget.TAB_ALARM -> AlarmListContent(onEdit = onEditAlarm)
            OpenTarget.TAB_ALLERGY -> AllergyListContent(onEdit = onEditAllergy)
            OpenTarget.TAB_MED -> MedContent(onEdit = onEditMed, onEditRecord = onEditRecord)
            OpenTarget.TAB_CYCLE -> CycleContent(selected = selectedDate)
            OpenTarget.TAB_STUDY -> app.birthdayreminder.ui.study.StudyContent()
            OpenTarget.TAB_PHRASE -> PhraseListContent(onEdit = onEditPhrase)
            OpenTarget.TAB_BIRTHDAY -> LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                if (bRows.isEmpty()) item { EmptyHint("还没有生日，点右下角“+”添加") }
                items(bRows, key = { it.birthday.id }) { row ->
                    CardListItem(
                        leadingContent = { TgAvatar(row.birthday.name) },
                        headlineContent = { Text(row.birthday.name, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(Occurrence.birthdayDesc(row.birthday), fontSize = 13.sp) },
                        trailingContent = {
                            val d = row.days
                            val n = row.next
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    when {
                                        d == null -> "—"
                                        d == 0 -> "今天"
                                        else -> "还有${d}天"
                                    },
                                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    // 只有今天和一周内才用主色，其余用正文色，不让蓝色铺满
                                    color = if (d != null && d <= 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (n != null) Text("${n.monthValue}月${n.dayOfMonth}日", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.clickable { onEditBirthday(row.birthday.id) }
                    )
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                if (todos.isEmpty() && rRows.isEmpty()) item { EmptyHint("还没有提醒，点右下角“+”添加；不需要日期的事可以选“无时间待办”") }
                if (todos.isNotEmpty()) item { SectionTitle("待办") }
                // 做完的收成一行“已完成（N）”，点开再看：不然划了线的和没做的一样大，挤在一起
                val openTodos = todos.filter { !Occurrence.todoDone(it) }
                val doneTodos = todos.filter { Occurrence.todoDone(it) }
                if (todos.isNotEmpty() && openTodos.isEmpty()) item { EmptyHint("待办都做完了") }
                items(openTodos + (if (showDone) doneTodos else emptyList()), key = { "t" + it.id }) { r ->
                    val done = Occurrence.todoDone(r)
                    EventCard("todo", r.title, subtitle = r.note.ifBlank { null }, strike = done,
                        leading = { Checkbox(checked = done, onCheckedChange = { rvm.toggleTodo(r) }, modifier = Modifier.size(36.dp)) },
                        onClick = { onEditReminder(r.id) })
                }
                if (doneTodos.isNotEmpty()) item(key = "doneToggle") {
                    TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(start = 8.dp)) {
                        Text(if (showDone) "收起已完成" else "已完成（${doneTodos.size}）›")
                    }
                }
                if (todos.isNotEmpty() && rRows.isNotEmpty()) item { SectionTitle("已安排") }
                items(rRows, key = { it.reminder.id }) { row ->
                    val r = row.reminder
                    CardListItem(
                        leadingContent = { EventIcon(reminderKind(r.title), size = 40.dp, tint = LiColors.reminder) },      // 形状按内容，颜色统一紫色
                        overlineContent = {
                            // 时间做视觉锚点：一眼看到什么时候
                            Text(r.time.ifBlank { "全天" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = if (row.next != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        headlineContent = { Text(r.title, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            val n = row.next
                            val whenText = if (n != null) "${n.monthValue}月${n.dayOfMonth}日" else "已结束"
                            Text("$whenText · ${Occurrence.repeatLabel(r)}", fontSize = 13.sp)
                        },
                        modifier = Modifier.clickable { onEditReminder(r.id) }
                    )
                }
            }
        }
}
