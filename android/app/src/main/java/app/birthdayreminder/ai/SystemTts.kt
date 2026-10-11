package app.birthdayreminder.ai

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 手机自带朗读（Android 系统 TextToSpeech）。
 * 第一次用时才初始化（要等系统朗读引擎准备好），准备好之前要念的内容先排队。
 * 念完 / 出错时调用 onFinish，让界面把 ⏹ 变回 🔊。
 */
object SystemTts {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pending: (() -> Unit)? = null
    var onFinish: ((String) -> Unit)? = null      // 参数是这次朗读的标识

    private fun ensure(ctx: Context) {
        if (tts != null) return
        failed = false
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { utteranceId?.let { onFinish?.invoke(it) } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { utteranceId?.let { onFinish?.invoke(it) } }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { utteranceId?.let { onFinish?.invoke(it) } }
                })
                pending?.invoke(); pending = null
            } else {
                failed = true
                pending = null
                tts = null
            }
        }
    }

    /**
     * 念一段文字（已经去掉 Markdown 符号的）。返回 null 表示开始念了（或排队等引擎准备），否则是念不了的原因。
     */
    fun speak(ctx: Context, key: String, text: String, rate: String): String? {
        if (failed) { failed = false; tts = null }
        ensure(ctx)
        val job = job@{
            val t = tts ?: run { onFinish?.invoke(key); return@job }
            val locale = if (SystemTtsLogic.isEnglish(text)) Locale.US else Locale.SIMPLIFIED_CHINESE
            val res = t.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (locale != Locale.US) t.setLanguage(Locale.CHINESE)     // 再试一次通用中文
            }
            t.setSpeechRate(SystemTtsLogic.rate(rate))
            t.speak(text.take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_FLUSH, null, key)
            Unit
        }
        if (ready) job() else pending = job
        return null
    }

    /** 手机上的朗读引擎有没有中文（没有的话提示去系统设置装语音包） */
    fun chineseAvailable(): Boolean? {
        val t = tts ?: return null
        if (!ready) return null
        val r = t.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun stop() { try { tts?.stop() } catch (e: Exception) { } }

    /** 打开系统的“文字转语音”设置（选引擎、装语音包） */
    fun openSettings(ctx: Context): Boolean = try {
        ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e2: Exception) { false }
    }
}
