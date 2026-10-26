package app.birthdayreminder.ui.calendar

import app.birthdayreminder.ui.common.TileSheet
import app.birthdayreminder.ui.common.HomeTabs
import app.birthdayreminder.ui.common.TgFabLabeled
import app.birthdayreminder.notify.Reliability
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import app.birthdayreminder.ui.common.SheetItem
import app.birthdayreminder.ui.common.SwipeTabs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.KeyboardArrowUp
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.ui.list.DataTabContent
import app.birthdayreminder.ui.common.AppTabs
import app.birthdayreminder.domain.AlarmTriggers
import app.birthdayreminder.domain.DayTimeline
import app.birthdayreminder.ui.common.eventIcon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.itemsIndexed
import app.birthdayreminder.domain.OpenTarget
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.FabPosition
import app.birthdayreminder.ui.common.TgFabSecondary
import app.birthdayreminder.ui.common.rememberScrollHideFab
import app.birthdayreminder.ui.common.HideOnScroll
import app.birthdayreminder.domain.LunarCache
import app.birthdayreminder.lunar.LunarCalendar
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animate
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import app.birthdayreminder.domain.UpcomingItem
import app.birthdayreminder.domain.Agenda
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.birthdayreminder.ui.common.AddTile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.birthdayreminder.ui.theme.LiColors
import app.birthdayreminder.ui.common.splitTitle
import app.birthdayreminder.ui.common.stripLeadingEmoji
import app.birthdayreminder.ui.common.EventCard
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import app.birthdayreminder.ui.common.TgSectionTitle
import app.birthdayreminder.ui.common.TgGap
import app.birthdayreminder.ui.common.Tg
import app.birthdayreminder.ui.common.TgFab
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import app.birthdayreminder.ui.common.SoftCard
import app.birthdayreminder.ui.common.CardListItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.domain.StudyLogic
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.ui.common.ModuleStore
import java.time.YearMonth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.Features
import app.birthdayreminder.data.Reminder
import java.time.LocalDate
import app.birthdayreminder.domain.Upcoming

