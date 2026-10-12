package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/** 云盘上的一个备份文件 */
data class CloudFile(val name: String, val size: Long, val modifiedMs: Long)

/**
 * 云盘备份的规则（纯逻辑，可测试）：文件命名、保留最新几份、频率。
 */
object CloudPlan {
    const val PREFIX = "li-cloud-backup-"
    val KEEP_OPTIONS = listOf(3, 7, 15)

    fun fileName(nowMs: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(nowMs).atZone(zone)
        return PREFIX + "%04d%02d%02d-%02d%02d%02d.zip".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second)
    }

    /** 云盘上要删掉的旧备份：只管日迹自己上传的（名字以 li-cloud-backup- 开头），保留最新 keep 份 */
    fun toDelete(files: List<CloudFile>, keep: Int): List<String> =
        files.map { it.name }.filter { it.startsWith(PREFIX) && it.endsWith(".zip") }.sortedDescending().drop(keep.coerceAtLeast(1))

    /** 列表里显示的备份：日迹上传的和手动备份的 zip，新的在前 */
    fun restorable(files: List<CloudFile>): List<CloudFile> =
        files.filter { it.name.endsWith(".zip") && (it.name.startsWith(PREFIX) || it.name.startsWith("li-backup-") || it.name.startsWith("li-auto-backup-")) }
            .sortedByDescending { it.name.substringAfterLast("-backup-") }
}

/** OAuth 的 PKCE（手机 App 这种“公开客户端”用的安全登录方式，不需要密钥） */
object Pkce {
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    fun verifier(random: ByteArray): String = b64.encodeToString(random)
    fun challenge(verifier: String): String = b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

/**
 * OneDrive（Microsoft Graph）：只申请“应用文件夹”的权限，备份放在 OneDrive 的“应用/日迹”里，碰不到别的文件。
 */
object OneDriveApi {
    const val AUTH_BASE = "https://login.microsoftonline.com/common/oauth2/v2.0"
    const val GRAPH = "https://graph.microsoft.com/v1.0"
    const val SCOPES = "offline_access Files.ReadWrite.AppFolder User.Read"
    const val REDIRECT = "rijii://onedrive-auth"
    /** 小于这个大小直接上传；更大的分段上传 */
    const val SMALL_LIMIT = 4L * 1024 * 1024
    /** 分段上传每段的大小：微软要求是 320 KiB 的整数倍 */
    const val CHUNK = 10L * 320 * 1024

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun authorizeUrl(clientId: String, challenge: String, state: String): String =
        "$AUTH_BASE/authorize?client_id=${enc(clientId)}&response_type=code&redirect_uri=${enc(REDIRECT)}" +
            "&response_mode=query&scope=${enc(SCOPES)}&code_challenge=${enc(challenge)}&code_challenge_method=S256&state=${enc(state)}&prompt=select_account"

    fun codeForm(clientId: String, code: String, verifier: String) = mapOf(
        "client_id" to clientId, "grant_type" to "authorization_code", "code" to code,
        "redirect_uri" to REDIRECT, "code_verifier" to verifier, "scope" to SCOPES)

