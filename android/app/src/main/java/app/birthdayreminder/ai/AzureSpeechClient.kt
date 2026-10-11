package app.birthdayreminder.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 按住说话时用的录音器：16kHz 单声道 16 位，松手后交出全部录音 */
class AzureSpeechRecorder {
    @Volatile private var running = false
    private val data = ByteArrayOutputStream()
    var startedAt = 0L; private set

    /** 开始录音（在后台线程里一直读，直到 stop 或者到 55 秒上限）。调用前要确认已经有麦克风权限 */
    @SuppressLint("MissingPermission")
    suspend fun record() = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(AzureSpeech.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, AzureSpeech.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); throw IllegalStateException("麦克风打不开，可能被别的 App 占用了") }
        val buf = ByteArray(3200)
        val maxBytes = AzureSpeech.SAMPLE_RATE * 2 * AzureSpeech.MAX_SECONDS
        data.reset()
        running = true
        startedAt = System.currentTimeMillis()
        try {
            rec.startRecording()
            while (running && data.size() < maxBytes) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) data.write(buf, 0, n)
            }
        } finally {
            running = false
            try { rec.stop() } catch (e: Exception) { }
            rec.release()
        }
    }

    fun stop() { running = false }

    fun pcm(): ByteArray = data.toByteArray()
}

/** 联网部分：把录音发给 Azure 语音识别、检查 Key 是否可用 */
object AzureSpeechClient {
    suspend fun recognize(key: String, region: String, pcm: ByteArray): AzureSpeech.Result = withContext(Dispatchers.IO) {
        try {
            val c = URL(AzureSpeech.recognizeUrl(region)).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Ocp-Apim-Subscription-Key", key)
            c.setRequestProperty("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=${AzureSpeech.SAMPLE_RATE}")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(AzureSpeech.wav(pcm)) }
            val status = c.responseCode
            val body = (if (status in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
            AzureSpeech.parse(status, body)
        } catch (e: Exception) {
            AzureSpeech.Result.Fail("连不上 Azure 语音服务，请检查网络和区域")
        }
    }

    /** 检查 Key 和区域：能换到临时令牌就说明可用。返回 null 表示可用，否则是原因 */
    suspend fun checkKey(key: String, region: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(AzureSpeech.tokenUrl(region)).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("Ocp-Apim-Subscription-Key", key)
            c.setFixedLengthStreamingMode(0)
            c.outputStream.close()
            when (val s = c.responseCode) {
                in 200..299 -> null
                401, 403 -> "Key 和区域对不上：请确认 Key 是这个区域的语音资源的"
                else -> "检查失败（$s）"
            }
        } catch (e: Exception) {
            "连不上：请检查网络，以及区域名是否写对（比如 eastasia）"
        }
    }
}