private val WEEK_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 日历主屏：月视图（农历 / 节日节气 / 休班 / 小圆点）+ 选中日的当天日程 + 右下角添加菜单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNewBirthday: () -> Unit,
    onNewReminder: (LocalDate) -> Unit,
    onNewDiary: (LocalDate) -> Unit,
    onNewAlarm: () -> Unit,
    onEditBirthday: (String) -> Unit,
    onEditReminder: (String) -> Unit,
    onEditDiary: (String) -> Unit,
    onNewPhrase: () -> Unit,
    onNewFitness: (LocalDate) -> Unit,
    onNewAllergy: (LocalDate) -> Unit,
    onNewMed: () -> Unit = {},
    onNewVisit: () -> Unit = {},
    onOpenCycle: () -> Unit = {},
    onOpenList: () -> Unit,
    onOpenData: (Int) -> Unit = {},
    onEditAlarm: (String) -> Unit = {},
    onEditMed: (String) -> Unit = {},
    onEditRecord: (String) -> Unit = {},
    onEditPhrase: (String) -> Unit = {},
    onOpenFitnessDate: (String) -> Unit = {},
    onOpenAllergyDate: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenReliability: () -> Unit = onOpenSettings,
    onAiClick: () -> Unit = {},
    onPrivacyEntry: () -> Unit = {},
    onSearch: () -> Unit = {},
    vm: CalendarViewModel = viewModel()
) {
    val ui by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var homeTab by rememberSaveable { mutableIntStateOf(-1) }      // 日程区域的标签：-1 = 全部（时间轴），其它是“数据”里的某一项
    val ctx = LocalContext.current
    val panelState = rememberLazyListState()
    var agendaMode by rememberSaveable { mutableStateOf(false) }      // 顶栏切换：月历 / 日程
    var agendaToToday by remember { mutableIntStateOf(0) }             // 日程里点“今天”：滚回今天
    var collapse by remember { mutableFloatStateOf(0f) }               // 月历收起了多少（像素）
    val fabHide = rememberScrollHideFab()      // 任何列表上下滑动时隐藏“+”
    val haptic = LocalHapticFeedback.current
    var preview by remember { mutableStateOf<LocalDate?>(null) }       // 长按月历上的某一天：弹出那天的简要内容
    // 功能开关：关掉的功能在加号菜单、AI 按钮、日历内容里都不出现
    val moduleVer by ModuleStore.version
    val modCtx = LocalContext.current
    val off = remember(moduleVer) { ModuleStore.disabled(modCtx) }
    fun on(k: String) = Modules.isOn(off, k, Features.allergy)
    LaunchedEffect(moduleVer) { vm.refreshModules() }

    Scaffold(
        modifier = Modifier.nestedScroll(fabHide.connection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // 右下角：AI 助手在“+”上方；上下滑动时一起隐藏，停下来再出现
            Column(modifier = Modifier.navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (on(Modules.AI)) HideOnScroll(fabHide) {
                    androidx.compose.material3.SmallFloatingActionButton(onClick = onAiClick, shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 1.dp, pressedElevation = 3.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI 助手", modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                // “+”：日程区域选“全部”时打开添加菜单；选了具体项目就直接进那一项的添加页面
                // （生理期在标签里有自己的“今天来了 / 记录今天”按钮，不显示“+”）
                val addAction: (() -> Unit)? = when (homeTab) {
                    -1 -> { { showAdd = true } }
                    OpenTarget.TAB_DIARY -> { { onNewDiary(ui.selected) } }
                    OpenTarget.TAB_REMINDER -> { { onNewReminder(ui.selected) } }
                    OpenTarget.TAB_FITNESS -> { { onNewFitness(ui.selected) } }
                    OpenTarget.TAB_ALARM -> { { onNewAlarm() } }
                    OpenTarget.TAB_MED -> { { onNewMed() } }
                    OpenTarget.TAB_ALLERGY -> { { onNewAllergy(ui.selected) } }
                    OpenTarget.TAB_PHRASE -> { { onNewPhrase() } }
                    OpenTarget.TAB_BIRTHDAY -> { { onNewBirthday() } }
                    OpenTarget.TAB_STUDY -> { { app.birthdayreminder.ui.study.StudyUi.requestNew(ui.selected) } }
                    else -> null
                }
                // 选了具体标签：按钮上写明加什么；“全部”下点了是选择面板，只放“+”
                val addLabel = HomeTabs.DEFAULT.firstOrNull { it.first == homeTab && homeTab != HomeTabs.ALL }?.second
                    ?.let { if (it == "用药就医") "药品" else it }
                if (addAction != null) HideOnScroll(fabHide) {
                    if (addLabel != null) TgFabLabeled(addLabel, onClick = addAction)
                    else TgFab(onClick = addAction) { Icon(Icons.Default.Add, contentDescription = "添加") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            MonthHeader(
                title = if (agendaMode) "日程" else "${ui.yearMonth.year}年${ui.yearMonth.monthValue}月",
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onToday = vm::goToToday,
                onList = onOpenList,
                onSettings = onOpenSettings,
                onTitleHold = onPrivacyEntry,
                onSearch = onSearch,
                agendaMode = agendaMode,
                onToggleAgenda = { agendaMode = !agendaMode },
                onAgendaToday = { agendaToToday++ }
            )
            if (agendaMode) {
                // 日程视图：从今天往后的连续列表，按 月 → 周 → 天 分组（参照 Google 日历）
                AgendaList(ui.agenda, ui.weekStart, toToday = agendaToToday, onLoadEarlier = vm::loadEarlierAgenda, onLoadMore = vm::loadMoreAgenda) { u ->
                    when (u.kind) {
                        "birthday" -> u.refId?.let(onEditBirthday)
                        "reminder" -> u.refId?.let(onEditReminder)
                        "diary" -> u.refId?.let(onEditDiary)
                        else -> { agendaMode = false; vm.select(u.date) }      // 假期、补班、复诊、就医：回到月历的那一天
                    }
                }
                return@Column
            }
            // 月历：往上滑下面的内容时，月历收成只剩选中日期那一周；滑回顶部或往下拉就展开
            val density = LocalDensity.current
            val rowH = if (ui.showLunar) 38.dp else 34.dp
            val rows = (ui.days.size / 7).coerceAtLeast(1)
            val rowPx = with(density) { rowH.toPx() }
            val maxCollapse = rowPx * (rows - 1)
            LaunchedEffect(maxCollapse) { if (collapse > maxCollapse) collapse = maxCollapse }
            val connection = remember(maxCollapse) { collapseConnection(maxCollapse, { collapse }, { collapse = it }) }
            val fraction = if (maxCollapse > 0f) (collapse / maxCollapse).coerceIn(0f, 1f) else 0f
            val selRow = ui.days.indexOfFirst { it.date == ui.selected }.let { if (it < 0) 0 else it / 7 }
            Column(modifier = Modifier.fillMaxSize().nestedScroll(connection)) {
            WeekdayRow(ui.weekStart)
            Box(modifier = Modifier.fillMaxWidth().height(rowH * rows - with(density) { collapse.toDp() }).clipToBounds()) {
                Box(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -fraction * selRow * rowPx }) {
                    // 跟手翻页：左右拖动时当前月和相邻的月一起跟着手指走，过半或快速一甩就翻过去
                    MonthPager(ui, rowH * rows, onShowMonth = vm::showMonth, onSelect = vm::select,
                        onLongPress = { d -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.select(d); preview = d })
                }
            }
            if (fraction < 0.5f) DotLegend(ui.days)
            // 第一次打开：提示一次“上滑查看日程”；往上滑过一次或点一下就不再显示
            var hint by remember { mutableStateOf(!Prefs.swipeHintShown(ctx)) }
            val dismissHint = { hint = false; Prefs.setSwipeHintShown(ctx) }
            LaunchedEffect(collapse > 0f) { if (collapse > 0f && hint) dismissHint() }
            if (hint && fraction < 0.5f) SwipeHint(dismissHint)
            // 数据融进日程区域：“全部”是这一天的时间轴，其它标签直接显示那一项的全部数据
            val tabVer by HomeTabs.version
            val dataTabs = remember(moduleVer, tabVer) { HomeTabs.visible(ctx) }
            val tabIndex = dataTabs.indexOfFirst { it.first == homeTab }.let { if (it < 0) 0 else it }
            // 在设置里把正在看的标签藏掉了：回到“全部”，“+”也跟着变
            LaunchedEffect(dataTabs) { if (dataTabs.none { it.first == homeTab }) homeTab = HomeTabs.ALL }
            // 日期抬头只在跟日期有关的标签（全部、生理期）显示，其它标签显示的是全部数据，收起来给列表腾地方
            AnimatedVisibility(visible = dataTabs.getOrNull(tabIndex)?.first in HomeTabs.DATED) { DayHeader(ui) }
            // 标签和内容同步，内容区域可以左右滑动切换（月历区域左右滑动仍然是翻月）
            SwipeTabs(
                titles = dataTabs.map { it.second },
                selected = tabIndex,
                onSelectedChange = { homeTab = dataTabs[it].first },
                scrollableTabs = true,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                val tabId = dataTabs[page].first
                if (tabId >= 0) {
                    DataTabContent(
                        tab = tabId,
                        onEditDiary = onEditDiary, onEditReminder = onEditReminder, onEditFitness = { date -> onOpenFitnessDate(date) },
                        onEditAlarm = onEditAlarm, onEditAllergy = { date -> onOpenAllergyDate(date) }, onEditMed = onEditMed,
                        onEditRecord = onEditRecord, onEditPhrase = onEditPhrase, onEditBirthday = onEditBirthday,
                        selectedDate = ui.selected
                    )
                } else {
                    DayPanel(
                        listState = panelState,
                        ui = ui,
                        onEditBirthday = onEditBirthday,
                        onEditReminder = onEditReminder,
                        onEditDiary = onEditDiary,
                        onEditFitness = { onNewFitness(ui.selected) },
                        onEditAllergy = { onNewAllergy(ui.selected) },
                        onToggleDone = { r -> vm.toggleDone(r, ui.selected) },
                        onToggleTodo = { r -> vm.toggleTodo(r) },
                        onSelectDate = { d -> vm.select(d) },
                        onEditAlarm = onEditAlarm,
                        // 学习记录：切到“学习”标签并打开那条的编辑框
                        onEditStudy = { id -> homeTab = OpenTarget.TAB_STUDY; app.birthdayreminder.ui.study.StudyUi.requestEdit(id) },
                        onOpenMed = { homeTab = OpenTarget.TAB_MED },
                        onSeeAll = { agendaMode = true },      // 查看全部：切到日程视图
                        onNewDiary = if (on(Modules.DIARY)) { { onNewDiary(ui.selected) } } else null,
                        onNewReminder = if (on(Modules.REMINDER)) { { onNewReminder(ui.selected) } } else null,
                        banner = { ReliabilityBanner(onOpenReliability) }
                    )
                }
            }
            }
        }
    }

    // 长按某一天：等选中的日子切过去、数据算好了再弹出来
    preview?.let { d ->
        if (ui.selected == d) DayPreviewSheet(ui, onDismiss = { preview = null }) { e ->
            preview = null
            val id = e.ref?.substringAfter(':') ?: ""
            when (e.kind) {
                "birthday" -> onEditBirthday(id); "reminder" -> onEditReminder(id); "diary" -> onEditDiary(id)
                "alarm" -> onEditAlarm(id); "fitness" -> onNewFitness(d); "allergy" -> onNewAllergy(d)
                "study" -> { homeTab = OpenTarget.TAB_STUDY; app.birthdayreminder.ui.study.StudyUi.requestEdit(id) }
                "med" -> homeTab = OpenTarget.TAB_MED
            }
        }
    }

    // 第一次打开、而且有会让提醒不准时的设置：带用户去“提醒检查”看一遍（只弹一次，之后靠首页的提示条）
    // 用户点了按钮才算看过（一出现就记的话，页面重建时弹窗没了，以后也不会再弹）
    var intro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!Prefs.reliabilityIntroShown(ctx)) {
            intro = Reliability.check(ctx).any { !it.ok }
            if (!intro) Prefs.setReliabilityIntroShown(ctx)      // 都没问题就不用弹了
        }
    }
    val closeIntro = { intro = false; Prefs.setReliabilityIntroShown(ctx) }
    if (intro) androidx.compose.material3.AlertDialog(
        onDismissRequest = closeIntro,
        title = { Text("让提醒准时弹出来") },
        text = { Text("很多手机默认会在后台限制 App，生日、提醒、吃药可能晚弹甚至不弹。花一分钟到“提醒检查”里把标红的几项打开。") },
        confirmButton = { TextButton(onClick = { closeIntro(); onOpenReliability() }) { Text("去检查") } },
        dismissButton = { TextButton(onClick = closeIntro) { Text("以后再说") } }
    )

    // “+”添加面板：白底、浅灰格子；关掉的功能不显示
    if (showAdd) {
        fun go(f: () -> Unit): () -> Unit = { showAdd = false; f() }
        TileSheet(
            groups = listOf(
                "记录" to listOfNotNull(
                    if (on(Modules.DIARY)) SheetItem("diary", "日记", go { onNewDiary(ui.selected) }) else null,
                    if (on(Modules.REMINDER)) SheetItem("reminder", "提醒", go { onNewReminder(ui.selected) }) else null,
                    if (on(Modules.FITNESS)) SheetItem("fitness", "健身数据", go { onNewFitness(ui.selected) }) else null,
                    // 学习：切到“学习”标签并打开记录框
                    if (on(Modules.STUDY)) SheetItem("study", "学习", go { homeTab = OpenTarget.TAB_STUDY; app.birthdayreminder.ui.study.StudyUi.requestNew(ui.selected) }) else null
                ),
                "健康" to listOfNotNull(
                    if (on(Modules.ALLERGY)) SheetItem("allergy", "过敏记录", go { onNewAllergy(ui.selected) }) else null,
                    if (on(Modules.MED)) SheetItem("med", "用药", go { onNewMed() }) else null,
                    if (on(Modules.MED)) SheetItem("followup", "就医记录", go { onNewVisit() }) else null,
                    if (on(Modules.CYCLE)) SheetItem("cycle", "生理期", go { onOpenCycle() }) else null
                ),
                "生活" to listOfNotNull(
                    if (on(Modules.ALARM)) SheetItem("alarm", "闹钟", go { onNewAlarm() }) else null,
                    if (on(Modules.BIRTHDAY)) SheetItem("birthday", "生日", go { onNewBirthday() }) else null
                ),
                "工具" to listOfNotNull(
                    if (on(Modules.PHRASE)) SheetItem("phrase", "常用语", go { onNewPhrase() }) else null
                )
            ),
            container = MaterialTheme.colorScheme.surface,
            tile = Tg.card,
            onDismiss = { showAdd = false }
        )
    }
}

/**
 * 首页提示条：有会让提醒不准时的设置没处理时显示，点进“提醒检查”。
 * 点 × 记下当时有问题的是哪几项，之后又出了新问题才再显示。回到首页时重新检查一次（可能刚从系统设置改完回来）。
 */
@Composable
private fun ReliabilityBanner(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val bad = remember(tick) { Reliability.check(ctx).filter { !it.ok } }
    val sig = bad.map { it.key }.sorted().joinToString(",")
    var dismissed by remember { mutableStateOf(Prefs.reliabilityDismissed(ctx)) }
    if (bad.isEmpty() || sig == dismissed) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)).clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("提醒可能不准时：有 ${bad.size} 项设置需要处理 ›", fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f))
        IconButton(onClick = { Prefs.setReliabilityDismissed(ctx, sig); dismissed = sig }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "不再提示", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
        }
    }
}

