package app.birthdayreminder.ui.common

import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthdayreminder.ui.theme.LiColors

/**
 * 日迹的设计系统：简洁、轻盈、留白，信息分主次。
 *  - 浅色顶栏（白底深色字），蓝色只用于强调：选中、今天、按钮、链接
 *  - 卡片没有边框和阴影，只靠浅底色（#F6F8FA）、14 圆角和间距
 *  - 少用分隔线、不用灰色色带，靠留白区分区块
 *  - 用自己的一套线性图标（浅色圆底）代替表情符号
 * 组件名沿用原来的（SoftCard、CardListItem、CardGroup、Tg…），各页面不用改调用。
 */

/** 现在是不是深色界面（看文字颜色判断） */
@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f

object Tg {
    /** 顶栏：和页面同色（浅色顶栏） */
    val header: Color @Composable get() = MaterialTheme.colorScheme.surface
    val onHeader: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    /** 区块之间不再用灰色色带，只留白 */
    val gap: Color @Composable get() = Color.Transparent
    /** 区块标题：正文色、加粗（不再是蓝色） */
    val section: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    /** 卡片底色 */
    val card: Color @Composable get() = if (isDarkUi()) LiColors.cardDark else LiColors.cardLight

    /** 头像的 7 种颜色（柔和） */
    val AVATARS = listOf(
        Color(0xFFE8638A), Color(0xFFF2994A), Color(0xFF9B87E8), Color(0xFF5CB176),
        Color(0xFF45B3C0), Color(0xFF4A90E2), Color(0xFFD97FB0)
    )
}

/** 区块之间的留白 */
@Composable
fun TgGap(height: Dp = 12.dp) { Spacer(Modifier.fillMaxWidth().height(height)) }

/** 圆形头像：名字的第一个字（或日期数字），颜色按内容固定分配 */
@Composable
fun TgAvatar(text: String, seed: String = text, size: Dp = 42.dp) {
    val label = text.trim().let { t -> if (t.length <= 2 && t.all { it.isDigit() }) t else t.take(1) }.ifEmpty { "·" }
    val color = Tg.AVATARS[Math.floorMod(seed.hashCode(), Tg.AVATARS.size)]
    Box(modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = if (label.length > 1) 15.sp else 17.sp)
    }
}

/** 区块标题：正文色、加粗，上方留白；可以带一句灰色说明 */
@Composable
fun TgSectionTitle(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = Tg.section, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (hint != null) Text("  $hint", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** 不再画外框（保留这个名字，旧代码调用时什么也不做） */
@Composable
fun Modifier.softCard(shape: Shape = CircleShape): Modifier = this

/** 卡片：浅底色、14 圆角、左右 16 的外边距，没有边框和阴影 */
@Composable
fun SoftCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(shape).background(Tg.card).then(modifier), content = content)
}

/** 列表的一行：白底，有头像时分隔线从文字处开始，分隔线很淡 */
@Composable
fun CardListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = headlineContent,
            modifier = modifier.padding(vertical = 2.dp),
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        )
        HorizontalDivider(modifier = Modifier.padding(start = if (leadingContent != null) 74.dp else 16.dp),
            thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 设置页的一组：标题在上（灰色小字），内容放在一张浅底卡片里 */
@Composable
fun CardGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 24.dp, bottom = 8.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Tg.card)
            .padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** 顶栏：浅色（和页面同色），深色标题和图标 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Tg.header, scrolledContainerColor = Tg.header,
            titleContentColor = Tg.onHeader, navigationIconContentColor = Tg.onHeader, actionIconContentColor = Tg.onHeader
        )
    )
}

/** 以前蓝色顶栏用的配色切换；现在顶栏是浅色，不需要切换（保留名字） */
@Composable
fun OnHeader(content: @Composable () -> Unit) { content() }

/** 浮动按钮：主色、48dp、16 圆角 */
@Composable
fun TgFab(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(48.dp), shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White,
        // 阴影调低，视觉上轻一点，不和旁边的卡片抢
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp, focusedElevation = 2.dp, hoveredElevation = 3.dp),
        content = content)
}

