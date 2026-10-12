package app.birthdayreminder.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthdayreminder.cloud.BaiduAuth
import app.birthdayreminder.cloud.BaiduPanStorage
import app.birthdayreminder.cloud.CloudConfig
import app.birthdayreminder.cloud.CloudSync
import app.birthdayreminder.domain.BaiduPanApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 云盘备份 → 百度网盘：填开放平台应用的 AppKey、SecretKey、应用文件夹，再用设备码登录。
 * 登录时在浏览器打开百度的授权页确认，日迹在后台等授权结果。
 */
@Composable
internal fun BaiduPanSection(onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver by BaiduAuth.version
    var key by remember { mutableStateOf(CloudConfig.bdKey(ctx)) }
    var secret by remember { mutableStateOf(CloudConfig.bdSecret(ctx)) }
    var folder by remember { mutableStateOf(CloudConfig.bdFolder(ctx)) }
    var device by remember { mutableStateOf<BaiduPanApi.Device?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    val signedIn = remember(ver) { BaiduAuth.signedIn(ctx) }

    OutlinedTextField(value = key, onValueChange = { key = it.trim() }, singleLine = true, label = { Text("AppKey") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = secret, onValueChange = { secret = it.trim() }, singleLine = true, label = { Text("SecretKey") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = folder, onValueChange = { folder = it }, singleLine = true, label = { Text("应用文件夹（/apps/你的应用名）") }, modifier = Modifier.fillMaxWidth())
    Text("在百度网盘开放平台创建应用后，在应用详情里能看到 AppKey 和 SecretKey。备份放在网盘的“我的应用数据 / 应用名”文件夹里。" +
        "SecretKey 加密保存在手机上，不会放进备份文件。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    fun save() { CloudConfig.saveBaiduApp(ctx, key, secret, folder); folder = CloudConfig.bdFolder(ctx) }

    if (signedIn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已登录：" + CloudConfig.bdAccount(ctx).ifBlank { "百度账号" }, modifier = Modifier.weight(1f))
            TextButton(onClick = { BaiduAuth.signOut(ctx); CloudSync.schedule(ctx) }) { Text("退出登录") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                save(); CloudSync.schedule(ctx); busy = true
                scope.launch {
                    val err = BaiduPanStorage(ctx.applicationContext).test()
                    busy = false
                    onMessage(err?.let { "连接失败：$it" } ?: "连接成功，已保存")
                }
            }) { Text(if (busy) "正在连接…" else "保存并测试连接") }
        }
    } else {
        OutlinedButton(enabled = !busy, onClick = {
            save(); busy = true
            scope.launch {
                try {
                    val d = BaiduAuth.requestDevice(ctx)
                    device = d
                    openBrowser(ctx, BaiduPanApi.verifyPage(d))
                    job = scope.launch {
                        val err = BaiduAuth.waitForGrant(ctx, d)
                        device = null; busy = false
                        if (err == null) CloudSync.schedule(ctx)
                        onMessage(err ?: "百度网盘已登录")
                    }
                } catch (e: Exception) {
                    busy = false
                    onMessage(e.message ?: "登录失败")
                }
            }
        }) { Text(if (busy) "等待授权…" else "保存并登录百度网盘") }
    }

    device?.let { d ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("在浏览器里确认授权") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已经打开百度的授权页，登录百度账号后点“授权”。如果页面要你输入用户码，填这个：")
                    Text(d.userCode, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("授权完回到日迹，这里会自动完成登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { openBrowser(ctx, BaiduPanApi.verifyPage(d)) }) { Text("再次打开授权页") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("用户码", d.userCode))
                        onMessage("已复制用户码")
                    }) { Text("复制用户码") }
                    TextButton(onClick = { job?.cancel(); job = null; device = null; busy = false }) { Text("取消") }
                }
            }
        )
    }
}

private fun openBrowser(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}
