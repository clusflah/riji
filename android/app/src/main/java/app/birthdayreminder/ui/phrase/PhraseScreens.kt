package app.birthdayreminder.ui.phrase

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import app.birthdayreminder.ui.common.EventIcon
import app.birthdayreminder.ui.common.TgAvatar
import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.CardListItem
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.birthdayreminder.notify.Prefs
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.Phrase
import app.birthdayreminder.domain.PhraseLogic
import app.birthdayreminder.domain.DiaryText
import app.birthdayreminder.privacy.SecureWindow
import java.util.UUID

fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("常用语", text))
    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
}

/**
 * 常用语列表（放在“全部”页的标签里）：按分类分组，点分组标题折叠 / 展开（会记住），
 * 点一行编辑，右边“复制”一键复制内容。
 */
@Composable
fun PhraseListContent(onEdit: (String) -> Unit, vm: PhraseViewModel = viewModel()) {
    val ctx = LocalContext.current
    val all by vm.all.collectAsState()
    var collapsed by remember { mutableStateOf(Prefs.phraseCollapsed(ctx)) }
    fun updateCollapsed(v: Set<String>) { collapsed = v; Prefs.setPhraseCollapsed(ctx, v) }
    // 分组被删光或改名后，把旧名字从记录里清掉
    LaunchedEffect(all) {
        if (all.isNotEmpty()) {
            val p = PhraseLogic.prune(collapsed, all)
            if (p != collapsed) updateCollapsed(p)
        }
    }
    val rows = remember(all, collapsed) { PhraseLogic.rows(all, collapsed) }
    val cats = remember(all) { PhraseLogic.categories(all) }

    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        if (all.isEmpty()) {
            item {
                Text("还没有常用语，点右下角“+”添加。适合存学号、邮箱、手机号这类经常要复制的信息。",
                    modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (cats.size > 1) {
            item(key = "expandAll") {
                val allCollapsed = cats.all { it in collapsed }
                Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { updateCollapsed(if (allCollapsed) emptySet() else cats.toSet()) }) {
                        Text(if (allCollapsed) "全部展开" else "全部折叠")
                    }
                }
            }
        }
        items(rows, key = { r -> when (r) { is PhraseLogic.Row.Header -> "cat_" + r.category; is PhraseLogic.Row.Item -> r.phrase.id } }) { r ->
            when (r) {
                is PhraseLogic.Row.Header -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { updateCollapsed(PhraseLogic.toggle(collapsed, r.category)) }
                        .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 灰色箭头 + 正文色名字 + 灰色数量（不用蓝色）
                    Icon(if (r.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(r.category, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("${r.count}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is PhraseLogic.Row.Item -> {
                    val p = r.phrase
                    CardListItem(
                        leadingContent = { EventIcon("phrase", size = 40.dp) },
                        headlineContent = { Text(p.title, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(DiaryText.oneLine(p.value, 60)) },
                        trailingContent = { TextButton(onClick = { copyToClipboard(ctx, p.value) }) { Text("复制") } },
                        modifier = Modifier.clickable { onEdit(p.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhraseEditScreen(id: String?, onBack: () -> Unit, initialPrivate: Boolean = false, vm: PhraseViewModel = viewModel()) {
    val all by vm.everything.collectAsState()
    var existing by remember { mutableStateOf<Phrase?>(null) }
    var category by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(initialPrivate) }
    SecureWindow(isPrivate)

    LaunchedEffect(id) {
        if (id != null) vm.get(id)?.let { p -> existing = p; category = p.category; title = p.title; value = p.value; isPrivate = p.isPrivate }
    }
    val cats = remember(all) { PhraseLogic.categories(all).filter { it != PhraseLogic.UNCATEGORIZED } }

    fun save() {
        val e = PhraseLogic.validate(category, title, value)
        if (e != null) { error = e; return }
        vm.save(Phrase(existing?.id ?: UUID.randomUUID().toString(), category.trim().take(20), title.trim().take(40), value.trim().take(500), isPrivate = isPrivate), onBack)
    }

    Scaffold(topBar = {
        TgTopBar(
            title = { Text(if (existing != null) "编辑常用语" else "添加常用语") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            actions = { TextButton(onClick = { save() }) { Text("保存") } }
        )
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = category, onValueChange = { category = it.take(20) }, singleLine = true,
                label = { Text("分类（选填）") }, placeholder = { Text("例如：邮箱、手机号") }, modifier = Modifier.fillMaxWidth())
            if (cats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    cats.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, singleLine = true,
                label = { Text("标题") }, placeholder = { Text("例如：学号") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = value, onValueChange = { value = it.take(500) },
                label = { Text("内容") }, placeholder = { Text("例如：20100710") }, minLines = 6, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这条常用语", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除常用语") }, text = { Text("确定删除“${existing?.title ?: ""}”吗？") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; existing?.let { vm.delete(it, onBack) } }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
