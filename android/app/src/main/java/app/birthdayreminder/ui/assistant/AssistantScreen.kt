package app.birthdayreminder.ui.assistant

import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.SoftCard
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.birthdayreminder.ai.SystemTts
import app.birthdayreminder.ai.GeminiSpeech
import app.birthdayreminder.ai.GoogleSpeech
import app.birthdayreminder.ai.SpeechConfig
import app.birthdayreminder.ai.AiModels
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import app.birthdayreminder.ai.TtsPlayer
import app.birthdayreminder.ai.AzureTts
import androidx.compose.foundation.verticalScroll
import app.birthdayreminder.ai.AzureSpeechClient
import app.birthdayreminder.ai.AzureSpeech
import androidx.compose.material3.HorizontalDivider
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Switch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.ai.AiClient
import app.birthdayreminder.ai.AiConnTest
import app.birthdayreminder.ai.AiProviders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.birthdayreminder.ai.AiTools
import app.birthdayreminder.notify.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(onBack: () -> Unit, vm: AssistantViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    var voiceState by remember { mutableStateOf<String?>(null) }   // 语音输入时显示“正在听…”和实时文字
    val playing by TtsPlayer.playing.collectAsState()               // 正在朗读哪一条
    val ctx = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 只显示 user / assistant；工具结果并进对应那条 assistant 消息下面
    val visible = vm.chat.filter { it.role != "tool" }
    LaunchedEffect(vm.chat.size, vm.busy, vm.pendingConfirm) {
        // 新消息、思考中、确认卡片出现时，滚到最底下
        // 列表里的条数：提示语(没消息时) / 每条消息 + 确认卡片 + “正在思考”
        val n = (if (visible.isEmpty()) 1 else visible.size) +
            (if (vm.pendingConfirm != null) 1 else 0) + (if (vm.busy && vm.pendingConfirm == null) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Scaffold(
        topBar = {
            TgTopBar(
                title = { Text("AI 助手 · " + (AiProviders.LABEL[vm.provider] ?: "")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = { vm.clear() }) { Text("清空") }
                    TextButton(onClick = { showSettings = true }) { Text("设置") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            "试着说：“帮我加一个提醒，明天下午3点开会”，或者“提前一天提醒我妈妈农历八月十五的生日”。",
                            modifier = Modifier.padding(top = 40.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visible.size) { i ->
                    val m = visible[i]
                    if (m.role == "user") {
                        Bubble(m.display ?: m.text, mine = true)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 调用工具时顺带说的开场白（“我先读一下数据”，有时还是英文）不显示：下面那行“🔍 读取……”已经说明了在做什么
                            if (m.text.isNotBlank() && m.calls.isEmpty()) {
                                // AI 回复旁边的 🔊：点一下用 Azure 语音朗读，正在念时变成 ⏹，再点停止
                                val key = vm.ttsKey(vm.chat.indexOf(m))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Box(modifier = Modifier.weight(1f)) { Bubble(m.text, mine = false) }
                                    IconButton(onClick = {
                                        if (playing == key) TtsPlayer.stop()
                                        else TtsPlayer.speak(ctx, key, m.text) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
                                    }, modifier = Modifier.size(36.dp)) {
                                        Text(if (playing == key) "⏹" else "🔊")
                                    }
                                }
                            }
                            m.calls.forEach { c ->
                                val line = AiTools.describeToolCall(c.name, vm.toolResultFor(c.id))
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                // 确认卡片：AI 想添加数据时，等你点“确认”
                vm.pendingConfirm?.let { lines ->
                    item(key = "confirm") {
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("AI 想帮你添加：", fontWeight = FontWeight.SemiBold)
                                lines.forEach { Text(it) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { vm.answerConfirm(true) }) { Text("确认") }
                                    OutlinedButton(onClick = { vm.answerConfirm(false) }) { Text("取消") }
                                }
                            }
                        }
                    }
                }
                if (vm.busy && vm.pendingConfirm == null) item { Text("AI 正在思考…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp)) }
            }
            // 快捷按钮：本周 / 本月总结
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { vm.report("this_week") }, enabled = !vm.busy, label = { Text("📊 本周总结") })
                AssistChip(onClick = { vm.report("this_month") }, enabled = !vm.busy, label = { Text("📊 本月总结") })
            }
            voiceState?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 按住说话：松开后识别出的文字直接发给 AI
                VoiceInputButton(
                    enabled = !vm.busy,
                    onState = { voiceState = it },
                    onResult = { text -> vm.send(text, viaVoice = true) },
                    onError = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
                )
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text("输入消息，或按住🎤说话") },
                    modifier = Modifier.weight(1f), maxLines = 4, enabled = !vm.busy
                )
                IconButton(onClick = { vm.send(input); input = "" }, enabled = !vm.busy && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }

    if (showSettings) AiSettingsDialog(vm = vm, onDismiss = { showSettings = false })
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            // AI 的回复常带 Markdown（**加粗**、列表、表格）：按格式显示，不然星号原样露出来；自己发的按原文
            if (mine) Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            else app.birthdayreminder.ui.diary.MarkdownText(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/** 选服务商、填 Key / 模型 / 接口地址。都只存在本机。 */
@Composable
private fun AiSettingsDialog(vm: AssistantViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = vm.provider
    var key by remember(p) { mutableStateOf(Prefs.aiKey(context, p)) }
    var model by remember(p) { mutableStateOf(Prefs.aiModel(context, p, AiProviders.DEFAULT_MODEL.getValue(p))) }
    var url by remember(p) { mutableStateOf(Prefs.aiUrl(context, p, AiProviders.DEFAULT_URL.getValue(p))) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    // 测试结果：一行一条（第一步、第二步），换了服务商就清空
    var testLines by remember(p) { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    fun runTest() {
        AiConnTest.precheck(key, model, url)?.let { testLines = listOf(false to it); return }
        testing = true
        testLines = listOf(true to "正在测试第 1 步：基本对话…")
        testJob = scope.launch {
            try {
                val (ms, reply) = AiClient.testBasic(p, key, model, url)
                val step1 = true to AiConnTest.basicOk(ms, reply)
                testLines = listOf(step1, true to "正在测试第 2 步：工具调用…")
                try {
                    val t = AiClient.testToolRoundTrip(p, key, model, url)
                    testLines = listOf(step1, if (t != null) true to AiConnTest.toolOk(t) else false to AiConnTest.TOOL_NOT_CALLED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    testLines = listOf(step1, false to "❌ 工具调用失败：" + AiClient.errorText(e))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                testLines = listOf(false to "❌ " + AiClient.errorText(e))
            } finally {
                testing = false
            }
        }
    }

    fun saveAll() {
        Prefs.setAiKey(context, p, key)
        Prefs.setAiModel(context, p, model)
        Prefs.setAiUrl(context, p, url)
    }

    AlertDialog(
        onDismissRequest = { testJob?.cancel(); saveAll(); onDismiss() },
        title = { Text("AI 助手设置") },
        confirmButton = { TextButton(onClick = { testJob?.cancel(); saveAll(); onDismiss() }) { Text("完成") } },
        text = {
            // 内容比较多，小屏幕上可以上下滚动，不会把底部按钮挤出屏幕
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("模型提供方", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AiProviders.IDS.forEach { id ->
                        FilterChip(selected = p == id, onClick = { saveAll(); vm.changeProvider(id) }, label = { Text(AiProviders.LABEL.getValue(id)) })
                    }
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("${AiProviders.LABEL[p]} API Key") },
                    placeholder = { Text("在 ${AiProviders.KEY_HINT[p]} 获取") }
                )
                // 模型：下拉选择（内置常用 + 从服务商拉取的），也可以直接手动输入
                var menuOpen by remember { mutableStateOf(false) }
                var fetched by remember(p) { mutableStateOf<List<String>>(emptyList()) }
                var fetchMsg by remember(p) { mutableStateOf<String?>(null) }
                var fetching by remember { mutableStateOf(false) }
                val options = remember(p, fetched, model) { AiModels.merge(p, fetched, model) }
                Box {
                    OutlinedTextField(
                        value = model, onValueChange = { model = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型") },
                        trailingIcon = { IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.ArrowDropDown, contentDescription = "选择模型") } }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        options.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m + (if (m == AiProviders.DEFAULT_MODEL[p]) "（推荐）" else "")) },
                                onClick = { model = m; menuOpen = false }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !fetching, onClick = {
                        fetching = true; fetchMsg = "获取中…"
                        scope.launch {
                            val (list, err) = AiClient.listModels(p, key, url)
                            fetching = false
                            if (list.isNotEmpty()) { fetched = list; fetchMsg = "已获取 ${list.size} 个可用模型，点模型框右边的箭头选择"; menuOpen = true }
                            else fetchMsg = err
                        }
                    }) { Text(if (fetching) "获取中…" else "获取可用模型") }
                }
                fetchMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("接口地址") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runTest() }, enabled = !testing) { Text(if (testing) "测试中…" else "测试连接") }
                    if (testing) TextButton(onClick = { testJob?.cancel(); testing = false; testLines = emptyList() }) { Text("取消") }
                }
                testLines.forEach { (ok, line) ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }
                // ---------------- 语音：说话转文字 + 回复朗读（Azure / Google） ----------------
                HorizontalDivider()
                Text("语音", style = MaterialTheme.typography.labelLarge)
                var spProvider by remember { mutableStateOf(Prefs.speechProvider(context)) }
                var engine by remember { mutableStateOf(Prefs.voiceEngine(context)) }
                var spKey by remember { mutableStateOf(Prefs.azureSpeechKey(context)) }
                var spRegion by remember { mutableStateOf(Prefs.azureSpeechRegion(context)) }
                var gKey by remember { mutableStateOf(Prefs.googleSpeechKey(context)) }
                var spTest by remember { mutableStateOf<String?>(null) }
                var spTesting by remember { mutableStateOf(false) }
                var ttsAuto by remember { mutableStateOf(Prefs.ttsAuto(context)) }
                var ttsRate by remember { mutableStateOf(Prefs.ttsRate(context)) }

                Text("语音服务（说话转文字、朗读回复都用它）", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    SpeechConfig.PROVIDERS.forEach { (v, l) ->
                        FilterChip(selected = spProvider == v, onClick = { spProvider = v; Prefs.setSpeechProvider(context, v); spTest = null }, label = { Text(l) })
                    }
                }
                if (spProvider == "gemini") {
                    Text("直接用上面 Gemini 服务商里填的 API Key，不用另外申请。说话转文字用你选的 Gemini 模型，朗读用 ${GeminiSpeech.TTS_MODEL}。" +
                        (if (Prefs.aiKey(context, "gemini").isBlank()) "\n⚠️ 还没有填 Gemini 的 Key：先在上面选 Gemini 服务商、填好 Key。" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (spProvider == "google") {
                    OutlinedTextField(value = gKey, onValueChange = { gKey = it; Prefs.setGoogleSpeechKey(context, it) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), label = { Text("Google API Key") },
                        placeholder = { Text("Google Cloud 控制台 → API 和服务 → 凭据") })
                    Text("需要在 Google Cloud 控制台启用 “Cloud Speech-to-Text API” 和 “Cloud Text-to-Speech API”。国内网络一般要开代理才能连上。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(value = spKey, onValueChange = { spKey = it; Prefs.setAzureSpeechKey(context, it) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), label = { Text("Azure 语音 Key") },
                        placeholder = { Text("Azure 门户 → 语音服务资源 → 密钥和终结点") })
                    OutlinedTextField(value = spRegion, onValueChange = { spRegion = it; Prefs.setAzureSpeechRegion(context, it) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), label = { Text("区域（位置/区域）") }, placeholder = { Text("比如 eastasia") })
                }
                OutlinedButton(enabled = !spTesting, onClick = {
                    if (spProvider == "gemini") {
                        saveAll()      // 上面刚改的 Gemini Key 先存下来
                        spTesting = true; spTest = "检查中…"
                        scope.launch {
                            val err = SpeechConfig.geminiCheckKey(context)
                            spTesting = false
                            spTest = if (err == null) "✅ Gemini Key 可用" else "❌ $err"
                        }
                        return@OutlinedButton
                    }
                    if (spProvider == "google") {
                        if (gKey.isBlank()) { spTest = "❌ 请先填 Key"; return@OutlinedButton }
                        spTesting = true; spTest = "检查中…"
                        scope.launch {
                            val err = SpeechConfig.googleCheckKey(gKey)
                            spTesting = false
                            spTest = if (err == null) "✅ Key 可用（朗读的 API 已启用）" else "❌ $err"
                        }
                    } else {
                        val region = AzureSpeech.cleanRegion(spRegion)
                        when {
                            spKey.isBlank() -> spTest = "❌ 请先填 Key"
                            region == null -> spTest = "❌ 区域写法不对，比如 eastasia（小写字母，没有空格）"
                            else -> {
                                spTesting = true; spTest = "检查中…"
                                scope.launch {
                                    val err = AzureSpeechClient.checkKey(spKey.trim(), region)
                                    spTesting = false
                                    spTest = if (err == null) "✅ Key 可用" else "❌ $err"
                                }
                            }
                        }
                    }
                }) { Text("检查 Key") }
                spTest?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                Text("说话转文字（按住🎤）", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = engine == "service", onClick = { engine = "service"; Prefs.setVoiceEngine(context, "service") },
                        label = { Text("用 " + (SpeechConfig.PROVIDERS.firstOrNull { it.first == spProvider }?.second ?: "语音服务")) })
                    FilterChip(selected = engine == "system", onClick = { engine = "system"; Prefs.setVoiceEngine(context, "system") }, label = { Text("手机自带") })
                }

                Text("朗读 AI 回复", style = MaterialTheme.typography.bodyMedium)
                var ttsEngine by remember { mutableStateOf(Prefs.ttsEngine(context)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = ttsEngine == "cloud", onClick = { ttsEngine = "cloud"; Prefs.setTtsEngine(context, "cloud") },
                        label = { Text("云端（" + (SpeechConfig.PROVIDERS.firstOrNull { it.first == spProvider }?.second ?: "") + "）") })
                    FilterChip(selected = ttsEngine == "system", onClick = { ttsEngine = "system"; Prefs.setTtsEngine(context, "system") }, label = { Text("手机自带") })
                }
                Text(
                    if (ttsEngine == "system") "用手机系统的朗读，不联网、不花钱。声音和语音包在系统设置里换。"
                    else "云端声音更自然；没设置好、没网、没开代理或额度用完时，会自动改用手机自带的朗读。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    if (!SystemTts.openSettings(context)) Toast.makeText(context, "打不开系统朗读设置，请在系统设置里搜索“文字转语音”", Toast.LENGTH_LONG).show()
                }) { Text("打开系统朗读设置（换引擎、装中文语音包）") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AzureTts.AUTO.forEach { (v, l) -> FilterChip(selected = ttsAuto == v, onClick = { ttsAuto = v; Prefs.setTtsAuto(context, v) }, label = { Text("自动：$l") }) }
                }
                // 声音：下拉选择；可以从云端拉取这家服务商当前所有的中文、英文声音
                var voiceTick by remember { mutableStateOf(0) }
                val voiceOpts = remember(spProvider, voiceTick) { SpeechConfig.voiceOptions(context, spProvider) }
                val curVoice = remember(spProvider, voiceTick) { SpeechConfig.currentVoice(context, spProvider) }
                var voiceMenu by remember { mutableStateOf(false) }
                var voiceFetching by remember { mutableStateOf(false) }
                var voiceMsg by remember(spProvider) { mutableStateOf<String?>(null) }
                Box {
                    OutlinedButton(onClick = { voiceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("声音：" + (voiceOpts.firstOrNull { it.id == curVoice }?.label ?: curVoice), maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                        voiceOpts.forEach { v ->
                            DropdownMenuItem(
                                text = { Text((if (v.id == curVoice) "✓ " else "") + v.label) },
                                onClick = { SpeechConfig.setVoice(context, spProvider, v.id); voiceTick++; voiceMenu = false }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !voiceFetching, onClick = {
                        if (spProvider == "gemini") saveAll()
                        voiceFetching = true; voiceMsg = "获取中…"
                        scope.launch {
                            val (list, err) = SpeechConfig.fetchVoices(context, spProvider)
                            voiceFetching = false
                            voiceTick++
                            voiceMsg = err ?: "已获取 ${list.size} 个中文、英文声音"
                        }
                    }) { Text(if (voiceFetching) "获取中…" else "从云端获取声音") }
                    Text(
                        voiceMsg ?: (if (SpeechConfig.hasCloudVoices(context, spProvider)) "共 ${voiceOpts.size} 个（云端）" else "现在是内置的几个常用声音"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AzureTts.RATES.forEach { (v, l) -> FilterChip(selected = ttsRate == v, onClick = { ttsRate = v; Prefs.setTtsRate(context, v) }, label = { Text("语速$l") }) }
                }
                val previewPlaying by TtsPlayer.playing.collectAsState()
                OutlinedButton(onClick = {
                    if (previewPlaying == "preview") TtsPlayer.stop()
                    else TtsPlayer.speak(context, "preview", "你好，我是日迹的 AI 助手。明天下午三点的开会提醒已经帮你加好了。") { spTest = "❌ $it" }
                }) { Text(if (previewPlaying == "preview") "停止试听" else "试听声音") }
                Text("“语音提问时”：你按住🎤说话提问，回复就自动念出来；打字提问时不出声。每条回复旁边的 🔊 也可以随时点。" +
                    "录音和要朗读的文字只发给所选的语音服务，不会保存。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()

                var confirmOn by remember { mutableStateOf(Prefs.aiConfirm(context)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("添加前先确认")
                        Text("AI 要添加生日、提醒、闹钟、日记时，先弹卡片等你点“确认”", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = confirmOn, onCheckedChange = { confirmOn = it; Prefs.setAiConfirm(context, it) })
                }
                Text(
                    "对话内容，以及 AI 决定查看的生日 / 提醒 / 日记，会发送给你选的服务商（用你自己的 Key）。" +
                        "AI 执行“添加”类操作时不会再确认，结果会显示在对话里，需要的话可以到对应页面手动删除。" +
                        "Key 和对话记录只保存在本机。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
