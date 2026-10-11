package app.birthdayreminder.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 朗读 AI 的回复：按所选语音服务（Azure / Google）把文字合成 mp3，存到缓存再播放。
 * 同一时间只念一段；playing 告诉界面现在念的是哪一条（对应气泡上的 🔊 变成 ⏹）。
 */
object TtsPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: MediaPlayer? = null

    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing

    /** 什么时候都能朗读：云端没设置好就用手机自带 */
    fun configured(ctx: Context): Boolean = true

    fun stop() {
        job?.cancel(); job = null
        player?.let { try { it.stop() } catch (e: Exception) { }; it.release() }
        player = null
        SystemTts.stop()
        _playing.value = null
    }

    /** 这次打开 App 后有没有提示过“已改用手机自带朗读”（只提示一次，免得刷屏） */
    private var fallbackNoticed = false

    /** 用手机自带朗读念 */
    private fun speakSystem(app: Context, key: String, text: String, onError: (String) -> Unit) {
        SystemTts.onFinish = { k -> if (_playing.value == k) _playing.value = null }
        _playing.value = key
        val err = SystemTts.speak(app, key, AzureTts.clean(text), Prefs.ttsRate(app))
        if (err != null) { _playing.value = null; onError(err) }
    }

    /** 云端念不了：改用手机自带，第一次回退时说一下原因 */
    private fun fallback(app: Context, key: String, text: String, reason: String, onError: (String) -> Unit) {
        if (!fallbackNoticed) { fallbackNoticed = true; onError(SystemTtsLogic.fallbackNotice(reason)) }
        speakSystem(app, key, text, onError)
    }

    /**
     * 朗读一段文字；key 标识是哪条消息。
     * 朗读引擎选“手机自带”就直接用手机念；选“云端”时，没配置好、连不上、额度用完、播放失败……都会自动改用手机自带朗读。
     */
    fun speak(ctx: Context, key: String, text: String, onError: (String) -> Unit = {}) {
        stop()
        val app = ctx.applicationContext
        if (AzureTts.clean(text).isBlank()) return
        if (Prefs.ttsEngine(app) == "system") { speakSystem(app, key, text, onError); return }
        if (!SpeechConfig.configured(app)) { fallback(app, key, text, "语音服务还没设置好", onError); return }
        val provider = SpeechConfig.provider(app)
        _playing.value = key
        job = scope.launch {
            val file = try {
                if (provider == "gemini") {
                    val bytes = SpeechConfig.geminiSynthesize(app, text, Prefs.ttsVoiceGemini(app), Prefs.ttsRate(app))
                    withContext(Dispatchers.IO) { File(app.cacheDir, "tts.wav").apply { writeBytes(bytes) } }
                } else if (provider == "google") {
                    val bytes = SpeechConfig.googleSynthesize(Prefs.googleSpeechKey(app), text, Prefs.ttsVoiceGoogle(app), Prefs.ttsRate(app))
                    withContext(Dispatchers.IO) { File(app.cacheDir, "tts.mp3").apply { writeBytes(bytes) } }
                } else {
                    val region = AzureSpeech.cleanRegion(Prefs.azureSpeechRegion(app))!!
                    synthesize(app, Prefs.azureSpeechKey(app), region, AzureTts.ssml(text, Prefs.ttsVoice(app), Prefs.ttsRate(app)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e        // 用户点了停止：不回退
            } catch (e: IllegalStateException) {
                fallback(app, key, text, e.message ?: "云端出错", onError); return@launch
            } catch (e: TtsError) {
                fallback(app, key, text, e.message ?: "云端出错", onError); return@launch
            } catch (e: Exception) {
                fallback(app, key, text, "连不上服务器", onError); return@launch
            }
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { if (player === it) { it.release(); player = null; _playing.value = null } }
                mp.prepare()
                player = mp
                mp.start()
            } catch (e: Exception) {
                fallback(app, key, text, "云端声音播放失败", onError)
            }
        }
    }

    private class TtsError(msg: String) : Exception(msg)

    private suspend fun synthesize(ctx: Context, key: String, region: String, ssml: String): File = withContext(Dispatchers.IO) {
        val c = URL(AzureTts.url(region)).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.doOutput = true
        c.setRequestProperty("Ocp-Apim-Subscription-Key", key)
        c.setRequestProperty("Content-Type", "application/ssml+xml")
        c.setRequestProperty("X-Microsoft-OutputFormat", AzureTts.OUTPUT_FORMAT)
        c.setRequestProperty("User-Agent", "rijii-android")
        c.outputStream.use { it.write(ssml.toByteArray(Charsets.UTF_8)) }
        val status = c.responseCode
        if (status !in 200..299) throw TtsError(AzureTts.errorText(status))
        val f = File(ctx.cacheDir, "tts.mp3")
        c.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
        f
    }
}
