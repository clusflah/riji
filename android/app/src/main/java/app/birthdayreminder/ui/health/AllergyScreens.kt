package app.birthdayreminder.ui.health

import app.birthdayreminder.privacy.SecureWindow
import androidx.compose.foundation.horizontalScroll
import app.birthdayreminder.ui.common.DateBadge
import app.birthdayreminder.ui.common.TgAvatar
import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.CardListItem
import app.birthdayreminder.ui.common.SoftCard
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.AllergyLog
import app.birthdayreminder.domain.AllergyLogic
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 过敏记录列表（“全部”页的标签）：上面是最近 30 天各症状出现的天数，下面是每天的记录 */
@Composable
fun AllergyListContent(onEdit: (String) -> Unit, vm: AllergyViewModel = viewModel()) {
    val all by vm.all.collectAsState()
    val today = remember { LocalDate.now() }
    val stats = remember(all) { AllergyLogic.last30Days(all, today) }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        if (all.isEmpty()) {
            item {
                Text("还没有过敏记录，点右下角“+”记今天的情况", modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("最近 30 天", fontWeight = FontWeight.SemiBold)
                        Text(stats.joinToString("　") { (label, n) -> "$label ${n}天" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(all, key = { it.date }) { log ->
            CardListItem(
                leadingContent = { DateBadge(log.date.takeLast(2).trimStart('0')) },
                overlineContent = { Text(shortDate(log.date, today)) },
                headlineContent = { Text(AllergyLogic.summary(log)) },
                supportingContent = if (log.note.isNotBlank()) { { Text(log.note, maxLines = 1) } } else null,
                modifier = Modifier.clickable { onEdit(log.date) }
            )
        }
    }
}

/** 记某一天的过敏情况：眼睛过敏、鼻子过敏、咳嗽各选一个程度 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllergyEditScreen(initialDate: LocalDate, onBack: () -> Unit, initialPrivate: Boolean = false, vm: AllergyViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initialDate) }
    var originalDate by remember { mutableStateOf<String?>(null) }
    val levels = remember { mutableStateMapOf("eye" to 0, "nose" to 0, "cough" to 0) }
    var note by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(initialPrivate) }      // 在隐私空间里新建的就是私密的；编辑已有的保持原样
    SecureWindow(isPrivate)                                    // 私密内容不让截屏
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    suspend fun load(d: LocalDate) {
        val log = vm.get(d.toString())
        levels["eye"] = log?.eye ?: 0
        levels["nose"] = log?.nose ?: 0
        levels["cough"] = log?.cough ?: 0
        note = log?.note ?: ""
        isPrivate = log?.isPrivate ?: initialPrivate      // 这天还没有记录：按入口决定（隐私空间里新建的是私密的）
        originalDate = log?.date
    }
    LaunchedEffect(Unit) { load(initialDate) }

    fun save() {
        vm.save(
            AllergyLog(date.toString(), levels["eye"] ?: 0, levels["nose"] ?: 0, levels["cough"] ?: 0, note.trim().take(200), isPrivate = isPrivate),
            originalDate, onBack
        )
    }

    Scaffold(topBar = {
        TgTopBar(
            title = { Text(if (originalDate != null) "编辑过敏记录" else "记过敏情况") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            actions = { TextButton(onClick = { save() }) { Text("保存") } }
        )
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) { Text(dateLabel(date)) }
            if (originalDate != null) {
                Text("这天已经记过，改完点保存会更新这条记录", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AllergyLogic.SYMPTOMS.forEach { (key, label) ->
                Text(label, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AllergyLogic.LEVELS.forEachIndexed { i, name ->
                        FilterChip(selected = (levels[key] ?: 0) == i, onClick = { levels[key] = i }, label = { Text(name) })
                    }
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("备注（选填，比如用药、诱因）") },
                modifier = Modifier.fillMaxWidth())
            if (originalDate != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这天的记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDate) RecordDatePicker(date, onPick = { d -> date = d; scope.launch { load(d) } }, onDismiss = { showDate = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录") }, text = { Text("确定删除 ${dateLabel(date)} 的过敏记录吗？") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; originalDate?.let { vm.delete(it, onBack) } }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