/** 和“+”一样大的次要按钮：浅蓝底、蓝色图标（首页左下角的“数据”） */
@Composable
fun TgFabSecondary(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(48.dp), shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary, content = content)
}

// ---------------------------------------------------------------- 图标（代替表情符号）

/** 各类事件的图标和颜色：生日粉、节气蓝、假期橙、完成绿，其余用柔和的中性色 */
fun eventIcon(kind: String): Pair<ImageVector, Color> = when (kind) {
    "birthday" -> Icons.Outlined.Cake to LiColors.birthday
    "holiday" -> Icons.Outlined.BeachAccess to LiColors.holiday
    "work" -> Icons.Outlined.WorkOutline to Color(0xFF6B7A90)
    "reminder" -> Icons.Outlined.NotificationsNone to LiColors.reminder
    "followup", "visit" -> Icons.Outlined.LocalHospital to Color(0xFF2BA99B)
    "diary" -> Icons.Outlined.EditNote to LiColors.diary
    "fitness" -> Icons.Outlined.FitnessCenter to LiColors.done
    "allergy" -> Icons.Outlined.LocalFlorist to Color(0xFF7FA650)
    "med" -> Icons.Outlined.Medication to Color(0xFF2BA99B)
    "cycle" -> Icons.Outlined.WaterDrop to LiColors.period
    "todo" -> Icons.Outlined.CheckCircle to LiColors.reminder
    "shopping" -> Icons.Outlined.ShoppingCart to LiColors.holiday
    "ai" -> Icons.Outlined.AutoAwesome to LiColors.term
    "bill" -> Icons.Outlined.ReceiptLong to Color(0xFF6B7A90)
    "phrase" -> Icons.Outlined.ContentCopy to Color(0xFF6B7A90)
    "alarm" -> Icons.Outlined.Alarm to LiColors.term
    // 设置页用的
    "appearance" -> Icons.Outlined.Palette to Color(0xFF8B7BE6)
    "notify" -> Icons.Outlined.NotificationsNone to Color(0xFFD9A030)
    "check" -> Icons.Outlined.VerifiedUser to LiColors.done
    "modules" -> Icons.Outlined.Apps to LiColors.term
    "backup" -> Icons.Outlined.CloudUpload to LiColors.term
    "map" -> Icons.Outlined.Map to Color(0xFF2BA99B)
    "calendar" -> Icons.Outlined.CalendarMonth to LiColors.term
    "lunar" -> Icons.Outlined.DarkMode to Color(0xFF6B7A90)
    "week" -> Icons.Outlined.ViewWeek to Color(0xFF6B7A90)
    else -> Icons.Outlined.Event to LiColors.term
}

