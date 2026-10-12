package app.birthdayreminder.cloud

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.birthdayreminder.domain.BaiduPanApi
import app.birthdayreminder.domain.CloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 百度网盘登录（设备码方式）：日迹先向百度要一个用户码，在浏览器里打开授权页确认，
 * 日迹在后面每隔几秒问一次百度“授权了没有”，授权了就拿到令牌。日迹不接触百度密码。
 */
object BaiduAuth {
    /** 登录状态变了就 +1，界面刷新用 */
    val version = mutableIntStateOf(0)
    private val lock = Mutex()

    private fun get(url: String): Pair<Int, String> =
        Http.client.newCall(Request.Builder().url(url).header("User-Agent", BaiduPanApi.UA).get().build()).execute().use { it.code to (it.body?.string() ?: "") }

    /** 第一步：要用户码 */
    suspend fun requestDevice(ctx: Context): BaiduPanApi.Device = withContext(Dispatchers.IO) {
        val key = CloudConfig.bdKey(ctx)
        if (key.isBlank() || CloudConfig.bdSecret(ctx).isBlank()) throw CloudException("请先填写 AppKey 和 SecretKey 并保存")
        val (code, text) = get(BaiduPanApi.deviceCodeUrl(key))
        BaiduPanApi.parseDevice(text) ?: throw CloudException("拿不到授权码（$code）" +
            ((BaiduPanApi.field(text, "error_description") as? String)?.let { "：$it" } ?: ""))
    }

    /** 第二步：等用户在浏览器里确认（最多等到授权码过期）。成功返回 null，否则返回原因 */
    suspend fun waitForGrant(ctx: Context, d: BaiduPanApi.Device): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val deadline = System.currentTimeMillis() + d.expiresInSec * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(d.intervalSec * 1000)
            val text = try { get(BaiduPanApi.deviceTokenUrl(CloudConfig.bdKey(app), CloudConfig.bdSecret(app), d.deviceCode)).second } catch (e: java.io.IOException) { continue }
            when (val r = BaiduPanApi.parseTokenResult(text, System.currentTimeMillis())) {
                is BaiduPanApi.TokenResult.Pending -> continue
                is BaiduPanApi.TokenResult.Fail -> return@withContext r.message
                is BaiduPanApi.TokenResult.Ok -> {
                    CloudConfig.saveBdTokens(app, r.token.access, r.token.refresh, r.token.expiresAtMs)
                    try { BaiduPanApi.parseAccount(get(BaiduPanApi.uinfoUrl(r.token.access)).second)?.let { CloudConfig.setBdAccount(app, it) } } catch (e: Exception) { }
                    version.intValue++
                    return@withContext null
                }
            }
        }
        "等太久了，授权码已过期，请重新登录"
    }

    /** 拿可用的令牌：快过期了就用刷新令牌换新的（百度的令牌 30 天，刷新令牌 10 年） */
    suspend fun accessToken(ctx: Context): String = lock.withLock {
        val app = ctx.applicationContext
        val cur = CloudConfig.bdAccess(app)
        if (cur != null && System.currentTimeMillis() < CloudConfig.bdExpires(app)) return@withLock cur
        val refresh = CloudConfig.bdRefresh(app) ?: throw CloudException("百度网盘还没有登录")
        val text = withContext(Dispatchers.IO) { get(BaiduPanApi.refreshUrl(CloudConfig.bdKey(app), CloudConfig.bdSecret(app), refresh)).second }
        when (val r = BaiduPanApi.parseTokenResult(text, System.currentTimeMillis())) {
            is BaiduPanApi.TokenResult.Ok -> { CloudConfig.saveBdTokens(app, r.token.access, r.token.refresh, r.token.expiresAtMs); r.token.access }
            is BaiduPanApi.TokenResult.Fail -> throw CloudException("百度网盘登录已失效，请重新登录（${r.message}）")
            else -> throw CloudException("百度网盘登录已失效，请重新登录")
        }
    }

    fun signOut(ctx: Context) { CloudConfig.clearBaidu(ctx); version.intValue++ }
    fun signedIn(ctx: Context) = CloudConfig.bdRefresh(ctx) != null
}

/** 百度网盘：备份放在应用文件夹（/apps/应用名）里 */
class BaiduPanStorage(private val ctx: Context) : CloudStorage {
    private val folder get() = CloudConfig.bdFolder(ctx)

