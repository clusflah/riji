package app.birthdayreminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPlanTest {
    private val now = 1_000_000_000L

    @Test fun futureItemsKeepTheirTime() {
        val s = DeliveryPlan.plan(listOf("a" to now + 60_000, "b" to now + 1000), emptySet(), now)
        assertEquals(listOf("b" to now + 1000, "a" to now + 60_000), s.map { it.key to it.fireAt })
    }

    @Test fun missedPromisedItemIsCaughtUp() {
        // 被推迟、又在重排时被取消的那条：答应过、没弹出来，马上补发
        val s = DeliveryPlan.plan(listOf("late" to now - 5 * 60_000, "next" to now + 3600_000), setOf("late", "next"), now)
        assertEquals("late", s.first().key)
        assertTrue(s.first().fireAt in (now + 1)..(now + 10_000))
    }

    @Test fun pastItemNotPromisedIsDropped() {
        // 刚新建的、过点的提醒，或者已经弹过（从名单上划掉）的，不补
        val s = DeliveryPlan.plan(listOf("new" to now - 60_000), emptySet(), now)
        assertTrue(s.isEmpty())
    }

    @Test fun tooOldIsDropped() {
        val s = DeliveryPlan.plan(listOf("old" to now - DeliveryPlan.GRACE_MS - 1), setOf("old"), now)
        assertTrue(s.isEmpty())
    }

    @Test fun catchUpsAreSpacedOut() {
        val s = DeliveryPlan.plan(listOf("x" to now - 1000, "y" to now - 2000), setOf("x", "y"), now)
        assertEquals(2, s.map { it.fireAt }.toSet().size)
    }

    @Test fun notifIdStaysInRange() {
        for (k in listOf("", "a", "r:123|2026-10-17|1700000000000")) {
            assertTrue(DeliveryPlan.notifId(k, 40000, 10000) in 40000 until 50000)
        }
    }
}