/** 长按月历上某一天弹出的简要内容：这天的时间轴，点一条打开它 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPreviewSheet(ui: CalendarUiState, onDismiss: () -> Unit, onOpen: (DayTimeline.Entry) -> Unit) {
    val d = ui.selected
    val entries = remember(ui) { dayEntries(ui) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("${d.monthValue}月${d.dayOfMonth}日 " + WEEK_NAMES[d.dayOfWeek.value - 1], fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text(listOfNotNull(ui.selectedLunar.replace("农历 ", "农历"), ui.selectedFestival, ui.selectedHoliday).joinToString(" · "),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            if (entries.isEmpty()) Text("这天没有记录", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            entries.forEach { e ->
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.time ?: "全天", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        EventCard(e.kind, e.title, subtitle = e.subtitle, horizontalMargin = 0.dp, onClick = { onOpen(e) })
                    }
                }
            }
        }
    }
}

/** 选中这天的时间轴条目：全天的在上，有时间的按时间排（首页“全部”和长按预览共用） */
private fun dayEntries(ui: CalendarUiState): List<DayTimeline.Entry> = DayTimeline.order(buildList {
    ui.birthdays.forEach { add(DayTimeline.Entry(null, "birthday", it.birthday.name + (it.age?.let { a -> " ${a}岁生日" } ?: "的生日"), it.desc, "b:" + it.birthday.id)) }
    ui.fitness?.let { add(DayTimeline.Entry(null, "fitness", ui.fitnessSummary ?: "健身记录", it.note.ifBlank { null }, "f:")) }
    ui.allergy?.let { add(DayTimeline.Entry(null, "allergy", ui.allergySummary ?: "过敏记录", it.note.ifBlank { null }, "g:")) }
    ui.reminders.forEach { add(DayTimeline.Entry(it.reminder.time, "reminder", it.reminder.title, it.repeatLabel, "r:" + it.reminder.id)) }
    ui.alarms.forEach { add(DayTimeline.Entry(it.time, "alarm", it.label.ifBlank { "闹钟" }, AlarmTriggers.repeatText(it), "a:" + it.id)) }
    // 吃药：药名 + 用量 · 吃了没有（未到时间 / 待服 / 已服用 / 漏服），点了切到“用药就医”标签去记
    ui.doses.forEach { s ->
        add(DayTimeline.Entry(s.dose.time, "med", s.dose.medName,
            app.birthdayreminder.domain.MedLogic.fmt(s.dose.amount) + ui.medUnits[s.dose.medId].orEmpty() + " · " + app.birthdayreminder.domain.MedLogic.statusText(s.status), "m:" + s.dose.key))
    }
    ui.diaries.forEach { item ->
        // 日记按写的时间；如果是后来补写的（写的那天不是日记那天），就放在全天
        val t = java.time.Instant.ofEpochMilli(item.entity.updatedAt).atZone(java.time.ZoneId.systemDefault())
        val time = if (t.toLocalDate().toString() == item.entity.date) "%02d:%02d".format(t.hour, t.minute) else null
        val tags = listOfNotNull(if (item.hasImage) "有图片" else null, if (item.hasLocation) "有位置" else null).joinToString(" · ")
        add(DayTimeline.Entry(time, "diary", item.snippet.ifBlank { "日记" }, tags.ifEmpty { null }, "d:" + item.entity.id))
    }
    // 学习：填了开始时间的按时间排，没填的放全天
    ui.studies.forEach { s ->
        add(DayTimeline.Entry(s.time.ifBlank { null }, "study", StudyLogic.summary(StudyLogic.itemsFromJson(s.items)).ifBlank { "学习" },
            listOf(StudyLogic.fmtMinutes(s.minutes), s.note).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }, "s:" + s.id))
    }
})