    fun refreshForm(clientId: String, refresh: String) = mapOf(
        "client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to refresh, "scope" to SCOPES)

    data class Token(val access: String, val refresh: String?, val expiresAtMs: Long)

    /** 解析登录返回：提前 2 分钟当作过期，免得用到一半失效 */
    fun parseToken(json: String, nowMs: Long): Token? {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return null
        val access = m["access_token"] as? String ?: return null
        val sec = (m["expires_in"] as? Number)?.toLong() ?: 3600L
        return Token(access, m["refresh_token"] as? String, nowMs + (sec - 120).coerceAtLeast(60) * 1000)
    }

    /** 登录失败时微软返回的错误说明 */
    fun parseError(json: String): String? = (MiniJson.parse(json) as? Map<*, *>)?.let { m ->
        (m["error_description"] as? String)?.substringBefore("\r")?.substringBefore("\n")
            ?: ((m["error"] as? Map<*, *>)?.get("message") as? String) ?: (m["error"] as? String)
    }

    /** 应用文件夹里某个文件的地址：…/approot:/文件名:/content 等 */
    fun itemUrl(name: String, suffix: String = ""): String = "$GRAPH/me/drive/special/approot:/${enc(name)}:$suffix"
    const val LIST_URL = "$GRAPH/me/drive/special/approot/children?\$select=name,size,lastModifiedDateTime,file&\$top=200"
    const val ME_URL = "$GRAPH/me?\$select=displayName,userPrincipalName"

    fun parseChildren(json: String): List<CloudFile> {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return emptyList()
        return (m["value"] as? List<*>).orEmpty().mapNotNull { x ->
            val it = x as? Map<*, *> ?: return@mapNotNull null
            if (it["file"] == null && it.containsKey("folder")) return@mapNotNull null
            val name = it["name"] as? String ?: return@mapNotNull null
            val t = (it["lastModifiedDateTime"] as? String)?.let { s -> try { Instant.parse(s).toEpochMilli() } catch (e: Exception) { 0L } } ?: 0L
            CloudFile(name, (it["size"] as? Number)?.toLong() ?: 0L, t)
        }
    }

    fun parseAccount(json: String): String? = (MiniJson.parse(json) as? Map<*, *>)?.let { (it["userPrincipalName"] as? String) ?: (it["displayName"] as? String) }

    /** 分段上传的每一段（含首含尾的字节范围） */
    fun chunks(total: Long, chunk: Long = CHUNK): List<LongRange> =
        if (total <= 0) emptyList() else (0 until total step chunk).map { s -> s..minOf(total, s + chunk) - 1 }

    fun contentRange(r: LongRange, total: Long) = "bytes ${r.first}-${r.last}/$total"

    fun parseUploadUrl(json: String): String? = (MiniJson.parse(json) as? Map<*, *>)?.get("uploadUrl") as? String
}

/**
 * 百度网盘开放平台（xpan）：用“设备码”方式登录（在浏览器里授权，日迹不接触密码），
 * 备份放在应用文件夹 /apps/应用名 里。上传分三步：预上传 → 分片上传（每片 4MB）→ 合并。
 */
object BaiduPanApi {
    const val OAUTH = "https://openapi.baidu.com/oauth/2.0"
    const val XPAN = "https://pan.baidu.com/rest/2.0/xpan"
    const val PCS_UPLOAD = "https://d.pcs.baidu.com/rest/2.0/pcs/superfile2"
    /** 百度要求的 User-Agent（下载等接口不带会被拒） */
    const val UA = "pan.baidu.com"
    /** 分片大小：普通用户 4MB */
    const val CHUNK = 4L * 1024 * 1024
    const val DEFAULT_FOLDER = "/apps/日迹"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun deviceCodeUrl(appKey: String) = "$OAUTH/device/code?response_type=device_code&client_id=${enc(appKey)}&scope=basic,netdisk"
    fun deviceTokenUrl(appKey: String, secret: String, deviceCode: String) =
        "$OAUTH/token?grant_type=device_token&code=${enc(deviceCode)}&client_id=${enc(appKey)}&client_secret=${enc(secret)}"
    fun refreshUrl(appKey: String, secret: String, refresh: String) =
        "$OAUTH/token?grant_type=refresh_token&refresh_token=${enc(refresh)}&client_id=${enc(appKey)}&client_secret=${enc(secret)}"

    data class Device(val deviceCode: String, val userCode: String, val verifyUrl: String, val expiresInSec: Long, val intervalSec: Long)

    fun parseDevice(json: String): Device? {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return null
        return Device(m["device_code"] as? String ?: return null, m["user_code"] as? String ?: return null,
            m["verification_url"] as? String ?: "https://openapi.baidu.com/device",
            (m["expires_in"] as? Number)?.toLong() ?: 300L, ((m["interval"] as? Number)?.toLong() ?: 5L).coerceAtLeast(2L))
    }

    /** 浏览器打开的授权页：带上用户码，打开就能直接确认 */
    fun verifyPage(d: Device) = "${d.verifyUrl}?code=${enc(d.userCode)}&display=mobile"

    /** 换令牌的返回：成功是令牌；还没授权是 Pending；其它是错误说明 */
    sealed class TokenResult {
        data class Ok(val token: OneDriveApi.Token) : TokenResult()
        object Pending : TokenResult()
        data class Fail(val message: String) : TokenResult()
    }

    fun parseTokenResult(json: String, nowMs: Long): TokenResult {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return TokenResult.Fail("返回内容看不懂")
        OneDriveApi.parseToken(json, nowMs)?.let { return TokenResult.Ok(it) }
        return when (m["error"] as? String) {
            "authorization_pending", "slow_down" -> TokenResult.Pending
            "expired_token" -> TokenResult.Fail("授权码过期了，请重新登录")
            "invalid_client" -> TokenResult.Fail("AppKey 或 SecretKey 不对")
            "authorization_declined", "access_denied" -> TokenResult.Fail("你取消了授权")
            else -> TokenResult.Fail((m["error_description"] as? String) ?: (m["error"] as? String) ?: "未知错误")
        }
    }

