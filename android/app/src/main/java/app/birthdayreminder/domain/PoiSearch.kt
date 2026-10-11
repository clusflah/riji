package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson

/** 搜索到的一个地点。坐标是高德坐标系(GCJ-02)，和地图上点选得到的坐标一致。 */
data class PoiItem(val name: String, val address: String, val lat: Double, val lng: Double) {
    /** 选中后写进日记的地址文字：名称，详细地址 */
    val label: String get() = if (address.isBlank()) name else "$name，$address"
}

sealed class SearchOutcome {
    data class Ok(val items: List<PoiItem>) : SearchOutcome()
    data class Fail(val message: String) : SearchOutcome()
}

/** 地图页面里的搜索脚本回传给原生的 JSON：{"ok":true,"items":[{name,address,lat,lng}]} 或 {"ok":false,"msg":"…"} */
object PoiSearch {
    @Suppress("UNCHECKED_CAST")
    fun parse(json: String): SearchOutcome {
        val root = try { MiniJson.parse(json) as? Map<String, Any?> } catch (e: Exception) { null }
            ?: return SearchOutcome.Fail("搜索结果格式不对，请重试")
        if (root["ok"] != true) return SearchOutcome.Fail((root["msg"] as? String)?.ifBlank { null } ?: "搜索失败，请重试")
        val items = (root["items"] as? List<Any?>).orEmpty().mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            val name = (m["name"] as? String)?.trim().orEmpty()
            val lat = (m["lat"] as? Number)?.toDouble()
            val lng = (m["lng"] as? Number)?.toDouble()
            if (name.isEmpty() || lat == null || lng == null) return@mapNotNull null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
            PoiItem(name, (m["address"] as? String)?.trim().orEmpty(), lat, lng)
        }
        return SearchOutcome.Ok(items)
    }
}
