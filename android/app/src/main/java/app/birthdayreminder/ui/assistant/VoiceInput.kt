package app.birthdayreminder.ui.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.rememberCoroutineScope
import app.birthdayreminder.ai.AzureSpeech
import app.birthdayreminder.ai.AzureSpeechClient
import app.birthdayreminder.ai.AzureSpeechRecorder
import app.birthdayreminder.ai.SpeechConfig
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.launch

/**
 * 按住说话的麦克风按钮：按下开始听，松开结束，识别出的文字交给 onResult（直接发给 AI）。
 * 两种识别方式（AI 设置 → 语音输入 里选）：
 *  - 手机自带：用手机的语音识别服务；没有的话退回系统语音输入界面；
 *  - 语音服务（Azure / Google）：App 自己录音，松手后发给所选的服务识别（不依赖手机有没有识别服务）。
 *
 * onState：听的过程中把状态和已经识别出的部分文字告诉外面（显示“正在听…”和实时文字），null 表示没在听。
 */
@Composable
fun VoiceInputButton(
    enabled: Boolean,
    onState: (String?) -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val ctx = LocalContext.current
    val stateCb = rememberUpdatedState(onState)
    val resultCb = rememberUpdatedState(onResult)
    val errorCb = rememberUpdatedState(onError)
    val available = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    val holder = remember { arrayOf<String?>(null) }    // 最后一次的部分结果（松手时识别结果可能还没回来）
    val scope = rememberCoroutineScope()

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { stateCb.value("正在听…松开发送") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { stateCb.value("正在识别…") }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) { holder[0] = t; stateCb.value("🎤 $t") }
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: holder[0]
                holder[0] = null
                stateCb.value(null)
                if (!t.isNullOrBlank()) resultCb.value(t) else errorCb.value("没有听清，再说一次试试")
            }
            override fun onError(error: Int) {
                val partial = holder[0]
                holder[0] = null
                stateCb.value(null)
                // 已经识别出一部分时就用这部分，不算失败
                if (!partial.isNullOrBlank()) { resultCb.value(partial); return }
                errorCb.value(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，再说一次试试"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别需要联网，请检查网络"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正忙，稍后再试"
                    else -> "语音识别出错了（代码 $error），可以改用输入法里的语音输入"
                })
            }
        })
        onDispose { try { recognizer?.destroy() } catch (e: Exception) { } }
    }

    // 手机没有可以直接调用的识别服务时：打开系统的语音输入界面（说完自动返回）
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) resultCb.value(t)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) errorCb.value("没有麦克风权限，没法语音输入。可以到系统设置里给“日迹”打开麦克风权限")
        else errorCb.value("已经允许麦克风，再按住说话就行")
    }

    fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "说说要记什么，比如：明天下午三点开会")
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(enabled, available) {
                detectTapGestures(onPress = {
                    if (!enabled) return@detectTapGestures
                    app.birthdayreminder.ai.TtsPlayer.stop()      // 先停掉正在朗读的回复，免得录进去
                    // ---------- 语音服务（Azure / Google）：自己录音，松手后上传识别 ----------
                    if (Prefs.voiceEngine(ctx) == "service") {
                        if (!SpeechConfig.configured(ctx)) {
                            errorCb.value(SpeechConfig.notConfiguredText(ctx))
                            return@detectTapGestures
                        }
                        val provider = SpeechConfig.provider(ctx)
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }
                        val rec = AzureSpeechRecorder()
                        var recordError: String? = null
                        val job = scope.launch { try { rec.record() } catch (e: Exception) { recordError = e.message ?: "录音失败" } }
                        stateCb.value("正在听…松开发送")
                        tryAwaitRelease()          // 一直按着就一直录
                        rec.stop()
                        scope.launch {
                            job.join()
                            val pcm = rec.pcm()
                            when {
                                recordError != null -> { stateCb.value(null); errorCb.value(recordError!!) }
                                pcm.size < AzureSpeech.SAMPLE_RATE * 2 * AzureSpeech.MIN_MS / 1000 -> {
                                    stateCb.value(null); errorCb.value("按的时间太短了，说话时请一直按着")
                                }
                                else -> {
                                    stateCb.value("正在识别…")
                                    val r = when (provider) {
                                        "gemini" -> SpeechConfig.geminiRecognize(ctx, pcm)
                                        "google" -> SpeechConfig.googleRecognize(Prefs.googleSpeechKey(ctx), pcm)
                                        else -> AzureSpeechClient.recognize(Prefs.azureSpeechKey(ctx), AzureSpeech.cleanRegion(Prefs.azureSpeechRegion(ctx))!!, pcm)
                                    }
                                    stateCb.value(null)
                                    when (r) {
                                        is AzureSpeech.Result.Ok -> resultCb.value(r.text)
                                        is AzureSpeech.Result.Fail -> errorCb.value(r.message)
                                    }
                                }
                            }
                        }
                        return@detectTapGestures
                    }
                    // ---------- 手机自带的语音识别 ----------
                    if (recognizer == null) {
                        // 没有直接可用的识别服务：点一下打开系统语音输入界面
                        val i = speechIntent()
                        if (i.resolveActivity(ctx.packageManager) != null) fallback.launch(i)
                        else errorCb.value("这台手机没有可用的语音识别服务，可以用输入法键盘上的麦克风输入")
                        return@detectTapGestures
                    }
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                        return@detectTapGestures
                    }
                    holder[0] = null
                    stateCb.value("正在准备…")
                    recognizer.startListening(speechIntent())
                    tryAwaitRelease()          // 一直按着就一直听
                    recognizer.stopListening()  // 松手：结束录音，等识别结果
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Mic, contentDescription = "按住说话",
            tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
