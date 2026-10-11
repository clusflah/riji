package app.birthdayreminder.ui.search

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.CardListItem
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.ui.common.ModuleStore
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.AppDatabase
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.domain.SearchDiary
import app.birthdayreminder.domain.SearchHit
import app.birthdayreminder.domain.SearchLogic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 搜索要用的全部数据（数据有变化会自动更新；日记正文提前从文字块里取出来） */
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    data class Source(
        val diaries: List<SearchDiary> = emptyList(),
        val reminders: List<app.birthdayreminder.data.Reminder> = emptyList(),
        val birthdays: List<app.birthdayreminder.data.Birthday> = emptyList(),
        val phrases: List<app.birthdayreminder.data.Phrase> = emptyList(),
        val loaded: Boolean = false        // 数据库第一次读完之前是 false：显示“正在加载”，不要误报“没有找到”
    )

    val source: StateFlow<Source> = combine(
        db.diaryDao().observeAll(), db.reminderDao().observeAll(), db.birthdayDao().observeAll(), db.phraseDao().observeAll()
    ) { d, r, b, p ->
        Source(
            d.map {
                val bl = BlockConverter.fromJson(it.content)
                // 正文 + 附件的文件名都能搜到
                val names = bl.filterIsInstance<app.birthdayreminder.data.ContentBlock.FileBlock>().joinToString(" ") { f -> "📎" + f.name }
                SearchDiary(it.id, it.date, DiaryText.plainText(bl) + (if (names.isNotEmpty()) "\n" + names else ""), it.locationAddress, it.isPrivate)
            },
            r, b, p, loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Source())
}

/**
 * 全局搜索：同时搜日记、提醒、生日、常用语。空格隔开的多个词要同时出现。
 * 私密内容不会被搜出来。点结果进入编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (kind: String, id: String) -> Unit,
    vm: SearchViewModel = viewModel()
) {
    var q by rememberSaveable { mutableStateOf("") }
    val src by vm.source.collectAsState()
    // 关掉的功能不搜
    val moduleVer by ModuleStore.version
    val ctxS = androidx.compose.ui.platform.LocalContext.current
    val off = remember(moduleVer) { ModuleStore.disabled(ctxS) }
    val hits = remember(q, src, off) {
        SearchLogic.search(q,
            if (Modules.DIARY in off) emptyList() else src.diaries,
            if (Modules.REMINDER in off) emptyList() else src.reminders,
            if (Modules.BIRTHDAY in off) emptyList() else src.birthdays,
            if (Modules.PHRASE in off) emptyList() else src.phrases)
    }
    val terms = remember(q) { SearchLogic.terms(q) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) { } }

    Scaffold(topBar = {
        TgTopBar(
            title = {
                OutlinedTextField(
                    value = q, onValueChange = { q = it.take(50) }, singleLine = true,
                    placeholder = { Text("搜日记、提醒、生日、常用语") },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, contentDescription = "清空") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus)
                )
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
        )
    }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            when {
                terms.isEmpty() -> item {
                    Text("输入关键词就开始搜索。多个词用空格隔开，比如“医院 复查”会找同时包含这两个词的内容。",
                        modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                !src.loaded -> item {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  正在加载…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                hits.isEmpty() -> item {
                    Text("没有找到包含“${q.trim()}”的内容", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    item { Text("找到 ${hits.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
                    hits.groupBy { it.kind }.forEach { (kind, list) ->
                        item(key = "h_$kind") {
                            Text("${SearchLogic.KIND_LABEL[kind]} ${list.size}", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
                        }
                        items(list, key = { it.kind + it.id }) { h -> HitRow(h, terms) { keyboard?.hide(); onOpen(h.kind, h.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(h: SearchHit, terms: List<String>, onClick: () -> Unit) {
    CardListItem(
        headlineContent = { Text(highlight(h.title, terms), maxLines = 1) },
        supportingContent = { Text(highlight(h.snippet, terms), maxLines = 2) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** 把关键词加粗、换成主色 */
@Composable
private fun highlight(text: String, terms: List<String>): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    val ranges = SearchLogic.highlights(text, terms)
    return buildAnnotatedString {
        var pos = 0
        for (r in ranges) {
            if (r.first > pos) append(text.substring(pos, r.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(r.first, r.last + 1)) }
            pos = r.last + 1
        }
        if (pos < text.length) append(text.substring(pos))
    }
}