@Composable
private fun AddOption(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun MonthHeader(
    title: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onList: () -> Unit, onSettings: () -> Unit,
    onTitleHold: () -> Unit,
    onSearch: () -> Unit,
    agendaMode: Boolean = false,
    onToggleAgenda: () -> Unit = {},
    onAgendaToday: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val holdState = rememberUpdatedState(onTitleHold)
    // 标题放不下时自动一号号缩小（最小 16sp），保证“2026年11月”始终完整显示
    var titleSize by remember(title) { mutableStateOf(22f) }
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            // 月历 / 日程 切换：放在年月的左边，做得小而淡，不抢标题（点按区域仍然够大）
            IconButton(onClick = onToggleAgenda, modifier = Modifier.size(40.dp)) {
                Icon(if (agendaMode) Icons.Outlined.CalendarMonth else Icons.Outlined.ViewAgenda,
                    contentDescription = if (agendaMode) "月历" else "日程",
                    tint = iconTint.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
            }
            // 隐私空间的隐藏入口：按住年月标题约 2 秒（普通点按、短按都没有任何反应，看不出这里能按）
            Text(
                title, fontSize = titleSize.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                onTextLayout = { r -> if (r.hasVisualOverflow && titleSize > 16f) titleSize -= 1f },
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = withTimeoutOrNull(2000L) { waitForUpOrCancellation() }
                        if (up == null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            holdState.value()
                            waitForUpOrCancellation()
                        }
                    }
                }
            )
            // “‹ 今天 ›”并进标题这一行（以前单独占一行），省下来的高度给下面的日程；日程模式只有“今天”
            if (!agendaMode) IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "上个月", tint = iconTint, modifier = Modifier.size(20.dp))
            }
            TextButton(onClick = if (agendaMode) onAgendaToday else onToday, contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.defaultMinSize(minWidth = 40.dp)) { Text("今天", fontWeight = FontWeight.Medium) }
            if (!agendaMode) IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronRight, contentDescription = "下个月", tint = iconTint, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = iconTint) }
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = iconTint.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * 包住月历：识别左右滑动，并在月份变化时做滑动动画。
 * 动画用的是每一页自己那个月的格子数据（days），所以翻页过程中旧月和新月各自显示自己的内容。
 * 只认明显的横向滑动（超过 56dp），点日期、上下滚动都不受影响。
 */
