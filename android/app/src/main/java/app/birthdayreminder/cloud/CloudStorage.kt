package app.birthdayreminder.cloud

import android.content.Context
import app.birthdayreminder.domain.CloudFile
import app.birthdayreminder.domain.OneDriveApi
import app.birthdayreminder.domain.WebDavApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** 云盘：上传、列出、下载、删除、测试连接。OneDrive 和 WebDAV 各一个实现，以后加别的云盘只要再写一个 */
interface CloudStorage {
    suspend fun upload(file: File, name: String)
    suspend fun list(): List<CloudFile>
    suspend fun download(name: String, dest: File)
    suspend fun delete(name: String)
    /** 能连上返回 null，否则返回原因 */
    suspend fun test(): String?
}

class CloudException(msg: String) : Exception(msg)

internal object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    }
    val ZIP = "application/zip".toMediaType()
    val XML = "application/xml; charset=utf-8".toMediaType()

    fun Response.failMessage(what: String): String {
        val b = try { body?.string()?.take(500) } catch (e: Exception) { null }
        val detail = b?.let { OneDriveApi.parseError(it) }
        return "${what}失败（${code}）" + (detail?.let { "：$it" } ?: when (code) {
            401 -> "：账号或密码不对，或者登录已失效"; 403 -> "：没有权限"; 404 -> "：地址不存在"; 507 -> "：云盘空间不够"; else -> "" })
    }
}

// ---------------------------------------------------------------- WebDAV

class WebDavStorage(private val base: String, private val folder: String, private val user: String, private val pass: String) : CloudStorage {
    private fun req(url: String) = Request.Builder().url(url).header("Authorization", Credentials.basic(user, pass, Charsets.UTF_8))

    /** 逐级建文件夹（已经有了会返回 405，忽略） */
    private fun ensureFolder() {
        val segs = folder.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        for (i in segs.indices) {
            val url = WebDavApi.url(base, segs.take(i + 1).joinToString("/"))
            Http.client.newCall(req(url).method("MKCOL", null).build()).execute().use { r ->
                if (!r.isSuccessful && r.code != 405 && r.code != 301 && r.code != 409) {
                    with(Http) { throw CloudException(r.failMessage("建文件夹")) }
                }
            }
        }
    }

    override suspend fun upload(file: File, name: String): Unit = withContext(Dispatchers.IO) {
        ensureFolder()
        Http.client.newCall(req(WebDavApi.url(base, folder, name)).put(file.asRequestBody(Http.ZIP)).build()).execute().use { r ->
            if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("上传")) }
        }
    }

    override suspend fun list(): List<CloudFile> = withContext(Dispatchers.IO) {
        val r = Http.client.newCall(req(WebDavApi.url(base, folder)).header("Depth", "1")
            .method("PROPFIND", WebDavApi.PROPFIND_BODY.toRequestBody(Http.XML)).build()).execute()
        r.use {
            if (r.code == 404) return@withContext emptyList()
            if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("读取文件列表")) }
            WebDavApi.parsePropfind(r.body?.string() ?: "")
        }
    }

    override suspend fun download(name: String, dest: File): Unit = withContext(Dispatchers.IO) {
        Http.client.newCall(req(WebDavApi.url(base, folder, name)).get().build()).execute().use { r ->
            if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("下载")) }
            dest.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
    }

    override suspend fun delete(name: String): Unit = withContext(Dispatchers.IO) {
        Http.client.newCall(req(WebDavApi.url(base, folder, name)).delete().build()).execute().close()
    }

    override suspend fun test(): String? = try {
        withContext(Dispatchers.IO) { ensureFolder() }; list(); null
    } catch (e: Exception) { e.message ?: "连接失败" }
}

// ---------------------------------------------------------------- OneDrive

class OneDriveStorage(private val ctx: Context) : CloudStorage {
    private suspend fun auth(b: Request.Builder): Request.Builder = b.header("Authorization", "Bearer " + OneDriveAuth.accessToken(ctx))

    override suspend fun upload(file: File, name: String): Unit = withContext(Dispatchers.IO) {
        val total = file.length()
        if (total <= OneDriveApi.SMALL_LIMIT) {
            Http.client.newCall(auth(Request.Builder().url(OneDriveApi.itemUrl(name, "/content"))).put(file.asRequestBody(Http.ZIP)).build()).execute().use { r ->
                if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("上传")) }
            }
            return@withContext
        }
        // 大文件：先建上传会话，再一段一段传（网络断了只需要重传那一段）
        val body = "{\"item\":{\"@microsoft.graph.conflictBehavior\":\"replace\"}}".toRequestBody("application/json".toMediaType())
        val uploadUrl = Http.client.newCall(auth(Request.Builder().url(OneDriveApi.itemUrl(name, "/createUploadSession"))).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("开始上传")) }
            OneDriveApi.parseUploadUrl(r.body?.string() ?: "") ?: throw CloudException("开始上传失败：没有拿到上传地址")
        }
        RandomAccessFile(file, "r").use { raf ->
            for (range in OneDriveApi.chunks(total)) {
                val len = (range.last - range.first + 1).toInt()
                val buf = ByteArray(len); raf.seek(range.first); raf.readFully(buf)
                var tries = 0
                while (true) {
                    val ok = try {
                        Http.client.newCall(Request.Builder().url(uploadUrl).header("Content-Range", OneDriveApi.contentRange(range, total))
                            .put(buf.toRequestBody(null)).build()).execute().use { r ->
                            if (r.isSuccessful) true else if (r.code in 500..599) false else with(Http) { throw CloudException(r.failMessage("上传")) }
                        }
                    } catch (e: java.io.IOException) { false }
                    if (ok) break
                    if (++tries >= 3) throw CloudException("上传中断：网络不稳定，稍后会自动重试")
                    kotlinx.coroutines.delay(2000L * tries)
                }
            }
        }
    }

    override suspend fun list(): List<CloudFile> = withContext(Dispatchers.IO) {
        Http.client.newCall(auth(Request.Builder().url(OneDriveApi.LIST_URL)).get().build()).execute().use { r ->
            if (r.code == 404) return@withContext emptyList()
            if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("读取文件列表")) }
            OneDriveApi.parseChildren(r.body?.string() ?: "")
        }
    }

    override suspend fun download(name: String, dest: File): Unit = withContext(Dispatchers.IO) {
        Http.client.newCall(auth(Request.Builder().url(OneDriveApi.itemUrl(name, "/content"))).get().build()).execute().use { r ->
            if (!r.isSuccessful) with(Http) { throw CloudException(r.failMessage("下载")) }
            dest.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
    }

    override suspend fun delete(name: String): Unit = withContext(Dispatchers.IO) {
        Http.client.newCall(auth(Request.Builder().url(OneDriveApi.itemUrl(name))).delete().build()).execute().close()
    }

    override suspend fun test(): String? = try { list(); null } catch (e: Exception) { e.message ?: "连接失败" }
}
