package app.birthdayreminder.domain

/**
 * 重排提醒时“哪些照常排、哪些要补发”的规则（纯逻辑，生日/提醒、生理期、用药共用）。
 *
 * 以前重排时一律只排“还没到点”的：一条提醒被省电模式推迟了几分钟，这期间正好重排一次
 * （同一时刻的另一条提醒响了、打开了 App……），它的定时就被取消，又因为“已经过点”不再排，这一条就丢了。
 * 现在每次排好都记下“答应过要弹”的 key，弹出来了再划掉；重排时已经过点、却还在名单上的，马上补发。
 */
object DeliveryPlan {
    /** 错过的提醒最多补发多久以前的：再早的补了也没意义 */
    const val GRACE_MS = 6 * 3600_000L
    /** 补发的几条之间隔开几秒，免得挤在同一瞬间被系统限流 */
    const val CATCH_UP_GAP_MS = 3_000L

    /** fireAt：排给系统的时间；due：提醒原定的时间（补发的那条 fireAt 是“现在”，due 还是原来的点） */
    data class Slot(val key: String, val fireAt: Long, val due: Long = fireAt)

    /**
     * entries：重新算出来的（key, 原定时间），要包含最近 GRACE_MS 内已经过点的。
     * promised：上次排好、还没弹出来的 key。
     * 还没到点的照常排；已经过点的，只有答应过、又没弹出来的才补发（新加的、早就弹过的不补）。
     */
    fun plan(entries: List<Pair<String, Long>>, promised: Set<String>, now: Long): List<Slot> {
        val out = ArrayList<Slot>()
        var late = 0
        for ((key, at) in entries.sortedBy { it.second }) {
            if (out.any { it.key == key }) continue
            when {
                at > now -> out += Slot(key, at)
                at > now - GRACE_MS && key in promised -> out += Slot(key, now + CATCH_UP_GAP_MS * ++late, at)
            }
        }
        return out.sortedBy { it.fireAt }
    }

    /** 通知编号按 key 固定下来：用排序序号的话，重排后后一条会顶掉还没处理的前一条通知 */
    fun notifId(key: String, base: Int, span: Int): Int = base + Math.floorMod(key.hashCode(), span)
}