    private fun call(b: Request.Builder, what: String): String =
        Http.client.newCall(b.header("User-Agent", BaiduPanApi.UA).build()).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw CloudException("${what}失败（${r.code}）" + (BaiduPanApi.errno(text)?.let { "：" + BaiduPanApi.errnoText(it) } ?: ""))
            text
        }

    /** 检查返回的 errno：不是 0 就报错 */
    private fun check(text: String, what: String): String {
        val e = BaiduPanApi.errno(text) ?: 0
        if (e != 0) throw CloudException("${what}失败：" + BaiduPanApi.errnoText(e))
        return text
    }

    private fun form(vararg kv: Pair<String, String>) = FormBody.Builder().apply { kv.forEach { (k, v) -> add(k, v) } }.build()

    /** 每 4MB 一片，算每片的 md5（百度用它校验） */
    private fun blockMd5s(file: File): List<String> {
        val out = ArrayList<String>()
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            var md = MessageDigest.getInstance("MD5"); var inBlock = 0L
            while (true) {
                val want = minOf(buf.size.toLong(), BaiduPanApi.CHUNK - inBlock).toInt()
                val n = input.read(buf, 0, want)
                if (n < 0) break
                md.update(buf, 0, n); inBlock += n
                if (inBlock == BaiduPanApi.CHUNK) { out += hex(md.digest()); md = MessageDigest.getInstance("MD5"); inBlock = 0 }
            }
            if (inBlock > 0 || out.isEmpty()) out += hex(md.digest())
        }
        return out
    }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    override suspend fun upload(file: File, name: String): Unit = withContext(Dispatchers.IO) {
        val token = BaiduAuth.accessToken(ctx)
        val path = BaiduPanApi.path(folder, name)
        val size = file.length().toString()
        val blocks = BaiduPanApi.blockListJson(blockMd5s(file))
        // 1. 预上传：拿到 uploadid（rtype=3：同名就覆盖）
        val pre = check(call(Request.Builder().url(BaiduPanApi.fileUrl(token, "precreate"))
            .post(form("path" to path, "size" to size, "isdir" to "0", "autoinit" to "1", "rtype" to "3", "block_list" to blocks)), "开始上传"), "开始上传")
        val uploadId = BaiduPanApi.field(pre, "uploadid") as? String ?: throw CloudException("开始上传失败：没有拿到 uploadid")
        // 2. 一片一片传（网络断了只重传那一片）
        RandomAccessFile(file, "r").use { raf ->
            val total = file.length()
            var seq = 0
            var pos = 0L
            while (pos < total || (total == 0L && seq == 0)) {
                val len = minOf(BaiduPanApi.CHUNK, total - pos).toInt()
                val buf = ByteArray(len); raf.seek(pos); raf.readFully(buf)
                var tries = 0
                while (true) {
                    val ok = try {
                        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", "part", buf.toRequestBody(null)).build()
                        check(call(Request.Builder().url(BaiduPanApi.uploadPartUrl(token, path, uploadId, seq)).post(body), "上传"), "上传"); true
                    } catch (e: java.io.IOException) { false }
                    if (ok) break
                    if (++tries >= 3) throw CloudException("上传中断：网络不稳定，稍后会自动重试")
                    delay(2000L * tries)
                }
                pos += len; seq++
                if (total == 0L) break
            }
        }
        // 3. 合并成文件
        check(call(Request.Builder().url(BaiduPanApi.fileUrl(token, "create"))
            .post(form("path" to path, "size" to size, "isdir" to "0", "rtype" to "3", "uploadid" to uploadId, "block_list" to blocks)), "完成上传"), "完成上传")
    }

    private suspend fun entries(): List<BaiduPanApi.Entry> = withContext(Dispatchers.IO) {
        val text = call(Request.Builder().url(BaiduPanApi.listUrl(BaiduAuth.accessToken(ctx), folder)).get(), "读取文件列表")
        if (BaiduPanApi.errno(text) == -9) return@withContext emptyList()      // 文件夹还没有（第一次上传前）
        BaiduPanApi.parseList(check(text, "读取文件列表"))
    }

    override suspend fun list(): List<CloudFile> = entries().map { it.file }

    override suspend fun download(name: String, dest: File): Unit = withContext(Dispatchers.IO) {
        val token = BaiduAuth.accessToken(ctx)
        val e = entries().firstOrNull { it.file.name == name } ?: throw CloudException("下载失败：云盘上找不到这个文件")
        val dlink = BaiduPanApi.parseDlink(check(call(Request.Builder().url(BaiduPanApi.metaUrl(token, e.fsId)).get(), "下载"), "下载"))
            ?: throw CloudException("下载失败：没有拿到下载地址")
        Http.client.newCall(Request.Builder().url(dlink + "&access_token=" + token).header("User-Agent", BaiduPanApi.UA).get().build()).execute().use { r ->
            if (!r.isSuccessful) throw CloudException("下载失败（${r.code}）")
            dest.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
    }

    override suspend fun delete(name: String): Unit = withContext(Dispatchers.IO) {
        val path = BaiduPanApi.path(folder, name)
        call(Request.Builder().url(BaiduPanApi.fileUrl(BaiduAuth.accessToken(ctx), "filemanager", "&opera=delete"))
            .post(form("async" to "0", "filelist" to "[\"" + path.replace("\"", "\\\"") + "\"]")), "删除")
    }

    override suspend fun test(): String? = try { list(); null } catch (e: Exception) { e.message ?: "连接失败" }
}