@Composable
private fun SwipeMonth(
    month: YearMonth,
    days: List<CalendarDayUi>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    content: @Composable (List<CalendarDayUi>) -> Unit
) {
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val prevState = rememberUpdatedState(onPrev)
    val nextState = rememberUpdatedState(onNext)
    AnimatedContent(
        targetState = MonthPage(month, days),
        contentKey = { it.month },
        transitionSpec = {
            // 往后翻：新页从右边进来；往前翻：从左边进来（按钮、“今天”、滑动都一样）
            val forward = targetState.month > initialState.month
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(220)) { w -> dir * w } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(220)) { w -> -dir * w } + fadeOut(tween(160)))
        },
        modifier = Modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragCancel = { total = 0f },
                onDragEnd = {
                    when {
                        total <= -threshold -> nextState.value()
                        total >= threshold -> prevState.value()
                    }
                    total = 0f
                },
                onHorizontalDrag = { change, dx -> change.consume(); total += dx }
            )
        },
        label = "month"
    ) { page -> content(page.days) }
}

/** 一页月历：月份 + 那个月的格子 */
private data class MonthPage(val month: YearMonth, val days: List<CalendarDayUi>)

@Composable
private fun WeekdayRow(weekStart: Int) {
    val all = listOf("日", "一", "二", "三", "四", "五", "六")
    val names = List(7) { all[(it + weekStart) % 7] }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        names.forEach {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MonthGrid(days: List<CalendarDayUi>, selected: LocalDate, showLunar: Boolean, onSelect: (LocalDate) -> Unit, onLongPress: (LocalDate) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f)) {
                        DayCell(day, day.date == selected, showLunar, onLongPress = { onLongPress(day.date) }) { onSelect(day.date) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DayCell(day: CalendarDayUi, isSelected: Boolean, showLunar: Boolean, onLongPress: () -> Unit, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = if (day.inCurrentMonth) 1f else 0.32f
    val numColor = when {
        day.isToday -> primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showLunar) 38.dp else 34.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected && day.isToday -> primary.copy(alpha = 0.16f)
                    isSelected -> primary.copy(alpha = 0.09f)
                    else -> Color.Transparent
                }
            )
            // 不用系统波纹：滑动翻月时波纹可能残留在格子上（之前看到的灰块），选中高亮就是点按反馈
            // 长按：弹出这天的简要内容
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onLongClick = onLongPress, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = cellDescription(day, showLunar)
                if (isSelected) selected = true
                role = Role.Button
            }
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                day.date.dayOfMonth.toString(),
                fontSize = 15.sp, lineHeight = 17.sp,
                color = numColor,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
            )
            // 右上角“休 / 班”小角标
            day.holidayTag?.let { tag ->
                Text(
                    // 行高必须给小：默认样式行高 24sp，会把这一行撑高，格子高度固定，最下面的小圆点就被挤没了
                    tag, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = (if (tag == "休") LiColors.holiday else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = dim),
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp)
                )
            }
        }
        if (showLunar) {
            // 农历：小、灰、淡；节日 / 节气：蓝色
            val fest = day.festivalLabel
            Text(
                fest ?: day.lunarLabel,
                fontSize = 9.5.sp, lineHeight = 11.sp, maxLines = 1,
                fontWeight = if (fest != null) FontWeight.Medium else FontWeight.Normal,
                color = (if (fest != null) LiColors.term else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)).copy(alpha = dim)
            )
        }
        Spacer(Modifier.height(1.dp))
        // 小圆点：每一类只用一种颜色；预测出来的（预计经期、易孕期）画空心圈，不再用深浅区分。
        // 最多画 3 个，再多画一个小“+”，挤在一起分不清
        val dots = buildList {
            if (day.hasBirthday) add(LiColors.birthday to false)
            if (day.hasReminder) add(LiColors.reminder to false)
            if (day.hasDiary) add(LiColors.diary to false)
            // 学习：黑色（深色模式下黑点看不见，用正文色，也就是白色）
            if (day.hasStudy) add(MaterialTheme.colorScheme.onSurface to false)
            when (day.cycleMark) {
                CycleLogic.Mark.PERIOD -> add(LiColors.period to false)
                CycleLogic.Mark.PREDICTED -> add(LiColors.period to true)
                CycleLogic.Mark.OVULATION -> add(LiColors.fertile to false)
                CycleLogic.Mark.FERTILE -> add(LiColors.fertile to true)
                CycleLogic.Mark.NONE -> {}
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(5.dp)) {
            dots.take(3).forEach { (c, hollow) -> Dot(c, hollow) }
            if (dots.size > 3) MoreMark(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 跟手翻页的月历（像系统日历）：页码和月份一一对应，停在哪页就显示哪个月；
 * 外面改了月份（按钮、“今天”、选中别的月的日期）就滑过去。相邻两个月的格子 ViewModel 已经备好，拖动时内容完整。
 */
@Composable
private fun MonthPager(ui: CalendarUiState, height: androidx.compose.ui.unit.Dp, onShowMonth: (YearMonth) -> Unit, onSelect: (LocalDate) -> Unit, onLongPress: (LocalDate) -> Unit) {
    val center = 1200
    val base = remember { ui.yearMonth }
    fun pageOf(m: YearMonth) = center + java.time.temporal.ChronoUnit.MONTHS.between(base.atDay(1), m.atDay(1)).toInt()
    fun monthOf(p: Int): YearMonth = base.plusMonths((p - center).toLong())
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pageOf(ui.yearMonth)) { center * 2 }
    // 外面改了月份：滑过去（离得远就直接跳到前一页再滑，免得一路滑过很多个月）
    LaunchedEffect(ui.yearMonth) {
        val target = pageOf(ui.yearMonth)
        if (pager.settledPage != target && !pager.isScrollInProgress) {
            if (kotlin.math.abs(target - pager.currentPage) > 2) pager.scrollToPage(if (target > pager.currentPage) target - 1 else target + 1)
            pager.animateScrollToPage(target)
        }
    }
    // 停稳后告诉 ViewModel 现在是哪个月
    LaunchedEffect(pager.settledPage) {
        val m = monthOf(pager.settledPage)
        if (m != ui.yearMonth) onShowMonth(m)
    }
    androidx.compose.foundation.pager.HorizontalPager(state = pager, beyondViewportPageCount = 1, key = { it }) { p ->
        val m = monthOf(p)
        val days = when (m) {
            ui.yearMonth -> ui.days
            ui.yearMonth.minusMonths(1) -> ui.prevDays
            ui.yearMonth.plusMonths(1) -> ui.nextDays
            else -> null
        }
        if (days != null && days.isNotEmpty()) MonthGrid(days, ui.selected, ui.showLunar, onSelect, onLongPress)
        else Spacer(Modifier.fillMaxWidth().height(height))       // 很远的月份一闪而过，先占个位
    }
}

/**
 * 月历收起：下面的列表往上滑时，先把月历收起（最多收到只剩一周），收完了列表才开始滚动；
 * 列表在顶部时往下拉，先把月历展开。松手后停在“展开”或“收起”中离得近的那个。
 */
private fun collapseConnection(max: Float, get: () -> Float, set: (Float) -> Unit) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy < 0f && get() < max) {
            val c = (-dy).coerceAtMost(max - get()); set(get() + c); return Offset(0f, -c)
        }
        return Offset.Zero
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy > 0f && get() > 0f) {
            val c = dy.coerceAtMost(get()); set(get() - c); return Offset(0f, c)
        }
        return Offset.Zero
    }
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val cur = get()
        if (cur > 0f && cur < max) {
            val target = if (cur > max / 2) max else 0f
            animate(cur, target, animationSpec = tween(220)) { v, _ -> set(v) }
        }
        return super.onPostFling(consumed, available)
    }
}

