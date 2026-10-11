package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import app.birthdayreminder.domain.FitnessFields.Field
import app.birthdayreminder.domain.FitnessFields.Kind

/**
 * 健身“项目设置”（纯逻辑）：调整顺序、显示 / 隐藏、添加和删除自定义项目、存到手机。
 * 内置项目只能隐藏不能删（避免误删后历史数据没有名字）；自定义项目可以改名、删除。
 */
object FitnessConfig {
    const val MAX_FIELDS = 30
    const val MAX_NAME = 8

    /** 添加自定义项目时可选的类型（体重只有内置的一个） */
    val CUSTOM_KINDS = listOf(
        Kind.COUNT to "次数（可记组数）",
        Kind.TIME to "时间（分钟）",
        Kind.DISTANCE to "距离（公里）",
        Kind.NUMBER to "其它数字",
        Kind.BODY to "身体数据（不累加，比如腕围、体温）"
    )

    fun defaultUnit(kind: Kind): String = when (kind) {
        Kind.WEIGHT -> "kg"; Kind.COUNT -> "次"; Kind.TIME -> "分钟"; Kind.DISTANCE -> "公里"; Kind.NUMBER -> ""; Kind.BODY -> "cm"
    }

    private val customKey = Regex("^c_[a-z0-9]{4,16}$")
    fun looksLikeCustomKey(k: String) = customKey.matches(k)

    /**
     * 存下来的设置 + 内置默认 -> 实际用的列表：
     * 按存的顺序；内置项目的名字和类型以内置定义为准（只保留用户的显示 / 隐藏）；
     * 存的设置里没有的内置项目（新版本加的）插在它在默认顺序里前一项的后面（找不到就放最前），默认显示。
     */
    fun merge(stored: List<Field>): List<Field> {
        val defaults = FitnessFields.DEFAULTS.associateBy { it.key }
        val out = ArrayList<Field>()
        val seen = HashSet<String>()
        for (f in stored) {
            if (!seen.add(f.key)) continue
            val d = defaults[f.key]
            when {
                d != null -> out.add(d.copy(visible = f.visible))
                looksLikeCustomKey(f.key) && f.kind != Kind.WEIGHT && f.label.isNotBlank() ->
                    out.add(f.copy(custom = true, label = f.label.trim().take(MAX_NAME), unit = f.unit.trim().take(6)))
            }
        }
        // 新加的内置项目：插到默认顺序里前一个已有项目的后面，比如新的围度跟在臀围后面
        FitnessFields.DEFAULTS.forEachIndexed { i, d ->
            if (d.key in seen) return@forEachIndexed
            val prev = FitnessFields.DEFAULTS.subList(0, i).lastOrNull { p -> out.any { it.key == p.key } }
            val at = if (prev == null) 0 else out.indexOfFirst { it.key == prev.key } + 1
            out.add(at, d); seen.add(d.key)
        }
        return out.take(MAX_FIELDS + FitnessFields.DEFAULTS.size)
    }

    fun move(list: List<Field>, index: Int, delta: Int): List<Field> {
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return list
        val m = list.toMutableList()
        val x = m.removeAt(index); m.add(to, x)
        return m
    }

    fun setVisible(list: List<Field>, key: String, visible: Boolean): List<Field> =
        list.map { if (it.key == key) it.copy(visible = visible) else it }

    sealed class Result {
        data class Ok(val list: List<Field>) : Result()
        data class Bad(val message: String) : Result()
    }

    /** 添加自定义项目；newKey 由调用方生成（方便测试），形如 c_ab12cd34 */
    fun addCustom(list: List<Field>, name: String, kind: Kind, unit: String, newKey: String): Result {
        val n = name.trim()
        if (n.isEmpty()) return Result.Bad("请填写项目名字")
        if (n.length > MAX_NAME) return Result.Bad("名字最多 $MAX_NAME 个字")
        if (kind == Kind.WEIGHT) return Result.Bad("体重只有一个，不能再加")
        if (list.any { it.label == n }) return Result.Bad("已经有叫“$n”的项目了")
        if (list.count { it.custom } >= MAX_FIELDS) return Result.Bad("自定义项目最多 $MAX_FIELDS 个")
        if (!looksLikeCustomKey(newKey) || list.any { it.key == newKey }) return Result.Bad("请再试一次")
        val u = unit.trim().take(6).ifEmpty { defaultUnit(kind) }
        return Result.Ok(list + Field(newKey, n, u, kind, custom = true, visible = true))
    }

    /** 删除自定义项目（内置的删不了，只能隐藏） */
    fun removeCustom(list: List<Field>, key: String): List<Field> = list.filter { !(it.key == key && it.custom) }

    fun renameCustom(list: List<Field>, key: String, name: String): Result {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME) return Result.Bad("名字要 1 到 $MAX_NAME 个字")
        if (list.any { it.label == n && it.key != key }) return Result.Bad("已经有叫“$n”的项目了")
        return Result.Ok(list.map { if (it.key == key && it.custom) it.copy(label = n) else it })
    }

    // ---------------- 存到手机 / 备份 ----------------
    fun toMaps(list: List<Field>): List<Map<String, Any?>> = list.map {
        mapOf("key" to it.key, "label" to it.label, "unit" to it.unit, "kind" to it.kind.name.lowercase(), "visible" to it.visible)
    }

    fun toJson(list: List<Field>): String = MiniJson.write(toMaps(list))

    @Suppress("UNCHECKED_CAST")
    fun fromMaps(items: List<Any?>?): List<Field> = items.orEmpty().mapNotNull { x ->
        val m = x as? Map<String, Any?> ?: return@mapNotNull null
        val key = m["key"] as? String ?: return@mapNotNull null
        val kind = Kind.values().firstOrNull { it.name.equals(m["kind"] as? String, true) } ?: Kind.NUMBER
        Field(key, (m["label"] as? String).orEmpty(), (m["unit"] as? String).orEmpty(), kind,
            custom = looksLikeCustomKey(key), visible = m["visible"] != false)
    }

    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: String?): List<Field> = try { merge(fromMaps(MiniJson.parse(json ?: "[]") as? List<Any?>)) } catch (e: Exception) { FitnessFields.DEFAULTS }
}
