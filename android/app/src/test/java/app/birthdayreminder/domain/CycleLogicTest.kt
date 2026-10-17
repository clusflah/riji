package app.birthdayreminder.domain

import app.birthdayreminder.data.CycleDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class CycleLogicTest {
    private fun day(date: String, flow: Int) = CycleDay(date, flow = flow)
    private val today = LocalDate.parse("2026-10-16")
    private val d15 = LocalDate.parse("2026-10-15")

    private fun mark15(days: List<CycleDay>) =
        CycleLogic.marks(days, d15, d15, today, birthControl = true)[d15]

    // 11–14 号记了经期，15 号没记：16 号记成“少”还是“点滴”，15 号都不该跟着变
    private val base = (11..14).map { day("2026-10-$it", CycleLogic.PERIOD_ONLY) }

    @Test fun unrecordedGapDayIsNotMarkedAsPeriod() {
        assertNotEquals(CycleLogic.Mark.PERIOD, mark15(base + day("2026-10-16", 2)))
        assertNotEquals(CycleLogic.Mark.PERIOD, mark15(base + day("2026-10-16", 1)))
    }

    @Test fun gapDayStillCountsForPeriodLength() {
        // 算周期时漏记的一天仍然连着：11–16 号是一次经期，今天是第 6 天
        val ps = CycleLogic.periods(base + day("2026-10-16", 2))
        assertEquals(1, ps.size)
        assertEquals(6, ps[0].length)
    }

    @Test fun recordedDayIsMarkedAsPeriod() {
        assertEquals(CycleLogic.Mark.PERIOD, mark15(base + day("2026-10-15", 2)))
    }

    @Test fun autoFillStopsAtDayWithChosenFlow() {
        // 15 号来了，16 号已经改成点滴、17 号以后被取消了：把 15 号改掉再改回经期，16、17 号都不该被补回来
        val days = listOf(day("2026-10-16", 1))
        assertEquals(emptyList<LocalDate>(), CycleLogic.autoFillDates(days, d15, 5))
        // 后面都没记过：照常补到一共 5 天
        assertEquals(4, CycleLogic.autoFillDates(emptyList(), d15, 5).size)
    }
}