/** 日程视图的一行：月份标题 / 周标题 / 某一天 */
private sealed class AgendaRow(val key: String) {
    class MonthTitle(val m: java.time.YearMonth) : AgendaRow("m$m")
    class WeekTitle(val label: String, start: LocalDate) : AgendaRow("w$start")
    class DayRow(val day: Agenda.Day) : AgendaRow("d${day.date}")
}

/**
 * 日程视图：月份标题 → 周标题 → 每天一行（左边星期、日期、农历；右边当天的事）。
 * 打开时定位到今天；滑到顶加载更早的，滑到底加载更晚的；toToday 每变一次就滚回今天。
 */
@Composable
private fun AgendaList(items: List<UpcomingItem>, weekStart: Int, toToday: Int, onLoadEarlier: () -> Unit, onLoadMore: () -> Unit, onOpen: (UpcomingItem) -> Unit) {
    val today = LocalDate.now()
    val rows = remember(items, weekStart) {
        buildList<AgendaRow> {
            Agenda.group(items, weekStart, ensureDay = today).forEach { m ->
                add(AgendaRow.MonthTitle(m.month))
                m.weeks.forEach { w ->
                    add(AgendaRow.WeekTitle(Agenda.weekLabel(w.start, w.end), w.start))
                    w.days.forEach { add(AgendaRow.DayRow(it)) }
                }
            }
        }
    }
    val todayIndex = rows.indexOfFirst { it is AgendaRow.DayRow && it.day.date == today }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (todayIndex - 1).coerceAtLeast(0))
    // 数据到了以后先定位到今天一次，之后才允许“滑到顶加载更早的”（免得一打开就往前加载、位置跑掉）
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && !positioned) { state.scrollToItem((todayIndex - 1).coerceAtLeast(0)); positioned = true }
    }
    LaunchedEffect(toToday) { if (toToday > 0) state.animateScrollToItem((todayIndex - 1).coerceAtLeast(0)) }
    // 滑到顶加载更早的（新加的行插在上面，列表会停在原来看的位置）；滑到底加载更晚的
    LaunchedEffect(state, rows) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }
            .collect { top -> if (top && positioned && rows.size > 1) onLoadEarlier() }
    }
    LaunchedEffect(state, rows) {
        snapshotFlow { state.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 3 } }
            .collect { near -> if (near && rows.isNotEmpty()) onLoadMore() }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        items(rows, key = { it.key }, contentType = { it::class.simpleName }) { row ->
            when (row) {
                is AgendaRow.MonthTitle -> Text("${row.m.year}年${row.m.monthValue}月", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp))
                is AgendaRow.WeekTitle -> Text(row.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 84.dp, top = 12.dp, bottom = 4.dp))
                is AgendaRow.DayRow -> AgendaDayRow(row.day, today, onOpen)
            }
        }
    }
}

