package app.birthdayreminder.ui.alarm

import app.birthdayreminder.privacy.SecureWindow
import app.birthdayreminder.ui.common.TgTopBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.AlarmEngine
import app.birthdayreminder.data.Alarm
import app.birthdayreminder.domain.AlarmTriggers
import app.birthdayreminder.ui.common.FieldLabel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

private val REPEAT_OPTIONS = listOf(
    "once" to "仅一次", "daily" to "每天", "workday" to "工作日", "restday" to "休息日", "custom" to "自定义"
)
private val SNOOZE_OPTIONS = listOf(0 to "不稍后提醒", 5 to "5 分钟", 10 to "10 分钟", 15 to "15 分钟", 30 to "30 分钟")
// 周一到周日，数字对应 0=周日 … 6=周六
private val WEEK_CHIPS = listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 0 to "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    id: String?,
    onBack: () -> Unit, initialPrivate: Boolean = false,
    vm: AlarmViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<Alarm?>(null) }
    var time by remember { mutableStateOf("07:00") }
    var label by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("workday") }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var snooze by remember { mutableStateOf(10) }
    var vibrate by remember { mutableStateOf(true) }
    var isPrivate by remember { mutableStateOf(initialPrivate) }      // 在隐私空间里新建的就是私密的；编辑已有的保持原样
    SecureWindow(isPrivate)                                    // 私密内容不让截屏
    var soundPath by remember { mutableStateOf<String?>(null) }
    var soundName by remember { mutableStateOf<String?>(null) }
    var newSoundPath by remember { mutableStateOf<String?>(null) }   // 本次编辑期间新选的铃声，取消时要删掉
    var error by remember { mutableStateOf<String?>(null) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            vm.get(id)?.let { a ->
                existing = a
                time = a.time; label = a.label; repeat = a.repeat
                days = a.days.toSet(); snooze = a.snooze; vibrate = a.vibrate; isPrivate = a.isPrivate
                soundPath = a.soundPath; soundName = a.soundName
            }
        }
    }

    // 离开页面时：停掉试听；没保存的话，把这次新选的铃声文件删掉，不留垃圾
    DisposableEffect(Unit) {
        onDispose {
            AlarmEngine.stopPreview()
            if (!saved) AlarmEngine.deleteSound(context, newSoundPath)
        }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = AlarmEngine.importSound(context, uri)) {
                is AlarmEngine.SoundResult.Ok -> {
                    AlarmEngine.deleteSound(context, newSoundPath)      // 这次编辑里之前选过的，换掉
                    newSoundPath = r.path
                    soundPath = r.path
                    soundName = r.name
                    error = null
                }
                is AlarmEngine.SoundResult.Fail -> error = r.message
            }
        }
    }

    fun save() {
        if (repeat == "custom" && days.isEmpty()) { error = "自定义重复请至少选一天"; return }
        error = null
        val now = System.currentTimeMillis()
        val a = Alarm(
            id = existing?.id ?: UUID.randomUUID().toString(),
            time = time,
            label = label.trim().take(20),
            repeat = repeat,
            days = if (repeat == "custom") days.sorted() else emptyList(),
            snooze = snooze,
            vibrate = vibrate,
            soundPath = soundPath,
            soundName = soundName,
            enabled = true,
            onceAt = if (repeat == "once") AlarmTriggers.nextOnceAt(time, now, ZoneId.systemDefault()) else null,
            isPrivate = isPrivate
        )
        // 原来有自定义铃声、这次换了或者清掉了：旧文件删掉
        val oldPath = existing?.soundPath
        if (oldPath != null && oldPath != soundPath) AlarmEngine.deleteSound(context, oldPath)
        saved = true
        vm.save(a, onBack)
    }

    Scaffold(
        topBar = {
            TgTopBar(
                title = { Text(if (existing != null) "编辑闹钟" else "添加闹钟") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = { TextButton(onClick = { save() }) { Text("保存") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FieldLabel("时间")
            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                Text(time, style = MaterialTheme.typography.headlineMedium)
            }

            OutlinedTextField(
                value = label, onValueChange = { label = it.take(20) },
                label = { Text("标签（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            FieldLabel("重复")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                REPEAT_OPTIONS.forEach { (v, l) ->
                    FilterChip(selected = repeat == v, onClick = { repeat = v }, label = { Text(l) })
                }
            }
            if (repeat == "custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    WEEK_CHIPS.forEach { (v, l) ->
                        FilterChip(
                            selected = v in days,
                            onClick = { days = if (v in days) days - v else days + v },
                            label = { Text("周$l") }
                        )
                    }
                }
            }
            if (repeat == "workday") {
                Text("按法定节假日和调休补班判断：放假日不响，周末补班的那天会响。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (repeat == "restday") {
                Text("周末和法定节假日响，调休补班的那天不响。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            FieldLabel("稍后提醒")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SNOOZE_OPTIONS.forEach { (v, l) ->
                    FilterChip(selected = snooze == v, onClick = { snooze = v }, label = { Text(l) })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("振动", modifier = Modifier.weight(1f))
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }

            FieldLabel("铃声")
            Text(soundName ?: "系统默认闹钟铃声", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text("选择本机音频") }
                OutlinedButton(onClick = { AlarmEngine.previewSound(context, soundPath) }) { Text("试听") }
                if (soundPath != null) {
                    OutlinedButton(onClick = {
                        if (soundPath == newSoundPath) { AlarmEngine.deleteSound(context, newSoundPath); newSoundPath = null }
                        soundPath = null; soundName = null
                    }) { Text("恢复默认") }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这个闹钟", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showTime) {
        val initial = try { LocalTime.parse(time) } catch (e: Exception) { LocalTime.of(7, 0) }
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { time = "%02d:%02d".format(state.hour, state.minute); showTime = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除闹钟") },
            text = { Text("确定删除这个闹钟吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saved = true
                    existing?.let { vm.delete(it, onBack) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
