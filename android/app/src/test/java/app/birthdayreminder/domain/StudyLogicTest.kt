package app.birthdayreminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLogicTest {
    @Test fun parsesStudyLine() {
        val items = StudyLogic.parseDiaryText("逻辑填空 20 图推 20 逻辑判断 10 片段阅读 10 削弱 20 加强 10")
        assertEquals(listOf("逻辑填空", "图推", "逻辑判断", "片段阅读", "削弱", "加强"), items.map { it.name })
        assertEquals(listOf(20.0, 20.0, 10.0, 10.0, 20.0, 10.0), items.map { it.amount })
    }

    @Test fun parsesUnits() {
        val items = StudyLogic.parseDiaryText("单词 50个 阅读 3篇 数学 12道")
        assertEquals(listOf("个", "篇", "题"), items.map { it.unit })
    }

    @Test fun ignoresOrdinaryDiary() {
        assertTrue(StudyLogic.parseDiaryText("今天和朋友去公园散步，天气很好，回来吃了 2 个苹果").isEmpty())
    }

    @Test fun itemsRoundTrip() {
        val list = listOf(StudyItem("逻辑填空", 20.0, "题"), StudyItem("背书", null, ""))
        assertEquals(list, StudyLogic.itemsFromJson(StudyLogic.itemsToJson(list)))
        assertEquals("逻辑填空 20题 · 背书", StudyLogic.summary(list))
    }

    @Test fun timeUnitsAddUp() {
        val items = listOf(StudyItem("听力", 30.0, "分钟"), StudyItem("阅读", 1.5, "小时"), StudyItem("逻辑填空", 20.0, "题"))
        assertEquals(120, StudyLogic.itemMinutes(items))
        assertEquals(listOf("分钟", "小时"), StudyLogic.parseDiaryText("听力 30分钟 阅读 2小时").map { it.unit })
    }

    @Test fun formatsMinutes() {
        assertEquals("30分钟", StudyLogic.fmtMinutes(30))
        assertEquals("1小时25分", StudyLogic.fmtMinutes(85))
        assertEquals("2小时", StudyLogic.fmtMinutes(120))
    }
}