@Composable
private fun AgendaDayRow(day: Agenda.Day, today: LocalDate, onOpen: (UpcomingItem) -> Unit) {
    val isToday = day.date == today
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
        Column(modifier = Modifier.width(60.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day.date.dayOfWeek.value - 1], fontSize = 13.sp,
                color = if (isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal)
            Column(
                modifier = Modifier.padding(top = 2.dp).size(48.dp).clip(CircleShape).background(if (isToday) primary.copy(alpha = 0.15f) else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(day.date.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = if (isToday) primary else MaterialTheme.colorScheme.onSurface)
                Text(LunarCalendar.cellLabel(LunarCache.of(day.date)), fontSize = 11.sp, color = if (isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (day.items.isEmpty()) Text("今天没有安排", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp))
            day.items.forEach { u ->
                val (t, sub) = splitTitle(stripLeadingEmoji(u.title))
                EventCard(u.kind, t, subtitle = sub, horizontalMargin = 0.dp, onClick = { onOpen(u) })
            }
        }
    }
}

/**
 * 时间轴的一行：左边时间（全天的只在第一行写“全天”），中间一条竖线和一个圆点（颜色跟事件类型一样），右边事件卡片。
 */
@Composable
private fun TimelineRow(label: String?, kind: String, first: Boolean, last: Boolean, gapBefore: Boolean, content: @Composable () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val dotColor = eventIcon(kind).second
    Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp).padding(top = if (gapBefore) 8.dp else 0.dp).height(IntrinsicSize.Min)) {
        Text(label ?: "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (label == "全天") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp).padding(top = 18.dp))
        Box(modifier = Modifier.width(20.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2; val dotY = 24.dp.toPx()
            drawLine(lineColor, Offset(x, if (first) dotY else 0f), Offset(x, if (last) dotY else size.height), strokeWidth = 0.75.dp.toPx())
            drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(x, dotY))
        })
        Box(modifier = Modifier.weight(1f).padding(start = 4.dp)) { content() }
    }
}

/**
 * 日程区域的抬头，做成明显的锚点：
 *   今天  10月8日          （不是今天时只写日期，旁边小字写“明天 / 3天前”）
 *   周四 · 农历八月廿八 · 寒露
 * 假期、经期再用各自的颜色写一行。
 */
