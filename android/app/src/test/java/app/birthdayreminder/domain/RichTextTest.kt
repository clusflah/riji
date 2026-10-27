package app.birthdayreminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {
    @Test
    fun markdownInlineStylesRoundTrip() {
        val md = "> 引用里有 **粗** 和 *斜* 和 `code` 和 ~~划~~"
        val raw = RichText.fromMarkdown(md)
        assertEquals(RichText.QUOTE, raw[0])
        val line = RichText.parse(raw)[0]
        assertEquals("引用里有 粗 和 斜 和 code 和 划", line.text)
        assertEquals(RichText.BOLD, line.styles[line.text.indexOf('粗')])
        assertEquals(RichText.ITALIC, line.styles[line.text.indexOf('斜')])
        assertEquals(RichText.CODE, line.styles[line.text.indexOf('c')])
        assertEquals(RichText.STRIKE, line.styles[line.text.indexOf('划')])
        assertEquals(md, RichText.toMarkdown(raw))
    }

    @Test
    fun renderHidesMarksAndStyles() {
        val raw = RichText.fromMarkdown("a **bc** d")
        val r = RichText.render(raw)
        assertEquals("a bc d", r.display)
        assertTrue(r.spans.any { it.style == RichText.Style.BOLD && it.start == 2 && it.end == 4 })
        // 光标对照表长度正确
        assertEquals(raw.length + 1, r.origToTrans.size)
        assertEquals(r.display.length + 1, r.transToOrig.size)
    }

    @Test
    fun toggleBoldOnSelectionAndOff() {
        val raw = "你好世界"
        val on = RichText.toggleInline(raw, 1, 3, RichText.BOLD)
        assertEquals("你好世界", RichText.parse(on.text)[0].text)
        assertEquals(listOf(0, RichText.BOLD, RichText.BOLD, 0), RichText.parse(on.text)[0].styles)
        val (s, e) = on.selStart to on.selEnd
        val off = RichText.toggleInline(on.text, s, e, RichText.BOLD)
        assertEquals(raw, off.text)
    }

    @Test
    fun overlappingStylesSerializeStably() {
        val l = RichText.Line(null, "abc", listOf(RichText.BOLD, RichText.BOLD or RichText.ITALIC, RichText.ITALIC))
        val raw = RichText.serialize(listOf(l))
        assertEquals(listOf(l), RichText.parse(raw))
        assertEquals(raw, RichText.normalize(raw))
        assertEquals("**a*b****c*", RichText.toMarkdown(raw))
    }

    @Test
    fun quoteContinuesOnEnter() {
        val old = RichText.QUOTE + "abc"
        val new = old + "\n"
        val r = RichText.continueOnEnter(old, new, new.length)!!
        assertEquals(old + "\n" + RichText.QUOTE, r.text)
    }
}