/** 图标放在同色的浅色圆底里 */
@Composable
fun EventIcon(kind: String, size: Dp = 36.dp, tint: Color? = null) {
    val (icon, c0) = eventIcon(kind)
    val color = tint ?: c0
    // 圆底很浅，图标区域不抢眼
    Box(modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** 去掉标题开头的表情符号（旧数据和纯逻辑里还带着），换成图标显示 */
fun stripLeadingEmoji(s: String): String {
    val t = s.trimStart()
    if (t.isEmpty()) return t
    val cp = t.codePointAt(0)
    // 只认真正的表情符号区段（不会误删“（备注）”这类中文标点开头的标题）
    val isEmoji = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp == 0x231A || cp == 0x23F0
    return if (isEmoji) t.substringAfter(' ', t).trimStart() else t
}

/** 把“国庆节放假中 · 第5天，共7天”拆成标题和补充说明 */
fun splitTitle(s: String): Pair<String, String?> {
    val i = s.indexOf(" · ")
    return if (i > 0) s.substring(0, i) to s.substring(i + 3) else s to null
}

/** 事件卡片：左边图标（或复选框），中间标题 + 灰色补充说明；浅底、14 圆角、没有边框 */
@Composable
fun EventCard(
    kind: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    strike: Boolean = false,
    horizontalMargin: Dp = 16.dp,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    // 不是所有事都值得一张“大卡片”：普通事件只有很淡的底色；提醒（含待办）和生日用稍明显的卡片，底色带一点各自的颜色
    val accent = when (kind) { "birthday" -> LiColors.birthday; "reminder", "todo" -> LiColors.reminder; else -> null }
    val special = accent != null
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalMargin, vertical = if (special) 4.dp else 2.dp).clip(RoundedCornerShape(12.dp))
            .background(accent?.copy(alpha = if (isDarkUi()) 0.16f else 0.10f) ?: Tg.card.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = if (special) 12.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) leading() else EventIcon(kind)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (strike) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- 标签栏

/**
 * 统一的标签栏：选中项主色、未选中灰色，下面是短圆角指示条（32dp × 3dp），没有长横线。
 * scrollable = true 时可以左右滑动，当前标签会自动滚到能看见的位置。
 */
@Composable
fun AppTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, scrollable: Boolean = false) {
    val sel = selected.coerceIn(0, maxOf(0, titles.lastIndex))
    val primary = MaterialTheme.colorScheme.primary
    val indicator: @Composable (List<androidx.compose.material3.TabPosition>) -> Unit = { positions ->
        if (sel < positions.size) {
            val pos = positions[sel]
            val x by androidx.compose.animation.core.animateDpAsState(pos.left + (pos.width - 32.dp) / 2, label = "tabIndicator")
            Box(Modifier.fillMaxWidth().wrapContentSize(Alignment.BottomStart)) {
                Box(Modifier.offset(x = x).width(32.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(primary))
            }
        }
    }
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { i, t ->
            androidx.compose.material3.Tab(
                selected = i == sel, onClick = { onSelect(i) },
                selectedContentColor = primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(t, fontSize = 15.sp, fontWeight = if (i == sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1) }
            )
        }
    }
    if (scrollable) {
        androidx.compose.material3.ScrollableTabRow(selectedTabIndex = sel, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.surface,
            indicator = indicator, divider = {}, tabs = tabs)
    } else {
        androidx.compose.material3.TabRow(selectedTabIndex = sel, containerColor = MaterialTheme.colorScheme.surface,
            indicator = indicator, divider = {}, tabs = tabs)
    }
}

// ---------------------------------------------------------------- 摘要卡片 / 日期徽章

/** 摘要卡片：小标题（灰）、大数字（正文色）、补充说明（灰）。浅底、12 圆角 */
@Composable
fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier, unit: String = "", hint: String? = null, accent: Color? = null) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Tg.card).padding(16.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = accent ?: MaterialTheme.colorScheme.onSurface)
            if (unit.isNotEmpty()) Text(" $unit", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (!hint.isNullOrBlank()) Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 日期徽章：日期数字放在统一颜色的浅底圆角方块里（不再随机彩色） */
@Composable
fun DateBadge(day: String, size: Dp = 40.dp) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Tg.card), contentAlignment = Alignment.Center) {
        Text(day, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 按提醒标题推断用什么图标（买东西、用药、工作、运动、医院、AI…），推断不出就用铃铛 */
fun reminderKind(title: String): String {
    val t = title.lowercase()
    return when {
        listOf("买", "购", "超市", "快递", "取件").any { it in t } -> "shopping"
        listOf("药", "吃药", "服药").any { it in t } -> "med"
        listOf("医院", "复诊", "体检", "挂号", "看病", "牙").any { it in t } -> "followup"
        listOf("开会", "会议", "上班", "工作", "报告", "汇报", "面试").any { it in t } -> "work"
        listOf("运动", "跑步", "健身", "锻炼", "游泳", "瑜伽").any { it in t } -> "fitness"
        listOf("生日").any { it in t } -> "birthday"
        listOf("claude", "gpt", "gemini", "chatgpt").any { it in t } || Regex("(^|[^a-z])ai([^a-z]|$)").containsMatchIn(t) -> "ai"   // “AI”要单独成词，不误认 email、rain
        listOf("交", "缴费", "房租", "还款", "账单", "水电").any { it in t } -> "bill"
        else -> "reminder"
    }
}

// ---------------------------------------------------------------- 设置总览、添加菜单

/** 一组设置：灰色小标题在上，下面一张浅底卡片，组内的行用细线分隔 */
@Composable
fun NavGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 24.dp, bottom = 8.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Tg.card), content = content)
    }
}

