package app.birthdayreminder.domain

import app.birthdayreminder.data.MoodLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MoodLogicTest {
    private fun m(date: String, level: Int) = MoodLog(date + level, date, 0L, level)
    private val logs = listOf(m("2026-10-05", 5), m("2026-10-05", 3), m("2026-10-06", 2), m("2026-09-01", 4))

    @Test fun dailySeriesAverages() {
        val s = MoodLogic.series(logs, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-12"))
        assertEquals(listOf(4.0, 2.0), s.map { it.avg })
        assertEquals(listOf(2, 1), s.map { it.count })
    }

    @Test fun longRangeGroupsByWeek() {
        // 10-05 是周一，10-06 也在同一周
        val s = MoodLogic.series(logs, LocalDate.parse("2026-04-13"), LocalDate.parse("2026-10-12"))
        assertEquals(listOf(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-10-05")), s.map { it.start })
    }

    @Test fun countsFromBestToWorst() {
        val c = MoodLogic.counts(logs, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-12"))
        assertEquals(listOf(1, 0, 1, 1, 0), c.map { it.second })
    }

    @Test fun weekdayAverage() {
        val w = MoodLogic.weekdayAvg(logs, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-12"))
        assertEquals(4.0, w[0]!!, 0.0)    // 周一
        assertEquals(2.0, w[1]!!, 0.0)    // 周二
        assertNull(w[2])
    }
}