    fun path(folder: String, name: String) = folder.trimEnd('/') + "/" + name
    fun listUrl(token: String, folder: String) = "$XPAN/file?method=list&dir=${enc(folder)}&limit=1000&access_token=${enc(token)}"
    fun uinfoUrl(token: String) = "$XPAN/nas?method=uinfo&access_token=${enc(token)}"
    fun fileUrl(token: String, method: String, extra: String = "") = "$XPAN/file?method=$method$extra&access_token=${enc(token)}"
    fun uploadPartUrl(token: String, path: String, uploadId: String, partSeq: Int) =
        "$PCS_UPLOAD?method=upload&type=tmpfile&path=${enc(path)}&uploadid=${enc(uploadId)}&partseq=$partSeq&access_token=${enc(token)}"
    fun metaUrl(token: String, fsId: Long) = "$XPAN/multimedia?method=filemetas&dlink=1&fsids=${enc("[$fsId]")}&access_token=${enc(token)}"

    /** 分片的 md5 列表 → JSON 文本 ["md5", ...] */
    fun blockListJson(md5s: List<String>) = md5s.joinToString(",", "[", "]") { "\"$it\"" }

    /** 列表里的一项（带 fs_id，下载要用） */
    data class Entry(val file: CloudFile, val fsId: Long)

    fun parseList(json: String): List<Entry> {
        val m = MiniJson.parse(json) as? Map<*, *> ?: return emptyList()
        return (m["list"] as? List<*>).orEmpty().mapNotNull { x ->
            val it = x as? Map<*, *> ?: return@mapNotNull null
            if ((it["isdir"] as? Number)?.toInt() == 1) return@mapNotNull null
            val name = it["server_filename"] as? String ?: return@mapNotNull null
            Entry(CloudFile(name, (it["size"] as? Number)?.toLong() ?: 0L, ((it["server_mtime"] as? Number)?.toLong() ?: 0L) * 1000),
                (it["fs_id"] as? Number)?.toLong() ?: return@mapNotNull null)
        }
    }

    fun errno(json: String): Int? = ((MiniJson.parse(json) as? Map<*, *>)?.get("errno") as? Number)?.toInt()
    fun field(json: String, key: String): Any? = (MiniJson.parse(json) as? Map<*, *>)?.get(key)
    fun parseAccount(json: String): String? = (MiniJson.parse(json) as? Map<*, *>)?.let { (it["netdisk_name"] as? String)?.ifBlank { null } ?: (it["baidu_name"] as? String) }
    fun parseDlink(json: String): String? =
        (((MiniJson.parse(json) as? Map<*, *>)?.get("list") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("dlink") as? String

    /** 常见错误码的说明 */
    fun errnoText(code: Int): String = when (code) {
        -6, 111, 110 -> "登录已失效，请重新登录百度网盘"
        -7 -> "文件夹名不对或没有权限（应用文件夹应该是 /apps/你的应用名）"
        -9 -> "文件不存在"
        -10, 31112 -> "网盘空间不够"
        2 -> "参数错误"
        31034, 31023 -> "请求太频繁，稍后会自动重试"
        else -> "错误码 $code"
    }
}

/**
 * WebDAV（坚果云、Nextcloud、群晖等）：地址拼接、解析 PROPFIND 返回的文件列表。
 */
object WebDavApi {
    const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""

    private fun encSeg(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** 基础地址 + 文件夹（可以多级，比如 “备份/日迹”）+ 文件名；文件名为空时得到文件夹地址（以 / 结尾） */
    fun url(base: String, folder: String, name: String? = null): String {
        val b = base.trim().trimEnd('/')
        val segs = folder.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val dir = b + "/" + segs.joinToString("") { encSeg(it) + "/" }
        return if (name.isNullOrEmpty()) dir else dir + encSeg(name)
    }

    /** 解析 PROPFIND（Depth: 1）返回：只要文件，不要文件夹本身；兼容 d:/D:/没有前缀的写法 */
    fun parsePropfind(xml: String): List<CloudFile> {
        val out = ArrayList<CloudFile>()
        val resp = Regex("<(?:[A-Za-z0-9]+:)?response\\b[^>]*>(.*?)</(?:[A-Za-z0-9]+:)?response>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        fun tag(body: String, name: String): String? =
            Regex("<(?:[A-Za-z0-9]+:)?$name\\b[^>]*>(.*?)</(?:[A-Za-z0-9]+:)?$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(body)?.groupValues?.get(1)?.trim()
        for (m in resp.findAll(xml)) {
            val body = m.groupValues[1]
            val href = tag(body, "href") ?: continue
            if (Regex("<(?:[A-Za-z0-9]+:)?collection\\b", RegexOption.IGNORE_CASE).containsMatchIn(body) || href.endsWith("/")) continue
            val name = try { java.net.URLDecoder.decode(href.substringAfterLast('/').replace("+", "%2B"), "UTF-8") } catch (e: Exception) { href.substringAfterLast('/') }
            if (name.isEmpty()) continue
            val size = tag(body, "getcontentlength")?.toLongOrNull() ?: 0L
            val t = tag(body, "getlastmodified")?.let { s ->
                try { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
            } ?: 0L
            out += CloudFile(name, size, t)
        }
        return out
    }
}
