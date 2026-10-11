package app.birthdayreminder.ui.diary

import app.birthdayreminder.ui.common.TgTopBar
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.birthdayreminder.data.DiaryLocation
import app.birthdayreminder.domain.PoiItem
import app.birthdayreminder.domain.PoiSearch
import app.birthdayreminder.domain.SearchOutcome
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.delay

/**
 * 日记“添加位置”页面。
 *
 * 搜索框、搜索按钮、定位按钮、搜索结果列表全部是 App 原生控件（中文输入法、点击反馈都可靠），
 * 内嵌的 WebView 只负责画高德地图、点地图选点和跟高德服务器对话（复用你旧版里已经申请好的
 * Web端(JS API) Key，不用再申请 Android 平台专属的 Key）。
 * 点搜索的瞬间界面就会显示“搜索中…”；网页没加载好、搜索出错、超时，都会有明确的文字提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryLocationPickerScreen(
    vm: DiaryEditViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var key by remember { mutableStateOf(Prefs.amapKey(context)) }
    var secCode by remember { mutableStateOf(Prefs.amapSecCode(context)) }
    var keyInput by remember { mutableStateOf(key) }
    var secInput by remember { mutableStateOf(secCode) }
    var picked by remember { mutableStateOf(vm.location) }
    val dark = isSystemInDarkTheme()
    app.birthdayreminder.privacy.SecureWindow(vm.isPrivate)

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PoiItem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus: FocusManager = LocalFocusManager.current

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestOneShotLocation(context, webView) else message = "没有授予定位权限，请到系统设置里给“日迹”开启"
    }

    // 搜索发出去 15 秒还没结果，界面上要说一声，不能一直转
    LaunchedEffect(searching) {
        if (searching) {
            delay(15_000)
            if (searching) { searching = false; message = "搜索超时，请检查网络后重试" }
        }
    }

    fun startSearch() {
        val kw = query.trim()
        if (kw.isEmpty()) { message = "请先输入要搜索的地点"; return }
        keyboard?.hide()
        focus.clearFocus()
        val wv = webView
        if (wv == null) { message = "地图还没准备好，请稍等一下再试"; return }
        searching = true
        message = null
        results = emptyList()
        // 网页里的搜索函数还没就绪（网络慢、Key 有问题）时返回 0，直接提示，不让界面一直等
        wv.evaluateJavascript(
            "(typeof window.nativeSearch==='function')?(window.nativeSearch(${MapHtml.jsStr(kw)}),1):0"
        ) { r ->
            if (r != "1") { searching = false; message = "地图还没加载好，请检查网络、Key 和安全密钥后重试" }
        }
    }

    fun startLocate() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) requestOneShotLocation(context, webView) else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun choose(p: PoiItem) {
        picked = DiaryLocation(p.lat, p.lng, p.label)       // 直接记下来，不用等网页确认，“确定”立刻可用
        results = emptyList()
        message = null
        webView?.evaluateJavascript("window.nativePick && window.nativePick(${p.lat}, ${p.lng}, ${MapHtml.jsStr(p.label)})", null)
    }

    val onSearchResult = rememberUpdatedState { json: String ->
        searching = false
        when (val o = PoiSearch.parse(json)) {
            is SearchOutcome.Ok -> {
                results = o.items
                message = if (o.items.isEmpty()) "没有找到相关地点，换个关键词试试" else null
            }
            is SearchOutcome.Fail -> { results = emptyList(); message = o.message }
        }
    }

    Scaffold(
        topBar = {
            TgTopBar(
                title = { Text("添加位置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (key.isNotBlank()) {
                        TextButton(onClick = { picked?.let { vm.location = it }; onBack() }, enabled = picked != null) { Text("确定") }
                    }
                }
            )
        }
    ) { padding ->
        if (key.isBlank()) {
            Column(
                modifier = Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "第一次用需要填一下高德地图的 Key（和以前网页版“历”用的是同一个，去 console.amap.com 申请 " +
                        "Web端(JS API) 类型即可，只保存在本机）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput, onValueChange = { keyInput = it },
                    label = { Text("高德地图 Key") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secInput, onValueChange = { secInput = it },
                    label = { Text("安全密钥 JSCode（大部分新申请的Key都需要）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    Prefs.setAmapKey(context, keyInput.trim())
                    Prefs.setAmapSecCode(context, secInput.trim())
                    key = keyInput.trim()
                    secCode = secInput.trim()
                }) { Text("保存并继续") }
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("搜索地点，如：人民广场") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { startSearch() }),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { startSearch() }) { Text("搜索") }
                    TextButton(onClick = { startLocate() }) { Text("📍定位") }
                }
                if (searching) {
                    Text("搜索中…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                message?.let {
                    Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.error)
                }
                if (results.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 230.dp)) {
                        items(results) { p ->
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = if (p.address.isNotBlank()) { { Text(p.address) } } else null,
                                modifier = Modifier.clickable { choose(p) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                picked?.let {
                    Text("已选：" + it.address, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                AmapWebView(
                    key = key,
                    secCode = secCode,
                    dark = dark,
                    initial = vm.location,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onWebView = { webView = it },
                    onPicked = { picked = it },
                    onSearchResult = { json -> onSearchResult.value(json) }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AmapWebView(
    key: String,
    secCode: String,
    dark: Boolean,
    initial: DiaryLocation?,
    modifier: Modifier = Modifier,
    onWebView: (WebView) -> Unit,
    onPicked: (DiaryLocation) -> Unit,
    onSearchResult: (String) -> Unit
) {
    val onPickedState = rememberUpdatedState(onPicked)
    val onSearchState = rememberUpdatedState(onSearchResult)
    val main = remember { Handler(Looper.getMainLooper()) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                // 网页里的 JS 是在别的线程回调过来的，统一切回主线程再改界面状态
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onPicked(lat: Double, lng: Double, address: String) {
                        main.post { onPickedState.value(DiaryLocation(lat, lng, address)) }
                    }

                    @JavascriptInterface
                    fun onSearchResult(json: String) {
                        main.post { onSearchState.value(json) }
                    }

                    @JavascriptInterface
                    fun onError(message: String) { /* 页面里已经把原因显示在地址栏上了 */ }
                }, "Android")
                loadDataWithBaseURL(
                    "https://webapi.amap.com",
                    MapHtml.build(key, secCode, dark, initial),
                    "text/html", "utf-8", null
                )
                onWebView(this)
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun requestOneShotLocation(context: Context, webView: WebView?) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
    if (provider == null) {
        webView?.evaluateJavascript("window.onNativeLocationFailed && window.onNativeLocationFailed('请先打开手机定位服务')", null)
        return
    }
    try {
        lm.requestSingleUpdate(provider, { loc ->
            webView?.evaluateJavascript(
                "window.onNativeLocation && window.onNativeLocation(${loc.latitude}, ${loc.longitude})", null
            )
        }, Looper.getMainLooper())
    } catch (e: SecurityException) {
        webView?.evaluateJavascript("window.onNativeLocationFailed && window.onNativeLocationFailed('没有定位权限')", null)
    }
}