/** 设置的一行：图标 + 标题（黑）+ 一行摘要（灰）+ 右边开关或箭头；last = 最后一行（不画分隔线） */
@Composable
fun NavRow(kind: String?, title: String, summary: String? = null, summaryColor: Color? = null, last: Boolean = false,
           trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (kind != null) { EventIcon(kind, size = 32.dp); Spacer(Modifier.width(12.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                if (!summary.isNullOrBlank()) Text(summary, fontSize = 13.sp, color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        if (!last) HorizontalDivider(modifier = Modifier.padding(start = if (kind != null) 60.dp else 16.dp), thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 添加菜单里的一格：小的彩色图标圆 + 名称（没有说明），浅底圆角，一行放 4 个 */
@Composable
fun AddTile(kind: String, title: String, modifier: Modifier = Modifier, color: Color = Tg.card, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(color).clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EventIcon(kind, size = 32.dp)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- 可以左右滑动切换的标签页

/**
 * 标签栏 + 可以左右滑动的页面（两者同步）：滑动时内容跟手，松手停在哪页，标签就选中哪页；点标签会滑过去。
 * 嵌套使用时（比如“全部”页里的用药就医子页），里面的页面滑到头，再滑就交给外面切换。
 * selected / onSelectedChange 用页面停稳后的结果同步，避免点标签滑动途中来回跳。
 */
@Composable
fun SwipeTabs(
    titles: List<String>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollableTabs: Boolean = false,
    content: @Composable (page: Int) -> Unit
) {
    val count = titles.size
    if (count == 0) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = selected.coerceIn(0, count - 1)) { titles.size }
    // 外面改了选中项（比如点“设置避孕药”跳到第 4 页、功能被关掉换页）：滑过去
    androidx.compose.runtime.LaunchedEffect(selected, count) {
        val target = selected.coerceIn(0, count - 1)
        if (pager.settledPage != target) pager.animateScrollToPage(target)
    }
    // 页面停稳后告诉外面
    androidx.compose.runtime.LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != selected && pager.settledPage < count) onSelectedChange(pager.settledPage)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        AppTabs(titles, pager.currentPage.coerceIn(0, count - 1), { i -> scope.launch { pager.animateScrollToPage(i) } }, scrollable = scrollableTabs)
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 0,
            key = { it }
        ) { page ->
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) { content(page) }
        }
    }
}

// ---------------------------------------------------------------- 滑动时隐藏“+”

/**
 * 页面里任何列表上下滑动时隐藏右下角的“+”，停下来约半秒后再出现（惯性滚动期间也保持隐藏）。
 * 用法：Scaffold(modifier = Modifier.nestedScroll(fab.connection), floatingActionButton = { HideOnScroll(fab) { ... } })
 * 只看上下滑动，左右滑动切换标签不会触发。
 */
class ScrollHideFab(private val scope: kotlinx.coroutines.CoroutineScope) {
    var visible by androidx.compose.runtime.mutableStateOf(true)
        private set
    private var job: kotlinx.coroutines.Job? = null

    private fun onScroll() {
        visible = false
        job?.cancel()
        job = scope.launch { kotlinx.coroutines.delay(450); visible = true }
    }

    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (kotlin.math.abs(available.y) > 0.5f) onScroll()
            return androidx.compose.ui.geometry.Offset.Zero        // 只看不拦：滑动照常交给列表
        }
    }
}

@Composable
fun rememberScrollHideFab(): ScrollHideFab {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return androidx.compose.runtime.remember { ScrollHideFab(scope) }
}

/** 包住“+”：滑动时缩小淡出，停下来放大淡入 */
@Composable
fun HideOnScroll(state: ScrollHideFab, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = state.visible,
        enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
    ) { content() }
}