@Composable
private fun DayHeader(ui: CalendarUiState) {
    val d = ui.selected
    val gap = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
    val relative = when (gap) { 1L -> "明天"; 2L -> "后天"; -1L -> "昨天"; -2L -> "前天"; else -> if (gap > 0) "${gap}天后" else "${-gap}天前" }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (gap == 0L) {
                Text("今天", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
            }
            Text("${d.monthValue}月${d.dayOfMonth}日", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (gap != 0L) Text("  $relative", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Row(modifier = Modifier.padding(top = 2.dp)) {
            Text(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1] + " · " + ui.selectedLunar.replace("农历 ", "农历"),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.selectedFestival?.let { Text(" · $it", fontSize = 13.sp, color = LiColors.term, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
        val extra = listOfNotNull(ui.selectedHoliday?.let { it to LiColors.holiday }, ui.selectedCycle?.let { stripLeadingEmoji(it) to LiColors.period })
        if (extra.isNotEmpty()) Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            extra.forEach { (t, c) -> Text(t, fontSize = 13.sp, color = c, maxLines = 1) }
        }
    }
}

/** 第一次打开时提示一次：上滑查看日程（滑过一次或点一下就不再显示） */
@Composable
private fun SwipeHint(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(" 上滑查看日程", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}

/** 读屏读出的这一天：10月7日 星期三，今天，休，国庆节，生日，提醒或日记，经期 */
private fun cellDescription(day: CalendarDayUi, showLunar: Boolean): String = buildList {
    add("${day.date.monthValue}月${day.date.dayOfMonth}日 " + listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[day.date.dayOfWeek.value - 1])
    if (day.isToday) add("今天")
    day.holidayTag?.let { add(if (it == "休") "放假" else "调休上班") }
    val fest = day.festivalLabel
    if (fest != null) add(fest) else if (showLunar) add("农历" + day.lunarLabel)
    if (day.hasBirthday) add("有生日")
    if (day.hasReminder || day.hasDiary) add("有提醒或日记")
    if (day.hasStudy) add("有学习记录")
    val cycleText = when (day.cycleMark) {
        CycleLogic.Mark.PERIOD -> "经期"
        CycleLogic.Mark.PREDICTED -> "预测经期"
        CycleLogic.Mark.OVULATION -> "可能是排卵日"
        CycleLogic.Mark.FERTILE -> "易孕期"
        CycleLogic.Mark.NONE -> null
    }
    if (cycleText != null) add(cycleText)
}.joinToString("，")

/** 月历下方的图例：只列这个月出现过的颜色 */
@Composable
private fun DotLegend(days: List<CalendarDayUi>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val items = buildList {
        if (days.any { it.inCurrentMonth && it.hasBirthday }) add(LiColors.birthday to "生日")
        if (days.any { it.inCurrentMonth && it.hasReminder }) add(LiColors.reminder to "提醒")
        if (days.any { it.inCurrentMonth && it.hasDiary }) add(LiColors.diary to "日记")
        if (days.any { it.inCurrentMonth && it.hasStudy }) add(ink to "学习")
        if (days.any { it.inCurrentMonth && (it.cycleMark == CycleLogic.Mark.PERIOD || it.cycleMark == CycleLogic.Mark.PREDICTED) }) add(LiColors.period to "经期")
        if (days.any { it.inCurrentMonth && (it.cycleMark == CycleLogic.Mark.FERTILE || it.cycleMark == CycleLogic.Mark.OVULATION) }) add(LiColors.fertile to "易孕期")
    }
    // 有预测出来的（空心圈）就加一项说明
    val predicted = days.any { it.inCurrentMonth && (it.cycleMark == CycleLogic.Mark.PREDICTED || it.cycleMark == CycleLogic.Mark.FERTILE) }
    if (items.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(c))
                Text(" $t", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (predicted) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Text(" 预测", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Dot(color: Color, hollow: Boolean = false) {
    if (hollow) Box(modifier = Modifier.size(5.dp).border(1.dp, color, CircleShape))
    else Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
}

/** 一天里超过 3 类记录：最后画一个小“+” */
@Composable
private fun MoreMark(color: Color) {
    Box(modifier = Modifier.size(5.dp).drawBehind {
        val w = 1.dp.toPx()
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w)
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = w)
    })
}

@Composable
private fun DayPanel(
    listState: LazyListState,
    ui: CalendarUiState,
    onEditBirthday: (String) -> Unit,
    onEditReminder: (String) -> Unit,
    onEditDiary: (String) -> Unit,
    onEditFitness: () -> Unit,
    onEditAllergy: () -> Unit,
    onToggleDone: (Reminder) -> Unit,
    onToggleTodo: (Reminder) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onEditAlarm: (String) -> Unit = {},
    onEditStudy: (String) -> Unit = {},
    onOpenMed: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onNewDiary: (() -> Unit)? = null,
    onNewReminder: (() -> Unit)? = null,
    banner: @Composable () -> Unit = {}
) {
    val d = ui.selected
    val today = LocalDate.now()

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)) {
        item(key = "banner", contentType = "banner") { banner() }
        // ---------------- 时间轴：全天的在上，有时间的按时间排 ----------------
        val entries = dayEntries(ui)
        if (entries.isEmpty()) {
            item(key = "empty", contentType = "hint") {
                // 空着的时候顺手给两个入口，不用再去找“+”
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (d == today) "今天还没有安排" else "这天没有安排", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    onNewDiary?.let { f -> androidx.compose.material3.AssistChip(onClick = f, label = { Text("写日记") }) }
                    onNewReminder?.let { f -> androidx.compose.material3.AssistChip(onClick = f, label = { Text("加提醒") }) }
                }
            }
        }
        val firstTimed = entries.indexOfFirst { it.time != null }
        itemsIndexed(entries, key = { _, e -> e.ref ?: e.title }, contentType = { _, _ -> "timeline" }) { i, e ->
            val label = when {
                e.time != null -> e.time
                i == 0 -> "全天"
                else -> null
            }
            val reminder = if (e.kind == "reminder") ui.reminders.firstOrNull { "r:" + it.reminder.id == e.ref } else null
            TimelineRow(label = label, kind = e.kind, first = i == 0, last = i == entries.lastIndex, gapBefore = i == firstTimed && i > 0) {
                EventCard(e.kind, e.title, subtitle = e.subtitle, horizontalMargin = 0.dp, strike = reminder?.done == true,
                    leading = reminder?.let { r -> { Checkbox(checked = r.done, onCheckedChange = { onToggleDone(r.reminder) }, modifier = Modifier.size(36.dp)) } },
                    onClick = {
                        val id = e.ref?.substringAfter(':') ?: ""
                        when (e.kind) {
                            "birthday" -> onEditBirthday(id); "reminder" -> onEditReminder(id); "diary" -> onEditDiary(id)
                            "alarm" -> onEditAlarm(id); "fitness" -> onEditFitness(); "allergy" -> onEditAllergy(); "study" -> onEditStudy(id)
                            "med" -> onOpenMed()
                        }
                    })
            }
        }

        // ---------------- 待办（不管选中哪天都显示） ----------------
        if (ui.todos.isNotEmpty()) {
            item(key = "todoTitle", contentType = "title") { SectionTitle("待办", "${ui.todos.size} 项") }
            items(ui.todos, key = { "t" + it.id }, contentType = { "eventCheck" }) { r ->
                EventCard("todo", r.title,
                    leading = { Checkbox(checked = false, onCheckedChange = { onToggleTodo(r) }, modifier = Modifier.size(36.dp)) },
                    onClick = { onEditReminder(r.id) })
            }
        }

        // ---------------- 未来 30 天：时间线（从今天起，和上面翻到哪个月无关） ----------------
        item(key = "upTitle", contentType = "title") {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f)) { SectionTitle("未来 30 天") }
                TextButton(onClick = onSeeAll) { Text("查看全部 ›", fontSize = 14.sp) }
            }
        }
        if (ui.upcoming.none { it.date != today || d != today || it.kind == "followup" }) {
            item(key = "upEmpty", contentType = "hint") {
                Text("接下来 30 天没有生日、提醒或假期", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
        val timeline = if (d == today) ui.upcoming.filter { it.date != today || it.kind == "followup" } else ui.upcoming
        timeline.groupBy { it.date }.forEach { (date, list) ->
            item(key = "upd_$date", contentType = "timeline") {
                // 日期写在上面（“后天”只在今天有意义，看别的日期时就没意义了），下面是这天的事
                Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                    Text("${date.monthValue}月${date.dayOfMonth}日 · " + listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1],
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelectDate(date) })
                    Column(modifier = Modifier.fillMaxWidth()) {
                        list.forEach { u ->
                            val (t, sub) = splitTitle(stripLeadingEmoji(u.title))
                            EventCard(u.kind, t, subtitle = sub, horizontalMargin = 0.dp, onClick = {
                                when (u.kind) {
                                    "birthday" -> u.refId?.let(onEditBirthday)
                                    "reminder" -> u.refId?.let(onEditReminder)
                                    else -> onSelectDate(date)       // 假期、补班、复诊：日历跳到那一天
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, hint: String? = null) {
    TgSectionTitle(text, hint = hint)
}
