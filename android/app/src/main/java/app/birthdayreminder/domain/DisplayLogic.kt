package app.birthdayreminder.domain

/**
 * 高刷新率：从屏幕支持的显示模式里，挑和当前分辨率相同、刷新率最高的那个（至少 90Hz）。
 * 纯逻辑，方便测试；真正设置在 MainActivityCompose 里。
 */
object DisplayLogic {
    data class Mode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

    const val MIN_HIGH = 90f

    /** 返回要用的模式编号；屏幕最高不到 90Hz（或分辨率对不上）就返回 null，不动系统设置 */
    fun pickHighRefresh(modes: List<Mode>, current: Mode?, minRate: Float = MIN_HIGH): Int? {
        if (current == null) return null
        val same = modes.filter { it.width == current.width && it.height == current.height }
        val best = same.maxByOrNull { it.refreshRate } ?: return null
        return if (best.refreshRate + 0.5f >= minRate) best.id else null      // 89.9Hz 这类也算 90
    }
}
