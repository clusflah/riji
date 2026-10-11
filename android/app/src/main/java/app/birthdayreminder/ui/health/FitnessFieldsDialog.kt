package app.birthdayreminder.ui.health

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.birthdayreminder.domain.FitnessConfig
import app.birthdayreminder.domain.FitnessFields

private fun kindText(f: FitnessFields.Field): String = when (f.kind) {
    FitnessFields.Kind.WEIGHT -> "kg"
    FitnessFields.Kind.COUNT -> "次数" + (if (f.unit != "次") "（${f.unit}）" else "") + "·可记组数"
    FitnessFields.Kind.TIME -> "时间（${f.unit}）"
    FitnessFields.Kind.DISTANCE -> "距离（${f.unit}）"
    FitnessFields.Kind.NUMBER -> "数字" + (if (f.unit.isNotBlank()) "（${f.unit}）" else "")
    FitnessFields.Kind.BODY -> "身体数据" + (if (f.unit.isNotBlank()) "（${f.unit}）" else "") + "·不累加"
}

/**
 * 健身项目设置：上下箭头调整顺序；开关控制显示 / 隐藏；添加自定义项目；自定义项目可以改名、删除。
 * 每一步改动立刻保存。
 */
@Composable
fun FitnessFieldsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(FitnessFields.ALL) }
    fun commit(l: List<FitnessFields.Field>) { list = l; FitnessFieldStore.save(ctx, l) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FitnessFields.Field?>(null) }
    var confirmDelete by remember { mutableStateOf<FitnessFields.Field?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("健身项目设置") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("用箭头调整顺序，开关控制在记录页里显示还是隐藏。隐藏的项目以前记的数据不会丢。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEachIndexed { i, f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.label + (if (f.custom) " ✎" else ""),
                                color = if (f.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(kindText(f) + (if (f.custom) " · 自定义" else ""), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { commit(FitnessConfig.move(list, i, -1)) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(onClick = { commit(FitnessConfig.move(list, i, 1)) }, enabled = i < list.lastIndex, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                        }
                        Switch(checked = f.visible, onCheckedChange = { v -> commit(FitnessConfig.setVisible(list, f.key, v)) })
                    }
                    if (f.custom) {
                        Row(modifier = Modifier.padding(start = 8.dp)) {
                            TextButton(onClick = { renaming = f }) { Text("改名") }
                            TextButton(onClick = { confirmDelete = f }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = { adding = true }) { Text("＋ 添加自定义项目") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )

    if (adding) AddFieldDialog(list, onDismiss = { adding = false }) { l -> commit(l); adding = false }

    renaming?.let { f ->
        var name by remember(f.key) { mutableStateOf(f.label) }
        var err by remember(f.key) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("改名") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(FitnessConfig.MAX_NAME) }, singleLine = true)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (val r = FitnessConfig.renameCustom(list, f.key, name)) {
                        is FitnessConfig.Result.Ok -> { commit(r.list); renaming = null }
                        is FitnessConfig.Result.Bad -> err = r.message
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } }
        )
    }

    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除“${f.label}”？") },
            text = { Text("删除后这个项目以前记的数据在列表里不再显示（数据本身还在，重新添加同名项目也找不回来）。如果只是暂时不用，建议用开关隐藏。") },
            confirmButton = {
                TextButton(onClick = { commit(FitnessConfig.removeCustom(list, f.key)); confirmDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}

/** 添加自定义项目：名字 + 类型（次数 / 时间 / 距离 / 其它数字），“其它数字”可以填单位 */
@Composable
private fun AddFieldDialog(list: List<FitnessFields.Field>, onDismiss: () -> Unit, onAdded: (List<FitnessFields.Field>) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(FitnessFields.Kind.COUNT) }
    var unit by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(FitnessConfig.MAX_NAME) }, singleLine = true,
                    label = { Text("名字，比如：跳绳、游泳、平板支撑") })
                Text("类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FitnessConfig.CUSTOM_KINDS.forEach { (k, l) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(value = unit, onValueChange = { unit = it.take(6) }, singleLine = true,
                    label = { Text("单位（选填）") },
                    placeholder = { Text(FitnessConfig.defaultUnit(kind).ifEmpty { "比如：步、千卡、层" }) })
                Text(when (kind) {
                    FitnessFields.Kind.COUNT -> "填整数，还可以记组数。适合跳绳、仰卧起坐这类。"
                    FitnessFields.Kind.TIME -> "填分钟数（整数）。适合游泳、瑜伽、平板支撑这类。"
                    FitnessFields.Kind.DISTANCE -> "填公里数，可以到小数点后两位。适合骑车、游泳距离这类。"
                    FitnessFields.Kind.BODY -> "测量值（一位小数），不累加，收在“身体数据”里。适合腕围、体温这类。"
                    else -> "填任意数字（可带两位小数），单位自己定，比如步数、千卡、爬楼层数。"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = FitnessConfig.addCustom(list, name, kind, unit, FitnessFieldStore.newKey())) {
                    is FitnessConfig.Result.Ok -> { onAdded(r.list); Toast.makeText(ctx, "已添加“${name.trim()}”", Toast.LENGTH_SHORT).show() }
                    is FitnessConfig.Result.Bad -> err = r.message
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
